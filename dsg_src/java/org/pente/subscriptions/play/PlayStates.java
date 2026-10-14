package org.pente.subscriptions.play;

/**
 * Values of dsg_subscribers_android.state (VARCHAR(64)) and the Play acknowledgement states.
 * Subscription rows hold Google's SubscriptionState name verbatim; one-time rows hold this
 * feature's own states.
 */
public final class PlayStates {

    // Google SubscriptionState names (subscriptionsv2)
    public static final String ACTIVE = "SUBSCRIPTION_STATE_ACTIVE";
    public static final String IN_GRACE_PERIOD = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";
    public static final String CANCELED = "SUBSCRIPTION_STATE_CANCELED";
    public static final String ON_HOLD = "SUBSCRIPTION_STATE_ON_HOLD";
    public static final String EXPIRED = "SUBSCRIPTION_STATE_EXPIRED";
    public static final String PENDING = "SUBSCRIPTION_STATE_PENDING";

    // Google acknowledgementState names (subscriptionsv2)
    public static final String ACK_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING";
    public static final String ACK_ACKNOWLEDGED = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED";

    // this feature's own states
    /** one-time purchase verified and stored, nothing granted yet */
    public static final String PURCHASED = "PURCHASED";
    /** vacation grant in progress; addExtraTBVacationDays is not idempotent, so never retried automatically */
    public static final String GRANTING = "GRANTING";
    public static final String GRANTED = "GRANTED";
    public static final String CONSUMED = "CONSUMED";
    /** vacation purchase refunded through orders.refund because the player is in a turn-based tourney */
    public static final String REFUNDED = "REFUNDED";
    /** order voided (refund or chargeback) */
    public static final String REVOKED = "REVOKED";
    /** replaced by a token whose linkedPurchaseToken points here */
    public static final String SUPERSEDED = "SUPERSEDED";

    private PlayStates() {
    }

    /** subscription states that keep (or grant) access until expiryTime */
    public static boolean isGrantable(String state) {
        return ACTIVE.equals(state) || IN_GRACE_PERIOD.equals(state) || CANCELED.equals(state);
    }

    /** subscription states where entitlement ends at Play's expiryTime (clamp) */
    public static boolean isClamped(String state) {
        return EXPIRED.equals(state) || ON_HOLD.equals(state);
    }
}
