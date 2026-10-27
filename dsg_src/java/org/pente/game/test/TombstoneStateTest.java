package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

/** Tests for the Tombstone Pente rules engine. */
public class TombstoneStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TombstoneStateTest.class);
    }

    public TombstoneStateTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
    private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }

    private static final int PASS = 722;
    private static int pt(int x, int y) { return x + 19 * y; }
    private static int dead(int x, int y) { return 361 + pt(x, y); }
    private TombstoneState s;

    protected void setUp() { s = new TombstoneState(); s.addMove(180); }   // P1's centre, turn 0 closed

    /** One own entry (stone or PASS), then close the turn without a dead stone. */
    private void ownOnly(int code) { s.addMove(code); s.skipDeadStone(); }

    public void testSimpleGridStateCapacityConstructor() {
        SimpleGridState big = new SimpleGridState(19, 19, 3000);
        assertEquals(3000, big.getHashes().length);
        assertEquals(9, big.getHashes()[0].length);
        assertEquals(3000, big.getRotations().length);
        assertEquals(19, big.getGridSizeX());
        assertEquals(720, new SimpleGridState(19, 19).getHashes().length);
    }

    public void testTurnZeroIsCentreOnly() {
        TombstoneState f = new TombstoneState();
        assertEquals(1, f.getCurrentPlayer());
        assertTrue(f.isValidMove(180, 1));
        assertFalse(f.isValidMove(181, 1));
        assertFalse(f.isValidMove(PASS, 1));
        assertFalse(f.isValidMove(180, 2));
        try { f.addMove(PASS); fail("own pass on turn 0"); } catch (IllegalArgumentException expected) { }
        try { f.addMove(361); fail("dead stone on turn 0"); } catch (IllegalArgumentException expected) { }
        assertEquals(0, f.getNumMoves());
        f.addMove(180);
        assertEquals(1, f.getPosition(180));
        assertEquals(1, f.getTurnCount());
        assertFalse(f.isAwaitingDeadStone());
        assertEquals(2, f.getCurrentPlayer());
        assertEquals(1, f.getColor(0));
        assertEquals(0, f.getColor(1));
    }

    public void testFourTurnShapes() {
        s.addMove(pt(3, 3));                       // P2 own
        assertTrue(s.isAwaitingDeadStone());
        assertEquals(2, s.getCurrentPlayer());
        s.addMove(dead(4, 4));                     // P2 dead -> turn closed
        assertFalse(s.isAwaitingDeadStone());
        assertEquals(1, s.getCurrentPlayer());
        assertEquals(2, s.getPosition(pt(3, 3)));
        assertEquals(3, s.getPosition(pt(4, 4)));
        ownOnly(pt(10, 10));                       // P1 own only
        assertEquals(1, s.getPosition(pt(10, 10)));
        assertEquals(2, s.getCurrentPlayer());
        s.addMove(PASS);                           // P2 dead only
        assertTrue(s.isAwaitingDeadStone());
        s.addMove(dead(5, 5));
        assertEquals(0, s.getConsecutiveEmptyTurns());
        assertEquals(1, s.getCurrentPlayer());
        ownOnly(PASS);                             // P1 empty turn
        assertEquals(1, s.getConsecutiveEmptyTurns());
        assertEquals(2, s.getCurrentPlayer());
        assertEquals(7, s.getNumMoves());
        assertEquals(5, s.getTurnCount());
        int[] colors = {1, 2, 2, 1, 2, 2, 1};
        for (int i = 0; i < colors.length; i++) assertEquals("entry " + i, colors[i], s.getColor(i));
        assertEquals(0, s.getColor(7));
        assertFalse(s.isGameOver());
        assertEquals(0, s.getWinner());
    }

    public void testIsValidMoveByPhase() {
        assertTrue(s.isValidMove(PASS, 2));
        assertTrue(s.isValidMove(pt(0, 0), 2));
        assertFalse(s.isValidMove(180, 2));
        assertFalse(s.isValidMove(361, 2));
        assertFalse(s.isValidMove(723, 2));
        assertFalse(s.isValidMove(-1, 2));
        assertFalse(s.isValidMove(pt(0, 0), 1));
        s.addMove(pt(0, 0));
        assertFalse(s.isValidMove(361, 2));        // dead on point 0, now occupied by P2's stone
        assertTrue(s.isValidMove(362, 2));
        assertFalse(s.isValidMove(361 + 180, 2));  // centre occupied
        assertFalse(s.isValidMove(pt(1, 0), 2));   // own stone in the dead phase
        assertFalse(s.isValidMove(PASS, 2));
        assertFalse(s.isValidMove(362, 1));
    }

    public void testReplayPrimitiveRejectsIllegalEntries() {
        try { s.addMove(361); fail("dead stone while not awaiting"); } catch (IllegalArgumentException expected) { }
        try { s.addMove(180); fail("occupied"); } catch (IllegalArgumentException expected) { }
        try { s.addMove(723); fail("above 722"); } catch (IllegalArgumentException expected) { }
        try { s.addMove(-1); fail("negative"); } catch (IllegalArgumentException expected) { }
        s.addMove(pt(3, 3));
        try { s.addMove(361 + pt(3, 3)); fail("dead on occupied"); } catch (IllegalArgumentException expected) { }
        assertEquals(2, s.getNumMoves());          // rejected entries leave no trace
        assertTrue(s.isAwaitingDeadStone());
    }

    /** A rejected own entry while awaiting must not run the implicit close first. */
    public void testRejectedOwnEntryWhileAwaitingLeavesTurnOpen() {
        s.addMove(PASS);                           // P2 own pass, awaiting; closing it would count an empty turn
        try { s.addMove(180); fail("occupied"); } catch (IllegalArgumentException expected) { }
        assertTrue(s.isAwaitingDeadStone());
        assertEquals(2, s.getNumMoves());
        assertEquals(0, s.getConsecutiveEmptyTurns());
        assertEquals(2, s.getTurnCount());
        assertEquals(2, s.getCurrentPlayer());
        s.skipDeadStone();                         // turn 1 empty
        for (int i = 0; i < 4; i++) ownOnly(PASS); // turns 2-5 empty
        s.addMove(PASS);                           // turn 6 open; its close would be the 6th empty turn
        try { s.addMove(pt(0, 0)); fail("own entry after the drawing close"); } catch (IllegalArgumentException expected) { }
        assertFalse(s.isGameOver());
        assertTrue(s.isAwaitingDeadStone());
        assertEquals(5, s.getConsecutiveEmptyTurns());
        s.skipDeadStone();
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner());
    }

    public void testOwnEntryWhileAwaitingImplicitlyClosesTurn() {
        s.addMove(pt(3, 3));                       // P2, awaiting
        s.addMove(pt(4, 4));                       // closes P2's turn as a skip; P1's own stone
        assertEquals(2, s.getPosition(pt(3, 3)));
        assertEquals(1, s.getPosition(pt(4, 4)));
        assertTrue(s.isAwaitingDeadStone());
        assertEquals(1, s.getCurrentPlayer());
        assertEquals(3, s.getTurnCount());
        s.addMove(PASS);                           // closes P1's (non-empty) turn; P2 passes
        assertEquals(0, s.getConsecutiveEmptyTurns());
        s.addMove(PASS);                           // closes P2's empty turn; P1 passes
        assertEquals(1, s.getConsecutiveEmptyTurns());
    }

    public void testSkipDeadStoneRequiresAwaiting() {
        try { new TombstoneState().skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
        try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
        s.addMove(pt(3, 3));
        s.addMove(dead(4, 4));
        try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
    }

    public void testCaptureByOwnStoneAndDeadOnVacatedPoint() {
        ownOnly(pt(10, 9));                        // P2
        ownOnly(pt(0, 18));                        // P1 filler
        ownOnly(pt(11, 9));                        // P2
        s.addMove(pt(12, 9));                      // P1: (9,9) is P1 -> captures (11,9),(10,9)
        assertEquals(2, s.getNumCaptures(1));
        assertEquals(0, s.getNumCaptures(2));
        assertEquals(0, s.getPosition(pt(10, 9)));
        assertEquals(0, s.getPosition(pt(11, 9)));
        assertTrue(s.isValidMove(dead(10, 9), 1)); // a just-vacated point takes a dead stone (D2)
        s.addMove(dead(10, 9));
        assertEquals(3, s.getPosition(pt(10, 9)));
    }

    public void testPlacingBetweenEnemyStonesIsSafe() {
        ownOnly(pt(0, 0));                         // P2 filler
        ownOnly(pt(12, 9));                        // P1
        ownOnly(pt(10, 9));                        // P2
        ownOnly(pt(0, 18));                        // P1 filler
        s.addMove(pt(11, 9));                      // P2 completes P1 . P2 P2 . P1 -> no capture
        assertEquals(2, s.getPosition(pt(10, 9)));
        assertEquals(2, s.getPosition(pt(11, 9)));
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(0, s.getNumCaptures(2));
    }

    public void testDeadStoneBlocksCaptureAsOpponent() {
        s.addMove(pt(11, 9));                      // P2 own
        s.addMove(dead(10, 9));                    // P2 dead between centre and (11,9)
        s.addMove(pt(12, 9));                      // P1: pattern P1 dead P2 P1 is not a capture
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(2, s.getPosition(pt(11, 9)));
        assertEquals(3, s.getPosition(pt(10, 9)));
    }

    public void testDeadStoneBlocksCaptureAsOwn() {
        s.addMove(pt(0, 0));                       // P2
        s.addMove(dead(11, 9));
        ownOnly(pt(10, 9));                        // P1: (9,9),(10,9)
        s.addMove(pt(8, 9));                       // P2: P2 P1 P1 dead -> no capture
        assertEquals(0, s.getNumCaptures(2));
        assertEquals(1, s.getPosition(pt(9, 9)));
        assertEquals(1, s.getPosition(pt(10, 9)));
    }

    public void testDeadStoneNeverCaptures() {
        ownOnly(pt(10, 9));                        // P2
        ownOnly(pt(0, 18));                        // P1
        ownOnly(pt(11, 9));                        // P2
        s.addMove(PASS);                           // P1 passes own
        s.addMove(dead(12, 9));                    // P1 dead at the capture point
        assertEquals(2, s.getPosition(pt(10, 9)));
        assertEquals(2, s.getPosition(pt(11, 9)));
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(0, s.getNumCaptures(2));
    }

    public void testCaptureDoesNotWrapAcrossEdge() {
        ownOnly(pt(17, 9));                        // P2
        ownOnly(pt(0, 10));                        // P1: linear index 190 = 189 + 1
        ownOnly(pt(18, 9));                        // P2
        s.addMove(pt(16, 9));                      // P1: east is (17,9) P2, (18,9) P2, then the edge
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(2, s.getPosition(pt(17, 9)));
        assertEquals(2, s.getPosition(pt(18, 9)));
    }

    public void testFiveWins() {
        int[] p1 = {pt(10, 9), pt(11, 9), pt(12, 9)};
        int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6)};
        for (int i = 0; i < 3; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
        ownOnly(p2[3]);
        assertTrue(s.isValidMove(pt(13, 9), 1));
        s.addMove(pt(13, 9));                      // (9..13, 9)
        assertTrue(s.isGameOver());
        assertEquals(1, s.getWinner());
        assertFalse(s.isAwaitingDeadStone());      // D4: no dead stone after a winning stone
        assertFalse(s.isValidMove(dead(0, 18), 1));
        try { s.addMove(dead(0, 18)); fail("entry after game over"); } catch (IllegalArgumentException expected) { }
        try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
    }

    public void testOverlineWins() {
        int[] p1 = {pt(10, 9), pt(11, 9), pt(13, 9), pt(14, 9)};
        int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6), pt(0, 8)};
        for (int i = 0; i < 4; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
        ownOnly(p2[4]);
        s.addMove(pt(12, 9));                      // (9..14, 9): six in a row
        assertTrue(s.isGameOver());
        assertEquals(1, s.getWinner());
    }

    public void testDeadStoneIsNotCountedInLine() {
        s.addMove(pt(0, 0));                       // P2
        s.addMove(dead(13, 9));
        int[] p1 = {pt(10, 9), pt(11, 9), pt(12, 9)};
        int[] p2 = {pt(0, 2), pt(0, 4), pt(0, 6)};
        for (int i = 0; i < 3; i++) { ownOnly(p1[i]); ownOnly(p2[i]); }
        s.addMove(pt(14, 9));                      // P1 P1 P1 P1 dead P1: not a five
        assertFalse(s.isGameOver());
        assertTrue(s.isAwaitingDeadStone());
    }

    public void testFiveDoesNotWrapAcrossEdge() {
        int[] p1 = {pt(16, 9), pt(17, 9), pt(18, 9), pt(0, 10)};
        int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6)};
        for (int i = 0; i < 4; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
        ownOnly(pt(0, 8));                         // P2
        s.addMove(pt(1, 10));                      // linear 187..191 are consecutive, but not a line
        assertFalse(s.isGameOver());
    }

    public void testFifthCaptureWins() {
        int[] rows = {1, 4, 7, 13, 16};
        for (int k = 0; k < 5; k++) {
            int r = rows[k];
            ownOnly(pt(3, r));                     // P2
            ownOnly(pt(2, r));                     // P1 anchor
            ownOnly(pt(4, r));                     // P2
            s.addMove(pt(5, r));                   // P1 captures (4,r),(3,r)
            assertEquals(2 * (k + 1), s.getNumCaptures(1));
            if (k < 4) { assertFalse(s.isGameOver()); s.skipDeadStone(); }
        }
        assertTrue(s.isGameOver());
        assertEquals(1, s.getWinner());
    }

    public void testSixEmptyTurnsDraw() {
        for (int i = 0; i < 5; i++) ownOnly(PASS);
        assertFalse(s.isGameOver());
        assertEquals(5, s.getConsecutiveEmptyTurns());
        ownOnly(PASS);
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner());
        assertEquals(6, s.getConsecutiveEmptyTurns());
        assertFalse(s.isValidMove(pt(0, 0), s.getCurrentPlayer()));
    }

    public void testNonEmptyTurnResetsEmptyCounter() {
        for (int i = 0; i < 5; i++) ownOnly(PASS);
        ownOnly(pt(0, 0));                         // turn 6 (P1): own only resets
        assertEquals(0, s.getConsecutiveEmptyTurns());
        for (int i = 0; i < 5; i++) ownOnly(PASS);
        assertFalse(s.isGameOver());
        s.addMove(PASS);                           // dead only also resets
        s.addMove(dead(1, 1));
        assertEquals(0, s.getConsecutiveEmptyTurns());
        assertFalse(s.isGameOver());
    }

    public void testFullBoardDrawAfterLongHistory() {
        for (int q = 0; q < 361; q++) {
            if (q == 180) continue;
            s.addMove(PASS);
            s.addMove(361 + q);
            if (s.isGameOver()) break;
            for (int k = 0; k < 5; k++) ownOnly(PASS);
        }
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner());
        assertEquals(1 + 360 * 2 + 359 * 5, s.getNumMoves());   // 2516 entries > the old 720 slots
    }

    /** Spec §3.4: an own stone that fills the last point without winning is a draw; a capture gives 2 points back. */
    public void testFullBoardDrawByOwnStoneAfterCapture() {
        ownOnly(pt(3, 1));                         // P2
        ownOnly(pt(2, 1));                         // P1 anchor
        ownOnly(pt(4, 1));                         // P2
        s.addMove(pt(5, 1));                       // P1 captures (4,1),(3,1): both points are empty again
        assertEquals(2, s.getNumCaptures(1));
        s.skipDeadStone();
        int last = pt(18, 18);
        for (int q = 0; q < 361; q++) {
            if (q == 180 || q == pt(2, 1) || q == pt(5, 1) || q == last) continue;
            s.addMove(PASS);
            s.addMove(361 + q);                    // fills (3,1) and (4,1) too
            assertFalse("ended early at point " + q, s.isGameOver());   // catches a missing +2 per capture
        }
        s.addMove(last);                           // own stone on the last empty point, no five, no capture
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner());
        assertFalse(s.isAwaitingDeadStone());
        assertEquals(1 + 4 + 357 * 2 + 1, s.getNumMoves());
    }
    // Spec §17 names a "2812-entry worst case". That bound (§3.5) needs refills of captured
    // points and is impractical to script; this 2516-entry legal history is
    // capture-free, exercises the capacity constructor far past 720, and MAX_ENTRIES = 3000
    // covers 2812 by the §3.5 argument.
}
