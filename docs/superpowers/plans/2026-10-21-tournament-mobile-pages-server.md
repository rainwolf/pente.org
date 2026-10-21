# Tournament Mobile Pages + Withdraw: Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App-only mobile layout for the tournament sign-up/status/help pages, plus a self-service "withdraw" while registration is open.

**Architecture:** `top.jsp` turns on a mobile shell (viewport tag, `body.app` class, `/res/app.css`) only when the page opted in AND the user agent is one of the apps. Overrides live in one scoped stylesheet, so desktop output is unchanged. Withdraw is a new JSP (`tournamentWithdraw.jsp`) that shows a confirm page on GET and removes the player on POST, using pure Java helpers for the decisions.

**Tech Stack:** Java 8+ / JSP on Tomcat, Ant (`build.xml`), JUnit 3 (`junit.framework.TestCase`, run via `ant test`), local docker stack (`./justCompile`), `curl -k`.

**Spec:** `pente.org/docs/superpowers/specs/2026-10-21-tournament-mobile-pages-design.md`

## Global Constraints

- App user agent tokens, exact: `penteLive-Android` (already sent, `penteLive-Android/.../WebViewActivity.java:109`) and `penteLive-iOS` (added by the iOS plan).
- Mobile shell active only when page opted in AND app user agent. Desktop browsers: no visible change except the withdraw button. Desktop HTML diff before/after may differ only by whitespace and added `class`/`id` attributes.
- Opt-in pages: `gameServer/tournaments/tournamentConfirm.jsp`, `tournamentSignup.jsp`, `tournamentWithdraw.jsp` (new), `status.jsp`, `statusRound.jsp`, `help/helpWindow.jsp`. No other page opts in.
- In the shell: site header (`#header`) and footer (`#footer`) hidden; help table of contents hidden.
- Test tournaments must be for a game `iostest` and `graviton` have rated games in, with valid email on the accounts (sign-up eligibility, `tournamentConfirm.jsp:77-153`), so "sign-up form shows again" checks don't fail for unrelated reasons.
- Registration open = tournament's eid is in `resources.getTourneyStorer().getUpcomingTournies()` AND `getSignupEndDate()` is after now. Never read a status field from `Tourney` (it has none).
- Removal only via `resources.getTourneyStorer().removePlayerFromTourney(pid, eid)` (the cache wrapper). Never `MySQLTourneyStorer` directly.
- No JavaScript `confirm()`/`alert()` anywhere in this feature (app web views have no JS dialog handlers).
- Withdraw POST requires same origin via existing `KidsServlet.sameOrigin(origin, referer, serverName)`; failure → HTTP 403.
- Commits: signed (`git commit -S`), no co-author or generated-by lines. Always `git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org`.
- Scratch output (HTML captures, cookie jars, scripts, screenshots) goes under `/private/tmp/claude-501/tourney-mobile/`, never in the repo.
- Production deploy is out of this plan (owner confirms it separately; server ships before the iOS release). Note for that deploy: `sync_gameServer.sh` ships only `gameServer/` and classes, but `top.jsp`, `help/` and `res/` are baked into the image (`Dockerfile:26`), so the mobile shell needs an image rebuild (`build_and_deploy.sh pente.org`). Classes before JSPs (`tournamentConfirm.jsp` references `TourneyWithdraw`).
- JUnit is 3.7 (`dsg_src/lib/junit-3.7.jar`): every test class needs `public XTest(String name) { super(name); }` (see `kids/test/KidsServletOriginTest.java:9-11`).
- Redis runs in the separate `pente_cache` container: `./justCompile` does not clear it. After any SQL change to tournament data, load `/gameServer/admin/flushTourneyCache.jsp` (calls `CacheTourneyStorer.flushCache()`) or `docker compose restart pente_cache`.
- `curl` is blocked in plain Bash by a hook: run HTTP checks inside `ctx_execute` (language `shell`, `curl -k …`) or a Node/Playwright script there.
- Local stack: `https://localhost` (self-signed, use `curl -k`). `./justCompile` compiles Java and restarts the container; JSP edits under `dsg_src/httpdocs/gameServer` are live. `top.jsp`, `help/` and `res/` are NOT mounted live (`docker-compose.yml:39-41` mounts only `join.jsp`, `gameServer/` and classes; `Dockerfile:26` copies the rest at build time). Deploy them locally with `docker compose cp dsg_src/httpdocs/top.jsp pente.org:/usr/local/tomcat/webapps/ROOT/top.jsp` (same for `help/helpWindow.jsp`, `res/app.css`); Jasper recompiles includers. Do not add new mounts to `docker-compose.yml`. Test accounts: `iostest` / `app_tsetsoi`, `graviton` / `virginia` (never commit). Login: POST `name2`, `password2` to `/gameServer/index.jsp` with a cookie jar.

