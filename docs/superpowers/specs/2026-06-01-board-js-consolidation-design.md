# Board-Canvas JS Consolidation — Design

**Date:** 2026-06-01
**Area:** `dsg_src/httpdocs/gameServer/`

## Goal

Eliminate duplicated board-canvas JavaScript spread across the game-viewer pages so
that the two repeated pieces of logic — **board color selection** and **replay
dispatch** — live in exactly one place. Game identifiers used by that logic must be
sourced from `org.pente.game.GridStateFactory` (the Java source of truth) instead of
hard-coded magic numbers (`51`, `53`, `69`, …).

## Current State

Already shared in `tb/gameServer/tb/gameScript.js`:
- Color variables: `penteColor`, `keryoPenteColor`, `gomokuColor`, `dPenteColor`,
  `gPenteColor`, `poofPenteColor`, `connect6Color`, `boatPenteColor`, `goColor`,
  `oPenteColor` (verify `keryoPenteColor` and `dkeryoPenteColor` are actually defined —
  they are referenced by the switches; if missing that is a pre-existing latent bug to
  surface, not silently introduce).
- Per-game replay functions: `replayPenteGame`, `replayKeryoPenteGame`,
  `replayGomokuGame`, `replayGPenteGame`, `replayPoofPenteGame`, `replayConnect6Game`,
  `replayGoGame`, `replayOPenteGame`.
- Drawing primitives: `drawGrid`, `drawStone`, etc.

Duplicated **inline** in each of these 9 board pages:
1. The `boardColor` selection `switch (game)` inside `init()` (~40 lines each).
2. The `replayGame(abstractBoard, movesList, until)` dispatcher `switch (game)` (~45
   lines each).

Both switch on a global integer `var game` whose values equal `GridStateFactory`
identifiers (`TB_PENTE = TB_START + PENTE = 51`, `TB_GO = 69`, …).

Confirmed facts that make the change small:
- All 9 pages already `<script src="/gameServer/tb/gameScript.js">`.
- All 9 pages already define a global `var game = …`.
- Several pages already `import="org.pente.game.GridStateFactory"`.

Affected files:
- `viewGameEmbed.jsp`
- `viewLiveGameEmbed.jsp`
- `viewLiveGameMobile.jsp`
- `tb/mobileGame.jsp`
- `tb/cancelReply.jsp`
- `tb/undoReply.jsp`
- `tb/listedMobileGame.jsp`
- `tb/finalGo.jsp` (Go-only `replayGame`)
- `tb/deadGo.jsp` (Go-only `replayGame`)

## Design

### Component 1 — `gameConstants.jspf` (new)

Path: `dsg_src/httpdocs/gameServer/gameConstants.jspf`

A JSP fragment that imports `GridStateFactory` and emits a single global `GAME` object
into a `<script>` block, so the values come straight from the Java class at render time
(one source of truth):

```jsp
<%@ page import="org.pente.game.GridStateFactory" %>
<script type="text/javascript">
var GAME = {
   PENTE:        <%= GridStateFactory.PENTE %>,
   SPEED_PENTE:  <%= GridStateFactory.SPEED_PENTE %>,
   KERYO:        <%= GridStateFactory.KERYO %>,
   /* … all live ids 1–30 … */
   TB_PENTE:     <%= GridStateFactory.TB_PENTE %>,
   TB_KERYO:     <%= GridStateFactory.TB_KERYO %>,
   /* … all TB ids 51–79 … */
   TB_SWAP2KERYO:<%= GridStateFactory.TB_SWAP2KERYO %>
};
</script>
```

Covers both the live (`1–30`) and turn-based (`51–80`) id ranges so the shared helpers
work on any page regardless of which range that page's `game` falls in.

### Component 2 — `js/boardCommon.js` (new)

Path: `dsg_src/httpdocs/gameServer/js/boardCommon.js`

Static JS holding the two consolidated functions. It depends on globals provided by
`gameConstants.jspf` (`GAME`) and `gameScript.js` (color vars, `replay*Game`,
`whiteCaptures`/`blackCaptures`), so it MUST load after both. A header comment documents
that dependency.

