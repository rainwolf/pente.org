# Game-ID Touchpoint Inventory — Java server CORE

Subsystem: `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java`
(791 `.java` files swept). **EXCLUDED by assignment**: `MySQL*Storer` / `Cache*Storer`
classes and the `org.pente.gameServer.event` wire codec (`DSGEventWrapper` & friends) —
covered by sibling agents. Hand-off points to those layers are listed in a dedicated
section at the end.

Recon only. No file modified, no DB write.

Method note: every line below was re-derived by direct targeted `grep`/`sed` against the
source tree in this session and then read in context. I did **not** rely on any cached
tool-result blob.

---

## 0. The id scheme, as the core actually implements it

Source of truth: `dsg_src/java/org/pente/game/GridStateFactory.java`.

```
live normal  N     odd,  1..31
live speed   N+1   even, 2..32
turn-based   50+N  odd,  51..81      (TB_START = 50, private)
```

16 game families currently exist (PENTE=1 … RENJU=31). The **hard cap is
`TB_START = 50`**: the 17th family would need `N = 33`, whose speed twin is 34 and whose
turn-based id would be 83 — but 33 and 34 collide with nothing yet, while ids 33..48 are
*already consumed as array positions* inside `allGames[]` (see class D). The cap is
therefore **two independent limits stacked on the same number**:

1. `isSpeedGame(game)` / `isTurnbasedGame(game)` partition the id space at the literal 50.
2. `allGames[]` is simultaneously id-indexed (1..32) **and** positionally packed
   (33..48 hold the TB `Game` objects whose real ids are 51..81).

Limit 2 bites *first*: adding family 17 as `N=33` would make `allGames[33]`
(currently `TB_PENTE_GAME`) ambiguous.

The `docs/adding-a-new-game-playbook.md` §Layer 1 states the contract explicitly
(`adding-a-new-game-playbook.md:29-34`): *"each game consumes three consecutive ids …
the odd/even/+50 adjacency is assumed by helper math across the codebase, you cannot
insert in the middle without renumbering."*

---

## Class A — GridStateFactory: id constants and the registry itself

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:7-38` | `PENTE = 1; SPEED_PENTE = PENTE + 1; … RENJU = 31; SPEED_RENJU = RENJU + 1;` | 32 live id constants, speed twin defined as `+1` | Keep constants as opaque handles; drop the `+1` derivation so speed becomes a *flag* on a descriptor, not an id offset |
| `game/GridStateFactory.java:40-57` | `public static final int[] LIVE_GAMES = { PENTE, SPEED_PENTE, … RENJU, SPEED_RENJU }` | flat id list, **ordered normal-then-speed in pairs** — consumers rely on "odd first" (see `IndexResponse:420`) | Replace with an ordered `List<GameDescriptor>`; preserve iteration order or you reorder user-visible KOTH lists |
| `game/GridStateFactory.java:61` | `private static final int TB_START = 50;` | the cap constant. **private** — every external caller re-hardcodes the literal `50` | Making it `public` is a zero-risk stage-0 win: it turns ~10 magic literals into one symbol |
| `game/GridStateFactory.java:62-77` | `TB_PENTE = TB_START + PENTE; … TB_RENJU = TB_START + RENJU;` | 16 TB id constants derived by offset | Same treatment as speed: TB-ness becomes a variant flag on a descriptor |
| `game/GridStateFactory.java:79-84` | `public static final int[] TB_GAMES = { TB_PENTE, … TB_RENJU }` | flat TB id list | as above |
| `game/GridStateFactory.java:85` | `public final static int[] ALL_GAMES = ArrayUtils.addAll(TB_GAMES, LIVE_GAMES);` | union, **TB ids first, then live** | Order is observable: `CacheKOTHStorer:150` iterates it. Preserve order when replacing |
| `game/GridStateFactory.java:88-137` | `public static final Game PENTE_GAME = new Game(PENTE, "Pente", false); …` (48 `Game` singletons) | id → (name, isSpeed) value objects, one per live+speed+TB id | These are the natural seed of a `GameDescriptor` registry |
| `game/Game.java:3-37` | `class Game { int id; String name; boolean speed; }` | the descriptor type. No board size, no rules, no TB flag | **Extension point**: add `variant` (LIVE/SPEED/TB) + `baseFamily` here and the arithmetic helpers can be reimplemented without touching call sites |
| `game/Game.java:28-33` | `static boolean isSpeedGame(int initial, int incremental)` | *time-control* speed test, unrelated to id parity | Do not conflate with `GridStateFactory.isSpeedGame(int game)` — same name, different meaning. `ServerTable:912` uses this one |

## Class B — `+50` / `-50` / `> 50` turn-based offset arithmetic

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:467-469` | `getNormalGameFromTurnbased(game) { return game - TB_START; }` | TB id → base live id | Replace with `getGame(id).getBaseFamilyId()` |
| `game/GridStateFactory.java:475-477` | `isTurnbasedGame(game) { return game > TB_START; }` | **the cap predicate** | Replace with descriptor lookup `getGame(id).isTurnBased()`. Must be stage-1: everything downstream depends on it |
| `game/GridStateFactory.java:385-389` | `createGridState(game, moveData)`: `if (game > TB_START) tbGridStates[(game-TB_START-1)/2]…` | TB id → prototype slot via **offset + halving** (assumes TB ids are 51,53,55,… stepping by 2) | Replace with a `Map<Integer,GridState>` keyed by id |
| `game/GridStateFactory.java:392-398` | `getGame(game)`: `if (game > TB_START) tbGames[(game-TB_START-1)/2] else allGames[game]` | dual-mode lookup: id-indexed below 50, arithmetic-indexed above | Single `Map<Integer,Game>`; this one method is the highest-leverage change in the whole subsystem |
| `gameServer/tourney/Tourney.java:123` | `return this.game > 50;` (`isTurnBased()`) | hardcoded literal, not `TB_START` | Route through `GridStateFactory.isTurnbasedGame` first (pure refactor), then through descriptors |
| `gameServer/mobile/KothResponse.java:61` | `if (game > 50 && !myData.hasPlayerDonated())` | TB-only subscription gate | as above |
| `gameServer/mobile/KothResponse.java:83` | `if (game > 50) { … }` | TB branch in KOTH listing | as above |
| `gameServer/mobile/IndexResponse.java:447` | `if (gameInt > 50 && (hill.getMembers()==null \|\| isEmpty())) return;` | hide empty TB hills | as above |
| `gameServer/mobile/IndexResponse.java:481` | `String displayName = (g.getId() > 50 ? "tb-" : "") + …` | **user-visible label derived from id magnitude** | Descriptor-driven prefix |
| `kingOfTheHill/CacheKOTHStorer.java:96,177,213` | `if (game > 50)` (3 sites) | live vs TB hill routing *(Cache storer — sibling agent owns, listed for completeness)* | hand-off |
| `game/MySQLGameVenueStorer.java:466` | `int baseGame = game > 50 ? game - 50 : game;` | TB venue node shares the base game's node | hand-off (MySQL storer), but note: the *comment* at `:447` documents a fixed regression where the old code did `tree.get(game - 1 - 50)` — dense positional indexing that already broke once |
| `gameServer/tourney/CacheTourneyStorer.java:815` | `GridStateFactory.getDisplayName(game - 50)` | auto tournament naming for TB games | hand-off, but tournament-semantic: worth flagging to the tourney owner |
| `kingOfTheHill/MySQLKOTHStorer.java:46` | `// if (game > 50) {` (commented) | dead | none |
| `tools/RatingsGrapher.java:116,131,141,146-150` | `isTurnbasedGame(game) ? getNormalGameFromTurnbased(game) : game` (5 sites) | ratings history query maps TB id back to base id for the `event`/`game` columns | Offline tool. Low risk, but it is the clearest statement that **ratings storage keys on base id + a TB flag**, not on the TB id |
| `gameServer/core/FastMySQLDSGGameLookup.java:55-56,60,104-105` | `int actualGame = isTurnbasedGame(game) ? getNormalGameFromTurnbased(game) : game; boolean tb = isTurnbasedGame(game);` | same base-id+flag decomposition on the hot game-lookup path | Core, in scope. Becomes `d.getBaseFamilyId()` / `d.isTurnBased()` |

