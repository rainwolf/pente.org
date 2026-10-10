# Arena persistent join requests — design

Date: 2026-10-10. Repos: `pente.org` (server, owns protocol), `react_live_game_room`, `penteLive-Android`, `penteLive-iOS`.

## Goal

Arena join requests stop being ephemeral. A request lives until the server ends it. The server holds all request state and pushes it. Clients render what the server sends and run no expiry timers.

## Current state (2026-10-10, all repos on `main`)

- Server keeps requests per table in `ArenaServerTable.joinRequestMap` (keyed by requester) and a 60 s (second) reject cooldown in `rejectMap`. No index by requester. Entries leave only via `clear()` on accept.
- The "already pending" check never fires: it compares against client-sent `time`, which every client sends as 0.
- Clients fake expiry: React `TableCard` 6 s overlay, Android `ArenaJoinRequestAdapter` 6 s auto-remove, iOS `ArenaJoinRequestList` row timers + `RoomViewController` 6 s tap block.
- Android and iOS send decline as `DSGArenaRejectTableJoinEvent` (capital key). Gson cannot decode it, the server treats the frame as an error and disconnects the owner, whose table then closes.
- Requesters are never told about cancellation or table close. Owners are never told a request went away.
- Accept/decline do not check that the sender is the owner.
- Each table runs its own event pump (`SerialEventPump`). `Server.removeTable` → `SynchronizedServerTable.destroy()` stops the pump, dropping queued events. Table numbers are reused (`ArenaServer.createNewTable` fills null slots). Tables are removed from two places: `ServerTable.exit()` and `ServerTable.handleMainRoomJoin()`.

## Server rules

Terms. A table is **open** when it has exactly 1 player (the owner) and `state == NO_GAME_IN_PROGRESS`. A table's **memory** is its pending requests, its blocked set, and any outstanding claim.

R1. A request is keyed by (requester, table). It is addressed to that table's owner.

R2. A request is created only if all hold:
- the table exists, is an arena table, is open, and has no outstanding claim (R5);
- the requester is in the main room, is not the owner, is not seated at any table, and has no outstanding claim anywhere;
- no pending (requester, table) exists;
- the requester is not in the table's blocked set;
- the existing guest-on-rated-table rule still applies.

A refused attempt sends the requester `dsgArenaRequestEndedEvent` with the matching reason. A request to a table number that no longer exists (dropped today as "Invalid table" in `ArenaServer.routeEventToTable`) is refused with `NOT_AVAILABLE`.

R3. Withdraw (requester, new C→S event): pending (requester, table) is removed and the requester joins the table's blocked set. Withdraw then re-request is spam, so it is treated like a decline.

R4. Decline (owner): pending (requester, table) is removed, the requester joins the table's blocked set and gets `DECLINED`. Blocked requesters cannot request that table until R6 wipes its memory.

R5. Accept (owner) only **claims**. Atomically:
- check (requester, table) is pending and the requester has no claim elsewhere. Otherwise answer the owner with `NO_LONGER_AVAILABLE` and a snapshot;
- remove (requester, table) and record the claim (requester → table);
- queue the `DSGJoinTableEvent` on the table's pump, as today.

No other request is touched yet. While the claim is outstanding, R2 refuses new requests to that table and new requests from that requester.

R6. The claimed join lands in `ArenaServerTable.handleJoin`. Before calling `super.handleJoin` it checks that the requester is still in the main room and not seated elsewhere. If not, it releases the claim, refuses the join, and sends the owner `NO_LONGER_AVAILABLE` plus a snapshot. This also stops the phantom `null` seat `super.handleJoin` creates today for a player who has left. Whenever a table reaches 2 players by any path (claimed join, returning player, admin join):
- the remaining pending requests to it end with `TABLE_FULL`;
- its memory is wiped (blocked set, claim);
- the joining player's requests at all other tables are removed silently (R7).

R6b. When a table goes from not open to open, its memory is wiped and the owner gets a fresh (empty) snapshot. Anyone may then request, including previously blocked players. This happens when:
- the 2-player table's owner leaves while no game is in progress, and the remaining player becomes the owner (`exit()` sends `DSGOwnerTableEvent`);
- one player leaves after the game ends;
- a 1-player table waiting for a player to return (`WAITING_FOR_PLAYER_TO_RETURN`) has its set cancelled or force-resigned, so it moves to `NO_GAME_IN_PROGRESS`.

The table publishes the change into the registry (R10) from its own pump, on every change to the player count, owner, or `state`. While a game or set is in progress, the table stays not open whatever its player count.

