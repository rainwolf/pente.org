# Arena Persistent Join Requests (server) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make arena join requests persistent and server-owned: one synchronized `ArenaJoinRequestRegistry` holds every request, claim and blocked set, and pushes full snapshots (`dsgArenaJoinRequestsEvent`, `dsgArenaMyRequestsEvent`) and reason-coded notices (`dsgArenaRequestEndedEvent`) so clients never run expiry timers.

**Architecture:** `ArenaServer` owns one `ArenaJoinRequestRegistry`. Each `ArenaServerTable` forwards request/withdraw/accept/decline to it from its own pump, and publishes its own state (owner, players, open) into it on every join, exit and game-state change. The registry decides everything under one lock and sends events through an injected `Notifier` (production: `DSGEventToPlayerRouter.routeEvent`; tests: an in-memory log), so all rules are unit-testable without sockets. Accept only *claims*; the queued `DSGJoinTableEvent` is re-checked when it lands in `ArenaServerTable.handleJoin`.

**Tech Stack:** Java 21 (Tomcat webapp), Ant (`build.xml`), JUnit 3.7 `TestCase` run by `junit.textui.TestRunner`, Gson (wire JSON), log4j 1.2 `Category`. E2E: Node 26 built-ins (`node:tls`) against the local docker stack.

**Spec:** `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docs/superpowers/specs/2026-10-10-arena-persistent-join-requests-design.md` (approved). Executors read the spec and this plan together. This plan covers only the `pente.org` server part; the React, Android and iOS parts have their own plans and use the wire protocol below as the contract.

**Base:** branch `arena-persistent-join-requests` at commit `1cfe15a`. Every `file:line` below refers to that commit; earlier tasks in this plan shift lines in the same file, so always match on the quoted code, not the number.

## Global Constraints

- Build/test toolchain: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/`, `ant` at `/opt/homebrew/bin/ant`. JUnit is `dsg_src/lib/junit-3.7.jar`: there is **no `assertFalse`** (write `assertTrue(!x)`), tests extend `junit.framework.TestCase` with a `(String name)` constructor, like `server/test/ServerTableDrawScoringTest.java`.
- Run one test class (production sources must be synced into `deploy/` first, exactly as `./justCompile` does):
  `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=<fully.qualified.TestClass>`
  Success prints `OK (N tests)` then `BUILD SUCCESSFUL`. A failure prints `FAILURES!!!` (or a `javac` error) then `BUILD FAILED`.
- Envelope unchanged: `{"<key>": {fields}}`, lowercase `dsg…` keys. Keys and fields, verbatim from the spec:
  - C→S, existing, shape unchanged: `dsgArenaRequestJoinTableEvent {table}`, `dsgArenaAcceptTableJoinEvent {table, playerToAccept}`, `dsgArenaRejectTableJoinEvent {table, playerToReject}`.
  - C→S, new: `dsgArenaWithdrawJoinRequestEvent {table}`.
  - S→C, new: `dsgArenaJoinRequestsEvent {table, requests: [{player, seq}]}` → owner; `dsgArenaMyRequestsEvent {tables: [int]}` → requester; `dsgArenaRequestEndedEvent {table, player, owner, reason}`.
  - Every event also carries the existing `time` field from `AbstractDSGEvent`; Gson omits null fields.
- Reasons, verbatim: `DECLINED`, `TABLE_FULL`, `TABLE_CLOSED`, `DUPLICATE`, `BLOCKED`, `NOT_AVAILABLE`, `GUEST_RATED`, `BOOTED`, `NO_LONGER_AVAILABLE`.
- R2 boot check, verbatim: "the requester is not currently booted from that table (`ServerTable.bootTimes` entry still in its 5-minute window). `ArenaServerTable` checks this on its own pump before calling the registry and refuses with `BOOTED`. A boot is table state, not request memory, so a reopen (R6b) does not clear it."
- "No longer sent S→C: `dsgArenaRequestJoinTableEvent` and `dsgArenaRejectTableJoinEvent`."
- "Old app builds are not supported." "There is no server alias for the capital-key decline."
- R10: "Every mutation runs in a `synchronized` method"; "The registry never takes the `tables` lock"; "Outgoing events are enqueued while the lock is held"; "Payloads are immutable copies". Lock order is always `Server.tables` → an arena table's `publishLock` (Task 8) → registry → router → player writer queue, never the reverse.
- Known edges, accepted and to be copied into the client plans (no server change):
  - On login, `dsgArenaMyRequestsEvent` can reach the client before its own `dsgJoinMainRoomEvent` echo and the table list: `ArenaServer.routeEventToMainRoom` sends it right after `super` only queued the join on the main-room pump. On a fresh login it is normally `{tables: []}` (R7 ended every request when the player left), so a client that resets lobby state on main-room join loses nothing; clients must still apply whichever `dsgArenaMyRequestsEvent` is newest, whatever its arrival relative to the main-room join, and must not treat it as "lobby loaded".
  - A fast disconnect and reconnect: each table's pump calls R7 for the old `DSGExitMainRoomEvent` (`ServerMainRoom.handleExit` → `routeEventToAllTables`), so a lagging pump can run it after the new session's login answer or new requests, and R7 then removes them. The player still gets a matching `dsgArenaMyRequestsEvent`, so the client stays consistent; the requests are simply gone, as for any disconnect (no grace period is a spec non-goal).
  - `startGame()` also runs on the `pressPlayTimer` thread (`ArenaServerTable.java:140-147`), off the pump. Task 7 makes the publish read `playersInTable` through one atomic copy, and Task 8 serializes every publish and `destroy()` on a per-table `publishLock`, so a timer publish cannot throw into `startGame()`, cannot apply a view older than an earlier publish, and cannot recreate a removed table.
- `seq` is "server-assigned at creation, increasing, and never reused".
- Non-goals (do not do): blocking a plain `dsgJoinTableEvent` that bypasses arena requests; freezing the lobby table list; a grace period for reconnecting requesters; supporting old app builds; removing `ArenaServerMainRoom` or the dead `closeTableTimer` code.
- Commits: `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "..."`, with no co-author and no generated-by lines. Stage explicit paths only: the working tree already has unrelated `M docker-compose.yml`, `M docker-compose-replica.yml` and `?? CLAUDE.md`, which must never be staged.
- Building writes `deploy/` and `deployClasses/` (both gitignored). `deployClasses/org` is mounted into the local container (`docker-compose.yml:41`); that stack is the isolated dev backend. Nothing in this plan deploys to production.
- Scratch files (E2E driver, logs) live in `/private/tmp/claude-501/arena-plans/`, never inside a repo.

## Review Focus

1. **Owner double-taps Accept on two different requesters before the first join lands.** Expect exactly one claim and one queued join; the second accept is answered with a snapshot (R11) and that requester later gets `TABLE_FULL`. Pinned by `testOwnerDoubleTapOnTwoRequestersClaimsOnlyTheFirst` (Task 4).
2. **Accept or decline naming a player who is not pending** (stale row, or a malformed frame with no `playerToAccept`). Expect no exception and nobody blocked; accept answers `NO_LONGER_AVAILABLE` plus a snapshot, decline answers a snapshot. Pinned by `testAcceptOfPlayerWhoIsNotPendingIsNoLongerAvailable` (Task 4) and `testDeclineOfPlayerWhoIsNotPendingOnlyAnswersOwner` (Task 3).
3. **A destroyed table object publishes again** (its `pressPlayTimer` fires `startGame()` on a `java.util.Timer` thread after `destroy()`) while its table number has been reused. Expect the stale publish to be ignored so the new table keeps its own state. Pinned by `testDestroyedTableNeverRepublishesIntoReusedNumber` (Task 8).
4. **Request, withdraw, accept or decline aimed at a table that is gone, or whose creator join has not been processed yet.** Expect an R11 answer and `NOT_AVAILABLE` with `owner: ""`, never an NPE or silence. Pinned by `testUnknownTableRefusesWithEmptyOwner` (Task 3) and the `answerUnroutable` tests (Task 5), and E2E scenario "events for a gone table are answered" (Task 9).
5. **`seq` across a reopen.** A requester who asks again after the table filled and reopened gets a higher `seq` than requests made meanwhile, so the owner's list stays in arrival order and numbers are never reused. Pinned by `testSeqKeepsGrowingAcrossReopen` (Task 4).
6. **A claimed join that `admitJoin` lets through but `ServerTable.handleJoin` still turns away** (a boot recorded after the request was made, `ServerTable.java:447-453`, or the join throws; a boot recorded before the request is refused up front, Review Focus 8). Expect the claim released and the owner told `NO_LONGER_AVAILABLE` plus a snapshot, so neither the table nor the requester stays stuck on a claim. Pinned by `testClaimedJoinTheTableTurnsAwayReleasesClaimAndTellsOwner` (Task 4) and `testClaimedJoinRefusedByTheTableReleasesTheClaim` (Task 7).
7. **The table fills by another path while a claimed join is still queued.** The claim ends with `TABLE_FULL` to the requester; expect the queued join, when it lands, to be refused instead of seating the requester as a spectator. Pinned by `testJoinQueuedByAClaimThatTheTableFullReleasedIsRefused` (Task 4).
8. **A player the owner booted requests that table again within 5 minutes**, including after the boot itself reopened the table (R6b). Expect `BOOTED` with `owner` set, the R11 `dsgArenaMyRequestsEvent` answer, no request created and no snapshot to the owner; once the 5 minutes pass the request is accepted. The check reads the table's own `bootTimes` on its pump, before the registry. Pinned by `testBootedRequesterIsRefusedWithOwnerNamed`, `testBootRefusalStillAnswersMyRequests`, `testExpiredBootNoLongerRefuses` (Task 6), `testBootThatReopensTheTableStillRefusesTheBootedPlayer` (Task 8) and E2E scenario "booted player is refused after the boot reopens the table" (Task 9).

---

## File Structure

All Java paths are under `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/org/pente/gameServer/`.

| File | Status | Responsibility |
|---|---|---|
| `event/DSGArenaWithdrawJoinRequestEvent.java` | create | C→S withdraw (a `DSGTableEvent`, so `ServerPlayer` routes it) |
| `event/DSGArenaJoinRequestsEvent.java` | create | S→C owner snapshot, with nested `Request {player, seq}` |
| `event/DSGArenaMyRequestsEvent.java` | create | S→C requester's pending tables |
| `event/DSGArenaRequestEndedEvent.java` | create | S→C notice with the nine reason constants |
| `event/DSGEventWrapper.java` | modify `:80-89`, `:677-683` | four new wrapper fields + accessors (field name = wire key) |
| `server/ServerTable.java` | modify `:3919-3929` | no-op `handleArenaWithdrawJoin` |
| `server/SynchronizedServerTable.java` | modify `:179-184` | dispatch case for the withdraw event |
| `server/ArenaJoinRequestRegistry.java` | create | all request state and rules R1-R11; `Notifier` seam |
| `server/ArenaServer.java` | modify `:22-62` | owns the registry; main-room-join hook; answers events for gone tables |
| `server/ArenaServerTable.java` | modify `:43-44`, `:46-98`, `:155-171`, `:219-276` | forwards actions, publishes state, R6/R7/R8 hooks |
| `server/test/ArenaJoinRequestWireTest.java` | create | JSON keys/fields round trip, withdraw decode, dispatch wiring |
| `server/test/ArenaEventLog.java` | create | test double: records events per recipient (`Notifier` + router) |
| `server/test/ArenaJoinRequestRegistryTest.java` | create | registry rules without tables |
| `server/test/ArenaServerTableJoinRequestTest.java` | create | table hooks through real `ServerTable` code, side effects stubbed |
| `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml` | modify `:41-82` | wire the three new test classes into target `test` |
| `/private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs` | create (scratch, not committed) | protocol-level E2E driver |

---

### Task 1: New arena events and their wire keys

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/event/DSGArenaWithdrawJoinRequestEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGArenaJoinRequestsEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGArenaMyRequestsEvent.java`
- Create: `dsg_src/java/org/pente/gameServer/event/DSGArenaRequestEndedEvent.java`
- Modify: `dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java:80-89` (fields) and `:677-683` (accessors)
- Modify: `build.xml:78-82` (append a runner after `MMAIPlayerInitTest`)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestWireTest.java`

**Interfaces:**
- Consumes: `AbstractDSGTableEvent(String player, int table)`, `AbstractDSGEvent`, `DSGEventWrapper(Object)`, `DSGEventWrapper.getJSON()`, `DSGEventWrapper.getEncodedEvent()`.
- Produces (package `org.pente.gameServer.event`):
  - `DSGArenaWithdrawJoinRequestEvent()` and `DSGArenaWithdrawJoinRequestEvent(String player, int table)`
  - `DSGArenaJoinRequestsEvent(int table, List<DSGArenaJoinRequestsEvent.Request> requests)`; `List<Request> getRequests()`; `int getTable()`; nested `static class Request` with `Request(String player, long seq)`, `String getPlayer()`, `long getSeq()`
  - `DSGArenaMyRequestsEvent(int[] tables)`; `int[] getTables()`
  - `DSGArenaRequestEndedEvent(String player, int table, String owner, String reason)`; `getPlayer()`, `getTable()`, `String getOwner()`, `String getReason()`; constants `DECLINED`, `TABLE_FULL`, `TABLE_CLOSED`, `DUPLICATE`, `BLOCKED`, `NOT_AVAILABLE`, `GUEST_RATED`, `BOOTED`, `NO_LONGER_AVAILABLE` (each equal to its own name)

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestWireTest.java`:

```java
package org.pente.gameServer.server.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.event.*;

import java.awt.Color;
import java.util.*;

/**
 * The arena join-request events on the wire (spec 2026-10-10, Wire protocol):
 * each one is wrapped under its exact lowercase key and carries exactly the
 * spec's fields (plus the envelope-wide "time").
 */
public class ArenaJoinRequestWireTest extends TestCase {

    public ArenaJoinRequestWireTest(String name) {
        super(name);
    }

    // ---- S->C ----------------------------------------------------------

    public void testJoinRequestsEventKeyAndFields() {
        DSGArenaJoinRequestsEvent e = new DSGArenaJoinRequestsEvent(7, Arrays.asList(
                new DSGArenaJoinRequestsEvent.Request("bob", 4),
                new DSGArenaJoinRequestsEvent.Request("dave", 9)));

        JsonObject body = body(e, "dsgArenaJoinRequestsEvent");

        assertKeys(body, "table", "requests", "time");
        assertEquals(7, body.get("table").getAsInt());
        JsonArray requests = body.getAsJsonArray("requests");
        assertEquals(2, requests.size());
        JsonObject first = requests.get(0).getAsJsonObject();
        assertKeys(first, "player", "seq");
        assertEquals("bob", first.get("player").getAsString());
        assertEquals(4L, first.get("seq").getAsLong());
        assertEquals("dave", requests.get(1).getAsJsonObject().get("player").getAsString());
        assertEquals(9L, requests.get(1).getAsJsonObject().get("seq").getAsLong());
    }

    public void testEmptyJoinRequestsEventStillCarriesRequestsArray() {
        JsonObject body = body(new DSGArenaJoinRequestsEvent(3,
                Collections.<DSGArenaJoinRequestsEvent.Request>emptyList()), "dsgArenaJoinRequestsEvent");

        assertKeys(body, "table", "requests", "time");
        assertEquals(0, body.getAsJsonArray("requests").size());
    }

    public void testJoinRequestsEventCopiesItsList() {
        List<DSGArenaJoinRequestsEvent.Request> list = new ArrayList<>();
        list.add(new DSGArenaJoinRequestsEvent.Request("bob", 1));
        DSGArenaJoinRequestsEvent e = new DSGArenaJoinRequestsEvent(3, list);
        list.clear();

        assertEquals(1, e.getRequests().size());
    }

    public void testMyRequestsEventKeyAndFields() {
        int[] tables = {2, 5};
        DSGArenaMyRequestsEvent e = new DSGArenaMyRequestsEvent(tables);
        tables[0] = 99;   // the event keeps its own copy

        JsonObject body = body(e, "dsgArenaMyRequestsEvent");

        assertKeys(body, "tables", "time");
        JsonArray out = body.getAsJsonArray("tables");
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).getAsInt());
        assertEquals(5, out.get(1).getAsInt());
    }

    public void testRequestEndedEventKeyAndFields() {
        JsonObject body = body(new DSGArenaRequestEndedEvent(
                "bob", 7, "alice", DSGArenaRequestEndedEvent.DECLINED), "dsgArenaRequestEndedEvent");

        assertKeys(body, "table", "player", "owner", "reason", "time");
        assertEquals(7, body.get("table").getAsInt());
        assertEquals("bob", body.get("player").getAsString());
        assertEquals("alice", body.get("owner").getAsString());
        assertEquals("DECLINED", body.get("reason").getAsString());
    }

    public void testReasonCodesAreTheSpecStrings() {
        assertEquals("DECLINED", DSGArenaRequestEndedEvent.DECLINED);
        assertEquals("TABLE_FULL", DSGArenaRequestEndedEvent.TABLE_FULL);
        assertEquals("TABLE_CLOSED", DSGArenaRequestEndedEvent.TABLE_CLOSED);
        assertEquals("DUPLICATE", DSGArenaRequestEndedEvent.DUPLICATE);
        assertEquals("BLOCKED", DSGArenaRequestEndedEvent.BLOCKED);
        assertEquals("NOT_AVAILABLE", DSGArenaRequestEndedEvent.NOT_AVAILABLE);
        assertEquals("GUEST_RATED", DSGArenaRequestEndedEvent.GUEST_RATED);
        assertEquals("BOOTED", DSGArenaRequestEndedEvent.BOOTED);
        assertEquals("NO_LONGER_AVAILABLE", DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE);
    }

    // ---- C->S ----------------------------------------------------------

    public void testWithdrawEventDecodesFromClientFrame() {
        DSGEventWrapper w = serverGson().fromJson(
                "{\"dsgArenaWithdrawJoinRequestEvent\":{\"table\":7,\"time\":0}}", DSGEventWrapper.class);

        Object decoded = w.getEncodedEvent();

        assertTrue(decoded instanceof DSGArenaWithdrawJoinRequestEvent);
        assertTrue(decoded instanceof DSGTableEvent);
        assertEquals(7, ((DSGArenaWithdrawJoinRequestEvent) decoded).getTable());
        assertNull(((DSGArenaWithdrawJoinRequestEvent) decoded).getPlayer());
    }

    public void testWithdrawEventEncodesWithLowercaseKey() {
        JsonObject body = body(new DSGArenaWithdrawJoinRequestEvent("bob", 7), "dsgArenaWithdrawJoinRequestEvent");

        assertKeys(body, "table", "player", "time");
        assertEquals(7, body.get("table").getAsInt());
    }

    public void testLowercaseDeclineDecodesAndCapitalKeyIsNotAliased() {
        DSGEventWrapper lower = serverGson().fromJson(
                "{\"dsgArenaRejectTableJoinEvent\":{\"table\":7,\"playerToReject\":\"bob\"}}", DSGEventWrapper.class);
        DSGEventWrapper capital = serverGson().fromJson(
                "{\"DSGArenaRejectTableJoinEvent\":{\"table\":7,\"playerToReject\":\"bob\"}}", DSGEventWrapper.class);

        assertTrue(lower.getEncodedEvent() instanceof DSGArenaRejectTableJoinEvent);
        assertNull(capital.getEncodedEvent());
    }

    // ---- helpers -------------------------------------------------------

    /** Same Gson setup the server uses to read frames (WebSocketDSGEventHandler.readMessage). */
    private static Gson serverGson() {
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(Color.class, new DSGColorAdapter());
        b.registerTypeAdapter(DSGPlayerData.class, new DSGPlayerDataAdapter());
        b.registerTypeAdapter(DSGPlayerGameData.class, new DSGPlayerGameDataAdapter());
        return b.create();
    }

    /** Encodes like the server's writers do and returns the body under the one wrapper key. */
    private static JsonObject body(Object event, String key) {
        String json = new DSGEventWrapper(event).getJSON();
        JsonObject root = new Gson().fromJson(json, JsonObject.class);
        assertEquals("exactly one wrapper key in " + json, 1, root.entrySet().size());
        assertTrue("wrapper key " + key + " in " + json, root.has(key));
        return root.getAsJsonObject(key);
    }

    private static void assertKeys(JsonObject o, String... expected) {
        Set<String> actual = new TreeSet<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            actual.add(e.getKey());
        }
        assertEquals(new TreeSet<>(Arrays.asList(expected)), actual);
    }
}
```

