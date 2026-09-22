# Jive Forums latin1 → utf8mb4 migration plan

**Status:** plan only. Nothing in this document has been executed against `dsg`.
**Written:** 2026-09-22, measured against the local Docker stack (`penteorg-main_db-1`,
MariaDB 13.0.2). Production was never contacted.
**Follows from:** [`docs/forum-edit-revert-rca.md`](forum-edit-revert-rca.md). Read that first;
this plan implements its "Recommended follow-up" and closes its remaining defects 1 and 4.

> **Scope note.** Every number below was measured with the query shown. Read-only queries and
> `EXPLAIN` only, plus temporary `_tmp_*` clone tables that were created, measured and dropped
> (§3.6 lists exactly what was created; `information_schema` confirms 0 leftovers and `dsg` is
> back to its original 80 base tables).

---

## 1. Why this migration, in one paragraph

`jiveMessage.body` is `text` on a **MyISAM latin1** table. MariaDB's `latin1` **is cp1252**, and
`sql_mode` contains `STRICT_TRANS_TABLES`, so any character outside cp1252 makes the `UPDATE`
fail with **ERROR 1366** rather than truncate. `DbForumMessage.saveToDb` swallows that exception
while `setBody` still populates the 6-hour `messageCache` — so an edit appears to apply, then
"reverts" hours later. The one-line `\p{Zs}` fold already applied to
`dsg_src/httpdocs/gameServer/forums/editform.jsp` removed the single U+202F that every edit was
injecting. It does not close two holes:

1. **12 live player names cannot be encoded in cp1252** (CJK, Korean, Greek, one Cyrillic
   homoglyph). The editor's username is interpolated into the edit comment, so under a
   UTF-8-served forum those users can never edit a post with "add following text" checked.
2. **265 of 19,342 bodies and 57 subjects already contain cp1252 high bytes** (paste-from-Word
   `0x95 •`, `0x92 ’`, `0x85 …`). Depending on the served charset these degrade to `?` or make
   Tomcat reject the whole POST with HTTP 400.

Both disappear when the columns hold utf8mb4.

### Verified: the failure and the fix, side by side

```sql
-- latin1 clone, STRICT_TRANS_TABLES, real U+202F bytes:
UPDATE _tmp_l1_jiveMessage SET body=CONCAT('8:32',CONVERT(UNHEX('E280AF') USING utf8mb4),'AM');
-- ERROR 1366 (22007): Incorrect string value: '\xE2\x80\xAFAM' for column ...body at row 1

-- same table after CONVERT TO CHARACTER SET utf8mb4, same sql_mode:
-- succeeds; SELECT HEX(body) -> 383A3332E280AF414D   (U+202F preserved)
```

A single insert carrying U+202F **and** an emoji **and** a curly quote **and** a CJK username
**and** Greek also succeeded under `STRICT_TRANS_TABLES`:

```
Message was edited by: Q冰艳 at Sep 22, 2026, 8:32 AM — it’s ok 🎲 χρώμα
```

---

## 2. Exact inventory

### 2.1 Whole-database picture

```sql
SELECT SUBSTRING_INDEX(TABLE_COLLATION,'_',1) cs, ENGINE, COUNT(*) n
FROM information_schema.TABLES
WHERE TABLE_SCHEMA='dsg' AND TABLE_TYPE='BASE TABLE' GROUP BY 1,2;
```

| charset | engine | tables |
|---|---|---|
| latin1 | MyISAM | **70** |
| utf8mb4 | MyISAM | 6 |
| utf8mb4 | InnoDB | 4 |

All 10 existing utf8mb4 tables are **`utf8mb4_general_ci`** (`player`, `player`'s `name` column
is `utf8mb4_bin`, `dsg_message`, `tb_message`, `koth`, `dsg_goodies`, `pente_renju_offer`,
`dsg_email_verification`, `webdb_*`). The **server default is `utf8mb4_uca1400_ai_ci`** —
see §2.5, this matters.

Server facts:

```
VERSION()                13.0.2-MariaDB-ubu2604-log
@@sql_mode               STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION
@@character_set_server   utf8mb4
@@collation_server       utf8mb4_uca1400_ai_ci
log_bin                  ON        binlog_format  ROW      server_id 1
max_allowed_packet       16777216 (16 MiB — mebibytes)
key_buffer_size          1073741824 (1 GiB)
datadir                  /var/lib/mysql/
```

### 2.2 The 24 tables this plan migrates — all `jive*`, all MyISAM latin1_swedish_ci

Row counts and sizes from `information_schema.TABLES`
(`ROUND(DATA_LENGTH/1048576,2)`, `ROUND(INDEX_LENGTH/1048576,2)`):

| table | rows | data MiB | index MiB | CHAR/VARCHAR/TEXT columns |
|---|---:|---:|---:|---|
| jiveAttachment | 272 | 0.02 | 0.01 | fileName vc255, contentType vc50, creationDate vc15, modificationDate vc15 |
| jiveAttachmentProp | 0 | 0.00 | 0.00 | name vc100, propValue text |
| jiveCategory | 4 | 0.00 | 0.00 | name vc255, description text, creationDate vc15, modificationDate vc15 |
| jiveCategoryProp | 0 | 0.00 | 0.00 | name vc100, propValue text |
| jiveForum | 29 | 0.00 | 0.00 | name vc255, description text, creationDate vc15, modificationDate vc15 |
| jiveForumProp | 0 | 0.00 | 0.00 | name vc100, propValue text |
| jiveGroup | 1 | 0.00 | 0.00 | name vc50, description vc255, creationDate vc15, modificationDate vc15 |
| jiveGroupPerm | 0 | 0.00 | 0.00 | *(none — numeric only)* |
| jiveGroupProp | 0 | 0.00 | 0.00 | name vc100, propValue text |
| jiveGroupUser | 0 | 0.00 | 0.00 | *(none)* |
| jiveID | 7 | 0.00 | 0.00 | *(none)* |
| **jiveMessage** | **19,342** | **11.24** | **2.43** | **subject vc255, body text**, creationDate vc15, modificationDate vc15 |
| jiveMessageProp | 3,221 | 0.11 | 0.05 | name vc100, propValue text |
| jiveModeration | 6 | 0.00 | 0.00 | modDate vc15 |
| jiveReadTracker | 2,124 † | 0.09 | 0.07 | readDate vc15 |
| jiveReward | 0 | 0.00 | 0.00 | creationDate vc15 |
| jiveThread | 2,984 | 0.19 | 0.30 | creationDate vc15, modificationDate vc15 |
| jiveThreadProp | 3,128 | 0.08 | 0.06 | name vc100, propValue text |
| jiveUser | **1** | 0.00 | 0.01 | username vc30, passwordHash vc32, name vc100, email vc100, creationDate vc15, modificationDate vc15 |
| jiveUserPerm | 1,344 | 0.03 | 0.04 | *(none)* |
| **jiveUserProp** | **274,037** | **10.63** | **14.16** | name vc100, propValue text |
| jiveUserReward | 0 | 0.00 | 0.00 | *(none)* |
| jiveUserRoster | 0 | 0.00 | 0.00 | *(none)* |
| **jiveWatch** | **231,885** | **7.30** | **18.47** | *(none)* |

Total on disk: `du -ch /var/lib/mysql/dsg/jive*.MY[DI]` → **66 MiB**.

> † **Row counts drift — this is a live system.** `jiveReadTracker` read 2,259 when this table was
> first compiled and **2,124** hours later. Treat every count here as an order-of-magnitude
> illustration, never as a gate. §7 step 5 captures the real "before" values and §9 diffs against
> those. (`jiveMessage`, `jiveThread`, `jiveUserProp`, `jiveWatch`, `jiveMessageProp`,
> `jiveThreadProp`, `jiveUserPerm` and `jiveAttachment` were all stable across the same interval.)

Seven of the 24 (`jiveGroupPerm`, `jiveGroupUser`, `jiveID`, `jiveUserPerm`, `jiveUserReward`,
`jiveUserRoster`, `jiveWatch`) have **no character columns at all**, confirmed by:

```sql
SELECT t.TABLE_NAME FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA='dsg' AND t.TABLE_COLLATION LIKE 'latin1%'
  AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS c
                  WHERE c.TABLE_SCHEMA='dsg' AND c.TABLE_NAME=t.TABLE_NAME
                    AND c.CHARACTER_SET_NAME IS NOT NULL);
```

**They are still included.** Rationale: converting them is a data no-op, costs under a second
(§6), and without it any future `ALTER TABLE jiveWatch ADD COLUMN … varchar(…)` silently gets
latin1 again. Their rollback is trivial because no byte of data changes. I considered using the
cheaper `ALTER TABLE … DEFAULT CHARACTER SET utf8mb4` for these — **measurement says don't
bother**: on MyISAM it is *not* metadata-only (`jiveWatch` took 793 ms, indistinguishable from
the 778 ms full `CONVERT TO`). Use one uniform statement form.

No `jive*` column carries a per-column charset override:

```sql
SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dsg'
  AND TABLE_NAME LIKE 'jive%' AND CHARACTER_SET_NAME IS NOT NULL AND CHARACTER_SET_NAME<>'latin1';
-- 0
```

### 2.3 Tables this plan does NOT migrate, and why

**46 non-jive latin1 tables.** Grouped by reason:

**(a) No character columns at all — nothing to convert, huge to rewrite.** `pente_move`
(36,131,577 rows, 1,034 MiB data + 2,565 MiB index), `tb_move` (19,083,896 rows), `tb_move_ai`,
`tb_emergency_time`, `dsg_followers`, `dsg_koth`, `dsg_server_access`, `dsg_server_game`,
`dsg_tournament`, `dsg_tournament_admin`, `dsg_tournament_player`,
`dsg_tournament_restriction`, `dsg_vacation*`, `speed_mapping`, `speed_mapping1`, `temp`,
`temp_tb`. Converting `pente_move` alone would rewrite 3.6 GiB for zero benefit.

**(b) Character columns that only ever hold ASCII — measured, not assumed.** Scanned with
`SUM(OCTET_LENGTH(CONVERT(col USING utf8mb4)) > OCTET_LENGTH(col))` — no regex, so no
escaping trap (see Appendix B):

| table.column | rows | non-ASCII rows |
|---|---:|---:|
| dsg_player.homepage | 59,287 | 0 |
| dsg_player.timezone | 59,287 | 0 |
| dsg_player_prefs.pref_name | 26,953 | 0 |
| dsg_server.name | 46 | 0 |
| dsg_server_message.message | 88 | 0 |
| tb_set.cancel_msg | 383,467 | 0 |
| tb_set_ai.cancel_msg | 32,053 | 0 |
| pente_game.round / .section | 1,263,725 | 0 / 0 |
| notifications.token / notifications_android.token | 149 / 74 | 0 / 0 |
| dsg_subscribers_ios.receipt | 223 | 0 |
| dsg_player_avatar.content_type | 289 | 0 |
| dsg_ip.ip | 0 | 0 |

Plus the pure `char(1)` / `enum(...)` status flags across `dsg_live_set`, `dsg_donation`,
`dsg_player_game`, `dsg_player_ignore`, `dsg_tournament_*`, `tb_game`, `tb_game_ai`, `tb_set*`,
`pente_game`, `game_site`, `spam_*`.

**(c) Has non-ASCII user text but is out of scope for the forum fix — documented follow-up.**

| table.column | rows | non-ASCII rows |
|---|---:|---:|
| dsg_player.location | 59,287 | **56** |
| dsg_player.email | 59,287 | **7** |
| dsg_player.note | 59,287 | **3** |
| dsg_return_email.email | 79,559 | **5** |
| game_event.name | 1,075 | **7** |

These are real latin1-trapped user text and will eventually want the same treatment, but they are
not on the forum-edit path and widening this migration to cover them would break the "deliver
what was asked" boundary and enlarge the blast radius. **Track them separately.**

> ### ⚠️ `dsg_player.note` needs a DIFFERENT test — the escape-free form lies about it
>
> It is the **only** column in the entire database with a per-column charset override
> (`varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci` inside a latin1 table), verified
> with:
>
> ```sql
> SELECT c.TABLE_NAME, c.COLUMN_NAME, c.CHARACTER_SET_NAME
> FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
>   ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
> WHERE c.TABLE_SCHEMA='dsg' AND t.TABLE_COLLATION LIKE 'latin1%'
>   AND c.CHARACTER_SET_NAME IS NOT NULL AND c.CHARACTER_SET_NAME<>'latin1';
> -- exactly one row: dsg_player | note | utf8mb3
> ```
>
> **utf8mb3 and utf8mb4 encode every BMP character identically**, so `CONVERT(note USING utf8mb4)`
> is length-preserving and the standard probe silently returns **0**:
>
> | test | result | correct? |
> |---|---:|---|
> | `SUM(OCTET_LENGTH(CONVERT(note USING utf8mb4)) <> OCTET_LENGTH(note))` | **0** | ✗ false negative |
> | `SUM(OCTET_LENGTH(note) > CHAR_LENGTH(note))` | **3** | ✓ |
>
> **Rule: `OCTET_LENGTH(CONVERT(col USING utf8mb4)) <> OCTET_LENGTH(col)` detects non-ASCII only in
> a latin1 column. For a column that is already Unicode, use `OCTET_LENGTH(col) > CHAR_LENGTH(col)`.**
> This is the same distinction that makes §9.2(b) tautological after the ALTER. A future
> `CONVERT TO` on `dsg_player` promotes `note` to utf8mb4 losslessly.

`tb_set_ai.cancel_msg` is listed as 0 non-ASCII, but strictly it is **100 % NULL** (32,053 of
32,053), so the aggregate returns NULL rather than 0. Same conclusion, stated precisely.

**(d) `spam_messages`, `spam_threads`, `spam_users` — optional add.** Forum-adjacent (they hold
`messageID` / `threadID` / `pid` only) and each is a single `bigint(20)` column, so a `CONVERT TO`
is a pure no-op. Include them only if you want the whole forum subsystem uniform; skipping them
costs nothing today.

### 2.4 The `jiveUser` vs `player` split — read this before assuming anything

`jiveUser` holds **exactly one row**: `userID=1, username='admin', name='Administrator',
email='admin@yoursite.com'`. It is vestigial. Real authentication goes through
`org.pente.jive.DSGAuthFactory`, which queries `player` + `dsg_player` directly:

```sql
select player.pid from player, dsg_player
 where player.pid = dsg_player.pid and player.name = ? and dsg_player.status='A'
   and dsg_player.password = ?
```

`player` is **already utf8mb4** (`player.name varchar(100) CHARACTER SET utf8mb4 COLLATE
utf8mb4_bin`). So:

- The 12 non-cp1252 names are **already stored correctly**; the migration does not move them.
- `jiveMessage.userID` joins `player.pid` — numeric, so no collation interaction.
  Verified: 19,342 messages, 719 distinct authors, **0 orphans**.
- The only Java SQL touching a converted string column is
  `dsg_src/java/org/pente/jive/SimpleUserAdapter.java:42,44`
  (`SELECT name, propValue FROM jiveUserProp WHERE userID=?` /
  `DELETE FROM jiveUserProp WHERE userID=? AND name=?`) — bound parameters over a utf8mb4 JDBC
  connection, unaffected.
- A grep for cross-charset *string* joins between `jive*` and `player` found **none**.

**Nuance worth recording:** none of the 12 non-cp1252-named players has ever authored a forum
message:

```sql
SELECT COUNT(*) FROM jiveMessage m JOIN player p ON p.pid=m.userID
WHERE CONVERT(CONVERT(p.name USING latin1) USING utf8mb4) <> p.name;   -- 0
```

So defect 1 is **latent**, not currently firing: it bites the moment one of them posts, or a
moderator with such a name edits anyone's post. Five of the twelve have a `dsg_player` row with
`status='A'` and 0–5 logins; the other seven belong to `site_id=4` (a different game site sharing
the `player` table) and have no `dsg_player` row at all. This lowers the *urgency* of defect 1
but not its correctness: the failure is silent when it happens.

The 12 names, for the verification step in §9:

```
40000000003252 Q冰艳      40000000003258 连珠        40000000003266 二流六子棋
40000000003274 双线棋     40000000003282 五子六子    40000000003285 五子棋520
40000000003302 Viсtoria (Cyrillic с U+0441)         23000000036201 q冰艳qby
23000000042845 조수민병신 23000000043288 나바보아니야
23000000045850 χαρμολύπη  23000000046029 χρώμα
```

Query that produced them (the round-trip test is the definition of "not cp1252-encodable"):

```sql
SELECT pid, name, HEX(CONVERT(name USING utf8mb4)) FROM player
WHERE CONVERT(CONVERT(name USING latin1) USING utf8mb4) <> name;
```

### 2.5 Collation choice — must be explicit

`ALTER TABLE … CONVERT TO CHARACTER SET utf8mb4` **without** a `COLLATE` clause picks the server
default. Probed:

```sql
CREATE TABLE _tmp_c (a varchar(10)) ENGINE=MyISAM CHARSET=latin1;
ALTER TABLE _tmp_c CONVERT TO CHARACTER SET utf8mb4;
-- TABLE_COLLATION = utf8mb4_uca1400_ai_ci
```

