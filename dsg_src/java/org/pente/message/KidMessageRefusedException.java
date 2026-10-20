package org.pente.message;

/** A private message the kid chat rule refuses. */
public class KidMessageRefusedException extends DSGMessageStoreException {

    private static final long serialVersionUID = 1L;

    public KidMessageRefusedException(String message) {
        super(message);
    }
}
