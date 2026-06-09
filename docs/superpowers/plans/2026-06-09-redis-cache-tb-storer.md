# CacheTBStorer → Redis (Aggregate-Root) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `CacheTBStorer` cache turn-based sets/games in Redis with `TBSet` as the single source of truth, so a set's `TBGame` and the standalone-cached game for the same gid can never diverge.

**Architecture:** `TBSet` is the aggregate root. Only sets are stored in Redis (`SID_TO_TB_SET`); a `TBGame` lives only inside its set blob. A `GID_TO_SID` index resolves `getGame(gid)` → sid → set → `set.getGame(gid)`. Every mutating method follows one invariant: load the canonical set, mutate the in-set game, **persist the whole set**, then delegate to `baseStorer`. Per-JVM `synchronized(cacheTbLock)` is retained; distributed locking is out of scope.

**Tech Stack:** Java (Tomcat), Ant (`build.xml`), JUnit 3.7 (`junit.textui.TestRunner`), `RedisConnectionManager` (UnifiedJedis + in-memory `fallback` map), MariaDB via `MySQLTBGameStorer`.

**Branch:** `refactor/redis-cache-tb` (already checked out).
**Spec:** `docs/superpowers/specs/2026-06-09-redis-cache-storers-design.md`

---

## Background the engineer must know

`CacheTBStorer` (`dsg_src/java/org/pente/turnBased/CacheTBStorer.java`, 1992 lines) today holds four in-memory structures, all guarded by `cacheTbLock`:
- `gamesMap` (`Map<Long,TBGame>`, gid→game)
- `setsMap` (`Map<Long,TBSet>`, sid→set)
- `setsByPid` (`Map<Long,HashSet<Long>>`, pid→sids)
- `waitingSets` (`TreeSet<TBSet>`, sorted by game type + creation date)
- `eidMap` (`Map<Integer,Integer>`, gameType→eventId)

The same `TBGame` instance is shared between `gamesMap` and its `TBSet.games[]`; `cacheSet()` (lines 1160-1177) establishes this by calling `g.setTbSet(set)` then `cacheGame(g)`. Redis serializes sets and games independently, so this sharing must be replaced by "always derive the game from the set".

**Key verified facts:**
- `TBSet`, `TBGame`, `TBMessage` all `implements Serializable`; all fields serialization-safe. The `TBGame.tbSet ↔ TBSet.games[]` cycle round-trips correctly **within one serialized set blob** (Java serialization preserves back-references), so after `set = hget(SID_TO_TB_SET, sid)`, `set.getGame(gid).getTbSet() == set`.
- `TBSet.getSetId()`→`long`, `TBSet.getGames()`→`TBGame[]` (length 2, entries may be null), `TBSet.getGame(long)`→`TBGame`.
- `TBGame.getGid()`→`long`, `TBGame.getSetId()`→`long`, `getTbSet()`/`setTbSet(TBSet)`.
- `RedisConnectionManager`: `hput(String,long,T extends Serializable)`, `<T> T hget(String,long)` (null on miss), `hexists(String,long)`, `hremove(String,long)`, `invalidate(String)`, `<T> List<T> hgetAllValues(String)`. Static `getInstance()`, `setInstance(RedisConnectionManager)`, `resetInstance()`, protected no-arg ctor (jedis=null → uses in-memory `fallback`).
- `MySQLTBGameStorer.loadSetByGid(gid)` returns a `TBSet` with `setId` populated and both games present.
- Tests: JUnit 3.7. Compile `ant compile`; compile tests `ant compile-tests`; run one `ant test-one -Dtest=<FQCN>`. JUnit jar `dsg_src/lib/junit-3.7.jar`.

**Redis namespaces** (constants on `RedisConnectionManager`): existing `SID_TO_TB_SET="sid:tb_set"`, `GID_TO_TB_GAME="gid:tb_game"` (to be repurposed), `PID_TO_TB_SET_IDS="pid:tb_set_ids"`, `EID_TO_TB_EID="eid:tb_eid"`, `PID_TO_TB_VACATION` (already used). This plan **adds** `GID_TO_SID` and `TB_WAITING_SET_IDS`, and stops using `GID_TO_TB_GAME`.

---

## File Structure

- **Modify:** `dsg_src/java/org/pente/gameServer/server/RedisConnectionManager.java` — add two namespace constants (`GID_TO_SID`, `TB_WAITING_SET_IDS`); add `hgetAllFields(String)` helper for set-id index reads.
- **Modify:** `dsg_src/java/org/pente/turnBased/CacheTBStorer.java` — replace the five in-memory structures with Redis-backed access; add `persistSet`/`indexSet`/`unindexSet` helpers; convert all mutators to the write invariant.
- **Modify:** `dsg_src/java/org/pente/turnBased/web/MoveServlet.java` — fold three local game-flag mutations into storer write methods.
- **Create:** `dsg_src/java/org/pente/turnBased/test/InMemoryTBGameStorer.java` — mock base storer for tests (an in-memory `TBGameStorer`).
- **Create:** `dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java` — JUnit 3.7 test verifying no set/game divergence across reloads.
- **Modify:** `build.xml` — register `CacheTBStorerRedisTest` in the `test` target.

---

### Task 1: Test fixture — in-memory base storer + fallback Redis

**Files:**
- Create: `dsg_src/java/org/pente/turnBased/test/InMemoryTBGameStorer.java`
- Create: `dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java`

`CacheTBStorer`'s constructor starts background timers and requires several collaborators. For tests we wrap an in-memory `TBGameStorer` and install a fallback-backed `RedisConnectionManager` so no MySQL/Redis server is needed.

- [ ] **Step 1: Write the in-memory base storer**

