# Renju Pass / Timeout-Draw / Draw Offers — Server + JSP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement renju pass moves, exact timeout-draw adjudication, and draw offers in the Java server (rules core, live tables, turn-based servlet/persistence) and the JSP turn-based board.

**Architecture:** Rules live in `RenjuState` (pass sentinel 225, double-pass draw) and a new pure `RenjuTimeoutDrawEvaluator` (three-stage exact search). Live play extends `DSGMoveTableEvent` with a `drawOffer` flag plus two new accept/reject events routed through `ServerTable`. Turn-based rides on `MoveServlet` commands with the pending offer persisted as a fixed-slot sentinel row in `tb_move`.

**Tech Stack:** Java 17+ (pattern-switch syntax already in use), Ant build, JUnit 3 (`junit.framework.TestCase`), Gson wire format, MariaDB.

**Spec:** `docs/superpowers/specs/2026-07-15-renju-pass-draw-design.md` (read it before starting any task).

## Global Constraints

- Renju board is 15×15; real moves are `0..224`; **pass sentinel = 225** (`gridSizeX*gridSizeY`).
- TB persistence sentinels in `tb_move`: `-1` = undo request (existing); **`-2` = pending draw offer**, both written at the next-free `move_num` slot (undo's pattern — `move_num` is UNSIGNED, negative slots are impossible). **Mutual exclusion rule:** an undo request cancels a pending draw offer (`-1` overwrites the `-2` row via `ON DUPLICATE KEY UPDATE`); an opponent's move overwrites either sentinel (implicit decline/clear); `-1` and `-2` never coexist.
- Pass and draw offers are legal **only when `RenjuState.isOpeningComplete()`** — never during phase `MOVE` (an opening sub-phase), never in non-renju games, never at AI tables.
- New live error code: `NO_DRAW_OFFERED = 25` (codes 17–24 are taken).
- Timeout-draw rule (exact, cooperative model): on renju timeout, draw iff the opponent cannot win by ANY series of legal moves. Win test = `finder.isFive(x,y,0)` for black / any 5-window free of black stones for white; legality test = `!finder.isForbidden(x,y)`. Five-priority is already built into `RenjuForbiddenPointFinder` — do not "fix" it.
- Tests are JUnit 3 style (`public void testXxx()`, `junit.framework.TestCase`, `suite()`/`main` boilerplate). Run: `ant test-one -Dtest=<fqcn>` from the `pente.org/` repo root.
- Every commit: `git commit -S` (signed), message style `feat(renju): ...` / `test(renju): ...`.
- No new DB columns; no schema migration. `draw` is derived (winner==0), not a column.
- Do not reformat surrounding code; match existing style (JUnit 3, log4j, no lambdas in old classes except where already present).

---

### Task 1: RenjuState — pass move + double-pass draw

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuPassTest.java` (new)

**Interfaces:**
- Consumes: existing `RenjuState` (constructors at L20-42, `addMove` L154-168, `isGameOver` L189-213, `getWinner` L219-233, `isOpeningComplete` L256-258, `forceOpeningComplete` L263-265), `SimpleGridState.setAllowNonBoardMoves` (L58-60), `SimpleGridState.addMove` (L178-192: silently drops out-of-board moves unless `allowNonBoardMoves`).
- Produces: `public int getPassMove()` (returns 225 on 15×15), `public boolean isPass(int move)`, `public boolean doublePass()`; `addMove(225)` appends a pass; `isValidMove(225, seat)` true iff opening complete; double pass ⇒ `isGameOver()==true` and `getWinner()==0`. Later tasks (6, 8, 9) rely on exactly these names.

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/game/test/RenjuPassTest.java`:

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuPassTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuPassTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuPassTest.class);
    }

    public RenjuPassTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    // Fast-forward through a minimal Taraguchi opening: 6 stones, no swaps,
    // Branch A. After move 6 addMove() sets openingComplete (RenjuState L163-164).
    // If the state machine demands an explicit branch choice, insert
    // s.chooseBranch(false) after the 4th move's swap decline — adjust the
    // setup as needed, never the assertions.
    private RenjuState openedState() {
        RenjuState s = new RenjuState(15, 15);
        int[] opening = {xy(7, 7), xy(7, 8), xy(8, 7), xy(8, 8), xy(6, 7)};
        for (int m : opening) {
            s.addMove(m);
            if (s.isAwaitingSwapDecision()) {
                s.renjuSwapDecisionMade(false);
            }
        }
        s.addMove(xy(6, 8)); // move 6 -> openingComplete
        assertTrue(s.isOpeningComplete());
        return s;
    }

    public void testPassMoveConstant() {
        RenjuState s = new RenjuState(15, 15);
        assertEquals(225, s.getPassMove());
        assertTrue(s.isPass(225));
        assertFalse(s.isPass(224));
    }

    public void testPassInvalidDuringOpening() {
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));
        assertFalse(s.isValidMove(225, 2));
        try {
            s.addMove(225);
            fail("pass during opening must throw");
        } catch (IllegalStateException expected) {
        }
    }

    public void testPassValidAfterOpening() {
        RenjuState s = openedState();
        int n = s.getNumMoves();
        assertTrue(s.isValidMove(225, s.getCurrentPlayer()));
        s.addMove(225); // must not throw (also exercises the hash path)
        assertEquals(n + 1, s.getNumMoves());
        assertEquals(225, s.getMove(n));
        assertFalse(s.isGameOver());
        assertFalse(s.doublePass());
    }

    public void testDoublePassEndsGameInDraw() {
        RenjuState s = openedState();
        s.addMove(225);
        s.addMove(225);
        assertTrue(s.doublePass());
        assertTrue(s.isGameOver());
        assertEquals(0, s.getWinner()); // 0 == draw for callers (winner==0 branch)
    }

    public void testUndoPass() {
        RenjuState s = openedState();
        int n = s.getNumMoves();
        s.addMove(225);
        s.undoMove();
        assertEquals(n, s.getNumMoves());
        assertFalse(s.isGameOver());
    }

    public void testSinglePassDoesNotTriggerBoardFullDraw() {
        // Guard: drawCheck must count stones, not raw numMoves, once passes exist.
        RenjuState s = openedState();
        s.addMove(225);
        s.addMove(xy(0, 0));
        s.addMove(225);
        assertFalse(s.isGameOver());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuPassTest`
Expected: FAIL/ERROR — `getPassMove()` / `isPass(int)` / `doublePass()` don't exist (compile error).

- [ ] **Step 3: Implement in `RenjuState.java`**

3a. Add fields + accessors near `openingComplete` (around L254):

```java
    // Pass support: one past the last board index (mirrors GoState.passMove).
    // Legal only once the Taraguchi opening is complete; two consecutive
    // passes end the game in a draw (no Go-style scoring phase).
    private int passMove;

    public int getPassMove() {
        return passMove;
    }

    public boolean isPass(int move) {
        return move == passMove;
    }
```

3b. Initialize in BOTH constructors (after the `finder = ...` lines, L32/L40) and enable out-of-board appends on the wrapped grid:

```java
        passMove = gridState.getGridSizeX() * gridState.getGridSizeY();
        ((SimpleGridState) gridState).setAllowNonBoardMoves(true);
```

(`SimpleGridState.addMove` L178-192 otherwise SILENTLY drops out-of-board moves — the flag makes it append them without touching the board. Only `passMove` itself gets through because `isValidMove`/`addMove` below reject everything else out of range.)

3c. Rework `addMove` (L154-168) to route passes and reject other out-of-board values:

```java
    public void addMove(int move) {
        if (outOfBounds(move)) {
            if (move != passMove) {
                throw new IllegalArgumentException("move out of range: " + move);
            }
            if (!openingComplete) {
                throw new IllegalStateException("pass not allowed during renju opening");
            }
            gridState.addMove(move);   // appended to the move list, no stone placed
            return;                    // no finder refresh, no opening bookkeeping, no hash
        }
        gridState.addMove(move);
        refreshFinder();
        int n = gridState.getNumMoves();
        if (!openingComplete) {
            if (n >= 1 && n <= 4) {
                awaitingSwap = true;
            } else if (n == 5 && !tenOffer) {
                awaitingSwap = true; // Branch A: white may swap before move 6
            } else if (n == 6) {
                openingComplete = true;
            }
        }
        updateHash(this);
    }
```

IMPORTANT: the pass branch deliberately skips `updateHash(this)` — inspect the class's `HashCalculator` methods before finishing this step; if any of them index Zobrist tables by move (`rand[...][move]`), a 225 index would overflow. Skipping the hash update for a pass keeps the position hash equal to the board position, which is correct (a pass does not change the position). If `updateHash` turns out to be safe AND necessary for move-count bookkeeping, guard the indexing instead — the test in Step 1 (`testPassValidAfterOpening`) fails with an exception either way if this is wrong.

3d. Add `doublePass()` and wire game end. Add after `isGameOver()` (L213):

```java
    public boolean doublePass() {
        int n = getNumMoves();
        return n >= 2 && getMove(n - 1) == passMove && getMove(n - 2) == passMove;
    }
```

In `isGameOver()` (L189), insert before the `outOfBounds` check:

```java
        if (doublePass()) return true;
```

(The existing `if (outOfBounds(lastMove)) return drawCheck(n);` line then only handles a single trailing pass — correct: not game over unless board full.)

In `getWinner()` (L219): no change needed — `outOfBounds(lastMove) → return 0` already reports a draw for a double-pass ending.

3e. Fix `drawCheck` (L215-217) to count stones, not moves (passes inflate `n`):

```java
    private boolean drawCheck(int n) {
        int stones = 0;
        for (int i = 0; i < n; i++) {
            if (!outOfBounds(getMove(i))) stones++;
        }
        return stones == gridState.getGridSizeX() * gridState.getGridSizeY();
    }
```

3f. Extend `isValidMove` (find it around L706-727; it currently delegates/accepts board moves without a forbidden check). Add at the top:

```java
        if (move == passMove) {
            return openingComplete && !isGameOver();
        }
```

Leave the rest of the method untouched.

3g. Check `undoMove` (L170-174): `gridState.undoMove()` must tolerate removing a pass. Read `SimpleGridState.undoMove`; its `setPosition(move, 0)` path is guarded by `outOfBounds` (`setPosition` L270-275 no-ops out of board), so no change is expected — `testUndoPass` proves it.

3h. Check `clear()` (L176-187): nothing pass-specific to reset (passMove is fixed by board size). No change.

- [ ] **Step 4: Run test to verify it passes**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuPassTest`
Expected: PASS (6 tests).

Also run the existing renju suites to catch regressions:

```
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java dsg_src/java/org/pente/game/test/RenjuPassTest.java
git commit -S -m "feat(renju): pass move (225) after opening, double-pass ends in draw"
```

---

### Task 2: RenjuTimeoutDrawEvaluator — white case + candidate windows

**Files:**
- Create: `dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java` (new)

**Interfaces:**
- Consumes: `GridState.getPosition(x,y)` (0 empty / 1 black / 2 white), `getGridSizeX/Y`; `RenjuForbiddenPointFinder` (`setStone(x,y,char)`, `isFive(x,y,0)`, `isForbidden(x,y)`, constants `BLACK/WHITE/EMPTY`).
- Produces: `public static boolean opponentCanWin(GridState state, int opponentColor)` — `opponentColor` is the BOARD COLOR (1=black, 2=white) of the non-timed-out player. Returns true iff that player can still theoretically win under the cooperative model. Tasks 7 and 11 call exactly this.

- [ ] **Step 1: Write the failing test (white cases + window geometry)**

Create `dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java`:

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuTimeoutDrawEvaluatorTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuTimeoutDrawEvaluatorTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuTimeoutDrawEvaluatorTest.class);
    }

    public RenjuTimeoutDrawEvaluatorTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    // Bare board container: SimpleGridState positions can be set directly,
    // bypassing opening rules — the evaluator only reads positions.
    private SimpleGridState board() {
        return new SimpleGridState(15, 15);
    }

    public void testWhiteWinsOnEmptyBoard() {
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(board(), 2));
    }

    public void testBlackWinsOnEmptyBoard() {
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(board(), 1));
    }

    // Black stones on every 5th column kill every horizontal/diagonal window
    // wider than 4 and every vertical window in those columns... build the
    // classic draw lattice: black stone at every (x,y) with (x % 5 == 0),
    // filling full columns 0,5,10 leaves 4-wide gaps: no 5-window without black.
    public void testWhiteDrawWhenEveryWindowBlocked() {
        SimpleGridState s = board();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x += 5) {
                s.setPosition(s.convertMove(x, y), 1); // black walls
            }
        }
        // vertical windows inside the 4-wide corridors are still black-free:
        // corridors are columns 1-4, full height -> vertical 5-window exists.
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
        // now poison the corridors vertically too: black row every 5th row
        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 15; y += 5) {
                s.setPosition(s.convertMove(x, y), 1);
            }
        }
        assertFalse(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
    }

    public void testWhiteWindowMayContainWhiteStones() {
        SimpleGridState s = board();
        // fill almost everything with black except one row segment holding
        // 3 white stones + 2 empties: white can still complete five (overline
        // irrelevant: flanks are black, but white overline also wins — and
        // here it is exactly five anyway).
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                s.setPosition(s.convertMove(x, y), 1);
            }
        }
        for (int x = 4; x <= 8; x++) {
            s.setPosition(s.convertMove(x, 7), 0);
        }
        s.setPosition(s.convertMove(4, 7), 2);
        s.setPosition(s.convertMove(5, 7), 2);
        s.setPosition(s.convertMove(6, 7), 2);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
    }

    // Black candidate windows demand non-black flanks: a lone 5-gap whose
    // both flanking cells are black can never be an exactly-five.
    public void testBlackFlankedWindowIsDead() {
        SimpleGridState s = board();
        // block everything except row 7, x=4..8 empty, with BLACK flanks at
        // x=3 and x=9. All other cells white.
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                s.setPosition(s.convertMove(x, y), 2);
            }
        }
        for (int x = 4; x <= 8; x++) {
            s.setPosition(s.convertMove(x, 7), 0);
        }
        s.setPosition(s.convertMove(3, 7), 1);
        s.setPosition(s.convertMove(9, 7), 1);
        assertFalse(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: FAIL — class `RenjuTimeoutDrawEvaluator` does not exist.

- [ ] **Step 3: Implement the skeleton + white case + window enumeration**

Create `dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java`:

```java
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
```

NOTE for this step only: the two black-opponent tests in Step 1 (`testBlackWinsOnEmptyBoard`, `testBlackFlankedWindowIsDead`) will still fail on the `UnsupportedOperationException` / empty-window path respectively — `testBlackFlankedWindowIsDead` passes already (no candidate windows → false), `testBlackWinsOnEmptyBoard` stays red until Task 3. That is expected; run the suite and confirm ONLY that test is red before committing (JUnit 3 has no skip mechanism worth adding here).

- [ ] **Step 4: Run test**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: 4 of 5 pass; `testBlackWinsOnEmptyBoard` errors with `UnsupportedOperationException: stage1: Task 3`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java
git commit -S -m "feat(renju): timeout-draw evaluator — white case + black candidate windows (stage 1 pending)"
```

---

### Task 3: RenjuTimeoutDrawEvaluator — stage 1 (window-only fill orders)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java`
- Modify: `dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java`

**Interfaces:**
- Consumes: Task 2's `blackCandidateWindows`, `buildFinder`.
- Produces: working `stage1(...)`; `opponentCanWin` correct for all positions where no helper stones are needed.

- [ ] **Step 1: Add failing tests**

Append to `RenjuTimeoutDrawEvaluatorTest.java`:

```java
    public void testBlackSimpleWindowFill() {
        SimpleGridState s = board();
        s.setPosition(s.convertMove(3, 3), 2); // lone white stone far away
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Five-priority regression: completing an exactly-five while forming a
    // cross-line double-three must count as a WIN (finder cancels forbidden
    // on five). Board: black B at (4,7),(5,7),(6,7),(7,7) — horizontal four —
    // and prepared verticals so that (8,7) also creates threes; (8,7)
    // completes exactly-five horizontally.
    public void testFivePriorityWinCountsAsWin() {
        SimpleGridState s = board();
        int[][] black = {{4, 7}, {5, 7}, {6, 7}, {7, 7}, {8, 5}, {8, 6}, {8, 9}, {8, 10}};
        for (int[] b : black) {
            s.setPosition(s.convertMove(b[0], b[1]), 1);
        }
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // A window whose ONLY fill order collides with a double-three at one cell
    // unless another window cell is filled first — exercises order search.
    // Construction: window row 7 x=4..8; black already at (4,7),(5,7).
    // Extra black pairs make (6,7) a double-three UNTIL (7,7) is placed
    // (turning one three into a four). Any fixed left-to-right fill fails;
    // the (7,7)-before-(6,7) order succeeds.
    public void testStage1OrderMatters() {
        SimpleGridState s = board();
        int[][] black = {{4, 7}, {5, 7},
                {6, 5}, {6, 6},      // vertical pair through (6,7)
                {4, 9}, {5, 8}};     // diagonal pair toward (6,7)
        for (int[] b : black) {
            s.setPosition(s.convertMove(b[0], b[1]), 1);
        }
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }
```

(These constructions are best-effort on paper; when implementing, PRINT the finder verdicts (`isForbidden`) for the window cells before trusting a test — adjust stone placements until the intended forbidden/legal pattern actually holds, keeping the intent comments accurate. The invariant to test is stated in each comment.)

- [ ] **Step 2: Run to verify the new tests fail**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: new tests error on `UnsupportedOperationException`.

- [ ] **Step 3: Implement stage 1**

Replace the `stage1` placeholder:

```java
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
                // window already fully black: position is already won/over —
                // treat as winnable (defensive; live positions never reach this)
                return true;
            }
            RenjuForbiddenPointFinder f = buildFinder(state, sx, sy);
            if (fillSearch(f, empty, new boolean[empty.size()])) return true;
        }
        return false;
    }

    /** DFS over fill orders of the window's empty cells. */
    private static boolean fillSearch(RenjuForbiddenPointFinder f, List<int[]> cells, boolean[] used) {
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) continue;
            int x = cells.get(i)[0], y = cells.get(i)[1];
            if (f.isFive(x, y, 0)) return true;          // five-priority win
            if (f.isForbidden(x, y)) continue;           // illegal now; try later order
            used[i] = true;
            f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
            boolean win = allUsed(used) || fillSearch(f, cells, used);
            f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
            used[i] = false;
            if (win && !allUsedIsFalseAlarm(cells, used)) {
                // NOTE: reaching allUsed without an isFive firing cannot
                // actually happen for a candidate window (its own line closes
                // as exactly-five on the last stone, pre-filtered flanks) —
                // keep the guard simple:
                return true;
            }
        }
        return false;
    }

    private static boolean allUsed(boolean[] used) {
        for (boolean u : used) if (!u) return false;
        return true;
    }

    private static boolean allUsedIsFalseAlarm(List<int[]> cells, boolean[] used) {
        return false;
    }
```

Simplification during implementation is welcome (the `allUsedIsFalseAlarm` scaffold exists only to keep the win condition honest — the LAST window cell always completes an exactly-five on the window's line since flanks were pre-filtered and white never intrudes in stage 1, so `isFive` fires on it; the clean final form is: win iff `isFive` fires on some placement, full fill without a five returning false). Prefer the clean form:

```java
    private static boolean fillSearch(RenjuForbiddenPointFinder f, List<int[]> cells, boolean[] used) {
        for (int i = 0; i < cells.size(); i++) {
            if (used[i]) continue;
            int x = cells.get(i)[0], y = cells.get(i)[1];
            if (f.isFive(x, y, 0)) return true;
            if (f.isForbidden(x, y)) continue;
            used[i] = true;
            f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
            boolean win = fillSearch(f, cells, used);
            f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
            used[i] = false;
            if (win) return true;
        }
        return false;
    }
```

- [ ] **Step 4: Run tests, calibrate the hand-built positions**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: PASS (8 tests). If `testStage1OrderMatters` or `testFivePriorityWinCountsAsWin` fail, verify the position with a debug print of `isForbidden`/`isFive` per window cell and adjust stones so the comment's scenario genuinely holds — do NOT weaken the assertion.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java
git commit -S -m "feat(renju): timeout-draw evaluator stage 1 — window fill-order search"
```

---

### Task 4: RenjuTimeoutDrawEvaluator — stages 2+3 (helper stones, exact search)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java`
- Modify: `dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java`

**Interfaces:**
- Consumes: Tasks 2-3 internals.
- Produces: final exact `opponentCanWin`. Search caps: memo limited to 4_000_000 states; on overflow return `true` (never wrongly declares a draw; logs via `System.err` — this class is pure, no log4j dependency needed).

- [ ] **Step 1: Add failing tests**

Append to the test class:

```java
    // Stage 2: a black HELPER outside every candidate window is required —
    // one window cell is double-three-forbidden in every window-only order,
    // but extending one of the threes into a four (helper) legalizes it.
    public void testStage2BlackHelperNeeded() {
        SimpleGridState s = board();
        // Construction sketch (calibrate with finder prints, keep the intent):
        // - window: row 7, x=4..8, cells empty, flanks empty
        // - cell (6,7) is double-three via two crossing prepared black pairs
        //   whose lines DO NOT intersect the window elsewhere
        // - no window-only order avoids placing (6,7) while it is forbidden
        //   (other window cells don't touch the crossing lines)
        // - helper at (6,4) extends the vertical three -> four; then (6,7) is
        //   a four+three point, legal.
        int[][] black = {
                {6, 5}, {6, 6},          // vertical pair (three when (6,7) placed)
                {4, 9}, {5, 8},          // diagonal pair (three when (6,7) placed)
                {2, 7}, {10, 7},         // pin the window row so shifted windows
                {3, 6}, {9, 6},          //   through (6,7) are white-blocked below
        };
        int[][] white = {
                {1, 7}, {11, 7},
                // block alternative windows so THIS window is the only hope;
                // calibrate so stage 1 fails but stage 2 succeeds
        };
        for (int[] b : black) s.setPosition(s.convertMove(b[0], b[1]), 1);
        for (int[] w : white) s.setPosition(s.convertMove(w[0], w[1]), 2);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Stage 3: only a cooperative WHITE stone can dissolve the forbiddenness
    // (blocking one three's extension square, making it fake).
    public void testStage3WhiteHelperNeeded() {
        SimpleGridState s = board();
        // Same shape as stage-2 test, but the vertical line is arranged so a
        // black extension would itself be forbidden/overline-bound, leaving a
        // white block of the OTHER three's extension as the only legalizer.
        // Calibrate with finder prints; the assertion is the contract:
        int[][] black = {
                {6, 5}, {6, 6}, {6, 3}, {6, 2},   // vertical: extending makes overline threat
                {4, 9}, {5, 8},
        };
        for (int[] b : black) s.setPosition(s.convertMove(b[0], b[1]), 1);
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
    }

    // Full-board congested draw: no candidate window at all survives.
    public void testCongestedDraw() {
        SimpleGridState s = board();
        // checkerboard-ish white poisoning: white at every (x+y) % 2 == 0
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x + y) % 2 == 0) s.setPosition(s.convertMove(x, y), 2);
            }
        }
        assertFalse(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
        // white perspective: black poisoning mirror
        SimpleGridState t = board();
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if ((x + y) % 2 == 0) t.setPosition(t.convertMove(x, y), 1);
            }
        }
        assertFalse(RenjuTimeoutDrawEvaluator.opponentCanWin(t, 2));
    }

    // Perf guard: a mid-density board where stage 1 succeeds must return fast.
    public void testOpenPositionFast() {
        SimpleGridState s = board();
        s.setPosition(s.convertMove(7, 7), 1);
        s.setPosition(s.convertMove(8, 8), 2);
        long t0 = System.currentTimeMillis();
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 1));
        assertTrue(RenjuTimeoutDrawEvaluator.opponentCanWin(s, 2));
        assertTrue("evaluator too slow on open position",
                System.currentTimeMillis() - t0 < 2000);
    }
```

- [ ] **Step 2: Run to verify the stage-2/3 tests fail**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: `testStage2BlackHelperNeeded` / `testStage3WhiteHelperNeeded` fail (stage 1 alone can't crack them, evaluator returns false). If they PASS, the constructions aren't actually stage-2/3-hard — recalibrate the stones until stage 1 demonstrably fails on them (temporarily instrument stage1 to log).

- [ ] **Step 3: Implement stages 2+3**

Replace `blackCanWin` and add the search:

```java
    private static final int MEMO_CAP = 4_000_000;

    private static boolean blackCanWin(GridState state, int sx, int sy) {
        List<int[]> windows = blackCandidateWindows(state, sx, sy);
        if (windows.isEmpty()) return false;
        if (stage1(state, sx, sy, windows)) return true;

        // Relevant region: empty cells reachable from candidate-window cells
        // by hops of line-distance <= 6 (covers the finder's longest pattern
        // span). Fixpoint expansion.
        Set<Integer> region = relevantRegion(state, sx, sy, windows);

        // Stage 2: black helpers only.
        HelperSearch s2 = new HelperSearch(state, sx, sy, windows, region, false);
        if (s2.search()) return true;
        if (s2.overflowed) return true; // conservative: never wrongly declare draw

        // Stage 3: black + cooperative white helpers.
        HelperSearch s3 = new HelperSearch(state, sx, sy, windows, region, true);
        if (s3.search()) return true;
        return s3.overflowed; // overflow -> conservative true
    }

    /** Empty-cell region: fixpoint of line-distance<=6 hops from window cells. */
    static Set<Integer> relevantRegion(GridState state, int sx, int sy, List<int[]> windows) {
        Set<Integer> region = new HashSet<Integer>();
        List<int[]> frontier = new ArrayList<int[]>();
        for (int[] w : windows) {
            for (int i = 0; i < 5; i++) {
                int cx = w[0] + i * w[2], cy = w[1] + i * w[3];
                if (frontierAdd(state, sx, sy, region, frontier, cx, cy)) {
                    // added
                }
            }
        }
        while (!frontier.isEmpty()) {
            int[] c = frontier.remove(frontier.size() - 1);
            for (int[] d : DIRS) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    for (int k = 1; k <= 6; k++) {
                        int nx = c[0] + sign * k * d[0], ny = c[1] + sign * k * d[1];
                        if (!inBounds(nx, ny, sx, sy)) break;
                        frontierAdd(state, sx, sy, region, frontier, nx, ny);
                    }
                }
            }
        }
        return region;
    }

    private static boolean frontierAdd(GridState state, int sx, int sy,
                                       Set<Integer> region, List<int[]> frontier, int x, int y) {
        if (!inBounds(x, y, sx, sy)) return false;
        if (state.getPosition(x, y) != 0) return false;
        Integer key = Integer.valueOf(x + y * sx);
        if (!region.add(key)) return false;
        frontier.add(new int[]{x, y});
        return true;
    }

    /** Memoized DFS over monotone stone additions inside the region. */
    private static class HelperSearch {
        final GridState state;
        final int sx, sy;
        final List<int[]> windows;
        final int[] regionCells;   // packed x + y*sx
        final boolean whiteHelpers;
        final RenjuForbiddenPointFinder f;
        final byte[] placed;       // 0 empty, 1 black, 2 white (parallel to regionCells)
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
            String key = new String(placed, java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!memo.add(key)) return false;
            if (memo.size() > MEMO_CAP) {
                overflowed = true;
                System.err.println("RenjuTimeoutDrawEvaluator: memo cap hit, returning conservative CAN-WIN");
                return false;
            }
            for (int i = 0; i < regionCells.length; i++) {
                if (placed[i] != 0) continue;
                int x = regionCells[i] % sx, y = regionCells[i] / sx;
                // black placement
                if (f.isFive(x, y, 0)) return true;      // five-priority win
                if (!f.isForbidden(x, y)) {
                    placed[i] = 1;
                    f.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
                    boolean win = dfs();
                    f.setStone(x, y, RenjuForbiddenPointFinder.EMPTY);
                    placed[i] = 0;
                    if (win) return true;
                }
                // cooperative white placement (stage 3 only)
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
```

Implementation freedom: the memo key can be anything collision-safe over `placed` (the byte-array-as-latin1 string is simple and exact); ordering heuristics (try cells nearest a window first) are welcome; correctness contract is the test suite. Do not add depth caps — only the memo cap, whose overflow returns "can win" (conservative).

- [ ] **Step 4: Run tests**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest`
Expected: PASS (12 tests). Calibrate stage-2/3 fixtures per Step 2's method if needed.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuTimeoutDrawEvaluator.java dsg_src/java/org/pente/game/test/RenjuTimeoutDrawEvaluatorTest.java
git commit -S -m "feat(renju): timeout-draw evaluator stages 2+3 — helper stones, exact cooperative model"
```

---

### Task 5: Live protocol — new events, drawOffer flag, error code, state sync field

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuAcceptDrawTableEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuRejectDrawTableEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuDrawTableErrorEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGMoveTableEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGTableErrorEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGGameStateTableEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java`

**Interfaces:**
- Produces (Task 6 consumes all of these):
  - `DSGRenjuAcceptDrawTableEvent(String player, int table)` / `DSGRenjuRejectDrawTableEvent(String player, int table)` — no payload beyond player/table.
  - `DSGRenjuDrawTableErrorEvent(String player, int table, int error)`.
  - `DSGMoveTableEvent`: `public boolean isDrawOffer()` / `public void setDrawOffer(boolean)`; new ctor `DSGMoveTableEvent(String player, int table, int move, boolean drawOffer)`.
  - `DSGTableErrorEvent.NO_DRAW_OFFERED = 25`.
  - `DSGGameStateTableEvent`: `public String getDrawOfferedBy()` / `setDrawOfferedBy(String)` (null = none).
  - `DSGEventWrapper`: fields `dsgRenjuAcceptDrawTableEvent`, `dsgRenjuRejectDrawTableEvent`, `dsgRenjuDrawTableErrorEvent` + getters/setters.

- [ ] **Step 1: Create the three event classes**

`DSGRenjuAcceptDrawTableEvent.java` (template: `DSGUndoRequestTableEvent`):

```java
package org.pente.gameServer.event;

public class DSGRenjuAcceptDrawTableEvent extends AbstractDSGTableEvent {

    public DSGRenjuAcceptDrawTableEvent() {
        super();
    }

    public DSGRenjuAcceptDrawTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "renju accept draw " + super.toString();
    }
}
```

`DSGRenjuRejectDrawTableEvent.java` — identical shape, class name/`toString` say `reject`:

```java
package org.pente.gameServer.event;

public class DSGRenjuRejectDrawTableEvent extends AbstractDSGTableEvent {

    public DSGRenjuRejectDrawTableEvent() {
        super();
    }

    public DSGRenjuRejectDrawTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "renju reject draw " + super.toString();
    }
}
```

`DSGRenjuDrawTableErrorEvent.java` — mirror the existing error-event shape. FIRST read `dsg_src/java/org/pente/gameServer/event/DSGUndoRequestTableErrorEvent.java` and copy its exact structure (it extends the abstract error base and carries an `int error`); then write:

```java
package org.pente.gameServer.event;

public class DSGRenjuDrawTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGRenjuDrawTableErrorEvent() {
        super();
    }

    public DSGRenjuDrawTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "renju draw error " + super.toString();
    }
}
```

(If `AbstractDSGTableErrorEvent`'s constructor signature differs, follow the undo error event's actual pattern verbatim.)

- [ ] **Step 2: Extend `DSGMoveTableEvent`**

Add below `private int moves[];` (L10):

```java
    private boolean drawOffer;
