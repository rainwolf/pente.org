# No wide ServerTable event interface

`SynchronizedServerTable` dispatches ~30 `handleXxx` events to a concrete `ServerTable` (and its `TournamentServerTable`/`ArenaServerTable` subclasses); the table implements no interface. A natural suggestion is to extract a `ServerTableEvents` interface so the plain table becomes substitutable in tests. **We deliberately do not do this.**

## Why

- **It would be a shallow interface** — a 1:1 mirror of ~32 concrete methods, not a deepening. Deletion test: remove the interface and nothing concentrates; the wrapper still calls the same concrete methods. It adds a type, not leverage.
- **There is almost no consumer to justify the seam.** The only non-dispatch call the wrapper makes on the table is `destroy()`, and across the whole codebase external code reaches into `getServerTable().` exactly once (`isPlayerInTable` in `ArenaServerTable`). One real adapter, not two.
- **Its only payoff is marginal.** An interface + a construction seam would let a fake table unit-test the wrapper's `instanceof`/`switch` dispatch — i.e. test mechanical routing — while forcing changes into the concurrency-sensitive `SynchronizedServerTable` constructor.
- **It does not address the actual problem**, which is that `ServerTable` is a ~3800-line god class. A wide interface documents that surface without shrinking it.

## Revisit when

`ServerTable` is decomposed into genuinely distinct collaborators (e.g. seating, game lifecycle, offers, end-of-game persistence). Those narrower units may each warrant a real seam with a second adapter — at which point interfaces are earned rather than mirrored. See `CONTEXT.md` and the deferred `GameResultPersister` note.
