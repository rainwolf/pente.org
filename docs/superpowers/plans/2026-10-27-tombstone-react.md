# Tombstone Pente — React Live Game Room Implementation Plan (Plan D of 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play Tombstone Pente (ids 33 / 34) in the React live game room: gray dead stones, the two-placement turn (own stone or own pass, then dead stone or skip), the reused PASS button with its two labels, whole-turn undo, per-turn move list, and game selectors that list only the games the current server offers (so only Tombstone on the new Experimental server, and no Tombstone anywhere else).

**Architecture:** One pure turn model (`src/game/tombstoneTurn.js`) decodes codes and derives turns, phase, buttons, labels, undo seat and history stepping from the flat move list plus one flag (`trailingClosed`). `GameClass` delegates every Tombstone decision to it and never computes `% 19` on a raw code. The reducer keeps `trailingClosed` in `game.gameState.tombstone`; a transient `tombstoneUi` slice (cloned from `ui/renjuOpeningUi.js`) blocks double sends until the server echoes. Components only read these.

**Tech Stack:** React 18.3 + MUI 5, Redux 4, Vitest 4.

**Repo:** `/Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room`, branch `feat/tombstone-pente` (already checked out). All paths below are relative to its `src/` unless they start with `pente.org/`. Commits go to THIS repo. The repo has `commit.gpgsign=true`, so a plain `git commit -m` signs; use plain messages, no co-author or generated-by lines.

**Spec:** `pente.org/docs/superpowers/specs/2026-10-27-tombstone-pente-design.md` — §13 is this plan's scope; §8.1 and §16.3 are the wire contract with Plan B; §3 and §5.3 are the rules and whose-turn formula.

**Ordering with the other plans:** Plan A (game core) lands first in `pente.org` because B and C compile against it. This plan has **no code dependency** on A, B or C: it depends only on the wire contract (§16.3) and can be implemented and unit-tested in parallel. Only Task 7 (end-to-end smoke test) needs Plans A and B merged on `feat/tombstone-pente` in `pente.org` and running on the local server.

## Global Constraints

- Ids: `TOMBSTONE = 33`, `SPEED_TOMBSTONE = 34`, `TB_TOMBSTONE = 83` (83 is never shown live). `isTombstone(id)` is true for exactly these three.
- Codes: own stone `0..360` (`x + 19*y`); dead stone `361 + point` (`361..721`); own pass `722`. Dead-stone passes are never stored. Constants `TOMBSTONE_DEAD_OFFSET = 361`, `TOMBSTONE_OWN_PASS = 722`, `DEAD_CELL = 3`.
- **No Tombstone code path may treat `gridSize*gridSize` (= 361 on 19x19) as a pass.** 361 is a dead stone on A19. Every Tombstone branch must come before the Go/Renju pass branches.
- Turn 0 = P1's centre stone (`180`), never a dead stone. Turn `t >= 1` belongs to P2 when `t` is odd, P1 when even. Each turn after the first is one own entry (`0..360` or `722`) optionally followed by one dead entry.
- Live frames carry ONE code each (`moves: [code]`); a 2-code live frame is never sent (`redux_reducers/utils.js:206` would read it as a bulk replay).
- Button copy, exact: **Place only a dead stone** (sends `722`; the same wording as the website TB button), **Skip dead stone** (sends `dsgTombstoneSkipDeadTableEvent`). MUI uppercases them like the existing PASS button.
- Live servers (spec §2, §7.2, §13.1): Tombstone is offered only by the server **"Experimental"** (id `47`, port `16004`), which offers only `33`/`34`. The room's server list (`Pages/Servers.js:40-56`, from `/gameServer/activeServers`) already lists every running server, and clicking one connects to `/websocketServer/<port>` (`Servers.js:14`, `redux_actions/actionTypes.js:49-57`), so showing and entering Experimental needs **no code change**; Task 7 checks it.
- Wire names, exact: `dsgTombstoneSkipDeadTableEvent` (`cmd: 'tombstoneSkipDead'`, out `['player', 'table']`, inbound adds `silent`), `dsgTombstoneSkipDeadTableErrorEvent` (inbound, `player`, `table`, `error`; `NOT_AWAITING_DEAD_STONE = 26`).
- Colours: board fill and lobby card `#D7BDE2`; dead stone gradient `#c8c8c8` → `#6e6e6e`, element id `gray-stone-gradient`.
- Variant key `'tombstone-pente'`; display name `'Tombstone Pente'` (`game_name(34)` = `'Speed Tombstone Pente'`).
- Rated Tombstone has no tournament rule (`postRule: 'none'`); captures count like Pente (`gameHasCaptures` true, default `critical_captures` threshold).
- Tests: `cd /Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room && TMPDIR=/private/tmp/claude-501/tombstone-react npx vitest run` (create the TMPDIR first with `mkdir -p`). Baseline on `feat/tombstone-pente` before any change: **36 files, 589 tests passed** (recorded 2026-10-27). Every task ends with the whole suite green and the count only going up.
- Scratch files go under `/private/tmp/claude-501/tombstone-react`, never in the repo. Before the final commit, `git status --porcelain | grep '^??'` lists only files this plan creates.
- Not in scope: mobile apps, server code, beta gating, AI, KOTH, tournaments, draw offers.

## Decisions this plan adds (the spec leaves them open)

| # | Decision | Reason |
|---|---|---|
| P1 | `tombstoneButton` returns `null` while `moves.length === 0`. | At game start P1 is "on turn" until the server's auto-centre echo arrives. A "Place only a dead stone" press then would send `722` on turn 0, which the server rejects (spec §5.5: only `180` on turn 0). |
| P2 | `decodeTombstone` returns `null` for any code outside `0..722`; replay and parsing skip `null` entries. | The server never sends one; skipping keeps a corrupt frame from throwing inside a reducer and blanking the UI. |
| P3 | `parseTombstoneTurns` ignores a dead entry that has no open turn (previous turn already has a dead stone, or the list starts with one). | Same: malformed input only. |
| P4 | `tombstoneUi.pending` is owned entirely by the slice (set by an explicit action, cleared by `ADVANCING_EVENTS`). `addMove` does not touch it. | One owner, as in the Renju precedent. Spec §13.5's "both clear pending" is satisfied because `dsgMoveTableEvent` is an advancing event. |
| P5 | The Settings/Arena selectors list a standard id `g` iff the login frame's `serverData.gameEvents[].game` contains `g` or `g + 1` (its speed id). Without a `gameEvents` array they list `STANDARD_GAME_IDS` minus `33`. | User decision: the picker offers only the games the current server offers. Today both pickers map `STANDARD_GAME_IDS` unconditionally (`SettingsModal.js:128`, `CreateArenaTableModal.js:126`). Confirmed: the captured login frame has `serverData.gameEvents[].game` (`protocol/__fixtures__/wire-fixtures.json:411`; sent with the login event, `pente.org/dsg_src/java/org/pente/gameServer/server/ServerPlayer.java:222`). The no-array fallback is every pre-existing server's set, and the server-offering rule (spec §7.3) rejects anything unoffered either way. Side effect, intended: the Go server's picker shows only the three Go boards. |
| P6 | The two new fixture frames are synthesized from the §16.3 contract, not captured. Task 7 replaces them with captured frames if the real ones differ. | Plan B is not running yet when Task 3 is done. |

## Review Focus

