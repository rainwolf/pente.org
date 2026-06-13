# RenjuState + Taraguchi-10 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Renju rules (black exact-5 win, white 5+ win, black forbidden points) and the full Taraguchi-10 opening protocol to the `org.pente.game` package.

**Architecture:** Two new classes. `RenjuForbiddenPointFinder` is a pure, board-only Java port of the C++ `CForbiddenPointFinder` (`../ForbiddenPointFinder`). `RenjuState extends GridStateDecorator` wraps a `SimpleGomokuState` (board store + Zobrist hashing) and overrides win detection, move validation, and turn sequencing. Swaps are sequenced + recorded only; the seat↔color mapping stays in the controller layer (matching `SimplePenteState`'s swap2/dPente convention). Stone color is fixed by move parity: `getCurrentColor() = numMoves%2+1`, color 1 = black, color 2 = white.

**Tech Stack:** Java (Tomcat backend), JUnit 3 (`junit.framework.TestCase`, `junit.textui.TestRunner`), Ant build.

**Spec:** `docs/superpowers/specs/2026-06-13-renju-state-design.md`

## Build & Test Commands

Production source lives in `dsg_src/java/...` but `ant compile` builds from the synced `deploy/` dir. So after creating/editing any **production** `.java` file you must sync first:

```bash
./justCompile                                                  # sync dsg_src/java → deploy + compile production
ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```

`compile-tests` (an `ant test-one` dependency) compiles `**/test/**/*.java` straight from `dsg_src/java`, so test files do NOT need a sync — but the production classes they exercise do. When in doubt, run `./justCompile` before `ant test-one`.

Run from: `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`

## File Structure

- Create: `dsg_src/java/org/pente/game/RenjuForbiddenPointFinder.java` — pure forbidden-point engine, no GridState dependency.
- Create: `dsg_src/java/org/pente/game/RenjuState.java` — `GridStateDecorator` subclass: win rules, opening protocol, validation.
- Create: `dsg_src/java/org/pente/game/test/RenjuForbiddenPointFinderTest.java`
- Create: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

---

## Porting Notes (read before Task 1)

The C++ uses `cBoard[i--]` / `cBoard[i++]` inside `if` conditions: the index is mutated **every** iteration regardless of the branch. Faithful Java for a left scan `i = x; while (i > 0) { if (cBoard[i--][y+1] == c) nLine++; else break; }` is:

```java
i = x;
while (i > 0) {
    boolean match = b[i][y + 1] == c;
    i--;
    if (match) nLine++; else break;
}
```

The `IsFour` / `IsOpenFour` / `IsOpenThree` scans instead use plain `cBoard[i]` and mutate `i` manually inside the body — those translate 1:1 with no trick.

Other invariants preserved verbatim from the C++:
- `isFive(x,y,0)` (black, all-directions) returns true only when a direction is **exactly** 5; `isFive(x,y,1)` (white) returns true on **≥5**.
- The 4-arg `isFive(x,y,color,dir)` always tests `== 5` regardless of color (color only selects the stone char).
- `isFour`/`isOpenFour`/`isOpenThree` call `isFive(..., 0, nDir)` with color hard-coded to `0` in the gap check — keep the literal `0`.
- `isOpenThree` ↔ `isDoubleThree` and `isOpenFour` ↔ `isDoubleFour` are mutually recursive. They must all exist before any of them compiles — implemented together in Task 1.
- `isOverline` returns `false` (not overline) if any direction is exactly 5.
- Internal board is `(size+2)×(size+2)` with a `'$'` sentinel border; callers use 0-based `[0,size-1]` and the class adds `+1`.

---

## Task 1: RenjuForbiddenPointFinder — full faithful port

**Files:**
- Create: `dsg_src/java/org/pente/game/RenjuForbiddenPointFinder.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuForbiddenPointFinderTest.java`

Because of the mutual recursion (`isOpenThree`↔`isDoubleThree`, `isOpenFour`↔`isDoubleFour`), the whole engine is ported in one task, with a behavioral test matrix written first.

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/game/test/RenjuForbiddenPointFinderTest.java`. Coordinates are 0-based `(x,y)`. Default board 15×15.

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuForbiddenPointFinderTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                RenjuForbiddenPointFinderTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuForbiddenPointFinderTest.class);
    }

    public RenjuForbiddenPointFinderTest(String name) {
        super(name);
    }

    private RenjuForbiddenPointFinder f;

    protected void setUp() {
        f = new RenjuForbiddenPointFinder(15);
    }

    // place a horizontal run of color c starting at (x,y) going right
    private void row(int x, int y, int len, char c) {
        for (int k = 0; k < len; k++) f.setStone(x + k, y, c);
    }

    public void testBlackExactFiveIsFive() {
        row(3, 7, 4, RenjuForbiddenPointFinder.BLACK); // x=3,4,5,6
        assertTrue(f.isFive(7, 7, 0));                  // filling x=7 makes 5
    }

    public void testBlackSixIsNotFive() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK); // x=3..7
        assertFalse(f.isFive(8, 7, 0));                 // filling x=8 makes 6, not exact 5
    }

    public void testWhiteFiveIsFive() {
        row(3, 7, 4, RenjuForbiddenPointFinder.WHITE);
        assertTrue(f.isFive(7, 7, 1));
    }

    public void testWhiteSixIsFive() {
        row(3, 7, 5, RenjuForbiddenPointFinder.WHITE);
        assertTrue(f.isFive(8, 7, 1));                  // white wins on 6 too
    }

    public void testBlackOverline() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK); // x=3..7
        assertTrue(f.isOverline(8, 7));                 // filling x=8 makes 6
    }

    public void testExactFiveIsNotOverline() {
        row(3, 7, 4, RenjuForbiddenPointFinder.BLACK);
        assertFalse(f.isOverline(7, 7));                // exactly 5 is a win, not overline
    }

    public void testForbiddenOverline() {
        row(3, 7, 5, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isForbidden(8, 7));
    }

    // Double-three: two independent open threes meeting at (7,7).
    // Horizontal open three: stones at (5,7),(6,7); vertical open three: (7,5),(7,6).
    public void testDoubleThreeForbidden() {
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isDoubleThree(7, 7));
        assertTrue(f.isForbidden(7, 7));
    }

    // Single open three is NOT forbidden.
    public void testSingleThreeNotForbidden() {
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        assertFalse(f.isDoubleThree(7, 7));
        assertFalse(f.isForbidden(7, 7));
    }

    // Double-four: two fours through (7,7).
    // Horizontal: (4,7),(5,7),(6,7) and (8,7),(9,7),(10,7) -> playing (7,7) makes 7-in-line
    // which is overline, so use two separate four-threats instead:
    // Horizontal four: (3,7),(4,7),(5,7),(6,7) already 4 -> (7,7) makes five (excluded).
    // Use a gapped four on each axis so (7,7) completes a four (not five) in two directions.
    // Horizontal: stones (4,7),(5,7),(6,7) with empty (3,7) and (8,7): (7,7) -> four.
    // Vertical:   stones (7,4),(7,5),(7,6) with empty (7,3) and (7,8): (7,7) -> four.
    public void testDoubleFourForbidden() {
        f.setStone(4, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(5, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(6, 7, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 4, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 5, RenjuForbiddenPointFinder.BLACK);
        f.setStone(7, 6, RenjuForbiddenPointFinder.BLACK);
        assertTrue(f.isDoubleFour(7, 7));
        assertTrue(f.isForbidden(7, 7));
    }

    public void testEmptyBoardNoForbidden() {
        assertFalse(f.isForbidden(7, 7));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest
```
Expected: FAIL — compile error, `RenjuForbiddenPointFinder` does not exist.

- [ ] **Step 3: Write the full implementation**

Create `dsg_src/java/org/pente/game/RenjuForbiddenPointFinder.java`. This is a verbatim port — keep the structure identical to the C++ so it can be diffed against the source.

```java
package org.pente.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Faithful Java port of CForbiddenPointFinder (C++, by Wenzhe Lu).
 * Detects Renju forbidden points for black: overline, double-four, double-three.
 * Pure board logic — no GridState dependency. 0-based external coordinates.
 */
public class RenjuForbiddenPointFinder {

    public static final char BLACK = 'X';
    public static final char WHITE = 'O';
    public static final char EMPTY = '.';
    public static final char BORDER = '$';

    private final int size;
    private final char[][] b; // (size+2) x (size+2), 1-based playable region

    public RenjuForbiddenPointFinder() {
        this(15);
    }

    public RenjuForbiddenPointFinder(int size) {
        this.size = size;
        this.b = new char[size + 2][size + 2];
        clear();
    }

    public int getSize() {
        return size;
    }

    public void clear() {
        for (int i = 0; i < size + 2; i++) {
            b[0][i] = BORDER;
            b[size + 1][i] = BORDER;
            b[i][0] = BORDER;
            b[i][size + 1] = BORDER;
        }
        for (int i = 1; i <= size; i++) {
            for (int j = 1; j <= size; j++) {
                b[i][j] = EMPTY;
            }
        }
    }

    public void setStone(int x, int y, char stone) {
        b[x + 1][y + 1] = stone;
    }

    public char getStone(int x, int y) {
        return b[x + 1][y + 1];
    }

    /** Black (nColor==0): exactly 5. White (nColor==1): 5 or more. */
    public boolean isFive(int x, int y, int nColor) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        char c;
        boolean exact; // black: require ==5; white: >=5
        if (nColor == 0) { c = BLACK; exact = true; }
        else if (nColor == 1) { c = WHITE; exact = false; }
        else { return false; }

        setStone(x, y, c);
        boolean five = false;
        for (int dir = 1; dir <= 4 && !five; dir++) {
            int n = countLine(x, y, c, dir);
            five = exact ? (n == 5) : (n >= 5);
        }
        setStone(x, y, EMPTY);
        return five;
    }

    /** Count consecutive stones of color c through (x,y) along dir (the placed stone counts). */
    private int countLine(int x, int y, char c, int dir) {
        int nLine = 1;
        int i, j;
        switch (dir) {
            case 1: // horizontal
                i = x;
                while (i > 0) { boolean m = b[i][y + 1] == c; i--; if (m) nLine++; else break; }
                i = x + 2;
                while (i < size + 1) { boolean m = b[i][y + 1] == c; i++; if (m) nLine++; else break; }
                break;
            case 2: // vertical
                i = y;
                while (i > 0) { boolean m = b[x + 1][i] == c; i--; if (m) nLine++; else break; }
                i = y + 2;
                while (i < size + 1) { boolean m = b[x + 1][i] == c; i++; if (m) nLine++; else break; }
                break;
            case 3: // diagonal '/'
                i = x; j = y;
                while (i > 0 && j > 0) { boolean m = b[i][j] == c; i--; j--; if (m) nLine++; else break; }
                i = x + 2; j = y + 2;
                while (i < size + 1 && j < size + 1) { boolean m = b[i][j] == c; i++; j++; if (m) nLine++; else break; }
                break;
            case 4: // diagonal '\'
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) { boolean m = b[i][j] == c; i--; j++; if (m) nLine++; else break; }
                i = x + 2; j = y;
                while (i < size + 1 && j > 0) { boolean m = b[i][j] == c; i++; j--; if (m) nLine++; else break; }
                break;
            default:
                return 0;
        }
        return nLine;
    }

    /** True if placing black at (x,y) makes 6+ in some direction (but a direction of exactly 5 cancels). */
    public boolean isOverline(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        setStone(x, y, BLACK);
        boolean overline = false;
        for (int dir = 1; dir <= 4; dir++) {
            int n = countLine(x, y, BLACK, dir);
            if (n == 5) {
                setStone(x, y, EMPTY);
                return false;
            } else {
                overline |= (n >= 6);
            }
        }
        setStone(x, y, EMPTY);
        return overline;
    }

    /** Single-direction five (exactly 5), color only picks the stone char. */
    public boolean isFive(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        int n = countLine(x, y, c, dir);
        setStone(x, y, EMPTY);
        return n == 5;
    }

    /** True if placing color at (x,y) yields exactly a four (one empty extension makes five) along dir. */
    public boolean isFour(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, nColor)) return false;
        if (nColor == 0 && isOverline(x, y)) return false;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        boolean result = false;
        int i, j;
        switch (dir) {
            case 1:
                i = x;
                while (i > 0) {
                    if (b[i][y + 1] == c) { i--; continue; }
                    else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; continue; }
                        else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i > 0) {
                    if (b[x + 1][i] == c) { i--; continue; }
                    else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; continue; }
                        else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i > 0 && j > 0) {
                    if (b[i][j] == c) { i--; j--; continue; }
                    else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) {
                    if (b[i][j] == c) { i--; j++; continue; }
                    else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return result;
    }

    /** 0 = not open four; 1 = open four of exactly 4 stones; 2 = 5-stone variant (counts double). */
    public int isOpenFour(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) return 0;
        if (isFive(x, y, nColor)) return 0;
        if (nColor == 0 && isOverline(x, y)) return 0;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return 0;

        setStone(x, y, c);
        int nLine = 1;
        int i, j;
        int ret = 0;
        boolean done = false;
        switch (dir) {
            case 1:
                i = x;
                while (i >= 0) {
                    if (b[i][y + 1] == c) { i--; nLine++; continue; }
                    else if (b[i][y + 1] == EMPTY) { if (!isFive(i - 1, y, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; nLine++; continue; }
                        else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i >= 0) {
                    if (b[x + 1][i] == c) { i--; nLine++; continue; }
                    else if (b[x + 1][i] == EMPTY) { if (!isFive(x, i - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; nLine++; continue; }
                        else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i >= 0 && j >= 0) {
                    if (b[i][j] == c) { i--; j--; nLine++; continue; }
                    else if (b[i][j] == EMPTY) { if (!isFive(i - 1, j - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; nLine++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i >= 0 && j <= size + 1) {
                    if (b[i][j] == c) { i--; j++; nLine++; continue; }
                    else if (b[i][j] == EMPTY) { if (!isFive(i - 1, j - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; nLine++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return ret;
    }

    public boolean isDoubleFour(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, 0)) return false;

        int nFour = 0;
        for (int dir = 1; dir <= 4; dir++) {
            if (isOpenFour(x, y, 0, dir) == 2) nFour += 2;
            else if (isFour(x, y, 0, dir)) nFour++;
        }
        return nFour >= 2;
    }

    public boolean isOpenThree(int x, int y, int nColor, int dir) {
        if (isFive(x, y, nColor)) return false;
        if (nColor == 0 && isOverline(x, y)) return false;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        boolean result = false;
        int i, j;
        switch (dir) {
            case 1:
                i = x;
                while (i > 0) {
                    if (b[i][y + 1] == c) { i--; continue; }
                    else if (b[i][y + 1] == EMPTY) {
                        if (isOpenFour(i - 1, y, nColor, dir) == 1 && !isDoubleFour(i - 1, y) && !isDoubleThree(i - 1, y)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; continue; }
                        else if (b[i][y + 1] == EMPTY) {
                            if (isOpenFour(i - 1, y, nColor, dir) == 1 && !isDoubleFour(i - 1, y) && !isDoubleThree(i - 1, y)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i > 0) {
                    if (b[x + 1][i] == c) { i--; continue; }
                    else if (b[x + 1][i] == EMPTY) {
                        if (isOpenFour(x, i - 1, nColor, dir) == 1 && !isDoubleFour(x, i - 1) && !isDoubleThree(x, i - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; continue; }
                        else if (b[x + 1][i] == EMPTY) {
                            if (isOpenFour(x, i - 1, nColor, dir) == 1 && !isDoubleFour(x, i - 1) && !isDoubleThree(x, i - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i > 0 && j > 0) {
                    if (b[i][j] == c) { i--; j--; continue; }
                    else if (b[i][j] == EMPTY) {
                        if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; continue; }
                        else if (b[i][j] == EMPTY) {
                            if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) {
                    if (b[i][j] == c) { i--; j++; continue; }
                    else if (b[i][j] == EMPTY) {
                        if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; continue; }
                        else if (b[i][j] == EMPTY) {
                            if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return result;
    }

    public boolean isDoubleThree(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, 0)) return false;

        int nThree = 0;
        for (int dir = 1; dir <= 4; dir++) {
            if (isOpenThree(x, y, 0, dir)) nThree++;
        }
        return nThree >= 2;
    }

    /** A point is forbidden for black if it makes an overline, double-four, or double-three. */
    public boolean isForbidden(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        return isOverline(x, y) || isDoubleFour(x, y) || isDoubleThree(x, y);
    }

    /** Scan the whole board for black forbidden points (for UI hinting). */
    public List<Coord> findForbiddenPoints() {
        List<Coord> pts = new ArrayList<Coord>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (b[i + 1][j + 1] != EMPTY) continue;
                if (isOverline(i, j) || isDoubleFour(i, j) || isDoubleThree(i, j)) {
                    pts.add(new Coord(i, j));
                }
            }
        }
        return pts;
    }
}
```

> **Note on the `isFour`/`isOpenFour` refactor:** the C++ `return` inside a switch is reproduced with a `result`/`ret`/`done` flag + a single `setStone(EMPTY)` at the end, so the temp stone is always restored on every path. Behavior is identical to the C++ early returns.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest
```
Expected: PASS (OK, 11 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuForbiddenPointFinder.java \
        dsg_src/java/org/pente/game/test/RenjuForbiddenPointFinderTest.java
git commit -m "feat(renju): port ForbiddenPointFinder to Java

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: RenjuState skeleton + win semantics

**Files:**
- Create: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/game/test/RenjuStateTest.java`. These tests bypass the opening rules by constructing the state and adding raw moves; opening enforcement is layered on in later tasks but win detection is independent of it.

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                RenjuStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuStateTest.class);
    }

    public RenjuStateTest(String name) {
        super(name);
    }

    private RenjuState newState() {
        return new RenjuState(15, 15);
    }

    private int xy(RenjuState s, int x, int y) {
        return s.convertMove(x, y);
    }

    private void add(RenjuState s, int... moves) {
        for (int m : moves) s.addMove(m);
    }

    // Black plays color 1 (even move indices), white color 2 (odd).
    // Interleave with throwaway white stones far from the action.
    public void testBlackExactFiveWins() {
        RenjuState s = newState();
        // black at (3,7),(4,7),(5,7),(6,7),(7,7); white scattered on row 0
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),
            xy(s, 4, 7), xy(s, 1, 0),
            xy(s, 5, 7), xy(s, 2, 0),
            xy(s, 6, 7), xy(s, 3, 0),
            xy(s, 7, 7));
        assertTrue(s.isGameOver());
        assertEquals(1, s.getWinner());
    }

    public void testBlackOverlineNotWin() {
        RenjuState s = newState();
        // black 6 in a row (3..8,7) — not a win for black
        add(s,
            xy(s, 3, 7), xy(s, 0, 0),
            xy(s, 4, 7), xy(s, 1, 0),
            xy(s, 5, 7), xy(s, 2, 0),
            xy(s, 6, 7), xy(s, 3, 0),
            xy(s, 7, 7), xy(s, 4, 0),
            xy(s, 8, 7));
        assertFalse(s.isGameOver());
    }

    public void testWhiteFiveWins() {
        RenjuState s = newState();
        // white at (3,7)..(7,7); black scattered
        add(s,
            xy(s, 0, 0), xy(s, 3, 7),
            xy(s, 1, 0), xy(s, 4, 7),
            xy(s, 2, 0), xy(s, 5, 7),
            xy(s, 3, 0), xy(s, 6, 7),
            xy(s, 4, 0), xy(s, 7, 7));
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());
    }

    public void testWhiteOverlineWins() {
        RenjuState s = newState();
        // white 6 in a row wins
        add(s,
            xy(s, 0, 0), xy(s, 3, 7),
            xy(s, 1, 0), xy(s, 4, 7),
            xy(s, 2, 0), xy(s, 5, 7),
            xy(s, 3, 0), xy(s, 6, 7),
            xy(s, 4, 0), xy(s, 7, 7),
            xy(s, 5, 0), xy(s, 8, 7));
        assertTrue(s.isGameOver());
        assertEquals(2, s.getWinner());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — `RenjuState` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `dsg_src/java/org/pente/game/RenjuState.java`. This task implements construction, finder refresh, and win semantics only; the opening/validation overrides come in later tasks (default to delegating for now).

```java
package org.pente.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Renju rules + Taraguchi-10 opening, as a decorator over a SimpleGomokuState.
 * Black (color 1) wins only on exactly five; white (color 2) wins on five or more.
 * Black may not play a forbidden point (overline / double-four / double-three).
 */
public class RenjuState extends GridStateDecorator implements GomokuState, HashCalculator {

    private final RenjuForbiddenPointFinder finder;

    public RenjuState() {
        this(15, 15);
    }

    public RenjuState(GridState gridState) {
        super(gridState);
        finder = new RenjuForbiddenPointFinder(gridState.getGridSizeX());
        refreshFinder();
    }

    public RenjuState(int boardSizeX, int boardSizeY) {
        super(new SimpleGomokuState(boardSizeX, boardSizeY));
        ((SimpleGomokuState) gridState).allowOverlines(true);
        ((SimpleGomokuState) gridState).setDoHashes(false);
        finder = new RenjuForbiddenPointFinder(boardSizeX);
        refreshFinder();
    }

    // GomokuState interface (overline policy is Renju-specific; report allowed for white)
    public void allowOverlines(boolean allow) {
        ((SimpleGomokuState) gridState).allowOverlines(allow);
    }

    public boolean areOverlinesAllowed() {
        return ((SimpleGomokuState) gridState).areOverlinesAllowed();
    }

    /** Sync the finder's board from the wrapped grid (color 1 -> 'X', 2 -> 'O', 0 -> '.'). */
    private void refreshFinder() {
        finder.clear();
        int sx = gridState.getGridSizeX();
        int sy = gridState.getGridSizeY();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                int p = gridState.getPosition(x, y);
                if (p == 1) finder.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
                else if (p == 2) finder.setStone(x, y, RenjuForbiddenPointFinder.WHITE);
            }
        }
    }

    public void addMove(int move) {
        gridState.addMove(move);
        refreshFinder();
        updateHash(this);
    }

    public void undoMove() {
        gridState.undoMove();
        refreshFinder();
        updateHash(this);
    }

    public boolean isGameOver() {
        int n = getNumMoves();
        if (n == 0) return false;

        int lastMove = getMove(n - 1);
        if (outOfBounds(lastMove)) {
            return drawCheck(n);
        }
        Coord c = convertMove(lastMove);
        int lastColor = getColor(n - 1); // 1 = black, 2 = white

        // Re-evaluate the last move as if just placed: temporarily clear it in the finder.
        finder.setStone(c.x, c.y, RenjuForbiddenPointFinder.EMPTY);
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        // restore
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);

        if (five) return true;
        return drawCheck(n);
    }

    private boolean drawCheck(int n) {
        return n == gridState.getGridSizeX() * gridState.getGridSizeY();
    }

    public int getWinner() {
        if (!isGameOver()) return 0;
        int n = getNumMoves();
        int lastMove = getMove(n - 1);
        if (outOfBounds(lastMove)) return 0; // draw
        Coord c = convertMove(lastMove);
        int lastColor = getColor(n - 1);
        finder.setStone(c.x, c.y, RenjuForbiddenPointFinder.EMPTY);
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);
        return five ? lastColor : 0;
    }

    private boolean outOfBounds(int move) {
        Coord c = convertMove(move);
        return c.x < 0 || c.x >= gridState.getGridSizeX()
                || c.y < 0 || c.y >= gridState.getGridSizeY();
    }

    /** Black forbidden points on the current board (for UI). */
    public List<Coord> getForbiddenPoints() {
        return finder.findForbiddenPoints();
    }

    public long calcHash(long cHash, int p, int move, int rot) {
        cHash ^= ZobristUtil.rand[p - 1][rotateMove(move, rot)];
        return cHash;
    }

    public void printBoard() {
        ((SimpleGomokuState) gridState).printBoard();
    }
}
```

> **Note:** `getColor(int)` comes from `MoveData` via the decorator and returns `moveNum % 2 + 1` (color 1 for move 0). `isGameOver`/`getWinner` only ever check the *last* move's owner, which matches the C++ `AddStone` semantics (a win is detected when the winning stone is placed).

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 4 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): RenjuState skeleton with Renju win semantics

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Forbidden-point blocking in isValidMove (post-opening)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

