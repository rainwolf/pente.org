package org.pente.webdb;

import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.DBHandler;
import org.pente.game.GameData;
import org.pente.game.GameEventData;
import org.pente.game.GameSiteData;
import org.pente.game.GameStorer;
import org.pente.game.GameVenueStorer;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;
import org.pente.game.MySQLPenteGameStorer;
import org.pente.gameServer.core.DSGPlayerStorer;

import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.WebDbGameData;

/**
 * Endpoint logic for {@code POST /api/db/games/search} (archive scope).
 *
 * <p>Two query shapes share one response ({@link GameSearchResponse}):
 * <ul>
 *   <li><b>position-constrained</b> (when {@code moves} is non-empty) — mirrors
 *       {@code MySQLGameStorerSearcher.getMatchingGames}: the canonical position
 *       hash from {@link GridStateFactory} drives a paged
 *       {@code select m.gid ... where hash_key=? and move_num=? order by
 *       play_date desc} against {@code pente_move}, with a {@code count(*)} twin
 *       for the total, then {@link MySQLPenteGameStorer#loadGames} hydrates the
 *       headers.</li>
 *   <li><b>filter-only</b> (no {@code moves}) — a paged {@code select g.gid from
 *       pente_game g <filters> order by g.play_date desc} plus its {@code
 *       count(*)} twin.</li>
 * </ul>
 *
 * <p>Every SQL value is bound as a parameter (venue names are resolved to ids
 * via the venue tree, exactly as {@code PositionStatsHandler} does).
 */
public class GameSearchHandler {

    private static Category cat =
            Category.getInstance(GameSearchHandler.class.getName());

    /** Default page size when the request omits/zeroes {@code limit}. */
    private static final int DEFAULT_LIMIT = 25;
    /** Hard cap so a caller cannot ask the DB to hydrate an unbounded page. */
    private static final int MAX_LIMIT = 100;

    private static final int[] NO_MOVES = new int[0];

    private final DBHandler dbHandler;
    private final GameStorer gameStorer;
    private final GameVenueStorer gameVenueStorer;
    /** Personal ("mine") collection source; null for an archive-only handler. */
    private final MySQLWebDbStorer webDbStorer;
    /** Resolves the request's login name to a pid; null for archive-only. */
    private final DSGPlayerStorer playerStorer;

    /**
     * Archive-only handler (no {@code scope="mine"}/{@code "both"} support).
     * Retained for the archive DB test and any caller that never needs auth.
     */
    public GameSearchHandler(DBHandler dbHandler, GameStorer gameStorer,
                             GameVenueStorer gameVenueStorer) {
        this(dbHandler, gameStorer, gameVenueStorer, null, null);
    }

    /**
     * Full handler: archive plus the authenticated {@code scope="mine"}/
     * {@code "both"} listings over the caller's personal collection.
     */
    public GameSearchHandler(DBHandler dbHandler, GameStorer gameStorer,
                             GameVenueStorer gameVenueStorer,
                             MySQLWebDbStorer webDbStorer,
                             DSGPlayerStorer playerStorer) {
        this.dbHandler = dbHandler;
        this.gameStorer = gameStorer;
        this.gameVenueStorer = gameVenueStorer;
        this.webDbStorer = webDbStorer;
        this.playerStorer = playerStorer;
    }

    /** Servlet entry point: read JSON, run the search, write JSON (or error). */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {

        GameSearchRequest req =
                JsonHttp.readBody(request, response, GameSearchRequest.class);
        if (req == null) {
            return; // readBody already emitted the 4xx envelope
        }

        String scope = notEmpty(req.scope) ? req.scope : "archive";
        long pid = -1L;
        if (needsAuth(scope)) {
            pid = WebDbAuth.requirePid(request, response, playerStorer);
            if (pid < 0) {
                return; // 401 already emitted
            }
        }

        try {
            GameSearchResponse resp = search(req, pid);
            JsonHttp.ok(response, resp);
        } catch (IllegalArgumentException e) {
            // Bad user input (e.g. a malformed date filter) is a 400, not a 500.
            JsonHttp.error(response, 400, "bad_request", e.getMessage());
        } catch (Exception e) {
            cat.error("games/search failed", e);
            JsonHttp.error(response, 500, "server_error", "game search failed");
        }
    }

    /** {@code scope="mine"}/{@code "both"} require an authenticated pid. */
    private static boolean needsAuth(String scope) {
        return "mine".equals(scope) || "both".equals(scope);
    }

    /**
     * Archive-scope search. Split out from {@link #handle} so the DB-backed
     * test can exercise it without a servlet container.
     */
    public GameSearchResponse search(GameSearchRequest req) throws Exception {
        return archiveSearch(req);
    }

