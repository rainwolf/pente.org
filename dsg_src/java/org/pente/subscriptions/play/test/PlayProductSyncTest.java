package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProduct;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.PlayTokens;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * syncProduct: month-pass stacking across levels, vacation GRANTING/GRANTED, refund during a tourney,
 * consume retry, idempotency and the per-pid lock (spec 2026-10-11 §4.4, §4.5, §5).
 *
 * Run: ant test-play-billing
 */
public class PlayProductSyncTest extends PlayServiceTestCase {

    public PlayProductSyncTest(String name) {
        super(name);
    }

    private long midnight(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long plusDays(long ms, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.add(Calendar.DATE, days);
        return c.getTimeInMillis();
    }

    public void testMonthPassWithoutEntitlementStartsNowAndIsConsumed() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        assertEquals(PlayClassification.NEW, service.syncProduct(PlayProducts.MONTH_PASS, "m1", Long.valueOf(PID)));
        SubscriberRow r = row("GPA.m1");
        assertEquals(PlayProducts.MONTH_LEVEL, r.level);
        assertEquals(now, r.paymentDateMs);
        assertEquals(1, api.consumed.size());
        assertEquals(PlayStates.CONSUMED, android("m1").state);
        assertEquals("GPA.m1", android("m1").latestSuccessfulOrderId);
        assertEquals(PID + ":month_pass:NEW", players.grants.get(0));
    }

    public void testMonthPassStacksAfterYearlyWhateverTheLevel() throws Exception {
        long yearlyExpiry = now + 20 * DAY;
        subscription("y", PID, PlayStates.CANCELED, "GPA.y", yearlyExpiry);
        service.syncSubscription("y", null);
        long current = expiration(PID).longValue();
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        assertEquals(current, row("GPA.m1").paymentDateMs);
        assertEquals(plusDays(current, 31), expiration(PID).longValue());
    }

