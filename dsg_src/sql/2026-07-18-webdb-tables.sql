CREATE TABLE `webdb_analysis` (
  `aid`     bigint NOT NULL AUTO_INCREMENT,
  `pid`     bigint unsigned NOT NULL,
  `name`    varchar(100) NOT NULL,
  `game`    smallint NOT NULL,
  `tree`    mediumtext NOT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`aid`),
  KEY `idx_pid` (`pid`,`updated`)
);

CREATE TABLE `webdb_game` (
  `wgid`      bigint NOT NULL AUTO_INCREMENT,
  `pid`       bigint unsigned NOT NULL,
  `game`      smallint NOT NULL,
  `player1`   varchar(64) NOT NULL,
  `player2`   varchar(64) NOT NULL,
  `winner`    smallint NOT NULL,
  `site`      varchar(128) DEFAULT NULL,
  `event`     varchar(128) DEFAULT NULL,
  `round`     varchar(32)  DEFAULT NULL,
  `section`   varchar(32)  DEFAULT NULL,
  `play_date` timestamp NULL DEFAULT NULL,
  `imported`  timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`wgid`),
  KEY `idx_owner` (`pid`,`game`,`imported`)
);

CREATE TABLE `webdb_move` (
  `wgid`      bigint NOT NULL,
  `move_num`  smallint NOT NULL,
  `next_move` smallint NOT NULL,
  `hash_key`  bigint NOT NULL,
  `rotation`  smallint NOT NULL,
  `game`      smallint NOT NULL,
  `winner`    smallint NOT NULL,
  `pid`       bigint unsigned NOT NULL,
  PRIMARY KEY (`wgid`,`move_num`),
  KEY `idx_stats` (`pid`,`hash_key`,`move_num`,`game`,`next_move`,`rotation`,`winner`)
);
