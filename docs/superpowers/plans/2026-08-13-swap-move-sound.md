# Swap-choice move sound — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play each live client's existing "new move" sound when an opening swap choice is made, so the player who is now on the move gets an audible cue.

**Architecture:** Client-only. No server change, no protocol change, no new audio assets. Three swap wire events get a sound call added inside each client's existing table-scoping guard, reusing that client's existing move-sound asset and mute check.

**Tech Stack:** React 18 + Redux + Vitest (`react_live_game_room`); Swift + AudioToolbox (`penteLive-iOS`); Java + MediaPlayer (`penteLive-Android`).

**Spec:** `pente.org/docs/superpowers/specs/2026-08-13-swap-move-sound-design.md`

> **AMENDED after the final whole-branch review (2026-08-13).** `dsgSwap2PassTableEvent` was
> originally the third sounding event. It does **not** hand the turn over — in Swap2, "let p1
> decide" means player 2 places two *more* stones and keeps the move. Verified in
> `SimplePenteState.getCurrentPlayer()` / `isValidMove` and by the single-index timer restart in
> `ServerTable.handleSwap2Pass`. The owner ruled to drop that cue. **Tasks 1–3 below still show
> the original three-event steps as they were executed; the swap2-pass step in each was
> subsequently reverted by the final fix wave.** Task 4's matrix has been corrected in place —
> use it as written.

## Global Constraints

- **Sound on exactly two wire events, and only these:**
  - `dsgSwapSeatsTableEvent` when `silent == false`
  - `dsgRenjuTaraguchiOffer10TableEvent` when `player` is present and non-null
- **Never sound on:** `dsgSwapSeatsTableEvent` with `silent == true`; `dsgSwap2PassTableEvent` (either flag value); `dsgRenjuTaraguchiOffer10TableEvent` with `player` absent; `dsgRenjuTaraguchiSwapTableEvent` (any variant); `dsgRenjuTaraguchi10Select1TableEvent`.
- **No audience filter.** Everyone viewing the table hears it, *including the player who made the choice*. Do not add a `player != me` check. This is a deliberate owner decision.
- **Always inside the table guard.** The server broadcasts swap events to every player in the lobby (`ServerTable.broadcastMainRoom`), not just the table. A sound call outside the table-identity guard fires for every table in the room.
- **Reuse the existing move-sound asset and the existing mute path.** No new `.mp3`/`.caf`, no new preference, no direct `MediaPlayer`/`AudioServices` call outside the designated helper.
- **`player` may be absent, not null.** Gson omits null fields, so the rejoin replay of the Offer10 frame has no `player` key at all. Guards must treat absent and null alike: `data.player != null` (JS/Java), `event["player"] as? String != nil` (Swift).
- **Do not fix** the stale-mute bugs (iOS `let playSounds`, Android `silent` cached in `onCreate`), and do not add a mute preference to React. Out of scope; they affect the existing move sound identically.
- **Branch before committing.** All three repos are currently on `main`. Each task creates `swap-move-sound` in its own repo first.
- **Sign commits** with `git commit -S`.

---

## File Structure

| Repo | File | Change |
|---|---|---|
| `react_live_game_room` | `src/redux_reducers/utils.js` | Modify `swapSeats`, `swap2Pass`, `renjuOffer10` — add one `emit` each |
| `react_live_game_room` | `src/redux_reducers/__tests__/swapSound.test.js` | Create — pins which swap events emit a `move` notification |
| `penteLive-iOS` | `test1/RoomViewController.swift` | Add `playTurnSound()`; route the existing move site through it; call it from three swap handlers |
| `penteLive-Android` | `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java` | Add `playTurnSoundForTable(int)`; call it from `swapSeats`, `swap2Pass`, and the Offer10 branch |

The three tasks are independent — no task consumes anything another produces. They may be reviewed and merged separately.

---

### Task 1: React live game room

