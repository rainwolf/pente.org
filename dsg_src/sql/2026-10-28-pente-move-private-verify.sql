-- Verify pente_move private flag and covering indexes
--
-- Read-only. Safe on either server. Run after 2026-10-28-pente-move-private.sql.
-- Each query is labelled with its expected result.
-- The row-joining queries scan all ~36M pente_move rows: they take minutes.
-- Run during the migration window, not on live traffic.

-- A. Column shape. Expected: private enum('Y','N') NOT NULL DEFAULT 'Y',
--    positioned after winner.
SELECT ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pente_move'
  ORDER BY ORDINAL_POSITION;

-- B. Index shape. Expected, in this order:
--    hash_key: hash_key, move_num, game, private, next_move, rotation, winner
--    hash_key_2: hash_key, move_num, game, private, play_date, gid
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pente_move'
  ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- C. Flag agrees with pente_game. Expected: 0
SELECT COUNT(*) FROM pente_move m JOIN pente_game g ON g.gid = m.gid
  WHERE m.private <> g.private;

-- D. No orphan rows left non-private. Expected: 0
SELECT COUNT(*) FROM pente_move m LEFT JOIN pente_game g ON g.gid = m.gid
  WHERE g.gid IS NULL AND m.private <> 'Y';

-- E. Distribution. Expected (2026-10-28 replica data): Y 839,422
--    (839,332 + 90 orphan rows), N 35,333,856
SELECT m.private, COUNT(*) FROM pente_move m GROUP BY m.private;

-- F. play_date guard. Expected: 0 for rows touched by the backfill.
--    Replace <migration start time> with the timestamp recorded before
--    section 2 ran. Only games stored at migration start may have changed,
--    and the backfill must not have changed play_date.
SELECT COUNT(*) FROM pente_move WHERE play_date >= '<migration start time>';

-- G. Index-only plans (run EXPLAIN on the webdb position query shapes).
--    Expected: Using index; no Using temporary; no Using filesort; no
--    pente_game join. Hash and move values are the game-1 position check.
EXPLAIN SELECT m.next_move, m.rotation, m.winner, COUNT(*)
  FROM pente_move m
  WHERE m.hash_key = -4230528974382094949 AND m.move_num = 0 AND m.game = 1
    AND m.private = 'N'
  GROUP BY m.next_move, m.rotation, m.winner;

EXPLAIN SELECT m.gid
  FROM pente_move m
  WHERE m.hash_key = -4230528974382094949 AND m.move_num = 0 AND m.game = 1
    AND m.private = 'N'
  ORDER BY m.play_date DESC, m.gid DESC
  LIMIT 50;

EXPLAIN SELECT COUNT(*)
  FROM pente_move m
  WHERE m.hash_key = -4230528974382094949 AND m.move_num = 0 AND m.game = 1
    AND m.private = 'N';