Implement every method declared in `dsg_src/java/org/pente/turnBased/TBGameStorer.java`. The methods the tests exercise must behave as below; **implement all remaining interface methods as no-ops** (return `null`, `0`, empty `ArrayList`, or do nothing) — they are not exercised by these tests and that is the complete instruction, not a placeholder.

```java
package org.pente.turnBased.test;

import org.pente.turnBased.*;
import java.util.*;

/** Minimal in-memory TBGameStorer for unit tests. Only set/game persistence
 *  is real; everything else is a no-op default. */
public class InMemoryTBGameStorer implements TBGameStorer {

    private final Map<Long, TBSet> sets = new HashMap<Long, TBSet>();
    private final Map<Long, Long> gidToSid = new HashMap<Long, Long>();
    private long nextSetId = 1, nextGid = 1;

    public synchronized void createSet(TBSet set) throws TBStoreException {
        if (set.getSetId() == 0) { setSetId(set, nextSetId++); }
        for (TBGame g : set.getGames()) {
            if (g == null) continue;
            if (g.getGid() == 0) { g.setGid(nextGid++); }
            gidToSid.put(g.getGid(), set.getSetId());
        }
        sets.put(set.getSetId(), copy(set));
    }

    public synchronized TBSet loadSet(long sid) throws TBStoreException {
        return copy(sets.get(sid));
    }
    public synchronized TBSet loadSetByGid(long gid) throws TBStoreException {
        Long sid = gidToSid.get(gid);
        return sid == null ? null : copy(sets.get(sid));
    }
    public synchronized void updateGameAfterMove(TBGame game) throws TBStoreException {
        TBSet stored = sets.get(game.getSetId() != 0 ? game.getSetId() : gidToSid.get(game.getGid()));
        if (stored != null) { overwriteGame(stored, game); }
    }
    public synchronized void storeNewMove(long gid, int moveNum, int move) throws TBStoreException {
        // moves are persisted by updateGameAfterMove in this mock; no-op here
    }
    // ... implement the remaining TBGameStorer methods as no-op defaults ...

    private void setSetId(TBSet set, long sid) {
        // TBSet has no public setSetId; use createSet-time constructor id.
        // In this mock, set ids are assigned via reflection-free path:
        // construct sets in tests with an explicit id (see helper below).
        throw new IllegalStateException("construct test sets with an explicit setId");
    }
    private void overwriteGame(TBSet stored, TBGame game) {
        TBGame[] gs = stored.getGames();
        for (int i = 0; i < gs.length; i++) {
            if (gs[i] != null && gs[i].getGid() == game.getGid()) { gs[i] = copy(game); }
        }
    }
    private TBSet copy(TBSet s) { return s == null ? null : (TBSet) deepClone(s); }
    private TBGame copy(TBGame g) { return g == null ? null : (TBGame) deepClone(g); }
    private static Object deepClone(java.io.Serializable o) {
        return RedisConnectionManagerCloneHelper.clone(o); // see Step 2
    }
}
```

> Note: `TBSet` set-ids are assigned by `MySQLTBGameStorer` via the DB. Confirm whether `TBSet` exposes a way to set the id (constructor `new TBSet(setId, g1, g2)` exists per `MySQLTBGameStorer:235`). In tests, **construct sets with `new TBSet(sid, g1, g2)`** so `createSet` does not need to assign ids; delete the `setSetId` throw path if the constructor route is used.

- [ ] **Step 2: Add a serialize-clone helper** (so the mock returns copies, mirroring Redis semantics)

Create `dsg_src/java/org/pente/turnBased/test/RedisConnectionManagerCloneHelper.java`:

```java
package org.pente.turnBased.test;
import java.io.*;
public final class RedisConnectionManagerCloneHelper {
    public static Object clone(Serializable o) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new ObjectOutputStream(b).writeObject(o);
            return new ObjectInputStream(new ByteArrayInputStream(b.toByteArray())).readObject();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
```

- [ ] **Step 3: Write the failing divergence test**

```java
package org.pente.turnBased.test;

import junit.framework.TestCase;
import org.pente.turnBased.*;
import org.pente.gameServer.server.RedisConnectionManager;

public class CacheTBStorerRedisTest extends TestCase {

    private InMemoryTBGameStorer base;
    private CacheTBStorer cache;

    protected void setUp() {
        RedisConnectionManager.setInstance(new RedisConnectionManager() {}); // no-arg -> fallback map
        RedisConnectionManager.getInstance().invalidate(RedisConnectionManager.SID_TO_TB_SET);
        RedisConnectionManager.getInstance().invalidate(RedisConnectionManager.GID_TO_SID);
        base = new InMemoryTBGameStorer();
        cache = new CacheTBStorer(base, null, null, null, null); // collaborators unused in this test
    }
    protected void tearDown() {
        cache.destroy();
        RedisConnectionManager.resetInstance();
    }

    /** After a move, a fresh set load and a fresh game load must agree. */
    public void testGameAndSetDoNotDivergeAfterMove() throws Exception {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setState(TBGame.STATE_ACTIVE);
        // p1/p2, daysPerMove, etc. as needed for a legal pente first move
        TBSet set = new TBSet(100L, g, null);
        base.createSet(set);
        cache.loadSet(100L);            // warms Redis cache

        long gid = set.getGame1().getGid();
        cache.storeNewMove(gid, 0, 112); // a legal centre-ish move

        TBGame fromGame = cache.loadGame(gid);
        TBGame fromSet  = cache.loadSet(100L).getGame(gid);
        assertEquals("move count must match across caches",
                fromGame.getNumMoves(), fromSet.getNumMoves());
    }
}
```

- [ ] **Step 4: Run it — expect FAIL (pre-migration: divergence or NPE)**

