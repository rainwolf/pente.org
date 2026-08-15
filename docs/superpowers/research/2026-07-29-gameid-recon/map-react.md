# Game-ID touchpoint inventory — React apps (react_mmai + react_live_game_room)

Scope: `react_mmai/` (AI-opponent practice app, branch `main`) and `react_live_game_room/`
(underscored standalone clone, branch `main`). `react_mmai/MMAIWASM/Ai.cpp` (the C++ WASM
engine shipped *inside* react_mmai's bundle) is included since it is the client-side game-rules
authority for that app and hardcodes the same partition independently.

Legend: file:line — snippet — meaning — refactor note.

---

## PART 1 — react_live_game_room (lgr)

### 1A. Hardcoded ids / id→something maps (pattern class a)

- `src/game/boardGeometry.js:17` — `if (gameId === 21 || gameId === 22) return 9;` — Go 9x9 board size for ids 21/22 — refactor: fold into a generic {id: gridSize} map or a variant-registry lookup so new sizes don't need new `if` arms.
- `src/game/boardGeometry.js:18` — `if (gameId === 23 || gameId === 24) return 13;` — Go 13x13 board size — same note.
- `src/game/boardGeometry.js:19` — `if (gameId === 31 || gameId === 32 || gameId === 81) return 15;` — Renju board size 15x15, and the ONLY place in this file that already includes a TB id (81) alongside its live pair (31/32) — proves the live client does render/replay TB-origin renju games (spectate/replay path), so any new game's TB id must be added here too, not just its live pair.
- `src/game/boardGeometry.js:27-41` (`variantKey`) — 12-branch dense range chain (`gameId < 3` → 'pente', `< 5` → 'keryo-pente', … `< 29` → 'swap2-pente') then an explicit id-equality check for renju, THEN an unconditional fallback. This is the single most load-bearing id-partition in the whole app: it drives CSS class, display name, lobby color, capture-eligibility, and (indirectly) which move/replay ENGINE is used. — refactor: replace with an explicit registry keyed by id (or id-range descriptor objects) with an EXPLICIT unknown case, not a silent last-branch fallback.
- `src/game/boardGeometry.js:40` — `if (gameId === 31 || gameId === 32 || gameId === 81) return 'renju';` — canonical renju id triple, hardcoded again (3rd occurrence in this file alone after L19).
- `src/game/boardGeometry.js:41` — `return 'swap2-keryo';` — **the silent default** every unrecognized id (new games, ids 33-80, 82+) falls into. See §1E.
- `src/game/boardGeometry.js:45` — `export const boardStyleClass = variantKey;` — board's CSS class IS the variant key; inherits the same fallback risk.
- `src/game/boardGeometry.js:51` — `export const STANDARD_GAME_IDS = [1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31];` — the ONE array every game-picker dropdown iterates (comment confirms this is intentional single-sourcing). A new variant's id is simply invisible in the UI until manually appended here — this is the SAFE failure mode (§1E) but still a required manual edit.
- `src/game/boardGeometry.js:56` — `export function isGoBoard(gameId) { return gameId > 18 && gameId < 25; }` — range check, reused by GameClass.isGo and TableClass.gameIsGo/player_color.
- `src/game/boardGeometry.js:59` — `const CIRCLES = [120, 126, 180, 234, 240];` — board-index positions (not game ids) for the non-Go decorative circle markers on a 19x19 grid; hardcoded per grid geometry, unrelated to id width but co-located with id logic.
- `src/game/boardGeometry.js:62-68` (`GO_DOTS`) — object keyed by **grid size** {19:[…], 9:[…], 13:[…]}, not by game id directly (indirect via `gridSizeForGame`).
- `src/game/boardGeometry.js:71` (`boardSpecialPoints`) — `if (gameId === 31 || gameId === 32 || gameId === 81) { … }` — renju star-point layout, 4th hardcoded occurrence of the 31/32/81 triple in this one file.
- `src/game/boardGeometry.js:79` — `return GO_DOTS[gridSizeForGame(gameId)].map(...)` — indirect id→geometry via grid size.
- `src/Classes/utils.js:7-21` (`VARIANT_NAMES`) — 13-entry map keyed by the `variantKey` STRING (indirect on id, but exhaustiveness still tracks the same partition 1:1 — a 14th variant key with no name entry renders `undefined`).
- `src/Classes/utils.js:23-27` (`GO_NAMES`) — `{19: 'Go', 9: 'Go (9x9)', 13: 'Go (13x13)'}` keyed by grid size.
- `src/Classes/utils.js:29-33` (`game_name`) — looks up `variantKey(g)` then `GO_NAMES`/`VARIANT_NAMES`; inherits the swap2-keryo fallback for unknown ids (§1E).
- `src/Classes/TableClass.js:10-22` (`VARIANT_COLORS`) — 13-entry lobby-card-color map keyed by `variantKey` string — same indirect-but-exhaustive risk as VARIANT_NAMES.
- `src/Classes/TableClass.js:75` (`player_color`) — `if (isGoBoard(this.game)) { p = 3 - p; }` — Go-specific stone-color inversion gated by the id-range helper.
- `src/Classes/TableClass.js:100` — `table_color = () => VARIANT_COLORS[variantKey(this.game)];`
- `src/Classes/TableClass.js:129` — `gameIsGo = () => isGoBoard(this.game);`
- `src/Classes/TableClass.js:132` — `if (this.gameIsGo()) { seat = 3 - seat; }` (colorForSeat).
- `src/Classes/TableClass.js:142` — `gameHasCaptures = () => variantKey(this.game) !== 'gomoku';` — capture-panel visibility keyed off the SAME shared partition (good: single source; risk: gomoku is the only excluded key, so a brand-new no-capture variant would show a capture panel it doesn't need, unless its variantKey happens to also be 'gomoku').
- `src/Classes/GameClass.js:28-42` (`VARIANT_RULES`) — 13-entry table keyed by `variantKey` string; `{replay, disableRatedOnReplay, add, goMove, player, postRule}` — this is THE move-application/replay engine dispatch. Every entry is reached only via `variantKey(this.game)`, so it inherits the swap2-keryo fallback (§1E) — a genuinely new id would replay using the **keryo capture-triple engine**, not whatever its real rules are.
- `src/Classes/GameClass.js:118` — `if (this.game === 3 || this.game === 4 || this.game === 17 || this.game === 18 || this.game === 25 || this.game === 26) { threshold = 11; }` (`critical_captures`) — explicit 6-id enumeration (keryo-pente, dk-pente, o-pente pairs) completely independent of `variantKey`/VARIANT_RULES — a SECOND, uncoordinated id partition inside the same file.
- `src/Classes/GameClass.js:172-175` (`setGame`) — `this.gridSize = gridSizeForGame(game);` — clean delegate, no local duplication (contrast with react_mmai, §2).
- `src/Classes/GameClass.js:219` — `else if (this.isGo() || this.isRenjuGame())` — Go and Renju share the pass-move sentinel (`gridSize*gridSize`); comment explicitly flags the shared-sentinel coupling.
- `src/Classes/GameClass.js:285` — `#isDPente = () => this.game === 7 || this.game === 8 || this.game === 17 || this.game === 18;` — d-pente/dk-pente pair enumeration, a THIRD independent id partition (disagrees in shape with `variantKey`'s range chain, which treats 7-8 and 17-18 as different variants — d-pente vs dk-pente — while `#isDPente` groups them together for opening-phase purposes only).
- `src/Classes/GameClass.js:288` — `isConnect6 = () => this.game === 13 || this.game === 14;`
- `src/Classes/GameClass.js:290` — `isGo = () => isGoBoard(this.game);` (delegates, clean).
- `src/Classes/GameClass.js:292` — `#isSwap2 = () => this.game === 27 || this.game === 28 || this.game === 29 || this.game === 30;` — groups swap2-pente AND swap2-keryo (two different `variantKey`s) into one opening-phase gate — a FOURTH independent partition, coarser than `variantKey`.
- `src/Classes/GameClass.js:297` — `isRenjuGame = () => this.game === 31 || this.game === 32 || this.game === 81;` — the canonical, most-copied renju id triple (this is the file's own 5th-ish restatement of it after boardGeometry.js's four).
- `src/Classes/GameClass.js:377,399,418` — `const rules = VARIANT_RULES[variantKey(this.game)];` — three call sites (replayGame / addMoveFromList / addMove) all keyed off the single shared partition — the good half of the design (one lookup, not three separate range chains) but all three inherit the same fallback risk in one shot.

### 1B. Id arithmetic (pattern class b)

- `src/Classes/utils.js:32` — `return g % 2 === 0 ? 'Speed ' + base : base;` — the **parity = speed-pairing** rule, expressed as a display-name suffix. This is the cleanest/only place lgr encodes "even id = Speed twin of the preceding odd id" as arithmetic (everywhere else the pairing is baked into the range-chain literals, e.g. `gameId < 5` swallowing both 3 and 4).
- `src/Components/Table/SettingsModal.js:119` — `value={table.game % 2 === 0 ? table.game - 1 : table.game}` — normalizes the table's current (possibly Speed/even) id down to its odd base BEFORE looking it up in the odds-only `STANDARD_GAME_IDS` picker — i.e., explicit `-1`-if-even arithmetic, the mirror image of react_mmai's `this.game - 1` pattern (§2B) but done at the UI layer instead of inside GameClass.
- `src/game/openingPhase.js:131` — `const lastColor = ((n - 1) % 2) + 1;` — move-count parity (not game-id parity) for swap2/d-pente opening color; included because it's on the same "% 2" axis a refactor touching game-id parity might grep-match and should NOT change.
- `src/game/openingPhase.js:139` — `return (n % 2) + 1;` — same caveat.
- `src/Classes/GameClass.js` (many lines, e.g. 235, 260, 403-404, 422-423) — `1 + (this.moves.length % 2)`, `2 - (this.moves.length % 2)`, `(this.moves.length % 4)` — MOVE-COUNT parity for turn/color alternation, not game-ID parity. Flagged as a false-positive class for any grep-based refactor sweep on `% 2`.

### 1C. Width / wire-encoding constraints (pattern class c)

- `src/protocol/messages.js:28` — `dsgChangeStateTableEvent: { …, out: [..., 'game', 'table'] }` — `game` travels as a bare field name in a JSON object built by `Commands.changeState(...)`; **no declared type, no byte/short packing** — it's whatever JS number `table.game` holds, serialized by `JSON.stringify` over the WebSocket text frame.
- `src/protocol/messages.js:68` — `dsgArenaCreateTableEvent: { …, out: [..., 'game', 'playAs', ...] }` — same: `game` is a plain JSON field for arena-table creation.
- `src/protocol/decode.js:36-46` — inbound decode only checks a message's **required field names** are present (`field in payload`); it does NOT range- or type-check `game`'s value. Any numeric (or even non-numeric) `game` the server sends is passed through untouched into the reducer.
- **Finding for (c):** no id-width cap exists anywhere in this app's transport layer — it's JSON-over-WebSocket, so the practical ceiling is JS's safe-integer range, not a byte/short. Any width constraint on game id is a backend/DB/mobile-wire concern outside this subsystem.

### 1D. Validation / enumeration (pattern class d)

- `src/game/boardGeometry.js:51` — `STANDARD_GAME_IDS` (see §1A) — the sole enumeration source for game **creation** UI (`SettingsModal.js:128`). No `isValidGame`/`getMaxGameId` equivalent exists anywhere in this app.
- `src/Components/Table/SettingsModal.js:128-129` — `{STANDARD_GAME_IDS.map(game => <MenuItem key={game} value={game}>{table.game_name(game)}</MenuItem>)}` — the live game-picker dropdown for changing an existing table's settings; single-sourced off §1A's array (good), but still requires a manual array edit + a `VARIANT_NAMES`/`VARIANT_COLORS`/`VARIANT_RULES` edit for a new game to be selectable and correctly rendered.
- `src/redux_reducers/utils.js` (`changeState`-style table-update helper, ~L70-76) — `game.setGame(tableState.game);` — the reducer path that applies a server-pushed game id to client state performs **zero validation**; whatever the server sends is trusted and handed straight to `variantKey`'s fallback chain.
- No client-side `isValidGame`/`getMaxGameId` function exists in lgr — validation is implicit and total: `variantKey` never throws, it always returns *some* string (§1E).

### 1E. UNKNOWN-ID BEHAVIOR — react_live_game_room

Exact path: an id the app doesn't recognize (any value other than the ranges/equalities in
`boardGeometry.js:28-40`) falls through **`boardGeometry.js:41: return 'swap2-keryo';`**. This
single line is the fork point for everything downstream:
- `boardStyleClass` (=`variantKey`) → wrong CSS class ('swap2-keryo') applied to the board.
- `utils.js:31` `VARIANT_NAMES['swap2-keryo']` → displays "Swap2-Keryo" (or "Speed Swap2-Keryo") as the game name — wrong, but a real, non-crashing string.
- `TableClass.js:100` `VARIANT_COLORS['swap2-keryo']` → wrong lobby-card color, non-crashing.
- `GameClass.js:377/399/418` `VARIANT_RULES['swap2-keryo']` = `{replay:'keryo', add:'keryo', postRule:'none'}` → **moves are replayed/applied using the Keryo-Pente capture-triple engine** regardless of what the new game's actual rules are. This is silent WRONG GAMEPLAY, not a display glitch: captures may fire when they shouldn't (or vice versa), and win detection runs against the wrong rule set.
- Meanwhile the SEPARATE per-id gates (`#isDPente`, `#isSwap2`, `isConnect6`, `isRenjuGame` — §1A) all evaluate `false` for a genuinely new id, so `currentPlayer()`/`currentColor()` fall through to the generic `1 + (moves.length % 2)` alternation — internally consistent with "no special opening," but now DISAGREEING with `VARIANT_RULES`'s choice of engine (which assumed swap2-keryo's opening-less "postRule: none" but the wrong capture rules). Net effect: **the board renders and is playable, but silently applies the wrong game's rules — no crash, no error, no console warning.**
- The one SAFE unknown-id path is `STANDARD_GAME_IDS` (§1D): a new id simply never appears in the create/settings picker, so a player cannot *start* a game with an unknown id through the UI. The danger is entirely in **rendering/replaying a game whose id was created some other way** (server push, direct URL/table-id join, spectating) — the client never validates the id the server hands it.
- `gridSizeForGame` and `isGoBoard` both fail SAFE for unknown ids (return 19 / false respectively — confirmed by `src/game/__tests__/boardGeometry.test.js:46-49` and `:80-82`, which explicitly test and lock in "no crash" default behavior). Only the *variant/rules* partition (`variantKey`) fails UNSAFE (wrong-but-plausible).

---

## PART 2 — react_mmai

Scope note: react_mmai supports far fewer variants than react_live_game_room (no
Renju/Swap2/DK-Pente-choice at all — grep-confirmed absence). Its id-classification logic is
NOT consolidated into one seam (unlike lgr's `boardGeometry.js`); the same id-range knowledge
is independently re-encoded in at least 4 separate places (2A), each with its own bugs, plus a
5th classification axis compiled into WASM (2F). This fragmentation is itself a risk finding.

### 2A. Hardcoded ids / id-keyed maps

- `src/Classes/GameClass.js:82-90` `setGame()` — grid-size chosen by raw numeric-range
  `if (game < 21) 19; else if (game < 23) 9; else if (game < 25) 13; else 19;`. Same
  range-chain-with-catch-all shape as lgr's `gridSizeForGame`, but duplicated/independent code.
- `src/Classes/GameClass.js:94-96` `critical_captures()` — explicit id-literal list
  `this.game === 3 || 4 || 17 || 18 || 25 || 26` (threshold 11 vs default 7). Hardcoded ids, not
  range- or base-derived; any future variant needing threshold 11 must be added here by hand.
- `src/Classes/GameClass.js:103-139` `game_name(g)` — 12-branch range chain (Pente..O-Pente,
  ids 1-26) with **no final `else`**; `let name;` is left `undefined` for any `g >= 27`. Line
  135 `g % 2 === 0 ? 'Speed '+name : name` means: unknown EVEN id -> literal string
  `"Speed undefined"`; unknown ODD id >=27 -> the actual JS value `undefined` (not a string) is
  returned. (Correction vs. earlier draft: only the even/Speed branch yields the literal text
  "undefined"; the odd branch returns real `undefined`, which React silently renders as nothing
  and which would throw if any caller does `.toUpperCase()`/string-concat on it without a guard.)
- `src/Classes/GameClass.js:141-143` `gameHasCaptures()` — `this.game < 5 || this.game > 6`.
  Note this is NOT id-enumerated like lgr's `variantKey !== 'gomoku'`; it's a bare numeric
  range that returns `true` for literally any id > 6, including all future/unknown ids. Fails
  safe-ish (over-inclusive) rather than crashing, but is inconsistent with `game_name`'s
  narrower recognized range.
- `src/Classes/GameClass.js:145-206` `isGameOver()` — capture/row win detection keyed to
  `this.game === 1`, `this.game === 3`, or `base` (line 153: `(game%2===0) ? game-1 : game`)
  `=== 11 | 15 | 25`. **No branch for any other id, and no final `else`/default win-check.**
  See 2E — this is the single highest-severity unknown-id finding in the whole recon.
- `src/Classes/GameClass.js:470,473,476` `#isDPente()`, `isConnect6()`, `isGo()` — private
  per-variant boolean gates (grep-confirmed present); independently maintained from the
  `game_name`/`setGame`/`isGameOver` range chains above, same "parallel un-unified
  classification axes" pattern flagged in lgr's Part 1.
- `src/Board/Board.js:12` `mapStateToProps`: `game_id: state.game.game` — no `table` object in
  mmai at all (unlike lgr); the AI-player app only ever has a single local `Game` instance, no
  server-authoritative table-state id to cross-check against.
- `src/Board/Board.js:148-170` — a **third independent** range chain for CSS board style,
  covering `game_id < 3..< 25` (Pente..Go) with an explicit trailing `else { style = 'o-pente' }`
  (line 168-169) for any `game_id >= 25`, i.e. every unknown id >=25 is silently painted as
  O-Pente's board skin.
- `src/Board/Board.js:171-173` — grid size: default 19, `21|22 -> 9`, `23|24 -> 13` — a
  **fourth** independent copy of the same size-partition logic already in `GameClass.js:82-90`.
- `src/Board/Board.js:106-115` — Go-dots/circles block: `if (game_id < 19 || game_id > 24)`
  use generic circles, `else` pick dot layout by `19/20`, `21/22`, `23/24` — safe fail (circles)
  for ids outside the Go range, including any future id.
- `src/Board/BoardSquare.js:39-51` `boardpart(size)` — switch on `part` (values 1-9, 51, 52)
  with `default: return this.bottomrightcorner(size);` (line 51). **FALSE POSITIVE**: `part` is
  a board-square-position code (corner/side/cross), not a game id; flagged here only to
  document it was checked and ruled out, mirroring the lgr false-positive list.
- `src/Pages/GameInfoPanel.js:158` — hardcoded picker whitelist `[1, 3, 11, 13, 15, 25].map(g =>
  ...)` — this IS mmai's equivalent of lgr's `STANDARD_GAME_IDS`: the literal enumeration that
  bounds which ids a human can select via the UI (Select at lines 148-161, `value={game.game}`,
  `onChange={change_game}`). This is the array the refactor's "remove the game-count cap" would
  need to stop hardcoding. It matches the Android `MMAIActivity.VARIANT_GAMES = {1,3,11,15,25,13}`
  set exactly (order differs, membership identical) — cross-platform corroboration that mmai's
  scope is deliberately fixed at exactly these 6 variants today.

### 2B. Id arithmetic

- `game % 2 === 0` (Speed-pair parity) appears independently at `GameClass.js:135` (`game_name`)
  and `GameClass.js:153` (`isGameOver`'s `base` normalization) — two separate inline
  reimplementations of the same odd/even Speed-twin rule, not shared via a helper.
- `MMAIWASM/Ai.cpp:24-26` — a **third**, C++-side reimplementation of the same normalization:
  `int base = gameId; if (base == 2) base = 3; else if (base > 2 && base % 2 == 0) base -= 1;`
  (special-cases legacy id `2` to `3` before the generic `-1` normalization). Three independent
  implementations of "even -> odd base" arithmetic across JS x2 and C++ x1, none shared.

### 2C. Wire / transport constraints

mmai has no `table`/websocket protocol layer analogous to lgr's `src/protocol/*` — it is a
single-player-vs-AI app; the only "transport" for a game id is the numeric value stored in
local Redux state (`state.game.game`, `rootReducer.js:13` `game: new Game()`), set once at
game creation via the picker (2A) and never revalidated afterward. No server round-trip
carries or could carry richer variant metadata for this app as currently built.

### 2D. Validation / enumeration

- `src/Pages/GameInfoPanel.js:158` `[1, 3, 11, 13, 15, 25]` — the sole picker whitelist (see
  2A). This is the only place a human chooses a game id in mmai; a new variant is invisible to
  the UI until added here, same "silent omission, not error" pattern as lgr's
  `STANDARD_GAME_IDS`.
- No other enumeration/validation of ids exists in the JS layer — `setGame`, `game_name`,
  `isGameOver`, and `Board.js`'s style/gridsize chains all accept **any** numeric id without
  rejecting or flagging unrecognized values; they just silently misclassify (2A/2E).
- `src/redux_saga/sagas.js:36-40` — code comment explicitly documents the intended contract:
  "the WASM engine accepts 1/3/11/15/25 (and legacy 2 = Keryo)" — i.e. the JS layer's authors
  already knew the id space is constrained by the WASM layer, but nothing in JS enforces it;
  the raw `game.game` is passed through unchecked (line 40: `Module.ccall('getAIMove', ...,
  [game.game, level, o, ...])`).

### 2E. UNKNOWN-ID BEHAVIOR — react_mmai

react_mmai has **no single unknown-id failure mode** — it has (at least) four independent ones,
layered across JS display, JS rendering, JS win-detection, and compiled WASM AI logic, each
triggered by the same raw id flowing unchecked through `state.game.game`:

1. **Display (`GameClass.js:103-139`)**: unknown id -> `game_name()` returns `undefined` (odd)
   or the string `"Speed undefined"` (even). No crash; renders as blank or literal garbage text
   in `GameInfoPanel.js:76` (`{game.game_name()}`).
2. **Board skin (`Board.js:148-170`)**: unknown id >=25 -> silently styled as `'o-pente'`. No
   crash; cosmetically wrong CSS class only (grid math is separately safe-defaulted, 2A).
3. **Win detection (`GameClass.js:145-206`, HIGHEST SEVERITY)**: unknown id (anything other than
   exactly `1`, `3`, or base `11`/`15`/`25`) matches **none** of `isGameOver()`'s branches, so
   `this.winner` is never set by capture/row logic and the function returns `false`
   unconditionally on every check. Practical effect: **a new variant's games can never end via
   capture or row win** — the human/AI could reach an objectively won position and the app would
   keep reporting the game as ongoing, silently, forever (short of resignation). This is strictly
   worse than lgr's "wrong ruleset applied" failure because it's not just wrong, it's a stuck
   game state with no error signal.
4. **AI move generation (`MMAIWASM/Ai.cpp:17-58`, `configFor`)**: unknown/unmapped `gameId`
   (explicitly documented in the code's own comment at lines 22-23: "incl. 0, negatives,
   D-Pente=5, G-Pente=7...") falls through every `case` to `default: break;` (line 54-55),
   leaving `VariantConfig c` at its default-constructed values — i.e. **the AI silently plays
   according to plain Pente's rules** for any game it doesn't recognize, regardless of the new
   variant's actual capture/win/board rules. This is compiled into the WASM binary
   (`react_mmai/MMAIWASM/mmai.cpp` exposes `getAIMove` via `EMSCRIPTEN_KEEPALIVE`, called from
   `sagas.js:40`), so unlike findings 1-3 it **cannot be hot-patched in JS** — fixing it requires
   editing `Ai.cpp` and rebuilding/re-shipping the `.wasm`/`.js` glue artifact.
   The ONE mitigating factor: the picker whitelist (2D, `GameInfoPanel.js:158`) means a human
   cannot currently *select* an unmapped id through mmai's own UI — all four failure modes are
   latent, reachable only if game-id space is widened (i.e., exactly the refactor under design)
   without also widening this whitelist and the WASM switch in lockstep.

### 2F. Where server metadata could substitute (react_mmai)

mmai has no table/server concept for its AI games at all (2C) — metadata-from-server is not
applicable to this app's core play loop the way it might be for lgr. The one server touchpoint
`GameInfoPanel.js` comments out a `table.game_name(game)` call (line 124, dead/commented code) —
faint evidence an earlier design considered a server-driven table object here, abandoned. Any
refactor giving mmai variant metadata would need a new mechanism, not a repurposed existing one.

---

## PART 3 — CROSS-CUTTING RISK FLAGS & SUMMARY

### Risk flags

1. **Four to five independent silent-fallback layers, all disagreeing.** An id the server
   starts emitting post-refactor that neither client "knows" about produces a DIFFERENT wrong
   behavior in each layer: lgr JS -> mislabeled as `swap2-keryo` ruleset (Part 1E); mmai JS
   display -> blank/`"Speed undefined"` text (2E.1); mmai JS board -> wrong CSS skin (2E.2);
   mmai JS win-detection -> **game can never end** (2E.3, worst-in-class); mmai WASM AI ->
   silently plays plain-Pente rules (2E.4). None of these raise an error or log; all are
   discoverable only by a human noticing wrong behavior mid-game.
2. **At least four independent hardcoded id whitelists must be updated in lockstep** for any
   new id to be safely usable end-to-end: lgr's `STANDARD_GAME_IDS` array (Part 1), mmai's
   `GameInfoPanel.js:158` picker array, Android's `MMAIActivity.VARIANT_GAMES` set (referenced,
   not re-verified this session), and the WASM `configFor` switch (`Ai.cpp:29-56`). A rollout
   that updates the server and only some of these silently degrades rather than erroring.
3. **No server-side variant-metadata channel exists today** to let clients avoid hardcoding.
   lgr's wire protocol (`src/protocol/messages.js`) carries `game` as a bare untyped numeric
   field with zero validation at decode time (Part 1's protocol findings) and no message type
   carries name/color/geometry. mmai has no table/server concept for AI games at all (2F).
   Any "read metadata from server instead" refactor strategy requires a NEW message/endpoint,
   not a wiring change to an existing one.
4. **Severity ranking for rollout risk, worst first**: (a) mmai `isGameOver()` — hung/unwinnable
   game state, no error, no crash — hardest to detect via QA smoke-testing since the game
   *looks* normal until the win condition silently fails to fire; (b) mmai WASM AI — plays
   wrong ruleset without any human-visible signal, could produce moves that are illegal/nonsense
   under the new variant's real rules; (c) lgr `VARIANT_RULES` misclassification (Part 1) — wrong
   move-apply/replay engine, likely to desync or misrender fairly quickly and thus more likely
   to be *noticed*; (d) both apps' pure-display bugs (wrong name/wrong CSS skin) — cosmetic,
   lowest risk.
5. **Consolidation debt**: lgr already has a single seam (`boardGeometry.js`'s `variantKey`/
   `gridSizeForGame`) that most (not all — `GameClass.js`'s private `#isDPente`/`isConnect6`
   gates and `critical_captures`'s inline id list are separate axes) downstream logic funnels
   through, making it the easier app to retrofit with server-driven metadata. mmai has NO such
   seam — `setGame`, `game_name`, `isGameOver`, and `Board.js`'s style/gridsize chains are four
   separately-maintained copies of similar range logic; a refactor here has more surface area
   to touch, not less, despite mmai supporting fewer variants overall.
6. **False-positive traps already ruled out** (do not re-investigate these as game-id logic):
   lgr `UserClass.js` id-like switch (25-39) is a KOTH crown/rank-level switch; lgr
   `openingPhase.js`'s `% 2` uses are move-count parity, not game-id parity; mmai
   `BoardSquare.js`'s `part` switch (1-9, 51, 52) is board-square-position codes.

### Summary for refactor-strategy designer (under 200 words)

Both React clients classify game ids via ad-hoc numeric range-chains with silent catch-all
fallbacks — never a thrown error or explicit "unknown variant" state. react_live_game_room is
comparatively well-consolidated (one `variantKey`/`gridSizeForGame` seam feeds most downstream
maps) but still has 2-3 independently-maintained classification axes that can disagree; unknown
ids get misclassified as `swap2-keryo`'s ruleset. react_mmai is worse: four unconsolidated
JS range-chains plus a compiled WASM switch, each independently silently-wrong for unmapped
ids — critically, its `isGameOver()` never fires for an unrecognized id, so a new-variant game
can become permanently unwinnable with zero error signal, and its WASM AI silently reverts to
plain-Pente rules (documented in the C++ source's own comments, requires a WASM rebuild to
fix, not hot-patchable). Neither app can currently take variant metadata from the server: the
wire protocol carries `game` as an untyped, unvalidated numeric field with no metadata message.
A safe rollout needs either (a) a genuinely new server-pushed metadata channel adopted by both
apps before ANY new id is emitted, or (b) coordinated updates to all four-plus hardcoded
whitelists (lgr array, mmai picker array, Android set, WASM switch) in lockstep with an
explicit reject-unknown-id guard added at each silent-fallback site enumerated above.

