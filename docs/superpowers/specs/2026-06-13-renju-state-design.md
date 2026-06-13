# RenjuState + Taraguchi-10 — Design

Date: 2026-06-13
Branch: feat/renju
Status: approved-pending-review

## Goal

Add a `RenjuState` to `org.pente.game` that:

1. Enforces **Renju** win/forbidden semantics (black exact-5; white 5+; black
   may not play overline, double-three, or double-four).
2. Enforces the full **Taraguchi-10** opening protocol (per
   [RenjuNet rule 25](https://www.renju.net/rule/25/)), including swap options
   and the two post-move-4 branches.

The forbidden-point logic is a faithful Java port of the C++
`CForbiddenPointFinder` in `../ForbiddenPointFinder`.

## Decisions (locked during brainstorming)

- **Board size**: parametric — taken from the wrapped `GridState`
  (`getGridSizeX/Y`). Works on any odd N (15 or 19). Central-square radii and
  the center cell derive from `size/2`.
- **Opening scope**: full Taraguchi-10 protocol (swaps + branch + 10-offer +
  symmetry dedup).
- **Class structure**: decorator over `SimpleGomokuState`, mirroring
  `SimplePenteState`.
- **Forbidden move**: **blocked** in `isValidMove` — a black forbidden point is
  simply not a legal move. No allow-then-lose state.
- **Swap model**: **sequence + record only**. `RenjuState` drives the opening
  via `getCurrentPlayer()` and records decisions via setter hooks + getter
  flags. The seat↔color reassignment stays in the controller layer, exactly as
  `swap2`/`dPente` do today (the codebase represents stone color purely by move
  parity: `getCurrentColor() = numMoves%2 + 1`, color 1 = black, color 2 =
  white; swaps are never recolored inside the GridState).

## Components

### 1. `RenjuForbiddenPointFinder` (new, pure logic)

Faithful port of `CForbiddenPointFinder`. No `GridState` dependency so it can be
unit-tested in isolation.

- **Board**: `char[size+2][size+2]` with `'$'` sentinel border; `'X'`=black,
  `'O'`=white, `'.'`=empty. Constructor takes board size (default 15).
- **Coordinate convention**: callers pass `x,y ∈ [0, size-1]`; internally
  `+1` for the bordered array (matches C++).
- **Mutators**: `clear()`, `setStone(x,y,c)`, `void setBoard(int[][] board)` /
  or `setStone` calls driven by `RenjuState` (maps color 1→'X', 2→'O', 0→'.').
- **Ported predicates** (direction `nDir ∈ {1,2,3,4}` = H, V, "/", "\\"):
  - `boolean isFive(x, y, color)` — exactly 5 for black; ≥5 for white.
  - `boolean isFive(x, y, color, dir)` — single-direction variant used by the
    four/open-four gap checks.
  - `boolean isOverline(x, y)` — black makes ≥6 (returns false if that
    direction is exactly 5).
  - `boolean isFour(x, y, color, dir)`
  - `int isOpenFour(x, y, color, dir)` → 0 / 1 / 2.
  - `boolean isOpenThree(x, y, color, dir)`
  - `boolean isDoubleFour(x, y)`
  - `boolean isDoubleThree(x, y)`
- **Public entry**: `boolean isForbidden(x, y)` =
  `isOverline || isDoubleFour || isDoubleThree` (black only; assumes cell
  empty). Optionally `findForbiddenPoints()` returning a `List<Coord>` for UI
  hinting (mirrors C++ `ptForbidden[]`).

Mutual recursion between `isOpenThree`, `isDoubleFour`, `isDoubleThree`,
`isOpenFour` is preserved exactly as in the C++ source (this is what prevents
false-positive threes). All predicates place a temp stone, evaluate, and
restore to `'.'`.

### 2. `RenjuState extends GridStateDecorator implements GomokuState, HashCalculator`

Wraps a `SimpleGomokuState` (board storage + Zobrist hashing). Constructors
mirror `SimplePenteState`: `RenjuState()` → 15×15; `RenjuState(GridState)`;
`RenjuState(int x, int y)`. Holds one `RenjuForbiddenPointFinder` sized to the
board, refreshed from the board on each `addMove`/`undoMove`.

Overridden methods:

- **`isGameOver()` / `getWinner()`** — uses the finder's `isFive` on the last
  move: black wins only on exact 5, white on ≥5. Draw at full board. Does not
  delegate to `SimpleGomokuState.isGameOver()` (that flag is symmetric and
  can't express the black-exact / white-overline asymmetry).
- **`isValidMove(move, player)`** — in order:
  1. bounds + occupied + `player == getCurrentPlayer()` (as today).
  2. **Opening restriction** (see state machine) — center / 3×3 / 5×5 / 7×7 /
     9×9 by move index; reject out-of-square.
  3. **Negotiation block** — if a swap decision or offer selection is pending,
     reject normal board moves (mirrors `dPenteWaitForSwap`).
  4. **Forbidden block** — once the opening is over and it's black to move,
     reject any move where `finder.isForbidden(x,y)`.
- **`getCurrentPlayer()`** — the Taraguchi-10 state machine (below). Falls
  through to `super.getCurrentPlayer()` after the opening completes.
- **`addMove(move)`** — delegate to gomoku, refresh finder from board, update
  hash.
- **`undoMove()`** — delegate, refresh finder, roll back opening-phase flags if
  the undo crosses a decision boundary (kept simple per `SimplePenteState`'s
  `canPlayerUndo` precedent: no undo across a resolved swap).
- **`canPlayerUndo(player)`** — block undo while a decision is pending /
  across a resolved swap, otherwise delegate.

### 3. Opening state machine (Taraguchi-10)

Stones are placed normally (color by parity). Swap decisions and the branch /
offer choices are recorded, not applied to stone color.

Let `c = size/2` (center), `r(k)` the half-width of the k×k central square
(`r = (k-1)/2`). Restriction by board-move count `n = getNumMoves()`:

| Move | n | Color | Restriction |
|------|---|-------|-------------|
| 1 | 0 | black | must be exactly `(c,c)` |
| 2 | 1 | white | within 3×3 (`|dx|,|dy| ≤ 1`) |
| 3 | 2 | black | within 5×5 (`≤ 2`) |
| 4 | 3 | white | within 7×7 (`≤ 3`) |
| 5 (Branch A) | 4 | black | within 9×9 (`≤ 4`) |
| 6 | 5 | white | anywhere |

**Swap option** after each of moves 1–4 (and move 5 in Branch A): the other
seat may swap (take over the side just played) before the next stone. Modeled
with a small phase enum + a pending-decision flag; `getCurrentPlayer()` returns
the deciding seat while pending, and `renjuSwapDecisionMade(boolean swap)`
resolves it.

**After move 4 — branch choice** (black decides):

- **Branch A**: black plays move 5 inside 9×9 → white gets a swap option →
  white plays move 6 anywhere.
- **Branch B (10-offer)**: black offers **10** candidate 5th moves, each
  anywhere, no two symmetrically equivalent. White picks one to become the real
  move 5; white then plays move 6 anywhere.

Branch B representation:

- Candidates staged in `List<Integer> offered5thMoves` — **not** added to the
  underlying move vector until selection.
- Black submits via `offerFifthMove(move)` (validates: empty cell, not a
  symmetric duplicate of an already-offered candidate). When 10 are collected,
  state advances to "await white selection".
- **Symmetry dedup**: a candidate is a duplicate if, under any of the 8 board
  symmetries that map the current 4-stone position onto itself, it coincides
  with an already-offered candidate. Implemented with the existing
  `getAllPossibleRotations` / `rotateMove` utilities on `SimpleGridState`.
- White selects via `selectFifthMove(move)` (must be one of the offered);
  that move is committed as move 5 via `addMove`. The other 9 are discarded.
- White then plays move 6 anywhere → opening complete.

Decision/branch hooks (public, controller-facing):

```
void renjuSwapDecisionMade(boolean swap)   // resolve a pending swap option
boolean isAwaitingSwapDecision()
void chooseBranch(boolean tenOffer)        // false = Branch A, true = Branch B
boolean isAwaitingBranchChoice()
void offerFifthMove(int move)              // Branch B: stage a candidate
List<Integer> getOfferedFifthMoves()
boolean isAwaitingFifthOffers()            // black still submitting 10
void selectFifthMove(int move)             // Branch B: white commits one
boolean isAwaitingFifthSelection()         // white to pick
boolean isOpeningComplete()
```

`getCurrentPlayer()` returns the seat that must act for whichever pending
state is active (black while offering, white while selecting, the non-mover
while a swap is pending, etc.).

## Data flow

```
controller → isValidMove(move, seat) ─┐
                                       ├─ opening gate (state machine)
                                       ├─ negotiation gate (pending decision)
                                       └─ forbidden gate (finder.isForbidden)
controller → addMove(move) → gomoku.addMove → finder.refresh(board) → updateHash
controller → renjuSwapDecisionMade / chooseBranch / offerFifthMove /
             selectFifthMove  (advance the state machine)
isGameOver/getWinner ← finder.isFive(lastMove, lastColor)
```

## Error handling

- Out-of-bounds / occupied / wrong-turn → `isValidMove` returns false (no
  throw), consistent with `SimplePenteState`.
- Negotiation hooks called out of sequence (e.g. `selectFifthMove` before 10
  offers, or a non-offered move) → `IllegalStateException` /
  `IllegalArgumentException`, since these are controller-protocol violations,
  not player input.
- Forbidden black move → not a valid move (false), never throws.

## Testing

JUnit (match existing test layout under the source tree — confirm location
during planning):

- **Finder unit tests** (no GridState): each predicate against hand-built
  positions — exact-5 vs overline, simple four, open four (type 1 & 2), open
  three, the canonical 3-3 and 4-4 forbidden shapes, and the non-forbidden
  cases the mutual recursion is designed to exclude (a "three" that can't become
  an open four). Cross-check a few positions against the C++ output if feasible.
- **Win semantics**: black exact-5 wins; black 6 (overline) is not a win and is
  forbidden; white 5 and white 6 both win.
- **Opening**: legal/illegal placement at each square (center, 3/5/7/9);
  swap-pending blocks moves; branch A full sequence; branch B — 10 offers,
  symmetric-duplicate rejection, selection commits move 5, white move 6 ends
  opening; post-opening forbidden enforcement switches on.
- **Undo**: within opening and across the post-opening boundary behaves like the
  `SimplePenteState` precedent.

## Out of scope (follow-ups)

- `GridStateFactory` registration / game-id allocation / `game_event` rows.
- React/Android/iOS UI for offering & selecting the 5th move and swap prompts.
- AI/engine support for Renju.
```
