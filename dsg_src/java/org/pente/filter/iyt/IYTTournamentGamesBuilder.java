/**
 * IYTTournamentGamesBuilder.java
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
import org.pente.filter.http.*;

/** Builds a list of game ids for 1 player in 1 section in 1
 *  round of 1 tournament.  Can be run in a separate thread.
 *  @see IYTTournamentGamesFilter
 *  @see IYTSpider
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTTournamentGamesBuilder implements Runnable {

    /** The Vector to store game ids in */
    private Vector games;

    /** The tournament id to get games from */
    private int tournamentID;

    /** The type of game to get games for */
    private int gameTypeID;

    /** The round to get games from */
    private int round;

    /** The player to get games for */
    private String playerID;

    /** Cookies used to connect to iyt */
    private Hashtable cookies;

    /** Creates an IYTTournamentGamesBuilder with a new vector of game ids
     *  @param tournamentID The tournament id
     *  @param gameTypeID The game type
     *  @param round The round
     *  @param playerID The players id
     *  @param cookies The cookies to connect to iyt
     */
    public IYTTournamentGamesBuilder(int tournamentID, int gameTypeID, int round, String playerID, Hashtable cookies) {
        this(tournamentID, gameTypeID, round, playerID, cookies, new Vector());
    }

    /** Creates an IYTTournamentGamesBuilder with an existing vector of game ids
     *  @param tournamentID The tournament id
     *  @param gameTypeID The game type
     *  @param round The round
     *  @param playerID The players id
     *  @param cookies The cookies to connect to iyt
     *  @param games The vector to put game ids in
     */
    public IYTTournamentGamesBuilder(int tournamentID, int gameTypeID, int round, String playerID, Hashtable cookies, Vector games) {
        this.tournamentID = tournamentID;
        this.gameTypeID = gameTypeID;
        this.round = round;
        this.playerID = playerID;
        this.cookies = cookies;
        this.games = games;
    }

    /** Gets the game ids filtered
     *  @return Vector The game ids
     */
    public Vector getGames() {
        return games;
    }

    /** Creates an IYTTournamentGamesFilter and uses it with a HttpFilterController
     *  to filter out game ids
     */
    public void run() {

        Hashtable params = new Hashtable();
        params.put("id", Integer.toString(tournamentID));
        params.put("gmtypeid", Integer.toString(gameTypeID));
        params.put("r", Integer.toString(round));
        params.put(IYTConstants.USERID_PARAMETER, playerID);

        IYTTournamentGamesFilter filter = new IYTTournamentGamesFilter(games);

        FilterController httpFilterController = new HttpFilterController("GET",
                IYTConstants.HOST,
                IYTConstants.SECTION_REQUEST,
                params,
                cookies,
                filter);
        FilterController filterController = new RetryFilterController(httpFilterController, RetryFilterController.INFINITE_RETRIES, 60);
        filterController.run();
    }
}