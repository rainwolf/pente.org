# Game-ID Assumption Inventory — Persistence + Wire Protocol

Subsystem: `dsg_src/java` storers (MySQL*/Cache*), the DSGEventWrapper wire codec
(TCP + WebSocket), and the live MariaDB schema.

Recon only. No repo file modified; DB touched with SELECT/SHOW only.

Source root: `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java`
(`pente.org/deploy/` is a gitignored build copy — ignored throughout; line numbers below
are all from `dsg_src/java`).

---

## 0. Headline findings (read this first)

1. **The wire protocol imposes NO width limit.** Both the raw-TCP mobile path and the
   WebSocket path serialise events as **Gson JSON**, delimited on TCP by a single
   `0xFF` byte. Game ids travel as JSON numbers backed by Java `int`. There is no
   byte/short field, no bit-packing, no fixed-width binary frame anywhere in
   `org/pente/gameServer/event/`. **`grep -rn 'writeByte|writeShort|writeInt|readByte|readShort|readInt' org/pente/gameServer/event/` returns zero hits.**

2. **The DB *does* impose a width limit, and it is inconsistent.** Five columns are
   `TINYINT(3) UNSIGNED` (max 255) while the turn-based and personal-collection tables
   are `SMALLINT` (max 65535 / 32767). See §F. TINYINT is not the *current* binding
   constraint (the cap is 24 games because of `TB_START=50`), but it becomes one at
   >255 and it silently truncates/rejects rather than erroring loudly on MyISAM in
   non-strict mode.

3. **The real cap mechanism is `GridStateFactory.allGames[]`, not just `TB_START`.**
   `allGames` is a 49-element array positionally indexed by id for ids ≤ 50, but
   indices 33..48 are already occupied by the *turn-based* Game objects. Adding a 25th
   live pair (ids 33, 34) would make `getGame(33)` return `TB_PENTE_GAME`. This is a
   **silent wrong-answer**, not a crash. See §B and §E.

4. **The turn-based `gid` threshold is `50000000000000` (5e13), not 4e13.** Confirmed in
   code (7 call sites) and in the live DB (`tb_game` AUTO_INCREMENT = `50000000731286`;
   max non-TB `pente_game.gid` = `34194140053447`).

5. **Unknown-id behaviour is *crash or silently-wrong*, never graceful-skip**, on the
   server side. The client-supplied game id in `DSGChangeStateTableEvent` reaches
   `GridStateFactory.getGame()` with **no validation** (`ServerTable.java:976`). See §E.

---

## A. Live DB recon — exact column types

Query used (read-only):

```sql
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='dsg' AND (COLUMN_NAME LIKE '%game%' OR COLUMN_NAME IN ('eid','event_id','gid'));
```

| Table | Column | Type | Key | Range | Note |
|---|---|---|---|---|---|
| `game_event` | `game` | **`tinyint(3) unsigned`** | — | 0–255 | **WIDTH RISK** |
| `game_event` | `eid` | `int(10) unsigned` | PRI | 0–4.29e9 | auto_inc 1814 |
| `pente_game` | `game` | **`tinyint(3) unsigned`** | MUL | 0–255 | **WIDTH RISK** |
| `pente_game` | `gid` | `bigint(20) unsigned` | PRI | — | 5e13 = TB marker |
| `pente_game` | `event_id` | `int(10) unsigned` | — | — | → `game_event.eid` |
| `pente_move` | `game` | **`tinyint(3) unsigned`** | — | 0–255 | **WIDTH RISK** |
| `pente_move` | `gid` | `bigint(20) unsigned` | PRI | — | |
| `dsg_player_game` | `game` | **`tinyint(3) unsigned`** | **PRI** | 0–255 | **WIDTH RISK** — ratings PK |
| `dsg_server_game` | `game` | **`tinyint(3) unsigned`** | **PRI** | 0–255 | **WIDTH RISK** — offerings PK |
| `dsg_server_game` | `event_id` | `int(10) unsigned` | PRI | — | |
| `tb_game` | `game` | `smallint(5) unsigned` | — | 0–65535 | OK |
| `tb_game` | `gid` | `bigint(20) unsigned` | PRI | — | AUTO_INCREMENT=**50000000731286** |
| `tb_game` | `event_id` | `int(10) unsigned` | — | — | |
| `tb_game_ai` | `game` | `smallint(5) unsigned` | — | 0–65535 | OK |
| `webdb_game` | `game` | `smallint(6)` (signed) | — | −32768–32767 | OK |
| `webdb_analysis` | `game` | `smallint(6)` (signed) | — | | OK |
| `webdb_move` | `game` | `smallint(6)` (signed) | — | | OK |
| `koth` | `koth_id` | `int(8) unsigned` | PRI | — | **is an `eid`, not a game id** |
| `dsg_tournament*` | `event_id` | `int(10)/int(11)` | PRI/— | — | tourney keys on `eid` |
| `dsg_tournament_match` | `gid` | `bigint(20) unsigned` | — | — | |

Tables with **no** game column (checked, for completeness):
`dsg_live_set` (only `g1_gid`/`g2_gid` bigint), `dsg_koth` (pid/date/streak),
`speed_mapping` (pid→pid, unrelated to game ids), `tb_move`, `tb_move_ai`,
`tb_message`, `pente_renju_offer` (all keyed only on `gid`).

### Distinct game ids actually present (live DB)

| Table | Ids present |
|---|---|
| `game_event` | 1–32 (all, dense) + 51,53,…,81 (odd only, 16 TB ids) |
| `pente_game` | 1–31 (all), **plus 2 anomalous rows with game=77 and game=79** (gid 77 & 79) |
| `tb_game` | 51,53,…,81 (16 ids) **+ 4 rows with game=1** (legacy corruption) |
| `tb_game_ai` | 51, 55 |
| `dsg_server_game` | 1–32 + 51,53,…,65 (only 8 TB ids offered) |
| `dsg_player_game` | 1–19,21,26–30 + 51,53,…,81 |
| `webdb_game` | (empty — feature not yet populated) |
| `koth.koth_id` | 1243,1244,1245,1249,1250,1375,1376,1382,1601 → all resolve to `game_event` rows named "King of the Hill" with game = 51,53,55,63,65,69,71,73,77 |

**Confirmed TB gid convention:** `tb_game.gid` ranges `50000000000000` … `50000000731285`.
Archived TB games in `pente_game` carry the **base (odd, live) game id** with
`gid >= 50000000000000`; e.g. `pente_game.game=1` spans `gid` 41000000000005 (BrainKing
import) → 50000000731257. Max `pente_game.gid` below the threshold is `34194140053447`.
The BrainKing importer's `BASE_GID = 41000000000000L`
(`org/pente/tools/BrainkingImporter.java:72`) sits *below* 5e13, so imports classify as
live — correct today, but it is only 1.2e13 of headroom from the TB boundary.

