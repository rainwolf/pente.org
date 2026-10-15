package org.pente.subscriptions.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.pente.gameServer.core.MySQLDSGPlayerStorer;

/**
 * Month-pass restacking shared by PlayEntitlementService and AppleEntitlementService
 * (spec 2026-10-15 §4.5 "small extraction first"). Public because the App Store code lives in
 * org.pente.subscriptions.apple.
 */
public final class MonthPassStack {

    private MonthPassStack() {
    }

    /**
     * A row about to be zeroed (revoke) or a ONEYEAR row about to be shortened (clamp), given as its new level and
     * paymentdate: month passes stacked after its old expiry (ONEMONTH rows dated after now) move back
     * to start, in their order, where the remaining entitlement now ends, or now. A row never moves
     * later. Idempotent, and run before the changed row is written, so a crash in between is redone by
     * the repeat revoke or the next sync. Caller holds EntitlementLocks.lockFor(pid).
     */
    public static void restack(SubscriberStore subscribers, long pid, String transactionId, int newLevel,
                               long newPaymentDateMs, long now) throws PlayStoreException {
        List<SubscriberRow> started = new ArrayList<SubscriberRow>();
        List<SubscriberRow> stacked = new ArrayList<SubscriberRow>();
        for (SubscriberRow r : subscribers.findByPid(pid)) {
            if (transactionId.equals(r.transactionId)) {
                r = new SubscriberRow(r.pid, r.transactionId, newLevel, newPaymentDateMs);
            }
            if ((r.level & MySQLDSGPlayerStorer.ONEMONTH) != 0 && r.paymentDateMs > now) {
                stacked.add(r);
            } else {
                started.add(r);
            }
        }
        if (stacked.isEmpty()) {
            return;
        }
        Collections.sort(stacked, new Comparator<SubscriberRow>() {
            public int compare(SubscriberRow a, SubscriberRow b) {
                return Long.compare(a.paymentDateMs, b.paymentDateMs);
            }
        });
        Long end = SubscriberRule.expirationMs(started, now);
        long start = end != null && end.longValue() > now ? end.longValue() : now;
        for (SubscriberRow r : stacked) {
            long paymentDate = r.paymentDateMs;
            if (start < paymentDate && r.transactionId != null) {
                subscribers.updatePaymentDate(r.transactionId, start);
                paymentDate = start;
            }
            start = Math.max(start, SubscriberRule.impliedExpiryMs(r.level, paymentDate).longValue());
        }
    }
}
