package org.pente.gameServer.kids;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.pente.database.DBHandler;
import org.pente.gameServer.core.DSGPlayerStoreException;

public class MySQLKidStorer implements KidStorer {

    private final DBHandler dbHandler;

    public MySQLKidStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /** Runs a statement that returns nothing. */
    private void update(String what, String sql, Object... args) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(sql);
                bind(stmt, args);
                stmt.executeUpdate();
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        } catch (SQLException e) {
            throw new DSGPlayerStoreException("Problem " + what, e);
        }
    }

    private static void bind(PreparedStatement stmt, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) {
                stmt.setNull(i + 1, Types.BIGINT);
            } else if (a instanceof Long) {
                stmt.setLong(i + 1, (Long) a);
            } else if (a instanceof String) {
                stmt.setString(i + 1, (String) a);
            } else if (a instanceof Timestamp) {
                stmt.setTimestamp(i + 1, (Timestamp) a);
            } else {
                throw new IllegalArgumentException("unsupported arg " + a);
            }
        }
    }

    public Set<Long> getWhitelist(long kidPid) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        Set<Long> pids = new HashSet<Long>();
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                    "select allowed_pid from dsg_kid_whitelist where kid_pid = ?");
                stmt.setLong(1, kidPid);
                result = stmt.executeQuery();
                while (result.next()) {
                    pids.add(result.getLong(1));
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
        } catch (SQLException e) {
            throw new DSGPlayerStoreException("Problem getting whitelist for " + kidPid, e);
        }
        return pids;
    }

    public void addToWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException {
        update("adding to whitelist of " + kidPid,
            "insert ignore into dsg_kid_whitelist (kid_pid, allowed_pid, last_update_date) " +
            "values (?, ?, sysdate())", kidPid, allowedPid);
    }

    public void removeFromWhitelist(long kidPid, long allowedPid) throws DSGPlayerStoreException {
        update("removing from whitelist of " + kidPid,
            "delete from dsg_kid_whitelist where kid_pid = ? and allowed_pid = ?",
            kidPid, allowedPid);
    }

    public KidSettings getSettings(long kidPid) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                    "select copy_messages, copy_live_chat, copy_tb_messages from dsg_kid_settings where kid_pid = ?");
                stmt.setLong(1, kidPid);
                result = stmt.executeQuery();
                if (result.next()) {
                    return new KidSettings(kidPid,
                        "Y".equals(result.getString(1)), "Y".equals(result.getString(2)),
                        "Y".equals(result.getString(3)));
                }
                return KidSettings.off(kidPid);
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
        } catch (SQLException e) {
            throw new DSGPlayerStoreException("Problem getting kid settings for " + kidPid, e);
        }
    }

    public void storeSettings(KidSettings s) throws DSGPlayerStoreException {
        String m = s.isCopyMessages() ? "Y" : "N";
        String c = s.isCopyLiveChat() ? "Y" : "N";
        String t = s.isCopyTbMessages() ? "Y" : "N";
        update("storing kid settings for " + s.getKidPid(),
            "insert into dsg_kid_settings (kid_pid, copy_messages, copy_live_chat, copy_tb_messages) " +
            "values (?, ?, ?, ?) on duplicate key update " +
            "copy_messages = values(copy_messages), copy_live_chat = values(copy_live_chat), " +
            "copy_tb_messages = values(copy_tb_messages)",
            s.getKidPid(), m, c, t);
    }

    public void deleteKidData(long kidPid) throws DSGPlayerStoreException {
        update("deleting whitelist of " + kidPid,
            "delete from dsg_kid_whitelist where kid_pid = ?", kidPid);
        update("deleting kid settings of " + kidPid,
            "delete from dsg_kid_settings where kid_pid = ?", kidPid);
    }

    public void setParentPid(long kidPid, Long parentPid) throws DSGPlayerStoreException {
        update("setting parent_pid of " + kidPid,
            "update dsg_player set parent_pid = ? where pid = ?", parentPid, kidPid);
    }

    public List<Long> getKidPids(long parentPid) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List<Long> pids = new ArrayList<Long>();
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement("select pid from dsg_player where parent_pid = ?");
                stmt.setLong(1, parentPid);
                result = stmt.executeQuery();
                while (result.next()) {
                    pids.add(result.getLong(1));
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
        } catch (SQLException e) {
            throw new DSGPlayerStoreException("Problem getting kids of " + parentPid, e);
        }
        return pids;
    }

    public void storeLinkRequest(KidLinkRequest r) throws DSGPlayerStoreException {
        update("clearing old link requests",
            "delete from dsg_kid_link_request where kid_pid = ? " +
            "or creation_date < date_sub(sysdate(), interval 7 day)", r.getKidPid());
        update("storing link request",
            "insert into dsg_kid_link_request (code, parent_pid, kid_pid, creation_date) " +
            "values (?, ?, ?, ?)",
            r.getCode(), r.getParentPid(), r.getKidPid(),
            new Timestamp(r.getCreationDate().getTime()));
    }

    public KidLinkRequest getLinkRequest(String code) throws DSGPlayerStoreException {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                    "select parent_pid, kid_pid, creation_date from dsg_kid_link_request " +
                    "where code = ?");
                stmt.setString(1, code);
                result = stmt.executeQuery();
                if (result.next()) {
                    return new KidLinkRequest(code, result.getLong(1), result.getLong(2),
                        new java.util.Date(result.getTimestamp(3).getTime()));
                }
                return null;
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
        } catch (SQLException e) {
            throw new DSGPlayerStoreException("Problem getting link request", e);
        }
    }

    public void deleteLinkRequest(String code) throws DSGPlayerStoreException {
        update("deleting link request", "delete from dsg_kid_link_request where code = ?", code);
    }
}
