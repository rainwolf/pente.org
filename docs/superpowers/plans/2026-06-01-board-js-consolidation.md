# Board-Canvas JS Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `boardColor` and `replayGame` switches duplicated across 9 game-viewer pages with two shared functions (`getBoardColor`, `replayGame`) in one JS file, keyed by named `GridStateFactory` ids emitted from a JSP fragment.

**Architecture:** A new JSP fragment `gameConstants.jspf` imports `org.pente.game.GridStateFactory` and emits a global JS `GAME` object (named id → int). A new static `js/boardCommon.js` defines `getBoardColor(game)` and `replayGame(abstractBoard, movesList, until)`, reusing the color vars and `replay*Game` functions already in `tb/gameScript.js`. Each of the 9 pages includes both, deletes its inline `replayGame`, and replaces its inline color switch with `boardColor = getBoardColor(game);`.

**Tech Stack:** Java/JSP (Tomcat), vanilla browser JS, Node (test runner for a backend-independent regression test), Playwright + the local HTTPS backend for smoke verification.

---

## File Structure

- **Create** `dsg_src/httpdocs/gameServer/gameConstants.jspf` — emits the `GAME` constants object (single source of truth = the Java class).
- **Create** `dsg_src/httpdocs/gameServer/js/boardCommon.js` — `getBoardColor` + `replayGame`. Depends on `GAME` and on color vars / `replay*Game` from `tb/gameScript.js`; must load after both.
- **Create** `dsg_src/httpdocs/gameServer/js/boardCommon.test.js` — Node regression test asserting every id maps to the exact color/replay function the old switches used. No backend needed.
- **Modify** these 9 pages (add 2 includes, delete inline `replayGame`, swap the color switch for a call):
  - `viewGameEmbed.jsp`, `viewLiveGameEmbed.jsp`, `viewLiveGameMobile.jsp`
  - `tb/mobileGame.jsp`, `tb/cancelReply.jsp`, `tb/undoReply.jsp`, `tb/listedMobileGame.jsp`
  - `tb/finalGo.jsp`, `tb/deadGo.jsp` (Go-only: a single `boardColor = goColor;` line and a trivial Go `replayGame`)

All paths below are relative to repo root `pente.org/` unless absolute.

---

## Reference: authoritative id → color / replay mapping

Taken verbatim from the existing switches (`tb/mobileGame.jsp` holds the full set incl. O-Pente/Swap2; shorter pages omit later cases). `X` covers the live id, its `SPEED_` sibling, and its `TB_` sibling.

| Game family (ids) | color | replay fn |
|---|---|---|
| PENTE / SPEED_PENTE / TB_PENTE | `penteColor` | `replayPenteGame` |
| KERYO / SPEED_KERYO / TB_KERYO | `keryoPenteColor` | `replayKeryoPenteGame` |
| GOMOKU / SPEED_GOMOKU / TB_GOMOKU | `gomokuColor` | `replayGomokuGame` |
| DPENTE / SPEED_DPENTE / TB_DPENTE | `dPenteColor` | `replayPenteGame` |
| GPENTE / SPEED_GPENTE / TB_GPENTE | `gPenteColor` | `replayGPenteGame` |
| POOF_PENTE / SPEED / TB | `poofPenteColor` | `replayPoofPenteGame` |
| CONNECT6 / SPEED / TB | `connect6Color` | `replayConnect6Game` |
| BOAT_PENTE / SPEED / TB | `boatPenteColor` | `replayPenteGame` |
| DKERYO / SPEED / TB | `dkeryoPenteColor` | `replayKeryoPenteGame` |
| GO, GO9, GO13 (+SPEED +TB) | `goColor` | `replayGoGame` |
| OPENTE / SPEED / TB | `oPenteColor` | `replayOPenteGame` |
| SWAP2PENTE / SPEED / TB | `swap2PenteColor` | `replayPenteGame` |
| SWAP2KERYO / SPEED / TB | `swap2KeryoColor` | `replayKeryoPenteGame` |

Unknown id → **throw** in both functions (loud by design; old code silently defaulted).

---

## Task 1: Shared `boardCommon.js` + backend-independent regression test (TDD)

