# Tombstone Pente — Plan B: Live Server, Archive, Boot Registration, Logging

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the live game server play Tombstone Pente turn by turn (own placement, then an optional dead stone, with a dedicated "skip dead stone" event), archive finished Tombstone games without losing dead stones on point 0, register the game at boot without King of the Hill (KOTH) events, create a new live server "Experimental" (id 47, port 16004) at boot that offers only Tombstone, refuse any table game a server does not offer, hide Experimental from the mobile apps, keep Tombstone out of pentedb/webdb, and stop move logging from crashing on codes `>= 361`.

**Architecture:** All rules live in Plan A's `TombstoneState` and `TombstoneCodes`. This plan only wires them into `ServerTable` (phase-aware timers, a skip handler with a post-commit tail, whole-turn undo, a rejoin correction), adds two events to the Gson wrapper, swaps the archive's `361` "no next move" sentinel for `723` on Tombstone ids, derives boot registration from Plan A's `NON_KOTH_*` arrays, and adds early rejections to the webdb handlers. A new pure class `org.pente.gameServer.core.ExperimentalServer` is the single source of truth for the Experimental server (id, name, port, games) and holds the pure predicates that the boot step, the server-offering rule (`ServerTable`, `ArenaServerTable`), the mobile list filters and the native-socket refusal all call. Server-table behaviour is tested through a `ServerTable` subclass with fake timers and a recording router, the same pattern as `ServerTableKidTest`/`ServerTableDrawScoringTest`.

**Tech Stack:** Java 21 (pattern-switch already used in `SynchronizedServerTable`), Ant, JUnit 3 (`junit.framework.TestCase`), Gson wire format, MariaDB (archive rows only; no schema change).

**Spec:** `docs/superpowers/specs/2026-10-27-tombstone-pente-design.md`. Read §2 (including D14), §4, §7.2-7.3, §7.5, §8, §9.1-9.2, §9.4, §14 and §16 before starting any task. This plan is "Plan B" of §16.

## Ordering and dependencies

- **Plan A (game core) must be merged on `feat/tombstone-pente` first.** Every task here except Task 1 compiles against Plan A's `TombstoneCodes`, `TombstoneState`, `GridStateFactory.TOMBSTONE*`, `GridStateFactory.isTombstone`, `GridStateFactory.NON_KOTH_LIVE_GAMES`/`NON_KOTH_TB_GAMES`. Task 0 checks this.
- Task 1 (logging) has no Plan A dependency and may land before A.
- Plan C (TB + website) is independent of this plan once A is in. C does not touch `ServerTable`, the events or the archive writer; this plan does not touch `turnBased/*`, JSP or JS (spec §16.2). C relies on this plan's archive writer (Task 9) for finished TB games, unchanged.
- Plan D (React) depends only on the wire contract this plan implements (spec §8.1, §16.3: `dsgTombstoneSkipDeadTableEvent`, `dsgTombstoneSkipDeadTableErrorEvent`, error code `26`).

## Global Constraints

- Ids: `TOMBSTONE = 33`, `SPEED_TOMBSTONE = 34`, `TB_TOMBSTONE = 83`. Use `GridStateFactory.isTombstone(int)` for every branch; never compare against literals in production code.
- Codes: own stone `0..360`, dead stone `361 + point` (`361..721`), own pass `722`. Dead-stone passes are never stored. Archive-only sentinel `723` (`TombstoneCodes.ARCHIVE_NO_NEXT_MOVE`). **No decoder may interpret a code `>= 361` without first knowing that the game is Tombstone** (spec §4.1).
- Live: each placement is its own `DSGMoveTableEvent` with a single code. A 2-code `moves` frame is never sent live (spec §8.1).
- New error code: `DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE = 26` (25 `NO_DRAW_OFFERED` is the last used).
- Invariant: when `timed`, `moveTimes` has exactly one entry per stored entry (spec §8.3).
- Live Fischer increment once per turn, never per placement (D7). Undo removes the whole last turn (D5).
- No `KOTH_EVENT` rows and no server-45 offering for Tombstone (D9). `LIVE_GAMES`, `TB_GAMES`, `ALL_GAMES` stay unchanged.
- Live servers (spec §2, §7.2, D14): Tombstone is offered **only** by the new Experimental server, `ExperimentalServer.ID = 47`, `NAME = "Experimental"`, `PORT = 16004`, `GAMES = {TOMBSTONE, SPEED_TOMBSTONE}`, and Experimental offers nothing else. Servers 1, 37, 45, 46 and the Arena (copies 1) keep today's offerings exactly. Never hard-code `47`, `16004` or `"Experimental"` outside `ExperimentalServer`.
- Server-offering rule (spec §7.3): a table never plays a game its server does not offer: `ExperimentalServer.gameAllowed(serverData, id)` at `handleChangeState`, `ServerTable.resetTable` and `ArenaServerTable.resetTable`.
- Mobile apps (spec §7.5): the four mobile server/room lists skip Experimental; `Server.addPlayerSocket` refuses native sockets on Experimental. `/gameServer/activeServers` and the website lists are **not** filtered (the React room uses them).
- No caller may take the result (winner, game over, draw) of a **finished** game from a replayed `TombstoneState`; the stored winner is authoritative (spec §5.4).
- **Do not deploy this branch to production** (`sync_gameServer.sh`) and do not merge it into a deployable `main` until beta gating exists (spec §15). Local checks use the local smoke-test route only.
- Tests are JUnit 3 (`public void testXxx()`, `extends TestCase`, a `(String name)` constructor). Build and run from the `pente.org/` repo root, **never `./justCompile`** (it restarts docker):

  ```bash
  cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
  rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/
  JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant compile-tests
  /opt/homebrew/opt/openjdk@21/bin/java --add-opens=java.base/java.lang=ALL-UNNAMED \
      -cp "deployClasses:dsg_src/lib/*" junit.textui.TestRunner <fully.qualified.TestClass>
  ```

  The bundled JUnit is `dsg_src/lib/junit-3.7.jar`, whose `Assert` has `assertEquals`, `assertNotNull`, `assertNull`, `assertSame` and `assertTrue` but **no `assertFalse`** (see the note at `turnBased/web/test/RenjuTbContractTest.java:149`). Every new test class in this plan that uses `assertFalse` declares `private static void assertFalse(boolean b) { assertTrue(!b); }` and `private static void assertFalse(String m, boolean b) { assertTrue(m, !b); }`.

  Below, "**Run** `<Class>`" means these three commands with that class. `compile-tests` compiles test sources straight from `dsg_src/java` (the rsync excludes `test` dirs); production sources compile from `deploy/`, so the rsync is required after every production edit.
- Commits: on branch `feat/tombstone-pente`, plain one-line messages, no co-author or "generated by" lines. Never commit to `main`.
- Scratch output (logs, temp files) goes to `/private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad`, never into the repo.
- Match the surrounding style (log4j `Category`, no reformatting of untouched code).

## Review Focus

Spec-implied cases that no single feature test naturally reaches, most likely to bite first. Each one has a pinned test in the task named.

1. **0-minute games (`initialMinutes == 0`)**: the own entry must neither reset nor re-`go()` the mover's clock mid-turn, and the skip must `reset()` it once. Today's same-player branch resets on every stone. Test: Task 3 `testZeroMinuteOwnEntryDoesNotResetClock`, Task 4 `testZeroMinuteSkipResetsClockOnce`.
2. **Untimed tables**: a skip with `timed == false` must not touch timers or `moveTimes` (timers exist on every table). Test: Task 4 `testUntimedSkipTouchesNoClock`.
3. **A pending undo request when the opponent skips**: it must be implicitly declined exactly as a move declines it. Test: Task 4 `testSkipDeclinesPendingUndoAndCancel` (a request cannot be made mid-turn, so the test sets the flags directly; the decline is defensive parity with `handleMove`).
4. **Game ends mid-turn (resign/timeout while a dead stone is pending)**: the archive gets a trailing own entry; row count, `moveTimes` alignment and the stored winner must come from `GameData`, not from the replayed state. Test: Task 9 `testTrailingOwnEntryKeepsRowAlignment`.
5. **Spectator or player joining while the dead stone is still pending**: no silent skip may be sent (it would wrongly close the turn on that client). Test: Task 6 `testNoSilentSkipWhileAwaitingDead`.

## Deviation from the spec (with evidence)

- **Game-change guard placement (spec §7.3, now reconciled there).** An earlier spec draft put the guard "after the speed/normal conversion (`:927-935`) and before `:938`". By then `handleChangeState` has already applied `initialMinutes`, `incrementalSeconds`, `timed`, `rated` and reset every timer (`ServerTable.java:896-919`), so "change nothing" would not hold. Task 7 places the guard as an `else if` in the validation chain (before the final `else {` at `:886`), resolving the target game with the same speed conversion itself. The observable contract is the spec's: `status = UNKNOWN`, the existing error tail (`:962-972`) sends `DSGChangeStateTableErrorEvent` with the unchanged settings, and nothing on the table changes.
- **The `resetTable` sites (now in spec §7.3).** `ServerTable.resetTable` (`ServerTable.java:255`, called from the constructor at `:211` when a player creates a table) restores the creator's saved `"gameState"` preference and sets `game = GridStateFactory.getGame(e.getGame())` (`:281-288`) with no `getGameEvents()` check; it falls back to Pente 10/5 only when no preference was restored (`:298-302`). The preference is stored per player, not per server (`handleChangeState` saves it at `:975-977`, after `changeStateEvent.setGame(game.getId())` at `:955`, so the stored id is already speed/normal-converted). A player who last set up Tombstone on Experimental and then creates a table on server 1 would get a Tombstone table without any change event, and every new table on Experimental would default to Pente; both hit the NullPointerException at `:3407` (and on server 45 the junk hill at `:3841-3842`). `ArenaServerTable.resetTable` (`ArenaServerTable.java:343-364`) takes the game straight from the client's create event (`:354`). Task 7 applies `ExperimentalServer.gameAllowed` at all three sites.

## File map

| File | Change | Task |
|---|---|---|
| `dsg_src/java/org/pente/gameServer/event/DSGMoveTableEvent.java` | `formatMoveForLog`, `toString` uses it | 1 |
| `dsg_src/java/org/pente/gameServer/server/ActivityTableData.java` | `getMoves` uses `formatMoveForLog` | 1 |
| `dsg_src/java/org/pente/gameServer/event/DSGTombstoneSkipDeadTableEvent.java` | new | 2 |
| `dsg_src/java/org/pente/gameServer/event/DSGTombstoneSkipDeadTableErrorEvent.java` | new | 2 |
| `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java` | 2 fields + getters/setters | 2 |
| `dsg_src/java/org/pente/gameServer/event/DSGTableErrorEvent.java` | `NOT_AWAITING_DEAD_STONE = 26` | 2 |
| `dsg_src/java/org/pente/gameServer/server/ServerTable.java` | handleMove arms, skip handler, undo, rejoin, server-offering rule (`handleChangeState`, `resetTable`) | 3-7 |
| `dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java` | server-offering rule in `resetTable` | 7 |
| `dsg_src/java/org/pente/gameServer/core/ExperimentalServer.java` | new: id/name/port/games, `isExperimental`, `isExperimentalRoom`, `acceptsNativeSocket`, `gameAllowed`, `defaultGame` | 7 |
| `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java` | dispatch case | 4 |
| `dsg_src/java/org/pente/game/MySQLGameVenueStorer.java` | `allGameEventRows()` | 8 |
| `dsg_src/java/org/pente/gameServer/core/MySQLServerStorer.java` | `ensureServer(...)` | 8 |
| `dsg_src/java/org/pente/gameServer/server/DSGContextListener.java` | `serverOfferings()`, Experimental boot step | 8 |
| `dsg_src/java/org/pente/gameServer/mobile/{LiveServersResponse,WhosonlineAndLiveResponse}.java` | skip Experimental | 11 |
| `dsg_src/httpdocs/gameServer/mobile/{liveServers,whosonlineandlive}.jsp` | skip Experimental (iOS text lists) | 11 |
| `dsg_src/java/org/pente/gameServer/server/Server.java` | `addPlayerSocket` refuses native sockets on Experimental | 11 |
| `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java` | `archiveNextMove`, `loadMovesSql`, `loadGames` comment | 9 |
| `dsg_src/java/org/pente/webdb/{PositionStatsHandler,GameSearchHandler,GameLoadHandler,CollectionHandler}.java` | Tombstone rejections | 10 |
| `dsg_src/java/org/pente/gameDatabase/SimpleGameStorerSearchRequestFilterFormat.java` | reject Tombstone | 10 |
| `build.xml` | add new test classes to the `test` target | 11 |
| Tests (new): `gameServer/server/test/DSGMoveTableEventLogTest`, `gameServer/server/test/TombstoneSkipDeadWireTest`, `gameServer/server/test/ServerTableTombstoneTest`, `gameServer/core/test/ExperimentalServerTest`, `game/test/TombstoneBootRegistrationTest`, `game/test/MySQLPenteGameStorerTombstoneTest`, `webdb/test/TombstoneWebDbExclusionTest`, `gameServer/mobile/test/MobileServerListFilterTest` | | 1-11 |

