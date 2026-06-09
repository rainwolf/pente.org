# Design: Migrate CacheTBStorer & CacheTourneyStorer to Redis (Aggregate-Root Model)

**Date:** 2026-06-09
**Status:** Approved (design); implementation plan pending
**Author:** rainwolf (with Claude)

## Problem

`CacheTBStorer` and `CacheTourneyStorer` are in-memory, single-JVM caches that
wrap their MySQL-backed storers. We want them to cache in Redis instead, so the
cache survives restarts and (eventually) can be shared across instances.

The core hazard is **object identity**. Today the same `TBGame` instance lives in
three places at once:

- `CacheTBStorer.gamesMap` (gid → `TBGame`)
- inside the containing `TBSet.games[]` array (in `setsMap`)
- referenced back via `TBGame.tbSet`

Because these are the *same JVM object*, mutating it anywhere updates it
everywhere. Redis serializes `gid→game` and `sid→set` as **independent blobs**,
so the two copies would diverge: a `TBSet` could hold a `TBGame` whose state
differs from the standalone cached `TBGame` for the same gid. That divergence is
the exact failure we must prevent.

The same shape exists in `CacheTourneyStorer`: the `tournies` map and the
upcoming/current/completed lists, plus an independently-cached
`tourneyPlayerPids`.

## Goal & Non-Goals

**Goal:** Eliminate the two-copy divergence class entirely by making the
aggregate the single source of truth in Redis. A `TBGame` is never cached
independently of its `TBSet`; a `Tourney`'s membership lists never hold
divergent copies.

**Non-goals (this migration):**
- Distributed write locking across instances. Per-JVM `synchronized` is retained;
  cross-instance write races are explicitly out of scope and noted as future work.
- Changing the MySQL schema or the `baseStorer`/`backingStorer` contracts.
- Touching the live-game-room / websocket layer (it does not call the TB storer).

## Chosen Approach: Aggregate Root (single source of truth)

Rejected alternatives:
- **Two caches kept in sync** (`GID_TO_TB_GAME` + `SID_TO_TB_SET`, both written on
  every mutation under a transaction): correctness depends on discipline at every
  mutation path and background thread; one missed write-back silently diverges —
  exactly the failure we are trying to design out.
- **Redis as L2 behind the in-memory L1:** preserves identity within a JVM but each
  instance's L1 can still diverge from peers; only helps restart-survival.

We choose the aggregate-root model. The double Redis lookup it costs for a
gid-only read (gid → sid → set) is accepted.

### Why it is safe

- Java serialization preserves the `TBGame.tbSet ↔ TBSet.games` cycle **within a
  single blob**, so after deserializing a set, `set.getGame(gid).getTbSet() == set`.
  `EndGameRunnable`'s `data.game.getTbSet()` keeps working — provided the game was
  obtained *from* that set load (never mixed across loads).
- `TBGame` and `TBSet` override neither `equals()` nor `hashCode()`, but every
  cache keys by `Long` (`gamesMap`, `setsMap`, `setsByPid`) and the `waitingSets`
  `TreeSet` comparator sorts by *fields* (gid, game type, creation date). Deserialized
  copies therefore key and sort identically — no identity landmines.
- `getGames()` returns a defensive copy and has **no mutating callers** (admin /
  monitoring display only).

## CacheTBStorer Design

### Redis layout

| Namespace | Key → Value | Role |
|---|---|---|
| `SID_TO_TB_SET` | sid → serialized `TBSet` (carries its `TBGame`s) | **Canonical** |
| `GID_TO_SID` *(new)* | gid → sid | gid lookup index (replaces `GID_TO_TB_GAME`) |
| `PID_TO_TB_SET_IDS` | pid → set of sids | per-player set index |
| `EID_TO_TB_EID` | game type → event id | event id cache |
| `PID_TO_TB_VACATION` | pid → `TBVacation` | already migrated (unchanged) |

`GID_TO_TB_GAME` is removed. Games are never stored as standalone Redis entries.

### Read path

`getGame(gid)` / `loadGame(gid)`:
1. `sid = hget(GID_TO_SID, gid)`; if missing, `baseStorer.loadSetByGid(gid)` →
   `cacheSet(set)` (which writes `SID_TO_TB_SET`, `GID_TO_SID`, `PID_TO_TB_SET_IDS`).
2. `set = hget(SID_TO_TB_SET, sid)`.
3. return `set.getGame(gid)`.

`getSet`/`loadSet`/`loadSetByGid`: read `SID_TO_TB_SET` (via `GID_TO_SID` for the
by-gid variant), fall through to `baseStorer` + `cacheSet` on miss.

### Write invariant (the rule that makes this correct)

> Every mutating method loads the canonical `TBSet`, applies the change to the
> in-set `TBGame`, writes the **whole set** back to `SID_TO_TB_SET`, then delegates
> to `baseStorer`. No method ever persists a `TBGame` independently of its set.