Until the opening state machine lands (Task 4+), treat the game as "opening complete" so this task can be tested in isolation. Task 4 replaces the `openingComplete` stub with the real machine; the forbidden gate written here stays valid because it only runs once `isOpeningComplete()` is true.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`:

```java
    // After enough moves to be "post-opening", black may not play a double-three.
    public void testForbiddenMoveBlockedForBlack() {
        RenjuState s = newState();
        // Build a black double-three around (7,7) with interleaved harmless white moves,
        // then assert black cannot play (7,7). Black stones: (5,7),(6,7),(7,5),(7,6).
        // Sequence so that it is black's turn (color 1) to play (7,7).
        add(s,
            xy(s, 5, 7), xy(s, 0, 0),   // b, w
            xy(s, 6, 7), xy(s, 0, 1),   // b, w
            xy(s, 7, 5), xy(s, 0, 2),   // b, w
            xy(s, 7, 6), xy(s, 0, 3));  // b, w  -> 8 moves, next is black (color 1)
        s.forceOpeningComplete(); // test hook (see implementation)
        int forbidden = xy(s, 7, 7);
        assertFalse(s.isValidMove(forbidden, 1));
        // a normal empty non-forbidden point is fine for black
        assertTrue(s.isValidMove(xy(s, 12, 12), 1));
    }

    public void testForbiddenPointNotBlockedForWhite() {
        RenjuState s = newState();
        // Same double-three shape but it becomes white's turn; white has no forbidden points.
        add(s,
            xy(s, 0, 0), xy(s, 5, 7),   // b, w
            xy(s, 0, 1), xy(s, 6, 7),   // b, w
            xy(s, 0, 2), xy(s, 7, 5),   // b, w
            xy(s, 0, 3), xy(s, 7, 6),   // b, w
            xy(s, 0, 4));               // b -> 9 moves, next is white (color 2)
        s.forceOpeningComplete();
        int dbl3 = xy(s, 7, 7);
        assertTrue(s.isValidMove(dbl3, 2)); // white allowed
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — `forceOpeningComplete` / overridden `isValidMove` not present.

- [ ] **Step 3: Add the forbidden gate + test hook**

In `RenjuState.java` add fields and methods:

```java
    // --- opening protocol state (fully wired in Task 4) ---
    private boolean openingComplete = false;

    public boolean isOpeningComplete() {
        return openingComplete;
    }

    /** Test/seam hook: mark the opening done so post-opening rules apply. */
    void forceOpeningComplete() {
        openingComplete = true;
    }

    public boolean isValidMove(int move, int player) {
        if (outOfBounds(move)) {
            return false;
        }
        if (player != getCurrentPlayer()) {
            return false;
        }
        if (getPosition(move) != 0) {
            return false;
        }

        if (openingComplete) {
            // Block black forbidden points.
            if (getCurrentColor() == 1) {
                Coord c = convertMove(move);
                if (finder.isForbidden(c.x, c.y)) {
                    return false;
                }
            }
        }
        return true;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 6 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): block black forbidden points in isValidMove

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Taraguchi-10 opening — central squares + turn sequencing (no swaps yet)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

This task introduces the move-index → central-square restriction and the `getCurrentPlayer` sequencing, and replaces the `forceOpeningComplete` stub with a real `openingComplete` driven by `addMove`. Swap windows and the branch/offer logic are added in Tasks 5–7; here, assume branch A and no swaps so the straight-line opening can be tested.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`. Helper: center of a 15×15 board is (7,7).

```java
    public void testMove1MustBeCenter() {
        RenjuState s = newState();
        assertFalse(s.isValidMove(xy(s, 7, 8), 1)); // off-center
        assertTrue(s.isValidMove(xy(s, 7, 7), 1));  // center
    }

    public void testMove2WithinThreeBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));                       // move 1 (black) center
        assertFalse(s.isValidMove(xy(s, 7, 9), 2));   // dy=2 -> outside 3x3
        assertTrue(s.isValidMove(xy(s, 8, 8), 2));    // inside 3x3
    }

    public void testMove3WithinFiveBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));   // 1 black
        s.addMove(xy(s, 8, 8));   // 2 white
        assertFalse(s.isValidMove(xy(s, 7, 10), 1));  // dy=3 -> outside 5x5
        assertTrue(s.isValidMove(xy(s, 9, 9), 1));    // inside 5x5
    }

    public void testMove4WithinSevenBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));
        s.addMove(xy(s, 8, 8));
        s.addMove(xy(s, 9, 9));
        assertFalse(s.isValidMove(xy(s, 7, 11), 2));  // dy=4 -> outside 7x7
        assertTrue(s.isValidMove(xy(s, 10, 10), 2));  // inside 7x7
    }
```

(These four tests assume no swap window blocks play between moves — true only after Task 5 wires swaps, which default to "no pending decision" until `addMove` sets one. To keep Task 4 green, swaps are introduced as *resolved-by-default* here and made interactive in Task 5. See implementation.)

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — move 1 is currently allowed anywhere (no opening restriction yet).

- [ ] **Step 3: Implement central-square restriction + sequencing**

In `RenjuState.java`, remove `forceOpeningComplete`'s sole role by keeping it (still used by Task 3 tests) but add the real opening logic. Replace `isValidMove` and add helpers + an `addMove` override that advances `openingComplete`:

```java
    // branch flags (Branch B / offers wired in Tasks 6-7; default Branch A here)
    private boolean branchChosen = true;   // Task 5/6 will flip default to false
    private boolean tenOffer = false;

    private int centerX() { return gridState.getGridSizeX() / 2; }
    private int centerY() { return gridState.getGridSizeY() / 2; }

    /** Opening central-square restriction by number of stones already placed (n). */
    private boolean withinOpeningSquare(int move, int n) {
        Coord c = convertMove(move);
        int dx = Math.abs(c.x - centerX());
        int dy = Math.abs(c.y - centerY());
        switch (n) {
            case 0: return dx == 0 && dy == 0; // move 1: center
            case 1: return dx <= 1 && dy <= 1; // move 2: 3x3
            case 2: return dx <= 2 && dy <= 2; // move 3: 5x5
            case 3: return dx <= 3 && dy <= 3; // move 4: 7x7
            case 4: return dx <= 4 && dy <= 4; // move 5 (Branch A): 9x9
            default: return true;              // move 6+: anywhere
        }
    }

    public boolean isValidMove(int move, int player) {
        if (outOfBounds(move)) return false;
        if (player != getCurrentPlayer()) return false;
        if (getPosition(move) != 0) return false;

        if (!openingComplete) {
            int n = gridState.getNumMoves();
            if (!withinOpeningSquare(move, n)) return false;
            return true;
        }

        // post-opening: block black forbidden points
        if (getCurrentColor() == 1) {
            Coord c = convertMove(move);
            if (finder.isForbidden(c.x, c.y)) return false;
        }
        return true;
    }

    public int getCurrentPlayer() {
        if (openingComplete) return super.getCurrentPlayer();
        // Straight-line sequencing (swaps added in Task 5): color by parity.
        int n = gridState.getNumMoves();
        return n % 2 + 1;
    }
```

And update `addMove` to advance `openingComplete` (replace the Task 2 `addMove`):

```java
    public void addMove(int move) {
        gridState.addMove(move);
        refreshFinder();
        int n = gridState.getNumMoves();
        if (!openingComplete && n >= 6) {
            openingComplete = true;
        }
        updateHash(this);
    }
```

Keep `forceOpeningComplete()` (Task 3 tests still call it; harmless).

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 10 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): Taraguchi-10 central-square opening restriction

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Opening — swap windows after moves 1–4 (and 5 in Branch A)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