All paths below are relative to `pente.org/`.

---

### Task 0: Confirm Plan A has landed

**Files:** none.

- [ ] **Step 1: Check the Plan A interfaces exist**

Run:
```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
git branch --show-current
grep -c "isTombstone\|NON_KOTH_LIVE_GAMES\|NON_KOTH_TB_GAMES\|TB_TOMBSTONE" dsg_src/java/org/pente/game/GridStateFactory.java
ls dsg_src/java/org/pente/game/TombstoneState.java dsg_src/java/org/pente/game/TombstoneCodes.java
```
Expected: branch `feat/tombstone-pente`, a count `>= 4`, both files listed. If any is missing, stop: only Task 1 may proceed before Plan A.

---

### Task 1: Safe move formatting in logs

Spec §8.6. `DSGMoveTableEvent.toString()` is logged at `SynchronizedServerTable.java:117` (`"in: "`, outside the `try`) and `LoggingDSGEventToPlayerRouter.java:60` (`"out: "`). It formats codes with `AlphaNumericGridCoordinates(19, 19).getCoordinate(int)` (`DSGMoveTableEvent.java:8`, `:63-75`), which indexes `numbers[y]` with `y < 0` for codes `362..721` and `722`, throwing before the event is handled. `ActivityTableData.getMoves()` (`ActivityTableData.java:41-51`) has the same flaw and no callers.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGMoveTableEvent.java:63-75`
- Modify: `dsg_src/java/org/pente/gameServer/server/ActivityTableData.java:41-51`
- Test: `dsg_src/java/org/pente/gameServer/server/test/DSGMoveTableEventLogTest.java` (new)

**Interfaces:**
- Consumes: nothing from Plan A.
- Produces: `public static String DSGMoveTableEvent.formatMoveForLog(int m)`: `0..360` → the coordinate from the existing static `coordinates`; any other value → `"#" + m`. Go's `361` therefore logs as `#361` (accepted, spec §8.6).

- [ ] **Step 1: Write the failing test**

```java
package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.DSGMoveTableEvent;

public class DSGMoveTableEventLogTest extends TestCase {
    public DSGMoveTableEventLogTest(String name) { super(name); }

    public void testFormatMoveForLog() {
        assertEquals("A19", DSGMoveTableEvent.formatMoveForLog(0));
        assertEquals("K10", DSGMoveTableEvent.formatMoveForLog(180));
        assertEquals("#361", DSGMoveTableEvent.formatMoveForLog(361));
        assertEquals("#380", DSGMoveTableEvent.formatMoveForLog(380));
        assertEquals("#721", DSGMoveTableEvent.formatMoveForLog(721));
        assertEquals("#722", DSGMoveTableEvent.formatMoveForLog(722));
        assertEquals("#-1", DSGMoveTableEvent.formatMoveForLog(-1));
    }

    public void testToStringNeverThrowsForTombstoneCodes() {
        for (int m : new int[]{0, 180, 361, 362, 380, 721, 722}) {
            String s = new DSGMoveTableEvent("alice", 3, m).toString();
            assertTrue(s, s.startsWith("move " + DSGMoveTableEvent.formatMoveForLog(m) + " "));
        }
        String bulk = new DSGMoveTableEvent(3, new int[]{180, 200, 361 + 5, 722}).toString();
        assertTrue(bulk, bulk.startsWith("moves K10,"));
        assertTrue(bulk, bulk.contains("#366,#722 "));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

**Run** `org.pente.gameServer.server.test.DSGMoveTableEventLogTest`.
Expected: compile failure, `cannot find symbol ... formatMoveForLog`.

- [ ] **Step 3: Implement**

Add `formatMoveForLog(int m)` to `DSGMoveTableEvent` (range check `m >= 0 && m <= 360`, else `"#" + m`) and use it for both branches of `toString()`. In `ActivityTableData.getMoves()`, replace `gridCoordinates.getCoordinate(moves[i])` with `DSGMoveTableEvent.formatMoveForLog(moves[i])` and drop the now-unused `gridCoordinates` field and its import if nothing else uses them.

- [ ] **Step 4: Run it to verify it passes**

**Run** `org.pente.gameServer.server.test.DSGMoveTableEventLogTest`.
Expected: `OK (2 tests)`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/event/DSGMoveTableEvent.java dsg_src/java/org/pente/gameServer/server/ActivityTableData.java dsg_src/java/org/pente/gameServer/server/test/DSGMoveTableEventLogTest.java
git commit -m "Format live move codes safely in logs"
```

---

### Task 2: Skip-dead-stone events and error code

Spec §8.1 and §16.3. Wire names are the wrapper field names: `dsgTombstoneSkipDeadTableEvent`, `dsgTombstoneSkipDeadTableErrorEvent`. `DSGEventWrapper(Object)` fills the field whose type matches the event's class, and `getEncodedEvent()` returns the first non-null field (`DSGEventWrapper.java:95-122`), so a field plus getter/setter is all the wire needs. There is no inbound allow-list.

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/event/DSGTombstoneSkipDeadTableEvent.java` (model: `DSGSwap2PassTableEvent.java`)
- Create: `dsg_src/java/org/pente/gameServer/event/DSGTombstoneSkipDeadTableErrorEvent.java` (model: `DSGRenjuDrawTableErrorEvent.java`)
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java` (fields after `dsgRenjuDrawTableErrorEvent` at `:93`; getters/setters after `:766`)
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGTableErrorEvent.java:29` (add `NOT_AWAITING_DEAD_STONE = 26` after `NO_DRAW_OFFERED = 25`)
- Test: `dsg_src/java/org/pente/gameServer/server/test/TombstoneSkipDeadWireTest.java` (new; Gson helper copied from `ArenaJoinRequestWireTest.serverGson()` at `:172-178`). Parse JSON with `serverGson().fromJson(json, JsonObject.class)`, not `JsonParser.parseString`: `dsg_src/lib` holds both `gson-2.8.0.jar` and `gson-2.10.1.jar`, and `parseString` does not exist in 2.8.0.

**Interfaces:**
- Produces:
  - `DSGTombstoneSkipDeadTableEvent extends AbstractDSGTableEvent`: fresh `serialVersionUID`; field `boolean silent = false`; constructors `()` and `(String player, int table, boolean silent)`; `boolean isSilent()`; `toString()` = `"tombstone skip dead stone " + super.toString()`.
  - `DSGTombstoneSkipDeadTableErrorEvent extends AbstractDSGTableErrorEvent`: constructors `()` and `(String player, int table, int error)`; `toString()` = `"tombstone skip dead stone error " + super.toString()`.
  - `DSGEventWrapper.getDsgTombstoneSkipDeadTableEvent()` / `setDsgTombstoneSkipDeadTableEvent(...)`, `getDsgTombstoneSkipDeadTableErrorEvent()` / `setDsgTombstoneSkipDeadTableErrorEvent(...)`.
  - `DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE = 26`.

- [ ] **Step 1: Write the failing test**

```java
public void testInboundSkipDecodes() {
    DSGEventWrapper w = serverGson().fromJson(
        "{\"dsgTombstoneSkipDeadTableEvent\":{\"player\":\"alice\",\"table\":7,\"time\":0}}",
        DSGEventWrapper.class);
    Object e = w.getEncodedEvent();
    assertTrue(e instanceof DSGTombstoneSkipDeadTableEvent);
    assertEquals("alice", ((DSGTombstoneSkipDeadTableEvent) e).getPlayer());
    assertEquals(7, ((DSGTombstoneSkipDeadTableEvent) e).getTable());
    assertFalse(((DSGTombstoneSkipDeadTableEvent) e).isSilent());
}

public void testOutboundSkipCarriesSilent() {
    JsonObject o = serverGson().fromJson(new DSGEventWrapper(
        new DSGTombstoneSkipDeadTableEvent("alice", 7, true)).getJSON(), JsonObject.class);
    JsonObject ev = o.getAsJsonObject("dsgTombstoneSkipDeadTableEvent");
    assertNotNull(ev);
    assertTrue(ev.get("silent").getAsBoolean());
    assertEquals("alice", ev.get("player").getAsString());
}

public void testOutboundErrorCarriesCode() {
    JsonObject o = serverGson().fromJson(new DSGEventWrapper(
        new DSGTombstoneSkipDeadTableErrorEvent("alice", 7,
            DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE)).getJSON(), JsonObject.class);
    assertEquals(26, o.getAsJsonObject("dsgTombstoneSkipDeadTableErrorEvent").get("error").getAsInt());
}
```

- [ ] **Step 2: Run it to verify it fails**

**Run** `org.pente.gameServer.server.test.TombstoneSkipDeadWireTest`.
Expected: compile failure on `DSGTombstoneSkipDeadTableEvent`.

- [ ] **Step 3: Implement the two event classes, the wrapper fields and the error constant**

- [ ] **Step 4: Run it to verify it passes**

**Run** `org.pente.gameServer.server.test.TombstoneSkipDeadWireTest`, then `org.pente.gameServer.server.test.ArenaJoinRequestWireTest` (wrapper regression).
Expected: `OK (3 tests)`, then the existing count `OK`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/event/ dsg_src/java/org/pente/gameServer/server/test/TombstoneSkipDeadWireTest.java
git commit -m "Add Tombstone skip-dead-stone events"
```

---

### Task 3: Test harness and phase-aware clocks in `handleMove`

Spec §8.2-8.3. `handleMove` keeps its generic checks (`ServerTable.java:2016-2036`); Plan A's `TombstoneState.isValidMove`/`getCurrentPlayer` make them phase-aware, so the dead phase rejects own codes (`INVALID_MOVE`) and the opponent (`NOT_TURN`) with no new code. Two arms change:

- The same-player branch (`:2106`, "own entry, turn stays open") increments and, for 0-minute games, resets the clock on every stone. For Tombstone it must only append the `moveTimes` snapshot.
- The post-move restart guard (`:2172`) re-`go()`s on `initialMinutes == 0` even when the player did not change. For Tombstone it uses `oldCurrentPlayer != newCurrentPlayer` only.

The dead entry (player changes) uses the existing changed-player branch (`:2063-2105`) unchanged: stop, one increment (or reset), snapshot. A winning own stone uses the existing game-over tail (`:2154-2166`).

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:2106-2124` and `:2172`
- Test: `dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java` (new; extended by Tasks 4-7)

**Interfaces:**
- Consumes (Plan A): `GridStateFactory.TOMBSTONE`, `TOMBSTONE_GAME`, `createGridState(int game, int x, int y)` returning a `TombstoneState`; `TombstoneState.getCurrentPlayer()`, `isAwaitingDeadStone()`, `isValidMove(int, int)`; `TombstoneCodes.OWN_PASS`, `deadCode(int)`.
- Produces (test harness, reused by Tasks 4-7, all nested in `ServerTableTombstoneTest`):
  - `static final class FakeTimer implements org.pente.gameServer.client.GameTimer`: no thread; time is a `long millis` changed only by `increment(s)` (`+ s*1000`), `incrementMillis`, `adjust(m, s)` (sets), `reset()` (sets to `startMinutes:startSeconds`); counters `goCalls`, `stopCalls`, `resetCalls`, `incrementCalls`; a `boolean running` that `go()` sets to `true` and `stop()` sets to `false` (initially `false`), returned by `isRunning()`; `getMinutes()`/`getSeconds()` derived from `millis`; listener methods are no-ops.
  - `static final class TombTable extends ServerTable` with constructor `TombTable(boolean timed, int initialMinutes, int... offeredGameIds)`: `tableNum = 3`; `dsgEventRouter` = a `ServerTableKidTest.Router` (same package, records `(name, event)`); `playersInMainRoom = new Vector<>()`; `serverData = new ServerData()` with one `SimpleGameEventData` (`setGame(id)`, `setName(MySQLGameVenueStorer.LIVE_EVENT)`) per offered id; `pingManager` = an anonymous `PingManager` whose `getPingTime` returns `0`; `activityLogger = new ActivityLogger(new Resources()) { public void updateGameState(long s, int t, long h, int[] m) {} }`; `timers = {null, new FakeTimer(), new FakeTimer()}` with `setStartMinutes(initialMinutes)`, `setStartSeconds(initialMinutes == 0 ? INC : 0)`, `reset()`; `incrementalSeconds = INC = 10`; seats `alice` (1) and `bob` (2) as `SimpleDSGPlayerData` humans in `playersInTable`, `playersInMainRoom`, `sittingPlayers`, `playingPlayers`; `game = TOMBSTONE_GAME`; `gridState = createGridState(TOMBSTONE, 19, 19)`; `state = GAME_IN_PROGRESS`.
  - `TombTable` overrides `gameOver(boolean draw, String w, String l, boolean resign, boolean timeup, boolean force)` to append `draw + "|" + w + "|" + l` to `List<String> gameOvers` and set `state = NO_GAME_IN_PROGRESS`, and `sendTimers(String)` to a no-op.
  - Accessors on `TombTable` (protected fields are not reachable from the outer test class in another package): `FakeTimer timer(int seat)`, `List<Time> times()`, `GridState grid()`, `Game currentGame()`, `int initialMinutes()`, `void state(int s)`, `void undoFlag(boolean b)`, `boolean undoFlag()`, `void cancelFlag(boolean b)`, `void useGame(Game g)` (sets `game` and a fresh `gridState` for it).
  - Helper `static void turn(TombTable t, String p, int own, int deadPointOrMinus1)`: `t.handleMove(p, own)` then, if `deadPointOrMinus1 >= 0`, `t.handleMove(p, TombstoneCodes.deadCode(deadPointOrMinus1))`.
  - Helper `<T extends DSGEvent> List<T> to(TombTable t, String name, Class<T> type)` delegating to `Router.to`.

- [ ] **Step 1: Write the harness and the failing tests**

```java
public void testOwnThenDeadIncrementsOnce() {
    TombTable t = new TombTable(true, 5, 33, 34);
    t.handleMove("alice", 180);
    t.handleMove("bob", 200);
    assertEquals(2, t.times().size());
    assertEquals(2, t.grid().getCurrentPlayer());
    assertEquals(0, t.timer(2).incrementCalls);
    assertEquals(1, t.timer(2).goCalls);            // started after the centre only
    t.handleMove("bob", TombstoneCodes.deadCode(201));
    assertEquals(3, t.times().size());
    assertEquals(1, t.timer(2).incrementCalls);
    assertEquals(5, t.times().get(2).getMinutes());
    assertEquals(10, t.times().get(2).getSeconds());
    assertEquals(1, t.timer(1).goCalls);
}

