package org.pente.subscriptions.apple.test;

import java.util.function.LongSupplier;

import org.pente.subscriptions.apple.AppleBilling;
import org.pente.subscriptions.apple.AppleEntitlementService;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleHistoryReplay;
import org.pente.subscriptions.apple.AppleNotificationHandler;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleSandboxGate;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.test.FakePlayerGateway;

/**
 * The 6-hour sweep and the daily alerts (spec 2026-10-15 §4.7), and AppleBilling's never-throwing timer entry points.
 *
 * Run: ant test-apple-billing
 */
public class ApplePollerTest extends AppleServiceTestCase {

    public ApplePollerTest(String name) {
        super(name);
    }

    public void testSweepRenewsAnExpiringSubscriptionWithoutTheApp() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + DAY), null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
        now += 2 * DAY;
        status(1, yearly("2000000100000002", Long.valueOf(PID), now + 363 * DAY), null);

        service.pollSubscriptions();

        assertEquals(PlayProducts.YEARLY_LEVEL, row("2000000100000002").level);
    }

    public void testSweepSkipsRowsThatAreFarFromExpiryAndUnchanged() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + 300 * DAY), null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
        now += 40 * DAY;
        int calls = api.calls.size();
        service.pollSubscriptions();
        assertEquals(calls, api.calls.size());
    }

    public void testSweepCatchesARefundOfARecentlyActiveRow() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
        now += 10 * DAY;
        AppleTransaction refunded = year.copy();
        refunded.revocationDateMs = Long.valueOf(now - 1000);
        status(5, refunded, null);

        service.pollSubscriptions();

        assertEquals(0, row(OTID).level);
        assertEquals(AppleStates.REVOKED, apple(OTID).state);
    }

    public void testOneFailingRowDoesNotStopTheOthers() throws Exception {
        AppleRow first = new AppleRow();
        first.originalTransactionId = "2000000500000001";
        first.pid = OTHER_PID;
        first.productId = AppleProducts.YEARLY;
        first.state = AppleStates.ACTIVE;
        first.expiryTimeMs = Long.valueOf(now + DAY);
        first.environment = AppleEnvironment.PRODUCTION;
        store.upsert(first);
        status(1, yearly(OTID, Long.valueOf(PID), now + DAY), null);
        AppleRow second = first.copy();
        second.originalTransactionId = OTID;
        second.pid = PID;
        store.upsert(second);

        service.pollSubscriptions();

        assertNull("unknown to Apple: logged", row("2000000500000001"));
        assertEquals(PlayProducts.YEARLY_LEVEL, row(OTID).level);
    }

    public void testSweepUsesTheRowsEnvironment() throws Exception {
        sandboxAllowed.add(Long.valueOf(PID));
        AppleTransaction t = yearly(OTID, Long.valueOf(PID), now + DAY);
        t.environment = AppleEnvironment.SANDBOX;
        status(1, t, null);
        service.syncSubscription(OTID, AppleEnvironment.SANDBOX, null);
        api.calls.clear();
        service.pollSubscriptions();
        assertEquals("statuses:" + OTID + ":SANDBOX", api.calls.get(0));
    }

    public void testDailyAlerts() throws Exception {
        AppleRow granting = new AppleRow();
        granting.originalTransactionId = "3000000000000003";
        granting.pid = PID;
        granting.productId = AppleProducts.VACATION_30;
        granting.state = AppleStates.GRANTING;
        granting.environment = AppleEnvironment.PRODUCTION;
        store.upsert(granting);
        AppleRow stale = granting.copy();
        stale.originalTransactionId = OTID;
        stale.productId = AppleProducts.YEARLY;
        stale.state = AppleStates.ACTIVE;
        stale.expiryTimeMs = Long.valueOf(now - 3 * DAY);
        store.upsert(stale);
        AppleRow fresh = stale.copy();
        fresh.originalTransactionId = "2000000100000009";
        fresh.expiryTimeMs = Long.valueOf(now - DAY);
        store.upsert(fresh);

        service.dailyAlerts();
        assertEquals("GRANTING is only stale after an hour; one stale ACTIVE row", 1, players.alerts.size());

        now += 2 * 3600L * 1000;
        players.alerts.clear();
        service.dailyAlerts();
        assertEquals(2, players.alerts.size());
        assertTrue(players.alerts.get(0), players.alerts.get(0).contains("stuck in GRANTING"));
        assertTrue(players.alerts.get(1), players.alerts.get(1).contains(OTID));
    }

    public void testDailyAlertsOneFailingRowDoesNotStopTheOthers() throws Exception {
        final String bad = "3000000000000003";
        AppleRow first = new AppleRow();
        first.originalTransactionId = bad;
        first.pid = PID;
        first.productId = AppleProducts.VACATION_30;
        first.state = AppleStates.GRANTING;
        first.environment = AppleEnvironment.PRODUCTION;
        store.upsert(first);
        AppleRow second = first.copy();
        second.originalTransactionId = "3000000000000007";
        store.upsert(second);
        AppleRow stale = first.copy();
        stale.originalTransactionId = OTID;
        stale.productId = AppleProducts.YEARLY;
        stale.state = AppleStates.ACTIVE;
        stale.expiryTimeMs = Long.valueOf(now - 3 * DAY);
        store.upsert(stale);
        now += 2 * 3600L * 1000;

        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        FakePlayerGateway failing = new FakePlayerGateway(subscribers, clock) {
            public synchronized void adminAlert(String message) {
                if (message.contains(bad)) {
                    throw new IllegalStateException("alert channel down");
                }
                super.adminAlert(message);
            }
        };
        AppleEntitlementService s = new AppleEntitlementService(api, verifier, store, subscribers, failing, tokens,
                new AppleSandboxGate() {
                    public boolean allows(long pid) {
                        return false;
                    }
                }, clock);

        s.dailyAlerts();

        assertEquals(failing.alerts.toString(), 2, failing.alerts.size());
        assertTrue(failing.alerts.get(0), failing.alerts.get(0).contains("3000000000000007"));
        assertTrue(failing.alerts.get(1), failing.alerts.get(1).contains(OTID));
    }

    public void testBillingDailyRunsEveryStepWhenOneFails() throws Exception {
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        AppleNotificationHandler handler = new AppleNotificationHandler(service, verifier, store, clock);
        AppleBilling billing = new AppleBilling(service, handler, new AppleHistoryReplay(api, verifier, store, handler, clock));
        api.failNotificationHistory = 1;
        AppleRow granting = new AppleRow();
        granting.originalTransactionId = "3000000000000003";
        granting.pid = PID;
        granting.productId = AppleProducts.VACATION_30;
        granting.state = AppleStates.GRANTING;
        granting.environment = AppleEnvironment.PRODUCTION;
        store.upsert(granting);
        now += 2 * 3600L * 1000;

        billing.daily();
        billing.sweep();

        assertNull("production failed", store.historyUntil(AppleEnvironment.PRODUCTION));
        assertEquals("sandbox still ran", Long.valueOf(now), store.historyUntil(AppleEnvironment.SANDBOX));
        assertEquals("alerts still ran", 1, players.alerts.size());
    }

    /** controller ruling (Task 8): a month pass is granted by its gate row, so a stuck GRANTING row is promoted */
    public void testDailyAlertsPromotesAGrantedMonthPassInsteadOfAlerting() throws Exception {
        AppleRow pass = new AppleRow();
        pass.originalTransactionId = "3000000000000004";
        pass.pid = PID;
        pass.productId = AppleProducts.MONTH_PASS;
        pass.latestTransactionId = pass.originalTransactionId;
        pass.state = AppleStates.GRANTING;
        pass.environment = AppleEnvironment.PRODUCTION;
        store.upsert(pass);
        subscribers.insert(new SubscriberRow(PID, pass.originalTransactionId, PlayProducts.MONTH_LEVEL, now));
        now += 2 * 3600L * 1000;

        service.dailyAlerts();

        assertEquals(0, players.alerts.size());
        assertEquals(AppleStates.CONSUMED, apple(pass.originalTransactionId).state);
    }

    /** controller ruling (Task 8): vacation days may not have been added, so a stuck vacation row still alerts */
    public void testDailyAlertsStillAlertsOnAStuckVacationWithAGateRow() throws Exception {
        AppleRow vacation = new AppleRow();
        vacation.originalTransactionId = "3000000000000005";
        vacation.pid = PID;
        vacation.productId = AppleProducts.VACATION_10;
        vacation.latestTransactionId = vacation.originalTransactionId;
        vacation.state = AppleStates.GRANTING;
        vacation.environment = AppleEnvironment.PRODUCTION;
        store.upsert(vacation);
        subscribers.insert(new SubscriberRow(PID, vacation.originalTransactionId,
                AppleProducts.level(AppleProducts.VACATION_10), now));
        now += 2 * 3600L * 1000;

        service.dailyAlerts();

        assertEquals(1, players.alerts.size());
        assertTrue(players.alerts.get(0), players.alerts.get(0).contains("stuck in GRANTING"));
        assertEquals(AppleStates.GRANTING, apple(vacation.originalTransactionId).state);
    }
}
