package org.pente.subscriptions.apple;

import java.util.List;
import java.util.UUID;

/**
 * The App Store Server API calls the server makes (spec 2026-10-15 §4.5), one client per environment.
 * AppStoreApi is the real implementation over Apple's library and verifies every signed transaction and renewal
 * info it returns; UnconfiguredAppleApi stands in when the key is missing; tests use FakeAppleApi.
 */
public interface AppleApi {

    /** Get All Subscription Statuses: every lastTransactions item of every group, verified */
    List<AppleSubscriptionStatus> subscriptionStatuses(AppleEnvironment env, String originalTransactionId)
            throws AppleApiException;

    /** Get Transaction Info, verified */
    AppleTransaction transactionInfo(AppleEnvironment env, String transactionId) throws AppleApiException;

    /**
     * Get Transaction History V2 filtered to one product, all pages, verified, de-duplicated by transactionId.
     * Apple returns the customer's transactions, not one chain: callers filter on originalTransactionId.
     */
    List<AppleTransaction> transactionHistory(AppleEnvironment env, String anyTransactionId, String productId)
            throws AppleApiException;

    /** one page of Get Notification History; paginationToken null for the first page */
    AppleNotificationPage notificationHistory(AppleEnvironment env, long startMs, long endMs, String paginationToken)
            throws AppleApiException;

    /** Set App Account Token on an original transaction id */
    void setAppAccountToken(AppleEnvironment env, String originalTransactionId, UUID token) throws AppleApiException;

    /** Request a Test Notification; returns the test notification token */
    String requestTestNotification(AppleEnvironment env) throws AppleApiException;
}
