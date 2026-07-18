package org.pente.webdb;

import java.sql.*;
import java.util.*;

import org.apache.log4j.Category;

import org.pente.database.DBHandler;
import org.pente.game.GridState;
import org.pente.game.GridStateFactory;
import org.pente.game.MoveData;

import org.pente.webdb.dto.AnalysisMeta;
import org.pente.webdb.dto.WebDbGameData;

/**
 * Persistence for a logged-in player's private data: the personal game
 * collection ({@code webdb_game} / {@code webdb_move}) and saved analysis trees
 * ({@code webdb_analysis}). Every read and write is scoped by {@code pid}, so a
 * player can only ever touch their own rows.
 *
 * <p>The move-row layout mirrors the production archive storer
 * ({@code MySQLPenteGameStorer.storeGame}): one {@code webdb_move} row per
 * position, {@code move_num = i}, {@code next_move = moves[i+1]} (or the terminal
 * sentinel {@code 361} on the last move), and the canonical
 * {@code hash_key}/{@code rotation} pulled straight from
 * {@code GridStateFactory.createGridState(game, moves)} at index {@code i}. This
 * keeps the per-player {@code idx_stats} covering index shaped exactly like the
 * archive stats path, so {@link #positionStats} returns the same raw
 * {@code (next_move, rotation, winner, count)} rows the Task 6 handler folds
 * through the shared rotation-normalization loop.
 *
 * <p>All SQL values are bound as parameters. {@link #storeGame} and
 * {@link #deleteGame} run inside a transaction (autoCommit toggled off, then
 * committed or rolled back, and restored in {@code finally}).
 */
public class MySQLWebDbStorer {

    private static Category cat =
            Category.getInstance(MySQLWebDbStorer.class.getName());

    private static final String GAME_TABLE = "webdb_game";
    private static final String MOVE_TABLE = "webdb_move";
    private static final String ANALYSIS_TABLE = "webdb_analysis";

    /** Terminal sentinel written as {@code next_move} on a game's final row. */
    private static final int TERMINAL = 361;

    private final DBHandler dbHandler;

