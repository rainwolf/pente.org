package org.pente.game;

/**
 * The Renju (re)join contract, as a pure encode/decode pair.
 *
 * On a mid-opening (re)join the server sends ONE signal for the CURRENT
 * decision point. Reaching numMoves=N implies windows 1..N-1 are already
 * resolved, so only the current window is in question. The authoritative seats
 * are conveyed separately by sendPlayingPlayers; this signal carries only the
 * PHASE the client must reconstruct.
 *
 * Signal table (numMoves -> signal -> client phase), with {@link RejoinKind}:
 *   n=1..3 : NONE        -> SWAP (window open)        ; SILENT_SWAP -> MOVE (window resolved)
 *   n=4    : NONE        -> SWAP (window 4 open)
 *            SILENT_SWAP -> swapValue ? MOVE (took over -> Branch A auto-committed)
 *                                     : BRANCH (declined -> A-vs-B choice pending)
 *            OFFERS      -> SELECTION (Branch B)
 *   n=5    : NONE        -> SWAP (Branch A swap-5)     ; SILENT_SWAP -> MOVE (Branch A resolved)
 *                                                       ; SELECT1     -> MOVE (Branch B selected)
 *   n>=6   : NONE        -> COMPLETE (normal play)
 *
 * Within each n the kinds are distinct -- and at n=4 the SILENT_SWAP signal
 * additionally carries swapValue -- so (n, signal) is injective and
 * {@link #decode} recovers exactly the {@link RenjuOpeningPhase} the server
 * holds for every REJOIN-reachable (persisted-between-events) opening state:
 * decode(numMoves, encode(state)) == state.getOpeningPhase().
 *
 * SCOPE -- rejoin-reachable states only. A taken swap at window 4 auto-commits
 * Branch A (getOpeningPhase()==MOVE) and IS rejoin-observable (the swap-seats
 * event is sent, then the swapped-in side must play move 5); it round-trips via
 * SILENT_SWAP with swapValue==true. By contrast two transient INTRA-handler
 * states on the DECLINE path at numMoves=4 report MOVE yet encode to SILENT_SWAP
 * with swapValue==false (which decodes to BRANCH), so they do NOT round-trip:
 *   (a) Branch A chosen but move 5 not yet placed (chooseBranch(false) before
 *       its bundled addMove), and
 *   (b) Branch B chosen but the ten offers not yet complete (chooseBranch(true)
 *       before offerFifthMoves).
 * Neither is rejoin-observable: ServerTable.handleRenjuSwap commits
 * chooseBranch(false)+move5 atomically, and handleRenjuOffer10 commits
 * chooseBranch(true)+offerFifthMoves atomically, each inside ONE serialized
 * (SynchronizedServerTable) handler -- so no network event is ever emitted
 * between a branch choice and its follow-up. These pre-commit states are
 * therefore intentionally OUT of the encode/decode contract; the exclusion is
 * pinned by RenjuReconstructTest.
 *
 * {@link #encode} and {@link #decode} are pure (no mutation) so the server -
 * client reconstruction equivalence is unit-testable without any networking.
 */
public final class RenjuRejoin {

    private RenjuRejoin() {
    }

    public enum RejoinKind {
        /** Nothing sent: the current swap window is still open, or the opening is complete. */
        NONE,
        /** A silent DSGSwapSeatsTableEvent: the current swap window was resolved. */
        SILENT_SWAP,
        /** The ten Branch-B 5th-move offers: white must still select one. */
        OFFERS,
        /** A replayed select1: Branch B's move 5 was already chosen. */
        SELECT1
    }

