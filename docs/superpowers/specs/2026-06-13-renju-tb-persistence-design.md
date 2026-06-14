# Renju Turn-Based Persistence + Opening-State Storage — Design

Date: 2026-06-13
Branch: feat/renju
Status: implemented (TB path; live pente_game write deferred)

## Goal

Persist Renju's Taraguchi-10 opening state (swap decisions, branch choice, the
10 offered 5th moves, and the selection) so turn-based Renju games can be saved,
reloaded, and resumed mid-opening across days, and so completed games (live and
TB) can be replayed faithfully. Wire it through `TBGame`, `MySQLTBGameStorer`,
`CacheTBStorer`, and the turn-based servlets.

Builds on the engine + factory work already on `feat/renju`
(`RenjuState`, `RenjuForbiddenPointFinder`, `GridStateFactory` registration).

## Background / precedent

Opening decisions in this codebase are **persisted as inline scalar columns on
the game row and never reconstructed** (verified):

- `swap2`: `tb_game.swap2pass tinyint`, `pente_game.swap2pass tinyint`; set by
  `MySQLTBGameStorer.swap2Pass(game)`, restored in `fillGame()`.
- `dPente`: `tb_game.dpente_state tinyint` (phase) + `tb_game.dpente_swap
  enum('Y','N')`; set by `MySQLTBGameStorer.dPenteSwap(game, swap)`, which also
  **physically swaps `p1_pid`↔`p2_pid`** on a swap, and is restored in
  `fillGame()`.

Moves live in child tables (`tb_move(gid, move_num, move)`,
`pente_move(...)`) — separate id sequences per table. For TB, the whole `TBSet`
(containing `TBGame`s) is Java-serialized into one Redis aggregate
(`SID_TO_TB_SET`), so new `TBGame` fields persist to Redis automatically; MySQL
is the durable mirror written via the storer's explicit update methods.

Live games (`pente_game`) are written **only at game-over**
(`ServerTable.updateDatabaseAfterGameOver`, all-human games). During live play
the opening state lives only in the in-memory `ServerTable`/`RenjuState`.

Renju generalizes the swap2/dPente pattern: up to five swap windows + a branch
choice + a 10-offer/selection sub-protocol.

## Decisions (locked during brainstorming)

- **Swap/branch state**: one **base-3 (ternary) packed** value, because a
  turn-based game reloads into an *unresolved* (pending) swap window, which a
  binary flag cannot represent. Stored as `renju_swaps smallint unsigned NULL`
  on **both** `tb_game` and `pente_game` (same encoding; completed games simply
  carry no `0` digit).
- **10 offered moves**: **retained permanently** for replay.
  - `tb_game`: `renju_offers varbinary(10) NULL` column (rides the Redis
    aggregate, fast per-request reload).
  - `pente_game`: a `pente_renju_offer` **side table** (no bloat on the
    high-volume row; only Branch-B Renju games get rows).
- On a swap = yes, the storer **swaps `p1_pid`↔`p2_pid`**, exactly like
  `dPenteSwap` (seat reassignment; the engine stays "sequence + record only").
- **This step implements the TB path** (`TBGame`, `MySQLTBGameStorer`,
  `CacheTBStorer`, turn-based servlets) + the schema for both tables. The live
  `pente_game` *write* path (populating `renju_swaps` / `pente_renju_offer` at
  game-over in `ServerTable`) is **deferred** to the live-wiring step; the
  schema is added now to avoid a second migration.

## Ternary encoding

Six base-3 digits, value `= Σ dᵢ · 3ⁱ`. Each digit ∈ `{0,1,2}`:

| digit | weight | decision | 0 | 1 | 2 |
|---|---|---|---|---|---|
| d0 | 1   | swap after move 1 (white decides) | pending | no | yes |
| d1 | 3   | swap after move 2 (black) | pending | no | yes |
| d2 | 9   | swap after move 3 (white) | pending | no | yes |
| d3 | 27  | swap after move 4 (white) | pending | no | yes |
| d4 | 81  | branch choice (black)     | pending | A  | B  |
| d5 | 243 | swap after move 5 (white, Branch A only) | pending | no | yes |

Max value `3⁶−1 = 728` → fits `smallint unsigned`. `NULL` ⇒ not a Renju game.

