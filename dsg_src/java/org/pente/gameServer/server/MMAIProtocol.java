package org.pente.gameServer.server;

import java.util.List;

/** Wire protocol helpers for the mmai_player sidecar (spec §5.2) plus the
 *  Connect6 second-stone cache used by {@link MMAIPlayer} (spec §6.3).
 *  Pure logic — no process, no I/O — so it is unit-testable in isolation. */
public class MMAIProtocol {

    /** Connect6 replies pack a whole two-stone turn as m1*362 + m2. */
    public static final int C6_BASE = 362;
    /** m2 == 361 means the turn placed only one stone (the opening turn). */
    public static final int C6_SINGLE_STONE = 361;

    /** Format: {@code MOVE <game> <level> <n> <m1> ... <mn>} (no newline). */
    public static String encodeMoveRequest(int game, int level, List<Integer> moves) {
        StringBuilder sb = new StringBuilder(16 + moves.size() * 4);
        sb.append("MOVE ").append(game).append(' ').append(level)
          .append(' ').append(moves.size());
        for (Integer m : moves) {
            sb.append(' ').append(m.intValue());
        }
        return sb.toString();
    }

    /** Parse {@code OK <v>} to v. Anything else (ERR, null/EOF, garbage,
     *  extra tokens) throws — callers treat that as an engine failure. */
    public static int parseOkReply(String line) throws ProtocolException {
        if (line == null) {
            throw new ProtocolException("EOF from sidecar (no reply line)");
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 2 && "OK".equals(parts[0])) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ProtocolException("unparseable OK reply: " + line);
            }
        }
        throw new ProtocolException("sidecar reply not OK: " + line);
    }

    /** Connect6 canonical id 13, Speed twin 14. */
    public static boolean isConnect6(int game) {
        return game == 13 || game == 14;
    }

    public static class ProtocolException extends Exception {
        public ProtocolException(String message) {
            super(message);
        }
    }

    /** Connect6 second-stone cache: the engine returns a whole two-stone turn
     *  packed base 362, but the AIPlayer contract returns one stone per
     *  getMove(). The second stone is cached here and served on the next
     *  getMove() without a sidecar round-trip. */
    public static class PendingMove {
        private int pending = -1;

        public boolean hasPending() {
            return pending != -1;
        }

        /** Returns the cached stone (or -1 if none) and clears the cache. */
        public int consume() {
            int m = pending;
            pending = -1;
            return m;
        }

        /** Decode a packed reply: cache m2 unless it is the single-stone
         *  sentinel, return m1. */
        public int acceptPacked(int packed) {
            int m1 = packed / C6_BASE;
            int m2 = packed % C6_BASE;
            if (m2 != C6_SINGLE_STONE) {
                pending = m2;
            }
            return m1;
        }

        /** Stale after undoMove()/stopThinking() — drop it (spec §6.2). */
        public void clear() {
            pending = -1;
        }
    }
}
