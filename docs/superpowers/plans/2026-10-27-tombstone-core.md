# Tombstone Pente — Plan A (Game Core) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Tombstone Pente rules engine (`TombstoneCodes`, `TombstoneState`), its Zobrist row, its game registration in `GridStateFactory`, and its PGN tokens, so Plans B and C can compile and build on them.

**Architecture:** `TombstoneCodes` is a pure, stateless codec for the stored move codes (own stone `0..360`, dead stone `361..721`, own pass `722`, archive sentinel `723`). `TombstoneState` is a `GridStateDecorator` over a 3000-entry `SimpleGridState` that replays entries through one lenient primitive (`addMove`), does colour-explicit Pente captures and five detection, tracks turn phase and the empty-turn draw, and rebuilds itself by replay on undo. Hashing is a pure function of the position, using a new fifth `ZobristUtil.rand` row for dead stones.

**Tech Stack:** Java 21 (Homebrew `openjdk@21`), Ant, JUnit 3.7 (`dsg_src/lib/junit-3.7.jar`; `junit.framework.TestCase`, `junit.textui.TestRunner`) — it has **no `assertFalse`** (added in 3.8).

**Spec:** `docs/superpowers/specs/2026-10-27-tombstone-pente-design.md` — read §2-§7.1, §9.3 and §16.1 before starting. This plan implements the "A — game core" row of the spec's §16 table and nothing else.

## Global Constraints

- Repo: `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`, branch `feat/tombstone-pente` (already checked out). Never commit to `main`.
- Commits: plain imperative messages (repo style, e.g. `Add TombstoneCodes move codec`), **no** `Co-Authored-By`, no "Generated with" lines. The repo signs commits automatically (`commit.gpgsign=true`); do not pass `--no-gpg-sign`.
- **Do not deploy.** Never run `./justCompile` (it restarts docker) and never run `sync_gameServer.sh` (production). Spec §15: this branch must not reach production until beta gating exists.
- Scratch files (generator program, temp output) go in `/private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad`, never in the repo.
- Ids (spec D10): `TOMBSTONE = 33`, `SPEED_TOMBSTONE = 34`, `TB_TOMBSTONE = 83`. Names: `"Tombstone Pente"`, `"Speed Tombstone Pente"`, TB game name `"Tombstone Pente"`, TB display name `"Turn-based Tombstone Pente"`.
- Codes (spec §4): own stone `0..360` (`x + 19*y`), dead stone `361 + point` (`361..721`), own pass `722`, archive "no next move" sentinel `723` (Tombstone only). Board cell values: `0` empty, `1`/`2` stones, `3` dead.
- Constants: `TombstoneState.MAX_ENTRIES = 3000`, `CAPTURES_TO_WIN = 10` (stones), `EMPTY_TURNS_FOR_DRAW = 6`; centre = `180`.
- Zobrist: existing `ZobristUtil.rand[0..3]` values must not change by a single digit (stored `pente_move.hash_key` depend on them). New row `rand[4]` has exactly 361 values; `ZobristUtil.TOMBSTONE_DEAD_ROW = 4`.
- `LIVE_GAMES`, `TB_GAMES`, `ALL_GAMES` stay unchanged (spec §7.3). `isSingleGameSet` and `firstMoveCanBeOffCenter` stay unchanged (D6, §7.1).
- Tests are JUnit 3 classes in `dsg_src/java/org/pente/game/test/` with the boilerplate of `RenjuFactoryTest` (`main`, `suite()`, `(String name)` constructor).
- **JUnit 3.7 has no `assertFalse`** (`javap junit.framework.Assert` lists `assertTrue`, `assertEquals`, `assertNull`, `assertSame`, `fail` only; the repo works around it with `assertTrue(!...)`, e.g. `MMAIProtocolTest.java:50`). The test code below calls `assertFalse(cond)` and `assertFalse(msg, cond)`, so the "JUnit 3 boilerplate" of **every** new test class in this plan (`TombstoneZobristTest`, `TombstoneCodesTest`, `TombstoneStateTest`, `TombstoneUndoReplayTest`, `TombstoneFactoryTest`, `PGNTombstoneTest`) also includes these two helpers, or the class fails to compile with `cannot find symbol: assertFalse`:
  ```java
  private static void assertFalse(boolean cond) { assertTrue(!cond); }            // junit-3.7 has no assertFalse
  private static void assertFalse(String msg, boolean cond) { assertTrue(msg, !cond); }
  ```
  With the helpers in place, each red step fails for the reason it states (the missing production symbol or behaviour), not on `assertFalse`.
- **BUILD** (from the repo root; compiles `deploy/` and all test sources):
  ```bash
  cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
  rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/ && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant compile-tests
  ```
  Success line: `BUILD SUCCESSFUL`.
- **RUN `<Class>`**: `java -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner org.pente.game.test.<Class>` (the `java` on PATH is 21.0.13). Success line: `OK (N tests)`.
- Pre-existing failures (verified 2026-10-27 on this branch before any change; not caused by this plan, do not fix): `AllGamesTest` `Tests run: 78, Failures: 8` (`testConvertMove`/`testAddMove` in the grid/gomoku/pente/poof suites), `PenteStateTest` `Tests run: 24, Failures: 2`, `Connect6StateTest` `Tests run: 9, Failures: 3`. Baseline greens: `RenjuFactoryTest` OK (7), `GridStateFactorySingleGameSetTest` OK (3), `MySQLGameVenueStorerTbLookupTest` OK (4), `PGNRenjuPassTest` OK (2), `RenjuStateTest` OK (37).

## Ordering with the other plans

- **Plan A lands first.** Plans B (live server, archive, boot, logging) and C (TB + website) compile against `TombstoneCodes`, `TombstoneState`, `GridStateFactory.isTombstone`, `NON_KOTH_LIVE_GAMES`/`NON_KOTH_TB_GAMES` and `ZobristUtil.TOMBSTONE_DEAD_ROW`. Do not start B or C until Task 7 of this plan is committed.
- Plan D (React) depends only on the wire contract and may run in parallel.
- After this plan alone, Tombstone appears in `getNormalGames()`/`getSpeedGames()`/`getTbGames()`/`getDisplayGames()` (rating pages, `tb/new.jsp`), but the live server, TB storer and pages cannot yet play it. That is expected on this branch (spec §15) and is closed by B and C.

## Review Focus

Inputs and conditions the spec implies that a reasonable user would hit, with the test that pins each:

1. **Board-edge wraparound** — linear indexes `189, 190` are adjacent numbers but (18,9) and (0,10) are not neighbours; a capture or five must never wrap across the row edge. Pinned by `testCaptureDoesNotWrapAcrossEdge` and `testFiveDoesNotWrapAcrossEdge` (Task 3).
2. **A dead stone is not a wildcard** — a line `P P P P dead P` must not count as five/overline, and `dead` must never stand in for either colour in a capture pattern. Pinned by `testDeadStoneIsNotCountedInLine`, `testDeadStoneBlocksCaptureAsOpponent`, `testDeadStoneBlocksCaptureAsOwn` (Task 3).
3. **History ending mid-turn** (a live game resigned or timed out while a dead stone is pending, or a TB history ending in the sixth `722`) — replay must close the trailing turn exactly as a skip and must not throw. Pinned by `testReplayClosesTrailingOwnEntry` and `testReplayOfSixEmptyTurnsIsDraw` (Task 5).
4. **Undo after captures and skips** — the restored board, capture counts, empty-turn counter and hashes must equal a fresh replay. Pinned by `testUndoTurnRestoresCapturesAndHash` and `testUndoTurnRestoresEmptyTurnCounter` (Task 5).
5. **Malformed PGN tokens** (`PASS` in upper case, `x`, `xZ99`, `pas`, `K0`) — must parse to the right code or be skipped (`-1`), never throw `NumberFormatException`. Pinned by `testParsePgnTokenRejectsGarbage` (Task 2) and `testParseAcceptsUpperCasePass` (Task 7).

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `dsg_src/java/org/pente/game/ZobristUtil.java` | Modify (`:3-12` comment/decl, `:993` row end) | append `rand[4]` (361 values) + `TOMBSTONE_DEAD_ROW` |
| `dsg_src/java/org/pente/game/TombstoneCodes.java` | Create | pure codec: predicates, turn shape, labels, PGN tokens, archive sentinel |
| `dsg_src/java/org/pente/game/SimpleGridState.java` | Modify (`:77-83`, `:349-350`) | capacity constructor `(x, y, maxEntries)` |
| `dsg_src/java/org/pente/game/TombstoneState.java` | Create | rules, phase, draws, hashing, undo/rebuild, replay |
| `dsg_src/java/org/pente/game/GridStateFactory.java` | Modify (`:37-38`, `:77`, `:135-137`, `:139-235`, `:372-379`, `:400-402`, `:426-428`) | ids, `Game`s, arrays, `createGridState`, `isTombstone`, `NON_KOTH_*` |
| `dsg_src/java/org/pente/game/PGNGameFormat.java` | Modify (`:213-221`, `:347-361`) | Tombstone PGN tokens on format and parse |
| `dsg_src/java/org/pente/game/test/TombstoneZobristTest.java` | Create | row counts/distinctness (Task 1) + hash behaviour (Task 4) |
| `dsg_src/java/org/pente/game/test/TombstoneCodesTest.java` | Create | codec tests |
| `dsg_src/java/org/pente/game/test/TombstoneStateTest.java` | Create | rules tests (Task 3) |
| `dsg_src/java/org/pente/game/test/TombstoneUndoReplayTest.java` | Create | undo, replay, live-only methods (Task 5) |
| `dsg_src/java/org/pente/game/test/TombstoneFactoryTest.java` | Create | registration |
| `dsg_src/java/org/pente/game/test/RenjuFactoryTest.java` | Modify (`:70`) | `getMaxGameId()` is now `TB_TOMBSTONE` |
| `dsg_src/java/org/pente/game/test/PGNTombstoneTest.java` | Create | PGN round trip |

`TombstoneCodes.archiveNoNextMove(int game)` needs `GridStateFactory.isTombstone`, so it is added in Task 6, not Task 2.

---

### Task 1: ZobristUtil — dedicated dead-stone row

**Files:**
- Modify: `dsg_src/java/org/pente/game/ZobristUtil.java` (class comment `:4-11`, row 3 ends at `:993` with `...-5349835967319192806L}};`)
- Test: `dsg_src/java/org/pente/game/test/TombstoneZobristTest.java` (new)

**Interfaces:**
- Consumes: `ZobristUtil.rand` (rows 0..3, lengths `[723, 723, 720, 721]`, 2887 values, 2868 distinct — verified by a scratch program on 2026-10-27).
- Produces: `public static final int TOMBSTONE_DEAD_ROW = 4;` and `rand[4]` of length 361, used by `TombstoneState.calcHash` (Task 4).

- [ ] **Step 0: Commit the spec and plan if they are untracked**

Run: `git status --porcelain docs/superpowers` — if either `docs/superpowers/specs/2026-10-27-tombstone-pente-design.md` or this plan shows `??`:
```bash
git add docs/superpowers/specs/2026-10-27-tombstone-pente-design.md docs/superpowers/plans/2026-10-27-tombstone-core.md
git commit -m "Add Tombstone Pente design and game core plan"
```

- [ ] **Step 1: Write the failing test**

Create `TombstoneZobristTest` (JUnit 3 boilerplate) with:

