-- Kid TB copy: per-kid flag to copy turn-based game messages to the parent
-- Apply to live + replica DBs before deploying the classes. Idempotent.

ALTER TABLE dsg_kid_settings ADD COLUMN IF NOT EXISTS copy_tb_messages char(1) NOT NULL DEFAULT 'N';