## Class C — `+1` / `-1` speed pairing and `%2` parity

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:459-461` | `getSpeedGame(Game normalGame) { return allGames[normalGame.getId() + 1]; }` | normal → speed twin by `+1` **and** id-as-index | Two assumptions in one line. Replace with `descriptor.getSpeedVariant()` |
| `game/GridStateFactory.java:463-465` | `getNormalGame(Game speedGame) { return allGames[speedGame.getId() - 1]; }` | speed → normal twin | as above |
| `game/GridStateFactory.java:471-473` | `isSpeedGame(game) { return game < TB_START && (game % 2) == 0; }` | **parity is the speed test** — this is why ids must be allocated in odd/even pairs at all | The single most constraining rule after the 50 boundary. Replace with `Game.isSpeed()` (the field already exists and is already populated correctly!) |
| `gameServer/server/ServerTable.java:912` | `boolean speed = timed && Game.isSpeedGame(initialMinutes, incrementalSeconds);` | derives *desired* speed-ness from the clock, then... | (context for next row) |
| `gameServer/server/ServerTable.java:936-941` | `if (speed && !newGame.isSpeed()) newGame = getSpeedGame(newGame); else if (!speed && newGame.isSpeed()) newGame = getNormalGame(newGame);` | **live table game-change path**: coerces the client's requested game to the correct speed variant using `±1` | Highest-traffic use of the `+1` pairing. Must be migrated together with `getSpeedGame`/`getNormalGame` |
| `gameServer/client/awt/GameBoardFrame.java:939` | `int localGame = (game - 1) / 2;` | maps live id → **dense 0-based slot** for `GAME_BG_COLORS[localGame]` and `gameBoard.setGridState(localGame)` | Legacy AWT applet client. Hard-breaks on any non-dense id. Comment above it still says "map game from 1-16" (stale by 16 ids) |
| `gameServer/client/web/GameBoard.java:226` | `setGridState(num / 2);` | same dense-slot halving in the web applet | as above |
| `gameServer/client/awt/GameBoard.java:~226` | `setGridState(num / 2);` | duplicate of the above in the AWT twin | as above |

## Class D — arrays / structures indexed *by* game id (the "allGames id-indexing" gotcha)

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:139-154` | `Game allGames[] = { null, PENTE_GAME, …, SPEED_RENJU_GAME, TB_PENTE_GAME, …, TB_RENJU_GAME };` | **49 entries. Indices 1..32 == id. Indices 33..48 hold TB games whose real ids are 51..81.** Leading `null` at 0 | The core defect. Two addressing schemes in one array. Replace with `Map<Integer,Game>` + separately-ordered lists for display |
| `game/GridStateFactory.java:237` | `GridState gridStates[] = new GridState[getNumGames() + 1];` → length **49** | prototype cache indexed by id | `getColor(moveNum, 51)` would be `ArrayIndexOutOfBounds` (see class E) |
| `game/GridStateFactory.java:240-243` | `for (int i = 1; i < gridStates.length; i++) gridStates[i] = createGridState(i);` | boot loop over ids 1..48 | **ids 33..48 have no `case` in the switch → `createGridState` returns `null` → `gridStates[33..48]` are permanently null.** Silent, currently harmless, and a trap for anyone who allocates `N=33` |
| `game/GridStateFactory.java:238,244-246` | `tbGridStates[] = new GridState[tbGames.length]; for(i…) tbGridStates[i] = createGridState(tbGames[i].getId());` | TB prototypes in a *dense positional* array, addressed by `(game-51)/2` | Positional; adding a TB game out of order corrupts every lookup |
| `game/GridStateFactory.java:388` | `gridStates[game].getInstance(moveData)` | id-as-index prototype fetch | see unknown-id section |
| `game/GridStateFactory.java:396` | `allGames[game]` | id-as-index descriptor fetch, live only | see unknown-id section |
| `game/GridStateFactory.java:460,464` | `allGames[id ± 1]` | id-as-index twin fetch | see class C |
| `game/GridStateFactory.java:479-481` | `getColor(moveNum, game) { return gridStates[game].getColor(moveNum); }` | color-to-move by id-index | **Throws AIOOBE for any TB id (51..81 > array length 49).** Currently only called with live ids |
| `gameServer/client/awt/PlayerStatsDialog.java:315-324` | `Game games[] = getAllGames(); for (int i = 1; i < games.length; i++) { … getPlayerGameData(i, …) … games[i].getName() }` | **loop index used as a game id** while also used as an array position | For `i` in 33..48 it queries player stats for *nonexistent* game ids 33..48 while displaying the names of TB games 51..81. Live latent bug caused by exactly the dual-addressing defect |
| `gameServer/client/awt/GameBoard.java:19` / `client/web/GameBoard.java:23` | `private GridState gridStates[]` + `gridStates[0] // PENTE` | applet-local dense prototype array | legacy client; positional |
| `tutorial/SimpleTutorialScreen.java:72,170,354` | `gridStates[GridStateFactory.PENTE]`, `gridStates[newGame]` | tutorial-local array indexed by game id | small, but must be migrated with the factory |
| `gameDatabase/swing/PlunkGameVenueStorer.java:516,538` | `// tree.get(game - 1)` (commented) | dead positional venue lookup | none — but it is the ancestor of the `MySQLGameVenueStorer` regression |

