package org.pente.webdb.test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;

import junit.framework.TestCase;

import org.pente.database.test.SlowFakeDb;
import org.pente.game.DefaultGameData;
import org.pente.game.DefaultPlayerData;
import org.pente.game.GameData;
import org.pente.game.GridStateFactory;
import org.pente.game.MySQLPenteGameStorer;
import org.pente.gameServer.core.MySQLDSGPlayerStorer;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.PlayerBadges;
import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;

/**
 * DB-free tests for {@link PlayerBadges}: the batched replica lookup that fills
 * {@code nameColor} and {@code crown} on game header players, plus the
 * {@link MySQLDSGPlayerStorer#activeSubscriberBits} helper it shares with the
 * player storer. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.test.PlayerBadgesTest}.
 */
public class PlayerBadgesTest extends TestCase {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;
    private static final int MONTH1 = MySQLDSGPlayerStorer.ONEMONTH | 1;
    private static final int YEAR1 = MySQLDSGPlayerStorer.ONEYEAR | 1;
    private static final int BLACK = (255 << 24) + 1;

    private static final String SQL_PLAYER = "select pid, name_color from dsg_player where pid in (";
    private static final String SQL_CROWN = "select pid, tourney_winner from dsg_player_game where pid in (";
    private static final String SQL_SUBS = "select pid, level, paymentdate from dsg_subscribers where pid in (";

    public PlayerBadgesTest(String name) {
        super(name);
    }

    // ---- fake connection -------------------------------------------------

    /** Connection fake: records SQL and setLong binds, returns rows by SQL prefix. */
    static final class FakeCon {
        final List<String> prepared = Collections.synchronizedList(new ArrayList<String>());
        final Map<String, List<Long>> binds = new HashMap<String, List<Long>>();
        final Map<String, Object[][]> rowsByPrefix = new HashMap<String, Object[][]>();
        String throwOnPrefix;
        Exception toThrow;

        FakeCon rows(String prefix, Object[][] rows) {
            rowsByPrefix.put(prefix, rows);
            return this;
        }

