package org.pente.kingOfTheHill;

import org.apache.log4j.Category;
import org.pente.database.DBHandler;

import java.sql.*;
import java.util.*;
import java.util.Date;

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
                // if (game > 50) {
                //     stmt.setString(3, "Turn-based King of the Hill");
                // } else {
                //     stmt.setString(3, "King of the Hill");
                // }
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
                con.setAutoCommit(false);
                
                int i = 0;
                long hill_id = hill.getHillID();
                stmt = con.prepareStatement(
                        "insert into koth(step, koth_id, pid, last_game) " +
                                " VALUES(?, ?, ?, ?) " +
                                " ON DUPLICATE KEY UPDATE " +
                                " step = VALUES(step), last_game = VALUES(last_game)");

                for (Step step : hill.getSteps()) {
                    for (Player player : step.getPlayers()) {
                        stmt.setInt(1, i);
                        stmt.setLong(2, hill_id);
                        stmt.setLong(3, player.getPid());
                        stmt.setTimestamp(4, new Timestamp(player.getLastGame().getTime()));
                        stmt.addBatch();
                    }
                    i += 1;
                }

                stmt.executeBatch();
                
                con.commit();
                con.setAutoCommit(true);
                
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
                stmt = con.prepareStatement("select koth_id, pid, step, last_game from koth order by koth_id, step asc");
                result = stmt.executeQuery();
                Hill hill = null;

                while (result.next()) {
                    int hill_id = result.getInt(1);
                    long pid  = result.getLong(2);
                    int step_idx = result.getInt(3);
                    java.util.Date lastGameDate = new Date(result.getTimestamp(4).getTime());
                    if (hill == null || hill.getHillID() != hill_id) {
                        hill = new Hill();
                        hill.setHillID(hill_id);
                        hills.put(hill_id, hill);
                    }
                    if (hill.getSteps() == null) {
                        hill.setSteps(new ArrayList<>());
                    }
                    while (step_idx + 1 > hill.getSteps().size()) {
                        hill.getSteps().add(new Step());
                    }
                    hill.getSteps().get(step_idx).addPlayer(new Player(pid, lastGameDate));
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
                        stmt = con.prepareStatement("update dsg_player_game set tourney_winner='4' where game = ?  and tourney_winner='0' and computer = 'N' and pid = ?");
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

    public java.util.Date getLastGameDate(int hill_id, long pid) {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        java.util.Date last_Date = null;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("select last_game from koth where koth_id = ? and pid = ?");
                stmt.setInt(1, hill_id);
                stmt.setLong(2, pid);
                result = stmt.executeQuery();

                if (result.next()) {
                    last_Date = new java.util.Date(result.getTimestamp(1).getTime());
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
            log4j.error("MySQLKOTHStorer.getLastGameDate(" + hill_id + ", " + pid + ")");
        }

        return last_Date;
    }

    public void updatePlayerLastGameDate(int hill_id, long pid) {
        Connection con = null;
        PreparedStatement stmt = null;
//        ResultSet result = null;

        java.util.Date last_Date = null;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("update koth set last_game = NOW() where koth_id = ? and pid = ?");
                stmt.setInt(1, hill_id);
                stmt.setLong(2, pid);
                stmt.executeUpdate();

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
            log4j.error("MySQLKOTHStorer.updatePlayerLastGameDate(" + hill_id + ", " + pid + ")");
        }
    }


    public void removePlayerFromHill(int hill_id, long pid) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("delete from koth where koth_id = ? and pid = ?");
                stmt.setInt(1, hill_id);
                stmt.setLong(2, pid);
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException se) {
            log4j.error("MySQLKOTHStorer.removePlayerFromHill(" + hill_id + ", " + pid + ")");
        }
    }
}














