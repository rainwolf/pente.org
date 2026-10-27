package org.pente.game.test;

import junit.framework.*;

import org.pente.game.*;

public class TombstoneZobristTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneZobristTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TombstoneZobristTest.class);
    }

    public TombstoneZobristTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
    private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }

    public void testRowLengths() {
        long[][] r = ZobristUtil.rand;
        assertEquals(5, r.length);
        assertEquals(723, r[0].length);
        assertEquals(723, r[1].length);
        assertEquals(720, r[2].length);
        assertEquals(721, r[3].length);
        assertEquals(361, r[ZobristUtil.TOMBSTONE_DEAD_ROW].length);
        assertEquals(4, ZobristUtil.TOMBSTONE_DEAD_ROW);
    }

    public void testDeadRowIsNonZeroDistinctAndDisjoint() {
        java.util.Set<Long> existing = new java.util.HashSet<>();
        int total = 0;
        for (int row = 0; row < 4; row++) {
            for (long v : ZobristUtil.rand[row]) { existing.add(v); total++; }
        }
        assertEquals(2887, total);
        assertEquals(2868, existing.size());   // pins that rows 0-3 were not edited
        java.util.Set<Long> dead = new java.util.HashSet<>();
        for (long v : ZobristUtil.rand[4]) {
            assertTrue("zero value", v != 0L);
            assertTrue("duplicate in row 4: " + v, dead.add(v));
            assertFalse("row 4 value already in rows 0-3: " + v, existing.contains(v));
        }
        assertEquals(361, dead.size());
    }

    public void testExistingRowSpotValuesUnchanged() {
        assertEquals(-473272652262568974L, ZobristUtil.rand[0][0]);
        assertEquals(6938546511637388830L, ZobristUtil.rand[1][0]);
        assertEquals(-653791976413045955L, ZobristUtil.rand[2][0]);
        assertEquals(-5349835967319192806L, ZobristUtil.rand[3][720]);
    }
}