public void testEachPlacementIsBroadcastAsOneCode() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    turn(t, "bob", 200, 201);
    List<DSGMoveTableEvent> seen = to(t, "alice", DSGMoveTableEvent.class);
    assertEquals(3, seen.size());
    assertEquals(180, seen.get(0).getMove());
    assertEquals(200, seen.get(1).getMove());
    assertEquals(361 + 201, seen.get(2).getMove());
    for (DSGMoveTableEvent e : seen) assertEquals(1, e.getMoves().length);
}

public void testOwnCodeInDeadPhaseIsInvalidMove() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleMove("bob", 201);
    List<DSGMoveTableErrorEvent> errs = to(t, "bob", DSGMoveTableErrorEvent.class);
    assertEquals(1, errs.size());
    assertEquals(DSGTableErrorEvent.INVALID_MOVE, errs.get(0).getError());
    assertEquals(2, t.grid().getNumMoves());
}

public void testOpponentInDeadPhaseIsNotTurn() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleMove("alice", TombstoneCodes.deadCode(5));
    assertEquals(DSGTableErrorEvent.NOT_TURN,
        to(t, "alice", DSGMoveTableErrorEvent.class).get(0).getError());
}

public void testOwnPassThenDeadOnPointZero() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    turn(t, "bob", TombstoneCodes.OWN_PASS, 0);          // dead code 361
    assertEquals(3, t.grid().getNumMoves());
    assertEquals(361, t.grid().getMove(2));
    assertEquals(3, t.times().size());
    assertEquals(1, t.grid().getCurrentPlayer());
}

public void testZeroMinuteOwnEntryDoesNotResetClock() {
    TombTable t = new TombTable(true, 0, 33, 34);
    turn(t, "alice", 180, -1);
    int resets = t.timer(2).resetCalls, goes = t.timer(2).goCalls;
    t.handleMove("bob", 200);
    assertEquals(resets, t.timer(2).resetCalls);
    assertEquals(goes, t.timer(2).goCalls);
    t.handleMove("bob", TombstoneCodes.deadCode(201));
    assertEquals(resets + 1, t.timer(2).resetCalls);
}

public void testWinningOwnStoneEndsGameAtOnce() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);                            // K10 row: 180..184
    turn(t, "bob", 0, 340);
    turn(t, "alice", 181, 341);
    turn(t, "bob", 1, 342);
    turn(t, "alice", 182, 343);
    turn(t, "bob", 2, 344);
    turn(t, "alice", 183, 345);
    turn(t, "bob", 3, 346);
    t.handleMove("alice", 184);
    assertEquals(Arrays.asList("false|alice|bob"), t.gameOvers);
    assertEquals(t.grid().getNumMoves(), t.times().size());
}
```

- [ ] **Step 2: Run to verify the clock tests fail**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `testOwnThenDeadIncrementsOnce` fails (`incrementCalls` is 1 after the own entry) and `testZeroMinuteOwnEntryDoesNotResetClock` fails (`resetCalls` grew). The validity tests may already pass (they exercise Plan A through unchanged checks). If `new ActivityLogger(new Resources())` fails in class init, stub only in the test; do not change production code for the harness.

- [ ] **Step 3: Implement the two arms in `handleMove`**

In the same-player branch at `:2106`: `if (gridState instanceof TombstoneState)` append `new Time(timers[oldCurrentPlayer].getMinutes(), timers[oldCurrentPlayer].getSeconds())` to `moveTimes` and nothing else; otherwise the existing body. At `:2172`, restart the new player's timer when `gridState instanceof TombstoneState ? oldCurrentPlayer != newCurrentPlayer : (oldCurrentPlayer != newCurrentPlayer || initialMinutes == 0)`.

- [ ] **Step 4: Run to verify all pass**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`, then `org.pente.gameServer.server.test.ServerTableDrawScoringTest` and `org.pente.gameServer.server.test.ServerTableKidTest`.
Expected: `OK (7 tests)`, then both existing classes `OK`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java
git commit -m "Keep the Tombstone clock running through the dead-stone phase"
```

---

### Task 4: `handleTombstoneSkipDead` and its dispatch

Spec §8.2 (checks, in order) and §8.3 (post-commit tail). A skip stores nothing, so it adds no `moveTimes` entry; it **replaces** `moveTimes.getLast()` (the pre-increment snapshot taken at the turn's own entry) with the post-increment clock, so `pente_move.seconds_left` and a later undo see the same value a dead entry would have produced. The pending-undo decline mirrors `handleMove` (`:2128-2136`), and the pending-cancel decline is included for the same reason, as `broadcastRenjuFifthMove` (`:1629`, the precedent for a post-commit tail outside `handleMove`) does.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java` (new public handler next to `handleSwap2Pass` at `:1284`)
- Modify: `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java` (one `case` after `DSGSwap2PassTableEvent` at `:179-180`)
- Test: `ServerTableTombstoneTest` (extend)

**Interfaces:**
- Consumes: Task 2 events and `NOT_AWAITING_DEAD_STONE`; Task 3 harness; Plan A `TombstoneState.skipDeadStone()`, `isAwaitingDeadStone()`, `isGameOver()`, `getCurrentPlayer()`.
- Produces: `public void ServerTable.handleTombstoneSkipDead(DSGTombstoneSkipDeadTableEvent e)`. Broadcasts `new DSGTombstoneSkipDeadTableEvent(player, tableNum, false)` to the table on success; routes `new DSGTombstoneSkipDeadTableErrorEvent(player, tableNum, error)` to the sender only on failure.

Checks, first failure wins: not in table → `NOT_IN_TABLE`; not seated → `NOT_SITTING`; `state == GAME_WAITING_FOR_PLAYER_TO_RETURN` → `GAME_WAITING_FOR_PLAYER_TO_RETURN`; `state == NO_GAME_IN_PROGRESS` or `WAIT_GAME_TWO_OF_SET` → `NO_GAME_IN_PROGRESS`; `!(gridState instanceof TombstoneState)` → `INVALID_MOVE`; `getCurrentPlayer() != seat` → `NOT_TURN`; `!isAwaitingDeadStone()` → `NOT_AWAITING_DEAD_STONE`.

The tail, inside `synchronized (this)`, in this order (the order is the contract; it matches the changed-player branch at `:2063-2105` and the tail at `:2126-2183`):

```java
ts.skipDeadStone();
if (timed) {
    timers[seat].stop();
    if (shouldTimerRun()) {
        if (initialMinutes == 0) timers[seat].reset();
        else { timers[seat].increment(incrementalSeconds);
               timers[seat].incrementMillis((int) pingManager.getPingTime(player)); }
    }
    moveTimes.set(moveTimes.size() - 1,
        new Time(timers[seat].getMinutes(), timers[seat].getSeconds()));
}
// decline pending undo / cancel exactly as handleMove :2128-2136
broadcastTable(new DSGTombstoneSkipDeadTableEvent(player, tableNum, false));
if (timed) broadCastPlayerTimer(seat);
activityLogger.updateGameState(sid, tableNum, gridState.getHash(), gridState.getMoves());
if (ts.isGameOver()) gameOver(true, playingPlayers[1].getName(), playingPlayers[2].getName(), false, false, false);
else if (timed) timers[ts.getCurrentPlayer()].go();
```

- [ ] **Step 1: Write the failing tests**

```java
public void testOwnThenSkipClosesTurnWithOneIncrement() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    assertEquals(1, t.grid().getCurrentPlayer());
    assertEquals(2, t.times().size());                    // nothing stored, nothing added
    assertEquals(5, t.times().get(1).getMinutes());
    assertEquals(10, t.times().get(1).getSeconds());      // replaced with post-increment
    assertEquals(1, t.timer(2).incrementCalls);
    assertTrue(t.timer(1).isRunning());
    List<DSGTombstoneSkipDeadTableEvent> a = to(t, "alice", DSGTombstoneSkipDeadTableEvent.class);
    assertEquals(1, a.size());
    assertFalse(a.get(0).isSilent());
    assertEquals("bob", a.get(0).getPlayer());
    assertEquals(1, to(t, "bob", DSGTombstoneSkipDeadTableEvent.class).size());
}

public void testSixEmptyTurnsDrawViaSkip() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    String[] movers = {"bob", "alice", "bob", "alice", "bob", "alice"};
    for (int i = 0; i < 6; i++) {
        t.handleMove(movers[i], TombstoneCodes.OWN_PASS);
        assertTrue("draws only on a skip", t.gameOvers.isEmpty());
        t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent(movers[i], 3, false));
    }
    assertEquals(Arrays.asList("true|alice|bob"), t.gameOvers);
}

public void testSkipErrorCodes() {
    assertSkipError(table -> {}, "carol", DSGTableErrorEvent.NOT_IN_TABLE);
    assertSkipError(table -> table.addSpectator("sam"), "sam", DSGTableErrorEvent.NOT_SITTING);
    assertSkipError(table -> table.state(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN),
        "bob", DSGTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN);
    assertSkipError(table -> table.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS),
        "bob", DSGTableErrorEvent.NO_GAME_IN_PROGRESS);
    assertSkipError(table -> table.state(DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET),
        "bob", DSGTableErrorEvent.NO_GAME_IN_PROGRESS);
    assertSkipError(table -> table.useGame(GridStateFactory.PENTE_GAME), "bob",
        DSGTableErrorEvent.INVALID_MOVE);
    assertSkipError(table -> table.handleMove("bob", 200), "alice", DSGTableErrorEvent.NOT_TURN);
    assertSkipError(table -> {}, "bob", DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE);
}
// assertSkipError(setup, sender, code): new TombTable(true, 5, 33, 34);
// handleMove("alice", 180) (the centre); setup.accept(table); if the setup installed a fresh
// grid (grid().getNumMoves() == 0, only the useGame(PENTE_GAME) case), handleMove("alice", 180)
// again so the Pente board is also on bob's turn; snapshot getNumMoves() and times().size(); call the handler as
// sender; assert exactly one DSGTombstoneSkipDeadTableErrorEvent routed to sender with
// getError() == code, none to anyone else, and both snapshots unchanged.
// TombTable gains void addSpectator(String name) (playersInTable only).

public void testSkipDeclinesPendingUndoAndCancel() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.undoFlag(true);
    t.cancelFlag(true);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    assertFalse(t.undoFlag());
    List<DSGUndoReplyTableEvent> r = to(t, "alice", DSGUndoReplyTableEvent.class);
    assertEquals(1, r.size());
    assertFalse(r.get(0).getAccepted());
    assertEquals(1, to(t, "alice", DSGCancelReplyTableEvent.class).size());
}

public void testUntimedSkipTouchesNoClock() {
    TombTable t = new TombTable(false, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    assertEquals(0, t.times().size());
    for (int s = 1; s <= 2; s++) {
        assertEquals(0, t.timer(s).goCalls + t.timer(s).stopCalls + t.timer(s).incrementCalls);
    }
    assertEquals(1, t.grid().getCurrentPlayer());
}

public void testZeroMinuteSkipResetsClockOnce() {
    TombTable t = new TombTable(true, 0, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    int resets = t.timer(2).resetCalls;
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    assertEquals(resets + 1, t.timer(2).resetCalls);
    assertEquals(0, t.timer(2).incrementCalls);
    assertEquals(0, t.times().get(1).getMinutes());
    assertEquals(10, t.times().get(1).getSeconds());
}
```

