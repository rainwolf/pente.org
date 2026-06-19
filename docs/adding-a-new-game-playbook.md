# Adding a New Game to pente.org

A game-agnostic engineering playbook for adding a brand-new board game (with or
without a negotiated opening phase) to pente.org. It is distilled from the renju
(Taraguchi-10) work as a worked example. Throughout, placeholders are
`<GameName>`, `<GAME_ID>`, `<YourState>`, etc.; renju appears only as a
parenthetical example.

## Architecture Orientation

A game in pente.org is an integer id that flows through five concentric layers:
the **engine/factory** (`GridStateFactory` mints the id and a `GridState`
subclass encodes the rules), **persistence** (SQL columns + four storers
serialize moves and any opening state), the **live server** (websocket/socket
table events + `ServerTable` handlers drive real-time play), the **turn-based
web/contract** (servlets that orchestrate HTTP moves through a pure resolver),
and the **web UI / client constants** (the JS-visible `GAME.*` registry, the
canvas board renderer, JSP play/viewer pages, and the React live game room).
Each layer keys off the id minted in the first, so the **strict dependency
order** is: (1) mint ids + write the `GridState` rules in `GridStateFactory`;
(2) add persistence only if you carry extra/opening state; (3) wire live-server
dispatch + turn-based servlets; (4) expose the id to clients via
`gameConstants.jspf` and the React `boardGeometry`; (5) register board color +
replay in the JS/React switches and (optionally) build the opening-decision UI.
A plain 19x19 game that reuses an existing engine touches only the factory, the
client id registries, and the per-page render branches — no SQL, no new events,
no servlet branches.

The id-numbering contract underpins everything: each game consumes **three
consecutive ids** — `N` (normal, an odd int), `N+1` (speed, even), and
`TB_START(50) + N` (turn-based). Pick the next free odd `N`; the odd/even/+50
adjacency is assumed by helper math across the codebase, so you cannot insert in
the middle without renumbering. (Example: renju took `RENJU=31`,
`SPEED_RENJU=32`, `TB_RENJU=81`.)

## Layer 1 — Engine Core + Factory Registration

All edits here are in
`dsg_src/java/org/pente/game/GridStateFactory.java` unless noted. This is the
single registry where an id becomes behavior.

1. **Allocate the id triple [PLUMBING].** Add the next free odd pair plus the TB
   id as `public static final int` constants:
   ```java
   public static final int <GAME>       = N;            // odd, next free
   public static final int SPEED_<GAME> = <GAME> + 1;   // even
   public static final int TB_<GAME>    = TB_START + <GAME>;  // TB_START = 50
   ```
   WHY: the odd/even/+50 adjacency is load-bearing —
   `getSpeedGame`/`getNormalGame`/`isSpeedGame`/`getNormalGameFromTurnbased`
   derive variants by arithmetic. Do this first; everything keys off it.

2. **Register ids in the game arrays [PLUMBING].** Append `<GAME>, SPEED_<GAME>`
   to `LIVE_GAMES[]` and `TB_<GAME>` to `TB_GAMES[]`. `ALL_GAMES` is
   auto-derived. WHY: these arrays drive boot-time iteration and partitioning.

3. **Create `Game` metadata objects [PLUMBING].** `Game` is the immutable triple
   `Game(int id, String name, boolean speed)`. Declare three constants:
   ```java
   public static final Game <GAME>_GAME       = new Game(<GAME>, "<GameName>", false);
   public static final Game SPEED_<GAME>_GAME = new Game(SPEED_<GAME>, "Speed <GameName>", true);
   public static final Game TB_<GAME>_GAME    = new Game(TB_<GAME>, "<GameName>", false);
   ```
   Only the display strings are game-specific.

4. **Insert the `Game` objects into the five lookup tables — ordering matters
   [PLUMBING].** `allGames[]` is **index-mapped (array index == game id)** with a
   leading `null` and live-then-TB ordering, so the new live entries must be
   inserted in strict id order (example: renju inserted after id 30 and before
   the `TB_*` block; `TB_<GAME>_GAME` appended to the TB tail). `displaygames[]`
   (three sections: normal, "Turn-based X", "Speed X"), `normalGames[]`,
   `speedGames[]`, and `tbGames[]` are searched by name/id, so order is not
   correctness-critical but the entry must appear in each. WHY: `getGame`/
   `getColor`/the boot loop index `allGames`/`gridStates` by id.

5. **Bump the boundary helpers [PLUMBING].** If your game is the new maximum id:
   `isValidGame(game)` upper bound → new highest **live** id (`SPEED_<GAME>`);
   `getMaxGameId()` → new highest **TB** id (`TB_<GAME>`). WHY: these encode the
   current last id and silently reject the new game otherwise.

6. **Add the `createGridState` switch case [PLUMBING case / GAME-SPECIFIC
   body].** This is the **one point where id becomes behavior**:
   ```java
   case <GAME>:
   case SPEED_<GAME>:
   case TB_<GAME>:
       return new <YourState>(W, H);   // fixed-board idiom: ignore requested x,y
   ```
   Honor the requested `x,y` only if your board is resizable (example: renju
   hard-pins 15x15 like GO9/GO13; Pente honors `x,y`). The default returns null.
   WHY: combined with step 4, the `static {}` block auto-registers your state by
   calling `createGridState(i)` for every id — **no separate registry to touch**.

