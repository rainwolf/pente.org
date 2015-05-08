/** IYTConstants.java
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

package org.pente.filter.iyt;

/** Useful constants for the IYT filter/spider classes
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public final class IYTConstants {

    /** Non-instantiable class */
    private IYTConstants() {
    }

    /** Host to filter from */
    public static final String 	HOST = 				    "www.itsyourturn.com";

    /** Name of the site for storing/displaying */
    public static final String  SITE_NAME =             "It's Your Turn";

    /** possible request, returns a text representation of the game
     *  specified in the parameter "game"
     */
    public static final String SHOW_GAME =             "/iyt.dll/game.txt";
    /** URL to the help file for the filter */
    public static final String HELP_FILE_URL =         "http://www.pente.org/downloads/iyt/readme";


    /** Cookie name used to track sessions in IYTServer */
    public static final String	JSESSIONID = 		    "JSESSIONID";
    /** Name of the hashtable containing games in a session */
    public static final String	SESSION_GAMES = 	    "games";
    /** Name of the player's name in the session */
    public static final String  SESSION_PLAYER_DATA =   "playerData";

    /** Name of the parameter specifying the game id */
    public static final String  OLD_GAME_PARAMETER =    "game";
    /** Name of the parameter specifying the move number */
    public static final String  OLD_MOVE_PARAMETER =    "move";
    /** Name of the parameter specifying the game id */
    public static final String  GAME_PARAMETER =        "g";
    /** Name of the parameter specifying the move number */
    public static final String  MOVE_PARAMETER =        "m";
    /** Cookie name used by iyt to identity users */
    public static final String  USERID_COOKIE =         "USERID";
    /** Name of the parameter specifying a user's id */
    public static final String  USERID_PARAMETER =      "userid";

    /** Request string to load a game board */
    public static final String  GAME_REQUEST =          "/iyt.dll?a";
    /** Request string to load a section in a tournament */
    public static final String  SECTION_REQUEST =       "/iyt.dll?tourn_gamestatus";
    /** Request string to load a round in a tournament */
    public static final String  ROUND_REQUEST =         "/iyt.dll?tourn_showplayers";
    /** Request string to load the user profile screen */
    public static final String  USER_PROFILE_REQUEST =  "/iyt.dll?userprofile";
    /** Request string to load a users completed games */
    public static final String  COMPLETE_GAMES_REQUEST = "/iyt.dll?completedgame";

    /** Player1 is black at iyt */
    public static final String  PLAYER1 =               "black";
    /** Player2 is white at iyt */
    public static final String  PLAYER2 =               "white";
}