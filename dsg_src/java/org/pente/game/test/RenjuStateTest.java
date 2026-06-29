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

    // Raw 15x15 move encoding (no RenjuState needed), for building reconstruct move lists.
    private int xy(int x, int y) {
        return x + y * 15;
    }

    // A MoveData move list (used by RenjuState.reconstruct) built from raw moves.
    private SimpleGridState movesData(int... mv) {
        SimpleGridState s = new SimpleGridState(15, 15);
        for (int m : mv) s.addMove(m);
        return s;
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
        // black 6 in a row (3..8,7) — NOT a win for black. Under Renju an overline is a
        // forbidden point, so completing it ends the game with WHITE the winner.
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),
            xy(s, 4, 7), xy(s, 1, 0),
            xy(s, 5, 7), xy(s, 2, 0),
            xy(s, 6, 7), xy(s, 3, 0),
            xy(s, 7, 7), xy(s, 4, 0),
            xy(s, 8, 7));
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner()); // overline is a black loss, not a black win
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

    // Post-opening, a black double-three is now a LEGAL (but losing) move: black may
    // play it, and doing so immediately ends the game with white the winner.
    public void testForbiddenMoveLosesForBlack() {
        RenjuState s = newState();
        // Build a black double-three around (7,7) with interleaved harmless white moves.
        // Black stones: (5,7),(6,7),(7,5),(7,6). Sequence so it is black's turn at (7,7).
        add(s,
            xy(s, 5, 7), xy(s, 0, 0),   // b, w
            xy(s, 6, 7), xy(s, 0, 1),   // b, w
            xy(s, 7, 5), xy(s, 0, 2),   // b, w
            xy(s, 7, 6), xy(s, 0, 3));  // b, w  -> 8 moves, next is black (color 1)
        s.forceOpeningComplete(); // test hook (see implementation)
        int forbidden = xy(s, 7, 7);
        assertTrue(s.isValidMove(forbidden, 1));      // forbidden point is now a legal move
        // a normal empty non-forbidden point is also fine for black
        assertTrue(s.isValidMove(xy(s, 12, 12), 1));
        s.addMove(forbidden);                          // black plays the double-three
        assertTrue(s.isGameOver());                    // immediate game over
        assertEquals(2, s.getWinner());                // white wins
    }

    // A black OVERLINE (six in a row) is also a forbidden point and an immediate loss.
    public void testOverlineMoveLosesForBlack() {
        RenjuState s = newState();
        // Black stones (3,7),(4,7),(5,7),(7,7),(8,7); playing (6,7) makes six in a row.
        // White fillers spaced in column 0 so they never make five themselves.
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),   // b, w
            xy(s, 4, 7), xy(s, 0, 2),   // b, w
            xy(s, 5, 7), xy(s, 0, 4),   // b, w
            xy(s, 7, 7), xy(s, 0, 6),   // b, w
            xy(s, 8, 7), xy(s, 0, 8));  // b, w  -> 10 moves, next is black (color 1)
        s.forceOpeningComplete();
        int overline = xy(s, 6, 7);
        assertTrue(s.isValidMove(overline, 1));        // overline point is a legal move
        s.addMove(overline);                           // black makes six in a row
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());                // white wins (overline is forbidden)
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

    // ---------------------------------------------------------------------
    // Regression: 5th-offer symmetry must use the placed-shape stabilizer in
    // ABSOLUTE coordinates, not symmetries about the fixed board centre.
    // Position (from the user's screenshot):
    //   m1=(7,7) B, m2=(7,6) W, m3=(6,7) B, m4=(6,8) W
    // BLACK={(7,7),(6,7)} WHITE={(7,6),(6,8)}.
    // Ground-truth stabilizer = { identity, 180deg about (6.5,7): (x,y)->(13-x,14-y) }.
    // The OLD centre-based code finds only {identity} here (it rejects r4 because
    // about (7,7) it sends (6,7)->(8,7)=empty), so it offers mirror pairs as
    // distinct -- THE BUG these tests pin.
    // ---------------------------------------------------------------------
    private RenjuState regressionPosition() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false); // 1 black (tengen)
        s.addMove(xy(s, 7, 6)); s.renjuSwapDecisionMade(false); // 2 white
        s.addMove(xy(s, 6, 7)); s.renjuSwapDecisionMade(false); // 3 black
        s.addMove(xy(s, 6, 8)); s.renjuSwapDecisionMade(false); // 4 white
        return s;
    }

    // 1) Dedup now fires. Each mirror pair: offering the first must reject the
    //    second under the off-centre 180deg symmetry. (Old code accepts both.)
    public void testRegressionMirrorPairsRejected() {
        int[][] pairs = {
            {8, 4, 5, 10}, // idx 68 <-> 155
            {6, 5, 7, 9},  // idx 81 <-> 142
            {5, 6, 8, 8},  // idx 95 <-> 128
            {9, 9, 4, 5},  // idx 144 <-> 79
        };
        for (int[] p : pairs) {
            RenjuState s = regressionPosition();
            s.chooseBranch(true);
            s.offerFifthMove(xy(s, p[0], p[1]));
            try {
                s.offerFifthMove(xy(s, p[2], p[3]));
                fail("expected symmetric-duplicate rejection for mirror of ("
                        + p[0] + "," + p[1] + ") -> (" + p[2] + "," + p[3] + ")");
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    // 2) No over-collapse. Replace m4 with (5,8) so the shape is asymmetric ->
    //    stabilizer == {identity} -> (8,4) then (5,10) are BOTH accepted.
    public void testRegressionAsymmetricNoOverCollapse() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 7, 6)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 6, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 5, 8)); s.renjuSwapDecisionMade(false); // asymmetric m4
        s.chooseBranch(true);
        s.offerFifthMove(xy(s, 8, 4));
        s.offerFifthMove(xy(s, 5, 10)); // NOT a duplicate when shape is asymmetric
        assertEquals(2, s.getOfferedFifthMoves().size());
    }

    // 3) Bounds guard. (14,14) under (r=4,tx=13,ty=14) -> (-1,0) off-board ->
    //    sentinel, so it must NOT be flagged as a duplicate of any prior offer.
    public void testRegressionBoundsGuardNoWraparound() {
        RenjuState s = regressionPosition();
        s.chooseBranch(true);
        s.offerFifthMove(xy(s, 8, 4));    // unrelated prior offer
        s.offerFifthMove(xy(s, 14, 14));  // image leaves the board -> accepted
        assertEquals(2, s.getOfferedFifthMoves().size());

        // Load-bearing per-axis bounds guard: a TRUE row-wraparound case.
        // The (14,14) case above only exercises the `image < 0` sentinel
        // (its image is (-1,0) -> flat -1), so it does NOT prove the per-axis
        // X/Y guard does anything. Here the naive flat index is a VALID cell:
        //   offer (14,13) = idx 209 (empty).
        //   candidate (14,0) = idx 14 (empty).
        //   non-identity stabilizer op (r=4,tx=13,ty=14): lin_r4(14,0)=(-14,0);
        //   +(13,14) = (-1,14). X=-1 is off-board so the per-axis guard returns
        //   the sentinel (-1) -> NOT a duplicate -> (14,0) ACCEPTED.
        // WITHOUT the per-axis guard the naive flat index is (-1)+(14*15)=209,
        // a valid prior-row cell == the (14,13) offer, so (14,0) would be
        // FALSELY rejected. This assertion fails if the guard is removed.
        RenjuState s2 = regressionPosition();
        s2.chooseBranch(true);
        s2.offerFifthMove(xy(s2, 14, 13)); // idx 209
        s2.offerFifthMove(xy(s2, 14, 0));  // idx 14, image off-board -> accepted
        assertEquals(2, s2.getOfferedFifthMoves().size());
    }

    // 4) Valid full set: 10 candidates, no two related by any stabilizer
    //    transform (one per orbit), all accepted.
    public void testRegressionValidFullSetAccepted() {
        RenjuState s = regressionPosition();
        s.chooseBranch(true);
        int[][] offers = {
            {0,0},{1,0},{2,0},{3,0},{4,0},{5,0},{0,1},{1,1},{2,1},{3,1}
        };
        for (int[] o : offers) s.offerFifthMove(xy(s, o[0], o[1]));
        assertEquals(10, s.getOfferedFifthMoves().size());
        assertTrue(s.isAwaitingFifthSelection());
        // The pure pre-check agrees with the committing path.
        RenjuState s2 = regressionPosition();
        s2.chooseBranch(true);
        int[] flat = new int[10];
        for (int i = 0; i < 10; i++) flat[i] = xy(s2, offers[i][0], offers[i][1]);
        assertTrue(s2.wouldAcceptFifthOffers(flat));
    }

    // wouldAcceptFifthOffers must use the same off-centre stabilizer: a set
    // containing a mirror pair is rejected without mutating state.
    public void testRegressionWouldAcceptRejectsMirrorPair() {
        RenjuState s = regressionPosition();
        s.chooseBranch(true);
        int[] withMirror = {
            xy(s,8,4), xy(s,5,10), // mirror pair -> must be rejected
            xy(s,1,0), xy(s,2,0), xy(s,3,0), xy(s,4,0),
            xy(s,0,1), xy(s,1,1), xy(s,2,1), xy(s,3,1)
        };
        assertTrue(!s.wouldAcceptFifthOffers(withMirror));
        assertEquals(0, s.getOfferedFifthMoves().size()); // no mutation
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
    // Afterwards the post-opening forbidden rule must be live: a black double-three point
    // (built far from the opening with extra post-opening stones) is a legal but losing
    // move -- playing it ends the game with white the winner.
    public void testNaturalOpeningThenForbiddenLoses() {
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

        assertTrue(s.isValidMove(xy(s, 3, 3), 1));        // double-three is a legal (losing) move
        assertTrue(s.isValidMove(xy(s, 12, 12), 1));      // ordinary empty point is fine too
        s.addMove(xy(s, 3, 3));                            // black plays the forbidden point
        assertTrue(s.isGameOver());                       // immediate loss
        assertEquals(2, s.getWinner());                   // white wins
    }

    // Reconstruct a stored game whose LAST move is a black forbidden point: a full Branch A
    // opening (swaps declined) followed by post-opening play ending in a black double-three
    // at (3,3). RenjuState.reconstruct replays the moves; the rebuilt state must report the
    // game over with white the winner (the forbidden-loss verdict survives persistence).
    public void testReconstructBlackForbiddenLastMoveWhiteWins() {
        SimpleGridState md = movesData(
            xy(7, 7), xy(8, 8), xy(9, 7), xy(6, 8), xy(11, 7), xy(13, 13), // opening 1-6
            xy(1, 3), xy(14, 0),    // 7 black, 8 white
            xy(2, 3), xy(14, 1),    // 9 black, 10 white
            xy(3, 1), xy(14, 2),    // 11 black, 12 white
            xy(3, 2), xy(14, 3),    // 13 black, 14 white
            xy(3, 3));              // 15 black -> forbidden double-three
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.NO;   // Branch A
        st.swap5 = RenjuOpeningState.NO;
        RenjuState s = RenjuState.reconstruct(md, st.encode(), null);

        assertEquals(15, s.getNumMoves());
        assertTrue(s.isOpeningComplete());
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());     // white wins
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
