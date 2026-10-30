package org.pente.webdb.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.*;

import org.pente.game.GameVenueStorer;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.dto.PositionStatsRequest;

/** buildArchiveSql with excludeTimeouts / liveOrTurnBased. No DB. */
public class PositionStatsGameStateSqlTest extends TestCase {

    private static final String DEFAULT_SQL =
        "select m.next_move, m.rotation, m.winner, count(*) from pente_move m"
        + " where m.hash_key = ? and m.move_num = ? and m.game = ?"
        + " and m.private = 'N' group by m.next_move, m.rotation, m.winner";

    public PositionStatsGameStateSqlTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(PositionStatsGameStateSqlTest.class);
    }

    private static GameVenueStorer nullVenues() {
        return (GameVenueStorer) Proxy.newProxyInstance(
            GameVenueStorer.class.getClassLoader(),
            new Class[]{GameVenueStorer.class},
            new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) { return null; }
            });
    }

    private List<Object> params;

    private String sql(PositionStatsRequest.Filters f) throws Exception {
        params = new ArrayList<Object>();
        return new PositionStatsHandler(null, nullVenues())
            .buildArchiveSql(1, 12345L, 3, f, params);
    }

    private static void assertContains(String s, String sub) {
        assertTrue("expected [" + sub + "] in: " + s, s.indexOf(sub) >= 0);
    }

    private void assertTail(Object... expected) {
        List<Object> exp = Arrays.asList(expected);
        List<Object> tail = params.subList(params.size() - exp.size(), params.size());
        assertEquals(exp, tail);
    }

    public void testDefaultSqlUnchangedNullFilters() throws Exception {
        assertEquals(DEFAULT_SQL, sql(null));
        assertEquals(Arrays.<Object>asList(12345L, 2, 1), params);
    }

    public void testDefaultSqlUnchangedDefaultFilters() throws Exception {
        assertEquals(DEFAULT_SQL, sql(new PositionStatsRequest.Filters()));
        assertEquals(Arrays.<Object>asList(12345L, 2, 1), params);
    }

    public void testExcludeTimeoutsAddsGameJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.excludeTimeouts = true;
        String s = sql(f);
        assertContains(s, ", pente_game g");
        assertContains(s, " and m.gid = g.gid and g.game = ?");
        assertContains(s, "g.status <> ?");
        assertTail("T");
    }

    public void testLiveBinds() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "live";
        String s = sql(f);
        assertContains(s, "(g.site_id = ? and g.gid < ?)");
        assertTail(2, 50000000000000L);
    }

    public void testTurnBasedBinds() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "turnBased";
        sql(f);
        assertTail(2, 50000000000000L, 2);
    }

    public void testWinnerStaysLast() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.excludeTimeouts = true;
        f.winner = 1;
        String s = sql(f);
        int status = s.indexOf("g.status <> ?");
        int winner = s.indexOf(" and m.winner = ?");
        assertTrue(status >= 0 && winner > status);
        assertTail(1);
    }

    public void testAllIsNoJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "all";
        assertEquals(DEFAULT_SQL, sql(f));
    }
}
