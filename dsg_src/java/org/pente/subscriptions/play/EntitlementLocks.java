package org.pente.subscriptions.play;

import java.util.concurrent.ConcurrentHashMap;

/**
 * One lock object per pid, shared by every store's entitlement service (Google Play and the App Store),
 * so a Play grant and an App Store grant for the same player never stack from the same cached expiry
 * (spec 2026-10-15 §4.5). The PayPal servlet and the legacy iOS poller do not take it.
 */
public final class EntitlementLocks {

    private static final ConcurrentHashMap<Long, Object> LOCKS = new ConcurrentHashMap<Long, Object>();

    private EntitlementLocks() {
    }

    /** the monitor every grant for pid synchronizes on; the same object for the life of the JVM */
    public static Object lockFor(long pid) {
        Object lock = LOCKS.get(Long.valueOf(pid));
        if (lock == null) {
            Object fresh = new Object();
            lock = LOCKS.putIfAbsent(Long.valueOf(pid), fresh);
            if (lock == null) {
                lock = fresh;
            }
        }
        return lock;
    }
}