A swap option opens after each opening stone (moves 1–4, plus move 5 in Branch A). While pending, the opponent of the last mover is `getCurrentPlayer()`, board moves are blocked, and the controller must call `renjuSwapDecisionMade(boolean)` to advance.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`:

```java
    public void testSwapWindowBlocksMovesUntilDecided() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); // move 1 (black, color 1)
        // swap window open: white (player 2) is the decider, no board move allowed
        assertTrue(s.isAwaitingSwapDecision());
        assertEquals(2, s.getCurrentPlayer());
        assertFalse(s.isValidMove(xy(s, 8, 8), 2)); // blocked while pending
        s.renjuSwapDecisionMade(false);              // white declines swap
        assertFalse(s.isAwaitingSwapDecision());
        assertEquals(2, s.getCurrentPlayer());        // white now plays move 2
        assertTrue(s.isValidMove(xy(s, 8, 8), 2));
    }

    public void testSwapDecisionRecorded() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));
        s.renjuSwapDecisionMade(true);
        assertTrue(s.didSwapAt(1)); // swap recorded for the window after stone 1
    }
```

Adjust the four Task-4 square tests to resolve each swap window before the next stone. Update them in place:

```java
    public void testMove2WithinThreeBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7));
        s.renjuSwapDecisionMade(false);
        assertFalse(s.isValidMove(xy(s, 7, 9), 2));
        assertTrue(s.isValidMove(xy(s, 8, 8), 2));
    }

    public void testMove3WithinFiveBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);
        assertFalse(s.isValidMove(xy(s, 7, 10), 1));
        assertTrue(s.isValidMove(xy(s, 9, 9), 1));
    }

    public void testMove4WithinSevenBySquare() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);
        s.addMove(xy(s, 9, 9)); s.renjuSwapDecisionMade(false);
        assertFalse(s.isValidMove(xy(s, 7, 11), 2));
        assertTrue(s.isValidMove(xy(s, 10, 10), 2));
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — `isAwaitingSwapDecision` / `renjuSwapDecisionMade` / `didSwapAt` missing.

