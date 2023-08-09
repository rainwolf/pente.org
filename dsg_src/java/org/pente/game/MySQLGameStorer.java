/**
 * MySQLGameStorer.java
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

import java.sql.*;
import java.util.*;

import org.pente.database.*;

/** Abstract template implementation of GameStorer.  Subclasses
 *  need to implement addGame() and gameAlreadyStored().  This class
 *  allows individual games to subclass for storing games in different
 *  tables with possibly different information.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public abstract class MySQLGameStorer implements PlayerStorer, GameStorer {

    /** The name of the table with the player information */
    protected static final String PLAYER_TABLE = "player";

    protected static final Vector PLAYER_TABLES = new Vector();

    static {
        PLAYER_TABLES.addElement(PLAYER_TABLE);
        PLAYER_TABLES.addElement(MySQLGameVenueStorer.GAME_SITE_TABLE);
    }

    protected DBHandler dbHandler;
    protected GameVenueStorer gameVenueStorer;

    public MySQLGameStorer(DBHandler dbHandler, GameVenueStorer gameVenueStorer) throws Exception {
        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
    }

    /** Make sure the database handler is destroyed
     */
    public void destroy() {
        dbHandler.destroy();
    }

    /** Store the game information
     *  @param data The GameData for a game
     *  @exception Exception If the game can't be stored
     */
    public void storeGame(GameData data) throws Exception {

        Connection con = null;

        try {

            con = dbHandler.getConnection();

            if (!playerAlreadyStored(con, data.getPlayer1Data().getUserID(), data.getSite())) {
                storePlayer(con, data.getPlayer1Data(), data.getSite());
            }
            if (!playerAlreadyStored(con, data.getPlayer2Data().getUserID(), data.getSite())) {
                storePlayer(con, data.getPlayer2Data(), data.getSite());
            }

            // add the game
            storeGame(con, data);

//        } finally {
//            if (con != null) {
//                dbHandler.freeConnection(con);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /** Add the game to the database, implemented by individual game subclasses
     *  and called by storeGame(GameData)
     *  @param con A database connection
     *  @param data The GameData for a game
     *  @exception Exception If the game can't be added
     */
    public abstract void storeGame(Connection con, GameData data) throws Exception;


    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(long playerID, String site) throws Exception {

        Connection con = null;
        boolean stored = false;

        try {
            con = dbHandler.getConnection();

            stored = playerAlreadyStored(con, playerID, site);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return stored;
    }

    /** Checks if a player exists in the database
     *  @param con A database connection
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @exception Exception If there is a problem checking for a player
     */
    public boolean playerAlreadyStored(Connection con, long playerID, String site) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        boolean exists = true;

        try {

            int siteID = gameVenueStorer.getSiteID(site);
            if (siteID == -1) {
                exists = false;
            } else {
                stmt = con.prepareStatement("select 1 " +
                        "from " + PLAYER_TABLE + " " +
                        "where pid = ? " +
                        "and site_id = ?");
                stmt.setLong(1, playerID);
                stmt.setInt(2, siteID);

                result = stmt.executeQuery();
                exists = result.next();
            }

        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException ex) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                }
            }
        }

        return exists;
    }

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(String name, String site) throws Exception {

        Connection con = null;
        boolean stored = false;

        try {
            con = dbHandler.getConnection();

            stored = playerAlreadyStored(con, name, site);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return stored;
    }

    /** Checks to see if the player has already been stored
     *  @param con A database connection
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(Connection con, String name, String site) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        boolean exists = true;

        try {
            int siteID = gameVenueStorer.getSiteID(site);
            if (siteID == -1) {
                exists = false;
            } else {
                stmt = con.prepareStatement("select 1 " +
                        "from " + PLAYER_TABLE + " " +
                        "where name = ? " +
                        "and site_id = ?");
                stmt.setString(1, name);
                stmt.setInt(2, siteID);

                result = stmt.executeQuery();
                exists = result.next();
            }

        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException ex) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                }
            }
        }

        return exists;
    }

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @exception If the player cannot be stored
     */
    public void storePlayer(PlayerData data, String site) throws Exception {

        Connection con = null;

        try {
            con = dbHandler.getConnection();

            storePlayer(con, data, site);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /** Add a player to the database
     *  @param con A database connection
     *  @param playerData Information about a player
     *  @exception Exception If the player can't be added
     */
    public void storePlayer(Connection con, PlayerData playerData,
                            String site) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        boolean newPlayer = false;

        try {

            int siteID = gameVenueStorer.getSiteID(site);

            if (playerAlreadyStored(con, playerData.getUserIDName(), site) ||
                    (playerData.getUserID() != 0 && playerAlreadyStored(
                            con, playerData.getUserID(), site))) {
                return;
            }

            // if this is a new player
            if (playerData.getUserID() == 0) {

                stmt = con.prepareStatement("select max(pid) + 1 " +
                        "from " + PLAYER_TABLE + " " +
                        "where site_id = ?");
                stmt.setInt(1, siteID);

                result = stmt.executeQuery();
                if (result.next()) {
                    playerData.setUserID(result.getLong(1));
                }

                newPlayer = true;

                if (result != null) {
                    result.close();
                }

                if (stmt != null) {
                    stmt.close();
                }
            }

            stmt = con.prepareStatement("insert into " + PLAYER_TABLE + " " +
                    "(pid, name, site_id, name_lower) " +
                    "values(?, ?, ?, lower(?))");

            stmt.setLong(1, playerData.getUserID());
            stmt.setString(2, playerData.getUserIDName());
            stmt.setInt(3, siteID);
            stmt.setString(4, playerData.getUserIDName());

            stmt.executeUpdate();

        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException ex) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                }
            }
        }
    }

    /** Loads the player information
     *  @param playerID The unique player id
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(long playerID, String site) throws Exception {

        Connection con = null;
        PlayerData playerData = null;

        try {
            con = dbHandler.getConnection();

            playerData = loadPlayer(con, playerID, site);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return playerData;
    }

    /** Loads the player information
     *  @param con A database connection to load the player from
     *  @param playerID The unique player id
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(Connection con, long playerID, String site) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        PlayerData playerData = null;

        try {

            int siteID = gameVenueStorer.getSiteID(site);
            if (siteID != -1) {

                stmt = con.prepareStatement("select name " +
                        "from " + PLAYER_TABLE + " " +
                        "where pid = ? " +
                        "and site_id = ?");
                stmt.setLong(1, playerID);
                stmt.setInt(2, siteID);

                result = stmt.executeQuery();

                if (result.next()) {

                    playerData = new DefaultPlayerData();
                    playerData.setUserID(playerID);
                    playerData.setUserIDName(result.getString(1));
                }
            }

        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                }
            }
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException ex) {
                }
            }
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

        Connection con = null;
        PlayerData playerData = null;

        try {
            con = dbHandler.getConnection();

            playerData = loadPlayer(con, name, site);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }

        return playerData;
    }

    /** Loads the player information
     *  @param con A database connection to load the player from
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(Connection con, String name, String site) throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        PlayerData playerData = null;

        try {
            int siteID = gameVenueStorer.getSiteID(site);
            if (siteID > 0) {

                stmt = con.prepareStatement("select pid " +
                        "from " + PLAYER_TABLE + " " +
                        "where name = ? " +
                        "and site_id = ?");
                stmt.setString(1, name);
                stmt.setInt(2, siteID);

                //MySQLDBHandler.lockTable(PLAYER_TABLE, con);
                result = stmt.executeQuery();

                if (result.next()) {

                    playerData = new DefaultPlayerData();
                    playerData.setUserID(result.getLong(1));
                    playerData.setUserIDName(name);
                }
            }

        } finally {
            //if (con != null) {
            //    MySQLDBHandler.unLockTables(con);
            //}
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ex) {
                }
            }
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException ex) {
                }
            }
        }

        return playerData;
    }
}