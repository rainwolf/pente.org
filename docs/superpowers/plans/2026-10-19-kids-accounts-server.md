# Kids Accounts (server + website) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent manages kid accounts (create, convert by email confirmation, password, whitelist, copy toggles, release), and the server lets a kid exchange text only with approved players on every chat surface, with optional email copies to the parent.

**Architecture:** `dsg_player.parent_pid` marks a kid. Three new parent-owned tables (whitelist, settings, link requests) sit behind a Redis-cached `CacheKidStorer` that notifies listeners on every write. One pure `KidChatRules.allowed(a, b)` decides every surface. Persisted surfaces (private messages, broadcasts, TB text) ask a storer-backed `KidChatPolicy`. The live server keeps a per-`Server` `KidLiveSessions` registry, loaded once per session in `ServerPlayer` and refreshed by the listener, and filters the per-recipient chat loops. Parent copies go through `BackgroundMailer`. One JSON servlet `/gameServer/kids` serves the apps and the website "My kids" page.

**Tech Stack:** Java 21 Tomcat webapp, Ant (`build.xml`), JUnit 3.7 `TestCase` run by `junit.textui.TestRunner`, Gson, Jedis (`UnifiedJedis`), log4j 1.2 `Category`, MariaDB (MyISAM), JSP. E2E: Python 3 `urllib` and Node 26 `node:tls` against the local docker stack.

**Spec:** `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docs/superpowers/specs/2026-10-19-kids-accounts-design.md` (approved). Executors read the spec and this plan together. This plan covers only `pente.org` (server and website). The React, Android and iOS plans are written from the "Server API contract" section below.

**Base:** branch `kids-accounts` at commit `2f086ee` (already checked out). Every `file:line` refers to that commit. Earlier tasks shift lines in the same file, so always match on the quoted code, not the number. Paths below are relative to `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/`. `J/` means `dsg_src/java/org/pente/`.

## Server API contract (consumed by client plans)

Everything a client needs. Client plans must not assume anything beyond this section.

### Login-only kid field: `parentPid`

The field name is `parentPid`, a JSON number (the parent's pid). It is **present only when the logged-in player is a kid** and absent otherwise (Gson omits nulls). "Kid" means `parentPid` is present. A server without the feature never sends it, so clients treat a missing field as "not a kid".

It appears in exactly these places, and only for the logged-in player's own data:

| Where | Path to the field | Used by |
|---|---|---|
| Live room login echo (websocket and TCP/TLS port 15999 alike) | `{"dsgLoginEvent": {"me": {"parentPid": 23000000012345, ...}}}` | React, Android native live room, iOS native live room |
| Mobile dashboard JSON, `GET`/`POST` `/gameServer/mobile/json/index.jsp?name=&password=` (both apps use this JSP) | top-level `"player": {"name": ..., "parentPid": 23000000012345, ...}` | Android (`JsonModels.IndexResponse.PlayerInfo`), iOS (`DashboardEndpoint`) |
| `/gameServer/kids` GET | `"isKid": true` plus `"parentName"` | all apps, website |

It is **never** in `dsgJoinMainRoomEvent.dsgPlayerData` or `dsgUpdatePlayerDataEvent.data`, including the copies sent to the kid's own client. Clients read kid status from the login echo only.

### Live room (websocket and TCP): no new events

- No new event types and no new error codes. Clients need no decoder changes.
- Lobby text (`dsgTextMainRoomEvent`) and table text (`dsgTextTableEvent`, players and spectators) are filtered **silently** per recipient: a line is delivered only when the kid rule holds both ways. The sender still gets its own echo. No `dsgTextTableErrorEvent` or other refusal is sent for kid filtering.
- `dsgInviteTableEvent.inviteText` and `dsgInviteResponseTableEvent.responseText` arrive as `""` when the kid rule does not hold. The invite and the response themselves are still delivered (games stay open). Clients must render an empty text without a quote bubble.
- Admin chat, `"game server"` system lines (`dsgSystemMessageTableEvent`) and computer-player lines always reach kids.

### `/gameServer/kids` (JSON, apps and website)

Auth is `LoginFilter`: `name2` + `password2` request params (apps), or the website session/cookie. Responses are `application/json; charset=UTF-8`. No credentials at all gives 401 JSON `Login required`. **Wrong** `name2`/`password2` comes back as the HTML login page with status 200 (`LoginFilter` handles a failed login attempt before the servlet runs). Any non-JSON body or `"success": false` is a failure.

Website (cookie/session) POSTs must come from the pente.org page itself: a POST without `name2`/`password2` whose `Origin` (or, when absent, `Referer`) host is not the request's host, or that carries neither header, gets 403 `Request origin not allowed` and changes nothing. App POSTs that send `name2`/`password2` are not checked.

**GET `/gameServer/kids`** returns the caller's view.

Parent (or any non-kid account; `kids` is `[]` when they have none):
```json
{
  "success": true,
  "isKid": false,
  "emailVerified": true,
  "kids": [
    {
      "kidPid": 23000000012345,
      "name": "kid_one",
      "copyMessages": false,
      "copyLiveChat": false,
      "whitelist": [ { "pid": 23000000000777, "name": "graviton", "nameColor": 0, "tourneyWinner": 0 } ]
    }
  ]
}
```
Kid:
```json
{
  "success": true,
  "isKid": true,
  "emailVerified": true,
  "parentName": "iostest",
  "whitelist": [ { "pid": 23000000000777, "name": "graviton", "nameColor": 0, "tourneyWinner": 0 } ],
  "kids": []
}
```
- `whitelist` is sorted by `name`. `nameColor` and `tourneyWinner` follow `/gameServer/ignored` (`MobileJsonHelper.playerColorNonZero(d)`, `DSGPlayerData.getTourneyWinner()`). The kid's own parent is always allowed and is not listed.
- A kid's new-message composer limits only its **suggestions/autocomplete** to `parentName` plus the `whitelist` names. It never blocks a send on the client: whatever the kid types is sent, and the server's refusal string (below) is shown as-is.

**POST `/gameServer/kids`**, form-encoded, `action=` one of the rows below. Every success returns the same body as GET for the caller (after the change), plus `"message"` where noted. Flags accept `Y` or `true` (anything else is false).

| `action` | Params | Notes |
|---|---|---|
| `create` | `kidName`, `kidPassword` | Same name and password rules as `/join`. Kid email = parent's email, stored as **not verified** (so the server's normal PM notification emails never go to the parent; only the opt-in copies do). |
| `convert` | `kidName` | Emails a confirmation link to that account's registered email. Success adds `"message": "Confirmation email sent to <name>"`. The account becomes a kid only when the link is confirmed. When the target is already a kid, has kids, or is an admin, the reply is the **same** success body and nothing is stored or sent (kid status stays private). |
| `password` | `kidPid`, `kidPassword` | Sets the kid's password. |
| `whitelistAdd` | `kidPid`, `playerName` | Idempotent. Name match is case-insensitive. |
| `whitelistRemove` | `kidPid`, `playerPid` | |
| `settings` | `kidPid`, optional `copyMessages`, optional `copyLiveChat` | A missing flag keeps its current value. |
| `release` | `kidPid` | Kid becomes a normal account; its whitelist and settings are deleted. |

Errors are `{"success": false, "error": "<string>"}` with the status below. Clients match these exact strings:

| Status | `error` | When |
|---|---|---|
| 401 | `Login required` | No logged-in non-guest player |
| 400 | `Unknown action` | Bad `action` |
| 403 | `Request origin not allowed` | Cookie/session POST from another origin, or with neither `Origin` nor `Referer` |
| 403 | `Kid accounts can't manage kids` | Caller is a kid (any POST) |
| 403 | `Verify your email address before adding kids` | `create`/`convert` and caller's `email_valid` is not `Y` |
| 400 | `Invalid registration data, please review instructions and try again.` | `create` name/password fail `/join` rules |
| 409 | `Requested name <name> is already taken, please choose another.` | `create` name taken or reserved (same text as `/join`) |
| 404 | `Player not found` | `convert`/`whitelistAdd` target missing, inactive or guest |
| 400 | `You can't add yourself as a kid` | `convert` of self |
| 400 | `That account can't become a kid account` | `convert` target is a computer account (admin, kid and has-kids targets get the neutral success instead) |
| 404 | `Kid not found` | `kidPid` missing, unparseable, or not the caller's kid (never reveals other parents' kids) |
| 400 | `Passwords must be 5-16 letters, numbers or _` | `password` invalid |
| 400 | `A kid can't approve themself` | `whitelistAdd` of the kid itself |
| 404 | `Player is not approved` | `whitelistRemove` pid not on the list |
| 500 | `Database error` | Storage failure |

### Other HTTP refusals clients match

