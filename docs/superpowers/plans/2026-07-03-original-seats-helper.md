# Original-Seats Helper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "net seat-swap parity" helper across turn-based and live surfaces so callers can recover the original p1/p2 of swap-variant games (dpente, dkeryo, swap2×2, renju), and fix the four call sites that currently mishandle swapped seats.

**Architecture:** Derive orientation from existing swap records — `dPenteSwapped` boolean (dpente family) and the packed base-3 `renjuSwaps` word (renju; parity of YES digits among swap1–4 + swap5, `branch` digit excluded). Three surfaces: `RenjuOpeningState.netSwapped()`, `TBGame.seatsSwapped()/getOriginalPlayer1Pid()/getOriginalPlayer2Pid()`, and `GridState.seatsSwapped()` (interface default + overrides). No schema change.

**Tech Stack:** Java (Tomcat backend), Ant build, JUnit 3.7.

**Spec:** `docs/superpowers/specs/2026-07-03-original-seats-helper-design.md`

## Global Constraints

- All work in repo `pente.org/` (cwd for all commands): `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`
- Compile with `./justCompile` (user instruction). Run a single test class with `ant test-one -Dtest=<fqcn>`.
- JUnit is 3.7: tests `extends TestCase`, `suite()` method, **no `assertFalse`** — write `assertTrue(!x)` (existing tests do this).
- The `branch` digit of `RenjuOpeningState` is a branch choice (1=A, 2=B), **never** a seat swap.
- PENDING(0) and NO(1) digits contribute nothing to parity — helpers must be valid mid-game.
- No DB schema changes.

---

