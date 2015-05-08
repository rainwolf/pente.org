/** GameOptions.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
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

package org.pente.gameServer.client;

public interface GameOptions extends java.io.Serializable {

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static final int RED = 2;
    public static final int ORANGE = 3;
    public static final int YELLOW = 4;
    public static final int BLUE = 5;
    public static final int GREEN = 6;
    public static final int PURPLE = 7;

    public int getPlayerColor(int playerNum);
    public void setPlayerColor(int color, int playerNum);

    public boolean getDraw3DPieces();
    public void setDraw3DPieces(boolean draw3DPieces);

    public boolean getShowLastMove();
    public void setShowLastMove(boolean showLastMove);

    public boolean getPlaySound();
    public void setPlaySound(boolean playSound);

    public void setDrawDepth(boolean drawDepth);
    public boolean getDrawDepth();
    
    public GameOptions newInstance();
}