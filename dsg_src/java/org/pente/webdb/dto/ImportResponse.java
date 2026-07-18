package org.pente.webdb.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire response for {@code POST /api/db/collection/import} (auth).
 *
 * <pre>
 * {"imported":2,"duplicates":[1],"errors":[{"index":3,"message":"illegal move at 12"}]}
 * </pre>
 *
 * A single import request is processed per-game and never fails as a batch: a
 * game that cannot be stored (illegal move replay, or an unsupported variant)
 * is reported in {@link #errors} by its request {@code index}; a game already in
 * the caller's collection is reported in {@link #duplicates} by its index; every
 * other game is stored and counted in {@link #imported}.
 *
 * <p>Public fields, no getters/setters (Gson-friendly plain data carrier).
 */
public class ImportResponse {

    /** Number of games newly stored (excludes duplicates and errors). */
    public int imported;

    /** Request indices of games already present in the caller's collection. */
    public List<Integer> duplicates = new ArrayList<Integer>();

    /** Per-game failures, each carrying the request index and a reason. */
    public List<Error> errors = new ArrayList<Error>();

    /** One rejected game: its request {@code index} and why it was rejected. */
    public static class Error {

        public int index;
        public String message;

        public Error() {
        }

        public Error(int index, String message) {
            this.index = index;
            this.message = message;
        }
    }
}
