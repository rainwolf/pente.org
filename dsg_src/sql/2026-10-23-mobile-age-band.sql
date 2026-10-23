-- Mobile age band: age category reported by the apps (Apple Declared Age Range / Google Play Age Signals)
-- Apply to live + replica DBs before deploying the classes. Idempotent. NULL = unknown.

ALTER TABLE dsg_player ADD COLUMN IF NOT EXISTS mobile_age_band enum('UNDER_13','13_15','16_17','18_PLUS') NULL DEFAULT NULL;