- [ ] **Step 2: Wire the test into `build.xml` target `test`**

In `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml`, after the `MMAIPlayerInitTest` block (`:78-81`) and before `</target>` (`:82`), insert:

```xml
        <java classname="junit.textui.TestRunner" fork="true" failonerror="true">
            <classpath refid="test-classpath"/>
            <arg value="org.pente.gameServer.server.test.ArenaJoinRequestWireTest"/>
        </java>
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestWireTest`
Expected: `BUILD FAILED` with `error: cannot find symbol` for `DSGArenaJoinRequestsEvent` (and the other new classes).

- [ ] **Step 4: Create the four event classes**

`dsg_src/java/org/pente/gameServer/event/DSGArenaWithdrawJoinRequestEvent.java`:

```java
package org.pente.gameServer.event;

/** Requester takes back their pending request to a table (spec R3). */
public class DSGArenaWithdrawJoinRequestEvent extends AbstractDSGTableEvent {

    public DSGArenaWithdrawJoinRequestEvent() {
        super();
    }

    public DSGArenaWithdrawJoinRequestEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "Arena withdraw join request " + super.toString();
    }
}
```

`dsg_src/java/org/pente/gameServer/event/DSGArenaJoinRequestsEvent.java`:

```java
package org.pente.gameServer.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to owner: the full pending request list for one arena table,
 * ordered by seq ascending. The list is copied on construction because Gson
 * serializes the event later, on the player's writer thread.
 */
public class DSGArenaJoinRequestsEvent extends AbstractDSGTableEvent {

    public static class Request implements Serializable {

        private String player;
        private long seq;

        public Request(String player, long seq) {
            this.player = player;
            this.seq = seq;
        }

        public String getPlayer() {
            return player;
        }

        public long getSeq() {
            return seq;
        }
    }

    private List<Request> requests;

    public DSGArenaJoinRequestsEvent(int table, List<Request> requests) {
        super(null, table);
        this.requests = new ArrayList<>(requests);
    }

    public List<Request> getRequests() {
        return Collections.unmodifiableList(requests);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Arena join requests table " + getTable() + ":");
        for (Request r : requests) {
            sb.append(' ').append(r.player).append('#').append(r.seq);
        }
        return sb.toString();
    }
}
```

`dsg_src/java/org/pente/gameServer/event/DSGArenaMyRequestsEvent.java`:

```java
package org.pente.gameServer.event;

import java.util.Arrays;

/** Server to requester: every table they have a pending request at. */
public class DSGArenaMyRequestsEvent extends AbstractDSGEvent {

    private int[] tables;

    public DSGArenaMyRequestsEvent(int[] tables) {
        this.tables = tables.clone();
    }

    public int[] getTables() {
        return tables.clone();
    }

    public String toString() {
        return "Arena my requests " + Arrays.toString(tables);
    }
}
```

`dsg_src/java/org/pente/gameServer/event/DSGArenaRequestEndedEvent.java`:

```java
package org.pente.gameServer.event;

/**
 * Server to whoever needs a notice that a request ended or was refused.
 * player is the requester, owner the table owner's name ("" if unknown).
 */
public class DSGArenaRequestEndedEvent extends AbstractDSGTableEvent {

    public static final String DECLINED = "DECLINED";
    public static final String TABLE_FULL = "TABLE_FULL";
    public static final String TABLE_CLOSED = "TABLE_CLOSED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String BLOCKED = "BLOCKED";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    public static final String GUEST_RATED = "GUEST_RATED";
    public static final String BOOTED = "BOOTED";
    public static final String NO_LONGER_AVAILABLE = "NO_LONGER_AVAILABLE";

    private String owner;
    private String reason;

    public DSGArenaRequestEndedEvent(String player, int table, String owner, String reason) {
        super(player, table);
        this.owner = owner;
        this.reason = reason;
    }

    public String getOwner() {
        return owner;
    }

    public String getReason() {
        return reason;
    }

    public String toString() {
        return "Arena request ended (" + reason + ", owner " + owner + ") " + super.toString();
    }
}
```

- [ ] **Step 5: Add the wrapper fields and accessors**

In `DSGEventWrapper.java`, after `private DSGArenaRequestJoinTableEvent dsgArenaRequestJoinTableEvent;` (`:83`) insert:

```java
    private DSGArenaWithdrawJoinRequestEvent dsgArenaWithdrawJoinRequestEvent;
    private DSGArenaJoinRequestsEvent dsgArenaJoinRequestsEvent;
    private DSGArenaMyRequestsEvent dsgArenaMyRequestsEvent;
    private DSGArenaRequestEndedEvent dsgArenaRequestEndedEvent;
```

After `setDsgArenaRequestJoinTableEvent(...)`'s closing brace (`:683`) insert:

```java

    public DSGArenaWithdrawJoinRequestEvent getDsgArenaWithdrawJoinRequestEvent() {
        return dsgArenaWithdrawJoinRequestEvent;
    }

    public void setDsgArenaWithdrawJoinRequestEvent(DSGArenaWithdrawJoinRequestEvent dsgArenaWithdrawJoinRequestEvent) {
        this.dsgArenaWithdrawJoinRequestEvent = dsgArenaWithdrawJoinRequestEvent;
    }

    public DSGArenaJoinRequestsEvent getDsgArenaJoinRequestsEvent() {
        return dsgArenaJoinRequestsEvent;
    }

    public void setDsgArenaJoinRequestsEvent(DSGArenaJoinRequestsEvent dsgArenaJoinRequestsEvent) {
        this.dsgArenaJoinRequestsEvent = dsgArenaJoinRequestsEvent;
    }

    public DSGArenaMyRequestsEvent getDsgArenaMyRequestsEvent() {
        return dsgArenaMyRequestsEvent;
    }

    public void setDsgArenaMyRequestsEvent(DSGArenaMyRequestsEvent dsgArenaMyRequestsEvent) {
        this.dsgArenaMyRequestsEvent = dsgArenaMyRequestsEvent;
    }

    public DSGArenaRequestEndedEvent getDsgArenaRequestEndedEvent() {
        return dsgArenaRequestEndedEvent;
    }

    public void setDsgArenaRequestEndedEvent(DSGArenaRequestEndedEvent dsgArenaRequestEndedEvent) {
        this.dsgArenaRequestEndedEvent = dsgArenaRequestEndedEvent;
    }
```

The wrapper's constructor matches the event's class name to a field type, and Gson uses the field name as the JSON key, so these field names are the wire keys. Do not rename them.

