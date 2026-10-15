package org.pente.subscriptions.apple.test;

import java.util.Arrays;
import java.util.UUID;

import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleSubscriptionStatus;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * syncSubscription (spec 2026-10-15 §4.5): every status, the matching lastTransactions item, grace, revocation,
 * clamp with month-pass restack, idempotent repeats, renewals, the re-assert after a legacy overwrite, owners
 * without a requester, stamping and the pid move.
 *
 * Run: ant test-apple-billing
 */
public class AppleSubscriptionSyncTest extends AppleServiceTestCase {

    public AppleSubscriptionSyncTest(String name) {
        super(name);
    }

    private PlayClassification sync() throws Exception {
        return service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
    }

    public void testFirstTransactionIsNewWithTheYearlyRow() throws Exception {
        long expires = now + 365 * DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);

        assertEquals(PlayClassification.NEW, sync());

        SubscriberRow r = row(OTID);
        assertEquals(PID, r.pid);
        assertEquals(PlayProducts.YEARLY_LEVEL, r.level);
        assertEquals(expires - YEAR_OFFSET, r.paymentDateMs);
        AppleRow a = apple(OTID);
        assertEquals(AppleStates.ACTIVE, a.state);
        assertEquals(OTID, a.latestTransactionId);
        assertEquals(Long.valueOf(expires), a.expiryTimeMs);
        assertTrue("the purchase carried our token", a.tokenStamped);
        assertEquals("[" + PID + ":1YRNOADSORLIMITS:NEW]", players.grants.toString());
        assertEquals(0, api.stamped.size());
    }

    public void testRenewalTransactionIsRenewal() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + DAY), null);
        sync();
        now += 2 * DAY;
        status(1, yearly("2000000100000002", Long.valueOf(PID), now + 364 * DAY), null);

        assertEquals(PlayClassification.RENEWAL, sync());

        assertEquals(PlayProducts.YEARLY_LEVEL, row("2000000100000002").level);
        assertEquals("2000000100000002", apple(OTID).latestTransactionId);
        assertEquals(2, players.grants.size());
    }

    public void testSameStatusTwiceIsKnownWithOneGrantAndOneWrite() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + 365 * DAY), null);
        assertEquals(PlayClassification.NEW, sync());
        assertEquals(PlayClassification.KNOWN, sync());
        assertEquals(1, players.grants.size());
        assertEquals("an unchanged row is not rewritten, so 'updated' means changed", 1, store.upserts);
    }

    public void testPicksTheItemOfThisChain() throws Exception {
        AppleSubscriptionStatus mine = status(2, yearly(OTID, Long.valueOf(PID), now - DAY), null);
        AppleTransaction other = yearly("2000000900000009", Long.valueOf(PID), now + 300 * DAY);
        other.originalTransactionId = "2000000900000009";
        AppleSubscriptionStatus otherChain = new AppleSubscriptionStatus();
        otherChain.originalTransactionId = "2000000900000009";
        otherChain.status = 1;
        otherChain.transaction = other;
        api.statuses.get(OTID).add(0, otherChain);

        assertEquals(PlayClassification.INACTIVE, sync());

        assertNull("the other chain's active transaction is not granted here", row("2000000900000009"));
        assertEquals(AppleStates.EXPIRED, apple(OTID).state);
        assertEquals(mine.transaction.transactionId, apple(OTID).latestTransactionId);
    }

    public void testNoItemForTheChainIsInvalid() throws Exception {
        AppleSubscriptionStatus s = status(1, yearly(OTID, Long.valueOf(PID), now + DAY), null);
        s.originalTransactionId = "2000000999999999";
        assertEquals(PlayClassification.INVALID, sync());
        assertTrue(subscribers.rows.isEmpty());
        assertTrue(store.rows.isEmpty());
    }

    public void testBillingRetryGrantsNothing() throws Exception {
        status(3, yearly(OTID, Long.valueOf(PID), now - DAY), null);
        assertEquals(PlayClassification.INACTIVE, sync());
        assertNull(row(OTID));
        assertEquals(AppleStates.BILLING_RETRY, apple(OTID).state);
    }

    public void testExpiredGrantsNothing() throws Exception {
        status(2, yearly(OTID, Long.valueOf(PID), now - DAY), null);
        assertEquals(PlayClassification.INACTIVE, sync());
        assertNull(row(OTID));
        assertEquals(AppleStates.EXPIRED, apple(OTID).state);
    }

    public void testActiveButAlreadyPastGrantsNothing() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now - 1000), null);
        assertEquals(PlayClassification.INACTIVE, sync());
        assertNull(row(OTID));
    }

    public void testUnknownStatusIsInactive() throws Exception {
        status(9, yearly(OTID, Long.valueOf(PID), now + DAY), null);
        assertEquals(PlayClassification.INACTIVE, sync());
        assertNull(row(OTID));
    }

    public void testGraceExtendsTheLatestRow() throws Exception {
        long expires = now + DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        now += 2 * DAY;
        long graceEnd = expires + 16 * DAY;
        status(4, yearly(OTID, Long.valueOf(PID), expires), Long.valueOf(graceEnd));

        assertEquals(PlayClassification.KNOWN, sync());

        assertEquals(graceEnd - YEAR_OFFSET, row(OTID).paymentDateMs);
        assertTrue("access runs to the end of grace", expiration(PID).longValue() >= graceEnd - DAY);
        assertEquals(AppleStates.GRACE, apple(OTID).state);
        assertEquals(Long.valueOf(graceEnd), apple(OTID).expiryTimeMs);
        assertTrue(players.changed.contains(Long.valueOf(PID)));
    }

    public void testGraceNeverMovesARowEarlier() throws Exception {
        long expires = now + 300 * DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        status(4, yearly(OTID, Long.valueOf(PID), expires), Long.valueOf(now + 10 * DAY));
        sync();
        assertEquals(expires - YEAR_OFFSET, row(OTID).paymentDateMs);
    }

    public void testGraceWithoutAGraceDateGrantsNothingNew() throws Exception {
        status(4, yearly(OTID, Long.valueOf(PID), now - DAY), null);
        assertEquals(PlayClassification.INACTIVE, sync());
        assertNull(row(OTID));
    }

    public void testReassertsPaymentDateAfterALegacyOverwrite() throws Exception {
        long expires = now + DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        now += 2 * DAY;
        long graceEnd = expires + 16 * DAY;
        status(4, yearly(OTID, Long.valueOf(PID), expires), Long.valueOf(graceEnd));
        sync();
        // CheckiOSSubscribersRunnable's ON DUPLICATE KEY UPDATE puts the receipt's paymentdate back (§4.8)
        subscribers.updatePaymentDate(OTID, expires - YEAR_OFFSET);

        sync();

        assertEquals(graceEnd - YEAR_OFFSET, row(OTID).paymentDateMs);
    }

    public void testRevocationDateRevokesTheRowAndRestacksPasses() throws Exception {
        long expires = now + 200 * DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        long yearEnd = expiration(PID).longValue();
        subscribers.insert(new SubscriberRow(PID, "M1", PlayProducts.MONTH_LEVEL, yearEnd));
        AppleTransaction refunded = yearly(OTID, Long.valueOf(PID), expires);
        refunded.revocationDateMs = Long.valueOf(now - 1000);
        status(5, refunded, null);

        assertEquals(PlayClassification.INACTIVE, sync());

        assertEquals(0, row(OTID).level);
        assertEquals("the stacked pass starts now", now, row("M1").paymentDateMs);
        assertEquals(AppleStates.REVOKED, apple(OTID).state);
        assertEquals(Long.valueOf(now - 1000), apple(OTID).expiryTimeMs);
        assertTrue(players.changed.contains(Long.valueOf(PID)));
    }

    public void testRevocationRepeatChangesNothing() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + 200 * DAY), null);
        sync();
        AppleTransaction refunded = yearly(OTID, Long.valueOf(PID), now + 200 * DAY);
        refunded.revocationDateMs = Long.valueOf(now - 1000);
        status(5, refunded, null);
        sync();
        int changes = players.changed.size();
        int upserts = store.upserts;
        sync();
        assertEquals(changes, players.changed.size());
        assertEquals(upserts, store.upserts);
    }

    public void testEarlyExpiryClampsAndRestacksPasses() throws Exception {
        long expires = now + 200 * DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        long yearEnd = expiration(PID).longValue();
        subscribers.insert(new SubscriberRow(PID, "M1", PlayProducts.MONTH_LEVEL, yearEnd));
        long end = now + 10 * DAY;
        status(2, yearly(OTID, Long.valueOf(PID), end), null);

        sync();

        assertEquals(end - YEAR_OFFSET, row(OTID).paymentDateMs);
        assertTrue("the pass follows the clamped year", row("M1").paymentDateMs < yearEnd);
        assertTrue("the pass never starts before now", row("M1").paymentDateMs >= now);
    }

    public void testNaturalExpiryWithinSlackIsNotClamped() throws Exception {
        long expires = now + DAY;
        status(1, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        now += 2 * DAY;
        status(2, yearly(OTID, Long.valueOf(PID), expires), null);
        sync();
        assertEquals(expires - YEAR_OFFSET, row(OTID).paymentDateMs);
    }

    public void testFamilySharedIsInvalid() throws Exception {
        AppleTransaction shared = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        shared.familyShared = true;
        status(1, shared, null);
        assertEquals(PlayClassification.INVALID, sync());
        assertTrue(subscribers.rows.isEmpty());
        assertTrue(store.rows.isEmpty());
    }

    public void testForgedTokenIsInvalidAndWritesNothing() throws Exception {
        AppleTransaction forged = yearly(OTID, null, now + 300 * DAY);
        forged.appAccountToken = UUID.randomUUID();
        status(1, forged, null);
        assertEquals(PlayClassification.INVALID, sync());
        assertTrue(subscribers.rows.isEmpty());
        assertEquals(0, api.stamped.size());
    }

    public void testNoTokenResolvesThroughTheLegacyChainAndStampsOnce() throws Exception {
        // the legacy verifyReceipt path recorded last year's transaction for PID
        subscribers.insert(new SubscriberRow(PID, OTID, PlayProducts.YEARLY_LEVEL, now - 300 * DAY));
        AppleTransaction renewal = yearly("2000000100000002", null, now + 65 * DAY);
        api.histories.put(OTID, Arrays.asList(yearly(OTID, null, now - DAY), renewal));
        status(1, renewal, null);

        assertEquals(PlayClassification.RENEWAL, sync());
        assertEquals(PID, row("2000000100000002").pid);
        assertEquals("[" + OTID + ":" + tokens.create(PID) + "]", api.stamped.toString());
        assertTrue(apple(OTID).tokenStamped);

        sync();
        assertEquals("stamped once", 1, api.stamped.size());
    }

    public void testStampFailureIsRetriedAtTheNextSync() throws Exception {
        status(1, yearly(OTID, null, now + 300 * DAY), null);
        api.failStamps = 1;
        assertEquals(PlayClassification.NEW, service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, Long.valueOf(PID)));
        assertTrue(!apple(OTID).tokenStamped);
        assertEquals(0, api.stamped.size());

        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);

        assertEquals(1, api.stamped.size());
        assertTrue(apple(OTID).tokenStamped);
    }

    public void testUnresolvableOwnerIsInvalidAndWritesNothing() throws Exception {
        status(1, yearly(OTID, null, now + 300 * DAY), null);
        assertEquals(PlayClassification.INVALID, sync());
        assertTrue(subscribers.rows.isEmpty());
        assertTrue(store.rows.isEmpty());
        assertEquals(0, api.stamped.size());
    }

    public void testTwoChainOwnersLeaveItToThePost() throws Exception {
        subscribers.insert(new SubscriberRow(PID, OTID, PlayProducts.YEARLY_LEVEL, now - 700 * DAY));
        subscribers.insert(new SubscriberRow(OTHER_PID, "2000000100000002", PlayProducts.YEARLY_LEVEL, now - 300 * DAY));
        api.histories.put(OTID, Arrays.asList(yearly("2000000100000002", null, now - DAY)));
        status(1, yearly("2000000100000003", null, now + 300 * DAY), null);
        assertEquals(PlayClassification.INVALID, sync());
        assertNull(row("2000000100000003"));
    }

    public void testOwnerFromThePostNeedsNoResolution() throws Exception {
        status(1, yearly(OTID, null, now + 300 * DAY), null);
        assertEquals(PlayClassification.NEW, service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, Long.valueOf(PID)));
        assertEquals(PID, row(OTID).pid);
        assertEquals(0, api.count("history:"));
    }

    public void testPidMoveFollowsTheTokenAndAlerts() throws Exception {
        status(1, yearly(OTID, Long.valueOf(OTHER_PID), now + DAY), null);
        sync();
        now += 2 * DAY;
        // the same Apple ID resubscribed from a new pente.org account
        status(1, yearly("2000000100000002", Long.valueOf(PID), now + 364 * DAY), null);

        assertEquals(PlayClassification.RENEWAL, sync());

        assertEquals(PID, apple(OTID).pid);
        assertEquals(PID, row("2000000100000002").pid);
        assertEquals("already-written rows stay", OTHER_PID, row(OTID).pid);
        assertEquals(1, players.alerts.size());
        assertTrue(players.alerts.get(0), players.alerts.get(0).contains("moved from pid " + OTHER_PID + " to pid " + PID));
    }

    public void testATokenlessOwnerMismatchDoesNotMove() throws Exception {
        status(1, yearly(OTID, Long.valueOf(OTHER_PID), now + DAY), null);
        sync();
        now += 2 * DAY;
        status(1, yearly("2000000100000002", null, now + 364 * DAY), null);

        assertEquals(PlayClassification.SHARED,
                service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, Long.valueOf(PID)));

        assertEquals("no token, no move", OTHER_PID, apple(OTID).pid);
        assertNull(row("2000000100000002"));
        assertTrue(players.alerts.toString(), players.alerts.isEmpty());
    }

    public void testAMoveThatEndsSharedAlertsTheAdmin() throws Exception {
        status(1, yearly(OTID, Long.valueOf(OTHER_PID), now + 300 * DAY), null);
        sync();
        // the latest transaction now carries PID's token, but its dsg_subscribers row is OTHER_PID's
        status(1, yearly(OTID, Long.valueOf(PID), now + 300 * DAY), null);

        assertEquals(PlayClassification.SHARED, sync());

        assertEquals("not upserted, not moved", OTHER_PID, apple(OTID).pid);
        assertEquals(players.alerts.toString(), 1, players.alerts.size());
        String alert = players.alerts.get(0);
        assertTrue(alert, alert.contains(OTID) && alert.contains("from pid " + OTHER_PID + " to pid " + PID));
    }

    public void testSandboxNeedsTheAllowList() throws Exception {
        AppleTransaction t = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        t.environment = AppleEnvironment.SANDBOX;
        status(1, t, null);
        assertEquals(PlayClassification.INVALID, service.syncSubscription(OTID, AppleEnvironment.SANDBOX, null));
        assertTrue(subscribers.rows.isEmpty());
        sandboxAllowed.add(Long.valueOf(PID));
        assertEquals(PlayClassification.NEW, service.syncSubscription(OTID, AppleEnvironment.SANDBOX, null));
        assertEquals(AppleEnvironment.SANDBOX, apple(OTID).environment);
        assertEquals("api calls use the row's environment", "statuses:" + OTID + ":SANDBOX", api.calls.get(api.calls.size() - 1));
    }

    public void testApiFailurePropagatesForRetry() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + 300 * DAY), null);
        api.failStatuses = 1;
        try {
            sync();
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
        assertTrue(subscribers.rows.isEmpty());
        assertEquals(PlayClassification.NEW, sync());
    }
}
