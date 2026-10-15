package org.pente.subscriptions.apple.test;

import java.util.HashMap;
import java.util.Map;

import org.pente.subscriptions.apple.AppleNotification;
import org.pente.subscriptions.apple.AppleTransaction;
import org.pente.subscriptions.apple.AppleVerificationException;
import org.pente.subscriptions.apple.AppleVerifier;

/**
 * "Signed" strings mapped to what they decode to. An unknown string fails as not Apple's (non-retryable);
 * failWith, when set, is thrown for every call.
 */
public class FakeAppleVerifier implements AppleVerifier {

    public final Map<String, AppleTransaction> transactions = new HashMap<String, AppleTransaction>();
    public final Map<String, AppleNotification> notifications = new HashMap<String, AppleNotification>();
    public volatile AppleVerificationException failWith;

    public synchronized AppleTransaction verifyTransaction(String jws) throws AppleVerificationException {
        if (failWith != null) {
            throw failWith;
        }
        AppleTransaction t = transactions.get(jws);
        if (t == null) {
            throw new AppleVerificationException("fake: VERIFICATION_FAILURE", false);
        }
        return t.copy();
    }

    public synchronized AppleNotification verifyNotification(String signedPayload) throws AppleVerificationException {
        if (failWith != null) {
            throw failWith;
        }
        AppleNotification n = notifications.get(signedPayload);
        if (n == null) {
            throw new AppleVerificationException("fake: VERIFICATION_FAILURE", false);
        }
        AppleNotification copy = new AppleNotification();
        copy.notificationUuid = n.notificationUuid;
        copy.type = n.type;
        copy.subtype = n.subtype;
        copy.environment = n.environment;
        copy.transaction = n.transaction == null ? null : n.transaction.copy();
        copy.renewal = n.renewal == null ? null : n.renewal.copy();
        return copy;
    }
}