- [ ] **Step 6: Run the test to verify it passes**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestWireTest`
Expected: `OK (9 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/event/DSGArenaWithdrawJoinRequestEvent.java dsg_src/java/org/pente/gameServer/event/DSGArenaJoinRequestsEvent.java dsg_src/java/org/pente/gameServer/event/DSGArenaMyRequestsEvent.java dsg_src/java/org/pente/gameServer/event/DSGArenaRequestEndedEvent.java dsg_src/java/org/pente/gameServer/event/DSGEventWrapper.java dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestWireTest.java build.xml
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Add arena join-request snapshot, notice and withdraw events" -m "dsgArenaJoinRequestsEvent, dsgArenaMyRequestsEvent and dsgArenaRequestEndedEvent go to clients; dsgArenaWithdrawJoinRequestEvent comes from them. ArenaJoinRequestWireTest pins each wrapper key and field set, and that the capital-key decline is still not decoded."
```

---

### Task 2: Route the withdraw event to the table

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ServerTable.java:3919-3929`
- Modify: `dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java:179-184`
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestWireTest.java` (extend)

**Interfaces:**
- Consumes: `DSGArenaWithdrawJoinRequestEvent` (Task 1).
- Produces: `public void ServerTable.handleArenaWithdrawJoin(DSGArenaWithdrawJoinRequestEvent dsgEvent)` (no-op in `ServerTable`; overridden by `ArenaServerTable` in Task 6). `SynchronizedServerTable.callServerTable` dispatches the event to it.

- [ ] **Step 1: Write the failing test**

Add these imports to `ArenaJoinRequestWireTest.java`:

```java
import org.pente.gameServer.server.ServerTable;
import org.pente.gameServer.server.SynchronizedServerTable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
```

Add these members before the `// ---- helpers` section:

```java
    // ---- dispatch ------------------------------------------------------

    /** ServerTable that records withdraw calls. */
    static final class RecordingTable extends ServerTable {
        final List<DSGArenaWithdrawJoinRequestEvent> withdrawals = new ArrayList<>();

        @Override
        public void handleArenaWithdrawJoin(DSGArenaWithdrawJoinRequestEvent dsgEvent) {
            withdrawals.add(dsgEvent);
        }
    }

    /**
     * SynchronizedServerTable's switch drops unknown events silently, so a
     * missing case would lose every withdraw. Calls the real dispatch method.
     */
    public void testSynchronizedServerTableDispatchesWithdraw() throws Exception {
        SynchronizedServerTable sync = new SynchronizedServerTable();
        RecordingTable table = new RecordingTable();
        Field serverTable = SynchronizedServerTable.class.getDeclaredField("serverTable");
        serverTable.setAccessible(true);
        serverTable.set(sync, table);
        Method dispatch = SynchronizedServerTable.class.getDeclaredMethod("callServerTable", DSGEvent.class);
        dispatch.setAccessible(true);
        DSGArenaWithdrawJoinRequestEvent withdraw = new DSGArenaWithdrawJoinRequestEvent("bob", 7);

        dispatch.invoke(sync, withdraw);

        assertEquals(1, table.withdrawals.size());
        assertSame(withdraw, table.withdrawals.get(0));
    }
```

(The `ServerTable` no-op itself needs no test of its own: `RecordingTable`'s `@Override` does not compile without it.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestWireTest`
Expected: `BUILD FAILED`, `method does not override or implement a method from a supertype` / `cannot find symbol: method handleArenaWithdrawJoin`.

- [ ] **Step 3: Add the no-op handler and the dispatch case**

In `ServerTable.java`, after `handleArenaAcceptJoin(...) { }` (`:3927-3929`) and before the class's closing brace, insert:

```java

    public void handleArenaWithdrawJoin(DSGArenaWithdrawJoinRequestEvent dsgEvent) {

    }
```

In `SynchronizedServerTable.java`, after the accept case (`:183-184`):

```java
                    case DSGArenaAcceptTableJoinEvent dsgArenaAcceptTableJoinEvent ->
                            serverTable.handleArenaAcceptJoin(dsgArenaAcceptTableJoinEvent);
```

insert:

```java
                    case DSGArenaWithdrawJoinRequestEvent dsgArenaWithdrawJoinRequestEvent ->
                            serverTable.handleArenaWithdrawJoin(dsgArenaWithdrawJoinRequestEvent);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestWireTest`
Expected: `OK (10 tests)` and `BUILD SUCCESSFUL`. (log4j may print `log4j:WARN No appenders could be found`; that is expected without a test log config.)

- [ ] **Step 5: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ServerTable.java dsg_src/java/org/pente/gameServer/server/SynchronizedServerTable.java dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestWireTest.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Dispatch arena withdraw events to the table" -m "SynchronizedServerTable's switch drops unknown events, so the withdraw needs its own case. ServerTable gets a no-op handler that ArenaServerTable overrides."
```

---

### Task 3: Registry core: table state, request, withdraw, decline

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java`
- Create: `dsg_src/java/org/pente/gameServer/server/test/ArenaEventLog.java`
- Modify: `build.xml` (append a runner after `ArenaJoinRequestWireTest`)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java`

**Interfaces:**
- Consumes: the Task 1 events.
- Produces (package `org.pente.gameServer.server`, all public methods `synchronized`):
  - `interface ArenaJoinRequestRegistry.Notifier { void send(String player, DSGEvent event); }`
  - `ArenaJoinRequestRegistry(Notifier notifier)`
  - `void publishTable(int table, String owner, Collection<String> players, boolean noGameInProgress, boolean rated)`; a table is open when `players.size() == 1 && noGameInProgress`
  - `void request(int table, String requester, boolean requesterInMainRoom)`
  - `void withdraw(int table, String requester)`
  - `void decline(int table, String sender, String requester)`
  - `void sendMyRequests(String requester)`
  - read-only: `List<String> pendingRequesters(int table)`, `SortedSet<Integer> pendingTables(String requester)`, `boolean isBlocked(int table, String requester)`, `boolean isOpen(int table)`
  - test double `org.pente.gameServer.server.test.ArenaEventLog implements ArenaJoinRequestRegistry.Notifier, DSGEventToPlayerRouter` with `clear()`, `to(String player, Class<E>)`, `countAll(Class)`, `lastSnapshot(String owner, int table)`, `lastSnapshotSeqs(String owner, int table)`, `lastMyRequests(String player)`, `reasons(String player)` (each entry `"REASON@table"`)

Refusal precedence in `request` (the spec does not order them): table missing / not open / has a claim → `NOT_AVAILABLE`; requester outside main room / is the owner / is seated at any table / holds a claim → `NOT_AVAILABLE`; guest at a rated table → `GUEST_RATED`; already pending → `DUPLICATE`; blocked → `BLOCKED`. The guest rule keeps today's check, `requester.startsWith("guest")` (`ArenaServerTable.java:222`).

- [ ] **Step 1: Write the test double**

Create `dsg_src/java/org/pente/gameServer/server/test/ArenaEventLog.java`:

```java
package org.pente.gameServer.server.test;

import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;
import org.pente.gameServer.server.DSGEventToPlayerRouter;

import java.util.*;

/**
 * Records every event the join-request registry or an arena table sends, in
 * order, with its recipient. Stands in for the player router in unit tests.
 */
public class ArenaEventLog implements ArenaJoinRequestRegistry.Notifier, DSGEventToPlayerRouter {

    private final List<String> recipients = new ArrayList<>();
    private final List<DSGEvent> events = new ArrayList<>();

    public synchronized void send(String player, DSGEvent event) {
        recipients.add(player);
        events.add(event);
    }

    public void routeEvent(DSGEvent dsgEvent, String name) {
        send(name, dsgEvent);
    }

    public void addRoute(DSGEventListener dsgEventListener, String name) {
    }

    public DSGEventListener removeRoute(String name) {
        return null;
    }

    public DSGEventListener getRoute(String name) {
        return null;
    }

    public synchronized void clear() {
        recipients.clear();
        events.clear();
    }

    /** every event of this type sent to player, oldest first */
    public synchronized <E extends DSGEvent> List<E> to(String player, Class<E> type) {
        List<E> out = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (player.equals(recipients.get(i)) && type.isInstance(events.get(i))) {
                out.add(type.cast(events.get(i)));
            }
        }
        return out;
    }

    /** how many events of this type were sent to anyone */
    public synchronized int countAll(Class<? extends DSGEvent> type) {
        int n = 0;
        for (DSGEvent e : events) {
            if (type.isInstance(e)) {
                n++;
            }
        }
        return n;
    }

    /** requester names in the newest snapshot owner got for table, or null if none */
    public synchronized List<String> lastSnapshot(String owner, int table) {
        DSGArenaJoinRequestsEvent last = lastSnapshotEvent(owner, table);
        if (last == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (DSGArenaJoinRequestsEvent.Request r : last.getRequests()) {
            names.add(r.getPlayer());
        }
        return names;
    }

    /** seqs in the newest snapshot owner got for table, or null if none */
    public synchronized List<Long> lastSnapshotSeqs(String owner, int table) {
        DSGArenaJoinRequestsEvent last = lastSnapshotEvent(owner, table);
        if (last == null) {
            return null;
        }
        List<Long> seqs = new ArrayList<>();
        for (DSGArenaJoinRequestsEvent.Request r : last.getRequests()) {
            seqs.add(r.getSeq());
        }
        return seqs;
    }

    /** tables in the newest my-requests event player got, or null if none */
    public synchronized List<Integer> lastMyRequests(String player) {
        List<DSGArenaMyRequestsEvent> all = to(player, DSGArenaMyRequestsEvent.class);
        if (all.isEmpty()) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (int t : all.get(all.size() - 1).getTables()) {
            out.add(t);
        }
        return out;
    }

    /** "REASON@table" for every request-ended notice player got, oldest first */
    public synchronized List<String> reasons(String player) {
        List<String> out = new ArrayList<>();
        for (DSGArenaRequestEndedEvent e : to(player, DSGArenaRequestEndedEvent.class)) {
            out.add(e.getReason() + "@" + e.getTable());
        }
        return out;
    }

    private DSGArenaJoinRequestsEvent lastSnapshotEvent(String owner, int table) {
        List<DSGArenaJoinRequestsEvent> all = to(owner, DSGArenaJoinRequestsEvent.class);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).getTable() == table) {
                return all.get(i);
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java`:

```java
package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;

import java.util.*;

/**
 * ArenaJoinRequestRegistry rules R1-R11 (spec 2026-10-10), driven without
 * tables or sockets: an ArenaEventLog stands in for the player router.
 */
public class ArenaJoinRequestRegistryTest extends TestCase {

    private static final int A = 1;   // alice's table
    private static final int C = 2;   // carol's table

    private ArenaEventLog log;
    private ArenaJoinRequestRegistry reg;

    public ArenaJoinRequestRegistryTest(String name) {
        super(name);
    }

    protected void setUp() {
        log = new ArenaEventLog();
        reg = new ArenaJoinRequestRegistry(log);
        reg.publishTable(A, "alice", players("alice"), true, false);
        reg.publishTable(C, "carol", players("carol"), true, false);
        log.clear();
    }

    private static List<String> players(String... names) {
        return Arrays.asList(names);
    }

    private static List<Integer> tables(Integer... numbers) {
        return Arrays.asList(numbers);
    }

    // ---- table state ----------------------------------------------------

    public void testNewTableOwnerGetsEmptySnapshot() {
        ArenaEventLog fresh = new ArenaEventLog();
        ArenaJoinRequestRegistry r = new ArenaJoinRequestRegistry(fresh);

        r.publishTable(7, "zed", players("zed"), true, false);

        assertEquals(players(), fresh.lastSnapshot("zed", 7));
        assertTrue(r.isOpen(7));
    }

    // ---- R1/R2: creating a request ----------------------------------------

    public void testRequestGoesToOwnerAndRequesterSeesIt() {
        reg.request(A, "bob", true);

        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(players("bob"), log.lastSnapshot("alice", A));
        assertEquals(tables(A), log.lastMyRequests("bob"));
        assertEquals(players(), log.reasons("bob"));
    }

    public void testSnapshotIsOrderedBySeqAndSeqIsNeverReused() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        assertEquals(players("bob", "dave"), log.lastSnapshot("alice", A));
        List<Long> before = log.lastSnapshotSeqs("alice", A);
        assertTrue(before.get(0) < before.get(1));

        reg.withdraw(A, "bob");
        reg.request(A, "erin", true);

        assertEquals(players("dave", "erin"), log.lastSnapshot("alice", A));
        List<Long> after = log.lastSnapshotSeqs("alice", A);
        assertEquals(before.get(1), after.get(0));
        assertTrue(after.get(1) > before.get(1));
    }

    public void testRequesterMayRequestSeveralTables() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);

        assertEquals(tables(A, C), log.lastMyRequests("bob"));
        assertEquals(new TreeSet<>(tables(A, C)), reg.pendingTables("bob"));
    }

    public void testDuplicateRequestIsRefusedAndStillAnswered() {
        reg.request(A, "bob", true);
        reg.request(A, "bob", true);

        assertEquals(players("DUPLICATE@" + A), log.reasons("bob"));
        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(2, log.to("bob", DSGArenaMyRequestsEvent.class).size());
        assertEquals(tables(A), log.lastMyRequests("bob"));
    }

    public void testRequesterOutsideMainRoomIsRefused() {
        reg.request(A, "bob", false);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("bob"));
        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testOwnerAndSeatedPlayersAreRefused() {
        reg.publishTable(5, "zed", players("zed"), true, false);

        reg.request(A, "alice", true);
        reg.request(A, "zed", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("zed"));
        assertEquals(players(), reg.pendingRequesters(A));
    }

    public void testTableThatIsNotOpenRefuses() {
        // alice is alone but her game is paused: not open
        reg.publishTable(A, "alice", players("alice"), false, false);

        reg.request(A, "bob", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("bob"));
    }

    public void testUnknownTableRefusesWithEmptyOwner() {
        reg.request(42, "bob", true);

        List<DSGArenaRequestEndedEvent> ended = log.to("bob", DSGArenaRequestEndedEvent.class);
        assertEquals(1, ended.size());
        assertEquals(DSGArenaRequestEndedEvent.NOT_AVAILABLE, ended.get(0).getReason());
        assertEquals("", ended.get(0).getOwner());
        assertEquals("bob", ended.get(0).getPlayer());
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testGuestIsRefusedAtRatedTableOnly() {
        reg.publishTable(6, "rita", players("rita"), true, true);

        reg.request(6, "guest12", true);
        reg.request(A, "guest12", true);

        assertEquals(players("GUEST_RATED@6"), log.reasons("guest12"));
        assertEquals(players("guest12"), reg.pendingRequesters(A));
    }

    // ---- R3: withdraw -----------------------------------------------------

    public void testWithdrawRemovesRequestAndBlocksReRequest() {
        reg.request(A, "bob", true);

        reg.withdraw(A, "bob");

        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(reg.isBlocked(A, "bob"));

        reg.request(A, "bob", true);

        assertEquals(players("BLOCKED@" + A), log.reasons("bob"));
        assertEquals(players(), reg.pendingRequesters(A));
    }

    public void testWithdrawWithNothingPendingIsAnsweredAndDoesNotBlock() {
        reg.withdraw(A, "bob");

        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(!reg.isBlocked(A, "bob"));
        reg.request(A, "bob", true);
        assertEquals(players("bob"), reg.pendingRequesters(A));
    }

    // ---- R4: decline ------------------------------------------------------

    public void testDeclineRemovesBlocksAndTellsRequester() {
        reg.request(A, "bob", true);

        reg.decline(A, "alice", "bob");

        List<DSGArenaRequestEndedEvent> ended = log.to("bob", DSGArenaRequestEndedEvent.class);
        assertEquals(1, ended.size());
        assertEquals(DSGArenaRequestEndedEvent.DECLINED, ended.get(0).getReason());
        assertEquals("alice", ended.get(0).getOwner());
        assertEquals("bob", ended.get(0).getPlayer());
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertEquals(players(), log.lastSnapshot("alice", A));

        reg.request(A, "bob", true);

        assertEquals(players("DECLINED@" + A, "BLOCKED@" + A), log.reasons("bob"));
    }

    public void testBlockIsPerTable() {
        reg.request(A, "bob", true);
        reg.decline(A, "alice", "bob");

        reg.request(C, "bob", true);

        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testDeclineOfPlayerWhoIsNotPendingOnlyAnswersOwner() {
        reg.request(A, "bob", true);
        log.clear();

        reg.decline(A, "alice", "nobody");
        reg.decline(A, "alice", null);

        assertEquals(2, log.to("alice", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(players("bob"), log.lastSnapshot("alice", A));
        assertTrue(!reg.isBlocked(A, "nobody"));
        assertEquals(0, log.to("nobody", DSGEvent.class).size());
    }

    // ---- R9: only the owner decides -------------------------------------

    public void testNonOwnerDeclineIsIgnoredAndAnsweredWithEmptySnapshot() {
        reg.request(A, "bob", true);

        reg.decline(A, "dave", "bob");

        assertEquals(players("bob"), reg.pendingRequesters(A));
        assertEquals(players(), log.lastSnapshot("dave", A));
        assertEquals(players(), log.reasons("bob"));
        assertTrue(!reg.isBlocked(A, "bob"));
    }
}
```

- [ ] **Step 3: Wire the test into `build.xml` target `test`**

After the `ArenaJoinRequestWireTest` runner block added in Task 1, insert:

```xml
        <java classname="junit.textui.TestRunner" fork="true" failonerror="true">
            <classpath refid="test-classpath"/>
            <arg value="org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest"/>
        </java>
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `BUILD FAILED`, `cannot find symbol: class ArenaJoinRequestRegistry`.

- [ ] **Step 5: Write the registry**

Create `dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java`:

```java
package org.pente.gameServer.server;

import org.apache.log4j.Category;
import org.pente.gameServer.event.*;

import java.util.*;

/**
 * All arena join-request state (spec 2026-10-10, R1-R11), shared by every
 * arena table and owned by ArenaServer.
 * <p>
 * Locking: every public method is synchronized, so a change that spans
 * tables (a requester leaving, a claim) is one atomic step. This class never
 * takes the Server.tables lock: Server.removeTable holds that lock while it
 * calls ArenaServerTable.destroy(), which calls tableRemoved() here, so the
 * reverse order would deadlock.
 * <p>
 * Outgoing events go to the Notifier while the lock is held. The production
 * notifier only enqueues on the player's writer, so each client sees events
 * in mutation order. Every event carries copies, because Gson serializes it
 * later on the writer thread.
 */
public class ArenaJoinRequestRegistry {

    private static final Category log4j =
            Category.getInstance(ArenaJoinRequestRegistry.class.getName());

    /** Delivers one event to one player; production wraps DSGEventToPlayerRouter.routeEvent. */
    public interface Notifier {
        void send(String player, DSGEvent event);
    }

    /** What the registry knows about one table: its published state plus its memory. */
    private static final class TableMemory {
        String owner;
        boolean open;
        boolean rated;
        Set<String> players = new HashSet<>();
        /** requester -> seq; seq only grows, so insertion order is seq order */
        final LinkedHashMap<String, Long> pending = new LinkedHashMap<>();
        final Set<String> blocked = new HashSet<>();
        /** requester whose accepted join is queued on this table, or null (R5) */
        String claimed;
    }

    private final Notifier notifier;
    private final Map<Integer, TableMemory> tables = new HashMap<>();
    /** requester -> table whose accept they hold (R5) */
    private final Map<String, Integer> claims = new HashMap<>();
    private long nextSeq = 1;

    public ArenaJoinRequestRegistry(Notifier notifier) {
        this.notifier = notifier;
    }

    // ---- table state, published by each table from its own pump ----------

    public synchronized void publishTable(int table, String owner, Collection<String> players,
                                          boolean noGameInProgress, boolean rated) {
        TableMemory t = tables.get(table);
        if (t == null) {
            t = new TableMemory();
            tables.put(table, t);
        }
        String oldOwner = t.owner;

        t.owner = owner;
        t.rated = rated;
        t.players = new HashSet<>(players);
        t.open = t.players.size() == 1 && noGameInProgress;

        // a player who becomes owner gets the list for their table
        if (owner != null && !owner.equals(oldOwner)) {
            notifySnapshot(table, t, owner);
        }
    }

    // ---- requester actions -------------------------------------------------

    /** R2: create (requester, table) or refuse it; always answers with my-requests (R11). */
    public synchronized void request(int table, String requester, boolean requesterInMainRoom) {
        TableMemory t = tables.get(table);
        String reason = refusal(t, requester, requesterInMainRoom);
        if (reason != null) {
            notifyEnded(requester, table, t, requester, reason);
        } else {
            t.pending.put(requester, nextSeq++);
            notifySnapshot(table, t, t.owner);
        }
        notifyMyRequests(requester);
    }

    /** R3: like a decline the requester gives themselves; always answers (R11). */
    public synchronized void withdraw(int table, String requester) {
        TableMemory t = tables.get(table);
        if (t != null && t.pending.remove(requester) != null) {
            t.blocked.add(requester);
            notifySnapshot(table, t, t.owner);
        }
        notifyMyRequests(requester);
    }

    // ---- owner actions -----------------------------------------------------

    /** R4 and R9; always answers the sender with a snapshot (R11). */
    public synchronized void decline(int table, String sender, String requester) {
        TableMemory t = tables.get(table);
        if (t == null || !sender.equals(t.owner)) {
            log4j.warn("Arena decline at table " + table + " by non-owner " + sender + " ignored");
            notifyEmptySnapshot(table, sender);
            return;
        }
        if (t.pending.remove(requester) != null) {
            t.blocked.add(requester);
            notifyEnded(requester, table, t, requester, DSGArenaRequestEndedEvent.DECLINED);
            notifyMyRequests(requester);
        }
        notifySnapshot(table, t, sender);
    }

    // ---- answers on demand ---------------------------------------------------

    /** Sent when a player joins the main room. */
    public synchronized void sendMyRequests(String requester) {
        notifyMyRequests(requester);
    }

    // ---- read-only observability for tests; never mutates ------------------

    public synchronized List<String> pendingRequesters(int table) {
        TableMemory t = tables.get(table);
        return t == null ? new ArrayList<String>() : new ArrayList<>(t.pending.keySet());
    }

    public synchronized SortedSet<Integer> pendingTables(String requester) {
        return pendingTablesOf(requester);
    }

    public synchronized boolean isBlocked(int table, String requester) {
        TableMemory t = tables.get(table);
        return t != null && t.blocked.contains(requester);
    }

    public synchronized boolean isOpen(int table) {
        TableMemory t = tables.get(table);
        return t != null && t.open;
    }

    // ---- helpers; the caller holds the lock ---------------------------------

    private String refusal(TableMemory t, String requester, boolean requesterInMainRoom) {
        if (t == null || !t.open || t.claimed != null) {
            return DSGArenaRequestEndedEvent.NOT_AVAILABLE;
        }
        if (!requesterInMainRoom || requester.equals(t.owner)
                || isSeatedAnywhere(requester) || claims.containsKey(requester)) {
            return DSGArenaRequestEndedEvent.NOT_AVAILABLE;
        }
        if (requester.startsWith("guest") && t.rated) {
            return DSGArenaRequestEndedEvent.GUEST_RATED;
        }
        if (t.pending.containsKey(requester)) {
            return DSGArenaRequestEndedEvent.DUPLICATE;
        }
        if (t.blocked.contains(requester)) {
            return DSGArenaRequestEndedEvent.BLOCKED;
        }
        return null;
    }

    private boolean isSeatedAnywhere(String player) {
        for (TableMemory t : tables.values()) {
            if (t.players.contains(player)) {
                return true;
            }
        }
        return false;
    }

    private SortedSet<Integer> pendingTablesOf(String requester) {
        SortedSet<Integer> mine = new TreeSet<>();
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            if (e.getValue().pending.containsKey(requester)) {
                mine.add(e.getKey());
            }
        }
        return mine;
    }

    private void notifyMyRequests(String requester) {
        SortedSet<Integer> mine = pendingTablesOf(requester);
        int[] out = new int[mine.size()];
        int i = 0;
        for (int table : mine) {
            out[i++] = table;
        }
        notifier.send(requester, new DSGArenaMyRequestsEvent(out));
    }

    private void notifySnapshot(int table, TableMemory t, String to) {
        if (to == null) {
            return;
        }
        List<DSGArenaJoinRequestsEvent.Request> list = new ArrayList<>();
        if (t != null) {
            for (Map.Entry<String, Long> e : t.pending.entrySet()) {
                list.add(new DSGArenaJoinRequestsEvent.Request(e.getKey(), e.getValue()));
            }
        }
        notifier.send(to, new DSGArenaJoinRequestsEvent(table, list));
    }

    private void notifyEmptySnapshot(int table, String to) {
        notifySnapshot(table, null, to);
    }

    private void notifyEnded(String to, int table, TableMemory t, String requester, String reason) {
        String owner = t == null || t.owner == null ? "" : t.owner;
        notifier.send(to, new DSGArenaRequestEndedEvent(requester, table, owner, reason));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `OK (16 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java dsg_src/java/org/pente/gameServer/server/test/ArenaEventLog.java dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java build.xml
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Add ArenaJoinRequestRegistry with request, withdraw and decline" -m "One synchronized registry holds every arena request and blocked set and answers each action with a full snapshot. Events go through a Notifier so the rules run in unit tests without sockets."
```

---

### Task 4: Accept claims, claimed joins, full and reopened tables

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java` (replace `publishTable`, add methods and helpers)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java` (extend)

**Interfaces:**
- Consumes: Task 3 registry and `ArenaEventLog`.
- Produces:
  - `boolean accept(int table, String sender, String requester, Runnable queueJoin)`: R5/R9; runs `queueJoin` inside the lock; `true` only when a claim was recorded.
  - `boolean admitJoin(int table, String player, boolean playerInMainRoom)`: R6 pre-check; `false` means refuse the join (claim released, owner told `NO_LONGER_AVAILABLE`), and also for the one queued join whose claim a fill already released with `TABLE_FULL` (refused silently; the requester was told). Other unclaimed joins always return `true`.
  - `void playerJoinedTable(int table, String player)`: R7 on join, and fulfils a claim at `table` (a claim at another table is kept so that table refuses it).
  - `void joinFailed(int table, String player)`: the join was admitted but `ServerTable.handleJoin` did not seat the player (`BOOTED`, `ServerTable.java:447-453`, or an exception). Releases a claim held at `table` and tells the owner `NO_LONGER_AVAILABLE` plus a snapshot; no-op without such a claim.
  - `Integer claimedTable(String requester)`: read-only.
  - `publishTable` now applies R6 (table reaches 2 players: pending end `TABLE_FULL`, claim released, blocked set cleared) and R6b (not open → open: memory wiped, owner gets a fresh snapshot).
  - `DSGArenaRequestEndedEvent.player` is never null: an accept with no `playerToAccept` answers with `player: ""`.
- Ordering contract for callers (Task 7 relies on it): when a join lands, call `admitJoin` (stop if `false`), then seat the player, then exactly one of `playerJoinedTable` (seated) or `joinFailed` (not seated), then `publishTable`.

- [ ] **Step 1: Write the failing tests**

Add these imports to `ArenaJoinRequestRegistryTest.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
```

Add these members before the class's closing brace:

```java
    // ---- R5: accept claims ------------------------------------------------

    /** Stands in for queueing the DSGJoinTableEvent on the table's pump. */
    private static final class JoinQueue implements Runnable {
        int runs;

        public void run() {
            runs++;
        }
    }

    public void testAcceptClaimsRequesterAndQueuesJoin() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        JoinQueue q = new JoinQueue();

        assertTrue(reg.accept(A, "alice", "bob", q));

        assertEquals(1, q.runs);
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(tables(C), log.lastMyRequests("bob"));      // R5: no other request touched
        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testNonOwnerAcceptIsIgnoredAndAnsweredWithEmptySnapshot() {
        reg.request(A, "bob", true);
        JoinQueue q = new JoinQueue();

        assertTrue(!reg.accept(A, "bob", "bob", q));

        assertEquals(0, q.runs);
        assertNull(reg.claimedTable("bob"));
        assertEquals(players(), log.lastSnapshot("bob", A));
        assertEquals(players("bob"), reg.pendingRequesters(A));
    }

    public void testAcceptOfPlayerWhoIsNotPendingIsNoLongerAvailable() {
        JoinQueue q = new JoinQueue();

        assertTrue(!reg.accept(A, "alice", "ghost", q));
        assertTrue(!reg.accept(A, "alice", null, q));

        assertEquals(0, q.runs);
        assertEquals(players("NO_LONGER_AVAILABLE@" + A, "NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("ghost", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        // never null: Gson would drop the key and a client would render "undefined"
        assertEquals("", log.to("alice", DSGArenaRequestEndedEvent.class).get(1).getPlayer());
        assertEquals(2, log.to("alice", DSGArenaJoinRequestsEvent.class).size());
    }

    public void testDoubleAcceptOfSameRequesterClaimsOnce() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        JoinQueue qa = new JoinQueue();
        JoinQueue qc = new JoinQueue();

        assertTrue(reg.accept(A, "alice", "bob", qa));
        assertTrue(!reg.accept(C, "carol", "bob", qc));

        assertEquals(1, qa.runs);
        assertEquals(0, qc.runs);
        assertEquals(players("NO_LONGER_AVAILABLE@" + C), log.reasons("carol"));
        assertEquals(players("bob"), log.lastSnapshot("carol", C));   // still pending there (R5)
    }

    public void testOutstandingClaimRefusesNewRequestsToTableAndFromRequester() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());

        reg.request(A, "dave", true);
        reg.request(C, "bob", true);

        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("dave"));
        assertEquals(players("NOT_AVAILABLE@" + C), log.reasons("bob"));
    }

    public void testOwnerDoubleTapOnTwoRequestersClaimsOnlyTheFirst() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        JoinQueue qb = new JoinQueue();
        JoinQueue qd = new JoinQueue();
        assertTrue(reg.accept(A, "alice", "bob", qb));
        log.clear();

        assertTrue(!reg.accept(A, "alice", "dave", qd));

        assertEquals(0, qd.runs);
        assertEquals(players("dave"), log.lastSnapshot("alice", A));   // answered (R11)
        assertEquals(players(), log.reasons("alice"));
        assertEquals(players("dave"), reg.pendingRequesters(A));

        // bob's join lands: the table fills and dave's request ends
        reg.playerJoinedTable(A, "bob");
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertEquals(players("TABLE_FULL@" + A), log.reasons("dave"));
    }

    /** R10: two owners accepting the same requester at once claim exactly once. */
    public void testConcurrentAcceptsOfSameRequesterClaimOnce() throws Exception {
        for (int round = 0; round < 200; round++) {
            final ArenaJoinRequestRegistry r = new ArenaJoinRequestRegistry(new ArenaEventLog());
            r.publishTable(A, "alice", players("alice"), true, false);
            r.publishTable(C, "carol", players("carol"), true, false);
            r.request(A, "bob", true);
            r.request(C, "bob", true);
            final AtomicInteger joins = new AtomicInteger();
            final CountDownLatch go = new CountDownLatch(1);
            Thread ta = new Thread(() -> {
                awaitQuietly(go);
                r.accept(A, "alice", "bob", joins::incrementAndGet);
            });
            Thread tc = new Thread(() -> {
                awaitQuietly(go);
                r.accept(C, "carol", "bob", joins::incrementAndGet);
            });
            ta.start();
            tc.start();
            go.countDown();
            ta.join();
            tc.join();

            assertEquals("round " + round, 1, joins.get());
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- R6: the claimed join lands ---------------------------------------

    public void testClaimedJoinIsAdmittedWhileRequesterIsAvailable() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());

        assertTrue(reg.admitJoin(A, "bob", true));
        reg.playerJoinedTable(A, "bob");

        assertNull(reg.claimedTable("bob"));
    }

    public void testClaimedJoinIsRefusedWhenRequesterLeftMainRoom() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        assertTrue(!reg.admitJoin(A, "bob", false));

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("bob", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        assertEquals(players(), log.lastSnapshot("alice", A));
        // the table takes requests again
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
    }

    public void testClaimedJoinIsRefusedWhenRequesterSatDownElsewhere() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        // bob creates his own table 7 before the join lands at A
        assertTrue(reg.admitJoin(7, "bob", true));
        reg.playerJoinedTable(7, "bob");
        reg.publishTable(7, "bob", players("bob"), true, false);
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));   // kept so A can refuse it

        assertTrue(!reg.admitJoin(A, "bob", true));

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
    }

    public void testUnclaimedJoinIsAlwaysAdmitted() {
        assertTrue(reg.admitJoin(A, "zed", false));
    }

    /** Review Focus 6: admitted, then ServerTable.handleJoin answered BOOTED (or threw). */
    public void testClaimedJoinTheTableTurnsAwayReleasesClaimAndTellsOwner() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        assertTrue(reg.admitJoin(A, "bob", true));
        log.clear();

        reg.joinFailed(A, "bob");

        assertNull(reg.claimedTable("bob"));
        assertEquals(players("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals("bob", log.to("alice", DSGArenaRequestEndedEvent.class).get(0).getPlayer());
        assertEquals(players(), log.lastSnapshot("alice", A));
        // neither the table nor the requester is stuck on the claim
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
        reg.request(C, "bob", true);
        assertEquals(players("bob"), reg.pendingRequesters(C));
    }

    public void testFailedJoinWithoutAClaimThereChangesNothing() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        reg.joinFailed(A, "zed");
        reg.joinFailed(C, "bob");   // bob's claim is at A, not C

        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
        assertEquals(0, log.countAll(DSGEvent.class));
    }

    /** Review Focus 7: the table filled by another path before the claimed join landed. */
    public void testJoinQueuedByAClaimThatTheTableFullReleasedIsRefused() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        // zed's plain join was ahead of bob's in A's queue and fills the table
        assertTrue(reg.admitJoin(A, "zed", true));
        reg.playerJoinedTable(A, "zed");
        reg.publishTable(A, "alice", players("alice", "zed"), true, false);
        assertEquals(players("TABLE_FULL@" + A), log.reasons("bob"));
        assertNull(reg.claimedTable("bob"));

        assertTrue(!reg.admitJoin(A, "bob", true));   // bob's queued join: not seated as a spectator
        assertTrue(reg.admitJoin(A, "bob", true));    // a later plain join of his own is not affected
    }

    public void testJoiningAnyTableEndsRequestsSilently() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        log.clear();

        reg.playerJoinedTable(9, "bob");

        assertEquals(players(), reg.pendingRequesters(A));
        assertEquals(players(), reg.pendingRequesters(C));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(players(), log.lastSnapshot("carol", C));
        assertEquals(players(), log.reasons("bob"));
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testTableReachingTwoPlayersEndsPendingWithTableFull() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        reg.request(C, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        reg.playerJoinedTable(A, "bob");
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertEquals(players("TABLE_FULL@" + A), log.reasons("dave"));
        assertEquals("alice", log.to("dave", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(tables(), log.lastMyRequests("dave"));
        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(players(), log.reasons("bob"));               // R7: silent
        assertEquals(players(), log.lastSnapshot("carol", C));
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(!reg.isOpen(A));
    }

    public void testFullTableForgetsItsBlockedSet() {
        reg.request(A, "dave", true);
        reg.decline(A, "alice", "dave");

        reg.publishTable(A, "alice", players("alice", "bob"), true, false);

        assertTrue(!reg.isBlocked(A, "dave"));
    }

    // ---- R6b: reopening -----------------------------------------------------

    public void testOwnerLeavingFullTableReopensItForTheNewOwner() {
        reg.publishTable(A, "alice", players("alice", "bob"), true, false);
        log.clear();

        reg.publishTable(A, "bob", players("bob"), true, false);

        assertTrue(reg.isOpen(A));
        assertEquals(1, log.to("bob", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(players(), log.lastSnapshot("bob", A));
        reg.request(A, "dave", true);
        assertEquals(players("dave"), log.lastSnapshot("bob", A));
    }

    /** Registry-level: drive a paused, then cancelled, one-player table directly. */
    public void testPausedTableReopensWithMemoryWiped() {
        reg.request(A, "dave", true);
        reg.decline(A, "alice", "dave");
        reg.publishTable(A, "alice", players("alice"), false, false);   // waiting for player to return
        log.clear();
        reg.request(A, "erin", true);
        assertEquals(players("NOT_AVAILABLE@" + A), log.reasons("erin"));

        reg.publishTable(A, "alice", players("alice"), true, false);    // set cancelled

        assertEquals(players(), log.lastSnapshot("alice", A));
        assertTrue(!reg.isBlocked(A, "dave"));
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
    }

    public void testOwnerChangeWhileGameRunsSendsNewOwnerASnapshot() {
        reg.publishTable(A, "alice", players("alice", "bob"), false, false);
        log.clear();

        reg.publishTable(A, "bob", players("bob"), false, false);   // alice left mid-game

        assertTrue(!reg.isOpen(A));
        assertEquals(players(), log.lastSnapshot("bob", A));
    }

    public void testSeqKeepsGrowingAcrossReopen() {
        reg.request(A, "bob", true);
        long bobFirst = log.lastSnapshotSeqs("alice", A).get(0);
        reg.publishTable(A, "alice", players("alice", "zed"), true, false);   // full
        reg.publishTable(A, "alice", players("alice"), true, false);          // reopened

        reg.request(A, "dave", true);
        reg.request(A, "bob", true);

        assertEquals(players("dave", "bob"), log.lastSnapshot("alice", A));
        List<Long> seqs = log.lastSnapshotSeqs("alice", A);
        assertTrue(seqs.get(0) > bobFirst);
        assertTrue(seqs.get(1) > seqs.get(0));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `BUILD FAILED`, `cannot find symbol: method accept(...)` (and `admitJoin`, `playerJoinedTable`, `joinFailed`, `claimedTable`).

- [ ] **Step 3: Replace `publishTable` with the R6/R6b version**

In `ArenaJoinRequestRegistry.java`, add a field to `TableMemory` after `String claimed;`:

```java
        /** requesters whose claim a fill released while their join was still queued (R6) */
        final Set<String> releasedJoins = new HashSet<>();
```

Then replace the whole `publishTable(...)` method with:

```java
    public synchronized void publishTable(int table, String owner, Collection<String> players,
                                          boolean noGameInProgress, boolean rated) {
        TableMemory t = tables.get(table);
        boolean created = t == null;
        if (created) {
            t = new TableMemory();
            tables.put(table, t);
        }
        String oldOwner = t.owner;
        boolean wasOpen = t.open;
        int oldCount = t.players.size();

        t.owner = owner;
        t.rated = rated;
        t.players = new HashSet<>(players);
        t.open = t.players.size() == 1 && noGameInProgress;

        // a player who becomes owner gets the list for their table
        boolean snapshotOwner = owner != null && !owner.equals(oldOwner);
        if (oldCount < 2 && t.players.size() >= 2) {
            // R6: the table is full
            snapshotOwner |= !t.pending.isEmpty();
            wipeMemory(table, t);
        } else if (!created && !wasOpen && t.open) {
            // R6b: not open -> open. Requests only exist while a table is
            // open, so pending is already empty; the owner starts fresh.
            wipeMemory(table, t);
            snapshotOwner = true;
        }
        if (snapshotOwner && owner != null) {
            notifySnapshot(table, t, owner);
        }
    }
```

- [ ] **Step 4: Add accept, admitJoin and playerJoinedTable**

Insert after the closing brace of `decline(...)`:

```java

    /**
     * R5: accept only claims. Runs queueJoin, which queues the
     * DSGJoinTableEvent on the table's own pump, inside the lock. Always
     * answers the sender with a snapshot (R11).
     *
     * @return true if the requester was claimed and the join queued
     */
    public synchronized boolean accept(int table, String sender, String requester, Runnable queueJoin) {
        TableMemory t = tables.get(table);
        if (t == null || !sender.equals(t.owner)) {
            log4j.warn("Arena accept at table " + table + " by non-owner " + sender + " ignored");
            notifyEmptySnapshot(table, sender);
            return false;
        }
        if (t.claimed != null) {
            // an earlier accept's join is queued; the table fills when it lands
            notifySnapshot(table, t, sender);
            return false;
        }
        if (requester == null || !t.pending.containsKey(requester) || claims.containsKey(requester)) {
            // "" for a frame without playerToAccept: Gson would drop a null player
            notifier.send(sender, new DSGArenaRequestEndedEvent(
                    requester == null ? "" : requester, table, t.owner,
                    DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE));
            notifySnapshot(table, t, sender);
            return false;
        }
        t.pending.remove(requester);
        t.claimed = requester;
        claims.put(requester, table);
        queueJoin.run();
        notifySnapshot(table, t, sender);
        notifyMyRequests(requester);
        return true;
    }

    /**
     * R6: may this join land? Only a claimed join is checked: the requester
     * must still be in the main room and not seated at another table.
     * Otherwise the claim is released and the owner told. The one queued
     * join whose claim a fill already ended with TABLE_FULL is refused
     * silently, so the requester is not seated as a spectator after that
     * notice.
     */
    public synchronized boolean admitJoin(int table, String player, boolean playerInMainRoom) {
        TableMemory t = tables.get(table);
        if (t != null && t.releasedJoins.remove(player)) {
            return false;
        }
        Integer claimedTable = claims.get(player);
        if (claimedTable == null || claimedTable != table) {
            return true;
        }
        if (playerInMainRoom && !isSeatedElsewhere(player, table)) {
            return true;
        }
        releaseClaimAndTellOwner(table, player);
        return false;
    }

    /**
     * The join was admitted but ServerTable.handleJoin did not seat the
     * player (BOOTED within 5 minutes, or it threw). A claim held at this
     * table is released and the owner told, so neither stays stuck on it.
     */
    public synchronized void joinFailed(int table, String player) {
        Integer claimedTable = claims.get(player);
        if (claimedTable != null && claimedTable == table) {
            releaseClaimAndTellOwner(table, player);
        }
    }

    /**
     * R7: player sat down at table, so their requests everywhere end
     * silently. Fulfils their claim at this table; a claim at another table
     * is kept so that table refuses the join when it lands (R6).
     */
    public synchronized void playerJoinedTable(int table, String player) {
        Integer claimedTable = claims.get(player);
        if (claimedTable != null && claimedTable == table) {
            claims.remove(player);
            TableMemory t = tables.get(table);
            if (t != null) {
                t.claimed = null;
            }
        }
        if (removeAllPending(player)) {
            notifyMyRequests(player);
        }
    }
```

- [ ] **Step 5: Add the accessor and helpers**

Insert after `isOpen(...)` in the read-only section:

```java

    public synchronized Integer claimedTable(String requester) {
        return claims.get(requester);
    }
```

Insert after `isSeatedAnywhere(...)` in the helpers section:

```java

    private boolean isSeatedElsewhere(String player, int table) {
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            if (e.getKey() != table && e.getValue().players.contains(player)) {
                return true;
            }
        }
        return false;
    }

    /** R6/R6b: end pending with TABLE_FULL, release any claim, forget the blocked set. */
    private void wipeMemory(int table, TableMemory t) {
        endAllPending(table, t, DSGArenaRequestEndedEvent.TABLE_FULL);
        String released = t.claimed;
        releaseClaim(table, t, DSGArenaRequestEndedEvent.TABLE_FULL);
        t.blocked.clear();
        if (released != null) {
            // its DSGJoinTableEvent is still queued on the table's pump
            t.releasedJoins.add(released);
        }
    }

    /** R6: the claimed join will not seat the requester; the owner gets NO_LONGER_AVAILABLE. */
    private void releaseClaimAndTellOwner(int table, String player) {
        claims.remove(player);
        TableMemory t = tables.get(table);
        if (t != null) {
            t.claimed = null;
            if (t.owner != null) {
                notifier.send(t.owner, new DSGArenaRequestEndedEvent(
                        player, table, t.owner, DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE));
                notifySnapshot(table, t, t.owner);
            }
        }
    }

    /** Ends every pending request at t with a notice; each requester gets my-requests. */
    private void endAllPending(int table, TableMemory t, String reason) {
        List<String> ended = new ArrayList<>(t.pending.keySet());
        t.pending.clear();
        for (String requester : ended) {
            notifyEnded(requester, table, t, requester, reason);
            notifyMyRequests(requester);
        }
    }

    private void releaseClaim(int table, TableMemory t, String reason) {
        if (t.claimed == null) {
            return;
        }
        String requester = t.claimed;
        t.claimed = null;
        claims.remove(requester);
        notifyEnded(requester, table, t, requester, reason);
    }

    /** Removes requester's pending requests everywhere; owners get snapshots. */
    private boolean removeAllPending(String requester) {
        boolean changed = false;
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            TableMemory t = e.getValue();
            if (t.pending.remove(requester) != null) {
                changed = true;
                notifySnapshot(e.getKey(), t, t.owner);
            }
        }
        return changed;
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `OK (37 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Claim on arena accept and settle requests when tables fill or reopen" -m "Accept records a claim and queues the join under the registry lock, so two owners can never seat the same requester. A table reaching two players ends its other requests with TABLE_FULL; a table that reopens starts with empty memory."
```

---

### Task 5: Requester leaving, table removal, events for gone tables

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java` (add three methods)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java` (extend)

**Interfaces:**
- Consumes: Task 4 helpers `endAllPending`, `releaseClaim`, `removeAllPending`.
- Produces:
  - `void requesterLeftMainRoom(String player)`: R7 on leave; claim kept so R6 refuses it.
  - `void tableRemoved(int table)`: R8; pending and claimed requesters get `TABLE_CLOSED`; memory purged so the number can be reused.
  - `void answerUnroutable(DSGEvent event, int table)`: R2/R11 for events whose table no longer exists. Request → `NOT_AVAILABLE` (owner `""`) + my-requests; withdraw → my-requests; accept/decline → empty snapshot; anything else → nothing.

- [ ] **Step 1: Write the failing tests**

Add before the class's closing brace in `ArenaJoinRequestRegistryTest.java`:

```java
    // ---- R7: leaving the main room ----------------------------------------

    public void testRequesterLeavingMainRoomLosesRequestsSilently() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        log.clear();

        reg.requesterLeftMainRoom("bob");

        assertEquals(players(), log.lastSnapshot("alice", A));
        assertEquals(players(), log.lastSnapshot("carol", C));
        assertEquals(players(), log.reasons("bob"));
        assertTrue(reg.pendingTables("bob").isEmpty());
    }

    public void testLeavingKeepsTheClaimSoTheJoinIsRefused() {
        reg.request(A, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());

        reg.requesterLeftMainRoom("bob");

        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
        assertTrue(!reg.admitJoin(A, "bob", false));
    }

    // ---- R8: table removal --------------------------------------------------

    public void testRemovedTableClosesPendingRequests() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        log.clear();

        reg.tableRemoved(A);

        assertEquals(players("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertEquals(players("TABLE_CLOSED@" + A), log.reasons("dave"));
        assertEquals("alice", log.to("bob", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(tables(), log.lastMyRequests("bob"));
        assertTrue(!reg.isOpen(A));
    }

    public void testAcceptThenTableRemovedBeforeJoinLands() {
        reg.request(A, "bob", true);
        reg.request(C, "bob", true);
        reg.accept(A, "alice", "bob", new JoinQueue());
        log.clear();

        reg.tableRemoved(A);

        assertEquals(players("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertNull(reg.claimedTable("bob"));
        assertEquals(players("bob"), reg.pendingRequesters(C));   // lost nothing else
        JoinQueue q = new JoinQueue();
        assertTrue(reg.accept(C, "carol", "bob", q));
        assertEquals(1, q.runs);
    }

    public void testReusedTableNumberStartsClean() {
        reg.request(A, "bob", true);
        reg.request(A, "dave", true);
        reg.decline(A, "alice", "dave");
        reg.tableRemoved(A);
        log.clear();

        reg.publishTable(A, "zed", players("zed"), true, false);

        assertEquals(players(), log.lastSnapshot("zed", A));
        assertEquals(players(), reg.pendingRequesters(A));
        reg.request(A, "dave", true);
        assertEquals(players("dave"), reg.pendingRequesters(A));
    }

    // ---- R2/R11: events for a table that is gone ---------------------------

    public void testRequestToGoneTableIsAnsweredNotAvailable() {
        reg.answerUnroutable(new DSGArenaRequestJoinTableEvent("bob", 99), 99);

        assertEquals(players("NOT_AVAILABLE@99"), log.reasons("bob"));
        assertEquals("", log.to("bob", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(tables(), log.lastMyRequests("bob"));
    }

    public void testWithdrawFromGoneTableIsAnsweredWithMyRequests() {
        reg.request(C, "bob", true);

        reg.answerUnroutable(new DSGArenaWithdrawJoinRequestEvent("bob", 99), 99);

        assertEquals(2, log.to("bob", DSGArenaMyRequestsEvent.class).size());
        assertEquals(tables(C), log.lastMyRequests("bob"));
    }

    public void testAcceptAndDeclineAtGoneTableAreAnsweredWithEmptySnapshot() {
        reg.answerUnroutable(new DSGArenaAcceptTableJoinEvent("alice", 99, "bob"), 99);
        reg.answerUnroutable(new DSGArenaRejectTableJoinEvent("alice", 99, "bob", null), 99);

        assertEquals(2, log.to("alice", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(players(), log.lastSnapshot("alice", 99));
    }

    public void testOtherEventsAtGoneTableAreIgnored() {
        reg.answerUnroutable(new DSGJoinTableEvent("bob", 99), 99);

        assertEquals(0, log.to("bob", DSGEvent.class).size());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `BUILD FAILED`, `cannot find symbol: method requesterLeftMainRoom(String)` (and `tableRemoved`, `answerUnroutable`).

- [ ] **Step 3: Add the three methods**

In `ArenaJoinRequestRegistry.java`, insert after the closing brace of `playerJoinedTable(...)`:

```java

    /**
     * R7: the player left the main room (or disconnected); their requests
     * end silently. A claim is kept so the queued join is refused (R6).
     */
    public synchronized void requesterLeftMainRoom(String player) {
        if (removeAllPending(player)) {
            notifyMyRequests(player);
        }
    }

    /**
     * R8: the table is gone. Pending and claimed requesters get TABLE_CLOSED
     * and the memory is purged, so a reused table number starts clean.
     */
    public synchronized void tableRemoved(int table) {
        TableMemory t = tables.remove(table);
        if (t == null) {
            return;
        }
        endAllPending(table, t, DSGArenaRequestEndedEvent.TABLE_CLOSED);
        releaseClaim(table, t, DSGArenaRequestEndedEvent.TABLE_CLOSED);
    }

    /**
     * R2/R11: an arena event for a table number that no longer exists still
     * gets its answer. Called by ArenaServer.routeEventToTable.
     */
    public synchronized void answerUnroutable(DSGEvent event, int table) {
        if (event instanceof DSGArenaRequestJoinTableEvent request) {
            notifier.send(request.getPlayer(), new DSGArenaRequestEndedEvent(
                    request.getPlayer(), table, "", DSGArenaRequestEndedEvent.NOT_AVAILABLE));
            notifyMyRequests(request.getPlayer());
        } else if (event instanceof DSGArenaWithdrawJoinRequestEvent withdraw) {
            notifyMyRequests(withdraw.getPlayer());
        } else if (event instanceof DSGArenaAcceptTableJoinEvent
                || event instanceof DSGArenaRejectTableJoinEvent) {
            notifyEmptySnapshot(table, ((DSGTableEvent) event).getPlayer());
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaJoinRequestRegistryTest`
Expected: `OK (46 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaJoinRequestRegistry.java dsg_src/java/org/pente/gameServer/server/test/ArenaJoinRequestRegistryTest.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "End arena requests when requesters leave or tables close" -m "Leaving the main room drops a requester's requests silently. Removing a table tells its requesters TABLE_CLOSED and purges its memory so a reused number starts clean. Events for a table that is gone still get an answer."
```

---

### Task 6: Arena table forwards actions to the registry; ArenaServer owns it

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaServer.java:22-62`
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java:43-44` (fields), `:46-82` (constructors), `:219-276` (handlers)
- Modify: `build.xml` (append a runner after `ArenaJoinRequestRegistryTest`)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java`

**Interfaces:**
- Consumes: registry `request`, `withdraw`, `decline`, `accept`, `sendMyRequests`, `answerUnroutable`, `publishTable` (Tasks 3-5); `ServerTable.handleArenaWithdrawJoin` (Task 2).
- Produces:
  - `public ArenaJoinRequestRegistry ArenaServer.getJoinRequestRegistry()`
  - `ArenaServer.routeEventToMainRoom(DSGEvent)` override: after routing a `DSGJoinMainRoomEvent`, sends that player `dsgArenaMyRequestsEvent`.
  - `ArenaServer.routeEventToTable` answers events for a missing table via `answerUnroutable`, outside the `tables` lock.
  - `protected ArenaJoinRequestRegistry ArenaServerTable.joinRequests` (set in the real constructor from `((ArenaServer) server).getJoinRequestRegistry()`)
  - `protected ArenaServerTable()`: for unit tests only, builds an empty table without a server.
  - `ArenaServerTable.handleArenaRequestJoin`, `handleArenaWithdrawJoin`, `handleArenaRejectJoin`, `handleArenaAcceptJoin` forward to the registry; accept's `queueJoin` is `synchronizedTableListener.eventOccurred(new DSGJoinTableEvent(player, tableNum))`.
  - R2 boot check: `handleArenaRequestJoin` first checks `isBooted(player)` (private; the `ServerTable.handleJoin` rule at `ServerTable.java:447-453`: a `bootTimes` entry whose time is still in the future). A booted requester gets `dsgArenaRequestEndedEvent(player, tableNum, owner, BOOTED)` through `dsgEventRouter`, with `owner` from `getOwner()` (`""` if null), then `joinRequests.sendMyRequests(player)` as the R11 answer; `joinRequests.request` is not called. So `BOOTED` comes before every registry refusal. Both sends happen on this table's pump, in that order, and nothing changes, so the registry lock is not needed for ordering. `bootTimes` is only ever cleared by `handleInvite` (`ServerTable.java:3180`), never by a reopen.
  - The R2 "requester is in the main room" input is the table's own `isPlayerInMainRoom(player)`, which is updated on this table's pump in the same order as the request.
  - `joinRequestMap` and `rejectMap` are deleted; the old S→C `DSGArenaRequestJoinTableEvent` / `DSGArenaRejectTableJoinEvent` are no longer sent.
- Test scaffolding produced for Tasks 7-8 (in `ArenaServerTableJoinRequestTest`): nested `TestArenaTable extends ArenaServerTable` with stubs and drivers `landQueuedJoin()`, `leaveMainRoom(String)`, `seats(String)`, `playerCount()`, `number()`, `beginGame()`, `waitingTimeIsUp()`, `gameState()`, `holds(String)`, `bootedRecently(String)`, `bootExpired(String)`, and field `pump`; outer helpers `human`, `names`, `nums`, `request`, `accept`, `seededTable`, `ownedTable`.

- [ ] **Step 1: Write the failing test**

Create `dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java`:

```java
package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.LiveSet;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;
import org.pente.gameServer.server.ArenaServerTable;

import java.util.*;

/**
 * ArenaServerTable's join-request hooks (spec 2026-10-10, R2-R11), driven
 * through the real ServerTable code. Timers, threads and the database are
 * stubbed out; events land in an ArenaEventLog.
 */
public class ArenaServerTableJoinRequestTest extends TestCase {

    private static final int A = 1;
    private static final int C = 2;
    private static final String[] EVERYONE = {"alice", "bob", "carol", "dave", "erin", "zed"};

    private ArenaEventLog log;
    private ArenaJoinRequestRegistry reg;

    public ArenaServerTableJoinRequestTest(String name) {
        super(name);
    }

    protected void setUp() {
        log = new ArenaEventLog();
        reg = new ArenaJoinRequestRegistry(log);
    }

    // ---- scaffolding ------------------------------------------------------

    /** Collects what the table queues on its own pump instead of running it. */
    static final class Pump implements DSGEventListener {
        final List<DSGEvent> queued = new ArrayList<>();

        public void eventOccurred(DSGEvent dsgEvent) {
            queued.add(dsgEvent);
        }
    }

    static DSGPlayerData human(String name) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setName(name);
        p.setPlayerType(DSGPlayerData.HUMAN);
        return p;
    }

    /** An unrated public arena table whose main room holds EVERYONE. */
    static final class TestArenaTable extends ArenaServerTable {
        final Pump pump = new Pump();

        TestArenaTable(int table, ArenaJoinRequestRegistry registry, ArenaEventLog log) {
            tableNum = table;
            joinRequests = registry;
            dsgEventRouter = log;
            synchronizedTableListener = pump;
            playersInMainRoom = new Vector<>();
            for (String name : EVERYONE) {
                playersInMainRoom.add(human(name));
            }
            tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
            state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;
            timed = false;
            rated = false;
        }

        // side effects that need timers, threads or the database
        protected void startPressPlayTimer() {
        }

        protected void resetTableGameOver() {
        }

        protected void sendTimers(String toPlayer) {
        }

        protected void stopTimers() {
        }

        protected void startWaitingForPlayerToReturnTimer() {
        }

        protected void updateDatabaseAfterGameOverInSeparateThread(
                String winnerPlayer, String loserPlayer, int winner,
                LiveSet localSet, String status) {
        }

        // drivers
        /** Delivers the oldest queued join, as the pump would. */
        void landQueuedJoin() {
            DSGJoinTableEvent join = (DSGJoinTableEvent) pump.queued.remove(0);
            handleJoin(join.getPlayer());
        }

        void leaveMainRoom(String player) {
            handleMainRoomExit(player);
        }

        boolean seats(String player) {
            return isPlayerInTable(player);
        }

        int playerCount() {
            return playersInTable.size();
        }

        int number() {
            return tableNum;
        }

        void beginGame() {
            playingPlayers[1] = sittingPlayers[1];
            playingPlayers[2] = sittingPlayers[2];
            changeGameState(DSGGameStateTableEvent.GAME_IN_PROGRESS, "game started", 0);
        }

        void waitingTimeIsUp() {
            waitingForPlayerToReturnTimeUp = true;
        }

        int gameState() {
            return state;
        }

        /** Puts a player inside the ServerTable without the join path, so getOwner() sees them. */
        void holds(String player) {
            playersInTable.add(human(player));
        }

        /** Records a boot the way handleBoot does (ServerTable.java:3117): no rejoin for 5 minutes. */
        void bootedRecently(String player) {
            bootTimes.put(player, System.currentTimeMillis() + 1000L * 60 * 5);
        }

        /** A boot whose 5 minutes have passed: ServerTable leaves the entry, it just stops counting. */
        void bootExpired(String player) {
            bootTimes.put(player, System.currentTimeMillis() - 1);
        }
    }

    private static List<String> names(String... names) {
        return Arrays.asList(names);
    }

    private static List<Integer> nums(Integer... numbers) {
        return Arrays.asList(numbers);
    }

    private static void request(TestArenaTable t, String player) {
        t.handleArenaRequestJoin(new DSGArenaRequestJoinTableEvent(player, t.number()));
    }

    private static void accept(TestArenaTable t, String owner, String player) {
        t.handleArenaAcceptJoin(new DSGArenaAcceptTableJoinEvent(owner, t.number(), player));
    }

    /** A table whose state is put straight into the registry (no join hook needed). */
    private TestArenaTable seededTable(int table, String owner) {
        TestArenaTable t = new TestArenaTable(table, reg, log);
        reg.publishTable(table, owner, names(owner), true, false);
        log.clear();
        return t;
    }

    /** seededTable with the owner also inside the ServerTable, for checks that read getOwner(). */
    private TestArenaTable ownedTable(int table, String owner) {
        TestArenaTable t = seededTable(table, owner);
        t.holds(owner);
        return t;
    }

    // ---- Task 6: actions go through the registry --------------------------

    public void testRequestUsesTheTablesMainRoomView() {
        TestArenaTable a = seededTable(A, "alice");
        a.leaveMainRoom("bob");

        request(a, "bob");
        request(a, "dave");

        assertEquals(names("NOT_AVAILABLE@" + A), log.reasons("bob"));
        assertEquals(names("dave"), reg.pendingRequesters(A));
        assertEquals(names("dave"), log.lastSnapshot("alice", A));
    }

    public void testWithdrawGoesThroughRegistry() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        a.handleArenaWithdrawJoin(new DSGArenaWithdrawJoinRequestEvent("bob", A));

        assertTrue(reg.isBlocked(A, "bob"));
        assertEquals(nums(), log.lastMyRequests("bob"));
        request(a, "bob");
        assertEquals(names("BLOCKED@" + A), log.reasons("bob"));
    }

    public void testDeclineGoesThroughRegistry() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        a.handleArenaRejectJoin(new DSGArenaRejectTableJoinEvent("alice", A, "bob", null));

        assertEquals(names("DECLINED@" + A), log.reasons("bob"));
        assertEquals(names(), log.lastSnapshot("alice", A));
    }

    public void testAcceptQueuesClaimedJoinOnTheTablesOwnPump() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        accept(a, "alice", "bob");

        assertEquals(1, a.pump.queued.size());
        DSGJoinTableEvent join = (DSGJoinTableEvent) a.pump.queued.get(0);
        assertEquals("bob", join.getPlayer());
        assertEquals(A, join.getTable());
        assertEquals(Integer.valueOf(A), reg.claimedTable("bob"));
    }

    public void testNonOwnerAcceptQueuesNothing() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");

        accept(a, "bob", "bob");

        assertEquals(0, a.pump.queued.size());
        assertEquals(names(), log.lastSnapshot("bob", A));
        assertEquals(names("bob"), reg.pendingRequesters(A));
    }

    public void testOldIncrementalEventsAreNoLongerSent() {
        TestArenaTable a = seededTable(A, "alice");
        request(a, "bob");
        a.handleArenaRejectJoin(new DSGArenaRejectTableJoinEvent("alice", A, "bob", null));
        request(a, "dave");
        accept(a, "alice", "dave");

        assertEquals(0, log.countAll(DSGArenaRequestJoinTableEvent.class));
        assertEquals(0, log.countAll(DSGArenaRejectTableJoinEvent.class));
    }

    // ---- Task 6: R2 boot check, on the table's own pump ---------------------

    public void testBootedRequesterIsRefusedWithOwnerNamed() {
        TestArenaTable a = ownedTable(A, "alice");
        a.bootedRecently("bob");

        request(a, "bob");

        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        DSGArenaRequestEndedEvent ended = log.to("bob", DSGArenaRequestEndedEvent.class).get(0);
        assertEquals("bob", ended.getPlayer());
        assertEquals("alice", ended.getOwner());
        assertEquals(names(), reg.pendingRequesters(A));
        assertNull(log.lastSnapshot("alice", A));   // the registry never saw the request
    }

    public void testBootRefusalStillAnswersMyRequests() {
        TestArenaTable a = ownedTable(A, "alice");
        TestArenaTable c = ownedTable(C, "carol");
        request(c, "bob");
        a.bootedRecently("bob");
        log.clear();

        request(a, "bob");

        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        assertEquals(1, log.to("bob", DSGArenaMyRequestsEvent.class).size());   // R11
        assertEquals(nums(C), log.lastMyRequests("bob"));
        assertEquals(names("bob"), reg.pendingRequesters(C));                    // untouched
    }

    public void testExpiredBootNoLongerRefuses() {
        TestArenaTable a = ownedTable(A, "alice");
        a.bootExpired("bob");

        request(a, "bob");

        assertEquals(names(), log.reasons("bob"));
        assertEquals(names("bob"), reg.pendingRequesters(A));
        assertEquals(names("bob"), log.lastSnapshot("alice", A));
        assertEquals(nums(A), log.lastMyRequests("bob"));
    }
}
```

- [ ] **Step 2: Wire the test into `build.xml` target `test`**

After the `ArenaJoinRequestRegistryTest` runner block, insert:

```xml
        <java classname="junit.textui.TestRunner" fork="true" failonerror="true">
            <classpath refid="test-classpath"/>
            <arg value="org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest"/>
        </java>
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `BUILD FAILED`, `cannot find symbol: variable joinRequests` and `ArenaServerTable() ... cannot be applied` (no no-arg constructor).

- [ ] **Step 4: Give `ArenaServer` the registry**

Replace `ArenaServer.java:22-62` (imports through the end of `routeEventToTable`) with:

```java
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.ServerData;
import org.pente.gameServer.event.DSGArenaCreateTableEvent;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGJoinMainRoomEvent;
import org.pente.gameServer.event.DSGJoinTableEvent;

import java.util.Collection;


/**
 * A simple class to contain the necessary components that make up the server
 */
public class ArenaServer extends Server {

    // All arena join-request state (spec R10). Field initializers run after
    // super() returns, so the hooks below null-check it for events that race
    // server startup.
    private final ArenaJoinRequestRegistry joinRequests =
            new ArenaJoinRequestRegistry((player, event) -> dsgEventToPlayerRouter.routeEvent(event, player));

    public ArenaServer(Resources resources,
                       ServerData serverData) throws Throwable {

        super(resources, serverData);
    }

    public ArenaJoinRequestRegistry getJoinRequestRegistry() {
        return joinRequests;
    }

    @Override
    public void routeEventToMainRoom(DSGEvent event) {
        super.routeEventToMainRoom(event);
        // spec: dsgArenaMyRequestsEvent is sent when a player joins the main room
        if (event instanceof DSGJoinMainRoomEvent && joinRequests != null) {
            joinRequests.sendMyRequests(((DSGJoinMainRoomEvent) event).getPlayer());
        }
    }

    public void routeEventToTable(DSGEvent event, int tableNum) {
        if (event instanceof DSGArenaCreateTableEvent) {
            DSGJoinTableEvent joinEvent = new DSGJoinTableEvent();
            try {
                tableNum = createNewTable((DSGArenaCreateTableEvent) event);
                ((DSGArenaCreateTableEvent) event).setTable(tableNum);
            } catch (Throwable t) {
                log4j.error("Problem creating new ArenaServer table.", t);
            }
            joinEvent.setPlayer(((DSGArenaCreateTableEvent) event).getPlayer());
            joinEvent.setTable(tableNum);
            event = joinEvent;
        }
        boolean routed = false;
        synchronized (tables) {
            if (tableNum < 1 || tableNum >= tables.size() || tables.get(tableNum) == null) {
                log4j.error("Invalid table: " + tableNum + " for event " + event);
            } else {
                tables.get(tableNum).eventOccurred(event);
                routed = true;
            }
        }
        // R2/R11: arena events for a gone table still get their answer.
        // Outside the tables lock: the registry never runs under it from here.
        if (!routed && joinRequests != null) {
            joinRequests.answerUnroutable(event, tableNum);
        }
    }
```

(`createNewTable` at `:64-96` and the class's closing brace stay as they are.)

- [ ] **Step 5: Rewire `ArenaServerTable`**

Replace the two map fields (`ArenaServerTable.java:43-44`):

```java
    Map<String, DSGArenaRequestJoinTableEvent> joinRequestMap = new HashMap<>();
    Map<String, Date> rejectMap = new HashMap<>();
```

with:

```java
    protected ArenaJoinRequestRegistry joinRequests;

    /** Only for unit tests: an empty table without a server. */
    protected ArenaServerTable() {
    }
```

In the real constructor, after `this.creator = joinEvent.getPlayer();` (`:76`) insert:

```java
        this.joinRequests = ((ArenaServer) server).getJoinRequestRegistry();
```

Replace the three handlers (`:219-276`, from `@Override public void handleArenaRequestJoin` through the closing brace of `handleArenaAcceptJoin`) with:

```java
    @Override
    public void handleArenaRequestJoin(DSGArenaRequestJoinTableEvent dsgEvent) {
        String player = dsgEvent.getPlayer();
        // R2: a boot is this table's state, so it is checked here on the
        // table's own pump, before the registry sees the request. A reopen
        // (R6b) wipes registry memory but leaves bootTimes alone.
        if (isBooted(player)) {
            String owner = getOwner();
            dsgEventRouter.routeEvent(new DSGArenaRequestEndedEvent(player, tableNum,
                    owner == null ? "" : owner, DSGArenaRequestEndedEvent.BOOTED), player);
            joinRequests.sendMyRequests(player);   // R11 answer
            return;
        }
        joinRequests.request(tableNum, player, isPlayerInMainRoom(player));
    }

    /** The ServerTable.handleJoin boot rule (ServerTable.java:447-453), applied at request time. */
    private boolean isBooted(String player) {
        Long until = bootTimes.get(player);
        return until != null && System.currentTimeMillis() < until;
    }

    @Override
    public void handleArenaWithdrawJoin(DSGArenaWithdrawJoinRequestEvent dsgEvent) {
        joinRequests.withdraw(tableNum, dsgEvent.getPlayer());
    }

    @Override
    public void handleArenaRejectJoin(DSGArenaRejectTableJoinEvent dsgEvent) {
        joinRequests.decline(tableNum, dsgEvent.getPlayer(), dsgEvent.getPlayerToReject());
    }

    @Override
    public void handleArenaAcceptJoin(DSGArenaAcceptTableJoinEvent dsgEvent) {
        final String player = dsgEvent.getPlayerToAccept();
        // R5: only claims; the join is queued on this table's pump and re-checked when it lands (R6)
        joinRequests.accept(tableNum, dsgEvent.getPlayer(), player,
                () -> synchronizedTableListener.eventOccurred(new DSGJoinTableEvent(player, tableNum)));
    }
```

This also removes the unlocked `server.tables` scan that the old accept did (spec R10). `bootTimes` is `protected` in `ServerTable` (`:57`), so the subclass reads it directly; `DSGArenaRequestEndedEvent` comes in through the existing `org.pente.gameServer.event.*` import.

- [ ] **Step 6: Run the test to verify it passes**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `OK (9 tests)` and `BUILD SUCCESSFUL`. The `compile` target also builds `ArenaServer.java` here, which proves the server wiring compiles; its behavior is exercised by E2E scenarios "events for a gone table are answered" and every login (Task 9).

- [ ] **Step 7: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaServer.java dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java build.xml
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Route arena requests through the shared registry" -m "ArenaServer owns the registry, sends each player their pending tables on main-room join, and answers events aimed at a gone table. Arena tables forward request, withdraw, accept and decline to it and drop their per-table maps and the old incremental events. A player booted from a table less than 5 minutes ago is refused with BOOTED before the registry sees the request."
```

---

### Task 7: Join, leave and removal hooks on the arena table

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java:85-98` (`destroy`), `:155-171` (`handleJoin`); add `handleMainRoomExit` and `publishJoinRequestState`
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java` (extend)

**Interfaces:**
- Consumes: registry `admitJoin`, `playerJoinedTable`, `joinFailed`, `publishTable`, `requesterLeftMainRoom`, `tableRemoved`; Task 6 scaffolding.
- Produces:
  - `ArenaServerTable.handleJoin(String)`: `admitJoin` (return if `false`) → `super.handleJoin` → auto-sit, then in a `finally`: `playerJoinedTable` if the player is seated, else `joinFailed` (Review Focus 6), then `publishJoinRequestState()`. Every join passes through here (create, claimed join, returning player, admin join).
  - `protected void ArenaServerTable.publishJoinRequestState()`: takes one atomic copy of `playersInTable` and publishes its names, its owner (first non-null human, the `ServerTable.getOwner()` rule at `ServerTable.java:1974-1995`), `state == NO_GAME_IN_PROGRESS` and `rated`. The copy matters because `startGame()` publishes from the `pressPlayTimer` thread (Task 8) while the pump may change the `Vector`.
  - `ArenaServerTable.handleMainRoomExit(String)`: `requesterLeftMainRoom` then `super`.
  - `ArenaServerTable.destroy()`: calls `tableRemoved(tableNum)` (covers both removal paths, since `Server.removeTable` always calls `destroy()`).

- [ ] **Step 1: Write the failing tests**

In `ArenaServerTableJoinRequestTest.java` (the `bootedRecently` driver already exists from Task 6), add before the class's closing brace:

```java
    // ---- Task 7: join, leave and removal hooks ----------------------------

    private TestArenaTable tableCreatedBy(int table, String owner) {
        TestArenaTable t = new TestArenaTable(table, reg, log);
        t.handleJoin(owner);
        return t;
    }

    public void testCreatingTableOpensItAndGivesOwnerEmptySnapshot() {
        TestArenaTable a = tableCreatedBy(A, "alice");

        assertTrue(a.seats("alice"));
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
    }

    public void testClaimedJoinSeatsRequesterAndEndsEveryoneElse() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(a, "dave");
        request(c, "bob");
        accept(a, "alice", "bob");
        log.clear();

        a.landQueuedJoin();

        assertTrue(a.seats("bob"));
        assertEquals(2, a.playerCount());
        assertTrue(!reg.isOpen(A));
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("TABLE_FULL@" + A), log.reasons("dave"));
        assertEquals(names(), log.reasons("bob"));                 // R7: silent
        assertEquals(names(), log.lastSnapshot("carol", C));
        assertEquals(nums(), log.lastMyRequests("bob"));
    }

    public void testAcceptThenRequesterLeavesBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.leaveMainRoom("bob");                // the exit reached this pump first
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(1, a.playerCount());      // no phantom null player
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertTrue(reg.isOpen(A));
    }

    public void testAcceptThenRequesterSitsDownElsewhereBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        tableCreatedBy(7, "bob");
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
    }

    /**
     * Review Focus 6: the request passed R2's boot check, then a boot was
     * recorded on this table before the claimed join landed. admitJoin lets
     * the claimed join through, then ServerTable.handleJoin answers BOOTED.
     */
    public void testClaimedJoinRefusedByTheTableReleasesTheClaim() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.bootedRecently("bob");               // after the request was created
        log.clear();

        a.landQueuedJoin();

        assertTrue(!a.seats("bob"));
        assertEquals(1, log.to("bob", DSGJoinTableErrorEvent.class).size());
        assertNull(reg.claimedTable("bob"));
        assertEquals(names("NO_LONGER_AVAILABLE@" + A), log.reasons("alice"));
        assertEquals(names(), log.lastSnapshot("alice", A));
        assertTrue(reg.isOpen(A));
        // neither the table nor bob stays stuck on the claim
        request(a, "dave");
        assertEquals(names("dave"), reg.pendingRequesters(A));
        request(c, "bob");
        assertEquals(names("bob"), reg.pendingRequesters(C));
    }

    public void testAcceptThenTableRemovedBeforeJoinLands() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(c, "bob");
        accept(a, "alice", "bob");
        log.clear();

        a.destroy();   // Server.removeTable stopped the pump: the queued join is dropped

        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertNull(reg.claimedTable("bob"));
        accept(c, "carol", "bob");
        assertEquals(1, c.pump.queued.size());
    }

    public void testDoubleAcceptAcrossTablesQueuesOneJoin() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        TestArenaTable c = tableCreatedBy(C, "carol");
        request(a, "bob");
        request(c, "bob");

        accept(a, "alice", "bob");
        accept(c, "carol", "bob");

        assertEquals(1, a.pump.queued.size());
        assertEquals(0, c.pump.queued.size());
        assertEquals(names("NO_LONGER_AVAILABLE@" + C), log.reasons("carol"));
        a.landQueuedJoin();
        assertEquals(names(), log.lastSnapshot("carol", C));   // bob's request at C ends when he sits at A
    }

    public void testRequesterDisconnectRemovesRequests() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        log.clear();

        a.leaveMainRoom("bob");

        assertEquals(names(), log.lastSnapshot("alice", A));
        assertEquals(names(), log.reasons("bob"));
    }

    public void testClosingTableEndsPendingRequests() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        request(a, "dave");
        log.clear();

        a.destroy();

        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("bob"));
        assertEquals(names("TABLE_CLOSED@" + A), log.reasons("dave"));
        assertTrue(reg.pendingTables("bob").isEmpty());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `FAILURES!!!` — e.g. `testCreatingTableOpensItAndGivesOwnerEmptySnapshot` (`isOpen` false: nothing publishes yet) and `testAcceptThenRequesterLeavesBeforeJoinLands` (a null player is seated, or an error).

- [ ] **Step 3: Implement the hooks**

In `ArenaServerTable.java`, in `destroy()` (`:85`), make the first statement:

```java
        if (joinRequests != null) {
            joinRequests.tableRemoved(tableNum);   // R8
        }
```

Replace `handleJoin` (`:155-171`):

```java
    @Override
    public void handleJoin(String player) {
        super.handleJoin(player);
        if (isPlayerInTable(player)) {
            if (!rated) {
                if (sittingPlayers[playAs] == null) {
                    this.sit(player, playAs);
                    return;
                }
            }
            if (this.sittingPlayers[1] == null) {
                this.sit(player, 1);
            } else if (this.sittingPlayers[2] == null) {
                this.sit(player, 2);
            }
        }
    }
```

with:

```java
    @Override
    public void handleJoin(String player) {
        // R6: a claimed join only lands if the requester is still available
        if (!joinRequests.admitJoin(tableNum, player, isPlayerInMainRoom(player))) {
            return;
        }
        try {
            super.handleJoin(player);
            if (isPlayerInTable(player)) {
                sitJoinedPlayer(player);
            }
        } finally {
            if (isPlayerInTable(player)) {
                // R7: joining any table ends the player's requests everywhere
                joinRequests.playerJoinedTable(tableNum, player);
            } else {
                // turned away after admitJoin (BOOTED, or super threw and
                // callServerTable swallowed it): never leave a claim stuck
                joinRequests.joinFailed(tableNum, player);
            }
            publishJoinRequestState();
        }
    }

    private void sitJoinedPlayer(String player) {
        if (!rated) {
            if (sittingPlayers[playAs] == null) {
                this.sit(player, playAs);
                return;
            }
        }
        if (this.sittingPlayers[1] == null) {
            this.sit(player, 1);
        } else if (this.sittingPlayers[2] == null) {
            this.sit(player, 2);
        }
    }

    @Override
    public void handleMainRoomExit(String player) {
        // R7: leaving the main room (or disconnecting) ends the player's requests
        joinRequests.requesterLeftMainRoom(player);
        super.handleMainRoomExit(player);
    }

    /** R6b/R10: tell the registry who sits here, who owns it, and whether a game runs. */
    protected void publishJoinRequestState() {
        // one atomic copy (Vector.toArray is synchronized): startGame() also
        // publishes from the pressPlayTimer thread while the pump may change
        // the list, and iterating the Vector itself could throw into startGame()
        List<DSGPlayerData> seated = new ArrayList<>(playersInTable);
        List<String> names = new ArrayList<>();
        String owner = null;
        for (DSGPlayerData d : seated) {
            if (d != null) {
                names.add(d.getName());
                if (owner == null && d.isHuman()) {
                    owner = d.getName();   // the ServerTable.getOwner() rule, on the copy
                }
            }
        }
        joinRequests.publishTable(tableNum, owner, names,
                state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, rated);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `OK (18 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Check claimed arena joins and settle requests on join, leave and close" -m "A claimed join is refused if the requester left the main room or sat down elsewhere, which also stops the phantom null seat. A claimed join the table still turns away (BOOTED) releases its claim. Joining any table, leaving the main room and table removal now end the right requests, and every join publishes the table's state."
```

---

### Task 8: Reopen transitions through real exits and game ends

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java` (add `publishLock` and `destroyed` flag, guard and lock in `publishJoinRequestState`, `exit` and `changeGameState` overrides, set flag under the lock in `destroy`)
- Test: `dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java` (extend)

**Interfaces:**
- Consumes: `ServerTable.exit(String, boolean)` (`ServerTable.java:2622-2673`), `ServerTable.changeGameState(int, String, String, int)` (`:2714-2719`; the 3-arg overload delegates to it), `publishJoinRequestState()` (Task 7).
- Produces:
  - `ArenaServerTable.exit(String, boolean)` and `changeGameState(int, String, String, int)` overrides that call `super` then `publishJoinRequestState()`, so the registry sees every change to player count, owner and `state` (spec R6b: "on every change to the player count, owner, or `state`").
  - `protected boolean ArenaServerTable.destroyed`, set in `destroy()` together with `tableRemoved` under a private `publishLock`; `publishJoinRequestState()` checks it and publishes under the same lock, so a stale table object (its `pressPlayTimer` thread) cannot write into a removed or reused table number, not even one whose check ran just before `destroy()`.

- [ ] **Step 1: Write the failing tests**

Add before the class's closing brace in `ArenaServerTableJoinRequestTest.java`:

```java
    // ---- Task 8: R6b reopen transitions -------------------------------------

    /** alice's table with bob accepted and seated, and a game running */
    private TestArenaTable gameInProgress() {
        TestArenaTable a = tableCreatedBy(A, "alice");
        request(a, "bob");
        accept(a, "alice", "bob");
        a.landQueuedJoin();
        a.beginGame();
        log.clear();
        return a;
    }

    public void testTableStaysClosedWhilePausedForReturningPlayer() {
        TestArenaTable a = gameInProgress();

        a.leaveMainRoom("bob");   // bob disconnects mid-game

        assertEquals(DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN, a.gameState());
        assertEquals(1, a.playerCount());
        assertTrue(!reg.isOpen(A));
        request(a, "erin");
        assertEquals(names("NOT_AVAILABLE@" + A), log.reasons("erin"));
    }

    public void testOwnerLeavingFinishedGameHandsOverAnOpenTable() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(!reg.isOpen(A));   // still two players
        log.clear();

        a.handleExit("alice", false);

        assertTrue(reg.isOpen(A));
        assertEquals(1, log.to("bob", DSGArenaJoinRequestsEvent.class).size());
        assertEquals(names(), log.lastSnapshot("bob", A));
        request(a, "erin");
        assertEquals(names("erin"), log.lastSnapshot("bob", A));
    }

    public void testPlayerLeavingAfterGameEndsReopensTable() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        log.clear();

        a.handleExit("bob", false);

        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), log.lastSnapshot("alice", A));
    }

    /**
     * Review Focus 8: handleBoot runs exit (which reopens the table, R6b,
     * and wipes registry memory) and then records bootTimes. The boot
     * outlives the reopen, so bob is still refused with BOOTED.
     */
    public void testBootThatReopensTheTableStillRefusesTheBootedPlayer() {
        TestArenaTable a = gameInProgress();
        a.handleResign(new DSGResignTableEvent("bob", A));
        log.clear();

        a.handleBoot(new DSGBootTableEvent("alice", A, "bob"));

        assertTrue(!a.seats("bob"));
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "bob");
        assertEquals(names("BOOTED@" + A), log.reasons("bob"));
        assertEquals("alice", log.to("bob", DSGArenaRequestEndedEvent.class).get(0).getOwner());
        assertEquals(nums(), log.lastMyRequests("bob"));
        assertEquals(names(), reg.pendingRequesters(A));
        request(a, "erin");   // the reopen still lets everyone else in
        assertEquals(names("erin"), log.lastSnapshot("alice", A));
    }

    /**
     * Through real tables the memory is already empty here: filling to two
     * players wiped it (R6) and a table that is not open takes no requests.
     * What R6b adds at this level is the owner's fresh snapshot and that the
     * table takes requests again; the wipe itself is pinned at registry level
     * by testPausedTableReopensWithMemoryWiped (Task 4).
     */
    public void testWaitingGameCancelledReopensTable() {
        TestArenaTable a = gameInProgress();
        a.leaveMainRoom("bob");
        request(a, "erin");           // refused while paused
        a.waitingTimeIsUp();
        log.clear();

        a.handleForceCancelResign(new DSGForceCancelResignTableEvent(
                "alice", A, DSGForceCancelResignTableEvent.CANCEL));

        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), reg.pendingRequesters(A));
    }

    public void testWaitingGameForceResignedReopensTable() {
        TestArenaTable a = gameInProgress();
        a.leaveMainRoom("bob");
        a.waitingTimeIsUp();
        log.clear();

        a.handleForceCancelResign(new DSGForceCancelResignTableEvent(
                "alice", A, DSGForceCancelResignTableEvent.RESIGN));

        assertEquals(DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, a.gameState());
        assertTrue(reg.isOpen(A));
        assertEquals(names(), log.lastSnapshot("alice", A));
        request(a, "erin");
        assertEquals(names("erin"), reg.pendingRequesters(A));
    }

    /**
     * Review Focus 3. Passes before this task (nothing published on state
     * change) and must keep passing after it: the guard is what holds.
     */
    public void testDestroyedTableNeverRepublishesIntoReusedNumber() {
        TestArenaTable old = tableCreatedBy(A, "alice");
        old.destroy();
        TestArenaTable reused = tableCreatedBy(A, "zed");

        old.beginGame();   // a timer thread on the old object still runs startGame()

        assertTrue(reg.isOpen(A));
        request(reused, "erin");
        assertEquals(names("erin"), log.lastSnapshot("zed", A));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `FAILURES!!!` in the five reopen tests (`testOwnerLeavingFinishedGameHandsOverAnOpenTable`, `testPlayerLeavingAfterGameEndsReopensTable`, `testBootThatReopensTheTableStillRefusesTheBootedPlayer`, `testWaitingGameCancelledReopensTable`, `testWaitingGameForceResignedReopensTable`): the registry never hears about exits or state changes, so `isOpen` stays false and no snapshot reaches the owner. `testTableStaysClosedWhilePausedForReturningPlayer` and `testDestroyedTableNeverRepublishesIntoReusedNumber` already pass here; they guard against the new publishes opening a paused table or a reused number, and must still pass after Step 3.

- [ ] **Step 3: Implement the overrides and the guard**

In `ArenaServerTable.java`, after `protected ArenaJoinRequestRegistry joinRequests;` add:

```java
    /**
     * Serializes every publish with destroy(): startGame() publishes from the
     * pressPlayTimer thread, so without it a publish that passed the
     * destroyed check could land after tableRemoved and recreate the table.
     * Lock order: Server.tables -> publishLock -> registry (never reversed).
     */
    private final Object publishLock = new Object();
    /** Set by destroy() under publishLock; a destroyed table never publishes again. */
    protected boolean destroyed = false;
```

In `destroy()`, replace the Task 7 block

```java
        if (joinRequests != null) {
            joinRequests.tableRemoved(tableNum);   // R8
        }
```

with:

```java
        synchronized (publishLock) {
            destroyed = true;
            if (joinRequests != null) {
                joinRequests.tableRemoved(tableNum);   // R8
            }
        }
```

Wrap the whole body of `publishJoinRequestState()` (from the `List<DSGPlayerData> seated` line through the `publishTable` call) in the lock, with the guard first, so it reads:

```java
    protected void publishJoinRequestState() {
        synchronized (publishLock) {
            if (destroyed) {
                return;
            }
            // one atomic copy (Vector.toArray is synchronized): startGame() also
            // publishes from the pressPlayTimer thread while the pump may change
            // the list, and iterating the Vector itself could throw into startGame()
            List<DSGPlayerData> seated = new ArrayList<>(playersInTable);
            List<String> names = new ArrayList<>();
            String owner = null;
            for (DSGPlayerData d : seated) {
                if (d != null) {
                    names.add(d.getName());
                    if (owner == null && d.isHuman()) {
                        owner = d.getName();   // the ServerTable.getOwner() rule, on the copy
                    }
                }
            }
            joinRequests.publishTable(tableNum, owner, names,
                    state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS, rated);
        }
    }
```

After `publishJoinRequestState()` add:

```java

    @Override
    protected void exit(String player, boolean booted) {
        super.exit(player, booted);
        publishJoinRequestState();   // player count and maybe owner changed (R6b)
    }

    @Override
    protected void changeGameState(int newState, String reason, String winner, int gameInSet) {
        super.changeGameState(newState, reason, winner, gameInSet);
        publishJoinRequestState();   // a table is only open with no game in progress (R6b)
    }
```

`exit` may remove the table (`server.removeTable` → `destroy()`) before returning; the `destroyed` guard makes the publish after it a no-op. `startGame()` can run on the `pressPlayTimer` thread (`ArenaServerTable.java:140-147`), off the pump: `publishLock` orders that publish against the pump's publishes and `destroy()`, and the copy in `publishJoinRequestState()` means it cannot throw into `startGame()` (which would leave a game marked in progress with no centre move and no running timer, `ServerTable.java:1923-1940`). While holding the registry lock, the only call into a table is accept's `queueJoin`, a plain pump queue add that never takes `publishLock`, so `publishLock` → registry cannot deadlock.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ArenaServerTableJoinRequestTest`
Expected: `OK (25 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/gameServer/server/ArenaServerTable.java dsg_src/java/org/pente/gameServer/server/test/ArenaServerTableJoinRequestTest.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Reopen arena tables after exits and game ends" -m "Arena tables publish their state on every exit and game-state change, so a table that becomes open again wipes its memory and sends the owner a fresh list: owner hand-over, a player leaving after the game, and cancelled or force-resigned waiting games. A destroyed table can no longer publish into a reused table number."
```

---

### Task 9: Verification: full suite and local E2E

**Files:**
- Create (scratch, not committed): `/private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs`
- No repo changes. If a step fails, first check the failing expectation against the spec rule it exercises (R1-R11): a driver expectation that contradicts the spec is fixed in the driver. Only a real server defect is fixed in the task that owns the code (with its own test and commit), then rerun this task from Step 1.

**Interfaces:**
- Consumes: everything above, deployed to the local docker stack.
- Produces: evidence (command plus output lines) for the controller's report.

The spec's E2E list names "two React instances plus one Android or iOS device". Those client-side checks belong to the client plans. This task exercises every spec E2E scenario at the protocol level, on the same TCP+TLS port 15999 the mobile apps use, with guest accounts (no credentials needed).

- [ ] **Step 1: Run the full server suite**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test`
Expected: one `OK (N tests)` per runner, including `OK (10 tests)` (wire), `OK (46 tests)` (registry), `OK (25 tests)` (table), then `BUILD SUCCESSFUL`. Route the output through `ctx_execute` and grep for `OK (`, `FAILURES`, `BUILD`.

- [ ] **Step 2: Run the `ServerTable` regression test that `test` does not include**

`ServerTableDrawScoringTest` is not wired into target `test` (only `test-one` runs it), and this plan changes code it subclasses. Run:
`env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.gameServer.server.test.ServerTableDrawScoringTest`
Expected: `OK (15 tests)` and `BUILD SUCCESSFUL`.

- [ ] **Step 3: Deploy to the local stack**

Check the stack: `docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml ps pente.org` shows the service running. If it is not running, start the local dev stack with `docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml up -d` and wait for it.

Then compile and restart (there is no hot reload of classes; `justCompile` restarts the container itself):
`cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org && ./justCompile`
Expected: `BUILD SUCCESSFUL` and docker's restart line for the `pente.org` container. Connected live clients are dropped by the restart.

- [ ] **Step 4: Write the E2E driver**

`mkdir -p /private/tmp/claude-501/arena-plans/e2e`, then create `/private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs`:

```js
// Protocol-level E2E for arena persistent join requests (spec 2026-10-10,
// Testing -> E2E) against the local pente.org stack. Speaks the TCP+TLS
// protocol the mobile apps use on port 15999: one JSON object per frame,
// each frame ended by byte 255. Logs in guests, so no credentials are needed.
// Usage: node arena-e2e.mjs [scenario-name-substring]
import tls from 'node:tls';

const HOST = process.env.ARENA_HOST ?? 'localhost';
const PORT = Number(process.env.ARENA_PORT ?? 15999);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class Client {
  constructor() {
    this.frames = [];
    this.waiters = [];
    this.buf = Buffer.alloc(0);
    this.name = '(not logged in)';
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.sock = tls.connect({ host: HOST, port: PORT, rejectUnauthorized: false }, resolve);
      // stays attached for the socket's life so a late error never crashes node
      this.sock.on('error', (e) => reject(e));
      this.sock.on('data', (chunk) => this.onData(chunk));
    });
  }

  onData(chunk) {
    this.buf = Buffer.concat([this.buf, chunk]);
    let end;
    while ((end = this.buf.indexOf(255)) !== -1) {
      const frame = JSON.parse(this.buf.subarray(0, end).toString('utf8'));
      this.buf = this.buf.subarray(end + 1);
      const key = Object.keys(frame)[0];
      if (key === 'dsgPingEvent') {
        this.write(frame);
        continue;
      }
      const body = frame[key];
      this.frames.push({ key, body });
      for (const w of [...this.waiters]) {
        if (w.key === key && w.pred(body)) {
          this.waiters.splice(this.waiters.indexOf(w), 1);
          clearTimeout(w.timer);
          w.resolve(body);
        }
      }
    }
  }

  write(obj) {
    this.sock.write(Buffer.concat([Buffer.from(JSON.stringify(obj), 'utf8'), Buffer.from([255])]));
  }

  send(key, body) {
    this.write({ [key]: { ...body, time: 0 } });
  }

  mark() {
    return this.frames.length;
  }

  // resolves with the first frame at or after `since` with this key matching pred
  waitFor(key, pred, since, ms = 5000) {
    for (let i = since; i < this.frames.length; i++) {
      if (this.frames[i].key === key && pred(this.frames[i].body)) {
        return Promise.resolve(this.frames[i].body);
      }
    }
    return new Promise((resolve, reject) => {
      const w = { key, pred, resolve };
      w.timer = setTimeout(() => {
        this.waiters.splice(this.waiters.indexOf(w), 1);
        reject(new Error(`${this.name} got no matching ${key} within ${ms} ms`));
      }, ms);
      this.waiters.push(w);
    });
  }

  since(mark, key) {
    return this.frames.slice(mark).filter((f) => f.key === key).map((f) => f.body);
  }

  async loginGuest() {
    await this.connect();
    const m = this.mark();
    this.send('dsgLoginEvent', { guest: true });
    const login = await this.waitFor('dsgLoginEvent', () => true, m);
    this.name = login.player;
    // spec: dsgArenaMyRequestsEvent is sent when a player joins the main room
    await this.waitFor('dsgArenaMyRequestsEvent', (b) => b.tables.length === 0, m);
    return this;
  }

  async createTable() {
    const m = this.mark();
    this.send('dsgArenaCreateTableEvent', {
      timed: true, initialMinutes: 10, incrementalSeconds: 0,
      rated: false, game: 1, playAs: 1, table: -1,
    });
    // the creator becomes owner and gets an empty snapshot for the new table
    const snap = await this.waitFor('dsgArenaJoinRequestsEvent', (b) => b.requests.length === 0, m);
    return snap.table;
  }

  request(table) { this.send('dsgArenaRequestJoinTableEvent', { table }); }
  withdraw(table) { this.send('dsgArenaWithdrawJoinRequestEvent', { table }); }
  accept(table, player) { this.send('dsgArenaAcceptTableJoinEvent', { table, playerToAccept: player }); }
  decline(table, player) { this.send('dsgArenaRejectTableJoinEvent', { table, playerToReject: player }); }
  exitTable(table) { this.send('dsgExitTableEvent', { table, forced: false, booted: false }); }
  resign(table) { this.send('dsgResignTableEvent', { table }); }
  forceCancelResign(table, action) { this.send('dsgForceCancelResignTableEvent', { table, action }); }
  boot(table, player) { this.send('dsgBootTableEvent', { table, toBoot: player }); }
  disconnect() { this.sock.destroy(); }
}

const names = (b) => b.requests.map((r) => r.player);
const snapIs = (table, list) => (b) => b.table === table && JSON.stringify(names(b)) === JSON.stringify(list);
const endedIs = (table, reason) => (b) => b.table === table && b.reason === reason;
const sorted = (xs) => [...xs].sort((x, y) => x - y);
const mineIs = (list) => (b) => JSON.stringify(sorted(b.tables)) === JSON.stringify(sorted(list));
const stateIs = (table, state) => (b) => b.table === table && b.state === state;

async function guests(n) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(await new Client().loginGuest());
  return out;
}

function closeAll(clients) {
  for (const c of clients) {
    try { c.disconnect(); } catch { /* already closed */ }
  }
}

async function requestOk(requester, owner, table, expected) {
  const mr = requester.mark();
  const mo = owner.mark();
  requester.request(table);
  await requester.waitFor('dsgArenaMyRequestsEvent', (b) => b.tables.includes(table), mr);
  await owner.waitFor('dsgArenaJoinRequestsEvent', snapIs(table, expected), mo);
}

async function requestRefused(requester, table, reason) {
  const m = requester.mark();
  requester.request(table);
  await requester.waitFor('dsgArenaRequestEndedEvent', endedIs(table, reason), m);
  // R11 answer: a DUPLICATE leaves the existing request pending (R2), every
  // other refusal means there is no request at this table
  const stillPending = reason === 'DUPLICATE';
  await requester.waitFor('dsgArenaMyRequestsEvent', (b) => b.tables.includes(table) === stillPending, m);
}

async function acceptAndJoin(owner, requester, table) {
  const m = requester.mark();
  owner.accept(table, requester.name);
  await requester.waitFor('dsgJoinTableEvent', (b) => b.table === table && b.player === requester.name, m);
}

// arena auto-starts 5 s after both seats fill (ArenaServerTable.WAIT_TO_PRESS_PLAY)
async function playUntilOver(owner, joiner, table, since) {
  await owner.waitFor('dsgGameStateTableEvent', stateIs(table, 2), since, 10000);
  const m = owner.mark();
  joiner.resign(table);
  await owner.waitFor('dsgGameStateTableEvent', stateIs(table, 1), m);
}

async function declineThenReRequestRefused() {
  const all = await guests(3);
  const [alice, bob, dave] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    await requestRefused(bob, t, 'DUPLICATE');

    // R9: a non-owner decline is ignored and answered with an empty snapshot
    const md = dave.mark();
    dave.decline(t, bob.name);
    await dave.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), md);

    const mb = bob.mark();
    const ma = alice.mark();
    alice.decline(t, bob.name);
    const ended = await bob.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'DECLINED'), mb);
    if (ended.owner !== alice.name || ended.player !== bob.name) {
      throw new Error(`bad DECLINED notice ${JSON.stringify(ended)}`);
    }
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), ma);
    await requestRefused(bob, t, 'BLOCKED');
  } finally {
    closeAll(all);
  }
}