`assertSkipError` takes a `java.util.function.Consumer<TombTable>`.

- [ ] **Step 2: Run to verify they fail**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: compile failure, `cannot find symbol ... handleTombstoneSkipDead`.

- [ ] **Step 3: Implement the handler and the dispatch case**

In `SynchronizedServerTable.callServerTable`, add `case DSGTombstoneSkipDeadTableEvent dsgTombstoneSkipDeadTableEvent -> serverTable.handleTombstoneSkipDead(dsgTombstoneSkipDeadTableEvent);` directly after the `DSGSwap2PassTableEvent` case.

- [ ] **Step 4: Run to verify they pass**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `OK (13 tests)`.
Then: `grep -n "DSGTombstoneSkipDeadTableEvent" dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java` prints the new `case` line.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java
git commit -m "Handle the live Tombstone skip-dead-stone event"
```

---

### Task 5: Whole-turn live undo

Spec §5.6, §8.4, D5. `handleUndoRequest`/`handleUndoReply` keep their flow; Plan A's `TombstoneState.canPlayerUndo` already forbids requests mid-turn and on the centre turn. On acceptance, `gridState.undoMove()` (`:2267`) becomes `undoTurn()` for Tombstone, and `moveTimes.removeLast()` (`:2275`) runs once per removed entry. The existing clock restore (`:2277-2318`) is unchanged: it sets the accepting player's clock from `moveTimes.getLast()`, which is then the last snapshot of that player's previous turn (post-increment for every turn shape, thanks to Task 4), and takes `incrementalSeconds` back from the undoing player.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:2265-2276`
- Test: `ServerTableTombstoneTest` (extend)

**Interfaces:**
- Consumes: Plan A `TombstoneState.undoTurn()` (returns entries removed, 1 or 2), `canPlayerUndo(int seat)`.
- Produces: no new API.

- [ ] **Step 1: Write the failing tests**

```java
public void testUndoTwoEntryTurn() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    turn(t, "bob", 200, 201);
    t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
    t.handleUndoReply(new DSGUndoReplyTableEvent("alice", 3, true));
    assertEquals(1, t.grid().getNumMoves());
    assertEquals(1, t.times().size());
    assertEquals(2, t.grid().getCurrentPlayer());
    assertEquals(5, t.timer(2).getMinutes());
    assertEquals(0, t.timer(2).getSeconds());            // increment taken back
}

public void testUndoOwnOnlyTurnClosedBySkip() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
    t.handleUndoReply(new DSGUndoReplyTableEvent("alice", 3, true));
    assertEquals(1, t.grid().getNumMoves());
    assertEquals(1, t.times().size());
    assertFalse(((TombstoneState) t.grid()).isAwaitingDeadStone());
}

public void testUndoRestoresAcceptorsPostIncrementTimeAfterSkip() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false)); // bob 5:10
    turn(t, "alice", 220, 221);                                                      // alice 5:10
    t.handleUndoRequest(new DSGUndoRequestTableEvent("alice", 3));
    t.handleUndoReply(new DSGUndoReplyTableEvent("bob", 3, true));
    assertEquals(2, t.grid().getNumMoves());
    assertEquals(2, t.times().size());
    assertEquals(5, t.timer(2).getMinutes());
    assertEquals(10, t.timer(2).getSeconds());           // bob restored post-increment
    assertEquals(5, t.timer(1).getMinutes());
    assertEquals(0, t.timer(1).getSeconds());            // alice loses her increment
}

public void testNoUndoMidTurnOrOfCentre() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleUndoRequest(new DSGUndoRequestTableEvent("alice", 3));
    t.handleMove("bob", 200);
    t.handleUndoRequest(new DSGUndoRequestTableEvent("bob", 3));
    List<DSGUndoRequestTableErrorEvent> a = to(t, "alice", DSGUndoRequestTableErrorEvent.class);
    List<DSGUndoRequestTableErrorEvent> b = to(t, "bob", DSGUndoRequestTableErrorEvent.class);
    assertEquals(DSGTableErrorEvent.CANT_UNDO, a.get(0).getError());
    assertEquals(DSGTableErrorEvent.CANT_UNDO, b.get(0).getError());
    assertFalse(t.undoFlag());
}
```

- [ ] **Step 2: Run to verify they fail**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `testUndoTwoEntryTurn` and `testUndoRestoresAcceptorsPostIncrementTimeAfterSkip` fail (one entry undone, `times().size()` off by one). The mid-turn test may already pass through Plan A's `canPlayerUndo`.

- [ ] **Step 3: Implement**

Replace `gridState.undoMove();` with: `int removedEntries = 1;` then `if (gridState instanceof TombstoneState) removedEntries = ((TombstoneState) gridState).undoTurn(); else gridState.undoMove();`. Replace the single `moveTimes.removeLast();` with a loop of `removedEntries` removals. Everything else in the method stays.

- [ ] **Step 4: Run to verify they pass**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `OK (17 tests)`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java
git commit -m "Undo the whole last turn in live Tombstone games"
```

---

### Task 6: Rejoin correction (silent skip)

Spec §8.5. A bulk move list cannot tell "own entry, dead stone pending" from "own entry, dead stone skipped". React's default after a bulk list is "pending" (spec §13.5), so when the trailing turn was closed without a dead stone the server sends a silent skip to the joining player only, **after** the bulk list (the bulk list resets client state). The send goes into a small protected method so it is testable without driving the whole of `handleJoin`.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java` (new `protected void sendTombstoneTurnState(String toPlayer)` next to `sendMoves` at `:646`; call it on the line after `sendMoves(player);` at `:610`)
- Test: `ServerTableTombstoneTest` (extend)

