package org.pente.subscriptions.apple.test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.apple.AppleAccountTokens;
import org.pente.subscriptions.apple.AppleEntitlementService;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleRenewal;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleSandboxGate;
import org.pente.subscriptions.apple.AppleSubscriptionStatus;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.test.FakePlayerGateway;
import org.pente.subscriptions.play.test.FakeSubscriberStore;

/** Shared fixture: AppleEntitlementService over fakes with a settable clock. */
public abstract class AppleServiceTestCase extends TestCase {

    protected static final long DAY = PlayProducts.DAY_MS;
    protected static final long YEAR_OFFSET = PlayProducts.PAYMENTDATE_OFFSET_MS;
    protected static final long PID = 23000000020001L;
    protected static final long OTHER_PID = 23000000020002L;
    /** the original transaction id of the test subscription chain */
    protected static final String OTID = "2000000100000001";

    protected long now;
    protected FakeAppleApi api;
    protected FakeAppleVerifier verifier;
    protected FakeAppleStore store;
    protected FakeSubscriberStore subscribers;
    protected FakePlayerGateway players;
    protected AppleAccountTokens tokens;
    /** pids whose username is on the sandbox allow-list */
    protected Set<Long> sandboxAllowed;
    protected AppleEntitlementService service;

    public AppleServiceTestCase(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 15, 15, 0, 0);
        now = c.getTimeInMillis();
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        api = new FakeAppleApi();
        verifier = new FakeAppleVerifier();
        store = new FakeAppleStore(clock);
        subscribers = new FakeSubscriberStore();
        players = new FakePlayerGateway(subscribers, clock);
        tokens = AppleAccountTokens.fromSecret("test-secret");
        sandboxAllowed = new HashSet<Long>();
        AppleSandboxGate gate = new AppleSandboxGate() {
            public boolean allows(long pid) {
                return sandboxAllowed.contains(Long.valueOf(pid));
            }
        };
        service = new AppleEntitlementService(api, verifier, store, subscribers, players, tokens, gate, clock);
    }

    /** a 1YRNOADSORLIMITS transaction of the test chain; tokenPid null means no appAccountToken */
    protected AppleTransaction yearly(String transactionId, Long tokenPid, long expiresMs) {
        AppleTransaction t = new AppleTransaction();
        t.transactionId = transactionId;
        t.originalTransactionId = OTID;
        t.productId = AppleProducts.YEARLY;
        t.environment = AppleEnvironment.PRODUCTION;
        t.purchaseDateMs = Long.valueOf(expiresMs - 365 * DAY);
        t.expiresDateMs = Long.valueOf(expiresMs);
        t.appAccountToken = tokenPid == null ? null : tokens.create(tokenPid.longValue());
        return t;
    }

    /** a consumable transaction; tokenPid null means no appAccountToken */
    protected AppleTransaction consumable(String transactionId, String productId, Long tokenPid) {
        AppleTransaction t = new AppleTransaction();
        t.transactionId = transactionId;
        t.originalTransactionId = transactionId;
        t.productId = productId;
        t.environment = AppleEnvironment.PRODUCTION;
        t.purchaseDateMs = Long.valueOf(now);
        t.appAccountToken = tokenPid == null ? null : tokens.create(tokenPid.longValue());
        return t;
    }

    /** Apple's Get All Subscription Statuses answer for OTID: exactly this item (graceMs null: no grace period) */
    protected AppleSubscriptionStatus status(int status, AppleTransaction latest, Long graceMs) {
        AppleSubscriptionStatus s = new AppleSubscriptionStatus();
        s.originalTransactionId = latest.originalTransactionId;
        s.status = status;
        s.transaction = latest;
        AppleRenewal r = new AppleRenewal();
        r.originalTransactionId = latest.originalTransactionId;
        r.gracePeriodExpiresDateMs = graceMs;
        r.appAccountToken = latest.appAccountToken;
        s.renewal = r;
        List<AppleSubscriptionStatus> answer = new ArrayList<AppleSubscriptionStatus>();
        answer.add(s);
        api.statuses.put(latest.originalTransactionId, answer);
        api.transactions.put(latest.transactionId, latest);
        return s;
    }

    protected SubscriberRow row(String transactionId) {
        return subscribers.findByTransactionId(transactionId);
    }

    protected AppleRow apple(String key) {
        return store.find(key);
    }

    /** getSubscriptionExpiration() at the fixture clock, per the getSubscriberData rule */
    protected Long expiration(long pid) {
        return players.subscriptionExpirationMs(pid);
    }
}
