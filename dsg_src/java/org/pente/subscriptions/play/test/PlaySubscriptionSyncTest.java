package org.pente.subscriptions.play.test;

import java.util.function.LongSupplier;

import org.pente.subscriptions.play.PlayApiException;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayEntitlementService;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * syncSubscription: classification, grant, deferral, clamp, acknowledge, link, resolvePid
 * (spec 2026-10-11 §4.3, §4.5, §5).
 *
 * Run: ant test-play-billing
 */
public class PlaySubscriptionSyncTest extends PlayServiceTestCase {

    public PlaySubscriptionSyncTest(String name) {
        super(name);
    }

    public void testNewPurchaseGrantsYearAndAcknowledges() throws Exception {
        long expiry = now + 365 * DAY;
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", expiry);

        assertEquals(PlayClassification.NEW, service.syncSubscription("tok1", Long.valueOf(PID)));

        SubscriberRow r = row("GPA.1");
        assertEquals(PID, r.pid);
        assertEquals(PlayProducts.YEARLY_LEVEL, r.level);
        assertEquals(expiry - PlayProducts.PAYMENTDATE_OFFSET_MS, r.paymentDateMs);
        assertEquals(1, api.acknowledged.size());
        assertTrue(android("tok1").acknowledged);
        assertEquals(PlayStates.ACTIVE, android("tok1").state);
        assertEquals(PID, android("tok1").pid);
        assertEquals("GPA.1", android("tok1").latestSuccessfulOrderId);
        assertEquals(1, players.grants.size());
        assertEquals(PID + ":yearly_subscription:NEW", players.grants.get(0));
        long implied = expiration(PID).longValue();
        assertTrue(implied >= expiry && implied <= expiry + 2 * DAY);
    }

    public void testSameTokenTwiceIsKnownWithOneGrant() throws Exception {
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        service.syncSubscription("tok1", Long.valueOf(PID));
        assertEquals(PlayClassification.KNOWN, service.syncSubscription("tok1", null));
        assertEquals(1, subscribers.rows.size());
        assertEquals(1, players.grants.size());
        assertEquals(1, api.acknowledged.size());
    }

