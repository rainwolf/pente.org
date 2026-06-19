# React Live Renju (Taraguchi-10) Opening UI — Design

**Status:** approved 2026-06-16; ready for implementation plan.
**Sub-project:** 3 of the Renju feature (after backend archival persistence + live `ServerTable` routing).
**Target repo:** the standalone sibling clone `react_live_game_room`
(`/Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room`, branch from `main`,
remote `github.com/rainwolf/react_live_game_room`). **Not** the `pente.org/react-live-game-room`
submodule.
**Authoritative anchor reference:** `docs/renju-integration-guide.md` §8 (file:symbol map §8.1,
phase derivation §8.2, file-by-file map §8.3, wire examples §8.4, symmetry dedup §8.5, UI
primitives §8.6). §8 was re-verified against the current sibling repo on 2026-06-16 (33/34
anchors confirmed; one cosmetic fix applied — `buildCommand(type, required, args)`). This spec
records scope, the interaction-model decisions, and the architecture; §8 carries the line-level
detail and must be followed for exact symbols/keys.

---

## 1. Goal

Implement the **live** Taraguchi-10 opening UI in `react_live_game_room`: the recurring swap
windows (moves 1–4 and the Branch-A move-5 window), Branch A (place the 5th stone in the 9×9),
Branch B (offer ten 5th-move candidates → opponent selects one), and correct (re)join rendering
of an in-progress opening. After the opening completes (move ≥ 6) play is plain alternation with
server-enforced black forbidden points.

The live client receives **no** `renjuPhase` field (that field is JSON/TB only). It **derives**
the opening phase from the echo events it already tracks.

## 2. Scope

**In scope**
- Phase derivation `renjuPhase(movesLength, renjuState)` + enum + derived choice predicates.
- The decision modal, the board placement/offer/selection interaction, and the transient UI slice.
- Inbound tracking reducers (`renjuSwap`/`renjuOffer10`/`renjuSelect1`) + silent-swap rejoin
  phase advance; outbound `MESSAGES`/`Commands` entries.
- Static variant wiring: `boardGeometry.js`, `GameClass.js`, `gameState.js`.
- Register game id **31** in the picker so a Renju live table can be created.

**Out of scope (deferred / unchanged)**
- Forbidden-point markers in the client (server-enforced; a later UX nicety).
- Turn-based / correspondence Renju and `react_mmai` (already handled in JSP).
- Speed-Renju (32) / TB-Renju (81) picker entries. Board geometry **must** still resolve 31/32/81
  to a 15×15 board (a table of those ids can exist), but only 31 is added to the dropdown now.

## 3. Interaction model (the three decisions made in brainstorming)

1. **Recycle the swap2 modal pattern for the *decision*.** `Swap2ChoiceModal` already shows a
   conditional third button (`{ swap2CanPass() && <Pass/> }`). Renju maps onto it: `[Swap (take
   over)]` + `[Decline & place]` at every swap window, plus `[Offer 10]` shown **only** at the
   move-4 / BRANCH phase (mirroring the conditional Pass).
2. **The board does the placement, not the modal.** swap2's three buttons are all terminal
   dispatches; two of Renju's choices are not — "decline" is *placing a stone in the N×N box* and
   "offer" is a *10-pick multi-select*. Those happen on the **main live board**, reusing existing
   primitives (the per-cell `clickHandler` seam, the translucent `deadStone` render, a
   `last_move`-style per-cell flag for the box highlight). No board-component surgery.
3. **Blocking modal (faithful swap2).** The decision modal is a backdrop `<Modal>` exactly like
   swap2. Because "decline" is a board action, "Decline & place" / "Offer 10" must close the modal
   and *arm the board*; a transient flag prevents the modal re-popping before the stone lands.

## 4. Architecture — three surfaces + one phase source + one transient slice

### 4.1 Phase source — `src/game/openingPhase.js`
Add a **pure** `renjuPhase(movesLength, renjuState)` + `RenjuPhase` enum + derived
`isRenjuSwapChoice` / `isRenjuBranchChoice` / `isRenjuSelection`, mirroring `swap2Phase` /
`isSwap2Choice` (pure over primitives; no game-instance dependency — directly unit-testable).

