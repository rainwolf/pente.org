package org.pente.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helpers for pure-SELECT reads against the read-only (replica) pool.
 *
 * <p>The RO pool does not validate on borrow (that cost one network round trip per
 * borrow), so a connection killed while idle, for example when the SSH tunnel was
 * restarted, can be handed out once. {@link #withConnection} retries such a read
 * once on a freshly borrowed, explicitly validated connection. Only use it for
 * idempotent reads.
 *
 * <p>Also holds the shared executor for overlapping independent RO queries.
 */
public final class RoReads {

    /** A unit of work on one borrowed connection. */
    public interface SqlFn<T> {
        T apply(Connection con) throws Exception;
    }

    /** A unit of work needing no connection argument (it borrows its own). */
    public interface Work<T> {
        T call() throws Exception;
    }

    /** Seconds allowed for the {@code isValid} check of a re-borrowed connection. */
    static final int VALID_TIMEOUT_SECONDS = 5;
    /** Upper bound on invalid connections skipped while re-borrowing (pool maxIdle + 1). */
    static final int MAX_BORROW_ATTEMPTS = 31;

    /**
     * Blocking JDBC tasks; virtual threads so they never starve (or pin) the
     * common pool. Daemon by construction.
     */
    private static final ExecutorService EXEC =
            Executors.newVirtualThreadPerTaskExecutor();

    private RoReads() {
    }

    /**
     * Borrow a connection, run {@code fn}, and free the connection. If {@code fn}
     * fails with a connection-level error, discard that connection and run it once
     * more on a freshly borrowed connection that passed {@code isValid}.
     */
    public static <T> T withConnection(DBHandler db, SqlFn<T> fn) throws Exception {
        Connection con = db.getConnection();
        try {
            return fn.apply(con);
        } catch (Exception first) {
            if (!connectionBroken(first, con)) {
                throw first;
            }
            // fall through to the retry; free the broken connection first
            freeQuietly(db, con);
            con = null;
            Connection retry = borrowValid(db);
            try {
                return fn.apply(retry);
            } finally {
                freeQuietly(db, retry);
            }
        } finally {
            if (con != null) {
                db.freeConnection(con);
            }
        }
    }

    /** Run {@code work} on the shared RO executor. */
    public static <T> CompletableFuture<T> async(Work<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        EXEC.execute(() -> {
            try {
                f.complete(work.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** Wait for {@code f}; rethrow the task's own exception, not a wrapper. */
    public static <T> T join(CompletableFuture<T> f) throws Exception {
        try {
            return f.get();
        } catch (ExecutionException | CompletionException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) {
                throw (Exception) c;
            }
            if (c instanceof Error) {
                throw (Error) c;
            }
            throw e;
        }
    }

    static boolean connectionBroken(Throwable t, Connection con) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLNonTransientConnectionException) {
                return true;
            }
            if (c instanceof SQLException) {
                String state = ((SQLException) c).getSQLState();
                if (state != null && state.startsWith("08")) {
                    return true;
                }
            }
            if (c.getCause() == c) {
                break;
            }
        }
        try {
            return con.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    private static Connection borrowValid(DBHandler db) throws SQLException {
        List<Connection> bad = new ArrayList<>();
        try {
            for (int i = 0; i < MAX_BORROW_ATTEMPTS; i++) {
                Connection c = db.getConnection();
                boolean ok;
                try {
                    ok = c.isValid(VALID_TIMEOUT_SECONDS);
                } catch (SQLException e) {
                    ok = false;
                }
                if (ok) {
                    return c;
                }
                // hold it until a good one is found so the pool cannot hand it back
                bad.add(c);
            }
            throw new SQLException("no valid read-only connection available", "08001");
        } finally {
            for (Connection c : bad) {
                freeQuietly(db, c);
            }
        }
    }

    private static void freeQuietly(DBHandler db, Connection c) {
        try {
            db.freeConnection(c);
        } catch (SQLException e) {
            // already broken; nothing more to do
        }
    }
}