## Review Focus

1. A desktop page that includes `top.jsp` but is not opted in (`/index.jsp`, `/join.jsp`, `/gameServer/index.jsp`) opened from the app must stay desktop → Task 2 checks it with the app user agent.
2. Withdraw clicked twice / back + resubmit → second POST shows "not signed up", no exception → Task 4 E2E step.
3. Withdraw after the signup end date passes while the confirm page is open → POST rejected with the closed message → Task 4 E2E step (closed tournament).
4. Non-numeric or unknown `eid` on the withdraw page → "Tournament not found." page, not a 500 → Task 4 E2E step.
5. Cancelled tournament (status `'S'`, not in upcoming list) with a future signup end date → no button, POST rejected → Task 3 unit test `testCancelledTourneyIsClosed`.

---

### Task 1: Branch and baseline captures

**Files:** none in repo. Scratch: `/private/tmp/claude-501/tourney-mobile/baseline/`.

- [ ] **Step 1: Create the branch**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org checkout -b tournament-mobile-pages
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add docs/superpowers/specs/2026-10-21-tournament-mobile-pages-design.md docs/superpowers/plans/2026-10-21-tournament-mobile-pages-server.md
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "docs: tournament mobile pages + withdraw design and server plan"
```

- [ ] **Step 2: Make sure local test data exists**

Need, on the local DB: (a) one tournament with registration open (signup end date in the future), (b) one whose signup end date has passed but has not started, (c) one started tournament with at least one round, (d) one cancelled (`dsg_tournament_detail.status='S'`) with a future signup end date. Check `/gameServer/tournaments/index.jsp` on `https://localhost`. If missing, create them (admin page `gameServer/admin/newTourney.jsp`, or SQL into MariaDB `127.0.0.1:3316`, tables `game_event` + `dsg_tournament` + `dsg_tournament_detail` — see `MySQLTourneyStorer.java:564-567` — then flush the tourney cache, see Global Constraints). Register `graviton` for (b) BEFORE moving its signup end date into the past, via POST `tournamentSignup.jsp` (it has no open check and keeps Redis consistent), and likewise register `graviton` for (d). Record the eids in `/private/tmp/claude-501/tourney-mobile/eids.txt` as `OPEN=`, `CLOSED=`, `STARTED=`, `ROUND=`, `CANCELLED=`.

- [ ] **Step 3: Capture desktop baseline HTML before any code change**

Logged in as `iostest` with the default curl user agent, save to `baseline/`: `tournamentConfirm.jsp?eid=$OPEN`, `status.jsp?eid=$CLOSED`, `statusRound.jsp?eid=$STARTED&round=$ROUND`, `/help/helpWindow.jsp?file=tourneySwiss`, `/help/helpWindow.jsp?file=tournaments`, `/index.jsp`, `/join.jsp`, `/gameServer/index.jsp`. Also capture the same `tournamentConfirm.jsp?eid=$OPEN` after signing `iostest` up (POST `tournamentSignup.jsp` with `eid`, `rules=on`) as `baseline/confirm-registered.html`, then leave `iostest` registered.

Expected: 9 non-empty HTML files, none containing `five00` / exception text.

