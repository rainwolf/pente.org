package org.pente.notifications;

/**
 * Created by waliedothman on 28/01/2017.
 */
public class NotificationServerException extends Exception {

    public NotificationServerException() {
        super();
    }

    public NotificationServerException(String message) {
        super(message);
    }

    public NotificationServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationServerException(Throwable cause) {
        super(cause);
    }

}

