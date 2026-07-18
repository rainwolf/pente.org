package org.pente.webdb.test;

import java.sql.*;
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
    private DBHandler dbHandler; // kept for tests that run their own raw SQL

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

    /**
     * Independent cross-check against raw archive rows.
     *
     * <p>{@code testWinPctFormulaAndTotals} used to recompute {@code winPct}
     * with the handler's own formula and re-derive {@code totalWinPct} from
     * the handler's own per-move output — it could never catch an
     * aggregation bug (e.g. miscounted games or wins), only a formula typo.
     *
     * <p>This test instead runs its own {@code select ... group by} against
     * {@code pente_move} for the same position (same hash, same
     * {@code move_num}/{@code game}), and aggregates the raw rows with its
     * own counters/map/bounds/winner check — entirely independent of
     * {@link PositionStatsHandler#aggregateArchive}. The only piece of
     * production logic reused is {@code GridState.rotateMoveToLocalRotation},
     * the mandated rotation primitive itself (not the counting/aggregation
     * this finding is about). The handler's response must match this
     * independent aggregation exactly: totalGames, every move's games/wins,
     * and every winPct (per-move and total).
     */
    public void testWinPctMatchesIndependentArchiveAggregation() throws Exception {
        int[] moves = new int[]{CENTER};
        int numMoves = moves.length;
        int currentPlayer = (numMoves % 2) + 1;

        GridState state =
                GridStateFactory.createGridState(PENTE, moveDataOf(moves));
        long hash = state.getHash();

        // Independent raw aggregation: this test's own totals counter and
        // its own per-move map, built directly from pente_move rows.
        long totalGames = 0;
        Map<Integer, long[]> byMove = new HashMap<Integer, long[]>(); // move -> {games, wins}

        Connection con = dbHandler.getConnection();
        try {
            PreparedStatement stmt = con.prepareStatement(
                    "select next_move, rotation, winner, count(*) "
                            + "from pente_move "
                            + "where hash_key = ? and move_num = ? and game = ? "
                            + "group by next_move, rotation, winner");
            try {
                stmt.setLong(1, hash);
                stmt.setInt(2, numMoves - 1);
                stmt.setInt(3, PENTE);
                ResultSet rs = stmt.executeQuery();
                try {
                    while (rs.next()) {
                        int nextMove = rs.getInt(1);
                        int rotation = rs.getInt(2);
                        int winner = rs.getInt(3);
                        long count = rs.getLong(4);

                        // Every matching row counts toward the total,
                        // including terminal (361) / undecided / corrupt
                        // out-of-board next_move values.
                        totalGames += count;

                        if (nextMove < 0 || nextMove > 360) {
                            continue; // not a real board cell
                        }
                        if (winner == 0) {
                            continue; // undecided game: no move-level stats
                        }

                        int localMove =
                                state.rotateMoveToLocalRotation(nextMove, rotation);

                        long[] agg = byMove.get(Integer.valueOf(localMove));
                        if (agg == null) {
                            agg = new long[2]; // {games, wins}
                            byMove.put(Integer.valueOf(localMove), agg);
                        }
                        agg[0] += count;
                        if (winner == currentPlayer) {
                            agg[1] += count;
                        }
                    }
                } finally {
                    rs.close();
                }
            } finally {
                stmt.close();
            }
        } finally {
            dbHandler.freeConnection(con);
        }

        PositionStatsResponse resp = handler.computeArchive(req(PENTE, moves));

        assertTrue("expected the independent aggregation to find games "
                + "for the opening position", totalGames > 0);
        assertEquals("totalGames must match independent row-count aggregation",
                totalGames, resp.totalGames);
        assertEquals("handler must report exactly the moves the independent "
                + "aggregation found (no missing/extra moves)",
                byMove.size(), resp.nextMoves.size());

        long sumGames = 0, sumWins = 0;
        for (PositionStatsResponse.NextMove nm : resp.nextMoves) {
            long[] agg = byMove.get(Integer.valueOf(nm.move));
            assertNotNull("handler reported move " + nm.move
                    + " which the independent aggregation did not find", agg);

            assertEquals("games for move " + nm.move, agg[0], nm.games);
            assertEquals("wins for move " + nm.move, agg[1], nm.wins);

            double expectedWinPct = (agg[0] <= 0) ? 0.0
                    : Math.round(1000.0 * agg[1] / agg[0]) / 10.0;
            assertEquals("winPct for move " + nm.move,
                    expectedWinPct, nm.winPct, 0.0);

            sumGames += agg[0];
            sumWins += agg[1];
        }

        double expectedTotalWinPct = (sumGames <= 0) ? 0.0
                : Math.round(1000.0 * sumWins / sumGames) / 10.0;
        assertEquals("totalWinPct must match the independently computed "
                + "aggregate win rate",
                expectedTotalWinPct, resp.totalWinPct, 0.0);

        // rotation is one of the 8 symmetries.
        assertTrue("rotation in 0..7",
                resp.rotation >= 0 && resp.rotation <= 7);
    }

    /** Builds a {@link MoveData} over a fixed move list, mirroring the small
     * adapter {@link PositionStatsHandler} uses internally to hand a raw
     * move array to {@link GridStateFactory#createGridState(int, MoveData)}. */
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
