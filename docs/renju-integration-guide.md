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

**Opening flow (Taraguchi-10)** — phase source depends on the transport:
- **Historic / turn-based JSON** (`GameResponse`): the server ships the phase in the `renjuPhase` field (derived once by `TBGame.getRenjuPhase()`, §2.6). Read it directly.
- **Live WebSocket / raw-TCP** (`ServerTable`): **NO `renjuPhase` is sent.** The live path emits only the three decision echoes + `DSGMoveTableEvent`s; the engine is server-side only. The live client must **derive** the phase from the tracked opening state — see the derivation table in §8.2. Do **not** wait for a `renjuPhase` field on the socket; it never arrives.

The phase, however obtained, gates the same controls:
- [ ] Central-square limits: move 1 center, 2 ∈ 3×3, 3 ∈ 5×5, 4 ∈ 7×7, Branch-A move 5 ∈ 9×9 (radii 0/1/2/3/4 from center).
- [ ] Swap windows after moves 1–4: **swap** (no stone) or **decline + play next stone** (bundled). Declining after move 4 → branch step (no stone) (TB servlet path; the live client bundles the move-5 stone with the decline — see §8.2).
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

- **React `react_live_game_room` opening UI (sub-project 3)** — not started in code, but **fully scoped in §8** (grounded zero-context handoff: anchors, live phase derivation, file-by-file map, wire examples).
- **Viewer rendering of the offer phase during historic replay** — the offers/swaps are now persisted + exposed in JSON, but the historic viewers don't yet render the offer/selection phase.
- **Forbidden-point marking** in any client — deferred (server-enforced only). Add via `getForbiddenPoints` → expose like `renjuOffers` → mark; don't port the finder.
- **React / iOS / Android** clients — not started (this guide is their input).
- **AI** for Renju — none.

---

## 8. Sub-project 3 — React (`react_live_game_room`) Taraguchi-10 handoff

Zero-context handoff for a fresh agent implementing the live Renju opening UI in the
`react_live_game_room` submodule (a **separate repo** — do not edit it from this one). Every
anchor below was read from the submodule on this branch; line numbers are as-of-now and may
drift, so grep the symbol. Anchors were **re-grounded against the Protocol-module refactor**:
the inbound decode/dispatch seam now lives in `src/protocol/middleware.js` and the whole `dsg*`
wire schema is a deep module behind the `Protocol` facade `src/protocol/index.js` (consumers
`import {Commands} from '../../protocol'`); the reducer's `EVENT_HANDLERS` registry **stayed**
in `src/redux_reducers/rootReducer.js` (it did NOT move into the protocol module). The live
client gets **NO `renjuPhase`** (that field is JSON/TB only, §2.6 / the §4 fix) — it
**derives** the opening phase from tracked echo state (§8.2).

### 8.0 Board basics (restated for this client)
- Board **15×15**, game ids **31 (Renju) / 32 (Speed Renju) / 81 (TB Renju)**. Move encoding `x + y·15`; **center = 112** (`7 + 7·15`), server auto-places it as move 1.
- **Black plays first.** React stone-color convention (`Components/Board/Board.js:45-48`): for **non-Go** boards `player_colors = [undefined, 'white-stone-gradient', 'black-stone-gradient']` — i.e. **board value 1 = white, value 2 = black** (only `game.isGo()` flips it). So "black first" ⇒ the first stone must carry abstractBoard **value 2**.

### 8.1 Confirmed anchors (file : symbol)
All verified in the submodule; treat as fact unless marked **(verify)**.

