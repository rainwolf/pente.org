# Renju Tourney Tie Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A drawn turn-based tournament game (Renju pass-pass / agreed draw / timeout-draw) records `RESULT_TIE` and triggers the existing colour-swapped replay machinery instead of stalling the round forever as `RESULT_UNFINISHED`.

**Architecture:** Two surgical fixes plus tests. (1) `CacheTBStorer.storeGameDSG`'s tourney branch maps a drawn game to `TourneyMatch.RESULT_TIE` (today it writes raw `getWinner()==0`, which collides with `RESULT_UNFINISHED`). The mapping is extracted into a small static helper so it is testable without executing the whole god-method. (2) `CacheTourneyStorer.applyMatchTo`'s tie branch calls the existing-but-never-called `createMoreMatchesAfterTie(original, true)` overload for single-game-set games (TB_RENJU=81) so the one-game replay swaps seats. Everything downstream already works: `CacheTourneyStorer.insertMatch` auto-creates the TB set for any inserted match (`:483-497`), `hasBeenPlayed()` is `result != 0` so a tie row counts as played, and `SingleEliminationSection.init` folds the tie row + replay row into one aggregate that resolves when the replay is decisive.

**Tech Stack:** Java (Tomcat webapp), JUnit 3 (`junit.framework.TestCase`), Ant. No Redis/MariaDB needed — tests use `SerializingRedisConnectionManager` + `InMemory*Storer` fixtures.

## Global Constraints

- Branch: work on `fix/renju-tourney-tie` (already created). NEVER commit the pre-existing unrelated dirty files (`Dockerfile`, `docker-compose.yml`, `docker-compose-replica.yml`, untracked repro/png files). `git add` only files you created/modified for your task.
- Build: `./justCompile` (compiles main source). Tests: `ant compile-tests` then `ant test-one -Dtest=<fully.qualified.ClassName>` (see `build.xml` targets `compile-tests` ~:27-38, `test-one` ~:88). Verify target names in `build.xml` before first use.
- JUnit 3 style: extend `TestCase`, constructor `public X(String name) { super(name); }`, `setUp`/`tearDown` overrides — copy the shape of `dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java`.
- Key ids: `GridStateFactory.TB_RENJU == 81`; `GridStateFactory.isSingleGameSet(81) == true`. `TourneyMatch` results: `RESULT_UNFINISHED=0, RESULT_P1_WINS=1, RESULT_P2_WINS=2, RESULT_DBL_FORFEIT=3, RESULT_TIE=4` (`TourneyMatch.java:25-29`).
- Scope guard: do NOT touch the seeding SQL bug, statusRoundRobin.jsp NPE, Swiss/RR half-point scoring, or anything else from the audit (`docs/superpowers/research/2026-08-08-renju-tb-tournament-readiness.md`). Tie mapping + single-game replay swap only.

---

### Task 1: Draw → RESULT_TIE mapping in CacheTBStorer

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` (tourney branch ~:1250-1263; add one static helper)
- Create: `dsg_src/java/org/pente/turnBased/test/TourneyTieMappingTest.java`

**Interfaces:**
- Consumes: `TBGame` (`org.pente.turnBased.TBGame`) — `isDraw()`, `getWinner()`, `seatsSwapped()`, `setWinner(int)`, `setState(int)`; `TourneyMatch.RESULT_*` constants.
- Produces: `public static int tourneyResult(TBGame game)` on `CacheTBStorer` — later tasks and the production branch both use it. Returns `RESULT_TIE` for a drawn game, else the (seat-swap-corrected) winner 1 or 2.

Current production code at `CacheTBStorer.java:1250-1263` (verify before editing):

```java
} else if (game.getEventId() != getEventId(game.getGame())) {
    TourneyMatch tourneyMatch = tourneyStorer.getUnplayedMatch(
            game.getOriginalPlayer1Pid(), game.getOriginalPlayer2Pid(), game.getEventId());
    if (tourneyMatch != null) {
        tourneyMatch.setGid(game.getGid());
        int winner = game.getWinner();
        if (game.seatsSwapped() && winner != 0) { // != 0: not a draw
            winner = 3 - winner;
        }
        tourneyMatch.setResult(winner);
        tourneyStorer.updateMatch(tourneyMatch);
        // return;
    }
}
```

- [ ] **Step 1: Write the failing test**

First read `dsg_src/java/org/pente/turnBased/test/TBGamePassDrawTest.java` to learn how that test puts a `TBGame` into a drawn state (state constant + `setWinner(0)` order); mirror exactly that construction. Then create `TourneyTieMappingTest`:

```java
package org.pente.turnBased.test;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBGame;

