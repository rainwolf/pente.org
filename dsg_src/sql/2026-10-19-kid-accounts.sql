-- Kid accounts: parent link, chat whitelist, copy settings, link requests
-- Apply to live + replica DBs before deploying the classes. Idempotent.
--
-- dsg_player.parent_pid: pid of the parent, NULL = not a kid account.

ALTER TABLE dsg_player ADD COLUMN IF NOT EXISTS parent_pid bigint(20) unsigned DEFAULT NULL;
ALTER TABLE dsg_player ADD INDEX IF NOT EXISTS parent_pid_key (parent_pid);

CREATE TABLE IF NOT EXISTS dsg_kid_whitelist (
  kid_pid bigint(20) unsigned NOT NULL,
  allowed_pid bigint(20) unsigned NOT NULL,
  last_update_date datetime NOT NULL,
  UNIQUE KEY kid_allowed (kid_pid, allowed_pid)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dsg_kid_settings (
  kid_pid bigint(20) unsigned NOT NULL,
  copy_messages char(1) NOT NULL DEFAULT 'N',
  copy_live_chat char(1) NOT NULL DEFAULT 'N',
  PRIMARY KEY (kid_pid)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dsg_kid_link_request (
  code varchar(64) NOT NULL,
  parent_pid bigint(20) unsigned NOT NULL,
  kid_pid bigint(20) unsigned NOT NULL,
  creation_date datetime NOT NULL,
  PRIMARY KEY (code),
  KEY kid_pid_key (kid_pid)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;
