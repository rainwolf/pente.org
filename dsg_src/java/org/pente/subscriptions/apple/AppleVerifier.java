package org.pente.subscriptions.apple;

/**
 * Verifies Apple's signed data from outside the API (spec 2026-10-15 §4.4 step 1, §4.6 step 1): the client's
 * jwsRepresentation and notification payloads. Production first, sandbox on INVALID_ENVIRONMENT.
 * AppStoreVerifier is the real implementation; tests use FakeAppleVerifier.
 */
public interface AppleVerifier {

    AppleTransaction verifyTransaction(String jws) throws AppleVerificationException;

    /** the notification with its signed transaction and renewal info verified too */
    AppleNotification verifyNotification(String signedPayload) throws AppleVerificationException;
}
