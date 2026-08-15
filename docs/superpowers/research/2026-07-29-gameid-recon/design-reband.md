# Staged refactor strategy — "Gen-2 quad banding": the minimal-diff widening option

**Angle**: devil's advocate for *minimal widening*. Keep the derivation arithmetic
(`speed = base+1`, `tb = base+OFFSET`), re-band it so the ceiling moves, and change as
little as physically possible. Push it until it breaks, then say exactly where.

**Grounding**: every claim below is traced to the recon maps in this directory —
`map-java-core.md`, `map-java-db-wire.md`, `map-jsp-web.md`, `map-react.md`,
`map-react-android.md`, `map-android.md`, `../ios-game-id-touchpoints.md`,
`MASTER-game-id-refactor-report.md`. Where the maps are silent I say so and mark it
**[OPEN]** rather than inventing behaviour.

---

## 0. TL;DR verdict (read this, then decide whether to read the rest)

1. **The re-band angle is right about the first four stages and wrong about the fifth.**
   Stages 0–3 below are mandatory *regardless of which end-state you pick* — you cannot
   allocate any id ≥ 49 without them (`map-java-core.md` §D: `allGames[]` is a 49-slot
   array that is id-indexed at 1..32 and positionally packed at 33..48;
   `gridStates[]` is 49 long and `getColor` AIOOBEs on every TB id). So "minimal widening"
   is **not cheaper than the registry approach for the first 80% of the work**. It shares
   the entire prerequisite.

2. **Where it becomes genuinely cheaper is one stage only**: it lets
   `MySQLGameVenueStorer.registerGame()` (`:~246-268`, does `speedGame = baseGame + 1;
   tbGame = baseGame + 50` inline), `getSpeedGame`/`getNormalGame`
   (`GridStateFactory.java:459-465`) and `getNormalGameFromTurnbased` (`:467-469`) keep
   working on new ids with a one-line `if (id < GEN2_FLOOR)` added to each, instead of
   becoming descriptor-driven. That is worth roughly **one stage, ~2 weeks**.

3. **It removes the numeric ceiling only on Ramp C** (widen the DB to `INT`). On Ramp A
   (stay ≤255, zero DB migration) the ceiling is **40 new families**; on Ramp B
   (`SMALLINT UNSIGNED`) it is **16 360**. See §9 for the precise arithmetic.

4. **It never removes the *shape* ceiling, and that one bites immediately.** A stride-4
   quad hard-codes "every family is exactly {live, speed, tb, spare}". Renju already
   violates this in practice (`map-android.md` #5: `Table.gameNames` has 31 = Renju with
   *no* Speed pair; `../ios-game-id-touchpoints.md` #24:
   `ArenaTableSetupView.swift:257` computes row count as `Table.gameNames.count / 2` and
   "silently truncates the last game if the dict size is ever odd"). Every family that
   doesn't want all four slots burns dead ids that `(g & 3)` will classify as
   valid-but-nonexistent — **re-creating the exact phantom-band pathology Stage 2 just
   removed**.

5. **Recommendation**: adopt Stages 0–3 verbatim (they are the honest floor of the
   effort, and they are unavoidable). At Stage 4, **keep the quad *convention* for
   allocation** — so ids stay eyeball-decodable in the 15 hand-copied id lists and the
   raw magic numbers in `tb/gameScript.js` / `tb/mobileGame.jsp` — but **derive semantics
   from a descriptor field, not from `(g & 3)`**. Same diff size, no shape ceiling.

6. **Silent rollout**: yes for stages 0–6, no for mobile at stage 7 *unless* you ship a
   defensive mobile patch first. See §10.

---

## 1. What "minimal widening" can and cannot mean here

The naive form — "bump `TB_START` from 50 to 1000" — is **disqualified on requirement 3**.
It renumbers every existing TB id (51..81 → 1001..1031), which requires rewriting
`game_event.game`, `pente_game.game` (indexed), `pente_move.game`, `dsg_player_game.game`
(**PK component**), `dsg_server_game.game` (**PK component**), `tb_game.game`,
`tb_game_ai.game` — and would instantly break every client that hardcodes a TB literal:

| Hardcoded legacy TB ids in clients | Cite |
|---|---|
| `81` (TB_RENJU) in 4 places in one file | `map-react.md` §1A — `boardGeometry.js:19,40,71` + `GameClass.js:297` |
| `69/71/73` (TB_GO/GO9/GO13), `81` | `map-jsp-web.md` #5-8 — `tb/gameScript.js:83,577,600,642` |
| `63` (TB_CONNECT6) ×20 sites, `77/79` (TB_SWAP2*), `57/67` (TB_DPENTE/DKERYO) | `map-jsp-web.md` #10,12,13 — `tb/mobileGame.jsp` |
| TB ids 51..81 in a fourth hand-maintained map | `../ios-game-id-touchpoints.md` §0-D — `SocialViewController.swift:28-39` |
| `id == 81 → RENJU` special case | `map-android.md` #2 — `rules/Variants.java:90-94` |

So the only admissible form of re-banding is: **freeze generation 1 exactly where it is,
and give generation 2 its own band with its own derivation rule.** Nothing is renumbered,
ever. Requirement 3 is then satisfied *by construction* at every stage.

## 2. The Gen-2 quad layout (concrete numbers)

```
  band          ids           rule                                   families
  ------------- ------------- -------------------------------------- --------
  GEN-1 live    1 .. 32       base odd, speed = base+1               16 (frozen)
  GEN-1 tb      51 .. 81 odd  tb = base + 50                         16 (frozen)
  POISON        33 .. 50      permanently unallocated (see below)     0
  RESERVED      82 .. 95      permanently unallocated (canary)        0
  GEN-2         96 .. N       base = 96 + 4k                         (N-92)/4
                              speed = base + 1
                              tb    = base + 2
                              base+3 reserved
```

Predicates, for `g >= GEN2_FLOOR (96)`:

```java
isSpeedGame(g)      ==  (g & 3) == 1
isTurnbasedGame(g)  ==  (g & 3) == 2
baseFamilyId(g)     ==  g & ~3
```

and for `g < 96`, the existing gen-1 rules verbatim (`g < 50 && g % 2 == 0`; `g > 50`;
`g - 50`).

**Why 33..50 must stay poisoned.** After Stage 2 those ids become *technically*
allocatable, but the gen-1 rule `tb = base + 50` would put their TB twins at 83..99,
colliding with `GEN2_FLOOR`. Leaving them dead costs 8 families and keeps the gen-1
rule "everything ≤ 50 is gen-1 live, everything 51..81 is gen-1 TB" exactly true. It also
preserves a diagnostic: any id observed in 33..50 after Stage 2 is a bug, and a hard
assertion can say so. (`map-java-core.md` risk flag: `gridStates[33..48]` are permanently
null today and `PlayerStatsDialog.java:315-324` already queries player stats for those
phantom ids — a live latent bug caused by exactly this band.)

**Why 82..95 is reserved.** It is the band immediately above `getMaxGameId()` (=81,
`GridStateFactory.java:426-428`). Leaving it dead means the *first* gen-2 id is
unambiguously ≥ 96, so any code that still assumes `> 81 == invalid` fails loudly on a
value in the reserved band during testing rather than silently on a real id.

## 3. Ceiling arithmetic per ramp

| Ramp | DB change | Max gen-2 base | New families | Total families | Req-4 satisfied? |
|---|---|---|---|---|---|
| **A** | none (`TINYINT UNSIGNED`, 255) | 252 | **40** | 56 | **No** — hard ceiling |
| **B** | 5 cols → `SMALLINT UNSIGNED` | 65 532 | **16 360** | 16 376 | No, but practically yes |
| **C** | 5 cols → `INT UNSIGNED` | 4.29e9 | ~1.07e9 | ~1.07e9 | Practically yes |

