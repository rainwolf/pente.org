package org.pente.gameServer.server.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGEventListener;
import org.pente.gameServer.event.DSGInviteResponseTableEvent;
import org.pente.gameServer.event.DSGInviteTableEvent;
import org.pente.gameServer.event.DSGTextTableEvent;
import org.pente.gameServer.kids.KidCopyMailer;
import org.pente.gameServer.kids.KidLiveSessions;
import org.pente.gameServer.kids.KidSettings;
import org.pente.gameServer.kids.test.InMemoryChatCopyStore;
import org.pente.gameServer.kids.test.InMemoryKidStorer;
import org.pente.gameServer.kids.test.MapPlayerStorer;
import org.pente.gameServer.kids.test.RecordingTextMailer;
import org.pente.gameServer.server.DSGEventToPlayerRouter;
import org.pente.gameServer.server.ServerTable;

/** Kid chat rules on table text and invites, driven through real ServerTable code. */
public class ServerTableKidTest extends TestCase {

    static final long SERVER = 7;
    static final long PARENT = 1;
    static final long KID = 100;
    static final long KID2 = 101;
    static final long BOB = 200;
    static final long AMY = 300;
    static final long AI = 600;
    private static final int TABLE = 3;

    private MapPlayerStorer players;
    private InMemoryChatCopyStore store;
    private KidLiveSessions sessions;
    private Router router;
    private SimpleDSGPlayerData kid;
    private SimpleDSGPlayerData kid2;
    private SimpleDSGPlayerData bob;
    private SimpleDSGPlayerData amy;
    private SimpleDSGPlayerData ai;

    public ServerTableKidTest(String name) {
        super(name);
    }

    /** Records every routed event by recipient. */
    static final class Router implements DSGEventToPlayerRouter {
        final List<String> names = new ArrayList<>();
        final List<DSGEvent> events = new ArrayList<>();

        public void addRoute(DSGEventListener l, String name) {
        }

        public DSGEventListener removeRoute(String name) {
            return null;
        }

        public DSGEventListener getRoute(String name) {
            return null;
        }

        public void routeEvent(DSGEvent e, String name) {
            names.add(name);
            events.add(e);
        }

