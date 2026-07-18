package org.pente.webdb.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import junit.framework.*;

import org.apache.log4j.BasicConfigurator;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.*;

import org.pente.webdb.AnalysesHandler;
import org.pente.webdb.CollectionHandler;
import org.pente.webdb.GameSearchHandler;
import org.pente.webdb.MySQLWebDbStorer;
import org.pente.webdb.PositionStatsHandler;
import org.pente.webdb.WebDbHttpError;
import org.pente.webdb.dto.AnalysisDtos;
import org.pente.webdb.dto.GameDetailResponse;
import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.ImportRequest;
import org.pente.webdb.dto.ImportResponse;
import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.PositionStatsResponse;
import org.pente.webdb.dto.WebDbGameData;

/**
 * DB-backed tests for the Task 6 authenticated endpoints — personal-collection
 * import/list/get/delete, analyses CRUD, and the {@code scope="mine"}/{@code
 * "both"} merge on position-stats and games/search. Built like the other webdb
 * tests: a {@link MySQLDBHandler} on env {@code MYSQL_USER}/{@code MYSQL_PASSWORD}/
 * {@code MYSQL_DATABASE}, host default {@code 127.0.0.1:3316}. Run via
 * {@code ant test-one -Dtest=org.pente.webdb.test.AuthEndpointsTest}.
 *
 * <p>Every row written is scoped to the sentinel pid {@code 999999999} (a second
 * sentinel {@code 999999998} exercises cross-owner denial); {@link #tearDown}
 * purges both from all three {@code webdb_*} tables and asserts zero residue, so
 * the shared DB is never left dirty. The archive tables are read-only.
 *
 * <p>Anonymous ({@code 401}) requests are driven through the real {@code handle}
 * methods with lightweight {@link Proxy}-based servlet request/response fakes
 * (a {@code null} {@code "name"} attribute is an anonymous session); auth is
 * resolved by {@code WebDbAuth}, which short-circuits before touching the
 * player storer, so the fakes never need one.
 */
public class AuthEndpointsTest extends TestCase {

    private static final int PENTE = GridStateFactory.PENTE; // 1
    private static final int CENTER = 180;
    private static final long PID = 999999999L;       // sentinel owner
    private static final long OTHER_PID = 999999998L; // cross-owner probe