## Class E — dense-range iteration over the id space

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:240-243` | `for (int i = 1; i < gridStates.length; i++)` | boot prototype build, assumes ids 1..48 are contiguous and meaningful | see class D |
| `game/GridStateFactory.java:408-420` | `getGameId(String)`: `for (int i = 1; i < allGames.length; i++) if (allGames[i].getName().equals(name)) return allGames[i].getId();` then a second loop over `tbGames` | name → id. Uses `.getId()` (correct) not `i` (would be wrong) | The one place that iterates `allGames` *safely*. Note: live and TB share display names ("Pente"), so the live id always wins — intended |
| `gameServer/client/awt/test/CustomTablesPanelTest.java:88` | `for (int i = 1; i <= GridStateFactory.getNumGames() + 1; i++)` | test iterates 1..49 as game ids | Test-only, but encodes `getNumGames()` == "highest id", which is false (48 vs max id 81) |
| `game/GridStateFactory.java:422-424` | `getNumGames() { return allGames.length - 1; }` → **48** | "number of games" — actually the array length, and it is neither the count of families (16) nor the max id (81) | Deeply ambiguous name. Any refactor must decide what each caller meant. Only 2 callers (line 237 and the test above) — cheap to fix early |
| `kingOfTheHill/web/KotHServlet.java:66-67` | `for (int i = 0; i < TB_GAMES.length; i++) if (game == TB_GAMES[i])` | membership test by linear scan | Already id-agnostic — good pattern to generalize |
| `kingOfTheHill/CacheKOTHStorer.java:150,164,170,294,330` | `for (int gameId : ALL_GAMES)` / `LIVE_GAMES` / `TB_GAMES` | hill enumeration over the id arrays | hand-off (Cache storer), but confirms KOTH spans **both** live and TB ids |
| `game/MySQLGameVenueStorer.java:701,705` | `for (int game : LIVE_GAMES) … for (int game : TB_GAMES) …` in `registerAllGames` | boot-time `game_event` registration: every live id gets LIVE+KOTH events, every TB id gets TB+KOTH events | hand-off, but architecturally the **good** pattern: array-driven, no arithmetic |
| `gameServer/mobile/IndexResponse.java:419-420` | `addKothEntries(result, TB_GAMES, …, false); addKothEntries(result, LIVE_GAMES, …, true); // odd first` | mobile home screen KOTH list; the trailing comment shows the caller relies on `LIVE_GAMES` being ordered normal-before-speed | Order-sensitive consumer of the arrays |
| `gameServer/mobile/IndexResponse.java:470-472` | `addRatingStats(result, getTbGames()); … getNormalGames(); … getSpeedGames();` | mobile ratings panel — three sections | Enumeration API, id-agnostic. Safe |

