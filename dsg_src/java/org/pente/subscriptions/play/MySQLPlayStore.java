package org.pente.subscriptions.play;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.pente.database.DBHandler;
import org.pente.gameServer.core.MySQLDSGPlayerStorer;

/**
 * JDBC storage for Google Play purchases: dsg_subscribers_android and the dsg_subscribers rows they
 * grant (migration dsg_src/sql/2026-10-11-google-play-billing.sql). Every timestamp comes from the
 * JVM clock, never from SQL NOW(), so it compares consistently with getSubscriberData.
 */
public class MySQLPlayStore implements AndroidPurchaseStore, SubscriberStore {

    /** MySQL/MariaDB ER_DUP_ENTRY */
    private static final int ER_DUP_ENTRY = 1062;
    private static final long DAY = PlayProducts.DAY_MS;

    private static final String COLUMNS = "purchase_token_hash, purchase_token, pid, product_id, "
            + "latest_successful_order_id, linked_purchase_token_hash, expiry_time, state, acknowledged, created, updated";

    private final DBHandler dbHandler;

    public MySQLPlayStore(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    private interface Work<T> {
        T run(Connection con) throws SQLException;
    }

    private <T> T withConnection(String what, Work<T> work) throws PlayStoreException {
        Connection con = null;
        try {
            con = dbHandler.getConnection();
            return work.run(con);
        } catch (SQLException e) {
            throw new PlayStoreException(what + " failed", e);
        } finally {
            if (con != null) {
                try {
                    dbHandler.freeConnection(con);
                } catch (SQLException e) {
                    // connection already unusable; nothing else to do
                }
            }
        }
    }

    private static Timestamp ts(long ms) {
        return new Timestamp(ms);
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }

    private static AndroidPurchaseRow readRow(ResultSet rs) throws SQLException {
        AndroidPurchaseRow r = new AndroidPurchaseRow();
        r.tokenHash = rs.getString("purchase_token_hash");
        r.purchaseToken = rs.getString("purchase_token");
        r.pid = rs.getLong("pid");
        r.productId = rs.getString("product_id");
        r.latestSuccessfulOrderId = rs.getString("latest_successful_order_id");
        r.linkedPurchaseTokenHash = rs.getString("linked_purchase_token_hash");
        Timestamp expiry = rs.getTimestamp("expiry_time");
        r.expiryTimeMs = expiry == null ? null : Long.valueOf(expiry.getTime());
        r.state = rs.getString("state");
        r.acknowledged = rs.getInt("acknowledged") != 0;
        r.createdMs = rs.getTimestamp("created").getTime();
        r.updatedMs = rs.getTimestamp("updated").getTime();
        return r;
    }

    private static List<AndroidPurchaseRow> readRows(PreparedStatement stmt) throws SQLException {
        List<AndroidPurchaseRow> rows = new ArrayList<AndroidPurchaseRow>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                rows.add(readRow(rs));
            }
        }
        return rows;
    }

    private static void setNullableTimestamp(PreparedStatement stmt, int index, Long ms) throws SQLException {
        if (ms == null) {
            stmt.setNull(index, Types.TIMESTAMP);
        } else {
            stmt.setTimestamp(index, ts(ms.longValue()));
        }
    }

    // ---------------------------------------------------------------- dsg_subscribers_android

    public AndroidPurchaseRow find(final String tokenHash) throws PlayStoreException {
        return withConnection("find android purchase", new Work<AndroidPurchaseRow>() {
            public AndroidPurchaseRow run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_android WHERE purchase_token_hash = ?")) {
                    stmt.setString(1, tokenHash);
                    List<AndroidPurchaseRow> rows = readRows(stmt);
                    return rows.isEmpty() ? null : rows.get(0);
                }
            }
        });
    }

    public void upsertSubscription(final AndroidPurchaseRow row) throws PlayStoreException {
        withConnection("upsert android subscription", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO dsg_subscribers_android (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE purchase_token = VALUES(purchase_token), "
                                + "product_id = VALUES(product_id), "
                                + "latest_successful_order_id = COALESCE(VALUES(latest_successful_order_id), latest_successful_order_id), "
                                + "linked_purchase_token_hash = VALUES(linked_purchase_token_hash), "
                                + "expiry_time = COALESCE(VALUES(expiry_time), expiry_time), "
                                + "state = VALUES(state), "
                                + "acknowledged = GREATEST(acknowledged, VALUES(acknowledged)), "
                                + "updated = VALUES(updated)")) {
                    Timestamp now = now();
                    stmt.setString(1, row.tokenHash);
                    stmt.setString(2, row.purchaseToken);
                    stmt.setLong(3, row.pid);
                    stmt.setString(4, row.productId);
                    stmt.setString(5, row.latestSuccessfulOrderId);
                    stmt.setString(6, row.linkedPurchaseTokenHash);
                    setNullableTimestamp(stmt, 7, row.expiryTimeMs);
                    stmt.setString(8, row.state);
                    stmt.setInt(9, row.acknowledged ? 1 : 0);
                    stmt.setTimestamp(10, now);
                    stmt.setTimestamp(11, now);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void insertProductIfAbsent(final AndroidPurchaseRow row) throws PlayStoreException {
        withConnection("insert android product", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO dsg_subscribers_android (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE purchase_token_hash = purchase_token_hash")) {
                    Timestamp now = now();
                    stmt.setString(1, row.tokenHash);
                    stmt.setString(2, row.purchaseToken);
                    stmt.setLong(3, row.pid);
                    stmt.setString(4, row.productId);
                    stmt.setString(5, row.latestSuccessfulOrderId);
                    stmt.setString(6, row.linkedPurchaseTokenHash);
                    setNullableTimestamp(stmt, 7, row.expiryTimeMs);
                    stmt.setString(8, row.state);
                    stmt.setInt(9, row.acknowledged ? 1 : 0);
                    stmt.setTimestamp(10, now);
                    stmt.setTimestamp(11, now);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void setState(final String tokenHash, final String state) throws PlayStoreException {
        withConnection("set android purchase state", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers_android SET state = ?, updated = ? WHERE purchase_token_hash = ?")) {
                    stmt.setString(1, state);
                    stmt.setTimestamp(2, now());
                    stmt.setString(3, tokenHash);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void setAcknowledged(final String tokenHash) throws PlayStoreException {
        withConnection("set android purchase acknowledged", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers_android SET acknowledged = 1, updated = ? WHERE purchase_token_hash = ?")) {
                    stmt.setTimestamp(1, now());
                    stmt.setString(2, tokenHash);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public List<AndroidPurchaseRow> findByOrderId(final String orderId) throws PlayStoreException {
        return withConnection("find android purchases by order", new Work<List<AndroidPurchaseRow>>() {
            public List<AndroidPurchaseRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_android WHERE latest_successful_order_id = ?")) {
                    stmt.setString(1, orderId);
                    return readRows(stmt);
                }
            }
        });
    }

    public boolean hasAutoRenewingSubscription(final long pid) throws PlayStoreException {
        return withConnection("check auto-renewing subscription", new Work<Boolean>() {
            public Boolean run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT 1 FROM dsg_subscribers_android WHERE pid = ? AND product_id = ? AND state IN (?, ?) LIMIT 1")) {
                    stmt.setLong(1, pid);
                    stmt.setString(2, PlayProducts.YEARLY);
                    stmt.setString(3, PlayStates.ACTIVE);
                    stmt.setString(4, PlayStates.IN_GRACE_PERIOD);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return Boolean.valueOf(rs.next());
                    }
                }
            }
        }).booleanValue();
    }

    public List<AndroidPurchaseRow> subscriptionsToPoll(final long nowMs) throws PlayStoreException {
        return withConnection("list subscriptions to poll", new Work<List<AndroidPurchaseRow>>() {
            public List<AndroidPurchaseRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_android WHERE product_id = ? AND ("
                                + "(state NOT IN (?, ?, ?) AND expiry_time BETWEEN ? AND ?) "
                                + "OR (acknowledged = 0 AND created > ?))")) {
                    stmt.setString(1, PlayProducts.YEARLY);
                    stmt.setString(2, PlayStates.EXPIRED);
                    stmt.setString(3, PlayStates.REVOKED);
                    stmt.setString(4, PlayStates.SUPERSEDED);
                    stmt.setTimestamp(5, ts(nowMs - 60 * DAY));
                    stmt.setTimestamp(6, ts(nowMs + 2 * DAY));
                    stmt.setTimestamp(7, ts(nowMs - 3 * DAY));
                    return readRows(stmt);
                }
            }
        });
    }

    public List<AndroidPurchaseRow> productsToPoll(final long nowMs) throws PlayStoreException {
        return withConnection("list products to poll", new Work<List<AndroidPurchaseRow>>() {
            public List<AndroidPurchaseRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_android "
                                + "WHERE product_id <> ? AND state IN (?, ?) AND created > ?")) {
                    stmt.setString(1, PlayProducts.YEARLY);
                    stmt.setString(2, PlayStates.PURCHASED);
                    stmt.setString(3, PlayStates.GRANTED);
                    stmt.setTimestamp(4, ts(nowMs - 3 * DAY));
                    return readRows(stmt);
                }
            }
        });
    }

    public List<AndroidPurchaseRow> staleGranting(final long olderThanMs) throws PlayStoreException {
        return withConnection("list stale GRANTING purchases", new Work<List<AndroidPurchaseRow>>() {
            public List<AndroidPurchaseRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_android WHERE state = ? AND updated < ?")) {
                    stmt.setString(1, PlayStates.GRANTING);
                    stmt.setTimestamp(2, ts(olderThanMs));
                    return readRows(stmt);
                }
            }
        });
    }

    // ---------------------------------------------------------------- dsg_subscribers

    public boolean insert(final SubscriberRow row) throws PlayStoreException {
        return withConnection("insert dsg_subscribers row", new Work<Boolean>() {
            public Boolean run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount, verified) "
                                + "VALUES (?, ?, ?, ?, 0, 1)")) {
                    stmt.setLong(1, row.pid);
                    stmt.setInt(2, row.level);
                    stmt.setTimestamp(3, ts(row.paymentDateMs));
                    stmt.setString(4, row.transactionId);
                    stmt.executeUpdate();
                    return Boolean.TRUE;
                } catch (SQLException e) {
                    if (e instanceof SQLIntegrityConstraintViolationException || e.getErrorCode() == ER_DUP_ENTRY) {
                        return Boolean.FALSE;
                    }
                    throw e;
                }
            }
        }).booleanValue();
    }

    public SubscriberRow findByTransactionId(String transactionId) throws PlayStoreException {
        final String id = transactionId;
        return withConnection("find dsg_subscribers row", new Work<SubscriberRow>() {
            public SubscriberRow run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT pid, level, paymentdate, transactionid FROM dsg_subscribers WHERE transactionid = ?")) {
                    stmt.setString(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        return new SubscriberRow(rs.getLong("pid"), rs.getString("transactionid"),
                                rs.getInt("level"), rs.getTimestamp("paymentdate").getTime());
                    }
                }
            }
        });
    }

    public List<SubscriberRow> findByPid(final long pid) throws PlayStoreException {
        return withConnection("find dsg_subscribers rows by pid", new Work<List<SubscriberRow>>() {
            public List<SubscriberRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT pid, level, paymentdate, transactionid FROM dsg_subscribers WHERE pid = ?")) {
                    stmt.setLong(1, pid);
                    List<SubscriberRow> rows = new ArrayList<SubscriberRow>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new SubscriberRow(rs.getLong("pid"), rs.getString("transactionid"),
                                    rs.getInt("level"), rs.getTimestamp("paymentdate").getTime()));
                        }
                    }
                    return rows;
                }
            }
        });
    }

    public void updatePaymentDate(final String transactionId, final long paymentDateMs) throws PlayStoreException {
        withConnection("update dsg_subscribers paymentdate", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers SET paymentdate = ? WHERE transactionid = ?")) {
                    stmt.setTimestamp(1, ts(paymentDateMs));
                    stmt.setString(2, transactionId);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void setLevelZero(final String transactionId) throws PlayStoreException {
        withConnection("zero dsg_subscribers level", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers SET level = 0 WHERE transactionid = ?")) {
                    stmt.setString(1, transactionId);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void shiftMonthRowsBack(final long pid, final long afterMs, final String exceptTransactionId, final int days)
            throws PlayStoreException {
        withConnection("shift ONEMONTH rows", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers SET paymentdate = DATE_SUB(paymentdate, INTERVAL " + days + " DAY) "
                                + "WHERE pid = ? AND (level & " + MySQLDSGPlayerStorer.ONEMONTH + ") <> 0 "
                                + "AND paymentdate > ? AND transactionid <> ?")) {
                    stmt.setLong(1, pid);
                    stmt.setTimestamp(2, ts(afterMs));
                    stmt.setString(3, exceptTransactionId);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }
}
