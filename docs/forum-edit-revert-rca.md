# Forum post edits revert to the original — root cause analysis

**Reported:** forum thread 233491, 2026-06-03 — *"the edits end up reverting to the original
post after a time (an hour or so, give or take). Also … when I go to edit a post which I've
already edited once, what it gives me to edit is the original post."*
A second user added: *"the issue primarily occurs if you **don't** uncheck the 'add the
following text' box."* That hint was correct.

**Investigated:** 2026-09-22, against the local Docker stack. Production was not touched.

---

## Root cause

`editform.jsp` appends a "Message was edited by: {user} at {date}" comment to the body. Under
**JDK 20+ (CLDR 42)** the en_US `MEDIUM`/`SHORT` date emits **U+202F NARROW NO-BREAK SPACE**
before AM/PM. `jiveMessage.body` is `text` on a **MyISAM latin1** table (MariaDB latin1 =
cp1252) and `sql_mode` has `STRICT_TRANS_TABLES`, so U+202F is a hard **ERROR 1366**, not a
truncation.

The error never reaches the user:

1. `DbForumMessage.saveToDb` (~line 1075) catches `java.lang.Exception`, calls `Log.error`,
   sets a rollback flag and **returns normally**.
2. `setBody()` then runs `messageCache.put(id, this)` **unconditionally** — DB = OLD,
   cache = NEW.
3. `EditAction.doExecute` sees no exception, returns `"success"`, browser gets a 302.
4. `thread.jspa` reads through `DatabaseCacheManager.getMessage()` → cache hit → **the user
   sees their edit applied**.
5. `messageCache` TTL is `6 * 3600000` ms. On eviction, LRU cull or Tomcat restart the next
   read reloads from the DB and **the original returns**.
6. The edit form reads the DB, which is why re-editing shows the original.

Observed log line:

```
2026.09.22 08:32 [com.jivesoftware.forum.database.DbForumMessage.saveToDb(DbForumMessage.java:1075)]
java.sql.SQLSyntaxErrorException: (conn=1530) Incorrect string value:
  '\xE2\x80\xAFAM\x0A' for column `dsg`.`jivemessage`.`body` at row 1
```

### Proof — controlled A/B on the live stack

| # | request | HTTP | DB body |
|---|---------|------|---------|
| D | comment with plain `0x20` before AM | 302 | **changed** |
| E | byte-identical, real U+202F | 302 | **unchanged** |
| I | `addComment` omitted | 302 | **changed** |
| J | `addComment=true` + U+202F | 302 | **unchanged** |
| F/G | `U+2019` / `U+2014` in body | 302 | **changed** (cp1252 maps them to 0x92/0x97) |

The revert itself was reproduced on demand: after a failed POST `thread.jspa` showed the new
text; after `docker restart penteorg-pente.org-1` cleared the cache, the same URL showed the
original.

### Regression window

JDK 21 reached production in the **2023-11-05 … 2023-11-19** container cutover. The last edit
comment that ever landed is messageID 265026, **2023-11-13**, whose stored bytes end
`32 20 50 4D` — a plain `0x20`, i.e. a pre-JDK-20 runtime. Zero edit comments have landed
since, across 19,342 messages. Pre-break, 29% of re-modified rows carried one.

---

## Fix applied

Three files, same one-line change. Fold Unicode space separators out of the timestamp before
it goes into the body:

| file | how the date is built | verified |
|------|----------------------|----------|
| `forums/editform.jsp` | `DateFormat.getDateTimeInstance(MEDIUM, SHORT)` | end-to-end, user edit |
| `forums/admin/forumContent_edit.jsp` | `SkinUtils.formatDate(request, pageUser, …)` | end-to-end, admin edit |
| `forums/admin/pending.jsp` | `SkinUtils.formatDate(request, pageUser, …)` | Jasper compile + identical code path |

`javap` confirms `SkinUtils.formatDate` also calls
`DateFormat.getDateTimeInstance(MEDIUM, SHORT, …)`, so the two admin pages had the identical
defect and the same silent save path (`message.setBody(body + "\n" + editedByText)`). That is
also why `SkinUtils` would *not* have been a better fix for `editform.jsp` — it is the same
defect source.

In `editform.jsp`, format the date as before, then fold
Unicode space separators to a plain space:

```java
String editedAtDate = formatter.format(new java.util.Date()).replaceAll("\\p{Zs}", " ");
```

JSP only. `dsg_src/httpdocs/gameServer` is bind-mounted into the container, so Jasper
recompiles on the next request — no `./justCompile`, no restart, no DB change.

**Verified — user edit path.** Rendered form emits `Sep 22, 2026, 8:32 AM` with byte `0x20`
before AM (was `0x3F`); POSTing the fixed form's own comment with `addComment=true` → 302 and
the DB body changed; negative control with a raw U+202F → 302 but body unchanged.

**Verified — admin moderation path.** Logged in as `rainwolf` (player login, then
`admin/login.jsp`), `forumContent_edit.jsp?forum=1&thread=233620&message=268560` renders the
textarea as `[Edited by: rainwolf on Sep 22, 2026, 11:18 AM]`, hex `… 31 31 3a 31 38 20 41 4d 5d`
— `0x20` before AM. Save → 302 and the DB body changed. Negative control with a raw U+202F in
`editedByText` → 302 but body unchanged.

In both cases the negative control still fails, which is the point: the underlying landmine is
untouched and the fix works by never emitting the character. `JspC … -compile` reports
`COMPILED OK` for all three files.