That would introduce a **third** collation into `dsg` (alongside latin1_swedish_ci and the
utf8mb4_general_ci that all 10 existing utf8mb4 tables use), multiplying future
"Illegal mix of collations" surface. **Every statement in §7 therefore carries
`COLLATE utf8mb4_general_ci` explicitly.**

**But the explicit `COLLATE` only fixes these 24 tables.** The database default is already the
thing that mints uca1400:

```sql
SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='dsg';
-- utf8mb4 | utf8mb4_uca1400_ai_ci
```

So the next `CREATE TABLE` in `dsg` that omits `COLLATE` gets uca1400 regardless of this migration.
Two honest options — **this is a judgement call, not a measurement:**

- **Accept it** and rely on always writing `COLLATE` explicitly. Zero risk today.
- **Align the database default** by adding to step 6:
  `ALTER DATABASE dsg CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;`
  This is metadata-only and touches no table, but it changes the default for *everything* created
  in `dsg` afterwards — including future non-forum work. **Out of scope for a forum migration**, so
  it is not in the runbook; raise it as a separate decision.

Note this also undercuts §2.2's stated reason for converting the 8 character-column-free tables
("so a future `ADD COLUMN varchar` doesn't get latin1"): correct as far as it goes — the *table*
default would otherwise be latin1 — but the *database* default is a separate hole that converting
them does not close.

---

## 3. The MyISAM 1000-byte key-length trap

This is the step most likely to fail a migration halfway, so it is done two ways: computed
per index, then proven empirically.

### 3.1 The limit is real and it is 1000 bytes

```sql
CREATE TABLE _tmp_keylen  (a varchar(251) NOT NULL, PRIMARY KEY(a)) ENGINE=MyISAM CHARSET=utf8mb4;
-- ERROR 1071 (42000): Specified key was too long; max key length is 1000 bytes
CREATE TABLE _tmp_keylen2 (a varchar(250) NOT NULL, PRIMARY KEY(a)) ENGINE=MyISAM CHARSET=utf8mb4;
-- OK   (250*4 = 1000 exactly)
```

So under utf8mb4 a single MyISAM-indexed `varchar` tops out at **250 characters** — the limit
counts the 4×char payload and *not* the 2 varchar length bytes. The byte model in §3.2 does add
those 2 bytes per varchar, so it **over**-estimates by 2 bytes per key part. That is the safe
direction and it changes no conclusion (worst real key 410 vs a 1000-byte limit), but do not treat
§3.2's figures as exact to the byte. For contrast,
**InnoDB's limit is 3072 bytes** per index (DYNAMIC/COMPRESSED row format) and **767 bytes**
under COMPACT/REDUNDANT — different numbers, and irrelevant here because every `jive*` table is
MyISAM and stays MyISAM. Do not carry the 3072 figure over.

### 3.2 Computed key length for every index on every table being migrated

Model: each key part costs `chars × bytes_per_char` (+2 for `varchar` length bytes, +1 if
nullable); integers cost their storage width. Full query used:

```sql
SELECT s.TABLE_NAME, s.INDEX_NAME,
  GROUP_CONCAT(CONCAT(s.COLUMN_NAME, IF(s.SUB_PART IS NULL,'',CONCAT('(',s.SUB_PART,')')))
               ORDER BY s.SEQ_IN_INDEX) cols,
  SUM(CASE WHEN c.CHARACTER_MAXIMUM_LENGTH IS NULL
           THEN CASE c.DATA_TYPE WHEN 'bigint' THEN 8 WHEN 'int' THEN 4 ELSE 8 END
           ELSE IFNULL(s.SUB_PART,c.CHARACTER_MAXIMUM_LENGTH)*1 + IF(c.DATA_TYPE='varchar',2,0) END
      + IF(c.IS_NULLABLE='YES',1,0)) bytes_latin1,
  SUM(CASE WHEN c.CHARACTER_MAXIMUM_LENGTH IS NULL
           THEN CASE c.DATA_TYPE WHEN 'bigint' THEN 8 WHEN 'int' THEN 4 ELSE 8 END
           ELSE IFNULL(s.SUB_PART,c.CHARACTER_MAXIMUM_LENGTH)*4 + IF(c.DATA_TYPE='varchar',2,0) END
      + IF(c.IS_NULLABLE='YES',1,0)) bytes_utf8mb4
FROM information_schema.STATISTICS s
JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=s.TABLE_SCHEMA AND c.TABLE_NAME=s.TABLE_NAME AND c.COLUMN_NAME=s.COLUMN_NAME
WHERE s.TABLE_SCHEMA='dsg' AND s.TABLE_NAME LIKE 'jive%'
GROUP BY s.TABLE_NAME, s.INDEX_NAME ORDER BY bytes_utf8mb4 DESC;
```

All 57 indexes, widest first. **Every one is under 1000 bytes. Nothing needs a prefix index,
a shortened column, or a selective per-column conversion.**

| table | index | key parts | latin1 B | utf8mb4 B | headroom to 1000 |
|---|---|---|---:|---:|---:|
| jiveAttachmentProp | PRIMARY | attachmentID, name | 110 | **410** | 590 |
| jiveCategoryProp | PRIMARY | categoryID, name | 110 | **410** | 590 |
| jiveForumProp | PRIMARY | forumID, name | 110 | **410** | 590 |
| jiveGroupProp | PRIMARY | groupID, name | 110 | **410** | 590 |
| jiveMessageProp | PRIMARY | messageID, name | 110 | **410** | 590 |
| jiveThreadProp | PRIMARY | threadID, name | 110 | **410** | 590 |
| jiveUserProp | PRIMARY | userID, name | 110 | **410** | 590 |
| jiveUser | jiveUser_hash_idx | passwordHash | 34 | 130 | 870 |
| jiveUser | username (UNIQUE) | username | 32 | 122 | 878 |
| jiveGroup | jiveGroup_cDate_idx | creationDate | 17 | 62 | 938 |
| jiveMessage | jiveMessage_cDate_idx | creationDate | 17 | 62 | 938 |
| jiveMessage | jiveMessage_mDate_idx | modificationDate | 17 | 62 | 938 |
| jiveReward | jiveReward_creationDate_idx | creationDate | 17 | 62 | 938 |
| jiveThread | jiveThread_cDate_idx | creationDate | 17 | 62 | 938 |
| jiveThread | jiveThread_mDate_idx | modificationDate | 17 | 62 | 938 |
| jiveUser | jiveUser_cDate_idx | creationDate | 17 | 62 | 938 |
| jiveForum | jiveForum_name_idx | name(10) *(prefix)* | 12 | 42 | 958 |
| jiveGroup | jiveGroup_name_idx | name(10) *(prefix)* | 12 | 42 | 958 |
| jiveUser | jiveUser_username_idx | username(10) *(prefix)* | 12 | 42 | 958 |
| jiveWatch | PRIMARY | userID, objectID, objectType, watchType | 28 | 28 | — |
| jiveGroupUser | PRIMARY | groupID, userID, administrator | 20 | 20 | — |
| jiveReadTracker | PRIMARY | userID, objectType, objectID | 20 | 20 | — |
| jiveMessage | jiveMessage_forumID_modVal_idx | forumID, modValue | 16 | 16 | — |
| jiveThread | jiveThread_fID_mV_idx | forumID, modValue | 16 | 16 | — |
| jiveUserRoster | PRIMARY | userID, subUserID | 16 | 16 | — |
| jiveGroupPerm | jiveGroupPerm_object_idx | objectType, objectID | 12 | 12 | — |
| jiveUserPerm | jiveUserPerm_object_idx | objectType, objectID | 12 | 12 | — |
| jiveUserReward | PRIMARY | userID, rewardPoints | 12 | 12 | — |
| *(29 more)* | single `bigint`/`int` keys | — | 4–9 | 4–9 | — |

The remaining 29 are all single numeric keys at 4–9 bytes (`jiveMessage_userID_idx`,
`jiveMessage_threadID_idx`, `jiveMessage_forumID_idx`, `jiveMessage_modValue_idx`, every table's
`PRIMARY` on a `bigint`, `jiveWatch_*_idx`, `jiveModeration_*_idx`, `jiveReward_*_idx`,
`jiveAttachment_messageID_idx`, `jiveForum_cat_idx`, `jiveCategory_lft_idx/_rgt_idx`,
`jiveGroupPerm_groupID_idx`, `jiveUserPerm_userID_idx`, `jiveID.PRIMARY`). Charset is irrelevant
to them.

**Worst case is 410 bytes — 59% of the limit unused.** The 7 `*Prop` primary keys
(`bigint(20) NOT NULL` + `varchar(100) NOT NULL`) are the only thing even close: 8 + (100×4 + 2).

### 3.3 The computation model was validated against the server

`EXPLAIN` reports real key lengths on the live latin1 tables, and they match the model exactly:

```sql
EXPLAIN SELECT 1 FROM jiveMessage FORCE INDEX (jiveMessage_cDate_idx)
  WHERE creationDate='001173589387198';
-- key_len 17      (model: varchar(15) NOT NULL latin1 = 15*1 + 2 = 17)  ✓
EXPLAIN SELECT 1 FROM jiveMessage FORCE INDEX (jiveMessage_forumID_modVal_idx)
  WHERE forumID=1 AND modValue=1;
-- key_len 16      (model: 8 + 8 = 16)                                   ✓
```

### 3.4 Empirical proof: all 24 `ALTER`s were executed for real

The decisive test. For each of the 24 tables a schema-identical empty clone was made with
`CREATE TABLE _tmp_u8_<t> LIKE <t>` (which reproduces every index definition byte for byte), then
the exact production statement was run against it:

```sql
ALTER TABLE _tmp_u8_<t> CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**Result: 24 / 24 OK, zero errors.** Had any index exceeded 1000 bytes, MariaDB would have thrown
`ERROR 1071` — the same error §3.1 deliberately provoked. This is the strongest available evidence
that the DDL in §7 will not fail halfway.

### 3.5 `TEXT` byte-limit risk — and a surprise that removes it

The RCA checked `jiveMessage.body` the right way. Repeating it for **every** `text` column
being migrated (`MAX(OCTET_LENGTH(CONVERT(col USING utf8mb4)))` vs `TEXT`'s 65,535-byte cap):

| table.column | rows | max latin1 bytes | max utf8mb4 bytes | non-ASCII rows |
|---|---:|---:|---:|---:|
| jiveMessage.body | 19,342 | 33,752 | **33,752** | 265 |
| jiveMessageProp.propValue | 3,221 | 56 | 56 | 0 |
| jiveThreadProp.propValue | 3,128 | 7 | 7 | 0 |
| jiveUserProp.propValue | 274,037 | 16 | 16 | 0 |
| jiveForum.description | 29 | 158 | 158 | 0 |
| jiveCategory.description | 4 | 28 | 28 | 0 |
| jiveForumProp / jiveCategoryProp / jiveGroupProp / jiveAttachmentProp .propValue | 0 | — | — | — |

(`body`'s max is unchanged because the single longest row is pure ASCII; the 265 non-ASCII rows
grow but stay far shorter.)

**The surprise:** MariaDB does **not** keep `text` as `text` through `CONVERT TO`. It widens it:

```sql
-- before: `body` text
ALTER TABLE _tmp_u8_jiveMessage CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
SHOW CREATE TABLE _tmp_u8_jiveMessage;
-- after:  `body` mediumtext   CHARACTER SET utf8mb4
```

It widens **all ten** `text` columns to `mediumtext` (`CHARACTER_MAXIMUM_LENGTH` 65,535 →
16,777,215), preserving the theoretical 65,535-*character* capacity that would otherwise need
262,140 bytes:

```
jiveAttachmentProp.propValue  text -> mediumtext      jiveMessage.body       text -> mediumtext
jiveCategory.description      text -> mediumtext      jiveMessageProp.propValue text -> mediumtext
jiveCategoryProp.propValue    text -> mediumtext      jiveThreadProp.propValue  text -> mediumtext
jiveForum.description         text -> mediumtext      jiveUserProp.propValue    text -> mediumtext
jiveForumProp.propValue       text -> mediumtext      jiveGroupProp.propValue   text -> mediumtext
```

Consequences:

- **Truncation risk is zero** — not because the data is short, but because the type grows. The
  RCA's `33,752 < 65,535` check turns out to be belt-and-braces.
- **This is a schema change you must expect and accept.** `dsg_src/sql/schema.sql` will show
  `mediumtext`. Nothing in the codebase depends on the `TEXT` type name (the Jive DAO binds
  `setString`/`getString`), and `max_allowed_packet` is 16 MiB against a 33,752-byte worst case,
  so there is no practical impact.
- If you would rather pin it back, append
  `ALTER TABLE jiveMessage MODIFY body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;`
  after the convert. **Not recommended** — it reintroduces a 65,535-byte ceiling on data that is
  currently 33,752 bytes and growing, for no gain.
- **No `varchar` length changed.** `subject` stayed `varchar(255)`
  (`CHARACTER_OCTET_LENGTH` 255 → 1020), `creationDate` stayed `varchar(15)` (→ 60), every
  `*Prop.name` stayed `varchar(100)` (→ 400). `CONVERT TO` counts characters, not bytes, for
  `varchar`.

### 3.6 Temporary tables created during this analysis (all dropped)

For the record, since the brief requires disclosure: `_tmp_u8_<t>` × 24 (empty `LIKE` clones,
§3.4), `_tmp_l1_jiveMessage` (1 row, the ERROR 1366 control in §1), `_tmp_fid` (310 real rows,
the fidelity test in §4.3), `_tmp_t_jiveUserProp` / `_tmp_t_jiveWatch` / `_tmp_t_jiveMessage`
(full-data clones, the timing test in §6), `_tmp_d_jiveWatch` (the `DEFAULT CHARACTER SET`
comparison in §2.2), `_tmp_c` (the collation probe in §2.5), `_tmp_keylen` / `_tmp_keylen2`
(the 1000-byte probes in §3.1). All dropped; `SELECT COUNT(*) FROM information_schema.TABLES
WHERE TABLE_NAME LIKE '_tmp_%'` returns 0 and `dsg` is back to 80 base tables. **No existing
table was read non-destructively only — no `ALTER`, `UPDATE`, `INSERT`, `DELETE`, `CREATE` or
`DROP` touched any real `dsg` table.**

---

## 4. Data safety pre-checks — every query here is **PRE-MIGRATION ONLY**

Run all of §4 **before** the backup in §7. Each query is read-only and each has a stated pass
condition. The numbers shown are what the local stack returned on 2026-09-22; re-run them on the
target host, because the pass/fail is about *that* host's data.

> ### 🔀 The PRE/POST trap — the single most dangerous pattern in this document
>
> **Some expressions are correct on one side of the `ALTER` and silently wrong on the other.**
> They do not error. They return a plausible number that fails a good migration. This bit this
> plan twice, in opposite directions, so every check in §4 and §9 is now explicitly labelled.
>
> | | column is **latin1** (before) | column is **utf8mb4** (after) |
> |---|---|---|
> | `OCTET_LENGTH(CONVERT(col USING utf8mb4)) <> OCTET_LENGTH(col)` | ✅ **correct** — a real conversion, counts non-ASCII | ❌ **always 0** — `CONVERT` is the identity function |
> | `OCTET_LENGTH(col) > CHAR_LENGTH(col)` | ❌ **always 0** — latin1 is 1 byte per char by definition | ✅ **correct** — counts genuinely multi-byte rows |
>
> Demonstrated on the already-utf8mb4 `player.name`: total 64,299; the `CONVERT` form counts
> **64,299** (i.e. everything, useless); the `OCTET>CHAR` form counts 64,197 non-multibyte and
> **102** multibyte, which is the truth.
>
> **Rule of thumb:** use the `CONVERT` form *only* in §4 and step 5; use `OCTET_LENGTH` vs
> `CHAR_LENGTH` *only* in §9. A third case exists — `dsg_player.note` is utf8mb3 even before the
> migration, so it needs the `OCTET>CHAR` form on **both** sides (§2.3).
>
> **Corollary for gates:** prefer *"expect N confirmations"* over *"expect no failures"*. A check
> that cannot distinguish "passed" from "never ran" is not a check — that is how the §9.6 collation
> query hid an `ERROR 1056` behind a `2>/dev/null` and how the checksum loop printed `OK` for
> tables it never read.

### 4.1 [PRE] `CONVERT TO` reinterprets — prove MariaDB's latin1 really is cp1252

```sql
SELECT HEX(CONVERT(_latin1 0x92 USING utf8mb4)),  -- E28099  U+2019 ’  RIGHT SINGLE QUOTE
       HEX(CONVERT(_latin1 0x95 USING utf8mb4)),  -- E280A2  U+2022 •  BULLET
       HEX(CONVERT(_latin1 0x85 USING utf8mb4)),  -- E280A6  U+2026 …  ELLIPSIS
       HEX(CONVERT(_latin1 0x97 USING utf8mb4));  -- E28094  U+2014 —  EM DASH
