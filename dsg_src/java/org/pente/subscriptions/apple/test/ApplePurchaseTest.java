package org.pente.subscriptions.apple.test;

import java.util.UUID;
import java.util.function.LongSupplier;

import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEntitlementService;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleSandboxGate;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.apple.AppleVerificationException;
import org.pente.subscriptions.apple.UnconfiguredAppleApi;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * POST /gameServer/appStorePurchase through AppleEntitlementService.purchase (spec 2026-10-15 §4.3, §4.4):
 * verification, the server-side re-read overriding the client's copy, resolvePid with a requester, the sandbox
 * gate on the owner, and the retry-later cases.
 *
 * Run: ant test-apple-billing
 */
public class ApplePurchaseTest extends AppleServiceTestCase {

    public ApplePurchaseTest(String name) {
        super(name);
    }

    /** the client posts "jws-" + id, which verifies to clientCopy; Apple's server answers serverCopy */
    private String post(AppleTransaction clientCopy, AppleTransaction serverCopy) {
        verifier.transactions.put("jws-" + clientCopy.transactionId, clientCopy);
        api.transactions.put(serverCopy.transactionId, serverCopy);
        return "jws-" + clientCopy.transactionId;
    }

    private String post(AppleTransaction t) {
        return post(t, t);
    }