1. **A dead stone on A19 (code `361`)** equals `gridSize*gridSize`. `last_move`, `moves_strings` and the move list must show it as a dead stone at A19, not as PASS or "no marker". Pinned in Task 4 (`last_move marks A19 for code 361`, `moves_strings labels 361 as dead A19`).
2. **Game start before the centre echo** (`moves = []`): no Tombstone button, and the board click is the normal own-stone path. Pinned in Task 2 (`no button before the centre stone`).
3. **Rejoin mid-turn**: a bulk list ending in an own entry beyond the centre must open the DEAD phase; a following silent skip must close it without a sound; a bulk list of only `[180]` must be P2's OWN phase. Pinned in Task 5.
4. **A rejected action** (move error or skip error) must re-enable the board and button. Pinned in Task 5 (`pending cleared by every ADVANCING_EVENTS entry`).
5. **Browsing history** (`until < moves.length`) across a partial trailing turn and across an undo: stepping lands on turn ends and never inside a turn; `undoTurn` while browsing keeps `until <= moves.length`. Pinned in Task 2 (`tombstoneStepTarget` cases) and Task 4 (`undoTurn while browsing`).

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `game/boardGeometry.js` | modify | `isTombstone`, `variantKey` arm, `STANDARD_GAME_IDS += 33`, `selectableGameIds(serverGameIds)` |
| `game/tombstoneTurn.js` | **create** | pure turn model: decode, turns, phase, entry player, button, labels, undo seat, step target |
| `Classes/utils.js`, `Classes/TableClass.js`, `App.css` | modify | name, lobby colour, board fill + gray gradient; `canUndo` Tombstone arm |
| `protocol/messages.js`, `protocol/__fixtures__/wire-fixtures.json` | modify | skip event descriptor, error event, fixtures |
| `Classes/GameClass.js` | modify | Tombstone engine: rules row, replay/add, current player/colour, last move, move strings/cells, stepping, `undoTurn`, `tombstone` game state |
| `ui/tombstoneUi.js` | **create** | `{pending}` slice |
| `redux_reducers/utils.js`, `redux_reducers/rootReducer.js`, `selectors/index.js` | modify | `trailingClosed` tracking, skip handler, undo, slice wiring, `serverGameIds`, `selectSelectableGameIds` |
| `Components/Board/Board.js`, `Components/Table/GameInfoPanel.js`, `Components/Table/MovesListPanel.js`, `Components/Table/SettingsModal.js`, `Components/Arena/CreateArenaTableModal.js` | modify | gray stones and hover, phase-aware click, button, per-turn list, filtered selectors |

### Task 1: Identity — ids, variant key, name, colours, selectable ids

**Files:**
- Modify: `game/boardGeometry.js:27-51` (`variantKey`, `STANDARD_GAME_IDS`), append `isTombstone`, `selectableGameIds`
- Modify: `Classes/utils.js:7-21` (`VARIANT_NAMES`)
- Modify: `Classes/TableClass.js:10-25` (`VARIANT_COLORS`)
- Modify: `App.css` (after `#red-stone-gradient`, `:67-70`; after `.renju`, `:124-126`)
- Test: `game/__tests__/boardGeometry.test.js` (update `:5-13`, append), `Classes/__tests__/utils.test.js` (extend `EXPECTED`), `Classes/__tests__/tableClass.test.js` (extend `cases` `:43-47` and the `1..32` loop `:55`)

**Interfaces:**
- Produces: `isTombstone(gameId: number): boolean` (true for 33, 34, 83); `variantKey(33|34|83) === 'tombstone-pente'`; `STANDARD_GAME_IDS` ends `..., 31, 33]`; `selectableGameIds(serverGameIds: number[] | undefined): number[]`.

- [ ] **Step 1: Write the failing tests**

In `boardGeometry.test.js`, add `isTombstone, selectableGameIds` to the import at `:2`, change the pinned list (`:6`, now ending `31, 33`), length (`:7`, 17) and variant-key count (`:12`, 15), and append:

```javascript
describe('Tombstone identity', () => {
  test('isTombstone is true exactly for 33, 34, 83', () => {
    expect([33, 34, 83].every(isTombstone)).toBe(true);
    expect([1, 31, 32, 35, 81, 82, 84, undefined].some(isTombstone)).toBe(false);
  });
  test('variantKey maps 33/34/83 to tombstone-pente, renju and swap2-keryo unchanged', () => {
    expect(variantKey(33)).toBe('tombstone-pente');
    expect(variantKey(34)).toBe('tombstone-pente');
    expect(variantKey(83)).toBe('tombstone-pente');
    expect(variantKey(31)).toBe('renju');
    expect(variantKey(30)).toBe('swap2-keryo');
  });
  test('Tombstone is 19x19 with the non-go circles', () => {
    expect(gridSizeForGame(33)).toBe(19);
    expect(boardSpecialPoints(33)).toEqual(boardSpecialPoints(1));
  });
});

describe('selectableGameIds — only the games the current server offers', () => {
  const OLD_16 = STANDARD_GAME_IDS.filter((g) => g !== 33);
  const MAIN_ROOM = [];                       // LIVE_GAMES: ids 1..32
  for (let g = 1; g <= 32; g++) MAIN_ROOM.push(g);
  test('Experimental (33, 34) -> only Tombstone', () => {
    expect(selectableGameIds([33, 34])).toEqual([33]);
  });
  test('a speed id alone still offers its standard id', () => {
    expect(selectableGameIds([34])).toEqual([33]);
  });
  test('Main Room / Beginners / KOTH / Arena (1..32) -> the 16 old ids, no Tombstone', () => {
    expect(selectableGameIds(MAIN_ROOM)).toEqual(OLD_16);
  });
  test('Go server -> only the three Go boards', () => {
    expect(selectableGameIds([19, 20, 21, 22, 23, 24])).toEqual([19, 21, 23]);
  });
  test('no server list -> the 16 old ids, no Tombstone', () => {
    expect(selectableGameIds(undefined)).toEqual(OLD_16);
  });
  test('same server list -> same array reference (no modal re-render per action)', () => {
    const ids = [33, 34];
    expect(selectableGameIds(ids)).toBe(selectableGameIds(ids));
  });
});
```

In `utils.test.js` add `33: 'Tombstone Pente', 34: 'Speed Tombstone Pente'` to `EXPECTED`. In `tableClass.test.js` add `[33, '#D7BDE2'], [34, '#D7BDE2'], [83, '#D7BDE2']` to `cases`, change the loop to `g <= 34`, and assert `tableForGame(33).gameHasCaptures()` is `true`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/game src/Classes`
Expected: FAIL — `isTombstone` / `selectableGameIds` are not exported; `variantKey(33)` is `'swap2-keryo'`, so `game_name(33)` is `'Swap2-Keryo'` and the `EXPECTED` entry fails on that string.

- [ ] **Step 3: Implement**

- `boardGeometry.js`: `export function isTombstone(gameId)` for `33 || 34 || 83`. In `variantKey`, insert `if (isTombstone(gameId)) return 'tombstone-pente';` directly before `return 'swap2-keryo';` (`:41`). Append `33` to `STANDARD_GAME_IDS` and update its comment count. `selectableGameIds(serverGameIds)`: when `serverGameIds` is an array, return `STANDARD_GAME_IDS.filter((g) => serverGameIds.includes(g) || serverGameIds.includes(g + 1))`; otherwise `STANDARD_GAME_IDS` without `33`. Memoise on the `serverGameIds` reference (a module-level last-input/last-output pair) so the modals' `mapStateToProps` gets the same array on every store update and `connect` does not re-render them.
- `Classes/utils.js`: `'tombstone-pente': 'Tombstone Pente'`.
- `TableClass.js`: `'tombstone-pente': '#D7BDE2'`.
- `App.css`: `#gray-stone-gradient { --color1: #c8c8c8; --color2: #6e6e6e; }` after `#red-stone-gradient`, and `.tombstone-pente { fill: #D7BDE2 }` after `.renju`.

- [ ] **Step 4: Run the full suite**

Run: `npx vitest run`
Expected: PASS, 36 files, more than 589 tests. `replayDispatch.test.js` loops ids `1..30` only and stays green.

- [ ] **Step 5: Commit**

```bash
git add src/game/boardGeometry.js src/game/__tests__/boardGeometry.test.js src/Classes/utils.js src/Classes/TableClass.js src/Classes/__tests__/utils.test.js src/Classes/__tests__/tableClass.test.js src/App.css
git commit -m "Register Tombstone Pente ids, name and colours"
```

