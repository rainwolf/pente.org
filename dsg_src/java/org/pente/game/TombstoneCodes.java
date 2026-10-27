package org.pente.game;

/**
 * Pure, stateless codec for Tombstone Pente stored move codes:
 * own stone 0..360 (x + 19*y), dead stone 361..721 (361 + point),
 * own pass 722, archive "no next move" sentinel 723.
 */
public final class TombstoneCodes {

    public static final int POINTS = 361;
    public static final int DEAD_OFFSET = 361;
    public static final int OWN_PASS = 722;
    public static final int ARCHIVE_NO_NEXT_MOVE = 723;
    public static final int DEAD_CELL = 3;

    private TombstoneCodes() { }

    public static boolean isOwnStone(int code) {
        return code >= 0 && code < POINTS;
    }

    public static boolean isOwnPass(int code) {
        return code == OWN_PASS;
    }

    public static boolean isOwnEntry(int code) {
        return isOwnStone(code) || isOwnPass(code);
    }

    public static boolean isDeadStone(int code) {
        return code >= DEAD_OFFSET && code < DEAD_OFFSET + POINTS;
    }

    public static int deadCode(int point) {
        return DEAD_OFFSET + point;
    }

    /** Board point of a stone or dead-stone code, or -1 for anything else. */
    public static int pointOf(int code) {
        if (isOwnStone(code)) return code;
        if (isDeadStone(code)) return code - DEAD_OFFSET;
        return -1;
    }

    public static int countOwnEntries(int[] codes, int n) {
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (isOwnEntry(codes[i])) count++;
        }
        return count;
    }

    public static int currentPlayer(int[] codes, int n) {
        return countOwnEntries(codes, n) % 2 + 1;
    }

    public static int lastTurnLength(int[] codes, int n) {
        if (n <= 1) return 0;
        return isDeadStone(codes[n - 1]) ? 2 : 1;
    }

    /** Null if the turn is well formed, else the error message. */
    public static String validateTurnShape(int[] turn) {
        if (turn == null || turn.length == 0 || turn.length > 2) {
            return "Invalid turn: send one or two moves.";
        }
        if (!isOwnEntry(turn[0])) {
            return "Invalid turn: the first move must be your stone or a pass.";
        }
        if (turn.length == 2 && !isDeadStone(turn[1])) {
            return "Invalid turn: the second move must be a dead stone.";
        }
        return null;
    }

    public static String coordinate(int point) {
        return PGNGameFormat.formatCoordinates(point);
    }

    public static String entryLabel(int code) {
        if (isOwnStone(code)) return coordinate(code);
        if (isOwnPass(code)) return "PASS";
        if (isDeadStone(code)) return "dead " + coordinate(code - DEAD_OFFSET);
        throw new IllegalArgumentException("not an entry: " + code);
    }

    public static String turnLabel(int ownCode, int deadCodeOrMinus1) {
        String s = entryLabel(ownCode);
        if (deadCodeOrMinus1 != -1) {
            if (!isDeadStone(deadCodeOrMinus1)) throw new IllegalArgumentException("not a dead stone: " + deadCodeOrMinus1);
            s += " - " + coordinate(deadCodeOrMinus1 - DEAD_OFFSET);
        }
        return s;
    }

    public static String pgnToken(int code) {
        if (isOwnStone(code)) return coordinate(code);
        if (isOwnPass(code)) return "pass";
        if (isDeadStone(code)) return "x" + coordinate(code - DEAD_OFFSET);
        throw new IllegalArgumentException("not an entry: " + code);
    }

    /** Parse a PGN token to a code, or -1 if it is not a Tombstone move token. */
    public static int parsePgnToken(String token) {
        if (token == null || token.length() == 0) return -1;
        if (token.equalsIgnoreCase("pass")) return OWN_PASS;
        char c = token.charAt(0);
        if (token.length() >= 3 && (c == 'x' || c == 'X')) {
            int p = parseCoord(token.substring(1));
            return p < 0 ? -1 : DEAD_OFFSET + p;
        }
        return parseCoord(token);
    }

    private static int parseCoord(String s) {
        int p;
        try {
            p = PGNGameFormat.parseCoordinates(s);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (p < 0 || p >= POINTS) return -1;
        if (!PGNGameFormat.formatCoordinates(p).equalsIgnoreCase(s)) return -1;
        return p;
    }

    /** The pente_move.next_move "no next move" sentinel for a game (spec 9.1). */
    public static int archiveNoNextMove(int game) {
        return GridStateFactory.isTombstone(game) ? ARCHIVE_NO_NEXT_MOVE : 361;
    }
}
