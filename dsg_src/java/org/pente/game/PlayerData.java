/**
 * PlayerData.java
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

/** Interface for data structure that holds information about a player.  Written
 *  originally to handle iyt players.  Probably can be used for other sites.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface PlayerData extends Serializable {

    /** Set the players user id name
     *  @param name The players user id name (what you log in with)
     */
    public void setUserIDName(String name);

    /** Get the players user id name
     *  @return String The players user id name (what you log in with)
     */
    public String getUserIDName();


    /** Set the players user id
     *  @param userID The players user id
     */
    public void setUserID(long userID);

    /** Get the players user id
     *  @return long The players user id
     */
    public long getUserID();


    /** If the player is a human */
    public final int HUMAN = 0;
    /** If the player is a computer */
    public final int COMPUTER = 1;

    /** Set the players type
     *  @param type The players type
     */
    public void setType(int type);

    /** Get the players type
     *  @return int Type players type
     */
    public int getType();


    /** Set the players rating
     *  @param rating The players rating
     */
    public void setRating(int rating);

    /** Get the players rating
     *  @return int The players rating
     */
    public int getRating();

    public int getNameColor();

    public void setNameColor(int nameColor);
}