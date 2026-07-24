package org.pente.webdb;

import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.DBHandler;
import org.pente.game.GameEventData;
import org.pente.game.GameSiteData;
import org.pente.game.GameVenueStorer;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;
import org.pente.gameServer.core.DSGPlayerStorer;

import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.PositionStatsResponse;

/**
 * Endpoint logic for {@code POST /api/db/position-stats} (archive scope).
 *
 * <p>Given a position (a game/variant id plus a move list in the caller's
 * board orientation), this returns how every continuation played out across the
 * live archive: for each next move, how many games reached it and how often the
 * side to move went on to win. It reproduces the production statistics query in
 * {@code MySQLGameStorerSearcher.getSearchResults} — the canonical position hash
 * from {@link GridStateFactory}, an aggregation over {@code pente_move} grouped
 * by {@code (next_move, rotation, winner)}, and the rotation-normalization loop
 * from {@code pentedb-SPEC.md} §8.1 — but binds every SQL value as a parameter
 * rather than inlining it.
 *
 * <p>The heavy lifting lives in {@link #aggregateArchive} / {@link #buildArchiveSql}
 * so that the {@code scope="mine"} sibling added in a later task can mirror the
 * same loop against the per-player {@code webdb_move} table.
 */
public class PositionStatsHandler {

    private static Category cat =
            Category.getInstance(PositionStatsHandler.class.getName());

    /**
     * Highest real board cell. Moves are {@code 0..360} on the 19x19 grid; the
     * documented terminal sentinel is {@code 361} and anything above the board
     * is treated as terminal (see the aggregation loop).
     */
    private static final int MAX_BOARD_MOVE = 360;

    /** {@code GameData.UNKNOWN} — an undecided game. */
    private static final int WINNER_UNKNOWN = 0;

    private static final int[] NO_MOVES = new int[0];

    private final DBHandler dbHandler;
    private final GameVenueStorer gameVenueStorer;
    /** Personal ("mine") stats source; null for an archive-only handler. */
    private final MySQLWebDbStorer webDbStorer;
    /** Resolves the request's login name to a pid; null for archive-only. */
    private final DSGPlayerStorer playerStorer;

    /**
     * LRU+TTL cache for pure archive-scope responses. Never consulted or
     * populated for {@code scope="mine"}/{@code "both"} (see {@link #computeStats}).
     */
    private final StatsCache statsCache = new StatsCache();

    /**
     * Archive-only handler (no {@code scope="mine"}/{@code "both"} support).
     * Retained for the archive DB test and any caller that never needs auth.
     */
    public PositionStatsHandler(DBHandler dbHandler,
                                GameVenueStorer gameVenueStorer) {
        this(dbHandler, gameVenueStorer, null, null);
    }

    /**
     * Full handler: archive plus the authenticated {@code scope="mine"}/
     * {@code "both"} paths, which fold the caller's own {@code webdb_move} rows
     * through the same rotation-normalization loop as the archive.
     */
    public PositionStatsHandler(DBHandler dbHandler,
                                GameVenueStorer gameVenueStorer,
                                MySQLWebDbStorer webDbStorer,
                                DSGPlayerStorer playerStorer) {
        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
        this.webDbStorer = webDbStorer;
        this.playerStorer = playerStorer;
    }

    /**
     * Archive stats-cache hit count since startup — a test/metrics hook. Kept
     * on the handler (rather than exposing the package-private cache itself) so
     * {@link StatsCache} stays internal to this package.
     */
    public int cacheHitCount() {
        return statsCache.hitCount();
    }