## Class F — validation, max-id, and enumeration APIs

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:400-402` | `isValidGame(game) { return game >= PENTE && game <= SPEED_RENJU; }` → `1..32` | **live-only** validity range. Rejects every TB id (51..81) | Misnamed: it is `isValidLiveGame`. Must become a registry `containsKey` |
| `game/GridStateFactory.java:426-428` | `getMaxGameId() { return TB_RENJU; }` → **81** | highest TB id | Used as an upper bound in servlet validation. Becomes `registry.maxId()` or, better, callers switch to membership tests |
| `turnBased/web/NewGameServlet.java:195` | `if (game == -1 \|\| game > GridStateFactory.getMaxGameId()) { … }` | **the only server-side range validation for the TB "game" request parameter** | Accepts any id in `1..81`, including the 17 phantom ids 33..50 and every live id. Weak but *fail-closed above the max* — a client sending a future id gets rejected here |
| `kingOfTheHill/web/KotHServlet.java:54-62` | `error = "Invalid game."; for (…TB_GAMES…) if (game == TB_GAMES[i]) error = null;` | KOTH join/leave validates by **membership**, not range | Correct pattern; unaffected by the cap |
| `game/GridStateFactory.java:430-432` | `getAllGames()` → the 49-slot array *including the leading null* | callers must start at `i = 1` | Every caller does (`PlayerStatsDialog:316`, `followersing.jsp:68`, `newTourney.jsp:118`, `broadcast.jsp:73`, `NewDialog2.java:19`). Returning a `List` would remove a whole class of off-by-one risk |
| `game/GridStateFactory.java:434-441` | `getDisplayName(game)`: linear scan of `displaygames[]`, **returns `null` if not found** | user-facing label | Silent `null` → JSPs render the literal string "null". This is the graceful-degradation path for an unknown id (see section E) |
| `game/GridStateFactory.java:155-204` | `displaygames[]` — 16 normal + 16 `"Turn-based X"` + 16 `"Speed X"` = 48 entries | the user-visible catalogue, hand-maintained with its own display strings | A 4th hand-maintained list. A registry should generate it |
| `game/GridStateFactory.java:206-215` | `normalGames[]` (16) | live normal catalogue | |
| `game/GridStateFactory.java:216-225` | `speedGames[]` (16) | live speed catalogue | |
| `game/GridStateFactory.java:226-235` | `tbGames[]` (16) — **also the positional backing store for `getGame`/`createGridState` above 50** | dual-purpose: display list *and* arithmetic lookup table | Splitting these two roles is a safe early stage |
| `game/GridStateFactory.java:443-457` | `getDisplayGames() / getSpeedGames() / getNormalGames() / getTbGames()` | the enumeration API the whole UI is built on | These 4 + `getAllGames` are the **entire surface** the JSP layer sees. Adding a game to the arrays automatically propagates to every page below |
| `gameServer/client/web/LeaderBoard.java:23` | `for (Game g : getDisplayGames())` | leaderboard sections | auto-extends |
| `httpdocs/gameServer/leaderboard.jsp:28`, `playerstatsbox.jsp:20`, `statsMain.jsp:29` | `getDisplayGames()` | user-facing game pickers/tables | auto-extends |
| `httpdocs/gameServer/tb/new.jsp:255`, `new2.jsp:120`, `newKotH.jsp:146`, `newAIgame.jsp:138` | `getTbGames()` | TB game-creation dropdowns | auto-extends |
| `httpdocs/gameServer/mobile/index.jsp:403,412,421` | `getTbGames() / getNormalGames() / getSpeedGames()` | mobile web ratings sections | auto-extends |
| `httpdocs/gameServer/followersing.jsp:67`, `admin/newTourney.jsp:117`, `broadcast.jsp:73` | `getAllGames()` from `i=1` | follow-filter, tournament creation, broadcast filter | auto-extends. `broadcast.jsp:75` additionally does `if (games[i].getId() > 50) continue;` — another hardcoded 50 in the JSP layer |
| `gameServer/server/DSGContextListener.java:125-138` | `int[] liveGames = GridStateFactory.LIVE_GAMES; … addServerGames(dbHandler, 1, 2, liveGames, LIVE_EVENT); … (37, …) … (46, …, goGames …) … (45, …, liveGames, KOTH_EVENT)` | **boot-time sync of server "offerings" from the factory arrays** | This is the mechanism that makes a newly-added game appear in the lobby with no manual SQL. Server 46 (Go) keeps a hand-written subset `goGames` — the one place a *per-server curated* list exists |
| `gameServer/server/DSGContextListener.java:119-141` | whole block wrapped in `try { … } catch (Throwable t) { log4j.error(…) }` | boot registration is **best-effort** | Good: a bad new game id cannot brick startup |

## Class G — family-membership OR-chains (rules dispatch by id)

These are the "does this game behave like X" tests. None of them crash on an unknown id;
they all fall through to a default. That is the single most important fact for staged
rollout.

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `game/GridStateFactory.java:262-381` | `switch (game) { case PENTE: case SPEED_PENTE: case TB_PENTE: … }` — **the only `switch(game)` in the codebase** | id → rules object. Each family lists its 3 ids | `default:` falls through to `return null` at `:381`. Replace with a per-descriptor factory lambda |
| `game/GridStateFactory.java:502-507` | `isSingleGameSet(game)`: `game == GO \|\| GO9 \|\| GO13 \|\| SPEED_* \|\| TB_* \|\| RENJU \|\| SPEED_RENJU \|\| TB_RENJU` (12 ids) | tournament set = 1 game instead of a colour-alternating pair | Becomes a boolean on the descriptor |
| `game/GridStateFactory.java:522-532` | `firstMoveCanBeOffCenter(game)`: DPENTE/DKERYO/GO/GO9/GO13 families (~15 ids) | whether `moves[0]` is the forced board centre — **load-bearing for the personal-collection storage model** | Becomes a descriptor flag. Its docblock warns that variants returning `true` "must be rejected or handled specially or silently corrupted" |
| `game/GridStateFactory.java:486-494` | `getCenterMove(game)`: `gs.getGridSizeX()/2, /2 → convertMove` | derived from the actual board, **not hardcoded 19x19** | Already id-agnostic. Good |
| `gameServer/server/ServerTable.java:1883-1892` | `if (game == PENTE_GAME \|\| SPEED_PENTE \|\| KERYO \|\| SPEED_KERYO \|\| BOAT_PENTE \|\| SPEED_BOAT \|\| POOF \|\| SPEED_POOF \|\| OPENTE \|\| SPEED_OPENTE) ((PenteState)gridState).setTournamentRule(rated)` | unrated games drop the tournament rule | New game not listed → keeps whatever `createGridState` set. Safe default |
| `gameServer/server/ServerTable.java:1930-1938` | `if (game != GO… && != GO9… && != GO13… && != SWAP2PENTE… && != SWAP2KERYO…) handleMove(p2, GridStateFactory.getCenterMove(game.getId()));` | **auto-place the centre stone** unless the family has a free/negotiated first move | **Fail-open in the dangerous direction**: an unlisted new game gets a forced centre stone. Any new free-placement variant MUST be added here |
| `gameServer/server/ServerTable.java:1961-1967` | `gridState.getNumMoves() > 1 \|\| game == DPENTE \|\| DKERYO \|\| GO \|\| GO9 \|\| GO13 \|\| SWAP2PENTE \|\| SWAP2KERYO` (+ speed twins) | "has the opening really started" | same class as above |
| `gameServer/server/ServerTable.java:532-533` | `(game == SWAP2PENTE_GAME \|\| SPEED_SWAP2PENTE \|\| SWAP2KERYO \|\| SPEED_SWAP2KERYO) && …` | swap2 opening handler guard | [G] game-specific |
| `gameServer/server/ServerTable.java:543-546` | `(game == DPENTE \|\| SPEED_DPENTE \|\| DKERYO \|\| SPEED_DKERYO \|\| SWAP2PENTE \|\| SPEED_SWAP2PENTE \|\| SWAP2KERYO \|\| SPEED_SWAP2KERYO) && …` | double-move / swap2 opening guard | [G] |
| `gameServer/server/ServerTable.java:2054-2057` | `(game == DPENTE \|\| SPEED_DPENTE \|\| DKERYO \|\| SPEED_DKERYO) && …` | D-family second-stone handling | [G] |
| `gameServer/server/ServerTable.java:2778-2781` | `boolean single_game = (game.getId()==GO \|\| SPEED_GO \|\| GO9 \|\| SPEED_GO9 \|\| GO13 \|\| SPEED_GO13 \|\| RENJU \|\| SPEED_RENJU)` | live-set structure: 1 game vs 2 | **Hand-copied duplicate of `GridStateFactory.isSingleGameSet`** (live subset only — correct today, but two lists to keep in sync). Collapse into the factory predicate |
| `gameServer/server/ServerTable.java:2784,2804` | `if (set.getG1Gid() == 0 && !single_game) … else if (single_game)` | consumers of the above | |
| `gameServer/server/ServerTable.java:3650-3653` | `boolean k32Game = game==GO \|\| SPEED_GO \|\| GO9 \|\| SPEED_GO9 \|\| GO13 \|\| SPEED_GO13 \|\| RENJU \|\| SPEED_RENJU` | **rating K-factor selection** — third hand-copy of the same id set | `k32Game ? 32 : 64` at `:3718, :3738, :3797`. A new game silently defaults to K=64. Deliberate per-game choice needed → descriptor field `kFactor` |
| `gameServer/server/ServerTable.java:3657,3718,3738,3797` | `double k = k32Game ? 32 : 64;` | Elo update | see above |
| `turnBased/TBGame.java:196-202` | `if (game == TB_GO) … else if (TB_GO9) … TB_GO13 … TB_RENJU` | board geometry per TB game | Unknown TB id → falls through to the 19x19 default |
| `turnBased/TBGame.java:293-330` | `if (TB_GO\|TB_GO9\|TB_GO13) … else if ((TB_DPENTE\|TB_DKERYO) && …) … else if ((TB_SWAP2PENTE\|TB_SWAP2KERYO) …) … else if (TB_CONNECT6) … else if (TB_RENJU)` | `getCurrentPlayer()` seat/colour rotation — **the single most game-specific TB branch** | An unlisted new TB game gets plain alternating seats. Wrong for any negotiated-opening variant, but does not crash |
| `turnBased/TBGame.java:557,618,743,746` | `if (game != TB_RENJU) …` / `== TB_RENJU` / `== TB_SWAP2PENTE \|\| TB_SWAP2KERYO` | renju + swap2 opening state plumbing | [G] |
| `turnBased/TBGame.java:626-629` | `return game == TB_DPENTE \|\| TB_DKERYO \|\| TB_SWAP2PENTE \|\| TB_SWAP2KERYO;` | "has a negotiated opening" | descriptor flag |
| `turnBased/web/MoveServlet.java` (20 constant refs, incl. `:113` `isSwap2 = game.getGame() == TB_SWAP2PENTE \|\| …`) | TB move-submission branches per opening family | [G] |
| `gameServer/mobile/GameResponse.java:168-174` | `(TB_DPENTE \|\| TB_DKERYO \|\| TB_SWAP2PENTE \|\| TB_SWAP2KERYO)` and `== TB_RENJU` | mobile JSON opening-phase flags | [G] |
| `gameServer/mobile/GameResponse.java:286` | `+ (tbGame.getGame() == TB_CONNECT6 ? 2 : 0)` | Connect6 double-stone move-count fudge | [G] |
| `gameServer/mobile/MobileJsonHelper.java:62-64` | `game == TB_GO \|\| TB_GO9 \|\| TB_GO13` | Go-like JSON shape | [P] evaluate |
| `gameDatabase/HttpGameServlet.java:298-303` | `getGame()==DPENTE \|\| SPEED_DPENTE \|\| DKERYO \|\| SPEED_DKERYO \|\| GO \|\| SPEED_GO` | game-database search: do not synthesize the centre first move | Hand-copied variant of `firstMoveCanBeOffCenter` |
| `gameDatabase/MobileGameServlet.java:222-225` | same list **minus `GO`/`SPEED_GO`** | same purpose, mobile endpoint | **Confirmed divergence** between the two endpoints, and both diverge from `GridStateFactory.firstMoveCanBeOffCenter` (which also covers GO9/GO13/TB ids). Pre-existing bug; collapsing all three onto the factory predicate is a clean early stage |
| `puzzle/swing/PuzzlePanel.java:153-155` | `puzzle.getGame() == TB_PENTE \|\| TB_KERYO \|\| TB_BOAT_PENTE` | puzzle rendering | small |

## Class H — AI / bot wiring

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `gameServer/server/MMAIPlayer.java:70-77` | `SUPPORTED_GAMES = new HashSet<Integer>(Arrays.asList(PENTE, SPEED_PENTE, KERYO, SPEED_KERYO, POOF_PENTE, SPEED_POOF_PENTE, CONNECT6, SPEED_CONNECT6, BOAT_PENTE, SPEED_BOAT_PENTE, OPENTE, SPEED_OPENTE))` | **allow-list** of the 12 ids the C++ MMAI sidecar can play | Best-practice pattern in the whole subsystem: a `Set`, not a range. Its docblock states *"Anything else would be silently remapped to the plain Pente engine, so it is rejected up front."* An unknown/new id is **rejected, not mis-played** |
| `gameServer/server/MMAIProtocol.java:47-48` | `return game == CONNECT6 \|\| game == SPEED_CONNECT6;` | two-stones-per-turn protocol flag | descriptor flag |
| `gameServer/server/XMLAIConfigurator.java:122` | `int validGame = GridStateFactory.getGameId(textBuffer.toString());` | AI config XML declares supported games **by display name**, resolved to an id at load | Name-keyed, so id renumbering is invisible here — but an unknown *name* throws `IllegalArgumentException` (see section E) |
| `gameServer/server/ServerTable.java` (`removeAllComputers()` at the game-change branch, `:948-951`) | on game change: `removeAllComputers(); // they might not know how to play the different game` | bots are evicted whenever the table's game changes | Safe default for new games: no bot is offered unless explicitly wired |
| `gameServer/server/MarksAIPlayer.java:770,1264` | `tmpscr[...] += 50; sco[fr] += 50;` | **false positives** — heuristic score bonuses, not game ids | ignore |