7. **Write the `<YourState>` class [PLUMBING interface / GAME-SPECIFIC rules].**
   Implement `GridState` (`dsg_src/java/org/pente/game/GridState.java`, extends
   `MoveData`). In practice extend the abstract `GridStateDecorator`, which wraps
   a `protected GridState gridState` and forwards every method; override only the
   methods your rules change. Required contract:
   `isValidMove(move, player)`, `canPlayerUndo(player)`, `isGameOver()`,
   `getWinner()` (0 if not over/draw), `clear()`, `getGridSizeX/Y()`, the
   `MoveData` move-list methods, `getCurrentPlayer()`, `convertMove`, `getColor`.
   The move integer is `convertMove(x,y) = x + y*sizeX`. (Example: `RenjuState`
   wraps a `SimpleGomokuState`, mixes in `GomokuState`/`HashCalculator`, and
   delegates win/forbidden logic to a `RenjuForbiddenPointFinder` — its rules
   helper.) Pick whatever base/decorator suits your game (Go → `GoState`,
   Connect6 → `SimpleConnect6State`, etc.).

8. **Override `getInstance(MoveData)` to return your own type [PLUMBING contract
   / GAME-SPECIFIC replay].** Stored games are rebuilt via
   `createGridState(game, MoveData)` → `prototype.getInstance(moveData)`. The
   decorator default delegates to the wrapped base and silently drops your rules,
   so override it:
   ```java
   public GridState getInstance(MoveData moveData) {
       <YourState> s = new <YourState>(gridState.getGridSizeX(), gridState.getGridSizeY());
       for (int i = 0; i < moveData.getNumMoves(); i++) s.addMove(moveData.getMove(i));
       return s;
   }
   ```
   WHY: reconstruction-from-moves must preserve your type. (Caveat: negotiated
   opening *decisions* are not in the move list — full recovery uses
   `reconstruct(...)`, see the Optional section.)

9. **Reuse `getCenterMove(int game)` for centered first stones [PLUMBING].** It
   derives the center from the actual board
   (`gs.convertMove(gridX/2, gridY/2)`), never hardcoding 19x19. WHY: any game
   that auto-places a center stone gets the right value for free (example: renju
   15x15 → 112, not 180).

Key files: `GridStateFactory.java`, `GridState.java`,
`GridStateDecorator.java`, your `<YourState>.java` (+ a rules helper such as the
forbidden-point finder).

## Layer 2 — Persistence (SQL Schema + Storers)

Skip this entire layer if your game carries no extra/opening state beyond moves
(a plain game reuses the existing move storage). If you do carry state, the
reusable recipe is **"persist N extra ints + an optional blob/child-table of
opening state, in lock-step across four storers + the schema."**

1. **Author the opening-state value object first [GAME-SPECIFIC content /
   PLUMBING pattern].** A pure codec class with `int encode()` / static
   `decode(int)` (pack the decision-vector into one small int) and static
   `encode<Blob>(int[])` / `decode<Blob>(byte[])` for variable-length data. WHY:
   every storer calls the same static codec. (Example: `RenjuOpeningState` packs
   six base-3 digits into one int 0..728 and one unsigned byte per offered move.)

2. **Write a hand-authored idempotent SQL migration, then regenerate
   `schema.sql` [PLUMBING].** Author
   `dsg_src/sql/<date>-<game>-opening-state.sql` using
   `ADD COLUMN IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS`. `schema.sql` is a
   **generated** `mysqldump` — never hand-edit it as source; apply the migration
   to a DB then regenerate the dump. **Add opening columns to *every* table a
   game of that type can load from** (the easiest thing to get wrong):

   | Table | Add | Why |
   |---|---|---|
   | `tb_game` | inline int col (+ optional blob) | live turn-based games |
   | `tb_game_ai` | same | AI TB games load via the same `TB_COLUMNS` SELECT — omit and every AI load fails "unknown column" |
   | `pente_game` | inline int col only | archival (finished) games |
   | `<game>_offer` (new) | `(gid, site_id, offer_num, move)` PK `(gid,site_id,offer_num)` | archival variable-length data as normalized child rows |

   Note the **two storage shapes for the same data**: TB uses an inline blob,
   archival uses a normalized child table. Pick one per table family; the
   decision-vector int is stored inline in both.

3. **Extend `TBGame` (in-memory model) [PLUMBING].**
   `dsg_src/java/org/pente/turnBased/TBGame.java`. Add nullable fields + getters/
   setters for your opening state. Three sub-points:
   - **`getCurrentPlayer()` branch** if your opening has an asymmetric turn (same
     move count, different actor). Default is `moves.size() % 2 + 1`; add a
     `game == TB_<GAME>` branch overriding parity (example: renju forces seat 2
     at move 4 so the offerer and selector differ).
   - **`getGameData()` handoff** (the toGameData block): when `game == TB_<GAME>`,
     copy your opening fields into the archival `PenteGameData`. WHY: this is what
     persists the opening when the game is archived.
   - Optional **phase-derivation accessor** that reconstructs the engine on
     demand (`<YourState>.reconstruct(this, packed, offers)`) and maps
     `isAwaiting*` predicates to a view-facing phase string; return null for
     non-matching games (zero cost). Don't serialize derived phase.

