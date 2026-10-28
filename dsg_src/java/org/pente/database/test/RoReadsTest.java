package org.pente.database.test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import junit.framework.TestCase;

import org.pente.database.DBHandler;
import org.pente.database.RoReads;

/** DB-free tests for the retry-once helper used on the read-only pool. */
public class RoReadsTest extends TestCase {

    private static final class FakeDb implements DBHandler {
        final List<Connection> handedOut = new ArrayList<>();
        final List<Connection> freed = new ArrayList<>();
        final boolean[] valid;
        final boolean[] closed;

        FakeDb(boolean[] valid, boolean[] closed) {
            this.valid = valid;
            this.closed = closed;
        }

        public Connection getConnection() {
            final int idx = handedOut.size();
            Connection c = (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (p, m, a) -> {
                        switch (m.getName()) {
                            case "isValid":
                                return idx < valid.length ? valid[idx] : true;
                            case "isClosed":
                                return idx < closed.length && closed[idx];
                            case "hashCode":
                                return System.identityHashCode(p);
                            case "equals":
                                return p == a[0];
                            case "toString":
                                return "fakeCon" + idx;
                            default:
                                return null;
                        }
                    });
            handedOut.add(c);
            return c;
        }

        public void freeConnection(Connection con) {
            freed.add(con);
        }

        public void destroy() {
        }
    }

    public RoReadsTest(String name) {
        super(name);
    }

    public void testRetriesOnceOnConnectionError() throws Exception {
        FakeDb db = new FakeDb(new boolean[0], new boolean[0]);
        final int[] calls = {0};
        String r = RoReads.withConnection(db, con -> {
            if (calls[0]++ == 0) {
                throw new SQLException("x", "08S01");
            }
            return "ok";
        });
        assertEquals("ok", r);
        assertEquals(2, calls[0]);
        assertEquals(2, db.handedOut.size());
        assertEquals(2, db.freed.size());
        assertSame(db.handedOut.get(0), db.freed.get(0));
        assertSame(db.handedOut.get(1), db.freed.get(1));
    }

    public void testRetriesWhenConnectionClosedAfterFailure() throws Exception {
        FakeDb db = new FakeDb(new boolean[0], new boolean[]{true});
        final int[] calls = {0};
        String r = RoReads.withConnection(db, con -> {
            if (calls[0]++ == 0) {
                throw new SQLException("boom", "HY000");
            }
            return "ok";
        });
        assertEquals("ok", r);
        assertEquals(2, calls[0]);
    }

    public void testRetryBorrowSkipsInvalidConnections() throws Exception {
        // con0 fails the first run; con1 is invalid; con2 is valid and is used
        FakeDb db = new FakeDb(new boolean[]{true, false, true}, new boolean[0]);
        final List<Connection> used = new ArrayList<>();
        String r = RoReads.withConnection(db, con -> {
            used.add(con);
            if (used.size() == 1) {
                throw new SQLException("x", "08006");
            }
            return "ok";
        });
        assertEquals("ok", r);
        assertEquals(3, db.handedOut.size());
        assertSame(db.handedOut.get(2), used.get(1));
        assertEquals(3, db.freed.size());
    }

    public void testNonConnectionErrorIsNotRetried() throws Exception {
        FakeDb db = new FakeDb(new boolean[0], new boolean[0]);
        final int[] calls = {0};
        try {
            RoReads.withConnection(db, con -> {
                calls[0]++;
                throw new SQLException("syntax", "42000");
            });
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("42000", e.getSQLState());
        }
        assertEquals(1, calls[0]);
        assertEquals(1, db.handedOut.size());
        assertEquals(1, db.freed.size());
    }

    public void testSecondFailureSurfacesUnchanged() throws Exception {
        FakeDb db = new FakeDb(new boolean[0], new boolean[0]);
        try {
            RoReads.withConnection(db, con -> {
                throw new SQLException("still down", "08S01");
            });
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("still down", e.getMessage());
        }
        assertEquals(2, db.handedOut.size());
        assertEquals(2, db.freed.size());
    }

    public void testAsyncJoinUnwrapsCause() throws Exception {
        CompletableFuture<String> f = RoReads.async(() -> {
            throw new SQLException("bad", "42000");
        });
        try {
            RoReads.join(f);
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("bad", e.getMessage());
        }
        assertEquals("v", RoReads.join(RoReads.async(() -> "v")));
    }
}