**Files:**
- Create: `dsg_src/httpdocs/gameServer/js/boardCommon.test.js`
- Create: `dsg_src/httpdocs/gameServer/js/boardCommon.js`
- Reads (at test runtime): `dsg_src/httpdocs/gameServer/tb/gameScript.js` (color vars only)

- [ ] **Step 1: Write the failing test**

Create `dsg_src/httpdocs/gameServer/js/boardCommon.test.js`. It loads the real color vars out of `gameScript.js`, builds the `GAME` constants with literal ids matching `GridStateFactory.java`, stubs the `replay*Game` functions as spies, evaluates `boardCommon.js` in a `vm` sandbox, then asserts color + replay routing for **every** id (live, SPEED, TB) and asserts unknown ids throw.

```javascript
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const HERE = __dirname;
const gameScriptSrc = fs.readFileSync(path.join(HERE, '../tb/gameScript.js'), 'utf8');
const boardCommonSrc = fs.readFileSync(path.join(HERE, 'boardCommon.js'), 'utf8');

// Pull ONLY the leading `var <name>Color = "...";` lines out of gameScript.js so the
// test stays in sync with the real palette without evaluating its DOM-dependent body.
const colorLines = gameScriptSrc
  .split('\n')
  .filter(l => /^var\s+\w+Color\s*=/.test(l))
  .join('\n');

// Named ids, mirroring org.pente.game.GridStateFactory (live=base, SPEED=+1, TB=+50).
const base = {
  PENTE: 1, KERYO: 3, GOMOKU: 5, DPENTE: 7, GPENTE: 9, POOF_PENTE: 11,
  CONNECT6: 13, BOAT_PENTE: 15, DKERYO: 17, GO: 19, GO9: 21, GO13: 23,
  OPENTE: 25, SWAP2PENTE: 27, SWAP2KERYO: 29,
};
const GAME = {};
for (const [name, id] of Object.entries(base)) {
  GAME[name] = id;
  GAME['SPEED_' + name] = id + 1;
  GAME['TB_' + name] = id + 50;
}

// Expected family -> color var name + replay fn name (the authoritative mapping).
const FAMILIES = [
  { names: ['PENTE'],       color: 'penteColor',      replay: 'replayPenteGame' },
  { names: ['KERYO'],       color: 'keryoPenteColor',  replay: 'replayKeryoPenteGame' },
  { names: ['GOMOKU'],      color: 'gomokuColor',      replay: 'replayGomokuGame' },
  { names: ['DPENTE'],      color: 'dPenteColor',      replay: 'replayPenteGame' },
  { names: ['GPENTE'],      color: 'gPenteColor',      replay: 'replayGPenteGame' },
  { names: ['POOF_PENTE'],  color: 'poofPenteColor',   replay: 'replayPoofPenteGame' },
  { names: ['CONNECT6'],    color: 'connect6Color',    replay: 'replayConnect6Game' },
  { names: ['BOAT_PENTE'],  color: 'boatPenteColor',   replay: 'replayPenteGame' },
  { names: ['DKERYO'],      color: 'dkeryoPenteColor',  replay: 'replayKeryoPenteGame' },
  { names: ['GO','GO9','GO13'], color: 'goColor',       replay: 'replayGoGame' },
  { names: ['OPENTE'],      color: 'oPenteColor',      replay: 'replayOPenteGame' },
  { names: ['SWAP2PENTE'],  color: 'swap2PenteColor',  replay: 'replayPenteGame' },
  { names: ['SWAP2KERYO'],  color: 'swap2KeryoColor',  replay: 'replayKeryoPenteGame' },
];

const REPLAY_FNS = [
  'replayPenteGame','replayKeryoPenteGame','replayGomokuGame','replayGPenteGame',
  'replayPoofPenteGame','replayConnect6Game','replayGoGame','replayOPenteGame',
];

let lastCalled = null;
const sandbox = { GAME, game: 0, whiteCaptures: 0, blackCaptures: 0 };
REPLAY_FNS.forEach(fn => { sandbox[fn] = () => { lastCalled = fn; }; });

const ctx = vm.createContext(sandbox);
vm.runInContext(colorLines, ctx);     // defines penteColor, ..., swap2KeryoColor
vm.runInContext(boardCommonSrc, ctx); // defines getBoardColor, replayGame

let checks = 0;
for (const fam of FAMILIES) {
  for (const short of fam.names) {
    for (const prefix of ['', 'SPEED_', 'TB_']) {
      const id = GAME[prefix + short];
      const expectedColor = ctx[fam.color];
      assert.strictEqual(
        ctx.getBoardColor(id), expectedColor,
        `getBoardColor(${prefix + short}=${id}) should be ${fam.color} (${expectedColor})`);

      sandbox.game = id;
      lastCalled = null;
      ctx.replayGame([], [], 0);
      assert.strictEqual(
        lastCalled, fam.replay,
        `replayGame for ${prefix + short}=${id} should call ${fam.replay}, called ${lastCalled}`);
      checks += 2;
    }
  }
}

// Unknown ids are loud.
assert.throws(() => ctx.getBoardColor(99999), /unknown game id/);
sandbox.game = 99999;
assert.throws(() => ctx.replayGame([], [], 0), /unknown game id/);

console.log(`OK: ${checks} mapping assertions + 2 throw assertions passed`);
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `node dsg_src/httpdocs/gameServer/js/boardCommon.test.js`
Expected: FAIL — `ENOENT` reading `boardCommon.js` (file not created yet).

- [ ] **Step 3: Write the minimal implementation**

Create `dsg_src/httpdocs/gameServer/js/boardCommon.js`:

```javascript
// Shared board-viewer helpers. Single source for board color + replay dispatch.
// Depends on globals defined elsewhere and loaded FIRST:
//   - GAME            : game-id constants, emitted by gameConstants.jspf
//   - color vars      : penteColor, keryoPenteColor, ... swap2KeryoColor  (tb/gameScript.js)
//   - replay*Game     : replayPenteGame, ... replayOPenteGame             (tb/gameScript.js)
//   - whiteCaptures / blackCaptures / game : page-level globals on each viewer page
// Unknown game ids throw on purpose, so a mis-set id fails loudly in the console.

