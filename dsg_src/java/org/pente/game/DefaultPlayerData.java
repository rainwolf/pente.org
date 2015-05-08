/** DefaultPlayerData.java
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

/** Interface for data structure that holds information about a player.  Written
 *  originally to handle iyt players.  Probably can be used for other sites.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class DefaultPlayerData implements PlayerData {

    /** User ID name of the player */
    protected String    userIDName;
    /** User ID of the player */
    protected long      userID;
    /** Type of the player */
    protected int       type;
    /** Rating of the player */
    protected int       rating;
    
    protected int nameColor;


    /** Set the players user id name
     *  @param name The players user id name (what you log in with)
     */
    public void setUserIDName(String name) {
        this.userIDName = name;
    }

    /** Get the players user id name
     *  @return String The players user id name (what you log in with)
     */
    public String getUserIDName() {
        return userIDName;
    }


    /** Set the players user id
     *  @param userID The players user id
     */
    public void setUserID(long userID) {
        this.userID = userID;
    }

    /** Get the players user id
     *  @return long The players user id
     */
    public long getUserID() {
        return userID;
    }


    /** If the player is a human */
    public final int HUMAN = 0;
    /** If the player is a computer */
    public final int COMPUTER = 1;

    /** Set the players type
     *  @param type The players type
     */
    public void setType(int type) {
        this.type = type;
    }

    /** Get the players type
     *  @return int Type players type
     */
    public int getType() {
        return type;
    }


    /** Set the players rating
     *  @param rating The players rating
     */
    public void setRating(int rating) {
        this.rating = rating;
    }

    /** Get the players rating
     *  @return int The players rating
     */
    public int getRating() {
        return rating;
    }

	public int getNameColor() {
		return nameColor;
	}

	public void setNameColor(int nameColor) {
		this.nameColor = nameColor;
	}
}