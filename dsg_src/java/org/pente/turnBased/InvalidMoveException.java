package org.pente.turnBased;

/**
 * Thrown when a submitted move is rejected by the game rules (illegal placement,
 * out-of-square opening move, Renju forbidden point, etc.) — as opposed to a
 * genuine storage/DB failure. Lets the servlet show a clean "invalid move"
 * message instead of a generic database error.
 */
public class InvalidMoveException extends TBStoreException {

    private static final long serialVersionUID = -551809551323851413L;

    public InvalidMoveException(String message) {
        super(message);
    }
}
