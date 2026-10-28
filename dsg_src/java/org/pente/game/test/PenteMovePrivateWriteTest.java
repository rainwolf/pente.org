package org.pente.game.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import junit.framework.*;

import org.pente.database.DBHandler;
import org.pente.game.*;

/**
 * DB-free tests that {@link MySQLPenteGameStorer} writes pente_move.private on
 * every move insert. JDBC is faked with {@link Proxy}; the private
 * storeGameNewThread is invoked reflectively (it is the synchronous core, so
 * no thread timing is involved).
 */
public class PenteMovePrivateWriteTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{PenteMovePrivateWriteTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PenteMovePrivateWriteTest.class);
    }

    public PenteMovePrivateWriteTest(String name) {
        super(name);
    }

    /** One executed statement: SQL plus a snapshot of the bound parameters. */
    private static class Exec {
        final String sql;
        final Map<Integer, Object> params;
        Exec(String sql, Map<Integer, Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private List<Exec> executed;
    private String storedPrivate; // value returned by the game-status query

    protected void setUp() {
        executed = new ArrayList<Exec>();
        storedPrivate = "N";
    }

    private ResultSet fakeResult(final String sql) {
        final boolean[] first = {true};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("next")) {
                    boolean r = first[0];
                    first[0] = false;
                    return r;
                }
                if (n.equals("getTimestamp")) return new Timestamp(1000L);
                if (n.equals("getString")) return storedPrivate;
                if (n.equals("getInt")) {
                    // max(move_num)+1 -> 1 move stored; status query winner -> UNKNOWN
                    return sql.contains("max(move_num)") ? 1 : 0;
                }
                return defaultFor(m.getReturnType());
            }
        });
    }

    private PreparedStatement fakePs(final String sql) {
        final Map<Integer, Object> params = new HashMap<Integer, Object>();
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                String n = m.getName();
                if (n.startsWith("set") && a != null && a.length >= 2 && a[0] instanceof Integer) {
                    params.put((Integer) a[0], a[1]);
                    return null;
                }
                if (n.equals("executeUpdate")) {
                    executed.add(new Exec(sql, new HashMap<Integer, Object>(params)));
                    return 1;
                }
                if (n.equals("executeQuery")) return fakeResult(sql);
                return defaultFor(m.getReturnType());
            }
        });
    }

    private Connection fakeCon() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                if (m.getName().equals("prepareStatement")) return fakePs((String) a[0]);
                return defaultFor(m.getReturnType());
            }
        });
    }

    private static Object defaultFor(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        return null;
    }

    private MySQLPenteGameStorer storer() throws Exception {
        DBHandler db = (DBHandler) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{DBHandler.class}, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                return defaultFor(m.getReturnType());
            }
        });
        GameVenueStorer venue = (GameVenueStorer) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{GameVenueStorer.class},
                new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                if (m.getName().equals("getGameSiteData")) {
                    SimpleGameSiteData s = new SimpleGameSiteData();
                    s.setSiteID(1);
                    s.setName("pente.org");
                    return s;
                }
                if (m.getName().equals("getGameEventData")) {
                    SimpleGameEventData e = new SimpleGameEventData();
                    e.setEventID(1);
                    e.setName("ev");
                    return e;
                }
                return defaultFor(m.getReturnType());
            }
        });
        return new MySQLPenteGameStorer(db, venue);
    }

    private GameData game(boolean priv) {
        GameData d = new DefaultGameData();
        d.setGame("Pente");
        d.setGameID(42L);
        d.setSite("pente.org");
        d.setEvent("ev");
        d.setDate(new java.util.Date(1000L));
        d.setPrivateGame(priv);
        d.addMove(180);
        d.addMove(200);
        d.addMove(220);
        return d;
    }

    private void store(GameData d, boolean newGid, boolean alreadyStored) throws Exception {
        Method m = MySQLPenteGameStorer.class.getDeclaredMethod("storeGameNewThread",
                Connection.class, GameData.class, boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(storer(), fakeCon(), d, newGid, alreadyStored);
    }

    /** Inserts into pente_move, in execution order. */
    private List<Exec> moveInserts() {
        List<Exec> l = new ArrayList<Exec>();
        for (Exec e : executed) {
            if (e.sql.startsWith("insert into pente_move")) l.add(e);
        }
        return l;
    }

    /** Bound value of the named column in a pente_move insert (null if the column is absent). */
    private static Object bound(Exec e, String col) {
        String cols = e.sql.substring(e.sql.indexOf('(') + 1, e.sql.indexOf(')'));
        String[] names = cols.split(",");
        for (int i = 0; i < names.length; i++) {
            if (names[i].trim().equals(col)) return e.params.get(i + 1);
        }
        return null;
    }

    private void assertAllRows(int expectedRows, String expected) {
        List<Exec> rows = moveInserts();
        assertEquals(expectedRows, rows.size());
        for (Exec e : rows) {
            assertEquals(expected, bound(e, "private"));
        }
    }

    public void testNewPrivateGameWritesY() throws Exception {
        store(game(true), true, false);
        assertAllRows(3, "Y");
    }

    public void testNewPublicGameWritesN() throws Exception {
        store(game(false), true, false);
        assertAllRows(3, "N");
    }

    public void testAppendToStoredGameUsesStoredFlag() throws Exception {
        storedPrivate = "Y";
        store(game(false), false, true); // caller says public, DB says private
        assertAllRows(2, "Y");           // 1 move already stored, 2 appended
    }

    public void testWinnerUpdateKeepsPlayDate() throws Exception {
        storedPrivate = "N";
        store(game(false), false, true);
        int seen = 0;
        for (Exec e : executed) {
            if (e.sql.startsWith("update pente_move set winner")) {
                seen++;
                assertTrue(e.sql, e.sql.contains("play_date = play_date"));
            }
        }
        assertEquals(1, seen);
    }
}
