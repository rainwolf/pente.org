# Renju Live Opening Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teach the live (WebSocket + raw-TCP) game server to drive the Renju **Taraguchi-10** opening. Add three inbound `…TableEvent` types and their `ServerTable` handlers so the server accepts, validates (via the already-tested `RenjuState`), applies, echoes, and snapshots each opening decision — the per-window swap, the Branch-A decline+move-5, the Branch-B ten offers, and the selection — keeping the seat↔color binding correct across swaps.

**Architecture:** Three plain data-carrier events (`DSGRenjuTaraguchiSwapTableEvent`, `DSGRenjuTaraguchiOffer10TableEvent`, `DSGRenjuTaraguchi10Select1TableEvent`) extending `AbstractDSGTableEvent`, registered once in `DSGEventWrapper` (one private field each — the Gson codec + `getEncodedEvent()` reflect over fields, so this single registration serves **both** the TCP-socket and WebSocket front-ends). `SynchronizedServerTable.callServerTable` gains three `case` arms routing to three new `ServerTable` handlers. The handlers mirror the proven `handleMove` / `handleSwap` / `handleSwap2Pass` patterns: accumulate `int error`, mutate nothing on failure, emit `DSGMoveTableErrorEvent` to the sender, and on success drive the `RenjuState` opening hooks, swap both seat arrays on `swap=true`, and echo the decision. Stone placement always rides the single existing `DSGMoveTableEvent` broadcast path (delegated to `handleMove`, or a small reproduced tail where the engine already `addMove`d). A guarded `handleJoin` step re-sends the ten offers to a client that (re)joins mid-offer.

**Tech Stack:** Java (Tomcat), JUnit 3, Ant, WebSocket+TCP DSGEvent layer.

**Spec:** docs/superpowers/specs/2026-06-15-renju-live-opening-routing-design.md

---

## Build & Test Commands
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
ant test-one -Dtest=org.pente.game.test.<TestClass>
```
Run from `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org`. A production backend runs on localhost and `./justCompile` syncs the compiled classes into the volume-mounted `deployClasses/`; **`./justCompile` alone is enough to verify these changes compile.** To see Java changes take effect on the live server you must also `docker restart penteorg-pente.org-1` (Tomcat caches classes) — only needed for the manual WebSocket round-trip in Task 7, not for compile verification.

## Testing reality

The opening **rules** (phase legality, forbidden-point blocking, symmetric-offer rejection, swap-decision encoding) already live in `RenjuState` and are covered by the existing unit suites (`RenjuStateTest`, `RenjuOpeningStateTest`, `RenjuReconstructTest`, `RenjuFactoryTest`, `RenjuForbiddenPointFinderTest`, `TBGameRenjuPhaseTest`) — **not re-tested here.**

The **new** code is event-routing + seat remap inside `ServerTable`. Per the extracted test-harness search, there is **NO `ServerTable` unit-test harness** in the repo (`org/pente/gameServer/server/test/` holds only `LoginLoadTest` (socket load test) and `TestAI` (AI init) — neither instantiates `ServerTable`). `ServerTable` is wired to live timers, routers, storers, and a ping manager with no constructor seam, so fabricating a unit test would require inventing infrastructure. Therefore the three new handlers and the join snapshot are **compile-verified** and exercised by a **documented manual WebSocket round-trip** (Task 7).

The **one** piece of new logic that *is* engine-level and unit-testable — `RenjuState.offerFifthMoves(int[])`, the atomic ten-offer wrapper — gets real JUnit tests (Task 4), added to `RenjuReconstructTest` (whose `xy(...)` helper and 4-move Branch setup are already known-good from the archival work). No fabricated `ServerTable`/DB/integration tests.

## File Structure

- **Create:** `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiSwapTableEvent.java` — swap-window event (`boolean swap`, `int move`).
- **Create:** `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiOffer10TableEvent.java` — Branch-B ten-offer event (`int[] moves`).
- **Create:** `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchi10Select1TableEvent.java` — fifth-move selection event (`int move`).
- **Modify:** `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java` — three private fields + getter/setter pairs (codec registration, shared by both transports).
- **Modify:** `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java` — three dispatch `case` arms in `callServerTable`.
- **Modify:** `dsg_src/java/org/pente/gameServer/server/ServerTable.java` — three handlers (`handleRenjuSwap`, `handleRenjuOffer10`, `handleRenjuSelect1`), one move-broadcast helper (`broadcastRenjuFifthMove`), one join helper (`sendRenjuBranchBOffers`), and the `handleJoin` call site.
- **Modify:** `dsg_src/java/org/pente/game/RenjuState.java` — `offerFifthMoves(int[])` atomic Branch-B offer wrapper.
- **Test:** `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java` — atomic-rollback tests for `offerFifthMoves`.
- **Modify:** `docs/superpowers/specs/2026-06-15-renju-live-opening-routing-design.md` — flip Status to implemented (Task 7).
- **Modify:** `docs/renju-integration-guide.md` — move sub-project 2 from "Still deferred" to done (Task 7).

### Move-broadcast policy (single source of truth — read before Tasks 3–6)

To avoid double-placing a stone on the client, the implementation follows ONE rule:

- **`DSGMoveTableEvent` (broadcast to the table) is the SOLE stone-placement event a client acts on.** It is produced exactly once per placed stone — by delegating to `handleMove` (swap-decline + bundled move) or by the small reproduced tail in `broadcastRenjuFifthMove` (Branch-B selection, where `RenjuState.selectFifthMove` has already `addMove`d).
- **The three Renju echo events are opening-phase DECISION signals, broadcast via `broadcastMainRoom` (mirroring `handleSwap`/`handleSwap2Pass`).** A client updates its opening state machine from them (swap accepted/declined, the ten offers, which offer became move 5) but **never places a stone from them.** The `move` field carried on the swap/select echoes is informational/cross-check only.
  - **Recipient-set caveat:** `broadcastMainRoom` iterates `playersInMainRoom` (the lobby list, ServerTable ≈357) while the bundled stone (`DSGMoveTableEvent` via `handleMove` / `broadcastRenjuFifthMove`) uses `broadcastTable`, which iterates `playersInTable` (≈345). The echo and its stone therefore target two *different* recipient sets. This is exactly what shipped `handleSwap`/`handleSwap2Pass` do, and it works because a seated player (the opponent) is a member of **both** lists, so the opponent always receives both. The only divergence is a client in one list but not the other (e.g. a spectator who is in the table list but not the main-room list, or vice-versa) could get the stone without the decision. If exact parity is required, switch the three echoes to `broadcastTable` so each echo and its stone share one recipient set; Task 7 verifies the opponent/spectators actually receive every echo before deciding.

This is why there is no double broadcast: `swap=true` and `offer10` place no stone (echo only); `swap=false` and `select1` place exactly one stone via the `DSGMoveTableEvent` path while the echo carries only the decision.

---

## Task 1: Three event classes + DSGEventWrapper registration

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiSwapTableEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiOffer10TableEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchi10Select1TableEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java`