**Data anomaly worth flagging:** `pente_game` rows `(gid=77, game=77)` and
`(gid=79, game=79)` store a *turn-based* id in a column whose semantic is base-id.
Any code that does `GridStateFactory.getGame(77)` on those rows gets `TB_SWAP2PENTE_GAME`
(index 47 of `allGames`) rather than crashing — an existing, latent silent-wrong-answer.

---

## B. The id source of truth — `org/pente/game/GridStateFactory.java`

Every storer and every wire handler funnels through this class, so its assumptions are
transitively the persistence layer's assumptions.

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `GridStateFactory.java:7-38` | `PENTE=1; SPEED_PENTE=PENTE+1; … RENJU=31; SPEED_RENJU=RENJU+1` | **speed = base+1 pairing**, hardcoded per game; base ids are odd, speed ids even | Replace with a registry keyed by (family, speed?) so speed is a flag not an arithmetic offset |
| `GridStateFactory.java:40-57` | `public static final int[] LIVE_GAMES = {PENTE, SPEED_PENTE, …}` | Dense live-id enumeration; **already the authoritative list** consumed by boot-time registration | Keep as the single source; make it derived from a registry |
| `GridStateFactory.java:61` | `private static final int TB_START = 50;` | **The cap.** TB ids start at 51 → live ids must be < 50 → 24 live pairs max | Root cause #1. Replace TB-ness with an explicit dimension, not an id offset |
| `GridStateFactory.java:62-77` | `TB_PENTE = TB_START + PENTE;` ×16 | **+50 TB offset**, hardcoded per game | |
| `GridStateFactory.java:79-83` | `int[] TB_GAMES = {TB_PENTE, …}` | TB enumeration | |
| `GridStateFactory.java:85` | `int[] ALL_GAMES = ArrayUtils.addAll(TB_GAMES, LIVE_GAMES)` | Union used by `CacheKOTHStorer` | Safe pattern — keep |
| `GridStateFactory.java:139-154` | `Game allGames[] = {null, PENTE_GAME, …, SPEED_RENJU_GAME, TB_PENTE_GAME, …, TB_RENJU_GAME}` | **49-element array positionally indexed by id for ids ≤ 50.** Indices 1..32 = live; **indices 33..48 = TB Game objects appended, unreachable by their own ids** | Root cause #2. `allGames[33]` is `TB_PENTE_GAME` — the slot a 25th live game would need. Replace with `Map<Integer, Game>` |
| `GridStateFactory.java:237` | `GridState gridStates[] = new GridState[getNumGames() + 1]` | Array of 49 GridStates positionally indexed by id | `gridStates[51..81]` is out of bounds — see `getColor` below |
| `GridStateFactory.java:238` | `GridState tbGridStates[] = new GridState[tbGames.length]` | 16 TB states, indexed by **TB ordinal**, not id | |
| `GridStateFactory.java:240-246` | `for (int i = 1; i < gridStates.length; i++) gridStates[i] = createGridState(i);` | **Dense 1..48 iteration.** For i=33..48 the factory switch has no case → stores `null` | Harmless today; becomes wrong when ids 33+ become live |
| `GridStateFactory.java:~260-395` | `case PENTE: case SPEED_PENTE: case TB_PENTE:` … giant switch | Per-id switch, 3 ids per family | Every new game must be added here or `createGridState` returns `null` |
| `GridStateFactory.java:396-402` | `getGame(int game) { if (game > TB_START) return tbGames[(game - TB_START - 1) / 2]; else return allGames[game]; }` | **Positional index by id + `(id-51)/2` TB-ordinal arithmetic.** Depends on live ids being dense odd/even pairs *and* TB ids being dense odd | The single hottest touchpoint. Unbounded — see §E |
| `GridStateFactory.java:400` | `isValidGame(game) { return game >= PENTE && game <= SPEED_RENJU; }` | Validity = `1..32`. **Returns `false` for every turn-based id** | Misleading name; only used by AWT client + tests. Any new validation must handle TB |
| `GridStateFactory.java:408-420` | `getGameId(String)` loops `allGames[1..48]` then `tbGames` | Name→id reverse lookup over the positional array | |
| `GridStateFactory.java:422-424` | `getNumGames() { return allGames.length - 1; }` = **48** | Not "number of games"; it is the allGames array bound | Rename/replace — it is used to size `gridStates` |
| `GridStateFactory.java:426-428` | `getMaxGameId() { return TB_RENJU; }` = **81** | Upper validation bound; used by `NewGameServlet.java:195` | Hardcoded to the last TB constant — must be recomputed from the registry |
| `GridStateFactory.java:459-461` | `getSpeedGame(g) { return allGames[normalGame.getId() + 1]; }` | **+1 speed pairing via array index** | |
| `GridStateFactory.java:463-465` | `getNormalGame(g) { return allGames[speedGame.getId() - 1]; }` | **−1 speed pairing via array index** | |
| `GridStateFactory.java:467-469` | `getNormalGameFromTurnbased(game) { return game - TB_START; }` | **−50 TB→base**; used by `FastMySQLDSGGameLookup` | |
| `GridStateFactory.java:471-473` | `isSpeedGame(game) { return game < TB_START && (game % 2) == 0; }` | **Parity test + comparison vs 50.** Speed-ness is encoded in id parity | Root cause #3 |
| `GridStateFactory.java:475-477` | `isTurnbasedGame(game) { return game > TB_START; }` | **Comparison vs 50** | |
| `GridStateFactory.java:479-481` | `getColor(moveNum, game) { return gridStates[game].getColor(moveNum); }` | **Direct positional index, no bound check.** `game >= 49` → `ArrayIndexOutOfBoundsException`; a TB id always throws | Landmine: callers must pre-convert TB→base |
| `GridStateFactory.java:502-507` | `isSingleGameSet(game)` — 12-way `==` against GO/RENJU ids | Per-id behavioural predicate | Move to a `Game` property |
| `GridStateFactory.java:522-53x` | `firstMoveCanBeOffCenter(game)` — ~15-way `==` | Per-id behavioural predicate; **the personal-collection and archive load paths synthesise `moves[0]` from `getCenterMove(game)` and silently corrupt games where this returns true** | Move to a `Game` property |
| `GridStateFactory.java:~486-494` | `getCenterMove(int game)` derives from `createGridState(game)` grid size | Unknown id → `createGridState` returns `null` → **NPE** | |

---

## C. Persistence touchpoints

### C1. `game_event` / `eid` resolution — the indirection every subsystem shares

