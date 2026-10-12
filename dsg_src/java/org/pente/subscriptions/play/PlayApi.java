package org.pente.subscriptions.play;

/**
 * The only way this feature reaches the Google Play Developer API (spec 2026-10-11 §4.4).
 * GooglePlayApi is the one real implementation; tests use a fake.
 */
public interface PlayApi {

    /** purchases.subscriptionsv2.get; null when Google does not know the token (400/404/410) */
    PlaySubscription getSubscription(String purchaseToken) throws PlayApiException;

    /** purchases.subscriptions.acknowledge */
    void acknowledgeSubscription(String purchaseToken) throws PlayApiException;

    /** purchases.products.get; null when Google does not know the token (400/404/410) */
    PlayProduct getProduct(String productId, String purchaseToken) throws PlayApiException;

    /** purchases.products.consume */
    void consumeProduct(String productId, String purchaseToken) throws PlayApiException;

    /** orders.refund with revoke=true */
    void refundOrder(String orderId) throws PlayApiException;

    /** purchases.voidedpurchases.list with type=1 (subscriptions included); pageToken null for the first page */
    VoidedPage listVoided(long startTimeMs, String pageToken) throws PlayApiException;
}