R7. A requester's requests at all tables are removed silently (no notice, self-caused) when they join or create any table (hook: `ArenaServerTable.handleJoin`, which every join passes through) or leave the main room, including on disconnect (hook: `handleMainRoomExit`). There is no grace period.

R8. Table removal is hooked in `ArenaServerTable.destroy()`, which covers both removal paths:
- pending requesters get `TABLE_CLOSED`;
- a claimed requester has the claim released and gets `TABLE_CLOSED`. Their other requests were never touched (R5), so they lose nothing else;
- the table's memory is purged so a reused table number starts clean.

R9. Only the owner may accept or decline. A non-owner sender is ignored and logged, then answered with an empty snapshot for that table so their client never hangs (R11).

R10. Concurrency.
- All request state lives in one `ArenaJoinRequestRegistry` owned by `ArenaServer`. Every mutation runs in a `synchronized` method, so cross-table removal is one atomic step.
- The registry never takes the `tables` lock (`removeTable` holds `tables` then calls `destroy()`, which takes the registry lock, so the reverse order would deadlock).
- Each table publishes its own state (owner, open, seated players) into the registry from its own pump on join, exit, and state change. R2's "seated at any table" and "table open" checks read the registry, not `server.tables`. The unlocked `server.tables` scan in `handleArenaAcceptJoin` goes away.
- Outgoing events are enqueued while the lock is held: `routeEvent` is a non-blocking queue add, so per-client order matches mutation order. Payloads are immutable copies, because Gson serializes them later on the writer thread.

R11. Every client action gets an answer.
- request and withdraw → `dsgArenaMyRequestsEvent`, even when nothing changed;
- accept and decline → `dsgArenaJoinRequestsEvent` for that table, even when nothing changed.

Clients use this to clear in-flight state without a timer.

## Wire protocol

Envelope unchanged: `{"<key>": {fields}}`, lowercase `dsg…` keys.

C→S, existing, shape unchanged:
- `dsgArenaRequestJoinTableEvent {table}`
- `dsgArenaAcceptTableJoinEvent {table, playerToAccept}`
- `dsgArenaRejectTableJoinEvent {table, playerToReject}`

C→S, new: `dsgArenaWithdrawJoinRequestEvent {table}`. It needs:
- a `DSGTableEvent` subclass, so `ServerPlayer` routes it;
- a `DSGEventWrapper` field;
- a case in the `SynchronizedServerTable` dispatch switch (its `default` drops unknown events silently);
- a no-op handler on `ServerTable`;
- descriptors in React `protocol/messages.js` and the Android/iOS encoders.

S→C, new (each needs a `DSGEventWrapper` field and decoders in all three clients):
- `dsgArenaJoinRequestsEvent {table, requests: [{player, seq}]}` → owner. The full pending list for that table, ordered by `seq` ascending. `seq` is server-assigned at creation, increasing, and never reused. Sent on every change to the table's pending set, on R11, and when a player becomes the owner of a table.
- `dsgArenaMyRequestsEvent {tables: [int]}` → requester. The full set of tables they have pending requests at. Sent on every change to that set, on R11, and when they join the main room.
- `dsgArenaRequestEndedEvent {table, player, owner, reason}` → whoever needs a notice. `player` is the requester and `owner` is the table owner's name. Reasons:
  - `DECLINED`, `TABLE_FULL`, `TABLE_CLOSED`;
  - `DUPLICATE`, `BLOCKED` (in the blocked set);
  - `NOT_AVAILABLE` (an R2 table or requester check failed);
  - `GUEST_RATED`;
  - `NO_LONGER_AVAILABLE` (to the owner, R5/R6).

No longer sent S→C: `dsgArenaRequestJoinTableEvent` and `dsgArenaRejectTableJoinEvent`. Snapshots and `dsgArenaRequestEndedEvent` replace them.

Old app builds are not supported. They ignore the new events and keep their old timers. There is no server alias for the capital-key decline.

## Clients

C1. Remove all countdowns:
- React: the `TableCard` overlay and `countdown` prop.
- Android: the `ArenaJoinRequestAdapter` timeout, ticker, and progress bar.
- iOS: the `ArenaJoinRequestList` row timers and progress, and the `RoomViewController` countdown overlay.

