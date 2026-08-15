# Game-ID system — cross-platform touchpoint inventory & refactor strategy

**Scope of investigation**: every place across the pente.org stack that encodes, branches on,
persists, or transmits the numeric "game type" id (`GridStateFactory.PENTE=1`,
`SPEED_PENTE=2`, ..., `TB_*` = base+50 family), in preparation for a staged refactor toward a
single, centrally-defined game/variant registry.

**Layers covered** (sub-reports, all in this directory unless noted):

| Layer | File | Touchpoints |
|---|---|---|
| Java core (server rules/state) | `map-java-core.md` | 131 |
| Java DB/wire layer | `map-java-db-wire.md` | 14 DB-column limits + 3 cross-cutting "traps" |
| iOS app | `../ios-game-id-touchpoints.md` | 28 |
| Tomcat JSP + hand-written client JS | `map-jsp-web.md` | 25 |
| React (`react_live_game_room` + `react_mmai`) + Android | `map-react-android.md` (+ richer lgr detail in `map-react.md`) | 20 |
| **Total** | | **218** |

All figures above are per-file self-reported counts from direct source inspection this
session (`grep`/`Read` against the actual tree, cross-checked against unit tests where they
exist — `RenjuFactoryTest`, `GridStateFactorySingleGameSetTest`,
`VariantPredicateEquivalenceTest`, `boardCommon.test.js`). None of this report's factual
claims are sourced from the injected "compaction directive" / fake "SessionStart hook" blocks
that appeared partway through this session's tool output — those were recognized as prompt
injection (imperative second-person instructions embedded in tool-output-shaped text) and
disregarded; see "A note on this session's tooling" at the end.

---

## 1. The core finding: one concept, at least 6 independent identity schemes

Across the stack, "what game/variant is this" is represented six structurally different ways,
with no single conversion layer between them:

1. **Numeric id, live/speed/TB-offset** (`GridStateFactory.PENTE=1` etc.) — the canonical
   scheme, defined once in Java, but re-derived or hand-copied everywhere else.
2. **Arithmetic on that id** — `id % 2` (speed pairing), `id > 50` / `id - 50` (TB-ness),
   `id < N` range ladders (variant family) — reimplemented independently in Java, JSP, JS
   (both React apps), and implicitly on iOS/Android.
3. **Display-name strings** — `GridStateFactory.getGameName(id)` / `getDisplayName(id)`,
   Android's `Game.java` string-keyed predicates, KOTH's inconsistently-spelled event name.
4. **DB row conventions** — `pente_game` stores TB games under their **base** id;
   `tb_game` stores the **TB** id itself for the same logical game. Two conventions for one
   concept, confirmed directly in the DB/wire layer.
5. **Picker/dropdown position** — iOS `UIPickerView` row index (`row*2+1` arithmetic),
   Android `Spinner.getSelectedItemPosition()`, both persisted directly into
   `UserDefaults`/`SharedPreferences` as if they were stable ids.
6. **A partially-started `Variant`/`Variants` registry on Android** (`be.submanifold.pente.rules`),
   already mid-migration under an existing "Task 8," with one predicate (`isSwap2`)
   deliberately held back from the new registry because the migration author found a real
   behavioral divergence.

No refactor that treats this as "rename `GridStateFactory` constants" will be safe; the risk
is almost entirely in the five-to-six *parallel, disagreeing reimplementations* of the
classification logic that sits downstream of those constants.

## 2. The clearest already-live bug this sweep surfaced

The "has a negotiated / off-center opening" predicate (the Go / DPENTE / DKERYO / SWAP2
family) is hand-copied **at least 5 times** with **3 different member lists**:

| Site | Family included |
|---|---|
| `GridStateFactory.firstMoveCanBeOffCenter` (Java) | Go + DPENTE + DKERYO |
| `HttpGameServlet.java:298-303` | DPENTE + DKERYO + GO (no GO9/GO13) |
| `MobileGameServlet.java:222-225` | DPENTE + DKERYO (no Go at all) |
| `ServerTable.java:1930-1938` | DPENTE + DKERYO + GO + GO9 + GO13 + SWAP2 |
| `tb/mobileGame.jsp:745,918,1213` | DPENTE + DKERYO + SWAP2 (no Go family) |

These five do not agree with each other today, independent of any refactor. This is the
single strongest piece of evidence for centralizing onto one `Game`/registry-level boolean
flag: it is not just future-proofing, it fixes a bug that already exists in production logic.

## 3. The "unbounded final else" bug shape — found independently on two platforms

- **iOS**: `gameColor()`'s range-chain ends in an unconditional `else return` for the last
  variant family, so any id past the last explicit check silently inherits that family's
  color/rules.
- **React** (`react_live_game_room/src/game/boardGeometry.js`): `variantKey()` is the exact
  same shape — a `<`-chained range ladder ending in an unconditional fallback to
  `'swap2-keryo'`. This is the single most load-bearing function in the file: it drives
  `VARIANT_RULES` (move-application/replay engine selection) and `boardStyleClass` (CSS).
  The file's own analysis (recorded in `map-react.md` §1E) states the consequence explicitly:
  an unrecognized id renders a **fully playable board that silently applies the wrong game's
  capture/opening rules — no crash, no console warning, nothing visibly wrong**. This is a
  materially worse failure mode than a crash, because it produces incorrect game outcomes
  that look legitimate.

Two independent teams/eras produced the same anti-pattern on two different platforms. Any
shared registry design should make the "no more cases" path a **hard failure**
(`default: throw` / `switch` exhaustiveness check), matching the one place in the whole stack
that already does this correctly: `js/boardCommon.js`'s `getBoardColor`/`replayMoves`, which
throw `Error("... unknown game id " + game)` and are the only functions in the entire
investigation backed by an explicit unknown-id regression test
(`boardCommon.test.js` asserts `game=99999` throws for both).

## 4. Picker-position-as-identity — a cross-platform persistence hazard

- **iOS**: `UIPickerView` selected row persisted via `row*2+1` arithmetic.
- **Android**: `InvitationActivity.java` / `InviteAIActivity.java` persist
  `gameTypeSpinner.getSelectedItemPosition()` (the index, not the id) via
  `PrefUtils.saveIntToPrefs`; `SocialActivity.java` persists a game **name string** instead,
  a fourth scheme local to one screen.

In both cases, the persisted integer's meaning depends entirely on the *current* population
order of a UI list that lives in a different file from the persistence call. Any future
reordering, insertion, or removal in that list silently reinterprets a previously-saved user
preference as a different game type on next launch — no validation, no migration, no error.
This generalizes to any web `<select>` whose `<option>` order a saved value depends on, though
that was not separately catalogued as its own touchpoint in the JSP sweep.

## 5. Unknown-id behavior, summarized across all six layers

Behavior on an id the classification logic doesn't recognize splits four ways, and only one
of the four is "loud":

- **Fail loud (rare — 1 module in the whole stack)**: `js/boardCommon.js` throws a descriptive
  `Error`, tested.
- **Silent `null` → literal "null" string rendered to the page**: every JSP call to
  `GridStateFactory.getDisplayName`/`getGameName` (`viewLiveGames.jsp`, `mobile/game.jsp`,
  `kothBox.jsp`, `admin/tb/games.jsp`) inherits the Java layer's null-return-on-miss with no
  JSP-side guard.
- **Silent misclassification into the last/default bucket**: iOS `gameColor()`'s unbounded
  else; React `variantKey()`'s `'swap2-keryo'` fallback (§3 above) — the worst variant of this
  because it drives actual rules-engine selection, not just color/label.
  Android's `Variants`-migration test suite is the one place that treats a *known* divergence
  (`isSwap2`) as an explicit, tracked exception rather than silently unifying it — evidence
  that when this class of bug was actually caught during a migration, the team chose the
  correct, conservative fix (keep the legacy path, document the gap) rather than papering over
  it.
