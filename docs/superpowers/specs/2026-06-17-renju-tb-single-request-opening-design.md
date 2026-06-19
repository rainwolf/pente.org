# Renju TB Single-Request Opening Contract — Design

**Date:** 2026-06-17
**Branch (backend):** `feat/renju` · **iOS:** `renju-turnbased` · **Android:** `feat/renju-android-tb`
**Status:** approved (scope + 3 decisions confirmed by user) — pending spec review

---

## Goal

Collapse the turn-based (HTTP / `MoveServlet`) Renju opening protocol so the
deciding player's submission after **any** swap window is a single request,
expressed as exactly one of three shapes, with branch A-vs-B implied by stone
count alone. Eliminate the multi-step `branch` → `offer` dance and the
`moves[0]` decline/take-over sentinel.

## Architecture

`MoveServlet` is the TB transport. It reconstructs the authoritative
`RenjuState` from persisted `(moves, renju_swaps, renju_offers)` on every
request and drives the engine's existing FSM methods. **The engine
(`RenjuState`) does not change** — only how the servlet maps wire actions onto
`renjuSwap` / `renjuBranch` / `offerFifthMoves` / `selectFifthMove`. The three
mobile clients (iOS, Android) and the JSP TB client (`mobileGame.jsp`) are
updated to emit the new contract.

## Tech Stack

Java/Tomcat servlet (backend), Objective-C (iOS `BoardViewController.m`),
Java/OkHttp (Android `OkHttpPenteApi` / `Game` / `BoardActivity`), JSP
(`mobileGame.jsp`). JUnit3 (`ant test-one`) for the engine; XCTest / Android
JUnit for clients.

---

## Background: the current contract (what we're replacing)

After move 4 the servlet accepts **five** `renjuAction` values
(`MoveServlet.java:409-569`):

| Action | Payload | Meaning |
|---|---|---|
| `swap` | `[1]` / `[0,<m>]` | `1` = take over; `0,<m>` = decline + place next stone (windows 1–3/5). At window 4, decline carries **no** stone. |
| `branch` | `[1]` / `[2]` | choose Branch A (1) or B (2) — a **separate** request |
| `offer` | `[m1..m10]` | Branch B: offer ten 5th-move candidates — a **separate** request |
| `select` | `[m]` | Branch B: opponent picks one of the ten |
| `move4` | `[d, m...]` | combined fold: `d`=1 decline / 0 swap-already-taken, then 1 stone = A, 10 = B |

So Branch B reaches the board in up to three requests
(`swap`-decline → `branch` → `offer`), and the `move4` fold that already bundles
it carries an awkward leading sentinel and competes with the granular path.

The LIVE transport (`ServerTable.handleRenju*`, WebSocket) is a **different,
fully event-driven contract and is explicitly out of scope.**

---

## Target contract: three actions for the whole opening

After `openingComplete`, opening stones are no longer special — the normal move
path applies (no `renjuAction`). During the opening the servlet accepts exactly:

| Action | Payload | Server behavior |
|---|---|---|
| `swap` | none (`moves` ignored) | Take over at the open swap window: `renjuSwap(game, true)`. Seats swap. No stone is placed. The next decision (branch / next stone) arrives as a subsequent `move`. |
| `move` | `m` (1 stone) | Auto-decline a pending swap (`renjuSwap(game, false)` iff `isAwaitingSwapDecision`), then: **at the branch point** (`numMoves==4`, branch unchosen) → Branch A: `renjuBranch(game, false)` + place move 5 (9×9 validated by the storer). **Otherwise** → place the single stone (move 2/3/4 after windows 1–3, move 6 after window 5, or a plain opening move). |
| `move` | `m1..m10` (10 stones) | Auto-decline a pending swap, then Branch B: `renjuBranch(game, true)` + validate & persist the ten offers. **Only valid at the branch point.** |
| `select` | `m5, m6` (2 stones) | Branch B: the opponent commits one of the ten offered as **move 5 (black)** *and* places their own **move 6 (white)** in the same request → opening complete. Atomic: nothing is stored unless both are legal. (Move parity: 5=black, 6=white; there is no swap window before move 6 in Branch B, so the two stones travel together.) |

### What this means per window

- **Windows 1–3** (after moves 1–3): `swap` = take over; `move`+1 stone =
  decline + place the next stone. (No Branch B here.)
- **Window 4** (after move 4 — the branch point): `swap` = take over (then the
  new mover sends a `move`); `move`+1 = decline + Branch A + move 5; `move`+10 =
  decline + Branch B + offer 10.
- **Branch-choice after a take-over at move 4**: same `move`+1 / `move`+10
  (no swap pending; `branchPoint` still true).
- **Window 5** (Branch A, after move 5): `swap` = take over; `move`+1 =
  decline + place move 6 → opening complete.
- **Branch B selection**: `select`+2 stones commits move 5 (chosen black offer)
  **and** move 6 (white) → opening complete. Move 6 in Branch B is therefore
  **not** a standalone step.
