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
- **`gameServer/js/boardCommon.js`** — `getBoardColor` (board wood color) and `replayMoves` (replay dispatch) must handle the new ids. Renju → its **own** `renjuColor` (`#D98880`, dusty rose — **distinct from gomoku**) for the board, and its **own** `replayRenjuGame` (see next).
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
- [ ] Board background colour **`#D98880`** (`renjuColor`, dusty rose) — **distinct from gomoku** (`#A3FDEB`). Canonical across web (`getBoardColor`/`gameScript.js`), react_live (`.renju` + `VARIANT_COLORS`), iOS (`BoardVariantMapping.backgroundColor`, §9), Android (`Table.getGameColor`, §10).

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
- **Rejoin/spectate opening state (current-decision-point signal):** on (re)join `handleJoin` sends the authoritative seats (`sendPlayingPlayers`, unchanged — that conveys *position*) PLUS **exactly one** signal for the **current** decision point. Reaching `numMoves=N` implies windows `1..N-1` are already resolved, so only the current window is in question. The signal table (keyed by `numMoves`) — and the client phase it reconstructs via `RenjuRejoin.decode(numMoves, signal)`:
  - **`numMoves=1..3`** — *nothing* → `SWAP` pending (present the swap choice); *silent `DSGSwapSeatsTableEvent`* → window resolved, `MOVE` (place move n+1).
  - **`numMoves=4`** — *nothing* → `SWAP` pending; *silent swap* → `BRANCH` pending (move-4 window resolved; black picks Branch A vs B); *ten offers* (`DSGRenjuTaraguchiOffer10TableEvent`) → `SELECTION` pending.
  - **`numMoves=5`** — *nothing* → Branch A swap-5 `SWAP` pending; *silent swap* → Branch A swap-5 resolved, `MOVE` (place move 6); *replayed `DSGRenjuTaraguchi10Select1TableEvent`* → Branch B (move 5 was a selection) → `MOVE` (place move 6), **no swap window**.
  - **`numMoves>=6` / opening complete** — *nothing* → `COMPLETE` (normal play).

  Within each `numMoves` the four kinds `{nothing, silent-swap, ten-offers, select1}` are distinct, so `(numMoves, signal)` is **injective** and the client reconstructs the exact server phase. **Seats still come from `sendPlayingPlayers`** — the silent swap event is a *phase marker*, not a seat re-apply: handle it like the dPente silent swap (advance the tracked opening phase for the current window; do **not** re-animate or double-swap on top of the already-current seats). **Its `swap` bit is the CURRENT window's resolved decision, NOT net seat orientation** — `RenjuRejoin.decode` ignores it, and the client must **not** derive who-owns-black from it (seats come only from `sendPlayingPlayers`). The server truth is `RenjuState.getOpeningPhase()` (`SWAP/BRANCH/SELECTION/MOVE/COMPLETE`); the encode/decode contract lives in `RenjuRejoin` and is proven equivalent to the server phase across every **rejoin-reachable** (persisted-between-events) opening state by `RenjuReconstructTest` (`decode(numMoves, encode(state)) == getOpeningPhase()`; the transient intra-handler pre-commit `n==4` `MOVE` states are atomically committed inside one handler, so they are not rejoin-observable and are intentionally out of contract). **(This replaces the prior `isNetSwapped()`/net-swap design, which has been removed.)**
  - **Two design-verification notes accounted for:** *(a)* a `(numMoves=1, silent-swap)` rejoin is not meaningful in production (the move-1 window is the symmetric centre; a take-over there does not occur), but the new signal keys on *window resolution*, not swap parity, so the contract round-trips regardless and needs no special case. *(b)* `select1` **is** sent on rejoin under the new design (it is the Branch-B `numMoves=5` signal); under the old design only the ten-offers signal existed and `handleJoin` never replayed `select1`.
- **Decision-echo contract:** clients must **NOT** place stones from the swap/offer/select echo events; stones arrive as `DSGMoveTableEvent`.
- **Recovery contracts:** if a declined-swap's bundled stone is rejected, the swap-decline is **already committed** — the client recovers by sending the stone as a plain move (the swap window is consumed). If an offer10's ten are rejected, the move-4 decline + Branch-B are **already committed** — the client recovers by re-sending a corrected ten (the handler re-accepts via the `isAwaitingFifthOffers` guard).
- **Branch-A move-5 phase:** Branch A move-5 is sent as a swap-event (`swap=false`, `move5`) by the to-move side in **BOTH** the move-4 swap-decline and post-swap (`isAwaitingBranchChoice`) states; `swap=true` is valid only while the swap window is open (rejected with `INVALID_MOVE` in the branch-choice state); windows 1–3 post-swap place the next opening stone as a plain `DSGMoveTableEvent`.
- **Echo recipient:** echoes currently use `broadcastMainRoom` (mirrors `handleSwap`/`handleSwap2Pass`); verify opponent/spectator receipt in the manual round-trip and switch the three echoes to `broadcastTable` if exact recipient parity is needed.
- **Known minor:** the three handlers use `state != GAME_IN_PROGRESS` as a single catch-all, so a disconnect-mid-opening surfaces `NO_GAME_IN_PROGRESS` rather than `GAME_WAITING_FOR_PLAYER_TO_RETURN` — cosmetic error code only, no logic impact.
- **Live WS round-trip (QA — DONE 2026-06-15):** verified via a headless two-client WS harness (login → create Renju table → sit → start → drive the opening) against `localhost`. All scenarios passed: **auto-center**; **Branch A** (decline+move5, single `DSGMoveTableEvent`); **Branch B** (offer10 → select1, move 5 placed once, other nine clear); **swap=true + post-swap Branch A** (take-over then `swap=false`+move5); **error-to-sender** (`NOT_TURN` / `INVALID_MOVE`, offer10 with `move=-1`, sender-only delivery, table state intact on reject); and **rejoin-mid-offer** (the `SELECTION`-phase rejoin signal is the re-sent ten offers — `DSGRenjuTaraguchiOffer10TableEvent` — alongside the authoritative seats; under the current-decision-point design no silent swap accompanies it). *(The QA harness above predates the `isNetSwapped`→current-decision-point redesign; re-verify the per-phase rejoin signals against the new `RenjuRejoin` table when re-running it.)*

### Still deferred

