package org.pente.gameServer.server.test;

import java.lang.reflect.Field;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGIgnoreData;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGExitMainRoomEvent;
import org.pente.gameServer.event.DSGJoinMainRoomEvent;
import org.pente.gameServer.event.DSGTextMainRoomEvent;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidLiveSessions;
import org.pente.gameServer.kids.KidSettings;
import org.pente.gameServer.kids.test.InMemoryChatCopyStore;
import org.pente.gameServer.kids.test.InMemoryKidStorer;
import org.pente.gameServer.kids.test.MapPlayerStorer;
import org.pente.gameServer.kids.test.RecordingTextMailer;
import org.pente.gameServer.server.Resources;
import org.pente.gameServer.server.ServerMainRoom;
import org.pente.gameServer.server.TournamentServer;
import org.pente.gameServer.server.TournamentServerMainRoom;
import org.pente.gameServer.tourney.Tourney;

import static org.pente.gameServer.server.test.ServerTableKidTest.*;

/** Kid chat rules on lobby text and kid session registration, driven through real main room code. */
public class ServerMainRoomKidTest extends TestCase {

    private KidLiveSessions sessions;
    private InMemoryChatCopyStore store;
    private ServerTableKidTest.Router router;
    private StubServer server;
    private Resources resources;
    private SimpleDSGPlayerData kid;
    private SimpleDSGPlayerData bob;
    private SimpleDSGPlayerData amy;

    public ServerMainRoomKidTest(String name) {
        super(name);
    }

    /**
     * Server and CacheDSGPlayerStorer constructors open an SSL socket and
     * need Redis, so the stubs are allocated without running a constructor.
     * Only the overridden methods below are ever called on them.
     */
    static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }

    /** Never constructed; see allocate. */
    static class StubServer extends TournamentServer {
        KidLiveSessions ks;

        StubServer() throws Throwable {
            super(null, null);
        }

        public KidLiveSessions getKidSessions() {
            return ks;
        }

        public void routeEventToAllTables(DSGEvent event) {
        }

        public synchronized void matchOnJoin(DSGPlayerData playerData) {
        }

        public Tourney getTourney() {
            return null;
        }
    }

    /** Never constructed; see allocate. Nobody ignores anybody. */
    static class NoIgnoreStorer extends CacheDSGPlayerStorer {
        NoIgnoreStorer() throws Exception {
            super(null, null, null);
        }

        public DSGIgnoreData getIgnoreData(long pid, long ignorePid) {
            return null;
        }
    }

    protected void setUp() throws Exception {
        MapPlayerStorer players = new MapPlayerStorer();
        SimpleDSGPlayerData parent = player(PARENT, "parent", null, DSGPlayerData.HUMAN);
        players.put(parent);
        kid = player(KID, "kid", PARENT, DSGPlayerData.HUMAN);
        bob = player(BOB, "bob", null, DSGPlayerData.HUMAN);
        amy = player(AMY, "amy", null, DSGPlayerData.HUMAN);
        players.put(kid);
        players.put(bob);
        players.put(amy);
        InMemoryKidStorer kids = new InMemoryKidStorer();
        kids.addToWhitelist(KID, AMY);
        kids.storeSettings(new KidSettings(KID, false, true));
        store = new InMemoryChatCopyStore();
        KidCopyMailer mailer = new KidCopyMailer(true, new RecordingTextMailer(), players, kids);
        sessions = new KidLiveSessions(SERVER, players, kids, store, mailer);
        router = new ServerTableKidTest.Router();
        server = allocate(StubServer.class);
        server.ks = sessions;
        resources = new Resources();
        resources.setDsgPlayerStorer(allocate(NoIgnoreStorer.class));
    }

    private void join(ServerMainRoom room, SimpleDSGPlayerData d) {
        DSGJoinMainRoomEvent join = new DSGJoinMainRoomEvent(d.getName(), d);
        join.setKidState(sessions.load(d));
        room.handleJoin(join);
    }

    public void testLobbyTextFilteredPerRecipient() throws Throwable {
        ServerMainRoom room = new ServerMainRoom(server, resources, router);
        join(room, kid);
        join(room, bob);
        join(room, amy);

        room.handleText("bob", "anyone?");
        room.handleText("amy", "hi kid");

        List<DSGTextMainRoomEvent> toKid = router.to("kid", DSGTextMainRoomEvent.class);
        assertEquals(1, toKid.size());
        assertEquals("hi kid", toKid.get(0).getText());
        assertEquals(2, router.to("bob", DSGTextMainRoomEvent.class).size());
        assertEquals(2, router.to("amy", DSGTextMainRoomEvent.class).size());
        List<String> lines = store.lines("kids:livechat:" + SERVER + ":" + KID);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("[lobby] amy: hi kid"));
    }

    public void testJoinAndExitRegisterKidState() throws Throwable {
        assertJoinAndExitRegisterKidState(new ServerMainRoom(server, resources, router));
    }

    public void testTournamentRoomJoinAndExitRegisterKidState() throws Throwable {
        assertJoinAndExitRegisterKidState(new TournamentServerMainRoom(server, resources, router));
    }

    private void assertJoinAndExitRegisterKidState(ServerMainRoom room) {
        join(room, kid);
        assertNotNull(sessions.get("kid"));
        assertTrue(sessions.get("kid").isKid());

        room.handleExit(new DSGExitMainRoomEvent("kid", false));
        assertNull(sessions.get("kid"));
    }
}
