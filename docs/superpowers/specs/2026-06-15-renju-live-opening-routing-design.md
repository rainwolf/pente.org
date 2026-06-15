# Renju Live Opening Routing (ServerTable) — Design

Date: 2026-06-15
Branch: feat/renju
Status: implemented (manual WS round-trip pending)

## Goal

Teach the live (WebSocket) game server to drive the Renju **Taraguchi-10**
opening. Today `ServerTable` only knows how to apply normal moves
(`DSGMoveTableEvent`) and the Pente swap2 pass (`DSGSwap2PassTableEvent`); it has
no path for Renju's per-window swap decisions, the Branch-A/B fork, the ten
Branch-B offers, or the selection. This sub-project adds three inbound events and
their `ServerTable` handlers, keeps the seat↔color binding correct across swaps,
echoes each decision to the other connected client, and re-sends the ten offers
to a client that (re)joins mid-offer.

This is **sub-project 2** of the live-Renju work. Sub-project 1 (archival
persistence) is done; sub-project 3 (React `react_live_game_room` opening UI) is
separate and guide-driven.

## Scope

- **Backend protocol + `ServerTable` routing only.** No client UI work. The React
  live-game-room and the mobile/iOS/Android clients are out of scope (guide-driven,
  separate sub-projects). End-to-end live play lights up once a client is wired;
  this sub-project makes the *server* accept, validate, apply, echo, and snapshot
  the opening.
- The opening *rules* already live in `RenjuState` (ported + unit-tested in prior
  work). This sub-project is the live transport/controller layer around them.

## Background (verified integration points)

Anchors are point-in-time (verified 2026-06-15); confirm exact lines during
implementation.

- **Transport is a hybrid, and the design is agnostic to it.** The game server
  fronts the event layer with BOTH a raw TCP socket (`SocketDSGEventHandler`) and a
  WebSocket (`WebSocketDSGEventHandler`). Crucially they share ONE codec: both
  deserialize an inbound message into a `DSGEventWrapper` via Gson, extract the
  single non-null concrete event via `getEncodedEvent()`, and route through
  `SynchronizedServerTable.callServerTable(DSGEvent)` (≈144-223), which pattern-
  matches the concrete type to a `serverTable.handleX(...)` call. They differ ONLY
  in wire framing — TCP delimits messages with a `255` byte, WS uses text frames.
  There is **no separate TCP event registry**. Therefore everything in this design
  lives in the shared, transport-agnostic layer (`DSGEventWrapper` +
  `SynchronizedServerTable` dispatch + `ServerTable` handlers + `dsgEventRouter`);
  the three events and their routing work identically over both transports with
  **zero per-transport code**.
- **Event registration is reflection-based, not discriminator-based, and shared by
  both transports.** `DSGEventWrapper` declares one private field per concrete event
  type (80+). On deserialize, Gson fills whichever field matches the JSON;
  `getEncodedEvent()` returns the non-null one; `getJSON()` serializes it back. Both
  `SocketDSGEventHandler` and `WebSocketDSGEventHandler` use this same wrapper. So a
  new event type added as a field (+ getter/setter) there is automatically
  (de)serializable on TCP **and** WS — one registration, both transports.
- **Seat ↔ player ↔ color.** `ServerTable` tracks `DSGPlayerData sittingPlayers[]`
  and `playingPlayers[]` (≈48), seat index **1 = black / first**, **2 = white /
  second**. `handleMove` (≈1549) maps player→seat (`getPlayerSeat`) and validates
  `gridState.getCurrentPlayer() != seat` (≈1564) before `gridState.addMove(move)`
  (≈1579), then broadcasts `new DSGMoveTableEvent(player, tableNum, move)` (≈1661).
- **Swap precedent.** The dPente swap (`handleSwap`, ≈1107-1148) swaps BOTH seat
  arrays:
  ```java
  DSGPlayerData tmp = playingPlayers[1];
  playingPlayers[1] = playingPlayers[2]; playingPlayers[2] = tmp;
  sittingPlayers[1] = sittingPlayers[2]; sittingPlayers[2] = tmp;
  ```
  and notifies the engine (`((PenteState) gridState).dPenteSwapDecisionMade(...)`,
  ≈1170). This is the exact pattern a Renju `swap=true` decision mirrors.
