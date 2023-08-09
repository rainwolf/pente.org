package org.pente.gameServer.core;

import java.sql.*;

import org.apache.log4j.*;

import org.pente.database.*;

public class SmallLoginHandler implements LoginHandler {

    private static Category cat =
            Category.getInstance(SmallLoginHandler.class.getName());

    private DBHandler dbHandler;

    public SmallLoginHandler(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    public int isValidLogin(String name, String password) {

        boolean valid = false;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select dsg_player.status from player, dsg_player " +
                            "where player.pid = dsg_player.pid " +
                            "and player.name = ? " +
                            "and player.site_id = 2 " +
                            "and dsg_player.password = ?");
            stmt.setString(1, name);
            stmt.setString(2, password);
            result = stmt.executeQuery();

            if (!result.next()) {
                return LoginHandler.INVALID;
            }

            String status = result.getString(1);
            if (status.charAt(0) == DSGPlayerData.ACTIVE) {
                return LoginHandler.VALID;
            } else if (status.charAt(0) == DSGPlayerData.SPEED) {
                return LoginHandler.SPEED;
            } else {
                return LoginHandler.INVALID;
            }

        } catch (Throwable e) {
            cat.error("Error checking login " + name, e);
        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException e) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (Throwable t) {
                }
            }
        }

        return LoginHandler.INVALID;
    }

    public boolean login(String name, String password) {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select pid from player where name = ? and site_id = 2");
            stmt.setString(1, name);
            result = stmt.executeQuery();
            result.next();
            long pid = result.getLong(1);
            result.close();
            stmt.close();
            stmt = con.prepareStatement(
                    "update dsg_player set num_logins = num_logins + 1, last_login_date = sysdate() " +
                            "where pid = ?");
            stmt.setLong(1, pid);
            stmt.executeUpdate();

        } catch (Throwable e) {
            cat.error("Error logging in " + name, e);
            return false;
        } finally {
            if (result != null) {
                try {
                    result.close();
                } catch (SQLException e) {
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (Throwable t) {
                }
            }
        }

        return true;
    }
}