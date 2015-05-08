/** PlayerStorer.java
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

package org.pente.game;

/** Interface for classes that store the player data for a player
 *  @see GameStorer
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface PlayerStorer {

    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(long playerID, String site) throws Exception;

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(String name, String site) throws Exception;

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @param site The site the player is registered for
     *  @exception If the player cannot be stored
     */
    public void storePlayer(PlayerData data, String site) throws Exception;

    /** Loads the player information
     *  @param playerID The unique player id
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(long playerID, String site) throws Exception;

    /** Loads the player information
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(String name, String site) throws Exception;

    /** Clean up any resources that are still being held
     */
    public void destroy();
}