package org.pente.webdb;

import java.io.*;
import java.util.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameDatabase.*;

/**
 * JSON API servlet for the pente position/game database, mapped to
 * {@code /api/db/*}. Dispatches on {@link HttpServletRequest#getPathInfo()}
 * (e.g. {@code /ping}, {@code /position-stats}). Concrete endpoint handlers are
 * added in later tasks; for now only {@code GET /api/db/ping} is served and any
 * other path returns the standard 404 error envelope.
 *
 * DB access follows the repo idiom: the pre-built storers are published on the
 * {@code ServletContext} by {@code DSGContextListener} under their interface
 * class-name keys and pulled here in {@link #init}, rather than constructing a
 * {@code DBHandler} directly.
 */
public class WebDbApiServlet extends HttpServlet {

    private static Category cat =
            Category.getInstance(WebDbApiServlet.class.getName());

    private static DBHandler dbHandler;
    private static DSGPlayerStorer dsgPlayerStorer;
    private static GameStorer gameStorer;
    private static GameVenueStorer gameVenueStorer;
    private static GameStorerSearcher gameStorerSearcher;
    // TODO(webdb Task 5): private static MySQLWebDbStorer webDbStorer;

    private static PositionStatsHandler positionStatsHandler;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            dbHandler = (DBHandler)
                    ctx.getAttribute(DBHandler.class.getName());
            dsgPlayerStorer = (DSGPlayerStorer)
                    ctx.getAttribute(DSGPlayerStorer.class.getName());
            gameStorer = (GameStorer)
                    ctx.getAttribute(GameStorer.class.getName());
            gameVenueStorer = (GameVenueStorer)
                    ctx.getAttribute(GameVenueStorer.class.getName());
            gameStorerSearcher = (GameStorerSearcher)
                    ctx.getAttribute(GameStorerSearcher.class.getName());

            positionStatsHandler =
                    new PositionStatsHandler(dbHandler, gameVenueStorer);

            // TODO(webdb Task 5): construct MySQLWebDbStorer on top of the
            // storers above and keep it for the real position/stats endpoints.

        } catch (Throwable t) {
            cat.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        if ("/ping".equals(path)) {
            handlePing(request, response);
            return;
        }

        notFound(response, path);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        if ("/position-stats".equals(path)) {
            positionStatsHandler.handle(request, response);
            return;
        }

        notFound(response, path);
    }

    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        // No PUT endpoints yet; handlers added in later tasks.
        notFound(response, pathOf(request));
    }

    public void doDelete(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {
        // No DELETE endpoints yet; handlers added in later tasks.
        notFound(response, pathOf(request));
    }

    /**
     * {@code GET /api/db/ping} — liveness plus current auth identity.
     * Returns {@code {"ok":true,"auth":"<name-or-null>"}}.
     */
    private void handlePing(HttpServletRequest request,
                            HttpServletResponse response) throws IOException {

        String name = (String) request.getAttribute("name");

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", Boolean.TRUE);
        body.put("auth", name); // null when anonymous

        JsonHttp.ok(response, body);
    }

    private void notFound(HttpServletResponse response, String path)
            throws IOException {
        JsonHttp.error(response, HttpServletResponse.SC_NOT_FOUND,
                "not_found", "No such endpoint: " + path);
    }

    private static String pathOf(HttpServletRequest request) {
        String p = request.getPathInfo();
        return p == null ? "/" : p;
    }
}
