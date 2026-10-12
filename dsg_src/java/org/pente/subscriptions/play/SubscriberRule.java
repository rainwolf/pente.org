package org.pente.subscriptions.play;

import java.util.Calendar;
import java.util.List;

import org.pente.gameServer.core.MySQLDSGPlayerStorer;

/**
 * Mirror of MySQLDSGPlayerStorer.getSubscriberData's expiry rule, which this feature does not change:
 * paymentdate is read with ResultSet.getDate (truncated to local midnight); a ONEMONTH row counts
 * while paymentdate is after now - 31 days and expires at paymentdate + 31 days; a ONEYEAR row counts
 * while paymentdate is after now - 1 year and expires at paymentdate + 1 year.
 */
public final class SubscriberRule {

    private SubscriberRule() {
    }

    /** paymentdate as getSubscriberData sees it: local midnight of that day */
    static Calendar day(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** expiry getSubscriberData derives from one row, or null for a row with neither period bit (level 0) */
    public static Long impliedExpiryMs(int level, long paymentDateMs) {
        Calendar c = day(paymentDateMs);
        if ((level & MySQLDSGPlayerStorer.ONEMONTH) != 0) {
            c.add(Calendar.DATE, 31);
            return Long.valueOf(c.getTimeInMillis());
        }
        if ((level & MySQLDSGPlayerStorer.ONEYEAR) != 0) {
            c.add(Calendar.YEAR, 1);
            return Long.valueOf(c.getTimeInMillis());
        }
        return null;
    }

    /** getSubscriptionExpiration() as getSubscriberData computes it at nowMs, or null */
    public static Long expirationMs(List<SubscriberRow> rows, long nowMs) {
        Calendar lastMonth = Calendar.getInstance();
        lastMonth.setTimeInMillis(nowMs);
        lastMonth.add(Calendar.DATE, -31);
        Calendar lastYear = Calendar.getInstance();
        lastYear.setTimeInMillis(nowMs);
        lastYear.add(Calendar.YEAR, -1);
        Long expiration = null;
        for (SubscriberRow row : rows) {
            Calendar paymentDate = day(row.paymentDateMs);
            boolean counts;
            if ((row.level & MySQLDSGPlayerStorer.ONEMONTH) != 0) {
                counts = paymentDate.after(lastMonth);
            } else if ((row.level & MySQLDSGPlayerStorer.ONEYEAR) != 0) {
                counts = paymentDate.after(lastYear);
            } else {
                counts = false;
            }
            if (counts) {
                Long expiry = impliedExpiryMs(row.level, row.paymentDateMs);
                if (expiration == null || expiry.longValue() > expiration.longValue()) {
                    expiration = expiry;
                }
            }
        }
        return expiration;
    }

    /** getSubscriberLevel() as getSubscriberData computes it at nowMs */
    public static int level(List<SubscriberRow> rows, long nowMs) {
        Calendar lastMonth = Calendar.getInstance();
        lastMonth.setTimeInMillis(nowMs);
        lastMonth.add(Calendar.DATE, -31);
        Calendar lastYear = Calendar.getInstance();
        lastYear.setTimeInMillis(nowMs);
        lastYear.add(Calendar.YEAR, -1);
        int level = 0;
        for (SubscriberRow row : rows) {
            Calendar paymentDate = day(row.paymentDateMs);
            if ((row.level & MySQLDSGPlayerStorer.ONEMONTH) != 0) {
                if (paymentDate.after(lastMonth)) {
                    level |= row.level;
                }
            } else if ((row.level & MySQLDSGPlayerStorer.ONEYEAR) != 0) {
                if (paymentDate.after(lastYear)) {
                    level |= row.level;
                }
            }
        }
        return level;
    }
}