### Task 1: `RenjuOpeningState.netSwapped()`

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuOpeningState.java` (add after `decode`, ~line 46)
- Test: `dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java` (append methods to existing class)

**Interfaces:**
- Consumes: existing `RenjuOpeningState` fields `swap1..swap5`, `branch`, constants `PENDING/NO/YES`, `encode()/decode(int)`.
- Produces: `public boolean netSwapped()` (instance) and `public static boolean netSwapped(int packed)` — used by Tasks 2 and 3.

- [ ] **Step 1: Write the failing tests** — append to `RenjuOpeningStateTest` (keep existing class header/ctor untouched):

```java
    public void testNetSwappedAllPendingFalse() {
        RenjuOpeningState st = new RenjuOpeningState();
        assertTrue(!st.netSwapped());
        assertTrue(!RenjuOpeningState.netSwapped(st.encode()));
    }

    public void testNetSwappedEachSingleYesTrue() {
        for (int i = 0; i < 5; i++) {
            RenjuOpeningState st = new RenjuOpeningState();
            if (i == 0) st.swap1 = RenjuOpeningState.YES;
            if (i == 1) st.swap2 = RenjuOpeningState.YES;
            if (i == 2) st.swap3 = RenjuOpeningState.YES;
            if (i == 3) st.swap4 = RenjuOpeningState.YES;
            if (i == 4) st.swap5 = RenjuOpeningState.YES;
            assertTrue("digit " + i, st.netSwapped());
            assertTrue("digit " + i, RenjuOpeningState.netSwapped(st.encode()));
        }
    }

    public void testNetSwappedTwoYesCancel() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap3 = RenjuOpeningState.YES;
        assertTrue(!st.netSwapped());
    }

    public void testNetSwappedThreeYesOdd() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.YES;
        st.swap5 = RenjuOpeningState.YES;
        assertTrue(st.netSwapped());
    }

    public void testNetSwappedBranchDigitIgnored() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.branch = RenjuOpeningState.YES; // Branch B chosen — not a swap
        assertTrue(!st.netSwapped());
        st.swap2 = RenjuOpeningState.YES;
        assertTrue(st.netSwapped()); // branch still ignored on top of a swap
    }

    public void testNetSwappedNoDeclinesIgnored() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        assertTrue(!st.netSwapped());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest`
Expected: compile FAILURE — `cannot find symbol: method netSwapped()`.

- [ ] **Step 3: Implement** — add to `RenjuOpeningState` after `decode(int)`:

```java
    /**
     * Net seat orientation: true iff an odd number of seat swaps happened —
     * take-overs after moves 1-4 plus the Branch A 5th-move swap.
     * The branch digit is a branch choice, not a swap. PENDING and NO digits
     * count as no swap, so this is valid mid-game.
     */
    public boolean netSwapped() {
        int yes = 0;
        if (swap1 == YES) yes++;
        if (swap2 == YES) yes++;
        if (swap3 == YES) yes++;
        if (swap4 == YES) yes++;
        if (swap5 == YES) yes++;
        return (yes & 1) == 1;
    }

    /** Net seat orientation straight from the packed word. */
    public static boolean netSwapped(int packed) {
        return decode(packed).netSwapped();
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest`
Expected: `OK (N tests)` — all pre-existing tests plus the 6 new ones.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuOpeningState.java dsg_src/java/org/pente/game/test/RenjuOpeningStateTest.java
git commit -m "Add RenjuOpeningState.netSwapped: net seat-swap parity helper"
```

---

### Task 2: `TBGame.seatsSwapped()` + original-pid accessors

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java` (add after `renjuSwap`, ~line 591)
- Create: `dsg_src/java/org/pente/turnBased/test/TBGameSeatsSwappedTest.java`

**Interfaces:**
- Consumes: `RenjuOpeningState.netSwapped(int)` (Task 1); existing `TBGame` members: `int game`, `boolean dPenteSwapped`, `int renjuSwaps`, `long player1Pid/player2Pid`, `GridStateFactory.TB_DPENTE/TB_DKERYO/TB_SWAP2PENTE/TB_SWAP2KERYO/TB_RENJU`.
- Produces: `public boolean seatsSwapped()`, `public long getOriginalPlayer1Pid()`, `public long getOriginalPlayer2Pid()` — used by Task 4.

- [ ] **Step 1: Write the failing test** — create `dsg_src/java/org/pente/turnBased/test/TBGameSeatsSwappedTest.java`:

```java
package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.*;
import org.pente.turnBased.*;

public class TBGameSeatsSwappedTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                TBGameSeatsSwappedTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGameSeatsSwappedTest.class);
    }

    public TBGameSeatsSwappedTest(String name) {
        super(name);
    }

    private TBGame game(int type) {
        TBGame g = new TBGame();
        g.setGame(type);
        g.setPlayer1Pid(11L);
        g.setPlayer2Pid(22L);
        return g;
    }

    public void testNonSwapVariantNeverSwapped() {
        TBGame g = game(GridStateFactory.TB_PENTE);
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testDPenteFamilySwapRestoresOriginalPids() {
        int[] types = {GridStateFactory.TB_DPENTE, GridStateFactory.TB_DKERYO,
                GridStateFactory.TB_SWAP2PENTE, GridStateFactory.TB_SWAP2KERYO};
        for (int type : types) {
            TBGame g = game(type);
            assertTrue(!g.seatsSwapped());
            g.dPenteSwap(true); // physically swaps pids
            assertTrue(g.seatsSwapped());
            assertEquals(22L, g.getPlayer1Pid());
            assertEquals(11L, g.getOriginalPlayer1Pid());
            assertEquals(22L, g.getOriginalPlayer2Pid());
        }
    }

    public void testDPenteDeclinedSwapKeepsPids() {
        TBGame g = game(GridStateFactory.TB_DPENTE);
        g.dPenteSwap(false);
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
    }

    public void testRenjuSingleSwapOdd() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode());
        // simulate the pid swap renjuSwap() performed alongside recording
        g.setPlayer1Pid(22L);
        g.setPlayer2Pid(11L);
        assertTrue(g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testRenjuTwoSwapsCancel() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode()); // two swaps: pids back where they started
        assertTrue(!g.seatsSwapped());
        assertEquals(11L, g.getOriginalPlayer1Pid());
        assertEquals(22L, g.getOriginalPlayer2Pid());
    }

    public void testRenjuBranchDigitIgnored() {
        TBGame g = game(GridStateFactory.TB_RENJU);
        RenjuOpeningState st = new RenjuOpeningState();
        st.branch = RenjuOpeningState.YES;
        g.setRenjuSwaps(st.encode());
        assertTrue(!g.seatsSwapped());
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGameSeatsSwappedTest`
Expected: compile FAILURE — `cannot find symbol: method seatsSwapped()`.

- [ ] **Step 3: Implement** — add to `TBGame.java` directly after `renjuSwap(boolean)` (~line 591):

```java
    /**
     * True if the current player1Pid/player2Pid are flipped relative to how
     * the game started, due to an opening swap. dpente-family games have a
     * single swap opportunity (boolean); renju derives net parity from the
     * packed take-over decisions. Valid mid-game.
     */
    public boolean seatsSwapped() {
        if (game == GridStateFactory.TB_DPENTE ||
                game == GridStateFactory.TB_DKERYO ||
                game == GridStateFactory.TB_SWAP2PENTE ||
                game == GridStateFactory.TB_SWAP2KERYO) {
            return dPenteSwapped;
        }
        if (game == GridStateFactory.TB_RENJU) {
            return org.pente.game.RenjuOpeningState.netSwapped(renjuSwaps);
        }
        return false;
    }

    /** The pid seated as player 1 when the game started. */
    public long getOriginalPlayer1Pid() {
        return seatsSwapped() ? player2Pid : player1Pid;
    }

    /** The pid seated as player 2 when the game started. */
    public long getOriginalPlayer2Pid() {
        return seatsSwapped() ? player1Pid : player2Pid;
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGameSeatsSwappedTest`
Expected: `OK (6 tests)`.

- [ ] **Step 5: Regression-run the existing TBGame renju test**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest`
Expected: PASS (unchanged behavior).

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGame.java dsg_src/java/org/pente/turnBased/test/TBGameSeatsSwappedTest.java
git commit -m "Add TBGame.seatsSwapped + original-pid accessors"
```

---

### Task 3: `GridState.seatsSwapped()` — live surface

**Files:**
- Modify: `dsg_src/java/org/pente/game/GridState.java` (interface, add default method)
- Modify: `dsg_src/java/org/pente/game/GridStateDecorator.java` (delegate to wrapped state)
- Modify: `dsg_src/java/org/pente/game/SynchronizedGridState.java` (synchronized delegate)
- Modify: `dsg_src/java/org/pente/game/SimplePenteState.java` (override, after `didDPenteSwap()` ~line 161)
- Modify: `dsg_src/java/org/pente/game/RenjuState.java` (override, near `didSwapAt` ~line 578)
- Create: `dsg_src/java/org/pente/game/test/SeatsSwappedGridStateTest.java`

**Interfaces:**
- Consumes: `SimplePenteState.didDPenteSwap()`, `RenjuState.didSwapAt(int)`, `RenjuState.reconstruct(MoveData, int, int[])`, `RenjuOpeningState` (Task 1 file, fields only).
- Produces: `GridState.seatsSwapped()` available polymorphically on every grid state — used by Tasks 5–7.

- [ ] **Step 1: Write the failing test** — create `dsg_src/java/org/pente/game/test/SeatsSwappedGridStateTest.java`:

```java
package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class SeatsSwappedGridStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                SeatsSwappedGridStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(SeatsSwappedGridStateTest.class);
    }

    public SeatsSwappedGridStateTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15; // 15x15 Renju board move encoding
    }

    private SimpleGridState moves(int... mv) {
        SimpleGridState s = new SimpleGridState(15, 15);
        for (int m : mv) s.addMove(m);
        return s;
    }

    public void testDefaultIsFalse() {
        GridState plain = new SimpleGridState(19, 19);
        assertTrue(!plain.seatsSwapped());
    }

    public void testSimplePenteStateFollowsDPenteSwap() {
        SimplePenteState s = new SimplePenteState(new SimpleGomokuState(19, 19));
        assertTrue(!s.seatsSwapped());
        s.dPenteSwapDecisionMade(true);
        assertTrue(s.seatsSwapped());
    }

    public void testSimplePenteStateDeclinedSwapFalse() {
        SimplePenteState s = new SimplePenteState(new SimpleGomokuState(19, 19));
        s.dPenteSwapDecisionMade(false);
        assertTrue(!s.seatsSwapped());
    }

    public void testSynchronizedGridStateDelegates() {
        SimplePenteState inner = new SimplePenteState(new SimpleGomokuState(19, 19));
        GridState wrapped = new SynchronizedGridState(inner);
        assertTrue(!wrapped.seatsSwapped());
        inner.dPenteSwapDecisionMade(true);
        assertTrue(wrapped.seatsSwapped());
    }

    public void testRenjuStateSingleSwapOdd() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(moves(xy(7, 7)), st.encode(), null);
        assertTrue(s.seatsSwapped());
    }

    public void testRenjuStateTwoSwapsCancel() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.YES;
        RenjuState s = RenjuState.reconstruct(moves(xy(7, 7), xy(8, 8)), st.encode(), null);
        assertTrue(!s.seatsSwapped());
    }

    public void testRenjuStateNoSwapsFalse() {
        RenjuState s = new RenjuState(15, 15);
        assertTrue(!s.seatsSwapped());
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `ant test-one -Dtest=org.pente.game.test.SeatsSwappedGridStateTest`
Expected: compile FAILURE — `cannot find symbol: method seatsSwapped()`.

- [ ] **Step 3: Implement — four edits**

`GridState.java` — add inside the interface (near the other query methods, e.g. after `getWinner()`):

```java
    /**
     * True if the current seat order is flipped relative to game start by an
     * opening swap (dpente family take-over, renju take-overs). Net parity:
     * two renju take-overs cancel. Default: game has no swap mechanic.
     */
    default boolean seatsSwapped() {
        return false;
    }
```

`GridStateDecorator.java` — add delegating method (matching the class's existing delegation style):

```java
    public boolean seatsSwapped() {
        return gridState.seatsSwapped();
    }
```

Note: `SimplePenteState` and `RenjuState` extend this decorator and override below — the decorator delegation only matters for pass-through wrappers.

`SynchronizedGridState.java` — add (matching the class's `synchronized` delegate style):

```java
    public synchronized boolean seatsSwapped() {
        return gridState.seatsSwapped();
    }
```

`SimplePenteState.java` — add directly after `didDPenteSwap()` (~line 161):

```java
    @Override
    public boolean seatsSwapped() {
        return dPenteSwap;
    }
```

`RenjuState.java` — add directly after `didSwapAt(int)` (~line 578):

```java
    /** Net parity of the recorded take-over decisions (windows 1-5). */
    @Override
    public boolean seatsSwapped() {
        return swapDecision[1] ^ swapDecision[2] ^ swapDecision[3]
                ^ swapDecision[4] ^ swapDecision[5];
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `ant test-one -Dtest=org.pente.game.test.SeatsSwappedGridStateTest`
Expected: `OK (7 tests)`.

- [ ] **Step 5: Regression-run renju state tests**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuStateTest` and `ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/game/GridState.java dsg_src/java/org/pente/game/GridStateDecorator.java dsg_src/java/org/pente/game/SynchronizedGridState.java dsg_src/java/org/pente/game/SimplePenteState.java dsg_src/java/org/pente/game/RenjuState.java dsg_src/java/org/pente/game/test/SeatsSwappedGridStateTest.java
git commit -m "Add GridState.seatsSwapped with overrides for swap-capable states"
```

---

### Task 4: Turn-based tournament lookup uses original pids (`CacheTBStorer`)

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1125-1142`

**Interfaces:**
- Consumes: `TBGame.seatsSwapped()`, `getOriginalPlayer1Pid()`, `getOriginalPlayer2Pid()` (Task 2).
- Produces: behavior fix only.

- [ ] **Step 1: Edit the game-over tournament gate**

Current code (verbatim, `CacheTBStorer.java:1125-1142`):

```java
                } else if (game.getEventId() != getEventId(game.getGame())) {
                    TourneyMatch tourneyMatch = null;
                    if ((game.getGame() == GridStateFactory.TB_DPENTE ||
                            game.getGame() == GridStateFactory.TB_DKERYO ||
                            game.getGame() == GridStateFactory.TB_SWAP2PENTE ||
                            game.getGame() == GridStateFactory.TB_SWAP2KERYO) && game.didDPenteSwap()) {
                        tourneyMatch = tourneyStorer.getUnplayedMatch(game.getPlayer2Pid(), game.getPlayer1Pid(), game.getEventId());
                    } else {
                        tourneyMatch = tourneyStorer.getUnplayedMatch(game.getPlayer1Pid(), game.getPlayer2Pid(), game.getEventId());
                    }
                    if (tourneyMatch != null) {
                        tourneyMatch.setGid(game.getGid());
                        int winner = game.getWinner();
                        if ((game.getGame() == GridStateFactory.TB_DPENTE ||
                                game.getGame() == GridStateFactory.TB_DKERYO ||
                                game.getGame() == GridStateFactory.TB_SWAP2PENTE ||
                                game.getGame() == GridStateFactory.TB_SWAP2KERYO) && game.didDPenteSwap()) {
                            winner = 3 - winner;
```

Replace with (the two variant-list `if` gates collapse into the helper; renju now covered):

```java
                } else if (game.getEventId() != getEventId(game.getGame())) {
                    TourneyMatch tourneyMatch = tourneyStorer.getUnplayedMatch(
                            game.getOriginalPlayer1Pid(), game.getOriginalPlayer2Pid(), game.getEventId());
                    if (tourneyMatch != null) {
                        tourneyMatch.setGid(game.getGid());
                        int winner = game.getWinner();
                        if (game.seatsSwapped()) {
                            winner = 3 - winner;
```

Keep everything after `winner = 3 - winner;` (closing braces, subsequent statements) exactly as it is — only the shown lines change. For the dpente family this is behavior-identical: `seatsSwapped()` ≡ the old variant-list `&& didDPenteSwap()` check, and `getOriginalPlayer1Pid()/getOriginalPlayer2Pid()` reproduce the old pid-order flip. Renju games gain the same treatment via parity.

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 3: Run turn-based regression tests**

Run: `ant test-one -Dtest=org.pente.turnBased.test.TBGameSeatsSwappedTest` and `ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest`
Expected: PASS. (No unit test exists for `CacheTBStorer` tournament flow — it needs Redis + storer wiring; the helper itself is covered by Task 2 tests and this edit is a mechanical substitution.)

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "TB tournament: resolve unplayed match via original seats (covers renju)"
```

---

### Task 5: `ServerTable.swapSeats()` guard via `seatsSwapped()`

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:3314-3326`

**Interfaces:**
- Consumes: `GridState.seatsSwapped()` (Task 3).
- Produces: behavior fix only (renju end-of-game rotation).

- [ ] **Step 1: Edit the guard**

Current code (verbatim, `ServerTable.java:3314-3326`):

```java
    protected void swapSeats() {
        boolean swap2 = game == GridStateFactory.SWAP2PENTE_GAME || game == GridStateFactory.SPEED_SWAP2PENTE_GAME ||
                game == GridStateFactory.SWAP2KERYO_GAME || game == GridStateFactory.SPEED_SWAP2KERYO_GAME;
        // only swap if both players still sitting
        // (if forced resign, don't swap)
        // (if d-pente and already swapped, don't swap back)
        if (game == GridStateFactory.DPENTE_GAME || game == GridStateFactory.SPEED_DPENTE_GAME
                || game == GridStateFactory.DKERYO_GAME || game == GridStateFactory.SPEED_DKERYO_GAME
                || swap2) {
            if (((PenteState) gridState).didDPenteSwap()) {
                return; // already swapped seats
            }
        }
```

Replace with:

```java
    protected void swapSeats() {
        // only swap if both players still sitting
        // (if forced resign, don't swap)
        // (if an opening swap already flipped the seats, don't swap back --
        //  net parity, so e.g. two renju take-overs cancel and we DO rotate)
        if (gridState != null && gridState.seatsSwapped()) {
            return; // already swapped seats
        }
```

The rest of the method (from the `if (!anyComputersSitting() ...` line) stays untouched. Behavior-identical for the dpente family (`SimplePenteState.seatsSwapped()` ≡ `didDPenteSwap()`); renju gains the guard with parity semantics; non-swap games still return `false` and rotate.

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL. (If an unused-variable warning policy flags nothing else, confirm the removed `swap2` local is not referenced elsewhere in the method — it was only used in the deleted guard.)

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "swapSeats: skip end-of-game rotation via net swap parity (adds renju)"
```

---

### Task 6: Tournament rejoin honors in-progress swap (`TournamentServerTable.handleJoin`)

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/TournamentServerTable.java:239-257`

**Interfaces:**
- Consumes: `GridState.seatsSwapped()` (Task 3); inherited `protected GridState gridState` (ServerTable:87).
- Produces: behavior fix only.

- [ ] **Step 1: Edit handleJoin**

Current code (verbatim, `TournamentServerTable.java:239-257`):

```java
    public void handleJoin(String player) {
        super.handleJoin(player);
        if (isPlayerInTable(player)) {
            int i = 0;
            if (tourneyMatch != null && tourneyMatch.getPlayer1().getName().equals(player)) {
                i = 1;
            } else if (tourneyMatch != null && tourneyMatch.getPlayer2().getName().equals(player)) {
                i = 2;
            }
            if (i > 0) {
                if (this.sittingPlayers[i] == null) {
                    sit(player, i);
                } else if (!this.sittingPlayers[i].getName().equals(player)) {
                    stand(this.sittingPlayers[i].getName(), i);
                    sit(player, i);
                }
            }
        }
    }
```

Replace the `if (i > 0) {` block opening with a mirror adjustment (only the lines shown change — add the flip between the role match and the seating):

```java
            if (i > 0) {
                // if an opening swap flipped the seats of the game in
                // progress, the match roles are seated mirrored until it ends
                if (gridState != null && !gridState.isGameOver()
                        && gridState.seatsSwapped()) {
                    i = 3 - i;
                }
                if (this.sittingPlayers[i] == null) {
                    sit(player, i);
                } else if (!this.sittingPlayers[i].getName().equals(player)) {
                    stand(this.sittingPlayers[i].getName(), i);
                    sit(player, i);
                }
            }
```

Rationale: mid-game the server-side `sittingPlayers[]` were physically swapped by `handleSwap`/`handleRenjuSwap`; re-seating by raw match role would overwrite that. After the game is over (`isGameOver()`), seating reverts to raw match roles for the next match — the pre-existing behavior.

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/TournamentServerTable.java
git commit -m "Tournament rejoin: seat by match role mirrored through in-progress swap"
```

---

### Task 7: Tournament next-match lookup un-flips swapped seats (`updateDatabaseAfterGameOver`)

> **EXECUTED THEN REVERTED (commit 8b72b04).** Execution analysis showed this
> call site was already correct: `super.updateDatabaseAfterGameOver`'s nested
> tournament block (`ServerTable.java:3715-3792`) runs first, resolves the
> reversed-roles next match itself, and conditionally rotates seats
> (`swapSeats()` at `:3765`) so that `sittingPlayers` order after super always
> equals the next match's orientation — the physical mid-game swap doubles as
> the set alternation. Un-flipping here produced wrong-orientation lookups for
> swapped games. The real renju gap in the live tournament path is super's
> `swapped` derivation — fixed in Task 8 below.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/TournamentServerTable.java:314-333`

**Interfaces:**
- Consumes: `GridState.seatsSwapped()` (Task 3).
- Produces: behavior fix only.

- [ ] **Step 1: Edit the pid extraction**

Current code (verbatim, `TournamentServerTable.java:319-325`):

```java
        try {
            super.updateDatabaseAfterGameOver(gameData, fileGameData, winnerPlayer, loserPlayer, game, localWinner, localSet);
            long newPid1 = sittingPlayers[1].getPlayerID();
            long newPid2 = sittingPlayers[2].getPlayerID();
            TourneyMatch newMatch = resources.getTourneyStorer().getUnplayedMatch(
                    newPid1, newPid2,
                    getGameEvent(game).getEventID());
```

Replace with:

```java
        try {
            super.updateDatabaseAfterGameOver(gameData, fileGameData, winnerPlayer, loserPlayer, game, localWinner, localSet);
            long newPid1 = sittingPlayers[1].getPlayerID();
            long newPid2 = sittingPlayers[2].getPlayerID();
            // an opening swap physically flipped sittingPlayers mid-game;
            // restore the game-start seat order before the order-sensitive
            // unplayed-match lookup
            if (gridState != null && gridState.seatsSwapped()) {
                long tmp = newPid1;
                newPid1 = newPid2;
                newPid2 = tmp;
            }
            TourneyMatch newMatch = resources.getTourneyStorer().getUnplayedMatch(
                    newPid1, newPid2,
                    getGameEvent(game).getEventID());
```

The rest of the method is unchanged. (`gridState` at this point still holds the just-finished game — the tournament branch of `updateDatabaseAfterGameOverInSeparateThread` calls this before any new game is started, and never calls `swapSeats()`.)

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full test sweep**

Run: `ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest`, `ant test-one -Dtest=org.pente.game.test.SeatsSwappedGridStateTest`, `ant test-one -Dtest=org.pente.turnBased.test.TBGameSeatsSwappedTest`, `ant test-one -Dtest=org.pente.game.test.RenjuStateTest`, `ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest`, `ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/TournamentServerTable.java
git commit -m "Tournament game-over: un-flip swapped seats before unplayed-match lookup"
```

---

### Task 8 (added during execution): `ServerTable` nested tournament block derives `swapped` via `seatsSwapped()`

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:3718-3733` (inside the `serverData.isTournament()` block of `updateDatabaseAfterGameOver`)

**Interfaces:**
- Consumes: `GridState.seatsSwapped()` (Task 3).
- Produces: behavior fix only — live tournament renju games get the correct match-result winner flip, next-match orientation, and rotation skip.

- [ ] **Step 1: Edit the `swapped` derivation**

Current code (verbatim, `ServerTable.java:3718-3733`):

```java
                int localWinner2 = localWinner;
                boolean swapped = false;
                // for dpente games, don't swap player ids
                // just record game as being won by correct id
                if (game == GridStateFactory.DPENTE || game == GridStateFactory.SPEED_DPENTE ||
                        game == GridStateFactory.DKERYO || game == GridStateFactory.SPEED_DKERYO ||
                        game == GridStateFactory.SWAP2PENTE || game == GridStateFactory.SPEED_SWAP2PENTE ||
                        game == GridStateFactory.SWAP2KERYO || game == GridStateFactory.SPEED_SWAP2KERYO) {

                    if (((PenteState) gridState).didDPenteSwap()) {
                        if (localWinner2 != 0) { //draw
                            localWinner2 = 3 - localWinner;
                        }
                        swapped = true;
                    }
                }
```

Replace with:

```java
                int localWinner2 = localWinner;
                // if an opening swap flipped the player ids mid-game, record
                // the result from the match's original perspective. net
                // parity via seatsSwapped() covers the dpente family and
                // renju take-overs alike.
                boolean swapped = gridState != null && gridState.seatsSwapped();
                if (swapped && localWinner2 != 0) { //draw
                    localWinner2 = 3 - localWinner;
                }
```

Everything after (from `tourneyMatch.setGid(...)`) stays exactly as-is — the
downstream un-flip of the reversed lookup pids and the `!swapped` →
`swapSeats()` rotation already generalize once `swapped` is right.
Behavior-identical for the dpente family; renju gains parity semantics.

- [ ] **Step 2: Compile**

Run: `./justCompile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "Live tournament game-over: derive swapped via net swap parity (adds renju)"
```

---

### Task 9 (added during execution): renju is a single-game set — wire tournament set creation like go

Renju (Taraguchi-10) is played as a SINGLE-game set, not a two-game
color-alternating set — the opening protocol balances the first move, like
komi does for go (see `NewGameServlet.isBlackFirst`, commit 5884e49, which
already treats renju invitations this way). Five tournament sites hard-code
the go family as the only single-game games; renju must join them via one
shared predicate.

**Files:**
- Modify: `dsg_src/java/org/pente/game/GridStateFactory.java` — add predicate
- Create: `dsg_src/java/org/pente/game/test/GridStateFactorySingleGameSetTest.java`
- Modify: `dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java` (~line 110: swapped second match row)
- Modify: `dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java` (~line 261: `boolean set = (...)`)
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java` (two sites: `insertMatch` ~line 490 dedup exemption; `applyMatchTo` tie block ~line 550)
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` (`createTournamentSet` ~line 2059: skip reversed second game)

**Interfaces:**
- Produces: `public static boolean isSingleGameSet(int game)` on `GridStateFactory`.

- [ ] **Step 1: Failing test** — new JUnit 3 class (same skeleton as sibling tests in `game/test/`; `assertTrue(!x)`, no `assertFalse`):

```java
    public void testGoFamilyIsSingleGameSet() {
        int[] go = {GridStateFactory.GO, GridStateFactory.GO9, GridStateFactory.GO13,
                GridStateFactory.SPEED_GO, GridStateFactory.SPEED_GO9, GridStateFactory.SPEED_GO13,
                GridStateFactory.TB_GO, GridStateFactory.TB_GO9, GridStateFactory.TB_GO13};
        for (int g : go) {
            assertTrue("game " + g, GridStateFactory.isSingleGameSet(g));
        }
    }

    public void testRenjuIsSingleGameSet() {
        assertTrue(GridStateFactory.isSingleGameSet(GridStateFactory.RENJU));
        assertTrue(GridStateFactory.isSingleGameSet(GridStateFactory.SPEED_RENJU));
        assertTrue(GridStateFactory.isSingleGameSet(GridStateFactory.TB_RENJU));
    }

    public void testTwoGameSetGamesAreNot() {
        int[] two = {GridStateFactory.PENTE, GridStateFactory.TB_PENTE,
                GridStateFactory.DPENTE, GridStateFactory.TB_DPENTE,
                GridStateFactory.SWAP2PENTE, GridStateFactory.TB_SWAP2PENTE,
                GridStateFactory.KERYO, GridStateFactory.GOMOKU};
        for (int g : two) {
            assertTrue("game " + g, !GridStateFactory.isSingleGameSet(g));
        }
    }
```

- [ ] **Step 2: Implement predicate** in `GridStateFactory`:

```java
    /**
     * Games played as a single-game set rather than a two-game
     * (color-alternating) set: the go family, where komi balances the colors,
     * and renju, where the Taraguchi-10 opening protocol balances the first
     * move. Accepts live, speed and turn-based game ids.
     */
    public static boolean isSingleGameSet(int game) {
        return game == GO || game == GO9 || game == GO13 ||
                game == SPEED_GO || game == SPEED_GO9 || game == SPEED_GO13 ||
                game == TB_GO || game == TB_GO9 || game == TB_GO13 ||
                game == RENJU || game == SPEED_RENJU || game == TB_RENJU;
    }
```

Run: `ant test-one -Dtest=org.pente.game.test.GridStateFactorySingleGameSetTest` → OK (3 tests).

- [ ] **Step 3: Replace the five go-lists with the predicate** (each is a
mechanical substitution; the surrounding logic must not change):

1. `SingleEliminationFormat` — the 9-clause `if (tourney.getGame() != GridStateFactory.GO && ...)` guarding the "now add match with players swapped" block becomes `if (!GridStateFactory.isSingleGameSet(tourney.getGame()))`.
2. `DoubleEliminationFormat` — `boolean set = (tourney.getGame() != GridStateFactory.GO && ...)` becomes `boolean set = !GridStateFactory.isSingleGameSet(tourney.getGame());`.
3. `CacheTourneyStorer.insertMatch` — the `(( player1 < player2 ) || t.getGame() == GridStateFactory.TB_GO || ... TB_GO13 )` disjunct becomes `(( ...player1 < player2... ) || GridStateFactory.isSingleGameSet(t.getGame()))`.
4. `CacheTourneyStorer.applyMatchTo` tie block — the 9-clause `if (t.getGame() != GridStateFactory.GO && ...)` guarding `insertMatch(more[1])` becomes `if (!GridStateFactory.isSingleGameSet(t.getGame()))`.
5. `CacheTBStorer.createTournamentSet` — `if (game != GridStateFactory.TB_GO && game != GridStateFactory.TB_GO9 && game != GridStateFactory.TB_GO13)` guarding the `tbg2` creation becomes `if (!GridStateFactory.isSingleGameSet(game))`.

- [ ] **Step 4: Compile + regression**

Run: `./justCompile` → BUILD SUCCESSFUL, then the Task 7 six-test sweep plus the new test class.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/GridStateFactory.java dsg_src/java/org/pente/game/test/GridStateFactorySingleGameSetTest.java dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "Tournaments: renju is a single-game set — shared isSingleGameSet predicate (go + renju)"
```

Out of scope, checked deliberately: `MySQLPenteGameStorer:545` (different predicate — swap-capable+go grouping), `MobileJsonHelper.isGoGame` (display), `NewGameServlet.isBlackFirst` (color-slot mapping; already renju-correct).

---

## Notes for the implementer

- `GridState` is an interface (`GridState.java:29`); the codebase compiles on a modern JDK (no `source=` pin in `build.xml`), so a `default` method is fine.
- Live `gridState` is the **concrete** state (`GridStateFactory.createGridState` returns `SimplePenteState`/`RenjuState`/etc. unwrapped — see factory `:259+`; `ServerTable` does `gridState instanceof RenjuState` at `:557`). The decorator/synchronized delegations in Task 3 are defensive completeness.
- Do NOT touch `CacheTBStorer.java:850-861` — that lookup already tries both pid orders (swap-agnostic).
- Do NOT touch `determineAndUpdateForfeit` (`TournamentServerTable.java:215-219`) — no game was played on the forfeited match, so no opening swap can have flipped anything; its deliberate reversed-order lookup is set alternation, not swap handling.
- JUnit 3.7: no `assertFalse` — use `assertTrue(!x)`.
