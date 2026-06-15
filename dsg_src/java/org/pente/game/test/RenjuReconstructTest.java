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
}
