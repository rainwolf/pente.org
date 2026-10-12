package org.pente.subscriptions.play.test;

import java.util.Calendar;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.play.AccountIds;
import org.pente.subscriptions.play.AndroidPurchaseRow;
import org.pente.subscriptions.play.PlayEntitlementService;
import org.pente.subscriptions.play.PlayProduct;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.PlayTokens;
import org.pente.subscriptions.play.SubscriberRow;

/** Shared fixture: PlayEntitlementService over fakes with a settable clock. */
public abstract class PlayServiceTestCase extends TestCase {

    protected static final long DAY = PlayProducts.DAY_MS;
    protected static final long PID = 23000000020001L;
    protected static final long OTHER_PID = 23000000020002L;

    protected long now;
    protected FakePlayApi api;
    protected FakeAndroidPurchaseStore purchases;
    protected FakeSubscriberStore subscribers;
    protected FakePlayerGateway players;
    protected AccountIds accountIds;
    protected PlayEntitlementService service;

    public PlayServiceTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 11, 15, 0, 0);
        now = c.getTimeInMillis();
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        api = new FakePlayApi();
        purchases = new FakeAndroidPurchaseStore(clock);
        subscribers = new FakeSubscriberStore();
        players = new FakePlayerGateway(subscribers, clock);
        accountIds = AccountIds.fromSecret("test-secret");
        service = new PlayEntitlementService(api, purchases, subscribers, players, accountIds, clock);
    }

    protected static String hash(String token) {
        return PlayTokens.sha256Hex(token);
    }

    /** a yearly_subscription response bound to pid through the account id */
    protected PlaySubscription subscription(String token, long pid, String state, String orderId, long expiryMs) {
        PlaySubscription s = new PlaySubscription();
        s.subscriptionState = state;
        s.acknowledgementState = PlayStates.ACK_PENDING;
        s.productId = PlayProducts.YEARLY;
        s.expiryTimeMs = Long.valueOf(expiryMs);
        s.latestSuccessfulOrderId = orderId;
        s.obfuscatedAccountId = accountIds.create(pid);
        api.subscriptions.put(token, s);
        return s;
    }

    /** a purchased, unconsumed one-time product response bound to pid through the account id */
    protected PlayProduct product(String token, long pid, String productId, String orderId) {
        PlayProduct p = new PlayProduct();
        p.productId = productId;
        p.purchaseState = Integer.valueOf(0);
        p.consumptionState = Integer.valueOf(0);
        p.acknowledgementState = Integer.valueOf(0);
        p.orderId = orderId;
        p.obfuscatedAccountId = accountIds.create(pid);
        api.products.put(token, p);
        return p;
    }

    protected SubscriberRow row(String transactionId) {
        return subscribers.findByTransactionId(transactionId);
    }

    protected AndroidPurchaseRow android(String token) {
        return purchases.row(hash(token));
    }

    /** getSubscriptionExpiration() at the fixture clock, per the getSubscriberData rule */
    protected Long expiration(long pid) {
        return players.subscriptionExpirationMs(pid);
    }
}