    public MySQLWebDbStorer(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    // ------------------------------------------------------------------
    // personal collection
    // ------------------------------------------------------------------

    /**
     * Store a game and all of its move rows in one transaction. Returns the
     * generated {@code wgid} (also set on {@code g.wgid}). The move rows carry
     * the canonical hash/rotation for each position so the game is findable by
     * {@link #positionStats} and {@link #findDuplicate}.
     */
    public long storeGame(long pid, WebDbGameData g) throws Exception {

        int[] moves = g.moves;
        GridState state =
                GridStateFactory.createGridState(g.game, moveDataOf(moves));

        Connection con = null;
        boolean oldAutoCommit = true;
        try {
            con = dbHandler.getConnection();
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            long wgid = insertGameRow(con, pid, g);
            insertMoveRows(con, pid, wgid, g, state);

            con.commit();
            g.wgid = wgid;
            return wgid;

        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ignore) {
                }
            }
            throw e;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignore) {
                }
                dbHandler.freeConnection(con);
            }
        }
    }

    private long insertGameRow(Connection con, long pid, WebDbGameData g)
            throws SQLException {

        PreparedStatement stmt = null;
        ResultSet keys = null;
        try {
            stmt = con.prepareStatement(
                    "insert into " + GAME_TABLE + " " +
                    "(pid, game, player1, player2, winner, " +
                    " site, event, round, section, play_date) " +
                    "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            stmt.setLong(1, pid);
            stmt.setInt(2, g.game);
            stmt.setString(3, g.player1);
            stmt.setString(4, g.player2);
            stmt.setInt(5, g.winner);
            stmt.setString(6, g.site);
            stmt.setString(7, g.event);
            stmt.setString(8, g.round);
            stmt.setString(9, g.section);
            if (g.playDate != null) {
                stmt.setTimestamp(10, new Timestamp(g.playDate.getTime()));
            } else {
                stmt.setNull(10, Types.TIMESTAMP);
            }
            stmt.executeUpdate();

            keys = stmt.getGeneratedKeys();
            if (!keys.next()) {
                throw new SQLException("no generated wgid for stored game");
            }
            return keys.getLong(1);
        } finally {
            close(keys);
            close(stmt);
        }
    }

    private void insertMoveRows(Connection con, long pid, long wgid,
                                WebDbGameData g, GridState state)
            throws SQLException {

        int n = g.moves.length;
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(
                    "insert into " + MOVE_TABLE + " " +
                    "(wgid, move_num, next_move, hash_key, rotation, " +
                    " game, winner, pid) " +
                    "values(?, ?, ?, ?, ?, ?, ?, ?)");
            for (int i = 0; i < n; i++) {
                stmt.setLong(1, wgid);
                stmt.setInt(2, i);
                // last move terminates the game with the 361 sentinel
                stmt.setInt(3, (i == n - 1) ? TERMINAL : g.moves[i + 1]);
                stmt.setLong(4, state.getHash(i));
                stmt.setInt(5, state.getRotation(i));
                stmt.setInt(6, g.game);
                stmt.setInt(7, g.winner);
                stmt.setLong(8, pid);
                stmt.executeUpdate();
            }
        } finally {
            close(stmt);
        }
    }

    /**
     * Duplicate detection (pentedb-SPEC §8.3, applied to {@code webdb_move}):
     * first a cheap candidate query keyed on the game's final canonical hash +
     * terminal move_num + game + pid, then a deep move-by-move comparison of each
     * candidate against {@code g}. Returns the matching {@code wgid}, or
     * {@code null} when the player has no identical game.
     */
    public Long findDuplicate(long pid, WebDbGameData g) throws Exception {

        int[] moves = g.moves;
        int n = moves.length;
        if (n == 0) {
            return null;
        }
        GridState state =
                GridStateFactory.createGridState(g.game, moveDataOf(moves));
        long finalHash = state.getHash(n - 1);
        int finalMoveNum = n - 1;

        Connection con = null;
        try {
            con = dbHandler.getConnection();

            List<Long> candidates = new ArrayList<Long>();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select wgid from " + MOVE_TABLE + " " +
                        "where pid = ? and hash_key = ? and move_num = ? " +
                        "and game = ? and next_move = ?");
                stmt.setLong(1, pid);
                stmt.setLong(2, finalHash);
                stmt.setInt(3, finalMoveNum);
                stmt.setInt(4, g.game);
                stmt.setInt(5, TERMINAL);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    candidates.add(Long.valueOf(rs.getLong(1)));
                }
            } finally {
                close(rs);
                close(stmt);
            }

            for (int i = 0; i < candidates.size(); i++) {
                long cand = candidates.get(i).longValue();
                int[] stored = loadMoves(con, cand, g.game);
                if (Arrays.equals(stored, moves)) {
                    return Long.valueOf(cand);
                }
            }
            return null;

        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /**
     * A page of the player's games for one variant, newest first. Header fields
     * only — {@code moves} is left {@code null} (use {@link #loadGame} for the
     * full move list). {@code offset}/{@code limit} map to MySQL {@code LIMIT}.
     */
    public List<WebDbGameData> listGames(long pid, int game, int offset, int limit)
            throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            List<WebDbGameData> out = new ArrayList<WebDbGameData>();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select wgid, pid, game, player1, player2, winner, " +
                        " site, event, round, section, play_date, imported " +
                        "from " + GAME_TABLE + " " +
                        "where pid = ? and game = ? " +
                        "order by imported desc, wgid desc " +
                        "limit ?, ?");
                stmt.setLong(1, pid);
                stmt.setInt(2, game);
                stmt.setInt(3, offset);
                stmt.setInt(4, limit);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    out.add(readGameRow(rs));
                }
            } finally {
                close(rs);
                close(stmt);
            }
            return out;
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /** Total number of the player's games for one variant. */
    public int countGames(long pid, int game) throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select count(*) from " + GAME_TABLE + " " +
                        "where pid = ? and game = ?");
                stmt.setLong(1, pid);
                stmt.setInt(2, game);
                rs = stmt.executeQuery();
                rs.next();
                return rs.getInt(1);
            } finally {
                close(rs);
                close(stmt);
            }
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /**
     * Load one of the player's games (header + reconstructed move list). Returns
     * {@code null} when the game does not exist or belongs to another player —
     * the {@code pid} predicate makes those two cases indistinguishable, which is
     * what the caller wants (no ownership leak).
     */
    public WebDbGameData loadGame(long pid, long wgid) throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            WebDbGameData g = loadGameHeader(con, pid, wgid);
            if (g == null) {
                return null;
            }
            g.moves = loadMoves(con, wgid, g.game);
            return g;
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    private WebDbGameData loadGameHeader(Connection con, long pid, long wgid)
            throws SQLException {

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(
                    "select wgid, pid, game, player1, player2, winner, " +
                    " site, event, round, section, play_date, imported " +
                    "from " + GAME_TABLE + " " +
                    "where wgid = ? and pid = ?");
            stmt.setLong(1, wgid);
            stmt.setLong(2, pid);
            rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return readGameRow(rs);
        } finally {
            close(rs);
            close(stmt);
        }
    }

    /**
     * Reconstruct a game's move list from its {@code webdb_move} rows. Each row
     * stores the move played FROM its position ({@code next_move}), not the move
     * itself, so {@code moves[0]} — never a {@code next_move} of any row — is
     * synthesized as the variant's center stone (mirrors the production
     * {@code MySQLPenteGameStorer} load path for non-off-center variants), and
     * the remaining moves are the ordered non-terminal {@code next_move}s.
     */
    private int[] loadMoves(Connection con, long wgid, int game)
            throws SQLException {

        List<Integer> mv = new ArrayList<Integer>();
        mv.add(Integer.valueOf(GridStateFactory.getCenterMove(game)));

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.prepareStatement(
                    "select next_move from " + MOVE_TABLE + " " +
                    "where wgid = ? and next_move != ? " +
                    "order by move_num");
            stmt.setLong(1, wgid);
            stmt.setInt(2, TERMINAL);
            rs = stmt.executeQuery();
            while (rs.next()) {
                mv.add(Integer.valueOf(rs.getInt(1)));
            }
        } finally {
            close(rs);
            close(stmt);
        }

        int[] out = new int[mv.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = mv.get(i).intValue();
        }
        return out;
    }

    /**
     * Delete one of the player's games and all its move rows, in a transaction.
     * Owner-checked on both tables. Returns {@code true} iff a game row belonging
     * to {@code pid} was actually removed.
     */
    public boolean deleteGame(long pid, long wgid) throws Exception {

        Connection con = null;
        boolean oldAutoCommit = true;
        try {
            con = dbHandler.getConnection();
            oldAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            deleteWhereOwned(con, MOVE_TABLE, wgid, pid);
            int gameRows = deleteWhereOwned(con, GAME_TABLE, wgid, pid);

            con.commit();
            return gameRows > 0;

        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ignore) {
                }
            }
            throw e;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignore) {
                }
                dbHandler.freeConnection(con);
            }
        }
    }

    private int deleteWhereOwned(Connection con, String table, long wgid, long pid)
            throws SQLException {

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement(
                    "delete from " + table + " where wgid = ? and pid = ?");
            stmt.setLong(1, wgid);
            stmt.setLong(2, pid);
            return stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    // ------------------------------------------------------------------
    // stats over the player's own games
    // ------------------------------------------------------------------

    /**
     * Raw per-move statistics over the player's own games for a single position,
     * shaped exactly like the archive stats query: one row per
     * {@code (next_move, rotation, winner)} group with its game count. Hits the
     * {@code idx_stats} covering index. The Task 6 handler merges these rows
     * through the same rotation-normalization loop the archive path uses.
     *
     * @param moveNum the {@code move_num} to filter on (i.e. {@code numMoves - 1}
     *                for the queried position)
     * @return rows of {@code {next_move, rotation, winner, count}}
     */
    public List<long[]> positionStats(long pid, long hash, int moveNum, int game)
            throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            List<long[]> rows = new ArrayList<long[]>();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select next_move, rotation, winner, count(*) " +
                        "from " + MOVE_TABLE + " " +
                        "where pid = ? and hash_key = ? and move_num = ? " +
                        "and game = ? " +
                        "group by next_move, rotation, winner");
                stmt.setLong(1, pid);
                stmt.setLong(2, hash);
                stmt.setInt(3, moveNum);
                stmt.setInt(4, game);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    rows.add(new long[]{
                            rs.getLong(1), rs.getLong(2),
                            rs.getLong(3), rs.getLong(4)});
                }
            } finally {
                close(rs);
                close(stmt);
            }
            return rows;
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    // ------------------------------------------------------------------
    // analyses
    // ------------------------------------------------------------------

    /** Store a new analysis tree; returns the generated {@code aid}. */
    public long storeAnalysis(long pid, String name, int game, String treeJson)
            throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            PreparedStatement stmt = null;
            ResultSet keys = null;
            try {
                stmt = con.prepareStatement(
                        "insert into " + ANALYSIS_TABLE + " " +
                        "(pid, name, game, tree) values(?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                stmt.setLong(1, pid);
                stmt.setString(2, name);
                stmt.setInt(3, game);
                stmt.setString(4, treeJson);
                stmt.executeUpdate();

                keys = stmt.getGeneratedKeys();
                if (!keys.next()) {
                    throw new SQLException("no generated aid for stored analysis");
                }
                return keys.getLong(1);
            } finally {
                close(keys);
                close(stmt);
            }
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /** The player's analyses (metadata only, no tree blob), newest first. */
    public List<AnalysisMeta> listAnalyses(long pid) throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            List<AnalysisMeta> out = new ArrayList<AnalysisMeta>();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select aid, name, game, created, updated " +
                        "from " + ANALYSIS_TABLE + " " +
                        "where pid = ? " +
                        "order by updated desc, aid desc");
                stmt.setLong(1, pid);
                rs = stmt.executeQuery();
                while (rs.next()) {
                    out.add(readAnalysisMeta(rs));
                }
            } finally {
                close(rs);
                close(stmt);
            }
            return out;
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /**
     * Load one analysis's {@code tree} JSON, filling {@code metaOut} (if
     * non-null) with its header fields. Returns {@code null} when the analysis
     * does not exist or belongs to another player.
     */
    public String loadAnalysis(long pid, long aid, AnalysisMeta metaOut)
            throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = con.prepareStatement(
                        "select aid, name, game, tree, created, updated " +
                        "from " + ANALYSIS_TABLE + " " +
                        "where aid = ? and pid = ?");
                stmt.setLong(1, aid);
                stmt.setLong(2, pid);
                rs = stmt.executeQuery();
                if (!rs.next()) {
                    return null;
                }
                if (metaOut != null) {
                    metaOut.aid = rs.getLong(1);
                    metaOut.name = rs.getString(2);
                    metaOut.game = rs.getInt(3);
                    metaOut.created = toDate(rs.getTimestamp(5));
                    metaOut.updated = toDate(rs.getTimestamp(6));
                }
                return rs.getString(4);
            } finally {
                close(rs);
                close(stmt);
            }
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /**
     * Rename an analysis and replace its tree. Owner-checked. Returns
     * {@code true} iff a row belonging to {@code pid} was updated. The
     * {@code updated} timestamp is bumped automatically by the DB.
     */
    public boolean updateAnalysis(long pid, long aid, String name, String treeJson)
            throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            PreparedStatement stmt = null;
            try {
                stmt = con.prepareStatement(
                        "update " + ANALYSIS_TABLE + " " +
                        "set name = ?, tree = ? " +
                        "where aid = ? and pid = ?");
                stmt.setString(1, name);
                stmt.setString(2, treeJson);
                stmt.setLong(3, aid);
                stmt.setLong(4, pid);
                return stmt.executeUpdate() > 0;
            } finally {
                close(stmt);
            }
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /** Delete an analysis. Owner-checked. Returns {@code true} iff removed. */
    public boolean deleteAnalysis(long pid, long aid) throws Exception {

        Connection con = null;
        try {
            con = dbHandler.getConnection();
            PreparedStatement stmt = null;
            try {
                stmt = con.prepareStatement(
                        "delete from " + ANALYSIS_TABLE + " " +
                        "where aid = ? and pid = ?");
                stmt.setLong(1, aid);
                stmt.setLong(2, pid);
                return stmt.executeUpdate() > 0;
            } finally {
                close(stmt);
            }
        } finally {
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static WebDbGameData readGameRow(ResultSet rs) throws SQLException {
        WebDbGameData g = new WebDbGameData();
        g.wgid = rs.getLong(1);
        g.pid = rs.getLong(2);
        g.game = rs.getInt(3);
        g.player1 = rs.getString(4);
        g.player2 = rs.getString(5);
        g.winner = rs.getInt(6);
        g.site = rs.getString(7);
        g.event = rs.getString(8);
        g.round = rs.getString(9);
        g.section = rs.getString(10);
        g.playDate = toDate(rs.getTimestamp(11));
        g.imported = toDate(rs.getTimestamp(12));
        return g;
    }

    private static AnalysisMeta readAnalysisMeta(ResultSet rs) throws SQLException {
        AnalysisMeta m = new AnalysisMeta();
        m.aid = rs.getLong(1);
        m.name = rs.getString(2);
        m.game = rs.getInt(3);
        m.created = toDate(rs.getTimestamp(4));
        m.updated = toDate(rs.getTimestamp(5));
        return m;
    }

    private static java.util.Date toDate(Timestamp ts) {
        return ts == null ? null : new java.util.Date(ts.getTime());
    }

    /** Wrap a bare move array as a read-only {@link MoveData} for hashing. */
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

    private static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                cat.warn("closing ResultSet", e);
            }
        }
    }

    private static void close(Statement st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {
                cat.warn("closing Statement", e);
            }
        }
    }
}
