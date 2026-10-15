package org.pente.subscriptions.apple;

import java.util.Objects;

/**
 * One dsg_subscribers_apple row (spec 2026-10-15 §4.2): a subscription keyed by its originalTransactionId,
 * or a consumable keyed by its transactionId.
 */
public class AppleRow {

    public String originalTransactionId;
    public long pid;
    public String productId;
    public String latestTransactionId;
    public Long expiryTimeMs;
    public String state;
    public AppleEnvironment environment;
    public boolean tokenStamped;
    public long createdMs;
    public long updatedMs;

    public AppleRow copy() {
        AppleRow r = new AppleRow();
        r.originalTransactionId = originalTransactionId;
        r.pid = pid;
        r.productId = productId;
        r.latestTransactionId = latestTransactionId;
        r.expiryTimeMs = expiryTimeMs;
        r.state = state;
        r.environment = environment;
        r.tokenStamped = tokenStamped;
        r.createdMs = createdMs;
        r.updatedMs = updatedMs;
        return r;
    }

    /**
     * true when every stored field but created and updated is equal. The service upserts only rows that
     * changed, so "updated" is the time of the last change and the sweep's "updated in the last 35 days"
     * does not keep every row ever seen in the sweep.
     */
    public boolean sameContent(AppleRow o) {
        return o != null && Objects.equals(originalTransactionId, o.originalTransactionId) && pid == o.pid
                && Objects.equals(productId, o.productId) && Objects.equals(latestTransactionId, o.latestTransactionId)
                && Objects.equals(expiryTimeMs, o.expiryTimeMs) && Objects.equals(state, o.state)
                && environment == o.environment && tokenStamped == o.tokenStamped;
    }
}
