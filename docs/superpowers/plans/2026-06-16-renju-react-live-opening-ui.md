# React Live Renju (Taraguchi-10) Opening UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the live Taraguchi-10 opening UI (swap windows, Branch A / Branch B, selection, rejoin) in the `react_live_game_room` client, wired to the already-shipped backend events.

**Architecture:** A pure phase classifier (`renjuPhase`, mirroring the server's `getOpeningPhase`/`RenjuRejoin.decode`) drives three UI surfaces — a recycled-swap2 decision **modal**, the live **board** (constrained placement / 10-pick / selection), and a transient **`renjuOpeningUi`** redux slice that both suppresses the modal and arms the board. Inbound echoes update a tracked `renjuState` on `game.gameState`; no surface ever places a stone (stones ride `DSGMoveTableEvent`).

**Tech Stack:** React 18 + Redux (single root reducer, mutate-then-return), `@giantmachines/redux-websocket`, the `Protocol` facade (`src/protocol`), MUI modals, Vitest (`environment: 'node'`, `src/**/*.test.js`). No jsdom/RTL/Playwright in-repo — components are browser-verified via the Playwright MCP driver.

**Spec:** `docs/superpowers/specs/2026-06-16-renju-react-live-opening-ui-design.md`. Anchor reference: `docs/renju-integration-guide.md` §8.

**Working dir for ALL tasks:** the **sibling standalone clone**
`/Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room`
(branch off `main`; remote `rainwolf/react_live_game_room`). **Never** the `pente.org/react-live-game-room` submodule.

---

## Canonical reference (used by many tasks)

### The tracked opening record (mirror of the server booleans)
Lives at `game.gameState.renjuState`. Mirrors `RenjuState`'s `openingComplete` / `awaitingSwap` / `branchChosen` / `tenOffer` / `offeredFifth` / `selectedFifth`:
```js
// fresh value (set in GameClass.reset)
{ complete: false, awaitingSwap: false, branchChosen: false, tenOffer: false, offered: [], selected: null }
```

### Server phase truth being mirrored (`RenjuState.getOpeningPhase`)
```
COMPLETE  if openingComplete
SWAP      if awaitingSwap
BRANCH    if (!awaitingSwap && numMoves==4 && !branchChosen)
SELECTION if (branchChosen && tenOffer && numMoves==4 && offered==10 && selected==null)
MOVE      otherwise
```

### Per-phase wire command the client must send (from `ServerTable.handleRenjuSwap/Offer10/Select1` + `handleMove`)
| Situation | Phase | Client sends |
|---|---|---|
| Take over the side (any open window) | SWAP | `Commands.renjuSwap({swap:true, move:0, …})` |
| Decline + place move 2/3/4 (windows 1–4) | SWAP, `numMoves`∈[1,4] | `Commands.renjuSwap({swap:false, move, …})` — **stone bundled** (box-constrained) |
| Decline swap-5 (window 5) | SWAP, `numMoves==5` | `Commands.renjuSwap({swap:false, move:0, …})` — **bare, no stone**; move 6 follows as a normal move |
| Place move after a take-over (moves 2–4) | MOVE, `numMoves`∈[1,3] | `Commands.move({move, moves:[move], …})` — box-constrained |
| Branch A: place move 5 | BRANCH | `Commands.renjuSwap({swap:false, move5, …})` — 9×9 box |
| Branch B: offer ten | BRANCH | `Commands.renjuOffer10({moves, …})` |
| Branch B: select one | SELECTION (white) | `Commands.renjuSelect1({move, …})` |
| Normal play | MOVE (post-window-5) / COMPLETE | `Commands.move(...)` — anywhere |

**Box radius** for placing move `numMoves+1`: `numMoves` for `numMoves`∈[1,4] (3×3/5×5/7×7/9×9 about center 112), else **0 = whole board** (move 6+). Center = `(7,7)` on 15×15; index `= x + y*15`.

### Echo → tracking transitions (the reducers in Task 6)
- **move echo** (`addMove`) brings `numMoves` to `N`: recompute `awaitingSwap` / `complete`:
  `awaitingSwap = N<=4 || (N===5 && !tenOffer)` ; `complete = !awaitingSwap && N>=5` ; (when `complete`, `awaitingSwap=false`).
- **renjuSwap echo**: `awaitingSwap=false`; if `swap===false && N===4` ⇒ `branchChosen=true, tenOffer=false` (Branch A); a bundled move echo follows and reopens the next window.
- **renjuOffer10 echo**: `branchChosen=true, tenOffer=true, offered=data.moves, awaitingSwap=false`.
- **renjuSelect1 echo**: `selected=data.move` (the following move echo sets `complete`).

---

## Task 0: Branch + harness baseline

**Files:** none (git + sanity).

- [ ] **Step 1: Branch off main in the sibling repo**

Run (in the sibling repo):
```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room
git checkout main && git pull --ff-only && git checkout -b renju
```
Expected: on a new branch `renju`.

- [ ] **Step 2: Confirm a green baseline**

Run: `npm test`
Expected: all existing vitest suites pass (PASS lines for `src/game/__tests__`, `src/protocol/__tests__`, `src/redux_reducers/__tests__`, `src/Classes/__tests__`, etc.).

- [ ] **Step 3: Confirm single-file run works**

Run: `npx vitest run src/game/__tests__/openingPhase.test.js`
Expected: PASS. (This is the per-task run command pattern.)

---

## Task 1: `gameState.js` — fresh-tracking factory

**Files:**
- Modify: `src/game/gameState.js`
- Test: `src/game/__tests__/gameState.test.js` (create)

- [ ] **Step 1: Write the failing test**

`src/game/__tests__/gameState.test.js`:
```js
import { describe, test, expect } from 'vitest';
import { GameState, freshRenjuTracking } from '../gameState';

describe('freshRenjuTracking — initial Renju opening tracking record', () => {
  test('defaults: no decisions made, opening not complete', () => {
    expect(freshRenjuTracking()).toEqual({
      complete: false, awaitingSwap: false, branchChosen: false,
      tenOffer: false, offered: [], selected: null,
    });
  });
  test('returns a fresh object each call (no shared mutable state)', () => {
    const a = freshRenjuTracking();
    a.offered.push(1);
    expect(freshRenjuTracking().offered).toEqual([]);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/game/__tests__/gameState.test.js`
Expected: FAIL — `freshRenjuTracking is not a function`.

- [ ] **Step 3: Implement**

Append to `src/game/gameState.js` (after the `GameState` export):
```js

// Fresh Renju (Taraguchi-10) opening tracking — the client mirror of the server's
// openingComplete / awaitingSwap / branchChosen / tenOffer / offeredFifth / selectedFifth.
// A plain object (not an enum) because it accumulates several decision variables from the
// opening echoes; renjuPhase() in openingPhase.js classifies it. New object each call so
// GameClass.reset() never aliases a shared array.
export function freshRenjuTracking() {
  return { complete: false, awaitingSwap: false, branchChosen: false, tenOffer: false, offered: [], selected: null };
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/game/__tests__/gameState.test.js`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**
```bash
git add src/game/gameState.js src/game/__tests__/gameState.test.js
git commit -m "feat(renju): freshRenjuTracking factory for opening state"
```

---

## Task 2: `openingPhase.js` — RenjuPhase + classifier + derived predicates

**Files:**
- Modify: `src/game/openingPhase.js`
- Test: `src/game/__tests__/openingPhase.test.js` (extend existing)

This mirrors the server `getOpeningPhase` / `getCurrentPlayer` and the `RenjuRejoin.decode` contract. Pure over `(numMoves, renjuState)`.

- [ ] **Step 1: Write the failing tests**

Append to `src/game/__tests__/openingPhase.test.js`:
```js
import {
  RenjuPhase, renjuPhase, renjuOpeningPlayer,
  isRenjuSwapChoice, isRenjuBranchChoice, isRenjuSelection, renjuModalButtons,
} from '../openingPhase';
import { freshRenjuTracking } from '../gameState';

const rs = (over = {}) => ({ ...freshRenjuTracking(), ...over });

describe('renjuPhase — mirrors RenjuState.getOpeningPhase', () => {
  test('windows 1-4 open => SWAP', () => {
    for (const n of [1, 2, 3, 4]) {
      expect(renjuPhase(n, rs({ awaitingSwap: true }))).toBe(RenjuPhase.SWAP);
    }
  });
  test('move-4 window resolved, no branch yet => BRANCH', () => {
    expect(renjuPhase(4, rs({ awaitingSwap: false, branchChosen: false }))).toBe(RenjuPhase.BRANCH);
  });
  test('branch B, ten offers in, none selected => SELECTION', () => {
    expect(renjuPhase(4, rs({ branchChosen: true, tenOffer: true, offered: new Array(10).fill(0), selected: null })))
      .toBe(RenjuPhase.SELECTION);
  });
  test('branch A move-5 window open => SWAP (window 5)', () => {
    expect(renjuPhase(5, rs({ branchChosen: true, tenOffer: false, awaitingSwap: true }))).toBe(RenjuPhase.SWAP);
  });
  test('branch A move-5 window resolved => MOVE (place move 6)', () => {
    expect(renjuPhase(5, rs({ branchChosen: true, tenOffer: false, awaitingSwap: false }))).toBe(RenjuPhase.MOVE);
  });
  test('branch B after selection (move 5 placed) => COMPLETE', () => {
    expect(renjuPhase(5, rs({ branchChosen: true, tenOffer: true, complete: true }))).toBe(RenjuPhase.COMPLETE);
  });
  test('post-take-over windows 1-3 (awaiting cleared, n<4) => MOVE', () => {
    for (const n of [1, 2, 3]) {
      expect(renjuPhase(n, rs({ awaitingSwap: false }))).toBe(RenjuPhase.MOVE);
    }
  });
  test('opening complete => COMPLETE', () => {
    expect(renjuPhase(6, rs({ complete: true }))).toBe(RenjuPhase.COMPLETE);
  });
});

describe('renjuOpeningPlayer — mirrors RenjuState.getCurrentPlayer during the opening', () => {
  test('awaiting a swap decision: the not-last color is to move', () => {
    // n=1 (move 1 placed): lastColor=(1-1)%2+1=1 -> returns 2
    expect(renjuOpeningPlayer(1, rs({ awaitingSwap: true }))).toBe(2);
    // n=4: lastColor=(4-1)%2+1=2 -> returns 1
    expect(renjuOpeningPlayer(4, rs({ awaitingSwap: true }))).toBe(1);
  });
  test('branch choice at n=4 (no branch yet): black (1)', () => {
    expect(renjuOpeningPlayer(4, rs({ awaitingSwap: false, branchChosen: false }))).toBe(1);
  });
  test('branch B offering: black (1); selecting: white (2)', () => {
    expect(renjuOpeningPlayer(4, rs({ branchChosen: true, tenOffer: true, offered: [1, 2] }))).toBe(1);
    expect(renjuOpeningPlayer(4, rs({ branchChosen: true, tenOffer: true, offered: new Array(10).fill(0), selected: null }))).toBe(2);
  });
  test('complete => null (caller falls back to parity)', () => {
    expect(renjuOpeningPlayer(6, rs({ complete: true }))).toBe(null);
  });
});

describe('renju choice predicates + modal buttons', () => {
  test('predicates gate on started', () => {
    expect(isRenjuSwapChoice(2, rs({ awaitingSwap: true }), false)).toBe(false);
    expect(isRenjuSwapChoice(2, rs({ awaitingSwap: true }), true)).toBe(true);
    expect(isRenjuBranchChoice(4, rs({ awaitingSwap: false, branchChosen: false }), true)).toBe(true);
    expect(isRenjuSelection(4, rs({ branchChosen: true, tenOffer: true, offered: new Array(10).fill(0) }), true)).toBe(true);
  });
  test('modal buttons by phase', () => {
    // windows 1-3 open: swap + declinePlace, no offer10
    expect(renjuModalButtons(2, rs({ awaitingSwap: true }), true)).toEqual({ swap: true, declinePlace: true, offer10: false });
    // open move-4 window: all three
    expect(renjuModalButtons(4, rs({ awaitingSwap: true }), true)).toEqual({ swap: true, declinePlace: true, offer10: true });
    // standalone BRANCH (post take-over): place + offer10, NO swap
    expect(renjuModalButtons(4, rs({ awaitingSwap: false, branchChosen: false }), true)).toEqual({ swap: false, declinePlace: true, offer10: true });
    // window 5: swap + decline, no offer10
    expect(renjuModalButtons(5, rs({ branchChosen: true, awaitingSwap: true }), true)).toEqual({ swap: true, declinePlace: true, offer10: false });
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/game/__tests__/openingPhase.test.js`
Expected: FAIL — `renjuPhase is not exported` (and the new imports undefined).

- [ ] **Step 3: Implement**

Append to `src/game/openingPhase.js`:
```js

// --- renju (Taraguchi-10) -------------------------------------------------------------
//
// The CLIENT mirror of RenjuState.getOpeningPhase / getCurrentPlayer (server) and the
// RenjuRejoin.decode contract. Pure over (numMoves, renjuState) where renjuState is the
// tracked record (gameState.js freshRenjuTracking) accumulated from the opening echoes —
// NOT a computed engine state. The thresholds live here and nowhere else.

export const RenjuPhase = {
  SWAP: 'SWAP',           // a swap window is open (swap, or decline + place)
  BRANCH: 'BRANCH',       // move-4 window resolved; black picks Branch A vs B
  SELECTION: 'SELECTION', // Branch B: ten offers in, white picks one
  MOVE: 'MOVE',           // no decision pending; place a stone
  COMPLETE: 'COMPLETE',   // six-stone opening done; normal play
};

// Mirrors RenjuState.getOpeningPhase().
export function renjuPhase(numMoves, renjuState) {
  const { complete, awaitingSwap, branchChosen, tenOffer, offered, selected } = renjuState;
  if (complete) return RenjuPhase.COMPLETE;
  if (awaitingSwap) return RenjuPhase.SWAP;
  if (numMoves === 4 && !branchChosen) return RenjuPhase.BRANCH;
  if (numMoves === 4 && branchChosen && tenOffer && offered.length === 10 && selected == null) {
    return RenjuPhase.SELECTION;
  }
  return RenjuPhase.MOVE;
}

// Mirrors RenjuState.getCurrentPlayer() during the opening; returns null once complete so
// the caller (GameClass.currentPlayer) falls back to plain alternation — same shape as
// swap2OpeningPlayer / dPenteOpeningPlayer.
export function renjuOpeningPlayer(numMoves, renjuState) {
  const { complete, awaitingSwap, branchChosen, tenOffer, offered, selected } = renjuState;
  if (complete) return null;
  const n = numMoves;
  if (awaitingSwap) {
    const lastColor = ((n - 1) % 2) + 1;
    return 3 - lastColor;
  }
  if (branchChosen && tenOffer && n === 4) {
    if (offered.length < 10) return 1;   // black offering
    if (selected == null) return 2;      // white selecting
  }
  if (n === 4 && !branchChosen) return 1; // black chooses branch (and plays move 5)
  return (n % 2) + 1;
}

export function isRenjuSwapChoice(numMoves, renjuState, started) {
  return started && renjuPhase(numMoves, renjuState) === RenjuPhase.SWAP;
}
export function isRenjuBranchChoice(numMoves, renjuState, started) {
  return started && renjuPhase(numMoves, renjuState) === RenjuPhase.BRANCH;
}
export function isRenjuSelection(numMoves, renjuState, started) {
  return started && renjuPhase(numMoves, renjuState) === RenjuPhase.SELECTION;
}

// Which decision-modal buttons to show. Swap only while a window is open; Offer-10 whenever
// Branch B is still reachable (open move-4 window OR the standalone BRANCH after a take-over);
// Decline/Place at any swap window or the branch choice.
export function renjuModalButtons(numMoves, renjuState, started) {
  const swapChoice = isRenjuSwapChoice(numMoves, renjuState, started);
  const branchChoice = isRenjuBranchChoice(numMoves, renjuState, started);
  return {
    swap: swapChoice,
    declinePlace: swapChoice || branchChoice,
    offer10: branchChoice || (swapChoice && numMoves === 4),
  };
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/game/__tests__/openingPhase.test.js`
Expected: PASS (all new describes green; existing swap2/dPente tests still pass).

- [ ] **Step 5: Commit**
```bash
git add src/game/openingPhase.js src/game/__tests__/openingPhase.test.js
git commit -m "feat(renju): pure renjuPhase classifier + opening-player + modal-button predicates"
```

---

## Task 3: `boardGeometry.js` — register ids 31/32/81

**Files:**
- Modify: `src/game/boardGeometry.js`, `src/Classes/Utils.js` (VARIANT_NAMES), `src/App.css` (`.renju` class)
- Test: `src/game/__tests__/boardGeometry.test.js` (extend + **update** the obsolete `STANDARD_GAME_IDS` assertions)

> **Plan correction (found during execution):** registering id 31 in the picker is not just a one-liner. Three existing things pin the old `STANDARD_GAME_IDS`/names and must be reconciled, or the picker shows a blank name:
> 1. `boardGeometry.test.js` asserts `STANDARD_GAME_IDS` `toEqual([1..29])`, `toHaveLength(15)`, `.not.toContain(31)`, and `new Set(map(variantKey)).size === 13` → update to length **16**, contains **31**, distinct keys **14**.
> 2. `src/Classes/Utils.js` `VARIANT_NAMES` has no `'renju'` key → `game_name(31)`/`game_name(32)` produce `undefined`/`'Speed undefined'`. Add `'renju': 'Renju'`.
> 3. `utils.test.js` loops `STANDARD_GAME_IDS` asserting `game_name(id)`/`game_name(id+1)` are `'undefined'`-free strings — passes once (2) is done (no edit needed).
> 4. `src/Classes/TableClass.js` `VARIANT_COLORS` (per-variantKey lobby-card colour) has the SAME failure mode — `tableClass.test.js` loops `g=1..32` asserting `table_color()` is a string. Add `'renju': '<color>'` matching the `.renju` fill. (Done in execution as `#D98880`. Note the file is git-tracked lowercase as `src/Classes/utils.js`.)
> The live picker (`SettingsModal.js`, `CreateArenaTableModal.js`) renders `game_name` per id, so (2) is required for renju to be creatable. Edits (c)+(f) below cover this.

- [ ] **Step 1: Write the failing tests**

Append to `src/game/__tests__/boardGeometry.test.js` (match its existing import style):
```js
import { gridSizeForGame, variantKey, STANDARD_GAME_IDS, boardSpecialPoints, isGoBoard } from '../boardGeometry';

describe('renju geometry (ids 31 / 32 / 81)', () => {
  test('grid size is 15 for all renju ids', () => {
    expect(gridSizeForGame(31)).toBe(15);
    expect(gridSizeForGame(32)).toBe(15);
    expect(gridSizeForGame(81)).toBe(15);
  });
  test("variant key is 'renju'", () => {
    expect(variantKey(31)).toBe('renju');
    expect(variantKey(32)).toBe('renju');
    expect(variantKey(81)).toBe('renju');
  });
  test('renju is not a go board', () => {
    expect(isGoBoard(31)).toBe(false);
  });
  test('renju 31 is offered in the picker', () => {
    expect(STANDARD_GAME_IDS).toContain(31);
  });
  test('renju star points are the 9 dots at cols/rows {3,7,11}', () => {
    const pts = boardSpecialPoints(31);
    expect(pts.map((p) => p.index).sort((a, b) => a - b)).toEqual([48, 52, 56, 108, 112, 116, 168, 172, 176]);
    expect(pts.every((p) => p.part === 52)).toBe(true);
  });
  test('existing variants unchanged', () => {
    expect(variantKey(1)).toBe('pente');
    expect(variantKey(29)).toBe('swap2-keryo');
    expect(gridSizeForGame(21)).toBe(9);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/game/__tests__/boardGeometry.test.js`
Expected: FAIL — `gridSizeForGame(31)` is 19, `variantKey(31)` is `'swap2-keryo'`, `STANDARD_GAME_IDS` lacks 31, `boardSpecialPoints(31)` returns the 19×19 CIRCLES.

- [ ] **Step 3: Implement**

In `src/game/boardGeometry.js`:

(a) `gridSizeForGame` — add before `return 19;`:
```js
  if (gameId === 31 || gameId === 32 || gameId === 81) return 15;
```

(b) `variantKey` — add before `return 'swap2-keryo';`:
```js
  if (gameId === 31 || gameId === 32 || gameId === 81) return 'renju';
```
(The range chain is lowest-id-first, but renju ids are above the chain's `< 29` guards; an explicit id check before the final fallthrough keeps it unambiguous and avoids disturbing the `swap2-keryo` default for 30.)

(c) `STANDARD_GAME_IDS` — add `31`:
```js
export const STANDARD_GAME_IDS = [1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31];
```
…and **update the now-obsolete assertions** in `boardGeometry.test.js`: the `STANDARD_GAME_IDS` `toEqual([...])` / `toHaveLength(15)` / `.not.toContain(31)` and the `new Set(STANDARD_GAME_IDS.map(variantKey)).size` `toBe(13)` → length **16**, include **31**, distinct keys **14**.

(f) `src/Classes/Utils.js` — add a `'renju'` display name so `game_name(31)`/`game_name(32)` resolve (the picker renders these): add `'renju': 'Renju',` to the `VARIANT_NAMES` map (keyed by `variantKey`).

(d) `boardSpecialPoints` — add a renju branch at the top:
```js
export function boardSpecialPoints(gameId) {
  if (gameId === 31 || gameId === 32 || gameId === 81) {
    // 9 star points at cols/rows {3,7,11} on the 15x15 board (index = col + row*15; center 112).
    // Use the go-style dot (part 52); the non-go CIRCLES are 19x19-specific.
    return [48, 52, 56, 108, 112, 116, 168, 172, 176].map((index) => ({ index, part: 52 }));
  }
  if (!isGoBoard(gameId)) {
    return CIRCLES.map((index) => ({ index, part: 51 }));
  }
  return GO_DOTS[gridSizeForGame(gameId)].map((index) => ({ index, part: 52 }));
}
```

(e) CSS class — the board CSS class equals the variant key (`boardStyleClass`). Add a `.renju` board class mirroring the existing variant classes. Locate the board CSS (grep the repo for `swap2-keryo` in `.css`/`.scss`):
```bash
grep -rn "swap2-keryo" src --include=*.css --include=*.scss
```
Add a `.renju` rule alongside the matched selector (copy a non-Go 19×19→15×15 board background; if the styling is purely grid-line CSS independent of size, copy the `gomoku`/`pente` block). Note in the commit if no dedicated background asset exists (a follow-up can add one — the grid renders from `gridSizeForGame` regardless).

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/game/__tests__/boardGeometry.test.js`
Expected: PASS (renju + unchanged-variant tests).

- [ ] **Step 5: Commit**
```bash
git add src/game/boardGeometry.js src/game/__tests__/boardGeometry.test.js src/Classes/Utils.js src/App.css
git commit -m "feat(renju): board geometry + picker name + star points for ids 31/32/81"
```

---

## Task 4: `GameClass.js` — variant rules, black-first replay/colors, opening player

**Files:**
- Modify: `src/Classes/GameClass.js`
- Test: `src/Classes/__tests__/gameVariantPhase.test.js` (extend; this is where opening-player/color tests live) and/or `replayDispatch.test.js`

- [ ] **Step 1: Write the failing tests**

Append to `src/Classes/__tests__/gameVariantPhase.test.js` (match its imports — it constructs `new Game()` and `setGame`):
```js
import { Game, GameState } from '../GameClass';

function renjuGameAfter(moves) {
  const g = new Game();
  g.setGame(31);
  g.gameState.state = GameState.State.STARTED;
  // replay drives color + abstractBoard; addMove appends + recolors
  moves.forEach((m) => g.addMove(m));
  return g;
}

describe('renju is black-first (board value 2 = black)', () => {
  test('currentColor before any move is black (2)', () => {
    const g = new Game(); g.setGame(31);
    expect(g.currentColor()).toBe(2); // move 1 (center) is black
  });
  test('replayRenjuGame colors stones black, white, black… on the 15x15 grid', () => {
    const g = renjuGameAfter([112, 113, 97]); // center, +1col, -1row (all in-board on 15x15)
    g.replayGame();
    // move 1 -> value 2 (black) at center 112 = (7,7)
    expect(g.abstractBoard[7][7]).toBe(2);
    // move 2 -> value 1 (white) at 113 = (8,7)
    expect(g.abstractBoard[7][8]).toBe(1);
    // move 3 -> value 2 (black) at 97 = (7,6)
    expect(g.abstractBoard[6][7]).toBe(2);
  });
});

describe('renju opening player drives isMyTurn', () => {
  test('currentPlayer uses renjuOpeningPlayer while opening incomplete', () => {
    const g = new Game(); g.setGame(31);
    g.gameState.state = GameState.State.STARTED;
    g.addMove(112); // move 1 placed; reducer normally sets awaitingSwap — set it for the unit test
    g.gameState.renjuState.awaitingSwap = true;
    // n=1 awaiting swap: lastColor=1 -> player 2 to move
    expect(g.currentPlayer()).toBe(2);
  });
});
```

NOTE: `replayGame` reads `% this.gridSize`; verify `g.gridSize === 15` after `setGame(31)` (Task 3 makes `gridSizeForGame(31)===15`). The `97`/`113` indices above assume 15-wide rows.

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/Classes/__tests__/gameVariantPhase.test.js`
Expected: FAIL — `currentColor()` returns 1 (white-first), `replayGame` uses the gomoku white-first `%19` path (stones land off-grid / wrong color), `currentPlayer` doesn't branch on renju.

- [ ] **Step 3: Implement** (six edits in `GameClass.js`)

(a) Import the renju helpers (extend the existing `openingPhase` import):
```js
import { /* …existing… */ renjuOpeningPlayer } from '../game/openingPhase';
import { /* …existing… */ freshRenjuTracking } from '../game/gameState';
```

(b) `VARIANT_RULES` — add a `'renju'` row (note `add: 'gomoku'` reuses `#addGomokuMove`):
```js
   'renju':       {replay: 'renju',    disableRatedOnReplay: false, add: 'gomoku', goMove: false, player: 'renju',    postRule: 'none'},
```

(c) `replayGame` — add `renju` to the inline dispatch object:
```js
      const replay = {
         pente: this.#replayPenteGame, keryo: this.#replayKeryoPenteGame, gomoku: this.#replayGomokuGame,
         gPente: this.#replayGPenteGame, poof: this.#replayPoofPenteGame, connect6: this.#replayConnect6Game,
         go: this.#replayGoGame, oPente: this.#replayOPenteGame, renju: this.#replayRenjuGame,
      }[rules.replay];
```

(d) Add `#replayRenjuGame` next to `#replayGomokuGame` — black-first + `this.gridSize` (NOT `19`):
```js
   #replayRenjuGame = (until) => {
      for (let i = 0; i < Math.min(this.moves.length, until); i++) {
         let color = 2 - (i % 2); // i=0 -> 2 (black-first); gomoku is white-first (1 + i%2)
         let x = this.moves[i] % this.gridSize, y = Math.floor(this.moves[i] / this.gridSize);
         this.#addGomokuMove(x, y, color);
      }
   };
```

(e) Per-move color arms — add a `'renju'` branch in `addMoveFromList` and `addMove` (both black-first), and a renju arm in `currentColor`:
```js
   // in addMoveFromList: replace the player= ternary with a 3-way
   const player = rules.player === 'connect6'
      ? ((((i % 4) === 0) || ((i % 4) === 3)) ? 1 : 2)
      : rules.player === 'renju'
         ? 2 - (i % 2)
         : 1 + (i % 2);
```
```js
   // in addMove: replace the player= ternary with a 3-way
   const player = rules.player === 'connect6'
      ? ((((this.moves.length % 4) === 1) || ((this.moves.length % 4) === 0)) ? 1 : 2)
      : rules.player === 'renju'
         ? 1 + (this.moves.length % 2)   // moves.length==1 -> 2 (black-first)
         : 2 - (this.moves.length % 2);
```
```js
   // in currentColor: add the renju arm before the standard return
   currentColor = () => {
      if (this.isConnect6()) {
         return (((this.moves.length % 4) === 0) || ((this.moves.length % 4) === 3)) ? 1 : 2;
      }
      if (this.#isRenju()) {
         return 2 - (this.moves.length % 2); // black-first hover/ghost
      }
      return 1 + (this.moves.length % 2);
   };
```

(f) Guard + `currentPlayer` arm + `reset` init + a `renjuChoice` helper. Add `#isRenju` near the other guards:
```js
   #isRenju = () => {
      return this.game === 31 || this.game === 32 || this.game === 81;
   };
```
Add a renju arm in `currentPlayer` (mirror the `#isSwap2()` arm):
```js
      } else if (this.#isRenju()) {
         const p = renjuOpeningPlayer(this.moves.length, this.gameState.renjuState);
         if (p !== null) {
            return p;
         }
      } else if (this.isGo() && this.gameState.goState === GameState.GoState.MARK_STONES) {
```
Initialize the tracking record in `reset()`:
```js
      this.gameState = {
         state: GameState.State.NOT_STARTED,
         dPenteState: GameState.DPenteState.NO_CHOICE,
         swap2State: GameState.Swap2State.NO_CHOICE,
         goState: GameState.GoState.PLAY,
         renjuState: freshRenjuTracking(),
      }
```
Add a `renjuChoice()` predicate (used by `TableClass.myRenjuChoice` in Task 10) near `swap2Choice`/`dPenteChoice`:
```js
   renjuChoice = () => {
      if (!this.#isRenju()) return false;
      const started = this.gameState.state === GameState.State.STARTED;
      return isRenjuSwapChoice(this.moves.length, this.gameState.renjuState, started)
          || isRenjuBranchChoice(this.moves.length, this.gameState.renjuState, started);
   };
```
…and add `isRenjuSwapChoice, isRenjuBranchChoice` to the `openingPhase` import (b).

**`newInstance()` check:** `GameClass.newInstance()` must deep-copy `gameState` (the reducers call `game.newInstance()` then mutate `gameState.renjuState`). Grep `newInstance` in `GameClass.js`; if it shallow-copies `gameState`, ensure `renjuState` (and its `offered` array) is cloned, e.g. `gameState: { ...this.gameState, renjuState: { ...this.gameState.renjuState, offered: [...this.gameState.renjuState.offered] } }`. Add a test asserting a mutation to the copy's `renjuState.offered` does not affect the original.

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/Classes/__tests__/gameVariantPhase.test.js src/Classes/__tests__/replayDispatch.test.js`
Expected: PASS (renju color/replay/player + existing variants unchanged).

- [ ] **Step 5: Commit**
```bash
git add src/Classes/GameClass.js src/Classes/__tests__/gameVariantPhase.test.js
git commit -m "feat(renju): GameClass variant rules, black-first replay/colors, opening player"
```

---

## Task 5: `renjuOpeningUi` redux slice (transient mode + picks)

**Files:**
- Create: `src/ui/renjuOpeningUi.js`
- Test: `src/ui/__tests__/renjuOpeningUi.test.js` (create)

Mirrors the `modals` sub-slice pattern (`src/ui/modals.js` → `newState.modals = modalsReducer(...)`). State: `{ mode: 'idle'|'placing'|'offering', picks: number[] }`.

- [ ] **Step 1: Write the failing test**
```js
import { describe, test, expect } from 'vitest';
import {
  renjuOpeningUiReducer, renjuBeginPlace, renjuBeginOffer, renjuTogglePick, renjuResetOpeningUi,
} from '../renjuOpeningUi';

const init = () => renjuOpeningUiReducer(undefined, { type: '@@INIT' });

describe('renjuOpeningUi slice', () => {
  test('initial state is idle with no picks', () => {
    expect(init()).toEqual({ mode: 'idle', picks: [] });
  });
  test('beginPlace -> placing; beginOffer -> offering (picks reset)', () => {
    expect(renjuOpeningUiReducer(init(), renjuBeginPlace())).toEqual({ mode: 'placing', picks: [] });
    expect(renjuOpeningUiReducer({ mode: 'idle', picks: [9] }, renjuBeginOffer())).toEqual({ mode: 'offering', picks: [] });
  });
  test('togglePick adds then removes (only in offering mode)', () => {
    let s = renjuOpeningUiReducer(init(), renjuBeginOffer());
    s = renjuOpeningUiReducer(s, renjuTogglePick(40));
    expect(s.picks).toEqual([40]);
    s = renjuOpeningUiReducer(s, renjuTogglePick(40));
    expect(s.picks).toEqual([]);
  });
  test('reset -> idle/empty', () => {
    expect(renjuOpeningUiReducer({ mode: 'offering', picks: [1, 2] }, renjuResetOpeningUi())).toEqual({ mode: 'idle', picks: [] });
  });
  test('unrelated action returns the SAME reference (no-op, matches modals slice)', () => {
    const s = init();
    expect(renjuOpeningUiReducer(s, { type: 'SOMETHING_ELSE' })).toBe(s);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/ui/__tests__/renjuOpeningUi.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement** `src/ui/renjuOpeningUi.js`:
```js
// Transient UI state for the live Renju opening. Sits beside `modals` as a no-op-by-default
// sub-slice (same reference returned for unrelated actions). It does double duty: suppresses
// the decision modal (open gate) and arms the board — `placing` routes board taps to a
// renjuSwap(swap:false) decline/Branch-A move, `offering` accumulates the 10-pick selection.
// Reset to idle on the server echo that advances the phase (and on table change / unmount).

const BEGIN_PLACE = 'RENJU_UI/BEGIN_PLACE';
const BEGIN_OFFER = 'RENJU_UI/BEGIN_OFFER';
const TOGGLE_PICK = 'RENJU_UI/TOGGLE_PICK';
const RESET = 'RENJU_UI/RESET';

export const renjuBeginPlace = () => ({ type: BEGIN_PLACE });
export const renjuBeginOffer = () => ({ type: BEGIN_OFFER });
export const renjuTogglePick = (move) => ({ type: TOGGLE_PICK, move });
export const renjuResetOpeningUi = () => ({ type: RESET });

const INITIAL = { mode: 'idle', picks: [] };

export function renjuOpeningUiReducer(state = INITIAL, action) {
  switch (action.type) {
    case BEGIN_PLACE:
      return { mode: 'placing', picks: [] };
    case BEGIN_OFFER:
      return { mode: 'offering', picks: [] };
    case TOGGLE_PICK: {
      if (state.mode !== 'offering') return state;
      const picks = state.picks.includes(action.move)
        ? state.picks.filter((m) => m !== action.move)
        : [...state.picks, action.move];
      return { ...state, picks };
    }
    case RESET:
      return INITIAL;
    default:
      return state;
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/ui/__tests__/renjuOpeningUi.test.js`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**
```bash
git add src/ui/renjuOpeningUi.js src/ui/__tests__/renjuOpeningUi.test.js
git commit -m "feat(renju): transient renjuOpeningUi redux slice (mode + picks)"
```

---

## Task 6: inbound tracking reducers (`utils.js`)

**Files:**
- Modify: `src/redux_reducers/utils.js`
- Test: `src/redux_reducers/__tests__/renjuTracking.test.js` (create)

Adds `renjuSwap` / `renjuOffer10` / `renjuSelect1`, extends `addMove` to advance renju tracking, and extends `swapSeats`'s silent branch to advance the rejoin phase. All follow the existing `(data, state)` mutate-then-reassign pattern with the `data.table === state.table` guard.

- [ ] **Step 1: Write the failing test**
```js
import { describe, test, expect } from 'vitest';
import { addMove, renjuSwap, renjuOffer10, renjuSelect1 } from '../utils';
import { Game, GameState } from '../../Classes/GameClass';
import Table from '../../Classes/TableClass';

function renjuState() {
  const g = new Game();
  g.setGame(31);
  g.gameState.state = GameState.State.STARTED;
  const t = new Table({ table: 5, initialMinutes: 10 });
  t.me = 'alice';
  return { game: g, tables: { 5: t }, table: 5, me: 'alice', pendingNotifications: [] };
}
const move = (s, m, player = 'srv') => addMove({ table: 5, move: m, moves: [m], player }, s);

describe('renju tracking reducers', () => {
  test('addMove opens the swap window after moves 1-4', () => {
    const s = renjuState();
    move(s, 112);
    expect(s.game.gameState.renjuState.awaitingSwap).toBe(true);
    expect(s.game.gameState.renjuState.complete).toBe(false);
  });
  test('renjuSwap(false) at n=4 chooses Branch A and clears awaitingSwap', () => {
    const s = renjuState();
    [112, 113, 97, 98].forEach((m) => move(s, m)); // n=4, awaitingSwap
    renjuSwap({ table: 5, swap: false, move: 129, player: 'alice' }, s);
    expect(s.game.gameState.renjuState.awaitingSwap).toBe(false);
    expect(s.game.gameState.renjuState.branchChosen).toBe(true);
    expect(s.game.gameState.renjuState.tenOffer).toBe(false);
  });
  test('renjuOffer10 records Branch B + offers', () => {
    const s = renjuState();
    [112, 113, 97, 98].forEach((m) => move(s, m));
    const offers = [40, 41, 42, 55, 57, 70, 71, 72, 160, 176];
    renjuOffer10({ table: 5, moves: offers, player: 'alice' }, s);
    const r = s.game.gameState.renjuState;
    expect(r.branchChosen).toBe(true);
    expect(r.tenOffer).toBe(true);
    expect(r.offered).toEqual(offers);
    expect(r.awaitingSwap).toBe(false);
  });
  test('renjuSelect1 records the selection; following move 5 completes Branch B', () => {
    const s = renjuState();
    [112, 113, 97, 98].forEach((m) => move(s, m));
    renjuOffer10({ table: 5, moves: [40, 41, 42, 55, 57, 70, 71, 72, 160, 176], player: 'alice' }, s);
    renjuSelect1({ table: 5, move: 57, player: 'bob' }, s);
    expect(s.game.gameState.renjuState.selected).toBe(57);
    move(s, 57); // server places move 5
    expect(s.game.gameState.renjuState.complete).toBe(true); // branch B completes at n=5
  });
  test('Branch A: move 5 then window 5; move 6 completes', () => {
    const s = renjuState();
    [112, 113, 97, 98].forEach((m) => move(s, m));
    renjuSwap({ table: 5, swap: false, move: 129, player: 'alice' }, s); // branch A
    move(s, 129); // move 5 placed -> window 5 opens
    expect(s.game.gameState.renjuState.awaitingSwap).toBe(true);
    expect(s.game.gameState.renjuState.complete).toBe(false);
    renjuSwap({ table: 5, swap: false, move: 0, player: 'bob' }, s); // bare window-5 decline
    move(s, 200); // move 6 anywhere
    expect(s.game.gameState.renjuState.complete).toBe(true);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/redux_reducers/__tests__/renjuTracking.test.js`
Expected: FAIL — `renjuSwap`/`renjuOffer10`/`renjuSelect1` not exported; `addMove` does not touch `renjuState`.

- [ ] **Step 3: Implement** in `src/redux_reducers/utils.js`

Add a private helper + the three reducers, and call the helper from `addMove`:
```js
// Advance the tracked Renju opening record after a stone lands (mirror RenjuState.addMove):
// windows open after moves 1-4 and after move 5 in Branch A; the opening completes otherwise
// (move 6 in Branch A, move 5 in Branch B). No-op for non-renju games.
function advanceRenjuTrackingAfterMove(game) {
   if (!game.isRenjuGame || !game.isRenjuGame()) return;
   const r = game.gameState.renjuState;
   const n = game.moves.length;
   const windowOpens = n <= 4 || (n === 5 && !r.tenOffer);
   r.awaitingSwap = windowOpens;
   r.complete = !windowOpens && n >= 5;
}
```
(Expose `isRenjuGame()` publicly on `GameClass` — `#isRenju` is private — e.g. `isRenjuGame = () => this.game === 31 || this.game === 32 || this.game === 81;` and have `#isRenju` delegate to it. Add this in Task 4 instead if cleaner; cross-reference there.)

In `addMove`, after the stone(s) are applied and before `state.game = game;`:
```js
   advanceRenjuTrackingAfterMove(game);
```
(Place it inside the `if (data.table === state.table)` block, after the move application, so a fresh `game` copy already reflects the new `moves.length`.)

The three reducers (append, matching the file's export style):
```js
export function renjuSwap(data, state) {
   if (data.table === state.table) {
      const game = state.game.newInstance();
      const r = game.gameState.renjuState;
      r.awaitingSwap = false;
      // A swap=false at the move-4 window (or the standalone branch-choice state) continues
      // Branch A; the bundled stone arrives via the following DSGMoveTableEvent. The visual
      // seat swap for swap=true is handled by swapSeats/table.swap(), NOT here.
      if (data.swap === false && game.moves.length === 4) {
         r.branchChosen = true;
         r.tenOffer = false;
      }
      state.game = game;
   }
}

export function renjuOffer10(data, state) {
   if (data.table === state.table) {
      const game = state.game.newInstance();
      const r = game.gameState.renjuState;
      r.branchChosen = true;
      r.tenOffer = true;
      r.offered = [...data.moves];
      r.awaitingSwap = false;
      state.game = game;
   }
}

export function renjuSelect1(data, state) {
   if (data.table === state.table) {
      const game = state.game.newInstance();
      game.gameState.renjuState.selected = data.move;
      state.game = game;
   }
}
```

Extend `swapSeats`'s silent branch (rejoin phase marker) — after the existing `dPenteState`/`swap2State` assignment, add:
```js
      // Renju rejoin: a SILENT swap-seats is the "current window resolved" phase marker
      // (RenjuRejoin). Advance the tracked window without animating; its swap bit is the
      // CURRENT window's decision, NOT net orientation (seats come from sendPlayingPlayers).
      if (data.silent && game.isRenjuGame && game.isRenjuGame()) {
         const r = game.gameState.renjuState;
         r.awaitingSwap = false;
         if (game.moves.length === 4) { r.branchChosen = true; r.tenOffer = false; } // resolved -> BRANCH
      }
```
(The non-silent live `swap=true` still hits the `table.swap()` visual branch unchanged; the tracking `awaitingSwap=false` for a live take-over is set by `renjuSwap` above.)

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/redux_reducers/__tests__/renjuTracking.test.js`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**
```bash
git add src/redux_reducers/utils.js src/redux_reducers/__tests__/renjuTracking.test.js
git commit -m "feat(renju): inbound tracking reducers + addMove/swapSeats opening advance"
```

---

## Task 7: register handlers + wire the UI slice (`rootReducer.js`)

**Files:**
- Modify: `src/redux_reducers/rootReducer.js`
- Test: `src/redux_reducers/__tests__/renjuTracking.test.js` (extend with a dispatch-routing test)

- [ ] **Step 1: Write the failing test**

Append to `src/redux_reducers/__tests__/renjuTracking.test.js`:
```js
import liveGameApp from '../rootReducer';
import { renjuBeginOffer } from '../../ui/renjuOpeningUi';

describe('rootReducer wiring', () => {
  test('renjuOffer10 event is routed through EVENT_HANDLERS', () => {
    // build a started renju table via reducer init + minimal setup is heavy; instead assert
    // the handler is registered by dispatching the typed action onto a hand-built state.
    let s = { table: 5, me: 'alice', tables: {}, pendingNotifications: [],
      game: (() => { const g = new Game(); g.setGame(31); g.gameState.state = GameState.State.STARTED;
        [112,113,97,98].forEach(m=>g.addMove(m)); return g; })() };
    s.tables[5] = new Table({ table: 5, initialMinutes: 10 });
    const out = liveGameApp(s, { type: 'dsgRenjuTaraguchiOffer10TableEvent',
      payload: { table: 5, moves: [40,41,42,55,57,70,71,72,160,176], player: 'alice' } });
    expect(out.game.gameState.renjuState.tenOffer).toBe(true);
  });
  test('renjuOpeningUi slice is part of root state and reacts to its actions', () => {
    const base = liveGameApp(undefined, { type: '@@INIT' });
    expect(base.renjuOpeningUi).toEqual({ mode: 'idle', picks: [] });
    const out = liveGameApp(base, renjuBeginOffer());
    expect(out.renjuOpeningUi.mode).toBe('offering');
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/redux_reducers/__tests__/renjuTracking.test.js`
Expected: FAIL — handlers not registered (`tenOffer` stays false); `renjuOpeningUi` undefined.

- [ ] **Step 3: Implement** in `src/redux_reducers/rootReducer.js`

(a) Import the three reducers (extend the `from "./utils"` import) and the UI slice:
```js
import { /* …existing… */ renjuSwap, renjuOffer10, renjuSelect1 } from "./utils";
import { renjuOpeningUiReducer } from "../ui/renjuOpeningUi";
```

(b) Add to `EVENT_HANDLERS` (keys MUST equal the wrapper JSON keys):
```js
   dsgRenjuTaraguchiSwapTableEvent:     (p, s) => renjuSwap(p, s),
   dsgRenjuTaraguchiOffer10TableEvent:  (p, s) => renjuOffer10(p, s),
   dsgRenjuTaraguchi10Select1TableEvent:(p, s) => renjuSelect1(p, s),
```

(c) Add `renjuOpeningUi: { mode: 'idle', picks: [] }` to `initialState`.

(d) Wire the sub-slice like `modals` (after the switch, before `return newState;`):
```js
   newState.renjuOpeningUi = renjuOpeningUiReducer(newState.renjuOpeningUi, action);
```
(Mirror the exact placement of the existing `newState.modals = modalsReducer(...)` line.)

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/redux_reducers/__tests__/renjuTracking.test.js src/redux_reducers/__tests__/reducer.test.js`
Expected: PASS (routing + slice tests; existing reducer suite unaffected).

- [ ] **Step 5: Commit**
```bash
git add src/redux_reducers/rootReducer.js src/redux_reducers/__tests__/renjuTracking.test.js
git commit -m "feat(renju): register opening event handlers + renjuOpeningUi slice"
```

---

## Task 8: outbound protocol (`messages.js`)

**Files:**
- Modify: `src/protocol/messages.js`
- Test: `src/protocol/__tests__/commands.test.js` and `decode.test.js` (extend)

- [ ] **Step 1: Write the failing tests**

Append to `src/protocol/__tests__/commands.test.js` (match its style):
```js
import { Commands } from '../commands';

describe('renju outbound commands', () => {
  test('renjuSwap frame: type key + fields + auto time:0', () => {
    expect(Commands.renjuSwap({ swap: false, move: 113, player: 'alice', table: 5 }))
      .toEqual({ dsgRenjuTaraguchiSwapTableEvent: { swap: false, move: 113, player: 'alice', table: 5, time: 0 } });
  });
  test('renjuOffer10 frame', () => {
    expect(Commands.renjuOffer10({ moves: [40, 41], player: 'alice', table: 5 }))
      .toEqual({ dsgRenjuTaraguchiOffer10TableEvent: { moves: [40, 41], player: 'alice', table: 5, time: 0 } });
  });
  test('renjuSelect1 frame', () => {
    expect(Commands.renjuSelect1({ move: 57, player: 'bob', table: 5 }))
      .toEqual({ dsgRenjuTaraguchi10Select1TableEvent: { move: 57, player: 'bob', table: 5, time: 0 } });
  });
});
```

Append to `src/protocol/__tests__/decode.test.js`:
```js
import { decode } from '../decode';
const frame = (obj) => decode({ type: 'REDUX_WEBSOCKET::MESSAGE', payload: { message: JSON.stringify(obj) } });

describe('renju inbound decode', () => {
  test('a swap echo decodes to a typed event', () => {
    const r = frame({ dsgRenjuTaraguchiSwapTableEvent: { swap: false, move: 113, player: 'alice', table: 5, time: 123 } });
    expect(r.ok).toBe(true);
    expect(r.event.type).toBe('dsgRenjuTaraguchiSwapTableEvent');
    expect(r.event.payload.move).toBe(113);
  });
  test('an offer10 echo decodes', () => {
    const r = frame({ dsgRenjuTaraguchiOffer10TableEvent: { moves: [1, 2], player: 'a', table: 5, time: 1 } });
    expect(r.ok).toBe(true);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/protocol/__tests__/commands.test.js src/protocol/__tests__/decode.test.js`
Expected: FAIL — `Commands.renjuSwap` undefined; decode reports `{kind:'unknown'}` for the renju types.

- [ ] **Step 3: Implement** — add three entries to `MESSAGES` in `src/protocol/messages.js` (in the `gameplay` section, after `dsgSwap2PassTableEvent`):
```js
  dsgRenjuTaraguchiSwapTableEvent:      { dir: 'both', cmd: 'renjuSwap',    out: ['swap', 'move', 'player', 'table'], req: TBL },
  dsgRenjuTaraguchiOffer10TableEvent:   { dir: 'both', cmd: 'renjuOffer10', out: ['moves', 'player', 'table'],        req: TBL },
  dsgRenjuTaraguchi10Select1TableEvent: { dir: 'both', cmd: 'renjuSelect1', out: ['move', 'player', 'table'],         req: TBL },
```
`INBOUND_TYPES`, `COMMANDS`, and `Commands.*` regenerate automatically from `MESSAGES`. (`time:0` is auto-stamped by `buildCommand`; do not list it in `out`.)

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/protocol/__tests__/commands.test.js src/protocol/__tests__/decode.test.js src/protocol/__tests__/index.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add src/protocol/messages.js src/protocol/__tests__/commands.test.js src/protocol/__tests__/decode.test.js
git commit -m "feat(renju): protocol MESSAGES entries for swap/offer10/select1"
```

---

## Task 9: client-side offer symmetry dedup (UX pre-check)

**Files:**
- Create: `src/game/renjuSymmetry.js`
- Test: `src/game/__tests__/renjuSymmetry.test.js` (create)

Mirrors the server's `RenjuState.offerFifthMove` D4 rejection (guide §8.5). A UX nicety — the server is the authoritative oracle.

- [ ] **Step 1: Write the failing test**
```js
import { describe, test, expect } from 'vitest';
import { d4Images, isSymmetricDup } from '../renjuSymmetry';

describe('renju D4 symmetry (15x15, center (7,7))', () => {
  test('the 8 images of an off-axis point are distinct and include the point', () => {
    const imgs = d4Images(40); // (10,2): dx=3, dy=-5
    expect(new Set(imgs).size).toBe(8);
    expect(imgs).toContain(40);
  });
  test('a center-symmetric counterpart is rejected against an accepted offer', () => {
    // 40 = (10,2) dx=3,dy=-5 ; its 180-degree image (-3,5) = (4,12) = 4 + 12*15 = 184
    expect(isSymmetricDup(184, [40])).toBe(true);
  });
  test('a non-symmetric point is accepted', () => {
    expect(isSymmetricDup(56, [40])).toBe(false); // (11,3) is not in the orbit of (10,2)
  });
  test('on-diagonal points have fewer than 8 distinct images but still self-consistent', () => {
    const imgs = d4Images(112 + 16); // (8,8) on main diagonal: dx=dy=1
    expect(imgs).toContain(112 + 16);
    expect(isSymmetricDup(112 + 16, [112 + 16])).toBe(true);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/game/__tests__/renjuSymmetry.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement** `src/game/renjuSymmetry.js` (algorithm from guide §8.5):
```js
// Client-side D4 (dihedral-8) duplicate check for the Branch-B fifth-move offers, mirroring
// the server's RenjuState.offerFifthMove rejection. The ten offers must contain no two points
// that are equivalent under the board's 8 symmetries about the center. UX pre-check only —
// the server is authoritative. 15x15 board, center (7,7); index = x + y*15. Offers are NOT
// box-constrained (any in-bounds empty non-symmetric point is legal).

const SIZE = 15;
const C = 7;

// The 8 dihedral images of a board point (rotations + reflections about the centre).
export function d4Images(move) {
  const x = move % SIZE, y = Math.floor(move / SIZE);
  const dx = x - C, dy = y - C;
  const orbit = [
    [dx, dy], [-dy, dx], [-dx, -dy], [dy, -dx],   // rotations 0/90/180/270
    [-dx, dy], [dx, -dy], [dy, dx], [-dy, -dx],    // reflections
  ];
  return orbit.map(([tx, ty]) => (tx + C) + (ty + C) * SIZE);
}

// True if `move` collides (under any symmetry) with an already-accepted offer.
export function isSymmetricDup(move, accepted) {
  const acc = new Set(accepted);
  return d4Images(move).some((img) => acc.has(img));
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/game/__tests__/renjuSymmetry.test.js`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**
```bash
git add src/game/renjuSymmetry.js src/game/__tests__/renjuSymmetry.test.js
git commit -m "feat(renju): client-side D4 offer-symmetry dedup (UX pre-check)"
```

---

## Task 10: `TableClass.myRenjuChoice` + `GameClass` board helpers

**Files:**
- Modify: `src/Classes/TableClass.js`, `src/Classes/GameClass.js`
- Test: `src/Classes/__tests__/tableClass.test.js` (extend)

`myRenjuChoice` gates the modal; `GameClass.renjuPhaseNow()` / `renjuBoxRadius()` are read by the Board (Task 12).

- [ ] **Step 1: Write the failing test**

Append to `src/Classes/__tests__/tableClass.test.js` (match its construction style):
```js
import { Game, GameState } from '../GameClass';
import Table from '../TableClass';

function startedRenju(moves, awaitingSwap = true) {
  const g = new Game(); g.setGame(31); g.gameState.state = GameState.State.STARTED;
  moves.forEach((m) => g.addMove(m));
  g.gameState.renjuState.awaitingSwap = awaitingSwap;
  return g;
}

describe('myRenjuChoice + board helpers', () => {
  test('the to-move seated player has a renju choice during a swap window', () => {
    const g = startedRenju([112]); // n=1, awaiting -> player 2 to move
    const t = new Table({ table: 5, initialMinutes: 10 });
    t.seats = [undefined, 'alice', 'bob']; t.me = 'bob'; // bob is seat 2
    expect(t.myRenjuChoice(g)).toBe(true);
    t.me = 'alice';
    expect(t.myRenjuChoice(g)).toBe(false);
  });
  test('renjuBoxRadius: numMoves 1..4 -> that radius; else 0 (whole board)', () => {
    const g = new Game(); g.setGame(31);
    g.moves = [0]; expect(g.renjuBoxRadius()).toBe(1);     // placing move 2 -> 3x3
    g.moves = [0, 0, 0, 0]; expect(g.renjuBoxRadius()).toBe(4); // placing move 5 -> 9x9
    g.moves = [0, 0, 0, 0, 0]; expect(g.renjuBoxRadius()).toBe(0); // move 6 -> anywhere
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/Classes/__tests__/tableClass.test.js`
Expected: FAIL — `myRenjuChoice` / `renjuBoxRadius` not defined.

- [ ] **Step 3: Implement**

`TableClass.js` (next to `mySwap2Choice`):
```js
   myRenjuChoice = (game) => {
      return this.isMyTurn(game) && game.renjuChoice();
   };
```

`GameClass.js` (near `currentColor`; `renjuChoice` was added in Task 4):
```js
   renjuPhaseNow = () => {
      return renjuPhase(this.moves.length, this.gameState.renjuState);
   };
   // Box radius about centre for placing the NEXT stone: numMoves for moves 2-5, else 0
   // (whole board — move 6 and normal play). Mirrors the server's box constraint.
   renjuBoxRadius = () => {
      const n = this.moves.length;
      return (n >= 1 && n <= 4) ? n : 0;
   };
```
…and add `renjuPhase` to the `openingPhase` import.

- [ ] **Step 4: Run to verify it passes**

Run: `npx vitest run src/Classes/__tests__/tableClass.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add src/Classes/TableClass.js src/Classes/GameClass.js src/Classes/__tests__/tableClass.test.js
git commit -m "feat(renju): myRenjuChoice gate + renjuPhaseNow/renjuBoxRadius board helpers"
```

---

## Task 11: `RenjuChoiceModal` decision component

**Files:**
- Create: `src/Components/Table/RenjuChoiceModal.js`
- (No vitest — no DOM in the harness; pure button logic is already tested via `renjuModalButtons` in Task 2. Browser-verified in Task 14.)

- [ ] **Step 1: Implement** (clone of `Swap2ChoiceModal`, button set from `renjuModalButtons`):
```js
import React from 'react';
import PropTypes from 'prop-types';
import {withStyles} from '@mui/styles';
import Typography from '@mui/material/Typography';
import Modal from '@mui/material/Modal';
import Button from '@mui/material/Button';

import {connect} from 'react-redux';
import {send_message} from "../../redux_actions/actionTypes";
import {Commands} from '../../protocol';
import {selectCurrentTable} from '../../selectors';
import {GameState} from '../../game/gameState';
import {renjuModalButtons} from '../../game/openingPhase';
import {renjuBeginPlace, renjuBeginOffer} from '../../ui/renjuOpeningUi';

function getModalStyle() {
   const top = 70, left = 70;
   return {top: `${top}%`, left: `${left}%`, transform: `translate(-${top}%, -${left}%)`};
}
const styles = () => ({
   paper: {position: 'absolute', backgroundColor: 'white', boxShadow: '10px 10px 10px black', padding: '2%', outline: 'none'},
});
const mapStateToProps = state => ({game: state.game, table: selectCurrentTable(state), renjuUi: state.renjuOpeningUi});
const mapDispatchToProps = dispatch => ({
   send_message: m => dispatch(send_message(m)),
   beginPlace: () => dispatch(renjuBeginPlace()),
   beginOffer: () => dispatch(renjuBeginOffer()),
});

const UnconnectedRenjuChoiceModal = (props) => {
   const {classes, table, game, renjuUi} = props;
   const started = game.gameState.state === GameState.State.STARTED;
   const n = game.moves.length;
   const buttons = renjuModalButtons(n, game.gameState.renjuState, started);
   // open only when it's my swap/branch choice AND no board interaction is armed
   const open = table.myRenjuChoice(game) && renjuUi.mode === 'idle';

   const takeOver = () => props.send_message(Commands.renjuSwap({swap: true, move: 0, player: table.me, table: table.table}));
   const decline = () => {
      if (n === 5) {
         // window-5 decline is BARE (no bundled stone); move 6 follows as a normal move
         props.send_message(Commands.renjuSwap({swap: false, move: 0, player: table.me, table: table.table}));
      } else {
         props.beginPlace(); // arm the board for the box-constrained decline/Branch-A stone
      }
   };
   const offer = () => props.beginOffer(); // arm the board for the 10-pick

   return (
      <div>
         <Modal aria-labelledby="renju-modal-title" open={open}>
            <div style={getModalStyle()} className={classes.paper}>
               <Typography variant="h6" id="renju-modal-title">Taraguchi opening — your choice:</Typography>
               {buttons.swap && <Button onClick={takeOver}>Take over (swap sides)</Button>}
               {buttons.declinePlace && <Button onClick={decline}>{n === 5 ? 'Decline swap' : (n === 4 ? 'Place 5th move' : 'Decline & place')}</Button>}
               {buttons.offer10 && <Button onClick={offer}>Offer 10 fifth-moves</Button>}
            </div>
         </Modal>
      </div>
   );
};
UnconnectedRenjuChoiceModal.propTypes = {classes: PropTypes.object.isRequired};

const RenjuChoiceModal = connect(mapStateToProps, mapDispatchToProps)(withStyles(styles)(UnconnectedRenjuChoiceModal));
export default RenjuChoiceModal;
```

- [ ] **Step 2: Build sanity**

Run: `npm run build`
Expected: build succeeds (no import/JSX errors). (Behavior is verified in Task 14.)

- [ ] **Step 3: Commit**
```bash
git add src/Components/Table/RenjuChoiceModal.js
git commit -m "feat(renju): RenjuChoiceModal decision component (swap/decline/offer10)"
```

---

## Task 12: `Board.js` — placing / offering / selection wiring + box highlight + translucent candidates

**Files:**
- Modify: `src/Components/Board/Board.js`, `src/Components/Board/BoardSquare.js`
- (No vitest — browser-verified in Task 14.)

- [ ] **Step 1: Implement Board.js**

(a) Extend `mapStateToProps` to read the UI slice; add the renju dispatchers to `mapDispatchToProps`:
```js
const mapStateToProps = state => {
   const table = selectCurrentTable(state);
   return { game_id: table.game, game: state.game, table, renjuUi: state.renjuOpeningUi };
};
// in mapDispatchToProps:
   togglePick: move => dispatch(renjuTogglePick(move)),
   resetRenjuUi: () => dispatch(renjuResetOpeningUi()),
```
(imports: `import {renjuTogglePick, renjuResetOpeningUi} from '../../ui/renjuOpeningUi';` and `import {RenjuPhase} from '../../game/openingPhase';`).

(b) Add renju dispatch helpers next to `sendMove`:
```js
   const sendRenjuDecline = (move) => {
      send_message(Commands.renjuSwap({swap: false, move, player: table.me, table: table.table}));
      props.resetRenjuUi();
   };
   const sendRenjuSelect = (move) => {
      send_message(Commands.renjuSelect1({move, player: table.me, table: table.table}));
   };
```

(c) In `makeBoard`, compute the renju context once (before the cell loop):
```js
   const isRenju = game.isRenjuGame && game.isRenjuGame();
   const renjuPhaseNow = isRenju ? game.renjuPhaseNow() : null;
   const boxRadius = isRenju ? game.renjuBoxRadius() : 0;
   const center = 7; // 15x15
   const inBox = (m) => {
      if (boxRadius === 0) return true;
      const x = m % 15, y = Math.floor(m / 15);
      return Math.abs(x - center) <= boxRadius && Math.abs(y - center) <= boxRadius;
   };
   const picks = (isRenju && renjuUi.mode === 'offering') ? renjuUi.picks : [];
   const offers = (isRenju && renjuPhaseNow === RenjuPhase.SELECTION) ? game.gameState.renjuState.offered : [];
```

(d) Replace the per-cell `clickHandler` assignment with a renju-aware version. The cell index is `m` (the existing loop variable used in `board.push`). Inside the `if (myTurn)` block, branch on renju mode/phase BEFORE the existing default:
```js
            let clickHandler = undefined;
            if (myTurn) {
               const empty = game.abstractBoard[i][j] === 0;
               if (isRenju && renjuUi.mode === 'placing') {
                  if (empty && inBox(m)) clickHandler = () => sendRenjuDecline(m);
               } else if (isRenju && renjuUi.mode === 'offering') {
                  if (empty && !isSymmetricDup(m, renjuUi.picks)) clickHandler = () => togglePick(m);
               } else if (isRenju && renjuPhaseNow === RenjuPhase.SELECTION) {
                  if (offers.includes(m)) clickHandler = () => sendRenjuSelect(m);
               } else if (isRenju && (renjuPhaseNow === RenjuPhase.SWAP || renjuPhaseNow === RenjuPhase.BRANCH)) {
                  // a decision modal is up; the board is inert
                  clickHandler = undefined;
               } else if (empty && (!isRenju || inBox(m))) {
                  // normal move (incl. renju MOVE/COMPLETE, box-constrained for opening moves 2-5)
                  clickHandler = sendMove;
               }
               // (keep the existing Go MARK_STONES branch AFTER this, unchanged)
            }
```
(`isSymmetricDup` import: `import {isSymmetricDup} from '../../game/renjuSymmetry';`. The `() => sendRenjuDecline(m)` / `() => togglePick(m)` close over the loop index — ensure `m` is `const`/`let` block-scoped per-iteration; if the loop uses `var`, capture `const cell = m;` first.)

(e) Box highlight + translucent candidates — set per-cell flags after the board array is built (mirror the `deadStone`/`last_move` blocks):
```js
   if (isRenju) {
      const blackTranslucent = player_colors[2]; // 'black-stone-gradient'
      picks.forEach((s) => { if (board[s]) board[s].deadStone = blackTranslucent; });
      offers.forEach((s) => { if (board[s]) board[s].deadStone = blackTranslucent; });
      // highlight the legal box during a placing/MOVE opening placement
      const highlight = (renjuUi.mode === 'placing') ||
         (renjuPhaseNow === RenjuPhase.MOVE && boxRadius > 0);
      if (highlight) {
         for (let s = 0; s < board.length; s++) {
            const x = s % 15, y = Math.floor(s / 15);
            if (Math.abs(x - 7) <= boxRadius && Math.abs(y - 7) <= boxRadius) board[s].renjuBox = true;
         }
      }
   }
```

- [ ] **Step 2: Implement BoardSquare.js** — render the box-highlight flag (mirror the `last_move` render, lines around the `LastMove` call):
```js
   {this.props.renjuBox && (
      <rect x={1} y={1} width={8} height={8} fill={"none"} stroke={"#3aa3ff"} strokeWidth={0.6} pointerEvents={'none'}/>
   )}
```
…and pass `renjuBox` from the cell descriptor where the other flags (`deadStone`, `last_move`) are threaded into `<BoardSquare …/>` (add `renjuBox={square.renjuBox}` alongside `deadStone={square.deadStone}`). Grep BoardSquare's prop list / the Board render map to match the existing wiring.

- [ ] **Step 3: Build sanity**

Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 4: Commit**
```bash
git add src/Components/Board/Board.js src/Components/Board/BoardSquare.js
git commit -m "feat(renju): board placing/offering/selection wiring + box highlight + candidates"
```

---

## Task 13: mount the modal + the offer-10 counter/submit panel

**Files:**
- Modify: `src/Pages/Table.js`
- Create: `src/Components/Table/RenjuOfferPanel.js`

The offer panel shows `n/10` and a submit button during `offering` mode (the board handles the picking).

- [ ] **Step 1: Implement `RenjuOfferPanel.js`**:
```js
import React from 'react';
import {connect} from 'react-redux';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import {send_message} from "../../redux_actions/actionTypes";
import {Commands} from '../../protocol';
import {selectCurrentTable} from '../../selectors';
import {renjuResetOpeningUi} from '../../ui/renjuOpeningUi';

const mapStateToProps = state => ({table: selectCurrentTable(state), renjuUi: state.renjuOpeningUi});
const mapDispatchToProps = dispatch => ({
   send_message: m => dispatch(send_message(m)),
   reset: () => dispatch(renjuResetOpeningUi()),
});

const UnconnectedRenjuOfferPanel = ({table, renjuUi, send_message, reset}) => {
   if (renjuUi.mode !== 'offering') return null;
   const n = renjuUi.picks.length;
   const submit = () => {
      if (n !== 10) { alert('Select exactly 10 distinct, non-symmetric fifth-move candidates.'); return; }
      send_message(Commands.renjuOffer10({moves: renjuUi.picks, player: table.me, table: table.table}));
      reset();
   };
   return (
      <div style={{padding: '0.5rem'}}>
         <Typography variant="subtitle1">Offer fifth moves: {n}/10</Typography>
         <Button variant="contained" disabled={n !== 10} onClick={submit}>Submit offers</Button>
         <Button onClick={reset}>Cancel</Button>
      </div>
   );
};
export default connect(mapStateToProps, mapDispatchToProps)(UnconnectedRenjuOfferPanel);
```

- [ ] **Step 2: Mount in `Table.js`** — add imports next to the `Swap2ChoiceModal` import:
```js
import RenjuChoiceModal from "../Components/Table/RenjuChoiceModal";
import RenjuOfferPanel from "../Components/Table/RenjuOfferPanel";
```
…and mount next to `<Swap2ChoiceModal/>`:
```jsx
                  <DPenteChoiceModal/>
                  <Swap2ChoiceModal/>
                  <RenjuChoiceModal/>
                  <RenjuOfferPanel/>
```
(Place `<RenjuOfferPanel/>` where a transient panel reads well near the board; if the modal region is overlay-only, mount the panel in the board column instead — match the existing layout grid.)

- [ ] **Step 3: Build sanity**

Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 4: Commit**
```bash
git add src/Pages/Table.js src/Components/Table/RenjuOfferPanel.js
git commit -m "feat(renju): mount RenjuChoiceModal + offer-10 counter/submit panel"
```

---

## Task 14: browser integration verification (Playwright MCP, live backend)

**Files:** none (verification; capture findings as commit notes / a short `docs/` log if useful).

No e2e suite is committed (matching the repo). Verify against the running dev server + the production-grade backend on localhost using the Playwright MCP browser tools. Backend is already built/committed for sub-projects 1–2; if a backend rebuild is ever needed: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; ./justCompile && docker restart penteorg-pente.org-1`. Test creds and the WS handshake are in project memory (`iostest`/`app_tsetsoi`, `graviton`/`virginia`).

- [ ] **Step 1: Start the client**

Run (background): `npm start` and note the dev URL.

- [ ] **Step 2: Create a Renju table + two players**

Drive two browser contexts (two logins). Create a **timed** Renju (game 31) table (non-zero time control — required by the server), sit both, start. Confirm the board renders 15×15 with the 9 star points and the auto-center black stone (value 2).

- [ ] **Step 3: Branch A walkthrough**

Through windows 1–4: confirm `RenjuChoiceModal` shows the right buttons (windows 1–3: Take-over + Decline&place; window 4: + Offer 10). Decline & place into the highlighted box each time; confirm the stone lands (via the move echo, not locally) and alternates black/white. At move 4 choose Branch A (Place 5th in the 9×9). At window 5 Decline; place move 6 anywhere; confirm `COMPLETE` (normal play, no more prompts). Verify a too-far click is refused (box highlight + server INVALID_MOVE to the sender only).

- [ ] **Step 4: Branch B walkthrough**

Fresh table. At move 4 choose **Offer 10**: confirm the board enters multi-select, translucent black candidates appear, the `n/10` counter increments, a D4-duplicate pick is refused client-side, and Submit (exactly 10) sends the offer. As the opponent (white), confirm the ten translucent candidates render and tapping one selects it (move 5 placed via the move echo); then white plays move 6 directly (no window 5).

- [ ] **Step 5: Rejoin verification**

Mid-opening at each decision point (open window; resolved→MOVE/BRANCH; Branch-B offers pending; Branch-B selected): reload one client (observer **and** seated). Confirm seats come from `sendPlayingPlayers` and the phase/UI reconstructs correctly from the single decision-point signal — open window → modal; resolved → board armed; offers pending → candidates; selected → move 6.

- [ ] **Step 6: Record outcome**

Note pass/fail per step. File any bug as a `systematic-debugging` follow-up (root-cause first). No commit unless code changed.

---

## Task 15: Final review + finish the branch

- [ ] **Step 1: Full suite green**

Run: `npm test`
Expected: all suites pass (existing + new renju suites).

- [ ] **Step 2: Final code review**

Dispatch a code-reviewer over the whole `renju` branch diff (correctness vs the spec/§8 contract, the per-phase wire table, no-stone-from-echo invariant, `newInstance` deep-copy of `renjuState`).

- [ ] **Step 3: Finish**

Use superpowers:finishing-a-development-branch. Do not push or open a PR unless the user asks (project rule: commit/push only when asked).

---

## Self-review notes (author)

- **Spec coverage:** geometry/picker (T3), variant rules + black-first replay/colors + opening player (T4), pure phase classifier + predicates (T2), tracking record (T1), transient slice (T5), inbound reducers + rejoin advance (T6), handler/slice wiring (T7), outbound protocol (T8), symmetry dedup (T9), modal gate + board helpers (T10), modal (T11), board interaction (T12), mount + offer panel (T13), browser verify (T14), finish (T15). All §8.3 steps and spec §4 surfaces are covered.
- **Harness reality:** components (T11–T13) have no vitest (no jsdom/RTL); their pure logic is extracted and unit-tested (`renjuModalButtons`, `renjuBoxRadius`, the reducers). Browser behavior is T14 via MCP, not a committed e2e suite (YAGNI; matches repo).
- **Cross-task type consistency:** `game.gameState.renjuState` shape (T1) is the single record read by T2/T4/T6/T10/T12; `renjuOpeningUi {mode,picks}` (T5) is read by T11/T12/T13; `Commands.renjuSwap/renjuOffer10/renjuSelect1` (T8) names match the dispatch sites (T11/T12/T13) and the per-phase wire table.
- **Watch-items:** `isRenjuGame()` public accessor (used by reducers/board) added in T4; `newInstance()` must deep-copy `renjuState.offered` (T4 step 3f); the move-4 `swap=false` tracking and the bare window-5 decline are the two easiest places to get the wire wrong (T6/T11 encode them explicitly).
