package org.pente.subscriptions.play.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.pente.subscriptions.play.AndroidPurchaseRow;
import org.pente.subscriptions.play.AndroidPurchaseStore;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStates;

/** In-memory dsg_subscribers_android with the same semantics as MySQLPlayStore's SQL. Thread-safe. */
public class FakeAndroidPurchaseStore implements AndroidPurchaseStore {

    private static final long DAY = PlayProducts.DAY_MS;

    public final Map<String, AndroidPurchaseRow> rows = new LinkedHashMap<String, AndroidPurchaseRow>();
    private final LongSupplier clock;

    public FakeAndroidPurchaseStore(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized AndroidPurchaseRow find(String tokenHash) {
        AndroidPurchaseRow r = rows.get(tokenHash);
        return r == null ? null : r.copy();
    }

    public synchronized void upsertSubscription(AndroidPurchaseRow row) {
        long now = clock.getAsLong();
        AndroidPurchaseRow existing = rows.get(row.tokenHash);
        if (existing == null) {
            AndroidPurchaseRow r = row.copy();
            r.createdMs = now;
            r.updatedMs = now;
            rows.put(r.tokenHash, r);
            return;
        }
        existing.purchaseToken = row.purchaseToken;
        existing.productId = row.productId;
        if (row.latestSuccessfulOrderId != null) {
            existing.latestSuccessfulOrderId = row.latestSuccessfulOrderId;
        }
        existing.linkedPurchaseTokenHash = row.linkedPurchaseTokenHash;
        if (row.expiryTimeMs != null) {
            existing.expiryTimeMs = row.expiryTimeMs;
        }
        existing.state = row.state;
        existing.acknowledged = existing.acknowledged || row.acknowledged;
        existing.updatedMs = now;
    }

    public synchronized void insertProductIfAbsent(AndroidPurchaseRow row) {
        if (rows.containsKey(row.tokenHash)) {
            return;
        }
        long now = clock.getAsLong();
        AndroidPurchaseRow r = row.copy();
        r.createdMs = now;
        r.updatedMs = now;
        rows.put(r.tokenHash, r);
    }

    public synchronized void setState(String tokenHash, String state) {
        AndroidPurchaseRow r = rows.get(tokenHash);
        if (r != null) {
            r.state = state;
            r.updatedMs = clock.getAsLong();
        }
    }

    public synchronized void setAcknowledged(String tokenHash) {
        AndroidPurchaseRow r = rows.get(tokenHash);
        if (r != null) {
            r.acknowledged = true;
            r.updatedMs = clock.getAsLong();
        }
    }

    public synchronized List<AndroidPurchaseRow> findByOrderId(String orderId) {
        List<AndroidPurchaseRow> result = new ArrayList<AndroidPurchaseRow>();
        for (AndroidPurchaseRow r : rows.values()) {
            if (orderId.equals(r.latestSuccessfulOrderId)) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized boolean hasAutoRenewingSubscription(long pid) {
        for (AndroidPurchaseRow r : rows.values()) {
            if (r.pid == pid && PlayProducts.YEARLY.equals(r.productId)
                    && (PlayStates.ACTIVE.equals(r.state) || PlayStates.IN_GRACE_PERIOD.equals(r.state))) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<AndroidPurchaseRow> subscriptionsToPoll(long nowMs) {
        List<AndroidPurchaseRow> result = new ArrayList<AndroidPurchaseRow>();
        for (AndroidPurchaseRow r : rows.values()) {
            if (!PlayProducts.YEARLY.equals(r.productId)) {
                continue;
            }
            boolean live = !PlayStates.EXPIRED.equals(r.state) && !PlayStates.REVOKED.equals(r.state)
                    && !PlayStates.SUPERSEDED.equals(r.state)
                    && r.expiryTimeMs != null
                    && r.expiryTimeMs.longValue() >= nowMs - 60 * DAY
                    && r.expiryTimeMs.longValue() <= nowMs + 2 * DAY;
            boolean unacknowledged = !r.acknowledged && r.createdMs > nowMs - 3 * DAY;
            if (live || unacknowledged) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized List<AndroidPurchaseRow> productsToPoll(long nowMs) {
        List<AndroidPurchaseRow> result = new ArrayList<AndroidPurchaseRow>();
        for (AndroidPurchaseRow r : rows.values()) {
            if (!PlayProducts.YEARLY.equals(r.productId)
                    && (PlayStates.PURCHASED.equals(r.state) || PlayStates.GRANTED.equals(r.state))
                    && r.createdMs > nowMs - 3 * DAY) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized List<AndroidPurchaseRow> staleGranting(long olderThanMs) {
        List<AndroidPurchaseRow> result = new ArrayList<AndroidPurchaseRow>();
        for (AndroidPurchaseRow r : rows.values()) {
            if (PlayStates.GRANTING.equals(r.state) && r.updatedMs < olderThanMs) {
                result.add(r.copy());
            }
        }
        return result;
    }

    /** test helper: the stored row (not a copy), or null */
    public synchronized AndroidPurchaseRow row(String tokenHash) {
        return rows.get(tokenHash);
    }
}