/**
 * A drawn TB tourney game must map to RESULT_TIE (4), never to raw
 * getWinner()==0, which collides with RESULT_UNFINISHED and stalls the
 * round forever (hasBeenPlayed() is result != 0).
 */
public class TourneyTieMappingTest extends TestCase {

    public TourneyTieMappingTest(String name) {
        super(name);
    }

    private TBGame completedGame(int winner, boolean swapped) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_RENJU);
        // Use the same state/setWinner sequence TBGamePassDrawTest uses for a
        // completed game; setWinner(0) on a completed game marks a draw.
        g.setState(TBGame.STATE_COMPLETED);
        if (swapped) {
            g.swapSeats(); // use the real seat-swap API found in TBGameSeatsSwappedTest
        }
        g.setWinner(winner);
        return g;
    }

    public void testDrawMapsToResultTie() {
        TBGame g = completedGame(0, false);
        assertTrue(g.isDraw());
        assertEquals(TourneyMatch.RESULT_TIE, CacheTBStorer.tourneyResult(g));
    }

    public void testDrawWithSwappedSeatsStillTie() {
        TBGame g = completedGame(0, true);
        assertEquals(TourneyMatch.RESULT_TIE, CacheTBStorer.tourneyResult(g));
    }

    public void testP1WinUnswapped() {
        assertEquals(TourneyMatch.RESULT_P1_WINS,
                CacheTBStorer.tourneyResult(completedGame(1, false)));
    }

    public void testP1WinSwappedFlipsToP2() {
        assertEquals(TourneyMatch.RESULT_P2_WINS,
                CacheTBStorer.tourneyResult(completedGame(1, true)));
    }
}
```

Adapt the two marked lines to reality: the exact completed-state constant (`TBGame.STATE_COMPLETED` vs another name — check `TBGame.java` ~:479-488 where `setWinner` sets the draw flag for `STATE_COMPLETED`/`STATE_COMPLETED_TO`) and the real seat-swap method name (find it in `TBGameSeatsSwappedTest.java` — do not invent one). If `swapSeats()` requires player pids set first, set `setPlayer1Pid(1001L)/setPlayer2Pid(1002L)` before swapping.

- [ ] **Step 2: Run test to verify it fails**

Run: `ant compile-tests` — expected: compile error `cannot find symbol: method tourneyResult` (that is the failure for a not-yet-written static method; note it in the task report).

- [ ] **Step 3: Write minimal implementation**

In `CacheTBStorer.java`, add next to `storeGameDSG` (package-visible static, javadoc included):

```java
/**
 * Maps a finished TB game to a TourneyMatch result code. A draw must become
 * RESULT_TIE — raw getWinner() is 0 for draws, which collides with
 * RESULT_UNFINISHED and would leave the match permanently "unplayed".
 * Winners are flipped when the game's seats were swapped, because tourney
 * matches are stored in original-seat orientation.
 */
