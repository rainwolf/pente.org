package org.pente.webdb.test;

import java.sql.*;
import java.util.*;

import junit.framework.*;

import org.apache.log4j.BasicConfigurator;

import org.pente.database.*;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;

import org.pente.webdb.MySQLWebDbStorer;
import org.pente.webdb.dto.AnalysisMeta;
import org.pente.webdb.dto.WebDbGameData;

/**
 * DB-backed test for {@link MySQLWebDbStorer}. Runs against the live local
 * MariaDB container (the production-backed DB), built the same way
 * {@code PositionStatsHandlerTest} / {@code GameEndpointsTest} do — env
 * {@code MYSQL_USER}/{@code MYSQL_PASSWORD}/{@code MYSQL_DATABASE}, host default
 * {@code 127.0.0.1:3316}. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.test.MySQLWebDbStorerTest} with
 * those variables exported (source {@code pente.org/.env}).
 *
 * <p>Every row this test writes is scoped to the sentinel pid
 * {@code 999999999}; {@link #tearDown} deletes those rows from all three
 * {@code webdb_*} tables after each test AND asserts zero residue, so the suite
 * never leaves data behind in the shared DB.
 */
public class MySQLWebDbStorerTest extends TestCase {

    private static final int PENTE = GridStateFactory.PENTE; // 1
    private static final long PID = 999999999L;      // sentinel owner
    private static final long OTHER_PID = 999999998L; // never written to

