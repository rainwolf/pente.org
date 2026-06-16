package org.pente.game;

/**
 * The decision point a Renju (Taraguchi-10) game is currently sitting at.
 *
 * This is the SERVER's authoritative opening state, computed from
 * {@link RenjuState}'s existing predicates. A (re)joining client reconstructs
 * the SAME value from only (numMoves, rejoin-signal) via
 * {@link RenjuRejoin#decode(int, RenjuRejoin.RejoinSignal)} — see RenjuRejoin
 * for the encode/decode contract.
 *
 *   SWAP      — a swap window is open (the to-move side may swap or decline).
 *   BRANCH    — black must pick Branch A vs Branch B (move-4 window resolved).
 *   SELECTION — Branch B: the ten 5th-move offers are in, white must pick one.
 *   MOVE      — no opening decision is pending; the to-move side places a stone.
 *   COMPLETE  — the six-stone opening is finished; normal play.
 */
public enum RenjuOpeningPhase {
    SWAP,
    BRANCH,
    SELECTION,
    MOVE,
    COMPLETE
}
