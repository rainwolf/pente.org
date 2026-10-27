package org.pente.game.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.game.*;

public class MySQLPenteGameStorerTombstoneTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                MySQLPenteGameStorerTombstoneTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(MySQLPenteGameStorerTombstoneTest.class);
    }

    public MySQLPenteGameStorerTombstoneTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean b) { assertTrue(!b); }

    private static GameData game(String name, int... moves) {
        GameData d = new DefaultGameData();
        d.setGame(name);
        for (int m : moves) d.addMove(m);
        return d;
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<Integer>();
        for (int x : a) l.add(x);
        return l;
    }

    private static final int[] MIXED = {180, 200, 361 + 0, 722, 220, 361 + 5, 722};

    public void testLastRowSentinelIs723ForTombstone() {
        GameData d = game("Tombstone Pente", MIXED);
        int n = MIXED.length;
        assertEquals(TombstoneCodes.ARCHIVE_NO_NEXT_MOVE, MySQLPenteGameStorer.archiveNextMove(d, 33, n - 1, n));
        assertEquals(723, MySQLPenteGameStorer.archiveNextMove(d, 34, n - 1, n));
        assertEquals(361, MySQLPenteGameStorer.archiveNextMove(d, 33, 1, n));   // dead on A19 is kept
        assertEquals(722, MySQLPenteGameStorer.archiveNextMove(d, 33, 2, n));
    }

    public void testLastRowSentinelUnchangedForOtherGames() {
        GameData d = game("Pente", 180, 200, 220);
        assertEquals(361, MySQLPenteGameStorer.archiveNextMove(d, GridStateFactory.PENTE, 2, 3));
    }

    public void testLoadSqlIsGameAware() {
        assertTrue(MySQLPenteGameStorer.loadMovesSql(33).contains("next_move != 723"));
        assertTrue(MySQLPenteGameStorer.loadMovesSql(34).contains("next_move != 723"));
        assertTrue(MySQLPenteGameStorer.loadMovesSql(GridStateFactory.PENTE).contains("next_move != 361"));
        assertFalse(MySQLPenteGameStorer.loadMovesSql(GridStateFactory.GO).contains("next_move !="));
    }

    public void testRoundTripKeepsDeadStoneOnPointZero() {
        GameData d = game("Tombstone Pente", MIXED);
        List<Integer> loaded = new ArrayList<Integer>();
        loaded.add(GridStateFactory.getCenterMove(33));                 // load prepends the centre
        for (int i = 0; i < MIXED.length; i++) {
            int next = MySQLPenteGameStorer.archiveNextMove(d, 33, i, MIXED.length);
            if (next != 723) loaded.add(next);                         // the loadGame filter
        }
        assertEquals(toList(MIXED), loaded);
    }

    public void testTrailingOwnEntryKeepsRowAlignment() {
        // resign/timeout while the dead stone was pending: history ends in an open own entry
        GameData d = game("Tombstone Pente", 180, 200, 361 + 201, 220);
        d.setWinner(1);
        GridState s = GridStateFactory.createGridState(33, d);
        assertEquals(d.getNumMoves(), s.getNumMoves());
        assertEquals(723, MySQLPenteGameStorer.archiveNextMove(d, 33, 3, s.getNumMoves()));
    }

    public void testPassRowRepeatsPreviousHash() {
        GameData d = game("Tombstone Pente", 180, 722);
        GridState s = GridStateFactory.createGridState(33, d);
        assertEquals(2, s.getNumMoves());
        assertEquals(s.getHash(0), s.getHash(1));
    }
}
