# Renju TB Mobile UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive the Taraguchi-10 opening (swap / branch / 10-offer / selection) from `gameServer/tb/mobileGame.jsp`, render the 15×15 board with central-square hinting, and expose the opening state in the JSON endpoint — all via one shared `TBGame.getRenjuPhase()`.

**Architecture:** `TBGame.getRenjuPhase()` reconstructs the engine once and returns a phase string consumed by both the HTML JSP and the Gson `GameResponse`. `mobileGame.jsp` reads the phase, renders the matching opening UI (mirroring the dPente/swap2 button block), and posts opening actions to the existing `MoveServlet` `renjuAction` contract. Server validation is authoritative; client hinting is UX.

**Tech Stack:** Java (Tomcat), JSP + canvas JavaScript, Gson, JUnit 3, Ant.

**Spec:** `docs/superpowers/specs/2026-06-14-renju-tb-mobile-ui-design.md`

## Build & Test Commands

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest
```

Run from `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`.

**Testing reality:** `TBGame.getRenjuPhase()` is pure and unit-tested here. `GameResponse` is compile-verified. **`mobileGame.jsp` is NOT compiled by `./justCompile`** (JSPs compile at Tomcat runtime), so JSP tasks are verified by a self-review checklist + the Java still compiling; runtime/visual verification is a manual follow-up (deploy + open a Renju TB game). Each JSP task lists the exact self-review checks instead of a build gate.

## File Structure

- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java` — add phase constants + `getRenjuPhase()`.
- Create: `dsg_src/java/org/pente/turnBased/test/TBGameRenjuPhaseTest.java`.
- Modify: `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java` — 3 JSON fields.
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp` — board size, exposed vars, opening UI, tap handling, hinting.

---

## Task 1: TBGame.getRenjuPhase()

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java`
- Test: `dsg_src/java/org/pente/turnBased/test/TBGameRenjuPhaseTest.java`

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/turnBased/test/TBGameRenjuPhaseTest.java`:

```java
package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.*;

import org.pente.game.GridStateFactory;
import org.pente.game.RenjuOpeningState;
import org.pente.turnBased.TBGame;

public class TBGameRenjuPhaseTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{TBGameRenjuPhaseTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(TBGameRenjuPhaseTest.class);
    }

    public TBGameRenjuPhaseTest(String name) {
        super(name);
    }

    private int xy(int x, int y) {
        return x + y * 15;
    }

    private TBGame renju(int[] moves, int swaps, int[] offers) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_RENJU);
        List<Integer> list = new ArrayList<Integer>();
        for (int m : moves) list.add(m);
        g.setMoves(list);
        g.setRenjuSwaps(swaps);
        g.setRenjuOffers(offers);
        return g;
    }

    public void testNonRenjuReturnsNull() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        assertNull(g.getRenjuPhase());
    }

    public void testSwapPendingAfterMove1() {
        TBGame g = renju(new int[]{xy(7, 7)}, 0, null); // 1 move, all pending
        assertEquals(TBGame.RENJU_SWAP, g.getRenjuPhase());
    }

    public void testMovePhaseAfterSwapResolved() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO; // move-1 swap declined
        TBGame g = renju(new int[]{xy(7, 7)}, st.encode(), null);
        assertEquals(TBGame.RENJU_MOVE, g.getRenjuPhase()); // awaiting move 2
    }

    public void testBranchPendingAfterMove4() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), null);
        assertEquals(TBGame.RENJU_BRANCH, g.getRenjuPhase());
    }

    public void testOffersPendingBranchB() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), null);
        assertEquals(TBGame.RENJU_OFFERS, g.getRenjuPhase());
    }

    public void testSelectionPendingBranchB() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.YES;
        int[] offers = {0,2,4,6,8,10,12,14,16,18};
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8)}, st.encode(), offers);
        assertEquals(TBGame.RENJU_SELECTION, g.getRenjuPhase());
    }

    public void testCompleteAfterMove6BranchA() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = st.swap2 = st.swap3 = st.swap4 = RenjuOpeningState.NO;
        st.branch = RenjuOpeningState.NO; // Branch A
        st.swap5 = RenjuOpeningState.NO;
        TBGame g = renju(new int[]{xy(7,7), xy(8,8), xy(9,7), xy(6,8), xy(11,7), xy(0,0)},
                st.encode(), null);
        assertEquals(TBGame.RENJU_COMPLETE, g.getRenjuPhase());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest
```
Expected: FAIL — `getRenjuPhase` / `RENJU_*` constants not defined.

- [ ] **Step 3: Implement getRenjuPhase**

In `dsg_src/java/org/pente/turnBased/TBGame.java`, add the constants + method immediately after the `setRenjuOffers(int[])` method (around line 538):

```java
    public static final String RENJU_SWAP = "SWAP";
    public static final String RENJU_BRANCH = "BRANCH";
    public static final String RENJU_OFFERS = "OFFERS";
    public static final String RENJU_SELECTION = "SELECTION";
    public static final String RENJU_MOVE = "MOVE";
    public static final String RENJU_COMPLETE = "COMPLETE";

    /**
     * Derived Taraguchi-10 opening phase for the mobile/JSON views. Returns null
     * for non-Renju games. Reconstructs the engine once from the persisted
     * (moves + renjuSwaps + renjuOffers) and maps its pending decision to a
     * phase string. Not serialized; computed on demand (no cost for non-Renju).
     */
    public String getRenjuPhase() {
        if (game != GridStateFactory.TB_RENJU) {
            return null;
        }
        org.pente.game.RenjuState rs =
                org.pente.game.RenjuState.reconstruct(this, renjuSwaps, renjuOffers);
        if (rs.isAwaitingSwapDecision())   return RENJU_SWAP;
        if (rs.isAwaitingBranchChoice())   return RENJU_BRANCH;
        if (rs.isAwaitingFifthOffers())    return RENJU_OFFERS;
        if (rs.isAwaitingFifthSelection()) return RENJU_SELECTION;
        if (rs.isOpeningComplete())        return RENJU_COMPLETE;
        return RENJU_MOVE;
    }
```

> `game`, `renjuSwaps`, `renjuOffers` are existing fields; `GridStateFactory` is referenced elsewhere in this file. `RenjuState`/`RenjuOpeningState` are used fully-qualified (the file already references `org.pente.game.RenjuOpeningState`).

- [ ] **Step 4: Run test to verify it passes**

```bash
./justCompile && ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest
```
Expected: PASS (OK, 7 tests).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGame.java \
        dsg_src/java/org/pente/turnBased/test/TBGameRenjuPhaseTest.java
git commit -m "feat(renju): TBGame.getRenjuPhase derived opening-phase accessor

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: GameResponse JSON fields

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java`

Compile-verified (mirrors the `dPenteState`/`swap2pass` path).

- [ ] **Step 1: Add the fields**

In `GameResponse.java`, after the `public final Boolean swap2pass;` field (line 44), add:

```java
    public final String renjuPhase;    // TB_RENJU only: SWAP|BRANCH|OFFERS|SELECTION|MOVE|COMPLETE, else null
    public final String renjuOffers;   // TB_RENJU Branch B: comma-separated offered moves, else null
    public final Integer renjuSwaps;   // TB_RENJU: packed opening word, else null
```

- [ ] **Step 2: Extend the constructor**

Change the private constructor signature (line 67-73) to add the three params at the end, and assign them. Replace the parameter list's final `Boolean swap2pass)` with:

```java
                         Boolean swap2pass,
                         String renjuPhase, String renjuOffers, Integer renjuSwaps) {
```

and after `this.swap2pass = swap2pass;` (line 95) add:

```java
        this.renjuPhase = renjuPhase;
        this.renjuOffers = renjuOffers;
        this.renjuSwaps = renjuSwaps;
```

- [ ] **Step 3: Populate in build()**

In `build(...)`, just before the `return new GameResponse(` (line 150), add:

```java
        boolean isRenju = !tbGame.isCompleted()
                && tbGame.getGame() == GridStateFactory.TB_RENJU;
        String renjuPhase = isRenju ? tbGame.getRenjuPhase() : null;
        String renjuOffersStr = null;
        if (isRenju && tbGame.getRenjuOffers() != null) {
            StringBuilder ro = new StringBuilder();
            int[] offers = tbGame.getRenjuOffers();
            for (int i = 0; i < offers.length; i++) {
                if (i > 0) ro.append(',');
                ro.append(offers[i]);
            }
            renjuOffersStr = ro.toString();
        }
        Integer renjuSwaps = isRenju ? Integer.valueOf(tbGame.getRenjuSwaps()) : null;
```

Then change the final two constructor arguments (currently ending `isDPente ? tbGame.didSwap2Pass() : null` on line 172) to append the new ones:

```java
                isDPente ? tbGame.didSwap2Pass() : null,
                renjuPhase, renjuOffersStr, renjuSwaps
```

- [ ] **Step 4: Update the historic-game constructor call**

In `buildHistoric(...)` the constructor call (lines 194-205) passes trailing nulls. Append three more `null`s so the arg count matches. Change the final line `null, null, null, null, null, null` (line 204) to:

```java
                null, null, null, null, null, null,
                null, null, null
```

- [ ] **Step 5: Compile**

```bash
./justCompile
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/mobile/GameResponse.java
git commit -m "feat(renju): expose opening phase/offers/swaps in JSON GameResponse

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: mobileGame.jsp — board size + exposed JS vars

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

JSP not compiled by `./justCompile`. Self-review checklist below is the gate.

- [ ] **Step 1: Add the 15×15 board size + scriptlet vars**

In the top scriptlet, replace the `gridSize` block (lines 120-125):

```jsp
   int gridSize = 19;
   if (game.getGame() == GridStateFactory.TB_GO9) {
      gridSize = 9;
   } else if (game.getGame() == GridStateFactory.TB_GO13) {
      gridSize = 13;
   }
```

with:

```jsp
   int gridSize = 19;
   if (game.getGame() == GridStateFactory.TB_GO9) {
      gridSize = 9;
   } else if (game.getGame() == GridStateFactory.TB_GO13) {
      gridSize = 13;
   } else if (game.getGame() == GridStateFactory.TB_RENJU) {
      gridSize = 15;
   }

   // Renju opening state exposed to the client script (empty/blank for non-Renju)
   String renjuPhaseStr = "";
   String renjuOffersJs = "";
   if (game.getGame() == GridStateFactory.TB_RENJU) {
      String ph = game.getRenjuPhase();
      renjuPhaseStr = ph == null ? "" : ph;
      if (game.getRenjuOffers() != null) {
         StringBuilder sb = new StringBuilder();
         int[] ro = game.getRenjuOffers();
         for (int k = 0; k < ro.length; k++) {
            if (k > 0) sb.append(',');
            sb.append(ro[k]);
         }
         renjuOffersJs = sb.toString();
      }
   }
```

- [ ] **Step 2: Add the JS vars**

After the `var gridSize = <%=gridSize%>;` line (line 597), add:

```jsp
   var isRenju = game === <%= GridStateFactory.TB_RENJU %>;
   var renjuPhase = "<%=renjuPhaseStr%>";
   var renjuOfferedMoves = [<%=renjuOffersJs%>];  // persisted offers (for SELECTION)
   var renjuOfferList = [];                         // client picks (Branch B OFFERS)