async function withdrawThenReRequestRefused() {
  const all = await guests(2);
  const [alice, bob] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    const mb = bob.mark();
    const ma = alice.mark();
    bob.withdraw(t);
    await bob.waitFor('dsgArenaMyRequestsEvent', mineIs([]), mb);
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), ma);
    await requestRefused(bob, t, 'BLOCKED');
  } finally {
    closeAll(all);
  }
}

async function acceptCancelsOtherRequests() {
  const all = await guests(4);
  const [alice, carol, bob, dave] = all;
  try {
    const tA = await alice.createTable();
    const tC = await carol.createTable();
    await requestOk(bob, alice, tA, [bob.name]);
    await requestOk(bob, carol, tC, [bob.name]);
    await requestOk(dave, alice, tA, [bob.name, dave.name]);
    const mb = bob.mark();
    const mc = carol.mark();
    const md = dave.mark();
    await acceptAndJoin(alice, bob, tA);
    await dave.waitFor('dsgArenaRequestEndedEvent', endedIs(tA, 'TABLE_FULL'), md);
    await carol.waitFor('dsgArenaJoinRequestsEvent', snapIs(tC, []), mc);
    await bob.waitFor('dsgArenaMyRequestsEvent', mineIs([]), mb);
    await sleep(500);
    if (bob.since(mb, 'dsgArenaRequestEndedEvent').length > 0) {
      throw new Error('bob got a notice for removals he caused himself');
    }
  } finally {
    closeAll(all);
  }
}

