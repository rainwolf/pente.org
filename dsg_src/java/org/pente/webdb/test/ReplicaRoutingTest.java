package org.pente.webdb.test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Vector;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

import junit.framework.*;

import org.pente.database.DBHandler;
import org.pente.game.GameStorer;
import org.pente.game.GameTreeData;
import org.pente.game.GameVenueStorer;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameDatabase.GameStorerSearcher;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.WebDbApiServlet;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.PositionStatsRequest;

/**
 * DB-free tests that the webdb API sends heavy archive reads to the read-only
 * replica handler and keeps personal/auth paths on the primary, that a missing
 * replica handler falls back to the primary, and that the stats cache is
 * bypassed. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.test.ReplicaRoutingTest}.
 */
public class ReplicaRoutingTest extends TestCase {

    /** Counts getConnection calls; hands out an all-empty fake connection. */
    private static final class CountingDb implements DBHandler {
        int gets;
        public Connection getConnection() {
            gets++;
            return (Connection) proxy(Connection.class, (p, m, a) -> {
                if ("prepareStatement".equals(m.getName())) {
                    return proxy(PreparedStatement.class, (p2, m2, a2) -> {
                        if ("executeQuery".equals(m2.getName())) {
                            return proxy(ResultSet.class, (p3, m3, a3) ->
                                    dflt(m3.getReturnType()));
                        }
                        return dflt(m2.getReturnType());
                    });
                }
                return dflt(m.getReturnType());
            });
        }
        public void freeConnection(Connection con) { }
        public void destroy() { }
    }

