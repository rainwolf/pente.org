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
import org.pente.gameServer.server.ActivityLogger;

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

    private static final long serialVersionUID = 3559299031987038680L;

    private static Category cat =
            Category.getInstance(WebDbApiServlet.class.getName());

    /** Optional capabilities advertised on {@code /ping} for client gating. */
    static final List<String> FEATURES = List.of(
            "excludeTimeouts", "liveOrTurnBased", "badges", "blocked");

    /**
     * Context attribute names under which {@code DSGContextListener}
     * publishes the read-only replica ({@code dsg_ro}) {@link DBHandler} and
     * the {@link GameStorer} built on it.
     */
    public static final String RO_DB_ATTR = DBHandler.class.getName() + ".ro";
    public static final String RO_GAME_STORER_ATTR =
            GameStorer.class.getName() + ".ro";

    private static DBHandler dbHandler;
    /** Replica handler for heavy archive reads; primary if absent. */
    private static DBHandler dbHandlerRo;
    /** Archive game loader on the replica; primary storer if absent. */
    private static GameStorer gameStorerRo;
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

            // Heavy archive reads go to the replica. Writes, auth/access
            // checks and the personal webdb_* tables stay on the primary
            // (read-after-write; replica lag). Fall back to the primary if
            // the replica handler was not published.
            dbHandlerRo = (DBHandler) ctx.getAttribute(RO_DB_ATTR);
            gameStorerRo = (GameStorer) ctx.getAttribute(RO_GAME_STORER_ATTR);
            if (dbHandlerRo == null || gameStorerRo == null) {
                cat.warn("read-only replica handler not available in the "
                        + "servlet context; webdb archive reads fall back "
                        + "to the primary database");
                dbHandlerRo = dbHandler;
                gameStorerRo = gameStorer;
            }

            // Personal collection + analyses persistence (Task 5). The auth'd
            // "my games" / "my analyses" / scope endpoints build on this.
            webDbStorer = new MySQLWebDbStorer(dbHandler);

            positionStatsHandler = new PositionStatsHandler(
                    dbHandler, dbHandlerRo, gameVenueStorer, webDbStorer, dsgPlayerStorer);
            gameSearchHandler = new GameSearchHandler(
                    dbHandler, dbHandlerRo, gameStorerRo, gameVenueStorer, webDbStorer,
                    dsgPlayerStorer);
            gameLoadHandler = new GameLoadHandler(gameStorerRo);
            // Legacy viewDb blocked parity (B5); null (not published) means
            // requests are never blocked.
            ActivityLogger activityLogger = (ActivityLogger)
                    ctx.getAttribute(ActivityLogger.class.getName());
            positionStatsHandler.setActivityLogger(activityLogger);
            gameSearchHandler.setActivityLogger(activityLogger);
            gameLoadHandler.setActivityLogger(activityLogger);
            venuesHandler = new VenuesHandler(gameVenueStorer);
            playersHandler = new PlayersHandler(dbHandlerRo);
            collectionHandler =
                    new CollectionHandler(webDbStorer, dsgPlayerStorer);
            analysesHandler =
                    new AnalysesHandler(webDbStorer, dsgPlayerStorer);

        } catch (Throwable t) {
            cat.error("Problem in init()", t);
        }
    }

    /**
     * Central subscriber-only gate: every request, whatever its HTTP method
     * or path, must pass {@link WebDbAuth#requireDbAccess} before it reaches
     * any {@code doXxx} dispatch — except {@code GET}/{@code HEAD /ping},
     * which stays open and reports {@code dbAccess}. Gating here (not per
     * endpoint) means a newly added endpoint cannot forget the check.
     */
    @Override
    protected void service(HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);
        String method = request.getMethod();
        boolean open = "/ping".equals(path)
                && ("GET".equals(method) || "HEAD".equals(method));

        if (!open) {
            response.setHeader("Cache-Control", "no-store");
            try {
                if (!WebDbAuth.requireDbAccess(request, response,
                        dsgPlayerStorer)) {
                    return;
                }
            } catch (Throwable t) {
                serverError(response, path, t);
                return;
            }
        }
        super.service(request, response);
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);
        setCacheControl(response, path);

        try {
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
        } catch (Throwable t) {
            serverError(response, path, t);
        }
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        try {
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
        } catch (Throwable t) {
            serverError(response, path, t);
        }
    }

    public void doPut(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        try {
            if (path.startsWith("/analyses/")) {
                analysesHandler.handleUpdate(request, response,
                        path.substring("/analyses/".length()));
                return;
            }

            notFound(response, path);
        } catch (Throwable t) {
            serverError(response, path, t);
        }
    }

    public void doDelete(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        String path = pathOf(request);

        try {
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
        } catch (Throwable t) {
            serverError(response, path, t);
        }
    }

    /**
     * {@code GET /api/db/ping} — liveness plus current auth identity.
     * Returns {@code {"ok":true,"auth":"<name-or-null>","dbAccess":<bool>,
     * "features":["excludeTimeouts","liveOrTurnBased","badges","blocked"]}};
     * {@code dbAccess} is false when anonymous. {@code features} lists the
     * optional capabilities this server supports (see {@link #FEATURES});
     * older servers omit it, which clients treat as none. Not gated (see
     * {@link #service}).
     */
    private void handlePing(HttpServletRequest request,
                            HttpServletResponse response) throws IOException {

        String name = (String) request.getAttribute("name");
        boolean dbAccess = WebDbAuth.hasDbAccess(
                WebDbAuth.loadSessionPlayer(request, dsgPlayerStorer),
                System.currentTimeMillis());

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", Boolean.TRUE);
        body.put("auth", name); // null when anonymous
        body.put("dbAccess", dbAccess);
        body.put("features", FEATURES);

        JsonHttp.ok(response, body);
    }

    private void notFound(HttpServletResponse response, String path)
            throws IOException {
        JsonHttp.error(response, HttpServletResponse.SC_NOT_FOUND,
                "not_found", "No such endpoint: " + path);
    }

    /**
     * Cache-Control for GET responses: the venue tree is large and rarely
     * changes, so it may be cached briefly ({@code max-age=300}); every other
     * GET carries live per-caller/auth-scoped data and must not be cached
     * ({@code no-store}). Set before any body is written.
     */
    private static void setCacheControl(HttpServletResponse response, String path) {
        if ("/venues".equals(path)) {
            // private: the response is now subscriber-gated, so shared
            // caches must not replay it to unauthenticated callers.
            response.setHeader("Cache-Control", "private, max-age=300");
        } else {
            response.setHeader("Cache-Control", "no-store");
        }
    }

    /**
     * Last-resort handler for anything a per-endpoint {@code catch} did not
     * already convert to an error envelope (e.g. an unchecked exception escaping
     * the routing layer). Logs the full stack via log4j and emits a bare
     * {@code 500} envelope with no exception detail in the body, so internals
     * never leak to the client. Best-effort: if the response is already
     * committed, the write simply fails and is swallowed.
     */
    private void serverError(HttpServletResponse response, String path,
                             Throwable t) {
        cat.error("Unhandled error serving " + path, t);
        try {
            JsonHttp.error(response, 500, "server_error", null);
        } catch (Throwable ignore) {
            // Response likely already committed; nothing more we can do.
        }
    }

    private static String pathOf(HttpServletRequest request) {
        String p = request.getPathInfo();
        return p == null ? "/" : p;
    }
}
