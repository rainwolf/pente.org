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

    public void testMove1MustBeCenter() {
        RenjuState s = newState();
        assertTrue(!s.isValidMove(xy(s, 7, 8), 1)); // off-center
        assertTrue(s.isValidMove(xy(s, 7, 7), 1));  // center
    }

    public void testMove2WithinThreeBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));
        s.renjuSwapDecisionMade(false);
        assertTrue(!s.isValidMove(xy(s, 7, 9), 2));
        assertTrue(s.isValidMove(xy(s, 8, 8), 2));
    }

    public void testMove3WithinFiveBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);
        assertTrue(!s.isValidMove(xy(s, 7, 10), 1));
        assertTrue(s.isValidMove(xy(s, 9, 9), 1));
    }

    public void testMove4WithinSevenBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 9, 9)); s.renjuSwapDecisionMade(false);
        assertTrue(!s.isValidMove(xy(s, 7, 11), 2));
        assertTrue(s.isValidMove(xy(s, 10, 10), 2));
    }

    public void testSwapWindowBlocksMovesUntilDecided() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); // move 1 (black, color 1)
        // swap window open: white (player 2) is the decider, no board move allowed
        assertTrue(s.isAwaitingSwapDecision());
        assertEquals(2, s.getCurrentPlayer());
        assertTrue(!s.isValidMove(xy(s, 8, 8), 2)); // blocked while pending
        s.renjuSwapDecisionMade(false);              // white declines swap
        assertTrue(!s.isAwaitingSwapDecision());
        assertEquals(2, s.getCurrentPlayer());        // white now plays move 2
        assertTrue(s.isValidMove(xy(s, 8, 8), 2));
    }

    public void testSwapDecisionRecorded() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));
        s.renjuSwapDecisionMade(true);
        assertTrue(s.didSwapAt(1)); // swap recorded for the window after stone 1
    }

    private RenjuState openedToFour() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);  // 1 black
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);  // 2 white
        s.addMove(xy(s, 9, 7)); s.renjuSwapDecisionMade(false);  // 3 black
        s.addMove(xy(s, 6, 8)); s.renjuSwapDecisionMade(false);  // 4 white
        return s;
    }

    public void testBranchChoiceRequiredAfterMove4() {
        RenjuState s = openedToFour();
        assertTrue(s.isAwaitingBranchChoice());
        assertEquals(1, s.getCurrentPlayer());               // black chooses
        assertTrue(!s.isValidMove(xy(s, 5, 5), 1));          // blocked until chosen
    }

    public void testBranchAFullSequence() {
        RenjuState s = openedToFour();
        s.chooseBranch(false);                                // Branch A
        assertTrue(!s.isAwaitingBranchChoice());
        // move 5 (black) must be within 9x9
        assertTrue(!s.isValidMove(xy(s, 7, 12), 1));          // dy=5 outside 9x9
        assertTrue(s.isValidMove(xy(s, 11, 7), 1));           // dx=4 inside 9x9
        s.addMove(xy(s, 11, 7));                               // move 5
        // swap window for white before move 6
        assertTrue(s.isAwaitingSwapDecision());
        s.renjuSwapDecisionMade(false);
        // move 6 (white) anywhere
        assertTrue(s.isValidMove(xy(s, 0, 0), 2));
        s.addMove(xy(s, 0, 0));                                // move 6
        assertTrue(s.isOpeningComplete());
    }

    public void testBranchBOffersAndSelection() {
        RenjuState s = openedToFour();
        s.chooseBranch(true); // Branch B
        assertTrue(s.isAwaitingFifthOffers());
        assertEquals(1, s.getCurrentPlayer()); // black offers

        // Offer 10 distinct, non-symmetric candidates far from center to avoid symmetry collisions.
        int[][] offers = {
            {0,0},{0,2},{0,4},{0,6},{0,8},{0,10},{0,12},{0,14},{2,0},{4,0}
        };
        for (int[] o : offers) s.offerFifthMove(xy(s, o[0], o[1]));
        assertEquals(10, s.getOfferedFifthMoves().size());
        assertTrue(!s.isAwaitingFifthOffers());
        assertTrue(s.isAwaitingFifthSelection());
        assertEquals(2, s.getCurrentPlayer()); // white selects

        s.selectFifthMove(xy(s, 0, 6));        // white picks one offered move
        assertEquals(5, s.getNumMoves());       // committed as move 5
        assertEquals(1, s.getColor(4));         // move 5 is black (color 1)
        // move 6 (white) anywhere
        assertTrue(s.isValidMove(xy(s, 14, 14), 2));
        s.addMove(xy(s, 14, 14));
        assertTrue(s.isOpeningComplete());
    }

    // Exact-duplicate rejection: the degenerate symmetry (identity, rot=0).
    public void testSymmetricDuplicateOfferRejected() {
        RenjuState s = openedToFour();
        s.chooseBranch(true);
        s.offerFifthMove(xy(s, 0, 0));
        try {
            s.offerFifthMove(xy(s, 0, 0)); // exact duplicate
            fail("expected duplicate rejection");
        } catch (IllegalArgumentException expected) {
        }
    }

    // Non-identity D4 dedup: build a 4-stone opening invariant under the horizontal
    // mirror (reflection across the y=7 axis). Blacks {(7,7),(9,7)} sit on the axis;
    // whites {(7,6),(7,8)} are a mirror pair. positionStabilizer() is therefore {0,2}
    // (identity + that reflection), so offering (0,0) must reject its mirror (0,14) —
    // a DIFFERENT point mapped onto the existing offer by the non-identity symmetry.
    // A bug in positionStabilizer() or the rotation direction would let (0,14) through.
    public void testMirroredOfferRejectedUnderSymmetry() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false); // 1 black (center, on axis)
        s.addMove(xy(s, 7, 6)); s.renjuSwapDecisionMade(false); // 2 white (below axis)
        s.addMove(xy(s, 9, 7)); s.renjuSwapDecisionMade(false); // 3 black (on axis)
        s.addMove(xy(s, 7, 8)); s.renjuSwapDecisionMade(false); // 4 white (mirror of move 2)
        s.chooseBranch(true);
        assertTrue(s.isAwaitingFifthOffers());

        s.offerFifthMove(xy(s, 0, 0));
        try {
            s.offerFifthMove(xy(s, 0, 14)); // mirror of (0,0) across y=7; not identical
            fail("expected symmetric-duplicate rejection of mirrored offer");
        } catch (IllegalArgumentException expected) {
        }
        // A genuinely distinct, non-mirrored point is still accepted.
        s.offerFifthMove(xy(s, 1, 0));
        assertEquals(2, s.getOfferedFifthMoves().size());
    }

    public void testNoUndoWhileSwapPending() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); // swap window open
        assertTrue(!s.canPlayerUndo(1));
        assertTrue(!s.canPlayerUndo(2));
    }

    public void testUndoDelegatesPostOpening() {
        RenjuState s = openedToFour();
        s.chooseBranch(false);
        s.addMove(xy(s, 11, 7)); s.renjuSwapDecisionMade(false); // move 5
        s.addMove(xy(s, 0, 0));                                  // move 6 -> opening complete
        s.addMove(xy(s, 1, 1));                                  // move 7 (black)
        // after move 7 it's white's turn; white just did NOT move last -> black may undo
        assertTrue(s.canPlayerUndo(s.getCurrentColor() == 1 ? 2 : 1));
    }

    // openingComplete is latched and undoMove() never restores it, so an undo that
    // dropped numMoves below the 6-stone committed opening would corrupt the state
    // machine (post-opening branch of isValidMove would skip the central-square /
    // swap windows). No undo may cross back into the negotiated region.
    public void testNoUndoBackIntoOpening() {
        RenjuState s = openedToFour();
        s.chooseBranch(false);
        s.addMove(xy(s, 11, 7)); s.renjuSwapDecisionMade(false); // move 5
        s.addMove(xy(s, 0, 0));                                  // move 6 -> opening complete
        assertTrue(s.isOpeningComplete());
        assertEquals(6, s.getNumMoves());
        assertTrue(!s.canPlayerUndo(1));
        assertTrue(!s.canPlayerUndo(2));
    }

    // End-to-end: a full legal Taraguchi-10 Branch A opening with NO forceOpeningComplete.
    // Center, then 3x3/5x5/7x7 moves each followed by a declined swap, chooseBranch(false),
    // a 9x9 move 5, a declined swap, and move 6 anywhere -> opening naturally completes.
    // Afterwards the post-opening forbidden switch must be live: a black double-three point
    // (built far from the opening with extra post-opening stones) is rejected by isValidMove.
    public void testNaturalOpeningThenForbiddenSwitchesOn() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false); // 1 black (center)
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false); // 2 white (3x3)
        s.addMove(xy(s, 9, 9)); s.renjuSwapDecisionMade(false); // 3 black (5x5)
        s.addMove(xy(s, 6, 6)); s.renjuSwapDecisionMade(false); // 4 white (7x7)
        s.chooseBranch(false);                                  // Branch A
        s.addMove(xy(s, 11, 7)); s.renjuSwapDecisionMade(false); // 5 black (9x9)
        s.addMove(xy(s, 13, 13));                                // 6 white (anywhere)
        assertTrue(s.isOpeningComplete());

        // Continue past the opening with normal alternating moves. Black stones at move
        // indices 6,8,10,12 form a double-three around (3,3); whites are harmless fillers.
        // The trailing white filler leaves it black's turn so the forbidden check applies.
        s.addMove(xy(s, 1, 3));  s.addMove(xy(s, 14, 0)); // b,w
        s.addMove(xy(s, 2, 3));  s.addMove(xy(s, 14, 1)); // b,w
        s.addMove(xy(s, 3, 1));  s.addMove(xy(s, 14, 2)); // b,w
        s.addMove(xy(s, 3, 2));  s.addMove(xy(s, 14, 3)); // b,w
        assertEquals(1, s.getCurrentColor());             // black to move

        assertTrue(!s.isValidMove(xy(s, 3, 3), 1));       // double-three now forbidden
        assertTrue(s.isValidMove(xy(s, 12, 12), 1));      // ordinary empty point is fine
    }

    // selectFifthMove with a move that was never offered -> IllegalArgumentException.
    public void testSelectFifthMoveNonOfferedThrows() {
        RenjuState s = openedToFour();
        s.chooseBranch(true);
        int[][] offers = {
            {0,0},{0,2},{0,4},{0,6},{0,8},{0,10},{0,12},{0,14},{2,0},{4,0}
        };
        for (int[] o : offers) s.offerFifthMove(xy(s, o[0], o[1]));
        assertTrue(s.isAwaitingFifthSelection());
        try {
            s.selectFifthMove(xy(s, 1, 1)); // never offered
            fail("expected IllegalArgumentException for non-offered selection");
        } catch (IllegalArgumentException expected) {
        }
    }

    // offerFifthMove while not in the offer state (no branch chosen) -> IllegalStateException.
    public void testOfferFifthMoveBeforeBranchThrows() {
        RenjuState s = openedToFour();
        assertTrue(!s.isAwaitingFifthOffers());
        try {
            s.offerFifthMove(xy(s, 0, 0));
            fail("expected IllegalStateException offering before chooseBranch(true)");
        } catch (IllegalStateException expected) {
        }
    }

    // renjuSwapDecisionMade with no swap window open -> IllegalStateException.
    public void testRenjuSwapDecisionNonePendingThrows() {
        RenjuState s = newState(); // fresh: no swap pending
        assertTrue(!s.isAwaitingSwapDecision());
        try {
            s.renjuSwapDecisionMade(false);
            fail("expected IllegalStateException with no swap decision pending");
        } catch (IllegalStateException expected) {
        }
    }

    // chooseBranch when not awaiting a branch choice -> IllegalStateException.
    public void testChooseBranchNotAwaitingThrows() {
        RenjuState s = newState(); // fresh: numMoves 0, not awaiting branch
        assertTrue(!s.isAwaitingBranchChoice());
        try {
            s.chooseBranch(false);
            fail("expected IllegalStateException choosing branch when not awaiting");
        } catch (IllegalStateException expected) {
        }
    }

    // Full-board draw. A 5x4 board is used (not 5x5): only its length-5 horizontal lines
    // can reach five, and a simple row-major fill alternates colors within every row, so
    // no five ever forms (5x5 would force its main diagonals monochrome under naive fills).
    public void testFullBoardDrawNoWinner() {
        RenjuState s = new RenjuState(5, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 5; x++) {
                s.addMove(s.convertMove(x, y));
            }
        }
        assertEquals(20, s.getNumMoves());
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner());
    }
}