async function gameEndsThenBlockedPlayerCanRequest() {
  const all = await guests(3);
  const [alice, bob, erin] = all;
  try {
    const t = await alice.createTable();
    await requestOk(erin, alice, t, [erin.name]);
    const me = erin.mark();
    alice.decline(t, erin.name);
    await erin.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'DECLINED'), me);
    await requestRefused(erin, t, 'BLOCKED');

    await requestOk(bob, alice, t, [bob.name]);
    const ma = alice.mark();
    await acceptAndJoin(alice, bob, t);
    await playUntilOver(alice, bob, t, ma);

    const mo = alice.mark();
    bob.exitTable(t);
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), mo);   // R6b
    await requestOk(erin, alice, t, [erin.name]);
  } finally {
    closeAll(all);
  }
}

// R2 boot check: the boot reopens the table (R6b) but bootTimes survives it
async function bootedPlayerIsRefused() {
  const all = await guests(3);
  const [alice, bob, erin] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    const ma = alice.mark();
    await acceptAndJoin(alice, bob, t);
    await playUntilOver(alice, bob, t, ma);

    const mo = alice.mark();
    alice.boot(t, bob.name);
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), mo);   // R6b
    const mb = bob.mark();
    await requestRefused(bob, t, 'BOOTED');
    const ended = bob.since(mb, 'dsgArenaRequestEndedEvent')[0];
    if (ended.owner !== alice.name || ended.player !== bob.name) {
      throw new Error(`bad BOOTED notice ${JSON.stringify(ended)}`);
    }
    await sleep(500);
    if (alice.since(mo, 'dsgArenaJoinRequestsEvent').some((b) => b.table === t && names(b).length > 0)) {
      throw new Error('the owner saw a request from a booted player');
    }
    await requestOk(erin, alice, t, [erin.name]);
  } finally {
    closeAll(all);
  }
}

