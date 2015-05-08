/** GridPieceAction.java
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

package org.pente.gameServer.core;

public class GridPieceAction {

    public static final int ADD = 1;
    public static final int REMOVE = 2;
    public static final int POOF = 3;

    private GridPiece   gridPiece;
    private int         turn;
    private int         action;

    public GridPieceAction(GridPiece gridPiece, int turn, int action) {
        this.gridPiece = gridPiece;
        this.turn = turn;
        this.action = action;
    }

    public GridPiece getGridPiece() {
        return gridPiece;
    }
    public int getTurn() {
        return turn;
    }
    public int getAction() {
        return action;
    }

    public String toString() {
        return "Turn: " + turn + ", action: " + action + ", gridpiece: " + gridPiece;
    }
}