The five columns are `game_event.game`, `pente_game.game`, `pente_move.game`,
`dsg_player_game.game`, `dsg_server_game.game` — all `TINYINT(3) UNSIGNED`, the last two
being PRIMARY KEY components on MyISAM (`map-java-db-wire.md` §A, §F rows 5-9).
`tb_game`/`tb_game_ai` are already `SMALLINT UNSIGNED` and `webdb_*` are `SMALLINT`
signed, so those three subsystems need nothing on Ramp A or B.

Project history calibration: **16 families in ~20 years**. Ramp A's 40 new families is
~50 years of headroom at the historical rate. That is the honest case for stopping at
Ramp A and never migrating the DB — and equally the honest reason it fails requirement 4
as literally written.

## 4. Stage overview

| # | Stage | Track | Player-visible? | Size | Blocks cap? |
|---|---|---|---|---|---|
| 0 | Pin & census (tests + read-only DB inventory) | server | no | S | prereq |
| 1 | Mobile defensive patch (Android + iOS) | **mobile, parallel from t=0** | no | M | no (but critical path) |
| 2 | De-alias identity primitives → maps; null-safe network entry | server | no | M | **YES** |
| 3 | Name the boundaries: public `TB_START`, one predicate impl, kill 14 literals | server | no | S | **YES** |
| 4 | Gen-2 band arithmetic + hard validation + `visible` flag. **Allocate nothing.** | server | no | S | **YES** |
| 5 | Descriptor-ize per-family behaviour flags (divergences preserved verbatim) | server | no | M | no (quality) |
| 6 | Allocate family #17 in gen-2; dark launch (`visible=false`) | server | no | S | no |
| 7 | Web client enablement (JSP/JS + react_live_game_room) | web | **YES — first** | M | no |
| 8 | Mobile enablement (flip `visible` for mobile once patched builds dominate) | mobile | yes | S | no |
| 9 | **Conditional** — DB widening, only when family #57 is needed | DB | no | L | Ramp B/C only |

**Stage 1 has no dependencies and must be dispatched at t=0**, in parallel with Stage 0.
Its adoption clock (users update on their own schedule) is the critical path for the whole
programme; every other stage is a matter of weeks, that one is a matter of months.

---

## 5. Stages in detail

### Stage 0 — Pin & census

**Scope (test/read-only, zero production code).**

- Java characterization test over the whole `GridStateFactory` public surface for
  `game = 0..100`: `getGame`, `getGameName`, `getDisplayName`, `createGridState(int)`,
  `createGridState(int, MoveData)`, `getColor`, `isSpeedGame`, `isTurnbasedGame`,
  `getSpeedGame`, `getNormalGame`, `getNormalGameFromTurnbased`, `isValidGame`,
  `isSingleGameSet`, `firstMoveCanBeOffCenter`, `getCenterMove`, `getGameId(String)`,
  `getNumGames`, `getMaxGameId`, `getAllGames`, `getDisplayGames`, `getNormalGames`,
  `getSpeedGames`, `getTbGames`. Record the result **or the thrown exception class** per
  (method, id) into a golden file.
- Extend, do not replace, the four tests that already exist:
  `game/test/RenjuFactoryTest.java:68-70`,
  `game/test/GridStateFactorySingleGameSetTest.java`,
  `game/test/MySQLGameVenueStorerTbLookupTest.java:13-15,68`,
  `httpdocs/gameServer/js/boardCommon.test.js:82-85` (the only unknown-id test anywhere
  in the stack — it asserts `game=99999` throws).
- Read-only DB census (see §7 for the exact queries) establishing which ids actually
  exist, so Stage 2's behaviour changes can be proven unreachable.

**Why invisible**: nothing in `src` changes.

**Critical framing**: the golden file **pins the current bugs on purpose**. It will record
`getGame(33) -> TB_PENTE_GAME` (id 51) and `getColor(m, 51) -> AIOOBE`. That is a pin, not
an endorsement. Stages 2–4 change specific golden entries with a written rationale and a
reviewed diff; a stage that changes an entry it didn't intend to change fails review.

**Verify**: tests green on unmodified HEAD; golden file committed.
**Rollback**: delete the tests. Nothing to undo.
**Size**: S — ~300 lines of test, ~1 day.

---

### Stage 1 — Mobile defensive patch (parallel track, dispatch at t=0)

**Scope.** Turn every unknown-id *crash* in both mobile apps into a defined, boring
degrade. This is the only stage whose cost is dominated by wall-clock (store review +
user adoption), so it starts first and runs concurrently with everything else.

Android (`pentelive-android/app`):
- `liveGameRoom/Table.java:165-177` `shouldTimerRun()` — `gameNames.get(game).contains(...)`
  with **no null check**; `map-android.md` risk flag #1 calls this a *confirmed* NPE crash
  path for any unrecognized id on a timed table with no moves yet. Replace with a
  null-guarded lookup.
- `liveGameRoom/Table.java:1011-1012` `getGameName()` — returns `null` silently; make it
  return a neutral literal (e.g. `"Game #" + id`).
- `TableListAdapter.java`, `LiveTableFragment.java` — **[OPEN]** `map-android.md`
  explicitly flags these two as read-but-not-re-verified after context compaction, and
  "likely share `shouldTimerRun`'s unchecked-map-lookup pattern". Audit both before
  shipping this patch.
- Do **not** touch the six `strings.xml` `<string-array>` lists or
  `ArenaTableSetupDialog.java:66` (`spinnerPosition*2+1`). Those bound what the app can
  *create*, which is already a safe failure mode ("cannot select it at all",
  `map-android.md` §unknown-id). YAGNI.

iOS (`penteLive-iOS/test1`):
- `HelperClasses.swift:135` (`shouldTimerRun()`) and `:624` (`gameName()`) —
  force-unwrapped `Table.gameNames[game]!`; `../ios-game-id-touchpoints.md` risk flag #1
  calls this "the single highest-severity item — turns any id gap during a staged rollout
  into a guaranteed crash". Replace both with a safe accessor defaulting to a neutral
  name, matching the codebase's one *intentional* precedent,
  `BoardVariantMapping.variant(forGameType:)` (`BoardVariantMapping.swift:8-30`), which
  documents its Pente fallback as a design decision.
- `HelperClasses.swift:627-657` `gameColor()` — unbounded final `else` maps any id ≥ 31 to
  Renju's board colour. Give it an explicit neutral colour for unknown ids. (This is a
  *silent* bug, so it will not be caught by crash testing — flag #5.)
- `ArenaTableSetupView.swift:257` — `Table.gameNames.count / 2` row count "silently
  truncates the last game if the dict size is ever odd" (#24). A gen-2 family added to
  `gameNames` without a Speed twin would trip this. Fix now, while it is a no-op.

**Why invisible**: every changed branch is reachable only by an id that does not exist
anywhere in the system today. The DB census (§7) proves that: no row in any table carries
an id outside `1..32 ∪ 51..81`. So this ships as a pure hardening release with zero
observable behaviour change.

**Verify**: unit tests asserting each patched accessor returns the neutral value (not a
crash) for ids 96, 97, 98, 255, 1000, -1, 0 — mirroring `boardCommon.test.js`'s
`game=99999` assertion, the pattern the codebase already trusts. Manual smoke: join
existing Pente/Renju/Go tables on both apps, confirm names/timers/colours unchanged.

