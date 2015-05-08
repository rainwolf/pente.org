/** DemoApplet.java
 *  Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.demo;

import java.awt.*;
import java.applet.Applet;
import java.net.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.swing.*;

public class DemoApplet extends Applet implements GridBoardListener {
    
    private PenteBoardLW gridBoardCanvas;
    
    private Thread thread;
    private volatile boolean running = true;
    
    private String playURL;
    
    public void init() {
        
        playURL = getParameter("playURL");
        
        GameOptions gameOptions = new SimpleGameOptions(2);
        gameOptions.setPlayerColor(GameOptions.WHITE, 1);
        gameOptions.setPlayerColor(GameOptions.BLACK, 2);
        gameOptions.setDraw3DPieces(true);
        gameOptions.setPlaySound(true);
        gameOptions.setShowLastMove(true);

        String playersStr = getParameter("players");
        gridBoardCanvas = new DemoGridBoardCanvas(1);

        gridBoardCanvas.gameOptionsChanged(gameOptions);

        gridBoardCanvas.addGridBoardListener(this);
        
        setLayout(new BorderLayout());
        add("Center", gridBoardCanvas);
        
        setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    
    public void start() {
        
        // fake game, remove captures manually to keep size of applet low
        // (don't have to include big grid state classes)
        final GridPiece removePiece1 = new SimpleGridPiece(4, 5, 1);
        final GridPiece removePiece2 = new SimpleGridPiece(5, 4, 1);
        final GridPiece captureMove1 = new SimpleGridPiece(6, 3, 2);
        final GridPiece pieces[] = {
            new SimpleGridPiece(4, 4, 1),
            new SimpleGridPiece(5, 5, 2),
            new SimpleGridPiece(7, 4, 1),
            new SimpleGridPiece(7, 5, 2),
            new SimpleGridPiece(4, 6, 1),
            new SimpleGridPiece(6, 5, 2),
            removePiece1,
            new SimpleGridPiece(4, 7, 2),
            removePiece2,
            new SimpleGridPiece(3, 6, 2),
            new SimpleGridPiece(6, 4, 1),
            captureMove1,
            removePiece2,
            new SimpleGridPiece(8, 4, 2),
            new SimpleGridPiece(3, 4, 1)
             };
            
        thread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    gridBoardCanvas.clearPieces();
                    
                    for (int i = 0; i < pieces.length; i++) {
                        gridBoardCanvas.addPiece(pieces[i]);
                        if (pieces[i] == captureMove1) {
                            gridBoardCanvas.removePiece(removePiece1);
                            gridBoardCanvas.removePiece(removePiece2);
                        }
                        if (!safeSleep(1500)) return;
                    }

                    if (!safeSleep(5000)) return;
                }
            }
        });
        thread.start();
    }
    
    private boolean safeSleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ex) {
        }
        return running;
    }
    
    public void stop() {
        if (gridBoardCanvas != null) {
            gridBoardCanvas.destroy();
        }
        if (thread != null) {
            running = false;
            thread.interrupt();
            thread = null;
        }
    }

    public void destroy() {
        if (gridBoardCanvas != null) {
            gridBoardCanvas.destroy();
        }
        if (thread != null) {
            running = false;
            thread.interrupt();
            thread = null;
        }
    }


    // implementation of GridBoardListener interface
    public void gridClicked(int x, int y, int button) {
        try {
            URL url = new URL(
                "http", getCodeBase().getHost(), playURL);
            getAppletContext().showDocument(url); 
                    
        } catch (MalformedURLException e) {
        }
    }
    public void gridMoved(int x, int y) {
    }
}