## Class I — tournaments and King of the Hill

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `gameServer/tourney/Tourney.java:20,87,94-95` | `private int game; getGame(); setGame(int)` | a tournament stores **one** id, which may be live *or* TB | KOTH/tourney id spans are the reason `ALL_GAMES` exists |
| `gameServer/tourney/Tourney.java:91` | `return GridStateFactory.getDisplayName(game);` | tournament title | **`getDisplayName` returns `null` for an unknown id** → NPE risk downstream / literal "null" in UI |
| `gameServer/tourney/Tourney.java:123` | `return this.game > 50;` | `isTurnBased()` | see class B |
| `gameServer/tourney/SingleEliminationFormat.java:111` | `if (!GridStateFactory.isSingleGameSet(tourney.getGame())) { … }` | bracket = 1 game or a colour-alternating pair | descriptor flag |
| `gameServer/tourney/SingleEliminationFormat.java:122` | `// if (GridStateFactory.isTurnbasedGame(tourney.getGame()))` (commented) | dead | none |
| `gameServer/tourney/DoubleEliminationFormat.java:261` | `boolean set = !GridStateFactory.isSingleGameSet(tourney.getGame());` | same | descriptor flag |
| `httpdocs/gameServer/admin/newTourney.jsp:117-121` | `Game games[] = getAllGames(); for (i=1; …) <option value=getId()>getDisplayName(getId())` | admin tournament-creation dropdown, **spans live + TB** | auto-extends when the factory arrays grow; renders "null" for ids absent from `displaygames[]` |
| `kingOfTheHill/web/KotHServlet.java:40-48` | `game = Integer.parseInt(request.getParameter("game"))` | raw servlet param → id | then membership-validated at `:54-62` |
| `kingOfTheHill/web/KotHServlet.java:54-62` | `for (…TB_GAMES…) if (game == TB_GAMES[i]) error = null;` | **KOTH is TB-only via this servlet**, validated by membership | id-agnostic; safe |
| `gameServer/mobile/IndexResponse.java:419-420,447,481` | `addKothEntries(TB_GAMES, …)` then `addKothEntries(LIVE_GAMES, …)`; `> 50` filters and the `"tb-"` prefix | mobile KOTH home screen spans **both** id ranges | see class B |
| `gameServer/server/DSGContextListener.java:138` | `addServerGames(dbHandler, 45, 2, liveGames, KOTH_EVENT)` | server 45 = KOTH, offered for every **live** id | array-driven |
| `game/MySQLGameVenueStorer.java:699-707` | `for (int game : LIVE_GAMES) { LIVE_EVENT; KOTH_EVENT } for (int game : TB_GAMES) { TB_EVENT; KOTH_EVENT }` | **KOTH `game_event` rows exist for live *and* TB ids** — the id span is 1..32 ∪ 51..81 | hand-off, but this is the authoritative statement of the KOTH span |

