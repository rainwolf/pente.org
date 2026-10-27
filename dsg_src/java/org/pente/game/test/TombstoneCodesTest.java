package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

/** Tests for the pure Tombstone Pente move codec. */
public class TombstoneCodesTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneCodesTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TombstoneCodesTest.class);
    }

    public TombstoneCodesTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
    private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }

    public void testPredicates() {
        assertTrue(TombstoneCodes.isOwnStone(0));
        assertTrue(TombstoneCodes.isOwnStone(360));
        assertFalse(TombstoneCodes.isOwnStone(361));
        assertFalse(TombstoneCodes.isOwnStone(-1));
        assertTrue(TombstoneCodes.isOwnPass(722));
        assertFalse(TombstoneCodes.isOwnPass(361));
        assertTrue(TombstoneCodes.isOwnEntry(722));
        assertTrue(TombstoneCodes.isOwnEntry(180));
        assertFalse(TombstoneCodes.isOwnEntry(361));
        assertTrue(TombstoneCodes.isDeadStone(361));
        assertTrue(TombstoneCodes.isDeadStone(721));
        assertFalse(TombstoneCodes.isDeadStone(360));
        assertFalse(TombstoneCodes.isDeadStone(722));
        assertFalse(TombstoneCodes.isDeadStone(723));
        assertEquals(361, TombstoneCodes.deadCode(0));
        assertEquals(721, TombstoneCodes.deadCode(360));
        assertEquals(5, TombstoneCodes.pointOf(5));
        assertEquals(5, TombstoneCodes.pointOf(366));
        assertEquals(-1, TombstoneCodes.pointOf(722));
        assertEquals(-1, TombstoneCodes.pointOf(723));
        assertEquals(-1, TombstoneCodes.pointOf(-1));
    }

    public void testCountsAndCurrentPlayer() {
        int[] h = {180, 3, 364, 722, 722, 400};
        assertEquals(4, TombstoneCodes.countOwnEntries(h, 6));
        assertEquals(1, TombstoneCodes.countOwnEntries(h, 1));
        assertEquals(1, TombstoneCodes.currentPlayer(new int[0], 0));
        assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180}, 1));
        assertEquals(1, TombstoneCodes.currentPlayer(new int[]{180, 3}, 2));
        assertEquals(1, TombstoneCodes.currentPlayer(new int[]{180, 3, 364}, 3));
        assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180, 3, 364, 722}, 4));
        // n bounds the scan: trailing array slots are ignored
        assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180, 3, 364}, 1));
    }

    public void testLastTurnLength() {
        assertEquals(0, TombstoneCodes.lastTurnLength(new int[0], 0));
        assertEquals(0, TombstoneCodes.lastTurnLength(new int[]{180}, 1));
        assertEquals(1, TombstoneCodes.lastTurnLength(new int[]{180, 3}, 2));
        assertEquals(1, TombstoneCodes.lastTurnLength(new int[]{180, 722}, 2));
        assertEquals(2, TombstoneCodes.lastTurnLength(new int[]{180, 3, 364}, 3));
        assertEquals(2, TombstoneCodes.lastTurnLength(new int[]{180, 722, 361}, 3));
    }

    public void testValidateTurnShape() {
        String one = "Invalid turn: send one or two moves.";
        String first = "Invalid turn: the first move must be your stone or a pass.";
        String second = "Invalid turn: the second move must be a dead stone.";
        assertNull(TombstoneCodes.validateTurnShape(new int[]{3}));
        assertNull(TombstoneCodes.validateTurnShape(new int[]{3, 365}));
        assertNull(TombstoneCodes.validateTurnShape(new int[]{722}));
        assertNull(TombstoneCodes.validateTurnShape(new int[]{722, 721}));
        assertEquals(one, TombstoneCodes.validateTurnShape(null));
        assertEquals(one, TombstoneCodes.validateTurnShape(new int[0]));
        assertEquals(one, TombstoneCodes.validateTurnShape(new int[]{1, 362, 3}));
        assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{361}));
        assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{723}));
        assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{-1}));
        assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{-2, 365}));
        assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 722}));
        assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 3}));
        assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 723}));
    }

    public void testLabels() {
        assertEquals("K10", TombstoneCodes.coordinate(180));
        assertEquals("A19", TombstoneCodes.coordinate(0));
        assertEquals("H19", TombstoneCodes.coordinate(7));
        assertEquals("J19", TombstoneCodes.coordinate(8));
        assertEquals("T1", TombstoneCodes.coordinate(360));
        assertEquals("K10", TombstoneCodes.entryLabel(180));
        assertEquals("PASS", TombstoneCodes.entryLabel(722));
        assertEquals("dead A19", TombstoneCodes.entryLabel(361));
        assertEquals("dead K10", TombstoneCodes.entryLabel(361 + 180));
        assertEquals("K10", TombstoneCodes.turnLabel(180, -1));
        assertEquals("PASS", TombstoneCodes.turnLabel(722, -1));
        assertEquals("K10 + dead L10", TombstoneCodes.turnLabel(180, 361 + 181));
        assertEquals("PASS + dead A19", TombstoneCodes.turnLabel(722, 361));
        try { TombstoneCodes.entryLabel(723); fail("723 is not an entry"); } catch (IllegalArgumentException expected) { }
        try { TombstoneCodes.entryLabel(-1); fail("-1 is not an entry"); } catch (IllegalArgumentException expected) { }
    }

    public void testPgnTokensRoundTripEveryCode() {
        assertEquals("K10", TombstoneCodes.pgnToken(180));
        assertEquals("pass", TombstoneCodes.pgnToken(722));
        assertEquals("xA19", TombstoneCodes.pgnToken(361));
        for (int code = 0; code <= 722; code++) {
            assertEquals("code " + code, code, TombstoneCodes.parsePgnToken(TombstoneCodes.pgnToken(code)));
        }
        assertEquals(722, TombstoneCodes.parsePgnToken("PASS"));
        assertEquals(361 + 180, TombstoneCodes.parsePgnToken("XK10"));
    }

    public void testParsePgnTokenRejectsGarbage() {
        String[] bad = {null, "", "x", "X", "pas", "Z5", "xZ99", "K0", "K20", "1-0", "1.", "Resign"};
        for (String t : bad) {
            assertEquals("token " + t, -1, TombstoneCodes.parsePgnToken(t));
        }
    }
}
