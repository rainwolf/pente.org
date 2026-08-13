# Swap-choice move sound — design

**Date:** 2026-08-13
**Scope:** `react_live_game_room`, `penteLive-iOS`, `penteLive-Android` (live game room only)
**Status:** approved design, ready for planning

## Problem

When a player makes an opening **swap choice** — D-Pente/D-Keryo colour choice, Swap2 colour
choice, or a Renju Taraguchi take-over or ten-stone offer — play moves on, but **no stone is
placed**. All three live clients play their "new move" sound only on `dsgMoveTableEvent`, so a
swap choice is completely silent. The player left waiting gets no audible cue that anything
happened.

Not every swap-shaped event moves the turn, and the difference decides which ones sound — see the
decision table and the seat-swap note below.

## Goal

On every swap event that genuinely hands the turn over and is **not** followed by a
`dsgMoveTableEvent`, play the client's existing move sound.

## Non-goals

- No new audio assets. Each client reuses the asset already bound to its move sound.
- No new mute/preference UI, and no change to existing mute semantics.
- No change to the wire protocol or the server. This is a client-only change.
- The turn-based / correspondence swap surfaces are untouched (see "Out of scope").

---

## Wire background

The server publishes swap outcomes with `broadcastMainRoom(...)`
(`pente.org/dsg_src/java/org/pente/gameServer/server/ServerTable.java:359-369`), which reaches
**every player in the lobby including the actor** — there is no sender exclusion in
`SimpleDSGEventToPlayerRouter.routeEvent` (`SimpleDSGEventToPlayerRouter.java:47-57`). A move, by
contrast, uses the narrower `broadcastTable(...)` (`ServerTable.java:347-357`). Client-side
filtering on the `table` field is therefore mandatory — every client already does this for its
move sound, and the new call sites sit inside those same guards.

The server never sends an explicit "whose turn is it" field on the live-room websocket; clients
derive the turn locally. `dsgSetPlayingPlayerTableEvent` maps a name to a seat and is **not** a
turn signal.

`DSGEventWrapper.getJSON()` (`DSGEventWrapper.java:122-130`) does not call `serializeNulls`, so
**Gson omits null fields entirely**. A field that the server left null is *absent* from the frame,
not present-and-null. This matters for the Offer10 rejoin guard below: the guard must treat
*absent* and *null* alike (`data.player != null` in JS/Java, `event["player"] as? String != nil`
in Swift) rather than testing for a null value that never arrives on the wire.

---

## Decision table — which events sound

| Wire event | Sound | Reason |
|---|---|---|
| `dsgSwapSeatsTableEvent`, `silent=false` | **yes** | D-Pente/D-Keryo colour choice, Swap2 colour choice, and the Renju Taraguchi take-over (the server synthesizes this frame for take-overs at `ServerTable.java:1389`). Turn passes, no stone placed. |
| `dsgRenjuTaraguchiOffer10TableEvent`, `player` present | **yes** | Branch-B ten-stone offer. Turn passes to the selecting player, no move event follows. |
| `dsgSwapSeatsTableEvent`, `silent=true` | no | Rejoin/state-sync replay marker (`ServerTable.java:549-552`, `:576-578`). |
| `dsgSwap2PassTableEvent` (any) | no | **Does not hand the turn over.** "Let p1 decide" means player 2 places two *more* stones and keeps the move. `SimplePenteState.getCurrentPlayer()` returns 2 both before the pass (`numMoves == 3 && dPenteWaitForSwap`) and after (`numMoves < 5 && dPenteWaitForSwap`); `isValidMove` (`:282-288`) *unblocks* player 2 at move 3; and `handleSwap2Pass` (`ServerTable.java:1289-1308`) stops and restarts the **same** timer index, where every genuine handoff swaps two. Player 1's decision opens only at `numMoves == 5`, and stone 5 arrives as an ordinary `dsgMoveTableEvent` that already sounds — there is no silent gap to fill. |
| `dsgRenjuTaraguchiOffer10TableEvent`, `player` absent | no | Rejoin replay (`ServerTable.java:649`). This frame carries **no `silent` flag** — an absent `player` key is the only replay marker available. |
| `dsgRenjuTaraguchiSwapTableEvent` (any variant) | no | `swap=true` take-over is not echoed as this frame — the server sends a synthesized `dsgSwapSeatsTableEvent` instead, which already sounds. `swap=false` with a bundled stone (windows 1–4, and the post-take-over branch decline) is immediately followed by `handleMove` → `dsgMoveTableEvent` (`ServerTable.java:1414-1415`), which already sounds. `swap=false` at window 5 (`ServerTable.java:1424`) does **not** hand the turn over — white keeps it and plays move 6 — so there is nothing to cue. |
| `dsgRenjuTaraguchi10Select1TableEvent` | no | Followed by `broadcastRenjuFifthMove` → `dsgMoveTableEvent` (`ServerTable.java:1659`), which already sounds. |
| Between-games seat rotation | no | `ServerTable.swapSeats()` (`:3442-3465`) emits Stand/Sit events, not a swap event. It is a set rotation between games, not an in-game turn handoff. |

