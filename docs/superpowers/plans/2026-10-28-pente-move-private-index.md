# pente_move private flag + covering indexes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make position stats, game count and game paging for the web database index-only by copying `pente_game.private` into `pente_move` and rebuilding the two `pente_move` secondary indexes around it.

**Architecture:** One MyISAM `ALTER` adds `pente_move.private` and rebuilds `hash_key` and `hash_key_2` in a single table copy. A play_date-preserving backfill copies the private flag over. The only production writer (`MySQLPenteGameStorer`) starts writing the flag. Then the read paths filter on `m.private='N'` and drop the per-row `pente_game` join whenever no game-level filter is set. DB changes ship before code: every reader and writer change references the new column.

**Tech Stack:** MariaDB 13.0.2 (MyISAM, ROW binlog, GTID replica), Java 21 / Tomcat, Ant, JUnit 3.7 (`junit-3.7.jar`, no `assertFalse`).

**Spec:** The "Design" section below. It captures the measured findings from the 2026-10-28 performance investigation (EXPLAIN/ANALYZE on the local replica) and the read-only write-path sweep. Raw evidence: `/private/tmp/claude-501/pentedb-perf/`, `/private/tmp/claude-501/pm-sweep/` (scratch; copy anything worth keeping before it is cleaned).

## Design

**Problem (measured on the replica, first load of Pente, game 1, moves `[180]`):** stats, count and page each spend 2.3–3.8 s warm (13 s cold for stats) doing ~1.04M primary-key lookups into `pente_game`, only to apply `g.private='N'`. The position match alone, on the existing covering index, takes 0.07–0.10 s. The page query also sorts ~1M rows to keep 25.

**Target schema:**
- `pente_move.private ENUM('Y','N') NOT NULL DEFAULT 'N'` (same type as `pente_game.private`). The default becomes `'Y'` (fail closed) once the writer sets the flag explicitly (Task 6).
- `KEY hash_key (hash_key, move_num, game, private, next_move, rotation, winner)` serves unfiltered and winner-filtered stats and the count, index-only, with no temp table.
- `KEY hash_key_2 (hash_key, move_num, game, private, play_date, gid)` serves the page `order by m.play_date desc, m.gid desc` with no filesort, and the count.
- MYI grows from ~2.69 GB to ~3.10 GB (gigabytes); MYD from ~1.085 GB to ~1.121 GB. Peak extra disk ~4.2 GB during the copy.

**What becomes index-only:** stats, count and page with no filters, plus stats with only a winner filter. Filters on players, site, event, round, section or date still join `pente_game` (those columns only exist there, and `pente_move.play_date` differs from `pente_game.play_date` for ~88% of games, so date filters cannot move). Those filters are selective, so the join stays cheap.

**Facts the plan relies on (from the sweep):**
- `MySQLPenteGameStorer.storeGameNewThread` (`dsg_src/java/org/pente/game/MySQLPenteGameStorer.java:484-524`) is the only production writer of `pente_move`. Callers: live game end (`ServerTable.java:3838`), turn-based completion (`CacheTBStorer.java:1085`), and one-off importers.
- Nothing changes `pente_game.private` after insert, so a one-time copy plus write-time population stays consistent.
- No trigger, routine or event exists. No `SELECT *` or positional `INSERT` touches `pente_move`, so adding a column breaks no reader.
- `pente_move.play_date` is `TIMESTAMP ... ON UPDATE current_timestamp()`. **Every UPDATE of `pente_move` must also set `play_date = play_date`**, or it rewrites the paging sort key to NOW().
- 3 orphan games (gids 21000000004659–21000000004661, 90 move rows) have moves but no `pente_game` row. They are marked `'Y'` so they stay hidden, as the join hides them today.
- Reads go to the replica (`jdbc/dsg_ro` → `dbHandlerRo`, `DSGContextListener.java:124,219-222`). Writes go to the primary (`jdbc/dsg`). Replication is ROW-based with GTID, `replicate_do_db=dsg`, one applier thread. After the migration, new `pente_move` rows from the primary carry the `private` column, so the replica must already have it when replication restarts.
- The legacy mobile search (`MySQLGameStorerSearcher`, used by the Android and iOS apps through `/gameServer/mobileController/search`) loses its current index ordering after the rebuild unless it also filters `m.private` with an equality (Task 5).

## Global Constraints

