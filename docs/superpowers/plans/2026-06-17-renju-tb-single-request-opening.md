# Renju TB Single-Request Opening Contract — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collapse the turn-based Renju opening protocol to three `renjuAction` values — `swap` (take over, no stone), `move` (1 stone = decline+place / Branch A move 5; 10 stones = decline + Branch B offer), `select` (atomic 2 stones: black move 5 + white move 6) — deleting `branch`, `offer`, `move4`, and the `moves[0]` sentinel, across backend + JSP + iOS + Android + docs.

**Architecture:** `MoveServlet` reconstructs the authoritative `RenjuState` per request and drives the unchanged engine FSM. The error-prone contract logic is extracted into a pure, fully unit-tested `RenjuTbContract.resolve(...)`; the servlet executes the returned decision's storer calls. Clients emit the new wire contract; OFFERS phase becomes unreachable (offers are atomic with the branch choice), BRANCH remains only after a take-over at move 4.

**Tech Stack:** Java/Tomcat servlet + JUnit3 (`ant test-one`, `JAVA_HOME=/opt/homebrew/opt/openjdk@21/...`, `./justCompile`); JSP inline JS; Objective-C (iOS); Java/OkHttp + Android JUnit.

**Spec:** `docs/superpowers/specs/2026-06-17-renju-tb-single-request-opening-design.md`

---

## File Structure

**Backend — `pente.org` (`feat/renju`)**
- Create `dsg_src/java/org/pente/turnBased/web/RenjuTbContract.java` — pure resolver: `(action, moves, RenjuState) → RenjuTbDecision`, throws `RenjuContractException(msg)` on any contract violation. No I/O, no DB. The single source of truth for the wire contract.
- Create `dsg_src/java/org/pente/turnBased/web/test/RenjuTbContractTest.java` — JUnit3 covering every contract row + every rejection.
- Modify `dsg_src/java/org/pente/turnBased/web/MoveServlet.java:409-569` — replace the 5-action inline block with: reconstruct → `RenjuTbContract.resolve` → execute decision.
- Modify `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp` — 3-action UI; delete BRANCH/OFFERS controls + `renjuMoveFour`/`renjuSubmitOffers`; 2-tap selection.

**iOS — `penteLive-iOS` (`renju-turnbased`)**
- Modify `test1/BoardViewController.m` — action remap (`move4`→`move`, drop decline prefix, fold BRANCH, 2-tap SELECTION).

**Android — `pentelive-android` (`feat/renju-android-tb`)**
- Modify `app/src/main/java/be/submanifold/pentelive/BoardActivity.java` — windows 1–3 decline → `move`; window-4 decline → place 1/10 → `move`; delete BRANCH/OFFERS; 2-tap SELECTION.
- Modify `app/src/test/java/be/submanifold/pentelive/GameRenjuUnitTest.java` + `net/OkHttpPenteApiTest.java` — new action/payload assertions.

**Docs — `pente.org` + app repos**
- `docs/renju-integration-guide.md` §2.4/§3/§3.6/§4; `penteLive-iOS/docs/renju-handoff.md`; `pentelive-android/docs/renju-handoff.md`.

---

## Contract reference (target)

| `renjuAction` | valid phase | `moves` | engine effect |
|---|---|---|---|
| `swap` | SWAP | (ignored) | `renjuSwap(true)` — take over, no stone |
| `move` | SWAP / BRANCH / MOVE | `m` | decline pending swap if any; at branch point → `renjuBranch(false)` + place move 5; else place the stone |
| `move` | SWAP@4 / BRANCH | `m1..m10` | decline pending swap if any; pre-validate offers; `renjuBranch(true)` + persist 10 offers |
| `select` | SELECTION | `m5,m6` | atomic: commit `m5` (black, must be offered) + `m6` (white, empty/in-bounds/≠m5) |

Branch point ≡ `pending.getNumMoves()==4 && branch not yet chosen` (covers fresh decline *and* post-take-over). Guard: `swap`→`isAwaitingSwapDecision`; `move`→`!isOpeningComplete && !isAwaitingFifthSelection`; `select`→`isAwaitingFifthSelection`.

---

## Task 1: `RenjuTbDecision` + `RenjuContractException` value types

**Files:**
- Create: `dsg_src/java/org/pente/turnBased/web/RenjuTbContract.java` (holds the nested types + resolver; built incrementally over Tasks 1–2)

- [ ] **Step 1: Create the file with the value types only**

```java
package org.pente.turnBased.web;

import org.pente.game.RenjuState;
import org.pente.game.RenjuOpeningState;

/**
 * Pure (no I/O) resolver for the turn-based Renju opening wire contract.
 * Maps a (renjuAction, moves[], reconstructed RenjuState) request onto a
 * typed {@link Decision} describing the storer calls MoveServlet must make,
 * or throws {@link RenjuContractException} (whose message is the user-facing
 * error) when the request violates the contract. Single source of truth for
 * the three-action contract: swap / move(1|10) / select(2).
 */
public final class RenjuTbContract {

    private RenjuTbContract() {}

    /** A contract violation; the message is surfaced verbatim via handleError. */
    public static final class RenjuContractException extends Exception {
        public RenjuContractException(String message) { super(message); }
    }

    public enum Kind { TAKE_OVER, PLACE, BRANCH_A, BRANCH_B, SELECT }

    /** The resolved plan of mutations. {@code stones} contents depend on kind:
     *  TAKE_OVER: empty · PLACE: [stone] · BRANCH_A: [move5] ·
     *  BRANCH_B: [10 offers] · SELECT: [move5, move6]. */
    public static final class Decision {
        public final Kind kind;
        public final boolean declineSwap; // a pending swap window must be declined first
        public final int[] stones;
        public Decision(Kind kind, boolean declineSwap, int[] stones) {
            this.kind = kind; this.declineSwap = declineSwap; this.stones = stones;
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org && ./justCompile`
Expected: clean (the file compiles; resolver method added next).

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/RenjuTbContract.java
git commit -m "feat(renju): add RenjuTbContract value types (TB contract seam)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `RenjuTbContract.resolve` — TDD the whole contract