**Rollback**: revert; the previous build is still in stores' phased-release control.
Because nothing else depends on this stage *landing*, only on it being *adopted*, a
rollback costs schedule, not correctness.

**Size**: M — ~6 files across two platforms, ~150 lines. Wall clock is 2 store releases
plus an adoption tail you should measure, not guess.

---

### Stage 2 — De-alias the identity primitives (the unavoidable one)

**Scope**: `GridStateFactory.java` + four network entry points. **No id value changes.**

Replace the dual-addressed positional arrays with maps keyed by the *real* id:

| Today | Cite | After |
|---|---|---|
| `Game allGames[]` — 49 slots, id-indexed 1..32, positionally packed 33..48 with TB `Game` objects | `GridStateFactory.java:139-154` | `Map<Integer,Game> byId` built from the same singletons |
| `GridState gridStates[] = new GridState[getNumGames()+1]` — length 49 | `:237`, boot loop `:240-243` | `Map<Integer,GridState> prototypes` |
| `getGame(g)`: `g > TB_START ? tbGames[(g-51)/2] : allGames[g]` | `:392-398` | `byId.get(g)` |
| `createGridState(g, MoveData)`: `g > TB_START ? tbGridStates[(g-51)/2] : gridStates[g]` | `:384-390` | `prototypes.get(g)` |
| `getColor(moveNum, g)`: `gridStates[g]` | `:479-481` | `prototypes.get(g)` |
| `getSpeedGame` / `getNormalGame`: `allGames[id ± 1]` | `:459-465` | `byId.get(id ± 1)` (arithmetic **unchanged** — that is the point of this angle) |
| `getNumGames()` = `allGames.length - 1` = 48 (neither a count nor a max) | `:422-424` | split into `maxKnownId()` and `familyCount()`; only 2 callers |

Also fold in the null-safety that map-ification *creates the need for*: `getGame` now
returns `null` where it previously threw AIOOBE or returned the wrong object, so the four
places that feed it an unvalidated client-supplied id must reject instead of NPE-ing
later:

- `ServerTable.java:284` — `DSGArenaCreateTableEvent.getGame()`
- `ServerTable.java:910` / `:932` — `changeStateEvent.getGame()` (the live table
  game-change path; `map-java-core.md` risk flag calls this "AIOOBE/NPE reachable from the
  network")
- `ServerTable.java:976` — same
- `turnBased/web/NewGameServlet.java:195` — currently `game > getMaxGameId()` only, which
  "admits the dangerous 33..50 band" (`map-java-db-wire.md` §E.2)

Deliberate, reviewed golden-file diffs — all confined to ids that do not exist:

| id range | before | after |
|---|---|---|
| 33..48 | `getGame` returns a **TB `Game` with a different id** (silent wrong answer) | `null`, event rejected |
| 49, 50 | `AIOOBE` | `null`, event rejected |
| 82+ | `AIOOBE` | `null`, event rejected |
| 51..81 into `getColor` | `AIOOBE` (`gridStates.length == 49`) | correct colour |

Keep `getAllGames()` returning the **same 49-slot array with the same contents including
the leading null**, marked `@Deprecated`. Its five callers all iterate `i = 1..48`
(`PlayerStatsDialog.java:316`, `followersing.jsp:68`, `newTourney.jsp:118`,
`broadcast.jsp:73`, `NewDialog2.java:19`) and changing its shape would be a visible change
to four JSP pages for no benefit this stage. YAGNI.

**Note on a map discrepancy**: `map-java-db-wire.md` describes `getGame(77)` as resolving
via `allGames[47]`, while `map-java-core.md` shows it taking the `> TB_START` branch to
`tbGames[13]`. Both land on `TB_SWAP2PENTE_GAME`, so the two anomalous live rows
`pente_game (gid=77, game=77)` and `(gid=79, game=79)` resolve identically before and
after this stage — but the golden file must assert that explicitly rather than reason
about it.

**Why invisible**: no id changes; every changed branch is provably unreachable per the
§7 census; the four rejections replace paths that already destroyed the connection
(`SocketDSGEventHandler` catches `Throwable` → `handleError` → `destroy()`,
`map-java-db-wire.md` §E.2) — so a malformed client goes from "disconnect with the table
left inconsistent" to "event ignored", strictly an improvement.

**Verify**: golden diff limited to exactly the four rows above, reviewed line by line.
`RenjuFactoryTest` / `GridStateFactorySingleGameSetTest` unchanged. Local smoke on
`penteorg-pente.org-1` (bind-mount compiled classes + restart, per the project's
local smoke-test procedure) covering one game of each family shape: Pente (plain),
Speed Pente (parity), Go 9x9 (board size), Connect6 (double stone), Swap2-Pente
(negotiated opening), Renju (15x15 + no-Speed-in-some-clients), TB Renju (81).

**Rollback**: revert one file plus four one-line guards. No data touched, no id written.

**Size**: M — one file, ~150 lines changed, ~2 days including the smoke matrix.

---

### Stage 3 — Name the boundaries (pure inlining, byte-identical)

**Scope**: make `TB_START` public (`GridStateFactory.java:61`, currently `private` — which
is *why* ~14 sites re-hardcode the literal `50`), then route every hand-copied literal
through the factory's own predicates. **Zero-diff stage**: the golden file must not move
at all.

Sites to convert (all from `map-java-core.md` class B and `map-java-db-wire.md` §C):

| File:line | Today | After |
|---|---|---|
| `gameServer/tourney/Tourney.java:123` | `return this.game > 50;` | `GridStateFactory.isTurnbasedGame(game)` |
| `gameServer/mobile/KothResponse.java:61,83` | `game > 50`, `tree.get(game - 1 - 50)` | predicate + `getNormalGameFromTurnbased` |
| `gameServer/mobile/IndexResponse.java:447,481` | `> 50` (the `"tb-"` prefix trick) | predicate |
| `kingOfTheHill/CacheKOTHStorer.java:96,177,213` | `game > 50` | predicate |
| `game/MySQLGameVenueStorer.java:466` | `int baseGame = game > 50 ? game - 50 : game;` | `baseFamilyId(game)` |
| `gameServer/tourney/CacheTourneyStorer.java:487,741,815` | `> 50`, `getDisplayName(game - 50)` | predicate + `getNormalGameFromTurnbased` |
| `httpdocs/gameServer/broadcast.jsp:75` | `if (games[i].getId() > 50) continue;` | predicate |
| `httpdocs/gameServer/kothBox.jsp:88` | `getGame() > 50 ? "TB-" : ""` | predicate |
| `gameServer/core/FastMySQLDSGGameLookup.java:55-56,104-105,157-158` | 3 copies of the TB→base+flag decomposition | single helper |
| `tools/RatingsGrapher.java:116,131,141,146-150` | 5 copies of the same | single helper |

Two more, which are *not* `> 50` but are the same class of duplicated derivation and
**must** move now because Stage 4 changes the rule they encode:

- `game/MySQLGameVenueStorer.java` `registerGame(...)` — derives `speedGame = baseGame + 1`
  and `tbGame = baseGame + 50` inline. This is the boot-time `game_event` row generator
  (`registerAllGames`, `:699-707`, called from `DSGContextListener.java:118`). If it keeps
  its own arithmetic, gen-2 families get gen-1-shaped `game_event` rows.
- `gameServer/client/awt/GameBoardFrame.java:939` (`(game - 1) / 2`) and
  `client/web/GameBoard.java:226` / `client/awt/GameBoard.java:~226` (`setGridState(num/2)`)
  — dense-slot halving in the legacy applet clients. **Decision: do not fix, do not
  extend.** These are the AWT/web applet clients; they will hard-break on any gen-2 id.
  Add a comment and a `if (id >= GEN2_FLOOR) return;` bail so they degrade to "applet
  doesn't offer this game" rather than mis-render. YAGNI — do not rebuild the applet.

Also fix here, because it is a **prerequisite for the first new game** and is cheap:

- `kingOfTheHill/MySQLKOTHStorer.java:31` returns `-1` on a missing `game_event` row, but
  all four `CacheKOTHStorer` call sites guard `if (hill_id == 0) return;`
  (`:111,147,202,226`). An unregistered game therefore **writes and reads KOTH rows under
  `koth_id = -1`** (`map-java-db-wire.md` §C4 latent-bug flag). Align the sentinel.
- The KOTH event-name string is inconsistent three ways:
  `MySQLGameVenueStorer.KOTH_EVENT = "King of Hill"`, `MySQLKOTHStorer` queries
  `name='King Hill'`, and the live DB actually contains `'King of the Hill'`. `eid`
  resolution is an exact string match, so a gen-2 family's hill will silently not resolve.
  **[OPEN]** — confirm the live value with the §7 query before choosing which string wins;
  changing it is a data migration on `game_event.name`, so it may deserve its own stage.

**Why invisible**: pure inlining. Same inputs, same outputs, for every id that exists.
**Verify**: golden file diff must be **empty**. Plus a grep gate in CI:
`grep -rnE '(> *50|>= *51|- *50|\+ *50)' dsg_src/java dsg_src/httpdocs` returns only
`GridStateFactory.java` and known false positives (`LeaderBoard.java:66` is
`wins+losses+draws >= 50`, not an id).
**Rollback**: revert ~18 one-line edits, independently.
**Size**: S — ~half a day, ~18 files, 1-3 lines each.

---

### Stage 4 — Gen-2 band arithmetic + validation + visibility flag (allocate nothing)

**Scope**: teach the six derivation helpers the two-generation rule, add a membership-based
validator, add a `visible` flag. **No gen-2 id is allocated in this stage, so every new
branch is dead code.**

```java
public static final int GEN2_FLOOR = 96;   // first gen-2 base; 96 = 4*24
// 33..50 poisoned, 82..95 reserved — see §2

static boolean isSpeedGame(int g) {
    return g >= GEN2_FLOOR ? (g & 3) == 1 : (g < TB_START && (g & 1) == 0);
}
static boolean isTurnbasedGame(int g) {
    return g >= GEN2_FLOOR ? (g & 3) == 2 : (g > TB_START);
}
static int baseFamilyId(int g) {
    return g >= GEN2_FLOOR ? (g & ~3) : (g > TB_START ? g - TB_START : (isSpeedGame(g) ? g - 1 : g));
}
```

and the same one-line fork in `getSpeedGame` / `getNormalGame` /
`getNormalGameFromTurnbased` / `MySQLGameVenueStorer.registerGame`.

Additionally:
- Replace `isValidGame(g)` (`GridStateFactory.java:400`, currently `g >= 1 && g <= 32` —
  live-only, returns `false` for every legitimate TB id, and is called nowhere on the
  server path) with `byId.containsKey(g)`. Generalize the pattern
  `KotHServlet.java:54-62` already uses correctly (membership test, not range test).
- Replace `getMaxGameId()` (`:426-428`, hardcoded `TB_RENJU`) — its one caller
  `NewGameServlet.java:195` becomes a membership check.
- **`gameDatabase/MySQLGameStorerSearcher.java:400`** uses `GridStateFactory.TB_RENJU`
  as an upper range bound in a search filter. Left alone, every gen-2 TB game is silently
  excluded from the game database. Convert to membership.
- Add `boolean visible` to `Game` (default `true` for all 32+16 existing entries) and
  filter the five enumeration APIs on it: `getDisplayGames`, `getNormalGames`,
  `getSpeedGames`, `getTbGames`, `getAllGames`. This is the **dark-launch switch** and it
  is the single most important addition in the whole plan for requirement 2 — it is what
  makes Stage 6 invisible. ~20 lines.

**Why invisible**: `GEN2_FLOOR` is 96 and nothing allocates ≥ 96 until Stage 6, so both
new branches are unreachable. `visible` defaults `true`, so enumeration is unchanged.
`isValidGame`/`getMaxGameId` changes only affect ids in the poison/reserved bands.

**Verify**: golden diff limited to `isValidGame(51..81)` flipping `false → true` (a
correction — those are legitimate ids) and to poison/reserved-band rows. New unit tests
asserting the gen-2 predicates for a *hypothetical* family at 96/97/98 without
registering it. Confirm iteration **order** of `ALL_GAMES` (`= TB_GAMES ++ LIVE_GAMES`,
`GridStateFactory.java:85`) is preserved — it is observably relied on by
`CacheKOTHStorer.java:150` and by `IndexResponse.java:419-420`, whose trailing comment
`// odd first` documents the dependency.

**Rollback**: revert one file. Nothing allocated, nothing persisted.
**Size**: S — ~60 lines in one file plus 3 one-liners elsewhere.

---

### Stage 5 — Descriptor-ize the behaviour predicates (optional for the cap)

**This stage does not remove the cap. Skip it if you will only ever add 1-2 more games.**
Include it if requirement 4 ("arbitrarily many") is real, because without it every new
family requires ~9 correct edits to scattered OR-chains and the maps show that process
has already failed in production.

**The evidence it has already failed**: the "has a negotiated / off-centre opening"
predicate exists in **5 hand-copied places with 3 disagreeing member lists**
(`MASTER-game-id-refactor-report.md` §2):

| Site | Members |
|---|---|
| `GridStateFactory.firstMoveCanBeOffCenter` | Go + DPENTE + DKERYO |
| `HttpGameServlet.java:298-303` | DPENTE + DKERYO + GO |
| `MobileGameServlet.java:222-225` | DPENTE + DKERYO (**no Go at all**) |
| `ServerTable.java:1930-1938` | DPENTE + DKERYO + GO + GO9 + GO13 + SWAP2 |
| `tb/mobileGame.jsp:745,918,1213` | DPENTE + DKERYO + SWAP2 (**no Go family**) |

**Critical design choice — and this is where minimal-diff discipline pays off: do NOT fix
the disagreement in this stage.** Introduce one descriptor field *per site* that
reproduces that site's current member list **exactly**
(`firstMoveOffCenter_liveTable`, `_archiveSearch`, `_mobileSearch`, …). The refactor stays
byte-identical and therefore player-invisible. Reconciling the five lists is a genuinely
player-visible behaviour change (it alters what the mobile game-database endpoint returns
for Go games) and belongs in its own, separately-reviewed, separately-rollback-able
change that is **not** on the cap-removal path.

This is exactly the precedent the Android team already set: `VariantPredicateEquivalenceTest`
documents that `isSwap2()` "DIVERGES on 'Speed Swap2-*' strings; NOT rerouted" — the
migration author found a real divergence and deliberately kept the legacy path rather than
silently unifying it (`map-react-android.md` #14). Follow that, predicate by predicate,
with pinning tests written *before* each move.

Other predicates to descriptor-ize the same way:
- `ServerTable.java:1930-1938` — auto-place centre stone unless negotiated opening.
  **Fail-open in the dangerous direction**: an unlisted new game gets a *forced* centre
  stone (`map-java-core.md` class G).
- `ServerTable.java:2778` (`single_game`) vs `GridStateFactory.java:502-507`
  (`isSingleGameSet`) — two hand-copied copies of the same set.
- `ServerTable.java:3650-3653` (`k32Game`) → `Game.kFactor`; a new game silently defaults
  to K=64.
- `turnBased/TBGame.java:196-202` (board geometry), `:293-330` (seat/colour rotation),
  `:626-629` (negotiated opening) → descriptor fields; an unlisted TB game gets plain
  alternating seats on a 19×19 board.
- `MMAIProtocol.java:47-48` (`stonesPerTurn`).
- **Leave `MMAIPlayer.SUPPORTED_GAMES` (`:70-77`) exactly as it is.** It is already the
  best pattern in the codebase — a `HashSet` allow-list whose docblock explains that an
  unlisted game "would be silently remapped to the plain Pente engine, so it is rejected
  up front". A new game gets no bot until explicitly wired. That is the correct default.

**Why invisible**: every field reproduces its site's current list verbatim; golden diff
empty; add per-site parity tests asserting old-predicate == new-field for all 48 ids.
**Rollback**: per-predicate; each move is independent.
**Size**: M — ~10 predicates × (1 field + 1 parity test), ~3-4 days.

---

### Stage 6 — Allocate family #17 in gen-2, dark launch

**Scope**: add one family at base 96 (`96` live, `97` speed, `98` TB, `99` reserved) with
`visible = false`. Register its `game_event` rows via the existing boot path
(`DSGContextListener.java:118` → `MySQLGameVenueStorer.registerAllGames(2)` →
`:699-707`, array-driven, wrapped in `catch (Throwable)` at `DSGContextListener.java:119-141`
so a bad entry cannot brick startup). Do **not** add it to the
`DSGContextListener.addServerGames(...)` arrays (servers 1, 37, 45=KOTH, 46=Go), so no
`dsg_server_game` row exists and the game is offered in no room.

**Why invisible**: two independent gates.
1. `visible=false` removes it from all five enumeration APIs → absent from every JSP
   dropdown (`newTourney.jsp:117-121`, `followersing.jsp:68`, `broadcast.jsp:73`), every
   KOTH list, every mobile ratings section (`IndexResponse.java:470-472`).
2. No `dsg_server_game` row → `MySQLServerStorer.addServerGames` graceful-skips (inserts
   zero rows, `map-java-db-wire.md` §E.3) → no room offers it → no table can carry the id
   → no `DSGChangeStateTableEvent` ever broadcasts it.

**Verify**: after boot, `SELECT eid, name, game FROM game_event WHERE game IN (96,97,98)`
returns the expected LIVE/TB/KOTH rows; `SELECT COUNT(*) FROM dsg_server_game WHERE game
>= 96` returns 0. Load a lobby on web, React and both mobile apps and diff the game lists
against a pre-stage capture — must be identical. Exercise the new family end-to-end by
temporarily flipping `visible` on a **staging** instance only.

**Rollback**: remove the family from the arrays; delete its `game_event` rows
(`DELETE FROM game_event WHERE game >= 96` — safe, nothing references those `eid`s yet
because no game was ever played). This is the **last stage with a clean, data-free
rollback**.

**Size**: S — the descriptor entry plus one `createGridState` case, ~1 day. (The *rules*
for the new game are its own project and are out of scope here.)

---

### Stage 7 — Web client enablement — **FIRST PLAYER-VISIBLE STAGE**

**Scope** (per-game work; re-banding does nothing to reduce it — see §9.4):

*Tomcat JSP / hand-written JS* (`dsg_src/httpdocs/gameServer/`):
- `gameConstants.jspf` — 48 hand-written lines emitting `GAME.<CONST> =
  <%= GridStateFactory.<CONST> %>`. Add 3. **Optional improvement**: add a `symbolicName`
  to the descriptor and generate this file by looping the registry — small, invisible, and
  it removes one of the ~15 hand-maintained lists permanently. Recommended, but not
  required.
- `js/boardCommon.js:9-45` `getBoardColor` — add 3 symbolic cases. This is the one
  fail-loud dispatcher in the entire stack (`default: throw new Error("... unknown game
  id " + game)`) and it has the only unknown-id regression test
  (`boardCommon.test.js:82-85`). **Adding the cases is mandatory** or the board throws.
- `tb/gameScript.js:83,577,600,642` and `tb/mobileGame.jsp` (20+ raw `game === 63` sites,
  plus `57/67/77/79/69/71/73/31/32/81`) — only touch the branches the new game actually
  needs. Do not attempt to symbolize all 30+ raw literals in this stage; that is a
  separate cleanup.

*react_live_game_room* (`react_live_game_room/src/`):
- `game/boardGeometry.js` — `gridSizeForGame` (line 17-19), `variantKey` (27-41),
  `boardSpecialPoints` (71), and `STANDARD_GAME_IDS` (51, the sole picker enumeration).
- `Classes/utils.js:7-21` `VARIANT_NAMES`; `Classes/TableClass.js:10-22` `VARIANT_COLORS`;
  `Classes/GameClass.js:28-42` `VARIANT_RULES` (the replay/move engine dispatch).
- **Do this at the same time as one hardening change**: `boardGeometry.js:41`'s
  unconditional `return 'swap2-keryo'` fallback is the worst failure mode found anywhere
  in the stack — an unrecognized id "renders a fully playable board that silently applies
  the wrong game's capture/opening rules, with no crash and no console warning"
  (`map-react.md` §1E). Convert it to a throw, matching `boardCommon.js`, and add the
  `game=99999` test. Without this, a browser holding a **cached older bundle** silently
  plays the new game with Keryo capture rules — "old clients" exist on web too.
- `utils.js:32` `g % 2 === 0 ? 'Speed ' + base : base` and
  `Components/Table/SettingsModal.js:119` `table.game % 2 === 0 ? table.game - 1 :
  table.game` — **both encode gen-1 parity**. A gen-2 speed id (97, `97 & 3 == 1`, odd) is
  misclassified by both. These two lines are the client-side price of the re-band and
  must be fixed here.

*react_mmai*: **only if the new game is AI-playable.** `MMAIPlayer.SUPPORTED_GAMES`
already rejects unlisted games server-side, so react_mmai needs nothing otherwise. Note
`react_mmai/src/game/GameClass.js` `setGame()` has its own unconsolidated board-size
ladder — `boardGeometry.js`'s consolidation was never ported (`map-react-android.md` #11).
**Do not port it now.** YAGNI.

**Player-visible because**: flipping `visible=true` (web-scoped, see §10) makes the game
appear in web/React pickers and lobbies. This is the intended moment.

**Verify**: `boardCommon.test.js` + `boardGeometry.test.js` extended with the new ids;
a play-through of a full game per client; a spectate/replay of a *finished* new-game
record (this is the path that exercises `VARIANT_RULES` and the archive loader).
Confirm `MySQLPenteGameStorer.java:767-769,785,1071` and `MySQLWebDbStorer.java:420` —
which synthesise `moves[0]` from `getCenterMove(game)` and are **only correct when
`firstMoveCanBeOffCenter(game)` is false** — are correct for the new family, or you will
silently corrupt every archived and collected game of it (`map-java-db-wire.md` §C2/C8).

**Rollback**: flip `visible=false`. Games already played keep their rows; the client code
is inert without server-side visibility. Clean.
**Size**: M — ~10 files, ~2-3 days plus the new game's own rules work.

---

### Stage 8 — Mobile enablement

**Scope**: add the new family to Android's 7 lists (`Table.gameNames` +
`strings.xml:338,356,374,396,428,475`) and iOS's 4 maps (`GameEnum`,
`Table.gameNames`, `LobbyViewController.gameNames`, `SocialViewController.gameNames`),
then flip mobile visibility.