```

- [ ] **Step 3: Self-review checklist (no build gate for JSP)**

Verify by reading the diff:
- [ ] `gridSize` is 15 only for `TB_RENJU`; other games unchanged.
- [ ] `renjuPhaseStr`/`renjuOffersJs` are computed only inside the `TB_RENJU` guard; blank otherwise.
- [ ] The new JS vars sit inside the `<script>` block alongside the existing `var gridSize` and use valid JSP expression syntax.
- [ ] `./justCompile` still succeeds (confirms the Java referenced from scriptlets — `getRenjuPhase`, `getRenjuOffers` — resolves).

Run: `./justCompile` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "feat(renju): mobileGame.jsp 15x15 board + exposed opening vars

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: mobileGame.jsp — swap & branch UI + post helpers

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

- [ ] **Step 1: Add the Renju decision buttons**

After the swap2pass button block closes (line 332 `<% } %>`), and before the `&nbsp;` (line 333), insert:

```jsp
                     <% if (game.getGame() == GridStateFactory.TB_RENJU && !"false".equals(myTurn)) {
                        String renjuPh = game.getRenjuPhase();
                        if (TBGame.RENJU_SWAP.equals(renjuPh)) { %>
                     <a class="boldbuttons" href="javascript:renjuSwapYes();"
                        style="margin-right:5px;"><span>Swap (take over)</span></a>
                     <a class="boldbuttons" href="javascript:renjuSwapNo();"
                        style="margin-right:5px;"><span>Don't swap</span></a>
                     <% } else if (TBGame.RENJU_BRANCH.equals(renjuPh)) { %>
                     <a class="boldbuttons" href="javascript:renjuBranchA();"
                        style="margin-right:5px;"><span>Branch A — place 5th in 9×9</span></a>
                     <a class="boldbuttons" href="javascript:renjuBranchB();"
                        style="margin-right:5px;"><span>Branch B — offer 10 moves</span></a>
                     <% } else if (TBGame.RENJU_OFFERS.equals(renjuPh)) { %>
                     <span id="renjuOfferCount" style="margin-right:5px;">0/10</span>
                     <a class="boldbuttons" href="javascript:renjuSubmitOffers();"
                        style="margin-right:5px;"><span>Submit 10 offers</span></a>
                     <% } else if (TBGame.RENJU_SELECTION.equals(renjuPh)) { %>
                     <a class="boldbuttons" href="javascript:renjuSelect();"
                        style="margin-right:5px;"><span>Choose this 5th move</span></a>
                     <% } %>
                     <% } %>
```

> Requires `TBGame` to be imported in the JSP. It already references `game.getDPenteState()` etc. so `org.pente.turnBased.TBGame` is imported; if not, add `<%@ page import="org.pente.turnBased.TBGame" %>` near the other imports at the top.

- [ ] **Step 2: Add the JS post helpers**

After the `swap2pass()` JS function (ends ~line 1218), add:

```javascript
   function renjuPost(action, moveStr) {
      window.open("/gameServer/tb/game?command=move&gid="+<%=game.getGid()%>+
      cycleStr + hideStr + "&renjuAction=" + action + "&moves=" + moveStr +
      "&message=" + encodeURIComponent(document.getElementById('message').value), "_self"
   )
      ;
   }
   function renjuSwapYes() { renjuPost("swap", "1"); }
   function renjuSwapNo()  { renjuPost("swap", "0"); }
   function renjuBranchA() { renjuPost("branch", "1"); }
   function renjuBranchB() { renjuPost("branch", "2"); }
   function renjuSubmitOffers() {
      if (renjuOfferList.length !== 10) {
         alert("Pick exactly 10 offered moves (currently " + renjuOfferList.length + ").");
         return;
      }
      renjuPost("offer", renjuOfferList.join(","));
   }
   function renjuSelect() {
      if (playedMove < 0 || renjuOfferedMoves.indexOf(playedMove) < 0) {
         alert("Tap one of the highlighted offered points first.");
         return;
      }
      renjuPost("select", "" + playedMove);
   }
