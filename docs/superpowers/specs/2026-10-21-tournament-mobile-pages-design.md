# Tournament pages in the apps: mobile layout + withdraw button

Status: draft for owner review (2026-10-21).
Repos: pente.org (server, most of the work), penteLive-iOS (one small change), penteLive-Android (no change expected).

## 1. Why

The iOS and Android apps open three server pages inside an in-app web view when a player taps a tournament on the dashboard:

| Dashboard state | Page | Opened by |
|---|---|---|
| 1, registration open | `gameServer/tournaments/tournamentConfirm.jsp?eid=` (sign-up) | iOS `GamesTableViewController.m:2286-2297`, Android `MainActivity.java:122` |
| 2, registration closed, not started | `gameServer/tournaments/status.jsp?eid=` | iOS `:2269-2283`, Android `:118` |
| 3, started | `gameServer/tournaments/statusRound.jsp?eid=&round=` | iOS `:2301-2321`, Android `:126-127` |

All three pages link to the format rules page `help/helpWindow.jsp?file=tourney<Format>` (`tourneyDetails.jsp:21`) and the official rules `file=tournaments` (`tourneyDetails.jsp:27`, `tournamentConfirm.jsp:167`).

These are desktop pages: a ~950 px (pixel) fixed layout (`res/style2.css` `#wrapper` 950px, `#header` 930px, `begin.jsp:121` `.pagebody` 930px, `helpWindow.jsp:41` 800px table), no viewport tag (`top.jsp:36` is commented out), and a site header/tab bar/footer. On a phone they render zoomed out and hard to use.

Separately, a player who signed up has no way to leave a tournament. Only admins can drop players (`gameServer/admin/manageTourney.jsp:78-83`).

## 2. Goals

- G1. When opened from either app, the sign-up, tournament status, round status, and tournament help/rules pages fit the phone width and are readable without zooming.
- G2. Web (desktop browser) users see exactly what they see today, apart from the new withdraw button.
- G3. A signed-up player can remove themselves from a tournament while registration is open, from the same sign-up page, on web and in both apps.
- G4. One copy of each page and of the rules text. No native re-implementation.

## 3. Non-goals

- Making the whole site responsive. 75 pages include `begin.jsp`; only the pages listed in G1 opt in.
- Native sign-up screens.
- Changing how iOS authenticates its web views (`name2`/`password2` in the URL query). See section 9.
- Hardening the existing sign-up endpoint (it has no server-side registration-open, eligibility, or duplicate check). See section 9.
- A per-player "registered" flag on the app dashboard. The dashboard tournament list is global (`IndexResponse.java:649-669`), so withdrawing does not change the row and no app change is needed for it.

## 4. Anti-goals (would count as failure even if it works)

- Any visible change to a page in a desktop browser other than the withdraw button.
- A mobile layout that leaks into app pages that did not opt in (profile, prefs, stairs, live games) and breaks them.
- Two code paths for the same page (a "mobile copy" of a JSP that drifts from the original).
- A withdraw that can be triggered by another site (cross-site request forgery, CSRF) or after registration closes.

## 5. Part A: app-only mobile shell

### 5.1 Detecting "opened from the app"

Decision (my call): detect by user agent, not by a URL flag or session flag.

- Android already appends ` penteLive-Android` to the web view user agent (`WebViewActivity.java:109`), and the server already tests for it (`profile.jsp:171`). Installed Android builds therefore get the mobile layout as soon as the server ships.
- iOS sets no user agent (`PenteWebViewController.swift:20` uses a default `WKWebViewConfiguration`). The iOS change is small: the web view is a stored-property initializer, so build a `WKWebViewConfiguration` first and set `applicationNameForUserAgent` so the user agent ends with `penteLive-iOS`. Mobile layout reaches iOS users with the next app release.
- Why not a URL flag stored in the session: it needs changes at every app call site, old builds never send it, and it depends on a guest session existing. A user agent is sent on every request in the web view, including in-page links like the format rules page, so no propagation is needed.

