# Removing the game-count cap — protocol/capability-forward staged strategy

**Approach name:** Capability-Gated Edge Visibility over a Frozen Legacy ID Window

**Status:** design only. Produced from the six map reports in this directory plus targeted
read-only verification this session (source `Read`/`grep`, `SELECT`-only DB queries against
`penteorg-main_db-1`). No repo file was modified; no DB write was issued.

Every factual claim below is either cited to a `file:line` I read this session, cited to a map
report, or explicitly marked **[OPEN]** as an unverified assumption for the implementer.

---

## 0. Executive summary

Three findings from this session's verification pass invert the obvious design:

1. **There is nothing to translate at the wire.** The map reports already established that the
   codec is id-agnostic Gson JSON (`map-java-db-wire.md` §D.1, §E.4; `map-android.md` "Wire
   protocol"). Combined with the decision to **never renumber a legacy id**, this means a
   translation layer keyed by client capability has *no id to translate*. What old clients
   actually need is not translation but **not being shown modern games at all** — because their
   unknown-id behaviour is *crash*, not *degrade* (Android `Table.shouldTimerRun()` NPE,
   `map-android.md` #6; iOS `Table.gameNames[game]!` force-unwrap trap,
   `ios-game-id-touchpoints.md` §2). So the compat layer is a **visibility gate**, not a
   translation table. This is the single biggest scope reduction available.

2. **A per-room, per-client game list already ships on the wire and every modern client
   ignores it.** `ServerData` (`gameServer/core/ServerData.java:75-81`) carries
   `Vector<GameEventData> gameEvents`; `GameEventData` (`org/pente/game/GameEventData.java`)
   exposes `getGame()`/`getName()`/`getEventID()`. It is populated from `dsg_server_game` at
   `MySQLServerStorer.java:207`, copied per-room at `DSGContextListener.java:316-317`, and sent
   to every client on login at `ServerPlayer.java:192`
   (`loginEvent.setServerData(server.getServerData())`). Current consumption:
   - React lgr reads only `serverData.tournament` / `serverData.arena`
     (`react_live_game_room/src/redux_reducers/rootReducer.js:95-96`) — yet `gameEvents` **is**
     present in its own golden wire fixture (`src/protocol/__fixtures__/wire-fixtures.json:423`);
   - Android reads only `serverData.loginMessages`
     (`liveGameRoom/TablesAndPlayers.java:127-128`);
   - iOS reads only `serverData["loginMessages"]` (`RoomViewController.swift:364-365`);
   - the legacy AWT applet reads `getGameEvents().elementAt(0)`
     (`client/awt/MainRoomPanel.java:140,485`) and `Server.java:429` does `.get(0)` server-side.

   So the metadata-driven game list the "new clients negotiate a game list" idea calls for
   **already exists, already crosses the wire, and is already safely ignored**. It does not
   need to be invented; it needs to be *populated correctly* and *filtered per capability*.
   The only hard constraint is: **never emit an empty `gameEvents`** — `MainRoomPanel:140` and
   `Server.java:429` both index element 0 unguarded.

3. **Room membership is the one complete, cheap isolation boundary, and it is already a
   server-side chokepoint for both mobile apps.** Android fetches its room list from
   `https://…/gameServer/mobile/json/liveServers.jsp` (`LobbyActivity.java:199-201`) and builds
   `LiveGameRoom(server.name, server.port)` from the response (`LobbyActivity.java:252`); iOS
   fetches `https://…/gameServer/mobile/liveServers.jsp?iPhone`
   (`LobbyViewController.swift:87-91`). Both are rendered by one server-side builder,
   `LiveServersResponse.build(servers, rooms)`
   (`gameServer/mobile/LiveServersResponse.java`), over `Resources.getServerData()`. Per-room
   game curation is an existing, exercised mechanism: `DSGContextListener.java` registers
   `liveGames` for servers 1 and 37, a hand-curated `goGames` subset for server 46, and
   `Resources.startNewServer(tourneyID)` (`Resources.java:~85-110`) already mints whole rooms at
   runtime with a single `GameEventData`. Live DB confirms the curation is real:
   `dsg_server_game` server_id=1 holds all 32 live ids, servers 2..29 hold 1 game each.

**Consequence for the plan:** the cap is removed entirely on the server, invisibly, in six
stages that touch no client and no wire byte. The first player-visible stage ships a 17th game
**web-first into its own gated room**. Mobile apps are updated last and old builds never see
the new game — not because we lie to them, but because we do not list it for them.

**The one thing that is genuinely not free:** old Android/iOS builds cannot be made to *show*
a new game. Not a protocol limit — a client-code limit (both crash on an unknown id, neither
has a fallback). See §7.

---

## 1. Target identity model

### 1.1 Canonical internal identity

```java
final class GameDescriptor {
    int      id;            // stable, unique, never reused, never renumbered
    String   key;           // "renju", "renju.speed", "renju.tb" — human/config-stable
    String   familyKey;     // "renju"
    Variant  variant;       // LIVE | SPEED | TURN_BASED   (enum, not arithmetic)
    String   displayName;
    // behaviour flags, one field per predicate currently hand-copied:
    boolean  singleGameSet;         // replaces isSingleGameSet          (GridStateFactory.java:502-507)
    boolean  firstMoveOffCenter;    // replaces firstMoveCanBeOffCenter  (:522-532)
    boolean  negotiatedOpening;     // replaces the 5-way copied predicate
    int      ratingKFactor;         // replaces ServerTable k32Game      (:3650-3653)
    int      stonesPerTurn;         // replaces MMAIProtocol connect6    (:47-48)
    GridStateSupplier stateFactory; // replaces the switch               (:262-381)
}
```

`Game.java` already carries `id`, `name`, `speed` — so `variant`/`familyKey`/flags are
**additive fields on an existing class**, which is why Stage 2 is cheap. `map-java-core.md`
Class A already identifies `Game.java:3-37` as the natural seed.

### 1.2 Two id bands — the load-bearing decision

| Band | Range | Contents | Rule |
|---|---|---|---|
| **Legacy window** | `1..81` | the 16 existing families (live odd 1..31, speed even 2..32, TB 51..81) | **Frozen forever.** No id ever changes value. `33..48` stays permanently unallocated. |
| **Modern band** | `>= 1000` | every family added from now on | Allocated by the registry. No parity rule, no `+1`, no `+50`. `variant` is a field. |

**Why `33..48` is left empty rather than reclaimed.** After Stage 1 it *is* technically free
(today `allGames[33]` holds `TB_PENTE_GAME` — `map-java-db-wire.md` §E.1, the "id 33 is not
free" blocker). Reclaiming it is tempting because legacy arithmetic keeps working there: a live
game at 35 satisfies `isTurnbasedGame(35)==false` and `isSpeedGame(36)==true` correctly. But it
buys only 8 more families, it permanently re-entangles new games with the `%2`/`+50` scheme this
refactor exists to delete, and — worst — a missed *client-side* range check would classify id 35
as a plausible legacy id rather than an obviously alien one. **Recommendation: leave 33..48
empty.** Recorded as an available emergency bridge if a 17th family must ship before Stage 6
lands, with that exact tradeoff.

**The precondition that makes `>= 1000` safe.** In the modern band, legacy arithmetic gives
*wrong* answers: `isTurnbasedGame(1001)` → `true` for a live game; `isSpeedGame(1002)` → `false`
for a speed game. Therefore **no modern id may be minted until Stage 3 has removed every raw
`> 50` / `- 50` / `% 2` / `± 1` site server-side**, enforced by an automated guard (§Stage 3
verification). This is the single hardest gate in the plan and it is checkable mechanically.

**Ceiling, honestly stated.** After Stage 6 the binding limit is `SMALLINT UNSIGNED` = 65535 on
the widened columns (`tb_game.game` and `webdb_*.game` are already SMALLINT —
`map-java-db-wire.md` §A). 65535 games is not a practical game-count ceiling. Widening to `INT`
is available and additive, but it is a larger MyISAM rebuild for zero present value — **YAGNI,
do not do it**. If the requirement is read as literally "no ceiling", the honest answer is
"65 535, which is 4 000× the current catalogue".

### 1.3 Legacy-scoped vs registry-scoped enumeration APIs — the invisibility mechanism

This is the structural trick that makes every stage before Stage 7 provably invisible and makes
Stage 7's blast radius auditable in one grep.

Keep **every existing enumeration API frozen to the legacy window**:

- `GridStateFactory.LIVE_GAMES`, `TB_GAMES`, `ALL_GAMES` (`GridStateFactory.java:40-57,79-85`)
- `getDisplayGames() / getNormalGames() / getSpeedGames() / getTbGames() / getAllGames()` (`:430-457`)

Add **new registry APIs** alongside: `registry.allIds()`, `registry.familiesVisibleTo(cap)`,
`registry.descriptor(id)`.

Consequence: every one of the ~40 existing consumers of those arrays — the JSP pickers
(`leaderboard.jsp:28`, `tb/new.jsp:255`, `followersing.jsp:67`, `admin/newTourney.jsp:117`,
`broadcast.jsp:73`, `kothBox.jsp:57-71`), the mobile home screen
(`IndexResponse.java:419-420,470-472`), KOTH hill enumeration
(`CacheKOTHStorer.java:150,164,170,294,330`), the AWT applet pickers — **keeps its current
behaviour by construction, with no edit and no risk**. Only the call sites deliberately migrated
to `registry.allIds()` in Stage 7 (there are exactly two that must be:
`MySQLGameVenueStorer.registerAllGames` at `:699-707` and `DSGContextListener.addServerGames`
at `:125-138`) will ever see a modern game.

This also closes a real trap: if modern ids were simply appended to `LIVE_GAMES`,
`IndexResponse.buildKoth` would start pushing KOTH rows for modern games to **un-updated mobile
clients** on the home screen. Legacy-scoping the arrays prevents that by default rather than by
vigilance.

---

## 2. Where the compat layer lives, and where it does not

### 2.1 It does NOT live in the codec

`SocketDSGEventHandler`, `WebSocketDSGEventHandler`, `DSGEventWrapper`, and the three Gson type
adapters get **zero changes in every stage of this plan**. Justification:

- Ids are `int` → JSON number end-to-end; no narrowing, no bit-packing
  (`map-java-db-wire.md` §D.1 verified `grep` for `writeByte|writeShort|writeInt|…` in
  `org/pente/gameServer/event/` returns nothing).
- Legacy ids never change value, so there is no old↔new id mapping to apply.
- Per-connection re-encoding *is* technically feasible — each handler owns its own
  `outputQueue` and calls `new DSGEventWrapper(o).getJSON()` itself
  (`SocketDSGEventHandler.ObjectWriter`; `WebSocketDSGEventHandler.MessageWriter`), so a
  per-recipient rewrite would not break batching. **Reject it anyway**: the only thing it could
  do is present a modern game to an old client under a legacy id, which means that client
  applies the wrong rules engine and silently produces wrong game outcomes — precisely the
  failure mode `map-react.md` §1E and `MASTER-game-id-refactor-report.md` §3 identify as worse
  than a crash. Never lie about identity at the edge.

### 2.2 Capability capture — one place

`ServerPlayer.eventOccurred`, login branch (`ServerPlayer.java:92-97`, response built at
`:192`). `DSGLoginEvent` already carries a `ClientInfo` POJO
(`event/DSGLoginEvent.java`, `event/ClientInfo.java` — 5 `String` fields: `browser`,
`javaVersion`, `javaClassVersion`, `os`, `osVersion`).

Add one field: `private String capabilities;` (comma-separated tokens, e.g. `"gamecat1"`).
Gson tolerates unknown fields in both directions, so:

- **Old clients send no `capabilities`** → `null` → server maps to `Capability.LEGACY`.
  Verified that none of the three modern clients populates `ClientInfo` today: React lgr's login
  message declares `out: ['guest']` only (`src/protocol/messages.js:19`); `grep -i clientInfo`
  over the Android `app/src/main/java` tree and the iOS `test1` tree returns **zero hits**.
  So `null` is not a hypothetical default — it is what 100% of live traffic sends today.
- New client builds add `capabilities: "gamecat1"` — a one-line change per platform.

Store the resolved `Capability` on `ServerPlayer` (and mirror onto the handler if a
list-producer needs it without a `ServerPlayer` reference). **[OPEN]** the implementer must
confirm `ServerPlayer` is reachable from `ServerTable`'s broadcast paths; the maps do not cover
`ServerPlayer`'s full field set.

**Capability is client-asserted and unverifiable.** A hostile client can claim `gamecat1` and be
shown modern games it renders wrong. That is a self-inflicted UX bug, not a security issue —
accepted, recorded in §8.

### 2.3 Admission control — three lines that also close a live hole

`ServerTable.java:284` (`DSGArenaCreateTableEvent.getGame()`), `:910` and `:976`
(`changeStateEvent.getGame()`) pass a **client-supplied id straight into
`GridStateFactory.getGame()` with no validation** (`map-java-core.md` §e;
`map-java-db-wire.md` §E.2). Today this is reachable from the network and throws AIOOBE inside
the table event loop, or silently resolves to the wrong game in the 33..48 band.

Insert `registry.isOfferedTo(capability, id)` at all three. This simultaneously:
- fixes the existing unvalidated-input defect;
- guarantees an old client can never *create* a table with a modern id;
- guarantees a modern id can never enter a room that does not offer it.

### 2.4 Visibility gates — three list chokepoints

| # | Chokepoint | File | Who consumes it |
|---|---|---|---|
| 1 | per-room game list | `ServerData.gameEvents`, filtered where `ServerPlayer.java:192` attaches it | all live-room clients (currently ignored by all three modern ones) |
| 2 | **room list** | `mobile/LiveServersResponse.build(...)` + `httpdocs/gameServer/mobile/json/liveServers.jsp` (Android) and `httpdocs/gameServer/mobile/liveServers.jsp` (iOS) | Android `LobbyActivity.java:199-252`, iOS `LobbyViewController.swift:87-91` |
| 3 | mobile TB home screen | `IndexResponse.buildKoth` / `buildRatingStats` (`IndexResponse.java:419-420,470-472`) | Android + iOS TB app surfaces |

Chokepoint 2 is the **complete** boundary: a room an old app is never told about cannot leak a
modern game to it through any path. Chokepoints 1 and 3 are defence in depth.

Note chokepoint 3 already self-filters usefully: `addRatingStats` skips any game where
`gd == null || gd.getTotalGames() == 0`, so a modern game is invisible in mobile ratings until
that player has actually played it — which, gated, they cannot.

**[OPEN]** the two `liveServers.jsp` variants carry no client-version parameter today (Android
sends `?name2=&password2=`, iOS sends `?iPhone`). Gating them requires new builds to add a
`?caps=` parameter; absent parameter ⇒ legacy ⇒ modern rooms hidden. Fail-safe by default.

### 2.5 Retirement

The gate is data, not code, once Stage 7 lands: a room is modern-only if its `ServerData`
carries any modern id. Retirement is therefore **not a code deletion, it is a policy flip**:

1. When modern-capable app adoption crosses the chosen threshold, add the modern game's ids to
   the main rooms' offerings (a `DSGContextListener.addServerGames` argument change).
2. When the *last* legacy-capable client is judged extinct, delete `Capability` and the four
   filter call sites. The registry, the descriptors and the modern band all stay.

Because the gate is a filter over a list rather than a fork in the domain, there is no
"legacy path" to rot. That is the main argument for putting the gate at the list producers
rather than in `ServerTable`.

---

## 3. Stage table (overview)

| # | Stage | Player-visible? | Size |
|---|---|---|---|
| 0 | Pinning harness (tests only) | no | S |
| 1 | De-positionalise `GridStateFactory` | no | M |
| 2 | `GameDescriptor`: variant + family + behaviour flags | no | M |
| 3 | Route duplicated predicates through the registry, member lists preserved | no | M |
| 4 | *(optional, independently orderable)* Reconcile the 5 divergent member lists | **yes, small** | S |
| 5 | Capability capture + admission control + visibility gates | no | M |
| 6 | DB expand: `TINYINT` → `SMALLINT UNSIGNED` | no | S |
| 7 | Mint the modern band; ship family #17 in a gated web-only room | **yes — first required** | M |
| 8 | Client hardening + capability declaration (React ×2, Android, iOS) | yes, on updated builds | L |
| 9 | Gate retirement | yes (new game reaches main rooms) | S |

Stages 0–3 and 5–6 are strictly server-internal: no wire byte, no client file, no id value, no
row value changes. Stage 4 is the only pre-Stage-7 stage with any player-visible delta and it is
deliberately severable.

---

## 4. Stages in detail

### Stage 0 — Pinning harness (tests only)

**Scope.** No production code. Add characterization tests that freeze today's answers so every
later stage is provably behaviour-preserving.

- `org.pente.game.test.GameIdPinningTest` — a table-driven test asserting, for **every** id in
  `0..90` (deliberately including the 33..48 phantom band, 49, 50 and 82..90), the exact current
  outcome of: `getGame`, `getGameName`, `getDisplayName`, `createGridState(int)`,
  `createGridState(int,MoveData)`, `getColor`, `getCenterMove`, `isSpeedGame`,
  `isTurnbasedGame`, `getSpeedGame`, `getNormalGame`, `getNormalGameFromTurnbased`,
  `isValidGame`, `isSingleGameSet`, `firstMoveCanBeOffCenter`, `getGameId(String)`.
  Where today's outcome is an exception, **assert the exception** — including
  `getGame(33) == TB_PENTE_GAME` (the silent-wrong-answer, `map-java-db-wire.md` §E.1) and
  `getColor(n, 51)` → `ArrayIndexOutOfBoundsException` (`GridStateFactory.java:479-481`).
  Pinning the *bugs* is the point: Stage 1 must change them deliberately, not accidentally.
- `PredicateDivergencePinningTest` — asserts the current, *disagreeing* member lists of the five
  "negotiated / off-center opening" copies separately:
  `GridStateFactory.firstMoveCanBeOffCenter` (`:522-532`), `HttpGameServlet.java:298-303`,
  `MobileGameServlet.java:222-225`, `ServerTable.java:1930-1938`,
  `httpdocs/gameServer/tb/mobileGame.jsp:745,918,1213`. Modelled on Android's existing
  `VariantPredicateEquivalenceTest`, which is in-repo precedent for exactly this
  (`map-react-android.md` #13-15).
- Wire golden test: extend `react_live_game_room/src/protocol/__tests__/decode.test.js` against
  the existing `__fixtures__/wire-fixtures.json` to assert a `dsgChangeStateTableEvent` with
  `game: 99999` round-trips unchanged — pinning the "codec is id-agnostic" claim the whole plan
  rests on.

**Why invisible.** Tests only.

**Verification.** `ant test-one -Dtest=org.pente.game.test.GameIdPinningTest`. **Note:** the
`test` target in `build.xml:41-83` runs only 10 explicitly-named classes and **none of the
`org.pente.game.test.*` suite** (`RenjuFactoryTest`, `GridStateFactorySingleGameSetTest`,
`AllGamesTest` are all currently unwired). New pinning classes must be *added to the `test`
target*, otherwise they will silently never run in CI. `npx vitest run src/protocol` in
`react_live_game_room/`.

**Rollback.** Delete the test files.

**Size.** S — 2-3 new test classes, 1 test file edit, 1 `build.xml` edit.

---

### Stage 1 — De-positionalise `GridStateFactory`

**Scope.** `dsg_src/java/org/pente/game/GridStateFactory.java` only. Replace the three
positional/dual-addressed arrays with maps keyed by the **real** id. **Every id constant keeps
its exact current value.**

- `allGames[]` (`:139-154`, 49 slots, id-indexed at 1..32 but positionally packed at 33..48 with
  TB `Game` objects whose real ids are 51..81) → `Map<Integer, Game>` keyed by real id.
- `gridStates[]` (`:237`, length 49, built by the `for i=1..48` loop at `:240-243` which leaves
  33..48 permanently `null`) → `Map<Integer, GridState>` keyed by real id, built by iterating the
  registry rather than a dense range.
- `tbGames[]` (`:226-235`) — split its two conflated roles: keep the array as the *display list*
  returned by `getTbGames()`; delete its use as the arithmetic backing store for
  `getGame`/`createGridState` (`:386,:394`, the `(game-TB_START-1)/2` indexing).
- `getGame` (`:392-398`), `getGameName` (`:404`), `createGridState(game,MoveData)` (`:384-390`),
  `getColor` (`:479-481`) → map lookups with an explicit `IllegalArgumentException("unknown game
  id: " + id)` on miss.
- `TB_START` (`:61`) → `public`. Do **not** yet rewrite the ~10 external hardcoded `50` literals
  (`Tourney.java:123`, `KothResponse.java:61,83`, `IndexResponse.java:447,481`,
  `CacheKOTHStorer.java:96,177,213`, `MySQLGameVenueStorer.java:466`,
  `CacheTourneyStorer.java:815`, `broadcast.jsp:75`) — that is Stage 3, kept separate so this
  stage's diff stays inside one file.
- `getNumGames()` (`:422-424`, returns `allGames.length - 1` = 48, which is neither a family
  count nor a max id) has exactly 2 callers — resolve both explicitly, then delete or rename.

**Why player-invisible.** Every id value, every enumeration array, every display name and every
enumeration API output is byte-for-byte identical. The only behaviour changes are on inputs that
are *currently defects*: the 33..48 band stops silently resolving to turn-based games and starts
throwing; `getColor` on a TB id throws a named exception instead of AIOOBE. Both are unreachable
from any legitimate path — `map-java-core.md` §E confirms `gridStates[33..48]` are "silent
today". Pair with Stage 5's admission check so the one network-reachable route into that band
(`ServerTable.java:910/976`) is closed in the same release train.

**Verification.** `GameIdPinningTest` from Stage 0, with the ~6 intentionally-changed rows
updated in the same commit and each change reviewed as a line item. Existing
`RenjuFactoryTest`, `GridStateFactorySingleGameSetTest`, `MySQLGameVenueStorerTbLookupTest`
must pass unchanged. Boot the local stack (compile → bind-mount → restart
`penteorg-pente.org-1`) and confirm `registerAllGames` still writes the same `game_event` set:
```sql
SELECT game, name, COUNT(*) FROM game_event WHERE site_id=2 GROUP BY game, name ORDER BY game;
```
must be identical before and after (1..32 dense + 51,53,…,81 odd, per `map-java-db-wire.md` §A).

**Rollback.** Single-file revert. No data, no wire, no client artefact touched.

**Size.** M — one file, ~150 lines changed.

---

### Stage 2 — `GameDescriptor`: variant + family + behaviour flags

**Scope.** `org/pente/game/Game.java` (add `variant`, `familyKey`, and the behaviour flags from
§1.1) and `GridStateFactory` (build descriptors; reimplement the five arithmetic helpers on top
of them):

- `isSpeedGame(int)` (`:471`, currently `game < TB_START && game % 2 == 0`) →
  `descriptor(game).variant == SPEED`. Note `Game.java` **already carries a correct `speed`
  boolean** — the parity arithmetic is redundant today (`map-java-core.md` risk flag).
- `isTurnbasedGame(int)` (`:475`) → `variant == TURN_BASED`.
- `getSpeedGame(Game)` / `getNormalGame(Game)` (`:459-465`, `allGames[id±1]`) → family lookup.
- `getNormalGameFromTurnbased(int)` (`:467`, `game - TB_START`) → `descriptor(game).familyKey`
  → LIVE member.
- `isSingleGameSet` (`:502-507`) and `firstMoveCanBeOffCenter` (`:522-532`) read their flags.
- The 120-line `switch (game)` (`:262-381`) → per-descriptor `stateFactory`.

**Why player-invisible.** Pure reimplementation: for all ids in the legacy window each helper
returns exactly what the arithmetic returns today, which the flag values are chosen to
reproduce. ~60 call sites of these helpers are **not edited** — that is the design goal.

**Verification.** `GameIdPinningTest` unchanged and green (this is the strongest possible signal
here — the pinning table covers all five helpers across `0..90`). Add
`DescriptorArithmeticEquivalenceTest`: for every id in `1..81`, assert
`isSpeedGame(id) == (id < 50 && id % 2 == 0)` and `isTurnbasedGame(id) == (id > 50)` — i.e.
assert the new implementation still agrees with the old arithmetic **within the legacy window**.
Keep this test forever; it is the contract that legacy ids never change meaning.

**Rollback.** Two-file revert.

**Size.** M — 2 files, ~250 lines, plus one test.

---

### Stage 3 — Route duplicated predicates through the registry (member lists preserved)

**Scope.** Mechanical, one predicate at a time. Every site keeps **its own current answer**;
only the *mechanism* changes from a hand-copied OR-chain to a named registry-backed predicate.

- The 5 "negotiated / off-center opening" copies: `GridStateFactory.firstMoveCanBeOffCenter`,
  `HttpGameServlet.java:298-303`, `MobileGameServlet.java:222-225`,
  `ServerTable.java:1930-1938`, `tb/mobileGame.jsp:745,918,1213`. Because these three disagree
  today (`MASTER-game-id-refactor-report.md` §2), each gets a **distinct** registry-backed
  predicate preserving its own membership, e.g. `registry.offCenterFirstMove_archive(id)` vs
  `registry.offCenterFirstMove_liveTable(id)`. Ugly on purpose — the ugliness is the bug, made
  visible in one file instead of five.
- The ~14 raw `> 50` / `- 50` sites → `registry.isTurnBased(id)` / `registry.baseIdOf(id)`.
- The 3 hand-copied id sets: `ServerTable:2778` (`single_game`), `ServerTable:3650` (`k32Game`),
  `GridStateFactory:502` (`isSingleGameSet`) → descriptor flags.
- The 15 `50000000000000` gid literals (`FastMySQLDSGGameLookup` ×8,
  `MySQLGameStorerSearcher` ×2, `MySQLPenteGameStorer:295-299`, …) → one named constant. Note
  `map-java-db-wire.md` §A corrects the brief: the marker is **5e13, not 4e13**, and
  `BrainkingImporter.java:72 BASE_GID = 41000000000000L` sits below it and is classified as
  live.
- Fix the KOTH sentinel bug while here: `MySQLKOTHStorer.getEventId` returns `-1` on miss
  (`:31`) but all four `CacheKOTHStorer` call sites guard `if (hill_id == 0) return;`
  (`:111,147,202,226`), so an unregistered game writes hills under `koth_id = -1`
  (`map-java-db-wire.md` §C4). This **must** be fixed before any new game is registered.

**Why player-invisible.** Every predicate returns the same value for every legacy id; the KOTH
sentinel fix changes behaviour only for ids with no `game_event` row, of which there are
currently none (verified: `game_event` holds 1..32 + odd 51..81, exactly the registered set).

**Verification (this stage carries the plan's hardest gate).**
1. `PredicateDivergencePinningTest` from Stage 0 green **unchanged** — it asserts the five
   member lists are still individually what they were, divergence included.
2. New `ModernBandProbeTest`: register a synthetic descriptor at id `9001` (LIVE) / `9002`
   (SPEED) / `9003` (TURN_BASED) in a test-only registry and assert every migrated predicate
   answers correctly for it. This is what proves the modern band is safe to mint in Stage 7.
3. **Automated arithmetic guard** — add to CI, e.g.
   `grep -rnE '[^a-zA-Z_](>|>=|<|<=|==|!=)\s*50\b|-\s*50\b|%\s*2\b' dsg_src/java --include=*.java`
   filtered to the game-id call sites, asserted against an allow-list that must shrink to empty
   before Stage 7. Same for `httpdocs/gameServer/**/*.jsp` and `**/*.js`. Without this guard,
   Stage 7 is not safe to start. **[OPEN]** the exact regex will need tuning against false
   positives such as `LeaderBoard.java:66` (`wins+losses+draws >= 50`, unrelated to game ids —
   already flagged in `map-java-db-wire.md` §C5).

**Rollback.** Per-predicate revert; each predicate is an independent commit by construction.

**Size.** M — ~20 files, small diffs each. Deliberately *not* a bulk find-replace: Android's own
Task-8 migration found and preserved a real divergence (`isSwap2`) rather than unifying it
(`map-react-android.md` #14), which is the precedent this stage follows.

---

### Stage 4 — *(optional, severable)* Reconcile the divergent member lists

**Scope.** Collapse the five predicates from Stage 3 into one. This lands the **already-live
bug** identified in `MASTER-game-id-refactor-report.md` §2 and the confirmed divergence where
`HttpGameServlet.java:298-303` includes GO/SPEED_GO in its off-center list and
`MobileGameServlet.java:222-225` does not — meaning the same archived game currently renders
differently through the web and mobile paths.

**Why this is the one early player-visible stage.** Fixing a live bug *is* a behaviour change:
some archived games will render with a different `moves[0]` than they do today. It is therefore
deliberately severable — it can ship before Stage 5, after Stage 9, or never, without affecting
the cap removal. **It is not on the critical path.** It is listed here because Stage 3 makes it
a ~20-line diff and it will never be cheaper.

**Verification.** Replace `PredicateDivergencePinningTest` with a single-membership assertion in
the same commit, and diff-render a sample of archived games per affected variant before/after:
```sql
SELECT gid, game FROM pente_game WHERE game IN (19,20,21,22,23,24,7,8,17,18,27,28,29,30) LIMIT 50;
```
then load each through both the web and mobile archive paths and compare move lists.

**Rollback.** Revert; the per-site predicates from Stage 3 still exist in history.

**Size.** S — but high review weight per line.

---

### Stage 5 — Capability capture, admission control, visibility gates

**Scope.** The compat layer itself. No modern game exists yet, so every gate is a **no-op on
live data** — which is exactly why it ships before the games rather than with them.

1. `event/ClientInfo.java` — add `private String capabilities;` + getter/setter. Gson-additive
   in both directions; verified that no current client populates `ClientInfo` at all (§2.2), so
   every live connection resolves to `Capability.LEGACY`.
2. `server/ServerPlayer.java:92-97` — parse `loginEvent.getInfo()` into a `Capability`, store it
   on the `ServerPlayer`. Null/absent/unparseable ⇒ `LEGACY`.
3. `server/ServerTable.java:284, 910, 976` — `registry.isOfferedTo(cap, id)` before
   `GridStateFactory.getGame(...)`; on failure emit the existing
   `DSGChangeStateTableErrorEvent` (`ServerTable` already builds one on the error path) rather
   than throwing.
4. Filter `ServerData.gameEvents` per capability where it is attached at
   `ServerPlayer.java:192`. **Never emit an empty list** — `MainRoomPanel.java:140,485` and
   `Server.java:429` both index element 0 unguarded. Assert non-empty in code.
5. `mobile/LiveServersResponse.build(...)` — accept a capability and omit modern-only rooms.
   Thread a `?caps=` parameter through `httpdocs/gameServer/mobile/json/liveServers.jsp`
   (Android) and `httpdocs/gameServer/mobile/liveServers.jsp` (iOS); absent ⇒ `LEGACY`.
6. `mobile/IndexResponse.java` — `buildKoth`/`buildRatingStats` already iterate the
   legacy-scoped `LIVE_GAMES`/`TB_GAMES` arrays (§1.3), so they are gated by construction.
   Add an assertion test rather than code.

**Why player-invisible.** All three client families send no capability ⇒ `LEGACY` ⇒ the filters
pass the full legacy catalogue unchanged. Admission control rejects only ids that are already
broken today (33..50, >81, and everything outside the registry). No wire format change: one new
optional JSON string field that nobody sends and everybody's parser ignores.

**Verification.**
- `CapabilityGateTest`: `LEGACY` sees exactly today's per-room game set; a synthetic
  `gamecat1` capability additionally sees a synthetic modern id.
- `AdmissionControlTest`: feed `getGame`-hostile ids (0, 33, 49, 50, 82, -1, `Integer.MAX_VALUE`)
  through `ServerTable`'s change-state path and assert a `DSGChangeStateTableErrorEvent`, not an
  exception. This is a genuine hardening win independent of the cap.
- Golden wire check: capture a real login frame from the local stack before and after; assert
  the only diff is the absent/`null` `capabilities` field. Re-run
  `react_live_game_room` `npx vitest run src/protocol` against `wire-fixtures.json` unchanged.
- Manual: log the local Android build and iOS build into the local stack and confirm the room
  list and lobby are byte-identical.

**Rollback.** Revert; the `ClientInfo` field can stay harmlessly (no client sends it).

**Size.** M — ~8 files, mostly small.

---

### Stage 6 — DB expand (`TINYINT` → `SMALLINT UNSIGNED`)

**Scope.** Expand-only; there is no contract phase, because no column narrows and no value is
rewritten. Five columns (`map-java-db-wire.md` §A, re-verified live this session):

| Table | Column | Now | Target | Note |
|---|---|---|---|---|
| `game_event` | `game` | `tinyint(3) unsigned` | `smallint(5) unsigned` | |
| `pente_game` | `game` | `tinyint(3) unsigned` | `smallint(5) unsigned` | indexed (`MUL`) |
| `pente_move` | `game` | `tinyint(3) unsigned` | `smallint(5) unsigned` | |
| `dsg_player_game` | `game` | `tinyint(3) unsigned` | `smallint(5) unsigned` | **PRIMARY KEY part** `(pid, game, computer)` |
| `dsg_server_game` | `game` | `tinyint(3) unsigned` | `smallint(5) unsigned` | **PRIMARY KEY part** `(server_id, event_id, game)` |

`tb_game.game` / `tb_game_ai.game` are already `SMALLINT UNSIGNED`; `webdb_*.game` are already
`SMALLINT` signed — no change needed, though the signed/unsigned inconsistency should be
recorded.

**Why player-invisible.** Widening an unsigned integer column is value-preserving; every Java
field is already `int` (`map-java-core.md` "no width limit in Java core"). Nothing reads the
column width.

**Execution notes.** The two PK columns force a table rebuild on MyISAM. Schedule in a
maintenance window, take a dump first, and follow the existing schema workflow (`dump-schema.sh`
regenerates `schema.sql` from the live DB — do not hand-edit `schema.sql`). Do this stage
**before** Stage 7 and verify separately; do not bundle a migration with a feature.

**Verification.**
```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='dsg' AND COLUMN_NAME='game';
-- row-count and checksum parity, per table, before and after:
SELECT COUNT(*), MIN(game), MAX(game), SUM(game) FROM dsg_player_game;
SELECT COUNT(*), MIN(game), MAX(game), SUM(game) FROM dsg_server_game;
```
Then boot the stack and confirm `registerAllGames` is a no-op (inserts 0 rows) and the lobby,
ratings and archive pages are unchanged.

**Rollback.** Restore from the pre-migration dump, or `ALTER … MODIFY … TINYINT UNSIGNED` back —
safe as long as no modern id has been minted, which Stage 7 guarantees comes later. This
ordering is the reason Stage 6 precedes Stage 7 rather than shipping with it.

**Size.** S in code (zero lines), M in operational care.

---

### Stage 7 — Mint the modern band; ship family #17 in a gated, web-only room

**This is the first required player-visible stage.** Everything before it is invisible.

**Preconditions (all mechanically checkable, all must be green):**
- Stage 3's arithmetic-guard allow-list is empty for `dsg_src/java`.
- `ModernBandProbeTest` green.
- Stage 6 column widening verified in `information_schema`.
- Stage 5 gates green with a synthetic modern id.

**Scope.**
1. Register the new family in the registry at ids `1001` (LIVE) / `1002` (SPEED) / `1003`
   (TURN_BASED) — values chosen by the registry, not derived.
2. Migrate exactly **two** call sites from legacy-scoped arrays to `registry.allIds()`:
   - `game/MySQLGameVenueStorer.registerAllGames` (`:699-707`) so `game_event` rows are created
     for the new ids at boot — the existing array-driven, idempotent, self-healing mechanism
     (`map-java-db-wire.md` §C1) does the rest with no hand-written SQL;
   - `server/DSGContextListener` (`:125-138`) `addServerGames` for the **new room only**.
   Every other consumer of `LIVE_GAMES`/`TB_GAMES`/`ALL_GAMES`/`getDisplayGames()` stays
   legacy-scoped and therefore keeps ignoring the new game (§1.3).
3. Create the new room the same way server 46 (Go) was created: a `dsg_server` row plus an
   `addServerGames(dbHandler, <newServerId>, 2, newGameIds, LIVE_EVENT)` call. Mark its
   `ServerData` as requiring capability `gamecat1`.
4. Web surfaces: the JSP pickers and `gameConstants.jspf` pick the new game up automatically
   *only* where they iterate registry-scoped APIs. Decide per page. Recommended: expose the new
   game on the web deliberately, page by page, rather than by widening the shared arrays.

**Why this is safe for old clients.** Old Android/iOS builds never receive the new room from
`liveServers.jsp` (Stage 5 gate #2), so they cannot join it, cannot be broadcast its tables, and
cannot encounter id `1001`. Their `Table.gameNames` NPE and `Table.gameNames[game]!` trap are
never reached. The mobile TB home screen (`IndexResponse`) still iterates legacy-scoped arrays
so the new game does not appear there either.

**Why the web has no skew problem at all.** JSP pages, `gameConstants.jspf`, `js/boardCommon.js`
and both React bundles are **served by the same deploy as the server**. There is no installed
old build to be compatible with (modulo browser cache, mitigated by the existing bundle
hashing). This is why "web first" is the correct rollout order and costs nothing.

**Verification.**
- Before/after `game_event` diff — expect exactly the new rows, nothing else changed:
  ```sql
  SELECT game, name, eid FROM game_event WHERE site_id=2 AND game >= 1000 ORDER BY game, name;
  SELECT COUNT(*) FROM game_event WHERE site_id=2 AND game < 1000;  -- must be unchanged
  ```
- `SELECT server_id, GROUP_CONCAT(game ORDER BY game) FROM dsg_server_game GROUP BY server_id;`
  — servers 1, 37, 45, 46 must be **byte-identical to today**; only the new server_id gains rows.
- Log an un-updated Android build and an un-updated iOS build into the local stack; assert the
  room list from `liveServers.jsp` is unchanged and the new room is absent. This is the single
  most important manual check in the whole plan.
- Log a browser client in; play a full game of the new variant end to end; confirm it archives
  (`pente_game`), rates (`dsg_player_game`), and replays.

**Rollback.** Remove the room from `DSGContextListener` and restart. The `game_event` rows are
inert once unreferenced. If real games have already been played, rollback becomes data-bearing —
so keep the new room invitation-only or off-peak for its first days.

**Size.** M.

---

### Stage 8 — Client hardening + capability declaration

**Scope, per client.** Each is independent and independently shippable.

**JSP / hand-written JS (`httpdocs/gameServer/**`).** `js/boardCommon.js` is already the one
correct implementation in the stack — symbolic `GAME.*` dispatch with
`default: throw new Error("… unknown game id …")` and a dedicated unknown-id test asserting
`getBoardColor(99999)` throws (`map-jsp-web.md` Class A #4). Work here is: source
`gameConstants.jspf` from the registry, then convert the raw-literal sites in
`tb/gameScript.js:83,577,600` and the ~20 `game === 63` (TB_CONNECT6) sites in
`tb/mobileGame.jsp` to `GAME.*` symbols. Note the asymmetry flagged at `gameScript.js:600`
(includes TB_GO and TB_GO13 but not TB_GO9) — preserve it and pin it; do not "fix" it blind.

**React `react_live_game_room`.** The load-bearing change: `src/game/boardGeometry.js:27-41`
`variantKey()`'s unbounded `return 'swap2-keryo'` fallback must become an explicit unknown case.
It drives `VARIANT_RULES` (`src/Classes/GameClass.js:28-42,377,399,418`), i.e. the actual
replay/move engine, so today an unrecognized id renders a **fully playable board silently
running Keryo capture rules** (`map-react.md` §1E). Convert to hard failure + a visible
"unsupported game" state, backed by a test in the style of `boardCommon.test.js`. Then feed
`STANDARD_GAME_IDS` (`boardGeometry.js:51`) from `serverData.gameEvents` instead of a hardcoded
array — the metadata channel from §0 finding 2, at last consumed. Also fold in the three
independent partitions that disagree with `variantKey` (`#isDPente`, `#isSwap2`,
`critical_captures`' 6-id list at `GameClass.js:118`) and the parity normalisation at
`SettingsModal.js:119` (`table.game % 2 === 0 ? table.game - 1 : table.game`), which cannot
express a modern speed variant.

**React `react_mmai`.** Local WASM AI practice app with its own unconsolidated ladder in
`src/game/GameClass.js` (`game<21→19, <23→9, <25→13, else 19`) — `boardGeometry.js` was never
ported here (`map-react-android.md` #11). **YAGNI: do nothing unless the new family is offered
against the AI.** If it is, port `boardGeometry.js` rather than adding a fifth ladder.

**Android.** In priority order:
1. Null-safe `Table.gameNames` access — `shouldTimerRun()` (`liveGameRoom/Table.java:165-177`)
   is a confirmed NPE on any unmapped id; `getGameName()` (`:1011-1012`) returns `null`.
   Give both one defined unknown-id behaviour. **[OPEN]** `TableListAdapter.java` and
   `LiveTableFragment.java` are flagged-but-unverified for the same unchecked-`.get()` pattern
   (`map-android.md` "Not re-verified this pass") — check before shipping.
2. Wire the existing `rules/Variant`/`Variants` module into production. It already exists,
   already models ids 1-31 + the `81` TB-Renju special case, and is already equivalence-tested
   against `Table.java` — but is currently reachable only from
   `VariantPredicateEquivalenceTest`. Two sources of truth kept in sync by test coverage alone
   is the pre-existing risk; this is the moment to collapse them.
3. Replace `ArenaTableSetupDialog.java:66` `spinnerPosition*2+1` with an explicit
   `(id, displayName)` list. That arithmetic **structurally cannot express a modern id**.
4. Source the game lists from `serverData.gameEvents` instead of the six hand-maintained
   `strings.xml` `<string-array>`s (`:338,356,374,396,428,475`) — seven lists collapse to one
   server-driven list, which is the actual payoff of this whole exercise for the mobile team.
5. Send `capabilities: "gamecat1"` in `ClientInfo` on login and `?caps=gamecat1` on
   `liveServers.jsp`.

**iOS.** Same shape:
1. Replace the two force-unwrapped `Table.gameNames[game]!` sites
   (`HelperClasses.swift:135` `shouldTimerRun()`, `:624` `gameName()`) with a safe accessor —
   these are the highest-severity items in the whole client inventory.
2. Bound `gameColor()`'s unbounded final `else` (`HelperClasses.swift:627-657`), which currently
   maps any id ≥ 31 to Renju's board colour.
3. Generate `Table.gameNames`, `LobbyViewController.gameNames` (stale — missing Renju/Speed
   Renju) and `SocialViewController.gameNames` (which invents its own unshared `+50` TB scheme)
   from one source, ideally `serverData.gameEvents`.
4. Replace `row*2+1` / `game/2` / `count/2` in `TableSetupView.swift` and
   `ArenaTableSetupView.swift:71,257,270,283,284` with explicit lookup. Same structural
   impossibility as Android #3.
5. Preserve `BoardVariantMapping.variant(forGameType:)`'s **deliberate, documented** fallback to
   `.pente` — it is the one intentional unknown-input contract in the stack. Do not accidentally
   replicate it as a stray `default:` elsewhere.
6. Send the capability on login and on `liveServers.jsp`.

**Why this is player-visible only on updated builds.** Old builds keep the Stage 5 `LEGACY`
capability and keep seeing exactly today's catalogue.

**Verification.** Per platform: an explicit unknown-id test asserting the defined behaviour
(the `boardCommon.test.js` `game=99999` test is the model); Android's
`VariantPredicateEquivalenceTest` extended predicate-by-predicate; a manual matrix of
{old build, new build} × {legacy room, modern room} against the local stack.

**Rollback.** Per platform, per app release. Nothing here is coupled to the server.

**Size.** L overall, but four independent M/S pieces.

---

### Stage 9 — Gate retirement

**Scope.** Data/policy, not code (§2.5). When modern-capable adoption is judged sufficient, add
the modern ids to servers 1/37's `addServerGames` call so the new family appears in the main
rooms. When the legacy population is judged extinct, delete `Capability` and its four filter
call sites.

**Why player-visible.** The new game reaches the main lobby.

**Verification.** Adoption telemetry from the `capabilities` field
(`ServerPlayer.java:92-97` already logs `ClientInfo` via `DSGLoginEvent.toString()`, so
capability distribution is greppable from existing logs with no new instrumentation) — this is
the concrete reason to add the field to `ClientInfo` rather than to a new event type.

**Rollback.** Revert the offerings change; the gate is still in place.

**Size.** S.

---

## 5. Per-client summary

| Client | Version skew? | Unknown-id behaviour today | Stage that touches it | Old builds after rollout |
|---|---|---|---|---|
| JSP + `js/boardCommon.js` | **none** — served with the server | throws with a tested message (the one correct impl) | 8 (cleanup only) | n/a |
| `tb/gameScript.js`, `tb/mobileGame.jsp` | none | silent no-op fallthrough | 8 | n/a |
| React `react_live_game_room` | none (bundle) | **silent wrong rules engine** via `variantKey` fallback | 8 | n/a |
| React `react_mmai` | none (bundle) | own unconsolidated ladder | **not touched** (YAGNI) | n/a |
| Android | **yes** — installed app | **NPE crash** (`Table.shouldTimerRun`) | 8 | never see modern games; all legacy games work forever |
| iOS | **yes** — installed app | **trap crash** (force-unwrap) | 8 | same |
| Legacy AWT applet | yes | dense-slot arithmetic (`(game-1)/2`) | **not touched** | same; keep `gameEvents` non-empty |

## 6. DB changes, expand-then-contract

- **Expand (Stage 6):** five `TINYINT UNSIGNED` → `SMALLINT UNSIGNED`. Value-preserving,
  reversible while no modern id exists.
- **Expand (Stage 7):** new `game_event` rows created by the existing idempotent
  `registerAllGames`; new `dsg_server_game` rows for the new room only.
- **Contract: none, deliberately.** Nothing narrows, nothing is rewritten, no id is remapped.
- **Explicitly out of scope (YAGNI):** reconciling the two storage conventions —
  `pente_game.game` holds the **base** id with TB-ness encoded as `gid >= 5e13`, while
  `tb_game.game` holds the **TB** id (`map-java-db-wire.md` §C2/§C3). This is a real wart, but
  it is **not on the critical path**: a modern family stores base id `1001` in `pente_game` and
  TB id `1003` in `tb_game`, both well within `SMALLINT`, and both conventions keep working
  unchanged. Do not bundle a data migration with a cap removal.
- **Also out of scope:** the 3-way KOTH event-name inconsistency
  (`MySQLGameVenueStorer.KOTH_EVENT = "King of Hill"` vs `MySQLKOTHStorer` querying
  `"King Hill"` vs the live DB's `"King of the Hill"`) — pre-existing, orthogonal, but it
  **must be understood before Stage 7** because a modern game's KOTH `game_event` row is
  resolved by exact string match. Recommended: verify with
  `SELECT DISTINCT name FROM game_event WHERE site_id=2;` and pin the actual string in a test
  rather than "fixing" it.

## 7. Silent-rollout verdict

**Can new games appear silently, with old clients gracefully not seeing them? — Yes for web,
yes for old mobile builds' *non-participation*, but no for old mobile builds' *participation*;
and the reason is client code, not the protocol.**

Grounding, point by point:

- **The protocol imposes nothing.** Ids are JSON numbers over a `0xFF`-delimited UTF-8 TCP
  stream or a WebSocket text frame; no fixed-width field exists
  (`map-java-db-wire.md` §D.1 with a verified negative grep; `map-android.md` wire section).
  No version negotiation is required and none is proposed.
- **Existing games and old apps: safe indefinitely, by construction.** No legacy id ever changes
  value; `LIVE_GAMES`/`TB_GAMES` order is preserved; `ServerData.gameEvents` element 0 is
  preserved. Requirement (3) is met without any compatibility code at all — it is met by the
  decision not to renumber.
- **Web is not a compatibility surface.** JSP, `gameConstants.jspf`, `boardCommon.js` and both
  React bundles ship with the server. A new game can appear on the web the moment the server
  deploys, with zero rollout risk from stale clients.
- **Old mobile builds cannot be made to *show* a new game — and this is unavoidable.** Both
  crash rather than degrade: Android `Table.shouldTimerRun()` calls
  `gameNames.get(game).contains(...)` with no null check (`Table.java:165-177`); iOS
  force-unwraps `Table.gameNames[game]!` at two sites. There is no forward-compatible fallback
  anywhere in either app to lean on. No server-side trick fixes this: the only alternative
  would be presenting the modern game under a legacy id, which makes the client apply the wrong
  rules — rejected in §2.1.
- **But no forced update is needed.** Because visibility is gated at the room list — a
  server-side chokepoint both apps already depend on
  (`LiveServersResponse.build`, consumed at `LobbyActivity.java:199-252` and
  `LobbyViewController.swift:87-91`) — an un-updated app simply never learns the modern room
  exists. It keeps playing all 16 existing families, live and turn-based, forever. Users update
  on their own schedule; nothing breaks if they never do.
- **The Stage 8 mobile release is the real deliverable.** Its value is not the new game — it is
  that once a build ships the safe accessor plus `serverData.gameEvents`-driven lists, **every
  future game appears on that build with no further app update**. That is the capability being
  negotiated. Framed that way, Stage 8 is a one-time cost that removes the mobile constraint
  permanently, rather than a per-game tax.

One-line verdict: *new games ship silently and safely on web from Stage 7; old mobile builds
never see them and never break; making new games visible on mobile requires one hardening
release per platform, after which no further release is ever required.*

## 8. Residual risks

1. **We can only inspect current app source, not previously shipped binaries.** The claim "old
   Android/iOS builds read only `serverData.loginMessages`" is verified against the *newest*
   source. An older shipped build may parse `gameEvents` differently. Mitigated by gating at the
   **room** level (the coarsest boundary): a room an app never learns about cannot leak anything
   through any field. This is the main reason room-level gating is preferred over per-field
   filtering.
2. **Capability is client-asserted and unverifiable.** A client can claim `gamecat1` and be shown
   games it renders wrong. UX bug, not a security issue. Not worth defending against.
3. **The Stage 3 arithmetic guard is a regex over source.** It will have false positives (e.g.
   `LeaderBoard.java:66`'s unrelated `>= 50`) and can miss dynamically-constructed comparisons.
   `ModernBandProbeTest` is the real safety net; the grep is the tripwire.
4. **`ServerTable` broadcasts every table in a room to every client in that room.** If a modern
   game is ever offered in a *shared* room before Stage 9, old apps in that room will be
   broadcast a table with an unknown id and crash. Per-connection table filtering was not
   designed here because room-level isolation makes it unnecessary — but it means **Stage 9's
   policy flip is genuinely irreversible in effect for anyone still on an old build**, and must
   be gated on adoption telemetry, not on a calendar.
5. **Two `liveServers.jsp` variants** (`mobile/json/liveServers.jsp` for Android,
   `mobile/liveServers.jsp?iPhone` for iOS) must both be gated. Missing one silently exposes the
   modern room to that platform.
6. **KOTH `-1` sentinel and the 3-way event-name inconsistency** must be resolved before Stage 7
   or the modern family's hills/tournaments will be written under `koth_id = -1`.
7. **`getGameId(String)` resolves shared live/TB display names to the LIVE id** (it scans
   `allGames` before `tbGames`), and the archive DTO round-trips the *name*, not the id
   (`ServerTable.java:3332` `gameData.setGame(game.getName())`). A modern family must therefore
   have **globally unique display names per variant**, or its archived TB games will resolve to
   the live id. Cheap to satisfy; expensive to discover later.
8. **Pre-existing data anomalies survive the refactor:** `pente_game` rows `(gid=77, game=77)`
   and `(gid=79, game=79)` store TB ids in a base-id column, and `tb_game` has 4 rows with
   `game=1`. Stage 1 turns `getGame(77)` from a silent wrong answer into an exception — verify
   which load paths touch those 6 rows before Stage 1 ships, or they become a visible error
   where they are currently a silent one.
9. **MyISAM PK rebuilds** on `dsg_player_game` and `dsg_server_game` need a maintenance window
   and a verified dump.
10. **`react_mmai` is deliberately untouched.** If the new family is ever offered against the
    AI, it becomes a real gap — recorded, not pre-solved.

## 9. Open verification items (do not treat as established)

- **[OPEN]** Whether `ServerPlayer`'s capability is reachable from every list producer that needs
  it, in particular `ServerData` attachment and any `ServerTable` broadcast path.
- **[OPEN]** Whether `TableListAdapter.java` / `LiveTableFragment.java` (Android) share the
  unchecked `gameNames.get()` pattern — flagged unverified in `map-android.md`.
- **[OPEN]** Exact behaviour of `Resources.getServerData()` ordering and whether filtering the
  room list can disturb any client's positional assumptions.
- **[OPEN]** Whether any *shipped* (not current-source) Android/iOS build consumes
  `serverData.gameEvents`.
- **[OPEN]** The live `game_event.name` strings — run
  `SELECT DISTINCT name, COUNT(*) FROM game_event WHERE site_id=2 GROUP BY name;` before Stage 7.
- **[OPEN]** Whether `react_mmai` has any server connection at all, or is purely local WASM.
- **[OPEN]** Precise `ServerTable` broadcast fan-out for table lists (assumed room-wide,
  unfiltered) — the assumption behind residual risk 4.
