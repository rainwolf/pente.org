package org.pente.subscriptions.apple.test;

import java.util.function.LongSupplier;

import org.pente.subscriptions.apple.AppleEntitlementService;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleNotification;
import org.pente.subscriptions.apple.AppleNotificationHandler;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleSandboxGate;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.apple.AppleVerificationException;
import org.pente.subscriptions.apple.UnconfiguredAppleApi;
import org.pente.subscriptions.play.PlayProducts;

/**
 * Notification routing (spec 2026-10-15 §4.6): per type including OFFER_REDEEMED, duplicate UUIDs, sandbox
 * gating, 500 without recording on a transient failure, and 401 versus 500 by verification status.
 *
 * Run: ant test-apple-billing
 */
public class AppleNotificationTest extends AppleServiceTestCase {

    private AppleNotificationHandler handler;

    public AppleNotificationTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        handler = new AppleNotificationHandler(service, verifier, store, new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        });
    }

    /** registers a verified notification and returns the POST body that carries it */
    private String body(String uuid, String type, AppleTransaction tx) {
        AppleNotification n = new AppleNotification();
        n.notificationUuid = uuid;
        n.type = type;
        n.environment = tx == null ? AppleEnvironment.PRODUCTION : tx.environment;
        n.transaction = tx;
        verifier.notifications.put("payload-" + uuid, n);
        return "{\"signedPayload\":\"payload-" + uuid + "\"}";
    }

    public void testSubscriptionTypesSyncTheChain() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        String[] types = {"SUBSCRIBED", "DID_RENEW", "EXPIRED", "DID_FAIL_TO_RENEW", "GRACE_PERIOD_EXPIRED",
                "DID_CHANGE_RENEWAL_STATUS", "PRICE_INCREASE", "RENEWAL_EXTENDED", "OFFER_REDEEMED"};
        for (int i = 0; i < types.length; i++) {
            assertEquals(types[i], 200, handler.handle(body("u" + i, types[i], year)));
        }
        assertEquals("two status reads per sync", 2 * types.length, api.count("statuses:"));
        assertEquals(PlayProducts.YEARLY_LEVEL, row(OTID).level);
        assertEquals(1, players.grants.size());
        assertEquals(types.length, store.notifications.size());
    }

    public void testOneTimeChargeWithATokenGrants() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        assertEquals(200, handler.handle(body("u1", "ONE_TIME_CHARGE", pass)));
        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
    }

    public void testOneTimeChargeWithoutATokenIsLeftToThePost() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        assertEquals(200, handler.handle(body("u1", "ONE_TIME_CHARGE", pass)));
        assertTrue(subscribers.rows.isEmpty());
        assertTrue(store.notificationSeen("u1"));
    }

    public void testRefundTypesRefreshTheRefundState() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        service.syncOneTime(pass, PID);
        AppleTransaction refunded = pass.copy();
        refunded.revocationDateMs = Long.valueOf(now - 1000);
        api.transactions.put(refunded.transactionId, refunded);
        assertEquals(200, handler.handle(body("u1", "REFUND", pass)));
        assertEquals(0, row("3000000000000001").level);
        api.transactions.put(pass.transactionId, pass);
        assertEquals(200, handler.handle(body("u2", "REFUND_REVERSED", pass)));
        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
        assertEquals(200, handler.handle(body("u3", "REVOKE", pass)));
        assertEquals(3, api.count("transactionInfo:"));
    }

    public void testLogOnlyTypesTouchNothing() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        String[] types = {"CONSUMPTION_REQUEST", "REFUND_DECLINED", "TEST", "PRICE_CHANGE", "METADATA_UPDATE",
                "MIGRATION", "RENEWAL_EXTENSION", "SOMETHING_NEW"};
        for (int i = 0; i < types.length; i++) {
            assertEquals(types[i], 200, handler.handle(body("u" + i, types[i], i == 2 ? null : pass)));
        }
        assertEquals(0, api.calls.size());
        assertTrue(subscribers.rows.isEmpty());
        assertEquals(types.length, store.notifications.size());
    }

    public void testDuplicateUuidIsProcessedOnce() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        String b = body("u1", "DID_RENEW", year);
        assertEquals(200, handler.handle(b));
        int calls = api.calls.size();
        assertEquals(200, handler.handle(b));
        assertEquals(calls, api.calls.size());
    }

    public void testSandboxNotificationForAnUnlistedOwnerIs200AndGrantsNothing() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        pass.environment = AppleEnvironment.SANDBOX;
        assertEquals(200, handler.handle(body("u1", "ONE_TIME_CHARGE", pass)));
        assertTrue(subscribers.rows.isEmpty());
        sandboxAllowed.add(Long.valueOf(PID));
        assertEquals(200, handler.handle(body("u2", "ONE_TIME_CHARGE", pass)));
        assertEquals(PID, row("3000000000000001").pid);
    }

    public void testTransientFailureIs500WithoutRecording() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        String b = body("u1", "DID_RENEW", year);
        api.failStatuses = 1;
        assertEquals(500, handler.handle(b));
        assertTrue(!store.notificationSeen("u1"));
        assertEquals(200, handler.handle(b));
        assertTrue(store.notificationSeen("u1"));
        assertEquals(PlayProducts.YEARLY_LEVEL, row(OTID).level);
    }

    public void testRecordFailureIs500AndTheRetryIsHarmless() throws Exception {
        AppleTransaction vacation = consumable("3000000000000003", AppleProducts.VACATION_30, Long.valueOf(PID));
        String b = body("u1", "ONE_TIME_CHARGE", vacation);
        store.failRecords = 1;
        assertEquals(500, handler.handle(b));
        assertEquals(200, handler.handle(b));
        assertEquals(30, players.vacationDaysOf(PID));
    }

    public void testVerificationStatusPicks401Or500() throws Exception {
        assertEquals("not JSON", 401, handler.handle("garbage"));
        assertEquals("no signedPayload", 401, handler.handle("{}"));
        assertEquals("not Apple's", 401, handler.handle("{\"signedPayload\":\"forged\"}"));
        verifier.failWith = new AppleVerificationException("RETRYABLE_VERIFICATION_FAILURE", true);
        assertEquals(500, handler.handle("{\"signedPayload\":\"x\"}"));
        verifier.failWith = new AppleVerificationException("INVALID_CHAIN", false);
        assertEquals(401, handler.handle("{\"signedPayload\":\"x\"}"));
        assertTrue(store.notifications.isEmpty());
    }

    public void testFamilySharedSubscriptionIsLoggedOnly() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        year.familyShared = true;
        status(1, year, null);
        assertEquals(200, handler.handle(body("u1", "SUBSCRIBED", year)));
        assertEquals(0, api.calls.size());
        assertTrue(subscribers.rows.isEmpty());
    }

    public void testUnconfiguredApiIs500ButTestIs200() throws Exception {
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        AppleSandboxGate none = new AppleSandboxGate() {
            public boolean allows(long pid) {
                return false;
            }
        };
        AppleEntitlementService unconfigured = new AppleEntitlementService(new UnconfiguredAppleApi("no key"), verifier,
                store, subscribers, players, tokens, none, clock);
        AppleNotificationHandler h = new AppleNotificationHandler(unconfigured, verifier, store, clock);
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        assertEquals(500, h.handle(body("u1", "DID_RENEW", year)));
        assertEquals(200, h.handle(body("u2", "TEST", null)));
    }
}
