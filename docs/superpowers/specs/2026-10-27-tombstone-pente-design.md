# Tombstone Pente — Design

Date: 2026-10-27
Branch: `feat/tombstone-pente` (both `pente.org/` and `react_live_game_room/`, already checked out)
Status: Draft design (pending review, then four implementation plans — see §16)
Scope: server (game core, live server, archive, turn-based (TB) persistence and move servlet), website JSP pages and JS (TB board and game viewers), and the React live game room. Mobile apps are out of scope.

> **Do not deploy this branch to production.** This work registers Tombstone Pente like every other game and contains no beta gating. Beta gating ships later, as separate work. Until it exists, the branch must not be deployed with `sync_gameServer.sh` or merged into a deployable `main` state. See §15.

## 1. Overview

Tombstone Pente is Pente (five in a row, or five captured pairs; pair captures) plus gray **dead stones** that belong to neither player. The turn flow is Connect6-like. P1's first turn is the centre stone only. Every later turn, starting with P2, has two optional placements: first one stone of the mover's own colour, then one dead stone. Either placement may be passed, independently of the other.

The hard part is not the rules. It is that the existing code everywhere assumes a turn is one stored move (or, for Connect6, a fixed pair), and it uses `361` as both the Go pass and the "no next move" sentinel. Tombstone stores 1 or 2 entries per turn, and code `361` means "dead stone on point 0". Every decoder must therefore be game-aware.

## 2. Decisions

User decisions (authoritative, not open to change):

- **Rules**: Pente rules plus colour-neutral gray dead stones. Turn 0 = P1's centre stone, with no dead stone. Every later turn = [own stone or own pass] then [dead stone or nothing]. All four shapes are legal: own+dead, own only, dead only (own pass + dead), neither (own pass only).
- **Encoding**: own stone = point `0..360` (`x + 19*y`); dead stone = `361 + point` (`361..721`); own-stone pass = `722`. Dead-stone passes are **not stored**. `pente_move`, the archive and `tb_move` store the same codes. Each turn after the first stores exactly one own entry (`0..360` or `722`), optionally followed by one dead entry (`361..721`).
- **Endless passing**: the game is a draw after 3 consecutive empty rounds, meaning 6 consecutive empty turns (an empty turn = own pass and no dead stone). A full board is also a draw.
- **Zobrist hashing**: a new dedicated row of 361 freshly generated 64-bit values (SecureRandom, generated once, committed as constants) for colour-neutral dead stones, indexed by rotated point. Passes carry the hash forward (hash = board only). Existing `rand` values do not change.
- **TB moves are whole turns**: the move servlet receives `[own]`, `[own, dead]`, `[722]` or `[722, dead]` and validates the turn as a whole.
- **TB board buttons**: no stone placed → "Place only a dead stone" (records own pass locally); own stone placed or passed → "Submit without dead stone"; dead stone placed → "Submit".
- **Live buttons**: the PASS button is reused with a changing label: no stone placed this turn → "Place only a dead stone" (sends own pass `722`); after the own stone is placed or passed → "Skip dead stone" (ends the turn without a dead stone). "Place only a dead stone" is the one wording for this action everywhere (TB board and live room).
- **Dead stone colour**: gray. **Board colour**: `#D7BDE2` (accepted by the user; final, not a placeholder).
- **Live servers**: Tombstone is **not** offered on any existing live server (Main Room 1, Beginners 37, King of the Hill 45, Go 46, nor the Arena, which copies server 1). A new live server **"Experimental"** (id `47`, port `16004`) offers **only** Tombstone (`33`, `34`). It is created and kept in sync at boot (§7.2). The server refuses a table game that the server does not offer, in both directions: Tombstone only on Experimental, nothing else on Experimental (§7.3).
- **Mobile apps never see Experimental**: it is filtered out of every mobile-facing server and room list, and native-socket (app) connections to it are refused (§7.5). The website and the React live room list it and can enter it.
- **No King of the Hill**: no KOTH event rows, no server-45 offering, and `NewGameServlet` rejects TB KOTH invitations for Tombstone (§7.3, D9).
- **Turn-based Tombstone (`83`)** stays available on the website TB pages, with no gating in this work.
- **No beta gating** in this work. No tournaments for this game.

Controller defaults (stated as decisions here; a reviewer may challenge them with evidence):

| # | Decision | Reason |
|---|---|---|
| D1 | P2 may pass on their first turn. No tournament rule, no pro opening rule. | Matches "each placement may be passed independently". The Pente tournament rule (`SimplePenteState.java:256`) is keyed on move-count parity, which does not hold here. |
| D2 | Dead stones are permanent. They are never captured or removed. They block fives and capture patterns for both colours. A dead stone may go on a point vacated by a capture in the same turn. | A cell value that is neither 1 nor 2 already behaves this way in the capture and line scans (`SimplePenteState.java:391-420` compares cells to exact colours). |
| D3 | Only own stones capture. The capture check runs on the own stone. Dead stones never trigger captures. | Running `makeCaptures` on a value-3 stone would compute opponent `3-3=0` and "capture" empty points (see §5.4). |
| D4 | If the own stone wins (five, or reaching 10 captured stones), the game ends at once and there is no dead stone that turn. In TB, a dead stone submitted after a winning own stone is **discarded** (not rejected). | The website JS does not evaluate wins, so the player cannot know the turn is already over. Rejecting would make a player who has just won resubmit. The outcome is identical: a dead stone has no effect on a finished game. This matches the Connect6 precedent, which drops the second stone when the first one wins (`MoveServlet.java:642`). |
| D5 | Live undo removes the whole last turn. | A half-undone turn leaves an orphan own stone and corrupts whose turn it is. |
| D6 | Rated games are 2-game sets with a seat swap, like Pente. K-factor 64. | P1's centre stone is a first-move advantage, as in Pente. So Tombstone is kept out of `single_game` (`ServerTable.java:2841`), `isSingleGameSet` (`GridStateFactory.java:502`) and `k32Game` (`ServerTable.java:3721`). |
| D7 | The live Fischer increment is applied once per turn, not once per placement. | Connect6's same-player branch increments on every stone (`ServerTable.java:2106-2118`), which gives a 2-entry turn twice the bonus of a 1-entry turn. |
| D8 | No AI support (not in `ai_config.xml`, not in the `MMAIPlayer` game list). | Out of scope. Unknown games get no AI. |
| D9 | No King of the Hill (KOTH) events and no KOTH server offering, enforced live by the server-offering guard (§7.3) and in TB by the `NewGameServlet` KOTH reject. See §7.3 for the evidence. | Excluding costs 2 call sites if Tombstone ids live in new arrays, but about 20 if they are added to `LIVE_GAMES`/`TB_GAMES`. The offering rows alone do not stop a table switch, so the guard is needed. |
| D10 | Ids: `TOMBSTONE=33`, `SPEED_TOMBSTONE=34`, `TB_TOMBSTONE=83`. | Next free odd/even live pair after `RENJU=31`/`SPEED_RENJU=32` (`GridStateFactory.java:37-38`). TB = 50 + live (`TB_START=50`, `:61`). `tbGames[(83-51)/2] = tbGames[16]` is the next append slot after `TB_RENJU` at index 15. Live ids must stay below 50 (`isSpeedGame`, `isTurnbasedGame`, `:471-476`). |
| D11 | Tombstone is excluded from pentedb/webdb position stats, search, load and import, and from the legacy game database search. | Those paths decode `next_move` as `0..360` only and drop or garble anything else (`PositionStatsHandler.java:52` `MAX_BOARD_MOVE = 360`). |
| D12 | Live "skip dead stone" is a new dedicated event, not a sentinel move int. | A skip is not stored. Putting a transient int through `DSGMoveTableEvent` would force every consumer of that event to special-case it. |
| D13 | TB turns are stored atomically by a new storer call (`CacheTBStorer.storeTombstoneTurn`), not by two `storeNewMove` calls. | After the first row, `TBGame.getCurrentPlayer` already flips. The second row would then be validated and timed against the wrong player, and the opponent could briefly see a half turn. |
| D14 | Experimental server id `47`, name `"Experimental"`, port `16004`, created at boot by an insert-if-absent with an explicit id. | `dsg_server.id` is `AUTO_INCREMENT` (`schema.sql:239-249`). The local main and replica databases (assumed to be copies of production; production itself was not queried) both hold ids `1..46`, with `max(id) = 46` and `Auto_increment = 47`; the active servers are 1, 37, 45, 46 on ports 16000-16003, and the Arena is in-memory id 0 on 15999 (`DSGContextListener.java:347-352`). Each server opens a TLS listener on its port (`Server.java:110-118`) and a websocket endpoint `/websocketServer/<port>` (`DSGContextListener.java:374`), so the port must be unique among active servers. 16004 is the next free port and is already published by `docker-compose.yml:21`. Speed tournament servers take `max(port) + 1` (`Resources.java:123-130`), so they move to 16005 and up, which compose also publishes (`:22-27`). If production holds another row at id 47, the boot step logs an error and offers nothing (§7.2), rather than taking over that row. |

## 3. Rules

### 3.1 Turn structure

- Turn `t = 0` belongs to P1 and is exactly the centre stone (`180` on 19x19, `GridStateFactory.getCenterMove`). It never has a dead stone. It closes automatically.
- Turn `t >= 1` belongs to P2 when `t` is odd and to P1 when `t` is even. A turn is:
  1. **own placement**: a stone of the mover's colour on an empty point (`0..360`), or an own pass (`722`). Then
  2. **dead placement**: one dead stone on an empty point (`361 + point`), or nothing (dead pass, never stored).
- The dead placement happens **after** the own placement's captures have been applied, so a dead stone may fill a point the own stone just vacated by capturing.

### 3.2 Captures

- These are Pente pair captures, done by the own stone only. If the stone placed at `p` has two opponent stones at `p+d` and `p+2d` and an own stone at `p+3d`, in any of the 8 directions, both opponent stones are removed and the mover's capture count goes up by 2.
- A dead stone at `p+d`, `p+2d` or `p+3d` blocks the pattern, because a dead cell is neither colour.
- Placing a stone between two enemy stones is safe, as in Pente: captures are only checked outward from the placed stone.
- Dead stones are never captured and never removed.

### 3.3 Wins

- **Five**: the own stone completes five or more in a row of its colour (overlines win, as in Pente).
- **Captures**: the mover's capture count reaches 10 stones (5 pairs), as in `SimplePenteState` (`capturesToWin = 10`, `:104`).
- Wins are checked only after an own **stone**, never after a pass or a dead stone. A winning own stone ends the game immediately (D4).

### 3.4 Draws

- **Empty-turn draw**: an empty turn is an own pass (`722`) closed without a dead stone. When 6 consecutive turns are empty (3 rounds by both players), the game ends in a draw (winner `0`). Any non-empty turn resets the counter. Turn 0 is never empty.
- **Full board**: if no empty point remains after an entry, the game ends in a draw. This includes an own stone that fills the last point without winning, because no dead stone is then possible.
- A draw is stored as winner `0`, like the existing draw handling (`ServerTable.java` game-over tail; `CacheTBStorer` `REASON_DRAW`).

### 3.5 Bound on stored entries

Each non-empty turn places at least 1 stone. Placements are at most 361 points plus refills of captured points. Before the game ends, each player has captured at most 8 stones (4 pairs). The final capture can add at most 16 more (8 directions x 2). So placements are at most `361 + 40`. Between non-empty turns there are at most 5 empty turns. So the stored entries are at most `2*401 + 5*402 = 2812`. `TombstoneState.MAX_ENTRIES = 3000` covers this bound. The 720-slot hash arrays in `SimpleGridState` (`:349-350`) do not, so Tombstone needs a capacity constructor (§5.5).

## 4. Move encoding and decoding

| Code | Meaning | Stored? |
|---|---|---|
| `0..360` | own stone on point `code` | yes (turn 0 = `180` only) |
| `361..721` | dead stone on point `code - 361` | yes, always directly after the own entry of the same turn |
| `722` | own-stone pass | yes |
| *(none)* | dead-stone pass | **no** — live: a `DSGTombstoneSkipDeadTableEvent` (§8); TB: the submitted turn has 1 code |
| `723` | archive-only sentinel: `pente_move.next_move` of the last row of a Tombstone game | only in `pente_move` (§9) |

All codes fit the columns: `tb_move.move` is a signed `smallint(5)`, `pente_move.next_move` is an unsigned `smallint(5)` (`schema.sql:884`, `:1045`). The TB sentinels `-1` (undo request) and `-2` (draw offer) stay disjoint from these codes (`MySQLTBGameStorer.java:515-517`).

### 4.1 The collision with 361

Existing code reads `361` in three ways:

