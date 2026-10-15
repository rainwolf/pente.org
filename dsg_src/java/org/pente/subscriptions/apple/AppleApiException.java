package org.pente.subscriptions.apple;

/**
 * An App Store Server API call failed (spec 2026-10-15 §4.4). retryable: a transport failure, HTTP 429 or 5xx,
 * a *_RETRYABLE API error, or the API is not configured; the client gets "retry later" and a notification 500.
 * Not retryable: any other 4xx (for example an unknown transaction id).
 */
public class AppleApiException extends Exception {

    private final boolean retryable;

    public AppleApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AppleApiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
