/** IYTTournamentPlayersBuilder.java
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
import org.pente.filter.http.*;

/** Builds a list of player ids for 1 round of 1 tournament.  Can be run in a
 *  separate thread.
 *  @see IYTTournamentPlayersFilter
 *  @see IYTSpider
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTTournamentPlayersBuilder implements Runnable {

    /** The Vector to store player ids in */
    private Vector      players;

    /** The tournament id to get players from */
    private int         tournamentID;

    /** The type of game to get players for */
    private int         gameTypeID;

    /** The round to get players from */
    private int         round;

    /** Cookies used to connect to iyt */
    private Hashtable   cookies;

    /** Creates an IYTTournamentGamesBuilder with a new vector of player ids
     *  @param tournamentID The tournament id
     *  @param gameTypeID The game type
     *  @param round The round
     *  @param playerID The players id
     *  @param cookies The cookies to connect to iyt
     */
    public IYTTournamentPlayersBuilder(int tournamentID, int gameTypeID, int round, Hashtable cookies) {

        this.tournamentID = tournamentID;
        this.gameTypeID = gameTypeID;
        this.round = round;
        this.cookies = cookies;

        players = new Vector();
    }

    /** Gets the player ids
     *  @return Vector The player ids
     */
    public Vector getPlayers() {
        return players;
    }
    /** Creates an IYTTournamentPlayerssFilter and uses it with a HttpFilterController
     *  to filter out player ids
     */
    public void run() {

        IYTTournamentPlayersFilter filter = new IYTTournamentPlayersFilter(players);

        Hashtable params = new Hashtable();
        params.put("id", Integer.toString(tournamentID));
        params.put("gmtypeid", Integer.toString(gameTypeID));
        params.put("type", "1");
        params.put("st", "1");
        params.put("range", "100");
        params.put("r", Integer.toString(round));

        FilterController httpFilterController = new HttpFilterController("GET",
                                                                         IYTConstants.HOST,
                                                                         IYTConstants.ROUND_REQUEST,
                                                                         params,
                                                                         cookies,
                                                                         filter);
        FilterController filterController = new RetryFilterController(httpFilterController, RetryFilterController.INFINITE_RETRIES, 60);
        filterController.run();
    }
}