Run: `ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: FAIL — under the current in-memory implementation `loadGame` returns from `gamesMap` while `loadSet` deserializes from Redis (which isn't written yet), so counts differ / NPE. This proves the test detects divergence.

> If `CacheTBStorer`'s constructor NPEs on null collaborators because a timer task runs immediately, adjust the test to pass lightweight stubs, or add a package-private test constructor that skips `startTasks()`. Prefer the stub route to avoid production changes.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/test/InMemoryTBGameStorer.java \
        dsg_src/java/org/pente/turnBased/test/RedisConnectionManagerCloneHelper.java \
        dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java build.xml
git commit -m "test: failing divergence test + in-memory base storer for CacheTBStorer Redis migration"
```

---

### Task 2: Add Redis namespace constants + index-read helper

**Files:**
- Modify: `dsg_src/java/org/pente/gameServer/server/RedisConnectionManager.java`

- [ ] **Step 1: Add constants** next to the existing TB constants (after `GID_TO_TB_GAME`, ~line 139):

```java
    /** gid -> sid index (aggregate-root lookup, replaces GID_TO_TB_GAME) */
    public static final String GID_TO_SID = "gid:tb_sid";

    /** set of sids that are currently waiting (invitations) */
    public static final String TB_WAITING_SET_IDS = "tb:waiting_set_ids";
```

- [ ] **Step 2: Add a fields helper** (the existing `hgetAllValues` returns values; we also need all keys of a namespace to iterate sids). Add near `hgetAllValues` (~line 431):

```java
    /** Returns all field names (keys) in a namespace; empty list if none. */
    public List<String> hgetAllFields(String namespace) {
        try {
            if (jedis != null) {
                return new ArrayList<String>(jedis.hkeys(namespace));
            }
        } catch (Exception e) {
            log.error("hgetAllFields failed for " + namespace + ", using fallback", e);
        }
        Map<String, Object> map = fallback.get(namespace);
        return map == null ? new ArrayList<String>() : new ArrayList<String>(map.keySet());
    }
```

> Verify `jedis.hkeys` and the `log`/`fallback` field names match the surrounding code in this file before saving (the agent already saw `fallback` at line 58 and a `redisAvailable` flag; mirror the exact pattern used by `hgetAllValues`).

- [ ] **Step 3: Compile**

Run: `ant compile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/gameServer/server/RedisConnectionManager.java
git commit -m "feat: add GID_TO_SID + TB_WAITING_SET_IDS namespaces and hgetAllFields helper"
```

---

### Task 3: Core helpers — persistSet / indexSet / unindexSet, and rewrite cacheSet/cacheGame

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

This task introduces the single write primitive every later task uses. Do NOT yet remove the in-memory maps — add the Redis primitives alongside, then switch reads (Task 4) and writes (Tasks 5-9), then delete the maps (Task 10). This keeps each commit compilable.

- [ ] **Step 1: Add the `pente_cache` field usage and helpers.** `pente_cache` already exists (line 32). Add these private methods near `cacheSet` (line 1160):

```java
    /** THE write primitive: persist a set as the single source of truth and
     *  refresh its gid->sid index entries. Call inside synchronized(cacheTbLock). */
    private void persistSet(TBSet set) {
        if (set == null) return;
        pente_cache.hput(RedisConnectionManager.SID_TO_TB_SET, set.getSetId(), set);
        for (TBGame g : set.getGames()) {
            if (g != null) {
                pente_cache.hput(RedisConnectionManager.GID_TO_SID, g.getGid(), set.getSetId());
            }
        }
    }

    /** Add a set's sids to a player's set-id index. */
    private void indexSetForPlayer(TBSet set, long pid) {
        if (pid == 0) return;
        HashSet<Long> sids = pente_cache.hget(RedisConnectionManager.PID_TO_TB_SET_IDS, pid);
        if (sids == null) sids = new HashSet<Long>();
        sids.add(set.getSetId());
        pente_cache.hput(RedisConnectionManager.PID_TO_TB_SET_IDS, pid, sids);
    }

    /** Remove a set from Redis and from both players' indexes. */
    private void evictSet(TBSet set) {
        pente_cache.hremove(RedisConnectionManager.SID_TO_TB_SET, set.getSetId());
        for (TBGame g : set.getGames()) {
            if (g != null) pente_cache.hremove(RedisConnectionManager.GID_TO_SID, g.getGid());
        }
        removeSetFromPlayerIndex(set.getSetId(), set.getPlayer1Pid());
        removeSetFromPlayerIndex(set.getSetId(), set.getPlayer2Pid());
        pente_cache.hremove(RedisConnectionManager.TB_WAITING_SET_IDS, set.getSetId());
    }

    private void removeSetFromPlayerIndex(long sid, long pid) {
        if (pid == 0) return;
        HashSet<Long> sids = pente_cache.hget(RedisConnectionManager.PID_TO_TB_SET_IDS, pid);
        if (sids != null) { sids.remove(sid); pente_cache.hput(RedisConnectionManager.PID_TO_TB_SET_IDS, pid, sids); }
    }
```

- [ ] **Step 2: Rewrite `cacheSet` (lines 1160-1177)** to persist to Redis (keep writing the in-memory maps for now so reads still work until Task 4):

```java
    private void cacheSet(TBSet set) {
        log4j.debug("CacheTBGameStorer.cacheSet(" + set.getSetId() + ")");
        cacheStats.incrementSetsCached();
        synchronized (cacheTbLock) {
            for (TBGame g : set.getGames()) {
                if (g != null) g.setTbSet(set);
            }
            persistSet(set);
            // legacy in-memory mirror (removed in Task 10)
            setsMap.put(set.getSetId(), set);
            for (TBGame g : set.getGames()) { if (g != null) gamesMap.put(g.getGid(), g); }
        }
    }
```