**Interfaces:**
- Consumes: Plan A `TombstoneState.isTrailingTurnClosedWithoutDead()`, `getColor(int entryIndex)` (entry's player).
- Produces: `protected void sendTombstoneTurnState(String toPlayer)`: if `gridState instanceof TombstoneState` and `isTrailingTurnClosedWithoutDead()`, route `new DSGTombstoneSkipDeadTableEvent(name, tableNum, true)` to `toPlayer`, where `name` is `playingPlayers[getColor(getNumMoves() - 1)].getName()`, or `null` if that seat is empty (the Swap2 silent event also sends a `null` player, `:552`). Otherwise send nothing. `TombTable` exposes it as `void sendTurnState(String p)`.

- [ ] **Step 1: Write the failing tests**

```java
public void testSilentSkipAfterOwnOnlyTurn() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.handleTombstoneSkipDead(new DSGTombstoneSkipDeadTableEvent("bob", 3, false));
    t.sendTurnState("sam");
    List<DSGTombstoneSkipDeadTableEvent> s = to(t, "sam", DSGTombstoneSkipDeadTableEvent.class);
    assertEquals(1, s.size());
    assertTrue(s.get(0).isSilent());
    assertEquals("bob", s.get(0).getPlayer());
}

public void testNoSilentSkipWhileAwaitingDead() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.handleMove("bob", 200);
    t.sendTurnState("sam");
    assertEquals(0, to(t, "sam", DSGTombstoneSkipDeadTableEvent.class).size());
}

public void testNoSilentSkipAfterCentreOrDeadEntry() {
    TombTable t = new TombTable(true, 5, 33, 34);
    turn(t, "alice", 180, -1);
    t.sendTurnState("sam");
    turn(t, "bob", 200, 201);
    t.sendTurnState("sam");
    assertEquals(0, to(t, "sam", DSGTombstoneSkipDeadTableEvent.class).size());
}

public void testNonTombstoneTableSendsNothing() {
    TombTable t = new TombTable(true, 5, 1, 2);
    t.useGame(GridStateFactory.PENTE_GAME);
    t.handleMove("alice", 180);
    t.handleMove("bob", 200);
    t.sendTurnState("sam");
    assertEquals(0, to(t, "sam", DSGTombstoneSkipDeadTableEvent.class).size());
}
```

- [ ] **Step 2: Run to verify they fail**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: compile failure on `sendTombstoneTurnState`.

- [ ] **Step 3: Implement the method and the call after `sendMoves(player)` at `:610`**

- [ ] **Step 4: Run to verify they pass, and check the call order**

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `OK (21 tests)`.
Then `grep -n -A1 "sendMoves(player);" dsg_src/java/org/pente/gameServer/server/ServerTable.java` shows `sendTombstoneTurnState(player);` on the next line.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java
git commit -m "Tell rejoining clients when the last Tombstone turn skipped its dead stone"
```

---

### Task 7: `ExperimentalServer` and the server-offering rule (both directions)

Spec §7.3, §7.5, D9, D14; placement per "Deviation from the spec" above. A table's game is chosen at three sites, none of which checks `serverData.getGameEvents()`: `handleChangeState` (`:925`, `:938-941`), `ServerTable.resetTable` (`:281-302`) and `ArenaServerTable.resetTable` (`ArenaServerTable.java:354`). At game end `getGameData` dereferences `getGameEvent(game.getId())` (`:3407`), which is `null` when the server has no row for the id (`:3373-3381`), so a table playing an unoffered game throws a NullPointerException at game end (and on 45 creates a junk hill, `:3841-3842`). One pure predicate fixes all three, for every game: because only Experimental offers Tombstone and Experimental offers only Tombstone, it enforces both directions (Tombstone only on Experimental, nothing else on Experimental). It also closes the existing gap for non-Go games on the Go server (46); that is intended (spec §7.3).

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/core/ExperimentalServer.java`
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:884-886` (new `else if` before the final `else {` of the validation chain), `:279-302` (preference restore and default in `resetTable`), and a new `protected Game requestedGame(DSGChangeStateTableEvent e)`
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java:359-364` (after the speed/normal conversion)
- Test: `dsg_src/java/org/pente/gameServer/core/test/ExperimentalServerTest.java` (new), `ServerTableTombstoneTest` (extend)

**Interfaces:**
- Consumes: Plan A `GridStateFactory.TOMBSTONE`, `SPEED_TOMBSTONE`, `getSpeedGame(Game)`, `getNormalGame(Game)`, `getGame(int)`, `PENTE_GAME`; existing `Game.isSpeedGame(int initial, int incremental)` (`Game.java:28-33`: speed iff `initial*60 + incremental*15 < 331`, or `incremental < 24` when `initial == 0`); existing `ServerData` getters (`ServerData.java:13-23`), `GameEventData.getGame()`.
- Produces (spec §7.5, verbatim):
  - `public final class ExperimentalServer` in `org.pente.gameServer.core`: `ID = 47`, `NAME = "Experimental"`, `PORT = 16004`, `int[] GAMES = {GridStateFactory.TOMBSTONE, GridStateFactory.SPEED_TOMBSTONE}`; `isExperimental(ServerData sd)` = `sd != null && sd.getServerId() == ID && NAME.equals(sd.getName())`; `isExperimentalRoom(String roomName)` = `NAME.equals(roomName)`; `acceptsNativeSocket(ServerData sd)` = `!isExperimental(sd)`; `gameAllowed(ServerData sd, int gameId)` reads only the in-memory `sd.getGameEvents()` (never a DB lookup by server id: the Arena, id 0, has no `dsg_server_game` rows and holds a copy of server 1's events made in `DSGContextListener.setupArenaServers`, `:347-358`). If `sd == null`, `sd.isTournament()` or `sd.getGameEvents().isEmpty()`: `true` iff `gameId` is not a Tombstone id (today's behaviour for those servers, but Tombstone never leaks). Otherwise `true` iff some event has `getGame() == gameId`; `Game defaultGame(ServerData sd)` = the offered id with the lowest value among non-speed games (`!GridStateFactory.getGame(id).isSpeed()`), as a `Game`, or `GridStateFactory.PENTE_GAME` if there is none. The class comment says it is the single source of truth for the Experimental server.
  - `protected Game requestedGame(DSGChangeStateTableEvent e)` in `ServerTable`: `GridStateFactory.getGame(e.getGame())` converted exactly as `:927-935`, but with `speed = e.getTimed() && Game.isSpeedGame(e.getInitialMinutes(), e.getIncrementalSeconds())`.
- `handleChangeState` chain: `} else if (!ExperimentalServer.gameAllowed(serverData, requestedGame(changeStateEvent).getId())) { status = DSGTableErrorEvent.UNKNOWN; }`, placed after `serverData.isTournament()` (`:884-885`). The existing error tail (`:962-972`) then sends `DSGChangeStateTableErrorEvent` with the table's current settings.
- `ServerTable.resetTable`, inside the `"gameState"` branch, right after the cast to `DSGChangeStateTableEvent e` and before any field is assigned: `if (!ExperimentalServer.gameAllowed(serverData, requestedGame(e).getId())) { break; }`. This leaves `fromPref == false`, so the existing fallback (`:298-302`) runs. Re-running the speed/normal conversion on the stored event is idempotent (its id was converted against the same settings before it was saved, `:955`, `:975-977`). In that fallback, after `game = GridStateFactory.PENTE_GAME;`, add `if (!ExperimentalServer.gameAllowed(serverData, game.getId())) { game = ExperimentalServer.defaultGame(serverData); }`. 10/5 is not a speed setting, so the normal id is right; `timed`/`rated`/`tableType` keep the defaults set at `:263-265`.
- `ArenaServerTable.resetTable`, after the conversion at `:359-364`: `if (!ExperimentalServer.gameAllowed(serverData, game.getId())) { Game d = ExperimentalServer.defaultGame(serverData); game = speed ? GridStateFactory.getSpeedGame(d) : d; }`. The Arena's events are a copy of server 1's (`DSGContextListener.java:356-358`), so a crafted Tombstone create request becomes Pente.
- `TombTable` gains `void asExperimental()`: `serverData.setServerId(ExperimentalServer.ID); serverData.setName(ExperimentalServer.NAME);`.
- `TombTable` gains `void createFromPreference(DSGChangeStateTableEvent saved)`: sets `dsgPlayerStorer` to a `java.lang.reflect.Proxy` of `DSGPlayerStorer` whose `loadPlayerPreferences` returns `List.of(new DSGPlayerPreference("gameState", saved))` and whose other methods return `null`; calls `resetTable(new DSGJoinTableEvent("alice", 3))` (`alice` is in `playersInMainRoom`, so `getPlayerInMainRoom` at `:274` finds her); then calls `destroy()` on each entry of the new `timers` array, because `resetTable` replaces them with thread-backed `MilliSecondGameTimer`s (`:323-325`; `MilliSecondGameTimer.java:28-29` starts a thread).
- `TombTable` gains `void ownerIsGuest()` (sets `alice`'s guest flag via `SimpleDSGPlayerData.setGuest(true)`, so the success path skips `dsgPlayerStorer.storePlayerPreference` at `:977`).

- [ ] **Step 1: Write the failing tests**

`ExperimentalServerTest` (new, `org.pente.gameServer.core.test`, JUnit 3 like `IgnoreRulesTest`):

```java
private static ServerData server(int id, String name, boolean tournament, int... games) {
    ServerData d = new ServerData();
    d.setServerId(id);
    d.setName(name);
    d.setTournament(tournament);
    for (int g : games) {
        SimpleGameEventData e = new SimpleGameEventData();
        e.setGame(g);
        e.setName(MySQLGameVenueStorer.LIVE_EVENT);
        d.addGameEvent(e);
    }
    return d;
}

public void testIdentity() {
    assertEquals(47, ExperimentalServer.ID);
    assertEquals("Experimental", ExperimentalServer.NAME);
    assertEquals(16004, ExperimentalServer.PORT);
    assertTrue(Arrays.equals(new int[]{33, 34}, ExperimentalServer.GAMES));
}

public void testTombstoneOnlyOnExperimental() {
    ServerData main = server(1, "Main Room", false, 1, 2, 31, 32);
    assertFalse(ExperimentalServer.gameAllowed(main, GridStateFactory.TOMBSTONE));
    assertFalse(ExperimentalServer.gameAllowed(main, GridStateFactory.SPEED_TOMBSTONE));
    assertTrue(ExperimentalServer.gameAllowed(main, GridStateFactory.PENTE));
}

public void testNothingElseOnExperimental() {
    ServerData exp = server(47, "Experimental", false, 33, 34);
    assertTrue(ExperimentalServer.gameAllowed(exp, 33));
    assertTrue(ExperimentalServer.gameAllowed(exp, 34));
    assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.PENTE));
    assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.SPEED_PENTE));
    assertFalse(ExperimentalServer.gameAllowed(exp, GridStateFactory.GO));
}

public void testTournamentAndUnconfiguredServersNeverAllowTombstone() {
    assertFalse(ExperimentalServer.gameAllowed(server(99, "Tourney", true, 1), 33));
    assertTrue(ExperimentalServer.gameAllowed(server(99, "Tourney", true, 1), GridStateFactory.PENTE));
    assertFalse(ExperimentalServer.gameAllowed(server(98, "Bare", false), 33));
    assertTrue(ExperimentalServer.gameAllowed(server(98, "Bare", false), GridStateFactory.PENTE));
    assertFalse(ExperimentalServer.gameAllowed(null, 34));
}

// The Arena (id 0) is in-memory: DSGContextListener.setupArenaServers copies
// server 1's GameEventData into a new ServerData and never reads dsg_server_game
// for id 0. The rule must work off that in-memory copy, never a DB lookup by id.
public void testArenaUsesCopiedServerOneEvents() {
    ServerData main = server(1, "Main Room", false, 1, 2, 31, 32);
    ServerData arena = server(0, "Arena Server", false);
    for (Object e : main.getGameEvents()) {
        arena.addGameEvent((GameEventData) e);
    }
    assertTrue(ExperimentalServer.gameAllowed(arena, GridStateFactory.PENTE));
    assertTrue(ExperimentalServer.gameAllowed(arena, 32));
    assertFalse(ExperimentalServer.gameAllowed(arena, 33));
    assertFalse(ExperimentalServer.gameAllowed(arena, 34));
    assertFalse(ExperimentalServer.isExperimental(arena));
}

public void testArenaWithoutCopiedEventsStillRefusesTombstone() {
    // server 1 failed to load: the Arena's copy is empty
    assertFalse(ExperimentalServer.gameAllowed(server(0, "Arena Server", false), 33));
}

public void testDefaultGameIsLowestNormalOfferedId() {
    assertEquals(33, ExperimentalServer.defaultGame(server(47, "Experimental", false, 34, 33)).getId());
    assertEquals(GridStateFactory.GO, ExperimentalServer.defaultGame(
        server(46, "Go", false, 20, 19, 22, 21, 24, 23)).getId());
    assertEquals(GridStateFactory.PENTE, ExperimentalServer.defaultGame(server(1, "Main Room", false, 2, 1)).getId());
    assertEquals(GridStateFactory.PENTE, ExperimentalServer.defaultGame(server(98, "Bare", false)).getId());
}

public void testIsExperimentalNeedsIdAndName() {
    assertTrue(ExperimentalServer.isExperimental(server(47, "Experimental", false)));
    assertFalse(ExperimentalServer.isExperimental(server(47, "Someone else's room", false)));
    assertFalse(ExperimentalServer.isExperimental(server(1, "Experimental", false)));
    assertFalse(ExperimentalServer.isExperimental(null));
}

public void testNativeSocketsRefusedOnlyOnExperimental() {
    assertFalse(ExperimentalServer.acceptsNativeSocket(server(47, "Experimental", false)));
    assertTrue(ExperimentalServer.acceptsNativeSocket(server(1, "Main Room", false)));
    assertTrue(ExperimentalServer.acceptsNativeSocket(server(0, "Arena Server", false)));
}

public void testRoomName() {
    assertTrue(ExperimentalServer.isExperimentalRoom("Experimental"));
    assertFalse(ExperimentalServer.isExperimentalRoom("Main Room"));
    assertFalse(ExperimentalServer.isExperimentalRoom(null));
}
```

`ServerTableTombstoneTest` (extend):

```java
private static DSGChangeStateTableEvent change(TombTable t, int game, int minutes) {
    DSGChangeStateTableEvent e = new DSGChangeStateTableEvent("alice", 3);
    e.setGame(game);
    e.setInitialMinutes(minutes);
    e.setIncrementalSeconds(0);
    e.setTimed(true);
    e.setRated(false);
    e.setTableType(t.tableType());   // TombTable accessor: unchanged table type
    return e;
}

public void testChangeToTombstoneRejectedWithoutEventRow() {
    TombTable t = new TombTable(true, 5, 1, 2);          // KOTH-like: Pente rows only
    t.useGame(GridStateFactory.PENTE_GAME);
    t.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS);
    int resets = t.timer(1).resetCalls;
    t.handleChangeState(change(t, GridStateFactory.TOMBSTONE, 20));
    List<DSGChangeStateTableErrorEvent> errs = to(t, "alice", DSGChangeStateTableErrorEvent.class);
    assertEquals(1, errs.size());
    assertEquals(DSGTableErrorEvent.UNKNOWN, errs.get(0).getError());
    assertEquals(GridStateFactory.PENTE, errs.get(0).getGame());
    assertEquals(GridStateFactory.PENTE, t.currentGame().getId());
    assertEquals(5, t.initialMinutes());                 // nothing applied
    assertEquals(resets, t.timer(1).resetCalls);
}

public void testSpeedTombstoneCheckedAfterConversion() {
    TombTable t = new TombTable(true, 20, 1, 2, 33);     // normal Tombstone row only
    t.useGame(GridStateFactory.PENTE_GAME);
    t.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS);
    t.handleChangeState(change(t, GridStateFactory.TOMBSTONE, 5));   // 5+0 is speed -> 34
    assertEquals(1, to(t, "alice", DSGChangeStateTableErrorEvent.class).size());
    assertEquals(GridStateFactory.PENTE, t.currentGame().getId());
}

public void testChangeToTombstoneAllowedWhenOffered() {
    TombTable t = new TombTable(true, 5, 1, 2, 33, 34);
    t.useGame(GridStateFactory.PENTE_GAME);
    t.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS);
    t.ownerIsGuest();
    t.handleChangeState(change(t, GridStateFactory.TOMBSTONE, 20));
    assertEquals(0, to(t, "alice", DSGChangeStateTableErrorEvent.class).size());
    assertEquals(GridStateFactory.TOMBSTONE, t.currentGame().getId());
}

public void testChangeToPenteRejectedOnExperimental() {
    TombTable t = new TombTable(true, 5, 33, 34);        // Experimental: Tombstone rows only
    t.asExperimental();
    t.useGame(GridStateFactory.TOMBSTONE_GAME);
    t.state(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS);
    t.handleChangeState(change(t, GridStateFactory.PENTE, 20));
    List<DSGChangeStateTableErrorEvent> errs = to(t, "alice", DSGChangeStateTableErrorEvent.class);
    assertEquals(1, errs.size());
    assertEquals(DSGTableErrorEvent.UNKNOWN, errs.get(0).getError());
    assertEquals(GridStateFactory.TOMBSTONE, errs.get(0).getGame());
    assertEquals(GridStateFactory.TOMBSTONE, t.currentGame().getId());
}

public void testTombstonePreferenceIgnoredWithoutEventRow() {
    TombTable t = new TombTable(true, 5, 1, 2);          // Main-Room-like: no Tombstone rows
    DSGChangeStateTableEvent saved = change(t, GridStateFactory.TOMBSTONE, 20);  // as saved on Experimental
    t.createFromPreference(saved);
    assertEquals(GridStateFactory.PENTE, t.currentGame().getId());   // no-preference default
    assertEquals(10, t.initialMinutes());
}

public void testTombstonePreferenceRestoredWhenOffered() {
    TombTable t = new TombTable(true, 5, 33, 34);
    t.asExperimental();
    t.createFromPreference(change(t, GridStateFactory.TOMBSTONE, 20));
    assertEquals(GridStateFactory.TOMBSTONE, t.currentGame().getId());
    assertEquals(20, t.initialMinutes());
}

public void testNewTableOnExperimentalOpensAsTombstone() {
    TombTable t = new TombTable(true, 5, 33, 34);
    t.asExperimental();
    t.createFromPreference(change(t, GridStateFactory.PENTE, 20));   // Pente preference saved on server 1
    assertEquals(GridStateFactory.TOMBSTONE, t.currentGame().getId()); // preference ignored, default is Tombstone
    assertEquals(10, t.initialMinutes());
}
```

`TombTable` gains `int tableType()`.

- [ ] **Step 2: Run to verify they fail**

**Run** `org.pente.gameServer.core.test.ExperimentalServerTest`.
Expected: compile failure, `cannot find symbol: class ExperimentalServer`. (Because `compile-tests` compiles every test source, create an empty `public final class ExperimentalServer {}` stub first if the compile failure blocks the next run, then re-run: every `ExperimentalServerTest` test fails.)

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `testChangeToTombstoneRejectedWithoutEventRow`, `testSpeedTombstoneCheckedAfterConversion`, `testChangeToPenteRejectedOnExperimental`, `testTombstonePreferenceIgnoredWithoutEventRow` and `testNewTableOnExperimentalOpensAsTombstone` fail (no error event, or the wrong game). `testChangeToTombstoneAllowedWhenOffered` and `testTombstonePreferenceRestoredWhenOffered` pass already.

- [ ] **Step 3: Implement `ExperimentalServer`, `requestedGame`, the `else if`, the two `resetTable` checks and the Arena check**

- [ ] **Step 4: Run to verify they pass**

**Run** `org.pente.gameServer.core.test.ExperimentalServerTest`.
Expected: `OK (8 tests)`.

**Run** `org.pente.gameServer.server.test.ServerTableTombstoneTest`.
Expected: `OK (28 tests)`.

Then **Run** the existing table suites `org.pente.gameServer.server.test.ServerTableKidTest`, `org.pente.gameServer.server.test.ServerTableDrawScoringTest` and `org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`. Expected: each `OK` with its existing count. None of them calls `handleChangeState` or `resetTable` (they override only `resetTableGameOver`, `ServerTableDrawScoringTest.java:240`, `ArenaServerTableJoinRequestTest.java:77`), so the rule does not reach them.

Then:
```bash
grep -n "ExperimentalServer.gameAllowed" dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java
grep -rn "16004\|\"Experimental\"\|= 47;" dsg_src/java --include=*.java | grep -v "/test/"
```
Expected: three `gameAllowed` calls in `ServerTable.java` (chain, preference, default) and one in `ArenaServerTable.java`; the second grep prints only `ExperimentalServer.java`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/core/ExperimentalServer.java dsg_src/java/org/pente/gameServer/core/test/ExperimentalServerTest.java dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java dsg_src/java/org/pente/gameServer/server/test/ServerTableTombstoneTest.java
git commit -m "Refuse table games a server does not offer: Tombstone only on Experimental, nothing else there"
```

---

### Task 8: Boot registration without KOTH, and the Experimental server

Spec §7.2, D14. `MySQLGameVenueStorer.registerAllGames` (`:686-707`) builds `(game, eventName)` rows from `LIVE_GAMES` and `TB_GAMES`, each with a `KOTH_EVENT` row, and hands them to `ensureGameEvents` (insert-if-absent, `:712-760`). `DSGContextListener` (best-effort `try` at `:127-149`) offers `LIVE_GAMES` to servers 1 and 37 (`:141-142`), the Go ids to 46 (`:144`) and `LIVE_GAMES` as KOTH to 45 (`:146`); the Arena server copies server 1 (`setupArenaServers`, `:347-358`). Tombstone ids come from Plan A's separate arrays, so `LIVE_GAMES`/`TB_GAMES` and every KOTH site stay untouched. `game_event.game` and `dsg_server_game.game` are `tinyint unsigned` (`schema.sql:266`), so 33, 34 and 83 fit. None of servers 1, 37, 45, 46 gets a Tombstone id. The Experimental server is created by an insert-if-absent with an explicit id (`dsg_server.id` is `AUTO_INCREMENT`, `schema.sql:239-249`; local main and replica both have `max(id) = 46`, see D14), then offered `ExperimentalServer.GAMES` with the existing `addServerGames` (`MySQLServerStorer.java:86-123`). Both run before `setupLiveGameServers()` (`DSGContextListener.java:329`), which starts every active non-tournament row (`:385-398`). Extract the event rows and the offering table as pure static methods so they are testable without a database.

**Files:**
- Modify: `dsg_src/java/org/pente/game/MySQLGameVenueStorer.java:686-707`
- Modify: `dsg_src/java/org/pente/gameServer/core/MySQLServerStorer.java` (new `ensureServer` after `addServerGames`, `:123`)
- Modify: `dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:130-146`
- Test: `dsg_src/java/org/pente/game/test/TombstoneBootRegistrationTest.java` (new)

**Interfaces:**
- Consumes: Plan A `GridStateFactory.NON_KOTH_LIVE_GAMES = {TOMBSTONE, SPEED_TOMBSTONE}`, `NON_KOTH_TB_GAMES = {TB_TOMBSTONE}`; Task 7 `ExperimentalServer.ID`, `NAME`, `PORT`, `GAMES`.
- Produces:
  - `public static List<Object[]> MySQLGameVenueStorer.allGameEventRows()`: today's rows (in today's order), then `{id, LIVE_EVENT}` for each `NON_KOTH_LIVE_GAMES` id and `{id, TB_EVENT}` for each `NON_KOTH_TB_GAMES` id. `registerAllGames(siteId)` becomes `ensureGameEvents(siteId, allGameEventRows())` plus its log line.
  - `public static boolean MySQLServerStorer.ensureServer(DBHandler dbHandler, int id, String name, int port) throws Throwable`: `insert ignore into dsg_server (id, name, port, tournament, active, creation_date, last_mod_date, private) values (?, ?, ?, 'N', 'Y', sysdate(), sysdate(), 'N')`, then `select name from dsg_server where id = ?`; returns `name.equals(storedName)`. Never updates an existing row (an admin deactivation through `admin/modifyServers.jsp` survives reboots). Same connection/statement/`finally` pattern as `addServerGames`.
  - `public static List<Object[]> DSGContextListener.serverOfferings()`: rows `{Integer serverId, int[] games, String eventName}` in boot order: `{1, LIVE_GAMES, LIVE_EVENT}`, `{37, LIVE_GAMES, LIVE_EVENT}`, `{46, goGames, LIVE_EVENT}`, `{45, LIVE_GAMES, KOTH_EVENT}`, `{ExperimentalServer.ID, ExperimentalServer.GAMES, LIVE_EVENT}`. The `goGames` literal moves from the method body (`:136-139`) into this method unchanged.
  - Boot (`:130-146`): `for (Object[] row : serverOfferings())`: if the row is Experimental's and `!MySQLServerStorer.ensureServer(dbHandler, ExperimentalServer.ID, ExperimentalServer.NAME, ExperimentalServer.PORT)`, `log4j.error("dsg_server id " + ExperimentalServer.ID + " is not the Experimental server; not offering Tombstone")` and `continue`; else `MySQLServerStorer.addServerGames(dbHandler, id, 2, games, eventName)`. All inside the existing `try`, after `registerAllGames(2)`. Update the comment above the loop: servers 1/37/45/46 never get Tombstone; Experimental gets only Tombstone; the Arena copies server 1.

- [ ] **Step 1: Write the failing test**

```java
public void testTombstoneHasOnlyLiveAndTbRows() {
    List<Object[]> rows = MySQLGameVenueStorer.allGameEventRows();
    assertEquals(2 * GridStateFactory.LIVE_GAMES.length + 2 * GridStateFactory.TB_GAMES.length + 3,
        rows.size());
    assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT), namesFor(rows, 33));
    assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT), namesFor(rows, 34));
    assertEquals(Arrays.asList(MySQLGameVenueStorer.TB_EVENT), namesFor(rows, 83));
}

