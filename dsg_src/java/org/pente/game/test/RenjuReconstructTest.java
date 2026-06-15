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
}