```js
// Depends on: GAME (gameConstants.jspf) and the color vars + replay*Game functions
// defined in tb/gameScript.js. Load this AFTER gameScript.js.

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
         return penteColor;
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         return keryoPenteColor;
      default:
         return penteColor; // safe fallback (matches default board appearance)
   }
}

// Signature matches every existing call site, so NO call sites change.
// Reads global `game`, `whiteCaptures`, `blackCaptures` exactly like the inline
// versions did.
function replayGame(abstractBoard, movesList, until) {
   whiteCaptures = 0;
   blackCaptures = 0;
   switch (game) {
      case GAME.GOMOKU: case GAME.SPEED_GOMOKU: case GAME.TB_GOMOKU:
         replayGomokuGame(abstractBoard, movesList, until); break;
      case GAME.GPENTE: case GAME.SPEED_GPENTE: case GAME.TB_GPENTE:
         replayGPenteGame(abstractBoard, movesList, until); break;
      case GAME.POOF_PENTE: case GAME.SPEED_POOF_PENTE: case GAME.TB_POOF_PENTE:
         replayPoofPenteGame(abstractBoard, movesList, until); break;
      case GAME.CONNECT6: case GAME.SPEED_CONNECT6: case GAME.TB_CONNECT6:
         replayConnect6Game(abstractBoard, movesList, until); break;
      case GAME.KERYO: case GAME.SPEED_KERYO: case GAME.TB_KERYO:
      case GAME.DKERYO: case GAME.SPEED_DKERYO: case GAME.TB_DKERYO:
      case GAME.SWAP2KERYO: case GAME.SPEED_SWAP2KERYO: case GAME.TB_SWAP2KERYO:
         replayKeryoPenteGame(abstractBoard, movesList, until); break;
      case GAME.GO:  case GAME.SPEED_GO:
      case GAME.GO9: case GAME.SPEED_GO9:
      case GAME.GO13: case GAME.SPEED_GO13:
      case GAME.TB_GO: case GAME.TB_GO9: case GAME.TB_GO13:
         replayGoGame(abstractBoard, movesList, until); break;
      case GAME.OPENTE: case GAME.SPEED_OPENTE: case GAME.TB_OPENTE:
         replayOPenteGame(abstractBoard, movesList, until); break;
      // PENTE, DPENTE, BOAT_PENTE, SWAP2PENTE → replayPenteGame
      default:
         replayPenteGame(abstractBoard, movesList, until); break;
   }
}
```

Game→handler mapping is taken verbatim from the existing dispatcher:
Pente / D-Pente / Boat-Pente / Swap2-Pente → `replayPenteGame`;
Keryo / D-Keryo / Swap2-Keryo → `replayKeryoPenteGame`; the rest as shown.

> The exact case→handler and case→color mapping in this file is the single
> authoritative copy; the implementation plan must reproduce the current behavior
> precisely (cross-check every existing inline switch before deleting it).

### Component 3 — Per-page edits (×9)

For each affected page:
1. Include the constants fragment before any inline script that runs `init()`:
   `<%@ include file="gameConstants.jspf" %>` (use the correct relative path —
   `gameConstants.jspf` for top-level pages, `../gameConstants.jspf` for `tb/` pages,
   matching the existing `ratings.jspf` include convention).
2. Add `<script src="/gameServer/js/boardCommon.js"></script>` immediately after the
   existing `gameScript.js` include.
3. Delete the inline `function replayGame(abstractBoard, movesList, until) { … }`
   definition.
4. Replace the inline `boardColor` `switch` in `init()` with
   `boardColor = getBoardColor(game);`.

`finalGo.jsp` / `deadGo.jsp` currently define a Go-only `replayGame` that just calls
`replayGoGame`; they use the shared dispatcher like everyone else (their `game` is
always a Go id, which routes to `replayGoGame`).

## Load Order / Dependency Contract

`GAME` (gameConstants.jspf) → `gameScript.js` (colors + `replay*Game`) →
`boardCommon.js` (`getBoardColor`, `replayGame`) → inline `init()` / `replayGame(...)`
calls at page bottom. `boardCommon.js` documents this dependency at the top.

## Error Handling

- `getBoardColor`: unknown id → returns `penteColor` (a valid, neutral default).
- `replayGame`: unknown id → falls through to `replayPenteGame` (the most common game),
  matching the spirit of the current code where Pente variants are the default group.

## Testing

No automated JS test harness exists in the repo. Verification is manual:
- Load each of the 9 pages for a representative spread of game types — at minimum
  Pente, Keryo-Pente, Gomoku, Go, Connect6, O-Pente — and confirm the board color and
  the replayed position render identically to the pre-refactor pages.
- Spot-check capture counters and move navigation still update on replay.

## Out of Scope

- Navigation helpers (`selectMove` / `goBack` / `goForward`) and `init()` canvas setup —
  may share structure but are explicitly excluded from this pass.
- The Java-side expression `var game = <%= 50 + … %>` in `viewGameEmbed.jsp` (a
  server-side magic number). Note it for a future cleanup; leave it here.
- Anything inside `gameScript.js`'s existing functions.