- In Branch B (`d4 == 2`) there is no move-5 swap; `d5` stays `0` (unused).
- The offer/selection sub-phase is **not** encoded — it is derivable from
  `d4 == 2` (Branch B) + the count of stored offers (0..10) + whether move 5
  exists in `tb_move`:
  - `d4==2`, offers < 10 → black still offering;
  - offers == 10, no move 5 → awaiting white's selection;
  - move 5 present → selected, opening continues.

Codec lives in a small helper (`RenjuOpeningState`) so encoding logic is in one
place and unit-tested independently of storage:

```
class RenjuOpeningState {
    int swap1, swap2, swap3, swap4;  // 0 pending / 1 no / 2 yes
    int branch;                       // 0 pending / 1 A / 2 B
    int swap5;                        // 0 pending / 1 no / 2 yes (Branch A)
    static RenjuOpeningState decode(int packed);
    int encode();
}
```

## Schema changes (`dsg_src/sql/schema.sql`)

```sql
ALTER TABLE tb_game    ADD COLUMN renju_swaps  smallint unsigned NULL;
ALTER TABLE tb_game    ADD COLUMN renju_offers varbinary(10)     NULL;
ALTER TABLE pente_game ADD COLUMN renju_swaps  smallint unsigned NULL;

CREATE TABLE pente_renju_offer (
    gid       bigint(20) unsigned NOT NULL DEFAULT 0,
    site_id   smallint(5) unsigned NOT NULL DEFAULT 0,
    offer_num tinyint(3) unsigned NOT NULL DEFAULT 0,   -- 0..9
    move      smallint(5) unsigned NOT NULL DEFAULT 0,  -- 15x15 position 0..224
    PRIMARY KEY (gid, site_id, offer_num)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
```

`renju_offers` is `varbinary(10)`: byte *i* = the *i*-th offered position
(0..224, one byte each since 15×15 = 225 ≤ 255). Length = number of offers so
far (0..10). `schema.sql` is regenerated from the live DB (per the SQL workflow),
so these go into the canonical `schema.sql` and the column adds are the migration.

## Components

### 1. `RenjuOpeningState` (new, pure codec) — `org.pente.game`

Encode/decode the ternary word; helpers `isSwapPending(int numMoves, boolean
branchB)`, `currentSwapWindow(numMoves)`, etc. No storage/engine dependency.
Unit-tested in isolation.

### 2. `RenjuState` reconstruction — `org.pente.game`

`getInstance(MoveData)` only replays moves (decisions lost). Add a controller-
facing reconstruction that re-applies decisions in protocol order:

```
static RenjuState reconstruct(MoveData moves, int renjuSwapsPacked, int[] offers)
```

Algorithm (interleaves move replay with decision hooks):
1. new RenjuState(15,15); decode the packed word.
2. addMove(move 1); if swap1 resolved → renjuSwapDecisionMade(swap1==2).
3. addMove(move 2); apply swap2. addMove(move 3); apply swap3.
   addMove(move 4); apply swap4.
4. if branch resolved → chooseBranch(branch==2):
   - **Branch A** (1): addMove(move 5); apply swap5; addMove(move 6); …replay rest.
   - **Branch B** (2): for each offer → offerFifthMove(offer); if move 5 exists →
     selectFifthMove(move5) (which commits it); addMove(move 6); …replay rest.
5. Stop early wherever the persisted state is still pending (mid-opening reload):
   the resulting `RenjuState` reports the correct pending decision via its
   existing `isAwaiting*` predicates.

This makes the persisted `renju_swaps` + `renju_offers` the single source of
truth that fully rehydrates engine state — no reconstruction gaps.

### 3. `TBGame` — `org.pente.turnBased`

New fields + accessors mirroring `dPenteState`/`swap2Pass`:

```
int renjuSwaps;        // packed ternary word (0 when not yet started / non-Renju)
int[] renjuOffers;     // null or up to 10 offered positions
```

Plus convenience predicates that delegate to a decoded `RenjuOpeningState`
(e.g. `isRenjuSwapPending()`, `isRenjuAwaitingBranch()`,
`isRenjuAwaitingOffers()`, `isRenjuAwaitingSelection()`), used by the servlet to
route a submitted action. `TBGame` is `Serializable` → the new fields ride the
Redis aggregate automatically.

### 4. `MySQLTBGameStorer` — `org.pente.turnBased`