async function ownerLeavesFinishedTable() {
  const all = await guests(3);
  const [alice, bob, erin] = all;
  try {
    const t = await alice.createTable();
    await requestOk(erin, alice, t, [erin.name]);
    const me = erin.mark();
    alice.decline(t, erin.name);
    await erin.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'DECLINED'), me);

    await requestOk(bob, alice, t, [bob.name]);
    const ma = alice.mark();
    await acceptAndJoin(alice, bob, t);
    await playUntilOver(alice, bob, t, ma);

    const mb = bob.mark();
    alice.exitTable(t);
    await bob.waitFor('dsgOwnerTableEvent', (b) => b.table === t && b.player === bob.name, mb);
    await bob.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), mb);
    await requestOk(erin, bob, t, [erin.name]);
  } finally {
    closeAll(all);
  }
}

// action 1 = cancel, 2 = resign (DSGForceCancelResignTableEvent)
async function waitingGameEndsThenTableReopens(action) {
  const all = await guests(3);
  const [alice, bob, carol] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    const ma = alice.mark();
    await acceptAndJoin(alice, bob, t);
    await alice.waitFor('dsgGameStateTableEvent', stateIs(t, 2), ma, 10000);
    await requestRefused(carol, t, 'NOT_AVAILABLE');      // two players

    const mw = alice.mark();
    bob.disconnect();
    await alice.waitFor('dsgGameStateTableEvent', stateIs(t, 3), mw);
    await requestRefused(carol, t, 'NOT_AVAILABLE');      // one player, game paused

    // the server waits 1 minute for bob before letting alice decide
    await alice.waitFor('dsgWaitingPlayerReturnTimeUpTableEvent', (b) => b.table === t, mw, 90000);
    const mc = alice.mark();
    alice.forceCancelResign(t, action);
    await alice.waitFor('dsgGameStateTableEvent', stateIs(t, 1), mc);
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), mc);   // R6b
    await requestOk(carol, alice, t, [carol.name]);
  } finally {
    closeAll(all);
  }
}