```

Add a constructor and accessors after the existing `(String, int, int)` constructor (L16-19):

```java
    public DSGMoveTableEvent(String player, int table, int move, boolean drawOffer) {
        super(player, table);
        setMove(move);
        this.drawOffer = drawOffer;
    }

    public boolean isDrawOffer() {
        return drawOffer;
    }

    public void setDrawOffer(boolean drawOffer) {
        this.drawOffer = drawOffer;
    }
```

Gson notes (no code): absent field on inbound JSON → `false`; unknown field on old clients → ignored. Both directions stay wire-compatible.

- [ ] **Step 3: Error code + game-state field**

`DSGTableErrorEvent.java` — after `public static final int WAIT_GAME_TWO_OF_SET = 24;`:

```java
    public static final int NO_DRAW_OFFERED = 25;
```

`DSGGameStateTableEvent.java` — add field + accessors (below `private int gameInSet;`):

```java
    private String drawOfferedBy;

    public String getDrawOfferedBy() {
        return drawOfferedBy;
    }

    public void setDrawOfferedBy(String drawOfferedBy) {
        this.drawOfferedBy = drawOfferedBy;
    }
```

(No constructor change — callers set it explicitly when relevant; null stays off the wire meaningfully.)

- [ ] **Step 4: Register in `DSGEventWrapper`**

Add three fields in the fields block (keep alphabetical-ish placement near the other renju events):

```java
    private DSGRenjuAcceptDrawTableEvent dsgRenjuAcceptDrawTableEvent;
    private DSGRenjuRejectDrawTableEvent dsgRenjuRejectDrawTableEvent;
    private DSGRenjuDrawTableErrorEvent dsgRenjuDrawTableErrorEvent;