- [ ] **Step 3: Implement swap windows**

In `RenjuState.java`:

```java
    private boolean awaitingSwap = false;
    // swap decisions indexed by the stone count after which the window opened (1..5)
    private final boolean[] swapDecision = new boolean[6];

    public boolean isAwaitingSwapDecision() {
        return awaitingSwap;
    }

    public boolean didSwapAt(int afterStone) {
        return swapDecision[afterStone];
    }

    public void renjuSwapDecisionMade(boolean swap) {
        if (!awaitingSwap) {
            throw new IllegalStateException("no swap decision pending");
        }
        swapDecision[gridState.getNumMoves()] = swap;
        awaitingSwap = false;
    }
```

Update `getCurrentPlayer` to surface the swap decider:

```java
    public int getCurrentPlayer() {
        if (openingComplete) return super.getCurrentPlayer();
        int n = gridState.getNumMoves();
        if (awaitingSwap) {
            int lastColor = (n - 1) % 2 + 1; // color of stone n
            return 3 - lastColor;            // opponent decides
        }
        return n % 2 + 1;
    }
```

Block board moves while a swap is pending, in `isValidMove` (insert at the top of the `!openingComplete` block):

```java
        if (!openingComplete) {
            if (awaitingSwap) return false;
            int n = gridState.getNumMoves();
            if (!withinOpeningSquare(move, n)) return false;
            return true;
        }
```

