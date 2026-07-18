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
    private static MySQLWebDbStorer webDbStorer;

    private static PositionStatsHandler positionStatsHandler;
    private static GameSearchHandler gameSearchHandler;
    private static GameLoadHandler gameLoadHandler;
    private static VenuesHandler venuesHandler;
    private static PlayersHandler playersHandler;
    private static CollectionHandler collectionHandler;
    private static AnalysesHandler analysesHandler;

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

            // Personal collection + analyses persistence (Task 5). The auth'd
            // "my games" / "my analyses" / scope endpoints build on this.
            webDbStorer = new MySQLWebDbStorer(dbHandler);

            positionStatsHandler = new PositionStatsHandler(
                    dbHandler, gameVenueStorer, webDbStorer, dsgPlayerStorer);
            gameSearchHandler = new GameSearchHandler(
                    dbHandler, gameStorer, gameVenueStorer, webDbStorer,
                    dsgPlayerStorer);
            gameLoadHandler = new GameLoadHandler(gameStorer);
            venuesHandler = new VenuesHandler(gameVenueStorer);
            playersHandler = new PlayersHandler(dbHandler);
            collectionHandler =
                    new CollectionHandler(webDbStorer, dsgPlayerStorer);
            analysesHandler =
                    new AnalysesHandler(webDbStorer, dsgPlayerStorer);

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
        if ("/venues".equals(path)) {
            venuesHandler.handle(request, response);
            return;
        }
        if ("/players".equals(path)) {
            playersHandler.handle(request, response);
            return;
        }
        if (path.startsWith("/games/")) {
            gameLoadHandler.handle(request, response,
                    path.substring("/games/".length()));
            return;
        }
        if ("/collection".equals(path)) {
            collectionHandler.handleList(request, response);
            return;
        }
        if (path.startsWith("/collection/")) {
            collectionHandler.handleGet(request, response,
                    path.substring("/collection/".length()));
            return;
        }
        if ("/analyses".equals(path)) {
            analysesHandler.handleList(request, response);
            return;
        }
        if (path.startsWith("/analyses/")) {
            analysesHandler.handleGet(request, response,
                    path.substring("/analyses/".length()));
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
        if ("/games/search".equals(path)) {
            gameSearchHandler.handle(request, response);
            return;
        }
        if ("/collection/import".equals(path)) {
            collectionHandler.handleImport(request, response);
            return;
        }
        if ("/analyses".equals(path)) {
            analysesHandler.handleCreate(request, response);
            return;
        }

        notFound(response, path);
    }

    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        if (path.startsWith("/analyses/")) {
            analysesHandler.handleUpdate(request, response,
                    path.substring("/analyses/".length()));
            return;
        }

        notFound(response, path);
    }

    public void doDelete(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        if (path.startsWith("/collection/")) {
            collectionHandler.handleDelete(request, response,
                    path.substring("/collection/".length()));
            return;
        }
        if (path.startsWith("/analyses/")) {
            analysesHandler.handleDelete(request, response,
                    path.substring("/analyses/".length()));
            return;
        }

        notFound(response, path);
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