```

And the getter/setter pairs (template: the `dsgRenjuTaraguchiSwapTableEvent` pair at L682-688):

```java
    public DSGRenjuAcceptDrawTableEvent getDsgRenjuAcceptDrawTableEvent() {
        return dsgRenjuAcceptDrawTableEvent;
    }

    public void setDsgRenjuAcceptDrawTableEvent(DSGRenjuAcceptDrawTableEvent dsgRenjuAcceptDrawTableEvent) {
        this.dsgRenjuAcceptDrawTableEvent = dsgRenjuAcceptDrawTableEvent;
    }

    public DSGRenjuRejectDrawTableEvent getDsgRenjuRejectDrawTableEvent() {
        return dsgRenjuRejectDrawTableEvent;
    }

    public void setDsgRenjuRejectDrawTableEvent(DSGRenjuRejectDrawTableEvent dsgRenjuRejectDrawTableEvent) {
        this.dsgRenjuRejectDrawTableEvent = dsgRenjuRejectDrawTableEvent;
    }

    public DSGRenjuDrawTableErrorEvent getDsgRenjuDrawTableErrorEvent() {
        return dsgRenjuDrawTableErrorEvent;
    }

    public void setDsgRenjuDrawTableErrorEvent(DSGRenjuDrawTableErrorEvent dsgRenjuDrawTableErrorEvent) {
        this.dsgRenjuDrawTableErrorEvent = dsgRenjuDrawTableErrorEvent;
    }