**Files:**
- Modify: `react_live_game_room/src/redux_reducers/utils.js` (`swapSeats` at `:409`, `swap2Pass` at `:535`, `renjuOffer10` at `:591`)
- Test: `react_live_game_room/src/redux_reducers/__tests__/swapSound.test.js` (create)

**Interfaces:**
- Consumes: `emit(state, notification)` — the module-local helper at `utils.js:9-11`. It appends to `state.pendingNotifications`; `notificationMiddleware` drains that queue and calls `AudioService.play(n.sound)`.
- Produces: nothing consumed by other tasks.

**Background the implementer needs:** reducers in this codebase are pure. They never call the audio API directly — they push an intent `{sound: 'move'}` and the middleware plays it. The existing move sound does exactly this at `utils.js:214-216`. Note that the move sound filters on `data.player !== state.me`; the swap sound deliberately does **not**.

- [ ] **Step 1: Branch**

```bash
cd react_live_game_room
git checkout -b swap-move-sound
```

- [ ] **Step 2: Write the failing test**

Create `react_live_game_room/src/redux_reducers/__tests__/swapSound.test.js`:

```javascript
// A swap choice hands the turn over without placing a stone, so no dsgMoveTableEvent follows and
// the normal move sound never fires. These tests pin which swap frames emit the cue and which
// must stay silent -- the silent=true frames are rejoin/state-sync replay markers, and the
// Offer10 replay is recognisable only by its MISSING `player` key (Gson omits null fields).
import {describe, test, expect} from 'vitest';
import Table from '../../Classes/TableClass';
import {swapSeats, swap2Pass, renjuOffer10} from '../utils';

const TABLE = 5;
const OTHER_TABLE = 6;

function gameStub() {
   const g = {
      gameState: {
         renjuState: {awaitingSwap: true, branchChosen: false, tenOffer: false, offered: []},
      },
      moves: [],
      isRenjuGame: () => false,
      swap2Pass: () => {},
   };
   g.newInstance = () => gameStub();
   return g;
}

function stateAtTable() {
   const t = new Table();
   t.seats = [undefined, 'alice', 'bob'];
   return {table: TABLE, me: 'alice', tables: {[TABLE]: t}, game: gameStub()};
}

const sounds = (state) => (state.pendingNotifications || []).map((n) => n.sound);

describe('swap choices emit the move sound', () => {
   test('a live seat swap emits move', () => {
      const state = stateAtTable();
      swapSeats({table: TABLE, swap: true, silent: false, player: 'bob'}, state);
      expect(sounds(state)).toEqual(['move']);
   });

   test('a live seat swap emits move even when the chooser is me', () => {
      const state = stateAtTable();
      swapSeats({table: TABLE, swap: true, silent: false, player: 'alice'}, state);
      expect(sounds(state)).toEqual(['move']);
   });

   test('a declined seat swap still emits move -- the turn moved either way', () => {
      const state = stateAtTable();
      swapSeats({table: TABLE, swap: false, silent: false, player: 'bob'}, state);
      expect(sounds(state)).toEqual(['move']);
   });

   test('a silent seat swap (rejoin marker) is quiet', () => {
      const state = stateAtTable();
      swapSeats({table: TABLE, swap: true, silent: true}, state);
      expect(sounds(state)).toEqual([]);
   });

   test('a seat swap at another table is quiet', () => {
      const state = stateAtTable();
      swapSeats({table: OTHER_TABLE, swap: true, silent: false, player: 'bob'}, state);
      expect(sounds(state)).toEqual([]);
   });

   test('a live swap2 pass emits move', () => {
      const state = stateAtTable();
      swap2Pass({table: TABLE, silent: false, player: 'bob'}, state);
      expect(sounds(state)).toEqual(['move']);
   });

   test('a silent swap2 pass (rejoin marker) is quiet', () => {
      const state = stateAtTable();
      swap2Pass({table: TABLE, silent: true}, state);
      expect(sounds(state)).toEqual([]);
   });

   test('a live renju ten-stone offer emits move', () => {
      const state = stateAtTable();
      renjuOffer10({table: TABLE, moves: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10], player: 'bob'}, state);
      expect(sounds(state)).toEqual(['move']);
   });

   test('a replayed renju ten-stone offer (no player key) is quiet', () => {
      const state = stateAtTable();
      renjuOffer10({table: TABLE, moves: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]}, state);
      expect(sounds(state)).toEqual([]);
   });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd react_live_game_room
npx vitest run src/redux_reducers/__tests__/swapSound.test.js
```

