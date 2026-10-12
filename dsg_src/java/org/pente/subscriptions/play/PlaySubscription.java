package org.pente.subscriptions.play;

/** The fields of purchases.subscriptionsv2.get (SubscriptionPurchaseV2) this feature reads. */
public class PlaySubscription {

    /** subscriptionState, e.g. SUBSCRIPTION_STATE_ACTIVE */
    public String subscriptionState;
    /** acknowledgementState, e.g. ACKNOWLEDGEMENT_STATE_PENDING */
    public String acknowledgementState;
    /** lineItems[].productId of the line item read (only one product exists) */
    public String productId;
    /** lineItems[].expiryTime in epoch ms, or null */
    public Long expiryTimeMs;
    /** lineItems[].latestSuccessfulOrderId (latestOrderId is gone from client libraries since 2026-07-01) */
    public String latestSuccessfulOrderId;
    public String linkedPurchaseToken;
    /** externalAccountIdentifiers.obfuscatedExternalAccountId */
    public String obfuscatedAccountId;
    /** outOfAppPurchaseContext.expiredPurchaseToken (Play Store resubscribe after expiry) */
    public String expiredPurchaseToken;
}