| Area | File | Symbol / fact |
|---|---|---|
| grid size | `src/game/boardGeometry.js` | `gridSizeForGame(gameId)`: `21/22→9`, `23/24→13`, **else 19**. Renju 31/32/81 fall through → **19 (WRONG)**. |
| variant key | `src/game/boardGeometry.js` | `variantKey(gameId)`: range chain ending `if (gameId<29) return …; return 'swap2-keryo'`. Renju 31/32 → **`'swap2-keryo'` (WRONG)**. `boardStyleClass = variantKey` (CSS class = key). |
| picker list | `src/game/boardGeometry.js` | `STANDARD_GAME_IDS = [1,3,5,7,9,11,13,15,17,19,21,23,25,27,29]` — **31 absent** (Renju not offered in the dropdown). |
| markers | `src/game/boardGeometry.js` | `boardSpecialPoints(gameId)`: non-Go → `CIRCLES=[120,126,180,234,240]` part **51**; Go → `GO_DOTS[gridSize]` part **52**. Renju → non-Go branch → **circles at 19×19 positions (WRONG)**. `isGoBoard = gameId>18 && gameId<25`. |
| opening FSM | `src/game/openingPhase.js` | `swap2Phase(movesLength, swap2State)` + `Swap2Phase` enum; `dPentePhase(movesLength, dPenteState)` + `DPentePhase` enum; derived `swap2OpeningPlayer`/`dPenteOpeningPlayer`/`isSwap2Choice`/`isSwap2CanPass`/`isDPenteChoice`. **Pure over primitives** (count, sub-state, started). Mirror these for Renju. |
| state enums | `src/game/gameState.js` | `GameState.{State, DPenteState, Swap2State, GoState}`. `DPenteState={NO_CHOICE:0,SWAPPED:1,NOT_SWAPPED:2}`; `Swap2State` adds `SWAP2PASS:3`. |
| swap reducer | `src/redux_reducers/utils.js` | `swapSeats(data, state)`: if `!data.silent && data.swap` → `table.swap()` (visual seat swap); **then always** sets `dPenteState`/`swap2State` from `data.swapped`. The **silent branch** (`data.silent` true) **skips the visual swap** but still sets the swap-state flag. Also `swap2Pass(data,state)` and `addMove(data,state)` (places stones from `data.moves`/`data.move`). |
| variant rules | `src/Classes/GameClass.js` | `VARIANT_RULES` keyed by `variantKey`; entry `{replay, disableRatedOnReplay, add, goMove, player, postRule}`. **No `'renju'` key.** `replayGame` maps `rules.replay`→`#replay*Game` via an inline dispatch object; `#applyMove(rules,…)` maps `rules.add`→`#add*Move` (move engine) then applies `rules.postRule`; `rules.player` selects the per-move color parity (only `'connect6'` is special-cased — see "replay colors"). |
| replay colors | `src/Classes/GameClass.js` | `#replayGomokuGame`: `color = 1 + (i % 2)` (**white-first**) and **hardcoded `% 19` / `/ 19`** (bug on 15×15). Per-move color is computed inline as `rules.player === 'connect6' ? <c6> : <std>` then applied via `#applyMove`: `addMoveFromList(i)` std-arm `1 + (i%2)`; `addMove(move)` std-arm `2 - (moves.length % 2)`. `currentColor()` branches only on `isConnect6()`, else `1 + (moves.length % 2)`. |
| variant guards | `src/Classes/GameClass.js` | `#isDPente`={7,8,17,18}; `#isSwap2`={27,28,29,30}; `isConnect6`={13,14}; `isGo`=`isGoBoard`. `setGame(game)` sets `this.gridSize = gridSizeForGame(game)`. |
| event registry | `src/redux_reducers/rootReducer.js` | `EVENT_HANDLERS` map: `dsg<X>Event → (p,s)=>reducer(p,s)`. Existing: `dsgMoveTableEvent→addMove`, `dsgSwapSeatsTableEvent→swapSeats`, `dsgSwap2PassTableEvent→swap2Pass`. Applied in the reducer's `default:` arm on the typed action that `protocol/middleware.js` dispatches. **The registry stayed here — it did NOT move into the protocol module.** |
| inbound seam | `src/protocol/middleware.js` | `protocolMiddleware`: on a redux-websocket `…MESSAGE` action calls `decode()`, answers `dsgPingEvent` (echo), then `next({type, payload})` into the reducer; dev-logs + drops malformed/unknown. Replaced the old reducer `JSON.parse`-switch + pinger. |
| inbound decode | `src/protocol/decode.js` | `decode(action)`: frame is `{ "<eventType>": {payload} }`; **`type = Object.keys(json)[0]`**, must be in `INBOUND_TYPES`, `req` fields validated. Returns `{ok:true, event:{type, payload}}`. |
| outbound build | `src/protocol/messages.js` + `commands.js` (facade `src/protocol/index.js`) | `MESSAGES[type] = {dir, cmd, out:[fields], req:[fields]}` (`req: TBL=['table']` for table events). `buildCommand(type, out, args)` → `{ [type]: { ...args, ...DEFAULTS } }`, `DEFAULTS = { time: 0 }` — **`time:0` is auto-stamped on every outbound command** (do not list it in `out`). `COMMANDS` derives `cmd → {type, out}`; `Commands.<cmd>(args)`. `INBOUND_TYPES` = MESSAGES keys (`dir!=='out'`) + `ERROR_EVENTS` + `PING`. Consumers `import {Commands} from '../../protocol'`. |
| choice modals | `src/Components/Table/{Swap2,DPente}ChoiceModal.js` | Plain MUI `<Modal open={table.mySwap2Choice(game)}>` (resp. `myDPenteChoice`); `import {Commands} from '../../protocol'`; buttons `send_message(Commands.swapSeats({swap, silent:false, player:table.me, table:table.table}))` / `Commands.swap2Pass({silent:false, player, table})`. **Yes/no only — no board interaction.** Mounted in `src/Pages/Table.js` (`<DPenteChoiceModal/>`, `<Swap2ChoiceModal/>`). |
| table helpers | `src/Classes/TableClass.js` | `seats = [undefined, seat1, seat2]`; `swap()` = `seats = [undefined, seats[2], seats[1]]`; `isMyTurn(game) = state===STARTED && me===seats[game.currentPlayer()]`; `mySwap2Choice = isMyTurn && game.swap2Choice()`. |
| wrapper keys | `…/event/DSGEventWrapper.java` | Fields (→ JSON keys via Gson): **`dsgRenjuTaraguchiSwapTableEvent`**, **`dsgRenjuTaraguchiOffer10TableEvent`**, **`dsgRenjuTaraguchi10Select1TableEvent`**. `getJSON()` uses a `GsonBuilder` **without `serializeNulls()`** → nulls omitted → single top-level key per frame (same path the existing `dsgMoveTableEvent` already round-trips). |
| event fields | `…/event/DSGRenjuTaraguchi*.java` | `…Swap`: `boolean swap`, `int move`. `…Offer10`: `int[] moves`. `…Select1`: `int move`. All `extends AbstractDSGTableEvent` → inherited `String player`, `int table`; `AbstractDSGEvent` → `long time`. So each inner JSON object carries `player`, `table`, `time` plus its own fields. |