## Class J — servlet parameters, name↔id round-tripping, and misc

| Location | Snippet | Semantic meaning | Refactor note |
|---|---|---|---|
| `turnBased/web/NewGameServlet.java:64,132` | `String gameStr = request.getParameter("game"); … game = Integer.parseInt(gameStr);` | TB game creation: id arrives as a **decimal string** in an HTTP param | No width limit; JSON/query-string carry arbitrary ints |
| `turnBased/web/NewGameServlet.java:195` | `if (game == -1 \|\| game > getMaxGameId())` | only range check | see class F |
| `turnBased/web/NewGameServlet.java:246-282` | `GridStateFactory.getGameName(game)` in 6 user-facing error strings | id → name for messages | `getGameName` → `getGame(game).getName()` → **NPE/AIOOBE for an unknown id** (section E) |
| `turnBased/web/ReplyInvitationServlet.java` (4 refs) | `GridStateFactory.*` | invitation accept/decline branches | |
| `gameServer/client/web/BoardImageServlet.java:89,104` | `board.setGameById(GridStateFactory.getGameId(game))` | **name → id** from a URL parameter | Throws `IllegalArgumentException("Invalid game: …")` for an unknown name — fail-closed |
| `gameServer/client/awt/PlayerStatsDialog.java:258` | `game = GridStateFactory.getGameId(gameChoice.getSelectedItem());` | name → id from a UI choice | as above |
| `gameServer/client/awt/GameBoardFrame.java:856` | `storeNewMove(gid, 0, GridStateFactory.getCenterMove(game.getGame()))` | applet centre-stone | id-agnostic |
| `gameServer/client/awt/GameBoardFrame.java:372-374` | `Game games[] = getNormalGames(); … gameChoice.add(games[i].getName())` | applet game picker | auto-extends |
| `gameServer/mobile/WhosonlineResponse.java:24`, `WhosonlineAndLiveResponse.java:25`, `LiveServersResponse.java:27` | `d.getPlayerGameData(GridStateFactory.TB_PENTE)` | **TB_PENTE (51) hardcoded as the "representative" rating** shown next to a player's name | Not a cap issue, but a hardcoded id that must survive any renumbering |
| `gameServer/mobile/AiGameResponse.java:29` | `GridStateFactory.getGameName(tbGame.getGame())` | AI game label | NPE path for unknown id |
| `gameServer/core/SimpleDSGPlayerData.java:366,381-400,421,435,530` | `for (int i = 0; i < gameData.size(); i++)` then match on `getGame()` | **player ratings are a `List<DSGPlayerGameData>` searched linearly by game id — NOT an array indexed by id** | Good news: ratings storage in core is already id-agnostic and sparse-safe |
| `turnBased/TBGame.java:706,717,729` | `getGameName(getGame())`, `player.getPlayerGameData(getGame())` | TB game → rating bucket by TB id | id-agnostic given the list-based storage above |
| `tutorial/SimpleTutorialBuilder.java`, `AbstractTutorialStep.java`, `tree/AWTBoard.java:98`, `tree/HibernateNodeSearcher.java:35`, `tree/LocalFileNodeSearcher.java:23-24`, `tools/LoadGame.java`, `gameDatabase/swing/*` (`GameReviewBoard:1531`, `NewDialog:50`, `NewDialog2:19`, `Main:1274`, `ViewGamePanel`, `AiVisualizationPanel`, `AnalysisCreator`, importers), `game/PGNGameFormat.java` | `GridStateFactory.PENTE` / `getNormalGames()` | hardcoded PENTE default or enumeration in offline/desktop tooling | Low risk, but each is a place where "the default game" is a literal |
| `gameDatabase/GameVenueJSFormat.java:68-70` | `// if (t.getID() == CONNECT6 …) continue;` (commented) | dead venue filter | none |
| `gameServer/client/{web,awt}/CoordinatesListPanel.java` (18 refs each) | per-game move-notation branches | duplicated applet code | legacy; only matters if applets stay in scope |
| `turnBased/swing/TBGamePanel.java` (11 refs) | desktop TB client branches | legacy desktop |