public void testExistingGamesKeepKothRows() {
    List<Object[]> rows = MySQLGameVenueStorer.allGameEventRows();
    for (int g : GridStateFactory.LIVE_GAMES) {
        assertEquals(Arrays.asList(MySQLGameVenueStorer.LIVE_EVENT, MySQLGameVenueStorer.KOTH_EVENT),
            namesFor(rows, g));
    }
    for (int g : GridStateFactory.TB_GAMES) {
        assertEquals(Arrays.asList(MySQLGameVenueStorer.TB_EVENT, MySQLGameVenueStorer.KOTH_EVENT),
            namesFor(rows, g));
    }
}

public void testOnlyExperimentalOffersTombstone() {
    List<Object[]> offerings = DSGContextListener.serverOfferings();
    assertEquals(5, offerings.size());
    for (Object[] row : offerings) {
        int server = (Integer) row[0];
        int[] games = (int[]) row[1];
        if (server == ExperimentalServer.ID) {
            assertTrue(Arrays.equals(new int[]{33, 34}, games));
            assertEquals(MySQLGameVenueStorer.LIVE_EVENT, row[2]);
        } else {
            assertFalse("server " + server, contains(games, 33));
            assertFalse("server " + server, contains(games, 34));
        }
    }
}

public void testExistingServersKeepTodaysOfferings() {
    List<Object[]> offerings = DSGContextListener.serverOfferings();
    assertEquals(Arrays.asList(1, 37, 46, 45, ExperimentalServer.ID),
        offerings.stream().map(r -> (Integer) r[0]).collect(Collectors.toList()));
    assertSame(GridStateFactory.LIVE_GAMES, offerings.get(0)[1]);
    assertSame(GridStateFactory.LIVE_GAMES, offerings.get(1)[1]);
    assertEquals(6, ((int[]) offerings.get(2)[1]).length);                 // the Go ids
    assertSame(GridStateFactory.LIVE_GAMES, offerings.get(3)[1]);
    assertEquals(MySQLGameVenueStorer.KOTH_EVENT, offerings.get(3)[2]);  // 45 stays KOTH, no Tombstone
}
// namesFor(rows, g): event names of the rows whose game == g, in order. contains(int[], int): linear scan.
```

- [ ] **Step 2: Run to verify it fails**

**Run** `org.pente.game.test.TombstoneBootRegistrationTest`.
Expected: compile failure on `allGameEventRows` / `serverOfferings`.

- [ ] **Step 3: Implement `allGameEventRows`, `ensureServer`, `serverOfferings` and the boot loop**

- [ ] **Step 4: Run to verify it passes, plus the venue regression**

**Run** `org.pente.game.test.TombstoneBootRegistrationTest`, then `org.pente.game.test.MySQLGameVenueStorerTbLookupTest`.
Expected: `OK (4 tests)`, then the existing count `OK`.

- [ ] **Step 5: Check `ensureServer` against the local database (read-only checks; the restart is the local smoke-test route, never `sync_gameServer.sh`)**

Compile, bind-mount and restart `penteorg-pente.org-1` as in the "Local smoke-test server classes" memory, then:
```bash
docker exec penteorg-main_db-1 sh -c 'mariadb -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" dsg -e "select id,name,port,tournament,active,private from dsg_server where id=47; select g.server_id, g.game, e.name from dsg_server_game g join game_event e on e.eid=g.event_id where g.game in (33,34,83) order by g.server_id, g.game;"'
docker logs penteorg-pente.org-1 2>&1 | grep -E "Experimental|not the Experimental server" | tail -5
```
Expected: one row `47 Experimental 16004 N Y N`; exactly two offering rows, `47 33 Live Game` and `47 34 Live Game`, and none for servers 1, 37, 45, 46; a `Server [47=Experimental:16004, false] started.` log line (`DSGContextListener.java:371`, format from `ServerData.toString`, `ServerData.java:109-111`) and no "not the Experimental server" error. Restart once more and re-run the query: still one row 47 and two offering rows (idempotent).

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/game/MySQLGameVenueStorer.java dsg_src/java/org/pente/gameServer/core/MySQLServerStorer.java dsg_src/java/org/pente/gameServer/server/DSGContextListener.java dsg_src/java/org/pente/game/test/TombstoneBootRegistrationTest.java
git commit -m "Register Tombstone events at boot without KOTH and create the Experimental server offering only Tombstone"
```

---

### Task 9: Archive sentinel `723` and the game-aware load filter

Spec §9.1-9.2. `storeGame` writes one `pente_move` row per entry of `createGridState(game, data)` (`MySQLPenteGameStorer.java:479-529`); row `i` carries `next_move = data.getMove(i + 1)`, and the last row the literal sentinel `361` (`:509-511`). `loadGame` drops rows with `next_move != 361` (`:778`; Go has its own unfiltered query at `:770-774`) and prepends the centre (`:784-786`). For Tombstone, `361` is a real code (dead stone on A19), so the sentinel becomes `723` and the filter becomes `next_move != 723` on Tombstone ids. The id comes from `getGameId(data.getGame())`, which resolves "Tombstone Pente" to 33 and "Speed Tombstone Pente" to 34; finished TB games are archived under the live name (spec §7.1), so 83 never reaches the archive but is handled the same way. `loadGames` (batch, `:936`) keeps `!= 361`: its only caller is webdb's `GameSearchHandler` (`:400`), which Task 10 makes reject Tombstone.

Plan A's `TombstoneState.getInstance` appends exactly one inner entry per stored code, so `state.getNumMoves() == data.getNumMoves()` and `state.getHash(i)`, `data.getMove(i + 1)` and `moveTimes.get(i)` stay aligned; this task pins that, including a trailing own entry left by a mid-turn resign or timeout. The winner column comes from `data.getWinner()` (`:518`), never from the replayed state.

**Files:**
- Modify: `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java:508-514` (write), `:765-779` (load query), `:933-937` (comment only)
- Test: `dsg_src/java/org/pente/game/test/MySQLPenteGameStorerTombstoneTest.java` (new)