```java
public void testRowLengths() {
    long[][] r = ZobristUtil.rand;
    assertEquals(5, r.length);
    assertEquals(723, r[0].length);
    assertEquals(723, r[1].length);
    assertEquals(720, r[2].length);
    assertEquals(721, r[3].length);
    assertEquals(361, r[ZobristUtil.TOMBSTONE_DEAD_ROW].length);
    assertEquals(4, ZobristUtil.TOMBSTONE_DEAD_ROW);
}

public void testDeadRowIsNonZeroDistinctAndDisjoint() {
    java.util.Set<Long> existing = new java.util.HashSet<>();
    int total = 0;
    for (int row = 0; row < 4; row++) {
        for (long v : ZobristUtil.rand[row]) { existing.add(v); total++; }
    }
    assertEquals(2887, total);
    assertEquals(2868, existing.size());   // pins that rows 0-3 were not edited
    java.util.Set<Long> dead = new java.util.HashSet<>();
    for (long v : ZobristUtil.rand[4]) {
        assertTrue("zero value", v != 0L);
        assertTrue("duplicate in row 4: " + v, dead.add(v));
        assertFalse("row 4 value already in rows 0-3: " + v, existing.contains(v));
    }
    assertEquals(361, dead.size());
}

public void testExistingRowSpotValuesUnchanged() {
    assertEquals(-473272652262568974L, ZobristUtil.rand[0][0]);
    assertEquals(6938546511637388830L, ZobristUtil.rand[1][0]);
    assertEquals(-653791976413045955L, ZobristUtil.rand[2][0]);
    assertEquals(-5349835967319192806L, ZobristUtil.rand[3][720]);
}
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD. Expected: `BUILD FAILED` with `cannot find symbol ... TOMBSTONE_DEAD_ROW`.

- [ ] **Step 3: Generate the 361 values (scratch only, not committed)**

Write `/private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad/GenTombstoneRow.java`:

```java
import java.security.SecureRandom;
import java.util.*;

public class GenTombstoneRow {
    public static void main(String[] a) {
        Set<Long> seen = new HashSet<>();
        for (int row = 0; row < 4; row++) for (long v : org.pente.game.ZobristUtil.rand[row]) seen.add(v);
        SecureRandom sr = new SecureRandom();
        List<Long> out = new ArrayList<>();
        while (out.size() < 361) {
            long v = sr.nextLong();
            if (v == 0L || !seen.add(v)) continue;   // rejects 0, rows 0-3, and in-row duplicates
            out.add(v);
        }
        StringBuilder sb = new StringBuilder("            new long[]{");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append(i % 3 == 0 ? ",\n                    " : ", ");
            sb.append(out.get(i)).append('L');
        }
        System.out.println(sb.append("}};"));
    }
}
```

Run (deployClasses must hold the unmodified `ZobristUtil`, which the baseline BUILD guarantees):
`java -cp deployClasses /private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad/GenTombstoneRow.java > /private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad/row4.txt`

- [ ] **Step 4: Append the row and the constant**

In `ZobristUtil.java`: change the closing `}};` at the end of line 993 to `},` and insert the generated block from `row4.txt` (which ends in `}};`) after it. Add `public static final int TOMBSTONE_DEAD_ROW = 4;` above `rand`, and extend the class comment with one line: `row 4: 361 keys for Tombstone dead stones (colour-neutral), indexed by rotated point`. Touch nothing else in the file.

Verify the existing rows are byte-identical: `git diff -U0 dsg_src/java/org/pente/game/ZobristUtil.java | grep '^-' | grep -v '^---'` must print exactly one line (the old line 993).

- [ ] **Step 5: Run to verify it passes**

Run: BUILD, then RUN `TombstoneZobristTest`. Expected: `OK (3 tests)`.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/game/ZobristUtil.java dsg_src/java/org/pente/game/test/TombstoneZobristTest.java
git commit -m "Add Zobrist row for Tombstone dead stones"
```

---
### Task 2: TombstoneCodes — pure move codec

**Files:**
- Create: `dsg_src/java/org/pente/game/TombstoneCodes.java`
- Test: `dsg_src/java/org/pente/game/test/TombstoneCodesTest.java` (new)

**Interfaces:**
- Consumes: `PGNGameFormat.formatCoordinates(int)` (`PGNGameFormat.java:953-961`, `x` letters `A..T` skipping `I`, row `19 - y`) and `PGNGameFormat.parseCoordinates(String)` (`:967-981`; it throws `NumberFormatException` on input like `"pas"` and returns out-of-range codes for input like `"Z5"`).
- Produces (spec §16.1 signatures, all `public static`, class `public final` with a private constructor):
  `POINTS = 361`, `DEAD_OFFSET = 361`, `OWN_PASS = 722`, `ARCHIVE_NO_NEXT_MOVE = 723`, `DEAD_CELL = 3`;
  `boolean isOwnStone(int)`, `isOwnPass(int)`, `isOwnEntry(int)`, `isDeadStone(int)`; `int deadCode(int point)`, `int pointOf(int code)`;
  `int countOwnEntries(int[] codes, int n)`, `int currentPlayer(int[] codes, int n)`, `int lastTurnLength(int[] codes, int n)`;
  `String validateTurnShape(int[] turn)`; `String coordinate(int point)`, `String entryLabel(int code)`, `String turnLabel(int ownCode, int deadCodeOrMinus1)`;
  `String pgnToken(int code)`, `int parsePgnToken(String token)`.
  `archiveNoNextMove(int game)` is added in Task 6.

- [ ] **Step 1: Write the failing test**

Create `TombstoneCodesTest` (JUnit 3 boilerplate) with:

```java
public void testPredicates() {
    assertTrue(TombstoneCodes.isOwnStone(0));
    assertTrue(TombstoneCodes.isOwnStone(360));
    assertFalse(TombstoneCodes.isOwnStone(361));
    assertFalse(TombstoneCodes.isOwnStone(-1));
    assertTrue(TombstoneCodes.isOwnPass(722));
    assertFalse(TombstoneCodes.isOwnPass(361));
    assertTrue(TombstoneCodes.isOwnEntry(722));
    assertTrue(TombstoneCodes.isOwnEntry(180));
    assertFalse(TombstoneCodes.isOwnEntry(361));
    assertTrue(TombstoneCodes.isDeadStone(361));
    assertTrue(TombstoneCodes.isDeadStone(721));
    assertFalse(TombstoneCodes.isDeadStone(360));
    assertFalse(TombstoneCodes.isDeadStone(722));
    assertFalse(TombstoneCodes.isDeadStone(723));
    assertEquals(361, TombstoneCodes.deadCode(0));
    assertEquals(721, TombstoneCodes.deadCode(360));
    assertEquals(5, TombstoneCodes.pointOf(5));
    assertEquals(5, TombstoneCodes.pointOf(366));
    assertEquals(-1, TombstoneCodes.pointOf(722));
    assertEquals(-1, TombstoneCodes.pointOf(723));
    assertEquals(-1, TombstoneCodes.pointOf(-1));
}

public void testCountsAndCurrentPlayer() {
    int[] h = {180, 3, 364, 722, 722, 400};
    assertEquals(4, TombstoneCodes.countOwnEntries(h, 6));
    assertEquals(1, TombstoneCodes.countOwnEntries(h, 1));
    assertEquals(1, TombstoneCodes.currentPlayer(new int[0], 0));
    assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180}, 1));
    assertEquals(1, TombstoneCodes.currentPlayer(new int[]{180, 3}, 2));
    assertEquals(1, TombstoneCodes.currentPlayer(new int[]{180, 3, 364}, 3));
    assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180, 3, 364, 722}, 4));
    // n bounds the scan: trailing array slots are ignored
    assertEquals(2, TombstoneCodes.currentPlayer(new int[]{180, 3, 364}, 1));
}

public void testLastTurnLength() {
    assertEquals(0, TombstoneCodes.lastTurnLength(new int[0], 0));
    assertEquals(0, TombstoneCodes.lastTurnLength(new int[]{180}, 1));
    assertEquals(1, TombstoneCodes.lastTurnLength(new int[]{180, 3}, 2));
    assertEquals(1, TombstoneCodes.lastTurnLength(new int[]{180, 722}, 2));
    assertEquals(2, TombstoneCodes.lastTurnLength(new int[]{180, 3, 364}, 3));
    assertEquals(2, TombstoneCodes.lastTurnLength(new int[]{180, 722, 361}, 3));
}

public void testValidateTurnShape() {
    String one = "Invalid turn: send one or two moves.";
    String first = "Invalid turn: the first move must be your stone or a pass.";
    String second = "Invalid turn: the second move must be a dead stone.";
    assertNull(TombstoneCodes.validateTurnShape(new int[]{3}));
    assertNull(TombstoneCodes.validateTurnShape(new int[]{3, 365}));
    assertNull(TombstoneCodes.validateTurnShape(new int[]{722}));
    assertNull(TombstoneCodes.validateTurnShape(new int[]{722, 721}));
    assertEquals(one, TombstoneCodes.validateTurnShape(null));
    assertEquals(one, TombstoneCodes.validateTurnShape(new int[0]));
    assertEquals(one, TombstoneCodes.validateTurnShape(new int[]{1, 362, 3}));
    assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{361}));
    assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{723}));
    assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{-1}));
    assertEquals(first, TombstoneCodes.validateTurnShape(new int[]{-2, 365}));
    assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 722}));
    assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 3}));
    assertEquals(second, TombstoneCodes.validateTurnShape(new int[]{3, 723}));
}

public void testLabels() {
    assertEquals("K10", TombstoneCodes.coordinate(180));
    assertEquals("A19", TombstoneCodes.coordinate(0));
    assertEquals("H19", TombstoneCodes.coordinate(7));
    assertEquals("J19", TombstoneCodes.coordinate(8));
    assertEquals("T1", TombstoneCodes.coordinate(360));
    assertEquals("K10", TombstoneCodes.entryLabel(180));
    assertEquals("PASS", TombstoneCodes.entryLabel(722));
    assertEquals("dead A19", TombstoneCodes.entryLabel(361));
    assertEquals("dead K10", TombstoneCodes.entryLabel(361 + 180));
    assertEquals("K10", TombstoneCodes.turnLabel(180, -1));
    assertEquals("PASS", TombstoneCodes.turnLabel(722, -1));
    assertEquals("K10 + dead L10", TombstoneCodes.turnLabel(180, 361 + 181));
    assertEquals("PASS + dead A19", TombstoneCodes.turnLabel(722, 361));
    try { TombstoneCodes.entryLabel(723); fail("723 is not an entry"); } catch (IllegalArgumentException expected) { }
    try { TombstoneCodes.entryLabel(-1); fail("-1 is not an entry"); } catch (IllegalArgumentException expected) { }
}

public void testPgnTokensRoundTripEveryCode() {
    assertEquals("K10", TombstoneCodes.pgnToken(180));
    assertEquals("pass", TombstoneCodes.pgnToken(722));
    assertEquals("xA19", TombstoneCodes.pgnToken(361));
    for (int code = 0; code <= 722; code++) {
        assertEquals("code " + code, code, TombstoneCodes.parsePgnToken(TombstoneCodes.pgnToken(code)));
    }
    assertEquals(722, TombstoneCodes.parsePgnToken("PASS"));
    assertEquals(361 + 180, TombstoneCodes.parsePgnToken("XK10"));
}

public void testParsePgnTokenRejectsGarbage() {
    String[] bad = {null, "", "x", "X", "pas", "Z5", "xZ99", "K0", "K20", "1-0", "1.", "Resign"};
    for (String t : bad) {
        assertEquals("token " + t, -1, TombstoneCodes.parsePgnToken(t));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD. Expected: `BUILD FAILED`, `cannot find symbol ... TombstoneCodes`.

- [ ] **Step 3: Implement `TombstoneCodes`**

Rules the tests do not spell out:
- `entryLabel`: stone → `coordinate(code)`; `722` → `"PASS"`; dead → `"dead " + coordinate(code - 361)`; anything else → `IllegalArgumentException`. `turnLabel(own, dead)` = `entryLabel(own)` plus `" + " + entryLabel(dead)` when `dead != -1`.
- `coordinate(point)` delegates to `PGNGameFormat.formatCoordinates(point)` (one formatter for labels and PGN).
- `parsePgnToken`: `null`/empty → `-1`; `"pass"` (case-insensitive) → `722`; a token of length ≥ 3 starting with `x`/`X` → `361 + parseCoord(rest)` (or `-1`); otherwise `parseCoord(token)`. `parseCoord(s)` is a private helper: call `PGNGameFormat.parseCoordinates(s)` inside `try { } catch (NumberFormatException e) { return -1; }`, then accept the result only if it is in `0..360` **and** `PGNGameFormat.formatCoordinates(result).equalsIgnoreCase(s)` (this rejects `"Z5"`, `"K0"`, `"K20"`, which `parseCoordinates` maps to in-range garbage or out-of-range codes).
- `validateTurnShape` checks in the order: length (null, 0, > 2), first code, second code — returning the exact strings in the test.
- `countOwnEntries` scans `codes[0..n-1]`; `currentPlayer` = `countOwnEntries % 2 + 1`; `lastTurnLength` = `0` if `n <= 1`, `2` if `codes[n-1]` is dead, else `1`.

- [ ] **Step 4: Run to verify it passes**

Run: BUILD, then RUN `TombstoneCodesTest`. Expected: `OK (7 tests)`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/TombstoneCodes.java dsg_src/java/org/pente/game/test/TombstoneCodesTest.java
git commit -m "Add TombstoneCodes move codec"
```

