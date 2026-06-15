# Renju Archival Persistence (pente_game write/read) — Design

Date: 2026-06-15
Branch: feat/renju
Status: implemented (manual DB round-trip pending)

## Goal

Completed Renju games — both **TB-archived** and **live** — must persist their
Taraguchi-10 opening record (the `renju_swaps` packed word + the 10
`renju_offers`) into `pente_game` / `pente_renju_offer`, load it back into
`GameData`, and expose it in the JSON game endpoint. Closes the confirmed gap:
today neither archival path carries this state, so an archived Branch-B Renju
game loses its 10 offers and `pente_game.renju_swaps` stays NULL.

This is sub-project 1 of the live-Renju work (sub-projects 2 = live `ServerTable`
opening routing, 3 = React client — both out of scope here). **Backend-only.**
**Preserve only** — store/load/expose the data; rendering the offer phase in the
historic viewers is a separate later pass.

## Background (verified)

- A completed game is archived as a `GameData` via `GameStorer.storeGame` into
  `pente_game`. Two builders produce that `GameData`:
  - **TB:** `CacheTBStorer.storeGameDSG` (≈772-900) `new DefaultGameData()`; also
    `TBGame.convertToGameData` (≈656).
  - **Live:** `ServerTable.getGameData(winner, status)` (≈2763-2872), persisted at
    `ServerTable` ≈3079 (`gameDbStorer.storeGame`).
- `GameData` / `DefaultGameData` have **no** Renju fields (they carry `swapped`,
  `swap2Pass`, etc.). Nothing writes `pente_renju_offer`.
- `MySQLPenteGameStorer.loadGame` already re-adds the **implicit center** stone
  (board-aware via `getCenterMove`, gated by `firstMoveCanBeOffCenter`) — that
  stays. The schema (`pente_game.renju_swaps`, table `pente_renju_offer`) already
  exists.
- Live color = seat (seat 1 white, seat 2 black); the live opening runs in the
  in-memory `RenjuState` held as `ServerTable.gridState`.

## Decisions (locked during brainstorming)

- **`renjuSwaps` is a nullable `Integer`** on `GameData`: `null` = non-Renju (column
  NULL, no offer rows), a value = the packed `RenjuOpeningState` word. `int` 0 is a
  *valid* Renju value (fresh/all-pending), so it can't be the non-Renju sentinel.
  Matches the nullable `pente_game.renju_swaps` column and the existing
  `Boolean swap2pass` / `Integer renjuSwaps` (in `GameResponse`) precedent.
- **`renjuOffers`** = `int[]` (`null` = none).
- Set the fields **only when the game is Renju**; everything else stays null.
- Preserve-only; no viewer changes.

## Components

### 1. `GameData` (interface) + `DefaultGameData`
Add:
```java
Integer getRenjuSwaps();        // null = non-Renju; else packed RenjuOpeningState word
void    setRenjuSwaps(Integer renjuSwaps);
int[]   getRenjuOffers();       // null = none; else the offered 5th moves
void    setRenjuOffers(int[] renjuOffers);
```
`DefaultGameData` stores them; defaults `null`/`null`. Any other `GameData`
implementations get the same no-op-friendly accessors.

### 2. `RenjuState` exposers (used by the live builder + sub-project #2)
- `int getRenjuSwapsPacked()` — encode the engine's **resolved** opening decisions
  into the `RenjuOpeningState` word (swap1-4, branch, swap5). At game-over all are
  resolved; the live builder only calls this when the game is over.
- `int[] getOfferedFifthMoves()` — already present (returns the offered list);
  expose as `int[]` for archival.

### 3. Archival builders — set the Renju fields when Renju
- **`CacheTBStorer.storeGameDSG`** (+ `TBGame.convertToGameData`): if
  `game.getGame() == TB_RENJU`, `gameData.setRenjuSwaps(tbGame.getRenjuSwaps())` and
  `setRenjuOffers(tbGame.getRenjuOffers())`; else leave null.
- **`ServerTable.getGameData`**: if the game is Renju (gridState instanceof
  `RenjuState`), `gameData.setRenjuSwaps(((RenjuState) gridState).getRenjuSwapsPacked())`
  and `setRenjuOffers(((RenjuState) gridState).getOfferedFifthMoves())`.

### 4. `MySQLPenteGameStorer.storeGame`
- Add `renju_swaps` to the `pente_game` INSERT column list; bind
  `gameData.getRenjuSwaps()` (set NULL when the getter returns null).
- After the row insert, if `getRenjuOffers() != null`, insert one
  `pente_renju_offer(gid, site_id, offer_num, move)` row per offer (a prepared
  batch). Use the same `gid`/`site_id` the game row uses.

### 5. `MySQLPenteGameStorer.loadGame`
- Select `renju_swaps` into the result handling → `gameData.setRenjuSwaps(...)`
  (NULL → null).
- Load `pente_renju_offer` rows (ordered by `offer_num`) → `int[]` →
  `gameData.setRenjuOffers(...)` (none → null). Keep the existing implicit-center
  re-add.

### 6. `GameResponse.buildHistoric` (JSON)
- Emit `renjuSwaps` / `renjuOffers` from the loaded `GameData` (the active-game
  `build(...)` already emits these from the `TBGame`). `buildHistoric` currently
  passes trailing nulls — populate them from `gameData.getRenjuSwaps()` /
  `getRenjuOffers()`.

## Data flow

```
TB complete   → storeGameDSG / convertToGameData ─┐
live complete → ServerTable.getGameData          ─┴→ GameData{renjuSwaps:Integer, renjuOffers:int[]}
              → MySQLPenteGameStorer.storeGame → pente_game.renju_swaps + pente_renju_offer rows
view historic → loadGame → GameData{...} → GameResponse.buildHistoric → JSON
              (viewers still replay the move list; offers are available but not rendered yet)
```

## Error handling / compatibility

- Non-Renju games: getters return null → `renju_swaps` written NULL, no offer rows.
- Pre-existing historic games (NULL `renju_swaps`, no offer rows): load as
  null/none. **Backward compatible — no migration of existing rows.**
- A Renju game with no offers (Branch A / opening not reached B): `renjuOffers`
  null → no `pente_renju_offer` rows; `renju_swaps` still written.
- `pente_renju_offer` insert failures are storage errors (propagate as the storer
  already does); not move-validation.

## Testing

- **`RenjuState.getRenjuSwapsPacked()`** — pure unit test: drive a known opening
  (via the existing hooks) and assert the packed word round-trips with
  `RenjuOpeningState.decode`.
- **`GameData`/`DefaultGameData`** accessors — trivial; covered by compile + the
  round-trip reasoning.
- **`MySQLPenteGameStorer` store/load** — needs a live MySQL (the repo excludes
  DB-coupled storer tests, e.g. `TBStorerTest`), so verified by **clean compile**
  + a documented manual round-trip (archive a Branch-B TB Renju game, confirm
  `pente_renju_offer` rows + `pente_game.renju_swaps`, reload, check `GameData`).
  No fabricated DB tests.

## Out of scope (later sub-projects / passes)

- Live `ServerTable` opening-decision routing + the new `DSG…TableEvent` types
  (sub-project 2).
- React `react_live_game_room` opening UI (sub-project 3; guide-driven).
- Rendering the offer phase in `viewLiveGameMobile/Embed` during historic replay.