- **Join push.** `handleJoin(player)` (≈410) pushes table state to a joining
  client (player rejoin **or** spectator): `sendTableState` (change-state) →
  `sendPlayingPlayers` (≈1249, sends each seat via `DSGSetPlayingPlayerTableEvent`)
  → conditional swap/swap2 state (≈519-540) → `sendMoves` (≈542, all moves in one
  `DSGMoveTableEvent`) → `sendTimers`. Seats sent here already reflect any swaps,
  because swaps mutate the arrays.
- **`RenjuState` hooks** (`org.pente.game.RenjuState`): `isAwaitingSwapDecision()`,
  `renjuSwapDecisionMade(boolean)`, `isAwaitingBranchChoice()`,
  `chooseBranch(boolean tenOffer)`, `isAwaitingFifthOffers()`,
  `offerFifthMove(int)` (validates empty + non-duplicate + non-symmetric),
  `isAwaitingFifthSelection()`, `selectFifthMove(int)` (validates membership, then
  `addMove`), `getOfferedFifthMoves()` (`List<Integer>`, defensive copy),
  `isOpeningComplete()`, `getCurrentPlayer()` (1/2 by parity),
  `getRenjuSwapsPacked()`. Move 1 (center) is server-auto-placed via
  `GridStateFactory.getCenterMove(game)` as today.
- **TB reference protocol** (already built): `MoveServlet.renjuAction` =
  `swap` / `branch` / `offer` / `select` / `move4`. The live events mirror this,
  minus the HTTP bundling.

## Decisions (locked during brainstorming)

- **Three inbound events**, swap kept explicit so branch is never inferred from a
  swap payload:
  - `DSGRenjuTaraguchiSwapTableEvent(String player, int table, boolean swap, int move)`
    — a swap window (after moves 1–4, and the move-5 window). `swap=true` → take
    the other side, no move (`move` ignored/sentinel). `swap=false` → decline and
    place the next opening stone (`move`): moves 2–4 in their box, or move 5 in the
    9×9 = **Branch A**.
  - `DSGRenjuTaraguchiOffer10TableEvent(String player, int table, int[] moves)`
    — **Branch B**: the ten 5th-move candidates. Implies "declined the move-4
    swap + chose Branch B".
  - `DSGRenjuTaraguchi10Select1TableEvent(String player, int table, int move)`
    — the other player picks one of the ten as move 5.
- **Naming:** all three carry the `…TableEvent` suffix for convention consistency
  with every existing event class.
- **Decision-only echoes; stones ride `DSGMoveTableEvent`.** Each opening decision
  event (swap / offer10 / select1) is re-broadcast as a **decision signal** the other
  client must NOT place a stone from. Stone placement always travels as a normal
  `DSGMoveTableEvent`: a declined-swap-with-move delegates the placement to
  `handleMove` (reusing its proven timer / game-over / activity-logging tail rather
  than reimplementing it); the Branch-B move-5 stone (placed inside `selectFifthMove`)
  is broadcast via a small `broadcastRenjuFifthMove` helper that reproduces
  handleMove's post-move tail — BOTH the player-changed and same-player branches,
  since in Branch B the selector is white and also plays move 6, so the current
  player does NOT change at selection (a player-changed-only tail would drop white's
  increment and desync `moveTimes`). Move 6+ is the normal `DSGMoveTableEvent` path.
  Clients therefore treat the opening as decision-echo + the `DSGMoveTableEvent` they
  already handle — settled here for sub-project 3.
- **Seats encode swaps.** On `swap=true` the handler swaps both seat arrays, so the
  seat assignments already-sent on join fully describe the swap history — no packed
  word is sent live. (`getRenjuSwapsPacked()`/offers stay in `RenjuState` for the
  game-over historic serialization from sub-project 1, unchanged.)
- **Minimal join snapshot.** Seats + moves only, **except** when interrupted in
  Branch B after the ten were offered but before selection — then also re-send the
  ten. Uncommitted swap/branch decisions are not persisted; the player re-decides
  on re-entry.
- **No new opening rules.** Phase legality, forbidden-point blocking, and symmetric
  -offer rejection all reuse `RenjuState`'s existing checks.