**Files:**
- Test: `dsg_src/java/org/pente/turnBased/web/test/RenjuTbContractTest.java`
- Modify: `dsg_src/java/org/pente/turnBased/web/RenjuTbContract.java`

The resolver needs reconstructed `RenjuState`s at each phase. Build them with the engine the way `RenjuReconstructTest` does (place moves via `addMove` / use `RenjuState.reconstruct`). A small private helper in the test creates: a window-1 SWAP state (1 stone), a window-4 SWAP state (4 stones), a post-take-over BRANCH state (4 stones, swap4 resolved YES, branch PENDING), a SELECTION state (4 stones + branch=B + 10 offers), and a COMPLETE state.

- [ ] **Step 1: Write the failing test**

```java
package org.pente.turnBased.web.test;

import junit.framework.TestCase;
import org.pente.game.RenjuState;
import org.pente.turnBased.web.RenjuTbContract;
import org.pente.turnBased.web.RenjuTbContract.Decision;
import org.pente.turnBased.web.RenjuTbContract.Kind;
import org.pente.turnBased.web.RenjuTbContract.RenjuContractException;

public class RenjuTbContractTest extends TestCase {

    private static final int C = 112; // center of 15x15

    // --- builders (mirror RenjuReconstructTest style) ---

    /** SWAP window open after `n` opening stones (1..4), no swap taken yet. */
    private RenjuState swapWindow(int n) {
        RenjuState s = new RenjuState(15, 15);
        int[] seed = { C, C + 1, C - 1, C + 15 }; // center, then inside 3x3/5x5/7x7
        for (int i = 0; i < n; i++) s.addMove(seed[i]);
        assertTrue("expected SWAP after " + n, s.isAwaitingSwapDecision());
        return s;
    }

    /** Post-take-over BRANCH state: 4 stones, swap4 resolved, branch pending. */
    private RenjuState branchAfterTakeover() {
        RenjuState s = swapWindow(4);
        s.renjuSwapDecisionMade(true); // take over -> awaitingSwap=false, branch pending
        assertTrue(s.isAwaitingBranchChoice());
        return s;
    }

    /** SELECTION state: branch B chosen, 10 offers recorded. */
    private RenjuState selection() {
        RenjuState s = swapWindow(4);
        s.renjuSwapDecisionMade(false);
        s.chooseBranch(true);
        int[] offers = {113,114,115,116,128,129,130,131,144,145};
        s.offerFifthMoves(offers);
        assertTrue(s.isAwaitingFifthSelection());
        return s;
    }

    // --- swap ---

    public void testSwapTakeOver() throws Exception {
        Decision d = RenjuTbContract.resolve("swap", null, swapWindow(2));
        assertEquals(Kind.TAKE_OVER, d.kind);
    }

    public void testSwapRejectedWhenNoWindow() {
        try {
            RenjuTbContract.resolve("swap", null, branchAfterTakeover());
            fail("expected rejection");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- move: windows 1-3 decline+place ---

    public void testMoveDeclineAndPlaceWindow2() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, swapWindow(2));
        assertEquals(Kind.PLACE, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(C + 2, d.stones[0]);
    }

    // --- move: branch A at window 4 (fresh decline) ---

    public void testMoveBranchAFreshDecline() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, swapWindow(4));
        assertEquals(Kind.BRANCH_A, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(C + 2, d.stones[0]);
    }

    // --- move: branch A after take-over (no swap to decline) ---

    public void testMoveBranchAAfterTakeover() throws Exception {
        Decision d = RenjuTbContract.resolve("move", new int[]{ C + 2 }, branchAfterTakeover());
        assertEquals(Kind.BRANCH_A, d.kind);
        assertFalse(d.declineSwap);
    }

    // --- move: branch B (10 stones) ---

    public void testMoveBranchBTenOffers() throws Exception {
        int[] offers = {113,114,115,116,128,129,130,131,144,145};
        Decision d = RenjuTbContract.resolve("move", offers, swapWindow(4));
        assertEquals(Kind.BRANCH_B, d.kind);
        assertTrue(d.declineSwap);
        assertEquals(10, d.stones.length);
    }

    public void testMoveBranchBRejectsSymmetricOfferSet() {
        int[] bad = {40,41,42,55,57,70,71,72,160,176}; // < 10 distinct orbits
        try {
            RenjuTbContract.resolve("move", bad, swapWindow(4));
            fail("expected offer rejection");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testMoveTenStonesRejectedOutsideBranchPoint() {
        int[] offers = {113,114,115,116,128,129,130,131,144,145};
        try {
            RenjuTbContract.resolve("move", offers, swapWindow(2));
            fail("expected rejection: 10 stones only at branch point");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testMoveRejectsBadStoneCount() {
        try {
            RenjuTbContract.resolve("move", new int[]{1,2,3}, swapWindow(4));
            fail("expected rejection: count not in {1,10}");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- select: atomic 2-stone ---

    public void testSelectCommitsTwoStones() throws Exception {
        Decision d = RenjuTbContract.resolve("select", new int[]{130, 200}, selection());
        assertEquals(Kind.SELECT, d.kind);
        assertEquals(130, d.stones[0]); // black move 5 (was offered)
        assertEquals(200, d.stones[1]); // white move 6
    }

    public void testSelectRejectsUnofferedFifth() {
        try {
            RenjuTbContract.resolve("select", new int[]{99, 200}, selection());
            fail("expected rejection: move 5 not offered");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsMove6OnOccupied() {
        try {
            RenjuTbContract.resolve("select", new int[]{130, C}, selection()); // C is occupied (move 1)
            fail("expected rejection: move 6 not empty");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsMove6EqualMove5() {
        try {
            RenjuTbContract.resolve("select", new int[]{130, 130}, selection());
            fail("expected rejection: move 6 == move 5");
        } catch (RenjuContractException e) { /* ok */ }
    }

    public void testSelectRejectsWrongStoneCount() {
        try {
            RenjuTbContract.resolve("select", new int[]{130}, selection());
            fail("expected rejection: select needs 2 stones");
        } catch (RenjuContractException e) { /* ok */ }
    }

    // --- unknown action ---

    public void testUnknownActionRejected() {
        try {
            RenjuTbContract.resolve("branch", new int[]{1}, swapWindow(4));
            fail("expected rejection: unknown action");
        } catch (RenjuContractException e) { /* ok */ }
    }
}
```