```

(The wrapper's reflection constructor and `getEncodedEvent()` need no changes — they walk declared fields.)

- [ ] **Step 5: Compile + commit**

Run: `./justCompile` (or `ant compile`)
Expected: BUILD SUCCESSFUL.

```bash
git add dsg_src/java/org/pente/gameServer/event/
git commit -S -m "feat(renju): live draw-offer protocol — accept/reject/error events, move drawOffer flag, NO_DRAW_OFFERED=25, game-state drawOfferedBy"
```

---

### Task 6: ServerTable — pass validation, draw-offer lifecycle, accept/reject handlers

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java`

**Interfaces:**
- Consumes: Task 1 (`RenjuState.isOpeningComplete/isPass/getPassMove`), Task 5 events.
- Produces: `handleMove(String player, int move, boolean drawOffer)` (old 2-arg overload kept, delegating with `false`); `handleRenjuAcceptDraw(DSGRenjuAcceptDrawTableEvent)`; `handleRenjuRejectDraw(DSGRenjuRejectDrawTableEvent)`; table field `drawOfferedBySeat` (0 = none). Task 7 consumes the same field indirectly via `gameOver`.

Key existing anchors (verified line numbers): `handleMove` L1974-2133 (undo/cancel implicit-decline block at L2109-2117 relative pattern — the `if (undoRequested)` / `if (cancelRequested)` block right before `broadcastTable(new DSGMoveTableEvent(...))`), `handleUndoReply` L2178 (validation ladder to mirror), `gameOver` L2644, `handleJoin` reset block (`undoRequested = false; cancelRequested = false;` inside the returning-player branch), `sendGameState` L597-613, dispatch switch in `SynchronizedServerTable.callServerTable`.

