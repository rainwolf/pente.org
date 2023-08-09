package org.pente.gameServer.core;

import java.util.*;
import java.sql.*;

import org.pente.database.*;

public class MySQLDSGReturnEmailStorer {

    private static final String PLAYER_TABLE = "player";
    private static final String DSG_PLAYER_TABLE = "dsg_player";
    private static final String DSG_RETURN_EMAIL_TABLE = "dsg_return_email";

    private static final Vector ALL_TABLES = new Vector();

    static {
        ALL_TABLES.addElement(PLAYER_TABLE);
        ALL_TABLES.addElement(DSG_PLAYER_TABLE);
        ALL_TABLES.addElement(DSG_RETURN_EMAIL_TABLE);
    }

    private DBHandler dbHandler;

    public MySQLDSGReturnEmailStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    public void insertEmail(long playerId, String messageId, String email)
            throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;

        try {

            con = dbHandler.getConnection();


            MySQLDBHandler.lockTable(DSG_RETURN_EMAIL_TABLE, con);

            stmt = con.prepareStatement("insert into " + DSG_RETURN_EMAIL_TABLE + " " +
                    "(pid, message_id, email, send_date) " +
                    "values(?, ?, ?, ?)");
            stmt.setLong(1, playerId);
            stmt.setString(2, messageId);
            stmt.setString(3, email);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();

        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                MySQLDBHandler.unLockTables(con);
                dbHandler.freeConnection(con);
            }
        }
    }

    public DSGReturnEmailData getReturnedEmailData(String messageId)
            throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        DSGReturnEmailData returnEmailData = null;

        try {

            con = dbHandler.getConnection();


            MySQLDBHandler.lockTable(DSG_RETURN_EMAIL_TABLE, con);

            stmt = con.prepareStatement("select pid, email, send_date " +
                    "from " + DSG_RETURN_EMAIL_TABLE + " " +
                    "where message_id = ?");
            stmt.setString(1, messageId);

            result = stmt.executeQuery();
            if (result.next()) {
                returnEmailData = new DSGReturnEmailData();
                returnEmailData.setPid(new Long(result.getString(1)).longValue());
                returnEmailData.setEmail(result.getString(2));
                returnEmailData.setMessageId(messageId);
                Timestamp sendDate = result.getTimestamp(3);
                returnEmailData.setSendDate(new java.util.Date(sendDate.getTime()));
            }

        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                MySQLDBHandler.unLockTables(con);
                dbHandler.freeConnection(con);
            }
        }

        return returnEmailData;
    }
}