- [ ] **Step 1: Create `DSGRenjuTaraguchiSwapTableEvent`**

Write `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiSwapTableEvent.java`:

```java
package org.pente.gameServer.event;

public class DSGRenjuTaraguchiSwapTableEvent extends AbstractDSGTableEvent {

    private boolean swap;
    private int move;

    public DSGRenjuTaraguchiSwapTableEvent() {
        super();
    }

    /**
     * A Renju Taraguchi-10 swap window (after moves 1-4, and the move-5 window).
     * swap=true  -> take the other side; no stone placed (move ignored).
     * swap=false -> decline; place the next opening stone (move): moves 2-4 in
     *               their box, or move 5 in the 9x9 = Branch A. At the move-5
     *               window a decline carries no stone (move 5 already on board).
     */
    public DSGRenjuTaraguchiSwapTableEvent(String player, int table, boolean swap, int move) {
        super(player, table);
        this.swap = swap;
        this.move = move;
    }

    public void setSwap(boolean swap) {
        this.swap = swap;
    }

    public boolean isSwap() {
        return swap;
    }

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public String toString() {
        return "Renju Taraguchi swap=" + swap + " move=" + move + " " + super.toString();
    }
}
```

- [ ] **Step 2: Create `DSGRenjuTaraguchiOffer10TableEvent`**

Write `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiOffer10TableEvent.java`:

```java
package org.pente.gameServer.event;

public class DSGRenjuTaraguchiOffer10TableEvent extends AbstractDSGTableEvent {

    private int[] moves;

    public DSGRenjuTaraguchiOffer10TableEvent() {
        super();
    }

    /**
     * Branch B: the ten 5th-move candidates offered by black. Implies "declined
     * the move-4 swap + chose Branch B". Also re-sent (player==null) to a client
     * that joins while selection is still pending.
     */
    public DSGRenjuTaraguchiOffer10TableEvent(String player, int table, int[] moves) {
        super(player, table);
        this.moves = moves;
    }

    public void setMoves(int[] moves) {
        this.moves = moves;
    }

    public int[] getMoves() {
        return moves;
    }

    public String toString() {
        return "Renju Taraguchi offer10 " +
                (moves == null ? 0 : moves.length) + " moves " + super.toString();
    }
}
```

- [ ] **Step 3: Create `DSGRenjuTaraguchi10Select1TableEvent`**

Write `dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchi10Select1TableEvent.java`:

```java
package org.pente.gameServer.event;

public class DSGRenjuTaraguchi10Select1TableEvent extends AbstractDSGTableEvent {

    private int move;

    public DSGRenjuTaraguchi10Select1TableEvent() {
        super();
    }

    /** The other player picks one of the ten offered candidates as move 5. */
    public DSGRenjuTaraguchi10Select1TableEvent(String player, int table, int move) {
        super(player, table);
        this.move = move;
    }

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public String toString() {
        return "Renju Taraguchi select1 move=" + move + " " + super.toString();
    }
}
```

- [ ] **Step 4: Register the three fields + getter/setter in `DSGEventWrapper`**

The constructor (line 91, `f.set(this, o)`) and `getEncodedEvent()` (line 105, `f.get(this)`) reflect over the declared fields, so only the **field declarations** are strictly required for (de)serialization on both TCP and WebSocket. Getters/setters are added because the wrapper declares one for every existing event field (lines 127-677) — they keep the API consistent and are harmless.

In `DSGEventWrapper.java`, after the LAST field declaration (line 83, `private DSGArenaRequestJoinTableEvent dsgArenaRequestJoinTableEvent;`) and before the constructor (line 85), add:

```java
    private DSGRenjuTaraguchiSwapTableEvent dsgRenjuTaraguchiSwapTableEvent;
    private DSGRenjuTaraguchiOffer10TableEvent dsgRenjuTaraguchiOffer10TableEvent;
    private DSGRenjuTaraguchi10Select1TableEvent dsgRenjuTaraguchi10Select1TableEvent;
```

Placement among the getter/setter pairs is functionally irrelevant — the Gson codec only reflects over the field declarations, never the accessors — so add the three pairs anywhere among the existing pairs. Two convenient anchors: immediately after the `dsgMoveTableEvent` getter/setter pair (which ends at line 389, mid-file) or after the final getter/setter in the class, `setDsgArenaRequestJoinTableEvent` (ends at line 677, just before the closing brace at line 678). Add:

