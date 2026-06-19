# Renju Archival Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist a completed Renju game's opening record (`renju_swaps` packed word + the 10 `renju_offers`) through archival into `pente_game`/`pente_renju_offer`, load it back into `GameData`, and expose it in the JSON endpoint — closing the confirmed offer-loss gap.

**Architecture:** Add nullable `renjuSwaps` (`Integer`) + `renjuOffers` (`int[]`) to `GameData` (interface default methods, real fields in `DefaultGameData`). Both archival builders (`CacheTBStorer.storeGameDSG`/`TBGame.convertToGameData` for TB; `ServerTable.getGameData` for live) set them when the game is Renju. `MySQLPenteGameStorer.storeGame`/`loadGame` write/read `pente_game.renju_swaps` + `pente_renju_offer`. `GameResponse.buildHistoric` emits them. Preserve-only (no viewer rendering).

**Tech Stack:** Java (Tomcat backend), JUnit 3, Ant, MySQL.

**Spec:** `docs/superpowers/specs/2026-06-15-renju-archival-persistence-design.md`

## Build & Test Commands
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
ant test-one -Dtest=org.pente.game.test.<TestClass>
```
Run from `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`.

**Testing reality:** `RenjuState.getRenjuSwapsPacked()` is pure and unit-tested here. `GameData`/`DefaultGameData` accessors are trivial (compile). The storer write/read, the archival builders, and the JSON `buildHistoric` need a live MySQL / aren't unit-tested in this repo (it excludes DB-coupled storer tests) — they're **compile-verified**; a manual DB round-trip is documented in the final task. No fabricated DB tests.

## File Structure
- Modify: `dsg_src/java/org/pente/game/GameData.java` (interface — default methods)
- Modify: `dsg_src/java/org/pente/game/DefaultGameData.java` (fields + overrides)
- Modify: `dsg_src/java/org/pente/game/RenjuState.java` (`getRenjuSwapsPacked` + `swapResolved[]`)
- Test: `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java` (add a `getRenjuSwapsPacked` test)
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java` (`convertToGameData`)
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` (`storeGameDSG`)
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java` (`getGameData`)
- Modify: `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java` (`storeGame` + `loadGame`)
- Modify: `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java` (`buildHistoric`)

---

## Task 1: GameData renju accessors (interface defaults + DefaultGameData)

**Files:**
- Modify: `dsg_src/java/org/pente/game/GameData.java`
- Modify: `dsg_src/java/org/pente/game/DefaultGameData.java`

- [ ] **Step 1: Add interface default methods**

In `GameData.java`, after the `setSwap2Pass(boolean swap2pass);` declaration (line 233), add:

```java

    /** Renju opening state (null/none for non-Renju games). Default methods so
     *  non-DefaultGameData implementors keep compiling. */
    default Integer getRenjuSwaps() { return null; }

    default void setRenjuSwaps(Integer renjuSwaps) { }

    default int[] getRenjuOffers() { return null; }

    default void setRenjuOffers(int[] renjuOffers) { }
```

- [ ] **Step 2: Add fields + overrides in DefaultGameData**

In `DefaultGameData.java`, after the `swap2Pass` field (line 87), add:

```java

    /** Renju packed opening word (RenjuOpeningState), null for non-Renju */
    protected Integer renjuSwaps;

    /** Renju Branch-B offered 5th moves, null for none */
    protected int[] renjuOffers;
```

After the `setSwap2Pass(...)` method (line 372), add:

```java

    @Override
    public Integer getRenjuSwaps() {
        return renjuSwaps;
    }

    @Override
    public void setRenjuSwaps(Integer renjuSwaps) {
        this.renjuSwaps = renjuSwaps;
    }

    @Override
    public int[] getRenjuOffers() {
        return renjuOffers;
    }

    @Override
    public void setRenjuOffers(int[] renjuOffers) {
        this.renjuOffers = renjuOffers;
    }
```

