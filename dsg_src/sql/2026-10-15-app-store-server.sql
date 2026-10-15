-- App Store Server API and Notifications V2 (docs/superpowers/specs/2026-10-15-ios-app-store-server-design.md §4.2).
-- Apply once, before deploying the classes that use these tables.

-- One row per subscription (keyed by originalTransactionId) or per consumable transaction (its transactionId).
CREATE TABLE dsg_subscribers_apple (
  original_transaction_id VARCHAR(64) NOT NULL,
  pid BIGINT(20) UNSIGNED NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  latest_transaction_id VARCHAR(64) DEFAULT NULL,
  expiry_time DATETIME DEFAULT NULL,
  state VARCHAR(32) NOT NULL,
  environment VARCHAR(16) NOT NULL,
  token_stamped TINYINT(1) NOT NULL DEFAULT 0,
  created DATETIME NOT NULL,
  updated DATETIME NOT NULL,
  PRIMARY KEY (original_transaction_id),
  KEY pid (pid),
  KEY state_expiry (state, expiry_time)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

-- Notification UUIDs already handled, so retries and history replays are idempotent.
CREATE TABLE dsg_apple_notifications (
  notification_uuid CHAR(36) NOT NULL,
  notification_type VARCHAR(48) NOT NULL,
  received DATETIME NOT NULL,
  PRIMARY KEY (notification_uuid),
  KEY received (received)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;

-- High-water mark for the daily notification-history replay, one row per environment.
CREATE TABLE dsg_apple_poll_state (
  environment VARCHAR(16) NOT NULL,
  history_until DATETIME NOT NULL,
  PRIMARY KEY (environment)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;
