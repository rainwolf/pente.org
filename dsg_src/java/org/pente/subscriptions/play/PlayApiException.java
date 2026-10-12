package org.pente.subscriptions.play;

/** Transient Google Play Developer API failure: callers retry (client resync, Pub/Sub redelivery, pollers). */
public class PlayApiException extends Exception {

    public PlayApiException(String message) {
        super(message);
    }

    public PlayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
