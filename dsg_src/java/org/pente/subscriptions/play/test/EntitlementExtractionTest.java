package org.pente.subscriptions.play.test;

import java.util.Calendar;
import java.util.function.LongSupplier;

import junit.framework.TestCase;

import org.pente.subscriptions.play.AccountIds;
import org.pente.subscriptions.play.DsgPlayGateway;
import org.pente.subscriptions.play.EntitlementLocks;
import org.pente.subscriptions.play.MonthPassStack;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayEntitlementService;
import org.pente.subscriptions.play.PlayProduct;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.SubscriberRule;
import org.pente.turnBased.TBVacation;

/**
 * The pieces extracted from the Play code so the App Store code can share them
 * (spec 2026-10-15 §4.5 "small extraction first"): one lock per pid for every store, the public
 * month-pass restack, the store name in the grant alert, vacationDaysLeft and setLevel.
 * Play's own behaviour is pinned by the other play-billing test classes, which must stay green.
 *
 * Run: ant test-play-billing
 */
public class EntitlementExtractionTest extends TestCase {

    private static final long DAY = PlayProducts.DAY_MS;
    private static final long PID = 23000000020001L;

    private long now;

    public EntitlementExtractionTest(String name) {
        super(name);
    }

    protected void setUp() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, 15, 15, 0, 0);
        now = c.getTimeInMillis();
    }

    public void testOneLockPerPidForEveryCaller() {
        Object a = EntitlementLocks.lockFor(PID);
        assertSame(a, EntitlementLocks.lockFor(PID));
        assertTrue(a != EntitlementLocks.lockFor(PID + 1));
    }

    public void testPlayGrantWaitsForTheSharedLock() throws Exception {
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        FakePlayApi api = new FakePlayApi();
        final FakeSubscriberStore subscribers = new FakeSubscriberStore();
        AccountIds ids = AccountIds.fromSecret("test-secret");
        final PlayEntitlementService service = new PlayEntitlementService(api, new FakeAndroidPurchaseStore(clock),
                subscribers, new FakePlayerGateway(subscribers, clock), ids, clock);
        PlayProduct p = new PlayProduct();
        p.productId = PlayProducts.MONTH_PASS;
        p.purchaseState = Integer.valueOf(0);
        p.consumptionState = Integer.valueOf(0);
        p.acknowledgementState = Integer.valueOf(0);
        p.orderId = "GPA.m1";
        p.obfuscatedAccountId = ids.create(PID);
        api.products.put("m1", p);
        final Throwable[] error = new Throwable[1];
        Thread play = new Thread(new Runnable() {
            public void run() {
                try {
                    service.syncProduct(PlayProducts.MONTH_PASS, "m1", Long.valueOf(PID));
                } catch (Throwable e) {
                    error[0] = e;
                }
            }
        });
        // the App Store service holds the pid's lock: Play must wait for it, not use a private one
        synchronized (EntitlementLocks.lockFor(PID)) {
            play.start();
            play.join(300);
            assertTrue("Play grant ran while the shared lock was held", play.isAlive());
            assertNull(subscribers.findByTransactionId("GPA.m1"));
        }
        play.join(5000);
        assertNull(error[0]);
        assertEquals(PlayProducts.MONTH_LEVEL, subscribers.findByTransactionId("GPA.m1").level);
    }

    public void testRestackPullsAPassBackToTheRemainingEntitlement() throws Exception {
        FakeSubscriberStore subscribers = new FakeSubscriberStore();
        // a yearly row ending in 100 days, and a month pass stacked after it
        long yearlyPayment = now + 100 * DAY - PlayProducts.PAYMENTDATE_OFFSET_MS;
        subscribers.insert(new SubscriberRow(PID, "Y1", PlayProducts.YEARLY_LEVEL, yearlyPayment));
        long yearlyEnd = SubscriberRule.impliedExpiryMs(PlayProducts.YEARLY_LEVEL, yearlyPayment).longValue();
        subscribers.insert(new SubscriberRow(PID, "M1", PlayProducts.MONTH_LEVEL, yearlyEnd));

        // the yearly row is about to be zeroed: the pass moves back to now
        MonthPassStack.restack(subscribers, PID, "Y1", 0, yearlyPayment, now);

        assertEquals(now, subscribers.findByTransactionId("M1").paymentDateMs);
        assertEquals("restack never touches the changed row itself", PlayProducts.YEARLY_LEVEL,
                subscribers.findByTransactionId("Y1").level);
    }

    public void testRestackIsIdempotent() throws Exception {
        FakeSubscriberStore subscribers = new FakeSubscriberStore();
        subscribers.insert(new SubscriberRow(PID, "M1", PlayProducts.MONTH_LEVEL, now + 10 * DAY));
        MonthPassStack.restack(subscribers, PID, "none", 0, now, now);
        long once = subscribers.findByTransactionId("M1").paymentDateMs;
        MonthPassStack.restack(subscribers, PID, "none", 0, now, now);
        assertEquals(once, subscribers.findByTransactionId("M1").paymentDateMs);
        assertEquals(now, once);
    }

    public void testGrantAlertNamesTheStore() {
        assertEquals("Google Play month_pass (new) for rainwolf",
                DsgPlayGateway.grantAlert("Google Play", "month_pass", PlayClassification.NEW, "rainwolf"));
        assertEquals("App Store 1YRNOADSORLIMITS (renewal) for rainwolf",
                DsgPlayGateway.grantAlert("App Store", "1YRNOADSORLIMITS", PlayClassification.RENEWAL, "rainwolf"));
    }

    public void testDaysLeftIsWholeDays() {
        assertEquals(0, DsgPlayGateway.daysLeft(null));
        TBVacation v = new TBVacation();
        v.setHoursLeft(240);
        assertEquals(10, DsgPlayGateway.daysLeft(v));
        v.setHoursLeft(263);
        assertEquals(10, DsgPlayGateway.daysLeft(v));
        v.setHoursLeft(-5);
        assertEquals(0, DsgPlayGateway.daysLeft(v));
    }

    public void testFakeGatewayVacationDaysLeft() {
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        FakePlayerGateway players = new FakePlayerGateway(new FakeSubscriberStore(), clock);
        assertEquals(0, players.vacationDaysLeft(PID));
        players.daysLeft.put(Long.valueOf(PID), Integer.valueOf(7));
        assertEquals(7, players.vacationDaysLeft(PID));
    }

    public void testSetLevelPutsALevelBack() throws Exception {
        FakeSubscriberStore subscribers = new FakeSubscriberStore();
        subscribers.insert(new SubscriberRow(PID, "T1", PlayProducts.YEARLY_LEVEL, now));
        subscribers.setLevelZero("T1");
        subscribers.setLevel("T1", PlayProducts.YEARLY_LEVEL);
        assertEquals(PlayProducts.YEARLY_LEVEL, subscribers.findByTransactionId("T1").level);
        subscribers.setLevel("missing", 5);
        assertNull(subscribers.findByTransactionId("missing"));
    }
}