---
### Task 3: TombstoneState — turn grammar, captures, wins, draws (plus `SimpleGridState` capacity)

**Files:**
- Modify: `dsg_src/java/org/pente/game/SimpleGridState.java` (constructor `:77-83`; hash arrays `:349-350`)
- Create: `dsg_src/java/org/pente/game/TombstoneState.java`
- Test: `dsg_src/java/org/pente/game/test/TombstoneStateTest.java` (new)

**Interfaces:**
- Consumes: `TombstoneCodes` predicates and constants (Task 2); `GridStateDecorator(GridState)` (`GridStateDecorator.java:40-42`, protected field `gridState`); `SimpleGridState.addMove` (`:179-193`: for an in-board code it throws `IllegalArgumentException` if occupied and writes the **parity** colour `getNumMoves() % 2 + 1`; for an out-of-board code it only appends when `allowNonBoardMoves`); `SimpleGridState.updateHash(HashCalculator)` (`:559-603`: copies the previous slot, passes `p = 0` for out-of-board codes, calls `calcHash` for rotations 0..7, stores the max in slot 8); `SimpleGridState.clear()` (`:166-174`).
- Produces:
  - `public SimpleGridState(int boardSizeX, int boardSizeY, int maxEntries)` — sizes `hashes[maxEntries][9]` and `rots[maxEntries]`; the 2-arg constructor keeps 720.
  - `public class TombstoneState extends GridStateDecorator implements HashCalculator` with `MAX_ENTRIES = 3000`, `CAPTURES_TO_WIN = 10`, `EMPTY_TURNS_FOR_DRAW = 6`; `TombstoneState()`; `void addMove(int code)`; `boolean isValidMove(int code, int player)`; `boolean isAwaitingDeadStone()`; `void skipDeadStone()`; `int getCurrentPlayer()`; `int getCurrentColor()` (= `getCurrentPlayer()`); `int getColor(int entryIndex)`; `int getTurnCount()`; `boolean isGameOver()`; `int getWinner()`; `int getNumCaptures(int player)`; `int getConsecutiveEmptyTurns()`; `void clear()`; `long calcHash(long, int, int, int)` (placeholder in this task, real in Task 4).
  - Undo, replay (`getInstance`), `canPlayerUndo`, `undoTurn`, `getLastTurnLength`, `isTrailingTurnClosedWithoutDead` come in Task 5.

- [ ] **Step 1: Write the failing test**

Create `TombstoneStateTest` (JUnit 3 boilerplate) with these helpers and tests:

```java
private static final int PASS = 722;
private static int pt(int x, int y) { return x + 19 * y; }
private static int dead(int x, int y) { return 361 + pt(x, y); }
private TombstoneState s;

protected void setUp() { s = new TombstoneState(); s.addMove(180); }   // P1's centre, turn 0 closed

/** One own entry (stone or PASS), then close the turn without a dead stone. */
private void ownOnly(int code) { s.addMove(code); s.skipDeadStone(); }

public void testSimpleGridStateCapacityConstructor() {
    SimpleGridState big = new SimpleGridState(19, 19, 3000);
    assertEquals(3000, big.getHashes().length);
    assertEquals(9, big.getHashes()[0].length);
    assertEquals(3000, big.getRotations().length);
    assertEquals(19, big.getGridSizeX());
    assertEquals(720, new SimpleGridState(19, 19).getHashes().length);
}

public void testTurnZeroIsCentreOnly() {
    TombstoneState f = new TombstoneState();
    assertEquals(1, f.getCurrentPlayer());
    assertTrue(f.isValidMove(180, 1));
    assertFalse(f.isValidMove(181, 1));
    assertFalse(f.isValidMove(PASS, 1));
    assertFalse(f.isValidMove(180, 2));
    try { f.addMove(PASS); fail("own pass on turn 0"); } catch (IllegalArgumentException expected) { }
    try { f.addMove(361); fail("dead stone on turn 0"); } catch (IllegalArgumentException expected) { }
    assertEquals(0, f.getNumMoves());
    f.addMove(180);
    assertEquals(1, f.getPosition(180));
    assertEquals(1, f.getTurnCount());
    assertFalse(f.isAwaitingDeadStone());
    assertEquals(2, f.getCurrentPlayer());
    assertEquals(1, f.getColor(0));
    assertEquals(0, f.getColor(1));
}

public void testFourTurnShapes() {
    s.addMove(pt(3, 3));                       // P2 own
    assertTrue(s.isAwaitingDeadStone());
    assertEquals(2, s.getCurrentPlayer());
    s.addMove(dead(4, 4));                     // P2 dead -> turn closed
    assertFalse(s.isAwaitingDeadStone());
    assertEquals(1, s.getCurrentPlayer());
    assertEquals(2, s.getPosition(pt(3, 3)));
    assertEquals(3, s.getPosition(pt(4, 4)));
    ownOnly(pt(10, 10));                       // P1 own only
    assertEquals(1, s.getPosition(pt(10, 10)));
    assertEquals(2, s.getCurrentPlayer());
    s.addMove(PASS);                           // P2 dead only
    assertTrue(s.isAwaitingDeadStone());
    s.addMove(dead(5, 5));
    assertEquals(0, s.getConsecutiveEmptyTurns());
    assertEquals(1, s.getCurrentPlayer());
    ownOnly(PASS);                             // P1 empty turn
    assertEquals(1, s.getConsecutiveEmptyTurns());
    assertEquals(2, s.getCurrentPlayer());
    assertEquals(7, s.getNumMoves());
    assertEquals(5, s.getTurnCount());
    int[] colors = {1, 2, 2, 1, 2, 2, 1};
    for (int i = 0; i < colors.length; i++) assertEquals("entry " + i, colors[i], s.getColor(i));
    assertEquals(0, s.getColor(7));
    assertFalse(s.isGameOver());
    assertEquals(0, s.getWinner());
}

public void testIsValidMoveByPhase() {
    assertTrue(s.isValidMove(PASS, 2));
    assertTrue(s.isValidMove(pt(0, 0), 2));
    assertFalse(s.isValidMove(180, 2));
    assertFalse(s.isValidMove(361, 2));
    assertFalse(s.isValidMove(723, 2));
    assertFalse(s.isValidMove(-1, 2));
    assertFalse(s.isValidMove(pt(0, 0), 1));
    s.addMove(pt(0, 0));
    assertFalse(s.isValidMove(361, 2));        // dead on point 0, now occupied by P2's stone
    assertTrue(s.isValidMove(362, 2));
    assertFalse(s.isValidMove(361 + 180, 2));  // centre occupied
    assertFalse(s.isValidMove(pt(1, 0), 2));   // own stone in the dead phase
    assertFalse(s.isValidMove(PASS, 2));
    assertFalse(s.isValidMove(362, 1));
}

public void testReplayPrimitiveRejectsIllegalEntries() {
    try { s.addMove(361); fail("dead stone while not awaiting"); } catch (IllegalArgumentException expected) { }
    try { s.addMove(180); fail("occupied"); } catch (IllegalArgumentException expected) { }
    try { s.addMove(723); fail("above 722"); } catch (IllegalArgumentException expected) { }
    try { s.addMove(-1); fail("negative"); } catch (IllegalArgumentException expected) { }
    s.addMove(pt(3, 3));
    try { s.addMove(361 + pt(3, 3)); fail("dead on occupied"); } catch (IllegalArgumentException expected) { }
    assertEquals(2, s.getNumMoves());          // rejected entries leave no trace
    assertTrue(s.isAwaitingDeadStone());
}

/** A rejected own entry while awaiting must not run the implicit close first. */
public void testRejectedOwnEntryWhileAwaitingLeavesTurnOpen() {
    s.addMove(PASS);                           // P2 own pass, awaiting; closing it would count an empty turn
    try { s.addMove(180); fail("occupied"); } catch (IllegalArgumentException expected) { }
    assertTrue(s.isAwaitingDeadStone());
    assertEquals(2, s.getNumMoves());
    assertEquals(0, s.getConsecutiveEmptyTurns());
    assertEquals(2, s.getTurnCount());
    assertEquals(2, s.getCurrentPlayer());
    s.skipDeadStone();                         // turn 1 empty
    for (int i = 0; i < 4; i++) ownOnly(PASS); // turns 2-5 empty
    s.addMove(PASS);                           // turn 6 open; its close would be the 6th empty turn
    try { s.addMove(pt(0, 0)); fail("own entry after the drawing close"); } catch (IllegalArgumentException expected) { }
    assertFalse(s.isGameOver());
    assertTrue(s.isAwaitingDeadStone());
    assertEquals(5, s.getConsecutiveEmptyTurns());
    s.skipDeadStone();
    assertTrue(s.isGameOver());
    assertEquals(0, s.getWinner());
}

public void testOwnEntryWhileAwaitingImplicitlyClosesTurn() {
    s.addMove(pt(3, 3));                       // P2, awaiting
    s.addMove(pt(4, 4));                       // closes P2's turn as a skip; P1's own stone
    assertEquals(2, s.getPosition(pt(3, 3)));
    assertEquals(1, s.getPosition(pt(4, 4)));
    assertTrue(s.isAwaitingDeadStone());
    assertEquals(1, s.getCurrentPlayer());
    assertEquals(3, s.getTurnCount());
    s.addMove(PASS);                           // closes P1's (non-empty) turn; P2 passes
    assertEquals(0, s.getConsecutiveEmptyTurns());
    s.addMove(PASS);                           // closes P2's empty turn; P1 passes
    assertEquals(1, s.getConsecutiveEmptyTurns());
}

public void testSkipDeadStoneRequiresAwaiting() {
    try { new TombstoneState().skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
    try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
    s.addMove(pt(3, 3));
    s.addMove(dead(4, 4));
    try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
}

public void testCaptureByOwnStoneAndDeadOnVacatedPoint() {
    ownOnly(pt(10, 9));                        // P2
    ownOnly(pt(0, 18));                        // P1 filler
    ownOnly(pt(11, 9));                        // P2
    s.addMove(pt(12, 9));                      // P1: (9,9) is P1 -> captures (11,9),(10,9)
    assertEquals(2, s.getNumCaptures(1));
    assertEquals(0, s.getNumCaptures(2));
    assertEquals(0, s.getPosition(pt(10, 9)));
    assertEquals(0, s.getPosition(pt(11, 9)));
    assertTrue(s.isValidMove(dead(10, 9), 1)); // a just-vacated point takes a dead stone (D2)
    s.addMove(dead(10, 9));
    assertEquals(3, s.getPosition(pt(10, 9)));
}

public void testPlacingBetweenEnemyStonesIsSafe() {
    ownOnly(pt(0, 0));                         // P2 filler
    ownOnly(pt(12, 9));                        // P1
    ownOnly(pt(10, 9));                        // P2
    ownOnly(pt(0, 18));                        // P1 filler
    s.addMove(pt(11, 9));                      // P2 completes P1 . P2 P2 . P1 -> no capture
    assertEquals(2, s.getPosition(pt(10, 9)));
    assertEquals(2, s.getPosition(pt(11, 9)));
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(0, s.getNumCaptures(2));
}

public void testDeadStoneBlocksCaptureAsOpponent() {
    s.addMove(pt(11, 9));                      // P2 own
    s.addMove(dead(10, 9));                    // P2 dead between centre and (11,9)
    s.addMove(pt(12, 9));                      // P1: pattern P1 dead P2 P1 is not a capture
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(2, s.getPosition(pt(11, 9)));
    assertEquals(3, s.getPosition(pt(10, 9)));
}

public void testDeadStoneBlocksCaptureAsOwn() {
    s.addMove(pt(0, 0));                       // P2
    s.addMove(dead(11, 9));
    ownOnly(pt(10, 9));                        // P1: (9,9),(10,9)
    s.addMove(pt(8, 9));                       // P2: P2 P1 P1 dead -> no capture
    assertEquals(0, s.getNumCaptures(2));
    assertEquals(1, s.getPosition(pt(9, 9)));
    assertEquals(1, s.getPosition(pt(10, 9)));
}

public void testDeadStoneNeverCaptures() {
    ownOnly(pt(10, 9));                        // P2
    ownOnly(pt(0, 18));                        // P1
    ownOnly(pt(11, 9));                        // P2
    s.addMove(PASS);                           // P1 passes own
    s.addMove(dead(12, 9));                    // P1 dead at the capture point
    assertEquals(2, s.getPosition(pt(10, 9)));
    assertEquals(2, s.getPosition(pt(11, 9)));
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(0, s.getNumCaptures(2));
}

public void testCaptureDoesNotWrapAcrossEdge() {
    ownOnly(pt(17, 9));                        // P2
    ownOnly(pt(0, 10));                        // P1: linear index 190 = 189 + 1
    ownOnly(pt(18, 9));                        // P2
    s.addMove(pt(16, 9));                      // P1: east is (17,9) P2, (18,9) P2, then the edge
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(2, s.getPosition(pt(17, 9)));
    assertEquals(2, s.getPosition(pt(18, 9)));
}

public void testFiveWins() {
    int[] p1 = {pt(10, 9), pt(11, 9), pt(12, 9)};
    int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6)};
    for (int i = 0; i < 3; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
    ownOnly(p2[3]);
    assertTrue(s.isValidMove(pt(13, 9), 1));
    s.addMove(pt(13, 9));                      // (9..13, 9)
    assertTrue(s.isGameOver());
    assertEquals(1, s.getWinner());
    assertFalse(s.isAwaitingDeadStone());      // D4: no dead stone after a winning stone
    assertFalse(s.isValidMove(dead(0, 18), 1));
    try { s.addMove(dead(0, 18)); fail("entry after game over"); } catch (IllegalArgumentException expected) { }
    try { s.skipDeadStone(); fail(); } catch (IllegalStateException expected) { }
}

public void testOverlineWins() {
    int[] p1 = {pt(10, 9), pt(11, 9), pt(13, 9), pt(14, 9)};
    int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6), pt(0, 8)};
    for (int i = 0; i < 4; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
    ownOnly(p2[4]);
    s.addMove(pt(12, 9));                      // (9..14, 9): six in a row
    assertTrue(s.isGameOver());
    assertEquals(1, s.getWinner());
}

public void testDeadStoneIsNotCountedInLine() {
    s.addMove(pt(0, 0));                       // P2
    s.addMove(dead(13, 9));
    int[] p1 = {pt(10, 9), pt(11, 9), pt(12, 9)};
    int[] p2 = {pt(0, 2), pt(0, 4), pt(0, 6)};
    for (int i = 0; i < 3; i++) { ownOnly(p1[i]); ownOnly(p2[i]); }
    s.addMove(pt(14, 9));                      // P1 P1 P1 P1 dead P1: not a five
    assertFalse(s.isGameOver());
    assertTrue(s.isAwaitingDeadStone());
}

public void testFiveDoesNotWrapAcrossEdge() {
    int[] p1 = {pt(16, 9), pt(17, 9), pt(18, 9), pt(0, 10)};
    int[] p2 = {pt(0, 0), pt(0, 2), pt(0, 4), pt(0, 6)};
    for (int i = 0; i < 4; i++) { ownOnly(p2[i]); ownOnly(p1[i]); }
    ownOnly(pt(0, 8));                         // P2
    s.addMove(pt(1, 10));                      // linear 187..191 are consecutive, but not a line
    assertFalse(s.isGameOver());
}

public void testFifthCaptureWins() {
    int[] rows = {1, 4, 7, 13, 16};
    for (int k = 0; k < 5; k++) {
        int r = rows[k];
        ownOnly(pt(3, r));                     // P2
        ownOnly(pt(2, r));                     // P1 anchor
        ownOnly(pt(4, r));                     // P2
        s.addMove(pt(5, r));                   // P1 captures (4,r),(3,r)
        assertEquals(2 * (k + 1), s.getNumCaptures(1));
        if (k < 4) { assertFalse(s.isGameOver()); s.skipDeadStone(); }
    }
    assertTrue(s.isGameOver());
    assertEquals(1, s.getWinner());
}

public void testSixEmptyTurnsDraw() {
    for (int i = 0; i < 5; i++) ownOnly(PASS);
    assertFalse(s.isGameOver());
    assertEquals(5, s.getConsecutiveEmptyTurns());
    ownOnly(PASS);
    assertTrue(s.isGameOver());
    assertEquals(0, s.getWinner());
    assertEquals(6, s.getConsecutiveEmptyTurns());
    assertFalse(s.isValidMove(pt(0, 0), s.getCurrentPlayer()));
}

public void testNonEmptyTurnResetsEmptyCounter() {
    for (int i = 0; i < 5; i++) ownOnly(PASS);
    ownOnly(pt(0, 0));                         // turn 6 (P1): own only resets
    assertEquals(0, s.getConsecutiveEmptyTurns());
    for (int i = 0; i < 5; i++) ownOnly(PASS);
    assertFalse(s.isGameOver());
    s.addMove(PASS);                           // dead only also resets
    s.addMove(dead(1, 1));
    assertEquals(0, s.getConsecutiveEmptyTurns());
    assertFalse(s.isGameOver());
}

public void testFullBoardDrawAfterLongHistory() {
    for (int q = 0; q < 361; q++) {
        if (q == 180) continue;
        s.addMove(PASS);
        s.addMove(361 + q);
        if (s.isGameOver()) break;
        for (int k = 0; k < 5; k++) ownOnly(PASS);
    }
    assertTrue(s.isGameOver());
    assertEquals(0, s.getWinner());
    assertEquals(1 + 360 * 2 + 359 * 5, s.getNumMoves());   // 2516 entries > the old 720 slots
}

/** Spec §3.4: an own stone that fills the last point without winning is a draw; a capture gives 2 points back. */
public void testFullBoardDrawByOwnStoneAfterCapture() {
    ownOnly(pt(3, 1));                         // P2
    ownOnly(pt(2, 1));                         // P1 anchor
    ownOnly(pt(4, 1));                         // P2
    s.addMove(pt(5, 1));                       // P1 captures (4,1),(3,1): both points are empty again
    assertEquals(2, s.getNumCaptures(1));
    s.skipDeadStone();
    int last = pt(18, 18);
    for (int q = 0; q < 361; q++) {
        if (q == 180 || q == pt(2, 1) || q == pt(5, 1) || q == last) continue;
        s.addMove(PASS);
        s.addMove(361 + q);                    // fills (3,1) and (4,1) too
        assertFalse("ended early at point " + q, s.isGameOver());   // catches a missing +2 per capture
    }
    s.addMove(last);                           // own stone on the last empty point, no five, no capture
    assertTrue(s.isGameOver());
    assertEquals(0, s.getWinner());
    assertFalse(s.isAwaitingDeadStone());
    assertEquals(1 + 4 + 357 * 2 + 1, s.getNumMoves());
}
// Spec §17 names a "2812-entry worst case". That bound (§3.5) needs refills of captured
// points and is impractical to script; this 2516-entry legal history is
// capture-free, exercises the capacity constructor far past 720, and MAX_ENTRIES = 3000
// covers 2812 by the §3.5 argument.
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD. Expected: `BUILD FAILED`, `cannot find symbol` for `TombstoneState` and for the 3-arg `SimpleGridState` constructor.

- [ ] **Step 3: Add `SimpleGridState(int boardSizeX, int boardSizeY, int maxEntries)`**

Delegate to the 2-arg constructor, then replace `hashes` with `new long[maxEntries][9]` and `rots` with `new int[maxEntries]`. Leave the field initialisers at `:349-350` as the 720 default.

- [ ] **Step 4: Implement `TombstoneState` rules**

- Constructor: `super(new SimpleGridState(19, 19, MAX_ENTRIES))`, then `((SimpleGridState) gridState).setAllowNonBoardMoves(true)`. Private constant `CENTER = 180`. Allocate per-entry arrays `entryPlayer`, `capturesBefore1`, `capturesBefore2` of size `MAX_ENTRIES`; `captures = new int[3]`; transient `lastCapturedPoints = new int[16]` + `lastCapturedCount`; `turnCount`, `awaitingDead`, `consecutiveEmptyTurns`, `gameOver`, `winner`, and a private `emptyPoints` (starts at 361; minus 1 for every stone or dead stone placed, plus 2 per capture) for the full-board check. Keep a `serialVersionUID` like the other states.
- `playerOfTurn(t)` = `t % 2 + 1` (turn 0 → 1, odd → 2, even → 1; spec §5.3). `getCurrentPlayer()` = `awaitingDead ? playerOfTurn(turnCount - 1) : playerOfTurn(turnCount)`. `getCurrentColor()` returns `getCurrentPlayer()` (the inner state's parity colour is wrong for this game). `getColor(i)` = `entryPlayer[i]` for `0 <= i < getNumMoves()`, else `0`. `getPosition(int)` is inherited and still throws for out-of-board codes; callers decode first.
- `isValidMove(code, player)` — exactly spec §5.5: false if `gameOver` or `player != getCurrentPlayer()`; `numMoves == 0` → `code == CENTER`; `awaitingDead` → dead code with an empty decoded point; else own stone on an empty point, or `722`.
- `addMove(code)` — the replay primitive, spec §5.4 steps 1-5 and the in-method order (a)-(g). Validate **before** mutating anything, so a rejected entry leaves no trace. A dead entry while not awaiting, an occupied target, a code outside `0..722`, any entry after `gameOver`, an own pass or dead code at `numMoves == 0`, or `numMoves == MAX_ENTRIES` → `IllegalArgumentException`. Every one of these checks — including range and occupancy of an own entry that arrives while `awaitingDead` — runs **before** the implicit close, and the close itself never throws. One more rejection is predicted rather than discovered: an own entry while `awaitingDead`, when the open turn is empty (last entry `722`) and `consecutiveEmptyTurns + 1 >= EMPTY_TURNS_FOR_DRAW`, would follow a drawing close, so it is rejected with the turn left open (pinned by `testRejectedOwnEntryWhileAwaitingLeavesTurnOpen`). Only after all checks pass: own entry while `awaitingDead` → `closeTurn(lastEntryIsPass)` first (step 2). Then: (b) `gridState.addMove(code)`; (c) own stone → `gridState.setPosition(code, p)` (overwrites the parity colour), dead → `gridState.setPosition(code - 361, 3)`; snapshot `captures[1]`/`captures[2]` into locals **here, before (d)**; (d) own stone → `applyCaptures(code, p)`, which fills `lastCapturedPoints`/`lastCapturedCount` (set the count to 0 for passes and dead entries); (e) `entryPlayer[i] = p`, `capturesBefore1/2[i]` = the snapshot taken before (d) (spec §5.4 lists (e) after (d), but the values are the pre-(d) counts); (f) `updateHash(this)`; (g) own stone: `captures[p] >= CAPTURES_TO_WIN || hasFive(code, p)` → `gameOver = true; winner = p` (turn closed, `awaitingDead = false`); else if `emptyPoints == 0` → draw (`gameOver`, `winner = 0`); else `awaitingDead = true`. A pass: same full-board check, else `awaitingDead = true`. A dead entry: `closeTurn(false)` then the full-board check. `turnCount++` on every own entry. Turn 0 (`numMoves == 0`) closes at once: `turnCount = 1`, `awaitingDead = false`.
- `closeTurn(boolean empty)`: `awaitingDead = false`; `consecutiveEmptyTurns = empty ? consecutiveEmptyTurns + 1 : 0`; at `>= EMPTY_TURNS_FOR_DRAW` → `gameOver = true; winner = 0`. The open turn is empty iff its own entry (the last entry) is `722`. Both the implicit close in `addMove` and `skipDeadStone()` use it.
- `skipDeadStone()`: `IllegalStateException` unless `awaitingDead`; else `closeTurn(lastEntryIsPass)`.
- `applyCaptures(point, c)`: for each of the 8 `(dx, dy)` directions, with `(x, y)` of `point`: if `(x+dx, y+dy)` and `(x+2dx, y+2dy)` hold `3 - c` and `(x+3dx, y+3dy)` holds `c`, all in `0..18` on both axes, set both to `0`, add 2 to `captures[c]`, add 2 to `emptyPoints`, and record both points. Use x/y bounds, never linear-index arithmetic (Review Focus 1).
- `hasFive(point, c)`: for each of the 4 axes, count contiguous cells equal to `c` in both directions using x/y bounds; `>= 5` wins (overlines allowed). Cells of value `3` stop the run.
- `isGameOver()`, `getWinner()` return the stored fields (never `3 - getCurrentPlayer()`); `getNumCaptures(p)` = `captures[p]`; `getTurnCount()`, `getConsecutiveEmptyTurns()`, `isAwaitingDeadStone()` return fields.
- `clear()`: `gridState.clear()` and reset every field above to its constructor value.
- `calcHash(long cHash, int p, int move, int rot)`: **placeholder in this task** — `return cHash;` with a `// replaced in Plan A Task 4` comment. Task 4 writes the real body.

