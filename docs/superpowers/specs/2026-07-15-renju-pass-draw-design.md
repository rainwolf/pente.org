# Renju: Pass, Timeout-Draw, and Draw Offers — Design

Date: 2026-07-15
Status: Approved design (pending implementation plan)
Scope: renju games only. Server (`pente.org/`), react live game room (`react_live_game_room/`), JSP turn-based web UI, Android (`pentelive-android/`, live + turn-based), iOS (`penteLive-iOS/`, live + turn-based).

## 1. Overview

Three features for renju:

1. **Pass** — a player may pass instead of placing a stone, but only after the Taraguchi-10 opening sequence is complete. Two consecutive passes end the game in a draw.
2. **Timeout-draw** — when a player times out, the game is a draw (not a loss) if the opponent cannot theoretically win by any series of legal moves (chess-equivalent rule).
3. **Draw offers** — a player may offer a draw; the offer always rides on a move (stone or pass). The opponent accepts (game drawn) or declines (explicitly in live play, or implicitly by moving).

## 2. Sentinels and encodings

Moves are encoded `x + y*width` (`SimpleGridState.convertMove`, `SimpleGridState.java:300`). Renju boards are 15×15, so real moves are `0..224`.

| Value | Meaning | Notes |
|---|---|---|
| `225` (`gridSizeX*gridSizeY`) | **Renju pass** (new) | Same convention as Go (`GoState.java:116`). Stored in the move list like any move. |
| `-1` in `tb_move` | Undo request (existing) | Stripped on load (`MySQLTBGameStorer.java:516`) → `undoRequested=true`. |
| `-2` in `tb_move` | **Pending draw offer** (new) | Stored at the **fixed slot `(gid, move_num = -2, move = -2)`**. Stripped on load → `drawOffered=true`. Never enters the in-memory move list. |

No collisions: renju opening offers pack into `0..224` and `renjuSwaps`/`renjuOffers` separately (`RenjuOpeningState`); `GridPieceAction.REMOVE=2` is an action, not a move; `ServerAITableController.NO_MOVE=-1` is in-memory only. Go's `passMove`/`handicapPass` apply to Go boards only. The `move` column is a signed smallint (`schema.sql:944`), so `-1`/`-2`/`225` all fit.

**Why the offer needs a fixed `move_num` slot:** `tb_move`'s primary key is `(gid, move_num)` (`schema.sql:945`) and inserts use `ON DUPLICATE KEY UPDATE move=VALUES(move)` (`MySQLTBGameStorer.java:889`). Undo's `-1` row is written at the next free slot (`move_num = getNumMoves()`, `CacheTBStorer.java:259`); if the offer used the same convention, whichever sentinel was written second would silently overwrite the first. Additionally, `undoLastMove` deletes `MAX(move_num)` (`MySQLTBGameStorer.java:362`) and would consume a top-slot offer row instead of the intended move. The fixed `move_num = -2` slot sits below every real move and every undo row, so it can never be overwritten by, nor be mistaken for, either. `loadMoves` orders by `move_num asc`, so the offer row (if present) is simply the first row read.

## 3. Feature 1 — Pass

### Rules (server-authoritative, `RenjuState`)

- Pass (`move == 225`) is legal iff `getOpeningPhase() == COMPLETE` (`RenjuState.java:628`) and the game is not over. During SWAP/BRANCH/SELECTION and the first 5 opening moves, pass is rejected.
- A pass appends to the move list like a normal move: alternation, move numbering, and persistence are unchanged. It places no stone and creates no patterns (no forbidden-point interaction).
- **Two consecutive passes end the game immediately as a draw** — result `GameData.DRAW` (= 3). No Go-style scoring phase. Detection mirrors `GoState.doublePass()` (`GoState.java:530`).
- `TBGame` gains renju `passMove = 225` alongside the existing Go values (361/81/169, `TBGame.java:196-200`), and double-pass detection for renju.

### Validation failures

- Live: pass during opening / non-renju game → `DSGMoveTableErrorEvent` with `INVALID_MOVE` (existing code 13, `DSGTableErrorEvent.java:17`).
- Turn-based: `MoveServlet` rejects with the standard invalid-move handling.

### Client rendering

A move `== gridSize*gridSize` renders as "PASS" in move lists/history, reusing the Go rendering (react `GameClass.js:217`, JSP `mobileGame.jsp:479`, mobile equivalents).

## 4. Feature 2 — Timeout-draw

### Rule (chess-equivalent, exact)

When a renju player times out (live or turn-based), the game ends in a **draw** instead of a timeout loss iff the non-timed-out opponent **cannot win by any series of legal moves** — both players cooperating, exactly as in chess (FIDE Art. 6.9 analog). Otherwise the timed-out player loses as today.

