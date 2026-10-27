package org.pente.gameServer.core.test;

import java.util.Arrays;

import junit.framework.TestCase;

import org.pente.game.GameEventData;
import org.pente.game.GridStateFactory;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.game.SimpleGameEventData;
import org.pente.gameServer.core.ExperimentalServer;
import org.pente.gameServer.core.ServerData;

/**
 * The Experimental server's identity and the server-offering rule.
 *
 * Run: ant test-one -Dtest=org.pente.gameServer.core.test.ExperimentalServerTest
 */
public class ExperimentalServerTest extends TestCase {

    public ExperimentalServerTest(String name) {
        super(name);
    }

    private static void assertFalse(boolean b) {
        assertTrue(!b);
    }

    private static ServerData server(int id, String name, boolean tournament, int... games) {
        ServerData d = new ServerData();
        d.setServerId(id);
        d.setName(name);
        d.setTournament(tournament);
        for (int g : games) {
            SimpleGameEventData e = new SimpleGameEventData();
            e.setGame(g);
            e.setName(MySQLGameVenueStorer.LIVE_EVENT);
            d.addGameEvent(e);
        }
        return d;
    }

    public void testIdentity() {
        assertEquals(47, ExperimentalServer.ID);
        assertEquals("Experimental", ExperimentalServer.NAME);
        assertEquals(16004, ExperimentalServer.PORT);
        assertTrue(Arrays.equals(new int[]{33, 34}, ExperimentalServer.GAMES));
    }

    public void testTombstoneOnlyOnExperimental() {
        ServerData main = server(1, "Main Room", false, 1, 2, 31, 32);
        assertFalse(ExperimentalServer.gameAllowed(main, GridStateFactory.TOMBSTONE));
        assertFalse(ExperimentalServer.gameAllowed(main, GridStateFactory.SPEED_TOMBSTONE));
        assertTrue(ExperimentalServer.gameAllowed(main, GridStateFactory.PENTE));
    }

    public void testNothingElseOnExperimental() {
        ServerData exp = server(47, "Experimental", false, 33, 34);
        assertTrue(ExperimentalServer.gameAllowed(exp, 33));
        assertTrue(ExperimentalServer.gameAllowed(exp, 34));
        assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.PENTE));
        assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.SPEED_PENTE));
        assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.GO));
    }

    public void testTournamentAndUnconfiguredServersNeverAllowTombstone() {
        assertFalse(ExperimentalServer.gameAllowed(server(99, "Tourney", true, 1), 33));
        assertTrue(ExperimentalServer.gameAllowed(server(99, "Tourney", true, 1), GridStateFactory.PENTE));
        assertFalse(ExperimentalServer.gameAllowed(server(98, "Bare", false), 33));
        assertTrue(ExperimentalServer.gameAllowed(server(98, "Bare", false), GridStateFactory.PENTE));
        assertFalse(ExperimentalServer.gameAllowed(null, 34));
    }

    // The Arena (id 0) is in-memory: DSGContextListener.setupArenaServers copies
    // server 1's GameEventData into a new ServerData and never reads dsg_server_game
    // for id 0. The rule must work off that in-memory copy, never a DB lookup by id.
    public void testArenaUsesCopiedServerOneEvents() {
        ServerData main = server(1, "Main Room", false, 1, 2, 31, 32);
        ServerData arena = server(0, "Arena Server", false);
        for (GameEventData e : main.getGameEvents()) {
            arena.addGameEvent(e);
        }
        assertTrue(ExperimentalServer.gameAllowed(arena, GridStateFactory.PENTE));
        assertTrue(ExperimentalServer.gameAllowed(arena, 32));
        assertFalse(ExperimentalServer.gameAllowed(arena, 33));
        assertFalse(ExperimentalServer.gameAllowed(arena, 34));
        assertFalse(ExperimentalServer.isExperimental(arena));
    }

    public void testArenaWithoutCopiedEventsStillRefusesTombstone() {
        // server 1 failed to load: the Arena's copy is empty
        assertFalse(ExperimentalServer.gameAllowed(server(0, "Arena Server", false), 33));
    }

    public void testDefaultGameIsLowestNormalOfferedId() {
        assertEquals(33, ExperimentalServer.defaultGame(server(47, "Experimental", false, 34, 33)).getId());
        assertEquals(GridStateFactory.GO, ExperimentalServer.defaultGame(
            server(46, "Go", false, 20, 19, 22, 21, 24, 23)).getId());
        assertEquals(GridStateFactory.PENTE, ExperimentalServer.defaultGame(server(1, "Main Room", false, 2, 1)).getId());
        assertEquals(GridStateFactory.PENTE, ExperimentalServer.defaultGame(server(98, "Bare", false)).getId());
    }

    public void testIsExperimentalNeedsIdAndName() {
        assertTrue(ExperimentalServer.isExperimental(server(47, "Experimental", false)));
        assertFalse(ExperimentalServer.isExperimental(server(47, "Someone else's room", false)));
        assertFalse(ExperimentalServer.isExperimental(server(1, "Experimental", false)));
        assertFalse(ExperimentalServer.isExperimental(null));
    }

    public void testNativeSocketsRefusedOnlyOnExperimental() {
        assertFalse(ExperimentalServer.acceptsNativeSocket(server(47, "Experimental", false)));
        assertTrue(ExperimentalServer.acceptsNativeSocket(server(1, "Main Room", false)));
        assertTrue(ExperimentalServer.acceptsNativeSocket(server(0, "Arena Server", false)));
    }

    public void testRoomName() {
        assertTrue(ExperimentalServer.isExperimentalRoom("Experimental"));
        assertFalse(ExperimentalServer.isExperimentalRoom("Main Room"));
        assertFalse(ExperimentalServer.isExperimentalRoom(null));
    }
}
