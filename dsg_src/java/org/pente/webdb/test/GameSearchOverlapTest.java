package org.pente.webdb.test;

import junit.framework.TestCase;

import org.pente.database.test.SlowFakeDb;
import org.pente.game.GridStateFactory;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;

/**
 * DB-free test that the archive search runs its count(*) concurrently with the
 * page query (each on its own connection), and still reports both results. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.test.GameSearchOverlapTest}.
 */
public class GameSearchOverlapTest extends TestCase {

    private static final long DELAY_MS = 300;

    public GameSearchOverlapTest(String name) {
        super(name);
    }

    public void testCountOverlapsPageQuery() throws Exception {
        SlowFakeDb ro = new SlowFakeDb(DELAY_MS, sql ->
                sql.startsWith("select count(*)") ? new Object[][]{{5L}} : new Object[0][]);
        SlowFakeDb primary = new SlowFakeDb(0, sql -> new Object[0][]);
        GameSearchHandler h = new GameSearchHandler(primary, ro, null, null, null, null);

        GameSearchRequest req = new GameSearchRequest();
        req.game = GridStateFactory.PENTE;
        req.scope = "archive";
        req.limit = 10;

        long t0 = System.nanoTime();
        GameSearchResponse resp = h.search(req);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(5L, resp.total);
        assertNotNull(resp.games);
        assertEquals(0, resp.games.size());
        assertEquals("count and page on separate connections", 2, ro.gets.get());
        assertEquals(2, ro.frees.get());
        assertEquals(0, primary.gets.get());
        assertTrue("took " + ms + " ms; serial would be >= " + (2 * DELAY_MS), ms < 450);
    }
}
