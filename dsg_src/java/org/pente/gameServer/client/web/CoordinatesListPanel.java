/**
 * CoordinatesListPanel.java
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

import org.pente.game.GridStateFactory;
import org.pente.gameServer.client.*;
import org.pente.gameServer.core.GridCoordinates;
import org.pente.gameServer.core.GridCoordinatesChangeListener;
import org.pente.gameServer.core.GridPiece;
import org.pente.gameServer.core.OrderedPieceCollection;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

public class CoordinatesListPanel extends Panel
        implements CoordinatesListComponent,
        OrderedPieceCollection,
        GridCoordinatesChangeListener,
        GameOptionsChangeListener {

    private String players[];

    private int game;

    private Vector gridPieces;
    private Object drawLock;

    private int currentTurn;
    private boolean viewingCurrent;

    private int startRow;
    private int headerHeight;
    private int rowHeight;
    private int visibleRows;
    private Dimension minSize;
    private Dimension currentSize;
    private boolean scrollbarVisible;

    private boolean imageDirty;

    private Image coordsImage;
    private Graphics coordsGraphics;

    private Scrollbar scrollbar;
    private Panel navigatePanel;

    private Color highlightColor;

    private static final int numberOffset = 20;
    private static final int minRows = 15;
    private static final int offsetToButtons = 0;

    private static final Font playerFont = new Font("Arial", Font.PLAIN, 12);
    private static final Font coordsFont = new Font("Arial", Font.PLAIN, 12);

    private GameOptions gameOptions;
    private GridCoordinates coordinates;

    private Vector listeners;

    public CoordinatesListPanel(GameStyles gameStyle, int maxPlayers,
                                DSGButton buttonMaker) {


        players = new String[maxPlayers + 1];
        gridPieces = new Vector();
        drawLock = new Object();

        currentTurn = 0;
        startRow = 0;
        visibleRows = 0;
        viewingCurrent = true;

        minSize = new Dimension(0, 0);
        currentSize = new Dimension(0, 0);
        imageDirty = true;
        highlightColor = Color.blue;

        listeners = new Vector();
        addOrderedPieceCollectionVisitListener(this);

        // create scrollbar, add it when necessary later
        setLayout(new BorderLayout(0, offsetToButtons + 2));
        scrollbar = new Scrollbar(Scrollbar.VERTICAL);
        scrollbar.setUnitIncrement(1);
        scrollbar.setBlockIncrement(1);
        scrollbar.setMinimum(0);
        scrollbar.setValue(0);
        scrollbar.setVisibleAmount(1);

        // add listener to update startRow when scrollbar is adjusted
        scrollbar.addAdjustmentListener(e -> {
            synchronized (drawLock) {
                // set start row to current value of scrollbar not events
                // value in case another thread updated scrollbar's value
                // between the time the user clicked and this event was
                // received
                startRow = scrollbar.getValue();
                if (startRow < 0) startRow = 0;
                imageDirty = true;
            }
            requestFocus();
            repaint();
        });

        // add navigation buttons
        DSGButton firstButton = buttonMaker.createButton("<<", gameStyle);
        firstButton.addActionListener(e -> {
            for (int i = 0; i < listeners.size(); i++) {
                OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
                c.visitFirstTurn();
            }
            // visit first turn goes back before K10, so
            // visit next turn to get to K10
            //for (int i = 0; i < listeners.size(); i++) {
            //    OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
            //    c.visitNextTurn();
            //}
        });
        DSGButton backButton = buttonMaker.createButton("<", gameStyle);
        backButton.addActionListener(e -> {
            for (int i = 0; i < listeners.size(); i++) {
                OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
                c.visitPreviousTurn();
            }
        });
        DSGButton nextButton = buttonMaker.createButton(">", gameStyle);
        nextButton.addActionListener(e -> {
            for (int i = 0; i < listeners.size(); i++) {
                OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
                c.visitNextTurn();
            }
        });
        DSGButton lastButton = buttonMaker.createButton(">>", gameStyle);
        lastButton.addActionListener(e -> {
            for (int i = 0; i < listeners.size(); i++) {
                OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
                c.visitLastTurn();
            }
        });

        navigatePanel = new Panel();
        navigatePanel.setLayout(new GridLayout(1, 4, 0, 0));
        navigatePanel.add(firstButton.getButton());
        navigatePanel.add(backButton.getButton());
        navigatePanel.add(nextButton.getButton());
        navigatePanel.add(lastButton.getButton());
        add(navigatePanel, "South");

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {

                synchronized (drawLock) {
                    int halfWidth = 0;
                    if (scrollbarVisible) {
                        halfWidth = (getSize().width - scrollbar.getSize().width - numberOffset) / 2;
                    } else {
                        halfWidth = (getSize().width - numberOffset) / 2;
                    }
                    if (e.getX() < numberOffset) {
                        return;
                    }
                    int x = e.getX() < numberOffset + halfWidth ? 1 : 2;
                    int y = e.getY() - headerHeight;
                    if (y < 0) {
                        return;
                    }
                    y /= rowHeight;
                    int turn = getCurrentTurn(x, y);

                    if (turn > gridPieces.size()) {
                        return;
                    }

                    for (int i = 0; i < listeners.size(); i++) {
                        OrderedPieceCollection c = (OrderedPieceCollection) listeners.elementAt(i);
                        c.visitTurn(turn);
                    }
                }
            }
        });
    }


    // CoordinatesListComponent
    public void setGame(int game) {
        this.game = game;
    }

    public void setPlayer(int playerNum, String playerName) {
        synchronized (drawLock) {
            players[playerNum] = playerName;
            imageDirty = true;
        }

        repaint();
    }

    public void removePlayer(String playerName) {
        synchronized (drawLock) {
            for (int i = 0; i < players.length; i++) {
                if (players[i] != null && players[i].equals(playerName)) {
                    players[i] = null;
                    imageDirty = true;
                    break;
                }
            }
        }

        repaint();
    }

    public void setHighlightColor(Color color) {
        highlightColor = color;
    }

    public void addOrderedPieceCollectionVisitListener(OrderedPieceCollection collection) {
        listeners.addElement(collection);
    }

    public void removeOrderedPieceCollectionVisitListener(OrderedPieceCollection collection) {
        listeners.removeElement(collection);
    }
    // end CoordinatesListComponent

    // GridCoordinatesChangeListener
    public void gridCoordinatesChanged(GridCoordinates gridCoordinates) {
        synchronized (drawLock) {
            this.coordinates = gridCoordinates;
            imageDirty = true;
        }

        repaint();
    }
    // end GridCoordinatesChangeListener

    // GameOptionsChangeListener
    public void gameOptionsChanged(GameOptions gameOptions) {
        synchronized (drawLock) {
            this.gameOptions = gameOptions;
            imageDirty = true;
        }

        repaint();
    }
    // end GameOptionsChangeListener

    // OrderedPieceCollection
    public void addPiece(GridPiece gridPiece, int turn) {

        synchronized (drawLock) {
            gridPieces.addElement(gridPiece);

            updateScrollbarMaximum();

            if (viewingCurrent) {
                currentTurn++;
            }
            updateStartRow();
            scrollbar.setValue(startRow);

            // add scrollbar if needed
            if (!scrollbarVisible && getNumRows() > visibleRows) {
                scrollbarVisible = true;
                add(scrollbar, "East");
                validate();
            }

            imageDirty = true;
        }

        repaint();
    }

    public void removePiece(GridPiece gridPiece, int turn) {
        // do nothing
    }

    public void undoLastTurn() {

        synchronized (drawLock) {
            if (!gridPieces.isEmpty()) {
                gridPieces.removeElementAt(gridPieces.size() - 1);

                updateScrollbarMaximum();

                if (viewingCurrent) {
                    currentTurn--;
                } else if (currentTurn == gridPieces.size()) {
                    viewingCurrent = true;
                }

                updateStartRow();
                scrollbar.setValue(startRow);

                // remove scrollbar if not needed
                if (scrollbarVisible && getNumRows() <= visibleRows) {
                    scrollbarVisible = false;
                    remove(scrollbar);
                    validate();
                }

                imageDirty = true;
                repaint();
            }
        }
    }

    public void clearPieces() {

        synchronized (drawLock) {
            if (!gridPieces.isEmpty()) {
                gridPieces.removeAllElements();
                currentTurn = 0;
                startRow = 0;
                updateScrollbarMaximum();
                scrollbar.setValue(startRow);
                scrollbarVisible = false;
                remove(scrollbar);
                viewingCurrent = true;
                imageDirty = true;
                validate();
                repaint();
            }
        }
    }

    public void visitNextTurn() {

        synchronized (drawLock) {

            if (currentTurn < gridPieces.size()) {
                currentTurn++;
            }

            if (currentTurn == gridPieces.size()) {
                viewingCurrent = true;
            }

            updateStartRow();
            scrollbar.setValue(startRow);

            imageDirty = true;
            repaint();
        }
    }

    public void visitPreviousTurn() {

        synchronized (drawLock) {

            if (currentTurn > 0) {
                currentTurn--;
            }

            updateStartRow();
            scrollbar.setValue(startRow);

            imageDirty = true;
            viewingCurrent = false;
            repaint();
        }
    }

    public void visitFirstTurn() {

        synchronized (drawLock) {
            currentTurn = 0;
            startRow = 0;
            scrollbar.setValue(startRow);
            imageDirty = true;
            viewingCurrent = false;
            repaint();
        }
    }

    public void visitLastTurn() {

        synchronized (drawLock) {
            currentTurn = gridPieces.size();
            updateStartRow();
            scrollbar.setValue(startRow);
            imageDirty = true;
            viewingCurrent = true;
            repaint();
        }
    }

    public void visitTurn(int turn) {

        synchronized (drawLock) {
            if (turn < 0 || turn > gridPieces.size()) {
                return;
            }
            currentTurn = turn;
            updateStartRow();
            scrollbar.setValue(startRow);
            imageDirty = true;
            if (currentTurn == gridPieces.size()) {
                viewingCurrent = true;
            } else {
                viewingCurrent = false;
            }
            repaint();
        }
    }

    // end OrderedPieceCollection

    // Canvas
    public void addNotify() {
        super.addNotify();

        calculateFontMetrics();
        coordsImage = createImage(minSize.width, minSize.height);
        coordsGraphics = coordsImage.getGraphics();
        coordsGraphics.setClip(0, 0, minSize.width, minSize.height);
        setSize(getMinimumSize());
    }

    public void destroy() {
        if (coordsGraphics != null) {
            coordsGraphics.dispose();
            coordsGraphics = null;
        }
        if (coordsImage != null) {
            coordsImage.flush();
            coordsImage = null;
        }
    }

    private void calculateFontMetrics() {

        FontMetrics fontMetrics = getFontMetrics(playerFont);
        headerHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getMaxDescent() +
                fontMetrics.getLeading();

        fontMetrics = getFontMetrics(coordsFont);
        rowHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getLeading();

        int width = fontMetrics.stringWidth("AAAAAAAAAA") * 2 + numberOffset;
        int height = headerHeight + rowHeight * minRows + offsetToButtons;
        minSize = new Dimension(width, height);
    }

    public Dimension getPreferredSize() {

        int w = navigatePanel.getPreferredSize().width;

        int rows = getNumRows();
        if (rows < minRows) {
            rows = minRows;
        }

        int height = headerHeight + rows * rowHeight + offsetToButtons;
        int width = Math.max(w, minSize.width);
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }

        return new Dimension(width, height + navigatePanel.getSize().height);

    }

    public Dimension getMinimumSize() {

        int w = navigatePanel.getMinimumSize().width;

        int width = Math.max(w, minSize.width);
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }

        return new Dimension(width, minSize.height + navigatePanel.getSize().height);

    }

    public void setSize(Dimension size) {
        super.setSize(size);
        imageDirty = true;
        repaint();
    }

    public void setSize(int x, int y) {
        super.setSize(x, y);
        imageDirty = true;
        repaint();
    }

    // overridden to make scrollbar not extend into player names section
    public Insets getInsets() {
        // this was causing deadlock?
        //synchronized (drawLock) {
        return new Insets(headerHeight + 2, 0, 0, 2);
        //}
    }

    private void sizeChanged(int x, int y) {

        synchronized (drawLock) {

            calculateFontMetrics();

            visibleRows = (y - navigatePanel.getSize().height - headerHeight - offsetToButtons - 5) / rowHeight;

            if (visibleRows <= 0) {
                visibleRows = minRows;
            }
            updateScrollbarMaximum();
            // need to reset startRow here in case visibleRows has
            // expanded or contracted
            updateStartRow();
            scrollbar.setValue(startRow);

            if (coordsImage != null &&
                    x != 0 && y != 0) {

                // need a bigger image
                Rectangle rec = coordsGraphics.getClipBounds();
                if (rec != null &&
                        (rec.width < x ||
                                rec.height < y)) {
                    destroy();
                    coordsImage = createImage(x, y);
                    coordsGraphics = coordsImage.getGraphics();
                }
                // use the same image but less of it
                else {
                    coordsGraphics.clearRect(0, 0, coordsImage.getWidth(this), coordsImage.getHeight(this));
                }

                // set the clip to the current size
                coordsGraphics.clipRect(0, 0, x, y);
            }

            // add scrollbar if needed
            if (!scrollbarVisible && getNumRows() > visibleRows) {
                scrollbarVisible = true;
                add(scrollbar, "East");
            }
            // remove scrollbar if not needed
            else if (scrollbarVisible && getNumRows() <= visibleRows) {
                scrollbarVisible = false;
                remove(scrollbar);
            }

            imageDirty = true;
        }

        // resizes scrollbar if necessary
        validate();
        repaint();
    }

    public void update(Graphics g) {

        if (coordsGraphics != null) {
            paint(g);
        }
    }

    public void paint(Graphics g) {

        if (coordsGraphics != null) {
            try {
                synchronized (drawLock) {
                    if (imageDirty) {
                        Dimension size = getSize();
                        if (size.width != currentSize.width ||
                                size.height != currentSize.height) {
                            sizeChanged(size.width, size.height);
                            currentSize = size;
                        }
                        drawCoords(coordsGraphics);
                        imageDirty = false;
                    }
                }

                g.drawImage(coordsImage, 0, 0, this);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    void drawCoords(Graphics g) {

        int width = getSize().width;
        int totalWidth = width;
        if (scrollbarVisible) {
            width -= scrollbar.getSize().width;
        }

        int height = getSize().height - navigatePanel.getSize().height - offsetToButtons;
        int halfWidth = (width - numberOffset) / 2;
        g.setColor(Color.white);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.black);
        g.drawLine(0, headerHeight, totalWidth - 1, headerHeight);
        g.drawLine(numberOffset + halfWidth, 0, numberOffset + halfWidth, height - 3);
        g.drawLine(numberOffset, 0, numberOffset, height - 3);

        int p1c = gameOptions.getPlayerColor(1);
        int p2c = gameOptions.getPlayerColor(2);
        Color player1Color = GameStyles.colors[p1c][1];
        Color player2Color = GameStyles.colors[p2c][1];
        g.setColor(player1Color);
        g.fillRect(numberOffset + 1, 1, halfWidth - 1, headerHeight - 1);

        g.setColor(player2Color);
        g.fillRect(numberOffset + halfWidth + 1, 1, totalWidth - halfWidth - 1, headerHeight - 1);

        g.setFont(playerFont);
        if (players[1] != null) {
            g.setColor(p1c == GameOptions.WHITE || p1c == GameOptions.YELLOW ? Color.black : Color.white);
            g.setClip(numberOffset + 1, 1, halfWidth - 1, headerHeight - 1);
            g.drawString(players[1], numberOffset + 5, headerHeight - 1);
        }

        if (players[2] != null) {
            g.setColor(p2c == GameOptions.WHITE || p2c == GameOptions.YELLOW ? Color.black : Color.white);
            g.setClip(numberOffset + halfWidth + 1, 1, totalWidth - halfWidth - 1, headerHeight - 1);
            g.drawString(players[2], numberOffset + halfWidth + 5, headerHeight - 1);
        }

        g.setClip(0, 0, width, height);

        // shaded border
        g.setColor(new Color(128, 128, 128));
        g.drawLine(0, 0, 0, height - 2);
        g.drawLine(0, 0, totalWidth - 2, 0);

        g.setColor(Color.black);
        g.drawLine(1, 1, 1, height - 3);
        g.drawLine(1, 1, totalWidth - 3, 1);

        g.setColor(Color.white);
        g.drawLine(0, height - 1, totalWidth - 1, height - 1);
        g.drawLine(totalWidth - 1, height - 1, totalWidth - 1, 0);

        g.setColor(new Color(223, 223, 223));
        g.drawLine(1, height - 2, totalWidth - 2, height - 2);
        g.drawLine(totalWidth - 2, height - 2, totalWidth - 2, 1);
        // end shaded border


        g.setFont(coordsFont);
        int endRow = getEndRow();
        for (int i = startRow, rowNum = 0; i < endRow; i++, rowNum++) {

            // draw the move number
            g.setColor(Color.black);
            g.drawString(Integer.toString(i + 1), 3, headerHeight + (rowNum + 1) * rowHeight);

            // if this move is the current move
            // we're viewing, highlight it
            if (getCurrentRow() == i + 1 && getCurrentPlayer() == 1) {
                g.setColor(highlightColor);
                g.fillRect(numberOffset + 1, headerHeight + rowNum * rowHeight + 1, halfWidth - 2, rowHeight);
                g.setColor(Color.black);
                g.drawRect(numberOffset + 1, headerHeight + rowNum * rowHeight + 1, halfWidth - 2, rowHeight);
                g.setColor(Color.white);
            } else {
                g.setColor(Color.black);
            }

            // draw player 1 move
            String move = getTurnText(i, 1);
            g.drawString(move, numberOffset + 5, headerHeight + (rowNum + 1) * rowHeight);

            move = getTurnText(i, 2);
            // if there is a move by player 2 draw it
            if (!move.equals("")) {

                // if this move is the current move
                // we're viewing, highlight it
                if (getCurrentRow() == i + 1 && getCurrentPlayer() == 2) {
                    g.setColor(highlightColor);
                    g.fillRect(numberOffset + halfWidth, headerHeight + rowNum * rowHeight + 1, halfWidth - 3, rowHeight);
                    g.setColor(Color.black);
                    g.drawRect(numberOffset + halfWidth, headerHeight + rowNum * rowHeight + 1, halfWidth - 3, rowHeight);
                    g.setColor(Color.white);
                } else {
                    g.setColor(Color.black);
                }

                g.drawString(move, numberOffset + halfWidth + 5, headerHeight + (rowNum + 1) * rowHeight);
            }
        }
    }
    // end Canvas

    public int getCurrentTurn() {
        return currentTurn;
    }

    // y is 0 based
    // x = 1 or 2
    private int getCurrentTurn(int x, int y) {
        if (game == GridStateFactory.CONNECT6 ||
                game == GridStateFactory.SPEED_CONNECT6 ||
                game == GridStateFactory.TB_CONNECT6) {

            if (startRow == 0 && x == 1 && y == 0) return 1;
            int t = 0;
            if (startRow > 0) {
                t = startRow * 4;
            }
            t += y * 4 + (x - 1) * 2;
            return t;
        } else {
            return startRow * 2 + y * 2 + x;
        }
    }

    // private convenience methods
    private int getCurrentRow() {
        if (currentTurn == 0) {
            return 0;
        } else {

            if (game == GridStateFactory.CONNECT6 ||
                    game == GridStateFactory.SPEED_CONNECT6 ||
                    game == GridStateFactory.TB_CONNECT6) {
                if (currentTurn == 0) return 0;
                else return currentTurn / 4 + 1;
            } else {
                return currentTurn / 2 - 1 + currentTurn % 2 + 1;
            }
        }
    }

    private int getCurrentPlayer() {
        if (currentTurn == 0) {
            return 0;
        } else {

            if (game == GridStateFactory.CONNECT6 ||
                    game == GridStateFactory.SPEED_CONNECT6 ||
                    game == GridStateFactory.TB_CONNECT6) {
                return (currentTurn / 2) % 2 + 1;
            } else {
                return (currentTurn - 1) % 2 + 1;
            }
        }
    }

    private int getNumRows() {
        if (gridPieces.isEmpty()) {
            return 0;
        } else {
            if (game == GridStateFactory.CONNECT6 ||
                    game == GridStateFactory.SPEED_CONNECT6 ||
                    game == GridStateFactory.TB_CONNECT6) {
                if (gridPieces.isEmpty()) return 0;
                else return gridPieces.size() / 4 + 1;
            } else {
                return gridPieces.size() / 2 + gridPieces.size() % 2;
            }
        }
    }

    private int getEndRow() {

        int endRow = startRow + visibleRows;
        int actualEndRow = 0;
        if (game == GridStateFactory.CONNECT6 ||
                game == GridStateFactory.SPEED_CONNECT6 ||
                game == GridStateFactory.TB_CONNECT6) {
            if (gridPieces.isEmpty()) actualEndRow = 0;
            else actualEndRow = gridPieces.size() / 4 + 1;
        } else {
            actualEndRow = gridPieces.size() / 2 + gridPieces.size() % 2;
        }

        if (endRow > actualEndRow) {
            endRow = actualEndRow;
        }

        return endRow;
    }

    private void updateStartRow() {
        int currentRow = getCurrentRow();

        if (currentRow < startRow) {
            startRow = currentRow;
        } else if (currentRow >= (startRow + visibleRows)) {
            startRow = currentRow - visibleRows;
        }

        int endRow = getEndRow();
        if (endRow - startRow < visibleRows) {
            startRow = endRow - visibleRows;
        }
        if (startRow < 0) {
            startRow = 0;
        }
    }

    private void updateScrollbarMaximum() {

        int max = getNumRows() - visibleRows + 1;
        if (max < 0) {
            max = 0;
        }
        scrollbar.setMaximum(max);
    }

    private String getTurnText(int row, int player) {

        if (game == GridStateFactory.CONNECT6 ||
                game == GridStateFactory.SPEED_CONNECT6 ||
                game == GridStateFactory.TB_CONNECT6) {

            if (row == 0) {
                if (player == 1) {
                    return getMoveText(0);
                } else {
                    String s1 = getMoveText(1);
                    String s2 = getMoveText(2);
                    if (!s2.equals("")) {
                        s1 += "-" + s2;
                    }
                    return s1;
                }
            } else {
                int i = 3 + (row - 1) * 4 + (player - 1) * 2;
                String s1 = getMoveText(i);
                String s2 = getMoveText(i + 1);
                if (!s2.equals("")) {
                    s1 += "-" + s2;
                }
                return s1;
            }
        } else {
            int i = 2 * row + player - 1;
            return getMoveText(i);
        }
    }

    private String getMoveText(int moveNum) {
        if (gridPieces.size() <= moveNum) {
            return "";
        } else {
            GridPiece p = (GridPiece) gridPieces.elementAt(moveNum);
            return coordinates.getCoordinate(p.getX(), p.getY());
        }
    }
    // end convenience
}