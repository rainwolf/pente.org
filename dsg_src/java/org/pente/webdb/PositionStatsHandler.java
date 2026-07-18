package org.pente.webdb;

import java.io.IOException;
import java.sql.*;
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

    public PositionStatsHandler(DBHandler dbHandler,
                                GameVenueStorer gameVenueStorer) {
        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
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

        try {
            PositionStatsResponse resp = computeArchive(req);
            JsonHttp.ok(response, resp);
        } catch (Exception e) {
            cat.error("position-stats failed", e);
            JsonHttp.error(response, 500, "server_error",
                    "position-stats computation failed");
        }
    }

    /**
     * Compute next-move statistics for the request against the whole archive.
     */
    public PositionStatsResponse computeArchive(PositionStatsRequest req)
            throws Exception {

        int game = req.game;
        int[] moves = (req.moves == null) ? NO_MOVES : req.moves;
        int numMoves = moves.length;

        PositionStatsResponse resp = new PositionStatsResponse();
        resp.nextMoves = new ArrayList<PositionStatsResponse.NextMove>();

        // An empty move list has no position to hash. Non-Go archive positions
        // always begin with the center stone, so numMoves >= 1 in practice; the
        // guard just keeps getHash() from indexing an empty state.
        if (numMoves == 0) {
            return resp; // totalGames=0, rotation=0, nextMoves=[]
        }

        GridState state =
                GridStateFactory.createGridState(game, moveDataOf(moves));
        long hash = state.getHash();
        resp.rotation = state.getRotation();
        int currentPlayer = (numMoves % 2) + 1;

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            aggregateArchive(con, state, hash, numMoves, game, currentPlayer,
                    req.filters, resp);
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return resp;
    }

    /**
     * Run the archive statistics query and fold the rows into {@code resp}.
     * Kept separate from {@link #buildArchiveSql} and from the request handling
     * so a per-player ("mine") variant can reuse this exact aggregation loop.
     */
    private void aggregateArchive(Connection con, GridState state, long hash,
                                  int numMoves, int game, int currentPlayer,
                                  PositionStatsRequest.Filters filters,
                                  PositionStatsResponse resp) throws Exception {

        List<Object> params = new ArrayList<Object>();
        String sql = buildArchiveSql(game, hash, numMoves, filters, params);

        Map<Integer, PositionStatsResponse.NextMove> byMove =
                new HashMap<Integer, PositionStatsResponse.NextMove>();
        long totalGames = 0;

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(sql);
            bind(stmt, params);
            rs = stmt.executeQuery();
            while (rs.next()) {
                int nextMove = rs.getInt(1);
                int rotation = rs.getInt(2);
                int winner = rs.getInt(3);
                long count = rs.getLong(4);

                // Every matching game counts toward the total, including games
                // that ended here (next_move == 361) and undecided games.
                totalGames += count;

                // A candidate move must be a real board cell (0..360). The
                // documented terminal sentinel (361) and any out-of-board value
                // (rare corrupt/legacy archive rows carry, e.g., next_move ==
                // 3528) are counted toward the total only, never emitted as a
                // continuation — this keeps every returned move within 0..360.
                if (nextMove < 0 || nextMove > MAX_BOARD_MOVE) {
                    continue;
                }

                // Map the stored next move back into the caller's orientation.
                int localMove = (numMoves == 0)
                        ? state.rotateFirstMove(nextMove, rotation)
                        : state.rotateMoveToLocalRotation(nextMove, rotation);

                if (winner == WINNER_UNKNOWN) {
                    continue; // only decided games contribute to a move's stats
                }

                Integer key = Integer.valueOf(localMove);
                PositionStatsResponse.NextMove nm = byMove.get(key);
                if (nm == null) {
                    nm = new PositionStatsResponse.NextMove();
                    nm.move = localMove;
                    byMove.put(key, nm);
                }
                nm.games += count;
                if (currentPlayer == winner) {
                    nm.wins += count;
                }
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }

        long sumWins = 0;
        long sumGames = 0;
        List<PositionStatsResponse.NextMove> list =
                new ArrayList<PositionStatsResponse.NextMove>(byMove.values());
        for (PositionStatsResponse.NextMove nm : list) {
            nm.winPct = winPct(nm.wins, nm.games);
            sumWins += nm.wins;
            sumGames += nm.games;
        }
        Collections.sort(list, BY_GAMES_DESC);

        resp.nextMoves = list;
        resp.totalGames = totalGames;
        resp.totalWinPct = winPct(sumWins, sumGames);
    }

    /**
     * Build the archive aggregation SQL with every value bound. Filter joins and
     * predicates mirror {@code MySQLGameStorerSearcher.initFilterOptions}: a join
     * to {@code pente_game g} (with {@code m.gid = g.gid}) is added only when a
     * game-level filter is present, and player-name filters join {@code player}.
     * Appends the bind values to {@code params} in positional order.
     */
    private String buildArchiveSql(int game, long hash, int numMoves,
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

        // Resolve venue names to ids via the same cache the searcher uses. An
        // unresolved name binds an impossible id so the query returns no rows.
        Integer siteId = null;
        if (haveSite) {
            GameSiteData sd = gameVenueStorer.getGameSiteData(game, f.site);
            siteId = Integer.valueOf(sd == null ? -1 : sd.getSiteID());
        }
        Integer eventId = null;
        if (haveEvent) {
            // Event ids are scoped by site in the venue tree; a site name is
            // required to resolve one.
            if (haveSite) {
                GameEventData ed =
                        gameVenueStorer.getGameEventData(game, f.event, f.site);
                eventId = Integer.valueOf(ed == null ? -1 : ed.getEventID());
            } else {
                eventId = Integer.valueOf(-1);
            }
        }

        boolean gameLevel = haveSite || haveEvent || haveRound || haveSection
                || haveAfter || haveBefore || p1 || p2;

        StringBuilder from = new StringBuilder("from pente_move m");
        if (gameLevel) {
            from.append(", pente_game g");
        }
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

        if (gameLevel) {
            where.append(" and m.gid = g.gid and g.game = ?");
            params.add(Integer.valueOf(game));
        }

        if (p1) {
            appendPlayerPredicate(where, params, "p1", f.player1Seat, f.player1Name);
        }
        if (p2) {
            appendPlayerPredicate(where, params, "p2", f.player2Seat, f.player2Name);
        }

        if (haveSite) {
            where.append(" and g.site_id = ?");
            params.add(siteId);
        }
        if (haveEvent) {
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
