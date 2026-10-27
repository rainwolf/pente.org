package org.pente.game.test;

import java.util.*;
import java.util.stream.Collectors;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.gameServer.core.ExperimentalServer;
import org.pente.gameServer.server.DSGContextListener;

public class TombstoneBootRegistrationTest extends TestCase {

    public TombstoneBootRegistrationTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean b) { assertTrue(!b); }
    private static void assertFalse(String m, boolean b) { assertTrue(m, !b); }

    private static List<String> namesFor(List<Object[]> rows, int g) {
        List<String> names = new ArrayList<String>();
        for (Object[] r : rows) {
            if ((Integer) r[0] == g) {
                names.add((String) r[1]);
            }
        }
        return names;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) return true;
        }
        return false;
    }

    public void testTombstoneHasOnlyLiveAndTbRows() {
        List<Object[]> rows = MySQLGameVenueStorer.allGameEventRows();
        assertEquals(2 * GridStateFactory.LIVE_GAMES.length + 2 * GridStateFactory.TB_GAMES.length + 3,
            rows.size());
        assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT), namesFor(rows, 33));
        assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT), namesFor(rows, 34));
        assertEquals(Arrays.asList(MySQLGameVenueStorer.TB_EVENT), namesFor(rows, 83));
    }

    public void testExistingGamesKeepKothRows() {
        List<Object[]> rows = MySQLGameVenueStorer.allGameEventRows();
        for (int g : GridStateFactory.LIVE_GAMES) {
            assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT, MySQLGameVenueStorer.KOTH_EVENT),
                namesFor(rows, g));
        }
        for (int g : GridStateFactory.TB_GAMES) {
            assertEquals(Arrays.asList(MySQLGameVenueStorer.TB_EVENT, MySQLGameVenueStorer.KOTH_EVENT),
                namesFor(rows, g));
        }
    }

    public void testOnlyExperimentalOffersTombstone() {
        List<Object[]> offerings = DSGContextListener.serverOfferings();
        assertEquals(5, offerings.size());
        for (Object[] row : offerings) {
            int server = (Integer) row[0];
            int[] games = (int[]) row[1];
            if (server == ExperimentalServer.ID) {
                assertTrue(Arrays.equals(new int[]{33, 34}, games));
                assertEquals(MySQLGameVenueStorer.LIVE_EVENT, row[2]);
            } else {
                assertFalse("server " + server, contains(games, 33));
                assertFalse("server " + server, contains(games, 34));
            }
        }
    }

    public void testExistingServersKeepTodaysOfferings() {
        List<Object[]> offerings = DSGContextListener.serverOfferings();
        assertEquals(Arrays.asList(1, 37, 46, 45, ExperimentalServer.ID),
            offerings.stream().map(r -> (Integer) r[0]).collect(Collectors.toList()));
        assertSame(GridStateFactory.LIVE_GAMES, offerings.get(0)[1]);
        assertSame(GridStateFactory.LIVE_GAMES, offerings.get(1)[1]);
        assertEquals(6, ((int[]) offerings.get(2)[1]).length);
        assertSame(GridStateFactory.LIVE_GAMES, offerings.get(3)[1]);
        assertEquals(MySQLGameVenueStorer.KOTH_EVENT, offerings.get(3)[2]);
    }
}