`game_event` is the join table: `(eid PK, name, site_id, game TINYINT)`. Nothing stores a
game id directly in the tournament/KOTH tables — they store an `eid`, and the `eid` is
resolved by `(game, site_id=2, name)` lookup. The three magic `name` strings are the
variant discriminator.

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `game/MySQLGameVenueStorer.java:34-35` | `GAME_SITE_TABLE="game_site"; GAME_EVENT_TABLE="game_event"` | table names | |
| `game/MySQLGameVenueStorer.java:~39-41` | `LIVE_EVENT="Live Game"; TB_EVENT="Turn-based Game"; KOTH_EVENT="King of Hill"` | **The 3 event-name discriminators** written into `game_event.name` | Note the constant is `"King of Hill"` but live DB rows read **"King of the Hill"** and `MySQLKOTHStorer` queries `"King Hill"` — see C4, this is already inconsistent |
| `game/MySQLGameVenueStorer.java:~246-268` | `registerGame(int baseGame, int siteId)`: `int speedGame = baseGame + 1; int tbGame = baseGame + 50;` then inserts **6 rows** `{base,LIVE},{speed,LIVE},{tb,TB},{base,KOTH},{speed,KOTH},{tb,KOTH}` | **+1 and +50 arithmetic duplicated outside GridStateFactory** | Highest-value early fix: derive both from the registry |
| `game/MySQLGameVenueStorer.java:~270-290` | `registerAllGames(int siteId)` iterates `GridStateFactory.LIVE_GAMES` (→ LIVE + KOTH rows) and `GridStateFactory.TB_GAMES` (→ TB + KOTH rows) | **Already registry-driven** — adding ids to the arrays auto-creates `game_event` rows at boot | Good pattern; the safe extension point |
| `gameServer/server/DSGContextListener.java:117-119` | `gameVenueStorer.registerAllGames(2);` inside a try/catch that swallows errors | Boot-time idempotent registration, best-effort | Registration failure degrades silently → a new game would appear "registered" in code but unresolvable in DB |
| `game/MySQLGameVenueStorer.java:138-140` | `select distinct game, site_id, event_id, round, section from pente_game order by game, …` | Venue-tree seed from stored games | |
| `game/MySQLGameVenueStorer.java:171-172` | `select eid, name, game, site_id from game_event order by eid` | Venue-tree seed from registered events | |
| `game/MySQLGameVenueStorer.java:190-232` | Seeds tree nodes from `LIVE_EVENT` rows so live games resolve before any game is stored; dedupes legacy duplicate eids per `(game, site)` keeping lowest eid | Replaces the old `add_dummy_*.sql` hack | |
| `game/MySQLGameVenueStorer.java:299-300` | `if (d1.game != d2.game) return d1.game - d2.game;` | Tree sort by numeric game id | Ordering becomes arbitrary once ids are no longer family-ordered |
| `game/MySQLGameVenueStorer.java:339-345` | `… and game_event.game = pente_game.game order by game_event.game, …` | Venue tree join keyed on game id | |
| `game/MySQLGameVenueStorer.java:441-455` | `findGameTreeData(int game)`: `int baseGame = game > 50 ? game - 50 : game;` then **linear scan** `for i<tree.size() if tree.get(i).getID()==baseGame` | **−50 TB→base normalisation.** Previously `tree.get(game - 1 - 50)` positional — already fixed to id-based scan (see regression test) | The `>50` remains; convert to `isTurnbased()` on the registry |
| `game/test/MySQLGameVenueStorerTbLookupTest.java:13-15,68` | Comment: "Turn-based ids are base + 50 … old positional lookup translated turn-based ids with `tree.get(game - 1 - 50)`" | **Regression test guarding exactly this class of bug** | Extend it when the offset changes |
| `game/MySQLGameVenueStorer.java:180-218` | `addGameEventData(int game, …)`: `insert into game_event (name, site_id, game) values(?,?,?)` then re-select by `(name, site_id, game)` | Writes into the TINYINT column | |
| `game/GameVenueStorer.java` (iface), `game/SimpleGameEventData.java`, `game/GameEventData.java` | `getGame()/setGame(int)` | `eid ↔ game` carrier | |

### C2. `pente_game` archive + the `gid >= 5e13` TB encoding

Archived completed games (both live and turn-based) land in `pente_game`. TB-ness is
**not** stored in the `game` column — it is encoded in the `gid` magnitude. `pente_game.game`
holds the **base (live) id** even for turn-based games.

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `game/MySQLPenteGameStorer.java:295-299` | `// gids above 50000000000000 reserved for turn-based games` / `select max(gid)+1 from pente_game where site_id=? and gid < 50000000000000` | **gid allocation for live games is capped below 5e13** | Magic number, 1 of 7 |
| `game/MySQLPenteGameStorer.java:698` | `int game = gameResult.getInt(15);` | Reads `game` column into `int` (widening from TINYINT) | Java side is already `int` — only the column is narrow |
| `game/MySQLPenteGameStorer.java:764,1066` | `gameData.setGame(GridStateFactory.getGameName(game))` | **id → display name via `getGame(game).getName()`** → NPE/AIOOBE on unknown id | See §E |
| `game/MySQLPenteGameStorer.java:767-769` | `if (game == GO \|\| game == SPEED_GO \|\| game == GO9 \|\| … GO13 …)` | Per-id special case in the **load** path (go games skip synthesized center move) | Should be `firstMoveCanBeOffCenter()` |
| `game/MySQLPenteGameStorer.java:785,1071` | `gameData.addMove(GridStateFactory.getCenterMove(game))` | **Synthesises `moves[0]` from the game id.** Wrong for any variant where `firstMoveCanBeOffCenter()` is true | Pre-existing correctness hazard; a new variant added without touching this silently corrupts loads |
| `gameServer/core/FastMySQLDSGGameLookup.java:55-56,104-105,157-158` | `int actualGame = GridStateFactory.isTurnbasedGame(game) ? GridStateFactory.getNormalGameFromTurnbased(game) : game;` (×3) | **TB id −50 → base id before querying `pente_game.game`** | 3 copies of the same normalisation |
| `gameServer/core/FastMySQLDSGGameLookup.java:57-58` | `getGameEventData(actualGame, "Turn-based Game", "Pente.org")` | eid resolution by base id + name | |
| `gameServer/core/FastMySQLDSGGameLookup.java:66,72,116,123,170,178,233,245` | `"and gid " + (tb ? ">=" : "<") + " 50000000000000 "` (8 occurrences) | **String-concatenated TB discriminator in SQL** | Magic number, 8 more sites |
| `gameDatabase/MySQLGameStorerSearcher.java:392` | `and (g.site_id = 2 and g.gid < 50000000000000)` | "only live" search filter | |
| `gameDatabase/MySQLGameStorerSearcher.java:399` | `and ((g.site_id = 2 and g.gid >= 50000000000000) or g.site_id != 2)` | "only turn-based" search filter | |
| `gameDatabase/MySQLGameStorerSearcher.java:400` | `… GridStateFactory.TB_RENJU` | Upper bound in a game-id range filter | Hardcoded to the current last TB id |
| `tools/RatingsGrapher.java:132-133,142-143` | `and gid > 50000000000000` / `and gid < 50000000000000` | TB/live split in the ratings grapher | |
| `tools/RatingsDrawFixer.java:39` | `and g.gid < 50000000000000` | | |
| `tools/BrainkingImporter.java:72` | `private static final long BASE_GID = 41000000000000L;` | Import gid base, **1.2e13 below the TB boundary** | Only headroom left in the live gid space |
| `game/MySQLGameStorer.java`, `game/AbstractHttpStorer.java`, `game/HttpGameStorer.java` | `GameData.getGame()` is a **String name**, not an int | The archive DTO round-trips the *display name*, resolved back via `GridStateFactory.getGameId(String)` | Name collisions matter: live and TB `Game` objects share names ("Renju"), so `getGameId("Renju")` returns the **live** id (allGames scanned first) |

