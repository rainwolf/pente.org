package org.pente.webdb.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.webdb.GameStateFilters;
import org.pente.webdb.dto.PositionStatsRequest;

public class GameStateFiltersTest extends TestCase {

    public GameStateFiltersTest(String name) { super(name); }

    public static Test suite() {
        return new TestSuite(GameStateFiltersTest.class);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    private static String where(PositionStatsRequest.Filters f, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        GameStateFilters.append(sb, params, f);
        return sb.toString();
    }

    public void testNullAndDefaultFiltersAppendNothing() {
        assertTrue(!GameStateFilters.needsGame(null));
        List<Object> p = new ArrayList<Object>();
        assertEquals("", where(null, p));
        assertEquals(0, p.size());

        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        assertTrue(!GameStateFilters.needsGame(f));
        p = new ArrayList<Object>();
        assertEquals("", where(f, p));
        assertEquals(0, p.size());
    }

    public void testBlankAndAllAreOff() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        for (String v : new String[]{"", "all", null}) {
            f.liveOrTurnBased = v;
            assertNull(GameStateFilters.liveMode(f));
        }
        assertNull(GameStateFilters.liveMode(null));
    }

    public void testUnknownLiveModeRejected() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        for (String v : new String[]{"Live", "tb", "turnbased"}) {
            f.liveOrTurnBased = v;
            try {
                GameStateFilters.liveMode(f);
                fail("expected IllegalArgumentException for " + v);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    public void testExcludeTimeouts() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.excludeTimeouts = true;
        List<Object> p = new ArrayList<Object>();
        assertEquals(" and (g.status is null or g.status <> ?)", where(f, p));
        assertEquals(1, p.size());
        assertEquals("T", p.get(0));
        assertTrue(GameStateFilters.needsGame(f));
    }

    public void testLive() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "live";
        List<Object> p = new ArrayList<Object>();
        assertEquals(" and (g.site_id = ? and g.gid < ?)", where(f, p));
        assertEquals(2, p.size());
        assertEquals(Integer.valueOf(2), p.get(0));
        assertEquals(Long.valueOf(50000000000000L), p.get(1));
        assertTrue(GameStateFilters.needsGame(f));
    }

    public void testTurnBased() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.liveOrTurnBased = "turnBased";
        List<Object> p = new ArrayList<Object>();
        assertEquals(" and ((g.site_id = ? and g.gid >= ?) or g.site_id <> ?)", where(f, p));
        assertEquals(3, p.size());
        assertEquals(Integer.valueOf(2), p.get(0));
        assertEquals(Long.valueOf(50000000000000L), p.get(1));
        assertEquals(Integer.valueOf(2), p.get(2));
    }

    public void testTimeoutThenLiveOrder() {
        PositionStatsRequest.Filters f = new PositionStatsRequest.Filters();
        f.excludeTimeouts = true;
        f.liveOrTurnBased = "live";
        List<Object> p = new ArrayList<Object>();
        assertEquals(" and (g.status is null or g.status <> ?)"
            + " and (g.site_id = ? and g.gid < ?)", where(f, p));
        assertEquals(3, p.size());
        assertEquals("T", p.get(0));
    }
}