    private CountingDb primary;
    private CountingDb ro;
    private GameStorer primaryStorer;
    private GameStorer roStorer;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                ReplicaRoutingTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(ReplicaRoutingTest.class);
    }

    public ReplicaRoutingTest(String name) {
        super(name);
    }

    protected void setUp() {
        primary = new CountingDb();
        ro = new CountingDb();
        primaryStorer = (GameStorer) proxy(GameStorer.class,
                (p, m, a) -> dflt(m.getReturnType()));
        roStorer = (GameStorer) proxy(GameStorer.class,
                (p, m, a) -> dflt(m.getReturnType()));
    }

    private WebDbApiServlet initServlet(final boolean withRo) throws Exception {
        final DSGPlayerStorer players = (DSGPlayerStorer) proxy(
                DSGPlayerStorer.class, (p, m, a) -> dflt(m.getReturnType()));
        final GameVenueStorer venues = (GameVenueStorer) proxy(
                GameVenueStorer.class, (p, m, a) -> {
                    if ("getGameTree".equals(m.getName())) {
                        return new Vector<GameTreeData>();
                    }
                    return dflt(m.getReturnType());
                });
        final GameStorerSearcher searcher = (GameStorerSearcher) proxy(
                GameStorerSearcher.class, (p, m, a) -> dflt(m.getReturnType()));
        final ServletContext ctx = (ServletContext) proxy(
                ServletContext.class, (p, m, a) -> {
                    if ("getAttribute".equals(m.getName())) {
                        String k = (String) a[0];
                        if (DBHandler.class.getName().equals(k)) return primary;
                        if (GameStorer.class.getName().equals(k)) return primaryStorer;
                        if (withRo && WebDbApiServlet.RO_DB_ATTR.equals(k)) return ro;
                        if (withRo && WebDbApiServlet.RO_GAME_STORER_ATTR.equals(k)) return roStorer;
                        if (DSGPlayerStorer.class.getName().equals(k)) return players;
                        if (GameVenueStorer.class.getName().equals(k)) return venues;
                        if (GameStorerSearcher.class.getName().equals(k)) return searcher;
                        return null;
                    }
                    return dflt(m.getReturnType());
                });
        ServletConfig config = (ServletConfig) proxy(ServletConfig.class,
                (p, m, a) -> {
                    if ("getServletContext".equals(m.getName())) return ctx;
                    if ("getInitParameterNames".equals(m.getName())) {
                        return Collections.emptyEnumeration();
                    }
                    return dflt(m.getReturnType());
                });
        WebDbApiServlet s = new WebDbApiServlet();
        s.init(config);
        return s;
    }

    // ------------------------------------------------------------ wiring

    public void testWiringArchiveOnRoPersonalOnPrimary() throws Exception {
        initServlet(true);
        Object stats = staticField("positionStatsHandler");
        Object search = staticField("gameSearchHandler");
        assertSame(ro, field(stats, "archiveDb"));
        assertSame(primary, field(stats, "dbHandler"));
        assertSame(ro, field(search, "archiveDb"));
        assertSame(roStorer, field(search, "gameStorer"));
        assertSame(primary, field(search, "dbHandler"));
        assertSame(ro, field(staticField("playersHandler"), "dbHandler"));
        assertSame(roStorer, field(staticField("gameLoadHandler"), "gameStorer"));
        // personal collection / analyses persistence stays on the primary
        assertSame(primary, field(staticField("webDbStorer"), "dbHandler"));
    }

    public void testFallsBackToPrimaryWhenRoAbsent() throws Exception {
        initServlet(false);
        Object stats = staticField("positionStatsHandler");
        Object search = staticField("gameSearchHandler");
        assertSame(primary, field(stats, "archiveDb"));
        assertSame(primary, field(search, "archiveDb"));
        assertSame(primaryStorer, field(search, "gameStorer"));
        assertSame(primary, field(staticField("playersHandler"), "dbHandler"));
        assertSame(primaryStorer,
                field(staticField("gameLoadHandler"), "gameStorer"));
    }

    // ---------------------------------------------------------- behaviour

    public void testArchivePathsHitRoNotPrimary() throws Exception {
        initServlet(true);
        PositionStatsHandler stats =
                (PositionStatsHandler) staticField("positionStatsHandler");
        GameSearchHandler search =
                (GameSearchHandler) staticField("gameSearchHandler");

        stats.computeArchive(statsReq("archive"));
        assertTrue("stats on ro", ro.gets == 1);
        assertTrue("stats not on primary", primary.gets == 0);

        ro.gets = 0;
        try {
            search.search(searchReq("archive"));
        } catch (Exception e) {
            // empty fake ResultSet may trip the count; routing is what counts
        }
        assertTrue("search on ro", ro.gets >= 1);
        assertTrue("search not on primary", primary.gets == 0);
    }

    public void testMineScopeStaysOnPrimary() throws Exception {
        initServlet(true);
        GameSearchHandler search =
                (GameSearchHandler) staticField("gameSearchHandler");
        try {
            search.search(searchReq("mine"), 7L);
        } catch (Exception e) {
            // fake rows; only the connection routing matters
        }
        assertTrue("mine on primary", primary.gets >= 1);
        assertTrue("mine not on ro", ro.gets == 0);
    }

    public void testStatsCacheBypassed() throws Exception {
        initServlet(true);
        PositionStatsHandler stats =
                (PositionStatsHandler) staticField("positionStatsHandler");
        Field f = PositionStatsHandler.class
                .getDeclaredField("STATS_CACHE_ENABLED");
        f.setAccessible(true);
        assertTrue("cache switch off", !f.getBoolean(null));

        stats.computeArchive(statsReq("archive"));
        stats.computeArchive(statsReq("archive"));
        assertTrue("identical requests both reach the db", ro.gets == 2);
        assertTrue("no cache hits", stats.cacheHitCount() == 0);
    }

    // ------------------------------------------------------------ helpers

    private static PositionStatsRequest statsReq(String scope) {
        PositionStatsRequest r = new PositionStatsRequest();
        r.game = 1;
        r.moves = new int[]{180};
        r.scope = scope;
        return r;
    }

    private static GameSearchRequest searchReq(String scope) {
        GameSearchRequest r = new GameSearchRequest();
        r.game = 1;
        r.moves = new int[]{180};
        r.scope = scope;
        return r;
    }

    private static Object staticField(String name) throws Exception {
        Field f = WebDbApiServlet.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private interface H {
        Object invoke(Object proxy, Method m, Object[] args) throws Throwable;
    }

    private static Object proxy(Class<?> iface, final H h) {
        return Proxy.newProxyInstance(ReplicaRoutingTest.class.getClassLoader(),
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

    private static Object dflt(Class<?> t) {
        if (!t.isPrimitive() || t == void.class) return null;
        if (t == boolean.class) return Boolean.FALSE;
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == double.class) return 0d;
        if (t == float.class) return 0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        return (char) 0;
    }
}
