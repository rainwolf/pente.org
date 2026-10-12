package org.pente.subscriptions.play;

/** dsg_subscribers, keyed on transactionid. MySQLPlayStore is the real implementation; tests use a fake. */
public interface SubscriberStore {

    /** plain INSERT with amount=0, verified=1; false when the transactionid already exists (duplicate key) */
    boolean insert(SubscriberRow row) throws PlayStoreException;

    /** the row for this transactionid, or null */
    SubscriberRow findByTransactionId(String transactionId) throws PlayStoreException;

    void updatePaymentDate(String transactionId, long paymentDateMs) throws PlayStoreException;

    /** level=0, the PayPal refund precedent */
    void setLevelZero(String transactionId) throws PlayStoreException;

    /**
     * paymentdate -= days on every ONEMONTH row of pid whose paymentdate is after afterMs,
     * except the row exceptTransactionId
     */
    void shiftMonthRowsBack(long pid, long afterMs, String exceptTransactionId, int days) throws PlayStoreException;
}