Applies to renju only. Pente/gomoku variants with captures are out of scope.

### Exact three-stage decision procedure

The check answers: "can the opponent reach a winning line?" Stages are ordered as accelerating filters — a YES at any stage is a YES of the full cooperative model; DRAW is declared only after stage 3 exhausts.

**Definitions.** A *candidate window* is 5 consecutive cells (row/column/diagonal; ≤ ~2,200 windows on 15×15) usable for the opponent's five.

**Case A — opponent is white (p2).** White has no forbidden points, and overline also wins for white. Cooperative black stones can only obstruct (they can never be removed and cannot legalize anything for white, since white placements are always legal). Therefore, exactly:

> Win possible iff some 5-window contains no black stone.

Simple scan; stages 2–3 never apply.

**Case B — opponent is black (p1).** Candidate windows: all 5 cells empty-or-black, and both flanking cells not already black (a pre-existing black flank forces overline, which never wins for black; stones are never removed, so such windows are permanently dead).

- **Stage 1 — window-only fill orders.** For each candidate window, try every order (≤ 5! = 120) of filling its empty cells with black stones, all other cells untouched. Each intermediate placement must be non-forbidden per `RenjuForbiddenPointFinder` against the evolving board. The final placement wins if it completes an **exactly-five** (five-priority: a simultaneous double-three/double-four does not void the win; a simultaneous overline **on the same line** does — that is just an overline). Any success → not a draw. Any position with open space exits here.

  **Win-oracle requirement (critical):** the win test MUST check exactly-five completion first, *independently* of the forbidden check. The existing helpers do not implement five-priority: `RenjuForbiddenPointFinder.isForbidden` (L448-450) is `isOverline || isDoubleFour || isDoubleThree` with no five exemption, and `RenjuState.getWinner` checks `forbidden` before `five` (`RenjuState.java:231-232`). Reusing them naively would misclassify a five-priority win (exactly-five on one line + incidental double-three/four on others) as "not a win" and wrongly declare draws. **Companion fix (recommended, flagged for owner):** `RenjuState.getWinner`'s ordering is a latent five-priority bug affecting live rule adjudication too; the timeout oracle and game play should agree, so fixing the ordering there is recommended alongside this work.

- **Stage 2 — black helper stones.** Helpers outside a window can legalize fills stage 1 cannot (example: a helper extends one three of a double-three into a four, so the once-forbidden cell now makes four+three — legal). Exhaustive memoized DFS: state = set of added black stones; successor = any legal (non-forbidden) black placement on an empty cell; win test = a placement completing an exactly-five under five-priority. Search arena restricted to the **relevant region**: fixpoint expansion from candidate-window cells by line-distance ≤ 6 per hop — the per-hop radius must cover the longest single legality dependency, and open/broken three and gapped four detection in `RenjuForbiddenPointFinder` inspects spans of up to ~6 cells, so 4 (bare pattern radius) would under-cover. Stones outside the fixpoint provably cannot influence any legality chain into a window.

- **Stage 3 — cooperative white helper stones.** White helpers can do exactly one thing for black: occupy the extension square of a black three/four, making it "fake" and dissolving a double-three/double-four forbiddenness. (They cannot fix overline-forbiddenness — that depends only on black stones — and cannot join black's five.) Extend the DFS state to (added black, added white); white placements are always legal (no cascade); prune white candidates to cells within pattern radius of a potential black placement in the relevant region. Memoized; worst case 3^(empty∩region), reached only on congested boards where the region is small.

**Realizability of interleavings.** Any placement sequence found by stages 2–3 is realizable under alternation: with passes available, a pass by one side is always answerable by a stone (or the next needed placement) from the other, so two consecutive passes are never forced before the win.

**Termination and exactness.** Stones are only added (monotone), state space is finite, memoized DFS terminates. The procedure is exact for the cooperative model: no approximation.

### Integration points

- **Turn-based**: `CacheTBStorer.TimeoutCheckRunnable` (class at `CacheTBStorer.java:519`) — for renju, run the check before `fresh.setWinner(3 - seat)` (insertion point L672, preserving the premature-timeout guard at L680); on draw, end with `STATE_COMPLETED_TO` ('T') + `setDraw(true)` + `winner=0`, new `REASON_DRAW=4` (`CacheTBStorer` reason codes at L781-783). `EndGameRunnable` already branches on `isDraw()` for ratings and messages (L1003-1020, L1035-1039); verify its reason-based notification dispatch (L1111-1128 branches on RESIGN/TO/WIN) needs no extra `REASON_DRAW` branch beyond the `isDraw()` subject/text at L1163-1166.
- **Live**: the `DSGTimeUpTableEvent` handling path (`ArenaServerTable.java:340`, and `ServerTable`/`SynchronizedServerTable` consumers) — same check before recording a timeout win; on draw, record `DSGPlayerGameData.DRAW` (= 3) for both players.

## 5. Feature 3 — Draw offers

### Shared semantics

- An offer is a **flag riding on a move** (stone or pass — pass may carry an offer), legal only when the opening is complete and it is the offerer's turn.
- At most one offer can be pending: the offer resolves before the offerer moves again (opponent accepts, rejects, or moves).
- Accept (opponent only, while pending) → game ends `DRAW` immediately.
- Decline: **live** — explicit `DSGRenjuRejectDrawTableEvent`, or implicit by playing a move (covers a dismissed dialog); **turn-based** — implicit only: opponent's move clears the offer (no explicit decline command, mirroring how a move declines nothing else — this is simpler than undo's explicit decline and was chosen deliberately).
- The pending offer is also cleared by: any undo being performed, resignation, cancellation, or any game end.
- Offers are available to all players (not subscriber-gated like undo — this is a rules feature, not a convenience).
- Offerer identity needs no storage: it is always the player who made the most recent move.

