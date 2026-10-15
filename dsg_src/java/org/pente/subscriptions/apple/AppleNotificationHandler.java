package org.pente.subscriptions.apple;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

import org.apache.log4j.Category;
import org.json.JSONException;
import org.json.JSONObject;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayStoreException;

/**
 * App Store Server Notifications V2 (spec 2026-10-15 §4.6), for AppStoreNotificationServlet and the daily history
 * replay. Returns the HTTP status to answer: 200 handled (or ignored), 401 not Apple's, 500 try again later.
 * Dedupe is check, process, record: a live delivery and a replay can process one UUID at once, which is safe
 * because every action is gated (the gate inserts, the GRANTING order, the state-based refund refresh).
 */
public class AppleNotificationHandler {

    private static final Category log4j = Category.getInstance(AppleNotificationHandler.class.getName());

    /** synced through syncSubscription(originalTransactionId) */
    static final Set<String> SUBSCRIPTION_TYPES = new HashSet<String>(Arrays.asList(
            "SUBSCRIBED", "DID_RENEW", "EXPIRED", "DID_FAIL_TO_RENEW", "GRACE_PERIOD_EXPIRED",
            "DID_CHANGE_RENEWAL_STATUS", "PRICE_INCREASE", "RENEWAL_EXTENDED", "OFFER_REDEEMED"));
    /** refreshRefundState(transactionId) */
    static final Set<String> REFUND_TYPES = new HashSet<String>(Arrays.asList("REFUND", "REVOKE", "REFUND_REVERSED"));

    private final AppleEntitlementService service;
    private final AppleVerifier verifier;
    private final AppleStore store;
    private final LongSupplier clock;

    public AppleNotificationHandler(AppleEntitlementService service, AppleVerifier verifier, AppleStore store,
                                    LongSupplier clock) {
        this.service = service;
        this.verifier = verifier;
        this.store = store;
        this.clock = clock;
    }

    /** the POST body {"signedPayload": "..."}: 401 unless it verifies, else process's status */
    public int handle(String body) {
        String signedPayload;
        try {
            signedPayload = new JSONObject(body).optString("signedPayload", null);
        } catch (JSONException e) {
            signedPayload = null;
        }
        if (signedPayload == null || signedPayload.isEmpty()) {
            log4j.warn("AppleNotificationHandler: body without signedPayload");
            return 401;
        }
        AppleNotification n;
        try {
            n = verifier.verifyNotification(signedPayload);
        } catch (AppleVerificationException e) {
            log4j.warn("AppleNotificationHandler: notification not verified (" + (e.isRetryable() ? "retryable" : "refused")
                    + "): " + e.getMessage());
            return e.isRetryable() ? 500 : 401;
        }
        return process(n);
    }

    /** a verified notification: 200 once handled (UUID recorded) or already seen, 500 on a transient failure */
    public int process(AppleNotification n) {
        if (n.notificationUuid == null || n.notificationUuid.isEmpty()) {
            log4j.warn("AppleNotificationHandler: " + n.type + " without notificationUUID, ignored");
            return 200;
        }
        try {
            if (store.notificationSeen(n.notificationUuid)) {
                return 200;
            }
            route(n);
            store.recordNotification(n.notificationUuid, n.type == null ? "UNKNOWN" : n.type, clock.getAsLong());
            return 200;
        } catch (AppleApiException e) {
            log4j.warn("AppleNotificationHandler: " + n.type + " " + n.notificationUuid + " failed, Apple retries: " + e);
            return 500;
        } catch (PlayStoreException e) {
            log4j.warn("AppleNotificationHandler: " + n.type + " " + n.notificationUuid + " failed, Apple retries: " + e);
            return 500;
        } catch (RuntimeException e) {
            log4j.error("AppleNotificationHandler: " + n.type + " " + n.notificationUuid + " failed, Apple retries", e);
            return 500;
        }
    }

    private void route(AppleNotification n) throws AppleApiException, PlayStoreException {
        String type = n.type;
        AppleTransaction tx = n.transaction;
        String what = type + (n.subtype == null ? "" : "/" + n.subtype) + " " + n.environment + " " + n.notificationUuid;
        if (SUBSCRIPTION_TYPES.contains(type)) {
            if (tx == null || !AppleProducts.isSubscription(tx.productId)) {
                log4j.info("AppleNotificationHandler: " + what + " not for 1YRNOADSORLIMITS, logged only");
                return;
            }
            if (tx.familyShared) {
                log4j.info("AppleNotificationHandler: " + what + " is Family Sharing, logged only");
                return;
            }
            PlayClassification result = service.syncSubscription(tx.originalTransactionId, n.environment, null);
            log4j.info("AppleNotificationHandler: " + what + " " + tx.originalTransactionId + " -> " + result);
        } else if ("ONE_TIME_CHARGE".equals(type)) {
            PlayClassification result = service.notifyOneTime(tx);
            log4j.info("AppleNotificationHandler: " + what + " -> " + result);
        } else if (REFUND_TYPES.contains(type)) {
            if (tx == null) {
                log4j.warn("AppleNotificationHandler: " + what + " without a transaction, logged only");
                return;
            }
            service.refreshRefundState(tx.transactionId, n.environment);
            log4j.info("AppleNotificationHandler: " + what + " refund state of " + tx.transactionId + " refreshed");
        } else {
            // CONSUMPTION_REQUEST (no consent flow, never answered), REFUND_DECLINED, TEST, PRICE_CHANGE,
            // METADATA_UPDATE, MIGRATION, RENEWAL_EXTENSION summaries and anything new
            log4j.info("AppleNotificationHandler: " + what + " logged only");
        }
    }
}
