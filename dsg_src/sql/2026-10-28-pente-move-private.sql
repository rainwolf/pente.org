-- pente_move private flag and covering indexes
--
-- Purpose: copy the private flag from pente_game onto every pente_move row
-- and rebuild hash_key / hash_key_2 so web-database position queries can be
-- answered from pente_move alone (index-only, no pente_game join).
--
-- Apply on PRIMARY only, as `mariadb ... dsg < file`, so it replicates.
-- NEVER apply on the replica (runbook in docs/pente-move-private-rollout.md).
-- Stop the pente.org app container first (plan D2).
-- Not re-runnable as a whole: section 1 rebuilds the table.
-- Sections 2-3 are idempotent.
-- The verify queries in 2026-10-28-pente-move-private-verify.sql join all
-- ~36M rows: they take minutes and are run during the window, not on live traffic.
--
-- Sections, in order:
--   1. ALTER: add column, rebuild both indexes (one table copy).
--   2. Backfill private games (UPDATE, replicates as row events).
--   3. Backfill orphan move rows (no matching pente_game row).
--   4. Fail-closed default 'Y' (metadata only; INSTANT fails loudly rather than copy).

-- 1. Add column + rebuild indexes (single ALTER, single table copy)
ALTER TABLE pente_move ADD COLUMN IF NOT EXISTS private ENUM('Y','N') NOT NULL DEFAULT 'N' AFTER winner,
  DROP INDEX hash_key,
  ADD INDEX hash_key (hash_key, move_num, game, private, next_move, rotation, winner),
  DROP INDEX hash_key_2,
  ADD INDEX hash_key_2 (hash_key, move_num, game, private, play_date, gid);

-- 2. Backfill private games. play_date is set to itself so the ON UPDATE
--    current_timestamp() does not overwrite the move timestamp.
UPDATE pente_move m JOIN pente_game g ON g.gid = m.gid
  SET m.private = 'Y', m.play_date = m.play_date
  WHERE g.private = 'Y' AND m.private = 'N';

-- 3. Backfill orphan move rows (pente_move rows with no pente_game row).
--    Fail closed: treat them as private so they never reach public stats.
UPDATE pente_move m LEFT JOIN pente_game g ON g.gid = m.gid
  SET m.private = 'Y', m.play_date = m.play_date
  WHERE g.gid IS NULL AND m.private = 'N';

-- 4. Fail-closed default for future inserts. Metadata only.
--    ALGORITHM=INSTANT: if it fails, stop and report; do not drop the clause.
ALTER TABLE pente_move ALTER COLUMN private SET DEFAULT 'Y', ALGORITHM=INSTANT;
