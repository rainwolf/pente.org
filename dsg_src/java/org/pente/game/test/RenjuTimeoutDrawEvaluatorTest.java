package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuTimeoutDrawEvaluatorTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuTimeoutDrawEvaluatorTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuTimeoutDrawEvaluatorTest.class);
    }

    public RenjuTimeoutDrawEvaluatorTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    // Bare board container: SimpleGridState positions can be set directly,
    // bypassing opening rules — the evaluator only reads positions.
    private SimpleGridState board() {
        return new SimpleGridState(15, 15);
    }

    public void testWhiteWinsOnEmptyBoard() {
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(board(), 2));
    }

    public void testBlackWinsOnEmptyBoard() {
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(board(), 1));
    }

    // Black stones on every 5th column kill every horizontal/diagonal window
    // wider than 4 and every vertical window in those columns... build the
    // classic draw lattice: black stone at every (x,y) with (x % 5 == 0),
    // filling full columns 0,5,10 leaves 4-wide gaps: no 5-window without black.
    public void testWhiteDrawWhenEveryWindowBlocked() {
        SimpleGridState s = board();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x += 5) {
                s.setPosition(s.convertMove(x, y), 1); // black walls
            }
        }
        // vertical windows inside the 4-wide corridors are still black-free:
        // corridors are columns 1-4, full height -> vertical 5-window exists.
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
        // now poison the corridors vertically too: black row every 5th row
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y += 5) {
                s.setPosition(s.convertMove(x, y), 1);
            }
        }
        assertTrue(!RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
    }

    public void testWhiteWindowMayContainWhiteStones() {
        SimpleGridState s = board();
        // fill almost everything with black except one row segment holding
        // 3 white stones + 2 empties: white can still complete five (overline
        // irrelevant: flanks are black, but white overline also wins — and
        // here it is exactly five anyway).
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                s.setPosition(s.convertMove(x, y), 1);
            }
        }
        for (int x = 4; x <= 8; x++) {
            s.setPosition(s.convertMove(x, 7), 0);
        }
        s.setPosition(s.convertMove(4, 7), 2);
        s.setPosition(s.convertMove(5, 7), 2);
        s.setPosition(s.convertMove(6, 7), 2);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
    }

    // Black candidate windows demand non-black flanks: a lone 5-gap whose
    // both flanking cells are black can never be an exactly-five.
    public void testBlackFlankedWindowIsDead() {
        SimpleGridState s = board();
        // block everything except row 7, a 4-wide empty gap x=5..8 with BLACK
        // flanks at x=4 and x=9. All other cells white. A 4-empty gap flanked
        // by black yields NO black candidate window: every 5-cell run touching
        // these cells has a black outer flank (filling it makes an overline,
        // not an exact five), so blackCandidateWindows is empty -> false.
        // (A 5-empty gap would leave live shifted windows x=3..7 / x=5..9;
        // 4 wide is the geometry the brief's "no candidate windows" note means.)
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                s.setPosition(s.convertMove(x, y), 2);
            }
        }
        for (int x = 5; x <= 8; x++) {
            s.setPosition(s.convertMove(x, 7), 0);
        }
        s.setPosition(s.convertMove(4, 7), 1);
        s.setPosition(s.convertMove(9, 7), 1);
        assertTrue(!RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }
}
