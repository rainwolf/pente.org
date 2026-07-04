package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class SeatsSwappedGridStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                SeatsSwappedGridStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(SeatsSwappedGridStateTest.class);
    }

    public SeatsSwappedGridStateTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15; // 15x15 Renju board move encoding
    }

    private SimpleGridState moves(int... mv) {
        SimpleGridState s = new SimpleGridState(15, 15);
        for (int m : mv) s.addMove(m);
        return s;
    }

    public void testDefaultIsFalse() {
        GridState plain = new SimpleGridState(19, 19);
        assertTrue(!plain.seatsSwapped());
    }

    public void testSimplePenteStateFollowsDPenteSwap() {
        SimplePenteState s = new SimplePenteState(new SimpleGomokuState(19, 19));
        assertTrue(!s.seatsSwapped());
        s.dPenteSwapDecisionMade(true);
        assertTrue(s.seatsSwapped());
    }

    public void testSimplePenteStateDeclinedSwapFalse() {
        SimplePenteState s = new SimplePenteState(new SimpleGomokuState(19, 19));
        s.dPenteSwapDecisionMade(false);
        assertTrue(!s.seatsSwapped());
    }

    public void testSynchronizedGridStateDelegates() {
        SimplePenteState inner = new SimplePenteState(new SimpleGomokuState(19, 19));
        GridState wrapped = new SynchronizedGridState(inner);
        assertTrue(!wrapped.seatsSwapped());
        inner.dPenteSwapDecisionMade(true);
        assertTrue(wrapped.seatsSwapped());
    }

    public void testRenjuStateSingleSwapOdd() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(moves(xy(7, 7)), st.encode(), null);
        assertTrue(s.seatsSwapped());
    }

    public void testRenjuStateTwoSwapsCancel() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(moves(xy(7, 7), xy(8, 8)), st.encode(), null);
        assertTrue(!s.seatsSwapped());
    }

    public void testRenjuStateNoSwapsFalse() {
        RenjuState s = new RenjuState(15, 15);
        assertTrue(!s.seatsSwapped());
    }
}
