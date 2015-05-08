/** IYTTournamentPlayersFilter.java
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

import java.util.*;

import org.pente.filter.*;

/** This line filter stores all the players id's in 1 round of a tournament
 *  @see IYTTournamentPlayersBuilder
 *  @see IYTSpider
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTTournamentPlayersFilter implements LineFilter {

    /** The Vector to store player ids in */
    private Vector  players;

    /** Create an IYTTournamentPlayersFilter with the players vector
     *  @param players The Vector to store player ids in
     */
    public IYTTournamentPlayersFilter(Vector players) {
        this.players = players;
    }

    /** Look for a player id in this line
     *  @param line The line to filter
     *  @return String The same line passed in
     */
    public String filterLine(String line) {

        int beginIndex = line.indexOf(IYTConstants.SECTION_REQUEST);
        if (beginIndex >= 0) {

            int endIndex = line.indexOf("\"", beginIndex);
            if (endIndex == -1) {
                endIndex = line.indexOf(">", beginIndex);
                if (endIndex == -1) {
                    return null;
                }
            }
            line = line.substring(beginIndex + IYTConstants.SECTION_REQUEST.length() + 1, endIndex);

            String playerID = getPlayerID(line);
            if (playerID != null) {

                if (!players.contains(playerID)) {
                    players.addElement(playerID);
                }
            }
        }

        return null;
    }

    /** Gets a player id from a parameter list if it exists
     *  @param line The parameter list to get the player id from
     *  @return String The player id
     */
    public String getPlayerID(String line) {

        String playerID = null;

        StringTokenizer lineTokenizer = new StringTokenizer(line, "&");
        while (lineTokenizer.hasMoreTokens()) {
            String pair = lineTokenizer.nextToken();

            StringTokenizer pairTokenizer = new StringTokenizer(pair, "=");
            String key = pairTokenizer.nextToken();
            String value = pairTokenizer.nextToken();

            if (key.equals(IYTConstants.USERID_PARAMETER)) {
                playerID = value;
            }
        }

        return playerID;
    }
}