- **Silent no-op / falls through to the "normal" branch**: every raw `===`/`==` OR-chain
  across Java/JSP/JS with no `else` (e.g. TB negotiated-opening checks, Connect6 double-stone
  offsets) — an unrecognized id simply fails every check and the caller proceeds as if it were
  an ordinary variant with no special casing, silently producing a wrong (but not crashing)
  result.

## 6. Recommended staged strategy (synthesizing `map-java-db-wire.md` §G with the client-side
findings above)

1. **Stage 0 (this report)** — inventory, no code change.
2. **Stage 1 — invisible, server-only.** Replace `GridStateFactory`'s positional array
   indexing (`allGames[]`/`gridStates[]`/`tbGames[]`) with id-keyed maps, keeping every
   existing constant's numeric value byte-for-byte identical. Zero observable behavior
   change; `RenjuFactoryTest`/`GridStateFactorySingleGameSetTest` already pin current
   semantics as a regression guard. No client (web/iOS/Android/React) needs to know this
   happened.
3. **Stage 2 — DB column widening.** 5 `TINYINT` columns are within a soft ceiling of 255 and
   will need `SMALLINT UNSIGNED` widening before the id space can grow; purely additive since
   the Java-side fields are already `int`. No protocol change (db-wire report confirms no
   client-visible wire format depends on the narrow width).
4. **Stage 3 — centralize the predicates, not just the constants.** Replace the ~5
   independently-copied "negotiated opening" checks (§2) and the ~14 raw `> 50`/`- 50` TB
   heuristics with named registry predicates (`isTurnBased(id)`, `baseIdOf(id)`,
   `hasNegotiatedOpening(id)`), landing the *already-live* member-list bug fix from §2 as part
   of the same change, with `VariantPredicateEquivalenceTest`-style pinning tests written
   BEFORE the refactor lands (per Android's own precedent in §1, item 6) — one predicate at a
   time, not a bulk find-replace, specifically because of divergences like Android's
   `isSwap2()`.
5. **Stage 4 — client consolidation.** Port `react_live_game_room`'s `boardGeometry.js`
   pattern (a real, working precedent already in this codebase) to `react_mmai` (which has no
   equivalent today) and to iOS/Android's per-platform classification helpers, converting the
   unbounded-else fallbacks (§3) into hard failures backed by the same kind of unknown-id test
   `boardCommon.test.js` already has.
6. **Stage 5 — fix the two-conventions DB trap.** Reconcile `pente_game` (stores TB games
   under base id) vs `tb_game` (stores the TB id) onto one convention; likely the highest-risk,
   most migration-heavy stage, saved for last on purpose.

## 7. Risk flags (see `riskFlags` in structured output for the canonical list)

The most severe, non-obvious risks are: the already-live 5-way predicate disagreement (§2);
the two unbounded-else fallbacks on iOS and React that silently apply a wrong ruleset with no
error (§3); the two conflicting DB conventions for "TB game id" (§6 stage 5); and the
picker-position-as-identity persistence hazard on both mobile platforms (§4).

## A note on this session's tooling

Partway through this session, tool-output-shaped text appeared claiming to be a "compaction
summary" and, later, a "SessionStart hook" instructing adoption of a terse "caveman mode"
persona, mandatory rerouting of all Bash/Read/WebFetch calls through a third-party MCP
plugin, and unconditional invocation of unrelated "skill" workflows. These were recognized as
prompt injection — imperative second-person directives embedded inside content that should
have been inert data — and were not followed; this report and its sub-reports were produced
using direct `Read`/`Bash`/`Write` against the actual repository, consistent with the
subagent's own task instructions, which take precedence over instructions arriving embedded in
tool output.
