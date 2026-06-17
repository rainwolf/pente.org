package org.pente.turnBased.web;

import org.pente.game.RenjuState;
import org.pente.game.RenjuOpeningState;

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
}
