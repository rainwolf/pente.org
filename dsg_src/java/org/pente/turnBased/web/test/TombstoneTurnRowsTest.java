package org.pente.turnBased.web.test;

import java.util.List;

import junit.framework.*;

import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.web.TombstoneTurnRows;

/** One move-list row per Tombstone turn, shared by every JSP. */
public class TombstoneTurnRowsTest extends TestCase {

    public TombstoneTurnRowsTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TombstoneTurnRowsTest.class.getName()});
    }

    private static String labels(List<TombstoneTurnRows.Row> rows) {
        StringBuilder b = new StringBuilder();
        for (TombstoneTurnRows.Row r : rows) b.append(r.number).append(':').append(r.start).append('-')
                .append(r.end).append(':').append(r.label).append('|');
        return b.toString();
    }

    public void testEmpty() { assertEquals(0, TombstoneTurnRows.rows(new int[0], 0).size()); }

    public void testCentreOnly() { assertEquals("1:0-0:K10|", labels(TombstoneTurnRows.rows(new int[]{180}, 1))); }

    public void testMixedTurns() {
        int[] c = {180, 181, 561, 722, 722, 361, 200};
        assertEquals("1:0-0:K10|2:1-2:L10 + dead L9|3:3-3:PASS|4:4-5:PASS + dead A19|5:6-6:L9|",
                labels(TombstoneTurnRows.rows(c, c.length)));
    }

    public void testOrphanDeadEntryGetsOwnRow() {
        assertEquals("1:0-0:K10|2:1-1:dead L9|", labels(TombstoneTurnRows.rows(new int[]{180, 561}, 2)));
        assertEquals("1:0-0:K10|2:1-2:L10 + dead L9|3:3-3:dead M9|",
                labels(TombstoneTurnRows.rows(new int[]{180, 181, 561, 562}, 4)));
    }

    public void testOutOfRangeCodeNeverThrows() {
        assertEquals("1:0-0:K10|2:1-1:?|", labels(TombstoneTurnRows.rows(new int[]{180, 999}, 2)));
        assertEquals("1:0-0:K10|2:1-1:?|3:2-2:dead L9|", labels(TombstoneTurnRows.rows(new int[]{180, 999, 561}, 3)));
    }

    public void testRespectsN() { assertEquals("1:0-0:K10|", labels(TombstoneTurnRows.rows(new int[]{180, 181}, 1))); }

    public void testMoveDataAndListOverloadsAgree() {
        TBGame g = new TBGame(); g.setGame(GridStateFactory.TB_TOMBSTONE);
        for (int c : new int[]{180, 181, 561}) g.addMove(c);
        String expect = labels(TombstoneTurnRows.rows(g.getMoves(), g.getNumMoves()));
        assertEquals(expect, labels(TombstoneTurnRows.rows(g)));
        assertEquals(expect, labels(TombstoneTurnRows.rows(java.util.Arrays.asList(180, 181, 561))));
    }

    public void testIsTombstoneGameNameCoversSpeed() {
        assertTrue(TombstoneTurnRows.isTombstoneGameName("Tombstone Pente"));
        assertTrue(TombstoneTurnRows.isTombstoneGameName("Speed Tombstone Pente"));
        assertTrue(!TombstoneTurnRows.isTombstoneGameName("Pente"));
        assertTrue(!TombstoneTurnRows.isTombstoneGameName(null));
        assertTrue(!TombstoneTurnRows.isTombstoneGameName("No Such Game"));
    }
}