- [ ] **Step 5: Run to verify it passes**

Run: BUILD, then RUN `TombstoneStateTest`. Expected: `OK (23 tests)`.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/game/SimpleGridState.java dsg_src/java/org/pente/game/TombstoneState.java dsg_src/java/org/pente/game/test/TombstoneStateTest.java
git commit -m "Add TombstoneState turn rules, captures and draws"
```

---
### Task 4: TombstoneState — Zobrist hashing

**Files:**
- Modify: `dsg_src/java/org/pente/game/TombstoneState.java` (`calcHash` placeholder from Task 3)
- Test: `dsg_src/java/org/pente/game/test/TombstoneZobristTest.java` (extend)

**Interfaces:**
- Consumes: `ZobristUtil.rand[0..4]`, `TOMBSTONE_DEAD_ROW` (Task 1); Task 3's `entryPlayer`, `capturesBefore1/2`, `captures`, `lastCapturedPoints`/`lastCapturedCount` (all set before `updateHash(this)` runs, order (d)-(f)); `rotateMove(int, int)` (inherited, `SimpleGridState.java:401-419`; it returns out-of-board codes **unrotated**, so always pass a decoded point).
- Produces: `long calcHash(long cHash, int p, int move, int rot)` per spec §6.3; `getHash()`, `getHash(int)`, `getRotation(int)` (inherited) now meaningful for Tombstone. Plan B's archive writer stores these as `pente_move.hash_key`/`rotation`.

- [ ] **Step 1: Write the failing tests**

Add to `TombstoneZobristTest`:

```java
private static final int PASS = 722;
private static int pt(int x, int y) { return x + 19 * y; }
private static int dead(int x, int y) { return 361 + pt(x, y); }

/** Canonical hash recomputed from the board alone: max over 8 rotations of
 *  stones/dead stones at rotated points, XOR the (unrotated) capture-count keys. */
private static long expectedCanonical(TombstoneState s) {
    long max = Long.MIN_VALUE;
    for (int rot = 0; rot < 8; rot++) {
        long h = 0;
        for (int q = 0; q < 361; q++) {
            int v = s.getPosition(q);
            int r = s.rotateMove(q, rot);
            if (v == 1) h ^= ZobristUtil.rand[0][r];
            else if (v == 2) h ^= ZobristUtil.rand[1][r];
            else if (v == 3) h ^= ZobristUtil.rand[ZobristUtil.TOMBSTONE_DEAD_ROW][r];
        }
        if (s.getNumCaptures(1) > 0) h ^= ZobristUtil.rand[2][s.getNumCaptures(1)];
        if (s.getNumCaptures(2) > 0) h ^= ZobristUtil.rand[3][s.getNumCaptures(2)];
        max = Math.max(max, h);
    }
    return max;
}

/** Mixed game: own+dead, implicit closes, a P1 capture, a dead stone on the vacated point, passes. */
private static final int[] SCRIPT = {
        180, pt(10, 9), dead(3, 3), pt(0, 18), pt(11, 9), pt(12, 9), dead(10, 9),
        PASS, dead(4, 4), PASS, pt(5, 5)};

private static TombstoneState replay(int[] codes) {
    TombstoneState s = new TombstoneState();
    for (int c : codes) s.addMove(c);
    return s;
}

public void testHashIsPureFunctionOfPositionAfterEveryEntry() {
    TombstoneState s = new TombstoneState();
    for (int i = 0; i < SCRIPT.length; i++) {
        s.addMove(SCRIPT[i]);
        assertEquals("entry " + i, expectedCanonical(s), s.getHash());
        assertEquals(s.getHash(), s.getHash(i));
    }
    assertEquals(2, s.getNumCaptures(1));       // the script really captured
}

public void testPassKeepsHashSlot() {
    TombstoneState s = replay(new int[]{180, pt(3, 3), dead(4, 4)});
    s.addMove(PASS);
    assertTrue(s.getHash(2) != 0);                 // a constant-0 hash would pass the slot check
    assertEquals(s.getHash(2), s.getHash(3));
    assertEquals(s.getRotation(2), s.getRotation(3));
}

public void testTwoMoveOrdersGiveSameHash() {
    TombstoneState sa = replay(new int[]{180, pt(3, 3), dead(5, 5), pt(4, 4), dead(6, 6)});
    long a = sa.getHash();
    assertTrue(a != 0);
    assertEquals(expectedCanonical(sa), a);
    long b = replay(new int[]{180, pt(3, 3), dead(6, 6), pt(4, 4), dead(5, 5)}).getHash();
    long c = replay(new int[]{180, pt(3, 3), dead(5, 5), pt(4, 4), dead(6, 6), PASS, PASS}).getHash();
    assertEquals(a, b);
    assertEquals(a, c);
}

public void testEightRotationsShareCanonicalHash() {
    TombstoneState proto = new TombstoneState();
    TombstoneState baseState = replay(SCRIPT);
    long base = baseState.getHash();
    assertTrue(base != 0);
    assertEquals(expectedCanonical(baseState), base);
    for (int k = 0; k < 8; k++) {
        int[] rotated = new int[SCRIPT.length];
        for (int i = 0; i < SCRIPT.length; i++) {
            int c = SCRIPT[i];
            rotated[i] = TombstoneCodes.isOwnStone(c) ? proto.rotateMove(c, k)
                    : TombstoneCodes.isDeadStone(c) ? 361 + proto.rotateMove(c - 361, k) : c;
        }
        TombstoneState r = replay(rotated);
        assertEquals("rotation " + k, 2, r.getNumCaptures(1));
        assertEquals("rotation " + k, base, r.getHash());
    }
}

public void testDeadStoneHashDiffersFromOwnStone() {
    long stone = replay(new int[]{180, pt(3, 3)}).getHash();
    long deadStone = replay(new int[]{180, PASS, dead(3, 3)}).getHash();
    assertTrue(stone != deadStone);
    assertTrue(deadStone != replay(new int[]{180}).getHash());
}

public void testHashSlotsBeyondOldCapacity() {
    TombstoneState s = new TombstoneState();
    s.addMove(180);
    for (int q = 0; s.getNumMoves() <= 800; q++) {
        if (q == 180) continue;
        s.addMove(PASS);
        s.addMove(361 + q);
        for (int k = 0; k < 5; k++) { s.addMove(PASS); s.skipDeadStone(); }
    }
    assertEquals(expectedCanonical(s), s.getHash());
    assertEquals(s.getHash(), s.getHash(s.getNumMoves() - 1));
}
```

- [ ] **Step 2: Run to verify they fail**

Run: BUILD, then RUN `TombstoneZobristTest`. Expected: against the Task 3 placeholder (`return cHash;`, so every slot stays `0`) the six new tests fail — `testHashIsPureFunctionOfPositionAfterEveryEntry` and `testHashSlotsBeyondOldCapacity` (`expected:<...> but was:<0>`), `testPassKeepsHashSlot`, `testTwoMoveOrdersGiveSameHash` and `testEightRotationsShareCanonicalHash` (on their `!= 0` asserts), and `testDeadStoneHashDiffersFromOwnStone` (`0 != 0`); the three Task 1 tests still pass.

- [ ] **Step 3: Replace the `calcHash` placeholder**

Exactly spec §6.3, decoding `move` itself and ignoring `p` (which `updateHash` derives from the cell and is `0` for out-of-board codes, `SimpleGridState.java:578-581`):
- `722` → return `cHash`.
- dead `361 + q` → `cHash ^ rand[TOMBSTONE_DEAD_ROW][rotateMove(q, rot)]`.
- own stone `q` by `c = entryPlayer[getNumMoves() - 1]` → XOR `rand[c - 1][rotateMove(q, rot)]`; XOR `rand[(3 - c) - 1][rotateMove(cap, rot)]` for each of `lastCapturedPoints[0 .. lastCapturedCount - 1]`; then the capture-count delta on row `c == 1 ? 2 : 3`: XOR out `rand[row][before]` if `before > 0`, XOR in `rand[row][after]` if `after > 0`, where `before` is `capturesBefore1/2[last]` for `c` and `after` is `captures[c]`.

- [ ] **Step 4: Run to verify they pass**

Run: BUILD, then RUN `TombstoneZobristTest` (expected `OK (9 tests)`) and RUN `TombstoneStateTest` (expected `OK (23 tests)`).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/TombstoneState.java dsg_src/java/org/pente/game/test/TombstoneZobristTest.java
git commit -m "Hash Tombstone positions with the dead-stone Zobrist row"
```

