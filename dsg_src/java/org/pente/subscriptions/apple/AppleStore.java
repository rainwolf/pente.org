package org.pente.subscriptions.apple;

import java.util.List;

import org.pente.subscriptions.play.PlayStoreException;

/**
 * The three App Store tables of migration dsg_src/sql/2026-10-15-app-store-server.sql: dsg_subscribers_apple,
 * dsg_apple_notifications and dsg_apple_poll_state. MySQLAppleStore is the real implementation; tests use
 * FakeAppleStore. dsg_subscribers stays behind the shared SubscriberStore.
 */
public interface AppleStore {

    /** the row keyed by this original transaction id (a consumable: its transaction id), or null */
    AppleRow find(String originalTransactionId) throws PlayStoreException;

    /** insert, or update every field but created; updated is set to the store's clock */
    void upsert(AppleRow row) throws PlayStoreException;

    void setState(String originalTransactionId, String state) throws PlayStoreException;

    void setTokenStamped(String originalTransactionId) throws PlayStoreException;

    /** a 1YRNOADSORLIMITS row of pid in ACTIVE or GRACE whose expiry is after nowMs */
    boolean hasActiveSubscription(long pid, long nowMs) throws PlayStoreException;

    /**
     * 6-hour sweep (spec §4.7): subscription rows in ACTIVE, GRACE or BILLING_RETRY whose expiry_time is less than
     * 2 days ahead or already past, plus subscription rows in any state updated in the last 35 days
     */
    List<AppleRow> subscriptionsToPoll(long nowMs) throws PlayStoreException;

    /** GRANTING rows whose updated is before olderThanMs */
    List<AppleRow> staleGranting(long olderThanMs) throws PlayStoreException;

    /** subscription rows still ACTIVE whose expiry_time is before expiredBeforeMs */
    List<AppleRow> staleActive(long expiredBeforeMs) throws PlayStoreException;

    boolean notificationSeen(String notificationUuid) throws PlayStoreException;

    /** records a handled notification; a repeat is a no-op */
    void recordNotification(String notificationUuid, String notificationType, long receivedMs) throws PlayStoreException;

    /** the history replay's high-water mark for env, or null before the first successful replay */
    Long historyUntil(AppleEnvironment env) throws PlayStoreException;

    void setHistoryUntil(AppleEnvironment env, long untilMs) throws PlayStoreException;
}
