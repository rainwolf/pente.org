package org.pente.subscriptions.apple;

/**
 * Apple's signed data did not verify (spec 2026-10-15 §4.4 step 1, §4.6 step 1). retryable is the library's
 * RETRYABLE_VERIFICATION_FAILURE (OCSP could not be reached): "retry later" or 500. Anything else means the
 * data is not Apple's: "invalid receipt" or 401.
 */
public class AppleVerificationException extends Exception {

    private static final long serialVersionUID = -2336902911391025995L;

    private final boolean retryable;

    public AppleVerificationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AppleVerificationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
