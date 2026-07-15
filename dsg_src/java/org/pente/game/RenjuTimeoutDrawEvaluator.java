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

    /**
     * Search-state budget. Distinct cooperative configurations (memoised on the
     * region's placed-array) are capped here; on overflow the search returns the
     * conservative verdict "black can win" (never wrongly declares a draw).
     */
    private static final int MEMO_CAP = 4_000_000;

    private static boolean blackCanWin(GridState state, int sx, int sy) {
        List<int[]> windows = blackCandidateWindows(state, sx, sy);
        if (windows.isEmpty()) return false;

        // Stage 1: window-only fill orders (cheap fast path).
        if (stage1(state, sx, sy, windows)) return true;

        // Relevant region: empty cells reachable from candidate-window cells by
        // line-distance <= 6 hops (covers the finder's longest pattern span).
        Set<Integer> region = relevantRegion(state, sx, sy, windows);

        // Stage 2: cooperative search with BLACK helper stones only.
        HelperSearch s2 = new HelperSearch(state, sx, sy, windows, region, false);
        if (s2.search()) return true;
        if (s2.overflowed) return true; // conservative: never wrongly declare draw

        // Stage 3: cooperative search with black + cooperative WHITE helpers.
        HelperSearch s3 = new HelperSearch(state, sx, sy, windows, region, true);
        if (s3.search()) return true;
        return s3.overflowed; // overflow -> conservative "can win"
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

    /**
     * Stage 1: for each candidate window try to fill its empty cells with
     * black stones in some order such that every placement is legal
     * (!isForbidden) or immediately wins (isFive). Only window cells are
     * placed. Any success -> black can win.
     */
    private static boolean stage1(GridState state, int sx, int sy, List<int[]> windows) {
        for (int[] w : windows) {
            List<int[]> empty = new ArrayList<int[]>();
            for (int i = 0; i < 5; i++) {
                int cx = w[0] + i * w[2], cy = w[1] + i * w[3];
                if (state.getPosition(cx, cy) == 0) empty.add(new int[]{cx, cy});
            }
            if (empty.isEmpty()) {
                // window already fully black: position is already won/over --
                // treat as winnable (defensive; live positions never reach this)
                return true;
            }
            RenjuForbiddenPointFinder f = buildFinder(state, sx, sy);
            if (fillSearch(f, empty, new boolean[empty.size()])) return true;
        }
        return false;
    }

    /**
     * DFS over fill orders of a window's empty cells. Win iff some placement
     * completes an exactly-five (isFive); every intermediate placement must be
     * legal (!isForbidden). Five-priority is honoured by testing isFive before
     * isForbidden, so a cell that both wins and would otherwise be forbidden is
     * treated as a win. (The window's own line closes as exactly-five on its
     * last empty cell -- flanks were pre-filtered non-black and white never
     * intrudes in stage 1 -- so a full fill without a five cannot occur.)
     */
    private static boolean fillSearch(RenjuForbiddenPointFinder f, List<int[]> cells, boolean[] used) {
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) continue;
            int x = cells.get(i)[0], y = cells.get(i)[1];
            if (f.isFive(x, y, 0)) return true;          // five-priority win
            if (f.isForbidden(x, y)) continue;           // illegal now; try later order
            used[i] = true;
            f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
            boolean win = fillSearch(f, cells, used);
            f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
            used[i] = false;
            if (win) return true;
        }
        return false;
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

    /**
     * Empty-cell "relevant region": the fixpoint of empty board cells reachable
     * from any candidate-window cell by line-distance &lt;= 6 hops along the four
     * canonical axes (both directions). Six covers the longest span the finder
     * inspects when judging a placement (a broken four / three plus flanks), so
     * every empty cell that could possibly influence whether a window cell is a
     * five or a forbidden point is included. Occupied cells are never added, but
     * expansion probes past them (a superset region is safe: extra helper
     * candidates can only reveal more wins, never hide a draw). The region is the
     * search domain for stages 2-3.
     */
    static Set<Integer> relevantRegion(GridState state, int sx, int sy, List<int[]> windows) {
        Set<Integer> region = new HashSet<Integer>();
        List<int[]> frontier = new ArrayList<int[]>();
        for (int[] w : windows) {
            for (int i = 0; i < 5; i++) {
                int cx = w[0] + i * w[2], cy = w[1] + i * w[3];
                frontierAdd(state, sx, sy, region, frontier, cx, cy);
            }
        }
        while (!frontier.isEmpty()) {
            int[] c = frontier.remove(frontier.size() - 1);
            for (int[] d : DIRS) {
                for (int s = 1; s <= 6; s++) {
                    frontierAdd(state, sx, sy, region, frontier, c[0] + s * d[0], c[1] + s * d[1]);
                    frontierAdd(state, sx, sy, region, frontier, c[0] - s * d[0], c[1] - s * d[1]);
                }
            }
        }
        return region;
    }

    /** Add an in-bounds EMPTY cell to the region/frontier once; true iff newly added. */
    private static boolean frontierAdd(GridState state, int sx, int sy,
            Set<Integer> region, List<int[]> frontier, int x, int y) {
        if (!inBounds(x, y, sx, sy)) return false;
        if (state.getPosition(x, y) != 0) return false;
        Integer key = Integer.valueOf(x + y * sx);
        if (!region.add(key)) return false;
        frontier.add(new int[]{x, y});
        return true;
    }

    /**
     * Memoised cooperative DFS over monotone stone additions inside the relevant
     * region. Both players cooperate to let black reach an exactly-five; turn
     * order is irrelevant to reachability under cooperation, so stones are added
     * in any order. Black placements must be legal (!isForbidden) unless they
     * complete a five (five-priority wins even from an otherwise-forbidden point);
     * white placements (stage 3 only) are always legal. Configurations are
     * memoised on the region's placed-array so each reachable board is expanded
     * once; the memo size is capped at {@link #MEMO_CAP}, and on overflow the
     * caller treats the result as the conservative "black can win".
     */
    private static final class HelperSearch {
        final GridState state;
        final int sx, sy;
        final List<int[]> windows;
        final int[] regionCells;      // packed x + y*sx, sorted (deterministic order)
        final boolean whiteHelpers;
        final RenjuForbiddenPointFinder f;
        final byte[] placed;          // 0 empty, 1 black, 2 white (parallel to regionCells)
        final Set<String> memo = new HashSet<String>();
        boolean overflowed = false;

        HelperSearch(GridState state, int sx, int sy, List<int[]> windows,
                Set<Integer> region, boolean whiteHelpers) {
            this.state = state;
            this.sx = sx;
            this.sy = sy;
            this.windows = windows;
            this.whiteHelpers = whiteHelpers;
            this.regionCells = new int[region.size()];
            int i = 0;
            for (Integer c : region) regionCells[i++] = c.intValue();
            java.util.Arrays.sort(regionCells);
            this.placed = new byte[regionCells.length];
            this.f = buildFinder(state, sx, sy);
        }

        boolean search() {
            return dfs();
        }

        private boolean dfs() {
            if (overflowed) return false;
            // Memo key: the placed-array as a Latin-1 string. Computed at entry so
            // it reflects exactly the mutations visible in `placed` for this state
            // (recursion mutates and restores placed[], so the key is per-node).
            String key = new String(placed, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!memo.add(key)) return false;
            if (memo.size() > MEMO_CAP) {
                overflowed = true;
                System.err.println("RenjuTimeoutDrawEvaluator: cooperative search exceeded "
                        + MEMO_CAP + " states; returning conservative can-win.");
                return false;
            }
            for (int i = 0; i < regionCells.length; i++) {
                if (placed[i] != 0) continue;
                int x = regionCells[i] % sx, y = regionCells[i] / sx;

                // Winning black placement: five-priority wins even if the point
                // would otherwise be forbidden (the finder cancels the forbidden
                // verdict on an exactly-five).
                if (f.isFive(x, y, 0)) return true;

                // Legal black helper / window-fill placement.
                if (!f.isForbidden(x, y)) {
                    placed[i] = 1;
                    f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
                    boolean win = dfs();
                    f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
                    placed[i] = 0;
                    if (win) return true;
                }

                // Cooperative white placement (stage 3 only; white is never forbidden).
                if (whiteHelpers) {
                    placed[i] = 2;
                    f.setStone(x, y, RenjuForbiddenPointFinder.WHITE);
                    boolean win = dfs();
                    f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
                    placed[i] = 0;
                    if (win) return true;
                }
            }
            return false;
        }
    }
}
