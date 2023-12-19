/**
 * HttpGameStorer.java
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

import java.net.*;
import java.util.*;
import java.io.*;


import org.pente.gameDatabase.*;
import org.pente.filter.http.*;

/** An implementation of GameStorer, PlayerStorer that loads/stores data
 *  through an http server.
 *  @see HttpGameServer
 *  @since 0.3
 *  @author dweebo (dweebo@www.pente.org)
 */
public class HttpGameStorer extends AbstractHttpStorer implements GameStorer, PlayerStorer {

    //private static Category cat = Category.getInstance(HttpGameStorer.class.getName());

    public static void main(String args[]) throws Exception {

        //GameStorer localGameStorer = new MySQLPenteGameStorer(new File(args[0]));
        //PlayerStorer localPlayerStorer = (PlayerStorer) localGameStorer;
        //GameFormat gameFormat = new PGNGameFormat("\r\n");
        //GameStorer gameStorer = new HttpGameStorer(HttpGameServer.HOST, HttpGameServer.PORT, gameFormat, "/dsg_db/servlet/HttpGameServlet");
        //GameStorer gameStorer = new HttpGameStorer("dsg.ebizhostingsolutions.com", 80, gameFormat, "/servlet/HttpGameServlet");
        //PlayerStorer playerStorer = (PlayerStorer) gameStorer;

        //test manual load game data store
/*
        GameData gameData = new DefaultGameData();
        gameData.setDate(new java.util.Date());
        gameData.setEvent("Non-Tournament Game");
        gameData.setGame("Pro-Pente");
        gameData.setInitialTime(15);
        gameData.setRated(true);
        gameData.setSite(DSG2_12GameFormat.SITE_NAME);
        gameData.setTimed(true);
        gameData.setWinner(1);

        gameData.addMove(180);
        gameData.addMove(160);

        PlayerData p1 = new DefaultPlayerData();
        p1.setUserIDName("dweebo");
        gameData.setPlayer1Data(p1);
        PlayerData p2 = new DefaultPlayerData();
        p2.setUserIDName("mmammel");
        gameData.setPlayer2Data(p2);

        StringBuffer buf = new StringBuffer();
        buf = gameFormat.format(gameData, buf);
        System.out.println("sending...");
        System.out.println(buf.toString());

        gameStorer.storeGame(gameData);
*/
/*
        FileOutputStream log = new FileOutputStream(args[1]);
        PrintStream printStream = new PrintStream(log);
        System.setErr(printStream);
        System.setOut(printStream);

        PreparedStatement stmt = null;
        ResultSet result = null;
        Connection con = null;
        Vector pids = new Vector();
        Vector sites = new Vector();

        DBHandler dbHandler = new MySQLDBHandler(new File(args[0]));
        con = dbHandler.getConnection();
        stmt = con.prepareStatement("select p.pid, s.name from player p, game_site s where p.site_id = s.sid order by p.pid");
        result = stmt.executeQuery();
        while (result.next()) {
            pids.addElement(result.getString(1));
            sites.addElement(result.getString(2));
        }
        result.close();
        stmt.close();

        for (int i = 0; i < pids.size(); i++) {

            long pid = Long.parseLong((String) pids.elementAt(i));
            String site = (String) sites.elementAt(i);
            PlayerData playerData = localPlayerStorer.loadPlayer(pid, site);

playerData.setUserID(10000000000000L + playerData.getUserID());

            //System.out.println(playerData.getUserID());
            //System.out.println(playerData.getUserIDName());
            //System.out.println(site);

            System.out.println("attempting to store " + pid);
            //break;

            try {
                playerStorer.storePlayer(playerData, site);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            break;
        }
*/
        // test store player
/*
        PlayerData playerData = new DefaultPlayerData();
        playerData.setUserID(44444444444444L);
        playerData.setUserIDName("test_store");
        playerStorer.storePlayer(playerData, "Dweebo's Stone Games");
*/

        // test load player
//        PlayerData playerData = new DefaultPlayerData();
//        playerData.setUserIDName("scooter666");
//        playerData = playerStorer.loadPlayer(playerData.getUserIDName(), "Dweebo's Stone Games");
//        System.out.println("player id = " + playerData.getUserID());

        // test store game
/*
        GameData gameData = new DefaultGameData();
        localGameStorer.loadGame(31000000001253L, gameData);
        localGameStorer.destroy();
        gameData.setGameID(0L);
        gameStorer.storeGame(gameData);
*/
        // test game already stored
/*
        //System.out.println(gameStorer.gameAlreadyStored(15300003508874L));
*/
        // test load game
/*
        GameData gameData = new DefaultGameData();
        gameStorer.loadGame(15300003508874L, gameData);
        StringBuffer buffer = new StringBuffer();
        gameFormat.format(gameData, buffer);
        System.out.println(buffer.toString());
*/
    }