Expected: the five "emits move" tests FAIL with `expected [] to deeply equal [ 'move' ]`. The four "quiet" tests already pass — that is correct, they are the regression guards.

- [ ] **Step 4: Add the emit to `swapSeats`**

In `src/redux_reducers/utils.js`, in `swapSeats`, the block currently ends:

```javascript
      state.game = game;
   }
}
```

Change it to:

```javascript
      state.game = game;
      // A swap hands the turn over without placing a stone, so no move event follows and the
      // move sound at addMove never fires. Cue it here instead. Unlike the move sound this is
      // NOT filtered to `data.player !== state.me` -- everyone at the table hears it.
      // silent=true is the rejoin/state-sync replay marker, not a live choice.
      if (!data.silent) {
         emit(state, {sound: 'move'});
      }
   }
}
```

- [ ] **Step 5: Add the emit to `swap2Pass`**

Replace the body of `swap2Pass`:

```javascript
export function swap2Pass(data, state) {
   if (data.table === state.table) {
      const game = state.game.newInstance();
      game.swap2Pass();
      state.game = game;
      // "Let p1 decide": the turn returns to p1 with no stone placed. See swapSeats above.
      if (!data.silent) {
         emit(state, {sound: 'move'});
      }
   }
}
```

- [ ] **Step 6: Add the emit to `renjuOffer10`**

Replace the body of `renjuOffer10`:

```javascript
export function renjuOffer10(data, state) {
   if (data.table === state.table) {
      const game = state.game.newInstance();
      const r = game.gameState.renjuState;
      r.branchChosen = true;
      r.tenOffer = true;
      r.offered = [...data.moves];
      r.awaitingSwap = false;
      state.game = game;
      // The offer hands SELECTION to the opponent with no move event. This frame carries no
      // `silent` flag, so the rejoin replay is recognisable only by its missing `player`
      // (ServerTable.java:649 constructs it with player=null, and Gson omits null fields).
      if (data.player != null) {
         emit(state, {sound: 'move'});
      }
   }
}
```

- [ ] **Step 7: Run the new test to verify it passes**

```bash
cd react_live_game_room
npx vitest run src/redux_reducers/__tests__/swapSound.test.js
```

Expected: 9 passed.

- [ ] **Step 8: Run the full suite to verify nothing regressed**

```bash
cd react_live_game_room
npm test
```

Expected: all suites pass. Pay particular attention to `clockSwap.test.js` and `renjuSwapBranchA.test.js`, which drive `swapSeats` directly.

- [ ] **Step 9: Commit**

```bash
cd react_live_game_room
git add src/redux_reducers/utils.js src/redux_reducers/__tests__/swapSound.test.js
git commit -S -m "feat: play the move sound when a swap choice is made

A swap choice passes the turn without placing a stone, so no
dsgMoveTableEvent follows and the move sound never fired. Cue it from
swapSeats, swap2Pass and renjuOffer10 instead. Rejoin replay markers
(silent=true, or a missing player on the offer frame) stay quiet."
```

---

### Task 2: iOS

**Files:**
- Modify: `penteLive-iOS/test1/RoomViewController.swift` — add `playTurnSound()`; `moveTableEvent` (`:945`); `swapSeatsTableEvent` (`:757`); `swap2PassTableEvent` (`:772`); `renjuOffer10TableEvent` (`:792`)