---
### Task 5: TombstoneState — replay, undo, live-only queries

**Files:**
- Modify: `dsg_src/java/org/pente/game/TombstoneState.java`
- Test: `dsg_src/java/org/pente/game/test/TombstoneUndoReplayTest.java` (new)

**Interfaces:**
- Consumes: Task 3/4 `TombstoneState` (`addMove`, `closeTurn`, `clear`, fields); `TombstoneCodes.lastTurnLength`, `isOwnEntry`; `MoveData` (`getNumMoves()`, `getMove(int)`); `DefaultGameData` (no-arg constructor, `addMove`) for tests.
- Produces (spec §5.4, §5.6, §16.1): `GridState getInstance(MoveData data)` (replay + finish history); `boolean canPlayerUndo(int seat)`; `int undoTurn()` (returns 1 or 2; `IllegalStateException` when only the centre or nothing is stored); `void undoMove()` (one entry, no history finish); `int getLastTurnLength()`; `boolean isTrailingTurnClosedWithoutDead()`. Plan B uses `undoTurn`, `canPlayerUndo`, `isTrailingTurnClosedWithoutDead`; Plan C uses `getInstance` via `GridStateFactory.createGridState(TB_TOMBSTONE, game)`.

- [ ] **Step 1: Write the failing test**

Create `TombstoneUndoReplayTest` (JUnit 3 boilerplate) with:

```java
private static final int PASS = 722;
private static int pt(int x, int y) { return x + 19 * y; }
private static int dead(int x, int y) { return 361 + pt(x, y); }

private static TombstoneState replayOf(int... codes) {
    DefaultGameData d = new DefaultGameData();
    for (int c : codes) d.addMove(c);
    return (TombstoneState) new TombstoneState().getInstance(d);
}

private static int[] prefix(int[] codes, int n) { return java.util.Arrays.copyOf(codes, n); }

/** Six turns played through the live API: a capture, a dead stone on the vacated point, skips. */
private static TombstoneState live() {
    TombstoneState s = new TombstoneState();
    s.addMove(180);
    s.addMove(pt(10, 9)); s.skipDeadStone();        // t1 P2 own only
    s.addMove(pt(0, 18)); s.skipDeadStone();        // t2 P1 own only
    s.addMove(pt(11, 9)); s.addMove(dead(3, 3));    // t3 P2 own + dead
    s.addMove(pt(12, 9)); s.addMove(dead(10, 9));   // t4 P1 captures, dead on vacated point
    s.addMove(PASS); s.skipDeadStone();             // t5 P2 empty
    s.addMove(PASS); s.addMove(dead(4, 4));         // t6 P1 dead only
    return s;
}

private static void assertSameState(TombstoneState a, TombstoneState b) {
    assertEquals(a.getNumMoves(), b.getNumMoves());
    for (int q = 0; q < 361; q++) assertEquals("point " + q, a.getPosition(q), b.getPosition(q));
    assertEquals(a.getCurrentPlayer(), b.getCurrentPlayer());
    assertEquals(a.isAwaitingDeadStone(), b.isAwaitingDeadStone());
    assertEquals(a.getNumCaptures(1), b.getNumCaptures(1));
    assertEquals(a.getNumCaptures(2), b.getNumCaptures(2));
    assertEquals(a.getConsecutiveEmptyTurns(), b.getConsecutiveEmptyTurns());
    assertEquals(a.getTurnCount(), b.getTurnCount());
    assertEquals(a.isGameOver(), b.isGameOver());
    assertEquals(a.getWinner(), b.getWinner());
    for (int i = 0; i < a.getNumMoves(); i++) {
        assertEquals("hash " + i, a.getHash(i), b.getHash(i));
        assertEquals("rotation " + i, a.getRotation(i), b.getRotation(i));
        assertEquals("color " + i, a.getColor(i), b.getColor(i));
    }
}

public void testReplayEqualsLiveState() {
    TombstoneState s = live();
    assertEquals(10, s.getNumMoves());
    assertSameState(s, replayOf(s.getMoves()));
}

public void testReplayAgreesWithCodesCurrentPlayerForEveryPrefix() {
    int[] codes = live().getMoves();
    for (int n = 0; n <= codes.length; n++) {
        assertEquals("prefix " + n, TombstoneCodes.currentPlayer(codes, n),
                replayOf(prefix(codes, n)).getCurrentPlayer());
    }
}

public void testReplayClosesTrailingOwnEntry() {
    TombstoneState r = replayOf(180, pt(3, 3));
    assertFalse(r.isAwaitingDeadStone());
    assertEquals(1, r.getCurrentPlayer());
    assertTrue(r.isTrailingTurnClosedWithoutDead());
    assertEquals(0, r.getConsecutiveEmptyTurns());
    TombstoneState p = replayOf(180, pt(3, 3), PASS);
    assertEquals(1, p.getConsecutiveEmptyTurns());
    assertEquals(2, p.getCurrentPlayer());
}

public void testReplayOfSixEmptyTurnsIsDraw() {
    TombstoneState five = replayOf(180, PASS, PASS, PASS, PASS, PASS);
    assertFalse(five.isGameOver());
    assertEquals(5, five.getConsecutiveEmptyTurns());
    assertEquals(1, five.getCurrentPlayer());
    TombstoneState six = replayOf(180, PASS, PASS, PASS, PASS, PASS, PASS);
    assertTrue(six.isGameOver());
    assertEquals(0, six.getWinner());
}

public void testReplayRejectsDeadWithoutOwn() {
    try { replayOf(180, dead(3, 3)); fail(); } catch (IllegalArgumentException expected) { }
}

public void testReplayOfEmptyData() {
    TombstoneState r = replayOf();
    assertEquals(0, r.getNumMoves());
    assertEquals(1, r.getCurrentPlayer());
    assertFalse(r.isTrailingTurnClosedWithoutDead());
}

public void testCanPlayerUndoRules() {
    TombstoneState s = replayOf(180);
    assertFalse(s.canPlayerUndo(1));                 // nobody undoes the centre turn
    assertFalse(s.canPlayerUndo(2));
    s.addMove(pt(3, 3));
    assertFalse(s.canPlayerUndo(1));                 // mid-turn
    assertFalse(s.canPlayerUndo(2));
    s.addMove(dead(4, 4));
    assertTrue(s.canPlayerUndo(2));                  // P2 completed the last turn
    assertFalse(s.canPlayerUndo(1));
    TombstoneState over = replayOf(180, PASS, PASS, PASS, PASS, PASS, PASS);
    assertFalse(over.canPlayerUndo(1));
    assertFalse(over.canPlayerUndo(2));
}

public void testUndoTurnTwoEntries() {
    TombstoneState s = replayOf(180);
    s.addMove(pt(3, 3));
    s.addMove(dead(4, 4));
    assertEquals(2, s.getLastTurnLength());
    assertEquals(2, s.undoTurn());
    assertEquals(1, s.getNumMoves());
    assertEquals(0, s.getPosition(pt(3, 3)));
    assertEquals(0, s.getPosition(pt(4, 4)));
    assertEquals(2, s.getCurrentPlayer());
    assertEquals(replayOf(180).getHash(), s.getHash());
    assertEquals(0, s.getLastTurnLength());
}

public void testUndoTurnOneEntry() {
    TombstoneState s = replayOf(180);
    s.addMove(pt(3, 3));
    s.skipDeadStone();
    assertEquals(1, s.getLastTurnLength());
    assertEquals(1, s.undoTurn());
    assertEquals(1, s.getNumMoves());
    assertEquals(2, s.getCurrentPlayer());
    assertFalse(s.isAwaitingDeadStone());
}

public void testUndoTurnRestoresCapturesAndHash() {
    TombstoneState s = live();
    int[] codes = s.getMoves();
    assertEquals(2, s.undoTurn());                   // t6: PASS + dead
    assertEquals(1, s.undoTurn());                   // t5: PASS
    assertEquals(2, s.getNumCaptures(1));
    assertEquals(2, s.undoTurn());                   // t4: the capturing stone + dead
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(2, s.getPosition(pt(10, 9)));
    assertEquals(2, s.getPosition(pt(11, 9)));
    assertEquals(0, s.getPosition(pt(12, 9)));
    assertEquals(1, s.getCurrentPlayer());
    assertSameState(replayOf(prefix(codes, 5)), s);
}

public void testUndoTurnRestoresEmptyTurnCounter() {
    TombstoneState s = replayOf(180);
    for (int i = 0; i < 4; i++) { s.addMove(PASS); s.skipDeadStone(); }
    s.addMove(pt(0, 0)); s.skipDeadStone();          // t5, P2
    assertEquals(0, s.getConsecutiveEmptyTurns());
    assertEquals(1, s.undoTurn());
    assertEquals(4, s.getConsecutiveEmptyTurns());
    assertEquals(2, s.getCurrentPlayer());
}

public void testUndoTurnOnCentreOnlyThrows() {
    try { replayOf(180).undoTurn(); fail(); } catch (IllegalStateException expected) { }
    try { new TombstoneState().undoTurn(); fail(); } catch (IllegalStateException expected) { }
}

public void testUndoMoveLeavesTurnAwaiting() {
    TombstoneState s = replayOf(180);
    s.addMove(pt(3, 3));
    s.addMove(dead(4, 4));
    s.undoMove();
    assertEquals(2, s.getNumMoves());
    assertTrue(s.isAwaitingDeadStone());
    assertEquals(2, s.getCurrentPlayer());
    assertEquals(2, s.getPosition(pt(3, 3)));
    assertEquals(0, s.getPosition(pt(4, 4)));
}

public void testIsTrailingTurnClosedWithoutDead() {
    TombstoneState s = replayOf(180);
    assertFalse(s.isTrailingTurnClosedWithoutDead());
    s.addMove(pt(3, 3));
    assertFalse(s.isTrailingTurnClosedWithoutDead()); // still awaiting
    s.skipDeadStone();
    assertTrue(s.isTrailingTurnClosedWithoutDead());
    s.addMove(pt(4, 4));
    s.addMove(dead(5, 5));
    assertFalse(s.isTrailingTurnClosedWithoutDead());
    s.addMove(PASS);
    s.skipDeadStone();
    assertTrue(s.isTrailingTurnClosedWithoutDead());
}

public void testLastTurnLengthWhileAwaiting() {
    TombstoneState s = replayOf(180);
    assertEquals(0, s.getLastTurnLength());
    s.addMove(pt(3, 3));
    assertEquals(1, s.getLastTurnLength());
    s.addMove(dead(4, 4));
    assertEquals(2, s.getLastTurnLength());
}

public void testClearResets() {
    TombstoneState s = live();
    s.clear();
    assertEquals(0, s.getNumMoves());
    assertEquals(1, s.getCurrentPlayer());
    assertEquals(0, s.getNumCaptures(1));
    assertEquals(0, s.getTurnCount());
    assertEquals(0, s.getConsecutiveEmptyTurns());
    assertFalse(s.isGameOver());
    assertEquals(0, s.getPosition(pt(4, 4)));
    s.addMove(180);
    assertEquals(2, s.getCurrentPlayer());
}
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD. Expected: `BUILD FAILED`, `cannot find symbol` for `undoTurn()`, `getLastTurnLength()` and `isTrailingTurnClosedWithoutDead()` (`canPlayerUndo`, `undoMove` and `getInstance` are inherited from `GridStateDecorator`, which is why they compile but are wrong: the inherited `getInstance` returns a plain `SimpleGridState`).

- [ ] **Step 3: Implement**

- `finishHistory()` (private): if `awaitingDead`, `closeTurn(lastEntryIsPass)` — exactly what `skipDeadStone()` does (spec §5.4).
- `getInstance(MoveData data)`: a new `TombstoneState()`, `addMove(data.getMove(i))` for every entry in order, then `finishHistory()`. `IllegalArgumentException` from `addMove` propagates. Javadoc must carry the spec §5.4 rule: *no caller may take the result (winner, game over, draw) of a finished game from a replayed state; the stored winner is authoritative.*
- `rebuild(int keep)` (private): copy the first `keep` codes (`getMoves()`), `clear()`, then `addMove` each. The replay is the single source of captures, hashes, counters and `emptyPoints`; there is no separate undo bookkeeping.
- `undoTurn()`: `len = getLastTurnLength()`; `len == 0` → `IllegalStateException`; `rebuild(getNumMoves() - len)`; `finishHistory()`; return `len`.
- `undoMove()`: no-op on an empty state; else `rebuild(getNumMoves() - 1)` **without** `finishHistory()`.
- `canPlayerUndo(seat)`: `!gameOver && !awaitingDead && turnCount > 1 && playerOfTurn(turnCount - 1) == seat`.
- `getLastTurnLength()`: `TombstoneCodes.lastTurnLength(getMoves(), getNumMoves())`.
- `isTrailingTurnClosedWithoutDead()`: `!awaitingDead && getNumMoves() > 1 && TombstoneCodes.isOwnEntry(getMove(getNumMoves() - 1))`.

- [ ] **Step 4: Run to verify it passes**

Run: BUILD, then RUN `TombstoneUndoReplayTest` (expected `OK (16 tests)`), RUN `TombstoneStateTest` (`OK (23 tests)`), RUN `TombstoneZobristTest` (`OK (9 tests)`).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/TombstoneState.java dsg_src/java/org/pente/game/test/TombstoneUndoReplayTest.java
git commit -m "Add Tombstone replay and whole-turn undo"
```