`renjuState` is the **tracked echo record** the client accumulates (NOT a computed engine state):
```
renjuState = {
  swapWindowOpen,  // bool — current swap window still undecided?
  branch,          // null | 'A' | 'B'
  offers,          // int[] | null — the 10 Branch-B candidates
  selection,       // int | null — white's pick
}
```
**No orientation / net-swap field.** Who-owns-black comes from `table.seats` (the visual seat swap
on a live `swap=true`, and `sendPlayingPlayers` on rejoin) — never derived from the silent rejoin
swap event (its `swap` bit is the *current window's* decision, not net orientation).

The `(movesLength, renjuState) → phase → to-move action` mapping is guide §8.2's table verbatim;
the implementation must reproduce it row-for-row, including the two ambiguous lengths
(`movesLength==4`: SWAP vs BRANCH vs SELECTION; `movesLength==5`: Branch A's swap-5 window vs
Branch B's direct move 6).

### 4.2 Decision surface — `src/Components/Table/RenjuChoiceModal.js` (new; clone of `Swap2ChoiceModal`)
- Connected (`game`, `table`, `send_message`), `import {Commands} from '../../protocol'`.
- `open = table.myRenjuChoice(game) && renjuUi.mode === 'idle'` (the `&& mode==='idle'` is what
  suppresses re-pop after Decline/Offer). Add `TableClass.myRenjuChoice(game)` mirroring
  `mySwap2Choice` (`isMyTurn(game) && isRenjuSwapChoice/isRenjuBranchChoice`).
- Buttons, with **exact** visibility predicates (derivable from phase + `movesLength`):
  - `[Swap (take over)]` — shown when `isRenjuSwapChoice` (a swap window is *open*: moves 1–4 and
    the Branch-A move-5 window). **Hidden** in the standalone BRANCH state (post take-over) and in
    SELECTION. → `send_message(Commands.renjuSwap({swap:true, move:0, player:table.me, table:table.table}))`.
    (Send `0`; the server ignores `move` on `swap=true`.)
  - `[Decline & place]` (label "Place 5th move" when it is the 5th) — shown when `isRenjuSwapChoice
    || isRenjuBranchChoice` (the box/anywhere placement is the decline / Branch-A action). → dispatch
    `renjuBeginPlace` (sets `renjuUi.mode='placing'`); modal closes, board arms.
  - `[Offer 10]` — shown when **Branch B is still reachable**: `isRenjuBranchChoice ||
    (isRenjuSwapChoice && movesLength===4)` (the open move-4 window, or the standalone BRANCH after a
    take-over). **Hidden** at windows 1–3 and window 5. → dispatch `renjuBeginOffer`
    (`mode='offering'`); modal closes, board arms.

  So: windows 1–3 and window 5 show `[Swap]` + `[Decline & place]`; the **open move-4 window** shows
  all three; the **standalone BRANCH** (after `swap=true`) shows `[Place 5th]` + `[Offer 10]` only.
- Mounted in `src/Pages/Table.js` next to `<Swap2ChoiceModal/>` / `<DPenteChoiceModal/>`.

### 4.3 Placement / pick / selection surface — the live `Board` (`src/Components/Board/Board.js`)
In the `makeBoard` loop, branch on `renjuUi.mode` + derived phase (Board is already connected):
- **`mode==='placing'`** — the legal-cell set depends on `movesLength`: for **moves 2/3/4/5**
  (`movesLength` 1/2/3/4) assign `clickHandler` + a highlight flag only to empty cells **inside the
  N×N box** about center 112 (3×3/5×5/7×7/9×9 = radius 1/2/3/4; the Branch-A move 5 = 9×9). For the
  **window-5 decline** (`movesLength==5`, Branch A → the 6th stone) there is **no box** — move 6 may
  go **anywhere** empty (§8.6: box constrains only moves 2–5); light the whole empty board. Either
  way, tap → `Commands.renjuSwap({swap:false, move, player, table})`, then `renjuResetOpeningUi`.
- **`mode==='offering'`** — assign `clickHandler` to every empty, non-D4-duplicate cell on the
  **whole board** (offers are NOT box-constrained — §8.5). Tap toggles membership in
  `renjuUi.picks`; render picks as translucent `deadStone`; show an `n/10` counter + a submit
  control. Submit (exactly 10, distinct, non-symmetric) → `Commands.renjuOffer10({moves:picks, player, table})`,
  then `renjuResetOpeningUi`.
- **SELECTION phase, white to move** — render the tracked `offers` as translucent `deadStone`;
  assign `clickHandler` to those cells; tap one → `Commands.renjuSelect1({move, player, table})`.
- **Plain move (opening complete)** — when no swap window is open and not in placing/offering/
  selection: unchanged `Commands.move(...)`. (Note: Branch-A move 6 is the *window-5 decline* above,
  via `renjuSwap{swap:false}`, so plain moves resume at move 7 in Branch A and at move 6 in Branch B.)

**Invariant:** none of these surfaces ever places a stone locally. Stones arrive on
`DSGMoveTableEvent` → `addMove`. The translucent candidates are removed when the real stone lands.

Hover/ghost color must be black-first for Renju — the `currentColor()` Renju arm (§8.3 step 2)
feeds `player_colors[game.currentColor()]`, so the existing hover wiring is correct once that arm
exists.

### 4.4 Transient UI slice — `renjuUi { mode, picks }`
A small redux slice (idiomatic for this all-redux client). `mode ∈ {'idle','placing','offering'}`,
`picks: int[]`. Actions: `renjuBeginPlace`, `renjuBeginOffer`, `renjuTogglePick`,
`renjuResetOpeningUi`. It does double duty: **suppresses the modal** (`open` gate) and **arms the
board** (mode branch). Reset to `idle` on the server echo that advances the phase (and on table
change / unmount). This resolves the decline→place gap: the modal cannot re-pop while
`mode!=='idle'`, and the board is the only active surface until the stone echo arrives.

### 4.5 Inbound tracking + rejoin — `src/redux_reducers/{utils.js,rootReducer.js}`
- Three reducers update **tracking only, place no stones**:
  - `renjuSwap`: mark the window decided; at `movesLength==4`, any `swap=false` echo carrying a
    valid stone ⇒ `branch='A'`. (Visual seat swap on non-silent `swap=true` stays in the existing
    `swapSeats`/`table.swap()` path.)
  - `renjuOffer10`: `branch='B'`, `offers = data.moves`.
  - `renjuSelect1`: `selection = data.move`.
- Register the three in `EVENT_HANDLERS` (keys **must** equal the wrapper keys —
  `dsgRenjuTaraguchiSwapTableEvent`, `dsgRenjuTaraguchiOffer10TableEvent`,
  `dsgRenjuTaraguchi10Select1TableEvent`). The registry stays in `rootReducer.js`.
- **Rejoin:** the server sends authoritative seats (`sendPlayingPlayers`) plus **exactly one**
  current-decision-point signal (§8.4 / guide §7): nothing, a **silent** `dsgSwapSeatsTableEvent`
  (window resolved → advance the tracked phase for the current window only; **no** visual swap, **no**
  orientation flag), an **offer10** frame, or a replayed **select1** frame. Teach the silent branch
  of `swapSeats` to advance the Renju tracked phase (it is a phase marker, not a net-swap).

### 4.6 Outbound — `src/protocol/messages.js`
Add three `MESSAGES` entries (`time` auto-stamped, omit from `out`; `TBL=['table']`):
```js
dsgRenjuTaraguchiSwapTableEvent:     { dir:'both', cmd:'renjuSwap',    out:['swap','move','player','table'], req:TBL },
dsgRenjuTaraguchiOffer10TableEvent:  { dir:'both', cmd:'renjuOffer10',  out:['moves','player','table'],       req:TBL },
dsgRenjuTaraguchi10Select1TableEvent:{ dir:'both', cmd:'renjuSelect1',  out:['move','player','table'],        req:TBL },
```
`INBOUND_TYPES` / `COMMANDS` regenerate automatically; `Commands.renjuSwap(...)` etc. reach via
`import {Commands} from '../../protocol'`.

### 4.7 Static variant wiring (§8.3 — follow exactly)
- **`src/game/boardGeometry.js`:** `gridSizeForGame` → 15 for 31/32/81; `variantKey` → a `'renju'`
  branch before the `'swap2-keryo'` fallthrough (+ a `.renju` board CSS class, key doubles as class);
  add `31` to `STANDARD_GAME_IDS`; `boardSpecialPoints` → Renju branch returning the 9 star points at
  cols/rows {3,7,11} = `[48,52,56,108,112,116,168,172,176]`, using the Go-style dot part **52**.
- **`src/Classes/GameClass.js`:** `VARIANT_RULES['renju'] = {replay:'renju',
  disableRatedOnReplay:false, add:'gomoku', goMove:false, player:'renju', postRule:'none'}`; add
  `renju` to the `replayGame` dispatch object → `#replayRenjuGame` (black-first clone of
  `#replayGomokuGame`: `color = 2 - (i%2)` and `% this.gridSize` / `/ this.gridSize`, **not** `%19`);
  add a `rules.player === 'renju'` (black-first) arm in `addMoveFromList`/`addMove`; add a Renju arm
  to `currentColor()` (`2 - (moves.length % 2)`); add `#isRenju = ()=>game===31||32||81` and a
  `renjuOpeningPlayer(...)` branch in `currentPlayer()` (mirror the `#isSwap2()` arm) so `isMyTurn`
  is correct during the opening.
- **`src/game/gameState.js`:** initialize the `renjuState` tracking slice in `GameClass.reset()`
  next to `dPenteState`/`swap2State`.

## 5. Data flow (happy paths)

**Branch A (place 5th):** swap windows 1–3 → modal `[Decline & place]` → box tap →
`renjuSwap{swap:false,move}` (×3, moves 2/3/4) → at move 4 modal also offers `[Offer 10]`; choose
`[Decline & place]` (or `[Swap]`+then place) → 9×9 tap = move 5 → window-5 modal → `[Decline &
place]` → move 6 → opening complete.

**Branch B (offer 10):** at move 4, modal `[Offer 10]` → board 10-pick + submit →
`renjuOffer10{moves}`; opponent (white) sees 10 translucent candidates, taps one →
`renjuSelect1{move}` → server places move 5 (`DSGMoveTableEvent`) → white plays move 6 directly
(no window 5).

**Stones** always arrive via `DSGMoveTableEvent` → `addMove`; the three Renju echoes only advance
tracking.

## 6. Testing

**Unit (TDD first):**
- `renjuPhase` over **every** `(movesLength, renjuState)` row of §8.2 (incl. both ambiguous lengths
  and the rejoin-reachable states) → exact `RenjuPhase`.
- The `renjuUi` reducer: each action's transition; `togglePick` add/remove; reset.
- Client symmetry-dedup matches the JSP reference (`renjuRotate`/`renjuStabilizer`/
  `renjuIsSymmetricDup`) on a battery of points incl. corners and axis/diagonal cases.
- The three inbound reducers update tracking and place no stones.

**Integration (browser, Playwright, against the live backend on localhost):**
- Full opening, Branch A: two clients, every swap window, box constraint visible, move 6 reached.
- Full opening, Branch B: offer 10 (incl. a rejected symmetric pick), opponent selects, move 6.
- Rejoin mid-opening (observer + seated) reconstructs the correct phase/UI from the single
  decision-point signal.

Backend sync for manual/browser testing: `export JAVA_HOME=…openjdk@21…; ./justCompile` then
`docker restart penteorg-pente.org-1` (Java is already done/committed for sub-projects 1–2; no
backend changes expected here). Test creds and the WS handshake are in the project memory.

## 7. Risks / watch-items
- **Decline→place gap** — handled by `renjuUi.mode` gating both the modal and the board (§4.4).
- **`renjuOpeningPlayer` exact need** — guide §8.1/§8.3 mark it **(verify)**; add it (safe) and
  confirm `isMyTurn` during the opening in the browser test.
- **`swap=true` move sentinel** — send `0`; verify against `ServerTable.handleRenjuSwap` (it ignores
  `move` on `swap=true`).
- **`deadStone` outside the Go block** — confirmed reusable (Board sets it, `SimpleStone` honors
  `opacity`); just set `board[s].deadStone` for Renju candidates outside the `game.isGo()` branch.
- **Hardcoded `% 19`** in the gomoku replay copy — `#replayRenjuGame` must use `this.gridSize`.
