package org.pente.subscriptions.apple.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.database.DBHandler;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.MySQLAppleStore;
import org.pente.subscriptions.play.MySQLPlayStore;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * MySQLAppleStore against a real, throwaway MariaDB: the migration applies, and the SQL has the same semantics
 * as FakeAppleStore (AppleFakesTest). Also MySQLPlayStore.setLevel, added for refund reversals.
 * Needs -DappleDbUrl=jdbc:mariadb://127.0.0.1:PORT/DB?user=root and -DappleMigration=path/to/migration.sql;
 * run through "ant test-apple-billing-db". Never point it at a real database: it drops its tables.
 */
public class MySQLAppleStoreIT extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;
    private static final long PID = 23000000020001L;

    /** whole seconds: DATETIME keeps no milliseconds */
    private long now = 1760536800000L;
    private DBHandler db;
    private MySQLAppleStore store;

    public MySQLAppleStoreIT(String name) {
        super(name);
    }

    private void exec(String sql) throws SQLException {
        Connection con = db.getConnection();
        try (Statement s = con.createStatement()) {
            s.execute(sql);
        } finally {
            db.freeConnection(con);
        }
    }

    protected void setUp() throws Exception {
        final String url = System.getProperty("appleDbUrl");
        assertNotNull("set -DappleDbUrl", url);
        db = new DBHandler() {
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url);
            }

            public void freeConnection(Connection con) throws SQLException {
                con.close();
            }

            public void destroy() {
            }
        };
        exec("DROP TABLE IF EXISTS dsg_subscribers_apple");
        exec("DROP TABLE IF EXISTS dsg_apple_notifications");
        exec("DROP TABLE IF EXISTS dsg_apple_poll_state");
        exec("DROP TABLE IF EXISTS dsg_subscribers");
        // dsg_subscribers as schema.sql has it after the 2026-10-11 migration
        exec("CREATE TABLE dsg_subscribers (pid bigint(20) unsigned NOT NULL, level bigint(20) unsigned NOT NULL DEFAULT 0, "
                + "paymentdate datetime NOT NULL, transactionid varchar(64) NOT NULL, amount double NOT NULL DEFAULT 0, "
                + "verified tinyint(1) NOT NULL DEFAULT 0, PRIMARY KEY (transactionid), KEY pid (pid), "
                + "KEY paymentdate (paymentdate)) ENGINE=MyISAM DEFAULT CHARSET=latin1");
        String migration = new String(Files.readAllBytes(Paths.get(System.getProperty("appleMigration"))), StandardCharsets.UTF_8);
        StringBuilder statement = new StringBuilder();
        for (String line : migration.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            statement.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                exec(statement.toString().trim().replaceAll(";$", ""));
                statement.setLength(0);
            }
        }
        store = new MySQLAppleStore(db, new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        });
    }

    private static AppleRow row(String id, String productId, String state, Long expiryMs) {
        AppleRow r = new AppleRow();
        r.originalTransactionId = id;
        r.pid = PID;
        r.productId = productId;
        r.latestTransactionId = id;
        r.expiryTimeMs = expiryMs;
        r.state = state;
        r.environment = AppleEnvironment.SANDBOX;
        return r;
    }

    public void testUpsertRoundTripKeepsCreated() throws Exception {
        assertNull(store.find("1"));
        store.upsert(row("1", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + 100 * DAY)));
        AppleRow read = store.find("1");
        assertEquals(PID, read.pid);
        assertEquals(AppleProducts.YEARLY, read.productId);
        assertEquals(Long.valueOf(now + 100 * DAY), read.expiryTimeMs);
        assertEquals(AppleEnvironment.SANDBOX, read.environment);
        assertTrue(!read.tokenStamped);
        assertEquals(now, read.createdMs);
        now += DAY;
        read.state = AppleStates.EXPIRED;
        read.latestTransactionId = "2";
        read.expiryTimeMs = null;
        store.upsert(read);
        AppleRow again = store.find("1");
        assertEquals(AppleStates.EXPIRED, again.state);
        assertEquals("2", again.latestTransactionId);
        assertNull(again.expiryTimeMs);
        assertEquals(now - DAY, again.createdMs);
        assertEquals(now, again.updatedMs);
        store.setTokenStamped("1");
        assertTrue(store.find("1").tokenStamped);
        store.setState("1", AppleStates.REVOKED);
        assertEquals(AppleStates.REVOKED, store.find("1").state);
    }

    public void testSubscriptionsToPoll() throws Exception {
        store.upsert(row("expiring", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + DAY)));
        store.upsert(row("past", AppleProducts.YEARLY, AppleStates.BILLING_RETRY, Long.valueOf(now - 10 * DAY)));
        store.upsert(row("far", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + 100 * DAY)));
        store.upsert(row("oldExpired", AppleProducts.YEARLY, AppleStates.EXPIRED, Long.valueOf(now - 100 * DAY)));
        store.upsert(row("pass", AppleProducts.MONTH_PASS, AppleStates.CONSUMED, null));
        now += 36 * DAY;
        store.upsert(row("recentRevoked", AppleProducts.YEARLY, AppleStates.REVOKED, Long.valueOf(now - 50 * DAY)));
        List<AppleRow> polled = store.subscriptionsToPoll(now);
        StringBuilder ids = new StringBuilder();
        for (AppleRow r : polled) {
            ids.append(r.originalTransactionId).append(' ');
        }
        assertEquals("expiring past recentRevoked ", ids.toString());
    }

    public void testStaleGrantingStaleActiveAndActiveSubscription() throws Exception {
        store.upsert(row("g", AppleProducts.VACATION_10, AppleStates.GRANTING, null));
        store.upsert(row("a", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now - 3 * DAY)));
        store.upsert(row("grace", AppleProducts.YEARLY, AppleStates.GRACE, Long.valueOf(now + DAY)));
        assertEquals(0, store.staleGranting(now - 3600000L).size());
        now += 2 * 3600000L;
        assertEquals(1, store.staleGranting(now - 3600000L).size());
        List<AppleRow> stale = store.staleActive(now - 2 * DAY);
        assertEquals(1, stale.size());
        assertEquals("a", stale.get(0).originalTransactionId);
        assertTrue(store.hasActiveSubscription(PID, now));
        assertTrue(!store.hasActiveSubscription(PID + 1, now));
        store.setState("grace", AppleStates.BILLING_RETRY);
        assertTrue(!store.hasActiveSubscription(PID, now));
    }

    public void testNotificationsAndHighWaterMark() throws Exception {
        String uuid = "0c4f7f2e-8a6e-4d3c-9b1a-5e2f6a7b8c9d";
        assertTrue(!store.notificationSeen(uuid));
        store.recordNotification(uuid, "DID_RENEW", now);
        store.recordNotification(uuid, "DID_RENEW", now);
        assertTrue(store.notificationSeen(uuid));
        assertNull(store.historyUntil(AppleEnvironment.PRODUCTION));
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now);
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now + DAY);
        assertEquals(Long.valueOf(now + DAY), store.historyUntil(AppleEnvironment.PRODUCTION));
        assertNull(store.historyUntil(AppleEnvironment.SANDBOX));
    }

    public void testPlayStoreSetLevel() throws Exception {
        MySQLPlayStore subscribers = new MySQLPlayStore(db);
        assertTrue(subscribers.insert(new SubscriberRow(PID, "2000000123456789", PlayProducts.YEARLY_LEVEL, now)));
        subscribers.setLevelZero("2000000123456789");
        assertEquals(0, subscribers.findByTransactionId("2000000123456789").level);
        subscribers.setLevel("2000000123456789", PlayProducts.YEARLY_LEVEL);
        assertEquals(PlayProducts.YEARLY_LEVEL, subscribers.findByTransactionId("2000000123456789").level);
        assertTrue("a tombstone with pid 0 is a plain row", subscribers.insert(new SubscriberRow(0L, "2000000999", 0, now)));
        assertTrue(!subscribers.insert(new SubscriberRow(PID, "2000000999", PlayProducts.MONTH_LEVEL, now)));
    }
}
