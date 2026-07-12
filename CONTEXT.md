# pente.org gameServer — Context

Domain and architecture vocabulary for the live game server (`dsg_src/java/org/pente/gameServer/**`). Keeps naming consistent across future work and architecture reviews.

## Language

**MainRoom**:
The lobby of one server: chat, the player list, join/exit, boot, tourney announcements. One per server.
_Avoid_: lobby, hall.

**Table**:
A single game table: seating, ready/click-play, game start, move handling, offers (undo/cancel/resign), timers, and end-of-game persistence. Many per server.
_Avoid_: room, board (the board is the grid state, not the table).

**Serialization invariant**:
All mutation of a `MainRoom` or `Table` happens on **one** thread. Thread-safety comes from serializing every incoming event onto a single consumer thread — not from per-method locks. Any read from another thread must lock the wrapper instance (e.g. `getPlayersInMainRoom`).
_Avoid_: "thread-safe wrapper", "synchronized decorator" (only the `game`/`gameDatabase` value objects use a true synchronized decorator; the server rooms/tables are active objects).

**SerialEventPump**:
The deep module that owns the serialization invariant for a room or table: a `SynchronizedQueue` drained by one dedicated thread that hands each `DSGEvent` to a `DSGEventListener` sink. Interface is three verbs — construct(name, sink), `submit(event)`, `stop()` — plus a read-only `isAlive()` for test observation. Replaces the hand-copied queue+thread+`running`-flag lifecycle previously duplicated in `SynchronizedServerTable` and `SynchronizedServerMainRoom`. Construct the pump before publishing the wrapper as a listener, so an event can never reach a null pump.
_Avoid_: event loop, dispatcher thread, executor.

**Sink**:
The dispatch callback a `SerialEventPump` feeds — a `DSGEventListener` whose `eventOccurred` fans one event type out to the room/table handler methods. Runs on the pump thread.
_Avoid_: handler, listener (too generic here), consumer.

## Notes

- `EndGameRunnable` (inside `ServerTable`) is a *separate* active object with deliberately different semantics: a custom item type, stop-on-`Throwable` recovery, and per-game thread re-creation. It is **not** a `SerialEventPump` and is intentionally left outside it.