### A seat swap is not always a handoff — and that is accepted

`dsgSwapSeatsTableEvent` cues on both branches, but whether the **decider** keeps the move
depends on the seat swap interacting with move parity. Once the decision clears
`dPenteWaitForSwap`, `getCurrentPlayer()` falls through to `1 + (numMoves % 2)`, while
`handleSwap` (`ServerTable.java:1217-1223`) exchanges `playingPlayers[1]`/`[2]` only when
`swap == true`:

| Decision point | seat to move after | `swap=true` | `swap=false` |
|---|---|---|---|
| D-Pente, `numMoves == 4` | 1 | decider keeps the move | handoff |
| Swap2 colour, `numMoves == 3` | 2 | handoff | decider keeps the move |
| Swap2 p1, `numMoves == 5` | 2 | decider keeps the move | handoff |

So roughly half of all colour choices cue while the decider plays on. This is accepted rather
than corrected: deriving the post-mutation turn in three clients is exactly the complexity the
audience decision below already declined, and the decider does move immediately, so the cue is
early by one action rather than wrong for long. **The cue means "a swap decision landed at your
table", not strictly "your turn".** Do not "fix" this into a turn derivation without revisiting
the audience decision with it.

This is also why `dsgSwap2PassTableEvent` is excluded and this is not: there, *nobody's* turn
changes and the player told to move must wait two full stones.

### Audience

**No audience filter.** Everyone viewing the table hears it, including the player who made the
choice. This matches what iOS and Android already do for move sounds (both play the mover's own
move on the server echo, because neither applies moves optimistically).

Consequence to be aware of: `react_live_game_room` filters its *move* sound to
`data.player !== state.me` (`redux_reducers/utils.js:214-216`), so after this change React's swap
sound and move sound will use different audience rules. This is deliberate and was chosen
explicitly.

### Sound identity

Each client plays the asset already bound to its move sound. None of the three has a distinct
"swap" tone, and adding one is out of scope.

| Client | Logical name | Asset |
|---|---|---|
| React | `'move'` | `src/resources/sounds/move_sound.mp3` |
| iOS | `newMoveSndID` | `penteLiveNotificationSound.caf` |
| Android | `NEW_MOVE_SOUND` | `res/raw/pentelivenotificationsound.mp3` |

---

## Per-client implementation

### React — `react_live_game_room`

Architecture: reducers are pure and request sounds by pushing an intent onto
`state.pendingNotifications` via `emit()` (`src/redux_reducers/utils.js:9-11`);
`notificationMiddleware` (`src/notifications/middleware.js:11-31`) drains the queue and calls
`AudioService.play(...)`. There are no sagas or thunks.

Add `emit(state, {sound: 'move'})` to two reducer helpers in
`src/redux_reducers/utils.js`, each **inside** the existing `data.table === state.table` guard so
only the table being viewed sounds:

1. `swapSeats` (`utils.js:409`) — guarded on the event not being silent.
2. `renjuOffer10` (`utils.js:591`) — guarded on `data.player != null`.

Registration for both already exists in the `EVENT_HANDLERS` registry (`rootReducer.js:116`,
`:125`); no new wiring is needed. `swap2Pass` (`utils.js:535`) is left untouched.

**Ordering trap:** `swapSeats` builds a fresh `game` instance and only assigns it to `state.game`
at the end of the block. Do not place the `emit` where it would read a stale `state.game`. Since
the audience is unfiltered, the emit needs no game state at all — but keep it inside the
`data.table === state.table` guard.

**Do not** seed the event set from `renjuOpeningUi.js`'s `ADVANCING_EVENTS` (`:22-33`) — it omits
`dsgSwap2PassTableEvent` and includes six non-swap events.

### iOS — `penteLive-iOS`

All sound state lives on `RoomViewController`: `newMoveSndID` (`RoomViewController.swift:52`,
loaded `:123-127`) and `playSounds` (`:55`). `TableViewController` has no back-reference to
`RoomViewController`, so all call sites must be in `RoomViewController`.

1. Add a `private func playTurnSound()` after the sound-loading block (`:113-127`) that wraps the
   `playSounds` check and the `AudioServicesPlaySystemSound(newMoveSndID)` call.
2. Refactor the existing move-sound block (`:956-958`) to call it, so mute semantics cannot drift
   between move and swap.
3. Call it from `swapSeatsTableEvent` (`:757-770`, after `stateChanged()`, inside the
   `tableId == self.tableViewController?.table.table` guard at `:763`, gated on `!silent` using
   the local at `:761`).
4. Call it from `renjuOffer10TableEvent` (`:792-799`, inside the guard), gated on the `player` key
   being present. Read it as `event["player"] as? String` — never force-unwrap; the rejoin replay
   omits the key.

### Android — `penteLive-Android`

