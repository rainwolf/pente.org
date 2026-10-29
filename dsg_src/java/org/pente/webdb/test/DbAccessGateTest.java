package org.pente.webdb.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import junit.framework.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.pente.game.GameTreeData;
import org.pente.game.GameVenueStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerGameData;
import org.pente.webdb.WebDbApiServlet;
import org.pente.webdb.WebDbAuth;

/**
 * DB-free tests for the subscriber-only gate on {@code /api/db/*}
 * ({@link WebDbAuth#requireDbAccess}, wired centrally in
 * {@link WebDbApiServlet#service}). The real servlet is initialised with a
 * {@link Proxy}-faked {@link ServletContext} that publishes a fake
 * {@link DSGPlayerStorer} (players keyed by name) and an empty
 * {@link GameVenueStorer}, then driven with Proxy request/response fakes.
 * Run via {@code ant test-one -Dtest=org.pente.webdb.test.DbAccessGateTest}.
 */
public class DbAccessGateTest extends TestCase {

    private static final long DAY = 1000L * 3600 * 24;

    private Map<String, DSGPlayerData> players;
    private boolean storerThrows;
    private WebDbApiServlet servlet;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                DbAccessGateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(DbAccessGateTest.class);
    }

    public DbAccessGateTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        long now = System.currentTimeMillis();
        players = new HashMap<String, DSGPlayerData>();
        // subscriber: old account, databaseAccess flag set, no games needed
        players.put("sub", player("sub", true, now - 2000 * DAY, 0));
        // plain old account with games but no subscription
        players.put("free", player("free", false, now - 2000 * DAY, 50));
        // new player (registered 10 days ago) with a game: grace access
        players.put("newbie", player("newbie", false, now - 10 * DAY, 1));
        // new player with zero games: no access
        players.put("newnogames", player("newnogames", false, now - 10 * DAY, 0));
        // registered 61 days ago (outside the 60-day window), has games
        players.put("expired", player("expired", false, now - 61 * DAY, 3));
        // no register date at all: must not NPE, no access
        players.put("nodate", player("nodate", false, null, 3));
        storerThrows = false;

        final DSGPlayerStorer playerStorer = (DSGPlayerStorer) proxy(
                DSGPlayerStorer.class, (p, m, a) -> {
                    if ("loadPlayer".equals(m.getName()) && a != null
                            && a.length == 1 && a[0] instanceof String) {
                        if (storerThrows) {
                            throw new DSGPlayerStoreException("boom");
                        }
                        return players.get((String) a[0]);
                    }
                    return defaultValue(m.getReturnType());
                });
        final GameVenueStorer venueStorer = (GameVenueStorer) proxy(
                GameVenueStorer.class, (p, m, a) -> {
                    if ("getGameTree".equals(m.getName())) {
                        return new Vector<GameTreeData>();
                    }
                    return defaultValue(m.getReturnType());
                });
        final ServletContext ctx = (ServletContext) proxy(
                ServletContext.class, (p, m, a) -> {
                    if ("getAttribute".equals(m.getName())) {
                        if (DSGPlayerStorer.class.getName().equals(a[0])) {
                            return playerStorer;
                        }
                        if (GameVenueStorer.class.getName().equals(a[0])) {
                            return venueStorer;
                        }
                        return null;
                    }
                    return defaultValue(m.getReturnType());
                });
        ServletConfig config = (ServletConfig) proxy(
                ServletConfig.class, (p, m, a) -> {
                    if ("getServletContext".equals(m.getName())) {
                        return ctx;
                    }
                    if ("getInitParameterNames".equals(m.getName())) {
                        return Collections.emptyEnumeration();
                    }
                    return defaultValue(m.getReturnType());
                });

        servlet = new WebDbApiServlet();
        servlet.init(config);
    }

    // ------------------------------------------------------------------
    // anonymous -> 401
    // ------------------------------------------------------------------

    public void testAnonymousGetIs401() throws Exception {
        Resp r = call("GET", "/venues", null);
        assertEquals(401, r.status);
        assertEquals("auth", errorCode(r));
    }

    public void testAnonymousIs401OnEveryMethod() throws Exception {
        assertEquals(401, call("POST", "/position-stats", null).status);
        assertEquals(401, call("POST", "/games/search", null).status);
        assertEquals(401, call("PUT", "/analyses/1", null).status);
        assertEquals(401, call("DELETE", "/collection/1", null).status);
        assertEquals(401, call("GET", "/games/123", null).status);
    }

    public void testUnknownEndpointIsGatedToo() throws Exception {
        // gate runs before routing, so an unknown/new path can't bypass it
        assertEquals(401, call("GET", "/some-new-endpoint", null).status);
        assertEquals(403, call("GET", "/some-new-endpoint", "free").status);
    }

    public void testUnknownPlayerIs401() throws Exception {
        Resp r = call("GET", "/venues", "ghost");
        assertEquals(401, r.status);
        assertEquals("auth", errorCode(r));
    }

    public void testStorerFailureIs401() throws Exception {
        storerThrows = true;
        Resp r = call("GET", "/venues", "sub");
        assertEquals(401, r.status);
        assertEquals("auth", errorCode(r));
    }

    // ------------------------------------------------------------------
    // logged in without access -> 403
    // ------------------------------------------------------------------

    public void testLoggedInWithoutAccessIs403() throws Exception {
        Resp r = call("GET", "/venues", "free");
        assertEquals(403, r.status);
        assertEquals("subscription_required", errorCode(r));
        assertEquals("no-store", r.headers.get("Cache-Control"));

        assertEquals(403, call("POST", "/position-stats", "free").status);
        assertEquals(403, call("POST", "/games/search", "free").status);
        assertEquals(403, call("PUT", "/analyses/1", "free").status);
        assertEquals(403, call("DELETE", "/collection/1", "free").status);
    }

    // ------------------------------------------------------------------
    // subscriber -> 200
    // ------------------------------------------------------------------

    public void testSubscriberIs200() throws Exception {
        Resp r = call("GET", "/venues", "sub");
        assertEquals(200, r.status);
        assertTrue(json(r).getAsJsonObject().has("variants"));
        assertEquals("private, max-age=300", r.headers.get("Cache-Control"));
    }

    public void testSubscriberPassesGateToRouting() throws Exception {
        // passes the gate, then the normal 404 envelope
        Resp r = call("GET", "/some-new-endpoint", "sub");
        assertEquals(404, r.status);
        assertEquals("not_found", errorCode(r));
    }

    // ------------------------------------------------------------------
    // new-player rule (legacy database.jsp)
    // ------------------------------------------------------------------

    public void testNewPlayerWithGamesIs200() throws Exception {
        assertEquals(200, call("GET", "/venues", "newbie").status);
    }

    public void testNewPlayerWithoutGamesIs403() throws Exception {
        assertEquals(403, call("GET", "/venues", "newnogames").status);
    }

    public void testExpiredNewPlayerWindowIs403() throws Exception {
        assertEquals(403, call("GET", "/venues", "expired").status);
    }

    public void testMissingRegisterDateIs403() throws Exception {
        assertEquals(403, call("GET", "/venues", "nodate").status);
    }

    public void testHasDbAccessWindowBoundary() throws Exception {
        long now = 1_000_000_000_000L;
        long w = WebDbAuth.NEW_PLAYER_WINDOW_MS;
        assertEquals(1000L * 3600 * 24 * 30 * 2, w);
        assertFalse(WebDbAuth.hasDbAccess(null, now));
        // strictly greater than now - window, like database.jsp
        assertTrue(WebDbAuth.hasDbAccess(
                player("a", false, now - w + 1, 1), now));
        assertFalse(WebDbAuth.hasDbAccess(
                player("b", false, now - w, 1), now));
        assertFalse(WebDbAuth.hasDbAccess(
                player("c", false, now - w + 1, 0), now));
        assertTrue(WebDbAuth.hasDbAccess(
                player("d", true, now - 10 * w, 0), now));
    }

    // ------------------------------------------------------------------
    // /ping stays open and reports dbAccess
    // ------------------------------------------------------------------

    public void testPingAnonymous() throws Exception {
        Resp r = call("GET", "/ping", null);
        assertEquals(200, r.status);
        JsonObject o = json(r).getAsJsonObject();
        assertTrue(o.get("ok").getAsBoolean());
        assertTrue(o.get("auth").isJsonNull() || !o.has("auth"));
        assertFalse(o.get("dbAccess").getAsBoolean());
    }

    public void testPingLoggedInWithoutAccess() throws Exception {
        JsonObject o = json(call("GET", "/ping", "free")).getAsJsonObject();
        assertTrue(o.get("ok").getAsBoolean());
        assertEquals("free", o.get("auth").getAsString());
        assertFalse(o.get("dbAccess").getAsBoolean());
    }

    public void testPingSubscriberAndNewPlayer() throws Exception {
        JsonObject o = json(call("GET", "/ping", "sub")).getAsJsonObject();
        assertEquals("sub", o.get("auth").getAsString());
        assertTrue(o.get("dbAccess").getAsBoolean());
        o = json(call("GET", "/ping", "newbie")).getAsJsonObject();
        assertTrue(o.get("dbAccess").getAsBoolean());
    }

    public void testPingFeatures() throws Exception {
        Resp r = call("GET", "/ping", null);
        assertEquals(200, r.status);
        com.google.gson.JsonArray f =
                json(r).getAsJsonObject().get("features").getAsJsonArray();
        String[] want = {"excludeTimeouts", "liveOrTurnBased", "badges", "blocked"};
        assertEquals(want.length, f.size());
        for (int i = 0; i < want.length; i++) {
            assertEquals(want[i], f.get(i).getAsString());
        }
    }

    public void testPingOnlyOpenForGet() throws Exception {
        assertEquals(401, call("POST", "/ping", null).status);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static DSGPlayerData player(String name, boolean dbAccess,
                                        Object registered, int games) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setName(name);
        p.setPlayerID(name.hashCode() & 0x7fffffff);
        p.setDatabaseAccess(dbAccess);
        if (registered != null) {
            p.setRegisterDate(new Date((Long) registered));
        }
        if (games > 0) {
            SimpleDSGPlayerGameData g = new SimpleDSGPlayerGameData();
            g.setComputer('N');
            g.setWins(games);
            p.addPlayerGameData(g);
        }
        return p;
    }

    private static final class Resp {
        int status = 200; // servlet default when setStatus never called
        Map<String, String> headers = new HashMap<String, String>();
        StringWriter body = new StringWriter();
    }

    private Resp call(final String method, final String path,
                      final String name) throws Exception {
        final Resp r = new Resp();
        final PrintWriter writer = new PrintWriter(r.body);
        HttpServletRequest req = (HttpServletRequest) proxy(
                HttpServletRequest.class, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getMethod": return method;
                        case "getPathInfo": return path;
                        case "getAttribute":
                            return "name".equals(a[0]) ? name : null;
                        case "getProtocol": return "HTTP/1.1";
                        case "getDateHeader": return -1L;
                        case "getHeaderNames":
                            return Collections.emptyEnumeration();
                        default: return defaultValue(m.getReturnType());
                    }
                });
        HttpServletResponse resp = (HttpServletResponse) proxy(
                HttpServletResponse.class, (p, m, a) -> {
                    switch (m.getName()) {
                        case "setStatus": r.status = (Integer) a[0]; return null;
                        case "getStatus": return r.status;
                        case "setHeader":
                            r.headers.put((String) a[0], (String) a[1]);
                            return null;
                        case "getWriter": return writer;
                        default: return defaultValue(m.getReturnType());
                    }
                });
        // service(ServletRequest, ServletResponse) is the container entry
        servlet.service((jakarta.servlet.ServletRequest) req,
                (jakarta.servlet.ServletResponse) resp);
        writer.flush();
        return r;
    }

    // the bundled junit 3.x predates assertFalse
    private static void assertFalse(boolean b) {
        assertTrue(!b);
    }

    private static String errorCode(Resp r) {
        return json(r).getAsJsonObject().getAsJsonObject("error")
                .get("code").getAsString();
    }

    private static JsonElement json(Resp r) {
        return JsonParser.parseString(r.body.toString());
    }

    private interface Handler {
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable;
    }

    private static Object proxy(Class<?> iface, final Handler h) {
        return Proxy.newProxyInstance(DbAccessGateTest.class.getClassLoader(),
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
