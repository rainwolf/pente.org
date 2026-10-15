package org.pente.subscriptions.apple;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.pente.database.DBHandler;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStoreException;

/**
 * JDBC storage for the App Store tables (migration dsg_src/sql/2026-10-15-app-store-server.sql). Every timestamp
 * comes from the JVM clock, never from SQL NOW(), so it compares consistently with getSubscriberData.
 * dsg_subscribers itself is MySQLPlayStore's SubscriberStore.
 */
public class MySQLAppleStore implements AppleStore {

    private static final long DAY = PlayProducts.DAY_MS;

    private static final String COLUMNS = "original_transaction_id, pid, product_id, latest_transaction_id, "
            + "expiry_time, state, environment, token_stamped, created, updated";

    private final DBHandler dbHandler;
    private final LongSupplier clock;

    public MySQLAppleStore(DBHandler dbHandler, LongSupplier clock) {
        this.dbHandler = dbHandler;
        this.clock = clock;
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

    private static List<AppleRow> readRows(PreparedStatement stmt) throws SQLException {
        List<AppleRow> rows = new ArrayList<AppleRow>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                AppleRow r = new AppleRow();
                r.originalTransactionId = rs.getString("original_transaction_id");
                r.pid = rs.getLong("pid");
                r.productId = rs.getString("product_id");
                r.latestTransactionId = rs.getString("latest_transaction_id");
                Timestamp expiry = rs.getTimestamp("expiry_time");
                r.expiryTimeMs = expiry == null ? null : Long.valueOf(expiry.getTime());
                r.state = rs.getString("state");
                r.environment = AppleEnvironment.of(rs.getString("environment"));
                r.tokenStamped = rs.getInt("token_stamped") != 0;
                r.createdMs = rs.getTimestamp("created").getTime();
                r.updatedMs = rs.getTimestamp("updated").getTime();
                rows.add(r);
            }
        }
        return rows;
    }

    public AppleRow find(final String originalTransactionId) throws PlayStoreException {
        return withConnection("find dsg_subscribers_apple row", new Work<AppleRow>() {
            public AppleRow run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_apple WHERE original_transaction_id = ?")) {
                    stmt.setString(1, originalTransactionId);
                    List<AppleRow> rows = readRows(stmt);
                    return rows.isEmpty() ? null : rows.get(0);
                }
            }
        });
    }

    public void upsert(final AppleRow row) throws PlayStoreException {
        withConnection("upsert dsg_subscribers_apple row", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO dsg_subscribers_apple (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE pid = VALUES(pid), product_id = VALUES(product_id), "
                                + "latest_transaction_id = VALUES(latest_transaction_id), "
                                + "expiry_time = VALUES(expiry_time), state = VALUES(state), "
                                + "environment = VALUES(environment), token_stamped = VALUES(token_stamped), "
                                + "updated = VALUES(updated)")) {
                    Timestamp now = ts(clock.getAsLong());
                    stmt.setString(1, row.originalTransactionId);
                    stmt.setLong(2, row.pid);
                    stmt.setString(3, row.productId);
                    stmt.setString(4, row.latestTransactionId);
                    if (row.expiryTimeMs == null) {
                        stmt.setNull(5, Types.TIMESTAMP);
                    } else {
                        stmt.setTimestamp(5, ts(row.expiryTimeMs.longValue()));
                    }
                    stmt.setString(6, row.state);
                    stmt.setString(7, row.environment.name());
                    stmt.setInt(8, row.tokenStamped ? 1 : 0);
                    stmt.setTimestamp(9, now);
                    stmt.setTimestamp(10, now);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void setState(final String originalTransactionId, final String state) throws PlayStoreException {
        withConnection("set dsg_subscribers_apple state", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers_apple SET state = ?, updated = ? WHERE original_transaction_id = ?")) {
                    stmt.setString(1, state);
                    stmt.setTimestamp(2, ts(clock.getAsLong()));
                    stmt.setString(3, originalTransactionId);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public void setTokenStamped(final String originalTransactionId) throws PlayStoreException {
        withConnection("set dsg_subscribers_apple token_stamped", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE dsg_subscribers_apple SET token_stamped = 1 WHERE original_transaction_id = ?")) {
                    stmt.setString(1, originalTransactionId);
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public boolean hasActiveSubscription(final long pid, final long nowMs) throws PlayStoreException {
        return withConnection("find active apple subscription", new Work<Boolean>() {
            public Boolean run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT 1 FROM dsg_subscribers_apple WHERE pid = ? AND product_id = ? "
                                + "AND state IN (?, ?) AND expiry_time > ? LIMIT 1")) {
                    stmt.setLong(1, pid);
                    stmt.setString(2, AppleProducts.YEARLY);
                    stmt.setString(3, AppleStates.ACTIVE);
                    stmt.setString(4, AppleStates.GRACE);
                    stmt.setTimestamp(5, ts(nowMs));
                    try (ResultSet rs = stmt.executeQuery()) {
                        return Boolean.valueOf(rs.next());
                    }
                }
            }
        }).booleanValue();
    }

    public List<AppleRow> subscriptionsToPoll(final long nowMs) throws PlayStoreException {
        return withConnection("list apple subscriptions to poll", new Work<List<AppleRow>>() {
            public List<AppleRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_apple WHERE product_id = ? AND ("
                                + "(state IN (?, ?, ?) AND expiry_time < ?) OR updated >= ?) "
                                + "ORDER BY created, original_transaction_id")) {
                    stmt.setString(1, AppleProducts.YEARLY);
                    stmt.setString(2, AppleStates.ACTIVE);
                    stmt.setString(3, AppleStates.GRACE);
                    stmt.setString(4, AppleStates.BILLING_RETRY);
                    stmt.setTimestamp(5, ts(nowMs + 2 * DAY));
                    stmt.setTimestamp(6, ts(nowMs - 35 * DAY));
                    return readRows(stmt);
                }
            }
        });
    }

    public List<AppleRow> staleGranting(final long olderThanMs) throws PlayStoreException {
        return withConnection("list stale GRANTING apple rows", new Work<List<AppleRow>>() {
            public List<AppleRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_apple WHERE state = ? AND updated < ?")) {
                    stmt.setString(1, AppleStates.GRANTING);
                    stmt.setTimestamp(2, ts(olderThanMs));
                    return readRows(stmt);
                }
            }
        });
    }

    public List<AppleRow> staleActive(final long expiredBeforeMs) throws PlayStoreException {
        return withConnection("list stale ACTIVE apple rows", new Work<List<AppleRow>>() {
            public List<AppleRow> run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT " + COLUMNS + " FROM dsg_subscribers_apple WHERE product_id = ? AND state = ? "
                                + "AND expiry_time < ?")) {
                    stmt.setString(1, AppleProducts.YEARLY);
                    stmt.setString(2, AppleStates.ACTIVE);
                    stmt.setTimestamp(3, ts(expiredBeforeMs));
                    return readRows(stmt);
                }
            }
        });
    }

    public boolean notificationSeen(final String notificationUuid) throws PlayStoreException {
        return withConnection("find apple notification", new Work<Boolean>() {
            public Boolean run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT 1 FROM dsg_apple_notifications WHERE notification_uuid = ?")) {
                    stmt.setString(1, notificationUuid);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return Boolean.valueOf(rs.next());
                    }
                }
            }
        }).booleanValue();
    }

    public void recordNotification(final String notificationUuid, final String notificationType, final long receivedMs)
            throws PlayStoreException {
        withConnection("record apple notification", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT IGNORE INTO dsg_apple_notifications (notification_uuid, notification_type, received) "
                                + "VALUES (?, ?, ?)")) {
                    stmt.setString(1, notificationUuid);
                    stmt.setString(2, notificationType.length() > 48 ? notificationType.substring(0, 48) : notificationType);
                    stmt.setTimestamp(3, ts(receivedMs));
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }

    public Long historyUntil(final AppleEnvironment env) throws PlayStoreException {
        return withConnection("read apple history high-water mark", new Work<Long>() {
            public Long run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "SELECT history_until FROM dsg_apple_poll_state WHERE environment = ?")) {
                    stmt.setString(1, env.name());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? Long.valueOf(rs.getTimestamp("history_until").getTime()) : null;
                    }
                }
            }
        });
    }

    public void setHistoryUntil(final AppleEnvironment env, final long untilMs) throws PlayStoreException {
        withConnection("write apple history high-water mark", new Work<Void>() {
            public Void run(Connection con) throws SQLException {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO dsg_apple_poll_state (environment, history_until) VALUES (?, ?) "
                                + "ON DUPLICATE KEY UPDATE history_until = VALUES(history_until)")) {
                    stmt.setString(1, env.name());
                    stmt.setTimestamp(2, ts(untilMs));
                    stmt.executeUpdate();
                }
                return null;
            }
        });
    }
}
