# Kids: Turn-Based Messages for Parents (server + website) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent can read every turn-based (TB) message in their kid's games (private and hidden included) without gaining any write ability, can list the kid's active TB games, can find the kid's finished private games in the kid's history, and can opt in to one transcript email per TB game when it ends.

**Architecture:** One pure helper, `KidParentAccess`, decides "viewer is the parent of a player". The game servlets (`MoveServlet`, `ViewGameServlet`) compute it once and pass it to their JSPs as a request attribute. The two JSON/text app endpoints call it directly. The kid-history search reuses the existing private-game filter by substituting the profile player's pid as the requestor when the viewer is that player's parent. `CacheTBStorer` gets a single game-end hook (`TBGameEndListener`), fired from the end-game thread and from an active-set cancel. `KidCopyMailer.copyTbTranscript` listens to that hook. The `/gameServer/kids` servlet gains the `copyTbMessages` flag and a read-only `activeGames` GET action.

**Tech Stack:** Java 21 Tomcat webapp, Ant (`build.xml`), JUnit 3.7 `TestCase` run by `junit.textui.TestRunner`, Gson, Jedis, log4j 1.2 `Category`, MariaDB (MyISAM), JSP. E2E uses Python 3 `urllib` against the local docker stack.

**Spec:** `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docs/superpowers/specs/2026-10-21-kids-tb-messages-design.md` (approved). Executors read the spec and this plan together. Background on the shipped kids feature: `docs/superpowers/specs/2026-10-19-kids-accounts-design.md` and `docs/superpowers/plans/2026-10-19-kids-accounts-server.md`. This plan covers only `pente.org`. The Android and iOS plans are written from the "Server API contract" section below.

**Base:** `main` at `e13524a`. Every `file:line` below refers to that commit. Earlier tasks shift lines in the same file, so always match on the quoted code, not the number. Paths are relative to `/Users/waliedothman/mariposa/coding/pente.org-project/pente.org/`. `J/` means `dsg_src/java/org/pente/`, `H/` means `dsg_src/httpdocs/gameServer/`.

## Server API contract (consumed by the client plans)

Client plans must not assume anything beyond this section. Auth, origin rules and error bodies of `/gameServer/kids` are unchanged from the 2026-10-19 contract (`name2`/`password2` params for apps, session cookie for the website, `{"success": false, "error": "<string>"}` on failure).

### 1. `copyTbMessages` flag

- `GET /gameServer/kids` (parent view): each entry of `kids[]` gains `"copyTbMessages": false|true`, beside `copyMessages` and `copyLiveChat`.
- `POST /gameServer/kids` `action=settings`: new optional param `copyTbMessages` (`Y` or `true` is on, anything else off). A missing param keeps the stored value, exactly like the other two flags. The reply is the GET body after the change.
- A server without this feature omits the field; clients treat a missing field as `false` and may hide the toggle.
- Toggle label in every client (spec, verbatim): `Turn-based game messages`.

### 2. Active TB games of a kid

`GET /gameServer/kids?action=activeGames&kidPid=<kid pid>`

