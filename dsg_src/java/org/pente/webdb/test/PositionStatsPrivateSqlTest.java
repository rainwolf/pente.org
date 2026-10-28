package org.pente.webdb.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.game.GameEventData;
import org.pente.game.GameSiteData;
import org.pente.game.GameVenueStorer;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.dto.PositionStatsRequest;

/** buildArchiveSql shape: m.private filter, game join only for game-level filters. No DB. */
public class PositionStatsPrivateSqlTest extends TestCase {

    public PositionStatsPrivateSqlTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(PositionStatsPrivateSqlTest.class);
    }

    /** Venue storer that resolves nothing (getGameSiteData/EventData return null). */
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

    public void testUnfilteredHasNoGameJoin() throws Exception {
        String s = sql(null);
        assertContains(s, "m.private = 'N'");
        assertNotContains(s, "pente_game");
        assertNotContains(s, "g.private");
        assertContains(s, "group by m.next_move, m.rotation, m.winner");
    }

    public void testWinnerOnlyHasNoGameJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.winner = 1;
        String s = sql(f);
        assertNotContains(s, "pente_game");
        assertContains(s, "m.winner = ?");
    }

    public void testPlayerFilterKeepsJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Name = "alice";
        String s = sql(f);
        assertContains(s, "pente_game g");
        assertContains(s, "m.gid = g.gid");
        assertContains(s, "m.private = 'N'");
        assertNotContains(s, "g.private");
    }

    public void testDateFilterKeepsJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.afterDate = "2020-01-01";
        String s = sql(f);
        assertContains(s, "pente_game g");
        assertContains(s, "g.play_date");
    }

    public void testUnresolvedSiteNoJoin() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.site = "Nowhere";
        String s = sql(f);
        assertNotContains(s, "pente_game");
        assertNotContains(s, "g.site_id");
    }

    public void testPlaceholderCountMatchesParams() throws Exception {
        PositionStatsRequest.Filters unres = new PositionStatsRequest.Filters();
        unres.site = "Nowhere";
        PositionStatsRequest.Filters player = new PositionStatsRequest.Filters();
        player.player1Name = "alice";
        player.round = "2";
        PositionStatsRequest.Filters winner = new PositionStatsRequest.Filters();
        winner.winner = 2;
        PositionStatsRequest.Filters[] all = {null, unres, player, winner};
        for (int i = 0; i < all.length; i++) {
            String s = sql(all[i]);
            assertEquals("case " + i, count(s, '?'), params.size());
        }
    }
}