4. **Add negotiation verbs to the `TBGameStorer` interface [PLUMBING].**
   `dsg_src/java/org/pente/turnBased/TBGameStorer.java`. Add methods like
   `<game>Swap(TBGame, boolean)`, `<game>Branch(TBGame, boolean)`,
   `<game>Offers(TBGame)` — all `throws TBStoreException`. WHY: these are
   opening-negotiation persistence ops distinct from `storeNewMove` (they mutate
   decision state without placing a stone). Because it is an interface, adding
   them **forces all three implementors to update or compilation fails** — that
   is the parity-enforcement mechanism.

5. **Implement in lock-step across the three storers [PLUMBING shape /
   GAME-SPECIFIC SQL]:**
   - `MySQLTBGameStorer.java` — **read side**: extend both `TB_COLUMNS`
     constants (unqualified + `g.`-prefixed), appending new columns **at the
     end**; in the positional row-mapper append `setXxx(result.getXxx(r++))`
     **after** all existing reads (reads are positional — order must match the
     SELECT). **Write side**: each verb is a narrow `UPDATE ... where gid=?` that
     also bumps `last_move_date`/`timeout_date` (a swap also swaps `p1_pid`/
     `p2_pid`).
   - `CacheTBStorer.java` — the write-through front storer. Mirror the
     `getGameData` copy; each verb follows the template
     `loadGame(gid)` → mutate under `synchronized(cacheTbLock)` → recompute
     timeout via `Utilities.calculateNewTimeout` → `persistSet(...)` → delegate
     to `baseStorer.<verb>(...)`. The `storeNewMove` validity check runs
     *before* applying the move and throws `InvalidMoveException` on rule
     rejection. Set the rating **k-factor** for your ids here if non-default.
   - `dsg_src/java/org/pente/turnBased/test/InMemoryTBGameStorer.java` — add
     **no-op stubs** for the three new methods (pure interface conformance, so
     tests compile).

6. **Extend `MySQLPenteGameStorer` (archival store + load) [PLUMBING].**
   `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java`. **Store**: add the
   inline int column + one `?` to the INSERT, bound via `setInt`/`setNull(...,
   Types.SMALLINT)`; after the main insert, loop-insert child rows into
   `<game>_offer (gid, site_id, offer_num, move)`. **Load**: append the int
   column to the SELECT (positional) and read with a `wasNull()` guard to
   preserve "not this game" as null; a **separate** `SELECT move FROM
   <game>_offer ... ORDER BY offer_num` rebuilds the `int[]`.

7. **Reuse `InvalidMoveException` [PLUMBING].**
   `dsg_src/java/org/pente/turnBased/InvalidMoveException.java` extends
   `TBStoreException`; rule rejections (illegal placement, forbidden point) funnel
   here so the UI shows "invalid move" instead of a DB error. Reuse unchanged;
   only the conditions that raise it are game-specific.

8. **Generalize hardcoded center moves [PLUMBING — easy to miss].** Replace every
   hardcoded `180` (the 19x19 Pente center) with
   `GridStateFactory.getCenterMove(game)` (three spots in `CacheTBStorer`, two in
   the archival storer). WHY: any non-19x19 board breaks silently otherwise.

9. **Carrier fields on `GameData`/`DefaultGameData` [PLUMBING pattern].**
   `GameData.java` is an interface — add **Java 8 `default`** accessors returning
   null so every other implementor keeps compiling; `DefaultGameData.java` adds
   the backing nullable fields + `@Override` accessors. These are the in-memory
   half of the SQL columns. (Example: `getRenjuSwaps`/`getRenjuOffers`.)

## Layer 3 — Live Server (Table Events, Dispatch, Routing)

Only needed if your game has live (real-time) interactions beyond plain moves —
typically a negotiated opening. The wire/dispatch machinery is 100% generic;
only payload shape and handler bodies are game logic.

**How the protocol works (read first):** there is no event-id, no `read`/`write`,
no `Externalizable`. Serialization is Gson JSON reflection through one god-class,
`DSGEventWrapper`, which declares **one private field per event type**. The
**field name is the wire discriminator** (the literal JSON key clients switch
on). Encode: `new DSGEventWrapper(event).getJSON()`. Decode:
`gson.fromJson(msg, DSGEventWrapper.class).getEncodedEvent()`.

1. **Define event class(es) [PLUMBING shape / GAME-SPECIFIC payload].** In
   `dsg_src/java/org/pente/gameServer/event/`. Each `extends
   AbstractDSGTableEvent` (gives `get/setPlayer`/`get/setTable` for free), has a
   **public no-arg constructor** (Gson) plus a populated `(String player, int
   table, <payload...>)` constructor, and exposes payload as Gson-friendly bean
   fields. No event-id, no manual serialization. (Example: renju added swap /
   offer-10 / select-1 events.) A game replaces these with whatever its
   negotiation needs.

2. **Register each event in `DSGEventWrapper` [PLUMBING].** Add a private field +
   getter/setter per event. WHY: this single edit makes the event serialize **and**
   deserialize in **both** directions over **all** transports. The field name is a
   contract clients hard-code — pick it deliberately. Without it, the event
   serializes to an empty wrapper and is undeliverable.

