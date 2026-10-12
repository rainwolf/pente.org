package org.pente.subscriptions.play.test;

import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.PlaySubscription;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.SubscriberRule;

/**
 * revoke(orderId): level 0, REVOKED, month-pass restack, no clawback of vacation days, repeat revoke
 * is a no-op, and a re-sync never resurrects a revoked order (spec 2026-10-11 §4.5, §5).
 *
 * Run: ant test-play-billing
 */
public class PlayRevokeTest extends PlayServiceTestCase {

    public PlayRevokeTest(String name) {
        super(name);
    }

    private void buyThreePasses() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        product("m2", PID, PlayProducts.MONTH_PASS, "GPA.m2");
        product("m3", PID, PlayProducts.MONTH_PASS, "GPA.m3");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        service.syncProduct(PlayProducts.MONTH_PASS, "m2", null);
        service.syncProduct(PlayProducts.MONTH_PASS, "m3", null);
    }

    public void testRevokedMonthPassRestacksStackedPassesFromNow() throws Exception {
        buyThreePasses();
        long expiryBefore = expiration(PID).longValue();

        service.revoke("GPA.m1");

        assertEquals(0, row("GPA.m1").level);
        assertEquals(now, row("GPA.m2").paymentDateMs);
        long m3 = SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, now).longValue();
        assertEquals(m3, row("GPA.m3").paymentDateMs);
        assertEquals(SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, m3), expiration(PID));
        assertEquals(PlayStates.REVOKED, android("m1").state);
        assertEquals(PlayStates.CONSUMED, android("m2").state);
        assertTrue(expiration(PID).longValue() < expiryBefore - 30 * DAY);
        assertEquals(1, players.changed.size());
    }

    public void testRepeatRevokeRestacksOnce() throws Exception {
        buyThreePasses();
        service.revoke("GPA.m1");
        service.revoke("GPA.m1");
        assertEquals(now, row("GPA.m2").paymentDateMs);
        assertEquals(1, players.changed.size());
    }

    public void testRevokedMonthPassLeavesALaterSeparatePassAlone() throws Exception {
        product("a", PID, PlayProducts.MONTH_PASS, "GPA.a");
        service.syncProduct(PlayProducts.MONTH_PASS, "a", null);
        now += 40 * DAY;
        product("b", PID, PlayProducts.MONTH_PASS, "GPA.b");
        service.syncProduct(PlayProducts.MONTH_PASS, "b", null);
        long b = row("GPA.b").paymentDateMs;
        assertEquals(now, b);
        now += 10 * DAY;

        service.revoke("GPA.a");

        assertEquals(0, row("GPA.a").level);
        assertEquals(b, row("GPA.b").paymentDateMs);
        assertEquals(SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, b), expiration(PID));
    }

    public void testRevokedMonthPassLeavesOtherCurrentMonthRowsAlone() throws Exception {
        product("a", PID, PlayProducts.MONTH_PASS, "GPA.a");
        service.syncProduct(PlayProducts.MONTH_PASS, "a", null);
        long a = row("GPA.a").paymentDateMs;
        // a PayPal or KotH month row dated later than the pass but already running
        now += 5 * DAY;
        subscribers.insert(new SubscriberRow(PID, "PAYPAL", PlayProducts.MONTH_LEVEL, now - DAY));
        long paypal = now - DAY;
        assertTrue(paypal > a);

        service.revoke("GPA.a");

        assertEquals(paypal, row("PAYPAL").paymentDateMs);
    }

    public void testFailedMonthRestackHealsOnRepeatRevoke() throws Exception {
        buyThreePasses();
        subscribers.failPaymentDateUpdates = 1;
        try {
            service.revoke("GPA.m1");
            fail("expected PlayStoreException");
        } catch (org.pente.subscriptions.play.PlayStoreException expected) {
            // RTDN answers 500 and Pub/Sub redelivers; the voided poller repeats it
        }
        assertTrue(row("GPA.m1").level != 0);

        service.revoke("GPA.m1");

        assertEquals(0, row("GPA.m1").level);
        assertEquals(now, row("GPA.m2").paymentDateMs);
        assertEquals(SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, now).longValue(),
                row("GPA.m3").paymentDateMs);
    }

    public void testRevokingLastPassShiftsNothing() throws Exception {
        buyThreePasses();
        long m1 = row("GPA.m1").paymentDateMs;
        long m2 = row("GPA.m2").paymentDateMs;
        service.revoke("GPA.m3");
        assertEquals(m1, row("GPA.m1").paymentDateMs);
        assertEquals(m2, row("GPA.m2").paymentDateMs);
        assertEquals(0, row("GPA.m3").level);
    }

    public void testRevokeLeavesOtherPlayersAlone() throws Exception {
        buyThreePasses();
        subscribers.insert(new SubscriberRow(OTHER_PID, "OTHER", PlayProducts.MONTH_LEVEL, now + 40 * DAY));
        service.revoke("GPA.m1");
        assertEquals(now + 40 * DAY, row("OTHER").paymentDateMs);
    }

    public void testRevokedYearlyStaysRevokedAfterResync() throws Exception {
        PlaySubscription s = subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        service.revoke("GPA.y");
        assertEquals(0, row("GPA.y").level);
        // the per-token row keeps Google's state, so the poller still follows the token's later orders
        assertEquals(PlayStates.ACTIVE, android("y").state);
        assertEquals(1, players.changed.size());
        assertNull(expiration(PID));

        s.expiryTimeMs = Long.valueOf(now + 330 * DAY);
        assertEquals(PlayClassification.KNOWN, service.syncSubscription("y", null));
        assertEquals(0, row("GPA.y").level);
        assertNull(expiration(PID));
    }

    public void testRevokedYearlyMovesStackedPassBackToNow() throws Exception {
        subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        product("m", PID, PlayProducts.MONTH_PASS, "GPA.m");
        service.syncProduct(PlayProducts.MONTH_PASS, "m", null);
        assertTrue(row("GPA.m").paymentDateMs > now + 290 * DAY);

        service.revoke("GPA.y");

        assertEquals(0, row("GPA.y").level);
        assertEquals(now, row("GPA.m").paymentDateMs);
        assertEquals(SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, now), expiration(PID));
        assertEquals(1, players.changed.size());

        service.revoke("GPA.y");
        assertEquals(now, row("GPA.m").paymentDateMs);
        assertEquals(1, players.changed.size());
    }

    public void testRevokedYearlyRestacksPassesAfterTheRemainingEntitlement() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        product("m2", PID, PlayProducts.MONTH_PASS, "GPA.m2");
        service.syncProduct(PlayProducts.MONTH_PASS, "m2", null);
        long m2 = row("GPA.m2").paymentDateMs;
        subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        product("m3", PID, PlayProducts.MONTH_PASS, "GPA.m3");
        service.syncProduct(PlayProducts.MONTH_PASS, "m3", null);
        product("m4", PID, PlayProducts.MONTH_PASS, "GPA.m4");
        service.syncProduct(PlayProducts.MONTH_PASS, "m4", null);

        service.revoke("GPA.y");

        // m1 is running and m2 was stacked on it before the yearly: both untouched
        assertEquals(now, row("GPA.m1").paymentDateMs);
        assertEquals(m2, row("GPA.m2").paymentDateMs);
        long m3 = SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, m2).longValue();
        assertEquals(m3, row("GPA.m3").paymentDateMs);
        long m4 = SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, m3).longValue();
        assertEquals(m4, row("GPA.m4").paymentDateMs);
        assertEquals(SubscriberRule.impliedExpiryMs(PlayProducts.MONTH_LEVEL, m4), expiration(PID));
    }

    public void testRevokedYearlyLeavesPassesOfAnotherYearlyAlone() throws Exception {
        subscription("y1", PID, PlayStates.ACTIVE, "GPA.y1", now + 300 * DAY);
        service.syncSubscription("y1", null);
        subscription("y2", PID, PlayStates.ACTIVE, "GPA.y2", now + 400 * DAY);
        service.syncSubscription("y2", null);
        product("m", PID, PlayProducts.MONTH_PASS, "GPA.m");
        service.syncProduct(PlayProducts.MONTH_PASS, "m", null);
        long m = row("GPA.m").paymentDateMs;

        service.revoke("GPA.y1");

        assertEquals(m, row("GPA.m").paymentDateMs);
    }

    public void testRevokedVacationKeepsDays() throws Exception {
        product("v", PID, PlayProducts.VACATION_30, "GPA.v");
        service.syncProduct(PlayProducts.VACATION_30, "v", null);
        service.revoke("GPA.v");
        assertEquals(30, players.vacationDaysOf(PID));
        assertEquals(PlayStates.REVOKED, android("v").state);
        assertEquals(1, players.changed.size());
    }

    public void testUnknownOrderIsNoop() throws Exception {
        service.revoke("GPA.unknown");
        service.revoke(null);
        service.revoke("");
        assertEquals(0, players.changed.size());
    }

    public void testRevokedProductIsInvalidOnResync() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        service.revoke("GPA.m1");
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "m1", Long.valueOf(PID)));
    }
}
