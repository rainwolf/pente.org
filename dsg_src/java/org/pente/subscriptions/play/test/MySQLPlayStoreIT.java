package org.pente.subscriptions.play.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import junit.framework.TestCase;

import org.pente.database.DBHandler;
import org.pente.subscriptions.play.AndroidPurchaseRow;
import org.pente.subscriptions.play.MySQLPlayStore;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * MySQLPlayStore against a real, throwaway MariaDB: the migration applies on top of the legacy
 * dsg_subscribers table, and the SQL has the same semantics as the fakes (PlayFakesTest).
 * Needs -DplayDbUrl=jdbc:mariadb://127.0.0.1:PORT/DB?user=root and -DplayMigration=path/to/migration.sql;
 * run through "ant test-play-billing-db". Never point it at a real database: it drops both tables.
 */
public class MySQLPlayStoreIT extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;

    private DBHandler db;
    private MySQLPlayStore store;

    public MySQLPlayStoreIT(String name) {
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
        final String url = System.getProperty("playDbUrl");
        assertNotNull("set -DplayDbUrl", url);
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
        exec("DROP TABLE IF EXISTS dsg_subscribers");
        exec("DROP TABLE IF EXISTS dsg_subscribers_android");
        // dsg_subscribers as it is before the migration (schema.sql before 2026-10-11)
        exec("CREATE TABLE dsg_subscribers (pid bigint(20) unsigned NOT NULL, level bigint(20) unsigned NOT NULL DEFAULT 0, "
                + "paymentdate datetime NOT NULL, transactionid char(19) NOT NULL, amount double NOT NULL DEFAULT 0, "
                + "verified tinyint(1) NOT NULL DEFAULT 0, PRIMARY KEY (transactionid), KEY pid (pid), "
                + "KEY paymentdate (paymentdate)) ENGINE=MyISAM DEFAULT CHARSET=latin1");
        String migration = new String(Files.readAllBytes(Paths.get(System.getProperty("playMigration"))), StandardCharsets.UTF_8);
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
        store = new MySQLPlayStore(db);
    }

    protected void tearDown() throws Exception {
        exec("DROP TABLE IF EXISTS dsg_subscribers");
        exec("DROP TABLE IF EXISTS dsg_subscribers_android");
    }

    private static AndroidPurchaseRow sub(String hash, long pid, String state, Long expiry, boolean acked) {
        AndroidPurchaseRow r = new AndroidPurchaseRow();
        r.tokenHash = hash;
        r.purchaseToken = "token-" + hash;
        r.pid = pid;
        r.productId = PlayProducts.YEARLY;
        r.latestSuccessfulOrderId = "GPA." + hash;
        r.expiryTimeMs = expiry;
        r.state = state;
        r.acknowledged = acked;
        return r;
    }

    /** DATETIME keeps whole seconds */
    private static long sec(long ms) {
        return ms / 1000 * 1000;
    }

    public void testUpsertKeepsPidCreatedOrderAndAck() throws Exception {
        long expiry = sec(System.currentTimeMillis() + DAY);
        store.upsertSubscription(sub("h1", 1L, PlayStates.ACTIVE, Long.valueOf(expiry), true));
        long created = store.find("h1").createdMs;
        AndroidPurchaseRow update = sub("h1", 2L, PlayStates.CANCELED, null, false);
        update.latestSuccessfulOrderId = null;
        store.upsertSubscription(update);
        AndroidPurchaseRow r = store.find("h1");
        assertEquals(1L, r.pid);
        assertEquals(created, r.createdMs);
        assertEquals("GPA.h1", r.latestSuccessfulOrderId);
        assertEquals(Long.valueOf(expiry), r.expiryTimeMs);
        assertEquals(PlayStates.CANCELED, r.state);
        assertTrue(r.acknowledged);
        assertNull(store.find("missing"));
    }

    public void testLongTokenAndStateFit() throws Exception {
        AndroidPurchaseRow r = sub("h2", 1L, "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", null, false);
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            token.append("abcdefghij");
        }
        r.purchaseToken = token.toString();
        store.upsertSubscription(r);
        assertEquals(400, store.find("h2").purchaseToken.length());
        assertEquals("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", store.find("h2").state);
    }

    public void testInsertProductIfAbsentStateAndAck() throws Exception {
        AndroidPurchaseRow r = sub("p1", 1L, PlayStates.PURCHASED, null, false);
        r.productId = PlayProducts.MONTH_PASS;
        store.insertProductIfAbsent(r);
        store.setState("p1", PlayStates.GRANTED);
        store.insertProductIfAbsent(r);
        assertEquals(PlayStates.GRANTED, store.find("p1").state);
        store.setAcknowledged("p1");
        assertTrue(store.find("p1").acknowledged);
        assertEquals(1, store.findByOrderId("GPA.p1").size());
        assertEquals(0, store.findByOrderId("GPA.none").size());
    }

    public void testSubscriberRowsAfterMigration() throws Exception {
        String longKey = "GP." + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abc";
        assertEquals(64, longKey.length());
        long t = sec(System.currentTimeMillis());
        assertTrue(store.insert(new SubscriberRow(1L, longKey, PlayProducts.MONTH_LEVEL, t)));
        assertTrue(!store.insert(new SubscriberRow(2L, longKey, 0, t)));
        SubscriberRow r = store.findByTransactionId(longKey);
        assertEquals(1L, r.pid);
        assertEquals(PlayProducts.MONTH_LEVEL, r.level);
        assertEquals(t, r.paymentDateMs);
        store.updatePaymentDate(longKey, t + DAY);
        assertEquals(t + DAY, store.findByTransactionId(longKey).paymentDateMs);
        store.setLevelZero(longKey);
        assertEquals(0, store.findByTransactionId(longKey).level);
        assertNull(store.findByTransactionId("nope"));
    }

    public void testFindByPidReturnsEveryRowOfThatPidOnly() throws Exception {
        long t = sec(System.currentTimeMillis());
        store.insert(new SubscriberRow(1L, "A", PlayProducts.MONTH_LEVEL, t));
        store.insert(new SubscriberRow(1L, "Y", PlayProducts.YEARLY_LEVEL, t + 40 * DAY));
        store.insert(new SubscriberRow(1L, "Z", 0, t - DAY));
        store.insert(new SubscriberRow(2L, "C", PlayProducts.MONTH_LEVEL, t + 31 * DAY));
        List<SubscriberRow> rows = store.findByPid(1L);
        assertEquals(3, rows.size());
        int seen = 0;
        for (SubscriberRow r : rows) {
            assertEquals(1L, r.pid);
            if ("A".equals(r.transactionId)) {
                assertEquals(PlayProducts.MONTH_LEVEL, r.level);
                assertEquals(t, r.paymentDateMs);
                seen |= 1;
            } else if ("Y".equals(r.transactionId)) {
                assertEquals(PlayProducts.YEARLY_LEVEL, r.level);
                assertEquals(t + 40 * DAY, r.paymentDateMs);
                seen |= 2;
            } else if ("Z".equals(r.transactionId)) {
                assertEquals(0, r.level);
                assertEquals(t - DAY, r.paymentDateMs);
                seen |= 4;
            } else {
                fail("row of another pid: " + r.transactionId);
            }
        }
        assertEquals(7, seen);
        rows = store.findByPid(2L);
        assertEquals(1, rows.size());
        assertEquals("C", rows.get(0).transactionId);
        assertEquals(0, store.findByPid(3L).size());
    }

    public void testPollQueries() throws Exception {
        long now = System.currentTimeMillis();
        store.upsertSubscription(sub("live", 1L, PlayStates.ACTIVE, Long.valueOf(now + DAY), true));
        store.upsertSubscription(sub("far", 1L, PlayStates.ACTIVE, Long.valueOf(now + 300 * DAY), true));
        store.upsertSubscription(sub("old", 1L, PlayStates.ON_HOLD, Long.valueOf(now - 61 * DAY), true));
        store.upsertSubscription(sub("expired", 1L, PlayStates.EXPIRED, Long.valueOf(now - DAY), true));
        store.upsertSubscription(sub("superseded", 1L, PlayStates.SUPERSEDED, Long.valueOf(now - DAY), true));
        store.upsertSubscription(sub("unacked", 1L, PlayStates.ACTIVE, Long.valueOf(now + 300 * DAY), false));
        store.upsertSubscription(sub("unackedPending", 1L, PlayStates.PENDING, Long.valueOf(now + 300 * DAY), false));
        List<AndroidPurchaseRow> polled = store.subscriptionsToPoll(now);
        assertEquals(2, polled.size());
        // an unacknowledged grantable row is polled however old it is
        polled = store.subscriptionsToPoll(now + 10 * DAY);
        assertEquals(2, polled.size());
        boolean unacked = "unacked".equals(polled.get(0).tokenHash) || "unacked".equals(polled.get(1).tokenHash);
        assertTrue(unacked);

        AndroidPurchaseRow purchased = sub("p1", 1L, PlayStates.PURCHASED, null, false);
        purchased.productId = PlayProducts.MONTH_PASS;
        store.insertProductIfAbsent(purchased);
        AndroidPurchaseRow granting = sub("p2", 1L, PlayStates.GRANTING, null, false);
        granting.productId = PlayProducts.VACATION_10;
        store.insertProductIfAbsent(granting);
        assertEquals(1, store.productsToPoll(now).size());
        assertEquals(0, store.staleGranting(now - 3600L * 1000).size());
        assertEquals(1, store.staleGranting(now + 3600L * 1000).size());

        assertTrue(store.hasAutoRenewingSubscription(1L));
        assertTrue(!store.hasAutoRenewingSubscription(2L));
    }
}