**The two structural blockers, both mapped**:
- `ArenaTableSetupDialog.java:66`: `int game = gameSpinner.getSelectedItemPosition()*2+1;`
  — the created table's id is *computed from the spinner row*, so it "structurally cannot
  express a non-doubled id" (`map-android.md` risk flag #3).
- `TableSetupView.swift` and `ArenaTableSetupView.swift:71,257,270,283,284` — the same
  `row*2+1` / `game/2` / `count/2` pattern; `../ios-game-id-touchpoints.md` risk flag #2
  calls these "the riskiest spot to touch during a renumbering, the most likely place to
  silently send the *wrong* id".

A gen-2 base of 96 is even, so `row*2+1` can never produce it and `game/2` inverts wrong.
Both pickers must switch from arithmetic to an explicit `[(id, label)]` array. That is a
~30-line change per platform and it is unavoidable — **and note it would be equally
unavoidable under explicit allocation**, so it is not a cost of the re-band specifically.

Fix at the same time (they are already broken, independently of this refactor):
- `LobbyViewController.gameNames` is missing Renju/Speed Renju entirely — confirmed drift
  from when Renju shipped (`../ios-game-id-touchpoints.md` §0-C).
- `SocialViewController.gameNames` invents a `+50` TB scheme that no other iOS structure
  knows about (§0-D) — reconcile it against the shared table.