### Live protocol

- `DSGMoveTableEvent` gains optional boolean `drawOffer` (default false). Gson tolerates absence, so old clients remain wire-compatible both ways; the server rejects the flag for non-renju games or during the opening (`DSGMoveTableErrorEvent`, `INVALID_MOVE`).
- Two new events, standard registration recipe (class extends `AbstractDSGTableEvent`; field + getter/setter in `DSGEventWrapper`; POJO fields only, no custom Gson adapter):
  - `DSGRenjuAcceptDrawTableEvent`
  - `DSGRenjuRejectDrawTableEvent`
- React descriptors (`protocol/messages.js`): add `drawOffer` to the move descriptor's fields; add `renjuAcceptDraw`/`renjuRejectDraw`, `dir:'both'`, `out:['player','table']`.

### Live server (`ServerTable`/`SynchronizedServerTable`)

- On move with `drawOffer`: validate (renju, opening complete, mover's turn), set `pendingDrawOffer = offering seat`, broadcast the move event with the flag intact.
- On `DSGRenjuAcceptDrawTableEvent`: valid only from the non-offering seat while pending → end game as draw (both players `DSGPlayerGameData.DRAW`), broadcast game end.
- On `DSGRenjuRejectDrawTableEvent`: valid only from the non-offering seat while pending → clear offer, broadcast so the offerer's UI can un-arm/notify.
- On any move by the non-offering player: clear pending offer (implicit decline).
- Invalid accept/reject (no pending offer, wrong seat) → new error code `NO_DRAW_OFFERED = 25` on `DSGTableErrorEvent` (codes 17-24 are already taken, `DSGTableErrorEvent.java:21-30`; 25 is the next free value).
- **Rejoin/reconnect**: the pending offer (offering seat) rides on the live table-state sync — concretely, a new optional `pendingDrawOffer` field on `DSGGameStateTableEvent` (already in `DSGEventWrapper.java:33`; plain POJO field, old clients ignore it) — broadcast/sent on rejoin so the opponent's accept/reject dialog and the offerer's armed state are restored. (`RenjuRejoin` only encodes the opening phase and is not the carrier.)
- **AI tables** (`ServerAITableController`): pass and draw offers are disabled at AI tables in v1 — server rejects them (`INVALID_MOVE` / auto-reject) and clients hide the buttons. Rationale: the native AI engines do not understand a pass input, and the AI has no draw-evaluation logic.
- Spectators never see the buttons; events from non-seated players are rejected as today.

### Turn-based server

- `MoveServlet` `command=move` gains optional param `drawOffer=true` (validated: renju, **phase COMPLETE only** — `RenjuOpeningPhase.MOVE` is an *opening* sub-phase (Branch-A placements, see `RenjuRejoin.java:29`) and must NOT enable pass or offers anywhere). New `command=acceptDraw` mirroring `acceptUndo` (`MoveServlet.java:281`): valid only while an offer is pending and it is the acceptor's turn → end game `STATE_COMPLETED` + `setDraw(true)` + `winner=0`, `REASON_DRAW`.
- Double-pass in `CacheTBStorer.storeNewMove` (game-over handling at L1657-1669): renju double-pass → `STATE_COMPLETED` + draw, `REASON_DRAW`.
- Persistence: on storing a move with an offer, also write the offer row at the **fixed slot `(gid, move_num=-2, move=-2)`** (see §2 for why a next-free-slot row would collide with undo's `-1` under the `(gid, move_num)` primary key and be eaten by `undoLastMove`'s `MAX(move_num)` delete). `loadMoves` maps `move==-2 → drawOffered=true` (alongside `-1 → undoRequested`). Cleared storer-side (explicit `DELETE` of the `-2` slot, flag false) when the opponent moves or accepts. Any undo also clears the offer: delete the `-2` row explicitly — safe in any order since the fixed slot is never `MAX(move_num)`. `-1` and `-2` coexist safely in distinct slots. The plan must audit all other `tb_move` readers (archival, replica, stats) — they already skip `-1` and must equally skip negative-`move_num`/`-2` rows.
- `TBGame`: new transient `drawOffered` boolean (getter/setter), like `undoRequested` (`TBGame.java:50`).
- Mobile JSON (`GameResponse.java`): new `drawOffered` Boolean field, emitted like `undoRequested`.
- Notifications: move push/email notifications for a move carrying an offer say so ("… and offers a draw"); draw endings reuse the existing draw message text in `EndGameRunnable` (L1053-1071).

## 6. Client UI

### Shared turn-based button behavior (Android, iOS, JSP)

"Opening complete" means, uniformly across every gate in this document, the state equivalent to server `RenjuState.openingComplete`: `getOpeningPhase()/getRenjuPhase() == COMPLETE` (server/TB/JSP), `RenjuLiveState` complete flag (Android live), `state.renju.complete` (iOS live), renju tracking complete (react). The `MOVE` phase value is an opening sub-phase and never enables pass or draw offers.

When the game is renju, opening complete, it is my turn, and no stone is staged:

- **PASS** enabled, **SUBMIT** disabled, **DRAW?** enabled.
- Staging a stone on the board: PASS hides (a staged stone excludes passing), SUBMIT enables. **DRAW? stays visible whether or not a stone is staged**, so the offer can be armed or disarmed at any point before submitting. (Deviation from the original 3-button sketch, where staging hid both buttons — hiding an armed DRAW? would make it impossible to disarm.)
- Pressing **DRAW?**: arms the offer — long-lived bottom notification "Draw offer will be sent after you move"; DRAW? indicates armed (green background). **PASS stays visible while armed** (an offer may ride on a pass). Pressing DRAW? again disarms.
- Submitting (stone or pass) while armed sends the move with `drawOffer=true` and disarms.
- Incoming offer (opponent moved with offer, seen via `drawOffered` in polled state): presented like an undo request — accept, or just move to decline.

### React live game room

- `GameInfoPanel`: PASS and DRAW? buttons shown when renju + opening complete (`renjuPhaseNow()`), my turn, `GameState.State.STARTED`. PASS sends move `225` like the Go pass (`GameInfoPanel.js:72-79` pattern). DRAW? arms (snackbar + green state); next `Commands.move` carries `drawOffer:true`.
- Incoming offer: `dsgMoveTableEvent` with `drawOffer` sets `draw_requested` → new `DrawOfferModal` (clone of `UndoModal.js`): Accept → `Commands.renjuAcceptDraw`, Reject → `Commands.renjuRejectDraw`. Closing the modal without answering leaves the offer pending; playing a move implicitly declines (server clears; client clears `draw_requested` on own move).
- Offerer feedback: on reject event or opponent move, show a brief "draw declined" notice and un-arm.
- Reducer wiring mirrors undo/cancel (`rootReducer.js` handler map + `redux_reducers/utils.js`).

### Android

- **Live** (`LiveTableFragment`): two dedicated visible buttons PASS and DRAW? during renju play after opening (chosen over menu entries for discoverability; the retitled `playButton` trick stays Go-only). Arm flow as above (Toast for the armed notice). Incoming offer: bottom-gravity `AlertDialog` (same pattern as `undoRequested`, `LiveTableFragment.java:1026-1050`) → accept/reject events; dismissing and playing a move declines.
- **Turn-based** (`BoardActivity`/`Game`): 3-button spec above; `drawOffer` param appended by `Game.submitMove` URL builder; incoming offer from `GameResponse.drawOffered` → dialog with Accept (`command=acceptDraw`) / dismiss-and-move.

### iOS

- **Live** (`TableViewController`): PASS and DRAW? buttons alongside `playButton`; TSMessage (bottom) for the armed notice; incoming offer via `UIAlertController` (same as `requestUndo(player:)`, `TableViewController.swift:1065`) → accept/reject events.
- **Turn-based** (`BoardViewController`): buttons live in the existing bottom row (`BoardViewController.m:176-238`); armed notice via `TSMessage … atPosition:Bottom`; incoming offer via action sheet mirroring `presentUndoOptions` (`BoardViewController.m:2994`) with Accept → `command=acceptDraw`.

### JSP turn-based web (`mobileGame.jsp`)

- PASS button copied from the Go pass pattern (`submitPass()`, L329-331 / L1428-1431) posting `moves=225`, gated on renju + `game.getRenjuPhase() == "COMPLETE"` **only** (never `MOVE`, which is an opening sub-phase) + `myTurn`.
- DRAW? button arms a JS flag; `submit()`/`submitPass()` append `&drawOffer=true`.
- "Draw offered — Accept" indicator/link mirroring the `isUndoRequested()` else-if (L338-340); accept posts `command=acceptDraw`.

## 7. Edge cases

- Pass rejected: during opening, when game over, at AI tables, in non-renju games.
- `drawOffer` rejected: same conditions; also when a draw offer is somehow already pending from the same player (cannot normally happen — offer resolves before offerer moves again).
- Undo interactions: performing an undo clears any pending draw offer (explicit delete of the fixed `-2` slot; order-independent, see §5). An undo request and a draw offer may be simultaneously pending in TB (distinct fixed slots); each keeps its own lifecycle.
- Offer vs simultaneous timeout: events are processed serially in arrival order. If the timeout lands first, the game ends via the timeout path (including the timeout-draw check) and the pending offer is discarded like any game end; a late accept/reject gets `NO_DRAW_OFFERED` (live) or is ignored (TB). If an accept lands first, the game is drawn and the timeout is moot.
- Double-pass draw takes effect immediately even if the second pass carried a draw offer (game already drawn; offer moot).
- Move history: pass shown as "PASS"; the offer flag is not part of history (not replayed/exported). PGN: a drawn result uses the existing `GameData.DRAW` mapping (`PGNGameFormat.java:826`), but move serialization must handle `225` — `PGNGameFormat` converts move ints to board coordinates and `225` is out of range on 15×15. Serialize a pass as a literal `pass` token and accept it on parse, keeping round-trip intact.
- Cosmetic: `DSGMoveTableEvent.toString()` formats moves with hardcoded 19×19 coordinates (`DSGMoveTableEvent.java:7`); a `225` pass logs a bogus coordinate. Optionally print "PASS"; log-only, no functional impact.
- Old clients (live): unknown `drawOffer` field is ignored by Gson on old clients; unknown new events are dropped by their wrappers — an old-client opponent would never see the offer, and it gets cleared when they move. Acceptable degradation during rollout.
- Ratings/stats: draws flow through existing draw handling (`EndGameRunnable` draw branch; `DSGPlayerGameData.DRAW`).

## 8. Testing strategy

- **Rules core (JUnit)**: pass legality across all opening phases; double-pass draw; draw-offer validation; sentinel non-collision.
- **Timeout-draw (JUnit, most important)**: Case A scan correctness; stage 1 window orders incl. five-priority and same-line-overline exclusion; **win-oracle five-priority case: exactly-five completed simultaneously with a cross-line double-three/double-four must count as a win** (guards against reusing `isForbidden`/`getWinner` naively); stage 2 positions requiring a black helper (double-three dissolved by extending one three to a four); stage 3 positions requiring a white helper; congested full-board draws; region-fixpoint soundness incl. **a position whose sole helper sits at line-distance 5 from the window** (validates the ≤6 hop radius); performance guard on pathological mid-density boards.
- **TB persistence**: `-2` fixed-slot store/load/clear; coexistence with `-1` (both pending, then each resolved in both orders); `undoLastMove` with a pending offer never deletes the offer row implicitly (explicit clear only, and the right move row is removed); offer cleared by opponent move and by acceptDraw; timeout-draw end state ('T' + draw); `REASON_DRAW` notification dispatch produces the draw message.
- **Live integration**: offer→accept, offer→reject, offer→implicit-decline-by-move, rejoin with pending offer, old-client compatibility (event with `drawOffer` against wrapper without the field).
- **Clients**: manual test matrix per client (arm/disarm, pass+offer, incoming dialog accept/decline/dismiss, opening-phase gating, AI-table hiding).

## 9. Out of scope

- Pass/draw for pente, gomoku variants with captures, Go (unchanged), d-pente, swap2 games.
- AI understanding of passes or draw evaluation (v1 disables both at AI tables).
- Explicit TB decline-draw command (implicit decline by moving is the rule).
- Tournament-specific draw handling beyond existing `RESULT_TIE` plumbing (verify in plan; `TourneyMatch.RESULT_TIE=4` exists).
