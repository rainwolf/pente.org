# Adjudication — staged game-id cap-removal strategies

Scope: score `design-registry.md`, `design-protocol.md`, `design-reband.md` against the
seven required criteria, verify each against the MAP FACTS, and recommend one strategy
(or a concrete hybrid with an exact stage list).

Method: all six map reports + the iOS map read in full. Every load-bearing structural
claim that differentiates the three designs was re-verified read-only against the actual
source tree this session (marked **[checked]** below). No repo file modified, no DB write.

---

## 0. Verdict in one paragraph

**`design-registry.md` (Grandfathered Registry) wins and should be the spine.** It is the
only design that removes *both* ceilings — numeric and structural — while keeping the
codebase's one genuinely good existing property (array-driven auto-extension of every
picker, `map-java-core.md` class F). `design-protocol.md` is the best-researched document
of the three and contributes four mechanisms the winner is missing; it loses as a spine
because its central structural choice (freeze every enumeration API to the legacy window
forever) converts a self-extending system into a per-page, per-game manual edit, which is
the opposite of the stated goal. `design-reband.md` is a well-argued devil's advocate that
**refutes itself** in §9.4/§11 and then recommends the registry approach; it is not
adoptable as a spine, but it caught the single most important thing the other two missed.

---

## 1. Facts verified this session (these drive the scoring)

| # | Claim | Result |
|---|---|---|
| V1 | `ant test` runs only 10 explicitly-named classes and **none** from `org.pente.game.test.*` | **[checked]** `build.xml` lines 41-83: `RedisConnectionManagerTest`, `CacheDSGFollowerStorerTest`, `CacheDSGPlayerStorerTest`, `CacheTourneyStorerTest`, `CacheTourneyStorerRedisTest`, `TourneySerializationTest`, `CacheTBStorerRedisTest`, `SerialEventPumpTest`, `MMAIProtocolTest`, `MMAIPlayerInitTest`. `RenjuFactoryTest` / `GridStateFactorySingleGameSetTest` / `MySQLGameVenueStorerTbLookupTest` **do not run**. |
| V2 | `ServerData` carries a per-room game list that already crosses the wire | **[checked]** `core/ServerData.java:18` `private Vector<GameEventData> gameEvents`; `:75-80` add/get; attached at `server/ServerPlayer.java:192` `loginEvent.setServerData(server.getServerData())`. |
| V3 | `gameEvents[0]` is indexed unguarded — must never be empty | **[checked]** `client/awt/MainRoomPanel.java:140` and `:485` (`elementAt(0)`), and **server-side** at `server/Server.java:429` (`.get(0)`). |
| V4 | `ClientInfo` has no version/capability field | **[checked]** `event/ClientInfo.java:7-11` — exactly `browser`, `javaVersion`, `javaClassVersion`, `os`, `osVersion`. |
| V5 | `getGame` body is as all three designs quote it | **[checked]** `game/GridStateFactory.java:392-398` — `if (game > TB_START) return tbGames[(game-TB_START-1)/2]; else return allGames[game];` and `isValidGame` = `game >= PENTE && game <= SPEED_RENJU`. |
| V6 | **Full player ratings vector is broadcast room-wide** | **[checked]** `core/SimpleDSGPlayerData.java:76` `private Vector<DSGPlayerGameData> gameData`, serialized wholesale by `event/DSGPlayerDataAdapter.java:20-21` (plain `context.serialize`). Carried by `DSGLoginEvent`, **`DSGJoinMainRoomEvent`** and **`DSGUpdatePlayerDataEvent`**; `server/ServerMainRoom.java:156` sends `new DSGJoinMainRoomEvent(d.getName(), d)` for other players. |
| V7 | `MySQLGameStorerSearcher.java:400` does **not** use `GridStateFactory.TB_RENJU` | **[checked]** zero `TB_RENJU` hits in `org/pente/gameDatabase/`; lines 387-402 contain only the two `gid < / >= 50000000000000` filters. `map-java-db-wire.md` §C2 is wrong here, and `design-reband.md` propagated the error. |
| V8 | `getGame(77)` resolves via the TB branch, not `allGames[47]` | **[checked]** from V5: `77 > 50` → `tbGames[(77-51)/2] = tbGames[13]` = `TB_SWAP2PENTE_GAME`, whose real id **is 77**. Consequence below (§3, PROTO-E1). |