C2. The owner's requests list renders only the latest `dsgArenaJoinRequestsEvent` for its table, in `seq` order, keyed by player.
- **Freeze.** When a snapshot arrives while the list is visible, the list ignores taps and swipes and shows a spinner overlay for 400 ms (milliseconds). It then applies the newest snapshot and unfreezes. Snapshots that arrive during the freeze replace the held one, and only the last is applied.
- **Minimum interactive window.** After an unfreeze the list stays interactive for at least 1000 ms. Snapshots arriving in that window are held, without changing the rows, and trigger the next freeze when the window ends. A burst of updates therefore cannot lock the owner out.
- **Empty snapshot.** The list hides after the freeze.
- **New-request cue.** The existing sound and tab cue fires when a snapshot contains a player who was not in the previous one.
- **Accept/decline.** No optimistic removal. The tapped row shows a spinner and the whole list ignores taps until the next snapshot for that table arrives (guaranteed by R11). Then the freeze applies.
- **Decline key.** The client sends lowercase `dsgArenaRejectTableJoinEvent`. This fixes the disconnect on Android and iOS. Android's capital-key test in `ArenaEventsTest` is replaced.

C3. Requester lobby.
- **"Requested" mark.** Tables in the latest `dsgArenaMyRequestsEvent` show it.
- **Requesting.** Tapping an unmarked table sends a request and ignores further taps on that table until the next `dsgArenaMyRequestsEvent` arrives (R11). Double-taps therefore never produce `DUPLICATE`.
- **Withdrawing.** Tapping a marked table asks "Withdraw your request to table <n>? You won't be able to request it again until its next game." On confirm the client sends withdraw. The confirm protects against mis-taps, since the lobby list is not frozen and withdraw blocks re-requests.

C4. `dsgArenaRequestEndedEvent` shows a short notice. The style follows each app: React snackbar, Android Toast, iOS banner or alert.
- `DECLINED`: "<owner> declined your request"
- `TABLE_FULL`: "Table <n> is no longer available"
- `TABLE_CLOSED`: "Table <n> was closed"
- `DUPLICATE`: "You already requested table <n>"
- `BLOCKED`: "You can't request table <n> again until its next game"
- `NOT_AVAILABLE`: "You can't join table <n> right now"
- `GUEST_RATED`: "Guests can't join rated tables"
- `NO_LONGER_AVAILABLE` (owner): "<player> is no longer available"

C5. Bugs fixed because the redesign rewrites those lines:
- React:
  - `TableClass.newInstance` drops `arenaPlayerRequests` (replaced by snapshot state);
  - decline dispatches a bare string;
  - `mute` is not mapped.
- Android:
  - no handler for incoming reject/ended events;
  - a swipe at `NO_POSITION`.
- iOS:
  - force-unwrap of `tableViewController` off the main thread (`RoomViewController.swift:521`);
  - rows are not removed after accept/decline;
  - the popover is presented twice.

iOS UI must work on iPhone and iPad (`penteLive-iOS/CLAUDE.md`).

## Testing

- **Server:** unit tests for `ArenaJoinRequestRegistry` and the `ArenaServerTable` hooks. Cover R2–R11, including:
  - a double accept of the same requester;
  - accept then table removal before the join lands;
  - accept then requester leaving before the join lands;
  - table-number reuse;
  - the R6b reopen transitions (owner change, waiting set cancelled or force-resigned);
  - withdraw blocking re-requests.

  Follow `server/test/ServerTableDrawScoringTest.java` and its `build.xml` wiring.
- **React:** vitest reducer tests for the snapshot, my-requests, and ended events. Test the freeze and min-window logic as a pure function or hook with fake timers.
- **Android:** JSON-shape tests for the new and fixed events. Unit-test the adapter's diff and freeze logic.
- **iOS:** unit tests for event parsing and the list model in the hosted test target.
- **E2E on the local stack** (`./justCompile` compiles and restarts the container): two React instances plus one Android or iOS device. Scenarios:
  - request → decline → re-request refused;
  - withdraw → re-request refused;
  - accept → the requester's other requests cancelled, other requesters see `TABLE_FULL`;
  - game ends, table open → previously blocked player can request;
  - table close;
  - the owner leaves a finished 2-player table → the joiner becomes owner, the table is open, a previously blocked player can request;
  - a waiting set is cancelled or force-resigned → the table reopens with its memory wiped;
  - requester disconnect;
  - owner exits right after accept.

## Non-goals

- Blocking a plain `dsgJoinTableEvent` that bypasses arena requests.
- Freezing the lobby table list.
- A grace period for reconnecting requesters.
- Supporting old app builds.
- Removing the unused `ArenaServerMainRoom` class or the dead `closeTableTimer` code.