    private DBHandler dbHandler;
    private MySQLWebDbStorer storer;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                MySQLWebDbStorerTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(MySQLWebDbStorerTest.class);
    }

    public MySQLWebDbStorerTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();

        String user = System.getenv("MYSQL_USER");
        String pass = System.getenv("MYSQL_PASSWORD");
        String db = System.getenv("MYSQL_DATABASE");
        String host = System.getenv("WEBDB_TEST_HOST");
        if (host == null || host.length() == 0) {
            host = "127.0.0.1:3316";
        }
        if (user == null || pass == null || db == null) {
            fail("set MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE in the "
                    + "environment (source pente.org/.env) before running this test");
        }

        try {
            dbHandler = new MySQLDBHandler(user, pass, db, host);
        } catch (Throwable t) {
            throw new Exception("could not open local DB at " + host, t);
        }
        storer = new MySQLWebDbStorer(dbHandler);

        // pre-clean in case a previous crashed run left sentinel rows
        purgeSentinel();
    }

    protected void tearDown() throws Exception {
        try {
            purgeSentinel();
            // zero-residue verification: nothing must remain for the sentinel
            assertEquals("webdb_move residue", 0, countSentinel("webdb_move"));
            assertEquals("webdb_game residue", 0, countSentinel("webdb_game"));
            assertEquals("webdb_analysis residue", 0, countSentinel("webdb_analysis"));
        } finally {
            if (dbHandler != null) {
                dbHandler.destroy();
            }
        }
    }

    // ------------------------------------------------------------------
    // collection
    // ------------------------------------------------------------------

    /** store -> load preserves every header field and the full move list. */
    public void testStoreLoadRoundTrip() throws Exception {
        int[] moves = new int[]{180, 100, 300, 50, 250};
        WebDbGameData g = game(moves, 1);
        g.site = "MySite";
        g.event = "MyEvent";
        g.round = "R1";
        g.section = "A";
        long secs = System.currentTimeMillis() / 1000L;
        g.playDate = new java.util.Date(secs * 1000L); // whole-second precision

        long wgid = storer.storeGame(PID, g);
        assertTrue("wgid must be assigned", wgid > 0);
        assertEquals("storeGame back-fills g.wgid", wgid, g.wgid);

        WebDbGameData loaded = storer.loadGame(PID, wgid);
        assertNotNull("stored game must load back", loaded);
        assertEquals(wgid, loaded.wgid);
        assertEquals(PID, loaded.pid);
        assertEquals(PENTE, loaded.game);
        assertEquals("player1", g.player1, loaded.player1);
        assertEquals("player2", g.player2, loaded.player2);
        assertEquals("winner", 1, loaded.winner);
        assertEquals("site", "MySite", loaded.site);
        assertEquals("event", "MyEvent", loaded.event);
        assertEquals("round", "R1", loaded.round);
        assertEquals("section", "A", loaded.section);
        assertNotNull("play_date preserved", loaded.playDate);
        assertEquals("play_date (to second)",
                secs, loaded.playDate.getTime() / 1000L);
        assertNotNull("imported is DB-assigned", loaded.imported);
        assertTrue("move list round-trips",
                Arrays.equals(moves, loaded.moves));
    }

    /**
     * The move rows match the GridState hashes/rotations index-by-index (three
     * sampled indices) and terminate with next_move == 361 on the last move.
     */
    public void testMoveRowSemantics() throws Exception {
        int[] moves = new int[]{180, 100, 300, 50, 250, 20};
        long wgid = storer.storeGame(PID, game(moves, 1));

        GridState state =
                GridStateFactory.createGridState(PENTE, moveDataOf(moves));
        int n = moves.length;

        // exactly moves.length rows
        assertEquals("one move row per move", n, moveRowCount(wgid));

        // sample first, a middle, and the last index
        int[] sample = new int[]{0, n / 2, n - 1};
        for (int s = 0; s < sample.length; s++) {
            int i = sample[s];
            long[] row = moveRow(wgid, i); // {next_move, hash_key, rotation}
            int expectedNext = (i == n - 1) ? 361 : moves[i + 1];
            assertEquals("next_move at " + i, expectedNext, (int) row[0]);
            assertEquals("hash_key at " + i, state.getHash(i), row[1]);
            assertEquals("rotation at " + i,
                    state.getRotation(i), (int) row[2]);
        }
        // explicit terminal check
        assertEquals("last row is terminal",
                361, (int) moveRow(wgid, n - 1)[0]);
    }

    /** findDuplicate hits an identical game and misses after one move changes. */
    public void testFindDuplicateHitAndMiss() throws Exception {
        int[] moves = new int[]{180, 100, 300, 50, 250};
        long wgid = storer.storeGame(PID, game(moves, 1));

        // identical move list -> duplicate found
        WebDbGameData same = game(moves.clone(), 1);
        Long hit = storer.findDuplicate(PID, same);
        assertNotNull("identical game must be detected as a duplicate", hit);
        assertEquals("duplicate resolves to the stored wgid",
                wgid, hit.longValue());

        // change the last move -> no duplicate
        int[] different = moves.clone();
        different[different.length - 1] = 260;
        Long miss = storer.findDuplicate(PID, game(different, 1));
        assertNull("a game with a differing move is not a duplicate", miss);
    }

    /** deleteGame removes rows from both webdb_move and webdb_game. */
    public void testDeleteGameRemovesBothTables() throws Exception {
        int[] moves = new int[]{180, 100, 300, 50, 250};
        long wgid = storer.storeGame(PID, game(moves, 1));

        assertTrue("move rows exist before delete", moveRowCount(wgid) > 0);
        assertNotNull("game loads before delete", storer.loadGame(PID, wgid));

        assertTrue("deleteGame reports success", storer.deleteGame(PID, wgid));

        assertEquals("no move rows after delete", 0, moveRowCount(wgid));
        assertNull("game gone after delete", storer.loadGame(PID, wgid));

        // deleting a non-existent game reports false
        assertTrue("deleting a missing game is a no-op",
                !storer.deleteGame(PID, wgid));
    }

    /** A game owned by PID does not load for a different pid. */
    public void testCrossPidLoadReturnsNull() throws Exception {
        int[] moves = new int[]{180, 100, 300, 50, 250};
        long wgid = storer.storeGame(PID, game(moves, 1));

        assertNull("another player cannot load this game",
                storer.loadGame(OTHER_PID, wgid));
        assertNotNull("owner still loads it", storer.loadGame(PID, wgid));
    }

    /** positionStats over two games sharing a prefix sums the counts. */
    public void testPositionStatsSumsOverTwoGames() throws Exception {
        // both games pass through the position after [180, 100]
        int[] a = new int[]{180, 100, 300, 50, 250};
        int[] b = new int[]{180, 100, 250, 120, 40};
        storer.storeGame(PID, game(a, 1));
        storer.storeGame(PID, game(b, 2));

        // canonical hash of the shared 2-move position; move_num = 1
        GridState prefix = GridStateFactory.createGridState(
                PENTE, moveDataOf(new int[]{180, 100}));
        long hash = prefix.getHash(1);

        List<long[]> rows = storer.positionStats(PID, hash, 1, PENTE);
        assertTrue("shared position must return rows", !rows.isEmpty());

        long total = 0;
        for (int i = 0; i < rows.size(); i++) {
            total += rows.get(i)[3]; // count column
        }
        assertEquals("both games counted at the shared position", 2, total);
    }

    // ------------------------------------------------------------------
    // analyses
    // ------------------------------------------------------------------

    /** analyses store/load/list/update/delete round-trip. */
    public void testAnalysesCrudRoundTrip() throws Exception {
        String tree = "{\"root\":{\"move\":180,\"children\":[]}}";
        long aid = storer.storeAnalysis(PID, "Opening study", PENTE, tree);
        assertTrue("aid assigned", aid > 0);

        AnalysisMeta meta = new AnalysisMeta();
        String loaded = storer.loadAnalysis(PID, aid, meta);
        assertEquals("tree round-trips", tree, loaded);
        assertEquals("meta aid", aid, meta.aid);
        assertEquals("meta name", "Opening study", meta.name);
        assertEquals("meta game", PENTE, meta.game);
        assertNotNull("created assigned", meta.created);
        assertNotNull("updated assigned", meta.updated);

        // listed for the owner
        List<AnalysisMeta> list = storer.listAnalyses(PID);
        assertTrue("owner's list contains the analysis", containsAid(list, aid));

        // update replaces name + tree
        String tree2 = "{\"root\":{\"move\":180,\"children\":[210]}}";
        assertTrue("update reports success",
                storer.updateAnalysis(PID, aid, "Revised study", tree2));
        AnalysisMeta meta2 = new AnalysisMeta();
        assertEquals("updated tree", tree2, storer.loadAnalysis(PID, aid, meta2));
        assertEquals("updated name", "Revised study", meta2.name);

        // delete
        assertTrue("delete reports success", storer.deleteAnalysis(PID, aid));
        assertNull("gone after delete", storer.loadAnalysis(PID, aid, null));
    }

    /** Another player cannot read, update, or delete an analysis. */
    public void testAnalysesCrossPidDenial() throws Exception {
        String tree = "{\"root\":{\"move\":180}}";
        long aid = storer.storeAnalysis(PID, "Private", PENTE, tree);

        assertNull("cross-pid load denied",
                storer.loadAnalysis(OTHER_PID, aid, new AnalysisMeta()));
        assertTrue("cross-pid update denied",
                !storer.updateAnalysis(OTHER_PID, aid, "Hijacked", "{}"));
        assertTrue("cross-pid delete denied",
                !storer.deleteAnalysis(OTHER_PID, aid));

        // owner's copy is untouched
        assertEquals("owner's analysis is unchanged",
                tree, storer.loadAnalysis(PID, aid, null));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static WebDbGameData game(int[] moves, int winner) {
        WebDbGameData g = new WebDbGameData();
        g.game = PENTE;
        g.player1 = "alice";
        g.player2 = "bob";
        g.winner = winner;
        g.moves = moves;
        return g;
    }

    private static boolean containsAid(List<AnalysisMeta> list, long aid) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).aid == aid) {
                return true;
            }
        }
        return false;
    }

    private static MoveData moveDataOf(final int[] moves) {
        return new MoveData() {
            public void addMove(int move) {
                throw new UnsupportedOperationException();
            }
            public void undoMove() {
                throw new UnsupportedOperationException();
            }
            public int getMove(int num) {
                return moves[num];
            }
            public int getNumMoves() {
                return moves.length;
            }
            public int[] getMoves() {
                return moves;
            }
        };
    }

    private int moveRowCount(long wgid) throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            PreparedStatement stmt = con.prepareStatement(
                    "select count(*) from webdb_move where wgid = ?");
            try {
                stmt.setLong(1, wgid);
                ResultSet rs = stmt.executeQuery();
                rs.next();
                return rs.getInt(1);
            } finally {
                stmt.close();
            }
        } finally {
            dbHandler.freeConnection(con);
        }
    }

    /** @return {next_move, hash_key, rotation} for one move row. */
    private long[] moveRow(long wgid, int moveNum) throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            PreparedStatement stmt = con.prepareStatement(
                    "select next_move, hash_key, rotation from webdb_move "
                            + "where wgid = ? and move_num = ?");
            try {
                stmt.setLong(1, wgid);
                stmt.setInt(2, moveNum);
                ResultSet rs = stmt.executeQuery();
                assertTrue("row " + moveNum + " must exist", rs.next());
                return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
            } finally {
                stmt.close();
            }
        } finally {
            dbHandler.freeConnection(con);
        }
    }

    private int countSentinel(String table) throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            PreparedStatement stmt = con.prepareStatement(
                    "select count(*) from " + table + " where pid = ?");
            try {
                stmt.setLong(1, PID);
                ResultSet rs = stmt.executeQuery();
                rs.next();
                return rs.getInt(1);
            } finally {
                stmt.close();
            }
        } finally {
            dbHandler.freeConnection(con);
        }
    }

    private void purgeSentinel() throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            deleteSentinel(con, "webdb_move");
            deleteSentinel(con, "webdb_game");
            deleteSentinel(con, "webdb_analysis");
        } finally {
            dbHandler.freeConnection(con);
        }
    }

    private void deleteSentinel(Connection con, String table) throws Exception {
        PreparedStatement stmt = con.prepareStatement(
                "delete from " + table + " where pid = ?");
        try {
            stmt.setLong(1, PID);
            stmt.executeUpdate();
        } finally {
            stmt.close();
        }
    }
}
