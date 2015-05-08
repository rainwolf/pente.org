/** SimpleMySQLGameStats.java
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

import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.turnBased.TBGame;

public class SimpleMySQLGameStats implements GameStats {

    private int             numGames;
    private int             numDSGGames;
    private int             numMoves;
    private int             numPlayers;
    private int				numDSGPlayers;
    private int             numSites;
    private int				numTbGames;
    
    private DBHandler       dbHandler;
    private StatsUpdater    statsUpdater;

    private static Category cat = Category.getInstance(SimpleMySQLGameStats.class.getName());

    public static void main(String args[]) throws Exception {

        DBHandler handler = null;//broken - new MySQLDBHandler(new File(args[0]));
        SimpleMySQLGameStats stats = new SimpleMySQLGameStats(handler, 1000 * 5);

        try {
            Thread.sleep(1000 * 30);
        } catch(InterruptedException ex) {
        }

        stats.destroy();
        stats = null;

    }

    public SimpleMySQLGameStats(DBHandler dbHandler, int delay) {
        this.dbHandler = dbHandler;
        statsUpdater = new StatsUpdater(delay);
    }

    public void destroy() {

        if (statsUpdater != null) {
            statsUpdater.stopRunning();
            statsUpdater = null;
        }
    }

    public int getNumGames() {
        return numGames;
    }
    public int getNumDSGGames() {
        return numDSGGames;
    }
    public int getNumMoves() {
        return numMoves;
    }
    public int getNumPlayers() {
        return numPlayers;
    }
    public int getNumDSGPlayers() {
        return numDSGPlayers;
    }
    public int getNumSites() {
        return numSites;
    }
    public int getNumTbGames() {
    	return numTbGames;
    }

    /** Gets the stats from the database.  Called by the StatsUpdater thread */
    private void getStats() throws Exception {

        PreparedStatement stmt = null;
        ResultSet result = null;
        Connection con = null;

        cat.debug("getStats()");

        try {

            con = dbHandler.getConnection();

            // get num games
            stmt = con.prepareStatement(
                "select count(*) " +
                "from pente_game");
            result = stmt.executeQuery();

            if (result.next()) {
                numGames = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

            // get num DSG games
            stmt = con.prepareStatement(
                "select count(*) " +
                "from pente_game " +                "where site_id = 2");
            result = stmt.executeQuery();

            if (result.next()) {
                numDSGGames = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            
            // get num tb games
            stmt = con.prepareStatement(
                "select count(*) " +
                "from tb_game " +
                "where state = '" + TBGame.STATE_ACTIVE + "'" );
            result = stmt.executeQuery();

            if (result.next()) {
                numTbGames = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

            // get num moves
            stmt = con.prepareStatement(
                "select count(*) " +
                "from pente_move, pente_game " +
                "where pente_move.gid = pente_game.gid");
            result = stmt.executeQuery();

            if (result.next()) {
                numMoves = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

            // get num players
            stmt = con.prepareStatement("select count(*) from player");
            result = stmt.executeQuery();

            if (result.next()) {
                numPlayers = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

            // get num players
            stmt = con.prepareStatement("select count(*) from player " +
            	"where site_id = 2");
            result = stmt.executeQuery();

            if (result.next()) {
                numDSGPlayers = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

            // get num sites
            stmt = con.prepareStatement("select count(*) from game_site");
            result = stmt.executeQuery();

            if (result.next()) {
                numSites = result.getInt(1);
            }

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }

        } finally {

            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    private class StatsUpdater implements Runnable {

        private int                 delay;
        private Thread              thread;
        private volatile boolean    running = true;

        private StatsUpdater(int delay) {
            this.delay = delay;

            thread = new Thread(this, "StatsUpdater");
            thread.start();
        }

        public void run() {

            while (running) {

                try {
                    getStats();
                    Thread.sleep(delay);

                } catch (InterruptedException iex) {
                } catch (Throwable t) {
                    cat.error("Problem getting stats", t);
                }
            }
        }

        public void stopRunning() {

            running = false;

            if (thread != null) {
                thread.interrupt();
            }
        }
    }
}