```java
    public DSGRenjuTaraguchiSwapTableEvent getDsgRenjuTaraguchiSwapTableEvent() {
        return dsgRenjuTaraguchiSwapTableEvent;
    }

    public void setDsgRenjuTaraguchiSwapTableEvent(DSGRenjuTaraguchiSwapTableEvent dsgRenjuTaraguchiSwapTableEvent) {
        this.dsgRenjuTaraguchiSwapTableEvent = dsgRenjuTaraguchiSwapTableEvent;
    }

    public DSGRenjuTaraguchiOffer10TableEvent getDsgRenjuTaraguchiOffer10TableEvent() {
        return dsgRenjuTaraguchiOffer10TableEvent;
    }

    public void setDsgRenjuTaraguchiOffer10TableEvent(DSGRenjuTaraguchiOffer10TableEvent dsgRenjuTaraguchiOffer10TableEvent) {
        this.dsgRenjuTaraguchiOffer10TableEvent = dsgRenjuTaraguchiOffer10TableEvent;
    }

    public DSGRenjuTaraguchi10Select1TableEvent getDsgRenjuTaraguchi10Select1TableEvent() {
        return dsgRenjuTaraguchi10Select1TableEvent;
    }

    public void setDsgRenjuTaraguchi10Select1TableEvent(DSGRenjuTaraguchi10Select1TableEvent dsgRenjuTaraguchi10Select1TableEvent) {
        this.dsgRenjuTaraguchi10Select1TableEvent = dsgRenjuTaraguchi10Select1TableEvent;
    }
```

> The three event classes are in the same package (`org.pente.gameServer.event`) as `DSGEventWrapper`, so no imports are needed.

- [ ] **Step 5: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiSwapTableEvent.java \
        dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchiOffer10TableEvent.java \
        dsg_src/java/org/pente/gameServer/event/DSGRenjuTaraguchi10Select1TableEvent.java \
        dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java
git commit -m "feat(renju): add 3 Taraguchi-10 live opening events + codec registration

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Dispatch arms + handler stubs

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

This task only wires the switch to empty handler stubs so the whole path compiles; the real logic lands in Tasks 3-6.

- [ ] **Step 1: Add three `case` arms to `callServerTable`**

In `SynchronizedServerTable.java`, in the `switch (e)` inside `callServerTable` (≈158-218), immediately before the `default -> { }` arm (≈216), add (matching the existing single-line arrow style, e.g. the `DSGSwap2PassTableEvent` arm at ≈206):

```java
                    case DSGRenjuTaraguchiSwapTableEvent dsgRenjuTaraguchiSwapTableEvent ->
                            serverTable.handleRenjuSwap(dsgRenjuTaraguchiSwapTableEvent);
                    case DSGRenjuTaraguchiOffer10TableEvent dsgRenjuTaraguchiOffer10TableEvent ->
                            serverTable.handleRenjuOffer10(dsgRenjuTaraguchiOffer10TableEvent);
                    case DSGRenjuTaraguchi10Select1TableEvent dsgRenjuTaraguchi10Select1TableEvent ->
                            serverTable.handleRenjuSelect1(dsgRenjuTaraguchi10Select1TableEvent);
```

> The import `import org.pente.gameServer.event.*;` (line 26) already covers the three new types.

- [ ] **Step 2: Add three empty handler stubs to `ServerTable`**

In `ServerTable.java`, add these stubs near the other table-event handlers (e.g. right after `handleSwap2Pass`, which ends ≈1238). They make Task 2 compile; Tasks 3-5 fill the bodies.

```java
    public void handleRenjuSwap(DSGRenjuTaraguchiSwapTableEvent swapEvent) {
        // implemented in Task 3
    }

    public void handleRenjuOffer10(DSGRenjuTaraguchiOffer10TableEvent offerEvent) {
        // implemented in Task 4
    }

    public void handleRenjuSelect1(DSGRenjuTaraguchi10Select1TableEvent selectEvent) {
        // implemented in Task 5
    }
```

> `ServerTable` already wildcard-imports `org.pente.gameServer.event.*` (line 28) and `org.pente.game.*` (line 23, covering `RenjuState`), so no new imports are needed in this file for any task.

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java \
        dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): route 3 Taraguchi-10 events to ServerTable handler stubs

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Implement `handleRenjuSwap`

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

Mirrors `handleMove`'s error-accumulate / single-emit pattern (≈1551, ≈1703) and `handleSwap`'s seat-array + timer choreography (≈1107-1190).

**Phase / actor model (from the extracted `RenjuState.getCurrentPlayer`):** during a swap window `isAwaitingSwapDecision()` is true and `getCurrentPlayer()` returns the player who must decide; that player's seat must equal the actor's seat. `getNumMoves()` identifies the window: `1/2/3` = the move-2/3/4 windows (decline places the next opening stone), `4` = the move-4 window (decline = Branch A: choose branch A then place move 5), `5` = the move-5 window (decline carries no stone; move 6 arrives later as a normal `DSGMoveTableEvent`).

**Double-broadcast decision (Task-3 specific):** for `swap=false` the bundled stone is placed by **delegating to `handleMove(actor, move)`** — it owns `isValidMove`/box/forbidden validation (≈1565), timers (≈1582+), undo/cancel reset (≈1655+), `activityLogger` (≈1668), the single `broadcastTable(new DSGMoveTableEvent(...))` (≈1661), and game-over (≈1675). The swap echo is sent via `broadcastMainRoom` **before** `handleMove` and conveys only the decision (`swap=false`); the client never places a stone from it. The move is therefore broadcast exactly once. We must call `renjuSwapDecisionMade(false)` **before** `handleMove`, because `RenjuState.isValidMove` returns `false` while `awaitingSwap` is true (extracted line 420: `if (awaitingSwap) return false;`). Resolving the swap (a final, valid decision) and then attempting the move is consistent with `handleSwap`, which also commits the decision irrevocably ("it's too late"); if `handleMove` rejects a bad stone it emits its own `DSGMoveTableErrorEvent` and the player simply retries the stone as a normal move.

- [ ] **Step 1: Replace the `handleRenjuSwap` stub with the full body**

In `ServerTable.java`, replace the `handleRenjuSwap` stub from Task 2 with:

```java
    public void handleRenjuSwap(DSGRenjuTaraguchiSwapTableEvent swapEvent) {

        String actor = swapEvent.getPlayer();
        int move = swapEvent.getMove();
        int error = NO_ERROR;

        if (!isPlayerInTable(actor)) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else if (!(gridState instanceof RenjuState)) {
            error = DSGTableErrorEvent.UNKNOWN;
        } else {
            int seat = getPlayerSeat(actor);
            RenjuState rs = (RenjuState) gridState;
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            } else if (!rs.isAwaitingSwapDecision()) {
                error = DSGTableErrorEvent.INVALID_MOVE;   // not in a swap window
            } else if (gridState.getCurrentPlayer() != seat) {
                error = DSGTableErrorEvent.NOT_TURN;
            } else {

                // decision is final; cancel any pending undo (mirror handleSwap)
                undoRequested = false;

                if (swapEvent.isSwap()) {

                    // take the other side: swap both seat arrays exactly like handleSwap
                    DSGPlayerData tmp = playingPlayers[1];
                    playingPlayers[1] = playingPlayers[2];
                    playingPlayers[2] = tmp;
                    sittingPlayers[1] = sittingPlayers[2];
                    sittingPlayers[2] = tmp;

                    // update timers after the swap decision (mirror handleSwap)
                    if (timed) {
                        timers[gridState.getCurrentPlayer()].stop();
                        if (initialMinutes == 0) {
                            timers[gridState.getCurrentPlayer()].reset();
                        }
                        timers[gridState.getCurrentPlayer()].incrementMillis(
                                (int) pingManager.getPingTime(actor));
                        int s1 = timers[1].getSeconds();
                        int m1 = timers[1].getMinutes();
                        int s2 = timers[2].getSeconds();
                        int m2 = timers[2].getMinutes();
                        timers[1].adjust(m2, s2);
                        timers[2].adjust(m1, s1);
                    }

                    rs.renjuSwapDecisionMade(true);

                    if (timed) {
                        if (initialMinutes == 0) {
                            timers[gridState.getCurrentPlayer()].reset();
                        }
                        broadCastPlayerTimer(1);
                        broadCastPlayerTimer(2);
                        timers[gridState.getCurrentPlayer()].go();
                    }

                    broadcastMainRoom(swapEvent);

                } else {

                    int n = gridState.getNumMoves();
                    rs.renjuSwapDecisionMade(false);
                    // decision echo (decision-only on the client; the stone, if any,
                    // arrives via the DSGMoveTableEvent that handleMove broadcasts)
                    broadcastMainRoom(swapEvent);

                    if (n == 4) {
                        // move-4 window declined -> Branch A: choose A, then place move 5
                        rs.chooseBranch(false);
                        handleMove(actor, move);
                    } else if (n < 4) {
                        // move-2/3/4 windows: place the next opening stone
                        handleMove(actor, move);
                    } else {
                        // move-5 window: white declines swap5 and continues to play
                        // move 6 itself, so getCurrentPlayer() is unchanged (next ==
                        // seat == white). No bundled stone and no handoff to the other
                        // player; white's own clock is simply reset/continued. Move 6
                        // then arrives later as a normal DSGMoveTableEvent.
                        if (timed) {
                            timers[seat].stop();
                            if (initialMinutes == 0) {
                                timers[seat].reset();
                            }
                            timers[seat].incrementMillis((int) pingManager.getPingTime(actor));
                            int next = gridState.getCurrentPlayer();
                            if (initialMinutes == 0) {
                                timers[next].reset();
                            }
                            timers[next].go();
                            broadCastPlayerTimer(seat);
                            broadCastPlayerTimer(next);
                        }
                    }
                }
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGMoveTableErrorEvent(actor, tableNum, move, error),
                    actor);
        }
    }
```

- [ ] **Step 2: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): handleRenjuSwap - swap-window decisions + seat remap

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `RenjuState.offerFifthMoves` + implement `handleRenjuOffer10`

