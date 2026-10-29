package org.pente.webdb;

import java.io.IOException;
import java.util.Arrays;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.GameData;
import org.pente.game.GameStorer;
import org.pente.game.MySQLPenteGameStorer;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.server.ActivityLogger;

import org.pente.webdb.dto.GameDetailResponse;
import org.pente.webdb.dto.GameHeader;

/**
 * Endpoint logic for {@code GET /api/db/games/{gid}}.
 *
 * <p>Delegates to the production {@code GameStorer.loadGame(gid, null)}, which
 * reconstructs the full move list (center stone prepended for non-Go variants,
 * the terminal {@code 361} row filtered out) and loads any Renju swap offers.
 * A {@code null} return means no such game.
 */
public class GameLoadHandler {

    private static Category cat =
            Category.getInstance(GameLoadHandler.class.getName());

    private final GameStorer gameStorer;

    public GameLoadHandler(GameStorer gameStorer) {
        this.gameStorer = gameStorer;
    }

    /** Legacy blocked check ({@code viewDb}); null means never blocked. */
    private ActivityLogger activityLogger;

    public void setActivityLogger(ActivityLogger activityLogger) {
        this.activityLogger = activityLogger;
    }

    /**
     * Servlet entry point. {@code gidStr} is the path segment after
     * {@code /games/}; a non-numeric value yields a 400 envelope and an unknown
     * game a 404.
     */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response, String gidStr)
            throws IOException {

        long gid;
        try {
            gid = Long.parseLong(gidStr == null ? "" : gidStr.trim());
        } catch (NumberFormatException e) {
            JsonHttp.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "bad_request", "invalid game id: " + gidStr);
            return;
        }

        // Hash 0 and no moves (turn-based gids too): only the ban and the
        // "playing a live rated game" checks apply.
        if (WebDbAuth.viewDbBlocked(activityLogger, request, 0L, new int[0],
                "webdb games/" + gid)) {
            JsonHttp.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "blocked", "database unavailable during a rated game");
            return;
        }

        try {
            GameDetailResponse resp = load(gid);
            if (resp == null) {
                JsonHttp.error(response, HttpServletResponse.SC_NOT_FOUND,
                        "not_found", "no such game: " + gid);
                return;
            }
            JsonHttp.ok(response, resp);
        } catch (IllegalArgumentException e) {
            JsonHttp.error(response, 400, "bad_request", e.getMessage());
        } catch (Exception e) {
            cat.error("games/" + gid + " load failed", e);
            JsonHttp.error(response, 500, "server_error", "game load failed");
        }
    }

    /**
     * Load one game by id, or {@code null} if it does not exist. Split out from
     * {@link #handle} so the DB-backed test can exercise it directly.
     */
    public GameDetailResponse load(long gid) throws Exception {

        // loadGame(gid, null) returns null when the row does not exist; for an
        // existing game it reconstructs the move list (center-prepended for
        // non-Go variants) and any Renju swap offers.
        // On the high-latency read-only replica the Ro variant needs 2 queries
        // (1 round trip) instead of 5 serial ones; same result.
        GameData gd = (gameStorer instanceof MySQLPenteGameStorer)
                ? ((MySQLPenteGameStorer) gameStorer).loadGameRo(gid)
                : gameStorer.loadGame(gid, null);
        if (gd == null) {
            return null;
        }
        // Private archive games are not exposed by the public gid load path;
        // treat them as missing (→ 404), mirroring the search endpoint's
        // g.private = 'N' guard. F1.
        if (gd.isPrivateGame()) {
            return null;
        }
        if (GridStateFactory.isTombstone(GridStateFactory.getGameId(gd.getGame()))) {
            throw new IllegalArgumentException("variant not supported");
        }

        GameDetailResponse resp = new GameDetailResponse();
        resp.header = GameHeader.from(gd, "archive");
        resp.moves = Arrays.copyOf(gd.getMoves(), gd.getNumMoves());
        resp.renjuOffers = gd.getRenjuOffers();
        return resp;
    }
}
