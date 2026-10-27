# Tombstone Pente — Plan C: Turn-Based Persistence, Move Servlet, Website JSP/JS

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make turn-based (TB) Tombstone Pente playable on the website: whole-turn persistence, the `command=move` servlet branch, TB undo, KOTH/tournament exclusions, and the TB board plus every JS game viewer rendering dead stones, passes and per-turn move lists.

**Architecture:** `TBGame` derives whose turn it is from the count of own entries (`TombstoneCodes.currentPlayer`). A new `CacheTBStorer.storeTombstoneTurn` validates a whole turn against a replayed `TombstoneState` and commits it atomically (one `persistSet`, then the `tb_move` rows, then one `updateGameAfterMove`). On the web side, pure Tombstone helpers live in `tb/gameScript.js` (tested under Node with `vm`), `boardCommon.js` dispatches to them, and a small Java helper `TombstoneTurnRows` gives every JSP the same one-row-per-turn move list.

**Tech Stack:** Java 21 (Ant, JUnit 3.7 `junit.framework.TestCase`), JSP on Tomcat, plain browser JS (no bundler) tested with Node `vm`, Redis aggregate root via `CacheTBStorer`.

**Spec:** `docs/superpowers/specs/2026-10-27-tombstone-pente-design.md` — read §2 (decisions), §3-4 (rules, codes), §7.3-7.4 (KOTH/tournament exclusions), §10-12 (this plan's scope) and §16 (interfaces) before starting any task.

## Ordering dependencies

- **Plan A (game core) must be merged on `feat/tombstone-pente` first.** Every Java task here compiles against `TombstoneCodes`, `TombstoneState`, `GridStateFactory.TB_TOMBSTONE`/`TOMBSTONE`/`SPEED_TOMBSTONE`/`isTombstone`. Task 0 checks this and stops if it is missing.
- Plan B (live server, archive) is independent of this plan. Archived TB games reach the archive through B's storer change unchanged (§16.2); this plan never edits `ServerTable`, the live events, `MySQLPenteGameStorer` or webdb.
- Plan D (React) is independent of this plan.

## Global Constraints

- Branch `feat/tombstone-pente` in `pente.org/`. Never commit to `main`. Commit messages are plain one-liners (e.g. `Add TB Tombstone turn storage`); no co-author or generated-by trailers.
- **Never run `./justCompile`** (it restarts docker) and **never run `sync_gameServer.sh`** (production deploy, spec §15).
- Compile + run one test class, from the `pente.org/` repo root:
  ```bash
  rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant compile-tests
  /opt/homebrew/opt/openjdk@21/bin/java --add-opens=java.base/java.lang=ALL-UNNAMED \
      -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner <fully.qualified.TestClass>
  ```
  (`ant compile-tests` compiles test sources straight from `dsg_src/java`; the `--add-opens` flag mirrors `build.xml`'s `test-one` target and is needed by the Redis serializing fixture.) JUnit 3.7 has no no-arg `TestCase` constructor: every test class declares `public XxxTest(String name) { super(name); }`. JUnit 3.7 also has no `assertFalse`: use `assertTrue(!x)`; expected exceptions use `try { …; fail("expected …"); } catch (InvalidMoveException e) { … }`.
- JS tests: `node dsg_src/httpdocs/gameServer/js/boardCommon.test.js` (baseline today: `OK: 96 mapping assertions + 2 throw assertions passed`) and the new `node dsg_src/httpdocs/gameServer/js/tombstone.test.js`.
- Codes (spec §4): own stone `0..360` (`x + 19*y`), dead stone `361 + point` (`361..721`), own pass `722`. Dead passes are never stored. Turn 0 = centre `180` only. No decoder may read a code `>= 361` without knowing the game is Tombstone.
- Ids: `TOMBSTONE = 33`, `SPEED_TOMBSTONE = 34`, `TB_TOMBSTONE = 83`. JSP/JS viewers normalise to the odd TB id (`83`) — `viewLiveGameMobile.jsp:460`, `viewLiveGameEmbed.jsp:332`, `viewGameEmbed.jsp:206`. Server-side JSP code must test Tombstone with `GridStateFactory.isTombstone(...)` on the **live** id (33 or 34), never `gameId == 83`, because a Speed archive gives `getGameId(name) + 50 = 84`.
- Exact copy (do not reword):
  - TB buttons: `Place only a dead stone`, `Submit without dead stone`, `Submit`.
  - Turn-shape errors: `Invalid turn: send one or two moves.`, `Invalid turn: the first move must be your stone or a pass.`, `Invalid turn: the second move must be a dead stone.` (produced by Plan A's `TombstoneCodes.validateTurnShape`); occupancy/phase errors surface as the existing `Invalid move.`
  - `King of the Hill is not available for Tombstone Pente.`
  - `Tournaments are not available for Tombstone Pente.`
  - Labels: `K10`, `PASS`, `dead K10`; turn labels `K10 + dead L11`, `PASS + dead L11`, `K10`, `PASS`.
- Colours: board fill `tombstoneColor = "#D7BDE2"`; dead stone radial gradient `#C8C8C8` → `#6E6E6E`; board cell value for a dead stone `3`.
- D4: a dead stone submitted after a winning own stone is **discarded**, not rejected.
- User decisions (spec §2): turn-based Tombstone (`83`) stays available on the website TB pages (`tb/new.jsp`, `tb/new2.jsp` list it through `getTbGames()`), with no gating in this plan. King of the Hill is excluded entirely: no KOTH event rows (Plan B Task 8), no server-45 offering (Plan B), and this plan's Task 4 rejects TB KOTH invitations and hides Tombstone in `tb/newKotH.jsp` (spec §7.3 "KOTH summary"). Live Tombstone is offered only by the Experimental live server (Plan B); nothing in this plan depends on that.
- No new DB columns, no schema change, no edits to `TBGameStorer` (the interface), `ServerTable`, the live events, the archive storer or webdb.
- Match the surrounding style (log4j `Category`, JUnit 3, `var`/`function` declarations in `gameScript.js` so Node `vm` sees them as context globals; no `let`/`const` at the top level of `gameScript.js`).

### Plan A interfaces this plan consumes (from spec §16.1, verbatim names)

```java
// org.pente.game.TombstoneCodes
POINTS = 361; DEAD_OFFSET = 361; OWN_PASS = 722; DEAD_CELL = 3;
boolean isOwnStone(int), isOwnPass(int), isOwnEntry(int), isDeadStone(int);
int deadCode(int point); int pointOf(int code);
int countOwnEntries(int[] codes, int n); int currentPlayer(int[] codes, int n);
int lastTurnLength(int[] codes, int n); String validateTurnShape(int[] turn);
String coordinate(int point); String entryLabel(int code);
String turnLabel(int ownCode, int deadCodeOrMinus1); int parsePgnToken(String token);
// org.pente.game.TombstoneState (GridState)
boolean isValidMove(int code, int player); void addMove(int code); int getCurrentPlayer();
boolean isAwaitingDeadStone(); void skipDeadStone(); boolean isGameOver(); int getWinner();
// org.pente.game.GridStateFactory
TOMBSTONE, SPEED_TOMBSTONE, TB_TOMBSTONE, boolean isTombstone(int game);
createGridState(TB_TOMBSTONE, MoveData) -> TombstoneState (replays and closes a trailing open turn)
```

## Spec corrections found while planning (verified against source)

1. **`storeNewMove` must still accept the centre stone.** Spec §10.2 says `storeNewMove` throws for `TB_TOMBSTONE`, but `CacheTBStorer.createSet` starts active sets by calling `storeNewMove(gid, 0, getCenterMove(game))` (`CacheTBStorer.java:1489`). The refusal therefore applies only when the game already has moves (`game.getNumMoves() > 0`). `acceptInvite` writes the centre via `baseStorer.storeNewMove` directly (`:1954`) and is unaffected.
2. **`endGame` order.** `storeNewMove` calls `endGameRunnable.endGame` **before** the `tb_move` writes (`CacheTBStorer.java:1790-1797`, writes at `:1800-1805`) and `updateGameAfterMove` last (`:1807`). `storeTombstoneTurn` keeps that exact order rather than the spec's wording (writes, then endGame).
3. **`undoReply.jsp` has no undo preview.** It renders the full current board (`undoReply.jsp:582-592`). Only its move list and markers change (Task 8); there is no "drop the last turn" preview to adjust.
4. **`viewGameEmbed.jsp` parses coordinate strings, not codes.** The forum `[game g=… m=…]` tag (`jive/GameFilter.java:27-41`) passes user-typed coordinates that the page converts with `AlphaNumericGridCoordinates.getPoint` (`viewGameEmbed.jsp:50-58`). For Tombstone the page parses each token with `TombstoneCodes.parsePgnToken` (`K10`, `pass`, `xK10`) instead, and answers 500 `Invalid request` on a bad token (Task 9).
5. **`drawRedDot` never draws off the board.** It returns early when `i >= gridSize || j >= gridSize` (`tb/gameScript.js:670-673`), so raw dead/pass codes lose their marker rather than drawing garbage. The fix (decoded markers) is unchanged.
6. **The rainwolf-only "Search the DB" block on `mobileGame.jsp` (condition at `:1615`, the anchor at `:1624`)** posts the game to the legacy game database, which Plan B makes reject Tombstone (§9.4). It gets the same Connect6-style exclusion (Task 7).
7. **The player's message travels inside `storeTombstoneTurn`, not through the opening-message path.** Spec §10.3 hands the message to `openingMessage`, which `MoveServlet` stores only after the refresh (`MoveServlet.java:711-718`). But `storeTombstoneTurn`, like `storeNewMove` (`CacheTBStorer.java:1790-1797`), queues `endGameRunnable.endGame` on its own copy of the game before it returns. That copy never holds the message, which causes two bugs. (a) For `REASON_WIN` the runnable puts `game.getMessage(game.getNumMoves())` in front of the loser's email (`:1218-1222`), so the winner's message would never be prefixed. (b) When the set completes, the runnable calls `persistSet(set)` on that stale copy (`:924-932`). If it runs after the servlet's `storeNewMessage` (`:1826-1848`, `addMessage` + `persistSet`), the message is wiped from Redis. Other games avoid both because they store the message before `storeNewMove` (`MoveServlet.java:692-695`; Connect6 `:634-636`). Tombstone cannot set `moveNum` before the turn is validated: it is the row count after the turn, and that count depends on whether a dead stone is discarded after a win. So `storeTombstoneTurn` takes the message (nullable). It sets `moveNum` and `seqNbr` after the kept codes are added, and attaches the message under the same lock and in the same `persistSet`, before `endGame` (Tasks 2 and 3).

## Review Focus

1. A malformed or legacy history (dead entry with no open turn, two dead entries in a row, an empty history) must never throw in the move-list helper or the JS turn parser; the row shows `dead K10` on its own. Pinned by `TombstoneTurnRowsTest.testOrphanDeadEntryGetsOwnRow` and `tombstone.test.js` "orphan dead".
2. A turn whose second entry is illegal after a legal first entry (stale page, a dead stone dropped on a stone that is still there) must not write a half turn: the own stone alone must not be stored. Pinned by `CacheTBStorerTombstoneTest.testRejectedSecondEntryWritesNothing` (no base-storer call, no set write). Stale submissions by the wrong player are stopped earlier by the servlet's existing turn gate (`MoveServlet.java:386-389`).
3. A dead stone on the point the own stone just vacated by capture is legal; a dead stone on the own stone's own point is not. Pinned by `testDeadOnJustCapturedPointAccepted` and `testDeadOnOwnPointRejected`.
4. Stepping back/forward and clicking the move list must land only on turn ends, otherwise `selectMove` dereferences a missing cell id. Pinned by `tombstone.test.js` "tombstoneStep" cases (both ends clamp).
5. A Speed Tombstone archive (`getGameId = 34`, `+50 = 84`) must still render per-turn rows. Pinned by `TombstoneTurnRowsTest.testIsTombstoneGameNameCoversSpeed` (the JSPs use the helper's name test, not `== 83`).
6. The message sent with a game-ending turn must reach the end-game email and survive the runnable's set `persistSet` (spec correction 7). Pinned by `CacheTBStorerTombstoneTest.testWinningTurnMessageReachesEndGameAndSurvivesSetEnd`.

---

## File map

| File | Change | Task |
|---|---|---|
| `dsg_src/java/org/pente/turnBased/TBGame.java` | Tombstone branch in `getCurrentPlayer`; new `getLastTurnLength()` | 1 |
| `dsg_src/java/org/pente/turnBased/test/TBGameTombstoneTest.java` | new | 1 |
| `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` | new `storeTombstoneTurn`; `storeNewMove` refusal | 2 |
| `dsg_src/java/org/pente/turnBased/test/CacheTBStorerTombstoneTest.java` | new | 2 |
| `dsg_src/java/org/pente/turnBased/web/MoveServlet.java` | `move` branch; `acceptUndo` count via new `undoEntryCount` | 3 |
| `dsg_src/java/org/pente/turnBased/web/test/MoveServletTombstoneTest.java` | new | 3 |
| `dsg_src/java/org/pente/turnBased/web/NewGameServlet.java` | KOTH reject via new `kothUnavailableError` | 4 |
| `dsg_src/java/org/pente/turnBased/web/test/NewGameServletTombstoneTest.java` | new | 4 |
| `dsg_src/httpdocs/gameServer/tb/newKotH.jsp`, `admin/newTourney.jsp` | hide/reject Tombstone | 4 |
| `dsg_src/java/org/pente/turnBased/web/TombstoneTurnRows.java` | new JSP helper | 5 |
| `dsg_src/java/org/pente/turnBased/web/test/TombstoneTurnRowsTest.java` | new | 5 |
| `dsg_src/httpdocs/gameServer/gameConstants.jspf`, `tb/gameScript.js`, `js/boardCommon.js`, `js/boardCommon.test.js`, `js/tombstone.test.js` (new) | shared JS | 6 |
| `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp` | TB board | 7 |
| `dsg_src/httpdocs/gameServer/tb/cancelReply.jsp`, `tb/undoReply.jsp`, `tb/listedMobileGame.jsp` | per-turn list, markers | 8 |
| `dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp`, `viewLiveGameEmbed.jsp`, `viewGameEmbed.jsp` | viewers | 9 |

---

### Task 0: Preconditions and baseline

**Files:** none changed.

- [ ] **Step 1: Confirm branch and Plan A**

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
git branch --show-current            # expect: feat/tombstone-pente
ls dsg_src/java/org/pente/game/TombstoneCodes.java dsg_src/java/org/pente/game/TombstoneState.java
grep -n "TB_TOMBSTONE\|isTombstone" dsg_src/java/org/pente/game/GridStateFactory.java | head
```

Expected: branch matches, both files exist, `TB_TOMBSTONE` and `isTombstone` are defined. If any is missing, **stop**: Plan A has not landed.

- [ ] **Step 2: Baseline the suites this plan touches**

```bash
rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant compile-tests
for t in org.pente.turnBased.test.TBGamePassDrawTest org.pente.turnBased.test.TBGameRenjuPhaseTest \
         org.pente.turnBased.test.TBGameSeatsSwappedTest org.pente.turnBased.test.CacheTBStorerRedisTest \
         org.pente.turnBased.test.CacheTBStorerGameEndTest org.pente.turnBased.test.CacheTBStorerKidTest \
         org.pente.turnBased.web.test.NewGameServletColorTest org.pente.turnBased.web.test.RenjuTbContractTest; do
  /opt/homebrew/opt/openjdk@21/bin/java --add-opens=java.base/java.lang=ALL-UNNAMED \
      -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner $t | tail -2
done
node dsg_src/httpdocs/gameServer/js/boardCommon.test.js
```

Expected: `BUILD SUCCESSFUL`; each runner prints `OK (N tests)`; the Node test prints `OK: 96 mapping assertions + 2 throw assertions passed`. Record the counts; a red baseline is reported before any change.

---

### Task 1: `TBGame` — whose turn, last turn length

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/TBGame.java` (`getCurrentPlayer` at `:289`, the branch chain ends at `:352`; add the method next to `getNumMoves` at `:362`)
- Test: `dsg_src/java/org/pente/turnBased/test/TBGameTombstoneTest.java` (new)

**Interfaces:**
- Consumes: `TombstoneCodes.currentPlayer(int[], int)`, `TombstoneCodes.lastTurnLength(int[], int)`, `GridStateFactory.TB_TOMBSTONE`.
- Produces: `TBGame.getCurrentPlayer()` correct for `TB_TOMBSTONE`; `public int getLastTurnLength()` (0 when only the centre or nothing, 2 when the last row is a dead code, else 1). Tasks 2 and 3 rely on both.

- [ ] **Step 1: Write the failing test**

```java
package org.pente.turnBased.test;

import junit.framework.*;
import org.pente.game.GridStateFactory;
import org.pente.turnBased.TBGame;

public class TBGameTombstoneTest extends TestCase {
    public TBGameTombstoneTest(String name) { super(name); }
    public static Test suite() { return new TestSuite(TBGameTombstoneTest.class); }

    private static final long P1 = 1001L, P2 = 1002L;

    private TBGame game(int... codes) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_TOMBSTONE);
        g.setPlayer1Pid(P1);
        g.setPlayer2Pid(P2);
        for (int c : codes) g.addMove(c);
        return g;
    }

    public void testNoMovesIsP1()                 { assertEquals(P1, game().getCurrentPlayer()); }
    public void testAfterCentreIsP2()             { assertEquals(P2, game(180).getCurrentPlayer()); }
    public void testAfterOwnPlusDeadIsP1()        { assertEquals(P1, game(180, 181, 561).getCurrentPlayer()); }
    public void testAfterOwnOnlyIsP1()            { assertEquals(P1, game(180, 181).getCurrentPlayer()); }
    public void testAfterPassOnlyIsP1()           { assertEquals(P1, game(180, 722).getCurrentPlayer()); }
    public void testAfterPassDeadThenOwnIsP2()    { assertEquals(P2, game(180, 722, 361, 200).getCurrentPlayer()); }

    public void testLastTurnLength() {
        assertEquals(0, game().getLastTurnLength());
        assertEquals(0, game(180).getLastTurnLength());
        assertEquals(1, game(180, 181).getLastTurnLength());
        assertEquals(2, game(180, 181, 561).getLastTurnLength());
        assertEquals(1, game(180, 722).getLastTurnLength());
        assertEquals(2, game(180, 722, 361).getLastTurnLength());
    }

    /** spec §4.1: passMove stays 361 for Tombstone; a dead stone on point 0 (code 361)
     *  can never sit next to another 361, so Go's double-pass detector never fires. */
    public void testDoublePassNeverFiresWithDeadOnPointZero() {
        TBGame g = game(180, 722, 361, 722, 362, 200);
        assertEquals(-1, g.containsDoublePass());
        assertEquals(TBGame.GO_PLAY, g.getGoState());
        // Two consecutive empty turns: catches passMove being changed to 722 for
        // Tombstone in setGame, which would flip getGoState to GO_MARK_DEAD_STONES.
        TBGame twoEmpty = game(180, 722, 722);
        assertEquals(-1, twoEmpty.containsDoublePass());
        assertEquals(TBGame.GO_PLAY, twoEmpty.getGoState());
    }

    public void testOtherGamesUnchanged() {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setPlayer1Pid(P1); g.setPlayer2Pid(P2);
        g.addMove(180); g.addMove(181);
        assertEquals(P1, g.getCurrentPlayer());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the compile + runner from Global Constraints with `org.pente.turnBased.test.TBGameTombstoneTest`.
Expected: compile error `cannot find symbol: method getLastTurnLength()`.

- [ ] **Step 3: Implement**

In `getCurrentPlayer()`, add `} else if (game == GridStateFactory.TB_TOMBSTONE) { cp = org.pente.game.TombstoneCodes.currentPlayer(getMoves(), getNumMoves()); }` directly before the `TB_CONNECT6` branch (`:328`). Add `public int getLastTurnLength()` returning `TombstoneCodes.lastTurnLength(getMoves(), getNumMoves())`. Leave `setGame`/`passMove` untouched.

- [ ] **Step 4: Run it to verify it passes**

Same command. Expected: `OK (9 tests)`. Re-run `TBGamePassDrawTest` and `TBGameRenjuPhaseTest`: still `OK`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/TBGame.java dsg_src/java/org/pente/turnBased/test/TBGameTombstoneTest.java
git commit -m "Derive the TB Tombstone turn from own entries"
```

---

### Task 2: `CacheTBStorer.storeTombstoneTurn` — atomic whole-turn storage

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` (new public method next to `storeNewMove` at `:1682`; refusal at the top of `storeNewMove`, after the not-found/not-active checks at `:1690-1695`)
- Test: `dsg_src/java/org/pente/turnBased/test/CacheTBStorerTombstoneTest.java` (new)

**Interfaces:**
- Consumes: `TombstoneCodes.validateTurnShape(int[])`, `GridStateFactory.createGridState(TB_TOMBSTONE, MoveData)` → `TombstoneState`, `TombstoneState.isValidMove/addMove/getCurrentPlayer/isAwaitingDeadStone/skipDeadStone/isGameOver/getWinner`, `Utilities.calculateNewTimeout`, `TBMessageThread.nextSeqNbr(List<TBMessage>, int)` (`TBMessageThread.java:38`), the `kidChatPolicy` field (`CacheTBStorer.java:32`), private `persistSet`, `endGameRunnable`, `baseStorer`.
- Produces: `public void storeTombstoneTurn(long gid, int[] turn, TBMessage message) throws TBStoreException`. `message` may be `null`. The method throws `InvalidMoveException` (a `TBStoreException` subclass) for every shape, phase or occupancy problem, and nothing is written on any throw, the message included. A non-null message is attached to the game in the same `persistSet` as the turn, before `endGame` (spec correction 7). `storeNewMove` throws `InvalidMoveException("Tombstone turns must use storeTombstoneTurn")` for `TB_TOMBSTONE` when `game.getNumMoves() > 0`. Task 3 calls `storeTombstoneTurn` through `((CacheTBStorer) tbGameStorer)`.

- [ ] **Step 1: Write the failing test**

The fixture copies `CacheTBStorerGameEndTest`'s setup (`SerializingRedisConnectionManager`, `InMemoryTBGameStorer`, `new CacheTBStorer(base, new InMemoryDSGPlayerStorer(), null, null, null)`, `cache.destroy()` + `RedisConnectionManager.resetInstance()` in `tearDown`). Two recording subclasses:

```java
/** Records base-storer calls in order: "msg:<moveNum>", "move:<moveNum>:<code>" and "update". */
private static class RecordingBase extends InMemoryTBGameStorer {
    final List<String> log = Collections.synchronizedList(new ArrayList<String>());
    final CountDownLatch endLatch = new CountDownLatch(1);
    volatile TBGame endedGame;   // the copy endGameRunnable handed to baseStorer.endGame
    public void storeNewMessage(long gid, TBMessage m) throws TBStoreException {
        log.add("msg:" + m.getMoveNum());
        super.storeNewMessage(gid, m);
    }
    public synchronized void storeNewMove(long gid, int moveNum, int move) throws TBStoreException {
        log.add("move:" + moveNum + ":" + move);
        super.storeNewMove(gid, moveNum, move);
    }
    public void updateGameAfterMove(TBGame game) throws TBStoreException {
        log.add("update");
        super.updateGameAfterMove(game);
    }
    public void endGame(TBGame game) throws TBStoreException { endedGame = game; endLatch.countDown(); }
    /** endGameRunnable calls persistSet(set) just before endSet when the set completes (CacheTBStorer.java:924-932). */
    final CountDownLatch setLatch = new CountDownLatch(1);
    public void endSet(TBSet set) throws TBStoreException { super.endSet(set); setLatch.countDown(); }
}

/** A message from `pid`, built the way MoveServlet builds it (:436-445: moveNum = numMoves + 1, seqNbr 1). */
private static TBMessage msg(long pid, int numMovesBefore, String text) {
    TBMessage m = new TBMessage();
    m.setMessage(text); m.setDate(new Date()); m.setPid(pid);
    m.setMoveNum(numMovesBefore + 1); m.setSeqNbr(1);
    return m;
}

/** Counts set writes (persistSet does exactly one hput into SID_TO_TB_SET). */
private static class CountingRedis extends SerializingRedisConnectionManager {
    volatile int setWrites;
    public <T extends java.io.Serializable> void hput(String ns, String field, T value) {
        if (RedisConnectionManager.SID_TO_TB_SET.equals(ns)) setWrites++;
        super.hput(ns, field, value);
    }
}

/** Active TB_TOMBSTONE game with `history` already stored, warmed into the cache. */
private long active(long sid, boolean undoRequested, int... history) throws Exception {
    TBGame g = new TBGame();
    g.setGame(GridStateFactory.TB_TOMBSTONE);
    g.setState(TBGame.STATE_ACTIVE);
    g.setPlayer1Pid(1001L); g.setPlayer2Pid(1002L);
    g.setDaysPerMove(3); g.setLastMoveDate(new Date());
    g.setUndoRequested(undoRequested);
    for (int c : history) g.addMove(c);
    TBSet set = new TBSet(sid, g, null);
    set.setPlayer1Pid(1001L); set.setPlayer2Pid(1002L); set.setState(TBSet.STATE_ACTIVE);
    base.createSet(set);
    cache.loadSet(sid);
    base.log.clear();
    return set.getGame1().getGid();
}

private int[] stored(long gid) throws Exception { return cache.loadGame(gid).getMoves(); }
```

Positions used below (x + 19*y): centre `180` = K10. Five for P1 along row y=9: `180,181,182,183` + `184`. P2 filler on row 0: `0,2,4,6`. Capture: P1 `180`, P2 `181,182`, P1 plays `183` → removes `181,182`.

Tests (each asserts `stored(gid)`, `base.log`, and for the rejections that `base.log` stays empty and `stored(gid)` is unchanged):

| Test | History | `turn` | Expect |
|---|---|---|---|
| `testOwnPlusDead` | `180` | `{181, 361+200}` | stored `[180,181,561]`; log `[move:1:181, move:2:561, update]`; current player now P1 (`1001L`) |
| `testOwnOnly` | `180` | `{181}` | stored `[180,181]`; log `[move:1:181, update]` |
| `testPassPlusDead` | `180` | `{722, 361}` | stored `[180,722,361]` (dead on point 0 survives) |
| `testPassOnly` | `180` | `{722}` | stored `[180,722]`; game still `STATE_ACTIVE` |
| `testDeadDiscardedAfterWinningStone` | `180,0,181,2,182,4,183,6` | `{184, 361+100}` | stored ends `…,6,184` (no 461); log `[move:8:184, update]`; `loadGame(gid).getWinner() == 1`; game not active; `base.endLatch.await(5, SECONDS)` true |
| `testSixthEmptyTurnDraws` | `180,722,722,722,722,722` | `{722}` | stored length 7; `getWinner() == 0`; not active; `endLatch` released |
| `testDeadOnJustCapturedPointAccepted` | `180,181,300,182` | `{183, 361+181}` | stored ends `183,542` |
| `testDeadOnOwnPointRejected` | `180` | `{181, 361+181}` | `InvalidMoveException`; nothing stored |
| `testOccupiedOwnPointRejected` | `180` | `{180}` | `InvalidMoveException`; nothing stored |
| `testOwnPassOnTurnZeroRejected` | *(none)* | `{722}` | `InvalidMoveException` (turn 0 is the centre only) |
| `testBadShapesRejectedWithSpecMessage` | `180` | `{}`, `{181,561,562}`, `{561}`, `{181,182}` | `InvalidMoveException` whose `getMessage()` equals the `validateTurnShape` text for that input (`Invalid turn: send one or two moves.` ×2, `Invalid turn: the first move must be your stone or a pass.`, `Invalid turn: the second move must be a dead stone.`) |
| `testRejectedSecondEntryWritesNothing` | `180` | `{181, 361+180}` (dead on the occupied centre, after a valid own stone) | `InvalidMoveException`; `base.log` empty; `stored(gid)` still `[180]`; `redis.setWrites` unchanged |
| `testSinglePersistSetPerTurn` | `180` | `{181, 561}` with `msg(1002L, 1, "hi")` | `redis.setWrites` goes up by exactly **1** (reset the counter after `active(...)`); the message adds no second write |
| `testMessageStoredAtPostTurnRowCount` | `180` | `{181, 561}` with `msg(1002L, 1, "hi")` | `loadGame(gid).getMessage(3)` has text `hi`; `getMessage(2)` is null; log `[msg:3, move:1:181, move:2:561, update]` |
| `testWinningTurnMessageReachesEndGameAndSurvivesSetEnd` | `180,0,181,2,182,4,183,6` | `{184, 361+100}` with `msg(1001L, 8, "gg")` | `endLatch` and `setLatch` released within 5 s (seconds); `base.endedGame.getMessage(base.endedGame.getNumMoves())` has text `gg` (the runnable's `REASON_WIN` prefix source, `:1218-1222`); after `setLatch`, `loadGame(gid).getMessage(9)` still has text `gg` (the runnable's set `persistSet` did not wipe it); log starts `[msg:9, move:8:184, …]` |
| `testRejectedTurnStoresNoMessage` | `180` | `{181, 361+180}` with `msg(1002L, 1, "hi")` | `InvalidMoveException`; `base.log` empty; `loadGame(gid).getMessages()` empty |
| `testPendingUndoRowOverwrittenAndCleared` | `180,181` with `undoRequested=true` | `{722}` (P1) | first logged write is `move:2:722` (the slot holding the `-1` row); `loadGame(gid).isUndoRequested()` false |
| `testStoreNewMoveRefusesTombstoneAfterCentre` | `180` | `cache.storeNewMove(gid, 1, 181)` | `InvalidMoveException` with message `Tombstone turns must use storeTombstoneTurn` |
| `testStoreNewMoveStillWritesCentre` | *(none)* | `cache.storeNewMove(gid, 0, 180)` | stored `[180]` (createSet's auto-start path, `CacheTBStorer.java:1489`) |

`setUp` installs `redis = new CountingRedis()` instead of the plain serializing manager. Rows that name no message call `cache.storeTombstoneTurn(gid, turn, null)`.

- [ ] **Step 2: Run it to verify it fails**

Runner with `org.pente.turnBased.test.CacheTBStorerTombstoneTest`.
Expected: compile error `cannot find symbol: method storeTombstoneTurn(long,int[],TBMessage)`.

- [ ] **Step 3: Implement `public void storeTombstoneTurn(long gid, int[] turn, TBMessage message) throws TBStoreException`**

Order (spec §10.2, with correction 2):
1. `loadGame(gid)`; null → `TBStoreException("Invalid game, not found: " + gid)`; not `STATE_ACTIVE` → `TBStoreException("Game is not active: " + gid)` (same texts as `storeNewMove`). `cacheStats.incrementMovesMade()` once.
2. `String err = TombstoneCodes.validateTurnShape(turn)`; non-null → `throw new InvalidMoveException(err)`.
3. `TombstoneState s = (TombstoneState) GridStateFactory.createGridState(GridStateFactory.TB_TOMBSTONE, game);`
4. `!s.isValidMove(turn[0], s.getCurrentPlayer())` → `InvalidMoveException("Invalid move [" + turn[0] + "] for Tombstone Pente game: " + gid)`; else `s.addMove(turn[0])`; collect `turn[0]` into a `kept` list.
5. If `turn.length == 2`: when `s.isGameOver()`, drop `turn[1]` (log4j.debug "dead stone discarded after win"); otherwise validate it the same way and `s.addMove(turn[1])`, add to `kept`.
6. `if (!s.isGameOver() && s.isAwaitingDeadStone()) s.skipDeadStone();`
7. Commit, mirroring `storeNewMove:1760-1807` line for line: `int firstMoveNum = game.getNumMoves()`; `game.addMove(code)` for each kept code; `newTimeout = Utilities.calculateNewTimeout(game, dsgPlayerStorer)` **once, outside the lock**; under `cacheTbLock`: `setTimeoutDate`, `setUndoRequested(false)`, `setDrawOffered(false)`, `gameOver = s.isGameOver()` → `game.end(); game.setWinner(s.getWinner());`, then the message (below), then `persistSet(game.getTbSet())` once. After the lock, `baseStorer.storeNewMessage(gid, message)` when a message was attached. Then if `gameOver`: `endGameRunnable.endGame(game, game.getWinner() == 0 ? REASON_DRAW : REASON_WIN)`. Then `baseStorer.storeNewMove(gid, firstMoveNum + k, kept[k])` in order (no AI branch: Tombstone has no AI, D8). Finally `baseStorer.updateGameAfterMove(game)`.

Message (spec correction 7): attach it only when `message != null` and the kid-chat rule allows it. Use the same test as `storeNewMessage` (`CacheTBStorer.java:1835-1840`): a non-null `kidChatPolicy` whose `allowed(message.getPid(), game.getOpponent(message.getPid()))` is false drops the message with the same debug log, and the turn is still stored. When attached, inside the lock after the kept codes are added: `message.setMoveNum(game.getNumMoves())` (the row count after the turn, so a discarded dead stone is not counted), `message.setSeqNbr(TBMessageThread.nextSeqNbr(game.getMessages(), game.getNumMoves()))`, then `game.addMessage(message)`. This puts the message on the same game object that `endGame` receives, so the runnable's `REASON_WIN` email prefix (`:1218-1222`) and its set `persistSet` (`:924-932`) both see it.

All validation (steps 1-6) runs before any mutation of `game` or `message`, so a rejected turn writes nothing.

In `storeNewMove`, after the active check: `if (game.getGame() == GridStateFactory.TB_TOMBSTONE && game.getNumMoves() > 0) throw new InvalidMoveException("Tombstone turns must use storeTombstoneTurn");` (spec correction 1).

- [ ] **Step 4: Run it to verify it passes**

Same runner. Expected: `OK (19 tests)`. Re-run `CacheTBStorerRedisTest`, `CacheTBStorerGameEndTest`, `CacheTBStorerKidTest`: still `OK`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/test/CacheTBStorerTombstoneTest.java
git commit -m "Store TB Tombstone turns atomically"
```

---

### Task 3: `MoveServlet` — move branch and whole-turn undo

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java` (new branch immediately before `} else if (game.getGame() == GridStateFactory.TB_CONNECT6) {` at `:621`; `acceptUndo` count at `:304-308`)
- Test: `dsg_src/java/org/pente/turnBased/web/test/MoveServletTombstoneTest.java` (new)

**Interfaces:**
- Consumes: Task 1 `TBGame.getLastTurnLength()`, Task 2 `CacheTBStorer.storeTombstoneTurn`, `TombstoneCodes.validateTurnShape`.
- Produces: `public static int undoEntryCount(TBGame game)` (public so the `web.test` package can call it): `TB_TOMBSTONE` → `game.getLastTurnLength()`; `TB_CONNECT6` → 2; else 1.

- [ ] **Step 1: Write the failing test**

```java
public void testUndoEntryCountTombstone() {
    assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 181, 561)));
    assertEquals(1, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 722)));
    assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180, 722, 361)));
    assertEquals(0, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_TOMBSTONE, 180)));
}
public void testUndoEntryCountOtherGamesUnchanged() {
    assertEquals(2, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_CONNECT6, 180, 181, 182)));
    assertEquals(1, MoveServlet.undoEntryCount(tb(GridStateFactory.TB_PENTE, 180, 181)));
}
```

(`tb(int game, int... codes)` builds a `TBGame` with `setGame` + `addMove`, as in Task 1.)

- [ ] **Step 2: Run it to verify it fails**

Runner with `org.pente.turnBased.web.test.MoveServletTombstoneTest`. Expected: `cannot find symbol: method undoEntryCount`.

- [ ] **Step 3: Implement**

- `acceptUndo` (`:304-308`): replace the `numMoves = 1 (+1 for Connect6)` lines with `int numMoves = undoEntryCount(game);` and wrap the `undoLastMove` call and the notification in `if (numMoves > 0)`, inside the existing `getNumMoves() > 1` guard.
- `move` command, new branch before Connect6 (spec §10.3). The shape check runs first, so `moves[1]` is never read for a 1-code turn:

```java
} else if (game.getGame() == GridStateFactory.TB_TOMBSTONE) {
    String err = TombstoneCodes.validateTurnShape(moves);
    if (err != null) {
        log4j.error("MoveServlet, invalid tombstone turn " + moveStr + ": " + gid);
        handleError(request, response, err);
        return;
    }
    // The storer sets moveNum/seqNbr to the post-turn row count and attaches the
    // message before endGame (spec correction 7); never via openingMessage.
    ((CacheTBStorer) tbGameStorer).storeTombstoneTurn(game.getGid(), moves, message);
}
```

The branch leaves `openingMessage` null, so the post-refresh path (`:711-718`) does not store the message a second time. Nothing else in the servlet changes: the turn gate (`:386-389`), the draw-offer gate (`:423`), the refresh, notifications and redirects already apply. `InvalidMoveException` from the storer lands in the existing `catch` (`:781-783`) → `Invalid move.`

- [ ] **Step 4: Run it to verify it passes**

Same runner. Expected: `OK (2 tests)`. Then `grep -n "TB_TOMBSTONE" dsg_src/java/org/pente/turnBased/web/MoveServlet.java` shows the new branch on a line **before** the `TB_CONNECT6) {` line printed by `grep -n "TB_CONNECT6) {"`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/MoveServlet.java dsg_src/java/org/pente/turnBased/web/test/MoveServletTombstoneTest.java
git commit -m "Accept whole Tombstone turns and undo whole turns in the TB servlet"
```

---

### Task 4: Keep Tombstone out of TB King of the Hill and tournaments

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/NewGameServlet.java` (static helper next to `isBlackFirst` at `:465`; call before the `if (koth && error == null)` block at `:229`)
- Modify: `dsg_src/httpdocs/gameServer/tb/newKotH.jsp` (game list loop `:146-153`)
- Modify: `dsg_src/httpdocs/gameServer/admin/newTourney.jsp` (post handling `:21-24`, success message `:99-101`, game list loop `:117-122`)
- Test: `dsg_src/java/org/pente/turnBased/web/test/NewGameServletTombstoneTest.java` (new)

**Interfaces:**
- Consumes: `GridStateFactory.isTombstone(int)`.
- Produces: `public static String kothUnavailableError(int game)` → `"King of the Hill is not available for Tombstone Pente."` for `TB_TOMBSTONE` (and any `isTombstone` id), else `null`.

- [ ] **Step 1: Write the failing test**

```java
public void testKothRejectedForTombstone() {
    assertEquals("King of the Hill is not available for Tombstone Pente.",
            NewGameServlet.kothUnavailableError(GridStateFactory.TB_TOMBSTONE));
}
public void testKothAllowedForOtherTbGames() {
    assertNull(NewGameServlet.kothUnavailableError(GridStateFactory.TB_PENTE));
    assertNull(NewGameServlet.kothUnavailableError(GridStateFactory.TB_RENJU));
}
```

- [ ] **Step 2: Run it to verify it fails**

Runner with `org.pente.turnBased.web.test.NewGameServletTombstoneTest`. Expected: `cannot find symbol: method kothUnavailableError(int)`.

- [ ] **Step 3: Implement**

- `NewGameServlet`: add the helper; directly before `if (koth && error == null) {` (`:229`) insert `if (koth && error == null) { error = kothUnavailableError(game); }`. The existing block then skips `kothStorer.loadHill(game)`, and the error branch (`:436-447`) shows the message. `newKotH.jsp` sends no `difficulty` parameter (it has only the hidden `koth` input, `:268`), so the page used is `errorRedirectPage` (`/gameServer/tb/new.jsp`, `:31`, dispatched at `:438-440`), not `kothErrorRedirectPage`. `tb/new.jsp` shows `request.getAttribute("error")` (`:205`), so no routing change is needed.
- `tb/newKotH.jsp`: in the loop at `:147`, first statement `if (GridStateFactory.isTombstone(games[i].getId())) continue;`.
- `admin/newTourney.jsp`:
  - Before `if (action != null && action.equals("add")) {` (`:21`) add:
    ```jsp
    String tourneyError = null;
    if ("add".equals(action) && GridStateFactory.isTombstone(Integer.parseInt(request.getParameter("game")))) {
       tourneyError = "Tournaments are not available for Tombstone Pente.";
       action = null;   // skip the insert block below
    }
    ```
  - Next to the success message (`:99-101`): `<% if (tourneyError != null) { %><b><%= tourneyError %></b><br><% } %>`.
  - In the list loop (`:118`), first statement `if (GridStateFactory.isTombstone(games[i].getId())) continue;`. The `i == 1` "selected" default is unaffected (index 1 is Pente).

- [ ] **Step 4: Verify**

Runner: `OK (2 tests)`. Then:
```bash
grep -n "isTombstone" dsg_src/httpdocs/gameServer/tb/newKotH.jsp dsg_src/httpdocs/gameServer/admin/newTourney.jsp
```
Expected: one hit in `newKotH.jsp`, two in `newTourney.jsp`. The JSPs are checked in the browser in Task 10.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/NewGameServlet.java dsg_src/java/org/pente/turnBased/web/test/NewGameServletTombstoneTest.java \
        dsg_src/httpdocs/gameServer/tb/newKotH.jsp dsg_src/httpdocs/gameServer/admin/newTourney.jsp
git commit -m "Keep Tombstone Pente out of TB King of the Hill and tournaments"
```

---

### Task 5: `TombstoneTurnRows` — one move-list row per turn for every JSP

Six pages render a move list server-side (`tb/mobileGame.jsp`, `tb/cancelReply.jsp`, `tb/undoReply.jsp`, `viewLiveGameMobile.jsp`, `viewLiveGameEmbed.jsp`, `viewGameEmbed.jsp`). They read moves from a `TBGame`, a `GameData` or an `ArrayList<Integer>`. One tested helper keeps the grouping and labels identical across them.

**Files:**
- Create: `dsg_src/java/org/pente/turnBased/web/TombstoneTurnRows.java`
- Test: `dsg_src/java/org/pente/turnBased/web/test/TombstoneTurnRowsTest.java` (new)

**Interfaces:**
- Consumes: `TombstoneCodes.isOwnEntry/isDeadStone/turnLabel/entryLabel`, `GridStateFactory.getGameId(String)`, `GridStateFactory.isTombstone(int)`, `org.pente.game.MoveData`.
- Produces (Tasks 7-9 use exactly these):

```java
public final class TombstoneTurnRows {
    public static final class Row {
        public final int number;   // 1-based turn number
        public final int start;    // first entry index
        public final int end;      // last entry index (the cell id / selectMove target)
        public final int own;      // own code, or -1 for an orphan dead row
        public final int dead;     // dead code, or -1
        public final String label; // TombstoneCodes.turnLabel(own, dead), or entryLabel(dead) for an orphan, "?" for a code outside 0..722
    }
    public static boolean isTombstoneGameName(String gameName); // never throws (null or unknown name -> false); true for "Tombstone Pente" and "Speed Tombstone Pente"
    public static List<Row> rows(int[] codes, int n);
    public static List<Row> rows(MoveData data);           // rows(data.getMoves(), data.getNumMoves())
    public static List<Row> rows(java.util.List<Integer> codes);
}
```

Grouping rule (mirrors the JS `tombstoneTurns` of Task 6): entry 0 starts row 1 and never takes a dead stone (turn 0 is the centre only). Every later own entry (`0..360` or `722`) starts a new row. A dead entry joins the newest row only when that row is not row 1, its own entry is a valid own code (`0..360` or `722`) and it has no dead yet; otherwise it becomes its own orphan row. A dead entry therefore never joins a `"?"` row, which would make the label `turnLabel(invalidCode, dead)` and throw from Plan A's `entryLabel`. Any other code becomes its own `"?"` row. Never throws.

- [ ] **Step 1: Write the failing test**

```java
private static String labels(List<TombstoneTurnRows.Row> rows) {
    StringBuilder b = new StringBuilder();
    for (TombstoneTurnRows.Row r : rows) b.append(r.number).append(':').append(r.start).append('-')
            .append(r.end).append(':').append(r.label).append('|');
    return b.toString();
}
public void testEmpty() { assertEquals(0, TombstoneTurnRows.rows(new int[0], 0).size()); }
public void testCentreOnly() { assertEquals("1:0-0:K10|", labels(TombstoneTurnRows.rows(new int[]{180}, 1))); }
public void testMixedTurns() {
    int[] c = {180, 181, 561, 722, 722, 361, 200};
    assertEquals("1:0-0:K10|2:1-2:L10 + dead L9|3:3-3:PASS|4:4-5:PASS + dead A19|5:6-6:L9|",
            labels(TombstoneTurnRows.rows(c, c.length)));
}
public void testOrphanDeadEntryGetsOwnRow() {
    assertEquals("1:0-0:K10|2:1-1:dead L9|", labels(TombstoneTurnRows.rows(new int[]{180, 561}, 2)));
    assertEquals("1:0-0:K10|2:1-2:L10 + dead L9|3:3-3:dead M9|",
            labels(TombstoneTurnRows.rows(new int[]{180, 181, 561, 562}, 4)));
}
public void testOutOfRangeCodeNeverThrows() {
    assertEquals("1:0-0:K10|2:1-1:?|", labels(TombstoneTurnRows.rows(new int[]{180, 999}, 2)));
    // a dead entry after a "?" row never joins it (turnLabel(999, 561) would throw)
    assertEquals("1:0-0:K10|2:1-1:?|3:2-2:dead L9|", labels(TombstoneTurnRows.rows(new int[]{180, 999, 561}, 3)));
}
public void testRespectsN() { assertEquals("1:0-0:K10|", labels(TombstoneTurnRows.rows(new int[]{180, 181}, 1))); }
public void testMoveDataAndListOverloadsAgree() {
    TBGame g = new TBGame(); g.setGame(GridStateFactory.TB_TOMBSTONE);
    for (int c : new int[]{180, 181, 561}) g.addMove(c);
    String expect = labels(TombstoneTurnRows.rows(g.getMoves(), g.getNumMoves()));
    assertEquals(expect, labels(TombstoneTurnRows.rows(g)));
    assertEquals(expect, labels(TombstoneTurnRows.rows(java.util.Arrays.asList(180, 181, 561))));
}
public void testIsTombstoneGameNameCoversSpeed() {
    assertTrue(TombstoneTurnRows.isTombstoneGameName("Tombstone Pente"));
    assertTrue(TombstoneTurnRows.isTombstoneGameName("Speed Tombstone Pente"));
    assertTrue(!TombstoneTurnRows.isTombstoneGameName("Pente"));
    assertTrue(!TombstoneTurnRows.isTombstoneGameName(null));
    assertTrue(!TombstoneTurnRows.isTombstoneGameName("No Such Game"));   // getGameId throws; the helper must not
}
```

(Coordinates: `181` = L10, `561 - 361 = 200` = L9, `361 - 361 = 0` = A19, `562 - 361 = 201` = M9.)

- [ ] **Step 2: Run it to verify it fails**

Runner with `org.pente.turnBased.web.test.TombstoneTurnRowsTest`. Expected: `cannot find symbol: class TombstoneTurnRows`.

- [ ] **Step 3: Implement** the class per the Interfaces block and grouping rule. `isTombstoneGameName` returns `false` for `null`, otherwise `GridStateFactory.isTombstone(GridStateFactory.getGameId(name))` inside `try { … } catch (IllegalArgumentException e) { return false; }`. `getGameId` throws `IllegalArgumentException("Invalid game: " + gameName)` for an unknown name (`GridStateFactory.java:408-420`).

- [ ] **Step 4: Run it to verify it passes**

Expected: `OK (8 tests)`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/TombstoneTurnRows.java dsg_src/java/org/pente/turnBased/web/test/TombstoneTurnRowsTest.java
git commit -m "Add a per-turn move-list helper for Tombstone pages"
```

---

### Task 6: Shared JS — Tombstone helpers, gray stones, dispatcher cases

Every board page includes `gameConstants.jspf`, then `tb/gameScript.js`, then `js/boardCommon.js`. `boardCommon.js` throws on unknown ids (`:43`, `:82`), so until this task lands every Tombstone page dies in the console.

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/gameConstants.jspf` (after `TB_RENJU` at `:51`; add the missing comma)
- Modify: `dsg_src/httpdocs/gameServer/tb/gameScript.js` (colour after `renjuColor` `:14`; state after `c6Move1` `:38`; `drawStone` `:683`; new functions appended after `replayOPenteGame` at the end of the file)
- Modify: `dsg_src/httpdocs/gameServer/js/boardCommon.js` (both switches + header comment)
- Modify: `dsg_src/httpdocs/gameServer/js/boardCommon.test.js` (`base` `:18-22`, `FAMILIES`, `REPLAY_FNS`)
- Create: `dsg_src/httpdocs/gameServer/js/tombstone.test.js`

**Interfaces:**
- Consumes: page globals `GAME`, `gridSize`, `whiteCaptures`, `blackCaptures`, `coordinateLetters`, `detectPenteCapture` (`gameScript.js:955`), `resetAbstractBoard` (`:1500`), `drawRedDot` (`:670`).
- Produces (globals, all `var`/`function` declarations; spec §16.4 plus the helpers marked *new*, which Tasks 7-9 use):

| Name | Signature → result |
|---|---|
| `tombstoneColor`, `TOMBSTONE_DEAD_OFFSET`, `TOMBSTONE_OWN_PASS`, `TOMBSTONE_DEAD` | `"#D7BDE2"`, `361`, `722`, `3` |
| `tsOwn`, `tsDead` | TB board picks, `-1` when unset (`tsOwn` may be `722`) |
| `isTombstoneGame(g)` | `true` for `GAME.TOMBSTONE`, `GAME.SPEED_TOMBSTONE`, `GAME.TB_TOMBSTONE` |
| `tombstoneDecode(code)` | `{kind:'own'|'pass'|'dead'|'invalid', point}`; `point` is `-1` for pass/invalid |
| `tombstoneTurns(movesList, until)` | `[{player, own, dead, start, end}]` over the first `until` entries (`until` defaults to `movesList.length`); same grouping rule as Task 5; `player` is the own-entry ordinal rule (ordinal 0 → 1, odd → 2, even → 1); an orphan dead row has `own: -1, player: 0`; an invalid code is its own row with `own: code, player: 0` |
| `tombstoneCurrentPlayer(movesList)` *new* | own-entry count `% 2 + 1` (`[]` → 1) |
| `replayTombstoneGame(abstractBoard, movesList, until)` | reset; own stone → cell = player, then `detectPenteCapture(board, x, y, player)`; pass → nothing; dead → cell = `3`; invalid → skipped. No tournament `-1` mask |
| `tombstoneLabel(code)` | `'K10'` \| `'PASS'` \| `'dead K10'` \| `'?'` |
| `tombstoneTurnLabel(turn)` | `own === -1` → `tombstoneLabel(dead)`; else `tombstoneLabel(own)` plus `' + ' + tombstoneLabel(dead)` when `dead !== -1` |
| `tombstoneTbButtonState(tsOwn, tsDead)` | `{label:'Place only a dead stone', action:'ownPass'}` \| `{label:'Submit without dead stone', action:'submitOwn'}` \| `{label:'Submit', action:'submitBoth'}` |
| `tombstoneTbClick(tsOwn, tsDead, point, cellValue)` *new* | `{tsOwn, tsDead}`: occupied cell (`cellValue !== 0`, preview stones included) → both `-1`; else `tsOwn === -1` → `tsOwn = point`; else `tsDead === -1` → `tsDead = point`; else unchanged |
| `tombstonePreviewCodes(tsOwn, tsDead)` *new* | `[]` \| `[tsOwn]` \| `[tsOwn, 361 + tsDead]` |
| `tombstoneSubmitMoves(tsOwn, tsDead)` *new* | `null` when `tsOwn === -1`; else `"<tsOwn>"` or `"<tsOwn>,<361+tsDead>"` |
| `tombstoneLastMarks(movesList, until)` *new* | points of the last turn within `until`: its own stone point (none for a pass) then its dead point |
| `tombstoneStep(movesList, until, dir)` *new* | the adjacent turn boundary (`end + 1` values of `tombstoneTurns(movesList)`): `dir < 0` → largest boundary `< until`, else the first boundary; `dir > 0` → smallest boundary `> until`, else `movesList.length` |
| `tombstoneDrawLastMarks(movesList, until)` *new* | `drawRedDot(p % 19, Math.floor(p / 19))` for each `tombstoneLastMarks` point |

The click rule for `tombstoneTbClick`: any occupied cell clears both picks, matching the existing "click on an occupied cell resets" behaviour (`mobileGame.jsp:1259-1271`). This also gives the player a way out of "Place only a dead stone" without reloading (spec §11.2 assumed a reload was needed).

- [ ] **Step 1: Write the failing tests**

`boardCommon.test.js`: add `TOMBSTONE: 33` to `base`; add `{ names: ['TOMBSTONE'], color: 'tombstoneColor', replay: 'replayTombstoneGame' }` to `FAMILIES`; add `'replayTombstoneGame'` to `REPLAY_FNS`.

`tombstone.test.js` (same `fs`/`vm`/`assert` pattern as `boardCommon.test.js`). It evaluates the **whole** `gameScript.js` in a sandbox with a stub DOM, which works because the only top-level DOM use is `document.getElementById(...).getContext("2d")` (`gameScript.js:42-45`):

```js
const canvas = { getContext: () => ({}) };
const GAME = { PENTE: 1, RENJU: 31, TB_RENJU: 81, TOMBSTONE: 33, SPEED_TOMBSTONE: 34, TB_TOMBSTONE: 83 };
const sandbox = { document: { getElementById: () => canvas }, GAME, gridSize: 19, rated: false, moves: [] };
const ctx = vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../tb/gameScript.js'), 'utf8'), ctx);
const cell = (x, y) => ctx.abstractBoard[x][y];
const replay = (m) => { ctx.whiteCaptures = 0; ctx.blackCaptures = 0; ctx.replayTombstoneGame(ctx.abstractBoard, m, m.length); };
```

Assertions (use `assert.deepStrictEqual` on `JSON.parse(JSON.stringify(x))` for objects created inside the context; print `OK: <n> tombstone assertions passed` at the end):

- **decode**: `0 → own 0`, `360 → own 360`, `361 → dead 0`, `721 → dead 360`, `722 → pass -1`, `723` and `-1 → invalid -1`.
- **isTombstoneGame**: `33, 34, 83` true; `1, 31, 81` false.
- **turns**: `tombstoneTurns([180,181,561,722,722,361,200])` equals `[{player:1,own:180,dead:-1,start:0,end:0},{player:2,own:181,dead:561,start:1,end:2},{player:1,own:722,dead:-1,start:3,end:3},{player:2,own:722,dead:361,start:4,end:5},{player:1,own:200,dead:-1,start:6,end:6}]`; with `until = 3` only the first two rows.
- **orphan dead**: `tombstoneTurns([180,561])[1]` equals `{player:0,own:-1,dead:561,start:1,end:1}`; `tombstoneTurns([180,181,561,562]).length === 3`; a dead entry after an invalid code stays an orphan: `tombstoneTurns([180,999,561])` has 3 rows, `[1]` is `{player:0,own:999,dead:-1,start:1,end:1}` and `[2]` is `{player:0,own:-1,dead:561,start:2,end:2}`.
- **current player**: `[] → 1`, `[180] → 2`, `[180,181,561] → 1`, `[180,722] → 1`, `[180,722,361,200] → 2`.
- **replay capture + dead on the vacated point**: `replay([180,181,300,182,183,542])` → `cell(9,9)=1`, `cell(10,9)=3`, `cell(11,9)=0`, `cell(12,9)=1`, `cell(15,15)=1`, `ctx.blackCaptures === 2`.
- **dead stone blocks a capture**: `replay([180,181,300,543,0,183])` → `cell(10,9)=2`, `cell(11,9)=3`, `cell(12,9)=1`, `ctx.blackCaptures === 0`.
- **pass places nothing**: `replay([180,722])` → only `cell(9,9)` is non-zero.
- **labels**: `tombstoneLabel(180)='K10'`, `(722)='PASS'`, `(561)='dead L9'`, `(999)='?'`; turn labels `{own:181,dead:561}→'L10 + dead L9'`, `{own:722,dead:361}→'PASS + dead A19'`, `{own:181,dead:-1}→'L10'`, `{own:722,dead:-1}→'PASS'`, `{own:-1,dead:561}→'dead L9'`.
- **button state**: `(-1,-1)` → Place only a dead stone/ownPass; `(181,-1)` and `(722,-1)` → Submit without dead stone/submitOwn; `(181,200)` → Submit/submitBoth.
- **click**: `(-1,-1,181,0)→(181,-1)`; `(181,-1,200,0)→(181,200)`; `(722,-1,200,0)→(722,200)`; `(181,-1,181,2)→(-1,-1)`; `(181,200,200,3)→(-1,-1)`; `(181,200,50,0)→(181,200)`.
- **preview/submit**: `(-1,-1)→[]/null`; `(181,-1)→[181]/'181'`; `(181,200)→[181,561]/'181,561'`; `(722,-1)→[722]/'722'`; `(722,0)→[722,361]/'722,361'`.
- **last marks**: `([180,181,561],3)→[181,200]`; `(…,2)→[181]`; `(…,1)→[180]`; `(…,0)→[]`; `([180,722],2)→[]`; `([180,722,361],3)→[0]`.
- **step** on `m = [180,181,561,722,200]` (boundaries 1, 3, 4, 5): `(m,5,-1)=4`, `(m,4,-1)=3`, `(m,3,-1)=1`, `(m,1,-1)=1`, `(m,2,-1)=1`, `(m,1,+1)=3`, `(m,2,+1)=3`, `(m,4,+1)=5`, `(m,5,+1)=5`.
- **drawLastMarks**: after `ctx.drawRedDot = (i, j) => calls.push([i, j])`, `tombstoneDrawLastMarks([180,181,561],3)` records `[[10,9],[10,10]]`.

- [ ] **Step 2: Run them to verify they fail**

```bash
node dsg_src/httpdocs/gameServer/js/boardCommon.test.js
node dsg_src/httpdocs/gameServer/js/tombstone.test.js
```
Expected: the first fails with `getBoardColor: unknown game id 33`; the second with `ctx.replayTombstoneGame is not a function` (or the first missing helper).

- [ ] **Step 3: Implement**

- `gameConstants.jspf`: `TOMBSTONE: <%= GridStateFactory.TOMBSTONE %>`, `SPEED_TOMBSTONE: <%= GridStateFactory.SPEED_TOMBSTONE %>`, `TB_TOMBSTONE: <%= GridStateFactory.TB_TOMBSTONE %>` after `TB_RENJU` (which gains a trailing comma).
- `gameScript.js`: `var tombstoneColor = "#D7BDE2";` on its own line directly after `renjuColor` (keeps `boardCommon.test.js`'s `^var\s+\w+Color\s*=` extraction working); `var tsOwn = -1, tsDead = -1;` after the `c6Move1` declaration; `var TOMBSTONE_DEAD_OFFSET = 361, TOMBSTONE_OWN_PASS = 722, TOMBSTONE_DEAD = 3;` with them. In `drawStone`, before the `color < 1 || color > 2` guard, a `color === 3` branch that draws the same circle and shadow as the other colours with a radial gradient `#C8C8C8` → `#6E6E6E`, then returns. Append the functions from the Interfaces table.
- `boardCommon.js`: `case GAME.TOMBSTONE: case GAME.SPEED_TOMBSTONE: case GAME.TB_TOMBSTONE:` → `return tombstoneColor;` and → `replayTombstoneGame(abstractBoard, movesList, until); break;`. Add `tombstoneColor` and `replayTombstoneGame` to the header's dependency list.

- [ ] **Step 4: Run them to verify they pass**

Same two commands. Expected: `OK: 102 mapping assertions + 2 throw assertions passed` and `OK: <n> tombstone assertions passed`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/httpdocs/gameServer/gameConstants.jspf dsg_src/httpdocs/gameServer/tb/gameScript.js \
        dsg_src/httpdocs/gameServer/js/boardCommon.js dsg_src/httpdocs/gameServer/js/boardCommon.test.js \
        dsg_src/httpdocs/gameServer/js/tombstone.test.js
git commit -m "Add Tombstone board helpers, gray dead stones and viewer dispatch"
```

---

### The page pattern (used by Tasks 7, 8 and 9)

Every board page has the same four places that assume one entry per move. Each gets a Tombstone arm; the non-Tombstone code is left exactly as it is.

- **P1 — move table.** Server-side, wrap the existing per-entry loop: `<% if (isTombstone) { for (TombstoneTurnRows.Row r : TombstoneTurnRows.rows(<moves>)) { %>` one `<tr>` with `<td width="10%" align="center"><%= r.number %></td>` and `<td onclick='selectMove(<%= r.end %>)' id='<%= r.end %>' width="90%" align="center"><%= r.label %></td>` `<% } } else { %>` *(existing loop unchanged)* `<% } %>`. The cell id is the turn's **end** entry, which is what `selectMove` highlights.
- **P2 — markers.** In `selectMove`, and anywhere else a block draws `drawRedDot(lastMove % …)` for the current view, use `if (isTombstoneGame(game)) { tombstoneDrawLastMarks(moves, drawUntilMove); } else { /* existing lines */ }`.
- **P3 — stepping.** First statement of `goBack()` / `goForward()`: `if (isTombstoneGame(game)) { if (moves.length > 0) selectMove(tombstoneStep(moves, drawUntilMove, -1 /* or +1 */) - 1); return; }`. `selectMove` already replays to `newMove + 1` and redraws.
- **P4 — reset to the end.** The page-load tail after `init()` and any handler that jumps back to the latest position: for Tombstone, `selectMove(moves.length - 1)` replaces the existing red-dot + `selectMove(drawUntilMove - 1/-2)` lines.

`isTombstone` (server side) is `game.getGame() == GridStateFactory.TB_TOMBSTONE` on TB pages and `TombstoneTurnRows.isTombstoneGameName(game.getGame())` on archive viewers (Global Constraints: never `gameId == 83`). Pages that import only `TBEmoticon` from `org.pente.turnBased.web` (`cancelReply.jsp:3`, `undoReply.jsp:3`) use the fully qualified `org.pente.turnBased.web.TombstoneTurnRows`.

---

### Task 7: TB board `tb/mobileGame.jsp`

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`

**Interfaces:**
- Consumes: Task 5 `TombstoneTurnRows`; Task 6 globals (`tsOwn`, `tsDead`, `isTombstoneGame`, `tombstoneTbClick`, `tombstonePreviewCodes`, `tombstoneTbButtonState`, `tombstoneSubmitMoves`, `tombstoneCurrentPlayer`, `tombstoneStep`, `tombstoneDrawLastMarks`, `TOMBSTONE_OWN_PASS`).
- Produces: page functions `tombstoneRedraw()`, `tombstoneCellClick(i, j)`, `tombstoneButton()`, `tombstoneUpdateButton()`; anchor `#tombstoneBtn` with label span `#tombstoneBtnLabel`.

- [ ] **Step 1: Server-side markup**

- Next to `isGo` (`:119`): `boolean isTombstone = game.getGame() == GridStateFactory.TB_TOMBSTONE;`.
- Buttons (`:311-315`): the static Submit anchor's guard becomes `if (!renjuDecision && !isTombstone)`. Inside the same my-turn block add `<% if (isTombstone) { %><a class="boldbuttons" id="tombstoneBtn" href="javascript:tombstoneButton();" style="margin-right:5px;"><span id="tombstoneBtnLabel">Place only a dead stone</span></a><% } %>`. The Go and Renju Pass/Draw anchors stay gated on `isGo`/`renjuComplete` and never appear for Tombstone.
- Move table (`:462-512`): P1 with `TombstoneTurnRows.rows(game)`.
- "Search the DB" block (`:1614`): add `&& !GridStateFactory.isTombstone(gameId)` to the condition (spec correction 6).

- [ ] **Step 2: Page functions** (in the `<script>` after `init()` is defined, `:713`)

- `tombstoneRedraw()`: `codes = moves.concat(tombstonePreviewCodes(tsOwn, tsDead))`; `resetAbstractBoard`; `drawUntilMove = codes.length`; `replayGame(abstractBoard, codes, drawUntilMove)`; clear, `drawGrid(boardContext, boardColor, gridSize, true)`, `drawGame()`; `tombstoneDrawLastMarks(moves, moves.length)` (marks the opponent's last turn, not the preview); `tombstoneUpdateButton()`.
- `tombstoneCellClick(i, j)`: if `!active` → `selectMove(moves.length - 1)` and return (same reset-to-end as the other games' inactive click). Otherwise `tombstoneRedraw()` first so `abstractBoard` is the post-capture preview, then `s = tombstoneTbClick(tsOwn, tsDead, j * gridSize + i, abstractBoard[i][j])`, store `s.tsOwn`/`s.tsDead`, `tombstoneRedraw()`.
- `tombstoneButton()`: `st = tombstoneTbButtonState(tsOwn, tsDead)`; `ownPass` → `tsOwn = TOMBSTONE_OWN_PASS; tombstoneRedraw();`. Otherwise submit with the existing URL shape (`submit()`, `:1438-1441`): `"/gameServer/tb/game?command=move&gid=" + <%=game.getGid()%> + cycleStr + hideStr + "&moves=" + tombstoneSubmitMoves(tsOwn, tsDead) + "&message=" + encodeURIComponent(document.getElementById('message').value)`, `"_self"`. No `drawOfferParam()` (Renju only).
- `tombstoneUpdateButton()`: if `#tombstoneBtnLabel` exists, set its `textContent` to `tombstoneTbButtonState(tsOwn, tsDead).label`.

- [ ] **Step 3: Route the input handlers**

- `boardClick` (`:1133`): after `i`/`j` are computed (`:1146`), `if (isTombstoneGame(game)) { if (i >= 0 && i < gridSize && j >= 0 && j < gridSize) tombstoneCellClick(i, j); return; }`.
- `touchStart` (`:736`): first statements for Tombstone: `touching = true; stoneColor = (tsOwn === -1) ? (tombstoneCurrentPlayer(moves) === 2) : 'dead'; tombstoneRedraw(); return;`.
- `drawInteractionStone` (`:1277`): in the gradient section (`:1308`), a `color === 'dead'` branch before `if (color)` using the gray gradient `#C8C8C8` → `#6E6E6E`.
- `touchCancel` (`:844`): Tombstone → clear the interaction canvas, `tombstoneRedraw()`, return.
- `touchEnd` (`:856`): after `i`/`j` are computed (`:864`), Tombstone → in bounds `tombstoneCellClick(i, j)`, else `tombstoneRedraw()`; return.
- `selectMove` (`:201-226`): P2. `goBack` (`:1348`) / `goForward` (`:1381`): P3. Page-load tail (`:1596-1606`): P4, followed by `tombstoneUpdateButton()`.

- [ ] **Step 4: Verify the shared JS still passes and nothing else changed**

```bash
node dsg_src/httpdocs/gameServer/js/boardCommon.test.js
node dsg_src/httpdocs/gameServer/js/tombstone.test.js
git diff --stat dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
grep -n "isTombstone\|tombstone" dsg_src/httpdocs/gameServer/tb/mobileGame.jsp | wc -l
```
Expected: both Node tests `OK`; only `mobileGame.jsp` changed in this task; the grep shows the arms listed above. Browser behaviour is checked in Task 10.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/mobileGame.jsp
git commit -m "Play Tombstone turns on the TB board"
```

---

### Task 8: TB reply and list pages

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/tb/cancelReply.jsp` (move table `:256-300`; `selectMove` `:124-149`; `goBack` `:545`, `goForward` `:577`; load tail `:676-686`)
- Modify: `dsg_src/httpdocs/gameServer/tb/undoReply.jsp` (move table `:238-282`; `selectMove` `:112-137`; `goBack` `:527`, `goForward` `:559`; load tail `:581-591`)
- Modify: `dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp` (load tail `:210-218`)

**Interfaces:**
- Consumes: Task 5, Task 6, and the page pattern.

Both reply pages have board input disabled (`cancelReply.jsp:499-505` listeners are commented out; `undoReply.jsp` registers none) and render the full current board, so only P1-P4 apply. `undoReply.jsp` has no "board after undo" preview to change (spec correction 3); its "undo his last move" copy stays.

- [ ] **Step 1: Apply P1-P4** to `cancelReply.jsp` and `undoReply.jsp` (server-side `isTombstone = game.getGame() == GridStateFactory.TB_TOMBSTONE`; fully qualified helper name). In `listedMobileGame.jsp` replace the red-dot lines after `drawGame()` (`:213-218`) with the P2 form using `moves.length` (that page has no move list or stepping).

- [ ] **Step 2: Verify**

```bash
node dsg_src/httpdocs/gameServer/js/tombstone.test.js
grep -c "isTombstoneGame(game)" dsg_src/httpdocs/gameServer/tb/cancelReply.jsp dsg_src/httpdocs/gameServer/tb/undoReply.jsp dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp
```
Expected: Node `OK`; the reply pages show 4 arms each (selectMove, goBack, goForward, load tail), `listedMobileGame.jsp` 1.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/httpdocs/gameServer/tb/cancelReply.jsp dsg_src/httpdocs/gameServer/tb/undoReply.jsp dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp
git commit -m "Show Tombstone turns on the TB reply and list pages"
```

---

### Task 9: Game viewers

**Files:**
- Modify: `dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp` (move table `:257-293`; `selectMove` `:484`; `boardClick` reset block `:520-540`; `goBack` `:558`; `goForward` `:590`; load tail `:623-636`; `drawBoardUntilMove` `:638-656`; `gifer` `:658-681`)
- Modify: `dsg_src/httpdocs/gameServer/viewLiveGameEmbed.jsp` (move table `:172-208`; `selectMove` `:355`; `boardClick` `:382`; `goBack` `:419`; `goForward` `:451`; load tail `:484-495` — the 10 `drawRedDot` calls at `:370-491`)
- Modify: `dsg_src/httpdocs/gameServer/viewGameEmbed.jsp` (token parsing `:50-58`; move table `:136-170`; `selectMove` `:231`; `boardClick` `:258`; `goBack` `:295`; `goForward` `:327`; load tail `:360-371`)

**Interfaces:**
- Consumes: Task 5 (`TombstoneTurnRows.rows(MoveData)`, `rows(List<Integer>)`, `isTombstoneGameName`), Task 6 globals, the page pattern, Plan A `TombstoneCodes.parsePgnToken`.

`viewLiveGame.jsp` (the `TBApplet` page) is out of scope (spec §12, §18). Message numbering needs no change: `moveNums` is `entry.moveNum` for every game except `TB_CONNECT6` (`viewLiveGameMobile.jsp:116`), and Tombstone messages are stored at the post-turn row count (Task 2).

- [ ] **Step 1: `viewLiveGameMobile.jsp` and `viewLiveGameEmbed.jsp`**

Server side: `boolean isTombstone = TombstoneTurnRows.isTombstoneGameName(game.getGame());` next to `gameId` (`:21` / `:31`). Apply P1 (with `TombstoneTurnRows.rows(game)`; `game` is the archived `GameData`), P2, P3, P4; the viewers' `boardClick` is a reset-to-end handler, so it gets P4. In `viewLiveGameMobile.jsp` also: `drawBoardUntilMove` gets P2, and `gifer` for Tombstone adds one frame per turn — `tombstoneTurns(moves).forEach(t => { drawBoardUntilMove(t.end); encoder.addFrame(boardContext); })` — instead of one per entry.

- [ ] **Step 2: `viewGameEmbed.jsp` token parsing** (spec correction 4)

After `int gameId = GridStateFactory.getGameId(gameName);` (`:40`): `boolean isTombstone = GridStateFactory.isTombstone(gameId);`. In the loop at `:54-58`, for Tombstone: `int move = TombstoneCodes.parsePgnToken(moveStr[i].trim()); if (move < 0) { response.sendError(500, "Invalid request"); return; }`, then add it as today; the non-Tombstone path is unchanged. Then P1 with `TombstoneTurnRows.rows(gameMoves)`, P2, P3, P4. The JS `game` there is already `83` (`:206` maps 33/34 to `50 + 33`).

- [ ] **Step 3: Verify**

```bash
node dsg_src/httpdocs/gameServer/js/tombstone.test.js
grep -c "isTombstoneGame(game)" dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp dsg_src/httpdocs/gameServer/viewLiveGameEmbed.jsp dsg_src/httpdocs/gameServer/viewGameEmbed.jsp
grep -n "gameId == 83\|game == 83\|=== 83" dsg_src/httpdocs/gameServer/view*.jsp
```
Expected: Node `OK`; each viewer has its arms (Mobile ≥ 7, the embeds ≥ 5); the last grep prints nothing.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp dsg_src/httpdocs/gameServer/viewLiveGameEmbed.jsp dsg_src/httpdocs/gameServer/viewGameEmbed.jsp
git commit -m "Show Tombstone turns and dead stones in the game viewers"
```

---

### Task 10: Full regression and local browser check

**Files:** none changed (fixes found here go back to the owning task's files, with a test first where one is possible).

- [ ] **Step 1: Full automated suite**

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant compile-tests
for t in org.pente.turnBased.test.TBGameTombstoneTest org.pente.turnBased.test.CacheTBStorerTombstoneTest \
         org.pente.turnBased.web.test.MoveServletTombstoneTest org.pente.turnBased.web.test.NewGameServletTombstoneTest \
         org.pente.turnBased.web.test.TombstoneTurnRowsTest \
         org.pente.turnBased.test.TBGamePassDrawTest org.pente.turnBased.test.TBGameRenjuPhaseTest \
         org.pente.turnBased.test.TBGameSeatsSwappedTest org.pente.turnBased.test.CacheTBStorerRedisTest \
         org.pente.turnBased.test.CacheTBStorerGameEndTest org.pente.turnBased.test.CacheTBStorerKidTest \
         org.pente.turnBased.web.test.NewGameServletColorTest org.pente.turnBased.web.test.RenjuTbContractTest; do
  echo "== $t"; /opt/homebrew/opt/openjdk@21/bin/java --add-opens=java.base/java.lang=ALL-UNNAMED \
      -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner $t | tail -2
done
node dsg_src/httpdocs/gameServer/js/boardCommon.test.js
node dsg_src/httpdocs/gameServer/js/tombstone.test.js
```
Expected: every class `OK`, counts equal to the Task 0 baseline for pre-existing classes; Node prints `OK: 102 mapping assertions …` and `OK: <n> tombstone assertions passed`.

- [ ] **Step 2: Local browser check (local Docker stack only)**

The compile above already wrote `deployClasses/`, which is bind-mounted into `penteorg-pente.org-1`; JSPs under `httpdocs/gameServer` are live through the directory mount. **Ask the user before running `docker restart penteorg-pente.org-1`** (needed to load the new classes). Never `./justCompile`, never `sync_gameServer.sh`. With two local accounts:

1. `tb/new.jsp` lists "Tombstone Pente"; create and accept a rated invitation. P1's centre is placed automatically.
2. P2: stone + dead stone → **Submit**. P1: stone only → **Submit without dead stone**. P2: **Place only a dead stone**, click a point → **Submit** (`moves=722,<361+r>`). P1: **Place only a dead stone** → **Submit without dead stone** (`moves=722`). After each, the move list shows one row per turn with the exact labels, dead stones are gray, the board colour is `#D7BDE2`.
3. A turn with a message: the message shows when that turn is selected.
4. Capture a pair, then drop the dead stone on a vacated point in the same turn: accepted.
5. Request undo after a 2-entry turn; accept: both rows are gone and it is that player's turn again.
6. Win with a stone and also place a dead stone: the game ends, the dead stone is not stored.
7. Back/forward buttons and arrow keys step one turn at a time; markers sit on the last turn's own and dead stones; no console errors (check devtools).
8. Mobile web: devtools touch emulation, repeat step 2 by tapping; the hover ghost is gray in the dead phase.
9. `tb/newKotH.jsp` has no Tombstone option; posting `game=83&koth=1` to the new-game servlet shows `King of the Hill is not available for Tombstone Pente.`; `admin/newTourney.jsp` lists no Tombstone and rejects `game=33` with `Tournaments are not available for Tombstone Pente.`
10. After the game ends (Plan B archive in place): open it with `viewLiveGame?g=<gid>&mobile` and `&e=1`; open `/gameServer/viewGameEmbed.jsp?g=Tombstone%20Pente&m=K10,L10,xL9,pass`; check `tbpgn.jsp` export shows `pass`/`xK10` tokens. A bad token (`m=K10,xZZ`) returns 500.
11. JSP arms that the steps above never render (nothing automated compiles JSPs, so a scriptlet typo shows only as a 500 on that page):
    - `tb/cancelReply.jsp`: in an active Tombstone game with at least one 2-entry turn, P1 requests a set cancel; P2 opens the game (`MoveServlet` forwards to `cancelRedirectPage`, `MoveServlet.java:169-182`). The page loads without a 500 and shows one row per turn. Clicking a row and pressing back/forward land on turn ends. Then decline the cancel.
    - `tb/listedMobileGame.jsp`: open the `profile.jsp` of a player with a public, non-hidden active Tombstone game (`playerrunninggamesbox.jsp:44-51` includes it). The thumbnail renders gray dead stones and the last-turn markers, with no 500 and no console error.
    - `viewLiveGameMobile.jsp` GIF (Graphics Interchange Format) export: on the finished game from item 10, click the "Animated GIF" button (`:705`, which calls `gifer()` at `:658`). The GIF has one frame per turn, not one per entry.
    - Speed Tombstone archive (needs Plan B's live server): finish a live Speed Tombstone game (live id 34, so the server-side viewer id is `getGameId + 50 = 84`, which the JS maps to 83 at `viewLiveGameMobile.jsp:460`). Open it with `viewLiveGame?g=<gid>&mobile` and `&e=1`. It shows per-turn rows and gray dead stones, the same as a TB archive. If Plan B has not landed, report this item as not run.

Report each item as passed or failed with what was seen. Any failure gets a fix in the owning task's files and a re-run of Step 1.

- [ ] **Step 3: Tidy**

```bash
git status --porcelain | grep '^??'
```
Expected: no files created by this plan's work besides those committed (the pre-existing untracked `CLAUDE.md` and spec are not this plan's). Remove any scratch output.

---

## Self-review notes

- Spec coverage: §10.1 → Task 1; §10.2 → Task 2; §10.3-10.4 → Task 3; §7.3 TB KOTH and §7.4 tournaments → Task 4; §11.1 → Task 6; §11.2-11.4 → Task 7; §11.5 → Task 8; §12 → Task 9; §4.1 `containsDoublePass` pin → Task 1; §14 rows tagged (C) → Tasks 3, 6, 7, 9; §17 row C tests → Tasks 1, 2, 6 (plus Tasks 3, 4, 5 helper tests). §10.5 (cancel/resign/timeout) needs no change: the timeout winner comes from Task 1's `getCurrentPlayer`.
- Not in this plan (owned elsewhere): archive writer/loader and the `723` sentinel (B), webdb and legacy-db rejections (B), PGN tokens (A), live server (B), React (D), beta gating (later work; spec §15).
