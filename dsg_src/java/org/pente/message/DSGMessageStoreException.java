package org.pente.message;

public class DSGMessageStoreException extends Exception {

    private static final long serialVersionUID = 32098722100862366L;

    public DSGMessageStoreException() {
        super();
    }

    public DSGMessageStoreException(String message) {
        super(message);
    }

    public DSGMessageStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public DSGMessageStoreException(Throwable cause) {
        super(cause);
    }

}