| Endpoint | Condition | What the client gets |
|---|---|---|
| `/gameServer/mymessages` `command=create` (web and `mobile=` apps) | Sender is a kid and recipient not allowed | Error page (request attr `error`) with text `You can only send messages to players your parent approved.` |
| same | Recipient is a kid and sender not allowed | `Player is ignoring your messages.` (existing string, so kid status stays private) |
| `/gameServer/broadcast` | Caller is a kid | `Kid accounts can't broadcast.` (existing `handleError` path) |
| `/gameServer/ageVerification` | Caller is a kid | HTTP 403, body `{"result":failed,"error":"kid_account"}` (same unquoted style as the servlet's other errors); `mobile_adult` stays `N` |
| `/gameServer/myprofile/myInfo` (website only) | Kid changes password or email | `Your password and email are managed by your parent.` An empty or missing `changeEmail` counts as unchanged. A kid's `changeEmailVisible` is ignored and saved as `N`. |
| TB move, resign, invitation, accept, cancel request | Text not allowed | No error. The action goes through and the text is dropped. |
| `/gameServer/tb/replyInvitation` (`command=load`, web and `mobile=`), `cancelInvitation.jsp`, TB game pages (`tb/game.jsp`, `mobile/json/game.jsp`) | Viewer is a kid and the inviter of an open invitation is not allowed | The inviter's invitation text (move 0, first message) is omitted; the invitation itself is shown and can be accepted. |
| `/gameServer/forgotpassword` | Name is a kid | Same pages as today; the email goes to the parent. |

### Links for clients

- Website management page: `https://www.pente.org/gameServer/myprofile/kids` (React links here; apps may link here too).
- Conversion confirmation landing (sent by email, not called by clients): `https://www.pente.org/confirmKidLink?code=<64 hex>`.

## Global Constraints

- Repo `pente.org` only, branch `kids-accounts`. Always `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org`. Commit with `-S`. No co-author or generated-by lines.
- Toolchain: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/`, `ant` at `/opt/homebrew/bin/ant`. JUnit is `dsg_src/lib/junit-3.7.jar`: **no `assertFalse`** (use `assertTrue(!x)`), tests extend `junit.framework.TestCase` with a `(String name)` constructor.
- `TEST_ONE <FQCN>` in this plan means exactly:
  `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=<FQCN>`
  Pass prints `OK (N tests)` then `BUILD SUCCESSFUL`; failure prints `FAILURES!!!` or a `javac` error then `BUILD FAILED`. Route output through `ctx_execute` and grep `OK (|FAILURES|error:|BUILD`.
- `TEST_ALL` means the same rsync, then `ant -f .../build.xml test`. Every new test class is added to the `test` target's fixed list in `build.xml` (next to `org.pente.gameServer.core.test.UsernameFilterTest`), one `<java classname="junit.textui.TestRunner" ...>` block each.
- Tests never live under a `client/` path: `compile-tests` excludes `**/client/**` and rsync excludes `test`. New tests go in `J/gameServer/kids/test/` (package `org.pente.gameServer.kids.test`) unless a task says otherwise.
- New production code lives in package `org.pente.gameServer.kids` (`J/gameServer/kids/`), so its log4j output lands in `gameServer.log`.
- Local stack is a dev backend. Deploy Java with `./justCompile` run from the repo root (compiles, then restarts the `pente.org` container; live clients drop). JSPs under `dsg_src/httpdocs/gameServer/` are mounted live. `web.xml` is **not** mounted: after editing it, run `docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml cp /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/httpdocs/WEB-INF/web.xml pente.org:/usr/local/tomcat/webapps/ROOT/WEB-INF/web.xml` before `./justCompile`.
- Local SQL: `docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml exec -T main_db sh -c 'mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" dsg'` with SQL on stdin. Never print `.env` values.
- Local Redis: `docker compose -f .../docker-compose.yml exec -T pente_cache redis-cli <cmd>`.
- Local mail is real SMTP. E2E must only ever make the server email addresses ending `@example.invalid`.
- E2E credentials for local accounts `iostest` and `graviton`: read them from the memory note `/Users/waliedothman/.claudeboat/projects/-Users-waliedothman-mariposa-coding-pente-org-project/memory/local-backend-test-accounts.md` and export as `KIDS_E2E_A_PASS` / `KIDS_E2E_B_PASS`. Never write them into a repo or a committed file.
- Scratch and E2E files only under `/private/tmp/claude-501/kids-plans/` (create it). Never in a repo. `curl`/`wget` are hook-blocked; use Python `urllib` with an unverified SSL context, or Node.
- Hard-coded pids: `SystemPids.SYSTEM_PID = 23000000016237L` (system/admin account) and `SystemPids.COMPUTER_PID = 23000000020606L` (computer player). New code uses these constants. Existing literals elsewhere are left alone (scope).
- Kid rule (spec, verbatim intent): two players may exchange text if, for each side that is a kid, the other side is on that kid's whitelist, is the kid's own parent, is an admin (`dsg_player.admin = 'Y'`), is `SYSTEM_PID` or `COMPUTER_PID`, or is a computer player (live AI). Same pid is always allowed (own echo). Guests (pid `5000 + n`) are never on a whitelist.
- Enforcement happens only when text is written. Existing messages are never filtered or deleted (spec non-goal).
- Copies: both toggles default off; nothing is emailed or buffered while a toggle is off; global `emailEnabled` applies; the parent's `email_valid` must be `Y`; live-chat buffer TTL is `86400` seconds; an empty session sends no email; Redis down means live-chat copies are skipped.
- Website copy (exact): read-only fields note `Managed by your parent`; live-chat note `Live chat copies are best effort: if the server's chat buffer is unavailable, that session isn't copied.`; unverified parent note `Verify your email address on My Info before adding kids.`
- Error strings are the ones in the API contract tables, verbatim. They live as constants in `KidAccountRules` (Task 10) and are referenced, never retyped, by other tasks.

## Review Focus

1. **Kid reconnects quickly, or is booted, while a stale exit is still queued.** The new session must still be treated as a kid. Pinned: kid state rides on the `DSGJoinMainRoomEvent` and is registered on the main-room pump in `handleJoin`, so join/exit stay ordered (Task 9); `testRejoinAfterLeaveIsKidAgain` (Task 8); reconnect scenario in Task 13.
2. **Parent edits the whitelist, a toggle, converts or releases while the kid is connected.** The change must apply without the kid logging out. Pinned: `testKidDataChangedRefreshesJoinedState` and `testConvertWhileConnectedBecomesKid` (Task 8); listener tests (Task 3); live whitelist-add scenario (Task 13).
3. **A kid's open TB invitation (no invitee, opponent pid 0) carries text.** The text must not be stored. Pinned: `testUnknownOpponentRefusedForKidAuthor` (Task 4) and `testStoreNewMessagePassesOpponentPid` (Task 7).
4. **Redis unavailable during a kid session.** Chat must keep flowing; only copies are skipped; disconnect must not throw. Pinned: `testCopyStoreFailureDoesNotBreakDelivery` (Task 8) and `testRedisDownFallsThroughToBase` (Task 3).
5. **Another player forges a `kidPid` they don't own, or a kid forges parent actions.** Must get `Kid not found` / `Kid accounts can't manage kids` and change nothing. Pinned: `testOwnsKid*` (Task 10) and the forgery scenarios in Task 13.
6. **A stale cached player is written back after a convert or release.** `parent_pid` must survive. Pinned: `parent_pid` absent from the generic UPDATE (Task 1) and the stale-object smoke (Task 1 Step 6).
7. **A parent edit lands between the kid's `ServerPlayer` load and its main-room join.** Pinned: `testChangeBetweenLoadAndJoinIsApplied` and `testConvertBetweenLoadAndJoinBecomesKid` (Task 8).
8. **A third-party page POSTs to `/gameServer/kids` with the parent's cookie.** Pinned: `KidsServletOriginTest` (Task 11) and HTTP scenario 12 (Task 13).
9. **Server shuts down with kid live-chat buffered.** Each buffer must be mailed synchronously before Redis and the storers go away, within about 5 s in total; a buffer that is not mailed (budget spent, send failed) must stay in Redis for the next-leave path, never be lost or sent twice. Pinned: flush placed first in `contextDestroyed` (Task 9); `testShutdownFlushMailsSynchronouslyAndDeletesKeys`, `testShutdownFlushStopsAtBudgetAndKeepsRemainingKeys`, `testShutdownFlushSendFailureKeepsKey`, `testLeaveAfterShutdownFlushKeepsBuffer` (Task 8); `testLiveChatNowSendFailureReturnsFalse` (Task 5); live scenario 11 (Task 13).

---

## File Structure

New, in `J/gameServer/kids/`:
- `KidChatRules.java` — pure allowed rule and `Party` value.
- `KidSettings.java`, `KidLinkRequest.java` — value objects (Serializable).
- `KidStorer.java`, `MySQLKidStorer.java`, `CacheKidStorer.java`, `KidDataChangeListener.java` — parent-owned data and cache invalidation.
- `KidChatPolicy.java`, `StorerKidChatPolicy.java` — the rule for persisted surfaces (pid in, boolean out).
- `KidCopyEmails.java` (pure text), `KidCopyMailer.java` (who gets copies, queueing).
- `LiveKidState.java`, `ChatCopyStore.java`, `RedisChatCopyStore.java`, `KidLiveSessions.java` — live-room state, buffering, flush.
- `KidAccountRules.java` (pure validation and error constants), `KidAccountEmails.java` (pure text).
- `KidsServlet.java` (`/gameServer/kids`), `KidLinkConfirmServlet.java` (`/confirmKidLink`).

New elsewhere: `J/gameServer/core/SystemPids.java`, `J/gameServer/core/ReservedNames.java`, `J/message/TextMailer.java`, `J/message/KidMessageRefusedException.java`, `J/gameServer/client/web/ForumUserSetup.java`, `dsg_src/sql/2026-10-19-kid-accounts.sql`, `dsg_src/httpdocs/gameServer/myprofileKids.jsp`.

Modified choke points are listed per task.

---

### Task 1: Schema, `parentPid` plumbing, and keeping it private on the wire

**Files:**
- Create: `dsg_src/sql/2026-10-19-kid-accounts.sql`
- Modify: `dsg_src/sql/schema.sql` (`dsg_player` block `:111-137`; add the three new tables after `dsg_player_prefs`)
- Modify: `J/gameServer/core/DSGPlayerData.java` (after `setMobileAdult`, `:214-216`)
- Modify: `J/gameServer/core/SimpleDSGPlayerData.java` (field next to `mobileAdult`, `:68-76`)
- Modify: `J/gameServer/core/MySQLDSGPlayerStorer.java`: INSERT `:103-121`, UPDATE `:148-191`, SELECT by pid `:228-237`, SELECT by name `:286-295`, `fillDSGPlayerData` `:557-592`
- Modify: `J/gameServer/event/DSGJoinMainRoomEvent.java` (constructor `:13-19`), `J/gameServer/event/DSGUpdatePlayerDataEvent.java` (constructor `:28-33`)
- Modify: `J/gameServer/mobile/IndexResponse.java` (`PlayerInfo` `:63-86`, builder `new PlayerInfo(` `:386`)
- Test: `J/gameServer/kids/test/KidWirePrivacyTest.java`; add to `build.xml` `test` target

**Interfaces:**
- Produces:
  - `DSGPlayerData`: `Long getParentPid();` and `void setParentPid(Long parentPid);` (null = not a kid).
  - `SimpleDSGPlayerData`: `private Long parentPid;` — **not** `transient` (Redis caches players with Java serialization; `serialVersionUID` is fixed so old blobs read back as null).
  - DB: `dsg_player.parent_pid` plus tables `dsg_kid_whitelist`, `dsg_kid_settings`, `dsg_kid_link_request` exactly as in Step 1.
  - Wire: `DSGJoinMainRoomEvent` and `DSGUpdatePlayerDataEvent` clones carry `parentPid == null`. `DSGLoginEvent.me` keeps it. `IndexResponse.PlayerInfo` gains `public final Long parentPid` (last field and last constructor parameter).

- [ ] **Step 1: Write the migration** `dsg_src/sql/2026-10-19-kid-accounts.sql`, header comment in the style of `2026-10-15-message-reply-to.sql` ("Apply to live + replica DBs before deploying the classes. Idempotent."):

```sql
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
```
Mirror all four in `schema.sql` (the column as the last `dsg_player` column plus `KEY parent_pid_key (parent_pid)`; the three `CREATE TABLE` blocks in the file's `DROP TABLE IF EXISTS` style).

- [ ] **Step 2: Write the failing test** `KidWirePrivacyTest` (helpers copied from `J/gameServer/server/test/ArenaJoinRequestWireTest.java:173-191`: encode with `new DSGEventWrapper(event).getJSON()`, parse with `new Gson().fromJson(json, JsonObject.class)`). Player fixture: `SimpleDSGPlayerData` with pid 42, name `kid`, `setParentPid(7L)`.
  - `testJoinMainRoomEventHasNoParentPid`: body `dsgJoinMainRoomEvent.dsgPlayerData` has no key `parentPid`; and the source object still has `getParentPid() == 7L` (the clone was stripped, not the original).
  - `testUpdatePlayerDataEventHasNoParentPid`: `dsgUpdatePlayerDataEvent.data` has no `parentPid`.
  - `testLoginEchoKeepsParentPid`: a `DSGLoginEvent` with `setMe(player)` encodes `dsgLoginEvent.me.parentPid` as `7`.
  - `testNonKidLoginHasNoParentPidKey`: `me` with `parentPid` null has no `parentPid` key.
  - `testRedisRoundTripKeepsParentPid`: `RedisConnectionManager.deserialize(RedisConnectionManager.serialize(player))` has `getParentPid() == 7L`.

- [ ] **Step 3: Run it.** `TEST_ONE org.pente.gameServer.kids.test.KidWirePrivacyTest`. Expected: `javac` error, `getParentPid` not found.

- [ ] **Step 4: Implement.**
  - Getter/setter pair on the interface and `SimpleDSGPlayerData`.
  - `MySQLDSGPlayerStorer`: add `parent_pid` as the **last** column of the INSERT (bind `setNull(Types.BIGINT)` when null) and of both SELECTs (`dsg_player.mobile_adult, dsg_player.parent_pid`). **Not** in the generic UPDATE: many callers do load (a possibly stale Redis copy) → mutate → `updatePlayer` (`LoginFilter.java:214-216` on every login, `DSGPlayerStorerLoginHandler`, `ChangeProfileServlet`, `AgeVerificationServlet`, …), and writing `parent_pid` there would let a stale copy undo a convert or release. `parent_pid` is changed only by `KidStorer.setParentPid` (Task 3). In `fillDSGPlayerData`, after `setMobileAdult`: read `getLong`, then `setParentPid(result.wasNull() ? null : value)`.
  - Event constructors: after `dataCopy.setEmail("")`, add `dataCopy.setParentPid(null)`.
  - `IndexResponse`: `PlayerInfo.parentPid`, filled from `playerData.getParentPid()`.

- [ ] **Step 5: Run the test.** Same command. Expected: `OK (5 tests)`. Then `TEST_ALL`: every runner `OK`, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Apply locally and smoke-test.**
  - Pipe the migration into the local SQL command (Global Constraints) **twice**; both runs exit 0 (idempotent). Then `DESCRIBE dsg_player;` shows `parent_pid`, and `SHOW TABLES LIKE 'dsg_kid%';` lists three tables.
  - `./justCompile` from the repo root. Expected `BUILD SUCCESSFUL` and the container restart line.
  - Write `/private/tmp/claude-501/kids-plans/e2e/smoke_index.py`: POST `name=iostest&password=$KIDS_E2E_A_PASS` to `https://localhost/gameServer/mobile/json/index.jsp`. Expected: JSON with key `player`, and no `parentPid` inside it.
  - Stale-object check (`smoke_stale.py`): load graviton once through the site (POST `name2`/`password2` to `/gameServer/index.jsp`, which caches him with `parent_pid` NULL), then SQL `update dsg_player set parent_pid = <iostest pid> where name = 'graviton'`, then log in as graviton again (the login path runs `updatePlayer` on the cached copy). Expected: SQL still shows graviton's `parent_pid` = iostest's pid. Finally set it back to NULL via SQL and log in as graviton once more (`CacheDSGPlayerStorer.updatePlayer` re-reads the row after writing, so the cache drops the test value); SQL and the mobile index JSON both show no parent.

- [ ] **Step 7: Commit.** `git -C <repo> add dsg_src/sql/2026-10-19-kid-accounts.sql dsg_src/sql/schema.sql <java files> build.xml && git -C <repo> commit -S -m "Kids: parent_pid column, kid tables, login-only parentPid on the wire"`

---

### Task 2: Pure kid chat rule

**Files:**
- Create: `J/gameServer/core/SystemPids.java`
- Create: `J/gameServer/kids/KidChatRules.java`
- Test: `J/gameServer/kids/test/KidChatRulesTest.java`; add to `build.xml`

**Interfaces:**
- Produces:
  - `public final class SystemPids { public static final long SYSTEM_PID = 23000000016237L; public static final long COMPUTER_PID = 23000000020606L; }`
  - `public final class KidChatRules`:
    - `public static final class Party { public final long pid; public final boolean admin; public final boolean computer; public final Long parentPid; public final Set<Long> whitelist; public Party(long pid, boolean admin, boolean computer, Long parentPid, Set<Long> whitelist); public boolean isKid(); }` — constructor stores an unmodifiable copy; null whitelist becomes empty.
    - `public static Party party(DSGPlayerData d, Set<Long> whitelist)` — pid, `isAdmin()`, `isComputer()`, `getParentPid()` from `d`.
    - `public static boolean allowed(Party a, Party b)` — `a.pid == b.pid`, else `acceptable(a, b) && acceptable(b, a)`.
    - `public static boolean acceptable(Party side, Party other)` — true when `side` is not a kid; else true when `other.pid` is `side.parentPid`, in `side.whitelist`, `SYSTEM_PID` or `COMPUTER_PID`, or `other.admin` or `other.computer`.

- [ ] **Step 1: Write failing tests** (kid K pid 100 parent 1, stranger S pid 200, whitelisted W pid 300, admin A pid 400 `admin=true`, second kid K2 pid 500 parent 2):
  - `testTwoNonKidsAllowed`: `allowed(S, W)`.
  - `testKidAndStrangerRefusedBothDirections`: `!allowed(K, S)` and `!allowed(S, K)`.
  - `testKidAndWhitelistedAllowed`: K with whitelist {300}: `allowed(K, W)` and `allowed(W, K)`.
  - `testKidAndOwnParentAllowed`: parent pid 1 non-kid: `allowed(K, P1)`.
  - `testKidAndOtherKidsParentRefused`: `!allowed(K, P2)` (pid 2).
  - `testKidAndAdminAllowed`, `testKidAndSystemPidAllowed` (pid `SYSTEM_PID`, not admin), `testKidAndComputerPidAllowed`, `testKidAndComputerFlagAllowed` (pid 600, `computer=true`).
  - `testKidToKidNeedsBothWhitelists`: K lists 500 only: refused; both list each other: allowed.
  - `testSamePidAllowed`: `allowed(K, K)`.
  - `testGuestRefused`: pid 5001 non-kid: `!allowed(K, guest)`.
  - `testNullWhitelistIsEmpty`: `new Party(100, false, false, 1L, null).whitelist.isEmpty()`.

- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.KidChatRulesTest`. Expected: `javac` error.
- [ ] **Step 3: Implement** the two classes per Interfaces.
- [ ] **Step 4: Run.** Expected `OK (13 tests)`; then `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `git -C <repo> commit -S -m "Kids: pure KidChatRules and SystemPids"` (after `add`).

---

### Task 3: Parent-owned data: storer, Redis cache, change listeners

**Files:**
- Create: `J/gameServer/kids/KidSettings.java`, `KidLinkRequest.java`, `KidStorer.java`, `MySQLKidStorer.java`, `CacheKidStorer.java`, `KidDataChangeListener.java`
- Modify: `J/gameServer/server/RedisConnectionManager.java` (constants near `PID_TO_IGNORES`, `:100`)
- Modify: `J/gameServer/server/Resources.java` (field + getter/setter)
- Modify: `J/gameServer/server/DSGContextListener.java` (after `resources.setEmailEnabled(...)`, `:169`)
- Test: `J/gameServer/kids/test/InMemoryKidStorer.java`, `J/gameServer/kids/test/CacheKidStorerRedisTest.java`; add the test to `build.xml`

**Interfaces:**
- Consumes: `RedisConnectionManager` `hput/hget/hremove`; test fake `org.pente.turnBased.test.SerializingRedisConnectionManager` (has `setAvailable(boolean)`).
- Produces:
  - `public class KidSettings implements Serializable` — `KidSettings(long kidPid, boolean copyMessages, boolean copyLiveChat)`, getters `getKidPid()`, `isCopyMessages()`, `isCopyLiveChat()`, `static KidSettings off(long kidPid)`.
  - `public class KidLinkRequest` — `KidLinkRequest(String code, long parentPid, long kidPid, Date creationDate)`, getters, `static String newCode()` (32 `SecureRandom` bytes as 64 lowercase hex chars).
  - `public interface KidStorer` (all throw `DSGPlayerStoreException`):
    - `Set<Long> getWhitelist(long kidPid)` — never null.
    - `void addToWhitelist(long kidPid, long allowedPid)` — idempotent (`INSERT IGNORE`, `last_update_date = sysdate()`).
    - `void removeFromWhitelist(long kidPid, long allowedPid)`
    - `KidSettings getSettings(long kidPid)` — `KidSettings.off` when no row.
    - `void storeSettings(KidSettings s)` — upsert (`ON DUPLICATE KEY UPDATE`).
    - `void deleteKidData(long kidPid)` — deletes whitelist and settings rows.
    - `void setParentPid(long kidPid, Long parentPid)` — the **only** writer of `dsg_player.parent_pid` after insert: single-column `update dsg_player set parent_pid = ? where pid = ?` (`setNull` for null). The caller then refreshes the player cache with `((CacheDSGPlayerStorer) players).refreshPlayer(kidName)` and only after that calls `deleteKidData` (whose notify makes live sessions reload the fresh player). Used by convert-confirm and release (create inserts the kid with `parentPid` already set, Task 11).
    - `List<Long> getKidPids(long parentPid)` — `select pid from dsg_player where parent_pid = ?`.
    - `void storeLinkRequest(KidLinkRequest r)` — first deletes rows for the same `kid_pid` and rows older than 7 days, then inserts.
    - `KidLinkRequest getLinkRequest(String code)` — null if absent.
    - `void deleteLinkRequest(String code)`
  - `public interface KidDataChangeListener { void kidDataChanged(long kidPid); }`
  - `public class CacheKidStorer implements KidStorer` — `CacheKidStorer(KidStorer base)`; Redis namespaces `RedisConnectionManager.PID_TO_KID_WHITELIST = "pid:kid_whitelist"` (value `HashSet<Long>`) and `PID_TO_KID_SETTINGS = "pid:kid_settings"`; whitelist/settings writes and `deleteKidData` write through, `hremove` the kid's entries, then call `notifyKidListeners(kidPid)`; `getKidPids`, `setParentPid` (no notify) and link-request methods pass through uncached. Plus `addKidDataChangeListener`, `removeKidDataChangeListener`, `public void notifyKidListeners(long kidPid)` (listeners in a `CopyOnWriteArrayList`; a throwing listener is logged and does not stop the others).
  - `MySQLKidStorer(DBHandler dbHandler)` — connection handling copied from `MySQLDSGPlayerStorer` (`getConnection` / `freeConnection` in `finally`).
  - `Resources.getKidStorer()` / `setKidStorer(CacheKidStorer)`; `DSGContextListener` creates `new CacheKidStorer(new MySQLKidStorer(dbHandler))`, sets it on `resources` and as ctx attribute `KidStorer.class.getName()`.
  - Test helper `InMemoryKidStorer implements KidStorer` — maps in memory (including `public final Map<Long, Long> parentPids` for `setParentPid`), public counters `whitelistReads`, `settingsReads`, and `public boolean failReads` (throws `DSGPlayerStoreException` when true).

- [ ] **Step 1: Write failing tests** `CacheKidStorerRedisTest` (`setUp`: `RedisConnectionManager.setInstance(new SerializingRedisConnectionManager())`; `tearDown`: `RedisConnectionManager.resetInstance()`):
  - `testWhitelistReadIsCached`: two `getWhitelist(10)` calls give `base.whitelistReads == 1`.
  - `testAddInvalidatesAndNotifies`: a recording listener; `add(10, 20)` then `getWhitelist(10)` contains 20 and the listener got `[10]`.
  - `testRemoveInvalidatesAndNotifies`: same for remove.
  - `testSettingsDefaultOff`: `getSettings(10)` has both flags false.
  - `testStoreSettingsInvalidatesAndNotifies`: store `(10, true, false)`, read back `true/false`, listener got 10.
  - `testDeleteKidDataClearsBoth`: after delete, whitelist empty and settings off, listener notified.
  - `testRedisDownFallsThroughToBase`: `setAvailable(false)`; add then read gives the new pid; two reads give `whitelistReads == 2`.
  - `testThrowingListenerDoesNotStopOthers`.
  - `testSetParentPidPassesThroughWithoutNotify`: `setParentPid(10, 1L)` gives `base.parentPids.get(10L) == 1L` and the listener got nothing.
- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.CacheKidStorerRedisTest`. Expected: `javac` error.
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run.** Expected `OK (9 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `git -C <repo> commit -S -m "Kids: KidStorer with Redis cache and change listeners"`.

---

### Task 4: Kid chat policy for persisted surfaces

**Files:**
- Create: `J/gameServer/kids/KidChatPolicy.java`, `J/gameServer/kids/StorerKidChatPolicy.java`
- Modify: `Resources.java` (getter/setter), `DSGContextListener.java` (create right after the kid storer)
- Test: `J/gameServer/kids/test/MapPlayerStorer.java`, `J/gameServer/kids/test/StorerKidChatPolicyTest.java`; add the test to `build.xml`

**Interfaces:**
- Consumes: `KidChatRules.party/allowed` (Task 2); `KidStorer.getWhitelist` (Task 3); `DSGPlayerStorer.loadPlayer(long)`.
- Produces:
  - `public interface KidChatPolicy { boolean allowed(long pidA, long pidB); }`
  - `public class StorerKidChatPolicy implements KidChatPolicy` — `StorerKidChatPolicy(DSGPlayerStorer players, KidStorer kids)`. Same pid, or either pid `SYSTEM_PID` / `COMPUTER_PID`, returns true **before any load** (so a storer hiccup never refuses tournament, vacation or payment notices). A pid with no player (including 0, an open TB invitation) becomes `new Party(pid, false, false, null, empty)`. The whitelist is loaded only for kids. Any exception is logged and returns **false** (fail closed).
  - `Resources.getKidChatPolicy()` / `setKidChatPolicy(KidChatPolicy)`.
  - Test helper `MapPlayerStorer extends org.pente.turnBased.test.InMemoryDSGPlayerStorer`: `put(DSGPlayerData)`; `loadPlayer(long)` / `loadPlayer(String)` (case-insensitive) return the stored object or null; `insertPlayer`/`updatePlayer` store; `public boolean failLoads`.

- [ ] **Step 1: Write failing tests** (kid 100 parent 1, stranger 200, whitelisted 300):
  - `testKidAndStrangerRefused`, `testKidAndWhitelistedAllowed`, `testNonKidsAllowed`.
  - `testUnknownOpponentRefusedForKidAuthor`: `!allowed(100, 0)`.
  - `testUnknownOpponentAllowedForNonKid`: `allowed(200, 0)`.
  - `testStorerErrorFailsClosed`: `failLoads = true` gives `!allowed(200, 300)`.
  - `testWhitelistNotLoadedForNonKids`: `allowed(200, 300)` leaves `whitelistReads == 0`.
  - `testSystemPidsAndSamePidAllowedWithoutLoading`: with `failLoads = true`, `allowed(100, SYSTEM_PID)`, `allowed(COMPUTER_PID, 100)` and `allowed(100, 100)` are all true.
- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.StorerKidChatPolicyTest`. Expected: `javac` error.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected `OK (8 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `-m "Kids: storer-backed KidChatPolicy"`.

---

### Task 5: Parent copy emails (text and routing)

**Files:**
- Create: `J/message/TextMailer.java`
- Modify: `J/message/BackgroundMailer.java` (implements `TextMailer`; new work type beside `Work`, `:31-41`; `internalDoWork` `:50` branches on the work type; new synchronous `mailTextNow` beside it)
- Create: `J/gameServer/kids/KidCopyEmails.java`, `J/gameServer/kids/KidCopyMailer.java`
- Modify: `Resources.java` (getter/setter), `DSGContextListener.java` (create after the policy)
- Test: `J/gameServer/kids/test/RecordingTextMailer.java`, `KidCopyEmailsTest.java`, `KidCopyMailerTest.java`; add both tests to `build.xml`

**Interfaces:**
- Consumes: `KidStorer.getSettings`, `DSGPlayerStorer.loadPlayer(long)`, `MapPlayerStorer`, `InMemoryKidStorer`.
- Produces:
  - `public interface TextMailer { void mailText(String toName, String toEmail, String subject, String body); void mailTextNow(String toName, String toEmail, String subject, String body, int timeoutMillis) throws Exception; }`. `BackgroundMailer.mailText` queues a plain-text email (From `smtpUser` as "Pente.org", Reply-To `noreply@pente.org`) on its existing worker thread.
  - `BackgroundMailer.mailTextNow` sends the same message **on the caller's thread** and throws on any failure. It builds `Properties` as `:61-65` do, plus `mail.smtp.connectiontimeout`, `mail.smtp.timeout` and `mail.smtp.writetimeout` set to `timeoutMillis`, and uses `Session.getInstance(props, null)`, **not** `Session.getDefaultInstance` (`:67`): the default instance caches the first `Properties` JVM-wide, so the timeouts would be ignored. The queued and synchronous paths share one private message builder. Not `SendMail2.sendMail`: it reads `System` `mail.smtp.*` properties, uses `getDefaultInstance` with no timeouts, and connects without the port (`SendMail2.java:79-84`, `:113`), so a hung SMTP connect could eat the whole Docker stop grace (Task 9 shutdown flush).
  - `KidCopyEmails` (pure; dates formatted `yyyy-MM-dd HH:mm` in UTC with suffix ` UTC`):
    - `static String pmSubject(String fromName, String toName, String subject)` → `"Pente.org message copy: " + fromName + " to " + toName + ": " + subject`
    - `static String pmBody(String kidName, String fromName, String toName, Date sent, String subject, String body)` → lines `From: `, `To: `, `Date: `, `Subject: `, a blank line, the body, a blank line, then `You get this copy because message copies are on for <kidName>. Change it at https://www.pente.org/gameServer/myprofile/kids`
    - `static String liveChatLine(Date when, String where, String author, String text)` → `"2026-10-19 14:03 UTC [table 3] graviton: hello"`
    - `static String liveChatSubject(String kidName)` → `"Pente.org live chat copy: " + kidName`
    - `static String liveChatBody(String kidName, List<String> lines)` → `Live game room chat that <kidName> sent or saw:`, a blank line, one line each, a blank line, `You get this copy because live chat copies are on for <kidName>. Change it at https://www.pente.org/gameServer/myprofile/kids`
  - `KidCopyMailer(boolean emailEnabled, TextMailer mailer, DSGPlayerStorer players, KidStorer kids)`:
    - `public void copyPrivateMessage(DSGMessage m)` — for each side that is a kid with `copyMessages`, add its parent pid to a `LinkedHashSet`; mail each parent once. Never throws (logs).
    - `public void copyLiveChat(long kidPid, long parentPid, List<String> lines)` — nothing for an empty list. Never throws.
    - Both skip when `!emailEnabled`, or the parent is missing, inactive, or `!getEmailValid()`. Each mail logs `log4j.info("Kid copy queued: kid=" + kidPid + " parent=" + parentPid + " kind=pm|live")` (E2E greps this).
    - `public boolean copyLiveChatNow(long kidPid, long parentPid, List<String> lines, int timeoutMillis)` — the shutdown path (Task 8 `flushForShutdown`). Same skip rules as `copyLiveChat`; same subject and body. Sends through `mailer.mailTextNow(..., timeoutMillis)` on the caller's thread, never the `BackgroundWorker` queue (its thread is interrupted at shutdown and drops queued work, `BackgroundWorker.java:40-46`). Returns `true` when the mail was sent, the list is empty, or a skip rule applied (the buffer may then be deleted, the same outcome as the `leave` path, which drains before `copyLiveChat` skips). Returns `false` when the send throws (logged); the caller keeps the buffer. Never throws. On success logs `log4j.info("Kid copy sent: kid=" + kidPid + " parent=" + parentPid + " kind=live-shutdown")`.
  - Test helper `RecordingTextMailer implements TextMailer` with `public final List<String[]> sent` (`{toName, toEmail, subject, body}`) for `mailText`, and for `mailTextNow`: `public final List<String[]> sentNow`, `public final List<Integer> nowTimeouts`, `public boolean failNow` (throws `new Exception("send failed")` before recording), `public Runnable onSendNow` (run first when non-null; Task 8 uses it to advance a fake clock).
  - `Resources.getKidCopyMailer()` / `setKidCopyMailer(KidCopyMailer)`. `DSGContextListener` builds `new KidCopyMailer(emailEnabled, new BackgroundMailer(<the same four mail.smtp.* init params CacheMessageStorer gets at :259-262>, dsgPlayerStorer), dsgPlayerStorer, kidStorer)`.
  - `public void destroy()` — destroys the mailer when it is a `BackgroundMailer` (its `BackgroundWorker` thread starts in the constructor, `BackgroundWorker.java:34-36`). `DSGContextListener.contextDestroyed` calls `resources.getKidCopyMailer().destroy()` next to `((CacheMessageStorer) ...).destroy()` (`:433`), so the second mailer thread does not leak.

- [ ] **Step 1: Write failing tests.**
  - `KidCopyEmailsTest`: `testPmSubject`, `testLiveChatLineFormat` (fixed `Date` 2026-10-19 14:03 UTC, expected string exactly as above), `testLiveChatBodyListsLinesInOrder`, `testPmBodyHasHeadersAndFooter`.
  - `KidCopyMailerTest` (kid 100 parent 1 `email_valid` true `parent@example.invalid`; stranger 200):
    - `testNoCopyWhenToggleOff`: `sent` empty.
    - `testCopyWhenKidSends` and `testCopyWhenKidReceives`: one mail to `parent@example.invalid`, subject from `pmSubject`.
    - `testSiblingsShareParentGetOneCopy`: kids 100 and 101 both parent 1, both toggles on, gives 1 mail.
    - `testNoCopyWhenParentEmailInvalid`, `testNoCopyWhenEmailDisabled`.
    - `testLiveChatCopySent` and `testEmptyLiveChatSendsNothing`.
    - `testStorerErrorDoesNotThrow`.
    - `testDestroyIgnoresNonBackgroundMailer`: `destroy()` with `RecordingTextMailer` does not throw.
    - `testLiveChatNowSendsOnCallerThread`: `copyLiveChatNow(100, 1, two lines, 1234)` returns `true`; `sentNow.size() == 1` to `parent@example.invalid` with subject `liveChatSubject`; `sent` is empty; `nowTimeouts.get(0) == 1234`.
    - `testLiveChatNowSendFailureReturnsFalse`: `failNow = true`; returns `false`, does not throw, `sentNow` empty.
    - `testLiveChatNowSkippedByRulesReturnsTrue`: parent `email_valid` false; returns `true`, nothing in `sent` or `sentNow`.
- [ ] **Step 2: Run** both with `TEST_ONE`. Expected: `javac` errors.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected `OK (4 tests)` and `OK (13 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `-m "Kids: parent copy emails through BackgroundMailer"`.

---

### Task 6: Private messages, broadcasts and TB notices

**Files:**
- Modify: `J/message/DSGMessage.java` (`boolean systemNotice` + `isSystemNotice()`/`setSystemNotice(boolean)`; not persisted)
- Create: `J/message/KidMessageRefusedException.java` (`extends DSGMessageStoreException`, `(String message)` constructor)
- Modify: `J/message/CacheMessageStorer.java` (`createMessage(DSGMessage, boolean)`, `:77-125`)
- Modify: `J/message/DSGMessageServlet.java` (`create`, after the ignore check `:298-306`; catch block `:345-352`)
- Modify: `J/gameServer/client/web/BroadcastServlet.java` (kid check right after the player is loaded, **before** `canBroadcast` `:85` and the donor check `:90-93`; recipient loop `:113-130`; `init` reads `Resources`)
- Modify: `J/turnBased/web/ReplyInvitationServlet.java` (decline PM `:340-354`)
- Modify: `J/turnBased/CacheTBStorer.java` (dead-stones `:316-324`; win/lose `:1253-1267` inside `EndGameRunnable.storeGameDSG`)
- Modify: `DSGContextListener.java` (`setKidSupport` after the `CacheMessageStorer` is built, `:256-265`)
- Test: `J/gameServer/kids/test/CacheMessageStorerKidTest.java`; add to `build.xml`

**Interfaces:**
- Consumes: `KidChatPolicy` (Task 4), `KidCopyMailer` (Task 5), `KidAccountRules.KID_PM_ERROR` and `KidAccountRules.KID_BROADCAST_ERROR` (Task 10). If this task runs before Task 10, add exactly those two constants to a new `KidAccountRules` class now, with the contract's text; Task 10 extends the class.
- Produces:
  - `CacheMessageStorer.setKidSupport(KidChatPolicy policy, KidCopyMailer copier)`. In `createMessage(m, cc)`: when `policy != null && !m.isSystemNotice() && !policy.allowed(from, to)`, throw `KidMessageRefusedException` **before** `baseStorer.createMessage`. After a successful store and the existing email block, call `copier.copyPrivateMessage(m)` when `copier != null`, whether or not `emailEnabled` (the copier checks it).
  - `DSGMessageServlet` create: when the policy refuses, `handleError(..., playerData.getParentPid() != null ? KID_PM_ERROR : "Player is ignoring your messages.", errorRedirectPage)` and return before `createMessage` and before the push notification. A `KidMessageRefusedException` thrown anyway (race) gets the same text.
  - `BroadcastServlet`: a kid caller gets `handleError(request, response, KID_BROADCAST_ERROR)`, checked before `canBroadcast` and the donor check (so a kid who never donated still sees the kid text). Recipients the policy refuses are skipped (no message, no `sendBroadcastNotification`). In the recipient loop, `catch (KidMessageRefusedException e)` (a policy race) logs and `continue`s **before** the existing `DSGMessageStoreException` catch, so one refused follower does not stop the rest.
  - `ReplyInvitationServlet` decline: when `!policy.allowed(inviteePid, inviterPid)`, leave out `inviteeMessage` and `setSystemNotice(true)`. Otherwise unchanged.
  - `CacheTBStorer`: `public static DSGMessage noticeMessage(long fromPid, long toPid, String subject, String body)` (sets `creationDate` and `setSystemNotice(true)`) builds the dead-stones, win and lose messages. The win and lose `createMessage` calls each get their own `try { … } catch (DSGMessageStoreException e) { log4j.error(…) }`, so a failed notice can never skip the rest of game completion (KotH `movePlayersUpDown` at `:1269+`).

- [ ] **Step 1: Write failing tests** `CacheMessageStorerKidTest`. Base: an in-test `DSGMessageStorer` that records `createMessage` calls (other methods throw `UnsupportedOperationException`). Storer: `new CacheMessageStorer(base, false, "localhost", 25, "u", "p", players)` with `MapPlayerStorer`. Policy: a lambda refusing the pair (100, 200). Copier: `KidCopyMailer(true, recording, players, kids)`; kid 100 has `copyMessages` on, parent 1 valid. Call `destroy` where the class has it, in `tearDown`.
  - `testRefusedMessageThrowsAndIsNotStored`: from 200 to 100 throws `KidMessageRefusedException`; `base` recorded 0; `recording.sent` empty.
  - `testSystemNoticeBypassesPolicy`: same pair with `setSystemNotice(true)` is stored.
  - `testAllowedKidMessageStoredAndCopied`: 100 to 300 is stored; `recording.sent.size() == 1`.
  - `testNoKidSupportIsUnchanged`: no `setKidSupport`; 200 to 100 is stored.
  - `testTbNoticeMessagesBypassPolicy`: `CacheTBStorer.noticeMessage(200, 100, "You lost", "x")` and `noticeMessage(100, 200, "Congratulations, you won!", "y")` are both `isSystemNotice()` and both stored through the refusing storer (a kid losing to a stranger still gets both PMs).
- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.CacheMessageStorerKidTest`. Expected: `javac` error.
- [ ] **Step 3: Implement** all modifications listed under Files.
- [ ] **Step 4: Run.** Expected `OK (5 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `-m "Kids: enforce kid rule on private messages and broadcasts, mark TB notices as system"`.

---

### Task 7: Turn-based message and cancel text

**Files:**
- Modify: `J/turnBased/CacheTBStorer.java` (`storeNewMessage` `:1812-1826`, `requestCancel` `:2001-2009`, new setter)
- Modify: `DSGContextListener.java` (`tbGameStorer.setKidChatPolicy(...)` after `:272-276`)
- Create: `J/gameServer/kids/KidInvitationText.java` (open-invitation display filter, decision D3)
- Modify: `dsg_src/httpdocs/gameServer/tb/replyInvitation.jsp` (`:226-229`; the apps load this page via `replyInvitation?command=load&mobile=`), `tb/cancelInvitation.jsp` (`:151-154`), `tb/game.jsp` (message loop `:50`), `mobile/json/game.jsp` (encoder `:43-47`)
- Test: `J/turnBased/test/CacheTBStorerKidTest.java` (package `org.pente.turnBased.test`, to reuse `InMemoryTBGameStorer`), `J/gameServer/kids/test/KidInvitationTextTest.java`; add both to `build.xml`

**Interfaces:**
- Consumes: `KidChatPolicy` (Task 4).
- Produces:
  - `CacheTBStorer.setKidChatPolicy(KidChatPolicy policy)` (null means no rule).
  - `storeNewMessage(gid, m)`: after loading the game, when `policy != null && !policy.allowed(m.getPid(), game.getOpponent(m.getPid()))`, log at debug and **return without storing** (no exception, cache and base untouched).
  - `requestCancel(set, requestorPid, message)`: opponent is `set.getPlayer2Pid()` when the requestor is `getPlayer1Pid()`, else `getPlayer1Pid()`. When refused, use `null` for the message in both the cache (`setCancelMsg`) and `baseStorer.requestCancel`. The cancel request itself still goes through.
  - Open invitations (D3): a non-kid's open-invitation text is stored (recipient unknown at write time), so it is filtered when **served** to a kid. `KidInvitationText.hidden(KidChatPolicy policy, DSGPlayerData viewer, TBMessage m)` — true only when `policy != null`, `viewer != null`, `viewer.getParentPid() != null`, `m.getMoveNum() == 0 && m.getSeqNbr() == 1` (the inviter message `NewGameServlet.java:396-405` writes), `m.getPid() != viewer.getPlayerID()`, and `!policy.allowed(viewer.getPlayerID(), m.getPid())`. Never throws (policy errors already fail closed → hidden).
  - `replyInvitation.jsp` and `cancelInvitation.jsp` render `""` for the inviter message when `hidden(...)` for the logged-in viewer. `tb/game.jsp` and `mobile/json/game.jsp` skip a message for which `hidden(...)` is true (this covers the same text after a kid accepts the open invitation). Policy from `Resources.getKidChatPolicy()`. No other message is filtered (existing-message non-goal).

- [ ] **Step 1: Write failing tests.**
  - `KidInvitationTextTest` (kid 100 parent 1, stranger 200, policy refusing (100, 200)): `testStrangerInviterTextHiddenFromKid`, `testAllowedInviterTextShown`, `testNonKidViewerSeesText`, `testOnlyMoveZeroFirstMessageHidden` (seq 2 or move 3 from 200 → not hidden), `testNullPolicyOrViewerShows`.
  - `CacheTBStorerKidTest`, with setup copied from `CacheTBStorerRedisTest.setUp/tearDown` (`:36-57`) and the game fixture from `testMessageSurvivesIntoCacheAfterStoreNewMessage` (`:90-121`, players 1001/1002):
    - `testRefusedMoveMessageNotStored`: policy refusing (1001, 1002): `loadGame(gid).getMessages().size() == 0`.
    - `testAllowedMoveMessageStored`: size 1.
    - `testStoreNewMessagePassesOpponentPid`: a recording policy sees `(1001, 1002)`. For a game with `player2Pid` 0 it sees `(1001, 0)`.
    - `testCancelMessageStrippedWhenRefused`: `loadSet(101).getCancelMsg() == null`, `getCancelPid() == 1001`.
    - `testCancelMessageKeptWhenAllowed`: message `"please"` kept.
- [ ] **Step 2: Run.** `TEST_ONE org.pente.turnBased.test.CacheTBStorerKidTest` and `TEST_ONE org.pente.gameServer.kids.test.KidInvitationTextTest`. Expected: `javac` errors.
- [ ] **Step 3: Implement** (storer, helper, the four JSPs).
- [ ] **Step 4: Run.** Expected `OK (5 tests)` and `OK (5 tests)`; `TEST_ALL` passes (including `CacheTBStorerRedisTest`). JSPs are verified live in Task 13.
- [ ] **Step 5: Commit.** `-m "Kids: drop TB move, invitation and cancel text a kid may not exchange"`.

---

### Task 8: Live-room kid sessions and the live-chat copy buffer

**Files:**
- Create: `J/gameServer/kids/LiveKidState.java`, `ChatCopyStore.java`, `RedisChatCopyStore.java`, `KidLiveSessions.java`
- Test: `J/gameServer/kids/test/InMemoryChatCopyStore.java`, `J/gameServer/kids/test/KidLiveSessionsTest.java`; add the test to `build.xml`

**Interfaces:**
- Consumes: `KidChatRules` (Task 2), `KidStorer`/`KidDataChangeListener` (Task 3), `KidCopyMailer.copyLiveChat`, `KidCopyMailer.copyLiveChatNow` and `KidCopyEmails.liveChatLine` (Task 5).
- Produces:
  - `public final class LiveKidState implements Serializable` — `LiveKidState(long pid, Long parentPid, Set<Long> whitelist, boolean copyLiveChat, long loadedSeq)`, getters, `isKid()`, `static LiveKidState notKid(long pid)` (`loadedSeq` 0). Immutable.
  - `public interface ChatCopyStore { void append(String key, String line, int ttlSeconds); List<String> drain(String key); void restore(String key, List<String> lines, int ttlSeconds); }`. `drain` returns all lines in order and deletes the key. `restore` puts drained lines back **ahead of** anything appended since, in their original order, and resets the TTL (used when a shutdown send fails).
  - `RedisChatCopyStore implements ChatCopyStore` — `RedisConnectionManager.getInstance().getJedis()`; `rpush` then `expire(key, ttl)`; `drain` is `lrange 0 -1` then `del`; `restore` is `lpush` of the lines in **reverse** order, then `expire(key, ttl)` (nothing for an empty list). When `getJedis()` is null or `!isAvailable()`, `append` and `restore` do nothing and `drain` returns an empty list. Exceptions are logged and swallowed.
  - `public class KidLiveSessions implements KidDataChangeListener`:
    - `public static final int BUFFER_TTL_SECONDS = 86400;`
    - `public static final long SHUTDOWN_BUDGET_MILLIS = 5000;` — total for all servers. Docker's default stop grace is 10 s (seconds) and `docker-compose.yml` sets no `stop_grace_period` for `pente.org`, so this leaves room for the rest of `contextDestroyed` and Tomcat.
    - `public static final int SHUTDOWN_SEND_TIMEOUT_MILLIS = 1000;` — cap on each SMTP connect/read/write during the shutdown flush.
    - `KidLiveSessions(long serverId, DSGPlayerStorer players, KidStorer kids, ChatCopyStore copies, KidCopyMailer mailer)` — delegates to the next one with `System::currentTimeMillis`.
    - `KidLiveSessions(long serverId, DSGPlayerStorer players, KidStorer kids, ChatCopyStore copies, KidCopyMailer mailer, LongSupplier clock)` — the clock is only read by `flushForShutdown` (tests pass a fake).
    - `public LiveKidState load(DSGPlayerData d)` — takes `seq.incrementAndGet()` (an `AtomicLong`) **before** reading anything and stores it as `loadedSeq` (non-kids too). `notKid`-shaped state when `getParentPid()` is null. Otherwise whitelist plus `getSettings().isCopyLiveChat()`. On storer error: kid with an empty whitelist and copy off (fail closed). Never throws.
    - `public void join(String name, LiveKidState state)` — null state is ignored. If `changedAt.getOrDefault(pid, 0L) > state.getLoadedSeq()` (a parent edit landed between the `ServerPlayer` load and this join on the main-room pump), reload with `load(players.loadPlayer(pid))` before storing.
    - `public void leave(String name)` — removes the entry. When it was a kid: `flush(state)` = `drain(bufferKey(pid))`, then `mailer.copyLiveChat(pid, parentPid, lines)` if non-empty. Idempotent.
    - `public LiveKidState get(String name)` — null when not joined.
    - `public boolean allowed(DSGPlayerData a, DSGPlayerData b)` — builds `KidChatRules.Party` from each player's joined state (no state = not a kid, empty whitelist; `isAdmin()`/`isComputer()` from the data) and calls `KidChatRules.allowed`.
    - `public void delivered(DSGPlayerData recipient, String where, String author, String text, Date when)` — when the recipient's state is a kid with `copyLiveChat`, `append(bufferKey(pid), KidCopyEmails.liveChatLine(when, where, author, text), BUFFER_TTL_SECONDS)`. Never throws.
    - `public void kidDataChanged(long kidPid)` — first `changedAt.put(kidPid, seq.incrementAndGet())` (a `ConcurrentHashMap<Long, Long>`), then for each joined entry with that pid, reload with `load(players.loadPlayer(kidPid))` and replace it (never adds a missing entry; `join` catches the not-yet-joined case). When the old state was a kid with `copyLiveChat` and the new one is not a kid or has copy off (release, toggle off), `flush(old)` so the buffer is mailed now instead of sitting until the TTL.
    - `String bufferKey(long pid)` → `"kids:livechat:" + serverId + ":" + pid`.
    - Map is a `ConcurrentHashMap` keyed by player name (rooms are name-keyed). `kidDataChanged` runs on servlet threads.
    - `public int flushForShutdown(long deadlineMillis)` — the shutdown drain, called by `DSGContextListener.contextDestroyed` (Task 9) **before** any storer, mailer or Redis destroy. First sets `volatile boolean shuttingDown = true`. Then, for each joined entry that is a kid with `copyLiveChat`: if `clock.getAsLong() >= deadlineMillis`, stop (that key and every later one stay in Redis untouched). Otherwise `drain(bufferKey(pid))`; empty means next entry. Else `mailer.copyLiveChatNow(pid, parentPid, lines, (int) Math.max(1, Math.min(deadlineMillis - now, SHUTDOWN_SEND_TIMEOUT_MILLIS)))` on the calling thread. `true` means the buffer is gone (already drained) and the count goes up; `false` means `restore(bufferKey(pid), lines, BUFFER_TTL_SECONDS)`. Returns the number of sessions mailed. Never throws (catches per entry, logs, restores when lines were drained). Logs one `log4j.info("Kid live-chat shutdown flush: server=" + serverId + " mailed=" + n + " left=" + m)`.
    - Once `shuttingDown` is set, `flush` (from `leave` and `kidDataChanged`) does nothing, so a `leave` during the rest of shutdown cannot drain a buffer into the `BackgroundWorker` queue that is about to be interrupted (`BackgroundWorker.java:40-46` drops queued work). `delivered` still appends.
    - A buffer not mailed at shutdown (budget spent, send failed, or a kid's buffer from an earlier session who is not joined now) stays in Redis under the same key (TTL 24 h) and is mailed with the kid's next `leave` on that server, as before. Best effort, as the spec's live-chat note says.
  - Test helper `InMemoryChatCopyStore` with `public boolean fail` (throws `RuntimeException` from all three methods when true) and `public List<String> lines(String key)` (a copy; empty when absent).

- [ ] **Step 1: Write failing tests** (`MapPlayerStorer`, `InMemoryKidStorer`, `InMemoryChatCopyStore`, `RecordingTextMailer`; kid `kid` pid 100 parent 1, stranger `bob` 200, `amy` 300 on the whitelist):
  - `testNonKidLoadsNotKid`.
  - `testKidLoadsWhitelistAndCopyFlag`.
  - `testLoadFailsClosedOnStorerError`: `failReads` gives a kid state with an empty whitelist.
  - `testAllowedUsesJoinedState`: after `join` of the kid, `!allowed(kid, bob)` and `allowed(kid, amy)`. A player never joined counts as not a kid: `allowed(bob, amy)`.
  - `testKidDataChangedRefreshesJoinedState`: add 200 in the kid storer, `kidDataChanged(100)`, then `allowed(kid, bob)`.
  - `testConvertWhileConnectedBecomesKid`: join `bob` as `notKid`, set his `parentPid` in `MapPlayerStorer`, `kidDataChanged(200)`, gives `get("bob").isKid()`.
  - `testKidDataChangedIgnoresPlayersNotJoined`: `get("kid") == null` afterwards.
  - `testDeliveredBuffersOnlyWhenCopyOn`: copy off gives an empty store; copy on gives one line in the format of `liveChatLine`.
  - `testLeaveMailsBufferOnceAndClears`: 2 lines, then `leave`, gives `recording.sent.size() == 1`; a second `leave` sends nothing; the store key is empty.
  - `testEmptySessionSendsNoEmail`.
  - `testCopyStoreFailureDoesNotBreakDelivery`: `fail = true`; `delivered` and `leave` don't throw; `allowed` still works.
  - `testRejoinAfterLeaveIsKidAgain`.
  - `testChangeBetweenLoadAndJoinIsApplied`: `s = load(kid)`; add 200 in the kid storer; `kidDataChanged(100)` (not joined yet); `join("kid", s)` gives `allowed(kid, bob)`.
  - `testConvertBetweenLoadAndJoinBecomesKid`: `s = load(bob)` (not a kid); set his `parentPid` in `MapPlayerStorer`; `kidDataChanged(200)`; `join("bob", s)` gives `get("bob").isKid()`.
  - `testReleaseWhileConnectedMailsBuffer`: kid with copy on, one `delivered` line, then clear `parentPid` in `MapPlayerStorer` and `kidDataChanged(100)`: `recording.sent.size() == 1` and the key is empty.
  - `testBufferFromEarlierSessionMailedAtNextLeave`: a line appended to `bufferKey(100)` before `join`, then `join` + `leave`, gives one mail containing it.
  - Shutdown tests use the six-argument constructor with a fake clock (`long[] now = {1000}`, `() -> now[0]`) and a second kid `kid2` pid 101, parent 1, copy on:
  - `testShutdownFlushMailsSynchronouslyAndDeletesKeys`: `kid` and `kid2` joined, one `delivered` line each; `flushForShutdown(now[0] + 5000)` returns 2; `recording.sentNow.size() == 2`, `recording.sent` is empty (nothing queued); `lines(bufferKey(100))` and `lines(bufferKey(101))` are empty; every `nowTimeouts` value is `<= SHUTDOWN_SEND_TIMEOUT_MILLIS`.
  - `testShutdownFlushStopsAtBudgetAndKeepsRemainingKeys`: same setup; `recording.onSendNow = () -> now[0] += 6000`; returns 1; `sentNow.size() == 1`; exactly one of the two keys is empty and the other still holds its one line (map order is not fixed, so assert one-and-one).
  - `testShutdownFlushSendFailureKeepsKey`: `kid` joined, two `delivered` lines; `recording.failNow = true`; returns 0; `lines(bufferKey(100))` equals the two lines in their original order.
  - `testLeaveAfterShutdownFlushKeepsBuffer`: after the failing flush above, `recording.failNow = false` and `leave("kid")`: `sent` and `sentNow` are empty and the key still holds both lines (falls back to the next-leave path after restart).
  - `testShutdownFlushSkipsKidsWithCopyOff`: `kid` joined with copy off, a line appended to `bufferKey(100)` directly; returns 0, nothing sent, the line is still there.
- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.KidLiveSessionsTest`. Expected: `javac` error.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected `OK (21 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `-m "Kids: live-room kid sessions with Redis live-chat copy buffer"`.

---

### Task 9: Wire kid sessions into the live server

**Files:**
- Modify: `J/gameServer/event/DSGJoinMainRoomEvent.java` (add `private transient LiveKidState kidState;` + getter/setter)
- Modify: `J/gameServer/server/Server.java` (field and getter; construct after `mainRoom` is built and listeners added `:135-138`; `destroy()` `:407-409` removes the kid listener)
- Modify: `J/gameServer/server/ServerPlayer.java` (login `:161-199`)
- Modify: `J/gameServer/server/ServerMainRoom.java` (`handleText` `:74-110`, `handleJoin` `:112-130`, `handleExit` `:160-170`, new helper)
- Modify: `J/gameServer/server/TournamentServerMainRoom.java` (`handleJoin` `:107-122`, `handleExit` `:129-137`)
- Modify: `J/gameServer/server/ServerTable.java` (`handleText` `:371-410`, `handleInvite` `:3172-3184`, `handleInviteResponse` `:3194-3215`, new helper)
- Modify: `J/gameServer/server/DSGContextListener.java` (`contextDestroyed` `:418`: shutdown flush ahead of the destroys at `:428-434`)
- Test: extend `J/gameServer/kids/test/KidWirePrivacyTest.java`; create `J/gameServer/server/test/ServerTableKidTest.java` and `J/gameServer/server/test/ServerMainRoomKidTest.java` (package `org.pente.gameServer.server.test`); add both to `build.xml` next to `ArenaServerTableJoinRequestTest`

**Interfaces:**
- Consumes: `KidLiveSessions` (Task 8), `RedisChatCopyStore`, `Resources.getKidStorer()/getKidCopyMailer()`.
- Produces:
  - `Server.getKidSessions()` returns this server's `KidLiveSessions`, built as `new KidLiveSessions(serverData.getServerId(), dsgPlayerStorer, resources.getKidStorer(), new RedisChatCopyStore(), resources.getKidCopyMailer())` and registered with `resources.getKidStorer().addKidDataChangeListener(...)`.
  - `ServerPlayer` login: guests get `LiveKidState.notKid(playerData.getPlayerID())`; others get `server.getKidSessions().load(playerData)` right after `loadPlayer` (spec: loaded once per session). The join event built at `:198-199` gets `setKidState(state)` before `eventOccurred(join)`.
  - `ServerMainRoom` and `ServerTable`: `protected KidLiveSessions kidSessions()` returns `server == null ? null : server.getKidSessions()`; null means no kid filtering (unit-test subclasses only).
  - `handleJoin` (both main-room classes): `kidSessions().join(joinEvent.getPlayer(), joinEvent.getKidState())` before `playersInMainRoom.put`. `handleExit` (both): `kidSessions().leave(exitEvent.getPlayer())` inside the `isPlayerInMainRoom` branch, after removal. Boot already goes through `handleExit`.
  - `handleText` (main room, where `"lobby"`; table, where `"table " + tableNum`): for each recipient, `if (ks != null && !ks.allowed(actor, d)) continue;` before the ignore lookup. After every `dsgEventRouter.routeEvent(event, d.getName())` add `ks.delivered(d, where, player, text, new Date())`. No message to the sender about kid filtering.
  - `handleInvite`: at the routing point, when `!ks.allowed(actor, invitee)`, route `new DSGInviteTableEvent(inviteEvent.getPlayer(), tableNum, inviteEvent.getPlayerToInvite(), "")` instead of `inviteEvent`.
  - `handleInviteResponse`: when the responder (`getPlayerInMainRoom(inviteResponseEvent.getPlayer())`) and `actor` are not allowed, route `new DSGInviteResponseTableEvent(inviteResponseEvent.getPlayer(), tableNum, inviteResponseEvent.getToPlayer(), "", inviteResponseEvent.getAccept(), inviteResponseEvent.getIgnore())` instead. The `ignore` handling below is unchanged.
  - `DSGContextListener.contextDestroyed` (`:418`): the **first** statements of the method. Move the `Resources` lookup (`:424-426`) above `gameStats.destroy()` (`:419-421`), then add `private static void flushKidLiveChat(Resources resources)`, called right after it:
    - `long deadline = System.currentTimeMillis() + KidLiveSessions.SHUTDOWN_BUDGET_MILLIS;` once, shared by all servers.
    - `for (Server s : resources.getServers())` (`Resources.java:129` returns `List<Server>`): `if (s.getKidSessions() != null) s.getKidSessions().flushForShutdown(deadline);`, each in its own `try`/`catch (Throwable t)` that logs and continues, so a kid-flush problem never blocks the rest of shutdown.
    - It runs before `getTbGameStorer().destroy()` (`:428`), `getDsgPlayerStorer().destroy()` (`:430`), the message storer and kid copy mailer destroys (`:433`, Task 5) and `RedisConnectionManager.getInstance().destroy()` (`:434`), so the player storer, kid storer and Redis are all still up. Sends run on Tomcat's shutdown thread through `KidCopyMailer.copyLiveChatNow`, never the `BackgroundWorker` queue.
    - Worst case is about 5 s (`SHUTDOWN_BUDGET_MILLIS`) plus one `SHUTDOWN_SEND_TIMEOUT_MILLIS` per SMTP phase of the send already running at the deadline; Docker's default 10 s stop grace applies (`docker-compose.yml` sets no `stop_grace_period`).

- [ ] **Step 1: Add the failing tests.**
  - `KidWirePrivacyTest.testJoinEventDoesNotSerializeKidState`: a join event with `setKidState(new LiveKidState(42, 7L, Set.of(9L), true, 1L))` encodes with no `kidState` key anywhere under `dsgJoinMainRoomEvent`.
  - `ServerTableKidTest` and `ServerMainRoomKidTest`: subclass the room/table the way `ServerTableDrawScoringTest` (`CapturingTable`, `:220`) and `ArenaServerTableJoinRequestTest` do, with a recording `DSGEventToPlayerRouter`, and override `kidSessions()` to return a `KidLiveSessions` built on `MapPlayerStorer` / `InMemoryKidStorer` / `InMemoryChatCopyStore` / `RecordingTextMailer` (kid 100 parent 1, stranger 200, whitelisted 300, computer player 600). If a constructor needs more of a live `Server` than a subclass can stub, factor the per-recipient decision into a `protected` helper and test that instead.
    - `ServerTableKidTest`: `testTableTextToKidSpectatorDropped` (200 speaks; kid spectator gets nothing, another spectator gets it); `testKidTextToWhitelistedPlayerDelivered`; `testKidGetsOwnEcho`; `testComputerPlayerTextReachesKid`; `testInviteTextBlankedForKid` (routed `DSGInviteTableEvent` has `inviteText` `""`); `testInviteResponseTextBlanked` (`responseText` `""`, accept flag kept); `testDeliveredBuffersOnlyForKidWithCopyOn`.
    - `ServerMainRoomKidTest`: `testLobbyTextFilteredPerRecipient`; `testJoinAndExitRegisterKidState` (`get` non-null after join, null after exit); the same join/exit test for `TournamentServerMainRoom`.
- [ ] **Step 2: Run.** `TEST_ONE` for `org.pente.gameServer.kids.test.KidWirePrivacyTest`, `org.pente.gameServer.server.test.ServerTableKidTest`, `org.pente.gameServer.server.test.ServerMainRoomKidTest`. Expected: `javac` errors (`setKidState`, `kidSessions`).
- [ ] **Step 3: Implement** everything under Files.
- [ ] **Step 4: Run.** Expected `OK (6 tests)`, `OK (7 tests)`, `OK (3 tests)`. Then `TEST_ALL` passes, and `TEST_ONE org.pente.gameServer.server.test.ServerTableDrawScoringTest` passes (it subclasses `ServerTable` and is not in `test`).
- [ ] **Step 5: Deploy smoke.** `./justCompile`; expected `BUILD SUCCESSFUL`. Then `grep -i exception` over the last 200 lines of `dockerMain/logs/dsg/gameServer.log`. Expected: nothing new mentioning `Kid`. Then `docker compose -f docker-compose.yml restart pente.org` (as `justCompile:14` does) and grep the log for `Kid live-chat shutdown flush: server=`: one line per live server, `mailed=0 left=0` (no kids connected). Live behaviour, including a restart with a buffered kid, is verified in Task 13.
- [ ] **Step 6: Commit.** `-m "Kids: filter lobby, table and invite text per recipient in the live server"`.

---

### Task 10: Account rules and kid restrictions (profile, forgot password, age)

**Files:**
- Create: `J/gameServer/kids/KidAccountRules.java` (extend it if Task 6 created it)
- Create: `J/gameServer/core/ReservedNames.java`
- Modify: `J/gameServer/client/web/RegisterServlet.java` (reserved checks `:178-193` call `ReservedNames.isReserved`)
- Modify: `J/gameServer/client/web/ChangeProfileServlet.java` (`myInfo` validation chain, `:193-200`)
- Modify: `J/gameServer/client/web/ForgotPasswordServlet.java` (`:80-100`, redirect `:118-123`)
- Modify: `J/gameServer/client/web/AgeVerificationServlet.java` (`:59-66`)
- Modify: `dsg_src/httpdocs/gameServer/myprofile.jsp` (password rows `:100-123`, email row `:136-147`)
- Test: `J/gameServer/kids/test/KidAccountRulesTest.java`, `J/gameServer/core/test/ReservedNamesTest.java`; add both to `build.xml`

**Interfaces:**
- Produces:
  - `KidAccountRules` — public `static final String` constants, one per error string in the API contract tables: `LOGIN_REQUIRED`, `UNKNOWN_ACTION`, `BAD_ORIGIN`, `KIDS_CANT_MANAGE`, `VERIFY_EMAIL`, `INVALID_DATA`, `PLAYER_NOT_FOUND`, `CANT_ADD_SELF`, `CANT_CONVERT`, `KID_NOT_FOUND`, `INVALID_PASSWORD`, `CANT_APPROVE`, `NOT_APPROVED`, `DATABASE`, `PROFILE_MANAGED`, `KID_PM_ERROR`, `KID_BROADCAST_ERROR`, `LINK_INVALID = "This link is invalid or has expired."`, plus `static String nameTaken(String name)`. Pure statics:
    - `static String parentError(DSGPlayerData caller, boolean addingKid)` — `KIDS_CANT_MANAGE` if the caller is a kid; `VERIFY_EMAIL` if `addingKid && !getEmailValid()`; else null.
    - `static String conversionError(DSGPlayerData caller, DSGPlayerData target, boolean targetHasKids)` — `PLAYER_NOT_FOUND` (null, inactive or guest), `CANT_ADD_SELF`, `CANT_CONVERT` (admin, computer, already a kid, or has kids), else null. Used as-is by the confirm POST.
    - `static boolean conversionSilentlyRefused(DSGPlayerData target, boolean targetHasKids)` — true for an admin, a kid, or a target with kids. `KidsServlet.convert` checks it after `PLAYER_NOT_FOUND`/`CANT_ADD_SELF` and before `conversionError`, and answers the normal success body with nothing stored or sent, so `convert` can't probe kid status. `CANT_CONVERT` is then only reachable for computer accounts.
    - `static SimpleDSGPlayerData newKidData(DSGPlayerData parent, String name, String encryptedPassword)` — the row `create` inserts in one `insertPlayer` (atomic: no window where an unrestricted account exists). Fields as `DSGPlayerStorerRegisterHandler.register` `:97-108` sets them (email = parent's email, `emailVisible` false, parent's timezone, `DSGPlayerData.UNKNOWN` sex, age 0, `HUMAN`), plus `parentPid = parent.getPlayerID()`, `emailValid = false` (so `CacheMessageStorer.java:104-121` never emails or CCs PMs to the parent's address; only opt-in copies do), `mobileAdult = false`, and the status/dates a fresh registration gets.
    - `static boolean emailVisibleFor(boolean isKid, boolean requested)` — false for a kid, else `requested`.
    - `static boolean ownsKid(DSGPlayerData caller, DSGPlayerData kid)` — `kid != null && kid.getParentPid() != null && kid.getParentPid() == caller.getPlayerID()`.
    - `static boolean passwordValid(String pw)` — 5-16 chars of `[A-Za-z0-9_]` (same as `DSGPlayerStorerRegisterHandler.isValidRegistration`).
    - `static String whitelistAddError(DSGPlayerData kid, DSGPlayerData target)` — `PLAYER_NOT_FOUND` / `CANT_APPROVE` / null.
    - `static String profileChangeError(boolean isKid, String currentEmail, String newEmail, String newPassword)` — `PROFILE_MANAGED` when kid and (non-empty password, or `newEmail` non-empty and different from `currentEmail`), else null. An empty `newEmail` counts as unchanged (`ChangeProfileServlet.java:149-153` turns a missing `changeEmail` into `""`).
    - `static boolean linkExpired(Date created, Date now)` — older than 7 days.
    - `static DSGPlayerData passwordEmailTarget(DSGPlayerData player, DSGPlayerData parent)` — parent when the player is a kid and the parent is non-null, else the player.
  - `ReservedNames.isReserved(String lowerName, UsernameFilter filter)` — exactly today's `RegisterServlet` checks (`dwe`, starts `mm`, `mm_ai`, `admin`, `guest`, filter offensive; a null filter skips that check).
  - `ChangeProfileServlet`: for a kid, `changeProfileError = KidAccountRules.PROFILE_MANAGED` when `profileChangeError` is non-null. Nothing is saved. For a kid, an empty email keeps the stored email (never saved as `""`), and `setEmailVisible(emailVisibleFor(true, …))` always saves `N` (the kid's address is often the parent's). Other fields keep today's behaviour.
  - `ForgotPasswordServlet`: for a kid, load the parent and email it via `passwordEmailTarget`. Message: `<parentName>,\n\nHere's the password for the kid account <kidName> on Pente.org: <password>\n\nhttps://www.pente.org/`, subject `Pente.org - Your kid's password`, `toPid`/`toName`/`toEmail` from the parent. The `emailValid` flag on the confirmation redirect uses the target's `getEmailValid()`.
  - `AgeVerificationServlet`: kid gives status 403 and body `{"result":failed,"error":"kid_account"}` with no update.
  - `myprofile.jsp`: when `dsgPlayerData.getParentPid() != null`, render the password inputs `disabled`, the email input `readonly`, the email-visible checkbox `disabled` and unchecked, and the text `Managed by your parent` next to each.

- [ ] **Step 1: Write failing tests.**
  - `KidAccountRulesTest`: `testParentErrorKid`, `testParentErrorUnverifiedOnlyWhenAdding`, `testParentOk`, `testConversionErrors` (null, inactive, self, admin, computer, kid, has kids → each constant), `testConversionOk`, `testOwnsKidTrue`, `testOwnsKidOtherParent`, `testOwnsKidNotAKid`, `testPasswordValid` (`abcde` ok; `abcd`, 17 chars, `ab cd!` not), `testWhitelistAddErrors`, `testProfileChangeError` (kid same email and empty password → null; kid empty email → null; kid new password → `PROFILE_MANAGED`; kid changed email → `PROFILE_MANAGED`; non-kid → null), `testLinkExpired` (6 days false, 8 days true), `testPasswordEmailTarget`, `testNameTakenText` (equals `Requested name bob is already taken, please choose another.`), `testConversionSilentlyRefused` (admin, kid, has kids → true; normal player and computer → false), `testNewKidDataIsUnverifiedKid` (`getParentPid()` = parent pid, `getEmailValid()` false, email = parent's, `getMobileAdult()` false, `emailVisible` false), `testKidEmailNeverVisible`.
  - `ReservedNamesTest`: `testDweReserved` (`dweebo1`), `testMmPrefixReserved` (`mmfoo`), `testMmAiReserved` (`xmm_ai`), `testAdminReserved` (`badadmin`), `testGuestReserved` (`myguest`), `testNormalNameAllowed` (`kidone`, real filter from `UsernameFilter.loadShipped()`), `testNullFilterSkipsOffensiveCheck`.
- [ ] **Step 2: Run** both with `TEST_ONE` (`org.pente.gameServer.kids.test.KidAccountRulesTest`, `org.pente.gameServer.core.test.ReservedNamesTest`). Expected: `javac` errors.
- [ ] **Step 3: Implement** everything under Files.
- [ ] **Step 4: Run.** Expected `OK (17 tests)` and `OK (7 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Commit.** `-m "Kids: account rules; kids can't change password, email or adult flag; forgot password goes to parent"`.

---

### Task 11: `/gameServer/kids` and the conversion confirmation

**Files:**
- Create: `J/gameServer/kids/KidsServlet.java`, `J/gameServer/kids/KidLinkConfirmServlet.java`, `J/gameServer/kids/KidAccountEmails.java`
- Create: `J/gameServer/client/web/ForumUserSetup.java` (move `RegisterServlet`'s forum block `:227-253` and its `adminToken` `:34`)
- Modify: `RegisterServlet.java` (call `ForumUserSetup.initNewUser(name, emailUpdates)`)
- Modify: `dsg_src/httpdocs/WEB-INF/web.xml` (servlets beside `ChangeIgnoredServlet` `:573-576`; mappings beside `:812-815`)
- Modify: `J/gameServer/client/web/SimpleLoginAccessController.java` (`loginPages` `:5-27`: add `"gameServer/myprofile/kids"` only; matching is `endsWith`, so the existing `"gameServer/myprofile"` entry doesn't cover it). `"gameServer/kids"` stays **out**: `LoginFilter.java:304-307` forwards `requiresLogin` URIs to the HTML login page before the servlet runs, which would make the contract's 401 JSON unreachable. `LoginFilter` still processes `name2`/`password2` and the session for every URI, so the servlet's own `loggedInPlayer` check works.
- Test: `J/gameServer/kids/test/KidAccountEmailsTest.java`, `J/gameServer/kids/test/KidsServletOriginTest.java`; add both to `build.xml`

**Interfaces:**
- Consumes: `KidAccountRules` (Task 10), `KidStorer.setParentPid` (Task 3), `CacheDSGPlayerStorer.refreshPlayer(String)`, `CacheKidStorer` (Task 3), `ReservedNames` (Task 10), `DSGPlayerStorerRegisterHandler`, `PasswordHelper` (ctx attribute), `SendMail2.sendMailSaveInDb`, `MySQLDSGReturnEmailStorer` (ctx attribute), ctx attribute `emailEnabled`, `UsernameFilter.loadShipped()`, `MobileJsonHelper.playerColorNonZero`.
- Produces:
  - `ForumUserSetup.initNewUser(String name, boolean newsWatch)` — same Jive properties as today; never throws (logs).
  - `KidAccountEmails` (pure):
    - `createdSubject()` → `Pente.org kid account created`
    - `createdBody(String parentName, String kidName, String password)` → greeting `Dear <parent>,`, `You created the kid account <kid> on Pente.org.`, `Name: <kid>`, `Password: <password>`, `<kid> can play anyone and can chat only with players you approve at https://www.pente.org/gameServer/myprofile/kids`.
    - `conversionSubject()` → `Pente.org kid account request`
    - `conversionBody(String kidName, String parentName, String code)` → explains the kid restrictions, contains `https://www.pente.org/confirmKidLink?code=<code>`, says the link works for 7 days and to ignore the email if unwanted.
  - `KidsServlet` (mapping `/gameServer/kids`) implements the API contract exactly. Shape and helpers follow `ChangeIgnoredServlet` (`loggedInPlayer`, `flag`, `error`, `write`, private reply classes; Gson). Behaviour per action:
    - Every POST, first: when the request has no `name2`/`password2` (cookie/session auth), `public static boolean sameOrigin(String origin, String referer, String serverName)` (called with the two headers and `request.getServerName()`) must hold — the host of `Origin`, or when absent of `Referer`, equals `serverName` (parse with `java.net.URI`; unparseable is a fail); neither header present is a fail. Else 403 `BAD_ORIGIN`, nothing changed. Then `parentError(caller, action is create or convert)`; kid-scoped actions first `loadPlayer(kidPid)` + `ownsKid`, else 404 `KID_NOT_FOUND`.
    - `create`: lowercase and trim `kidName`, trim `kidPassword`. `ReservedNames` gives 409. `registerHandler.isValidRegistration(name, pw, caller.getEmail(), false)` else `INVALID_DATA` 400; `loadPlayer(name) != null` gives `nameTaken` 409. Then **one** `players.insertPlayer(KidAccountRules.newKidData(caller, name, passwordHelper.encrypt(pw)))` (kid flag, `emailValid = false` and `mobileAdult = false` in the same row: no window with an unrestricted account); `DSGPlayerStoreException` gives `DATABASE` 500. Then `ForumUserSetup.initNewUser(name, false)`. When `emailEnabled`, mail the parent with `createdBody`. No verification code.
    - `convert`: `PLAYER_NOT_FOUND` 404 / `CANT_ADD_SELF` 400 from `conversionError`; then if `conversionSilentlyRefused(target, hasKids)` reply the normal success body with `message` and stop (nothing stored or mailed); else `CANT_CONVERT` 400 (computer); `hasKids` = `!kidStorer.getKidPids(target).isEmpty()`. `storeLinkRequest(new KidLinkRequest(newCode(), caller, target, now))`. When `emailEnabled`, mail `conversionBody` to the target's registered email. Reply with `message`.
    - `password`: `passwordValid` else 400. `setPassword(passwordHelper.encrypt(pw))`, `updatePlayer` (safe: `parent_pid` is not in the generic UPDATE).
    - `whitelistAdd` / `whitelistRemove` / `settings`: via `CacheKidStorer` (which notifies listeners).
    - `release`: `kidStorer.setParentPid(kidPid, null)`, `((CacheDSGPlayerStorer) players).refreshPlayer(kidName)`, **then** `kidStorer.deleteKidData(kidPid)` (its notify makes connected sessions reload as not a kid and mail any buffered live chat).
    - Log one `log4j.info("Kids: <action> by <caller> kid=<pid>")` per successful POST.
  - `KidLinkConfirmServlet` (mapping `/confirmKidLink`, no login):
    - GET `?code=`: a missing or expired request shows `LINK_INVALID` (and deletes an expired row). Otherwise a minimal HTML page: `<parentName> wants to manage <kidName> as a kid account. ...` plus a POST form with hidden `code` and a `Confirm` button. GET never changes data, so mail scanners that prefetch links can't confirm.
    - POST: same checks; re-check `conversionError(parent, target, hasKids)` and that the parent is active and not a kid; on error show that error string. Else set `mobileAdult = false` and `updatePlayer` on a freshly loaded copy, then `kidStorer.setParentPid(kidPid, parentPid)`, `((CacheDSGPlayerStorer) players).refreshPlayer(kidName)`, `deleteKidData(kidPid)` (fresh rows plus notify, after the refresh so live sessions reload the kid), `deleteLinkRequest(code)`. Show `<kidName> is now a kid account managed by <parentName>.`

- [ ] **Step 1: Write failing tests** `KidAccountEmailsTest`: `testCreatedBodyHasNameAndPassword`, `testConversionBodyHasLink` (contains `https://www.pente.org/confirmKidLink?code=abc`), `testSubjects`. `KidsServletOriginTest` (calls `KidsServlet.sameOrigin` directly, server name `localhost`): `testSameHostOriginAllowed`, `testCrossOriginRefused` (`Origin: https://evil.example`), `testRefererUsedWhenNoOrigin`, `testNoHeadersRefused`.
- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.KidAccountEmailsTest` and `TEST_ONE org.pente.gameServer.kids.test.KidsServletOriginTest`. Expected: `javac` errors.
- [ ] **Step 3: Implement** everything under Files.
- [ ] **Step 4: Run.** Expected `OK (3 tests)` and `OK (4 tests)`; `TEST_ALL` passes.
- [ ] **Step 5: Deploy and smoke.** Copy `web.xml` into the container (Global Constraints), then `./justCompile`. With `/private/tmp/claude-501/kids-plans/e2e/smoke_kids.py`:
  - GET `/gameServer/kids` without creds gives 401 JSON `Login required`.
  - GET as iostest gives 200 `success:true`, `isKid:false`.
  - Log in as iostest with a cookie jar (POST `name2`/`password2` to `/gameServer/index.jsp`), then POST `action=settings&kidPid=1` with the cookie only and `Origin: https://evil.example`: 403 `Request origin not allowed`.
  - GET `/confirmKidLink?code=nope` gives a page containing `This link is invalid or has expired.`
- [ ] **Step 6: Commit.** `-m "Kids: /gameServer/kids JSON endpoint and email-confirmed conversion"`.

---

### Task 12: Website "My kids" page

**Files:**
- Create: `dsg_src/httpdocs/gameServer/myprofileKids.jsp`
- Modify: `ChangeProfileServlet.java` (`command.equals("kids")` sets `redirectPage = "/gameServer/myprofileKids.jsp"`, beside `:83-87`)
- Modify: `dsg_src/httpdocs/gameServer/tabs.jsp` (append `{"My Kids", "/gameServer/myprofile/kids"}` as the **last** tab, and a matching last `subtabs` entry `new String[][]{{"My Kids", "#kids"}}`, keeping the two arrays index-aligned)

**Interfaces:**
- Consumes: `/gameServer/kids` (Task 11) over same-origin `fetch` with the session cookie (the browser adds the `Origin` header the servlet's same-origin check needs; never send `name2`/`password2` from the page). Copy strings from Global Constraints.
- Produces: page at `/gameServer/myprofile/kids`. Layout and includes copied from `myprofilePrefs.jsp` (`begin.jsp`, forum style link, `selectedTab = "My Kids"`, `tabs.jsp`, `end.jsp`). Inline vanilla JS (no new libraries) renders from the GET body:
  - Kid caller: `Your account is managed by your parent, <parentName>.` plus the read-only approved list.
  - Others: a status line (`message` or `error`); `Verify your email address on My Info before adding kids.` when `emailVerified` is false.
  - An "Add a kid" form (name, password, confirm password; client-side match check; posts `create`) and a "Convert an existing account" form (posts `convert`).
  - Per kid: name, a set-password field (`password`), the approved list with a `Remove` button each (`whitelistRemove`), an add-player field (`whitelistAdd`), two checkboxes `Email me copies of private messages` and `Email me copies of live game room chat` (`settings`) with the live-chat best-effort note under the second, and `Release` behind `confirm("Release <name>? They will become a normal account.")`.
  - After any POST, re-render from the response body. All user text is inserted with `textContent`, never `innerHTML`.

- [ ] **Step 1: Implement.** Logic belongs in the servlet; the page only renders.
- [ ] **Step 2: Deploy.** `./justCompile` (the servlet changed; JSPs are live).
- [ ] **Step 3: Verify.** `/private/tmp/claude-501/kids-plans/e2e/check_page.py`:
  - Log in as iostest with a cookie jar (POST `name2`, `password2` to `/gameServer/index.jsp`).
  - GET `/gameServer/myprofile/kids`. Expected: 200, HTML containing `My Kids`, `Add a kid` and `/gameServer/kids`.
  - Then, if the Playwright tools are available, open the page (accept the self-signed cert, or note that it blocks) and take a screenshot to `/private/tmp/claude-501/kids-plans/e2e/page.png`. Report whether this ran.
- [ ] **Step 4: Commit.** `-m "Kids: My Kids page on the website"`.

---

### Task 13: Full suite and local end-to-end verification

**Files:**
- Create (scratch, never committed): `/private/tmp/claude-501/kids-plans/e2e/kids_e2e.py` (HTTP) and `/private/tmp/claude-501/kids-plans/e2e/kids_live.mjs` (live room, TCP+TLS port 15999: one JSON object per frame, each frame ended by byte `255`; model it on the `Conn` class of the arena E2E driver if `/private/tmp/claude-501/arena-plans/e2e/arena-e2e.mjs` exists).
- No repo changes. A failing expectation is first checked against the spec. A real defect is fixed in the task that owns the code (test first, own commit), then this task reruns from Step 1.

**Interfaces:**
- Consumes: everything above, deployed locally. Accounts: parent `iostest`, stranger `graviton`, and a kid `kidse2e1` created by the script.
- Produces: evidence lines (command plus output) for the controller.

- [ ] **Step 1: Full suite.** `TEST_ALL`. Expected: every runner prints `OK (`, then `BUILD SUCCESSFUL`. Also `TEST_ONE org.pente.gameServer.server.test.ServerTableDrawScoringTest` passes.
- [ ] **Step 2: Prepare local data.**
  - Record iostest's current `email` and `email_valid` into `/private/tmp/claude-501/kids-plans/e2e/restore.sql`.
  - Set iostest's email to `kids-e2e-parent@example.invalid`, `email_valid='Y'`.
  - Deploy: copy `web.xml`, then `./justCompile`.
- [ ] **Step 3: HTTP scenarios** (`kids_e2e.py` prints `PASS <name>` or `FAIL <name>: <detail>` per line, and `ALL OK` at the end):
  1. iostest `create` `kidse2e1` gives 200, and the kid appears in `kids`. Repeat gives 409 `nameTaken`. SQL: the kid's `email_valid` is `N` and `parent_pid` is iostest's pid (one row, written by one insert).
  2. `/gameServer/mobile/json/index.jsp` as the kid has `player.parentPid` equal to iostest's pid. As graviton it has no `parentPid`.
  3. Kid GET `/gameServer/kids` gives `isKid:true`, `parentName:"iostest"`. Kid POST `create` gives 403 `Kid accounts can't manage kids`.
  4. graviton POST `whitelistAdd` with the kid's `kidPid` gives 404 `Kid not found`; the DB whitelist is unchanged.
  5. PM graviton→kid via `/gameServer/mymessages` `command=create` gives a page containing `Player is ignoring your messages.` PM kid→graviton gives `You can only send messages to players your parent approved.` PM iostest→kid succeeds (302 to the inbox).
  6. `whitelistAdd playerName=Graviton` (mixed case) gives 200 with graviton in the list. Then the PM kid→graviton succeeds, and with `copyMessages` still off, no mail to `kids-e2e-parent@example.invalid` is logged for that PM or for a PM iostest→kid (grep the logs for the address and for `Kid copy queued`; both absent).
  7. `settings copyMessages=Y`, then a PM kid→graviton. `gameServer.log` gains `Kid copy queued: kid=<kid> parent=<iostest> kind=pm`.
  8. Kid POST `/gameServer/ageVerification adult=true` gives 403 with body containing `kid_account`.
  9. Forgot password for `kidse2e1`: the log shows mail to `kids-e2e-parent@example.invalid` (grep `dockerMain/logs` for that address, or the `SendMail2` log line).
  10. `convert kidName=graviton`: 200 `message` `Confirmation email sent to graviton`. Read the code from `dsg_kid_link_request` via SQL. GET `/confirmKidLink?code=…` shows the Confirm form and graviton is still not a kid. POST confirms; graviton's `parent_pid` is iostest. Then `release` graviton gives `parent_pid` NULL and no rows in the kid tables.
  11. graviton `convert kidName=kidse2e1` (already a kid) gives 200 with the same `message` shape as scenario 10, and `dsg_kid_link_request` gains no row for the kid.
  12. Cross-origin: with iostest's website cookie jar, POST `whitelistAdd` (kid's `kidPid`, `playerName=graviton` after removing him) with `Origin: https://evil.example` gives 403 `Request origin not allowed`; the DB whitelist is unchanged. The same POST with `Origin: https://localhost` succeeds.
  13. Open TB invitation text (D3): graviton creates an open TB invitation (no invitee) with message `hello-open` via `/gameServer/tb/new` (graviton not whitelisted). As the kid, GET `/gameServer/tb/replyInvitation?command=load&sid=<sid>` (and with `mobile=`): page has no `hello-open`. As iostest, the same page shows it. Then cancel the invitation as graviton.
- [ ] **Step 4: Live scenarios** (`kids_live.mjs`; logs in kid, iostest and graviton over TLS 15999 with `{"dsgLoginEvent":{"player":…,"password":…,"time":0}}`; prints `PASS`/`FAIL` lines and `ALL OK`). Start from graviton **not** whitelisted and `copyLiveChat=Y` (via HTTP):
  1. The kid's `dsgLoginEvent.me.parentPid` equals iostest's pid. graviton's copy of the kid's `dsgJoinMainRoomEvent.dsgPlayerData` has no `parentPid`.
  2. graviton says `g1` in the lobby: iostest gets it, the kid does not (wait 2 s).
  3. The kid says `k1`: iostest gets it, graviton does not; the kid gets its own echo.
  4. iostest says `p1`: the kid gets it.
  5. While connected, HTTP `whitelistAdd graviton`. graviton says `g2`: the kid now gets it (listener refresh).
  6. During the session `redis-cli KEYS 'kids:livechat:*'` lists one key, and `TTL` of it is between 1 and 86400.
  7. The kid disconnects. Within 5 s the key is gone and `gameServer.log` has `kind=live`.
  8. Reconnect: the kid logs in again right after closing. The stranger rule still holds (remove graviton, then graviton's `g3` does not reach the kid).
  9. Table invite: graviton creates a table and invites the kid with `inviteText` `hi`. The kid receives `dsgInviteTableEvent` with `inviteText` `""`.
  10. Table text: the kid sits at graviton's table (or watches it). graviton says `t1` at the table: the kid does not get it; iostest watching the same table does. The kid says `t2`: graviton does not get it.
  11. Restart with a buffered kid (shutdown flush): the kid connected with `copyLiveChat=Y`, iostest says `p2` so the kid's `kids:livechat:*` key holds a line. Run `docker compose -f docker-compose.yml restart pente.org` from the repo root while the kid is still connected. Expected: `gameServer.log` has `Kid copy sent: kid=<kid> parent=<iostest> kind=live-shutdown` and `Kid live-chat shutdown flush: server=… mailed=1 left=0`, and after the container is back `redis-cli KEYS 'kids:livechat:*'` is empty. If local SMTP refuses the send, the expected outcome is instead `mailed=0 left=1`, the key still present with its line and a TTL between 1 and 86400; then the kid logs in and disconnects again, and the log gains `kind=live` and the key is gone (next-leave fallback). Report which branch ran. Run this scenario last: the restart drops every live connection.
- [ ] **Step 5: Restore and clean up.**
  - Release `kidse2e1` and set its `status` to inactive via SQL (or leave it; it's local dev data). Note which in the report.
  - Run `restore.sql` for iostest.
  - `git -C <repo> status --porcelain | grep '^??'` shows nothing new.
- [ ] **Step 6: Report** the evidence lines. Name any scenario that could not run, and why.

---

## Self-review notes (writer)

- **Spec coverage:** data model (T1, T3), privacy of the kid flag (T1, T9), allowed rule and constants (T2), lifecycle create/convert/release/parent actions (T10, T11, T12), password/email/forgot/mobile_adult restrictions (T10), every surface in the spec table:
  - PMs (T6), broadcast (T6), lobby and table (T9), invite text (T9).
  - TB invitation/move/resign (T7), open-invitation display to kids (T7), cancel message (T7), decline PM (T6), opponent-attributed notices (T6).
  - System lines unchanged; push notifications follow the refusals (T6).
- **Copies:** PM copies (T5, T6), live-chat buffer with TTL, flush on logout/close/boot, synchronous flush at server shutdown with budget and next-leave fallback (T5, T8, T9, T13 live 11), Redis-down skip, empty session (T8, T9). Website page and confirmation landing (T11, T12). Listener refresh (T3, T8).
- **Decisions this plan makes where the spec leaves a choice (builder's choice):**
  - Live filtering is silent, with no new websocket events.
  - Invite text is blanked rather than the invite refused.
  - A recipient-kid PM refusal reuses the existing ignore text, so kid status stays private.
  - TB text is dropped silently.
  - A non-kid's open TB invitation text is stored (recipient unknown at write time), but is omitted when served to a kid viewer whose rule fails (D3, Task 7). A kid's is dropped at write time.
  - Decline PMs keep the notice and drop the free text.
  - A created kid's email (the parent's address) is stored **not verified**, so normal PM notification mail never reaches the parent; only opt-in copies do.
  - `parent_pid` is written only by `KidStorer.setParentPid` (and the create insert), never by the generic player UPDATE.
  - `convert` answers the same success for kid, parent and admin targets and sends nothing.
  - Cookie-authenticated POSTs to `/gameServer/kids` need a same-host `Origin`/`Referer`.
  - Live-chat buffers are drained at server shutdown (user decision 2026-10-19): `flushForShutdown` runs first in `contextDestroyed`, mails each joined kid's buffer synchronously via `copyLiveChatNow` / `BackgroundMailer.mailTextNow` (timeouts set, `Session.getInstance`), within a shared 5 s budget. Unsent buffers (budget spent, send failure, kid not joined) stay in Redis (TTL 24 h) and go out with the kid's next `leave`. After the flush, `leave` no longer drains, so nothing is queued onto the mailer thread that shutdown interrupts. Not `SendMail2`: no timeouts and JVM-wide default session.
  - Conversion GET shows a confirm button and only POST converts.
- **Not in scope (spec):** forums, profiles/avatars, existing messages, `EmailPlayerServlet` (unmapped dead code per recon).