    private DBHandler dbHandler;
    private MySQLWebDbStorer storer;
    private CollectionHandler collectionHandler;
    private AnalysesHandler analysesHandler;
    private PositionStatsHandler statsHandler;
    private GameSearchHandler searchHandler;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                AuthEndpointsTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(AuthEndpointsTest.class);
    }

    public AuthEndpointsTest(String name) {
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

        GameVenueStorer venueStorer = new MySQLGameVenueStorer(dbHandler);
        GameStorer gameStorer = new MySQLPenteGameStorer(dbHandler, venueStorer);
        storer = new MySQLWebDbStorer(dbHandler);

        // playerStorer left null: auth tests use anonymous (name==null) requests,
        // where WebDbAuth short-circuits before the player storer is consulted.
        collectionHandler = new CollectionHandler(storer, null);
        analysesHandler = new AnalysesHandler(storer, null);
        statsHandler = new PositionStatsHandler(dbHandler, venueStorer, storer, null);
        searchHandler = new GameSearchHandler(
                dbHandler, gameStorer, venueStorer, storer, null);

        purgeSentinel();
    }

    protected void tearDown() throws Exception {
        try {
            purgeSentinel();
            assertEquals("webdb_move residue", 0, countSentinel("webdb_move"));
            assertEquals("webdb_game residue", 0, countSentinel("webdb_game"));
            assertEquals("webdb_analysis residue", 0, countSentinel("webdb_analysis"));
        } finally {
            if (dbHandler != null) {
                dbHandler.destroy();
            }
        }
    }

    // ==================================================================
    // import
    // ==================================================================

    /** Two legal games are stored and counted; no duplicates or errors. */
    public void testImportStoresValidGames() throws Exception {
        ImportResponse resp = collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        assertEquals("both stored", 2, resp.imported);
        assertTrue("no duplicates", resp.duplicates.isEmpty());
        assertTrue("no errors", resp.errors.isEmpty());
        assertEquals("collection now holds two games",
                2, storer.countGames(PID, PENTE));
    }

    /** An illegal move (a repeated, occupied cell) lands in errors by index. */
    public void testImportIllegalMoveReportedByIndex() throws Exception {
        ImportResponse resp = collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),        // 0: legal
                game(new int[]{CENTER, 199, 199}, 1)));       // 1: 199 occupied

        assertEquals("only the legal game stored", 1, resp.imported);
        assertEquals("one error", 1, resp.errors.size());
        assertEquals("error carries the offending index",
                1, resp.errors.get(0).index);
        assertNotNull("error carries a message", resp.errors.get(0).message);
        assertEquals("good game persisted despite the bad one",
                1, storer.countGames(PID, PENTE));
    }

    /** A game already in the collection is reported as a duplicate, not re-stored. */
    public void testImportDuplicateReportedByIndex() throws Exception {
        int[] moves = new int[]{CENTER, 199, 218};
        assertEquals(1, collectionHandler.doImport(PID,
                importReq(game(moves, 1))).imported);

        ImportResponse again = collectionHandler.doImport(PID,
                importReq(game(moves, 1)));
        assertEquals("no new games stored", 0, again.imported);
        assertEquals("one duplicate", 1, again.duplicates.size());
        assertEquals("duplicate index", 0, again.duplicates.get(0).intValue());
        assertEquals("still a single stored game",
                1, storer.countGames(PID, PENTE));
    }

    /** An off-center variant is rejected per-item with the documented message. */
    public void testImportOffCenterVariantRejected() throws Exception {
        WebDbGameData go = new WebDbGameData();
        go.game = GridStateFactory.GO; // firstMoveCanBeOffCenter == true
        go.player1 = "alice";
        go.player2 = "bob";
        go.winner = 1;
        go.moves = new int[]{CENTER};

        ImportResponse resp = collectionHandler.doImport(PID, importReq(go));
        assertEquals("nothing stored", 0, resp.imported);
        assertEquals("one error", 1, resp.errors.size());
        assertEquals("index 0", 0, resp.errors.get(0).index);
        assertEquals("documented message",
                "variant not supported for import", resp.errors.get(0).message);
        assertEquals("no rows written", 0, storer.countGames(PID, PENTE));
    }

    /** A batch over the 200-game cap is a hard 400 (nothing stored). */
    public void testImportBatchTooLargeIs400() throws Exception {
        List<WebDbGameData> big = new ArrayList<WebDbGameData>();
        for (int i = 0; i < 201; i++) {
            big.add(game(new int[]{CENTER, 199, 218}, 1));
        }
        ImportRequest req = new ImportRequest();
        req.games = big;

        try {
            collectionHandler.doImport(PID, req);
            fail("expected a 400 for an oversized batch");
        } catch (WebDbHttpError he) {
            assertEquals("batch too large -> 400", 400, he.status);
        }
        assertEquals("oversized batch stored nothing",
                0, storer.countGames(PID, PENTE));
    }

    // ==================================================================
    // collection list / get / delete
    // ==================================================================

    /** The listing pages the caller's games as source="mine" headers, gid=wgid. */
    public void testCollectionListSourceAndGid() throws Exception {
        collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        GameSearchResponse resp = collectionHandler.listCollection(PID, PENTE, 0, 25);
        assertEquals("total counts all games", 2, resp.total);
        assertEquals("page returns both", 2, resp.games.size());
        for (GameHeader h : resp.games) {
            assertEquals("source is mine", "mine", h.source);
            assertEquals("variant echoed", PENTE, h.game);
            assertTrue("gid (wgid) set", h.gid > 0);
        }
    }

    /** GET one game reconstructs the center-first move list; cross-owner is null. */
    public void testCollectionGetRoundTripAndCrossOwner() throws Exception {
        int[] moves = new int[]{CENTER, 199, 218, 200};
        collectionHandler.doImport(PID, importReq(game(moves, 1)));
        long wgid = storer.listGames(PID, PENTE, 0, 25).get(0).wgid;

        GameDetailResponse detail = collectionHandler.getGame(PID, wgid);
        assertNotNull("owner can load", detail);
        assertEquals("header is mine", "mine", detail.header.source);
        assertEquals("header gid is wgid", wgid, detail.header.gid);
        assertEquals("move list starts at center", CENTER, detail.moves[0]);
        assertTrue("move list round-trips", Arrays.equals(moves, detail.moves));

        // Cross-owner GET is indistinguishable from missing -> null (404, not 403).
        assertNull("cross-owner GET is null", collectionHandler.getGame(OTHER_PID, wgid));
    }

    /** DELETE removes the owner's game; a cross-owner delete is a no-op (404). */
    public void testCollectionDeleteOwnerAndCrossOwner() throws Exception {
        collectionHandler.doImport(PID, importReq(game(new int[]{CENTER, 199, 218}, 1)));
        long wgid = storer.listGames(PID, PENTE, 0, 25).get(0).wgid;

        // Cross-owner delete must not touch the row (would map to 404).
        assertTrue("cross-owner delete is a no-op",
                !collectionHandler.deleteGame(OTHER_PID, wgid));
        assertNotNull("owner's game survives cross-owner delete",
                collectionHandler.getGame(PID, wgid));

        assertTrue("owner delete succeeds", collectionHandler.deleteGame(PID, wgid));
        assertNull("game gone after delete", collectionHandler.getGame(PID, wgid));
        assertTrue("deleting again is a no-op",
                !collectionHandler.deleteGame(PID, wgid));
    }

    // ==================================================================
    // analyses CRUD
    // ==================================================================

    /** create -> get -> list -> update(name) -> update(tree) -> delete. */
    public void testAnalysesCrudRoundTrip() throws Exception {
        AnalysisDtos.CreateRequest cr = new AnalysisDtos.CreateRequest();
        cr.name = "Opening study";
        cr.game = PENTE;
        cr.tree = tree(199);
        long aid = analysesHandler.createAnalysis(PID, cr);
        assertTrue("aid assigned", aid > 0);

        AnalysisDtos.DetailResponse got = analysesHandler.getAnalysis(PID, aid);
        assertNotNull("owner can load", got);
        assertEquals("aid", aid, got.aid);
        assertEquals("name", "Opening study", got.name);
        assertEquals("game", PENTE, got.game);
        assertTrue("tree came back as an object", got.tree.isJsonObject());
        assertEquals("tree v:1 preserved",
                1, got.tree.getAsJsonObject().get("v").getAsInt());
        assertTrue("tree root preserved",
                got.tree.getAsJsonObject().has("root"));

        AnalysisDtos.ListResponse list = analysesHandler.listAnalyses(PID);
        assertTrue("listing contains the analysis", containsAid(list, aid));
        for (AnalysisDtos.ListItem it : list.analyses) {
            if (it.aid == aid) {
                assertNotNull("created is ISO", it.created);
                assertNotNull("updated is ISO", it.updated);
            }
        }

        // Partial update: name only, tree untouched.
        AnalysisDtos.UpdateRequest nameOnly = new AnalysisDtos.UpdateRequest();
        nameOnly.name = "Revised study";
        assertTrue("name update ok", analysesHandler.updateAnalysis(PID, aid, nameOnly));
        AnalysisDtos.DetailResponse afterName = analysesHandler.getAnalysis(PID, aid);
        assertEquals("name changed", "Revised study", afterName.name);
        assertEquals("tree unchanged by a name-only update",
                199, afterName.tree.getAsJsonObject().get("root")
                        .getAsJsonObject().get("move").getAsInt());

        // Partial update: tree only, name untouched.
        AnalysisDtos.UpdateRequest treeOnly = new AnalysisDtos.UpdateRequest();
        treeOnly.tree = tree(210);
        assertTrue("tree update ok", analysesHandler.updateAnalysis(PID, aid, treeOnly));
        AnalysisDtos.DetailResponse afterTree = analysesHandler.getAnalysis(PID, aid);
        assertEquals("name unchanged by a tree-only update",
                "Revised study", afterTree.name);
        assertEquals("tree replaced",
                210, afterTree.tree.getAsJsonObject().get("root")
                        .getAsJsonObject().get("move").getAsInt());

        assertTrue("delete ok", analysesHandler.deleteAnalysis(PID, aid));
        assertNull("gone after delete", analysesHandler.getAnalysis(PID, aid));
    }

    /** Cross-owner get/update/delete deny (map to 404), owner's copy untouched. */
    public void testAnalysesCrossOwnerDenied() throws Exception {
        AnalysisDtos.CreateRequest cr = new AnalysisDtos.CreateRequest();
        cr.name = "Private";
        cr.game = PENTE;
        cr.tree = tree(199);
        long aid = analysesHandler.createAnalysis(PID, cr);

        assertNull("cross-owner get null", analysesHandler.getAnalysis(OTHER_PID, aid));

        AnalysisDtos.UpdateRequest u = new AnalysisDtos.UpdateRequest();
        u.name = "Hijacked";
        assertTrue("cross-owner update denied",
                !analysesHandler.updateAnalysis(OTHER_PID, aid, u));
        assertTrue("cross-owner delete denied",
                !analysesHandler.deleteAnalysis(OTHER_PID, aid));

        AnalysisDtos.DetailResponse owner = analysesHandler.getAnalysis(PID, aid);
        assertNotNull("owner's analysis survives", owner);
        assertEquals("owner's name untouched", "Private", owner.name);
    }

    /** A malformed tree (missing "root") is a 400; a non-object tree is a 400. */
    public void testAnalysesInvalidTreeIs400() throws Exception {
        JsonObject noRoot = new JsonObject();
        noRoot.addProperty("v", 1);
        assertBadTree(noRoot, 400);

        JsonObject wrongVersion = new JsonObject();
        wrongVersion.addProperty("v", 2);
        wrongVersion.add("root", new JsonObject());
        assertBadTree(wrongVersion, 400);

        assertBadTree(new JsonPrimitive("not an object"), 400);
    }

    /** A tree over 1 MB is rejected 413 (handler-level, before any DB write). */
    public void testAnalysesTreeTooLargeIs413() throws Exception {
        StringBuilder sb = new StringBuilder(1024 * 1024 + 32);
        for (int i = 0; i < 1024 * 1024 + 16; i++) {
            sb.append('a');
        }
        JsonObject huge = new JsonObject();
        huge.addProperty("v", 1);
        huge.addProperty("root", sb.toString());

        AnalysisDtos.CreateRequest cr = new AnalysisDtos.CreateRequest();
        cr.name = "huge";
        cr.game = PENTE;
        cr.tree = huge;
        try {
            analysesHandler.createAnalysis(PID, cr);
            fail("expected 413 for an over-1MB tree");
        } catch (WebDbHttpError he) {
            assertEquals("tree > 1MB -> 413", 413, he.status);
        }
        // Nothing should have been persisted.
        assertEquals("no analysis rows written",
                0, countSentinel("webdb_analysis"));
    }

    // ==================================================================
    // scope = mine / both  (position-stats)
    // ==================================================================

    /** scope="mine" over two imported games returns their replies (total == 2). */
    public void testPositionStatsMine() throws Exception {
        collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        PositionStatsResponse mine =
                statsHandler.compute(statsReq(new int[]{CENTER}, "mine"), PID);

        assertEquals("both my games reached the opening", 2, mine.totalGames);
        long sum = 0;
        for (PositionStatsResponse.NextMove nm : mine.nextMoves) {
            assertTrue("move in 0..360", nm.move >= 0 && nm.move <= 360);
            sum += nm.games;
        }
        assertEquals("per-move games sum to the two decided games", 2, sum);
        assertTrue("there are candidate replies", !mine.nextMoves.isEmpty());
    }

    /** scope="both" == archive + personal: totals add and a shared move sums. */
    public void testPositionStatsBothMergesArchiveAndPersonal() throws Exception {
        collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        PositionStatsResponse archive =
                statsHandler.compute(statsReq(new int[]{CENTER}, "archive"), PID);
        PositionStatsResponse mine =
                statsHandler.compute(statsReq(new int[]{CENTER}, "mine"), PID);
        PositionStatsResponse both =
                statsHandler.compute(statsReq(new int[]{CENTER}, "both"), PID);

        assertTrue("archive has opening data", archive.totalGames > 0);
        assertEquals("mine adds two", 2, mine.totalGames);
        assertEquals("both totals are the exact sum",
                archive.totalGames + mine.totalGames, both.totalGames);
        assertTrue("both >= archive-only", both.totalGames >= archive.totalGames);

        // For every reply that appears in mine, both == archive + mine per move.
        for (PositionStatsResponse.NextMove m : mine.nextMoves) {
            long a = gamesFor(archive, m.move);
            long b = gamesFor(both, m.move);
            assertEquals("both games for move " + m.move + " is the per-move sum",
                    a + m.games, b);
        }
    }

    // ==================================================================
    // scope = mine / both  (games/search)
    // ==================================================================

    /** scope="mine" search lists the caller's games (source mine, total == 2). */
    public void testSearchMine() throws Exception {
        collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        GameSearchResponse mine =
                searchHandler.search(searchReq(new int[0], "mine", 25), PID);
        assertEquals("mine total", 2, mine.total);
        assertEquals("mine page", 2, mine.games.size());
        for (GameHeader h : mine.games) {
            assertEquals("mine source", "mine", h.source);
            assertTrue("gid set", h.gid > 0);
        }
    }

    /** scope="both" search appends the caller's games after the archive page. */
    public void testSearchBothAppendsPersonal() throws Exception {
        collectionHandler.doImport(PID, importReq(
                game(new int[]{CENTER, 199, 218}, 1),
                game(new int[]{CENTER, 181, 200}, 2)));

        GameSearchResponse archive =
                searchHandler.search(searchReq(new int[]{CENTER}, "archive", 5), PID);
        GameSearchResponse both =
                searchHandler.search(searchReq(new int[]{CENTER}, "both", 5), PID);

        assertEquals("both total is archive + personal",
                archive.total + 2, both.total);

        int mineInBoth = 0;
        for (GameHeader h : both.games) {
            if ("mine".equals(h.source)) {
                mineInBoth++;
            }
        }
        assertEquals("both appends the two personal games", 2, mineInBoth);
        // Archive page comes first, personal last.
        assertEquals("archive source leads",
                "archive", both.games.get(0).source);
        assertEquals("personal source trails",
                "mine", both.games.get(both.games.size() - 1).source);
    }

    // ==================================================================
    // anonymous -> 401 on every auth endpoint
    // ==================================================================

    public void testAnonymousRequestsAre401() throws Exception {
        // collection
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                collectionHandler.handleImport(rq, rs);
            }
        }, "{}"));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                collectionHandler.handleList(rq, rs);
            }
        }, ""));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                collectionHandler.handleGet(rq, rs, "1");
            }
        }, ""));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                collectionHandler.handleDelete(rq, rs, "1");
            }
        }, ""));

        // analyses
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                analysesHandler.handleList(rq, rs);
            }
        }, ""));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                analysesHandler.handleCreate(rq, rs);
            }
        }, "{}"));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                analysesHandler.handleGet(rq, rs, "1");
            }
        }, ""));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                analysesHandler.handleUpdate(rq, rs, "1");
            }
        }, "{}"));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                analysesHandler.handleDelete(rq, rs, "1");
            }
        }, ""));

        // position-stats + games/search with scope=mine (auth required)
        final String mineBody = "{\"scope\":\"mine\",\"game\":1,\"moves\":[180]}";
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                statsHandler.handle(rq, rs);
            }
        }, mineBody));
        assertEquals(401, drive(new Call() {
            public void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
                searchHandler.handle(rq, rs);
            }
        }, mineBody));
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static WebDbGameData game(int[] moves, int winner) {
        WebDbGameData g = new WebDbGameData();
        g.game = PENTE;
        g.player1 = "alice";
        g.player2 = "bob";
        g.winner = winner;
        g.moves = moves;
        return g;
    }

    private static ImportRequest importReq(WebDbGameData... games) {
        ImportRequest req = new ImportRequest();
        req.games = new ArrayList<WebDbGameData>(Arrays.asList(games));
        return req;
    }

    private static PositionStatsRequest statsReq(int[] moves, String scope) {
        PositionStatsRequest r = new PositionStatsRequest();
        r.game = PENTE;
        r.moves = moves;
        r.scope = scope;
        return r;
    }

    private static GameSearchRequest searchReq(int[] moves, String scope, int limit) {
        GameSearchRequest r = new GameSearchRequest();
        r.game = PENTE;
        r.moves = moves;
        r.scope = scope;
        r.limit = limit;
        return r;
    }

    /** A minimal valid analysis tree: {@code {"v":1,"root":{"move":M}}}. */
    private static JsonObject tree(int move) {
        JsonObject root = new JsonObject();
        root.addProperty("move", move);
        JsonObject t = new JsonObject();
        t.addProperty("v", 1);
        t.add("root", root);
        return t;
    }

    private void assertBadTree(com.google.gson.JsonElement tree, int expectedStatus)
            throws Exception {
        AnalysisDtos.CreateRequest cr = new AnalysisDtos.CreateRequest();
        cr.name = "bad";
        cr.game = PENTE;
        cr.tree = tree;
        try {
            analysesHandler.createAnalysis(PID, cr);
            fail("expected " + expectedStatus + " for an invalid tree");
        } catch (WebDbHttpError he) {
            assertEquals("invalid tree status", expectedStatus, he.status);
        }
    }

    private static boolean containsAid(AnalysisDtos.ListResponse list, long aid) {
        for (AnalysisDtos.ListItem it : list.analyses) {
            if (it.aid == aid) {
                return true;
            }
        }
        return false;
    }

    private static long gamesFor(PositionStatsResponse resp, int move) {
        for (PositionStatsResponse.NextMove nm : resp.nextMoves) {
            if (nm.move == move) {
                return nm.games;
            }
        }
        return 0;
    }

    // ---- anonymous-request driving via servlet proxies --------------------

    private interface Call {
        void run(HttpServletRequest rq, HttpServletResponse rs) throws IOException;
    }

    /** Drive a handler with an anonymous request; return the HTTP status set. */
    private static int drive(Call call, String body) throws IOException {
        int[] status = new int[]{200};
        HttpServletRequest rq = anonRequest(body);
        HttpServletResponse rs = capturingResponse(status);
        call.run(rq, rs);
        return status[0];
    }

    private static HttpServletRequest anonRequest(final String body) {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        InvocationHandler h = new InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] args) {
                String name = m.getName();
                if ("getInputStream".equals(name)) {
                    return new FakeServletInputStream(bytes);
                }
                if ("getAttribute".equals(name)) {
                    return null; // no "name" -> anonymous
                }
                if ("getParameter".equals(name)) {
                    return null;
                }
                if ("getPathInfo".equals(name)) {
                    return null;
                }
                return defaultFor(m, name);
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class}, h);
    }

    private static HttpServletResponse capturingResponse(final int[] status) {
        final StringWriter body = new StringWriter();
        final PrintWriter pw = new PrintWriter(body);
        InvocationHandler h = new InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] args) {
                String name = m.getName();
                if ("setStatus".equals(name)) {
                    status[0] = ((Integer) args[0]).intValue();
                    return null;
                }
                if ("getStatus".equals(name)) {
                    return Integer.valueOf(status[0]);
                }
                if ("getWriter".equals(name)) {
                    return pw;
                }
                return defaultFor(m, name);
            }
        };
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class[]{HttpServletResponse.class}, h);
    }

    private static Object defaultFor(Method m, String name) {
        if ("toString".equals(name)) {
            return "proxy";
        }
        if ("hashCode".equals(name)) {
            return Integer.valueOf(0);
        }
        if ("equals".equals(name)) {
            return Boolean.FALSE;
        }
        Class<?> rt = m.getReturnType();
        if (rt == boolean.class) {
            return Boolean.FALSE;
        }
        if (rt == int.class) {
            return Integer.valueOf(0);
        }
        if (rt == long.class) {
            return Long.valueOf(0L);
        }
        return null;
    }

    private static final class FakeServletInputStream extends ServletInputStream {
        private final InputStream in;

        FakeServletInputStream(byte[] bytes) {
            this.in = new ByteArrayInputStream(bytes);
        }

        public int read() throws IOException {
            return in.read();
        }

        public boolean isFinished() {
            try {
                return in.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        public boolean isReady() {
            return true;
        }

        public void setReadListener(ReadListener readListener) {
        }
    }

    // ---- sentinel bookkeeping --------------------------------------------

    private void purgeSentinel() throws Exception {
        Connection con = dbHandler.getConnection();
        try {
            deleteSentinel(con, "webdb_move");
            deleteSentinel(con, "webdb_game");
            deleteSentinel(con, "webdb_analysis");
        } finally {
            dbHandler.freeConnection(con);
        }
    }

    private void deleteSentinel(Connection con, String table) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(
                    "delete from " + table + " where pid in (?, ?)");
            stmt.setLong(1, PID);
            stmt.setLong(2, OTHER_PID);
            stmt.executeUpdate();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    private int countSentinel(String table) throws Exception {
        Connection con = dbHandler.getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(
                    "select count(*) from " + table + " where pid in (?, ?)");
            stmt.setLong(1, PID);
            stmt.setLong(2, OTHER_PID);
            rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            dbHandler.freeConnection(con);
        }
    }
}