- The archive tables were declared read-only for webdb (`docs/superpowers/plans/2026-07-18-pentedb-0-master.md:42-43`). This plan changes `pente_move` schema and data. The owner chose the database route on 2026-10-28; confirm lifting the rule for this plan only before execution starts.
- **Agents never run DDL, UPDATEs, container restarts or deploys** against the local stack or production. Tasks 6 and 7 are owner-run runbooks; agents only write the files and the checklists.
- Migration file naming: `dsg_src/sql/YYYY-MM-DD-kebab-topic.sql`, header in the style of `dsg_src/sql/2026-10-19-kid-accounts.sql`. `schema.sql` is updated in the same commit.
- **This migration is applied on the primary only** (owner decision, 2026-10-28), with `dsg` as the default database so it replicates. **Never apply it on the replica by hand**: the replica gets the ALTER through replication, and a manual apply would copy the table a second time and break replication on the duplicate column. This deviates on purpose from the repo's "apply to live + replica" header convention.
- Before applying, check `SELECT @@sql_mode` on both servers has no `NO_ZERO_DATE`/`NO_ZERO_IN_DATE`/`STRICT_*` mode: gid 21000000033287 has 32 move rows with `play_date = 0000-00-00`, which would make the copy or the backfill fail.
- Java tests: JUnit 3.7, DB-free, run with `ant test-one -Dtest=<FQCN>` (canonical command in each task). Add each new test class to the `test` target in `build.xml`.
- Commits: `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S`.
- Package runners: local binaries only; never fetch-and-run. Scratch outside the repo: `/private/tmp/claude-501/pm-impl`.
- Deploy order: the **writer build (Task 2)** ships at the end of the downtime window, once the primary has the column and the backfill. The **reader builds (Tasks 3–5)** ship only once the replica has the column too. Either one fails with "Unknown column 'private'" if deployed early.
- **Expected stale period after the window:** the replica applies replicated events with one thread (`slave_parallel_threads=0`), so while it replays the ALTER (about as long as the primary's copy) all `dsg` replication waits. During that time games finished after the window are not yet viewable through `dsg_ro` (game views, ratings graph, stats). The owner accepted this over a parallel apply. Announce it with the downtime window.

## Owner decisions (confirmed 2026-10-28: all three defaults)

- **D1 Legacy stats and private games:** the legacy mobile stats query counts private games today (the guard only reaches the games list, `MySQLGameStorerSearcher.java:92-94`). Task 5 adds `m.private='N'` to it, so mobile stats stop counting private games. That matches the web database and the games list. The alternative, `m.private IN ('Y','N')`, keeps today's numbers but loses the index ordering. **Default in this plan: exclude (`='N'`).**
- **D2 Write pause during the copy:** while the ALTER copies `pente_move` on the primary, game-end inserts block. The store thread never retries (`MySQLPenteGameStorer.java:316-319`), and the `jdbc/dsg` pool reclaims connections held over 30 s (`dockercontext.xml:7,10`), so blocked games can lose their moves. **Default: stop the `pente.org` app container for the primary ALTER and backfill** (a planned downtime window), then restart it.
- **D3 Backups:** disable `dockerReplica/backupDBdocker` (it restarts `replica_db`, which would abort the replica's replay) for the window, and confirm a fresh backup exists before starting.

## Review Focus

1. **A new private game stored with `m.private='N'`** would leak into stats and search. The window restarts with the writer build already deployed and the default already `'Y'`, so no game is ever stored by old code on the new schema. Task 2's tests pin that the writer sends the flag on every insert.
2. **Any UPDATE of `pente_move` without `play_date = play_date`** silently reorders search results. Task 1's verification query compares play_dates before and after; Task 2 asserts the existing `update pente_move set winner` statement keeps play_date.
3. **A filtered search (player, site, date) must still return the same games as today.** Task 3 and 4 tests assert the join and `m.private='N'` are both present when a game-level filter is set.
4. **Moves stored by the "game already stored" path** (importers appending moves) must copy the stored game's flag, not the caller's. Task 2 pins it.
5. **Rollback to the old writer after the default is `'Y'`:** old code doesn't name `private`, so every new public game would get `'Y'` and vanish from search. The rollback section sets the default back to `'N'` before redeploying the old writer, and includes a `'Y'→'N'` repair for games stored in between.
6. **A bound-parameter count that no longer matches the SQL** after the `pente_game` join becomes conditional. Tasks 3 and 4 assert that the number of `?` placeholders equals the parameter count in every filter case, for stats, count and page.

---

### Task 1: Migration file, schema snapshot and verification queries

**Files:**
- Create: `dsg_src/sql/2026-10-28-pente-move-private.sql`
- Create: `dsg_src/sql/2026-10-28-pente-move-private-verify.sql`
- Modify: `dsg_src/sql/schema.sql:881-894` (`pente_move` definition)

**Interfaces:**
- Produces: column `pente_move.private ENUM('Y','N') NOT NULL DEFAULT 'N'`; indexes `hash_key (hash_key, move_num, game, private, next_move, rotation, winner)` and `hash_key_2 (hash_key, move_num, game, private, play_date, gid)`. Later tasks rely on these exact names and orders.

- [ ] **Step 1: Write `2026-10-28-pente-move-private.sql`** with a header stating: purpose; "Apply on the PRIMARY only, as `mariadb ... dsg < file`, so it replicates; NEVER apply on the replica (runbook in docs/pente-move-private-rollout.md)"; "Stop the pente.org app container first (plan D2)"; "Not re-runnable as a whole: section 1 rebuilds the table". Four sections, in order (the ALTERs replicate as statements, the backfill UPDATEs as ~0.84M row events):
  1. One statement: `ALTER TABLE pente_move ADD COLUMN IF NOT EXISTS private ENUM('Y','N') NOT NULL DEFAULT 'N' AFTER winner, DROP INDEX hash_key, ADD INDEX hash_key (hash_key, move_num, game, private, next_move, rotation, winner), DROP INDEX hash_key_2, ADD INDEX hash_key_2 (hash_key, move_num, game, private, play_date, gid);`
  2. Backfill private games: `UPDATE pente_move m JOIN pente_game g ON g.gid = m.gid SET m.private = 'Y', m.play_date = m.play_date WHERE g.private = 'Y' AND m.private = 'N';`
  3. Backfill orphans: `UPDATE pente_move m LEFT JOIN pente_game g ON g.gid = m.gid SET m.private = 'Y', m.play_date = m.play_date WHERE g.gid IS NULL AND m.private = 'N';`
  4. Fail-closed default, metadata only: `ALTER TABLE pente_move ALTER COLUMN private SET DEFAULT 'Y', ALGORITHM=INSTANT;` (`INSTANT` makes it fail loudly rather than copy the table again; if it fails, stop and report — do not drop the clause).
  Sections 2–3 are idempotent. The verify queries join all 36M rows; note in the file that they take minutes and are run during the window, not on live traffic.

- [ ] **Step 2: Write `2026-10-28-pente-move-private-verify.sql`** (read-only, safe on either server), each query labelled with its expected result:
  - column and index shape from `information_schema.COLUMNS` / `STATISTICS` (expected: the column list above, in order);
  - `SELECT COUNT(*) FROM pente_move m JOIN pente_game g ON g.gid = m.gid WHERE m.private <> g.private;` → `0`;
  - `SELECT COUNT(*) FROM pente_move m LEFT JOIN pente_game g ON g.gid = m.gid WHERE g.gid IS NULL AND m.private <> 'Y';` → `0`;
  - `SELECT m.private, COUNT(*) FROM pente_move m GROUP BY m.private;` → about `Y 839,422` (839,332 + 90 orphan rows), `N 35,333,856` on the 2026-10-28 replica data;
  - play_date guard: `SELECT COUNT(*) FROM pente_move WHERE play_date >= '<migration start time>';` → only rows from games stored after the start (the backfill must not touch play_date);
  - `EXPLAIN` of the three webdb queries for game 1 at `hash_key = -4230528974382094949, move_num = 0` with `m.private = 'N'` and no `pente_game` join → `Using index`, no `Using temporary`/`Using filesort`.

- [ ] **Step 3: Update `schema.sql`** `pente_move` block: add `` `private` enum('Y','N') NOT NULL DEFAULT 'Y' `` after `winner` (the final default, after Task 6), and replace the two KEY lines with the new orders.

- [ ] **Step 4: Review the SQL statically** (agents do not run it): every `UPDATE pente_move` sets `m.play_date = m.play_date`; section 1 is a single ALTER (one table copy); no statement targets the replica.

- [ ] **Step 5: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/sql/2026-10-28-pente-move-private.sql dsg_src/sql/2026-10-28-pente-move-private-verify.sql dsg_src/sql/schema.sql
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "sql: pente_move private flag and covering indexes (migration + verify)"
```

### Task 2: Writer sets `pente_move.private`

**Files:**
- Modify: `dsg_src/java/org/pente/game/MySQLPenteGameStorer.java` (`storeGameNewThread` :333-524, `getGameStatus` :594-620)
- Test: `dsg_src/java/org/pente/game/test/PenteMovePrivateWriteTest.java`
- Modify: `build.xml` (`test` target)

**Interfaces:**
- Consumes: column `pente_move.private` (Task 1).
- Produces: every `insert into pente_move` names `private` in its column list and binds `"Y"`/`"N"`.

- [ ] **Step 1: Write the failing tests** in `PenteMovePrivateWriteTest` using a `java.lang.reflect.Proxy` fake `Connection`/`PreparedStatement` that records SQL and bound parameters (same technique as `org.pente.webdb.test.DbAccessGateTest`):
  - `testNewPrivateGameWritesY`: storing a new game with `setPrivateGame(true)` records every `insert into pente_move` with a `private` column and the value `"Y"` bound for every row.
  - `testNewPublicGameWritesN`: same with `false` → `"N"`.
  - `testAppendToStoredGameUsesStoredFlag`: the fake returns `private = 'Y'` for the stored game from the status query, the caller's `GameData` says `false` → appended move rows bind `"Y"`.
  - `testWinnerUpdateKeepsPlayDate`: the existing `update pente_move set winner` statement, if issued, contains `play_date = play_date`.
- [ ] **Step 2: Run them to verify they fail**

Run: `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=org.pente.game.test.PenteMovePrivateWriteTest`
Expected: FAIL (no `private` column in the insert).

- [ ] **Step 3: Implement.** Add `private` to the `insert into pente_move` prepared statement (:484-487; it is bound at two sites, row 0 at :488-505 and later rows at :507-524), bound from a local `String movePrivate`. For a new game, `movePrivate = data.isPrivateGame() ? "Y" : "N"`. For the already-stored path, extend `getGameStatus` to also select `private` and use the stored value. Add `, play_date = play_date` to the `update pente_move set winner` statement at :465. Do not fix that statement's swapped parameter binding (out of scope; note it in the commit body).
- [ ] **Step 4: Run the test to verify it passes** (same command). Expected: `OK (4 tests)`.
- [ ] **Step 5: Add the class to the `test` target in `build.xml`, then commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/java/org/pente/game/MySQLPenteGameStorer.java dsg_src/java/org/pente/game/test/PenteMovePrivateWriteTest.java build.xml
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "storer: write pente_move.private on every move insert"
```

### Task 3: Position stats read path

**Files:**
- Modify: `dsg_src/java/org/pente/webdb/PositionStatsHandler.java` (`buildArchiveSql` :366-470)
- Test: `dsg_src/java/org/pente/webdb/test/PositionStatsPrivateSqlTest.java`
- Modify: `build.xml`

**Interfaces:**
- Consumes: `pente_move.private`, index `hash_key` (Task 1).
- Produces: `buildArchiveSql(...)` (existing public signature, unchanged) returns SQL that always contains `m.private = 'N'`, joins `pente_game g` only when a game-level filter (players, site, event, round, section, afterDate, beforeDate) is set, and never contains `g.private`.

- [ ] **Step 1: Write the failing tests** against `buildArchiveSql`:
  - `testUnfilteredHasNoGameJoin`: filters `null` → SQL contains `m.private = 'N'`, does not contain `pente_game`, still groups by `m.next_move, m.rotation, m.winner`.
  - `testWinnerOnlyHasNoGameJoin`: only `winner = 1` → no `pente_game`, contains `m.winner = ?`.
  - `testPlayerFilterKeepsJoin`: `player1Name = "alice"` → contains `pente_game g`, `m.gid = g.gid` and `m.private = 'N'`, not `g.private`.
  - `testDateFilterKeepsJoin`: `afterDate` set → contains `g.play_date`.
  - `testUnresolvedSiteNoJoin`: a site name that doesn't resolve to an id (the handler skips that filter) and nothing else → no `pente_game`.
  - `testPlaceholderCountMatchesParams`: for each case above, the number of `?` in the SQL equals the bound-parameter count.
- [ ] **Step 2: Run to verify they fail** (canonical command with `-Dtest=org.pente.webdb.test.PositionStatsPrivateSqlTest`). Expected: FAIL.
- [ ] **Step 3: Implement** in `buildArchiveSql`: replace `g.private = 'N'` with `m.private = 'N'`; add `g` to the FROM list and the `m.gid = g.gid and g.game = ?` predicates only when a game-level filter is actually applied (key on the resolved site/event ids, not the raw names). Bound-parameter order must match the SQL. If the test needs the parameter list, expose it through a package-private method next to `buildArchiveSql`.
- [ ] **Step 4: Run to verify pass**, plus the existing `org.pente.webdb.test.TombstoneWebDbExclusionTest`. Expected: both `OK`.
- [ ] **Step 5: Add to `build.xml` `test` target; commit** with message `webdb: position stats filter m.private and drop the game join when unfiltered`.

### Task 4: Games search count and page

**Files:**
- Modify: `dsg_src/java/org/pente/webdb/GameSearchHandler.java` (`buildArchive` :292-340, `appendFilters` :440-503)
- Modify: `dsg_src/java/org/pente/webdb/test/GameSearchPagingOrderTest.java`
- Test: `dsg_src/java/org/pente/webdb/test/GameSearchPrivateSqlTest.java`
- Modify: `build.xml`

**Interfaces:**
- Consumes: `pente_move.private`, index `hash_key_2` (Task 1); `pagedGidSql(String gidCol, String from, String where, String orderCol)` (existing, unchanged).
- Produces: position-mode archive queries (with `moves`) always contain `m.private = 'N'` and join `pente_game g` only for game-level filters. Filter-only search (no `moves`) is unchanged: it scans `pente_game` and keeps `g.private = 'N'`.

- [ ] **Step 1: Write the failing tests** using `archiveSqlForTest(GameSearchRequest)`:
  - `testPositionUnfilteredNoJoin`: moves `[180]`, filters `null` → contains `m.private = 'N'`, no `pente_game`, order `m.play_date desc, m.gid desc`.
  - `testPositionWinnerOnlyNoJoin`: winner filter only → no `pente_game`.
  - `testPositionPlayerFilterKeepsJoin`: player filter → `pente_game g`, `m.gid = g.gid`, `m.private = 'N'`, no `g.private`.
  - `testFilterOnlyUnchanged`: no moves → still `g.private = 'N'`, no `m.private`.
  - `testPlaceholderCountMatchesParams`: for each case above, `?` count equals the bound-parameter count, for both the count and the page SQL.
- [ ] **Step 2: Run to verify they fail.** Expected: FAIL.
- [ ] **Step 3: Implement** the same conditional-join rule as Task 3 in `buildArchive`/`appendFilters`, for both the count and the page (they share `from`/`where`). Update `GameSearchPagingOrderTest` expectations if they assert the old FROM clause.
- [ ] **Step 4: Run** this class, `GameSearchPagingOrderTest` and `TombstoneWebDbExclusionTest`. Expected: all `OK`.
- [ ] **Step 5: Add to `build.xml`; commit** with message `webdb: games search filters m.private and drops the game join when unfiltered`.

### Task 5: Legacy mobile/desktop search

**Files:**
- Modify: `dsg_src/java/org/pente/gameDatabase/MySQLGameStorerSearcher.java` (`getSearchResults` :423-520, `getMatchingGames` :582-660)
- Test: `dsg_src/java/org/pente/gameDatabase/test/LegacyPrivateSqlTest.java`
- Modify: `build.xml`

**Interfaces:**
- Consumes: `pente_move.private` (Task 1); decision D1.
- Produces: every legacy query arm contains `m.private = 'N'` right after the `m.game = <game>` predicate. There are four arms, not two: the either-seat player search builds a UNION in both methods (`getSearchResults` :470-488, `getMatchingGames` :643-660). The legacy code inlines literals (`"m.game = " + game`), not `?` placeholders; keep that style.

- [ ] **Step 1: Write the failing tests.** If the SQL is only built inline, first extract package-private `static String statsSql(...)` and `static String matchingGamesSql(...)` builders that return exactly today's strings (pure refactor, verified by a test comparing against today's literal output for an unfiltered request, a filtered request and an either-seat UNION request). Then:
  - `testStatsFiltersPrivate`: unfiltered stats SQL contains `m.private = 'N'` and still has no `pente_game` join.
  - `testMatchingGamesFiltersPrivate`: page SQL contains `m.private = 'N'` and keeps `g.private = 'N'`.
  - `testUnionArmsFilterPrivate`: for an either-seat player search, both UNION arms of both queries contain `m.private = 'N'`. Today arm 2 of `getMatchingGames` lacks `g.private = 'N'` (`filterOptionsWhere2` at :92-94 never gets the guard), so private games currently leak there; this test pins the fix.
- [ ] **Step 2: Run to verify they fail.** Expected: FAIL.
- [ ] **Step 3: Implement** by appending `and m.private = 'N'` to every arm in both builders.
- [ ] **Step 4: Run to verify pass.** Expected: `OK`.
- [ ] **Step 5: Add to `build.xml`; commit** with message `legacy search: filter m.private so the rebuilt indexes keep serving it` and the D1 behaviour change in the body.

### Task 6: Rehearsal and rollout runbook (owner-run)

**Files:**
- Create: `docs/pente-move-private-rollout.md`

**Interfaces:**
- Consumes: the migration and verify files (Task 1); the code from Tasks 2–5 (built, not deployed).

- [ ] **Step 1: Write the runbook** with a "Nothing here is executed by an agent" header (as in `docs/webdb-deploy-checklist.md:3`) and these steps, each with its exact command and the result that means "continue":
  1. Pre-checks on both servers: `SELECT @@sql_mode` has no zero-date or strict modes; ~5 GB free in the datadir and ~3 GB in tmpdir. Disable `dockerReplica/backupDBdocker`; confirm a recent backup.
  2. Have the writer build (Task 2) built and ready to sync; the reader builds (Tasks 3–5) stay unreleased.
  3. Stop the `pente.org` app container (D2). Record `SELECT MAX(gid) FROM pente_game` (used by the rollback repair).
  4. Apply the migration file **on the primary only**, with `dsg` as the default database: `docker compose -f docker-compose.yml exec -T main_db sh -c 'mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" dsg' < dsg_src/sql/2026-10-28-pente-move-private.sql`. Watch `SHOW PROCESSLIST` for `Repair by sorting` (not `Repair with keycache`, which is far slower). Record each section's duration.
  5. Run the verify file on the primary. All expectations must hold.
  6. Sync the **writer build** (Task 2) and start the `pente.org` container. Downtime ends here. Every new move row now carries an explicit flag and the default is already `'Y'`. Old readers keep working on the replica: they never name `private`. The replica is now replaying the ALTER; replication waits behind it (the stale period in Global Constraints).
  7. Watch the replica: `SHOW ALL SLAVES STATUS` until `Slave_SQL_Running: Yes`, no error, and `Seconds_Behind_Master = 0`. Record how long the replay took. Then run the verify file on the replica; the `private` counts must match the primary's from step 5 (plus any games stored since).
  8. Store a test game (or wait for a real one) and check its move rows have the game's `private` value on both servers.
  9. Deploy the reader builds (Tasks 3–5) with `sync_gameServer.sh` (no web.xml change). Only now: the readers query the replica, which must have the column.
  10. Re-enable the backup job.
  11. Time `/api/db/position-stats` and `/api/db/games/search` for Pente's first load (target: under 0.5 s each, against 2.3–3.8 s before).
- [ ] **Step 2: Add a rollback section:**
  (a) Readers only (Tasks 3–5): redeploy the previous reader classes. Nothing else changes.
  (b) Writer too: first `ALTER TABLE pente_move ALTER COLUMN private SET DEFAULT 'N', ALGORITHM=INSTANT;` on the primary (it replicates), then redeploy the previous writer. Then repair any public game stored between the two steps with `UPDATE pente_move m JOIN pente_game g ON g.gid = m.gid SET m.private = 'N', m.play_date = m.play_date WHERE g.private = 'N' AND m.private = 'Y' AND m.gid > <max gid recorded at step 3>;` on the primary (it replicates as rows).
  (c) Schema rollback is optional and costs another full copy: `ALTER TABLE pente_move DROP INDEX hash_key, ADD INDEX hash_key (hash_key, move_num, game, next_move, rotation, winner), DROP INDEX hash_key_2, ADD INDEX hash_key_2 (hash_key, move_num, game, play_date), DROP COLUMN private;`, applied the same way as the migration (primary only, app stopped, replica replays it).
- [ ] **Step 3: The owner runs the runbook on the local stack first** as a rehearsal and writes the measured ALTER and backfill durations into the runbook's production section (this sizes the production downtime window).
- [ ] **Step 4: Commit** the runbook with message `docs: pente_move private-flag rollout runbook`.

### Task 7: Production rollout (owner-run)

- [ ] **Step 1:** Announce the downtime window using the rehearsal durations from Task 6 step 3 (the primary's migration run plus verification), and the stale period that follows (the replica's replay time from the rehearsal).
- [ ] **Step 2:** Run the Task 6 runbook against production (`debian@pente.org` primary, replica on `wire.submanifold.be`).
- [ ] **Step 3:** Record the result, timings and verify output in `docs/pente-move-private-rollout.md`, and commit it.
