package org.pente.subscriptions.play;

/** One dsg_subscribers row. */
public class SubscriberRow {

    public long pid;
    public String transactionId;
    public int level;
    public long paymentDateMs;

    public SubscriberRow() {
    }

    public SubscriberRow(long pid, String transactionId, int level, long paymentDateMs) {
        this.pid = pid;
        this.transactionId = transactionId;
        this.level = level;
        this.paymentDateMs = paymentDateMs;
    }

    public SubscriberRow copy() {
        return new SubscriberRow(pid, transactionId, level, paymentDateMs);
    }
}