---

### Task 2: Mobile shell

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/server/AppClient.java`
- Create: `dsg_src/java/org/pente/gameServer/server/test/AppClientTest.java`
- Create: `dsg_src/httpdocs/res/app.css`
- Modify: `build.xml` (add `AppClientTest` to the `test` target, same form as `KidsServletOriginTest` at :113)
- Modify: `dsg_src/httpdocs/top.jsp` (head + `<body>`)
- Modify: `dsg_src/httpdocs/help/helpWindow.jsp` (opt-in line before `top.jsp` include at :30; `class="helptoc"` on the TOC `<td>` at :43)
- Modify (opt-in line only): `gameServer/tournaments/tournamentConfirm.jsp`, `tournamentSignup.jsp`, `status.jsp`, `statusRound.jsp`

**Interfaces:**
- Produces: `public static boolean AppClient.isApp(String userAgent)`; page attribute `"mobileShell"` (`Boolean.TRUE`) that a page sets via `pageContext.setAttribute("mobileShell", Boolean.TRUE)` before including `begin.jsp`/`top.jsp`; body class `app`.

- [ ] **Step 1: Write the failing test** `AppClientTest extends junit.framework.TestCase` (with the `String name` constructor)

```java
public void testAndroidAppAgent() { assertTrue(AppClient.isApp("Mozilla/5.0 (Linux; Android 14) ... Chrome/130 Mobile Safari/537.36 penteLive-Android")); }
public void testIosAppAgent()     { assertTrue(AppClient.isApp("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 penteLive-iOS")); }
public void testDesktopAgent()    { assertFalse(AppClient.isApp("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15")); }
public void testMobileSafariIsNotApp() { assertFalse(AppClient.isApp("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Version/18.0 Mobile/15E148 Safari/604.1")); }
public void testNull()            { assertFalse(AppClient.isApp(null)); }
```

- [ ] **Step 2: Run, expect FAIL** — `ant test` from the repo root → compile error, `AppClient` not found.

- [ ] **Step 3: Implement `AppClient.isApp`** — true iff non-null and contains `penteLive-Android` or `penteLive-iOS` (case-sensitive). Javadoc one line.

- [ ] **Step 4: Run, expect PASS** — `ant test` → `OK` for `AppClientTest`, all other suites still `OK`.

- [ ] **Step 5: Wire `top.jsp`.** After the existing attribute reads (`top.jsp:16-23`) compute `boolean appShell = Boolean.TRUE.equals(pageContext.getAttribute("mobileShell")) && AppClient.isApp(request.getHeader("User-Agent"));` When `appShell`, also `pageContext.setAttribute("pageWidth", "100%")` so `begin.jsp:104-121` renders `.pagebody` and `#main` at 100% natively (`begin.jsp` includes `top.jsp` at :81, before it reads `pageWidth`). When `appShell`: emit `<meta name="viewport" content="width=device-width, initial-scale=1">` (leave the commented line at :36 alone) and `<link href="/res/app.css" media="all" rel="Stylesheet" type="text/css">` after `hack.css` (:75); body tag becomes `<body class="app">`. When not: output identical to today (`<body>`).

- [ ] **Step 6: Write `res/app.css`.** Every rule scoped under `body.app`. Must achieve: `#header`, `#footer`, `.helptoc` and its spacer cell hidden; `#wrapper`, `#header`-adjacent and other remaining fixed widths (950 px `#wrapper` in `style2.css`, the 800 px help table, and the `width="400|480|500"` tables in `status.jsp:19`, `statusSwiss.jsp:13`, `statusRoundRobin.jsp:32`) fit the viewport; wide data tables scroll horizontally inside themselves rather than the page; body text ≥ 15 px; buttons and checkboxes ≥ 44 px tap height; side padding 12 px. No change to `style2.css`.