    /** The single signal sent to a (re)joining client for the current decision point. */
    public static final class RejoinSignal {
        public final RejoinKind kind;
        /**
         * Only meaningful for SILENT_SWAP: the CURRENT window's resolved swap
         * decision (true = the to-move side took over that window). This is a
         * per-window phase-marker datum, NOT the net seat orientation. {@link
         * RenjuRejoin#decode} consults it only at numMoves==4, to tell a Branch-A
         * takeover (MOVE) from a declined window (BRANCH); the client must still
         * NOT derive who-owns-black from it. Seats are authoritative only via
         * sendPlayingPlayers.
         */
        public final boolean swapValue;
        /** Only meaningful for SELECT1: the board point selected as move 5. */
        public final int move;

        public RejoinSignal(RejoinKind kind, boolean swapValue, int move) {
            this.kind = kind;
            this.swapValue = swapValue;
            this.move = move;
        }

        public String toString() {
            return "RejoinSignal{" + kind
                    + (kind == RejoinKind.SILENT_SWAP ? ", swap=" + swapValue : "")
                    + (kind == RejoinKind.SELECT1 ? ", move=" + move : "") + "}";
        }
    }

    /**
     * SERVER side: derive the single rejoin signal for the current decision point
     * from the state's phase + numMoves. Pure (no mutation).
     *
     *   SWAP / COMPLETE       -> NONE        (the open window vs normal play is told
     *                                         apart by numMoves on decode)
     *   SELECTION             -> OFFERS      (re-send the ten Branch-B candidates)
     *   BRANCH                -> SILENT_SWAP (window 4 resolved; swapValue = its decision)
     *   MOVE, n==5, Branch B  -> SELECT1     (move 5 already chosen; carries the point)
     *   MOVE, otherwise       -> SILENT_SWAP (a swap window was resolved; carries its
     *                                         decision at numMoves)
     */
    public static RejoinSignal encode(RenjuState state) {
        int n = state.getNumMoves();
        switch (state.getOpeningPhase()) {
            case SWAP:
            case COMPLETE:
                return new RejoinSignal(RejoinKind.NONE, false, -1);
            case SELECTION:
                return new RejoinSignal(RejoinKind.OFFERS, false, -1);
            case BRANCH:
                return new RejoinSignal(RejoinKind.SILENT_SWAP, state.getSwapDecisionAt(n), -1);
            case MOVE:
            default:
                if (n == 5 && state.isBranchOffer()) {
                    return new RejoinSignal(RejoinKind.SELECT1, false, state.getSelectedFifthMove());
                }
                return new RejoinSignal(RejoinKind.SILENT_SWAP, state.getSwapDecisionAt(n), -1);
        }
    }

    /**
     * CLIENT side: reconstruct the server's {@link RenjuOpeningPhase} from only
     * (numMoves, signal). Mirrors {@link #encode}; pure.
     *
     *   NONE        -> SWAP if numMoves <= 5 (a window is still open), else COMPLETE
     *   SILENT_SWAP -> at numMoves == 4: TAKEOVER (swapValue == true) auto-commits
     *                  Branch A -> MOVE (place move 5); DECLINE -> BRANCH (A-vs-B
     *                  choice pending). At other numMoves -> MOVE.
     *   OFFERS      -> SELECTION
     *   SELECT1     -> MOVE
     */
    public static RenjuOpeningPhase decode(int numMoves, RejoinSignal signal) {
        switch (signal.kind) {
            case NONE:
                return numMoves <= 5 ? RenjuOpeningPhase.SWAP : RenjuOpeningPhase.COMPLETE;
            case SILENT_SWAP:
                if (numMoves == 4) {
                    // A taken swap at move 4 IS one of the two Branch-A outcomes,
                    // so it resolves the branch: next is move 5 (MOVE). A declined
                    // window 4 leaves the A-vs-B (offer-ten) choice pending (BRANCH).
                    return signal.swapValue ? RenjuOpeningPhase.MOVE : RenjuOpeningPhase.BRANCH;
                }
                return RenjuOpeningPhase.MOVE;
            case OFFERS:
                return RenjuOpeningPhase.SELECTION;
            case SELECT1:
            default:
                return RenjuOpeningPhase.MOVE;
        }
    }
}
