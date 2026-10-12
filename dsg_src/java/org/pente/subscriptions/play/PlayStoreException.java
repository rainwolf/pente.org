package org.pente.subscriptions.play;

/** Database or player-storer failure while syncing a Play purchase: callers retry. */
public class PlayStoreException extends Exception {

    public PlayStoreException(String message) {
        super(message);
    }

    public PlayStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
