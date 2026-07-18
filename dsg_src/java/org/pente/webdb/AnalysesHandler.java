package org.pente.webdb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.pente.gameServer.core.DSGPlayerStorer;

import org.pente.webdb.dto.AnalysisDtos;
import org.pente.webdb.dto.AnalysisMeta;

/**
 * Endpoint logic for the authenticated saved-analysis endpoints:
 *
 * <ul>
 *   <li>{@code GET    /api/db/analyses} — list the caller's analyses (metadata only).</li>
 *   <li>{@code POST   /api/db/analyses} — create an analysis, returns its {@code aid}.</li>
 *   <li>{@code GET    /api/db/analyses/{aid}} — one analysis (with its tree).</li>
 *   <li>{@code PUT    /api/db/analyses/{aid}} — rename and/or replace the tree.</li>
 *   <li>{@code DELETE /api/db/analyses/{aid}} — delete an analysis.</li>
 * </ul>
 *
 * <p>The {@code tree} is persisted as its RAW JSON text; it is validated to be a
 * JSON object carrying {@code "v":1} and a {@code "root"} member, and rejected
 * ({@code 413}) beyond 1 MB. Every read/write is owner-scoped via
 * {@link MySQLWebDbStorer}, so a cross-owner {@code aid} surfaces as {@code 404}.
 * This class is HTTP + validation + DTO mapping; persistence stays in the storer.
 */
public class AnalysesHandler {

    private static Category cat =
            Category.getInstance(AnalysesHandler.class.getName());

    /** Max raw tree size accepted (the request-body cap may fire first). */
    static final int MAX_TREE_BYTES = 1024 * 1024; // 1 MB

    private final MySQLWebDbStorer storer;
    private final DSGPlayerStorer playerStorer;

    public AnalysesHandler(MySQLWebDbStorer storer,
                           DSGPlayerStorer playerStorer) {
        this.storer = storer;
        this.playerStorer = playerStorer;
    }

    // ------------------------------------------------------------------
    // GET /analyses  (list)
    // ------------------------------------------------------------------

