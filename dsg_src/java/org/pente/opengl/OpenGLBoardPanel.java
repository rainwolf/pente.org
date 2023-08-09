package org.pente.opengl;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.net.URL;
import java.nio.*;
import java.util.*;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.*;

import org.pente.gameServer.client.GameOptions;
import org.pente.gameServer.client.GameOptionsChangeListener;
import org.pente.gameServer.client.GridBoardListener;
import org.pente.gameServer.client.PenteBoardComponent;
import org.pente.gameServer.core.GridCoordinates;
import org.pente.gameServer.core.GridCoordinatesChangeListener;
import org.pente.gameServer.core.GridPiece;

import org.pente.opengl.util.*;

import com.sun.opengl.*;
import com.sun.opengl.util.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

public class OpenGLBoardPanel extends JPanel
        implements GLEventListener,
        PenteBoardComponent,
        GridCoordinatesChangeListener,
        GameOptionsChangeListener {


    private GLCanvas canvas;

    // specify all board coordinates using these dimensions
    // then use openGL to fit to actual window
    private int boardWidth = 400;
    private int boardHeight = 400;
    private int gridPieceSize = 20;

    class ColorInput extends Panel {
        private TextField rgb[] = new TextField[4];

        public ColorInput(String name, float init_rgb[]) {
            setLayout(new FlowLayout());
            add(new Label(name));
            for (int i = 0; i < 4; i++) {
                rgb[i] = new TextField("" + init_rgb[i], 3);
                add(rgb[i]);
            }
        }

        public float[] getColor() {
            float f[] = new float[4];
            for (int i = 0; i < 4; i++) {
                f[i] = Float.parseFloat(rgb[i].getText());
            }
            return f;
        }
    }

    private int distance = 920;
    private int distance2 = 1000;

    private Map<Integer, Piece> pieces = new HashMap<Integer, Piece>();
    private Piece currentPiece;
    private int currentX;
    private int currentY;
    private Object lock = new Object();

    private static final int STATE_DRAW = 1;
    private static final int STATE_SELECT = 2;
    private static final int STATE_MOVE = 3;
    private int state = STATE_DRAW;
    private int mouseX;
    private int mouseY;
    private int oldMouseX;
    private int oldMouseY;
    private boolean rotateBoard = false;

    double rotateTheta = 0;
    double tiltTheta = 0;
    double rotZ = 0.0;

    double rotXx = .5;
    double rotYx = .5;
    double rotZx = .5;

    GLU glu = new GLU();

    public OpenGLBoardPanel() {

        GLCapabilities capabilities = new GLCapabilities();

        canvas = new GLCanvas(capabilities);
        //canvas = GLDrawableFactory.getFactory().
        //	createGLCanvas(capabilities);
        canvas.setAutoSwapBufferMode(false);
        canvas.addGLEventListener(this);

        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);

        final ColorInput p1i = new ColorInput("P1: ",
                p1_color_test);
        final ColorInput p2i = new ColorInput("P2:",
                p2_color_test);

        Button b = new Button("Update");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                synchronized (lock) {
                    OpenGLBoardPanel.this.p1_color_test = p1i.getColor();
                    OpenGLBoardPanel.this.p2_color_test = p2i.getColor();
                }
                canvas.display();
            }
        });


        final JSlider tiltSlider = new JSlider(JSlider.HORIZONTAL,
                -90, 0, (int) tiltTheta);
        tiltSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                synchronized (lock) {
                    OpenGLBoardPanel.this.tiltTheta = (int) tiltSlider.getValue();
                    state = STATE_DRAW;
                }
                System.out.println("theta=" + tiltSlider.getValue());
                canvas.display();
            }
        });
        final JSlider perspecSlider = new JSlider(JSlider.HORIZONTAL,
                550, 1000, distance);
        perspecSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                synchronized (lock) {
                    OpenGLBoardPanel.this.distance = (int) perspecSlider.getValue();
                    state = STATE_DRAW;
                }
                System.out.println("distance=" + perspecSlider.getValue());
                canvas.display();
            }
        });

        Panel p = new Panel();
        p.setLayout(new FlowLayout());

        //p.add(p1i);
        //p.add(p2i);
        p.add(b);
        p.add(new JLabel("Perspective:"));
        p.add(perspecSlider);
        p.add(new JLabel("Tilt:"));
        p.add(tiltSlider);

        add(p, BorderLayout.SOUTH);

//		Piece p1 = new Piece(200, 200, 1, GREEN);
//		Piece p2 = new Piece(100, 100, 2, RED);
//		Piece p3 = new Piece(380, 80, 1, BLUE);
//		pieces.put(p1.getId(), p1);
//		pieces.put(p2.getId(), p2);
//		pieces.put(p3.getId(), p3);

        canvas.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                System.out.println("mousePressed");
                synchronized (lock) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                    state = STATE_SELECT;
