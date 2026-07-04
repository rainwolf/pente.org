package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.*;
import org.pente.turnBased.*;

public class TBGameSeatsSwappedTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TBGameSeatsSwappedTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGameSeatsSwappedTest.class);
    }

    public TBGameSeatsSwappedTest(String name) {
        super(name);
    }

    private TBGame game(int type) {
        TBGame g = new TBGame();
        g.setGame(type);
        g.setPlayer1Pid(11L);
        g.setPlayer2Pid(22L);
        return g;
    }

    public void testNonSwapVariantNeverSwapped() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testDPenteFamilySwapRestoresOriginalPids() {
        int[] types = {GridStateFactory.TB_DPENTE, GridStateFactory.TB_DKERYO,
                GridStateFactory.TB_SWAP2PENTE, GridStateFactory.TB_SWAP2KERYO};
        for (int type : types) {
            TBGame g = game(type);
            assertTrue(!g.seatsSwapped());
            g.dPenteSwap(true); // physically swaps pids
            assertTrue(g.seatsSwapped());
            assertEquals(22L, g.getPlayer1Pid());
            assertEquals(11L, g.getOriginalPlayer1Pid());
            assertEquals(22L, g.getOriginalPlayer2Pid());
        }
    }

    public void testDPenteDeclinedSwapKeepsPids() {
        TBGame g = game(GridStateFactory.TB_DPENTE);
        g.dPenteSwap(false);
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
    }

    public void testRenjuSingleSwapOdd() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode());
        // simulate the pid swap renjuSwap() performed alongside recording
        g.setPlayer1Pid(22L);
        g.setPlayer2Pid(11L);
        assertTrue(g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testRenjuTwoSwapsCancel() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode()); // two swaps: pids back where they started
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testRenjuBranchDigitIgnored() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.branch = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode());
        assertTrue(!g.seatsSwapped());
    }
}