function getBoardColor(game) {
   switch (game) {
      case GAME.PENTE: case GAME.SPEED_PENTE: case GAME.TB_PENTE:
         return penteColor;
      case GAME.KERYO: case GAME.SPEED_KERYO: case GAME.TB_KERYO:
         return keryoPenteColor;
      case GAME.GOMOKU: case GAME.SPEED_GOMOKU: case GAME.TB_GOMOKU:
         return gomokuColor;
      case GAME.DPENTE: case GAME.SPEED_DPENTE: case GAME.TB_DPENTE:
         return dPenteColor;
      case GAME.GPENTE: case GAME.SPEED_GPENTE: case GAME.TB_GPENTE:
         return gPenteColor;
      case GAME.POOF_PENTE: case GAME.SPEED_POOF_PENTE: case GAME.TB_POOF_PENTE:
         return poofPenteColor;
      case GAME.CONNECT6: case GAME.SPEED_CONNECT6: case GAME.TB_CONNECT6:
         return connect6Color;
      case GAME.BOAT_PENTE: case GAME.SPEED_BOAT_PENTE: case GAME.TB_BOAT_PENTE:
         return boatPenteColor;
      case GAME.DKERYO: case GAME.SPEED_DKERYO: case GAME.TB_DKERYO:
         return dkeryoPenteColor;
      case GAME.GO:  case GAME.SPEED_GO:
      case GAME.GO9: case GAME.SPEED_GO9:
      case GAME.GO13: case GAME.SPEED_GO13:
      case GAME.TB_GO: case GAME.TB_GO9: case GAME.TB_GO13:
         return goColor;
      case GAME.OPENTE: case GAME.SPEED_OPENTE: case GAME.TB_OPENTE:
         return oPenteColor;
      case GAME.SWAP2PENTE: case GAME.SPEED_SWAP2PENTE: case GAME.TB_SWAP2PENTE:
         return swap2PenteColor;
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         return swap2KeryoColor;
      default:
         throw new Error("getBoardColor: unknown game id " + game);
   }
}

