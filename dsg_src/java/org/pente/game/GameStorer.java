/**
 * GameStorer.java
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

/** Interface for classes that store the data from a game
 *  @see PlayerStorer
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface GameStorer {

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(long gameID) throws Exception;

    /** Checks to see if the game has already been stored
     *  @param data The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(GameData data) throws Exception;

    /** Stores the game information
     *  @param data The game data
     *  @exception Exception If the game cannot be stored
     */
    public void storeGame(GameData data) throws Exception;

    /** Loads the game information
     *  @param gameID The unique game id
     *  @param data To store the game data in
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public GameData loadGame(long gameID, GameData data) throws Exception;

    /** Clean up any resources that are still being held
     */
    public void destroy();
}