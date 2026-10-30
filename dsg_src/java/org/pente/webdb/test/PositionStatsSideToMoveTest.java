package org.pente.webdb;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import junit.framework.*;

import org.pente.database.DBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.PositionStatsResponse;

/**
 * Side to move in position-stats (B8): Connect6 moves are 1, 2, 2, 2, ... so
 * P1 is to move when {@code numMoves % 4} is 0 or 3; every other game
 * alternates. Lives in {@code org.pente.webdb} (like StatsCacheRatingKeyTest)
 * to reach the package-visible {@code sideToMove}. No DB. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.PositionStatsSideToMoveTest}.
 */
public class PositionStatsSideToMoveTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                PositionStatsSideToMoveTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PositionStatsSideToMoveTest.class);
    }

    public PositionStatsSideToMoveTest(String name) {
        super(name);
    }

    public void testNonConnect6Unchanged() {
        int[] games = {1, 5, 29};
        for (int g : games) {
            for (int n = 1; n <= 8; n++) {
                assertEquals("game " + g + " n=" + n, (n % 2) + 1,
                        PositionStatsHandler.sideToMove(g, n));
            }
        }
    }

    public void testConnect6Sequence() {
        int[] expected = {2, 2, 1, 1, 2, 2, 1, 1};
        for (int n = 1; n <= 8; n++) {
            assertEquals("n=" + n, expected[n - 1],
                    PositionStatsHandler.sideToMove(13, n));
        }
    }

    public void testConnect6AllIds() {
        for (int g : new int[]{14, 63, 64}) {
            for (int n = 0; n <= 12; n++) {
                assertEquals("game " + g + " n=" + n,
                        PositionStatsHandler.sideToMove(13, n),
                        PositionStatsHandler.sideToMove(g, n));
            }
        }
        for (int g : new int[]{12, 65}) {
            for (int n = 0; n <= 12; n++) {
                assertEquals("game " + g + " n=" + n, (n % 2) + 1,
                        PositionStatsHandler.sideToMove(g, n));
            }
        }
    }

    /** n = 2: P2 still to move (second stone of its pair). */
    public void testConnect6WinCreditAtTwo() throws Exception {
        assertEquals(1L, winsFor(new int[]{180, 181}, 2));
        assertEquals(0L, winsFor(new int[]{180, 181}, 1));
    }

    /** n = 3: P1 to move. Plain alternation would have credited P2. */
    public void testConnect6WinCreditAtThree() throws Exception {
        assertEquals(1L, winsFor(new int[]{180, 181, 182}, 1));
        assertEquals(0L, winsFor(new int[]{180, 181, 182}, 2));
    }

    /** Pente is unchanged: n = 2 means P1 to move. */
    public void testPenteWinCreditUnchanged() throws Exception {
        assertEquals(1L, winsFor(1, new int[]{180, 181}, 1));
        assertEquals(0L, winsFor(1, new int[]{180, 181}, 2));
    }

    // ------------------------------------------------------------ helpers

    private static long winsFor(int[] moves, int winner) throws Exception {
        return winsFor(13, moves, winner);
    }

    /**
     * Runs computeArchive against a fake archive DB returning exactly one
     * decided row {next_move=100, rotation=0, winner, count=1}; returns the
     * wins credited to the single candidate.
     */
    private static long winsFor(int game, int[] moves, final int winner)
            throws Exception {
        DBHandler db = new DBHandler() {
            public Connection getConnection() {
                return (Connection) proxy(Connection.class, (p, m, a) -> {
                    if ("prepareStatement".equals(m.getName())) {
                        return proxy(PreparedStatement.class, (p2, m2, a2) -> {
                            if ("executeQuery".equals(m2.getName())) {
                                return oneRow(winner);
                            }
                            return dflt(m2.getReturnType());
                        });
                    }
                    return dflt(m.getReturnType());
                });
            }
            public void freeConnection(Connection con) { }
            public void destroy() { }
        };
        GameVenueStorer venues = (GameVenueStorer) proxy(GameVenueStorer.class,
                (p, m, a) -> dflt(m.getReturnType()));
        PositionStatsRequest req = new PositionStatsRequest();
        req.game = game;
        req.moves = moves;
        PositionStatsResponse resp =
                new PositionStatsHandler(db, venues).computeArchive(req);
        assertEquals("one candidate", 1, resp.nextMoves.size());
        assertEquals("games", 1L, resp.nextMoves.get(0).games);
        return resp.nextMoves.get(0).wins;
    }

    private static ResultSet oneRow(final int winner) {
        final int[] calls = {0};
        return (ResultSet) proxy(ResultSet.class, (p, m, a) -> {
            switch (m.getName()) {
                case "next":
                    return calls[0]++ == 0;
                case "getInt": {
                    int col = (Integer) a[0];
                    if (col == 1) return 100;  // next_move
                    if (col == 2) return 0;    // rotation
                    if (col == 3) return winner;
                    return 0;
                }
                case "getLong":
                    return 1L;                 // count
                default:
                    return dflt(m.getReturnType());
            }
        });
    }

    private interface H {
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable;
    }

    private static Object proxy(final Class<?> iface, final H h) {
        return Proxy.newProxyInstance(
                PositionStatsSideToMoveTest.class.getClassLoader(),
                new Class<?>[]{iface}, new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a)
                            throws Throwable {
                        if (m.getDeclaringClass() == Object.class) {
                            switch (m.getName()) {
                                case "hashCode": return System.identityHashCode(p);
                                case "equals": return p == a[0];
                                default: return iface.getName() + "-fake";
                            }
                        }
                        return h.invoke(p, m, a);
                    }
                });
    }

    private static Object dflt(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        return (char) 0;
    }
}
