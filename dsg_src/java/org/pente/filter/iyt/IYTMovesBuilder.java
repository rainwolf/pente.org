/**
 * IYTMovesBuilder.java
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

package org.pente.filter.iyt;

import org.pente.game.*;

/** Interface to classes that can build a list of moves for a game.  There
 *  are a variety of techniques used in this procress, so different implementing
 *  classes can do things differently.
 *  @since 0.3
 *  @author dweebo (dweebo@www.pente.org)
 */
public interface IYTMovesBuilder {

    /** Builds the moves of the game into the game data
     *  @return GameData The game data with the moves
     *  @exception Exception If the moves couldn't be build
     */
    public GameData buildMoves() throws Exception;
}