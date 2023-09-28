/**
 * GridBoardCanvasTest.java
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

package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;

/** Tests the synchronization involved with grid board canvas by
 *  creating a bunch of threads that add and undo moves and other
 *  threads to resize the window in a loop, change the color of
 *  pieces in a loop, etc.  This test helped to find the deadlock
 *  bugs that occurred frequently, hopefully they are all gone now!
 */
public class GridBoardCanvasTest {

    private static final boolean resize = false;
    private static final boolean moveMouse = true;
    private static final boolean addMoves = true;
    private static final boolean changeThinkingPiece = true;
    private static final boolean changeColors = true;

    private static int playerType = 1;
    private static Object playerTypeLock = new Object();

    public static void main(String args[]) {

        final Frame f = new Frame("GridBoardCanvasTest");

        final GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);

        final GameOptions gameOptions = new SimpleGameOptions(2);
        gameOptions.setPlayerColor(GameOptions.WHITE, 1);
        gameOptions.setPlayerColor(GameOptions.BLACK, 2);
        gameOptions.setDraw3DPieces(true);
        gameOptions.setPlaySound(true);
        gameOptions.setShowLastMove(true);

        final PenteBoardCanvas penteCanvas = new PenteBoardCanvas();
        penteCanvas.gridCoordinatesChanged(coordinates);
        penteCanvas.gameOptionsChanged(gameOptions);

        final GridBoardOrderedPieceCollectionAdapter penteAdapter =
                new PenteBoardOrderedPieceCollectionAdapter(penteCanvas, false);
        penteAdapter.setGridHeight(19);
        penteAdapter.setGridWidth(19);
        penteAdapter.setOnGrid(true);
        penteAdapter.setThinkingPiecePlayer(1);
        penteAdapter.setThinkingPieceVisible(true);
        penteAdapter.setDrawInnerCircles(true);

        // setup grid states
        final PenteStatePieceCollectionAdapter penteState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        penteState.addOrderedPieceCollectionListener(penteAdapter);
        penteState.setCapturesToWin(1000);    // ridiculous number so game doesn't stop

        final GridState gridState = new SynchronizedPenteState(penteState);
        penteAdapter.setGameName(GridStateFactory.getGameName(GridStateFactory.PENTE));


        f.add(penteCanvas);

        f.pack();
        f.setLocation(100, 100);
        f.setSize(400, 400);
        f.setVisible(true);

        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
                penteCanvas.destroy();
            }
        });

        class GridBoardController implements GridBoardListener {

            public void gridMoved(int x, int y) {

                int move = (penteAdapter.getGridHeight() - y - 1) * penteAdapter.getGridWidth() + x;
                synchronized (playerTypeLock) {
                    penteAdapter.setThinkingPieceVisible(gridState.isValidMove(move, playerType));
                }
            }

            public void gridClicked(int x, int y, int button) {

            }
        }
        penteAdapter.addGridBoardListener(new GridBoardController());


        // change thinking piece in a separate thread
        if (changeThinkingPiece) {
            new Thread(() -> {
                for (int i = 0; i < 600; i++) {
                    synchronized (playerTypeLock) {
                        playerType = (i % 2) + 1;
                        penteAdapter.setThinkingPiecePlayer(playerType);
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {

                    }
                }
            }).start();
        }

        if (moveMouse) {
            new Thread(() -> {
                for (int i = 0; i < 600; i++) {

                    Dimension s = penteCanvas.getSize();
                    int x = (int) (Math.random() * s.width);
                    int y = (int) (Math.random() * s.height);

                    MouseEvent m = new MouseEvent(
                            penteCanvas,
                            MouseEvent.MOUSE_MOVED,
                            System.currentTimeMillis(),
                            0,
                            x,
                            y,
                            1,
                            false);
                    penteCanvas.testMouseEvent(m);

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {

                    }
                }
            }).start();
        }

        // resize the window in a thread
        if (resize) {
            new Thread(() -> {
                for (int i = 0; i < 600; i++) {
                    Dimension s = f.getSize();
                    int x = (int) (Math.random() * 50);
                    int y = (int) (Math.random() * 50);

                    int b = i % 2 == 0 ? -1 : 1;
                    x = s.width + b * x;
                    y = s.height + b * y;

                    f.setSize(x, y);
                    penteCanvas.setSize(x, y);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }).start();
        }

        // change the colors of the pieces randomly
        if (changeColors) {
            new Thread(() -> {
                for (int i = 0; i < 600; i++) {

                    int w = (int) (Math.random() * 8);
                    int b = (int) (Math.random() * 8);

                    gameOptions.setPlayerColor(w, 1);
                    gameOptions.setPlayerColor(b, 2);
                    penteCanvas.gameOptionsChanged(gameOptions);

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                }
            }).start();
        }


        // start a bunch of threads to add moves and undo moves
        if (addMoves) {
            for (int i = 0; i < 19; i++) {
                addThread(gridState, i, 100);

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {

                }
            }
        }
    }

    private static void addThread(final GridState gridState, final int y, final int delay) {
        new Thread(() -> {
            for (int i = 0; i < 19; i++) {

                gridState.addMove(y * 19 + i);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }
            for (int i = 0; i < 19; i++) {

                gridState.undoMove();

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                }
            }
        }).start();
    }
}

