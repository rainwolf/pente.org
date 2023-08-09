package org.pente.gameServer.server;

import java.util.*;
import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.core.*;

public class ActivityLogger {

    private Category log4j = Category.getInstance("ActivityLogger");

    private DBHandler dbHandler;
    private DSGPlayerStorer dsgPlayerStorer;
    private Resources resources;

    private Collection players = new ArrayList();
    private Collection tables = new ArrayList();

    public ActivityLogger(Resources resources) {
        this.dbHandler = resources.getDbHandler();
        this.dsgPlayerStorer = resources.getDsgPlayerStorer();
        this.resources = resources;
    }

    public void viewPage(ActivityData activity, String page) {
        log("view", activity, page);
    }

    public boolean login(ActivityData activity, String page) {
        log("login", activity, null);
        storeActivity(activity);
        return true;
    }

    public boolean joinPlayer(ActivityData activity) {
        log("join", activity, null);
        ActivityData d = null;
        synchronized (players) {
            d = findMatch(activity);
            players.add(activity);
        }

        if (d != null) {
            log4j.info("2: join potential match between " +
                    activity + " and " + d);
        }
        return false;
    }

    public boolean exitPlayer(ActivityData activity) {
        log("exit", activity, null);
        synchronized (players) {
            players.remove(activity);
        }
        return false;
    }

    /**
     * return boolean - true if access allowed
     * false if access not allowed
     */
    public boolean viewDb(ActivityData activity, long hashCode, int[] moves,
                          String searchData) {

        // currently only ban ppl from games history
        if (isBanned(activity)) {
            log4j.info("4: view db, banning access for " + activity);
            return true;
        }

        // check if checking db against same game as being played in game room
        ActivityTableData td = new ActivityTableData(-1, -1, hashCode, moves, false);
        ActivityTableData m = null;
        synchronized (tables) {
            m = findGameMatch(td);
        }
        if (m != null) {
            // if matches a rated game currently being played, ban access
            // (this could be an innocent coincidence, but better safe than sorry)
            if (m.isRated()) {
                log4j.info("6: view db by " + activity + " match between " + td + " and " + m + ", blocking");
                return true;
            } else {
                // not a rated game, so ok
                log4j.info("8: view db by " + activity + " match between " + td + " and " + m + ", not blocking");
                return false;
            }
        }

        // check if player is checking db at same time as in game room
        ActivityData d = null;
        synchronized (players) {
            d = findMatch(activity);
        }
        if (d != null) {
            if (d.playingRatedGame()) {
                log4j.info("3: view db match between " +
                        activity + " and " + d + ", blocking");
                return true;
            } else {
                log4j.info("9: view db match between " +
                        activity + " and " + d + ", not blocking");
                return false;
            }

            // if tournament game, don't allow access
            // tournament games rated, so don't need this anymore
            //ServerData s = resources.getServerData((int) d.getServerId());
            //if (s.isTournament()) {
            //    log4j.info("7: blocking access during tournament game");
            //    return true;
            //}
        } else {
            log4j.info("view db " + activity + " " + td + " [" + searchData + "]");
        }

        return false;
    }


    public void startGame(long serverId, int table, String p1, String p2,
                          boolean rated) {
        log4j.info("game [" + serverId + ":" + table + " start " + p1 + ", " + p2 + "]");

        ActivityTableData data = new ActivityTableData(serverId, table,
                0, null, rated);
        tables.add(data);

        // find p1,p2 activity data
        for (Iterator it = players.iterator(); it.hasNext(); ) {
            ActivityData d = (ActivityData) it.next();
            if (d.getPlayerName().equals(p1)) {
                d.addActiveGame(data);
            } else if (d.getPlayerName().equals(p2)) {
                d.addActiveGame(data);
            }
        }
    }

    public void updateGameState(long serverId, int table, long hashCode,
                                int moves[]) {

        ActivityTableData update = null;
        boolean found = false;
        ActivityTableData match = null;
        synchronized (tables) {
            for (Iterator it = tables.iterator(); it.hasNext(); ) {
                update = (ActivityTableData) it.next();
                if (update.getTableNum() == table) {
                    update.setHashCode(hashCode);
                    update.setMoves(moves);
                    found = true;
                    break;
                }
            }
            if (!found) {
                log4j.error("updateGameState, table data not found");
                //update = new ActivityTableData(serverId, table, hashCode, moves);
                //tables.add(update);
            }
        }
        match = findGameMatch(update);
        if (match != null) {
            log4j.info("5: game match between " + update + " and " + match);
        } else {
            log4j.info("game " + update);
        }
    }