**Files:**
- Modify: `dsg_src/java/org/pente/game/RenjuState.java`
- Test: `dsg_src/java/org/pente/game/test/RenjuReconstructTest.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

The spec requires the ten offers to "validate all ten up front and commit none if any is rejected." The extracted `offerFifthMove(int)` validates **and** commits incrementally (adds to the private `offeredFifth`), with no public rollback. So we add a thin **transactional wrapper** in `RenjuState` that reuses the existing per-move validation (no new rules) and rolls the private list back on any rejection. It is engine code and unit-testable, so we TDD it.

- [ ] **Step 1: Write the failing rollback tests**

In `RenjuReconstructTest.java`, add (the `xy(...)` helper and the 4-move decline setup are the same known-good ones used by the archival tests):

```java
    // Builds a Branch-B position: 4 opening moves with swaps declined, branch B chosen.
    private RenjuState branchBAtFour() {
        RenjuState s = new RenjuState(15, 15);
        s.addMove(xy(7, 7));  s.renjuSwapDecisionMade(false);   // after move 1
        s.addMove(xy(8, 8));  s.renjuSwapDecisionMade(false);   // after move 2
        s.addMove(xy(9, 7));  s.renjuSwapDecisionMade(false);   // after move 3
        s.addMove(xy(6, 8));  s.renjuSwapDecisionMade(false);   // after move 4 (swap4)
        s.chooseBranch(true);                                   // Branch B
        return s;
    }

    public void testOfferFifthMovesRejectsWrongCount() {
        RenjuState s = branchBAtFour();
        try {
            s.offerFifthMoves(new int[]{ xy(10, 10) });   // only 1, needs 10
            fail("expected rejection for wrong offer count");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue("no offers may be committed on rejection",
                s.getOfferedFifthMoves().isEmpty());
        assertTrue("engine must still accept the ten offers",
                s.isAwaitingFifthOffers());
    }

    public void testOfferFifthMovesRollsBackOnOccupiedPoint() {
        RenjuState s = branchBAtFour();
        // Nine VALID candidates, then move 1's occupied point as the 10th. The loop
        // commits the first nine to offeredFifth, then offerFifthMove throws on the
        // occupied 10th -> this genuinely exercises the partial-rollback path
        // (offeredFifth.clear() + addAll(snapshot) over a NON-empty accumulation),
        // not just the "throws before anything is added" case.
        int[] bad = new int[]{
                xy(10, 10), xy(11, 10), xy(12, 10), xy(13, 10), xy(10, 11),
                xy(11, 11), xy(12, 11), xy(13, 11), xy(10, 12),
                xy(7, 7)   // occupied (move 1) -> throws after nine offers were added
        };
        try {
            s.offerFifthMoves(bad);
            fail("expected rejection for occupied offer point");
        } catch (IllegalArgumentException expected) {
        }
        assertTrue("a rejected batch must roll back the nine already-added offers",
                s.getOfferedFifthMoves().isEmpty());
        assertTrue(s.isAwaitingFifthOffers());
    }
```

- [ ] **Step 2: Run to verify it fails**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: FAIL — `offerFifthMoves` not defined (compile error).

- [ ] **Step 3: Add `offerFifthMoves` to `RenjuState`**

In `RenjuState.java`, immediately after `offerFifthMove(int)` (extracted at lines 300-311), add:

```java
    /**
     * Atomically offer all ten Branch-B 5th-move candidates. Each is validated by
     * the SAME rules as offerFifthMove (empty board point, in bounds, not a
     * duplicate, not a symmetric duplicate). If ANY is rejected, NO offer is
     * committed: the offeredFifth list is restored to its prior contents and the
     * triggering exception is rethrown. Reuses offerFifthMove - no new rules.
     */
    public void offerFifthMoves(int[] moves) {
        if (!isAwaitingFifthOffers()) {
            throw new IllegalStateException("not accepting 5th-move offers");
        }
        if (moves == null || moves.length != 10) {
            throw new IllegalArgumentException("Branch B requires exactly ten 5th-move offers");
        }
        List<Integer> snapshot = new ArrayList<Integer>(offeredFifth);
        try {
            for (int m : moves) {
                offerFifthMove(m);
            }
        } catch (RuntimeException e) {
            offeredFifth.clear();
            offeredFifth.addAll(snapshot);
            throw e;
        }
    }
```

> `offeredFifth` is the private `List<Integer>` field already backing `offerFifthMove`/`getOfferedFifthMoves`. `List`/`ArrayList` are already imported in `RenjuState` (used by `getOfferedFifthMoves`, extracted lines 271-273). If the build flags them missing, add `import java.util.List;` / `import java.util.ArrayList;`.

- [ ] **Step 4: Run to verify it passes**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile && ant test-one -Dtest=org.pente.game.test.RenjuReconstructTest
```
Expected: PASS (existing tests + the 2 new ones).

- [ ] **Step 5: Replace the `handleRenjuOffer10` stub with the full body**

The offerer is black (`getCurrentPlayer()` at the move-4 decision). The handler resolves the move-4 swap window as declined (only if still awaiting), chooses Branch B, then offers the ten atomically. The swap-decline and the Branch-B choice are unconditional, valid decisions and are committed on the way in; only the ten-offer **payload** is validated atomically (rolled back inside `offerFifthMoves` if malformed), so a client that re-sends a corrected ten succeeds — the phase guard accepts `isAwaitingFifthOffers()` for exactly that retry. On success the turn/timer passes to the selector (white), mirroring `handleSwap2Pass`. No stone is placed (offers are not moves), so only the echo is broadcast — no double-broadcast risk.

In `ServerTable.java`, replace the `handleRenjuOffer10` stub from Task 2 with:

```java
    public void handleRenjuOffer10(DSGRenjuTaraguchiOffer10TableEvent offerEvent) {

        String actor = offerEvent.getPlayer();
        int[] moves = offerEvent.getMoves();
        int error = NO_ERROR;

        if (!isPlayerInTable(actor)) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else if (!(gridState instanceof RenjuState)) {
            error = DSGTableErrorEvent.UNKNOWN;
        } else {
            int seat = getPlayerSeat(actor);
            RenjuState rs = (RenjuState) gridState;
            boolean atMove4 = !rs.isOpeningComplete() && gridState.getNumMoves() == 4 &&
                    (rs.isAwaitingSwapDecision() || rs.isAwaitingBranchChoice()
                            || rs.isAwaitingFifthOffers());
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            } else if (!atMove4) {
                error = DSGTableErrorEvent.INVALID_MOVE;   // not at the post-move-4 decision
            } else if (gridState.getCurrentPlayer() != seat) {
                error = DSGTableErrorEvent.NOT_TURN;
            } else if (moves == null || moves.length != 10) {
                error = DSGTableErrorEvent.INVALID_MOVE;
            } else {

                undoRequested = false;
                int offererSeat = gridState.getCurrentPlayer();   // black, the offerer

                try {
                    if (rs.isAwaitingSwapDecision()) {
                        rs.renjuSwapDecisionMade(false);   // decline the move-4 swap
                    }
                    if (rs.isAwaitingBranchChoice()) {
                        rs.chooseBranch(true);             // Branch B
                    }
                    rs.offerFifthMoves(moves);             // atomic; throws -> INVALID_MOVE
                } catch (RuntimeException ex) {
                    log4j.info(psid() + "Renju offer10 rejected: " + ex.getMessage());
                    error = DSGTableErrorEvent.INVALID_MOVE;
                }

                if (error == NO_ERROR) {
                    // turn + timer pass to the selector (white), mirror handleSwap2Pass
                    if (timed) {
                        timers[offererSeat].stop();
                        if (initialMinutes == 0) {
                            timers[offererSeat].reset();
                        }
                        timers[offererSeat].incrementMillis(
                                (int) pingManager.getPingTime(actor));
                        int selector = gridState.getCurrentPlayer();   // white selecting
                        if (initialMinutes == 0) {
                            timers[selector].reset();
                        }
                        timers[selector].go();
                        broadCastPlayerTimer(offererSeat);
                        broadCastPlayerTimer(selector);
                    }
                    broadcastMainRoom(offerEvent);
                }
            }
        }

        if (error != NO_ERROR) {
            // The ten-offer commit is atomic (validate-all / commit-none via
            // offerFifthMoves), so on rejection NO candidate was applied and there is
            // no single "offending move" the way handleMove has one. We therefore
            // report move = -1; the client re-sends a corrected ten. (Spec alignment:
            // Task 7 updates the spec's Error-handling section to state that the
            // offer10 handler reports -1 for batch rejections, superseding its earlier
            // "the offending move" wording, which assumed an incremental commit.)
            dsgEventRouter.routeEvent(
                    new DSGMoveTableErrorEvent(actor, tableNum, -1, error),
                    actor);
        }
    }
```

- [ ] **Step 6: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add dsg_src/java/org/pente/game/RenjuState.java \
        dsg_src/java/org/pente/game/test/RenjuReconstructTest.java \
        dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): atomic offerFifthMoves + handleRenjuOffer10 routing

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Implement `handleRenjuSelect1` (+ `broadcastRenjuFifthMove` helper)

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

