package org.pente.subscriptions.play;

import org.json.JSONObject;

/** Body of GET /gameServer/googlePlayPurchase (spec 2026-10-11 §3.4, §4.4). */
public class StoreInfo {

    public String accountId;
    public boolean vacationAllowed;
    /** getSubscriptionExpiration() when in the future, else null */
    public Long subscribedUntilMs;
    /** a yearly_subscription row in state ACTIVE or IN_GRACE_PERIOD */
    public boolean autoRenewing;

    /** {"accountId", "vacationAllowed", "subscribedUntil" (epoch ms or null), "autoRenewing"} */
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("accountId", accountId);
        json.put("vacationAllowed", vacationAllowed);
        json.put("subscribedUntil", subscribedUntilMs == null ? JSONObject.NULL : (Object) subscribedUntilMs);
        json.put("autoRenewing", autoRenewing);
        return json.toString();
    }
}