- [ ] **Step 3: Compile**

Run: `ant compile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "feat: add persistSet/indexSetForPlayer/evictSet helpers; cacheSet writes Redis"
```

---

### Task 4: Switch reads to Redis (loadSet, loadGame, getGame, getGames, getSets, loadSets, getSetsByPid)

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: `loadSet` (lines 1299-1320)** — read from Redis:

```java
    public TBSet loadSet(long sid) throws TBStoreException {
        log4j.debug("CacheTBGameStorer.loadSet(" + sid + ")");
        cacheStats.incrementSetLoads();
        TBSet s = pente_cache.hget(RedisConnectionManager.SID_TO_TB_SET, sid);
        if (s == null) {
            s = baseStorer.loadSet(sid);
            if (s != null) cacheSet(s);
        } else {
            cacheStats.incrementSetLoadsCached();
        }
        return s;
    }
```

- [ ] **Step 2: `loadGame` (lines 1335-1355)** — resolve gid→sid→set→game:

```java
    public TBGame loadGame(long gid) throws TBStoreException {
        log4j.debug("CacheTBGameStorer.loadGame(" + gid + ")");
        cacheStats.incrementGameLoads();
        Long sid = pente_cache.hget(RedisConnectionManager.GID_TO_SID, gid);
        TBSet s = (sid == null) ? null : (TBSet) pente_cache.hget(RedisConnectionManager.SID_TO_TB_SET, sid);
        if (s == null) {
            s = baseStorer.loadSetByGid(gid);
            if (s != null) cacheSet(s);
        } else {
            cacheStats.incrementGameLoadsCached();
        }
        return s == null ? null : s.getGame(gid);
    }
```

> `hget(String,long)` returns `T` inferred from the assignment target. Assign to a typed local (`Long sid = ...`) so erasure picks the right cast; `(TBSet)` cast on the set read is explicit above for clarity.

- [ ] **Step 3: `getGame` (lines 96-100)** — delegate to `loadGame` (it no longer reads a live map):

```java
    public TBGame getGame(long gid) {
        try {
            return loadGame(gid);
        } catch (TBStoreException e) {
            log4j.error("getGame failed for " + gid, e);
            return null;
        }
    }
```

- [ ] **Step 4: `getGames` (lines 102-106) and `getSets` (108-112)** — iterate Redis sets:

```java
    public List<TBGame> getGames() {
        List<TBSet> sets = pente_cache.hgetAllValues(RedisConnectionManager.SID_TO_TB_SET);
        List<TBGame> games = new ArrayList<TBGame>();
        for (TBSet s : sets) {
            for (TBGame g : s.getGames()) { if (g != null) games.add(g); }
        }
        return games;
    }

    public List<TBSet> getSets() {
        return pente_cache.hgetAllValues(RedisConnectionManager.SID_TO_TB_SET);
    }
```

- [ ] **Step 5: `loadSets` (1409-1466), `getSetsByPid` (120-137), `getCachedPids` (114-118)** — use `PID_TO_TB_SET_IDS`. Replace the `setsByPid`-based bodies with index reads that resolve each sid through `loadSet`:

```java
    public List<TBSet> loadSets(long pid) throws TBStoreException {
        log4j.debug("CacheTBGameStorer.loadSets(" + pid + ")");
        HashSet<Long> sids = pente_cache.hget(RedisConnectionManager.PID_TO_TB_SET_IDS, pid);
        if (sids == null) {
            List<TBSet> sets = baseStorer.loadSets(pid);
            cacheStats.incrementSetLoads(sets.size());
            if (sets.isEmpty()) {
                pente_cache.hput(RedisConnectionManager.PID_TO_TB_SET_IDS, pid, new HashSet<Long>());
            } else {
                for (TBSet s : sets) { cacheSet(s); indexSetForPlayer(s, pid); }
            }
            return new ArrayList<TBSet>(sets);
        }
        cacheStats.incrementSetLoadsCached(sids.size());
        List<TBSet> sets = new ArrayList<TBSet>(sids.size());
        for (Long sid : sids) {
            TBSet s = pente_cache.hget(RedisConnectionManager.SID_TO_TB_SET, sid);
            if (s != null) sets.add(s);
        }
        return sets;
    }

    public List<TBSet> getSetsByPid(long pid) {
        try { return loadSets(pid); }
        catch (TBStoreException e) { log4j.error("getSetsByPid " + pid, e); return new ArrayList<TBSet>(); }
    }

    public List<Long> getCachedPids() {
        List<String> fields = pente_cache.hgetAllFields(RedisConnectionManager.PID_TO_TB_SET_IDS);
        List<Long> pids = new ArrayList<Long>(fields.size());
        for (String f : fields) pids.add(Long.valueOf(f));
        return pids;
    }
```

- [ ] **Step 6: Run the Task 1 test — still expected to FAIL** (writes not yet converted, but reads now go through Redis):

Run: `ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: still FAIL — `storeNewMove` mutates the in-memory game but does not `persistSet`, so the reloaded set lacks the move. Confirms the next task is needed.

- [ ] **Step 7: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "feat: route CacheTBStorer reads through Redis (loadSet/loadGame/getGames/loadSets)"
```

---

### Task 5: Convert the move path — `storeNewMove`, `storeNewMessage`, `updateGameAfterMove`

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: `storeNewMove` (lines 1468-1532)** — after mutating `game`, persist its set. The local `game` came from `loadGame`, so `game.getTbSet()` is its canonical set. Add a `persistSet` call before delegating to `baseStorer`, and persist again after `game.end()/setWinner` in the game-over branch:

Replace the tail of the method (from `synchronized (cacheTbLock) { game.addMove(move); }` onward) so every mutation of `game` is followed by persisting `game.getTbSet()`:

```java
        synchronized (cacheTbLock) {
            game.addMove(move);
            long newTimeout = Utilities.calculateNewTimeout(game, dsgPlayerStorer);
            game.setTimeoutDate(new Date(newTimeout));
            game.setUndoRequested(false);   // folded in from MoveServlet:633
            state.addMove(move);
            if (state.isGameOver()) {
                game.end();
                game.setWinner(state.getWinner());
            }
            persistSet(game.getTbSet());     // single source of truth write
        }

        if (state.isGameOver()) {
            log4j.debug("CacheTbStorer.gameover, send to endGameRunnable");
            endGameRunnable.endGame(game, EndGameRunnable.Data.REASON_WIN);
        }

        if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
            ((MySQLTBGameStorer) baseStorer).storeNewAIMove(gid, moveNum, move);
        } else {
            baseStorer.storeNewMove(gid, moveNum, move);
        }
        baseStorer.updateGameAfterMove(game);
```

> Note: `state.addMove(move)` was previously outside the lock; moving it inside is safe (it only mutates the local `state`). Preserve the original move-validation block above unchanged.

- [ ] **Step 2: `storeNewMessage` (lines 1550-1564)** — persist after `addMessage`:

```java
        synchronized (cacheTbLock) {
            game.addMessage(message);
            persistSet(game.getTbSet());
        }
        baseStorer.storeNewMessage(gid, message);
```

- [ ] **Step 3: `updateGameAfterMove` (lines 1566-1568)** — this public method receives a `game` from callers (e.g. `TimeoutCheckRunnable`). Persist its set too:

```java
    public void updateGameAfterMove(TBGame game) throws TBStoreException {
        synchronized (cacheTbLock) {
            persistSet(game.getTbSet());
        }
        baseStorer.updateGameAfterMove(game);
    }
```

> Callers must pass a `game` obtained via `loadGame`/from a set so `getTbSet()` is non-null. `TimeoutCheckRunnable` (Task 8) is updated to satisfy this.

- [ ] **Step 4: Run the Task 1 test — expect PASS**

Run: `ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: PASS — `loadGame` and `loadSet` now agree after a move.

- [ ] **Step 5: Add a game-over divergence test** to `CacheTBStorerRedisTest`:

```java
    public void testWinnerPersistsToSetOnGameOver() throws Exception {
        // build an active set, play moves until state.isGameOver(),
        // then assert cache.loadSet(sid).getGame(gid).getWinner() != 0
        // and equals cache.loadGame(gid).getWinner().
    }
```

> Replace the comment with a concrete sequence of `storeNewMove` calls that wins a TB_PENTE game (5 in a row for the side to move). Use the same board geometry helper the existing pente tests use; if none exists, drive moves via `GridStateFactory.createGridState` to compute a winning line, mirroring `storeNewMove`'s own validation.

- [ ] **Step 6: Run, expect PASS, commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java
git commit -m "feat: storeNewMove/storeNewMessage/updateGameAfterMove persist the set (no divergence)"
```

---

### Task 6: Convert undo/decline/request/hide paths

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

Each mutates a game fetched via `getGame`/`loadGame`. Add `persistSet(tbGame.getTbSet())` after the in-memory mutation, inside the existing `synchronized` block.

- [ ] **Step 1: `undoLastMove` (199-222)** — after the loop and timeout updates, before/after `baseStorer.updateGameAfterMove`:

```java
            tbGame.setTimeoutDate(new Date(newTimeout));
            persistSet(tbGame.getTbSet());
            try { baseStorer.updateGameAfterMove(tbGame); }
            catch (TBStoreException e) { e.printStackTrace(); }
```

- [ ] **Step 2: `declineUndo` (224-242)** — after `tbGame.setUndoRequested(false)`:

```java
            if (tbGame.isUndoRequested()) {
                tbGame.setUndoRequested(false);
                ((MySQLTBGameStorer) baseStorer).undoLastMove(gid);
                persistSet(tbGame.getTbSet());
            }
```

- [ ] **Step 3: `requestUndo` (244-260)** — after `tbGame.setUndoRequested(true)`:

```java
                tbGame.setUndoRequested(true);
                persistSet(tbGame.getTbSet());
                baseStorer.updateGameAfterMove(tbGame);
```

- [ ] **Step 4: `hideGame` (281-287)** — after `tbGame.setHiddenBy(hiddenBy)`:

```java
            tbGame.setHiddenBy(hiddenBy);
            persistSet(tbGame.getTbSet());
```

- [ ] **Step 5: Compile + run the test suite, expect PASS**

Run: `ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: PASS (no regression).

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "feat: undo/decline/request/hide persist the set"
```

---

### Task 7: Convert set-level mutators — cancel/decline/request/accept/dPente/swap2/dpenteState/resign

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: `cancelSet` (1651-1687)** — replace `uncacheSet(set)` with `evictSet(set)`, and `waitingSets.remove(set)` with `pente_cache.hremove(TB_WAITING_SET_IDS, set.getSetId())`. Since `cancelSet` ends with eviction, no `persistSet` is needed (the set leaves the cache); but `baseStorer.cancelSet(set)` still receives the mutated set. Concretely:

```java
        synchronized (cacheTbLock) {
            pente_cache.hremove(RedisConnectionManager.TB_WAITING_SET_IDS, set.getSetId());
            // ... existing state/completion-date mutations unchanged ...
            set.setState(TBSet.STATE_CANCEL);
            set.setCompletionDate(new Date());
        }
        baseStorer.cancelSet(set);
        synchronized (cacheTbLock) { evictSet(set); }
```

- [ ] **Step 2: `declineCancel` (1689-1696)** and `requestCancel` (1698-1705)** — persist after mutating:

