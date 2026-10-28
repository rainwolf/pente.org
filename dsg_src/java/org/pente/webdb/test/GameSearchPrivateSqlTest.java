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

/** Archive search SQL shape: m.private filter, game join only for game-level filters. No DB. */
public class GameSearchPrivateSqlTest extends TestCase {

    public GameSearchPrivateSqlTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(GameSearchPrivateSqlTest.class);
    }

    private static GameVenueStorer nullVenues() {
        return (GameVenueStorer) Proxy.newProxyInstance(
            GameVenueStorer.class.getClassLoader(),
            new Class[]{GameVenueStorer.class},
            new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) { return null; }
            });
    }

    private final GameSearchHandler h = new GameSearchHandler(null, null, nullVenues());

    private static GameSearchRequest req(int[] moves, PositionStatsRequest.Filters f) {
        GameSearchRequest r = new GameSearchRequest();
        r.game = 1;
        r.moves = moves;
        r.filters = f;
        return r;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    private static void assertContains(String s, String sub) {
        assertTrue("expected [" + sub + "] in: " + s, s.indexOf(sub) >= 0);
    }

    private static void assertNotContains(String s, String sub) {
        assertTrue("unexpected [" + sub + "] in: " + s, s.indexOf(sub) < 0);
    }

    public void testPositionUnfilteredNoJoin() throws Exception {
        String s = h.archiveSqlForTest(req(new int[]{180}, null));
        assertContains(s, "m.private = 'N'");
        assertNotContains(s, "pente_game");
        assertNotContains(s, "g.private");
        String page = GameSearchHandler.pagedGidSql("m.gid", "", s.substring(s.indexOf("from")), "m.play_date");
        assertContains(page, "order by m.play_date desc, m.gid desc");
    }

    public void testPositionWinnerOnlyNoJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.winner = 1;
        String s = h.archiveSqlForTest(req(new int[]{180}, f));
        assertNotContains(s, "pente_game");
        assertContains(s, "m.winner = ?");
    }

    public void testPositionPlayerFilterKeepsJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Name = "alice";
        String s = h.archiveSqlForTest(req(new int[]{180}, f));
        assertContains(s, "pente_game g");
        assertContains(s, "m.gid = g.gid");
        assertContains(s, "m.private = 'N'");
        assertNotContains(s, "g.private");
    }

    public void testFilterOnlyUnchanged() throws Exception {
        String s = h.archiveSqlForTest(req(new int[0], null));
        assertContains(s, "g.private = 'N'");
        assertNotContains(s, "m.private");
    }

    public void testPlaceholderCountMatchesParams() throws Exception {
        PositionStatsRequest.Filters unres = new PositionStatsRequest.Filters();
        unres.site = "Nowhere";
        PositionStatsRequest.Filters player = new PositionStatsRequest.Filters();
        player.player1Name = "alice";
        player.round = "2";
        PositionStatsRequest.Filters winner = new PositionStatsRequest.Filters();
        winner.winner = 2;
        PositionStatsRequest.Filters[] fs = {null, unres, player, winner};
        int[][] ms = {new int[]{180}, new int[0]};
        for (int j = 0; j < ms.length; j++) {
            for (int i = 0; i < fs.length; i++) {
                GameSearchRequest r = req(ms[j], fs[i]);
                String s = h.archiveSqlForTest(r);
                List<Object> params = h.archiveParamsForTest(r);
                String id = "moves " + j + " filter " + i;
                // count query: select count(*) <from><where>
                assertEquals(id, count(s, '?'), params.size());
                // page query adds limit ? offset ?
                String page = GameSearchHandler.pagedGidSql("x", "", s.substring(s.indexOf("from")), "y");
                assertEquals(id + " page", count(page, '?'), params.size() + 2);
            }
        }
    }
}