---

## 2. Scores

Scale 0-10 per criterion. Overall is the mean, with **per-stage risk**, **stageability**
and **silent-rollout honesty** weighted ×1.5 (they are what the brief actually asks for).

| Criterion | registry | protocol | reband |
|---|---|---|---|
| Reaches truly unbounded ids | 8.0 | 7.5 | 4.5 |
| Per-stage risk | 8.5 | 7.5 | 7.5 |
| Total diff size | 7.5 | 8.5 | 8.0 |
| Stageability / rollback | 9.0 | 8.0 | 8.5 |
| Player-invisibility of early stages | 9.0 | 9.0 | 8.5 |
| Silent-new-game feasibility, honestly grounded | 8.5 | 9.0 | 9.0 |
| Ops burden | 8.5 | 6.5 | 6.0 |
| **Weighted overall** | **8.5** | **8.0** | **7.4** |

### 2.1 `design-registry.md` — Grandfathered Registry

**Reaches unbounded (8.0).** Only design that makes family *shape* free: a family declares
`{live, speed, tb}` explicitly and may omit any of them (§3). That matters immediately —
Renju already has no clean speed/TB fit (`map-android.md` #2 special-cases `id==81`;
iOS `ArenaTableSetupView.swift:257` `count/2` truncates on an odd dict). Docked 2 points
for a mild overclaim: §12 says "Stage 9 removes the last ceiling", but Stage 9's target is
`SMALLINT UNSIGNED` = 65 535, not ∞; the `INT` path is never costed.

**Per-stage risk (8.5).** Stage 1's five intentional deltas are enumerated exactly and
proven unreachable by a census the author **actually ran** (G6, six tables, zero rows in
33..50 or >81), with `pente_move` honestly deferred as OV-1. Stage 3 is explicitly labelled
the first visible stage *and* explicitly severable. Weakness: `legacySafe` is
whitelist-by-omission over "the handful of enumeration APIs that feed old mobile clients"
— the author flags this himself (residual risk 3) but the enumeration of those APIs
(§Stage 8) is prose, not a closed list.

