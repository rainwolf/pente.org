/**
 * IYTTournamentGamesFilter.java
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

import java.util.*;

import org.pente.filter.*;

/** This line filter stores all the game id's for 1
 *  players games in 1 section of 1 round of a tournament
 *  @see IYTTournamentGamesBuilder
 *  @see IYTSpider
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTTournamentGamesFilter implements LineFilter {

    /** The Vector to store game ids in */
    private Vector games;

    /** Create an IYTTournamentGamesFilter with the games vector
     *  @param games The Vector to store game ids in
     */
    public IYTTournamentGamesFilter(Vector games) {
        this.games = games;
    }

    /** Look for a game id in this line
     *  @param line The line to filter
     *  @return String The same line passed in
     */
    public String filterLine(String line) {

        try {
            int beginIndex = line.indexOf(IYTConstants.GAME_REQUEST);
            if (beginIndex >= 0) {
                beginIndex = line.indexOf("?", beginIndex);
            }
            if (beginIndex >= 0) {

                int endIndex = line.indexOf("\"", beginIndex);
                if (endIndex == -1) {
                    endIndex = line.indexOf(">", beginIndex);
                    if (endIndex == -1) {
                        return null;
                    }
                }
                line = line.substring(beginIndex + 1, endIndex);

                String gameID = getGameID(line);
                if (gameID != null) {

                    if (!games.contains(gameID)) {
                        games.addElement(gameID);
                    }
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    /** Gets a game id from a parameter list if it exists
     *  @param line The parameter list to get the game id from
     *  @return String The game id
     */
    public String getGameID(String line) {

        StringTokenizer lineTokenizer = new StringTokenizer(line, "&");
        while (lineTokenizer.hasMoreTokens()) {
            String pair = lineTokenizer.nextToken();

            StringTokenizer pairTokenizer = new StringTokenizer(pair, "=");
            String key = pairTokenizer.nextToken();
            String value = pairTokenizer.nextToken();

            if (key.equals(IYTConstants.GAME_PARAMETER)) {
                return value;
            } else if (key.equals(IYTConstants.OLD_GAME_PARAMETER)) {
                return value;
            }
        }

        return null;
    }
}