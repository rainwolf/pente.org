package org.pente.subscriptions.play;

/**
 * Outcome of a Play sync, with the reply string the Android client reads (spec 2026-10-11 §3.3, §4.5).
 * The success replies match iOS IOSReceiptServlet.ReceiptClass.
 */
public enum PlayClassification {
    /** first grant on a token with no linked predecessor: thank-you message */
    NEW("success"),
    /** new order on a known token, or on a token whose linked token belongs to the same pid */
    RENEWAL("success:renewal"),
    /** order already recorded for this pid */
    KNOWN("success:known"),
    /** the purchase belongs to another pente.org account than the requester */
    SHARED("success:shared"),
    /** vacation days bought during a turn-based tourney: refunded through orders.refund */
    REFUNDED("success:refunded"),
    /** token unknown to Google, product not ours, or the pid cannot be resolved */
    INVALID("invalid receipt"),
    /** valid purchase with nothing to grant now (pending, expired, on hold); the client stays silent */
    INACTIVE("inactive");

    /** transient Google API or DB error: neither "success" nor "invalid receipt", so the client retries later */
    public static final String RETRY_REPLY = "retry later";

    private final String reply;

    PlayClassification(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }
}
