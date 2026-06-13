package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                RenjuStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuStateTest.class);
    }

    public RenjuStateTest(String name) {
        super(name);
    }

    private RenjuState newState() {
        return new RenjuState(15, 15);
    }

    private int xy(RenjuState s, int x, int y) {
        return s.convertMove(x, y);
    }

    private void add(RenjuState s, int... moves) {
        for (int m : moves) s.addMove(m);
    }

    // Black plays color 1 (even move indices), white color 2 (odd).
    // Interleave with throwaway white stones far from the action.
    public void testBlackExactFiveWins() {
        RenjuState s = newState();
        // black at (3,7),(4,7),(5,7),(6,7),(7,7); white scattered on row 0
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),
            xy(s, 4, 7), xy(s, 1, 0),
            xy(s, 5, 7), xy(s, 2, 0),
            xy(s, 6, 7), xy(s, 3, 0),
            xy(s, 7, 7));
        assertTrue(s.isGameOver());
        assertEquals(1, s.getWinner());
    }

    public void testBlackOverlineNotWin() {
        RenjuState s = newState();
        // black 6 in a row (3..8,7) — not a win for black
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),
            xy(s, 4, 7), xy(s, 1, 0),
            xy(s, 5, 7), xy(s, 2, 0),
            xy(s, 6, 7), xy(s, 3, 0),
            xy(s, 7, 7), xy(s, 4, 0),
            xy(s, 8, 7));
        assertTrue(!s.isGameOver());
    }

    public void testWhiteFiveWins() {
        RenjuState s = newState();
        // white at (3,7)..(7,7); black scattered
        add(s,
            xy(s, 0, 0), xy(s, 3, 7),
            xy(s, 1, 0), xy(s, 4, 7),
            xy(s, 2, 0), xy(s, 5, 7),
            xy(s, 3, 0), xy(s, 6, 7),
            xy(s, 4, 0), xy(s, 7, 7));
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());
    }

    public void testWhiteOverlineWins() {
        RenjuState s = newState();
        // white 6 in a row wins
        add(s,
            xy(s, 0, 0), xy(s, 3, 7),
            xy(s, 1, 0), xy(s, 4, 7),
            xy(s, 2, 0), xy(s, 5, 7),
            xy(s, 3, 0), xy(s, 6, 7),
            xy(s, 4, 0), xy(s, 7, 7),
            xy(s, 5, 0), xy(s, 8, 7));
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());
    }

    // After enough moves to be "post-opening", black may not play a double-three.
    public void testForbiddenMoveBlockedForBlack() {
        RenjuState s = newState();
        // Build a black double-three around (7,7) with interleaved harmless white moves,
        // then assert black cannot play (7,7). Black stones: (5,7),(6,7),(7,5),(7,6).
        // Sequence so that it is black's turn (color 1) to play (7,7).
        add(s,
            xy(s, 5, 7), xy(s, 0, 0),   // b, w
            xy(s, 6, 7), xy(s, 0, 1),   // b, w
            xy(s, 7, 5), xy(s, 0, 2),   // b, w
            xy(s, 7, 6), xy(s, 0, 3));  // b, w  -> 8 moves, next is black (color 1)
        s.forceOpeningComplete(); // test hook (see implementation)
        int forbidden = xy(s, 7, 7);
        assertTrue(!s.isValidMove(forbidden, 1));
        // a normal empty non-forbidden point is fine for black
        assertTrue(s.isValidMove(xy(s, 12, 12), 1));
    }

    public void testForbiddenPointNotBlockedForWhite() {
        RenjuState s = newState();
        // Same double-three shape but it becomes white's turn; white has no forbidden points.
        add(s,
            xy(s, 0, 0), xy(s, 5, 7),   // b, w
            xy(s, 0, 1), xy(s, 6, 7),   // b, w
            xy(s, 0, 2), xy(s, 7, 5),   // b, w
            xy(s, 0, 3), xy(s, 7, 6),   // b, w
            xy(s, 0, 4));               // b -> 9 moves, next is white (color 2)
        s.forceOpeningComplete();
        int dbl3 = xy(s, 7, 7);
        assertTrue(s.isValidMove(dbl3, 2)); // white allowed
    }
}
