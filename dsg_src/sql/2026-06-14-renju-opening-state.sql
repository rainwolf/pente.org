-- Renju Taraguchi-10 opening-state storage
-- Apply to live + replica DBs. Idempotent (MariaDB IF NOT EXISTS).
-- Spec: docs/superpowers/specs/2026-06-13-renju-tb-persistence-design.md
--
-- renju_swaps : base-3 packed word of the six Taraguchi decisions
--               (swaps after moves 1-4, branch choice, swap after move 5),
--               each digit 0=pending / 1=no(/branch A) / 2=yes(/branch B).
--               Range 0..728, NULL = not a Renju game.
-- renju_offers: the 10 offered 5th moves (Branch B), one unsigned byte each
--               (15x15 board position 0..224), NULL until offered.

-- Turn-based games.
ALTER TABLE tb_game
    ADD COLUMN IF NOT EXISTS renju_swaps  smallint(5) unsigned DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS renju_offers varbinary(10)        DEFAULT NULL;

-- AI turn-based games are loaded via the same TB_COLUMNS select against
-- tb_game_ai (MySQLTBGameStorer), so it needs the columns too or every AI
-- game load fails with "unknown column".
ALTER TABLE tb_game_ai
    ADD COLUMN IF NOT EXISTS renju_swaps  smallint(5) unsigned DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS renju_offers varbinary(10)        DEFAULT NULL;

-- Live games (written at game-over). Offers for live games live in a side table.
ALTER TABLE pente_game
    ADD COLUMN IF NOT EXISTS renju_swaps smallint(5) unsigned DEFAULT NULL;

CREATE TABLE IF NOT EXISTS pente_renju_offer (
    `gid`       bigint(20) unsigned NOT NULL DEFAULT 0,
    `site_id`   smallint(5) unsigned NOT NULL DEFAULT 0,
    `offer_num` tinyint(3) unsigned NOT NULL DEFAULT 0,
    `move`      smallint(5) unsigned NOT NULL DEFAULT 0,
    PRIMARY KEY (`gid`, `site_id`, `offer_num`)
) ENGINE=MyISAM;