        <T extends DSGEvent> List<T> to(String name, Class<T> type) {
            List<T> out = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(name) && type.isInstance(events.get(i))) {
                    out.add(type.cast(events.get(i)));
                }
            }
            return out;
        }
    }

    static SimpleDSGPlayerData player(long pid, String name, Long parentPid, char type) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setName(name);
        d.setParentPid(parentPid);
        d.setPlayerType(type);
        d.setStatus(DSGPlayerData.ACTIVE);
        return d;
    }

    /** ServerTable with only routing, the player storer and kid sessions wired. */
    static final class KidTable extends ServerTable {
        private final KidLiveSessions ks;

        KidTable(KidLiveSessions ks, DSGEventToPlayerRouter router, DSGPlayerStorer storer) {
            this.ks = ks;
            tableNum = TABLE;
            dsgEventRouter = router;
            dsgPlayerStorer = storer;
            playersInMainRoom = new Vector<>();
        }

        protected KidLiveSessions kidSessions() {
            return ks;
        }

        /** In the main room and seated at this table. */
        KidTable at(DSGPlayerData... ds) {
            for (DSGPlayerData d : ds) {
                playersInMainRoom.add(d);
                playersInTable.add(d);
            }
            return this;
        }

        /** In the main room only. */
        KidTable lobby(DSGPlayerData... ds) {
            for (DSGPlayerData d : ds) {
                playersInMainRoom.add(d);
            }
            return this;
        }
    }

    protected void setUp() {
        players = new MapPlayerStorer();
        SimpleDSGPlayerData parent = player(PARENT, "parent", null, DSGPlayerData.HUMAN);
        parent.setEmail("parent@example.invalid");
        parent.setEmailValid(true);
        players.put(parent);
        kid = player(KID, "kid", PARENT, DSGPlayerData.HUMAN);
        kid2 = player(KID2, "kid2", PARENT, DSGPlayerData.HUMAN);
        bob = player(BOB, "bob", null, DSGPlayerData.HUMAN);
        amy = player(AMY, "amy", null, DSGPlayerData.HUMAN);
        ai = player(AI, "ai", null, DSGPlayerData.COMPUTER);
        players.put(kid);
        players.put(kid2);
        players.put(bob);
        players.put(amy);
        players.put(ai);
        InMemoryKidStorer kids = new InMemoryKidStorer();
        kids.addToWhitelist(KID, AMY);
        kids.addToWhitelist(KID2, AMY);
        kids.storeSettings(new KidSettings(KID, false, true));
        kids.storeSettings(new KidSettings(KID2, false, false));
        store = new InMemoryChatCopyStore();
        KidCopyMailer mailer = new KidCopyMailer(true, new RecordingTextMailer(), players, kids);
        sessions = new KidLiveSessions(SERVER, players, kids, store, mailer);
        // humans join with their loaded state; the AI joins the way
        // ServerAIMainRoomController does, with no kid state
        for (SimpleDSGPlayerData d : new SimpleDSGPlayerData[]{kid, kid2, bob, amy}) {
            sessions.join(d.getName(), sessions.load(d));
        }
        router = new Router();
    }

    private KidTable table() {
        return new KidTable(sessions, router, players);
    }

    private static String key(long pid) {
        return "kids:livechat:" + SERVER + ":" + pid;
    }

    public void testTableTextToKidSpectatorDropped() {
        table().at(bob, kid, amy).handleText("bob", "hello");

        assertEquals(0, router.to("kid", DSGTextTableEvent.class).size());
        assertEquals(1, router.to("amy", DSGTextTableEvent.class).size());
        assertEquals(1, router.to("bob", DSGTextTableEvent.class).size());
    }

    public void testKidTextToWhitelistedPlayerDelivered() {
        table().at(kid, amy, bob).handleText("kid", "hi amy");

        List<DSGTextTableEvent> got = router.to("amy", DSGTextTableEvent.class);
        assertEquals(1, got.size());
        assertEquals("hi amy", got.get(0).getText());
        assertEquals(0, router.to("bob", DSGTextTableEvent.class).size());
    }

    public void testKidGetsOwnEcho() {
        table().at(kid, bob).handleText("kid", "me");

        assertEquals(1, router.to("kid", DSGTextTableEvent.class).size());
    }

    public void testComputerPlayerTextReachesKid() {
        table().at(ai, kid).handleText("ai", "good game");

        assertEquals(1, router.to("kid", DSGTextTableEvent.class).size());
    }

    public void testInviteTextBlankedForKid() {
        KidTable t = table().at(bob).lobby(kid, amy);

        t.handleInvite(new DSGInviteTableEvent("bob", TABLE, "kid", "come play"));
        t.handleInvite(new DSGInviteTableEvent("bob", TABLE, "amy", "come play"));

        List<DSGInviteTableEvent> toKid = router.to("kid", DSGInviteTableEvent.class);
        assertEquals(1, toKid.size());
        assertEquals("", toKid.get(0).getInviteText());
        assertEquals("bob", toKid.get(0).getPlayer());
        assertEquals("kid", toKid.get(0).getPlayerToInvite());
        assertEquals(TABLE, toKid.get(0).getTable());
        assertEquals("come play", router.to("amy", DSGInviteTableEvent.class).get(0).getInviteText());
    }

    public void testInviteResponseTextBlanked() {
        KidTable t = table().at(bob).lobby(kid, amy);

        t.handleInviteResponse(new DSGInviteResponseTableEvent("kid", TABLE, "bob", "on my way", true, false));
        t.handleInviteResponse(new DSGInviteResponseTableEvent("amy", TABLE, "bob", "soon", false, false));

        List<DSGInviteResponseTableEvent> got = router.to("bob", DSGInviteResponseTableEvent.class);
        assertEquals(2, got.size());
        assertEquals("kid", got.get(0).getPlayer());
        assertEquals("", got.get(0).getResponseText());
        assertTrue(got.get(0).getAccept());
        assertEquals("soon", got.get(1).getResponseText());
        assertTrue(!got.get(1).getAccept());
    }

    public void testDeliveredBuffersOnlyForKidWithCopyOn() {
        KidTable t = table().at(amy, kid, kid2, bob);

        t.handleText("amy", "hello kids");
        t.handleText("bob", "unseen");

        List<String> lines = store.lines(key(KID));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("hello kids"));
        assertTrue(lines.get(0), lines.get(0).contains("[table " + TABLE + "] amy: "));
        assertTrue(store.lines(key(KID2)).isEmpty());
        assertTrue(store.lines(key(AMY)).isEmpty());
        assertTrue(store.lines(key(BOB)).isEmpty());
    }
}