Methods to rewrite to obey this: `storeNewMove`, `updateGameAfterMove`,
`undoLastMove`, `requestUndo`, `declineUndo`, `hideGame`, `resignGame`,
`cancelSet`, `requestCancel`, `declineCancel`, `acceptInvite`, `dPenteSwap`,
`swap2Pass`, `updateDPenteState`, `continueGoGame`, `endGame`/`endSet`, set
creation (`createSet`, `createAISet`). Each must reload-mutate-rewrite the set.

### Iteration

`TimeoutCheckRunnable`, `LoadExpireSoonRunnable`, `getGames()`,
`RemoveStalePlayersInvitations`: replace `gamesMap.values()` iteration with
`hgetAllValues(SID_TO_TB_SET)` then flatten to games. When such a thread mutates a
game (e.g. timeout → `setWinner`/`timeout`), it follows the write invariant:
mutate the in-set game, rewrite the set, queue end-of-game.

### Concurrency

Retain `synchronized(cacheTbLock)` for per-JVM atomicity of the
read-mutate-rewrite sequence. Cross-instance locking (Redis distributed lock) is
out of scope and called out as future work.

## CacheTourneyStorer Design

| Namespace | Key → Value | Role |
|---|---|---|
| `EID_TO_TOURNEY` | eid → serialized `Tourney` | **Canonical** |
| `TOURNEY_LIST_UPCOMING` / `_CURRENT` / `_COMPLETED` | list of **eids** | ordered membership, resolved through `EID_TO_TOURNEY` |
| `EID_TO_TOURNEY_PLAYER_PIDS` | eid → list of pids | independent index |

- Lists store **eids only**, never full `Tourney` copies, so they cannot diverge
  from the canonical map. State transitions (upcoming → current) move an eid
  between lists.
- `getTourney(eid)` reads `EID_TO_TOURNEY` (fall through to `backingStorer` +
  cache on miss). `getUpcomingTournies()` etc. read the eid list and resolve each
  through `EID_TO_TOURNEY`.
- `addPlayerToTourney` / `removePlayerFromTourney` mutate
  `EID_TO_TOURNEY_PLAYER_PIDS` and **write it back to Redis** (today they mutate an
  in-memory list in place).
- Timer-based tournament start scheduling is unchanged in behavior; timers read
  canonical state from Redis when they fire.

## Required Caller Fixes (full external blast radius)

The codebase audit (Java + JSP + websocket) found the external surface is small.
Everything below `MoveServlet` already mutates-then-calls-an-explicit-storer-write,
so the whole-set rewrite covers it. The only external fixes:

**`MoveServlet.java` — three local pre-mutations of `game` followed by a gid-based
storer write.** In-memory these worked because the servlet's `game` *was* the
cached instance. Under the aggregate-root model the storer methods load their own
set copy, so the flag must be folded into the storer write:

- Line ~459 `game.setDPenteSwapped(true)` → `dPenteSwap(...)` must set the flag on
  the loaded set before persisting.
- Line ~515 `game.setSwap2Pass(true)` → `swap2Pass(...)` must set the flag on the
  loaded set.
- Line ~633 `game.setUndoRequested(false)` → fold into `storeNewMove` (a recorded
  move clears `undoRequested` on the canonical set).

No JSP changes. No websocket changes. No `equals`/`hashCode` or `==` fixes needed.

## Error Handling & Fallback

`RedisConnectionManager` already has an in-memory `fallback` map and degrades
gracefully when Redis is unavailable. The migrated storers inherit this: a Redis
outage falls back to the in-memory map (single-JVM semantics) rather than failing
requests. Serialization failures are logged and treated as cache misses (reload
from `baseStorer`).

## Testing

- **Unit/integration per mutation path:** for `storeNewMove`, `undoLastMove`,
  `requestUndo`, `resignGame`, `cancelSet`, `requestCancel`, swap/swap2, timeout —
  assert that after the call, a **fresh** `getGame(gid)` and `getSet(sid)` (forcing
  a Redis round-trip / deserialization) both reflect the mutation and agree with
  each other (no divergence).
- **Identity-after-reload:** assert `getSet(sid).getGame(gid).getTbSet() == getSet(sid)`
  within one load, and that two independent loads are value-equal.
- **Timeout thread:** simulate an expired active game; assert set completion and
  winner are persisted and visible on reload.
- **Tourney:** add/remove player then reload; assert `EID_TO_TOURNEY_PLAYER_PIDS`
  and list membership persist; assert list/map never diverge.
- **Fallback:** with Redis down, the same suite passes against the in-memory
  fallback.
- Existing `TBStorerTest`, `DoubleEliminationDriver`, `SwissDriver` updated/kept
  green.

## Rollout

1. Migrate **CacheTBStorer** first (higher risk, clearer invariant), behind the
   existing Redis config; verify with the test suite and on staging.
2. Migrate **CacheTourneyStorer** second.
3. Keep the in-memory `fallback` as the safety net throughout.

## Open Questions / Future Work

- Distributed write locking for true multi-instance write safety (out of scope here).
- TTL/eviction policy for `SID_TO_TB_SET` and `EID_TO_TOURNEY` (today: no eviction;
  acceptable initially, revisit if memory grows).