**Interfaces:**
- Consumes: Plan A `TombstoneCodes.archiveNoNextMove(int game)` (`723` for Tombstone ids, else `361`), `TombstoneCodes.ARCHIVE_NO_NEXT_MOVE`, `TombstoneCodes.deadCode`, `OWN_PASS`; `GridStateFactory.createGridState(int, MoveData)`.
- Produces:
  - `public static int MySQLPenteGameStorer.archiveNextMove(GameData data, int game, int row, int numRows)`: `row == numRows - 1 ? TombstoneCodes.archiveNoNextMove(game) : data.getMove(row + 1)`. The write loop calls it in place of the `if/else` at `:510-514`, with `numRows = state.getNumMoves()`.
  - `public static String MySQLPenteGameStorer.loadMovesSql(int game)`: the Go-family query (no `next_move` filter) for the six Go ids exactly as at `:766-774`; otherwise the filtered query with `"and next_move != " + TombstoneCodes.archiveNoNextMove(game) + " "`. `loadGame` uses it.
  - A comment on `loadGames` stating it keeps the `361` filter because webdb rejects Tombstone ids before calling it.

- [ ] **Step 1: Write the failing test**

```java
private static GameData game(String name, int... moves) {
    GameData d = new DefaultGameData();
    d.setGame(name);
    for (int m : moves) d.addMove(m);
    return d;
}

private static final int[] MIXED = {180, 200, 361 + 0, 722, 220, 361 + 5, 722};

public void testLastRowSentinelIs723ForTombstone() {
    GameData d = game("Tombstone Pente", MIXED);
    int n = MIXED.length;
    assertEquals(TombstoneCodes.ARCHIVE_NO_NEXT_MOVE, MySQLPenteGameStorer.archiveNextMove(d, 33, n - 1, n));
    assertEquals(723, MySQLPenteGameStorer.archiveNextMove(d, 34, n - 1, n));
    assertEquals(361, MySQLPenteGameStorer.archiveNextMove(d, 33, 1, n));   // dead on A19 is kept
    assertEquals(722, MySQLPenteGameStorer.archiveNextMove(d, 33, 2, n));
}

public void testLastRowSentinelUnchangedForOtherGames() {
    GameData d = game("Pente", 180, 200, 220);
    assertEquals(361, MySQLPenteGameStorer.archiveNextMove(d, GridStateFactory.PENTE, 2, 3));
}

public void testLoadSqlIsGameAware() {
    assertTrue(MySQLPenteGameStorer.loadMovesSql(33).contains("next_move != 723"));
    assertTrue(MySQLPenteGameStorer.loadMovesSql(34).contains("next_move != 723"));
    assertTrue(MySQLPenteGameStorer.loadMovesSql(GridStateFactory.PENTE).contains("next_move != 361"));
    assertFalse(MySQLPenteGameStorer.loadMovesSql(GridStateFactory.GO).contains("next_move !="));
}

public void testRoundTripKeepsDeadStoneOnPointZero() {
    GameData d = game("Tombstone Pente", MIXED);
    List<Integer> loaded = new ArrayList<>();
    loaded.add(GridStateFactory.getCenterMove(33));                 // load prepends the centre
    for (int i = 0; i < MIXED.length; i++) {
        int next = MySQLPenteGameStorer.archiveNextMove(d, 33, i, MIXED.length);
        if (next != 723) loaded.add(next);                         // the loadGame filter
    }
    assertEquals(toList(MIXED), loaded);
}

public void testTrailingOwnEntryKeepsRowAlignment() {
    // resign/timeout while the dead stone was pending: history ends in an open own entry
    GameData d = game("Tombstone Pente", 180, 200, 361 + 201, 220);
    d.setWinner(1);
    GridState s = GridStateFactory.createGridState(33, d);
    assertEquals(d.getNumMoves(), s.getNumMoves());
    assertEquals(723, MySQLPenteGameStorer.archiveNextMove(d, 33, 3, s.getNumMoves()));
}

public void testPassRowRepeatsPreviousHash() {
    GameData d = game("Tombstone Pente", 180, 722);
    GridState s = GridStateFactory.createGridState(33, d);
    assertEquals(2, s.getNumMoves());
    assertEquals(s.getHash(0), s.getHash(1));
}
```

`toList(int[])` is a local helper.

- [ ] **Step 2: Run to verify it fails**

**Run** `org.pente.game.test.MySQLPenteGameStorerTombstoneTest`.
Expected: compile failure on `archiveNextMove` / `loadMovesSql`.

- [ ] **Step 3: Implement both helpers, use them in `storeGame` and `loadGame`, and comment `loadGames`**

Keep the comment above the write loop accurate ("last move sets next_move to the archive sentinel: 723 for Tombstone, else 361").

- [ ] **Step 4: Run to verify it passes**

**Run** `org.pente.game.test.MySQLPenteGameStorerTombstoneTest`.
Expected: `OK (6 tests)`.
Then, because `testRoundTripKeepsDeadStoneOnPointZero` rebuilds the filter itself and cannot see whether `loadGame` uses the helper:
```bash
grep -n 'loadMovesSql(game)\|next_move != 361\|archiveNextMove(' dsg_src/java/org/pente/game/MySQLPenteGameStorer.java
```
Expected: one line calling `loadMovesSql(game)` inside `loadGame`, one `archiveNextMove(` call inside the `storeGame` write loop (besides its definition), and exactly one `next_move != 361` literal, inside `loadGames` (today `:936`). A `next_move != 361` line inside `loadGame` (today `:778`) means the helper is not wired in.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/game/MySQLPenteGameStorer.java dsg_src/java/org/pente/game/test/MySQLPenteGameStorerTombstoneTest.java
git commit -m "Use 723 as the archive end sentinel for Tombstone games"
```

---

### Task 10: Keep Tombstone out of webdb and the legacy game database

Spec §9.4, D11. These paths decode `next_move` as `0..360` only (`PositionStatsHandler.java:52` `MAX_BOARD_MOVE = 360`, `:310`), and the pentedb client cannot decode Tombstone codes. Each rejects Tombstone ids before touching the database, so the tests need no DB (the existing `webdb/test` classes are DB-backed; do not extend them).

| Path | Where | Behaviour for `isTombstone(game)` |
|---|---|---|
| `PositionStatsHandler.computeStats` | `:165`, right after `resp.nextMoves` is created (`:174`) | return `resp` as is (`totalGames = 0`, empty `nextMoves`) |
| `GameSearchHandler.search(req)` and `search(req, pid)` | first line of both overloads (`:135`, `:148`); `search(req)` bypasses `search(req, pid)` and goes straight to `archiveSearch` | `throw new IllegalArgumentException("variant not supported")`; `handle` already maps it to 400 `bad_request` (`:117-119`) |
| `GameLoadHandler.load(gid)` | `:71-91`, after the private-game check (`:83-85`) | `throw new IllegalArgumentException("variant not supported")` when `GridStateFactory.getGameId(gd.getGame())` is a Tombstone id; `handle` gains `catch (IllegalArgumentException e)` → `JsonHttp.error(response, 400, "bad_request", e.getMessage())` before the generic catch |
| `CollectionHandler.doImport` | `:117-123`, same check as the off-centre variants | per-item error `"variant not supported for import"`, `continue` |
| `CollectionHandler.handleList` | `:178-182` | `JsonHttp.error(response, 400, "bad_request", "variant not supported")` and return |
| `SimpleGameStorerSearchRequestFilterFormat.parse` | after `setGame(getGameId(gameStr))` at `:323` | `throw new Exception("Game not supported in the game database")` inside the existing `try`; the method's catch (`:413-416`) turns it into a `ParseException`, so `MySQLGameStorerSearcher` never queries |

**Files:**
- Modify: the five classes above under `dsg_src/java/org/pente/webdb/` and `dsg_src/java/org/pente/gameDatabase/`
- Test: `dsg_src/java/org/pente/webdb/test/TombstoneWebDbExclusionTest.java` (new)

**Interfaces:**
- Consumes: Plan A `GridStateFactory.isTombstone(int)`; existing handler constructors `PositionStatsHandler(DBHandler, GameVenueStorer)`, `GameSearchHandler(DBHandler, GameStorer, GameVenueStorer)`, `GameLoadHandler(GameStorer)`, `CollectionHandler(MySQLWebDbStorer, DSGPlayerStorer)` (all accept `null` collaborators; nothing is dereferenced before the new checks).
- Produces: no new API.

- [ ] **Step 1: Write the failing test**

```java
public void testPositionStatsEmptyForTombstone() throws Exception {
    PositionStatsRequest req = new PositionStatsRequest();
    req.game = 33;
    req.moves = new int[]{180, 200, 361 + 5};
    PositionStatsResponse r = new PositionStatsHandler(null, null).computeArchive(req);
    assertEquals(0L, r.totalGames);
    assertTrue(r.nextMoves.isEmpty());
}

public void testGameSearchRejectsTombstone() throws Exception {
    for (int g : new int[]{33, 34, 83}) {
        GameSearchRequest req = new GameSearchRequest();
        req.game = g;
        req.moves = new int[]{180};
        try {
            new GameSearchHandler(null, null, null).search(req, -1L);
            fail("game " + g);
        } catch (IllegalArgumentException e) {
            assertEquals("variant not supported", e.getMessage());
        }
        try {
            new GameSearchHandler(null, null, null).search(req);
            fail("archive-only search, game " + g);
        } catch (IllegalArgumentException e) {
            assertEquals("variant not supported", e.getMessage());
        }
    }
}

public void testGameLoadRejectsTombstone() throws Exception {
    final GameData gd = new DefaultGameData();
    gd.setGame("Tombstone Pente");
    gd.addMove(180);
    GameStorer stub = new GameStorer() {          // only loadGame is used
        public boolean gameAlreadyStored(long id) { return true; }
        public boolean gameAlreadyStored(GameData d) { return true; }
        public void storeGame(GameData d) {}
        public GameData loadGame(long id, GameData d) { return gd; }
        public void destroy() {}
    };
    try {
        new GameLoadHandler(stub).load(42L);
        fail();
    } catch (IllegalArgumentException e) {
        assertEquals("variant not supported", e.getMessage());
    }
}

public void testImportRejectsTombstonePerItem() throws Exception {
    WebDbGameData g = new WebDbGameData();
    g.game = 33;
    g.moves = new int[]{180, 200};
    ImportRequest req = new ImportRequest();
    req.games = new ArrayList<>(Arrays.asList(g));
    ImportResponse r = new CollectionHandler(null, null).doImport(1L, req);
    assertEquals(0, r.imported);
    assertEquals(1, r.errors.size());
    assertEquals("variant not supported for import", r.errors.get(0).message);
}

public void testLegacyGameDatabaseRejectsTombstone() {
    SimpleGameStorerSearchRequestFilterFormat f = new SimpleGameStorerSearchRequestFilterFormat();
    try {
        f.parse(new SimpleGameStorerSearchRequestFilterData(), new StringBuffer("game=Tombstone+Pente"));
        fail();
    } catch (ParseException expected) {
    }
}

public void testLegacyGameDatabaseStillAcceptsPente() throws Exception {
    new SimpleGameStorerSearchRequestFilterFormat().parse(
        new SimpleGameStorerSearchRequestFilterData(), new StringBuffer("game=Pente"));
}
```

If `GameStorer` declares more abstract methods than the five in `GameStorer.java:35-60`, add trivial stubs; if `"game=Pente"` alone does not parse today, add the minimum parameters the parser needs to both buffers, so the two legacy tests differ only in the game name.

- [ ] **Step 2: Run to verify it fails**

**Run** `org.pente.webdb.test.TombstoneWebDbExclusionTest`.
Expected: the five rejection tests fail; `testLegacyGameDatabaseStillAcceptsPente` passes.

- [ ] **Step 3: Implement the six checks in the table**

- [ ] **Step 4: Run to verify it passes**

**Run** `org.pente.webdb.test.TombstoneWebDbExclusionTest`.
Expected: `OK (6 tests)`.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/webdb/ dsg_src/java/org/pente/gameDatabase/SimpleGameStorerSearchRequestFilterFormat.java
git commit -m "Keep Tombstone games out of webdb and the legacy game database"
```

---

### Task 11: Hide the Experimental server from the mobile apps

Spec §7.5. The apps read four server/room lists, all built from `Resources.getServerData()` or `WhosOnline.getPlayers(...)` (rooms are named after servers, `WhosOnline.java:42`, `:126`):

| Endpoint | Builder | App caller |
|---|---|---|
| `mobile/json/liveServers.jsp:18-19` | `LiveServersResponse.build` (`LiveServersResponse.java:57-70`) | Android `LobbyActivity.java:194` |
| `mobile/liveServers.jsp:28-51` | inline loop | iOS `LobbyViewController.swift:92-94` |
| `mobile/json/whosonlineandlive.jsp:18-32` | `WhosonlineAndLiveResponse.build` (`WhosonlineAndLiveResponse.java:51-57`) | Android `OkHttpPenteApi.java:124` |
| `mobile/whosonlineandlive.jsp:41-51` | inline loop | iOS `GamesTableViewController.m:4104-4108` |

