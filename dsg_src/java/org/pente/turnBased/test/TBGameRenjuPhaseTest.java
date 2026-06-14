package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.game.GridStateFactory;
import org.pente.game.RenjuOpeningState;
import org.pente.turnBased.TBGame;

public class TBGameRenjuPhaseTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{TBGameRenjuPhaseTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGameRenjuPhaseTest.class);
    }

    public TBGameRenjuPhaseTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    private TBGame renju(int[] moves, int swaps, int[] offers) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_RENJU);
        List<Integer> list = new ArrayList<Integer>();
        for (int m : moves) list.add(m);
        g.setMoves(list);
        g.setRenjuSwaps(swaps);
        g.setRenjuOffers(offers);
        return g;
    }

    public void testNonRenjuReturnsNull() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        assertNull(g.getRenjuPhase());
    }

    public void testSwapPendingAfterMove1() {
        TBGame g = renju(new int[]{xy(7, 7)}, 0, null); // 1 move, all pending
        assertEquals(TBGame.RENJU_SWAP, g.getRenjuPhase());
    }

    public void testMovePhaseAfterSwapResolved() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO; // move-1 swap declined
        TBGame g = renju(new int[]{xy(7, 7)}, st.encode(), null);
        assertEquals(TBGame.RENJU_MOVE, g.getRenjuPhase()); // awaiting move 2
    }

    public void testBranchPendingAfterMove4() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), null);
        assertEquals(TBGame.RENJU_BRANCH, g.getRenjuPhase());
    }

    public void testOffersPendingBranchB() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), null);
        assertEquals(TBGame.RENJU_OFFERS, g.getRenjuPhase());
    }

    public void testSelectionPendingBranchB() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        int[] offers = {0,2,4,6,8,10,12,14,16,18};
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), offers);
        assertEquals(TBGame.RENJU_SELECTION, g.getRenjuPhase());
    }

    public void testCompleteAfterMove6BranchA() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.NO; // Branch A
        st.swap5 = RenjuOpeningState.NO;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8), xy(11,7), xy(0,0)},
                st.encode(), null);
        assertEquals(TBGame.RENJU_COMPLETE, g.getRenjuPhase());
    }
}
