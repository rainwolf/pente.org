/** MySQLPenteGameStorer.java
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

import java.io.*;
import java.util.*;
import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;

/** Stores game data in a mysql database
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class MySQLPenteGameStorer extends MySQLGameStorer {

    /** The name of the table with the game information */
    protected static final String GAME_TABLE = "pente_game";
    /** The name of the table with the move information */
    protected static final String MOVE_TABLE = "pente_move";

    /** The names of all tables, used to lock them at one time */
    protected static final Vector<String> ALL_TABLES = new Vector<String>();

    static {
        ALL_TABLES.addElement(GAME_TABLE);
        ALL_TABLES.addElement(MOVE_TABLE);
        ALL_TABLES.addElement(MySQLGameVenueStorer.GAME_SITE_TABLE);
    }

    private static Category log4j = Category.getInstance(MySQLPenteGameStorer.class.getName());

    /** For testing purposes you can attempt to load a game
     *  with the game id.  modify this to store a game, etc.
     *  @param args[] args[0] = game id
     */
    public static void main(String args[]) throws Exception {

        if (args.length != 2) {
            System.err.println("usage: java MySQLPenteGameStorer <db config file> <game id>");
        }
        else {
            File dbConfigFile = new File(args[0]);
            DBHandler dbHandler = null;//broken - new MySQLDBHandler(dbConfigFile);
            MySQLGameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
            GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);

            long gameID = Long.parseLong(args[1]);
            GameData gameData = gameStorer.loadGame(gameID, null);
            if (gameData != null) {
                StringBuffer buffer = new StringBuffer();
                GameFormat gameFormat = new PGNGameFormat("\n");
                gameFormat.format(gameData, buffer);
                System.out.print(buffer.toString());
            }
            else {
                System.out.println("game id " + gameID + " not found.");
            }

            gameStorer.destroy();
        }
    }

    /** Needed because super class throws Exception in default constructor
     *  @exception If super class throws Exception during initialization
     */
    public MySQLPenteGameStorer(DBHandler dbHandler, GameVenueStorer gameVenueStorer) throws Exception {
        super(dbHandler, gameVenueStorer);

        //this.gameVenueStorer = gameVenueStorer;
    }

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(long gameID) throws Exception {

        Connection con = null;
        boolean stored = false;

        try {
            con = dbHandler.getConnection();

            stored = gameAlreadyStored(con, gameID);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return stored;
    }

    /** Checks to see if the game has already been stored
     *  @param con A database connection to get check for the game
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(Connection con, long gameID) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        boolean stored = false;

        try {
            stmt = con.prepareStatement("select 1 " +
                                        "from " + GAME_TABLE + " " +
                                        "where gid = ?");
            stmt.setLong(1, gameID);

            result = stmt.executeQuery();

            stored = result.next();

        } finally {
            if (result != null) { try { result.close(); } catch(SQLException ex) {} }
            if (stmt != null) { try { stmt.close(); } catch(SQLException ex) {} }
        }

        return stored;
    }

    /** Checks to see if the game has already been stored
     *  @param gameData The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(GameData gameData) throws Exception {

        Connection con = null;
        boolean stored = false;

        try {
            con = dbHandler.getConnection();

            stored = gameAlreadyStored(con, gameData);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return stored;
    }

    /** Checks to see if the game has already been stored
     *  This method looks for games that occurred at the same time and then
     *  if any are found gets the data from the db and compares the games with
     *  equals().  If in the future there are games stored with only date info,
     *  not time info, a better strategy might be needed.
     *  @param con A database connection to get check for the game
     *  @param gameData The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(Connection con, GameData gameData) throws Exception {
        return getGid(con, gameData) != -1;
    }

    /** Checks to see if the game has already been stored
     *  This method looks for games that occurred at the same time and then
     *  if any are found gets the data from the db and compares the games with
     *  equals().  If in the future there are games stored with only date info,
     *  not time info, a better strategy might be needed.
     *  @param con A database connection to get check for the game
     *  @param gameData The game data
     *  @return long The game id
     *  @exception Exception If the game cannot be checked
     */
    public long getGid(Connection con, GameData gameData) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        long gid = -1;

        try {
            stmt = con.prepareStatement("select gid " +
                                        "from " + GAME_TABLE + " " +
                                        "where play_date = ?");

            stmt.setTimestamp(1, new Timestamp(gameData.getDate().getTime()));
            result = stmt.executeQuery();

            while (result.next()) {

                long mgid = result.getLong(1);
//cat.debug("possibly matched gid = " + mgid);
                GameData storedGameData = new DefaultGameData();
                loadGame(con, mgid, storedGameData);

                if (storedGameData.equals(gameData)) {
                    gid = mgid;
//cat.debug("matched gid = " + gid);
                    break;
                }
            }

        } finally {
            if (result != null) { try { result.close(); } catch(SQLException ex) {} }
            if (stmt != null) { try { stmt.close(); } catch(SQLException ex) {} }
        }

        return gid;
    }

    /** Stores the game information
     *  @param con A database connection to store the game in
     *  @param data The game data
     *  @exception Exception If the game cannot be stored
     */
    public void storeGame(Connection con, GameData data) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        boolean gameAlreadyStored = false;
        boolean newGidAssigned = false;

        try {

            // either this is a new game or the gid simply wasn't supplied
            if (data.getGameID() == 0) {

                // if the gid is found, then update the game data
                // and continue to below when we try to update moves
                long tgid = getGid(con, data);
                if (tgid != -1) {
                    data.setGameID(tgid);
                    gameAlreadyStored = true;
                }
                // else this is a new game, get the next available gid from the database
                else {
//cat.debug("new game");
                    GameSiteData siteData = gameVenueStorer.getGameSiteData(
                        GridStateFactory.getGameId(data.getGame()), data.getSite());
                    if (siteData == null) {

                        siteData = new SimpleGameSiteData();
                        siteData.setName(data.getSite());
                        gameVenueStorer.addGameSiteData(
                            GridStateFactory.getGameId(data.getGame()),
                            siteData);
                    }

                    // this will fail to get a good gid if there aren't any
                    // games stored from this site yet... don't feel like adding
                    // code to handle this right now
                    
                    // gids above 50000000000000 reserved for turn-based games
                    stmt = con.prepareStatement("select max(gid) + 1 " +
                                                "from " + GAME_TABLE + " " +
                                                "where site_id = ? " +
                                                "and gid < 50000000000000");
                    stmt.setInt(1, siteData.getSiteID());

                    result = stmt.executeQuery();
                    if (result.next()) {
                        data.setGameID(result.getLong(1));
                    }

                    newGidAssigned = true;
                }
            }

            // don't put game in db if game is already in db
            if (newGidAssigned || (!gameAlreadyStored && !gameAlreadyStored(con, data.getGameID()))) {

                gameAlreadyStored = false;

                // enter game info
                stmt = con.prepareStatement("insert into " + GAME_TABLE + " " +
                    "(site_id, event_id, round, section, play_date, timer, rated, " +
                    " initial_time, incremental_time, player1_pid, player2_pid, " +
                    " player1_rating, player2_rating, player1_type, player2_type, " +
                    " winner, gid, game, swapped, private, status) " +
                    "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

                String timer = "N";
                if (data.getTimed()) {
                    if (data.getIncrementalTime() == 0) {
                        timer = "S";
                    }
                    else {
                        timer = "I";
                    }
                }

                GameSiteData siteData = gameVenueStorer.getGameSiteData(
                    GridStateFactory.getGameId(data.getGame()), data.getSite());
                if (siteData == null) {
                    siteData = new SimpleGameSiteData();
                    siteData.setName(data.getSite());
                    // this also updates the site id
                    gameVenueStorer.addGameSiteData( 
                        GridStateFactory.getGameId(data.getGame()), siteData);
                }

                GameEventData eventData = gameVenueStorer.getGameEventData(
                    GridStateFactory.getGameId(data.getGame()), data.getEvent(),
                    data.getSite());
                if (eventData == null) {
                    eventData = new SimpleGameEventData();
                    eventData.setName(data.getEvent());
                    // this also updates the event id
                    gameVenueStorer.addGameEventData(
                        GridStateFactory.getGameId(data.getGame()),
                        eventData, siteData.getName());
                }

                stmt.setInt(1, siteData.getSiteID());
                stmt.setInt(2, eventData.getEventID());
                stmt.setString(3, data.getRound());
                stmt.setString(4, data.getSection());
                stmt.setTimestamp(5, new Timestamp(data.getDate().getTime()));
                stmt.setString(6, timer);
                stmt.setString(7, data.getRated() ? "Y" : "N");
                stmt.setInt(8, data.getInitialTime());
                stmt.setInt(9, data.getIncrementalTime());
                stmt.setLong(10, data.getPlayer1Data().getUserID());
                stmt.setLong(11, data.getPlayer2Data().getUserID());
                stmt.setInt(12, data.getPlayer1Data().getRating());
                stmt.setInt(13, data.getPlayer2Data().getRating());
                stmt.setInt(14, data.getPlayer1Data().getType());
                stmt.setInt(15, data.getPlayer2Data().getType());
                stmt.setInt(16, data.getWinner());
                stmt.setLong(17, data.getGameID());
                stmt.setInt(18, GridStateFactory.getGameId(data.getGame()));
                stmt.setString(19, data.didPlayersSwap() ? "Y" : "N");
                stmt.setString(20, data.isPrivateGame() ? "Y" : "N");
                stmt.setString(21, data.getStatus());
                stmt.executeUpdate();
                stmt.close();
            }
            else {
                gameAlreadyStored = true;
            }

            int numMovesInDb = 0;
            boolean gameComplete = false;

            // if the game was already stored, then get the status of the game
            if (gameAlreadyStored) {
                GameData gameStatusData = getGameStatus(con, data.getGameID());

                // if the game has no winner (game isn't over yet) then
                // get the number of moves in the game
                if (gameStatusData.getWinner() == GameData.UNKNOWN) {

                    numMovesInDb = getNumMoves(con, data.getGameID());

                    // if the game is not current (missing latest moves)
                    // update the game with latest date and winner
                    if (numMovesInDb < data.getNumMoves()) {

                        stmt = con.prepareStatement("update " + GAME_TABLE + " " +
                                                    "set play_date = ?, " +
                                                    "winner = ? " +
                                                    "where gid = ?");
                        stmt.setTimestamp(1, new Timestamp(data.getDate().getTime()));
                        stmt.setInt(2, data.getWinner());
                        stmt.setLong(3, data.getGameID());

                        stmt.executeUpdate();
						
						stmt = con.prepareStatement("update " + MOVE_TABLE + " " +
							"set winner = ? where gid = ?");
                        stmt.setLong(1, data.getGameID());
						stmt.setInt(2, data.getWinner());
						stmt.executeUpdate();
                    }
                }
                else {
                    gameComplete = true;
                }
            }

            if (!gameComplete && numMovesInDb < data.getNumMoves()) {

                // create hash keys for all moves
				int game = GridStateFactory.getGameId(data.getGame());
                GridState state = GridStateFactory.createGridState(
                    game, data);
                
                // store moves
                stmt = con.prepareStatement("insert into " + MOVE_TABLE + " " +
                    "(gid, move_num, next_move, hash_key, rotation, game, winner, " +
                    " play_date, seconds_left) " +
                	"values(?, ?, ?, ?, ?, ?, ?, ?, ?)");
                stmt.setLong(1, data.getGameID());
                
                if (numMovesInDb == 0 && state.getNumMoves() > 0 && firstMoveCanBeOffCenter(game)) {
                    stmt.setInt(2, -1);
                    stmt.setInt(3, data.getMove(0));
                    stmt.setLong(4, 0);
                    stmt.setInt(5, state.getFirstMoveRotation(data.getMove(0)));
                    stmt.setInt(6, game);
                    stmt.setInt(7, data.getWinner());
                    stmt.setTimestamp(8, new Timestamp(data.getDate().getTime()));

                    if (data.getTimed() && data.getMoveTimes() != null && 0 < data.getMoveTimes().size()) {
                        stmt.setInt(9, data.getMoveTimes().get(0).getTotalSeconds());
                    }
                    else {
                        stmt.setInt(9, 0);
                    }

                    stmt.executeUpdate();
                }
                for (int i = numMovesInDb; i < state.getNumMoves(); i++) {
                    stmt.setInt(2, i);
					// last move sets next_move to 361
					if (i == state.getNumMoves() - 1) {
						stmt.setInt(3, 361);
					}
					else {
	                    stmt.setInt(3, data.getMove(i + 1));
					}
                    stmt.setLong(4, state.getHash(i));
                    stmt.setInt(5, state.getRotation(i));
					stmt.setInt(6, game);
					stmt.setInt(7, data.getWinner());
					stmt.setTimestamp(8, new Timestamp(data.getDate().getTime()));
					
					if (data.getTimed() && data.getMoveTimes() != null && i < data.getMoveTimes().size()) {
						stmt.setInt(9, data.getMoveTimes().get(i).getTotalSeconds());
					}
					else {
						stmt.setInt(9, 0);
					}
					
                    stmt.executeUpdate();
                }
            }

        } finally {

            if (stmt != null) { try { stmt.close(); } catch(SQLException ex) {} }
        }
    }

    private boolean firstMoveCanBeOffCenter(int gameId) {
        return (gameId == GridStateFactory.DPENTE || gameId == GridStateFactory.SPEED_DPENTE ||
                gameId == GridStateFactory.DKERYO || gameId == GridStateFactory.SPEED_DKERYO ||
                gameId == GridStateFactory.TB_DPENTE || gameId == GridStateFactory.TB_DKERYO ||
                gameId == GridStateFactory.GO || gameId == GridStateFactory.SPEED_GO || gameId == GridStateFactory.TB_GO
        || gameId == GridStateFactory.GO9 || gameId == GridStateFactory.SPEED_GO9 || gameId == GridStateFactory.TB_GO9
        || gameId == GridStateFactory.GO13 || gameId == GridStateFactory.SPEED_GO13 || gameId == GridStateFactory.TB_GO13);
    }
    
    /** Gets the current number of moves stored for a game
     *  @param con A database connection
     *  @param gameID The unique game indentifier
     *  @return int The number of moves for this game
     *  @exception Exception If the number of moves can't be retrieved
     */
    private int getNumMoves(Connection con, long gameID) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        int count = 0;

        try {
            stmt = con.prepareStatement("select max(move_num) + 1 " +
                                        "from " + MOVE_TABLE + " " +
                                        "where gid = ?");
            stmt.setLong(1, gameID);

            result = stmt.executeQuery();

            if (result.next()) {
                count = result.getInt(1);
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }

        return count;
    }

    /** Gets the game's status, the winner and play_date are the only 2 fields
     *  that may need to be updated
     *  @param con A database connection used to load the game
     *  @param gameID The unique game id
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    private GameData getGameStatus(Connection con, long gameID) throws Exception {

        PreparedStatement gameStmt = null;
        ResultSet gameResult = null;

        PreparedStatement moveStmt = null;
        ResultSet moveResult = null;

        GameData gameData = null;

        try {

            gameStmt = con.prepareStatement("select play_date, winner " +
                                            "from " + GAME_TABLE + " " +
                                            "where gid = ?");
            gameStmt.setLong(1, gameID);
            gameResult = gameStmt.executeQuery();

            if (gameResult.next()) {

                gameData = new DefaultGameData();

                Timestamp playDate = gameResult.getTimestamp(1);
                gameData.setDate(new java.util.Date(playDate.getTime()));

                gameData.setWinner(gameResult.getInt(2));
            }

        } finally {
            if (gameResult != null) {
                gameResult.close();
            }
            if (gameStmt != null) {
                gameStmt.close();
            }
        }

        return gameData;
    }

    /** Loads the game information
     *  @param gameID The unique game id
     *  @param data To store the game data in
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public GameData loadGame(long gameID, GameData data) throws Exception {

        Connection con = null;
        GameData gameData = null;

        try {
            con = dbHandler.getConnection();

            gameData = loadGame(con, gameID, data);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return gameData;
    }

    /** Loads the game information
     *  @param con A database connection used to load the game
     *  @param gameID The unique game id
     *  @param data To load the game data into
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public GameData loadGame(Connection con, long gameID, GameData gameData) throws Exception {

        PreparedStatement gameStmt = null;
        ResultSet gameResult = null;

        PreparedStatement moveStmt = null;
        ResultSet moveResult = null;
log4j.debug("loadGame(" + gameID + ") start");
        try {

            gameStmt = con.prepareStatement(
                "select site_id, event_id, round, section, play_date, timer, " +
                "rated, initial_time, incremental_time, player1_pid, " +
                "player2_pid, player1_rating, player2_rating, winner, game, swapped, private, status " +
                "from " + GAME_TABLE + " " +
                "where gid = ?");
            gameStmt.setLong(1, gameID);
            gameResult = gameStmt.executeQuery();
log4j.debug("select data complete");
            if (gameResult.next()) {

                if (gameData == null) {
                    gameData = new DefaultGameData();
                }

                gameData.setGameID(gameID);

                int siteID = gameResult.getInt(1);
                int game = gameResult.getInt(15);
                
                log4j.debug("getGameSiteData for " + gameID + ", " + game + ", " + siteID);
                GameSiteData siteData = gameVenueStorer.getGameSiteData(
                    game, siteID);
                gameData.setSite(siteData.getName());
                gameData.setShortSite(siteData.getShortSite());
                gameData.setSiteURL(siteData.getURL());
				log4j.debug("done get site data");
                
                int eventID = gameResult.getInt(2);
				log4j.debug("get event data");
                GameEventData eventData = gameVenueStorer.getGameEventData(
                    game, eventID, siteData.getName());
                if (eventData != null) {
                    gameData.setEvent(eventData.getName());
                } else {
                    gameData.setEvent("");
                }
				log4j.debug("done get event data");

                gameData.setRound(gameResult.getString(3));
                gameData.setSection(gameResult.getString(4));

                Timestamp playDate = gameResult.getTimestamp(5);
                gameData.setDate(new java.util.Date(playDate.getTime()));

                String timed = gameResult.getString(6);
                if (timed.equals("S") || timed.equals("I")) {
                    gameData.setTimed(true);
                }
                else {
                    gameData.setTimed(false);
                }

                gameData.setRated(MySQLDBHandler.getBooleanValueFromDBString(gameResult.getString(7)));
                gameData.setInitialTime(gameResult.getInt(8));
                gameData.setIncrementalTime(gameResult.getInt(9));

				log4j.debug("get p1 data");
                long player1_pid = gameResult.getLong(10);
                int player1_rating = gameResult.getInt(12);
                PlayerData player1Data = loadPlayer(con, player1_pid, gameData.getSite());
                player1Data.setRating(player1_rating);
                gameData.setPlayer1Data(player1Data);
				log4j.debug("done get p1 data");

                long player2_pid = gameResult.getLong(11);
                PlayerData player2Data = loadPlayer(con, player2_pid, gameData.getSite());
               
                int player2_rating = gameResult.getInt(13);
                player2Data.setRating(player2_rating);
                gameData.setPlayer2Data(player2Data);
				log4j.debug("done get p2 data");

                gameData.setWinner(gameResult.getInt(14));
                gameData.setSwapped(gameResult.getString(16).equals("Y"));
                
                gameData.setPrivateGame(gameResult.getString(17).equals("Y"));
                gameData.setStatus(gameResult.getString(18));
                
                gameData.setGame(GridStateFactory.getGameName(game));

				log4j.debug("get moves");
				if (game == GridStateFactory.GO || game == GridStateFactory.SPEED_GO) {
                    moveStmt = con.prepareStatement("select next_move, move_num " +
                            "from " + MOVE_TABLE + " " +
                            "where gid = ? " +
                            "order by move_num");
                } else {
                    moveStmt = con.prepareStatement("select next_move, move_num " +
                            "from " + MOVE_TABLE + " " +
                            "where gid = ? " +
                            "and next_move != 361 " +
                            "order by move_num");
                }
                moveStmt.setLong(1, gameID);

                moveResult = moveStmt.executeQuery();
                if (!firstMoveCanBeOffCenter(game)) {
                    gameData.addMove(180);
                }
                while (moveResult.next()) {
                    gameData.addMove(moveResult.getInt(1));
                }
            }

            log4j.debug("loadGame(" + gameID + ") end");

        } finally {

            if (gameResult != null) {
                gameResult.close();
            }
            if (gameStmt != null) {
                gameStmt.close();
            }

            if (moveResult != null) {
                moveResult.close();
            }
            if (moveStmt != null) {
                moveStmt.close();
            }
        }

        return gameData;
    }
}