**Could NOT confirm from code (state as "verify" in any implementation):**
- The exact `move` sentinel sent on **`swap=true`** (take-over, no stone). The field is a plain `int`; the server `handleRenjuSwap` ignores it on `swap=true`. Send `0` and verify against `ServerTable.handleRenjuSwap`.
- Whether a per-variant `currentPlayer()` opening branch is strictly required for correct `isMyTurn` during the Renju opening (a `renjuOpeningPlayer` mirroring `swap2OpeningPlayer` is the safe move). **(verify)**
- Reuse of the Go **dead-stone** render path for translucent candidates is now **confirmed**: `Board.js` sets `board[s].deadStone = player_colors[i]` (today only inside the `game.isGo()` block); `BoardSquare.js` renders it as `SimpleStone({size, id: deadStone, opacity: 0.6})`; both `Stone.js`/`SimpleStone.js` accept an `opacity` prop → SVG `fillOpacity`. So the translucent primitive works for any board — the only change is to set `board[s].deadStone` for Renju candidates outside the Go-only block.

### 8.2 Live phase derivation (mirror `swap2Phase`/`dPentePhase`)
Add `renjuPhase(movesLength, renjuState)` to `openingPhase.js` (pure, mirroring the existing
classifiers). The engine is **not** client-side, so `renjuState` is the *tracked* opening
record the client accumulates **from the echo events** (§8.4), not a computed game state:

