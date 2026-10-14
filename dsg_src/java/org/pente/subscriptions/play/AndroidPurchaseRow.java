package org.pente.subscriptions.play;

/** One dsg_subscribers_android row. */
public class AndroidPurchaseRow {

    public String tokenHash;
    public String purchaseToken;
    public long pid;
    public String productId;
    /** subscriptions: latestSuccessfulOrderId; one-time products: the dsg_subscribers transactionid (PlayTokens.orderKey) */
    public String latestSuccessfulOrderId;
    public String linkedPurchaseTokenHash;
    /** subscriptions only, epoch ms */
    public Long expiryTimeMs;
    /** a SubscriptionState name, or PURCHASED/GRANTING/GRANTED/CONSUMED/REFUNDED/REVOKED/SUPERSEDED */
    public String state;
    public boolean acknowledged;
    public long createdMs;
    public long updatedMs;

    public AndroidPurchaseRow copy() {
        AndroidPurchaseRow r = new AndroidPurchaseRow();
        r.tokenHash = tokenHash;
        r.purchaseToken = purchaseToken;
        r.pid = pid;
        r.productId = productId;
        r.latestSuccessfulOrderId = latestSuccessfulOrderId;
        r.linkedPurchaseTokenHash = linkedPurchaseTokenHash;
        r.expiryTimeMs = expiryTimeMs;
        r.state = state;
        r.acknowledged = acknowledged;
        r.createdMs = createdMs;
        r.updatedMs = updatedMs;
        return r;
    }
}
