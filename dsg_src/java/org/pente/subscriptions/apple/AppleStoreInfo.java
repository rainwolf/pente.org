package org.pente.subscriptions.apple;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Body of GET /gameServer/appStorePurchase (spec 2026-10-15 §4.4). */
public class AppleStoreInfo {

    public String appAccountToken;
    /** getSubscriptionExpiration() when in the future, else null */
    public Long subscribedUntilMs;
    /** a 1YRNOADSORLIMITS dsg_subscribers_apple row in ACTIVE or GRACE that has not expired */
    public boolean hasActiveAppleSubscription;
    /** false during a turn-based tourney's last round */
    public boolean vacationAllowed;
    public int vacationDaysLeft;
    public List<String> products;

    /**
     * {"appAccountToken", "subscribedUntil" (epoch ms or null), "hasActiveAppleSubscription", "vacationAllowed",
     * "vacationDaysLeft", "products"}
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("appAccountToken", appAccountToken);
        json.put("subscribedUntil", subscribedUntilMs == null ? JSONObject.NULL : (Object) subscribedUntilMs);
        json.put("hasActiveAppleSubscription", hasActiveAppleSubscription);
        json.put("vacationAllowed", vacationAllowed);
        json.put("vacationDaysLeft", vacationDaysLeft);
        json.put("products", new JSONArray(products));
        return json.toString();
    }
}
