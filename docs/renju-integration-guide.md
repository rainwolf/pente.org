# Renju Integration & New-Game Playbook

**Status:** living document — keep updated as Renju and the game framework evolve.
**Branch:** `feat/renju`. **Maintainer note:** when you change any Renju touchpoint, update the matching section here.

This file serves three audiences:
1. **Record** of every change made to add Renju to the `pente.org` backend.
2. **Porting guide** for the other clients (iOS, React `react_live_game_room` / `react_mmai`, Android) to wire Renju in.
3. **Playbook** for adding *any* new game variant, using Renju as the worked example.

---

## 1. What Renju is (rules that drive the code)

- 15×15 board. Black plays first; stones alternate strictly by move order.
- **Win:** black wins only on **exactly five** in a row; white wins on **five or more** (overline). 
- **Black forbidden points:** black may not make an **overline (6+)**, a **double-four**, or a **double-three**. White has no forbidden points.
- **Taraguchi-10 opening** ([renju.net/rule/25](https://www.renju.net/rule/25/)):
  - Move 1 (black) = center. Then a **swap option** for the other player after each of moves 1–4.
  - Move 2 ∈ 3×3, move 3 ∈ 5×5, move 4 ∈ 7×7 (central squares around center).
  - After move 4, black either **continues** (Branch A: place move 5 ∈ 9×9, then white swap option, then white move 6 anywhere) or **offers** (Branch B: 10 alternative 5th moves anywhere, no two symmetric; white picks one, then white move 6 anywhere).
  - After move 6 → normal play (black bound by forbidden points).

Two modeling decisions that everything else follows from:
- **Stone color = move parity**, not seat. 1st stone black, 2nd white, … A *swap* reassigns which player owns black; it never recolors stones on the board. (Seat reassignment is done by swapping player ids in the controller, exactly like the existing dPente/swap2 games.)
- **Forbidden points are blocked** (an illegal move is simply rejected), not allowed-then-lose.

---

## 2. Backend architecture (Java, `org.pente.game`)

### 2.1 Rules engine (pure, reusable)
- **`RenjuForbiddenPointFinder.java`** — faithful Java port of the C++ `CForbiddenPointFinder` (`../ForbiddenPointFinder`). Pure board logic, parametric size. Public API: `isFive(x,y,color)` (black exact-5 / white ≥5), `isOverline`, `isFour`, `isOpenFour` (0/1/2), `isOpenThree`, `isDoubleFour`, `isDoubleThree`, **`isForbidden(x,y)`** (overline‖double-four‖double-three), `findForbiddenPoints()`. Mutual recursion between `isOpenThree`/`isDoubleThree`/`isOpenFour`/`isDoubleFour` is preserved exactly.
- **`RenjuState.java`** `extends GridStateDecorator implements GomokuState, HashCalculator` — wraps a `SimpleGomokuState`. Overrides:
  - `isGameOver`/`getWinner` — black exact-5, white ≥5 (re-evaluates the last move via the finder).
  - `isValidMove` — opening central-square gate + negotiation block + **forbidden-point block for black** post-opening.
  - `getCurrentPlayer` — the Taraguchi-10 state machine.
  - `getInstance(MoveData)` — rebuilds a `RenjuState` (else the decorator returns a bare gomoku — **silently dropping Renju rules**).
  - `getForbiddenPoints()` — for UI hinting (server-side; not yet surfaced to clients).
  - Opening hooks: `renjuSwapDecisionMade(boolean)`, `chooseBranch(boolean tenOffer)`, `offerFifthMove(int)`, `selectFifthMove(int)`, predicates `isAwaitingSwapDecision/BranchChoice/FifthOffers/FifthSelection`, `isOpeningComplete`.
  - **`static reconstruct(MoveData moves, int renjuSwapsPacked, int[] offers)`** — replays moves **and** re-applies the recorded swap/branch/offer/select decisions in order, so a persisted game rehydrates exactly. (Plain `getInstance` only replays moves.)
- **`RenjuOpeningState.java`** — codec for the opening decisions. Six **base-3 (ternary)** digits packed into one int (0–728, fits `smallint`): `swap1..swap4`, `branch`, `swap5`; each digit `0=pending / 1=no(=Branch A) / 2=yes(=Branch B)`. Also `encodeOffers(int[])`/`decodeOffers(byte[])` (each 15×15 position 0–224 = one unsigned byte). **Ternary, not binary**, because a turn-based game reloads into an *unresolved* (pending) swap window, which a bit can't represent.

### 2.2 Game registration — `GridStateFactory.java`
IDs follow the convention **odd = normal, even = speed (+1), turn-based = +50**:
- `RENJU = 31`, `SPEED_RENJU = 32`, `TB_RENJU = 81`.
- Added consistently to **every** array: `LIVE_GAMES`, `TB_GAMES`, the three `Game` objects (`RENJU_GAME`/`SPEED_RENJU_GAME`/`TB_RENJU_GAME`), `allGames` (**id-indexed — RENJU_GAME/SPEED_RENJU_GAME must sit at indices 31/32**, pushing the TB block down), `displaygames`, `normalGames`, `speedGames`, `tbGames`.
- `createGridState` switch: `case RENJU/SPEED_RENJU/TB_RENJU: return new RenjuState(15, 15);` (hardcodes 15×15 like GO9/GO13).
- `isValidGame` upper bound → `SPEED_RENJU`; `getMaxGameId` → `TB_RENJU`.
- **`getCenterMove(int game)`** (new helper) — the board-aware opening center: `createGridState(game)` → `convertMove(size/2, size/2)`. 19×19 → 180, Renju 15×15 → **112**. Replaces every hardcoded `180` on a Renju path (see §2.5).
- Boot `game_event`/server-offering registration (`MySQLGameVenueStorer.registerAllGames`, `DSGContextListener`) is array-driven off `LIVE_GAMES`/`TB_GAMES` — **auto-includes Renju, no change needed**. DB columns (`tinyint`/`smallint`) already fit the new ids; **no schema change for ids**.

### 2.3 Turn-based persistence
- **Schema** (`dsg_src/sql/schema.sql` + runnable migration `dsg_src/sql/2026-06-14-renju-opening-state.sql`, idempotent):
  - `tb_game` + **`tb_game_ai`** (⚠ both — `loadGame` selects `TB_COLUMNS` from `tb_game_ai` too; omitting it breaks *all* AI TB loads): `renju_swaps SMALLINT UNSIGNED NULL`, `renju_offers VARBINARY(10) NULL`.
  - `pente_game`: `renju_swaps SMALLINT UNSIGNED NULL`.
  - new table `pente_renju_offer (gid, site_id, offer_num, move)` for completed-live offers (write path deferred with live work).
- **`TBGame.java`**: fields `renjuSwaps` (packed) + `renjuOffers` (`int[]`); accessors; mutators `renjuSwap(boolean)` (sets the digit by move count, swaps `p1_pid`↔`p2_pid` on yes — like `dPenteSwap`) and `renjuBranch(boolean)`; **`getRenjuPhase()`** (derived, shared with all views — see §2.6). Serializable, so the fields ride the Redis `TBSet` aggregate.
- **`MySQLTBGameStorer.java`**: `TB_COLUMNS`/`fillGame` read the columns; new update methods `renjuSwap`/`renjuBranch`/`renjuOffers` (mirror `dPenteSwap`/`swap2Pass`).
- **`CacheTBStorer.java`**: cache overrides for those methods (mutate aggregate + write-through); **`storeNewMove` validates Renju moves via `RenjuState.reconstruct`** (decision-aware) and accepts the Branch-B selection (which `isValidMove` blocks by design); auto-first-move uses `getCenterMove`.
- **`TBGameStorer.java`** interface + test `InMemoryTBGameStorer` get the new methods.
- **`InvalidMoveException extends TBStoreException`** — thrown on validation failure so `MoveServlet` shows **"Invalid move"** instead of the generic "Database error".

### 2.4 Move-submission protocol — `MoveServlet` (THE wire contract clients must speak)
Request param **`renjuAction`** (alongside `command=move&gid=…&moves=…`). The server derives the pending decision from `RenjuState.reconstruct` and guards each action against it:

| `renjuAction` | `moves` payload | meaning |
|---|---|---|
| `swap` | `1` | take over opponent's side (no stone) |
| `swap` | `0,<move>` | decline + play the next opening stone (bundled) — for the move-1..3 swap windows |
| `move4` | `<d>,<s1>[,…,s10]` | move-4 decision. `d`=1 if declining the swap (SWAP phase), 0 if swap already taken (BRANCH phase). Then **1 stone = Branch A (move 5, must be 9×9)** or **10 stones = Branch B offers** |
| `select` | `<move>` | white picks one of the 10 offered moves (becomes move 5) |

Notes: declining the swap **after move 4** does NOT bundle a stone (the branch choice comes next). The server validates everything authoritatively (central squares, forbidden points, offer symmetry/distinctness via `offerFifthMove`); client checks are UX only.

### 2.5 The hardcoded-`180` gotcha (board-aware center)
Every game's auto-placed opening center stone was historically the literal `180` (= 19×19 center, `9 + 9·19`). Every forced-center game was 19×19, so it was always right — until Renju (15×15, center `112 = 7 + 7·15`). Fixed by routing all of them through `GridStateFactory.getCenterMove(game)`:
- `CacheTBStorer` (TB auto-first-move, 3 sites).
- `MySQLPenteGameStorer.loadGame` (historic games re-add the *implicit* center on load — gated by `firstMoveCanBeOffCenter`; the center is **not stored**, so this corrects already-saved games with no migration).
- `ServerTable` (live game auto-first-move).
- **Left alone on purpose:** ~30 other `addMove(180)` in Pente-only infrastructure (AI opening tree `tree/*`/`FastPenteState*`/`MarksAIPlayer`, the Pente game-database/analysis/import tools `gameDatabase/*`, `IYTGameFilter`) — Renju never traverses those; `HttpGameStorer:61` is commented-out dead code.

### 2.6 `getRenjuPhase()` — the single source of opening state for all views
`TBGame.getRenjuPhase()` reconstructs once and returns one of `SWAP` / `BRANCH` / `OFFERS` / `SELECTION` / `MOVE` (place the next central-square stone) / `COMPLETE` (normal play), or `null` for non-Renju. Both the HTML view and the JSON endpoint consume it (no duplicated logic). Exposed in JSON via `GameResponse` fields `renjuPhase` / `renjuOffers` / `renjuSwaps`.

---

## 3. Web/mobile client (the reference implementation to port from)

Board rendering is shared across the canvas viewers via two files; teach them about the game **once**:
- **`gameServer/gameConstants.jspf`** — add `GAME.RENJU/SPEED_RENJU/TB_RENJU` (emitted from `GridStateFactory`). Without these the `switch(game)` in `boardCommon.js` can't match → "unknown game id 81" thrown → board never renders.
- **`gameServer/js/boardCommon.js`** — `getBoardColor` (board wood color) and `replayMoves` (replay dispatch) must handle the new ids. Renju → `gomokuColor` for the board, but its **own** `replayRenjuGame` (see next).
- **`gameServer/tb/gameScript.js`**:
  - `replayRenjuGame` — **black first**: `color = 2 - (i % 2)` (drawStone renders value **2 = black, 1 = white**; gomoku's `1 + (i%2)` is white-first). Use `% gridSize` / `/ gridSize`, **never hardcoded 19** (a `%19` move on a 15-board lands outside the `0..gridSize-1` cells `drawGame()` iterates → invisible stone).
  - `drawGrid` star points — route Renju to the gridSize-aware branch (`c = floor(gridSize/2)`, `l = 3`, `r = gridSize-1-3` → 3/7/11 for 15×15) instead of the hardcoded-19 default.
- **`gameServer/tb/mobileGame.jsp`** (turn-based play — the full opening UI):
  - `gridSize = 15` for `TB_RENJU`.
  - Expose to JS: `isRenju`, `renjuPhase` (`game.getRenjuPhase()`), `renjuOfferedMoves` (persisted offers), `renjuOfferList` (client picks).
  - Phase-driven controls (mirrors the dPente/swap2 button block): swap windows → "Swap (take over)" / "Don't swap" (decline bundles the next stone); **move 4** → "Swap" or **"Don't swap or place 10"** (branch inferred from stone count: 1 = continue, 10 = offer; alert if neither, or if a lone continue stone is outside 9×9); selection → "Choose this 5th move".
  - **Central-square hinting** during `MOVE` *and* `SWAP` phases (the bundled decline-stone is placed in SWAP phase); radius by move number (0/1/2/3/4).
  - **Multi-pick** picker for the move-4 / offers (tap to add up to 10, tap again to remove, counter `n/10`).
  - **Symmetry dedup of offers, client-side** — JS port of `SimpleGridState.rotateMove` + the position stabilizer (`renjuRotate`/`renjuStabilizer`/`renjuIsSymmetricDup`); must agree exactly with the server's `offerFifthMove`.
  - Stone rendering for picks: **1 placed = solid black** (`drawStone(.,.,2)`); **2+ = translucent black** (`drawDeadStone`, the Go dead-stone look). Selection: picked candidate solid, the rest translucent.
  - Submit via `renjuPost(action, moveStr)` → the §2.4 wire contract.
- **`gameServer/viewLiveGameMobile.jsp` / `viewLiveGameEmbed.jsp`** (read-only replay) — only need `gridSize = 15`; the shared JS does the rest. (The Java-applet `viewLiveGame.jsp` was left untouched.)
- **`org.pente.gameServer.mobile.GameResponse`** (JSON, Gson) — `renjuPhase`/`renjuOffers`/`renjuSwaps` fields for native clients.

---

## 4. Porting checklist for iOS / React / Android

The server is authoritative; clients render + drive the opening. Each client needs:

**Board & rendering**
- [ ] Renju board size **15×15** (game ids 31/32/81 → 15; don't assume 19). Move encoding **`x + y·15`**.
- [ ] **Black plays first**; color by move parity. Offer candidates shown translucent (1 placed = solid).
- [ ] Star points at **3/7/11** (center 7); coordinate labels **A–P skipping I** (first 15 of the standard set).

**Opening flow (Taraguchi-10)** — read the current phase from the server (`renjuPhase` in JSON / `getRenjuPhase`):
- [ ] Central-square limits: move 1 center, 2 ∈ 3×3, 3 ∈ 5×5, 4 ∈ 7×7, Branch-A move 5 ∈ 9×9 (radii 0/1/2/3/4 from center).
- [ ] Swap windows after moves 1–4: **swap** (no stone) or **decline + play next stone** (bundled). Declining after move 4 → branch step (no stone).
- [ ] Move-4 branch by **stone count**: 1 = continue (Branch A move 5, 9×9), 10 = offer (Branch B). Reject counts ≠ {1,10}.
- [ ] 10-offer picker with **symmetry dedup** (port `rotateMove` + position stabilizer; or just let the server reject and surface the error).
- [ ] Selection screen for white to pick one of the 10 offered moves.

**Protocol**
- [ ] Speak the §2.4 contract (the `renjuAction` values + `moves` payloads) to `MoveServlet` / the live server. The JSON read endpoint is `gameServer/mobile/json/game.jsp` (`GameResponse`); moves POST to `/gameServer/tb/game`.
- [ ] Handle the server's **"Invalid move"** vs other errors.

**Win / forbidden**
- [ ] Win display: black exact-5, white 5+.
- [ ] Forbidden points are **server-enforced** (rejected on submit). Client-side marking is **optional/deferred** — if you add it, fetch from the server (`getForbiddenPoints`); do **not** re-implement the finder.

---

## 5. Generalized "add a new game" playbook

Ordered, with the Renju example in parentheses. Most steps are *array/registration* edits; the engine + UI are the real work.

1. **Rules engine** (`org.pente.game`): a `GridState`/`GridStateDecorator` subclass encoding win + legality (`RenjuState`). Reuse helpers where possible (`SimpleGomokuState`, the forbidden-point finder). Add a `getInstance(MoveData)` override so factory reconstruction keeps the type. Unit-test it (JUnit 3, `org/pente/game/test`).
2. **Board size**: if not 19×19, add `getCenterMove` coverage (automatic — derived from the state) and `gridSize` branches in the JSPs + `gameScript.js`. Audit for hardcoded `19`/`180`/`361`.
3. **Register in `GridStateFactory`**: id constants (odd normal / +1 speed / +50 TB), all the arrays (mind `allGames` is **id-indexed**), the `createGridState` switch, `isValidGame`/`getMaxGameId`. Boot game_event/offerings then pick it up automatically.
4. **Persistence** (only if the game has extra per-game state beyond moves): schema columns/side-tables (+ migration) on **`tb_game` AND `tb_game_ai`** and `pente_game`; `TBGame` fields + accessors; `MySQLTBGameStorer` read/write + decision-update methods; `CacheTBStorer` overrides + reconstruct-based validation; a `reconstruct(...)` on the engine. Encode resumable opening state so "pending" is representable (Renju used base-3).
5. **Protocol** (`MoveServlet`): handle any non-ordinary opening actions; throw `InvalidMoveException` for rejects.
6. **Shared board JS**: `gameConstants.jspf` GAME ids; `boardCommon.js` `getBoardColor` + `replayMoves` dispatch; `gameScript.js` replay (correct first-stone color, `gridSize`, star points).
7. **The game JSPs**: `mobileGame.jsp` (gridSize + any opening UI), `viewLiveGameMobile/Embed` (gridSize), `GameResponse` JSON.
8. **Live path** (`ServerTable`) if the game is offered live.

### Cross-cutting gotchas (bit us on Renju)
- `allGames[]` is **indexed by game id** — insert new live ids at their numeric index, not appended.
- **`tb_game_ai`** must get the same columns as `tb_game` (storer reads `TB_COLUMNS` from both).
- Hardcoded **`180`** opening-center is everywhere; use `getCenterMove`. Hardcoded **`19`/`%19`** in board JS breaks non-19 boards.
- **Deploy/iterate locally:** `gameServer` (JSPs) and `deployClasses/org` (compiled classes) are volume-mounted into the `penteorg-pente.org-1` container. JSP edits are live on reload; **static `.js` needs a browser hard-refresh** (cache); **Java edits need `./justCompile` + `docker restart penteorg-pente.org-1`** to reload classes (Tomcat caches them, and static-`include` JSPF changes need the restart too). `ant test-one` needs `JAVA_HOME=/opt/homebrew/opt/openjdk@21/...`. The self-signed `CN=localhost` cert blocks the Playwright MCP browser. DB: `docker exec penteorg-main_db-1 mariadb -uroot -p'<root pw from .env>' dsg`.

---

## 6. File-by-file change map (this branch vs `main`)

**New (engine/codec/persistence):** `RenjuForbiddenPointFinder.java`, `RenjuState.java`, `RenjuOpeningState.java`, `InvalidMoveException.java`, `dsg_src/sql/2026-06-14-renju-opening-state.sql`. Tests: `RenjuForbiddenPointFinderTest`, `RenjuStateTest`, `RenjuOpeningStateTest`, `RenjuReconstructTest`, `RenjuFactoryTest`, `TBGameRenjuPhaseTest`.
**Modified (Java):** `GridStateFactory`, `MySQLPenteGameStorer`, `GameResponse`, `ServerTable`, `CacheTBStorer`, `MySQLTBGameStorer`, `TBGame`, `TBGameStorer`, `InMemoryTBGameStorer`, `MoveServlet`.
**Modified (web):** `gameConstants.jspf`, `js/boardCommon.js`, `tb/gameScript.js`, `tb/mobileGame.jsp`, `viewLiveGameMobile.jsp`, `viewLiveGameEmbed.jsp`, `sql/schema.sql`.

Design/plan docs: `docs/superpowers/specs/2026-06-13-renju-state-design.md`, `…/2026-06-13-renju-tb-persistence-design.md`, `…/2026-06-14-renju-tb-mobile-ui-design.md` (+ matching plans).

Full commit list (oldest→newest) on `feat/renju`: from `b279564` (design spec) through `338822d` (live auto-center). Notable functional commits: `0facc7b` finder port, `c25c887` factory registration, `8be4a99`/`394e707` codec + reconstruct, `0b37c7c`/`df8dc78` storers, `9d9088a` MoveServlet routing, `8c389b0` getRenjuPhase, `f0278b3` move-4 branch-by-count, `58df25c` client symmetry dedup, `0eaa37b`/`02454d3`/`338822d` board-aware center.

---

## 7. Status: archival persistence + live opening routing done

### Done — archival persistence (sub-project 1: `pente_game` write/read/expose)

Completed Renju games — **TB-archived and live** — now persist their Taraguchi-10 opening record into `pente_game` / `pente_renju_offer`, load it back into `GameData`, and expose it in the historic JSON endpoint. This **closes the prior confirmed gap** (an archived Branch-B game used to lose its 10 offers + swap/branch record: `pente_game.renju_swaps` stayed NULL and `pente_renju_offer` got no rows).

- **`GameData` / `DefaultGameData`** now carry nullable `Integer renjuSwaps` + `int[] renjuOffers`, added as **interface default methods** on `GameData` (all implementors compile unchanged; `null` ⇒ non-Renju).
- **`RenjuState.getRenjuSwapsPacked()`** encodes the *resolved* opening decisions for archival, carrying a `swapResolved[]` flag so a decided-NO is distinguishable from a not-yet-decided (pending) swap window.
- Both archival builders carry the fields **only when the game is Renju**:
  - **TB:** `CacheTBStorer.storeGameDSG` + `TBGame.convertToGameData`, guarded on `game == TB_RENJU`.
  - **Live:** `ServerTable.getGameData(...)`, guarded on `gridState instanceof RenjuState`.
- **`MySQLPenteGameStorer.storeGame` / `loadGame`** write/read `pente_game.renju_swaps` (nullable `SMALLINT`, `wasNull` on load) plus the `pente_renju_offer(gid, site_id, offer_num, move)` rows.
- **`GameResponse.buildHistoric`** emits `renjuOffers` (comma-separated, **same format as the active `build(...)` path**) + `renjuSwaps` in the JSON.
- **Manual DB round-trip (QA — pending; DB-coupled, not unit-testable in this repo):** complete a Branch-B TB Renju game → `select * from pente_renju_offer where gid=<gid>` returns the offered-move rows and `pente_game.renju_swaps` is set for that gid → load it via the historic JSON endpoint / a viewer → confirm `renjuOffers` and `renjuSwaps` appear in the response.
- **KNOWN LIMITATION (still open):** only the single-game `MySQLPenteGameStorer.loadGame` reads `renju_swaps`/offers — that is the path the historic JSON endpoint uses. The bulk-load path `MySQLPenteGameStorer.loadGames` (game-list / history bulk query) does **not** read them; a future feature needing Renju opening data in the bulk list must give `loadGames` the same treatment.

### Done — live opening routing (sub-project 2: `ServerTable` Taraguchi-10)

Live (WebSocket + raw-TCP) Renju games now drive the full Taraguchi-10 opening server-side — the per-window swap, the Branch-A decline+move-5, the Branch-B ten offers, and the selection — keeping the seat↔color binding correct across swaps. This **closes the prior gap** where `ServerTable` only auto-centered and special-cased the Pente swap2 pass.

- **Three inbound events** — `DSGRenjuTaraguchiSwapTableEvent` (`boolean swap`, `int move`), `DSGRenjuTaraguchiOffer10TableEvent` (`int[] moves`), `DSGRenjuTaraguchi10Select1TableEvent` (`int move`) — extend `AbstractDSGTableEvent` and are registered **once** in `DSGEventWrapper` (one field + getter/setter each). The Gson codec reflects over the declared fields, so this single registration serves **both** transports (TCP socket **and** WebSocket — shared codec).
- **Three `SynchronizedServerTable.callServerTable` dispatch arms** route each event to its handler.
- **Three `ServerTable` handlers** — `handleRenjuSwap` / `handleRenjuOffer10` / `handleRenjuSelect1` — mirror the proven `handleMove`/`handleSwap`/`handleSwap2Pass` error-accumulate / single-emit pattern: validate (Renju + seat + phase + turn), mutate nothing on failure, emit `DSGMoveTableErrorEvent` to the sender.
  - `handleRenjuSwap`: on `swap=true` swaps both seat arrays + timers exactly like `handleSwap`; on `swap=false` commits the decline and (for the move-2/3/4 + Branch-A windows) places the bundled stone via `handleMove`.
  - `handleRenjuOffer10`: declines the move-4 swap, `chooseBranch(true)`, commits the ten atomically, hands the turn/timer to the selector (mirrors `handleSwap2Pass`).
  - `handleRenjuSelect1`: commits the chosen candidate as move 5 and places it via `broadcastRenjuFifthMove`.
- **Two `RenjuState` additions** — `wouldAcceptDeclinedOpeningMove(...)` (pre-validate the bundled stone before committing the decline) and `offerFifthMoves(int[])` (atomic **validate-all / commit-none** wrapper over `offerFifthMove`; unit-tested in `RenjuReconstructTest`).
- **Decision-only echoes; stones ride `DSGMoveTableEvent`.** The three echoes carry only the decision; the stone (if any) is placed exactly once — swap+move via `handleMove`, select1 via `broadcastRenjuFifthMove` (which reproduces `handleMove`'s post-move tail for **BOTH** the player-changed and the same-player branches).
- **Join re-send** — `handleJoin` re-sends the ten offers to a client that (re)joins while Branch-B selection is still pending.

**KNOWN NOTES (for sub-project 3 — the React client — and future games):**
- **Rejoin/spectate swap state:** on (re)join `handleJoin` sends the current seats (`sendPlayingPlayers`) PLUS a silent `DSGSwapSeatsTableEvent` when `RenjuState.isNetSwapped()` (parity of swap decisions) — the client must handle it like the dPente silent swap: set the swap-state flag only, do **not** re-animate or double-swap on top of the already-current seats.
- **Decision-echo contract:** clients must **NOT** place stones from the swap/offer/select echo events; stones arrive as `DSGMoveTableEvent`.
- **Recovery contracts:** if a declined-swap's bundled stone is rejected, the swap-decline is **already committed** — the client recovers by sending the stone as a plain move (the swap window is consumed). If an offer10's ten are rejected, the move-4 decline + Branch-B are **already committed** — the client recovers by re-sending a corrected ten (the handler re-accepts via the `isAwaitingFifthOffers` guard).
- **Branch-A move-5 phase:** Branch A move-5 is sent as a swap-event (`swap=false`, `move5`) by the to-move side in **BOTH** the move-4 swap-decline and post-swap (`isAwaitingBranchChoice`) states; `swap=true` is valid only while the swap window is open (rejected with `INVALID_MOVE` in the branch-choice state); windows 1–3 post-swap place the next opening stone as a plain `DSGMoveTableEvent`.
- **Echo recipient:** echoes currently use `broadcastMainRoom` (mirrors `handleSwap`/`handleSwap2Pass`); verify opponent/spectator receipt in the manual round-trip and switch the three echoes to `broadcastTable` if exact recipient parity is needed.
- **Known minor:** the three handlers use `state != GAME_IN_PROGRESS` as a single catch-all, so a disconnect-mid-opening surfaces `NO_GAME_IN_PROGRESS` rather than `GAME_WAITING_FOR_PLAYER_TO_RETURN` — cosmetic error code only, no logic impact.
- **Manual WS round-trip (QA — pending; DB/transport-coupled, no `ServerTable` unit-test harness exists):** restart the backend, then drive a live Renju game over two sessions and confirm — (a) **swap window:** swap (seats swap, no stone), and decline+move (echo + one `DSGMoveTableEvent`); (b) **Branch A:** decline+move5 (single `DSGMoveTableEvent`); (c) **Branch B:** offer10 → select1 (move 5 placed once, other nine clear); (d) a **rejoin/spectator mid-selection** receives the ten; (e) the **OPPONENT actually receives each decision echo** (sent via `broadcastMainRoom`) — if not, switch the three echoes to `broadcastTable`; (f) **error events** (`DSGMoveTableErrorEvent`) reach the sender on an illegal swap/offer/select.

### Still deferred

- **React `react_live_game_room` opening UI (sub-project 3)** — not started.
- **Viewer rendering of the offer phase during historic replay** — the offers/swaps are now persisted + exposed in JSON, but the historic viewers don't yet render the offer/selection phase.
- **Forbidden-point marking** in any client — deferred (server-enforced only). Add via `getForbiddenPoints` → expose like `renjuOffers` → mark; don't port the finder.
- **React / iOS / Android** clients — not started (this guide is their input).
- **AI** for Renju — none.