- **Transport-agnostic by construction.** All work lands in the shared event/
  `ServerTable` layer; the hybrid TCP-socket / WebSocket front-ends both ride the
  one `DSGEventWrapper` codec and the one `dsgEventRouter` fan-out, so nothing in
  this design (or in a future game's opening) should reference a specific transport.

## Components

### 1. Three event classes (`org/pente/gameServer/event/`)
Each `extends AbstractDSGTableEvent`, templated on `DSGMoveTableEvent` /
`DSGSwap2PassTableEvent`: `player`, `table`, the payload field(s) above,
constructor(s), getters. Plain data carriers — no logic.

### 2. `DSGEventWrapper` registration
For each of the three: add a `private` field of that type + matching
getter/setter, so Gson (de)serializes it and `getEncodedEvent()` can return it.
This is the only place the reflection dispatch learns the type, and it is **shared
by both the TCP and WebSocket front-ends** — registering here makes each event work
over both transports; there is no second, TCP-specific registry to touch.

### 3. `SynchronizedServerTable.callServerTable()` dispatch
Add three `case` arms routing each event to its `ServerTable` handler, alongside
the existing `DSGMoveTableEvent` / `DSGSwap2PassTableEvent` arms.

### 4. `ServerTable` handlers
- `handleRenjuSwap(DSGRenjuTaraguchiSwapTableEvent)` — verify it's a Renju game,
  the actor's seat is the current player, and `gridState` `isAwaitingSwapDecision()`
  (or the move-5 window). If `swap`: swap both seat arrays (mirror `handleSwap`) +
  `renjuSwapDecisionMade(true)`, then echo the decision (no stone). Else:
  `renjuSwapDecisionMade(false)`, echo the decision, then **delegate the placement
  to `handleMove(player, move)`** — `handleMove` enforces box/forbidden via
  `isValidMove`, ticks timers, broadcasts the `DSGMoveTableEvent`, and checks game
  over. (Do not place the stone directly — that would bypass / duplicate that path.)
- `handleRenjuOffer10(DSGRenjuTaraguchiOffer10TableEvent)` — verify Renju, actor
  seat, and that the engine is at the post-move-4 decision. Resolve the move-4 swap
  window as declined (`renjuSwapDecisionMade(false)` if still awaiting) +
  `chooseBranch(true)`, then commit the ten via a new **atomic**
  `RenjuState.offerFifthMoves(int[])` (validate-all / commit-none: snapshot the
  internal list, `offerFifthMove` each, restore on any rejection — engine rejects
  empty/duplicate/symmetric/occupied). Echo the offer event; hand the turn to the
  selector (timer choreography mirrors `handleSwap2Pass`).
- `handleRenjuSelect1(DSGRenjuTaraguchi10Select1TableEvent)` — verify Renju, actor
  seat, `isAwaitingFifthSelection()`, and membership; `selectFifthMove(move)`
  (engine places the stone itself). Because the stone is placed inside the hook (not
  via `handleMove`), broadcast it through `broadcastRenjuFifthMove` (the helper that
  reproduces handleMove's post-move tail, both branches) and echo the select event.

**Echo recipient.** The three decision echoes use `broadcastMainRoom` to mirror the
shipped `handleSwap`/`handleSwap2Pass` precedent (seated players are also in the
main-room list, so the opponent receives them); the bundled stone goes via
`broadcastTable`. Task 7's manual round-trip verifies the opponent/spectators
actually receive the echoes and offers — switch the echoes to `broadcastTable` for
exact recipient parity if not.

All three: on any validation failure, no state is mutated and a
`DSGMoveTableErrorEvent` is emitted to the sending player — see Error handling.

### 5. Join push — Branch-B offer re-send
In `handleJoin`, immediately after `sendMoves(player)` (≈542), add a guarded step:
if the game is Renju and `isAwaitingFifthSelection()` (ten offered, not yet
selected), send the joiner a `DSGRenjuTaraguchiOffer10TableEvent` built from
`getOfferedFifthMoves()`. A small private helper
(`sendRenjuBranchBOffers(player)`) keeps `handleJoin` readable. Scope available:
`player`, `gridState`, `tableNum`, `dsgEventRouter`.

## Data flow

```
client JSON ─(TCP socket OR WebSocket)→ Socket/WebSocketDSGEventHandler
            → DSGEventWrapper.getEncodedEvent() (one of the 3 new events)   [shared codec]
            → SynchronizedServerTable.callServerTable() switch
            → ServerTable.handleRenju{Swap|Offer10|Select1}
                 ├─ validate (Renju? actor seat == current player? phase ok? legal?)
                 ├─ drive RenjuState hooks (renjuSwapDecisionMade/chooseBranch/
                 │                          offerFifthMoves/selectFifthMove)
                 ├─ if swap=true: swap playingPlayers[]/sittingPlayers[] (1↔2)
                 ├─ echo the DECISION event (no stone placed from it)
                 └─ place stones as DSGMoveTableEvent:
                      swap=false move → handleMove(player, move)
                      select1        → broadcastRenjuFifthMove (handleMove tail)
            (move 6+ → normal DSGMoveTableEvent path)

join/rejoin → handleJoin → … → sendPlayingPlayers (seats, swap-aware)
                              → sendMoves
                              → if Renju && isAwaitingFifthSelection:
                                   send DSGRenjuTaraguchiOffer10TableEvent (the ten)
```

## Error handling / compatibility

- **Errors are reported to the sender, mirroring `handleMove`.** Each handler
  follows the established pattern (`ServerTable.handleMove` ≈1551 accumulates, ≈1703
  emits): start `int error = DSGTableErrorEvent.NO_ERROR`, set it on any failure,
  mutate no state, and at the end — when `error != NO_ERROR` — emit
  `dsgEventRouter.routeEvent(new DSGMoveTableErrorEvent(player, tableNum, move, error), player)`
  to the **sending player only**. Reuse the existing codes on `DSGTableErrorEvent`:
  - `NOT_TURN` (12) — the actor's seat is not the current player.
  - `INVALID_MOVE` (13) — the action doesn't fit the current `RenjuState` phase
    (e.g. an offer when not awaiting offers), an illegal/forbidden placement, an
    out-of-box opening stone, a duplicate/symmetric offer, or a selection not among
    the ten.

  Reusing `DSGMoveTableErrorEvent` (carries `move` + error code) means clients
  handle Renju opening errors via the exact same path as normal-move errors. The
  `move` field carried in the error event: the swap handler uses the event's move;
  `select1` uses the rejected selection; **`offer10` reports `-1`** — the ten are
  committed atomically (validate-all / commit-none), so no single candidate is "the"
  offender. A dedicated forbidden-point error code could be added later if a client
  wants to distinguish it — out of scope here.
- **Forbidden points / symmetric offers:** detected by `RenjuState`
  (`isValidMove`, offer dedup). On detection the handler sets `INVALID_MOVE` and
  emits as above **without partially mutating state** — in particular, validate all
  ten offers up front and commit none if any is rejected.
- **Non-Renju games:** the new handlers are only reached for the new event types,
  which a non-Renju client never sends; existing `handleMove`/`handleSwap2Pass`
  paths are untouched.
- **Reconnect before a decision:** nothing persisted; the player re-decides. Only
  committed offers (the ten) are re-sent, and only while selection is pending.

## Testing

- **Opening rules:** already covered by the `RenjuState` unit suites — not
  re-tested here.
- **New routing + seat remap:** add `ServerTable`-level tests if a usable harness
  exists in the repo (check `org/pente/gameServer/.../test`); assert that a
  `swap=true` event swaps the seat arrays and that move validation tracks the new
  mapping, and that out-of-phase events are rejected without mutation. If no
  harness exists, **compile-verify** and document a **manual WebSocket round-trip**:
  swap window (swap + decline+move), Branch A (decline+move5), Branch B
  (offer10 → select1), and a rejoin mid-offer that receives the ten. No fabricated
  tests.

## Out of scope (later sub-projects / passes)

- React `react_live_game_room` opening UI (sub-project 3; guide-driven).
- Mobile/iOS/Android live opening UI.
- Rendering the offer phase during historic replay (preserve-only, from
  sub-project 1).
- Forbidden-point marking in clients (engine-only today, deferred).
