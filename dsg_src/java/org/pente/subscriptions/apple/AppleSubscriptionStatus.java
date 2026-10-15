package org.pente.subscriptions.apple;

/**
 * One lastTransactions item of Get All Subscription Statuses, with its signed transaction and renewal info
 * already verified (spec 2026-10-15 §4.5 step 1). The response lists every chain of the customer, so callers
 * pick the item whose originalTransactionId they asked for.
 */
public class AppleSubscriptionStatus {

    public String originalTransactionId;
    /** 1 active, 2 expired, 3 billing retry, 4 billing grace period, 5 revoked */
    public int status;
    public AppleTransaction transaction;
    /** may be null */
    public AppleRenewal renewal;

    public AppleSubscriptionStatus copy() {
        AppleSubscriptionStatus s = new AppleSubscriptionStatus();
        s.originalTransactionId = originalTransactionId;
        s.status = status;
        s.transaction = transaction == null ? null : transaction.copy();
        s.renewal = renewal == null ? null : renewal.copy();
        return s;
    }
}
