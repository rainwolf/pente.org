/**
 * IYTGameBuilder.java
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

import org.pente.database.*;
import org.pente.game.*;
import org.pente.filter.*;
import org.pente.filter.http.*;
import org.pente.filter.iyt.game.*;

/** Gets all the information about a game and puts it in a GameData object.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTGameBuilder implements FilterListener, Runnable {

    /** The request string used at iyt */
    private String request;

    /** The parameters to send with the request */
    private Hashtable<String, String> params;

    /** The cookies needed to send the request to iyt */
    private Hashtable<String, String> cookies;

    /** The game id used by iyt to specify a game */
    private String gameID;

    /** The game data that we are building */
    private GameData gameData;

    /** The game filter used to build the game with a FilterController */
    private IYTGameFilter gameFilter;

    /** For testing purposes you can call this builder directly
     *  with the game id and your cookie to attempt to build a game
     *  @param args[] args[0] = game id, args[1] = cookie, args[2] = db property file, args[4] = redirected host
     */
    public static void main(String args[]) throws Exception {

        if (args.length != 4) {
            System.err.println("usage: <game id> <cookie> <db property file> <redirected host>");
        } else {

            // get the info from the command line
            String gameID = args[0];
            String cookie = args[1];
            String dbPropertyFile = args[2];
            String redirectedHost = args[3];

            // use default iyt game request
            String request = IYTConstants.GAME_REQUEST;

            // create dummy session
            //Session session = new IYTSession(new IYTSessionManager());

            DBHandler dbHandler = null;//broken - new MySQLDBHandler(new File(dbPropertyFile));
            GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
            GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
            PlayerStorer playerStorer = (PlayerStorer) gameStorer;
            PlayerData sessionPlayerData = new DefaultPlayerData();

            // create cookie hashtable
            Hashtable<String, String> cookies = new Hashtable<>();
            cookies.put(IYTConstants.USERID_COOKIE, cookie);

            // create the game builder and run it
            IYTGameBuilder iytGameBuilder = new IYTGameBuilder(gameID, request, gameStorer, playerStorer, sessionPlayerData, cookies, redirectedHost);
            iytGameBuilder.run();

            // display the game with the iyt game interface
            StringBuffer buffer = new StringBuffer();
            GameFormat gameFormat = new IYTPGNGameFormat("\n");
            gameFormat.format(iytGameBuilder.getGameData(), buffer);
            System.out.print(buffer.toString());
        }
    }

    /** Create a new iyt game builder
     *  @param gameID The unique game id
     *  @param request The request string for iyt
     *  @param cookies The cookies used to connect to iyt
     *  @param store Flag to either store the game or not
     */
    public IYTGameBuilder(String gameID,
                          String request,
                          GameStorer gameStorer,
                          PlayerStorer playerStorer,
                          PlayerData sessionPlayerData,
                          Hashtable<String, String> cookies,
                          String redirectedHost) {

        this.gameID = gameID;
        this.request = request;
        this.cookies = cookies;

        params = new Hashtable<>();
        params.put(IYTConstants.GAME_PARAMETER, gameID);
        params.put(IYTConstants.OLD_GAME_PARAMETER, gameID);
        params.put("stage", "7");   //old way
        params.put("s", "7");       //new way
///iyt.dll?a?s=7&g=15300006978941&m=1&u=15200000098568&t=&gn=42

        gameData = new IYTGameData();

        // create the game filter
        gameFilter = new IYTGameFilter(gameStorer, playerStorer, sessionPlayerData, params, cookies, redirectedHost);
    }

    /** Get the GameData built by calling run()
     *  @param GameData The game data
     */
    public GameData getGameData() {
        return gameData;
    }

    /** Start the process of building the game, can be run in a new thread
     *  if desired.
     */
    public void run() {

        // create the filter controller with the game filter
        FilterController httpFilterController = new HttpFilterController("GET",
                IYTConstants.HOST,
                request,
                params,
                cookies,
                gameFilter);
        FilterController filterController = new RetryFilterController(httpFilterController, 2, 60);
        filterController.addListener(this);
        filterController.run();
    }

    /** Only implemented for FilterListener interface
     *  @param line The filtered line
     */
    public void lineFiltered(String line) {
    }

    /** Either save the data or print the exception
     *  @param success Whether or not the filtering was successful
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex) {

        if (success) {
            gameData = gameFilter.getGameData();
        } else {
            ex.printStackTrace();
        }
    }
}