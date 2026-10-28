package org.pente.webdb.test;

import junit.framework.*;

import org.pente.webdb.GameSearchHandler;

/**
 * DB-free: archive pages must order by play_date desc THEN gid desc, so offset paging is stable
 * across games sharing a play_date. Run via
 * {@code junit.textui.TestRunner org.pente.webdb.test.GameSearchPagingOrderTest}.
 */
public class GameSearchPagingOrderTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{GameSearchPagingOrderTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(GameSearchPagingOrderTest.class);
    }

    public GameSearchPagingOrderTest(String name) {
        super(name);
    }

    public void testGameShapeOrdersByDateThenGid() {
        String sql = GameSearchHandler.pagedGidSql(
                "g.gid", "from pente_game g", " where g.game = ?", "g.play_date");
        assertTrue(sql, sql.contains("order by g.play_date desc, g.gid desc limit ? offset ?"));
    }

    public void testPositionShapeOrdersByDateThenGid() {
        String sql = GameSearchHandler.pagedGidSql(
                "m.gid", "from pente_move m", " where m.hash_key = ? and m.private = 'N'", "m.play_date");
        assertTrue(sql, sql.contains("order by m.play_date desc, m.gid desc limit ? offset ?"));
    }
}