- [ ] **Step 1: Table state + dispatch**

`ServerTable.java` — add a field next to `undoRequested`/`cancelRequested` (search `private boolean undoRequested`):

```java
    // seat (1|2) that has a renju draw offer pending; 0 = none
    protected int drawOfferedBySeat = 0;
```

`SynchronizedServerTable.java` — in `callServerTable`'s switch, change the move case and add two cases:

```java
                    case DSGMoveTableEvent dsgMoveTableEvent ->
                            serverTable.handleMove(dsgMoveTableEvent.getPlayer(),
                                    dsgMoveTableEvent.getMove(),
                                    dsgMoveTableEvent.isDrawOffer());
```

```java
                    case DSGRenjuAcceptDrawTableEvent dsgRenjuAcceptDrawTableEvent ->
                            serverTable.handleRenjuAcceptDraw(dsgRenjuAcceptDrawTableEvent);
                    case DSGRenjuRejectDrawTableEvent dsgRenjuRejectDrawTableEvent ->
                            serverTable.handleRenjuRejectDraw(dsgRenjuRejectDrawTableEvent);
```

- [ ] **Step 2: `handleMove` — new signature + validation + lifecycle**

Rename the existing method to the 3-arg form and add a compatibility overload:

```java
    public void handleMove(String player, int move) {
        handleMove(player, move, false);
    }

    public void handleMove(String player, int move, boolean drawOffer) {
```

Inside the validation ladder (after the `!gridState.isValidMove(move, seat)` check, before the success `else`), add renju-specific gates:

```java
            } else if (drawOffer &&
                    (!(gridState instanceof RenjuState) ||
                     !((RenjuState) gridState).isOpeningComplete() ||
                     isSeatComputer(3 - seat))) {
                error = DSGTableErrorEvent.INVALID_MOVE;
            } else if (gridState instanceof RenjuState &&
                    ((RenjuState) gridState).isPass(move) &&
                    isSeatComputer(3 - seat)) {
                // AI cannot answer passes in v1
                error = DSGTableErrorEvent.INVALID_MOVE;
            } else {
```

Add the helper (near `noHumanPlayersInTable()` — read that method first and reuse its computer-detection idiom; typical form):

```java
    protected boolean isSeatComputer(int seat) {
        return playingPlayers[seat] != null &&
                playingPlayers[seat].isComputer();
    }
```

(If `DSGPlayerData` exposes a different computer check — e.g. `getComputer() == 'Y'` — copy whatever `noHumanPlayersInTable()` uses.)

In the success block, next to the existing implicit-decline block (the `if (undoRequested) {...}` / `if (cancelRequested) {...}` pair right before `broadcastTable(new DSGMoveTableEvent(player, tableNum, move));`):

```java
                    if (drawOfferedBySeat != 0 && drawOfferedBySeat != seat) {
                        // opponent moved without answering: implicit decline
                        broadcastTable(new DSGRenjuRejectDrawTableEvent(player, tableNum));
                        drawOfferedBySeat = 0;
                    }
                    if (drawOffer) {
                        drawOfferedBySeat = seat;
                    }
```

And replace the move broadcast line with the flag-carrying constructor:

```java
                    broadcastTable(new DSGMoveTableEvent(player, tableNum, move, drawOffer));
```

(Double-pass needs NO extra code here: `gridState.isGameOver()` + `getWinner()==0` already funnel into `gameOver(true, ...)` via the existing L2038-2048 block.)

- [ ] **Step 3: Accept/reject handlers**

Add after `handleUndoReply` (mirror its validation ladder exactly — L2178 shape):

```java
    public void handleRenjuAcceptDraw(DSGRenjuAcceptDrawTableEvent event) {

        int error = NO_ERROR;
        if (!isPlayerInTable(event.getPlayer())) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else {
            int seat = getPlayerSeat(event.getPlayer());
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            } else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
                error = DSGTableErrorEvent.WAIT_GAME_TWO_OF_SET;
            } else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                error = DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN;
            } else if (drawOfferedBySeat == 0 || drawOfferedBySeat == seat) {
                error = DSGTableErrorEvent.NO_DRAW_OFFERED;
            } else {
                drawOfferedBySeat = 0;
                broadcastTable(event);
                gameOver(true, playingPlayers[1].getName(),
                        playingPlayers[2].getName(), false, false, false);
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGRenjuDrawTableErrorEvent(event.getPlayer(), tableNum, error),
                    event.getPlayer());
        }
    }

    public void handleRenjuRejectDraw(DSGRenjuRejectDrawTableEvent event) {

        int error = NO_ERROR;
        if (!isPlayerInTable(event.getPlayer())) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else {
            int seat = getPlayerSeat(event.getPlayer());
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (drawOfferedBySeat == 0 || drawOfferedBySeat == seat) {
                error = DSGTableErrorEvent.NO_DRAW_OFFERED;
            } else {
                drawOfferedBySeat = 0;
                broadcastTable(event);
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGRenjuDrawTableErrorEvent(event.getPlayer(), tableNum, error),
                    event.getPlayer());
        }
    }
```

(`gameOver(true, p1, p2, ...)` with draw=true matches the convention `handleMove` uses for `getWinner()==0` — winner/loser naming is ignored on the draw path.)

- [ ] **Step 4: Clears + rejoin sync**

1. `gameOver(...)` (L2644): add `drawOfferedBySeat = 0;` right after `resetTableGameOver();` — covers accept, resign, timeout, force-resign, double-pass, normal wins.
2. `handleUndoRequest` success branch (right after `undoRequested = true;`): add — mutual exclusion, mirrors TB:

```java
                if (drawOfferedBySeat != 0) {
                    broadcastTable(new DSGRenjuRejectDrawTableEvent(
                            undoRequestEvent.getPlayer(), tableNum));
                    drawOfferedBySeat = 0;
                }
```

   Also in `handleUndoReply` accepted branch (right after `undoRequested = false;`): add `drawOfferedBySeat = 0;` (defensive; should already be 0 by mutual exclusion).
3. Cancel accepted path (`handleCancelReply`, accepted branch — find `cancelRequested = false;` followed by the cancel/game-teardown): add `drawOfferedBySeat = 0;`.
4. `handleJoin` returning-player reset block (`undoRequested = false; cancelRequested = false; ...`): do NOT clear `drawOfferedBySeat` — the offer is game state, not a UI request, and survives reconnects (spec §5).
5. `sendGameState` (L597-613): before `routeEvent`, set the sync field:

```java
        DSGGameStateTableEvent ev = new DSGGameStateTableEvent(toPlayer, tableNum, state, stateMessage, getGameInSet());
        if (drawOfferedBySeat != 0 && playingPlayers[drawOfferedBySeat] != null) {
            ev.setDrawOfferedBy(playingPlayers[drawOfferedBySeat].getName());
        }
        dsgEventRouter.routeEvent(ev, toPlayer);
```

(Refactor the existing inline `new DSGGameStateTableEvent(...)` argument into the local `ev` shown.)

- [ ] **Step 5: Compile + smoke-test the dispatch**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL.

There is no live-table JUnit harness in the repo; verification of this task is by compile + the Task 14 end-to-end checklist. Grep-check the wiring:

```bash
grep -n "handleRenjuAcceptDraw\|handleRenjuRejectDraw\|drawOfferedBySeat" dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java
```

Expected: dispatch cases + field lifecycle sites all present.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/
git commit -S -m "feat(renju): live draw-offer lifecycle + pass gating in ServerTable"
```

---

### Task 7: ServerTable — timeout-draw on `handleTimeUp`

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:2858-2887` (`handleTimeUp`)

**Interfaces:**
- Consumes: Task 4's `RenjuTimeoutDrawEvaluator.opponentCanWin(GridState, int)`; Task 6's `gameOver` clear.
- Produces: renju timeouts end in a draw when the opponent can't win.

- [ ] **Step 1: Modify `handleTimeUp`**

Replace the game-over call inside the timer-expired branch (L2389-2393 region — `gameOver(false, playingPlayers[3 - seat].getName(), timeUpEvent.getPlayer(), false, true, false);`) with:

```java
                if (timers[seat].getMinutes() <= 0 &&
                        timers[seat].getSeconds() <= 0) {

                    boolean timeoutDraw = false;
                    if (gridState instanceof RenjuState) {
                        // draw unless the NON-timed-out player (seat 3-seat,
                        // board color == seat number) can still theoretically win
                        timeoutDraw = !RenjuTimeoutDrawEvaluator.opponentCanWin(
                                gridState, 3 - seat);
                    }

                    if (timeoutDraw) {
                        gameOver(true, playingPlayers[3 - seat].getName(),
                                timeUpEvent.getPlayer(), false, true, false);
                    } else {
                        gameOver(false, playingPlayers[3 - seat].getName(),
                                timeUpEvent.getPlayer(), false, true, false);
                    }
                } else {
```