- [ ] **Step 2: Run to verify it fails (resolve not implemented)**

Run: `cd .../pente.org && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ant test-one -Dtest=org.pente.turnBased.web.test.RenjuTbContractTest`
Expected: compile error / FAIL — `resolve` undefined.

- [ ] **Step 3: Implement `resolve`**

Add to `RenjuTbContract` (after the value types):

```java
    /**
     * Resolve a TB Renju opening request. Validates the action against the
     * pending phase and the payload against the engine's rules, WITHOUT
     * mutating anything. Throws RenjuContractException (message = user error)
     * on any violation; otherwise returns the plan of mutations.
     */
    public static Decision resolve(String action, int[] moves, RenjuState pending)
            throws RenjuContractException {

        if ("swap".equals(action)) {
            if (!pending.isAwaitingSwapDecision()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            return new Decision(Kind.TAKE_OVER, false, new int[0]);
        }

        if ("select".equals(action)) {
            if (!pending.isAwaitingFifthSelection()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            if (moves == null || moves.length != 2) {
                throw new RenjuContractException("Select requires the chosen 5th move and your 6th move.");
            }
            int m5 = moves[0], m6 = moves[1];
            if (!pending.getOfferedFifthMoves().contains(Integer.valueOf(m5))) {
                throw new RenjuContractException("Selected move was not offered.");
            }
            // move 6 (white): empty, in bounds, distinct from move 5. White has
            // no forbidden points, so no further restriction. Checked on the
            // 4-stone board (offers are not placed); the 9 unchosen offers are
            // discarded, so m6 may legally land on a former offer != m5.
            if (m6 == m5 || pending.outOfBoundsPublic(m6) || pending.getPosition(m6) != 0) {
                throw new RenjuContractException("Invalid selection or 6th move.");
            }
            return new Decision(Kind.SELECT, false, new int[]{ m5, m6 });
        }

        if ("move".equals(action)) {
            if (pending.isOpeningComplete() || pending.isAwaitingFifthSelection()) {
                throw new RenjuContractException("Renju action does not match the pending decision.");
            }
            boolean declineSwap = pending.isAwaitingSwapDecision();
            boolean branchPoint = pending.getNumMoves() == 4 && !pending.isBranchChosen();
            int n = (moves == null) ? 0 : moves.length;

            if (branchPoint) {
                if (n == 1) {
                    return new Decision(Kind.BRANCH_A, declineSwap, new int[]{ moves[0] });
                } else if (n == 10) {
                    if (!pending.wouldAcceptFifthOffers(moves)) {
                        throw new RenjuContractException("Invalid 5th-move offer.");
                    }
                    int[] offers = new int[10];
                    System.arraycopy(moves, 0, offers, 0, 10);
                    return new Decision(Kind.BRANCH_B, declineSwap, offers);
                }
                throw new RenjuContractException(
                        "At the branch point place 1 stone (Branch A) or 10 (Branch B).");
            } else {
                if (n != 1) {
                    throw new RenjuContractException("Expected a single move.");
                }
                return new Decision(Kind.PLACE, declineSwap, new int[]{ moves[0] });
            }
        }

        throw new RenjuContractException("Unknown renju action.");
    }
```

This requires two small **additive** accessors on `RenjuState` (Task 2a) — `isBranchChosen()` and a public bounds check. (`getOfferedFifthMoves`, `getPosition`, `isAwaitingSwapDecision`, `isAwaitingFifthSelection`, `isOpeningComplete`, `getNumMoves`, `wouldAcceptFifthOffers` already exist — verified in the survey.)

- [ ] **Step 3a: Add the two additive `RenjuState` accessors**

