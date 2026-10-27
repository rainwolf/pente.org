package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

/** Tests for Tombstone replay, undo and live-only queries. */
public class TombstoneUndoReplayTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneUndoReplayTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TombstoneUndoReplayTest.class);
    }

    public TombstoneUndoReplayTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
    private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }

    private static final int PASS = 722;
    private static int pt(int x, int y) { return x + 19 * y; }
    private static int dead(int x, int y) { return 361 + pt(x, y); }

    private static TombstoneState replayOf(int... codes) {
        DefaultGameData d = new DefaultGameData();
        for (int c : codes) d.addMove(c);
        return (TombstoneState) new TombstoneState().getInstance(d);
    }

    private static int[] prefix(int[] codes, int n) { return java.util.Arrays.copyOf(codes, n); }

    /** Six turns played through the live API: a capture, a dead stone on the vacated point, skips. */
    private static TombstoneState live() {
        TombstoneState s = new TombstoneState();
        s.addMove(180);
        s.addMove(pt(10, 9)); s.skipDeadStone();        // t1 P2 own only
        s.addMove(pt(0, 18)); s.skipDeadStone();        // t2 P1 own only
        s.addMove(pt(11, 9)); s.addMove(dead(3, 3));    // t3 P2 own + dead
        s.addMove(pt(12, 9)); s.addMove(dead(10, 9));   // t4 P1 captures, dead on vacated point
        s.addMove(PASS); s.skipDeadStone();             // t5 P2 empty
        s.addMove(PASS); s.addMove(dead(4, 4));         // t6 P1 dead only
        return s;
    }

    private static void assertSameState(TombstoneState a, TombstoneState b) {
        assertEquals(a.getNumMoves(), b.getNumMoves());
        for (int q = 0; q < 361; q++) assertEquals("point " + q, a.getPosition(q), b.getPosition(q));
        assertEquals(a.getCurrentPlayer(), b.getCurrentPlayer());
        assertEquals(a.isAwaitingDeadStone(), b.isAwaitingDeadStone());
        assertEquals(a.getNumCaptures(1), b.getNumCaptures(1));
        assertEquals(a.getNumCaptures(2), b.getNumCaptures(2));
        assertEquals(a.getConsecutiveEmptyTurns(), b.getConsecutiveEmptyTurns());
        assertEquals(a.getTurnCount(), b.getTurnCount());
        assertEquals(a.isGameOver(), b.isGameOver());
        assertEquals(a.getWinner(), b.getWinner());
        for (int i = 0; i < a.getNumMoves(); i++) {
            assertEquals("hash " + i, a.getHash(i), b.getHash(i));
            assertEquals("rotation " + i, a.getRotation(i), b.getRotation(i));
            assertEquals("color " + i, a.getColor(i), b.getColor(i));
        }
    }

    public void testReplayEqualsLiveState() {
        TombstoneState s = live();
        assertEquals(10, s.getNumMoves());
        assertSameState(s, replayOf(s.getMoves()));
    }

    public void testReplayAgreesWithCodesCurrentPlayerForEveryPrefix() {
        int[] codes = live().getMoves();
        for (int n = 0; n <= codes.length; n++) {
            assertEquals("prefix " + n, TombstoneCodes.currentPlayer(codes, n),
                    replayOf(prefix(codes, n)).getCurrentPlayer());
        }
    }

    public void testReplayClosesTrailingOwnEntry() {
        TombstoneState r = replayOf(180, pt(3, 3));
        assertFalse(r.isAwaitingDeadStone());
        assertEquals(1, r.getCurrentPlayer());
        assertTrue(r.isTrailingTurnClosedWithoutDead());
        assertEquals(0, r.getConsecutiveEmptyTurns());
        TombstoneState p = replayOf(180, pt(3, 3), PASS);
        assertEquals(1, p.getConsecutiveEmptyTurns());
        assertEquals(2, p.getCurrentPlayer());
    }

    public void testReplayOfSixEmptyTurnsIsDraw() {
        TombstoneState five = replayOf(180, PASS, PASS, PASS, PASS, PASS);
        assertFalse(five.isGameOver());
        assertEquals(5, five.getConsecutiveEmptyTurns());
        assertEquals(1, five.getCurrentPlayer());
        TombstoneState six = replayOf(180, PASS, PASS, PASS, PASS, PASS, PASS);
        assertTrue(six.isGameOver());
        assertEquals(0, six.getWinner());
    }

    public void testReplayRejectsDeadWithoutOwn() {
        try { replayOf(180, dead(3, 3)); fail(); } catch (IllegalArgumentException expected) { }
    }

    public void testReplayOfEmptyData() {
        TombstoneState r = replayOf();
        assertEquals(0, r.getNumMoves());
        assertEquals(1, r.getCurrentPlayer());
        assertFalse(r.isTrailingTurnClosedWithoutDead());
    }

    public void testCanPlayerUndoRules() {
        TombstoneState s = replayOf(180);
        assertFalse(s.canPlayerUndo(1));                 // nobody undoes the centre turn
        assertFalse(s.canPlayerUndo(2));
        s.addMove(pt(3, 3));
        assertFalse(s.canPlayerUndo(1));                 // mid-turn
        assertFalse(s.canPlayerUndo(2));
        s.addMove(dead(4, 4));
        assertTrue(s.canPlayerUndo(2));                  // P2 completed the last turn
        assertFalse(s.canPlayerUndo(1));
        TombstoneState over = replayOf(180, PASS, PASS, PASS, PASS, PASS, PASS);
        assertFalse(over.canPlayerUndo(1));
        assertFalse(over.canPlayerUndo(2));
    }

    public void testUndoTurnTwoEntries() {
        TombstoneState s = replayOf(180);
        s.addMove(pt(3, 3));
        s.addMove(dead(4, 4));
        assertEquals(2, s.getLastTurnLength());
        assertEquals(2, s.undoTurn());
        assertEquals(1, s.getNumMoves());
        assertEquals(0, s.getPosition(pt(3, 3)));
        assertEquals(0, s.getPosition(pt(4, 4)));
        assertEquals(2, s.getCurrentPlayer());
        assertEquals(replayOf(180).getHash(), s.getHash());
        assertEquals(0, s.getLastTurnLength());
    }

    public void testUndoTurnOneEntry() {
        TombstoneState s = replayOf(180);
        s.addMove(pt(3, 3));
        s.skipDeadStone();
        assertEquals(1, s.getLastTurnLength());
        assertEquals(1, s.undoTurn());
        assertEquals(1, s.getNumMoves());
        assertEquals(2, s.getCurrentPlayer());
        assertFalse(s.isAwaitingDeadStone());
    }

    public void testUndoTurnRestoresCapturesAndHash() {
        TombstoneState s = live();
        int[] codes = s.getMoves();
        assertEquals(2, s.undoTurn());                   // t6: PASS + dead
        assertEquals(1, s.undoTurn());                   // t5: PASS
        assertEquals(2, s.getNumCaptures(1));
        assertEquals(2, s.undoTurn());                   // t4: the capturing stone + dead
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(2, s.getPosition(pt(10, 9)));
        assertEquals(2, s.getPosition(pt(11, 9)));
        assertEquals(0, s.getPosition(pt(12, 9)));
        assertEquals(1, s.getCurrentPlayer());
        assertSameState(replayOf(prefix(codes, 5)), s);
    }

    public void testUndoTurnRestoresEmptyTurnCounter() {
        TombstoneState s = replayOf(180);
        for (int i = 0; i < 4; i++) { s.addMove(PASS); s.skipDeadStone(); }
        s.addMove(pt(0, 0)); s.skipDeadStone();          // t5, P2
        assertEquals(0, s.getConsecutiveEmptyTurns());
        assertEquals(1, s.undoTurn());
        assertEquals(4, s.getConsecutiveEmptyTurns());
        assertEquals(2, s.getCurrentPlayer());
    }

    public void testUndoTurnOnCentreOnlyThrows() {
        try { replayOf(180).undoTurn(); fail(); } catch (IllegalStateException expected) { }
        try { new TombstoneState().undoTurn(); fail(); } catch (IllegalStateException expected) { }
    }

    public void testUndoMoveLeavesTurnAwaiting() {
        TombstoneState s = replayOf(180);
        s.addMove(pt(3, 3));
        s.addMove(dead(4, 4));
        s.undoMove();
        assertEquals(2, s.getNumMoves());
        assertTrue(s.isAwaitingDeadStone());
        assertEquals(2, s.getCurrentPlayer());
        assertEquals(2, s.getPosition(pt(3, 3)));
        assertEquals(0, s.getPosition(pt(4, 4)));
    }

    public void testIsTrailingTurnClosedWithoutDead() {
        TombstoneState s = replayOf(180);
        assertFalse(s.isTrailingTurnClosedWithoutDead());
        s.addMove(pt(3, 3));
        assertFalse(s.isTrailingTurnClosedWithoutDead()); // still awaiting
        s.skipDeadStone();
        assertTrue(s.isTrailingTurnClosedWithoutDead());
        s.addMove(pt(4, 4));
        s.addMove(dead(5, 5));
        assertFalse(s.isTrailingTurnClosedWithoutDead());
        s.addMove(PASS);
        s.skipDeadStone();
        assertTrue(s.isTrailingTurnClosedWithoutDead());
    }

    public void testLastTurnLengthWhileAwaiting() {
        TombstoneState s = replayOf(180);
        assertEquals(0, s.getLastTurnLength());
        s.addMove(pt(3, 3));
        assertEquals(1, s.getLastTurnLength());
        s.addMove(dead(4, 4));
        assertEquals(2, s.getLastTurnLength());
    }

    public void testClearResets() {
        TombstoneState s = live();
        s.clear();
        assertEquals(0, s.getNumMoves());
        assertEquals(1, s.getCurrentPlayer());
        assertEquals(0, s.getNumCaptures(1));
        assertEquals(0, s.getTurnCount());
        assertEquals(0, s.getConsecutiveEmptyTurns());
        assertFalse(s.isGameOver());
        assertEquals(0, s.getPosition(pt(4, 4)));
        s.addMove(180);
        assertEquals(2, s.getCurrentPlayer());
    }
}