        FakeCon failOn(String prefix, Exception e) {
            throwOnPrefix = prefix;
            toThrow = e;
            return this;
        }

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "prepareStatement":
                                prepared.add((String) a[0]);
                                return statement((String) a[0]);
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == a[0];
                            default:
                                return dflt(m.getReturnType());
                        }
                    });
        }

        private PreparedStatement statement(final String sql) {
            final List<Long> b = new ArrayList<Long>();
            binds.put(sql, b);
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "setLong":
                                b.add((Long) a[1]);
                                return null;
                            case "executeQuery":
                                if (throwOnPrefix != null && sql.startsWith(throwOnPrefix)) {
                                    throw toThrow;
                                }
                                return resultSet(rowsFor(sql));
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == a[0];
                            default:
                                return dflt(m.getReturnType());
                        }
                    });
        }

        private Object[][] rowsFor(String sql) {
            for (Map.Entry<String, Object[][]> e : rowsByPrefix.entrySet()) {
                if (sql.startsWith(e.getKey())) {
                    return e.getValue();
                }
            }
            return new Object[0][];
        }

        private ResultSet resultSet(final Object[][] data) {
            final int[] pos = {-1};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "next":
                                return ++pos[0] < data.length;
                            case "getLong":
                                return ((Number) data[pos[0]][(Integer) a[0] - 1]).longValue();
                            case "getInt":
                                return ((Number) data[pos[0]][(Integer) a[0] - 1]).intValue();
                            case "getDate":
                                return (java.sql.Date) data[pos[0]][(Integer) a[0] - 1];
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == a[0];
                            default:
                                return dflt(m.getReturnType());
                        }
                    });
        }
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

    // ---- helpers ---------------------------------------------------------

    private static java.sql.Date daysAgo(int days) {
        return new java.sql.Date(NOW - days * DAY);
    }

    private static GameHeader.Player player(long pid) {
        GameHeader.Player p = new GameHeader.Player("p" + pid, 1600);
        p.pid = pid;
        return p;
    }

    private static GameHeader header(long pid1, Long pid2) {
        GameHeader h = new GameHeader();
        h.player1 = player(pid1);
        h.player2 = (pid2 == null) ? null : player(pid2);
        return h;
    }

    private static GameHeader.Player applyOne(FakeCon fc) {
        GameHeader h = header(7, null);
        PlayerBadges.apply(fc.connection(), Collections.singletonList(h), NOW);
        return h.player1;
    }

    // ---- cases -----------------------------------------------------------

    public void testSubscriberColorAndGoldCrown() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0xFF0000}})
                .rows(SQL_CROWN, new Object[][]{{7L, 1}})
                .rows(SQL_SUBS, new Object[][]{{7L, MONTH1, daysAgo(10)}});
        GameHeader.Player p = applyOne(fc);
        assertEquals(new java.awt.Color(0xFF0000).getRGB(), p.nameColor);
        assertEquals(1, p.crown);
    }

    public void testBlackEncodedNonZero() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0xFF000000}})
                .rows(SQL_SUBS, new Object[][]{{7L, MONTH1, daysAgo(10)}});
        assertEquals(BLACK, applyOne(fc).nameColor);
    }

    public void testSubscriberWithoutColorIsBlack() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0}})
                .rows(SQL_SUBS, new Object[][]{{7L, YEAR1, daysAgo(100)}});
        assertEquals(BLACK, applyOne(fc).nameColor);
    }

    public void testNonSubscriberColorHidden() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0xFF0000}})
                .rows(SQL_CROWN, new Object[][]{{7L, 2}});
        GameHeader.Player p = applyOne(fc);
        assertEquals(0, p.nameColor);
        assertEquals(2, p.crown);
    }

    public void testExpiredSubscriptionHidden() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0xFF0000}})
                .rows(SQL_SUBS, new Object[][]{
                        {7L, MONTH1, daysAgo(40)},
                        {7L, YEAR1, daysAgo(400)}});
        assertEquals(0, applyOne(fc).nameColor);
    }

    public void testKothCrownCode() {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0}})
                .rows(SQL_CROWN, new Object[][]{{7L, 4}, {7L, 4}});
        assertEquals(5, applyOne(fc).crown);

        FakeCon fc2 = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0}})
                .rows(SQL_CROWN, new Object[][]{{7L, 4}, {7L, 2}});
        assertEquals(2, applyOne(fc2).crown);
    }

    public void testBatchSqlAndBinds() {
        FakeCon fc = new FakeCon();
        List<GameHeader> headers = Arrays.asList(
                header(7, null), header(7, 3L), header(7, null));
        PlayerBadges.apply(fc.connection(), headers, NOW);

        assertEquals(Arrays.asList(
                "select pid, name_color from dsg_player where pid in (?,?)",
                "select pid, tourney_winner from dsg_player_game where pid in (?,?) and tourney_winner > 0",
                "select pid, level, paymentdate from dsg_subscribers where pid in (?,?)"),
                fc.prepared);
        for (String sql : fc.prepared) {
            assertEquals(sql, Arrays.asList(3L, 7L), fc.binds.get(sql));
        }
    }

    public void testEmptyPageNoSql() {
        FakeCon fc = new FakeCon();
        PlayerBadges.apply(fc.connection(), new ArrayList<GameHeader>(), NOW);
        assertEquals(0, fc.prepared.size());

        GameHeader h = header(0, null);
        PlayerBadges.apply(fc.connection(), Collections.singletonList(h), NOW);
        assertEquals(0, fc.prepared.size());
        assertEquals(0, h.player1.nameColor);
        assertEquals(0, h.player1.crown);

        GameHeader none = new GameHeader();
        PlayerBadges.apply(fc.connection(), Collections.singletonList(none), NOW);
        assertEquals(0, fc.prepared.size());
    }

    public void testUnknownPidStaysZero() {
        FakeCon fc = new FakeCon()
                .rows(SQL_CROWN, new Object[][]{{7L, 1}})
                .rows(SQL_SUBS, new Object[][]{{7L, MONTH1, daysAgo(10)}});
        GameHeader.Player p = applyOne(fc);
        assertEquals(0, p.nameColor);
        assertEquals(0, p.crown);
    }

    public void testSqlFailureDegradesToZero() {
        assertFailureDegrades(new SQLException("boom"));
    }

    public void testRuntimeFailureDegradesToZero() {
        assertFailureDegrades(new IllegalStateException("boom"));
    }

    private void assertFailureDegrades(Exception e) {
        FakeCon fc = new FakeCon()
                .rows(SQL_PLAYER, new Object[][]{{7L, 0xFF0000}, {3L, 0x00FF00}})
                .rows(SQL_CROWN, new Object[][]{{7L, 1}, {3L, 2}})
                .failOn(SQL_SUBS, e);
        GameHeader h = header(7, 3L);
        PlayerBadges.apply(fc.connection(), Collections.singletonList(h), NOW);
        assertEquals(3, fc.prepared.size());
        assertEquals(0, h.player1.nameColor);
        assertEquals(0, h.player1.crown);
        assertEquals(0, h.player2.nameColor);
        assertEquals(0, h.player2.crown);
    }

    public void testActiveSubscriberBits() {
        Calendar lastMonth = Calendar.getInstance();
        lastMonth.setTimeInMillis(NOW);
        lastMonth.add(Calendar.DATE, -31);
        Calendar lastYear = Calendar.getInstance();
        lastYear.setTimeInMillis(NOW);
        lastYear.add(Calendar.YEAR, -1);

        assertEquals(MONTH1, MySQLDSGPlayerStorer.activeSubscriberBits(MONTH1, daysAgo(10), lastMonth, lastYear));
        assertEquals(0, MySQLDSGPlayerStorer.activeSubscriberBits(MONTH1, daysAgo(40), lastMonth, lastYear));
        assertEquals(YEAR1, MySQLDSGPlayerStorer.activeSubscriberBits(YEAR1, daysAgo(300), lastMonth, lastYear));
        assertEquals(0, MySQLDSGPlayerStorer.activeSubscriberBits(YEAR1, daysAgo(400), lastMonth, lastYear));
        assertEquals(0, MySQLDSGPlayerStorer.activeSubscriberBits(1, daysAgo(1), lastMonth, lastYear));
        assertEquals(0, MySQLDSGPlayerStorer.activeSubscriberBits(MONTH1, null, lastMonth, lastYear));
        int both = MySQLDSGPlayerStorer.ONEMONTH | MySQLDSGPlayerStorer.ONEYEAR | 1;
        assertEquals(both, MySQLDSGPlayerStorer.activeSubscriberBits(both, daysAgo(10), lastMonth, lastYear));
        assertEquals("both bits use the month window", 0,
                MySQLDSGPlayerStorer.activeSubscriberBits(both, daysAgo(40), lastMonth, lastYear));
    }

    public void testHeaderJsonHasBadgesNotPid() {
        GameHeader h = header(7, 3L);
        String json = new GsonBuilder().serializeNulls().create().toJson(h);
        assertTrue(json, json.contains("\"nameColor\":"));
        assertTrue(json, json.contains("\"crown\":"));
        assertTrue(json, !json.contains("\"pid\""));
    }

    public void testArchiveSearchAppliesBadgesOnArchiveDb() throws Exception {
        SlowFakeDb ro = new SlowFakeDb(0, sql -> {
            if (sql.startsWith("select count(*)")) return new Object[][]{{5L}};
            if (sql.startsWith(SQL_PLAYER)) return new Object[][]{{7L, 0}};
            if (sql.startsWith(SQL_CROWN)) return new Object[][]{{7L, 1}};
            if (sql.startsWith(SQL_SUBS)) return new Object[0][];
            return new Object[][]{{100L}};
        });
        SlowFakeDb primary = new SlowFakeDb(0, sql -> new Object[0][]);
        MySQLPenteGameStorer gameStorer = new MySQLPenteGameStorer(null, null) {
            @Override
            public List<GameData> loadGames(Connection con, List<Long> gameIDs) {
                DefaultGameData gd = new DefaultGameData();
                gd.setGameID(100L);
                DefaultPlayerData p1 = new DefaultPlayerData();
                p1.setUserID(7L);
                p1.setUserIDName("alice");
                gd.setPlayer1Data(p1);
                List<GameData> out = new ArrayList<GameData>();
                out.add(gd);
                return out;
            }
        };
        GameSearchHandler h = new GameSearchHandler(primary, ro, gameStorer, null, null, null);

        GameSearchRequest req = new GameSearchRequest();
        req.game = GridStateFactory.PENTE;
        req.scope = "archive";
        req.limit = 10;
        GameSearchResponse resp = h.search(req);

        assertEquals(1, resp.games.size());
        assertEquals(1, resp.games.get(0).player1.crown);
        assertEquals(0, resp.games.get(0).player1.nameColor);
        assertEquals("no extra borrow for badges", 2, ro.gets.get());
        assertEquals(0, primary.gets.get());
    }
}