3. **Add a dispatch arm in `SynchronizedServerTable.callServerTable(...)`
   [PLUMBING arm / GAME-SPECIFIC handler name].** This Java 21 pattern-matching
   `switch` over the event's runtime type is the **only** inbound routing point;
   add `case DSG<Event> e -> serverTable.handle<X>(e);` before `default -> {}`.
   Only client-originated events need an arm (server→client echoes do not).
   Forgetting an arm = event silently dropped.

4. **Write the handlers in `ServerTable` [PLUMBING skeleton / GAME-SPECIFIC
   body].** Each follows the same validation-ladder skeleton:
   ```java
   public void handle<X>(DSG<X>TableEvent ev) {
       String actor = ev.getPlayer(); int error = NO_ERROR;
       if (!isPlayerInTable(actor))                     error = NOT_IN_TABLE;
       else if (!(gridState instanceof <YourState>))    error = UNKNOWN;
       else {
           int seat = getPlayerSeat(actor);
           if (seat == NOT_SITTING)                      error = NOT_SITTING;
           else if (state != GAME_IN_PROGRESS)           error = NO_GAME_IN_PROGRESS;
           else if (/* wrong opening phase */)           error = INVALID_MOVE;
           else if (gridState.getCurrentPlayer() != seat) error = NOT_TURN;
           else { /* GAME-SPECIFIC: mutate gridState; broadcast echo + side effects */ }
       }
       if (error != NO_ERROR)
           dsgEventRouter.routeEvent(new DSGMoveTableErrorEvent(actor, tableNum, move, error), actor);
   }
   ```
   Reusable primitives: `broadcastMainRoom(event)` / `broadcastTable(event)` (fan
   out) and `dsgEventRouter.routeEvent(event, player)` (unicast errors/replay).
   Use `move = -1` to report an all-or-nothing batch rejection. **Two generic
   principles:** (a) block normal `handleMove` during the opening by having your
   `GridState.isValidMove()` reject ordinary moves until the opening completes,
   and expose pure `wouldAccept...` pre-check predicates so handlers validate
   without mutating; (b) **a negotiated stone-placing event must replay the
   normal move tail** (clocks, move-time list, undo/cancel replies, the
   `DSGMoveTableEvent` placement broadcast, game-over check) so state stays
   consistent with stones placed the normal way. (Example: renju's
   `broadcastRenjuFifthMove` reproduces `handleMove`'s post-`addMove` tail
   verbatim.)

5. **Rejoin / state replay on join [PLUMBING hook / GAME-SPECIFIC signalling].**
   In `ServerTable`, before `sendMoves(player)`, unicast a **single phase-marker
   event** (with `player == null`) that lets a (re)joining client reconstruct the
   current opening phase; seats come separately from the already-authoritative
   `sendPlayingPlayers`. Implement your own `encode(state) -> signal` + matching
   unicasts. (Example: renju reuses the existing silent `DSGSwapSeatsTableEvent`
   as a phase marker.)

6. **Other `ServerTable` wiring [PLUMBING].** Generalize the game-start auto-move
   from `handleMove(name, 180)` to `handleMove(name,
   GridStateFactory.getCenterMove(game.getId()))`. Add your ids to the rated-set
   OR-chains: the `single_game` test (~line 2672) and the `k32Game` set (rating
   K = 32 vs 64) — or you silently inherit the default. Persist opening state via
   `if (gridState instanceof <YourState>) gameData.set...`.

7. **Mobile `GameResponse` (REST snapshot) [PLUMBING threading / GAME-SPECIFIC
   fields].** `dsg_src/java/org/pente/gameServer/mobile/GameResponse.java`. Add
   final fields populated only for your game; extend the **single all-args
   constructor** (update both call sites — the live `fromTbGame` factory and the
   historic factory); gate on `tbGame.getGame() == TB_<GAME>`, else null. The
   field names are a contract React/mobile clients hard-code.

## Layer 4 — Turn-Based Web (Servlets + Contract)

The HTTP entry point for human-vs-human turn-based play. **Central lesson:** pull
per-game opening logic out of the servlet into a *pure, I/O-free "contract"
resolver* — that contract object is the key extension seam. A game with no
special opening needs no contract (it uses the plain single-stone `else` branch).

