package org.pente.webdb.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.game.GameVenueStorer;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.dto.PositionStatsRequest;

/** Per-player rating-at-least (>=) predicates in buildArchiveSql. No DB. */
public class PositionStatsRatingSqlTest extends TestCase {

    public PositionStatsRatingSqlTest(String name) { super(name); }

    public static Test suite() { return new TestSuite(PositionStatsRatingSqlTest.class); }

    private static GameVenueStorer nullVenues() {
        return (GameVenueStorer) Proxy.newProxyInstance(
                GameVenueStorer.class.getClassLoader(),
                new Class[]{GameVenueStorer.class},
                new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a) { return null; }
                });
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) { if (s.charAt(i) == c) n++; }
        return n;
    }

    private static void assertContains(String s, String sub) {
        assertTrue("expected [" + sub + "] in: " + s, s.indexOf(sub) >= 0);
    }

    private static void assertNotContains(String s, String sub) {
        assertTrue("unexpected [" + sub + "] in: " + s, s.indexOf(sub) < 0);
    }

    private List<Object> params;

    private String sql(PositionStatsRequest.Filters f) throws Exception {
        params = new ArrayList<Object>();
        return new PositionStatsHandler(null, nullVenues())
                .buildArchiveSql(1, 12345L, 3, f, params);
    }

    private void check(String s) {
        assertEquals(s, count(s, '?'), params.size());
    }

    /** Number of Integer params bound (rating floors are the only Integers besides 3 base/game). */
    private int ratingParams() {
        int n = 0;
        for (Object o : params) {
            if (o instanceof Integer && ((Integer) o).intValue() >= 1000) n++;
        }
        return n;
    }

    private int last(int i) {
        return ((Integer) params.get(params.size() - 1 - i)).intValue();
    }

    public void testSlot1SeatOneNoNameRating() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1RatingAbove = 1600;
        String s = sql(f);
        assertContains(s, "pente_game g");
        assertContains(s, " and g.player1_rating >= ?");
        assertNotContains(s, "player2_rating");
        assertEquals(1600, last(0));
        check(s);
    }

    public void testSlot2SeatTwoNoNameRating() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player2RatingAbove = 2900;
        String s = sql(f);
        assertContains(s, "pente_game g");
        assertContains(s, " and g.player2_rating >= ?");
        assertNotContains(s, "player1_rating");
        check(s);
    }

    public void testNoNameSeatAll() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Seat = PositionStatsRequest.Filters.SEAT_ALL;
        f.player1RatingAbove = 1700;
        String s = sql(f);
        assertContains(s, "pente_game g");
        assertContains(s, " and (g.player1_rating >= ? or g.player2_rating >= ?)");
        assertEquals(2, ratingParams());
        check(s);
    }

    public void testNamedSeatOne() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Name = "Alice";
        f.player1Seat = 1;
        f.player1RatingAbove = 1800;
        String s = sql(f);
        assertContains(s, " and g.player1_pid = p1.pid and g.player1_rating >= ? and p1.name_lower = ?");
        check(s);
    }

    public void testNamedSeatTwo() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player2Name = "Bob";
        f.player2Seat = 2;
        f.player2RatingAbove = 1900;
        String s = sql(f);
        assertContains(s, " and g.player2_pid = p2.pid and g.player2_rating >= ? and p2.name_lower = ?");
        check(s);
    }

    public void testNamedSeatAllSameSeat() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Name = "Alice";
        f.player1Seat = PositionStatsRequest.Filters.SEAT_ALL;
        f.player1RatingAbove = 2000;
        String s = sql(f);
        assertContains(s, " and ((g.player1_pid = p1.pid and g.player1_rating >= ?)"
                + " or (g.player2_pid = p1.pid and g.player2_rating >= ?))"
                + " and p1.name_lower = ?");
        assertEquals(2, ratingParams());
        check(s);
    }

    public void testZeroAndNegativeAreOff() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1RatingAbove = 0;
        f.player2RatingAbove = -5;
        String s = sql(f);
        assertNotContains(s, "pente_game");
        assertNotContains(s, "_rating");
        check(s);
    }

    public void testBothSlotsAndNamedPlusUnnamed() throws Exception {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.player1Name = "Alice";
        f.player1RatingAbove = 1600;
        f.player2Seat = PositionStatsRequest.Filters.SEAT_ALL;
        f.player2RatingAbove = 2100;
        f.winner = 1;
        String s = sql(f);
        assertContains(s, "g.player1_rating >= ? and p1.name_lower = ?");
        assertContains(s, "(g.player1_rating >= ? or g.player2_rating >= ?)");
        check(s);
    }
}
