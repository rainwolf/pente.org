package org.pente.notifications;

import org.apache.log4j.Category;
import org.pente.database.DBHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by waliedothman on 28/01/2017.
 */
public class MySQLNotificationServer implements NotificationServer {
    private static final Category log4j = Category.getInstance(
            MySQLNotificationServer.class.getName());

    private DBHandler dbHandler;

    public MySQLNotificationServer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /** Make sure the database handler is destroyed
     */
    public void destroy() {
        dbHandler.destroy();
    }

    @Override
    public void registerDevice(long pid, String token, int device) throws NotificationServerException {
        Connection con = null;
        PreparedStatement stmt = null;

        String table = null;
        if (device == iOS) {
            table = "notifications";
        } else if (device == ANDROID) {
            table = "notifications_android";
        }
        
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "INSERT INTO " + table + " (pid, token, lastping) VALUES (?, ?, NOW())" +
                                " ON DUPLICATE KEY UPDATE " +
                                " token = VALUES(token), lastping = VALUES(lastping)");
                stmt.setLong(1, pid);
                stmt.setString(2, token);
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new NotificationServerException("registerDevice of "+pid+" problem", t);
        }
    }

    @Override
    public Map<String, Date> getTokens(long pid, int device) throws NotificationServerException {
        String table = null;
        if (device == iOS) {
            table = "notifications";
        } else if (device == ANDROID) {
            table = "notifications_android";
        }

        Map<String, Date> tokensMap = new HashMap<>();
        try {
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet result = null;

            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "select token, lastping " +
                                " from " + table +
                                " where pid = ?");
                stmt.setLong(1, pid);
                result = stmt.executeQuery();
                while (result.next()) {
                    String token = result.getString(1);
                    Date lastping = new Date(result.getTimestamp(2).getTime());
                    tokensMap.put(token, lastping);
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
        } catch (Throwable t) {
            throw new NotificationServerException("getTokens for "+pid+" problem", t);
        }
        return tokensMap;
    }

    @Override
    public void removeInvalidToken(long pid, String token, int device) throws NotificationServerException {
        Connection con = null;
        PreparedStatement stmt = null;

        String table = null;
        if (device == iOS) {
            table = "notifications";
        } else if (device == ANDROID) {
            table = "notifications_android";
        }

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "DELETE from " + table + " where token = ?");
                stmt.setString(1, token);
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new NotificationServerException("removeInvalidToken of "+pid+" problem", t);
        }
    }

    @Override
    public void sendMoveNotification(String fromName, long pid, long gameId, String gameName) {
        // no sql applicable
    }

    @Override
    public void sendInvitationNotification(String fromName, long pid, long setId, String gameName) {
        // no sql applicable
    }

    @Override
    public void sendMessageNotification(String fromName, long pid, long messageId, String subject) {
        // no sql applicable
    }

    @Override
    public void sendAdminNotification(String message) {
        // no sql applicable
    }
    
    public void removeOldTokens() throws NotificationServerException {
        Connection con = null;
        PreparedStatement stmt = null;

        Date twoWeeksAgo = new Date();
        long timeMillis = twoWeeksAgo.getTime();
        twoWeeksAgo.setTime(timeMillis - 1000L*3600*24*15);

        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                        "DELETE from notifications where lastping < ?");
                stmt.setTimestamp(1, new Timestamp(twoWeeksAgo.getTime()));
                stmt.executeUpdate();
                stmt.close();
                stmt = con.prepareStatement(
                        "DELETE from notifications_android where lastping < ?");
                stmt.setTimestamp(1, new Timestamp(twoWeeksAgo.getTime()));
                stmt.executeUpdate();

            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (Throwable t) {
            throw new NotificationServerException("removeOldTokens problem", t);
        }
    }
}