    public void handleList(HttpServletRequest request,
                           HttpServletResponse response) throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        try {
            JsonHttp.ok(response, listAnalyses(pid));
        } catch (Exception e) {
            cat.error("analyses list failed", e);
            JsonHttp.error(response, 500, "server_error", "analyses list failed");
        }
    }

    public AnalysisDtos.ListResponse listAnalyses(long pid) throws Exception {
        AnalysisDtos.ListResponse resp = new AnalysisDtos.ListResponse();
        resp.analyses = new ArrayList<AnalysisDtos.ListItem>();
        for (AnalysisMeta m : storer.listAnalyses(pid)) {
            resp.analyses.add(AnalysisDtos.ListItem.from(m));
        }
        return resp;
    }

    // ------------------------------------------------------------------
    // POST /analyses  (create)
    // ------------------------------------------------------------------

    public void handleCreate(HttpServletRequest request,
                             HttpServletResponse response) throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        AnalysisDtos.CreateRequest req =
                JsonHttp.readBody(request, response, AnalysisDtos.CreateRequest.class);
        if (req == null) {
            return;
        }
        try {
            long aid = createAnalysis(pid, req);
            JsonHttp.ok(response, new AnalysisDtos.CreateResponse(aid));
        } catch (WebDbHttpError he) {
            JsonHttp.error(response, he.status, he.code, he.getMessage());
        } catch (Exception e) {
            cat.error("analyses create failed", e);
            JsonHttp.error(response, 500, "server_error", "analyses create failed");
        }
    }

    public long createAnalysis(long pid, AnalysisDtos.CreateRequest req)
            throws Exception {
        if (req.name == null || req.name.trim().length() == 0) {
            throw new WebDbHttpError(400, "bad_request", "name is required");
        }
        String raw = validateTree(req.tree);
        return storer.storeAnalysis(pid, req.name, req.game, raw);
    }

    // ------------------------------------------------------------------
    // GET /analyses/{aid}
    // ------------------------------------------------------------------

    public void handleGet(HttpServletRequest request,
                          HttpServletResponse response, String aidStr)
            throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        long aid = parseId(aidStr, response);
        if (aid < 0) {
            return;
        }
        try {
            AnalysisDtos.DetailResponse resp = getAnalysis(pid, aid);
            if (resp == null) {
                JsonHttp.error(response, 404, "not_found", "no such analysis");
                return;
            }
            JsonHttp.ok(response, resp);
        } catch (Exception e) {
            cat.error("analyses get failed", e);
            JsonHttp.error(response, 500, "server_error", "analyses get failed");
        }
    }

    /** Load one analysis (with its tree), or {@code null} (→ 404 / cross-owner). */
    public AnalysisDtos.DetailResponse getAnalysis(long pid, long aid)
            throws Exception {
        AnalysisMeta meta = new AnalysisMeta();
        String raw = storer.loadAnalysis(pid, aid, meta);
        if (raw == null) {
            return null;
        }
        AnalysisDtos.DetailResponse resp = new AnalysisDtos.DetailResponse();
        resp.aid = meta.aid;
        resp.name = meta.name;
        resp.game = meta.game;
        resp.tree = parseTree(raw);
        return resp;
    }

    // ------------------------------------------------------------------
    // PUT /analyses/{aid}
    // ------------------------------------------------------------------

    public void handleUpdate(HttpServletRequest request,
                             HttpServletResponse response, String aidStr)
            throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        long aid = parseId(aidStr, response);
        if (aid < 0) {
            return;
        }
        AnalysisDtos.UpdateRequest req =
                JsonHttp.readBody(request, response, AnalysisDtos.UpdateRequest.class);
        if (req == null) {
            return;
        }
        try {
            if (!updateAnalysis(pid, aid, req)) {
                JsonHttp.error(response, 404, "not_found", "no such analysis");
                return;
            }
            JsonHttp.ok(response, ok());
        } catch (WebDbHttpError he) {
            JsonHttp.error(response, he.status, he.code, he.getMessage());
        } catch (Exception e) {
            cat.error("analyses update failed", e);
            JsonHttp.error(response, 500, "server_error", "analyses update failed");
        }
    }

    /**
     * Apply a partial update: absent {@code name}/{@code tree} fields keep the
     * stored values. Returns {@code false} when the analysis does not exist for
     * this player (→ 404). A malformed/oversized tree throws {@link WebDbHttpError}.
     */
    public boolean updateAnalysis(long pid, long aid, AnalysisDtos.UpdateRequest req)
            throws Exception {

        AnalysisMeta meta = new AnalysisMeta();
        String currentRaw = storer.loadAnalysis(pid, aid, meta);
        if (currentRaw == null) {
            return false; // not found / cross-owner
        }
        String newName = (req.name != null) ? req.name : meta.name;
        String newRaw = (req.tree != null) ? validateTree(req.tree) : currentRaw;
        return storer.updateAnalysis(pid, aid, newName, newRaw);
    }

    // ------------------------------------------------------------------
    // DELETE /analyses/{aid}
    // ------------------------------------------------------------------

    public void handleDelete(HttpServletRequest request,
                             HttpServletResponse response, String aidStr)
            throws IOException {

        long pid = WebDbAuth.requirePid(request, response, playerStorer);
        if (pid < 0) {
            return;
        }
        long aid = parseId(aidStr, response);
        if (aid < 0) {
            return;
        }
        try {
            if (!storer.deleteAnalysis(pid, aid)) {
                JsonHttp.error(response, 404, "not_found", "no such analysis");
                return;
            }
            JsonHttp.ok(response, ok());
        } catch (Exception e) {
            cat.error("analyses delete failed", e);
            JsonHttp.error(response, 500, "server_error", "analyses delete failed");
        }
    }

    public boolean deleteAnalysis(long pid, long aid) throws Exception {
        return storer.deleteAnalysis(pid, aid);
    }

    // ------------------------------------------------------------------
    // tree validation / parsing
    // ------------------------------------------------------------------

    /**
     * Validate the request tree and return its raw JSON text for storage. The
     * tree must be a JSON object with {@code "v":1} and a {@code "root"} member,
     * and at most 1 MB.
     */
    String validateTree(JsonElement tree) throws WebDbHttpError {
        if (tree == null || !tree.isJsonObject()) {
            throw new WebDbHttpError(400, "bad_tree", "tree must be a JSON object");
        }
        JsonObject o = tree.getAsJsonObject();
        JsonElement v = o.get("v");
        if (v == null || !v.isJsonPrimitive()
                || !v.getAsJsonPrimitive().isNumber() || v.getAsInt() != 1) {
            throw new WebDbHttpError(400, "bad_tree", "tree must carry \"v\":1");
        }
        if (!o.has("root")) {
            throw new WebDbHttpError(400, "bad_tree",
                    "tree must carry a \"root\" member");
        }
        String raw = tree.toString();
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_TREE_BYTES) {
            throw new WebDbHttpError(413, "payload_too_large",
                    "tree exceeds 1 MB");
        }
        return raw;
    }

    /** Re-parse stored raw tree JSON back into a nested value for the response. */
    private static JsonElement parseTree(String raw) {
        // Gson.fromJson(String, Class) is present and non-deprecated across the
        // 2.8/2.10 jars on the classpath (unlike JsonParser.parseString).
        return new Gson().fromJson(raw, JsonElement.class);
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ok", Boolean.TRUE);
        return m;
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
}