1. **Go pass** (`GoState`, `TBGame.passMove = 361` default `TBGame.java:68`, `gameScript.js` `passMove = 361`).
2. **"No next move" sentinel** in `pente_move.next_move` (`MySQLPenteGameStorer.java:509-511`), filtered out on load by `next_move != 361` (`:778`, `:936`).
3. **"PASS" label** for any code `>= gridSize*gridSize` (`AlphaNumericGridCoordinates.getCoordinate`, `mobileGame.jsp:483`, React `GameClass.js` Go/Renju pass handling).

For Tombstone, `361` is a dead stone on point 0 (A19), and `362..722` are further dead stones and the own pass. The rule: **no decoder may interpret a code `>= 361` without first knowing that the game is Tombstone.** Each subsystem branches on its game id with a single helper:

- Java: `GridStateFactory.isTombstone(int game)` is true for `33`, `34`, `83`. Code decoding goes through `TombstoneCodes` (§16.1).
- Website JS: `isTombstoneGame(game)` and `tombstoneDecode(code)` in `tb/gameScript.js`.
- React: `isTombstone(gameId)` in `game/boardGeometry.js` and `decodeTombstone(code)` in `game/tombstoneTurn.js`.

Per path:

- **Go pass detection** never runs for Tombstone. `TBGame.containsDoublePass` runs for every game through `getGoState()` (called unconditionally by `GameResponse.java:153`). With `passMove = 361` it would only fire on two consecutive entries equal to `361`. That cannot happen in Tombstone: point 0 holds at most one dead stone, and two dead entries are never adjacent. No change is needed, but Plan C adds a test that pins this.
- **Archive sentinel**: Tombstone uses `723` instead of `361` (§9).
- **Labels**: Tombstone code paths use `TombstoneCodes.entryLabel` / `tombstoneLabel` and never the generic `>= gridSize^2 → "PASS"` rule.

## 5. State model — `TombstoneState`

### 5.1 Class shape

`org.pente.game.TombstoneState extends GridStateDecorator implements HashCalculator`. It wraps a `SimpleGridState(19, 19, TombstoneState.MAX_ENTRIES)` with `setAllowNonBoardMoves(true)`. It is **not** layered on `SimplePenteState`, because that class derives colour, winner and capture undo from move-count parity: `getWinner = 3 - getCurrentPlayer()` (`SimplePenteState.java:346-348`), undo of captures via `getCurrentPlayer()` (`:484`), and the base stone colour from `getCurrentColor()` parity (`SimpleGridState.java:198-200`). It also does not implement `PenteState`: no server code casts generically to `PenteState`. The casts in `ServerTable.java:550-566, 1222-1314, 1910, 3483-3487` are all guarded by D-Pente, Swap2 or Pente-family ids.

The capture and five-in-a-row logic is a colour-explicit port of `SimplePenteState.capture` and the `SimpleGomokuState` line scan, with overlines allowed.

### 5.2 Internal state (all derived from the entry list, except one flag)

| Field | Meaning |
|---|---|
| `entryPlayer[i]` | player (1/2) who made entry `i` (own stone, own pass or dead stone) |
| `capturesBefore1[i]`, `capturesBefore2[i]` | capture counts before entry `i` (used by `calcHash` and rebuild) |
| `captures[1..2]` | stones captured by each player |
| `lastCapturedPoints` (`int[]`, transient) + `lastCapturedCount` | points of the stones removed by the current own entry, set by the capture step **before** `updateHash` runs and read only by `calcHash` (§6.3). It is empty for a pass or a dead entry. Rebuild (§5.6) recomputes it per entry. |
| `turnCount` | number of own entries (= turns started, including turn 0) |
| `awaitingDead` | the current turn has its own entry and is still open for a dead stone |
| `consecutiveEmptyTurns` | closed empty turns in a row |
| `winner` / `gameOver` | set at a winning own stone, or 0 + true on a draw |

Board cells: `0` empty, `1`/`2` stones, `TombstoneCodes.DEAD_CELL = 3` dead stone.

### 5.3 Whose turn

- The player of turn `t` is `t == 0 ? 1 : (t % 2 == 1 ? 2 : 1)`, which is the same as `(t % 2) + 1` for `t >= 1` and 1 for `t = 0`.
- `getCurrentPlayer()`: if `awaitingDead`, the player of the current turn; else the player of the next turn, `turnCount % 2 + 1` (after the centre stone `turnCount = 1`, so P2).
- For **complete-turn histories** (TB, archive) this reduces to the pure function `TombstoneCodes.currentPlayer(codes, n) = countOwnEntries % 2 + 1`. Every turn stores exactly one own entry, so this works without replay. `TBGame.getCurrentPlayer` uses it (§10.1).
- `getColor(int i)` returns `entryPlayer[i]` for `i < getNumMoves()` and `0` otherwise. The prototype in `GridStateFactory.gridStates[]` has no history, so `GridStateFactory.getColor(moveNum, game)` has no meaning for Tombstone. Its only callers are swing/awt (`MoveIconRenderer`, `SGFGameFormat`), which are out of scope.

### 5.4 Entry grammar

`addMove(int code)` is the **replay primitive** used by `getInstance(MoveData)`, the archive storer and the TB storer. It is lenient about turn closure, so a flat history without skip markers can be replayed:

1. `numMoves == 0`: the code must be an own stone. It is placed for P1, and turn 0 closes at once.
2. Own entry (`0..360` or `722`) while `awaitingDead`: first **implicitly close** the open turn (exactly as `skipDeadStone()` would, including the empty-turn count), then continue with step 3.
3. Own entry while not awaiting: start turn `turnCount` with player `p`. For a stone, set the cell to `p`, apply captures, then check five and captures; a win sets `gameOver`/`winner` and the turn closes. For `722`, the board is unchanged. If the game is not over and no empty point remains, it is a draw and the turn closes. Otherwise `awaitingDead = true`.
4. Dead entry (`361..721`): requires `awaitingDead` and an empty target point. Set the cell to `3`. No captures, no win check. The turn closes as non-empty (`consecutiveEmptyTurns = 0`). Full-board check (draw).
5. Anything else (a dead entry while not awaiting, an occupied point, a code `> 722` or `< 0`, any entry after game over) throws `IllegalArgumentException`.

Order inside one `addMove`, because the inner `SimpleGridState.addMove` sets the cell by move-count **parity** (`setPosition(move, getCurrentColor())`, `SimpleGridState.java:179-193`), and adds codes `>= 361` as non-board moves without touching any cell: (a) validate; (b) `gridState.addMove(code)` (appends the entry; for an own stone it writes the parity colour); (c) for an own stone, overwrite the cell with the real player `p`; for a dead entry, set the decoded cell to `3` itself; (d) apply captures for an own stone and fill `lastCapturedPoints`; (e) set `entryPlayer`, `capturesBefore*`; (f) `updateHash(this)`; (g) win, draw and turn closure. `calcHash` ignores the `p` argument, which `updateHash` derives from `getPosition(move)` (`:577-581`) and would be wrong for a captured-into or non-board code.

`getInstance(MoveData)` replays every entry and then **finishes the history**: if the replay ends with `awaitingDead`, it closes the turn as `skipDeadStone()` would. TB histories and the histories of games still in play contain only complete turns (§10.2 closes every TB turn), so this closure is exact there. That includes the 6th empty turn: a TB history ending in the sixth `[722]` replays as a draw.

Live **archives** are the exception. A live game that ends by resign, timeout or force-resign while a dead stone is pending (`handleTimeUp`, `ServerTable.java:3027`, ends the game in any turn phase) stores a trailing own entry whose turn never closed, because `getGameData` copies every entry (`:3505`). Replay closes it as a skip. If that entry is a `722` that would make the 6th empty turn, the replayed state reports a draw although the real result is a resign or timeout. This is harmless because the archive writer takes the winner from `data.getWinner()` (`MySQLPenteGameStorer.java:518`), not from the state. **Rule: no caller may take the result (winner, game over, draw) of a finished game from a replayed `TombstoneState`.** The stored winner is authoritative. Replayed states are only used for the board, hashes and whose turn it is.

Live-only methods:

- `skipDeadStone()`: requires `awaitingDead` and throws `IllegalStateException` otherwise. Closes the turn: empty if its own entry was `722`, else non-empty. Then `consecutiveEmptyTurns >= TombstoneState.EMPTY_TURNS_FOR_DRAW (6)` makes it a draw.
- `isAwaitingDeadStone()`.
- `isTrailingTurnClosedWithoutDead()`: `!awaitingDead && numMoves > 1 && isOwnEntry(lastCode)`. The live server uses it to tell a rejoining client that the trailing turn was skipped (§8.5).

### 5.5 Validity (`isValidMove(code, player)`) — strict, used by live and by the TB turn validator

- `false` if game over, or `player != getCurrentPlayer()`.
- `numMoves == 0`: only `code == 180` (centre). An own pass is not allowed on turn 0.
- `awaitingDead`: only a dead code whose point is empty.
- otherwise: an own stone on an empty point, or `722`.

Capacity: `SimpleGridState` gets a new constructor `SimpleGridState(int boardSizeX, int boardSizeY, int maxEntries)` that sizes `hashes[maxEntries][9]` and `rots[maxEntries]`. The existing constructors keep 720, so other games are unchanged. `TombstoneState` uses `MAX_ENTRIES = 3000` (§3.5).

### 5.6 Undo

- `canPlayerUndo(int seat)`: `!gameOver && !awaitingDead && turnCount > 1 && player(last turn) == seat`. So the player who completed the last turn may ask, and no one may undo the centre turn or ask while the opponent is mid-turn.
- `undoTurn()`: removes the last turn's entries (2 if the last entry is a dead code, else 1) and returns that count. It then **rebuilds** the state by clearing the inner `SimpleGridState` and replaying the remaining entries through `addMove`, followed by the history finish (§5.4). The rebuild makes captures, hashes and the empty-turn counter exact without separate undo bookkeeping. Cost: at most 3000 entries, negligible.
- `undoMove()` (the `GridState` contract) removes one entry with the same rebuild, but does **not** finish the history: removing a dead entry leaves the turn awaiting. Only `undoTurn()` is used by the servers.

### 5.7 Winner and game over

`isGameOver()` returns `gameOver`. `getWinner()` returns the stored winner (1/2), or `0` for a draw. It never uses `3 - getCurrentPlayer()`. `getNumCaptures(int player)` returns `captures[player]` for capture displays.

## 6. Zobrist hashing

### 6.1 Verified counts (parsed from `ZobristUtil.java`)

| Row | Lines | Values | Use today |
|---|---|---|---|
| `rand[0]` | 13-258 | 723 | P1 stone keys (points 0..360), Go pass 361-362, rest unused |
| `rand[1]` | 259-503 | 723 | P2 stone keys |
| `rand[2]` | 504-748 | 720 | P1 capture-count keys (Go uses more) |
| `rand[3]` | 749-994 | 721 | P2 capture-count keys |

Total 2887 values, of which 2868 are distinct. Known defects, which must be **left alone** because changing any value invalidates stored `pente_move.hash_key`/webdb hashes: duplicate pairs inside `rand[0][361..364]`; `rand[0][365..366] == rand[1][361..362]`; `rand[3][0..14] == rand[0][0..14]`. Tombstone does not use any index `>= 361` of rows 0/1.

### 6.2 New row

- Append a fifth row `rand[4]` with exactly **361** values, plus a constant `ZobristUtil.TOMBSTONE_DEAD_ROW = 4`. The values are generated once with `java.security.SecureRandom.nextLong()`, rejecting `0`, any duplicate within the row, and any value already present in rows 0-3. They are committed as literals. The generator is a throwaway (jshell) snippet in the plan and is not committed.
- After the change: row lengths `[723, 723, 720, 721, 361]`, 3248 values in total, and the 361 new values are all distinct from each other and from the 2868 distinct existing values.

### 6.3 `TombstoneState.calcHash(cHash, p, move, rot)`

`SimpleGridState.updateHash` copies the previous slot and calls `calcHash` once per rotation `rot` in `0..7`. It passes `p = 0` for out-of-bounds codes (`SimpleGridState.java:578-581`), so `calcHash` must decode the code itself and never index `rand[p-1]` for a non-stone:

- `722` (own pass): return `cHash` unchanged, so the slot equals the previous slot (hash = board only).
- dead `361+q`: `cHash ^ rand[4][rotateMove(q, rot)]`.
- own stone `q` by player `c` (= `entryPlayer[last]`): `cHash ^ rand[c-1][rotateMove(q, rot)]`; for each stone captured by this entry (`lastCapturedPoints[0..lastCapturedCount-1]`, §5.2), `^ rand[(3-c)-1][rotateMove(cap, rot)]`; then the capture-count delta for player `c`'s row (`rand[2]` for P1, `rand[3]` for P2): XOR out `rand[row][capturesBefore_c]` if it is `> 0`, and XOR in `rand[row][captures_c]` if it is `> 0`.

