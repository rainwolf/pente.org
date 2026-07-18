package org.pente.webdb.test;

import java.util.*;

import junit.framework.*;

import org.apache.log4j.BasicConfigurator;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.*;

import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.PositionStatsResponse;

/**
 * DB-backed test for {@link PositionStatsHandler#computeArchive}. It runs
 * against the live local archive (read-only) — a {@link MySQLDBHandler} built
 * the same way the standalone tools do
 * ({@code MySQLDSGPlayerStorerTest} / {@code FastGameLookupTest}), pointed at
 * the local MariaDB container.
 *
 * Connection config comes from the environment ({@code MYSQL_USER},
 * {@code MYSQL_PASSWORD}, {@code MYSQL_DATABASE}); host defaults to the local
 * container {@code 127.0.0.1:3316}. Run via {@code ant test-one} with those
 * variables exported (the forked JVM inherits the parent environment).
 *
 * Assertions are structural — the archive's exact counts are live data, so we
 * check invariants (totals, ranges, the winPct formula, orientation symmetry)
 * rather than magic numbers.
 */
public class PositionStatsHandlerTest extends TestCase {

    private static final int PENTE = GridStateFactory.PENTE; // 1
    private static final int CENTER = 180;
    private static final int SYMMETRY = 2; // an involution: rotateMove(rotateMove(m,2),2)==m

    private PositionStatsHandler handler;
    private GridState geom; // empty state used only for pure rotateMove() geometry

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                PositionStatsHandlerTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PositionStatsHandlerTest.class);
    }

    public PositionStatsHandlerTest(String name) {
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

        DBHandler dbHandler;
        try {
            dbHandler = new MySQLDBHandler(user, pass, db, host);
        } catch (Throwable t) {
            throw new Exception("could not open local DB at " + host, t);
        }
        GameVenueStorer venueStorer = new MySQLGameVenueStorer(dbHandler);
        handler = new PositionStatsHandler(dbHandler, venueStorer);

        geom = GridStateFactory.createGridState(PENTE);
    }

    private static PositionStatsRequest req(int game, int[] moves) {
        PositionStatsRequest r = new PositionStatsRequest();
        r.game = game;
        r.moves = moves;
        r.scope = "archive";
        return r;
    }

    /** Empty position ([180]) yields games and structurally valid rows. */
    public void testEmptyPositionStructure() throws Exception {
        PositionStatsResponse resp =
                handler.computeArchive(req(PENTE, new int[]{CENTER}));

        assertTrue("archive should hold Pente games from the opening position",
                resp.totalGames > 0);
        assertNotNull(resp.nextMoves);
        assertTrue("opening position should have candidate replies",
                !resp.nextMoves.isEmpty());

        long sumGames = 0;
        for (PositionStatsResponse.NextMove nm : resp.nextMoves) {
            assertTrue("move " + nm.move + " in 0..360",
                    nm.move >= 0 && nm.move <= 360);
            assertTrue("games positive", nm.games > 0);
            assertTrue("wins within games", nm.wins >= 0 && nm.wins <= nm.games);
            assertTrue("winPct in 0..100", nm.winPct >= 0.0 && nm.winPct <= 100.0);
            sumGames += nm.games;
        }
        // 361-terminated / undecided games count toward the total only.
        assertTrue("sum(games) <= totalGames", sumGames <= resp.totalGames);
    }

    /** winPct is round(1000*wins/games)/10 for every row and for the total. */
    public void testWinPctFormulaAndTotals() throws Exception {
        PositionStatsResponse resp =
                handler.computeArchive(req(PENTE, new int[]{CENTER}));

        long sumGames = 0, sumWins = 0;
        for (PositionStatsResponse.NextMove nm : resp.nextMoves) {
            double expected = Math.round(1000.0 * nm.wins / nm.games) / 10.0;
            assertEquals("winPct formula for move " + nm.move,
                    expected, nm.winPct, 0.0);
            sumGames += nm.games;
            sumWins += nm.wins;
        }

        assertTrue("totalWinPct in 0..100",
                resp.totalWinPct >= 0.0 && resp.totalWinPct <= 100.0);
        if (sumGames > 0) {
            double expectedTotal = Math.round(1000.0 * sumWins / sumGames) / 10.0;
            assertEquals("totalWinPct is the aggregate win rate",
                    expectedTotal, resp.totalWinPct, 0.0);
        }
        // rotation is one of the 8 symmetries.
        assertTrue("rotation in 0..7",
                resp.rotation >= 0 && resp.rotation <= 7);
    }

    /**
     * The same physical position submitted in two orientations (related by
     * symmetry 2) returns identical stats, with each response's moves expressed
     * in that caller's orientation. Mapping the rotated response back by the
     * (self-inverse) symmetry must reproduce the original response exactly.
     */
    public void testOrientationEquivalence() throws Exception {
        // Seed a position with real data: the most-played reply to the opening.
        PositionStatsResponse opening =
                handler.computeArchive(req(PENTE, new int[]{CENTER}));
        assertTrue(!opening.nextMoves.isEmpty());

        // Pick a common reply that symmetry 2 actually moves (i.e. not on the
        // reflection axis), so the two orientations are genuinely different.
        int reply = -1;
        for (PositionStatsResponse.NextMove nm : opening.nextMoves) {
            if (geom.rotateMove(nm.move, SYMMETRY) != nm.move) {
                reply = nm.move;
                break;
            }
        }
        assertTrue("expected an off-axis reply in the opening", reply >= 0);

        int[] p = new int[]{CENTER, reply};
        int[] pRot = new int[]{geom.rotateMove(CENTER, SYMMETRY),
                geom.rotateMove(reply, SYMMETRY)};
        assertTrue("symmetry must actually move the position",
                pRot[1] != p[1]);

        PositionStatsResponse rP = handler.computeArchive(req(PENTE, p));
        PositionStatsResponse rPrime = handler.computeArchive(req(PENTE, pRot));

        assertTrue("chosen position has data", rP.totalGames > 0);
        assertEquals("totalGames invariant under symmetry",
                rP.totalGames, rPrime.totalGames);
        assertEquals("totalWinPct invariant under symmetry",
                rP.totalWinPct, rPrime.totalWinPct, 0.0);
        assertEquals("same number of candidate moves",
                rP.nextMoves.size(), rPrime.nextMoves.size());

        // Map rotated response back into the original orientation and compare.
        Map<Integer, PositionStatsResponse.NextMove> back =
                new HashMap<Integer, PositionStatsResponse.NextMove>();
        for (PositionStatsResponse.NextMove nm : rPrime.nextMoves) {
            back.put(Integer.valueOf(geom.rotateMove(nm.move, SYMMETRY)), nm);
        }
        for (PositionStatsResponse.NextMove nm : rP.nextMoves) {
            PositionStatsResponse.NextMove other = back.get(Integer.valueOf(nm.move));
            assertNotNull("move " + nm.move + " has a symmetric counterpart", other);
            assertEquals("games match for move " + nm.move, nm.games, other.games);
            assertEquals("wins match for move " + nm.move, nm.wins, other.wins);
            assertEquals("winPct match for move " + nm.move,
                    nm.winPct, other.winPct, 0.0);
        }
    }
}
