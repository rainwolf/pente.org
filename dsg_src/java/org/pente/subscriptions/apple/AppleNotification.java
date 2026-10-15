package org.pente.subscriptions.apple;

/** A verified App Store Server Notification V2 (spec 2026-10-15 §4.6). */
public class AppleNotification {

    public String notificationUuid;
    /** NotificationTypeV2 name, e.g. "DID_RENEW"; Apple's raw string for a type this library does not know */
    public String type;
    /** may be null */
    public String subtype;
    public AppleEnvironment environment;
    /** data.signedTransactionInfo, verified; null for TEST and summary notifications */
    public AppleTransaction transaction;
    /** data.signedRenewalInfo, verified; may be null */
    public AppleRenewal renewal;
}