- [ ] **Step 7: Opt in the pages.** Add `pageContext.setAttribute("mobileShell", Boolean.TRUE);` in the existing scriptlet that sets `title`, before the `begin.jsp`/`top.jsp` include, in each of: `tournamentConfirm.jsp`, `tournamentSignup.jsp`, `status.jsp`, `statusRound.jsp`, `help/helpWindow.jsp`. Add `class="helptoc"` to the TOC `<td>` in `helpWindow.jsp`.

- [ ] **Step 8: Deploy and layout check.** `./justCompile`, then `docker compose cp` `top.jsp`, `help/helpWindow.jsp`, `res/app.css` (Global Constraints). Write a throwaway Playwright script in scratch (`ignoreHTTPSErrors: true`, `isMobile: true`, `hasTouch: true` — without `isMobile` Chromium ignores the viewport tag; log in via the form POST first or reuse the curl cookie) that, for each opted-in URL from Task 1 Step 3, with user agent ending `penteLive-Android` and with one ending `penteLive-iOS`, at viewport widths 360 and 390, asserts `document.documentElement.scrollWidth <= window.innerWidth` and `getComputedStyle(document.querySelector('#header')).display === 'none'`, and saves a screenshot. Expected: all pass; screenshots readable without zoom (look at them).

- [ ] **Step 9: Non-opted-in pages stay desktop in the app.** With the Android app user agent, fetch `/index.jsp`, `/join.jsp`, `/gameServer/index.jsp`. Expected: no `body class="app"`, no `viewport` meta, no `app.css`.

- [ ] **Step 10: Desktop diff.** Re-fetch every Task 1 baseline URL with the default user agent and `diff -wB` against `baseline/`. Expected: only `class="helptoc"` in the two help pages differs (plus volatile content such as timestamps/online counts, which you name in your report).

