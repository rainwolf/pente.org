# Original-Seats Helper for Swap-Variant Games — Design

Date: 2026-07-03
Status: approved

## Problem

In swap-capable variants (D-Pente, DK-Pente, Swap2-Pente, Swap2-Keryo, Renju/Taraguchi-10)
an opening swap changes who is player 1 mid-game:

- **Turn-based**: `TBGame.dPenteSwap()` / `TBGame.renjuSwap()` physically swap
  `player1Pid`/`player2Pid` (`TBGame.java:503-514`, `:572-591`).
- **Live**: `ServerTable.handleSwap` / `handleRenjuSwap` physically swap
  `sittingPlayers[]` and `playingPlayers[]` (`ServerTable.java:1198-1204`, `:1327-1368`).

No record of the *original* seating survives except the swap-decision fields.
This causes concrete bugs:

1. **End-of-game seat rotation** (`ServerTable.swapSeats()`, `:3314-3342`): live play
   rotates seats after each game so players alternate p1. The guard that skips rotation
   when an opening swap already flipped seats covers only the dpente family
   (`didDPenteSwap()`); Renju is missing. Renju also needs *net parity* — two take-overs
   cancel out and rotation should then still happen.
2. **Tournament rejoin desync** (`TournamentServerTable.handleJoin`, `:239-257`):
   re-seats players by `tourneyMatch.getPlayer1()/getPlayer2()` identity, ignoring an
   in-progress opening swap — overwrites the swapped arrangement mid-game.
3. **Tournament next-match retrieval** (`TournamentServerTable.updateDatabaseAfterGameOver`,
   `:321-325`): passes possibly-swapped `sittingPlayers` pids to the order-sensitive
   `TourneySection.getUnplayedMatch()` (`TourneySection.java:90-102`).
4. **Turn-based tournament**: same order-sensitive retrieval fed from swapped
   `TBGame` pids.

## Decision

Derive original seating from existing swap records — no schema change, no stored
original pids (rejected: DB migration + backfill; `inviterPid` rejected: inviter is
not reliably original p1).

**Concept**: one boolean per game — *net seat-swap parity*: "are current seats flipped
relative to game start?"

## Components

### 1. `RenjuOpeningState.netSwapped(int packed)` (static, + instance variant)

True iff the count of YES (=2) digits among `swap1..swap4` and `swap5` is odd.
The `branch` digit is a branch choice (A/B), **not** a seat swap — excluded.
Pending (0) and NO (1) digits contribute nothing, so the function is valid mid-game.

### 2. `TBGame.seatsSwapped()` + original-pid accessors

```java
boolean seatsSwapped()        // TB_DPENTE|TB_DKERYO|TB_SWAP2PENTE|TB_SWAP2KERYO -> dPenteSwapped
                              // TB_RENJU -> RenjuOpeningState.netSwapped(renjuSwaps)
                              // default -> false
long getOriginalPlayer1Pid()  // seatsSwapped() ? player2Pid : player1Pid
long getOriginalPlayer2Pid()  // symmetric
```

dpente family has a single swap opportunity, so the existing boolean equals parity.

### 3. `GridState.seatsSwapped()` (live surface)

Default method on `GridState` returning `false`. Overrides:

- `SimplePenteState` → `didDPenteSwap()`
- `RenjuState` → net parity of its recorded swap decisions (same digit semantics
  as `RenjuOpeningState.netSwapped`; reuse the static helper where possible)

Gives live tables one game-agnostic call; each implementation reads its own
native record.

## Call-site fixes

1. `ServerTable.swapSeats()` — replace the dpente-family guard with
   `gridState.seatsSwapped()`. No behavior change for the dpente family; fixes
   Renju (currently always rotates) and encodes parity semantics.
2. `TournamentServerTable.handleJoin` — seat = match role, flipped when a game is
   in progress and `gridState.seatsSwapped()`.
3. `TournamentServerTable.updateDatabaseAfterGameOver` — un-flip the
   `sittingPlayers` pid order via `seatsSwapped()` before calling
   `getUnplayedMatch()`.
4. Turn-based tournament unplayed-match lookup — use
   `TBGame.getOriginalPlayer1Pid()/getOriginalPlayer2Pid()` instead of current pids.

## Testing

- `RenjuOpeningState.netSwapped`: 0/1/2 YES swaps, `swap5` counted, `branch`
  digit ignored, pending/NO digits ignored.
- `TBGame`: original-pid round-trip after sequences of `renjuSwap`/`dPenteSwap`
  (swap, decline, double take-over); non-swap variants return current pids.
- `GridState.seatsSwapped`: default false; `SimplePenteState` follows
  `didDPenteSwap`; `RenjuState` parity incl. even-swap cancellation.
- `swapSeats()` guard: rotates on net-even, skips on net-odd, unchanged for
  dpente family.

## Out of scope

- Client-side (React/iOS/Android) seat display logic.
- Non-tournament arena rotation beyond the guard fix.
- Any DB schema change.