---

# (e) UNKNOWN-ID BEHAVIOUR — what happens when an id this layer doesn't know arrives

**Verdict: split. Rules dispatch degrades gracefully; identity lookup crashes or lies.**

Java core is the *authority* on ids, so "unknown" means an id arriving from a client
event, a servlet parameter, or a DB row that `GridStateFactory` has no entry for. This is
exactly the situation during a staged rollout where a newer server (or a newer DB row)
carries an id an older code path has not been taught.

### Graceful — new games can roll out silently through these paths

| Path | Exact code | Behaviour |
|---|---|---|
| Rules-family OR-chains | `ServerTable.java:1883, 1930, 1961, 2054, 2778, 3650`; `TBGame.java:196, 293, 626`; `MobileJsonHelper.java:62` | Falls through to the default arm: alternating seats, 19x19 board, K=64, 2-game set, forced centre stone. No exception |
| `createGridState(int game[, x, y])` | `GridStateFactory.java:262-381`, `default` → `return null` at `:381` | Returns `null` rather than throwing. Caller NPEs later, but the factory itself is safe |
| `getDisplayName(game)` | `GridStateFactory.java:434-441` — linear scan of `displaygames[]`, `return null` | Returns `null`. JSPs (`leaderboard.jsp:28`, `newTourney.jsp:120`, `followersing.jsp:70`, `broadcast.jsp:76`) render the literal string `"null"`. Ugly, not fatal |
| Enumeration APIs | `getDisplayGames/getNormalGames/getSpeedGames/getTbGames/getAllGames` | An id absent from the arrays is simply **not listed**. Every UI list, every server offering, every ratings section silently omits it. **This is the property that makes silent rollout possible** |
| MMAI bot allow-list | `MMAIPlayer.java:70-77` `SUPPORTED_GAMES.contains(game)` | Unknown id → bot refuses the table up front (documented: *"would be silently remapped to the plain Pente engine, so it is rejected"*) |
| Boot registration | `DSGContextListener.java:119-141` wrapped in `catch (Throwable t) { log4j.error(…) }` | A bad id during `registerAllGames`/`addServerGames` logs and continues. Boot never bricks |
| KOTH join/leave | `KotHServlet.java:54-62` membership scan over `TB_GAMES` | Unknown id → `"Invalid game."` Fail-closed |
| TB game creation | `NewGameServlet.java:195` `game > getMaxGameId()` | An id above 81 → rejected with an error. Fail-closed **above** the max; ids 33..50 slip through |
| Name→id | `getGameId(String)` `GridStateFactory.java:408-420` → `throw new IllegalArgumentException("Invalid game: " + gameName)` | Fail-closed, loud |

### Fatal or silently wrong — these must be fixed before any id beyond 32/81 exists

| Path | Exact code | Behaviour |
|---|---|---|
| **`getGame(id)`** | `GridStateFactory.java:392-398` | `id` in **33..48** → `allGames[id]` returns a **turn-based `Game` object with a different id** (e.g. `getGame(33)` → `TB_PENTE_GAME`, id 51). Silently wrong, no exception. `id` = 0 → `null`. `id` = 49 or 50 → **`ArrayIndexOutOfBoundsException`** (`allGames.length == 49`). `id` > 50 and even (e.g. 52) → `tbGames[(52-51)/2]` = `tbGames[0]` = wrong game. `id` > 81 → **AIOOBE** (`tbGames.length == 16`) |
| **`getGameName(id)`** | `GridStateFactory.java:404-406` → `getGame(game).getName()` | Inherits all of the above **plus NPE** when `getGame` returns `null`. Reached from user-facing strings: `NewGameServlet.java:246-282` (6 sites), `TBGame.java:706`, `AiGameResponse.java:29`, `MoveServlet.java:747` (push-notification text) |
| **`createGridState(game, MoveData)`** | `GridStateFactory.java:384-390` | `gridStates[33..48]` are **permanently `null`** (the boot loop at `:240-243` calls `createGridState(i)` for i=1..48; ids 33..48 hit no `case` and return `null`) → **NPE**. `game` = 49/50 → AIOOBE. `game` > 81 → AIOOBE on `tbGridStates` |
| **`getColor(moveNum, game)`** | `GridStateFactory.java:479-481` `gridStates[game]` | **AIOOBE for every turn-based id** (51..81 vs array length 49) and NPE for 33..48. Currently only ever called with live ids — a latent trap |
| **`getSpeedGame` / `getNormalGame`** | `GridStateFactory.java:459-465` `allGames[id ± 1]` | For `id` = 32 → `allGames[33]` = `TB_PENTE_GAME`. Already returns nonsense at the edge of the live range |
| **Live table game change** | `ServerTable.java:932` `Game newGame = GridStateFactory.getGame(changeStateEvent.getGame());` | **A client-supplied id goes straight into `getGame` with no validation.** An unknown/out-of-range id throws AIOOBE/NPE inside the table thread. Highest-severity single line in the subsystem |
| **Stats dialog id/index confusion** | `PlayerStatsDialog.java:315-324` `for (i=1..48) getPlayerGameData(i, …)` then `games[i].getName()` | Queries player stats for the phantom ids 33..48 while labelling them with the names of games 51..81. Live latent bug today |

### One-line answer

Rules dispatch and every user-facing list are **fail-soft** (unknown game gets default
rules and is simply omitted from menus), so a new game can be introduced server-side
without breaking older code paths; but the identity primitives `getGame` /
`getGameName` / `createGridState(game,MoveData)` / `getColor` are **array-indexed by id
with two different addressing schemes** and will either throw `ArrayIndexOutOfBounds` /
`NullPointerException` or — worse, in the phantom band **33..48** and for even ids above
50 — **silently return the wrong game**. `ServerTable.java:932` feeds an unvalidated
client id into `getGame`, so this is reachable from the network.

