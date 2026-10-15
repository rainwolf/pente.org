package org.pente.subscriptions.apple.test;

import java.util.List;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.apple.AppleVerificationException;
import org.pente.subscriptions.apple.UnconfiguredAppleApi;
import org.pente.subscriptions.play.PlayProducts;

/**
 * The fakes behave like the real seams: the store's sweep and alert queries (the same cases MySQLAppleStoreIT
 * runs against MariaDB), copies on every read, unknown signed data refused, and the unconfigured API retryable.
 *
 * Run: ant test-apple-billing
 */
public class AppleFakesTest extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;
    private long now = 1760536800000L;
    private final LongSupplier clock = new LongSupplier() {
        public long getAsLong() {
            return now;
        }
    };

    public AppleFakesTest(String name) {
        super(name);
    }

    static AppleRow row(String id, String productId, String state, Long expiryMs) {
        AppleRow r = new AppleRow();
        r.originalTransactionId = id;
        r.pid = 23000000020001L;
        r.productId = productId;
        r.latestTransactionId = id;
        r.expiryTimeMs = expiryMs;
        r.state = state;
        r.environment = AppleEnvironment.PRODUCTION;
        return r;
    }

    public void testUpsertKeepsCreatedAndCopies() throws Exception {
        FakeAppleStore store = new FakeAppleStore(clock);
        store.upsert(row("1", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + 100 * DAY)));
        long created = store.find("1").createdMs;
        now += DAY;
        AppleRow changed = store.find("1");
        changed.state = AppleStates.EXPIRED;
        store.upsert(changed);
        assertEquals(created, store.find("1").createdMs);
        assertEquals(now, store.find("1").updatedMs);
        store.find("1").state = "MUTATED";
        assertEquals(AppleStates.EXPIRED, store.find("1").state);
    }

    public void testSubscriptionsToPoll() throws Exception {
        FakeAppleStore store = new FakeAppleStore(clock);
        store.upsert(row("expiring", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + DAY)));
        store.upsert(row("past", AppleProducts.YEARLY, AppleStates.BILLING_RETRY, Long.valueOf(now - 10 * DAY)));
        store.upsert(row("far", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now + 100 * DAY)));
        store.upsert(row("oldExpired", AppleProducts.YEARLY, AppleStates.EXPIRED, Long.valueOf(now - 100 * DAY)));
        store.upsert(row("pass", AppleProducts.MONTH_PASS, AppleStates.CONSUMED, null));
        now += 36 * DAY;
        store.upsert(row("recentRevoked", AppleProducts.YEARLY, AppleStates.REVOKED, Long.valueOf(now - 50 * DAY)));
        // "far" was last changed 36 days ago and expires in 64 days: not polled
        List<AppleRow> polled = store.subscriptionsToPoll(now);
        StringBuilder ids = new StringBuilder();
        for (AppleRow r : polled) {
            ids.append(r.originalTransactionId).append(' ');
        }
        assertEquals("expiring past recentRevoked ", ids.toString());
    }

    public void testStaleGrantingAndStaleActive() throws Exception {
        FakeAppleStore store = new FakeAppleStore(clock);
        store.upsert(row("g", AppleProducts.VACATION_10, AppleStates.GRANTING, null));
        store.upsert(row("a", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now - 3 * DAY)));
        store.upsert(row("fresh", AppleProducts.YEARLY, AppleStates.ACTIVE, Long.valueOf(now - DAY)));
        assertEquals(0, store.staleGranting(now - 3600000L).size());
        now += 2 * 3600000L;
        assertEquals(1, store.staleGranting(now - 3600000L).size());
        List<AppleRow> stale = store.staleActive(now - 2 * DAY);
        assertEquals(1, stale.size());
        assertEquals("a", stale.get(0).originalTransactionId);
    }

    public void testHasActiveSubscription() throws Exception {
        FakeAppleStore store = new FakeAppleStore(clock);
        assertTrue(!store.hasActiveSubscription(23000000020001L, now));
        store.upsert(row("g", AppleProducts.YEARLY, AppleStates.GRACE, Long.valueOf(now + DAY)));
        assertTrue(store.hasActiveSubscription(23000000020001L, now));
        store.setState("g", AppleStates.BILLING_RETRY);
        assertTrue(!store.hasActiveSubscription(23000000020001L, now));
    }

    public void testNotificationsAndHighWaterMark() throws Exception {
        FakeAppleStore store = new FakeAppleStore(clock);
        assertTrue(!store.notificationSeen("u1"));
        store.recordNotification("u1", "TEST", now);
        store.recordNotification("u1", "TEST", now);
        assertTrue(store.notificationSeen("u1"));
        assertEquals(1, store.notifications.size());
        assertNull(store.historyUntil(AppleEnvironment.SANDBOX));
        store.setHistoryUntil(AppleEnvironment.SANDBOX, now);
        assertEquals(Long.valueOf(now), store.historyUntil(AppleEnvironment.SANDBOX));
        assertNull(store.historyUntil(AppleEnvironment.PRODUCTION));
    }

    public void testApiAnswersAreCopiesAndMissingIs404() throws Exception {
        FakeAppleApi api = new FakeAppleApi();
        AppleTransaction t = new AppleTransaction();
        t.transactionId = "1";
        t.productId = AppleProducts.MONTH_PASS;
        api.transactions.put("1", t);
        api.transactionInfo(AppleEnvironment.PRODUCTION, "1").productId = "x";
        assertEquals(AppleProducts.MONTH_PASS, api.transactionInfo(AppleEnvironment.PRODUCTION, "1").productId);
        try {
            api.transactionInfo(AppleEnvironment.PRODUCTION, "2");
            fail("missing transaction answered");
        } catch (AppleApiException expected) {
            assertTrue(!expected.isRetryable());
        }
        api.failTransactionInfo = 1;
        try {
            api.transactionInfo(AppleEnvironment.PRODUCTION, "1");
            fail("failure not thrown");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
    }

    public void testVerifierRefusesUnknownData() {
        FakeAppleVerifier verifier = new FakeAppleVerifier();
        try {
            verifier.verifyTransaction("forged");
            fail("forged data verified");
        } catch (AppleVerificationException expected) {
            assertTrue(!expected.isRetryable());
        }
    }

    public void testUnconfiguredApiIsRetryable() {
        UnconfiguredAppleApi api = new UnconfiguredAppleApi("no key");
        try {
            api.transactionInfo(AppleEnvironment.PRODUCTION, "1");
            fail("unconfigured API answered");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
            assertTrue(expected.getMessage().contains("no key"));
        }
    }
}
