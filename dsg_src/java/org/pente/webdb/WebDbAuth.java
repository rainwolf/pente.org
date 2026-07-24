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
}