    /**
     * Scope-aware search. {@code scope} is read from the request
     * ({@code "archive"} default, {@code "mine"}, {@code "both"}); {@code pid}
     * is consulted only for the personal-collection paths and must be a
     * resolved, authenticated player id there. For {@code "both"} the archive
     * page is returned first, then the caller's own games are appended (each
     * header self-describes via its {@code source} field), and {@code total} is
     * the sum of the two counts.
     */
    public GameSearchResponse search(GameSearchRequest req, long pid)
            throws Exception {

        String scope = notEmpty(req.scope) ? req.scope : "archive";
        if ("mine".equals(scope)) {
            return mineSearch(req, pid);
        }
        if ("both".equals(scope)) {
            return bothSearch(req, pid);
        }
        return archiveSearch(req);
    }

    /**
     * scope="both": ONE combined page over the virtual concatenation
     * {@code [archive rows...][personal rows...]}. The requested
     * {@code (offset, limit)} window is filled from the archive first; if the
     * archive is exhausted within that window, the remainder is topped up from
     * the caller's personal games. The returned page is therefore never larger
     * than {@code limit}, and {@code total} is the sum of the two source counts.
     *
     * <p>(Previously each source was paged independently with the same
     * offset/limit and then concatenated, which returned up to {@code 2 * limit}
     * rows and skipped/duplicated games while paging — F8.)
     */
    private GameSearchResponse bothSearch(GameSearchRequest req, long pid)
            throws Exception {

        int game = req.game;
        int limit = clampLimit(req.limit);
        int offset = Math.max(0, req.offset);

        // Archive page at (offset, limit); archive.total is the archive count.
        GameSearchResponse archive = archiveSearch(req);
        long archiveTotal = archive.total;
        long mineTotal = webDbStorer.countGames(pid, game);

        GameSearchResponse both = new GameSearchResponse();
        both.total = archiveTotal + mineTotal;

        List<GameHeader> page = new ArrayList<GameHeader>(archive.games);
        int remaining = limit - page.size();
        if (remaining > 0) {
            // Personal rows occupy combined indices [archiveTotal, ...); the
            // first personal row visible in this window is at that offset.
            int mineStart = (int) Math.max(0L, offset - archiveTotal);
            for (WebDbGameData g :
                    webDbStorer.listGames(pid, game, mineStart, remaining)) {
                if (page.size() >= limit) {
                    break;
                }
                page.add(GameHeader.fromWebDb(g));
            }
        }
        both.games = page;
        return both;
    }

    /**
     * List the caller's own games for one variant (newest first), as headers
     * with {@code source="mine"} and {@code gid=wgid}. Personal listings are a
     * plain variant scan — position and venue/player filters are not applied
     * (the collection storer exposes only variant + paging).
     */
    private GameSearchResponse mineSearch(GameSearchRequest req, long pid)
            throws Exception {

        int game = req.game;
        int limit = clampLimit(req.limit);
        int offset = Math.max(0, req.offset);

        GameSearchResponse resp = new GameSearchResponse();
        resp.total = webDbStorer.countGames(pid, game);

        List<WebDbGameData> rows = webDbStorer.listGames(pid, game, offset, limit);
        List<GameHeader> out = new ArrayList<GameHeader>();
        for (WebDbGameData g : rows) {
            out.add(GameHeader.fromWebDb(g));
        }
        resp.games = out;
        return resp;
    }