```java
    public void declineCancel(TBSet set) throws TBStoreException {
        synchronized (cacheTbLock) {
            set.setCancelMsg(""); set.setCancelPid(0);
            persistSet(set);
        }
        baseStorer.declineCancel(set);
    }
    public void requestCancel(TBSet set, long requestorPid, String message) throws TBStoreException {
        synchronized (cacheTbLock) {
            set.setCancelMsg(message); set.setCancelPid(requestorPid);
            persistSet(set);
        }
        baseStorer.requestCancel(set, requestorPid, message);
    }
```

> `declineCancel`/`requestCancel` receive a `set` from the servlet. Reload the canonical copy first to avoid persisting a stale set: change the first line to `TBSet set = loadSet(setArg.getSetId());` (rename the param to `setArg`). Apply the same reload-first guard wherever a public method takes a caller-supplied `TBSet` (see `cancelSet`, which already calls `loadSet(s.getSetId())`).

- [ ] **Step 3: `acceptInvite` (1575-1649)** — after the block that calls `set.acceptInvite(pid)` and sets timeouts, add `persistSet(set)` (the method already loads the canonical `set = loadSet(s.getSetId())`). Replace `waitingSets.remove(set)` with `pente_cache.hremove(TB_WAITING_SET_IDS, set.getSetId())` and `cacheSetForPlayer(set, pid, false)` with `indexSetForPlayer(set, pid)`. Add `persistSet(set)` right before `baseStorer.acceptInvite(set, pid)`:

```java
        synchronized (cacheTbLock) {
            // ... existing timeout loop ...
            persistSet(set);
        }
        baseStorer.acceptInvite(set, pid);
```

- [ ] **Step 4: `updateDPenteState` (1746-1757)** — persist after `game.setDPenteState(state)`:

```java
        synchronized (cacheTbLock) {
            game.setDPenteState(state);
            persistSet(game.getTbSet());
        }
        baseStorer.updateDPenteState(game, state);
```

- [ ] **Step 5: `dPenteSwap` (1759-1773)** — persist after the swap + timeout; also fold the `dPenteSwapped` flag here so MoveServlet:459 no longer needs its local mutation:

```java
        synchronized (cacheTbLock) {
            game.dPenteSwap(swap);            // sets the swapped flag on the canonical game
            long newTimeout = Utilities.calculateNewTimeout(game, dsgPlayerStorer);
            game.setTimeoutDate(new Date(newTimeout));
            persistSet(game.getTbSet());
        }
        baseStorer.dPenteSwap(game, swap);
```

> Verify `TBGame.dPenteSwap(boolean)` sets the `dPenteSwapped` flag that `MoveServlet:459` set manually. If it does NOT, add `game.setDPenteSwapped(true)` inside the synchronized block when `swap` is true, then persist. This is the canonical home for that flag.

- [ ] **Step 6: `swap2Pass` (1775-1780)** — currently it only delegates. Make it persist the pass flag on the canonical game (folding MoveServlet:515):

```java
    public void swap2Pass(TBGame g) throws TBStoreException {
        TBGame game = loadGame(g.getGid());
        synchronized (cacheTbLock) {
            game.setSwap2Pass(true);
            persistSet(game.getTbSet());
        }
        baseStorer.swap2Pass(game);
    }
```

- [ ] **Step 7: `resignGame(TBGame)` (1707-1717) and `resignGame(TBGame,long)` (1719-1735)** — persist the ended game's set before queueing end-of-game:

```java
        synchronized (cacheTbLock) {
            game.end();
            game.setWinner(/* existing winner expr */);
            persistSet(game.getTbSet());
            endGameRunnable.endGame(game, EndGameRunnable.Data.REASON_RESIGN);
        }
```

- [ ] **Step 8: Compile + run suite, expect PASS; add a resign+cancel test**

Add `testResignPersistsWinner` and `testRequestCancelPersists` mirroring Task 5's pattern (mutate via the storer, reload set+game, assert agreement). Run:
`ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java
git commit -m "feat: set-level mutators persist the set; fold dPenteSwapped/swap2Pass flags into storer"
```

---

### Task 8: Convert background threads + create/waiting paths

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: `TimeoutCheckRunnable.run` (468-601)** — replace the `gamesMap.values()` snapshot (line 479) with a Redis iteration, and ensure each mutated `t` has a non-null `getTbSet()` so `updateGameAfterMove`/end-of-game can persist:

```java
            List<TBGame> gs = getGames();   // now Redis-backed, games carry their set
            synchronized (cacheTbLock) { /* nothing to copy; gs already a snapshot */ }
```

The body already calls `updateGameAfterMove(t)` (now persists) and `endGameRunnable.endGame(t, ...)`. Because `getGames()` flattens from freshly-deserialized sets, each `t.getTbSet()` is its set — confirm by asserting non-null in a test. For the `t.timeout(); t.setWinner(...)` branch (590-592), add a `persistSet(t.getTbSet())` before `endGameRunnable.endGame(...)`:

```java
                    t.timeout();
                    int seat = t.getPlayerSeat(cp);
                    t.setWinner(3 - seat);
                    synchronized (cacheTbLock) { persistSet(t.getTbSet()); }
                    endGameRunnable.endGame(t, EndGameRunnable.Data.REASON_TO);
```

- [ ] **Step 2: `LoadExpireSoonRunnable.run` (414-451)** — replace `setsMap.get(s.getSetId())` existence check with a Redis check:

```java
                    for (TBSet s : sets) {
                        boolean cached = pente_cache.hexists(RedisConnectionManager.SID_TO_TB_SET, s.getSetId());
                        if (!cached) { cacheSet(s); }
                    }
```

