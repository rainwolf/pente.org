package org.pente.subscriptions.apple.test;

import org.json.JSONObject;

import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleStoreInfo;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.SubscriberRow;

/**
 * GET /gameServer/appStorePurchase JSON (spec 2026-10-15 §4.4).
 *
 * Run: ant test-apple-billing
 */
public class AppleStoreInfoTest extends AppleServiceTestCase {

    public AppleStoreInfoTest(String name) {
        super(name);
    }

    public void testNewPlayer() throws Exception {
        JSONObject json = new JSONObject(service.storeInfo(PID).toJson());
        assertEquals(Long.valueOf(PID), tokens.parse(json.getString("appAccountToken")));
        assertTrue(json.isNull("subscribedUntil"));
        assertTrue(!json.getBoolean("hasActiveAppleSubscription"));
        assertTrue(json.getBoolean("vacationAllowed"));
        assertEquals(0, json.getInt("vacationDaysLeft"));
        assertEquals("[\"1YRNOADSORLIMITS\",\"month_pass\",\"vacation_10\",\"vacation_30\",\"vacation_60\"]",
                json.getJSONArray("products").toString());
    }

    public void testSubscriber() throws Exception {
        status(1, yearly(OTID, Long.valueOf(PID), now + 300 * DAY), null);
        service.syncSubscription(OTID, AppleEnvironment.PRODUCTION, null);
        players.daysLeft.put(Long.valueOf(PID), Integer.valueOf(12));
        AppleStoreInfo info = service.storeInfo(PID);
        assertEquals(expiration(PID), info.subscribedUntilMs);
        assertTrue(info.hasActiveAppleSubscription);
        assertEquals(12, info.vacationDaysLeft);
    }

    public void testExpiredAccessIsNull() throws Exception {
        subscribers.insert(new SubscriberRow(PID, "old", PlayProducts.YEARLY_LEVEL, now - 400 * DAY));
        assertNull(service.storeInfo(PID).subscribedUntilMs);
    }

    public void testLastRoundDisablesVacation() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        assertTrue(!new JSONObject(service.storeInfo(PID).toJson()).getBoolean("vacationAllowed"));
    }
}