---

### Task 6: GridStateFactory registration and the archive sentinel

**Files:**
- Modify: `dsg_src/java/org/pente/game/GridStateFactory.java` — constants after `:37-38` and `:77`; `Game`s after `:135-137`; `allGames[]` `:139-154`; `displaygames[]` `:155-204` (after `:171`, `:187`, `:203`); `normalGames[]` `:206-215`; `speedGames[]` `:216-225`; `tbGames[]` `:226-235`; `createGridState` switch (after the Renju case `:372-378`); `isValidGame` `:400-402`; `getMaxGameId` `:426-428`; new `isTombstone`, `NON_KOTH_LIVE_GAMES`, `NON_KOTH_TB_GAMES`
- Modify: `dsg_src/java/org/pente/game/TombstoneCodes.java` (add `archiveNoNextMove`)
- Modify: `dsg_src/java/org/pente/game/test/RenjuFactoryTest.java:70`
- Test: `dsg_src/java/org/pente/game/test/TombstoneFactoryTest.java` (new)

**Interfaces:**
- Consumes: `TombstoneState()` and `getInstance` (Tasks 3, 5). Facts this task relies on (verified in source): `getGame`/`createGridState(int, MoveData)` index TB ids as `tbGames[(game - 51) / 2]` (`:385-397`), so `TB_TOMBSTONE = 83` must sit at `tbGames[16]`; `gridStates[]` is sized `getNumGames() + 1` and filled by id in a static block (`:237-247`), so `allGames[33]`/`[34]` must be the Tombstone games; `getGameId(name)` scans `allGames` before `tbGames` (`:408-420`), so `"Tombstone Pente"` resolves to 33.
- Produces (spec §7.1, §16.1): `public static final int TOMBSTONE = 33, SPEED_TOMBSTONE = TOMBSTONE + 1, TB_TOMBSTONE = TB_START + TOMBSTONE;` `public static final Game TOMBSTONE_GAME, SPEED_TOMBSTONE_GAME, TB_TOMBSTONE_GAME;` `public static final int[] NON_KOTH_LIVE_GAMES = {TOMBSTONE, SPEED_TOMBSTONE}, NON_KOTH_TB_GAMES = {TB_TOMBSTONE};` `public static boolean isTombstone(int game)`; `TombstoneCodes.archiveNoNextMove(int game)` → `723` for Tombstone ids, `361` otherwise. Plan B uses `NON_KOTH_*` (boot registration) and `archiveNoNextMove` (archive writer/loader); Plans B and C use `isTombstone`.

- [ ] **Step 1: Write the failing test**

Create `TombstoneFactoryTest` (JUnit 3 boilerplate; `import java.util.Arrays;`) with:

```java
private static final int[] IDS = {33, 34, 83};

public void testIds() {
    assertEquals(33, GridStateFactory.TOMBSTONE);
    assertEquals(34, GridStateFactory.SPEED_TOMBSTONE);
    assertEquals(83, GridStateFactory.TB_TOMBSTONE);
}

public void testIsTombstone() {
    for (int g : IDS) assertTrue("game " + g, GridStateFactory.isTombstone(g));
    for (int g : new int[]{0, 1, 31, 32, 35, 50, 51, 81, 82, 84}) assertFalse("game " + g, GridStateFactory.isTombstone(g));
}

public void testCreateGridState() {
    for (int g : IDS) {
        GridState s = GridStateFactory.createGridState(g);
        assertTrue("game " + g, s instanceof TombstoneState);
        assertEquals(19, s.getGridSizeX());
        assertEquals(19, s.getGridSizeY());
    }
    assertEquals(19, GridStateFactory.createGridState(GridStateFactory.TOMBSTONE, 9, 9).getGridSizeX());
}

public void testMoveDataReconstructionKeepsTombstoneType() {
    DefaultGameData d = new DefaultGameData();
    d.addMove(180);
    d.addMove(3 + 19 * 3);
    d.addMove(361 + 4 + 19 * 4);
    for (int g : IDS) {
        GridState s = GridStateFactory.createGridState(g, d);
        assertTrue("game " + g, s instanceof TombstoneState);
        assertEquals(3, s.getNumMoves());
        assertEquals(3, s.getPosition(4 + 19 * 4));
        assertEquals(1, s.getCurrentPlayer());
    }
}

public void testGamesAndNames() {
    assertEquals("Tombstone Pente", GridStateFactory.getGameName(33));
    assertEquals("Speed Tombstone Pente", GridStateFactory.getGameName(34));
    assertEquals("Tombstone Pente", GridStateFactory.getGameName(83));
    assertSame(GridStateFactory.TOMBSTONE_GAME, GridStateFactory.getGame(33));
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, GridStateFactory.getGame(34));
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, GridStateFactory.getGame(83));
    assertTrue(GridStateFactory.SPEED_TOMBSTONE_GAME.isSpeed());
    assertEquals(33, GridStateFactory.getGameId("Tombstone Pente"));
    assertEquals(34, GridStateFactory.getGameId("Speed Tombstone Pente"));
    assertEquals("Tombstone Pente", GridStateFactory.getDisplayName(33));
    assertEquals("Speed Tombstone Pente", GridStateFactory.getDisplayName(34));
    assertEquals("Turn-based Tombstone Pente", GridStateFactory.getDisplayName(83));
    assertEquals(34, GridStateFactory.getSpeedGame(GridStateFactory.TOMBSTONE_GAME).getId());
    assertEquals(33, GridStateFactory.getNormalGame(GridStateFactory.SPEED_TOMBSTONE_GAME).getId());
    assertTrue(GridStateFactory.isSpeedGame(34));
    assertFalse(GridStateFactory.isSpeedGame(33));
    assertTrue(GridStateFactory.isTurnbasedGame(83));
    assertEquals(33, GridStateFactory.getNormalGameFromTurnbased(83));
    assertTrue(GridStateFactory.isValidGame(33));
    assertTrue(GridStateFactory.isValidGame(34));
    assertFalse(GridStateFactory.isValidGame(35));
    assertEquals(83, GridStateFactory.getMaxGameId());
}

public void testArrayPlacement() {
    Game[] all = GridStateFactory.getAllGames();
    assertEquals(52, all.length);
    assertSame(GridStateFactory.RENJU_GAME, all[31]);
    assertSame(GridStateFactory.SPEED_RENJU_GAME, all[32]);
    assertSame(GridStateFactory.TOMBSTONE_GAME, all[33]);
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, all[34]);
    assertSame(GridStateFactory.TB_PENTE_GAME, all[35]);
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, all[51]);
    Game[] tb = GridStateFactory.getTbGames();
    assertEquals(17, tb.length);
    assertSame(GridStateFactory.TB_RENJU_GAME, tb[15]);
    assertSame(GridStateFactory.TB_TOMBSTONE_GAME, tb[16]);
    for (Game g : tb) assertSame(g, GridStateFactory.getGame(g.getId()));   // TB index formula holds for all
    Game[] normal = GridStateFactory.getNormalGames();
    assertEquals(17, normal.length);
    assertSame(GridStateFactory.TOMBSTONE_GAME, normal[16]);
    Game[] speed = GridStateFactory.getSpeedGames();
    assertEquals(17, speed.length);
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, speed[16]);
    Game[] display = GridStateFactory.getDisplayGames();
    assertEquals(51, display.length);
    assertSame(GridStateFactory.TOMBSTONE_GAME, display[16]);
    assertEquals(83, display[33].getId());
    assertEquals("Turn-based Tombstone Pente", display[33].getName());
    assertSame(GridStateFactory.SPEED_TOMBSTONE_GAME, display[50]);
}

public void testKothAndGlobalArraysUnchanged() {
    assertEquals(32, GridStateFactory.LIVE_GAMES.length);
    assertEquals(16, GridStateFactory.TB_GAMES.length);
    assertEquals(48, GridStateFactory.ALL_GAMES.length);
    for (int g : GridStateFactory.ALL_GAMES) assertFalse("game " + g, GridStateFactory.isTombstone(g));
    assertTrue(Arrays.equals(new int[]{33, 34}, GridStateFactory.NON_KOTH_LIVE_GAMES));
    assertTrue(Arrays.equals(new int[]{83}, GridStateFactory.NON_KOTH_TB_GAMES));
}

public void testSetAndCentreRulesUnchanged() {
    for (int g : IDS) {
        assertFalse("game " + g, GridStateFactory.isSingleGameSet(g));          // D6
        assertFalse("game " + g, GridStateFactory.firstMoveCanBeOffCenter(g));
        assertEquals("game " + g, 180, GridStateFactory.getCenterMove(g));
    }
}

public void testArchiveNoNextMove() {
    for (int g : IDS) assertEquals(723, TombstoneCodes.archiveNoNextMove(g));
    for (int g : new int[]{1, 19, 31, 81}) assertEquals(361, TombstoneCodes.archiveNoNextMove(g));
}

public void testPrototypeColourIsZero() {
    assertEquals(0, GridStateFactory.getColor(0, GridStateFactory.TOMBSTONE));   // spec §5.3: prototype has no history
}
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD. Expected: `BUILD FAILED`, `cannot find symbol ... TOMBSTONE`.

- [ ] **Step 3: Implement the registration**

Exactly spec §7.1, using the names in this task's Interfaces:
- `TOMBSTONE_GAME = new Game(TOMBSTONE, "Tombstone Pente", false)`, `SPEED_TOMBSTONE_GAME = new Game(SPEED_TOMBSTONE, "Speed Tombstone Pente", true)`, `TB_TOMBSTONE_GAME = new Game(TB_TOMBSTONE, "Tombstone Pente", false)`.
- `allGames[]`: insert `TOMBSTONE_GAME, SPEED_TOMBSTONE_GAME` right after `RENJU_GAME, SPEED_RENJU_GAME` (`:148`); append `TB_TOMBSTONE_GAME` after `TB_RENJU_GAME` (`:153`).
- `displaygames[]`: `TOMBSTONE_GAME` after `RENJU_GAME` (`:171`); `new Game(TB_TOMBSTONE, "Turn-based Tombstone Pente", false)` after the Turn-based Renju entry (`:187`); `SPEED_TOMBSTONE_GAME` after `SPEED_RENJU_GAME` (`:203`).
- `normalGames[]`, `speedGames[]`, `tbGames[]`: append the matching game **last**.
- `createGridState(int game, int x, int y)`: `case TOMBSTONE: case SPEED_TOMBSTONE: case TB_TOMBSTONE: return new TombstoneState();` with a one-line comment that Tombstone is always 19x19 and ignores `x`/`y`.
- `isValidGame`: upper bound `SPEED_TOMBSTONE`. `getMaxGameId()`: `return TB_TOMBSTONE;`.
- `isTombstone(int game)`: `game == TOMBSTONE || game == SPEED_TOMBSTONE || game == TB_TOMBSTONE`.
- `NON_KOTH_LIVE_GAMES` / `NON_KOTH_TB_GAMES` next to `LIVE_GAMES`/`TB_GAMES`, with a comment: *registered at boot without King of the Hill events (spec §7.3); live ids are offered only by the Experimental server (spec §7.2); kept out of LIVE_GAMES/TB_GAMES/ALL_GAMES, which the KOTH code and the existing servers' offerings use*.
- Do **not** change `LIVE_GAMES`, `TB_GAMES`, `ALL_GAMES`, `isSingleGameSet`, `firstMoveCanBeOffCenter`.
- `TombstoneCodes.archiveNoNextMove(int game)`: `GridStateFactory.isTombstone(game) ? ARCHIVE_NO_NEXT_MOVE : 361`, with a comment naming the `pente_move.next_move` "no next move" sentinel (spec §9.1).

Update `RenjuFactoryTest.java:70` to `assertEquals(GridStateFactory.TB_TOMBSTONE, GridStateFactory.getMaxGameId());` (the max id moved; the test's Renju intent is unchanged).

- [ ] **Step 4: Run to verify it passes**

Run: BUILD, then RUN `TombstoneFactoryTest` (expected `OK (10 tests)`), RUN `RenjuFactoryTest` (`OK (7 tests)`), RUN `GridStateFactorySingleGameSetTest` (`OK (3 tests)`), RUN `MySQLGameVenueStorerTbLookupTest` (`OK (4 tests)`), RUN `TombstoneUndoReplayTest` (`OK (16 tests)`).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/GridStateFactory.java dsg_src/java/org/pente/game/TombstoneCodes.java dsg_src/java/org/pente/game/test/TombstoneFactoryTest.java dsg_src/java/org/pente/game/test/RenjuFactoryTest.java
git commit -m "Register Tombstone Pente games 33, 34 and 83"
```

