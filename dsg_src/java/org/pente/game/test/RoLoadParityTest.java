package org.pente.game.test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GameData;
import org.pente.game.GridStateFactory;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.game.MySQLPenteGameStorer;
import org.pente.game.PlayerData;

/**
 * DB-backed, read-only parity checks against the local archive: the round-trip
 * optimised loaders must return exactly what the original per-row loader does.
 * Needs MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE in the environment (host
 * defaults to 127.0.0.1:3316, override with WEBDB_TEST_HOST). Only SELECTs are
 * issued. Run via
 * {@code ant test-one -Dtest=org.pente.game.test.RoLoadParityTest}.
 */
public class RoLoadParityTest extends TestCase {

    private DBHandler db;
    private MySQLPenteGameStorer storer;

    public RoLoadParityTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        String user = System.getenv("MYSQL_USER");
        String pass = System.getenv("MYSQL_PASSWORD");
        String name = System.getenv("MYSQL_DATABASE");
        String host = System.getenv("WEBDB_TEST_HOST");
        if (host == null || host.length() == 0) {
            host = "127.0.0.1:3316";
        }
        if (user == null || pass == null || name == null) {
            fail("set MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE (source pente.org/.env)");
        }
        try {
            db = new MySQLDBHandler(user, pass, name, host);
        } catch (Throwable t) {
            throw new Exception("could not open local DB at " + host, t);
        }
        storer = new MySQLPenteGameStorer(db, new MySQLGameVenueStorer(db));
    }

    protected void tearDown() {
        if (db != null) {
            db.destroy();
        }
    }

    private List<Long> pick(String where, int limit) throws Exception {
        Connection con = db.getConnection();
        try {
            PreparedStatement st = con.prepareStatement(
                    "select gid from pente_game where " + where + " order by gid desc limit " + limit);
            ResultSet rs = st.executeQuery();
            List<Long> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getLong(1));
            }
            rs.close();
            st.close();
            return out;
        } finally {
            db.freeConnection(con);
        }
    }

    private static String player(PlayerData p, boolean withRating) {
        if (p == null) {
            return "null";
        }
        return p.getUserID() + "/" + p.getUserIDName() + (withRating ? "/" + p.getRating() : "");
    }

    /** Fields loadGames fills (a subset of what loadGame fills). */
    private static String common(GameData g, boolean withRatings) {
        return g.getGameID() + "|" + g.getSite() + "|" + g.getShortSite() + "|" + g.getSiteURL()
                + "|" + g.getEvent() + "|" + g.getRound() + "|" + g.getSection() + "|" + g.getDate().getTime()
                + "|" + g.getTimed() + "|" + g.getRated() + "|" + g.getInitialTime() + "|" + g.getIncrementalTime()
                + "|" + player(g.getPlayer1Data(), withRatings) + "|" + player(g.getPlayer2Data(), withRatings)
                + "|" + g.getWinner() + "|" + g.didPlayersSwap() + "|" + g.isPrivateGame() + "|" + g.getStatus()
                + "|" + g.getGame() + "|" + g.didSwap2Pass()
                + "|" + Arrays.toString(Arrays.copyOf(g.getMoves(), g.getNumMoves()));
    }

    private static String full(GameData g) {
        return common(g, true) + "|swaps=" + g.getRenjuSwaps() + "|offers=" + Arrays.toString(g.getRenjuOffers());
    }

    public void testLoadGameRoMatchesLoadGame() throws Exception {
        List<Long> gids = new ArrayList<>();
        gids.addAll(pick("game = " + GridStateFactory.PENTE + " and private = 'N'", 4));
        gids.addAll(pick("game = " + GridStateFactory.GO + " or game = " + GridStateFactory.GO9
                + " or game = " + GridStateFactory.GO13, 2));
        gids.addAll(pick("game = " + GridStateFactory.RENJU + " or game = " + GridStateFactory.SPEED_RENJU, 3));
        gids.addAll(pick("game = " + GridStateFactory.GOMOKU, 1));
        gids.addAll(pick("game = " + GridStateFactory.CONNECT6, 1));
        gids.addAll(pick("private = 'Y'", 1));
        assertTrue("no games found in the local archive", !gids.isEmpty());

        for (Long gid : gids) {
            GameData expected = storer.loadGame(gid, null);
            GameData actual = storer.loadGameRo(gid);
            assertNotNull("gid " + gid, actual);
            assertEquals("gid " + gid, full(expected), full(actual));
        }
    }

    public void testLoadGameRoMissingGidIsNull() throws Exception {
        assertNull(storer.loadGame(Long.MAX_VALUE, null));
        assertNull(storer.loadGameRo(Long.MAX_VALUE));
    }

    public void testLoadGamesMatchesLoadGame() throws Exception {
        // not Go: loadGames always drops the 361 end marker, loadGame keeps it for Go
        List<Long> gids = pick("game = " + GridStateFactory.PENTE + " and private = 'N'", 6);
        gids.addAll(pick("game = " + GridStateFactory.GOMOKU, 1));
        assertTrue(!gids.isEmpty());

        // loadGames shares one PlayerData per pid, so a player's rating there is one
        // of his games' ratings; compare ratings only for players seen once.
        Map<Long, Integer> seen = new HashMap<>();
        List<GameData> single = new ArrayList<>();
        for (Long gid : gids) {
            GameData g = storer.loadGame(gid, null);
            single.add(g);
            seen.merge(g.getPlayer1Data().getUserID(), 1, Integer::sum);
            seen.merge(g.getPlayer2Data().getUserID(), 1, Integer::sum);
        }
        boolean allUnique = true;
        for (int n : seen.values()) {
            allUnique &= n == 1;
        }

        Connection con = db.getConnection();
        List<GameData> batch;
        try {
            batch = storer.loadGames(con, gids);
        } finally {
            db.freeConnection(con);
        }
        assertEquals(gids.size(), batch.size());
        Map<Long, GameData> byGid = new HashMap<>();
        for (GameData g : batch) {
            byGid.put(g.getGameID(), g);
        }
        for (GameData g : single) {
            GameData b = byGid.get(g.getGameID());
            assertNotNull("gid " + g.getGameID(), b);
            assertEquals("gid " + g.getGameID(), common(g, allUnique), common(b, allUnique));
        }
    }
}
