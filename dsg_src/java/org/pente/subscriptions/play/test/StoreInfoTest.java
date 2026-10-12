package org.pente.subscriptions.play.test;

import org.json.JSONObject;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;
import org.pente.subscriptions.play.StoreInfo;

/**
 * GET /gameServer/googlePlayPurchase body: accountId, vacationAllowed, subscribedUntil, autoRenewing
 * (spec 2026-10-11 §3.4, §4.4).
 *
 * Run: ant test-play-billing
 */
public class StoreInfoTest extends PlayServiceTestCase {

    public StoreInfoTest(String name) {
        super(name);
    }

    public void testNewPlayer() throws Exception {
        StoreInfo info = service.storeInfo(PID);
        assertEquals(accountIds.create(PID), info.accountId);
        assertEquals(service.accountId(PID), info.accountId);
        assertTrue(info.vacationAllowed);
        assertNull(info.subscribedUntilMs);
        assertTrue(!info.autoRenewing);
    }

    public void testActiveYearlyIsAutoRenewing() throws Exception {
        subscription("y", PID, PlayStates.ACTIVE, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        StoreInfo info = service.storeInfo(PID);
        assertTrue(info.autoRenewing);
        assertEquals(expiration(PID), info.subscribedUntilMs);
    }

    public void testCanceledYearlyIsNotAutoRenewing() throws Exception {
        subscription("y", PID, PlayStates.CANCELED, "GPA.y", now + 300 * DAY);
        service.syncSubscription("y", null);
        StoreInfo info = service.storeInfo(PID);
        assertTrue(!info.autoRenewing);
        assertNotNull(info.subscribedUntilMs);
    }

    public void testLapsedEntitlementIsNotShown() throws Exception {
        product("m", PID, PlayProducts.MONTH_PASS, "GPA.m");
        service.syncProduct(PlayProducts.MONTH_PASS, "m", null);
        now += 40 * DAY;
        assertNull(service.storeInfo(PID).subscribedUntilMs);
    }

    public void testVacationNotAllowedInTourney() throws Exception {
        players.inTourney.add(Long.valueOf(PID));
        assertTrue(!service.storeInfo(PID).vacationAllowed);
    }

    public void testJson() throws Exception {
        StoreInfo info = new StoreInfo();
        info.accountId = "1.abc";
        info.vacationAllowed = true;
        info.subscribedUntilMs = null;
        info.autoRenewing = false;
        JSONObject json = new JSONObject(info.toJson());
        assertEquals("1.abc", json.getString("accountId"));
        assertTrue(json.getBoolean("vacationAllowed"));
        assertTrue(json.has("subscribedUntil"));
        assertTrue(json.isNull("subscribedUntil"));
        assertTrue(!json.getBoolean("autoRenewing"));

        info.subscribedUntilMs = Long.valueOf(1790000000000L);
        json = new JSONObject(info.toJson());
        assertEquals(1790000000000L, json.getLong("subscribedUntil"));
    }
}
