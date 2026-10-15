package org.pente.subscriptions.apple.test;

import java.util.function.LongSupplier;

import org.pente.subscriptions.apple.AppleApiException;
import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleHistoryReplay;
import org.pente.subscriptions.apple.AppleNotification;
import org.pente.subscriptions.apple.AppleNotificationHandler;
import org.pente.subscriptions.apple.AppleNotificationPage;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.play.PlayProducts;

/**
 * The daily history replay (spec 2026-10-15 §4.7): the high-water mark advances only on success, seen UUIDs are
 * skipped, pages are followed, and the start stays inside Apple's retention window.
 *
 * Run: ant test-apple-billing
 */
public class AppleHistoryReplayTest extends AppleServiceTestCase {

    private static final long HOUR = 3600L * 1000;
    private AppleHistoryReplay replay;

    public AppleHistoryReplayTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        LongSupplier clock = new LongSupplier() {
            public long getAsLong() {
                return now;
            }
        };
        AppleNotificationHandler handler = new AppleNotificationHandler(service, verifier, store, clock);
        replay = new AppleHistoryReplay(api, verifier, store, handler, clock);
    }

    /** a page of env's history at paginationToken (null: first page), holding these verified payloads */
    private AppleNotificationPage page(AppleEnvironment env, String token, String next, String... payloads) {
        AppleNotificationPage p = new AppleNotificationPage();
        for (String payload : payloads) {
            p.signedPayloads.add(payload);
        }
        p.hasMore = next != null;
        p.paginationToken = next;
        api.pages.put(env + ":" + (token == null ? "" : token), p);
        return p;
    }

    private String charge(String uuid, String transactionId, AppleEnvironment env) {
        AppleTransaction t = consumable(transactionId, AppleProducts.VACATION_10, Long.valueOf(PID));
        t.environment = env;
        AppleNotification n = new AppleNotification();
        n.notificationUuid = uuid;
        n.type = "ONE_TIME_CHARGE";
        n.environment = env;
        n.transaction = t;
        verifier.notifications.put("payload-" + uuid, n);
        return "payload-" + uuid;
    }

    public void testFirstRunStartsThreeDaysBackAndAdvances() throws Exception {
        page(AppleEnvironment.PRODUCTION, null, null, charge("u1", "3000000000000001", AppleEnvironment.PRODUCTION));

        assertTrue(replay.replay(AppleEnvironment.PRODUCTION));

        assertEquals(now - 3 * DAY, api.historyWindows.get(0)[0]);
        assertEquals(now, api.historyWindows.get(0)[1]);
        assertEquals(Long.valueOf(now), store.historyUntil(AppleEnvironment.PRODUCTION));
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testNextRunOverlapsOneHourAndSkipsSeenUuids() throws Exception {
        String payload = charge("u1", "3000000000000001", AppleEnvironment.PRODUCTION);
        page(AppleEnvironment.PRODUCTION, null, null, payload);
        replay.replay(AppleEnvironment.PRODUCTION);
        long mark = now;
        now += DAY;

        assertTrue(replay.replay(AppleEnvironment.PRODUCTION));

        assertEquals(mark - HOUR, api.historyWindows.get(1)[0]);
        assertEquals(10, players.vacationDaysOf(PID));
        assertEquals(1, store.notifications.size());
    }

    public void testFollowsEveryPage() throws Exception {
        page(AppleEnvironment.SANDBOX, null, "p2", charge("u1", "3000000000000001", AppleEnvironment.SANDBOX));
        page(AppleEnvironment.SANDBOX, "p2", null, charge("u2", "3000000000000002", AppleEnvironment.SANDBOX));
        sandboxAllowed.add(Long.valueOf(PID));
        assertTrue(replay.replay(AppleEnvironment.SANDBOX));
        assertEquals(20, players.vacationDaysOf(PID));
        assertEquals(2, api.count("notificationHistory:"));
    }

    public void testATransientFailureKeepsTheMarkForTomorrow() throws Exception {
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now - DAY);
        page(AppleEnvironment.PRODUCTION, null, null, charge("u1", "3000000000000001", AppleEnvironment.PRODUCTION));
        store.failRecords = 1;

        assertTrue(!replay.replay(AppleEnvironment.PRODUCTION));
        assertEquals(Long.valueOf(now - DAY), store.historyUntil(AppleEnvironment.PRODUCTION));

        now += DAY;
        assertTrue(replay.replay(AppleEnvironment.PRODUCTION));
        assertEquals("the retry starts from the same mark", now - 2 * DAY - HOUR, api.historyWindows.get(1)[0]);
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testAFinalFailureStillAdvancesTheMark() throws Exception {
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now - DAY);
        // a REFUND of a transaction Apple answers 404 for: no retry can ever fix it
        AppleNotification n = new AppleNotification();
        n.notificationUuid = "u0";
        n.type = "REFUND";
        n.environment = AppleEnvironment.PRODUCTION;
        n.transaction = consumable("3000000000000009", AppleProducts.MONTH_PASS, Long.valueOf(PID));
        verifier.notifications.put("payload-u0", n);
        page(AppleEnvironment.PRODUCTION, null, null, "payload-u0",
                charge("u1", "3000000000000001", AppleEnvironment.PRODUCTION));

        assertTrue(replay.replay(AppleEnvironment.PRODUCTION));

        assertEquals(Long.valueOf(now), store.historyUntil(AppleEnvironment.PRODUCTION));
        assertEquals(10, players.vacationDaysOf(PID));
        assertEquals(1, players.alerts.size());
        assertTrue(players.alerts.get(0).contains("u0"));
    }

    public void testAnApiFailureKeepsTheMark() throws Exception {
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now - DAY);
        api.failNotificationHistory = 1;
        try {
            replay.replay(AppleEnvironment.PRODUCTION);
            fail("expected AppleApiException");
        } catch (AppleApiException expected) {
            assertTrue(expected.isRetryable());
        }
        assertEquals(Long.valueOf(now - DAY), store.historyUntil(AppleEnvironment.PRODUCTION));
    }

    public void testAForgedPayloadIsSkipped() throws Exception {
        page(AppleEnvironment.PRODUCTION, null, null, "forged", charge("u1", "3000000000000001", AppleEnvironment.PRODUCTION));
        assertTrue(replay.replay(AppleEnvironment.PRODUCTION));
        assertEquals(10, players.vacationDaysOf(PID));
    }

    public void testTheStartStaysInsideTheRetentionWindow() throws Exception {
        store.setHistoryUntil(AppleEnvironment.SANDBOX, now - 40 * DAY);
        store.setHistoryUntil(AppleEnvironment.PRODUCTION, now - 200 * DAY);
        replay.replay(AppleEnvironment.SANDBOX);
        replay.replay(AppleEnvironment.PRODUCTION);
        assertEquals(now - 30 * PlayProducts.DAY_MS + HOUR, api.historyWindows.get(0)[0]);
        assertEquals(now - 180 * PlayProducts.DAY_MS + HOUR, api.historyWindows.get(1)[0]);
    }
}