Dead points are rotated with `rotateMove(q, rot)` on the **decoded point**. `rotateMove` returns out-of-bounds codes unrotated (`SimpleGridState.java:401-402`), which would break the canonical rotation (`hashes[i][8]` = max over 8 rotations) and the stored `pente_move.rotation`.

The result is a pure function of the position: own stones, dead stones and capture counts. Two move orders that reach the same position give the same hash, and the 8 symmetric images share the canonical hash. Unlike `SimplePenteState.calcHash` (`:442-447`), which re-XORs the capture-count terms on every move, the capture-count terms here are applied as deltas. The known `rand[3][k] == rand[0][k]` (`k <= 14`) collision also affects Tombstone (a P2 capture count `k` and a P1 stone on point `k`). It is accepted because Tombstone positions are excluded from position stats (D11), and `pente_move`'s primary key `(gid, hash_key, move_num)` tolerates hash collisions.

## 7. Game registration

### 7.1 `GridStateFactory` (Plan A)

- Constants: `TOMBSTONE = 33`, `SPEED_TOMBSTONE = TOMBSTONE + 1`, `TB_TOMBSTONE = TB_START + TOMBSTONE` (= 83).
- Games: `TOMBSTONE_GAME = new Game(TOMBSTONE, "Tombstone Pente", false)`, `SPEED_TOMBSTONE_GAME = new Game(SPEED_TOMBSTONE, "Speed Tombstone Pente", true)`, `TB_TOMBSTONE_GAME = new Game(TB_TOMBSTONE, "Tombstone Pente", false)`. The TB name equals the live name, as for every other game, because finished TB games are archived under the live name/id. `TBGame.convertToGameData` sets the name via `getGameName` (`TBGame.java:706`), and `MySQLPenteGameStorer` resolves the id by name (`:479`). `getGameId(name)` scans `allGames` before `tbGames` (`GridStateFactory.java:408-420`), so `"Tombstone Pente"` resolves to 33. The `viewLiveGame*.jsp` pages compute the TB id as `getGameId(name) + 50` (`viewLiveGame.jsp:42`).
- `allGames[]` is positional: index == id for live ids. Insert `TOMBSTONE_GAME, SPEED_TOMBSTONE_GAME` directly after `SPEED_RENJU_GAME` (indexes 33, 34) and append `TB_TOMBSTONE_GAME` after `TB_RENJU_GAME`. `gridStates[]` is sized `getNumGames()+1` and filled by index (`:237-243`), so `gridStates[33]`/`[34]` become real Tombstone prototypes. The TB entries in `allGames` shift by two, which is harmless: TB lookups go through `tbGames` (`:386`, `:394`), and `getGameId` matches by name.
- `tbGames[]`: append `TB_TOMBSTONE_GAME` **last** (index 16 = `(83-51)/2`), so `tbGridStates[16]` is the Tombstone prototype.
- `normalGames[]` += `TOMBSTONE_GAME`; `speedGames[]` += `SPEED_TOMBSTONE_GAME`; `displaygames[]` += `TOMBSTONE_GAME`, `new Game(TB_TOMBSTONE, "Turn-based Tombstone Pente", false)` and `SPEED_TOMBSTONE_GAME`, in the same three groups as Renju (`:171`, `:187`, `:203`).
- `createGridState(int game, int x, int y)`: `case TOMBSTONE: case SPEED_TOMBSTONE: case TB_TOMBSTONE: return new TombstoneState();` (always 19x19).
- `isValidGame` upper bound becomes `SPEED_TOMBSTONE` (`:400-401`; it has no production callers). `getMaxGameId()` returns `TB_TOMBSTONE` (`:426-428`; the only caller is `NewGameServlet.java:197`).
- New `public static boolean isTombstone(int game)` for `33`, `34`, `83`.
- New arrays: `NON_KOTH_LIVE_GAMES = {TOMBSTONE, SPEED_TOMBSTONE}` and `NON_KOTH_TB_GAMES = {TB_TOMBSTONE}`. `LIVE_GAMES`, `TB_GAMES` and `ALL_GAMES` stay **unchanged** (§7.3).
- `firstMoveCanBeOffCenter`: unchanged (false for Tombstone), so the archive load prepends the centre (`MySQLPenteGameStorer.java:784-786`) and webdb import would accept it. Webdb import is rejected separately (§9.4).
- `isSingleGameSet`: unchanged (false) (D6).

### 7.2 Boot registration (Plan B)

- `MySQLGameVenueStorer.registerAllGames` (`:686-707`) adds a `LIVE_EVENT` row for each id in `NON_KOTH_LIVE_GAMES` and a `TB_EVENT` row for each id in `NON_KOTH_TB_GAMES`, with **no** `KOTH_EVENT` rows. That gives 3 `game_event` rows for Tombstone, where `registerAllGames` creates 6 for every other game. `game_event.game` is a `tinyint unsigned`, so 33, 34 and 83 fit.
- `DSGContextListener` (the best-effort `try` at `:127-149`): servers 1 (Main Room), 37 (Beginners), 45 (KOTH) and 46 (Go) keep exactly today's offerings (`:141-146`). None of them gets a Tombstone id. The Arena server copies server 1's events (`setupArenaServers`, `:347-358`), so it has no Tombstone either.
- **Experimental server (new, Plan B).** One source of truth, `org.pente.gameServer.core.ExperimentalServer`: `ID = 47`, `NAME = "Experimental"`, `PORT = 16004`, `GAMES = {TOMBSTONE, SPEED_TOMBSTONE}` (D14). Inside the same `try`, after `registerAllGames(2)` (`:128`, so the Tombstone `game_event` rows exist) and after the existing `addServerGames` calls:
  1. `MySQLServerStorer.ensureServer(dbHandler, ExperimentalServer.ID, ExperimentalServer.NAME, ExperimentalServer.PORT)`: `insert ignore into dsg_server (id, name, port, tournament, active, creation_date, last_mod_date, private) values (?, ?, ?, 'N', 'Y', sysdate(), sysdate(), 'N')`, then `select name from dsg_server where id = ?`. It returns `true` only when the stored name equals `NAME`. An existing row is never modified, so an admin who deactivates the server (`active = 'N'`, `admin/modifyServers.jsp` → `Resources.removeServer`) keeps it deactivated across reboots.
  2. Only if that returned `true`: `MySQLServerStorer.addServerGames(dbHandler, ExperimentalServer.ID, 2, ExperimentalServer.GAMES, MySQLGameVenueStorer.LIVE_EVENT)` (insert-if-absent, `MySQLServerStorer.java:86-123`). If it returned `false` (id 47 belongs to another server), log an error and offer nothing.
  Both run before `setupLiveGameServers()` (`:329`), which reads active, non-tournament rows (`MySQLServerStorer.getActiveServers`, `:153-251`) and starts each as a `Server` (`DSGContextListener.java:385-398`). So on the first boot Experimental starts like any other live server, with its TLS listener and its websocket endpoint `/websocketServer/16004`.
- Removing a Tombstone row from another server is not part of this boot step: on this branch no server has ever been offered Tombstone, so there is nothing to remove.

### 7.3 Why KOTH is excluded via separate arrays (D9 evidence)

`LIVE_GAMES`/`TB_GAMES`/`ALL_GAMES` are used only for boot registration, server offerings and KOTH. The KOTH sites are `CacheKOTHStorer.java:150,164,170,294,330`, `KotHServlet.java:68`, `IndexResponse.java:429-430`, `stairs.jsp:133,137,377,402,430`, `kothBox.jsp:57,66` and `mobile/index.jsp:367,377,388`. KOTH code assumes every id in those arrays has a KOTH event. `MySQLKOTHStorer.getEventId` returns `-1` (not `0`) when the row is missing (`:31-57`). `CacheKOTHStorer.loadHill` treats any non-zero id as real and invents an empty `Hill` for TB ids (`:96-99`).

So adding Tombstone to those arrays without KOTH events would create junk hills, and excluding it inside them would mean editing about 20 sites. Separate arrays need 2 call sites (§7.2).

The offering rows alone do not keep a game off a server. A table's game is chosen at three sites, none of which checks `serverData.getGameEvents()`:

1. `ServerTable.handleChangeState` resolves any registered id (`GridStateFactory.getGame(changeStateEvent.getGame())`, `ServerTable.java:925`) and sets `game = newGame` (`:938-941`).
2. `ServerTable.resetTable` (`:255`, called when a table is created, `:211`) restores the creator's per-player `"gameState"` preference, `game = GridStateFactory.getGame(e.getGame())` (`:281-288`), or else defaults to Pente 10/5 (`:298-302`). The preference is saved per player, not per server (`:975-977`), so a Tombstone setup saved on Experimental would open a Tombstone table on server 1, and every new table on Experimental would default to Pente.
3. `ArenaServerTable.resetTable` (`ArenaServerTable.java:343-364`) takes the game from the client's `DSGArenaCreateTableEvent` (`:354`).

The React selectors list every `STANDARD_GAME_ID` on every server (`SettingsModal.js:128`, `CreateArenaTableModal.js:126`). At game end, `getGameData` calls `getGameEvent(game.getId()).getName()` (`:3407`), and `getGameEvent` returns `null` when the server has no event row for the id (`:3373-3381`), so the game end throws a NullPointerException. On the KOTH server, `kothRanking.recordResult(game, …)` (`:3841-3842`) would also create the junk hill D9 is meant to prevent.

