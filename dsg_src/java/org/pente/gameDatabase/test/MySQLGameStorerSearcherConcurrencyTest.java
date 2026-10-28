package org.pente.gameDatabase.test;

import junit.framework.TestCase;

import org.pente.database.test.SlowFakeDb;
import org.pente.game.GridStateFactory;
import org.pente.gameDatabase.MySQLGameStorerSearcher;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestFilterData;
import org.pente.gameDatabase.SimpleGameStorerSearchResponseData;

/**
 * DB-free tests that the legacy searcher overlaps its stats and matching-games
 * queries (each on its own connection) and that the matching task's FROM-clause
 * tweak cannot leak into the stats SQL. Run via
 * {@code ant test-one -Dtest=org.pente.gameDatabase.test.MySQLGameStorerSearcherConcurrencyTest}.
 */
public class MySQLGameStorerSearcherConcurrencyTest extends TestCase {

    private static final long DELAY_MS = 300;

    public MySQLGameStorerSearcherConcurrencyTest(String name) {
        super(name);
    }

    private SimpleGameStorerSearchRequestData noFilterRequest() {
        SimpleGameStorerSearchRequestData req = new SimpleGameStorerSearchRequestData();
        SimpleGameStorerSearchRequestFilterData f = new SimpleGameStorerSearchRequestFilterData();
        f.setGame(GridStateFactory.PENTE);
        f.setStartGameNum(0);
        f.setEndGameNum(10);
        f.setGetNextMoves(true);
        req.setGameStorerSearchRequestFilterData(f);
        return req;
    }

    public void testStatsAndMatchingOverlap() throws Exception {
        SlowFakeDb db = new SlowFakeDb(DELAY_MS, sql -> new Object[0][]);
        MySQLGameStorerSearcher searcher = new MySQLGameStorerSearcher(db, null, null);

        long t0 = System.nanoTime();
        searcher.search(noFilterRequest(), new SimpleGameStorerSearchResponseData());
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("one connection per query", 2, db.gets.get());
        assertEquals("every connection freed", 2, db.frees.get());
        assertEquals(2, db.sqls.size());
        assertTrue("took " + ms + " ms; serial would be >= " + (2 * DELAY_MS), ms < 450);
    }

    public void testGameTableJoinDoesNotLeakIntoStatsSql() throws Exception {
        SlowFakeDb db = new SlowFakeDb(DELAY_MS, sql -> new Object[0][]);
        MySQLGameStorerSearcher searcher = new MySQLGameStorerSearcher(db, null, null);

        searcher.search(noFilterRequest(), new SimpleGameStorerSearchResponseData());

        String stats = null, matching = null;
        for (String sql : db.sqls) {
            if (sql.startsWith("select m.next_move")) stats = sql;
            if (sql.startsWith("select m.gid")) matching = sql;
        }
        assertNotNull(stats);
        assertNotNull(matching);
        assertTrue(stats, stats.indexOf("pente_game") < 0);
        assertTrue(matching, matching.contains("pente_game"));
    }

    public void testNoNextMovesSkipsStatsQuery() throws Exception {
        SlowFakeDb db = new SlowFakeDb(DELAY_MS, sql -> new Object[0][]);
        MySQLGameStorerSearcher searcher = new MySQLGameStorerSearcher(db, null, null);
        SimpleGameStorerSearchRequestData req = noFilterRequest();
        ((SimpleGameStorerSearchRequestFilterData) req.getGameStorerSearchRequestFilterData())
                .setGetNextMoves(false);

        searcher.search(req, new SimpleGameStorerSearchResponseData());

        assertEquals(1, db.gets.get());
        assertEquals(1, db.sqls.size());
        assertTrue(db.sqls.get(0), db.sqls.get(0).startsWith("select m.gid"));
    }
}