- [ ] **Step 3: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/game/GameData.java dsg_src/java/org/pente/game/DefaultGameData.java
git commit -m "feat(renju): GameData renjuSwaps/renjuOffers accessors

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: RenjuState.getRenjuSwapsPacked()

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java`

- [ ] **Step 1: Write the failing test**

In `RenjuReconstructTest.java`, add this method (it drives the engine's opening hooks and asserts the packed word decodes correctly):

```java
    public void testGetRenjuSwapsPackedEncodesResolvedDecisions() {
        // Branch A opening: decline swaps 1-4, branch A, decline swap 5.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));  s.renjuSwapDecisionMade(false);   // swap1 = NO
        s.addMove(xy(8, 8));  s.renjuSwapDecisionMade(false);   // swap2 = NO
        s.addMove(xy(9, 7));  s.renjuSwapDecisionMade(false);   // swap3 = NO
        s.addMove(xy(6, 8));  s.renjuSwapDecisionMade(false);   // swap4 = NO
        s.chooseBranch(false);                                  // Branch A
        s.addMove(xy(11, 7)); s.renjuSwapDecisionMade(true);    // swap5 = YES

        RenjuOpeningState st = RenjuOpeningState.decode(s.getRenjuSwapsPacked());
        assertEquals(RenjuOpeningState.NO,  st.swap1);
        assertEquals(RenjuOpeningState.NO,  st.swap2);
        assertEquals(RenjuOpeningState.NO,  st.swap3);
        assertEquals(RenjuOpeningState.NO,  st.swap4);
        assertEquals(RenjuOpeningState.NO,  st.branch);   // Branch A -> NO
        assertEquals(RenjuOpeningState.YES, st.swap5);
    }

    public void testGetRenjuSwapsPackedLeavesUnresolvedPending() {
        // Only move 1 placed, swap1 not yet decided -> all PENDING.
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));
        RenjuOpeningState st = RenjuOpeningState.decode(s.getRenjuSwapsPacked());
        assertEquals(RenjuOpeningState.PENDING, st.swap1);
        assertEquals(RenjuOpeningState.PENDING, st.branch);
    }
```

(`xy(...)`, the helper, already exists in `RenjuReconstructTest`.)

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: FAIL — `getRenjuSwapsPacked` not defined.

- [ ] **Step 3: Add `swapResolved[]` + `getRenjuSwapsPacked()`**

In `RenjuState.java`, next to the `swapDecision` field (line 341 `private final boolean[] swapDecision = new boolean[6];`), add:

```java
    // parallels swapDecision: true once the window's decision has been recorded
    // (so a false decision is distinguishable from "not yet decided").
    private final boolean[] swapResolved = new boolean[6];
```

In `renjuSwapDecisionMade(boolean swap)` (line 351), after `swapDecision[gridState.getNumMoves()] = swap;` add:

```java
        swapResolved[gridState.getNumMoves()] = true;
```

In `clear()` (find the existing opening-state reset — it resets `openingComplete`, `awaitingSwap`, `branchChosen`, `tenOffer`, and zeroes `swapDecision`), add a reset for the new array alongside the `swapDecision` reset:

```java
        java.util.Arrays.fill(swapResolved, false);