- Android's `rules/Variant.java` + `Variants.java` registry exists but is "wired only into
  unit tests, not into production `Table.java`/`Game.java`/`KingOfTheHill`"
  (`map-android.md` risk flag #4). **Decision: do not complete that migration now.** It is
  a separate, tracked "Task 8". Adding the new family to the production `Table.gameNames`
  *and* to `Variant.java` keeps both in sync via the existing
  `VariantPredicateEquivalenceTest`. YAGNI.

**Gating**: do not flip mobile visibility until patched-build (Stage 1) adoption is high
enough — **measure it, do not guess**. If the protocol carries a client version this can
be a server-side per-client filter; **[OPEN]** the maps do not establish that a version
field exists.

**Verify**: on a *pre-Stage-1* build (deliberately), confirm the new game is invisible and
nothing crashes; on a patched-but-not-updated build, confirm it degrades to the neutral
name rather than crashing; on the new build, confirm full function.
**Rollback**: flip mobile visibility off. App-store builds cannot be rolled back, which is
why Stage 1 exists.
**Size**: S-M for the lists, M for the two pickers.

---

### Stage 9 — Conditional: DB widening (expand-then-contract)

**Only needed when family #57 is wanted** (gen-2 base would exceed 252). Until then,
Ramp A holds and this stage never runs.

**Expand phase** (the only phase with real work):
```sql
ALTER TABLE game_event      MODIFY game SMALLINT UNSIGNED NOT NULL;
ALTER TABLE pente_game      MODIFY game SMALLINT UNSIGNED NOT NULL;
ALTER TABLE pente_move      MODIFY game SMALLINT UNSIGNED NOT NULL;
ALTER TABLE dsg_player_game MODIFY game SMALLINT UNSIGNED NOT NULL;  -- PK component
ALTER TABLE dsg_server_game MODIFY game SMALLINT UNSIGNED NOT NULL;  -- PK component
```
`tb_game`, `tb_game_ai` (already `SMALLINT UNSIGNED`) and `webdb_*` (`SMALLINT` signed,
32767) need nothing on Ramp B.

**Contract phase: there is none, and that is the good news.** The usual expand/contract
dance exists to retire a narrow *reader*. Here every Java field is already `int`
(`Game.java:5`, `Tourney.java:20`, `TBGame.java:42`, `SimpleGameEventData.java:40`,
`MySQLGameVenueStorer.java:108`, `ServerTable.java:3475`) and the JDBC reads are
`getInt`. `map-java-db-wire.md` verified there is **no** `getByte`/`setByte`/`getShort`
anywhere on the id path. So widening is value-preserving and read-compatible on day one;
nothing to contract.

**Why invisible**: no value changes; no client sees a column type.

**Verify**:
- Before: `SELECT game, COUNT(*) FROM <t> GROUP BY game ORDER BY game` per table, saved.
- `SHOW CREATE TABLE` before/after; `CHECK TABLE <t>` after.
- After: same GROUP BY, byte-identical result set.
- Grep gate: `grep -rn 'getByte\|setByte\|getShort\|setShort' dsg_src/java` returns no
  hit on a `game` column.
- The replica (`docker-compose-replica.yml`) must be migrated too, or replication of a
  >255 id breaks later.

**Rollback**: `MODIFY game TINYINT UNSIGNED` is value-preserving **only while no id > 255
exists**. That is the point of no return for the whole programme: after the first gen-2
family above 252 plays a single game, rolling the schema back requires deleting data.
Sequence the alter strictly before the allocation, and gate the allocation behind a check.

**Size**: L operationally — `dsg_player_game` and `dsg_server_game` are MyISAM with the
`game` column in the PRIMARY KEY, so both require a **full table rebuild**. **[OPEN]**
the maps report `dsg_player_game` as "60k+ rows" without a verified count — measure
`SELECT COUNT(*)` and time the rebuild on a restored copy before scheduling a window.

---

## 6. Wire protocol: no stage touches it

This is the strongest single finding across the recon and it removes an entire category of
risk. Both transports serialize `DSGEventWrapper` as **Gson JSON**:

- Raw TCP (mobile): `SocketDSGEventHandler` / `ClientSocketDSGEventHandler`, UTF-8 JSON
  text framed by a single `0xFF` byte. Confirmed independently from the *server* side
  (`map-java-db-wire.md` §D.1) and the *Android client* side (`map-android.md` headline).
- WebSocket (React/web): `WebSocketDSGEventHandler`, same Gson config,
  `session.getBasicRemote().sendText(...)`, no framing byte.

`grep -rn 'writeByte|writeShort|writeInt|readByte|readShort|readInt|writeUTF|readUTF'
org/pente/gameServer/event/` returns **zero hits**. Game ids are Java `int` in
`DSGChangeStateTableEvent`, `DSGArenaCreateTableEvent` and `DSGPlayerGameData`; the three
custom Gson adapters (`DSGColorAdapter`, `DSGPlayerDataAdapter`,
`DSGPlayerGameDataAdapter`) none of them touch game ids. React's
`src/protocol/messages.js:28,68` sends `game` as a bare JSON field and
`src/protocol/decode.js:36-46` checks only that required *field names* are present — no
range or type check.

**Consequences:**
1. No protocol version negotiation is needed at any stage. An old client parses
   `{"dsgChangeStateTableEvent":{"game":96,...}}` without error.
2. **There is no game-list event.** The server never enumerates games to clients; a client
   learns about game ids only from the ids it happens to *see* in broadcasts, plus its own
   hardcoded local list (`map-java-db-wire.md` §D.2). This is precisely why old mobile apps
   keep working for existing games — and it is also the mechanism the visibility gate
   exploits.
3. The only wire-adjacent change in the whole plan is **server-side inbound validation**
   (Stage 2), which is invisible to any conforming client.

Android's `Game.java` uses Parcelable `writeString` for the id while `RatingStat.java`
uses `writeInt` (`map-android.md` #18, #21). Both are 32-bit-safe; the inconsistency is an
audit trap, not a width limit. No action.

## 7. Verification queries (read-only, run against the live stack)

Base command: `docker exec penteorg-main_db-1 mariadb -uroot -p'<pw>' dsg -e "..."`

**Census — which ids exist (Stage 0 baseline):**
```sql
SELECT 'game_event' t, game, COUNT(*) c FROM game_event GROUP BY game
UNION ALL SELECT 'pente_game',      game, COUNT(*) FROM pente_game      GROUP BY game
UNION ALL SELECT 'pente_move',      game, COUNT(*) FROM pente_move      GROUP BY game
UNION ALL SELECT 'dsg_player_game', game, COUNT(*) FROM dsg_player_game GROUP BY game
UNION ALL SELECT 'dsg_server_game', game, COUNT(*) FROM dsg_server_game GROUP BY game
UNION ALL SELECT 'tb_game',         game, COUNT(*) FROM tb_game         GROUP BY game
UNION ALL SELECT 'tb_game_ai',      game, COUNT(*) FROM tb_game_ai      GROUP BY game
UNION ALL SELECT 'webdb_game',      game, COUNT(*) FROM webdb_game      GROUP BY game
ORDER BY 1,2;
```

**Band safety — proves Stage 2's behaviour changes are unreachable.** Every row must be 0:
```sql
SELECT 'game_event' t, COUNT(*) FROM game_event
   WHERE game BETWEEN 33 AND 50 OR game > 81 OR (game > 50 AND game % 2 = 0)
UNION ALL SELECT 'pente_game',      COUNT(*) FROM pente_game      WHERE game BETWEEN 33 AND 50 OR game > 81
UNION ALL SELECT 'pente_move',      COUNT(*) FROM pente_move      WHERE game BETWEEN 33 AND 50 OR game > 81
UNION ALL SELECT 'dsg_player_game', COUNT(*) FROM dsg_player_game WHERE game BETWEEN 33 AND 50 OR game > 81
UNION ALL SELECT 'dsg_server_game', COUNT(*) FROM dsg_server_game WHERE game BETWEEN 33 AND 50 OR game > 81
UNION ALL SELECT 'tb_game',         COUNT(*) FROM tb_game         WHERE game BETWEEN 33 AND 50 OR game > 81
UNION ALL SELECT 'tb_game_ai',      COUNT(*) FROM tb_game_ai      WHERE game BETWEEN 33 AND 50 OR game > 81;
```
Two known non-zero-adjacent anomalies to expect and account for, **both of which pass this
check**: `pente_game` rows `(gid=77, game=77)` and `(gid=79, game=79)` (TB ids stored in a
base-id column), and 4 `tb_game` rows with `game=1` (legacy corruption). Both are recorded
in `map-java-db-wire.md` §A. Neither is in the poison band; neither is affected.

**Column widths (Stage 9 gate):**
```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='dsg' AND COLUMN_NAME='game' ORDER BY TABLE_NAME;
```

**KOTH name reconciliation (Stage 3 [OPEN] item):**
```sql
SELECT DISTINCT name, COUNT(*) FROM game_event GROUP BY name;
SELECT k.koth_id, ge.game, ge.name FROM koth k
  JOIN game_event ge ON ge.eid = k.koth_id GROUP BY 1,2,3;
SELECT COUNT(*) FROM koth WHERE koth_id <= 0;   -- the -1 sentinel bug; must be 0
```

**Stage 6 dark-launch assertions:**
```sql
SELECT eid, name, game FROM game_event WHERE game >= 96;      -- expect LIVE/TB/KOTH rows
SELECT COUNT(*) FROM dsg_server_game WHERE game >= 96;        -- must be 0
```

## 8. Rollback summary — where the point of no return is

| Stage | Rollback | Destructive? |
|---|---|---|
| 0 | delete tests | no |
| 1 | revert; previous store build | no (costs schedule) |
| 2 | revert 1 file + 4 guards | no |
| 3 | revert ~18 one-liners, independently | no |
| 4 | revert 1 file; nothing allocated | no |
| 5 | per-predicate revert | no |
| 6 | `DELETE FROM game_event WHERE game >= 96` | no — no game played yet |
| 7 | `visible=false` | no — played games keep their rows |
| 8 | mobile `visible=false`; store build is *not* revertible | partially |
| 9 | `MODIFY ... TINYINT` — **only while no id > 255 exists** | **YES, after first >255 game** |

The single hard commit point is **Stage 9's alter followed by the first game played on a
family with base > 252**. Everything before that is reversible. If you stop at Ramp A,
there is no hard commit point in the entire programme.

## 9. Where this approach fails the "no ceiling" requirement — precisely

**9.1 — Numeric ceiling, Ramp A: fails outright.** 40 new families, hard-stopped by
`TINYINT UNSIGNED` on five columns. Requirement 4 says "no numeric ceiling"; this is a
ceiling, full stop. It is ~50 years of headroom at the project's historical rate, which is
an argument about *product* risk, not about whether the requirement is met.

**9.2 — Numeric ceiling, Ramp B: fails literally, passes practically.** 16 360 families.

**9.3 — Numeric ceiling, Ramp C: passes.** `INT UNSIGNED` gives ~1.07e9 families; the
remaining bound is Java `int`, which the recon confirms is the type end-to-end with no
narrowing anywhere. So *if* you are willing to run Stage 9 at `INT`, the re-band approach
**does** satisfy requirement 4 numerically. That is the honest steel-man.

**9.4 — Shape ceiling: fails at every ramp, and fails immediately.** `(g & 3)` fixes
family shape at {live, speed, tb, spare}. It cannot express:
- a family with **no** Speed variant — and Renju is already this shape in practice
  (`map-android.md` #5: `Table.gameNames` lists 31 = Renju "w/ no Speed pair";
  `../ios-game-id-touchpoints.md` #24: `ArenaTableSetupView.swift:257` computes rows as
  `count / 2` and truncates when the count is odd);
- a live-only or TB-only game;
- more than one speed tier;
- two variants sharing a rating pool.

Every such family burns a full quad, leaving ids that `(g & 3)` classifies as
*valid-but-nonexistent* — reproducing the phantom band that Stage 2 was written to
eliminate. **This is the approach failing on its own terms**, and it is the reason for the
§0.5 recommendation.

**9.5 — Operational ceiling: fails at every ramp, and this is the real cost.** Re-banding
changes **zero** of the ~200 client touchpoints. Adding family #17 still costs, at
minimum: 7 Android lists (`Table.gameNames` + 6 `strings.xml` arrays), 4 iOS maps, 2
React registries × 4 tables each, `gameConstants.jspf` + `boardCommon.js` +
`gameScript.js` + `mobileGame.jsp`, and ~9 Java OR-chains. Requirement 4 is about *adding
a game*, and adding a game remains an O(15 hand-edited lists) operation. Stage 5
(descriptors) attacks the Java third of that; nothing in this plan attacks the client
two-thirds, by design (YAGNI — see §11).

**9.6 — It is not cheaper where it matters.** Stages 0–3 are mandatory for *any* approach
that allocates an id ≥ 49. The re-band's entire incremental saving over explicit
allocation is that six helpers plus `registerGame` keep a one-line arithmetic fork instead
of reading a descriptor field — roughly one stage, and that stage (5) is one you probably
want anyway.

**9.7 — The one genuine, non-obvious argument in its favour: debuggability.** With
`(g & 3)` you can read `game: 98` in a log line, or in one of the ~30 raw magic numbers in
`tb/gameScript.js` / `tb/mobileGame.jsp`, and immediately know it is the TB variant of
family 96. In a system with 15 hand-copied id lists across 6 platforms and no shared
source of truth, that is worth more than it sounds. **The compromise in §11 keeps it.**

## 10. Silent-rollout verdict — grounded in the mapped unknown-id behaviours

**Server-side: fully silent through Stage 6.** Two independent gates (the `visible` flag,
and the absent `dsg_server_game` row) mean no client of any kind can see or reach the new
id. No wire change, no id change, no DB change on Ramp A.

**Web + React: silent is achievable but requires the throw-fix.** `STANDARD_GAME_IDS`
(`boardGeometry.js:51`) is a safe gate — an unlisted id simply never appears in the picker.
But a browser holding a **cached older bundle** that is pushed a new id via
`DSGChangeStateTableEvent` hits `variantKey`'s unconditional `return 'swap2-keryo'` and
renders a fully playable board running the **Keryo capture engine**, with no crash and no
console warning (`map-react.md` §1E). That is worse than a crash. Convert it to a throw in
Stage 7.

**Mobile: no, not silently — unless Stage 1 ships first.** This is the honest answer.
- Android `Table.shouldTimerRun()` (`Table.java:165-177`) does `gameNames.get(game).contains(...)`
  with no null check — a **confirmed NPE** on any unrecognized id on a timed table with no
  moves yet.
- iOS `Table.gameNames[game]!` is force-unwrapped at `HelperClasses.swift:135` and `:624`
  — traps the app.
- Neither app has any forward-compatible fallback to lean on
  (`map-android.md`: "there is no forward-compatible fallback path already in the code").
- The apps *cannot create* an unknown id (spinner/picker arithmetic bounds them), so the
  exposure is entirely **inbound**: another client creates a table with the new id in a
  room the old app is in, and the old app lists or opens it.

**Therefore**: a forced app update is **not** required for the refactor, **not** required
for existing games, and **not** required for web rollout. It *is* required before new
games become visible in any room an old mobile client occupies — **unless you spend one
small, invisible mobile release (Stage 1) first**, after which every future game ships
silently to mobile as an "unnamed generic game" until the user updates. That inversion —
pay one adoption wait once, then never again — is the single highest-leverage scheduling
decision in the plan, and it is why Stage 1 is dispatched at t=0 rather than at t=late.

**The leak the visibility flag does NOT close** — and this is the most important caveat
in this section: `MySQLDSGPlayerStorer` loads *every* rating row a player has, whatever
the id (`map-java-db-wire.md` §C5, described as "the one genuinely id-agnostic read path
in the whole layer"), and those become `DSGPlayerGameData` sent to the client on login.
So the moment anyone plays the new game, its id reaches **every** client in a ratings
payload, regardless of room gating. Android's `RatingStat` is a plain `int` and unused
entries appear inert; iOS's rendering of an unknown-id rating entry, and
`ArenaJoinRequestAdapter.java:36-127`'s `player.getRating(gameId)` map lookup, are
**[OPEN] — not verified null-safe**. Verify both before Stage 6, not before Stage 8.

**[OPEN] — the room-gating question.** Whether a dedicated server/room is a real gate
depends on whether old mobile clients enumerate servers from `LiveServersResponse` and
would join a new one. The maps do not establish this. If they would, then "put the new
game on its own server id" is *not* a gate for mobile and Stage 1 is the only defence.

## 11. Recommendation

Run Stages 0–4 exactly as specified. They are the honest floor of the effort, they are
unavoidable under every alternative design, and every one of them is player-invisible and
cleanly reversible.

At Stage 4, make one substitution:

> Keep the **quad allocation convention** (base = 96 + 4k; speed = base+1; tb = base+2)
> so ids stay eyeball-decodable in logs and in the ~30 raw magic numbers scattered through
> `tb/gameScript.js` and `tb/mobileGame.jsp` — but derive `isSpeed` / `isTurnbased` /
> `baseFamilyId` from **nullable descriptor fields** (`speedId`, `tbId`, `baseId`), not
> from `(g & 3)`.

Same diff size (~60 lines in one file either way). Same debuggability. But a family may
then omit its Speed or TB slot — which Renju already needs, which iOS's `count / 2` picker
already breaks on, and which `(g & 3)` can never express. That single substitution is the
difference between moving the ceiling and removing it.

If you disagree and want the pure arithmetic form, it is defensible: take Ramp A, ship 40
families, and revisit in 2075. Just do it knowing that Renju's missing Speed twin is
already the counter-example sitting in the codebase today.

## 12. Open verification items

1. **[OPEN]** Is `DSGPlayerGameData` for an unknown game id safe on iOS, and is
   `ArenaJoinRequestAdapter.java:36-127`'s `player.getRating(gameId)` null-safe on Android?
   This is the leak path the visibility flag does not close. Blocks Stage 6.
2. **[OPEN]** Do old mobile clients enumerate servers from `LiveServersResponse` and join
   new ones? Determines whether room-scoping is a real gate. Blocks Stage 8 planning.
3. **[OPEN]** Android `TableListAdapter.java` and `LiveTableFragment.java` were not
   re-verified in the recon; likely share `shouldTimerRun`'s unchecked-`.get()` pattern.
   Blocks Stage 1.
4. **[OPEN]** Live value(s) of `game_event.name` for KOTH — three spellings are in play
   (`"King of Hill"` in `MySQLGameVenueStorer`, `"King Hill"` in the `MySQLKOTHStorer`
   query, `"King of the Hill"` reported in the live DB). `eid` resolution is exact-match.
   Blocks Stage 3.
5. **[OPEN]** Row counts and rebuild time for `dsg_player_game` / `dsg_server_game`
   (MyISAM, `game` in PK). Blocks Stage 9 scheduling.
6. **[OPEN]** Does the client↔server protocol carry a client version usable for
   server-side per-client filtering? If yes, Stage 8's gate can be automatic.
7. **[OPEN]** `MySQLGameVenueStorer.registerGame` line numbers were not pinned in the
   recon (the `+1`/`+50` inline derivation is described but not cited to a line). Confirm
   before Stage 3.

## 13. Residual risks

1. Ratings payloads leak new game ids to all clients regardless of room gating (§10);
   iOS/Android handling unverified.
2. Old *web* clients exist too: a cached React bundle silently plays the wrong ruleset via
   `boardGeometry.js:41` until Stage 7's throw-fix ships and caches turn over.
3. `MySQLPenteGameStorer.java:785,1071` and `MySQLWebDbStorer.java:420` synthesise
   `moves[0]` from `getCenterMove(game)` — a new off-centre-opening variant silently
   corrupts every archived and collected game unless `firstMoveCanBeOffCenter` is wired
   correctly for it.
4. `CacheKOTHStorer` guards `== 0` while `MySQLKOTHStorer.getEventId` returns `-1` — an
   unregistered new game writes hills under `koth_id = -1`. Fixed in Stage 3; if that fix
   slips, Stage 6 corrupts the KOTH table.
5. `MySQLGameStorerSearcher.java:400` bounds search by `TB_RENJU` (81) — new TB games are
   silently absent from the game database until Stage 4's membership conversion lands.
6. The poison band (33..50) and reserved band (82..95) are conventions, not enforced
   constraints, unless Stage 4 adds an assertion. Without one, a future developer
   re-creates the phantom-band bug.
7. Stage 9's rollback becomes destructive the moment a game is played on a family above
   base 252 — sequence strictly, and gate allocation on the observed column type.
8. Stage 5's "preserve every divergence verbatim" is deliberate but leaves the already-live
   5-way `firstMoveCanBeOffCenter` disagreement in production. That is a conscious trade
   (invisibility now, correctness later), not an oversight — but it must be tracked, or it
   will be forgotten.
9. Legacy AWT/web applet clients (`GameBoardFrame.java:939`, `GameBoard.java:226`) are
   deliberately not extended to gen-2. If they still have users, those users see the new
   game omitted rather than broken — confirm that is acceptable.

