/** HttpGameServer.java
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

package org.pente.gameDatabase;

import java.io.*;
import java.net.*;

import org.pente.database.*;
import org.pente.game.*;

public class HttpGameServer {

    public static final String  GAME_ALREADY_STORED =   "/game_already_stored";
    public static final String  STORE_GAME =            "/store_game";
    public static final String  LOAD_GAME =             "/load_game";
    public static final String  PLAYER_ALREADY_STORED = "/player_already_stored";
    public static final String  STORE_PLAYER =          "/store_player";
    public static final String  LOAD_PLAYER =           "/load_player";

    public static final String  SEARCH =                "/search";
    public static final String  SEARCH_ZIP =            "/search.zip";
    public static final String  SEARCH_START_PARAM =    "quick_start";

    public static final String  GAME_DATA =             "game_data";
    public static final String  GAME_ID =               "game_id";
    //public static final String  PLAYER_DATA =           "player_data";
    public static final String  PLAYER_ID =             "player_id";
    public static final String  PLAYER_NAME =           "player_name";
    public static final String  PLAYER_SITE =           "player_site";
    public static final String  GAME_FORMAT =           "game_format";
    public static final String  REFRESH_CACHE =         "refresh_cache";

    public static final String  METHOD =                "POST";

    // move these to config file...
    public static final String  HOST =                  "localhost";
    public static final int     PORT =                  8080;

    /** Starts the server
     *  @param args[]
     */
    public static void main(String args[]) throws Exception {

        // move these to config file...
        File dbPropertyFile = new File(args[0]);

        // The server socket used to listen for connections
        ServerSocket serverSocket = null;

        // move these to config file...
        DBHandler dbHandler = null;//broken - new MySQLDBHandler(dbPropertyFile);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
        GameStorerSearcher gameStorerSearcher = new MySQLGameStorerSearcher(dbHandler, gameStorer, gameVenueStorer);
        try {

            // create the server socket
            serverSocket = new ServerSocket(PORT);

        } catch(Exception ex) {
            System.err.println("Error starting server");
            ex.printStackTrace(System.err);
        }

        System.out.println("HttpGameServer running at " + HOST + ":" + PORT);
        System.out.println();

        // continualy loop accepting connections
        while (true) {

            try {

                new Thread(new HttpGameServerThread(serverSocket.accept(),
                                                    gameStorer,
                                                    gameStorerSearcher)).start();

            } catch(Exception ex) {
                System.out.println("HttpGameServer accept error");
                ex.printStackTrace();
            }
        }
    }
}