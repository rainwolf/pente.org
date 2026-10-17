package org.pente.turnBased;

public class TBStoreException extends Exception {

    private static final long serialVersionUID = 2529690993071924489L;

    public TBStoreException() {
        super();
    }

    public TBStoreException(String message) {
        super(message);
    }

    public TBStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public TBStoreException(Throwable cause) {
        super(cause);
    }

}
