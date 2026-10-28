package org.pente.webdb;

import java.io.IOException;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;

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
}