```
renjuState = {
  swapWindowOpen,   // bool — is the current swap window still undecided?
  netSwapped,       // bool — parity of accepted swaps (who currently owns black)
  branch,           // null | 'A' | 'B' — set by the move-4 decision echoes
  offers,           // int[] | null — the 10 Branch-B candidates (offer10 echo)
  selection,        // int | null — white's pick (select1 echo)
}
```

`movesLength = game.moves.length` (stones on board, incl. the auto-center = move 1).

| movesLength | tracked state | phase | to-move acts |
|---|---|---|---|
| 1 | swapWindowOpen | `SWAP` (window 1) | Swap, **or** decline + place move 2 ∈ 3×3 |
| 2 | swapWindowOpen | `SWAP` (window 2) | Swap, **or** decline + place move 3 ∈ 5×5 |
| 3 | swapWindowOpen | `SWAP` (window 3) | Swap, **or** decline + place move 4 ∈ 7×7 |
| **4** | **swapWindowOpen** | **`SWAP`** (window 4) | THREE actions: `swap=true` take-over → **`BRANCH`** (no stone) · `swap=false` **bundled with move 5** → **Branch A** · `Offer10` → **Branch B** (see note ↓) |
| **4** | swap decided, `branch===null` | **`BRANCH`** | Branch A: place move 5 ∈ 9×9 · Branch B: offer 10 |
| **4** | `branch==='B'`, `offers` present | **`SELECTION`** | white picks 1 of the 10 → becomes move 5 |
| **5** | `branch==='A'`, swap-5 undecided | **`SWAP`** (window 5) | Swap, **or** decline → then move 6 |
| **5** | `branch==='A'`, swap-5 decided | `NORMAL` (move 6 anywhere) | place move 6 |
| **5** | `branch==='B'` (selection done) | `NORMAL` (move 6 anywhere) | place move 6 — **no swap-5 window in Branch B** |
| ≥6 | — | `COMPLETE` / `NORMAL` | plain alternation; black forbidden-points **server-enforced** |

**Move-4 model (live path).** At the move-4 swap window the to-move player has **three** wire
actions: (a) `swap=true` = take-over → enters the standalone **`BRANCH`** state (no stone
placed); (b) `swap=false` **bundled with move 5 in the 9×9** = **Branch A** (advances to 5
moves) — there is **no** stoneless move-4 decline; (c) `Offer10` = **Branch B**. The standalone
`BRANCH` state therefore arises **only** after a `swap=true` take-over (then that player chooses
Branch A via a `swap=false`+move5 event, or Branch B via `Offer10`). Grounds:
`ServerTable.handleRenjuSwap` (bundled decline + `chooseBranch(false)`),
`RenjuState.wouldAcceptDeclinedOpeningMove` (rejects a stoneless/invalid move-4 decline).

The two ambiguous lengths are resolved exactly as the task requires: **at `movesLength==4`**
the open swap window, the swap-decided/no-branch state, and the branch-B/offers-present state
disambiguate `SWAP` / `BRANCH` / `SELECTION`; **at `movesLength==5`** the **tracked `branch`**
distinguishes Branch A (swap-5 window, then move 6) from Branch B (move 6 directly). Branch-A
move 5 itself arrives as a **swap event** (`swap=false`, with the move) per §7's decision-echo
notes, not as a branch event.

