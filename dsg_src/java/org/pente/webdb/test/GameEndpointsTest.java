package org.pente.webdb.test;

import java.sql.*;
import java.util.*;

import junit.framework.*;

import org.apache.log4j.BasicConfigurator;

import org.pente.database.*;
import org.pente.game.*;

import org.pente.webdb.GameLoadHandler;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.PlayersHandler;
import org.pente.webdb.VenuesHandler;
import org.pente.webdb.dto.GameDetailResponse;
import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.VenuesResponse;

/**
 * DB-backed tests for the Task 4 game endpoints ({@code games/search},
 * {@code games/{gid}}, {@code venues}, {@code players}). Runs read-only against
 * the live local archive — a {@link MySQLDBHandler} built exactly as
 * {@code PositionStatsHandlerTest} does, pointed at the local MariaDB container.
 *
 * <p>Connection config comes from the environment ({@code MYSQL_USER},
 * {@code MYSQL_PASSWORD}, {@code MYSQL_DATABASE}); host defaults to
 * {@code 127.0.0.1:3316}. Run via {@code ant test-one
 * -Dtest=org.pente.webdb.test.GameEndpointsTest} with those variables exported
 * (source {@code pente.org/.env}).
 *
 * <p>Assertions are structural — the archive's exact counts are live data — so
 * they check invariants (paging bounds, the center-first move list, a variant
 * with sites, prefix matching) rather than magic numbers.
 */
public class GameEndpointsTest extends TestCase {

    private static final int PENTE = GridStateFactory.PENTE; // 1
    private static final int CENTER = 180;

    private DBHandler dbHandler;
    private GameVenueStorer venueStorer;
    private GameStorer gameStorer;