### C3. Turn-based storage — `tb_game` / `tb_game_ai`

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `turnBased/MySQLTBGameStorer.java:25-69` | `getEventId(int game)`: `select eid from game_event where game=? and site_id=? and name=?` with `site_id=2`, `name="Turn-based Game"`; **returns `-1` if no row** | eid resolution for TB games | **Unknown-id path: returns −1, no exception.** See §E |
| `turnBased/MySQLTBGameStorer.java:146-148` | `String tbTable=" tb_game "; if (p1==23000000020606L \|\| p2==…) tbTable=" tb_game_ai ";` | AI games routed to a parallel table by hardcoded pid | Unrelated to game ids, but doubles every schema change |
| `turnBased/MySQLTBGameStorer.java:153-167` | `insert into <tbTable> (state,…, game, event_id, …)` / `stmt.setInt(5, game.getGame())` | **Writes the TB id (51..81) into `tb_game.game` SMALLINT** — unlike `pente_game`, which stores the base id | Two different conventions for the same concept across two tables |
| `turnBased/CacheTBStorer.java:1366-1372` | `getEventId(int game) { int eid = baseStorer.getEventId(game); … }` | Cached eid resolution | |
| `turnBased/CacheTBStorer.java:1466-1467` | `if (game.getEventId()==0) game.setEventId(getEventId(game.getGame()));` | Lazy eid backfill | If `getEventId` returned −1 the game is stored with `event_id=-1` |
| `turnBased/CacheTBStorer.java:2261,2274` | `tbg1.setEventId(getEventId(game)); tbg2.setEventId(getEventId(game));` | New-set creation resolves eid twice | |
| `turnBased/CacheTBStorer.java:618,968-969,1244,1250` | `if (getEventId(t.getGame()) != t.getEventId())` / `if (game.getEventId() != kothStorer.getEventId(game.getGame()))` | **eid comparison is how the code decides "is this a plain game, a KOTH game, or a tournament game"** | A new game whose KOTH `game_event` row is missing makes `kothStorer.getEventId()` return −1, so a KOTH game is misclassified as a tournament game |
| `turnBased/CacheTBStorer.java:1665-1697` | `GridState state = GridStateFactory.createGridState(game.getGame(), …)`; then `if (game.getGame()==TB_PENTE \|\| TB_KERYO \|\| TB_BOAT_PENTE \|\| TB_POOF_PENTE \|\| TB_OPENTE)` (twice) and `if (game.getGame()==TB_RENJU)` | **Per-TB-id behavioural switches** in the state-reconstruction path | New TB variants fall through to the default branch — wrong state, no error |
| `turnBased/CacheTBStorer.java:1678` | `GridStateFactory.getGameName(game.getGame())` | id→name in a log/message path | |
| `turnBased/CacheTBStorer.java:2169` | `if (GridStateFactory.isTurnbasedGame(t.getGame()))` | **>50 test** | |
| `turnBased/TBGame.java` (22 hits) | `getGame()/setGame(int)` + per-id predicates | TB DTO carries the **TB id** (51..81) | |
| `turnBased/web/NewGameServlet.java:195` | `if (game == -1 \|\| game > GridStateFactory.getMaxGameId()) { … }` | **The only `getMaxGameId()` validation in the whole server.** Bounds a user-supplied id to ≤ 81 | Note: it does **not** reject 33..50, which are unassigned — see §E |

### C4. King of the Hill — keyed on `eid`, spans live + speed + TB ids

`koth.koth_id` is a `game_event.eid`, resolved from a game id by name lookup. Live DB shows
hills for ids 51,53,55,63,65,69,71,73,77 — i.e. turn-based only in practice, though the
registration code creates KOTH events for live and speed ids too.

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `kingOfTheHill/MySQLKOTHStorer.java:25-51` | `getEventId(int game)`: `select eid from game_event where game=? and site_id=? and name=?` with `site_id=2`, **`name="King Hill"`** | eid resolution. **Returns `-1` when not found** | The commented-out block at :46-50 shows the old `if (game > 50)` two-name scheme. The live DB stores `"King of the Hill"` while this queries `"King Hill"` and `MySQLGameVenueStorer.KOTH_EVENT` is `"King of Hill"` — **three different strings**, a live inconsistency the refactor must reconcile |
| `kingOfTheHill/MySQLKOTHStorer.java:150` | `select koth_id, pid, step, last_game from koth order by koth_id, step asc` | Loads all hills keyed by eid | |
| `kingOfTheHill/MySQLKOTHStorer.java:191-218` | `loadHill(int hill_id)` — hill_id is an eid | | |
| `kingOfTheHill/MySQLKOTHStorer.java:239-248` | `adjustCrown(int game, long pid)`: `update dsg_player_game set tourney_winner='0' … and game = ?` | **Crown state lives in the ratings table, keyed by the raw game id (TINYINT)** | |
| `kingOfTheHill/MySQLKOTHStorer.java:281-292` | `getCrownPid(int game)`: `select pid from dsg_player_game where tourney_winner='4' and computer='N' and game=?` | | |
| `kingOfTheHill/CacheKOTHStorer.java:29` | `private Map<Integer,Integer> eidMap` | **game id → eid memo map** — the right shape already | |
| `kingOfTheHill/CacheKOTHStorer.java:96` | `if (hill == null && game > 50) { hill = new Hill(); … }` | **>50 test**: auto-create empty hills for TB games only | |
| `kingOfTheHill/CacheKOTHStorer.java:110-118,146-149,201-204,225-228` | `int hill_id = getEventId(game); if (hill_id == 0) return;` (4+ sites) | **Guards on `== 0`, but `MySQLKOTHStorer.getEventId` returns `-1` on miss** | **Latent bug**: an unregistered game yields `hill_id = -1`, passes the `== 0` guard, and writes/reads hill rows under `koth_id = -1`. Fix the sentinel before adding games |
| `kingOfTheHill/CacheKOTHStorer.java:153` | `for (int gameId : GridStateFactory.ALL_GAMES)` | Registry-driven iteration over live+TB | Good pattern |
| `kingOfTheHill/CacheKOTHStorer.java:177,213,216` | `if (game > 50) { fixTBinvitations(game, pid); }` | **>50 test** gating TB-only side effects | |
| `kingOfTheHill/web/KotHServlet.java` (2 hits) | game id from request → storer | User-supplied id reaches `getEventId` | |

