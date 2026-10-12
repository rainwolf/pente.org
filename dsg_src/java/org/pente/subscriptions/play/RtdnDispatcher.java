package org.pente.subscriptions.play;

import org.apache.log4j.Category;

/**
 * Routes an authenticated RTDN push body to PlayEntitlementService (spec 2026-10-11 §4.6).
 * Every notification is re-read from the Google API, so a forged body cannot grant anything.
 */
public class RtdnDispatcher {

    private static final Category log4j = Category.getInstance(RtdnDispatcher.class.getName());

    public static final int HANDLED = 200;
    /** transient Google API or DB error: Pub/Sub redelivers; duplicate deliveries are harmless */
    public static final int RETRY = 500;

    private final PlayEntitlementService service;
    private final String packageName;

    public RtdnDispatcher(PlayEntitlementService service, String packageName) {
        this.service = service;
        this.packageName = packageName;
    }

    /** @return the HTTP status for Pub/Sub: 200 when handled or ignored (unknown pid included), 500 to retry */
    public int dispatch(String body) {
        RtdnNotification n = RtdnNotification.parse(body);
        if (n == null) {
            log4j.warn("RtdnDispatcher: ignoring malformed push body");
            return HANDLED;
        }
        if (packageName == null || !packageName.equals(n.packageName)) {
            log4j.warn("RtdnDispatcher: ignoring notification for package " + n.packageName);
            return HANDLED;
        }
        try {
            switch (n.kind) {
                case SUBSCRIPTION:
                    if (n.purchaseToken != null) {
                        PlayClassification c = service.syncSubscription(n.purchaseToken, null);
                        log4j.info("RtdnDispatcher: subscription notification " + n.notificationType + " -> " + c);
                    }
                    break;
                case ONE_TIME_PRODUCT:
                    if (n.notificationType == RtdnNotification.ONE_TIME_PRODUCT_PURCHASED && n.purchaseToken != null) {
                        PlayClassification c = service.syncProduct(n.sku, n.purchaseToken, null);
                        log4j.info("RtdnDispatcher: one-time product " + n.sku + " -> " + c);
                    } else {
                        log4j.info("RtdnDispatcher: one-time product notification type " + n.notificationType + " ignored");
                    }
                    break;
                case VOIDED:
                    service.revoke(n.orderId);
                    log4j.info("RtdnDispatcher: voided order " + n.orderId + " revoked");
                    break;
                case TEST:
                    log4j.info("RtdnDispatcher: test notification received");
                    break;
                default:
                    log4j.info("RtdnDispatcher: unknown notification ignored");
            }
            return HANDLED;
        } catch (PlayApiException e) {
            log4j.warn("RtdnDispatcher: transient Google API error, Pub/Sub will retry: " + e);
            return RETRY;
        } catch (PlayStoreException e) {
            log4j.warn("RtdnDispatcher: transient DB error, Pub/Sub will retry: " + e);
            return RETRY;
        }
    }
}
