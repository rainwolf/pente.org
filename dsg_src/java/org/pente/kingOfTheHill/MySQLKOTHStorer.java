package org.pente.kingOfTheHill;

import org.apache.log4j.Category;
import org.pente.database.DBHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by waliedothman on 26/06/16.
 */
public class MySQLKOTHStorer implements KOTHStorer {
    private Category log4j = Category.getInstance(
            MySQLKOTHStorer.class.getName());

    private DBHandler dbHandler;

    public MySQLKOTHStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    public int getEventId(int game) throws KOTHException {
        log4j.debug("MySQLKOTHStorer.getEventId(" + game + ")");

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        int eid = -1;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "select eid " +
                                "from game_event " +
                                "where game = ? " +
                                "and site_id = ? " +
                                "and name = ?");

                stmt.setInt(1, game);
                stmt.setInt(2, 2);
                stmt.setString(3, "King of the Hill");

                result = stmt.executeQuery();

                if (result.next()) {
                    eid = result.getInt(1);
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
        } catch (SQLException se) {
            throw new KOTHException(se);
        }

        return eid;
    }

    public void storeHill(Hill hill) throws KOTHException {
        log4j.debug("MySQLKOTHStorer.storeHill(" + hill.getHillID() + ")");

        Connection con = null;
        PreparedStatement stmt = null;
//        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();
                int i = 0;
                long hill_id = hill.getHillID();
                stmt = con.prepareStatement(
                        "insert into koth(step, koth_id, pid) " +
                                " VALUES(?, ?, ?) " +
                                " ON DUPLICATE KEY UPDATE " +
                                " step = VALUES(step)");

                for (Step step : hill.getSteps()) {
                    for (long pid : step.getPlayers()) {
                        stmt.setInt(1, i);
                        stmt.setLong(2, hill_id);
                        stmt.setLong(3, pid);
                        stmt.addBatch();
                    }
                    i += 1;
                }

                stmt.executeBatch();
//                int [] numUpdates=prepStmt.executeBatch();
//                for (int i=0; i < numUpdates.length; i++) {
//                    if (numUpdates[i] == -2)
//                        System.out.println("Execution " + i +
//                                ": unknown number of rows updated");
//                    else
//                        System.out.println("Execution " + i +
//                                "successful: " numUpdates[i] + " rows updated");
//                }
//                con.commit();

            } finally {
//                if (result != null) {
//                    result.close();
//                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException se) {
            throw new KOTHException(se);
        }
    }

    public Map<Integer, Hill> loadHills()  throws KOTHException {
        Map<Integer, Hill> hills = new HashMap<>();

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("select koth_id, pid, step from koth order by koth_id, step asc");
                result = stmt.executeQuery();
                Hill hill = null;

                while (result.next()) {
                    int hill_id = result.getInt(1);
                    long pid  = result.getLong(2);
                    int step_idx = result.getInt(3);
                    if (hill == null || hill.getHillID() != hill_id) {
                        hill = new Hill();
                        hill.setHillID(hill_id);
                        hills.put(new Integer(hill_id), hill);
                    }
                    if (hill.getSteps() == null) {
                        hill.setSteps(new ArrayList<>());
                    }
                    if (step_idx + 1 >= hill.getSteps().size()) {
                        hill.getSteps().add(new Step());
                    }
                    hill.getSteps().get(step_idx).addPlayer(pid);
                }


//                int i = 0;
//                long hill_id = hill.getHillID();
//                for (Step step : hill.getSteps()) {
//                    for (long pid : step.getPlayers()) {
//
//                        stmt.setInt(1, i);
//                        stmt.setLong(2, hill_id);
//                        stmt.setLong(3, pid);
//                        stmt.addBatch();
//                    }
//                    i += 1;
//                }


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
        } catch (SQLException se) {
            throw new KOTHException(se);
        }

        return hills;
    }

    public void adjustCrown(int game, long pid) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("update dsg_player_game set tourney_winner='0' where tourney_winner='4' and computer = 'N' and game = ?");
                stmt.setInt(1, game);
                stmt.executeUpdate();

                if (pid != 0) {
                    stmt = con.prepareStatement("select * from dsg_player_game where tourney_winner != '0' and computer = 'N' and game = ? and pid = ?");
                    stmt.setInt(1, game);
                    stmt.setLong(2, pid);
                    result = stmt.executeQuery();

                    if (!result.next()) {
                        stmt = con.prepareStatement("update dsg_player_game set tourney_winner='4' where game = ?  and computer = 'N' and pid = ?");
                        stmt.setInt(1, game);
                        stmt.setLong(2, pid);
                        stmt.executeUpdate();
                    }
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
        } catch (SQLException se) {
            log4j.error("MySQLKOTHStorer.adjustCrown(" + game + ", " + pid + ")");
        }
    }

    public long getCrownPid(int game) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        long pid = 0;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("select pid from dsg_player_game where tourney_winner = '4' and computer = 'N' and game = ?");
                stmt.setInt(1, game);
                result = stmt.executeQuery();

                if (result.next()) {
                    pid = result.getLong(1);
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
        } catch (SQLException se) {
            log4j.error("MySQLKOTHStorer.getCrownPid(" + game + ", " + pid + ")");
        }

        return pid;
    }



}














