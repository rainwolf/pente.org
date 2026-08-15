# Removing the game-count cap — staged refactor strategy

**Approach name: Grandfathered Registry (lookup-first, zero-renumber)**

Design doc. Recon-only session: no repo file outside this scratchpad was modified,
no DB write was issued. Every claim below is either cited to a map report in this
directory or to a direct read-only check performed while writing this document
(those are marked **[verified here]**).

Sources consumed in full:
`map-java-core.md`, `map-java-db-wire.md`, `map-jsp-web.md`, `map-react.md`,
`map-react-android.md`, `map-android.md`, `../ios-game-id-touchpoints.md`,
`MASTER-game-id-refactor-report.md`.

---

## 1. The strategy in one page

**Never renumber anything.** All 48 existing ids (live 1..32, turn-based odd 51..81)
keep their exact numeric values forever. No data migration, no client breakage for
existing games, no wire change — ever, at any stage.

**Replace derivation with lookup.** Today "what is this game" is answered by
arithmetic on the id (`+1` for speed, `+50` for turn-based, `%2`, `>50`,
`(id-51)/2`, `allGames[id]`). Every one of those becomes a lookup against a single
in-code registry of `Game` descriptors, seeded from the constants that already exist
in `GridStateFactory`. The arithmetic *results* are unchanged for all 48 legacy ids —
that is what makes the early stages provably invisible.

**Mint new ids outside the legacy band.** Once nothing derives meaning from the id's
value, a new family can take any free id (policy: 100+). Speed and turn-based
variants become *flags on a descriptor*, not `base+1` / `base+50` offsets.

**Materialize, don't relocate, the source of truth.** The registry stays in code
(`GridStateFactory` descriptors). At boot it materializes itself into the DB and
serves itself over a read-only JSON endpoint — extending the pattern that
`MySQLGameVenueStorer.registerAllGames` + `DSGContextListener` already use for
`game_event` rows (`map-java-db-wire.md` §C1). The DB table and the endpoint are
*derived views*, never authorities. This matches the recorded project preference for
boot-time self-configuration from one source of truth, without creating an ops
surface where a DB row edit can change game rules.

**Gate visibility per channel.** A one-boolean addition (`legacySafe`, default `true`
for the 48 grandfathered ids, `false` for every new one) gates the handful of
enumeration APIs that feed old mobile clients. This is the mechanism — and the only
mechanism available, see §8 — that makes a genuinely silent new-game rollout possible.

---

## 2. Ground truths this plan rests on

These are the load-bearing facts. If any turns out wrong, the stage that depends on
it must be re-planned.

**G1 — The wire imposes no limit and needs no change, ever.**
Both transports serialise `DSGEventWrapper` as Gson JSON; TCP frames it with a single
`0xFF` byte. `game` is a Java `int` → JSON number. `grep -rn
'writeByte|writeShort|writeInt|readByte|readShort|readInt' org/pente/gameServer/event/`
returns zero hits (`map-java-db-wire.md` §0.1, §D.1). The Android client confirms the
same from its side and finds no binary codec at all (`map-android.md` "Wire protocol").
**No stage in this plan changes the protocol.**

**G2 — There is no game-list event.** The server never enumerates games to clients;
clients discover ids from `DSGChangeStateTableEvent` broadcasts plus their own
hardcoded lists (`map-java-db-wire.md` §D.2). This is *why* old apps keep working —
and also why new games cannot be pushed to them.

**G3 — The real cap is four coupled limits in one file.**
`GridStateFactory.allGames[]` is 49 slots, id-indexed at 1..32 but *positionally*
packed at 33..48 with the turn-based `Game` objects; `gridStates[]` is sized 49;
`tbGames[]` is indexed `(id-51)/2`; `TB_START=50`. Id 33 is **not free** — `getGame(33)`
returns `TB_PENTE_GAME` today, silently (`map-java-core.md` class D,
`map-java-db-wire.md` §E.1). **[verified here]** the current source reads exactly:
`getGame(int game) { if (game > TB_START) return tbGames[(game-TB_START-1)/2]; else return allGames[game]; }`.

**G4 — Game id is `int` end-to-end in Java.** No narrowing, no bit-packing outside the
excluded codec package (`map-java-core.md` §"Cap-relevant width limits"). The cap is
purely semantic in the Java layer.

**G5 — The DB ceiling is 255, not 32.** Five columns are `TINYINT(3) UNSIGNED`:
`game_event.game`, `pente_game.game`, `pente_move.game`, `dsg_player_game.game` (PK
part), `dsg_server_game.game` (PK part). `tb_game`/`tb_game_ai` are already
`SMALLINT UNSIGNED`; `webdb_*` are `SMALLINT` signed (`map-java-db-wire.md` §A, §F).
**[verified here]** all seven relevant tables are **MyISAM**, with row counts:
`game_event` 1 034, `dsg_server_game` 197, `koth` 75, `dsg_player_game` 54 622,
`tb_game` 729 164, `pente_game` 1 252 528, **`pente_move` 35 775 023 rows / 1 024 MB
data / 2 539 MB index**. That last table is the only expensive `ALTER` in the whole plan.

**G6 — The phantom band is empty in production data.** **[verified here]** on the live
local stack:
```sql
SELECT COUNT(*) FROM <t> WHERE game BETWEEN 33 AND 50 OR game > 81;
-- game_event 0, pente_game 0, dsg_player_game 0, dsg_server_game 0,
-- tb_game 0, tb_game_ai 0
```
This is the precondition that makes Stage 1's intentional behaviour deltas unreachable
in production. (`pente_move` not swept — 35.8M-row full scan; see §10 OV-1.)

