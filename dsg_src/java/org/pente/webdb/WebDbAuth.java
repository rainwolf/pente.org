package org.pente.webdb;

import java.io.IOException;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.server.ActivityData;
import org.pente.gameServer.server.ActivityLogger;

/**
 * Auth helper for {@code /api/db/*} endpoints.
 *
 * The browser login state is published by {@code LoginFilter} as the request
 * attribute {@code "name"} (the login-name string). There is no numeric pid in
 * the session, so we look the player up per-request via {@link DSGPlayerStorer}.
 */
public final class WebDbAuth {

    private static Category cat = Category.getInstance(WebDbAuth.class.getName());

    private WebDbAuth() {
    }

    /**
     * Resolve the authenticated player's numeric pid, or write a 401
     * <code>{"error":{"code":"auth"}}</code> envelope and return {@code -1}.
     * Callers treat {@code -1} as "response already sent, stop".
     */
    public static long requirePid(HttpServletRequest request,
                                  HttpServletResponse response,
                                  DSGPlayerStorer playerStorer)
            throws IOException {

        String name = (String) request.getAttribute("name");
        if (name == null) {
            JsonHttp.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "auth", null);
            return -1;
        }

        try {
            DSGPlayerData pdata = playerStorer.loadPlayer(name);
            if (pdata == null) {
                JsonHttp.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "auth", null);
                return -1;
            }
            return pdata.getPlayerID();
        } catch (Exception e) {
            cat.error("Problem resolving pid for name=" + name, e);
            JsonHttp.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "auth", null);
            return -1;
        }
    }

    /**
     * Grace window for new players, copied from legacy {@code database.jsp}:
     * registered within the last ~60 days ({@code 1000L*3600*24*30*2}) and
     * played at least one game.
     */
    public static final long NEW_PLAYER_WINDOW_MS = 1000L * 3600 * 24 * 30 * 2;

    /**
     * Legacy {@code database.jsp} DB-access rule: a subscriber
     * ({@link DSGPlayerData#databaseAccess()}), or a new player (registered
     * within {@link #NEW_PLAYER_WINDOW_MS} of {@code nowMillis}) with at least
     * one game. {@code null} (anonymous / unknown) has no access.
     */
    public static boolean hasDbAccess(DSGPlayerData pdata, long nowMillis) {
        if (pdata == null) {
            return false;
        }
        if (pdata.databaseAccess()) {
            return true;
        }
        java.util.Date registered = pdata.getRegisterDate();
        return registered != null
                && registered.getTime() > nowMillis - NEW_PLAYER_WINDOW_MS
                && pdata.getTotalGames() > 0;
    }

    /**
     * Resolve the session player ({@code "name"} request attribute, as
     * {@link #requirePid}) and load them via {@code playerStorer}, or
     * {@code null} when anonymous / not found / lookup failed.
     */
    public static DSGPlayerData loadSessionPlayer(HttpServletRequest request,
                                                  DSGPlayerStorer playerStorer) {
        String name = (String) request.getAttribute("name");
        if (name == null || playerStorer == null) {
            return null;
        }
        try {
            return playerStorer.loadPlayer(name);
        } catch (Exception e) {
            cat.error("Problem loading player for name=" + name, e);
            return null;
        }
    }

    /**
     * Gate for the whole {@code /api/db/*} API. Returns {@code true} when the
     * session player has DB access ({@link #hasDbAccess}). Otherwise writes
     * {@code 401 {"error":{"code":"auth"}}} (anonymous / unknown player) or
     * {@code 403 {"error":{"code":"subscription_required"}}} (logged in, no
     * access) and returns {@code false}; callers stop on {@code false}.
     * Nothing is cached: one player lookup per request.
     */
    public static boolean requireDbAccess(HttpServletRequest request,
                                          HttpServletResponse response,
                                          DSGPlayerStorer playerStorer)
            throws IOException {

        DSGPlayerData pdata = loadSessionPlayer(request, playerStorer);
        if (pdata == null) {
            JsonHttp.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "auth", null);
            return false;
        }
        if (!hasDbAccess(pdata, System.currentTimeMillis())) {
            JsonHttp.error(response, HttpServletResponse.SC_FORBIDDEN,
                    "subscription_required",
                    "The pente.org database requires a subscription");
            return false;
        }
        return true;
    }

    /**
     * Kill switch for the legacy {@code ActivityLogger.viewDb} blocked check on
     * {@code /api/db/*} (B5). Committed as {@code true} (owner decision);
     * flipping it needs a rebuild and an owner-run redeploy.
     */
    public static final boolean VIEWDB_BLOCK_ENABLED = true;

    /**
     * Hash sent to {@code ActivityLogger.viewDb} in place of {@code 0} (no
     * moves / tombstone / games/{gid}). A freshly started live table holds
     * hash 0 with null moves until its first {@code updateGameState}, and
     * {@code findGameMatch} NPEs on it when the request hash is also 0, which
     * skips the in-live-rated-game player check. A non-zero sentinel skips such
     * tables on the hash compare; it can never match a real table either,
     * because {@code findGameMatch} needs more than 2 moves and equal move
     * counts, while these requests carry 0 moves.
     */
    public static final long NO_POSITION_HASH = Long.MIN_VALUE;

    /**
     * Canonical position hash for the {@code viewDb} check: the same value the
     * game room feeds {@code ActivityLogger.updateGameState}
     * ({@code ServerTable}: {@code gridState.getHash()}). {@code 0} when there
     * are no moves or the game is a tombstone code.
     */
    public static long positionHash(int game, int[] moves) {
        if (moves == null || moves.length == 0
                || GridStateFactory.isTombstone(game)) {
            return 0L;
        }
        GridState state = GridStateFactory.createGridState(game);
        for (int m : moves) {
            state.addMove(m);
        }
        return state.getHash();
    }

    /**
     * {@code true} when the legacy {@code viewDb} check blocks this request
     * (banned, or the session player / address is in a live rated game, or the
     * position matches a live rated table). Turn-based play is never blocked:
     * only the live game room feeds {@link ActivityLogger}.
     */
    public static boolean viewDbBlocked(ActivityLogger logger,
                                        HttpServletRequest request,
                                        long hash, int[] moves,
                                        String search) {
        return viewDbBlocked(VIEWDB_BLOCK_ENABLED, logger, request, hash,
                moves, search);
    }

    /**
     * As {@link #viewDbBlocked(ActivityLogger, HttpServletRequest, long, int[], String)}
     * with an explicit switch. {@code false} without calling the logger when
     * disabled or no logger is wired; a {@link RuntimeException} from
     * {@code viewDb} is logged and treated as not blocked (legacy
     * {@code isBanned} also fails open). Public only so tests can drive the
     * disabled path.
     */
    public static boolean viewDbBlocked(boolean enabled, ActivityLogger logger,
                                        HttpServletRequest request,
                                        long hash, int[] moves,
                                        String search) {
        if (!enabled || logger == null) {
            return false;
        }
        try {
            return logger.viewDb(new ActivityData(
                            (String) request.getAttribute("name"),
                            request.getRemoteAddr()),
                    hash == 0L ? NO_POSITION_HASH : hash,
                    moves == null ? new int[0] : moves, search);
        } catch (RuntimeException e) {
            cat.error("viewDb check failed for " + search
                    + "; treating as not blocked", e);
            return false;
        }
    }
}