One server helper decides it, e.g. a static method or a small `.jspf` that sets a request attribute `isApp` when the user agent contains `penteLive-Android` or `penteLive-iOS`. `profile.jsp:171` keeps its own check (out of scope).

### 5.2 Opt-in per page

The mobile shell is active only when `isApp` is true AND the page opted in. A page opts in by setting a page attribute before including the wrapper (same mechanism as the existing `pageWidth` / `title` attributes read by `begin.jsp:104-107` and `top.jsp:16-23`).

Opt-in set:
- `gameServer/tournaments/tournamentConfirm.jsp`
- `gameServer/tournaments/tournamentSignup.jsp` (sign-up result page)
- the new withdraw page(s) from Part B
- `gameServer/tournaments/status.jsp`
- `gameServer/tournaments/statusRound.jsp` (its per-format fragments `statusSwiss/RoundRobin/SingleElim/DoubleElim.jsp` are included by it, `statusRound.jsp:75-82`)
- `help/helpWindow.jsp` (covers every help fragment, including the format pages, official rules, privacy policy and EULA pages the apps already open)

### 5.3 What the shell changes (only when active)

- `top.jsp`: emit `<meta name="viewport" content="width=device-width, initial-scale=1">`, add a body class (e.g. `app`), and load one new small stylesheet (e.g. `/res/app.css`). Hide the site header (logo, search, tab bar). The apps provide their own navigation: iOS nav bar with back/forward (`PenteWebViewController.swift:85-115`), Android system back through web view history (`WebViewActivity.java:121-135`). Decision (my call): hide the header and footer rather than restyle them; site tabs inside the app lead to desktop pages that did not opt in.
- `bottom.jsp`: hide the footer.
- `gameServer/begin.jsp`: page width becomes 100% (the existing `pageWidth` attribute path already supports a `%` value, `begin.jsp:104-119`).
- `help/helpWindow.jsp`: drop the 800 px table width and hide the help table of contents column; the fragment fills the width.
- `app.css`: all overrides scoped under the body class, so desktop CSS is untouched. Fixed-width tables in the opted-in pages (`status.jsp:19` 480, `statusSwiss.jsp:13` 400, `statusRoundRobin.jsp:32` 500, and whatever `statusSingleElim/DoubleElim.jsp` contain) must not force page-wide horizontal scroll. Wide data tables (standings, player lists, brackets) may scroll horizontally inside their own container.
- Readable text and tap targets: base font size and form controls (rules checkbox, buttons) sized for touch.

Acceptance for Part A: at 360 px wide (small Android phone) and 390 px (iPhone), each opted-in page has no page-level horizontal scroll and body text is readable without zoom. In a desktop browser each page renders the same as before the change except the withdraw button (check by diffing desktop-user-agent HTML before vs after the change; whitespace-only differences from new scriptlet lines and added `class` attributes with no desktop styling pass).

### 5.4 Things the builder decides

Exact CSS, how the body class is threaded into `top.jsp`, helper name and location, whether `rightAd.jsp` (renders nothing today, `rightAd.jsp:1-10`) is skipped.

## 6. Part B: withdraw from a tournament

### 6.1 Behaviour

- On `tournamentConfirm.jsp`, in the "You are currently signed up for this tournament" branch (`tournamentConfirm.jsp:62-63`), show a "Withdraw from this tournament" button, only while registration is open.
- "Registration open" means what the server already uses for the upcoming list: `signupEndDate` in the future and status not `'S'` (cancelled) (`MySQLTourneyStorer.java:71`, `CacheTourneyStorer.java:198`). The `Tourney` object does not carry the status (`getTourneyDetails`, `MySQLTourneyStorer.java:553+`, does not select it), and cancelling drops the eid from the upcoming list (`CacheTourneyStorer.java:840-853`). So implement the check as: eid is in `getUpcomingTournies()` AND `signupEndDate` is in the future. One shared helper, used by both the button display and the withdraw endpoint.
- Tapping it leads to a server-rendered confirmation step ("Withdraw from <tournament>? You can sign up again while registration is open.") with a confirm button that POSTs. Decision (my call): no JavaScript `confirm()` dialog. Neither app's web view implements JavaScript dialogs (Android sets no `WebChromeClient`, iOS has no `WKUIDelegate` confirm handler), so `confirm()` would silently return false in the apps.
- After withdrawing: a result page "You have withdrawn from <tournament>" with a link back to the sign-up page, where the player can sign up again.
- Works the same for web and app users.

