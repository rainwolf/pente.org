-- Google Play Billing (docs/superpowers/specs/2026-10-11-android-play-billing-design.md §4.2).
-- Apply once, before deploying the classes that use these tables.

-- Play order ids are about 27 characters and the promo-code fallback key is "GP." + 61 hex chars.
ALTER TABLE dsg_subscribers MODIFY transactionid VARCHAR(64) NOT NULL;

-- One row per Play purchase token. created is set once and drives the pollers' "younger than 3 days".
CREATE TABLE dsg_subscribers_android (
  purchase_token_hash CHAR(64) NOT NULL,
  purchase_token TEXT NOT NULL,
  pid BIGINT(20) UNSIGNED NOT NULL,
  product_id VARCHAR(64) NOT NULL,
  latest_successful_order_id VARCHAR(64) DEFAULT NULL,
  linked_purchase_token_hash CHAR(64) DEFAULT NULL,
  expiry_time DATETIME DEFAULT NULL,
  state VARCHAR(64) NOT NULL,
  acknowledged TINYINT(1) NOT NULL DEFAULT 0,
  created DATETIME NOT NULL,
  updated DATETIME NOT NULL,
  PRIMARY KEY (purchase_token_hash),
  KEY pid (pid),
  KEY expiry_time (expiry_time),
  KEY latest_successful_order_id (latest_successful_order_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4;
