/**
 * GridBoardCanvas.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client.web;

import org.pente.gameServer.client.*;
import org.pente.gameServer.core.GridCoordinates;
import org.pente.gameServer.core.GridCoordinatesChangeListener;
import org.pente.gameServer.core.GridPiece;
import org.pente.gameServer.core.SimpleGridPiece;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class GridBoardCanvas extends Canvas
        implements GridBoardComponent,
        GridCoordinatesChangeListener,
        GameOptionsChangeListener {

    // the number of grids on the x axis
    int gridWidth;

    // number of grids on the y axis
    int gridHeight;

    // if true, the pieces show up ontop of the grid lines
    // if false, the pieces show up in between the grid lines
    boolean piecesOnGrid;

    // the width of the beveled edge around the whole board
    int beveledEdge;

    // space after beveled edge that won't be
    // used to draw the grid lines of the board
    Insets insets;

    // space used for the coordinates
    Dimension coordinatesDimensions;

    // the left over space between the edge of the
    // insets and the beginning of the grid lines of the board
    Dimension edgeLeftOvers;

    // the width of a piece on the board
    int gridPieceSize;


    private Map<Integer, List<Integer>> goTerritory;

    private String gameName;
    private Vector gridPieces;
    private GridPiece highlightPiece;
    private GridPiece thinkingPiece;
    private GridPiece oldThinkingPiece;
    private boolean showThinkingPiece;
    private boolean newMovesAvailable;
    private boolean showNewMovesAvailable;
    private GameTimer showNewMovesAvailableTimer;
    private boolean drawInnerCircles;
    private boolean drawGoDots;
    private boolean drawCoordinates = true;

    GameOptions gameOptions;
    GridCoordinates gridCoordinates;

    boolean boardDirty = true;
    boolean emptyBoardDirty = true;
    final Object drawLock = new Object();

    private Dimension currentSize;

    Image emptyBoardImage;
    Graphics emptyBoardGraphics;
    Image boardImage;
    Graphics boardGraphics;

    private Color backGroundColor;
    private Color gridColor;
    private Color gameNameColor;
    private Color highlightColor;
    private Color shadowColor;
    private Color transparentHighlightColor;
    private Color transparentShadowColor;

    // move listeners
    private Vector listeners;

    private static final Font MESSAGE_FONT = new Font("Arial", Font.PLAIN, 12);
    private String message;
    private boolean hideMessage = false;

    public GridBoardCanvas() {
        listeners = new Vector();
        gridPieces = new Vector();
        gridPieceSize = -1;

        addMouseListener(new MoveEventGenerator());
        addMouseMotionListener(new ThinkingPieceMoveGenerator());

        // temp
        backGroundColor = new Color(255, 222, 165);
        gameNameColor = new Color(234, 196, 136);
        gridColor = Color.gray;
        highlightColor = Color.yellow;
        transparentHighlightColor = new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), GameStyles.transparency);
        shadowColor = new Color(60, 60, 60);
        transparentShadowColor = new Color(shadowColor.getRed(), shadowColor.getGreen(), shadowColor.getBlue(), GameStyles.transparency);

        thinkingPiece = new SimpleGridPiece();
        thinkingPiece.setPlayer(1);
        thinkingPiece.setX(-1);
        thinkingPiece.setY(-1);
        oldThinkingPiece = new SimpleGridPiece();
        oldThinkingPiece.setPlayer(1);
        oldThinkingPiece.setX(-1);
        oldThinkingPiece.setY(-1);

        currentSize = new Dimension(0, 0);

        insets = new Insets(0, 10, 0, 10);
        coordinatesDimensions = new Dimension(0, 0);
        edgeLeftOvers = new Dimension(0, 0);
        beveledEdge = 3;
        // end temp
    }

    public void testMouseEvent(MouseEvent e) {
        processMouseMotionEvent(e);
    }

    // GridBoardComponent
    // most of these functions SHOULD be set prior
    // to any drawing operations since they aren't synchronized
    // on the drawLock...
    public int getGridWidth() {
        return gridWidth;
    }

    public void setGridWidth(int width) {
        this.gridWidth = width;
//        calculateGridSize();
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public void setGridHeight(int height) {
        this.gridHeight = height;
//        calculateGridSize();
    }

    public boolean getOnGrid() {
        return piecesOnGrid;
    }

    public void setOnGrid(boolean onGrid) {
        this.piecesOnGrid = onGrid;
    }

    public Vector getGridPieces() {
        return (Vector) gridPieces.clone();
    }

    public void setBackgroundColor(int color) {
        this.backGroundColor = new Color(color);
        gameNameColor = backGroundColor.darker();
    }

    public void setGridColor(int color) {
        this.gridColor = new Color(color);
    }

    public void setHighlightColor(int color) {
        this.highlightColor = new Color(color);
    }

    public void setGameNameColor(int color) {
        this.gameNameColor = new Color(color);
    }

    public void setTerritory(Map<Integer, List<Integer>> goTerritory) {
        synchronized (drawLock) {
            this.goTerritory = goTerritory;
            boardDirty = true;
        }
        repaint();
    }

    public void setMessage(String message) {
        synchronized (drawLock) {
            this.message = message;
        }
        repaint();
    }

    public void setGameName(String gameName) {
        synchronized (drawLock) {
            this.gameName = gameName;
            emptyBoardDirty = true;
        }
        repaint();
    }

    public void setHighlightPiece(GridPiece gridPiece) {
        synchronized (drawLock) {
            this.highlightPiece = gridPiece;
            emptyBoardDirty = true;
        }

        repaint();
    }

    public void setThinkingPieceVisible(boolean visible) {
        synchronized (drawLock) {
            if (showThinkingPiece != visible) {
                showThinkingPiece = visible;
            }
        }

        repaint();
    }

    public void setThinkingPiecePlayer(int player) {
        synchronized (drawLock) {
            if (thinkingPiece.getPlayer() != player) {
                thinkingPiece.setPlayer(player);
            }
        }

        repaint();
    }

    public void setNewMovesAvailable(boolean available) {
        synchronized (drawLock) {
            if (newMovesAvailable != available) {
                newMovesAvailable = available;

                if (newMovesAvailable) {
                    if (showNewMovesAvailableTimer == null) {
                        showNewMovesAvailableTimer = new SimpleGameTimer();
                        showNewMovesAvailableTimer.setStartMinutes(1000);
                        showNewMovesAvailableTimer.reset();
                        showNewMovesAvailableTimer.addGameTimerListener((newSeconds, newMinutes) -> {
                            synchronized (drawLock) {
                                if (newMovesAvailable) {
                                    showNewMovesAvailable = !showNewMovesAvailable;
                                    emptyBoardDirty = true;
                                }
                            }
                            repaint();
                        });
                    }
                    showNewMovesAvailableTimer.go();
                } else {
                    synchronized (drawLock) {
                        if (showNewMovesAvailableTimer != null) {
                            showNewMovesAvailableTimer.stop();
                        }
                        showNewMovesAvailable = false;
                        emptyBoardDirty = true;
                    }
                    repaint();
                }
            }
        }
    }

    public void setDrawInnerCircles(boolean drawInnerCircles) {
        this.drawInnerCircles = drawInnerCircles;
    }

    public void setDrawGoDots(boolean drawGoDots) {
        this.drawGoDots = drawGoDots;
    }

    public boolean drawGoDots() {
        return drawGoDots;
    }

    public void setDrawCoordinates(boolean drawCoordinates) {
        this.drawCoordinates = drawCoordinates;
    }

    public void setBoardInsets(int l, int t, int r, int b) {
        this.insets = new Insets(l, t, r, b);
    }

    public void addGridBoardListener(GridBoardListener listener) {
        listeners.addElement(listener);
    }

    public void removeGridBoardListener(GridBoardListener listener) {
        listeners.removeElement(listener);
    }
    // end GridBoardComponent


    // PieceCollection
    public void addPiece(GridPiece gridPiece) {

        synchronized (drawLock) {
            gridPieces.addElement(gridPiece);
            boardDirty = true;
        }
        repaint();
    }

    public void removePiece(GridPiece gridPiece) {
        synchronized (drawLock) {
            gridPieces.removeElement(gridPiece);
            boardDirty = true;
        }
        repaint();
    }

    public void updatePiecePlayer(int x, int y, int player) {
        synchronized (drawLock) {
            for (int i = 0; i < gridPieces.size(); i++) {
                GridPiece p = (GridPiece) gridPieces.elementAt(i);
                if (p.getX() == x && p.getY() == y) {
                    p.setPlayer(player);
                    boardDirty = true;
                    break;
                }
            }
            if (boardDirty) {
                repaint();
            }
        }
    }

    public void clearPieces() {
        synchronized (drawLock) {
            gridPieces.removeAllElements();
            boardDirty = true;
        }
        repaint();
    }
    // end PieceCollection

    // GameOptionsChangeListener
    public void gameOptionsChanged(GameOptions gameOptions) {
        synchronized (drawLock) {
            this.gameOptions = gameOptions;
            boardDirty = true;
        }
        repaint();
    }

    // GridCoordinatesChangeListener
    public void gridCoordinatesChanged(GridCoordinates gridCoordinates) {
        synchronized (drawLock) {
            this.gridCoordinates = gridCoordinates;
            emptyBoardDirty = true;
        }
        repaint();
    }

    // Canvas
    public Dimension getMinimumSize() {
        return new Dimension(200, 200);
    }

    public Dimension getPreferredSize() {
        return new Dimension(400, 400);
    }

    public void addNotify() {
        super.addNotify();

        emptyBoardImage = createImage(1, 1);
        emptyBoardGraphics = emptyBoardImage.getGraphics();
        emptyBoardGraphics.setClip(0, 0, 1, 1);

        boardImage = createImage(1, 1);
        boardGraphics = boardImage.getGraphics();
        boardGraphics.setClip(0, 0, 1, 1);
    }

    public void destroy() {

        if (emptyBoardGraphics != null) {
            emptyBoardGraphics.dispose();
            emptyBoardGraphics = null;
        }
        if (emptyBoardImage != null) {
            emptyBoardImage.flush();
            emptyBoardImage = null;
        }

        if (boardGraphics != null) {
            boardGraphics.dispose();
            boardGraphics = null;
        }
        if (boardImage != null) {
            boardImage.flush();
            boardImage = null;
        }

        if (showNewMovesAvailableTimer != null) {
            showNewMovesAvailableTimer.destroy();
        }
    }


    public void update(Graphics g) {
        paint(g);
    }

    public void refresh() {
        repaint();
    }

    public void myPaint(Graphics g, int width, int height) {
        setSize(width, height);
        emptyBoardGraphics = g;
        calculateGridSize();
        drawEmptyBoard(g);
        drawBoard(g);
    }

    public void paint(Graphics g) {

        if (emptyBoardGraphics != null) {
            try {
                synchronized (drawLock) {

                    Dimension size = getSize();
                    if (size.width != currentSize.width ||
                            size.height != currentSize.height) {

                        sizeChanged(size.width, size.height);
                        calculateGridSize();
                        emptyBoardDirty = true;
                        currentSize = size;
                    }

                    if (emptyBoardDirty) {
                        drawEmptyBoard(emptyBoardGraphics);
                        drawBoard(emptyBoardImage, boardGraphics);
                        g.setClip(0, 0, currentSize.width, currentSize.height);
                        g.drawImage(boardImage, 0, 0, this);
                    } else if (boardDirty) {
                        drawBoard(emptyBoardImage, boardGraphics);
                        g.setClip(0, 0, currentSize.width, currentSize.height);
                        g.drawImage(boardImage, 0, 0, this);
                    } else {
                        if (oldThinkingPiece.getX() >= 0 &&
                                oldThinkingPiece.getY() >= 0) {
                            int x = getStartX() + oldThinkingPiece.getX() * gridPieceSize;
                            int y = getStartY() + (gridHeight - oldThinkingPiece.getY() - 2) * gridPieceSize;

                            if (piecesOnGrid) {
                                x -= gridPieceSize / 2;
                                y += gridPieceSize / 2;
                            }
                            //g.setClip(x - 1, y - 1, x + gridPieceSize + 1, y + gridPieceSize + 1);
                            g.drawImage(boardImage, 0, 0, this);
                        }
                    }

                    g.setClip(0, 0, currentSize.width, currentSize.height);
                    // if the client wants to show thinking piece
                    // and thinking piece is on the board
                    if (showThinkingPiece &&
                            thinkingPiece.getX() >= 0 &&
                            thinkingPiece.getY() >= 0) {
                        drawPiece(g, thinkingPiece);
                    }

                    if (message != null && !hideMessage) {
                        int x = currentSize.width;
                        int y = getStartY() + (gridHeight - 2) * gridPieceSize;

                        g.setFont(MESSAGE_FONT);
                        FontMetrics fm = g.getFontMetrics(MESSAGE_FONT);
                        int mWidth = fm.stringWidth(message);
                        int mHeight = fm.getMaxAscent() +
                                fm.getLeading();

                        g.setColor(Color.white);
                        g.fillRect(x / 2 - mWidth / 2 - 10, y - mHeight / 2 - 10,
                                mWidth + 20, mHeight + 20);
                        g.setColor(Color.black);
                        g.drawRect(x / 2 - mWidth / 2 - 10, y - mHeight / 2 - 10,
                                mWidth + 20, mHeight + 20);
                        g.drawRect(x / 2 - mWidth / 2 - 9, y - mHeight / 2 - 9,
                                mWidth + 18, mHeight + 18);

                        x = (x / 2) - 100 + ((200 - mWidth) / 2);
                        y = y + mHeight / 2;
                        g.drawString(message, x, y);
                    }

                }
            } catch (Throwable t) {
                t.printStackTrace(System.err);
            }

        }
    }

    private Rectangle getMessageDimensions() {

        int x = currentSize.width;
        int y = getStartY() + (gridHeight - 2) * gridPieceSize;
        FontMetrics fm = getFontMetrics(MESSAGE_FONT);
        int mWidth = fm.stringWidth(message);
        int mHeight = fm.getMaxAscent() +
                fm.getLeading();

        return new Rectangle(x / 2 - mWidth / 2 - 10, y - mHeight / 2 - 10,
                mWidth + 20, mHeight + 20);
    }

    private void sizeChanged(int x, int y) {

        if (emptyBoardImage != null &&
                x != 0 && y != 0) {

            // need a bigger image
            Rectangle rec = emptyBoardGraphics.getClipBounds();
            if (rec != null &&
                    (rec.width < x ||
                            rec.height < y)) {
                if (emptyBoardGraphics != null) {
                    emptyBoardGraphics.dispose();
                    emptyBoardGraphics = null;
                }
                if (emptyBoardImage != null) {
                    emptyBoardImage.flush();
                    emptyBoardImage = null;
                }

                if (boardGraphics != null) {
                    boardGraphics.dispose();
                    boardGraphics = null;
                }
                if (boardImage != null) {
                    boardImage.flush();
                    boardImage = null;
                }

                emptyBoardImage = createImage(x, y);
                emptyBoardGraphics = emptyBoardImage.getGraphics();

                boardImage = createImage(x, y);
                boardGraphics = boardImage.getGraphics();
            }
            // use the same image but less of it
            else {
                emptyBoardGraphics.clearRect(0, 0, emptyBoardImage.getWidth(this), emptyBoardImage.getHeight(this));
                boardGraphics.clearRect(0, 0, boardImage.getWidth(this), boardImage.getHeight(this));
            }

            // set the clip to the current size
            emptyBoardGraphics.clipRect(0, 0, x, y);
            boardGraphics.clipRect(0, 0, x, y);
        }
    }

    protected void drawEmptyBoard(Graphics g) {
//System.out.println("drawEmptyBoard()");
        if (gridPieceSize < 0) {
            calculateGridSize();
        }

        drawEmptyBoardBackground(g);
        drawEmptyBoardGameName(g);
        drawEmptyBoardGrid(g);
        if (drawInnerCircles) {
            drawInnerCircles(g);
        } else if (drawGoDots) {
            drawGoDots(g);
        }
        if (drawCoordinates) {
            drawEmptyBoardCoordinates(g);
        }

        emptyBoardDirty = false;
    }

    protected int getStartX() {
        return insets.left + beveledEdge + coordinatesDimensions.width + edgeLeftOvers.width;
    }

    protected int getStartY() {
        return insets.top + beveledEdge + coordinatesDimensions.height + edgeLeftOvers.height;
    }

    public void calculateGridSize() {

        Dimension size = getSize();
//System.out.println("raw size = " + size);

        // get coordinates width/height
        if (drawCoordinates) {
            Font f = new Font("Helvetica", Font.PLAIN, 10);
            FontMetrics fm = emptyBoardGraphics.getFontMetrics(f);
            coordinatesDimensions.width = fm.stringWidth("10") + 2;
            coordinatesDimensions.height = fm.getAscent() + 2;
        } else {
            coordinatesDimensions.width = 0;
            coordinatesDimensions.height = 0;
        }
        // end coordinates

        // get gridpiecesize
        size.width -= (insets.left + insets.right + beveledEdge * 2 + coordinatesDimensions.width * 2);
        size.height -= (insets.top + insets.bottom + beveledEdge * 2 + coordinatesDimensions.height * 2);

//System.out.println("size after beveled and insets = "+size);

        int gridPieceSizeWidth = size.width / (gridWidth - 1);
        int gridPieceSizeHeight = size.height / (gridHeight - 1);

        gridPieceSize = gridPieceSizeWidth < gridPieceSizeHeight ?
                gridPieceSizeWidth : gridPieceSizeHeight;
        // end gridpiecesize

        // get edges left overs
        edgeLeftOvers.width = (size.width - gridPieceSize * (gridWidth - 1)) / 2;
        edgeLeftOvers.height = (size.height - gridPieceSize * (gridHeight - 1)) / 2;

//System.out.println("gridPieceSize="+gridPieceSize);
//System.out.println("edgeLeftOvers="+edgeLeftOvers);
    }

    private void drawEmptyBoardBackground(Graphics g) {
//System.out.println("drawEmptyBoardBackground()");
        Dimension size = getSize();
//System.out.println("size="+size);
        if (size.width == 0 || size.height == 0) {
            return;
        }

        g.setColor(backGroundColor);
        g.clearRect(0, 0, size.width, size.height);

        for (int i = 0; i < beveledEdge; i++) {
            g.fill3DRect(i, i, size.width - 2 * i, size.height - 2 * i, true);
        }
    }

    private void drawEmptyBoardGameName(Graphics g) {

        int gridSizePx = gridPieceSize * (gridWidth - 1);
        int gridSizePy = gridPieceSize * (gridHeight - 1);

        int fontSize = 32;
        int width, height;
        Font f;
        FontMetrics fm;

        while (true) {

            f = new Font("Arial", Font.BOLD, fontSize);
            fm = g.getFontMetrics(f);
            width = fm.stringWidth(gameName);
            if (width > gridSizePx) {
                break;
            }
            fontSize += 4;
        }

        f = new Font("Arial", Font.BOLD, fontSize - 4);
        fm = g.getFontMetrics(f);
        height = fm.getAscent();
        width = fm.stringWidth(gameName);

        int startX = getStartX();
        int startY = getStartY();
        int x = startX + gridSizePx / 2 - width / 2;
        int y = startY + gridSizePy / 2 + height / 3;

        g.setFont(f);
        g.setColor(gameNameColor);
        g.drawString(gameName, x, y);
    }

    private void drawEmptyBoardGrid(Graphics g) {

        Color middleColor = showNewMovesAvailable ? Color.red : Color.black;
        Color gridColor = showNewMovesAvailable ? Color.red : this.gridColor;

        int startX = getStartX();
        int startY = getStartY();
        int x = startX;
        int y = startY;

        boolean drawDifferentMiddleLine = gridWidth % 2 == 1 && gridHeight % 2 == 1;

        // draw vertical grid lines
        for (int i = 0; i < gridWidth; i++) {

            if (drawDifferentMiddleLine && i == gridWidth / 2) {
                g.setColor(middleColor);
            } else {
                g.setColor(gridColor);
            }

            g.drawLine(x, y, x, startY + gridPieceSize * (gridHeight - 1));
            x += gridPieceSize;
        }

        x = startX;
        y = startY;

        // draw horizontal grid lines
        for (int i = 0; i < gridHeight; i++) {

            if (drawDifferentMiddleLine && i == gridHeight / 2) {
                g.setColor(middleColor);
            } else {
                g.setColor(gridColor);
            }

            g.drawLine(x, y, startX + gridPieceSize * (gridWidth - 1), y);
            y += gridPieceSize;
        }
    }

    private void drawInnerCircles(Graphics g) {

        Color gridColor = showNewMovesAvailable ? Color.red : this.gridColor;
        g.setColor(gridColor);

        int distanceFromCenter = 3;
        int halfGridPieceSize = gridPieceSize / 2;
        int offsetFromX = (getGridWidth() / 2 - distanceFromCenter) * gridPieceSize - gridPieceSize / 4;
        int offsetFromY = (getGridHeight() / 2 - distanceFromCenter) * gridPieceSize - gridPieceSize / 4;

        int x = getStartX() + offsetFromX;
        int y = getStartY() + offsetFromY;

        g.drawOval(x, y, halfGridPieceSize, halfGridPieceSize);
        x += distanceFromCenter * 2 * gridPieceSize;
        g.drawOval(x, y, halfGridPieceSize, halfGridPieceSize);
        y += distanceFromCenter * 2 * gridPieceSize;
        g.drawOval(x, y, halfGridPieceSize, halfGridPieceSize);
        x -= distanceFromCenter * 2 * gridPieceSize;
        g.drawOval(x, y, halfGridPieceSize, halfGridPieceSize);
        x += distanceFromCenter * gridPieceSize;
        y -= distanceFromCenter * gridPieceSize;
        g.drawOval(x, y, halfGridPieceSize, halfGridPieceSize);
    }

    private void drawGoDots(Graphics g) {

        Color gridColor = showNewMovesAvailable ? Color.red : this.gridColor;
        g.setColor(gridColor);

        int distanceFromCenter = 6;
        if (gridWidth == 13) {
            distanceFromCenter = 3;
        } else if (gridWidth == 9) {
            distanceFromCenter = 2;
        }
        int halfGridPieceSize = gridPieceSize / 4;
        int offsetFromX = (getGridWidth() / 2 - distanceFromCenter) * gridPieceSize - halfGridPieceSize / 2;
        int offsetFromY = (getGridHeight() / 2 - distanceFromCenter) * gridPieceSize - halfGridPieceSize / 2;

        int x = getStartX() + offsetFromX;
        int y = getStartY() + offsetFromY;

        g.fillOval(x, y, halfGridPieceSize, halfGridPieceSize);
        if (gridWidth != 9) {
            g.fillOval(x + distanceFromCenter * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);
        }
        g.fillOval(x + distanceFromCenter * 2 * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);

        y += distanceFromCenter * gridPieceSize;
        g.fillOval(x + distanceFromCenter * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);
        if (gridWidth != 9) {
            g.fillOval(x, y, halfGridPieceSize, halfGridPieceSize);
            g.fillOval(x + distanceFromCenter * 2 * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);
        }

        y += distanceFromCenter * gridPieceSize;
        g.fillOval(x, y, halfGridPieceSize, halfGridPieceSize);
        if (gridWidth != 9) {
            g.fillOval(x + distanceFromCenter * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);
        }
        g.fillOval(x + distanceFromCenter * 2 * gridPieceSize, y, halfGridPieceSize, halfGridPieceSize);
    }

    private void drawEmptyBoardCoordinates(Graphics g) {

        int fontSize = 8;
        if (gridPieceSize > 14) {
            fontSize += 2;
        }
        if (gridPieceSize > 24) {
            fontSize += 2;
        }

        Font f = new Font("Helvetica", Font.PLAIN, fontSize);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int height = fm.getAscent();

        g.setColor(Color.gray);

        int startX = getStartX();
        int startY = getStartY();
        int x = startX;
        int y = startY - 1;

        String coordsX[] = gridCoordinates.getXCoordinates();
        for (int j = 0; j < 2; j++) {
//            for (int i = 0; i < coordsX.length; i++) {
            for (int i = 0; i < gridWidth; i++) {

                String h = coordsX[i];
                int x2 = x + gridPieceSize * i;

                if (piecesOnGrid) {
                    if (i == gridWidth - 1) {
                        x2 -= fm.stringWidth(h);
                    } else if (i != 0) {
                        x2 -= fm.stringWidth(h) / 2;
                    }
                } else {
                    x2 += gridPieceSize / 2 - fm.stringWidth(h) / 2;
                }
                g.drawString(h, x2, y);
            }

            y = startY + gridPieceSize * (gridHeight - 1) + height;
        }

        x = startX;
        y = startY;

        String coordsY[] = gridCoordinates.getYCoordinates();
        for (int j = 0; j < 2; j++) {
//            for (int i = 0; i < coordsY.length; i++) {
            for (int i = 0; i < gridHeight; i++) {

                String v = coordsY[i];
                int y2 = y + gridPieceSize * (gridHeight - i - 1);

                if (piecesOnGrid) {
                    if (i == gridHeight - 1) {
                        y2 += height;
                    } else if (i != 0) {
                        y2 += height / 2;
                    }
                } else {
                    y2 += height / 2 + gridPieceSize / 2;
                }

                int x2 = x;
                if (j == 0) {
                    x2 -= fm.stringWidth(v) + 1;
                } else {
                    x2 += 1;
                }

                g.drawString(v, x2, y2);
            }

            x = startX + gridPieceSize * (gridWidth - 1);
        }
    }

    void drawBoard(Graphics boardGraphics) {
        // draw highlight piece below other pieces
        if (gameOptions.getShowLastMove()) {
            for (int i = gridPieces.size() - 1; i >= 0; i--) {
                GridPiece piece = (GridPiece) gridPieces.elementAt(i);
                if (piece.getX() < 0 || piece.getY() < 0 || piece.getX() >= gridWidth || piece.getY() >= gridWidth) {
                    continue;
                }
                if (piece == highlightPiece) {
                    drawPiece(boardGraphics, piece);
                    break;
                }
            }
        }

        // loop through pieces and draw on board
        for (int i = gridPieces.size() - 1; i >= 0; i--) {
            GridPiece piece = (GridPiece) gridPieces.elementAt(i);

            if (gameOptions.getShowLastMove() && piece == highlightPiece) {
                continue;
            }
            if (piece.getX() < 0 || piece.getY() < 0 || piece.getX() >= gridWidth || piece.getY() >= gridWidth) {
                continue;
            }

            drawPiece(boardGraphics, piece);
        }

        if (goTerritory != null) {
            List<Integer> territory = goTerritory.get(1);
            int x, y;
            if (territory != null) {
                for (int pos : territory) {
                    x = pos % gridWidth;
                    y = pos / gridWidth;
                    fillRect(boardGraphics, x, y, gridPieceSize / 3, Color.BLACK);
                }
            }
            territory = goTerritory.get(2);
            if (territory != null) {
                for (int pos : territory) {
                    x = pos % gridWidth;
                    y = pos / gridWidth;
                    fillRect(boardGraphics, x, y, gridPieceSize / 3, Color.WHITE);
                }
            }
        }
    }

    private void drawBoard(Image emptyBoardImage, Graphics boardGraphics) {
//System.out.println("drawBoard()");
        boardGraphics.drawImage(emptyBoardImage, 0, 0, this);

        drawBoard(boardGraphics);

        boardDirty = false;
    }

    private void drawPiece(Graphics g, GridPiece p) {

        Color c[] = GameStyles.colors[gameOptions.getPlayerColor(p.getPlayer())];

        Color localHighlightColor = null;
        if (gameOptions.getShowLastMove() && p == highlightPiece) {
            if (c[0].getAlpha() != GameStyles.transparency) {
                localHighlightColor = highlightColor;
            } else {
                localHighlightColor = transparentHighlightColor;
            }
        }

        int x = getStartX() + p.getX() * gridPieceSize;
        int y = getStartY() + (gridHeight - p.getY() - 2) * gridPieceSize;

        if (piecesOnGrid) {
            x -= gridPieceSize / 2;
            y += gridPieceSize / 2;
        }

        if (gameOptions.getDraw3DPieces()) {
            draw3DPiece(g, new Point(x, y), c, localHighlightColor, gridPieceSize);
        } else {
            draw2DPiece(g, new Point(x, y), c[1], localHighlightColor, gridPieceSize);
        }
    }

    void draw2DPiece(Graphics g, Point p, Color c, Color highlightColor, int r) {

        int width = r < 30 ? r + 3 : r + 5;
        int offset = r < 30 ? 1 : 2;
        if (highlightColor != null) {
            fillOval(g, p.x - offset, p.y - offset, width, highlightColor);
        }

        fillOval(g, p.x, p.y, r, Color.black);  // black
        fillOval(g, p.x + 1, p.y + 1, r - 2, c);  // player color
    }

    void draw3DPiece(Graphics g, Point p, Color c[], Color highlightColor, int r) {

        int width = r < 30 ? r + 4 : r + 6;
        int offset = r < 30 ? 1 : 2;

        if (highlightColor != null) {
            fillOval(g, p.x - offset, p.y - offset, width, highlightColor); // highlight
        }

        int darkWidth = r - 1;
        int lightWidth = (int) ((double) darkWidth) * 5 / 6;
        int reflectionWidth = (darkWidth < 18) ? 4 : 6;
        int reflectionOffset = darkWidth / 4;

        if (c[0].getAlpha() != GameStyles.transparency) {
            fillOval(g, p.x + 2, p.y + 2, darkWidth, shadowColor); // shadow
        } else {
            fillOval(g, p.x + 2, p.y + 2, darkWidth, transparentShadowColor); // shadow
        }
        fillOval(g, p.x, p.y, darkWidth, c[0]);  // dark
        fillOval(g, p.x + 1, p.y + 1, lightWidth, c[1]); // light
        fillOval(g, p.x + reflectionOffset, p.y + reflectionOffset, reflectionWidth, Color.white); // reflection
    }

    private void fillOval(Graphics g, int x, int y, int r, Color c) {
        g.setColor(c);
        g.fillOval(x, y, r, r);
    }

    private void fillRect(Graphics g, int x, int y, int w, Color c) {
        g.setColor(c);
        x = getStartX() + x * gridPieceSize - w / 2;
        y = getStartY() + y * gridPieceSize - w / 2;
        g.fillRect(x, y, w, w);
    }

    private Point getGridMove(int x, int y) {
        x -= getStartX();
        y -= getStartY();
        y += gridPieceSize;

        if (y < 0) {
            return null;
        }

        if (piecesOnGrid) {
            x += gridPieceSize / 2;
            y -= gridPieceSize / 2;
        }
        x /= gridPieceSize;
        y /= gridPieceSize;
        y = gridHeight - 1 - y;

        int piecesOnGridOffset = (piecesOnGrid) ? 0 : 1;
        if (x >= 0 && x < gridWidth - piecesOnGridOffset &&
                y >= 0 && y < gridHeight - piecesOnGridOffset) {
            return new Point(x, y);
        } else {
            return null;
        }
    }

    // mouse click handling code to notify move listeners
    class MoveEventGenerator extends MouseAdapter {

        // listen for mouse presses instead of mouse clicks
        // mouse clicks are only registered when click down/up in same
        // place, doesn't always happen when moves are being made quickly
        public void mousePressed(MouseEvent e) {

            boolean gridClicked = false;
            Point gridMove = null;

            synchronized (drawLock) {

                gridMove = getGridMove(e.getX(), e.getY());
                if (gridMove != null) {
                    gridClicked = true;
                }
            }

            if (gridClicked) {
                for (int i = 0; i < listeners.size(); i++) {
                    GridBoardListener l = (GridBoardListener) listeners.elementAt(i);

                    // which button pressed changed in 1.4
                    l.gridClicked(gridMove.x, gridMove.y, e.getModifiers());
                }
            }
        }
    }

    class ThinkingPieceMoveGenerator extends MouseMotionAdapter {

        public void mouseMoved(MouseEvent e) {

            boolean gridMoved = false;
            Point gridMove = null;

            synchronized (drawLock) {
                gridMove = getGridMove(e.getX(), e.getY());
                if (gridMove == null) {
                    if (thinkingPiece.getX() != -1 || thinkingPiece.getY() != -1) {

                        oldThinkingPiece.setX(thinkingPiece.getX());
                        oldThinkingPiece.setY(thinkingPiece.getY());

                        thinkingPiece.setX(-1);
                        thinkingPiece.setY(-1);
                        repaint();
                    }
                } else {
                    if (gridMove.x != thinkingPiece.getX() ||
                            gridMove.y != thinkingPiece.getY()) {

                        oldThinkingPiece.setX(thinkingPiece.getX());
                        oldThinkingPiece.setY(thinkingPiece.getY());

                        thinkingPiece.setX(gridMove.x);
                        thinkingPiece.setY(gridMove.y);

                        gridMoved = true;

                        repaint();
                    }
                }

                if (message != null) {
                    Rectangle r = getMessageDimensions();
                    if (!hideMessage && r.contains(e.getX(), e.getY())) {
                        hideMessage = true;
                        repaint();
                    } else if (hideMessage && !r.contains(e.getX(), e.getY())) {
                        hideMessage = false;
                        repaint();
                    }
                }
            }

            // moved out of synchronized block due to deadlock
            if (gridMoved) {
                for (int i = 0; i < listeners.size(); i++) {
                    GridBoardListener l = (GridBoardListener) listeners.elementAt(i);
                    l.gridMoved(gridMove.x, gridMove.y);
                }
            }
        }
    }

    public void setCursor(int cursor) {
        setCursor(Cursor.getPredefinedCursor(cursor));
    }
}