Open a swap window in `addMove` after stones 1–4 (and 5 for Branch A; `tenOffer` stays false by default until Task 6):

```java
    public void addMove(int move) {
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

Update `clear()` (override; add one) to reset opening state:

```java
    public void clear() {
        super.clear();
        openingComplete = false;
        awaitingSwap = false;
        branchChosen = true; // Task 6 flips default
        tenOffer = false;
        for (int i = 0; i < swapDecision.length; i++) swapDecision[i] = false;
        refreshFinder();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 12 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): swap windows during Taraguchi-10 opening

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Opening — branch choice after move 4 + Branch A move 5/6

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

After move 4's swap window resolves, black chooses Branch A (9×9 move 5 + swap + white move 6) or Branch B (10-offer, Task 7). Board moves are blocked at `n == 4` until `chooseBranch(...)` is called.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`. Helper to run the first four moves + swaps:

```java
    private RenjuState openedToFour() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false);  // 1 black
        s.addMove(xy(s, 8, 8)); s.renjuSwapDecisionMade(false);  // 2 white
        s.addMove(xy(s, 9, 7)); s.renjuSwapDecisionMade(false);  // 3 black
        s.addMove(xy(s, 6, 8)); s.renjuSwapDecisionMade(false);  // 4 white
        return s;
    }

    public void testBranchChoiceRequiredAfterMove4() {
        RenjuState s = openedToFour();
        assertTrue(s.isAwaitingBranchChoice());
        assertEquals(1, s.getCurrentPlayer());               // black chooses
        assertFalse(s.isValidMove(xy(s, 5, 5), 1));          // blocked until chosen
    }

    public void testBranchAFullSequence() {
        RenjuState s = openedToFour();
        s.chooseBranch(false);                                // Branch A
        assertFalse(s.isAwaitingBranchChoice());
        // move 5 (black) must be within 9x9
        assertFalse(s.isValidMove(xy(s, 7, 12), 1));          // dy=5 outside 9x9
        assertTrue(s.isValidMove(xy(s, 11, 7), 1));           // dx=4 inside 9x9
        s.addMove(xy(s, 11, 7));                               // move 5
        // swap window for white before move 6
        assertTrue(s.isAwaitingSwapDecision());
        s.renjuSwapDecisionMade(false);
        // move 6 (white) anywhere
        assertTrue(s.isValidMove(xy(s, 0, 0), 2));
        s.addMove(xy(s, 0, 0));                                // move 6
        assertTrue(s.isOpeningComplete());
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — `isAwaitingBranchChoice` / `chooseBranch` missing; branch defaults to chosen.

- [ ] **Step 3: Implement branch choice**

In `RenjuState.java` change the `branchChosen` default to `false` (both the field initializer and `clear()`):

```java
    private boolean branchChosen = false;