File: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java`.
All live-room sound goes through `playSound(int)` (`:188`), which checks the cached mute field at
`:189`. All event handlers already run on the UI thread (`:500`).

Because the swap handlers here have no table-identity guard of their own, both calls go through a
new `playTurnSoundForTable(int)` helper that repeats the visible-fragment check used by
`addTableMessage` (`:554-562`) and `updateTableMove` (`:570`), including its `fragment.table != null`
guard:

1. `swapSeats(Map)` (`:663`) — call the helper inside the **existing** `if (!silent)` block at
   `:686`, alongside the `seats_swapped` / `seats_not_swapped` chat lines.
2. The `dsgRenjuTaraguchiOffer10TableEvent` branch (`:397-410`) — call the helper after
   `fragment.onRenjuDecisionEcho(tbl)` at `:408`, gated on the payload containing a non-null
   `player`. It sits outside the `if (t != null && t.isRenju())` block deliberately, so Android
   cannot fall silent where React and iOS — neither of which gates on renju-ness — would sound.

`swap2Pass` (`:695`) is left untouched.

**Naming hazard:** `silent` means two opposite things in this file. The **field** at `:73` is the
user's mute preference; the **locals** at `:666` and `:697` are the server's replay flag. Always
route playback through `playSound(...)` so the mute field is checked in exactly one place — never
hand-roll a `MediaPlayer` call at these sites.

---

## Risks and how the design handles them

| Risk | Handling |
|---|---|
| **Rejoin storm** — a reconnect replays swap state for every table | `silent=true` frames excluded on swapSeats; absent `player` excluded on Offer10 |
| **Cue with no turn change** | `dsgSwap2PassTableEvent` excluded entirely; renju window-5 decline excluded. The seat-swap branches where the decider keeps the move are a knowing exception — see the note above |
| **Double beep** — one user action producing two frames | Renju swap-with-stone and Select-1 are excluded precisely because their follow-up `dsgMoveTableEvent` already sounds; the renju take-over is covered once, by the synthesized `dsgSwapSeatsTableEvent`, and the renju swap frame itself is excluded |
| **Sound with no turn change** — renju window-5 decline | Excluded; the decliner keeps the turn |
| **Wrong table** — swaps are broadcast lobby-wide, so an unguarded cue fires for every table in the room | React and iOS call sites sit inside the handler's existing table-identity guard. **Android's swap handlers have no such guard** (`swapSeats`, `swap2Pass` and the Offer10 branch all act on any table id), so Android needs a new `playTurnSoundForTable(int)` helper that repeats the visible-fragment check already used by `addTableMessage` (`:554-562`) and `updateTableMove` (`:570`) |
| **Mute bypass** | Android routes through `playSound()`; iOS routes through the new `playTurnSound()`; React routes through the existing intent queue |
| **Android media truncation** — one shared `MediaPlayer` with `reset()` per call | Pre-existing. A swap immediately followed by a move can cut the first tone short. Accepted; not worth a rework for this feature |

## Out of scope — known issues deliberately not fixed

These are pre-existing and affect the current move sound identically. They are recorded here so
they are not mistaken for regressions introduced by this change.

- **Stale mute on iOS.** `playSounds` (`RoomViewController.swift:55`) is a `let`, evaluated once
  at construction. Toggling "Turn off all in-app sounds" mid-session has no effect until the room
  VC is recreated. (`AppDelegate.m:349` reads the key fresh and is the correct pattern.)
- **Stale mute on Android.** The `silent` field (`LiveGameRoomActivity.java:73`) is read once in
  `onCreate` (`:107`) and never refreshed in `onResume`.
- **React has no mute preference at all.** The `MUTE`/`UNMUTE` actions mute a *player's chat and
  invitations*, not audio. React's swap sound will be unsilenceable — exactly as its move sound
  already is.
- **No foreground check** on any client's in-app sounds.
- **Turn-based / correspondence swap UIs** — `penteLive-iOS/.../BoardViewController.m`,
  `penteLive-Android/.../BoardActivity.java`, and
  `pente.org/dsg_src/java/org/pente/turnBased/web/MoveServlet.java` — have **no sound subsystem
  whatsoever**. Adding a cue there means building one from scratch. Flagged to the owner as a
  separate decision.
- **Legacy AWT/JNLP client** already implements a turn-gated version of this feature at
  `GameBoardFrame.java:1613-1616`, but only for the D-Pente family, and without a `silent` guard
  (so it beeps spuriously on rejoin). Out of scope, noted as prior art.

## Testing

- **React** — reducer-level unit tests asserting `pendingNotifications` contains
  `{sound: 'move'}` after a non-silent `dsgSwapSeatsTableEvent` and after an Offer10 carrying a
  `player`, and is empty for each silent / player-absent counterpart, for a different `table`,
  and for every event on the "never sound" list — `dsgSwap2PassTableEvent` (both flag values),
  `renjuSwap`, and `renjuSelect1`. Existing swap fixtures live in
  `src/protocol/__fixtures__/wire-fixtures.json`; existing swap-sequence tests are in
  `src/redux_reducers/__tests__/clockSwap.test.js`.
- **iOS / Android** — no existing test harness covers the sound path. Verification is manual:
  two clients, one table, run each of the two sounding events and each of the four excluded
  events, plus a mid-game rejoin to confirm silence.
- **Manual cross-client check** — a D-Pente colour choice, a Swap2 colour choice, a Renju
  take-over and a Renju ten-stone offer must each sound; a Swap2 pass must not. Observe each from
  both seats and from a spectator, on all three clients. Include a D-Pente `swap=true` and a
  Swap2 `swap=false` specifically: those are the branches where the decider keeps the move, so
  confirm by ear that the early cue is tolerable before treating that decision as settled.
- On iOS and Android the cue follows the **notification** volume and the hardware silent switch,
  not media volume. "I heard nothing" is not evidence the code did not run.