```

**Pass:** the four values above. If `0x92` came back as `C292` (U+0092, a C1 control) the server
would be using true ISO-8859-1 and the paste-from-Word bytes would convert to garbage. It does
not — **the existing `0x92`/`0x95`/`0x85` bytes map to the correct characters automatically**, with
no pre-pass `UPDATE` needed. This is the single most important premise of the whole migration.

### 4.2 [PRE] Double-encoded / mojibake rows — the case where `CONVERT TO` is WRONG

If a row's raw latin1 bytes are *themselves* a UTF-8 sequence (UTF-8 accidentally written into a
latin1 column), `CONVERT TO` turns `Ã©` into a literal `Ã©` instead of recovering `é`. Those rows
need a `BINARY` round-trip (`col → varbinary → utf8mb4`) instead, which means they must be found
*before* the ALTER.

Broad detector — any UTF-8 lead byte followed by a continuation byte, over raw bytes:

```sql
SELECT 'body' k,
       SUM(CAST(body AS BINARY) REGEXP
           CONCAT('[',UNHEX('C2'),'-',UNHEX('F4'),'][',UNHEX('80'),'-',UNHEX('BF'),']')) n
FROM jiveMessage
UNION ALL SELECT 'subject',
       SUM(CAST(subject AS BINARY) REGEXP
           CONCAT('[',UNHEX('C2'),'-',UNHEX('F4'),'][',UNHEX('80'),'-',UNHEX('BF'),']'))
FROM jiveMessage
UNION ALL SELECT 'forum.description',
       SUM(CAST(description AS BINARY) REGEXP
           CONCAT('[',UNHEX('C2'),'-',UNHEX('F4'),'][',UNHEX('80'),'-',UNHEX('BF'),']'))
FROM jiveForum;
```

Result: **body 2, subject 0, forum.description 0.** Narrow detector for the two signatures that
actually indicate double-encoding:

```sql
SELECT 'C2 lead', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('C2'))>0
UNION ALL SELECT 'C3 lead', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('C3'))>0
UNION ALL SELECT 'E2 80 99 (â€™)', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('E28099'))>0
UNION ALL SELECT 'subj C3 lead', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(subject AS BINARY), UNHEX('C3'))>0;
-- 0, 0, 0, 0
```

**Zero `0xC2` or `0xC3` lead bytes anywhere in `body` or `subject`, and zero `â€` sequences.**
Mojibake in this data would essentially have to start with one of those; it does not.

**The 2 broad-detector hits are false positives, verified individually:**

| messageID | byte pairs matched | verdict |
|---|---|---|
| 11123 | `E486`, `EBA7`, `F186` | `ä†`, `ë§`, `ñ†` — Latin-1 accented letters that happen to sit next to a `0x86 †`. Decodes as intelligible English. Not double-encoded. |
| 20922 | `D4A1`, `D4A7`, `D5B5` | `ÔõÔ¡○oÔ§ÒõÔÕµ` — decorative accented capitals in a puzzle post ("The winning side is …"). Reinterpreting the bytes as UTF-8 gives Armenian letters plus `??` for the invalid parts, i.e. the **cp1252 reading is the intended one**. Not double-encoded. |

Check both by eye before committing:

```sql
SELECT messageID, LEFT(CONVERT(body USING utf8mb4),300)     AS cp1252_reading,
                  LEFT(CONVERT(CAST(body AS BINARY) USING utf8mb4),300) AS utf8_reading
FROM jiveMessage WHERE messageID IN (11123,20922)\G
```

**Conclusion: 0 rows need a pre-pass fix-up, and no `BINARY` round-trip variant of the ALTER is
required.** This is the best possible outcome for this check and it is why the migration is a
plain `CONVERT TO` rather than a two-phase `MODIFY … VARBINARY` dance.

### 4.3 [PRE] Fidelity on real data — the whole affected set converted for real

Every row with any non-ASCII byte in `body` or `subject` (310 distinct rows: the 265 bodies and
57 subjects overlap) was copied into `_tmp_fid`, converted with the production statement, and
compared byte-for-byte against `CONVERT()`:

```sql
CREATE TABLE _tmp_fid LIKE jiveMessage;
INSERT INTO _tmp_fid SELECT * FROM jiveMessage
  WHERE OCTET_LENGTH(CONVERT(body    USING utf8mb4)) > OCTET_LENGTH(body)
     OR OCTET_LENGTH(CONVERT(subject USING utf8mb4)) > OCTET_LENGTH(subject);   -- 310 rows
ALTER TABLE _tmp_fid CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
SELECT COUNT(*) rows_checked,
       SUM(f.body    <=> CONVERT(j.body    USING utf8mb4)) body_identical,
       SUM(f.subject <=> CONVERT(j.subject USING utf8mb4)) subj_identical,
       SUM(OCTET_LENGTH(j.body)) before_bytes, SUM(OCTET_LENGTH(f.body)) after_bytes,
       MAX(OCTET_LENGTH(f.body)) max_after
FROM _tmp_fid f JOIN jiveMessage j USING (messageID);
```

```
rows_checked  body_identical  subj_identical  before_bytes  after_bytes  max_after
         310             310             310       410,331      414,062     32,219
```

**310/310 exact on both columns.** Total body bytes grow **+0.9 %** (410,331 → 414,062). Spot
checks confirm the intent was recovered, e.g. messageID 12853 now reads
`Africa\nEgypt\n•\tSardo cheese \n•\tTestouri cheese` (the `•` came from `0x95`).

### 4.4 [PRE] What the high bytes actually are

Per-byte histogram over the 265 non-ASCII bodies (top 20 by rows containing):

```
92 ’ (122)  94 ” (38)  93 “ (35)  E9 é (27)  F3 ó (25)  85 … (22)  E1 á (14)  91 ‘ (13)
AC ¬ (11)   96 – (10)  97 — (8)   DE Þ (7)   F1 ñ (7)   B4 ´ (6)   F6 ö (6)   A0 NBSP (6)
A3 £ (5)    AE ® (5)   BF ¿ (4)   A9 © (4)
```

Exactly the paste-from-Word profile the RCA described, plus ordinary Latin-1 accents. All map
identically in latin1 and cp1252.

### 4.5 [PRE] One documented wart: cp1252-undefined bytes

Positions `0x81 0x8D 0x8F 0x90 0x9D` are undefined in Windows-1252. MariaDB's latin1 maps them
to the ISO-8859-1 C1 controls (U+0081 etc.), so they convert **losslessly** but produce C1 control
codepoints in the utf8mb4 data.

```sql
SELECT SUM(CAST(body AS BINARY) REGEXP
       CONCAT('[',UNHEX('81'),UNHEX('8D'),UNHEX('8F'),UNHEX('90'),UNHEX('9D'),']')) bodies,
       SUM(CAST(subject AS BINARY) REGEXP
       CONCAT('[',UNHEX('81'),UNHEX('8D'),UNHEX('8F'),UNHEX('90'),UNHEX('9D'),']')) subjects
FROM jiveMessage;
-- bodies 4, subjects 0    (1 row has 0x81, 3 rows have 0x9D, none have 8D/8F/90)
```

Affected: messageID **12032, 254659, 256796, 256798**. In 254659 it stands where an apostrophe
should be (`while I?m thinking`) — almost certainly a mangled `0x92` from an old round trip.

**Decision: leave them.** 4 rows out of 19,342, lossless conversion, no error, no truncation.
Cleaning them means an `UPDATE` on user content based on a guess about intent. If you want them
cleaned, do it as a **separate, reviewed change after** the migration, when the column can hold
the correct character:

```sql
-- OPTIONAL, POST-MIGRATION, NOT PART OF THIS PLAN:
-- UPDATE jiveMessage SET body=REPLACE(body, CHAR(0x81 USING utf8mb4), '') WHERE messageID=12032;
```

### 4.6 [PRE] `varchar` truncation — impossible, and measured anyway

`CONVERT TO` preserves the declared **character** length of `varchar`, and MariaDB widens the
byte budget (`varchar(255)` → `CHARACTER_OCTET_LENGTH` 1020). So no `varchar` can truncate. Max
observed content lengths, for the record:

| column | max chars | max utf8mb4 bytes | non-ASCII rows |
|---|---:|---:|---:|
| jiveMessage.subject vc255 | 174 | 174 | **57** |
| jiveForum.name vc255 | 93 | 93 | 0 |
| jiveCategory.name vc255 | 11 | 11 | 0 |
| jiveGroup.name vc50 | 5 | 5 | 0 |
| jiveGroup.description vc255 | 0 | 0 | 0 |
| jiveUser.username vc30 | 5 | 5 | 0 |
| jiveUser.name vc100 | 13 | 13 | 0 |
| jiveUser.email vc100 | 18 | 18 | 0 |
| jiveAttachment.fileName vc255 | 46 | 46 | 0 |
| jiveAttachment.contentType vc50 | 28 | 28 | 0 |
| jiveMessageProp.name vc100 | 16 | 16 | 0 |
| jiveThreadProp.name vc100 | 6 | 6 | 0 |
| jiveUserProp.name vc100 | 22 | 22 | 0 |

Query form used per column:

```sql
SELECT MAX(CHAR_LENGTH(subject)), MAX(OCTET_LENGTH(CONVERT(subject USING utf8mb4))),
       SUM(OCTET_LENGTH(CONVERT(subject USING utf8mb4)) > OCTET_LENGTH(subject)) FROM jiveMessage;
```

### 4.7 Pre-check pass/fail summary

| check | pass condition | measured |
|---|---|---|
| §4.1 latin1 == cp1252 | `0x92` → `E28099` | ✅ |
| §4.2 mojibake rows | 0 after false-positive review | ✅ 0 |
| §4.3 conversion fidelity | 100 % match on all non-ASCII rows | ✅ 310/310 |
| §3.5 `TEXT` overflow | max utf8mb4 bytes < type cap | ✅ 33,752 → `mediumtext` |
| §3.2 key length | every index < 1000 B | ✅ max 410 B |
| §3.4 DDL accepted | 24/24 clone ALTERs succeed | ✅ |
| §4.6 `varchar` truncation | content ≤ declared chars | ✅ |
| §4.5 undefined cp1252 bytes | documented, accepted | ⚠️ 4 rows |

**If §4.2 ever returns a non-zero count that survives eyeball review, STOP.** The plan in §7 is
wrong for that data and needs the two-phase `MODIFY … VARBINARY` → `MODIFY … utf8mb4` form
instead.

---

## 5. Application-side changes that must land WITH the migration

### 5.1 ⚠️ THE ORDERING RULE — read this before touching any config

> **`<characterEncoding>` must NOT move to UTF-8 until the tables are utf8mb4.**
>
> Flipping it first *reproduces the production failure exactly.* That is not a theory; it is the
> mechanism the RCA identified.

Why, spelled out:

| tables | served charset | what happens to a non-cp1252 character on save |
|---|---|---|
| latin1 | ISO-8859-1 (today, local) | response encoder rewrites it to `?` on the wire → the POST carries `?` → the row **saves, mangled**. Annoying, not silent. |
| **latin1** | **UTF-8** | the real character reaches JDBC → server cannot convert to latin1 → **ERROR 1366** → swallowed by `saveToDb` → cache holds the new text → **silent revert hours later**. **This is the production bug.** |
| utf8mb4 | ISO-8859-1 | saves correctly, but stored `U+2019` still flattens to `?` on *display*, so the 265 rows stay ugly and RCA defect 4 (HTTP 400 on freshly typed accents) persists. |
| **utf8mb4** | **UTF-8** | correct end to end. **Target state.** |

So the order is **DB first, config second**, and the config flip is what actually delivers the
user-visible win. Doing the DB alone is safe but incomplete; doing the config alone is harmful.

### 5.2 `<characterEncoding>` in `jive_config.xml` — four copies, verified

`jiveHome` is `/etc/dsg/jive3` (`dsg_src/httpdocs/WEB-INF/web.xml:87-88`), and
`docker-compose.yml:29` mounts `./dockerMain/config:/etc/dsg`. So **`dockerMain/config/jive3/jive_config.xml`
is the live runtime file on this stack.**

| path | tracked? | current `<characterEncoding>` | what it should become |
|---|---|---|---|
| `dockerMain/config/jive3/jive_config.xml` | **gitignored** (`.gitignore:44` → `config/`) | `ISO-8859-1` (line 202) | **`UTF-8`** — this is the live local runtime file |
| `config/jive3/jive_config.xml` | **gitignored** (`.gitignore:44`) | `ISO-8859-1` (line 202) | **`UTF-8`** — stale local copy; update or delete so it can't be copied back |
| `config/dsg/jive3/jive_config.xml` | **tracked** | `ISO-8859-1` (line 202) | **`UTF-8`** — this is the one that lands in git |
| `dsg_src/conf/jive3/jive_config.xml` | **tracked** | **no `<locale>` block at all** (203 lines, ends after `<readTracker>`) | **add** a `<locale>` block with `<characterEncoding>UTF-8</characterEncoding>` so the template does not silently inherit Jive's own default |

Confirmed live in the running container:

```
docker exec penteorg-pente.org-1 grep -A3 '<locale>' /etc/dsg/jive3/jive_config.xml
#   <country>US</country> <language>en</language> <timeZone>Europe/Brussels</timeZone>
```

The block to write in each:

```xml
    <locale>
        <country>US</country>
        <language>en</language>
        <timeZone>Europe/Brussels</timeZone>
        <characterEncoding>UTF-8</characterEncoding>
    </locale>
