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

The full action set is **three** — `swap` / `move` / `select`; a **phase-driven** client (reading `renjuPhase`, §2.6) submits the one matching the current phase. **Branch A vs Branch B is inferred from the `move` stone count** (1 stone = Branch A, 10 stones = Branch B) — there is no separate branch or offer request:

| `renjuAction` | phase | `moves` payload | server behavior |
|---|---|---|---|
| `swap` | SWAP | none (`moves` ignored) | Take over the opponent's side at the open swap window — seats swap, **no stone** placed. The next decision (branch / next stone) arrives as a subsequent `move`. |
| `move` | SWAP / BRANCH / MOVE | `<m>` (1 stone) | Auto-declines a pending swap first (if `isAwaitingSwapDecision`), then places one stone — windows 1–3 → the next opening stone; **at the branch point** (move 4, branch unchosen; fresh-decline *or* post-take-over) → Branch A move 5 (restricted to the 9×9 centre); MOVE phase → a plain opening stone. |
| `move` | SWAP@4 / BRANCH | `<s1>,…,<s10>` (10 stones) | Auto-declines a pending swap, then Branch B: take the ten-offer branch and validate + persist the ten 5th-move offers, **atomically** (pre-validated; nothing persists if any offer is illegal). Only valid at the branch point. |
| `select` | SELECTION | `<m5>,<m6>` (2 stones) | **Atomic**: commit one of the ten offered moves as **move 5 (black)** *and* place **move 6 (white)** in one request → opening complete. Stores neither stone unless both are legal (m5 was offered; m6 empty, in bounds, ≠ m5). |

Notes: **Branch A vs Branch B is inferred from the `move` stone count alone** (1 = A, 10 = B) — there is no separate branch or offer request. **`swap` is always a take-over** (no stone; it never carries a `0`); **declining a swap is now implicit in sending a `move`** — a windows-1–3 decline is a one-stone `move`, not a `swap "0,m"`. The server **pre-validates** the Branch-B ten offers and the 2-stone `select` before any mutation, so there is **no half-applied opening** (nothing persists if any offer is illegal, or if either `select` stone is illegal). The server still **guards each action against the pending phase** (`matchesPending`): a mismatch returns a phase-specific error (e.g. "Renju action does not match the pending decision.", "Selected move was not offered."), not always the generic "Invalid move". **MOVE / COMPLETE (non-opening) moves take a plain `move` with NO `renjuAction`.** The server validates everything authoritatively (central squares, forbidden points, offer symmetry/distinctness via `offerFifthMove`); client checks are UX only. (The removed actions — `branch`, `offer`, `move4`, the `moves[0]` decline/take-over sentinel, and the standalone `swap "0,<move>"` decline — no longer exist.)

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
  - Phase-driven controls (mirrors the dPente/swap2 button block) over the three actions (§2.4): swap windows → "Swap (take over)" (a `swap`) / "Don't swap" (decline+place is a one-stone `move`); **move 4** → "Swap (take over)" or place **1 stone** (Branch A) / **10 stones** (Branch B) — branch inferred from the `move` stone count (alert if neither, or if a lone Branch-A stone is outside 9×9); selection → pick a 5th-move offer **and** your own move 6 → one atomic 2-stone `select`. No separate OFFERS/BRANCH steps.
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
- [ ] Swap windows after moves 1–4: a **`swap`** (take over, no stone) or **decline + place the next stone** as a one-stone **`move`** (declining is implicit in the `move`). At move 4 the `move` *is* the branch (see next bullet); the live client bundles the move-5 stone with the decline — see §8.2.
- [ ] Move-4 branch by the **`move` stone count**: 1 stone = Branch A move 5 (9×9), 10 stones = Branch B offers (atomic). Reject counts ≠ {1,10}. There is no separate branch/offer request.
- [ ] 10-offer picker with **symmetry dedup** (port `rotateMove` + position stabilizer; or just let the server reject and surface the error).
- [ ] Selection screen: white picks one of the 10 offered moves **and** places its own move 6 → one atomic 2-stone **`select`** (`<m5>,<m6>`) that completes the opening.

**Protocol**
- [ ] Speak the §2.4 contract: the THREE `renjuAction` values (`swap`/`move`/`select`) + their `moves` payloads, to `MoveServlet` / the live server. The JSON read endpoint is `gameServer/mobile/json/game.jsp` (`GameResponse`); moves POST to `/gameServer/tb/game`.
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
- **Take-over (`swap=true`) no-move sentinel — RESOLVED: send `-1`.** `handleRenjuSwap` reads `move` but never uses it on `swap=true` (nor on the bare window-5 decline) — it is a pure sentinel. Use `-1`, not `0`: `0` is the legal corner cell `(0,0)`, so it is ambiguous; `-1` is not a board index and matches the house no-move convention (`ServerTable:1529` emits `move = -1` for "no single move"). The client reducer (`utils.js renjuSwap`) also ignores `move`, so `-1` is safe end-to-end.
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
// outbound: take over the side (no stone). move = -1 is the no-move sentinel — the server ignores
// `move` on swap=true; -1 (not 0, a legal corner cell) is unambiguous (cf. ServerTable:1529)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": true,  "move": -1,  "player": "bob",   "table": 5, "time": 0 } }
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

> **CORRECTION (see §11).** iOS plays **BOTH** transports. This section (§9) covers the **live**
> path only (the Swift `PenteLiveSocket`/`TableViewController` stack). The original "LIVE ONLY /
> read-only viewer" verdict was **wrong**: the Objective-C `BoardViewController` is iOS's
> **interactive turn-based board** (`boardTap:`/`submitMove:`/`submitMoveToServer` building
> `command=move`), and it *does* read `game.jsp` JSON. The turn-based Renju handoff is **§11** —
> not deferred. (Unlike the live path, the TB path reads the **server-shipped** `renjuPhase`.)

**TRANSPORT VERDICT (live path): derive the phase, like §8.2 (React).** On the live socket the iOS
app is a raw-TCP / WebSocket client: `PenteLiveSocket.swift` opens a `GCDAsyncSocket` and reads
255-delimited JSON frames (`separator = Data([255])`, `PenteLiveSocket.swift:25`), dispatching by
top-level key in `processEvent` (`:105-174`); moves are **sent** as a hand-built
`dsgMoveTableEvent` dict via `socket.sendEvent(...)` (`TableViewController.sendMove:547`). The
**live Swift stack** has no `renjuPhase`/`GameResponse` consumer, so the live client gets **NO
`renjuPhase` on the wire** and must **derive** the Taraguchi-10 phase from tracked decision-echo
state (§9.2), identical in spirit to the React port. (The turn-based ObjC `BoardViewController`
path — which reads the server-shipped `renjuPhase` and submits `command=move&…&renjuAction=…` — is
documented separately in **§11**.)

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

// outbound: take over the side (no stone). Send move -1 — the no-move sentinel; the server ignores
// `move` on swap=true (handleRenjuSwap reads getMove() but never uses it), and -1 (not 0 = corner cell) is unambiguous.
["dsgRenjuTaraguchiSwapTableEvent": ["swap": true, "move": -1, "player": me, "table": table.table, "time": 0]]
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
   `.addQueryParameter("renjuAction", …)`) to speak the §2.4 contract — the three actions
   `swap` (take over) / `move` (1 stone = decline+place / Branch A; 10 stones = Branch B) / `select`
   (2 stones = chosen 5th + move 6), phase-matched (§2.4 / §12).
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
// outbound: take over the side (no stone). move = -1 no-move sentinel — server ignores `move` on
// swap=true; -1 (not 0, a legal corner cell) is unambiguous (cf. ServerTable:1529)
{ "dsgRenjuTaraguchiSwapTableEvent": { "swap": true,  "move": -1,  "player": "bob",   "table": 5, "time": 0 } }
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

**Turn-based (HTTP query params).** `OkHttpPenteApi.submitMove` builds
`gameServer/tb/game?command=move&gid=<gid>&moves=<payload>&renjuAction=<action>`. **Full
phase-driven contract: §2.4 and §12** — the three actions, each matching the server-shipped phase:

| `renjuPhase` (read) | `renjuAction` | `moves` payload | meaning |
|---|---|---|---|
| SWAP | `swap` | none | take over opponent's side (no stone) |
| SWAP | `move` | `<m>` (1 stone) | decline + place the next opening stone (windows 1–3); at the move-4 window 1 stone = Branch A move 5 (9×9) |
| SWAP@4 / BRANCH | `move` | `<s1>,…,<s10>` (10 stones) | Branch B: the ten 5th-move offers (atomic — branch inferred from the stone count) |
| BRANCH | `move` | `<m>` (1 stone) | Branch A move 5 (9×9) after a take-over |
| SELECTION | `select` | `<m5>,<m6>` (2 stones) | commit the chosen 5th (black) + place move 6 (white) → opening complete |
| MOVE / COMPLETE | *(none)* | `<m>` | plain move, no `renjuAction` |