**G7 — Old mobile clients crash on an unknown *live* id, they do not degrade.**
Android `liveGameRoom/Table.java:165-177` `shouldTimerRun()` calls
`gameNames.get(game).contains(...)` with no null check — confirmed NPE
(`map-android.md` #6, risk flag 1). iOS `HelperClasses.swift:135` and `:624`
force-unwrap `Table.gameNames[game]!` — trap (`ios-game-id-touchpoints.md` §2).
Neither has any "Unknown game" fallback.

**G8 — There is no client-version signal on the live wire today.** **[verified here]**
`DSGLoginEvent` carries `player`, `password`, `guest`, and a `ClientInfo`
(`browser/javaVersion/javaClassVersion/os/osVersion`) — no app version. And
`grep -rn clientInfo` across `pentelive-android/app/src/main/java`,
`penteLive-iOS/test1` and `react_live_game_room/src` returns **zero hits**: no modern
client populates it. Server-side per-version filtering of the lobby is therefore
**impossible without a client release**. This single fact decides the silent-rollout
verdict for live games (§8).

**G9 — There is a working precedent for every mechanism this plan uses.**
Array-driven boot registration (`MySQLGameVenueStorer.registerAllGames`, called from
`DSGContextListener.java:117-119` inside `catch (Throwable)`); allow-list membership
instead of ranges (`MMAIPlayer.SUPPORTED_GAMES`, `KotHServlet.java:54-62`); id-agnostic
sparse ratings storage (`SimpleDSGPlayerData`); a JSON API mount point
(**[verified here]** `web.xml:572` `<url-pattern>/api/db/*</url-pattern>` with
`org/pente/webdb/JsonHttp.java`); a consolidation module already shipped on one client
(`react_live_game_room/src/game/boardGeometry.js`); a fail-loud unknown-id test
(`js/boardCommon.test.js:82-85`); and an equivalence-pinned predicate migration
(Android `VariantPredicateEquivalenceTest`). **[verified here]** `build.xml` exposes
`compile`, `compile-tests`, `test`, `test-one`, `test-mmai-integration`.

---

## 3. End-state id allocation policy

| Band | Ids | Status |
|---|---|---|
| Legacy live + speed | 1..32 | **Frozen.** Never renumbered, never reused. |
| Phantom / poisoned | 33..50 | **Never allocate.** Was silently occupied by `allGames[33..48]` (G3); cheap insurance against any `>50` heuristic that survives the sweep. |
| Legacy turn-based | 51..81 (odd) | **Frozen.** |
| Reserved | 82..99 | **Never allocate.** Buffer; makes "id ≥ 100 ⇒ new scheme" a reliable debugging invariant. |
| New families | 100..255 | Free. ~52 more families at 3 variants each — **no DB change needed** (G5). |
| New families, phase 2 | 256..∞ | Free after Stage 9 (DB widening). |

A new family declares its own ids explicitly in the registry; there is no required
relationship between them. Example: `family GOMOKU_PLUS { live=100, speed=101, tb=102 }`
— or `{ live=103, tb=104 }` with no speed variant at all, which the current `+1` scheme
cannot express (Renju already wants this: it has a speed twin but the Android
`Variants.fromGameId` special-cases `id==81` because 81 doesn't fit the doubling scheme
at all — `map-android.md` #2).

Registry invariant enforced by a unit test: ids are unique, disjoint from the poisoned
bands, and ≤ 255 until Stage 9 lands.

---

## 4. Stage overview

| # | Stage | Player-visible? | Layers touched | Size | Reversible by |
|---|---|---|---|---|---|
| 0 | Pinning harness | No — no prod code | tests only | S | delete files |
| 1 | `GridStateFactory` positional arrays → id-keyed maps | No (G6) | 1 Java file | M | 1-file revert |
| 2 | `Game` descriptor + predicate delegation + `TB_START` publicised | No | ~15 Java files, 3 JSP | M | branch revert |
| 3 | Reconcile the 5 divergent opening predicates | **YES — first visible stage** (bug fix) | 5 files | S | branch revert |
| 4 | Boundary validation + KOTH sentinel/name fixes | No | ~8 Java files | S/M | branch revert |
| 5 | Boot materialization + `/api/games` metadata | No (additive) | 3 Java files + 1 DDL | M | drop table, unmap servlet |
| 6 | Web/JS client consolidation (JSP, boardCommon, React ×2) | No | JSP/JS/React | M/L | branch revert |
| 7 | Mobile hardening release (Android + iOS) | No (only "stops crashing") | 2 apps | M | app store rollback |
| 8 | Mint the first new family outside the legacy band | **Yes — that is the point** | registry data + client lists | S per game | flip `active=false` |
| 9 | DB widening (`TINYINT`→`SMALLINT UNSIGNED`) | No | DDL only | S + 1 big ALTER | column revert |

Stages 0–6 are server-and-web only and require **zero** mobile action.
**Stage 7 is fully independent and can start on day one, in parallel with Stage 0** —
it has no server dependency, and the sooner it ships the sooner its natural install-base
penetration clock starts running. That parallelism is the single biggest schedule lever
in this plan.

---

## 5. Stages 0–4 (server-side, invisible except Stage 3)

### Stage 0 — Pinning harness (no production code change)

**Scope.** One new characterization test, e.g.
`dsg_src/java/org/pente/game/test/GridStateFactoryCharacterizationTest.java`, that
snapshots today's behaviour for **every id 0..100** across every identity primitive and
predicate: `getGame`, `getGameName`, `getDisplayName`, `createGridState(id)` and
`createGridState(id, moveData)` (class name of result, or exception type),
`getColor(0, id)`, `isSpeedGame`, `isTurnbasedGame`, `getSpeedGame`, `getNormalGame`,
`getNormalGameFromTurnbased`, `isSingleGameSet`, `firstMoveCanBeOffCenter`,
`getCenterMove`, `isValidGame`, `getMaxGameId`, `getNumGames`, plus the **contents and
order** of `getAllGames/getDisplayGames/getNormalGames/getSpeedGames/getTbGames`
(order is observed by `IndexResponse.java:419-420` "odd first" and
`CacheKOTHStorer.java:150`).

Record the *known-wrong* answers as pinned facts, not as bugs to fix here:
`getGame(33) == TB_PENTE_GAME (id 51)`, `getGame(49|50)` throws AIOOBE,
`getColor(n, 51)` throws AIOOBE, `getDisplayName(unknown) == null`.

Add a second, DB-level snapshot script (read-only) capturing `game_event`,
`dsg_server_game` and the distinct-`game` sets of `pente_game` / `dsg_player_game` /
`tb_game`, so later stages can prove boot-time registration produced identical rows.

**Why invisible.** No production class is touched.

**Verify.** `ant test` green on the new test against unmodified `main`.

**Rollback.** Delete the test file.

**Size.** S — one test file (~300 lines) + one shell snapshot script.

---

### Stage 1 — `GridStateFactory` positional arrays → id-keyed maps

**Scope.** `dsg_src/java/org/pente/game/GridStateFactory.java` **only**. Replace the four
coupled positional structures (G3) with maps keyed by the *real* id:

- `allGames[]` (`:139-154`) and `tbGames[]`'s lookup role (`:226-235`) → one
  `Map<Integer, Game> gamesById`. Keep `tbGames[]`, `normalGames[]`, `speedGames[]`,
  `displaygames[]` alive purely as **ordered display lists** — this splits the dual
  role the map reports flag as the core defect.
- `gridStates[]` (`:237`) + `tbGridStates[]` (`:238`) → one
  `Map<Integer, GridState> prototypesById`, built by iterating the registered ids
  instead of `for (int i = 1; i < gridStates.length; i++)` (`:240-243`).
- `getGame` (`:392-398`), `createGridState(game, moveData)` (`:384-390`),
  `getColor` (`:479-481`), `getGameId(String)` (`:408-420`), `getNumGames` (`:422-424`)
  reimplemented on the maps.
- `getAllGames()` keeps returning the **same 49-slot array with the leading null**, as a
  derived view. Do not change its shape yet — five callers iterate it from `i=1`
  (`PlayerStatsDialog:316`, `followersing.jsp:68`, `newTourney.jsp:118`,
  `broadcast.jsp:73`, `NewDialog2.java:19`). Shape change is deferred to Stage 6.
- The giant `switch (game)` (`:262-381`) is **not** touched in this stage.

**Intentional behaviour deltas** (the complete list — everything else is byte-identical):

| Call | Before | After |
|---|---|---|
| `getGame(33..48)` | wrong `Game` (TB objects) | `null` |
| `getGame(0, 49, 50, even >50, >81)` | `null` / AIOOBE | `null` |
| `getGameName(33..50)` | wrong name / AIOOBE | NPE (unchanged shape: still an exception) |
| `getColor(n, 51..81)` | AIOOBE | correct colour |
| `createGridState(33..48, md)` | NPE | `null` |

**Why invisible.** Every delta is confined to ids that do not exist anywhere in
production data (G6, verified) and cannot be produced by any client UI (all pickers are
bounded by hardcoded lists on every platform). The only network path that reaches these
ids is a hostile/malformed client at `ServerTable.java:910`/`:976`/`:284` — today that
throws AIOOBE, after this it throws NPE; both are caught by `SocketDSGEventHandler`'s
`catch (Throwable)` → `handleError` → `destroy()`, so the blast radius (one connection)
is unchanged. Hardening that path properly is Stage 4.

**Verify.**
1. Stage-0 harness re-run; the diff must contain **exactly** the five rows above and
   nothing else. Review it line by line — this is the stage's real test.
2. `ant test` — existing pins `game/test/RenjuFactoryTest.java:68-70`,
   `GridStateFactorySingleGameSetTest`, `MySQLGameVenueStorerTbLookupTest` must stay green.
3. Local stack only (per project convention: compile + bind-mount + restart
   `penteorg-pente.org-1`; `sync_gameServer.sh` is production deploy, never for testing).
   Boot, then re-run the Stage-0 DB snapshot and diff: `game_event` and `dsg_server_game`
   must be byte-identical, proving `registerAllGames` produced the same rows.
4. Smoke: play one live game, one speed game, one turn-based game, view one archived
   game, load one KOTH page.

**Rollback.** `git revert` of a single file. No schema change, no data change, no client
change — rollback is unconditionally safe at any time.

**Size.** M — ~150 lines changed in one file.

---

### Stage 2 — `Game` descriptor + predicate delegation (pure, no member-list changes)

**Scope.** Add fields to `dsg_src/java/org/pente/game/Game.java` (**[verified here]** it
currently holds only `id`, `name`, `speed`): `variant` (`LIVE|SPEED|TB`),
`baseFamilyId`, `legacySafe`, and behavioural flags `singleGameSet`,
`firstMoveOffCenterArchive`, `kFactor`, `gridSize`, `stonesPerTurn`.
**Populate every flag from the current predicate's exact member list**, so the values
are correct by construction.

Then reimplement, leaving all ~60 call sites untouched:
`isSpeedGame` (`:471-473`, drop the `%2` parity — `Game` already carries a `speed`
boolean, unused), `isTurnbasedGame` (`:475-477`), `getSpeedGame` (`:459-461`),
`getNormalGame` (`:463-465`), `getNormalGameFromTurnbased` (`:467-469`),
`isSingleGameSet` (`:502-507`).

Make `TB_START` public *or* (preferred) add `GridStateFactory.isTurnbased(int)` and route
the ~14 hardcoded `50` literals through it: `Tourney.java:123`,
`KothResponse.java:61,83`, `IndexResponse.java:447,481`,
`CacheKOTHStorer.java:96,177,213`, `MySQLGameVenueStorer.java:466` (`findGameTreeData`),
`CacheTourneyStorer.java:487,741,815`, `CacheTBStorer.java:2169`,
`broadcast.jsp:75`, `kothBox.jsp:88`. Same for the `+1`/`+50` arithmetic duplicated
outside the factory at `MySQLGameVenueStorer.java:~246-268` (`registerGame`).

Collapse only the **provably equivalent** duplicates: `ServerTable.java:2778-2781`
(`single_game`) → `isSingleGameSet`; `ServerTable.java:3650-3653` (`k32Game`) →
`descriptor.kFactor`. Leave the divergent ones alone — that is Stage 3.

Also decompose `FastMySQLDSGGameLookup.java:55-56,104-105,157-158` and
`RatingsGrapher.java:116,131,141,146-150` onto `descriptor.baseFamilyId()` /
`descriptor.isTurnBased()` (3 + 5 copies of the same normalisation).

**Why invisible.** Every predicate returns the identical value for all 48 legacy ids by
construction; no id set changes membership. Speed-ness stops being derived from parity,
but every legacy speed id is even anyway.

**Verify.** Stage-0 harness must produce a **zero diff** — this stage is a pure
refactor and any diff is a defect. Plus `ant test`; plus the DB snapshot diff after a
local boot (`registerGame`'s arithmetic removal must not change `game_event`).
Write a small `GameDescriptorEquivalenceTest` in the spirit of Android's
`VariantPredicateEquivalenceTest`: for every id in `ALL_GAMES`, assert
`newPredicate(id) == oldInlineList(id)` with the old list literally inlined in the test.

**Rollback.** Branch revert. No data, no schema, no client.

**Size.** M — one new-ish field block plus ~15 mechanical call-site edits across
Java and 3 JSPs.

---

### Stage 3 — Reconcile the five divergent opening predicates ⚠️ FIRST PLAYER-VISIBLE STAGE

**Scope.** The "has a negotiated / off-center opening" predicate exists in five
hand-copied versions with **three disagreeing member lists**
(`MASTER-game-id-refactor-report.md` §2):

| Site | Members today |
|---|---|
| `GridStateFactory.firstMoveCanBeOffCenter` | Go + DPENTE + DKERYO (+ SWAP2, per source) |
| `HttpGameServlet.java:298-303` | DPENTE + DKERYO + GO (no GO9/GO13) |
| `MobileGameServlet.java:222-225` | DPENTE + DKERYO (no Go at all) |
| `ServerTable.java:1930-1938` | DPENTE + DKERYO + GO + GO9 + GO13 + SWAP2 |
| `tb/mobileGame.jsp:745,918,1213` | DPENTE + DKERYO + SWAP2 (no Go) |

Collapse onto one descriptor flag. Related consumers that synthesise `moves[0]` from
`getCenterMove(game)` and are only correct when the flag is false —
`MySQLPenteGameStorer.java:785,1071` and `MySQLWebDbStorer.java:420` — must be audited
in the same change (`map-java-db-wire.md` §C2, §C8).

**Why this one is visible.** It is a genuine bug fix, so by definition somebody's
rendering changes. Concretely: `MobileGameServlet` omits the Go family, so archived Go
games served to the mobile game-database endpoint currently get a spurious synthesized
centre stone that the web endpoint does not add. Unifying removes it — visibly.

**Why it is nonetheless safe and cheap.** It is fully self-contained and depends on
nothing else in this plan; it can be deferred to *any* later point without blocking a
single other stage (Stage 2 deliberately leaves each divergent site on its own literal
list). Ship it as its own release with its own changelog line.

**Verify.** Before/after diff of the rendered move list for one archived game per
affected family (Go, Go9, Go13, D-Pente, D-Keryo, Swap2-Pente, Swap2-Keryo) on **both**
`HttpGameServlet` and `MobileGameServlet`, plus the `tb/mobileGame.jsp` replay. Pin the
unified member list in a test *before* changing any call site (Android's precedent:
that project found a real `isSwap2` divergence and deliberately kept the legacy path
rather than silently unifying — `map-android.md` #4, risk flag 4).

**Rollback.** Branch revert; no persisted state is written by this change.

**Size.** S — 5 files, ~40 lines.

---

### Stage 4 — Boundary validation + KOTH sentinel and name fixes

**Scope.**
- Add `GridStateFactory.isKnownGame(int)` = `gamesById.containsKey(id)` (covers live +
  speed + TB uniformly, unlike `isValidGame` which is 1..32 and returns false for every
  legitimate TB id — `:400`).
- Validate the client-supplied id at the three unguarded wire entry points:
  `ServerTable.java:910`, `:976`, `:284` (from `DSGArenaCreateTableEvent`). On failure,
  respond with the existing `DSGChangeStateTableErrorEvent` (already constructed at
  `:951`) instead of throwing — turning a connection-killing exception into a normal
  error response.
- Replace `NewGameServlet.java:195`'s `game > getMaxGameId()` range check with
  `isKnownGame(game)`. Note this check is currently **default-closed** against new ids
  (`getMaxGameId()` returns `TB_RENJU` = 81), so until this edit lands no new-band id
  can be created turn-based — useful, and worth keeping in mind if you want to sequence
  new games web-first.
- Validate in `webdb/CollectionHandler` / `GameSearchHandler` before
  `createGridState(g.game, …)` (`MySQLWebDbStorer.java:69,187`), which today NPEs.
- Fix the KOTH sentinel bug: `MySQLKOTHStorer.getEventId` returns `-1` on miss
  (`:31`) but all four `CacheKOTHStorer` call sites guard `if (hill_id == 0) return;`
  (`:111,147,202,226`), so an unregistered game writes/reads hill rows under
  `koth_id = -1`. Make the sentinel consistent and guard `<= 0`.
- Reconcile the three KOTH event-name strings: `MySQLGameVenueStorer.KOTH_EVENT =
  "King of Hill"`, `MySQLKOTHStorer` queries `name = "King Hill"`, live DB rows read
  `"King of the Hill"`. **Read-only first**: `SELECT eid, name, game FROM game_event
  WHERE name LIKE '%Hill%'` on production before choosing the canonical string; any
  change here must be additive (write the canonical name, read with an `IN (...)` of all
  three) so no existing hill loses its rows.

**Why invisible.** Every change either rejects an id that today crashes, or fixes a
sentinel/name path that is only reachable for an id not yet registered. No legacy id
changes behaviour. **Caveat:** the KOTH name reconciliation touches live data resolution
and must be additive-read; that is the one part of this stage requiring a production
`SELECT` before the code is written.

**Verify.** Unit tests driving `changeTableState` with ids `0, 33, 49, 50, 82, 999`
asserting an error event rather than an exception. `SELECT DISTINCT koth_id FROM koth`
before/after (no `-1` rows should exist today; confirm). Local-stack KOTH join/leave
smoke for one live and one TB game.

**Rollback.** Branch revert. The KOTH name change is additive-read, so reverting the code
leaves no orphaned rows.

**Size.** S/M — ~8 files, ~120 lines.

---

## 6. Stages 5–9

### Stage 5 — Boot-time materialization + `/api/games` metadata (additive, nobody reads it yet)

**Scope.** Two additive pieces, both derived from the in-code descriptors:

1. **DB materialization.** New table, written idempotently at boot next to the existing
   `registerAllGames(2)` call in `DSGContextListener.java:117-119` (same
   `catch (Throwable)` wrapper, so a bad descriptor cannot brick startup — `map-java-core.md`
   class F, `:119-141`):
   ```sql
   CREATE TABLE game_registry (
     game        SMALLINT UNSIGNED NOT NULL PRIMARY KEY,
     family_id   SMALLINT UNSIGNED NOT NULL,
     variant     ENUM('LIVE','SPEED','TB') NOT NULL,
     name        VARCHAR(64)  NOT NULL,
     display_name VARCHAR(64) NOT NULL,
     grid_size   TINYINT UNSIGNED NOT NULL,
     legacy_safe TINYINT(1) NOT NULL DEFAULT 1,
     active      TINYINT(1) NOT NULL DEFAULT 1
   ) ENGINE=MyISAM;
   ```
   Note it is `SMALLINT` from birth — no widening needed later for this table.
   **Design decision, stated explicitly: this table is a materialized view, never an
   authority.** Nothing in the server reads it to decide behaviour. It exists so that
   ops/admin JSPs and reporting can join on it, and so the "one source of truth,
   self-configuring at boot" property the project already relies on for `game_event`
   extends to game metadata. Making the DB authoritative would mean a DB row edit could
   change game rules — explicitly rejected.
2. **Read-only JSON endpoint** `/api/games`, mounted alongside the existing
   `/api/db/*` (**[verified here]** `web.xml:572`, helper `org/pente/webdb/JsonHttp.java`).
   Emits the descriptor list: `{game, familyId, variant, name, displayName, gridSize,
   stonesPerTurn, hasCaptures, singleGameSet, firstMoveOffCenter, active}`. No auth, no
   writes, cacheable.

Also in this stage: make `MySQLGameVenueStorer.registerGame` descriptor-driven
(it currently hardcodes `speedGame = baseGame + 1; tbGame = baseGame + 50` at
`~:246-268`), so registering a family with a non-doubled id layout works.

**Why invisible.** Purely additive: a new table nothing reads, a new endpoint nothing
calls. `registerGame`'s rewrite produces byte-identical rows for all 16 legacy families.

**Verify.** Boot the local stack twice; `SELECT * FROM game_registry` must contain
exactly 48 rows and be identical between boots (idempotency). Diff `game_event` before
and after — must be unchanged. `curl`-equivalent fetch of `/api/games` compared against
`GridStateFactory` constants in a test.

**Rollback.** `DROP TABLE game_registry` + unmap the servlet. Nothing depends on either.

**Size.** M — 1 DDL, ~2 new Java classes, ~1 modified.

---

### Stage 6 — Web/JS client consolidation (JSP, hand-written JS, both React apps)

**Scope.** All of this is server-deployed, so it ships atomically with a release — there
is no "old client" problem on the web.

- **JSP/JS**: replace the raw magic-number chains that bypass `gameConstants.jspf`'s
  already-good `GAME.*` object — `tb/gameScript.js:83,577,600`,
  `tb/mobileGame.jsp:660,670,745,918,1213` and the ~20 `game === 63` (TB_CONNECT6) sites
  at `:226,236,733,740,764,778,844,910,954,976,980,992,1153,1205,1250,1262,1327,1329,1353`
  — with `GAME.*` symbols, then with descriptor flags served from `/api/games`.
  Guard the `getDisplayName`/`getGameName` null-renders at `viewLiveGames.jsp:26`,
  `mobile/game.jsp:120`, `kothBox.jsp:88`, `admin/tb/games.jsp` so pages stop printing
  the literal string `"null"`.
- **`js/boardCommon.js`**: already correct (symbolic, `default: throw`, and the only
  unknown-id regression test in the stack at `boardCommon.test.js:82-85`). Leave the
  shape; only extend its family table from the registry. **Use it as the template for
  every other client.**
- **`react_live_game_room`**: `src/game/boardGeometry.js` — replace `variantKey`'s
  `<`-chained ladder and its unbounded `return 'swap2-keryo'` fallback (`:27-41`) with an
  explicit id→descriptor map plus an **explicit unknown branch**. This is the highest-value
  single edit on any client: that fallback currently selects `VARIANT_RULES` in
  `GameClass.js:377,399,418`, so an unrecognized id renders a fully playable board running
  the Keryo capture engine with no crash and no console warning (`map-react.md` §1E).
  Also fold in the four *independent* partitions in `GameClass.js` (`:118` critical-captures
  six-id list, `:285` `#isDPente`, `:288` `isConnect6`, `:292` `#isSwap2`, `:297`
  `isRenjuGame`), `TableClass.js:10-22,75,100,129,132,142`, `utils.js:7-33`, and
  `SettingsModal.js:119`'s `%2 === 0 ? game-1` normalization. Drive `STANDARD_GAME_IDS`
  (`boardGeometry.js:51`) from `/api/games`.
- **`react_mmai`**: minimal only — port the same explicit-map + hard-unknown shape into
  `src/game/GameClass.js`'s `setGame()` threshold ladder and its duplicated
  `{3,4,17,18,25,26}` capture list (`map-react-android.md` #11-12). **Do not** port the
  whole `boardGeometry.js` module or touch `MMAIWASM/Ai.cpp` — react_mmai plays only the
  12 ids in `MMAIPlayer.SUPPORTED_GAMES`, and an unsupported id is rejected up front by
  the server-side allow-list, so it can never receive a new game id it doesn't know.
  (See OV-3: react_mmai was only partially mapped.)
- **Server-rendered enumeration**: convert `getAllGames()`'s five callers off the
  49-slot-array-with-leading-null shape onto a `List<Game> getRegisteredGames()`
  (`PlayerStatsDialog:315-324` — which also fixes its live index-as-id bug —
  `followersing.jsp:68`, `newTourney.jsp:118`, `broadcast.jsp:73`, `NewDialog2.java:19`).
  This is the edit that makes ids ≥ 100 renderable at all.

**Why invisible.** Same 48 games, same names, same order, same rules. The only behaviour
change is for ids that do not exist yet.

**Verify.** `npm test` in both React apps (`boardGeometry.test.js:46-49,80-82` already
pins "unknown id does not crash" for `gridSizeForGame`/`isGoBoard` — extend it to assert
`variantKey` now *fails loudly* rather than returning `'swap2-keryo'`). Node test for
`boardCommon.js`. For the JSP layer: render every game's board page for all 48 ids on the
local stack and diff the generated HTML against a pre-change capture.

**Rollback.** Branch revert per app; the React apps are independently deployable.

**Size.** M/L — the largest code stage, but split cleanly into 4 independent PRs
(JSP/JS, lgr, mmai, server enumeration APIs).

---

### Stage 7 — Mobile hardening release ⚡ START THIS IN PARALLEL WITH STAGE 0

**Scope — deliberately minimal. This is NOT "add the new games to mobile".** It is a
crash-proofing release whose only user-visible effect is that an unrecognized game shows
as "Unknown game" instead of taking the app down.

Android (`pentelive-android`):
- `liveGameRoom/Table.java:165-177` `shouldTimerRun()` — the confirmed NPE. Null-guard it.
- `Table.java:1011-1012` `getGameName()` — return a localized "Unknown game" instead of `null`.
- `TableListAdapter.java`, `LiveTableFragment.java`, `ArenaJoinRequestAdapter.java:36-127`
  — audit for the same unchecked `.get()` pattern (`map-android.md` flags these as
  not-re-verified; treat as must-check, see OV-2).
- `ArenaTableSetupDialog.java:66` `spinnerPosition*2+1` → look the id up from an explicit
  `(id, label)` list instead of computing it.

iOS (`penteLive-iOS/test1`):
- `HelperClasses.swift:135` and `:624` — replace `Table.gameNames[game]!` with a safe
  accessor returning an "Unknown game" bucket. This is the crash.
- `gameColor()`'s unbounded final `else` (id ≥ 31 → Renju's colour) → explicit default.
- `TableSetupView` / `ArenaTableSetupView.swift:71,257,270,283,284` — replace
  `row*2+1`, `game/2`, `Table.gameNames.count/2` with an explicit list; `count/2` already
  silently truncates if the dict size is ever odd.
- Fold `LobbyViewController.gameNames` (already stale: missing Renju) and
  `SocialViewController.gameNames` (has an unshared `+50` scheme) onto one table.
  Leave `BoardVariantMapping.variant(forGameType:)`'s **deliberate, documented** Pente
  fallback exactly as it is — it is the one intentional unknown-id contract in the app.

**Optional, ~5 lines, high future value:** have both apps populate the existing
`ClientInfo` on `DSGLoginEvent` with an app version string. **[verified here]** no client
populates it today and there is no other version signal (G8). Gson treats an absent field
as `null`, so this is backward compatible in both directions. Once the installed base
reports a version, the server can hide new games from old builds outright rather than
relying on their "Unknown game" fallback.

**Why invisible.** No id set changes, no server dependency, no new games. Users see
nothing until they meet an unknown id — which cannot happen yet.

**Verify.** Android: unit tests driving `shouldTimerRun`/`getGameName` with ids
`0, 33, 99, 100, 999`; extend the existing `VariantPredicateEquivalenceTest` harness.
iOS: XCTest equivalents. Manual: point a build at the local stack with a deliberately
injected shadow id and confirm the lobby lists "Unknown game" and does not crash.

**Rollback.** Standard app-store release rollback. No server coupling means a bad mobile
release cannot damage server state.

**Size.** M — two apps, ~10 files each, no architectural change.

**Schedule note.** This stage's value is entirely a function of *install-base
penetration*, which is a wall-clock process outside anyone's control. Ship it as early as
possible — ideally before Stage 1 lands — so the clock starts running while the server
work proceeds.

---

### Stage 8 — Mint the first new family outside the legacy band

**Scope.** Per new game, this should now be a *data* change:

1. Add the family's descriptors to the registry with ids from the 100+ band, e.g.
   `{live:100, speed:101, tb:102}`, `legacySafe=false`, `active=true`.
2. Add its rules to the `createGridState` factory (`GridStateFactory.java:262-381` — the
   only `switch (game)` in the codebase; by this stage it dispatches per-descriptor).
3. Add it to the web clients' registry tables (Stage 6 made these `/api/games`-driven,
   so ideally this is zero client code).
4. Boot: `registerAllGames` creates its `game_event` rows automatically; `addServerGames`
   creates its `dsg_server_game` offerings automatically (`DSGContextListener.java:125-138`).

**The `legacySafe` gate is what makes this silent.** The flag must exclude the game from
exactly the enumeration APIs that feed old mobile builds: `getNormalGames()`,
`getSpeedGames()`, `getTbGames()`, `getDisplayGames()` as consumed by
`IndexResponse.java:419-420,470-472`, `KothResponse.java`, `WhosonlineResponse` and
`AiGameResponse`, and the `int[] liveGames` array passed to `addServerGames` for the live
room servers. Web/React paths read the full registry; legacy mobile paths read the
`legacySafe` subset.

**Recommended sub-ordering (lowest risk first):**

- **8a — turn-based / web-only.** Offered on the web and in the personal-collection and
  turn-based subsystems, excluded from every live-room offering and from the legacy
  mobile enumerations. Old mobile builds never see it. **Pre-gate: OV-4 must pass.**
- **8b — live room, after Stage 7 penetration.** Only once telemetry (or, absent
  telemetry, a deliberate waiting period) says the pre-hardening install base is small
  enough. Before 8b, a single web player creating a live table with id 100 crashes every
  pre-Stage-7 Android and iOS client in that room (G7). There is no server-side way to
  prevent that without the version signal from Stage 7's optional piece.

**Why the first sub-stage is invisible to old clients.** They are never sent the id: the
server does not enumerate games (G2), the game is absent from their offerings, and their
own hardcoded pickers cannot select it.

**Verify.** With a shadow game registered but `active=false`, run the full local smoke
suite and confirm zero diffs in every legacy surface. Then flip `active=true` and check
`game_event`/`dsg_server_game` gained exactly the expected rows. Run an **old-build**
Android and iOS client against the local stack for the whole exercise — this is the
acceptance test that matters.

**Rollback.** Flip `active=false` and redeploy. Any games already played under the new id
persist harmlessly (their rows are simply unreferenced); ids are never reused, so
re-enabling later is safe.

**Size.** S per game, once the machinery exists.

---

### Stage 9 — DB widening (deferred; expand → allocate → contract)

**Only needed to go past id 255.** With the 100..255 band that is ~52 more families —
so this stage should stay unbuilt until a concrete need appears. `docs/adding-a-new-game-playbook.md`
should record 255 as the current hard ceiling and point here.

**Expand.** `ALTER TABLE … MODIFY game SMALLINT UNSIGNED NOT NULL` on the five TINYINT
columns. Java fields are already `int` (G4), so **no code change accompanies this** — it
is a pure, independently revertible DDL step. Cost is wildly uneven **[verified here]**:

| Table | Rows | Data / Index | Expected ALTER |
|---|---|---|---|
| `dsg_server_game` (PK part) | 197 | ~0 | instant |
| `game_event` | 1 034 | ~0 | instant |
| `dsg_player_game` (PK part) | 54 622 | 2 / 1 MB | seconds |
| `pente_game` | 1 252 528 | 72 / 168 MB | ~1–2 min |
| **`pente_move`** | **35 775 023** | **1 024 / 2 539 MB** | **full MyISAM rebuild — maintenance window, ~3.5 GB free space, table locked** |

Do the four cheap tables in one release; schedule `pente_move` separately. Two of the
five are PRIMARY KEY components on MyISAM, so those are full rebuilds regardless of size.
Run against the replica first (`docker-compose-replica.yml`) to measure.

**Allocate.** Only after all five are widened *and the replica has caught up* does the
registry's id validator start permitting ids > 255.

**Contract.** Remove the `id <= 255` assertion from the registry invariant test. That
assertion is the entire contract phase — there is no dual-write to unwind, because ids
are never renumbered.

**Why invisible.** Widening a column is transparent to every reader; no value changes.

**Verify.** `SHOW CREATE TABLE` before/after; row counts before/after; `CHECKSUM TABLE`
on the small ones; full app smoke on the replica before touching primary.

**Rollback.** `MODIFY game TINYINT UNSIGNED` — safe as long as no id > 255 has been
allocated yet, which the ordering guarantees.

**Size.** S in code (zero), L in ops for `pente_move` alone.

**Explicitly NOT in scope:** reconciling the two conflicting DB conventions
(`pente_game` stores turn-based games under the **base** id with TB-ness encoded as
`gid >= 5e13`; `tb_game` stores the **TB** id — `map-java-db-wire.md` §C2/§C3). It is the
highest-risk, most migration-heavy item in the whole inventory and **it is not on the
critical path to removing the cap**. A new family simply declares its live and TB ids and
follows the existing conventions per table. Leave it alone. If it is ever tackled, it is
a separate project with its own plan.

---

## 7. Touchpoint-class → stage mapping

Cross-check that every pattern class in the map reports has a home.

| Touchpoint class (map report) | Stage |
|---|---|
| `allGames[]`/`gridStates[]`/`tbGames[]` positional addressing (`map-java-core.md` class D) | 1 |
| `getGame`/`createGridState`/`getColor`/`getGameId`/`getNumGames` (class D/E) | 1 |
| `+1`/`-1`/`%2` speed pairing (class C) | 2 |
| `+50`/`-50`/`>50` TB arithmetic, ~14 sites incl. JSP (class B) | 2 |
| `TB_START` private → ~14 hardcoded `50` literals | 2 |
| Provably-equivalent duplicate id sets (`ServerTable:2778`, `:3650`) | 2 |
| `FastMySQLDSGGameLookup` / `RatingsGrapher` TB→base decomposition (8 copies) | 2 |
| The 5 divergent opening predicates (`MASTER` §2) | **3** |
| Unvalidated wire ids (`ServerTable:910,976,284`), `NewGameServlet:195`, webdb handlers | 4 |
| KOTH `-1`/`0` sentinel; 3-way KOTH event-name drift | 4 |
| `registerGame`'s duplicated `+1`/`+50` (`MySQLGameVenueStorer:246-268`) | 5 |
| Boot-time registration extension (`registerAllGames`, `addServerGames`) | 5, 8 |
| JSP raw-literal chains (`gameScript.js`, `mobileGame.jsp` ×20 Connect6 sites) | 6 |
| `getDisplayName` null → literal `"null"` renders (4 JSPs) | 6 |
| React lgr `variantKey` unbounded fallback + 4 independent partitions | 6 |
| react_mmai `setGame()` ladder + capture list | 6 (minimal) |
| `getAllGames()` 49-slot array shape; `PlayerStatsDialog:315-324` index-as-id | 6 |
| Android `Table.shouldTimerRun` NPE, `gameNames`, `row*2+1` | 7 |
| iOS force-unwraps, `gameColor()` unbounded else, `row*2+1`/`count/2` | 7 |
| Picker-position-as-identity persistence (both mobile platforms) | 7 |
| Legacy-mobile enumeration gating (`legacySafe`) | 8 |
| 5 × `TINYINT` columns | 9 |
| Wire codec | **never** (G1) |
| `pente_game` vs `tb_game` convention split; `gid >= 5e13` | **out of scope** |
| Legacy AWT/applet clients (`GameBoardFrame:939`, `GameBoard:226`, `(game-1)/2`) | **out of scope** — dead code; if they are still live, they need Stage 7-style treatment and that is an open question |

---

## 8. Silent-rollout verdict — honest assessment

**Short answer: yes for turn-based / web-only games; no for live-room games without one
small mobile release first. That release is unavoidable, but it is tiny and it does not
have to know anything about the new games.**

The reasoning, grounded strictly in the mapped unknown-id behaviours:

**Why silent works at all.** The server never enumerates games to clients (G2). Old apps
learn about a game only by seeing a table with that id. Their pickers are bounded by
hardcoded arrays (Android: six `strings.xml` `<string-array>`s + `Table.gameNames`;
iOS: `GameEnum` + three name tables), so they can never *create* a new id. And the wire
carries any `int` unchanged (G1). So visibility is entirely a server-side choice — which
is what the `legacySafe` gate operationalizes.

**Why turn-based / web-only is (probably) silent.** A TB/web-only game is absent from
`dsg_server_game` live offerings and from the legacy mobile enumerations, so no old client
can list it, join it, or see a table carrying it. Server-side, unregistered-id handling on
the offerings path is already a graceful skip (`MySQLServerStorer.java:95-104` inserts zero
rows). **This is contingent on OV-4 below** — the mobile *JSON* surfaces (ratings lists,
KOTH, TB game lists) were not exhaustively verified for unknown-id safety, and the
`legacySafe` gate must cover every one of them.

**Why live-room is not silent today.** The moment one web player creates a live table with
id 100, that table is broadcast to everyone in the room. Old Android hits
`Table.java:165-177` `gameNames.get(game).contains(...)` → **NPE, app down**. Old iOS hits
`Table.gameNames[game]!` → **trap, app down** (G7). Neither has a fallback. And there is
**no server-side workaround**, because there is no client-version signal on the wire
(G8, verified: `DSGLoginEvent`'s `ClientInfo` has no version field and no client populates
it at all). You cannot filter what you cannot identify.

**So the honest cost is exactly one mobile release, and it is a cheap one.** Stage 7 ships
null-safety and an "Unknown game" bucket — roughly 10 files per app, no architectural
change, no knowledge of any future game. After it has penetrated the install base, new
live games appear on old builds as an unplayable-but-harmless "Unknown game" row, forever,
with no further app updates ever required. That is the end state requirement (3) asks for,
and it is reachable.

**It is not a *forced* update in the hard sense** — no version wall, no "update to
continue". Existing games keep working on every old build indefinitely, because nothing
they already know about changes. It is a *soft* prerequisite: ship it early, let natural
updates do the work, and gate Stage 8b on penetration rather than on a deadline.

**The one thing that would make it truly free** is the optional 5-line addition in Stage 7
(populate `ClientInfo` with an app version). With it, the server can suppress new-game
tables entirely for pre-hardening builds rather than relying on their fallback — belt and
braces, and the mechanism for any future compatibility break. Worth the 5 lines.

---

## 9. Open verification items

These are places where the map reports are silent or self-flagged as unverified. **Do not
treat any of them as known.**

- **OV-1 — `pente_move` phantom-band sweep not run.** The G6 check covered six tables;
  `pente_move` (35.8M rows) was skipped to avoid a long full scan. Run
  `SELECT COUNT(*) FROM pente_move WHERE game BETWEEN 33 AND 50 OR game > 81;` in a
  maintenance window before Stage 1. Expected 0 (it is denormalized from `pente_game.game`,
  which is clean), but confirm.
- **OV-2 — Android `TableListAdapter.java` and `LiveTableFragment.java` unverified.**
  `map-android.md` explicitly flags these as read-then-lost-to-compaction and never
  re-confirmed. They consume `Table.gameNames` the same way `shouldTimerRun()` does and
  likely share the unchecked-`.get()` crash. Must be audited in Stage 7.
- **OV-3 — `react_mmai` only partially mapped.** `map-react.md` ends at line 84 with Part 1
  (`react_live_game_room`) only; the react_mmai section survives only as two low-confidence
  rows in `map-react-android.md` (#11-12), whose line numbers are self-flagged as
  unreliable. Re-map before Stage 6's react_mmai sub-PR. Also unmapped:
  `react_mmai/MMAIWASM/Ai.cpp`, which is stated to hardcode the same partition independently.
- **OV-4 — mobile JSON/TB surfaces' unknown-id behaviour.** Whether an unrecognized game id
  arriving through the mobile REST responses (`IndexResponse` ratings sections,
  `KothResponse`, TB game lists, `AiGameResponse`) degrades gracefully on old Android and
  iOS builds is **not** established by any map. `map-android.md` establishes only the
  live-room crash. Android's TB path appears to dispatch on display-*name* strings
  (`Game.java`) rather than ids, and iOS's `BoardVariantMapping` has a documented Pente
  fallback — both suggestive of graceful degradation, neither verified.
  **This is the gate on Stage 8a.** Verify by registering a shadow game on the local stack
  and driving an old-build Android and iOS client through: home screen, ratings panel,
  KOTH screen, TB game list, notifications.
- **OV-5 — are the AWT/applet clients still reachable?** `GameBoardFrame.java:939`
  (`(game-1)/2` dense-slot mapping), `client/web/GameBoard.java:226`, `PlayerStatsDialog`,
  `NewDialog2` all assume dense ids and would break on ids ≥ 100. The maps treat them as
  legacy but do not establish that they are dead. Confirm before Stage 6; if live, they
  need Stage 7 treatment.
- **OV-6 — production KOTH event-name distribution.** Three strings disagree
  (`"King of Hill"` constant, `"King Hill"` query, `"King of the Hill"` in the DB). Run the
  read-only `SELECT eid, name, game FROM game_event WHERE name LIKE '%Hill%'` on production
  (not just local) before writing Stage 4's reconciliation.
- **OV-7 — the `switch (game)` in `createGridState`** (`GridStateFactory.java:262-381`) is
  left intact by this plan through Stage 5. Whether it can be cleanly converted to
  per-descriptor construction, or whether some cases share mutable prototype state, was not
  examined. Assess before Stage 8.

---

## 10. Residual risks

1. **Stage 3 is a real behaviour change on a live surface.** Unifying the five opening
   predicates changes what the mobile game-database endpoint renders for Go games. It is a
   bug fix, but it will look like a regression to anyone who has memorised the current
   output. Mitigate with the before/after move-list diff and an explicit changelog entry.
2. **Stage 8b crashes every un-updated mobile client, and there is no undo for a crash.**
   Rolling back the server stops *new* crashes but does not un-crash anyone. Penetration
   judgement is the whole risk; the Stage 7 version signal converts it from judgement into
   measurement, which is why it is worth the 5 lines.
3. **`legacySafe` is a new invariant with many enforcement points.** Missing any one
   enumeration API that feeds legacy mobile leaks a new id to old builds. This is a
   whitelist-by-omission design and needs a dedicated test asserting that every
   legacy-mobile response contains only `legacySafe` ids.
4. **`pente_move`'s ALTER is a genuinely large ops event** (35.8M rows, 3.5 GB rebuild,
   MyISAM table lock). Deferred to Stage 9 and needed only past id 255 — but if it is
   ever needed urgently, it will not be quick.
5. **Two conflicting DB conventions remain** (`pente_game` base-id + `gid >= 5e13`
   vs `tb_game` TB-id). Deliberately out of scope; every new family must follow both,
   which means the trap is preserved, not removed. Anyone adding a game must be told.
6. **`BrainkingImporter.BASE_GID = 41000000000000L`** sits only 1.2e13 below the
   `5e13` turn-based `gid` boundary. Unrelated to game ids, but it is the other latent
   ceiling in this system and nothing in this plan addresses it.
7. **Silent-wrong-rules fallbacks survive on any client not reached by Stages 6–7.** The
   iOS `penteVariant(for:)` → Pente default and `BoardVariantMapping`'s documented fallback
   are intentional and should stay; but every *unintentional* one converted to a hard
   failure trades a silent wrong game for a visible error — which is correct, and will
   generate support tickets that look like new bugs.
8. **The registry stays hand-maintained in code.** This plan removes the *numeric* ceiling
   and the arithmetic coupling, but adding a game still means editing Java (descriptor +
   rules), and the `strings.xml`/`GameEnum` hand-maintained lists on mobile persist until
   Stage 7 (and fully only if mobile later consumes `/api/games`). This is accepted, not
   solved.
9. **Order dependence in enumeration APIs is load-bearing and easy to break.**
   `IndexResponse.java:419-420`'s trailing `// odd first` comment and
   `CacheKOTHStorer.java:150`'s `ALL_GAMES` iteration both observe array order. Stage 1's
   map conversion must preserve it explicitly (`LinkedHashMap` or separate ordered lists),
   and Stage 0's harness must pin it.

---

## 11. What this plan deliberately does not do (YAGNI)

- **No renumbering. No data migration.** Ever. The entire risk profile of this plan
  depends on that.
- **No wire-protocol change, no versioning handshake, no capability negotiation.** G1/G2
  make it unnecessary. The optional `ClientInfo` version string is not a protocol change —
  it is populating a field that already exists and is currently `null`.
- **No client rebuild.** Stage 7 is ~10 files per app of null-safety, not a re-architecture.
  The `Variant`/`Variants` module already sitting unwired in `pentelive-android/rules/`
  is *not* wired into production by this plan — that is a separate, already-tracked
  migration ("Task 8") whose author deliberately left one predicate (`isSwap2`) on the
  legacy path after finding a real divergence. Respect that; do not bulk-unify it.
- **No `boardGeometry.js` port to react_mmai.** react_mmai only ever plays the 12 ids in
  `MMAIPlayer.SUPPORTED_GAMES`; the server rejects anything else up front. Minimal
  hardening only.
- **No DB-as-source-of-truth.** `game_registry` is a materialized view.
- **No `pente_game`/`tb_game` convention reconciliation.** Highest-risk item in the
  inventory, not on the critical path.
- **No DB widening until it is actually needed.** 100..255 is ~52 more families.
- **No admin UI, no per-game feature flags beyond `active`/`legacySafe`, no plugin system
  for game rules.** Adding a game remains a code change; only the *numeric ceiling* is
  being removed.

---

## 12. Requirement check

| Requirement | Met by |
|---|---|
| (1) Low risk / minimal modification per stage | Every stage is a single-file or single-layer revert; Stages 0–2 and 4–5 have provable zero-diff verification |
| (2) Earliest stages 100% invisible | Stages 0, 1, 2 are invisible by construction (G6 verified); first visible is Stage 3, which is deferrable to any later point |
| (3) Existing ids + old apps keep working indefinitely | Nothing is renumbered; no wire change; old builds keep their hardcoded tables valid forever |
| (4) No numeric ceiling in the end state | Stage 9 removes the last one (255). Ids are `int` end-to-end in Java (G4) and unbounded on the wire (G1) |
| (5) Honest silent-rollout assessment | §8: silent for TB/web-only (pending OV-4); one small mobile hardening release required before live-room rollout, with no version wall and no forced update |

