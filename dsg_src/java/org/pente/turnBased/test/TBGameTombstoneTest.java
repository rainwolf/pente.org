package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;

public class TBGameTombstoneTest extends TestCase {
    public TBGameTombstoneTest(String name) { super(name); }
    public static Test suite() { return new TestSuite(TBGameTombstoneTest.class); }

    private static final long P1 = 1001L, P2 = 1002L;

    private TBGame game(int... codes) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_TOMBSTONE);
        g.setPlayer1Pid(P1);
        g.setPlayer2Pid(P2);
        for (int c : codes) g.addMove(c);
        return g;
    }

    public void testNoMovesIsP1()                 { assertEquals(P1, game().getCurrentPlayer()); }
    public void testAfterCentreIsP2()             { assertEquals(P2, game(180).getCurrentPlayer()); }
    public void testAfterOwnPlusDeadIsP1()        { assertEquals(P1, game(180, 181, 561).getCurrentPlayer()); }
    public void testAfterOwnOnlyIsP1()            { assertEquals(P1, game(180, 181).getCurrentPlayer()); }
    public void testAfterPassOnlyIsP1()           { assertEquals(P1, game(180, 722).getCurrentPlayer()); }
    public void testAfterPassDeadThenOwnIsP2()    { assertEquals(P2, game(180, 722, 361, 200).getCurrentPlayer()); }

    public void testLastTurnLength() {
        assertEquals(0, game().getLastTurnLength());
        assertEquals(0, game(180).getLastTurnLength());
        assertEquals(1, game(180, 181).getLastTurnLength());
        assertEquals(2, game(180, 181, 561).getLastTurnLength());
        assertEquals(1, game(180, 722).getLastTurnLength());
        assertEquals(2, game(180, 722, 361).getLastTurnLength());
    }

    /** spec 4.1: passMove stays 361 for Tombstone; a dead stone on point 0 (code 361)
     *  can never sit next to another 361, so Go's double-pass detector never fires. */
    public void testDoublePassNeverFiresWithDeadOnPointZero() {
        TBGame g = game(180, 722, 361, 722, 362, 200);
        assertEquals(-1, g.containsDoublePass());
        assertEquals(TBGame.GO_PLAY, g.getGoState());
        TBGame twoEmpty = game(180, 722, 722);
        assertEquals(-1, twoEmpty.containsDoublePass());
        assertEquals(TBGame.GO_PLAY, twoEmpty.getGoState());
    }

    public void testOtherGamesUnchanged() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setPlayer1Pid(P1); g.setPlayer2Pid(P2);
        g.addMove(180); g.addMove(181);
        assertEquals(P1, g.getCurrentPlayer());
    }
}