    public void gameOver(long serverId, int table, String p1, String p2) {

        log4j.info("game [" + serverId + ":" + table + " over " + p1 + ", " + p2 + "]");
        synchronized (tables) {
            for (Iterator it = tables.iterator(); it.hasNext(); ) {
                ActivityTableData d = (ActivityTableData) it.next();
                if (d.getTableNum() == table && d.getServerId() == serverId) {
                    it.remove();

                    for (Iterator it2 = players.iterator(); it2.hasNext(); ) {
                        ActivityData d2 = (ActivityData) it2.next();
                        log4j.debug("checking against " + d2);
                        if (d2.getPlayerName().equals(p1)) {
                            d2.removeActiveGame(d);
                        } else if (d2.getPlayerName().equals(p2)) {
                            d2.removeActiveGame(d);
                        }
                    }
                    return;
                }
            }
        }
    }

    private ActivityTableData findGameMatch(ActivityTableData activity) {
        for (Iterator it = tables.iterator(); it.hasNext(); ) {
            ActivityTableData d = (ActivityTableData) it.next();
            if (d.getHashCode() == activity.getHashCode() &&
                    d.getTableNum() != activity.getTableNum() &&
                    d.getNumMoves() > 2 && activity.getNumMoves() == d.getNumMoves()) {
                return d;
            }
        }
        return null;
    }

    private ActivityData findMatch(ActivityData activity) {
        outer:
        for (Iterator it = players.iterator(); it.hasNext(); ) {
            ActivityData d = (ActivityData) it.next();
            if (d.matches(activity)) {
                return d;
            }
        }
        return null;
    }

    public void log(String type, ActivityData activity, String data) {
        String tolog = type + " " + activity;
        if (data != null) {
            tolog += ", " + data;
        }
        log4j.info(tolog);
    }

    public ActivityData[] getPlayers() {
        synchronized (players) {
            return (ActivityData[]) players.toArray(new ActivityData[players.size()]);
        }
    }

    private void storeActivity(ActivityData activity) {

//        Connection con = null;
//        PreparedStatement stmt = null;
//        DSGPlayerData dsgPlayerData = null;
//        try {
//            dsgPlayerData = dsgPlayerStorer.loadPlayer(activity.getPlayerName());
//            
//        } catch (DSGPlayerStoreException e) {
//            log4j.error("Error loading player data for store activity.");
//            return;
//        }
//        
//        try {
//            con = dbHandler.getConnection();
//            stmt = con.prepareStatement(
//                "insert into dsg_ip(pid, ip, access_time) " +
//                "values(?, ?, sysdate())");
//            stmt.setLong(1, dsgPlayerData.getPlayerID());
//            stmt.setString(2, "0.0.0.0");
////            stmt.setString(2, activity.getAddressStr());
//            stmt.execute();
//            
//        } catch (Exception e) {
//            log4j.error("Error saving activity to db.", e);
//        } finally {
//            try {
//                if (stmt != null) {
//                    stmt.close();
//                }
//                dbHandler.freeConnection(con);
//            } catch (Exception e) {
//                log4j.error("Error saving activity to db, release.", e);
//            }
//        }
    }

    private boolean isBanned(ActivityData activity) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        DSGPlayerData dsgPlayerData = null;
        try {
            dsgPlayerData = dsgPlayerStorer.loadPlayer(activity.getPlayerName());
        } catch (DSGPlayerStoreException e) {
            log4j.error("Error loading player data for store activity.");
            return false;
        }

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select 1 " +
                            "from dsg_ip " +
                            "where (pid = ? and ban = 'Y') or (ip = ? and ban = 'Y')");
            stmt.setLong(1, dsgPlayerData.getPlayerID());
            stmt.setString(2, activity.getAddressStr());
            result = stmt.executeQuery();

            return result.next();

        } catch (Exception e) {
            log4j.error("Error checking banned in db for " + activity, e);
        } finally {
            try {
                if (result != null) {
                    result.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                dbHandler.freeConnection(con);
            } catch (Exception e) {
                log4j.error("Error checking banned in db, release.", e);
            }
        }
        return false;
    }
}
