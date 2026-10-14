package org.pente.gameServer.client.web;

import com.google.gson.Gson;
import org.apache.log4j.Category;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ignore preferences for the mobile apps: /gameServer/ignored (in SimpleLoginAccessController.loginPages).
 * The player is LoginFilter's "name" request attribute (name + password request params), never a
 * separate parameter.
 * <p>
 * GET: list the player's ignores.
 * POST action=add (ignoreName, ignoreInvite, ignoreChat), action=update (ignorePid, ignoreInvite, ignoreChat),
 * action=remove (ignorePid). Flags are "Y" or "true"; update with both flags unset removes the entry.
 * Every reply is JSON: {"success":bool, "error":string (only on failure), "ignores":[...] (only on success)}.
 */
public class ChangeIgnoredServlet extends HttpServlet {

    private static final Category log4j =
            Category.getInstance(ChangeIgnoredServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;
    private final Gson gson = new Gson();

    /** one entry of the "ignores" list in a reply */
    private static class IgnoreEntry {
        long ignorePid;
        String name;
        boolean ignoreInvite;
        boolean ignoreChat;
    }

    private static class IgnoreReply {
        boolean success;
        String error;
        List<IgnoreEntry> ignores;
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            Resources resources = (Resources)
                    getServletContext().getAttribute(Resources.class.getName());
            dsgPlayerStorer = resources.getDsgPlayerStorer();
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            DSGPlayerData me = loggedInPlayer(request);
            if (me == null) {
                error(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
                return;
            }
            success(response, me.getPlayerID());
        } catch (DSGPlayerStoreException e) {
            log4j.error("ChangeIgnoredServlet: GET failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            DSGPlayerData me = loggedInPlayer(request);
            if (me == null) {
                error(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
                return;
            }
            String action = request.getParameter("action");
            if ("add".equals(action)) {
                add(request, response, me);
            } else if ("update".equals(action)) {
                update(request, response, me);
            } else if ("remove".equals(action)) {
                remove(request, response, me);
            } else {
                error(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (DSGPlayerStoreException e) {
            log4j.error("ChangeIgnoredServlet: POST failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private void add(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        String ignoreName = request.getParameter("ignoreName");
        if (ignoreName == null || ignoreName.trim().isEmpty()) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "ignoreName is required");
            return;
        }
        boolean ignoreInvite = flag(request, "ignoreInvite");
        boolean ignoreChat = flag(request, "ignoreChat");
        if (!ignoreInvite && !ignoreChat) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "Ignore invitations and/or chat");
            return;
        }
        DSGPlayerData d = dsgPlayerStorer.loadPlayer(ignoreName.trim());
        if (d == null) {
            error(response, HttpServletResponse.SC_NOT_FOUND, "Player to ignore not found");
            return;
        }
        if (d.getPlayerID() == me.getPlayerID()) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "You can't ignore yourself silly");
            return;
        }
        if (dsgPlayerStorer.getIgnoreData(me.getPlayerID(), d.getPlayerID()) != null) {
            error(response, HttpServletResponse.SC_CONFLICT, "Player is already ignored");
            return;
        }
        DSGIgnoreData i = new DSGIgnoreData();
        i.setPid(me.getPlayerID());
        i.setIgnorePid(d.getPlayerID());
        i.setIgnoreInvite(ignoreInvite);
        i.setIgnoreChat(ignoreChat);
        dsgPlayerStorer.insertIgnore(i);
        changed(me.getPlayerID());
        success(response, me.getPlayerID());
    }

    private void update(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGIgnoreData i = existing(request, response, me);
        if (i == null) {
            return;
        }
        boolean ignoreInvite = flag(request, "ignoreInvite");
        boolean ignoreChat = flag(request, "ignoreChat");
        // neither flag set means the ignore is lifted
        if (!ignoreInvite && !ignoreChat) {
            dsgPlayerStorer.deleteIgnore(i);
            changed(me.getPlayerID());
        } else if (i.getIgnoreInvite() != ignoreInvite || i.getIgnoreChat() != ignoreChat) {
            i.setIgnoreInvite(ignoreInvite);
            i.setIgnoreChat(ignoreChat);
            dsgPlayerStorer.updateIgnore(i);
            changed(me.getPlayerID());
        }
        success(response, me.getPlayerID());
    }

    private void remove(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGIgnoreData i = existing(request, response, me);
        if (i == null) {
            return;
        }
        dsgPlayerStorer.deleteIgnore(i);
        changed(me.getPlayerID());
        success(response, me.getPlayerID());
    }

    /** the caller's existing ignore for the ignorePid parameter; null after writing the error reply */
    private DSGIgnoreData existing(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        long ignorePid;
        try {
            ignorePid = Long.parseLong(request.getParameter("ignorePid"));
        } catch (NumberFormatException e) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, "ignorePid is required");
            return null;
        }
        DSGIgnoreData i = dsgPlayerStorer.getIgnoreData(me.getPlayerID(), ignorePid);
        if (i == null) {
            error(response, HttpServletResponse.SC_NOT_FOUND, "Player is not ignored");
        }
        return i;
    }

    /** the logged-in non-guest player, or null (LoginFilter sets "guest" for crawlers) */
    private DSGPlayerData loggedInPlayer(HttpServletRequest request) throws DSGPlayerStoreException {
        String name = (String) request.getAttribute("name");
        if (name == null || name.trim().isEmpty() || "guest".equalsIgnoreCase(name.trim())) {
            return null;
        }
        DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
        if (data == null || data.isGuest()) {
            return null;
        }
        return data;
    }

    private static boolean flag(HttpServletRequest request, String param) {
        String v = request.getParameter(param);
        return "Y".equals(v) || "true".equalsIgnoreCase(v);
    }

    /** tell the live server (and any other listener) to reload this player's ignores */
    private void changed(long pid) throws DSGPlayerStoreException {
        if (dsgPlayerStorer instanceof CacheDSGPlayerStorer) {
            ((CacheDSGPlayerStorer) dsgPlayerStorer).notifyIgnoreListeners(pid);
        }
    }

    private void success(HttpServletResponse response, long pid)
            throws DSGPlayerStoreException, IOException {
        IgnoreReply reply = new IgnoreReply();
        reply.success = true;
        reply.ignores = new ArrayList<IgnoreEntry>();
        for (DSGIgnoreData i : dsgPlayerStorer.getIgnoreData(pid)) {
            IgnoreEntry e = new IgnoreEntry();
            e.ignorePid = i.getIgnorePid();
            DSGPlayerData d = dsgPlayerStorer.loadPlayer(i.getIgnorePid());
            if (d == null) {
                continue;
            }
            e.name = d.getName();
            e.ignoreInvite = i.getIgnoreInvite();
            e.ignoreChat = i.getIgnoreChat();
            reply.ignores.add(e);
        }
        write(response, HttpServletResponse.SC_OK, reply);
    }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        IgnoreReply reply = new IgnoreReply();
        reply.success = false;
        reply.error = message;
        write(response, status, reply);
    }

    private void write(HttpServletResponse response, int status, IgnoreReply reply) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(reply));
    }
}
