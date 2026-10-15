package org.pente.subscriptions.apple;

import java.util.UUID;

/** The fields of a verified JWSRenewalInfoDecodedPayload the server uses (spec 2026-10-15 §4.5). */
public class AppleRenewal {

    public String originalTransactionId;
    /** set while the subscription is in the Billing Grace Period, epoch ms */
    public Long gracePeriodExpiresDateMs;
    public UUID appAccountToken;

    public AppleRenewal copy() {
        AppleRenewal r = new AppleRenewal();
        r.originalTransactionId = originalTransactionId;
        r.gracePeriodExpiresDateMs = gracePeriodExpiresDateMs;
        r.appAccountToken = appAccountToken;
        return r;
    }
}
