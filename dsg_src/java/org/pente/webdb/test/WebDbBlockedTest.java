package org.pente.webdb.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import junit.framework.*;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.pente.database.DBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.server.ActivityData;
import org.pente.gameServer.server.ActivityLogger;
import org.pente.gameServer.server.Resources;
import org.pente.webdb.GameLoadHandler;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.WebDbAuth;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.PositionStatsResponse;

/**
 * DB-free tests for the {@code /api/db/*} blocked parity with the legacy
 * {@code ActivityLogger.viewDb} check (B5), and for the early
 * {@code liveOrTurnBased} validation in both POST handlers.
 * Run via {@code ant test-one -Dtest=org.pente.webdb.test.WebDbBlockedTest}.
 */
public class WebDbBlockedTest extends TestCase {

    private static final int[] MOVES = {180, 181, 182};

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                WebDbBlockedTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(WebDbBlockedTest.class);
    }

    public WebDbBlockedTest(String name) {
        super(name);
    }

    // ------------------------------------------------------------------
    // fake logger
    // ------------------------------------------------------------------

    private static final class Call {
        String name;
        byte[] address;
        long hash;
        int[] moves;
        String search;
    }

    private static final class FakeLogger extends ActivityLogger {
        final List<Call> calls = new ArrayList<Call>();
        final boolean flag;
        final boolean throwIt;

        FakeLogger(boolean flag, boolean throwIt) {
            super(new Resources());
            this.flag = flag;
            this.throwIt = throwIt;
        }

        public boolean viewDb(ActivityData activity, long hashCode,
                              int[] moves, String searchData) {
            Call c = new Call();
            c.name = activity.getPlayerName();
            c.address = activity.getAddress();
            c.hash = hashCode;
            c.moves = moves;
            c.search = searchData;
            calls.add(c);
            if (throwIt) {
                throw new RuntimeException("boom");
            }
            return flag;
        }
    }

    // ------------------------------------------------------------------
    // position-stats / games/search blocked
    // ------------------------------------------------------------------

    public void testPositionStatsBlocked() throws Exception {
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = post("alice", "{\"game\":1,\"moves\":[180,181,182]}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(200, r.status);
        JsonObject o = r.json().getAsJsonObject();
        assertTrue(o.get("blocked").getAsBoolean());
        assertEquals(0, o.getAsJsonArray("nextMoves").size());
        assertEquals(0L, o.get("totalGames").getAsLong());
        assertEquals(0, o.get("rotation").getAsInt());
        assertEquals(1, log.calls.size());
        assertEquals("webdb position-stats game=1", log.calls.get(0).search);
    }

    public void testBlockedSkipsCompute() throws Exception {
        // null DB handles: any compute would NPE and map to 500
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        h.setActivityLogger(new FakeLogger(true, false));
        Resp r = post("alice", "{\"game\":1,\"moves\":[180,181,182]}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(200, r.status);
    }

    public void testViewDbArgs() throws Exception {
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = post("alice", "{\"game\":1,\"moves\":[180,181,182]}");
        h.handle(r.req, r.resp);
        assertEquals(1, log.calls.size());
        Call c = log.calls.get(0);
        assertEquals("alice", c.name);
        assertTrue(Arrays.equals(new byte[]{10, 1, 2, 3}, c.address));
        assertEquals(WebDbAuth.positionHash(1, MOVES), c.hash);
        assertTrue(c.hash != 0);
        assertTrue(Arrays.equals(MOVES, c.moves));
    }

    public void testGameSearchBlocked() throws Exception {
        GameSearchHandler h = new GameSearchHandler(null, null, nullVenues());
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = post("alice", "{\"game\":1,\"moves\":[180,181,182]}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(200, r.status);
        JsonObject o = r.json().getAsJsonObject();
        assertTrue(o.get("blocked").getAsBoolean());
        assertEquals(0, o.getAsJsonArray("games").size());
        assertEquals(0L, o.get("total").getAsLong());
        assertEquals("webdb games/search game=1", log.calls.get(0).search);
        assertEquals(WebDbAuth.positionHash(1, MOVES), log.calls.get(0).hash);
    }

    // ------------------------------------------------------------------
    // games/{gid}
    // ------------------------------------------------------------------

    public void testGameLoadBlocked403() throws Exception {
        GameLoadHandler h = new GameLoadHandler(null);
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = get("alice");
        h.handle(r.req, r.resp, "123");
        r.flush();
        assertEquals(403, r.status);
        assertEquals("blocked", r.errorCode());
        assertEquals(1, log.calls.size());
        assertEquals(WebDbAuth.NO_POSITION_HASH, log.calls.get(0).hash);
        assertEquals(0, log.calls.get(0).moves.length);
        assertEquals("webdb games/123", log.calls.get(0).search);
    }

    public void testGameLoadInvalidGidStill400() throws Exception {
        GameLoadHandler h = new GameLoadHandler(null);
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = get("alice");
        h.handle(r.req, r.resp, "abc");
        r.flush();
        assertEquals(400, r.status);
        assertEquals("bad_request", r.errorCode());
        assertEquals(0, log.calls.size());
    }

    // ------------------------------------------------------------------
    // WebDbAuth.viewDbBlocked
    // ------------------------------------------------------------------

    public void testNoLoggerNotBlocked() throws Exception {
        assertFalse(WebDbAuth.viewDbBlocked(null, get("alice").req, 0,
                new int[0], "t"));
        // handler with no setActivityLogger: not blocked, compute runs
        // (empty moves returns early without touching the DB)
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        Resp r = post("alice", "{\"game\":1,\"moves\":[]}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(200, r.status);
        assertFalse(r.json().getAsJsonObject().get("blocked").getAsBoolean());
    }

    public void testDisabledNeverCallsLogger() throws Exception {
        FakeLogger log = new FakeLogger(true, false);
        assertFalse(WebDbAuth.viewDbBlocked(false, log,
                get("alice").req, 0, new int[0], "t"));
        assertEquals(0, log.calls.size());
    }

    public void testViewDbExceptionIsNotBlocked() throws Exception {
        FakeLogger log = new FakeLogger(true, true);
        assertFalse(WebDbAuth.viewDbBlocked(true, log,
                get("alice").req, 0, new int[0], "t"));
        assertEquals(1, log.calls.size());
    }

    public void testKillSwitchCommittedOn() {
        assertTrue(WebDbAuth.VIEWDB_BLOCK_ENABLED);
    }

    // ------------------------------------------------------------------
    // real ActivityLogger: live rated play blocks, turn-based never does
    // ------------------------------------------------------------------

    public void testLiveRatedPlayerBlocked() throws Exception {
        {
            ActivityLogger logger = realLogger();
            startLiveRated(logger);
            assertTrue(WebDbAuth.viewDbBlocked(true, logger,
                    get("alice").req, 0, new int[0], "t"));
            // the opponent too
            assertTrue(WebDbAuth.viewDbBlocked(true, logger,
                    request("bob", "10.7.7.7"), 0, new int[0], "t"));
            // a different player from a different network: not blocked
            assertFalse(WebDbAuth.viewDbBlocked(true, logger,
                    request("carol", "192.168.5.5"), 0, new int[0], "t"));
        }
    }

    /**
     * A table that was just started (no updateGameState yet) holds hash 0 and
     * null moves. A hash-0 request (games/{gid}, empty moves, tombstone) must
     * not trip over it: alice is still blocked by the in-live-rated-game check.
     */
    public void testFreshTableHashZeroPlayerBlocked() throws Exception {
        ActivityLogger logger = realLogger();
        logger.joinPlayer(new ActivityData("alice", "10.1.2.3", 1));
        logger.joinPlayer(new ActivityData("bob", "10.7.7.7", 1));
        logger.startGame(1, 1, "alice", "bob", true);
        assertTrue(WebDbAuth.viewDbBlocked(true, logger,
                get("alice").req, 0, new int[0], "t"));
        assertTrue(WebDbAuth.viewDbBlocked(true, logger,
                get("alice").req, 0, null, "t"));
        assertFalse(WebDbAuth.viewDbBlocked(true, logger,
                request("carol", "192.168.5.5"), 0, new int[0], "t"));

        // games/{gid} for alice is 403 blocked
        GameLoadHandler h = new GameLoadHandler(null);
        h.setActivityLogger(logger);
        Resp r = get("alice");
        h.handle(r.req, r.resp, "123");
        r.flush();
        assertEquals(403, r.status);
        assertEquals("blocked", r.errorCode());
    }

    public void testTurnBasedRatedPlayerNotBlocked() throws Exception {
        {
            ActivityLogger logger = realLogger();
            // alice is "in a TB rated game": TB play never calls joinPlayer
            // or startGame, so the logger holds nothing for her.
            assertFalse(WebDbAuth.viewDbBlocked(true, logger,
                    get("alice").req, 0, new int[0], "t"));
            assertFalse(WebDbAuth.viewDbBlocked(true, logger,
                    get("alice").req, WebDbAuth.positionHash(1, MOVES),
                    MOVES, "t"));

            // games/{gid} with a TB gid: hash 0, no moves -> not blocked
            GameLoadHandler h = new GameLoadHandler(null);
            h.setActivityLogger(logger);
            Resp r = get("alice");
            h.handle(r.req, r.resp, "50000000000123");
            r.flush();
            // not blocked, so load ran against the null storer (500)
            assertTrue(r.status != 403);

            // and with an unrelated live rated table running, a different
            // player on a different network is still not blocked
            startLiveRated(logger);
            assertFalse(WebDbAuth.viewDbBlocked(true, logger,
                    request("carol", "192.168.5.5"), 0, new int[0], "t"));
        }
    }

    /**
     * alice (10.1.2.3) and bob join a live server and start a rated game.
     * The first move is recorded the way {@code ServerTable} does right after
     * {@code startGame} (center stone), so the table carries a real hash.
     */
    private static void startLiveRated(ActivityLogger logger) {
        logger.joinPlayer(new ActivityData("alice", "10.1.2.3", 1));
        logger.joinPlayer(new ActivityData("bob", "10.7.7.7", 1));
        logger.startGame(1, 1, "alice", "bob", true);
        logger.updateGameState(1, 1, WebDbAuth.positionHash(1, new int[]{180}),
                new int[]{180});
    }

    // ------------------------------------------------------------------
    // early liveOrTurnBased validation
    // ------------------------------------------------------------------

    public void testInvalidLiveModeEmptyMoves400() throws Exception {
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        Resp r = post("alice",
                "{\"game\":1,\"moves\":[],\"filters\":{\"liveOrTurnBased\":\"Live\"}}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(400, r.status);
        assertEquals("bad_request", r.errorCode());
    }

    public void testInvalidLiveModeTombstone400() throws Exception {
        PositionStatsHandler h = new PositionStatsHandler(null, nullVenues());
        Resp r = post("alice", "{\"game\":" + GridStateFactory.TOMBSTONE
                + ",\"moves\":[180],\"filters\":{\"liveOrTurnBased\":\"tb\"}}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(400, r.status);
        assertEquals("bad_request", r.errorCode());
    }

    public void testInvalidLiveModeMineScope400() throws Exception {
        DSGPlayerStorer storer = (DSGPlayerStorer) proxy(DSGPlayerStorer.class,
                (p, m, a) -> {
                    if ("loadPlayer".equals(m.getName())
                            && a[0] instanceof String) {
                        return player5((String) a[0]);
                    }
                    return defaultValue(m.getReturnType());
                });
        // null webDbStorer: mineSearch would NPE -> 500
        GameSearchHandler h = new GameSearchHandler(null, null, null,
                nullVenues(), null, storer);
        FakeLogger log = new FakeLogger(true, false);
        h.setActivityLogger(log);
        Resp r = post("alice",
                "{\"game\":1,\"moves\":[],\"scope\":\"mine\","
                        + "\"filters\":{\"liveOrTurnBased\":\"tb\"}}");
        h.handle(r.req, r.resp);
        r.flush();
        assertEquals(400, r.status);
        assertEquals("bad_request", r.errorCode());
        // validation runs before the blocked check
        assertEquals(0, log.calls.size());
    }

    // ------------------------------------------------------------------
    // positionHash / DTO wire
    // ------------------------------------------------------------------

    public void testPositionHash() {
        assertEquals(0L, WebDbAuth.positionHash(1, new int[0]));
        assertEquals(0L, WebDbAuth.positionHash(1, null));
        GridState s = GridStateFactory.createGridState(1);
        s.addMove(180);
        s.addMove(181);
        s.addMove(182);
        assertEquals(s.getHash(), WebDbAuth.positionHash(1, MOVES));
        assertEquals(0L, WebDbAuth.positionHash(GridStateFactory.TOMBSTONE,
                MOVES));
    }

    public void testUnblockedResponseSerializesFalse() {
        String a = new GsonBuilder().serializeNulls().create()
                .toJson(new PositionStatsResponse());
        String b = new GsonBuilder().serializeNulls().create()
                .toJson(new GameSearchResponse());
        assertTrue(a, a.contains("\"blocked\":false"));
        assertTrue(b, b.contains("\"blocked\":false"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static DSGPlayerData player5(String name) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setName(name);
        p.setPlayerID(5);
        return p;
    }

    /**
     * Real logger; the ban query ({@code dsg_ip}) returns no row and
     * {@code loadPlayer(String)} returns a player with pid 5.
     * {@code Resources.setDsgPlayerStorer} takes a concrete
     * {@code CacheDSGPlayerStorer}, whose constructor needs Redis, so the
     * Proxy storer is put into the logger's private field instead.
     */
    private static ActivityLogger realLogger() throws Exception {
        DSGPlayerStorer storer = (DSGPlayerStorer) proxy(DSGPlayerStorer.class,
                (p, m, a) -> {
                    if ("loadPlayer".equals(m.getName())
                            && a[0] instanceof String) {
                        return player5((String) a[0]);
                    }
                    return defaultValue(m.getReturnType());
                });
        final ResultSet rs = (ResultSet) proxy(ResultSet.class,
                (p, m, a) -> defaultValue(m.getReturnType()));
        final PreparedStatement ps = (PreparedStatement) proxy(
                PreparedStatement.class, (p, m, a) -> {
                    if ("executeQuery".equals(m.getName())) return rs;
                    return defaultValue(m.getReturnType());
                });
        final Connection con = (Connection) proxy(Connection.class,
                (p, m, a) -> {
                    if ("prepareStatement".equals(m.getName())) return ps;
                    return defaultValue(m.getReturnType());
                });
        DBHandler db = (DBHandler) proxy(DBHandler.class, (p, m, a) -> {
            if ("getConnection".equals(m.getName())) return con;
            return defaultValue(m.getReturnType());
        });
        Resources res = new Resources();
        res.setDbHandler(db);
        ActivityLogger logger = new ActivityLogger(res);
        Field f = ActivityLogger.class.getDeclaredField("dsgPlayerStorer");
        f.setAccessible(true);
        f.set(logger, storer);
        return logger;
    }

    private static GameVenueStorer nullVenues() {
        return (GameVenueStorer) proxy(GameVenueStorer.class,
                (p, m, a) -> defaultValue(m.getReturnType()));
    }

    private static final class Resp {
        int status = 200; // servlet default when setStatus never called
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        HttpServletRequest req;
        HttpServletResponse resp;

        void flush() {
            writer.flush();
        }

        JsonElement json() {
            flush();
            return JsonParser.parseString(body.toString());
        }

        String errorCode() {
            return json().getAsJsonObject().getAsJsonObject("error")
                    .get("code").getAsString();
        }
    }

    private static HttpServletRequest request(String name, String addr) {
        return build("GET", name, addr, "").req;
    }

    private static Resp get(String name) {
        return build("GET", name, "10.1.2.3", "");
    }

    private static Resp post(String name, String body) {
        return build("POST", name, "10.1.2.3", body);
    }

    private static Resp build(final String method, final String name,
                              final String addr, String body) {
        final Resp r = new Resp();
        final ByteArrayInputStream in = new ByteArrayInputStream(
                body.getBytes(StandardCharsets.UTF_8));
        final ServletInputStream sin = new ServletInputStream() {
            public int read() throws IOException {
                return in.read();
            }

            public boolean isFinished() {
                return in.available() == 0;
            }

            public boolean isReady() {
                return true;
            }

            public void setReadListener(ReadListener l) {
            }
        };
        r.req = (HttpServletRequest) proxy(HttpServletRequest.class,
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getMethod": return method;
                        case "getAttribute":
                            return "name".equals(a[0]) ? name : null;
                        case "getRemoteAddr": return addr;
                        case "getInputStream": return sin;
                        case "getProtocol": return "HTTP/1.1";
                        case "getDateHeader": return -1L;
                        case "getHeaderNames":
                            return Collections.emptyEnumeration();
                        default: return defaultValue(m.getReturnType());
                    }
                });
        r.resp = (HttpServletResponse) proxy(HttpServletResponse.class,
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "setStatus": r.status = (Integer) a[0]; return null;
                        case "getStatus": return r.status;
                        case "getWriter": return r.writer;
                        default: return defaultValue(m.getReturnType());
                    }
                });
        return r;
    }

    // the bundled junit 3.x predates assertFalse
    private static void assertFalse(boolean b) {
        assertTrue(!b);
    }

    private interface Handler {
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable;
    }

    private static Object proxy(Class<?> iface, final Handler h) {
        return Proxy.newProxyInstance(WebDbBlockedTest.class.getClassLoader(),
                new Class<?>[]{iface}, new InvocationHandler() {
                    public Object invoke(Object p, Method m, Object[] a)
                            throws Throwable {
                        if (m.getDeclaringClass() == Object.class) {
                            switch (m.getName()) {
                                case "hashCode": return System.identityHashCode(p);
                                case "equals": return p == a[0];
                                default: return iface.getName() + "-fake";
                            }
                        }
                        return h.invoke(p, m, a);
                    }
                });
    }

    private static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        if (t == float.class) return 0f;
        return 0d;
    }
}
