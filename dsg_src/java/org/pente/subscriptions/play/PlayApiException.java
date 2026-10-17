package org.pente.subscriptions.play;

/** Transient Google Play Developer API failure: callers retry (client resync, Pub/Sub redelivery, pollers). */
public class PlayApiException extends Exception {

    private static final long serialVersionUID = -2632117126756775467L;

    public PlayApiException(String message) {
        super(message);
    }

    public PlayApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
