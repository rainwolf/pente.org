package org.pente.message;

import java.sql.*;
import java.util.*;

import org.pente.database.*;

import org.apache.log4j.*;

public class MySQLDSGMessageStorer implements DSGMessageStorer {

    private Category log4j = Category.getInstance(
            MySQLDSGMessageStorer.class.getName());

    private DBHandler dbHandler;

    public MySQLDSGMessageStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    public void createMessage(DSGMessage message, boolean ccSender)
            throws DSGMessageStoreException {
        createMessage(message);
    }

    public void createMessage(DSGMessage message)
            throws DSGMessageStoreException {

        log4j.debug("MySQLDSGMessageStorer.createMessage(" + message.getFromPid() +
                "->" + message.getToPid() + " : " + message.getSubject() + ")");

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        try {
            try {
                con = dbHandler.getConnection();

                stmt = con.prepareStatement(
                        "insert into dsg_message " +
                                "(from_pid, to_pid, subject, body, creation_date) " +
                                "values(?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);

                stmt.setLong(1, message.getFromPid());
                stmt.setLong(2, message.getToPid());
                stmt.setString(3, message.getSubject());
                stmt.setString(4, message.getBody());
                stmt.setTimestamp(5, new Timestamp(message.getCreationDate().getTime()));

                int rows = stmt.executeUpdate();
                result = stmt.getGeneratedKeys();

                if (result.next()) {
                    int mid = result.getInt(1);
                    message.setMid(mid);
                    message.setCreationDate(new java.util.Date());
                    message.setRead(false);
                    message.setViewable(true);
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
            throw new DSGMessageStoreException(se);
        }
    }

    public void readMessage(DSGMessage message) throws DSGMessageStoreException {
        log4j.debug("MySQLDSGMessageStorer.readMessage(" + message.getMid() + ")");

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "update dsg_message " +
                            "set read_fl = 'Y' " +
                            "where mid = ?");
            stmt.setLong(1, message.getMid());
            stmt.executeUpdate();

            message.setRead(true);

        } catch (SQLException se) {
            throw new DSGMessageStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }
    }

    public void deleteMessage(int mid)
            throws DSGMessageStoreException {
        log4j.debug("MySQLDSGMessageStorer.deleteMessage(" + mid + ")");

        Connection con = null;
        PreparedStatement stmt = null;

        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "update dsg_message " +
                            "set viewable = 'N' " +
                            "where mid = ?");
            stmt.setLong(1, mid);
            stmt.executeUpdate();

        } catch (SQLException se) {
            throw new DSGMessageStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }
    }

    public DSGMessage getMessage(int mid) throws DSGMessageStoreException {
        log4j.debug("MySQLDSGMessageStorer.getMessage(" + mid + ")");
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        DSGMessage m = null;

        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "select mid, from_pid, to_pid, subject, body, creation_date, read_fl " +
                            "from dsg_message " +
                            "where mid = ? and viewable = 'Y'");
            stmt.setInt(1, mid);

            result = stmt.executeQuery();
            while (result.next()) {
                m = new DSGMessage();
                m.setMid(result.getInt(1));
                m.setFromPid(result.getLong(2));
                m.setToPid(result.getLong(3));
                m.setSubject(result.getString(4));
                m.setBody(result.getString(5));
                m.setCreationDate(new java.util.Date(result.getTimestamp(6).getTime()));
                m.setRead(result.getString(7).equals("Y"));
            }

        } catch (SQLException se) {
            throw new DSGMessageStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }

        return m;
    }

    public int getNumNewMessages(long pid) throws DSGMessageStoreException {
        throw new UnsupportedOperationException("Not supported");
    }

    public List<DSGMessage> getMessages(long pid)
            throws DSGMessageStoreException {

        log4j.debug("MySQLDSGMessageStorer.getMessages(" + pid + ")");
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List<DSGMessage> messages = new ArrayList<DSGMessage>();

        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "select mid, from_pid, to_pid, subject, body, creation_date, read_fl " +
                            "from dsg_message " +
                            "where to_pid = ? " +
                            "and viewable = 'Y' " +
                            "order by creation_date desc limit 50");
            stmt.setLong(1, pid);

            result = stmt.executeQuery();
            while (result.next()) {
                DSGMessage m = new DSGMessage();
                m.setMid(result.getInt(1));
                m.setFromPid(result.getLong(2));
                m.setToPid(result.getLong(3));
                m.setSubject(result.getString(4));
                m.setBody(result.getString(5));
                m.setCreationDate(new java.util.Date(result.getTimestamp(6).getTime()));
                m.setRead(result.getString(7).equals("Y"));

                messages.add(m);
            }

        } catch (SQLException se) {
            throw new DSGMessageStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }

        return messages;
    }

    @Override
    public List<DSGMessage> getNextMessages(long pid, long start) throws DSGMessageStoreException {
        log4j.debug("MySQLDSGMessageStorer.getNextMessages(" + pid + ", " + start + ")");
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List<DSGMessage> messages = new ArrayList<DSGMessage>();

        try {
            con = dbHandler.getConnection();

            stmt = con.prepareStatement(
                    "select mid, from_pid, to_pid, subject, body, creation_date, read_fl " +
                            "from dsg_message " +
                            "where to_pid = ? " +
                            "and viewable = 'Y' " +
                            "order by creation_date desc limit ?, 50");
            stmt.setLong(1, pid);
            stmt.setLong(2, start);

            result = stmt.executeQuery();
            while (result.next()) {
                DSGMessage m = new DSGMessage();
                m.setMid(result.getInt(1));
                m.setFromPid(result.getLong(2));
                m.setToPid(result.getLong(3));
                m.setSubject(result.getString(4));
                m.setBody(result.getString(5));
                m.setCreationDate(new java.util.Date(result.getTimestamp(6).getTime()));
                m.setRead(result.getString(7).equals("Y"));

                messages.add(m);
            }

        } catch (SQLException se) {
            throw new DSGMessageStoreException(se);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException se) {
                }
            }
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException se) {
                }
            }
        }

        return messages;
    }
}