//					canvas.setCursor(Cursor.getPredefinedCursor(
//						Cursor.HAND_CURSOR));
                }
                canvas.display();
            }

            public void mouseReleased(MouseEvent e) {
                System.out.println("mouseReleased");
                boolean validMove = false;
                synchronized (lock) {
                    if (currentPiece != null) {
                        // snap to grid
                        if (currentX >= 0 && currentX < 19 &&
                                currentY >= 0 && currentY < 19) {

                            currentPiece.setX((currentX + 1) * gridPieceSize);
                            currentPiece.setY((currentY + 1) * gridPieceSize);

                            validMove = true;
                        }
                    }

                    currentPiece = null;
                    state = STATE_DRAW;
                }
                canvas.display();

                if (validMove) {
                    gridClicked(currentX, 18 - currentY, e.getButton());
                }
            }

        });
        canvas.addMouseMotionListener(new MouseMotionAdapter() {

            public void mouseDragged(MouseEvent e) {

                System.out.println("mouse dragged");
                if (currentPiece != null) {
                    System.out.println("piece not null");
                    synchronized (lock) {
                        mouseX = e.getX();
                        mouseY = e.getY();
                        state = STATE_MOVE;
                    }
                    canvas.display();

                    // don't do this just yet, make player drag&drop stone
//					if (currentX >= 0 && currentX < 19 &&
//						currentY >= 0 && currentY < 19) {
//						
//						gridMoved(currentX, 18 - currentY);
//					}
                } else if (rotateBoard) {
                    System.out.println("selected board");
                    synchronized (lock) {
                        oldMouseX = mouseX;
                        oldMouseY = mouseY;
                        mouseX = e.getX();
                        mouseY = e.getY();
                        state = STATE_MOVE;
                    }
                    canvas.display();
                }
            }
        });

        canvas.display();
    }

    private int texture[] = new int[2];

    public void init(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();

        gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); //white
        gl.glShadeModel(GL.GL_SMOOTH);
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_LIGHTING);
        gl.glEnable(GL.GL_NORMALIZE);
        gl.glEnable(GL.GL_LIGHT0);

        //gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);

        gl.glLightfv(GL.GL_LIGHT0, GL.GL_AMBIENT,
                new float[]{.8f, .8f, .8f, 1}, 0);
        gl.glLightfv(GL.GL_LIGHT0, GL.GL_DIFFUSE,
                new float[]{1, 1, 1, 1}, 0);
        gl.glLightModelfv(GL.GL_LIGHT_MODEL_AMBIENT,
                new float[]{.1f, .1f, .1f, .1f}, 0);
        gl.glLightModeli(GL.GL_LIGHT_MODEL_LOCAL_VIEWER, GL.GL_TRUE);
        gl.glLightfv(GL.GL_LIGHT0, GL.GL_SPECULAR,
                new float[]{1, 1, 1, 1}, 0);

        loadTextures(drawable);
    }

    private void loadTextures(GLAutoDrawable drawable) {

        GL gl = drawable.getGL();

        TextureReader.Texture texture1 = null;
        TextureReader.Texture texture2 = null;
        try {
            texture1 = TextureReader.readTexture("graphics/board.png");
            texture2 = TextureReader.readTexture("graphics/border.png");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        //BufferedImage img = readPNGImage("graphics/board.png");
        //BufferedImage img2 = readPNGImage("graphics/border.png");
        gl.glGenTextures(2, texture, 0);

        // board texture
        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);
        makeRGBTexture(gl, glu, texture1, GL.GL_TEXTURE_2D, true);
        //makeRGBTexture(gl, glu, img, GL.GL_TEXTURE_2D, true);

        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[1]);
        makeRGBTexture(gl, glu, texture2, GL.GL_TEXTURE_2D, true);
        //makeRGBTexture(gl, glu, img2, GL.GL_TEXTURE_2D, true);

        // texture settings
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexEnvf(GL.GL_TEXTURE_ENV, GL.GL_TEXTURE_ENV_MODE, GL.GL_MODULATE);

        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    int viewport[] = new int[4];
    double mvmatrix[] = new double[16];
    double mvmatrixnorot[] = new double[16];
    double projmatrix[] = new double[16];

    public void reshape(
            GLAutoDrawable drawable,
            int x,
            int y,
            int width,
            int height) {

        int min = Math.min(width, height);


        GL gl = drawable.getGL();

        gl.glViewport(0, 0, min, min);
        gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);

        gl.glGetIntegerv(GL.GL_VIEWPORT, viewport, 0);

        gl.glMatrixMode(GL.GL_MODELVIEW);
    }

    private void frustum(GL gl) {

        gl.glFrustum(-boardWidth / 2, boardWidth / 2,
                -boardHeight / 2, boardHeight / 2,
                distance - gridPieceSize / 2, 10000);

        gl.glGetDoublev(GL.GL_PROJECTION_MATRIX, projmatrix, 0);
    }

