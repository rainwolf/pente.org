package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pente.subscriptions.play.PlayStoreException;
import org.pente.subscriptions.play.SubscriberRow;
import org.pente.subscriptions.play.SubscriberStore;

/** In-memory dsg_subscribers with the same semantics as MySQLPlayStore's SQL. Thread-safe. */
public class FakeSubscriberStore implements SubscriberStore {

    public final Map<String, SubscriberRow> rows = new LinkedHashMap<String, SubscriberRow>();
    /** updatePaymentDate calls that throw before the next one succeeds */
    public int failPaymentDateUpdates;

    public synchronized boolean insert(SubscriberRow row) {
        if (rows.containsKey(row.transactionId)) {
            return false;
        }
        rows.put(row.transactionId, row.copy());
        return true;
    }

    public synchronized SubscriberRow findByTransactionId(String transactionId) {
        SubscriberRow r = rows.get(transactionId);
        return r == null ? null : r.copy();
    }

    public synchronized List<SubscriberRow> findByPid(long pid) {
        return rowsOf(pid);
    }

    public synchronized void updatePaymentDate(String transactionId, long paymentDateMs) throws PlayStoreException {
        if (failPaymentDateUpdates > 0) {
            failPaymentDateUpdates--;
            throw new PlayStoreException("fake: update paymentdate failed");
        }
        SubscriberRow r = rows.get(transactionId);
        if (r != null) {
            r.paymentDateMs = paymentDateMs;
        }
    }

    public synchronized void setLevelZero(String transactionId) {
        SubscriberRow r = rows.get(transactionId);
        if (r != null) {
            r.level = 0;
        }
    }

    /** test helper: copies of every row of pid */
    public synchronized List<SubscriberRow> rowsOf(long pid) {
        List<SubscriberRow> result = new ArrayList<SubscriberRow>();
        for (SubscriberRow r : rows.values()) {
            if (r.pid == pid) {
                result.add(r.copy());
            }
        }
        return result;
    }
}
