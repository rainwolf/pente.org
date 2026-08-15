# Game-ID Touchpoint Inventory — React frontends + Android

## Part 1 — `react_live_game_room/` (fully verified this session)

### The good precedent: `src/game/boardGeometry.js`

This module is direct, in-repo evidence that this exact class of refactor has **already been
attempted once, successfully, on this exact codebase**. Its own header comment states the
motivation verbatim (paraphrased from source): id-range/board-size logic used to be scattered
across `Board.js`'s render method, duplicated in `GameClass.setGame()`, and duplicated again
in `TableClass`'s variant partition; this module concentrates it in one place. That comment
also **self-documents its own incompleteness**: it says explicitly this is not the only place
game-id logic lives — `GameClass`'s per-variant replay dispatch and a `Utils.game_name`-style
helper keep their own coarser/finer groupings "intentionally left out."

| # | Location | What |
|---|---|---|
| 1 | `src/game/boardGeometry.js` `variantKey(gameId)` | Range-chained `if (gameId < X)` ladder (not `===` tables), ending in an **unbounded final `else`** that returns `'swap2-keryo'` for anything not caught earlier — same shape as iOS's `gameColor()` unbounded-else bug already flagged: any future id ≥ 29 that isn't explicitly `=== 31`, `=== 32`, or `=== 81` silently becomes `'swap2-keryo'` |
| 2 | `src/game/boardGeometry.js` `gridSizeForGame(gameId)` | `21/22→9`, `23/24→13`, `31/32/81→15`, `else→19` — confirms Renju is a distinct 15x15 board and that `TB_RENJU`(81) is explicitly folded in here (the one raw-id family this module treats as TB-aware; others are not) |
| 3 | `src/game/boardGeometry.js` `isGoBoard(gameId)` | `gameId > 18 && gameId < 25` — **bounded** range check, the safe pattern (mirrors iOS's bounded `isGo()`) |
| 4 | `src/game/boardGeometry.js` `STANDARD_GAME_IDS` | Explicit array literal of the 16 odd base ids `[1,3,5,...,31]` for the variant picker — avoids `%2`/arithmetic derivation, an improvement over positional/arithmetic approaches seen elsewhere |

### The gaps `boardGeometry.js` itself admits to

| # | Location | What |
|---|---|---|
| 5 | `src/game/GameClass.js:118` | `game===3\|\|game===4\|\|game===17\|\|game===18\|\|game===25\|\|game===26` — raw magic-number KERYO/DKERYO/O-PENTE family check, **not** routed through `boardGeometry.js` despite that module existing in the same package |
| 6 | `src/game/GameClass.js` `#isDPente()` | `game===7\|\|game===8\|\|game===17\|\|game===18` — private, raw magic numbers |
| 7 | `src/game/GameClass.js` `isConnect6()` | `game===13\|\|game===14` — raw magic numbers |
| 8 | `src/game/GameClass.js` `#isSwap2()` | `game===27\|\|game===28\|\|game===29\|\|game===30` — raw magic numbers |
| 9 | `src/game/GameClass.js` `isRenjuGame()` | `game===31\|\|game===32\|\|game===81` — raw magic numbers, but notably **is** TB-aware (includes 81), unlike #6-8 which have no TB counterpart listed at all — same asymmetric TB-awareness pattern seen in the JSP layer |
| 10 | `src/components/SettingsModal.js:119` | `table.game % 2 === 0 ? table.game - 1 : table.game` — parity-based speed-to-base normalization for a settings dropdown's current-value selection; an independent reimplementation of the base/speed pairing arithmetic that exists nowhere else as a shared helper |

## Part 2 — `react_mmai/` (from this session's earlier verification pass, not re-walked after
compaction; included for completeness, flagged as slightly lower-confidence on exact line
numbers)

| # | Location | What |
|---|---|---|
| 11 | `react_mmai/src/game/GameClass.js` `setGame()` | Raw threshold ladder: `game<21→19`, `game<23→9`, `game<25→13`, `else→19` for board size — **no shared helper**; the `boardGeometry.js` consolidation done in `react_live_game_room` was never ported here, so this is a fully independent, unconsolidated duplicate of touchpoint #2 |
| 12 | `react_mmai/src/game/GameClass.js` | Same `{3,4,17,18,25,26}` critical-captures magic-number list as touchpoint #5, duplicated a second, independent time in a sibling package |