```

**Production's copy is a fifth, unseen file.** The RCA left this open. Two of the four repo copies
are gitignored precisely because they differ per host, so **production's value is unknown and must
be read on the box before you change anything.** The RCA's indirect evidence (zero `?AM`/`?PM`
rows across 19,342 messages, where the ISO-8859-1 path demonstrably writes `?AM`) argues
production is **already UTF-8** — which, per §5.1 row 2, means **production is sitting in the
harmful combination right now** and the migration is the thing that rescues it. Settle it with one
command against the prod host before starting (the RCA's `curl -sI … | grep -i content-type`, or
read `/etc/dsg/jive3/jive_config.xml` over ssh). **Do not guess.**

### 5.3 JDBC URL — `dsg_src/conf/context.xml`: leave it alone (or make it explicit)

```xml
url="jdbc:mariadb://@db.host@/@db.name@?useServerPrepStmts=true"
```

There is **no `characterEncoding` parameter**, on either the `jdbc/dsg` or `jdbc/dsg_ro` resource.
Driver is `mariadb-java-client-3.5.9.jar`, which negotiates a **utf8mb4** connection charset by
default. Independent confirmation: the RCA's ERROR 1366 can only occur if the server *received* a
real U+202F — a latin1 connection would have mangled it client-side and never raised 1366. So the
connection is already UTF-8, which is why the server-side latin1 conversion is what fails.

**Required change: none.** Adding `&characterEncoding=UTF-8` documents the assumption and costs
nothing; adding `&characterEncoding=latin1` would silently break everything. If you touch this file
you must rebuild (`@`-tokens are filtered at Ant build time), so prefer leaving it.

> **Uncertainty, stated:** I inferred the connection charset from driver defaults plus the ERROR
> 1366 mechanism. I did not read it off a live app connection — the app pool's session variables
> are not reachable from `information_schema.PROCESSLIST`. To confirm directly, add
> `SELECT @@character_set_client, @@collation_connection` to a throwaway JSP on the bind-mounted
> `gameServer` tree, or enable the general log for one request.

### 5.4 The dead filter mapping and the charset contradictions

**(a) Illegal `url-pattern` — the mapping never fires.** Jive's own filter is mapped to a prefix
pattern that does not end in `/*`, which the servlet spec rejects, so the container silently never
matches it:

- `dsg_src/httpdocs/WEB-INF/web.xml:194-195` — `Set Character Encoding` →
  `<url-pattern>/gameServer/forums*</url-pattern>` (filter class
  `com.jivesoftware.util.SetCharacterEncodingFilter`, declared at line 175)
- `dsg_src/conf/web.xml:114-115` — the same broken mapping in the build template

**Remedy: delete both the `<filter>` and the `<filter-mapping>`, in both files.** Do not "fix" the
pattern to `/gameServer/forums/*`. `org.pente.filter.SetCharacterEncodingFilter` is already mapped
to `/*` with `encoding = UTF-8`
(`dsg_src/httpdocs/WEB-INF/web.xml:18-31`) and runs first; activating a second, competing encoding
filter over the same requests is a new bug, not a fix. Deleting dead config is the whole change.

**(b) Two JSPs hard-code a UTF-8 response, contradicting the config.** Not a `<meta>` tag as the
RCA phrased it — it is a JSP page directive, which sets the real HTTP header:

- `dsg_src/httpdocs/gameServer/forums/thread-flat.jsp:22` — `<%@ page contentType="text/html; charset=UTF-8" %>`
- `dsg_src/httpdocs/gameServer/forums/thread-messagebox.jsp:16` — same

These are two of the five thread-render views (`thread-threaded.jsp`, `thread-flat.jsp`,
`thread-tree.jsp` per `dsg_src/conf/jive3/custom-actions.xml:24-26`). **Measured header on this
stack right now:**

```
GET /gameServer/forums/index.jspa            → Content-Type: text/html;charset=ISO-8859-1
GET /gameServer/forums/thread.jspa?threadID=233491 → Content-Type: text/html;charset=ISO-8859-1
GET /gameServer/forums/edit!default.jspa?message=265026 → Content-Type: text/html;charset=ISO-8859-1
```

So the "flat" view is not the default on this stack and the directive is currently inert — but it
means **a user who switches to the flat view gets a UTF-8 page while the rest of the forum is
ISO-8859-1.** Once §5.2 sets the config to UTF-8 the inconsistency disappears on its own, so the
cleanest change is to **delete both directives** and let `JiveGlobals.getCharacterEncoding()` be
the single source of truth — which is exactly how the admin pages already do it
(`forums/admin/header.jsp:23`, `admin/sidebar.jsp:41`, `admin/tabs.jsp:34` all render
`charset=<%= JiveGlobals.getCharacterEncoding() %>`).

> **Uncertainty, stated:** I did not determine *why* the directive is inert — whether the default
> view is `threaded`, or whether something sets the response encoding after the JSP does. It does
> not change the recommendation (delete it either way), but do not record it as "the flat view
> serves UTF-8" without checking.

### 5.5 The two other U+202F sites — already fixed since the RCA was written

RCA defect 2 listed `admin/pending.jsp:360` and `admin/forumContent_edit.jsp:193` as unfixed.
**They have since been fixed** (verified with `git diff` on 2026-09-22): both now compute

```jsp
String editedOnDate = SkinUtils.formatDate(request, pageUser, new Date()).replaceAll("\\p{Zs}", " ");
```

at `admin/pending.jsp:364` / `admin/forumContent_edit.jsp:197`, used at lines 368 / 201. Both are
uncommitted working-tree changes; **commit them with, or before, this migration** so the fix cannot
be lost. `git status` also shows the matching `editform.jsp` change still uncommitted.

**This plan requires no further change to any of the three.** After the migration the folds become
redundant but harmless — a utf8mb4 column accepts U+202F (proven in §1). **Leave them in**:
removing them would re-widen the blast radius if the migration is ever rolled back to latin1, and
they cost nothing.

> Treat this as a correction to the RCA's "Not fixed" list, not a contradiction of it — the RCA was
> accurate when written; the working tree moved.

### 5.6 Deployment mechanics on this stack

Measured from `docker inspect penteorg-pente.org-1`:

| host path | container path | implication |
|---|---|---|
| `dsg_src/httpdocs/gameServer` | `/usr/local/tomcat/webapps/ROOT/gameServer` | **JSP edits are live** — Jasper recompiles on the next request. No rebuild, no restart. |
| `deployClasses/org` | `/usr/local/tomcat/webapps/ROOT/WEB-INF/classes/org` | compiled Java is bind-mounted, but **needs `./justCompile`**, which itself runs `docker compose restart pente.org` (no hot reload — see commit 8a5ae71). |
| `dockerMain/config` | `/etc/dsg` | `jive_config.xml` edits need only a **container restart** to be re-read. |
| `dockerMain/etctomcat9` | `/usr/local/tomcat/conf` | Tomcat `conf` is mounted; the webapp's own `WEB-INF/web.xml` is **baked into the image** (`Dockerfile:26 COPY dsg_src/httpdocs/`), so **web.xml changes require an image rebuild**, not just a restart. |

So the §5.4(a) `web.xml` change is the only application-side item needing a rebuild. **No Java
source change is required by this plan at all** — nothing in `dsg_src/java` references a charset
relevant to the forums (the only `ISO-8859-1` hits are `ImageInfo.java:507` for JPEG comment bytes
and `SGFGameFormat.java:130,156` for SGF export, both unrelated).

> `./sync_gameServer.sh` is a **production deploy** (`rsync … debian@pente.org:~/dockerMain/gameServer/`).
> Never run it as part of local testing.

### 5.7 Jive's search index, attachment store, and `jiveID` — checked, and one real trap

**(a) No MySQL `FULLTEXT` index exists anywhere in `dsg`.**

```sql
SELECT TABLE_NAME, INDEX_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='dsg' AND INDEX_TYPE='FULLTEXT';   -- empty
```

Forum search is **entirely Lucene on disk**, so the DDL cannot break a fulltext index, and there is
no `ft_min_word_len` / charset-sensitive tokenizer interaction to worry about.

**(b) The Lucene index does NOT need rebuilding.** It lives at
`/etc/dsg/jive3/search/dCX5gMyE` (5.3 MiB, 277 sub-entries), with
`<autoIndexEnabled>true</autoIndexEnabled>` and `<autoIndexInterval>10</autoIndexInterval>`.

The reasoning: **if** the JDBC connection is utf8mb4 (§5.3), the server has always converted
latin1 → utf8mb4 on the way *out*. Java would therefore already have received `U+2019` for a stored
`0x92`, the Strings Lucene indexed were already the correct characters, and after the migration the
identical Strings come back — so nothing the index contains changes meaning. (The asymmetry is the
whole bug: reads were always fine; it is *writes* that fail, because the server cannot convert
utf8mb4 → latin1.)

> ⚠️ **That conclusion is only as good as its premise, and the premise is INFERRED, not measured**
> (§11.3 says so; this section must not quietly upgrade it). If the connection were actually latin1,
> Java would hold `U+0092` for a stored `0x92`, the Lucene index would carry `U+0092`, and
> post-migration reads returning `U+2019` would **silently stop matching** — search would quietly
> miss accented and smart-quoted terms with no error anywhere.
>
> **Settle it first** with the throwaway JSP described in §5.3 (`SELECT @@character_set_client,
> @@collation_connection`). It costs one page load and it is the difference between "no reindex
> needed" and "reindex required".
>
> **Contingency if you skip that check:** after §9, search the forum for a term containing an
> accent or a curly quote that you know exists (e.g. a word from messageID 12853). If it misses,
> trigger Jive's admin rebuild-index from `forums/admin/` — **do not** hand-edit `<lastIndexed>`,
> for the reasons in (c).

**(c) ⚠️ The trap: `<lastIndexed>` and `<directory>` live in the same file you edit in step 8.**

```xml
<search>
    <enabled>true</enabled>
    <autoIndexEnabled>true</autoIndexEnabled>
    <autoIndexInterval>10</autoIndexInterval>
    <directory>dCX5gMyE</directory>          <!-- ← names the 5.3 MiB index dir on disk -->
    <lastIndexed>1790069028951</lastIndexed> <!-- ← Jive's incremental-index watermark -->
</search>
```

`jive_config.xml` is **not a pure static config file — Jive writes runtime state back into it.**
Consequences for step 8:

- **Edit only the `<characterEncoding>` line.** Do not regenerate the file, do not copy a repo copy
  over the runtime copy, and do not "tidy" it. Clobbering `<directory>dCX5gMyE</directory>` orphans
  the index and forum search silently returns **zero results** with no error anywhere.
- **Copy the runtime file before editing:**
  `cp dockerMain/config/jive3/jive_config.xml backups/jive_config-$STAMP.xml`
- This also explains why the repo's tracked copies differ from the runtime one in more than just
  `<characterEncoding>` (§5.2), and why two of the four are gitignored. **Do not try to make them
  identical.**

**(d) Attachments are unaffected.** The store is `/etc/dsg/jive3/attachments` (37 MiB), keyed by
**numeric attachment ID** (`337.bin`, `340.bin`, …), not by filename. And the DB side has no
non-ASCII to reinterpret:

```sql
SELECT COUNT(*) rows_total,
       SUM(OCTET_LENGTH(CONVERT(fileName    USING utf8mb4)) > OCTET_LENGTH(fileName))    nonascii_names,
       SUM(OCTET_LENGTH(CONVERT(contentType USING utf8mb4)) > OCTET_LENGTH(contentType)) nonascii_ct
FROM jiveAttachment;   -- 272, 0, 0
```

**(e) `jiveID` is the sequence allocator — another reason step 2 is mandatory.**

```
idType  id            (0=forum? 1=thread 2=message 3,4,13,14=other counters)
0       38      1  233630      2  268575      3  2      4  2      13  616      14  5
```

Jive hands out ID blocks from this table. Converting it is a data no-op (no character columns), but
**doing so while the app is running** puts a MyISAM table lock in the path of an in-flight block
allocation. With the app stopped (§7 step 2) this cannot happen. **Do not treat step 2 as
optional** — it is what makes the `jiveID` and `jiveMessage` conversions uneventful.

---

## 6. Backup, rollback, downtime, and the replica

### 6.1 Realistic downtime: seconds, not minutes

The `ALTER`s were timed against **full-data clones** of the three heaviest tables:

```sql
CREATE TABLE _tmp_t_jiveUserProp LIKE jiveUserProp;
INSERT INTO _tmp_t_jiveUserProp SELECT * FROM jiveUserProp;      -- 274,037 rows
ALTER TABLE _tmp_t_jiveUserProp CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

| table | rows | measured |
|---|---:|---:|
| jiveUserProp | 274,037 | **399 ms** |
| jiveWatch | 231,885 | **778 ms** |
| jiveMessage | 19,342 (11.24 MiB) | **249 ms** |
| the other 21 (all ≤ 3,221 rows, ≤ 0.3 MiB) | — | negligible |

**Total ALTER window: well under 3 seconds.** The real downtime is dominated by the backup and the
Tomcat restart, not the DDL:

**Done BEFORE the window** (app still up, `--lock-tables` gives a consistent snapshot):

| phase | realistic |
|---|---|
| `mariadb-dump` of the 24 jive tables (66 MiB) | 5–20 s |
| verify the dump — incl. a full restore of 525,000+ rows into a scratch schema and 24 `CHECKSUM TABLE`s | **2–5 min** |

**Inside the window** (site down):

| phase | realistic |
|---|---|
| `FLUSH TABLES … WITH READ LOCK` + tar the 72 MyISAM files (66 MiB) | 5–15 s |
| the 24 `ALTER`s | **< 3 s** |
| post-ALTER verification queries (§9.1, §9.2) | 20–40 s |
| `docker compose start pente.org` (clears `messageCache`, re-reads `jive_config.xml`) | 30–90 s |
| **site unavailable, end to end** | **≈ 2–3 minutes**, of which ~3 s is irreversible |
| **plus** §9.4's mandatory second full restart (cache-eviction proof) | **another 30–90 s**, later |
| **honest total site downtime** | **≈ 3–5 minutes across two outages** |

> ⚠️ **It is the whole site that goes down, not just the forum.** `docker-compose.yml` defines a
> **single** application service (`pente.org`) serving everything — forums, the live game room
> (ports 15999–16002), tourneys, the AI frontends. (`pente_org` further down that file is the
> *network*, not a second service.)
>
> **Consequence the forum framing hides: live timed games and in-flight tourney rounds are cut
> mid-clock.** The stack keeps emergency-time state (`tb_emergency_time`, 7,490 rows), so a
> restart during a timed game costs real players real clock. **Gate the window on "no in-progress
> timed games", not on "the forum looks quiet."** Announce it site-wide.

MyISAM takes a **table-level write lock** for the duration of each `ALTER` (it is a full table
rebuild — there is no online/in-place DDL for MyISAM). Reads block too while the rebuild swaps the
`.MYD`/`.MYI` files in.

### 6.2 Size effect: data +4 %, index actually shrinks

From the same clones:

| table | data MiB before → after | index MiB before → after |
|---|---|---|
| jiveUserProp | 10.63 → **11.05** (+4.0 %) | 14.16 → **7.26** (−49 %) |
| jiveMessage | 11.24 → **11.26** (+0.2 %) | 2.43 → **2.34** (−4 %) |
| jiveWatch | 7.30 → **7.30** (0 %) | 18.47 → **17.37** (−6 %) |

The index shrink is not a utf8mb4 effect — it is MyISAM key packing on a **freshly built** index.
The live `jiveUserProp.MYI` (14.8 MiB on disk) is bloated from years of in-place updates. So the
migration is also a free `OPTIMIZE TABLE`. Net on-disk change across all 24: **roughly −7 MiB**.

Free space is not a constraint: `df` inside the container reports **424 GiB available** on
`/var/lib/mysql`, against a 5.13 GiB total database and 66 MiB of jive tables.

### 6.3 Backup — the only rollback that matters

MyISAM `CONVERT TO` is **not transactional**. There is no `ROLLBACK`. The backup *is* the rollback.

**Two artefacts. Which one is primary depends on the replica**, and you must settle that at
Step 0 before you rely on either. For MyISAM a file-level copy is strictly more faithful than a
logical dump — byte-exact, index state included — and it restores in seconds rather than minutes.
But a physical restore writes no binlog, so with replication running it reverts the master and
leaves the replica converted: a silent split-brain schema. See the ROLLBACK HAZARD callout in
§6.5 — this is not a theoretical concern, the replica **was** found running.

| artefact | role | restore time | fidelity |
|---|---|---|---|
| `jive-myisam-files-*.tar.gz` (24 `.frm` + 24 `.MYD` + 24 `.MYI`, 66 MiB) | primary **only if** the replica is stopped for the window and re-seeded after (§6.5 option a) | seconds | byte-exact |
| `jive-latin1-*.sql.gz` | **the only sanctioned rollback while replication is live** (§6.5 option b); also the portable, inspectable, cross-version copy | minutes | depends on `--default-character-set=binary` being right |

> 🛑 **Do not restore the physical tar on a replicating master.** If Step 0 finds
> `Slave_IO_Running: Yes` on `penteorg-replica_db-1` and you did not take option (a), the tar is
> **not** a valid rollback — use the logical dump, then re-run the replica collation check.

> **Run every block in this document under `bash`.** This project's interactive shell is **fish**,
> where `set -a; . ./.env; set +a`, `STAMP=$(…)` and `Q() { … }` are all syntax errors on line 1.

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
set -a; . ./.env; set +a
mkdir -p backups

# Compute the stamp ONCE and persist it. Steps 3, 4, 8 and 11 run in different shells; a second
# `date` call there would point every verify command at a file that does not exist, and the
# resulting `test "" = 24` failure would look like a corrupt backup.
STAMP=$(date +%Y%m%d-%H%M%S); echo "$STAMP" > backups/.stamp
# every later step begins with:   STAMP=$(cat backups/.stamp)

JIVE_TABLES="jiveAttachment jiveAttachmentProp jiveCategory jiveCategoryProp jiveForum \
jiveForumProp jiveGroup jiveGroupPerm jiveGroupProp jiveGroupUser jiveID jiveMessage \
jiveMessageProp jiveModeration jiveReadTracker jiveReward jiveThread jiveThreadProp jiveUser \
jiveUserPerm jiveUserProp jiveUserReward jiveUserRoster jiveWatch"

# Logical dump. --default-character-set=binary is ESSENTIAL: it makes mariadb-dump emit the
# raw stored bytes with no client-side transcoding, so the dump restores byte-identically
# whether the table is latin1 or utf8mb4 at restore time.
# Flag set validated against this stack (real dump of jiveID, exit 0, "Dump completed" trailer):
#   --default-character-set=binary  raw bytes, no transcoding   --lock-tables  correct for MyISAM
#   --skip-routines --skip-events   this is a table subset, not a schema     (NOT --single-transaction:
#   that is an InnoDB-only guarantee and buys nothing on MyISAM)
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" penteorg-main_db-1 \
  mariadb-dump -uroot --default-character-set=binary --lock-tables \
    --add-drop-table --skip-routines --skip-events \
    "$MYSQL_DATABASE" $JIVE_TABLES \
  | gzip > "backups/jive-latin1-$STAMP.sql.gz"
# NOTE: the pipe into gzip hides mariadb-dump's exit code. Either set `set -o pipefail` first,
# or rely on the "Dump completed" trailer check below, which is the real gate.

# CONDITIONAL rollback artefact -- valid only with the slave stopped, see §6.4/§6.5 hazard.
# Physical copy of the 72 MyISAM files (24 .frm + 24 .MYD + 24 .MYI,
# 66 MiB). Taken AFTER quiesce (§7 step 2).
#
# CRITICAL: stopping Tomcat does not flush MariaDB. This server runs delay_key_write=ON with a
# 1 GiB key_buffer (measured), which is precisely the setting that keeps dirty index blocks in
# memory across writes -- so a naive tar can capture a .MYI that does not match its .MYD, and the
# "backup" silently restores a corrupt index. FLUSH first, and hold the lock across the copy.
docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" dsg \
  -e "FLUSH TABLES $(echo $JIVE_TABLES | tr ' ' ',') WITH READ LOCK; SELECT SLEEP(60);" &
FLUSH_PID=$!
sleep 2   # let the FLUSH land before copying
docker exec penteorg-main_db-1 sh -c \
  'cd /var/lib/mysql/dsg && tar czf - jive*.frm jive*.MYD jive*.MYI 2>/dev/null' \
  > "backups/jive-myisam-files-$STAMP.tar.gz"
kill $FLUSH_PID 2>/dev/null   # releases the read lock

# Simpler and safer if you can afford it: stop the DB container entirely for the copy.
#   docker compose stop main_db
#   tar czf - -C dockerMain/db/dsg jive... > backups/jive-myisam-files-$STAMP.tar.gz
#   docker compose start main_db
# With main_db stopped there is no lock to hold and no flush to get wrong. Prefer this.
```

**Verify the backup before the ALTER — an unverified backup is not a backup:**

```bash
STAMP=$(cat backups/.stamp)

# 1. all 24 CREATE TABLEs present, and still latin1.
#    ANCHOR THE PATTERN. Six forum bodies contain the literal text "CREATE TABLE" (verified:
#    SELECT SUM(body LIKE '%CREATE TABLE%') FROM jiveMessage; -> 6), and they ride along inside
#    INSERT lines. `zgrep -c` counts LINES, and mariadb-dump uses extended-insert (a few very long
#    INSERT lines per table), so the unanchored count lands somewhere above 24 -- the exact number
#    depends on how those 6 bodies fall across INSERT lines. Either way the `= 24` test fails and
#    step 4 ABORTS a perfectly good backup. Anchoring makes it exact regardless.
test "$(zgrep -c '^CREATE TABLE' backups/jive-latin1-$STAMP.sql.gz)" = 24 && echo "24 tables OK"
zgrep -c 'CHARSET=latin1' backups/jive-latin1-$STAMP.sql.gz          # expect 24 (0 bodies contain it)

# 2. the dump is complete (mariadb-dump writes this trailer only on success)
zcat backups/jive-latin1-$STAMP.sql.gz | tail -2 | grep -q 'Dump completed' && echo "complete"

# 3. the cp1252 high bytes are still SINGLE bytes in the dump, i.e. nothing transcoded them.
#    Counting INSERT lines would prove nothing; count the actual byte.
zcat backups/jive-latin1-$STAMP.sql.gz | LC_ALL=C grep -c $'\x92'   # compare to the live count
# live: SELECT COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY),UNHEX('92'))>0;  -> 123
# A dump showing 0 here means --default-character-set=binary was dropped: the backup is WRONG.

# 4. STRONGEST check: restore into a scratch schema and diff the checksums.
docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS dsg_restore_test"
zcat backups/jive-latin1-$STAMP.sql.gz | docker exec -i penteorg-main_db-1 \
  mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=binary dsg_restore_test
# Compare ALL 24 tables, not just jiveMessage.body. CHECKSUM TABLE covers every column of every
# row; a body-only CRC would pass while jiveUserProp (274k rows) was silently truncated.
ok=0
for t in $JIVE_TABLES; do
  a=$(docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B \
        -e "CHECKSUM TABLE dsg.$t EXTENDED" | awk '{print $2}')
  b=$(docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B \
        -e "CHECKSUM TABLE dsg_restore_test.$t EXTENDED" | awk '{print $2}')
  # Guard against BOTH degenerate values, because either makes the naive comparison pass:
  #   ""     -> the query failed outright (both sides "" compare equal -> prints OK, reads nothing)
  #   "NULL" -> CHECKSUM TABLE's answer for a MISSING table (verified: a bogus schema returns NULL,
  #             so two absent tables would compare equal and "verify" a backup containing neither)
  # No 2>/dev/null either -- hiding stderr is how both failure modes stay invisible.
  if [ -z "$a" ] || [ -z "$b" ] || [ "$a" = "NULL" ] || [ "$b" = "NULL" ]; then
       echo "ERROR    $t  checksum unusable (live='$a' restored='$b')"
  elif [ "$a" = "$b" ]; then echo "OK       $t  $a"; ok=$((ok+1))
  else echo "MISMATCH $t  live=$a restored=$b"; fi
done
echo "matched $ok/24"
# EXPECT: "matched 24/24". Any MISMATCH or ERROR -> the dump is not a usable rollback. Re-take it.
# Counting successes is the point: "no MISMATCH printed" is not the same as "24 tables verified".
# Plus the byte-level spot check on the column that matters:
docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
  SELECT 'live' src, COUNT(*) n, SUM(CRC32(CAST(body AS BINARY))) body_ck,
         SUM(CRC32(CAST(subject AS BINARY))) subj_ck FROM dsg.jiveMessage
  UNION ALL
  SELECT 'restored', COUNT(*), SUM(CRC32(CAST(body AS BINARY))),
         SUM(CRC32(CAST(subject AS BINARY))) FROM dsg_restore_test.jiveMessage;"
# the two rows MUST match on n, body_ck and subj_ck. Then:
docker exec penteorg-main_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "DROP DATABASE dsg_restore_test"
```

> **Do checks 1–4 BEFORE quiescing the app (§7 step 2).** `--lock-tables` already gives a
> consistent MyISAM snapshot, and a read-only dump plus a scratch-schema restore needs nobody
> offline. Check 4 restores 525,000+ rows and rebuilds every index — that is **minutes**, and
> running it inside the outage window would blow the downtime budget in §6.1 several times over.
> The runbook in §7 is ordered accordingly: logical dump and its verification happen at step 1b,
> and only the flushed physical copy happens inside the window.

Keep both backup artefacts until §9 verification passes **and** a full `messageCache` TTL
(6 hours) has elapsed with no user reports. The 6-hour figure matters: that is exactly how long the
RCA's failure mode took to surface, so a migration that "looks fine" for 20 minutes has not yet
been tested by the mechanism that caused the original bug.

### 6.4 Restore (rollback)

```bash
# Quiesce the app first (§7 step 2), then:
zcat backups/jive-latin1-$STAMP.sql.gz | docker exec -i penteorg-main_db-1 \
  mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=binary "$MYSQL_DATABASE"
# --add-drop-table in the dump means each table is dropped and recreated as latin1.
# ALSO revert <characterEncoding> to ISO-8859-1 in dockerMain/config/jive3/jive_config.xml
# (see §5.1 — utf8mb4 config over latin1 tables is the harmful combination), then:
docker compose restart pente.org
```

**The physical restore — byte-exact and seconds rather than minutes, but CONDITIONAL.**

> 🛑 **Only valid if replication was stopped for the window** (§6.5 option a, confirmed at
> Step 0a). A physical file restore writes **no binlog**: with the replica live it reverts the
> master to latin1 and leaves the replica on utf8mb4 — a silent split-brain schema that raises
> no error anywhere. If you are unsure, or Step 0a found `Slave_IO_Running: Yes` and you did not
> stop the slave, **use the logical restore above instead.** After either restore, re-run the
> replica collation check in §6.5 before declaring the rollback complete.

```bash
STAMP=$(cat backups/.stamp)
docker compose stop pente.org main_db
tar xzf "backups/jive-myisam-files-$STAMP.tar.gz" -C dockerMain/db/dsg/
docker compose start main_db
# NOTE: mariadb-check takes LITERAL table names -- a glob gives
#   "Failed to SHOW CREATE TABLE `jive*` / Table 'dsg.jive*' doesn't exist"  (verified).
docker exec penteorg-main_db-1 mariadb-check -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --check --medium-check dsg $JIVE_TABLES        # expect 24 x OK
docker compose start pente.org
```

Use the logical restore above instead whenever replication was left running, when the physical
files are unusable, or when you are moving across server versions.

### 6.5 The replica and the autossh tunnels

> ### ⚠️ CORRECTION — an earlier draft of this section was WRONG
>
> It claimed *"the replica is presently a standalone seeded copy, not a live replica … nothing to
> stop and nothing that will auto-propagate."* **That was false, and it was false because of a
> broken measurement, not a changed system.**
>
> The command used was `mariadb -N -B -e "SHOW SLAVE STATUS\G"`. **`-N` strips column names, so
> `\G`'s vertical output loses its `Master_Host:` labels** and the grep filter looking for those
> labels matched nothing — which reads exactly like "no replication configured". Re-run without
> `-N -B`, the truth is the opposite. **Never use `-N`/`-B` with `\G`.**

**Correctly measured state of this local stack, 2026-09-22:**

```
$ docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW SLAVE STATUS\G"
        Master_Host: replica_auto_ssh      Master_Port: 3310
   Slave_IO_Running: Yes                Slave_SQL_Running: Yes
    Replicate_Do_DB: dsg              Seconds_Behind_Master: 0
Exec_Master_Log_Pos: 37823322    Last_Error / Last_IO_Error / Last_SQL_Error: (all empty)
Slave_SQL_Running_State: Slave has read all relay log; waiting for more updates

$ replica: SELECT TABLE_COLLATION,COUNT(*) ... WHERE TABLE_NAME LIKE 'jive%'
  latin1_swedish_ci  24          -- in lockstep with master
$ SELECT COUNT(*) FROM information_schema.SLAVE_STATUS;   -- 1  (label-free cross-check)
```

`Master_Host: replica_auto_ssh` is **expected, not a contradiction** — the tunnels loop back to
this Mac with crossed targets (main→3317→replica_db, replica→3316→main_db).

**So replication IS live and caught up. The migration is NOT isolated: the 24 `ALTER`s will
propagate to the replica.** That is the desired outcome, but it changes the rollback story — see
the boxed warning below.

The wiring, for when it *is* running
(`docker-compose-replica.yml`, `dockerReplica/dbAlwaysInit/0001_init.sh`):

```
replica_db  --MASTER_HOST=replica_auto_ssh, MASTER_PORT=3310 (REPLICA_LOCAL_PORT)-->
replica_auto_ssh  --ssh rainwolf@host.docker.internal, forwards to remote 3316-->
host 127.0.0.1:3316 (REPLICA_REMOTE_PORT)  --published by-->  main_db:3306
```

`dockerReplica/dbConfig/*.cnf` sets `server_id=2` and **`replicate_do_db=dsg`**.

Four things follow:

1. **`replicate_do_db` filters DDL by the session's default database, not by the table name.**
   This is **live, not hypothetical.** Run every `ALTER` with the default database set to `dsg` —
   i.e. `mariadb … "$MYSQL_DATABASE" -e "…"` or a leading `USE dsg;`. A fully-qualified
   `ALTER TABLE dsg.jiveMessage …` issued with **no** default database **will be skipped by the
   replica**, silently leaving it latin1 while main is utf8mb4. This is the single easiest way to
   get a split-brain schema out of this migration.
2. **Forward propagation needs no action.** `binlog_format` is `ROW`, but DDL is always logged as
   `STATEMENT`, so the replica replays the identical `ALTER`s and both copies end up utf8mb4.
3. > ### 🛑 ROLLBACK HAZARD — a physical restore causes silent split-brain
   >
   > **A file-level `.MYD`/`.MYI` restore bypasses the binlog entirely.** Untar latin1 files into
   > `dockerMain/db/dsg/` and the **master reverts to latin1 while the replica stays utf8mb4** —
   > no error, no warning, nothing in any log. Reads served from the replica then disagree with the
   > master about the schema, and the next `ALTER` on either side diverges further.
   >
   > This invalidates "physical tar = primary rollback" as an unconditional rule. **Pick one:**
   >
   > - **(a) `STOP SLAVE` for the window** (below), do the migration, then either `START SLAVE` if
   >   you completed, or — if you rolled back physically — **re-seed the replica from the restored
   >   master** before restarting it. Most control, most steps.
   > - **(b) Demote the physical tar to secondary** and make the **logical dump the only sanctioned
   >   rollback**, because a logical restore *is* binlogged and therefore reverts both sides
   >   symmetrically. Simplest, and the default recommendation if you are unsure.
   >
   > Either way, **§9.6's replica-collation check is mandatory after any rollback.**
4. **An SSH error from the tunnels is never a MariaDB TLS problem.** The tunnels loop back to this
   Mac (`host.docker.internal`) with crossed DB targets; if `replica_auto_ssh` is unhealthy the
   replica simply cannot connect and will sit behind. Fix the tunnel, not the database.

**Option (a) — taking the replica out of the window:**

```bash
docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -e "STOP SLAVE;"
# ... run the migration ...
docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -e "START SLAVE;"
```

**Replica checks around the migration (mandatory either way):**

```bash
# NOTE: no -N and no -B. Those strip the labels from \G output and make a healthy replica
# look like an unconfigured one -- the exact mistake an earlier draft of this plan made.
R() { docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"; }

# BEFORE: note the position you can resume from
R -e "SHOW SLAVE STATUS\G" | grep -E 'Master_Log_File|Exec_Master_Log_Pos|Seconds_Behind_Master'

# AFTER: confirm the DDL landed on BOTH sides
R -e "SELECT COUNT(*) still_latin1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' AND TABLE_COLLATION LIKE 'latin1%';"
# expect 0. If it is 24, the cause is almost certainly gotcha (1) above.
R -e "SHOW SLAVE STATUS\G" | grep -E 'Slave_SQL_Running|Last_SQL_Error|Seconds_Behind'
```

**Production is off limits for this exercise.** When this plan is eventually applied there, the
prod replica's state must be re-measured on the box — do not carry this stack's "replication is
off" finding across.

---

## 7. The ordered runbook

Every step has a **rollback branch**. Steps 0–5 are reversible at zero cost (nothing has been
written). Step 8 is the point of no return without a restore.

> **Run every block under `bash`.** The project's interactive shell is fish, where the preamble
> below is three syntax errors. Start with `bash` (or put each step in a `#!/usr/bin/env bash`
> script).

Preamble for every shell block:

```bash
cd /Users/waliedothman/mariposa/coding/pente.org-project/pente.org
set -a; . ./.env; set +a
Q()  { docker exec penteorg-main_db-1 mariadb --default-character-set=utf8mb4 \
         -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "$1"; }   # default DB = dsg, per §6.5(1)
JIVE_TABLES="jiveAttachment jiveAttachmentProp jiveCategory jiveCategoryProp jiveForum \
jiveForumProp jiveGroup jiveGroupPerm jiveGroupProp jiveGroupUser jiveID jiveMessage \
jiveMessageProp jiveModeration jiveReadTracker jiveReward jiveThread jiveThreadProp jiveUser \
jiveUserPerm jiveUserProp jiveUserReward jiveUserRoster jiveWatch"
# Step 1b creates backups/.stamp. EVERY LATER STEP RE-READS IT -- do not call `date` again:
STAMP=$(cat backups/.stamp 2>/dev/null)
```

### Step 0 — Establish the target host's starting facts

```bash
Q "SELECT VERSION(); SELECT @@sql_mode, @@collation_server;
   SELECT COUNT(*) latin1_jive FROM information_schema.TABLES
    WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' AND TABLE_COLLATION LIKE 'latin1%';"
grep -n characterEncoding dockerMain/config/jive3/jive_config.xml
```

#### Step 0a — HARD REPLICA GATE. Do not skip; do not infer.

An earlier draft of this plan asserted "replication is not running" on the strength of a **broken
command** (`-N -B` with `\G`). It was wrong, and every downstream rollback decision depended on it.
So measure it, explicitly, and branch on the answer:

```bash
R() { docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"; }  # no -N/-B!
R -e "SHOW SLAVE STATUS\G" | grep -E 'Slave_IO_Running|Slave_SQL_Running|Replicate_Do_DB|Seconds_Behind_Master|Last_Error'
R -e "SELECT COUNT(*) slave_rows FROM information_schema.SLAVE_STATUS;"
R -e "SELECT TABLE_COLLATION, COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' GROUP BY 1;"
```

| what you see | what it means | what to do |
|---|---|---|
| `slave_rows` = 1, both `Running: Yes`, `Seconds_Behind_Master` 0 | **replication is LIVE** (this is the state measured on 2026-09-22) | §6.5 applies in full. **Choose rollback option (a) or (b) NOW, before step 1b.** Physical-tar-as-primary is *not* safe unless you take option (a). |
| `slave_rows` = 0 | genuinely no replica | physical tar may be primary; §6.5's hazard does not apply |
| `Running: No` / non-empty `Last_Error` | replica is broken **before** you start | **STOP.** Fix or deliberately decommission it first. Migrating onto a broken replica buries your change under an unrelated failure. |
| replica jive collations ≠ master's | already split-brain | **STOP.** Reconcile before adding another schema change. |

**Gate:** `latin1_jive` = 24 on master, `sql_mode` contains `STRICT_TRANS_TABLES`, the
`<characterEncoding>` value is written down, replica state is one of the two *proceed* rows above,
and **you have recorded which rollback option you chose.**
**Rollback:** n/a — nothing has changed.

#### Step 0b — Pre-migration integrity baseline

Without this, a table that was *already* corrupt shows up in §9.2(e2) afterwards and gets blamed on
the migration.

```bash
docker exec penteorg-main_db-1 mariadb-check -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --check --medium-check dsg $JIVE_TABLES | tee backups/precheck-tables.txt
```

**Gate:** 24 × `OK`. Anything else — repair it and re-baseline **before** migrating.

### Step 1 — Run every pre-check in §4

Specifically §4.1 (cp1252 proof), §4.2 (mojibake = 0), §3.5 (TEXT byte maxima), §4.6 (varchar).

**Gate:** the §4.7 table is all ✅ (the §4.5 ⚠️ is accepted).
**Rollback:** **if §4.2 is non-zero and the rows are genuinely double-encoded, ABORT.** This plan's
DDL is wrong for that data; it needs the two-phase `MODIFY … VARBINARY` → `MODIFY … utf8mb4` form
and a fresh review.

### Step 1b — Logical dump AND its full verification — *before* the outage window

Run the `mariadb-dump` from §6.3 and **all four** verification checks, including the
restore-into-`dsg_restore_test` + 24 `CHECKSUM TABLE` diff. The app stays up: `--lock-tables` gives
a consistent MyISAM snapshot and the scratch restore touches nothing live.

**Gate:** 24 anchored `^CREATE TABLE`, 24 `CHARSET=latin1`, `Dump completed` trailer, the `$'\x92'`
byte count matching the live 123, and **24 × OK** on the checksum diff.
**Rollback:** delete `backups/*`. Nothing has changed.

> This is the step that used to sit *inside* the outage window and silently turn a 2-minute
> migration into a 7-minute one. It is minutes of work; do it with the site up.

### Step 2 — Quiesce (⚠️ the WHOLE SITE goes down here)

```bash
docker compose stop pente.org          # the only writer to jive* tables
Q "SELECT COUNT(*) app_conns FROM information_schema.PROCESSLIST WHERE USER='$MYSQL_USER';"
```

**Gate:** `app_conns` = 0.

**Before you run this:** `pente.org` is the *single* app service — forums, live game room, tourneys,
AI frontends all stop together. Confirm no live timed games are in progress and no tourney round is
mid-clock (`tb_emergency_time` holds emergency-time state), and announce a **site-wide** outage, not
a forum one. See the warning in §6.1.
**Rollback:** `docker compose start pente.org`. Nothing changed.

### Step 3 — Physical backup (inside the window)

Run the `FLUSH TABLES … WITH READ LOCK` + tar block from §6.3. **Do not skip the FLUSH** —
`delay_key_write=ON` with a 1 GiB key buffer means an unflushed tar can capture a `.MYI` that does
not match its `.MYD`.

**Gate:** `backups/jive-myisam-files-$STAMP.tar.gz` exists and
`tar tzf … | wc -l` = **72** (24 `.frm` + 24 `.MYD` + 24 `.MYI`).
**Rollback:** delete the file; `docker compose start pente.org`.

> Redis (`penteorg-pente_cache-1`) needs no action. It is an ephemeral, best-effort cache for the
> tourney/player storers (`RedisConnectionManager`, `CacheTBStorer`, `CacheTourneyStorer`); the Jive
> `messageCache` is **in-JVM**, so stopping Tomcat is what clears it.

### Step 4 — Confirm both backup artefacts are in hand

Step 1b verified the logical dump; step 3 produced the physical one. Re-confirm both exist and that
`backups/.stamp` still names them, then record the byte-level character counts that §9.2(e) will
diff against:

```bash
Q "SELECT 'bullet_0x95' k, COUNT(*) n FROM jiveMessage WHERE INSTR(CAST(body AS BINARY),UNHEX('95'))>0
   UNION ALL SELECT 'rsquote_0x92', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY),UNHEX('92'))>0
   UNION ALL SELECT 'ellipsis_0x85', COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY),UNHEX('85'))>0
   UNION ALL SELECT 'null_bodies', SUM(body IS NULL) FROM jiveMessage
   UNION ALL SELECT 'total_body_bytes', SUM(OCTET_LENGTH(body)) FROM jiveMessage;" \
  | tee -a backups/fingerprint-pre-$STAMP.txt
# On this stack: bullet 3, rsquote 123, ellipsis 22, null_bodies 0, total_body_bytes 9,457,969
```

**Gate:** both artefacts present; `ls -la backups/jive-*-$STAMP.*` shows two non-trivial files.
**Rollback:** `docker compose start pente.org`. Nothing has changed yet.

**Gate:** 24 `CREATE TABLE`s, 24 `CHARSET=latin1`, `Dump completed` trailer, and the live-vs-restored
`COUNT(*)` **and** `SUM(CRC32(CAST(body AS BINARY)))` match exactly.
**Rollback:** **if any check fails, ABORT and do not proceed.** Re-take the dump. A migration with
an unverified backup has no rollback branch at all.

### Step 5 — Record a pre-migration fingerprint to diff against later

> ### ⚠️ Capture these; do NOT compare against the literals printed in this document.
>
> **This is a live system and row counts drift.** `jiveReadTracker` was 2,259 when §2.2 was
> written and **2,124** a few hours later — measured, not hypothetical. Every §9 gate that compares
> against a number baked into this plan will eventually fail on a perfectly good migration. Capture
> the "before" values here and diff against *those*.

```bash
# Per-table row counts, before.
for t in $JIVE_TABLES; do
  printf '%s %s\n' "$t" "$(Q "SELECT COUNT(*) FROM \`$t\`" -N -B)"
done | tee backups/rowcounts-pre-$STAMP.txt

# The body/subject shape figures the §9.2 gates need. NOTE the two different forms:
# body/subject are still latin1 here, so CONVERT is a real conversion and the correct
# pre-migration test is OCTET_LENGTH(CONVERT(...)) <> OCTET_LENGTH(...).
Q "SELECT 'msgs' k, COUNT(*) n, SUM(CRC32(CAST(body AS BINARY))) ck FROM jiveMessage
   UNION ALL SELECT 'subj', COUNT(*), SUM(CRC32(CAST(subject AS BINARY))) FROM jiveMessage
   UNION ALL SELECT 'body_nonascii_PRE',
     SUM(OCTET_LENGTH(CONVERT(body USING utf8mb4)) <> OCTET_LENGTH(body)), 0 FROM jiveMessage
   UNION ALL SELECT 'ascii_only_PRE',
     SUM(OCTET_LENGTH(CONVERT(body USING utf8mb4))  = OCTET_LENGTH(body)), 0 FROM jiveMessage
   UNION ALL SELECT 'subj_nonascii_PRE',
     SUM(OCTET_LENGTH(CONVERT(subject USING utf8mb4)) <> OCTET_LENGTH(subject)), 0 FROM jiveMessage
   UNION ALL SELECT 'userprop', COUNT(*), SUM(CRC32(CAST(propValue AS BINARY))) FROM jiveUserProp
   " | tee -a backups/fingerprint-pre-$STAMP.txt

export BODY_NONASCII_PRE=...   ASCII_ONLY_PRE=...   SUBJ_NONASCII_PRE=...   # fill from the output
```

On this stack at time of writing: msgs 19,342 / `ascii_only_PRE` 19,077 / `body_nonascii_PRE` 265 /
`subj_nonascii_PRE` 57 / userprop 274,037. **Treat those as illustrations, not gates.**

The `CRC32` over `CAST(… AS BINARY)` is over **raw bytes**, so it *will* change for the 310 rows
that gain multi-byte encodings — that is the point; §9.2 predicts the change rather than requiring
equality.
**Rollback:** n/a.

### Step 6 — THE ALTERs

One statement per table, default database `dsg`, explicit collation. Run them in this order
(small tables first, so a surprise surfaces cheaply):

```sql
-- Paste into: docker exec -i penteorg-main_db-1 mariadb -uroot -p… dsg
ALTER TABLE jiveAttachmentProp CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveCategoryProp   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveForumProp      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveGroupProp      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveGroupPerm      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveGroupUser      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveUserReward     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveUserRoster     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveReward         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveGroup          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveCategory       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveModeration     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveID             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveUser           CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveForum          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveAttachment     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveUserPerm       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveReadTracker    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveThreadProp     CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveMessageProp    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveThread         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveMessage        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveUserProp       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE jiveWatch          CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- optional, §2.3(d):
-- ALTER TABLE spam_messages   CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- ALTER TABLE spam_threads    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- ALTER TABLE spam_users      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

**Do not** wrap these in a transaction — MyISAM ignores it and you gain a false sense of safety.
**Do not** omit `COLLATE` (§2.5). **Do not** fully qualify as `dsg.jiveMessage` without a default
database (§6.5(1)).

**Gate:** every statement returns `Query OK` with `0 warnings`. Any `warnings > 0` → run
`SHOW WARNINGS` immediately and stop.
**Rollback:** restore per §6.4. Partial completion is safe to restore from — the dump has
`--add-drop-table` for all 24, so a re-restore fixes both converted and untouched tables.

### Step 7 — Verify the data (before restarting the app)

Run §9.1 and §9.2. **Gate:** all green.
**Rollback:** §6.4. The app is still down, so no user has seen anything.

### Step 8 — Flip `<characterEncoding>` to UTF-8 — ONLY NOW

Per §5.1. Edit `dockerMain/config/jive3/jive_config.xml` line 202 (the live runtime file), plus the
three repo copies in §5.2.

```bash
# MANDATORY first: Jive writes runtime state (<directory>, <lastIndexed>) into this file — §5.7(c)
cp dockerMain/config/jive3/jive_config.xml "backups/jive_config-$STAMP.xml"

# Surgical single-line edit. Do NOT regenerate or overwrite the file.
# PORTABLE. `sed -i ''` is BSD/macOS-only: GNU sed on the Debian prod host reads the empty
# string as the next FILENAME and fails (or edits the wrong thing). This form works on both.
sed 's|<characterEncoding>ISO-8859-1</characterEncoding>|<characterEncoding>UTF-8</characterEncoding>|' \
  dockerMain/config/jive3/jive_config.xml > /tmp/jc.$$ \
  && cat /tmp/jc.$$ > dockerMain/config/jive3/jive_config.xml && rm -f /tmp/jc.$$

grep -n characterEncoding dockerMain/config/jive3/jive_config.xml   # must now read UTF-8
grep -n '<directory>\|<lastIndexed>' dockerMain/config/jive3/jive_config.xml   # must be UNCHANGED
diff "backups/jive_config-$STAMP.xml" dockerMain/config/jive3/jive_config.xml
# EXPECT exactly one changed line. Anything more -> restore the backup and edit by hand.
```

**Gate:** the live file reads `<characterEncoding>UTF-8</characterEncoding>`, the `diff` shows
**exactly one** changed line, and `<directory>dCX5gMyE</directory>` is intact.
**Rollback:** set it back to `ISO-8859-1`. Safe on its own **only because step 6 already ran** —
the reverse order is the production bug.

### Step 9 — Restart the app

```bash
docker compose start pente.org
docker compose logs -f --tail=100 pente.org      # watch for Jive init errors
```

Restarting (rather than reloading) also clears the in-JVM `messageCache`, so the first read of every
thread comes from the converted tables — which is exactly the condition the RCA's revert needed.
**Gate:** container comes up, forum index renders.
**Rollback:** `docker compose stop pente.org`, §6.4, revert step 8, start.

> The `penteorg-pente.org-1` healthcheck already reports **unhealthy** on this stack, with exit
> code 60 (`curl` TLS verification against the self-signed local cert). That predates this work and
> is not a migration signal — judge success from the logs and §9, not from `docker ps`.

### Step 10 — The application-side changes from §5.4

`web.xml` (delete the dead Jive filter + mapping, both files) and the two JSP `contentType`
directives. The JSP edits are live (bind mount); the `web.xml` edits need an image rebuild.

**Gate:** §9.3 end-to-end edit test still passes afterwards.
**Rollback:** `git checkout` the files; rebuild.

### Step 11 — Smoke test end to end

Run §9.3 (the CJK + emoji + curly-quote edit) and §9.4 (the 6-hour cache-TTL check).
**Gate:** the edit survives a `docker restart penteorg-pente.org-1`.
**Rollback:** §6.4 + revert step 8. **This is the last step at which rollback is still cheap** —
after real users start posting emoji, a restore to latin1 loses their content.

### Step 12 — Schema bookkeeping

Per §10. `./dump-schema.sh`, review the diff, commit.
**Gate:** `grep -c 'CHARSET=latin1' dsg_src/sql/schema.sql` drops by 24 and the diff shows
`text` → `mediumtext` on exactly the 10 columns listed in §3.5.
**Rollback:** `git checkout dsg_src/sql/schema.sql`.

---

## 8. What is NOT in this plan (deliberately)

- The 5 non-jive latin1 columns that hold real non-ASCII user text (§2.3(c)) — separate change.
- Cleaning the 4 cp1252-undefined bytes (§4.5) — separate change, touches user content.
- The `\p{Zs}` fold in `admin/pending.jsp` / `admin/forumContent_edit.jsp` (§5.5) — the migration
  makes them harmless; folding is cosmetic.
- Migrating MyISAM → InnoDB. Tempting (transactional DDL would give a real rollback) but a
  completely different change with its own risk profile. **Do not bundle it.**
- `pente_move` / `tb_move` (3.6 GiB / 0.6 GiB, zero character columns) — no benefit, large rewrite.

---

## 9. Verification — every query here is **POST-MIGRATION ONLY**

> See the PRE/POST trap box in §4. The `OCTET_LENGTH(CONVERT(...))` form used throughout §4 is
> **identity-true here** and must not appear in this section. §9 uses `OCTET_LENGTH` vs
> `CHAR_LENGTH` instead. The one exception is §9.1, which reads `information_schema` metadata and
> is side-agnostic.

### 9.1 Schema is what we asked for

```sql
-- (a) all 24 converted, all to the RIGHT collation
SELECT TABLE_COLLATION, COUNT(*) n FROM information_schema.TABLES
WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' GROUP BY 1;
-- EXPECT: utf8mb4_general_ci 24    (NOT utf8mb4_uca1400_ai_ci — see §2.5)

-- (b) no latin1 column left in the jive family
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' AND CHARACTER_SET_NAME='latin1';
-- EXPECT: 0

-- (c) the 10 TEXT->MEDIUMTEXT widenings, and nothing else, changed type
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' AND DATA_TYPE LIKE '%text'
ORDER BY 1;
-- EXPECT: 10 rows, all mediumtext / utf8mb4 / utf8mb4_general_ci (list in §3.5)

-- (d) varchar lengths untouched
SELECT COLUMN_NAME, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, CHARACTER_OCTET_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME='jiveMessage' AND CHARACTER_SET_NAME IS NOT NULL;
-- EXPECT: subject varchar(255) 255/1020 ; creationDate varchar(15) 15/60 ; modificationDate 15/60

-- (e) every index still exists (CONVERT TO rebuilds them; a silently dropped index is a real risk)
SELECT COUNT(DISTINCT CONCAT(TABLE_NAME,'.',INDEX_NAME)) idx_count
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%';
-- EXPECT: 57   (the count measured pre-migration in §3.2)
```

### 9.2 Data survived, byte-for-byte where it should and changed where it must

```sql
-- (a) [POST] row counts unchanged -- diff against what STEP 5 CAPTURED, not against literals.
--     Row counts drift on a live system (jiveReadTracker moved 2,259 -> 2,124 within hours),
--     so a hardcoded expectation eventually fails a good migration.
--   for t in $JIVE_TABLES; do printf '%s %s\n' "$t" "$(Q "SELECT COUNT(*) FROM \`$t\`" -N -B)"; done \
--     > backups/rowcounts-post-$STAMP.txt
--   diff backups/rowcounts-pre-$STAMP.txt backups/rowcounts-post-$STAMP.txt && echo "ALL 24 MATCH"
-- EXPECT: no diff. CONVERT TO must not add or drop a single row.

-- (b) [POST] the ASCII-only majority is byte-identical.
--
--   ⚠️ DO NOT use OCTET_LENGTH(CONVERT(body USING utf8mb4)) = OCTET_LENGTH(body) HERE.
--   That form is correct only while the column is still latin1 (§4.3, §4.6). AFTER the ALTER,
--   `body` IS utf8mb4, CONVERT is the identity function, and the predicate is ALWAYS TRUE.
--   Demonstrated on the already-utf8mb4 player.name: total 64,299, that predicate counts
--   64,299 (i.e. everything), while the correct form counts 64,197 with 102 truly multi-byte.
--   Used here it would return 19,342 against an expected 19,077 and roll back a GOOD migration.
SELECT COUNT(*) unchanged_ascii_bodies FROM jiveMessage
 WHERE OCTET_LENGTH(body) = CHAR_LENGTH(body);
-- EXPECT: $ASCII_ONLY_PRE captured at step 5 (19,077 when this plan was written)

-- (c) [POST] the rows that had high bytes now hold real multi-byte characters
SELECT COUNT(*) now_multibyte FROM jiveMessage
WHERE OCTET_LENGTH(body) > CHAR_LENGTH(body);
-- EXPECT: $BODY_NONASCII_PRE captured at step 5 (265 when this plan was written)
-- (b) + (c) must sum to the total row count.

-- (d) [POST] total body bytes grew by exactly the predicted amount
SELECT SUM(OCTET_LENGTH(body)) total_bytes, MAX(OCTET_LENGTH(body)) max_bytes FROM jiveMessage;
-- EXPECT: max_bytes  33,752      (the longest row is pure ASCII — unchanged)
--         total_bytes 9,461,700  (+/- a few; pre-migration 9,457,969 plus the +3,731 measured
--                                 on the 310-row affected subset in §4.3: 410,331 -> 414,062)
-- A total that grew by much MORE than ~3,731 means something converted twice. Investigate.

-- (e2) MyISAM integrity after 24 table rebuilds — cheap insurance, not strictly required.
--      CONVERT TO is a clean rebuild, so this should be boring. Run it anyway; a rebuild
--      interrupted by an OOM or a full disk is exactly what it catches.
--   docker exec penteorg-main_db-1 mariadb-check -uroot -p"$MYSQL_ROOT_PASSWORD" \
--     --check --medium-check dsg $JIVE_TABLES
--   EXPECT: every table "OK". Anything else -> restore per §6.4, do NOT --repair over it
--           (a repair on a half-converted table can discard rows).

-- (e) [POST] the specific characters landed right.
--     NOTE: MariaDB has NO \uXXXX string escape. '•' is the literal 5-character text
--     "u2022" (HEX -> 7532303232), so `body LIKE '%•%'` silently matches nothing and
--     looks like a failed check. Always build the character from its UTF-8 bytes:
SELECT COUNT(*) bullets  FROM jiveMessage
 WHERE INSTR(body, CONVERT(UNHEX('E280A2') USING utf8mb4)) > 0;   -- U+2022, was 0x95
SELECT COUNT(*) rsquotes FROM jiveMessage
 WHERE INSTR(body, CONVERT(UNHEX('E28099') USING utf8mb4)) > 0;   -- U+2019, was 0x92
SELECT COUNT(*) ellipses FROM jiveMessage
 WHERE INSTR(body, CONVERT(UNHEX('E280A6') USING utf8mb4)) > 0;   -- U+2026, was 0x85
-- EXPECT (pre-migration counts of the raw bytes, which these must now equal):
--   bullets 3, rsquotes 123, ellipses 22    -- all three measured with
--   SELECT COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('95'))>0;  -- 3
--   SELECT COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('92'))>0;  -- 123
--   SELECT COUNT(*) FROM jiveMessage WHERE INSTR(CAST(body AS BINARY), UNHEX('85'))>0;  -- 22
-- (22 matches the 0x85 row in §4.4's histogram.) Record all three byte-level counts in step 4
-- and require equality here. This is the tightest available proof that reinterpretation was 1:1.

SELECT messageID, LEFT(body,90) FROM jiveMessage WHERE messageID=12853;
-- EXPECT: 'Africa\nEgypt\n•\tSardo cheese \n•\tTestouri cheese ...'

-- (f) [POST] no row became NULL or empty
SELECT COUNT(*) newly_null FROM jiveMessage WHERE body IS NULL;
-- EXPECT: the same count as before the migration (record it in step 5)

-- (g) [POST] subjects (OCTET>CHAR, NOT the CONVERT form -- subject is utf8mb4 now, see (b))
SELECT COUNT(*) FROM jiveMessage WHERE OCTET_LENGTH(subject) > CHAR_LENGTH(subject);
-- EXPECT: $SUBJ_NONASCII_PRE captured at step 5 (57 when this plan was written)
```

### 9.3 End-to-end: the edit that could not previously be saved

This is the acceptance test. It must exercise **all three** characters the migration exists for.

```bash
# 0. Precondition: the fixed editform.jsp is in place (bind-mounted, no rebuild needed).
grep -n 'p{Zs}' dsg_src/httpdocs/gameServer/forums/editform.jsp

# 1. Confirm the forum now serves UTF-8 (step 8 + step 9 done):
printf 'GET /gameServer/forums/index.jspa HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' \
  | openssl s_client -quiet -connect 127.0.0.1:443 -servername localhost 2>/dev/null \
  | grep -i '^content-type'
# EXPECT: Content-Type: text/html;charset=UTF-8      (was ISO-8859-1)
```

Then, in a browser logged in as a forum user:

1. Open a thread, click **Edit** on one of your own posts.
2. Leave **"add following text"** CHECKED — this is the path that was broken.
3. Into the body, paste a string containing all four hazards:
   `Q冰艳 — it’s ok 🎲 χρώμα` (CJK, em dash, curly quote, emoji, Greek).
4. Submit. Expect a 302 and the edited post rendered.

Now prove it reached the **database**, not just the cache — this is the step the RCA shows everyone
skipped:

```sql
-- NOTE on escapes -- READ APPENDIX B BEFORE EDITING THIS QUERY.
--   The SQL *string-literal* parser strips a lone backslash before it ever reaches PCRE2, so
--   '[\x{4e00}-...]' arrives as '[x{4e00}-...]' and raises ERROR 1139 'range out of order'.
--   Backslashes must be DOUBLED in the SQL text itself: '[\\x{4e00}-\\x{9fff}]'.
--   For characters in string (non-regex) context use CONVERT(UNHEX(...) USING utf8mb4).
SELECT messageID,
       INSTR(body, CONVERT(UNHEX('F09F8EB2') USING utf8mb4))>0 AS has_emoji,   -- U+1F3B2 🎲
       body REGEXP '[\\x{4e00}-\\x{9fff}]'                   AS has_cjk,
       INSTR(body, CONVERT(UNHEX('E28099') USING utf8mb4))>0   AS has_rsquote, -- U+2019 ’
       INSTR(body, CONVERT(UNHEX('E280AF') USING utf8mb4))>0   AS has_u202f,   -- U+202F
       CHAR_LENGTH(body) AS chars, OCTET_LENGTH(body) AS bytes
FROM jiveMessage WHERE messageID = <the messageID you edited>;
-- EXPECT: has_emoji 1, has_cjk 1, has_rsquote 1.
--   has_u202f may be 0 — the editform.jsp \p{Zs} fold removes it. That is fine; the point is the
--   column would now ACCEPT it (proven in §1), so the fold is belt-and-braces, not load-bearing.
```

Also test the **CJK-username** path (RCA defect 1) specifically, since none of the 12 has ever
posted (§2.4). Either log in as one of them and edit a post, or synthesise it:

```sql
-- read-only check that the interpolated comment now round-trips:
SELECT p.name,
       CONVERT(CONVERT(p.name USING latin1) USING utf8mb4) = p.name AS was_cp1252_safe_before
FROM player p WHERE p.pid IN (23000000046029, 40000000003252);
-- EXPECT: was_cp1252_safe_before = 0 for both — i.e. these are exactly the names that used to
--         make the UPDATE fail, and which a utf8mb4 body column now stores fine.
```

### 9.4 The 6-hour test — do not declare victory before this

The RCA's failure mode was invisible for up to 6 hours (`messageCache` TTL `6 * 3600000` ms). So:

```bash
# Force the cache to be irrelevant: restart, then re-read the edited thread.
docker restart penteorg-pente.org-1
# wait for the container, then open the same thread URL again in the browser.
```

**Gate:** the edited text is still there after the restart. If it reverts, the `UPDATE` failed and
was swallowed again — check `dockerMain/logs/tomcat/` for a `Log.error` from
`DbForumMessage.saveToDb`, and treat it as ABORT + §6.4.

This is the same technique the RCA used to reproduce the bug on demand, run in reverse as a proof
of fix. **Keep the backups until this passes.**

### 9.5 Regression check on the paths that were already working

```sql
-- forum listing / thread listing still resolve (exercises the rebuilt indexes)
EXPLAIN SELECT messageID FROM jiveMessage WHERE forumID=1 AND modValue=1;
-- EXPECT: key jiveMessage_forumID_modVal_idx, key_len 16 (numeric parts, charset-independent)
EXPLAIN SELECT messageID FROM jiveMessage WHERE creationDate='001173589387198';
-- EXPECT: key jiveMessage_cDate_idx, key_len 62 (was 17 — this is the ×4 inflation, expected)

-- the Jive user-property path used by org.pente.jive.SimpleUserAdapter:42
SELECT COUNT(*) FROM jiveUserProp WHERE userID=(SELECT userID FROM jiveMessage LIMIT 1);
-- EXPECT: a non-zero count AND no ERROR 1267. "It didn't error" is only half the gate --
--         a query that returns 0 rows also doesn't error.

-- no illegal-mix-of-collations anywhere on the message->author join
SELECT COUNT(*) FROM jiveMessage m JOIN player p ON p.pid=m.userID;
-- EXPECT: exactly the jiveMessage row count captured at step 5 -- an equality, not a vibe.
--         A short count means the join silently dropped rows; ERROR 1267 ("Illegal mix of
--         collations") means it broke loudly. Both are failures; only one announces itself.
```

Browser-level: forum index, a thread in each of the threaded/flat/tree views, post a new reply,
search, and one admin page (`forums/admin/…`, which renders
`charset=<%= JiveGlobals.getCharacterEncoding() %>` and should now say UTF-8).

### 9.6 Replica agreement — MANDATORY, and MANDATORY AGAIN after any rollback

Replication is live (§6.5). Master and replica must never disagree about the schema, and **nothing
surfaces an error when they do** — this check is the only thing standing between you and a silent
split-brain.

```bash
R() { docker exec penteorg-replica_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" "$@"; }  # no -N/-B

# 1. Replica applied the DDL and is healthy
R -e "SHOW SLAVE STATUS\G" | grep -cE 'Slave_IO_Running: Yes|Slave_SQL_Running: Yes'
# EXPECT the NUMBER 2. Not "no errors" -- a count. If the command fails, or the labels are
# missing (the -N trap), grep prints 0 and you see a failure instead of reassuring silence.
R -e "SHOW SLAVE STATUS\G" | grep -E 'Seconds_Behind_Master|Last_SQL_Error'
# EXPECT: Seconds_Behind_Master small, Last_SQL_Error empty -- read these, do not just glance.

# 2. Both sides agree on collation — the actual split-brain test.
#    GROUP BY TABLE_COLLATION, *not* GROUP BY 1: ordinal 1 refers to the aliased CONCAT, which
#    contains COUNT(*), and MariaDB rejects it with "ERROR 1056 Can't group on ...". Do NOT add
#    2>/dev/null here -- that is what hid this very bug during drafting. Let errors be seen.
for side in main replica; do
  printf '%-9s' "$side:"
  docker exec penteorg-${side}_db-1 mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e \
    "SELECT CONCAT(TABLE_COLLATION,'=',COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA='dsg' AND TABLE_NAME LIKE 'jive%' GROUP BY TABLE_COLLATION;" \
    2>&1 | grep -v 'Using a password'
done
# EXPECT after the migration, both lines:  utf8mb4_general_ci=24
# BEFORE the migration both read           latin1_swedish_ci=24   <-- verified on both containers
#                                                                     2026-09-22, they agree today.
# ANY disagreement between the two lines = split-brain. Stop and reconcile.
# An EMPTY line is NOT a pass -- it means the query errored. Investigate before continuing.
```

> **Run section 9.6 again after ANY rollback.** A logical restore is binlogged and reverts both
> sides; a **physical** `.MYD`/`.MYI` restore is not, and leaves master latin1 with replica
> utf8mb4. See the rollback hazard box in §6.5. If you took option (a) (`STOP SLAVE`), the replica
> will still be utf8mb4 after a physical restore and **must be re-seeded from the restored master**
> before `START SLAVE`.

---

## 10. Schema-file bookkeeping

`dsg_src/sql/schema.sql` is **generated, not hand-edited**. The generator is
**`./dump-schema.sh`** (repo root):

```bash
docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$CONTAINER" \
  mariadb-dump -u"${MYSQL_USER:-dsg_rw}" --no-data --skip-comments --routines --events --triggers \
  "${MYSQL_DATABASE:-dsg}" > "$tmp"
sed -E 's/ AUTO_INCREMENT=[0-9]+//' "$tmp" > dsg_src/sql/schema.sql
```

Container defaults to `penteorg-main_db-1` (override with `DB_CONTAINER`); creds come from `.env`.
`AUTO_INCREMENT` counters are stripped so the file diffs cleanly. It is **schema only** — the full
data dump that bootstraps a fresh Docker DB lives separately in `dockerMain/dbInit/*.sql.gz`.

**When to run it:** step 12, immediately after §9.1 passes and before committing. Not before — you
want the file to describe the migrated reality, and running it mid-migration would capture a
half-converted schema.

```bash
./dump-schema.sh
git diff --stat dsg_src/sql/schema.sql
grep -c 'CHARSET=latin1' dsg_src/sql/schema.sql       # was 71, EXPECT 47  (71 - 24)
grep -c 'mediumtext'     dsg_src/sql/schema.sql       # EXPECT +10 vs before
git diff dsg_src/sql/schema.sql | grep -E '^[+-].*(CHARSET|mediumtext|text)' | head -60
```

**Review the diff, do not just commit it.** The expected diff is exactly:

- 24 × `DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci` → `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci`
- 10 × `` `col` text `` → `` `col` mediumtext `` (the list in §3.5)
- nothing else

Anything else in that diff — a dropped index, a changed `varchar` length, a table you did not
intend to convert, an `AUTO_INCREMENT` leak — is a **finding, not noise.** Investigate before
committing.

### 10.1 ⚠️ `dockerMain/dbInit` is a second source of truth, and it will be stale

**Confirmed, not speculated.** `dockerMain/dbInit/penteDBdocker-20260922.sql.gz` (830 MB, dated
today) is the full data dump that bootstraps a fresh local DB, and it currently contains:

```
grep -c 'CREATE TABLE'    -> 80
grep -c 'CHARSET=latin1'  -> 70     # includes all 24 jive tables
```

It is **not** regenerated by `dump-schema.sh`. So after this migration:

> Anyone who rebuilds their stack from `dbInit` gets **latin1 jive tables back** while their
> `jive_config.xml` (if taken from a post-migration repo copy, §5.2) says **UTF-8** — which is
> precisely **the harmful combination from §5.1**. They will reproduce the original production bug
> on a "fresh" stack and have no reason to suspect the bootstrap dump.

**Required follow-up:** either re-take that dump after the migration, or add a one-line note beside
it recording that a fresh bootstrap needs the §7 step-6 `ALTER`s applied afterwards. Do not leave
this undone — it is the most likely way this migration silently un-does itself.

---

## 11. Risk assessment — honest version

### 11.0 This document was adversarially reviewed, and the review found real bugs

An independent review pass (2026-09-22) found **2 blockers and 6 majors**, all verified against the
live DB and all now fixed. Recording them because they are the failure modes this kind of plan
actually has:

| # | what was wrong | why it mattered |
|---|---|---|
| B1 | Every `REGEXP '[^\x00-\x7F]'` used a **single** backslash, which MariaDB's string parser strips | §9.2(b)'s gate returned **148 instead of 19,077** — a *correct* migration would have failed its own verification. §4.6 returned 19,100 instead of 57. Now escape-free (`OCTET_LENGTH(CONVERT(...))`), see Appendix B |
| B2 | Appendix B attributed a `\u` error to a statement containing no `\u` | the guidance was incoherent and the "put the SQL in a file" advice was exactly backwards |
| M3 | `zgrep -c 'CREATE TABLE'` unanchored | **6 forum bodies contain the literal text "CREATE TABLE"** and ride inside INSERT lines, so the count exceeded 24, the `= 24` test failed, and step 4 would have **aborted a good backup** |
| M4 | `$STAMP` recomputed by `date` in two places | later steps pointed at files that do not exist |
| M5 | physical tar taken without `FLUSH TABLES` | `delay_key_write=ON` + 1 GiB key buffer ⇒ the `.MYI` can mismatch its `.MYD`; the "backup" restores a corrupt index |
| M6 | `mariadb-check --repair dsg 'jive*'` | globs are not accepted; the *only* physical recovery path errored out |
| M7 | the 2-minute downtime budget excluded the restore-verify it mandates | a 525,000-row scratch restore sat inside the outage window |
| M8 | called the outage "the forum" | it is the **whole site**; live timed games get cut mid-clock |

> **Provenance note.** The review was read-only by design, so anything needing a `CREATE TABLE`
> was *not* independently confirmed — including §3.1's `varchar(250)` passes / `varchar(251)` fails
> probe and the 24 clone `ALTER`s in §3.4. Those rest on this plan's own `_tmp_*` measurements
> (disclosed in §3.6), not on the review. The direction is harmless: if the 2 varchar length bytes
> are not counted toward the 1000-byte limit, §3.2's model over-estimates and every headroom figure
> understates, so "max 410 B, nothing near 1000" only gets stronger.

**A second round found four more, two of them worse than anything above:**

| # | what was wrong | why it mattered |
|---|---|---|
| F1 | "replication is not running" — from `-N -B` with `\G`, which strips the labels the grep needed | **the replica IS live and caught up.** The 24 ALTERs propagate; a physical rollback silently split-brains master vs replica. Drove the wrong rollback strategy |
| F2 | printed `REGEXP` queries were broken even though the *numbers* beside them were right | the evidence was not reproducible; re-running §4.7's gate would spuriously ABORT |
| F3 | `OCTET_LENGTH(CONVERT(body USING utf8mb4)) = OCTET_LENGTH(body)` as a **post**-migration gate | `body` is utf8mb4 by then, so `CONVERT` is the identity and the predicate is **always true** — returns 19,342 vs an expected 19,077 and rolls back a **correct** migration. A bug I introduced while fixing B1 |
| F4/F6 | `sed -i ''` (BSD-only, breaks on the Debian prod host); no pre-migration `mariadb-check`; row-count literals baked into gates (`jiveReadTracker` drifted 2,259 → **2,124** within hours) | each turns a good migration into an apparent failure |

**Takeaway for the operator:** the dangerous bugs here were in the *verification and backup*
scaffolding, not in the `ALTER` statements. Note F3 in particular — it was introduced *by the fix
for B1*. Fixes need the same scrutiny as the code they repair.

### 11.0.1 The transferable lesson — ask two questions, not one

Every defect above was one shape: **a check that could not fail, or that failed for a reason
unrelated to what it claimed to test.** B1, the `CONVERT` tautology, the `CHECKSUM` false-pass and
the `-N`-induced replica misreading are four instances of it.

The reason it survived a thorough review round is worth recording, because it is the part that
generalises. Both review passes asked:

> **Q1 — "Is this number right?"**  Every stated number was re-measured, and every one held:
> 265 / 57 / 310, +0.909 %, 57 indexes, 0 FULLTEXT, 12 names, 22/123/3, 424 GiB, 24 `.frm`, 71→47.

and largely did not ask:

> **Q2 — "Can the query as printed actually produce the number attributed to it?"**

Q2 is where every real defect lived. `SUM(subject REGEXP '[^\x00-\x7F]')` returns 19,100 beside a
correct "57". `mariadb -N -B -e "SHOW SLAVE STATUS\G"` returns nothing beside a confident
"replication not running". The numbers were right; the evidence for them was not reproducible.

**So when reviewing or re-running this plan: run the query, do not read the answer.** And prefer
gates that count confirmations over gates that assert an absence — the strongest pattern in this
document is §9.6's replica check, which expects the **number 2** and demonstrably returns **0**
under the very trap that caused F1. A gate you have never seen fail is a gate you have not tested. Distrust a check that passes without you understanding
why, and re-run §9's queries against the pre-migration database first — every one of them should
return the "before" value. A check that cannot fail is not a check.

### 11.1 Overall: LOW risk for the DDL, MEDIUM for the surrounding config

The database change itself is about as safe as a schema migration gets, and I can say that with
evidence rather than optimism:

| why it is low-risk | evidence |
|---|---|
| No index comes close to the MyISAM limit | max 410 of 1000 bytes (§3.2); 24/24 clone `ALTER`s succeeded (§3.4) |
| No truncation is possible | MariaDB widens `text`→`mediumtext` (§3.5); `varchar` keeps character length (§4.6) |
| No data converts wrong | 0 mojibake rows (§4.2); 310/310 byte-exact on the affected set (§4.3) |
| latin1 really is cp1252 here | `0x92`→`E28099` proven (§4.1) |
| The window is tiny | < 3 s of `ALTER` (§6.1) |
| Disk is a non-issue | 424 GiB free vs 66 MiB of tables; net −7 MiB (§6.2) |
| Rollback is a verified dump | §6.3 includes a restore-and-checksum-diff gate |
| The fix is provably the right one | the exact ERROR 1366 was reproduced on latin1 and shown to succeed on utf8mb4 (§1) |

### 11.2 Things that make it riskier than it looks — ranked

**1. The ordering trap is a foot-gun with a silent failure mode. (highest)**
Flipping `<characterEncoding>` to UTF-8 before the `ALTER`s **reproduces the production bug**, and
that bug is invisible for 6 hours. There are **four** copies of `jive_config.xml`, **two of them
gitignored**, and the one that matters at runtime
(`dockerMain/config/jive3/jive_config.xml`) is not the one in git. It is entirely plausible for
someone to edit the tracked copy, see no effect, and then edit the runtime copy on a different day
from the migration. §5.1 and §7 step 8 exist to prevent exactly that. **If you do one thing from
this document, respect the order.**

**2. Production may already be in the harmful state, and I could not check.**
The RCA's indirect argument (zero `?AM`/`?PM` rows) says production serves UTF-8 over latin1
tables — i.e. the migration is not an improvement there, it is a *repair*. But production is off
limits for this exercise, so **that is an inference, not a measurement.** If production is actually
ISO-8859-1, its failure mode is "mangled `?`" rather than "silent revert", the urgency drops, and
the ordering rule becomes even more important because you would be *introducing* UTF-8 serving.
**Read production's `jive_config.xml` before touching production.**

**3. Replication is LIVE, and two separate mechanisms can silently split-brain the schema. (§6.5)**
An earlier draft of this plan said the replica was not running. **That was wrong** — it came from
`mariadb -N -B -e "SHOW SLAVE STATUS\G"`, where `-N` strips the labels that the grep was looking
for. Correctly measured: `Slave_IO_Running: Yes`, `Slave_SQL_Running: Yes`,
`Seconds_Behind_Master: 0`, `Replicate_Do_DB: dsg`. Two ways to diverge, neither of which raises an
error anywhere:

- **Forward:** `replicate_do_db` filters DDL by the session's *default database*. An
  `ALTER TABLE dsg.jiveMessage …` issued without `USE dsg` is **skipped by the replica** — master
  utf8mb4, replica latin1.
- **Backward:** a **physical** `.MYD`/`.MYI` restore bypasses the binlog — master back to latin1,
  replica still utf8mb4.

Mitigated by the hard gate in step 0a, by always passing the database name to `mariadb`, by
choosing a rollback option up front, and by §9.6 — which must be re-run after any rollback.

**The meta-lesson is worse than the finding:** a confidently-stated "measurement" that was actually
a broken command drove a rollback strategy. Prefer label-free cross-checks
(`SELECT COUNT(*) FROM information_schema.SLAVE_STATUS`) over grepping formatted output.

**4. `jive_config.xml` is not a static config file — Jive writes search state into it. (§5.7(c))**
`<directory>dCX5gMyE</directory>` names the 5.3 MiB Lucene index on disk and `<lastIndexed>` is an
incremental watermark. A careless step 8 — regenerating the file, or copying a repo copy over the
runtime one to "get UTF-8 in there" — orphans the index, and **forum search then returns zero
results with no error in any log.** That is a worse user-visible regression than the bug being
fixed, and it would be blamed on the migration rather than on the file edit. Mitigated by the
`cp` + one-line `sed` + `diff` gate now in step 8.

**5. `dockerMain/dbInit` re-introduces latin1 on any fresh stack. (§10.1)**
Confirmed 70 `CHARSET=latin1` in today's 830 MB bootstrap dump. This is how the migration quietly
un-does itself six months from now.

**6. MyISAM gives you no transactional rollback.**
Each `ALTER` is independent and irreversible. A power loss between table 12 and table 13 leaves a
mixed-charset schema. Recovery is the §6.4 restore, which is fine — but it means the **verified**
backup in step 4 is not ceremony, it is the only safety net. I would not run step 6 without step 4
having passed.

**7. `CONVERT TO` rebuilds every index; a silently dropped index would be a slow, invisible
regression.** Hence the index-count check in §9.1(e) (expect 57) and the `EXPLAIN` checks in §9.5.
I have no reason to think MariaDB drops indexes here — the 24 clone `ALTER`s preserved them — but
"the forum got slow three weeks later" is a bad way to find out.

**8. The `text`→`mediumtext` widening is an unrequested schema change.**
Harmless (§3.5) but it *will* show up in the `schema.sql` diff and in any future tooling that
introspects column types. Someone reviewing that commit needs to know it is expected, which is why
§10 spells out the exact diff shape.

### 11.3 What I could not verify, stated plainly

- **Production's `<characterEncoding>`, and production's replica state.** Off limits by
  instruction. Both must be read on the box.
- **The JDBC connection charset, read from a live app connection.** Inferred from
  `mariadb-java-client-3.5.9` defaults plus the ERROR 1366 mechanism (§5.3). Confident, but
  inferred. A one-line throwaway JSP would settle it.
- **Why the `contentType="text/html; charset=UTF-8"` directive in `thread-flat.jsp` is inert**
  (§5.4(b)). Measured that `thread.jspa` serves ISO-8859-1; did not trace whether the default view
  is `threaded` or whether something overrides after the JSP. Does not change the recommendation.
- **Whether the 4 cp1252-undefined bytes (§4.5) were originally `0x92`.** The context in messageID
  254659 (`while I?m thinking`) strongly suggests it, but I am not going to `UPDATE` user content on
  a strong suggestion.
- **Timing under production load.** The < 3 s figure is from an idle local stack. MyISAM takes a
  table lock, so on a busy server the `ALTER` waits for in-flight readers to drain before it starts;
  the rebuild itself will not be slower, but the *wall clock* from issuing the statement to it
  completing can be. Quiescing the app (step 2) removes this entirely — do not skip it.

### 11.4 Recommendation

**Proceed.** Run it on the local stack first, end to end including §9.4's 6-hour cache test, then
schedule the production window. The evidence for the DDL is about as strong as this kind of change
allows, the irreversible portion is under 3 seconds, and the rollback is a checksum-verified dump.

The risk is not in the `ALTER` statements. It is in the four `jive_config.xml` copies, the stale
bootstrap dump, and the replica filter — three pieces of configuration that fail **silently and
late**. Budget your attention accordingly.

---

## Appendix A — one-line summary of every measurement

| # | claim | value | where |
|---|---|---|---|
| 1 | latin1 tables in `dsg` | 70 (all MyISAM) | §2.1 |
| 2 | tables migrated | **24** (all `jive*`) | §2.2 |
| 3 | tables not migrated | 46 non-jive latin1 | §2.3 |
| 4 | existing utf8mb4 tables | 10, all `utf8mb4_general_ci` | §2.1 |
| 5 | widest index after ×4 | **410 B** (7 × `*Prop` PK) | §3.2 |
| 6 | MyISAM key limit | 1000 B (proven: vc251 fails, **vc250 passes** — cap is 250 chars) | §3.1 |
| 7 | indexes exceeding the limit | **0** | §3.2, §3.4 |
| 8 | total indexes on `jive*` | 57 | §9.1(e) |
| 9 | `text` → `mediumtext` columns | 10 | §3.5 |
| 10 | `jiveMessage.body` max bytes | 33,752 (unchanged; longest row is ASCII) | §3.5 |
| 11 | mojibake / double-encoded rows | **0** (2 false positives reviewed) | §4.2 |
| 12 | bodies with cp1252 high bytes | 265 of 19,342 | §4.4 |
| 13 | subjects with cp1252 high bytes | 57 | §4.6 |
| 14 | conversion fidelity on affected rows | **310 / 310 exact** | §4.3 |
| 15 | body bytes before → after | 9,457,969 total; +0.9 % on the 310 affected | §4.3, §9.2 |
| 16 | cp1252-undefined bytes | 4 bodies (1× `0x81`, 3× `0x9D`) — accepted | §4.5 |
| 17 | non-cp1252 player names | **12** | §2.4 |
| 18 | forum messages authored by those 12 | **0** (defect is latent) | §2.4 |
| 19 | `ALTER` time, heaviest table | 778 ms (`jiveWatch`, 231,885 rows) | §6.1 |
| 20 | total `ALTER` window | **< 3 s** | §6.1 |
| 21 | forum downtime, end to end | ≈ 2 min (dominated by Tomcat restart) | §6.1 |
| 22 | on-disk size change | ≈ **−7 MiB** (index repacking) | §6.2 |
| 23 | free space | 424 GiB vs 66 MiB of tables | §6.2 |
| 24 | `jive_config.xml` copies | 4 (2 tracked, 2 gitignored) | §5.2 |
| 25 | live runtime config | `dockerMain/config/jive3/` → `/etc/dsg/jive3` | §5.2 |
| 26 | served charset today | ISO-8859-1 (measured on 3 forum URLs) | §5.4(b) |
| 27 | replica state | **replication LIVE**, IO+SQL Yes, 0 s behind, `Replicate_Do_DB=dsg` | §6.5, §9.6 |
| 28 | `latin1` occurrences in `schema.sql` | 71 → expect 47 | §10 |
| 29 | `CHARSET=latin1` in `dbInit` dump | **70** — will be stale | §10.1 |
| 30 | Java source changes required | **none** | §5.6 |
| 31 | MySQL `FULLTEXT` indexes in `dsg` | **0** (search is on-disk Lucene) | §5.7(a) |
| 32 | Lucene reindex needed | **probably not — INFERRED, not measured** (rests on the JDBC charset in §5.3, itself inferred) | §5.7(b) |
| 35 | `dsg` database default collation | `utf8mb4_uca1400_ai_ci` — unchanged by this migration | §2.5 |
| 36 | bodies containing literal text `CREATE TABLE` | 6 (breaks an unanchored backup check) | §6.3 |
| 37 | `delay_key_write` | **ON**, key buffer 1 GiB — `FLUSH` before any file copy | §6.3 |
| 38 | app services in `docker-compose.yml` | **1** (`pente.org`) — the whole site stops | §6.1 |
| 39 | ellipsis `0x85` bodies | 22 | §9.2(e) |
| 40 | MyISAM files to back up | 72 (24 `.frm` + 24 `.MYD` + 24 `.MYI`) | §6.3 |
| 33 | `jiveAttachment.fileName` non-ASCII | 0 of 272 | §5.7(d) |
| 34 | `jiveID` sequence rows | 7 | §5.7(e) |

### Appendix B — the backslash trap. Read this before writing any check query.

**This is the single easiest way to make a correct migration look broken.** An earlier draft of this
document got it wrong in a way that would have failed §9.2(b) on a perfectly good migration, so it
is spelled out in full. All outputs below were measured by piping SQL from a **file** (no shell
involved), so nothing here is a quoting artefact.

#### The rule

**MariaDB's string-literal parser strips a lone backslash before the value ever reaches PCRE2 or
`LIKE`.** It is not the shell. Proof:

```sql
SELECT HEX('[^\x00-\x7F]');   -- 5B5E7830302D7837465D  =  [^x00-x7F]   ← the \x is GONE
SELECT CHAR_LENGTH('\x41'), '\x41';        -- 3, 'x41'   (not 1, not 'A')
SELECT 'A' REGEXP '\x41';                  -- 0   ← silently wrong
SELECT 'A' REGEXP '\\x41';                 -- 1   ← correct
SELECT HEX('[^\\x00-\\x7F]');              -- 5B5E5C7830302D5C7837465D  =  [^\x00-\x7F]  ✓
```

So **every backslash must be doubled in the SQL text itself**, independently of how the SQL is
delivered. Doubling again for a shell layer is a *separate* concern on top of this.

#### What the single-backslash form actually returns

| query | intended | actually returns |
|---|---:|---:|
| `SUM(body NOT REGEXP '[^\x00-\x7F]')` | 19,077 | **148** |
| `SUM(body REGEXP '[^\x00-\x7F]')` | 265 | **19,194** |
| `SUM(subject REGEXP '[^\x00-\x7F]')` | 57 | **19,100** |
| `SUM(fileName REGEXP '[^\x00-\x7F]')` | 0 | **272** |

Doubled, all four are correct: 19,077 / 265 / 57 / 0. Verified.

#### Best practice: don't use a regex for the ASCII test at all

Every non-ASCII check in this document now uses a form with **no escapes to get wrong**, which
works because a latin1 column's octet length only grows when a byte maps to a multi-byte character:

```sql
-- non-ASCII rows, escape-free. Returns 265 / 57 / 310 — identical to the correct regex.
SELECT SUM(OCTET_LENGTH(CONVERT(body    USING utf8mb4)) > OCTET_LENGTH(body))    body_nonascii,
       SUM(OCTET_LENGTH(CONVERT(subject USING utf8mb4)) > OCTET_LENGTH(subject)) subj_nonascii
FROM jiveMessage;
```

Post-migration the same idea reads `OCTET_LENGTH(col) > CHAR_LENGTH(col)` (see §9.2(c)).

#### Two more escapes that do not exist

1. **There is no `\uXXXX` string escape.** `'•'` is the literal 5-character text `u2022`
   (`HEX` → `7532303232`, `CHAR_LENGTH` → 5), so `body LIKE '%•%'` matches nothing and reads
   like a failed verification. **Use `CONVERT(UNHEX('E280A2') USING utf8mb4)`.**
2. **PCRE2 rejects `\u` even when it survives to the engine.** With the backslash doubled so PCRE2
   really sees `\u`:
   `SELECT '冰' REGEXP '[\\u4e00-\\u9fff]'` →
   `ERROR 1139 (42000): Regex error 'PCRE2 does not support \F, \L, \l, \N{name}, \U, or \u at offset 3'`.
   **Use `\x{...}`, doubled:** `'冰' REGEXP '[\\x{4e00}-\\x{9fff}]'` → 1, `'abc'` → 0. ✓
   With a *single* backslash it instead fails as
   `ERROR 1139 … 'range out of order in character class at offset 9'` — because PCRE2 received the
   literal class `[x{4e00}-x{9fff}]`. Two different errors, same root cause.

A literal-character class also works and sidesteps escapes entirely: `'冰' REGEXP '[一-鿿]'` → 1.