**Interfaces:**
- Consumes: `playSounds` (`RoomViewController.swift:55`) and `newMoveSndID` (`:52`, loaded in `viewDidLoad` at `:123-127`) — both stored properties of `RoomViewController`.
- Produces: `private func playTurnSound()` — no args, no return. Every in-app turn cue in this file goes through it.

**Background the implementer needs:** all sound state lives on `RoomViewController`. `TableViewController` has no back-reference to it, so the calls must be made here. Every handler body already runs inside `DispatchQueue.main.async`, so no threading work is needed. The existing handlers force-cast their fields (`as! Bool`); do **not** extend that pattern to `player`, which is absent on the replay frame — use `as? String`.

There is no test harness for this path; verification is a clean build plus the manual matrix in Task 4.

- [ ] **Step 1: Branch**

```bash
cd penteLive-iOS
git checkout -b swap-move-sound
```

- [ ] **Step 2: Add the `playTurnSound` helper**

In `test1/RoomViewController.swift`, immediately **above** `func swapSeatsTableEvent(event: [String: Any]) {` (currently line 757), insert:

```swift
    /// The cue that it is now someone's turn at the open table. Used by the move event and by
    /// every swap choice that hands the turn over without placing a stone. Kept in one place so
    /// the mute check cannot drift between the two.
    private func playTurnSound() {
        if playSounds {
            AudioServicesPlaySystemSound(newMoveSndID)
        }
    }
```

- [ ] **Step 3: Route the existing move sound through the helper**

In `moveTableEvent`, replace:

```swift
                if move != 0 {
                    if self.playSounds {
                        AudioServicesPlaySystemSound(self.newMoveSndID)
                    }
                    self.tableViewController?.addMove(move: move)
```

with:

```swift
                if move != 0 {
                    self.playTurnSound()
                    self.tableViewController?.addMove(move: move)
```

- [ ] **Step 4: Cue on a seat swap**

Replace the body of `swapSeatsTableEvent`:

```swift
    func swapSeatsTableEvent(event: [String: Any]) {
        DispatchQueue.main.async {
            let tableId = event["table"] as! Int
            let swap = event["swap"] as! Bool
            let silent = event["silent"] as! Bool
            self.playersAndTables.swapSeats(tableId: tableId, swap: swap, silent: silent)
            if tableId == self.tableViewController?.table.table {
                self.tableViewController?.stateChanged()
                // A swap choice passes the turn without placing a stone, so no move event
                // follows. silent=true is the rejoin/state-sync marker, not a live choice.
                if !silent {
                    self.playTurnSound()
                }
            }
        }
    }
```

- [ ] **Step 5: Cue on a swap2 pass**

Replace the body of `swap2PassTableEvent`:

```swift
    func swap2PassTableEvent(event: [String: Any]) {
        DispatchQueue.main.async {
            let tableId = event["table"] as! Int
            let silent = event["silent"] as! Bool
            self.playersAndTables.swap2Pass(tableId: tableId, silent: silent)
            if tableId == self.tableViewController?.table.table {
                self.tableViewController?.stateChanged()
                // "Let p1 decide" -- the turn returns to p1 with no stone placed. Note
                // LiveTable.swap2Pass(silent:) discards the flag, so gate on the local.
                if !silent {
                    self.playTurnSound()
                }
            }
        }
    }
```

- [ ] **Step 6: Cue on a renju ten-stone offer**

Replace the body of `renjuOffer10TableEvent`:

```swift
    func renjuOffer10TableEvent(event: [String: Any]) {
        DispatchQueue.main.async {
            let tableId = event["table"] as! Int
            let moves = event["moves"] as! [Int]
            self.playersAndTables.renjuOffer10(tableId: tableId, moves: moves)
            if tableId == self.tableViewController?.table.table {
                self.tableViewController?.stateChanged()
                // The offer hands SELECTION to the opponent with no move event. This frame
                // carries no `silent` flag; the rejoin replay is recognisable only by its
                // missing `player` (ServerTable.java:649 builds it with player=nil, and the
                // server's Gson encoder omits null fields entirely).
                if event["player"] as? String != nil {
                    self.playTurnSound()
                }
            }
        }
    }
```