`react_mmai` is AI-vs-human live-only (no TB ids ever reach it), so it has no TB-awareness
gaps to report — but it also has zero indirection against any future renumbering of the live
id range, since it was never wired into `boardGeometry.js`.

## Part 3 — Android (`pentelive-android/`)

Verified this session via targeted grep + earlier-session recon; both corroborate.

### Existing prior-art: a migration is already mid-flight on this exact axis

| # | Location | What |
|---|---|---|
| 13 | `.../rules/VariantPredicateEquivalenceTest.java` | A **real, already-existing regression test**. Docblock (paraphrased): "Guard test for Task 8 (Variants-routing refactor). Pins the equivalence between legacy predicate logic and the Variants-based routing BEFORE any code is changed." This is direct proof that a `Variant`/`Variants` abstraction already exists as a partial migration target, mid-flight, under an already-tracked "Task 8." Directly relevant prior art for scoping the cross-platform refactor this investigation supports. |
| 14 | Same test, docblock | Explicitly documents a **known, intentional divergence**: `isSwap2()` "DIVERGES on 'Speed Swap2-*' strings; NOT rerouted" — i.e. the migration author already found a real behavioral mismatch between the legacy string-matching logic and the new `Variants` registry for swap2, and chose to leave `isSwap2()` on the legacy path rather than silently unify it. This is exactly the kind of latent semantic gap a naive automated refactor would paper over. |
| 15 | Same test, docblock | Notes `Table.java`'s three id-based predicates **are** fully equivalent and have been rerouted to `Variants`; only `Game.java`'s string-based predicate set is partially migrated, `isSwap2()` held back per #14 |
| 16 | `.../model/Game.java` | Legacy DTO: `isConnect6()`, `isGomoku()`, `isDPente()`, `isSwap2()`, `isGo()` — all dispatch off a **display-name string** (`gameType`), not a numeric id. This is a structural twin of iOS's `BoardVariantMapping` "string-content matching" identity scheme — decoupled from numeric ids, and therefore invisible to any numeric-id renumbering, but silently broken by any *display name* wording change instead |

### Picker-position-as-identity anti-pattern (cross-checked against iOS's `row*2+1`)

| # | Location | What |
|---|---|---|
| 17 | `InvitationActivity.java` | `gameTypeSpinner` — persists `getSelectedItemPosition()` (the spinner's **index**, not the game id) into `SharedPreferences` via `PrefUtils.saveIntToPrefs(...)` |
| 18 | `InviteAIActivity.java` | Same pattern: spinner selection **position**, not id, persisted |
| 19 | `SocialActivity.java` | Persists a game **name string** (e.g. "Turn-based Pente" as the default) via `PrefUtils`, rather than an id or position — a fourth distinct identity scheme, local to this one screen |

Touchpoints #17-18 are the Android-side instance of the exact same fragility class already
flagged on iOS (`row*2+1` arithmetic over a picker's row index) and implicitly present in
every HTML `<select>`/dropdown across the JSP layer: whichever code populates the option list,
in whatever order, is the de facto source of truth for what a persisted integer *means*. If
that population order or membership ever changes, a previously-saved preference silently
refers to a different game type on next launch — with no error, no validation, and no
migration path. This is a **generalizable, cross-platform finding**, not an Android-specific
one: it shows up in iOS pickers, Android spinners, and (implicitly, not separately catalogued
here) any web `<select>` whose `<option>` order matters to a saved value.

| # | Location | What |
|---|---|---|
| 20 | `BoardActivity.java` | `game.isRenju()`, `game.isSwap2()` — consumer-side call sites; delegate out to `Game.java`, do not duplicate raw ids locally (the good pattern, contrast with React's `GameClass.js`) |

## Touchpoint count (this file)

**20 distinct touchpoints** (#1-20): 10 in `react_live_game_room`, 2 in `react_mmai`, 8 in
Android.
