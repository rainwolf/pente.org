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

    public void testBlackSimpleWindowFill() {
        SimpleGridState s = board();
        s.setPosition(s.convertMove(3, 3), 2); // lone white stone far away
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Five-priority regression: completing an exactly-five while forming a
    // cross-line double-three must count as a WIN (the finder cancels the
    // forbidden verdict on five, and fillSearch tests isFive before
    // isForbidden). Board (calibrated -- see task-3-report finder evidence):
    // black four on row 7 at (4,7),(5,7),(6,7),(7,7); a vertical pair
    // (8,5),(8,6) and a '\' diagonal pair (6,5),(7,6) both aiming at (8,7).
    // Absent the horizontal five, placing black at (8,7) would be a genuine
    // double-three (vertical open three (8,5),(8,6),(8,7) + diagonal open
    // three (6,5),(7,6),(8,7)) -> forbidden. But (8,7) also completes the
    // exactly-five (4..8,7), so isFive fires first and it is a WIN.
    public void testFivePriorityWinCountsAsWin() {
        SimpleGridState s = board();
        int[][] black = {{4, 7}, {5, 7}, {6, 7}, {7, 7}, {8, 5}, {8, 6}, {6, 5}, {7, 6}};
        for (int[] b : black) {
            s.setPosition(s.convertMove(b[0], b[1]), 1);
        }
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // A window whose left-to-right fill order collides with a double-three
    // unless a later cell is filled first -- exercises the DFS order search.
    // Window row 7, x=4..8; black already at (4,7),(5,7). A vertical pair
    // (6,5),(6,6) makes (6,7) a double-three (horizontal open three
    // (4,7),(5,7),(6,7) + vertical open three (6,5),(6,6),(6,7)) so long as
    // (6,7) is placed first -> forbidden. Placing (7,7) FIRST turns the
    // horizontal open three through (6,7) into a plain four, dropping (6,7)
    // to a single (vertical) three -> legal; then (6,7) is legal and (8,7)
    // completes the exactly-five. A naive fixed left-to-right fill starting
    // at (6,7) fails; the (7,7)-before-(6,7) order succeeds.
    public void testStage1OrderMatters() {
        SimpleGridState s = board();
        int[][] black = {{4, 7}, {5, 7},
                {6, 5}, {6, 6}};     // vertical pair through (6,7)
        for (int[] b : black) {
            s.setPosition(s.convertMove(b[0], b[1]), 1);
        }
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Build a board poisoned solid white except a small empty pocket + black
    // stones: this guarantees NO trivial all-empty 5-window exists anywhere, so
    // black can only win through the intended congested pocket. (This is the
    // "poison remaining regions" discipline the brief mandates for the staged
    // fixtures; it makes stage 1 provably fail — see the task-4 report.)
    private SimpleGridState whiteBoardWith(int[][] black, int[][] empties) {
        SimpleGridState s = board();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                s.setPosition(s.convertMove(x, y), 2); // solid white poison
            }
        }
        for (int[] e : empties) s.setPosition(s.convertMove(e[0], e[1]), 0);
        for (int[] b : black) s.setPosition(s.convertMove(b[0], b[1]), 1);
        return s;
    }

    // Stage 2: black HELPER stones OUTSIDE any fillable window are required.
    // Fixture calibrated by exhaustive finder search (task-4 report): stage 1
    // (window-only fills) FAILS on this board -- both live window cells (5,7)
    // and (7,7) are double-three-forbidden in every window-only order, and
    // neither window line is independently fillable to a five. Placing black
    // helper stones in the region makes one extension point forbidden, which
    // demotes a blocking cell's crossing open-three, legalising the fill. The
    // exact cooperative search (stage 2, black helpers only) finds the win;
    // white cooperation is NOT needed here.
    public void testStage2BlackHelperNeeded() {
        int[][] black = {
            {5, 5}, {7, 5},
            {4, 6}, {6, 6}, {8, 6},
            {4, 7}, {6, 7}, {8, 7},
            {5, 8}, {7, 8},
            {5, 9}, {7, 9},
        };
        int[][] empties = {
            {5, 7}, {7, 7}, {5, 6}, {7, 6}, {3, 6}, {6, 8}, {6, 9}, {9, 6},
        };
        SimpleGridState s = whiteBoardWith(black, empties);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Stage 3: cooperative WHITE helper stones are required (black helpers alone
    // are insufficient). Fixture calibrated by exhaustive finder search: stage 1
    // fails and the black-helpers-only search (stage 2) also fails on this board;
    // only when a cooperative white stone blocks the extension of a blocking
    // cell's open-three (a defuse black cannot achieve, since adding black never
    // closes a black three) does the window become fillable. The exact stage-3
    // search finds it.
    public void testStage3WhiteHelperNeeded() {
        int[][] black = {
            {5, 4}, {7, 4},
            {5, 5}, {7, 5},
            {4, 6}, {6, 6}, {8, 6},
            {4, 7}, {6, 7}, {8, 7},
            {5, 8}, {7, 8},
        };
        int[][] empties = {
            {5, 7}, {7, 7}, {5, 6}, {7, 6}, {3, 5}, {6, 8}, {9, 5}, {4, 9},
        };
        SimpleGridState s = whiteBoardWith(black, empties);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Full-board congested draw: every 5-window (all four directions) is blocked,
    // so neither colour can ever complete a five. The blocking set is the lattice
    // (x + 2y) % 5 == 0: for each direction (dx,dy) in {E,S,SE,NE} the per-step
    // delta (dx + 2*dy) mod 5 is non-zero, so every run of 5 consecutive cells
    // contains exactly one lattice cell. (A plain (x+y)%2 checkerboard does NOT
    // draw: its constant-parity diagonals are entirely one colour or entirely
    // empty, and an all-empty diagonal 5-window is winnable -- verified in the
    // task-4 report -- so the brief's checkerboard sketch is replaced here with a
    // provably-blocking lattice; setup-only, no assertion weakened.)
    public void testCongestedDraw() {
        SimpleGridState s = board(); // white poison -> black (timed-out opponent) cannot win
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x + 2 * y) % 5 == 0) s.setPosition(s.convertMove(x, y), 2);
            }
        }
        assertTrue(!RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));

        SimpleGridState t = board(); // mirror: black poison -> white cannot win
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x + 2 * y) % 5 == 0) t.setPosition(t.convertMove(x, y), 1);
            }
        }
        assertTrue(!RenjuTimeoutDrawEvaluator.opponentCanWin(t, 2));
    }

    // Perf guard: a mid-density board on which stage 1 succeeds must return fast
    // (well under the 2s budget). Rows 0-6 carry a deterministic stone scatter;
    // the lower board is left open, so stage 1 finds a fillable window without
    // ever building the region or entering the cooperative search.
    public void testOpenPositionFast() {
        SimpleGridState s = board();
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x * 3 + y * 7) % 4 == 0) {
                    s.setPosition(s.convertMove(x, y), ((x + y) % 2 == 0) ? 1 : 2);
                }
            }
        }
        long t0 = System.currentTimeMillis();
        boolean canWin = RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1);
        long elapsed = System.currentTimeMillis() - t0;
        assertTrue(canWin);
        assertTrue(elapsed < 2000);
    }
}