`/gameServer/activeServers` (`ActiveServersServlet.java:38-55`) is **not** filtered: it is the React room's server list (`react_live_game_room/src/Pages/Servers.js:40`), and no current app calls it. `mobile/whosonline.jsp`, `mobile/json/whosonline.jsp` (only the `"Mobile"` room) and `mobile/index.jsp`, `mobile/json/index.jsp` (a player count, no server list) are not changed.

App connections are told apart by transport: both apps open a raw TLS socket to the server's port (Android `penteLive-Android/app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java:130-132`, iOS `penteLive-iOS/test1/PenteLiveSocket.swift:45`), which `Server`'s listener hands to `addPlayerSocket` (`Server.java:153-165`, `:230-249`); the React room uses the websocket path (`Server.addPlayerWebSocketSession`, `:210-227`). So `addPlayerSocket` refuses native sockets on Experimental. The listener stays open: the `Server` constructor returns early, before the main room and AI controller are built, when it cannot listen (`:116-123`), so not listening is not an option.

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/mobile/LiveServersResponse.java:57-70`
- Modify: `dsg_src/java/org/pente/gameServer/mobile/WhosonlineAndLiveResponse.java:51-57`
- Modify: `dsg_src/httpdocs/gameServer/mobile/liveServers.jsp:28` (first statement in the loop)
- Modify: `dsg_src/httpdocs/gameServer/mobile/whosonlineandlive.jsp:41` (first statement in the loop)
- Modify: `dsg_src/java/org/pente/gameServer/server/Server.java:230-234` (start of `addPlayerSocket`)
- Test: `dsg_src/java/org/pente/gameServer/mobile/test/MobileServerListFilterTest.java` (new)

**Interfaces:**
- Consumes: Task 7 `ExperimentalServer.isExperimental(ServerData)`, `isExperimentalRoom(String)`, `acceptsNativeSocket(ServerData)`.
- Produces: no new API. Behaviour:
  - `LiveServersResponse.build`: first statement in the `for` loop, `if (ExperimentalServer.isExperimental(data)) { continue; }`. Update the `@param servers` comment (`:54`) to say the Experimental server is skipped.
  - `WhosonlineAndLiveResponse.build`: first statement in the loop, `if (ExperimentalServer.isExperimentalRoom(room.getName())) { continue; }`. Update the class comment (`:11-14`).
  - `mobile/liveServers.jsp`: `if (ExperimentalServer.isExperimental(data)) { continue; }` as the loop's first statement (`org.pente.gameServer.core.*` is already imported, `:2`).
  - `mobile/whosonlineandlive.jsp`: `if (ExperimentalServer.isExperimentalRoom(room.getName())) { continue; }` as the loop's first statement (same import, `:2`).
  - `Server.addPlayerSocket`: first statement, `if (!ExperimentalServer.acceptsNativeSocket(serverData)) { log4j.info("Refused native socket on " + name + " from " + socket.getInetAddress()); try { socket.close(); } catch (java.io.IOException e) { /* nothing to do */ } return; }` (fully qualified: `Server.java` imports `java.net.*` but not `java.io`, `:23`).

- [ ] **Step 1: Write the failing test**

`MobileServerListFilterTest` (JUnit 3, same shape as `IndexResponseGameIdTest`):

```java
private static ServerData server(int id, String name) {
    ServerData d = new ServerData();
    d.setServerId(id);
    d.setName(name);
    return d;
}

public void testLiveServersSkipsExperimental() {
    List<LiveServersResponse.ServerEntry> out = LiveServersResponse.build(
        Arrays.asList(server(1, "Main Room"), server(47, "Experimental"), server(0, "Arena Server")),
        Collections.<WhosOnlineRoom>emptyList());
    assertEquals(Arrays.asList("Main Room", "Arena Server"),
        out.stream().map(e -> e.name).collect(Collectors.toList()));
}

public void testLiveServersKeepsAForeignRowAtId47() {
    List<LiveServersResponse.ServerEntry> out = LiveServersResponse.build(
        Arrays.asList(server(47, "Someone else's room")), Collections.<WhosOnlineRoom>emptyList());
    assertEquals(1, out.size());
}

public void testWhosOnlineSkipsTheExperimentalRoom() {
    List<WhosonlineAndLiveResponse.RoomEntry> out = WhosonlineAndLiveResponse.build(Arrays.asList(
        new WhosOnlineRoom("Main Room", new ArrayList<DSGPlayerData>()),
        new WhosOnlineRoom("Experimental", new ArrayList<DSGPlayerData>()),
        new WhosOnlineRoom("Website", new ArrayList<DSGPlayerData>())));
    assertEquals(Arrays.asList("Main Room", "Website"),
        out.stream().map(r -> r.name).collect(Collectors.toList()));
}
```

- [ ] **Step 2: Run to verify it fails**

**Run** `org.pente.gameServer.mobile.test.MobileServerListFilterTest`.
Expected: `testLiveServersSkipsExperimental` and `testWhosOnlineSkipsTheExperimentalRoom` fail (the Experimental entry is present); `testLiveServersKeepsAForeignRowAtId47` passes.

- [ ] **Step 3: Implement the two `build` filters, the two JSP filters and the socket refusal**

- [ ] **Step 4: Run to verify it passes**

**Run** `org.pente.gameServer.mobile.test.MobileServerListFilterTest`, then `org.pente.gameServer.mobile.test.IndexResponseGameIdTest`.
Expected: `OK (3 tests)`, then the existing count `OK`.
Then:
```bash
grep -n "ExperimentalServer" dsg_src/java/org/pente/gameServer/mobile/*.java dsg_src/httpdocs/gameServer/mobile/liveServers.jsp dsg_src/httpdocs/gameServer/mobile/whosonlineandlive.jsp dsg_src/java/org/pente/gameServer/server/Server.java dsg_src/java/org/pente/gameServer/client/web/ActiveServersServlet.java
```
Expected: one hit in each of the four mobile files and in `Server.java`, none in `ActiveServersServlet.java`.

- [ ] **Step 5: Local check (after the Task 12 restart, or restart now by the local smoke-test route)**

In a browser logged in to the local site, open `https://localhost/gameServer/mobile/json/liveServers.jsp`, `https://localhost/gameServer/mobile/liveServers.jsp?iPhone`, `https://localhost/gameServer/mobile/json/whosonlineandlive.jsp` and `https://localhost/gameServer/mobile/whosonlineandlive.jsp` (enter the Experimental room in the React client first, so its room is non-empty). Expected: no `Experimental` entry anywhere. Open `https://localhost/gameServer/activeServers`: it lists `16004 Experimental (n)`. Then open a raw TLS connection to the Experimental port and to the Main Room port:
```bash
openssl s_client -connect localhost:16004 </dev/null > /dev/null 2>&1
docker logs penteorg-pente.org-1 2>&1 | grep "Refused native socket on Experimental" | tail -1
```
Expected: one `Refused native socket on Experimental from ...` line. The same `openssl` call to `localhost:16000` logs no refusal.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/mobile/LiveServersResponse.java dsg_src/java/org/pente/gameServer/mobile/WhosonlineAndLiveResponse.java dsg_src/httpdocs/gameServer/mobile/liveServers.jsp dsg_src/httpdocs/gameServer/mobile/whosonlineandlive.jsp dsg_src/java/org/pente/gameServer/server/Server.java dsg_src/java/org/pente/gameServer/mobile/test/MobileServerListFilterTest.java
git commit -m "Hide the Experimental server from the mobile apps and refuse their sockets on it"
```

---

### Task 12: Test target, regression run, local smoke test, tidy

**Files:**
- Modify: `build.xml` (`test` target, `:48-192`): add one `<java>` block per new class: `DSGMoveTableEventLogTest`, `TombstoneSkipDeadWireTest` (with the `--add-opens` `jvmarg`, as `ArenaJoinRequestWireTest` at `:69-73`), `ServerTableTombstoneTest`, `org.pente.gameServer.core.test.ExperimentalServerTest`, `org.pente.game.test.TombstoneBootRegistrationTest`, `org.pente.game.test.MySQLPenteGameStorerTombstoneTest`, `org.pente.webdb.test.TombstoneWebDbExclusionTest`, `org.pente.gameServer.mobile.test.MobileServerListFilterTest`.

- [ ] **Step 1: Add the blocks and run the whole target**

Run:
```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
rsync -urtd --exclude-from exclude_compile.txt dsg_src/java/ deploy/
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ant test > /private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad/planB-ant-test.log 2>&1; echo "exit $?"
grep -E "^OK|FAILURES|Tests run" /private/tmp/claude-501/-Users-waliedothman-mariposa-coding-pente-org-project-pente-org/7ad7f2dc-9a60-42a5-97e6-0b614d6297c7/scratchpad/planB-ant-test.log | sort | uniq -c
```
Expected: `exit 0`, only `OK (...)` lines, no `FAILURES`.

- [ ] **Step 2: Re-run the Plan A and venue suites this plan sits on**

**Run** each of `org.pente.game.test.AllGamesTest`, `org.pente.game.test.RenjuFactoryTest`, `org.pente.game.test.GridStateFactorySingleGameSetTest`, `org.pente.game.test.MySQLGameVenueStorerTbLookupTest`, `org.pente.game.test.PGNRenjuPassTest`, `org.pente.game.test.PenteStateTest`, plus Plan A's `TombstoneStateTest`, `TombstoneCodesTest`, `TombstoneZobristTest`, `TombstoneFactoryTest`, `PGNTombstoneTest`, `TombstoneUndoReplayTest` (all in `org.pente.game.test`; the last two pin the `undoTurn`, replay and PGN behaviour Tasks 5 and 9 rely on).
Expected: every class `OK`.

- [ ] **Step 3: Local smoke test (never `sync_gameServer.sh`)**

Follow the local smoke-test route (compile, bind-mount the classes, restart `penteorg-pente.org-1`; see the "Local smoke-test server classes" memory). In two browsers on the **Experimental** server (pick it in the React room's server list), with a React build that has Plan D (or the wire frames from spec §8.1 sent by hand), check that a new table opens as Tombstone Pente, then play: own+dead, own+skip, pass+dead, pass+skip, a capture followed by a dead stone on the vacated point, an undo of each turn length, a rejoin after own+skip, and six empty turns (draw). Then reload the archived game from the archive viewer and check that a dead stone on A19 (code `361`) is present. On Experimental, try switching a table to Pente (hand-sent `dsgChangeStateTableEvent` with `game: 1`) and check the client gets `dsgChangeStateTableErrorEvent` and stays on Tombstone. On the Main Room (1) and the KOTH server (45), try switching a table to Tombstone (`game: 33`) and check the same rejection. Then, as the player whose last table setup was Tombstone on Experimental, create a new table on the Main Room and check it opens as Pente 10/5, not Tombstone. Re-run the Task 8 Step 5 query and the Task 11 Step 5 checks. Check `docker logs penteorg-pente.org-1` for `ArrayIndexOutOfBoundsException` and `NullPointerException`: expected none.

- [ ] **Step 4: Tidy and commit**

Run:
```bash
git status --porcelain | grep '^??'
```
Expected: no files this plan created besides committed ones (pre-existing untracked files such as `CLAUDE.md` stay untouched). Remove any scratch output.

```bash
git add build.xml
git commit -m "Run the Tombstone server tests in the ant test target"
```

## Spec coverage (Plan B scope, spec §16)

| Spec item | Task |
|---|---|
| §8.1 events, wrapper, dispatch, error code 26 | 2, 4 |
| §8.2 move validation (phase-aware via Plan A), skip checks | 3, 4 |
| §8.3 timers, `moveTimes` invariant, skip tail | 3, 4 |
| §8.4 whole-turn undo | 5 |
| §8.5 rejoin silent skip | 6 |
| §8.6 logging | 1 |
| §7.3 server-offering rule, both directions (table switch, table creation from a saved preference or the default, Arena create) | 7 |
| §7.5 `ExperimentalServer` identity and predicates | 7 |
| §7.2 boot registration and offerings, Experimental server creation (D14) | 8 |
| §7.3 KOTH summary: no KOTH rows, 45 never offered Tombstone | 8 (rows, offerings); TB KOTH reject is Plan C Task 4 |
| §7.5 mobile list filters and native-socket refusal | 11 |
| §9.1-9.2 archive sentinel and filter | 9 |
| §9.4 webdb and legacy exclusions | 10 |
| §8.7 unchanged paths (auto-centre, tournament rule, AI) | no change; the centre is exercised in every Task 3-7 test |
