package org.pente.webdb;

import junit.framework.*;

import org.pente.webdb.dto.PositionStatsRequest;

/** StatsCache.Key must discriminate on the rating floors. */
public class StatsCacheRatingKeyTest extends TestCase {

    public StatsCacheRatingKeyTest(String name) { super(name); }

    public static Test suite() { return new TestSuite(StatsCacheRatingKeyTest.class); }

    public void testRatingFloorsAreInKey() {
        PositionStatsRequest.Filters plain = new PositionStatsRequest.Filters();
        PositionStatsRequest.Filters r1 = new PositionStatsRequest.Filters();
        r1.player1RatingAbove = 1600;
        PositionStatsRequest.Filters r2 = new PositionStatsRequest.Filters();
        r2.player2RatingAbove = 1600;
        PositionStatsRequest.Filters r1b = new PositionStatsRequest.Filters();
        r1b.player1RatingAbove = 1600;

        Object kPlain = StatsCache.keyOf(1, 7L, 3, 0, plain);
        Object k1 = StatsCache.keyOf(1, 7L, 3, 0, r1);
        Object k2 = StatsCache.keyOf(1, 7L, 3, 0, r2);
        Object k1b = StatsCache.keyOf(1, 7L, 3, 0, r1b);

        assertTrue(!kPlain.equals(k1));
        assertTrue(!kPlain.equals(k2));
        assertTrue(!k1.equals(k2));
        assertEquals(k1, k1b);
        assertEquals(k1.hashCode(), k1b.hashCode());

        r1b.player1RatingAbove = 1700;
        assertTrue(!k1.equals(StatsCache.keyOf(1, 7L, 3, 0, r1b)));
    }
}