**`selectFifthMove`-vs-`addMove` decision:** the extracted `RenjuState.selectFifthMove(int)` (lines 313-322) **internally calls `addMove(move)`** at line 321 to commit the chosen candidate as move 5. Therefore we must **NOT** delegate to `handleMove` (it would `addMove` a second time = double-place). Instead we reproduce the post-move tail from `handleMove` (≈1582-1698) — the timer + `moveTimes` accounting (BOTH the player-changed branch ≈1586-1628 and the same-player branch ≈1629-1648), undo/cancel reset (≈1651-1659), the single `broadcastTable(new DSGMoveTableEvent(...))` placement (≈1661), `activityLogger` (≈1668), and game-over / final `go()` (≈1671-1698) — in a small private helper `broadcastRenjuFifthMove`. `getCurrentPlayer()` is captured **before** `selectFifthMove` for symmetry with `handleMove` (which captures `oldCurrentPlayer` before its own `addMove`). **NOTE — the player does NOT change across the selection:** the selector is white (seat 2 — `getCurrentPlayer()` returns 2 while `selectedFifth == null`), and after `selectFifthMove()` commits the chosen candidate as move 5 (`addMove` → n=5) the engine reports `getCurrentPlayer() == 5 % 2 + 1 == 2` again, because white also plays move 6 (`addMove`'s `n == 5 && !tenOffer` swap branch is skipped here since `tenOffer` is true in Branch B). So `oldCurrentPlayer == newCurrentPlayer == 2`, and the helper must reproduce handleMove's **same-player** branch — that branch is the one that fires, and it still adds the `moveTimes` entry and applies the reset/increment for this stone. The select echo (`broadcastMainRoom`) identifies which of the ten became move 5 (so the client clears the other nine); the stone itself is placed once by the `DSGMoveTableEvent`.

- [ ] **Step 1: Replace the `handleRenjuSelect1` stub with the full body**

In `ServerTable.java`, replace the `handleRenjuSelect1` stub from Task 2 with:

```java
    public void handleRenjuSelect1(DSGRenjuTaraguchi10Select1TableEvent selectEvent) {

        String actor = selectEvent.getPlayer();
        int move = selectEvent.getMove();
        int error = NO_ERROR;

        if (!isPlayerInTable(actor)) {
            error = DSGTableErrorEvent.NOT_IN_TABLE;
        } else if (!(gridState instanceof RenjuState)) {
            error = DSGTableErrorEvent.UNKNOWN;
        } else {
            int seat = getPlayerSeat(actor);
            RenjuState rs = (RenjuState) gridState;
            if (seat == NOT_SITTING) {
                error = DSGTableErrorEvent.NOT_SITTING;
            } else if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                error = DSGTableErrorEvent.NO_GAME_IN_PROGRESS;
            } else if (!rs.isAwaitingFifthSelection()) {
                error = DSGTableErrorEvent.INVALID_MOVE;   // not awaiting a selection
            } else if (gridState.getCurrentPlayer() != seat) {
                error = DSGTableErrorEvent.NOT_TURN;
            } else if (!rs.getOfferedFifthMoves().contains(move)) {
                error = DSGTableErrorEvent.INVALID_MOVE;    // not one of the ten
            } else {

                // Do NOT pre-clear undoRequested here. Unlike the swap handlers (which
                // mirror handleSwap and set undoRequested=false directly), this path
                // places a stone via the handleMove tail, so broadcastRenjuFifthMove
                // performs the undo/cancel reset exactly as handleMove does (it
                // broadcasts the decline replies before clearing the flags).
                int oldCurrentPlayer = gridState.getCurrentPlayer();   // white, selecting
                rs.selectFifthMove(move);          // engine addMove's the chosen candidate as move 5 (opening completes at move 6)

                // decision echo: which offer became move 5 (clears the other nine)
                broadcastMainRoom(selectEvent);
                // sole stone placement + timer accounting + game-over (handleMove tail)
                broadcastRenjuFifthMove(actor, move, oldCurrentPlayer);
            }
        }

        if (error != NO_ERROR) {
            dsgEventRouter.routeEvent(
                    new DSGMoveTableErrorEvent(actor, tableNum, move, error),
                    actor);
        }
    }
```

- [ ] **Step 2: Add the `broadcastRenjuFifthMove` helper**

Place it next to `handleRenjuSelect1`. It faithfully reproduces `handleMove`'s tail (≈1582-1698) for the single move-5 commit, **including handleMove's `else { // same player }` branch**. In Branch B `oldCurrentPlayer == newCurrentPlayer` (white selects move 5 and also plays move 6 — see Step 1), so the same-player branch is the one that actually fires here: it must add the `moveTimes` entry and apply the reset/increment exactly like every other stone. Gating `moveTimes.add` on `old != new` (as the old draft did) would under-record the per-move time list by one and skip the Fischer/byo-yomi accounting for every Branch-B timed game. The `synchronized (this)` wrapper matches handleMove, which wraps its own tail in `synchronized (this)` at ≈1582.

```java
    /**
     * Post-commit tail for the Branch-B 5th move. selectFifthMove() already did
     * addMove(), so this does exactly what handleMove does AFTER its own addMove:
     * the timer + moveTimes accounting (BOTH the player-changed and the same-player
     * branch), the undo/cancel decline replies, the single DSGMoveTableEvent
     * placement broadcast, activity logging, and the game-over / final-go() check.
     *
     * In Branch B oldCurrentPlayer == newCurrentPlayer (white selects move 5 and
     * also plays move 6), so handleMove's same-player branch is the one that fires;
     * both branches are reproduced verbatim so the per-move time list and the
     * Fischer/byo-yomi clocks stay consistent with every stone placed via
     * handleMove. oldCurrentPlayer was captured BEFORE selectFifthMove().
     */
    private void broadcastRenjuFifthMove(String player, int move, int oldCurrentPlayer) {
        int newCurrentPlayer = gridState.getCurrentPlayer();
        synchronized (this) {

            if (timed) {
                if (oldCurrentPlayer != newCurrentPlayer) {
                    timers[oldCurrentPlayer].stop();
                    if (shouldTimerRun()) {
                        if (initialMinutes == 0) {
                            timers[oldCurrentPlayer].reset();
                        } else {
                            timers[oldCurrentPlayer].increment(incrementalSeconds);
                            timers[oldCurrentPlayer].incrementMillis(
                                    (int) pingManager.getPingTime(player));
                        }
                    }
                    moveTimes.add(new Time(timers[oldCurrentPlayer].getMinutes(),
                            timers[oldCurrentPlayer].getSeconds()));
                } else {  // same player (Branch B: white selected move 5 and plays move 6)
                    if (shouldTimerRun()) {
                        if (initialMinutes == 0) {
                            timers[oldCurrentPlayer].stop();
                            timers[oldCurrentPlayer].reset();
                        } else {
                            timers[oldCurrentPlayer].increment(incrementalSeconds);
                            timers[oldCurrentPlayer].incrementMillis(
                                    (int) pingManager.getPingTime(player));
                        }
                    }
                    moveTimes.add(new Time(timers[oldCurrentPlayer].getMinutes(),
                            timers[oldCurrentPlayer].getSeconds()));
                }
            }

            // undo/cancel reset, mirroring handleMove (≈1651-1659)
            if (undoRequested) {
                broadcastTable(new DSGUndoReplyTableEvent(player, tableNum, false));
                undoRequested = false;
            }
            if (cancelRequested) {
                broadcastTable(new DSGCancelReplyTableEvent(player, tableNum, false));
                cancelRequested = false;
                cancelRequestedBy = null;
            }

            broadcastTable(new DSGMoveTableEvent(player, tableNum, move));

            if (shouldTimerRun() && timed) {
                broadCastPlayerTimer(oldCurrentPlayer);
            }

            activityLogger.updateGameState(sid, tableNum, gridState.getHash(),
                    gridState.getMoves());

            if (gridState.isGameOver()) {
                String winner;
                String loser;
                if (gridState.getWinner() == 0) {
                    winner = playingPlayers[1].getName();
                    loser = playingPlayers[2].getName();
                } else {
                    winner = playingPlayers[gridState.getWinner()].getName();
                    loser = playingPlayers[3 - gridState.getWinner()].getName();
                }
                gameOver(gridState.getWinner() == 0, winner, loser, false, false, false);
            } else if (timed) {
                if (oldCurrentPlayer != newCurrentPlayer || initialMinutes == 0) {
                    timers[newCurrentPlayer].go();
                }
            }
        }
    }
```

> Every symbol used here appears in the extracted `handleMove` body (`timed`, `timers`, `shouldTimerRun`, `initialMinutes`, `incrementalSeconds`, `pingManager`, `Time`, `moveTimes`, `undoRequested`, `cancelRequested`, `cancelRequestedBy`, `DSGUndoReplyTableEvent`, `DSGCancelReplyTableEvent`, `broadcastTable`, `broadCastPlayerTimer`, `activityLogger`, `sid`, `playingPlayers`, `gameOver`) — no new fields/methods are introduced. (`undoRequested`/`cancelRequested`/`cancelRequestedBy` are `ServerTable` fields at lines 91-93; `DSGUndoReplyTableEvent`/`DSGCancelReplyTableEvent` are covered by the existing `org.pente.gameServer.event.*` import.)

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): handleRenjuSelect1 commits move 5 without double-placing

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Join snapshot — re-send the ten offers mid-selection

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java`

A client that joins (rejoin or spectator) while Branch B is between offer and selection must receive the ten so it can render the selection prompt. Uncommitted swap/branch decisions are not persisted and are not re-sent; only the committed ten, and only while selection is pending.

- [ ] **Step 1: Call the helper right after `sendMoves(player)` in `handleJoin`**

In `ServerTable.java`, `handleJoin` calls `sendMoves(player);` (≈542). Immediately after that line, add:

```java
            sendRenjuBranchBOffers(player);