(`gameOver` draw branch produces "game over, game is a draw" text and draw-flagged rating updates; `timeup=true` keeps `GameData.STATUS_TIMEOUT` on the stored game — exactly the spec's "timeout ending, drawn result".)

Seat/color note (verify while implementing, don't assume): in `ServerTable`, seats 1/2 ARE grid colors 1/2 (`gridState.getCurrentPlayer() != seat` in `handleMove` L2005 compares them directly). Renju seat swaps move PLAYERS between seats; the grid's color-1 remains the first mover. So the non-timed-out player's board color is `3 - seat`.

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -S -m "feat(renju): live timeout ends in draw when opponent cannot theoretically win"
```

---

### Task 8: TBGame — renju passMove, drawOffered flag, timeout-draw derivation

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java`
- Test: `dsg_src/java/org/pente/turnBased/test/TBGamePassDrawTest.java` (new)

**Interfaces:**
- Consumes: existing `passMove` field (L78, default 361), `setGame` (L195-203), `containsDoublePass()` (L88-103), `setWinner` (L480-485), `undoRequested` pattern (L61, L639-645).
- Produces: renju `passMove == 225`; `public boolean isDrawOffered()` / `public void setDrawOffered(boolean)`; `setWinner(0)` derives `draw=true` for BOTH `STATE_COMPLETED` and `STATE_COMPLETED_TO`. Tasks 9-12 rely on these names.

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/turnBased/test/TBGamePassDrawTest.java`:

```java
package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;

public class TBGamePassDrawTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{TBGamePassDrawTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGamePassDrawTest.class);
    }

    public TBGamePassDrawTest(String name) {
        super(name);
    }

    public void testRenjuPassMoveDoublePass() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_RENJU);
        g.addMove(112); // any stones
        g.addMove(113);
        g.addMove(225); // pass
        assertEquals(-1, g.containsDoublePass());
        g.addMove(225); // second consecutive pass
        assertTrue(g.containsDoublePass() >= 0);
    }

    public void testGoPassUnaffected() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_GO);
        g.addMove(361);
        g.addMove(361);
        assertTrue(g.containsDoublePass() >= 0);
    }

    public void testDrawOfferedFlag() {
        TBGame g = new TBGame();
        assertFalse(g.isDrawOffered());
        g.setDrawOffered(true);
        assertTrue(g.isDrawOffered());
    }

    public void testTimeoutDrawDerivation() {
        TBGame g = new TBGame();
        g.timeout(); // -> STATE_COMPLETED_TO
        g.setWinner(0);
        assertTrue(g.isDraw());
    }
}
```

(If `TBGame` lacks a public `addMove(int)` — check; `CacheTBStorer.storeNewMove` calls `game.addMove(move)` so it exists — or a public `timeout()` — `TimeoutCheckRunnable` calls `fresh.timeout()` so it exists — adjust only the test setup, never the assertions.)

- [ ] **Step 2: Run test to verify it fails**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGamePassDrawTest`
Expected: compile error — `isDrawOffered` undefined; `testRenjuPassMoveDoublePass` would fail (renju passMove still 361).

- [ ] **Step 3: Implement**

3a. `setGame` (L195-203) — add the renju branch:

```java
    public void setGame(int game) {
        if (game == GridStateFactory.TB_GO) {
            passMove = 19 * 19;
        } else if (game == GridStateFactory.TB_GO9) {
            passMove = 9 * 9;
        } else if (game == GridStateFactory.TB_GO13) {
            passMove = 13 * 13;
        } else if (game == GridStateFactory.TB_RENJU) {
            passMove = 15 * 15;
        }
        this.game = game;
    }
```

3b. Fields — next to `private boolean undoRequested;` (L61):

```java
    private boolean drawOffered;
```

Accessors — next to `isUndoRequested`/`setUndoRequested` (L639-645):

```java
    public boolean isDrawOffered() {
        return drawOffered;
    }

    public void setDrawOffered(boolean drawOffered) {
        this.drawOffered = drawOffered;
    }
```

3c. `setWinner` (L480-485) — include timeout completions:

```java
    public void setWinner(int winner) {
        this.winner = winner;
        if ((state == STATE_COMPLETED || state == STATE_COMPLETED_TO) && winner == 0) {
            draw = true;
        }
    }
```

(Active games have `state=='A'`, so `winner=0` there still never marks a draw. Verify order-of-set on load: `MySQLTBGameStorer` load path must call `setState` BEFORE `setWinner` for the derivation to fire — grep the loadGame code; if it sets winner first, ALSO derive in `setState` or normalize after load. State the actual fix in the commit message.)

3d. Renju `getCurrentPlayer()` sanity: passes sit in `moves`, so parity-based branches stay correct. Read the renju branch of `getCurrentPlayer()` (mid-body ~L327-350) and confirm it reduces to parity once the opening is complete; if it reconstructs `RenjuState`, Task 1 already made reconstruction pass-safe (`getInstance`/`reconstruct` replay `addMove(225)` — which requires `openingComplete`; confirm `RenjuState.reconstruct` replays opening decisions BEFORE the trailing passes so the replayed `addMove(225)` sees `openingComplete==true`; the reconstruct flow at `RenjuState.java:111-121` commits swap decisions in order, so passes only appear after move 6 in the list — they replay fine).

- [ ] **Step 4: Run tests**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGamePassDrawTest`
Expected: PASS (4 tests).
Also: `ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest` — Expected: PASS (no regression).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGame.java dsg_src/java/org/pente/turnBased/test/TBGamePassDrawTest.java
git commit -S -m "feat(renju): TB passMove=225, drawOffered flag, draw derivation for timeout completions"
```

---

### Task 9: CacheTBStorer + MySQLTBGameStorer — offer persistence, acceptDraw, REASON_DRAW, TB timeout-draw

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`
- Modify: `dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java`

**Interfaces:**
- Consumes: Task 8 (`isDrawOffered/setDrawOffered`), Task 4 (`RenjuTimeoutDrawEvaluator`), Task 1 (`RenjuState` pass support).
- Produces: `CacheTBStorer.offerDraw(long gid)`, `CacheTBStorer.acceptDraw(long gid)`, `EndGameRunnable.Data.REASON_DRAW = 4`. Task 10 calls `offerDraw`/`acceptDraw`.

No DB-backed JUnit harness exists for these classes (`TBStorerTest` is excluded in `build.xml`); verification is compile + the Task 14 end-to-end checklist. Keep changes surgical.

- [ ] **Step 1: `MySQLTBGameStorer.loadMoves` (L496-531) — recognize `-2`**

```java
            while (result.next()) {
                int move = result.getInt(1);
                if (move == -1) {
                    game.setUndoRequested(true);
                } else if (move == -2) {
                    game.setDrawOffered(true);
                } else {
                    moves.add(move);
                }
            }
```

- [ ] **Step 2: `CacheTBStorer.offerDraw` — mirror `requestUndo` (L255+) exactly**

```java
    public void offerDraw(long gid) {
        synchronized (cacheTbLock) {
            TBGame tbGame = getGame(gid);
            try {
                ((MySQLTBGameStorer) baseStorer).storeNewMove(gid, tbGame.getNumMoves(), -2);
                tbGame.setDrawOffered(true);
                persistSet(tbGame.getTbSet());
            } catch (TBStoreException e) {
                log4j.error("offerDraw(" + gid + ")", e);
            }
        }
    }
```

(Copy `requestUndo`'s ACTUAL tail — read it past L260; if it recalculates timeouts or has no try/catch, mirror that form instead of the sketch above. The invariants: write `-2` at `getNumMoves()`, set the flag, persist the set.)

- [ ] **Step 3: `CacheTBStorer.acceptDraw` — mirror `declineUndo` (L234-253) + game end**

```java
    public void acceptDraw(long gid) {
        synchronized (cacheTbLock) {
            TBGame tbGame = getGame(gid);
            if (tbGame.isDrawOffered()) {
                tbGame.setDrawOffered(false);
                // the -2 row is the top move_num row while pending — same
                // MAX(move_num) delete declineUndo uses for the -1 row
                ((MySQLTBGameStorer) baseStorer).undoLastMove(gid);
                tbGame.end();          // STATE_COMPLETED + completionDate
                tbGame.setWinner(0);   // -> draw = true (Task 8 derivation)
                persistSet(tbGame.getTbSet());
                endGameRunnable.endGame(tbGame, EndGameRunnable.Data.REASON_DRAW);
            }
        }
    }
```

(Check how other end paths persist the state/winner columns — `storeNewMove`'s game-over branch does `game.end(); game.setWinner(...); persistSet(...)` then `endGameRunnable.endGame(...)`; if `persistSet` alone doesn't write `tb_game.state/winner`, replicate whatever `storeNewMove`'s game-over path does — e.g. a `baseStorer.updateGame...` call — verbatim.)

- [ ] **Step 4: Mutual exclusion + implicit decline**

1. `requestUndo` (L255+): after `tbGame.setUndoRequested(true);` add:

```java
                tbGame.setDrawOffered(false); // -1 just overwrote any -2 row (same slot)
```

2. `storeNewMove` (L1570+): next to `game.setUndoRequested(false);` (L1656) add:

```java
            game.setDrawOffered(false); // opponent's move overwrites any -2 row (implicit decline)
