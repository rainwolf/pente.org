package org.pente.subscriptions.play.test;

import java.util.List;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.play.AndroidPurchaseRow;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * The store contract every later test relies on (and MySQLPlayStore's SQL implements):
 * upsert keeps pid/created, never loses an order id or an acknowledgement, duplicate inserts are
 * refused, and the poller windows of spec §4.7.
 *
 * Run: ant test-play-billing
 */
public class PlayFakesTest extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;
    private static final long T0 = 1_790_000_000_000L;

    private long now;
    private FakeAndroidPurchaseStore purchases;
    private FakeSubscriberStore subscribers;

    public PlayFakesTest(String name) {
        super(name);
    }

    protected void setUp() {
        now = T0;
        purchases = new FakeAndroidPurchaseStore(new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        });
        subscribers = new FakeSubscriberStore();
    }

    private AndroidPurchaseRow sub(String hash, long pid, String state, Long expiry, boolean acked) {
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

    public void testUpsertKeepsPidCreatedOrderAndAck() {
        purchases.upsertSubscription(sub("h1", 1L, PlayStates.ACTIVE, Long.valueOf(T0 + DAY), true));
        now = T0 + 1000;
        AndroidPurchaseRow update = sub("h1", 2L, PlayStates.CANCELED, null, false);
        update.latestSuccessfulOrderId = null;
        purchases.upsertSubscription(update);
        AndroidPurchaseRow r = purchases.find("h1");
        assertEquals(1L, r.pid);
        assertEquals(T0, r.createdMs);
        assertEquals(T0 + 1000, r.updatedMs);
        assertEquals("GPA.h1", r.latestSuccessfulOrderId);
        assertEquals(Long.valueOf(T0 + DAY), r.expiryTimeMs);
        assertEquals(PlayStates.CANCELED, r.state);
        assertTrue(r.acknowledged);
    }

    public void testFindReturnsCopies() {
        purchases.upsertSubscription(sub("h1", 1L, PlayStates.ACTIVE, null, false));
        purchases.find("h1").state = "CHANGED";
        assertEquals(PlayStates.ACTIVE, purchases.find("h1").state);
    }

    public void testInsertProductIfAbsentDoesNotOverwrite() {
        AndroidPurchaseRow r = sub("p1", 1L, PlayStates.PURCHASED, null, false);
        r.productId = PlayProducts.MONTH_PASS;
        purchases.insertProductIfAbsent(r);
        purchases.setState("p1", PlayStates.GRANTED);
        purchases.insertProductIfAbsent(r);
        assertEquals(PlayStates.GRANTED, purchases.find("p1").state);
    }

    public void testSubscriberInsertRefusesDuplicate() {
        assertTrue(subscribers.insert(new SubscriberRow(1L, "GPA.1", 5, T0)));
        assertTrue(!subscribers.insert(new SubscriberRow(2L, "GPA.1", 7, T0 + DAY)));
        assertEquals(1L, subscribers.findByTransactionId("GPA.1").pid);
    }

    public void testSubscriptionPollWindows() {
        purchases.upsertSubscription(sub("live", 1L, PlayStates.ACTIVE, Long.valueOf(T0 + DAY), true));
        purchases.upsertSubscription(sub("far", 1L, PlayStates.ACTIVE, Long.valueOf(T0 + 300 * DAY), true));
        purchases.upsertSubscription(sub("old", 1L, PlayStates.ON_HOLD, Long.valueOf(T0 - 61 * DAY), true));
        purchases.upsertSubscription(sub("expired", 1L, PlayStates.EXPIRED, Long.valueOf(T0 - DAY), true));
        purchases.upsertSubscription(sub("revoked", 1L, PlayStates.REVOKED, Long.valueOf(T0 - DAY), true));
        purchases.upsertSubscription(sub("superseded", 1L, PlayStates.SUPERSEDED, Long.valueOf(T0 - DAY), true));
        purchases.upsertSubscription(sub("unacked", 1L, PlayStates.ACTIVE, Long.valueOf(T0 + 300 * DAY), false));
        List<AndroidPurchaseRow> polled = purchases.subscriptionsToPoll(T0);
        assertEquals(2, polled.size());
        assertEquals("live", polled.get(0).tokenHash);
        assertEquals("unacked", polled.get(1).tokenHash);
        // three days later the unacknowledged row has aged out; the live row is still near expiry
        polled = purchases.subscriptionsToPoll(T0 + 3 * DAY + 1);
        assertEquals(1, polled.size());
        assertEquals("live", polled.get(0).tokenHash);
    }

    public void testProductPollAndStaleGranting() {
        AndroidPurchaseRow purchased = sub("p1", 1L, PlayStates.PURCHASED, null, false);
        purchased.productId = PlayProducts.MONTH_PASS;
        purchases.insertProductIfAbsent(purchased);
        AndroidPurchaseRow consumed = sub("p2", 1L, PlayStates.CONSUMED, null, true);
        consumed.productId = PlayProducts.VACATION_10;
        purchases.insertProductIfAbsent(consumed);
        AndroidPurchaseRow granting = sub("p3", 1L, PlayStates.GRANTING, null, false);
        granting.productId = PlayProducts.VACATION_30;
        purchases.insertProductIfAbsent(granting);
        assertEquals(1, purchases.productsToPoll(T0).size());
        assertEquals("p1", purchases.productsToPoll(T0).get(0).tokenHash);
        assertEquals(0, purchases.productsToPoll(T0 + 3 * DAY + 1).size());
        assertEquals(0, purchases.staleGranting(T0).size());
        assertEquals(1, purchases.staleGranting(T0 + 1).size());
    }

    public void testAutoRenewingOnlyActiveOrGrace() {
        purchases.upsertSubscription(sub("c", 1L, PlayStates.CANCELED, Long.valueOf(T0 + DAY), true));
        assertTrue(!purchases.hasAutoRenewingSubscription(1L));
        purchases.upsertSubscription(sub("g", 1L, PlayStates.IN_GRACE_PERIOD, Long.valueOf(T0 + DAY), true));
        assertTrue(purchases.hasAutoRenewingSubscription(1L));
        assertTrue(!purchases.hasAutoRenewingSubscription(2L));
    }
}
