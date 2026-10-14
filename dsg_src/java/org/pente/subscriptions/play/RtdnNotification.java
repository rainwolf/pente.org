package org.pente.subscriptions.play;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A Play real-time developer notification decoded from a Pub/Sub push body
 * {"message": {"data": base64(DeveloperNotification), "messageId": ...}, "subscription": ...}.
 */
public class RtdnNotification {

    public enum Kind {SUBSCRIPTION, ONE_TIME_PRODUCT, VOIDED, TEST, UNKNOWN}

    /** OneTimeProductNotification.notificationType for a completed purchase */
    public static final int ONE_TIME_PRODUCT_PURCHASED = 1;

    public Kind kind = Kind.UNKNOWN;
    public String packageName;
    public int notificationType;
    public String purchaseToken;
    /** oneTimeProductNotification.sku */
    public String sku;
    /** voidedPurchaseNotification.orderId */
    public String orderId;

    /** @return the notification, or null when the body is not a well-formed Pub/Sub envelope */
    public static RtdnNotification parse(String body) {
        if (body == null) {
            return null;
        }
        try {
            JSONObject envelope = new JSONObject(body);
            JSONObject message = envelope.optJSONObject("message");
            if (message == null) {
                return null;
            }
            String data = message.optString("data", null);
            if (data == null) {
                return null;
            }
            JSONObject dn = new JSONObject(new String(Base64.getDecoder().decode(data.trim()), StandardCharsets.UTF_8));
            RtdnNotification n = new RtdnNotification();
            n.packageName = dn.optString("packageName", null);
            JSONObject sub = dn.optJSONObject("subscriptionNotification");
            JSONObject oneTime = dn.optJSONObject("oneTimeProductNotification");
            JSONObject voided = dn.optJSONObject("voidedPurchaseNotification");
            if (sub != null) {
                n.kind = Kind.SUBSCRIPTION;
                n.notificationType = sub.optInt("notificationType", 0);
                n.purchaseToken = sub.optString("purchaseToken", null);
            } else if (oneTime != null) {
                n.kind = Kind.ONE_TIME_PRODUCT;
                n.notificationType = oneTime.optInt("notificationType", 0);
                n.purchaseToken = oneTime.optString("purchaseToken", null);
                n.sku = oneTime.optString("sku", null);
            } else if (voided != null) {
                n.kind = Kind.VOIDED;
                n.purchaseToken = voided.optString("purchaseToken", null);
                n.orderId = voided.optString("orderId", null);
            } else if (dn.has("testNotification")) {
                n.kind = Kind.TEST;
            }
            return n;
        } catch (JSONException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