async function tableCloseEndsRequests() {
  const all = await guests(3);
  const [alice, bob, dave] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    await requestOk(dave, alice, t, [bob.name, dave.name]);
    const mb = bob.mark();
    const md = dave.mark();
    alice.exitTable(t);
    const ended = await bob.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'TABLE_CLOSED'), mb);
    if (ended.owner !== alice.name) throw new Error(`TABLE_CLOSED owner ${ended.owner}`);
    await bob.waitFor('dsgArenaMyRequestsEvent', mineIs([]), mb);
    await dave.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'TABLE_CLOSED'), md);
  } finally {
    closeAll(all);
  }
}

async function requesterDisconnect() {
  const all = await guests(2);
  const [alice, bob] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    const ma = alice.mark();
    bob.disconnect();
    await alice.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), ma);
  } finally {
    closeAll(all);
  }
}

async function ownerExitsRightAfterAccept() {
  const all = await guests(2);
  const [alice, bob] = all;
  try {
    const t = await alice.createTable();
    await requestOk(bob, alice, t, [bob.name]);
    const mb = bob.mark();
    alice.accept(t, bob.name);
    alice.exitTable(t);
    // either the exit lands first (table removed, claim released) or the join
    // does (bob inherits the table, which reopens for him)
    const outcome = await Promise.any([
      bob.waitFor('dsgArenaRequestEndedEvent', endedIs(t, 'TABLE_CLOSED'), mb).then(() => 'closed'),
      bob.waitFor('dsgOwnerTableEvent', (b) => b.table === t && b.player === bob.name, mb).then(() => 'handed over'),
    ]);
    if (outcome === 'handed over') {
      await bob.waitFor('dsgArenaJoinRequestsEvent', snapIs(t, []), mb);
    }
    console.log(`      (outcome: ${outcome})`);
  } finally {
    closeAll(all);
  }
}

