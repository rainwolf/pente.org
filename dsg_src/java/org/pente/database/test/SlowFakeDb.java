package org.pente.database.test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.pente.database.DBHandler;

/**
 * Test double for a DBHandler whose every query sleeps {@code delayMs} (a stand-in
 * for a cross-continent round trip) and returns canned rows chosen by SQL text.
 * Records the SQL it saw and how many connections were borrowed.
 */
public class SlowFakeDb implements DBHandler {

    public final AtomicInteger gets = new AtomicInteger();
    public final AtomicInteger frees = new AtomicInteger();
    public final List<String> sqls = Collections.synchronizedList(new ArrayList<String>());
    private final long delayMs;
    private final Function<String, Object[][]> rows;

    public SlowFakeDb(long delayMs, Function<String, Object[][]> rows) {
        this.delayMs = delayMs;
        this.rows = rows;
    }

    public Connection getConnection() {
        gets.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "prepareStatement":
                            return statement((String) a[0]);
                        case "isClosed":
                            return Boolean.FALSE;
                        case "isValid":
                            return Boolean.TRUE;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == a[0];
                        case "toString":
                            return "fakeConnection";
                        default:
                            return dflt(m.getReturnType());
                    }
                });
    }

    private PreparedStatement statement(final String sql) {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "executeQuery":
                            sqls.add(sql);
                            Thread.sleep(delayMs);
                            return resultSet(rows.apply(sql));
                        case "toString":
                            return sql;
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == a[0];
                        default:
                            return dflt(m.getReturnType());
                    }
                });
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
                        case "getString":
                            return (String) data[pos[0]][(Integer) a[0] - 1];
                        case "hashCode":
                            return System.identityHashCode(p);
                        case "equals":
                            return p == a[0];
                        default:
                            return dflt(m.getReturnType());
                    }
                });
    }

    public void freeConnection(Connection con) {
        frees.incrementAndGet();
    }

    public void destroy() {
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