```

Add:

```java
    public boolean isAwaitingBranchChoice() {
        return !openingComplete && !awaitingSwap
                && gridState.getNumMoves() == 4 && !branchChosen;
    }

    /** Black picks the post-move-4 path. false = Branch A (9x9 + swap), true = Branch B (10 offers). */
    public void chooseBranch(boolean tenOffer) {
        if (!isAwaitingBranchChoice()) {
            throw new IllegalStateException("branch choice not pending");
        }
        this.tenOffer = tenOffer;
        this.branchChosen = true;
    }
```

Update `getCurrentPlayer` to return black during branch choice (insert before the parity fallthrough):

```java
    public int getCurrentPlayer() {
        if (openingComplete) return super.getCurrentPlayer();
        int n = gridState.getNumMoves();
        if (awaitingSwap) {
            int lastColor = (n - 1) % 2 + 1;
            return 3 - lastColor;
        }
        if (n == 4 && !branchChosen) {
            return 1; // black chooses branch (and would play move 5)
        }
        return n % 2 + 1;
    }
```

Block board moves while branch choice is pending, in `isValidMove`'s `!openingComplete` block:

```java
        if (!openingComplete) {
            if (awaitingSwap) return false;
            int n = gridState.getNumMoves();
            if (n == 4 && !branchChosen) return false; // must choose branch first
            if (!withinOpeningSquare(move, n)) return false;
            return true;
        }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 14 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): branch choice + Branch A move 5/6 sequencing

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Opening — Branch B (10 offers + symmetry dedup + selection)

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

