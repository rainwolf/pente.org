-- Private message reply link
-- Apply to live + replica DBs before deploying the classes. Idempotent.
--
-- reply_to_mid: mid of the dsg_message this one replies to, NULL = not a reply.

ALTER TABLE dsg_message
    ADD COLUMN IF NOT EXISTS reply_to_mid int(10) unsigned DEFAULT NULL;