static int tourneyResult(TBGame game) {
    if (game.isDraw()) {
        return TourneyMatch.RESULT_TIE;
    }
    int winner = game.getWinner();
    if (game.seatsSwapped() && winner != 0) {
        winner = 3 - winner;
    }
    return winner;
}
```

Then replace the inline block in the tourney branch (`:1255-1259`) so it reads:

```java
    if (tourneyMatch != null) {
        tourneyMatch.setGid(game.getGid());
        tourneyMatch.setResult(tourneyResult(game));
        tourneyStorer.updateMatch(tourneyMatch);
        // return;
    }
```

Note: `tourneyResult` must be `public static` if the test package (`org.pente.turnBased.test`) differs from `org.pente.turnBased` — it does, so make it `public static`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.TourneyTieMappingTest`
Expected: all 4 tests PASS. Also run `ant test-one -Dtest=org.pente.turnBased.test.TBGamePassDrawTest` (must stay green).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/test/TourneyTieMappingTest.java
git commit -m "fix(tourney): map drawn TB games to RESULT_TIE, not RESULT_UNFINISHED"
```

---

### Task 2: Single-game replay swaps seats (createMoreMatchesAfterTie single=true)

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java` (~:544-556, the `RESULT_TIE` branch inside `applyMatchTo`)
- Possibly modify: `dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java` (only if step 1 reading shows it is needed — see Step 3)
- Create: `dsg_src/java/org/pente/gameServer/tourney/test/TieReplayMatchTest.java`

**Interfaces:**
- Consumes: `SingleEliminationFormat.createMoreMatchesAfterTie(TourneyMatch, boolean single)` (`SingleEliminationFormat.java:170-195`) — with `single=true` returns a 1-element-used array whose `more[0]` has player1/player2 SWAPPED relative to the original and `seq = original.getSeq()+1`; `GridStateFactory.isSingleGameSet(int game)`.
- Produces: the tie branch in `applyMatchTo` passes `single=true` for single-game-set games. No new names for later tasks.

Current production code at `CacheTourneyStorer.java` (inside `applyMatchTo`, verify exact lines ~:544-556):

```java
if (m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound()) {
    TourneyMatch more[] = f.createMoreMatchesAfterTie(tourneyMatch);
    insertMatch(more[0]);
    s.addMatch(more[0]);
    if (!GridStateFactory.isSingleGameSet(t.getGame())) {
        insertMatch(more[1]);
        s.addMatch(more[1]);
    }
}
```

- [ ] **Step 1: Read the surrounding code**

Read `CacheTourneyStorer.applyMatchTo` in full (~:521-570): establish the declared type of `f` and whether the tie branch is guarded by `instanceof SingleEliminationFormat`. Read `DoubleEliminationFormat.java:325-345` — its own 1-arg `createMoreMatchesAfterTie` override. Record: does `DoubleEliminationFormat` extend `SingleEliminationFormat`, and does its 1-arg override do anything beyond delegating (e.g. loser-bracket handling)?

- [ ] **Step 2: Write the failing test**

```java
package org.pente.gameServer.tourney.test;

import junit.framework.TestCase;

import org.pente.gameServer.tourney.SingleEliminationFormat;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;

/**
 * Single-game-set games (e.g. TB Renju, 81) replay a tie as ONE new match
 * with seats swapped — the single=true overload. The 1-arg call produces an
 * UNSWAPPED more[0], so calling it for single-game sets repeats colours.
 */
public class TieReplayMatchTest extends TestCase {

    public TieReplayMatchTest(String name) {
        super(name);
    }

    private TourneyMatch tiedMatch(TourneyPlayerData p1, TourneyPlayerData p2) {
        TourneyMatch m = new TourneyMatch();
        m.setEvent(5000);
        m.setRound(1);
        m.setSection(1);
        m.setSeq(1);
        m.setPlayer1(p1);
        m.setPlayer2(p2);
        m.setResult(TourneyMatch.RESULT_TIE);
        return m;
    }

    public void testSingleReplaySwapsSeatsAndBumpsSeq() {
        // Construct TourneyPlayerData the same way InMemoryTourneyStorer /
        // CacheTourneyStorerRedisTest do — copy their construction verbatim.
        TourneyPlayerData p1 = playerWithPid(1001L);
        TourneyPlayerData p2 = playerWithPid(1002L);
        TourneyMatch original = tiedMatch(p1, p2);

        TourneyMatch[] more = new SingleEliminationFormat()
                .createMoreMatchesAfterTie(original, true);

        assertEquals(1002L, more[0].getPlayer1().getPlayerID());
        assertEquals(1001L, more[0].getPlayer2().getPlayerID());
        assertEquals(original.getSeq() + 1, more[0].getSeq());
        assertEquals(TourneyMatch.RESULT_UNFINISHED, more[0].getResult());
        assertEquals(original.getEvent(), more[0].getEvent());
        assertEquals(original.getRound(), more[0].getRound());
        assertNull(more[1]);
    }
}
```