In Branch B, black submits 10 candidate 5th moves via `offerFifthMove` (anywhere, no symmetric duplicates). White commits one via `selectFifthMove` (placed as move 5), then plays move 6 anywhere.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`:

```java
    public void testBranchBOffersAndSelection() {
        RenjuState s = openedToFour();
        s.chooseBranch(true); // Branch B
        assertTrue(s.isAwaitingFifthOffers());
        assertEquals(1, s.getCurrentPlayer()); // black offers

        // Offer 10 distinct, non-symmetric candidates far from center to avoid symmetry collisions.
        int[][] offers = {
            {0,0},{0,2},{0,4},{0,6},{0,8},{0,10},{0,12},{0,14},{2,0},{4,0}
        };
        for (int[] o : offers) s.offerFifthMove(xy(s, o[0], o[1]));
        assertEquals(10, s.getOfferedFifthMoves().size());
        assertFalse(s.isAwaitingFifthOffers());
        assertTrue(s.isAwaitingFifthSelection());
        assertEquals(2, s.getCurrentPlayer()); // white selects

        s.selectFifthMove(xy(s, 0, 6));        // white picks one offered move
        assertEquals(5, s.getNumMoves());       // committed as move 5
        assertEquals(1, s.getColor(4));         // move 5 is black (color 1)
        // move 6 (white) anywhere
        assertTrue(s.isValidMove(xy(s, 14, 14), 2));
        s.addMove(xy(s, 14, 14));
        assertTrue(s.isOpeningComplete());
    }

    public void testSymmetricDuplicateOfferRejected() {
        RenjuState s = newState();
        // Empty-ish board symmetry: with only the center stone placed, the position is
        // symmetric under all 8 D4 operations. Offer (5,7); its mirror (9,7) is a duplicate.
        s.addMove(xy(s, 7, 7)); s.renjuSwapDecisionMade(false); // move 1 only
        // Force into Branch B offer state via the normal path is not possible at n=1;
        // instead drive a minimal 4-move opening that is symmetric about the center.
        // Use a center-symmetric 4-stone setup: (7,7) black, (7,7) is the only forced one.
        // Simpler: assert offerFifthMove dedup directly after reaching offer state.
        RenjuState t = openedToFour();
        t.chooseBranch(true);
        t.offerFifthMove(xy(t, 0, 0));
        try {
            t.offerFifthMove(xy(t, 0, 0)); // exact duplicate
            fail("expected duplicate rejection");
        } catch (IllegalArgumentException expected) {
        }
    }
```

> The first test exercises the happy path; the second asserts exact-duplicate rejection (a degenerate symmetry: identity). Full D4 dedup is implemented below and covered by the happy-path test choosing non-symmetric offers.

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — offer/select API missing.

- [ ] **Step 3: Implement Branch B**

In `RenjuState.java` add imports already present (`ArrayList`, `List`). Add fields:

```java
    private final List<Integer> offeredFifth = new ArrayList<Integer>();
    private Integer selectedFifth = null;
```

Reset them in `clear()`:

```java
        offeredFifth.clear();
        selectedFifth = null;
```

Add state predicates:

```java
    public boolean isAwaitingFifthOffers() {
        return !openingComplete && branchChosen && tenOffer
                && gridState.getNumMoves() == 4 && offeredFifth.size() < 10;
    }

    public boolean isAwaitingFifthSelection() {
        return !openingComplete && branchChosen && tenOffer
                && gridState.getNumMoves() == 4 && offeredFifth.size() == 10
                && selectedFifth == null;
    }

    public List<Integer> getOfferedFifthMoves() {
        return new ArrayList<Integer>(offeredFifth);
    }