async function goneTableIsAnswered() {
  const all = await guests(1);
  const [x] = all;
  const gone = 9999;
  try {
    let m = x.mark();
    x.request(gone);
    const ended = await x.waitFor('dsgArenaRequestEndedEvent', endedIs(gone, 'NOT_AVAILABLE'), m);
    if (ended.owner !== '') throw new Error(`owner for a gone table: ${JSON.stringify(ended.owner)}`);
    await x.waitFor('dsgArenaMyRequestsEvent', mineIs([]), m);
    m = x.mark();
    x.withdraw(gone);
    await x.waitFor('dsgArenaMyRequestsEvent', mineIs([]), m);
    m = x.mark();
    x.accept(gone, 'nobody');
    await x.waitFor('dsgArenaJoinRequestsEvent', snapIs(gone, []), m);
    m = x.mark();
    x.decline(gone, 'nobody');
    await x.waitFor('dsgArenaJoinRequestsEvent', snapIs(gone, []), m);
  } finally {
    closeAll(all);
  }
}

async function waitForServer(timeoutMs = 180000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    try {
      const c = new Client();
      await c.connect();
      c.disconnect();
      return;
    } catch (e) {
      if (Date.now() > deadline) throw new Error(`arena server not reachable on ${HOST}:${PORT}: ${e.message}`);
      await sleep(2000);
    }
  }
}

const scenarios = [
  ['request, duplicate, non-owner decline, decline, re-request refused', declineThenReRequestRefused],
  ['withdraw then re-request refused', withdrawThenReRequestRefused],
  ['accept cancels the requester elsewhere, others see TABLE_FULL', acceptCancelsOtherRequests],
  ['game ends, table reopens, previously blocked player can request', gameEndsThenBlockedPlayerCanRequest],
  ['owner leaves finished table, joiner owns an open table', ownerLeavesFinishedTable],
  ['waiting game cancelled reopens table', () => waitingGameEndsThenTableReopens(1)],
  ['waiting game force-resigned reopens table', () => waitingGameEndsThenTableReopens(2)],
  ['table close ends requests with TABLE_CLOSED', tableCloseEndsRequests],
  ['requester disconnect removes request', requesterDisconnect],
  ['owner exits right after accept', ownerExitsRightAfterAccept],
  ['events for a gone table are answered', goneTableIsAnswered],
  ['booted player is refused after the boot reopens the table', bootedPlayerIsRefused],
];

const only = process.argv[2];
await waitForServer();
let failed = 0;
for (const [name, run] of scenarios) {
  if (only && !name.includes(only)) continue;
  try {
    await run();
    console.log(`PASS  ${name}`);
  } catch (e) {
    failed++;
    console.log(`FAIL  ${name}: ${e.message}`);
  }
}
console.log(failed === 0 ? 'ALL PASS' : `${failed} scenario(s) FAILED`);
process.exit(failed === 0 ? 0 : 1);
```

Scenario ↔ spec E2E mapping: "request → decline → re-request refused" = scenario 1; "withdraw → re-request refused" = 2; "accept → the requester's other requests cancelled, other requesters see `TABLE_FULL`" = 3; "game ends, table open → previously blocked player can request" = 4; "the owner leaves a finished 2-player table → …" = 5; "a waiting set is cancelled or force-resigned → …" = 6 and 7; "table close" = 8; "requester disconnect" = 9; "owner exits right after accept" = 10. Scenario 11 covers Review Focus 4, scenario 12 covers Review Focus 8 (the R2 boot check).

- [ ] **Step 5: Run the E2E**

Run (about 4 minutes; the two waiting-game scenarios each wait out the server's 1-minute return timer): `node /private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs` with a 600000 ms timeout.
Expected: 12 `PASS` lines (scenario 10 also prints its outcome, `closed` or `handed over`; both are valid) and `ALL PASS`, exit code 0.

If anything fails, pull the server side through `ctx_execute`:
`docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml logs --since 15m pente.org 2>&1 | grep -i -E "arena|exception|Invalid table" | tail -80`
and rerun a single scenario with `node /private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs "<name substring>"` after the fix.

- [ ] **Step 6: Workspace check**

Run: `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org status --porcelain`
Expected: only the pre-existing ` M docker-compose-replica.yml`, ` M docker-compose.yml` and `?? CLAUDE.md`; no untracked file from this work (`deploy/` and `deployClasses/` are gitignored). Then `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org log --oneline 1cfe15a..HEAD` lists this plan's commit ("Add arena persistent join requests implementation plan") followed by the 8 commits from Tasks 1-8, each signed (`git -C … log --show-signature -8` shows a good signature).

- [ ] **Step 7: Report**

No commit in this task. Report to the controller: the `ant test` result lines (Step 1), the `ServerTableDrawScoringTest` line (Step 2), the E2E output (Step 5, including scenario 10's outcome), and the `git status` line (Step 6). The E2E driver stays in the scratch directory.