```

- [ ] **Step 3: Self-review checklist**

- [ ] Buttons render only for `TB_RENJU` and only on the player's turn; each phase shows exactly its controls.
- [ ] `renjuPost` mirrors the existing `dPentePlayAsP1` window.open shape (uses `cycleStr`, `hideStr`, `message`), adding `&renjuAction=`.
- [ ] `swap`→1/0, `branch`→1(A)/2(B) match `MoveServlet` (`moves[0]==1` swap; `moves[0]==2` Branch B).
- [ ] `./justCompile` still succeeds.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "feat(renju): mobileGame.jsp swap/branch opening controls

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: mobileGame.jsp — 10-offer picker + selection (tap handling)

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

- [ ] **Step 1: Intercept taps for OFFERS / SELECTION**

In the touch/tap handler, at the very start of the in-bounds block (right after line 786 `playedMove = j * gridSize + i;`), insert the Renju interception:

```javascript
         if (isRenju && (renjuPhase === "OFFERS" || renjuPhase === "SELECTION")) {
            var rMove = j * gridSize + i;
            if (renjuPhase === "OFFERS") {
               if (abstractBoard[i][j] !== 0) return;       // only empty points
               var oi = renjuOfferList.indexOf(rMove);
               if (oi >= 0) {
                  renjuOfferList.splice(oi, 1);             // tap again removes
               } else if (renjuOfferList.length < 10) {
                  renjuOfferList.push(rMove);
               }
               renjuRenderOffers();
               return;
            } else { // SELECTION
               if (renjuOfferedMoves.indexOf(rMove) < 0) return; // only offered points
               playedMove = rMove;
               renjuRenderSelection();
               return;
            }
         }
```

- [ ] **Step 2: Add the offer/selection render helpers**

After the tap handler function closes (line 880-881), add:

```javascript
   function renjuRedrawBoard() {
      resetAbstractBoard(abstractBoard);
      drawUntilMove = moves.length;
      replayGame(abstractBoard, moves, drawUntilMove);
      boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
      boardContext.fill();
      drawGrid(boardContext, boardColor, gridSize, true);
      drawGame();
   }
   function renjuRenderOffers() {
      renjuRedrawBoard();
      for (var k = 0; k < renjuOfferList.length; k++) {
         var m = renjuOfferList[k];
         drawRedDot(m % gridSize, Math.floor(m / gridSize));
      }
      var cnt = document.getElementById('renjuOfferCount');
      if (cnt) cnt.innerText = renjuOfferList.length + "/10";
   }
   function renjuRenderSelection() {
      renjuRedrawBoard();
      for (var k = 0; k < renjuOfferedMoves.length; k++) {
         var m = renjuOfferedMoves[k];
         drawRedDot(m % gridSize, Math.floor(m / gridSize));
      }
   }
```

- [ ] **Step 3: Render the offered/candidate points on load**

In the `init()` / board-ready path (where `drawGame()` is first called after page load — search for the initial `drawGame();` invocation in the `<script>` that runs on load), append:

```javascript
   if (isRenju && renjuPhase === "SELECTION") {
      renjuRenderSelection();
   }
```

(OFFERS starts with an empty `renjuOfferList`, so the count shows `0/10` from the JSP and no markers are needed until the player taps.)

- [ ] **Step 4: Self-review checklist**

- [ ] In OFFERS, tapping an empty point toggles it in `renjuOfferList` (cap 10), updates `#renjuOfferCount`, and draws a marker; tapping a filled point or a stone is ignored.
- [ ] In SELECTION, only one of `renjuOfferedMoves` is tappable; it sets `playedMove`; candidates are highlighted on load.
- [ ] `renjuRedrawBoard` uses the same redraw calls (`resetAbstractBoard`/`replayGame`/`drawGrid`/`drawGame`) the tap handler already uses — no new draw primitives invented.
- [ ] Non-Renju games never enter these branches (`isRenju` guard).
- [ ] `./justCompile` still succeeds.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "feat(renju): mobileGame.jsp 10-offer picker + 5th-move selection

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: mobileGame.jsp — central-square hinting (MOVE phase)

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

- [ ] **Step 1: Block out-of-square taps during MOVE**

In the tap handler, immediately after the OFFERS/SELECTION interception added in Task 5 (still right after `playedMove = j * gridSize + i;`), insert:

```javascript
         if (isRenju && renjuPhase === "MOVE") {
            var rCenter = Math.floor(gridSize / 2);
            var rRadius = renjuMoveRadius(moves.length);
            if (rRadius >= 0 &&
                (Math.abs(i - rCenter) > rRadius || Math.abs(j - rCenter) > rRadius)) {
               return; // outside the allowed central square for this opening move
            }
         }
```