Note `LoginFilter:243-256` returns **404** (not 403) for the whole admin area unless the player
login is valid *and* `DSGPlayerData.isAdmin()`, so an admin URL 404ing is an auth signal, not a
missing file.

`\p{Zs}` was verified as the correct class: it is a Unicode general category and needs no
`UNICODE_CHARACTER_CLASS`, whereas `\s` does **not** match U+202F by default and would have
been a silent no-op. A sweep of all 1,069 `DateFormat` locales: every `en_*` locale, and all
16 en_US style combinations, are latin1-safe after the fold.

---

## Resolved: production serves UTF-8

`jive_config.xml` is gitignored and legitimately differs per host, so this had to be measured
rather than assumed. Confirmed 2026-09-22 against production (read-only, unauthenticated):

```
GET /gameServer/forums/forum.jspa?forumID=1   -> 200  Content-Type: text/html;charset=UTF-8
GET /gameServer/forums/edit!default.jspa      -> 200  Content-Type: text/html;charset=UTF-8
```

This was predicted by the DB: the production DB has **zero** rows containing `?AM`/`?PM` across
19,342 messages, and the ISO-8859-1 path demonstrably writes `?AM`, so those rows would exist
if production served ISO-8859-1.

Two consequences:

- **The reported symptom is confirmed as the U+202F path.** On a UTF-8-served form the narrow
  no-break space reaches the browser intact, is resubmitted as `%E2%80%AF`, and hits the latin1
  column — 1366, swallowed, cached, reverts. The fix above addresses exactly this.
- **This local stack is ISO-8859-1 — config drift, not production behaviour.** Defect 3 below
  (HTTP 400 on typed accents, and `?`-mangling of stored apostrophes) is therefore a
  *local-only* artifact and does **not** affect production. Do not chase it in prod.

Conversely, because production is UTF-8, any character a user submits that latin1 cannot hold —
emoji, CJK, and the usernames in defect 1 — silently vanishes through the same swallowed write.
That is live on production today, and only the migration closes it.

---

## Not fixed — known remaining defects

Ranked by how likely they are to bite.

1. **Non-cp1252 usernames break editing permanently — 12 live accounts.**
   The same textarea interpolates `<ww:property value="pageUser/username"/>` unsanitised.
   `player.name` is `varchar(100) CHARACTER SET utf8mb4`, and registration validates with
   `Character.isLetterOrDigit`, which is Unicode-aware — 49,199 BMP characters pass it but are
   not cp1252-encodable. A DB query found **12 existing players** with such names (CJK, Korean,
   and `Viсtoria` with a Cyrillic `с`). Under a UTF-8 config those users can never edit a post
   with the box checked. The `\p{Zs}` fold does not help here.

2. **`saveToDb` swallows every `java.lang.Exception`** and `messageCache.put` is
   unconditional. Any future write failure — charset, length, deadlock — will silently report
   success and poison the cache for up to 6 hours. Jive ships as bytecode, so this cannot be
   fixed in place.

3. **Request/response charset mismatch — LOCAL STACK ONLY, not production** (see above; prod
   serves UTF-8, so this class does not arise there). Pages are
   served ISO-8859-1 but `org.pente.filter.SetCharacterEncodingFilter` (first filter, `/*`)
   forces the request to UTF-8, and `LoginFilter:93` calls `getParameter`, freezing it.
   Consequences measured on this stack: a freshly typed accent or smart quote makes Tomcat
   reject the whole POST with **HTTP 400**; and a stored `0x92` comes back through JDBC as
   U+2019, which the ISO-8859-1 encoder flattens to `?` — so an edit silently overwrites the
   user's apostrophe. 265 of 19,342 bodies and 57 subjects carry such bytes. Under a UTF-8
   config this whole class disappears.

4. **Dead / misleading configuration.** Jive's own
   `com.jivesoftware.util.SetCharacterEncodingFilter` is mapped to `/gameServer/forums*`,
   which is not a legal servlet prefix pattern (those must end `/*`), so it never fires. And
   every forum page emits `<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">`
   contradicting its own ISO-8859-1 header. The header wins in browsers, so it is not the
   trigger — but it is a trap for anyone changing the encoding.

### Recommended follow-up

Migrate the Jive text tables to utf8mb4 — `ALTER TABLE jiveMessage CONVERT TO CHARACTER SET
utf8mb4`, plus `jiveThread`, `jiveForum` and the `*Prop` tables. This closes items 1 and 4 and
removes the whole failure class instead of one character, which is what items 1–3 argue for.

Checked in advance: a utf8mb4 `TEXT` column accepts U+202F under the same `STRICT_TRANS_TABLES`
session, and `MAX(OCTET_LENGTH(CONVERT(body USING utf8mb4))) = 33,752` against `TEXT`'s 65,535,
so no overflow. `CONVERT TO` reinterprets rather than re-encodes, and because MariaDB's latin1
*is* cp1252 the existing `0x92`/`0x95`/`0x85` bytes map to the right characters automatically.

Caveats: 24 latin1 `jive*` tables, MyISAM, needs a backup and a maintenance window; indexed
`varchar` columns can exceed MyISAM's 1000-byte key limit once each character costs 4 bytes, so
check each table before the `ALTER`. **Do not** move `<characterEncoding>` to UTF-8 without
this migration — that combination is exactly the production failure reproduced above.
