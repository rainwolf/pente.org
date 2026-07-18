package org.pente.webdb;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;
import org.pente.gameServer.core.DSGPlayerStorer;

import org.pente.webdb.dto.GameDetailResponse;
import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.ImportRequest;
import org.pente.webdb.dto.ImportResponse;
import org.pente.webdb.dto.WebDbGameData;

/**
 * Endpoint logic for the authenticated personal-collection endpoints:
 *
 * <ul>
 *   <li>{@code POST /api/db/collection/import} — bulk import client-parsed games.</li>
 *   <li>{@code GET  /api/db/collection?game=&offset=&limit=} — page the caller's games.</li>
 *   <li>{@code GET  /api/db/collection/{wgid}} — one of the caller's games (header + moves).</li>
 *   <li>{@code DELETE /api/db/collection/{wgid}} — delete one of the caller's games.</li>
 * </ul>
 *
 * <p>Every request is scoped to the authenticated pid ({@link WebDbAuth}). The
 * storer's owner predicates make a cross-owner {@code wgid} indistinguishable
 * from a missing one, so both surface as {@code 404} (the servlet never leaks
 * whether another player's row exists). Persistence lives entirely in
 * {@link MySQLWebDbStorer}; this class is HTTP + validation + DTO mapping.
 */
public class CollectionHandler {

    private static Category cat =
            Category.getInstance(CollectionHandler.class.getName());

    /** Hard cap on games per import request (larger batches are rejected 400). */
    static final int MAX_IMPORT = 200;

    /** Paging defaults for the collection listing (mirror GameSearchHandler). */
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final MySQLWebDbStorer storer;
    private final DSGPlayerStorer playerStorer;

    public CollectionHandler(MySQLWebDbStorer storer,
                             DSGPlayerStorer playerStorer) {
        this.storer = storer;
        this.playerStorer = playerStorer;
    }

    // ------------------------------------------------------------------
    // POST /collection/import
    // ------------------------------------------------------------------