- **React `react_live_game_room` opening UI (sub-project 3)** — **IMPLEMENTED** (§8 is the as-built reference: anchors, live phase derivation, file-by-file map, wire examples). Live-verified: Branch A/B opening, take-over seat swap, offer-10 auto-send, white selection + server prompt.
- **Viewer rendering of the offer phase during historic replay** — the offers/swaps are now persisted + exposed in JSON, but the historic viewers don't yet render the offer/selection phase.
- **Forbidden-point marking** in any client — deferred (server-enforced only). Add via `getForbiddenPoints` → expose like `renjuOffers` → mark; don't port the finder.
- **iOS / Android** clients — not started in code; **zero-context handoffs now written** (§9 iOS, §10 Android: transport verdicts, confirmed anchors, live phase derivation, file-by-file maps, wire examples). React (`react_live_game_room`) is implemented (above).
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
| outbound build | `src/protocol/messages.js` + `commands.js` (facade `src/protocol/index.js`) | `MESSAGES[type] = {dir, cmd, out:[fields], req:[fields]}` (`req: TBL=['table']` for table events). `buildCommand(type, required, args)` → `{ [type]: { ...args, ...DEFAULTS } }`, `DEFAULTS = { time: 0 }` — **`time:0` is auto-stamped on every outbound command** (do not list it in `out`). `COMMANDS` derives `cmd → {type, out}`; `Commands.<cmd>(args)`. `INBOUND_TYPES` = MESSAGES keys (`dir!=='out'`) + `ERROR_EVENTS` + `PING`. Consumers `import {Commands} from '../../protocol'`. |
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
  branch,           // null | 'A' | 'B' — set by the move-4 decision echoes
  offers,           // int[] | null — the 10 Branch-B candidates (offer10 echo)
  selection,        // int | null — white's pick (select1 echo)
}
// NOTE: no net-swap/orientation field is tracked here. Who-owns-black comes from
// `table.seats` (the visual seat swap on a live swap=true, and `sendPlayingPlayers`
// on rejoin) — never derived from the silent rejoin swap event (its swap bit is the
// current window's decision, not net orientation).
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
   - Extend `swapSeats(data, state)`: when the game is Renju, in the **silent branch** (rejoin phase marker, §7) **advance the tracked opening phase for the current window only** (mark the current window resolved → the derivation in §8.2 moves it to `MOVE`/`BRANCH`) — do **not** animate (the silent `DSGSwapSeatsTableEvent` arrives after seats are already current; double-swapping corrupts the board, exactly the dPente silent-swap contract) and do **not** set any net-swap/orientation flag (its `swap` bit is the current window's decision, not net orientation; seats come from `sendPlayingPlayers`).
   - Add three reducers `renjuSwap` / `renjuOffer10` / `renjuSelect1` that **update opening-tracking state only and place NO stones** — stones arrive via `addMove` (`DSGMoveTableEvent`). `renjuSwap`: mark the window decided (the visual seat swap that reflects who-owns-black is handled by the non-silent `swapSeats` branch / `table.swap()`, not tracked here) and (when the bundled stone follows) leave placement to `addMove`; at `movesLength==4`, **any** `swap=false` echo carrying a valid stone ⇒ `branch='A'` (whether the move-4 swap window was still open — i.e. a bundled decline — or already closed by a prior take-over). `renjuOffer10`: `branch='B'`, store `offers = data.moves`. `renjuSelect1`: store `selection = data.move`.
6. **`src/redux_reducers/rootReducer.js`** — register three `EVENT_HANDLERS` entries (keys **must** equal the wrapper keys in 8.1):
   ```js
   dsgRenjuTaraguchiSwapTableEvent:    (p, s) => renjuSwap(p, s),
   dsgRenjuTaraguchiOffer10TableEvent: (p, s) => renjuOffer10(p, s),
   dsgRenjuTaraguchi10Select1TableEvent:(p, s) => renjuSelect1(p, s),
   ```
   This is still the registration point post-refactor (it did **not** move to `protocol/middleware.js`). Decode + dispatch is automatic: once these types are in `MESSAGES`/`INBOUND_TYPES` (step 7), `protocol/middleware.js` decodes each frame and dispatches the typed action, which the reducer's `default:` arm routes through `EVENT_HANDLERS`. The silent swap-seats **phase marker** is already routed (`dsgSwapSeatsTableEvent → swapSeats`); just teach `swapSeats` to advance the Renju opening phase in the silent branch (step 5) — it is a phase marker, **not** a net-swap (seats come from `sendPlayingPlayers`).
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
**Offer 10** (Branch B — black offers ten 5th-move candidates; no two D4-symmetric — offsets
`(1,0)(2,0)(3,0)(4,0)(1,1)(2,1)(3,1)(4,1)(2,2)(3,2)` about centre 112 → **10 distinct {|dx|,|dy|} orbits**):
```json
{ "dsgRenjuTaraguchiOffer10TableEvent": { "moves": [113,114,115,116,128,129,130,131,144,145], "player": "alice", "table": 5, "time": 0 } }
```
**Select 1** (white picks one of the ten → becomes move 5; placed via a following `dsgMoveTableEvent`):
```json
{ "dsgRenjuTaraguchi10Select1TableEvent": { "move": 130, "player": "bob", "table": 5, "time": 0 } }
```
**Contract reminders (from §7):** never place stones from these three echoes — stones ride
`DSGMoveTableEvent` (`addMove`). On (re)join the server sends the authoritative seats
(`sendPlayingPlayers`) plus **exactly one** current-decision-point signal (§7 table, keyed by
`numMoves`): *nothing* (window open / opening complete), a **silent** `dsgSwapSeatsTableEvent`
(window resolved → `MOVE`/`BRANCH`; its `swap` is the **current window's** resolved decision —
**not** net orientation — and seats are NOT re-applied), an **offer10** frame (Branch-B
selection pending), or a replayed **select1** frame
(Branch-B move 5 already chosen). The client reconstructs the phase via
`RenjuRejoin.decode(numMoves, signal)`. *(This supersedes the old "re-send the ten plus a silent
swap if net-swapped" behavior — `isNetSwapped()` was removed.)*

### 8.5 Offer symmetry dedup (client-side, UX nicety)
The ten Branch-B offers must contain no two D4-symmetric duplicates. The server already rejects
violations via `RenjuState.offerFifthMoves` (→ `offerFifthMove`), so client-side checking is a
**UX nicety** (instant feedback instead of a round-trip error) — recommended, not required.

**The ten offers are NOT box-constrained.** `offerFifthMove` accepts **any** in-bounds, empty,
non-D4-symmetric point — corner offers are legal (confirmed by the 2026-06-15 round-trip). Only
the **Branch-A** move 5 is restricted to the 9×9 box; the Branch-B offered 5th moves can be
anywhere on the board. So the 10-pick multi-select must allow the **whole board** (minus
occupied + symmetric-duplicate cells), **not** a central square.

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
  window (the bundled stone is constrained to the same square). This box constraint covers **only
  the single-stone placements (moves 2–5, including the Branch-A move 5)** — the ten Branch-B
  offers are **not** box-constrained (§8.5), so do **not** draw a box for the offer-10 picker.
- **Translucent "dead-stone" candidates** — render the 10 Branch-B offers (and, during
  `SELECTION`, the non-picked nine) as translucent black. The Go **dead-stone** render path is
  the closest existing primitive (`Board.js` `board[s].deadStone`; `Stone`/`SimpleStone` take an
  `opacity` prop) — reuse it if the prop wiring works for a non-Go board **(verify)**; otherwise
  add a translucent variant.
- **10-pick multi-select + submit** — tap to add a candidate, tap again to remove, with an
  `n/10` counter and a submit button. **Validation before send:** exactly **1** stone (and inside
  the 9×9) for Branch A, or exactly **10** distinct, non-D4-duplicate (§8.5) stones placed
  **anywhere on the board** (in-bounds + empty; **not** box-constrained) for Branch B;
  alert otherwise. Branch is inferred from the count (1 = continue / 10 = offer), matching the
  `MoveServlet`/`ServerTable` contract.

Visual reference (different framework, do not copy code): `gameServer/tb/mobileGame.jsp` and its
board JS — `drawDeadStone`, the central-square hinting by move number, and the multi-pick picker.

---

## 9. Sub-project 4 — iOS (`penteLive-iOS`) Taraguchi-10 handoff

Zero-context handoff for a fresh agent wiring the live Renju opening UI into the
`penteLive-iOS` submodule (a **separate repo** — do not edit it from this one). Every anchor
below was grep-verified against the submodule on this branch; line numbers are as-of-now and
drift, so grep the symbol.

**TRANSPORT VERDICT: LIVE ONLY — derive the phase, exactly like §8.2 (React).** The iOS app is a
raw-TCP / WebSocket client: `PenteLiveSocket.swift` opens a `GCDAsyncSocket` and reads
255-delimited JSON frames (`separator = Data([255])`, `PenteLiveSocket.swift:25`), dispatching by
top-level key in `processEvent` (`:105-174`); moves are **sent** as a hand-built
`dsgMoveTableEvent` dict via `socket.sendEvent(...)` (`TableViewController.sendMove:547`). There
is **no** `MoveServlet`/`renjuAction` POST path and **no** `GameResponse`/`renjuPhase` consumer
anywhere in the codebase (grep for `renjuPhase`/`renjuOffers`/`renjuSwaps`/`GameResponse` → zero
hits). So the live client gets **NO `renjuPhase` on the wire** and must **derive** the
Taraguchi-10 phase from tracked decision-echo state (§9.2), identical in spirit to the React port.
A **secondary, read-only** path exists — the legacy Objective-C `BoardViewController.m` loads
finished games via `GET /gameServer/mobile/json/game.jsp?gid=…` (`:1422` production URL / `:1427`
localhost variant) — but its JSON parser (`:1453-1503`; `NSJSONSerialization` at `:1453`, field
reads `player1`/`player2`/`currentPlayer`/`moves` at `:1473-1503`) reads only
`moves`/`currentPlayer`/`player1`/`player2` and does **not** read
`renjuPhase`/`renjuOffers`/`renjuSwaps`; rendering the historic opening there is the §2.6/§4
read-the-field path and is **deferred** (it does not affect live play).

**iOS and Android have no Renju support whatsoever today.** Game ids 31/32/81 are absent from
every enum and map; see §9.1 for the silent-degradation fall-through.

**How iOS genuinely differs from the React reference (§8) — read before forcing the React shape:**
- **Two languages.** The live-play stack is **Swift** (`PenteLiveSocket`, `TableViewController`,
  `RoomViewController`, `HelperClasses`, `LiveBoard`, `PenteEngine/*`). The read-only replay /
  turn-based viewer is **legacy Objective-C** (`BoardViewController.m/.h`, `BoardView.m/.h`,
  179 KB of ObjC). All Renju live work lands in the Swift files; the ObjC viewer is optional.
- **A client-side rules-engine *copy*.** `PenteEngine/PenteGame.swift` + `RuleSet.swift` +
  `PenteVariant.swift` are a structured Swift port of the move engine (capture rules, a
  `cadence` enum for move→color, and an `OpeningMask` enum for opening legality). React replays
  inline in `GameClass.js`; iOS routes color through `rules.cadence` and opening through
  `OpeningMask`, so Renju needs a **new variant + RuleSet + cadence + opening-mask kind** — more
  structured work than React’s inline `2-(i%2)` flip.
- **No event-wrapper classes, no Protocol module.** iOS parses each frame into a bare
  `[String: Any]` (`convertJSONStringToDictionary:177`) and dispatches via a literal if-else chain;
  outbound frames are **hand-built dicts** (`sendMove:548`). There is no `MESSAGES`/`Commands`
  registry (React) and no typed event package (Android). Adding the three Renju events = three new
  if-else arms + three hand-built send methods. Simpler plumbing, but **no schema validation** and
  **no `time:0` auto-stamp** — the sender must include `"time": 0` literally (as `sendMove` does).
- **No `openingPhase.js` analogue.** React centralizes phase logic in pure classifiers; iOS
  scatters it across `HelperClasses` (`isSwap2ChoiceWithPassOption:150`, `currentPlayer:466`) and
  carries only `swap2State`/`dPenteState` enums — there is **no `renjuState`**. You add the
  tracking slice and a `renjuPhase(...)` derivation from scratch.
- **Board size hardcoded `19` more pervasively.** Beyond the Swift engine’s literal `/19`/`%19`,
  there is a C array `abstractBoard[19][19]` in `BoardViewController.h:36`. React already had
  `gridSizeForGame` (only missing the 31/32/81 case); iOS needs a broader sizing pass.
- **No multi-select opening-UI precedent** (same gap as React, but worse): the swap2/dPente UI is
  a yes/no/pass `UIAlertController` action-sheet (`stateChanged:593/611/632`), not board
  interaction. There is no 10-pick picker, no translucent-candidate array, no central-box
  highlight, no “select 1 of 10” screen.

### 9.0 Board basics (restated for this client)
- Board **15×15**, game ids **31 (Renju) / 32 (Speed Renju) / 81 (TB Renju)**. Move encoding
  `x + y·15`; **center = 112** (`7 + 7·15`). The **server auto-places** the center as move 1 —
  it arrives as an ordinary `dsgMoveTableEvent`, so the client only needs the board sized to 15
  to render it correctly (no client-side auto-center).
- **Board background colour = `#D98880` (dusty rose)** — the canonical Renju board colour,
  **distinct from gomoku's `#A3FDEB`**. Matches the web (`renjuColor`, `gameScript.js:14`) and
  `react_live_game_room` (`.renju` in `TableClass.js`). This is the exact value the `.renju`
  `backgroundColor` case (§9.3 step 2a) must return.
- **Black plays first.** iOS color conventions to reconcile:
  - `abstractBoard` cell values: **0 = empty, 1 = WHITE, 2 = BLACK, −1 = masked** (the rendering
    convention; `BoardView.h:13-15` separately `#define WHITE 0 / BLACK 1 / RED 2` is a distinct
    legacy palette enum, **not** the board-value convention — do not conflate them).
  - The engine’s move→color is `PenteGame.colorForMove(_:)` `:94`: for the `.alternating` cadence
    it returns `(index % 2) + 1`, i.e. **move 0 → value 1 = WHITE first** — this is the **opposite**
    of Renju. The same white-first parity is echoed in `HelperClasses.currentPlayer():466`
    (`1 + moves.count % 2`).
  - So **“black first” ⇒ the first stone (move 0/center) must carry board value 2**. Because color
    flows through `rules.cadence`, the fix is a **new black-first cadence** (e.g.
    `colorForMove → 2 - (index % 2)`, giving move 0 → value 2), **not** an inline flip.

### 9.1 Confirmed anchors (file : symbol)
All grep-verified in the submodule on this branch; treat as fact unless marked **(verify)**.
“**WRONG today**” = the code path Renju ids 31/32/81 hit right now, which is incorrect for Renju.

| Area | File | Symbol / fact |
|---|---|---|
| inbound dispatch | `test1/PenteLiveSocket.swift` | `processEvent(eventString:)` **:105-174** — if-else chain keyed on the top-level JSON key. Present: `dsgMoveTableEvent` (:146), `dsgSystemMessageTableEvent` (:148), `dsgSwapSeatsTableEvent` (:150), `dsgSwap2PassTableEvent` (:170). **No** `dsgRenjuTaraguchiSwapTableEvent` / `…Offer10…` / `…Select1…` → the three echoes are **silently dropped** (no else-arm). **WRONG today.** |
| frame decode | `test1/PenteLiveSocket.swift` | `socket(didRead:withTag:)` **:89-96** reads to `separator = Data([255])` (:25), UTF-8 → `convertJSONStringToDictionary` (:177) → `[String: Any]`; **no schema validation**. |
| outbound send | `test1/PenteLiveSocket.swift` | `sendEvent(eventDictionary:)` → `sendEvent(eventData:)` **:225-243** — `JSONSerialization` → append `separator` → `socket.write`. Generic; senders hand-build the dict. **No `time` auto-stamp.** |
| move send | `test1/TableViewController.swift` | `sendMove(move:)` **:547-548** builds `["dsgMoveTableEvent": ["move": move, "moves": [move], "player": me, "table": table.table, "time": 0]]`. The dict-shape template for the new Renju senders. |
| dPente swap UI | `test1/TableViewController.swift` | `stateChanged()` **:593-607** — `UIAlertController(.actionSheet)` “Continue play as” with **Player 1 (white)** / **Player 2 (black)**; sends `dsgSwapSeatsTableEvent`. |
| swap2 swap UI | `test1/TableViewController.swift` | `stateChanged()` **:611-631** (with pass: P1/P2/**Pass Decision**) and **:632+** (without pass); sends `dsgSwapSeatsTableEvent` or `dsgSwap2PassTableEvent`. The opening-UI dispatch precedent to mirror — **yes/no/pass only, no board interaction.** |
| swap2 detection | `test1/HelperClasses.swift` | `isSwap2ChoiceWithPassOption()` **:150-152** = `isSwap2() && moves.count==3 && state.swap2State == .noChoice`; `isSwap2ChoiceWithoutPassOption()` **:154-156** (`moves.count==5`). The count+state derivation pattern to copy for Renju. |
| opening-state enums | `test1/HelperClasses.swift` | `class GameState` **:651-683** with `enum DPenteState` (:659) + `enum Swap2State` (:665); fields `dPenteState`/`swap2State` (:679-680). **No `renjuState` / `RenjuState` enum.** **WRONG today.** |
| swap reducers | `test1/HelperClasses.swift` | `swapSeats(swap:silent:)` **:414-434** sets `dPenteState`/`swap2State` → `.swapped`/`.notSwapped`; `swap2Pass(silent:)` **:436-438** sets `.swap2Pass`. Table-level wrappers `swapSeats(tableId:swap:silent:)` (:777) / `swap2Pass(tableId:silent:)` (:785). |
| color parity (table) | `test1/HelperClasses.swift` | `currentPlayer()` **:466-485** — non-Go/non-Connect6 returns `1 + (moves.count % 2)`. This turn-order parity is **CORRECT for Renju normal play** (alternation is unchanged); the black-first concern is a stone-**COLOUR** fix via the cadence (`colorForMove → 2 - (index % 2)`, §9.0), **NOT** a `currentPlayer` change. **WRONG today only in the OPENING** — needs a `renjuOpeningPlayer` arm (§9.2) for the swap/branch/selection decision points. |
| color parity (engine) | `test1/PenteEngine/PenteGame.swift` | `colorForMove(_:)` **:94-101** — `.alternating` cadence → `(index % 2) + 1` (move 0 = value 1 = white); `.connect6` cadence is special. Keyed on `rules.cadence`. **WRONG today** for Renju. |
| board-value palette | `test1/BoardView.h` | **:13-15** `#define WHITE 0 / BLACK 1 / RED 2` (legacy palette, distinct from the `abstractBoard` 0/1/2/−1 convention). `abstractBoard` ivar at :29. |
| board size (Table) | `test1/HelperClasses.swift` | `abstractBoard` **:103** = `Array(repeating: Array(repeating: 0, count: 19), count: 19)` (also re-inited :211, :374, :445); `var gridSize = 19` **:328** (dynamic only for Go ids). **WRONG today** (Renju needs 15). |
| board size (engine) | `test1/PenteEngine/PenteGame.swift` | board init `count: 19` (**:15, :20**); `stone(at:)` **:27-31** `board[rowCol/19][rowCol%19]`; `play(_:)` **:34-45** `board[move/19][move%19]`; `apply(removed)` **:106** `cap.position/19`. Literal `19` divisor throughout. **WRONG today.** |
| board size (ObjC) | `test1/BoardViewController.h` | C array `abstractBoard[19][19]` **:36** (+ `replayGame` `gridSize` default 19, `BoardViewController.m:1383`). Read-only viewer; **WRONG today** if used for Renju. |
| opening mask | `test1/PenteEngine/PenteGame.swift` | `applyOpeningMask()` **:127-130** dispatches on `OpeningMask`; `maskTournamentOpening()` **:145-148** = `for i in 7..<12, j in 7..<12` (center 5×5 of 19×19, masks idx 7-11); `maskGPenteOpening()` :153-159; `.swap2` case is **unhandled** (`break`). **No radius-by-move-number mask** for Renju’s 3×3/5×5/7×7/9×9. **WRONG today.** |
| opening-mask enum | `test1/PenteEngine/RuleSet.swift` | `enum OpeningMask { none, tournament, gpente, swap2 }` **:7** — no Renju/radius mask. **WRONG today.** |
| variant→ruleset | `test1/PenteEngine/RuleSet.swift` | `ruleSet(for:)` **:114-126** — switch over **11** `PenteVariant` cases, **no `.renju`**. **WRONG today.** |
| variant enum | `test1/PenteEngine/PenteVariant.swift` | `enum PenteVariant: Int` **:6-17** — 11 cases `pente=0 … connect6=10`; **no `renju`**. Raw values frozen to legacy ObjC enum → **next free raw value is 11**. **WRONG today.** |
| id→variant | `test1/HelperClasses.swift` | `penteVariant(for:)` **:266-280** — switch on `GameEnum`; `default: return .pente` (:278). Ids 31/32/81 hit default → **`.pente`** (Pente capture rules, not Gomoku). **WRONG today.** |
| game-id enum | `test1/HelperClasses.swift` | `enum GameEnum: Int` **:81** — cases 1…30 (`speedSwap2Keryo=30`). `GameEnum(rawValue: 31)` ⇒ **nil**. **WRONG today.** |
| game names | `test1/HelperClasses.swift` | `static let gameNames` **:115-121** — keys 1…30 only; **31/32/81 absent** (UI shows no name). **WRONG today.** |
| string→variant | `test1/BoardVariantMapping.swift` | `variant(forGameType:)` **:8-28** — maps game-type strings to `PenteVariant`; fallback `return .pente` (:28). **No Renju gameType.** **WRONG today.** |
| star points + sizing | `test1/LiveBoard.swift` | `var gridSize = 19` **:23**; `draw(_:)` **:80-151** draws 5 special circles via `c = floor(gridSize/2)` and a 19-specific point set (with a `gridSize == 9` special-case). **No central-box concept.** Needs 15×15 star points {3,7,11} → indices `[48,52,56,108,112,116,168,172,176]`. **WRONG today.** |
| translucent stones | `test1/LiveBoard.swift` | `init` **:30-35** — `whiteStone`/`blackStone` created with `alpha = 0.7; isOpaque = false; fill = true`. **Reusable translucent primitive** for offer candidates. |
| move-apply path | `test1/RoomViewController.swift` | `moveTableEvent(event:)` **:871** — on `dsgMoveTableEvent` reads `event["move"] as! Int` (**:877**) and `event["moves"] as! [Int]` (**:878**), then calls `table.addMove`/`addMoves` and `tableViewController.stateChanged()`. **Confirmed: a coordinate is a single `Int` board index (`x + y·15`), inbound (`:877-878`) and outbound (`sendMove:548`).** |
| move replay | `test1/HelperClasses.swift` | `addMoves(moves:)` (≈**:209-232**) → `engine.replay(...)` then `syncFromEngine()` (:296-309, hardcoded `0..<19` loop). Engine is authority for Pente-family. |
| swap2 handler pattern | `test1/RoomViewController.swift` | `swapSeatsTableEvent(event:)` **:745**; `swap2PassTableEvent(event:)` (≈**:760-769**) extracts fields on main queue → `playersAndTables.swap2Pass(tableId:silent:)` → `stateChanged()`. **The direct reference for the three new Renju handlers.** |
| system-message handler | `test1/PenteLiveSocket.swift` / `RoomViewController.swift` | dispatch at `PenteLiveSocket:148` → `systemMessageTableEvent(event:)` (≈`RoomViewController:894-905`) extracts `message` and shows it via `tableViewController.addText`. **Display-only** today; reusable (with a non-dismissible variant) as the Branch-B selector prompt. |
| dPente choice precedent (ObjC) | `test1/BoardViewController.m` | `dPenteChoiceLabel` (`:52, :340, :1824-1870`) — “Play as” server-driven opening-UI precedent in the legacy viewer; `boardTap:` (`:644`) tracks `swap2Move1/2/3`, `dPenteMove1-4`. **(verify — legacy ObjC, read-only path)** |

### 9.2 Live phase derivation (mirror `swap2Phase` / §8.2)
iOS plays Renju **live**, so — exactly as React — there is **no `renjuPhase` on the socket**; the
client must derive it. iOS has **no `openingPhase.js`** and **no `renjuState`** (only
`swap2State`/`dPenteState`, `HelperClasses.swift:679-680`), so add both:

(1) a tracking slice on the `Table`/`GameState`, accumulated **from the three echo events** (§9.4):

```swift
enum RenjuBranch { case a, b }
struct RenjuTracking {
    var swapWindowOpen: Bool = true   // is the CURRENT swap window still undecided?
    var branch: RenjuBranch? = nil    // set by the move-4 decision echoes
    var offers: [Int]? = nil          // the 10 Branch-B candidates (offer10 echo)
    var selection: Int? = nil         // white's pick (select1 echo)
}
// NOTE: no net-swap / orientation field here. Who-owns-black comes from `table.seats`
// (the visual seat swap on a live swap=true, and sendPlayingPlayers on rejoin) — NEVER
// from the silent rejoin swap event (its swap bit is the current window's decision, §7).
```

(2) a pure `renjuPhase(movesCount, tracking)` classifier (mirror `isSwap2ChoiceWithPassOption`),
where `movesCount = table.moves.count` (stones on board, incl. the auto-center = move 1):

| movesCount | tracked state | phase | to-move acts |
|---|---|---|---|
| 1 | swapWindowOpen | `SWAP` (window 1) | Swap, **or** decline + place move 2 ∈ 3×3 |
| 2 | swapWindowOpen | `SWAP` (window 2) | Swap, **or** decline + place move 3 ∈ 5×5 |
| 3 | swapWindowOpen | `SWAP` (window 3) | Swap, **or** decline + place move 4 ∈ 7×7 |
| **4** | **swapWindowOpen** | **`SWAP`** (window 4) | THREE actions: `swap=true` take-over → **`BRANCH`** (no stone) · `swap=false` **bundled with move 5 ∈ 9×9** → **Branch A** (constrained **`MOVE`** placement) · `Offer10` → **Branch B** (**`OFFERS`**) |
| **4** | swap=true taken, `branch==nil` | **`BRANCH`** | black chooses → place move 5 ∈ 9×9 (**`MOVE`**, Branch A) **or** offer 10 (**`OFFERS`**, Branch B) |
| **4** | `branch==.a` (after take-over) | **`MOVE`** | place move 5 inside the 9×9 — constrained opening placement |
| **4** | `branch==.b`, offering | **`OFFERS`** | black offers ten 5th-move candidates (anywhere on board, §9.5) |
| **4** | `branch==.b`, `offers` present | **`SELECTION`** | white picks 1 of the 10 → becomes move 5 |
| **5** | `branch==.a`, swap-5 undecided | **`SWAP`** (window 5) | Swap, **or** decline → then move 6 |
| **5** | `branch==.a`, swap-5 decided | **`COMPLETE`** (move 6 anywhere) | place move 6 — free alternating play |
| **5** | `branch==.b` (selection done) | **`COMPLETE`** (move 6 anywhere) | place move 6 — **no swap-5 window in Branch B** |
| ≥6 | — | **`COMPLETE`** | plain alternation; black forbidden-points **server-enforced** |

**Move-4 model (live path), identical to §8.2.** At the move-4 window the to-move player has
**three** wire actions: (a) `swap=true` take-over → standalone **`BRANCH`** (no stone);
(b) `swap=false` **bundled with move 5 in the 9×9** = **Branch A** (there is **no** stoneless
move-4 decline); (c) `Offer10` = **Branch B**. The standalone `BRANCH` state therefore arises
**only** after a take-over. Branch-A move 5 itself arrives as a **swap event** (`swap=false`, with
the move) per the §7 decision-echo notes, not as a branch event. Grounds (server side, §7):
`ServerTable.handleRenjuSwap` (bundled decline + `chooseBranch(false)`),
`RenjuState.wouldAcceptDeclinedOpeningMove`.

**Reuse the swap2 derivation shape.** `currentPlayer()` (`:466`) and the `isSwap2Choice*`
predicates already derive “whose move / which choice” from `(moves.count, swap2State)`. Add a
`renjuOpeningPlayer(movesCount, tracking)` and an `isRenjuSwapChoice`/`isRenjuBranchChoice`/
`isRenjuSelection` set in the same style, then gate the UI (§9.6) off them — **(verify the exact
`currentPlayer()` arm is needed for correct `isMyTurn` during the opening; the safe move is to add
it, mirroring the swap2 arm).**

**Rejoin / spectate.** Honour the §7 current-decision-point contract: the server sends
authoritative seats (`sendPlayingPlayers`) **plus exactly one** signal keyed by `numMoves` —
*nothing* (window open / complete), a **silent** `dsgSwapSeatsTableEvent` (window resolved →
`MOVE`/`BRANCH`), an **offer10** frame (Branch-B selection pending), or a replayed **select1**
(Branch-B move 5 chosen). Reconstruct via the §7 `RenjuRejoin.decode(numMoves, signal)` rules. In
the **silent** `swapSeats` branch (`swapSeats(swap:silent:)` :414) for Renju, **advance the
tracked phase for the current window only** — do **not** double-swap seats (seats are already
current) and do **not** derive who-owns-black from the swap bit (seats come from
`sendPlayingPlayers`). This is exactly the dPente silent-swap contract.

**Alternative considered:** port `RenjuState`’s server-side Taraguchi-10 state machine into Swift.
**Not recommended** — it duplicates a non-trivial engine plus the forbidden-point finder it leans
on. Track only the four decision variables above; the server stays authoritative.

### 9.3 iOS file-by-file map
1. **`test1/HelperClasses.swift`** — Renju ids resolve WRONG today (§9.1). 
   - `GameEnum` (:81): add `case renju = 31`, `speedRenju = 32`, `tbRenju = 81`.
   - `gameNames` (:115): add `31: "Renju", 32: "Speed Renju", 81: "TB Renju"`.
   - `penteVariant(for:)` (:266-280): add `case .renju, .speedRenju, .tbRenju: return .renju` **before** the `.pente` default (requires the new `PenteVariant.renju`, step 4).
   - `var gridSize` (:328): add a Renju branch returning **15** (alongside the Go 9/13/19 cases).
   - `abstractBoard` (:103, and the re-inits at :211/:374/:445): size from `gridSize` (15 for Renju) instead of the literal `19`.
   - `currentPlayer()` (:466-485): add a `#isRenju` arm. Outside the opening, Renju is plain move-parity; during the opening, return `renjuOpeningPlayer(moves.count, renjuTracking)` (mirror the swap2 arm). **(verify need.)**
   - `GameState` (:651-683): add a `renjuTracking` slice (the §9.2 struct/enum) next to `dPenteState`/`swap2State`; initialise it in `reset()` (next to :380-381 / :451).
   - `swapSeats(swap:silent:)` (:414-434): add a Renju branch — in the **silent** branch advance `renjuTracking` for the current window only (rejoin phase marker, §7); do **not** set `.swapped`/`.notSwapped` and do **not** re-animate. The non-silent branch keeps the visual `table.swap()` (who-owns-black).
   - Add mutators `renjuSwap(swap:move:silent:)`, `renjuOffer10(moves:)`, `renjuSelect1(move:)` (mirror `swap2Pass:436`) that **update `renjuTracking` only and place NO stones** (stones arrive via the `addMove` path). At `moves.count == 4`, **any** `swap=false` echo carrying a valid stone ⇒ `branch = .a` (whether the window was open — a bundled decline — or already closed by a prior take-over). `renjuOffer10`: `branch = .b`, `offers = data["moves"]`. `renjuSelect1`: `selection = data["move"]`.
   - `addMoves`/`syncFromEngine` (:209-232 / :296-309): drive the loop from `gridSize`, not `0..<19`; the engine replay must run the Renju variant.
2. **`test1/PenteEngine/PenteVariant.swift`** (:6-17): add `case renju = 11` (next frozen raw value). **⚠ Bumping this enum breaks the build at every *exhaustive* `switch` over `PenteVariant` that has no `default` — audit and patch each one, INCLUDING the test targets (`PenteVariantTests` / `RuleSetTests`).** Known non-default switch: `BoardVariantMapping.backgroundColor(for:boatPente:)` (step 2a). (`hidesCaptureLabels(for:opening:)` at `BoardVariantMapping.swift:66` is **safe** — it has a `default`.)
2a. **`test1/BoardVariantMapping.swift`** — `backgroundColor(for:boatPente:)` (**:35-60**) is an exhaustive `switch` over all `PenteVariant` cases with **no `default`**, so adding `.renju` won't compile until you add a case. Add `case .renju: return UIColor(red: 0.851, green: 0.533, blue: 0.502, alpha: 1)` — the canonical Renju board colour **#D98880** (§9.0), distinct from the `.gomoku` case. (`variant(forGameType:)` at :8-28 has a `.pente` fallback so it won't break the build; add a Renju gameType branch there only if the server emits a Renju game-type string.)
3. **`test1/PenteEngine/RuleSet.swift`**
   - `OpeningMask` (:7): add a Renju kind (e.g. `case renju` or a parametric radius mask) for the 3×3/5×5/7×7/9×9 central squares by move number.
   - Add a `RenjuRules` struct: **Gomoku-like** (no captures), win = black-exact-5 / white-5+ (display only — server is authority), **black-first cadence**, `opening = .renju`. Add `case .renju: return RenjuRules()` to `ruleSet(for:)` (:114-126).
   - Cadence: add a black-first cadence consumed by `colorForMove` (step 4) so move 0 → value 2.
4. **`test1/PenteEngine/PenteGame.swift`**
   - Replace the literal `19` divisor with a `boardSize` (15 for Renju) in `stone(at:)` (:31), `play(_:)` (:45), `apply(removed)` (:106), and board init (:15/:20).
   - `colorForMove(_:)` (:94): handle the new black-first cadence → `2 - (index % 2)` (move 0 = value 2 = black).
   - Add a `maskRenjuOpening(moveNumber:)` (analogous to `maskTournamentOpening:145`) that masks everything **outside** the N×N central square about center index 112 for the current opening move (radii 1/2/3/4 → 3×3/5×5/7×7/9×9), and wire it into `applyOpeningMask()` (:127). (The center stone is server-auto-placed; the client only renders it.)
5. **`test1/LiveBoard.swift`**
   - `gridSize` (:23) + `draw(_:)` star points (:80-151): for Renju use `gridSize = 15` and the 9 star points at {3,7,11} → indices `[48,52,56,108,112,116,168,172,176]` (index `= col + row·15`, center 112); do **not** reuse the 19-specific 5-point set.
   - Add a **central-box highlight** overlay (new rect/dashed layer) for the legal N×N region during `MOVE` and the decline-and-place action (§9.6).
   - Reuse the `alpha = 0.7` `whiteStone`/`blackStone` (:30-35) to render up to 10 translucent candidates.
6. **`test1/PenteLiveSocket.swift`** — `processEvent` (:105-174): add three else-arms mirroring the `dsgSwap2PassTableEvent` arm (:170):
   ```swift
   } else if let content = event?["dsgRenjuTaraguchiSwapTableEvent"] {
       room.renjuSwapTableEvent(event: content as! [String: Any])
   } else if let content = event?["dsgRenjuTaraguchiOffer10TableEvent"] {
       room.renjuOffer10TableEvent(event: content as! [String: Any])
   } else if let content = event?["dsgRenjuTaraguchi10Select1TableEvent"] {
       room.renjuSelect1TableEvent(event: content as! [String: Any])
   }
   ```
   (Outbound `sendEvent(eventDictionary:)` at :225 is generic — no change.)
7. **`test1/RoomViewController.swift`** — add `renjuSwapTableEvent` / `renjuOffer10TableEvent` / `renjuSelect1TableEvent` (mirror `swap2PassTableEvent:760` / `swapSeatsTableEvent:745`): on the main queue extract `table`/fields, call the `playersAndTables` Renju mutators (step 1), then `stateChanged()`. Consider a **non-dismissible** variant of `systemMessageTableEvent` (:894) for the Branch-B selector prompt.
8. **`test1/TableViewController.swift`**
   - Add send methods mirroring `sendMove(move:)` (:547): `sendRenjuSwap(swap:move:)`, `sendRenjuOffer10(_:)`, `sendRenjuSelect1(move:)` — hand-build the dicts (§9.4) and call `socket.sendEvent(eventDictionary:)`. Include `"time": 0` explicitly (no auto-stamp).
   - `stateChanged()` (:552): add a Renju block gated off the derived phase (§9.2), mirroring the dPente/swap2 action-sheet blocks (:593/:611/:632) — but routing to the **new board-interaction UI** (§9.6), not a yes/no sheet: swap windows → “Swap (take over)” / “Don’t swap (place next stone)”; move-4 → “Swap” or branch-by-stone-count; selection → pick.
9. **New Swift files** under `test1/` — the Renju opening UI (§9.6): central-box overlay, 10-pick multi-select, translucent-candidate rendering, and the white selection screen. No existing component is more than a yes/no sheet, so these are new.
10. **(Deferred, read-only viewer)** `test1/BoardViewController.m/.h`, `BoardView.m/.h`: size `abstractBoard[19][19]` (`BoardViewController.h:36`) and the `drawRect:` index decode (`BoardView.m:219-220`) to the game’s grid; and to render the historic opening, read `renjuPhase`/`renjuOffers`/`renjuSwaps` from `game.jsp` (the §2.6/§4 fields the parser at :1453-1503 ignores today). Only needed if historic Renju replay must show the opening — **not required for live play**.

### 9.4 Wire examples (verified keys + fields)
Outbound is a hand-built `[String: Any]` (no `Commands` facade, no `time` auto-stamp — include
`"time": 0` yourself, as `sendMove` does), serialized by `socket.sendEvent(eventDictionary:)`.
Inbound is the same single-key shape parsed into `[String: Any]`, with a **server-stamped
non-zero `time`** (epoch ms). Keys verified against the backend wrapper (`DSGEventWrapper` →
`dsgRenjuTaraguchiSwapTableEvent` / `…Offer10…` / `…Select1…`; inherited `player`/`table`/`time`).
One literal per event (table 5, center 112, 15×15):

**Swap event** — take-over, decline+place, or Branch-A move 5 (all three use this event):
```swift
// outbound: decline window-1 swap + place move 2 at col8,row7 (=113, in 3×3)
let e1: [String: Any] = ["dsgRenjuTaraguchiSwapTableEvent":
    ["swap": false, "move": 113, "player": me, "table": table.table, "time": 0]]
socket.sendEvent(eventDictionary: e1)

// outbound: take over the side (no stone). Send move 0 — the server ignores `move` on swap=true
// (ServerTable.handleRenjuSwap reads getMove() but never uses it for placement on a take-over).
["dsgRenjuTaraguchiSwapTableEvent": ["swap": true, "move": 0, "player": me, "table": table.table, "time": 0]]
```
```json
// inbound echo (server time stamped); the stone, if any, arrives separately as dsgMoveTableEvent
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": false, "move": 113, "player": "alice", "table": 5, "time": 1718400000123 } }
```
**Offer 10** (Branch B — black offers ten 5th-move candidates). The ten must have no two
D4-symmetric duplicates (§9.5); this example uses offsets `(1,0)(2,0)(3,0)(4,0)(1,1)(2,1)(3,1)(4,1)(2,2)(3,2)`
about centre 112 → **10 distinct {|dx|,|dy|} orbits**, all in-bounds, none = centre:
```swift
["dsgRenjuTaraguchiOffer10TableEvent":
    ["moves": [113, 114, 115, 116, 128, 129, 130, 131, 144, 145], "player": me, "table": table.table, "time": 0]]
```
**Select 1** (white picks one of the ten → becomes move 5; placed via a following `dsgMoveTableEvent`):
```swift
["dsgRenjuTaraguchi10Select1TableEvent": ["move": 130, "player": me, "table": table.table, "time": 0]]
```
**Contract reminders (from §7), enforced in the reducers (step 1):** never place stones from these
three echoes — stones ride `dsgMoveTableEvent` (the `addMove`/`moveTableEvent` path). On (re)join,
the server sends authoritative seats (`sendPlayingPlayers`) plus **exactly one**
current-decision-point signal (§7 / §9.2): *nothing*, a **silent** `dsgSwapSeatsTableEvent`
(window resolved; its `swap` bit is the **current window’s** decision, **not** net orientation —
seats are **not** re-applied), an **offer10** frame (Branch-B selection pending), or a replayed
**select1** frame (Branch-B move 5 chosen). Reconstruct via `RenjuRejoin.decode(numMoves, signal)`.

### 9.5 Offer symmetry dedup (client-side, UX nicety)
The ten Branch-B offers must contain no two D4-symmetric duplicates. The server already rejects
violations via `RenjuState.offerFifthMoves` (→ `offerFifthMove`), so client-side checking is a
**UX nicety** (instant feedback vs a round-trip error) — recommended, not required. iOS has **no**
existing port (unlike the JSP `renjuRotate`/`renjuStabilizer`/`renjuIsSymmetricDup`), so port the
algorithm into Swift — or simply let the server reject and surface the error.

**The ten offers are NOT box-constrained.** Any in-bounds, empty, non-D4-symmetric point is legal
— corner offers included (confirmed by the 2026-06-15 round-trip). Only the **Branch-A** move 5 is
restricted to the 9×9 box. So the 10-pick multi-select must allow the **whole board** (minus
occupied + symmetric-duplicate cells), **not** a central square.

Algorithm for the 15×15 board (center `(7,7)`):
- For move `m`: `x = m % 15`, `y = m / 15`, `dx = x − 7`, `dy = y − 7`.
- The **8 D4 images** of `(dx,dy)`: rotations `(dx,dy)`,`(−dy,dx)`,`(−dx,−dy)`,`(dy,−dx)` and
  reflections `(−dx,dy)`,`(dx,−dy)`,`(dy,dx)`,`(−dy,−dx)`. Map each back: `m' = (tx+7) + (ty+7)·15`.
- Reject an offer if **any** of its 8 images equals an already-accepted offer. Maintain a running
  `Set<Int>` of all images of accepted offers and test membership.

Mirror the server logic exactly: the canonical reference is the JSP port (`renjuRotate` /
`renjuStabilizer` / `renjuIsSymmetricDup` in `gameServer/tb/mobileGame.jsp`), itself a JS port of
`SimpleGridState.rotateMove` + the position stabilizer (§3). Translate that to Swift verbatim so
the client agrees with `offerFifthMove`.

### 9.6 New UI primitives (no iOS precedent)
The swap2/dPente UI is a plain `UIAlertController` action-sheet (§9.1) — the Renju opening needs
board-level interaction with **no existing analogue** in this client (`KOTHTableViewController`
uses a `UIPickerView` for a single pick, which is **not** a multi-select):
- **Central-box highlight** — render a colored/dashed rectangle marking only the legal cells inside
  the N×N square about center 112 for the current opening move: **moves 2/3/4/5 → 3×3 / 5×5 / 7×7 /
  9×9** (radius 1/2/3/4). Applies during the `MOVE`/placement phase **and** the decline-and-place
  action of a `SWAP` window (the bundled stone is constrained to the same square). This box covers
  **only the single-stone placements (moves 2–5, incl. Branch-A move 5)** — the ten Branch-B offers
  are **not** box-constrained (§9.5), so do **not** draw a box for the offer-10 picker. No precedent
  in `LiveBoard.draw`’s star-points logic → likely a new overlay/`CALayer` or extra `draw` pass.
- **Translucent “dead-stone” candidates** — render the 10 Branch-B offers (and, during `SELECTION`,
  the non-picked nine) as translucent black, with an optional pick-order label (1–10). Reuse
  `LiveBoard`’s `alpha = 0.7` `blackStone` (:32-35) — the closest existing primitive; rendering
  **10 simultaneously and clearing on interaction is untested**. **(verify the array render/clear.)**
- **10-pick multi-select + submit** — tap to add a candidate, tap again to remove, with a
  `Pick n of 10` counter; **auto-send** on the 10th pick (emit `dsgRenjuTaraguchiOffer10TableEvent`
  without a separate Confirm button). Validation before send: exactly **1** stone inside the 9×9
  for Branch A, or exactly **10** distinct, non-D4-duplicate (§9.5) stones placed **anywhere on the
  board** for Branch B; alert otherwise. Branch is inferred from the count (1 = continue / 10 =
  offer), matching the `ServerTable`/`MoveServlet` contract.
- **White selection screen** — a full-screen or modal view for white to choose 1 of the 10 offered
  moves (like the swap prompt, but 10 board cells/buttons instead of 2–3). On pick, emit
  `dsgRenjuTaraguchi10Select1TableEvent` (§9.4). **(verify: separate `UIViewController` vs an
  in-`TableViewController` overlay.)**
- **Non-dismissible selection prompt** — the server prompts the selector via
  `dsgSystemMessageTableEvent` (today display-only via `addText`, §9.1). For Renju the prompt must
  be **action-forcing** (non-dismissible) until a selection is sent, not a passive text-log line.
  **(verify the server actually sends this for Renju selection.)**

Visual reference (different framework, do not copy code): `gameServer/tb/mobileGame.jsp` and its
board JS — `drawDeadStone`, the central-square hinting by move number, and the multi-pick picker.

**Could NOT confirm from code (carry forward; treat as "verify"):**
- Whether a per-variant `currentPlayer()` opening branch is strictly required for correct
  `isMyTurn` during the Renju opening (a `renjuOpeningPlayer` mirroring the swap2 arm is the safe
  move). **(verify)**
- Whether the iOS app supports **any** turn-based move POST (e.g. `renjuAction`/`MoveServlet`), or
  is strictly live-WS for sending; the `game.jsp` GET is read-only load. Assumed live-only.
  **(verify)**
- How the historic `game.jsp` endpoint behaves when handed a Renju id 31/32/81 — does it ship
  `renjuPhase`/`renjuOffers`/`renjuSwaps` (the parser ignores them) or a malformed response?
  **(verify)**
- How live games assign player 1 vs player 2 (is P1 always white, or rotation/negotiation?), and
  how iOS initiates/joins a Renju game (lobby/creation UI vs live room only). **(verify)**
- Whether coordinate axis labels (A–P, 1–15) are rendered anywhere beyond `LiveBoard`/`BoardView`
  (none found in their `draw` code). **(verify)**
- The `playersAndTables.swapSeats` / `swap2Pass` method bodies in the `TablesAndPlayer` class
  (≈`HelperClasses.swift:685`) — referenced by the room handlers but their definitions were not
  inspected; the new Renju mutators must follow the same internal pattern. **(verify)**
- Whether the central-box highlight is achievable in the current `LiveBoard.draw` architecture or
  needs a new overlay/view; the exact multi-select gesture (long-press / tap-count / explicit
  buttons); and efficient render/clear of 10 simultaneous translucent candidates. **(verify)**
- Client-side forbidden-point validation (overline / double-four / double-three) is **expected to
  be NONE** (server-enforced per the contract); do **not** port the finder. If marking is ever
  added, fetch `getForbiddenPoints` from the server. **(verify the server-only assumption.)**

*Resolved while grounding (no longer open):* the stone-color convention **is** confirmed from code
— `abstractBoard` values 1 = white / 2 = black (with `−1` masked), and engine
`colorForMove → (index % 2) + 1` is white-first; so Renju black-first ⇒ first stone value 2 via a
new black-first cadence (§9.0).

---

## 10. Sub-project 5 — Android (`pentelive-android`) Taraguchi-10 handoff

Zero-context handoff for a fresh agent wiring the Renju (Taraguchi-10) opening into the
`pentelive-android` app. **iOS and Android have no Renju support today**; this section (Android)
and §9 (iOS) are their from-scratch handoffs. Every anchor below was grep-verified against the
submodule on this branch; line numbers are as-of-now and may drift, so grep the symbol.

**TRANSPORT VERDICT — Android plays BOTH.** Unlike the React reference (live-only WebSocket),
Android has two live-game *and* turn-based code paths:
- **LIVE** — raw **SSL TCP** socket (NOT WebSocket) speaking the `dsg*TableEvent` JSON protocol,
  byte-`255` frame delimited (`SocketDSGEventHandler.run:44-84`), dispatched by an if-else chain
  in `LiveGameRoomActivity.eventOccurred:231-420`. The live server sends **NO `renjuPhase`**
  (§2.6 / §4) — the live client must **DERIVE** the opening phase from tracked echo state, exactly
  like React §8.2 (see §10.2a).
- **JSON / turn-based** — HTTP `GET gameServer/mobile/json/game.jsp` → Gson `GameResponse`
  (`Game.java:432`), moves sent as an HTTP **GET** with query params (`OkHttpPenteApi.submitMove`
  uses `get(url)` at `:174`). Here the server **ships the derived phase** in `renjuPhase` (§2.6) —
  read it directly (see §10.2b).

So Android needs **both shapes**. **Recommended order: do LIVE first** (it mirrors the React
reference port, and all of Android's existing opening UI lives in the live path). The turn-based
path has **no opening UI at all today** (the offline `Game.java`/`BoardView.java` screen only
places ordinary moves), so TB Renju opening is a larger from-scratch build and can be deferred —
but still add the read-side fields (`GameResponse`) and the `renjuAction` param so historic
viewers and TB reads don't break.

**Where Android genuinely differs from React (do not force the React shape):**
- **Language/UI:** Java + Android `Canvas` drawing + `AlertDialog`, not JSX/MUI.
- **No Protocol abstraction.** React has a `protocol/` module (decode + `MESSAGES` registry +
  `Commands` facade). Android has **none of that**: inbound is a raw `if (jsonEvent.get("dsgXEvent")!=null)`
  chain over a `Map<String,Object>` (from `jsonToMap`), and **outbound events are built by raw
  JSON string concatenation** (`LiveBoardView:156`, `LiveTableFragment.sendSwap2Choice`). There is
  **no Gson serialization on the live send path** and no `Commands.<cmd>(...)` — you add three
  `else if` arms + three handler methods + three string builders.
- **Two board views, two screens.** Offline/TB = `Game.java` + `BoardView.java`; live =
  `LiveGameRoomActivity` + `LiveTableFragment` + `LiveBoardView.java`. Both board views hardcode
  19×19 star points; fix **both**.
- **The `rules/` module is a lightweight variant registry**
  (`Variant`/`Variants`/`BoardState`), **NOT** a copy of the server `org.pente.game` engine. So
  like React, **derive the phase from echoes — do not run a client-side engine** (there isn't one).
- **Stone-color convention is already black-capable.** `BoardState.java:6` encodes `0=empty,
  1=white, 2=black, -1=forbidden`. The `currentColor()` Go (PLAY) arm (`Table.java:282`,
  `2 - moves.size()%2`, inside `if (isGo())` → `goState==PLAY`) is already black-first — Renju
  reuses that formula. Do **not** copy the Connect6 else-branch formula at `:288-293`
  (`2 - (((moves.size()-1)/2)%2)`, 2-stones-per-turn) — it is wrong for single-stone Renju.

### 10.0 Board basics (restated for this client)
- Board **15×15**, game ids **31 (Renju) / 32 (Speed Renju) / 81 (TB Renju)**. Move encoding
  `x + y·15`; **center = 112** (`7 + 7·15`); the **server auto-places** it as move 1 — the live
  client receives it as an ordinary `dsgMoveTableEvent`, it must **not** place the center itself.
- **Board background colour = `#D98880` (dusty rose)** — the canonical Renju board colour, **distinct
  from gomoku's `#A3FDEB`**. Matches the web (`renjuColor`, `gameServer/tb/gameScript.js:14`) and
  react_live_game_room (`.renju` / `VARIANT_COLORS['renju']`). Android selects the board colour by id
  in `Table.getGameColor:904-932`; set ids 31/32 to this value (see §10.3 step 3).
- **Black plays first.** Android board values: `1 = white`, `2 = black` (`BoardState.java:6`;
  confirmed in `LiveBoardView.drawStone` — `stoneColor==2` renders black). So "black first" ⇒ the
  first stone must carry board **value 2**. Today the default `currentColor()` arm
  (`Table.java:287`) returns `1 + (moves.size()%2)` → first stone = value 1 = **WHITE** (inverted
  for Renju). The fix is a Renju arm = `2 - moves.size()%2` (black-first), identical to the
  existing Go (PLAY) arm at line 282 (inside `if (isGo())` → `goState==PLAY`). Do **not** copy the
  Connect6 else-branch formula at `:288-293` (`2 - (((moves.size()-1)/2)%2)`, 2-stones-per-turn) — it
  is wrong for single-stone Renju.
- Move encoding parity: `LiveBoardView:147` already computes `playedMove = gridSize*stoneI + stoneJ`
  = `x + y·gridSize` (correct once `gridSize=15`). The bug is **decode**: `Table.addMove:199-200`
  hardcodes `/19` and `%19`.
- Phase source depends on transport (see §10.2): **LIVE derives**, **TB reads `renjuPhase`**.

### 10.1 Confirmed anchors (file : symbol)
All grep-verified in the submodule. **(WRONG today)** = breaks for Renju as-is; **(OK)** = already
correct / reusable as precedent; **(verify)** = could not fully confirm from code.

| Area | File | Symbol / fact |
|---|---|---|
| variant enum | `rules/.../pente/rules/Variant.java` | `enum Variant:13-29` — entries `(canonicalGameId, gridSize, CaptureRule, stonesPerTurn)`; live ids 1–29 (+ even speed doubles); `GO_13(23,13,NONE,1)`, `GO_19(19,19,NONE,1)`; predicates `isDPente`/`isSwap2`/`isGo` (no `isRenju`). **No 31/32/81.** **(WRONG today)** |
| variant by id | `rules/.../Variants.java` | `fromGameId:87-89` → `BY_CANONICAL_ID.get(canonical)` (odd canonical, even = id-1). `31/32/81` → **null** → NPE / silent default. **(WRONG today)** |
| variant by name | `rules/.../Variants.java` | `fromGameType:33` — substring match on `gameName`; `"Renju"` not handled → null (the TB/offline lookup path). **(WRONG today)** |
| live game names | `app/.../liveGameRoom/Table.java` | static `gameNames` map `:54-86` — ids 1→30 only (ends `30→"Speed Swap2-Keryo"`); `getGameName()` returns null for 31/32. **(WRONG today)** |
| live grid size | `Table.java` | `setGame:1090-1102` sets `gridSize` by id (`==21/22→9`, `==23/24→13`, **else 19**); no 31/32 → 19. Default `gridSize=19` (`:102`); `setGridSize:98-99` exists. **(WRONG today)** |
| live move decode | `Table.java` | `addMove:199-200` — `move_i = move/19; move_j = move%19` (hardcoded 19). For Renju, `112` decodes to `board[5][17]` (off-center). NOTE: the capture helpers (`:537+`) already use `gridSize`. **(WRONG today)** |
| live move encode | `app/.../liveGameRoom/LiveBoardView.java` | `onTouchEvent:142-156` — `stoneI = gridSize*stoneY/size`, `playedMove = gridSize*stoneI + stoneJ` = `x + y·gridSize` (matches contract once `gridSize=15`); emits move JSON at `:156`. **(OK)** |
| live render decode | `LiveBoardView.java` | `drawBoard:216-222` — `movei = move/gridSize; movej = move%gridSize`. **(OK)** |
| stone-color encoding | `rules/.../BoardState.java` | `:6` — `0 empty / 1 white / 2 black / -1 forbidden` (authoritative). **(OK)** |
| first-stone color | `Table.java` | `currentColor:279-294` — default arm `:287` `1 + (moves.size()%2)` → move 0 = value 1 = **WHITE** (inverted for Renju). The black-first pattern Renju needs is the Go (PLAY) arm `:282` `2 - moves.size()%2` (inside `if (isGo())` → `goState==PLAY`) — **NOT** the Connect6 else-branch `:288-293` (`2 - (((moves.size()-1)/2)%2)`, 2-stones-per-turn, wrong for single-stone Renju). **(WRONG today)** |
| opening-player FSM | `Table.java` | `currentPlayer:300-342` — derives to-move seat for dPente/swap2 from move count + state enums (arms `1 + moves.size()%2` at `:309,:335`); no Renju branch. **(needs Renju arm)** |
| swap-state enums | `app/.../liveGameRoom/DPenteState.java`, `Swap2State.java` | `DPenteState:4 = {NOCHOICE, SWAPPED, NOTSWAPPED}`; `Swap2State:4 = {NOCHOICE, SWAP2PASS, SWAPPED, NOTSWAPPED}`. Pattern for a new `RenjuState` enum. **(OK precedent)** |
| opening-phase tracking | `app/.../liveGameRoom/GameState.java` | `:8-12` — fields `state`, `dPenteState`, `swap2State`, `goState`; **no `renjuState`**. **(WRONG today / missing)** |
| live event dispatch | `app/.../liveGameRoom/LiveGameRoomActivity.java` | `eventOccurred:231-420` — if-else chain on `jsonEvent.get("dsg…Event")`; has `dsgMoveTableEvent:345→updateTableMove:491`, `dsgSwapSeatsTableEvent:363→swapSeats:571`, `dsgSwap2PassTableEvent:366→swap2Pass:591`, `dsgSystemMessageTableEvent:409→addTableMessage:481`. **No `dsgRenju*`.** This is the dispatch seam. **(WRONG today / missing)** |
| live transport frame | `app/.../org/pente/gameServer/event/SocketDSGEventHandler.java` | `run:44-84` — raw SSL TCP; reads bytes until `255` (`:56`), UTF-8 string (`:59`) → `notifyListeners(String)` (`:69`); outbound writes terminator `255` (`:110`). **NOT WebSocket.** **(OK)** |
| outbound build (live) | `LiveBoardView.java:156` / `LiveTableFragment.sendSwap2Choice` | raw JSON **string concatenation** via `fragment.getListener().sendEvent("{…}")`; no Gson/Commands facade on send. **(OK pattern)** |
| swap dialog precedent | `app/.../liveGameRoom/LiveTableFragment.java` | `showDPenteChoice:1017` / `showSwap2Choice:1049` — `AlertDialog.Builder` + `setItems(options, cb)`, `Gravity.BOTTOM` (`:565+,:973`); send `dsgSwapSeatsTableEvent`. **Yes/no only, no board interaction.** **(OK pattern)** |
| dialog trigger | `LiveTableFragment.java` | `addMove:452-510` — after `table.addMove(move)` checks `isDPente()&&moves==4&&NOCHOICE` etc. → shows the modal. Renju gates here off the derived phase. **(OK pattern)** |
| translucent stones | `LiveBoardView.java` | `drawStone:243-278` — Go dead stones (`stoneColor==3/4`) use `stonePaint.setAlpha(180)` (`:264,:268`). Reusable for translucent candidates. **(OK)** |
| board-tap legality | `LiveBoardView.java` | `onTouchEvent:142-153` — only checks the cell is empty (`abstractBoard[i][j]!=0`); no central-square / forbidden checks (server-side). **(OK)** |
| system-message handler | `LiveGameRoomActivity.java` | `dsgSystemMessageTableEvent:409-413` — `data.get("message")` → `addTableMessage(tableId, "* "+msg)`. The Branch-B selector prompt (server→white) routes here; gating the picker on it is new UI. **(OK, repurpose)** |
| TB JSON model | `app/.../JsonModels.java` | `GameResponse:121-154` — fields `gameName:125`, `moves`, `state:136`, `goState:137`, `dPenteState:142`, `swap2pass:143`. **No `renjuPhase`/`renjuOffers`/`renjuSwaps`.** **(WRONG today / missing)** |
| TB move submit | `app/.../net/OkHttpPenteApi.java` | `submitMove:163-176` — query params `command=move`, `gid`, `moves` (`, message`). **No `renjuAction`.** Cannot speak the §2.4 opening contract. **(WRONG today / missing)** |
| TB game load | `app/.../Game.java` | `RetrieveGame.doInBackground:432` — HTTP GET `game.jsp?gid=` → Gson `GameResponse`. **(OK)** |
| offline board size | `Game.java` | `parseGame:998-1007` — `gameType.contains("(9x9)")→9`, `("(13x13)")→13`, **else 19**; no `"Renju"` → 19. **(WRONG today)** |
| offline star points | `app/.../BoardView.java` | `drawBoard:483-487` — hardcoded index `6` (`margin+6*step`), 4 corners + center; correct for 19×19, wrong for 15×15. Live twin: `LiveBoardView.drawBoard:200-204` same. **(WRONG today)** |
| offline coord labels | `BoardView.java` | `onTouchEvent:364-406` — modulo 19 (`coordinateLetters[m%19]`, `19-(m/19)`); `coordinateLetters` (`:71`) = 19 letters A–T skip I. Renju needs first 15 (A–P skip I) and `% gridSize`. **(WRONG today)** |
| server wrapper keys | backend `…/event/DSGEventWrapper.java` (reference) | The three live frames use exact top-level keys **`dsgRenjuTaraguchiSwapTableEvent`**, **`dsgRenjuTaraguchiOffer10TableEvent`**, **`dsgRenjuTaraguchi10Select1TableEvent`** (one top-level key per frame). Android's `eventOccurred` keys + outbound strings **must** match these byte-for-byte. **(server contract)** |
| server event fields | backend `…/event/DSGRenjuTaraguchi*.java` (reference) | `…Swap`: `boolean swap`, `int move`. `…Offer10`: `int[] moves`. `…Select1`: `int move`. All extend `AbstractDSGTableEvent` → inherited `String player`, `int table`; `AbstractDSGEvent` → `long time`. Each inner object carries `player`/`table`/`time` plus its own fields. **(server contract)** |

**Resolve before coding (table-specific verify):** the survey's *protocol* pass claimed
`currentColor()` returns black (value 2) on the first move; that is **wrong** — `BoardState.java:6`
(`1=white, 2=black`) plus `currentColor:287` (`1 + moves%2` → first = value 1 = white) confirm the
first stone renders **white** today. The black-first fix (a `2 - moves%2` Renju arm) is therefore
**required**, not optional. Confirm by rendering a live Renju move 1 after the fix.

### 10.2 Two phase sources (LIVE derives · TB reads `renjuPhase`)

Heading adapted because Android is **BOTH**. The live path mirrors React §8.2; the TB path mirrors
the §2.6/§4 read.

#### 10.2a LIVE phase derivation (mirror React §8.2)
The live server sends **no `renjuPhase`**; the engine is server-side only. The Android live client
**accumulates** a tracked opening record **from the echo events** (§10.4) and derives the phase
from it + the move count — exactly the React approach. Add a new `RenjuState` enum (mirror
`DPenteState`/`Swap2State`) **plus** a tracked object on `GameState`:

```
renjuState = {
  swapWindowOpen,   // bool — is the current swap window still undecided?
  branch,           // null | 'A' | 'B' — set by the move-4 decision echoes
  offers,           // int[] | null — the 10 Branch-B candidates (offer10 echo)
  selection,        // int | null — white's pick (select1 echo)
}
// NO net-swap/orientation field. Who-owns-black comes from table.seats (the visual seat
// swap that rides dsgSwapSeatsTableEvent on a take-over, and sendPlayingPlayers on rejoin) —
// NEVER from the silent rejoin swap event (its swap bit is the current window's decision,
// not net orientation).
```

`movesLength` = stones on board (incl. the auto-center = move 1). Add a pure
`renjuPhase(movesLength, renjuState)` helper (Android has no `openingPhase.js` analogue — the
dPente/swap2 logic lives inline in `Table.currentPlayer`/`currentColor` + `LiveTableFragment.addMove`,
so put the helper on `Table`/a small `RenjuPhase` class):

| movesLength | tracked state | phase | to-move acts |
|---|---|---|---|
| 1 | swapWindowOpen | `SWAP` (window 1) | Swap, **or** decline + place move 2 ∈ 3×3 |
| 2 | swapWindowOpen | `SWAP` (window 2) | Swap, **or** decline + place move 3 ∈ 5×5 |
| 3 | swapWindowOpen | `SWAP` (window 3) | Swap, **or** decline + place move 4 ∈ 7×7 |
| **4** | **swapWindowOpen** | **`SWAP`** (window 4) | THREE actions: `swap=true` take-over → `BRANCH` (no stone) · `swap=false` **bundled with move 5 ∈ 9×9** → Branch A · `Offer10` → Branch B |
| **4** | swap decided, `branch===null` | **`BRANCH`** | Branch A: place move 5 ∈ 9×9 · Branch B: offer 10 |
| **4** | `branch==='B'`, offers present | **`SELECTION`** | white picks 1 of the 10 → becomes move 5 |
| **5** | `branch==='A'`, swap-5 undecided | **`SWAP`** (window 5) | Swap, **or** decline → then move 6 |
| **5** | `branch==='A'`, swap-5 decided | `NORMAL` (move 6 anywhere) | place move 6 |
| **5** | `branch==='B'` (selection done) | `NORMAL` (move 6 anywhere) | place move 6 — **no swap-5 window in Branch B** |
| ≥6 | — | `COMPLETE` / `NORMAL` | plain alternation; black forbidden points **server-enforced** |

> **Naming reconciliation (docs only, no code impact).** This live-derived table uses `NORMAL` and
> folds the Branch-B offer step into `BRANCH`; §10.2b and the backend enum use `MOVE` and a distinct
> `OFFERS`. Map them: live-derived `NORMAL` == server `MOVE`; the server `OFFERS` phase is represented
> inside the live `BRANCH` state (movesLength 4, `branch == null`).

**Move-4 model (live).** Three wire actions at the move-4 window: (a) `swap=true` take-over →
standalone `BRANCH` (no stone); (b) `swap=false` **bundled with move 5 in the 9×9** = Branch A
(advances to 5 moves — there is **no** stoneless move-4 decline); (c) `Offer10` = Branch B. The
standalone `BRANCH` state arises **only** after a take-over. Branch-A move 5 always arrives as a
**swap event** (`swap=false`, with the move), not a branch event.

**Rejoin / spectate (§7 current-decision-point signal).** On (re)join the server sends the
authoritative seats (`sendPlayingPlayers`) **plus exactly one** signal keyed by `numMoves`:
*nothing* (window open / opening complete), a **silent** `dsgSwapSeatsTableEvent` (window resolved
→ `MOVE`/`BRANCH`), an **offer10** frame (Branch-B selection pending), or a replayed **select1**
frame (Branch-B move 5 chosen). Android's `swapSeats:571` handler must learn the silent branch for
Renju: **advance the tracked phase for the current window only; do NOT re-swap seats** (seats are
already current from `sendPlayingPlayers`; its `swap` bit is the current window's decision, not net
orientation) — the same contract Android already honours for the dPente silent swap.

**Do not** port `RenjuState`'s server-side Taraguchi-10 engine into Android — it drags the
forbidden-point finder with it. Track the four decision variables the echoes carry; that is enough.

#### 10.2b TB phase (read `renjuPhase` from `GameResponse`, §2.6 / §4)
For turn-based Renju (`TB_RENJU=81`) the server **already ships** the derived phase. Add the three
fields to `GameResponse` and **read** them — no derivation:
`renjuPhase ∈ {SWAP, BRANCH, OFFERS, SELECTION, MOVE, COMPLETE}` plus `renjuOffers` (the persisted
Branch-B candidates) and `renjuSwaps` (packed decisions). **Caveat:** the offline/TB screen
(`Game.java`/`BoardView.java`) has **no opening UI today** — all opening dialogs/pickers live in
the live `LiveTableFragment`. So the TB opening flow (swap windows, branch, 10-pick, selection) is
a from-scratch build on the offline screen and is **recommended deferred**; add the fields +
`renjuAction` param now so the *read* side and historic viewers work, then build the TB opening UI
as a follow-up (or reuse the live pickers if the screens are unified).

### 10.3 File-by-file map (the real work)
Live-first ordering. **(L)** = live path, **(TB)** = turn-based/offline, **(both)** = shared rules.

1. **`rules/.../pente/rules/Variant.java`** *(both)* — add `RENJU(31, 15, CaptureRule.NONE, 1)`
   (captures NONE — Renju has no captures; forbidden points are server-enforced). `SPEED_RENJU`
   reuses this entry via canonical id 31 (the enum lists only odd canonicals). Add an
   `isRenju()` predicate (`this == RENJU`) alongside `isDPente()/isSwap2()/isGo()`. **`TB_RENJU=81`
   has no canonical entry** (existing TB games aren't in this enum either) — resolve via the
   `fromGameType` string path below, or add an explicit `81→RENJU` mapping **(verify which path the
   TB code hits)**.
2. **`rules/.../Variants.java`** *(both)* — `fromGameId:87-89`: ensure `31→RENJU`, `32→`(canonical
   31)`→RENJU`; add `81→RENJU` if the TB path calls `fromGameId(81)`. `fromGameType:33`: add a
   `"Renju"` / `"Speed Renju"` / `"TB Renju"` substring arm → `RENJU` (the string the server puts
   in `GameResponse.gameName` — **verify the exact value**).
3. **`app/.../liveGameRoom/Table.java`** *(L)* —
   - `gameNames:54-86`: `put(31, "Renju"); put(32, "Speed Renju");` (81 is TB, not a live table id
     — confirm live never sees 81).
   - `setGame:1090-1102`: add `else if (game==31 || game==32) gridSize = 15;` **before** the
     `else gridSize=19`.
   - `addMove:199-200`: replace `move/19`,`move%19` with `move/gridSize`,`move%gridSize` (also fixes
     Go 9/13 live). This is the **game-breaking** decode bug.
   - `currentColor:279-294`: add a Renju arm returning `2 - moves.size()%2` (black-first), mirroring
     the Go (PLAY) arm at `:282` (inside `if (isGo())` → `goState==PLAY`) — **not** the Connect6
     else-branch at `:288-293` (`2 - (((moves.size()-1)/2)%2)`, 2-stones-per-turn, wrong for Renju).
     Without it the first stone renders white.
   - `currentPlayer:300-342`: add a Renju opening branch (mirror the `isDPente`/`isSwap2` arms) that
     calls a new `renjuOpeningPlayer(moves.size(), renjuState)` so `isMyTurn` is right during the
     opening **(verify exact need)**.
   - `getGameColor:904-932`: add `31/32` → a new `renjuColor = 0xFFD98880` constant (dusty rose, the
     canonical Renju board colour, §10.0; today ids 31/32 fall through to `swap2KeryoColor` = wrong).
   - add `isRenju()` (mirror `isDPente:269` / `isSwap2:274`).
4. **`app/.../liveGameRoom/LiveBoardView.java`** *(L)* — `drawBoard:200-204`: add a Renju
   star-point branch. Match §4's `{3,7,11}` (center 7). The existing renderer draws a **5-point**
   set (4 corners + center) — for Renju that is indices **`[48, 56, 168, 176, 112]`**
   (`(3,3)/(11,3)/(3,11)/(11,11)/(7,7)`, index `= col + row·15`); or switch to the full Go-style
   9-dot set like React §8.3 (`[48,52,56,108,112,116,168,172,176]`). Encode/decode already
   `gridSize`-correct; `setGridSize:44-46` flows from `LiveTableFragment.updateTable:395`
   (`board.setGridSize(table.getGridSize())`). Reuse `drawStone` `setAlpha(180)` (`:264,:268`) for
   translucent candidates (§10.6).
5. **`app/.../liveGameRoom/GameState.java`** *(L)* — add a `RenjuState renjuState` field (new enum)
   **and/or** the tracked `renjuState` object of §10.2a. Initialize it where `dPenteState`/`swap2State`
   are reset.
6. **`app/.../liveGameRoom/LiveGameRoomActivity.java`** *(L)* — in `eventOccurred:231-420` add three
   `else if` arms (keys **must** equal the wrapper keys in §10.1):
   ```java
   } else if (jsonEvent.get("dsgRenjuTaraguchiSwapTableEvent") != null) {
       handleRenjuSwap((Map<String,Object>) jsonEvent.get("dsgRenjuTaraguchiSwapTableEvent"));
   } else if (jsonEvent.get("dsgRenjuTaraguchiOffer10TableEvent") != null) {
       handleRenjuOffer10((Map<String,Object>) jsonEvent.get("dsgRenjuTaraguchiOffer10TableEvent"));
   } else if (jsonEvent.get("dsgRenjuTaraguchi10Select1TableEvent") != null) {
       handleRenjuSelect1((Map<String,Object>) jsonEvent.get("dsgRenjuTaraguchi10Select1TableEvent"));
   }
   ```
   Add the three handler methods (mirror `updateTableMove:491` / `swapSeats:571` / `swap2Pass:591`).
   They **update opening-tracking state ONLY and place NO stones** — stones ride
   `dsgMoveTableEvent → updateTableMove`:
   - `handleRenjuSwap`: mark the current window decided; at `movesLength==4`, **any** `swap=false`
     echo carrying a valid stone ⇒ `branch='A'`. The take-over visual seat swap rides a separate
     `dsgSwapSeatsTableEvent` (already handled by `swapSeats`).
   - `handleRenjuOffer10`: `branch='B'`, `offers = (List) data.get("moves")`.
   - `handleRenjuSelect1`: `selection = data.get("move")`.
   Also extend `swapSeats:571`: in the **silent** branch for Renju, advance the tracked phase for
   the current window (do not re-swap). Repurpose `dsgSystemMessageTableEvent:409` to gate the
   Branch-B selection UI (the server→white prompt arrives here).
   Note: `jsonToMap` yields JSON numbers as `Double`/`Long` and arrays as `List` — cast `move`/`moves`
   exactly as `updateTableMove:491-507` already does.
7. **`app/.../liveGameRoom/LiveTableFragment.java`** *(L)* — the opening UI. After `table.addMove`
   (the existing `addMove:452-510` dispatch point) read the derived phase and show the right control,
   reusing the `showSwap2Choice:1049`/`showDPenteChoice:1017` pattern (`AlertDialog.Builder.setItems`,
   `Gravity.BOTTOM`) for the yes/no cases and `sendSwap2Choice:1092` (raw JSON string →
   `mListener.sendEvent(...)`) for sending:
   - **SWAP windows 1–3:** "Swap (take over)" / "Don't swap" — decline **bundles** the next opening
     stone (constrained to the move's central square).
   - **move-4 SWAP window:** three choices — take-over, Branch A (decline + place move 5 ∈ 9×9),
     Branch B (offer 10).
   - **BRANCH** (after take-over): place move 5 ∈ 9×9, or offer 10.
   - **SELECTION:** white picks one of the 10.
   The board interaction (central-box highlight, 10-pick multi-select, translucent candidates,
   selection screen) is **new** — see §10.6.
8. **`app/.../JsonModels.java`** *(TB)* — add to `GameResponse:121-154`:
   `public String renjuPhase; public String renjuOffers; public Integer renjuSwaps;`
   These match the backend `GameResponse.java:45-47` exactly (confirmed): `renjuPhase` (`String`,
   one of `SWAP|BRANCH|OFFERS|SELECTION|MOVE|COMPLETE`, else null), `renjuOffers` (`String`,
   comma-separated offered moves, else null), `renjuSwaps` (`Integer`, packed opening word, else
   null). The String/String/Integer POJO is correct. Gson tolerates missing fields, so this is
   backward-safe.
9. **`app/.../net/OkHttpPenteApi.java`** *(TB)* — `submitMove:163-176`: add a `renjuAction` query
   param (overload `submitMove(gid, moves, message, renjuAction)` →
   `.addQueryParameter("renjuAction", …)`) to speak the §2.4 contract (`swap` / `move4` / `select`).
10. **`app/.../Game.java` + `app/.../BoardView.java`** *(TB, deferrable)* —
    `Game.parseGame:998-1007`: add `"Renju"` → `gridSize=15`. `BoardView.drawBoard:483-487`: Renju
    star points (same set as step 4). `BoardView.onTouchEvent:364-406` + `coordinateLetters:71`: use
    `% gridSize` and the first-15 label set **A–P skipping I** (instead of `%19` / 19 letters). The
    TB opening UI itself (no precedent on this screen) is the deferred follow-up.

### 10.4 Wire examples (verified keys + fields)
**Live (raw JSON strings).** Android builds outbound frames by **string concatenation** (no
`Commands` facade) — e.g. existing moves: `sendEvent("{\"dsgMoveTableEvent\":{\"move\":" + m +
",\"moves\":[" + m + "],\"player\":\"" + me + "\",\"table\":" + table + ",\"time\":0}}")`
(`LiveBoardView:156`). Inbound arrives as a `Map<String,Object>` via `jsonToMap` with a
**server-stamped non-zero `time`**. One literal per event (table 5, center 112, 15×15):

**Swap event** — take-over, decline+place, or Branch-A move 5 (all share this event):
```json
// outbound: decline window-1 swap + place move 2 at col8,row7 (=113, in 3×3)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": false, "move": 113, "player": "alice", "table": 5, "time": 0 } }
// outbound: take over the side (no stone; move ignored on swap=true — verify sentinel, send 0)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": true,  "move": 0,   "player": "bob",   "table": 5, "time": 0 } }
// inbound echo (server time stamped); the stone, if any, arrives separately as dsgMoveTableEvent
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": false, "move": 113, "player": "alice", "table": 5, "time": 1718400000123 } }
```
**Offer 10** (Branch B — black offers ten 5th-move candidates, no two D4-symmetric — offsets
`(1,0)(2,0)(3,0)(4,0)(1,1)(2,1)(3,1)(4,1)(2,2)(3,2)` about centre 112 → **10 distinct {|dx|,|dy|} orbits**):
```json
{ "dsgRenjuTaraguchiOffer10TableEvent": { "moves": [113,114,115,116,128,129,130,131,144,145], "player": "alice", "table": 5, "time": 0 } }
```
**Select 1** (white picks one of the ten → becomes move 5; the stone follows as a `dsgMoveTableEvent`):
```json
{ "dsgRenjuTaraguchi10Select1TableEvent": { "move": 130, "player": "bob", "table": 5, "time": 0 } }
```
**Stone (always separate):** `{ "dsgMoveTableEvent": { "move": 113, "moves": [113], "player": "alice", "table": 5, "time": 0 } }` — the §10.1 `LiveBoardView:156` format.

**Turn-based (HTTP query params, §2.4).** `OkHttpPenteApi.submitMove` builds
`gameServer/tb/game?command=move&gid=<gid>&moves=<payload>&renjuAction=<action>`:

| `renjuAction` | `moves` payload | meaning |
|---|---|---|
| `swap` | `1` | take over opponent's side (no stone) |
| `swap` | `0,<move>` | decline + play the next opening stone (move-1..3 windows) |
| `move4` | `<d>,<s1>[,…,s10]` | move-4 decision. `d`=1 declining swap (SWAP phase), 0 if swap already taken (BRANCH). Then **1 stone = Branch A** (move 5, must be 9×9) or **10 stones = Branch B offers** |
| `select` | `<move>` | white picks one of the 10 offered moves (becomes move 5) |

Concrete: decline window-1 + place 113 → `…&moves=0,113&renjuAction=swap`; offer ten →
`…&moves=1,113,114,115,116,128,129,130,131,144,145&renjuAction=move4`; select → `…&moves=130&renjuAction=select`.

**Contract reminders (§7):** never place stones from the three echoes — stones ride
`dsgMoveTableEvent`. On (re)join, take seats from `sendPlayingPlayers`; the rejoin signal is
exactly one of {*nothing* / silent `dsgSwapSeatsTableEvent` / `offer10` / `select1`} — its silent
swap `swap` bit is the **current window's** decision, **not** net orientation. **Recovery:** if a
declined-swap's bundled stone is rejected, the decline is already committed → recover by re-sending
the stone as a plain `dsgMoveTableEvent`; if the ten offers are rejected, the move-4 decline +
Branch B are already committed → recover by re-sending a corrected ten.

### 10.5 Offer symmetry dedup (client-side, UX nicety)
The ten Branch-B offers must contain no two D4-symmetric duplicates. The server already rejects
violations (`RenjuState.offerFifthMoves` → `offerFifthMove`), so client-side checking is a **UX
nicety** (instant feedback vs a round-trip error) — recommended, not required. **The ten offers are
NOT box-constrained** — any in-bounds, empty, non-D4-symmetric point is legal (corners included);
only the **Branch-A** move 5 is restricted to the 9×9. So the 10-pick picker must allow the **whole
board** (minus occupied + symmetric-duplicate cells).

Port the algorithm to Java (15×15, center `(7,7)`):
- For move `m`: `x = m % 15`, `y = m / 15`, `dx = x - 7`, `dy = y - 7`.
- The **8 D4 images** of `(dx,dy)`: rotations `(dx,dy)`, `(-dy,dx)`, `(-dx,-dy)`, `(dy,-dx)` and
  reflections `(-dx,dy)`, `(dx,-dy)`, `(dy,dx)`, `(-dy,-dx)`. Map each back:
  `m' = (tx + 7) + (ty + 7)·15`.
- Reject an offer if **any** of its 8 images equals an already-accepted offer. Maintain a running
  set of all images of accepted offers and test membership (`n/10` counter, §10.6).

Mirror the proven reference so the client agrees with the server exactly: the JSP port
`renjuRotate` / `renjuStabilizer` / `renjuIsSymmetricDup` in `gameServer/tb/mobileGame.jsp` (itself
a JS port of `SimpleGridState.rotateMove` + the position stabilizer, §3).

### 10.6 New UI primitives (no Android precedent)
The swap2/dPente dialogs are plain yes/no (`AlertDialog` + `setItems`, §10.1) — the Renju opening
needs board-level interaction with **no analogue** in this client. Single-tap placement
(`LiveBoardView.onTouchEvent:142-153`) is the only existing board interaction; there is **no**
multi-select, **no** zone highlight (`drawBoard:171-241` draws only lines + star points).
- **Central-box highlight** — a new `Canvas` draw layer in `LiveBoardView.drawBoard` highlighting
  the legal cells of the N×N square about center 112 for the current opening move:
  **moves 2/3/4/5 → 3×3 / 5×5 / 7×7 / 9×9** (radius 1/2/3/4). Applies during the placement phase and
  the **decline-and-place** action of a SWAP window. **Only single-stone placements (moves 2–5,
  incl. Branch-A move 5) are box-constrained** — do **not** draw a box for the Branch-B offer-10
  picker (§10.5).
- **Translucent "dead-stone" candidates** — render the 10 Branch-B offers (and, during SELECTION,
  the non-picked nine) as translucent black. **Reuse the existing primitive:** `drawStone:243-278`
  already applies `stonePaint.setAlpha(180)` for Go dead stones (`:264,:268`) — draw candidates with
  the same alpha (value 2 + alpha) rather than adding a new path.
- **10-pick multi-select + submit** — tap to add a candidate, tap again to remove, `n/10` counter,
  submit button (a new dialog/overlay; the `Gravity.BOTTOM` dialog chrome from
  `showSwap2Choice:973+` is the styling precedent). **Validation before send:** exactly **1** stone
  (and inside the 9×9) for Branch A, or exactly **10** distinct, non-D4-duplicate (§10.5) stones
  **anywhere on the board** for Branch B; alert otherwise. Branch is inferred from the count
  (1 = continue / 10 = offer), matching the `ServerTable`/`MoveServlet` contract.
- **White selection screen** — gate on the `dsgSystemMessageTableEvent` prompt (`:409`); show the
  ten offered candidates and let white tap one → send `dsgRenjuTaraguchi10Select1TableEvent`. The
  picked candidate renders solid (value 2), the rest translucent.

Visual reference (different framework — do not copy code): `gameServer/tb/mobileGame.jsp` and its
board JS — `drawDeadStone`, the central-square hinting by move number, the multi-pick picker.

### Could NOT confirm (carry into QA / verify before relying on)
- **Stone-color contradiction (resolved, confirm visually):** the *protocol* survey pass said
  `currentColor` is already black-first; the *board* pass + verified `BoardState.java:6` +
  `currentColor:287` say the first stone is **white** today. The §10.0/§10.3 black-first fix
  (`2 - moves%2`) is required — confirm by rendering a live Renju move 1.
- **`swap=true` take-over `move` sentinel** — the field is ignored server-side; send `0` and verify
  against `ServerTable.handleRenjuSwap`.
- **`renjuOpeningPlayer` need** — whether a Renju arm in `Table.currentPlayer` is strictly required
  for correct `isMyTurn` during the opening (mirror `swap2OpeningPlayer`; the safe move). **(verify)**
- **`TB_RENJU=81` resolution** — does the TB/offline path call `Variants.fromGameId(81)` (needs a
  canonical `81→31` mapping) or `Variants.fromGameType(gameName)` (string)? And **what string** does
  the server put in `GameResponse.gameName` / the live `gameNames` for Renju? **(verify)**
- **`GameResponse` JSON types (CONFIRMED — was a verify item):** matched against backend
  `GameResponse.java:45-47` — `renjuPhase` (`String`), `renjuOffers` (`String`, comma-separated), and
  `renjuSwaps` (`Integer`). The String/String/Integer POJO in §10.3 step 8 is correct; no further verify.
- **Server auto-center on the live socket** — the contract says the server auto-places move 1 (112);
  confirm Android receives it as an ordinary `dsgMoveTableEvent` (and `movesLength` includes it), so
  the client never places the center. **(verify)**
- **`dsgSystemMessageTableEvent` for the Branch-B selector** — confirm the server actually emits it
  to the Android selector and that it is sufficient to gate the picker (vs needing a dedicated signal). **(verify)**
- **Index parity end-to-end** — contract is `x + y·15` (x = low component, `move%15`). Android
  encodes `gridSize*row + col` = `x + y·gridSize` (`LiveBoardView:147`), consistent; confirm after
  the `addMove:199-200` `/gridSize` fix. (This closes the survey's open "col+row·15 vs row·15+col"
  question → it is `x + y·15`.) **(verify)**
- **`gameHasCaptures` (`Table.java:~900`)** — current `game != 5,6,13,14` would let Renju **detect**
  captures; confirm Renju (ids 31/32) is excluded (it has none). **(verify)**
- **Other hardcoded-19 assumptions** — undo, resignation, message formatting on either board view. **(verify)**
- **Arena mode** — `isArenaTable` Renju opening behavior is unspecified. **(verify)**
- **`Canvas` layer / z-order** — for the central-box highlight + translucent candidates relative to
  stones; drawing order not specified. **(verify)**
- **TB opening UI scope** — there is **no** turn-based opening UI on the offline `Game`/`BoardView`
  screen today (all opening UI is live-only). Confirm whether TB Renju opening is required now or
  deferred (this handoff recommends live-first; wire only the TB **read** side + `renjuAction`
  initially). **(verify)**
