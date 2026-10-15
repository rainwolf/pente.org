package org.pente.subscriptions.apple.test;

import java.util.UUID;

import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStoreException;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * syncOneTime and notifyOneTime (spec 2026-10-15 §4.5, §4.6): the gate, GRANTING, grant, CONSUMED order, a crash
 * between steps, month-pass stacking, vacation days including during a last round, repeats and concurrency.
 *
 * Run: ant test-apple-billing
 */
public class AppleOneTimeSyncTest extends AppleServiceTestCase {

    public AppleOneTimeSyncTest(String name) {
        super(name);
    }

    public void testMonthPassIsGrantedOnceAndConsumed() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));

        assertEquals(PlayClassification.NEW, service.syncOneTime(pass, PID));

        SubscriberRow r = row("3000000000000001");
        assertEquals(PlayProducts.MONTH_LEVEL, r.level);
        assertEquals(now, r.paymentDateMs);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000001").state);
        assertEquals("[" + PID + ":month_pass:NEW]", players.grants.toString());
    }

    public void testMonthPassStacksAfterTheCurrentAccessWhateverItsLevel() throws Exception {
        subscribers.insert(new SubscriberRow(PID, "Y1", PlayProducts.YEARLY_LEVEL, now - 100 * DAY));
        long yearEnd = expiration(PID).longValue();
        service.syncOneTime(consumable("3000000000000001", AppleProducts.MONTH_PASS, null), PID);
        assertEquals(yearEnd, row("3000000000000001").paymentDateMs);
    }

    public void testTwoMonthPassesStack() throws Exception {
        service.syncOneTime(consumable("3000000000000001", AppleProducts.MONTH_PASS, null), PID);
        long firstEnd = expiration(PID).longValue();
        service.syncOneTime(consumable("3000000000000002", AppleProducts.MONTH_PASS, null), PID);
        assertEquals(firstEnd, row("3000000000000002").paymentDateMs);
    }

    public void testVacationAddsDaysOnceOnALevelZeroRow() throws Exception {
        assertEquals(PlayClassification.NEW,
                service.syncOneTime(consumable("3000000000000003", AppleProducts.VACATION_30, null), PID));
        assertEquals(30, players.vacationDaysOf(PID));
        assertEquals(0, row("3000000000000003").level);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000003").state);
    }

    public void testVacationDuringALastRoundIsStillGranted() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        assertEquals(PlayClassification.NEW,
                service.syncOneTime(consumable("3000000000000003", AppleProducts.VACATION_10, null), PID));
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testRepeatIsKnownAndGrantsNothingMore() throws Exception {
        AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_60, null);
        service.syncOneTime(t, PID);
        assertEquals(PlayClassification.KNOWN, service.syncOneTime(t, PID));
        assertEquals(60, players.vacationDaysOf(PID));
        assertEquals(1, players.grants.size());
    }

    public void testCrashAfterTheGateLeavesVacationInGranting() throws Exception {
        AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_30, null);
        store.failUpserts = 1;
        try {
            service.syncOneTime(t, PID);
            fail("expected the store failure");
        } catch (PlayStoreException expected) {
            // the client gets retry later
        }
        assertNotNull("the gate row is in", row("3000000000000003"));

        assertEquals(PlayClassification.KNOWN, service.syncOneTime(t, PID));

        assertEquals("never added twice; the admin checks by hand", 0, players.vacationDaysOf(PID));
        assertEquals(AppleStates.GRANTING, apple("3000000000000003").state);
    }

    public void testCrashAfterTheGateCompletesAMonthPass() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        store.failUpserts = 1;
        try {
            service.syncOneTime(t, PID);
            fail("expected the store failure");
        } catch (PlayStoreException expected) {
            // the client gets retry later
        }
        assertEquals(PlayClassification.KNOWN, service.syncOneTime(t, PID));
        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000001").state);
    }

    public void testCrashAfterTheDaysStaysGrantingAndIsNotRepeated() throws Exception {
        AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_30, null);
        store.failSetStates = 1;
        try {
            service.syncOneTime(t, PID);
            fail("expected the store failure");
        } catch (PlayStoreException expected) {
            // the client gets retry later
        }
        assertEquals(PlayClassification.KNOWN, service.syncOneTime(t, PID));
        assertEquals(30, players.vacationDaysOf(PID));
        assertEquals(AppleStates.GRANTING, apple("3000000000000003").state);
    }

    public void testConcurrentDeliveriesOfOneVacationGrantOnce() throws Exception {
        final AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_30, null);
        players.sleepMs = 50;
        Thread[] threads = new Thread[3];
        final Exception[] failure = new Exception[1];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        service.syncOneTime(t, PID);
                    } catch (Exception e) {
                        failure[0] = e;
                    }
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertNull(failure[0]);
        assertEquals(30, players.vacationDaysOf(PID));
    }

    public void testConcurrentMonthPassesForOnePidStack() throws Exception {
        players.sleepMs = 50;
        final String[] ids = {"3000000000000001", "3000000000000002"};
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            final String id = ids[i];
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        service.syncOneTime(consumable(id, AppleProducts.MONTH_PASS, null), PID);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long a = row(ids[0]).paymentDateMs;
        long b = row(ids[1]).paymentDateMs;
        assertTrue("one pass starts where the other ends", Math.abs(a - b) >= 28 * DAY);
    }

    public void testFamilySharedAndUnknownProductsAreInvalid() throws Exception {
        AppleTransaction shared = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        shared.familyShared = true;
        assertEquals(PlayClassification.INVALID, service.syncOneTime(shared, PID));
        assertEquals(PlayClassification.INVALID,
                service.syncOneTime(consumable("3000000000000002", AppleProducts.YEARLY, null), PID));
        assertEquals(PlayClassification.INVALID,
                service.syncOneTime(consumable("3000000000000003", "vacation_20", null), PID));
        assertTrue(subscribers.rows.isEmpty());
    }

    public void testSandboxNeedsTheAllowList() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        t.environment = AppleEnvironment.SANDBOX;
        assertEquals(PlayClassification.INVALID, service.syncOneTime(t, PID));
        assertTrue(subscribers.rows.isEmpty());
        sandboxAllowed.add(Long.valueOf(PID));
        assertEquals(PlayClassification.NEW, service.syncOneTime(t, PID));
        assertEquals(AppleEnvironment.SANDBOX, apple("3000000000000001").environment);
    }

    public void testGateOfAnotherPidIsShared() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        service.syncOneTime(t, OTHER_PID);
        assertEquals(PlayClassification.SHARED, service.syncOneTime(t, PID));
        assertEquals(OTHER_PID, row("3000000000000001").pid);
    }

    public void testRevokedTransactionIsNeverGranted() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        t.revocationDateMs = Long.valueOf(now - 1000);
        assertEquals(PlayClassification.INVALID, service.syncOneTime(t, PID));
        assertTrue(players.grants.isEmpty());
    }

    public void testOneTimeChargeWithTokenIsGranted() throws Exception {
        assertEquals(PlayClassification.NEW,
                service.notifyOneTime(consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID))));
        assertEquals(PID, row("3000000000000001").pid);
    }

    public void testOneTimeChargeWithoutTokenWaitsForThePost() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        assertEquals(PlayClassification.INACTIVE, service.notifyOneTime(t));
        assertTrue(subscribers.rows.isEmpty());
        service.syncOneTime(t, PID);
        assertEquals("after the POST the row names the owner", PlayClassification.KNOWN, service.notifyOneTime(t));
    }

    public void testOneTimeChargeWithForgedTokenIsInvalid() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        t.appAccountToken = UUID.randomUUID();
        assertEquals(PlayClassification.INVALID, service.notifyOneTime(t));
        assertTrue(subscribers.rows.isEmpty());
    }
}