- [ ] **Step 7: Leave `renjuSwapTableEvent` and `renjuSelect1TableEvent` untouched**

Confirm by reading them that neither gained a `playTurnSound()` call. A take-over is delivered as a synthesized `dsgSwapSeatsTableEvent` (already cued in Step 4); a decline with a bundled stone and a select-1 are each followed by a real `dsgMoveTableEvent` (already cued in Step 3); a window-5 decline does not pass the turn at all. Adding a call in either handler double-beeps.

- [ ] **Step 8: Build**

```bash
cd penteLive-iOS
xcodebuild -workspace penteLive.xcworkspace -scheme test1 \
  -destination 'generic/platform=iOS Simulator' build
```

Expected: `** BUILD SUCCEEDED **`. Build the **workspace**, not the `.xcodeproj`. If the generic
destination is rejected, substitute a concrete one from `xcodebuild -showdestinations -workspace
penteLive.xcworkspace -scheme test1`; do not switch to a device destination, which would drag in
code signing.

- [ ] **Step 9: Commit**

```bash
cd penteLive-iOS
git add test1/RoomViewController.swift
git commit -S -m "feat: play the move sound when a swap choice is made

A swap choice passes the turn without placing a stone, so no
dsgMoveTableEvent follows and the move sound never fired. Add
playTurnSound() and call it from the seat-swap, swap2-pass and renju
offer-10 handlers; route the existing move site through it too so the
mute check stays in one place. Rejoin replay markers stay quiet."
```

---

### Task 3: Android

**Files:**
- Modify: `penteLive-Android/app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java` — add `playTurnSoundForTable(int)`; `swapSeats` (`:663`); `swap2Pass` (`:695`); the `dsgRenjuTaraguchiOffer10TableEvent` branch (`:397-411`)

**Interfaces:**
- Consumes: `playSound(int)` (`:188`) and the `NEW_MOVE_SOUND` constant (`:76`).
- Produces: `private void playTurnSoundForTable(final int tableId)` — no return.

**Background the implementer needs, and the one real trap:** unlike React and iOS, Android's swap handlers have **no table-identity guard** — `swapSeats`, `swap2Pass` and the Offer10 branch all act on whatever table id arrives. Because the server broadcasts swap events to the whole lobby, a bare `playSound(NEW_MOVE_SOUND)` in these handlers would beep for every swap at every table in the room. That is why Step 2 adds a helper that repeats the visible-fragment check already used by `addTableMessage` (`:554-562`) and `updateTableMove` (`:570`).

Second trap: `silent` means two opposite things in this file. The **field** at `:73` is the user's mute preference; the **locals** at `:666` and `:697` are the server's replay flag. Always play through `playSound(...)` so the mute field is read in exactly one place — never hand-roll a `MediaPlayer` call at these sites.

There is no test harness for this path; verification is a clean build plus the manual matrix in Task 4.

- [ ] **Step 1: Branch**

```bash
cd penteLive-Android
git checkout -b swap-move-sound
```

- [ ] **Step 2: Add the `playTurnSoundForTable` helper**

In `LiveGameRoomActivity.java`, immediately **after** the closing brace of `addTableMessage` (currently line 562) and **before** `private void updateTableMove(...)`, insert:

```java
    /**
     * The cue that it is now someone's turn. Swap events are broadcast to the whole main room,
     * not just the table, so the visible-fragment check is mandatory here -- without it a swap
     * at any table in the lobby would make noise. Mute is handled inside playSound().
     */
    private void playTurnSoundForTable(final int tableId) {
        LiveTableFragment fragment = (LiveTableFragment)
                getSupportFragmentManager().findFragmentByTag("liveTable");
        if (fragment != null && fragment.table.getId() == tableId) {
            playSound(NEW_MOVE_SOUND);
        }
    }
```

- [ ] **Step 3: Cue on a seat swap**

In `swapSeats(Map<String, Object> data)`, the method currently ends:

```java
        if (!silent) {
            if (swapped) {
                addTableMessage(tableId, "* " + getString(R.string.seats_swapped));
            } else {
                addTableMessage(tableId, "* " + getString(R.string.seats_not_swapped));
            }
        }
    }
```

Change it to:

```java
        if (!silent) {
            if (swapped) {
                addTableMessage(tableId, "* " + getString(R.string.seats_swapped));
            } else {
                addTableMessage(tableId, "* " + getString(R.string.seats_not_swapped));
            }
            // A swap choice passes the turn without placing a stone, so no dsgMoveTableEvent
            // follows and the move sound never fires. NOTE: `silent` here is the LOCAL at the
            // top of this method (the server's replay marker), not the mute field at :73.
            playTurnSoundForTable(tableId);
        }
    }
```

- [ ] **Step 4: Cue on a swap2 pass**

Replace the body of `swap2Pass(Map<String, Object> data)`:

```java
    private void swap2Pass(Map<String, Object> data) {
        final int tableId = (int) data.get("table");
        final boolean silent = (boolean) data.get("silent");
        Table table = tablesAndPlayers.tables.get(tableId);
        if (table != null) {
            table.swap2Pass(silent);
        }
        // "Let p1 decide": the turn returns to p1 with no stone placed. `silent` is the
        // server's rejoin replay marker, not the mute field.
        if (!silent) {
            playTurnSoundForTable(tableId);
        }
    }
```

- [ ] **Step 5: Cue on a renju ten-stone offer**

In the `dsgRenjuTaraguchiOffer10TableEvent` branch (currently `:397-411`), the block currently reads:

```java
                                    Table t = tablesAndPlayers.tables.get(tbl);
                                    if (t != null && t.isRenju()) {
                                        t.getGameState().renjuState.applyOffer10(moves);
                                        LiveTableFragment fragment = (LiveTableFragment)
                                                getSupportFragmentManager().findFragmentByTag("liveTable");
                                        if (fragment != null) {
                                            fragment.onRenjuDecisionEcho(tbl);
                                        }
                                    }
```

Append immediately after that closing brace, still inside the `else if` branch:

```java
                                    // The offer hands SELECTION to the opponent with no move
                                    // event. This frame carries no `silent` flag; the rejoin
                                    // replay is recognisable only by its missing `player`
                                    // (ServerTable.java:649 builds it with player=null, and the
                                    // server's Gson encoder omits null fields entirely).
                                    if (p.get("player") != null) {
                                        playTurnSoundForTable(tbl);
                                    }
```

- [ ] **Step 6: Leave the renju swap and select-1 branches untouched**

Confirm by reading them that neither the `dsgRenjuTaraguchiSwapTableEvent` branch (`:381-396`) nor the `dsgRenjuTaraguchi10Select1TableEvent` branch (`:411-424`) gained a call. A take-over arrives as a synthesized `dsgSwapSeatsTableEvent` (Step 3); a decline with a bundled stone and a select-1 are each followed by a real `dsgMoveTableEvent`, which already calls `playSound(NEW_MOVE_SOUND)` at `:573`; a window-5 decline does not pass the turn. Adding a call in either branch double-beeps.

- [ ] **Step 7: Build**

```bash
cd penteLive-Android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Gradle needs JDK 17+; the machine's default `JAVA_HOME` is JDK 8, hence the override.

- [ ] **Step 8: Commit**

```bash
cd penteLive-Android
git add app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java
git commit -S -m "feat: play the move sound when a swap choice is made

