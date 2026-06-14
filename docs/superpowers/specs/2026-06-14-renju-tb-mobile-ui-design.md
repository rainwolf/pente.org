# Renju Turn-Based Mobile UI (mobileGame.jsp + JSON) — Design

Date: 2026-06-14
Branch: feat/renju
Status: implemented (mobile JSP + JSON; manual render pending)

## Goal

Let a turn-based Renju player drive the Taraguchi-10 opening from the mobile web
client (`gameServer/tb/mobileGame.jsp`): swap decisions, the branch choice after
move 4, the 10 offered 5th moves, and white's selection — plus correct 15×15
board rendering and central-square hinting for opening stones. Expose the same
opening state in the JSON game endpoint (`gameServer/mobile/json/game.jsp`) so
native clients can consume it. The opening state is surfaced once on `TBGame`
so both views share a single source of truth.

Builds on the completed TB-persistence work (`RenjuState`,
`RenjuState.reconstruct`, `RenjuOpeningState`, `TBGame.renjuSwaps/renjuOffers`,
`MoveServlet` `renjuAction` routing).

## Decisions (locked during brainstorming)

- **Shared state on `TBGame`** — one derived getter (`getRenjuPhase()`) backed by
  `RenjuState.reconstruct`, consumed by both `mobileGame.jsp` and `GameResponse`
  (the JSON DTO). Not a serialized field; computed on demand.
- **10-offer UX**: tap empty points to add (counter `n/10`, tap again to remove);
  Submit enables at exactly 10. Symmetric-duplicate / illegal offers are rejected
  server-side (`offerFifthMove`) — client hinting is best-effort.
- **Opening-move limits**: client-side central-square hinting (block taps outside
  the allowed radius for the current opening move), on top of server validation.
- **Wire protocol**: the existing `renjuAction` request param (`swap`/`branch`/
  `offer`/`select`) that `MoveServlet` already parses. The JSON write path is the
  same `/gameServer/tb/game` servlet (no separate JSON move endpoint).

## Background (verified)

- `mobileGame.jsp` (1313 lines): JSP scriptlets + embedded JS, canvas board.
  `gridSize` defaults to 19, only Go variants override (lines ~120-125). Moves
  submitted via `window.open("/gameServer/tb/game?command=move&gid=...&moves=...
  &message=...")`. dPente/swap2 opening UI = conditional buttons (lines ~317-328)
  + JS functions posting `moves=0/1`. Move encoding `x + y*gridSize`.
- `game.jsp` (JSON) builds `new Gson().toJson(GameResponse.build(tbGame, ...))`.
  `GameResponse` (`org.pente.gameServer.mobile.GameResponse`) already emits
  `dPenteState`, `swap2pass`. Both views obtain the same `TBGame` via the storer.
- `TBGame.getCurrentPlayer()` already has a `TB_RENJU` branch decoding
  `renjuSwaps`, so `TBGame` already references `RenjuOpeningState` and is the
  natural home for derived Renju accessors.
- `coordinateLetters` is a 19-entry array (A–T skipping I); its first 15 entries
  are A–P skipping I = the standard 15×15 labels, so slicing to `gridSize` works.

## Components

### 1. `TBGame.getRenjuPhase()` (new derived getter)

```java
// returns null for non-Renju games; otherwise one of the phase constants
public String getRenjuPhase()
```

Phases (string constants on TBGame): `SWAP`, `BRANCH`, `OFFERS`, `SELECTION`,
`MOVE`, `COMPLETE`.

Implementation — delegate to the engine (single source of truth):

```java
public static final String RENJU_SWAP = "SWAP";
public static final String RENJU_BRANCH = "BRANCH";
public static final String RENJU_OFFERS = "OFFERS";
public static final String RENJU_SELECTION = "SELECTION";
public static final String RENJU_MOVE = "MOVE";
public static final String RENJU_COMPLETE = "COMPLETE";

public String getRenjuPhase() {
    if (game != GridStateFactory.TB_RENJU) {
        return null;
    }
    org.pente.game.RenjuState rs =
            org.pente.game.RenjuState.reconstruct(this, renjuSwaps, renjuOffers);
    if (rs.isAwaitingSwapDecision())    return RENJU_SWAP;
    if (rs.isAwaitingBranchChoice())    return RENJU_BRANCH;
    if (rs.isAwaitingFifthOffers())     return RENJU_OFFERS;
    if (rs.isAwaitingFifthSelection())  return RENJU_SELECTION;
    if (rs.isOpeningComplete())         return RENJU_COMPLETE;
    return RENJU_MOVE; // opening in progress, awaiting the next central-square stone
}
```

- Not serialized; recomputed per call. For non-Renju it returns immediately, so
  it adds nothing to normal games. Reconstruct replays ≤6 opening moves — trivial.
- `getRenjuOffers()` / `getRenjuSwaps()` already exist for rendering the offered
  points and decoding state.

### 2. `GameResponse` (JSON DTO) — new fields

Add three public final fields and populate in `build(...)`, mirroring the
`dPenteState`/`swap2pass` pattern (null for non-Renju):

```java
public final String renjuPhase;   // TBGame.getRenjuPhase()
public final String renjuOffers;  // comma-separated offered moves, or null
public final Integer renjuSwaps;  // packed word, or null
```

`build()` sets them when `tbGame.getGame() == GridStateFactory.TB_RENJU`
(`renjuOffers` joined from `tbGame.getRenjuOffers()`; null when no offers yet).
Native clients already POST opening actions to `MoveServlet` via `renjuAction`,
so only read fields are added here.

### 3. `mobileGame.jsp`

