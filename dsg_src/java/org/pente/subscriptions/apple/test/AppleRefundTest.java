package org.pente.subscriptions.apple.test;

import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * revoke with tombstones and refreshRefundState (spec 2026-10-15 §4.5): a refunded purchase can never be granted
 * later, a refund and its reversal give Apple's final state in either order, and repeats change nothing.
 *
 * Run: ant test-apple-billing
 */
public class AppleRefundTest extends AppleServiceTestCase {

    public AppleRefundTest(String name) {
        super(name);
    }

    private AppleTransaction refunded(AppleTransaction t) {
        AppleTransaction r = t.copy();
        r.revocationDateMs = Long.valueOf(now - 1000);
        api.transactions.put(r.transactionId, r);
        return r;
    }

    private void reversed(AppleTransaction t) {
        AppleTransaction r = t.copy();
        r.revocationDateMs = null;
        api.transactions.put(r.transactionId, r);
    }

    public void testTombstoneWhenNoRowExistsBlocksLaterGrants() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));

        service.revoke(refunded(pass), Long.valueOf(PID));

        SubscriberRow tombstone = row("3000000000000001");
        assertEquals(PID, tombstone.pid);
        assertEquals(0, tombstone.level);
        assertEquals(AppleStates.REFUNDED, apple("3000000000000001").state);
        assertEquals("a later grant is refused", PlayClassification.INVALID, service.syncOneTime(pass, PID));
        assertEquals(0, row("3000000000000001").level);
        assertTrue(players.grants.isEmpty());
        // MySQLDSGPlayerStorer.hasiOSTransactionId is "SELECT * FROM dsg_subscribers WHERE transactionid = ?"
        assertNotNull("the legacy poller sees the transaction id", subscribers.findByTransactionId("3000000000000001"));
    }

    public void testTombstoneWithoutAnOwnerUsesPidZero() throws Exception {
        service.revoke(refunded(consumable("3000000000000001", AppleProducts.VACATION_10, null)), null);
        assertEquals(0L, row("3000000000000001").pid);
        assertEquals(PlayClassification.INVALID,
                service.syncOneTime(consumable("3000000000000001", AppleProducts.VACATION_10, null), PID));
        assertEquals(0, players.vacationDaysOf(PID));
    }

    public void testSubscriptionTombstone() throws Exception {
        AppleTransaction year = yearly(OTID, null, now + 300 * DAY);
        service.revoke(refunded(year), null);
        assertEquals(0, row(OTID).level);
        assertEquals(AppleStates.REVOKED, apple(OTID).state);
        assertEquals(OTID, apple(OTID).latestTransactionId);
    }

    public void testRevokedMonthPassPullsTheLaterPassBackOnce() throws Exception {
        AppleTransaction first = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        AppleTransaction second = consumable("3000000000000002", AppleProducts.MONTH_PASS, null);
        service.syncOneTime(first, PID);
        service.syncOneTime(second, PID);
        long secondStart = row("3000000000000002").paymentDateMs;

        service.revoke(refunded(first), Long.valueOf(PID));
        service.revoke(refunded(first), Long.valueOf(PID));

        assertEquals(0, row("3000000000000001").level);
        assertEquals(now, row("3000000000000002").paymentDateMs);
        assertTrue(secondStart > now);
        assertEquals(AppleStates.REFUNDED, apple("3000000000000001").state);
        assertEquals(1, players.changed.size());
    }

    public void testRevokedVacationKeepsItsDays() throws Exception {
        AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_30, null);
        service.syncOneTime(t, PID);
        service.revoke(refunded(t), Long.valueOf(PID));
        assertEquals(30, players.vacationDaysOf(PID));
        assertEquals(AppleStates.REFUNDED, apple("3000000000000003").state);
    }

    public void testOlderYearRefundLeavesTheChainState() throws Exception {
        subscribers.insert(new SubscriberRow(PID, OTID, PlayProducts.YEARLY_LEVEL, now - 400 * DAY));
        status(1, yearly("2000000100000002", Long.valueOf(PID), now + 300 * DAY), null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);

        service.revoke(refunded(yearly(OTID, null, now - 35 * DAY)), null);

        assertEquals(0, row(OTID).level);
        assertEquals(AppleStates.ACTIVE, apple(OTID).state);
        assertEquals(PlayProducts.YEARLY_LEVEL, row("2000000100000002").level);
    }

    public void testRefundThenReversalOfAMonthPass() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        service.syncOneTime(pass, PID);

        refunded(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(0, row("3000000000000001").level);
        assertEquals(AppleStates.REFUNDED, apple("3000000000000001").state);

        reversed(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000001").state);
    }

    public void testNotificationsInEitherOrderGiveApplesFinalState() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        service.syncOneTime(pass, PID);
        // Apple already reversed the refund when the late REFUND and the REFUND_REVERSED arrive, in that order
        reversed(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000001").state);
        // and the other way round: the refund stands when both arrive
        refunded(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(0, row("3000000000000001").level);
        assertEquals(AppleStates.REFUNDED, apple("3000000000000001").state);
    }

    public void testSubscriptionRefundThenReversalTrustsRevocationDate() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);

        status(5, refunded(year), null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);
        assertEquals(0, row(OTID).level);
        assertEquals(AppleStates.REVOKED, apple(OTID).state);

        // Apple reversed the refund but still reports status 5 for this active subscription
        AppleTransaction back = year.copy();
        status(5, back, null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);
        assertEquals(PlayProducts.YEARLY_LEVEL, row(OTID).level);
        assertEquals(year.expiresDateMs.longValue() - YEAR_OFFSET, row(OTID).paymentDateMs);
        assertEquals(AppleStates.ACTIVE, apple(OTID).state);
    }

    public void testReversalOfATombstonedPurchaseGrantsIt() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        refunded(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(0, row("3000000000000001").level);

        reversed(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);

        assertEquals(PlayProducts.MONTH_LEVEL, row("3000000000000001").level);
        assertEquals(AppleStates.CONSUMED, apple("3000000000000001").state);
        assertEquals("[" + PID + ":month_pass:NEW]", players.grants.toString());
        assertEquals(PlayClassification.KNOWN, service.syncOneTime(pass, PID));
    }

    public void testReversalOfATombstonedVacationAddsTheDaysOnce() throws Exception {
        AppleTransaction t = consumable("3000000000000003", AppleProducts.VACATION_10, Long.valueOf(PID));
        refunded(t);
        service.refreshRefundState("3000000000000003", AppleEnvironment.PRODUCTION);
        reversed(t);
        service.refreshRefundState("3000000000000003", AppleEnvironment.PRODUCTION);
        service.refreshRefundState("3000000000000003", AppleEnvironment.PRODUCTION);
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testReversalOfAnOwnerlessTombstoneAlerts() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, null);
        refunded(t);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        reversed(t);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertEquals(0, row("3000000000000001").level);
        assertEquals(1, players.alerts.size());
        assertTrue(players.grants.isEmpty());
    }

    public void testSandboxRefundOfAnUnlistedOwnerIsIgnored() throws Exception {
        AppleTransaction t = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        t.environment = AppleEnvironment.SANDBOX;
        refunded(t);
        service.refreshRefundState("3000000000000001", AppleEnvironment.SANDBOX);
        assertNull(row("3000000000000001"));
        assertEquals("transactionInfo:3000000000000001:SANDBOX", api.calls.get(0));
    }

    public void testConcurrentReversalsGrantOnce() throws Exception {
        AppleTransaction pass = consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        refunded(pass);
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        reversed(pass);
        // an Apple retry of REFUND_REVERSED overlapping the daily replay
        players.sleepMs = 50;
        Thread[] threads = new Thread[2];
        final Exception[] failure = new Exception[1];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
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
        players.sleepMs = 0;
        assertNull(failure[0]);
        assertEquals("[" + PID + ":month_pass:NEW]", players.grants.toString());
        assertEquals("not stacked after itself", now, row("3000000000000001").paymentDateMs);
    }

    public void testSubscriptionTombstoneReversalUsesApplesExpiry() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(5, refunded(year), null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);
        assertEquals(0, row(OTID).level);
        assertEquals(PID, row(OTID).pid);

        status(5, year.copy(), null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);

        assertEquals(PlayProducts.YEARLY_LEVEL, row(OTID).level);
        assertEquals("Apple's year, not a year from the refund", year.expiresDateMs.longValue() - YEAR_OFFSET,
                row(OTID).paymentDateMs);
        assertEquals(AppleStates.ACTIVE, apple(OTID).state);
    }

    public void testYearlyReversalRestacksTheMonthPass() throws Exception {
        AppleTransaction year = yearly(OTID, Long.valueOf(PID), now + 300 * DAY);
        status(1, year, null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
        service.syncOneTime(consumable("3000000000000001", AppleProducts.MONTH_PASS, Long.valueOf(PID)), PID);
        long passStart = row("3000000000000001").paymentDateMs;
        Long before = expiration(PID);
        assertTrue(passStart > now);

        status(5, refunded(year), null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);
        assertEquals("the refund pulls the pass back", now, row("3000000000000001").paymentDateMs);

        status(5, year.copy(), null);
        service.refreshRefundState(OTID, AppleEnvironment.PRODUCTION);

        assertEquals("the reversal puts the pass after the year again", passStart,
                row("3000000000000001").paymentDateMs);
        assertEquals(before, expiration(PID));
    }

    public void testFamilySharedRefundWritesNothing() throws Exception {
        AppleTransaction r = refunded(consumable("3000000000000001", AppleProducts.MONTH_PASS, null));
        r.familyShared = true;
        service.refreshRefundState("3000000000000001", AppleEnvironment.PRODUCTION);
        assertNull(row("3000000000000001"));
        assertNull(apple("3000000000000001"));
    }
}