Success (200):
```json
{
  "success": true,
  "kidPid": 23000000012345,
  "games": [
    {
      "gid": 50000000001234,
      "game": 51,
      "gameName": "Pente",
      "opponentPid": 23000000000777,
      "opponentName": "graviton",
      "kidsTurn": true,
      "lastMoveDate": 1760882580000,
      "privateGame": true
    }
  ]
}
```
- `games` holds every game in state active (`'A'`) of every set the kid plays in, both games of a two-game set separately. Invitations that have not started and finished games are never listed. `games` is `[]` when there are none.
- `game` is the TB game id (`GridStateFactory` `TB_*`), `gameName` is `GridStateFactory.getGameName(game)`.
- `kidsTurn` is `TBGame.getCurrentPlayer() == kidPid`.
- `lastMoveDate` is epoch milliseconds (`TBGame.getLastMoveDate()`), `0` if unknown.
- Order: `lastMoveDate` descending, ties by `gid` ascending.
- A game whose opponent player cannot be loaded is left out.
- Errors: 401 `Login required` (no login), 404 `Kid not found` (`kidPid` missing, unparseable, or not the caller's kid; a kid caller always gets this), 500 `Database error`.
- `GET /gameServer/kids` without `action`, or with any other `action`, returns the existing caller view unchanged.
- Opening a row: apps load the game exactly as today (`/gameServer/mobile/json/game.jsp?gid=<gid>`) and show it read-only. The website links `/gameServer/tb/game?gid=<gid>&command=load`.

### 3. Parent can read messages

A viewer is a **parent of the game** when either player's `parent_pid` equals the viewer's pid at request time. A released kid ends this at once.

| Endpoint | Change for a parent of the game |
|---|---|
| `/gameServer/mobile/json/game.jsp?gid=` (Android, iOS) | Message fields are filled exactly as for a player, with **all** stored messages (the kid-only invitation-text filter does not apply to a parent). |
| `/gameServer/mobile/game.jsp` (legacy text format) | Same. |
| `/gameServer/tb/game?gid=&command=load` (web, MoveServlet) | Private and hidden games load instead of the error `Invalid game, game hidden or private and other player trying to view it.`; messages are shown, including on the Go dead-stone pages. |
| `/gameServer/viewLiveGame?g=` and `?g=&mobile=` (finished games) | Private games load instead of `Game id invalid, please try again.`; messages are shown. |
| `/gameServer/viewLiveGames?p=<kid>&g=<game>` (kid's history) | The parent sees the kid's private games, as the kid does. |

No other field of `mobile/json/game.jsp` changes. Writes by a parent who is not a player are refused exactly as for any non-player (no new error strings). Clients must still show the board read-only for a non-player.

## Global Constraints

- Repo `pente.org` only, branch `kids-tb-messages` created from an up-to-date `main`. Always `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org`, never a cwd-dependent git command. Commit with `-S`. No co-author or generated-by lines. Never stage the pre-existing `.gitignore` change or files this plan did not create or modify.
- Toolchain: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/`, `ant` at `/opt/homebrew/bin/ant`. JUnit is `dsg_src/lib/junit-3.7.jar`: **no `assertFalse`** (use `assertTrue(!x)`); tests extend `junit.framework.TestCase` with a `(String name)` constructor.
- `TEST_ONE <FQCN>` means exactly:
  `rsync -urtd --exclude-from /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/exclude_compile.txt /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/ /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/deploy/ && env TMPDIR=/private/tmp/claude-501/kids-tb JAVA_HOME=/opt/homebrew/opt/openjdk@21/ ant -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/build.xml test-one -Dtest=<FQCN>`
  Pass prints `OK (N tests)` then `BUILD SUCCESSFUL`; failure prints `FAILURES!!!` or a `javac` error then `BUILD FAILED`. Route output through `ctx_execute` and grep `OK (|FAILURES|error:|BUILD`.
- `TEST_ALL` means the same rsync, then `ant -f .../build.xml test` with the same env. Every new test class is added to the `test` target's fixed list in `build.xml` (after the `KidLiveSessionsTest` block, `build.xml:168-171`), one `<java classname="junit.textui.TestRunner" fork="true" failonerror="true">` block each.
- `deploy/` and `deployClasses/` are the project's gitignored build dirs (`.gitignore:5-6`); that is where builds go. Every other scratch, temp and E2E file lives only under `/private/tmp/claude-501/kids-tb/` (create it). Never in a repo. Before each commit, `git -C <repo> status --porcelain | grep '^??'` must list nothing this plan did not mean to add.
- Tests never live under a `client/` path (`build.xml:37` excludes `**/client/**`). New kids tests go in `J/gameServer/kids/test/` (package `org.pente.gameServer.kids.test`); storer tests go in `J/turnBased/test/` (package `org.pente.turnBased.test`, to reuse `InMemoryTBGameStorer`, `InMemoryDSGPlayerStorer`, `SerializingRedisConnectionManager`).
- New production code lives in package `org.pente.gameServer.kids` (`J/gameServer/kids/`) except `TBGameEndListener` (package `org.pente.turnBased`). Log output lands in `dockerMain/logs/dsg/gameServer.log`.
- Local stack is an isolated dev backend. Deploy Java with `./justCompile` run from the repo root (compiles, then restarts the `pente.org` container; no hot reload). JSPs under `H/` are mounted live.
- Local SQL: `docker compose -f /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml exec -T main_db sh -c 'mariadb -uroot -p"$MYSQL_ROOT_PASSWORD" dsg'` with SQL on stdin. Never print `.env` values.
- Local outbound mail fails by design (DKIM; `NoSuchProviderException: smtp` in the log is expected). Email behaviour is proven by unit tests with `RecordingTextMailer` and, live, by the `Kid copy queued: ... kind=tb ...` log line.
- E2E credentials for `iostest` and `graviton`: read them from `/Users/waliedothman/.claudeboat/projects/-Users-waliedothman-mariposa-coding-pente-org-project/memory/local-backend-test-accounts.md`, export as `KIDS_E2E_A_PASS` / `KIDS_E2E_B_PASS`. Never write them into a repo or a committed file. `curl`/`wget` are hook-blocked: use Python `urllib` with an unverified SSL context (the `curl -k` equivalent); `/private/tmp/claude-501/kids-plans/e2e/lib.py` is a working model to copy.
- Parent rule (spec, verbatim intent): the viewer is the parent of a player in the game when that player's `parent_pid` equals the viewer's pid **at request time**. A released kid (no `parent_pid`) ends parent access immediately. The check lives in `KidParentAccess` only, never re-implemented in a JSP.
- Read-only: a parent who is not a player gets no write path (move, message, cancel request or reply, undo, resign, draw, hide). No new write code accepts a parent.
- Parent sees the whole conversation: no `KidInvitationText` filtering for a parent viewer or in the transcript.
- Transcript: toggle `copy_tb_messages` default `'N'`; read **at game end** (toggle and parent link); only games that were started; at least one stored message; one email per game per parent; `emailEnabled` and the parent's `email_valid = 'Y'` apply (`usableParent`); sent through `KidCopyMailer` and the existing `BackgroundMailer` queue; never throws into game end.
- Web copy (exact): toggle label `Turn-based game messages`; list heading `Active turn-based games`; empty list `No active turn-based games.`

## Review Focus

1. **A player resigns with a final message.** `ResignServlet:148-151` stores the message *after* `resignGame` queues the game end, so the transcript can miss the last line. Expected: the resign message is in the transcript. Pinned: reorder in Task 5, `messages=<n>` in the log line, E2E scenario 10 checks `n`.
2. **A two-game set is cancelled by agreement after game 1 already finished.** Expected: only the still-active game gets an email; game 1 is not mailed twice. Pinned: `testActiveSetCancelNotifiesOnlyActiveGames` (Task 4).
3. **The kid is released, or the toggle is switched off, between game start and game end.** Expected: no email; released kid's parent can no longer open the private game. Pinned: `testTbTranscriptReleasedKidNoMail`, `testTbTranscriptToggleOffNoMail` (Task 5), `testReleasedKidNotParent` (Task 2), E2E scenario 14.
4. **A sibling (another kid of the same parent) or any other non-player opens a private game.** Expected: refused exactly as before; no message text. Pinned: `testSiblingIsNotParent` (Task 2), E2E scenarios 6-7 with a second kid as viewer.
5. **The copier throws (player storer down, bad game id) during game end or cancel.** Expected: game end and cancel complete, the next game still ends. Pinned: `testThrowingListenerDoesNotStopGameEnd` (Task 4), `testTbTranscriptNeverThrows` (Task 5).

Known and accepted (not tested): an admin re-running `fixTb.jsp` on an already-ended game re-fires the end hook and so re-sends one transcript (admin-only, rare). Timeouts reach the same `EndGameRunnable` hook as resigns (`CacheTBStorer.java:750`) and are covered by the hook test, not by a live E2E (they need days to elapse).

---

## File Structure

New, in `J/gameServer/kids/`:
- `KidParentAccess.java`: pure "viewer is parent of a player" rule, storer-backed overload, history requestor pid, request-attribute name.
- `KidActiveGames.java`: builds the active-games rows from a kid's sets (pure apart from player loads).
- `KidTbTranscript.java`: pure "game was started" rule and message ordering.

New elsewhere: `J/turnBased/TBGameEndListener.java`, `dsg_src/sql/2026-10-21-kid-tb-copy.sql`.

New tests: `J/gameServer/kids/test/KidParentAccessTest.java`, `KidActiveGamesTest.java`, `KidTbTranscriptTest.java`, `J/turnBased/test/CacheTBStorerGameEndTest.java`. Extended: `KidCopyEmailsTest.java`, `KidCopyMailerTest.java`, `CacheKidStorerRedisTest.java`.

Modified choke points are listed per task.

---

### Task 1: `copy_tb_messages` setting end to end (DB, storers, `/gameServer/kids` JSON and `settings`)

**Files:**
- Create: `dsg_src/sql/2026-10-21-kid-tb-copy.sql`
- Modify: `dsg_src/sql/schema.sql` (`dsg_kid_settings` block `:204-209`)
- Modify: `J/gameServer/kids/KidSettings.java` (whole file, 36 lines)
- Modify: `J/gameServer/kids/MySQLKidStorer.java` (`getSettings` `:109-138`, select at `:117`; `storeSettings` `:141-150`)
- Modify: `J/gameServer/kids/KidsServlet.java` (`KidEntry` `:59-65`, `settings` `:329-343`, `success` `:459-466`)
- Test: `J/gameServer/kids/test/CacheKidStorerRedisTest.java` (add tests)

**Interfaces:**
- Produces:
  - `KidSettings(long kidPid, boolean copyMessages, boolean copyLiveChat, boolean copyTbMessages)` plus `boolean isCopyTbMessages()`. The existing 3-arg constructor stays and delegates with `copyTbMessages = false` (existing callers and tests keep compiling). `serialVersionUID` stays `1L`, so a `KidSettings` cached in Redis before deploy reads back with `copyTbMessages == false`. `off(kidPid)` has all three false.
  - DB column `dsg_kid_settings.copy_tb_messages char(1) NOT NULL DEFAULT 'N'`.
  - JSON: `KidEntry.copyTbMessages` (boolean); `settings` action reads optional `copyTbMessages`.

- [ ] **Step 0: Branch and docs.**
  `git -C <repo> fetch origin && git -C <repo> checkout main && git -C <repo> pull --ff-only && git -C <repo> checkout -b kids-tb-messages`. If `main` moved past `e13524a`, match every location below by its quoted code. Then `git -C <repo> add docs/superpowers/specs/2026-10-21-kids-tb-messages-design.md docs/superpowers/plans/2026-10-21-kids-tb-messages-server.md && git -C <repo> commit -S -m "Kids TB messages: spec and server plan"`. `mkdir -p /private/tmp/claude-501/kids-tb`.

- [ ] **Step 1: Write the migration** `dsg_src/sql/2026-10-21-kid-tb-copy.sql`, header in the style of `2026-10-19-kid-accounts.sql` ("Apply to live + replica DBs before deploying the classes. Idempotent."):
```sql
ALTER TABLE dsg_kid_settings ADD COLUMN IF NOT EXISTS copy_tb_messages char(1) NOT NULL DEFAULT 'N';
```
Mirror it in `schema.sql` as the last column of `dsg_kid_settings` (after `copy_live_chat`, backquoted like its neighbours).

- [ ] **Step 2: Write failing tests** in `CacheKidStorerRedisTest` (existing setUp/tearDown):
  - `testSettingsTbFlagRoundTrip`: `cache.storeSettings(new KidSettings(10, false, false, true))`; `cache.getSettings(10).isCopyTbMessages()` is true and the two other flags are false.
  - `testThreeArgSettingsHasTbOff`: `new KidSettings(10, true, true).isCopyTbMessages()` is false; `KidSettings.off(10).isCopyTbMessages()` is false.
  - `testTbFlagSurvivesRedisSerialization`: `RedisConnectionManager.deserialize(RedisConnectionManager.serialize(new KidSettings(10, false, false, true)))` has `isCopyTbMessages()` true.

- [ ] **Step 3: Run.** `TEST_ONE org.pente.gameServer.kids.test.CacheKidStorerRedisTest`. Expected: `javac` error, constructor not found.

- [ ] **Step 4: Implement.**
  - `KidSettings` per Interfaces.
  - `MySQLKidStorer.getSettings`: select `copy_messages, copy_live_chat, copy_tb_messages`; flag is `"Y".equals(...)` like the others. `storeSettings`: insert the third column and add `copy_tb_messages = values(copy_tb_messages)` to the `ON DUPLICATE KEY UPDATE`.
  - `KidsServlet`: `KidEntry.copyTbMessages = s.isCopyTbMessages()`; in `settings`, `copyTbMessages` follows the same "missing keeps current, else `flag(...)`" rule; store the 4-arg `KidSettings`.

- [ ] **Step 5: Run.** Same command, expected `OK (12 tests)` (9 existing + 3). Then `TEST_ALL`: every runner `OK`, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Apply locally and smoke.** Pipe the migration into the local SQL command **twice** (both exit 0). `DESCRIBE dsg_kid_settings;` shows `copy_tb_messages` default `N`. `./justCompile` → `BUILD SUCCESSFUL` and the restart line. Write `/private/tmp/claude-501/kids-tb/e2e/smoke_settings.py` (login as iostest with `name2`/`password2`): GET `/gameServer/kids` shows `copyTbMessages` on each kid; if iostest has a kid, POST `action=settings&kidPid=<kid>&copyTbMessages=Y` returns that kid with `copyTbMessages: true` and unchanged `copyMessages`/`copyLiveChat`; POST again with `copyTbMessages=N` restores it. If iostest has no kid yet, this check moves to Task 9 scenario 1.

- [ ] **Step 7: Commit.** `git -C <repo> add dsg_src/sql/2026-10-21-kid-tb-copy.sql dsg_src/sql/schema.sql J/gameServer/kids/KidSettings.java J/gameServer/kids/MySQLKidStorer.java J/gameServer/kids/KidsServlet.java J/gameServer/kids/test/CacheKidStorerRedisTest.java && git -C <repo> commit -S -m "Kids TB: copy_tb_messages setting in storers and /gameServer/kids"` (paths spelled out in full under `dsg_src/java/org/pente/`).

---

### Task 2: Parent access rule and every message gate

**Files:**
- Create: `J/gameServer/kids/KidParentAccess.java`
- Modify: `J/turnBased/web/MoveServlet.java` (`load` branch `:132-158`: `myTurn` `:133-136`, private/hidden refusal `:137-143`, `showMessages` `:158`)
- Modify: `J/gameServer/client/web/ViewGameServlet.java` (private refusal `:85-91`, attributes `:96-99`)
- Modify: `J/gameServer/client/web/ViewGamesServlet.java` (`search` call `:116-119`)
- Modify JSPs: `H/tb/mobileGame.jsp:64`, `H/tb/deadGo.jsp:63`, `H/tb/finalGo.jsp:63`, `H/mobile/game.jsp:66`, `H/mobile/json/game.jsp:39-41`, `H/viewLiveGame.jsp:110-115`, `H/viewLiveGameMobile.jsp:106-111`
- Test: `J/gameServer/kids/test/KidParentAccessTest.java`; add to `build.xml`

**Gate inventory (complete on `e13524a`, from grepping every JSP/servlet that reads `getMessages()`, `TBMessageThread` or `showMessages`):**

| Gate | Today admits | Change |
|---|---|---|
| `MoveServlet:137-143` private/hidden refusal for non-players | players | exempt the parent |
| `MoveServlet:158` `showMessages` attr for non-players | admin | admin or parent |
| `tb/mobileGame.jsp:64` (served for both desktop and mobile: `MoveServlet:25-26`) | `rainwolf`, players | or request attr `KidParentAccess.REQUEST_ATTR` is `TRUE` |
| `tb/deadGo.jsp:63`, `tb/finalGo.jsp:63` (forwarded from `MoveServlet:188-204`; `:62` is the commented-out old check) | players | same attr |
| `mobile/json/game.jsp:39-41` `canSeeMessages` | `rainwolf`, players | or `KidParentAccess.isParentOf(visitor, player1, player2)` |
| `mobile/game.jsp:66` | `rainwolf`, players | or `KidParentAccess.isParentOf(visitor, player1, player2)` (`visitor` loaded at `:63`) |
| `ViewGameServlet:85-91` private refusal | players | exempt the parent; set the attr for its JSPs |
| `viewLiveGame.jsp:110-115`, `viewLiveGameMobile.jsp:106-111` | players | or attr `TRUE` |
| `ViewGamesServlet:116-119` → `FastMySQLDSGGameLookup.search` private filter (`:234-235`, `:246-247`, binds `:256-262`) | requestor is a player | pass `historyRequestorPid(meData, playerData)` as `requestorPid` |

Not gates (no change): `tb/game.jsp` (not served by any servlet; `MoveServlet:25-26` both point at `mobileGame.jsp`), `tb/cancelReply.jsp:106` and `tb/undoReply.jsp:95` (reached only by players, `showMessages=false`), `viewLiveGameEmbed.jsp:99` (`showMessages` always false), `tbpgn.jsp` (no messages). `FastMySQLDSGGameLookup.count` (`:46-93`) has no private filter at all, so paging stays consistent; `totalGames`/`totalWins` are head-to-head counts of the viewer and keep `meData`'s pid.

**Interfaces:**
- Produces:
  - `public final class KidParentAccess`:
    - `public static final String REQUEST_ATTR = "kidParentViewer";` (value `Boolean`).
    - `public static boolean isParentOf(DSGPlayerData viewer, DSGPlayerData p1, DSGPlayerData p2)`: true when `viewer != null` and some non-null `p` in `{p1, p2}` has `p.getParentPid() != null && p.getParentPid().longValue() == viewer.getPlayerID()`.
    - `public static boolean isParentOf(DSGPlayerData viewer, DSGPlayerStorer players, long p1Pid, long p2Pid)`: loads both (pid `0` → null) and calls the pure overload; any exception is logged and returns **false** (fail closed).
    - `public static long historyRequestorPid(DSGPlayerData viewer, DSGPlayerData profile)`: `profile.getPlayerID()` when `isParentOf(viewer, profile, null)`, else `viewer.getPlayerID()`.
  - Servlets set `request.setAttribute(KidParentAccess.REQUEST_ATTR, Boolean.valueOf(parent))` on every forward of the `load` path (`MoveServlet`) and in `ViewGameServlet` before forwarding.

- [ ] **Step 1: Write failing tests** `KidParentAccessTest` (players as in `KidCopyMailerTest.player(...)`: parent P pid 1, kid K pid 100 parent 1, sibling S pid 101 parent 1, stranger X pid 200, other kid O pid 300 parent 2, released kid R pid 400 parent null):
  - `testParentOfPlayer1`: `isParentOf(P, K, X)`. `testParentOfPlayer2`: `isParentOf(P, X, K)`.
  - `testStrangerIsNotParent`: `!isParentOf(X, K, O)`.
  - `testOtherKidsParentIsNotParent`: viewer pid 2 non-kid, players K and X: false.
  - `testSiblingIsNotParent`: `!isParentOf(S, K, X)`.
  - `testReleasedKidNotParent`: `!isParentOf(P, R, X)`.
  - `testNullsAreFalse`: viewer null → false; both players null → false.
  - `testStorerOverloadLoads`: `MapPlayerStorer` holding P, K, X: `isParentOf(P, players, 100, 200)` true; `isParentOf(P, players, 200, 0)` false.
  - `testStorerFailureFailsClosed`: `failLoads = true` gives false and does not throw.
  - `testHistoryRequestorPid`: `historyRequestorPid(P, K) == 100`; `historyRequestorPid(X, K) == 200`; `historyRequestorPid(P, X) == 1`.

- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.KidParentAccessTest`. Expected: `javac` error.

- [ ] **Step 3: Implement `KidParentAccess`** per Interfaces.

- [ ] **Step 4: Run.** Expected `OK (10 tests)`.

- [ ] **Step 5: Wire the gates** per the inventory table.
  - `MoveServlet`: in `load`, compute `boolean parentViewer = KidParentAccess.isParentOf(playerData, p1, p2)` (needs `p1`/`p2` from `:120-121`; hoist them into locals). In the non-player block: refusal only when `!parentViewer`; `showMessages` is `playerData.isAdmin() || parentViewer`; and `request.setAttribute("myTurn", "false")` (non-players only, so players see no change). Today `myTurn` is only ever set to `"true"` (`:133-136`, current player) and the three page JSPs default a null to `"false"` (`deadGo.jsp:30-31`, `finalGo.jsp:30-31`, `mobileGame.jsp:30-31`), so this is an explicit belt-and-braces guard now that a parent reaches private/hidden Go pages: their Submit / Accept-Reject buttons (`deadGo.jsp:260`, `finalGo.jsp:254`), hide checkbox (`deadGo.jsp:294`, `finalGo.jsp:279`) and `var active` (`deadGo.jsp:508`, `finalGo.jsp:496`) all key on `!"false".equals(myTurn)`. Set `REQUEST_ATTR` before the cancel/undo/Go/game forwards (one `setAttribute` right after computing it covers them all). Nothing else in `MoveServlet` changes.
  - `ViewGameServlet`: compute with the storer overload from `game.getPlayer1Data().getUserID()` / `getPlayer2Data().getUserID()`; private refusal only when not a player and not `parentViewer`; set `REQUEST_ATTR`.
  - `ViewGamesServlet`: `search(..., KidParentAccess.historyRequestorPid(meData, playerData), game, startSeq, 100)`. No change to `FastMySQLDSGGameLookup`.
  - JSPs read the attribute as `Boolean.TRUE.equals(request.getAttribute(org.pente.gameServer.kids.KidParentAccess.REQUEST_ATTR))` and OR it into the existing condition. The two app JSPs call the pure overload. In `mobile/json/game.jsp` the `KidInvitationText.hidden(...)` call stays; it already returns false for a non-kid viewer, so the parent gets every message.

- [ ] **Step 6: Run.** `TEST_ALL` passes. `./justCompile` → `BUILD SUCCESSFUL`. JSP and servlet behaviour is verified live in Task 9 (scenarios 5-8, 9a, 10a, 11-12, 14).

- [ ] **Step 7: Commit.** Add the helper, its test, `build.xml`, the three servlets and seven JSPs. `-m "Kids TB: parents of a player can read TB messages and see the kid's private games"`.

---

### Task 3: Parent write refusal audit (no code change expected)

**Files:**
- Read only: `J/turnBased/web/MoveServlet.java`, `ResignServlet.java`, `CancelServlet.java`, `CancelInvitationServlet.java`, `ReplyInvitationServlet.java`, `NewGameServlet.java`, `PrefServlet.java`, `LogServlet.java`; `H/tb/deadGo.jsp`, `H/tb/finalGo.jsp`, `H/tb/mobileGame.jsp` (their action links all go to these servlets).
- Modify: none, unless a row below turns out false. Then fix it in the servlet (test first where a unit seam exists, else a Task 9 scenario first), own commit.

**Interfaces:**
- Consumes: Task 2's `parentViewer` (only `load` uses it).
- Produces: the table below, confirmed line by line, pasted into the task report.

Every write and the existing guard that refuses a non-player (verified on `e13524a`):

| Write | Where | Guard that refuses a non-player parent |
|---|---|---|
| move, incl. message, `hide`, draw offer, renju/swap/Go dead-stone actions | `MoveServlet` `command=move` `:374-382` | `game.getCurrentPlayer() != playerData.getPlayerID()` → `Its not your turn.` (all sub-actions, `hide` at `:443-452`, run after it) |
| request undo | `:230-262` | `getOpponent(getCurrentPlayer()) != playerData` → error |
| accept / decline undo | `:283-289`, `:315-321` | `getCurrentPlayer() != playerData` → error |
| accept draw | `:346-352` | `getCurrentPlayer() != playerData` → error |
| resign | `ResignServlet` `command=resign` `:133-136` | non-current player → `resignGame(game, pid)` throws `Player ... is not in game` (`CacheTBStorer.java:2061-2063`) → `Database error, please try again later.` page, nothing stored |
| resign confirm, cancel confirm | `ResignServlet:115-131`, `CancelServlet:124-140` | read-only pages, no write |
| cancel request | `CancelServlet` `command=request` `:148-151` | not player1/player2 → `Invalid set.` |
| cancel reply Yes / No | `:177-180`, `:204-207` | not player1/player2 → `Invalid set.` |
| withdraw invitation | `CancelInvitationServlet:127` | not the inviter → error |
| accept / decline invitation | `ReplyInvitationServlet:106-117` | set must be not started; named invitee only. An **open** invitation (`invitee_pid` 0) can be accepted or declined by any logged-in player, including a parent: pre-existing, not a parent privilege, and not yet a game. Out of scope; report it. |
| page-level write controls (Submit, Accept/Reject, Pass, hide checkbox, active board) | `deadGo.jsp:236-294`, `finalGo.jsp:243-279`, `mobileGame.jsp:302-424`; `var active` `deadGo.jsp:508`, `finalGo.jsp:496`, `mobileGame.jsp:663` | every one keys on `!"false".equals(myTurn)`; `myTurn` is `"true"` only for the current player (`MoveServlet:133-136`), each JSP defaults null to `"false"` (`:30-31`), and Task 2 sets `"false"` explicitly for non-players. The undo/donor rows (`deadGo.jsp:264-268`, `finalGo.jsp:260-264`, `mobileGame.jsp:358-366`) also require `meData` to be player1/player2. Even if shown, the post is refused at `MoveServlet:381` (`Its not your turn.`). Live proof: Task 9 scenario 9a. |
| `PrefServlet`, `LogServlet` | params `name`/`value`, `message` | no game id; not game writes |

- [ ] **Step 1: Re-read each guard** on the branch (after Task 2) and confirm each row; note any difference.
- [ ] **Step 2: Confirm `MoveServlet` cancel/undo forwards** (`:163-171`, `:174-181`) still require a player, so a parent with a pending cancel request sees the normal page, not `cancelReply.jsp`.
- [ ] **Step 3: Report** the confirmed table. The live proof is Task 9 scenario 9.

---

### Task 4: One game-end hook in `CacheTBStorer`

**Files:**
- Create: `J/turnBased/TBGameEndListener.java`
- Modify: `J/turnBased/CacheTBStorer.java` (field near `kidChatPolicy` `:32`; setter beside `setKidChatPolicy` `:2375`; `EndGameRunnable.run` after `storeGameDSG(data, set)` `:936`; `cancelSet` `:1968-2006`)
- Test: `J/turnBased/test/CacheTBStorerGameEndTest.java`; add to `build.xml`

End paths enumerated (all on `e13524a`): every finished game goes through `EndGameRunnable.endGame` (win `:1794`, draw by rule `:1794`, agreed draw `:308`, timeout incl. renju timeout-draw `:750`, resign `:2049` and `:2068`, admin `fixTb.jsp` → `fixGame` `:1820`), which `run()` (`:908-952`) processes on the `TB-EndGame` thread. The only other end is cancel by agreement: `CancelServlet:189` → `cancelSet` with the set `STATE_ACTIVE` (`:1979-1990`). Every other `cancelSet` caller (`CancelInvitationServlet:145`, `ReplyInvitationServlet:345`, stale cleanup `:464`) handles a `STATE_NOT_STARTED` set (`:1991-1996`) and must not fire the hook.

**Interfaces:**
- Produces:
  - `public interface TBGameEndListener { void gameEnded(TBGame game); }` in `org.pente.turnBased`.
  - `CacheTBStorer.setGameEndListener(TBGameEndListener listener)` (field `volatile`, null = none).
  - Private `notifyGameEnded(TBGame game)`: calls the listener inside `try { } catch (Throwable t) { log4j.error("Game end listener failed for " + gid, t); }`.
  - Fired once per ended game: in `EndGameRunnable.run` right after `storeGameDSG(data, set)` with `data.game`; in `cancelSet`, for each game the `STATE_ACTIVE` branch moved from `STATE_ACTIVE` to `STATE_CANCEL`, after `baseStorer.cancelSet(set)` and the `evictSet` block. Never for a `STATE_NOT_STARTED` set. The hook does not filter "started"; the listener does (Task 5).

- [ ] **Step 1: Write failing tests** `CacheTBStorerGameEndTest`, setup copied from `CacheTBStorerKidTest.setUp/tearDown` (`J/turnBased/test/CacheTBStorerKidTest.java:41-58`) with a `RecordingBase extends InMemoryTBGameStorer` that counts `endGame` calls and releases a `CountDownLatch`. Fixtures: TB_PENTE games, players 1001/1002, state active, one or two moves; two-game sets via `new TBSet(sid, g1, g2)` with `setState(TBSet.STATE_ACTIVE)`. Listener records gids into a synchronized list and counts down its own latch. Wait at most 5 s on latches.
  - `testResignNotifiesOnce`: `cache.resignGame(game, 1001)`; listener gets exactly `[gid]` (wait for the latch, then 200 ms, size still 1).
  - `testFixGameNotifies`: `cache.fixGame(gid)` (admin path) notifies `[gid]`.
  - `testActiveSetCancelNotifiesEachActiveGame`: two active games in one set; `cancelSet(set)` notifies both gids synchronously (no wait needed), in game1, game2 order.
  - `testActiveSetCancelNotifiesOnlyActiveGames`: game1 state `STATE_COMPLETED`, game2 active; `cancelSet` notifies only game2's gid.
  - `testNotStartedCancelDoesNotNotify`: set `STATE_NOT_STARTED`; `cancelSet` gives no calls.
  - `testThrowingListenerDoesNotStopGameEnd`: listener throws `RuntimeException`; a `cancelSet` on an active set returns normally (no exception reaches the caller, as it would reach `CancelServlet:189`) and `loadSet(sid)` (or the base's stored set) has state `STATE_CANCEL`. No resign half: `EndGameRunnable.run` already catches `Throwable` (`CacheTBStorer.java:948`), so a resign case would pass without `notifyGameEnded`'s own try/catch and prove nothing. Say so in a one-line comment in the test.
  - `testNoListenerIsFine`: no listener set; resign completes (`endGame` count 1).

- [ ] **Step 2: Run.** `TEST_ONE org.pente.turnBased.test.CacheTBStorerGameEndTest`. Expected: `javac` error.
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run.** Expected `OK (7 tests)`; `TEST_ALL` passes (incl. `CacheTBStorerRedisTest`, `CacheTBStorerKidTest`).
- [ ] **Step 5: Commit.** `-m "TB: single game-end hook for ended and agreed-cancel games"`.

---

### Task 5: Transcript email at game end

**Files:**
- Create: `J/gameServer/kids/KidTbTranscript.java`
- Modify: `J/gameServer/kids/KidCopyEmails.java` (three new methods), `J/gameServer/kids/KidCopyMailer.java` (new method)
- Modify: `J/gameServer/server/DSGContextListener.java` (after `tbGameStorer.setKidChatPolicy(...)` `:296`)
- Modify: `J/turnBased/web/ResignServlet.java` (`:138-151`: store the resign message **before** `resignGame(game)`)
- Test: `J/gameServer/kids/test/KidTbTranscriptTest.java` (new, add to `build.xml`), `KidCopyEmailsTest.java`, `KidCopyMailerTest.java` (extend)

**Interfaces:**
- Consumes: `TBGameEndListener`, `CacheTBStorer.setGameEndListener` (Task 4); `KidSettings.isCopyTbMessages()` (Task 1).
- Produces:
  - `public final class KidTbTranscript`:
    - `public static boolean started(TBGame g)`: `g.getNumMoves() > opening` where `opening` is 1 for games that get the automatic centre stone on accept and 0 for `TB_DPENTE`, `TB_DKERYO`, `TB_GO`, `TB_GO9`, `TB_GO13`, `TB_SWAP2PENTE`, `TB_SWAP2KERYO` (the list at `CacheTBStorer.java:1906-1912`; comment the source).
    - `public static List<TBMessage> ordered(TBGame g)`: copy of `g.getMessages()` sorted by `moveNum`, then `seqNbr`, then date (null date first). Same order as `TBMessageThread.entries` (`J/turnBased/TBMessageThread.java:56-59`).
  - `KidCopyEmails` (exact copy, uses the existing private `format(Date)` and `KIDS_URL`):
    - `public static String tbSubject(String kidName, String opponentName, String gameName)` → `"Pente.org game messages: " + kidName + " vs " + opponentName + " (" + gameName + ")"`.
    - `public static String tbLine(int moveNum, Date when, String author, String text)` → `"Move " + moveNum + ", " + format(when) + ", " + author + ": " + text`; when `when == null`: `"Move " + moveNum + ", " + author + ": " + text`.
    - `public static String tbBody(String kidName, String p1Name, String p2Name, String gameName, long gid, List<String> lines)` →
      ```
      Turn-based game messages between <p1Name> and <p2Name> (<gameName>):

      <one line each>

      View the game: https://www.pente.org/gameServer/tb/game?gid=<gid>&command=load

      You get this copy because turn-based game message copies are on for <kidName>. Change it at https://www.pente.org/gameServer/myprofile/kids
      ```
      (lines joined with `\n`, no trailing newline after the footer).
  - `public void KidCopyMailer.copyTbTranscript(TBGame game)`: never throws (catch `Throwable`, log `"Kid TB transcript copy failed"`). Returns without mail when `game == null`, `!KidTbTranscript.started(game)`, or no messages. Loads both players; for each side that is a kid (`getParentPid() != null`) with `kids.getSettings(pid).isCopyTbMessages()`, collects the parent in a `LinkedHashSet` (first kid wins as `kidName` for that parent, as in `copyPrivateMessage`). For each `usableParent`: subject `tbSubject(kidName, opponentOfThatKid, GridStateFactory.getGameName(game.getGame()))`, body from `ordered(game)` where author is the name of the player whose pid matches (`p1`, `p2`, else `"Pente.org"`), then `mailer.mailText(...)` and `log4j.info("Kid copy queued: kid=" + kidPid + " parent=" + parentPid + " kind=tb gid=" + gid + " messages=" + n)`.
  - `DSGContextListener`: when `resources.getKidCopyMailer() != null`, `tbGameStorer.setGameEndListener(resources.getKidCopyMailer()::copyTbTranscript)`.
  - `ResignServlet` (current-player branch): build the message as today, then `storeNewMessage` **before** `resignGame(game)`, so the queued end game already carries it (also makes the existing "last message goes into the win notice" at `CacheTBStorer.java:1205-1209` reliable).

- [ ] **Step 1: Write failing tests.**
  - `KidTbTranscriptTest`: `testPenteStartsAfterCentre` (1 move → false, 2 → true); `testOpeningGamesStartOnFirstMove` (for each of the 7 ids: 0 moves → false, 1 → true); `testOrderedByMoveSeqThenDate` (messages at (3,1), (0,1), (3,2), (1,1) with dates come back (0,1), (1,1), (3,1), (3,2); two at (2,1) with dates 10:00 and 09:00 come back 09:00 first).
  - `KidCopyEmailsTest`: `testTbLineFormat` (`tbLine(12, <2026-10-19 14:03 UTC>, "graviton", "good luck")` equals `"Move 12, 2026-10-19 14:03 UTC, graviton: good luck"`), `testTbLineWithoutDate` (`"Move 12, graviton: good luck"`), `testTbSubject` (`"Pente.org game messages: kid vs graviton (Pente)"`), `testTbBodyExact` (two lines; whole string equals the template above for kid `kid`, players `kid`/`graviton`, game `Pente`, gid `50000000001234`).
  - `KidCopyMailerTest` (existing fixture: parent 1 `parent@example.invalid` valid, kid 100, kid2 101 same parent, stranger 200; add parent2 pid 2 `parent2@example.invalid` valid and kid3 300 with parent 2). Game helper: TB_PENTE, p1 = kid 100, p2 = stranger 200, moves `[180, 181]`, gid `50000000001234`, messages: (0,1) stranger `"hello from stranger"` (the open-invitation text a kid may not see), (1,1) kid `"hi"`, (2,1) stranger `"gg"`. Settings: kid 100 `new KidSettings(100, false, false, true)`.
    - `testTbTranscriptSent`: one mail in `sent`, to `parent@example.invalid`, subject `KidCopyEmails.tbSubject("kid", "stranger", GridStateFactory.getGameName(GridStateFactory.TB_PENTE))`; body contains `"stranger: hello from stranger"`, `"kid: hi"`, `"stranger: gg"` in that order, and `"gid=50000000001234&command=load"`.
    - `testTbTranscriptToggleOffNoMail`: settings `(100, true, true, false)` → nothing sent.
    - `testTbTranscriptUnstartedNoMail`: moves `[180]` only → nothing sent.
    - `testTbTranscriptNoMessagesNoMail`.
    - `testTbTranscriptReleasedKidNoMail`: kid 100 `setParentPid(null)` → nothing sent.
    - `testTbTranscriptBothKidsEachParent`: p2 = kid3 300 with copyTb on → two mails, one to each parent, each subject names that parent's kid first.
    - `testTbTranscriptSiblingsOneMail`: p2 = kid2 101, both toggles on → one mail.
    - `testTbTranscriptEmailDisabledNoMail`: `mailer(false)`.
    - `testTbTranscriptNeverThrows`: `players.failLoads = true` → no exception, nothing sent; also `copyTbTranscript(null)` does not throw.

- [ ] **Step 2: Run** the three `TEST_ONE`s (`KidTbTranscriptTest`, `KidCopyEmailsTest`, `KidCopyMailerTest`). Expected: `javac` errors.
- [ ] **Step 3: Implement** per Interfaces (helper, emails, mailer, context wiring, `ResignServlet` reorder).
- [ ] **Step 4: Run.** Expected `OK (3 tests)`, `OK (8 tests)` (4 + 4), `OK (22 tests)` (13 + 9); `TEST_ALL` passes.
- [ ] **Step 5: Deploy smoke.** `./justCompile`; then the last 200 lines of `dockerMain/logs/dsg/gameServer.log` show no new exception mentioning `Kid` or `listener`.
- [ ] **Step 6: Commit.** `-m "Kids TB: end-of-game transcript email behind copy_tb_messages"`.

---

### Task 6: Active games of a kid (`/gameServer/kids?action=activeGames`)

**Files:**
- Create: `J/gameServer/kids/KidActiveGames.java`
- Modify: `J/gameServer/kids/KidsServlet.java` (`init` `:78-99` add `tbGameStorer = resources.getTbGameStorer()`; `doGet` `:134-147`; `write` `:479-484` takes `Object`)
- Test: `J/gameServer/kids/test/KidActiveGamesTest.java`; add to `build.xml`

**Interfaces:**
- Produces:
  - `public final class KidActiveGames`:
    - `public static final class Entry { public final long gid; public final int game; public final String gameName; public final long opponentPid; public final String opponentName; public final boolean kidsTurn; public final long lastMoveDate; public final boolean privateGame; }` (field names are the JSON keys in the API contract; Gson serializes fields).
    - `public static List<Entry> build(long kidPid, List<TBSet> sets, DSGPlayerStorer players) throws DSGPlayerStoreException`: every non-null game of every set with `getState() == TBGame.STATE_ACTIVE` and the kid as player1 or player2; opponent via `game.getOpponent(kidPid)`, skipped when `loadPlayer` returns null; `privateGame` from `set.isPrivateGame()`; sort per the contract.
  - `KidsServlet` GET: when `"activeGames".equals(request.getParameter("action"))`: no login → 401 `LOGIN_REQUIRED`; `ownedKid(...)` (existing, 404 `KID_NOT_FOUND`); then `KidActiveGames.build(kid.getPlayerID(), tbGameStorer.loadSets(kid.getPlayerID()), players)`; reply `{success, kidPid, games}` from a new private `ActiveGamesReply` class; `TBStoreException`/`DSGPlayerStoreException` → 500 `DATABASE`. Any other or missing `action` → existing view. Log `Kids: activeGames by <name> kid=<pid>` via `logAction`.

- [ ] **Step 1: Write failing tests** `KidActiveGamesTest` (kid 100, stranger 200, other 300, `MapPlayerStorer` holding all three; games TB_PENTE with `setGid`, `setLastMoveDate`):
  - `testOnlyActiveGamesListed`: sets with an active game (p1 200, p2 100, one move), a completed game, and a not-started game → one entry.
  - `testEntryFields`: that entry has gid, `game == GridStateFactory.TB_PENTE`, `gameName == GridStateFactory.getGameName(TB_PENTE)`, `opponentPid == 200`, `opponentName == "stranger"`, `kidsTurn` true (one move: seat 2 to play, `TBGame.java:351`), `lastMoveDate` equals the fixture millis, `privateGame` true for a set with `setPrivateGame(true)`.
  - `testTwoGameSetListsBoth`: both games active → two entries.
  - `testSortedByLastMoveDesc`: three games at t1 < t2 < t3 → order t3, t2, t1.
  - `testMissingOpponentSkipped`: opponent pid 999 not in the storer → no entry, no exception.
  - `testOtherPlayersGamesIgnored`: a set between 200 and 300 in the list → not included.

- [ ] **Step 2: Run.** `TEST_ONE org.pente.gameServer.kids.test.KidActiveGamesTest`. Expected: `javac` error.
- [ ] **Step 3: Implement** helper and servlet action.
- [ ] **Step 4: Run.** Expected `OK (6 tests)`; `TEST_ALL` passes. Live checks in Task 9 scenario 4.
- [ ] **Step 5: Commit.** `-m "Kids TB: activeGames action on /gameServer/kids"`.

---

### Task 7: Website My Kids page: toggle and active games list

**Files:**
- Modify: `H/myprofileKids.jsp` (`kidBox` `:124-171`, settings checkboxes `:146-163`)

**Interfaces:**
- Consumes: `copyTbMessages` in GET/`settings` (Task 1); `GET /gameServer/kids?action=activeGames&kidPid=` (Task 6).

- [ ] **Step 1: Toggle.** Third checkbox `ct` checked from `!!k.copyTbMessages`, label text exactly `Turn-based game messages` (inserted the same way as `l1`/`l2`), its own `margin:8px 0` div after the live-chat block, plus a note div in the `LIVE_NOTE` style: `One email per game when it ends, with every message from both players.` `saveSettings` sends all three flags (`copyTbMessages: ct.checked ? "Y" : "N"`) and all three checkboxes listen to `change`.
- [ ] **Step 2: List.** In `kidBox`, before the Release button: `h4` `Active turn-based games` and a `ul`, then `fetch(URL + "?action=activeGames&kidPid=" + encodeURIComponent(k.kidPid), {credentials: "same-origin"})`. On `success`, one `li` per game: an `a` with `href` `/gameServer/tb/game?gid=<gid>&command=load` and text `<gameName> vs <opponentName>`, then text ` - ` + (`kidsTurn` ? `<kid name>'s turn` : `<opponentName>'s turn`) + (`lastMoveDate` > 0 ? `, last move ` + `new Date(lastMoveDate).toLocaleString()` : ``). Empty list: one `li` `No active turn-based games.` Failure: one `li` with the server's `error` or `Could not load games.` All text via `textContent` (existing `el` helper), never `innerHTML`.
- [ ] **Step 3: Verify.** Pages are mounted live. Run `/private/tmp/claude-501/kids-tb/e2e/page_check.py`: as iostest, GET `/gameServer/myprofile/kids` is 200 and contains `Turn-based game messages`, `Active turn-based games` and `action=activeGames`. Behaviour (list contents, toggle round trip) is covered by the JSON scenarios in Task 9; a visual check in a browser is optional and not a gate.
- [ ] **Step 4: Commit.** `-m "Kids TB: My Kids page toggle and active games list"`.

---

### Task 8: Full suite

- [ ] **Step 1:** `TEST_ALL`. Expected: every runner prints `OK (...)`, final `BUILD SUCCESSFUL`. New runners present: `KidParentAccessTest`, `KidTbTranscriptTest`, `KidActiveGamesTest`, `CacheTBStorerGameEndTest`.
- [ ] **Step 2:** `./justCompile` from the repo root. Expected `BUILD SUCCESSFUL` and the container restart line.

---

### Task 9: Local end-to-end verification

**Files:**
- Create (scratch, never committed): `/private/tmp/claude-501/kids-tb/e2e/kids_tb_e2e.py` (copy session helpers from `/private/tmp/claude-501/kids-plans/e2e/lib.py`; `BASE='https://localhost'`, unverified SSL context, `name2`/`password2` on each request).
- No repo changes. A failing expectation is first checked against the spec; a real defect is fixed in the task that owns the code (test first, own commit), then this task reruns from scenario 1.

**Interfaces:**
- Consumes: everything above, deployed locally. Accounts: parent `iostest`, opponent `graviton`, kid `kidstb1` and sibling kid `kidstb2` (both created by the script with `action=create` if absent from iostest's GET `kids`; reuse if present). Find pids, sids and gids with SQL (`DESCRIBE tb_set; DESCRIBE tb_game; DESCRIBE tb_message;` first to confirm column names). Log checks grep `dockerMain/logs/dsg/gameServer.log` for lines after a marker timestamp taken at the start of each scenario.
- Produces: one `PASS`/`FAIL` line per scenario with the command and the decisive output, for the controller.

Preconditions: SQL `select email, email_valid from dsg_player where name='iostest'` shows `email_valid = 'Y'` (needed for `usableParent`); record the email domain in the report. iostest whitelists `graviton` for `kidstb1` (`whitelistAdd`), so TB text between them is stored (kid rule from the 2026-10-19 feature). Message texts are single words without spaces or punctuation (e.g. `kidhello1`) so encoded pages still contain them verbatim.

- [ ] **Scenarios:**
  1. Settings: GET `/gameServer/kids` as iostest lists `kidstb1` with `copyTbMessages:false`. POST `settings kidPid=<kidstb1> copyTbMessages=Y` → `true`, other two flags unchanged. SQL `select copy_tb_messages from dsg_kid_settings where kid_pid=<kidstb1>` gives `Y`.
  2. Game A (private, kid moves first): as `kidstb1` POST `/gameServer/tb/newGame` inviting `graviton`, TB Pente, `privateGame=Y`, `inviterMessage=kidhello1`, `playAs` chosen so `kidstb1` is the first to move after the automatic centre stone (read `NewGameServlet.java:64-90` for exact param values). As graviton POST `/gameServer/tb/replyInvitation` `sid=<sid> command=Accept inviteeMessage=gravhello1`; SQL confirms the current player is `kidstb1`. Then exactly three `command=move` posts (`/gameServer/tb/game`, `gid`, `moves=<int>`), in this order: (a) `kidstb1` with `message=kidmove1`; (b) graviton with `message=gravmove1`; (c) `kidstb1` with **no** `message`. It is now graviton's turn, which scenario 10 needs: `ResignServlet:135-136` resigns a non-current player via `resignGame(game, pid)` and silently drops the message. SQL: `select count(*) from tb_message where gid=<A>` is 4.
  3. Game B (public, same players, toggle stays on) and Game C (two-game set if TB Pente is one, else another single game) set up the same way with one message each; leave them active.
  4. Active games: GET `/gameServer/kids?action=activeGames&kidPid=<kidstb1>` as iostest lists A, B (and C) with `opponentName:"graviton"`, `privateGame` true for A only, `kidsTurn` consistent with SQL. Same request as graviton → 404 `Kid not found`; as `kidstb1` → 404; with no credentials → 401 `Login required`.
  5. Parent reads, web: GET `/gameServer/tb/game?gid=<A>&command=load` as iostest → 200, page contains `kidhello1`, `gravhello1`, `kidmove1`, `gravmove1`.
     - Hidden sub-check (the separate `game.isHidden()` branch, `MoveServlet:139`): SQL `update tb_game set hiddenBy=1 where gid=<B>` (record the old value, normally 0). The set is cached in Redis (`CacheTBStorer.loadGame:1522-1533` reads the cache first), so then clear the TB cache: GET `/gameServer/admin/tb/clear.jsp` (`uncacheAll`, `CacheTBStorer.java:187`) as an admin account if one exists locally, else `redis-cli` `DEL` the keys `uncacheAll` invalidates in the `pente_cache` container and restart the `pente.org` container. GET `tb/game?gid=<B>&command=load` as iostest → 200 with B's message word; as `kidstb2` → `Invalid game, game hidden or private`. Restore: SQL `hiddenBy=<old>` and clear the cache the same way; as `kidstb2` B loads again (scenario 8 needs it).
  6. Non-parent refused, web: same as `kidstb2` (sibling, not a player) → page contains `Invalid game, game hidden or private`; none of the four words.
  7. Parent reads, apps: GET `/gameServer/mobile/json/game.jsp?gid=<A>` as iostest → JSON whose message field(s) contain all four words; as `kidstb2` → no message text. GET `/gameServer/mobile/game.jsp?gid=<A>` as iostest contains `kidmove1`.
  8. Public game B viewed by `kidstb2` (not a player, not a parent): the page loads and shows no message text (unchanged behaviour for non-parents).
  9. Writes refused (Task 3 table): as iostest against game A, in turn: `command=move` with `moves` and `message=parentmove`; `/gameServer/tb/resign command=resign gid=<A> message=parentresign`; `/gameServer/tb/cancel command=request sid=<A's sid> message=parentcancel`; `command=Yes` and `command=No`; `/gameServer/tb/game command=requestUndo`, `acceptUndo`, `declineUndo`, `acceptDraw`. Each returns an error page. After all: SQL shows game A state `A`, same move count, no `tb_message` row containing `parent`, set `cancel_pid` 0. Then, with kid having requested a cancel on game B (`command=request`), iostest loading B gets the normal game page (no `cancelReply` content such as the Yes/No form).
  9a. Go dead-stone page read-only for the parent: Game D, TB Go 9x9, `kidstb1` invites graviton (`inviterMessage=kidgo1`), graviton accepts. The two players each post one pass (`moves=<passMove>`, i.e. grid size squared, 81; confirm `passMove` in `TBGame.java` and `submitPass()` in `mobileGame.jsp`), graviton's with `message=gravgo1`, until `TBGame.getGoState()` is `GO_MARK_DEAD_STONES` (double pass at the end, `TBGame.java:106-115`; `MoveServlet:188-196` forwards to `deadGo.jsp`). GET `tb/game?gid=<D>&command=load` as iostest → 200, contains `kidgo1` and `gravgo1`, contains `var active = false`, and does **not** contain `<span>Submit</span>`. Control: the same GET as the player to move contains `<span>Submit</span>` (proves the check can fail). Leave D active.
  10. Transcript on resign: marker; SQL confirms graviton is A's current player (scenario 2 left it so); graviton POSTs `/gameServer/tb/resign command=resign gid=<A> message=gravresign1`. Within 10 s the log has exactly one `Kid copy queued: kid=<kidstb1> parent=<iostest> kind=tb gid=<A> messages=5`, and `select count(*) from tb_message where gid=<A>` is 5 (`kidhello1`, `gravhello1`, `kidmove1`, `gravmove1`, `gravresign1`; the third move carried no message). No exception lines mentioning `Kid` or `listener`.
  10a. The email's link works on the finished game: GET `/gameServer/tb/game?gid=<A>&command=load` as iostest → 200, contains `gravresign1` and `kidmove1`; as `kidstb2` → `Invalid game, game hidden or private`, neither word.
  11. History: GET `/gameServer/viewLiveGames?p=kidstb1&g=<TB Pente id>` as iostest → page contains game A's gid; as `kidstb2` → it does not. GET `/gameServer/viewLiveGame?g=<A>` as iostest → 200 with `kidmove1`; as `kidstb2` → `Game id invalid, please try again.`; same pair with `&mobile=`.
  12. Toggle off: POST `settings copyTbMessages=N`; marker; finish game B (resign by the player to move). Log has no `kind=tb gid=<B>` line. Then `copyTbMessages=Y` again.
  13. Cancel by agreement and not-started: marker; in game C (or a fresh started game) `kidstb1` requests cancel, graviton `command=Yes` → exactly one `kind=tb gid=<that gid>` line for each game of that set that had a move after the centre and at least one message, and none for the others. Then `kidstb1` invites graviton again with `inviterMessage=kidhello2` and graviton `command=Decline` → no `kind=tb` line for that set's gids.
  14. Release ends access: iostest POST `release kidPid=<kidstb2>` (cleanup, no effect on A). Then iostest POST `release kidPid=<kidstb1>`; GET `/gameServer/viewLiveGame?g=<A>` as iostest → `Game id invalid, please try again.` (private, iostest no longer a parent); GET `/gameServer/tb/game?gid=<A>&command=load` as iostest → `Invalid game, game hidden or private`, no `gravresign1`. Leave the two accounts as normal accounts.
- [ ] **Report** the evidence lines. Name any scenario that could not run and why.
- [ ] **Cleanup:** `git -C <repo> status --porcelain | grep '^??'` shows nothing created by this plan; delete `/private/tmp/claude-501/kids-tb/` scratch only after the report is accepted.

---

## Deployment order (production)

1. Apply `dsg_src/sql/2026-10-21-kid-tb-copy.sql` to the live **and** replica DBs (idempotent). Must precede the classes: the new `MySQLKidStorer` selects and writes `copy_tb_messages`.
2. Deploy the classes. They must be live before any changed JSP: the JSPs reference `org.pente.gameServer.kids.KidParentAccess`, and a JSP compiled before that class exists returns 500. Same order as the TB-opening-messages deploy.
3. Then deploy the JSPs.
4. Then release the apps (Android, iOS), which read `copyTbMessages` and call `action=activeGames`. Older apps keep working: they ignore the new field, and message display already follows the server.

## Self-review notes (writer)

- **Spec coverage:** Goal 1 viewing: Task 2 (all gates incl. private/hidden exemption, Go pages, finished-game pages, app endpoints), read-only: Task 3 + Task 9 scenario 9. Goal 2 active games: Task 6 (server), Task 7 (web); history of finished private games: Task 2 (`ViewGamesServlet`, `ViewGameServlet`). Goal 3 transcript: Tasks 1, 4, 5; toggle on web: Task 7. Data model: Task 1. Parent rule at request time and release: Task 2 tests + Task 9 scenario 14. Private **and hidden** exemption: Task 9 scenario 5 (hidden sub-check), finished-game link: scenario 10a, Go dead-stone page read-only: scenario 9a. Whole conversation incl. hidden invitation text: Task 2 (no filter for non-kid viewers) + `testTbTranscriptSent`. Triggers and non-triggers (win, draw, resign, timeout, admin, agreed cancel; not withdraw/decline/stale/not-started): Task 4. Toggle and link read at game end: listener reads settings and players when fired (Task 5). Never throws: Tasks 4-5. Apps: out of this plan, contract above.
- **Decisions this plan makes where the spec leaves a choice:** endpoint is a GET action on `/gameServer/kids` (reuses auth and `ownedKid`); helper lives in `org.pente.gameServer.kids.KidParentAccess` and reaches forwarded JSPs as a request attribute; history uses requestor substitution instead of new SQL; transcript wording above; equal timestamps ordered by `moveNum`, `seqNbr`, then date; `ResignServlet` stores the resign message before ending the game; a game whose opponent cannot be loaded is left out of `activeGames`; the parent exemption in `ViewGameServlet` and the requestor substitution in `ViewGamesServlet` also cover the kid's private **live** (non-TB) games, in the history list and in `viewLiveGame`. That is intended and consistent with spec Goal 2 ("finished private games in the kid's history" is not limited to TB), not a leak.
- **Out of scope, reported:** any player can accept or decline an **open** TB invitation (pre-existing); `mobile/json/game.jsp` has no private-game check for the board itself (pre-existing); `mobileGame.jsp` lacks the `KidInvitationText` filter for kid viewers (spec non-goal); a message sent with Go's final accept/continue (`MoveServlet:657-676`, the `GO_EVALUATE_DEAD_STONES` branch) is never stored, so it is in no view and no transcript (pre-existing); a resign message from the non-current player is dropped (`ResignServlet:135-136` calls `resignGame(game, pid)` without storing it; pre-existing). Both mean the transcript holds every **stored** message, not strictly every message typed.