// Signature is identical to the old inline versions, so NO call site changes.
// Reads the page globals `game`, `whiteCaptures`, `blackCaptures`.
function replayGame(abstractBoard, movesList, until) {
   whiteCaptures = 0;
   blackCaptures = 0;
   switch (game) {
      case GAME.PENTE: case GAME.SPEED_PENTE: case GAME.TB_PENTE:
      case GAME.DPENTE: case GAME.SPEED_DPENTE: case GAME.TB_DPENTE:
      case GAME.BOAT_PENTE: case GAME.SPEED_BOAT_PENTE: case GAME.TB_BOAT_PENTE:
      case GAME.SWAP2PENTE: case GAME.SPEED_SWAP2PENTE: case GAME.TB_SWAP2PENTE:
         replayPenteGame(abstractBoard, movesList, until); break;
      case GAME.KERYO: case GAME.SPEED_KERYO: case GAME.TB_KERYO:
      case GAME.DKERYO: case GAME.SPEED_DKERYO: case GAME.TB_DKERYO:
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         replayKeryoPenteGame(abstractBoard, movesList, until); break;
      case GAME.GOMOKU: case GAME.SPEED_GOMOKU: case GAME.TB_GOMOKU:
         replayGomokuGame(abstractBoard, movesList, until); break;
      case GAME.GPENTE: case GAME.SPEED_GPENTE: case GAME.TB_GPENTE:
         replayGPenteGame(abstractBoard, movesList, until); break;
      case GAME.POOF_PENTE: case GAME.SPEED_POOF_PENTE: case GAME.TB_POOF_PENTE:
         replayPoofPenteGame(abstractBoard, movesList, until); break;
      case GAME.CONNECT6: case GAME.SPEED_CONNECT6: case GAME.TB_CONNECT6:
         replayConnect6Game(abstractBoard, movesList, until); break;
      case GAME.GO:  case GAME.SPEED_GO:
      case GAME.GO9: case GAME.SPEED_GO9:
      case GAME.GO13: case GAME.SPEED_GO13:
      case GAME.TB_GO: case GAME.TB_GO9: case GAME.TB_GO13:
         replayGoGame(abstractBoard, movesList, until); break;
      case GAME.OPENTE: case GAME.SPEED_OPENTE: case GAME.TB_OPENTE:
         replayOPenteGame(abstractBoard, movesList, until); break;
      default:
         throw new Error("replayGame: unknown game id " + game);
   }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `node dsg_src/httpdocs/gameServer/js/boardCommon.test.js`
Expected: PASS — prints `OK: 90 mapping assertions + 2 throw assertions passed` (15 short names × 3 prefixes × 2 assertions = 90).

- [ ] **Step 5: Commit**

```bash
git add dsg_src/httpdocs/gameServer/js/boardCommon.js dsg_src/httpdocs/gameServer/js/boardCommon.test.js
git commit -m "Add shared board color + replay dispatch (boardCommon.js) with regression test"
```

---

## Task 2: `gameConstants.jspf` — emit GAME ids from GridStateFactory

**Files:**
- Create: `dsg_src/httpdocs/gameServer/gameConstants.jspf`

- [ ] **Step 1: Create the fragment**

Create `dsg_src/httpdocs/gameServer/gameConstants.jspf`. It is a static-include fragment; its `<%@ page import %>` applies to the including page's translation unit. Emit every id the helpers reference.

```jsp
<%@ page import="org.pente.game.GridStateFactory" %>
<script type="text/javascript">
var GAME = {
   PENTE:             <%= GridStateFactory.PENTE %>,
   SPEED_PENTE:       <%= GridStateFactory.SPEED_PENTE %>,
   TB_PENTE:          <%= GridStateFactory.TB_PENTE %>,
   KERYO:             <%= GridStateFactory.KERYO %>,
   SPEED_KERYO:       <%= GridStateFactory.SPEED_KERYO %>,
   TB_KERYO:          <%= GridStateFactory.TB_KERYO %>,
   GOMOKU:            <%= GridStateFactory.GOMOKU %>,
   SPEED_GOMOKU:      <%= GridStateFactory.SPEED_GOMOKU %>,
   TB_GOMOKU:         <%= GridStateFactory.TB_GOMOKU %>,
   DPENTE:            <%= GridStateFactory.DPENTE %>,
   SPEED_DPENTE:      <%= GridStateFactory.SPEED_DPENTE %>,
   TB_DPENTE:         <%= GridStateFactory.TB_DPENTE %>,
   GPENTE:            <%= GridStateFactory.GPENTE %>,
   SPEED_GPENTE:      <%= GridStateFactory.SPEED_GPENTE %>,
   TB_GPENTE:         <%= GridStateFactory.TB_GPENTE %>,
   POOF_PENTE:        <%= GridStateFactory.POOF_PENTE %>,
   SPEED_POOF_PENTE:  <%= GridStateFactory.SPEED_POOF_PENTE %>,
   TB_POOF_PENTE:     <%= GridStateFactory.TB_POOF_PENTE %>,
   CONNECT6:          <%= GridStateFactory.CONNECT6 %>,
   SPEED_CONNECT6:    <%= GridStateFactory.SPEED_CONNECT6 %>,
   TB_CONNECT6:       <%= GridStateFactory.TB_CONNECT6 %>,
   BOAT_PENTE:        <%= GridStateFactory.BOAT_PENTE %>,
   SPEED_BOAT_PENTE:  <%= GridStateFactory.SPEED_BOAT_PENTE %>,
   TB_BOAT_PENTE:     <%= GridStateFactory.TB_BOAT_PENTE %>,
   DKERYO:            <%= GridStateFactory.DKERYO %>,
   SPEED_DKERYO:      <%= GridStateFactory.SPEED_DKERYO %>,
   TB_DKERYO:         <%= GridStateFactory.TB_DKERYO %>,
   GO:                <%= GridStateFactory.GO %>,
   SPEED_GO:          <%= GridStateFactory.SPEED_GO %>,
   TB_GO:             <%= GridStateFactory.TB_GO %>,
   GO9:               <%= GridStateFactory.GO9 %>,
   SPEED_GO9:         <%= GridStateFactory.SPEED_GO9 %>,
   TB_GO9:            <%= GridStateFactory.TB_GO9 %>,
   GO13:              <%= GridStateFactory.GO13 %>,
   SPEED_GO13:        <%= GridStateFactory.SPEED_GO13 %>,
   TB_GO13:           <%= GridStateFactory.TB_GO13 %>,
   OPENTE:            <%= GridStateFactory.OPENTE %>,
   SPEED_OPENTE:      <%= GridStateFactory.SPEED_OPENTE %>,
   TB_OPENTE:         <%= GridStateFactory.TB_OPENTE %>,
   SWAP2PENTE:        <%= GridStateFactory.SWAP2PENTE %>,
   SPEED_SWAP2PENTE:  <%= GridStateFactory.SPEED_SWAP2PENTE %>,
   TB_SWAP2PENTE:     <%= GridStateFactory.TB_SWAP2PENTE %>,
   SWAP2KERYO:        <%= GridStateFactory.SWAP2KERYO %>,
   SPEED_SWAP2KERYO:  <%= GridStateFactory.SPEED_SWAP2KERYO %>,
   TB_SWAP2KERYO:     <%= GridStateFactory.TB_SWAP2KERYO %>
};
</script>
```

- [ ] **Step 2: Sanity-check it has no template gaps**

Run: `grep -c '<%= GridStateFactory' dsg_src/httpdocs/gameServer/gameConstants.jspf`
Expected: `45` (15 families × 3 variants). Full page-render verification happens in Task 12 once a page includes it.

- [ ] **Step 3: Commit**

```bash
git add dsg_src/httpdocs/gameServer/gameConstants.jspf
git commit -m "Add gameConstants.jspf emitting GAME ids from GridStateFactory"
```

---

## Tasks 3–11: Wire each page to the shared helpers

Each page edit is the **same four changes**. Apply per file using the exact anchor lines listed; read the file first to confirm the block boundaries (lines drift as you edit).

**The four changes (pattern):**

**(a) Add the constants include** immediately before the existing `tb/gameScript.js` `<script>` line.
- Top-level pages (`viewGameEmbed`, `viewLiveGameEmbed`, `viewLiveGameMobile`):
  `<%@ include file="gameConstants.jspf" %>`
- `tb/` pages: `<%@ include file="../gameConstants.jspf" %>`

**(b) Add boardCommon.js** immediately after the existing `tb/gameScript.js` `<script>` line:
`<script src="/gameServer/js/boardCommon.js"></script>`

**(c) Delete the inline `replayGame`** — remove the entire `function replayGame(abstractBoard, movesList, until) { ... }` block (the shared one replaces it). Its first line is at the "replayGame line" anchor.

**(d) Replace the inline color switch** inside `init()` — replace the whole `switch (game) { case ...: boardColor = X; ... }` block (from the `switch (game) {` at the "init switch" anchor through its closing `}`, i.e. the `}` right before the `boardContext.clearRect(...)` call) with a single line:
`      boardColor = getBoardColor(game);`

**Verification (every page task):** after the edit, confirm the structural deletions/additions:
```bash
# replace <FILE> with the page path
grep -c 'function replayGame' <FILE>            # expect 0
grep -c 'boardColor = getBoardColor(game)' <FILE>  # expect 1
grep -c 'boardCommon.js' <FILE>                 # expect 1
grep -c 'gameConstants.jspf' <FILE>             # expect 1
```
Then commit that page.

---

### Task 3: `viewGameEmbed.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/viewGameEmbed.jsp`
Anchors: gameScript include `197`; inline `replayGame` at `287` (switch at `290`); `init()` color switch at `390`.
- [ ] (a) include before line 197 → `<%@ include file="gameConstants.jspf" %>`
- [ ] (b) boardCommon.js after line 197
- [ ] (c) delete `replayGame` block at ~287
- [ ] (d) replace color switch at ~390 with `boardColor = getBoardColor(game);`
- [ ] Run the page verification greps (expect 0/1/1/1)
- [ ] Commit: `git commit -am "viewGameEmbed: use shared getBoardColor + replayGame"`

### Task 4: `viewLiveGameEmbed.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/viewLiveGameEmbed.jsp`
Anchors: gameScript include `322`; inline `replayGame` at `411` (switch `414`); `init()` color switch at `514`.
- [ ] (a) `<%@ include file="gameConstants.jspf" %>` before 322  (b) boardCommon.js after 322
- [ ] (c) delete `replayGame` at ~411  (d) replace color switch at ~514
- [ ] Run page verification greps; Commit `git commit -am "viewLiveGameEmbed: use shared getBoardColor + replayGame"`

### Task 5: `viewLiveGameMobile.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp`
Anchors: gameScript include `464`; inline `replayGame` at `554` (switch `557`); `init()` color switch at `678`.
- [ ] (a) `<%@ include file="gameConstants.jspf" %>` before 464  (b) boardCommon.js after 464
- [ ] (c) delete `replayGame` at ~554  (d) replace color switch at ~678
- [ ] Run page verification greps; Commit `git commit -am "viewLiveGameMobile: use shared getBoardColor + replayGame"`

### Task 6: `tb/mobileGame.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp`
Anchors: gameScript include `578`; `init()` color switch at `629`; inline `replayGame` at `1078` (switch `1081`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 578  (b) boardCommon.js after 578
- [ ] (c) delete `replayGame` at ~1078  (d) replace color switch at ~629
- [ ] Run page verification greps; Commit `git commit -am "tb/mobileGame: use shared getBoardColor + replayGame"`

### Task 7: `tb/cancelReply.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/cancelReply.jsp`
Anchors: gameScript include `418`; `init()` color switch at `468`; inline `replayGame` at `528` (switch `531`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 418  (b) boardCommon.js after 418
- [ ] (c) delete `replayGame` at ~528  (d) replace color switch at ~468
- [ ] Run page verification greps; Commit `git commit -am "tb/cancelReply: use shared getBoardColor + replayGame"`

### Task 8: `tb/undoReply.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/undoReply.jsp`
Anchors: gameScript include `400`; `init()` color switch at `452`; inline `replayGame` at `511` (switch `514`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 400  (b) boardCommon.js after 400
- [ ] (c) delete `replayGame` at ~511  (d) replace color switch at ~452
- [ ] Run page verification greps; Commit `git commit -am "tb/undoReply: use shared getBoardColor + replayGame"`

### Task 9: `tb/listedMobileGame.jsp`
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp`
Anchors: gameScript include `154`; `init()` color switch at `184`; inline `replayGame` at `245` (switch `248`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 154  (b) boardCommon.js after 154
- [ ] (c) delete `replayGame` at ~245  (d) replace color switch at ~184
- [ ] Run page verification greps; Commit `git commit -am "tb/listedMobileGame: use shared getBoardColor + replayGame"`

### Task 10: `tb/finalGo.jsp` (Go-only)
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/finalGo.jsp`
Anchors: gameScript include `485`; `init()` sets `boardColor = goColor;` directly at line `531` (no switch); inline `replayGame` at `554` (just calls `replayGoGame`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 485  (b) boardCommon.js after 485
- [ ] (c) delete the `function replayGame(...) { replayGoGame(...); }` block at ~554
- [ ] (d) replace the single line `boardColor = goColor;` at ~531 with `boardColor = getBoardColor(game);`
- [ ] Verify: `grep -c 'function replayGame' ...` → 0; `grep -c 'getBoardColor(game)' ...` → 1; includes present
- [ ] Commit `git commit -am "tb/finalGo: use shared getBoardColor + replayGame"`

### Task 11: `tb/deadGo.jsp` (Go-only)
**Files:** Modify `dsg_src/httpdocs/gameServer/tb/deadGo.jsp`
Anchors: gameScript include `497`; `init()` sets `boardColor = goColor;` directly at line `543` (no switch); inline `replayGame` at `767` (just calls `replayGoGame`).
- [ ] (a) `<%@ include file="../gameConstants.jspf" %>` before 497  (b) boardCommon.js after 497
- [ ] (c) delete the `function replayGame(...) { replayGoGame(...); }` block at ~767
- [ ] (d) replace the single line `boardColor = goColor;` at ~543 with `boardColor = getBoardColor(game);`
- [ ] Verify greps; Commit `git commit -am "tb/deadGo: use shared getBoardColor + replayGame"`

> NOTE on `deadGo.jsp`: it has multiple `drawGrid(boardContext, boardColor, gridSize, true)` calls that reuse the `boardColor` global set in `init()` — those keep working unchanged because `boardColor` is still assigned in `init()`, now via `getBoardColor(game)`.

---

## Task 12: Live-backend smoke verification

The local backend serves HTTPS on `https://localhost` (self-signed cert — ignore TLS errors). Static includes (`.jspf`) are merged at JSP translation time; because every page in Tasks 3–11 is itself edited, Tomcat recompiles it and picks up the new fragment. If a page somehow serves stale, restart the app container or `touch` the `.jsp`.

- [ ] **Step 1: Re-run the regression test** (guards the mapping regardless of backend)

Run: `node dsg_src/httpdocs/gameServer/js/boardCommon.test.js`
Expected: `OK: 90 mapping assertions + 2 throw assertions passed`

- [ ] **Step 2: Confirm static assets serve and the fragment renders real ints**

Using the Playwright MCP browser (accepts the self-signed cert):
1. `browser_navigate` to `https://localhost/gameServer/js/boardCommon.js` → page shows the JS source (200, not 404).
2. Obtain a real viewer URL: `browser_navigate` to the site's finished/live games listing and click into a game so a real `viewGameEmbed.jsp` / `viewLiveGameEmbed.jsp` / `tb/mobileGame.jsp` URL loads (these need valid game ids from the DB; do not hand-fabricate ids).
3. On the loaded viewer page run `browser_evaluate` with:
   ```js
   () => ({ game: typeof game !== 'undefined' ? game : null,
            hasGAME: typeof GAME === 'object' && Object.keys(GAME).length,
            color: (typeof boardColor !== 'undefined') ? boardColor : null })
   ```
   Expected: `game` is an int, `hasGAME` is `45`, `color` is a `#......` hex string.
4. `browser_console_messages` → expect **no** `Error` / `unknown game id` entries.

- [ ] **Step 3: Visual parity spot-check**

For at least Pente, a Go game, and one other family (e.g. Connect6 or O-Pente), load the viewer and confirm the board background color and the replayed stone layout look correct (compare against `git stash`-ed old page if unsure). `browser_take_screenshot` each for the record.

- [ ] **Step 4: Final verification summary**

Confirm and report: regression test green; `grep -rc 'function replayGame' dsg_src/httpdocs/gameServer/{*.jsp,tb/*.jsp}` shows `0` for all 9 edited pages; no console errors on the smoke-tested pages.

---

## Notes / Out of Scope
- Navigation helpers (`selectMove`/`goBack`/`goForward`) and `init()` canvas setup are **not** consolidated in this pass.
- The server-side `var game = <%= 50 + ... %>` expression in `viewGameEmbed.jsp` (a Java-side magic number) is left as-is; noted for a future cleanup.
- No changes to `tb/gameScript.js` internals.