    /** Create a new http game storer
     *  @param host The host to connect to
     *  @param port The port number to connect to
     *  @gameFormat The format to send/receive games
     */
    public HttpGameStorer(String host, int port, GameFormat gameFormat) {
        super(host, port, gameFormat);
    }

    /** Create a new http game storer
     *  @param host The host to connect to
     *  @param port The port number to connect to
     *  @gameFormat The format to send/receive games
     *  @context If any additional path info is needed before commands
     */
    public HttpGameStorer(String host, int port, GameFormat gameFormat,
                          String context, String userName, String password) {
        super(host, port, gameFormat, context, userName, password);
    }

    //TODO this code is ugly
    public Object[] loadVenueData() throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, "/venues");
        Socket s = null;
        int len = 0;
        try {
            s = getHttpResponseSocket(requestBuffer);
            // read past the http headers to the data
            InputStream in = s.getInputStream();
            int l = 0;
            StringBuffer headerBuf = new StringBuffer();
            while (true) {
                char c = (char) in.read();
                headerBuf.append(c);
                if (l == 0 && c == '\r') l++;
                else if (l == 1 && c == '\n') l++;
                else if (l == 2 && c == '\r') l++;
                else if (l == 3 && c == '\n') break;
                else l = 0;
            }
            String headers[] = headerBuf.toString().split("\r\n");

            for (int i = 1; i < headers.length; i++) {
                if (headers[i].toLowerCase().startsWith("content-length:")) {
                    len = Integer.parseInt(headers[i].substring(16,
                            headers[i].length()));
                }
            }

        } catch (Exception e) {
            if (s != null) {
                s.close();
                s = null;
            }
        }

        return new Object[]{s, len};

    }

    // not implemented yet
    public boolean gameAlreadyStored(GameData gameData) throws Exception {
        return false;
    }

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(long gameID) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer(HttpGameServer.GAME_ID).append("=").append(gameID);
        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.GAME_ALREADY_STORED);
        String response = sendHttpRequest(requestBuffer).toString().trim();

        int status = getHttpResponseCode(response);
        if (status == HttpConstants.STATUS_OK) {
            return true;
        } else if (status == HttpConstants.STATUS_NOT_FOUND) {
            return false;
        } else {
            throw new HttpGameStorerException(status + " - " + response);
        }
    }

    /** Stores the game information
     *  @param data The game data
     *  @exception Exception If the game cannot be stored
     */
    public void storeGame(GameData data) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.GAME_ID).append("=").append(data.getGameID()).append("&").
                append(HttpGameServer.GAME_FORMAT).append("=").append(gameFormat.getClass().getName()).append("&").
                append(HttpGameServer.GAME_DATA).append("=");

        StringBuffer gameBuffer = new StringBuffer();
        gameFormat.format(data, gameBuffer);
        try {
            paramsBuffer.append(URLEncoder.encode(gameBuffer.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
        }

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.STORE_GAME);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status != HttpConstants.STATUS_OK) {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }
    }

    /** Loads the game information
     *  @param gameID The unique game id
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public GameData loadGame(long gameID, GameData gameData) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.GAME_ID).append("=").append(gameID).append("&").
                append(HttpGameServer.GAME_FORMAT).append("=").append(gameFormat.getClass().getName());

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.LOAD_GAME);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status == HttpConstants.STATUS_OK) {

            responseBuffer = getHttpResponse(responseBuffer);

            gameFormat.parse(gameData, responseBuffer);
        } else {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }

        return gameData;
    }

    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(long playerID, String site) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.PLAYER_ID).append("=").append(playerID).append("&").
                append(HttpGameServer.PLAYER_SITE).append("=").append(site);

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.LOAD_PLAYER);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status == HttpConstants.STATUS_OK) {
            return true;
        } else if (status == HttpConstants.STATUS_NOT_FOUND) {
            return false;
        } else {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }
    }

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(String name, String site) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.PLAYER_NAME).append("=").append(name).append("&").
                append(HttpGameServer.PLAYER_SITE).append("=").append(site);

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.LOAD_PLAYER);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status == HttpConstants.STATUS_OK) {
            return true;
        } else if (status == HttpConstants.STATUS_NOT_FOUND) {
            return false;
        } else {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }
    }

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @param site The site for a game
     *  @exception If the player cannot be stored
     */
    public void storePlayer(PlayerData data, String site) throws Exception {

        StringBuffer paramsBuffer = new StringBuffer();
        try {
            paramsBuffer.append(HttpGameServer.PLAYER_ID).append("=").append(data.getUserID()).append("&").
                    append(HttpGameServer.PLAYER_NAME).append("=").append(data.getUserIDName()).append("&").
                    append(HttpGameServer.PLAYER_SITE).append("=").append(URLEncoder.encode(site, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
        }
        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.STORE_PLAYER);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status != HttpConstants.STATUS_OK) {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }
    }

    /** Loads the player information
     *  @param playerID The unique player id
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(long playerID, String site) throws Exception {

        PlayerData playerData = new DefaultPlayerData();

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.PLAYER_ID).append("=").append(playerID).append("&").
                append(HttpGameServer.PLAYER_SITE).append("=").append(site);

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.LOAD_PLAYER);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status == HttpConstants.STATUS_OK) {

            responseBuffer = getHttpResponse(responseBuffer);

            Hashtable<String, Object> params = new Hashtable<>();
            HttpUtilities.parseParams(responseBuffer.toString(), params);
            playerData.setUserIDName((String) params.get(HttpGameServer.PLAYER_NAME));
            String userID = (String) params.get(HttpGameServer.PLAYER_ID);
            if (userID != null) {
                playerData.setUserID(Long.parseLong(userID));
            }
        } else {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }

        return playerData;
    }

    /** Loads the player information
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(String name, String site) throws Exception {

        PlayerData playerData = new DefaultPlayerData();

        StringBuffer paramsBuffer = new StringBuffer();
        paramsBuffer.append(HttpGameServer.PLAYER_NAME).append("=").append(name).append("&").
                append(HttpGameServer.PLAYER_SITE).append("=").append(site);

        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.LOAD_PLAYER);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status == HttpConstants.STATUS_OK) {

            responseBuffer = getHttpResponse(responseBuffer);

            Hashtable<String, Object> params = new Hashtable<>();
            HttpUtilities.parseParams(responseBuffer.toString(), params);
            playerData.setUserIDName((String) params.get(HttpGameServer.PLAYER_NAME));
            String userID = (String) params.get(HttpGameServer.PLAYER_ID);
            if (userID != null) {
                playerData.setUserID(Long.parseLong(userID));
            }
        } else {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        }

        return playerData;
    }

    /** Nothing to clean up
     */
    public void destroy() {
    }
}