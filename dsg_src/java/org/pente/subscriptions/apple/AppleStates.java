package org.pente.subscriptions.apple;

/** dsg_subscribers_apple.state values (spec 2026-10-15 §4.2). */
public final class AppleStates {

    // subscriptions
    public static final String ACTIVE = "ACTIVE";
    public static final String GRACE = "GRACE";
    public static final String BILLING_RETRY = "BILLING_RETRY";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";
    // consumables
    public static final String GRANTING = "GRANTING";
    public static final String CONSUMED = "CONSUMED";
    public static final String REFUNDED = "REFUNDED";

    private AppleStates() {
    }

    /**
     * App Store Server API subscription status (getAllSubscriptionStatuses lastTransactions.status):
     * 1 active, 2 expired, 3 billing retry, 4 billing grace period, 5 revoked (refunded or Family Sharing
     * revoked). null for any other value.
     */
    public static String fromStatus(int status) {
        switch (status) {
            case 1:
                return ACTIVE;
            case 2:
                return EXPIRED;
            case 3:
                return BILLING_RETRY;
            case 4:
                return GRACE;
            case 5:
                return REVOKED;
            default:
                return null;
        }
    }

    /** subscription states the 6-hour sweep follows by expiry */
    public static boolean isLive(String state) {
        return ACTIVE.equals(state) || GRACE.equals(state) || BILLING_RETRY.equals(state);
    }

    /** a refunded consumable or a revoked subscription */
    public static boolean isRevoked(String state) {
        return REVOKED.equals(state) || REFUNDED.equals(state);
    }
}