A swap choice passes the turn without placing a stone, so no
dsgMoveTableEvent follows and the move sound never fired. Add
playTurnSoundForTable() -- which repeats the visible-fragment check,
since swap events are broadcast lobby-wide -- and call it from
swapSeats, swap2Pass and the renju offer-10 branch. Rejoin replay
markers stay quiet."
```

---

### Task 4: Cross-client manual verification

**Files:** none — this task produces a written result, not a code change.

**Interfaces:**
- Consumes: the branches produced by Tasks 1–3.
- Produces: a pass/fail matrix reported back to the owner.

**Background:** on iOS and Android the cue follows the **notification** volume and the hardware silent switch, not media volume. "I heard nothing" is not evidence the code did not run — check the device is unmuted and the in-app sound setting is on before recording a failure.

Needs two accounts at one table plus a third client spectating. Local-backend credentials and the `PentePlayer.development=true` switch are covered by the existing project notes.

- [ ] **Step 1: Sounding cases — expect a cue on every client at the table**

For each of the three clients, from **both** seats and from a spectator:

| # | Action | Game type | Note |
|---|---|---|---|
| 1 | Accept the colour choice (`swap=true`) | D-Pente | the **decider keeps the move** here — confirm the early cue is tolerable |
| 2 | Decline the colour choice (`swap=false`) | D-Pente | genuine handoff |
| 3 | Accept the colour choice (`swap=true`) | Swap2 | genuine handoff |
| 4 | Decline the colour choice (`swap=false`) | Swap2 | the **decider keeps the move** here — confirm the early cue is tolerable |
| 5 | Take over at the move-4 window | Renju Taraguchi-10 | |
| 6 | Offer ten 5th-move candidates (Branch B) | Renju Taraguchi-10 | |

Expected: exactly **one** cue per action, heard by both players and the spectator. Case 5 must not double-beep (the take-over produces a synthesized seat-swap frame).

Cases 1 and 4 are the branches where the seat swap and move parity combine so that the player who just decided is also the player to move. The cue still fires, deliberately — it means "a swap decision landed at your table", not strictly "your turn". These two cases are the ones to listen to before treating that decision as settled.

- [ ] **Step 2: Silent cases — expect no cue at all**

| # | Action | Expected |
|---|---|---|
| 7 | Decline at a renju window 1–4 (stone bundled) | exactly one cue, from the follow-up move event — not two |
| 8 | Decline at renju window 5 (no stone, white keeps the turn) | no cue |
| 9 | Select one of the ten offered stones | exactly one cue, from the follow-up move event — not two |
| 10 | Rejoin a table whose swap window is already resolved | no cue |
| 11 | Rejoin a renju table mid Branch-B offer | no cue |
| 12 | A swap at a **different** table while you sit in the lobby | no cue |
| 13 | Swap2 "let p1 decide" pass, observed from **p1's** seat | **no cue** — p2 keeps the move and places two more stones; p1's real cue is the move sound when stone 5 lands |
| 14 | The two stones p2 then places after that pass | one ordinary move cue each |

Case 12 is the one that catches a missing table guard, and it is the case most likely to be skipped — do not skip it.

- [ ] **Step 3: Mute check**

With the in-app sound setting turned **off** (iOS "Turn off all in-app sounds"; Android "Mute in-app sounds"), repeat case 1. Expected: silent on iOS and Android. On React expect it to **still sound** — React has no sound preference at all, which is pre-existing and out of scope.

On iOS and Android the setting is cached at construction, so toggle it and then fully restart the app before testing.

- [ ] **Step 4: Report**

Report the matrix back to the owner: each numbered case, per client, pass/fail. Do not mark this task complete on inference — every row needs to have actually been run, and any row that was not run is reported as not run.

---

## Self-review notes

Checked against the spec:

- All three sounding events covered, once per client (Tasks 1/2/3, steps 4–6 in each).
- All four excluded events explicitly guarded, with an explicit "leave untouched" step in the two clients that have separate renju handlers.
- Rejoin guards present in all three clients for all three events.
- No audience filter anywhere; the React test pins the chooser-hears-it case so a later reader does not "fix" it.
- The Android table-guard gap — the one place where the spec's "call sites sit inside existing guards" did not hold — is handled by the new helper, and case 12 in Task 4 tests for it.
- Out-of-scope items (stale mute, React's missing preference, turn-based clients) are stated as constraints so no task quietly widens scope.
