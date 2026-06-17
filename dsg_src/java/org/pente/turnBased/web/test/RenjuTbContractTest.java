package org.pente.turnBased.web.test;

import junit.framework.TestCase;
import org.pente.game.RenjuState;
import org.pente.turnBased.web.RenjuTbContract;
import org.pente.turnBased.web.RenjuTbContract.Decision;
import org.pente.turnBased.web.RenjuTbContract.Kind;
import org.pente.turnBased.web.RenjuTbContract.RenjuContractException;

public class RenjuTbContractTest extends TestCase {

    public RenjuTbContractTest(String name) {
        super(name); // junit-3.7 TestCase has no no-arg constructor
    }

    private static final int C = 112; // center of 15x15 -> (7,7)

    // Ten legal Branch-B 5th-move offers given the seed below. Moves encode as
    // move = x + y*15. The seed places black on the x=7 axis ((7,7),(7,8)) and
    // white as a mirror pair ((8,7),(6,7)), so the COLOR-preserving D4
    // stabilizer is exactly {identity, mirror across x=7} (positionStabilizer
    // compares colors, not mere occupancy). Every offer below sits at x in 9..13
    // (right of the x=7 mirror axis) and rows y in 8..9, so all are empty, in
    // bounds, distinct, and none is the mirror image of another (mirrors land at
    // x in 1..5). Includes 130 (the selected 5th in the SELECT tests); excludes
    // 99 (the un-offered probe).
    private static final int[] GOOD_OFFERS =
            {129, 130, 131, 132, 133, 144, 145, 146, 147, 148};

    // Same set but 148 replaced by 124 = mirror-x image of 130: (10,8)->(4,8).
    // Under the {identity, mirror-x} stabilizer this is a genuine symmetric
    // duplicate of 130, so wouldAcceptFifthOffers must reject the whole set.
    private static final int[] SYMMETRIC_OFFERS =
            {129, 130, 131, 132, 133, 144, 145, 146, 147, 124};

    // --- builders (mirror RenjuReconstructTest style) ---

    /** SWAP window open after `n` opening stones (1..4), no swap taken yet. */
    private RenjuState swapWindow(int n) {
        RenjuState s = new RenjuState(15, 15);
        // center, then inside 3x3 / 5x5 / 7x7. Order chosen so black ((7,7),
        // (7,8)) lies on the x=7 axis and white ((8,7),(6,7)) is a mirror pair,
        // giving a color-symmetric {identity, mirror-x} stabilizer (see OFFERS).
        int[] seed = { C, C + 1, C + 15, C - 1 };
        for (int i = 0; i < n; i++) s.addMove(seed[i]);
        assertTrue("expected SWAP after " + n, s.isAwaitingSwapDecision());
        return s;
    }

    /** Post-take-over BRANCH state: 4 stones, swap4 resolved, branch pending. */
    private RenjuState branchAfterTakeover() {
        RenjuState s = swapWindow(4);
        s.renjuSwapDecisionMade(true); // take over -> awaitingSwap=false, branch pending
        assertTrue(s.isAwaitingBranchChoice());
        return s;
    }

    /** SELECTION state: branch B chosen, 10 offers recorded. */
    private RenjuState selection() {
        RenjuState s = swapWindow(4);
        s.renjuSwapDecisionMade(false);
        s.chooseBranch(true);
        s.offerFifthMoves(GOOD_OFFERS);
        assertTrue(s.isAwaitingFifthSelection());
        return s;
    }

    // --- swap ---

    public void testSwapTakeOver() throws Exception {
        Decision d = RenjuTbContract.resolve("swap", null, swapWindow(2));
        assertEquals(Kind.TAKE_OVER, d.kind);
    }

    public void testSwapRejectedWhenNoWindow() {
        try {
            RenjuTbContract.resolve("swap", null, branchAfterTakeover());
            fail("expected rejection");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- move: windows 1-3 decline+place ---

    public void testMoveDeclineAndPlaceWindow2() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, swapWindow(2));
        assertEquals(Kind.PLACE, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(C + 2, d.stones[0]);
    }

    // --- move: branch A at window 4 (fresh decline) ---

    public void testMoveBranchAFreshDecline() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, swapWindow(4));
        assertEquals(Kind.BRANCH_A, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(C + 2, d.stones[0]);
    }

    // --- move: branch A after take-over (no swap to decline) ---

    public void testMoveBranchAAfterTakeover() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, branchAfterTakeover());
        assertEquals(Kind.BRANCH_A, d.kind);
        assertTrue(!d.declineSwap); // junit-3.7 has no assertFalse
    }

    // --- move: branch B (10 stones) ---

    public void testMoveBranchBTenOffers() throws Exception {
        Decision d = RenjuTbContract.resolve("move", GOOD_OFFERS, swapWindow(4));
        assertEquals(Kind.BRANCH_B, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(10, d.stones.length);
    }

    public void testMoveBranchBRejectsSymmetricOfferSet() {
        try {
            RenjuTbContract.resolve("move", SYMMETRIC_OFFERS, swapWindow(4));
            fail("expected offer rejection");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testMoveTenStonesRejectedOutsideBranchPoint() {
        try {
            RenjuTbContract.resolve("move", GOOD_OFFERS, swapWindow(2));
            fail("expected rejection: 10 stones only at branch point");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testMoveRejectsBadStoneCount() {
        try {
            RenjuTbContract.resolve("move", new int[]{1,2,3}, swapWindow(4));
            fail("expected rejection: count not in {1,10}");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- select: atomic 2-stone ---

    public void testSelectCommitsTwoStones() throws Exception {
        Decision d = RenjuTbContract.resolve("select", new int[]{130, 200}, selection());
        assertEquals(Kind.SELECT, d.kind);
        assertEquals(130, d.stones[0]); // black move 5 (was offered)
        assertEquals(200, d.stones[1]); // white move 6
    }

    public void testSelectRejectsUnofferedFifth() {
        try {
            RenjuTbContract.resolve("select", new int[]{99, 200}, selection());
            fail("expected rejection: move 5 not offered");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsMove6OnOccupied() {
        try {
            RenjuTbContract.resolve("select", new int[]{130, C}, selection()); // C is occupied (move 1)
            fail("expected rejection: move 6 not empty");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsMove6EqualMove5() {
        try {
            RenjuTbContract.resolve("select", new int[]{130, 130}, selection());
            fail("expected rejection: move 6 == move 5");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsWrongStoneCount() {
        try {
            RenjuTbContract.resolve("select", new int[]{130}, selection());
            fail("expected rejection: select needs 2 stones");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- unknown action ---

    public void testUnknownActionRejected() {
        try {
            RenjuTbContract.resolve("branch", new int[]{1}, swapWindow(4));
            fail("expected rejection: unknown action");
        } catch (RenjuContractException e) { /* ok */ }
    }
}
