package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;

public class TBGamePassDrawTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{TBGamePassDrawTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGamePassDrawTest.class);
    }

    public TBGamePassDrawTest(String name) {
        super(name);
    }

    public void testRenjuPassMoveDoublePass() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_RENJU);
        g.addMove(112); // any stones
        g.addMove(113);
        g.addMove(225); // pass
        assertEquals(-1, g.containsDoublePass());
        g.addMove(225); // second consecutive pass
        assertTrue(g.containsDoublePass() >= 0);
    }

    public void testGoPassUnaffected() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_GO);
        g.addMove(361);
        g.addMove(361);
        assertTrue(g.containsDoublePass() >= 0);
    }

    public void testDrawOfferedFlag() {
        TBGame g = new TBGame();
        assertTrue(!g.isDrawOffered());
        g.setDrawOffered(true);
        assertTrue(g.isDrawOffered());
    }

    public void testTimeoutDrawDerivation() {
        TBGame g = new TBGame();
        g.timeout(); // -> STATE_COMPLETED_TO
        g.setWinner(0);
        assertTrue(g.isDraw());
    }
}