- [ ] **Step 11: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add build.xml dsg_src/java/org/pente/gameServer/server/AppClient.java dsg_src/java/org/pente/gameServer/server/test/AppClientTest.java dsg_src/httpdocs/res/app.css dsg_src/httpdocs/top.jsp dsg_src/httpdocs/help/helpWindow.jsp dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp dsg_src/httpdocs/gameServer/tournaments/tournamentSignup.jsp dsg_src/httpdocs/gameServer/tournaments/status.jsp dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Mobile layout for tournament and help pages opened from the apps"
```

---

### Task 3: Withdraw rules (pure Java)

**Files:**
- Create: `dsg_src/java/org/pente/gameServer/tourney/TourneyWithdraw.java`
- Create: `dsg_src/java/org/pente/gameServer/tourney/test/TourneyWithdrawTest.java`
- Modify: `build.xml` (add `TourneyWithdrawTest` to `test`)

**Interfaces:**
- Produces:
  - `public static boolean TourneyWithdraw.isRegistrationOpen(Tourney t, List<Tourney> upcoming, Date now)` — false if `t`, `upcoming` or `t.getSignupEndDate()` is null; true iff some element of `upcoming` has `getEventID() == t.getEventID()` and `t.getSignupEndDate().after(now)`.
  - `public enum TourneyWithdraw.Result { OK, NOT_LOGGED_IN, NO_TOURNEY, REGISTRATION_CLOSED, NOT_REGISTERED }`
  - `public static Result TourneyWithdraw.check(boolean loggedIn, Tourney t, boolean registrationOpen, boolean registered)` — checks in that order, first failure wins.
  - `public static String TourneyWithdraw.message(Result r, String tourneyName)` — exact copy below.

| Result | message |
|---|---|
| `OK` | `You have withdrawn from <name>. You can sign up again while registration is open.` |
| `NOT_LOGGED_IN` | `You must be logged in to withdraw from a tournament.` |
| `NO_TOURNEY` | `Tournament not found.` |
| `REGISTRATION_CLOSED` | `Registration for <name> is closed, so you can no longer withdraw.` |
| `NOT_REGISTERED` | `You are not signed up for <name>.` |

`<name>` is the raw tournament name; the JSP escapes nothing else today, so keep that consistent (tournament names are admin-entered).

- [ ] **Step 1: Write the failing tests** (class has the `String name` constructor). Build `Tourney` objects with the existing setters (`setEventID`, `setName`, `setSignupEndDate`). Tests:
  - `testOpenWhenUpcomingAndFutureEnd` → `isRegistrationOpen(t, [t], now)` true.
  - `testCancelledTourneyIsClosed` → end date tomorrow, `upcoming` empty → false.
  - `testPastEndIsClosed` → in upcoming list, end date yesterday → false.
  - `testNullTourneyIsClosed` → false; also `t` with null signup end date → false.
  - `testCheckOrder` → `check(false, null, false, false) == NOT_LOGGED_IN`; `check(true, null, false, false) == NO_TOURNEY`; `check(true, t, false, false) == REGISTRATION_CLOSED`; `check(true, t, true, false) == NOT_REGISTERED`; `check(true, t, true, true) == OK`.
  - `testMessages` → `message(REGISTRATION_CLOSED, "Swiss Cup")` equals `Registration for Swiss Cup is closed, so you can no longer withdraw.`; same exact-string check for `OK` and `NOT_REGISTERED`.

- [ ] **Step 2: Run, expect FAIL** — `ant test` → `TourneyWithdraw` not found.
- [ ] **Step 3: Implement** the four members above in `TourneyWithdraw` (final class, private constructor).
- [ ] **Step 4: Run, expect PASS** — `ant test` → all suites `OK`.
- [ ] **Step 5: Commit** — `git -C … add` the three files; `git -C … commit -S -m "Tournament withdraw rules"`.

---

### Task 4: Withdraw page and button

**Files:**
- Create: `dsg_src/httpdocs/gameServer/tournaments/tournamentWithdraw.jsp`
- Modify: `dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:62-63` (registered branch)
- Modify: `dsg_src/java/org/pente/gameServer/client/web/SimpleLoginAccessController.java:17` (add `"gameServer/tournaments/tournamentWithdraw.jsp"` after the signup entry)

**Interfaces:**
- Consumes: `TourneyWithdraw.isRegistrationOpen/check/message/Result` (Task 3); `KidsServlet.sameOrigin(String origin, String referer, String serverName)`; `"mobileShell"` opt-in (Task 2).

- [ ] **Step 1: Button on the confirm page.** In the registered branch, after "You are currently signed up for this tournament.", when `TourneyWithdraw.isRegistrationOpen(tourney, resources.getTourneyStorer().getUpcomingTournies(), new Date())`: a `<form method="get" action="tournamentWithdraw.jsp">` with hidden `eid` and a submit button labelled `Withdraw from this tournament`. Nothing shown when closed.

- [ ] **Step 2: `tournamentWithdraw.jsp`.** Structure mirrors `tournamentSignup.jsp` (reads `name` request attribute and `dsgPlayerStorer` before including `../begin.jsp`, sets `title` and `mobileShell`). All decisions, `response.setStatus(403)` and the removal happen in the scriptlet BEFORE the `begin.jsp` include (otherwise the response may already be committed); the markup after the include only renders the outcome. Import `org.pente.gameServer.kids.KidsServlet` and `org.pente.gameServer.tourney.*` (not in `begin.jsp:1-16`). Flow:
  - Only call `dsgPlayerStorer.loadPlayer(name)` when `name != null`.
  - Parse `eid`; non-numeric or `getTourneyDetails` returns null → `NO_TOURNEY`.
  - `registered` = `getTourneyPlayerPids(eid).contains(Long.valueOf(pid))` (`pid` is a `long`).
  - Compute `Result` with `TourneyWithdraw.check(...)`.
  - GET and `Result.OK`: heading `Withdraw from <name>?`, text `You can sign up again while registration is open.`, a `<form method="post" action="tournamentWithdraw.jsp">` with hidden `eid`, submit `Withdraw`, and a `Cancel` link to `tournamentConfirm.jsp?eid=<eid>`. No removal on GET.
  - POST: first, if `!KidsServlet.sameOrigin(request.getHeader("Origin"), request.getHeader("Referer"), request.getServerName())` → `response.setStatus(403)` and show `This request could not be verified. Please go back and try again.` Otherwise, if `Result.OK` → `removePlayerFromTourney(pid, eid)`. Show `TourneyWithdraw.message(result, name)`.
  - Any non-OK result on GET or POST shows `message(...)`.
  - Every result page ends with a `Back` link to `tournamentConfirm.jsp?eid=<eid>` (omit when `NO_TOURNEY`).

- [ ] **Step 3: Deploy** — `./justCompile` (the Java change in `SimpleLoginAccessController`).

- [ ] **Step 4: E2E with curl** (cookie jar for `iostest`, still registered for `$OPEN` from Task 1). Each line is one check; all must hold:
  - GET `tournamentConfirm.jsp?eid=$OPEN` → contains `Withdraw from this tournament`.
  - GET `tournamentConfirm.jsp?eid=$CLOSED` as `graviton` (registered in Task 1) → no `Withdraw from this tournament`.
  - GET `tournamentConfirm.jsp?eid=$CANCELLED` as `graviton` → no `Withdraw from this tournament`; POST `tournamentWithdraw.jsp` (`eid=$CANCELLED`, same Origin) → `is closed, so you can no longer withdraw`.
  - GET `tournamentWithdraw.jsp?eid=$OPEN` → contains `Withdraw from` and a POST form; player still listed on the confirm page.
  - POST with `-H 'Origin: https://evil.example'` → status 403, player still listed.
  - POST with no Origin and no Referer → 403.
  - POST with `-H 'Origin: https://localhost'` → `You have withdrawn from`; confirm page no longer lists `iostest` and shows the sign-up form again.
  - Same POST again → `You are not signed up for`, status 200, no stack trace.
  - POST for `$CLOSED` as `graviton` → `is closed, so you can no longer withdraw`, still registered.
  - GET `tournamentWithdraw.jsp?eid=abc` and `?eid=999999` → `Tournament not found.`, no `five00`/exception.
  - Without a session cookie GET `tournamentWithdraw.jsp?eid=$OPEN` → redirected/forwarded to the login page.
  - `docker compose logs pente.org` (or the container's log) shows the main-room drop event without errors.
  - Sign `iostest` up again via `tournamentSignup.jsp` → succeeds (re-signup works after withdraw).

- [ ] **Step 5: Re-run Task 2 Step 8 for `tournamentWithdraw.jsp`** (confirm and result pages) and Task 2 Step 10's diff for `confirm-registered.html` (expected extra difference: the withdraw form only).

- [ ] **Step 6: Commit**

```bash
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org add dsg_src/httpdocs/gameServer/tournaments/tournamentWithdraw.jsp dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp dsg_src/java/org/pente/gameServer/client/web/SimpleLoginAccessController.java
git -C /Users/waliedothman/mariposa/coding/pente.org-project/pente.org commit -S -m "Let players withdraw from a tournament while registration is open"
```

---

### Task 5: Android emulator check (no Android code change)

**Files:** none committed. `PentePlayer.development=true` locally only to reach the local backend (never commit; revert after).

- [ ] **Step 1:** Build and run penteLive-Android on an emulator (JDK 17+: Android Studio JBR) against the local backend, logged in as `graviton`.
- [ ] **Step 2:** Tap the `$OPEN` tournament → mobile layout (no site header, fits width). Sign up, withdraw (confirm page → Withdraw → result), sign up again. Tap the format link (e.g. Swiss rules) → mobile help page with no TOC. Back gesture returns through history to the dashboard.
- [ ] **Step 3:** Tap the `$CLOSED` and `$STARTED` tournaments → mobile status pages, no page-wide horizontal scroll.
- [ ] **Step 4:** Save screenshots (`adb exec-out screencap -p`) to scratch; revert the `development` flag; `git -C …/penteLive-Android status --porcelain` shows no change from this task.
