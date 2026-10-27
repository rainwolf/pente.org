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


    private static final int PASS = 722;
    private static int pt(int x, int y) { return x + 19 * y; }
    private static int dead(int x, int y) { return 361 + pt(x, y); }

    /** Canonical hash recomputed from the board alone: max over 8 rotations of
     *  stones/dead stones at rotated points, XOR the (unrotated) capture-count keys. */
    private static long expectedCanonical(TombstoneState s) {
        long max = Long.MIN_VALUE;
        for (int rot = 0; rot < 8; rot++) {
            long h = 0;
            for (int q = 0; q < 361; q++) {
                int v = s.getPosition(q);
                int r = s.rotateMove(q, rot);
                if (v == 1) h ^= ZobristUtil.rand[0][r];
                else if (v == 2) h ^= ZobristUtil.rand[1][r];
                else if (v == 3) h ^= ZobristUtil.rand[ZobristUtil.TOMBSTONE_DEAD_ROW][r];
            }
            if (s.getNumCaptures(1) > 0) h ^= ZobristUtil.rand[2][s.getNumCaptures(1)];
            if (s.getNumCaptures(2) > 0) h ^= ZobristUtil.rand[3][s.getNumCaptures(2)];
            max = Math.max(max, h);
        }
        return max;
    }

    /** Mixed game: own+dead, implicit closes, a P1 capture, a dead stone on the vacated point, passes. */
    private static final int[] SCRIPT = {
            180, pt(10, 9), dead(3, 3), pt(0, 18), pt(11, 9), pt(12, 9), dead(10, 9),
            PASS, dead(4, 4), PASS, pt(5, 5)};

    private static TombstoneState replay(int[] codes) {
        TombstoneState s = new TombstoneState();
        for (int c : codes) s.addMove(c);
        return s;
    }

    public void testHashIsPureFunctionOfPositionAfterEveryEntry() {
        TombstoneState s = new TombstoneState();
        for (int i = 0; i < SCRIPT.length; i++) {
            s.addMove(SCRIPT[i]);
            assertEquals("entry " + i, expectedCanonical(s), s.getHash());
            assertEquals(s.getHash(), s.getHash(i));
        }
        assertEquals(2, s.getNumCaptures(1));       // the script really captured
    }

    public void testPassKeepsHashSlot() {
        TombstoneState s = replay(new int[]{180, pt(3, 3), dead(4, 4)});
        s.addMove(PASS);
        assertTrue(s.getHash(2) != 0);                 // a constant-0 hash would pass the slot check
        assertEquals(s.getHash(2), s.getHash(3));
        assertEquals(s.getRotation(2), s.getRotation(3));
    }

    public void testTwoMoveOrdersGiveSameHash() {
        TombstoneState sa = replay(new int[]{180, pt(3, 3), dead(5, 5), pt(4, 4), dead(6, 6)});
        long a = sa.getHash();
        assertTrue(a != 0);
        assertEquals(expectedCanonical(sa), a);
        long b = replay(new int[]{180, pt(3, 3), dead(6, 6), pt(4, 4), dead(5, 5)}).getHash();
        long c = replay(new int[]{180, pt(3, 3), dead(5, 5), pt(4, 4), dead(6, 6), PASS, PASS}).getHash();
        assertEquals(a, b);
        assertEquals(a, c);
    }

    public void testEightRotationsShareCanonicalHash() {
        TombstoneState proto = new TombstoneState();
        TombstoneState baseState = replay(SCRIPT);
        long base = baseState.getHash();
        assertTrue(base != 0);
        assertEquals(expectedCanonical(baseState), base);
        for (int k = 0; k < 8; k++) {
            int[] rotated = new int[SCRIPT.length];
            for (int i = 0; i < SCRIPT.length; i++) {
                int c = SCRIPT[i];
                rotated[i] = TombstoneCodes.isOwnStone(c) ? proto.rotateMove(c, k)
                        : TombstoneCodes.isDeadStone(c) ? 361 + proto.rotateMove(c - 361, k) : c;
            }
            TombstoneState r = replay(rotated);
            assertEquals("rotation " + k, 2, r.getNumCaptures(1));
            assertEquals("rotation " + k, base, r.getHash());
        }
    }

    public void testDeadStoneHashDiffersFromOwnStone() {
        long stone = replay(new int[]{180, pt(3, 3)}).getHash();
        long deadStone = replay(new int[]{180, PASS, dead(3, 3)}).getHash();
        assertTrue(stone != deadStone);
        assertTrue(deadStone != replay(new int[]{180}).getHash());
    }

    public void testHashSlotsBeyondOldCapacity() {
        TombstoneState s = new TombstoneState();
        s.addMove(180);
        for (int q = 0; s.getNumMoves() <= 800; q++) {
            if (q == 180) continue;
            s.addMove(PASS);
            s.addMove(361 + q);
            for (int k = 0; k < 5; k++) { s.addMove(PASS); s.skipDeadStone(); }
        }
        assertEquals(expectedCanonical(s), s.getHash());
        assertEquals(s.getHash(), s.getHash(s.getNumMoves() - 1));
    }
}