- `TB_COLUMNS` / `fillGame()`: read `renju_swaps`, `renju_offers`.
- INSERT/UPDATE: write them.
- New decision-update methods, each mirroring `dPenteSwap`/`swap2Pass`
  (update the row + `last_move_date`/`timeout_date`; swap pids on swap=yes):
  - `renjuSwap(TBGame g, boolean swap)` — resolve the current pending swap
    window (window inferred from move count + decoded state); set its digit;
    swap `p1_pid`↔`p2_pid` if `swap`.
  - `renjuBranch(TBGame g, boolean tenOffer)` — set d4.
  - `renjuOffers(TBGame g)` — persist `renju_offers` (all offers submitted in one
    action by black).
  - `renjuSelect(TBGame g)` — the chosen 5th move is added as a normal move via
    the existing move-append path; this method persists any state advance.

### 5. `CacheTBStorer` — `org.pente.turnBased`

Mirror the new decision methods: mutate the cached `TBGame` inside the `TBSet`
aggregate, re-`hput` the set, and write through to `MySQLTBGameStorer` (same
pattern as the existing `dPenteSwap`/`swap2Pass` overrides). New `TBGame` fields
are already covered by aggregate serialization.

### 6. Servlets — `org.pente.turnBased.web`

`MoveServlet.doPost` currently branches on `dPenteState` for swap2/dPente
decisions. Add Renju routing (game id ∈ {RENJU, SPEED_RENJU, TB_RENJU}) that
inspects the decoded opening state and dispatches the submitted action:

- pending swap window → `tbGameStorer.renjuSwap(game, swap)`
- awaiting branch → `tbGameStorer.renjuBranch(game, tenOffer)`
- awaiting offers → validate 10 distinct, non-symmetric (engine's
  `offerFifthMove` rules) → `tbGameStorer.renjuOffers(game)`
- awaiting selection → validate the move is one of the offers → append move 5 +
  `tbGameStorer.renjuSelect(game)`
- otherwise → ordinary move (existing path), with the engine's `isValidMove`
  (incl. forbidden-point block once the opening completes)

The reconstruction (`RenjuState.reconstruct`) is invoked wherever the servlet
currently builds the `GridState` from a loaded `TBGame` for Renju games, so
turn/legality checks are correct mid-opening.

## Data flow

```
load:  MySQLTBGameStorer.fillGame → TBGame{renjuSwaps, renjuOffers}
       (or Redis TBSet aggregate) → RenjuState.reconstruct(moves, swaps, offers)
move submission (MoveServlet):
       decode TBGame opening state → route action →
         renjuSwap / renjuBranch / renjuOffers / renjuSelect / ordinary move
       → CacheTBStorer (aggregate + write-through) → MySQLTBGameStorer (row/offers)
       → swap=yes also swaps p1_pid↔p2_pid
```

## Error handling

- Action that doesn't match the current pending state (e.g. an offer submission
  when no offer is pending) → reject as an invalid move (servlet returns the
  existing error response), never corrupt state.
- Decode of an out-of-range `renju_swaps` (>728) → treat as data error, log;
  do not silently mis-decode.
- Offer validation (10 distinct, non-symmetric, empty cells) reuses the engine's
  `offerFifthMove` checks — single source of truth.

## Testing

- **`RenjuOpeningState`**: encode/decode round-trips for all digit combinations
  (exhaustive 0..728), pending/no/yes per window, Branch A vs B.
- **`RenjuState.reconstruct`**: a game persisted at every opening checkpoint
  (each pending swap, branch chosen A & B, mid-offer, awaiting selection, post-
  selection, opening complete) rebuilds to a state whose `isAwaiting*` /
  `getCurrentPlayer` / board match a live-played equivalent. Round-trip a full
  Branch-A and a full Branch-B game.
- **`MySQLTBGameStorer`**: store/load round-trip of `renju_swaps` + `renju_offers`
  (follow the existing storer test harness; confirm location during planning);
  `renjuSwap` swaps pids; offers persist/restore.
- **`CacheTBStorer`**: aggregate carries the fields; decision methods mutate +
  write through (mirror existing dPente/swap2 cache tests).
- **`MoveServlet`**: each opening action routes correctly and rejects
  out-of-sequence/illegal submissions.

## Out of scope (follow-ups)

- Live `pente_game` **write** path: populate `renju_swaps` + `pente_renju_offer`
  at game-over and reconstruct for live replay — part of the `ServerTable`
  live-wiring step (schema is added now; write path deferred).
- React clients (separately maintained submodules).
- AI engine support for Renju.
```