---

### Task 2: Pure turn model — `game/tombstoneTurn.js`

**Files:**
- Create: `game/tombstoneTurn.js`
- Test: `game/__tests__/tombstoneTurn.test.js`

**Interfaces:**
- Consumes: nothing (pure, no imports).
- Produces (every later task uses these names):

```javascript
export const TOMBSTONE_DEAD_OFFSET = 361, TOMBSTONE_OWN_PASS = 722, DEAD_CELL = 3;
export function decodeTombstone(code)
  // 0..360 -> {kind:'own', point:code}; 361..721 -> {kind:'dead', point:code-361};
  // 722 -> {kind:'pass', point:-1}; anything else -> null (P2)
export function isOwnEntry(code)                  // own stone or own pass
export function turnPlayer(t)                     // t % 2 === 1 ? 2 : 1  (turn 0 -> 1)
export function parseTombstoneTurns(moves)        // [{player, own, dead /*code|-1*/, start, end}]
export function tombstoneEntryPlayer(moves, i)    // player who made entry i
export function tombstonePhase(moves, trailingClosed)  // {player: 1|2, phase: 'OWN'|'DEAD'}
export function tombstoneButton(moves, trailingClosed, pending)
  // null | {label:'Place only a dead stone', action:'ownPass'} | {label:'Skip dead stone', action:'skipDead'}
export function tombstoneTurnLabel(turn, coord)   // coord: point -> 'K10'
export function tombstoneUndoSeat(moves, trailingClosed)  // seat (1|2) that may request undo, or 0
export function tombstoneStepTarget(moves, until, forward) // new `until` for history stepping
```

Rules the bodies must follow (spec §5.3, §13.2):
- `parseTombstoneTurns`: entry 0 is turn 0 (player 1). Each later own entry starts the next turn; a dead entry joins the open turn if that turn has `dead === -1`, else it is ignored (P3). `null`-decoding entries are ignored. `start`/`end` are entry indexes (inclusive).
- `tombstoneEntryPlayer(moves, i)`: `turnPlayer(ownCount - 1)` where `ownCount` counts own entries in `moves[0..i]`.
- `tombstonePhase`: `moves.length === 0` → `{player: 1, phase: 'OWN'}`. Last entry is an own entry at index `> 0` and `!trailingClosed` → `{player: <that turn's player>, phase: 'DEAD'}`. Otherwise `{player: ownCount % 2 + 1, phase: 'OWN'}`.
- `tombstoneButton`: `null` if `pending` or `moves.length === 0` (P1); OWN → ownPass; DEAD → skipDead.
- `tombstoneTurnLabel`: own part `'PASS'` for `722` else `coord(own)`; dead part `' + dead ' + coord(dead - 361)` when `dead !== -1`.
- `tombstoneUndoSeat`: `0` unless phase is OWN and there are more than 1 turns; then the last turn's player.
- `tombstoneStepTarget`: forward from `until < moves.length` → `end + 1` of the turn containing entry `until`; back from `until > 1` → `start` of the turn containing entry `until - 1`, but never below `1`; otherwise `until` unchanged.

- [ ] **Step 1: Write the failing tests**

```javascript
import { describe, test, expect } from 'vitest';
import {
  decodeTombstone, isOwnEntry, turnPlayer, parseTombstoneTurns, tombstoneEntryPlayer,
  tombstonePhase, tombstoneButton, tombstoneTurnLabel, tombstoneUndoSeat, tombstoneStepTarget,
} from '../tombstoneTurn';

const coord = (p) => 'ABCDEFGHJKLMNOPQRST'[p % 19] + (19 - Math.floor(p / 19));
// centre; P2 own 100 + dead on 0 (361); P1 pass + dead 50 (411); P2 own 200 only; P1 pass only
const H = [180, 100, 361, 722, 411, 200, 722];

describe('decodeTombstone', () => {
  test('own, dead (incl. A19 = 361), pass, invalid', () => {
    expect(decodeTombstone(0)).toEqual({ kind: 'own', point: 0 });
    expect(decodeTombstone(360)).toEqual({ kind: 'own', point: 360 });
    expect(decodeTombstone(361)).toEqual({ kind: 'dead', point: 0 });
    expect(decodeTombstone(721)).toEqual({ kind: 'dead', point: 360 });
    expect(decodeTombstone(722)).toEqual({ kind: 'pass', point: -1 });
    expect(decodeTombstone(723)).toBeNull();
    expect(decodeTombstone(-1)).toBeNull();
    expect(isOwnEntry(722)).toBe(true);
    expect(isOwnEntry(361)).toBe(false);
  });
});

describe('parseTombstoneTurns / entry player', () => {
  test('mixed history', () => {
    expect(parseTombstoneTurns(H)).toEqual([
      { player: 1, own: 180, dead: -1, start: 0, end: 0 },
      { player: 2, own: 100, dead: 361, start: 1, end: 2 },
      { player: 1, own: 722, dead: 411, start: 3, end: 4 },
      { player: 2, own: 200, dead: -1, start: 5, end: 5 },
      { player: 1, own: 722, dead: -1, start: 6, end: 6 },
    ]);
    expect(H.map((_, i) => tombstoneEntryPlayer(H, i))).toEqual([1, 2, 2, 1, 1, 2, 1]);
    expect([0, 1, 2, 3].map(turnPlayer)).toEqual([1, 2, 1, 2]);
  });
  test('a second dead entry in one turn is ignored', () => {
    expect(parseTombstoneTurns([180, 100, 361, 362])).toHaveLength(2);
    expect(parseTombstoneTurns([180, 100, 361, 362])[1].end).toBe(2);
  });
});

describe('tombstonePhase / tombstoneButton', () => {
  test('no button before the centre stone', () => {
    expect(tombstonePhase([], true)).toEqual({ player: 1, phase: 'OWN' });
    expect(tombstoneButton([], true, false)).toBeNull();
  });
  test('after the centre: P2 OWN, button = Place only a dead stone', () => {
    expect(tombstonePhase([180], true)).toEqual({ player: 2, phase: 'OWN' });
    expect(tombstoneButton([180], true, false)).toEqual({ label: 'Place only a dead stone', action: 'ownPass' });
  });
  test('own entry open -> DEAD phase of the same player, button = Skip dead stone', () => {
    expect(tombstonePhase([180, 100], false)).toEqual({ player: 2, phase: 'DEAD' });
    expect(tombstonePhase([180, 722], false)).toEqual({ player: 2, phase: 'DEAD' });
    expect(tombstoneButton([180, 100], false, false)).toEqual({ label: 'Skip dead stone', action: 'skipDead' });
  });
  test('own entry closed by skip -> next player OWN', () => {
    expect(tombstonePhase([180, 100], true)).toEqual({ player: 1, phase: 'OWN' });
  });
  test('dead entry closes the turn', () => {
    expect(tombstonePhase([180, 100, 361], true)).toEqual({ player: 1, phase: 'OWN' });
  });
  test('pending hides the button', () => {
    expect(tombstoneButton([180], true, true)).toBeNull();
  });
});

describe('tombstoneTurnLabel', () => {
  test('the four shapes', () => {
    const t = parseTombstoneTurns(H);
    expect(tombstoneTurnLabel(t[0], coord)).toBe('K10');
    expect(tombstoneTurnLabel(t[1], coord)).toBe(coord(100) + ' + dead A19');
    expect(tombstoneTurnLabel(t[2], coord)).toBe('PASS + dead ' + coord(50));
    expect(tombstoneTurnLabel(t[4], coord)).toBe('PASS');
  });
});

describe('tombstoneUndoSeat', () => {
  test('nobody may undo the centre or ask mid-turn', () => {
    expect(tombstoneUndoSeat([180], true)).toBe(0);
    expect(tombstoneUndoSeat([180, 100], false)).toBe(0);
  });
  test('the player who completed the last turn', () => {
    expect(tombstoneUndoSeat([180, 100], true)).toBe(2);
    expect(tombstoneUndoSeat([180, 100, 361], true)).toBe(2);
    expect(tombstoneUndoSeat(H, true)).toBe(1);
  });
});

describe('tombstoneStepTarget', () => {
  test('forward lands on turn ends', () => {
    expect(tombstoneStepTarget(H, 1, true)).toBe(3);
    expect(tombstoneStepTarget(H, 3, true)).toBe(5);
    expect(tombstoneStepTarget(H, 7, true)).toBe(7);
  });
  test('back lands on the previous turn end, never below 1', () => {
    expect(tombstoneStepTarget(H, 5, false)).toBe(3);
    expect(tombstoneStepTarget(H, 3, false)).toBe(1);
    expect(tombstoneStepTarget(H, 1, false)).toBe(1);
  });
  test('partial trailing turn (own entry, dead pending) is one step', () => {
    expect(tombstoneStepTarget([180, 100], 1, true)).toBe(2);
    expect(tombstoneStepTarget([180, 100], 2, false)).toBe(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/game/__tests__/tombstoneTurn.test.js`