---

# Cap-relevant width limits

**No type-width limit exists inside Java core.** The cap is entirely semantic.

| Constraint | Where | Value | Notes |
|---|---|---|---|
| Game id declared type | `Game.java:5` `private int id`; `Tourney.java:20`, `TBGame.java:42`, `SimpleGameEventData.java:40`, `MySQLGameVenueStorer.java:108`, `ServerTable.java:3475` — all `int` | 32-bit signed | Verified by sweep: **no `byte`/`short` narrowing and no bit-packing of a game id anywhere outside `org/pente/gameServer/event`** (the codec package, excluded here) |
| Servlet parameter | `NewGameServlet.java:64,132` / `KotHServlet.java:40-44` — `Integer.parseInt(request.getParameter("game"))` | unbounded decimal string | HTTP/JSON impose no width limit |
| **Live/TB partition** | `GridStateFactory.java:61` `TB_START = 50` (private) + literal `50` at `Tourney.java:123`, `KothResponse.java:61,83`, `IndexResponse.java:447,481`, `CacheKOTHStorer.java:96,177,213`, `MySQLGameVenueStorer.java:466`, `CacheTourneyStorer.java:815`, `broadcast.jsp:75` | **≤ 24 live ids (1..49), i.e. 24 families** | The headline cap |
| **`allGames[]` dual addressing** | `GridStateFactory.java:139-154` | **49 slots; ids 1..32 addressable, 33..48 stolen for TB objects** | Bites *before* the 50 boundary: family 17 at `N=33` collides immediately. Effective cap today is **16 families**, not 24 |
| `gridStates[]` prototype cache | `GridStateFactory.java:237` `new GridState[getNumGames() + 1]` = 49 | 49 | Sized off `getNumGames()`, which means "array length", not "max id" |
| `tbGridStates[]` / `tbGames[]` positional | `GridStateFactory.java:238`, addressed `(game - 51) / 2` | 16 | Requires TB ids to be exactly 51, 53, 55 … with no gaps |
| Speed-twin parity | `GridStateFactory.java:471-473` `game % 2 == 0` | — | Forces ids to be allocated as odd/even pairs; blocks any sparse or non-contiguous allocation |
| `isValidGame` range | `GridStateFactory.java:400-402` `1..SPEED_RENJU (32)` | 32 | Must be bumped per game today |
| `getMaxGameId` | `GridStateFactory.java:426-428` `TB_RENJU (81)` | 81 | Used as the only range check in `NewGameServlet.java:195` |

**Implication for the refactor:** because every id is already an `int` end-to-end in
Java core, the cap can be lifted **without any width change in this layer** — it is
purely a matter of replacing arithmetic/positional addressing with a keyed registry.
Width risk, if any, lives in the wire codec and the DB columns, which are owned by the
sibling agents.

---

# Hand-off points to the excluded layers

| Boundary | Java-core side | What the other agent owns |
|---|---|---|
| Wire codec | `ServerTable.java:932` `changeStateEvent.getGame()` — the client-supplied id enters core here, unvalidated | `org/pente/gameServer/event/DSGEventWrapper` + reflection codec: how `game` is encoded on the raw-TCP/WebSocket frame, and whether it is narrowed |
| `game_event` registration | `DSGContextListener.java:118` `gameVenueStorer.registerAllGames(2)`; interface at `GameVenueStorer.java:132` | `MySQLGameVenueStorer.java:699-707` (array-driven row generation) and `ensureGameEvents`; `game_event.eid` fan-out to 13 tables |
| Venue tree lookup | — | `MySQLGameVenueStorer.java:462-475` `findGameTreeData` — `game > 50 ? game - 50 : game`, plus the documented regression from the old `tree.get(game - 1 - 50)` positional form |
| Server offerings | `DSGContextListener.java:125-138` builds the id arrays | `MySQLServerStorer.addServerGames(...)` writes them |
| Ratings persistence | `FastMySQLDSGGameLookup.java:55-60,104-105` decomposes a TB id into (base id, tb flag) before querying | `MySQLDSGPlayerStorer` column types for `game` |
| KOTH storage | `KotHServlet.java:54-68`, `IndexResponse.java:419-420` | `CacheKOTHStorer.java:96,150,164,170,177,213,294,330` (`ALL_GAMES` / `LIVE_GAMES` / `TB_GAMES` iteration + `> 50`) |
| Tournament storage | `Tourney.java:91,123`, `SingleEliminationFormat.java:111`, `DoubleEliminationFormat.java:261` | `CacheTourneyStorer.java:492,551` (`isSingleGameSet`) and `:815` (`getDisplayName(game - 50)`) |
| TB game storage | `TBGame.java` accessors | `CacheTBStorer.java:1857,1895,2200`; `MySQLTBGameStorer.java:1500` (`RenjuOpeningState.encodeOffers`) |
| Live game storage | — | `MySQLPenteGameStorer.java:479,698,1006`; the `gid >= 4e13` TB encoding |
| JSP / JS clients | The five enumeration APIs (`getAllGames/getDisplayGames/getNormalGames/getSpeedGames/getTbGames`) are the entire contract the JSP layer consumes | `gameConstants.jspf`, `boardCommon.js`, React `boardGeometry.js` |

---

# Touchpoint count

**131 distinct touchpoints** catalogued across classes A–J (excluding the four dead /
commented-out sites and the two `MarksAIPlayer` false positives, which are listed and
marked as such).

Highest-leverage single fixes, in dependency order:

1. Make `TB_START` public and route the ~10 literal `50`s through it *(pure refactor, zero behaviour change)*.
2. Split `tbGames[]`'s two roles: display list vs. arithmetic lookup table.
3. Replace `getGame` / `createGridState(game,MoveData)` / `getColor` array indexing with a
   `Map<Integer, …>` keyed by real id — this alone removes the phantom 33..48 band and
   every AIOOBE in the unknown-id table above.
4. Add `variant` + `baseFamilyId` to `Game`, then reimplement `isSpeedGame` /
   `isTurnbasedGame` / `getSpeedGame` / `getNormalGame` / `getNormalGameFromTurnbased`
   on top of it, leaving all ~60 call sites untouched.
5. Only then is the id space free, and new families can be minted anywhere.