**Alternative considered:** port `RenjuState`'s server-side phase logic (the Taraguchi-10 state
machine) into the client. **Not recommended** — it duplicates a non-trivial engine and the
forbidden-point finder it leans on. The lightweight derivation above is the recommended path:
it tracks only the four decision variables the echoes already carry.

### 8.3 React file-by-file map
1. **`src/game/boardGeometry.js`** — Renju ids resolve **WRONG** today (see 8.1). Add `31/32/81`:
   - `gridSizeForGame`: return `15` for `31||32||81`.
   - `variantKey`: insert a `'renju'` branch (the new id range) **before** the `'swap2-keryo'` fallthrough; add the matching `.renju` board CSS class (key doubles as class).
   - `STANDARD_GAME_IDS`: add `31` so the picker can offer Renju.
   - `boardSpecialPoints`: add a Renju branch returning the **9 star points at cols/rows {3,7,11}** → indices **`[48,52,56,108,112,116,168,172,176]`** (index `= col + row·15`; center 112). Use star-dot part **52** (the Go-style dot) — the current non-Go circle set is 19×19-specific and wrong here.
2. **`src/Classes/GameClass.js`**
   - `VARIANT_RULES` (the table keyed by `variantKey`): add `'renju': {replay:'renju', disableRatedOnReplay:false, add:'gomoku', goMove:false, player:'renju', postRule:'none'}` (`add:'gomoku'` reuses `#addGomokuMove` via `#applyMove`; `postRule:'none'`).
   - `replayGame`'s inline replay-dispatch object (keyed by `rules.replay`): add `renju: this.#replayRenjuGame`.
   - Add `#replayRenjuGame(until)` = a **black-first** clone of `#replayGomokuGame`: `color = 2 - (i % 2)` (i=0 → value 2 = black) and **`% this.gridSize` / `/ this.gridSize`** (NOT `% 19` — the gomoku copy's hardcoded 19 puts a 15-board move off-grid → invisible stone). This mirrors the JSP `replayRenjuGame` (`2 - (i%2)`) vs gomoku's white-first `1 + (i%2)`.
   - Per-move color now flows through `#applyMove(rules, move, x, y, player)`; the `player` parity is computed inline in `addMoveFromList`/`addMove` as `rules.player === 'connect6' ? <c6> : <std white-first>`. Add a `rules.player === 'renju'` arm = **black-first**: `addMoveFromList` → `2 - (i%2)`; `addMove` → `1 + (this.moves.length % 2)` (both give first stone → value 2). `currentColor()` branches only on `isConnect6()` today — add a Renju arm (`2 - (moves.length % 2)`) so the hover stone (`player_colors[game.currentColor()]`, Board.js) is black-first.
   - `currentPlayer()` does **not** read `rules.player`; it uses the `#isConnect6()`/`#isDPente()`/`#isSwap2()`/`isGo()` guards and calls `dPenteOpeningPlayer`/`swap2OpeningPlayer`. Add `#isRenju = () => game===31||32||81` and a `renjuOpeningPlayer(moves.length, renjuState)` branch (mirror the `#isSwap2()` arm) so `isMyTurn` is right during the opening **(verify exact need)**.
3. **`src/game/openingPhase.js`** — add `RenjuPhase` enum + `renjuPhase(...)` per §8.2, plus a derived `isRenjuSwapChoice` / `isRenjuBranchChoice` / `isRenjuSelection` (pattern: `started && phase===…`), so the UI gating mirrors `isSwap2Choice`.
4. **`src/game/gameState.js`** — add the Renju tracking slice (e.g. `GameState.RenjuState` enum **and/or** the `renjuState` object of §8.2). Initialize it in `GameClass.reset()` next to `dPenteState`/`swap2State`.
5. **`src/redux_reducers/utils.js`**
   - Extend `swapSeats(data, state)`: when the game is Renju, in the **silent branch** (rejoin net-swap, §7) set the Renju net-swap flag **only** — do not animate (the silent `DSGSwapSeatsTableEvent` arrives after seats are already current; double-swapping corrupts the board, exactly the dPente silent-swap contract).
   - Add three reducers `renjuSwap` / `renjuOffer10` / `renjuSelect1` that **update opening-tracking state only and place NO stones** — stones arrive via `addMove` (`DSGMoveTableEvent`). `renjuSwap`: toggle `netSwapped` on `swap===true`, mark the window decided, and (when the bundled stone follows) leave placement to `addMove`; at `movesLength==4`, **any** `swap=false` echo carrying a valid stone ⇒ `branch='A'` (whether the move-4 swap window was still open — i.e. a bundled decline — or already closed by a prior take-over). `renjuOffer10`: `branch='B'`, store `offers = data.moves`. `renjuSelect1`: store `selection = data.move`.
6. **`src/redux_reducers/rootReducer.js`** — register three `EVENT_HANDLERS` entries (keys **must** equal the wrapper keys in 8.1):
   ```js
   dsgRenjuTaraguchiSwapTableEvent:    (p, s) => renjuSwap(p, s),
   dsgRenjuTaraguchiOffer10TableEvent: (p, s) => renjuOffer10(p, s),
   dsgRenjuTaraguchi10Select1TableEvent:(p, s) => renjuSelect1(p, s),
   ```
   This is still the registration point post-refactor (it did **not** move to `protocol/middleware.js`). Decode + dispatch is automatic: once these types are in `MESSAGES`/`INBOUND_TYPES` (step 7), `protocol/middleware.js` decodes each frame and dispatches the typed action, which the reducer's `default:` arm routes through `EVENT_HANDLERS`. The silent net-swap is already routed (`dsgSwapSeatsTableEvent → swapSeats`); just teach `swapSeats` the Renju flag (step 5).
7. **`src/protocol/messages.js`** — add three `MESSAGES` entries (`time` is auto-stamped, omit from `out`; `TBL = ['table']` is defined at the top of the file):
   ```js
   dsgRenjuTaraguchiSwapTableEvent:     { dir:'both', cmd:'renjuSwap',    out:['swap','move','player','table'], req:TBL },
   dsgRenjuTaraguchiOffer10TableEvent:  { dir:'both', cmd:'renjuOffer10',  out:['moves','player','table'],       req:TBL },
   dsgRenjuTaraguchi10Select1TableEvent:{ dir:'both', cmd:'renjuSelect1',  out:['move','player','table'],        req:TBL },
   ```
   `INBOUND_TYPES`/`COMMANDS` regenerate automatically; `Commands.renjuSwap(...)` etc. (reached via `import {Commands} from '../../protocol'`) then build the frames in §8.4. (`cmd` names are client-internal — only the type key travels on the wire.)
8. **New components** under `src/Components/Table/` — the Renju opening UI (§8.6). The existing modals are the *dispatch* pattern to copy (connected, `send_message(Commands.…)`); the *interaction* (board picking, multi-select) is new.

### 8.4 Wire examples (verified keys + fields)
Outbound (client→server) is built by `Commands.<cmd>(args)` → `{ [type]: {...args, time:0} }`.
Inbound (server→client) is the same single-key shape, pretty-printed, with a **server-stamped
non-zero `time`** (epoch ms). One literal per event (table 5, center 112, 15×15):

**Swap event** — take-over, decline+place, or Branch-A move 5 (all use this event):
```json
// outbound: decline window-1 swap + place move 2 at col8,row7 (=113, in 3×3)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": false, "move": 113, "player": "alice", "table": 5, "time": 0 } }
// outbound: take over the side (no stone; move ignored on swap=true — verify sentinel)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": true,  "move": 0,   "player": "bob",   "table": 5, "time": 0 } }
// inbound echo (server time stamped); the stone, if any, arrives separately as dsgMoveTableEvent
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": false, "move": 113, "player": "alice", "table": 5, "time": 1718400000123 } }
```
**Offer 10** (Branch B — black offers ten 5th-move candidates):
```json
{ "dsgRenjuTaraguchiOffer10TableEvent": { "moves": [40,41,42,55,57,70,71,72,160,176], "player": "alice", "table": 5, "time": 0 } }
```
**Select 1** (white picks one of the ten → becomes move 5; placed via a following `dsgMoveTableEvent`):
```json
{ "dsgRenjuTaraguchi10Select1TableEvent": { "move": 57, "player": "bob", "table": 5, "time": 0 } }
```
**Contract reminders (from §7):** never place stones from these three echoes — stones ride
`DSGMoveTableEvent` (`addMove`). On (re)join while Branch-B selection is pending, the server
re-sends the ten via an offer10 frame, plus a **silent** `dsgSwapSeatsTableEvent` if net-swapped.

### 8.5 Offer symmetry dedup (client-side, UX nicety)
The ten Branch-B offers must contain no two D4-symmetric duplicates. The server already rejects
violations via `RenjuState.offerFifthMoves` (→ `offerFifthMove`), so client-side checking is a
**UX nicety** (instant feedback instead of a round-trip error) — recommended, not required.

Algorithm for the 15×15 board (center `(7,7)`):
- For move `m`: `x=m%15`, `y=Math.floor(m/15)`, `dx=x-7`, `dy=y-7`.
- The **8 D4 images** of `(dx,dy)`: rotations `(dx,dy)`,`(-dy,dx)`,`(-dx,-dy)`,`(dy,-dx)` and reflections `(-dx,dy)`,`(dx,-dy)`,`(dy,dx)`,`(-dy,-dx)`. Map each back: `m' = (tx+7) + (ty+7)·15`.
- Reject an offer if **any** of its 8 images equals an already-accepted offer (the orbits collide). Build the running set of all images of accepted offers and test membership.

Reference impl to mirror exactly (so the client agrees with the server): the JSP port
`renjuRotate` / `renjuStabilizer` / `renjuIsSymmetricDup` in `gameServer/tb/mobileGame.jsp`,
itself a JS port of `SimpleGridState.rotateMove` + the position stabilizer (§3).

### 8.6 New UI primitives (no React precedent)
The swap2/dPente modals are plain yes/no (§8.1) — the Renju opening needs board-level
interaction with **no existing analogue** in this client:
- **Central-box highlight** — highlight only the legal cells inside the N×N square about center
  112 for the current opening move: **moves 2/3/4/5 → 3×3 / 5×5 / 7×7 / 9×9** (radius 1/2/3/4).
  Applies during both the `MOVE`/placement phase and the **decline-and-place** action of a `SWAP`
  window (the bundled stone is constrained to the same square).
- **Translucent "dead-stone" candidates** — render the 10 Branch-B offers (and, during
  `SELECTION`, the non-picked nine) as translucent black. The Go **dead-stone** render path is
  the closest existing primitive (`Board.js` `board[s].deadStone`; `Stone`/`SimpleStone` take an
  `opacity` prop) — reuse it if the prop wiring works for a non-Go board **(verify)**; otherwise
  add a translucent variant.
- **10-pick multi-select + submit** — tap to add a candidate, tap again to remove, with an
  `n/10` counter and a submit button. **Validation before send:** exactly **1** stone (and inside
  the 9×9) for Branch A, or exactly **10** distinct, non-D4-duplicate (§8.5) stones for Branch B;
  alert otherwise. Branch is inferred from the count (1 = continue / 10 = offer), matching the
  `MoveServlet`/`ServerTable` contract.

Visual reference (different framework, do not copy code): `gameServer/tb/mobileGame.jsp` and its
board JS — `drawDeadStone`, the central-square hinting by move number, and the multi-pick picker.