Fill in `playerWithPid` from real `TourneyPlayerData` construction (check its constructors/setters; `CacheTourneyStorerRedisTest.java` and `InMemoryTourneyStorer.java` show working examples). If `SingleEliminationFormat`'s constructor needs arguments, copy a working construction from `SingleEliminationFormat` usages in tests or `CacheTourneyStorer`.

This test passes already IF the overload behaves as documented — that is fine and expected (it pins behaviour). The failing part of this task is the call-site test below; if writing a call-site-level test against `CacheTourneyStorer.applyMatchTo` requires excessive wiring (it is `private`/complex), the overload-pinning test plus a source-level assertion in review is acceptable — in that case still make the Step 4 production change and state clearly in the task report that the call-site change is covered by Task 3's aggregate test plus review, not by a dedicated unit test.

- [ ] **Step 3: Run the new test**

Run: `ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.TieReplayMatchTest`
Expected: PASS (behaviour-pinning). Note the result.

- [ ] **Step 4: Production change**

In `CacheTourneyStorer.applyMatchTo`, change the tie branch to:

```java
if (m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound()) {
    boolean single = GridStateFactory.isSingleGameSet(t.getGame());
    TourneyMatch more[] = single
            ? f.createMoreMatchesAfterTie(tourneyMatch, true)
            : f.createMoreMatchesAfterTie(tourneyMatch);
    insertMatch(more[0]);
    s.addMatch(more[0]);
    if (!single) {
        insertMatch(more[1]);
        s.addMatch(more[1]);
    }
}
```

Adapt to the declared type of `f` found in Step 1: if `f` is typed such that the 2-arg overload is not visible, cast where the branch is already guarded by `instanceof SingleEliminationFormat`. If `DoubleEliminationFormat`'s 1-arg override contains extra logic (not a pure delegate), add a matching 2-arg override there that preserves that logic for the single case — do NOT let the 2-arg call silently bypass double-elimination-specific behaviour. If its override IS a pure delegate or double-elim can never reach this branch, note that in the task report instead.

- [ ] **Step 5: Compile + full relevant tests**

Run: `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.TieReplayMatchTest && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java dsg_src/java/org/pente/gameServer/tourney/test/TieReplayMatchTest.java
# plus DoubleEliminationFormat.java if modified
git commit -m "fix(tourney): single-game tie replay swaps seats via single=true overload"
```

---

### Task 3: Aggregate resolution test — tie row + decisive replay ends the round

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/tourney/test/TieAggregateResolutionTest.java`

**Interfaces:**
- Consumes: `Task 1`'s semantics (a drawn game's match row holds `RESULT_TIE`), `SingleEliminationSection` (init folds same-pair rows; `:96-172`), `TourneyMatch.hasBeenPlayed()`, `SingleEliminationMatch.isComplete()/updateResult()` (`SingleEliminationMatch.java:75-96`), `SingleEliminationSection.getWinners()` (`:177-192`).
- Produces: nothing new — proves the end state: tie row counts as played, aggregate stays open until the replay is decisive, then exactly one winner advances.

- [ ] **Step 1: Write the failing/proving test**

Read `SingleEliminationSection.java` and `SingleEliminationFormat.createRound`/section construction first, plus `InMemoryTourneyStorer`, to learn how a section with matches is built in-memory (`CacheTourneyStorerRedisTest.java` has a working tourney construction — copy its setup). Then:

```java
package org.pente.gameServer.tourney.test;