    public void testTwoMonthPassesStack() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        product("m2", PID, PlayProducts.MONTH_PASS, "GPA.m2");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        service.syncProduct(PlayProducts.MONTH_PASS, "m2", null);
        assertEquals(plusDays(midnight(now), 62), expiration(PID).longValue());
    }

    public void testSameTokenTwiceIsKnown() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        assertEquals(PlayClassification.KNOWN, service.syncProduct(PlayProducts.MONTH_PASS, "m1", Long.valueOf(PID)));
        assertEquals(1, subscribers.rows.size());
        assertEquals(1, api.consumed.size());
        assertEquals(1, players.grants.size());
    }

    public void testVacationGrantsDaysAndLevelZeroRow() throws Exception {
        product("v1", PID, PlayProducts.VACATION_30, "GPA.v1");
        assertEquals(PlayClassification.NEW, service.syncProduct(PlayProducts.VACATION_30, "v1", Long.valueOf(PID)));
        assertEquals(30, players.vacationDaysOf(PID));
        assertEquals(0, row("GPA.v1").level);
        assertEquals(PlayStates.CONSUMED, android("v1").state);
        assertNull(expiration(PID));
    }

    public void testVacationDuringTourneyIsRefundedNotGranted() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        product("v1", PID, PlayProducts.VACATION_10, "GPA.v1");
        assertEquals(PlayClassification.REFUNDED, service.syncProduct(PlayProducts.VACATION_10, "v1", Long.valueOf(PID)));
        assertEquals(Collections.singletonList("GPA.v1"), api.refunded);
        assertEquals(0, players.vacationDaysOf(PID));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, api.consumed.size());
        assertEquals(PlayStates.REFUNDED, android("v1").state);
        assertEquals(PlayClassification.REFUNDED, service.syncProduct(PlayProducts.VACATION_10, "v1", null));
        assertEquals(1, api.refunded.size());
    }

    public void testRefundFailureLeavesPurchaseForRetry() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        product("v1", PID, PlayProducts.VACATION_10, "GPA.v1");
        api.failRefunds = 1;
        try {
            service.syncProduct(PlayProducts.VACATION_10, "v1", Long.valueOf(PID));
            fail("expected PlayApiException");
        } catch (org.pente.subscriptions.play.PlayApiException expected) {
            // retried by the client, RTDN or the poller
        }
        assertEquals(PlayStates.PURCHASED, android("v1").state);
        assertEquals(PlayClassification.REFUNDED, service.syncProduct(PlayProducts.VACATION_10, "v1", null));
    }

    public void testPromoCodeWithoutOrderIdUsesTokenKey() throws Exception {
        product("promo", PID, PlayProducts.MONTH_PASS, null);
        service.syncProduct(PlayProducts.MONTH_PASS, "promo", null);
        String key = "GP." + PlayTokens.sha256Hex("promo").substring(0, 61);
        assertNotNull(row(key));
        assertEquals(64, key.length());
    }

    public void testPromoVacationDuringTourneyRefundsWithoutApiCall() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        product("promo", PID, PlayProducts.VACATION_10, null);
        assertEquals(PlayClassification.REFUNDED, service.syncProduct(PlayProducts.VACATION_10, "promo", null));
        assertEquals(0, api.refunded.size());
    }

    public void testConsumeRetriedAfterFailure() throws Exception {
        product("v1", PID, PlayProducts.VACATION_60, "GPA.v1");
        api.failConsumes = 1;
        assertEquals(PlayClassification.NEW, service.syncProduct(PlayProducts.VACATION_60, "v1", Long.valueOf(PID)));
        assertEquals(PlayStates.GRANTED, android("v1").state);
        assertEquals(0, api.consumed.size());
        assertEquals(PlayClassification.KNOWN, service.syncProduct(PlayProducts.VACATION_60, "v1", null));
        assertEquals(1, api.consumed.size());
        assertEquals(PlayStates.CONSUMED, android("v1").state);
        assertEquals(60, players.vacationDaysOf(PID));
        assertEquals(1, subscribers.rows.size());
    }

    public void testAlreadyConsumedAtGoogleIsNotConsumedAgain() throws Exception {
        PlayProduct p = product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        p.consumptionState = Integer.valueOf(1);
        service.syncProduct(PlayProducts.MONTH_PASS, "m1", null);
        assertEquals(0, api.consumed.size());
        assertEquals(PlayStates.CONSUMED, android("m1").state);
    }

    public void testStuckGrantingIsNeverRegranted() throws Exception {
        product("v1", PID, PlayProducts.VACATION_10, "GPA.v1");
        api.failConsumes = 1;
        service.syncProduct(PlayProducts.VACATION_10, "v1", null);
        purchases.setState(hash("v1"), PlayStates.GRANTING);
        assertEquals(PlayClassification.KNOWN, service.syncProduct(PlayProducts.VACATION_10, "v1", null));
        assertEquals(10, players.vacationDaysOf(PID));
        assertEquals(0, api.consumed.size());
    }

    public void testApiProductIdDecidesTheGrant() throws Exception {
        product("t", PID, PlayProducts.VACATION_60, "GPA.t");
        service.syncProduct(PlayProducts.MONTH_PASS, "t", Long.valueOf(PID));
        assertEquals(60, players.vacationDaysOf(PID));
        assertEquals(0, row("GPA.t").level);
    }

    public void testClientPostWithoutApiProductIdUsesRequestedWhitelistedSku() throws Exception {
        PlayProduct p = product("t", PID, PlayProducts.MONTH_PASS, "GPA.t");
        p.productId = null;
        api.productTokens.put("t", PlayProducts.MONTH_PASS);
        assertEquals(PlayClassification.NEW, service.syncFromClient(PlayProducts.MONTH_PASS, "t", PID));
        assertEquals(PlayProducts.MONTH_LEVEL, row("GPA.t").level);
        assertEquals(PlayStates.CONSUMED, android("t").state);
        assertEquals(PID + ":month_pass:NEW", players.grants.get(0));
    }

    public void testTokenForOtherProductIsInvalid() throws Exception {
        PlayProduct p = product("t", PID, PlayProducts.VACATION_60, "GPA.t");
        p.productId = null;
        api.productTokens.put("t", PlayProducts.VACATION_60);
        // Google answers products.get(month_pass, t) with HTTP 400, which GooglePlayApi maps to null
        assertEquals(PlayClassification.INVALID, service.syncFromClient(PlayProducts.MONTH_PASS, "t", PID));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, purchases.rows.size());
        assertEquals(0, players.grants.size());
        assertEquals(0, api.consumed.size());
        // asked as the product it was bought as, the same token is granted
        assertEquals(PlayClassification.NEW, service.syncFromClient(PlayProducts.VACATION_60, "t", PID));
        assertEquals(60, players.vacationDaysOf(PID));
    }

    public void testBuyerIsThankedWhenRtdnGrantedFirst() throws Exception {
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        assertEquals(PlayClassification.NEW, service.syncProduct(PlayProducts.MONTH_PASS, "m1", null));
        now += 9 * 60 * 1000L;
        assertEquals(PlayClassification.NEW, service.syncFromClient(PlayProducts.MONTH_PASS, "m1", PID));
        now += 2 * 60 * 1000L;
        assertEquals(PlayClassification.KNOWN, service.syncFromClient(PlayProducts.MONTH_PASS, "m1", PID));
        assertEquals(1, players.grants.size());
        assertEquals(1, subscribers.rows.size());
        assertEquals(PlayClassification.SHARED, service.syncFromClient(PlayProducts.MONTH_PASS, "m1", OTHER_PID));
    }

    public void testSubscriberIsThankedWhenRtdnGrantedFirst() throws Exception {
        subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 365 * DAY);
        assertEquals(PlayClassification.NEW, service.syncSubscription("y", null));
        now += 9 * 60 * 1000L;
        assertEquals(PlayClassification.NEW, service.syncFromClient(PlayProducts.YEARLY, "y", PID));
        now += 2 * 60 * 1000L;
        assertEquals(PlayClassification.KNOWN, service.syncFromClient(PlayProducts.YEARLY, "y", PID));
        assertEquals(1, players.grants.size());
        assertEquals(1, subscribers.rows.size());
    }

    public void testProductsOffTheWhitelistAreInvalid() throws Exception {
        product("t", PID, PlayProducts.MONTH_PASS, "GPA.t");
        assertEquals(PlayClassification.INVALID, service.syncProduct("gold_pass", "t", Long.valueOf(PID)));
        PlayProduct p = product("y", PID, PlayProducts.YEARLY, "GPA.y");
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "y", Long.valueOf(PID)));
        p.productId = null;
        assertEquals(PlayClassification.INVALID, service.syncProduct("gold_pass", "y", Long.valueOf(PID)));
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "missing", Long.valueOf(PID)));
        assertEquals(0, subscribers.rows.size());
    }

    public void testPendingCanceledAndMultiQuantity() throws Exception {
        PlayProduct p = product("t", PID, PlayProducts.MONTH_PASS, "GPA.t");
        p.purchaseState = Integer.valueOf(2);
        assertEquals(PlayClassification.INACTIVE, service.syncProduct(PlayProducts.MONTH_PASS, "t", null));
        p.purchaseState = Integer.valueOf(1);
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "t", null));
        p.purchaseState = Integer.valueOf(0);
        p.quantity = Integer.valueOf(2);
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "t", null));
        p.quantity = Integer.valueOf(1);
        assertEquals(PlayClassification.NEW, service.syncProduct(PlayProducts.MONTH_PASS, "t", null));
    }

    public void testSharedAndTamperedProducts() throws Exception {
        product("t", PID, PlayProducts.MONTH_PASS, "GPA.t");
        assertEquals(PlayClassification.SHARED, service.syncProduct(PlayProducts.MONTH_PASS, "t", Long.valueOf(OTHER_PID)));
        PlayProduct p = product("u", PID, PlayProducts.MONTH_PASS, "GPA.u");
        p.obfuscatedAccountId = p.obfuscatedAccountId.substring(0, p.obfuscatedAccountId.length() - 1) + "x";
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "u", null));
        p.obfuscatedAccountId = null;
        assertEquals(PlayClassification.INVALID, service.syncProduct(PlayProducts.MONTH_PASS, "u", null));
        assertEquals(0, subscribers.rows.size());
        assertEquals(0, api.consumed.size());
    }

    private void runConcurrently(final Runnable a, final Runnable b) throws InterruptedException {
        final CountDownLatch start = new CountDownLatch(1);
        Thread ta = new Thread(new Runnable() {
            public void run() {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                a.run();
            }
        });
        Thread tb = new Thread(new Runnable() {
            public void run() {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                b.run();
            }
        });
        ta.start();
        tb.start();
        start.countDown();
        ta.join(10000);
        tb.join(10000);
    }

    private Runnable sync(final String productId, final String token, final List<Throwable> errors) {
        return new Runnable() {
            public void run() {
                try {
                    service.syncProduct(productId, token, null);
                } catch (Throwable t) {
                    errors.add(t);
                }
            }
        };
    }

    public void testConcurrentMonthPassesForOnePidStack() throws Exception {
        players.sleepMs = 100;
        product("m1", PID, PlayProducts.MONTH_PASS, "GPA.m1");
        product("m2", PID, PlayProducts.MONTH_PASS, "GPA.m2");
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
        runConcurrently(sync(PlayProducts.MONTH_PASS, "m1", errors), sync(PlayProducts.MONTH_PASS, "m2", errors));
        assertEquals(0, errors.size());
        players.sleepMs = 0;
        assertEquals(plusDays(midnight(now), 62), expiration(PID).longValue());
    }

    public void testConcurrentDeliveriesOfOneVacationTokenGrantOnce() throws Exception {
        players.sleepMs = 100;
        product("v1", PID, PlayProducts.VACATION_10, "GPA.v1");
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
        runConcurrently(sync(PlayProducts.VACATION_10, "v1", errors), sync(PlayProducts.VACATION_10, "v1", errors));
        assertEquals(0, errors.size());
        assertEquals(10, players.vacationDaysOf(PID));
        assertEquals(1, subscribers.rows.size());
    }
}
