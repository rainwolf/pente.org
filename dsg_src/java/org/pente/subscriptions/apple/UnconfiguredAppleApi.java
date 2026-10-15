package org.pente.subscriptions.apple;

import java.util.List;
import java.util.UUID;

/**
 * Stands in for AppStoreApi when the In-App Purchase key or its ids are missing (spec 2026-10-15 §4.1): every
 * call fails as retryable, so purchase POSTs answer "retry later" and notifications 500 until the key is installed.
 */
public class UnconfiguredAppleApi implements AppleApi {

    private final String reason;

    public UnconfiguredAppleApi(String reason) {
        this.reason = reason;
    }

    private AppleApiException unavailable() {
        return new AppleApiException("App Store Server API not configured: " + reason, true);
    }

    public List<AppleSubscriptionStatus> subscriptionStatuses(AppleEnvironment env, String originalTransactionId)
            throws AppleApiException {
        throw unavailable();
    }

    public AppleTransaction transactionInfo(AppleEnvironment env, String transactionId) throws AppleApiException {
        throw unavailable();
    }

    public List<AppleTransaction> transactionHistory(AppleEnvironment env, String anyTransactionId, String productId)
            throws AppleApiException {
        throw unavailable();
    }

    public AppleNotificationPage notificationHistory(AppleEnvironment env, long startMs, long endMs,
                                                     String paginationToken) throws AppleApiException {
        throw unavailable();
    }

    public void setAppAccountToken(AppleEnvironment env, String originalTransactionId, UUID token)
            throws AppleApiException {
        throw unavailable();
    }

    public String requestTestNotification(AppleEnvironment env) throws AppleApiException {
        throw unavailable();
    }
}
