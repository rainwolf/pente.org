package org.pente.turnBased.web;

import org.pente.game.RenjuState;

/**
 * Pure (no I/O) resolver for the turn-based Renju opening wire contract.
 * Maps a (renjuAction, moves[], reconstructed RenjuState) request onto a
 * typed {@link Decision} describing the storer calls MoveServlet must make,
 * or throws {@link RenjuContractException} (whose message is the user-facing
 * error) when the request violates the contract. Single source of truth for
 * the three-action contract: swap / move(1|10) / select(2).
 */
public final class RenjuTbContract {

    private RenjuTbContract() {}

    /** A contract violation; the message is surfaced verbatim via handleError. */
    public static final class RenjuContractException extends Exception {
        private static final long serialVersionUID = 1L;
        public RenjuContractException(String message) { super(message); }
    }

    public enum Kind { TAKE_OVER, PLACE, BRANCH_A, BRANCH_B, SELECT }

    /** The resolved plan of mutations. {@code stones} contents depend on kind:
     *  TAKE_OVER: empty · PLACE: [stone] · BRANCH_A: [move5] ·
     *  BRANCH_B: [10 offers] · SELECT: [move5, move6]. */
    public static final class Decision {
        public final Kind kind;
        public final boolean declineSwap; // a pending swap window must be declined first
        public final int[] stones;
        public Decision(Kind kind, boolean declineSwap, int[] stones) {
            this.kind = kind; this.declineSwap = declineSwap; this.stones = stones;
        }
    }

    /**
     * Resolve a TB Renju opening request. Validates the action against the
     * pending phase and the payload against the engine's rules, WITHOUT
     * mutating anything. Throws RenjuContractException (message = user error)
     * on any violation; otherwise returns the plan of mutations.
     */
    public static Decision resolve(String action, int[] moves, RenjuState pending)
            throws RenjuContractException {

        if ("swap".equals(action)) {
            if (!pending.isAwaitingSwapDecision()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            return new Decision(Kind.TAKE_OVER, false, new int[0]);
        }

        if ("select".equals(action)) {
            if (!pending.isAwaitingFifthSelection()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            if (moves == null || moves.length != 2) {
                throw new RenjuContractException("Select requires the chosen 5th move and your 6th move.");
            }
            int m5 = moves[0], m6 = moves[1];
            if (!pending.getOfferedFifthMoves().contains(Integer.valueOf(m5))) {
                throw new RenjuContractException("Selected move was not offered.");
            }
            // move 6 (white): empty, in bounds, distinct from move 5. White has
            // no forbidden points, so no further restriction. Checked on the
            // 4-stone board (offers are not placed); the 9 unchosen offers are
            // discarded, so m6 may legally land on a former offer != m5.
            if (m6 == m5 || pending.isOutOfBounds(m6) || pending.getPosition(m6) != 0) {
                throw new RenjuContractException(
                        "Your 6th move must be an empty board point different from your 5th.");
            }
            return new Decision(Kind.SELECT, false, new int[]{ m5, m6 });
        }

        if ("move".equals(action)) {
            if (pending.isOpeningComplete() || pending.isAwaitingFifthSelection()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            boolean declineSwap = pending.isAwaitingSwapDecision();
            boolean branchPoint = pending.getNumMoves() == 4 && !pending.isBranchChosen();
            int n = (moves == null) ? 0 : moves.length;

            if (branchPoint) {
                if (n == 1) {
                    // Pre-validate the bundled move 5 read-only (no mutation on
                    // rejection): wouldAcceptDeclinedOpeningMove checks in-bounds,
                    // empty, current player, and the 9x9 central-square restriction.
                    // branchPoint is now the fresh-decline (swap pending) case only:
                    // a move-4 take-over auto-commits Branch A, so move 5 after a
                    // take-over is a plain PLACE (else-branch below), not a branch
                    // point. Mirrors Branch B's wouldAcceptFifthOffers.
                    if (!pending.wouldAcceptDeclinedOpeningMove(moves[0])) {
                        throw new RenjuContractException(
                                "Branch A move 5 must be an empty point inside the 9x9 center.");
                    }
                    return new Decision(Kind.BRANCH_A, declineSwap, new int[]{ moves[0] });
                } else if (n == 10) {
                    if (!pending.wouldAcceptFifthOffers(moves)) {
                        throw new RenjuContractException("Invalid 5th-move offer.");
                    }
                    int[] offers = new int[10];
                    System.arraycopy(moves, 0, offers, 0, 10);
                    return new Decision(Kind.BRANCH_B, declineSwap, offers);
                }
                throw new RenjuContractException(
                        "At the branch point place 1 stone (Branch A) or 10 (Branch B).");
            } else {
                if (n != 1) {
                    throw new RenjuContractException("Expected a single move.");
                }
                return new Decision(Kind.PLACE, declineSwap, new int[]{ moves[0] });
            }
        }

        throw new RenjuContractException("Unknown renju action.");
    }
}