```

Add offer + selection logic:

```java
    public void offerFifthMove(int move) {
        if (!isAwaitingFifthOffers()) {
            throw new IllegalStateException("not accepting 5th-move offers");
        }
        if (outOfBounds(move) || getPosition(move) != 0) {
            throw new IllegalArgumentException("offered move not an empty board point");
        }
        if (isSymmetricDuplicate(move)) {
            throw new IllegalArgumentException("offered move is a symmetric duplicate");
        }
        offeredFifth.add(move);
    }

    public void selectFifthMove(int move) {
        if (!isAwaitingFifthSelection()) {
            throw new IllegalStateException("not awaiting 5th-move selection");
        }
        if (!offeredFifth.contains(move)) {
            throw new IllegalArgumentException("selected move was not offered");
        }
        selectedFifth = move;
        addMove(move); // commit as move 5 (color parity -> black); discards the other 9
    }

    /**
     * D4 symmetry dedup: a candidate is a duplicate if some board symmetry that maps the
     * current placed-stone position onto itself also maps the candidate onto an existing offer.
     */
    private boolean isSymmetricDuplicate(int move) {
        for (int rot : positionStabilizer()) {
            int image = rotateMove(move, rot);
            for (Integer existing : offeredFifth) {
                if (existing.intValue() == image) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Rotation indices (0..7) whose D4 operation leaves the current stones invariant. */
    private List<Integer> positionStabilizer() {
        List<Integer> stab = new ArrayList<Integer>();
        int n = gridState.getNumMoves();
        for (int rot = 0; rot < 8; rot++) {
            boolean invariant = true;
            for (int m = 0; m < n && invariant; m++) {
                int src = getMove(m);
                if (outOfBounds(src)) continue;
                int dst = rotateMove(src, rot);
                if (getPosition(dst) != getPosition(src)) {
                    invariant = false;
                }
            }
            if (invariant) stab.add(rot);
        }
        return stab;
    }
```

> `rotateMove(move, rot)` is `SimpleGridState`'s D4 rotation about the board center (`rot` 0..7), available through the decorator. `rot == 0` is the identity, so exact duplicates are always caught.

Extend `getCurrentPlayer` for the offer/select windows (insert after the `awaitingSwap` block, before the `n == 4 && !branchChosen` block):

```java
        if (branchChosen && tenOffer && n == 4) {
            if (offeredFifth.size() < 10) return 1;   // black offering
            if (selectedFifth == null) return 2;      // white selecting
            return 2;                                  // white plays move 6
        }
```

Block normal board moves during offer/select in `isValidMove`'s `!openingComplete` block (insert after the branch-choice guard):

```java
            if (branchChosen && tenOffer && n == 4) {
                // offers/selection go through dedicated hooks, not isValidMove
                return false;
            }
```

`addMove` already opens no swap window at `n == 5` when `tenOffer` is true (the `n == 5 && !tenOffer` guard from Task 5), and sets `openingComplete` at `n == 6`. No change needed.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 16 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): Branch B 10-offer with symmetry dedup + selection

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Undo policy across opening boundaries

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuStateTest.java`

Mirror `SimplePenteState`'s conservative stance: no undo while a decision is pending or once the opening has completed back into the negotiated region. Within normal post-opening play, delegate to the wrapped state.

- [ ] **Step 1: Write the failing test**

Add to `RenjuStateTest.java`:

```java
    public void testNoUndoWhileSwapPending() {
        RenjuState s = newState();
        s.addMove(xy(s, 7, 7)); // swap window open
        assertFalse(s.canPlayerUndo(1));
        assertFalse(s.canPlayerUndo(2));
    }

    public void testUndoDelegatesPostOpening() {
        RenjuState s = openedToFour();
        s.chooseBranch(false);
        s.addMove(xy(s, 11, 7)); s.renjuSwapDecisionMade(false); // move 5
        s.addMove(xy(s, 0, 0));                                  // move 6 -> opening complete
        s.addMove(xy(s, 1, 1));                                  // move 7 (black)
        // after move 7 it's white's turn; white just did NOT move last -> black may undo
        assertTrue(s.canPlayerUndo(s.getCurrentColor() == 1 ? 2 : 1));
    }
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: FAIL — default `canPlayerUndo` allows undo during the swap window.

- [ ] **Step 3: Implement undo policy**

In `RenjuState.java`:

```java
    public boolean canPlayerUndo(int player) {
        if (!openingComplete) {
            // No undo while any opening decision is pending or being negotiated.
            return false;
        }
        return gridState.canPlayerUndo(player);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: PASS (OK, 18 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuStateTest.java
git commit -m "feat(renju): conservative undo policy during opening

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Full regression + spec status update

**Files:**
- Modify: `docs/superpowers/specs/2026-06-13-renju-state-design.md`

- [ ] **Step 1: Run both suites**

```bash
./justCompile \
  && ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuStateTest
```
Expected: both PASS.

- [ ] **Step 2: Confirm no regressions in the game test group**

```bash
ant test-one -Dtest=org.pente.game.test.GomokuStateTest
ant test-one -Dtest=org.pente.game.test.PenteStateTest
```
Expected: both PASS (these classes were not modified; this confirms the new files compile cleanly alongside them).

- [ ] **Step 3: Mark the spec done**

Edit the spec header `Status: approved-pending-review` → `Status: implemented`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-13-renju-state-design.md
git commit -m "docs(renju): mark design spec implemented

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Parametric board → `RenjuForbiddenPointFinder(size)` + `RenjuState(x,y)` derive center/radii from board size. ✓ (Tasks 1, 4)
- Black exact-5 / white 5+ win → Task 2. ✓
- Forbidden = blocked in `isValidMove` → Task 3 (+ guard reused in Task 4/6). ✓
- Taraguchi-10 central squares (center/3/5/7/9) → Task 4. ✓
- Swap windows after moves 1–4 + Branch A move 5 → Task 5. ✓
- Branch choice after move 4 → Task 6. ✓
- Branch A move 5 (9×9) + swap + move 6 anywhere → Task 6. ✓
- Branch B 10 offers + symmetry dedup + white selection + move 6 → Task 7. ✓
- Sequence + record swap model (no recolor; controller owns seats) → Tasks 5–7 use decision hooks + flags only. ✓
- Faithful C++ port → Task 1 verbatim, with explicit porting notes. ✓
- Undo policy → Task 8. ✓
- Out of scope (factory wiring, UI, AI) → untouched. ✓

**Placeholder scan:** none — every step has full code or exact commands.

**Type consistency:** `finder` (`RenjuForbiddenPointFinder`), `isForbidden(x,y)`, `isFive(x,y,nColor)`, `withinOpeningSquare(move,n)`, `renjuSwapDecisionMade(boolean)`, `chooseBranch(boolean)`, `offerFifthMove(int)`/`selectFifthMove(int)`, `isAwaiting*` predicates, `swapDecision[]`/`didSwapAt(int)` — names are consistent across tasks. `branchChosen` default changes from `true` (Task 4 stub) to `false` (Task 6) — called out explicitly in Task 6 Step 3.

**Note on Task 4 ordering seam:** Task 4 ships a temporary `branchChosen = true` default so the straight-line opening is testable before branch logic exists; Task 6 flips it to `false` and adds the gate. This is intentional incremental scaffolding, not a contradiction.