### C5. Ratings — `dsg_player_game` (game id is part of the PRIMARY KEY)

`PRIMARY KEY (pid, game, computer)` with `game TINYINT UNSIGNED`. Live and turn-based
ratings are separated purely by the +50 id offset — this is the *stated reason* TB ids
exist at all (`GridStateFactory.java:59-60`: "50 + normal game for turn-based games /
only used for separate ratings").

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameServer/core/MySQLDSGPlayerStorer.java:753-754` | `insert into dsg_player_game (pid, game, wins, losses, draws, rating, streak, last_game_date, …)` | Rating row insert keyed on the raw game id | TINYINT bound |
| `gameServer/core/MySQLDSGPlayerStorer.java:798-801` | `update … last_game_date = ? … and game = ?` | | |
| `gameServer/core/MySQLDSGPlayerStorer.java:829-851` | `loadGame(int game, long playerID, boolean computer)`: `select … where … and game = ?` / `stmt.setInt(2, game)` | Single-variant rating load | |
| `gameServer/core/MySQLDSGPlayerStorer.java:890-914` | `loadAllGames`: `select pid, game, wins, … ` (no game filter) | **Loads every rating row the player has, whatever the id.** Rows for unknown ids are carried as data | The one genuinely id-agnostic read path in the whole layer |
| `gameServer/core/MySQLDSGPlayerStorer.java:337` | `for (int i = 0; i < allGames.size(); i++)` | Iterates a **loaded list**, not a dense id range | Safe |
| `gameServer/core/MySQLDSGPlayerStorer.java:940-951` | `sortFields[] = {"dsg_player_game.wins", …}` | Leaderboard sort columns | id-agnostic |
| `gameServer/core/CacheDSGPlayerStorer.java` (2 hits) | cache wrapper over the above | | |
| `gameServer/core/SimpleDSGPlayerGameData.java`, `DSGPlayerGameData.java` | `getGame()/setGame(int)` | **Rating DTO; also travels on the wire** — see §D | |
| `gameServer/client/web/LeaderBoard.java:66` | `and (g.wins + g.losses + g.draws >= 50)` | **False positive** — 50 games played, not a game id | Ignore |

### C6. Server offerings — `dsg_server_game`

`PRIMARY KEY (server_id, event_id, game)` with `game TINYINT UNSIGNED`. This table is what
makes a game *appear* in a room, so it is the gate for player-visible rollout.

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameServer/core/MySQLServerStorer.java:42` | `insert into dsg_server_game …` | | |
| `gameServer/core/MySQLServerStorer.java:88-113` | `addServerGames(dbHandler, int serverId, int siteId, int[] games, String eventName)`: `insert into dsg_server_game (server_id, event_id, game) select d.id, ge.eid, ge.game … join game_event ge on ge.site_id=? and ge.game=? and ge.eid=(select min(eid) …) and not exists (select 1 from dsg_server_game sg where sg.server_id=d.id and sg.game=?)` then `for (int game : games)` | **Idempotent, registry-driven offering insert.** Replaces the old `add_dummy_*.sql` scripts. Silently no-ops when the `game_event` row is missing | The clean extension point: a game not registered in `game_event` simply never gets offered — **graceful skip**, the only one in this layer |
| `gameServer/core/MySQLServerStorer.java:174-177` | `select g.game, g.event_id, e.name from dsg_server_game g, game_event e … and g.event_id = e.eid` | Reads a server's offered games at boot | |
| `gameServer/server/DSGContextListener.java:125-135` | `int[] liveGames = GridStateFactory.LIVE_GAMES;` / `int[] goGames = {GO, SPEED_GO, GO9, SPEED_GO9, GO13, SPEED_GO13};` / `addServerGames(…, 1, 2, liveGames, LIVE_EVENT)` / `(…, 37, …)` / `(…, 46, 2, goGames, …)` / server 45 = KOTH | Boot-time offering sync, derived from `LIVE_GAMES` | **`goGames` is a hardcoded per-id subset** — the only non-registry list here |

### C7. Tournaments — keyed on `eid`, TB-ness by `> 50`

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameServer/tourney/Tourney.java:123` | `return this.game > 50;` (`isTurnBased()`) | **Local reimplementation of `isTurnbasedGame`** | Delete, delegate to the registry |
| `gameServer/tourney/Tourney.java:91` | `return GridStateFactory.getDisplayName(game);` | id → display name; **returns `null` for unknown ids** (linear scan over `displaygames`) | Graceful-null, then NPE downstream |
| `gameServer/tourney/CacheTourneyStorer.java:487` | `if (t.getGame() > 50 && tourneyMatch.getPlayer1() != null && …)` | **>50 test** gating TB tournament match creation | |
| `gameServer/tourney/CacheTourneyStorer.java:492,551` | `GridStateFactory.isSingleGameSet(t.getGame())` | Per-id predicate deciding 1-game vs 2-game sets | |
| `gameServer/tourney/CacheTourneyStorer.java:741` | `if (game > 50) { … }` | **>50 test** | |
| `gameServer/tourney/CacheTourneyStorer.java:744,759,770,787,801` | `if (game == GridStateFactory.TB_PENTE)` ×5 | Hardcoded single-id special cases (auto-tournament scheduling) | |
| `gameServer/tourney/CacheTourneyStorer.java:815` | `tournamentBaseName = GridStateFactory.getDisplayName(game - 50) + " " + dateSuffix;` | **−50 TB→base, then id→name.** Unknown id → `null` concatenated into the tournament name as the literal string `"null"` | |
| `gameServer/tourney/MySQLTourneyStorer.java` (4 hits) | `dsg_tournament.event_id` reads/writes | Tourneys store `eid`, never a game id | |
| `gameServer/tourney/SingleEliminationFormat.java` (4), `DoubleEliminationFormat.java` (1), `AbstractTourneyFormat.java` (1) | `GridStateFactory.*` per-id predicates | | |

### C8. Personal collection / analysis — `webdb_*` (newest subsystem, `SMALLINT`)

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `webdb/MySQLWebDbStorer.java:69,187` | `GridStateFactory.createGridState(g.game, moveDataOf(moves))` | Rebuilds board state from the stored game id. **Unknown id → `createGridState` returns `null` → NPE** | |
| `webdb/MySQLWebDbStorer.java:112-117` | `insert into webdb_game (pid, game, player1, …)` / `stmt.setInt(2, g.game)` | | SMALLINT — headroom to 32767 |
| `webdb/MySQLWebDbStorer.java:153-162` | `insert into webdb_move (… game, winner, pid)` / `stmt.setInt(6, g.game)` | game id denormalised onto every move row | |
| `webdb/MySQLWebDbStorer.java:202-206` | `… and game = ? and next_move = ?` | Dedup candidate query keyed on game id | |
| `webdb/MySQLWebDbStorer.java:238-256` | `listGames(long pid, int game, int offset, int limit)`: `where pid = ? and game = ?` | Per-variant listing | |
| `webdb/MySQLWebDbStorer.java:420` | `mv.add(Integer.valueOf(GridStateFactory.getCenterMove(game)));` | **Synthesises `moves[0]` from the center move** — the storage model documented at `GridStateFactory.java:509-521` as only correct when `firstMoveCanBeOffCenter(game)` is false | Silent corruption for go/D-Pente/swap2 variants; a new off-center variant inherits the bug |
| `webdb/CollectionHandler.java` (6), `GameSearchHandler.java` (1), `PositionStatsHandler.java` (1), `dto/GameHeader.java` (1) | game id plumbed from HTTP JSON → storer | User-supplied id reaches `createGridState` |

### C9. Search / lookup

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameDatabase/MySQLGameStorerSearcher.java:392,399,400` | gid 5e13 filters + `GridStateFactory.TB_RENJU` bound | See C2 | |
| `gameDatabase/HttpGameServlet.java` (9), `MobileGameServlet.java` (7) | game id from request params → searcher | | |
| `gameDatabase/GameVenueJSFormat.java` (3) | Serialises the venue tree to JS for the game browser | id → name; unknown id renders wrong | |
| `game/SimpleCacheGameStorerProxy.java`, `SimpleFileGameStorer.java`, `SimpleMemoryGameStorer.java` | no id arithmetic | Safe | |

---

## D. Wire protocol

### D.1 Transport and encoding — **JSON, no width limit**

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameServer/event/SocketDSGEventHandler.java:120-131` | `DSGEventWrapper wrappedEvent = new DSGEventWrapper(o); String jsonStr = wrappedEvent.getJSON(); byte[] bytes = jsonStr.getBytes("UTF-8"); outStream.write(bytes,0,len); outStream.write(255);` | **TCP (mobile) write path: UTF-8 JSON terminated by a single `0xFF` byte** | `0xFF` is never a valid UTF-8 byte, so the framing is safe for arbitrary integer widths |
| `gameServer/event/SocketDSGEventHandler.java:58-76` | `while ((b = inStream.read()) > -1) { if (b != 255) baos.write(b); else { … gson.fromJson(jsonStr, DSGEventWrapper.class); break; } }` | **TCP read path**: accumulate until `0xFF`, then Gson-deserialise | |
| `gameServer/event/ClientSocketDSGEventHandler.java:33-46` | `outStream = new BufferedOutputStream(socket.getOutputStream()); inStream = new BufferedInputStream(...)` — note the commented-out `ObjectOutputStream`/`DataOutputStream` | Confirms the legacy Java-serialization and DataStream paths are **dead**, replaced by JSON | No binary event codec exists any more |
| `gameServer/event/WebSocketDSGEventHandler.java` (MessageWriter / `readMessage(String)`) | Same Gson configuration, `session.getBasicRemote().sendText(...)` | **WebSocket path is text JSON** — identical codec, no framing byte | |
| `gameServer/event/DSGEventWrapper.java:15-89` | 78 typed fields, one per event class (`private DSGChangeStateTableEvent dsgChangeStateTableEvent;` …) | **The event-type discriminator is the JSON field name**, resolved reflectively | Adding a new *event type* is a breaking change for old clients; adding a new *game id* is not |
| `gameServer/event/DSGEventWrapper.java:91-104` | ctor: `for (Field f : getDeclaredFields()) if (o.getClass().getName().equals(f.getType().getName())) f.set(this, o)` | Reflective encode | |
| `gameServer/event/DSGEventWrapper.java:106-120` | `getEncodedEvent()`: returns the first non-null field | Reflective decode | |
| `gameServer/event/DSGEventWrapper.java:122-130` | Gson with `DSGColorAdapter`, `DSGPlayerDataAdapter`, `DSGPlayerGameDataAdapter` | Only 3 custom adapters; **none touches game ids** | |
| `gameServer/event/DSGPlayerGameDataAdapter.java:159-169` | deserialises to `SimpleDSGPlayerGameData` | Ratings-per-game DTO crosses the wire with an `int game` field | |

**Verified absence:** `grep -rn 'writeByte\|writeShort\|writeInt\|readByte\|readShort\|readInt\|writeUTF\|readUTF' org/pente/gameServer/event/` → **no output**. There is no fixed-width
encoding, no bit-packing, no length-prefixed binary field anywhere in the event package.

### D.2 Events that carry a game id — all `int`

| File:line | Field | Encoded width | Notes |
|---|---|---|---|
| `event/DSGChangeStateTableEvent.java:12` | `private int game;` (+ `setGame/getGame` :78-84) | **JSON number ← Java `int`** | **The main game-selection message.** Client→server table config: game, timed, minutes, increment, rated, tableType |
| `event/DSGChangeStateTableErrorEvent.java` (extends `AbstractDSGTableErrorEvent`) | `setGame(int)` used at `ServerTable.java:951` | JSON int | Server→client state resync on error |
| `event/DSGArenaCreateTableEvent.java:5` | `private int game;` (ctor :109-117, `getGame()` :123) | JSON int | Arena/quick-match table creation |
| `event/DSGGameStateTableEvent.java:13` | `private int gameInSet;` | JSON int | **Not a game id** — the 1-or-2 index within a tournament set. Do not confuse |
| `event/DSGServerStatsEvent.java:8` | `private int games;` | JSON int | **Not a game id** — a count |
| `core/DSGPlayerGameData` / `SimpleDSGPlayerGameData` | `getGame()/setGame(int)` | JSON int, via `DSGPlayerGameDataAdapter` | Per-variant rating rows pushed to clients on login/update |

There is **no** "game list" / "server offerings" event. The set of games a room offers is
delivered implicitly: the server builds `ServerTable` objects from `dsg_server_game` at
boot, and the client discovers games from the ids it sees in `DSGChangeStateTableEvent`
broadcasts plus its own hardcoded local list. **This is why old mobile clients keep
working: the server never enumerates games to them.**

### D.3 Server-side handling of the wire game id — `ServerTable.java`

| File:line | Snippet | Meaning | Refactor note |
|---|---|---|---|
| `gameServer/server/ServerTable.java:85` | `protected Game game = GridStateFactory.PENTE_GAME;` | Default table game | |
| `ServerTable.java:269` | `game = GridStateFactory.getGame(tourney.getGame());` | Tourney game id (may be a **TB id**) → `getGame()` | `getGame(51..81)` → `tbGames[(id-51)/2]` |
| `ServerTable.java:284` | `game = GridStateFactory.getGame(e.getGame());` | From `DSGArenaCreateTableEvent` — **client-supplied** | |
| `ServerTable.java:910` | `Game newGame = GridStateFactory.getGame(changeStateEvent.getGame());` then `newGame.getId()` | **Client-supplied id, unvalidated** | See §E |
| `ServerTable.java:915,918` | `newGame = GridStateFactory.getSpeedGame(newGame);` / `getNormalGame(newGame)` | **±1 speed pairing applied server-side** based on `Game.isSpeedGame(minutes, increment)` | The server *rewrites* the client's game id — clients always send the normal id and receive back the speed id |
| `ServerTable.java:940,753,1092,951` | `changeStateEvent.setGame(game.getId());` | Server echoes the resolved id back | |
| `ServerTable.java:976` | `game = GridStateFactory.getGame(changeStateEvent.getGame());` inside `changeTableState()` | **Second unvalidated client→`getGame()` path** | |
| `ServerTable.java:1882` | `gridState = GridStateFactory.createGridState(game.getId());` | Board construction | `null` for unknown ids |
| `ServerTable.java:1938` | `handleMove(playingPlayers[1].getName(), GridStateFactory.getCenterMove(game.getId()));` | Auto-plays the center opening move | Guarded by a 14-way `!=` id list at :1931-1937 |
| `ServerTable.java:532-546, 1185-1206, 1270-1271, 1885-1894, 1961-1967, 2054-2057, 3406-3410` | Long `game == GridStateFactory.X_GAME \|\| …` chains (swap2 / D-Pente / go / renju behaviour) | ~9 separate per-id behavioural switches on the live-game path | Each is a place a new variant silently gets default behaviour |
| `ServerTable.java:2778-2781` | `boolean single_game = (id==GO \|\| SPEED_GO \|\| GO9 \|\| SPEED_GO9 \|\| GO13 \|\| SPEED_GO13 \|\| RENJU \|\| SPEED_RENJU)` | Duplicates `GridStateFactory.isSingleGameSet` for live ids only | Consolidate |
| `ServerTable.java:3650-3653` | `boolean k32Game = game==GO \|\| … \|\| RENJU \|\| SPEED_RENJU;` | K-factor 32 rating selection by id | |
| `ServerTable.java:3306` | `if (d.getGame() == game)` | Rating-row match by `Game` object identity | Works because `Game` objects are singletons |
| `ServerTable.java:3332` | `gameData.setGame(game.getName());` | **Persists the display *name*, not the id**, into the archive DTO | Name↔id round-trip via `getGameId(String)` — live/TB share names |
| `gameServer/server/ArenaServerTable.java` (4), `MMAIPlayer.java` (6), `MMAIProtocol.java` (2), `AIPlayerFactory.java` (1), `XMLAIConfigurator.java` (1) | `GridStateFactory.*` per-id dispatch | AI-side game selection | |

---

## E. UNKNOWN-ID BEHAVIOUR — what happens when this layer meets a game id it doesn't know

**Verdict: the persistence + wire layer never gracefully skips an unknown live id. It
either throws or silently returns the wrong game. There is exactly one graceful-skip path
(`MySQLServerStorer.addServerGames`), and it is on the write side.**

Three distinct unknown-id regimes, by id range:

### E.1 Id in 33..48 — **SILENT WRONG ANSWER (worst case)**

Exact path: `ServerTable.java:976` → `GridStateFactory.getGame(33)` →
`GridStateFactory.java:396-402`: `game > TB_START` is false (33 < 50) → `return allGames[33]`
→ `allGames` (`GridStateFactory.java:139-154`) has `TB_PENTE_GAME` at index 33.

The server silently treats the table as turn-based Pente. `game.getId()` is then echoed
back to every client as **51**, and `createGridState(33)` (`GridStateFactory.java:240-246`
pre-populated `gridStates[33] = createGridState(33)`) returned **`null`** because the
factory switch has no `case 33`. So `ServerTable.java:1882` assigns `gridState = null`
and the first move NPEs. No exception is raised at the id-resolution boundary — the
failure surfaces far downstream with no indication that the game id was the cause.

**This is the single most important fact for the refactor: id 33 is not free. It is
currently occupied by `TB_PENTE_GAME` inside `allGames[]`.** Any staged plan that adds a
25th live pair at 33/34 without first replacing `allGames[]` with a map will corrupt
turn-based Pente resolution.

### E.2 Id in 49, 50, or > 81 — **CRASH (`ArrayIndexOutOfBoundsException`)**

- `getGame(49)` / `getGame(50)`: `game > 50` false → `allGames[49]` / `allGames[50]`, but
  `allGames.length == 49` → **AIOOBE**.
- `getGame(83)`: `game > 50` true → `tbGames[(83-51)/2]` = `tbGames[16]`, but
  `tbGames.length == 16` → **AIOOBE**.
- `getColor(moveNum, game)` (`GridStateFactory.java:479-481`): `gridStates[game]` with
  `gridStates.length == 49` → **AIOOBE for any id ≥ 49, including every turn-based id**.

Reached from the wire with no validation at `ServerTable.java:910` and `:976`
(`changeStateEvent.getGame()` is whatever the client sent) and at `:284`
(`DSGArenaCreateTableEvent.getGame()`). A malformed or malicious client can throw an
unhandled exception inside the table's event loop today. `SocketDSGEventHandler`'s
`ObjectReader` catches `Throwable` and calls `handleError` → `destroy()` → disconnect, so
the blast radius is the connection, not the JVM — but the table is left in an
inconsistent state.

The **only** bound check in the whole server is
`turnBased/web/NewGameServlet.java:195`: `if (game == -1 || game > GridStateFactory.getMaxGameId())`.
It rejects > 81 but **admits 33..50**, i.e. exactly the E.1 silent-wrong-answer band.
`GridStateFactory.isValidGame()` (`:400`, `game >= 1 && game <= 32`) would catch it, but
it is not called anywhere on the server path — only in the AWT client and tests, and it
returns `false` for every legitimate turn-based id.

### E.3 Id is well-formed but **unregistered in `game_event`** — split behaviour

This is the realistic rollout scenario: server code knows the id, DB has no row yet.

| Path | Behaviour | Cite |
|---|---|---|
| Server offerings | **Graceful skip.** The `insert … select … join game_event ge` matches nothing, zero rows inserted, no error. The game simply never appears in a room | `gameServer/core/MySQLServerStorer.java:95-104` |
| Boot registration | **Self-healing.** `registerAllGames(2)` recreates the missing `game_event` rows from `LIVE_GAMES`/`TB_GAMES` on every boot; wrapped in a try/catch that logs and continues | `game/MySQLGameVenueStorer.java:270-290`, `gameServer/server/DSGContextListener.java:117-119` |
| Turn-based create | **Silent bad data.** `getEventId` returns `-1`; caller stores `event_id = -1` into `tb_game` | `turnBased/MySQLTBGameStorer.java:31,68` + `turnBased/CacheTBStorer.java:1466-1467` |
| KOTH | **Latent bug.** `MySQLKOTHStorer.getEventId` returns `-1`, but every caller guards on `if (hill_id == 0) return;` — so `-1` passes and hills are read/written under `koth_id = -1` | `kingOfTheHill/MySQLKOTHStorer.java:31` vs `CacheKOTHStorer.java:111,147,202,226` |
| Venue tree | **Graceful null.** `findGameTreeData` linear-scans and returns `null`; callers `getGameSiteData`/`getGameEventData` return `null` | `game/MySQLGameVenueStorer.java:441-455,160-178` |
| Archive load | **NPE.** `getGameName(game)` → `getGame(game).getName()` | `game/MySQLPenteGameStorer.java:764,1066` |
| Personal collection | **NPE.** `createGridState(g.game, …)` returns `null` for an unswitch-cased id | `webdb/MySQLWebDbStorer.java:69,187` |
| Tournament naming | **String `"null"`.** `getDisplayName()` linear-scans `displaygames` and returns `null` | `gameServer/tourney/CacheTourneyStorer.java:815`, `Tourney.java:91` |

### E.4 Wire layer — **fully id-agnostic, this is the good news**

The JSON codec has no knowledge of game ids at all. `DSGEventWrapper` dispatches on the
*event class name*; the `game` field is a plain `int` that Gson round-trips unchanged.
A server that starts emitting `{"dsgChangeStateTableEvent":{"game":97,...}}` will be
parsed without error by any existing client build, old or new — the client's *own*
lookup table is what decides whether it renders correctly. **Nothing in
`org/pente/gameServer/event/` needs to change to carry arbitrarily large game ids**, and
no wire-format version negotiation is required. Old mobile apps keep working for
existing games because the server never sends them a game enumeration (§D.2).

---

## F. Cap-relevant width limits

Ordered by which binds first.

| # | Limit | Value | Where | Binds at | Blocker? |
|---|---|---|---|---|---|
| 1 | `allGames[]` positional array; indices 33..48 hold TB Game objects | **32 live ids** | `game/GridStateFactory.java:139-154` + `:396-402` | the **25th** live pair (ids 33/34) | **YES — hard blocker, silent corruption** |
| 2 | `gridStates[]` sized `getNumGames()+1` = 49 | **48** | `game/GridStateFactory.java:237,240-246,479-481` | id ≥ 49 → AIOOBE in `getColor` | **YES** |
| 3 | `TB_START = 50` → live ids must be < 50 | **24 live pairs** | `game/GridStateFactory.java:61` | the 25th live pair | **YES — the documented cap** |
| 4 | `tbGames[]` indexed `(id - 51) / 2` — requires TB ids to be *dense and odd* | **16 TB ids** | `game/GridStateFactory.java:396-402` | any TB id outside {51,53,…,81} | **YES** |
| 5 | `game_event.game` | `TINYINT UNSIGNED` = **255** | live DB | live id > 205 (since TB = live+50) or any id > 255 | Width blocker at 255 |
| 6 | `pente_game.game` | `TINYINT UNSIGNED` = **255** | live DB, indexed (`MUL`) | same | Width blocker at 255 |
| 7 | `pente_move.game` | `TINYINT UNSIGNED` = **255** | live DB | same | Width blocker at 255 |
| 8 | `dsg_player_game.game` | `TINYINT UNSIGNED` = **255**, part of `PRIMARY KEY (pid, game, computer)` | live DB | same | Width blocker at 255; **PK change = table rebuild on a 60k+ row MyISAM table** |
| 9 | `dsg_server_game.game` | `TINYINT UNSIGNED` = **255**, part of `PRIMARY KEY (server_id, event_id, game)` | live DB | same | Width blocker at 255; PK change |
| 10 | `tb_game.game`, `tb_game_ai.game` | `SMALLINT UNSIGNED` = **65535** | live DB | id > 65535 | Not a practical blocker |
| 11 | `webdb_game/move/analysis.game` | `SMALLINT` signed = **32767** | live DB | id > 32767 | Not a practical blocker |
| 12 | `pente_game.gid` TB discriminator `>= 50000000000000` | live gid space **capped at 5e13**; ~1.6e13 consumed (max live gid 3.42e13) | 15 code sites (`FastMySQLDSGGameLookup` ×8, `MySQLGameStorerSearcher` ×2, `MySQLPenteGameStorer` ×1, `RatingsGrapher` ×4) + `tb_game` AUTO_INCREMENT seed | ~1.6e13 more archived live games | Not a game-count limit, but a magic constant to centralise |
| 13 | Wire protocol | **none** — Gson JSON, Java `int` | `event/SocketDSGEventHandler.java`, `WebSocketDSGEventHandler.java` | `Integer.MAX_VALUE` | **NOT a blocker** |
| 14 | `game_event.eid` | `INT UNSIGNED`, auto_inc at 1814 | live DB | 4.29e9 | Not a blocker |

**Practical reading:** limits 1–4 are the real cap and are all in one file
(`GridStateFactory.java`). Limits 5–9 are a second, softer ceiling at 255 that a widening
migration (`TINYINT → SMALLINT UNSIGNED`) removes; two of those five are primary-key
columns, so plan for MyISAM table rebuilds. Limit 13 confirms **no client-visible protocol
change is needed at any stage** — which is what makes an invisible early-stage rollout
feasible.

---

## G. Notes for the strategy designer

- **Safest first stage** (invisible to players, zero client change): replace
  `allGames[]`/`gridStates[]`/`tbGames[]` positional indexing in `GridStateFactory` with
  maps keyed by id, keeping every existing constant and every existing id value byte-for-byte
  identical. This removes limits 1, 2 and 4 with no observable behaviour change and no
  migration. `game/test/RenjuFactoryTest.java:68-70` and
  `game/test/GridStateFactorySingleGameSetTest.java` already pin the current semantics.
- **Second stage**: widen the five `TINYINT` columns to `SMALLINT UNSIGNED`. Purely
  additive; every Java field is already `int`.
- **Third stage**: centralise the 15 `50000000000000` literals and the ~14 `> 50` /
  `- 50` sites behind registry predicates (`isTurnbased(id)`, `baseOf(id)`), still with
  the current id values. Only then is `TB_START` free to change.
- **Traps to carry into the plan**: (a) `pente_game` stores TB games under the *base* id
  while `tb_game` stores the *TB* id — two conventions for one concept; (b) the KOTH
  event-name string is inconsistent three ways ("King Hill" / "King of Hill" /
  "King of the Hill" in the live DB); (c) `CacheKOTHStorer` guards `== 0` against a
  storer that returns `-1`; (d) `GridStateFactory.getGameId(String)` resolves shared
  live/TB display names to the **live** id.