Expected: FAIL — cannot resolve `../tombstoneTurn`.

- [ ] **Step 3: Implement `game/tombstoneTurn.js`** with the signatures and rules above. Header comment: the module is the React twin of `TombstoneCodes` (spec §16.1) and the only place that decodes Tombstone codes.

- [ ] **Step 4: Run the full suite**

Run: `npx vitest run`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/game/tombstoneTurn.js src/game/__tests__/tombstoneTurn.test.js
git commit -m "Add the Tombstone turn model"
```

---

### Task 3: Protocol — skip event, error event, fixtures

**Files:**
- Modify: `protocol/messages.js` (after `dsgRenjuRejectDrawTableEvent`, `:51`; `ERROR_EVENTS`, `:83-97`)
- Modify: `protocol/__fixtures__/wire-fixtures.json` (`messages` and `errorEvents`, keys in alphabetical order like the rest)
- Test: `protocol/__tests__/commands.test.js`, `protocol/__tests__/decode.test.js` (append)

**Interfaces:**
- Produces: `Commands.tombstoneSkipDead({player, table})` → `{dsgTombstoneSkipDeadTableEvent: {player, table, time: 0}}`; decode accepts inbound `dsgTombstoneSkipDeadTableEvent` (requires `table`) and `dsgTombstoneSkipDeadTableErrorEvent`. The own pass and dead stones need no protocol change: they are ordinary `Commands.move({move, moves: [move], player, table})`.

- [ ] **Step 1: Write the failing tests**

Append to `commands.test.js`:

```javascript
describe('tombstone commands', () => {
  test('tombstoneSkipDead frame', () => {
    expect(Commands.tombstoneSkipDead({ player: 'alice', table: 5 }))
      .toEqual({ dsgTombstoneSkipDeadTableEvent: { player: 'alice', table: 5, time: 0 } });
  });
  test('own pass and dead stone are single-code move frames', () => {
    expect(Commands.move({ move: 722, moves: [722], player: 'alice', table: 5 }))
      .toEqual({ dsgMoveTableEvent: { move: 722, moves: [722], player: 'alice', table: 5, time: 0 } });
    expect(Commands.move({ move: 361, moves: [361], player: 'alice', table: 5 }))
      .toEqual({ dsgMoveTableEvent: { move: 361, moves: [361], player: 'alice', table: 5, time: 0 } });
  });
});
```

Append to `decode.test.js`:

```javascript
describe('decode — tombstone events', () => {
  test('inbound skip carries silent', () => {
    const r = decode(wrap('dsgTombstoneSkipDeadTableEvent', { player: 'bob', table: 5, silent: true, time: 1 }));
    expect(r.ok).toBe(true);
    expect(r.event.payload.silent).toBe(true);
  });
  test('skip without a table id is rejected', () => {
    const r = decode(wrap('dsgTombstoneSkipDeadTableEvent', { player: 'bob' }));
    expect(r.ok).toBe(false);
    expect(r.error.kind).not.toBe('unknown'); // rejected for the missing table, not the unknown type
  });
  test('skip error event (NOT_AWAITING_DEAD_STONE = 26) is a known type', () => {
    const r = decode(wrap('dsgTombstoneSkipDeadTableErrorEvent', { player: 'bob', table: 5, error: 26, time: 1 }));
    expect(r.ok).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/protocol`
Expected: FAIL — `Commands.tombstoneSkipDead` is not a function; the decode tests get `kind: 'unknown'` (the missing-table test fails on its `not.toBe('unknown')` line, not on `ok`).

- [ ] **Step 3: Implement**

- `messages.js`: `dsgTombstoneSkipDeadTableEvent: { dir: 'both', cmd: 'tombstoneSkipDead', out: ['player', 'table'], req: TBL },` and `'dsgTombstoneSkipDeadTableErrorEvent'` in `ERROR_EVENTS`.
- Fixtures (P6): `messages.dsgTombstoneSkipDeadTableEvent = {"in": {"player": "graviton", "silent": false, "table": 1, "time": 1781365568849}, "out": {"player": "guest55", "table": 1, "time": 0}}`; `errorEvents.dsgTombstoneSkipDeadTableErrorEvent = {"26": {"error": 26, "player": "guest55", "table": 1, "time": 1781365568849}}`. The existing characterization `test.each` over fixtures then covers both directions.

- [ ] **Step 4: Run the full suite**

Run: `npx vitest run`
Expected: PASS (the fixture-driven `test.each` rows for the new events included).

- [ ] **Step 5: Commit**

```bash
git add src/protocol/messages.js src/protocol/__fixtures__/wire-fixtures.json src/protocol/__tests__/commands.test.js src/protocol/__tests__/decode.test.js
git commit -m "Add the Tombstone skip-dead-stone protocol events"
```

---

### Task 4: `GameClass` Tombstone engine

**Files:**
- Modify: `Classes/GameClass.js` — `VARIANT_RULES` (`:28-43`), `newInstance` (`:72-105`), `last_move` (`:126-150`), `goForwardBack` (`:152-190`), `moves_strings` (`:204-227`), `currentPlayer` (`:233-257`), `currentColor` (`:258-271`), `reset` (`:329-339`), `replayGame` (`:370-394`), `addMoveFromList` (`:396-406`), `addMove` (`:408-425`); new methods next to `isRenjuGame` (`:296`) and `undoMove` (`:361`)
- Test: `Classes/__tests__/tombstoneGame.test.js` (create)

**Interfaces:**
- Consumes: Task 1 `isTombstone`, `variantKey`; Task 2 `decodeTombstone`, `parseTombstoneTurns`, `tombstoneEntryPlayer`, `tombstonePhase`, `tombstoneTurnLabel`, `tombstoneStepTarget`, `DEAD_CELL`.
- Produces (Tasks 5-6 call these):
  - `game.gameState.tombstone = {trailingClosed: boolean}` — set to `{trailingClosed: true}` in `reset()`; `newInstance()` copies it as a new object (`{...this.gameState.tombstone}`) so reducers can mutate the copy without touching the previous state.
  - `isTombstoneGame(): boolean`
  - `tombstoneTrailingClosed(): boolean` — `true` when `gameState.tombstone` is missing.
  - `tombstonePhaseNow(): {player, phase}` — `tombstonePhase(this.moves, this.tombstoneTrailingClosed())`.
  - `tombstone_turn_cells(): [{label: string, until: number}]` — one per turn, `until = turn.end + 1`.
  - `undoTurn(): void` — removes the last parsed turn's entries (new array, never mutate `this.moves` in place, because `newInstance` shares it), clamps a defined `until` to `min(until, newLength)` (a browsing `until` below that is kept), sets `trailingClosed = true`, then `replayGame(this.until)` (full replay when `until` is undefined; unlike `undoMove`, which always replays everything, this keeps a browsing view consistent).
  - Behaviour: `currentPlayer()` = phase player; `currentColor()` = `DEAD_CELL` (3) in the DEAD phase, else the phase player; `last_move()` = decoded points of the last displayed turn (the own point, unless the own entry is a pass, plus the dead point if there is one); `moves_strings()` = the cell labels; `goForwardBack` steps with `tombstoneStepTarget` (forward through `addMoveFromList`, back through `replayGame`).

Implementation notes (the parts the tests do not force):
- `VARIANT_RULES['tombstone-pente'] = {replay: 'tombstone', disableRatedOnReplay: false, add: 'tombstone', goMove: false, player: 'tombstone', postRule: 'none'}`.
- One private `#applyTombstoneEntry(code, player)`: decode; own → `abstractBoard[x][y] = player` then `#detectPenteCapture(x, y, player)` (it compares exact colours, so value-3 cells already block captures); pass → nothing; dead → `abstractBoard[x][y] = DEAD_CELL` and **no** capture check (D3: never call `#detectPenteCapture` with colour 3, whose `opponentColor` would be 2); `null` → nothing.
- `addMove` and `addMoveFromList` branch on `rules.add === 'tombstone'` **before** computing `x`/`y` from the raw code; the player comes from `tombstoneEntryPlayer(this.moves, index)`. `#replayTombstoneGame(until)` applies entries `0..min(len, until)-1` the same way. `#applyPostRule` stays a no-op (`'none'`).
- Every Tombstone branch in `last_move`, `moves_strings`, `currentPlayer`, `currentColor` and `goForwardBack` comes **first**, before the Connect6 / Go / Renju arms.

- [ ] **Step 1: Write the failing tests**

```javascript
import { describe, test, expect } from 'vitest';
import { Game, GameState } from '../GameClass';

function ts(moves = []) {
  const g = new Game(); g.setGame(33); g.gameState.state = GameState.State.STARTED;
  moves.forEach((m) => g.addMove(m));
  return g;
}
const cell = (g, p) => g.abstractBoard[p % 19][Math.floor(p / 19)];
const close = (g) => { g.gameState.tombstone.trailingClosed = true; return g; };

describe('Tombstone replay / addMove', () => {
  test('own stones by turn player, dead cells = 3, pass = nothing', () => {
    const g = ts([180, 100, 361, 722, 411]);
    expect(cell(g, 180)).toBe(1);
    expect(cell(g, 100)).toBe(2);
    expect(cell(g, 0)).toBe(3);
    expect(cell(g, 50)).toBe(3);
    expect(g.abstractBoard.flat().filter((v) => v !== 0)).toHaveLength(4);
  });
  test('own stone captures a pair; a dead stone may fill a just-captured point', () => {
    // turns: P1 180 | P2 181 | P1 pass | P2 182 | P1 183 (captures 181+182) + dead on 181
    const g = ts([180, 181, 722, 182, 183, 361 + 181]);
    expect(cell(g, 182)).toBe(0);
    expect(cell(g, 181)).toBe(3);
    expect(g.captures[2]).toBe(2);
  });
  test('a dead stone blocks a capture', () => {
    const g = ts([180, 181, 361 + 182, 183]);
    expect(cell(g, 181)).toBe(2);
    expect(g.captures[2]).toBe(0);
  });
  test('dead stones never capture (D3)', () => {
    // P2 201 + dead 200 | P1 pass | P2 202 + dead 203: P2 pair 201+202 sits between two dead stones
    const g = ts([180, 201, 361 + 200, 722, 202, 361 + 203]);
    expect(cell(g, 201)).toBe(2);
    expect(cell(g, 202)).toBe(2);
    expect(g.captures[2]).toBe(0);
  });
  test('replayGame and addMoveFromList give the same board as addMove', () => {
    const H = [180, 181, 722, 182, 183, 542, 722, 400];
    const a = ts(H);
    const b = ts(); b.moves = [...H]; b.replayGame();
    const c = ts(); c.moves = [...H]; for (let i = 0; i < H.length; i++) c.addMoveFromList(i);
    expect(b.abstractBoard).toEqual(a.abstractBoard);
    expect(c.abstractBoard).toEqual(a.abstractBoard);
    expect(b.captures).toEqual(a.captures);
  });
});

describe('Tombstone whose turn / colour', () => {
  test('DEAD phase keeps the mover and hovers gray', () => {
    const g = ts([180, 100]);
    g.gameState.tombstone.trailingClosed = false;
    expect(g.currentPlayer()).toBe(2);
    expect(g.currentColor()).toBe(3);
    close(g);
    expect(g.currentPlayer()).toBe(1);
    expect(g.currentColor()).toBe(1);
  });
  test('fresh game: trailingClosed true, P1, survives newInstance as a copy', () => {
    const g = ts();
    expect(g.gameState.tombstone).toEqual({ trailingClosed: true });
    const h = g.newInstance();
    h.gameState.tombstone.trailingClosed = false;
    expect(g.gameState.tombstone.trailingClosed).toBe(true);
  });
});

describe('Tombstone last_move / moves_strings / cells', () => {
  test('last_move marks A19 for code 361 and the own stone', () => {
    expect(ts([180, 100, 361]).last_move().sort((x, y) => x - y)).toEqual([0, 100]);
  });
  test('last_move of a pass-only turn is empty, of pass + dead is the dead point', () => {
    expect(ts([180, 722]).last_move()).toEqual([]);
    expect(ts([180, 722, 411]).last_move()).toEqual([50]);
  });
  test('moves_strings labels 361 as dead A19, one cell per turn', () => {
    const g = ts([180, 100, 361, 722]);
    expect(g.moves_strings()).toEqual(['K10', g.getMoveCoord(100) + ' + dead A19', 'PASS']);
    expect(g.tombstone_turn_cells().map((c) => c.until)).toEqual([1, 3, 4]);
  });
});

describe('Tombstone history stepping and undo', () => {
  test('goForwardBack steps whole turns', () => {
    const g = ts([180, 100, 361, 722, 411, 200]);
    g.goForwardBack(false); expect(g.until).toBe(5);
    g.goForwardBack(false); expect(g.until).toBe(3);
    expect(cell(g, 50)).toBe(0);
    g.goForwardBack(true); expect(g.until).toBe(5);
    expect(cell(g, 50)).toBe(3);
  });
  test('undoTurn removes 2 entries after a dead stone, 1 after a skip', () => {
    const g = close(ts([180, 100, 361]));
    g.undoTurn();
    expect(g.moves).toEqual([180]);
    expect(cell(g, 0)).toBe(0);
    const h = close(ts([180, 100, 361, 722]));
    h.undoTurn();
    expect(h.moves).toEqual([180, 100, 361]);
    expect(h.gameState.tombstone.trailingClosed).toBe(true);
  });
  test('undoTurn does not mutate the previous instance moves array', () => {
    const g = close(ts([180, 100, 361]));
    const before = g.moves;
    const h = g.newInstance(); h.undoTurn();
    expect(before).toEqual([180, 100, 361]);
  });
  test('undoTurn while browsing keeps until within the moves', () => {
    const g = close(ts([180, 100, 361, 722, 411]));
    g.goto_move(1); g.undoTurn();
    expect(g.moves).toEqual([180, 100, 361]);
    expect(g.until).toBe(1);
    const h = close(ts([180, 100, 361, 722, 411]));
    h.undoTurn();
    expect(h.until).toBe(3);
  });
});
```

Check the capture test coordinates before running: on 19x19, `181`, `182`, `183` are the three points right of `180` on the same row, so P1's `183` with P1's `180` brackets P2's `181`+`182`. Note `captures[c]` counts stones *of colour* `c` lost (`#detectPenteCapture` adds to `captures[opponentColor]`), which is why the assertion reads `captures[2]`.

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/Classes/__tests__/tombstoneGame.test.js`
Expected: FAIL — `VARIANT_RULES['tombstone-pente']` is undefined, so `addMove` throws on `rules.player`.

- [ ] **Step 3: Implement** the Produces list above in `GameClass.js`, importing `isTombstone` from `../game/boardGeometry` and the Task 2 names from `../game/tombstoneTurn`.

- [ ] **Step 4: Run the full suite**

Run: `npx vitest run`
Expected: PASS; `replayDispatch.test.js` golden master unchanged (ids 1..30).

- [ ] **Step 5: Commit**

```bash
git add src/Classes/GameClass.js src/Classes/__tests__/tombstoneGame.test.js
git commit -m "Replay, label and step Tombstone games in GameClass"
```

---

### Task 5: Reducers — turn tracking, skip handler, undo, `tombstoneUi` slice, server game ids

**Files:**
- Create: `ui/tombstoneUi.js`
- Modify: `redux_reducers/utils.js` — `addMove` (`:203-237`), `undoReply` (`:357-367`), new `tombstoneSkipDead`
- Modify: `redux_reducers/rootReducer.js` — imports (`:22`, `:25-65`), `initialState` (`:70-89`), `dsgLoginEvent` (`:97-105`), `EVENT_HANDLERS` (`:96-140`), slice line after `:217`
- Modify: `selectors/index.js` (append)
- Test: `redux_reducers/__tests__/tombstoneTracking.test.js` (create), `ui/__tests__/tombstoneUi.test.js` (create), `selectors/__tests__/selectors.test.js` (append)

**Interfaces:**
- Consumes: Task 2 `isOwnEntry`; Task 3 event names; Task 4 `isTombstoneGame`, `undoTurn`, `gameState.tombstone`, `tombstonePhaseNow`.
- Produces:
  - `ui/tombstoneUi.js`: `INITIAL = {pending: false}`, `tombstoneMarkPending()` → `{type: 'TOMBSTONE_UI/MARK_PENDING'}`, `tombstoneUiReducer(state, action)`, `ADVANCING_EVENTS` (exported for the test) = `dsgMoveTableEvent`, `dsgTombstoneSkipDeadTableEvent`, `dsgMoveTableErrorEvent`, `dsgTombstoneSkipDeadTableErrorEvent`, `dsgUndoReplyTableEvent`, `dsgGameStateTableEvent`, `dsgChangeStateTableEvent`, `dsgExitTableEvent`, `dsgBootTableEvent`. It returns the same reference when nothing changes (as `renjuOpeningUi.js:59-64`).
  - Root state: `state.tombstoneUi`; `state.serverGameIds: number[] | undefined` (from `p.serverData.gameEvents[].game` when that is an array).
  - `selectSelectableGameIds(state)` → `selectableGameIds(state.serverGameIds)` (Task 1).
  - `tombstoneSkipDead(data, state)` reducer helper (exported from `utils.js`).

Behaviour:
- `addMove`, after the existing incremental/bulk application and only when `game.isTombstoneGame()`: `trailingClosed = !(isOwnEntry(last) && game.moves.length > 1)`. One formula covers both paths: an incremental own entry beyond the centre opens the DEAD phase, a dead entry or the centre closes it, and a bulk rejoin defaults a trailing own entry to "dead stone pending" (spec §13.5). Set it on the **new** instance's copied `tombstone` object.
- `tombstoneSkipDead`: for the current table and a Tombstone game, a new instance with `trailingClosed = true`; `emit(state, {sound: 'move'})` unless `data.silent` or `data.player === state.me`.
- `undoReply` accepted: `game.isTombstoneGame() ? game.undoTurn() : game.undoMove()`.
- `dsgTombstoneSkipDeadTableErrorEvent` gets no `EVENT_HANDLERS` entry: its only effect is clearing `pending`, which the slice does (P4).

- [ ] **Step 1: Write the failing tests**

`ui/__tests__/tombstoneUi.test.js`:

```javascript
import { describe, test, expect } from 'vitest';
import { INITIAL, tombstoneMarkPending, tombstoneUiReducer, ADVANCING_EVENTS } from '../tombstoneUi';

describe('tombstoneUi slice', () => {
  test('mark pending, unrelated actions keep the reference', () => {
    const p = tombstoneUiReducer(INITIAL, tombstoneMarkPending());
    expect(p).toEqual({ pending: true });
    expect(tombstoneUiReducer(p, { type: 'dsgTextTableEvent' })).toBe(p);
    expect(tombstoneUiReducer(INITIAL, { type: 'dsgMoveTableEvent' })).toBe(INITIAL);
  });
  test.each([
    'dsgMoveTableEvent', 'dsgTombstoneSkipDeadTableEvent', 'dsgMoveTableErrorEvent',
    'dsgTombstoneSkipDeadTableErrorEvent', 'dsgUndoReplyTableEvent', 'dsgGameStateTableEvent',
    'dsgChangeStateTableEvent', 'dsgExitTableEvent', 'dsgBootTableEvent',
  ])('pending cleared by %s', (type) => {
    expect(ADVANCING_EVENTS.has(type)).toBe(true);
    expect(tombstoneUiReducer({ pending: true }, { type })).toEqual({ pending: false });
  });
});
```

`redux_reducers/__tests__/tombstoneTracking.test.js`:

```javascript
import { describe, test, expect } from 'vitest';
import { addMove, tombstoneSkipDead, undoReply } from '../utils';
import liveGameApp from '../rootReducer';
import { Game, GameState } from '../../Classes/GameClass';
import Table from '../../Classes/TableClass';
import { tombstoneMarkPending } from '../../ui/tombstoneUi';

function tsState() {
  const g = new Game(); g.setGame(33); g.gameState.state = GameState.State.STARTED;
  const t = new Table({ table: 5, initialMinutes: 10 }); t.me = 'alice';
  return { game: g, tables: { 5: t }, table: 5, me: 'alice', pendingNotifications: [],
    users: { 'game server': { muted: false } }, table_messages: [] }; // undoReply posts a table message
}
const move = (s, m, player = 'bob') => addMove({ table: 5, move: m, moves: [m], player }, s);
const bulk = (s, arr) => addMove({ table: 5, move: arr[arr.length - 1], moves: arr, player: 'srv' }, s);
const phase = (s) => s.game.tombstonePhaseNow();

describe('incremental tracking', () => {
  test('centre -> P2 OWN; own -> DEAD; dead -> P1 OWN', () => {
    const s = tsState();
    move(s, 180, 'alice'); expect(phase(s)).toEqual({ player: 2, phase: 'OWN' });
    move(s, 100);          expect(phase(s)).toEqual({ player: 2, phase: 'DEAD' });
    move(s, 361);          expect(phase(s)).toEqual({ player: 1, phase: 'OWN' });
  });
  test('own pass also opens DEAD', () => {
    const s = tsState(); move(s, 180, 'alice'); move(s, 722);
    expect(phase(s)).toEqual({ player: 2, phase: 'DEAD' });
  });
  test('skip echo closes the turn and sounds for the opponent only', () => {
    const s = tsState(); move(s, 180, 'alice'); move(s, 100);
    s.pendingNotifications = [];
    tombstoneSkipDead({ table: 5, player: 'bob', silent: false }, s);
    expect(phase(s)).toEqual({ player: 1, phase: 'OWN' });
    expect(s.pendingNotifications).toEqual([{ sound: 'move' }]);
  });
  test('the previous game instance is not mutated', () => {
    const s = tsState(); move(s, 180, 'alice'); move(s, 100);
    const before = s.game;
    tombstoneSkipDead({ table: 5, player: 'bob', silent: false }, s);
    expect(before.gameState.tombstone.trailingClosed).toBe(false);
  });
});

describe('rejoin (bulk) tracking', () => {
  test('bulk of only the centre -> P2 OWN', () => {
    const s = tsState(); bulk(s, [180]);
    expect(phase(s)).toEqual({ player: 2, phase: 'OWN' });
  });
  test('bulk ending in an own entry -> DEAD pending by default', () => {
    const s = tsState(); bulk(s, [180, 100, 361, 722]);
    expect(phase(s)).toEqual({ player: 1, phase: 'DEAD' });
  });
  test('silent skip after the bulk list corrects it, without a sound', () => {
    // the opponent (not state.me) is the last mover, so only data.silent can suppress the sound
    const s = tsState(); bulk(s, [180, 100]);
    s.pendingNotifications = [];
    tombstoneSkipDead({ table: 5, player: 'bob', silent: true }, s);
    expect(phase(s)).toEqual({ player: 1, phase: 'OWN' });
    expect(s.pendingNotifications).toEqual([]);
  });
  test('bulk ending in a dead entry -> OWN', () => {
    const s = tsState(); bulk(s, [180, 100, 361]);
    expect(phase(s)).toEqual({ player: 1, phase: 'OWN' });
  });
});

describe('undo', () => {
  test('accepted undo removes the whole last turn', () => {
    const s = tsState(); [180, 100, 361].forEach((m) => move(s, m));
    undoReply({ table: 5, accepted: true, player: 'alice' }, s);
    expect(s.game.moves).toEqual([180]);
    expect(phase(s)).toEqual({ player: 2, phase: 'OWN' });
  });
});

describe('rootReducer wiring', () => {
  test('skip event routed, slice present and cleared by the echo', () => {
    let s = liveGameApp(undefined, { type: '@@INIT' });
    expect(s.tombstoneUi).toEqual({ pending: false });
    s = { ...s, ...tsState() };
    move(s, 180, 'alice'); move(s, 100);
    s = liveGameApp(s, tombstoneMarkPending());
    expect(s.tombstoneUi.pending).toBe(true);
    s = liveGameApp(s, { type: 'dsgTombstoneSkipDeadTableEvent', payload: { table: 5, player: 'bob', silent: false } });
    expect(s.tombstoneUi.pending).toBe(false);
    expect(s.game.gameState.tombstone.trailingClosed).toBe(true);
  });
  test('login stores the server game ids', () => {
    const s = liveGameApp(undefined, { type: 'dsgLoginEvent', payload: {
      player: 'alice', me: { subscriberLevel: 1 }, serverData: { tournament: false, arena: false,
        gameEvents: [{ id: 1, name: 'Live Game', game: 1 }, { id: 2, name: 'Live Game', game: 33 }] } } });
    expect(s.serverGameIds).toEqual([1, 33]);
  });
});
```

Append to `selectors.test.js` (import `selectSelectableGameIds`):

```javascript
describe('selectSelectableGameIds', () => {
  test('lists only what the server offers', () => {
    expect(selectSelectableGameIds({ serverGameIds: [33, 34] })).toEqual([33]);
    expect(selectSelectableGameIds({ serverGameIds: [1, 2, 3, 4] })).toEqual([1, 3]);
    expect(selectSelectableGameIds({})).not.toContain(33);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/ui src/redux_reducers src/selectors`
Expected: FAIL — `../tombstoneUi` cannot be resolved; `tombstoneSkipDead` / `selectSelectableGameIds` are not exported.

- [ ] **Step 3: Implement** the Produces and Behaviour lists above. Model `ui/tombstoneUi.js` and its header comment on `ui/renjuOpeningUi.js`.

- [ ] **Step 4: Run the full suite**

Run: `npx vitest run`
Expected: PASS, including the existing `muteImmutability.test.js` and `renjuTracking.test.js`.

- [ ] **Step 5: Commit**

```bash
git add src/ui/tombstoneUi.js src/ui/__tests__/tombstoneUi.test.js src/redux_reducers/utils.js src/redux_reducers/rootReducer.js src/redux_reducers/__tests__/tombstoneTracking.test.js src/selectors/index.js src/selectors/__tests__/selectors.test.js
git commit -m "Track Tombstone turn phase, skip and undo in the reducers"
```

---

### Task 6: Components — board, button, move list, undo, selectors

**Files:**
- Modify: `Classes/TableClass.js` — `canUndo` (`:58-72`)
- Modify: `Components/Board/Board.js` — `mapStateToProps`/`mapDispatchToProps` (`:15-34`), `player_colors` (`:67`), click gating (`:132-172`)
- Modify: `Components/Table/GameInfoPanel.js` — `mapStateToProps`/`mapDispatchToProps` (`:31-59`), the PASS block (`:175-184`)
- Modify: `Components/Table/MovesListPanel.js` — cell building (`:59-90`, the Connect6 special case at `:61`)
- Modify: `Components/Table/SettingsModal.js` (`:49-54`, `:128`), `Components/Arena/CreateArenaTableModal.js` (`:49-54`, `:126`)
- Test: `Classes/__tests__/tableClass.test.js` (append)

**Interfaces:**
- Consumes: Task 2 `tombstoneButton`, `tombstoneUndoSeat`, `TOMBSTONE_DEAD_OFFSET`, `TOMBSTONE_OWN_PASS`; Task 3 `Commands.tombstoneSkipDead`; Task 4 `isTombstoneGame`, `tombstoneTrailingClosed`, `tombstonePhaseNow`, `tombstone_turn_cells`; Task 5 `state.tombstoneUi`, `tombstoneMarkPending`, `selectSelectableGameIds`.
- Produces: nothing other tasks consume.

Exact behaviour (spec §13.4, §13.6):
- **Wiring**: `Board.js` maps `tombstoneUi: state.tombstoneUi` and dispatches `tombstoneMarkPending()`; `GameInfoPanel.js` maps `tombstone_pending: state.tombstoneUi.pending` and the same dispatch.
- **`TableClass.canUndo`**: a first arm `if (isTombstone(this.game))` returning `game.gameState.state === STARTED && seat > 0 && this.me === this.seats[seat]` with `seat = tombstoneUndoSeat(game.moves, game.tombstoneTrailingClosed())`. The other arms are unchanged.
- **`Board.js`**: `player_colors[3] = 'gray-stone-gradient'` for every game (cell value 3 only exists in Tombstone), so dead stones render gray and the DEAD-phase hover (`currentColor() === 3`) is gray. A Tombstone click arm placed first inside `if (myTurn)`: when `props.tombstoneUi.pending` the board is inert; else an empty cell gets `clickHandler = () => sendTombstone(c)`, which sends `Commands.move({move: c, moves: [c], player: table.me, table: table.table})` and then dispatches `tombstoneMarkPending()`. `c` is computed from the **numeric** loop variable `m` (as the Renju arms do, `() => sendRenjuDecline(m)`): `c = m` in the OWN phase, `c = TOMBSTONE_DEAD_OFFSET + m` in the DEAD phase. Never take the point from the handler argument: `BoardSquare` passes the square's id as a **string** (`BoardSquare.js:106-108`), so `TOMBSTONE_DEAD_OFFSET + id` would send `"361100"` instead of `461`. No `drawOffer` on Tombstone frames. The Renju and generic arms follow unchanged (`else if`).
- **`GameInfoPanel.js`**: `const tsButton = game.isTombstoneGame() ? tombstoneButton(game.moves, game.tombstoneTrailingClosed(), props.tombstone_pending) : null;`. The PASS block shows when `table.isMyTurn(game) && STARTED && (game.isGo() || renjuPostOpening || tsButton)`. Its label is `tsButton ? tsButton.label : 'PASS'`. Its click is `pass()` for Go/Renju; for Tombstone `ownPass` sends `Commands.move({move: TOMBSTONE_OWN_PASS, moves: [TOMBSTONE_OWN_PASS], player: table.me, table: table.table})` and `skipDead` sends `Commands.tombstoneSkipDead({player: table.me, table: table.table})`, each followed by `tombstoneMarkPending()`. `pass()` (which sends `gridSize*gridSize`) is never called for Tombstone.
- **`MovesListPanel.js`**: a Tombstone arm before the Connect6 one: one `TableCell` per `game.tombstone_turn_cells()` entry, `onClick={() => props.goto_move(c.until)}`, highlighted (`'h5'`/`'error'`) when `game.until === c.until`. The two-cells-per-row grouping below is reused unchanged (row = P1 turn, P2 turn, as in Pente).
- **Selectors**: both modals map `gameIds: selectSelectableGameIds(state)` and iterate `props.gameIds` instead of `STANDARD_GAME_IDS` (`SettingsModal.js:128`, `CreateArenaTableModal.js:126`; drop the now-unused `STANDARD_GAME_IDS` imports at `SettingsModal.js:20` and `CreateArenaTableModal.js:18`). The table's current game is always offered by its server (Plan B Task 7 opens new tables on an offered game and refuses changes to unoffered ones), so the Settings `Select`'s `value` (`SettingsModal.js:119`) stays inside the list.

- [ ] **Step 1: Write the failing test** — append to `tableClass.test.js`:

```javascript
describe('canUndo — Tombstone whole-turn rule', () => {
  const tsTable = (me) => { const t = tableForGame(33); t.seats = [undefined, 'p1', 'p2']; t.me = me; return t; };
  const tsGame = (moves, closed) => {
    const g = new Game(); g.setGame(33); g.gameState.state = GameState.State.STARTED;
    moves.forEach((m) => g.addMove(m)); g.gameState.tombstone.trailingClosed = closed; return g;
  };
  test('the player who completed the last turn may ask, the mover may not', () => {
    expect(tsTable('p2').canUndo(tsGame([180, 100, 361], true))).toBe(true);
    expect(tsTable('p1').canUndo(tsGame([180, 100, 361], true))).toBe(false);
  });
  test('nobody while a dead stone is pending, nobody on the centre alone', () => {
    expect(tsTable('p2').canUndo(tsGame([180, 100], false))).toBe(false);
    expect(tsTable('p1').canUndo(tsGame([180, 100], false))).toBe(false);
    expect(tsTable('p1').canUndo(tsGame([180], true))).toBe(false);
  });
});
```

The file already imports `Game`, `GameState` and defines `tableForGame` (`:1-9`).

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/Classes/__tests__/tableClass.test.js`
Expected: FAIL — the generic arm (`seats[3 - currentPlayer()]`) returns `true` for `p1` on `[180, 100]` with the dead stone pending (current player 2) and for `p1` on `[180]` alone. The two first assertions already pass and pin that the new arm keeps them.

- [ ] **Step 3: Implement** the `canUndo` arm, then the component changes above. The components have no unit tests (the precedent `2026-07-15-renju-pass-draw-react.md` tests the same kind of change through pure helpers); their logic is the Task 2/4/5 functions they call.

- [ ] **Step 4: Run the full suite and the production build**

Run: `npx vitest run`
Expected: PASS.
Run: `npm run build`
Expected: exits 0 and writes `build/` (gitignored). This catches JSX and import errors the node tests cannot see.

- [ ] **Step 5: Commit**

```bash
git add src/Classes/TableClass.js src/Classes/__tests__/tableClass.test.js src/Components/Board/Board.js src/Components/Table/GameInfoPanel.js src/Components/Table/MovesListPanel.js src/Components/Table/SettingsModal.js src/Components/Arena/CreateArenaTableModal.js
git commit -m "Play Tombstone turns from the board and the pass button"
```

---

### Task 7: End-to-end smoke test against the local server

**Prerequisite:** Plans A and B merged on `feat/tombstone-pente` in `pente.org` and running locally by the local smoke-test route (compile, bind-mount, restart `penteorg-pente.org-1`; never `sync_gameServer.sh`, which deploys to production — spec §15). Skip this task, and say so in the hand-off, if they are not.

**Files:**
- Modify (only if a captured frame differs): `protocol/__fixtures__/wire-fixtures.json`

- [ ] **Step 1: Start the client** — `npm start` (the repo's `.env.local` sets `PUBLIC_LOCAL_BACKEND=1`, so it proxies to the local backend). Log in as two players in two browsers. On the server list, check that **Experimental** is listed next to the existing servers, click it, and create a table: it opens as **Tombstone Pente**, and Settings lists only Tombstone Pente. Sit both players and press play.

- [ ] **Step 2: Walk the states of spec §13.6** and check each against what appears:
  1. P1's centre stone appears by itself; P1 sees no Tombstone button.
  2. P2 (OWN): button **Place only a dead stone**; hover is black. Place a stone → button becomes **Skip dead stone**, hover is gray. Place a dead stone → gray stone, turn passes to P1.
  3. P1: **Place only a dead stone**, then place a dead stone (pass + dead).
  4. P2: place a stone, then **Skip dead stone** (own only).
  5. P1: **Place only a dead stone**, then **Skip dead stone** (empty turn); the move list shows `PASS`.
  6. Make a capture, then put a dead stone on a captured point.
  7. Undo: the player who just finished a turn requests undo, the other accepts → the whole turn disappears on both screens.
  8. Reload P2's browser mid-turn (after P2's own stone, before the dead stone) → P2 is back in the DEAD phase. Reload after a skip → OWN phase for the next player, and no move sound.
  9. Step back and forward through the move list: each click lands on a turn end; A19 dead stones (`361`) are shown and labelled as dead stones.
  10. Log in on the Main Room, the King of the Hill server (45) and the Arena (create-table dialog) and open the game picker: Tombstone is not listed and the old 16 games are. On the Go server (46) the picker lists only the three Go boards.

- [ ] **Step 3: Compare frames** — in the browser dev tools, copy one inbound `dsgTombstoneSkipDeadTableEvent` and one `dsgTombstoneSkipDeadTableErrorEvent` (the UI never offers the skip button outside the DEAD phase, so send a hand-built `{"dsgTombstoneSkipDeadTableEvent":{"player":"<me>","table":<n>,"time":0}}` frame over the open websocket from the dev tools console while in the OWN phase; if that is not practical, keep the synthesized error fixture and say so in the hand-off). If their field sets differ from the Task 3 fixtures, replace the fixtures with the captured frames and rerun `npx vitest run` (PASS).

- [ ] **Step 4: Tidy and commit**

Run: `git status --porcelain | grep '^??'`
Expected: no output (no scratch files in the repo).

```bash
git add src/protocol/__fixtures__/wire-fixtures.json   # only if Step 3 changed it
git commit -m "Use captured Tombstone skip frames in the protocol fixtures"   # only if Step 3 changed it
```

---

## Spec coverage (§13 → task)

| Spec item | Task |
|---|---|
| §13.1 `isTombstone`, `variantKey`, `STANDARD_GAME_IDS`, name, colours, CSS, gray gradient | 1 |
| §13.1 pickers list only the server's offered games, by `serverData.gameEvents` (confirmed present, P5) | 1 (pure), 5 (state), 6 (modals), 7 (manual) |
| §13.1 showing and entering the Experimental server (no code change) | 7 (manual) |
| §13.2 pure turn model | 2 |
| §13.3 `gameState.tombstone`, rules row, replay/add, current player/colour, last move, move strings, stepping, `undoTurn` | 4 |
| §13.4 gray `player_colors[3]`, phase-aware click, reused PASS button, `canUndo`, `clockRunning` (unchanged: uses `currentPlayer`) | 6 |
| §13.5 protocol + fixtures | 3 |
| §13.5 `addMove` tracking, skip handler + sound, error events, `undoReply`, `tombstoneUi` + `ADVANCING_EVENTS` | 5 |
| §13.6 exact UI states | 2, 5, 6 (unit), 7 (manual) |
| §17 D: pinned `boardGeometry.test.js` ids/count, `tableClass.test.js` colours | 1 |
| §8.1 never send 2-code live frames | 3, 6 (every send is `moves: [code]`) |
