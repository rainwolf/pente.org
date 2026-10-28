# pente_move private flag — owner rollout runbook

This **owner-run** runbook. Nothing in this document should be executed by an agent — it is documentation only. It covers adding the `private` flag to `pente_move`, rebuilding its `hash_key` / `hash_key_2` indexes, and deploying the reader builds that use them. Rehearse on the local stack first (section "Rehearsal results"), then run the production section.

Files:
- Migration: `dsg_src/sql/2026-10-28-pente-move-private.sql`
- Verify (read-only): `dsg_src/sql/2026-10-28-pente-move-private-verify.sql`

Servers:
- Primary: `debian@pente.org`
- Replica: `wire.submanifold.be`

## Rules for every step

- Run the migration on the **primary only**. The replica receives it by replication. Never apply it to the replica by hand.
- A step passes only when its stated expectation holds. If a step fails or its expectation does not hold, stop and report. Do not continue to the next step.
- The verify file is read-only and safe on either server.

## Placeholder: `<migration start time>`

The verify file has a literal `<migration start time>` placeholder in query F. Before running query F, the operator must replace it with the timestamp recorded at step 2/3 (taken before migration section 2 runs), in the form `'YYYY-MM-DD HH:MM:SS'`. Do not leave the placeholder in the query.

## Expectation for verify query A

Query A expects `COLUMN_DEFAULT` = `'Y'` (fail-closed default). That default is set only by migration section 4. Section 1 sets the default to `'N'`. So query A shows `'Y'` only when run **after** section 4 has completed. If query A shows `'N'` after section 4, stop and report.

## Pre-checks (both servers)

1. `SELECT @@sql_mode;` — no zero-date strict modes.
2. Free space: about 5 GB (gigabytes) in the datadir and about 3 GB in the tmpdir. The ALTER copies the table.
3. Disable the `dockerReplica` / `backupDBdocker` backup job.
4. Confirm a recent backup exists.

## Primary migration

1. Build and stage the **writer build** (Task 2). Do not release the reader builds (Tasks 3–5) yet.
2. Stop the `pente.org` app container (plan D2). Record the start time (for the query F placeholder).
3. Record the highest game id: `SELECT MAX(gid) FROM pente_game;` (needed for the rollback repair in step (b) below).
4. Apply the migration on the primary, with `dsg` as the default database:
   `docker compose -f docker-compose.yml exec -T main_db sh -c 'mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" dsg' < dsg_src/sql/2026-10-28-pente-move-private.sql`
   - Watch `SHOW PROCESSLIST;` for `Repair by sorting`. `Repair with keycache` is far slower; if it appears, note it in the report.
   - Record the duration of section 1 (ALTER), section 2 (private-game backfill), section 3 (orphan backfill), and section 4 (default).
   - Expectation: each section completes with no error. Section 4 (`ALGORITHM=INSTANT`) fails loudly if it cannot run as instant. In that case stop and report; do not remove the clause.
5. Run the verify file on the primary. Expectation: every query matches its label in the file. Query A shows default `'Y'` (section 4 has run). Query C = 0. Query D = 0. Query E matches the expected distribution. Query F = 0 after the placeholder is replaced. Query G shows `Using index`, with no `Using temporary`, `Using filesort`, or `pente_game` join.

## Downtime ends here

6. Sync the **writer build** (Task 2) and start the `pente.org` container. Downtime ends here.
   - Every new move row now carries an explicit flag. The default is `'Y'`.
   - Old readers keep working on the replica, since they never name `private`.
   - The replica starts lagging during step 4, once the primary's ALTER is binlogged. Replication waits behind it. This is the **stale period**: the replica lags the primary until replay finishes. Announce it to users before step 4.
7. Watch the replica with `SHOW ALL SLAVES STATUS;` until `Slave_SQL_Running: Yes`, no error, and `Seconds_Behind_Master = 0`. Record how long the replay took.
8. Run the verify file on the replica. The `private` counts must match the primary's step 5 counts, plus any games stored since.
   - Query F (play_date guard) is primary-only: it was checked at step 5. Do not expect 0 on the replica, because new games exist.
   - Queries C, D and E scan ~36M (million) rows each: minutes of load on a replica serving live traffic. Run them at low traffic.
9. Store a test game (or wait for a real one). Check that its move rows carry the game's `private` value on both servers.
10. Deploy the reader builds (Tasks 3–5) with `sync_gameServer.sh` (no `web.xml` change). Only now do readers query the replica, which must have the column.
11. Re-enable the backup job.
12. Time `/api/db/position-stats` and `/api/db/games/search` on Pente's first load. Target: under 0.5 s (seconds) each, against 2.3–3.8 s before.

## Announcements and downtime

- Downtime = primary migration + verify (steps 2–5).
- Announce the stale period before step 4: the replica lags from step 4 (once the ALTER is binlogged) until replay finishes (step 7). Size it from the rehearsal replay duration.

## Rollback

(a) **Readers only** (Tasks 3–5): redeploy the previous reader classes. Nothing else changes.

(b) **Writer too:**
1. On the primary, first run: `ALTER TABLE pente_move ALTER COLUMN private SET DEFAULT 'N', ALGORITHM=INSTANT;` (it replicates).
2. Redeploy the previous writer.
3. Repair any public game stored between the two steps, on the primary (it replicates as rows):
   `UPDATE pente_move m JOIN pente_game g ON g.gid = m.gid SET m.private = 'N', m.play_date = m.play_date WHERE g.private = 'N' AND m.private = 'Y' AND m.gid > <max gid recorded at step 3>;`

(c) **Schema rollback** (optional; costs another full table copy). Apply the same way as the migration: primary only, app stopped, replica replays it.
`ALTER TABLE pente_move DROP INDEX hash_key, ADD INDEX hash_key (hash_key, move_num, game, next_move, rotation, winner), DROP INDEX hash_key_2, ADD INDEX hash_key_2 (hash_key, move_num, game, play_date), DROP COLUMN private;`

## Rehearsal results (local stack)

Fill in during the owner's local rehearsal. Leave blank until measured.

- ALTER duration (section 1): ________
- Backfill duration (sections 2 + 3): ________
- Section 4 (`ALGORITHM=INSTANT`) outcome: ________
- Replica replay duration: ________
- Verify results (A–G): ________

The rehearsal **must confirm that section 4's `ALGORITHM=INSTANT` succeeds on MyISAM** (the table engine assumed here). If it fails, stop and decide before production. Do not drop the clause to force it through.

Use the rehearsal durations to size the production downtime window and the stale period above.