**Server-offering rule (Plan B), both directions.** One pure predicate, `ExperimentalServer.gameAllowed(ServerData sd, int gameId)`: `true` for a tournament server (its game comes from the tourney, `:266-271`, and changes are already refused at `:884-885`) and for a server with no offerings at all (a half-migrated database; keeps today's behaviour); otherwise `true` iff some `sd.getGameEvents()` entry has `getGame() == gameId`. Because Tombstone is offered only by Experimental and Experimental offers only Tombstone, this one rule gives both directions: Tombstone only on Experimental, nothing else on Experimental. It applies to every game on every server, which also closes the existing gap for non-Go games on the Go server (46). The three sites:

- **Live game change**: an `else if` in the `handleChangeState` validation chain, after the tournament check (`:884-885`) and before the final `else {` (`:886`), so nothing on the table has been applied yet. It resolves the target id with the same speed/normal conversion as `:927-935`, computed from the event (`e.getTimed() && Game.isSpeedGame(e.getInitialMinutes(), e.getIncrementalSeconds())`). If `!gameAllowed`, `status = DSGTableErrorEvent.UNKNOWN`. The existing error tail (`:962-972`) then sends `DSGChangeStateTableErrorEvent` with the unchanged settings, so the client resets itself.
- **Table creation (`ServerTable.resetTable`)**: a saved preference whose converted game is not allowed is ignored (the branch `break`s before assigning any field, so the no-preference defaults apply). Then, if the default game (Pente) is not allowed, the game becomes `ExperimentalServer.defaultGame(sd)`: the lowest non-speed offered id (Tombstone `33` on Experimental, Go `19` on the Go server), or Pente when nothing is offered. The time controls stay at the 10/5 default, which is not a speed setting.
- **Arena table creation (`ArenaServerTable.resetTable`)**: after the speed/normal conversion (`:359-364`), if the game is not allowed, use `defaultGame(sd)` converted with the same `speed` flag. The Arena copies server 1, so this keeps a crafted Tombstone create request off the Arena.
- **React selector (Plan D)**: the Settings and Arena pickers list only the ids the current server offers (§13.1). This is cosmetic; the server rule is the enforcement.
- **TB KOTH**: `NewGameServlet` must reject `koth` invitations for Tombstone (`:229` block) with the error "King of the Hill is not available for Tombstone Pente.", because it calls `kothStorer.loadHill(game)` directly. `tb/newKotH.jsp` lists `getTbGames()` (`:146`), so Plan C also skips Tombstone ids in that list. `KotHServlet` already rejects ids outside `TB_GAMES` (`:66-73`).

**KOTH summary (user decision).** Tombstone has no King of the Hill presence at all: `registerAllGames` writes no `KOTH_EVENT` row for 33, 34 or 83 (§7.2); server 45 is offered `LIVE_GAMES` only (`DSGContextListener.java:146`), so the server-offering rule refuses Tombstone there; `NewGameServlet` rejects TB KOTH for Tombstone and `tb/newKotH.jsp` does not list it; `KotHServlet` already rejects ids outside `TB_GAMES`; and `LIVE_GAMES`/`TB_GAMES`/`ALL_GAMES` stay unchanged, so no KOTH page or storer sees a Tombstone id.

### 7.4 Other registrations and exclusions

- **Tournaments**: `admin/newTourney.jsp` lists `getAllGames()` (`:117`) and parses the posted `game` (`:24-29`). Plan C hides Tombstone ids from the list and rejects them on post with "Tournaments are not available for Tombstone Pente."
- **TB new-game pages**: `tb/new.jsp:255` and `tb/new2.jsp:120` loop `getTbGames()`, so Tombstone appears automatically. `newAIgame.jsp` only offers id 51 (`:138-141`). `newKotH.jsp` hides Tombstone, and `NewGameServlet` blocks it server-side (§7.3).
- **Rating and stat pages** (`leaderboard.jsp`, `statsMain.jsp`, `playerstatsbox.jsp`, mobile rating lists via `getNormalGames`/`getSpeedGames`/`getTbGames`) include Tombstone, like any game.
- **Mobile apps** (out of scope) already skip unknown game ids, so Tombstone rows in the mobile index are ignored by current app builds. Live Tombstone never reaches them because the Experimental server is hidden from and closed to them (§7.5).

### 7.5 Experimental server and mobile clients (Plan B)

`ExperimentalServer` (`org.pente.gameServer.core`) holds the identity and the pure helpers:

```java
public final class ExperimentalServer {
    public static final int ID = 47;
    public static final String NAME = "Experimental";
    public static final int PORT = 16004;
    public static final int[] GAMES = {GridStateFactory.TOMBSTONE, GridStateFactory.SPEED_TOMBSTONE};
    public static boolean isExperimental(ServerData sd);      // sd != null && sd.getServerId() == ID && NAME.equals(sd.getName())
    public static boolean isExperimentalRoom(String roomName); // NAME.equals(roomName); who's-online rooms are named after servers (WhosOnline.java:42, :126)
    public static boolean acceptsNativeSocket(ServerData sd);  // !isExperimental(sd)
    public static boolean gameAllowed(ServerData sd, int gameId); // §7.3
    public static Game defaultGame(ServerData sd);             // §7.3: lowest non-speed offered id, else PENTE_GAME
}
```

`isExperimental` checks the name as well as the id, so a foreign row at id 47 (D14) is neither filtered nor restricted.

**Mobile-facing server and room lists, all filtered server-side.** Each skips the Experimental server (by `isExperimental`) or its room (by `isExperimentalRoom`):

| Endpoint | Builder | App caller |
|---|---|---|
| `mobile/json/liveServers.jsp` (`:18-19`) | `LiveServersResponse.build` (`LiveServersResponse.java:57-70`) | Android `LobbyActivity.java:194` |
| `mobile/liveServers.jsp` (loop `:28-51`) | inline | iOS `LobbyViewController.swift:92-94` (`?iPhone`) |
| `mobile/json/whosonlineandlive.jsp` (`:18-32`) | `WhosonlineAndLiveResponse.build` (`WhosonlineAndLiveResponse.java:51-57`) | Android `OkHttpPenteApi.java:124` |
| `mobile/whosonlineandlive.jsp` (loop `:41-51`) | inline | iOS `GamesTableViewController.m:4104-4108` |

The filters go inside the two `build` methods (so they are unit-testable) and inside the two text JSP loops. Dropping the Experimental room means a player who is only on Experimental is not listed in the apps' who's-online view; that is accepted.

Not filtered, with the reason:

- `mobile/whosonline.jsp` (`:27-30`) and `mobile/json/whosonline.jsp` (`:19-23`) read only the `"Mobile"` room, never a server room.
- `mobile/index.jsp` (`:60-78`) and `mobile/json/index.jsp` (`:49-68`) only count live players into a number; they list no server.
- `/gameServer/activeServers` (`ActiveServersServlet.java:38-55`) is the React room's server list (`react_live_game_room/src/Pages/Servers.js:40`) and the legacy applet's. The current apps do not call it (the only iOS reference is commented out, `SocialViewController.swift:233`). It must keep listing Experimental.
- Website pages (`play.jsp:129-134`, `index2.jsp:294-298`, `playTemplate.jspf:21-26`) and admin pages keep listing it.

**Refusing app connections.** The code does distinguish app connections by transport. Both apps open a raw TLS socket to the server's port: Android `SSLSocketFactory.getDefault().createSocket()` (`penteLive-Android/app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java:130-132`), iOS `NWConnection` (`penteLive-iOS/test1/PenteLiveSocket.swift:45`). That socket is accepted by `Server`'s listener thread and handed to `Server.addPlayerSocket` (`Server.java:153-165`, `:230-249`). The React room connects over the websocket endpoint (`actionTypes.js:57` → `WebSocketConfigurator.addSession` → `Server.addPlayerWebSocketSession`, `Server.java:210-227`). So `addPlayerSocket` starts with: if `!ExperimentalServer.acceptsNativeSocket(serverData)`, log one info line, close the socket and return, before any `ServerPlayer` is created. The listener itself stays open, because the `Server` constructor returns early, before the main room and the AI controller are built, when it cannot listen (`Server.java:116-123`). The legacy Java applet also uses the socket path; it is out of scope (§18) and is refused too. A phone browser that opens the React room uses the websocket and is treated as the website.

## 8. Live protocol (Plan B server, Plan D client)

### 8.1 Messages

Each placement is its own message. A 2-code `moves` array is never sent live, because the React reducer would read it as a bulk replay (`redux_reducers/utils.js:206`).

| Action | Client → server | Server → table (broadcast) |
|---|---|---|
| own stone on point `q` | `{"dsgMoveTableEvent":{"move":q,"moves":[q],"player":P,"table":T,"time":0}}` | `DSGMoveTableEvent(player, table, q)` (existing) |
| own pass ("Place only a dead stone") | same, with `move = 722` | `DSGMoveTableEvent(player, table, 722)` |
| dead stone on point `q` | same, with `move = 361 + q` | `DSGMoveTableEvent(player, table, 361+q)` |
| skip dead stone ("Skip dead stone") | `{"dsgTombstoneSkipDeadTableEvent":{"player":P,"table":T,"time":0}}` | `{"dsgTombstoneSkipDeadTableEvent":{"player":P,"table":T,"silent":false,...}}` |
| skip rejected | — | `{"dsgTombstoneSkipDeadTableErrorEvent":{"player":P,"table":T,"error":E,...}}` to the sender only |
| move rejected | — | existing `DSGMoveTableErrorEvent` (`NOT_TURN = 12`, `INVALID_MOVE = 13`) |

New server classes, following the standard event recipe (class in `gameServer/event`, nullable field plus getter/setter in `DSGEventWrapper`, case in the `SynchronizedServerTable` dispatch switch next to `DSGSwap2PassTableEvent` at `:177`):

- `DSGTombstoneSkipDeadTableEvent extends AbstractDSGTableEvent`: field `boolean silent` (default false); constructors `()` and `(String player, int table, boolean silent)`; `isSilent()`. Modelled on `DSGSwap2PassTableEvent`.
- `DSGTombstoneSkipDeadTableErrorEvent extends AbstractDSGTableErrorEvent`: constructor `(String player, int table, int error)`. Modelled on `DSGRenjuDrawTableErrorEvent`.
- `DSGTableErrorEvent.NOT_AWAITING_DEAD_STONE = 26` (25 `NO_DRAW_OFFERED` is the last used code, `DSGTableErrorEvent.java:29`).

### 8.2 Server validation

`ServerTable.handleMove` keeps its generic checks (`:2016-2036`): in table, seated, game in progress, `getCurrentPlayer() == seat` (`NOT_TURN`), `isValidMove(move, seat)` (`INVALID_MOVE`). `TombstoneState.isValidMove` (§5.5) makes these checks phase-aware:

- In the own phase only `0..360` (empty) or `722` is valid.
- In the dead phase only the same player may send, and only `361..721` with an empty point. Own codes are rejected, so a client cannot play two own entries in one turn.
- A `drawOffer` on a Tombstone move is rejected by the existing Renju-only gate (`:2035`).

`ServerTable.handleTombstoneSkipDead(DSGTombstoneSkipDeadTableEvent e)` checks, in order, with the same state checks as `handleMove` (`ServerTable.java:2019-2030`): not in table → `NOT_IN_TABLE (2)`; not seated → `NOT_SITTING (6)`; `state == GAME_WAITING_FOR_PLAYER_TO_RETURN` → `GAME_WAITING_FOR_PLAYER_TO_RETURN (5)`; `state == NO_GAME_IN_PROGRESS` or `state == WAIT_GAME_TWO_OF_SET` → `NO_GAME_IN_PROGRESS (4)`; then `!(gridState instanceof TombstoneState)` → `INVALID_MOVE`, `getCurrentPlayer() != seat` → `NOT_TURN`, `!isAwaitingDeadStone()` → `NOT_AWAITING_DEAD_STONE`. On success it runs the post-commit tail (§8.3). Errors go to the sender as `DSGTombstoneSkipDeadTableErrorEvent`.

### 8.3 Timers and `moveTimes`

Invariant: **`moveTimes` has exactly one entry per stored entry.** The archive writer reads `moveTimes.get(i)` by entry index (`MySQLPenteGameStorer.java:521-522`), and `ServerTable.getGameData` copies `gridState.getMove(i)` for every `i` (`:3505-3507`).

- **Own entry** (the player does not change, the turn stays open): a Tombstone arm in the same-player branch (`ServerTable.java:2106`) appends the `moveTimes` snapshot **only**. No `stop`, `reset`, `increment` or `go`.
- **Dead entry** (closes the turn, the player changes): the existing changed-player branch (`:2063-2105`) runs: stop the mover, increment once (or reset when `initialMinutes == 0`), snapshot, and later `timers[new].go()`.
- **Own entry that ends the game** (win or full board): the existing game-over tail.
- **Skip**: no new `moveTimes` entry, because nothing is stored. The handler copies the changed-player accounting: `timers[mover].stop()`; if `shouldTimerRun()`, then `reset()` when `initialMinutes == 0`, else `increment(incrementalSeconds)` plus `incrementMillis(ping)`. It then **replaces** `moveTimes.getLast()` (the snapshot taken at the turn's own entry, before the increment) with `new Time(timers[mover].getMinutes(), timers[mover].getSeconds())`, the post-increment value, as the changed-player branch takes its snapshot after the increment (`ServerTable.java:2089-2105`). The count does not change, so the invariant holds. Without this, `pente_move.seconds_left` (`MySQLPenteGameStorer.java:521-522`) would miss the increment of every own-only and pass-only turn, and a live undo would restore a pre-increment time (§8.4). Then `broadcastTable(skip event)`, `broadCastPlayerTimer(mover)`, the implicit decline of a pending undo request (as in `handleMove`), and `activityLogger.updateGameState`. Then either `gameOver(true, …)` when the skip produced the empty-turn draw, or `timers[next].go()`. `broadcastRenjuFifthMove` (`:1629`) is the precedent for such a post-commit tail outside `handleMove`.
- Centre stone: unchanged (`startGame` → `handleMove(p1, 180)`; `shouldTimerRun()` is false at 1 entry, `:1975-1976`).
- The post-move `timers[newCurrentPlayer].go()` guard (`oldCurrent != newCurrent || initialMinutes == 0`) must not re-`go()` mid-turn for Tombstone. Tombstone uses `oldCurrent != newCurrent` only.

A player who times out while a dead stone is pending loses on time, as today (`handleTimeUp`, `:3027`).

### 8.4 Undo (live)

- `handleUndoRequest` / `handleUndoReply` keep their flow. `canPlayerUndo` is the Tombstone version (§5.6).
- On an accepted undo for Tombstone: `int k = ((TombstoneState) gridState).undoTurn();`, then remove **k** `moveTimes` entries instead of one (`:2273-2275` removes one). The existing clock restore runs unchanged: it sets the accepting player's clock from `moveTimes.getLast()` (`:2297-2298`), which after the removal is the last snapshot of that player's previous turn. That snapshot is post-increment for every turn shape, because a skip replaces the own-entry snapshot (§8.3). `oldCurrent != newCurrent` always holds for a Tombstone undo, because the turn returns to the player who made it.
- Broadcast `DSGUndoReplyTableEvent(accepted=true)` as today. Clients remove the whole last turn (§13.6).
- A pending undo request is implicitly declined by any own or dead entry, or by a skip, from the opponent (existing `handleMove` behaviour, also added to the skip tail).

### 8.5 Rejoin

The move list cannot tell "own entry, dead stone still pending" apart from "own entry, dead stone skipped". After `sendMoves(player)` in the rejoin path (`ServerTable.java:610`; `sendMoves` is at `:646-651`), when `gridState instanceof TombstoneState && ((TombstoneState) gridState).isTrailingTurnClosedWithoutDead()`, the server routes `new DSGTombstoneSkipDeadTableEvent(playingPlayers[lastEntryPlayer].getName(), tableNum, true)` (silent) to the joining player only. The client's default after a bulk list (§13.3) is "trailing own entry beyond the centre = dead stone pending", and the silent skip corrects it. The event comes after the bulk list because the bulk list resets client state.

### 8.6 Logging (blocker)

`DSGMoveTableEvent.toString()` formats codes with a static `AlphaNumericGridCoordinates(19, 19)` (`DSGMoveTableEvent.java:8`, `:63-75`). For codes `362..379` this gives `y = -1` with `x != 0`, and for codes `>= 380` (including `722`) it gives `y < -1`. Both index `numbers[y]` and throw an ArrayIndexOutOfBoundsException (AIOOBE). The event is logged at `SynchronizedServerTable.java:117` (`log4j.info(psid() + "in: " + dsgEvent)`, **outside** the try) and again at `LoggingDSGEventToPlayerRouter.java:60` ("out:"). So the first dead stone or own pass on the wire would throw before it is handled.

Fix: `DSGMoveTableEvent` formats each code with a new static `formatMoveForLog(int m)`: `0..360` → coordinate; anything else → `"#" + m`. This is log-only. Go's `361` then logs as `#361` instead of `PASS`, which is accepted.

`ActivityTableData.getMoves()` (`:41-51`) has the same flaw but no callers (a grep of `java/` and `httpdocs/` finds none). It switches to the same helper defensively.

### 8.7 Unchanged live paths

- Auto-centre at game start (`ServerTable.java:1945-1956`): Tombstone is not in the exclusion list, so P1's centre stone is placed through `handleMove`.
- Tournament-rule setter (`:1900-1910`): Tombstone is not in the Pente list, so it has no tournament rule.
- Game change on a table (`:921-941`) resolves any registered id. With Tombstone registered, any client can switch a table to it on the only server that offers it, Experimental (47). This is intended for this branch (no beta gating, see §15). On every other server, and for any non-Tombstone game on Experimental, the server-offering rule in §7.3 rejects the change.
- AI tables: the AI is never offered (D8). If a computer is somehow seated, `MMAIPlayer` does not know the game and does not play.

## 9. Archive storage (Plan B)

### 9.1 Write (`MySQLPenteGameStorer.storeGame`, `:476-529`)

- The id comes from `getGameId(data.getGame())` (`:479`) → 33 or 34 (TB games are archived under the live name, §7.1). The state comes from `createGridState(game, data)` → `TombstoneState.getInstance`, which appends exactly **one inner entry per stored code**. So `state.getNumMoves() == data.getNumMoves()`, and `state.getHash(i)`, `data.getMove(i+1)` and `moveTimes.get(i)` stay aligned (critic finding: the loop at `:497-529` depends on this).
- `next_move`: rows `0..n-2` store `data.getMove(i+1)` (any code `0..722`). The last row stores the sentinel from `TombstoneCodes.archiveNoNextMove(game)`: **`723` for Tombstone ids**, `361` otherwise (replacing the literal at `:509-511`). `723` is outside every Tombstone code and fits `smallint unsigned`.
- `hash_key` / `rotation`: from `TombstoneState` (§6.3). A pass row repeats the previous row's `hash_key`. The primary key `(gid, hash_key, move_num)` (`schema.sql:891`) stays unique because `move_num` differs.

### 9.2 Read

- `loadGame(con, gid, data)` (`:776-779`): for Tombstone ids the filter is `next_move != 723`. Today's `!= 361` would silently drop a dead stone on point 0. The centre prepend (`:784-786`) is unchanged.
- `loadGames(con, gids)` (`:936`, batch, used only by webdb `GameSearchHandler.java:400`) keeps `!= 361`. It is unreachable for Tombstone because webdb rejects Tombstone ids (§9.4). The method gets a comment that pins this.

### 9.3 PGN (Portable Game Notation) for the file archive and TB export (Plan A)

`SimpleFileGameStorer` writes every all-human live game as PGN (`DSGContextListener.java:254-256`, `ServerTable.java:3684`). `tbpgn.jsp` exports TB games as PGN. `PGNGameFormat.formatCoordinates` (`:953-960`) does not crash on codes `>= 361`, but it prints garbage (`722` → `A-19`).

- `PGNGameFormat.format`: `boolean isTombstone = data.getGame() != null && data.getGame().toLowerCase().contains("tombstone")` (the same pattern as Renju at `:213`). Tokens come from `TombstoneCodes.pgnToken(code)`: own stone `K10`, own pass `pass`, dead stone `xK10` (for example `361+q` → `"x" + formatCoordinates(q)`).
- `PGNGameFormat.parse` (`:347-361`): with `isTombstone`, the token goes through `TombstoneCodes.parsePgnToken(String)`: `pass` → `722`, `x<coord>` → `361 + parseCoordinates(coord)`, otherwise the coordinate.
- The `i % 2` numbering of the move list (`:216-223`) stays per entry, not per turn. That keeps the parser's `tokenNum % 3` round trip (`:352`) intact. PGN numbering therefore does not match turns, which is accepted (Connect6 already has this mismatch).
- Other formats (`DSG2_12GameFormat`, `PBEMGameFormat`, `SGFGameFormat`) are not reached for Tombstone by any in-scope path and are not changed.

### 9.4 Exclusions (D11) (Plan B)

All of these return the existing "not supported" style error for `GridStateFactory.isTombstone(game)`:

- `webdb/PositionStatsHandler.computeStats` (`:165`): the response is empty (`totalGames = 0`). `foldRow` would otherwise drop every code outside `0..360` (`MAX_BOARD_MOVE = 360`, `:52`).
- `webdb/GameSearchHandler` (game read at `:176`, `:215`, `:234`): HTTP 400 `bad_request`, "variant not supported".
- `webdb/GameLoadHandler` (`:76` loads by gid): when the loaded `GameData`'s game resolves to a Tombstone id, HTTP 400 "variant not supported" (the pentedb client cannot decode Tombstone codes).
- `webdb/CollectionHandler` import (`:117-123`): reject per item with the same `"variant not supported for import"` message as the off-centre variants. Also the `game` list parameter at `:178-182`.
- Legacy game database (`database.jsp`): `SimpleGameStorerSearchRequestFilterFormat` (`:323`) throws "Game not supported in the game database" for Tombstone ids, so `MySQLGameStorerSearcher` never queries them.
- The swing/awt desktop tools (`AnalysisCreator`, `FasterGameStorerSearcher`, `PlunkDbUtil`) are out of scope and unchanged.

## 10. Turn-based persistence and the move servlet (Plan C)

### 10.1 `TBGame`

- `getCurrentPlayer()` (`TBGame.java:289`): a Tombstone branch before the default parity: `cp = TombstoneCodes.currentPlayer(getMoves(), getNumMoves())`. This is `countOwnEntries % 2 + 1`, so after the centre alone it is P2. It sits beside the Connect6 branch at `:328`.
- New `int getLastTurnLength()`: `TombstoneCodes.lastTurnLength(getMoves(), getNumMoves())`, which is 2 if the last row is a dead code, 1 if it is an own entry, and 0 if only the centre exists.
- `passMove` stays `361` for Tombstone (`setGame`, `:195-203`, overrides only Go/Renju). `containsDoublePass` cannot fire (§4.1).

### 10.2 Storer: `CacheTBStorer.storeTombstoneTurn(long gid, int[] turn)`

This is a new public method. The servlet calls it through a cast, as it already does for `continueGoGame` (`MoveServlet.java` acceptUndo/Go paths cast `(CacheTBStorer) tbGameStorer`). The `TBGameStorer` interface is unchanged. Steps:

1. `loadGame(gid)`. It throws `TBStoreException` if the game is missing or not active (as `storeNewMove`, `:1682-1695`).
2. `TombstoneCodes.validateTurnShape(turn)`. On a non-null message → `InvalidMoveException(message)`.
3. `TombstoneState s = (TombstoneState) GridStateFactory.createGridState(TB_TOMBSTONE, game)` (the replay closes the trailing turn).
4. Own entry: `s.isValidMove(turn[0], s.getCurrentPlayer())`, else `InvalidMoveException`. `s.addMove(turn[0])`.
5. If `turn.length == 2`: if `s.isGameOver()`, **discard** `turn[1]` (D4). Else `s.isValidMove(turn[1], s.getCurrentPlayer())` (the point must be empty after captures), else `InvalidMoveException`; then `s.addMove(turn[1])`.
6. If the turn is still awaiting, `s.skipDeadStone()` (closes it; may produce the empty-turn draw).
7. Mirror `storeNewMove`'s commit section (`:1760-1808`): for each **kept** code `game.addMove(code)`; compute the timeout once (`Utilities.calculateNewTimeout`, `:1772`) after all codes; under `cacheTbLock`: `setTimeoutDate`, `setUndoRequested(false)`, `setDrawOffered(false)`; `gameOver = s.isGameOver()` → `game.end()`, `game.setWinner(s.getWinner())`; `persistSet` **once**. Then write the kept codes to `tb_move` through `baseStorer.storeNewMove(gid, moveNum, code)` in order, so the first row lands on any pending `-1` undo row (ON DUPLICATE KEY overwrite = implicit decline, as today). On game over: `endGameRunnable.endGame(game, winner == 0 ? REASON_DRAW : REASON_WIN)`. Finally, call `baseStorer.updateGameAfterMove(game)` **once**, after the `tb_move` writes, as `storeNewMove` ends (`CacheTBStorer.java:1807`) and `undoLastMove` does (`:229`). It keeps the MySQL `tb_game` row (last move date, timeout, state) in step with Redis.

`storeNewMove` throws `InvalidMoveException("Tombstone turns must use storeTombstoneTurn")` for `TB_TOMBSTONE`, so there is exactly one write path.

The centre stone at game start is unchanged. `TB_TOMBSTONE` is not in the D-Pente/Go/Swap2 exclusion lists at `CacheTBStorer.java:1465-1490` and `:1942-1955`, so row 0 = `180` is written as for Renju.

### 10.3 Servlet contract (`/gameServer/tb/game?command=move`)

Request: `gid`, `moves` (comma list, parsed at `MoveServlet.java:403-414`), optional `message`, `cycle`, `hide`, `mobile`. A `drawOffer` is rejected by the existing Renju-only gate (`:423`). `renjuAction` is ignored.

New branch, placed **before** the Connect6 branch (`:621`). The length check comes first, because the Connect6 branch logs `moves[1]` before it checks the length (critic finding):

```
} else if (game.getGame() == GridStateFactory.TB_TOMBSTONE) {
    String err = TombstoneCodes.validateTurnShape(moves);
    if (err != null) { handleError(request, response, err); return; }
    ((CacheTBStorer) tbGameStorer).storeTombstoneTurn(game.getGid(), moves);
    openingMessage = message;   // stored after refresh with the post-turn row count (:711-718)
}
```

The message object is built at `:436-445` with `moveNum = numMoves + 1`, which is only right for 1-row moves. The Tombstone branch does not store it inside the branch. It hands it to the opening-message path (`:711-718`), which stores it after the refresh with `moveNum = refreshedGame.getNumMoves()`: the row count after the stored turn (a discarded dead stone is not counted). The JS shows the message when `drawUntilMove == moveNum` (§11.4).

Valid shapes and errors. The shape check is pure and lives in `TombstoneCodes.validateTurnShape`. The occupancy and phase checks live in the storer and surface as `InvalidMoveException` → "Invalid move." (`:781-783`).

| Input `moves` | Result | Error text |
|---|---|---|
| `q` (`0..360`) | own only | — |
| `q,361+r` | own + dead (dead discarded if `q` wins) | — |
| `722` | empty turn | — |
| `722,361+r` | dead only | — |
| empty / more than 2 codes | rejected | `Invalid turn: send one or two moves.` |
| first code not in `0..360` and not `722` | rejected | `Invalid turn: the first move must be your stone or a pass.` |
| second code not in `361..721` | rejected | `Invalid turn: the second move must be a dead stone.` |
| `q` occupied, or a dead point occupied after captures (including `r == q`) | rejected | `Invalid move.` |
| not the sender's turn | rejected (existing gate `:386-389`) | `Its not your turn.` |

### 10.4 Undo (TB)

- `requestUndo` (`MoveServlet.java:238`): unchanged. The "nothing to undo" guard at `getNumMoves() == 1` (`:257`) already protects the centre.
- `acceptUndo` (`:291-312`): `numMoves = game.getGame() == TB_TOMBSTONE ? game.getLastTurnLength() : (Connect6 ? 2 : 1)`. Call `undoLastMove(gid, numMoves)` only when `numMoves > 0`. `CacheTBStorer.undoLastMove` already deletes the `-1` sentinel first and then `numMoves` top rows (`:209-232`).

### 10.5 Cancel, resign, timeout

These are unchanged: they do not depend on the row structure. The timeout winner uses `getCurrentPlayer` (§10.1).

## 11. Website TB board (`tb/mobileGame.jsp`, `tb/gameScript.js`) (Plan C)

`MoveServlet` renders `/gameServer/tb/mobileGame.jsp` for both desktop and mobile (`MoveServlet.java:26-27`).

### 11.1 Shared JS (`tb/gameScript.js`, also used by every viewer)

- Constants: `TOMBSTONE_DEAD_OFFSET = 361`, `TOMBSTONE_OWN_PASS = 722`, `TOMBSTONE_DEAD = 3` (an `abstractBoard` value), `tombstoneColor = "#D7BDE2"` (board fill, next to `renjuColor` at `:14`; the leading `var xColor =` form keeps `boardCommon.test.js`'s palette extraction working).
- `isTombstoneGame(g)`: `g === GAME.TOMBSTONE || g === GAME.SPEED_TOMBSTONE || g === GAME.TB_TOMBSTONE`.
- `tombstoneDecode(code)` → `{kind: 'own'|'pass'|'dead', point}`.
- `tombstoneTurns(movesList, until)` → `[{player, own, dead, start, end}]`, where `own` is a code, `dead` is a code or `-1`, and `start`/`end` are entry indexes. Turn 0 = `[movesList[0]]` (P1). An own entry starts a turn; a dead entry joins the open turn.
- `replayTombstoneGame(abstractBoard, movesList, until)`: reset; then, for the first `until` entries, walk with `tombstoneTurns` colours. Own stone → cell = player, then `detectPenteCapture(board, x, y, player)` (`:955`; it compares only `myColor`/`1 + myColor % 2`, so value-3 cells already block). Pass → nothing. Dead → cell = `3`. No tournament `-1` mask.
- `drawStone(i, j, color)` (`:683`) returns early for `color > 2`. It gets a `color === 3` branch that draws a gray radial gradient (`#C8C8C8` → `#6E6E6E`) before that guard. `drawGame` (`:74`) already calls `drawStone` for every cell `> 0`.
- `tombstoneLabel(code)` → `"K10"` | `"PASS"` | `"dead K10"`, plus `tombstoneTurnLabel(turn)` → `"K10 + dead L11"`, `"PASS + dead L11"`, `"K10"`, `"PASS"`. These are the same strings as the Java `TombstoneCodes.entryLabel`/`turnLabel` and React `tombstoneTurnLabel`.

`js/boardCommon.js`: add Tombstone cases to `getBoardColor` (→ `tombstoneColor`) and `replayMoves` (→ `replayTombstoneGame`). Both throw on unknown ids today (`:43`, `:82`), so every viewer breaks until this is done. `gameConstants.jspf` adds `TOMBSTONE`, `SPEED_TOMBSTONE`, `TB_TOMBSTONE` after `TB_RENJU` (`:49-51`).

### 11.2 Board click and preview (TB_TOMBSTONE branch of `boardClick`, `mobileGame.jsp:1133`, and its touch duplicates around `:736-1000`)

JS state: `tsOwn` (`-1` | point | `722`), `tsDead` (`-1` | point), declared beside `c6Move1/c6Move2` (`gameScript.js:38`).

- `tsOwn === -1` and the click is on an empty cell → `tsOwn = point`.
- `tsOwn !== -1`, `tsDead === -1`, and the click is on an empty cell of the **post-capture preview** → `tsDead = point`.
- A click on a preview stone (own or dead) clears both (`tsOwn = tsDead = -1`). This matches the existing "click on an occupied cell resets" behaviour (`:1259-1271`). After "Place only a dead stone" with no dead preview yet, the turn can only be cleared by reloading the page.
- The preview is drawn by `replayGame(abstractBoard, moves.concat(previewCodes), ...)`, where `previewCodes = [tsOwn]` plus `[361 + tsDead]` if a dead stone is set. That way, preview captures are shown before the dead stone is placed.

### 11.3 Buttons (exact copy)

They are shown only when it is my turn (`!"false".equals(myTurn)`, `:311`) and the game is `TB_TOMBSTONE`. The generic static "Submit" anchor (`:313-314`) is replaced by **one** dynamic anchor, `<a class="boldbuttons" id="tombstoneBtn" href="javascript:tombstoneButton();"><span id="tombstoneBtnLabel">…</span></a>`. Its label is set by a pure function `tombstoneTbButtonState(tsOwn, tsDead)`:

| State | Label | Action |
|---|---|---|
| `tsOwn === -1` | **Place only a dead stone** | `tsOwn = 722`; redraw; label updates |
| `tsOwn !== -1 && tsDead === -1` | **Submit without dead stone** | submit `moves=<tsOwn>` |
| `tsDead !== -1` | **Submit** | submit `moves=<tsOwn>,<361+tsDead>` |

Submitting uses the existing URL shape: `"/gameServer/tb/game?command=move&gid=" + gid + cycleStr + hideStr + "&moves=" + list + "&message=" + encodeURIComponent(message)` (as `submit()`, `:1403-1444`). An empty turn is "Place only a dead stone" then "Submit without dead stone", which sends `moves=722`. The Go/Renju "Pass" and "Draw?" anchors stay gated on `isGo`/`renjuComplete` and never show for Tombstone.

### 11.4 Move list, navigation, messages

- Server-side move table (`mobileGame.jsp:458-512`): Tombstone gets **one row per turn**: `<td>N</td><td onclick='selectMove(end)' id='end'>turnLabel</td>`, where `turnLabel = TombstoneCodes.turnLabel(own, dead)`. Today's per-entry rendering would print every dead code as `PASS` (`:483` treats `>= gridSize^2` as a pass).
- `selectMove`/`goBack`/`goForward` (`:201`, `:1348`, `:1381`) step to turn ends (`drawUntilMove = end + 1`) using `tombstoneTurns`.
- Last-move markers: highlight the last displayed turn's own stone point (if it is not a pass) and its dead point, never `code % 19` on raw codes. Today `drawRedDot` on `722` would compute `y = 38`, which is off the board.
- Messages: `messageMoveNums` are row counts after a turn (§10.3), so a message shows when `drawUntilMove === moveNum`. There is no Connect6-style shift.

### 11.5 Other TB pages

- `tb/cancelReply.jsp` and `tb/undoReply.jsp` embed their own board and Connect6 logic. They render through `boardCommon.replayMoves`, which the Tombstone case fixes. Plan C must switch any per-entry coordinate rendering in them to `tombstoneLabel`, and make the undo preview drop the whole last turn (`getLastTurnLength`).
- `tb/finalGo.jsp`, `tb/deadGo.jsp`: Go-only routes, never reached for Tombstone. No change.
- `tb/listedMobileGame.jsp`: it includes `boardCommon.js`, so it is covered by the dispatcher.

## 12. Game viewers (Plan C)

These pages include `boardCommon.js` and replay through `replayMoves(..., game)` with the TB id (`viewLiveGameMobile.jsp:21` and `viewLiveGameEmbed.jsp:206` compute `getGameId(name) + 50`; `viewGameEmbed.jsp:206` maps to `50 + odd id`): `viewLiveGameMobile.jsp`, `viewLiveGameEmbed.jsp`, `viewGameEmbed.jsp`. `ViewGameServlet` serves `viewLiveGameMobile.jsp` when `mobile` is set (`ViewGameServlet.java:52-54`) and `viewLiveGameEmbed.jsp` when `e` is set (`:109`). The forum embeds use the `e` route (`jive/GameFilter.java:44` builds the iframe with `&e=1`).

`viewLiveGame.jsp` (the default route, `ViewGameServlet.java:52`) is **not** a JS viewer. It is the Java `TBApplet` `<object>` page (`viewLiveGame.jsp:155`, `:198`) and does not include `boardCommon.js`. It falls under the swing/applet clients and is out of scope (§18).

- The replay comes from §11.1 (gray dead stones, decoded codes).
- Server-side move lists (`viewLiveGameMobile.jsp:257-290`, `viewGameEmbed.jsp:134-170`, `viewLiveGameEmbed.jsp:171-196`): one row per turn with `TombstoneCodes.turnLabel`. This replaces the per-entry `move < gridSize*gridSize ? coord : "PASS"` rendering (`viewLiveGameMobile.jsp:276`) and the unguarded `coordinateLetters[move % gridSize] + (gridSize - move / gridSize)` (`viewGameEmbed.jsp:153`, `viewLiveGameEmbed.jsp:190`, `:194`, the latter with a Connect6-only (`gameId == 63`) pairing). That form does not throw, because `% gridSize` stays in range, but it prints garbage: `722` → `A-19`, `361` → `A0`.
- Last-move markers: `viewLiveGameEmbed.jsp` calls `drawRedDot(lastMove % gridSize, Math.floor(lastMove / gridSize))` on raw codes (10 calls, `:370-491`). For Tombstone they mark the decoded points of the last displayed turn, as in §11.4.
- JS stepping (`viewLiveGameMobile.jsp:371-394`, `viewGameEmbed.jsp:237-283`, and the matching Connect6 branches of `viewLiveGameEmbed.jsp`): Tombstone steps per turn, as in §11.4.
- Messages: `moveNums` stays `entry.moveNum` with no `+2` (the `+2` is `TB_CONNECT6`-only, `viewLiveGameMobile.jsp:116`).
- `tbpgn.jsp` gets the PGN tokens from §9.3 without a page change.

## 13. React live game room (Plan D)

### 13.1 Identity

- `game/boardGeometry.js`: `isTombstone(id)` for `33`, `34`, `83`. `variantKey` returns `'tombstone-pente'` for those ids, before the `'swap2-keryo'` fallthrough (`:41`); otherwise unknown ids fall into Swap2-Keryo. `STANDARD_GAME_IDS` (`:51`) appends `33`. Today the SettingsModal and CreateArenaTableModal selectors map `STANDARD_GAME_IDS` unconditionally (`SettingsModal.js:128`, `CreateArenaTableModal.js:126`), so every server lists all 16 games, including the Go server. Plan D makes both pickers list **only the games the current server offers**: the login reducer (`rootReducer.js:103-104`, which already reads `p.serverData.tournament`/`arena`) also stores the game ids from `p.serverData.gameEvents` (a non-transient field, `ServerData.java:20`, sent with the login event at `ServerPlayer.java:222`; present in the captured login frame, `protocol/__fixtures__/wire-fixtures.json:411`), and `selectableGameIds(serverGameIds)` keeps a standard id `g` iff the list contains `g` or its speed id `g + 1`. On Experimental the picker therefore shows only Tombstone Pente; on Main Room, Beginners, KOTH and the Arena it shows today's 16 games without Tombstone; on the Go server it shows the three Go boards. If the login frame has no `gameEvents` array, the picker shows `STANDARD_GAME_IDS` without `33` (every pre-existing server's set), and the server rule (§7.3) still rejects anything unoffered. `gridSizeForGame` is unchanged (19).
- **Entering Experimental.** No React change is needed. The room's server list comes from `/gameServer/activeServers` (`Pages/Servers.js:40-56`), which lists every running server unfiltered (§7.5), so "Experimental" appears with its port, and clicking it connects to `wss://<host>/websocketServer/16004` (`Servers.js:14`, `redux_actions/actionTypes.js:49-57`), the endpoint `DSGContextListener.addServer` registers for every server (`:374`). A new table there opens as Tombstone Pente (§7.3 `defaultGame`).
- `Classes/utils.js` `VARIANT_NAMES['tombstone-pente'] = 'Tombstone Pente'`, so `game_name(34)` gives "Speed Tombstone Pente". `Classes/TableClass.js` `VARIANT_COLORS['tombstone-pente'] = '#D7BDE2'`. `App.css` `.tombstone-pente` board fill, and `#gray-stone-gradient { --color1: #c8c8c8; --color2: #6e6e6e; }` next to `#black-stone-gradient` (`:57`).
- `gameHasCaptures` stays true (Pente captures). `critical_captures` uses the default threshold.

### 13.2 Pure turn model: `src/game/tombstoneTurn.js` (new)

```js
export const TOMBSTONE_DEAD_OFFSET = 361, TOMBSTONE_OWN_PASS = 722, DEAD_CELL = 3;
export function decodeTombstone(code)            // {kind:'own'|'pass'|'dead', point}
export function parseTombstoneTurns(moves)       // [{player, own, dead /*code|-1*/, start, end}]
export function tombstonePhase(moves, trailingClosed)
        // {player: 1|2, phase: 'OWN'|'DEAD'}
        // DEAD iff last entry is an own entry at index > 0 and !trailingClosed
export function tombstoneButton(moves, trailingClosed, pending)
        // null | {label: 'Place only a dead stone', action: 'ownPass'}
        //      | {label: 'Skip dead stone',       action: 'skipDead'}
        // (OWN phase -> ownPass, DEAD phase -> skipDead; pending -> null)
export function tombstoneTurnLabel(turn, coord)  // 'K10 + dead L11' | 'PASS + dead L11' | 'K10' | 'PASS'
```

The turn player and colour rule is the one in §5.3.

### 13.3 Game state

- `game.gameState.tombstone = {trailingClosed: true}`, set in `reset()` (`GameClass.js:329`) and copied in `newInstance()` (`:72`). Today `newInstance` copies only selected fields.
- `VARIANT_RULES['tombstone-pente'] = {replay: 'tombstone', add: 'tombstone', player: 'tombstone', postRule: 'none', disableRatedOnReplay: false, goMove: false}` (`:28`). `#replayTombstoneGame`/`#addTombstoneMove` decode codes: own stone → cell = turn player, plus Pente captures (reusing `#detectPenteCapture`); pass → nothing; dead → cell `3`. They must never compute `move % 19` on a raw code. Today `361+q` would give `y >= 19`, and `Board.js:197-201` `board[move].last_move = true` throws on undefined.
- `currentPlayer()` / `currentColor()` (`:233`, `:258`): for Tombstone, from `tombstonePhase`. `currentColor()` returns `3` in the DEAD phase, so the hover ghost is gray.
- `last_move()` (`:126`): for Tombstone, the decoded points of the last turn's own stone (skipped for a pass) and dead stone.
- `moves_strings()` (`:204`) and `MovesListPanel` (`:61` Connect6 special case): one cell per turn with `tombstoneTurnLabel`. A click calls `goto_move(turn.end + 1)`, and `goForwardBack` steps per turn.
- `undoTurn()`: drops the last parsed turn's entries, sets `trailingClosed = true`, and replays.

### 13.4 Board and buttons

- `Board.js` `player_colors` (`:67`) gets index 3 = `'gray-stone-gradient'`. The hover uses `player_colors[game.currentColor()]` (`:71`).
- Click gating (`:132`): when it is my turn in Tombstone and `tombstoneUi.pending` is false, an empty cell sends `sendMove(point)` in the OWN phase and `sendMove(361 + point)` in the DEAD phase (`sendMove`, `:41`).
- `GameInfoPanel.js`: the PASS button block (`:175-184`) is reused. For Tombstone it is shown when `table.isMyTurn(game)` and the game has started, with the label and action from `tombstoneButton(...)`:
  - **Place only a dead stone** → `Commands.move({move: 722, moves: [722], player, table})`
  - **Skip dead stone** → `Commands.tombstoneSkipDead({player, table})`

  Material UI (MUI)'s default uppercase transform renders these like the existing "PASS" button. Both actions set `tombstoneUi.pending = true`. The Go/Renju `pass()` (`:79`), which sends `gridSize*gridSize`, is never used for Tombstone.
- `TableClass.canUndo` (`:58`): Tombstone arm = started && `!isMyTurn` && phase `OWN` && turns > 1 && the last turn's player is my seat (mirrors §5.6).
- `clockRunning` (`:193`) already uses `game.currentPlayer()`, so the mover's clock keeps running through the DEAD phase.

### 13.5 Protocol and reducers

- `protocol/messages.js`: `dsgTombstoneSkipDeadTableEvent: { dir: 'both', cmd: 'tombstoneSkipDead', out: ['player', 'table'], req: TBL }`. Add `'dsgTombstoneSkipDeadTableErrorEvent'` to the inbound error list (`:82-96`). Add fixtures to `protocol/__fixtures__/wire-fixtures.json`.
- `redux_reducers/utils.js` `addMove` (`:203`), Tombstone branch:
  - **Incremental**: append; own entry at index 0 → `trailingClosed = true`; other own entry → `false`; dead entry → `true`.
  - **Bulk** (rejoin): replay; `trailingClosed = !(last is an own entry && moves.length > 1)`.

  Both clear `tombstoneUi.pending`.
- New handler `dsgTombstoneSkipDeadTableEvent` → `trailingClosed = true`, clear pending. Play the move sound unless `silent` or the event is from me. `dsgTombstoneSkipDeadTableErrorEvent` and `dsgMoveTableErrorEvent` clear pending.
- `undoReply` (`:357-362`): for Tombstone, call `game.undoTurn()` instead of `game.undoMove()`.
- `src/ui/tombstoneUi.js` (new, modelled on `ui/renjuOpeningUi.js`): `{pending: boolean}`, reset by `ADVANCING_EVENTS` = `dsgMoveTableEvent`, `dsgTombstoneSkipDeadTableEvent`, `dsgMoveTableErrorEvent`, `dsgTombstoneSkipDeadTableErrorEvent`, `dsgUndoReplyTableEvent`, plus the game- and table-reset events that the Renju precedent uses (`renjuOpeningUi.js:22-33`): `dsgGameStateTableEvent`, `dsgChangeStateTableEvent`, `dsgExitTableEvent`, `dsgBootTableEvent`. The reset events keep `pending` from leaking into the next game when a game ends some other way (resign, timeout, cancel) while an action is outstanding.

### 13.6 Exact UI states (my seat)

| Server state | Phase | Board click | Button |
|---|---|---|---|
| my turn, nothing placed | OWN | own stone | **Place only a dead stone** |
| my turn, own stone or own pass sent and echoed | DEAD | dead stone (gray hover) | **Skip dead stone** |
| action sent, echo pending | — | inert | hidden |
| opponent's turn (either phase) | — | inert | hidden; Undo shown if the canUndo rule holds |

## 14. Every crash or garble on codes ≥ 361, and the fix

| Site | Failure today | Fix (plan) |
|---|---|---|
| `DSGMoveTableEvent.toString` via `SynchronizedServerTable.java:117`, `LoggingDSGEventToPlayerRouter.java:60` | AIOOBE for `362..721`, `722` before the move is handled | `formatMoveForLog` (B, §8.6) |
| `ActivityTableData.getMoves` (`:41-51`) | AIOOBE (no callers found) | same helper (B) |
| `SimpleGridState.updateHash` p=0 for non-board codes (`:578-581`) → `rand[p-1]` | AIOOBE `rand[-1]` in Pente-style `calcHash` | `TombstoneState.calcHash` decodes the code itself (A, §6.3) |
| `SimpleGridState.rotateMove` leaves out-of-bounds codes unrotated (`:401-402`) | wrong canonical hash and rotation | rotate the decoded point (A) |
| `SimpleGridState.hashes[720]`/`rots[720]` (`:349-350`) | AIOOBE after 720 entries | capacity constructor, 3000 (A, §5.5) |
| `SimplePenteState.makeCaptures` on a value-3 stone | "captures" empty points; `capturedAt[3]` AIOOBE | captures only on own stones (A) |
| `MySQLPenteGameStorer` last-row `next_move = 361` and load filter `!= 361` (`:509-511`, `:778`) | a dead stone on point 0 is dropped on reload | sentinel `723` for Tombstone (B, §9) |
| `PGNGameFormat.formatCoordinates` (`:953-960`) | garbage tokens (`A-19`) | Tombstone tokens (A, §9.3) |
| `PositionStatsHandler.foldRow` (`MAX_BOARD_MOVE = 360`) and the other webdb handlers | silent wrong stats | exclusion (B, §9.4) |
| `boardCommon.js` `getBoardColor`/`replayMoves` (`:43`, `:82`) | throw on an unknown id | Tombstone cases (C) |
| `gameScript.js` `replay*` `% 19`, `drawRedDot` | stones off the board, dots off the board | `replayTombstoneGame`, decoded markers (C) |
| `mobileGame.jsp:483`, `viewLiveGameMobile.jsp:276`, `viewGameEmbed.jsp:153`, `viewLiveGameEmbed.jsp:190`, `:194` | dead stones shown as `PASS`, or garbage | turn labels (C) |
| `viewLiveGameEmbed.jsp` `drawRedDot(lastMove % gridSize, …)` (`:370-491`) | dots off the board | decoded markers (C, §12) |
| `ServerTable.handleChangeState` (`:925-941`), `ServerTable.resetTable` (`:281-302`), `ArenaServerTable.resetTable` (`:354`) → `getGameData` `getGameEvent(…).getName()` (`:3407`) | NullPointerException at game end, junk KOTH hill, whenever a table plays a game its server has no event row for (Tombstone off Experimental, or any other game on Experimental) | server-offering rule `ExperimentalServer.gameAllowed` at all three sites (B, §7.3) |
| `Server.addPlayerSocket` (`Server.java:230-249`) on Experimental | a mobile app would join a Tombstone-only room it cannot render | refuse native sockets on Experimental (B, §7.5) |
| `MoveServlet` Connect6-style `moves[1]` read before the length check (`:621`) | AIOOBE if copied for a 1-code turn | length check first (C, §10.3) |
| `MoveServlet.acceptUndo` `TB_CONNECT6 ? 2 : 1` (`:304-308`) | orphan own stone | `getLastTurnLength()` (C) |
| React `GameClass` `% 19` decoding, `Board.js:197-201` `board[move]` | throws, garbage | decode first (D) |
| React `utils.js:206` bulk-vs-incremental heuristic | a 2-code frame is misread as bulk | never send 2-code frames live (B/D, §8.1) |
| `TBGame.containsDoublePass` (`passMove = 361`) | cannot fire (§4.1) | none; pinned by a test (C) |
| swing/awt clients (including the `TBApplet` page `viewLiveGame.jsp`), `GridStateFactory.getColor`, `MoveIconRenderer`, `SGFGameFormat`, desktop db tools | wrong colours, garbage | out of scope |

## 15. Production-deploy warning

This branch registers Tombstone Pente publicly: the new Experimental live server (47) offers it to every website and React user, the TB new-game list shows it, the React selector lists it there, and no server-side check limits who may create or join a Tombstone game. Beta gating is deliberately not part of this work. **Until beta gating exists, this branch must not be deployed to production** (`sync_gameServer.sh`), and `main` must not be deployed while it contains these commits. Local verification uses the local smoke-test route (compile, bind-mount, restart `penteorg-pente.org-1`), never the production sync script.

## 16. Plan split and interfaces

There are four plans. **A** lands first, because B and C compile against its classes. **D** depends only on the wire contract (§8.1, §16.3) and can run in parallel with A, B and C. B and C are independent of each other once A is in. All work is on `feat/tombstone-pente` in each repo, with no commits to `main`.

| Plan | Repo | Owns |
|---|---|---|
| A — game core | `pente.org` | `GridStateFactory` registration (§7.1), `TombstoneCodes`, `TombstoneState`, `SimpleGridState` capacity constructor, `ZobristUtil.rand[4]` (§6), PGN tokens (§9.3) |
| B — live server, archive, boot, logging | `pente.org` | events + wrapper + dispatch (§8.1), `ServerTable` Tombstone arms (§8.2-8.5), `ExperimentalServer` (§7.5), the server-offering rule in `handleChangeState`, `ServerTable.resetTable` and `ArenaServerTable.resetTable` (§7.3), logging fix (§8.6), `registerAllGames`, `MySQLServerStorer.ensureServer` + the Experimental boot step in `DSGContextListener` (§7.2), the mobile list filters and the native-socket refusal (§7.5), archive sentinel (§9.1-9.2), webdb and legacy-db exclusions (§9.4) |
| C — TB + website | `pente.org` | `TBGame` (§10.1), `CacheTBStorer.storeTombstoneTurn` (§10.2), `MoveServlet` (§10.3-10.4), `NewGameServlet` KOTH reject and `tb/newKotH.jsp` list filter (§7.3), `admin/newTourney.jsp` exclusion (§7.4), `gameConstants.jspf`, `gameScript.js`, `boardCommon.js`, `mobileGame.jsp`, TB reply pages, viewers (§11-12) |
| D — React live room | `react_live_game_room` | §13 |

### 16.1 Plan A → B, C (Java, package `org.pente.game`)

```java
public final class TombstoneCodes {
    public static final int POINTS = 361;            // 19x19
    public static final int DEAD_OFFSET = 361;
    public static final int OWN_PASS = 722;
    public static final int ARCHIVE_NO_NEXT_MOVE = 723;
    public static final int DEAD_CELL = 3;           // board cell value
    public static boolean isOwnStone(int code);      // 0..360
    public static boolean isOwnPass(int code);       // == 722
    public static boolean isOwnEntry(int code);      // stone or pass
    public static boolean isDeadStone(int code);     // 361..721
    public static int deadCode(int point);           // 361 + point
    public static int pointOf(int code);             // stone: code; dead: code-361; pass: -1
    public static int countOwnEntries(int[] codes, int n);
    public static int currentPlayer(int[] codes, int n);   // complete-turn history: countOwnEntries % 2 + 1
    public static int lastTurnLength(int[] codes, int n);  // 0 if n <= 1; 2 if last is dead; else 1
    public static String validateTurnShape(int[] turn);    // null, or one of the §10.3 messages verbatim
    public static int archiveNoNextMove(int game);         // 723 if isTombstone(game) else 361
    public static String coordinate(int point);            // "K10" (A..T without I; row 19 - y)
    public static String entryLabel(int code);             // "K10" | "PASS" | "dead K10"
    public static String turnLabel(int ownCode, int deadCodeOrMinus1); // "K10 + dead L11"
    public static String pgnToken(int code);               // "K10" | "pass" | "xK10"
    public static int parsePgnToken(String token);         // inverse; -1 if invalid
}

public class TombstoneState extends GridStateDecorator implements HashCalculator {
    public static final int MAX_ENTRIES = 3000;
    public static final int CAPTURES_TO_WIN = 10;
    public static final int EMPTY_TURNS_FOR_DRAW = 6;
    public TombstoneState();                          // 19x19 prototype
    public GridState getInstance(MoveData data);      // replay + finish history (§5.4)
    public void addMove(int code);                    // replay primitive (§5.4)
    public boolean isValidMove(int code, int player); // strict (§5.5)
    public boolean isAwaitingDeadStone();
    public void skipDeadStone();                      // IllegalStateException if not awaiting
    public boolean isTrailingTurnClosedWithoutDead();
    public int getCurrentPlayer();
    public int getColor(int entryIndex);              // entry's player, 0 if out of range
    public int getTurnCount();
    public int getLastTurnLength();                   // in the live state (0 if only the centre)
    public boolean canPlayerUndo(int seat);           // §5.6
    public int undoTurn();                            // returns entries removed (1 or 2)
    public boolean isGameOver();
    public int getWinner();                           // 1, 2, or 0 = draw
    public int getNumCaptures(int player);
    public int getConsecutiveEmptyTurns();
    public long calcHash(long cHash, int p, int move, int rot); // §6.3; ignores p, reads lastCapturedPoints
}
```

`GridStateFactory` additions: `TOMBSTONE`, `SPEED_TOMBSTONE`, `TB_TOMBSTONE`, `TOMBSTONE_GAME`, `SPEED_TOMBSTONE_GAME`, `TB_TOMBSTONE_GAME`, `NON_KOTH_LIVE_GAMES`, `NON_KOTH_TB_GAMES`, `isTombstone(int)`. `SimpleGridState(int x, int y, int maxEntries)`. `ZobristUtil.TOMBSTONE_DEAD_ROW = 4` and `rand[4].length == 361`.

### 16.2 Plan B ↔ C (shared server behaviour)

- C does not touch `ServerTable`, the events or the archive writer. B does not touch `turnBased/*` or JSP/JS.
- Archive rows of finished TB games are written by B's storer change (§9.1), reached from C's `EndGameRunnable` path unchanged. C relies on `TombstoneCodes.archiveNoNextMove`.
- The viewers (C) read archived games loaded by B's `loadGame` filter (§9.2).

### 16.3 Plan B ↔ D (wire contract)

| Name | Direction | Fields | Notes |
|---|---|---|---|
| `dsgMoveTableEvent` | both | `move`, `moves` (= `[move]`), `player`, `table` | codes `0..360`, `361..721`, `722`; never 2 codes live |
| `dsgTombstoneSkipDeadTableEvent` | both | out: `player`, `table`; in: + `silent` | server broadcasts to the table; silent on rejoin only (§8.5) |
| `dsgTombstoneSkipDeadTableErrorEvent` | in | `player`, `table`, `error` | `error` in {`NOT_IN_TABLE = 2`, `NO_GAME_IN_PROGRESS = 4` (also sent for `WAIT_GAME_TWO_OF_SET`), `GAME_WAITING_FOR_PLAYER_TO_RETURN = 5`, `NOT_SITTING = 6`, `NOT_TURN = 12`, `INVALID_MOVE = 13`, `NOT_AWAITING_DEAD_STONE = 26`} (§8.2) |
| `dsgMoveTableErrorEvent` | in | existing | `NOT_TURN` / `INVALID_MOVE` |
| `dsgUndoReplyTableEvent` | both | existing | accepted ⇒ the whole last turn is removed |
| `dsgLoginEvent` | in | existing; D reads `serverData.gameEvents[].game` | the ids the current server offers; on Experimental exactly `[33, 34]` |
| `dsgChangeStateTableErrorEvent` | in | existing | `error = UNKNOWN (99)` (`DSGTableErrorEvent.java:31`) when the requested game is not offered by the server (§7.3); carries the unchanged table settings |
| `/gameServer/activeServers` (HTTP, text) | in | existing `"<port> <name> (<n>)"` lines | lists `16004 Experimental (n)`; mobile endpoints do not (§7.5) |

Ids: `33` (Tombstone Pente), `34` (Speed Tombstone Pente), `83` (turn-based, not shown live). Live server: Experimental, id `47`, port `16004`, the only server offering `33`/`34`.

### 16.4 Plan C internal contract (JSP ↔ JS)

- `gameConstants.jspf`: `GAME.TOMBSTONE = 33`, `GAME.SPEED_TOMBSTONE = 34`, `GAME.TB_TOMBSTONE = 83`.
- `gameScript.js` exports (globals): `isTombstoneGame`, `tombstoneDecode`, `tombstoneTurns`, `replayTombstoneGame`, `tombstoneLabel`, `tombstoneTurnLabel`, `tombstoneTbButtonState`, `tombstoneColor`, `TOMBSTONE_DEAD_OFFSET`, `TOMBSTONE_OWN_PASS`, `TOMBSTONE_DEAD`.
- Servlet `moves` parameter: §10.3.

## 17. Testing strategy

| Subsystem | Framework / command | Tests |
|---|---|---|
| A core | JUnit 3 classes in `dsg_src/java/org/pente/game/test/`, run one by one with `ant test-one -Dtest=org.pente.game.test.<Class>` (`build.xml:195`) | `TombstoneCodesTest`: shape validation table (§10.3), labels, PGN tokens round trip, `currentPlayer`/`lastTurnLength`. `TombstoneStateTest`: the 4 turn shapes; centre-only turn 0; own pass not allowed on turn 0; capture by an own stone; a dead stone blocks a capture and a five; a dead stone on a just-captured point; a winning own stone ends the game (no dead accepted); the 6-empty-turn draw (and the 5-then-stone reset); the full-board draw; `undoTurn` for both lengths; `canPlayerUndo` rules; `getInstance` replay equals the live state; `skipDeadStone` errors; 2812-entry worst case without AIOOBE. `TombstoneZobristTest`: row lengths `[723,723,720,721,361]`, the new row distinct and non-zero and disjoint from rows 0-3; the same position by two orders gives the same hash; the 8 rotations give the same canonical hash; a pass keeps the slot. `TombstoneFactoryTest` (modelled on `RenjuFactoryTest`): ids, `getSpeedGame`/`getNormalGame`, `createGridState(33/34/83)` type, `getGame(83)`, `getGameId("Tombstone Pente") == 33`, `getMaxGameId() == 83`, arrays unchanged except §7.1. `PGNTombstoneTest` (modelled on `PGNRenjuPassTest`). Re-run `AllGamesTest`, `RenjuFactoryTest`, `GridStateFactorySingleGameSetTest`, `MySQLGameVenueStorerTbLookupTest`, `PGNRenjuPassTest`, `PenteStateTest`, and update any count they pin. |
| B live/archive | JUnit, `org.pente.gameServer.server.test` (harness of `ServerTableDrawScoringTest`/`ServerTableKidTest`) | `ServerTableTombstoneTest`: own→dead, own→skip, pass→dead, pass→skip; `NOT_TURN`; an own code in the dead phase → `INVALID_MOVE`; skip outside the dead phase → `NOT_AWAITING_DEAD_STONE`; one `moveTimes` entry per stored entry; one increment per turn; after a skip, `moveTimes.getLast()` equals the post-increment clock; undo of both turn lengths restores `moveTimes`, and an undo whose previous turn by the accepting player was closed by a skip restores that player's post-increment time; a game change to Tombstone on a server without a Tombstone event row (KOTH-style `ServerData`) is rejected with `DSGChangeStateTableErrorEvent` and leaves `game` unchanged, and a change to Pente on an Experimental-style `ServerData` (only 33/34) is rejected the same way; a new table on Experimental opens as Tombstone, a saved Tombstone preference opens Pente on a Main-Room-style server; `ExperimentalServerTest` (pure): `gameAllowed` both directions plus the tournament and empty-offerings escapes, `defaultGame`, `isExperimental` id+name, `acceptsNativeSocket`; `MobileServerListFilterTest`: `LiveServersResponse.build` and `WhosonlineAndLiveResponse.build` drop Experimental and keep the rest; `TombstoneBootRegistrationTest`: no server's offering list contains 33/34 except Experimental's; the skip error codes for every §8.2 state; rejoin sends the silent skip only when `isTrailingTurnClosedWithoutDead`; the empty-turn draw ends the game via skip. A `DSGMoveTableEvent.toString()` unit test for `0, 180, 361, 380, 721, 722`. A unit test for `archiveNoNextMove` and the loader filter choice. Webdb handler tests (existing `webdb/test`, e.g. `PositionStatsHandlerTest`) for the Tombstone rejections. Manual: local smoke test (memory "Local smoke-test server classes"): after a restart, `dsg_server` has row 47 "Experimental" with `dsg_server_game` rows for 33/34 only; two browsers on Experimental play every turn shape; then reload the archived game; `mobile/json/liveServers.jsp` and `mobile/json/whosonlineandlive.jsp` omit Experimental while `/gameServer/activeServers` lists it; a raw TLS connection to port 16004 is closed at once. |
| C TB/website | JUnit `org.pente.turnBased.test` (`InMemoryTBGameStorer`, `SerializingRedisConnectionManager`, `CacheTBStorerRedisTest` patterns); Node for JS: `node dsg_src/httpdocs/gameServer/js/boardCommon.test.js` | `TBGameTombstoneTest`: `getCurrentPlayer` across shapes; `getLastTurnLength`; `containsDoublePass == -1` with a dead stone on point 0. `CacheTBStorerTombstoneTest`: `storeTombstoneTurn` for all shapes; the dead stone discarded after a win; an occupied dead point rejected; atomic persist (a single `persistSet`); a single `updateGameAfterMove` after the `tb_move` writes; a pending undo row overwritten; `storeNewMove` refuses `TB_TOMBSTONE`. `boardCommon.test.js`: add `TOMBSTONE: 33` to `base` (`:18-22`) and map it to `tombstoneColor`/`replayTombstoneGame`. New `js/tombstone.test.js` (same `vm` pattern): `tombstoneTurns`, `replayTombstoneGame` captures and dead cells, `tombstoneTbButtonState` labels, `tombstoneTurnLabel`. Manual: TB game through every shape on desktop and mobile web, viewers, `tbpgn.jsp` export. |
| D React | Vitest: `cd react_live_game_room && npx vitest run` (Plan D records the passing count on `feat/tombstone-pente` before its first change and treats that as the baseline; the figure is not pinned here) | `game/__tests__/tombstoneTurn.test.js` (parse, phase, button incl. pending, labels); `Classes/__tests__` replay/addMove/last_move/moves_strings/undoTurn for mixed turns; reducer tests modelled on `renjuTracking.test.js` + `renjuTestHelpers.js`: incremental own→DEAD phase, dead→OWN, skip echo, bulk rejoin default plus silent skip correction, undo; `tombstoneUi.pending` cleared by each `ADVANCING_EVENTS` entry (including game-state and table-reset events); the selectors list only the ids the login `serverData.gameEvents` offers (Experimental → `[33]`, Main Room → the 16 old ids, Go → `[19, 21, 23]`, absent → the 16 old ids); protocol fixtures and `decode`/`commands` tests for the new events; update the pinned `boardGeometry.test.js` (`:6` ids, `:12` variant count) and `tableClass.test.js` colours. Manual: play against a second browser on the local server. |

Every plan ends with its full suite green and `git status --porcelain | grep '^??'` clean of scratch files.

## 18. Out of scope

- Mobile app code (Android, iOS) and their JSON (`GameResponse`): they skip unknown game ids today. The server-side hiding of Experimental from the apps (§7.5) is in scope; app changes are not.
- Beta gating (separate, later work; §15).
- Tournaments, KOTH, AI play, draw offers (Renju-only), timeout-draw evaluation.
- pentedb/webdb support, the legacy game database, the swing/awt clients (including the `TBApplet` viewer `viewLiveGame.jsp`), the desktop db tools, and the SGF/DSG2/PBEM formats.
- Fixing the existing Zobrist defects (§6.1) or Connect6's per-stone increment quirk.
- Per-turn PGN numbering (§9.3).