**Diff size (7.5).** Largest client stage of the three (Stage 6 touches JSP + `boardCommon`
+ both React apps + changes `getAllGames()`'s array shape for 5 callers). That cost is
*bought*, not wasted — it is what keeps pickers auto-extending — but it is real.

**Stageability (9.0).** Best of the three: every stage is a single-file or single-layer
revert; Stage 9's expand-before-allocate ordering keeps the DDL reversible; nothing is ever
renumbered so there is no dual-write to unwind.

**Invisibility (9.0).** Stages 0/1/2 invisible by construction and backed by a run census.

**Silent rollout (8.5).** Honest and correct: yes for TB/web-only (gated on OV-4), no for
live-room without one small mobile release, because **[checked V4]** there is no client
version on the wire. Docked for missing the ratings-broadcast leak (§3, GAP-1).

**Ops burden (8.5).** Best: boot-time materialization + `/api/games` as *derived views,
never authorities* (an explicitly-stated design decision, and the right one); `pente_move`
ALTER deferred behind ~52 families of headroom; pickers keep auto-extending.

### 2.2 `design-protocol.md` — Capability-Gated Edge Visibility

**Research quality is the best of the three** and three of its findings are load-bearing
and now independently confirmed: V1 (`ant test` wiring), V2/V3 (`ServerData.gameEvents`),
V4 (`ClientInfo`). Its §0 finding 2 — "the metadata-driven game list already exists,
already crosses the wire, and is already safely ignored" — is a genuinely valuable
discovery that neither other design made.

**Reaches unbounded (7.5).** Modern band ≥1000, variant as a field (full shape freedom),
but explicitly stops at `SMALLINT` = 65 535 and rejects `INT` as YAGNI. Honest; literally
capped. Fine engineering, slightly below the brief as written.

**Per-stage risk (7.5).** Two concerns.
(a) Stage 1 makes `getGame` **throw** `IllegalArgumentException` on miss, while
`ServerTable.java:284/910/976` still feed it unvalidated client ids; admission control is
Stage 5, four stages later. The text acknowledges this ("pair with Stage 5 … in the same
release train") but the stage table does not encode it. Registry's choice (return `null`
in Stage 1, validate in Stage 4) sequences better.
(b) Stage 3's headline gate is a **regex over source** whose allow-list must reach empty
before Stage 7. The author concedes false positives and that dynamically-constructed
comparisons escape it. `ModernBandProbeTest` is the real net; the grep is theatre that
could produce false confidence.

**Diff size (8.5).** Genuinely the smallest path to a first new game: legacy arrays frozen,
exactly two call sites migrated (`registerAllGames`, `addServerGames`).

**Stageability (8.0).** Good, but Stage 9 is explicitly "irreversible in effect for anyone
still on an old build" and is gated on adoption judgement.

**Invisibility (9.0).** Stages 0-3 and 5-6 are strictly server-internal; Stage 4 severable.

**Silent rollout (9.0).** Best-grounded of the three. It found the *complete* boundary —
the room list, `LiveServersResponse` → `mobile/json/liveServers.jsp` (Android
`LobbyActivity.java:199-252`) and `mobile/liveServers.jsp?iPhone` (iOS
`LobbyViewController.swift:87-91`) — and correctly argues room-level gating beats
per-field filtering precisely because shipped binaries cannot be inspected (its residual
risk 1). It also answers reband's [OPEN] #2 outright.

**Ops burden (6.5) — this is why it loses.** §1.3 freezes `LIVE_GAMES`, `TB_GAMES`,
`ALL_GAMES`, `getDisplayGames/getNormalGames/getSpeedGames/getTbGames/getAllGames` to the
legacy window *forever*. That deliberately destroys the property `map-java-core.md` class F
identifies as the good pattern ("Adding a game to the arrays automatically propagates to
every page below"). Stage 7 item 4 then says: "Decide per page. Recommended: expose the new
game on the web deliberately, page by page." Concretely, every future game costs manual
edits at `leaderboard.jsp:28`, `playerstatsbox.jsp:20`, `statsMain.jsp:29`, `tb/new.jsp:255`,
`new2.jsp:120`, `newKotH.jsp:146`, `newAIgame.jsp:138`, `mobile/index.jsp:403,412,421`,
`followersing.jsp:67`, `admin/newTourney.jsp:117`, `broadcast.jsp:73`, `kothBox.jsp:57-71`
— permanently. Add: a separate gated room per new game until Stage 9, a client-asserted
capability token to maintain and eventually retire, and two `liveServers.jsp` variants that
must both be gated or the whole scheme leaks (its own residual risk 5).

### 2.3 `design-reband.md` — Gen-2 quad banding

**Reaches unbounded (4.5) — fails on its own terms, and says so.** Ramp A = 40 new
families, a hard ceiling. Ramp B = 16 360. Ramp C (`INT`) passes numerically. But §9.4 is
decisive: `(g & 3)` hard-codes family shape as `{live, speed, tb, spare}` and cannot
express a family without a speed twin — *which Renju already is* — so every irregular
family burns dead ids that `(g & 3)` classifies as valid-but-nonexistent, **recreating the
exact phantom-band pathology Stage 2 exists to remove**. §11 then abandons the arithmetic
core and recommends nullable descriptor fields, i.e. converges on `design-registry`.

**Per-stage risk (7.5).** Stages 0-3 are sound and near-identical to the other two.
Stage 4 introduces a *second derivation regime* that every client-side range check must
learn: it notes itself that gen-2 speed id 97 is odd, so `utils.js:32` (`g % 2 === 0`) and
`SettingsModal.js:119` (`game % 2 === 0 ? game - 1`) both misclassify it — and iOS
`row*2+1` / `game/2` and Android `spinnerPosition*2+1` structurally cannot produce an even
base like 96. Registry and protocol never let a client derive anything, so this is added
risk unique to reband.

**Diff size (8.0) / Stageability (8.5).** Best rollback analysis of the three: an explicit
per-stage table plus a named point of no return ("Stage 9's alter followed by the first
game played on a family with base > 252"). §7's census and band-safety SQL are the most
directly runnable verification artefacts produced by any of the three.

**Silent rollout (9.0).** Tied best, and for the single most valuable reason: it is the
**only** design that identifies the ratings leak (§10, "The leak the visibility flag does
NOT close"). **[checked V6]** — confirmed real. It also correctly identifies cached React
bundles as an "old client" population on the web.

**Ops burden (6.0).** Two derivation regimes forever; poison (33..50) and reserved (82..95)
bands are conventions, not enforced constraints; §9.5 concedes per-game cost stays
O(~15 hand-edited lists) because it changes zero client touchpoints by design.

---

## 3. Contradictions of mapped facts, and missed touchpoint classes

Each map report's risk flags were checked against all three designs. Findings below are
penalties already reflected in §2; the ones marked **GAP** are unaddressed by the *winning*
design and are therefore mandatory grafts in §4.

### PROTO-E1 — factual error in `design-protocol.md` residual risk 8
It states Stage 1 "turns `getGame(77)` from a silent wrong answer into an exception" for the
anomalous `pente_game` rows `(gid=77, game=77)` / `(gid=79, game=79)`. **[checked V8]** this
is wrong: 77 *is* `TB_SWAP2PENTE`'s real id and 79 *is* `TB_SWAP2KERYO`'s, so a map keyed by
real id resolves both **identically before and after** map-ification. The error is inherited
from `map-java-db-wire.md` §A, which reasons via `allGames[47]`; the real code takes the
`> TB_START` branch. `design-reband.md` §2 caught and corrected exactly this. Consequence:
protocol would schedule an unnecessary pre-Stage-1 investigation; the *actual* residual issue
(those rows store a TB id in a base-id column and so are mis-shaped data) survives all three
designs untouched.

### REBAND-E1 — unverifiable citation in `design-reband.md`
Stage 4 and residual risk 5 both assert `gameDatabase/MySQLGameStorerSearcher.java:400` uses
`GridStateFactory.TB_RENJU` as a game-id range bound, so "every gen-2 TB game is silently
excluded from the game database". **[checked V7]** there is no `TB_RENJU` reference anywhere
in `org/pente/gameDatabase/`. The claim originates in `map-java-db-wire.md` §C2 and was
propagated without verification. Registry and protocol simply omit it (neutral). **Do not
carry this item forward.**

### GAP-1 (severity: high) — ratings payload leaks new ids past every visibility gate
`map-java-db-wire.md` §C5 flags `MySQLDSGPlayerStorer.loadAllGames` as "the one genuinely
id-agnostic read path in the whole layer". Only `design-reband.md` §10 draws the conclusion.
**[checked V6]**: `SimpleDSGPlayerData.gameData` is a `Vector<DSGPlayerGameData>` serialized
wholesale by `DSGPlayerDataAdapter`, and it rides on `DSGLoginEvent`, `DSGJoinMainRoomEvent`
and `DSGUpdatePlayerDataEvent`; `ServerMainRoom.java:156` sends other players' `DSGPlayerData`
to a joining client. So the moment *any* player plays a new-band game, that id reaches **every
client in every room that player enters**, regardless of `legacySafe` (registry) or room
gating (protocol). Both losing gates are on *game enumeration*; this path is *player* data.

Likely-benign but unverified: Android `RatingStat.gameId` is a plain `int`
(`map-android.md` #18) and `ArenaJoinRequestAdapter.java:36-127` looks up by the id it wants
rather than iterating received rows; iOS handling of an unknown-id rating row is not mapped at
all. **This must be a hard pre-condition on the first stage that mints a new id, in whichever
design is adopted.**

### GAP-2 (severity: medium) — name↔id round-trip in the archive DTO
`map-java-db-wire.md` §C2 trap (d): `GridStateFactory.getGameId(String)` scans `allGames`
before `tbGames`, so a shared display name resolves to the **live** id; and
`ServerTable.java:3332` persists `gameData.setGame(game.getName())` — the archive DTO
round-trips the *name*, not the id. Only `design-protocol.md` draws the consequence
(residual risk 7): a new family must have **globally unique display names per variant**, or
its archived TB games resolve to the live id. `design-registry.md` and `design-reband.md`
both miss it. Cheap to satisfy up front, expensive to discover after games are archived.

### GAP-3 (severity: medium) — the pinning harness does not run in CI
**[checked V1]**. `design-registry.md` verifies Stages 0/1/2 with "`ant test` green" and
"existing pins `RenjuFactoryTest:68-70`, `GridStateFactorySingleGameSetTest`,
`MySQLGameVenueStorerTbLookupTest` must stay green" — none of which `ant test` executes.
Its entire zero-diff verification story would run zero relevant tests. `design-reband.md`
says "extend, do not replace, the four tests that already exist" with the same blind spot.
Only `design-protocol.md` caught it and requires a `build.xml` edit in Stage 0. Note also
`RenjuFactoryTest.java:70` asserts `getMaxGameId() == TB_RENJU`, which every design's
`getMaxGameId` rework breaks — and would break **silently**.

### Minor items, correctly handled by at least one design
- **`gameScript.js:600` asymmetry** (includes `TB_GO` 69 and `TB_GO13` 73 but not `TB_GO9`
  71 — `map-jsp-web.md` #7): protocol alone says "preserve it and pin it; do not fix it
  blind". Registry's Stage 6 folds it into a bulk symbolization without flagging it. Adopt
  protocol's instruction.
- **Android `rules/Variant`/`Variants` (Task 8, `map-android.md` risk flag 4)**: registry and
  reband both say *do not* complete that migration, honouring the deliberate `isSwap2`
  divergence the Android author documented. **Protocol Stage 8 item 2 says wire it into
  production** — that directly contradicts the mapped, deliberate hold-back. Reject.
- **iOS `gameColor()` unbounded else** (`ios-game-id-touchpoints.md` risk 5, "will NOT be
  caught by testing that only checks for crashes"): all three cover it; reband states the
  silent-vs-crash distinction most clearly.
- **`MMAIPlayer.SUPPORTED_GAMES`** (`map-java-core.md` class H): all three correctly say
  leave the allow-list alone. Good.
- **AWT/web applets** (`GameBoardFrame.java:939` `(game-1)/2`, `GameBoard.java:226`):
  registry OV-5 flags them as an open question; reband adds a concrete `if (id >= GEN2_FLOOR)
  bail`; protocol notes only "keep `gameEvents` non-empty". Reband's bail is the cheapest
  correct answer — but **[checked V3]** the applet path also drives `Server.java:429`
  server-side, so the non-empty invariant is the harder constraint and must be asserted.
- **`map-android.md` risk flag 5** (Parcelable `Game.writeString` vs `RatingStat.writeInt`):
  no design addresses it. Correct — it is a 32-bit-safe audit trap, not a width limit.
  Record it in the playbook, do nothing.

---

## 4. Recommendation — `design-registry.md` as spine, with six named grafts

Adopt `design-registry.md`'s stage list verbatim. Apply these six changes, each traceable to
a verified fact or a mapped risk flag the winner does not cover.

**G1 — wire the pinning harness into `build.xml` in Stage 0 (from protocol; closes GAP-3).**
Add `GridStateFactoryCharacterizationTest` *and* the three existing but unrun pins
(`RenjuFactoryTest`, `GridStateFactorySingleGameSetTest`, `MySQLGameVenueStorerTbLookupTest`)
to the `test` target. Until this lands, "zero diff" is unverifiable. Also carry protocol's
`ModernBandProbeTest` (register a synthetic descriptor at a new-band id in a test-only
registry and assert every migrated predicate answers correctly) — it, not any grep, is what
licenses Stage 8.

**G2 — close the ratings-broadcast leak before Stage 8 (from reband; closes GAP-1).**
Insert a new pre-condition on Stage 8a, ranked above OV-4: verify how an unknown-id
`DSGPlayerGameData` row is handled by an un-updated Android build and an un-updated iOS
build, driven from the local stack via `DSGJoinMainRoomEvent`/`DSGUpdatePlayerDataEvent`
(`ServerMainRoom.java:156`). If either is unsafe, filter the vector per capability at the
same chokepoint the `legacySafe` gate uses, or defer Stage 8 until Stage 7's mobile
hardening covers it. This is the one gate no visibility flag closes.

**G3 — add the room-list gate as the coarse belt (from protocol).**
Registry's `legacySafe` is whitelist-by-omission over an unclosed list of enumeration APIs.
Add protocol's chokepoint 2 — capability/absent-parameter gating in
`LiveServersResponse.build(...)`, threaded through **both** `mobile/json/liveServers.jsp`
(Android `LobbyActivity.java:199-201,252`) and `mobile/liveServers.jsp?iPhone` (iOS
`LobbyViewController.swift:87-91`), absent parameter ⇒ legacy ⇒ modern room hidden. Room
membership is the only boundary that survives protocol's residual risk 1 (shipped binaries
cannot be inspected). Use it for the *first* new live family only; retire it once Stage 7
penetration is measured. Do **not** adopt protocol's §1.3 legacy-freezing of the enumeration
APIs — that is the change that destroys auto-extension.

**G4 — use `ServerData.gameEvents` as the metadata channel (from protocol).**
**[checked V2/V3]** it already exists, is already populated from `dsg_server_game`, is
already sent on login, and is already ignored by all three modern clients (React lgr reads
only `serverData.tournament`/`arena`; Android only `loginMessages`; iOS only
`loginMessages`). Prefer it over inventing a client dependency on registry's new
`/api/games` for the live-room clients — one fewer moving part, and it makes Stage 6's
`STANDARD_GAME_IDS` server-driven for free. Keep `/api/games` for JSP/admin/reporting and
for the TB/web surfaces that have no `ServerData`. **Hard invariant, assert in code:
`gameEvents` is never empty** — `MainRoomPanel.java:140,485` and `Server.java:429` index
element 0 unguarded.

**G5 — require globally unique display names per variant (from protocol; closes GAP-2).**
Add to registry's Stage 2 registry-invariant unit test, alongside "ids are unique and
disjoint from the poisoned bands": display names are unique across all variants. Rationale:
`getGameId(String)` resolves shared names to the **live** id and `ServerTable.java:3332`
persists the name into the archive DTO.

**G6 — keep an eyeball-decodable allocation convention, derived from fields not arithmetic
(from reband §11).** Registry already picks a 100+ band; make the *convention* explicit and
documented (e.g. family base = 100 + 10k, `+1` speed, `+2` TB, rest reserved) so ids stay
legible in logs and in the ~30 raw magic numbers still living in `tb/gameScript.js` and
`tb/mobileGame.jsp`. Critically: `isSpeed`/`isTurnBased`/`baseFamilyId` must read **nullable
descriptor fields**, never derive from the id — that is exactly the substitution reband's own
§11 concludes with, and it is what preserves the ability to declare a family with no speed
twin (Renju's actual shape) or no TB variant.

Also carry forward, verbatim: reband's §7 census + band-safety SQL as Stage 0's DB snapshot;
reband's §8 rollback table with a named point of no return; and protocol's instruction to
**preserve and pin** the `gameScript.js:600` GO/GO9/GO13 asymmetry rather than "fixing" it.
Explicitly reject protocol's Stage 8 item 2 (wiring Android's `rules/Variants` into
production) — it contradicts the deliberate `isSwap2` hold-back in
`VariantPredicateEquivalenceTest`.

### 4.1 Final stage list (hybrid)

| # | Stage | Source | Visible? | Notes |
|---|---|---|---|---|
| 0 | Pinning harness **+ `build.xml` wiring** + DB census | registry + **G1** + reband §7 | no | pins today's bugs on purpose; without the `build.xml` edit nothing runs |
| 0p | Mobile hardening release — **dispatch at t=0, in parallel** | registry Stage 7 / reband Stage 1 | no | Android `Table.shouldTimerRun` NPE + `getGameName`; iOS 2× force-unwrap + `gameColor` else + `count/2`; audit `TableListAdapter`/`LiveTableFragment` (OV-2). Include the 5-line `ClientInfo` app-version string. Adoption clock is the programme's critical path |
| 1 | `GridStateFactory` positional arrays → id-keyed maps | registry | no | one file; five enumerated deltas, all unreachable per census; **preserve iteration order** (`IndexResponse:419-420` "odd first", `CacheKOTHStorer:150`) |
| 2 | `Game` descriptor + predicate delegation + `TB_START` routed | registry (+ **G5**, **G6**) | no | zero-diff stage; registry-invariant test adds unique-display-name and nullable-variant assertions |
| 3 | Reconcile the 5 divergent opening predicates | registry | **YES** (bug fix) | severable, deferrable to any later point; audit `MySQLPenteGameStorer:785,1071` and `MySQLWebDbStorer:420` in the same change |
| 4 | Boundary validation + KOTH sentinel/name fixes | registry | no | `isKnownGame` at `ServerTable:284,910,976`; `-1` vs `== 0` sentinel; KOTH name reconciliation **additive-read only**, after a production `SELECT` |
| 5 | Boot materialization + `/api/games` + **populate `ServerData.gameEvents` correctly** | registry + **G4** | no | derived views, never authorities; assert `gameEvents` non-empty |
| 6 | Web/JS client consolidation (JSP, `boardCommon`, React ×2) | registry | no | convert `variantKey`'s `'swap2-keryo'` fallback to a hard failure; **preserve+pin** the `gameScript.js:600` asymmetry; `react_mmai` minimal only |
| 7 | Room-list capability gate | **G3** (protocol) | no | both `liveServers.jsp` variants; absent parameter ⇒ legacy |
| 8a | **Ratings-leak verification gate** | **G2** (reband) | no | blocks 8b; unknown-id `DSGPlayerGameData` on old Android + old iOS |
| 8b | Mint first new family — TB/web-only first, then gated live room | registry (+ OV-4) | yes | `legacySafe=false`, `active` flip as rollback |
| 8c | New family in main rooms, after measured penetration | registry 8b / protocol 9 | yes | gate on telemetry from 0p's version string, not a calendar |
| 9 | DB widening `TINYINT`→`SMALLINT UNSIGNED` | registry | no | only past id 255; four cheap tables in one release, `pente_move` (35.8M rows, ~3.5 GB MyISAM rebuild) scheduled separately; replica first |

### 4.2 What the hybrid deliberately does not take

- Protocol's §1.3 legacy-freezing of `LIVE_GAMES`/`TB_GAMES`/`ALL_GAMES`/`getDisplayGames()`
  — it is safe-by-construction but converts ~13 auto-extending JSP surfaces into permanent
  per-game manual edits.
- Protocol's Stage 3 source-regex arithmetic guard as a *gate*. Keep it as a tripwire only;
  `ModernBandProbeTest` (G1) is the gate.
- Protocol's Stage 8 item 2 (wire Android `rules/Variants` into production) — contradicts the
  documented `isSwap2` divergence hold-back.
- Reband's `(g & 3)` derivation and its `MySQLGameStorerSearcher` item (REBAND-E1).
- Any renumbering, any wire-format change, any `pente_game`/`tb_game` convention
  reconciliation, any `INT` widening before it is needed. All three designs agree; so do I.

---

## 5. Open questions only the owner can answer

1. **Is `SMALLINT UNSIGNED` (65 535 ids) an acceptable end state, or must the plan commit to
   `INT UNSIGNED`?** This is the only place the three designs materially disagree about the
   goal itself, and the answer changes the cost of Stage 9 (`pente_move` is 35.8M rows /
   ~3.5 GB MyISAM rebuild either way, but `INT` doubles the column and index growth).
2. **Are the AWT / web applet clients still reachable by real users?** Registry OV-5. It
   decides whether `GameBoardFrame.java:939` `(game-1)/2` and `GameBoard.java:226` get a
   cheap bail-out or must be hardened — and **[checked V3]** `Server.java:429` indexes
   `gameEvents[0]` *server-side*, so the non-empty invariant is mandatory regardless.
3. **What penetration threshold (or wall-clock wait) authorises making a new game visible in
   the main live rooms?** Nobody can crash-proof retroactively; rolling the server back stops
   new crashes but does not un-crash anyone.
4. **Is adding an app-version string to `ClientInfo` on both mobile clients authorised now?**
   **[checked V4]** the field structure exists and nothing populates it. Five lines per
   platform converts question 3 from judgement into measurement — but it only helps if it
   ships in the *first* mobile release, not the second.
5. **Is one deliberately player-visible bug-fix release acceptable, and when?** Stage 3
   changes what the mobile game-database endpoint renders for archived Go games. It is
   severable to any point, so this is purely a product-timing call.
6. **What shape will the next few games actually be?** Specifically: will any family ship
   with no speed variant, no TB variant, or live-only? Renju is already awkward. The answer
   decides whether variant-optionality is a day-one requirement (it should be assumed yes) —
   and it is the single fact that disqualifies reband's `(g & 3)` scheme.
7. **Which KOTH `game_event.name` string is canonical in production?** Three spellings are in
   play ("King of Hill" constant / "King Hill" query / "King of the Hill" in the DB) and
   `eid` resolution is exact-match. Requires a production `SELECT`, not a local one.
8. **Is a separate, capability-gated room per new live game acceptable UX**, or must a new
   game appear in the main lobby from day one? If the latter, G3 is unavailable and the
   mobile hardening release becomes a hard blocker rather than a soft one.
9. **Should adding a game remain a code change?** All three designs assume yes (registry
   explicitly: "the registry stays hand-maintained in code", residual risk 8). If the owner
   wants data- or admin-driven game definitions later, the `game_registry` table should be
   designed for that now rather than strictly as a materialized view.
10. **Can a maintenance window be scheduled for `pente_move`, and is the replica
    (`docker-compose-replica.yml`) in scope for the same migration?** If replication is not
    migrated in lockstep, a >255 id breaks it later.

---

## 6. Scoring appendix — one-line rationale per cell

| | registry | protocol | reband |
|---|---|---|---|
| Unbounded | shape-free descriptors; SMALLINT end state, INT uncosted | shape-free; explicitly stops at 65 535 | `(g&3)` fixes family shape; Ramp A caps at 40; self-refuted §9.4 |
| Per-stage risk | deltas enumerated + census actually run | `getGame` throws 4 stages before admission control; regex gate | second derivation regime clients must learn; gen-2 speed id is odd |
| Diff size | biggest client stage, but buys auto-extension | smallest server path to first game | smallest helper diff; zero client touchpoints changed |
| Stageability | single-file reverts throughout; expand-before-allocate | good; Stage 9 flip irreversible in effect | best rollback table; named point of no return |
| Invisibility | census-verified; first visible stage severable | strictly server-internal through Stage 6 | two gates (visible flag + absent offering row) |
| Silent rollout | correct, but misses the ratings path | found the room-list chokepoint; verified all 3 clients | only design to find the ratings leak (V6 confirms) |
| Ops burden | derived views; pickers auto-extend | ~13 JSP surfaces manual per game, forever; 2 jsp gates; capability retirement | two regimes forever; ~15 hand-edited lists per game |