```

Add the encoder method (place it after `getOfferedFifthMoves()`, ~line 272):

```java
    /**
     * Encode the engine's resolved opening decisions into the RenjuOpeningState
     * packed word (base-3). Unresolved windows encode as PENDING, so this is
     * correct mid-opening (used live) and at game-over (used for archival).
     */
    public int getRenjuSwapsPacked() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = swapDigit(1);
        st.swap2 = swapDigit(2);
        st.swap3 = swapDigit(3);
        st.swap4 = swapDigit(4);
        st.branch = branchChosen
                ? (tenOffer ? RenjuOpeningState.YES : RenjuOpeningState.NO)
                : RenjuOpeningState.PENDING;
        st.swap5 = swapDigit(5);
        return st.encode();
    }

    private int swapDigit(int window) {
        if (!swapResolved[window]) {
            return RenjuOpeningState.PENDING;
        }
        return swapDecision[window] ? RenjuOpeningState.YES : RenjuOpeningState.NO;
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: PASS (existing tests + the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java dsg_src/java/org/pente/game/test/RenjuReconstructTest.java
git commit -m "feat(renju): RenjuState.getRenjuSwapsPacked encodes opening decisions

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Archival builders carry the Renju fields

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

Compile-verified.

- [ ] **Step 1: TB cache archival (`storeGameDSG`)**

In `CacheTBStorer.java` `storeGameDSG`, after the swap2pass block (line 885-888) and before the move loop (line 890), add:

```java
                if (game.getGame() == GridStateFactory.TB_RENJU) {
                    gameData.setRenjuSwaps(game.getRenjuSwaps());
                    gameData.setRenjuOffers(game.getRenjuOffers());
                }
```

- [ ] **Step 2: TB `convertToGameData`**

In `TBGame.java` `convertToGameData`, after the swap2Pass block (line 700-702) and before the move loop (line 704), add:

```java
        if (getGame() == GridStateFactory.TB_RENJU) {
            gameData.setRenjuSwaps(renjuSwaps);
            gameData.setRenjuOffers(renjuOffers);
        }
```

(`renjuSwaps`/`renjuOffers` are existing `TBGame` fields.)

- [ ] **Step 3: Live archival (`ServerTable.getGameData`)**

In `ServerTable.java` `getGameData`, after the swap2 block (line 2860-2862) and before `gameData.setStatus(status);` (line 2864), add:

```java
            if (gridState instanceof RenjuState) {
                RenjuState rs = (RenjuState) gridState;
                gameData.setRenjuSwaps(rs.getRenjuSwapsPacked());
                java.util.List<Integer> offers = rs.getOfferedFifthMoves();
                if (offers != null && !offers.isEmpty()) {
                    int[] arr = new int[offers.size()];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = offers.get(i);
                    }
                    gameData.setRenjuOffers(arr);
                }
            }
```

Add the import if not present: `import org.pente.game.RenjuState;` (check the top of `ServerTable.java`; `GridStateFactory` and `GameData` are already imported).

- [ ] **Step 4: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java \
        dsg_src/java/org/pente/turnBased/TBGame.java \
        dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): carry renju_swaps/renju_offers into GameData at archival

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: MySQLPenteGameStorer.storeGame — write the columns + offer rows

**Files:**
- Modify: `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java`

- [ ] **Step 1: Add renju_swaps to the pente_game INSERT**

Replace the INSERT statement (lines 344-349):

```java
                stmt = con.prepareStatement("insert into " + GAME_TABLE + " " +
                        "(site_id, event_id, round, section, play_date, timer, rated, " +
                        " initial_time, incremental_time, player1_pid, player2_pid, " +
                        " player1_rating, player2_rating, player1_type, player2_type, " +
                        " winner, gid, game, swapped, private, status, swap2pass) " +
                        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
```

with:

```java
                stmt = con.prepareStatement("insert into " + GAME_TABLE + " " +
                        "(site_id, event_id, round, section, play_date, timer, rated, " +
                        " initial_time, incremental_time, player1_pid, player2_pid, " +
                        " player1_rating, player2_rating, player1_type, player2_type, " +
                        " winner, gid, game, swapped, private, status, swap2pass, renju_swaps) " +
                        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
```

After the `stmt.setInt(22, data.didSwap2Pass() ? 1 : 0);` bind (line 403), add:

```java
                if (data.getRenjuSwaps() != null) {
                    stmt.setInt(23, data.getRenjuSwaps());
                } else {
                    stmt.setNull(23, java.sql.Types.SMALLINT);
                }
```

- [ ] **Step 2: Insert pente_renju_offer rows**

After the `pente_move` insert loop in `storeGame` (the `for` loop ending at line 499 with its `stmt.executeUpdate();`), and before that statement is closed, add a separate block (use the same `con`; `siteData.getSiteID()` and `data.getGameID()` are in scope from the game-row insert):

```java
                if (data.getRenjuOffers() != null) {
                    PreparedStatement offerStmt = null;
                    try {
                        offerStmt = con.prepareStatement("insert into pente_renju_offer " +
                                "(gid, site_id, offer_num, move) values(?, ?, ?, ?)");
                        int[] offers = data.getRenjuOffers();
                        for (int i = 0; i < offers.length; i++) {
                            offerStmt.setLong(1, data.getGameID());
                            offerStmt.setInt(2, siteData.getSiteID());
                            offerStmt.setInt(3, i);
                            offerStmt.setInt(4, offers[i]);
                            offerStmt.executeUpdate();
                        }
                    } finally {
                        if (offerStmt != null) {
                            offerStmt.close();
                        }
                    }
                }
```

> If `siteData`/the move-`stmt` are scoped such that this block can't see them, place it immediately after the game-row insert (where `siteData` and the game `stmt` are in scope) instead — the offers don't depend on the move rows.

- [ ] **Step 3: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/game/MySQLPenteGameStorer.java
git commit -m "feat(renju): write renju_swaps + pente_renju_offer in storeGame

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: MySQLPenteGameStorer.loadGame — read the columns + offer rows

**Files:**
- Modify: `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java`

- [ ] **Step 1: Add renju_swaps to the loadGame SELECT**

Replace the SELECT (lines 652-657):

```java
            gameStmt = con.prepareStatement(
                    "select site_id, event_id, round, section, play_date, timer, " +
                            "rated, initial_time, incremental_time, player1_pid, " +
                            "player2_pid, player1_rating, player2_rating, winner, game, swapped, private, status, swap2pass " +
                            "from " + GAME_TABLE + " " +
                            "where gid = ?");
```

with (append `, renju_swaps` as column 20):

```java
            gameStmt = con.prepareStatement(
                    "select site_id, event_id, round, section, play_date, timer, " +
                            "rated, initial_time, incremental_time, player1_pid, " +
                            "player2_pid, player1_rating, player2_rating, winner, game, swapped, private, status, swap2pass, renju_swaps " +
                            "from " + GAME_TABLE + " " +
                            "where gid = ?");
```

After `gameData.setSwap2Pass(gameResult.getInt(19) == 1);` (line 729), add:

```java
                int renjuSwaps = gameResult.getInt(20);
                if (!gameResult.wasNull()) {
                    gameData.setRenjuSwaps(renjuSwaps);
                }
```

- [ ] **Step 2: Load pente_renju_offer rows**

After the move-loading `while` loop (lines 754-756, `gameData.addMove(...)`), add (mirrors the move-load pattern; reuse a new statement; `gameID` is the load param):

```java
                PreparedStatement offerStmt = null;
                ResultSet offerResult = null;
                try {
                    offerStmt = con.prepareStatement("select move from pente_renju_offer " +
                            "where gid = ? order by offer_num");
                    offerStmt.setLong(1, gameID);
                    offerResult = offerStmt.executeQuery();
                    java.util.List<Integer> offers = new java.util.ArrayList<Integer>();
                    while (offerResult.next()) {
                        offers.add(offerResult.getInt(1));
                    }
                    if (!offers.isEmpty()) {
                        int[] arr = new int[offers.size()];
                        for (int i = 0; i < arr.length; i++) {
                            arr[i] = offers.get(i);
                        }
                        gameData.setRenjuOffers(arr);
                    }
                } finally {
                    if (offerResult != null) {
                        offerResult.close();
                    }
                    if (offerStmt != null) {
                        offerStmt.close();
                    }
                }
```

> Confirm the load param variable name during implementation (it's the `gid` passed to `loadGame`; the move query at line 742-746 uses it — reuse the exact same expression).

- [ ] **Step 3: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/game/MySQLPenteGameStorer.java
git commit -m "feat(renju): read renju_swaps + pente_renju_offer in loadGame

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: GameResponse.buildHistoric — emit the renju fields in JSON

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java`

- [ ] **Step 1: Build the offers string + pass the renju fields**

In `buildHistoric` (lines 205-230), before the `return new GameResponse(` add:

```java
        String historicRenjuOffers = null;
        if (game.getRenjuOffers() != null) {
            StringBuilder ro = new StringBuilder();
            int[] offers = game.getRenjuOffers();
            for (int i = 0; i < offers.length; i++) {
                if (i > 0) ro.append(',');
                ro.append(offers[i]);
            }
            historicRenjuOffers = ro.toString();
        }
```

Then replace the final three constructor args — the last line currently reads `null, null, null` (the `renjuPhase, renjuOffers, renjuSwaps` trio at line 228):

```java
                null, null, null
```

with:

```java
                null, historicRenjuOffers, game.getRenjuSwaps()
```

(`renjuPhase` stays `null` — a completed game has no pending phase; `game.getRenjuSwaps()` is the nullable `Integer`.)

- [ ] **Step 2: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/mobile/GameResponse.java
git commit -m "feat(renju): expose renju_swaps/renju_offers for historic games in JSON

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Full regression + manual round-trip note

**Files:** none (verification) + spec status

- [ ] **Step 1: Clean rebuild + Renju unit suites**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile \
  && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuFactoryTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest \
  && ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest
```
Expected: all PASS.

- [ ] **Step 2: Document the manual DB round-trip (not automatable here)**

This sequence can't be unit-tested (DB-coupled). Record it in the commit/PR for manual QA: complete a Branch-B TB Renju game → confirm `select * from pente_renju_offer where gid=<gid>` returns 10 rows and `pente_game.renju_swaps` is set for that gid → load it in a viewer / the JSON endpoint → confirm `renjuOffers`/`renjuSwaps` are present.

- [ ] **Step 3: Update spec status + guide**

In `docs/superpowers/specs/2026-06-15-renju-archival-persistence-design.md` change `Status: approved-pending-review` → `Status: implemented (manual DB round-trip pending)`. In `docs/renju-integration-guide.md` §7, move the "`pente_game` opening-state write/read" bullet from "Deferred" to done (archival path), leaving the live `ServerTable` opening routing as the remaining live item.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-15-renju-archival-persistence-design.md docs/renju-integration-guide.md
git commit -m "docs(renju): mark archival persistence implemented

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- `GameData`/`DefaultGameData` nullable `renjuSwaps` (Integer) + `renjuOffers` (int[]) → Task 1. ✓
- `RenjuState.getRenjuSwapsPacked()` (+ `getOfferedFifthMoves`) → Task 2 (with `swapResolved[]` for PENDING-correctness). ✓
- Archival builders carry the fields (TB `storeGameDSG` + `convertToGameData`; live `getGameData`) → Task 3. ✓
- `MySQLPenteGameStorer.storeGame` writes `renju_swaps` + `pente_renju_offer` → Task 4. ✓
- `MySQLPenteGameStorer.loadGame` reads them → Task 5. ✓
- `GameResponse.buildHistoric` emits them → Task 6. ✓
- Backward compatible (nullable / no rows) → Task 1 (Integer null) + Task 4/5 (null/empty handling). ✓
- Preserve-only (no viewer rendering) → not in scope; not implemented. ✓

**Placeholder scan:** none — every step has full code or exact commands. The two "confirm during implementation" notes (offer-insert scoping in Task 4; load-param name in Task 5) are pinpointed to specific existing lines, not vague.

**Type consistency:** `getRenjuSwaps()`/`setRenjuSwaps(Integer)` (nullable `Integer`), `getRenjuOffers()`/`setRenjuOffers(int[])`, `RenjuState.getRenjuSwapsPacked()` (int), `getOfferedFifthMoves()` (`List<Integer>`), `RenjuOpeningState.PENDING/NO/YES` + `decode`/`encode` — consistent across tasks and with the existing `GameResponse(Integer renjuSwaps)` constructor arg.
