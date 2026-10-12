package org.pente.subscriptions.play;

import java.util.List;

/** dsg_subscribers_android. MySQLPlayStore is the real implementation; tests use a fake. */
public interface AndroidPurchaseStore {

    /** the row for this token hash, or null */
    AndroidPurchaseRow find(String tokenHash) throws PlayStoreException;

    /**
     * Insert a subscription row, or update it. On update pid and created are kept,
     * latest_successful_order_id and expiry_time keep their old value when the new one is null,
     * and acknowledged never goes back from 1 to 0. created/updated are set by the store.
     */
    void upsertSubscription(AndroidPurchaseRow row) throws PlayStoreException;

    /** insert a one-time purchase row; does nothing when the token hash is already stored */
    void insertProductIfAbsent(AndroidPurchaseRow row) throws PlayStoreException;

    /** set state and updated; does nothing when the token hash is not stored */
    void setState(String tokenHash, String state) throws PlayStoreException;

    /** set acknowledged=1 and updated */
    void setAcknowledged(String tokenHash) throws PlayStoreException;

    /** rows whose latest_successful_order_id equals orderId */
    List<AndroidPurchaseRow> findByOrderId(String orderId) throws PlayStoreException;

    /** true when pid has a yearly_subscription row in state ACTIVE or IN_GRACE_PERIOD */
    boolean hasAutoRenewingSubscription(long pid) throws PlayStoreException;

    /**
     * yearly_subscription rows the 6-hour poller re-syncs: state not EXPIRED, REVOKED or SUPERSEDED with
     * expiry_time between now - 60 days and now + 2 days, or acknowledged=0 and state ACTIVE, IN_GRACE_PERIOD
     * or CANCELED (however old the row: a deferred payment can be PENDING for days before it is grantable)
     */
    List<AndroidPurchaseRow> subscriptionsToPoll(long nowMs) throws PlayStoreException;

    /** one-time rows in state PURCHASED or GRANTED created after now - 3 days (not consumed yet) */
    List<AndroidPurchaseRow> productsToPoll(long nowMs) throws PlayStoreException;

    /** rows in state GRANTING whose updated is before olderThanMs */
    List<AndroidPurchaseRow> staleGranting(long olderThanMs) throws PlayStoreException;
}