**a. Board size.** Add to the `gridSize` block:

```jsp
} else if (game.getGame() == GridStateFactory.TB_RENJU) {
    gridSize = 15;
}
```

Coordinate labels already work via the first `gridSize` letters.

**b. Opening UI.** In the decision-button region (mirroring the dPente/swap2
block), branch on `game.getRenjuPhase()` when it is the player's turn:

- `SWAP` → buttons "Swap (take over)" / "Don't swap" → `renjuSwapYes()` / `renjuSwapNo()`.
- `BRANCH` → "Branch A — place 5th in 9×9" / "Branch B — offer 10 moves" → `renjuBranchA()` / `renjuBranchB()`.
- `OFFERS` → the tap-to-add picker: a counter `n/10`, a "Submit offers" button
  (enabled at 10), instructions. Tapping the board adds/removes candidate points
  (client array `renjuOfferList`); Submit posts all 10.
- `SELECTION` → render the 10 stored offers (`game.getRenjuOffers()`) as
  highlighted candidate points; tapping one posts the selection.
- `MOVE` → ordinary stone placement, but the board tap handler blocks cells
  outside the allowed central square (see d).
- `COMPLETE` / null → existing normal play, unchanged.

**c. JS submission functions** (mirror dPente's `window.open` posts; include the
`message` field so chat isn't dropped):

```javascript
function renjuPost(action, moveStr) {
    window.open("/gameServer/tb/game?command=move&gid=" + <%=game.getGid()%>
        + cycleStr + hideStr
        + "&renjuAction=" + action
        + "&moves=" + moveStr
        + "&message=" + encodeURIComponent(document.getElementById('message').value),
        "_self");
}
function renjuSwapYes()  { renjuPost("swap", "1"); }
function renjuSwapNo()   { renjuPost("swap", "0"); }
function renjuBranchA()  { renjuPost("branch", "1"); }
function renjuBranchB()  { renjuPost("branch", "2"); }
function renjuSubmitOffers() {
    if (renjuOfferList.length !== 10) { alert("Pick exactly 10 offers."); return; }
    renjuPost("offer", renjuOfferList.join(","));
}
function renjuSelect()   {
    if (playedMove < 0) { alert("Tap one of the offered points."); return; }
    renjuPost("select", "" + playedMove);
}
```

**d. Central-square hinting** (phase `MOVE`). The allowed half-width by opening
move number `n = numMoves`:

| n | move | radius |
|---|------|--------|
| 0 | 1 | 0 (center only) |
| 1 | 2 | 1 (3×3) |
| 2 | 3 | 2 (5×5) |
| 3 | 4 | 3 (7×7) |
| 4 | 5 (Branch A) | 4 (9×9) |
| ≥5 | 6+ | unrestricted |

In JS: `center = Math.floor(gridSize/2)`; a candidate cell `(i,j)` is allowed iff
`Math.abs(i-center) <= radius && Math.abs(j-center) <= radius` (or radius ≥ 5 ⇒
any). The board tap handler ignores (or visually rejects) disallowed cells during
`MOVE`. The Submit/ordinary-move path is otherwise unchanged.

**e. Offer picker state.** A client array `renjuOfferList` of move ints; tap on an
empty allowed cell toggles membership (add if absent and `< 10`, remove if
present); the counter and the candidate markers reflect it; existing
single-`playedMove` logic is bypassed while in `OFFERS`.

## Data flow

```
load:  storer.loadGame(gid) -> TBGame
mobileGame.jsp:  game.getRenjuPhase() -> render the matching UI (+ getRenjuOffers for SELECTION)
JSON game.jsp:   GameResponse.build(tbGame,...) emits renjuPhase/renjuOffers/renjuSwaps
action:  window.open(.../game?command=move&renjuAction=<a>&moves=<...>) -> MoveServlet
         (already routes to renjuSwap/renjuBranch/renjuOffers/select)
```

## Error handling

- Illegal / symmetric / non-empty offers and out-of-square moves are rejected by
  the server (`offerFifthMove` / `isValidMove` via `reconstruct`), surfaced through
  the servlet's existing `handleError`. Client hinting reduces but does not replace
  this.
- `getRenjuPhase()` returns null for non-Renju → the JSP/JSON simply skip the
  Renju UI/fields (no NPEs; guarded by null/`!= TB_RENJU` checks).
- Submit-offers guards client-side on exactly 10; selection guards on a chosen
  point; both also validated server-side.

## Testing

- **`TBGame.getRenjuPhase()`** — unit-testable (pure, no DB): build a `TBGame`
  via `setMoves` + `setRenjuSwaps`/`setRenjuOffers` at each opening checkpoint and
  assert the phase string (SWAP/BRANCH/OFFERS/SELECTION/MOVE/COMPLETE) and null
  for a non-Renju game. New `TBGameRenjuPhaseTest` under `org/pente/turnBased/test`
  (confirm runnable harness during planning; if the TB test dir is DB-coupled,
  place the test under `org/pente/game/test` using a `SimpleGridState`-fed
  `MoveData` shim or a minimal `TBGame`).
- **`GameResponse`** — verified by clean compile; field population mirrors the
  existing `dPenteState` path.
- **`mobileGame.jsp`** — no automated test (JSP/canvas); verified by clean compile
  (`./justCompile` compiles JSPs? if not, manual render check). Manual scenario
  walk-through documented in the plan.

## Out of scope (follow-ups)

- Desktop `gameServer/tb/game.jsp` counterpart (same pattern; not requested).
- React clients (separately maintained submodules).
- Live (`pente_game`) Renju via `ServerTable`.
- AI engine support.