---
### Task 7: PGN tokens for Tombstone games

**Files:**
- Modify: `dsg_src/java/org/pente/game/PGNGameFormat.java` — `format`: the `isRenju` flag `:213` and the token call `:219`; `parse`: the `isRenju` flag `:347` and the token call `:361`
- Test: `dsg_src/java/org/pente/game/test/PGNTombstoneTest.java` (new, modelled on `PGNRenjuPassTest`)

**Interfaces:**
- Consumes: `TombstoneCodes.pgnToken(int)`, `parsePgnToken(String)` (Task 2). `parse` sees the game name only through the `Game`/`GameType` header (`:340-347`; `data.getGame()` is not populated yet at that point).
- Produces: PGN for any game whose name contains `"tombstone"` (case-insensitive) uses `K10` / `pass` / `xK10` tokens and parses them back. `SimpleFileGameStorer` (live archive files) and `tbpgn.jsp` (TB export) get this without changes (spec §9.3). The per-entry `i % 2` numbering stays (spec §9.3: PGN numbering does not match turns).

- [ ] **Step 1: Write the failing test**

Create `PGNTombstoneTest` (JUnit 3 boilerplate; imports `java.text.ParseException`, `java.util.Date`) with:

```java
private static GameData game(String name, int... codes) {
    GameData data = new DefaultGameData();
    data.setGame(name);
    data.setDate(new Date());
    data.setTimed(false);
    data.setRated(false);
    data.getPlayer1Data().setUserIDName("alice");
    data.getPlayer2Data().setUserIDName("bob");
    data.setWinner(GameData.UNKNOWN);
    for (int c : codes) data.addMove(c);
    return data;
}

private static String format(GameData data) {
    StringBuffer buffer = new StringBuffer();
    new PGNGameFormat("\n").format(data, buffer);
    return buffer.toString();
}

private static GameData parse(String pgn) throws ParseException {
    GameData parsed = new DefaultGameData();
    new PGNGameFormat("\n").parse(parsed, new StringBuffer(pgn));
    return parsed;
}

// centre; P2 D16 + dead E15; P1 A1; P2 pass + dead A19; P1 pass
private static final int[] CODES = {180, 60, 361 + 80, 342, 722, 361, 722};

public void testFormatAndParseRoundTrip() throws ParseException {
    String pgn = format(game("Tombstone Pente", CODES));
    assertTrue(pgn, pgn.contains("xE15"));
    assertTrue(pgn, pgn.contains("xA19"));
    assertTrue(pgn, pgn.contains(" pass "));
    assertFalse("old garbage token for 722: " + pgn, pgn.contains("A-19"));
    GameData parsed = parse(pgn);
    assertEquals(CODES.length, parsed.getNumMoves());
    for (int i = 0; i < CODES.length; i++) assertEquals("entry " + i, CODES[i], parsed.getMove(i));
}

public void testSpeedTombstoneUsesTokens() throws ParseException {
    String pgn = format(game("Speed Tombstone Pente", 180, 722, 361));
    assertTrue(pgn, pgn.contains("xA19"));
    GameData parsed = parse(pgn);
    assertEquals(3, parsed.getNumMoves());
    assertEquals(722, parsed.getMove(1));
    assertEquals(361, parsed.getMove(2));
}

public void testParseAcceptsUpperCasePass() throws ParseException {
    String pgn = format(game("Tombstone Pente", CODES)).replace(" pass ", " PASS ");
    assertTrue("no pass token was written: " + pgn, pgn.contains(" PASS "));   // else the upper-case path is untested
    GameData parsed = parse(pgn);
    assertEquals(722, parsed.getMove(4));
    assertEquals(722, parsed.getMove(6));
}

public void testOtherGamesUnchanged() throws ParseException {
    String pgn = format(game("Pente", 180, 181));
    assertTrue(pgn, pgn.contains("K10 L10"));
    GameData parsed = parse(pgn);
    assertEquals(2, parsed.getNumMoves());
    assertEquals(181, parsed.getMove(1));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: BUILD, then RUN `PGNTombstoneTest`. Expected: failures — `testFormatAndParseRoundTrip` fails on `contains("xE15")` (today code 441 prints as the garbage coordinate `E-4`), `testSpeedTombstoneUsesTokens` fails on `contains("xA19")`, `testParseAcceptsUpperCasePass` fails on `no pass token was written` (today no `pass` token exists, so the `.replace` is a no-op; without that assert the test would pass, because the garbage tokens `E-4`/`A-19`/`A0` happen to parse back to 441/722/361); `testOtherGamesUnchanged` passes.

- [ ] **Step 3: Implement**

In `format`: next to `isRenju` (`:213`) add `boolean isTombstone = data.getGame() != null && data.getGame().toLowerCase().contains("tombstone");` and at `:219` use `isTombstone ? TombstoneCodes.pgnToken(move) : formatMoveToken(move, isRenju)`. In `parse`: next to `isRenju` (`:347`) derive `isTombstone` the same way from `gameHeader`, and at `:361` use `isTombstone ? TombstoneCodes.parsePgnToken(move) : parseMoveToken(move, isRenju)`. The existing `if (moveInt != -1)` guard then skips garbage tokens. No other format class changes (spec §9.3).

- [ ] **Step 4: Run to verify it passes**

Run: BUILD, then RUN `PGNTombstoneTest` (expected `OK (4 tests)`) and RUN `PGNRenjuPassTest` (`OK (2 tests)`).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/PGNGameFormat.java dsg_src/java/org/pente/game/test/PGNTombstoneTest.java
git commit -m "Write and read Tombstone moves in PGN"
```

---

### Task 8: Full Plan A regression and tidy-up

**Files:** none changed (unless a regression is found; then fix it in the task that owns the code and re-run this task).

- [ ] **Step 1: Clean build**

Run: BUILD. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run every Tombstone suite and the regression suites**

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
for t in TombstoneZobristTest TombstoneCodesTest TombstoneStateTest TombstoneUndoReplayTest TombstoneFactoryTest PGNTombstoneTest \
         RenjuFactoryTest GridStateFactorySingleGameSetTest MySQLGameVenueStorerTbLookupTest PGNRenjuPassTest RenjuStateTest \
         AllGamesTest PenteStateTest Connect6StateTest; do
  echo "== $t"; java -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner org.pente.game.test.$t 2>&1 | grep -E '^OK|Tests run'
done
```

Expected: `OK (9 tests)`, `OK (7 tests)`, `OK (23 tests)`, `OK (16 tests)`, `OK (10 tests)`, `OK (4 tests)`, `OK (7 tests)`, `OK (3 tests)`, `OK (4 tests)`, `OK (2 tests)`, `OK (37 tests)`; and **exactly** the baseline for the three pre-existing failures: `AllGamesTest` `Tests run: 78,  Failures: 8`, `PenteStateTest` `Tests run: 24,  Failures: 2`, `Connect6StateTest` `Tests run: 9,  Failures: 3`. Any other count is a regression from this plan.

- [ ] **Step 3: Run the Ant suite that touches the game core through the TB storer**

Run: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`. Expected: `[java] OK (14 tests)` and `BUILD SUCCESSFUL` (baseline verified 2026-10-27; it builds `TBGame`s through `GridStateFactory` and so catches a broken static initialiser).

- [ ] **Step 4: Check the workspace**

Run: `git status --porcelain | grep '^??'`. Expected: only paths that were untracked before this plan started (`CLAUDE.md` and any sibling Tombstone plan files under `docs/superpowers/plans/`), nothing else. Remove the scratch generator only if it ended up in the repo (it should be in the scratchpad). `deploy/` and `deployClasses/` are git-ignored build output.

No commit in this task.

## Hand-off to Plans B and C

- Use `GridStateFactory.isTombstone(id)` for every game branch; never compare against 33/34/83 literals.
- `TombstoneState` result fields (`isGameOver`, `getWinner`) are only authoritative on a live state that saw every entry; for replayed archives use the stored winner (spec §5.4).
- `TombstoneState.getPosition(int)` throws for codes `>= 361` (inherited); decode with `TombstoneCodes.pointOf` first.
- The live server must call `skipDeadStone()` for a skip, and `undoTurn()` (not `undoMove()`) for an accepted undo.
