package org.pente.webdb.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import junit.framework.*;

import org.pente.game.GameVenueStorer;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.PositionStatsRequest;

/** excludeTimeouts / liveOrTurnBased predicates in GameSearchHandler. No DB. */
public class GameSearchGameStateSqlTest extends TestCase {

    public GameSearchGameStateSqlTest(String name) { super(name); }

    public static Test suite() { return new TestSuite(GameSearchGameStateSqlTest.class); }

    private static GameVenueStorer nullVenues() {
        return (GameVenueStorer) Proxy.newProxyInstance(
                GameVenueStorer.class.getClassLoader(),
                new Class[]{GameVenueStorer.class},
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) { return null; }
                });
    }

    private static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    private static void assertContains(String s, String sub) {
        assertTrue("expected [" + sub + "] in: " + s, s.indexOf(sub) >= 0);
    }

    private final GameSearchHandler h = new GameSearchHandler(null, null, nullVenues());
    private List<Object> params;

    private String sql(int[] moves, PositionStatsRequest.Filters f) throws Exception {
        GameSearchRequest r = new GameSearchRequest();
        r.game = 1;
        r.moves = moves;
        r.filters = f;
        params = h.archiveParamsForTest(r);
        return h.archiveSqlForTest(r);
    }

    private static final String POS =
            "select m.gid from pente_move m where m.hash_key = ? and m.move_num = ? "
            + "and m.game = ? and m.private = 'N'";

    public void testDefaultPositionUnchanged() throws Exception {
        assertEquals(POS, sql(new int[]{180}, null));
        assertEquals(POS, sql(new int[]{180}, new PositionStatsRequest.Filters()));
    }

    public void testDefaultFilterOnlyUnchanged() throws Exception {
        assertEquals("select g.gid from pente_game g where g.game = ? and g.private = 'N'",
                sql(null, null));
        assertEquals(1, params.size());
        assertEquals(Integer.valueOf(1), params.get(0));
    }

    public void testPositionExcludeTimeoutsJoinsGame() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.excludeTimeouts = true;
        String s = sql(new int[]{180}, f);
        assertContains(s, "from pente_move m, pente_game g");
        assertContains(s, "m.gid = g.gid");
        assertContains(s, "g.status <> ?");
        assertEquals("T", params.get(params.size() - 1));
    }

    public void testFilterOnlyLiveNoExtraJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "live";
        String s = sql(null, f);
        assertContains(s, "(g.site_id = ? and g.gid < ?)");
        assertEquals(1, count(s, "pente_game"));
        assertEquals(3, params.size());
        assertEquals(Integer.valueOf(1), params.get(0));
        assertEquals(Integer.valueOf(2), params.get(1));
        assertEquals(Long.valueOf(50000000000000L), params.get(2));
    }

    public void testTurnBasedThenWinnerOrder() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "turnBased";
        f.winner = 2;
        String s = sql(null, f);
        int tb = s.indexOf("g.gid >= ?");
        int w = s.indexOf("g.winner = ?");
        assertTrue(s, tb >= 0 && w > tb);
        assertEquals(Integer.valueOf(2), params.get(params.size() - 1));
    }

    public void testUnknownLiveModeThrows() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "Live";
        try {
            sql(null, f);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
