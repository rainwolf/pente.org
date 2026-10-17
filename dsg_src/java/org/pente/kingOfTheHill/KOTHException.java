package org.pente.kingOfTheHill;

/**
 * Created by waliedothman on 26/06/16.
 */

public class KOTHException extends Exception {

    private static final long serialVersionUID = 5496755159315593540L;

    public KOTHException() {
        super();
    }

    public KOTHException(String message) {
        super(message);
    }

    public KOTHException(String message, Throwable cause) {
        super(message, cause);
    }

    public KOTHException(Throwable cause) {
        super(cause);
    }

}
