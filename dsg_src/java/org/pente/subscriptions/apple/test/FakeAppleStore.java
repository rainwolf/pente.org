package org.pente.subscriptions.apple.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.pente.subscriptions.apple.AppleEnvironment;
import org.pente.subscriptions.apple.AppleProducts;
import org.pente.subscriptions.apple.AppleRow;
import org.pente.subscriptions.apple.AppleStates;
import org.pente.subscriptions.apple.AppleStore;
import org.pente.subscriptions.play.PlayProducts;
import org.pente.subscriptions.play.PlayStoreException;

/** In-memory App Store tables with the same semantics as MySQLAppleStore's SQL. Thread-safe. */
public class FakeAppleStore implements AppleStore {

    public final Map<String, AppleRow> rows = new LinkedHashMap<String, AppleRow>();
    /** handled notification UUIDs and their types */
    public final Map<String, String> notifications = new LinkedHashMap<String, String>();
    public final Map<AppleEnvironment, Long> historyUntil = new HashMap<AppleEnvironment, Long>();
    /** upsert calls that throw before the next one succeeds */
    public int failUpserts;
    /** recordNotification calls that throw before the next one succeeds */
    public int failRecords;
    /** setState calls that throw before the next one succeeds */
    public int failSetStates;
    /** how many upserts succeeded */
    public int upserts;

    private final LongSupplier clock;

    public FakeAppleStore(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized AppleRow find(String originalTransactionId) {
        AppleRow r = rows.get(originalTransactionId);
        return r == null ? null : r.copy();
    }

    public synchronized void upsert(AppleRow row) throws PlayStoreException {
        if (failUpserts > 0) {
            failUpserts--;
            throw new PlayStoreException("fake: upsert dsg_subscribers_apple failed");
        }
        long now = clock.getAsLong();
        AppleRow existing = rows.get(row.originalTransactionId);
        AppleRow stored = row.copy();
        stored.createdMs = existing == null ? now : existing.createdMs;
        stored.updatedMs = now;
        rows.put(row.originalTransactionId, stored);
        upserts++;
    }

    public synchronized void setState(String originalTransactionId, String state) throws PlayStoreException {
        if (failSetStates > 0) {
            failSetStates--;
            throw new PlayStoreException("fake: set dsg_subscribers_apple state failed");
        }
        AppleRow r = rows.get(originalTransactionId);
        if (r != null) {
            r.state = state;
            r.updatedMs = clock.getAsLong();
        }
    }

    public synchronized void setTokenStamped(String originalTransactionId) {
        AppleRow r = rows.get(originalTransactionId);
        if (r != null) {
            r.tokenStamped = true;
        }
    }

    public synchronized boolean hasActiveSubscription(long pid, long nowMs) {
        for (AppleRow r : rows.values()) {
            if (r.pid == pid && AppleProducts.isSubscription(r.productId)
                    && (AppleStates.ACTIVE.equals(r.state) || AppleStates.GRACE.equals(r.state))
                    && r.expiryTimeMs != null && r.expiryTimeMs.longValue() > nowMs) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<AppleRow> subscriptionsToPoll(long nowMs) {
        List<AppleRow> result = new ArrayList<AppleRow>();
        for (AppleRow r : rows.values()) {
            if (!AppleProducts.isSubscription(r.productId)) {
                continue;
            }
            boolean expiring = AppleStates.isLive(r.state) && r.expiryTimeMs != null
                    && r.expiryTimeMs.longValue() < nowMs + 2 * PlayProducts.DAY_MS;
            boolean recent = r.updatedMs >= nowMs - 35 * PlayProducts.DAY_MS;
            if (expiring || recent) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized List<AppleRow> staleGranting(long olderThanMs) {
        List<AppleRow> result = new ArrayList<AppleRow>();
        for (AppleRow r : rows.values()) {
            if (AppleStates.GRANTING.equals(r.state) && r.updatedMs < olderThanMs) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized List<AppleRow> staleActive(long expiredBeforeMs) {
        List<AppleRow> result = new ArrayList<AppleRow>();
        for (AppleRow r : rows.values()) {
            if (AppleProducts.isSubscription(r.productId) && AppleStates.ACTIVE.equals(r.state)
                    && r.expiryTimeMs != null && r.expiryTimeMs.longValue() < expiredBeforeMs) {
                result.add(r.copy());
            }
        }
        return result;
    }

    public synchronized boolean notificationSeen(String notificationUuid) {
        return notifications.containsKey(notificationUuid);
    }

    public synchronized void recordNotification(String notificationUuid, String notificationType, long receivedMs)
            throws PlayStoreException {
        if (failRecords > 0) {
            failRecords--;
            throw new PlayStoreException("fake: record notification failed");
        }
        if (!notifications.containsKey(notificationUuid)) {
            notifications.put(notificationUuid, notificationType);
        }
    }

    public synchronized Long historyUntil(AppleEnvironment env) {
        return historyUntil.get(env);
    }

    public synchronized void setHistoryUntil(AppleEnvironment env, long untilMs) {
        historyUntil.put(env, Long.valueOf(untilMs));
    }
}