- **MOVE-phase stone** (move 1 only): `move`+1 just places. May also be sent as
  a plain move with no `renjuAction`. (Move 6 is bundled — Branch A into the
  window-5 `move`, Branch B into `select` — so move 1 is the sole pure
  MOVE-phase opening stone.)

### Removed

- `renjuAction=branch` (folded into the 1-vs-10 stone count of `move`).
- `renjuAction=offer` (folded into `move`'s 10-stone case).
- The `move4` action **name** and its `moves[0]` decline/take-over sentinel.
  (`move4`'s bundled behavior survives, renamed `move`, sentinel dropped.)
- The standalone "bare decline" path: declining is now implicit in sending a
  `move`. `swap` is therefore always a take-over (YES); it never carries `0`.

---

## MoveServlet driving logic (target)

```
if (game.getGame() == TB_RENJU && renjuAction != null) {
    RenjuOpeningState rst = RenjuOpeningState.decode(game.getRenjuSwaps());
    RenjuState pending = RenjuState.reconstruct(
            game, game.getRenjuSwaps(), game.getRenjuOffers());

    boolean ok =
        ("swap".equals(a)   && pending.isAwaitingSwapDecision())
     || ("move".equals(a)   && !pending.isOpeningComplete()
                            && !pending.isAwaitingFifthSelection())
     || ("select".equals(a) && pending.isAwaitingFifthSelection());
    if (!ok) { error("Renju action does not match the pending decision."); return; }

    if ("swap".equals(a)) {
        tbGameStorer.renjuSwap(game, true);                 // take over, no stone

    } else if ("select".equals(a)) {
        if (moves.length != 2) {                            // [chosen black 5th, white 6th]
            error("Select requires the chosen 5th move and your 6th move."); return;
        }
        int sel = moves[0], white6 = moves[1];
        if (!contains(game.getRenjuOffers(), sel)) {
            error("Selected move was not offered."); return;
        }
        // Atomic pre-check: replay selection + the white 6th on a reconstructed
        // board; store NEITHER stone if the 6th is illegal (occupied / out of
        // bounds / == selection). Mirrors invariant #1.
        RenjuState rs = RenjuState.reconstruct(game, game.getRenjuSwaps(), game.getRenjuOffers());
        try { rs.selectFifthMove(sel); rs.addMove(white6); }
        catch (RuntimeException bad) { error("Invalid selection or 6th move."); return; }
        tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),     sel);    // move 5 (black)
        tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves() + 1, white6); // move 6 (white)
        if (message != null) { message.setMoveNum(...); tbGameStorer.storeNewMessage(...); }

    } else { // "move"
        if (pending.isAwaitingSwapDecision()) {
            tbGameStorer.renjuSwap(game, false);            // decline
        }
        boolean branchPoint = (game.getNumMoves() == 4)
                && (rst.branch == RenjuOpeningState.PENDING);
        if (branchPoint) {
            if (moves.length == 1) {                        // Branch A
                tbGameStorer.renjuBranch(game, false);
                tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), moves[0]);
            } else if (moves.length == 10) {                // Branch B
                tbGameStorer.renjuBranch(game, true);
                TBGame fresh = tbGameStorer.loadGame(game.getGid());   // see branch=B
                RenjuState rs = RenjuState.reconstruct(fresh, fresh.getRenjuSwaps(), null);
                try { for (int m : moves) rs.offerFifthMove(m); }
                catch (RuntimeException bad) { error("Invalid 5th-move offer."); return; }
                int[] offers = new int[10];
                System.arraycopy(moves, 0, offers, 0, 10);
                fresh.setRenjuOffers(offers);
                tbGameStorer.renjuOffers(fresh);
            } else {
                error("At the branch point place 1 stone (Branch A) or 10 (Branch B).");
                return;
            }
        } else {                                            // windows 1-3 / 5 / MOVE
            if (moves.length != 1) { error("Expected a single move."); return; }
            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), moves[0]);
        }
        if (message != null) { message.setMoveNum(...); tbGameStorer.storeNewMessage(...); }
    }
}
```

Sequencing mirrors the **proven** `move4` paths: Branch A does
`renjuBranch(false)` then `storeNewMove` (no reload); Branch B reloads via
`loadGame` so the engine sees `branch=B` before validating offers. This is a
deliberate reuse of code already shipped and reviewed — not a new sequence.

---

## Invariants to preserve

1. **No mutation on rejection.** Branch B still validates all ten offers
   (`offerFifthMove` loop on a reconstructed state) before `setRenjuOffers` /
   `renjuOffers`. A bad offer set persists nothing.
2. **Phase guard before any mutation.** The `matchesPending` check runs first;
   a stray/duplicate `swap` must never re-run `renjuSwap(true)` and corrupt
   seats. (Equivalent to the existing guard, with the action set reduced to
   `swap`/`move`/`select`.)
3. **Branch-A 9×9 restriction** stays enforced by `storeNewMove`'s
   reconstruct-based validation (unchanged).
4. **White turn-clock reset on offer** (`CacheTBStorer.renjuOffers` sets
   `lastMoveDate` + recomputed `timeoutDate`) is unchanged and still on the
   Branch B path.
5. **Messages** attach on every stone-storing path, as today.
6. **Atomic select.** The 2-stone `select` stores neither move 5 nor move 6 if
   the selection is not among the offers or the white 6th is illegal (validated
   on a reconstructed board first). No half-completed opening.

**Plan-time verification (confirm before/while coding):**
- `storeNewMove(gid, moveNum, move)` index semantics for two sequential stores
  in one request (`getNumMoves()` then `getNumMoves()+1`, vs. reload between).
  The dpente start path stores 4 stones in one request — use it as the
  reference for how multi-stone submits index moves.
- Whether `RenjuState.addMove(white6)` actually throws on an occupied /
  out-of-bounds point, or whether an explicit `getPosition(white6)!=0 ||
  outOfBounds(white6)` pre-check is needed for the atomic guard above.

---

## Blast radius (files)

**Backend (`pente.org`, `feat/renju`)**
- `dsg_src/java/org/pente/turnBased/web/MoveServlet.java` — rewrite the
  `TB_RENJU && renjuAction != null` block (`~409-569`) to the 3-action form.
- `dsg_src/httpdocs/gameServer/tb/mobileGame.jsp` — emit `move` (1/10 stones)
  and `swap` (no stone); drop `branch`/`offer`/sentinel.

**iOS (`penteLive-iOS`, `renju-turnbased`)**
- `test1/BoardViewController.m` — replace the branch/offer/move4-sentinel
  submissions with `swap` (take over) and `move` (1 or 10 stones); the Branch B
  **select UI now collects two points** (the chosen offer + the white move 6)
  before submitting `select` with 2 stones.

**Android (`pentelive-android`, `feat/renju-android-tb`)**
- `app/src/main/java/be/submanifold/pentelive/Game.java` (and
  `BoardActivity.java`) — same action remapping; the select flow collects the
  chosen offer **and** the white move 6 into a 2-stone `select`.
  `OkHttpPenteApi.submitMove` already passes `renjuAction` through unchanged.
- `app/src/test/java/.../GameRenjuUnitTest.java`,
  `.../net/OkHttpPenteApiTest.java` — update to the new action strings/payloads.

**Docs (`pente.org`)**
- `docs/renju-integration-guide.md` §2.4 (the action contract table) +
  any §8/§9/§10/§11/§12 references to `branch`/`offer`/`move4`.
- `penteLive-iOS/docs/renju-handoff.md`, `pentelive-android/docs/renju-handoff.md`.

## Out of scope

- **LIVE transport / React live room** — fully event-driven, different
  contract, untouched.
- **`RenjuState` engine** — FSM methods unchanged; only `MoveServlet`'s driving
  of them changes. (If a new engine method genuinely simplifies the servlet, it
  is additive and optional — not required by this spec.)
- **Persistence schema** — `renju_swaps` / `renju_offers` / `pente_renju_offer`
  unchanged.

## Migration / compatibility

No client on `main` uses the old TB contract; the only consumers are the three
in-flight feature branches, all updated as part of this change. There is **no
backward-compatibility window** — the granular actions are removed, not
deprecated. Coordinated landing: backend + both client branches move together.

---

## Test plan

**Engine (no change expected, but assert it):** the existing
`RenjuStateTest` / `RenjuReconstructTest` / `TBGameRenjuPhaseTest` must stay
green unchanged — proof the FSM is untouched.

**MoveServlet dispatch (new coverage):** if a servlet test harness exists,
add focused cases; otherwise add a thin unit around the action→engine mapping.
Cases, one per row of the target table:
- `swap` at each window → seats swap, no stone, exactly once (duplicate `swap`
  rejected by the guard).
- `move`+1 at windows 1–3 → decline + place; at window 4 → Branch A + move 5
  (and the 9×9 restriction rejects an out-of-square move 5).
- `move`+10 at window 4 (fresh decline **and** post-take-over) → Branch B,
  offers persisted; a symmetric-duplicate offer set persists nothing.
- `move`+10 anywhere except the branch point → rejected.
- `select`+2 → commits move 5 (chosen black) **and** move 6 (white), opening
  completes; non-offered move 5 → rejected (nothing stored); illegal move 6
  (occupied / out of bounds / `== move 5`) → rejected (nothing stored);
  `select` with ≠2 stones → rejected.
- phase-mismatch (`swap` when not awaiting, `select` when not selecting,
  `move` when complete) → rejected.

**Clients:** update `GameRenjuUnitTest` / `OkHttpPenteApiTest` to assert the new
emitted action strings and payloads; manual TB round-trip QA (iostest /
app_tsetsoi) for one Branch A and one Branch B game end-to-end.

## Self-review notes

- Placeholder scan: pseudocode uses `...` only for the **unchanged** `select`
  and message blocks (copied verbatim in the plan) — not omitted logic.
- Consistency: action set `{swap, move, select}` is used identically in the
  guard, the dispatch, and the blast-radius/test sections.
- Ambiguity: "branch point" is defined once (`numMoves==4 && branch==PENDING`)
  and reused. 1-vs-10 stone count is the **only** A/B discriminator.
- Scope: single subsystem (TB opening protocol) across the repos that
  implement it; no decomposition needed.