    public void handleImport(HttpServletRequest request,
                             HttpServletResponse response) throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return; // 401 already emitted
        }

        ImportRequest req =
                JsonHttp.readBody(request, response, ImportRequest.class);
        if (req == null) {
            return; // readBody already emitted the 4xx envelope
        }

        try {
            ImportResponse resp = doImport(pid, req);
            JsonHttp.ok(response, resp);
        } catch (WebDbHttpError he) {
            JsonHttp.error(response, he.status, he.code, he.getMessage());
        } catch (Exception e) {
            cat.error("collection import failed", e);
            JsonHttp.error(response, 500, "server_error", "import failed");
        }
    }

    /**
     * Import each game independently: a single bad game never rolls back the
     * batch's good games. Per game — reject unsupported variants and illegal
     * move replays into {@link ImportResponse#errors} (by request index), report
     * games already in the collection in {@link ImportResponse#duplicates}, and
     * store + count the rest. A batch larger than {@link #MAX_IMPORT} is a hard
     * {@code 400} (thrown as {@link WebDbHttpError}).
     */
    public ImportResponse doImport(long pid, ImportRequest req)
            throws Exception {

        List<WebDbGameData> games = (req == null) ? null : req.games;
        int n = (games == null) ? 0 : games.size();
        if (n > MAX_IMPORT) {
            throw new WebDbHttpError(400, "batch_too_large",
                    "at most " + MAX_IMPORT + " games per import");
        }

        ImportResponse resp = new ImportResponse();
        for (int i = 0; i < n; i++) {
            WebDbGameData g = games.get(i);

            if (g == null || g.moves == null || g.moves.length == 0) {
                resp.errors.add(new ImportResponse.Error(i, "empty game"));
                continue;
            }

            // The webdb storage model reconstructs moves[0] as the center move,
            // so off-center variants (go family, double-move / swap2 openings)
            // would corrupt silently — reject them per item.
            if (GridStateFactory.firstMoveCanBeOffCenter(g.game)) {
                resp.errors.add(new ImportResponse.Error(i,
                        "variant not supported for import"));
                continue;
            }

            // Reject games whose moves do not replay legally for the variant.
            try {
                GridStateFactory.createGridState(g.game, moveDataOf(g.moves));
            } catch (Exception e) {
                resp.errors.add(new ImportResponse.Error(i,
                        "illegal move: " + e.getMessage()));
                continue;
            }

            try {
                Long dup = storer.findDuplicate(pid, g);
                if (dup != null) {
                    resp.duplicates.add(Integer.valueOf(i));
                    continue;
                }
                storer.storeGame(pid, g);
                resp.imported++;
            } catch (Exception e) {
                // One game's storage failure must not sink the whole batch.
                cat.error("import of game index " + i + " failed", e);
                resp.errors.add(new ImportResponse.Error(i,
                        "store failed: " + e.getMessage()));
            }
        }
        return resp;
    }

    // ------------------------------------------------------------------
    // GET /collection  (list)
    // ------------------------------------------------------------------

    public void handleList(HttpServletRequest request,
                           HttpServletResponse response) throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }

        int game = intParam(request, "game", 0);
        if (game <= 0) {
            JsonHttp.error(response, 400, "bad_request", "game is required");
            return;
        }
        int offset = intParam(request, "offset", 0);
        int limit = intParam(request, "limit", DEFAULT_LIMIT);

        try {
            JsonHttp.ok(response, listCollection(pid, game, offset, limit));
        } catch (Exception e) {
            cat.error("collection list failed", e);
            JsonHttp.error(response, 500, "server_error", "collection list failed");
        }
    }

    /** Page the caller's games for one variant as {@code source="mine"} headers. */
    public GameSearchResponse listCollection(long pid, int game, int offset, int limit)
            throws Exception {

        int off = Math.max(0, offset);
        int lim = clampLimit(limit);

        GameSearchResponse resp = new GameSearchResponse();
        resp.total = storer.countGames(pid, game);
        resp.games = new java.util.ArrayList<GameHeader>();
        for (WebDbGameData g : storer.listGames(pid, game, off, lim)) {
            resp.games.add(GameHeader.fromWebDb(g));
        }
        return resp;
    }

    // ------------------------------------------------------------------
    // GET /collection/{wgid}
    // ------------------------------------------------------------------

    public void handleGet(HttpServletRequest request,
                          HttpServletResponse response, String wgidStr)
            throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        long wgid = parseId(wgidStr, response);
        if (wgid < 0) {
            return; // 400 already emitted
        }

        try {
            GameDetailResponse resp = getGame(pid, wgid);
            if (resp == null) {
                JsonHttp.error(response, 404, "not_found", "no such game");
                return;
            }
            JsonHttp.ok(response, resp);
        } catch (Exception e) {
            cat.error("collection get failed", e);
            JsonHttp.error(response, 500, "server_error", "collection get failed");
        }
    }

    /** Load one of the caller's games, or {@code null} (→ 404 / cross-owner). */
    public GameDetailResponse getGame(long pid, long wgid) throws Exception {
        WebDbGameData g = storer.loadGame(pid, wgid);
        if (g == null) {
            return null;
        }
        GameDetailResponse resp = new GameDetailResponse();
        resp.header = GameHeader.fromWebDb(g);
        resp.moves = g.moves;
        resp.renjuOffers = null;
        return resp;
    }

    // ------------------------------------------------------------------
    // DELETE /collection/{wgid}
    // ------------------------------------------------------------------

    public void handleDelete(HttpServletRequest request,
                             HttpServletResponse response, String wgidStr)
            throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        long wgid = parseId(wgidStr, response);
        if (wgid < 0) {
            return;
        }

        try {
            if (!storer.deleteGame(pid, wgid)) {
                JsonHttp.error(response, 404, "not_found", "no such game");
                return;
            }
            JsonHttp.ok(response, ok());
        } catch (Exception e) {
            cat.error("collection delete failed", e);
            JsonHttp.error(response, 500, "server_error", "collection delete failed");
        }
    }

    /** Delete one of the caller's games; {@code false} when nothing was owned. */
    public boolean deleteGame(long pid, long wgid) throws Exception {
        return storer.deleteGame(pid, wgid);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", Boolean.TRUE);
        return m;
    }

    private static int clampLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static int intParam(HttpServletRequest request, String name, int def) {
        String v = request.getParameter(name);
        if (v == null || v.trim().length() == 0) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseId(String s, HttpServletResponse response)
            throws IOException {
        try {
            return Long.parseLong(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            JsonHttp.error(response, 400, "bad_request", "invalid id: " + s);
            return -1L;
        }
    }

    private static MoveData moveDataOf(final int[] moves) {
        return new MoveData() {
            public void addMove(int move) {
                throw new UnsupportedOperationException();
            }
            public void undoMove() {
                throw new UnsupportedOperationException();
            }
            public int getMove(int num) {
                return moves[num];
            }
            public int getNumMoves() {
                return moves.length;
            }
            public int[] getMoves() {
                return moves;
            }
        };
    }
}