    /**
     * Servlet entry point: read the JSON request, compute the archive-scope
     * statistics, and write the JSON response (or the standard error envelope).
     */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {

        PositionStatsRequest req =
                JsonHttp.readBody(request, response, PositionStatsRequest.class);
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
            PositionStatsResponse resp = compute(req, pid);
            JsonHttp.ok(response, resp);
        } catch (IllegalArgumentException e) {
            // Bad user input (e.g. a malformed date filter) is a 400, not a 500.
            JsonHttp.error(response, 400, "bad_request", e.getMessage());
        } catch (Exception e) {
            cat.error("position-stats failed", e);
            JsonHttp.error(response, 500, "server_error",
                    "position-stats computation failed");
        }
    }

    /** {@code scope="mine"}/{@code "both"} require an authenticated pid. */
    private static boolean needsAuth(String scope) {
        return "mine".equals(scope) || "both".equals(scope);
    }

    /**
     * Compute next-move statistics for the request against the whole archive.
     */
    public PositionStatsResponse computeArchive(PositionStatsRequest req)
            throws Exception {
        return computeStats(req, "archive", -1L);
    }

    /**
     * Scope-aware entry point. {@code scope} is read from the request
     * ({@code "archive"} default, {@code "mine"}, or {@code "both"}); {@code pid}
     * is only consulted for the {@code "mine"}/{@code "both"} personal fold and
     * must be a resolved, authenticated player id there.
     */
    public PositionStatsResponse compute(PositionStatsRequest req, long pid)
            throws Exception {
        String scope = notEmpty(req.scope) ? req.scope : "archive";
        return computeStats(req, scope, pid);
    }

    private PositionStatsResponse computeStats(PositionStatsRequest req,
                                               String scope, long pid)
            throws Exception {

        int game = req.game;
        int[] moves = (req.moves == null) ? NO_MOVES : req.moves;
        int numMoves = moves.length;

        PositionStatsResponse resp = new PositionStatsResponse();
        resp.nextMoves = new ArrayList<PositionStatsResponse.NextMove>();

        // An empty move list has no position to hash. Non-Go positions always
        // begin with the center stone, so numMoves >= 1 in practice; the guard
        // just keeps getHash() from indexing an empty state.
        if (numMoves == 0) {
            return resp; // totalGames=0, rotation=0, nextMoves=[]
        }

        GridState state =
                GridStateFactory.createGridState(game, moveDataOf(moves));
        long hash = state.getHash();
        resp.rotation = state.getRotation();
        int currentPlayer = (numMoves % 2) + 1;

        boolean wantArchive = !"mine".equals(scope);              // archive or both
        boolean wantMine = "mine".equals(scope) || "both".equals(scope);

        // Cache pure archive-scope responses only. "mine" and "both" never read
        // or populate the cache: "mine" has no archive half, and "both" folds
        // the archive and personal rows into a single accumulator before
        // finalizing, so there is no standalone archive response to reuse. The
        // key pins the caller's rotation (see StatsCache) — same canonical hash,
        // different orientation, is a genuinely different response.
        boolean archiveOnly = wantArchive && !wantMine;
        StatsCache.Key cacheKey = null;
        if (archiveOnly) {
            cacheKey = StatsCache.keyOf(game, hash, numMoves, resp.rotation,
                    req.filters);
            PositionStatsResponse cached = statsCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        Acc acc = new Acc();

        if (wantArchive) {
            Connection con = null;
            try {
                con = dbHandler.getConnection();
                aggregateArchive(con, state, hash, numMoves, game, currentPlayer,
                        req.filters, acc);
            } finally {
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        }
        if (wantMine) {
            aggregatePersonal(pid, state, hash, numMoves, game, currentPlayer, acc);
        }

        finalizeStats(acc, resp);
        if (archiveOnly) {
            statsCache.put(cacheKey, resp);
        }
        return resp;
    }

    /**
     * Mutable accumulator shared across the archive and personal folds: one
     * running total plus a per-local-move aggregate. Both sources feed it
     * through {@link #foldRow}, and {@link #finalizeStats} turns it into the
     * response.
     */
    private static final class Acc {
        final Map<Integer, PositionStatsResponse.NextMove> byMove =
                new HashMap<Integer, PositionStatsResponse.NextMove>();
        long totalGames = 0;
    }

    /**
     * Run the archive statistics query and fold each row into {@code acc}.
     * Kept separate from {@link #buildArchiveSql} and from the request handling
     * so the per-player ("mine") path reuses the exact aggregation via
     * {@link #foldRow}.
     */
    private void aggregateArchive(Connection con, GridState state, long hash,
                                  int numMoves, int game, int currentPlayer,
                                  PositionStatsRequest.Filters filters,
                                  Acc acc) throws Exception {

        List<Object> params = new ArrayList<Object>();
        String sql = buildArchiveSql(game, hash, numMoves, filters, params);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(sql);
            bind(stmt, params);
            rs = stmt.executeQuery();
            while (rs.next()) {
                foldRow(acc, state, numMoves, currentPlayer,
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getLong(4));
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    /**
     * Fold the caller's own {@code webdb_move} rows into {@code acc}. The storer
     * returns rows already grouped as {@code {next_move, rotation, winner,
     * count}} — the same shape as the archive query — so they run through the
     * identical {@link #foldRow} rotate/skip/accumulate loop, with no personal
     * SQL or filtering divergence.
     */
    private void aggregatePersonal(long pid, GridState state, long hash,
                                   int numMoves, int game, int currentPlayer,
                                   Acc acc) throws Exception {

        List<long[]> rows = webDbStorer.positionStats(pid, hash, numMoves - 1, game);
        for (long[] row : rows) {
            foldRow(acc, state, numMoves, currentPlayer,
                    (int) row[0], (int) row[1], (int) row[2], row[3]);
        }
    }

    /**
     * The canonical single-row fold shared by archive and personal sources.
     * Accumulates the total, maps the stored next move back into the caller's
     * orientation, and (for decided games) tallies games/wins for that move.
     */
    private void foldRow(Acc acc, GridState state, int numMoves, int currentPlayer,
                         int nextMove, int rotation, int winner, long count) {

        // Every matching game counts toward the total, including games that
        // ended here (next_move == 361) and undecided games.
        acc.totalGames += count;

        // A candidate move must be a real board cell (0..360). The terminal
        // sentinel (361) and any out-of-board value (rare corrupt/legacy rows,
        // e.g. next_move == 3528) are counted toward the total only, never
        // emitted as a continuation — this keeps every returned move in 0..360.
        if (nextMove < 0 || nextMove > MAX_BOARD_MOVE) {
            return;
        }

        // Map the stored next move back into the caller's orientation.
        int localMove = (numMoves == 0)
                ? state.rotateFirstMove(nextMove, rotation)
                : state.rotateMoveToLocalRotation(nextMove, rotation);

        if (winner == WINNER_UNKNOWN) {
            return; // only decided games contribute to a move's stats
        }

        Integer key = Integer.valueOf(localMove);
        PositionStatsResponse.NextMove nm = acc.byMove.get(key);
        if (nm == null) {
            nm = new PositionStatsResponse.NextMove();
            nm.move = localMove;
            acc.byMove.put(key, nm);
        }
        nm.games += count;
        if (currentPlayer == winner) {
            nm.wins += count;
        }
    }

    /** Turn the accumulator into the response (per-move winPct, sort, totals). */
    private void finalizeStats(Acc acc, PositionStatsResponse resp) {
        long sumWins = 0;
        long sumGames = 0;
        List<PositionStatsResponse.NextMove> list =
                new ArrayList<PositionStatsResponse.NextMove>(acc.byMove.values());
        for (PositionStatsResponse.NextMove nm : list) {
            nm.winPct = winPct(nm.wins, nm.games);
            sumWins += nm.wins;
            sumGames += nm.games;
        }
        Collections.sort(list, BY_GAMES_DESC);

        resp.nextMoves = list;
        resp.totalGames = acc.totalGames;
        resp.totalWinPct = winPct(sumWins, sumGames);
    }

    /**
     * Build the archive aggregation SQL with every value bound. Filter joins and
     * predicates mirror {@code MySQLGameStorerSearcher.initFilterOptions}: a join
     * to {@code pente_game g} (with {@code m.gid = g.gid}) is added only when a
     * game-level filter is present, and player-name filters join {@code player}.
     * Appends the bind values to {@code params} in positional order.
     */
    public String buildArchiveSql(int game, long hash, int numMoves,
                                  PositionStatsRequest.Filters f,
                                  List<Object> params) throws Exception {

        boolean p1 = f != null && notEmpty(f.player1Name);
        boolean p2 = f != null && notEmpty(f.player2Name);

        boolean haveSite = f != null && notEmpty(f.site);
        boolean haveEvent = f != null && notEmpty(f.event);
        boolean haveRound = f != null && notEmpty(f.round);
        boolean haveSection = f != null && notEmpty(f.section);
        boolean haveAfter = f != null && notEmpty(f.afterDate);
        boolean haveBefore = f != null && notEmpty(f.beforeDate);
        boolean haveWinner = f != null && f.winner != WINNER_UNKNOWN;

        // Resolve venue names to ids. An UNRESOLVABLE name causes the predicate
        // to be OMITTED (the filter is ignored), matching production — never
        // bound to an impossible id (which would silently return zero rows). F9.
        Integer siteId = null;
        if (haveSite) {
            GameSiteData sd = gameVenueStorer.getGameSiteData(game, f.site);
            if (sd != null) {
                siteId = Integer.valueOf(sd.getSiteID());
            }
        }
        Integer eventId = null;
        // Event ids are scoped by site in the venue tree; a resolvable site name
        // is required to resolve one.
        if (haveEvent && siteId != null) {
            GameEventData ed =
                    gameVenueStorer.getGameEventData(game, f.event, f.site);
            if (ed != null) {
                eventId = Integer.valueOf(ed.getEventID());
            }
        }
        boolean useSite = siteId != null;
        boolean useEvent = eventId != null;

        // Always join pente_game so the private guard (and any game-level filter)
        // applies; production pays this join cost too. F1.
        StringBuilder from = new StringBuilder("from pente_move m, pente_game g");
        if (p1) {
            from.append(", player p1");
        }
        if (p2) {
            from.append(", player p2");
        }

        StringBuilder where = new StringBuilder(
                " where m.hash_key = ? and m.move_num = ? and m.game = ?");
        params.add(Long.valueOf(hash));
        params.add(Integer.valueOf(numMoves - 1));
        params.add(Integer.valueOf(game));

        where.append(" and m.gid = g.gid and g.game = ?");
        params.add(Integer.valueOf(game));

        // Private archive games are never included in position statistics. F1.
        where.append(" and g.private = 'N'");

        if (p1) {
            appendPlayerPredicate(where, params, "p1", f.player1Seat, f.player1Name);
        }
        if (p2) {
            appendPlayerPredicate(where, params, "p2", f.player2Seat, f.player2Name);
        }

        if (useSite) {
            where.append(" and g.site_id = ?");
            params.add(siteId);
        }
        if (useEvent) {
            where.append(" and g.event_id = ?");
            params.add(eventId);
        }
        if (haveRound) {
            where.append(" and g.round = ?");
            params.add(f.round);
        }
        if (haveSection) {
            where.append(" and g.section = ?");
            params.add(f.section);
        }
        if (haveAfter) {
            where.append(" and g.play_date > ?");
            params.add(toTimestamp(f.afterDate));
        }
        if (haveBefore) {
            where.append(" and g.play_date < ?");
            params.add(toTimestamp(f.beforeDate));
        }
        if (haveWinner) {
            where.append(" and m.winner = ?");
            params.add(Integer.valueOf(f.winner));
        }

        return "select m.next_move, m.rotation, m.winner, count(*) "
                + from + where
                + " group by m.next_move, m.rotation, m.winner";
    }

    /**
     * Append a player-name predicate mirroring the searcher's seat handling:
     * seat 1/2 pins the pid to that seat, {@code SEAT_ALL} matches either seat.
     * The name is bound lower-cased against {@code player.name_lower}.
     */
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

    /** {@code round(1000 * wins / games) / 10}; 0 when there are no games. */
    private static double winPct(long wins, long games) {
        if (games <= 0) {
            return 0.0;
        }
        return Math.round(1000.0 * wins / games) / 10.0;
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
            } catch (SQLException ignore) {
                // best effort
            }
        }
    }

    private static void closeQuietly(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignore) {
                // best effort
            }
        }
    }

    /** Most-played continuations first; ties broken by move for determinism. */
    private static final Comparator<PositionStatsResponse.NextMove> BY_GAMES_DESC =
            new Comparator<PositionStatsResponse.NextMove>() {
                public int compare(PositionStatsResponse.NextMove a,
                                   PositionStatsResponse.NextMove b) {
                    if (a.games != b.games) {
                        return a.games > b.games ? -1 : 1;
                    }
                    return a.move - b.move;
                }
            };
}
