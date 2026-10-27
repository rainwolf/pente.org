package org.pente.webdb.test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.*;

import org.pente.game.*;
import org.pente.gameDatabase.*;
import org.pente.webdb.CollectionHandler;
import org.pente.webdb.GameLoadHandler;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.dto.*;

/** Tombstone games must never reach the webdb or legacy game-database paths. No DB needed. */
public class TombstoneWebDbExclusionTest extends TestCase {

    public TombstoneWebDbExclusionTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(TombstoneWebDbExclusionTest.class);
    }

    public void testPositionStatsEmptyForTombstone() throws Exception {
        PositionStatsRequest req = new PositionStatsRequest();
        req.game = 33;
        req.moves = new int[]{180, 200, 361 + 5};
        PositionStatsResponse r = new PositionStatsHandler(null, null).computeArchive(req);
        assertEquals(0L, r.totalGames);
        assertTrue(r.nextMoves.isEmpty());
    }

    public void testGameSearchRejectsTombstone() throws Exception {
        for (int g : new int[]{33, 34, 83}) {
            GameSearchRequest req = new GameSearchRequest();
            req.game = g;
            req.moves = new int[]{180};
            try {
                new GameSearchHandler(null, null, null).search(req, -1L);
                fail("game " + g);
            } catch (IllegalArgumentException e) {
                assertEquals("variant not supported", e.getMessage());
            }
            try {
                new GameSearchHandler(null, null, null).search(req);
                fail("archive-only search, game " + g);
            } catch (IllegalArgumentException e) {
                assertEquals("variant not supported", e.getMessage());
            }
        }
    }

    public void testGameLoadRejectsTombstone() throws Exception {
        final GameData gd = new DefaultGameData();
        gd.setGame("Tombstone Pente");
        gd.addMove(180);
        GameStorer stub = new GameStorer() {
            public boolean gameAlreadyStored(long id) { return true; }
            public boolean gameAlreadyStored(GameData d) { return true; }
            public void storeGame(GameData d) {}
            public GameData loadGame(long id, GameData d) { return gd; }
            public void destroy() {}
        };
        try {
            new GameLoadHandler(stub).load(42L);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("variant not supported", e.getMessage());
        }
    }

    public void testImportRejectsTombstonePerItem() throws Exception {
        WebDbGameData g = new WebDbGameData();
        g.game = 33;
        g.moves = new int[]{180, 200};
        ImportRequest req = new ImportRequest();
        req.games = new ArrayList<WebDbGameData>(Arrays.asList(g));
        ImportResponse r = new CollectionHandler(null, null).doImport(1L, req);
        assertEquals(0, r.imported);
        assertEquals(1, r.errors.size());
        assertEquals("variant not supported for import", r.errors.get(0).message);
    }

    public void testLegacyGameDatabaseRejectsTombstone() {
        SimpleGameStorerSearchRequestFilterFormat f = new SimpleGameStorerSearchRequestFilterFormat();
        try {
            f.parse(new SimpleGameStorerSearchRequestFilterData(), new StringBuffer("game=Tombstone+Pente"));
            fail();
        } catch (ParseException expected) {
        }
    }

    public void testLegacyGameDatabaseStillAcceptsPente() throws Exception {
        new SimpleGameStorerSearchRequestFilterFormat().parse(
            new SimpleGameStorerSearchRequestFilterData(), new StringBuffer("game=Pente"));
    }
}