    private GameSearchResponse archiveSearch(GameSearchRequest req)
            throws Exception {

        int game = req.game;
        int[] moves = (req.moves == null) ? NO_MOVES : req.moves;
        int limit = clampLimit(req.limit);
        int offset = Math.max(0, req.offset);
        String source = "archive";

        ArchiveQuery q = buildArchive(game, moves, req.filters);

        GameSearchResponse resp = new GameSearchResponse();
        Connection con = null;
        try {
            con = dbHandler.getConnection();
            resp.total = count(con, q.from, q.where, q.params);
            List<Long> gids = selectGids(con, q.gidCol, q.from, q.where,
                    q.orderCol, q.params, limit, offset);
            resp.games = hydrate(con, gids, game, source);
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return resp;
    }

    /** Assembled archive query: shared FROM/WHERE, id/order columns, bind params. */
    static final class ArchiveQuery {
        final String from;
        final String where;
        final String gidCol;
        final String orderCol;
        final List<Object> params;

        ArchiveQuery(String from, String where, String gidCol, String orderCol,
                     List<Object> params) {
            this.from = from;
            this.where = where;
            this.gidCol = gidCol;
            this.orderCol = orderCol;
            this.params = params;
        }
    }

    /**
     * Build the archive FROM/WHERE (and bind params) for a request. Both query
     * shapes ALWAYS join {@code pente_game g} and append {@code g.private = 'N'}
     * so private archive games are never returned by the public search endpoint
     * — this mirrors production {@code MySQLGameStorerSearcher}, which always
     * appends the same guard (F1). The position-constrained shape pays the
     * {@code pente_game} join cost unconditionally, exactly as production does.
     */
    private ArchiveQuery buildArchive(int game, int[] moves,
                                      PositionStatsRequest.Filters f)
            throws Exception {

        StringBuilder from = new StringBuilder();
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        String gidCol;
        String orderCol;

        if (moves.length > 0) {
            // Position-constrained: hash the position and match pente_move. We
            // always join pente_game so the private guard (and any game-level
            // filter) can be applied; production pays this join cost too.
            GridState state =
                    GridStateFactory.createGridState(game, moveDataOf(moves));
            long hash = state.getHash();
            int numMoves = moves.length;

            from.append("from pente_move m, pente_game g");
            where.append(
                    " where m.hash_key = ? and m.move_num = ? and m.game = ?");
            params.add(Long.valueOf(hash));
            params.add(Integer.valueOf(numMoves - 1));
            params.add(Integer.valueOf(game));
            where.append(" and m.gid = g.gid and g.game = ?");
            params.add(Integer.valueOf(game));
            appendFilters(from, where, params, game, f, "m.winner");
            gidCol = "m.gid";
            orderCol = "m.play_date";
        } else {
            // Filter-only: a plain paged scan of pente_game.
            from.append("from pente_game g");
            where.append(" where g.game = ?");
            params.add(Integer.valueOf(game));
            appendFilters(from, where, params, game, f, "g.winner");
            gidCol = "g.gid";
            orderCol = "g.play_date";
        }

        // Private archive games are never exposed by the public search endpoint.
        where.append(" and g.private = 'N'");

        return new ArchiveQuery(from.toString(), where.toString(), gidCol,
                orderCol, params);
    }

    /**
     * Test seam: the archive SELECT a request would run, so tests can assert the
     * private-visibility guard is present without inserting into the read-only
     * archive tables. Not used by the production request paths.
     */
    public String archiveSqlForTest(GameSearchRequest req) throws Exception {
        int[] moves = (req.moves == null) ? NO_MOVES : req.moves;
        ArchiveQuery q = buildArchive(req.game, moves, req.filters);
        return "select " + q.gidCol + " " + q.from + q.where;
    }

    /** {@code select count(*)} over the shared FROM/WHERE (no paging). */
    private long count(Connection con, String from, String where,
                       List<Object> params) throws SQLException {
        String sql = "select count(*) " + from + where;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(sql);
            bind(stmt, params);
            rs = stmt.executeQuery();
            return rs.next() ? rs.getLong(1) : 0L;
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    /** The paged {@code select <gidCol> ... order by <orderCol> desc}. */
    private List<Long> selectGids(Connection con, String gidCol, String from,
                                  String where, String orderCol,
                                  List<Object> baseParams, int limit, int offset)
            throws SQLException {

        String sql = "select " + gidCol + " " + from + where
                + " order by " + orderCol + " desc limit ? offset ?";
        List<Object> params = new ArrayList<Object>(baseParams);
        params.add(Integer.valueOf(limit));
        params.add(Integer.valueOf(offset));

        List<Long> gids = new ArrayList<Long>();
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(sql);
            bind(stmt, params);
            rs = stmt.executeQuery();
            while (rs.next()) {
                gids.add(Long.valueOf(rs.getLong(1)));
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
        return gids;
    }

    /**
     * Batch-hydrate the gid page into headers via the production
     * {@code loadGames}, restoring the gid list's (play_date desc) order —
     * {@code loadGames} itself returns rows ordered by gid.
     */
    private List<GameHeader> hydrate(Connection con, List<Long> gids, int game,
                                     String source) throws Exception {
        List<GameHeader> out = new ArrayList<GameHeader>();
        if (gids.isEmpty()) {
            return out;
        }
        List<GameData> loaded =
                ((MySQLPenteGameStorer) gameStorer).loadGames(con, gids);
        Map<Long, GameData> byId = new HashMap<Long, GameData>();
        for (GameData gd : loaded) {
            byId.put(Long.valueOf(gd.getGameID()), gd);
        }
        for (Long gid : gids) {
            GameData gd = byId.get(gid);
            if (gd != null) {
                out.add(GameHeader.from(gd, game, source));
            }
        }
        return out;
    }

    /**
     * Append the venue/player/date/winner predicates, adding any player-table
     * joins to {@code from}. Mirrors {@code PositionStatsHandler.buildArchiveSql}
     * (and, through it, {@code MySQLGameStorerSearcher.initFilterOptions}); every
     * value is bound. {@code winnerCol} is the winner column for the base table
     * ({@code m.winner} for the position query, {@code g.winner} otherwise).
     */
    private void appendFilters(StringBuilder from, StringBuilder where,
                               List<Object> params, int game,
                               PositionStatsRequest.Filters f, String winnerCol)
            throws Exception {

        if (f == null) {
            return;
        }

        boolean p1 = notEmpty(f.player1Name);
        boolean p2 = notEmpty(f.player2Name);
        if (p1) {
            from.append(", player p1");
        }
        if (p2) {
            from.append(", player p2");
        }
        if (p1) {
            appendPlayerPredicate(where, params, "p1", f.player1Seat, f.player1Name);
        }
        if (p2) {
            appendPlayerPredicate(where, params, "p2", f.player2Seat, f.player2Name);
        }

        // Venue filters: an UNRESOLVABLE site/event name causes the predicate to
        // be OMITTED (the filter is ignored), matching production — never bound
        // to an impossible id (which would silently return zero rows). F9.
        if (notEmpty(f.site)) {
            GameSiteData sd = gameVenueStorer.getGameSiteData(game, f.site);
            if (sd != null) {
                where.append(" and g.site_id = ?");
                params.add(Integer.valueOf(sd.getSiteID()));
                if (notEmpty(f.event)) {
                    GameEventData ed =
                            gameVenueStorer.getGameEventData(game, f.event, f.site);
                    if (ed != null) {
                        where.append(" and g.event_id = ?");
                        params.add(Integer.valueOf(ed.getEventID()));
                    }
                    // Unresolvable event name: omit the event predicate.
                }
            }
            // Unresolvable site name: omit the site (and any event) predicate.
        }
        // An event without a site cannot resolve; production omits it, so do we.
        if (notEmpty(f.round)) {
            where.append(" and g.round = ?");
            params.add(f.round);
        }
        if (notEmpty(f.section)) {
            where.append(" and g.section = ?");
            params.add(f.section);
        }
        if (notEmpty(f.afterDate)) {
            where.append(" and g.play_date > ?");
            params.add(toTimestamp(f.afterDate));
        }
        if (notEmpty(f.beforeDate)) {
            where.append(" and g.play_date < ?");
            params.add(toTimestamp(f.beforeDate));
        }
        if (f.winner != 0) {
            where.append(" and ").append(winnerCol).append(" = ?");
            params.add(Integer.valueOf(f.winner));
        }
    }

    private void appendPlayerPredicate(StringBuilder where, List<Object> params,
                                       String alias, int seat, String name) {
        if (seat == PositionStatsRequest.Filters.SEAT_ALL) {
            where.append(" and (g.player1_pid = ").append(alias)
                    .append(".pid or g.player2_pid = ").append(alias).append(".pid)");
        } else if (seat == 2) {
            where.append(" and g.player2_pid = ").append(alias).append(".pid");
        } else {
            where.append(" and g.player1_pid = ").append(alias).append(".pid");
        }
        where.append(" and ").append(alias).append(".name_lower = ?");
        params.add(name.trim().toLowerCase());
    }

    private static int clampLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static void bind(PreparedStatement stmt, List<Object> params)
            throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object o = params.get(i);
            int idx = i + 1;
            if (o instanceof Long) {
                stmt.setLong(idx, ((Long) o).longValue());
            } else if (o instanceof Integer) {
                stmt.setInt(idx, ((Integer) o).intValue());
            } else if (o instanceof Timestamp) {
                stmt.setTimestamp(idx, (Timestamp) o);
            } else {
                stmt.setString(idx, String.valueOf(o));
            }
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && s.trim().length() > 0;
    }

    /**
     * Parse ISO {@code yyyy-MM-dd} (optionally with a time) to a Timestamp,
     * interpreted as UTC so date filters round-trip with the UTC output the
     * headers emit. A malformed value throws {@link IllegalArgumentException}
     * (mapped to a 400 by {@link #handle}), never a 500. F4.
     */
    private static Timestamp toTimestamp(String s) {
        String t = s.trim().replace('T', ' ');
        if (t.endsWith("Z")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        String pattern = (t.length() <= 10) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
        SimpleDateFormat fmt = new SimpleDateFormat(pattern);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        fmt.setLenient(false);
        try {
            return new Timestamp(fmt.parse(t).getTime());
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid date format");
        }
    }

    /** Adapt a raw move array to {@link MoveData} for the hash factory. */
    private static MoveData moveDataOf(final int[] moves) {
        return new MoveData() {
            public void addMove(int move) {
                throw new UnsupportedOperationException();
            }

            public void undoMove() {
                throw new UnsupportedOperationException();
            }

            public int getMove(int num) {
                return moves[num];
            }

            public int getNumMoves() {
                return moves.length;
            }

            public int[] getMoves() {
                return moves;
            }
        };
    }

    private static void closeQuietly(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }

    private static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