//	private void drawXYZGrids(GL gl) {
//		gl.glLineWidth(1);
//		setGLColor(gl, Color.BLACK);
//		gl.glBegin(GL.GL_LINES);
//		gl.glVertex3d(-2000, 0, 0);
//		gl.glVertex3d(2000, 0, 0);
//		gl.glEnd();
//		setGLColor(gl, Color.BLUE);
//		gl.glBegin(GL.GL_LINES);
//		gl.glVertex3d(0, -10000, 0);
//		gl.glVertex3d(0, 10000, 0);
//		gl.glEnd();
//		setGLColor(gl, Color.RED);
//		gl.glBegin(GL.GL_LINES);
//		gl.glVertex3d(0, 0, -2000);
//		gl.glVertex3d(0, 0, 2000);
//		gl.glEnd();
//	}
//	private void setGLColor(GL gl, Color c) {
//
//		gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT_AND_DIFFUSE, 
//			new float[] { 
//				c.getRed() / 255.0f,
//				c.getGreen() / 255.0f,
//				c.getBlue() / 255.0f,
//				1 });
//
//        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, 
//			new float[] { 1, 1, 1, 1 });
//		gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, 100.0f);
//		
//		gl.glColor3f(
//			c.getRed() / 255.0f,
//			c.getGreen() / 255.0f,
//			c.getBlue() / 255.0f);
//	}

    public void display(GLAutoDrawable drawable) {

        System.out.println("display: " + state);
        //long startTime = System.currentTimeMillis();

        GL gl = drawable.getGL();

        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glLoadIdentity();
        frustum(gl);

        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glLoadIdentity();

        gl.glTranslated(-boardWidth / 2, -boardHeight / 2, -distance2);
        gl.glRotated(90, 1, 0, 0);


        if (state == STATE_DRAW) {
            gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

            // light
            float lightx = boardWidth / 2;
            float lighty = 150;
            float lightz = -100;
            gl.glLightfv(GL.GL_LIGHT0, GL.GL_POSITION,
                    new float[]{lightx, lighty, lightz, 1}, 0);
            // simulate light
            gl.glPushMatrix();
            gl.glTranslated(lightx, lighty, lightz);
            gl.glScaled(5, 1, 5);
            //piece(gl, glu);
            gl.glPopMatrix();

            // grid temp
            //drawXYZGrids(gl);

            // draw board
            gl.glPushMatrix();

            gl.glRotated(tiltTheta, 1, 0, 0);
            gl.glGetDoublev(GL.GL_MODELVIEW_MATRIX, mvmatrixnorot, 0);

            gl.glTranslatef(boardWidth / 2, 0, -boardHeight / 2);
            gl.glRotated(rotateTheta, 0, 1, 0);
            gl.glTranslatef(-boardWidth / 2, 0, boardHeight / 2);

            gl.glGetDoublev(GL.GL_MODELVIEW_MATRIX, mvmatrix, 0);

            drawEmptyBoard(gl, glu);

            drawPieces(gl, glu);

            gl.glPopMatrix();

            canvas.swapBuffers();
        } else if (state == STATE_SELECT) {

            IntBuffer selectBuf = BufferUtil.newIntBuffer(512);
            gl.glSelectBuffer(512, selectBuf);

            gl.glRenderMode(GL.GL_SELECT);
            gl.glInitNames();
            gl.glPushName(0);

            gl.glMatrixMode(GL.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            glu.gluPickMatrix(mouseX, viewport[3] - mouseY, 5, 5, viewport, 0);
            frustum(gl);

            // actual drawing
            gl.glMatrixMode(GL.GL_MODELVIEW);
            gl.glPushMatrix();

            gl.glRotated(tiltTheta, 1, 0, 0);

            gl.glTranslatef(boardWidth / 2, 0, -boardHeight / 2);
            gl.glRotated(rotateTheta, 0, 1, 0);
            gl.glTranslatef(-boardWidth / 2, 0, boardHeight / 2);

            drawPieces(gl, glu);

            gl.glPopMatrix();

            // clean up
            gl.glMatrixMode(GL.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL.GL_MODELVIEW);

            // hits
            int hits = gl.glRenderMode(GL.GL_RENDER);
            int closestName = processHits(hits, selectBuf);
            Piece p = pieces.get(closestName);
            if (p != null) {
                System.out.println("picked piece " + p.getId() + ": " +
                        p.getX() + "," + p.getY());

                synchronized (lock) {
                    currentPiece = p;
                }
            } else {
                //maybe selected board
                FloatBuffer winZB = BufferUtil.newFloatBuffer(1);
                //float[] winZ = new float[1];
                gl.glReadPixels(mouseX, viewport[3] - mouseY, 1, 1,
                        GL.GL_DEPTH_COMPONENT, GL.GL_FLOAT, winZB);
                double world[] = new double[3];
                glu.gluUnProject(mouseX, viewport[3] - mouseY, winZB.get(), mvmatrix, 0,
                        projmatrix, 0, viewport, 0, world, 0);
                System.out.println("selected: " + world[0] + "," + world[1] + "," + world[2]);
                // selected board
                if (world[1] > -5 && world[1] < 5) {
                    synchronized (lock) {
                        rotateBoard = true;
                    }
                }

            }
            synchronized (lock) {
                state = STATE_DRAW;
            }
            canvas.display();
        } else if (state == STATE_MOVE) {
            if (currentPiece != null) {
                System.out.println("process move for piece " + currentPiece);

                FloatBuffer winZB = BufferUtil.newFloatBuffer(1);
                //float[] winZ = new float[1];
                gl.glReadPixels(mouseX, viewport[3] - mouseY, 1, 1,
                        GL.GL_DEPTH_COMPONENT, GL.GL_FLOAT, winZB);
                double world[] = new double[3];
                glu.gluUnProject(mouseX, viewport[3] - mouseY, winZB.get(), mvmatrix, 0,
                        projmatrix, 0, viewport, 0, world, 0);

                synchronized (lock) {
                    if (currentPiece != null) {
                        double x = world[0];
                        double y = -world[2];

                        currentPiece.setX(x);
                        currentPiece.setY(y);

                        // save off currentX and currentY, used to recenter
                        // piece after mouse released
                        x -= gridPieceSize / 2; // subtract 1/2 grid to center
                        y -= gridPieceSize / 2;
                        x /= gridPieceSize;
                        y /= gridPieceSize;
                        currentX = (int) x;
                        currentY = (int) y;

                        state = STATE_DRAW;
                    }
                }
                System.out.println("new position for piece " + currentPiece);
            } else if (rotateBoard) {
                System.out.println("process move board");
                FloatBuffer oldZB = BufferUtil.newFloatBuffer(1);
                //float[] oldZ = new float[1];
                gl.glReadPixels(oldMouseX, viewport[3] - oldMouseY, 1, 1,
                        GL.GL_DEPTH_COMPONENT, GL.GL_FLOAT, oldZB);
                double oldW[] = new double[3];
                glu.gluUnProject(oldMouseX, viewport[3] - oldMouseY, oldZB.get(), mvmatrix,
                        0, projmatrix, 0, viewport, 0, oldW, 0);
                System.out.println("old: " + oldW[0] + "," + oldW[1] + "," + oldW[2]);

                //float[] newZ = new float[1];
                FloatBuffer newZB = BufferUtil.newFloatBuffer(1);
                gl.glReadPixels(mouseX, viewport[3] - mouseY, 1, 1,
                        GL.GL_DEPTH_COMPONENT, GL.GL_FLOAT, newZB);

                double newW[] = new double[3];
                glu.gluUnProject(mouseX, viewport[3] - mouseY, newZB.get(), mvmatrix,
                        0, projmatrix, 0, viewport, 0, newW, 0);
                System.out.println("new: " + newW[0] + "," + newW[1] + "," + newW[2]);

                // mouse dragged off board
                if (newW[1] < -5 || newW[1] > 5) {
                    synchronized (lock) {
                        rotateBoard = false;
                        state = STATE_DRAW;
                    }
                } else {
                    // calculate angle moved around circle of board between
                    // new and old mouse points
                    double ac = dist(boardWidth / 2, boardHeight / 2, oldW[0], -oldW[2]);
                    double ab = dist(boardWidth / 2, boardHeight / 2, newW[0], -newW[2]);
                    double bc = dist(oldW[0], -oldW[2], newW[0], -newW[2]);

                    double cosa = -((bc * bc) - (ab * ab) - (ac * ac)) / (2 * ab * ac);
                    double a = Math.acos(cosa);
                    a = Math.toDegrees(a);
                    System.out.println("ac=" + ac + ",ab=" + ab + ",bc=" + bc + ",cosa=" + cosa + ",a=" + a);

                    // calculate angle of each mouse point around circle
                    // to determine which way we are rotating around (clockwise or countercw)
                    double acAngle = Math.acos((boardWidth / 2 - oldW[0]) / ac);
                    if (oldW[2] > -boardHeight / 2) {
                        acAngle = 360 - acAngle;
                    }
                    double abAngle = Math.acos((boardWidth / 2 - newW[0]) / ab);
                    if (newW[2] > -boardHeight / 2) {
                        abAngle = 360 - abAngle;
                    }
                    System.out.println("ac angle=" + acAngle + ",ab angle=" + abAngle);

                    synchronized (lock) {
                        if (acAngle > abAngle) {
                            rotateTheta += a;
                        } else {
                            rotateTheta -= a;
                        }
                        state = STATE_DRAW;
                    }
                }
            }

            canvas.display();
        }

        //long endTime = System.currentTimeMillis();
        //System.out.println("time="+(endTime-startTime));
    }

    private double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt((Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)));
    }

    /**
     * got this online somewhere
     */
    public int processHits(int hits, IntBuffer buffer) {
        System.out.println("---------------------------------");
        System.out.println(" HITS: " + hits);
        int offset = 0;
        int names;
        float z1, z2;
        int closestName = -1;
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < hits; i++) {
            System.out.println("- - - - - - - - - - - -");
            System.out.println(" hit: " + (i + 1));
            names = buffer.get(offset);
            offset++;
            z1 = (float) buffer.get(offset) / 0x7fffffff;
            offset++;
            z2 = (float) buffer.get(offset) / 0x7fffffff;
            offset++;
            System.out.println(" number of names: " + names);
            System.out.println(" z1: " + z1);
            System.out.println(" z2: " + z2);
            System.out.println(" names: ");

            if (z1 < closest) {
                closest = z1;
                closestName = buffer.get(offset);
            }

            for (int j = 0; j < names; j++) {
                System.out.print("       " + buffer.get(offset));
                if (j == (names - 1))
                    System.out.println("<-");
                else
                    System.out.println();
                offset++;
            }
            System.out.println("- - - - - - - - - - - -");
        }
        System.out.println("---------------------------------");

        return closestName;
    }

    private static final float GREEN[] = {0.0f, 0.4f, 0.0f, 0.9f};
    private static final float RED[] = {0.4f, 0f, 0f, 0.9f};
    private static final float BLUE[] = {0.0f, 0f, 0.8f, 0.5f};
    private static final float WHITE[] = {0.7f, .7f, .7f, 0.90f};
    private static final float BLACK[] = {0.1f, .1f, .1f, 0.90f};

    float[] p1_color_test = {0.0f, 0.4f, 0.0f, 0.9f};
    float[] p2_color_test = {0.0f, 0.4f, 0.0f, 0.9f};

    private void drawPieces(GL gl, GLU glu) {
        java.util.List<Piece> l = null;
        synchronized (lock) {
            l = new ArrayList<Piece>(pieces.values());
        }


        // setup opengl stuff
        gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(false);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        GLUquadric q = glu.gluNewQuadric();
        glu.gluQuadricDrawStyle(q, GLU.GLU_FILL);
        glu.gluQuadricNormals(q, GLU.GLU_SMOOTH);
        glu.gluQuadricOrientation(q, GLU.GLU_OUTSIDE);
        float[] no_mat = {0.0f, 0.0f, 0.0f, 1.0f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 1.0f};
        float high_shininess = 100.0f;

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_EMISSION, no_mat, 0);

        // draw pieces
        for (Piece p : l) {
            System.out.println("draw piece " + p);
            gl.glLoadName(p.getId());
            gl.glPushMatrix();
            gl.glTranslated(p.getX(), 0, -p.getY());
            gl.glScaled(1, .70, 1);

            //live, use this
            //gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, p.getColor());
            //gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, p.getColor());

            //test, use this
            if (p.getPlayer() == 1) {
                gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, p1_color_test, 0);
                gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, p1_color_test, 0);
            } else {
                gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, p2_color_test, 0);
                gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, p2_color_test, 0);
            }

            glu.gluSphere(q, gridPieceSize / 2, 20, 20);

            gl.glPopMatrix();
        }

        // opengl stuff
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
    }


    private void drawEmptyBoard(GL gl, GLU glu) {

        drawBoard3D(gl);
        drawEmptyBoardBackground(gl);
        drawEmptyBoardGrid(gl);
        drawInnerCircles(gl, glu);

        drawEmptyBoardGameName(gl, glu);
//        drawEmptyBoardCoordinates(g);
    }


    private int or = 20;
    private int bor = 10;
    private float repeatBorder = 20;

    private void drawBoard3D(GL gl) {

        // material properties
        float[] mat_ambient_color = {1f, .9f, .6f, 1f};
        float[] mat_diffuse = {.78f, 0.68f, 0.55f, 1f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 0.5f};
        float high_shininess = 100.0f;
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient_color, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);


        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[1]);
        gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);

        // one side border
        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 0, 1);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, 0, 0);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, bor, 0);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, 0);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, 0, 0);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, bor, 0);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, bor, or);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, or);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, 0);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 0, 1);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, bor, or);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, -or, or);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, -or, or);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, or);
        gl.glEnd();


        // 2 side border
        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(1, 0, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(0, 0, 0);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(0, 0, -boardHeight);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(0, bor, -boardHeight);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(0, bor, 0);


        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(0, bor, 0);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(0, bor, -boardHeight);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(-or, bor, -boardHeight);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, bor, 0);

        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(-1, 0, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, bor, or);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(-or, bor, -boardHeight - or);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(-or, -or, -boardHeight - or);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, -or, or);
        gl.glEnd();

        // 3 side border
        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 0, 1);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(0, 0, -boardHeight);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth, 0, -boardHeight);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth, bor, -boardHeight);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(0, bor, -boardHeight);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, bor, -boardHeight);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, -boardHeight);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, -boardHeight - or);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, bor, -boardHeight - or);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 0, -1);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(-or, bor, -boardHeight - or);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, -boardHeight - or);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, -or, -boardHeight - or);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(-or, -or, -boardHeight - or);
        gl.glEnd();


        // 4 side border
        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(-1, 0, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(boardWidth, 0, 0);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(boardWidth, bor, 0);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth, bor, -boardHeight);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth, 0, -boardHeight);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(boardWidth, bor, 0);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(boardWidth + or, bor, 0);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, -boardHeight);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth, bor, -boardHeight);
        gl.glEnd();

        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(1, 0, 0);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(boardWidth + or, bor, or);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(boardWidth + or, -or, or);
        gl.glTexCoord2f(1.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, -or, -boardHeight - or);
        gl.glTexCoord2f(0.0f, repeatBorder);
        gl.glVertex3f(boardWidth + or, bor, -boardHeight - or);
        gl.glEnd();

        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }


    private void drawEmptyBoardBackground(GL gl) {

        float[] mat_ambient_color = {1f, 1f, 1f, 1f};
        float[] mat_diffuse = {1f, 1f, 1f, 1f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 0.5f};
        float high_shininess = 100.0f;

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient_color, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);

        gl.glBindTexture(GL.GL_TEXTURE_2D, texture[0]);


        gl.glBegin(GL.GL_QUADS);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(0, 0, 0);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(boardWidth, 0, 0);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(boardWidth, 0, -boardHeight);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(0, 0, -boardHeight);
        gl.glEnd();

        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

    }

    int startX = gridPieceSize;
    int startZ = gridPieceSize;

    private void drawEmptyBoardGrid(GL gl) {

        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(false);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        float[] mat_ambient_color = {0.1f, .1f, .1f, 0.5f};
        float[] mat_diffuse = {0.1f, 0.1f, 0.1f, 0.5f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 0.5f};

        float high_shininess = 100.0f;

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient_color, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);

        int x = startX;
        float y = 0.01f;
        int z = -startZ;

        gl.glLineWidth(5);

        // draw vertical grid lines
        for (int i = 0; i < 19; i++) {
            gl.glBegin(GL.GL_LINES);
            gl.glVertex3d(x, y, z);
            gl.glVertex3d(x, y, z - gridPieceSize * 18);
            gl.glEnd();

            x += gridPieceSize;
        }

        x = startX;
        z = -startZ;

        // draw horizontal grid lines
        for (int i = 0; i < 19; i++) {

            gl.glLineWidth(3);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex3d(x, y, z);
            gl.glVertex3d(x + gridPieceSize * 18, y, z);
            gl.glEnd();

            z -= gridPieceSize;
        }

        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_LINE_SMOOTH);
    }

    private void drawEmptyBoardGameName(GL gl, GLU glu) {

        gl.glPushMatrix();

        //TODO don't blend but find appropriate color
        gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(false);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        float[] mat_ambient_color = {0.3f, .3f, .3f, 0.5f};
        float[] mat_diffuse = {0.3f, 0.3f, 0.3f, 0.5f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 0.5f};

        float high_shininess = 100.0f;

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient_color, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);

        double x = (gridPieceSize * 21 / -150) / 2;
        System.out.println("trans x = " + x);
        gl.glTranslated(95, 2, -180);
        gl.glScaled(7, 7, 7);

        OpenGLText.drawText(gl, glu, "Pente");

        gl.glPopMatrix();

        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
    }

    public void drawInnerCircles(GL gl, GLU glu) {

        gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(false);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        float[] mat_ambient_color = {0.1f, .1f, .1f, 0.5f};
        float[] mat_diffuse = {0.1f, 0.1f, 0.1f, 0.5f};
        float[] mat_specular = {1.0f, 1.0f, 1.0f, 0.5f};
        float high_shininess = 100.0f;

        gl.glMaterialfv(GL.GL_FRONT, GL.GL_AMBIENT, mat_ambient_color, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_DIFFUSE, mat_diffuse, 0);
        gl.glMaterialfv(GL.GL_FRONT, GL.GL_SPECULAR, mat_specular, 0);
        gl.glMaterialf(GL.GL_FRONT, GL.GL_SHININESS, high_shininess);

        int distanceFromCenter = 3;
        int halfGridPieceSize = gridPieceSize / 3;
        int offsetFromX = 6 * gridPieceSize;
        int offsetFromZ = 6 * gridPieceSize;

        float x = startX + offsetFromX;
        float z = -startZ - offsetFromZ;
        float y = 0.01f;


        drawCircle(gl, glu, x, y, z, halfGridPieceSize);
        x += distanceFromCenter * 2 * gridPieceSize;
        drawCircle(gl, glu, x, y, z, halfGridPieceSize);
        z -= distanceFromCenter * 2 * gridPieceSize;
        drawCircle(gl, glu, x, y, z, halfGridPieceSize);
        x -= distanceFromCenter * 2 * gridPieceSize;
        drawCircle(gl, glu, x, y, z, halfGridPieceSize);
        x += distanceFromCenter * gridPieceSize;
        z += distanceFromCenter * gridPieceSize;
        drawCircle(gl, glu, x, y, z, halfGridPieceSize);

        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
    }

    private void drawCircle(GL gl, GLU glu, float x0, float y0, float z0, int r) {
        double theta = 0;

        //double d = r / Math.cos(Math.PI / 4);

        gl.glPointSize(1);
        gl.glEnable(GL.GL_POINT_SMOOTH);
        gl.glBegin(GL.GL_POINTS);

        for (int i = 0; i < 360; i++) {
            double x = x0 + r * Math.cos(theta);
            double z = z0 + r * Math.sin(theta);
            gl.glVertex3d(x, y0, z);
            theta += 2 * Math.PI / 360;
        }

        gl.glEnd();

//		System.out.println("draw circle " + x0 + ","+y0+","+z0);
//		gl.glLineWidth(0.1f);
//		gl.glEnable(GL.GL_LINE_SMOOTH);
//		GLUquadric q = glu.gluNewQuadric();
//		glu.gluQuadricDrawStyle(q, GLU.GLU_LINE);
//		glu.gluQuadricNormals(q, GLU.GLU_SMOOTH);
//		glu.gluQuadricOrientation(q, GLU.GLU_OUTSIDE);
//		gl.glPushMatrix();
//		gl.glTranslated(x0, y0, z0);
//		gl.glRotated(90, 1, 0, 0);
//		glu.gluDisk(q, r-.01, r, 20, 20);
//		gl.glPopMatrix();
    }

    // not implemented
    public void displayChanged(
            GLAutoDrawable drawable,
            boolean modeChanged,
            boolean deviceChanged) {
    }


    private void makeRGBTexture(GL gl, GLU glu, TextureReader.Texture img,
                                int target, boolean mipmapped) {

        if (mipmapped) {
            glu.gluBuild2DMipmaps(target, GL.GL_RGB8, img.getWidth(),
                    img.getHeight(), GL.GL_RGB, GL.GL_UNSIGNED_BYTE, img.getPixels());
        } else {
            gl.glTexImage2D(target, 0, GL.GL_RGB, img.getWidth(),
                    img.getHeight(), 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, img.getPixels());
        }
    }

    /** got this online somewhere 
     private BufferedImage readPNGImage(String resourceName) {
     System.out.println("readPNGImage " + resourceName);
     try {

     URL url = getResource(resourceName);
     System.out.println("url="+url);
     if (url == null) {
     throw new RuntimeException("Error reading resource "
     + resourceName);
     }
     BufferedImage img = ImageIO.read(url);
     java.awt.geom.AffineTransform tx = java.awt.geom.AffineTransform
     .getScaleInstance(1, -1);
     tx.translate(0, -img.getHeight(null));
     AffineTransformOp op = new AffineTransformOp(tx,
     AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
     img = op.filter(img, null);
     return img;
     } catch (IOException e) {
     throw new RuntimeException(e);
     }
     }*/
    /** got this online somewhere 
     private void makeRGBTexture(GL gl, GLU glu, BufferedImage img, int target,
     boolean mipmapped) {
     ByteBuffer dest = null;
     switch (img.getType()) {
     case BufferedImage.TYPE_3BYTE_BGR:
     case BufferedImage.TYPE_CUSTOM: {
     byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer())
     .getData();
     dest = ByteBuffer.allocateDirect(data.length);
     dest.order(ByteOrder.nativeOrder());
     dest.put(data, 0, data.length);
     break;
     }
     case BufferedImage.TYPE_INT_RGB: {
     int[] data = ((DataBufferInt) img.getRaster().getDataBuffer())
     .getData();
     dest = ByteBuffer.allocateDirect(data.length
     * BufferUtil.SIZEOF_INT);
     dest.order(ByteOrder.nativeOrder());
     dest.asIntBuffer().put(data, 0, data.length);
     break;
     }
     default:
     throw new RuntimeException("Unsupported image type "
     + img.getType());
     }

     if (mipmapped) {
     glu.gluBuild2DMipmaps(target, GL.GL_RGB8, img.getWidth(), img
     .getHeight(), GL.GL_RGB, GL.GL_UNSIGNED_BYTE, dest);
     } else {
     gl.glTexImage2D(target, 0, GL.GL_RGB, img.getWidth(), img
     .getHeight(), 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, dest);
     }
     }
     */

    /**
     * Retrieve a URL resource from the jar. If the resource is not found, then
     * the local disk is also checked.
     *
     * @param filename Complete filename, including parent path
     * @return a URL object if resource is found, otherwise null.
     * @author - got this online somewhere
     * public final URL getResource(final String filename) {
     * <p>
     * <p>
     * // Try to load resource from jar
     * URL url = this.getClass().getResource(filename);
     * // If not found in jar, then load from disk
     * if (url == null) {
     * try {
     * url = new URL("file", "localhost", filename);
     * } catch (Exception urlException) {
     * } // ignore
     * }
     * return url;
     * }
     */


    public void setGameName(String name) {
        // TODO Auto-generated method stub

    }

    public void setGameNameColor(int color) {
        // TODO Auto-generated method stub

    }


    public void gridCoordinatesChanged(GridCoordinates gridCoordinates) {
        // TODO Auto-generated method stub
        // switch coordinates
    }

    public void destroy() {
    }

    private List<GridBoardListener> listeners = new ArrayList<GridBoardListener>(5);

    public void addGridBoardListener(GridBoardListener listener) {
        listeners.add(listener);
    }

    public void removeGridBoardListener(GridBoardListener listener) {
        listeners.remove(listener);
    }

    private void gridMoved(int x, int y) {
        for (GridBoardListener l : listeners) {
            l.gridMoved(x, y);
        }
    }

    private void gridClicked(int x, int y, int button) {
        for (GridBoardListener l : listeners) {
            l.gridClicked(x, y, button);
        }
    }

    public void addPiece(GridPiece gridPiece) {

        synchronized (lock) {
            double x = (gridPiece.getX() + 1) * gridPieceSize;
            double y = (gridPiece.getY() + 1) * gridPieceSize;
            Piece p = new Piece(gridPiece.getX(), gridPiece.getY(),
                    x, y, gridPiece.getPlayer(), WHITE);
            pieces.put(p.getId(), p);

            currentPiece = null;
            state = STATE_DRAW;
        }

        canvas.display();
    }

    public void updatePiecePlayer(int x, int y, int player) {
        synchronized (lock) {
            Piece r = null;
            for (Piece p : pieces.values()) {
                if (p.getXCoord() == x && p.getYCoord() == y) {
                    r = p;
                    break;
                }
            }
            if (r != null) {
                //pieces. set color
            }
        }
        canvas.display();
    }

    public void clearPieces() {
        synchronized (lock) {
            pieces.clear();
        }
        canvas.display();
    }

    public void removePiece(GridPiece g) {
        synchronized (lock) {
            Piece r = null;
            for (Piece p : pieces.values()) {
                if (p.getXCoord() == g.getX() && p.getYCoord() == g.getY()) {
                    r = p;
                    break;
                }
            }
            if (r != null) {
                pieces.remove(r.getId());
            }
        }
        canvas.display();
    }

    public void setCursor(int cursor) {

    }

    public void gameOptionsChanged(GameOptions gameOptions) {
        // TODO Auto-generated method stub
        // update player colors
    }

    public void decrementCaptures(int player) {
        // TODO Auto-generated method stub
        // remove a captured piece
    }

    public void incrementCaptures(int player) {
        // TODO Auto-generated method stub
        // add a captured piece
    }


    // everything below not implemented yet
    public int getGridHeight() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int getGridWidth() {
        // TODO Auto-generated method stub
        return 0;
    }

    public boolean getOnGrid() {
        // TODO Auto-generated method stub
        return false;
    }

    public void setBackgroundColor(int color) {
        // TODO Auto-generated method stub

    }


    public void setDrawCoordinates(boolean drawCoordinates) {
        // TODO Auto-generated method stub

    }

    public void setDrawInnerCircles(boolean drawInnerCircles) {
        // TODO Auto-generated method stub

    }

    public void setGridColor(int color) {
        // TODO Auto-generated method stub

    }

    public void setGridHeight(int height) {
        // TODO Auto-generated method stub

    }

    public void setGridWidth(int width) {
        // TODO Auto-generated method stub

    }

    public void setHighlightColor(int color) {
        // TODO Auto-generated method stub

    }

    public void setHighlightPiece(GridPiece gridPiece) {
        // TODO Auto-generated method stub

    }

    public void setMessage(String message) {
        // TODO Auto-generated method stub

    }

    public void setNewMovesAvailable(boolean available) {
        // TODO Auto-generated method stub

    }

    public void setOnGrid(boolean onGrid) {
        // TODO Auto-generated method stub

    }

    public void setThinkingPiecePlayer(int player) {
        // TODO Auto-generated method stub

    }

    public void setThinkingPieceVisible(boolean visible) {
        // TODO Auto-generated method stub

    }

    public void refresh() {
        // TODO Auto-generated method stub
    }

    public void setBoardInsets(int l, int t, int r, int b) {
        // TODO Auto-generated method stub

    }
}