```

(No DB delete needed in either case — the `-2` row occupied slot `numMoves` and the new `-1`/move row lands on exactly that slot via `ON DUPLICATE KEY UPDATE`. For `storeNewMove`, confirm `actualMoveNum = game.getNumMoves()` is computed from a move list that EXCLUDES sentinels — `loadMoves` strips them — so the slot matches. It does.)

- [ ] **Step 5: `REASON_DRAW` + notification dispatch**

`EndGameRunnable.Data` (L778-791):

```java
            public static final int REASON_WIN = 1;
            public static final int REASON_TO = 2;
            public static final int REASON_RESIGN = 3;
            public static final int REASON_DRAW = 4;
```

In the notification text dispatch (L1095-1174 — `if (data.reason == Data.REASON_RESIGN) ... else if (data.reason == Data.REASON_TO) ... else if (data.reason == Data.REASON_WIN)`): add a no-suffix branch:

```java
        } else if (data.reason == Data.REASON_DRAW) {
            // agreed draw / double-pass: subject+body already switch on game.isDraw()
        }
```

Confirm the draw subject/body selection (`"It's a Draw"` at the `winSubj`/`loseSubj` lines) fires purely on `game.isDraw()` — it does per the excerpt — so no other text work is needed.

- [ ] **Step 6: TB timeout-draw in `TimeoutCheckRunnable` (L655-698)**

Replace the winner assignment in the expiry branch:

```java
                            if (fresh != null
                                    && fresh.getState() == TBGame.STATE_ACTIVE
                                    && fresh.getTimeoutDate() != null
                                    && fresh.getTimeoutDate().getTime() < nowCheck
                                    && pastFloor) {
                                fresh.timeout();
                                int seat = fresh.getPlayerSeat(fresh.getCurrentPlayer());

                                boolean timeoutDraw = false;
                                if (fresh.getGame() == GridStateFactory.TB_RENJU) {
                                    RenjuState rs = RenjuState.reconstruct(
                                            fresh, fresh.getRenjuSwaps(), fresh.getRenjuOffers());
                                    // timed-out player is the one to move; their board
                                    // color is rs.getCurrentPlayer(); opponent = 3 - that
                                    timeoutDraw = !RenjuTimeoutDrawEvaluator.opponentCanWin(
                                            rs, 3 - rs.getCurrentPlayer());
                                }

                                if (timeoutDraw) {
                                    fresh.setWinner(0);
                                    fresh.setDraw(true); // explicit: state is 'T'
                                } else {
                                    fresh.setWinner(3 - seat);
                                }
                                persistSet(fresh.getTbSet());
                                doEnd = true;
                            }
```

(`RenjuState.reconstruct(fresh, ...)` accepts `fresh` because `TBGame implements MoveData` — the same call `getRenjuPhase()` makes. Note the evaluator only reads positions, and passes never placed stones, so the reconstructed grid is exactly the board. `endGameRunnable.endGame(fresh, REASON_TO)` stays as is: reason TO gives the "ran out of time" context line while `isDraw()` flips subject/body to the draw variants — the desired combination.)

- [ ] **Step 7: Audit every other `tb_move` reader**

The `-1` undo sentinel already flows through the table; every reader must now equally tolerate `-2`:

```bash
grep -rn "tb_move" dsg_src/java --include=*.java | grep -v test
```

For each hit besides `MySQLTBGameStorer.loadMoves`/`storeNewMove`/`undoLastMove` (expect: archival/stats/replica-sync code, `tb_move_ai` variants): check how it treats `move = -1` today and give `-2` the identical treatment (skip). List the audited files in the commit message. If a reader would break on `-1` too, note it as a pre-existing bug — do not fix here.

- [ ] **Step 8: Compile + commit**

Run: `./justCompile` — Expected: BUILD SUCCESSFUL.

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java
git commit -S -m "feat(renju): TB draw-offer persistence (-2 sentinel), acceptDraw, REASON_DRAW, timeout-draw check"
```

---

### Task 10: MoveServlet — drawOffer param, acceptDraw command

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java`

**Interfaces:**
- Consumes: Task 9 (`offerDraw`, `acceptDraw`), Task 8 (`isDrawOffered`, `RENJU_COMPLETE`).
- Produces: `command=move&drawOffer=true` arms an offer after the move; `command=acceptDraw` ends the game drawn. Task 12 (JSP) and the mobile clients call these.

- [ ] **Step 1: `move` command — parse + validate + persist the offer**

In the `move` block (L344+), after `String renjuAction = request.getParameter("renjuAction");`:

```java
                boolean drawOffer = "true".equals(request.getParameter("drawOffer"));
                if (drawOffer &&
                        (game.getGame() != GridStateFactory.TB_RENJU ||
                         !TBGame.RENJU_COMPLETE.equals(game.getRenjuPhase()))) {
                    log4j.error("MoveServlet, draw offer outside renju/opening-complete " + gid);
                    handleError(request, response,
                            "Draw offers are only available in Renju after the opening.");
                    return;
                }
```

After the move has been stored successfully (locate the post-`storeNewMove` success path — the refreshed-game reload `TBGame refreshedGame = tbGameStorer.loadGame(gid);` block at ~L690 — place this right after the reload):

```java
                if (drawOffer && refreshedGame != null &&
                        refreshedGame.getState() == TBGame.STATE_ACTIVE) {
                    ((CacheTBStorer) tbGameStorer).offerDraw(gid);
                    game = tbGameStorer.loadGame(gid); // pick up drawOffered for the forward
                }
```

(If the move ended the game — five, double-pass — the offer is moot and skipped. The pass move itself needs NO servlet change: `moves=225` flows through the generic path and `CacheTBStorer.storeNewMove`'s renju validation (`rs.isValidMove(move, ...)` — Task 1 accepts pass only when opening complete.)

- [ ] **Step 2: `acceptDraw` command — mirror `declineUndo` (L313-343)**

Add after the `declineUndo` block:

```java
            } else if (command.equals("acceptDraw")) {
                if (game.getCurrentPlayer() != playerData.getPlayerID()) {
                    log4j.error("MoveServlet, out-of-turn draw accept");
                    handleError(request, response,
                            "Accepting a draw is available when it's your turn.");
                    return;
                }
                if (!game.isDrawOffered() || game.getState() != TBGame.STATE_ACTIVE) {
                    log4j.error("MoveServlet, no draw offer exists " + gid);
                    handleError(request, response, "No draw offer exists.");
                    return;
                }
                ((CacheTBStorer) tbGameStorer).acceptDraw(gid);
                game = tbGameStorer.loadGame(gid);
                request.setAttribute("game", game);

                log4j.debug("forward to game page");
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(gamePage).forward(
                            request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileGamePage).forward(
                            request, response);
                }
                log4j.debug("done forwarding");
                return;
```

- [ ] **Step 3: Move-notification wording (verify-only)**

Find `notificationServer.sendMoveNotification(...)` (~L697). If its message text is free-form, append " and offers a draw" when `drawOffer`; if the signature is fixed (name, currentPlayer, gid, gameName — no text), make NO change: the `drawOffered` flag in the JSON (Task 11) is the offer's carrier and clients render it. Record which case applied in the commit message.

- [ ] **Step 4: Compile + commit**

Run: `./justCompile` — Expected: BUILD SUCCESSFUL.

```bash
git add dsg_src/java/org/pente/turnBased/web/MoveServlet.java
git commit -S -m "feat(renju): MoveServlet drawOffer param + acceptDraw command"
```

---

### Task 11: GameResponse — drawOffered field

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java`

**Interfaces:**
- Produces: JSON field `drawOffered` (Boolean; true only while active + pending). Mobile clients poll this.

- [ ] **Step 1: Add the field through the constructor chain**

Field (next to `public final Boolean undoRequested;`):

```java
    public final Boolean drawOffered;
```

Private constructor: add parameter `Boolean drawOffered` right after `Boolean undoRequested`, assign `this.drawOffered = drawOffered;`.

`build(...)`: pass, right after `tbGame.isUndoRequested()`:

```java
                tbGame.getState() == TBGame.STATE_ACTIVE ? tbGame.isDrawOffered() : Boolean.FALSE,
```

`buildHistoric(...)`: pass `null` in the same position (extend the existing null run).

- [ ] **Step 2: Compile + commit**

Run: `./justCompile` — Expected: BUILD SUCCESSFUL.

```bash
git add dsg_src/java/org/pente/gameServer/mobile/GameResponse.java
git commit -S -m "feat(renju): expose drawOffered in mobile game JSON"
```

---

### Task 12: mobileGame.jsp — PASS + DRAW? buttons, offer indicator, accept link

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

**Interfaces:**
- Consumes: Tasks 8/10 (`RENJU_COMPLETE`, `drawOffer` param, `acceptDraw` command, `game.isDrawOffered()`); existing `submitPass()` already posts `moves=gridSize*gridSize` = 225 for renju (gridSize=15 at L119-126).
- Produces: web TB renju UI for pass + draw offers.

- [ ] **Step 1: Gating var**

Next to the `renjuDecision` computation (L482-491):