Concrete (one action per phase): decline window-1 + place 113 → `…&moves=113&renjuAction=move`; take over →
`…&renjuAction=swap` (no `moves`); Branch B offers →
`…&moves=113,114,115,116,128,129,130,131,144,145&renjuAction=move` (10 stones); select →
`…&moves=130,131&renjuAction=select` (chosen 5th + move 6). Branch A vs B is inferred from the
`move` stone count (1 vs 10); there is no separate branch/offer request.

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
- **`swap=true` take-over `move` sentinel — RESOLVED: send `-1`.** `handleRenjuSwap` ignores `move`
  on `swap=true` (and on the bare window-5 decline); use `-1` (not `0`, a legal corner cell) — the
  house no-move convention (`ServerTable:1529`).
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

---

## 11. Sub-project 6 — iOS (`penteLive-iOS`) **turn-based** (correspondence) Taraguchi-10 handoff

This is the **turn-based (correspondence, days-per-move-over-HTTP) complement to the live iOS handoff in §9** — and it **corrects the §9 transport verdict**. §9 declared iOS "LIVE ONLY" and called the Objective-C `BoardViewController` a "read-only viewer." **That is wrong.** `BoardViewController` IS iOS's interactive turn-based board: its header declares **`boardTap:`** (`BoardViewController.h:77`, a `UILongPressGestureRecognizer` wired via `boardTapRecognizer:75`) and **`submitMove:`** (`:79`); the implementation has `submitMove:`/`submitMoveToServer` (`BoardViewController.m:1197`/`:1216`) which build a `game?command=move…&gid=…&moves=…&message=…` request inline (`:1275-1295`) and dispatch it via **GET** (`setHTTPMethod:@"GET"`, `:1302`) to `gameServer/tb/game`. The interactive-TB launcher is `GamesTableViewController.m`, which opens this board for play with `[boardController setActiveGame:YES]` (`:2166`, `:3658`). So iOS plays **BOTH** transports: **live** via the Swift `PenteLiveSocket`/`TableViewController` stack (§9), and **turn-based** via the ObjC `BoardViewController` (this section). The live and TB paths are different code in different languages and must be wired for Renju **separately** — §9 covers the Swift live stack; §11 covers the ObjC TB stack. Every anchor below was grep-verified against the `penteLive-iOS` submodule on this branch (a **separate repo** — do not edit it from this one); line numbers drift, so grep the symbol.

### 11.0 Board basics (restated for the TB board)
- Board **15×15**, game ids **31 (Renju) / 32 (Speed Renju) / 81 (TB Renju)**. The TB board most often shows **`TB_RENJU=81`** (turn-based games), but `BoardViewController` also replays finished live games, so all three ids must size correctly. Move encoding `x + y·15`; **center = 112** (`7 + 7·15`); the **server auto-places** the center as move 1 — it arrives inside the `game.jsp` `moves` array, so the client only needs the board sized to 15 (no client auto-center).
- **Board background colour = `#D98880` (dusty rose)** — the canonical Renju board colour, **distinct from gomoku's `#A3FDEB`** (iOS gomoku ≈ RGB `0.612,1,0.898`, `BoardVariantMapping.swift:56-57`). `#D98880` ≈ RGB `0.851,0.533,0.502`. This is the **same** value §9.3 step 2a adds to the shared `BoardVariantMapping.backgroundColor(for:.renju)` case — **(verify the ObjC TB board actually consumes that `@objc` bridge** — `@objc(backgroundColorForVariant:boatPente:)`, `BoardVariantMapping.swift:33` — **rather than a separate ObjC colour path).**
- **Black plays first.** Stone-value convention on the TB board: `abstractBoard` cell value **1 → light/white stone, 2+ → dark/black** (the value→fill test lives in `BoardView.m:193-196`) — **matches** the task's "board value 2 = black." (The `BoardView.h:13-15` `#define WHITE 0 / BLACK 1 / RED 2` is a **legacy palette enum, distinct** from the `abstractBoard` value convention — do **not** conflate them.) "Black first" ⇒ the first stone (the auto-center) must render as **value 2**. The TB board is **replay-driven** (it re-plays the `moves` array from `game.jsp`), so first-stone colour is set by the replay loop's parity, not a live cadence — ensure the Renju replay assigns value `2` to move 0 (black-first), the inverse of gomoku/Pente white-first.

### 11.1 Confirmed anchors (file : symbol)
All grep-verified in the submodule on this branch. **(WRONG today)** = the path Renju ids 31/32/81 hit now, incorrect for Renju; **(OK)** = correct / reusable as precedent; **(verify)** = not fully confirmed from code. Files are ObjC unless noted Swift.