- [ ] **Step 3: `createSet` (1230-1293)** — after `cacheSet(set)`, replace `waitingSets.add(set)` with a waiting-index write and `cacheSetForPlayer(...)` with `indexSetForPlayer(...)`:

```java
        cacheSet(set);
        synchronized (cacheTbLock) {
            if (set.isWaitingSet()) {
                pente_cache.hput(RedisConnectionManager.TB_WAITING_SET_IDS, set.getSetId(), set.getSetId());
            }
        }
        indexSetForPlayer(set, set.getPlayer1Pid());
        indexSetForPlayer(set, set.getPlayer2Pid());
```

> Keep the `baseStorer.createSet(set)` call and the tourney auto-start loop unchanged. `createGame` (1322-1333) only writes the DB (no cache) — leave as-is; the set is cached as a whole by `cacheSet`.

- [ ] **Step 4: `getWaitingSets` (156-168) + `loadWaitingSets` (1362-1384)** — back by `TB_WAITING_SET_IDS`, resolve to sets, sort with the existing comparator:

```java
    public List<TBSet> loadWaitingSets() throws TBStoreException {
        List<String> sidFields = pente_cache.hgetAllFields(RedisConnectionManager.TB_WAITING_SET_IDS);
        if (sidFields.isEmpty() && !waitingLoaded()) {
            List<TBSet> gs = baseStorer.loadWaitingSets();
            for (TBSet s : gs) {
                cacheSet(s);
                pente_cache.hput(RedisConnectionManager.TB_WAITING_SET_IDS, s.getSetId(), s.getSetId());
            }
            markWaitingLoaded();
            return new ArrayList<TBSet>(gs);
        }
        List<TBSet> sets = new ArrayList<TBSet>();
        for (String f : sidFields) {
            TBSet s = pente_cache.hget(RedisConnectionManager.SID_TO_TB_SET, Long.valueOf(f));
            if (s != null) sets.add(s);
        }
        return sets;
    }
```

> The `waitingSetsLoaded` boolean (line 70) becomes a Redis-or-flag question. Simplest: keep a one-time in-JVM `volatile boolean waitingSetsLoaded` to avoid re-hitting MySQL on every empty result (helper `waitingLoaded()`/`markWaitingLoaded()`), and rely on `TB_WAITING_SET_IDS` for membership. `getWaitingSets` returns `new ArrayList<TBSet>(loadWaitingSets())` sorted via the existing comparator (extract the comparator from the `waitingSets` TreeSet initializer at lines 53-69 into a `private static final Comparator<TBSet> WAITING_CMP` and apply `Collections.sort` on read).

- [ ] **Step 5: Compile + run suite, expect PASS; add a waiting-set test** (create a waiting set, assert it appears in `getWaitingSets`, accept it, assert it disappears).

Run: `ant compile && ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java dsg_src/java/org/pente/turnBased/test/CacheTBStorerRedisTest.java
git commit -m "feat: background threads + create/waiting-set paths use Redis aggregate"
```

---

### Task 9: Convert eviction/admin paths + eidMap

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: `uncacheAll` (182-197)** — invalidate the Redis namespaces instead of clearing maps:

```java
    public void uncacheAll() {
        synchronized (cacheTbLock) {
            pente_cache.invalidate(RedisConnectionManager.SID_TO_TB_SET);
            pente_cache.invalidate(RedisConnectionManager.GID_TO_SID);
            pente_cache.invalidate(RedisConnectionManager.PID_TO_TB_SET_IDS);
            pente_cache.invalidate(RedisConnectionManager.TB_WAITING_SET_IDS);
            pente_cache.invalidate(RedisConnectionManager.PID_TO_TB_VACATION);
            restartTasks();
        }
    }
```

- [ ] **Step 2: `uncacheGamesForPlayer` (175-180)** — remove the player's set-id index entry:

```java
    public void uncacheGamesForPlayer(long pid) {
        synchronized (cacheTbLock) {
            pente_cache.hremove(RedisConnectionManager.PID_TO_TB_SET_IDS, pid);
        }
    }
```

- [ ] **Step 3: `getEventId` (1218-1228)** — back by `EID_TO_TB_EID`:

```java
    public int getEventId(int game) throws TBStoreException {
        Integer e = pente_cache.hget(RedisConnectionManager.EID_TO_TB_EID, game);
        if (e == null) {
            int eid = baseStorer.getEventId(game);
            pente_cache.hput(RedisConnectionManager.EID_TO_TB_EID, game, Integer.valueOf(eid));
            return eid;
        }
        return e.intValue();
    }
```

> `hput(String,int,T)` and `hget(String,int)` overloads exist (RedisConnectionManager lines 305, 351). Use them for the `int` game-type key.

- [ ] **Step 4: `restoreGame` (1977-1986)** — unchanged logic, but it calls `uncacheGamesForPlayer` (now Redis). Confirm it compiles.

- [ ] **Step 5: Compile + run suite, expect PASS; commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "feat: admin/eviction paths + event-id cache use Redis"
```

---

### Task 10: Delete the in-memory maps and dead helpers

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/CacheTBStorer.java`

- [ ] **Step 1: Remove fields** `gamesMap` (42), `setsMap` (47), `waitingSets` TreeSet (53-69, after extracting the comparator in Task 8), `waitingSetsLoaded` (70) if replaced, `setsByPid` (75), `eidMap` (37). Keep `cacheTbLock`.

- [ ] **Step 2: Remove now-dead private helpers** `cacheGame` (1197-1206), `uncacheGame` (1208-1216), `uncacheSet` (1179-1195), `cacheSetForPlayer` (1125-1142), `uncacheSetForPlayer` (1144-1157) — only after confirming no remaining references (the conversions above replaced them with `persistSet`/`indexSetForPlayer`/`evictSet`). Also remove the legacy in-memory mirror lines added in Task 3 Step 2.

