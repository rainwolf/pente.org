package org.pente.opengl;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;

import com.sun.opengl.*;
import com.sun.opengl.util.*;

import javax.media.opengl.*;
import javax.media.opengl.glu.*;

public class OpenGLText {

    public static void drawText(final GL gl, final GLU glu, String text) {

        gl.glPushMatrix();

        gl.glRotated(90, 1, 0, 0);

        Font f = new Font("Arial", Font.PLAIN, 12);
        FontRenderContext frc = new FontRenderContext(null, false, false);
        GlyphVector g = f.layoutGlyphVector(frc,
                text.toCharArray(), 0, text.length(), 0);
        Shape shape = g.getOutline();

        class MyCallbackAdapter extends GLUtessellatorCallbackAdapter {
            double minx = Integer.MAX_VALUE;
            double maxx = 0;

            public void begin(int primitiveType) {
                gl.glBegin(primitiveType);
            }

            public void vertex(Object data) {
                double d[] = (double[]) data;
                if (d[0] < minx) {
                    minx = d[0];
                }
                if (d[0] > maxx) {
                    maxx = d[0];
                }
                gl.glVertex3dv(d, 0);
            }

            public void end() {
                gl.glEnd();
            }
        }
        MyCallbackAdapter adapter = new MyCallbackAdapter();
        GLUtessellator tesselator = glu.gluNewTess();
        glu.gluTessCallback(tesselator, GLU.GLU_TESS_BEGIN, adapter);
        glu.gluTessCallback(tesselator, GLU.GLU_TESS_VERTEX, adapter);
        glu.gluTessCallback(tesselator, GLU.GLU_TESS_END, adapter);
        glu.gluBeginPolygon(tesselator);


        PathIterator i = shape.getPathIterator(null, 0.05);
        double[] segment = new double[6];
        while (!i.isDone()) {
            switch (i.currentSegment(segment)) {
                case PathIterator.SEG_MOVETO: {
                    double[] coords = new double[3];
                    coords[0] = segment[0];
                    coords[1] = segment[1];
                    // z value stays 0

                    glu.gluTessBeginContour(tesselator);
                    //glu.gluTessNormal(tesselator, 0, -1, 0);
                    glu.gluTessVertex(tesselator, coords, 0, coords);
                    break;
                }
                case PathIterator.SEG_LINETO: {
                    double[] coords = new double[3];
                    coords[0] = segment[0];
                    coords[1] = segment[1];

                    //glu.gluTessNormal(tesselator, 0, -1, 0);
                    glu.gluTessVertex(tesselator, coords, 0, coords);
                    break;
                }
                case PathIterator.SEG_CLOSE: {
                    glu.gluTessEndContour(tesselator);
                    break;
                }
            }
            i.next();
        }
        glu.gluTessEndPolygon(tesselator);
//		System.out.println("2 " + adapter.minx  +"-"+adapter.maxx);
//		System.out.println("2 width="+(adapter.maxx - adapter.minx));
        gl.glPopMatrix();
    }
}
