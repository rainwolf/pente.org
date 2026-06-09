# CacheTourneyStorer → Redis (Aggregate-Root) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Build with `./justCompile` (rsyncs `dsg_src/java/ → deploy/` then compiles — `ant compile` alone builds the STALE `deploy/` copy and will silently test old code). Run tests with `ant compile-tests && ant test-one -Dtest=<FQCN>`.

**Goal:** Make `CacheTourneyStorer` cache tournaments in Redis with `Tourney` as the single source of truth, so the cached tourney and every nested round/section/match stay consistent and survive restarts — without the mutate-without-persist / stale-caller-copy bugs that the parallel `CacheTBStorer` migration exposed.

**Architecture:** `Tourney` is the aggregate root. Only whole `Tourney` objects are stored in Redis (`EID_TO_TOURNEY`, eid→serialized `Tourney` carrying its rounds→sections→matches→players). The upcoming/current/completed lists store **eids only** (`TOURNEY_LIST_*`), resolved through `getTourney`. `EID_TO_TOURNEY_PLAYER_PIDS` is an independent player-pid index. Every mutator loads the canonical tourney, mutates it (or the caller-supplied one), and **persists the whole tourney** back. Per-instance `synchronized(this)` is retained; distributed locking is out of scope.

**Tech Stack:** Java (Tomcat), Ant, JUnit 3.7, `RedisConnectionManager` (UnifiedJedis + in-memory `fallback`), MariaDB via `MySQLTourneyStorer`. Branch `refactor/redis-cache-tourney` (based on `main`, which already contains the TB migration's shared infra: `RedisConnectionManager.hgetAllFields`, `SerializingRedisConnectionManager`, and the pre-staged tourney namespace constants).

**Spec:** `docs/superpowers/specs/2026-06-09-redis-cache-storers-design.md`

---

## Background the engineer must know

`CacheTourneyStorer` (`dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`, 705 lines) wraps `MySQLTourneyStorer` (`backingStorer`). It synchronizes on **`this`** (no separate lock object). Current in-memory state:
- `tournies` `Map<Integer,Tourney>` — the deep cache (full graph), keyed by eid.
- `upcomingTournies` / `currentTournies` / `completedTournies` `List<Tourney>` — **shallow** Tourney stubs (eid+name+dates only), re-hydrated through `getTourney`.
- `tourneyPlayerPids` `Map<Integer,List<Long>>` — registered pids per eid.
- `listeners` `List<TourneyListener>`, `timers` `List<Timer>` — runtime callbacks + start schedulers (NOT cache state; keep in-memory).

**Object graph (verified):** `Tourney implements Serializable` (serialVersionUID) holding `List<TourneyRound> rounds`, `List<Long> directors`, `int status`, `int eventID`, `String name`, dates, `List<Restriction>`, `int[][] alreadyPlayed`. `TourneyRound`(Serializable, back-ref `Tourney tourney`) → `List<TourneySection>`. `TourneySection`(abstract, Serializable, back-ref `TourneyRound round`, `List<TBSet> sets`) subclasses hold `List<TourneyPlayerData> players` + `List<TourneyMatch> matches`. `TourneyMatch`(Serializable) holds `TourneyPlayerData player1/player2` (object refs, shared across matches), `int result/round/section/seq`, `long matchID/gid`; NO back-ref (uses int indices). Cycles + shared `TourneyPlayerData` identity round-trip correctly **within one serialized Tourney blob**.

**Two gotchas unique to tourney:**
1. **`Tourney.format` is `transient`** and rebuilt in `Tourney.readObject()` from `formatType`. `RedisConnectionManager.serialize/deserialize` use `ObjectOutputStream/ObjectInputStream`, which invoke `readObject`, so `format` is rebuilt on load — but this is load-bearing and MUST be covered by a test (Task 2).
2. **`TourneySection.sets` is `List<TBSet>`** — a latent cross-aggregate landmine (would drag the whole TBSet/TBGame graph into a Tourney blob). Empty today (`addSet` commented out). **Decision: mark it `transient`** (Task 3).

**Hazard class (same as TB), confirmed present:**
- *Internal mutate-without-persist:* `updateMatchOnly` (`s.init()`/`s.addMatch`), `checkRoundStatus`→`Tourney.createNextRound` (adds a round; only match *rows* persist via `insertMatch`), `startTournament` (`setStatus('S')` never persisted), `createFirstRound`.
- *Caller read-after-mutate:* **`dsg_src/httpdocs/gameServer/admin/manageTourney.jsp`** — `getTourney` → `forfeitPlayers`/`createFirstRound` (mutates aggregate) → `updateMatches`/`insertRound` → then **reads** `tourney.isComplete()`/`getLastRound()`/`getPlayers()`. **Safe** callers (verified, no change needed): `CacheTBStorer.storeGameDSG`, `server/TournamentServer`, `server/TournamentServerTable`, `server/ServerTable` (all mutate→updateMatch with no read-after, or re-fetch fresh).

**Redis namespaces (already declared in `RedisConnectionManager`):** `EID_TO_TOURNEY="eid:tourney"`, `EID_TO_TOURNEY_PLAYER_PIDS="eid:tourney_player_pids"`, `TOURNEY_LIST_UPCOMING/CURRENT/COMPLETED`. Helpers available: `hput(String,int,T extends Serializable)`, `<T> T hget(String,int)` (null on miss), `hexists(String,int)`, `hremove(String,int)`, `invalidate(String)`, `<T> List<T> hgetAllValues(String)`, `<T> List<T>`/`List<String> hgetAllFields(String)`. Test fixture `org.pente.turnBased.test.SerializingRedisConnectionManager` (public; reuse directly — it round-trips through bytes so tests have teeth, unlike the raw-reference production fallback).

**Decisions (from brainstorming):**
- **Caller contract:** mutators that callers read after (notably `updateMatches`) **operate on the caller's passed `Tourney`** and persist it (acceptInvite-style), so `manageTourney.jsp`'s reads-after work without a JSP rewrite.
- **`TourneySection.sets`:** mark `transient`.

---

## File Structure

- **Modify:** `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java` — replace in-memory `tournies`/lists/`tourneyPlayerPids` with Redis access; add `persistTourney`/index helpers; convert every mutator to persist; `updateMatch` re-find-and-apply; `updateMatches` operate-on-passed.
- **Modify:** `dsg_src/java/org/pente/gameServer/tourney/TourneySection.java` — mark `sets` field `transient`.
- **Create:** `dsg_src/java/org/pente/gameServer/tourney/test/InMemoryTourneyStorer.java` — in-memory `TourneyStorer` mock for tests (deep-copy semantics).
- **Create:** `dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java` — JUnit 3.7 regression tests.
- **Modify:** `build.xml` — register `CacheTourneyStorerRedisTest` in the `test` target.
- **Verify only (no change expected):** `admin/manageTourney.jsp` — its reads-after must pass once `updateMatches`/`setInitialSeeds` mutate-the-passed-tourney + persist.

---

### Task 1: Test fixture — in-memory backing storer + serializing-Redis test

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/tourney/test/InMemoryTourneyStorer.java`
- Create: `dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java`

- [ ] **Step 1: Read the interface + constructor.** Read `dsg_src/java/org/pente/gameServer/tourney/TourneyStorer.java` (full method list) and `CacheTourneyStorer.java` lines 1-115 (fields + constructor + `addTourneyListener`/`flushCache`) so the mock implements every interface method and the test constructs `CacheTourneyStorer` correctly. Note the constructor signature and which collaborators (`backingStorer`, `dsgPlayerStorer`, `tbStorer`, `notificationServer`, `kothStorer`) it takes.

- [ ] **Step 2: Write `InMemoryTourneyStorer`.** Implement every `TourneyStorer` method. Real behavior only for what the tests exercise; everything else returns `null`/`0`/empty `ArrayList` (a complete instruction, not a placeholder). Use serialize-clone to mimic Redis (reuse the helper `org.pente.turnBased.test.RedisConnectionManagerCloneHelper.clone`).

```java
package org.pente.gameServer.tourney.test;

import org.pente.gameServer.tourney.*;
import org.pente.turnBased.test.RedisConnectionManagerCloneHelper;
import java.util.*;

/** Minimal in-memory TourneyStorer for unit tests. Stores/returns deep copies
 *  so the cache layer can never accidentally share instances with the backing
 *  store (mirrors Redis serialize/deserialize). */
public class InMemoryTourneyStorer implements TourneyStorer {

    private final Map<Integer, Tourney> tournies = new HashMap<Integer, Tourney>();
    private final Map<Integer, List<Long>> playerPids = new HashMap<Integer, List<Long>>();
    private final List<TourneyMatch> updatedMatches = new ArrayList<TourneyMatch>();

    private static Tourney copy(Tourney t) {
        return t == null ? null : (Tourney) RedisConnectionManagerCloneHelper.clone(t);
    }

    public void insertTourney(Tourney t) { tournies.put(t.getEventID(), copy(t)); }
    public Tourney getTourney(int eid) { return copy(tournies.get(eid)); }
    public Tourney getTourneyDetails(int eid) { return copy(tournies.get(eid)); }

    public void updateMatch(TourneyMatch m) { updatedMatches.add((TourneyMatch) RedisConnectionManagerCloneHelper.clone(m)); }
    public void addPlayerToTourney(long pid, int eid) {
        List<Long> l = playerPids.get(eid);
        if (l == null) { l = new ArrayList<Long>(); playerPids.put(eid, l); }
        if (!l.contains(pid)) l.add(pid);
    }
    public void removePlayerFromTourney(long pid, int eid) {
        List<Long> l = playerPids.get(eid);
        if (l != null) l.remove(pid);
    }
    public List<Long> getTourneyPlayerPids(int eid) {
        List<Long> l = playerPids.get(eid);
        return l == null ? new ArrayList<Long>() : new ArrayList<Long>(l);
    }

    /** test accessor */
    public List<TourneyMatch> getUpdatedMatches() { return updatedMatches; }

    // ... implement remaining TourneyStorer methods as no-op defaults ...
}
```

> The exact set of "remaining" methods comes from Step 1. Provide trivial defaults for: `getUpcomingTournies/getCurrentTournies/getCompletedTournies` (empty list), `getUnplayedMatch` (null), `insertRound/insertMatch/updateMatches/completeTourney/assignCrown/removeCrown/cancelTourney/setInitialSeeds/getTourneyPlayers/findNextTournamentName/addTourneyListener/removeTourneyListener` (no-op / null / empty). Match each signature exactly from the interface.

- [ ] **Step 3: Write the failing test.** Construct a `CacheTourneyStorer` over the mock with a `SerializingRedisConnectionManager` installed, and assert the **player-add persists to the cache** (the simplest aggregate-independent mutation).

```java
package org.pente.gameServer.tourney.test;

import junit.framework.TestCase;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.gameServer.tourney.*;
import org.pente.turnBased.test.SerializingRedisConnectionManager;

public class CacheTourneyStorerRedisTest extends TestCase {

    private InMemoryTourneyStorer backing;
    private CacheTourneyStorer cache;

    public CacheTourneyStorerRedisTest(String name) { super(name); }

    protected void setUp() throws Exception {
        super.setUp();
        RedisConnectionManager.setInstance(new SerializingRedisConnectionManager());
        RedisConnectionManager.getInstance().invalidate(RedisConnectionManager.EID_TO_TOURNEY);
        RedisConnectionManager.getInstance().invalidate(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS);
        backing = new InMemoryTourneyStorer();
        // Construct with the real constructor signature found in Step 1; pass
        // lightweight stubs / null for collaborators not exercised here. If a
        // constructor side effect (setupTBTournaments / timer) NPEs on nulls,
        // prefer a stub over changing production code; report if unavoidable.
        cache = makeCache(backing);
    }

    protected void tearDown() throws Exception {
        if (cache != null) cache.destroy();
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    /** Build a minimal valid Tourney (eid set) and register it. */
    private static Tourney newTourney(int eid) {
        Tourney t = new Tourney();
        t.setEventID(eid);
        t.setName("T" + eid);
        return t;
    }

    public void testAddPlayerPersistsToPlayerPidIndex() throws Exception {
        Tourney t = newTourney(900);
        cache.insertTourney(t);
        cache.addPlayerToTourney(1001L, 900);
        cache.addPlayerToTourney(1002L, 900);

        java.util.List<Long> pids = cache.getTourneyPlayerPids(900);
        assertTrue("pid 1001 must be registered", pids.contains(1001L));
        assertTrue("pid 1002 must be registered", pids.contains(1002L));
    }
}
```

> `makeCache(backing)` is a helper YOU write in the test from the constructor signature found in Step 1 (e.g. `new CacheTourneyStorer(backing, dsgPlayerStorerStub, ...)`). If `Tourney` has no no-arg constructor or `setEventID` differs, adjust to the real API found while reading `Tourney.java`.

- [ ] **Step 4: Run — expect FAIL.** `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest`. Expected: FAIL (NPE or wrong result) — the current code uses the in-memory `tourneyPlayerPids` map and `SerializingRedisConnectionManager` is unused, so the Redis-backed read returns nothing. This proves the test detects the gap. (If the constructor can't be built with stubs, resolve that first and report.)

- [ ] **Step 5: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/test/InMemoryTourneyStorer.java \
        dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java
git commit -m "test: failing fixture + in-memory backing storer for CacheTourneyStorer Redis migration"
```

---

### Task 2: `format`-survives-deserialize guard test

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java`

Because `Tourney.format` is `transient` and rebuilt in `readObject`, a Redis round-trip must still yield a usable `format`. Lock this with a test before relying on it.

- [ ] **Step 1: Read** `Tourney.java` for `getFormat()`/`getFormatType()`/`readObject` and how `format` is rebuilt, so the assertion checks the right thing (e.g. `getFormat() != null` and `getFormatType()` preserved).

- [ ] **Step 2: Add the test.**

```java
    public void testTourneyFormatSurvivesRedisRoundTrip() throws Exception {
        Tourney t = newTourney(901);
        // set a real formatType so readObject can rebuild a format (use the
        // constant the codebase uses, found while reading Tourney.java).
        t.setFormatType(Tourney.SINGLE_ELIMINATION);   // adjust constant name to actual
        cache.insertTourney(t);

        Tourney loaded = cache.getTourney(901);
        assertEquals("formatType preserved", Tourney.SINGLE_ELIMINATION, loaded.getFormatType());
        assertNotNull("transient format must be rebuilt on deserialize", loaded.getFormat());
    }
```

> Replace `Tourney.SINGLE_ELIMINATION` with the real constant/value. If `getFormat()` isn't public, assert on observable behavior that depends on `format` (e.g. a method that uses it) instead. This test currently fails to even reach Redis until Task 3 routes `getTourney` through Redis — so it's allowed to fail now for the same reason as Task 1, OR write it to call `RedisConnectionManager.serialize`/`deserialize` directly on a Tourney to isolate the format concern. PREFER the direct serialize/deserialize form so this test is meaningful immediately:

```java
    public void testTourneyFormatSurvivesRedisRoundTrip() throws Exception {
        Tourney t = newTourney(901);
        t.setFormatType(Tourney.SINGLE_ELIMINATION);
        byte[] bytes = RedisConnectionManager.serialize(t);
        Tourney loaded = (Tourney) RedisConnectionManager.deserialize(bytes);
        assertEquals(Tourney.SINGLE_ELIMINATION, loaded.getFormatType());
        assertNotNull("transient format must be rebuilt on deserialize", loaded.getFormat());
    }
```

- [ ] **Step 3: Run — expect PASS** (standard Java serialization invokes `readObject`). `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest`. If it FAILS, the migration is blocked — `format` is lost across Redis; STOP and report (we'd need a custom serializer). This is a go/no-go gate.

- [ ] **Step 4: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java
git commit -m "test: guard that transient Tourney.format survives Redis serialization"
```

---

### Task 3: Mark `TourneySection.sets` transient + add `persistTourney` helpers

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/TourneySection.java`
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`

- [ ] **Step 1: Make `sets` transient.** In `TourneySection.java` find `List<TBSet> sets` (declared with an initializer like `= new ArrayList<TBSet>()`) and add `transient`: `private transient List<TBSet> sets = new ArrayList<TBSet>();`. Because it's transient, after deserialize it is `null` — guard any reader: find `getSets()`/`addSet()` and make `getSets()` lazily initialize (`if (sets == null) sets = new ArrayList<TBSet>(); return sets;`) so a deserialized section never NPEs. (Readers are rare — `addSet` is commented out — but the lazy-init is defensive.)

- [ ] **Step 2: Add the write primitive + helpers** to `CacheTourneyStorer` (place near the top, after the fields). `pente_cache` does not yet exist on this class — add a field `private final RedisConnectionManager pente_cache = RedisConnectionManager.getInstance();` (import `org.pente.gameServer.server.RedisConnectionManager`).

```java
    /** THE write primitive: persist a tourney as the single source of truth. */
    private void persistTourney(Tourney t) {
        if (t == null) return;
        pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY, t.getEventID(), t);
    }

    /** Add an eid to one of the three ordered list namespaces (no duplicates). */
    private void addToList(String namespace, int eid) {
        java.util.List<Integer> eids = pente_cache.hget(namespace, eid);   // see note
        // NOTE: lists are stored as a single value under a fixed field; use a
        // dedicated helper below instead of per-eid fields.
    }
```

> The three `TOURNEY_LIST_*` namespaces hold an **ordered list of eids**. Store the whole list under a single fixed field key (e.g. field `"list"`), so reads/writes are one value. Implement:

```java
    private static final String LIST_FIELD = "list";

    @SuppressWarnings("unchecked")
    private java.util.List<Integer> readEidList(String namespace) {
        java.util.ArrayList<Integer> l =
                pente_cache.hget(namespace, LIST_FIELD);
        return l == null ? new java.util.ArrayList<Integer>() : l;
    }
    private void writeEidList(String namespace, java.util.List<Integer> eids) {
        pente_cache.hput(namespace, LIST_FIELD, new java.util.ArrayList<Integer>(eids));
    }
```

> `hget(String,String)` / `hput(String,String,T)` overloads exist (string field key). `ArrayList<Integer>` is Serializable. This keeps each list a single serialized value.

- [ ] **Step 3: Compile.** `./justCompile` → BUILD SUCCESSFUL. (No test change yet; existing tests from Tasks 1-2 still in their current pass/fail state.)

- [ ] **Step 4: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/TourneySection.java \
        dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java
git commit -m "feat: mark TourneySection.sets transient; add persistTourney + eid-list helpers"
```

---

### Task 4: Route reads through Redis — `getTourney`, the three lists, player-pid index

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`

Read each method's current body first (they shifted from the line numbers below).

- [ ] **Step 1: `getTourney(int eid)`** (~227-241) — read `EID_TO_TOURNEY`, fall through to `backingStorer.getTourney` + cache:

```java
    public Tourney getTourney(int eid) {
        Tourney t = pente_cache.hget(RedisConnectionManager.EID_TO_TOURNEY, eid);
        if (t == null) {
            t = backingStorer.getTourney(eid);
            if (t != null) persistTourney(t);
        }
        return t;
    }
```
Keep `getTourneyDetails` delegating to `getTourney` (verify it does).

- [ ] **Step 2: `getTourneyPlayerPids(int eid)`** (~286-297) — back by `EID_TO_TOURNEY_PLAYER_PIDS`:

```java
    public java.util.List<Long> getTourneyPlayerPids(int eid) {
        java.util.ArrayList<Long> pids =
                pente_cache.hget(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid);
        if (pids == null) {
            java.util.List<Long> loaded = backingStorer.getTourneyPlayerPids(eid);
            pids = new java.util.ArrayList<Long>(loaded);
            pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid, pids);
        }
        return new java.util.ArrayList<Long>(pids);
    }
```

- [ ] **Step 3: the three list getters** (`getUpcomingTournies` ~118, `getCurrentTournies` ~137, `getCompletedTournies` ~157) — back each by its `TOURNEY_LIST_*` namespace storing **eids**, resolved via `getTourney`. On a cold list (empty eid-list AND a one-time `loaded` flag false), bootstrap from `backingStorer`. Keep the existing per-getter side effects (the upcoming→current promotion in `getUpcomingTournies`, the `checkRoundStatus`/end handling in `getCurrentTournies`) but operate on the Redis-resolved tournies and persist any state changes. Pattern for `getUpcomingTournies`:

```java
    public java.util.List<Tourney> getUpcomingTournies() {
        java.util.List<Integer> eids = ensureListLoaded(
                RedisConnectionManager.TOURNEY_LIST_UPCOMING, /*loaderTag*/ "upcoming");
        java.util.List<Tourney> out = new java.util.ArrayList<Tourney>();
        java.util.List<Integer> promote = new java.util.ArrayList<Integer>();
        for (Integer eid : new java.util.ArrayList<Integer>(eids)) {
            Tourney t = getTourney(eid);
            if (t == null) continue;
            if (/* start date passed -> belongs in current */ startDatePassed(t)) {
                promote.add(eid);
            } else {
                out.add(t);
            }
        }
        if (!promote.isEmpty()) {
            eids.removeAll(promote);
            writeEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING, eids);
            java.util.List<Integer> cur = readEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT);
            for (Integer eid : promote) if (!cur.contains(eid)) cur.add(eid);
            writeEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT, cur);
        }
        return out;
    }
```

> Implement `ensureListLoaded(namespace, tag)`: if a one-time in-JVM `volatile boolean` for that list is false, load the eids from the matching `backingStorer.getXxxTournies()` (map each returned shallow tourney to its eid via `getEventID()`, `persistTourney`-ing the full tourney via `getTourney` is NOT needed here — just record eids), `writeEidList`, set the flag true; then `return readEidList(namespace)`. Extract `startDatePassed(t)` from the existing date comparison in the current `getUpcomingTournies` (copy the exact condition). Apply the analogous structure to `getCurrentTournies` (resolve eids → getTourney → run the existing `checkRoundStatus(t)` and end-handling, persisting via the mutators in Task 6) and `getCompletedTournies` (resolve eids → getTourney, read-only).

- [ ] **Step 4: Compile + run the Task 1 test — now EXPECT the player-pid test to behave.** After Steps 1-2, `getTourneyPlayerPids` reads Redis but `addPlayerToTourney` (Task 5) hasn't been converted yet, so `testAddPlayerPersistsToPlayerPidIndex` may still FAIL (the add writes the old in-memory map). That's expected; it goes green in Task 5. `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest`. Confirm the `format` test (Task 2, direct-serialize form) still PASSES.

- [ ] **Step 5: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java
git commit -m "feat: route CacheTourneyStorer reads (getTourney, lists, player pids) through Redis"
```

---

### Task 5: Tourney-level mutators persist (insert/complete/add-remove-player/seeds/crowns/cancel)

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`

Read each method first. Rule: after mutating, `persistTourney(t)` (and write the relevant eid-list / pid-index), then delegate to `backingStorer`.

- [ ] **Step 1: `insertTourney(Tourney t)`** (~110) — persist + add to the upcoming eid-list:

```java
    public void insertTourney(Tourney t) {
        backingStorer.insertTourney(t);
        persistTourney(t);
        java.util.List<Integer> up = readEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        if (!up.contains(t.getEventID())) { up.add(t.getEventID()); writeEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING, up); }
    }
```
(Preserve the `insertTourney(Tourney, Resources)` overload's timer scheduling; just route its caching through the above.)

- [ ] **Step 2: `addPlayerToTourney`/`removePlayerFromTourney`** (~243 / ~262) — write the pid index back, keep `backingStorer` + `notifyListeners`:

```java
    public void addPlayerToTourney(long pid, int eid) {
        backingStorer.addPlayerToTourney(pid, eid);
        java.util.List<Long> pids = getTourneyPlayerPids(eid);  // loads/caches
        if (!pids.contains(pid)) pids.add(pid);
        pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid, new java.util.ArrayList<Long>(pids));
        notifyListeners(/* PLAYER_REGISTER event as today */);
    }
```
(Mirror for `removePlayerFromTourney` with `pids.remove(pid)` and the PLAYER_DROP event. Copy the exact `notifyListeners(...)` argument from the current code.)

- [ ] **Step 3: `completeTourney(Tourney t)`** (~165) — it mutates `t.setEndDate(...)`; persist the tourney, move its eid from current→completed lists, keep crown reassignment + `startAnotherTourney`:

```java
    public void completeTourney(Tourney t) {
        t.setEndDate(new java.util.Date());
        backingStorer.completeTourney(t);
        persistTourney(t);
        moveEid(RedisConnectionManager.TOURNEY_LIST_CURRENT, RedisConnectionManager.TOURNEY_LIST_COMPLETED, t.getEventID());
        // ... existing crown reassignment + startAnotherTourney + notifyListeners(COMPLETE) ...
    }
```
Add helper `moveEid(fromNs, toNs, eid)`: remove from `from` list, add to `to` list (dedup), write both.

- [ ] **Step 4: `setInitialSeeds`, `assignCrown`, `removeCrown`, `cancelTourney`** — for each: read the current body; if it mutates the cached `Tourney` graph, `persistTourney` after the mutation; if it only writes player/crown DB state not held in the Tourney blob, leave the Redis tourney untouched but `pente_cache.invalidate`/refresh as the old code did. Specifically: `cancelTourney(eid)` (~690) currently `flushCache`s — replace with removing the eid from all three lists + `hremove(EID_TO_TOURNEY, eid)` + `hremove(EID_TO_TOURNEY_PLAYER_PIDS, eid)`. `setInitialSeeds` (~325) removes disqualified players (calls `removePlayerFromTourney`, now Redis-backed) and delegates to backing — persist the tourney if it mutates rounds; otherwise no tourney persist needed.

- [ ] **Step 5: `flushCache`** (~74) — invalidate the Redis namespaces instead of clearing maps:

```java
    public void flushCache() {
        pente_cache.invalidate(RedisConnectionManager.EID_TO_TOURNEY);
        pente_cache.invalidate(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_CURRENT);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_COMPLETED);
        // reset the one-time list-loaded flags from Task 4 so next read re-bootstraps
    }
```

- [ ] **Step 6: Run — `testAddPlayerPersistsToPlayerPidIndex` now GREEN.** `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest` → all current tests PASS. Add a `testCompleteTourneyMovesEidToCompletedList` test (insert → put eid in current → `completeTourney` → assert eid absent from `getCurrentTournies` eid-list and present in completed). Keep it green.

- [ ] **Step 7: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java
git commit -m "feat: tourney-level mutators persist to Redis (insert/complete/players/seeds/crowns/cancel)"
```

---

### Task 6: Round/match core — `updateMatch` (re-find + apply), `updateMatches` (operate-on-passed), round creation

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`

This is the highest-risk task — the nested-mutation analog of TB's `storeNewMove`. Read `updateMatch`/`updateMatchOnly`/`updateMatches`/`insertMatch`/`insertRound`/`checkRoundStatus`/`startTournament` fully first, plus `TourneyRound.getSection`, `TourneySection.getUnplayedMatch`/`getMatches`/`init`, and `TourneyMatch` getters/setters for `matchID`/`gid`/`result`/`forfeit`/`round`/`section`.

- [ ] **Step 1: `updateMatch(TourneyMatch match)`** (~416) — the caller passes a DETACHED match (from a deserialized tourney). Load the canonical tourney, re-find the canonical match, copy the caller's fields onto it, recompute, persist:

```java
    public void updateMatch(TourneyMatch match) {
        backingStorer.updateMatch(match);                 // DB write by matchID (unchanged)
        synchronized (this) {
            Tourney t = getTourney(match.getEvent());
            if (t != null) {
                TourneySection s = t.getRound(match.getRound()).getSection(match.getSection());
                TourneyMatch canonical = findMatch(s, match);   // by matchID
                if (canonical != null) {
                    canonical.setGid(match.getGid());
                    canonical.setResult(match.getResult());
                    canonical.setForfeit(match.isForfeit());
                }
                s.init();                                  // recompute section state
                persistTourney(t);
                checkRoundStatus(t);                       // may create next round / complete (Task 6 Step 3)
            }
        }
    }

    private static TourneyMatch findMatch(TourneySection s, TourneyMatch like) {
        for (TourneyMatch m : s.getMatches()) {
            if (m.getMatchID() == like.getMatchID()) return m;
        }
        return null;
    }
```
> Confirm the exact getters: `getEvent()`/`getRound()`/`getSection()`/`getMatchID()`/`isForfeit()` on `TourneyMatch`; `getRound(int)`/`getSection(int)` on `Tourney`/`TourneyRound`; `getMatches()`/`init()` on `TourneySection`. If `s.init()` already rederives results from the matches' state, setting the fields first (above) is what makes it correct. `checkRoundStatus` must persist any round it creates (Step 3).

- [ ] **Step 2: `updateMatches(List<TourneyMatch> matches, Tourney tourney)`** (~400) — DECISION: operate on the caller's passed `tourney` so `manageTourney.jsp`'s reads-after work. Apply each match into the passed tourney, recompute, persist the passed tourney:

```java
    public void updateMatches(java.util.List<TourneyMatch> matches, Tourney tourney) {
        for (TourneyMatch match : matches) {
            backingStorer.updateMatch(match);
            TourneySection s = tourney.getRound(match.getRound()).getSection(match.getSection());
            TourneyMatch canonical = findMatch(s, match);
            if (canonical != null) {
                canonical.setGid(match.getGid());
                canonical.setResult(match.getResult());
                canonical.setForfeit(match.isForfeit());
            }
            s.init();
        }
        persistTourney(tourney);          // persist the caller's object (now canonical)
        checkRoundStatus(tourney);        // mutates `tourney` in place + persists (Step 3)
    }
```
> Because we mutate and persist the **passed** `tourney`, `manageTourney.jsp`'s subsequent `tourney.isComplete()/getLastRound()` reads reflect the new state. No JSP change needed (verified in Task 8).

- [ ] **Step 3: `checkRoundStatus(Tourney t)`** (~470) and round creation — when it calls `t.createNextRound(...)` / `createFirstRound` / `insertRound`, persist the tourney after the round is added:

```java
    private void checkRoundStatus(Tourney t) {
        // ... existing logic deciding round complete ...
        if (/* round complete and not last */ ...) {
            TourneyRound r = t.createNextRound(dsgPlayerStorer);   // mutates t (addRound)
            persistTourney(t);                                     // <-- NEW: persist the new round
            insertRound(r);                                        // DB match rows (existing)
        } else if (/* tourney over */ ...) {
            completeTourney(t);                                    // already persists (Task 5)
        }
    }
```
> Apply the same `persistTourney(t)` after `createFirstRound` in `startTournament` (~521), and set+persist status there: `t.setStatus('S'); persistTourney(t);` (the `setStatus('S')` that is currently never persisted). `insertRound`/`insertMatch` keep writing DB rows; add `persistTourney` of the owning tourney if they mutate the in-graph round/section (they build the round on the live tourney — persist after).

- [ ] **Step 4: Tests.** Add two tests:
  - `testUpdateMatchAppliesResultToCanonicalTourney`: build a Tourney with one round/section/one match (via the real `Tourney`/round/section builders — read how `manageTourney.jsp`/`startTournament` build them, or use `backingStorer` + `getTourney`); `insertTourney`; obtain a DETACHED match via `getUnplayedMatch` (or `getTourney(eid).getRound(0).getSection(0).getMatches().get(0)`); `match.setResult(1); match.setGid(555); updateMatch(match)`; reload `getTourney(eid)` and assert the canonical match has `result==1 && gid==555`. This is RED before this task's edit (canonical copy untouched), GREEN after.
  - `testUpdateMatchesPersistsPassedTourney`: pass a `tourney` + a mutated match list to `updateMatches`; assert the SAME passed `tourney` object reflects the applied result AND a fresh `getTourney(eid)` agrees.
  Run `./justCompile && ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest` → all GREEN.

- [ ] **Step 5: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java dsg_src/java/org/pente/gameServer/tourney/test/CacheTourneyStorerRedisTest.java
git commit -m "feat: updateMatch re-finds+applies to canonical tourney; updateMatches mutates passed tourney; round creation persists"
```

---

### Task 7: Delete the in-memory caches

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java`

- [ ] **Step 1: Grep** for `tournies`, `upcomingTournies`, `currentTournies`, `completedTournies`, `tourneyPlayerPids` to inventory remaining references after Tasks 4-6.

- [ ] **Step 2: Delete the field declarations** for `tournies`, `upcomingTournies`, `currentTournies`, `completedTournies`, `tourneyPlayerPids` and any now-dead private helpers that only touched them. Keep `listeners` and `timers` (runtime state, not cache). Keep the one-time list-loaded `volatile boolean` flags from Task 4.

- [ ] **Step 3: Compile — the compiler is the checklist.** `./justCompile` → BUILD SUCCESSFUL with zero references to the deleted fields. Fix any stragglers (convert remaining readers to the Redis helpers).

- [ ] **Step 4: Run full suite** → GREEN. `ant compile-tests && ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest`.

- [ ] **Step 5: Commit.**

```bash
git add dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java
git commit -m "refactor: remove in-memory tourney caches; CacheTourneyStorer is fully Redis-backed"
```

---

### Task 8: Verify caller read-after-mutate sites + register test

**Files:**
- Verify: `dsg_src/httpdocs/gameServer/admin/manageTourney.jsp`
- Modify: `build.xml`

- [ ] **Step 1: Re-read `manageTourney.jsp`** around the forfeit/seed flows. Confirm the pattern is: `Tourney tourney = tourneyStorer.getTourney(eid); ... tourneyStorer.updateMatches(list, tourney); ... tourney.isComplete()/getLastRound()`. Because `updateMatches` now mutates the **passed** `tourney` and persists it (Task 6 Step 2), and `setInitialSeeds`→`createFirstRound`→`insertRound` now persist (Tasks 5/6), the JSP's reads-after see the new state with NO JSP change. If the JSP instead re-assigns `tourney` from a different call, or reads a freshly-fetched tourney, confirm consistency. If a read-after still can't be satisfied by operate-on-passed (e.g. the JSP reads a tourney it did NOT pass to the mutator), add a single `tourney = tourneyStorer.getTourney(eid);` refresh before the reads and note it. Report findings; only edit the JSP if a genuine stale-read remains.

- [ ] **Step 2: Register the test** in `build.xml`'s `test` target, mirroring the sibling `<java classname="junit.textui.TestRunner" ...>` entries exactly (same `classpath refid`, an `<arg value="org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest"/>`).

- [ ] **Step 3: Full regression compile** — `./justCompile && ant compile-tests` → BUILD SUCCESSFUL (confirms `CacheTBStorer.storeGameDSG`, `server/TournamentServer*`, `server/ServerTable`, `web/TournamentServlet`, and the tourney drivers all still compile against the migrated `CacheTourneyStorer`). Then `ant test-one -Dtest=org.pente.gameServer.tourney.test.CacheTourneyStorerRedisTest` → `OK (N tests)`.

- [ ] **Step 4: Commit.**

```bash
git add build.xml dsg_src/httpdocs/gameServer/admin/manageTourney.jsp
git commit -m "test: register CacheTourneyStorerRedisTest; verify manageTourney.jsp reads-after"
```

---

## Manual / staging verification (real Redis, after merge to a test env)

- Create a tournament (admin/newTourney.jsp), register/drop players, confirm the roster survives a Tomcat restart.
- Start a tournament, play a round to completion, confirm: match results persist, the next round is created and visible, and `getCurrentTournies`/`getCompletedTournies` reflect the transition after restart.
- Run the manageTourney.jsp forfeit flow; confirm forfeited matches + any newly created round persist and the page reflects them.
- Confirm a deserialized Tourney's `format` works (play through a format-dependent operation after a restart).

## Self-Review (completed by author)

- **Spec coverage:** EID_TO_TOURNEY canonical (Tasks 3-6); lists store eids (Task 4); EID_TO_TOURNEY_PLAYER_PIDS index with write-back (Tasks 4-5); every mutator persists (Tasks 5-6); operate-on-passed for `updateMatches` (Task 6, your decision); `TourneySection.sets` transient (Task 3, your decision); serializing-fixture tests + `format` guard (Tasks 1-2); per-JVM `synchronized(this)` retained; fallback relied on for tests.
- **Lessons-learned baked in:** serializing test fixture FIRST (else toothless), authoritative re-find-and-apply in `updateMatch` (TB's move_num lesson), caller-read-after handled via operate-on-passed (TB's acceptInvite lesson), `./justCompile` sync called out in the header and every build step (TB's stale-`deploy/` trap), full internal mutate-without-persist sweep (TB's EndGameRunnable lesson).
- **Placeholders:** test bodies that need real builders/constants are marked with explicit "read X to get the real API" instructions, not vague TODOs; mock's unused methods are explicitly no-op defaults.
- **Type consistency:** `persistTourney`, `readEidList`/`writeEidList`/`addToList`/`moveEid`, `findMatch`, `pente_cache` used consistently; Redis namespace constants match `RedisConnectionManager`.
- **Open risk flagged for execution:** the exact `Tourney`/`TourneyRound`/`TourneySection` builder API for constructing test tournaments (Task 6 tests) must be read from the real classes; and the `Tourney.format`/`getFormat` accessors (Task 2) — both called out inline.