    public void testNewOrderOnKnownTokenIsRenewal() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 10 * DAY);
        service.syncSubscription("tok1", Long.valueOf(PID));
        s.latestSuccessfulOrderId = "GPA.1..0";
        s.expiryTimeMs = Long.valueOf(now + 375 * DAY);
        assertEquals(PlayClassification.RENEWAL, service.syncSubscription("tok1", null));
        assertEquals(2, subscribers.rows.size());
        assertEquals(now + 375 * DAY - PlayProducts.PAYMENTDATE_OFFSET_MS, row("GPA.1..0").paymentDateMs);
        assertEquals(PID + ":yearly_subscription:RENEWAL", players.grants.get(1));
    }

    public void testLinkedTokenOfSamePidIsRenewalAndSupersedesOld() throws Exception {
        subscription("old", PID, PlayStates.ACTIVE, "GPA.1", now + 5 * DAY);
        service.syncSubscription("old", Long.valueOf(PID));
        PlaySubscription s = subscription("new", PID, PlayStates.ACTIVE, "GPA.2", now + 365 * DAY);
        s.linkedPurchaseToken = "old";
        assertEquals(PlayClassification.RENEWAL, service.syncSubscription("new", Long.valueOf(PID)));
        assertEquals(PlayStates.SUPERSEDED, android("old").state);
        assertEquals(hash("old"), android("new").linkedPurchaseTokenHash);
    }

    public void testOtherRequesterGetsSharedAndNothingIsWritten() throws Exception {
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        assertEquals(PlayClassification.SHARED, service.syncSubscription("tok1", Long.valueOf(OTHER_PID)));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, purchases.rows.size());
        assertEquals(0, api.acknowledged.size());
    }

    public void testTamperedAccountIdIsInvalidEvenWithExpiredToken() throws Exception {
        subscription("old", PID, PlayStates.EXPIRED, "GPA.0", now - DAY);
        service.syncSubscription("old", null);
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        s.obfuscatedAccountId = OTHER_PID + s.obfuscatedAccountId.substring(s.obfuscatedAccountId.indexOf('.'));
        s.expiredPurchaseToken = "old";
        assertEquals(PlayClassification.INVALID, service.syncSubscription("tok1", null));
        assertNull(row("GPA.1"));
        assertEquals(0, api.acknowledged.size());
    }

    public void testUnknownTokenAndForeignProductAreInvalid() throws Exception {
        assertEquals(PlayClassification.INVALID, service.syncSubscription("nope", Long.valueOf(PID)));
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        s.productId = "gold_subscription";
        assertEquals(PlayClassification.INVALID, service.syncSubscription("tok1", Long.valueOf(PID)));
        assertEquals(0, subscribers.rows.size());
    }

    public void testExpiredPurchaseTokenResolvesPid() throws Exception {
        subscription("old", PID, PlayStates.EXPIRED, "GPA.0", now - DAY);
        service.syncSubscription("old", null);
        PlaySubscription s = subscription("resub", PID, PlayStates.ACTIVE, "GPA.9", now + 365 * DAY);
        s.obfuscatedAccountId = null;
        s.expiredPurchaseToken = "old";
        assertEquals(PlayClassification.NEW, service.syncSubscription("resub", null));
        assertEquals(PID, row("GPA.9").pid);
        assertEquals(1, api.acknowledged.size());
    }

    public void testUnresolvablePidIsNeitherGrantedNorAcknowledged() throws Exception {
        PlaySubscription s = subscription("resub", PID, PlayStates.ACTIVE, "GPA.9", now + 365 * DAY);
        s.obfuscatedAccountId = null;
        s.expiredPurchaseToken = "never-seen";
        assertEquals(PlayClassification.INVALID, service.syncSubscription("resub", null));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, purchases.rows.size());
        assertEquals(0, api.acknowledged.size());
    }

    public void testPendingPurchaseIsInactive() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.PENDING, null, now + 365 * DAY);
        s.expiryTimeMs = null;
        assertEquals(PlayClassification.INACTIVE, service.syncSubscription("tok1", Long.valueOf(PID)));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, api.acknowledged.size());
        assertEquals(PlayStates.PENDING, android("tok1").state);
    }

    public void testCanceledGrantsUntilExpiryAndExpiredDoesNot() throws Exception {
        subscription("c", PID, PlayStates.CANCELED, "GPA.c", now + 20 * DAY);
        assertEquals(PlayClassification.NEW, service.syncSubscription("c", null));
        subscription("e", PID, PlayStates.EXPIRED, "GPA.e", now - DAY);
        assertEquals(PlayClassification.INACTIVE, service.syncSubscription("e", null));
        assertNull(row("GPA.e"));
        subscription("g", PID, PlayStates.IN_GRACE_PERIOD, "GPA.g", now + 3 * DAY);
        assertEquals(PlayClassification.NEW, service.syncSubscription("g", null));
    }

    public void testDeferralMovesPaymentDateLater() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 100 * DAY);
        service.syncSubscription("tok1", null);
        s.expiryTimeMs = Long.valueOf(now + 130 * DAY);
        assertEquals(PlayClassification.KNOWN, service.syncSubscription("tok1", null));
        assertEquals(now + 130 * DAY - PlayProducts.PAYMENTDATE_OFFSET_MS, row("GPA.1").paymentDateMs);
        assertEquals(1, players.changed.size());
    }

    public void testEarlierExpiryWhileActiveDoesNotShorten() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 100 * DAY);
        service.syncSubscription("tok1", null);
        s.expiryTimeMs = Long.valueOf(now + 90 * DAY);
        service.syncSubscription("tok1", null);
        assertEquals(now + 100 * DAY - PlayProducts.PAYMENTDATE_OFFSET_MS, row("GPA.1").paymentDateMs);
    }

    public void testExpiredByRevocationClampsToPlayExpiry() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 300 * DAY);
        service.syncSubscription("tok1", null);
        long revokedAt = now - 3600L * 1000;
        s.subscriptionState = PlayStates.EXPIRED;
        s.expiryTimeMs = Long.valueOf(revokedAt);
        assertEquals(PlayClassification.INACTIVE, service.syncSubscription("tok1", null));
        assertEquals(revokedAt - PlayProducts.PAYMENTDATE_OFFSET_MS, row("GPA.1").paymentDateMs);
        long implied = expiration(PID).longValue();
        assertTrue(implied >= revokedAt && implied <= revokedAt + 2 * DAY);
        assertEquals(1, players.changed.size());
    }

    public void testStaleReadCannotUndoAClamp() throws Exception {
        final PlaySubscription staleActive = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 300 * DAY);
        service.syncSubscription("tok1", null);
        long revokedAt = now - 3600L * 1000;
        subscription("tok1", PID, PlayStates.EXPIRED, "GPA.1", revokedAt);
        service.syncSubscription("tok1", null);
        long clamped = row("GPA.1").paymentDateMs;
        assertEquals(revokedAt - PlayProducts.PAYMENTDATE_OFFSET_MS, clamped);

        // a poller read ACTIVE before the revoke was applied; by the time it holds the pid lock Google says EXPIRED
        FakePlayApi racing = new FakePlayApi() {
            private boolean staleServed;

            public synchronized PlaySubscription getSubscription(String purchaseToken) throws PlayApiException {
                if (!staleServed) {
                    staleServed = true;
                    return staleActive;
                }
                return super.getSubscription(purchaseToken);
            }
        };
        racing.subscriptions.putAll(api.subscriptions);
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        PlayEntitlementService poller = new PlayEntitlementService(racing, purchases, subscribers, players, accountIds, clock);
        assertEquals(PlayClassification.INACTIVE, poller.syncSubscription("tok1", null));
        assertEquals(clamped, row("GPA.1").paymentDateMs);
        assertEquals(PlayStates.EXPIRED, android("tok1").state);
        assertEquals(1, players.changed.size());
    }

    public void testOnHoldClamps() throws Exception {
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 200 * DAY);
        service.syncSubscription("tok1", null);
        s.subscriptionState = PlayStates.ON_HOLD;
        s.expiryTimeMs = Long.valueOf(now - 2 * DAY);
        service.syncSubscription("tok1", null);
        assertEquals(now - 2 * DAY - PlayProducts.PAYMENTDATE_OFFSET_MS, row("GPA.1").paymentDateMs);
    }

    public void testNaturalExpiryWithinSlackIsNotClamped() throws Exception {
        long expiry = now + 10 * DAY;
        PlaySubscription s = subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", expiry);
        service.syncSubscription("tok1", null);
        long paymentDate = row("GPA.1").paymentDateMs;
        now = expiry + DAY;
        s.subscriptionState = PlayStates.EXPIRED;
        service.syncSubscription("tok1", null);
        assertEquals(paymentDate, row("GPA.1").paymentDateMs);
        assertEquals(0, players.changed.size());
    }

    public void testAcknowledgeFailureKeepsGrantAndRetries() throws Exception {
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        api.failAcknowledges = 1;
        assertEquals(PlayClassification.NEW, service.syncSubscription("tok1", Long.valueOf(PID)));
        assertNotNull(row("GPA.1"));
        assertTrue(!android("tok1").acknowledged);
        assertEquals(PlayClassification.KNOWN, service.syncSubscription("tok1", null));
        assertEquals(1, api.acknowledged.size());
        assertTrue(android("tok1").acknowledged);
        assertEquals(1, subscribers.rows.size());
    }

    public void testGoogleFailurePropagatesForRetry() {
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        api.failGets = 1;
        try {
            service.syncSubscription("tok1", Long.valueOf(PID));
            fail("expected PlayApiException");
        } catch (PlayApiException expected) {
            // the servlet replies "retry later", RTDN replies 500
        } catch (Exception other) {
            fail("unexpected " + other);
        }
        assertEquals(0, subscribers.rows.size());
    }

    public void testOrderRecordedForAnotherPidIsShared() throws Exception {
        subscribers.insert(new SubscriberRow(OTHER_PID, "GPA.1", PlayProducts.YEARLY_LEVEL, now));
        subscription("tok1", PID, PlayStates.ACTIVE, "GPA.1", now + 365 * DAY);
        assertEquals(PlayClassification.SHARED, service.syncSubscription("tok1", null));
        assertEquals(0, api.acknowledged.size());
    }
}