    private GameSearchHandler searchHandler;
    private GameLoadHandler loadHandler;
    private VenuesHandler venuesHandler;
    private PlayersHandler playersHandler;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                GameEndpointsTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(GameEndpointsTest.class);
    }

    public GameEndpointsTest(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();

        String user = System.getenv("MYSQL_USER");
        String pass = System.getenv("MYSQL_PASSWORD");
        String db = System.getenv("MYSQL_DATABASE");
        String host = System.getenv("WEBDB_TEST_HOST");
        if (host == null || host.length() == 0) {
            host = "127.0.0.1:3316";
        }
        if (user == null || pass == null || db == null) {
            fail("set MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE in the "
                    + "environment (source pente.org/.env) before running this test");
        }

        try {
            dbHandler = new MySQLDBHandler(user, pass, db, host);
        } catch (Throwable t) {
            throw new Exception("could not open local DB at " + host, t);
        }

        venueStorer = new MySQLGameVenueStorer(dbHandler);
        gameStorer = new MySQLPenteGameStorer(dbHandler, venueStorer);

        searchHandler = new GameSearchHandler(dbHandler, gameStorer, venueStorer);
        loadHandler = new GameLoadHandler(gameStorer);
        venuesHandler = new VenuesHandler(venueStorer);
        playersHandler = new PlayersHandler(dbHandler);
    }

    private static GameSearchRequest searchReq(int game, int[] moves, int limit) {
        GameSearchRequest r = new GameSearchRequest();
        r.game = game;
        r.moves = moves;
        r.scope = "archive";
        r.offset = 0;
        r.limit = limit;
        return r;
    }

    /**
     * (a) A position-constrained search honors {@code limit} and reports a
     * {@code total} that is at least the returned page size. The Pente opening
     * ({@code [180]}) is present in the archive, so the page is non-empty.
     */
    public void testSearchPositionPagingAndTotal() throws Exception {
        GameSearchResponse resp =
                searchHandler.search(searchReq(PENTE, new int[]{CENTER}, 5));

        assertNotNull("search returned null", resp);
        assertNotNull("search returned null games list", resp.games);
        assertTrue("page must honor limit=5, got " + resp.games.size(),
                resp.games.size() <= 5);
        assertTrue("archive should hold Pente games from the opening position",
                resp.games.size() > 0);
        assertTrue("total (" + resp.total + ") must be >= page size ("
                + resp.games.size() + ")", resp.total >= resp.games.size());

        // Every header is the requested variant and reports a valid gid.
        for (GameHeader h : resp.games) {
            assertEquals("header.game must echo the requested variant",
                    PENTE, h.game);
            assertEquals("archive source", "archive", h.source);
            assertTrue("gid must be set", h.gid != 0);
            assertTrue("moveCount positive", h.moveCount > 0);
        }
    }

    /**
     * (b) Loading a gid returned by (a) reconstructs a move list that starts on
     * the center stone (Pente) and whose length equals the header's
     * {@code moveCount}.
     */
    public void testLoadGameStartsAtCenterAndMoveCountMatches() throws Exception {
        GameSearchResponse search =
                searchHandler.search(searchReq(PENTE, new int[]{CENTER}, 5));
        assertTrue("need at least one game from search to load",
                search.games.size() > 0);

        long gid = search.games.get(0).gid;
        GameDetailResponse detail = loadHandler.load(gid);

        assertNotNull("load of a known gid must not be null", detail);
        assertNotNull("detail.header must be set", detail.header);
        assertEquals("detail must be the loaded gid", gid, detail.header.gid);
        assertNotNull("detail.moves must be set", detail.moves);
        assertTrue("Pente move list must be non-empty", detail.moves.length > 0);
        assertEquals("Pente move list must start on the center stone",
                CENTER, detail.moves[0]);
        assertEquals("header.moveCount must equal the reconstructed move count",
                detail.moves.length, detail.header.moveCount);
    }

    /** An unknown gid loads as null (→ 404 at the servlet layer). */
    public void testLoadUnknownGidIsNull() throws Exception {
        assertNull("an impossible gid must load as null",
                loadHandler.load(-1L));
    }

    /**
     * (c) The venue tree contains variant 1 (Pente) with at least one site, and
     * every node carries the ids/names the wire contract promises.
     */
    public void testVenuesContainPenteWithSites() throws Exception {
        VenuesResponse resp = venuesHandler.venues();

        assertNotNull("venues returned null", resp);
        assertNotNull("venues.variants must be set", resp.variants);

        VenuesResponse.Variant pente = null;
        for (VenuesResponse.Variant v : resp.variants) {
            if (v.game == PENTE) {
                pente = v;
                break;
            }
        }
        assertNotNull("venue tree must contain variant 1 (Pente)", pente);
        assertNotNull("Pente variant must carry a name", pente.name);
        assertNotNull("Pente variant must carry a sites list", pente.sites);
        assertTrue("Pente must have at least one site", pente.sites.size() >= 1);

        // Structural spot check on the first site + its events.
        VenuesResponse.Site site = pente.sites.get(0);
        assertNotNull("site name must be set", site.name);
        assertNotNull("site events list must be set", site.events);
    }

    /**
     * (d) Player autocomplete returns at most 20 lowercase-prefix matches. The
     * prefix is derived from a real player name so the assertion holds against
     * whatever data the local archive carries.
     */
    public void testPlayersPrefixMatchesAndCap() throws Exception {
        String prefix = sampleTwoCharPrefix();

        List<String> names = playersHandler.players(prefix);

        assertNotNull("players returned null", names);
        assertTrue("at most 20 names, got " + names.size(), names.size() <= LIMIT);
        assertTrue("prefix '" + prefix + "' should match at least one player",
                names.size() >= 1);
        for (String n : names) {
            assertTrue("every result must start with the query prefix (lowercase): "
                            + n,
                    n.toLowerCase().startsWith(prefix));
        }
    }

    /** Queries shorter than two characters are rejected. */
    public void testPlayersShortQueryRejected() throws Exception {
        try {
            playersHandler.players("a");
            fail("a single-character query must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static final int LIMIT = 20;

    /**
     * Pull a real two-letter lowercase name prefix from the archive. Restricted
     * to two leading ASCII letters so the prefix cannot be whitespace-trimmed
     * below the two-character minimum the endpoint enforces.
     */
    private String sampleTwoCharPrefix() throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            PreparedStatement stmt = con.prepareStatement(
                    "select name_lower from player "
                            + "where name_lower rlike '^[a-z][a-z]' limit 1");
            try {
                ResultSet rs = stmt.executeQuery();
                try {
                    assertTrue("archive must have at least one named player",
                            rs.next());
                    return rs.getString(1).substring(0, 2);
                } finally {
                    rs.close();
                }
            } finally {
                stmt.close();
            }
        } finally {
            dbHandler.freeConnection(con);
        }
    }
}
