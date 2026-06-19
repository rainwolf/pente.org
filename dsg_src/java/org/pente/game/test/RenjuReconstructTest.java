package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuReconstructTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuReconstructTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuReconstructTest.class);
    }

    public RenjuReconstructTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15; // 15x15 Renju board move encoding
    }

    // Build a SimpleGridState move list (MoveData) from raw moves.
    private SimpleGridState moves(int... mv) {
        SimpleGridState s = new SimpleGridState(15, 15);
        for (int m : mv) s.addMove(m);
        return s;
    }

    // Reference: play the same opening live and compare board + pending state.
    public void testReconstructMidOpening_pendingSwapAfterMove1() {
        SimpleGridState md = moves(xy(7, 7)); // only move 1 played
        RenjuOpeningState st = new RenjuOpeningState(); // all pending
        RenjuState s = RenjuState.reconstruct(md, st.encode(), null);

        assertEquals(1, s.getNumMoves());
        assertTrue(s.isAwaitingSwapDecision()); // swap after move 1 still pending
        assertTrue(!s.isOpeningComplete());
    }

    public void testReconstructBranchA_full() {
        // moves: 1..4 opening, move5 (9x9), move6 anywhere
        SimpleGridState md = moves(
                xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8), xy(11, 7), xy(0, 0));
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.NO;  // Branch A
        st.swap5 = RenjuOpeningState.NO;
        RenjuState s = RenjuState.reconstruct(md, st.encode(), null);

        assertEquals(6, s.getNumMoves());
        assertTrue(s.isOpeningComplete());
    }

    public void testReconstructBranchB_full() {
        int[] offers = {xy(0,0), xy(0,2), xy(0,4), xy(0,6), xy(0,8),
                        xy(0,10), xy(0,12), xy(0,14), xy(2,0), xy(4,0)};
        // moves: 1..4 opening, then the selected 5th (offers[3]), then move6
        SimpleGridState md = moves(
                xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8), offers[3], xy(14, 14));
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES; // Branch B
        RenjuState s = RenjuState.reconstruct(md, st.encode(), offers);

        assertEquals(6, s.getNumMoves());
        assertTrue(s.isOpeningComplete());
    }

    public void testReconstructBranchB_awaitingSelection() {
        int[] offers = {xy(0,0), xy(0,2), xy(0,4), xy(0,6), xy(0,8),
                        xy(0,10), xy(0,12), xy(0,14), xy(2,0), xy(4,0)};
        SimpleGridState md = moves(xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8)); // 4 moves
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(md, st.encode(), offers);

        assertEquals(4, s.getNumMoves());
        assertTrue(s.isAwaitingFifthSelection());
    }

    public void testGetRenjuSwapsPackedEncodesResolvedDecisions() {
        // Branch A opening: decline swaps 1-4, branch A, decline swap 5.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));  s.renjuSwapDecisionMade(false);   // swap1 = NO
        s.addMove(xy(8, 8));  s.renjuSwapDecisionMade(false);   // swap2 = NO
        s.addMove(xy(9, 7));  s.renjuSwapDecisionMade(false);   // swap3 = NO
        s.addMove(xy(6, 8));  s.renjuSwapDecisionMade(false);   // swap4 = NO
        s.chooseBranch(false);                                  // Branch A
        s.addMove(xy(11, 7)); s.renjuSwapDecisionMade(true);    // swap5 = YES

        RenjuOpeningState st = RenjuOpeningState.decode(s.getRenjuSwapsPacked());
        assertEquals(RenjuOpeningState.NO,  st.swap1);
        assertEquals(RenjuOpeningState.NO,  st.swap2);
        assertEquals(RenjuOpeningState.NO,  st.swap3);
        assertEquals(RenjuOpeningState.NO,  st.swap4);
        assertEquals(RenjuOpeningState.NO,  st.branch);   // Branch A -> NO
        assertEquals(RenjuOpeningState.YES, st.swap5);
    }

    public void testWouldAcceptDeclinedOpeningMove_move1Window() {
        // After move 1, white is in the swap-1 window.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));
        assertTrue(s.isAwaitingSwapDecision());

        // A legal in-box move 2 (within the 3x3 around center) accepts;
        // an already-occupied point is rejected.
        assertTrue(s.wouldAcceptDeclinedOpeningMove(xy(8, 8)));
        assertTrue(!s.wouldAcceptDeclinedOpeningMove(xy(7, 7))); // occupied

        // Pure check: nothing was mutated.
        assertTrue(s.isAwaitingSwapDecision());
        assertEquals(1, s.getNumMoves());
    }

    public void testWouldAcceptDeclinedOpeningMove_move4Window_branchA() {
        // Drive declines through the move-4 swap window (Branch A continuation).
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(8, 8)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(9, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(6, 8));               // n == 4, swap-4 window open
        assertTrue(s.isAwaitingSwapDecision());

        // A legal move 5 (within the 9x9) accepts; an occupied point is rejected.
        assertTrue(s.wouldAcceptDeclinedOpeningMove(xy(11, 7)));
        assertTrue(!s.wouldAcceptDeclinedOpeningMove(xy(7, 7))); // occupied

        // Pure check: swap window + branch flags untouched.
        assertTrue(s.isAwaitingSwapDecision());
        assertTrue(!s.isAwaitingBranchChoice());
        assertEquals(4, s.getNumMoves());
    }

    public void testWouldAcceptDeclinedOpeningMove_postSwapBranchChoice_branchA() {
        // Drive to the move-4 swap window, then ACCEPT the swap. The engine clears the
        // swap window and enters the branch-choice state (n == 4, awaitingSwap = false):
        // the to-move side (black) must now pick Branch A by playing move 5 in the 9x9.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(8, 8)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(9, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(6, 8));                 // n == 4, swap-4 window open
        s.renjuSwapDecisionMade(true);       // ACCEPT the swap -> branch-choice state
        assertTrue(!s.isAwaitingSwapDecision());
        assertTrue(s.isAwaitingBranchChoice());

        // A legal Branch-A move 5 (within the 9x9) accepts; an occupied point rejects.
        assertTrue(s.wouldAcceptDeclinedOpeningMove(xy(11, 7)));
        assertTrue(!s.wouldAcceptDeclinedOpeningMove(xy(7, 7))); // occupied

        // Pure check: nothing mutated -- still awaiting the branch choice, no stone
        // committed, branch not actually chosen.
        assertTrue(s.isAwaitingBranchChoice());
        assertEquals(4, s.getNumMoves());
    }

    public void testWouldAcceptDeclinedOpeningMove_falseWhenNotAwaitingSwap() {
        RenjuState s = new RenjuState(15, 15);
        assertTrue(!s.wouldAcceptDeclinedOpeningMove(xy(7, 7)));
    }

    public void testGetRenjuSwapsPackedLeavesUnresolvedPending() {
        // Only move 1 placed, swap1 not yet decided -> all PENDING.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));
        RenjuOpeningState st = RenjuOpeningState.decode(s.getRenjuSwapsPacked());
        assertEquals(RenjuOpeningState.PENDING, st.swap1);
        assertEquals(RenjuOpeningState.PENDING, st.swap2);
        assertEquals(RenjuOpeningState.PENDING, st.swap5);
        assertEquals(RenjuOpeningState.PENDING, st.branch);
    }

    // Builds a Branch-B position: 4 opening moves with swaps declined, branch B chosen.
    private RenjuState branchBAtFour() {
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));  s.renjuSwapDecisionMade(false);   // after move 1
        s.addMove(xy(8, 8));  s.renjuSwapDecisionMade(false);   // after move 2
        s.addMove(xy(9, 7));  s.renjuSwapDecisionMade(false);   // after move 3
        s.addMove(xy(6, 8));  s.renjuSwapDecisionMade(false);   // after move 4 (swap4)
        s.chooseBranch(true);                                   // Branch B
        return s;
    }

    public void testOfferFifthMovesRejectsWrongCount() {
        RenjuState s = branchBAtFour();
        try {
            s.offerFifthMoves(new int[]{ xy(10, 10) });   // only 1, needs 10
            fail("expected rejection for wrong offer count");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue("no offers may be committed on rejection",
                s.getOfferedFifthMoves().isEmpty());
        assertTrue("engine must still accept the ten offers",
                s.isAwaitingFifthOffers());
    }

    public void testOfferFifthMovesRollsBackOnOccupiedPoint() {
        RenjuState s = branchBAtFour();
        // Nine VALID candidates, then move 1's occupied point as the 10th. The loop
        // commits the first nine to offeredFifth, then offerFifthMove throws on the
        // occupied 10th -> this genuinely exercises the partial-rollback path
        // (offeredFifth.clear() + addAll(snapshot) over a NON-empty accumulation),
        // not just the "throws before anything is added" case.
        int[] bad = new int[]{
                xy(10, 10), xy(11, 10), xy(12, 10), xy(13, 10), xy(10, 11),
                xy(11, 11), xy(12, 11), xy(13, 11), xy(10, 12),
                xy(7, 7)   // occupied (move 1) -> throws after nine offers were added
        };
        try {
            s.offerFifthMoves(bad);
            fail("expected rejection for occupied offer point");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue("a rejected batch must roll back the nine already-added offers",
                s.getOfferedFifthMoves().isEmpty());
        assertTrue(s.isAwaitingFifthOffers());
    }

    // ----- reconstruction equivalence (server phase <-> client rejoin signal) -----
    //
    // The deliverable: drive RenjuState through every REJOIN-reachable (persisted-
    // between-events) opening state and, for each, assert that the client's
    // reconstruction -- decode(numMoves, encode(state)) -- yields the SAME
    // RenjuOpeningPhase the server holds (getOpeningPhase()), from only (numMoves,
    // signal). This proves server<->client reconstruction equivalence across the whole
    // Taraguchi-10 opening. Also asserts getOpeningPhase() itself per state.
    //
    // SCOPE -- rejoin-reachable states. Two transient INTRA-handler states at n==4
    // (Branch A chosen before move 5; Branch B chosen before the ten offers) report
    // getOpeningPhase()==MOVE but encode to SILENT_SWAP (-> BRANCH), so they do NOT
    // round-trip. They are never persisted between network events (handleRenjuSwap
    // commits chooseBranch(false)+move5 atomically; handleRenjuOffer10 commits
    // chooseBranch(true)+offerFifthMoves atomically, each in one serialized handler),
    // so they are not rejoin-observable. testTransientPreCommitStatesAtFour_* pins
    // that they are intentionally out of contract.

    // The four shared opening stones (same as the other tests in this file).
    private static final int M1 = 7  + 7  * 15;  // xy(7,7) center
    private static final int M2 = 8  + 8  * 15;  // xy(8,8)
    private static final int M3 = 9  + 7  * 15;  // xy(9,7)
    private static final int M4 = 6  + 8  * 15;  // xy(6,8)
    private static final int M5A = 11 + 7 * 15;  // xy(11,7) Branch A move 5 (in 9x9)
    private static final int M6 = 0;             // xy(0,0) any empty point

    // Ten valid Branch-B 5th-move offers for the {M1..M4} position (reused from the
    // existing Branch-B reconstruct tests, where these are accepted by offerFifthMove).
    private int[] tenOffers() {
        return new int[]{xy(0,0), xy(0,2), xy(0,4), xy(0,6), xy(0,8),
                         xy(0,10), xy(0,12), xy(0,14), xy(2,0), xy(4,0)};
    }

    // Assert BOTH (a) the server phase equals expected, and (b) the client reconstructs
    // that exact phase from only (numMoves, encode(state)).
    private void assertReconstructs(RenjuState s, RenjuOpeningPhase expected) {
        assertEquals("server getOpeningPhase() @ n=" + s.getNumMoves(),
                expected, s.getOpeningPhase());
        RenjuRejoin.RejoinSignal sig = RenjuRejoin.encode(s);
        RenjuOpeningPhase reconstructed = RenjuRejoin.decode(s.getNumMoves(), sig);
        assertEquals("decode(numMoves, encode(state)) must equal the server phase"
                        + " @ n=" + s.getNumMoves() + " sig=" + sig,
                s.getOpeningPhase(), reconstructed);
    }

    // Build a RenjuState with moves 1..k placed and windows 1..(k-1) declined, leaving
    // window k OPEN (SWAP pending). k in 1..4.
    private RenjuState swapWindowOpen(int k) {
        RenjuState s = new RenjuState(15, 15);
        int[] m = {M1, M2, M3, M4};
        for (int i = 0; i < k; i++) {
            s.addMove(m[i]);
            if (i < k - 1) s.renjuSwapDecisionMade(false); // resolve earlier windows
        }
        return s;
    }

    public void testReconstruct_swapWindowsPending() {
        for (int k = 1; k <= 4; k++) {
            assertReconstructs(swapWindowOpen(k), RenjuOpeningPhase.SWAP);
        }
    }

    public void testReconstruct_swapWindows1to3Resolved_declineAndTakeover() {
        // Windows 1..3 resolved -> MOVE (place the next opening stone). Window 4 is
        // covered separately because resolving it yields BRANCH, not MOVE.
        for (int k = 1; k <= 3; k++) {
            RenjuState decline = swapWindowOpen(k);
            decline.renjuSwapDecisionMade(false);
            assertReconstructs(decline, RenjuOpeningPhase.MOVE);

            RenjuState takeover = swapWindowOpen(k);
            takeover.renjuSwapDecisionMade(true);   // engine allows take-over at any open window
            assertReconstructs(takeover, RenjuOpeningPhase.MOVE);
        }
    }

    public void testReconstruct_window4Resolved_isBranch_declineAndTakeover() {
        RenjuState decline = swapWindowOpen(4);
        decline.renjuSwapDecisionMade(false);       // window 4 declined -> branch choice
        assertReconstructs(decline, RenjuOpeningPhase.BRANCH);

        RenjuState takeover = swapWindowOpen(4);
        takeover.renjuSwapDecisionMade(true);        // window 4 take-over -> branch choice
        assertReconstructs(takeover, RenjuOpeningPhase.BRANCH);
    }

    // Branch A: window 4 resolved, Branch A chosen, move 5 placed -> swap-5 window OPEN.
    private RenjuState branchAAtFiveSwapOpen() {
        RenjuState s = swapWindowOpen(4);
        s.renjuSwapDecisionMade(false);  // resolve window 4 -> BRANCH
        s.chooseBranch(false);           // Branch A
        s.addMove(M5A);                  // move 5 in the 9x9 -> swap-5 window opens
        return s;
    }

    public void testReconstruct_branchA_swap5Pending() {
        assertReconstructs(branchAAtFiveSwapOpen(), RenjuOpeningPhase.SWAP);
    }

    public void testReconstruct_branchA_swap5Resolved_declineAndTakeover() {
        RenjuState decline = branchAAtFiveSwapOpen();
        decline.renjuSwapDecisionMade(false);   // swap-5 declined -> place move 6
        assertReconstructs(decline, RenjuOpeningPhase.MOVE);

        RenjuState takeover = branchAAtFiveSwapOpen();
        takeover.renjuSwapDecisionMade(true);   // swap-5 take-over -> place move 6
        assertReconstructs(takeover, RenjuOpeningPhase.MOVE);
    }

    public void testReconstruct_branchA_complete() {
        RenjuState s = branchAAtFiveSwapOpen();
        s.renjuSwapDecisionMade(false);
        s.addMove(M6);                          // move 6 -> opening complete
        assertReconstructs(s, RenjuOpeningPhase.COMPLETE);
    }

    public void testReconstruct_branchB_selectionPending() {
        RenjuState s = branchBAtFour();
        s.offerFifthMoves(tenOffers());         // ten offered -> selection pending
        assertReconstructs(s, RenjuOpeningPhase.SELECTION);
    }

    public void testReconstruct_branchB_afterSelect_isMove() {
        RenjuState s = branchBAtFour();
        int[] offers = tenOffers();
        s.offerFifthMoves(offers);
        s.selectFifthMove(offers[3]);           // commit move 5 -> place move 6 next
        assertEquals(5, s.getNumMoves());
        assertReconstructs(s, RenjuOpeningPhase.MOVE);
        // The Branch-B move-5 signal carries the selected point so it can be replayed.
        RenjuRejoin.RejoinSignal sig = RenjuRejoin.encode(s);
        assertEquals(RenjuRejoin.RejoinKind.SELECT1, sig.kind);
        assertEquals(offers[3], sig.move);
    }

    public void testReconstruct_branchB_complete() {
        RenjuState s = branchBAtFour();
        int[] offers = tenOffers();
        s.offerFifthMoves(offers);
        s.selectFifthMove(offers[3]);
        s.addMove(M6);                          // move 6 -> opening complete
        assertReconstructs(s, RenjuOpeningPhase.COMPLETE);
    }

    // The initial board (move 1 not yet placed) is a MOVE state that round-trips
    // cleanly. In production the centre is auto-placed as move 1, so n==0 is not a
    // live rejoin state, but the encode/decode contract still holds for it.
    public void testReconstruct_initialBoard_isMove() {
        assertReconstructs(new RenjuState(15, 15), RenjuOpeningPhase.MOVE);
        assertEquals(0, new RenjuState(15, 15).getNumMoves());
    }

    // The two transient INTRA-handler states at n==4 that are intentionally OUT of the
    // encode/decode contract: getOpeningPhase()==MOVE, but the rejoin signal encodes to
    // SILENT_SWAP, which decodes to BRANCH at n==4. These never persist between network
    // events (handleRenjuSwap commits chooseBranch(false)+move5 atomically;
    // handleRenjuOffer10 commits chooseBranch(true)+offerFifthMoves atomically, each in
    // one serialized handler), so they are not rejoin-observable. This PINS the
    // documented mismatch -- it deliberately does NOT use assertReconstructs.
    public void testTransientPreCommitStatesAtFour_areOutOfContract() {
        // (b) Branch B chosen, the ten offers not yet complete.
        RenjuState branchB = branchBAtFour();
        assertEquals(RenjuOpeningPhase.MOVE, branchB.getOpeningPhase());
        RenjuRejoin.RejoinSignal sigB = RenjuRejoin.encode(branchB);
        assertEquals(RenjuRejoin.RejoinKind.SILENT_SWAP, sigB.kind);
        assertEquals("transient Branch-B offers-pending is out of contract",
                RenjuOpeningPhase.BRANCH,
                RenjuRejoin.decode(branchB.getNumMoves(), sigB)); // != getOpeningPhase()

        // (a) Branch A chosen, move 5 not yet placed.
        RenjuState branchA = swapWindowOpen(4);
        branchA.renjuSwapDecisionMade(false); // window 4 resolved -> BRANCH
        branchA.chooseBranch(false);          // Branch A chosen, move 5 not yet placed
        assertEquals(RenjuOpeningPhase.MOVE, branchA.getOpeningPhase());
        RenjuRejoin.RejoinSignal sigA = RenjuRejoin.encode(branchA);
        assertEquals(RenjuRejoin.RejoinKind.SILENT_SWAP, sigA.kind);
        assertEquals("transient Branch-A pre-move-5 is out of contract",
                RenjuOpeningPhase.BRANCH,
                RenjuRejoin.decode(branchA.getNumMoves(), sigA)); // != getOpeningPhase()
    }

    // Spot-check the signal KIND for a couple of phases, so a regression in the
    // encode mapping (not just the decode round-trip) is caught.
    public void testReconstruct_signalKinds() {
        assertEquals(RenjuRejoin.RejoinKind.NONE,
                RenjuRejoin.encode(swapWindowOpen(2)).kind);          // SWAP pending
        RenjuState resolved = swapWindowOpen(2);
        resolved.renjuSwapDecisionMade(true);
        assertEquals(RenjuRejoin.RejoinKind.SILENT_SWAP,
                RenjuRejoin.encode(resolved).kind);                  // MOVE after resolve
        assertTrue("take-over swap value carried", RenjuRejoin.encode(resolved).swapValue);
        RenjuState branchB = branchBAtFour();
        branchB.offerFifthMoves(tenOffers());
        assertEquals(RenjuRejoin.RejoinKind.OFFERS,
                RenjuRejoin.encode(branchB).kind);                   // SELECTION
    }
}