```

- [ ] **Step 2: Add the `sendRenjuBranchBOffers` helper**

Place it next to `sendMoves` (≈575). Scope available: `gridState`, `tableNum`, `dsgEventRouter`.

```java
    private void sendRenjuBranchBOffers(String player) {
        if (gridState instanceof RenjuState) {
            RenjuState rs = (RenjuState) gridState;
            if (rs.isAwaitingFifthSelection()) {
                java.util.List<Integer> offers = rs.getOfferedFifthMoves();
                int[] arr = new int[offers.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = offers.get(i);
                }
                dsgEventRouter.routeEvent(
                        new DSGRenjuTaraguchiOffer10TableEvent(null, tableNum, arr),
                        player);
            }
        }
    }
```

> `isAwaitingFifthSelection()` is only true after all ten are offered and before one is selected, so `offers.size()` is exactly 10 here.

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./justCompile
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java
git commit -m "feat(renju): re-send Branch-B offers to clients joining mid-selection

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Verification + docs

**Files:** none (verification) + `docs/superpowers/specs/2026-06-15-renju-live-opening-routing-design.md` + `docs/renju-integration-guide.md`

- [ ] **Step 1: Clean rebuild + Renju regression suites**

The routing changes must not regress the engine. Run the full Renju suite (the same set the archival work runs):

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
Expected: all PASS (including the 2 new `offerFifthMoves` tests in `RenjuReconstructTest`).

- [ ] **Step 2: Document the manual WebSocket round-trip (no `ServerTable` harness exists)**

There is no `ServerTable` unit-test harness (see Testing reality), so the handlers are verified live. Restart the backend, then drive a live Renju game (two browser/WS sessions) and confirm each path. Record the result in the PR for manual QA.

```bash
docker restart penteorg-pente.org-1
```

Round-trip checklist (each inbound event over the shared `DSGEventWrapper` codec, i.e. works on TCP and WS):
- **Swap window:** send `DSGRenjuTaraguchiSwapTableEvent(swap=true)` -> both clients see the seats swap, no stone placed. Send `swap=false, move=<box point>` -> the swap echo arrives and the bundled stone arrives as one `DSGMoveTableEvent` (placed once, not twice).
- **Branch A:** at the move-4 window send `swap=false, move=<9x9 point>` -> branch A is taken and move 5 is placed via a single `DSGMoveTableEvent`.
- **Branch B:** send `DSGRenjuTaraguchiOffer10TableEvent(10 moves)` -> opponent receives the ten; send `DSGRenjuTaraguchi10Select1TableEvent(move)` -> move 5 is placed once and the other nine clear.
- **Out-of-phase / illegal:** a swap when not in a window, an offer when not at move 4, a selection not among the ten, or a forbidden/occupied/symmetric point -> sender receives a `DSGMoveTableErrorEvent` and the board does not change.
- **Rejoin mid-offer:** after the ten are offered but before selection, reconnect a client -> it receives the ten via `DSGRenjuTaraguchiOffer10TableEvent` (the Task 6 snapshot) after the normal move snapshot.

> Verify the **echo recipients**: the three echoes use `broadcastMainRoom` (iterates `playersInMainRoom`, ≈357) while the bundled stone uses `broadcastTable` (iterates `playersInTable`, ≈345) — two different recipient sets (see "Recipient-set caveat" in the move-broadcast policy). This mirrors the proven `handleSwap`/`handleSwap2Pass` behavior and works because the seated opponent is in both lists. Confirm the opponent **and any spectator** actually receives each echo (especially the Branch-B `offer10`, which the opponent needs in order to select). If a spectator/rejoiner in the table list does not receive an echo, switch the three echo calls to `broadcastTable` so the echo and its stone share one recipient set.

- [ ] **Step 3: Flip spec Status + update the integration guide**

In `docs/superpowers/specs/2026-06-15-renju-live-opening-routing-design.md`, change line 5 `Status: approved-pending-review` -> `Status: implemented (manual WS round-trip pending)`.

Also reconcile the spec's **Error handling** section with the atomic-batch reality: where it states the offer (`offer10`) handler's error event carries "the offending move (the rejected candidate)," change it to say the offer10 handler reports `move = -1` because the ten-offer commit is atomic (validate-all / commit-none), so no single candidate is ever applied; the client re-sends a corrected ten. (The per-stone handlers `handleRenjuSwap`/`handleRenjuSelect1` still report the actual offending `move`.) This keeps spec and plan in agreement.

In `docs/renju-integration-guide.md` §7, move the **"Live Renju play — opening-decision routing (sub-project 2)"** bullet (line 187, currently under "### Still deferred") into the done section: the swap/branch/offer/selection opening flow is now routed for live games via the three new `DSG…TableEvent` types + `ServerTable` handlers. Leave sub-project 3 (React `react_live_game_room` opening UI) and forbidden-point client marking under "Still deferred". Update the §7 heading (line 169) accordingly (e.g. "archival persistence + live opening routing done").

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-15-renju-live-opening-routing-design.md \
        docs/renju-integration-guide.md
git commit -m "docs(renju): mark live opening routing implemented

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Three inbound events (`DSGRenjuTaraguchiSwapTableEvent` / `…Offer10TableEvent` / `…Select1TableEvent`) extending `AbstractDSGTableEvent` -> Task 1. ✓
- `DSGEventWrapper` registration (field + getter/setter, shared by TCP+WS via the reflection codec) -> Task 1. ✓
- Three `SynchronizedServerTable.callServerTable` dispatch arms -> Task 2. ✓
- `handleRenjuSwap` (Renju + seat + phase validation, seat-array + timer swap on `swap=true`, decline+bundled-move via `handleMove`, Branch-A `chooseBranch(false)`, move-5-window decline) -> Task 3. ✓
- `handleRenjuOffer10` (resolve move-4 swap as declined, `chooseBranch(true)`, atomic ten-offer commit, selector turn/timer pass) -> Task 4. ✓
- `RenjuState.offerFifthMoves` atomic wrapper (validate-all / commit-none, reusing `offerFifthMove`) + TDD -> Task 4. ✓
- `handleRenjuSelect1` (membership + phase validation, `selectFifthMove` already `addMove`s -> reproduce tail, no `handleMove`) -> Task 5. ✓
- Join snapshot re-send of the ten while selection pending -> Task 6. ✓
- Errors emitted to the sender via `DSGMoveTableErrorEvent`, no mutation on failure -> all handlers. ✓
- Move broadcast exactly once via `DSGMoveTableEvent`; echoes are decision-only (no double-broadcast) -> move-broadcast policy + Tasks 3/5. ✓

**Placeholder scan:** none — every code step is complete and uses only symbols present in the extracted `handleMove`/`handleSwap`/`handleSwap2Pass`/`RenjuState` bodies. Two "if the build flags it" import notes (Task 1 wrapper same-package; Task 4 `List`/`ArrayList`) are pinned to specific evidence (same package; `getOfferedFifthMoves` already uses both).

**No invented API:** all `RenjuState` calls (`isAwaitingSwapDecision`, `renjuSwapDecisionMade`, `isAwaitingBranchChoice`, `chooseBranch`, `isAwaitingFifthOffers`, `offerFifthMove`, `isAwaitingFifthSelection`, `selectFifthMove`, `getOfferedFifthMoves`, `isOpeningComplete`, `getCurrentPlayer`, `getNumMoves`) are from the extracted bodies; `offerFifthMoves` is the one new method, defined in Task 4.
