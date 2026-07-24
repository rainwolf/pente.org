package org.pente.webdb.dto;

import java.util.List;

/**
 * Wire response for {@code POST /api/db/position-stats}.
 *
 * <pre>
 * {"totalGames": 1234, "totalWinPct": 55.1, "rotation": 3,
 *  "nextMoves": [{"move": 199, "games": 812, "wins": 495, "winPct": 61.0}, ...]}
 * </pre>
 *
 * {@code rotation} is the symmetry the server chose to canonicalize the queried
 * position; each {@link NextMove#move} is already rotated back into the caller's
 * orientation. {@code winPct} values are from the perspective of the side to
 * move and are rounded to one decimal place.
 */
public class PositionStatsResponse {

    /** All games that reached this position (includes terminal / undecided). */
    public long totalGames;

    /** Win rate for the side to move across decided games with a next move. */
    public double totalWinPct;

    /** Symmetry chosen to canonicalize the position (0..7). */
    public int rotation;

    /** Candidate next moves, sorted by {@link NextMove#games} descending. */
    public List<NextMove> nextMoves;

    /** Per-move aggregate: how a single continuation performed. */
    public static class NextMove {

        public int move;
        public long games;
        public long wins;
        public double winPct;

        public NextMove() {
        }

        public NextMove(int move, long games, long wins, double winPct) {
            this.move = move;
            this.games = games;
            this.wins = wins;
            this.winPct = winPct;
        }
    }
}
