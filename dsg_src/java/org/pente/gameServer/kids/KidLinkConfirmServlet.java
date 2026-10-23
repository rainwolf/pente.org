package org.pente.gameServer.kids;

import org.apache.log4j.Category;
import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.server.Resources;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;

/**
 * Landing page for the conversion email: /confirmKidLink?code=. No login.
 * GET only shows the request and a Confirm form (mail scanners that prefetch links can't
 * confirm); POST with the code makes the account a kid of the requesting parent.
 */
public class KidLinkConfirmServlet extends HttpServlet {
    private static final long serialVersionUID = -2286401837704652190L;

    private static final Category log4j =
            Category.getInstance(KidLinkConfirmServlet.class.getName());

    private transient CacheDSGPlayerStorer players;
    private transient CacheKidStorer kidStorer;

    /** a live link request with both players loaded */
    private static class Link {
        KidLinkRequest request;
        DSGPlayerData parent;
        DSGPlayerData kid;
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            Resources resources = (Resources)
                    getServletContext().getAttribute(Resources.class.getName());
            players = resources.getDsgPlayerStorer();
            kidStorer = resources.getKidStorer();
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Link link = load(request.getParameter("code"));
            if (link == null) {
                page(response, KidAccountRules.LINK_INVALID, null);
                return;
            }
            String text = escape(link.parent.getName()) + " wants to manage "
                    + escape(link.kid.getName()) + " as a kid account. "
                    + "A kid account can play anyone, but can chat and exchange messages only with "
                    + "players " + escape(link.parent.getName()) + " approves, and "
                    + escape(link.parent.getName()) + " manages its password and email.";
            page(response, text, link.request.getCode());
        } catch (DSGPlayerStoreException e) {
            log4j.error("Kids: confirm GET failed", e);
            page(response, KidAccountRules.DATABASE, null);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Link link = load(request.getParameter("code"));
            if (link == null) {
                page(response, KidAccountRules.LINK_INVALID, null);
                return;
            }
            DSGPlayerData parent = link.parent;
            long kidPid = link.kid.getPlayerID();
            if (!parent.isActive()) {
                page(response, KidAccountRules.LINK_INVALID, null);
                return;
            }
            String err = KidAccountRules.parentError(parent, false);
            if (err == null) {
                boolean hasKids = !kidStorer.getKidPids(kidPid).isEmpty();
                err = KidAccountRules.conversionError(parent, link.kid, hasKids);
            }
            if (err != null) {
                page(response, escape(err), null);
                return;
            }
            DSGPlayerData fresh = players.loadPlayer(kidPid);
            fresh.setMobileAdult(false);
            fresh.setMobileAgeBand(null);
            players.updatePlayer(fresh);
            kidStorer.setParentPid(kidPid, Long.valueOf(parent.getPlayerID()));
            players.refreshPlayer(fresh.getName());
            // after the refresh: fresh rows, and the notify makes live sessions reload the kid
            kidStorer.deleteKidData(kidPid);
            kidStorer.deleteLinkRequest(link.request.getCode());
            log4j.info("Kids: confirm by " + fresh.getName() + " kid=" + kidPid
                    + " parent=" + parent.getName());
            page(response, escape(fresh.getName()) + " is now a kid account managed by "
                    + escape(parent.getName()) + ".", null);
        } catch (DSGPlayerStoreException e) {
            log4j.error("Kids: confirm POST failed", e);
            page(response, KidAccountRules.DATABASE, null);
        }
    }

    /** the unexpired request for code with both players, or null (an expired row is deleted) */
    private Link load(String code) throws DSGPlayerStoreException {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        KidLinkRequest r = kidStorer.getLinkRequest(code.trim());
        if (r == null) {
            return null;
        }
        if (KidAccountRules.linkExpired(r.getCreationDate(), new Date())) {
            kidStorer.deleteLinkRequest(r.getCode());
            return null;
        }
        Link link = new Link();
        link.request = r;
        link.parent = players.loadPlayer(r.getParentPid());
        link.kid = players.loadPlayer(r.getKidPid());
        if (link.parent == null || link.kid == null) {
            return null;
        }
        return link;
    }

    /** a minimal page with html-safe text and, when code is set, the Confirm form */
    private void page(HttpServletResponse response, String html, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Pente.org kid account</title></head>");
        out.println("<body style=\"font-family: sans-serif; max-width: 40em; margin: 2em auto; padding: 0 1em;\">");
        out.println("<h2>Pente.org kid account</h2>");
        out.println("<p>" + html + "</p>");
        if (code != null) {
            out.println("<form method=\"post\" action=\"/confirmKidLink\">");
            out.println("<input type=\"hidden\" name=\"code\" value=\"" + escape(code) + "\">");
            out.println("<button type=\"submit\">Confirm</button>");
            out.println("</form>");
        }
        out.println("<p><a href=\"https://www.pente.org/\">Pente.org</a></p>");
        out.println("</body></html>");
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