- [ ] **Step 3: Compile — fix any remaining references** (the compiler is the checklist here):

Run: `ant compile`
Expected: BUILD SUCCESSFUL with zero references to the deleted fields.

- [ ] **Step 4: Run full suite, expect PASS**

Run: `ant compile-tests && ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/CacheTBStorer.java
git commit -m "refactor: remove in-memory maps; CacheTBStorer is fully Redis-backed"
```

---

### Task 11: Fix MoveServlet local pre-mutations

**Files:**
- Modify: `dsg_src/java/org/pente/turnBased/web/MoveServlet.java`

The flags are now persisted inside the storer methods (Tasks 5 & 7). Remove the redundant/broken local mutations so the servlet relies on the canonical writes.

- [ ] **Step 1: Line ~459** — delete `game.setDPenteSwapped(true);` (now set inside `dPenteSwap`). Confirm `dPenteSwap(game, swap)` was called just above (line 448) — it is, in the same branch. The subsequent `storeNewMove` then persists the already-swapped set.

- [ ] **Step 2: Line ~515** — delete `game.setSwap2Pass(true);` (now set inside `swap2Pass`, called on the next line 516).

- [ ] **Step 3: Line ~633** — delete `game.setUndoRequested(false);` (now cleared inside `storeNewMove`). Verify every code path that reaches line 633 went through a `storeNewMove`/swap that persisted the clear; if a path mutates without a store (e.g. a pure message with no move), keep a single explicit `requestUndo`-style persist. Trace the branches above 633: each either calls `storeNewMove`, `swap2Pass`, `dPenteSwap`, or `updateDPenteState`, all of which now persist. The message-only path (line 617-619) does NOT clear undo — for that path add, right after the move-handling block:

```java
                // ensure undo flag cleared even on message-only paths
                if (game.isUndoRequested()) {
                    ((CacheTBStorer) tbGameStorer).declineUndo(game.getGid());
                }
```

> Confirm this matches intended semantics: making any move (or sending the move-message) clears a pending undo request. If product intent differs, replace with the minimal storer call that persists `undoRequested=false`.

- [ ] **Step 4: Compile the web layer**

Run: `ant compile`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add dsg_src/java/org/pente/turnBased/web/MoveServlet.java
git commit -m "fix: MoveServlet relies on storer-side flag persistence (dPenteSwapped/swap2Pass/undoRequested)"
```

---

### Task 12: Register the test + full regression

**Files:**
- Modify: `build.xml`

- [ ] **Step 1: Add `CacheTBStorerRedisTest` to the `test` target** (build.xml ~line 41-62), following the existing `<java classname="junit.textui.TestRunner" ...>` entries:

```xml
        <java classname="junit.textui.TestRunner" fork="true" failonerror="true">
            <arg value="org.pente.turnBased.test.CacheTBStorerRedisTest"/>
            <classpath refid="test.classpath"/>
        </java>
```

> Match the exact `classpath` ref id and attributes used by the sibling test entries in the file.

- [ ] **Step 2: Run the registered suite**

Run: `ant test-one -Dtest=org.pente.turnBased.test.CacheTBStorerRedisTest`
Expected: `OK (N tests)`.

- [ ] **Step 3: Full compile to catch any callers**

Run: `ant compile && ant compile-tests`
Expected: BUILD SUCCESSFUL — confirms `CacheKOTHStorer`, `NewGameServlet`, `ResignServlet`, `CancelServlet`, `ReplyInvitationServlet`, `ViewGameServlet` (all audited as caller-safe) still compile against the changed `CacheTBStorer`.

- [ ] **Step 4: Commit**

```bash
git add build.xml
git commit -m "test: register CacheTBStorerRedisTest in ant test target"
```

---

## Manual / staging verification (after merge to a test environment)

Automated tests use the fallback in-memory Redis. Before production, on staging with a real Redis:
- Play a TB pente game end-to-end (move, message, undo request + decline, resign) and confirm board state after each action survives a Tomcat restart (cache rebuilds from Redis/MySQL with no divergence).
- Start a 2-game set; cancel one; confirm waiting/active lists and `loadSets(pid)` are correct.
- Force a timeout (set `timeoutDate` in the past via DB) and confirm `TimeoutCheckRunnable` ends the game and the set, visible on reload.
- Confirm dpente swap and swap2 pass decisions persist across a reload (the folded flags).

---

## Self-Review (completed by author)

- **Spec coverage:** SID_TO_TB_SET canonical (Tasks 3-5), GID_TO_SID index (Tasks 2,4), PID_TO_TB_SET_IDS (Task 4), EID_TO_TB_EID (Task 9), write invariant on every mutator (Tasks 5-8), iteration via hgetAllValues (Task 4), MoveServlet 459/515/633 fixes (Task 11), per-JVM synchronized retained, fallback relied upon for tests. Waiting-set handling (not explicit in spec) added as `TB_WAITING_SET_IDS` (Task 8) — a necessary index discovered during code reading; flagged here as a scope addition.
- **Placeholders:** test bodies that require a concrete winning move sequence are marked with explicit construction instructions, not vague TODOs. The mock storer's unused interface methods are explicitly "no-op defaults" (a complete instruction).
- **Type consistency:** helper names used consistently — `persistSet`, `indexSetForPlayer`, `evictSet`, `removeSetFromPlayerIndex`, `hgetAllFields`. Redis namespace constants match `RedisConnectionManager`.
- **Open risk to flag at execution:** `TBSet` set-id assignment in the mock (Task 1) depends on the `new TBSet(sid, g1, g2)` constructor — verify it exists and is public before relying on it; otherwise add a test-only setter.
