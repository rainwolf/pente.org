# Android app — game-id touchpoint inventory

Scope: `pentelive-android/app` + `pentelive-android/rules`. Companion to
`map-react-android.md`/`map-react.md` (React/web) and
`../ios-game-id-touchpoints.md` (iOS) — this file supersedes the thin
20-touchpoint Android line item in `MASTER-game-id-refactor-report.md` with a
dedicated pass.

## Wire protocol (headline finding)

`liveGameRoom/SocketDSGEventHandler.java` + `ClientSocketDSGEventHandler.java`:
the live-game-room socket protocol is **UTF-8 JSON text messages delimited by
a single 0xFF (255) byte** on a raw TCP socket — not length-prefixed, not a
typed/fixed-width binary field. `game` travels as an ordinary JSON number
literal (e.g. `"game":31`). Consequence: **no hard width limit** on this
transport — it's bounded only by Java `int`/JSON parser range, effectively
unlimited for this refactor's purposes.

Also confirmed by grep (zero hits in this repo): there is **no
`DSGEventWrapper`-style reflection binary codec** anywhere in the Android
source tree. If that class exists it's not part of the Android app — don't
assume it constrains this client.

## Touchpoints

| # | File:Line | What | Notes |
|---|---|---|---|
| 1 | `rules/.../Variant.java:14-30` | New rules-module enum, 16 constants incl. `RENJU(31,...)` | Canonical (base, odd) ids only, 1-31; speed/TB derived arithmetically elsewhere, not enum members |
| 2 | `rules/.../Variants.java:90-94` | `fromGameId(int)`: `canonical = (id%2==0) ? id-1 : id`, plus hardcoded `id==81 → RENJU` | Graceful — returns `null` for unmapped ids. The `81` special-case is TB Renju (31+50), which doesn't fit the doubling scheme at all |
| 3 | `rules/.../Variants.java:96-109` | `gridSize()/captureRule()/stonesPerTurn()` | Throw `IllegalArgumentException` if `Variant` is `null` — i.e. crash-on-unknown *if* wired to `fromGameId` |
| 4 | `rules/.../Variants.java:33-83` | `fromGameType(String)` substring-chain classifier | Order-dependent (longest match first, commented warning at top of file); returns `null` for unrecognized strings |
| — | `app/src/test/.../VariantPredicateEquivalenceTest.java` | Only production/test caller of `fromGameId`/`fromGameType` found | **The new rules module is not wired into production Android code yet** — it exists purely as an equivalence-tested parallel model of `Table.java`'s legacy logic. Two sources of truth, currently in sync only by test coverage |
| 5 | `liveGameRoom/Table.java:53-87` | `gameNames` static `Map<Integer,String>`, 31 hardcoded entries (odd 1-31 = base, even 2-30 = Speed, 31 = Renju w/ no Speed pair) | The actual production source of truth for the live game room |
| 6 | `liveGameRoom/Table.java:165-177` `shouldTimerRun()` | `gameNames.get(game).contains(...)` — **no null check** | **Confirmed NPE crash path**: any game id not in the 31-entry map crashes on the first timer-eligibility check of a timed table with no moves yet |
| 7 | `liveGameRoom/Table.java:1011-1012` `getGameName()` | `return gameNames.get(game);` | Returns `null` silently for unknown id; downstream callers not exhaustively verified null-safe |
| 8 | `liveGameRoom/Table.java:1227` `getGameNames()` | Exposes the static map to other callers (spinners/adapters) | Single leak point if the map itself is ever swapped for something wider |
| 9 | `liveGameRoom/ArenaTableSetupDialog.java:66` | `int game = gameSpinner.getSelectedItemPosition()*2+1;` | Table-creation id is **computed from spinner row**, not looked up — bakes the odd/even doubling scheme directly into the UI. Cannot express any id outside `2n+1` |
| 10-15 | `res/values/strings.xml:338,356,374,396,428,475` | Six independently hand-maintained `<string-array>` game-type lists: `game_types_array`(32), `turn_based_game_types_array`, `database_game_types_array`(20), `live_game_types_array`(30), `all_game_types_array`, `ai_game_types_array`(32) | Position-indexed against server ids; no single source of truth, must be edited by hand in lockstep across all 6 + `Table.gameNames` for every new game |
| 16 | `KingOfTheHillActivity.java:495-588` | `gameType` int field threaded through `SendInvitationTask`/`LoadHillTask` | Plain int, no width issue; another independent id-carrying path |
| 17 | `KingOfTheHill.java` | `Integer.parseInt(gameId)` from a String | Ordinary 32-bit parse, no fixed-width binary constraint |
| 18 | `RatingStat.java:16,63,76,144` | `gameId` field; `Integer.parseInt(gameId)` (line 76); Parcelable `dest.writeInt(gameId)` (line 144) | **Different Parcelable encoding than `Game.java`** (writeInt vs. writeString) — both safe to 32-bit but the inconsistency is a refactor trap (easy to miss one when auditing "how is id encoded") |
| 19 | `liveGameRoom/ArenaJoinRequestAdapter.java:36-127` | `gameId` field → `player.getRating(gameId)` | Another id-keyed map lookup (in `PentePlayer`), not individually verified for null-safety — same pattern-risk as #6 |
| 20 | `JsonModels.java:50,59` | Two `public int gameId;` Gson-style fields | Server JSON deserialized straight into `int`; confirms JSON-text wire format (consistent with wire-protocol finding above), no codec-level width cap |
| 21 | `Game.java` | Parcelable `mGameID` is a `String` (`readString`/`writeString`) | No width constraint; also inconsistent encoding vs. `RatingStat.java` (#18) |
| 22 | `InvitationActivity.java:37-390` | `gameType` String field; spinner selection position persisted to prefs (`PREFS_INVITATIONGAME_KEY`) | Another independent spinner-position-as-id path |
| 23 | `InviteAIActivity.java:42-209` | Same pattern, `R.array.ai_game_types_array`, prefs key `PREFS_AIINVITATIONGAME_KEY` | |
| 24 | `DatabaseActivity.java:123` | Spinner bound to `R.array.database_game_types_array` | |
| 25 | `SocialActivity.java:173,183` | Spinner/list bound to `R.array.all_game_types_array` | |
| 26 | `liveGameRoom/LobbyActivity.java:107` | Spinner bound to `R.array.live_game_types_array` | |

**Not re-verified this pass** (flagged, not counted above): `TableListAdapter.java`
and `LiveTableFragment.java` were read in an earlier part of this session but
not re-confirmed after context compaction. Given they consume `Table.gameNames`
the same way `shouldTimerRun()` does, they should be checked for the same
unchecked-`.get()` NPE pattern before any rollout.

## Unknown-id behavior (silent-rollout question)

No graceful "Unknown Game" fallback exists anywhere in the Android codebase —
every `gameNames`/id-keyed map lookup found is a bare `.get()`, never
`getOrDefault`/null-guarded. Net behavior if a new/larger game id reaches this
app:

- **UI cannot create or select it at all.** Every entry point (Arena create,
  Invitation, InviteAI, Database, Social, Lobby spinners) is bounded by a
  fixed `strings.xml` array + position arithmetic (`row*2+1`). A new id simply
  has no UI slot — not a crash, just unreachable.
- **It CAN reach the app another way**: another client (web/iOS, or a future
  server push) creates/joins a table with the new id, and this Android client
  then lists or opens it. That's the crash path: `Table.shouldTimerRun()`
  NPEs on a timed table with no moves yet (confirmed). Elsewhere (`getGameName()`
  and likely `TableListAdapter`/`LiveTableFragment`) the failure mode is a
  silent `null` name rather than an immediate crash, but downstream use of
  that null is unverified.
- The wire protocol itself (plain JSON) imposes no ceiling — the ceiling is
  entirely in these six client-side lookup tables/arrays, not in the socket
  codec.

## Risk flags

1. `Table.shouldTimerRun()` NPEs on any unrecognized game id in a timed table with no moves yet — confirmed crash path, not just a display bug.
2. Six independently hand-maintained `strings.xml` game-type arrays plus `Table.gameNames` = 7 lists that must be updated in lockstep by hand for every new game; no single source of truth in production.
3. Table-creation UI computes id via `spinnerPosition*2+1` (`ArenaTableSetupDialog`) — the odd/even doubling scheme is baked into arithmetic, not configuration; it structurally cannot express a non-doubled id (mirrors the exact same `row*2+1` pattern found in iOS).
4. A parallel, better-designed `Variant`/`Variants` rules module already exists under `rules/` (single canonical enum, ids 1-31, `fromGameId`/`fromGameType`) but is wired only into unit tests, not into `Table.java`/`Game.java`/`KingOfTheHill` — two sources of truth today, kept in sync only by test coverage, not by construction.
5. Inconsistent Parcelable encoding of game id (`Game.java` writeString vs. `RatingStat.java` writeInt) — no current width problem, but a trap for whoever audits "how is id encoded" during the refactor.
6. `TableListAdapter.java` and `LiveTableFragment.java` not re-verified this pass — likely share risk #1's pattern; check before any rollout that changes id ranges.

## Bottom line for the staged-rollout plan

The Android app's wire encoding of `game` (JSON number, no fixed width) is not
the constraint — the constraint is entirely client-side static tables. A
staged rollout is *technically* safe against the wire protocol, but **not**
against `Table.shouldTimerRun()`'s crash, so any stage that lets a
pre-update Android client encounter a genuinely new (non-doubled, >31 or
otherwise-unmapped) game id in a live table it can see/join will crash that
client. Silent rollout is only safe for stages that keep new ids fully
invisible to old Android clients (e.g., new games routed through a
capability/version gate so old clients never list or open those tables) until
users have updated past a build that ships the new `Table.gameNames`
entries — there is no forward-compatible fallback path already in the code to
lean on.
