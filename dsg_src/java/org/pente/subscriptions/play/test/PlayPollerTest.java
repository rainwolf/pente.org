package org.pente.subscriptions.play.test;

import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.VoidedPage;

/**
 * pollSubscriptions (6-hourly) and pollVoided (daily): renewals and acknowledgements without the app,
 * consume retries, paged voided purchases and the stale-GRANTING admin alert (spec 2026-10-11 §4.7).
 *
 * Run: ant test-play-billing
 */
public class PlayPollerTest extends PlayServiceTestCase {

    public PlayPollerTest(String name) {
        super(name);
    }

    public void testRenewalPickedUpWithoutTheApp() throws Exception {
        PlaySubscription s = subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + DAY);
        service.syncSubscription("y", null);
        s.latestSuccessfulOrderId = "GPA.y..0";
        s.expiryTimeMs = Long.valueOf(now + 366 * DAY);
        service.pollSubscriptions();
        assertNotNull(row("GPA.y..0"));
        assertEquals(PID + ":yearly_subscription:RENEWAL", players.grants.get(1));
    }

    public void testRenewalAfterARevokedYearlyOrderIsPolled() throws Exception {
        PlaySubscription s = subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + DAY);
        service.syncSubscription("y", null);
        // refunded in Play Console without revoking: the subscription stays ACTIVE at Google
        service.revoke("GPA.y");
        assertEquals(0, row("GPA.y").level);
        s.latestSuccessfulOrderId = "GPA.y..0";
        s.expiryTimeMs = Long.valueOf(now + 366 * DAY);

        service.pollSubscriptions();

        assertNotNull(row("GPA.y..0"));
        assertEquals(PlayProducts.YEARLY_LEVEL, row("GPA.y..0").level);
        assertEquals(0, row("GPA.y").level);
        assertEquals(PID + ":yearly_subscription:RENEWAL", players.grants.get(1));
    }

    public void testFailedAcknowledgeRetriedByPoller() throws Exception {
        subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        api.failAcknowledges = 1;
        service.syncSubscription("y", Long.valueOf(PID));
        assertTrue(!android("y").acknowledged);
        service.pollSubscriptions();
        assertTrue(android("y").acknowledged);
    }

    public void testFarFromExpiryAcknowledgedRowIsNotPolled() throws Exception {
        PlaySubscription s = subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        s.latestSuccessfulOrderId = "GPA.y..0";
        service.pollSubscriptions();
        assertNull(row("GPA.y..0"));
    }

    public void testUnconsumedProductConsumedByPoller() throws Exception {
        product("m", PID, PlayProducts.MONTH_PASS, "GPA.m");
        api.failConsumes = 1;
        service.syncProduct(PlayProducts.MONTH_PASS, "m", null);
        assertEquals(PlayStates.GRANTED, android("m").state);
        service.pollSubscriptions();
        assertEquals(PlayStates.CONSUMED, android("m").state);
        assertEquals(1, subscribers.rows.size());
    }

    public void testOneFailingTokenDoesNotStopTheOthers() throws Exception {
        PlaySubscription a = subscription("a", PID, PlayStates.ACTIVE, "GPA.a", now + DAY);
        PlaySubscription b = subscription("b", OTHER_PID, PlayStates.ACTIVE, "GPA.b", now + DAY);
        service.syncSubscription("a", null);
        service.syncSubscription("b", null);
        a.latestSuccessfulOrderId = "GPA.a..0";
        a.expiryTimeMs = Long.valueOf(now + 366 * DAY);
        b.latestSuccessfulOrderId = "GPA.b..0";
        b.expiryTimeMs = Long.valueOf(now + 366 * DAY);
        api.failGets = 1;
        service.pollSubscriptions();
        assertEquals(1, (row("GPA.a..0") == null ? 0 : 1) + (row("GPA.b..0") == null ? 0 : 1));
    }

    public void testVoidedPagesAreAllRevoked() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        product("m2", PID, PlayProducts.MONTH_PASS, "GPA.m2");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        service.syncProduct(PlayProducts.MONTH_PASS, "m2", null);
        VoidedPage first = new VoidedPage();
        first.orderIds.add("GPA.m1");
        first.orderIds.add("GPA.not-ours");
        first.nextPageToken = "1";
        VoidedPage second = new VoidedPage();
        second.orderIds.add("GPA.m2");
        api.voidedPages.add(first);
        api.voidedPages.add(second);

        service.pollVoided();

        assertEquals(0, row("GPA.m1").level);
        assertEquals(0, row("GPA.m2").level);
        assertEquals(2, api.voidedStartTimes.size());
        long start = api.voidedStartTimes.get(0).longValue();
        assertTrue("start inside Play's 30-day window", start > now - 30 * DAY);
        assertTrue("start covers at least 29 days", start <= now - 29 * DAY);
    }

    public void testStaleGrantingAlertsAdmin() throws Exception {
        product("v", PID, PlayProducts.VACATION_10, "GPA.v");
        api.failConsumes = 1;
        service.syncProduct(PlayProducts.VACATION_10, "v", null);
        purchases.setState(hash("v"), PlayStates.GRANTING);
        now += 30 * 60 * 1000L;
        service.pollVoided();
        assertEquals(0, players.alerts.size());
        now += 31 * 60 * 1000L;
        service.pollVoided();
        assertEquals(1, players.alerts.size());
        assertTrue(players.alerts.get(0), players.alerts.get(0).contains(hash("v")));
    }

    public void testStaleGrantingAlertSentEvenWhenGoogleFails() throws Exception {
        product("v", PID, PlayProducts.VACATION_10, "GPA.v");
        service.syncProduct(PlayProducts.VACATION_10, "v", null);
        purchases.setState(hash("v"), PlayStates.GRANTING);
        now += 2 * 3600 * 1000L;
        api.failVoided = 1;
        try {
            service.pollVoided();
            fail("expected PlayApiException");
        } catch (org.pente.subscriptions.play.PlayApiException expected) {
            // the daily runnable logs it
        }
        assertEquals(1, players.alerts.size());
        assertEquals(PlayClassification.KNOWN, service.syncProduct(PlayProducts.VACATION_10, "v", null));
    }
}
