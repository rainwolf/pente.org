/**
 * MoveData.java
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

package org.pente.game;

import java.io.*;

/** Interface for data structures that hold move data for a game */
public interface MoveData extends Serializable {

    /** Add a move for this game
     *  @param move An integer representation of a move
     */
    public void addMove(int move);

    /** Undo the last move */
    public void undoMove();

    /** Get a move for this game
     *  @param num The sequence number of the move
     */
    public int getMove(int num);

    /** Get the number of moves for this game
     *  @return The number of moves
     */
    public int getNumMoves();

    public int[] getMoves();
}