1. **`NewGameServlet` — single-game vs two-game match [PLUMBING].**
   `dsg_src/java/org/pente/turnBased/web/NewGameServlet.java` (~line 335).
   `createSet` builds two `TBGame`s (inviter as black, then as white). For a
   single-game / color-choice game, add your `TB_<GAME>` to the collapse chain so
   only the inviter's chosen color survives (`playAs == 2 ? keep white : keep
   black; tbg2 = null`). Skip for a conventional two-color match.

2. **`ReplyInvitationServlet` — beginner auto-clone [PLUMBING].**
   `.../web/ReplyInvitationServlet.java` (~line 267). Mirror step 1: add your
   `TB_<GAME>` to the guard that decides whether to build `beginnerGame2`. **Keep
   in sync with step 1.** Gotcha: always use the **`TB_`-prefixed** constants in
   turn-based servlets — the non-prefixed ids belong to the live subsystem (a
   pre-existing bug referenced the live `GO13` id here and never matched).

3. **`MoveServlet` — the dispatch chain [PLUMBING chain / GAME-SPECIFIC
   translation].** `.../web/MoveServlet.java` (~line 409). `doPost` parses the
   `moves` param then runs a long `if/else if` chain keyed on `game.getGame()`.
   Insert a new branch:
   ```java
   if (game.getGame() == GridStateFactory.TB_<GAME> && <game>Action != null) { ... }
   else if (/* dpente/dkeryo */) { ... }
   ...
   else { /* normal single-stone store */ }
   ```
   Drive it with a new request param (`<game>Action`, absent → falls through to
   the normal store). Keep the branch **thin glue**: reconstruct engine state,
   call the pure contract, `switch` on the returned `Decision.kind` to issue
   storer calls. **Do not `return` on success** — fall through to the shared tail
   (`loadGame(gid)` refresh, then notification/redirect) so you inherit
   win-detection/notification/redirect for free. Add a
   `catch (InvalidMoveException ime)` at the end of `doPost` as a generic safety
   net.

4. **Write the `<Game>TbContract` resolver — THE extension seam [PLUMBING pattern
   / GAME-SPECIFIC contents].** `.../web/<Game>TbContract.java`. A `final` class
   with a private constructor and one pure static method:
   ```java
   public static Decision resolve(String action, int[] moves, <YourState> pending)
           throws <Game>ContractException
   ```
   It validates against the engine's **read-only** predicates (no mutation) and
   returns a typed `Decision` (a `Kind` enum + a value object describing the plan
   of mutations), or throws a `<Game>ContractException` **whose message is
   surfaced verbatim to the user** via `handleError`. WHY: keeps all rule logic
   out of the servlet and makes every opening rule unit-testable without HTTP/DB/
   storer. The servlet executes the plan; the contract only decides it.
   (Example: `RenjuTbContract` maps verbs `swap`/`select`/`move` to
   `TAKE_OVER`/`PLACE`/`BRANCH_A`/`BRANCH_B`/`SELECT`.)

## Layer 5 — Web UI (JSP, Board JS, Constants, Viewers)

1. **`gameConstants.jspf` — the client-side id registry [PLUMBING — DO FIRST].**
   `dsg_src/httpdocs/gameServer/gameConstants.jspf`. This is the single bridge
   from the server id enum to client JS, emitting `var GAME = { ... }`. Add one
   entry per variant:
   ```jsp
   <GAME>:       <%= GridStateFactory.<GAME> %>,
   SPEED_<GAME>: <%= GridStateFactory.SPEED_<GAME> %>,
   TB_<GAME>:    <%= GridStateFactory.TB_<GAME> %>
   ```
   Gotcha: it is a plain object literal — the **last** entry must have no trailing
   comma. Get it wrong and the whole `GAME` object is a JS syntax error.

2. **`tb/gameScript.js` — color + replay + grid [PLUMBING + GAME-SPECIFIC].**
   `dsg_src/httpdocs/gameServer/tb/gameScript.js`. Three hooks: (a) add a
   `var <game>Color = "#hex";`; (b) add a `replay<Game>Game(abstractBoard,
   movesList, until)` with the fixed signature — `resetAbstractBoard` then a loop
   writing stone colors (value 2 = black, 1 = white) into
   `abstractBoard[move % gridSize][floor(move / gridSize)]`; (c) for non-19x19
   boards, add your three **raw numeric ids** to the `drawGrid` star-point
   branches (exclude from the 19x19 fixed branch, include in the size-derived
   branch). Critical: `resetAbstractBoard` must use `% gridSize` / `/ gridSize`,
   not `% 19` — a 15x15 move indexed mod-19 lands off-board and stones silently
   never draw.

3. **`js/boardCommon.js` — the two dispatch switches [PLUMBING].**
   `dsg_src/httpdocs/gameServer/js/boardCommon.js`. Add a `case GAME.<GAME>: case
   GAME.SPEED_<GAME>: case GAME.TB_<GAME>:` group to both `switch(game)` —
   `getBoardColor` (→ `<game>Color`) and the replay dispatch (→
   `replay<Game>Game(...)`). Unknown ids throw on purpose, so omission fails
   loudly.

4. **`js/boardCommon.test.js` — the sync guard [PLUMBING].** Register your game
   in three spots (id map, `FAMILIES`, `REPLAY_FNS`) and run it; this node test
   asserts every family has a color var + replay fn wired into both switches.

5. **The recurring `gridSize` scriptlet [PLUMBING — all-or-nothing].** Any
   non-19x19 board must declare its size in **every** page that builds a
   `GridCoordinates` or sizes the canvas. Add the one-liner
   `} else if (gameId == GridStateFactory.TB_<GAME>) { gridSize = W; }` to all of:

   | File | Sizes |
   |---|---|
   | `tb/mobileGame.jsp` | live play board |
   | `tb/listedMobileGame.jsp` | game-list thumbnail |
   | `tb/cancelReply.jsp` | cancel-request board |
   | `tb/undoReply.jsp` | undo-request board |
   | `viewLiveGameEmbed.jsp` | embedded live viewer |
   | `viewLiveGameMobile.jsp` | mobile live viewer |
   | `viewGameEmbed.jsp` | archived-game replay viewer |

   Miss one and that view renders on a 19x19 grid with stones at the wrong
   coordinates. `viewGameEmbed.jsp` additionally had `19`/`18` hardcoded
   throughout — grep for the literals and replace with `gridSize`/`gridSize-1`,
   and hoist the size block **above** `GridCoordinates` construction.

6. **`tb/new.jsp` — creation form [PLUMBING is automatic].** The game dropdown is
   auto-populated from `GridStateFactory.getTbGames()`, so a newly-registered TB
   game appears with **zero** changes. Only color-asymmetric games (where you may
   pick a side even when rated) add the `colorChoiceWhenRated` id whitelist
   (mirroring `NewGameServlet`) + `updateOptions()` show/hide logic.

## Layer 6 — React Live Game Room

`react-live-game-room/src/`. The frontend has its **own** id→variant partition
and does **not** read `GridStateFactory`, so the three numeric ids are hardcoded.

1. **`game/boardGeometry.js` [PLUMBING].** Add a branch to `variantKey(gameId)` —
   an ordered `if (id < N)` range partition; place your explicit case **before**
   the fallthrough (ordering matters). Add to `gridSizeForGame` (if non-19x19) and
   append your normal id to `STANDARD_GAME_IDS`. Star-dot layout is game-specific.

2. **`Classes/GameClass.js` `VARIANT_RULES` [PLUMBING].** Add one row keyed by
   your variant string: `{replay, disableRatedOnReplay, add, goMove, player,
   postRule}` — pick existing replay/player engines or add new ones. Opening
   predicates/phase/replay arms are game-specific.

3. **Display + color maps [PLUMBING].** Add one entry each to
   `Classes/utils.js` `VARIANT_NAMES` and the `Classes/TableClass.js` color map,
   keyed by variant string.

4. **`protocol/messages.js` event registration [PLUMBING].** Register one
   `{dir, cmd, out, req}` entry per new live event (mirrors the Java DSG events).

5. **Opening UI (game-specific).** `game/gameState.js`, `game/openingPhase.js`,
   reducers, `Components/Board/Board.js`, and any modal/panel components carry the
   opening interaction.

## Game-ID Switch Points Checklist

Every file/location that branches on game type. Thread your ids through each that
applies. `[P]` = plumbing every game replicates; `[G]` = game-specific (opening/
rules) that a plain game skips.

| File | Location | Branch kind | Class |
|---|---|---|---|
| `game/GridStateFactory.java` | id constants, `LIVE_GAMES`/`TB_GAMES`, `Game` objects, the 5 lookup tables | arrays/constants | [P] |
| `game/GridStateFactory.java` | `createGridState(int,int,int)` switch | `switch(game)` | [P] case / [G] body |
| `game/GridStateFactory.java` | `isValidGame` (~L401), `getMaxGameId` (~L427) | range/max | [P] |
| `gameServer/server/ServerTable.java` | `single_game` OR-chain (~L2673) | id OR-chain | [P] |
| `gameServer/server/ServerTable.java` | `k32Game` OR-chain (~L3535) | rating K-factor | [P] |
| `gameServer/server/ServerTable.java` | swap2/Go/Pente family OR-chains | id OR-chains | [P] evaluate |
| `gameServer/server/ServerTable.java` | center auto-move | `getCenterMove` | [P] |
| `gameServer/server/ServerTable.java` | opening handlers + rejoin | handler bodies | [G] |
| `gameServer/server/SynchronizedServerTable.java` | event dispatch switch (~L213) | pattern `switch` on event type | [G] arm |
| `gameServer/event/DSGEventWrapper.java` | field + getter/setter per event | wrapper container | [P] |
| `turnBased/TBGame.java` | `getCurrentPlayer` seat branch (~L327) | `if (game==TB_X)` | [G] |
| `turnBased/TBGame.java` | phase accessor + opening fields | accessor | [G] |
| `turnBased/TBGameStorer.java` | negotiation verbs (interface) | interface methods | [G] |
| `turnBased/MySQLTBGameStorer.java` | `TB_COLUMNS` SELECT/INSERT + mapRow + verbs | column lists | [G] |
| `turnBased/CacheTBStorer.java` | cache hooks + K-factor (~L941) | `if (game==TB_X)` | [P] K / [G] state |
| `turnBased/web/NewGameServlet.java` | single-game/color collapse (~L335) | id OR-chain | [P] |
| `turnBased/web/ReplyInvitationServlet.java` | beginner-clone guard (~L270) | `&& game != TB_X` | [P] |
| `turnBased/web/MoveServlet.java` | dispatch chain + `<game>Action` (~L409) | `if (game==TB_X)` | [G] |
| `game/GameData.java` / `DefaultGameData.java` | `default` accessors / impls | default methods | [P] |
| `gameServer/mobile/GameResponse.java` | `isRenju`-style gate (~L171), stones-per-move (~L283) | `if`/ternary | [G]/[P] |
| `gameServer/mobile/MobileJsonHelper.java` | `isGoLikeGame` (~L62) | id set | [P] evaluate |
| `sql/schema.sql` + migration | columns on `tb_game`/`tb_game_ai`/`pente_game` + child table | DDL | [G] |
| `httpdocs/gameServer/gameConstants.jspf` | `GAME.*` entries (~L49) | JS constant table | [P] |
| `httpdocs/.../viewLiveGameMobile.jsp`, `viewGameEmbed.jsp`, `viewLiveGameEmbed.jsp` | render select | `else if (gameId==...)` | [P] |
| `httpdocs/.../tb/undoReply.jsp`, `cancelReply.jsp`, `listedMobileGame.jsp`, `mobileGame.jsp` | per-game TB render | `else if` | [P] |
| `httpdocs/.../tb/new.jsp` | `colorChoiceWhenRated[]` (~L81) | JS array | [P] |
| `httpdocs/.../tb/mobileGame.jsp` | opening UI (~L129) | phase buttons | [G] |
| `httpdocs/.../js/boardCommon.js` | `getBoardColor` + replay switches | `switch(game)` | [P] |
| `httpdocs/.../tb/gameScript.js` | color var, replay fn, star-points | id list | [P]/[G] |
| `react-live-game-room/.../boardGeometry.js` | `variantKey`/`gridSizeForGame`/`STANDARD_GAME_IDS` | range partition | [P] |
| `react-live-game-room/.../GameClass.js` | `VARIANT_RULES` + predicates | config map | [P]/[G] |
| `react-live-game-room/.../utils.js`, `TableClass.js` | `VARIANT_NAMES` + color | maps | [P] |
| `react-live-game-room/.../protocol/messages.js` | event registration (~L47) | event map | [P] |

**Sites renju did NOT touch but a new game MUST evaluate:** tournament
eligibility (`tourney/DoubleEliminationFormat.java`,
`SingleEliminationFormat.java`, `CacheTourneyStorer.java` is-Go exclusion chains),
`mobile/MobileJsonHelper.java` Go-like set, `SGFGameFormat.java` `GM[]` export
tag, and Connect6 multi-stone handling
(`client/web/CoordinatesListPanel.java`, `GameResponse.java`). These branch on
game type and renju was deliberately excluded — a different game may need
inclusion.

**Free / automatic:** `DSGContextListener` auto-registers the 6 `game_event` rows
per game at boot from `GridStateFactory`, and `MySQLGameVenueStorer` seeds the
venue tree — **no manual SQL** once the game is in the factory.

## Optional: Negotiated Opening Phase

Skip this section entirely if your game has no multi-step opening (its
`isValidMove`/`getCurrentPlayer` just alternate, and `getInstance` is the only
reconstruction path). If it does, replicate this extra plumbing — the *pattern*
is reusable, the contents are game-specific.

- **Opening-phase enum** — a `<Game>OpeningPhase` enum naming each decision point
  (example: renju `SWAP, BRANCH, SELECTION, MOVE, COMPLETE`). `<YourState>`
  computes it from internal predicates; it is the public "where are we" handle the
  servlet/UI branch on.
- **Opening-state codec** — the value object from Layer 2 step 1 (pack the
  decision-vector to one int, variable-length data to bytes; pure inverse
  `encode`/`decode`). This is the persistence bridge to the `GameData` columns.
- **State-machine hooks** — internal flags driven by `addMove()` plus explicit
  decision hooks the servlet calls (example: `<game>SwapDecisionMade(boolean)`,
  `chooseBranch(...)`, `offer...`, `select...`). Provide **pure validate-without-
  mutating companions** (`wouldAccept...`) so handlers pre-check without side
  effects, and have `isValidMove()` reject ordinary moves while a window is open.
- **Rejoin codec** — a `<Game>Rejoin` with a `RejoinSignal` derived purely from
  the current state (`encode(state) -> signal`) and a `decode(numMoves, signal) ->
  phase` that satisfies `decode(numMoves, encode(state)) == phase` for every
  rejoin-reachable state. Both pure → unit-testable.
- **`reconstruct(MoveData, packed, offers)`** — the full-fidelity rebuild that
  replays moves **and** re-applies the opening decisions in protocol order,
  stopping at the still-pending window. This is the richer counterpart to
  `getInstance`, used wherever opening decisions matter.
- **Opening table events + dispatch** — Layer 3 steps 1–4: new `DSG<X>TableEvent`
  classes, `DSGEventWrapper` registration, `SynchronizedServerTable` dispatch
  arms, and `ServerTable` handlers + rejoin replay.
- **Opening-state SQL columns + storer parity** — Layer 2: the inline int column
  on `tb_game`/`tb_game_ai`/`pente_game`, the archival child table, the codec, and
  the verbs implemented in lock-step across all storers (see parity checklist).
- **Contract object** — Layer 4 step 4: the pure `<Game>TbContract.resolve(action,
  moves, state) -> Decision | exception` seam.
- **Opening UI** — `mobileGame.jsp` decision UI (phase vars from the `TBGame`
  model → gate the generic Submit → phase buttons → tap/boardClick interception →
  `<game>Post()` posting opening actions on `command=move` with a `<game>Action`
  discriminator) and the React modal/panel components.

## Persistence Parity Checklist

If you persist opening/extra state, **every** storer below must agree — adding a
verb to the `TBGameStorer` interface forces the implementors to update or
compilation fails, which is the safety net. Confirm all of:

| Storer | File | Must |
|---|---|---|
| MySQL TB | `turnBased/MySQLTBGameStorer.java` | extend both `TB_COLUMNS` lists (append at end) + positional reads in mapRow + targeted `UPDATE` per verb |
| Cache TB (write-through front) | `turnBased/CacheTBStorer.java` | mirror `getGameData` copy + write-through template per verb + validation branch + K-factor + `getCenterMove` |
| InMemory TB (test double) | `turnBased/test/InMemoryTBGameStorer.java` | no-op stub per new interface method |
| MySQL archival | `game/MySQLPenteGameStorer.java` | inline int col in INSERT/SELECT (`wasNull` guard) + child-table offers store/load + `getCenterMove` |

Also confirm: SQL columns on **all** of `tb_game`, `tb_game_ai`, `pente_game`
(+ the child table); the `GameData`/`DefaultGameData` carrier fields; and the
`TBGame.getGameData()` TB→archival handoff copy.

## Testing

Mirror the renju test classes for your game:

- `game/test/RenjuFactoryTest.java` → factory wiring: id literals, normal/speed/TB
  mappings, 15x15 creation, `getInstance` returns your type,
  `isValidGame`/`getMaxGameId`, name lookup, board-aware center move.
- `game/test/RenjuStateTest.java` + `RenjuForbiddenPointFinderTest.java` → engine
  rules.
- `game/test/RenjuOpeningStateTest.java` → codec `encode`/`decode` round-trips.
- `game/test/RenjuReconstructTest.java` → the rejoin contract
  `decode(numMoves, encode(state)) == phase`.
- `turnBased/test/TBGameRenjuPhaseTest.java` → phase derivation via plain setters
  (`setMoves`, opening setters) — no DB. Provide the same setters for free
  testability.
- `turnBased/web/test/RenjuTbContractTest.java` → the pure contract: builds engine
  states via state-machine helpers and asserts both accepted `Decision` shapes and
  that every illegal request throws. (JUnit 3.7 `TestCase`: explicit `(String
  name)` constructor; `assertFalse` unavailable → use `assertTrue(!...)`.)
- `httpdocs/gameServer/js/boardCommon.test.js` → run after wiring the JS switches.

## Minimal Checklist

Copy-pasteable, in dependency order. `[opening]` steps are only for games with a
negotiated opening; a plain 19x19 reuse-an-engine game does only the unmarked
steps.

- [ ] Mint id triple `<GAME>`/`SPEED_<GAME>`/`TB_<GAME>` in `GridStateFactory` (next free odd N, +1, +50)
- [ ] Add ids to `LIVE_GAMES[]`/`TB_GAMES[]`; create 3 `Game` objects; insert into all 5 lookup tables (`allGames` in id order)
- [ ] Bump `isValidGame` upper bound + `getMaxGameId`
- [ ] Add `createGridState` switch case returning `new <YourState>(...)`
- [ ] Write `<YourState>` (extend `GridStateDecorator`, implement rules) + override `getInstance(MoveData)`
- [ ] Reuse `getCenterMove` for any centered first stone
- [ ] [opening] Build the opening-phase enum, codec (`encode`/`decode`), state-machine hooks + `wouldAccept...` pre-checks, rejoin codec, and `reconstruct(...)`
- [ ] [opening] Add carrier fields: `GameData` `default` accessors + `DefaultGameData` fields
- [ ] [opening] Author idempotent SQL migration (columns on `tb_game`+`tb_game_ai`+`pente_game` + child table); regenerate `schema.sql`
- [ ] [opening] `TBGame`: opening fields + getters/setters + `getCurrentPlayer` branch + `getGameData` handoff + phase accessor
- [ ] [opening] Add verbs to `TBGameStorer` interface; implement in `MySQLTBGameStorer`, `CacheTBStorer`, `InMemoryTBGameStorer` (no-op)
- [ ] [opening] `MySQLPenteGameStorer` archival store + load (inline int + child-table offers)
- [ ] [opening] Replace hardcoded `180` with `getCenterMove(game)` everywhere
- [ ] [opening] Live events: define `DSG<X>TableEvent` classes, register in `DSGEventWrapper`, dispatch in `SynchronizedServerTable`, handlers + rejoin in `ServerTable`
- [ ] Add ids to `ServerTable` `single_game` + `k32Game` chains as appropriate
- [ ] `NewGameServlet` (+ `ReplyInvitationServlet`, in sync): single-game collapse if color-choice — use `TB_`-prefixed constants
- [ ] [opening] `MoveServlet`: new branch + `<game>Action` param → `<Game>TbContract.resolve` → storer calls; do NOT `return` on success; add `InvalidMoveException` catch
- [ ] [opening] Write `<Game>TbContract` (pure resolver) + its unit test
- [ ] `gameServer/mobile/GameResponse.java`: thread new fields through the all-args constructor (both factories)
- [ ] `gameConstants.jspf`: add `GAME.<GAME>`/`SPEED_<GAME>`/`TB_<GAME>` (mind trailing comma)
- [ ] `tb/gameScript.js`: `<game>Color`, `replay<Game>Game`, `drawGrid` star-points; ensure `resetAbstractBoard` is gridSize-based
- [ ] `js/boardCommon.js`: add case to both switches; update + run `boardCommon.test.js`
- [ ] Add the `gridSize` scriptlet to all 7 JSP pages; de-hardcode `19`/`18` in `viewGameEmbed.jsp`
- [ ] `tb/new.jsp`: only if color-asymmetric (`colorChoiceWhenRated` + `updateOptions`)
- [ ] React: `boardGeometry.variantKey`/`gridSizeForGame`/`STANDARD_GAME_IDS`, `GameClass.VARIANT_RULES`, `VARIANT_NAMES` + color map, `protocol/messages.js` events, (opening) modals/reducers
- [ ] Evaluate the "did-NOT-touch" sites: tournament eligibility, `MobileJsonHelper` Go-like set, `SGFGameFormat` `GM[]` tag, Connect6 multi-stone
- [ ] Mirror the renju test classes for your game