### 6.2 Server

- New POST endpoint, following the existing sign-up pattern (`tournamentSignup.jsp`), e.g. `gameServer/tournaments/tournamentWithdraw.jsp`, added to the login-required list in `SimpleLoginAccessController.java:5-30` (exact path suffix, matched by `endsWith`, `:39-43`).
- Server-side checks, in order, each with a plain message on failure: logged in; tournament exists; registration open (as defined above); player is currently registered.
- CSRF: reject the POST unless `Origin` (or `Referer` when `Origin` is absent) is the site's own host. Reuse or mirror the check in `KidsServlet.java:119-165`. Web view form posts from the page itself pass this.
- Removal uses the existing `resources.getTourneyStorer().removePlayerFromTourney(pid, eid)` (`CacheTourneyStorer.java:368-378`), which already deletes the row, updates the Redis (in-memory cache) pid list, and fires `TourneyEvent.PLAYER_DROP`. Do not call `MySQLTourneyStorer` directly (Redis would go stale).
- Main-room broadcast: `PLAYER_DROP` currently prints "<name> has been dropped from the tournament." (`ServerMainRoom.java:331-336`). Decision (my call): leave it unchanged, no new event type.
- Double submit (back + resubmit) lands on "not registered" and is harmless.

### 6.3 Apps

No app change needed for Part B: both apps already open `tournamentConfirm.jsp` for state 1, and form posts stay in the web view (Android `shouldOverrideUrlLoading` returns false, iOS `decidePolicyFor` allows). The dashboard row does not change after withdrawing (global list), so the missing iOS refresh-on-return does not matter here.

## 7. Change list by repo

- pente.org: shell helper + `top.jsp`, `bottom.jsp`, `begin.jsp`, `helpWindow.jsp`, new `res/app.css`, opt-in lines in the six pages, withdraw button in `tournamentConfirm.jsp`, new withdraw confirm/POST page(s), `SimpleLoginAccessController` entry.
- penteLive-iOS: `PenteWebViewController.swift` sets `applicationNameForUserAgent` to include `penteLive-iOS`.
- penteLive-Android: none.

Deploy order: server first (Android benefits immediately), then the iOS release. Production deploys need owner confirmation. `top.jsp`, `help/` and `res/` are baked into the image (`Dockerfile:26`) and `sync_gameServer.sh` ships only `gameServer/` + classes, so the server deploy needs an image rebuild; classes before JSPs.

## 8. Testing

- Local stack (`justCompile` restarts the container). Server: withdraw happy path, each rejected case (not logged in, not registered, registration closed, cancelled tournament, cross-origin POST), and that the Redis pid list and the sign-up page's player list drop the player.
- Layout: open each opted-in page with and without the app user agents at 360 and 390 px; diff desktop HTML before/after.
- Android emulator and iOS simulator (iPhone and iPad) against the local backend: tap a state-1 tournament, sign up, withdraw, sign up again; open the format rules link; check state-2 and state-3 pages.

## 9. Known issues left out of scope (follow-ups)

- iOS sends the plaintext password in web view URL queries (`name2`/`password2`), which Tomcat access logs record (`AccessLogValve` pattern `%r`, `dockerMain/etctomcat9/server.xml:116-139`). Android already avoids this via a shared cookie store (`NoCredentialsInSourceTest.java`).
- `tournamentSignup.jsp` does not re-check registration-open or eligibility (checks live only in the confirm page's rendering, `tournamentConfirm.jsp:77-153`), has no CSRF check, and a duplicate POST throws on the primary key (`schema.sql:431`).
- `tournamentConfirm.jsp` shows the sign-up form even after registration closes.
- `helpWindow.jsp:22-25` throws on a `file=` value containing `#`.
