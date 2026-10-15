package org.pente.subscriptions.apple;

import java.util.UUID;

/**
 * The fields of a verified JWSTransactionDecodedPayload the server uses (spec 2026-10-15 §4.4, §4.5).
 * Only AppleVerifier and AppStoreApi build these from Apple's signed data, so every instance the service sees
 * was verified.
 */
public class AppleTransaction {

    public String transactionId;
    public String originalTransactionId;
    public String productId;
    public AppleEnvironment environment;
    /** subscriptions only, epoch ms */
    public Long expiresDateMs;
    public Long purchaseDateMs;
    /** set when the App Store refunded the transaction or revoked it from Family Sharing */
    public Long revocationDateMs;
    /** null when the app did not set one (old app versions, offer codes, purchases outside the app) */
    public UUID appAccountToken;
    /** inAppOwnershipType FAMILY_SHARED: never granted (spec §3) */
    public boolean familyShared;

    public AppleTransaction copy() {
        AppleTransaction t = new AppleTransaction();
        t.transactionId = transactionId;
        t.originalTransactionId = originalTransactionId;
        t.productId = productId;
        t.environment = environment;
        t.expiresDateMs = expiresDateMs;
        t.purchaseDateMs = purchaseDateMs;
        t.revocationDateMs = revocationDateMs;
        t.appAccountToken = appAccountToken;
        t.familyShared = familyShared;
        return t;
    }
}
