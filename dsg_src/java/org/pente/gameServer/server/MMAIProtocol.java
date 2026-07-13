package org.pente.gameServer.server;

import java.util.List;

import org.pente.game.GridStateFactory;

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
        return game == GridStateFactory.CONNECT6
            || game == GridStateFactory.SPEED_CONNECT6;
    }

    /** Which player (1 or 2) plays the stone at 0-based index {@code moveNum}.
     *  Standard games strictly alternate (P1, P2, P1, P2, ...); Connect6 places
     *  one opening stone then two stones per turn, so ownership runs
     *  P1, P2, P2, P1, P1, P2, P2, P1, P1, ... — the moveNum%4 in {0,3} => P1
     *  pattern. ThreadedAIPlayer gates its turn on this so the Connect6 second
     *  stone is requested during the AI's own turn instead of being blocked by
     *  strict one-stone alternation (spec §6.3). For every non-Connect6 game
     *  this is byte-identical to the legacy {@code moveNum % 2 + 1}. */
    public static int moveOwner(int game, int moveNum) {
        if (isConnect6(game)) {
            int r = moveNum % 4;
            return (r == 0 || r == 3) ? 1 : 2;
        }
        return moveNum % 2 + 1;
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
    /** All methods are synchronized: the AIPlayerThread consumes/accepts while
     *  the controller thread may clear() concurrently from stopThinking()/
     *  undoMove() (spec §6.5). A single monitor makes consume() atomic so a
     *  clear() cannot interleave between a read and the reset. */
    public static class PendingMove {
        private int pending = -1;

        public synchronized boolean hasPending() {
            return pending != -1;
        }

        /** Returns the cached stone (or -1 if none) and clears the cache. */
        public synchronized int consume() {
            int m = pending;
            pending = -1;
            return m;
        }

        /** Decode a packed reply: cache m2 unless it is the single-stone
         *  sentinel, return m1. */
        public synchronized int acceptPacked(int packed) {
            int m1 = packed / C6_BASE;
            int m2 = packed % C6_BASE;
            if (m2 != C6_SINGLE_STONE) {
                pending = m2;
            }
            return m1;
        }

        /** Stale after undoMove()/stopThinking() — drop it (spec §6.2). */
        public synchronized void clear() {
            pending = -1;
        }
    }
}