import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.tourney.SingleEliminationSection;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;

/**
 * End-state proof for the Renju tourney tie fix: a RESULT_TIE row plus its
 * replay row fold into one aggregate that (a) is incomplete while the replay
 * is unplayed — the round stays open, no stall-as-unplayed — and (b) yields
 * exactly one winner once the replay is decisive.
 */
public class TieAggregateResolutionTest extends TestCase {

    public TieAggregateResolutionTest(String name) {
        super(name);
    }

    public void testTieRowCountsAsPlayed() {
        TourneyMatch tied = matchWith(TourneyMatch.RESULT_TIE);
        assertTrue(tied.hasBeenPlayed());
    }

    public void testAggregateOpenWhileReplayUnplayed_thenResolvesOnReplayWin() {
        // Build a SingleEliminationSection containing, for the same pair
        // (pids 1001/1002): row1 seq=1 RESULT_TIE, row2 seq=2 (seats swapped)
        // RESULT_UNFINISHED. Construct via the section/format APIs the way
        // CacheTourneyStorerRedisTest builds its tourney; then call init().
        SingleEliminationSection section = buildSectionWithTieAndReplay();

        assertFalse(section.isComplete());

        // Replay decided: swapped-orientation row, player1(=pid 1002) wins.
        decideReplay(section, TourneyMatch.RESULT_P1_WINS);
        section.init();

        assertTrue(section.isComplete());
        List winners = section.getWinners();
        assertEquals(1, winners.size());
        assertEquals(1002L, ((TourneyPlayerData) winners.get(0)).getPlayerID());
    }
}
```

The helpers `matchWith`, `buildSectionWithTieAndReplay`, `decideReplay` must be written against the real constructors found in Step 1 reading — keep them small and literal (build `TourneyMatch` rows exactly like Task 2's test, wrap them in the section the way production `createFirstRound`/`TourneyRound` does). If `SingleEliminationSection` cannot be constructed standalone (e.g. requires a `TourneyRound`/format), build the smallest real object graph that production uses — do not mock.

- [ ] **Step 2: Run it**

Run: `ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.TieAggregateResolutionTest`
Expected: PASS. If `isComplete()` is on the round/format rather than section, adjust the assertions to the real completeness method found in Step 1 (audit refs: `TourneySection.isComplete():63-72`, `TourneyRound.isComplete():42-49`).

- [ ] **Step 3: Full regression sweep of touched subsystems**

Run each, all must pass:
```bash
ant test-one -Dtest=org.pente.turnBased.test.TourneyTieMappingTest
ant test-one -Dtest=org.pente.turnBased.test.TBGamePassDrawTest
ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest
ant test-one -Dtest=org.pente.gameServer.tourney.test.TieReplayMatchTest
ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest
ant test-one -Dtest=org.pente.gameServer.tourney.test.TieAggregateResolutionTest
```

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/test/TieAggregateResolutionTest.java
git commit -m "test(tourney): prove tie row + decisive replay completes the round"
```

---

## Out of scope (explicitly)

Recorded so no task drifts into them: Swiss/RR half-point scoring (draw currently scores 0 for both, replay only fires for `SingleEliminationFormat`-family — pre-existing product behaviour), the seeding LEFT-JOIN bug, `statusRoundRobin.jsp` NPE, admin "declare tie" UI, prod migration/deploy steps. See `docs/superpowers/research/2026-08-08-renju-tb-tournament-readiness.md`.