| Area | File | Symbol / fact |
|---|---|---|
| interactive TB launcher | `test1/GamesTableViewController.m` | opens the board **for play**: `[boardController setActiveGame:YES]` (**:2166**, **:3658**) → `replayGame` → `[[boardController boardTapRecognizer] setEnabled:YES]`. This is the interactive-TB entry point. **(OK)** |
| read-only replay path | `test1/PenteWebViewController.swift` (Swift) | `webView(…decidePolicyFor…)` **:39/:47** detects `gameServer/tb/game?gid=` URLs, extracts the gid (**:47-52**), instantiates `BoardViewController` (**:69**), but sets `activeGame=false` (**:73**) and `boardTapRecognizer.isEnabled=false` (**:75**) **before** `replayGame()` (**:76**) — a **read-only** replay, not interactive play. **(OK)** |
| interactive TB board (the §9 correction) | `test1/BoardViewController.h` | **`boardTap:` IBAction :77** (`UILongPressGestureRecognizer *boardTapRecognizer :75`) + **`submitMove:` IBAction :79** → this IS the interactive TB board, not a read-only viewer. `int abstractBoard[19][19]` **:36** (sizing, see below). **(OK / sizing WRONG)** |
| game load (read) | `test1/BoardViewController.m` | `replayGame` **:1380** → `GET …/gameServer/mobile/json/game.jsp?gid=%@` **:1422** (prod) / **:1427** (localhost); `NSJSONSerialization` **:1453**. **(OK)** |
| JSON parse fields | `test1/BoardViewController.m` | `replayGame` parse block **:1465-1522** reads `canHide`/`canUnHide`(:1465-66), `player1`/`player2`(:1473-74), `currentPlayer`(:1475), `undoRequested`(:1476), `sid`(:1477), `moves`(:1478-81, a comma-separated `String` split via `componentsSeparatedByString:@","`), `rated`(:1485), `privateGame`(:1486), `gameName`(:1488), `messageNums`(:1509), `messages`(:1512), `cancel`(:1516). **Opening-state precedent: `dPenteState` IS parsed at :1767-1768.** **No `renjuPhase`/`renjuOffers`/`renjuSwaps`.** **(WRONG today)** |
| move submit (URL) | `test1/BoardViewController.m` | `submitMoveToServer` **:1216-1331**; builds the URL inline (**:1271-1299** — **:1275/:1281** no-message variant, **:1288/:1295** with-message) and dispatches it via **GET** (`setHTTPMethod:@"GET"` **:1302**) to `gameServer/tb/game`. (The `:2181/:2185` POST is `requestUndo`'s, not this submit.) **No `renjuAction` param.** **(WRONG today)** |
| move-string build | `test1/BoardViewController.m` | `submitMoveToServer` move-string construction **:1219-1253** (Connect6 / D-Pente / Swap2 via `finalMove`, `dPenteMove1-4`, `swap2Move1-3`). **No renju swap/move/select payload formats.** **(WRONG today)** |
| board tap (placement) | `test1/BoardViewController.m` | `boardTap:` impl **:644**; computes move index, checks the empty cell, stores `finalMove`, updates preview stone **:694-755**. Ordinary single-stone placement only; **no opening dialog** beyond dPente/swap2. Reusable; needs a central-box gate for Renju. **(OK)** |
| opening-UI precedent | `test1/BoardViewController.m` | `swap2Opening`/`swap2Choice` flags **:84-85**, `dPenteChoiceLabel` `@synthesize :52` / header `UILabel BoardViewController.h:59`, show/hide **:1824-1876**; D-Pente/Swap2 show `player1Button`/`player2Button`; Swap2 adds `passButton :1862`. **Yes/no/pass only — no board interaction, no Renju.** **(WRONG today for Renju)** |
| game registration | `test1/SocialViewController.swift` (Swift) | `gameNames` dict **:28-35** (TB ids `51…75`); `gameNamesArray` **:36-43** (the game-picker list). **`Renju`(31)/`Speed Renju`(32)/`Turn-based Renju`(81) absent** from both → no name, not pickable. **(WRONG today)** |
| global grid size | `test1/BoardViewController.m` | `int … gridSize = 19` **:103**; reset to 19 in `replayGame` **:1383**; adapted only for Go (`9`/`13`) at **:1888/:1891**. **No `15` for Renju.** **(WRONG today)** |
| board-array sizing | `test1/BoardViewController.h:36` / `.m:78` | `int abstractBoard[19][19]` / `int abstractGoBoard[19][19]`. **A 15×15 board fits inside the 19×19 array (15<19) — no realloc strictly required;** the breakage is the **decode math + iteration bounds**, not capacity. **(WRONG today — math)** |
| coordinate math | `test1/BoardViewController.m` | hardcoded `/19`, `%19` at **:564,:574,:590,:605,:620,:746,:831-833**; `char coordinateLetters[19]` (A–T skipping I) **:93-94**, accessed `coordinateLetters[move % 19]` / `19 - (move/19)` (**:831/:833**). Renju needs `% gridSize` and the **first 15 labels A–P skipping I**. **(WRONG today)** |
| TB board render | `test1/BoardView.m` | `gridSize` default 19 **:29/:63**; grid loop `for (i=0; i<gridSize; ++i)` **:86-95**; **`drawRect` decode `i = stoneInt/gridSize`, `j = stoneInt%gridSize` :219-220/:231-232/:270/:280 — already `gridSize`-aware (OK)**; **star points: 5 circles hardcoded for 19×19 :150-176 (WRONG for 15×15)**; stone fill value `1→light / 2+→dark` **:193-196 (OK, matches 2=black)**. |
| stone palette / fill | `test1/BoardView.h:13-15` (palette) + `StoneView` decl `BoardView.h:17-26` / impl `BoardView.m:292-359` | `#define WHITE 0/BLACK 1/RED 2` (legacy palette, **distinct** from `abstractBoard` 0/1/2 value convention); `abstractBoard` ivar `:29`. The value→fill test (1→light/white, 2+→dark) is in `BoardView.m:193-196`. (No `StoneView.m` exists — `StoneView` lives inside `BoardView.m`.) **(OK — don't conflate the two conventions)** |
| variant enum (shared w/ §9) | `test1/PenteEngine/PenteVariant.swift` (Swift) | `@objc enum PenteVariant: Int` **:6**, cases `pente=0 … connect6=10` (**:7-17**); **no `renju`**, next free raw value **11**. §9.3 step 4 adds `case renju = 11`. **(WRONG today)** |
| variant→colour map (shared w/ §9) | `test1/BoardVariantMapping.swift` (Swift) | `backgroundColor(for:boatPente:)` **:33-60** — exhaustive `switch` over all `PenteVariant` cases, **no `default`** (adding `.renju=11` won't compile until a case is added); `variant(forGameType:)` **:8-28** has a `.pente` fallback (**:11/:28**), no Renju gameType. §9.3 step 2/2a adds the `.renju` `#D98880` case + (if needed) a Renju gameType branch. **(WRONG today)** |
| ObjC consumption of the colour bridge | `test1/BoardViewController.*` / `BoardView.*` | **(verify)** whether the ObjC TB board reads its background from the `@objc(backgroundColorForVariant:boatPente:)` bridge (`BoardVariantMapping.swift:33`) — in which case §9.3 step 2a's `.renju` case covers it — **or from a separate ObjC colour path that also needs a Renju branch.** **(verify)** |

### 11.2 TB phase + transport — the server **ships** `renjuPhase` (read, don't derive)
**The defining difference from §9 (live).** On the **live** socket there is no `renjuPhase` on the wire and the client must **derive** the Taraguchi-10 phase from tracked decision echoes (§9.2 / §10.2a). On the **turn-based** path the server has **already derived and resolved the phase** (`TBGame.getRenjuPhase()`, §2.6) and **ships it in the JSON**. So the iOS TB board **READS** the phase directly — **there is NO client-side phase derivation here**, and no `openingPhase`-style classifier to port. This mirrors Android's TB read note (§10.2b).

**Read path.** `replayGame` (`BoardViewController.m:1380`) does `GET gameServer/mobile/json/game.jsp?gid=<id>` (`:1422`) → a Gson `GameResponse`. Add three fields to whatever model receives that JSON and parse them next to where the parser **already reads `dPenteState` (`:1767-1768`)** — the existing opening-state-field precedent. Field shapes (§2.6; types **confirmed** against the backend `GameResponse.java:45-47` per §10.3 step 8):
- **`renjuPhase`** — `String`, one of **`SWAP` | `BRANCH` | `OFFERS` | `SELECTION` | `MOVE` | `COMPLETE`**, else `null` (non-Renju).
- **`renjuOffers`** — `String`, **comma-separated** offered move indices, else `null`.
- **`renjuSwaps`** — `Integer` (packed opening word), else `null`.

The read phases map to the §11.5 UI: `SWAP` → swap window (take over, or decline+place a one-stone `move`; at move 4 the `move` is the branch — 1 stone = Branch A, 10 = Branch B); `BRANCH` (only after a take-over) → place a one-stone `move` (Branch A) or a 10-stone `move` (Branch B offers); `SELECTION` → white commits a chosen 5th-move offer **and** its own move 6 in one atomic 2-stone `select`; `MOVE` → constrained central-square placement (moves 2–5, incl. Branch-A move 5) sent as a plain move; `COMPLETE` → ordinary alternating play (black forbidden-points server-enforced). Under the single-request contract Branch B and its ten offers travel as that one branch-point `move`, so the client never has to act on a standalone `OFFERS` phase.

**Submit path — the §2.4 `MoveServlet` contract over the existing `submitMoveToServer` HTTP request.** Today `submitMoveToServer` (`:1216`) builds the URL inline (`:1271-1299`) — `gameServer/tb/game?command=move&mobile=&gid=<gid>&moves=<payload>&message=<msg>` — and dispatches it via **GET** (`setHTTPMethod:@"GET"`, `:1302`). For Renju the client **reads `renjuPhase`** (above) and **appends `&renjuAction=<action>` to that GET query string**, with a renju-shaped `moves` payload. The contract is **phase-driven** — submit the action that matches the phase the server shipped (verified `MoveServlet.java:422-544`; identical to Android's TB table, §10.4):

| `renjuPhase` (read) | `renjuAction` | `moves` payload | meaning |
|---|---|---|---|
| `SWAP` | `swap` | none (`moves` ignored) | take over opponent's side (no stone) |
| `SWAP` | `move` | `<m>` (1 stone) | decline + place the next opening stone (windows 1–3); at the move-4 window, 1 stone = **Branch A** move 5 (9×9) |
| `SWAP` (move 4) / `BRANCH` | `move` | `<s1>,…,<s10>` (10 stones) | **Branch B**: the ten 5th-move offers, atomic (branch inferred from the stone count) |
| `BRANCH` | `move` | `<m>` (1 stone) | **Branch A** move 5 (9×9) after a take-over |
| `SELECTION` | `select` | `<m5>,<m6>` (2 stones) | commit the chosen 5th-move offer (black) **and** place move 6 (white) → opening complete |
| `MOVE` / `COMPLETE` | *(none)* | `<move>` | a **plain** `command=move`, **no `renjuAction`** |

**Branch inferred from the `move` stone count.** There is no separate branch or offer request: at the move-4 window (`SWAP` at move 4) or after a take-over (`BRANCH`), a one-stone `move` takes Branch A (move 5, restricted to the 9×9) and a ten-stone `move` takes Branch B (the ten offers, validated and persisted atomically). Declining the move-4 swap is implicit in sending that `move` — there is no stoneless decline. `swap` is always a take-over (no stone, no `0` sentinel).

The server validates everything authoritatively (central squares, forbidden points, offer symmetry/distinctness). A `renjuAction` that does **not** match the pending phase is rejected with **"Renju action does not match the pending decision."** (`:438`); other rejections carry **distinct, often phase-specific** messages (e.g. "Expected 10 offered moves.", "Selected move was not offered.", "Expected a move when declining to swap.") that the client should **surface verbatim**, distinct from a transport/DB error. Client-side checks are UX only.

### 11.3 iOS TB file-by-file map (the real work)
Renju ids 31/32/81 resolve **WRONG** today across the ObjC TB stack (§11.1). The work is: register the game, parse the new JSON fields, size the board to 15, send `renjuAction`, and build the opening UI from scratch.

1. **`test1/SocialViewController.swift`** *(registration)* — add to `gameNames` (**:28-35**): `"Renju": 31, "Speed Renju": 32, "Turn-based Renju": 81`, and add the corresponding entries to `gameNamesArray` (**:36-43**) so Renju is **pickable** in the game selector (the picker reads the array at **:282/:302/:306/:310**; the lookup `gameNames[gameString]` is at **:136/:228**). Without this, Renju games show no name and cannot be created from this screen.
2. **`test1/BoardViewController.m` — `replayGame` JSON parse** *(read the phase)* — extend the parse block (next to the existing `dPenteState` read at **:1767-1768**) to read `jsonResponse[@"renjuPhase"]` (`NSString`), `jsonResponse[@"renjuOffers"]` (comma-separated `NSString` → `int[]`), and `jsonResponse[@"renjuSwaps"]` (`NSNumber`/nullable). Store them on the `Game`/board model **(verify whether `Game.swift`/`Move.swift` need new fields, or an ObjC ivar suffices — not explored)**. This is a **read**, not a derivation (§11.2).
3. **`test1/BoardViewController.m` — board sizing** *(15×15)* — after the `replayGame` reset `gridSize = 19` (**:1383**) add a Renju branch setting **`gridSize = 15`** for ids 31/32/81 (parallel to the Go `9`/`13` branches at **:1888/:1891**). Then make every hardcoded `/19`,`%19` **`gridSize`-aware** (**:564,:574,:590,:605,:620,:746,:831-833**) — `BoardView.m` already decodes with `/gridSize`,`%gridSize` (**:219-280**), so the controller must match it or the two disagree. The `abstractBoard[19][19]` array (`.h:36`) **physically fits** a 15×15 board (15<19), so no reallocation is strictly required — but the index math and the iteration bounds (any `0..<19` / `< gridSize` loops, e.g. the sync loops) **must** use `gridSize`. (This is the survey's "most dangerous" item, framed precisely: it is the decode math, not the array capacity.)
4. **`test1/BoardViewController.m` — coordinate labels** — `coordinateLetters[19]` (A–T skipping I, **:93-94**) accessed via `% 19` / `19 - (move/19)` (**:831/:833**) must use the **first 15 labels A–P skipping I** and `% gridSize` / `gridSize - 1 - (move/gridSize)`.
5. **`test1/BoardView.m` — star points** — the 5 hardcoded 19×19 circles (`drawRect` **:150-176**) are wrong for 15×15. Use the Renju star points at cols/rows **{3,7,11}** → indices **`[48,52,56,108,112,116,168,172,176]`** (index `= col + row·15`, center 112), matching §4 / §8.3 / §9.3. (`drawRect`'s stone decode at **:219-280** is already `gridSize`-aware — leave it.) Renju forbids black stones on these points anyway, so they are purely visual.
6. **`test1/BoardViewController.m` — `submitMoveToServer`** *(send `renjuAction`)* — extend the move-string construction (**:1219-1253**, alongside the Connect6/D-Pente/Swap2 cases) with the three Renju payload formats (§11.2 table): `swap` (no `moves` payload — take over), `move` (`<m>` for a 1-stone decline+place / Branch A, or `<s1>,…,<s10>` for the 10-stone Branch B), and `select` (`<m5>,<m6>` — chosen 5th + move 6); then extend the URL builders (**:1275/:1281/:1288/:1295**) to append **`&renjuAction=<action>`** to the GET query string when the game is Renju (and send **no** `renjuAction` for plain `MOVE`/`COMPLETE` placements). Surface the server's rejection message **verbatim** (often phase-specific — §11.2), distinct from a transport error.
7. **Shared Swift variant/colour (cross-ref §9.3 steps 2/2a/4)** — `PenteVariant.swift` `case renju = 11` and `BoardVariantMapping.swift` `.renju` → `#D98880` are **shared** with the live stack and specified in §9; do not re-spec them. For the TB board, only **confirm** the ObjC board actually consumes the `@objc` colour bridge (§11.1 last row) and that the `gameName` string the TB endpoint emits for Renju maps to `.renju` in `variant(forGameType:)`.
8. **Opening UI** *(from scratch — §11.5)* — the existing TB opening UI (`dPenteChoiceLabel`/`swap2`, **:1824-1876**) is yes/no/pass buttons only. Renju needs board-level interaction (central-box highlight, 10-pick multi-select, translucent candidates, white selection), driven by the **read** `renjuPhase` and submitted via `renjuAction`. Reuse `boardTap:` (**:644**) for placement and picking; reuse the dPente/swap2 button block (**:1824-1876**) as the dispatch precedent.

### 11.4 Wire / URL examples
**Read (server→client) — `game.jsp` `GameResponse` JSON** for an in-progress TB Renju game awaiting the white selection (Branch B), table-free (TB has no table id). The Renju fields ride alongside the existing ones the parser already reads (`:1465-1522`):
```json
{
  "gameName": "Turn-based Renju",
  "moves": "112,113,114,115",
  "currentPlayer": "bob",
  "rated": true,
  "privateGame": false,
  "renjuPhase": "SELECTION",
  "renjuOffers": "113,114,115,116,128,129,130,131,144,145",
  "renjuSwaps": 13
}
```
- `moves` is itself a **comma-separated `String`**, not a JSON array — iOS splits it with `componentsSeparatedByString:@","` (`BoardViewController.m:1478-1481`). `renjuPhase` is read **as-is** (no derivation). `renjuOffers` is the **same comma-separated `String`** shape — split to `int[]` (here the ten Branch-B candidates). `renjuSwaps` is the packed opening word (Integer; treat as opaque for UI — the phase already tells you what to show). For a non-Renju game all three are `null` (Gson tolerates missing fields).

**Submit (client→server)** — the existing `submitMoveToServer` **GET** with `&renjuAction=` appended to the query string (base `gameServer/tb/game?command=move&mobile=&gid=<gid>&moves=<payload>&message=<msg>`, built `:1271-1299`, dispatched `setHTTPMethod:@"GET"` `:1302`). Concrete (gid `4242`, center 112, 15×15) — **one action per phase**:
```
# SWAP (window 1): decline + place move 2 at col8,row7 (=113, inside the 3×3) — a one-stone move
…/gameServer/tb/game?command=move&mobile=&gid=4242&moves=113&message=&renjuAction=move

# SWAP: take over opponent's side (no stone) — any swap window; no moves payload
…/gameServer/tb/game?command=move&mobile=&gid=4242&message=&renjuAction=swap

# SWAP@4 or BRANCH: Branch A — place move 5 at 130 (9×9) as a one-stone move
…/gameServer/tb/game?command=move&mobile=&gid=4242&moves=130&message=&renjuAction=move

# SWAP@4 or BRANCH: Branch B — the ten 5th-move offers (10 stones = Branch B, atomic; no two D4-symmetric)
…/gameServer/tb/game?command=move&mobile=&gid=4242&moves=113,114,115,116,128,129,130,131,144,145&message=&renjuAction=move

# SELECTION: white commits the chosen 5th-move offer (130) + its own move 6 (131) → opening complete
…/gameServer/tb/game?command=move&mobile=&gid=4242&moves=130,131&message=&renjuAction=select

# MOVE / COMPLETE: a plain move — NO renjuAction
…/gameServer/tb/game?command=move&mobile=&gid=4242&moves=130&message=
```
Notes (from §2.4): **Branch A vs Branch B is inferred from the `move` stone count alone** (1 = Branch A move 5 in the 9×9; 10 = Branch B offers, validated + persisted atomically) — there is no separate branch or offer request. **Declining a swap is implicit in sending a `move`** (windows 1–3 decline is the one-stone `move` above, not a `swap "0,m"`); `swap` is always a take-over and carries no `moves` payload. The 2-stone `select` (`<m5>,<m6>`) is pre-validated before any mutation — neither stone persists unless m5 was offered and m6 is empty, in bounds, and ≠ m5. The ten Branch-B offers are **not** box-constrained (anywhere on the board, minus occupied + D4-symmetric duplicates, §11.5); only the **Branch-A** move 5 is restricted to the 9×9.

### 11.5 Opening UI on the TB board (driven by the read `renjuPhase`)
The TB board has **no opening UI today** other than the dPente/swap2 yes/no/pass buttons (`:1824-1876`); the Renju opening is a **from-scratch build** on the existing TB board (reusing `boardTap:` for placement/picking and the dPente/swap2 button block as the dispatch precedent). Unlike §9.6 (live, phase **derived**), here every control is gated by the **server-provided `renjuPhase`** (§11.2) and submitted via `renjuAction` (§11.4). No state machine to track — just `switch(renjuPhase)`:
- **`SWAP` (swap windows) — swap prompt.** Show two controls (mirror the `player1Button`/`player2Button` block): **"Swap (take over)"** → `renjuAction=swap` with **no `moves` payload**; **"Don't swap (place next stone)"** → place the next opening stone inside its central square and send it as a one-stone `renjuAction=move`, `moves=<move>` (**windows 1–3**). At the **move-4 swap window** the "Don't swap" path *is* the branch (see `BRANCH` below): place **1 stone** (Branch A move 5, 9×9) or **10 stones** (Branch B offers) as a single `renjuAction=move`. The decline-and-place stone is **central-box constrained** (see below).
- **Central-box placement (`MOVE`, and the decline-stone of a `SWAP` window).** Constrain `boardTap:` to the legal N×N square about center 112 for the current opening move: **moves 2/3/4/5 → 3×3 / 5×5 / 7×7 / 9×9** (radius 1/2/3/4). Highlight that square (a new overlay/`CALayer` or extra `drawRect` pass — the TB board has no zone-highlight precedent). This box covers **only** single-stone placements (moves 2–5, incl. Branch-A move 5) — **not** the Branch-B 10-pick. A `MOVE`-phase placement submits as a **plain** `command=move` (no `renjuAction`); the decline-stone of a windows-1–3 `SWAP` rides on a one-stone `renjuAction=move`.
- **`BRANCH` (only after a take-over).** Branch is chosen by the **stone count of a single `renjuAction=move`**: **Branch A** → place move 5 in the 9×9 as a one-stone `move`; **Branch B** → send the **ten** 5th-move offers as a ten-stone `move` (atomic). There is no separate branch request and no standalone `OFFERS` step — the offers ride this one `move`.
- **Branch-B 10-pick multi-select (at the move-4 `SWAP`/`BRANCH` point).** Tap to add a candidate, tap again to remove, with an `n/10` counter; render placed picks as **translucent black** (reuse the `BoardView` stone-fill at lower opacity / the Go dead-stone look). Picks are allowed **anywhere on the board** (in-bounds + empty, minus D4-symmetric duplicates — §11.5 dedup, a UX nicety; the server rejects violations via `offerFifthMove`). On submit send the ten as a single **`renjuAction=move`** with `moves=<s1>,…,<s10>` (**exactly ten** = Branch B, validated atomically).
- **`SELECTION` — white selection screen.** Present the ten `renjuOffers` candidates (parsed from the comma-separated JSON) as translucent black; white taps one to choose move 5 (it goes solid, the rest clear) **and then places its own move 6** anywhere legal → submit both as one atomic `renjuAction=select`, `moves=<m5>,<m6>` (m5 must be one of the offered; m6 empty, in bounds, ≠ m5) — this completes the opening. A non-dismissible prompt (vs the passive text-log line the existing message handler shows) is the right affordance.
- **`COMPLETE` — ordinary placement.** Plain `boardTap:` + a normal `command=move` (no `renjuAction`); black forbidden-points are server-enforced (rejected on submit with a **phase-specific message** the client surfaces verbatim).

Offer symmetry dedup (15×15, center `(7,7)`): for move `m`, `x=m%15`, `y=m/15`, `dx=x-7`, `dy=y-7`; the 8 D4 images are rotations `(dx,dy),(-dy,dx),(-dx,-dy),(dy,-dx)` + reflections `(-dx,dy),(dx,-dy),(dy,dx),(-dy,-dx)`, mapped back `m'=(tx+7)+(ty+7)·15`; reject an offer if any image is already accepted. Mirror the JSP `renjuRotate` (`mobileGame.jsp:998`) / `renjuStabilizer` (`:1008`) / `renjuIsSymmetricDup` (`:1027`) exactly so the client agrees with the server. Visual reference (different framework — do not copy code): `gameServer/tb/mobileGame.jsp` — central-square hinting by move number and the multi-pick picker; the translucent dead/candidate stone is drawn by `drawDeadStone` in **`gameServer/tb/gameScript.js:722`** (mobileGame.jsp only *calls* it, e.g. `:1045`/`:1059`).

### Could NOT confirm (carry into QA / verify before relying on)
- **Model fields for the new JSON.** Whether `Game.swift` / `Move.swift` (or the ObjC `Game`/board model) need explicit `renjuPhase`/`renjuOffers`/`renjuSwaps` fields, or an ObjC ivar suffices — the model structs were not opened. **(verify)**
- **ObjC consumption of the colour bridge.** Whether the ObjC TB board reads its background from the shared `@objc BoardVariantMapping.backgroundColorForVariant:boatPente:` (so §9.3 step 2a's `.renju #D98880` case covers it) or from a separate ObjC colour path needing its own Renju branch. **(verify)**
- **`gameName` string for Renju.** The exact `gameName` value the TB endpoint emits for ids 31/32/81 (`"Turn-based Renju"`? `"Renju"`?) and that it maps to `.renju` in `variant(forGameType:)` and to the right `gridSize` branch. **(verify)**
- **`renjuSwaps` packing.** It is an `Integer` packed opening word; the UI does not need to decode it (the phase suffices), but confirm no UI relies on its bits. **(verify backend packing if ever decoded.)**
- **Coordinate axis correctness after the `/gridSize` switch.** Contract is `x + y·15`; confirm the ObjC decode (post-fix at `:564…:833`) and `BoardView.m`'s `/gridSize` agree end-to-end on 15×15. **(verify)**
- **`canHide`/`canUnHide` (`:1465-66`)** — whether these are TB-only or also apply to replayed live games (affects whether the Renju TB flow must preserve them). **(verify)**
- **Forbidden-point validation is server-only (expected).** No client finder found; do **not** port it. If marking is ever added, fetch `getForbiddenPoints` from the server. **(verify the server-only assumption.)**
- **Rejection-message surfacing.** The server returns **distinct, often phase-specific** messages for a bad `renjuAction` (e.g. "Renju action does not match the pending decision." `:438`, "Expected 10 offered moves." `:474`, "Selected move was not offered." `:505`) rather than a generic "Invalid move". Confirm the iOS error-handling path in `submitMoveToServer` surfaces that message **verbatim**, distinct from a transport error. **(verify the iOS error-handling path.)**
- **Which `submitMoveToServer` URL variant Renju hits** (`&message=` at `:1275/:1281` vs `&message=%@` at `:1288/:1295`) and the cleanest place to append `&renjuAction=` across all four. **(verify.)**
- *Resolved while grounding (no longer open):* stone-value convention is confirmed (`abstractBoard` 1=white / 2=black, `BoardView.m:193-196`; there is **no** `StoneView.m` — `StoneView` lives in `BoardView.m:292-359`); the interactive-TB nature of `BoardViewController` is confirmed (`boardTap:`/`submitMove:` in the header, `submitMoveToServer` builds `command=move` and dispatches it via **GET** at `:1302`, launched interactively by `GamesTableViewController.m` `setActiveGame:YES` `:2166`/`:3658`; `PenteWebViewController.swift` is the **read-only** replay path); and the `GameResponse` field types are confirmed (String/String/Integer, §10.3 step 8).

---

## 12. Sub-project 7 — Android TURN-BASED (correspondence) Taraguchi-10 handoff

Zero-context handoff for a fresh agent wiring the Renju (Taraguchi-10) opening into the **turn-based / correspondence** (days-per-move, over HTTP) path of the `pentelive-android` app — the complement to the **live** handoff (§10, raw-SSL-TCP socket). **Correcting the record:** Android plays **both** transports (§10 established this; the live path is §10.2a). §10.2b already *sketched* the TB read-side (add three `GameResponse` fields + the `renjuAction` param); **this section is the full TB build** — JSON parsing, board sizing, the `renjuAction` submission, and the on-board opening UI. The same record-correction applies to iOS: the original §9 verdict ("LIVE ONLY"; `BoardViewController` a "read-only" viewer) is **WRONG** — `BoardViewController` is iOS's *interactive* turn-based board (`BoardViewController.h` declares `boardTap:` (a `UILongPressGestureRecognizer`) + `submitMove:`; `BoardViewController.m` has `submitMove:` ~:1190, `submitMoveToServer` ~:1216, and builds `game?command=move…&gid=…&moves=…&message=` ~:1275-1295). So **both** mobile apps play **both** live and turn-based; the originals documented only the live opening path. **THE KEY TB DIFFERENCE FROM LIVE:** in turn-based the **server ships the derived phase** — the client **reads** `renjuPhase` from the `game.jsp` `GameResponse` and does **NO** client-side phase derivation (unlike §10.2a/§8.2, which derive it from echo events). Moves go via the `MoveServlet` **§2.4 contract** (`command=move&…&renjuAction=…`).

### 12.0 Board basics (TB-specific)
- Board **15×15**, game ids **31 (Renju) / 32 (Speed Renju) / 81 (turn-based Renju)**. The server ships `gameName="Renju"` for **both** ids 31 and 81 and `"Speed Renju"` for 32 (`GridStateFactory.java:135-137`); the TB-vs-live distinction is by **endpoint/screen, not `gameName`** (there is no `"TB Renju"` string). Move encoding `x + y·15`; **center = 112** (`7 + 7·15`); the **server auto-places** it as move 1 (it already sits in `GameResponse.moves`, so the client only renders it — never places the center itself).
- The TB/correspondence screen is **`BoardActivity` + `Game.java` + `BoardView.java`** (the *offline* board; distinct from the live `LiveGameRoomActivity`/`LiveTableFragment`/`LiveBoardView`). Transport is **HTTP**, not the socket: load = `GET gameServer/mobile/json/game.jsp`, submit = `GET gameServer/tb/game?command=move…`.
- Correspondence Renju games carry id **81**; the **same** `game.jsp` endpoint also serves *completed* live games (31/32) historically — those ship `renjuPhase=COMPLETE` plus the archived `renjuOffers`/`renjuSwaps` (§2.7/§7 archival persistence), so the read-side must tolerate all three ids.
- **Board background colour = `#D98880` (dusty rose)** — the canonical Renju colour, **distinct from gomoku's `#A3FDEB`** (`BoardView.java:38`). `BoardView` has no `renjuColor` constant today (`:37-43`).
- **Black plays first.** Android board values: `1 = white`, `2 = black`, `3 = translucent white`, `4 = translucent black` (both translucent paths `setAlpha(180)`; confirmed in `BoardView.drawStone` **:611-646** — `==2`→black `:623`, `==1`→white `:626`, `==4`→translucent **black** `:629-632`, `==3`→translucent **white** `:633-636`). So "black first" ⇒ the first stone must carry board **value 2**. Today the offline replay (`replayGomokuGame`/`replayPenteGame`) computes `color = 1 + (i % 2)` at `Game.java:1531` (Gomoku) / `:1540` (Pente) → move 0 = value 1 = **WHITE** (inverted for Renju). The fix is a black-first Renju replay `color = 2 - (i % 2)` (a `2 - size%2` form already appears at `Game.java:1290`).
- **Win:** black on **exactly five**, white on **five+** (display only; the server is authority). **Black forbidden points are server-enforced** — never port the finder; if marking is ever wanted, fetch `getForbiddenPoints`.

### 12.1 Confirmed anchors (file : symbol)
All grep-verified in the submodule on this branch (line numbers as-of-now; grep the symbol). **(WRONG today)** = breaks for / ignores Renju as-is; **(OK)** = correct / reusable; **(precedent)** = existing TB mechanism to mirror; **(verify)** = not fully confirmable from code; **(server contract)** = backend-side fact. `renju` appears **zero** times anywhere in the app (confirmed) — no Renju support today.

| Area | File | Symbol / fact |
|---|---|---|
| TB JSON model | `app/.../JsonModels.java` | `GameResponse` **:121-154** — 22 fields: `gid, privateGame, rated, gameName:125, moves:126, player1/2 (PlayerRef), messages, messageNums, sid (Long), currentPlayer:132, seqNums, dates, players, state:136, goState:137, undoRequested (Boolean), canHide, canUnHide, cancel (CancelInfo), dPenteState:142 (String), swap2pass:143 (Boolean)`. **No `renjuPhase`/`renjuOffers`/`renjuSwaps`.** **(WRONG today / missing)** |
| TB game load | `app/.../Game.java` | `RetrieveGame.doInBackground` **:418** — `GET game.jsp?gid=` (URL `:432`, dev `:435`) → `new Gson().fromJson(…, GameResponse.class)` **:464**; result stored in `mGameJson` (`:59`, setter `:204`). Does not read the renju fields (they don't exist). **(WRONG today)** |
| TB move submit (URL) | `Game.java` | `SubmitMoveTask.doInBackground` **:530-538** — builds `https://www.pente.org/gameServer/tb/game?command=move + hideStr + &mobile=&gid=…&moves=…&message=…&name2=…&password2=…`. **No `renjuAction` param** → server rejects opening actions. **(WRONG today / missing)** |
| TB move submit (entry) | `Game.java` | `submitMove(String move, String message)` **:915-918** → `new SubmitMoveTask(move,message).execute()`. The single TB submission entry point; overload it to carry `renjuAction`. **(OK pattern)** |
| TB move submit (alt) | `app/.../net/OkHttpPenteApi.java` | `submitMove(String gid, String moves, String message)` **:163-176** — `addQueryParameter("command","move")`, `"mobile"`, `"gid"`, `"moves"`, `"message"`, `"name2"`, `"password2"`; `.get()` request (`:101`). **No `renjuAction`.** (Two submit paths coexist — confirm which BoardActivity uses; see verify.) **(WRONG today / missing)** |
| board sizing | `Game.java` | `parseGame` **:950**; the `(9x9)→9 / (13x13)→13 / else 19` block (**:1000-1006**, then `boardView.gridSize = gridSize` at **:1007**) sits **INSIDE an outer Go-only `if`** (`:997-1010`, gated on `mGameType.equals("Go")/"Speed Go"/"Go (9x9)"/…`) → **unreachable for Renju**. No `"Renju"` case anywhere → Renju falls through to the default field `private int gridSize = 19` **:2477**. The fix must be a **separate sibling branch OUTSIDE the Go `if`** that sets **both `this.gridSize = 15` AND `boardView.gridSize = 15`** for Renju game types. **(WRONG today)** |
| move-index DECODE | `Game.java` | replay methods hardcode `move / 19`, `move % 19` at **13+ sites** (`:1380, 1532, 1541, 1571, 1593, 1619, 1639, 1661, 1680, 1695, 1704, 1737, 2316`). For Renju `112` decodes to `board[5][17]` (off-center). Encode is fine (see below); **decode is the game-breaking bug.** **(WRONG today)** |
| abstractBoard dims | `Game.java` | `abstractBoard` **:97+** — a literal **19×19** `byte[][]` initializer (19 zeros/row). The draw loop reads it `gridSize`-bounded, but replay populates it via the `/19` decode. **(WRONG today)** |
| replay + colour dispatch | `Game.java` | two `getGameType().equals(...)` chains: **:1320-1362** (`replayGameUntilMove`; `boardView.setBackgroundColor(<variantColor>)` + `replayXGame(untilMove)`) and **:1480-1503** (incremental single-move; `replayXGame(moveI,moveJ,…)`). Exact-string match per variant (`"Gomoku"`,`"Pente"`,`"D-Pente"`,…); also `Variants.fromGameType(getGameType())` + `ALLOWLIST` delegable path (`:1321-1322`). **No `"Renju"` arm in either.** **(WRONG today)** |
| board colours | `app/.../BoardView.java` | **:37-43** (full list) — `penteColor=#FDDEA3, keryoPenteColor=#BAFDA3, gomokuColor=#A3FDEB (:38), dPenteColor=#A3CDFD, gPenteColor=#AEA3FD, poofPenteColor=#EDA3FD, connect6Color=#EDA3FD, boatPenteColor=#25BAFF, dkeryoColor=#FFA500, goColor=#FAC832, oPenteColor=#52be80, swap2PenteColor=#E5AA70, swap2KeryoColor=#50C878` (plus `blackColor`/`whiteColor`). **No `renjuColor`.** **(WRONG today / missing)** |
| board grid size | `BoardView.java` | `public int gridSize = 19` **:52**; set from `Game.parseGame` (`:1007`). **(WRONG today until 15)** |
| move ENCODE (touch) | `BoardView.java` | `onTouchEvent` **:259-306** — `stoneJ = gridSize*stoneX/size` (`:259`), `stoneI = gridSize*stoneY/size` (`:261`), bounds-check `>= gridSize` (`:262`), `playedMove = gridSize*stoneI + stoneJ` (`:306`) = `x + y·gridSize`. **Correct once `gridSize=15`.** **(OK)** |
| board draw loop | `BoardView.java` | `drawBoard` loop **:490-492** — `for i<gridSize, j<gridSize: drawStone(board[i][j])`; `board = game.getState().board` (`:489`). `gridSize`-bounded. **(OK)** |
| star points | `BoardView.java` | `drawBoard` **:482-488** — non-Go `else` branch draws 4 corners at `margin + 6*step` + center (`margin/2` radius). Distance **6** is 19×19-specific; Renju 15×15 needs distance **3** → cols/rows `{3,7,11}`. (A Go branch at `:478-480` uses `3*step` but only 3 circles.) **(WRONG today)** |
| coordinate labels | `BoardView.java` | `onTouchEvent` label build **:364-406** — `coordinateLetters[m % 19]` + `19 - (m / 19)`; `coordinateLetters` (`:71`) = 19 letters `A–T` skipping I. Renju needs `% gridSize` and the **first 15: A–P skipping I**. **(WRONG today)** |
| stone colours + translucent | `BoardView.java` | `drawStone` **:611-646** — `stoneColor==2`→black (`:623`), `==1`→white (`:626`), `==4`→translucent **black** `setAlpha(180)` (`:629-632`), `==3`→translucent **white** `setAlpha(180)` (`:633-636`). **Value 2=black, 1=white, 3=translucent white, 4=translucent black** confirmed; **move 5 is black, so its offer/candidate previews use value 4** (translucent black), solidified to value 2 once chosen. The translucent paths are the **reusable preview primitive**. **(OK / reusable)** |
| **existing TB opening precedent** | `Game.java` | `dPenteChoice` field **:61**, `swap2Choice` **:81**; `parseGame` **:1021-1026** reads `mGameJson.dPenteState == "2"` → sets `dPenteChoice=true` (+ `swap2Choice` if `isSwap2()`); `parseGame` **:1178-1182** makes `R.id.swap2PassButton` `VISIBLE` when `mActive && isSwap2() && movesList.size()==3 && swap2Choice`. **This is the read-server-field → show-choice precedent to mirror for `renjuPhase`.** **(precedent — confirmed now)** |
| **existing TB opening UI wiring** | `app/.../BoardActivity.java` | `onCreate` **:65-127** wires `R.id.playAsWhite`/`playAsBlackButton` + `R.id.dPenteLayout` — `game.submitMove("0", …)` fires at **:96** (playAsWhite / swap2 branch) and **:116** (playAsBlack / non-swap2 branch); `:99/:110/:127` are `setVisibility(INVISIBLE)` layout-hide calls (**not** submits) — and `R.id.swap2PassButton` (`:121-127`); `setRegularSubmitListener` **:239** branches at **:282** (`isSwap2() && swap2Choice`) and **:300** (`isDPente() && dPenteChoice`) before `game.submitMove(moves, …)` **:326**. **Corrects §10.2b's "no opening UI" claim** — a yes/no opening-answer skeleton exists on TB; Renju reuses its shape (read phase → show control → `submitMove`). **(precedent — confirmed now)** |
| load entry | `BoardActivity.java` | `onCreate` **:79** calls `game.parseGame(board)` immediately → triggers `RetrieveGame`. **(OK)** |
| game list → Game | `app/.../PentePlayer.java` | `populateFromJson` **:174** — only `IndexResponse.activeGamesMyTurn` (`:257-269`) builds `mActiveGames` (`this.mActiveGames = newActive` **:269**); `activeGamesOpponentTurn` (`:273-285`) feeds a **separate** non-active list `mNonActiveGames` (`:285`), **not** `mActiveGames`. Both call `new Game(String.valueOf(entry.gid), null, entry.gameName, …)`. **No game-type filter** → Renju games already list. The `entry.gameName` string flows into `Game.mGameType`. **(OK)** |
| player colour hint | `Game.java` | `mMyColor` field **:53** (ctor `:124`, getter `:163`); used `:1281` `mMyColor.contains("white") ? 1 : 2` and `:1290` `2 - mMovesList.size()%2`. Server tells the client which side it plays. **(OK / context)** |
| renjuAction wire | backend `MoveServlet.java` / `RenjuTbContract.resolve` (reference, §2.4) | TB opening actions = a `renjuAction` query param alongside `command=move`: **`swap`** (take over, no stone) / **`move`** (1 stone = decline+place / Branch A move 5; 10 stones = Branch B offers, atomic) / **`select`** (2 stones = chosen 5th + move 6) — **three total**, with the §2.4 `moves` payloads; the phase guard gates each action to its pending decision. Branch A vs B is inferred from the `move` stone count. Server validates and throws `InvalidMoveException` → page shows **"Invalid move"**. **(server contract)** |
| GameResponse renju fields | backend `GameResponse.java:45-47` (reference) | `renjuPhase` (`String`, one of `SWAP|BRANCH|OFFERS|SELECTION|MOVE|COMPLETE`, else null), `renjuOffers` (`String`, comma-separated offered move indices, else null), `renjuSwaps` (`Integer`, packed opening word, else null). The Android POJO types must be `String/String/Integer`. **(server contract)** |

### 12.2 TB phase + transport (the server SHIPS `renjuPhase` — read it, do NOT derive)
Unlike the live path (§10.2a, which **derives** the phase from `dsgRenjuTaraguchi*` echoes because the socket carries **no** `renjuPhase`), the turn-based path is **read-only on phase**: `TBGame.getRenjuPhase()` reconstructs server-side (§2.6) and ships the result in `GameResponse.renjuPhase`. The Android TB client adds the three fields to `GameResponse` and **reads `renjuPhase` directly** — no `renjuState` tracking object, no `renjuPhase(...)` classifier, none of the §10.2a echo accounting. This is strictly simpler than live; the only "logic" is mapping the read phase to a control and the chosen action to a `renjuAction` payload.

`renjuPhase` → control → §2.4 submission (the client shows the control the phase names, then submits the matching `renjuAction` via `submitMove`):

| `renjuPhase` (read) | meaning | client control | `renjuAction` + `moves` (§2.4) |
|---|---|---|---|
| `SWAP` | swap window open (after moves 1–4) | "Swap (take over)" / "Don't swap" — windows 1–3 decline+place is a one-stone `move`; at move 4 the `move` *is* the branch (1 = A, 10 = B) | take-over → `swap` (no `moves`) · windows 1–3 decline+place → `move` / `<m>` · move-4 Branch A → `move` / `<m5>` (1 stone) · move-4 Branch B → `move` / `<s1>,…,<s10>` (10 stones) |
| `MOVE` | place the next central-square opening stone (incl. Branch-A move 5), no decision pending (radius by move #) | constrained placement | **plain move** — `command=move&moves=<move>`, **no `renjuAction`** |
| `BRANCH` | move-4 swap was **taken**; brancher chooses A vs B by stone count | Branch A (place move 5 ∈ 9×9) **or** Branch B (the ten offers) | Branch A → `move` / `<m5>` (1 stone) · Branch B → `move` / `<s1>,…,<s10>` (10 stones, atomic) |
| `SELECTION` | white commits a chosen 5th-move offer **and** its own move 6 | tap one of `renjuOffers`, then place move 6 | `select` / `<m5>,<m6>` (2 stones) → opening complete |
| `COMPLETE` | opening done | normal play | **plain move** (no `renjuAction`); black forbidden points server-enforced |

The client submits exactly one of the three actions per phase (`swap` / `move` / `select` + plain `MOVE`/`COMPLETE`). **Branch A vs Branch B is inferred from the `move` stone count alone** (1 = Branch A move 5 in the 9×9; 10 = Branch B offers, validated and persisted atomically) — there is no separate branch or offer request, and declining the move-4 swap is implicit in sending the `move`. Under the single-request contract Branch B and its ten offers travel as that one branch-point `move`, so the client never has to act on a standalone `OFFERS` phase. `renjuOffers` (comma-separated indices) is the `SELECTION` render source. `renjuSwaps` (the §2.1 base-3 `RenjuOpeningState` word) the client does **not** decode — the server already shipped the resolved `renjuPhase`; `renjuSwaps` is only needed for archival/opening-replay rendering (deferred).

### 12.3 File-by-file map (the real work)
**(TB)** = turn-based/offline screen; **(both)** = shared `rules/` registry (already covered for live by §10.3 steps 1–2 — reuse, don't duplicate).

1. **`rules/.../pente/rules/Variant.java` + `Variants.java`** *(both)* — already specified in §10.3 steps 1–2: add `RENJU(31, 15, CaptureRule.NONE, 1)` + `isRenju()`; `fromGameId` `31/32/81→RENJU`; `fromGameType` `"Renju"`/`"Speed Renju"` → `RENJU` (**no "TB Renju" arm** — the server ships `gameName="Renju"` for **both** ids 31 and 81, `GridStateFactory.java:135-137`). The TB path leans on **`fromGameType(gameName)`** (the string in `GameResponse.gameName`), exercised at `Game.java:1321`.
2. **`app/.../JsonModels.java`** *(TB)* — add to `GameResponse` (`:121-154`): `public String renjuPhase; public String renjuOffers; public Integer renjuSwaps;` (matches backend `GameResponse.java:45-47`). Gson tolerates absent fields → backward-safe.
3. **`app/.../Game.java`** *(TB)* — the bulk of the work:
   - `parseGame`: add a **sibling Renju branch OUTSIDE the Go-only `if` (`:997-1010`)** — when `mGameType` is a Renju type, set **both `this.gridSize = 15` AND `boardView.gridSize = 15`**. (The existing `(9x9)/(13x13)/else 19` block at `:1000-1007` is unreachable for Renju, being gated behind the `Go`/`Speed Go` check — do **not** add the Renju case inside it.)
   - **Store the read phase:** after the Gson parse, capture `mGameJson.renjuPhase`/`renjuOffers`/`renjuSwaps` (parse `renjuOffers` to `int[]`) into `Game` instance fields, mirroring how `dPenteState` is consumed at `:1021-1026`. Add `isRenju()` mirroring `isDPente():945` (`Variants.fromGameType(getGameType())…isRenju()`).
   - **Replay + colour dispatch** `:1320-1362` **and** `:1480-1503`: add a `"Renju"`/`"Speed Renju"` arm in **both** → `boardView.setBackgroundColor(boardView.renjuColor)` + a new **black-first** `replayRenjuGame(…)`.
   - **`replayRenjuGame`** — a black-first clone of `replayGomokuGame`: `color = 2 - (i % 2)` (i=0 → value 2 = black) and `move / gridSize`, `move % gridSize` (**not** `/19`,`%19`).
   - **Fix the decode bug:** replace hardcoded `/19`,`%19` with `/gridSize`,`%gridSize` at the 13+ sites (`:1380,1532,1541,1571,1593,1619,1639,1661,1680,1695,1704,1737,2316`); allocate `abstractBoard` (`:97+`) from `gridSize` instead of the literal 19×19.
   - **`submitMove` + `SubmitMoveTask`** (`:915`, `:530-538`): overload to carry `renjuAction` and append `&renjuAction=<swap|move|select>` to the `tb/game` URL (omit it entirely for plain `MOVE`/`COMPLETE` moves).
4. **`app/.../net/OkHttpPenteApi.java`** *(TB)* — `submitMove:163-176`: overload `submitMove(gid, moves, message, renjuAction)` → `.addQueryParameter("renjuAction", action)`. **Resolve first** which submit path `BoardActivity` actually uses (`Game.SubmitMoveTask` vs `OkHttpPenteApi`) and wire `renjuAction` into that one (verify).
5. **`app/.../BoardView.java`** *(TB)* —
   - `:37-43`: add `renjuColor = Color.parseColor("#D98880")`.
   - `:482-488`: add a Renju star-point branch — distance-**3** corners + center (`{3,7,11}`, center `(7,7)`), not the hardcoded `6*step`.
   - `:364-406` + `coordinateLetters:71`: use `% gridSize` and the **first 15 letters A–P (skipping I)** instead of `% 19` / 19 letters.
   - Reuse `drawStone`'s translucent-black `stoneColor==4` `setAlpha(180)` path (`:629-632`) for the move-5 offer/selection candidates (move 5 is black); `stoneColor==3` (`:633-636`) is the translucent-**white** variant.
6. **`app/.../BoardActivity.java`** *(TB)* — the opening UI, mirroring the **existing dPente/swap2 skeleton** (`:65-127`, `setRegularSubmitListener:239-326`): read `Game.renjuPhase`, gate the matching control (§12.5), and submit via the new `renjuAction`-carrying `submitMove`. The yes/no swap windows reuse the `dPenteLayout`/`swap2PassButton` pattern almost verbatim; the branch/offer/selection board-interaction is new (§12.5).

### 12.4 Wire / URL examples (concrete `game.jsp` JSON + `renjuAction` requests)
**Read — `GET gameServer/mobile/json/game.jsp?gid=<id>&name2=<user>&password2=<pass>`** → Gson `GameResponse`. A Branch-B game awaiting white's selection (15×15, center 112) ships:
```json
{
  "gid": "12345", "gameName": "Renju", "moves": "112,113,114,115",
  "currentPlayer": "bob", "state": "...", "player1": {"name":"alice","rating":1500},
  "renjuPhase": "SELECTION",
  "renjuOffers": "113,114,115,116,128,129,130,131,144,145",
  "renjuSwaps": 5
}
```
A normal mid-opening read (white's swap window after black's move 2): `"renjuPhase": "SWAP"`, `"renjuOffers": null`, `"renjuSwaps": <packed>`. A finished/historic game: `"renjuPhase": "COMPLETE"` (with archived offers/swaps for replay).

**Submit — `GET gameServer/tb/game?command=move&mobile=&gid=<id>&moves=<payload>&renjuAction=<action>&message=…&name2=…&password2=…`** (the §12.3 step 3/4 builders add `&renjuAction=`):

| intent | `moves` | `renjuAction` | resulting query tail |
|---|---|---|---|
| take over opponent's side (no stone) | _(none)_ | `swap` | `…&renjuAction=swap` |
| decline window 1–3 + place next opening stone (e.g. move 2 @113) | `113` | `move` | `…&moves=113&renjuAction=move` |
| move-4 Branch A: place move 5 @130 (9×9) | `130` | `move` | `…&moves=130&renjuAction=move` |
| move-4 Branch B: the ten 5th-move offers (10 stones, atomic) | `113,114,115,116,128,129,130,131,144,145` | `move` | `…&moves=113,…,145&renjuAction=move` |
| white commits chosen 5th @130 + own move 6 @131 | `130,131` | `select` | `…&moves=130,131&renjuAction=select` |
| Branch-A move 5 @130 (server-shipped `MOVE` phase) / any `COMPLETE` move | `130` | _(none)_ | `…&command=move&moves=130` |

**Branch A vs B is inferred from the `move` stone count** (1 vs 10); declining a swap is implicit in sending a `move`, and `swap` is always a take-over with no `moves` payload. The 2-stone `select` and the 10-stone Branch-B `move` are both pre-validated before any mutation (no half-applied opening). The server validates authoritatively (central squares, forbidden points, offer distinctness/D4-symmetry) and throws `InvalidMoveException` → the page shows **"Invalid move"**; client-side checks are UX only. The ten Branch-B offers are **NOT** box-constrained (any in-bounds, empty, non-D4-symmetric point — corners legal); only the **Branch-A move 5** is restricted to the 9×9 (§10.5 has the client-side D4 dedup algorithm if you want instant feedback).

### 12.5 Opening UI on the TB board (driven by the read `renjuPhase`)
**There IS a precedent** (correcting §10.2b): the TB screen already reads an opening-state field (`dPenteState`, `Game.java:1021`) and shows yes/no opening controls (`R.id.dPenteLayout` two-button choice + `R.id.swap2PassButton`, `BoardActivity:96-127`), submitting via `game.submitMove(...)`. Renju reuses that exact shape — read `renjuPhase` → show control → `submitMove` with `renjuAction`. The **swap windows are essentially the existing pattern**; only the branch/offer/selection board-interaction is genuinely new (no multi-select / zone-highlight precedent on this screen).

- **Central-box placement highlight** (new `Canvas` layer in `BoardView.drawBoard`) — highlight only the legal cells of the N×N square about center 112 for the current opening move: **moves 2/3/4/5 → 3×3 / 5×5 / 7×7 / 9×9** (radius 1/2/3/4). Applies during `MOVE` placement **and** the decline-and-place action of a `SWAP` window (the bundled stone is constrained to the same square). **Only single-stone placements (moves 2–5, incl. Branch-A move 5) are box-constrained** — do **not** box the Branch-B offer picker.
- **Swap prompt** (`SWAP` phase) — mirror `dPenteLayout`/`swap2PassButton` (`AlertDialog`/bottom-gravity buttons): "Swap (take over)" → submit `renjuAction=swap` with **no `moves` payload**; "Don't swap" → in windows 1–3, tap the highlighted square to place the next stone → a one-stone `renjuAction=move`. At move 4 the "Don't swap" path *is* the branch: place 1 stone (Branch A move 5, 9×9) or 10 stones (Branch B offers) as a single `renjuAction=move` (see Branch choice below).
- **Branch choice** (`SWAP` at move 4, or `BRANCH` after a take-over) — branch is chosen by the **stone count of one `renjuAction=move`**: "Continue (place 5th move)" → place move 5 in the 9×9 as a one-stone `move` (Branch A); "Offer 10" → open the 10-pick and send the ten as a ten-stone `move` (Branch B, atomic). There is no separate branch request and no standalone `OFFERS` phase to wait on.
- **Branch-B 10-pick multi-select** (the move-4 branch point) — tap to add a candidate (move 5 is **black**, so render it translucent via `drawStone` **value 4** / `setAlpha(180)`), tap again to remove, `n/10` counter, submit button (exactly 10). Whole board allowed (minus occupied + D4-duplicate). Submit the ten as a single `renjuAction=move`, `moves=<s1>,…,<s10>` (10 stones = Branch B, validated atomically).
- **White selection** (`SELECTION` phase) — render the `renjuOffers` indices as translucent-black candidates (**value 4**); white taps one to choose move 5 → solidify it to value **2** (black), the rest clear, **then places its own move 6** anywhere legal → submit both as one atomic `renjuAction=select`, `moves=<m5>,<m6>` → opening complete.

### Could NOT confirm (carry into QA / verify before relying on)
- **Exact `gameName` string for Renju ids 31/32/81** — **RESOLVED:** the server ships `gameName="Renju"` for **both** 31 and 81 and `"Speed Renju"` for 32 (`GridStateFactory.java:135-137`). `parseGame` board-sizing and the replay/colour dispatch (`:1320-1362`, `:1480-1503`) match those exact `mGameType` strings; there is **no "TB Renju"** string. **(confirmed)**
- **Which submit path is canonical** — both `Game.SubmitMoveTask` (`:530-538`) and `OkHttpPenteApi.submitMove` (`:163-176`) build a `tb/game?command=move` request; `BoardActivity:326` calls `game.submitMove(...)` (→ `SubmitMoveTask`). Confirm which actually fires in production and wire `renjuAction` there (and whether the other is dead). **(verify)**
- **`MOVE`-phase submission** — **RESOLVED:** a `renjuPhase=MOVE` (or `COMPLETE`) opening stone is a **plain `command=move&moves=<move>` with NO `renjuAction`** — the `matchesPending` guard (`MoveServlet.java:430-435`) only accepts a `renjuAction` while a swap/branch/offer/selection is pending, so an unwrapped `MOVE` is the correct form. Branch-A move 5 is likewise a plain `MOVE`. **(confirmed)**
- **`renjuSwaps` packed format** — it is the §2.1 base-3 `RenjuOpeningState` word (`Integer`); the TB client need not decode it (the server ships `renjuPhase`). Confirm it is only needed for archival opening-replay rendering (deferred). **(verify / informational)**
- **Black-first colour fix** — confirm the offline replay is white-first today (`Game.java:1531` (Gomoku) / `:1540` (Pente) `1 + (i%2)`; `:1532` is the `move/19` **decode** line, not the colour) and that the new `replayRenjuGame` (`2 - (i%2)`) renders move 1 (center 112) as **black** after the fix. **(verify visually)**
- **Coordinate-label set for 15×15** — first 15 of the existing `coordinateLetters` (`A–P` skipping I) vs a Renju-specific array; current code is 19 letters `% 19` (`:364-406`). **(verify)**
- **Star-point layout** — `{3,7,11}` distance-3 corners + center for 15×15 (vs the hardcoded `6*step` at `:483-487`). **(verify)**
- **TB opening-UI scope on the offline screen** — the dPente/swap2 precedent (`:65-127`) is yes/no buttons only; the Renju branch/offer/selection board-interaction is from-scratch. Confirm whether the full TB opening UI is in scope now or staged after the read-side + `renjuAction` (this handoff recommends: ship parsing + `renjuAction` + the swap windows first, then the multi-select/selection). **(verify scope)**
- **iOS TB complement** — the corrected §9 verdict (`BoardViewController` is iOS's interactive TB board: `boardTap:`/`submitMove:` in `.h`; `submitMove:`~:1190, `submitMoveToServer`~:1216, `game?command=move…&moves=…`~:1275-1295) means iOS needs the same TB build (read `renjuPhase`, send `renjuAction`); not grep-re-verified here. **(verify against the iOS submodule)**
