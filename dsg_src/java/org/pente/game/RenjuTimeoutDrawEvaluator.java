package org.pente.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides whether the NON-timed-out player of a renju game can still
 * theoretically win, under the cooperative model (chess Art. 6.9 analog):
 * draw on timeout iff no series of legal moves ends in a win for the opponent.
 *
 * Exact three-stage search (see the 2026-07-15 spec):
 *   white opponent: any 5-window free of black stones (overline also wins white)
 *   black opponent: stage 1 window-only fills, stage 2 +black helpers,
 *                   stage 3 +cooperative white helpers.
 * Five-priority is built into RenjuForbiddenPointFinder (isFive cancels
 * overline/double-four/double-three), so isFive == win, !isForbidden == legal.
 *
 * API trap: isFive(x, y, nColor)'s third argument is a COLOR (0=black,
 * 1=white), NOT a direction — never loop it over 1..4.
 */
public class RenjuTimeoutDrawEvaluator {

    // 4 canonical line directions: E, S, SE, NE.
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /**
     * @param state         board to inspect (positions only; not mutated)
     * @param opponentColor board color (1=black, 2=white) of the player who
     *                      did NOT time out
     */
    public static boolean opponentCanWin(GridState state, int opponentColor) {
        int sx = state.getGridSizeX();
        int sy = state.getGridSizeY();
        if (opponentColor == 2) {
            return whiteCanWin(state, sx, sy);
        }
        return blackCanWin(state, sx, sy);
    }

    /** White: no forbidden points, overline wins — any 5-window without a black stone. */
    private static boolean whiteCanWin(GridState state, int sx, int sy) {
        for (int[] d : DIRS) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    if (!inBounds(x + 4 * d[0], y + 4 * d[1], sx, sy)) continue;
                    boolean blocked = false;
                    for (int i = 0; i < 5 && !blocked; i++) {
                        blocked = state.getPosition(x + i * d[0], y + i * d[1]) == 1;
                    }
                    if (!blocked) return true;
                }
            }
        }
        return false;
    }

    private static boolean blackCanWin(GridState state, int sx, int sy) {
        List<int[]> windows = blackCandidateWindows(state, sx, sy);
        if (windows.isEmpty()) return false;
        // Stages 1-3 arrive in Tasks 3 and 4.
        return stage1(state, sx, sy, windows);
    }

    /**
     * Black candidate windows: 5 cells all empty-or-black, both flanks not
     * already black (a black flank forces overline, which never wins black).
     * Each entry: {x, y, dx, dy}.
     */
    static List<int[]> blackCandidateWindows(GridState state, int sx, int sy) {
        List<int[]> out = new ArrayList<int[]>();
        for (int[] d : DIRS) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    if (!inBounds(x + 4 * d[0], y + 4 * d[1], sx, sy)) continue;
                    boolean ok = true;
                    for (int i = 0; i < 5 && ok; i++) {
                        ok = state.getPosition(x + i * d[0], y + i * d[1]) != 2;
                    }
                    if (!ok) continue;
                    int fx = x - d[0], fy = y - d[1];
                    if (inBounds(fx, fy, sx, sy) && state.getPosition(fx, fy) == 1) continue;
                    int gx = x + 5 * d[0], gy = y + 5 * d[1];
                    if (inBounds(gx, gy, sx, sy) && state.getPosition(gx, gy) == 1) continue;
                    out.add(new int[]{x, y, d[0], d[1]});
                }
            }
        }
        return out;
    }

    // Stage 1 lands in Task 3; keep the build green with a placeholder that
    // is CORRECT for the Task 2 tests (they never reach a fill search for
    // black except trivially fillable/unfillable windows).
    private static boolean stage1(GridState state, int sx, int sy, List<int[]> windows) {
        throw new UnsupportedOperationException("stage1: Task 3");
    }

    static boolean inBounds(int x, int y, int sx, int sy) {
        return x >= 0 && x < sx && y >= 0 && y < sy;
    }

    /** Seed a finder from the grid (mirrors RenjuState.refreshFinder). */
    static RenjuForbiddenPointFinder buildFinder(GridState state, int sx, int sy) {
        RenjuForbiddenPointFinder f = new RenjuForbiddenPointFinder(sx);
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                int p = state.getPosition(x, y);
                if (p == 1) f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
                else if (p == 2) f.setStone(x, y, RenjuForbiddenPointFinder.WHITE);
            }
        }
        return f;
    }
}
