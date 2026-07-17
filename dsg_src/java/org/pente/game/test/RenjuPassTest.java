package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuPassTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuPassTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuPassTest.class);
    }

    public RenjuPassTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    // Fast-forward through a minimal Taraguchi opening, declining every swap ->
    // Branch A. After move 6 addMove() sets openingComplete (RenjuState L163-164).
    // If the state machine demands an explicit branch choice, insert
    // s.chooseBranch(false) after the 4th move's swap decline -- adjust the
    // setup as needed, never the assertions.
    private RenjuState openedState() {
        RenjuState s = new RenjuState(15, 15);
        int[] opening = {xy(7, 7), xy(7, 8), xy(8, 7), xy(8, 8), xy(6, 7)};
        for (int m : opening) {
            s.addMove(m);
            if (s.isAwaitingSwapDecision()) {
                s.renjuSwapDecisionMade(false);
            }
        }
        s.addMove(xy(6, 8)); // move 6 -> openingComplete
        assertTrue(s.isOpeningComplete());
        return s;
    }

    public void testPassMoveConstant() {
        RenjuState s = new RenjuState(15, 15);
        assertEquals(225, s.getPassMove());
        assertTrue(s.isPass(225));
        assertTrue(!s.isPass(224));
    }

    public void testPassInvalidDuringOpening() {
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));
        assertTrue(!s.isValidMove(225, 2));
        try {
            s.addMove(225);
            fail("pass during opening must throw");
        } catch (IllegalStateException expected) {
        }
    }

    public void testPassValidAfterOpening() {
        RenjuState s = openedState();
        int n = s.getNumMoves();
        assertTrue(s.isValidMove(225, s.getCurrentPlayer()));
        s.addMove(225); // must not throw (also exercises the hash path)
        assertEquals(n + 1, s.getNumMoves());
        assertEquals(225, s.getMove(n));
        assertTrue(!s.isGameOver());
        assertTrue(!s.doublePass());
    }

    public void testDoublePassEndsGameInDraw() {
        RenjuState s = openedState();
        s.addMove(225);
        s.addMove(225);
        assertTrue(s.doublePass());
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner()); // 0 == draw for callers (winner==0 branch)
    }

    public void testUndoPass() {
        RenjuState s = openedState();
        int n = s.getNumMoves();
        s.addMove(225);
        s.undoMove();
        assertEquals(n, s.getNumMoves());
        assertTrue(!s.isGameOver());
    }

    public void testSinglePassDoesNotTriggerBoardFullDraw() {
        // Guard: drawCheck must count stones, not raw numMoves, once passes exist.
        RenjuState s = openedState();
        s.addMove(225);
        s.addMove(xy(0, 0));
        s.addMove(225);
        assertTrue(!s.isGameOver());
    }

    public void testPassPreservesPositionHash() {
        // A pass places no stone, so getHash() must still report the position
        // hash, and a real move after the pass must continue the hash chain
        // (not hash forward from a zeroed pass slot).
        RenjuState s = openedState();
        long h = s.getHash();
        s.addMove(225);
        assertEquals(h, s.getHash()); // pass leaves the position hash unchanged
        s.addMove(xy(0, 0));
        assertTrue(s.getHash() != h); // real move after a pass changes the hash
        s.undoMove(); // undo the stone -> back to the post-pass position
        assertEquals(h, s.getHash());
        s.undoMove(); // undo the pass -> back to the opened position
        assertEquals(h, s.getHash());
    }
}
