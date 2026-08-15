# Addendum — React sweep landed after adjudication

`map-react.md` (281-line rewrite) completed after `verdict.md` was written. It does
not change the chosen spine (Grandfathered Registry + 6 grafts); it refines Stage 6
scope and adds one Stage 8 precondition.

## New facts

- **react_live_game_room**: unknown id never crashes — `boardGeometry.js variantKey()`
  falls through to an unconditional `return 'swap2-keryo'`, silently applying the
  wrong ruleset. One consolidated seam (`variantKey`/`gridSizeForGame`) + 2 side axes
  (`GameClass` `#isDPente`/`isConnect6` gates, `critical_captures` inline id list).
- **react_mmai**: 4 uncoordinated silent-fallback layers. Worst: `GameClass.isGameOver()`
  has no unknown-id branch → `winner` never set → game permanently unwinnable, zero
  error signal. `Board.js` paints ids >=25 as O-Pente skin; `game_name()` renders
  "Speed undefined".
- **WASM AI (react_mmai)**: compiled `Ai.cpp configFor` silently falls back to plain-Pente
  rules for unmapped ids (documented in its own comment). Cannot be hot-patched —
  new game ⇒ WASM rebuild.
- **No metadata channel**: neither app reads variant metadata from the server today;
  ids travel as bare untyped numbers. (Consistent with graft G4: lgr should adopt
  `ServerData.gameEvents`; mmai has no `ServerData` → `/api/games`.)

## Plan impact

1. **Stage 6 scope** now enumerated precisely: retrofit lgr's single seam; consolidate
   mmai's 4 chains into one classifier; replace every silent catch-all in both apps
   with explicit reject-unknown guards (sites cataloged in map-react.md Part 3).
2. **New Stage 8 precondition**: react_mmai must not be offered a new id until its
   classifier + WASM `configFor` know it (both server-deployed, so still silent —
   but WASM rebuild is a required build step per new game with AI support).
3. Whitelists needing lockstep updates per new game until Stage 6/G4 land:
   lgr `STANDARD_GAME_IDS`, mmai picker array, WASM `configFor` (+ mobile lists).
