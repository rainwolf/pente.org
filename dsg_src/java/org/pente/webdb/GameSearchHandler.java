package org.pente.webdb;

import java.io.IOException;
import java.sql.*;
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

import org.pente.webdb.dto.GameHeader;
import org.pente.webdb.dto.GameSearchRequest;
import org.pente.webdb.dto.GameSearchResponse;
import org.pente.webdb.dto.PositionStatsRequest;

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

    public GameSearchHandler(DBHandler dbHandler, GameStorer gameStorer,
                             GameVenueStorer gameVenueStorer) {
        this.dbHandler = dbHandler;
        this.gameStorer = gameStorer;
        this.gameVenueStorer = gameVenueStorer;
    }

    /** Servlet entry point: read JSON, run the search, write JSON (or error). */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {

        GameSearchRequest req =
                JsonHttp.readBody(request, response, GameSearchRequest.class);
        if (req == null) {
            return; // readBody already emitted the 4xx envelope
        }

        try {
            GameSearchResponse resp = search(req);
            JsonHttp.ok(response, resp);
        } catch (Exception e) {
            cat.error("games/search failed", e);
            JsonHttp.error(response, 500, "server_error", "game search failed");
        }
    }

    /**
     * Run the search and return its response. Split out from {@link #handle} so
     * the DB-backed test can exercise it without a servlet container.
     */
    public GameSearchResponse search(GameSearchRequest req) throws Exception {

        int game = req.game;
        int[] moves = (req.moves == null) ? NO_MOVES : req.moves;
        int limit = clampLimit(req.limit);
        int offset = Math.max(0, req.offset);
        PositionStatsRequest.Filters f = req.filters;
        String source = notEmpty(req.scope) ? req.scope : "archive";

        GameSearchResponse resp = new GameSearchResponse();

        Connection con = null;
        try {
            con = dbHandler.getConnection();

            StringBuilder from = new StringBuilder();
            StringBuilder where = new StringBuilder();
            List<Object> baseParams = new ArrayList<Object>();
            String gidCol;
            String orderCol;

            if (moves.length > 0) {
                // Position-constrained: hash the position and match pente_move.
                GridState state =
                        GridStateFactory.createGridState(game, moveDataOf(moves));
                long hash = state.getHash();
                int numMoves = moves.length;

                boolean gameLevel = needsGameTable(f);
                from.append("from pente_move m");
                if (gameLevel) {
                    from.append(", pente_game g");
                }
                where.append(
                        " where m.hash_key = ? and m.move_num = ? and m.game = ?");
                baseParams.add(Long.valueOf(hash));
                baseParams.add(Integer.valueOf(numMoves - 1));
                baseParams.add(Integer.valueOf(game));
                if (gameLevel) {
                    where.append(" and m.gid = g.gid and g.game = ?");
                    baseParams.add(Integer.valueOf(game));
                }
                appendFilters(from, where, baseParams, game, f, "m.winner");
                gidCol = "m.gid";
                orderCol = "m.play_date";
            } else {
                // Filter-only: a plain paged scan of pente_game.
                from.append("from pente_game g");
                where.append(" where g.game = ?");
                baseParams.add(Integer.valueOf(game));
                appendFilters(from, where, baseParams, game, f, "g.winner");
                gidCol = "g.gid";
                orderCol = "g.play_date";
            }

            resp.total =
                    count(con, from.toString(), where.toString(), baseParams);
            List<Long> gids = selectGids(con, gidCol, from.toString(),
                    where.toString(), orderCol, baseParams, limit, offset);
            resp.games = hydrate(con, gids, game, source);

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return resp;
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

        if (notEmpty(f.site)) {
            GameSiteData sd = gameVenueStorer.getGameSiteData(game, f.site);
            where.append(" and g.site_id = ?");
            params.add(Integer.valueOf(sd == null ? -1 : sd.getSiteID()));
            if (notEmpty(f.event)) {
                GameEventData ed =
                        gameVenueStorer.getGameEventData(game, f.event, f.site);
                where.append(" and g.event_id = ?");
                params.add(Integer.valueOf(ed == null ? -1 : ed.getEventID()));
            }
        } else if (notEmpty(f.event)) {
            // Event ids are scoped by site; without a site name an event cannot
            // resolve, so bind an impossible id (matches the stats handler).
            where.append(" and g.event_id = ?");
            params.add(Integer.valueOf(-1));
        }
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

    /**
     * Whether any filter needs the {@code pente_game} join in the position
     * query. A winner-only filter does not — {@code m.winner} is denormalized
     * onto {@code pente_move}.
     */
    private static boolean needsGameTable(PositionStatsRequest.Filters f) {
        return f != null && (notEmpty(f.player1Name) || notEmpty(f.player2Name)
                || notEmpty(f.site) || notEmpty(f.event) || notEmpty(f.round)
                || notEmpty(f.section) || notEmpty(f.afterDate)
                || notEmpty(f.beforeDate));
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

    /** Parse ISO {@code yyyy-MM-dd} (optionally with a time) to a Timestamp. */
    private static Timestamp toTimestamp(String s) {
        String t = s.trim().replace('T', ' ');
        if (t.endsWith("Z")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        if (t.length() <= 10) {
            t = t + " 00:00:00";
        }
        return Timestamp.valueOf(t);
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
