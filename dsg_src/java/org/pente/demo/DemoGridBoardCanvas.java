/**
 * DemoGridBoardCanvas.java
 * Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
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

package org.pente.demo;

import java.awt.*;

import org.pente.gameServer.client.swing.PenteBoardLW;

/** Extension of GridBoardCanvas just to allow new text at the bottom
 *  displaying number of players in the game room currently.
 */
public class DemoGridBoardCanvas extends PenteBoardLW {

    private int players;
    private static final Font playersFont = new Font("Arial", Font.PLAIN, 14);

    public DemoGridBoardCanvas(int players) {
        super();

        this.players = players;

        setGridHeight(9);
        setGridWidth(9);
        setOnGrid(true);
        setGameName("Pente");
        setDrawInnerCircles(false);
        setDrawCoordinates(false);
        setThinkingPieceVisible(false);
        setNewMovesAvailable(false);
        //setBoardInsets(new Insets(0, 0, 20, 0));
    }

    protected void drawEmptyBoard(Graphics g) {

        super.drawEmptyBoard(g);

//        FontMetrics fm = g.getFontMetrics(playersFont);
//        String text = players + " players currently playing!";
//
//        int x = (getSize().width - fm.stringWidth(text)) / 2;
//        int y = getSize().height - getStartY() - 4;        
//
//        g.setFont(playersFont);
//        g.setColor(new Color(139, 0, 0));
//        g.drawString(text, x, y);
    }
}