In `dsg_src/java/org/pente/game/RenjuState.java`, add near the other predicate methods:

```java
    /** True once the post-move-4 branch (A or B) has been chosen. */
    public boolean isBranchChosen() {
        return branchChosen;
    }

    /** Public bounds check for callers validating a candidate point. */
    public boolean outOfBoundsPublic(int move) {
        return outOfBounds(move);
    }
```

(`branchChosen` is a field at `RenjuState.java:426`; `outOfBounds` is private at `:217`. Both additive — no behavior change. If a public bounds check is judged redundant, inline `convertMove`+grid-size check in the resolver instead; do NOT widen `outOfBounds` to public if the reviewer prefers the inline form.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd .../pente.org && ./justCompile && JAVA_HOME=... ant test-one -Dtest=org.pente.turnBased.web.test.RenjuTbContractTest`
Expected: all PASS.

- [ ] **Step 5: Run the engine suites to prove the FSM is untouched**

Run: `JAVA_HOME=... ant test-one -Dtest=org.pente.game.test.RenjuStateTest` then `...RenjuReconstructTest` then `org.pente.turnBased.test.TBGameRenjuPhaseTest`
Expected: all PASS unchanged.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/RenjuTbContract.java \
        dsg_src/java/org/pente/turnBased/web/test/RenjuTbContractTest.java \
        dsg_src/java/org/pente/game/RenjuState.java
git commit -m "feat(renju): RenjuTbContract.resolve — 3-action TB opening contract (TDD)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Rewrite the MoveServlet renju block to execute decisions

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java:409-569`

- [ ] **Step 1: Replace the whole `TB_RENJU && renjuAction != null` block**

Replace lines 409–569 (the entire block from `if (game.getGame() == GridStateFactory.TB_RENJU && renjuAction != null) {` through its closing `}` before the dpente `else`) with:

```java
                if (game.getGame() == GridStateFactory.TB_RENJU && renjuAction != null) {
                    RenjuState pending = RenjuState.reconstruct(
                            game, game.getRenjuSwaps(), game.getRenjuOffers());

                    RenjuTbContract.Decision decision;
                    try {
                        decision = RenjuTbContract.resolve(renjuAction, moves, pending);
                    } catch (RenjuTbContract.RenjuContractException bad) {
                        handleError(request, response, bad.getMessage());
                        return;
                    }

                    boolean storedStone = false;
                    switch (decision.kind) {
                        case TAKE_OVER:
                            tbGameStorer.renjuSwap(game, true);
                            break;

                        case PLACE:
                            if (decision.declineSwap) tbGameStorer.renjuSwap(game, false);
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), decision.stones[0]);
                            storedStone = true;
                            break;

                        case BRANCH_A:
                            if (decision.declineSwap) tbGameStorer.renjuSwap(game, false);
                            tbGameStorer.renjuBranch(game, false);
                            // move 5 — storeNewMove validates the 9x9 restriction
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), decision.stones[0]);
                            storedStone = true;
                            break;

                        case BRANCH_B:
                            if (decision.declineSwap) tbGameStorer.renjuSwap(game, false);
                            tbGameStorer.renjuBranch(game, true);
                            // offers already pre-validated by the resolver (no mutation on bad sets)
                            TBGame fresh = tbGameStorer.loadGame(game.getGid());
                            fresh.setRenjuOffers(decision.stones);
                            tbGameStorer.renjuOffers(fresh);
                            break;

                        case SELECT:
                            // atomic: move 5 (black, selected) then move 6 (white).
                            // dpente-start indexing: pass numMoves then numMoves+1
                            // (Cache reloads & ignores it; MySQL uses it directly).
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),     decision.stones[0]);
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves() + 1, decision.stones[1]);
                            storedStone = true;
                            break;
                    }

                    if (storedStone && message != null) {
                        message.setMoveNum(game.getNumMoves() + 1);
                        tbGameStorer.storeNewMessage(game.getGid(), message);
                    }

                } else
```

- [ ] **Step 2: Add the import**

At the top of `MoveServlet.java` with the other `org.pente.*` imports, add:
```java
import org.pente.turnBased.web.RenjuTbContract;
```
(Same package — the import is optional but harmless; skip if the file already lives in `org.pente.turnBased.web`.)

- [ ] **Step 3: Compile**

Run: `cd .../pente.org && ./justCompile`
Expected: clean. Confirm no remaining references to `"branch"`, `"offer"`, `"move4"`, or `moves[0] == 1` in the renju block:
Run: `grep -n '"branch"\|"offer"\|"move4"' dsg_src/java/org/pente/turnBased/web/MoveServlet.java`
Expected: no matches.

- [ ] **Step 4: Deploy + manual TB round-trip QA**

Run: `docker restart penteorg-pente.org-1` (after a build/deploy), then with test creds (iostest / app_tsetsoi) play one **Branch A** game (window-4 decline + 1 stone → window-5 decline + move 6 → complete) and one **Branch B** game (window-4 decline + 10 stones → opponent select 2 stones → complete), plus one **take-over at move 4** (swap → BRANCH → move 1). Verify each is a single request per decision (check the access log / network panel).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/MoveServlet.java
git commit -m "feat(renju): MoveServlet TB opening via RenjuTbContract; drop branch/offer/move4

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Rewrite `mobileGame.jsp` to the 3-action UI

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

No JS test harness exists for the JSP; verify in the browser (Step 5). The current functions are at the line ranges from the survey.

- [ ] **Step 1: `renjuSwapNo` → emit `move` (windows 1–3)**

Replace `renjuSwapNo` (≈1452–1460):
```javascript
   function renjuSwapNo()  {
      // Decline the swap and play your own next opening stone (one request).
      if (!(playedMove >= 0)) {
         alert("Place your next move before choosing not to swap.");
         return;
      }
      renjuPost("move", "" + playedMove);
   }
```
(Take-over `renjuSwapYes` stays `renjuPost("swap", "1")` — server ignores the payload; leave as-is.)

- [ ] **Step 2: Replace `renjuMoveFour` with a single `move` submit (window 4 / BRANCH)**

Replace `renjuMoveFour(declineSwap)` (≈1464–1478) with `renjuMoveBranch()` (no decline flag — the server infers decline from phase):
```javascript
   function renjuMoveBranch() {
      var n = renjuOfferList.length;
      if (n === 1) {
         var c = Math.floor(gridSize / 2);
         var x = renjuOfferList[0] % gridSize, y = Math.floor(renjuOfferList[0] / gridSize);
         if (Math.abs(x - c) > 4 || Math.abs(y - c) > 4) {
            alert("A single (continue) stone must be inside the 9×9 center.");
            return;
         }
      } else if (n !== 10) {
         alert("Place 1 stone to continue, or 10 stones to offer (currently " + n + ").");
         return;
      }
      renjuPost("move", renjuOfferList.join(","));
   }
```

- [ ] **Step 3: Delete `renjuSubmitOffers`**

Remove the entire `renjuSubmitOffers` function (≈1479–1485). The 10-offer submit is now `renjuMoveBranch` with 10 picks.

- [ ] **Step 4: `renjuSelect` → 2-stone select**

Replace `renjuSelect` (≈1486–1492). Introduce a 2-element selection array `renjuSel` (declare `var renjuSel = [];` beside the other renju state vars near `renjuOfferList`):
```javascript
   function renjuSelect() {
      if (renjuSel.length !== 2) {
         alert("Tap the offered black 5th move, then tap an empty point for your white 6th.");
         return;
      }
      renjuPost("select", renjuSel[0] + "," + renjuSel[1]);
   }
```

- [ ] **Step 5: Selection board-tap → collect 2 points; drop OFFERS/BRANCH phases**

In **both** the touchEnd block (≈849–871) and the boardClick block (≈1097–1119), change the phase condition to drop `OFFERS`/`BRANCH` and rework the SELECTION branch to collect 2 taps:
```javascript
         if (isRenju && (renjuPhase === "SELECTION"
               || (renjuPhase === "SWAP" && moves.length === 4))) {
            var rMove = j * gridSize + i;
            if (renjuPhase !== "SELECTION") {
               // SWAP@4: collect 1 (Branch A) or up to 10 (Branch B) candidate stones
               if (abstractBoard[i][j] !== 0) return;       // only empty points
               var oi = renjuOfferList.indexOf(rMove);
               if (oi >= 0) {
                  renjuOfferList.splice(oi, 1);             // tap again removes
               } else if (renjuOfferList.length < 10) {
                  if (renjuOfferList.length >= 1 && renjuIsSymmetricDup(rMove)) {
                     alert("That move is symmetric to one you've already offered.");
                     return;
                  }
                  renjuOfferList.push(rMove);
               }
               renjuRenderOffers();
               return;
            } else { // SELECTION: tap 1 = offered black 5th; tap 2 = empty white 6th
               if (renjuSel.length === 0) {
                  if (renjuOfferedMoves.indexOf(rMove) < 0) return; // 1st must be offered
                  renjuSel.push(rMove);
               } else if (renjuSel.length === 1) {
                  if (abstractBoard[i][j] !== 0 || rMove === renjuSel[0]) return; // 2nd empty & distinct
                  renjuSel.push(rMove);
               } else {
                  renjuSel = [];                            // 3rd tap resets the pair
                  if (renjuOfferedMoves.indexOf(rMove) >= 0) renjuSel.push(rMove);
               }
               renjuRenderSelection();
               return;
            }
         }
```
(Adjust `renjuRenderSelection` to highlight both `renjuSel` entries; keep `renjuRenderOffers` as-is. The symmetric-dup guard now only fires from the 2nd pick onward, matching the engine's stabilizer semantics.)

- [ ] **Step 6: Update the phase-gated button markup**

In the controls block (≈374–395):
- **Move-4 SWAP** (≈374–379): keep "Swap (take over)" → `renjuSwapYes()`; change the second button to `javascript:renjuMoveBranch()` labelled "Don't swap — place 1 or 10"; keep the `renjuOfferCount` `0/10` counter (it now tracks the move-4 picks).
- **Windows 1–3 SWAP** (≈380–384): unchanged ("Swap (take over)" / "Don't swap" → `renjuSwapYes()` / `renjuSwapNo()`).
- **BRANCH phase** (≈385–388): keep the block but point its button at `renjuMoveBranch()` (post-take-over: place 1 or 10), labelled "Place 1 or offer 10"; keep the `renjuOfferCount` counter.
- **OFFERS phase** (≈389–392): **delete the entire `else if (TBGame.RENJU_OFFERS...)` block** (unreachable now).
- **SELECTION phase** (≈393–395): relabel to "Place black's 5th + your 6th" → `renjuSelect()`.

- [ ] **Step 7: Reset `renjuSel` on phase entry**

Wherever `renjuOfferList` is reset on load/phase-change, also reset `renjuSel = [];` so a stale pair never leaks between games/phases.

- [ ] **Step 8: Browser verification**

Deploy and exercise in the mobile JSP: a Branch A game, a Branch B game (place-10 then opponent 2-tap select), and a move-4 take-over → BRANCH place. Confirm no `renjuMoveFour`/`renjuSubmitOffers`/OFFERS-button references remain:
Run: `grep -n 'renjuMoveFour\|renjuSubmitOffers\|RENJU_OFFERS' dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`
Expected: no matches.

- [ ] **Step 9: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "feat(renju): mobileGame.jsp 3-action TB opening UI (swap/move/2-stone select)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: iOS — `BoardViewController.m` action remap + 2-tap select

**Files:**
- Modify: `penteLive-iOS/test1/BoardViewController.m` (branch `renju-turnbased`)

iOS already folds the branch decision via `move4`; the change is (a) rename the wire action to `move`, (b) drop the leading decline/`1,` prefix, (c) fold the BRANCH phase into the same place-1-or-10 path, (d) make SELECTION collect two taps.

- [ ] **Step 1: Add a 2-point selection property**

Beside `renjuPickedOffers` (the `@property NSMutableArray`), add:
```objc
@property (nonatomic, strong) NSMutableArray *renjuSelectedPoints; // [black move5, white move6]
```
Initialize/clear it (= `[NSMutableArray array]`) wherever `renjuPickedOffers` is reset (the renju-reset path near lines 127–137).

- [ ] **Step 2: `renjuActionForCurrentPhaseFillingMoves` — emit `move` + 2-stone select**

In the state machine (≈1548–1592):
- For the Branch A path (was `move4`, moves `"1,<m5>"`): emit action `@"move"`, moves `[NSString stringWithFormat:@"%d", finalMove]` (no `1,` prefix).
- For the Branch B path (was `move4`, moves `"1,<o1..o10>"`): emit action `@"move"`, moves = the 10 offers joined by `,` (no `1,` prefix).
- For the SWAP decline+place (windows 1–3, was `swap` `"0,<m>"`): emit action `@"move"`, moves `[NSString stringWithFormat:@"%d", finalMove]`.
- For the take-over (was `swap` `"1"`): keep action `@"swap"`, moves `@"1"` (server ignores payload).
- Replace the SELECTION block (≈1585–1588):
```objc
if ([phase isEqualToString:@"SELECTION"]) {
    *outMoves = [NSString stringWithFormat:@"%@,%@",
                 self.renjuSelectedPoints[0], self.renjuSelectedPoints[1]];
    return @"select";
}
```

- [ ] **Step 3: SELECTION board-tap — collect two points**

Replace the single-tap auto-submit (≈1091–1099):
```objc
if ([self.renjuPhase isEqualToString:@"SELECTION"]) {
    if (self.renjuSelectedPoints.count == 0) {
        if (![self.renjuOffers containsObject:@(tapped)]) break; // 1st must be offered
        [self.renjuSelectedPoints addObject:@(tapped)];
        [self renderRenjuCandidates:self.renjuSelectedPoints];
    } else if (self.renjuSelectedPoints.count == 1) {
        if (board[/*x*/][/*y*/] != 0 || tapped == [self.renjuSelectedPoints[0] intValue]) break; // empty & distinct
        [self.renjuSelectedPoints addObject:@(tapped)];
        [self submitRenjuDecision]; // -> select, moves "m5,m6"
    } else {
        self.renjuSelectedPoints = [NSMutableArray array]; // reset on extra tap
    }
    break;
}
```
(Use the file's existing occupancy accessor for the white-6th empty check — match how `boardTap` already reads the board array elsewhere in this method.)

- [ ] **Step 4: Fold the BRANCH phase into place-1-or-10**

In `dPentePlayer1`/`dPentePlayer2`/`swap2Pass` and `renderRenjuOpeningUI` (≈789–875, 428–545): the post-take-over BRANCH phase should present the same "place 1 (Branch A) or 10 (Branch B)" affordance as the move-4 decline (set `renjuMove4BranchA`/`renjuMove4BranchB` based on whether 1 or 10 stones get placed, then submit `move`). Remove any UI/label implying a standalone branch choice that posts before placing stones. Relabel SELECTION from "Pick one offered move" → "Tap black's 5th, then your 6th" (≈858).

- [ ] **Step 5: Build**

Run the iOS build (Xcode or the project's `fastlane`/`xcodebuild` build lane). Confirm no `move4`/`@"branch"`/`@"offer"` wire strings remain:
Run: `grep -n '@"move4"\|@"branch"\|@"offer"' penteLive-iOS/test1/BoardViewController.m`
Expected: no matches (the local `renjuMove4BranchA/B` *flag* names may remain; only the wire-action strings must go).

- [ ] **Step 6: Manual TB QA on device/simulator** — Branch A, Branch B (2-tap select), move-4 take-over.

- [ ] **Step 7: Commit (in the iOS repo)**

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/penteLive-iOS
git add test1/BoardViewController.m
git commit -m "feat(renju): TB 3-action contract — move (1/10) + atomic 2-stone select

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Android — `BoardActivity` action remap + tests

**Files:**
- Modify: `pentelive-android/app/src/main/java/be/submanifold/pentelive/BoardActivity.java`
- Modify: `pentelive-android/app/src/test/java/be/submanifold/pentelive/GameRenjuUnitTest.java`
- Modify: `pentelive-android/app/src/test/java/be/submanifold/pentelive/net/OkHttpPenteApiTest.java`

Android currently uses the full multi-step path (`swap`, then a separate `branch`, then `offer`, then `select`). This is the largest client change.

- [ ] **Step 1 (TDD): update the URL-building unit tests first**

In `GameRenjuUnitTest.java`:
- `buildSubmitMoveUrlOmitsRenjuActionWhenNull` — keep (plain move, `renjuAction=null`); moves can stay `"130"`.
- `buildSubmitMoveUrlAppendsRenjuAction` — change the take-over case to assert `renjuAction=swap` with **moves `"1"`** (no appended stone). Add a new assertion for the decline+place case: `buildSubmitMoveUrl(..., "130", ..., "move")` contains `moves=130` and `renjuAction=move`.

In `OkHttpPenteApiTest.java`:
- `submitMoveAppendsRenjuAction` — change to `api.submitMove("999", "1", "", "swap")` and assert `renjuAction=swap` + `moves=1`. Add a case: `api.submitMove("999", "113,114,115,116,128,129,130,131,144,145", "", "move")` asserts `renjuAction=move` and the 10-CSV `moves`. Add a select case: `api.submitMove("999", "130,200", "", "select")` asserts `renjuAction=select` + `moves=130,200`.

Run: `cd /Users/waliedothman/mariposa/coding/pente.org-project/pentelive-android && ./gradlew testDebugUnitTest --tests '*GameRenjuUnitTest' --tests '*OkHttpPenteApiTest'`
Expected: FAIL (BoardActivity still emits old payloads / these are new assertions).

- [ ] **Step 2: Windows 1–3 decline → `move` (keep the stone)**

In the `playAsBlackButton` SWAP handler (≈119–152): windows 1–3 still reveal the board and collect the stone in `renjuBoxRadius` — unchanged. The **submit** of that stone (main submit button handler, ≈288–295) changes from:
```java
    moves = "0," + board.playedMove;
    renjuAction = "swap";
```
to:
```java
    moves = "" + board.playedMove;
    renjuAction = "move";
```
And remove the `board.playedMove == -1` guard ONLY for the take-over path (which no longer routes here). The take-over (`playAsWhiteButton` SWAP, ≈93–99) keeps `game.submitMove("1", msg(), "swap")` — no stone.

- [ ] **Step 3: Window-4 decline → place 1 or 10 → `move` (delete the standalone decline)**

In `playAsBlackButton` SWAP handler, the `window >= 4` branch currently does `game.submitMove("0", msg(), "swap")` (≈124). Replace it so it does NOT submit immediately; instead reveal the board for placing **1 (Branch A, 9×9) or up to 10 (Branch B)** candidate stones (reuse the offer-collection UI / `board.renjuPicks`), then the main submit posts `renjuAction=move` with the 1-or-10 CSV. The branch is inferred server-side by count.

- [ ] **Step 4: Delete BRANCH + OFFERS handling**

- Remove the two `"BRANCH".equals(game.renjuPhase)` blocks (`playAsWhiteButton` ≈101–105 submit `"1","branch"`, and `playAsBlackButton` ≈136–140 submit `"2","branch"`). BRANCH (post-take-over) now uses the same place-1-or-10 → `move` UI as the move-4 decline; route it there.
- Replace the OFFERS submit block (main submit, ≈303–318): it should fold into the move-4 `move` path. If the server never reports `OFFERS` (it doesn't, post-change), remove the `"OFFERS".equals(...)` branch entirely; keep `RenjuSymmetry.isValidOfferSet(arr)` as a **client-side UX pre-check** inside the move-4 place-10 submit.

- [ ] **Step 5: SELECTION → 2 taps → `move5,move6`**

Replace the SELECTION submit block (main submit, ≈319–325): require two collected points (the chosen offered black 5th + a white 6th empty/in-bounds/distinct point), then:
```java
    moves = sel5 + "," + sel6;
    renjuAction = "select";
```
Add the board-tap collection: first tap must be in `game.renjuOffers`; second tap must be an empty point ≠ first; a third tap resets. (Mirror the iOS two-tap UX; store in a `List<Integer>` on the board/activity.)

- [ ] **Step 6: Run unit tests**

Run: `./gradlew testDebugUnitTest --tests '*GameRenjuUnitTest' --tests '*OkHttpPenteApiTest'`
Expected: PASS. Confirm no old wire strings remain:
Run: `grep -rn '"branch"\|"offer"' app/src/main/java/be/submanifold/pentelive/BoardActivity.java`
Expected: no matches.

- [ ] **Step 7: Manual TB QA** (emulator/device): Branch A, Branch B (2-tap select), move-4 take-over.

- [ ] **Step 8: Commit (in the Android repo)**

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pentelive-android
git add app/src/main/java/be/submanifold/pentelive/BoardActivity.java \
        app/src/test/java/be/submanifold/pentelive/GameRenjuUnitTest.java \
        app/src/test/java/be/submanifold/pentelive/net/OkHttpPenteApiTest.java
git commit -m "feat(renju): TB 3-action contract — move (1/10) + atomic 2-stone select

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Docs — guide §2.4/§3/§3.6/§4 + both handoff intros + §10.4

**Files:**
- Modify: `pente.org/docs/renju-integration-guide.md`
- Modify: `penteLive-iOS/docs/renju-handoff.md`
- Modify: `pentelive-android/docs/renju-handoff.md`

- [ ] **Step 1: Rewrite guide §2.4 table (≈67–77)** to exactly three actions:

```markdown
| `renjuAction` | matching phase | `moves` payload | meaning |
|---|---|---|---|
| `swap` | SWAP | (none) | take over opponent's side (no stone). Branch/next stone follows as a `move`. |
| `move` | SWAP / BRANCH / MOVE | `<m>` | decline a pending swap (if any) + place: windows 1–3 → next stone; **move-4 / post-take-over → Branch A (move 5, 9×9)**; MOVE phase → plain stone |
| `move` | SWAP@4 / BRANCH | `<s1>,…,<s10>` | decline a pending swap (if any) + **Branch B**: branch + ten 5th-move offers, atomically (pre-validated; nothing persists if any offer is illegal) |
| `select` | SELECTION | `<move5>,<move6>` | **atomic 2-stone**: black move 5 (a selected Branch-B offer) + white move 6 → opening complete |
```

- [ ] **Step 2: Rewrite the §2.4 note (≈78)** — replace the "decline after move 4 carries no stone / branch comes next" sentence with: *"At move 4, declining is implicit in the `move` payload; the branch (A vs B) is inferred from the stone count (1 vs 10). The only standalone action is `swap` (take-over). The server pre-validates Branch-B offers and the 2-stone `select` before any mutation."*

- [ ] **Step 3: Rewrite §3 (≈129–131) and §3.6 (≈101–103)** per the survey's `changeNeeded` for those rows (swap windows; move-4 by stone count; the new atomic `select`; mobileGame.jsp control labels). Remove "branch step (no stone)" and the OFFERS/BRANCH control mentions.

- [ ] **Step 4: Rewrite §4 (≈135–136)** — the contract is "three `renjuAction` values (`swap`/`move`/`select`)".

- [ ] **Step 5: iOS handoff (`§9 intro`, line 9) and Android handoff (`§10 intro`, line 9)** — replace "five actions: swap/branch/offer/select + the move4 fold" with "three actions: `swap` (take-over, no stone) · `move` (1 or 10 stones; branch inferred by count) · `select` (atomic 2-stone: move 5 + move 6)".

- [ ] **Step 6: Android handoff §10.4 table (≈327–336)** — replace with the 3-row table (per the survey's `changeNeeded`). Do the same for any equivalent table in the iOS handoff (search it for a `branch`/`offer`/`move4` table).

- [ ] **Step 7: Residual sweep** across all three docs:
Run: `grep -rn 'move4\|renjuAction=branch\|renjuAction=offer\|\bOFFERS\b' pente.org/docs/renju-integration-guide.md penteLive-iOS/docs/renju-handoff.md pentelive-android/docs/renju-handoff.md`
Expected: only historical/LIVE-context mentions remain (LIVE transport is unchanged); no TB-contract reference to `branch`/`offer`/`move4` as a client action. Fix any that slip through.

- [ ] **Step 8: Commit** (guide in pente.org; each handoff in its own repo — three commits, same footer).

```bash
# pente.org
git add docs/renju-integration-guide.md
git commit -m "docs(renju): TB §2.4 → 3-action contract (swap/move/2-stone select)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Final verification + branch wrap-up

- [ ] **Step 1:** Backend full renju suite green:
  `JAVA_HOME=... ant test-one -Dtest=org.pente.turnBased.web.test.RenjuTbContractTest` + `...RenjuStateTest` + `...RenjuReconstructTest` + `org.pente.turnBased.test.TBGameRenjuPhaseTest`.
- [ ] **Step 2:** Android: `./gradlew testDebugUnitTest` green.
- [ ] **Step 3:** iOS: build + `RenjuOfferSymmetryTests` green.
- [ ] **Step 4:** End-to-end: one real Branch A, one Branch B, one move-4 take-over, played across the JSP client and (if available) one mobile client, against the deployed server — confirm each opening decision is a single request and the games complete.
- [ ] **Step 5:** Per-repo: do **not** stage stray files (Dockerfile, docker-compose*, log4j.properties, CLAUDE.md, node-compile-cache, .playwright-mcp, submodule pointers). Push only when the user asks; open/refresh the three PRs and cross-link them.

---

## Self-Review

- **Spec coverage:** swap/move(1)/move(10)/select(2) + deletions + all five surfaces (backend resolver, MoveServlet, JSP, iOS, Android) + docs — each has a task. Engine-unchanged assertion is Task 2 Step 5.
- **Placeholder scan:** the only intentional gaps are file-local accessor details (iOS board-occupancy read; Android tap-collection list type) flagged to "match existing code" — because the exact accessor name lives in code not fully quoted here; every *contract* decision and Java/JS snippet is complete.
- **Type consistency:** `Decision`/`Kind`/`RenjuContractException` names match between Tasks 1, 2, 3. `RenjuState` additive methods (`isBranchChosen`, `outOfBoundsPublic`, existing `wouldAcceptFifthOffers`/`getOfferedFifthMoves`) are used consistently. Wire action set `{swap, move, select}` identical across backend, JSP, iOS, Android, docs.
- **Risk note:** the resolver pre-validates Branch-B offers (fixes the current move4 mutate-then-validate gap) and the 2-stone select atomically — both align with spec invariant #1.