- [ ] **Step 2: Add the radius helper**

Next to `renjuRedrawBoard` (added in Task 5), add:

```javascript
   function renjuMoveRadius(n) {
      // half-width of the allowed central square by opening move number
      if (n === 0) return 0;   // move 1: center only
      if (n === 1) return 1;   // move 2: 3x3
      if (n === 2) return 2;   // move 3: 5x5
      if (n === 3) return 3;   // move 4: 7x7
      if (n === 4) return 4;   // move 5 (Branch A): 9x9
      return -1;               // move 6+: unrestricted
   }
```

- [ ] **Step 3: Self-review checklist**

- [ ] Radii match the spec table / engine `withinOpeningSquare` (0/1/2/3/4 for moves 1-5, unrestricted after).
- [ ] Center is `floor(gridSize/2)` = 7 for the 15×15 Renju board.
- [ ] The guard only fires for `isRenju && renjuPhase === "MOVE"`; ordinary placement, all other games, and post-opening Renju are unaffected.
- [ ] Server still validates (this is hinting only) — an out-of-square move that slips through is rejected by `MoveServlet`.
- [ ] `./justCompile` still succeeds.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "feat(renju): mobileGame.jsp central-square hinting for opening moves

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Full regression

**Files:** none (verification)

- [ ] **Step 1: Compile + run the Java unit suites**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile \
  && ant test-one -Dtest=org.pente.turnBased.test.TBGameRenjuPhaseTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuStateTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuForbiddenPointFinderTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuFactoryTest \
  && ant test-one -Dtest=org.pente.game.test.RenjuOpeningStateTest
```
Expected: all PASS.

- [ ] **Step 2: Update spec status**

In `docs/superpowers/specs/2026-06-14-renju-tb-mobile-ui-design.md`, change `Status: approved-pending-review` → `Status: implemented (mobile JSP + JSON; manual render pending)`.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-06-14-renju-tb-mobile-ui-design.md
git commit -m "docs(renju): mark TB mobile UI implemented

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- `TBGame.getRenjuPhase()` (shared source of truth) → Task 1. ✓
- JSON `renjuPhase`/`renjuOffers`/`renjuSwaps` → Task 2. ✓
- `gridSize=15` + coordinate labels (first-15 slice already works) → Task 3. ✓
- Exposed JS vars (`renjuPhase`, offers, `isRenju`) → Task 3. ✓
- Swap / branch UI + posts → Task 4. ✓
- 10-offer tap-to-add (counter, toggle, submit-at-10) + selection → Task 5. ✓
- Central-square hinting on MOVE → Task 6. ✓
- Wire protocol = existing `renjuAction` (no servlet change) → Tasks 4-5 post it. ✓
- Out of scope (desktop game.jsp, React, live/ServerTable, AI) → untouched. ✓

**Placeholder scan:** none. JSP tasks state the no-build-gate reality and use concrete self-review checklists + the exact draw primitives (`drawRedDot`/`replayGame`/etc.) read from the file, not invented.

**Type consistency:** phase constants `RENJU_SWAP/BRANCH/OFFERS/SELECTION/MOVE/COMPLETE` defined in Task 1 used in Tasks 2-6; JS `renjuPhase`/`renjuOfferList`/`renjuOfferedMoves`/`renjuPost`/`renjuMoveRadius`/`renjuRedrawBoard` names consistent across Tasks 3-6; `getRenjuPhase`/`getRenjuOffers`/`getRenjuSwaps` match `TBGame`.

**Note:** `TBGame` no-arg constructor + `setGame`/`setMoves`/`setRenjuSwaps`/`setRenjuOffers` are used by the Task 1 test; `setMoves(List)`, `setRenjuSwaps`, `setRenjuOffers` are confirmed present and `setGame` is used by `MySQLTBGameStorer.fillGame`. If `new TBGame()` is unavailable, the implementer uses the constructor the storer uses and adapts — but no other task depends on the test's construction style.