    public void testMatchingTokenGrantsTheRequester() throws Exception {
        String jws = post(consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID)));
        assertEquals(PlayClassification.NEW, service.purchase(jws, PID));
        assertEquals(PID, row("3000000000000001").pid);
    }

    public void testAnotherAccountsTokenGrantsItsOwnerAndRepliesShared() throws Exception {
        String jws = post(consumable("3000000000000003", AppleProducts.VACATION_30, Long.valueOf(OTHER_PID)));
        assertEquals(PlayClassification.SHARED, service.purchase(jws, PID));
        assertEquals("never lost: the owner gets it", 30, players.vacationDaysOf(OTHER_PID));
        assertEquals(0, players.vacationDaysOf(PID));
        assertEquals(PlayClassification.SHARED, service.purchase(jws, PID));
        assertEquals(30, players.vacationDaysOf(OTHER_PID));
    }

    public void testAnotherAccountsSubscriptionTokenGrantsItsOwner() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(OTHER_PID), now + 300 * DAY);
        status(1, year, null);
        assertEquals(PlayClassification.SHARED, service.purchase(post(year), PID));
        assertEquals(OTHER_PID, row(OTID).pid);
    }

    public void testInvalidTokenIsInvalid() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        t.appAccountToken = UUID.randomUUID();
        assertEquals(PlayClassification.INVALID, service.purchase(post(t), PID));
        assertTrue(subscribers.rows.isEmpty());
    }

    public void testNoTokenUnownedSubscriptionIsAssignedAndStampedOnce() throws Exception {
        AppleTransaction year = yearly(OTID, null, now + 300 * DAY);
        status(1, year, null);
        String jws = post(year);
        assertEquals(PlayClassification.NEW, service.purchase(jws, PID));
        assertEquals(PID, row(OTID).pid);
        service.purchase(jws, PID);
        assertEquals("[" + OTID + ":" + tokens.create(PID) + "]", api.stamped.toString());
    }

    public void testNoTokenSubscriptionOfAnotherPidIsSharedAndGrantsNothing() throws Exception {
        subscribers.insert(new SubscriberRow(OTHER_PID, OTID, PlayProducts.YEARLY_LEVEL, now - 300 * DAY));
        AppleTransaction renewal = yearly("2000000100000002", null, now + 65 * DAY);
        status(1, renewal, null);
        assertEquals(PlayClassification.SHARED, service.purchase(post(renewal), PID));
        assertNull(row("2000000100000002"));
        assertEquals(0, api.stamped.size());
    }

    public void testNoTokenSubscriptionOwnedByTheRequesterIsARenewal() throws Exception {
        subscribers.insert(new SubscriberRow(PID, OTID, PlayProducts.YEARLY_LEVEL, now - 300 * DAY));
        AppleTransaction renewal = yearly("2000000100000002", null, now + 65 * DAY);
        status(1, renewal, null);
        assertEquals(PlayClassification.RENEWAL, service.purchase(post(renewal), PID));
        assertEquals(PID, row("2000000100000002").pid);
    }

    public void testNoTokenConsumableGoesToTheRequester() throws Exception {
        assertEquals(PlayClassification.NEW,
                service.purchase(post(consumable("3000000000000003", AppleProducts.VACATION_10, null)), PID));
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testFamilySharedIsInvalid() throws Exception {
        AppleTransaction client = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        AppleTransaction server = client.copy();
        server.familyShared = true;
        assertEquals(PlayClassification.INVALID, service.purchase(post(client, server), PID));
        assertTrue(subscribers.rows.isEmpty());
    }

    public void testTheServerCopyOverridesTheClient() throws Exception {
        AppleTransaction client = consumable("3000000000000003", AppleProducts.VACATION_60, Long.valueOf(PID));
        AppleTransaction server = client.copy();
        server.productId = AppleProducts.VACATION_10;
        assertEquals(PlayClassification.NEW, service.purchase(post(client, server), PID));
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testRevokedOnTheServerIsInvalidAndTombstoned() throws Exception {
        AppleTransaction client = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        AppleTransaction server = client.copy();
        server.revocationDateMs = Long.valueOf(now - 1000);
        assertEquals(PlayClassification.INVALID, service.purchase(post(client, server), PID));
        assertEquals(0, row("3000000000000001").level);
        assertEquals(PID, row("3000000000000001").pid);
    }

    public void testPidZeroTombstoneIsNoOwner() throws Exception {
        // a refund arrived before anyone owned the chain (pid-0 tombstone); the buyer then resubscribes without a token
        AppleTransaction year = yearly(OTID, null, now - 10 * DAY);
        year.revocationDateMs = Long.valueOf(now - 20 * DAY);
        service.revoke(year, null);
        assertEquals(0L, row(OTID).pid);
        AppleTransaction renewal = yearly("2000000100000002", null, now + 300 * DAY);
        status(1, renewal, null);

        assertEquals("a notification finds no owner and grants nothing", PlayClassification.INVALID,
                service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null));
        assertNull(row("2000000100000002"));
        assertTrue(players.grants.isEmpty());

        assertEquals("the buyer's POST is assigned the subscription", PlayClassification.RENEWAL,
                service.purchase(post(renewal), PID));
        assertEquals(PID, row("2000000100000002").pid);
        assertEquals(PID, apple(OTID).pid);
        assertTrue("no pid-move alert for a tombstone", players.alerts.isEmpty());
    }

    public void testUnknownProductAndForgedDataAreInvalid() throws Exception {
        assertEquals(PlayClassification.INVALID,
                service.purchase(post(consumable("3000000000000001", "gold_pass", Long.valueOf(PID))), PID));
        assertEquals(PlayClassification.INVALID, service.purchase("forged", PID));
        assertTrue(subscribers.rows.isEmpty());
    }

    public void testRetryableVerificationIsRetryLater() throws Exception {
        verifier.failWith = new AppleVerificationException("RETRYABLE_VERIFICATION_FAILURE", true);
        try {
            service.purchase("jws", PID);
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
    }

    public void testUnknownAtAppleIsInvalidButAnOutageIsRetryLater() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        verifier.transactions.put("jws", t);
        assertEquals("Apple answers 404", PlayClassification.INVALID, service.purchase("jws", PID));
        api.transactions.put(t.transactionId, t);
        api.failTransactionInfo = 1;
        try {
            service.purchase("jws", PID);
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
        assertEquals(PlayClassification.NEW, service.purchase("jws", PID));
    }

    public void testFinalNonNotFoundFailureIsNotInvalid() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        verifier.transactions.put("jws", t);
        api.transactions.put(t.transactionId, t);
        api.finalTransactionInfoFailure = true;
        try {
            service.purchase("jws", PID);
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(!expected.isRetryable());
            assertTrue(!expected.isTransactionNotFound());
        }
    }

    public void testUnconfiguredApiIsRetryLater() throws Exception {
        verifier.transactions.put("jws", consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID)));
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
        try {
            unconfigured.purchase("jws", PID);
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
    }

    public void testSandboxChecksTheOwnerNotTheRequester() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(OTHER_PID));
        t.environment = AppleEnvironment.SANDBOX;
        sandboxAllowed.add(Long.valueOf(PID));
        assertEquals(PlayClassification.INVALID, service.purchase(post(t), PID));
        assertTrue(subscribers.rows.isEmpty());
        assertEquals("transactionInfo:3000000000000001:SANDBOX", api.calls.get(0));
    }

    public void testKnownWithinTenMinutesOfTheNotificationStillSaysSuccess() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        service.notifyOneTime(t);
        now += 5 * 60 * 1000L;
        assertEquals(PlayClassification.NEW, service.purchase(post(t), PID));
        now += 6 * 60 * 1000L;
        assertEquals(PlayClassification.KNOWN, service.purchase(post(t), PID));
        assertEquals(1, players.grants.size());
    }
}
