package org.pente.subscriptions.play;

import org.pente.gameServer.core.MySQLDSGPlayerStorer;

/**
 * The Play Console products and what each grants (spec 2026-10-11 §2).
 * Product ids come from the Google API response, never from the client.
 */
public final class PlayProducts {

    public static final String YEARLY = "yearly_subscription";
    public static final String MONTH_PASS = "month_pass";
    public static final String VACATION_10 = "vacation_10";
    public static final String VACATION_30 = "vacation_30";
    public static final String VACATION_60 = "vacation_60";

    /** same bits as iOS insertiOSTransactionId */
    public static final int YEARLY_LEVEL = MySQLDSGPlayerStorer.ONEYEAR | MySQLDSGPlayerStorer.UNLIMITEDTBGAMES
            | MySQLDSGPlayerStorer.NOADS | MySQLDSGPlayerStorer.DBACCESS;

    public static final int MONTH_LEVEL = MySQLDSGPlayerStorer.ONEMONTH | MySQLDSGPlayerStorer.UNLIMITEDTBGAMES
            | MySQLDSGPlayerStorer.NOADS | MySQLDSGPlayerStorer.DBACCESS;

    public static final long DAY_MS = 24L * 3600 * 1000;

    /** iOS encoding: paymentdate = expiryTime - 364 days, so "ONEYEAR expires at paymentdate + 1 year" holds */
    public static final long PAYMENTDATE_OFFSET_MS = 364L * DAY_MS;

    private PlayProducts() {
    }

    public static boolean isSubscription(String productId) {
        return YEARLY.equals(productId);
    }

    public static boolean isOneTime(String productId) {
        return MONTH_PASS.equals(productId) || vacationDays(productId) > 0;
    }

    /** extra turn-based vacation days a product grants, 0 for anything that is not a vacation product */
    public static int vacationDays(String productId) {
        if (VACATION_10.equals(productId)) {
            return 10;
        }
        if (VACATION_30.equals(productId)) {
            return 30;
        }
        if (VACATION_60.equals(productId)) {
            return 60;
        }
        return 0;
    }
}
