/**
 * PenteBoardCanvas.java
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

package org.pente.gameServer.client.awt;

import java.awt.*;

import org.pente.gameServer.client.*;
import org.pente.gameServer.core.GridPiece;

public class PenteBoardCanvas extends GridBoardCanvas implements PenteBoardComponent {

    int captureAreaWidth;
    int captures[];

    public PenteBoardCanvas() {
        super();

        insets = new Insets(0, 0, 0, 0);
        captures = new int[3];
    }

    public void incrementCaptures(int player) {
        synchronized (drawLock) {
            captures[player]++;
            boardDirty = true;
        }
        repaint();
    }

    public void decrementCaptures(int player) {
        synchronized (drawLock) {
            captures[player]--;
            boardDirty = true;
        }
        repaint();
    }

    public void clearPieces() {
        super.clearPieces();
        synchronized (drawLock) {
            captures[1] = 0;
            captures[2] = 0;
            boardDirty = true;
        }
        repaint();
    }

    protected int getStartX() {
        return beveledEdge + insets.left + captureAreaWidth + edgeLeftOvers.width + coordinatesDimensions.width;
    }

    public void calculateGridSize() {

        Dimension size = getSize();

        // get coordinates width/height
        Font f = new Font("Helvetica", Font.PLAIN, 10);
        FontMetrics fm = emptyBoardGraphics.getFontMetrics(f);
        coordinatesDimensions.width = fm.stringWidth("10") + 2;
        coordinatesDimensions.height = fm.getAscent() + 2;
        // end coordinates

        // get gridpiecesize
        size.width -= (insets.left + insets.right + beveledEdge * 2 + coordinatesDimensions.width * 2);
        size.height -= (insets.top + insets.bottom + beveledEdge * 2 + coordinatesDimensions.height * 2);

        int gridPieceSizeWidth = size.width / (gridWidth + 3);
        int gridPieceSizeHeight = size.height / (gridHeight - 1);

        gridPieceSize = gridPieceSizeWidth < gridPieceSizeHeight ?
                gridPieceSizeWidth : gridPieceSizeHeight;
        // end gridpiecesize

        captureAreaWidth = gridPieceSize * 2;

        // get edges left overs
        edgeLeftOvers.width = (size.width - gridPieceSize * (gridWidth + 3)) / 2;
        edgeLeftOvers.height = (size.height - gridPieceSize * (gridHeight - 1)) / 2;
    }

    void drawBoard(Graphics boardGraphics) {
        super.drawBoard(boardGraphics);

        int x = beveledEdge + insets.left + edgeLeftOvers.width;
        int y = getStartY();

        Color c[] = GameStyles.colors[gameOptions.getPlayerColor(2)];
        for (int i = 0; i < captures[1] / 2 + captures[1] % 2; i++) {
            int maxj = captures[1] >= 2 * (i + 1) ? 2 : 1;
            for (int j = 0; j < maxj; j++) {
                if (gameOptions.getDraw3DPieces()) {
                    draw3DPiece(boardGraphics, new Point(x + j * gridPieceSize, y + i * gridPieceSize), c, null, gridPieceSize);
                } else {
                    draw2DPiece(boardGraphics, new Point(x + j * gridPieceSize, y + i * gridPieceSize), c[1], null, gridPieceSize);
                }
                if (drawGoDots()) {
                    break;
                }
            }
            if (drawGoDots()) {
                break;
            }
        }

        x = getStartX() + gridPieceSize * (gridWidth - 1) + coordinatesDimensions.width;
        y = getStartY();
        c = GameStyles.colors[gameOptions.getPlayerColor(1)];
        for (int i = 0; i < captures[2] / 2 + captures[2] % 2; i++) {
            int maxj = captures[2] >= 2 * (i + 1) ? 2 : 1;
            for (int j = 0; j < maxj; j++) {
                if (drawGoDots()) {
                    j = 1;
                }
                if (gameOptions.getDraw3DPieces()) {
                    draw3DPiece(boardGraphics, new Point(x + j * gridPieceSize, y + i * gridPieceSize), c, null, gridPieceSize);
                } else {
                    draw2DPiece(boardGraphics, new Point(x + j * gridPieceSize, y + i * gridPieceSize), c[1], null, gridPieceSize);
                }
                if (drawGoDots()) {
                    break;
                }
            }
            if (drawGoDots()) {
                break;
            }
        }

    }
}