```jsp
                     boolean renjuComplete = game.getGame() == GridStateFactory.TB_RENJU
                                          && TBGame.RENJU_COMPLETE.equals(game.getRenjuPhase());
```

- [ ] **Step 2: Buttons**

Inside the `myTurn && dPenteState != 2` branch, right after the Go pass block (`<% if (isGo) { %>...<% } %>` at L1503-1511), add:

```jsp
                     <% if (renjuComplete) { %>
                     <a class="boldbuttons" href="javascript:submitPass();"
                        style="margin-right:5px;"><span>Pass</span></a>
                     <a class="boldbuttons" href="javascript:toggleDrawOffer();" id="drawOfferBtn"
                        style="margin-right:5px;"><span>Draw?</span></a>
                     <span id="drawOfferNote" style="display:none; font-weight:bold;">
                        Draw offer will be sent after you move</span>
                     <% if (game.isDrawOffered()) { %>
                     <b style="margin-left:10px;">Draw offered</b>
                     <a class="boldbuttons" href="javascript:acceptDraw();"
                        style="margin-left:5px;"><span>Accept draw</span></a>
                     <% } %>
                     <% } %>
```

(When it's my turn and `isDrawOffered()`, the offerer is the opponent — accepting is valid. Playing any move declines implicitly, no extra UI needed. For the OFFERER (not my turn): add next to the "Undo requested" indicator chain (L1512-1514 else-if):)

```jsp
                     <% } else if ((game.getPlayer1Pid() == meData.getPlayerID() || game.getPlayer2Pid() == meData.getPlayerID())
                                   && game.isDrawOffered() && "false".equals(myTurn)) { %>
                     <b>Draw offered &mdash; awaiting reply</b>
```

(Slot it as an additional `else if` in the same chain; keep the existing undo branch intact.)

- [ ] **Step 3: JS — arming + URL params + accept**

In the script section (near `submitPass()`, L1428):

```javascript
   var drawArmed = false;

   function toggleDrawOffer() {
      drawArmed = !drawArmed;
      var btn = document.getElementById('drawOfferBtn');
      var note = document.getElementById('drawOfferNote');
      if (drawArmed) {
         btn.style.backgroundColor = 'green';
         note.style.display = 'inline';
      } else {
         btn.style.backgroundColor = '';
         note.style.display = 'none';
      }
   }

   function drawOfferParam() {
      return drawArmed ? "&drawOffer=true" : "";
   }

   function acceptDraw() {
      window.open("/gameServer/tb/game?command=acceptDraw&gid=" + <%=game.getGid()%>, "_self");
   }
```

In `submit()` (L1385-1426): append `+ drawOfferParam()` to the URL in the FINAL generic branch only (renju never reaches the connect6/dpente/swap2 branches):

```javascript
         window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
         cycleStr + hideStr + "&moves=" + playedMove + "&message=" + encodeURIComponent(document.getElementById('message').value) + drawOfferParam(), "_self"
      )
```

In `submitPass()` (L1428-1433), same append:

```javascript
      window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
      cycleStr + hideStr + "&moves=" + (gridSize * gridSize) + "&message=" + encodeURIComponent(document.getElementById('message').value) + drawOfferParam(), "_self"
   )
```

(PASS stays visible while armed — the spec's rule; the move list already renders 225 as "PASS" via the L479 ternary, no change.)

- [ ] **Step 4: Manual smoke via local stack + commit**

Bring up the local docker stack and load a renju TB game past the opening with two test accounts; verify: PASS button posts and renders "PASS" in the list; DRAW? arms (green + note) and the next move/pass shows "Draw offered" to the opponent; Accept ends the game as a draw; playing instead clears the offer. (Test accounts per `pente-react-native/.env.local`; do not commit credentials.)

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -S -m "feat(renju): JSP TB board — pass button, draw-offer arming, offer indicator + accept"
```

---

### Task 13: PGN — serialize renju passes

**Files:**
- Modify: `dsg_src/java/org/pente/game/PGNGameFormat.java`
- Test: extend an existing PGN test class if one exists (`grep -rn "PGNGameFormat" dsg_src/java --include=*Test*.java`), else add `dsg_src/java/org/pente/game/test/PGNRenjuPassTest.java`.

**Interfaces:**
- Consumes: move loop L204-232 (`formatCoordinates(move)`), `parseCoordinates` L925-941, result mapping L814-860 (already draw-correct).
- Produces: renju pass (225) serialized as literal `pass`, parsed back to 225.

- [ ] **Step 1: Failing test**

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class PGNRenjuPassTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{PGNRenjuPassTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(PGNRenjuPassTest.class);
    }

    public PGNRenjuPassTest(String name) {
        super(name);
    }

    public void testPassTokenRoundTrip() {
        assertEquals("pass", PGNGameFormat.formatMoveToken(225, true));
        assertEquals(225, PGNGameFormat.parseMoveToken("pass", true));
        // non-pass moves unchanged
        assertEquals(PGNGameFormat.formatCoordinates(100),
                PGNGameFormat.formatMoveToken(100, true));
    }
}
```

- [ ] **Step 2: Run — fails (methods missing)**

Run: `ant test-one -Dtest=org.pente.game.test.PGNRenjuPassTest`

- [ ] **Step 3: Implement**

Add to `PGNGameFormat`:

```java
    /** Renju: 225 (=15*15) is a pass; serialized as the literal token "pass". */
    public static String formatMoveToken(int move, boolean renju) {
        if (renju && move == 15 * 15) {
            return "pass";
        }
        return formatCoordinates(move);
    }

    public static int parseMoveToken(String token, boolean renju) {
        if (renju && "pass".equalsIgnoreCase(token)) {
            return 15 * 15;
        }
        return parseCoordinates(token);
    }
```

In the serialization loop (L204-232), determine renju-ness from the game data (`data.getGame()` — verify the accessor; `GameOverUtilities`/storers pass `GridStateFactory.RENJU`-family ids) and use `formatMoveToken(move, isRenju)` instead of `formatCoordinates(move)`. In the move-list PARSING path (find where move tokens are read — search `parseCoordinates` call sites in this file), route through `parseMoveToken(token, isRenju)` and skip/accept "pass" accordingly.

KNOWN PRE-EXISTING GAP (do not fix here, note only): `formatCoordinates` hardcodes 19×19 (`p % 19`), so 15×15 renju coordinates in PGN are already questionable for x>14 columns; this task only prevents a 225 pass from emitting a bogus coordinate. If the renju flag is unavailable at a call site, fall back to `move == 225 && data`-game-family check inline.

- [ ] **Step 4: Run + commit**

Run: `ant test-one -Dtest=org.pente.game.test.PGNRenjuPassTest` — Expected: PASS.

```bash
git add dsg_src/java/org/pente/game/PGNGameFormat.java dsg_src/java/org/pente/game/test/PGNRenjuPassTest.java
git commit -S -m "feat(renju): PGN pass token for move 225"
```

---

### Task 14: End-to-end verification checklist (manual, local stack)

**Files:** none (verification only). Run after Tasks 1-13.

- [ ] Build clean: `./justCleanCompile` — BUILD SUCCESSFUL.
- [ ] Full renju unit sweep:

```
ant test-one -Dtest=org.pente.game.test.RenjuPassTest
ant test-one -Dtest=org.pente.game.test.RenjuTimeoutDrawEvaluatorTest
ant test-one -Dtest=org.pente.turnBased.test.TBGamePassDrawTest
ant test-one -Dtest=org.pente.game.test.PGNRenjuPassTest
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest
ant test-one -Dtest=org.pente.turnBased.web.test.RenjuTbContractTest
```

- [ ] TB flows on the local docker stack (two browsers, test accounts): pass after opening; pass rejected during opening (button absent + direct URL `moves=225` errors); double-pass → "It's a Draw" for both; offer→accept; offer→opponent-moves (offer cleared, no draw); offer→offerer-requests-undo (offer cancelled, undo pending — DB shows single `-1` row); undo request blocked during opening (existing behavior intact).
- [ ] tb_move hygiene after each flow: `select * from tb_move where gid=<gid> order by move_num` — sentinels never duplicated, no stray rows after acceptDraw.
- [ ] Live flows are exercised by the react-live-game-room plan's checklist (plan 2); server-side compile + code review cover Tasks 5-7 until then.
- [ ] Timeout-draw: covered by evaluator unit tests; optionally set a 1-day `daysPerMove` game in DB with a congested drawn board and force `timeout_date` into the past, then watch the runnable end it as a draw ('T', winner=0, "It's a Draw" messages).

---

## Execution notes

- Tasks 1-4 are pure rules-core and can be executed strictly in order by one engineer/subagent chain; Tasks 5-7 (live) and 8-12 (TB+JSP) are independent of each other after Task 4, but keep the numbered order unless parallelizing across worktrees.
- Model policy for this repo: implementation subagents run on opus (rules core, ServerTable, storers) or sonnet (JSP, GameResponse, PGN, event POJOs); reviews on opus.
- The four excerpt files under the session scratchpad (`excerpts-rules-core.md`, `excerpts-live-server.md`, `excerpts-tb-server.md`, plus client ones) contain verbatim source context gathered for this plan; implementers should trust the REPO, not the excerpts, wherever they diverge.
