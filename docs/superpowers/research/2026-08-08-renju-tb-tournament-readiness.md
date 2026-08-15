# Renju turn-based tournament readiness audit — 2026-08-08

Question: can an operator start and run a Renju (game 81, TB_RENJU) turn-based tournament today?
**Answer: NO — two confirmed code blockers (draw→RESULT_UNFINISHED mapping, seeding query join bug), one format-specific NPE (round-robin status page), plus ops gaps (prod migration, image rebuild, undo during opening).**

Produced by a 27-agent audit workflow (7 area auditors + adversarial verification of every non-ok finding + completeness critic). Statuses: `ok` verified fine; `gap/unclear -> REAL` = confirmed by an independent adversarial verifier; `-> REFUTED` = the verifier disproved the auditor's claim (kept for the record, with the refutation).

## Tournament creation & signup flow for a turn-based Renju tourney
Area verdict: **gaps**

### [OK] Creation UI/servlet accepts game=TB_RENJU (81) — no hardcoded game list omits Renju

The creation page is admin/newTourney.jsp (linked from admin/index.jsp:31 as 'Create Tournament'). Its game dropdown is fully dynamic: newTourney.jsp:117 iterates GridStateFactory.getAllGames() and emits value="<%= games[i].getId() %>" (:120) with label GridStateFactory.getDisplayName(games[i].getId()). allGames (GridStateFactory.java:139-153) includes TB_RENJU_GAME (id 81), and displaygames has new Game(TB_RENJU, "Turn-based Renju", false) at :187 so getDisplayName(81) is non-null. newTourney.jsp:24 does Integer.parseInt(request.getParameter("game")) with no whitelist and :29 setGame(game). Tourney.isTurnBased() is game > 50 (Tourney.java:122-124), so 81 is treated as turn-based. TournamentServlet.java is NOT the creation path — it is crown assign/remove only and hardcoded to player 'rainwolf' (TournamentServlet.java:61). Operator caution, not a bug: the dropdown also lists 'Renju' (31) and 'Speed Renju' (32); picking 31 yields a live tourney.

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:116-123, :24, :29; dsg_src/java/org/pente/game/GridStateFactory.java:139-153, :187; dsg_src/httpdocs/gameServer/admin/index.jsp:31

### [OK] Signup/registration path works for Renju (restrictions + which rating column)

Signup is generic on tourney.getGame(): tournamentConfirm.jsp:65 loads dsgPlayerData.getPlayerGameData(tourney.getGame()) i.e. game 81, and tournamentSignup.jsp:23 calls addPlayerToTourney. Rating column: per-game row in dsg_player_game keyed (pid, game, computer) — schema.sql:150-162, game is tinyint(3) unsigned so 81 fits; TB Renju therefore uses its own rating row game=81, separate from live Renju (31). Restrictions persist to dsg_tournament_restriction (MySQLTourneyStorer.java:280-289) and are enforced twice: displayed/blocked at tournamentConfirm.jsp:93-117 and re-applied at seeding in CacheTourneyStorer.setInitialSeeds:429-451, both reading getPlayerGameData(tourney.getGame()). Precondition worth flagging: tournamentConfirm.jsp:78-84 refuses signup unless getTotalGames() > 0 for game 81, so no one can sign up until rated turn-based Renju sets have actually been played on the site. SimpleDSGPlayerData:404-413 fabricates a zero-rating record rather than returning null, so a RATING_RESTRICTION_ABOVE tourney sees rating 0 for such players and setInitialSeeds would drop them — consistent, not a crash.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:65, :78-84, :93-117; tournamentSignup.jsp:22-23; dsg_src/sql/schema.sql:150-162; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:425-451; Restriction.java:11-13; MySQLTourneyStorer.java:280-289; SimpleDSGPlayerData.java:404-413

### [GAP → REFUTED] How the tourney is scheduled/started (who flips it to started)

Normal path works: newTourney.jsp:92 calls CacheTourneyStorer.insertTourney(tourney, resources), which at :145-146 does `else if (tourney.isTurnBased() && getNumRounds()==0) startTournamentOrSetupTimer(tourney)`. That method (:686-700) starts immediately if start_date has passed, else schedules an in-memory java.util.Timer. startTournament (:660-684) seeds, and with <2 players sets status 'S' and cancels, otherwise createFirstRound + insertRound. GAP: the boot re-arm loop in DSGContextListener.setupLiveGameServers (:360-393) wraps everything in `if (tourney.isSpeed())` at :367, and the startTournamentOrSetupTimer re-arm is at :390 INSIDE that block. A turn-based tourney created with a future start date loses its timer on any Tomcat restart and will never auto-start. Manual recovery exists: admin/manageTourneys.jsp -> manageTourney.jsp:87-90 runs setInitialSeeds/createFirstRound/insertRound behind the 'Start round 1' submit (:151, command=start at :129). Also note there is no 'started' status: 'S' means cancelled (MySQLTourneyStorer:883 sets status='S' on cancel; :71 and :110 exclude it), 'C' means complete (:180); current-vs-upcoming is decided purely by signup_end_date/completion_date.

**Verification:** REFUTED. The auditor's core claim — "a turn-based tourney created with a future start date loses its timer on any Tomcat restart and will never auto-start" — is factually wrong. The auditor examined `setupLiveGameServers` and correctly observed its re-arm at DSGContextListener.java:389-391 is inside the `if (tourney.isSpeed())` gate at :367, then concluded no TB re-arm exists. But the TB re-arm is a *separate call in the same boot method*, six lines below the `setupLiveGameServers` invocation: `contextInitialized` calls `setupLiveGameServers(...)` at DSGContextListener.java:296 and then `tourneyStorer.setupTBTournaments()` at :300.

`CacheTourneyStorer.setupTBTournaments()` (:709-720) is precisely the missing loop: it collects `getCurrentTournies()` (:711) + `getUpcomingTournies()` (:712), hydrates each via `getTourneyDetails` (:714), and for every `tourney.isTurnBased() && tourney.getNumRounds() == 0` calls `startTournamentOrSetupTimer(tourney)` (:715-717). This is the exact mirror of the insertTourney TB branch at :145-146. The speed gate at :367 is therefore correct-by-design, not a bug: the two paths are disjoint (`isSpeed()` vs `isTurnBased()`), so speed tourneys are armed once in `setupLiveGameServers` and TB tourneys once in `setupTBTournaments` — no double-arm, no gap.

Renju TB is covered: `Tourney.isTurnBased()` returns `game > 50` (Tourney.java:122-124), and TB_RENJU=81 satisfies it. The two list queries are complementary and exhaustive for a not-yet-started tourney: `signup_end_date > sysdate()` (MySQLTourneyStorer:71) for upcoming, `sysdate() > signup_end_date AND completion_date IS NULL` (:109-110) for current — both excluding `status <> 'S'`. A TB tourney with a future start date is in exactly one of them at boot and gets its timer re-armed. This is not a recent/unmerged fix: `setupTBTournaments` is on HEAD (commit 74444e8, "automatically start TB tournaments"), and both files are clean in the working tree.

The auditor's *secondary* factual observations are accurate but describe design, not gaps: there is no 'started' status ('S' = cancelled per MySQLTourneyStorer:883, excluded at :71/:110; 'C' = complete at :180), and the admin manual path at manageTourney.jsp:87-90 behind the "Start round N" submit (:151, command=start at :129) is a real fallback.

ONE GENUINE BUT NARROW RESIDUAL, distinct from and far smaller than the claim (flagging honestly, not as a blocker): in `getUpcomingTournies`, a tourney whose `signupEndDate` has passed is added to `promote` (:199) and NOT to the returned `out` (:201 is the else branch). Since `setupTBTournaments` reads current first (:711) then upcoming (:712), a TB tourney sitting in a warm Redis UPCOMING list that crossed the signup_end boundary since the last read would be promoted into CURRENT during the :712 call but returned by neither call — missing that single boot's re-arm. This requires Redis surviving the Tomcat restart with a stale loaded list AND the boundary crossing in that window; on a cold cache both lists bootstrap straight from the DB queries (:184-189, :221-227), which classify correctly. It self-heals on the next restart (the tourney is then in CURRENT), and the admin "Start round 1" button covers it manually. I did not verify this race against a live system — marking it unclear in severity, but it does not block starting and running a Renju TB tournament today.

*Verifier evidence:* REFUTING EVIDENCE (the call the auditor missed):
- dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:296 — `setupLiveGameServers(resources, ctx, tourneyStorer);`
- dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:300 — `tourneyStorer.setupTBTournaments();` (inside contextInitialized, declared at :71; tourneyStorer is CacheTourneyStorer-typed local at :273-274)
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:709-720 — setupTBTournaments(): :711 getCurrentTournies(), :712 getUpcomingTournies(), :714 getTourneyDetails(), :715 `if (tourney.isTurnBased() && tourney.getNumRounds() == 0)`, :717 startTournamentOrSetupTimer(tourney)

DISJOINTNESS OF THE TWO BOOT PATHS (why the :367 speed gate is not a bug):
- dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:367 — `if (tourney.isSpeed())`, re-arm at :389-391 (speed only)
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:715 — `isTurnBased()` filter (TB only)
- dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 — `isTurnBased() { return this.game > 50; }` → TB_RENJU=81 qualifies
- dsg_src/java/org/pente/gameServer/tourney/Tourney.java:126-128 — `isSpeed()` reads a separate boolean field

TIMER MECHANISM INTACT:
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:686-707 — startTournamentOrSetupTimer: :688 starts immediately if startDate passed, else schedules Timer at :691-703 (timerIdx captured at :692 before add at :703, so index is correct)
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:30, :93 — `timers` field initialized in constructor (non-null at boot); :849 cancelled in destroy()

LIST COVERAGE IS EXHAUSTIVE FOR AN UNSTARTED TOURNEY:
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:67-71 — upcoming: `signup_end_date > sysdate() and status <> 'S'`
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:105-110 — current: `sysdate() > signup_end_date and completion_date is null and status <> 'S'`

ON MAIN, NOT PENDING:
- `git log -S setupTBTournaments` → 74444e8 "automatically start TB tournaments"
- `git show HEAD:...DSGContextListener.java | grep -n setupTBTournaments` → 300
- `git status --porcelain` on both files → clean (no working-tree modifications)

AUDITOR'S SECONDARY CLAIMS (accurate, but design not gap):
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:883 — cancel sets status='S'; :71, :110 exclude it; :180 completeTourney sets status='C'
- dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:87-90 (setInitialSeeds/createFirstRound/insertRound), :129 (command=start), :151 (submit "Start round N")
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:92 — `((CacheTourneyStorer) resources.getTourneyStorer()).insertTourney(tourney, resources);`
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:145-146 — TB branch of insertTourney (normal creation path, as auditor described)

NARROW RESIDUAL (promote drops from returned list):
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:195-203 — :198-199 promoted eid added to `promote`; :201 `out.add(t)` only in the else branch; :212 returns `out`
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:204-211 — promote writes eid into TOURNEY_LIST_CURRENT, but setupTBTournaments already read current at :711
- Cold-cache bootstrap that avoids the race: :184-189 (upcoming), :221-227 (current)

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:145-146, :660-684, :686-700; dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:360-393 (gate at :367, call at :390); dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:87-90, :129, :151; MySQLTourneyStorer.java:71, :110, :180, :883

### [OK] event_id creation for the tournament, and with which game id

Yes — starting from creation, each tourney gets its OWN game_event row. MySQLTourneyStorer.insertTourney:231-236 builds a SimpleGameEventData with setGame(tourney.getGame()) = 81 and setName(tourney name), calls gameVenueStorer.addGameEventData(81, newEvent, DSG2_12GameFormat.SITE_NAME), then uses the generated eid as the tourney event_id (:236) and as dsg_tournament_detail.event_id (:246). MySQLGameVenueStorer.addGameEventData:613-635 inserts (name, site_id, game) into game_event and re-selects eid; site_id comes from getGameSiteData(game, site) at :609 with NO null guard, so this hinges on the TB->base translation in findGameTreeData:450-459 (`int baseGame = game > 50 ? game - 50 : game`), which maps 81 -> 31 (RENJU). The RENJU base node exists because updateGameTree:190-233 synthesizes venue nodes from 'Live Game' game_event rows without needing pente_game rows, and registerAllGames:690-711 (called at boot, DSGContextListener.java:118 registerAllGames(2)) creates a 'Live Game' row for every GridStateFactory.LIVE_GAMES id including RENJU=31. This exact NPE was a past regression, now covered by MySQLGameVenueStorerTbLookupTest:70-82 (asserted with TB_PENTE, same code path as TB_RENJU). Net: a Renju TB tourney adds a 7th game_event row (game=81, name=tourney name) on top of the 6 boot rows.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:231-236, :239-246; dsg_src/java/org/pente/game/MySQLGameVenueStorer.java:600-635, :609, :450-459, :190-233, :690-711; dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:118; dsg_src/java/org/pente/game/test/MySQLGameVenueStorerTbLookupTest.java:70-82

### [GAP → REFUTED] Extra: tournaments landing page rendering for a Renju tourney

The 'Tournaments in Progress' (:116-137) and 'Upcoming Tournaments' (:150-164) lists are fully generic over getCurrentTournies()/getUpcomingTournies() and label by t.isTurnBased(), so a Renju TB tourney shows up and links to tournamentConfirm.jsp correctly. But the 'Current Tournament Champs' block hardcodes one variable per game at :73-91 (TB_GOMOKU, TB_KERYO, TB_BOAT_PENTE, TB_DPENTE, TB_CONNECT6, TB_POOF_PENTE, TB_DKERYO, TB_GPENTE, TB_GO, TB_GO9, TB_GO13, TB_OPENTE, TB_SWAP2PENTE, TB_SWAP2KERYO plus three TB_PENTE variants) with no TB_RENJU entry, so a completed Renju tourney gets no champ panel. Cosmetic — does not block starting or running the tourney.

**Verification:** REFUTED as a genuine gap. The factual observation is accurate but it neither blocks nor degrades starting/running a Renju TB tournament, and the meaningful part of it is already handled elsewhere on the same page.

Confirmed accurate: index.jsp:73-91 hardcodes 17 Tourney variables (TB_GOMOKU, TB_KERYO, TB_BOAT_PENTE, TB_DPENTE, TB_CONNECT6, TB_POOF_PENTE, TB_DKERYO, TB_GPENTE, TB_GO, TB_GO9, TB_GO13, TB_OPENTE, TB_SWAP2PENTE, TB_SWAP2KERYO, SPEED_PENTE plus three TB_PENTE restriction variants via helpers at :31-70) with no TB_RENJU. grep -rn "RENJU" over the entire tournaments JSP directory returns zero hits. No later commit fixed it (last touch to index.jsp is 2c82e55, predating Renju; e55f161/f7db119 do not touch it), and docs/renju-integration-guide.md has no tournament/champ coverage.

Why it does not block or degrade:
(1) The champs block only renders AFTER a Renju tourney completes — it is strictly downstream of every step in "start and run", so it cannot block creation, signup, start, pairing, or play.
(2) The generic paths are verified correct for Renju: Tourney.isTurnBased() is `return this.game > 50` (Tourney.java:122-124) and TB_RENJU = TB_START + RENJU = 81, so the in-progress (:116-137) and upcoming (:150-164) lists label it "Turn-Based" and link to statusRound.jsp / tournamentConfirm.jsp correctly.
(3) ALREADY HANDLED ELSEWHERE: a completed Renju tourney is not invisible. The "Past Tournaments" block (index.jsp:466-498) iterates completedDetails fully generically and renders the year heading, tourney name, statusRound.jsp link, and the winner via dsgPlayerStorer.loadPlayer(t.getWinnerPid()) + playerLink.jspf (:487-491). Only the decorative crown/avatar grid omits Renju.
(4) No crash risk: MySQLTourneyStorer contains zero game-specific branching (grep for GridStateFactory|switch (game|case returns nothing), so tourneyStorer.getTourney() called at :16/:121/:154 cannot break on game=81; the Past Tournaments loop is additionally wrapped in catch (Throwable) at :495-498.
(5) The champs grid is a hand-curated hall-of-fame, not scaffolding — commit 2c82e55 ("add swap2 games to tournament champions") establishes that every new game needs a deliberate manual JSP edit. Renju's absence is the expected pre-first-tourney state.

Point that actively cuts against the finding: the champs render region (:195-460) has ZERO null checks (awk for != null/== null over that range returns nothing; cells dereference directly, e.g. tbDPente.getEventID() at :285, tbGo.getWinner() at :400). Adding a TB_RENJU panel today would NPE on getLastTBTourney returning null and take down the whole tournaments landing page until the first Renju tourney completes. The current omission is the safe state; "fixing" it now would be the real regression.

Unclear side note (independent of Renju): that same lack of null guards means index.jsp depends on production having at least one completed tourney for all 17 hardcoded games. DB state could not be checked under the read-only constraint, so whether that is latently broken is unclear — but it is a pre-existing issue unrelated to Renju.

*Verifier evidence:* dsg_src/httpdocs/gameServer/tournaments/index.jsp:73-91 — champs variable block, 17 hardcoded games, no TB_RENJU (confirmed accurate).
dsg_src/httpdocs/gameServer/tournaments/index.jsp:31-70 — getLastTBTourney / getLastPenteOpenTBTourney / getLastPenteUnder1800TBTourney / getLastPenteMastersTBTourney helpers, each returns null when no match.
dsg_src/httpdocs/gameServer/tournaments/index.jsp:116-137 — "Tournaments in Progress", generic over getCurrentTournies(), labels via t.isTurnBased(), links statusRound.jsp.
dsg_src/httpdocs/gameServer/tournaments/index.jsp:150-164 — "Upcoming Tournaments", generic over getUpcomingTournies(), links tournamentConfirm.jsp.
dsg_src/httpdocs/gameServer/tournaments/index.jsp:466-498 — "Past Tournaments": fully generic loop over completedDetails rendering year, name, statusRound.jsp link and winner (loadPlayer(t.getWinnerPid()) + playerLink.jspf at :487-491), wrapped in catch (Throwable) at :495-498. A completed Renju tourney DOES appear here.
dsg_src/httpdocs/gameServer/tournaments/index.jsp:195-460 — champs render region; awk scan for "!= null"/"== null" returns 0 matches; direct dereferences at :285 (tbDPente.getEventID()), :400 (tbGo.getWinner()).
dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 — public boolean isTurnBased() { return this.game > 50; } — TB_RENJU=81 qualifies.
dsg_src/java/org/pente/game/GridStateFactory.java:77 — TB_RENJU = TB_START + RENJU; :82 TB_RENJU in TB game array; :187 new Game(TB_RENJU, "Turn-based Renju", false).
dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java — grep "GridStateFactory|switch (game|case " returns zero matches (no per-game branching; getTourney() is game-agnostic).
git log --oneline -- dsg_src/httpdocs/gameServer/tournaments/index.jsp — most recent: 2c82e55 "add swap2 games to tournament champions"; no Renju-era commit (e55f161, f7db119) touches this file.
grep -rn "RENJU" dsg_src/httpdocs/gameServer/tournaments/ — zero hits.
docs/renju-integration-guide.md — no tournament/champ-panel section (grep for tournament|tourney|champ hits only iOS opening-mask lines :545-546, :650).

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/index.jsp:73-91, :116-164

### [GAP → REFUTED] Extra: newTourney.jsp input handling gotchas for a TB tourney

Not Renju-specific but hits any TB tourney creation. :31-33 Integer.parseInt on both 'initial' and 'incremental' with no try/catch — the label at :131 says incremental is 'any value for TB, will be ignored', but leaving it blank throws NumberFormatException and yields a 500 rather than a validation message. :153-156 neither speed radio is pre-checked; leaving it unset makes getParameter("speed") null so setSpeed(false) (:35) — which is the right value for TB — but then :36-38 requires roundLength, again unguarded parseInt. Operator must fill Initial (days), Incremental (any number), Round length days, Signup end date, Format and Admins or the page errors.

**Verification:** REFUTED — the code observations are all factually correct, but they describe using a decade-old admin form as designed, not a gap that blocks or degrades a Renju TB tournament.

What I confirmed (every cited line is accurate):
- newTourney.jsp:31 and :33 do call Integer.parseInt on "initial" and "incremental" with no try/catch; :38 does the same for "roundLength"; :49 for "format". A blank field submits "" and throws NumberFormatException.
- :131 label reads exactly "Incremental time: (any value for TB, will be ignored)".
- :154-155 carry no `checked` attribute on either speed radio; :35 therefore yields setSpeed(false) when unset.
- The page has zero `required` attributes and zero <script> blocks (grep count 0 for both), so there is no client-side validation either.

Why it is not a real gap:

1. It does not block. Every field it names is present and labelled with what to type: :127 "Initial time: (Minutes for live, days for TB)", :131, :159 "Round length days: (if this is non-speed)". The finding's own conclusion — "Operator must fill Initial, Incremental, Round length days, Signup end date, Format and Admins" — is a description of filling in the form, not of a missing capability. The operator can create the tourney today.

2. The failure mode is free and side-effect-free. Every parse (:31, :33, :38, :41, :49) and the director lookup (:59-64) runs before the single DB write at :92 (insertTourney). A blank numeric field throws before anything persists — no orphan dsg_game_event row, no half-created dsg_tournament_detail. Back button, refill, resubmit. And web.xml:778-781 maps 500 to /five00.jsp (file exists), so it is a styled error page, not a raw trace.

3. The one sub-claim that could have been substantive is false. I tested whether ":131 will be ignored" is a lie — it is not. The only consumers of getIncrementalTime() for tourney games are commented out (SingleEliminationFormat.java:127, :140). The value is written to dsg_tournament_detail.incremental_time (MySQLTourneyStorer.java:248) and never read on the TB path. ServerTable.java:268 is the live-server path only. The label is truthful; any integer works.

4. The speed-radio sub-claim is self-refuting and irrelevant to TB-ness. The finding itself notes unset yields setSpeed(false), "which is the right value for TB." More to the point, TB-ness is not derived from that radio at all: Tourney.isTurnBased() is `return this.game > 50` (Tourney.java:122-124), driven purely by the Game dropdown. Selecting "Turn-based Renju" and leaving speed blank produces exactly the isSpeed=false + isTurnBased=true pair that CacheTourneyStorer.java:145 needs to auto-start the TB tourney. The dropdown does render that option: allGames is id-indexed and holds TB_RENJU_GAME at index 81 (GridStateFactory.java:139-154), and displaygames contains `new Game(TB_RENJU, "Turn-based Renju", false)`, so getDisplayName(81) returns a real label, not null.

5. Not Renju-specific and long pre-existing — the auditor concedes the first. git log shows newTourney.jsp last touched in 3a9cdf4 ("Jakarta Source migration", 2025-09-23), well before any Renju work; TB tourney support landed back in 7e71bfd ("Turn-Based Tournaments"). If this ergonomics pattern blocked TB tourney creation, no TB tournament would ever have been created on the site. No later commit (f7db119, e55f161, c453ace) touches this file, but none needed to.

6. Audience of one. The page sits behind /gameServer/admin/ and its Directors field is hardcoded to default value "rainwolf" (:186) — the repo owner. This is a private ops form, not a public flow.

Net: a legitimate code-quality nit (unguarded parseInt on an admin form) with no data-integrity risk, no Renju coupling, and no impact on whether a Renju TB tournament can be started or run. Worth a "nice to have" hardening ticket, not an audit gap. I did not evaluate other creation-flow items (e.g. whether the format/auto-start path correctly handles a Renju TB single-game set) — those are separate findings.

*Verifier evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:31,33 — unguarded Integer.parseInt on "initial"/"incremental" (claim CONFIRMED)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:36-38 — `if (!tourney.isSpeed())` then unguarded parseInt on "roundLength" (CONFIRMED)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:49 — same pattern on "format" (auditor missed this one; same non-impact)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:131 — label "Incremental time: (any value for TB, will be ignored)" (CONFIRMED, and truthful — see below)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:154-155 — neither speed radio has `checked` (CONFIRMED)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:35 — `tourney.setSpeed(speed != null && speed.equals("Y"))` → false when unset, correct for TB
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:92 — `insertTourney(tourney, resources)` is the ONLY DB write, and it is after every parse → failures leave no partial state
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:127,159 — labels tell the operator exactly what to enter for TB
dsg_src/httpdocs/gameServer/admin/newTourney.jsp:186 — Directors defaults to "rainwolf" (admin-only page, audience of one)
dsg_src/httpdocs/gameServer/admin/newTourney.jsp — grep count: 0 occurrences of "required", 0 of "<script" (no validation of any kind, but also none ever existed)

Refuting evidence:
dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 — `public boolean isTurnBased() { return this.game > 50; }` → TB-ness comes from the Game dropdown, NOT the speed radio
dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:145 — `else if (tourney.isTurnBased() && tourney.getNumRounds() == 0) startTournamentOrSetupTimer(tourney);` → the isSpeed=false/isTurnBased=true pair produced by leaving speed blank is exactly what auto-starts a TB tourney
dsg_src/java/org/pente/game/GridStateFactory.java:139-154 — allGames[] is id-indexed (null at 0) and includes TB_RENJU_GAME at index 81, so the :117-122 dropdown loop renders it
dsg_src/java/org/pente/game/GridStateFactory.java:~187 — displaygames[] contains `new Game(TB_RENJU, "Turn-based Renju", false)` → getDisplayName(81) returns a real label
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:127,140 — the ONLY uses of getIncrementalTime() for tourney games are commented out: `// g1.setDaysPerMove(tourney.getIncrementalTime());` → the ":131 will be ignored" label is TRUE for TB
dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:248 — incremental_time is stored but never read back on the TB path
dsg_src/java/org/pente/gameServer/server/ServerTable.java:267-268 — getInitialTime/getIncrementalTime consumed only on the LIVE server path
dsg_src/httpdocs/WEB-INF/web.xml:778-781 — `<error-code>500</error-code> <location>/five00.jsp</location>`; dsg_src/httpdocs/five00.jsp exists → styled error page, not a stack trace
git log -- dsg_src/httpdocs/gameServer/admin/newTourney.jsp → newest is 3a9cdf4 (2025-09-23, "Jakarta Source migration"); TB tourney support dates to 7e71bfd "Turn-Based Tournaments" → pattern predates Renju by years and has never prevented TB tourney creation

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:31-33, :36-38, :131, :153-156

### [GAP] Extra: documentation coverage for Renju tournaments

The living guide docs/renju-integration-guide.md (1479 lines) has no tournament section at all — its only 'tourn' hits are maskTournamentOpening references in the iOS engine notes. Nothing documents that a Renju TB tourney needs game id 81 (not 31), that signup requires a prior rated TB Renju set, or the restart caveat on the start timer.

*Evidence:* docs/renju-integration-guide.md (grep -i 'tourn' matches only :545, :546, :650, all about the iOS opening mask)

### [OK] Extra: no Renju-specific special-casing anywhere in the tourney subsystem

The tourney package, all admin JSPs, and all tournaments JSPs are game-agnostic — they carry Renju purely via the integer game id. This is why creation and signup work with no Renju-specific scaffolding. The corollary is that anything a swap-variant game needs at match/game-creation time (e.g. the original-seats helper from f7db119 / PR #12 and single-game Renju sets) lives outside this area and was not audited here.

*Evidence:* grep -rni 'renju' dsg_src/java/org/pente/gameServer/tourney/ dsg_src/httpdocs/gameServer/admin/ dsg_src/httpdocs/gameServer/tournaments/ returns zero matches

### [UNCLEAR] Unclear: whether the RENJU 'Live Game' game_event row actually exists in the running DB

Creation of a Renju TB tourney depends at runtime on a 'Live Game' game_event row for game=31 existing at site 2, because addGameEventData dereferences getGameSiteData(81, site) with no null check (MySQLGameVenueStorer:609, :618) and that resolves 81->31 through the venue tree. The code path that creates it is registerAllGames(2) at boot (DSGContextListener:118, ensureGameEvents at :716), which is idempotent and derives from GridStateFactory.LIVE_GAMES. I could not confirm the row is present in the live database — this was a read-only source audit with no DB access. If that row is missing (e.g. boot registration failed), tournament creation for Renju NPEs with 'siteData is null' rather than reporting a clean error.

*Evidence:* dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:118; dsg_src/java/org/pente/game/MySQLGameVenueStorer.java:690-711, :716 ensureGameEvents

## Tourney engine — pairing, sections, sets, colors (Renju turn-based, game=81)
Area verdict: **gaps**

### [OK] f7db119 change: renju is a single-game set (set = 1 game) vs pente's 2-game color-alternating set

f7db119 replaced five hardcoded go-family lists with one shared predicate GridStateFactory.isSingleGameSet(game), which returns true for GO/GO9/GO13 (+speed/TB) AND RENJU(31)/SPEED_RENJU(32)/TB_RENJU(81) (GridStateFactory.java:502-507). All five wiring sites are present and correct for game=81: SingleEliminationFormat.java:111 suppresses the mirrored second match; DoubleEliminationFormat.java:261 computes `boolean set = !isSingleGameSet(...)` and threads it through PotentialSection.createRealSection (:95-104) into PotentialMatch.createRealMatch (:45-51), which emits the reversed-seat match only when `set`; CacheTourneyStorer.java:492 exempts single-game sets from the pid-ordering dedup so every renju match spawns its own set; CacheTourneyStorer.java:551 withholds the second tie-replay match; CacheTBStorer.java:2200 builds tbg2 only for two-game sets. The resulting TBSet(tbg1, null) is a supported shape — TBSet.java:52-57 (ctor), :170-178 (isCompleted handles games[1]==null), :181-183 (isTwoGameSet), :185-189 (getWinnerPid, draw-aware). Net: a renju single-elim/double-elim pairing produces exactly 1 TourneyMatch -> 1 createTournamentSet -> 1 TBGame.

*Evidence:* dsg_src/java/org/pente/game/GridStateFactory.java:502-507; dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:111; dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java:261, :95-104, :45-51; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:492, :551; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2200-2212

### [OK] Seat/color assignment for swap-variant games — original-seats helper is used on both surfaces

TBGame.seatsSwapped() (TBGame.java:614-622) derives net seat parity for TB_RENJU from RenjuOpeningState.netSwapped(renjuSwaps); getOriginalPlayer1Pid/getOriginalPlayer2Pid (:633-640) undo the physical pid flip that the Taraguchi take-overs perform. The turn-based game-over path resolves the tournament match through the original seats and mirrors the winner: CacheTBStorer.java:1251-1252 calls getUnplayedMatch(getOriginalPlayer1Pid(), getOriginalPlayer2Pid(), eventId) and :1256 flips `winner = 3 - winner` when seatsSwapped() (guarded `winner != 0` so a draw is not flipped). The live surface does the same via GridState.seatsSwapped() at ServerTable.java:3817-3819 and mirrors the next-match pid lookup at :3836-3845. Color balance for renju is delegated to the Taraguchi-10 opening rather than to a seat-alternating second game — that is the stated design in the isSingleGameSet javadoc (GridStateFactory.java:496-501 area).

*Evidence:* dsg_src/java/org/pente/turnBased/TBGame.java:614-622, :633-640; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1251-1259; dsg_src/java/org/pente/gameServer/server/ServerTable.java:3812-3845; dsg_src/java/org/pente/game/GridStateDecorator.java:77

### [OK] No hardcoded PENTE/other game ids in the engine that break game=81

Grepping GridStateFactory.* across dsg_src/java/org/pente/gameServer/tourney/ leaves only one hardcoded-id region: CacheTourneyStorer.startAnotherTourney (:744, :759, :770, :787, :801), a Pente-only auto-rotation chain picking the next Open/Championship/Amateurs event. game=81 falls to the generic else at :815, `getDisplayName(game - 50)` = getDisplayName(31), and RENJU_GAME is present in the displaygames array (GridStateFactory.java:171), so the name resolves rather than NPEing. TB_RENJU itself is fully registered: TB_RENJU_GAME at :137, in the tb list at :153, in displaygames at :187 ("Turn-based Renju") and :234, and in the switch at :374 — so getGameName(81) used in the set-created mail (CacheTBStorer.java:2225) is safe. Tourney.isTurnBased() is `game > 50` (Tourney.java:122-124) -> true for 81; Tourney.createSection (:270-280) dispatches on format, not game. Pairing/section/round code contains no game-specific branch at all.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:744, :759, :770, :787, :801, :815; dsg_src/java/org/pente/game/GridStateFactory.java:137, :153, :171, :187, :234; dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124, :270-280

### [GAP → CONFIRMED REAL] Round advancement / winner-of-match logic given single-game sets — a DRAW permanently stalls the round

BLOCKER. TourneyMatch result codes are UNFINISHED=0, P1_WINS=1, P2_WINS=2, DBL_FORFEIT=3, TIE=4 (TourneyMatch.java:25-29). The game-over path writes the raw TBGame winner: CacheTBStorer.java:1255-1259 does `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) winner = 3 - winner; tourneyMatch.setResult(winner);`. TBGame.getWinner() returns 0 for a draw (TBGame.java:483-488 — setWinner(0) on a completed game sets draw=true). So a drawn game writes result=0 == RESULT_UNFINISHED. TourneyMatch.hasBeenPlayed() is `result != 0 || isBye()` (:74-76) -> false forever, so TourneySection.isComplete() (TourneySection.java:63-72) never returns true, TourneyRound.isComplete() (TourneyRound.java:42-49) never returns true, and the round never advances. The tie-break cannot rescue it either: SingleEliminationSection.init() (:151-170) has branches for DBL_FORFEIT, UNFINISHED, result==1 and result==2 but NO branch for RESULT_TIE, and the drawn match arrives as UNFINISHED, so :154-155 forces the aggregated SingleEliminationMatch to RESULT_UNFINISHED; CacheTourneyStorer.java:547 requires `m.getResult() == RESULT_TIE` before calling createMoreMatchesAfterTie, so the replay is never created and the isSingleGameSet guard at :551 is dead code for renju. No production code path writes RESULT_TIE(4) to a TourneyMatch (setResult callers: TourneySection.java:127/129/134 forfeits, TournamentServer.java:362, TournamentServerTable.java:208, ServerTable.java:3823, CacheTBStorer.java:1259, MySQLTourneyStorer.java:366 DB load). Renju draws are first-class and routinely reachable: accepted draw offer -> CacheTBStorer.java:307 REASON_DRAW; renju timeout-draw -> CacheTBStorer.java:717-722 (RenjuTimeoutDrawEvaluator + setDraw(true)); double-pass / board-full -> CacheTBStorer.java:1740-1743. The identical hole exists on the live surface (ServerTable.java:3812-3823). This is latent for pente (draws are rare and a 2-game set offers a second decision) but becomes an expected outcome for renju, where the set is one game and draw offers/pass moves/timeout draws are core rules.

**Verification:** CONFIRMED — real blocker, with one wording correction ("permanently stalls" → "stalls until an admin manually forfeits a player"). I tried four refutation routes; all failed.

WHAT I CONFIRMED

1. A drawn TB renju game writes result=0 to the TourneyMatch. TBGame.getWinner() returns the raw field, and setWinner(0) on a completed/timed-out game sets draw=true (TBGame.java:479-488). CacheTBStorer.java:1255-1259 writes that raw 0 straight through. The flip guard at :1256 literally reads `if (game.seatsSwapped() && winner != 0) { // != 0: not a draw` — the author knew 0 means draw and still passed it to setResult(). Since RESULT_UNFINISHED = 0 (TourneyMatch.java:25) and hasBeenPlayed() is `result != 0 || isBye()` (:74-76), a legitimately drawn match is byte-identical to a never-played one.

2. The draw genuinely reaches that code. I checked the enclosing game-over block for a draw guard — there is none. CacheTBStorer.java ~1078-1092 explicitly assigns winnerData/loserData under `if (game.isDraw())`, :1223-1226 selects the "It's a Draw" subject/body, and control falls into the tourney branch at :1244-1263 unconditionally. Draws are routinely reachable: accepted offer (:307 setWinner(0) + REASON_DRAW), renju timeout-draw (:717-722 RenjuTimeoutDrawEvaluator + setWinner(0)/setDraw(true)), double-pass/board-full (:1740-1743 `reason = (game.getWinner() == 0) ? REASON_DRAW : REASON_WIN`).

3. The stall is format-independent, so picking Swiss instead of single-elimination does not dodge it. It bites at TourneySection.isComplete() (:63-72) → TourneyRound.isComplete() (TourneyRound.java:42-49) → CacheTourneyStorer.checkRoundStatus(), which advances only on `t.getLastRound().isComplete()` (:606-612). SwissSection.java:236-238 has the identical UNFINISHED-forcing branch as SingleEliminationSection.

4. NEW EVIDENCE the auditor asserted but did not cite — the exact mechanism that kills the tie-replay rescue. SingleEliminationSection.init():154-155 sets the aggregate to RESULT_UNFINISHED (0). SingleEliminationMatch.updateResult() (:83-96) is wrapped in `if (getResult() == -1)`, so once init() has stamped 0 the recompute never runs and setResult(RESULT_TIE) at :93 is unreachable. CacheTourneyStorer.java:547 requires `m.getResult() == RESULT_TIE`, so createMoreMatchesAfterTie never fires. Independently, for any single-game set a decisive game yields 1-0 (init :158-169) → result 1 or 2, never a tie — so RESULT_TIE is structurally unreachable for renju by both paths.

5. The dead-code claim is correct and is a design smell, not a fix. GridStateFactory.java:502-506 includes RENJU || SPEED_RENJU || TB_RENJU in isSingleGameSet. `git show f7db119` shows that commit refactored CacheTourneyStorer:551 to `if (!GridStateFactory.isSingleGameSet(t.getGame()))` — i.e. the author deliberately taught the tie-replay path about renju single-game sets, but that branch sits inside the RESULT_TIE block proven unreachable above. Intent was there; reachability was never wired.

6. No production path writes RESULT_TIE(4) to a persisted TourneyMatch. Repo-wide grep: RESULT_TIE appears only at TourneyMatch.java:29 (constant), SingleEliminationMatch.java:79/93/121 (the transient aggregate, not a TourneyMatch), SingleEliminationSection.java:186 (read), CacheTourneyStorer.java:547 (read), and DoubleEliminationDriver.java:64 (test). MySQLTourneyStorer.java:366 only reflects whatever the DB already holds.

THE ONE CORRECTION — recovery exists, but only manual and only by falsifying the record

"Permanently stalls" overstates it. manageTourney.jsp:41-65 calls TourneyRound.forfeitPlayers → TourneySection.forfeitPlayers (:111-138), and the skip guard at :116 is `if (m.getResult() != RESULT_UNFINISHED) continue;` — a drawn match has result 0, so it is NOT skipped. An admin can forfeit one player (result 2 at :129 / result 1 at :134) or both (DBL_FORFEIT at :127) to unstick the round. But manageTourney.jsp exposes only forfeit/drop checkboxes (:130-141); there is no control anywhere to record a draw or a tie. So the only recovery records a false forfeit against a player who legitimately drew and arbitrarily hands the match to an opponent who did not win it.

No automatic recovery exists for TB. The round-cutoff sweep that force-resolves stalled matches (TournamentServer.java:340-368, which does set DBL_FORFEIT) is a live-room mechanism gated on mainRoom.isPlayerInMainRoom, so it never touches TB tournaments. checkRoundStatus is the only TB advancement trigger and it has no deadline path.

NET: for a TB renju tournament — where the set is one game and draw offers, pass moves and timeout draws are core rules, not rare accidents — the first drawn match freezes its round until a human notices and falsifies the result. That is a genuine blocker to "start and run a Renju turn-based tournament today with no missing scaffolding."

*Verifier evidence:* dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25-29 (RESULT_UNFINISHED=0 … RESULT_TIE=4); :74-76 (hasBeenPlayed = result != 0 || isBye) | dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259 (raw winner written; :1256 comment "// != 0: not a draw"); ~1078-1092 (winnerData/loserData assigned for isDraw — no draw guard before the tourney branch); :1223-1226 ("It's a Draw" subject); :1244-1263 (tourney branch, unconditional); :307 (accepted draw offer, setWinner(0)+REASON_DRAW); :717-722 (renju timeout-draw, setWinner(0)/setDraw(true)); :1740-1743 (double-pass/board-full → REASON_DRAW) | dsg_src/java/org/pente/turnBased/TBGame.java:479-488 (getWinner raw; setWinner(0) on completed → draw=true) | dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:63-72 (isComplete via hasBeenPlayed); :111-138 (forfeitPlayers; :116 skip guard lets drawn match through; :127 DBL_FORFEIT, :129/:134 forced win) | dsg_src/java/org/pente/gameServer/tourney/TourneyRound.java:42-49 (isComplete); :170-174 (forfeitPlayers) | dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:151-170 (no RESULT_TIE branch; :154-155 forces RESULT_UNFINISHED); :186-189 (getWinners reads RESULT_TIE) | dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:83-96 (updateResult gated on getResult()==-1 — the mechanism that makes :93 setResult(RESULT_TIE) unreachable once init stamped 0) | dsg_src/java/org/pente/gameServer/tourney/SwissSection.java:236-238 (identical UNFINISHED forcing — stall is format-independent) | dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:547 (requires RESULT_TIE); :551 (isSingleGameSet guard, dead for renju); :606-612 (checkRoundStatus advances only on getLastRound().isComplete()) | dsg_src/java/org/pente/game/GridStateFactory.java:502-506 (isSingleGameSet includes RENJU/SPEED_RENJU/TB_RENJU) | dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:41-65, :130-141 (only forfeit/drop controls; no draw/tie result entry) | dsg_src/java/org/pente/gameServer/server/TournamentServer.java:340-368 (live-only round-cutoff sweep, gated on isPlayerInMainRoom; never rescues TB) | dsg_src/java/org/pente/gameServer/server/ServerTable.java:3812-3823 (same hole on live surface) | dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:366 (DB load only) | git show f7db119 (refactored CacheTourneyStorer:551 to isSingleGameSet — intent present, reachability absent)

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25-29, :74-76; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259; dsg_src/java/org/pente/turnBased/TBGame.java:479-488; dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:151-170; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:547; dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:63-72

### [GAP → REFUTED] Tie-replay match orientation for single-game sets — the reversed-seat overload is never called

Minor / dead code. SingleEliminationFormat.createMoreMatchesAfterTie(original, boolean single) (:170-195) was written for exactly this case: when single==true it builds ONE replay match with REVERSED seats (:183-185, more[0].setPlayer1(original.getPlayer2())). Nothing ever passes single=true — the only production caller, CacheTourneyStorer.java:548, uses the 1-arg overload (:166-168) which hardcodes single=false, so more[0] keeps the ORIGINAL orientation and more[1] is constructed then silently discarded by the isSingleGameSet guard at :551-554. DoubleEliminationFormat overrides with an always-two version (:332-348) and has no single variant at all. Practical impact for renju is cosmetic — the Taraguchi-10 opening balances colors regardless of seating — and the path is unreachable today anyway because of the draw gap above. But the intended API is unwired, and any fix to the draw handling should route through the `single` overload.

**Verification:** Every factual claim in the report is accurate, but the item does not block or degrade running a Renju turn-based tournament, and the auditor's own framing ("Minor / dead code", "cosmetic") concedes as much. It should be dropped from a blocker list, or at most kept as a code-hygiene note attached to the separate draw-recording finding.

WHAT I CONFIRMED (the facts are right):
1. The overload exists exactly as described. SingleEliminationFormat.java:170-195 — `matches = 1` when `single` (:172-175), and the reversed-seat build at :183-185 (`more[0].setPlayer1(original.getPlayer2())`). The 1-arg overload at :166-168 hardcodes `false`.
2. Nothing passes `single=true`. Exhaustive grep over *.java/*.jsp/*.xml returns only three call sites, all 1-arg: SingleEliminationFormat.java:167 (self-delegation with `false`), CacheTourneyStorer.java:548 (the only production caller), and the test harness DoubleEliminationDriver.java:67. The 2-arg overload is genuinely dead.
3. The discard is real. CacheTourneyStorer.java:549-554 inserts `more[0]` unconditionally and gates `more[1]` behind `!isSingleGameSet(t.getGame())` (:551), so for TB_RENJU `more[1]` is constructed and thrown away, and `more[0]` keeps the original orientation.
4. DoubleEliminationFormat.java:332-348 does override the 1-arg with an always-two version and has no `single` variant; it extends SingleEliminationFormat (DoubleEliminationFormat.java:13), and since its override does not delegate to the 2-arg, the 2-arg is unreachable there too.

WHY IT IS NOT A GAP:

A. The path is dead for Renju, and not because of some pending fix — structurally. RESULT_UNFINISHED = 0 and RESULT_TIE = 4 (TourneyMatch.java:25,29). On TB game-over, CacheTBStorer.java:1255-1260 computes `winner = game.getWinner()` and stores it directly; a draw yields 0 (the comment at :1257 says so: `!= 0: not a draw`), so a drawn Renju match is persisted as RESULT_UNFINISHED, never as a tie. SingleEliminationSection.init() then takes the `m.getResult() == RESULT_UNFINISHED` branch at :154-155 and explicitly sets `currentMatch.setResult(RESULT_UNFINISHED)`, which makes `SingleEliminationMatch.updateResult()`'s `if (getResult() == -1)` guard at :85 fail — so the aggregate never reaches RESULT_TIE. Consequently the guard at CacheTourneyStorer.java:547 (`m.getResult() == RESULT_TIE && ...`) cannot fire for a single-game-set game. For a one-game set the only reachable aggregate results are 1, 2, 3 (double forfeit) or UNFINISHED. The tie-replay machinery is unreachable for Renju by construction, so the orientation of a replay it never creates cannot degrade anything.

B. Even if it were reachable, the current behavior is functionally correct for Renju. The count is right: one replay match, which is what a single-game set wants. `more[0]` gets `seq = original.getSeq() + 1` (:180), correct for a replay counter. `more[1]` is a discarded object — no DB row, no leak. The only deviation is seating, and seating does not determine color in Renju: GridStateFactory.java:495-501 states in its own javadoc that renju is a single-game set precisely because "renju (Taraguchi-10) balances it via the opening swap protocol". The set-creation guard at CacheTourneyStorer.java:491-492 short-circuits on `isSingleGameSet`, so the replay set is created regardless of pid ordering, and lookups use `getOriginalPlayer1Pid()/getOriginalPlayer2Pid()` (CacheTBStorer.java:1253-1254, added by f7db119) which are orientation-agnostic. SingleEliminationSection.updateAlreadyPlayed (:195-201) increments both directions symmetrically. Nothing downstream reads orientation in a way that breaks.

C. It is not a Renju regression. `git log -S 'createMoreMatchesAfterTie(TourneyMatch original, boolean single)'` returns a single commit: b80d903 "Go tournaments". The overload was written for the Go family and was never wired then either. Renju merely inherited pre-existing dead code by joining `isSingleGameSet` (GridStateFactory.java:502-507); neither f7db119 nor e55f161 touched it, and the tourney-file log shows f7db119 as the only Renju-era change to these files.

ONE THING WORTH FORWARDING (not a blocker): the overload is mildly hazardous to wire naively. With `single=true` the loop runs one iteration, so `more[1]` stays **null** (:176-181). Any future fix that routes through the `single` overload must also remove or null-guard the `more[1]` access at CacheTourneyStorer.java:552-553, or it trades dead code for an NPE. That is a note to attach to whoever fixes the real draw-recording defect (CacheTBStorer.java:1259 storing a draw as RESULT_UNFINISHED), which is the item that actually matters.

*Verifier evidence:* CONFIRMS THE REPORT'S FACTS:
- /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:166-168 — 1-arg overload: `return createMoreMatchesAfterTie(original, false);`
- SingleEliminationFormat.java:170-195 — 2-arg overload; `matches = 1` at :172-175; reversed seats at :183-185 (`more[0].setPlayer1(original.getPlayer2()); more[0].setPlayer2(original.getPlayer1());`)
- CacheTourneyStorer.java:548 — only production caller, 1-arg form
- CacheTourneyStorer.java:549-554 — `insertMatch(more[0]); s.addMatch(more[0]);` then `if (!GridStateFactory.isSingleGameSet(t.getGame())) { insertMatch(more[1]); s.addMatch(more[1]); }`
- DoubleEliminationFormat.java:332-348 — always-two override, no single variant; DoubleEliminationFormat.java:13 — `extends SingleEliminationFormat`
- Exhaustive grep for `createMoreMatchesAfterTie` across dsg_src: only SingleEliminationFormat.java:166,167,170, DoubleEliminationFormat.java:332, CacheTourneyStorer.java:548, test/DoubleEliminationDriver.java:67 — no `single=true` anywhere

REFUTES BLOCKING/DEGRADING IMPACT:
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25,29 — `RESULT_UNFINISHED = 0`, `RESULT_TIE = 4`
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1260 — `int winner = game.getWinner(); ... tourneyMatch.setResult(winner);` with comment at :1257 `// != 0: not a draw` → a draw is stored as 0 = RESULT_UNFINISHED, never RESULT_TIE
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:154-155 — `else if (m.getResult() == TourneyMatch.RESULT_UNFINISHED) { currentMatch.setResult(TourneyMatch.RESULT_UNFINISHED); }`
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:84-95 — `updateResult()` only assigns RESULT_TIE (:93) under `if (getResult() == -1)` (:85), which the line above has already falsified
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:547 — tie guard `m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound()` therefore cannot fire for a single-game-set game
- dsg_src/java/org/pente/game/GridStateFactory.java:495-507 — javadoc "renju (Taraguchi-10) balances it via the opening swap protocol. Both therefore play one game per tournament set."; `isSingleGameSet` includes RENJU, SPEED_RENJU, TB_RENJU
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:491-492 — set creation short-circuits on `GridStateFactory.isSingleGameSet(t.getGame())`, so pid ordering is irrelevant
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1253-1254 — match lookup via `game.getOriginalPlayer1Pid(), game.getOriginalPlayer2Pid()` (orientation-agnostic, added by f7db119)
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:195-201 — `updateAlreadyPlayed` increments both `[p1][p2]` and `[p2][p1]`

PROVENANCE (pre-dates Renju):
- `git log -S 'createMoreMatchesAfterTie(TourneyMatch original, boolean single)' -- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java` → single result: `b80d903 Go tournaments`
- `git log --oneline -- .../SingleEliminationFormat.java .../CacheTourneyStorer.java` → f7db119 is the only Renju-era commit touching these files; it did not alter the tie path

LATENT HAZARD FOR ANY FUTURE FIX:
- SingleEliminationFormat.java:176-181 — the allocation loop runs `matches` times, so with `single=true` `more[1]` is null; CacheTourneyStorer.java:552-553 would NPE if the guard at :551 were relaxed

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:166-168, :170-195; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:548-554; dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java:332-348

### [OK] Swiss and Round-Robin match counts / color balance for a single-game-set game

Neither format consults isSingleGameSet, which initially looks like a missed wiring site, but both already emit mirrored ordered pairs so the game count and color balance land identically for renju. RoundRobinFormat.java:94-110 creates a match for every ordered pair (j != k) = 2 matches per pairing; for pente only the pid-ordered one spawns a set (CacheTourneyStorer.java:487-493) giving 1 set x 2 games, for renju both spawn sets giving 2 sets x 1 game — 2 games either way, colors 1-1. SwissFormat PotentialMatch.createRealMatch (:116-153) creates four matches per pairing (m1/m3 with p1 first, m2/m4 with p2 first); pente gets 2 sets x 2 games, renju gets 4 sets x 1 game — 4 games either way, colors 2-2. Both formats are still subject to the draw stall above (SwissSection.java:234-253 repeats the same missing RESULT_TIE branch), and neither has any tie-break follow-up at all since CacheTourneyStorer.java:538 gates it on `instanceof SingleEliminationFormat`.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/RoundRobinFormat.java:94-110; dsg_src/java/org/pente/gameServer/tourney/SwissFormat.java:116-153; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:487-496

### [UNCLEAR → REFUTED] Whether an operator can manually unstick a drawn match from the admin screen

CacheTourneyStorer.updateMatches (:559-578) exists and is documented as the admin-management-screen entry point, and it applies whatever result the caller sets, so in principle an admin could force a result onto a stalled drawn match. Whether admin/manageTourney.jsp actually exposes a draw/tie option (and whether RESULT_TIE=4 would be accepted downstream given SingleEliminationSection.init() has no RESULT_TIE branch) is JSP/admin-surface territory outside this area — I did not verify the JSP and will not guess. Flagging so the operator-facing audit can confirm whether a manual workaround exists for the blocker above.

**Verification:** REFUTED. An operator CAN manually unstick a drawn Renju TB match from the admin screen, and the mechanism works precisely because of the same quirk that causes the underlying blocker. This is not an additional gap.

WHAT THE ADMIN SCREEN ACTUALLY EXPOSES
The auditor speculated that updateMatches "applies whatever result the caller sets, so in principle an admin could force a result." That framing is wrong about the admin surface. manageTourney.jsp (162 lines total) has NO result-setting UI at all — its only per-player controls are two checkboxes, "Forfeit" and "Drop" (manageTourney.jsp:130-146). updateMatches is never fed an arbitrary list: it is only ever handed the list returned by forfeitPlayers (manageTourney.jsp:61-65). So there is no free-form "set result" lever, and the RESULT_TIE=4 question the auditor raised is moot — nothing in the admin surface can produce a 4.

WHY THE FORFEIT LEVER NEVERTHELESS WORKS ON A DRAWN MATCH
A drawn TB game writes result=0 onto the tourney match: CacheTBStorer.java:1255-1259 does tourneyMatch.setResult(game.getWinner()), and a draw's winner is 0, which is identical to TourneyMatch.RESULT_UNFINISHED (TourneyMatch.java:25). That is exactly the blocker (hasBeenPlayed() stays false — TourneyMatch.java:74-76; SingleEliminationSection.init() forces the aggregate to RESULT_UNFINISHED at :154-156, so the round never completes).

But the same 0 makes the match reachable by the forfeit path. TourneySection.forfeitPlayers skips only matches whose result is NOT RESULT_UNFINISHED (TourneySection.java:118: "if (m.getResult() != TourneyMatch.RESULT_UNFINISHED) continue;"). A drawn match has result 0, so it is NOT skipped — checking Forfeit for one of the two players sets result 1 or 2 with forfeit=true (TourneySection.java:127-134). Flow: manageTourney.jsp:61-65 -> TourneyRound.forfeitPlayers:170-177 -> TourneySection.forfeitPlayers:110-134 -> CacheTourneyStorer.updateMatches:566-578 (persistTourney + checkRoundStatus) -> round advances. Had the draw been recorded as any nonzero code (including RESULT_TIE=4), line 118 would have skipped it and the operator would genuinely have been stuck.

TWO MORE OF THE AUDITOR'S PREMISES ARE INACCURATE
1. "SingleEliminationSection.init() has no RESULT_TIE branch" is literally true of init() but misleading: getWinners() has one at SingleEliminationSection.java:186, and CacheTourneyStorer.java:547 has the tie -> createMoreMatchesAfterTie replay path. RESULT_TIE is a fully handled state.
2. RESULT_TIE never reaches a dsg_tournament_match row at all. It is set only on the derived SingleEliminationMatch aggregate when win counts are equal (SingleEliminationMatch.java:88-94), and MySQLTourneyStorer.updateMatch only ever persists gid/result/forfeit for real match rows (MySQLTourneyStorer.java:794-808). For Renju this tie path is also unreachable in practice, since Renju uses single-game sets, so parity-of-wins never arises — which is why the auditor's real blocker (draw -> 0) stands on its own.

DEGRADATIONS WORTH REPORTING (none blocking)
- The workaround records a forfeit, not a draw: the match renders as "defeats (forfeit)" (SingleEliminationMatch.java:104-110), misrepresenting a legitimately drawn game, and the operator arbitrarily picks which player survives.
- Forfeit is per-PLAYER, not per-MATCH — forfeitPlayers scans every unfinished match of that pid across all sections of the last round (TourneyRound.java:170-177, TourneySection.java:113-134). Harmless for Renju because Renju uses single-game sets (one pairing = one match), but it would over-apply in multi-game-set games.
- Checking Forfeit for BOTH players yields RESULT_DBL_FORFEIT=3 and eliminates both (TourneySection.java:124-126). Operator footgun, documented behaviour.
- The operator does get feedback when the round is still not complete (manageTourney.jsp:117-122).
- Forfeit is the ONLY lever. The other two admin JSPs do not help: addTourneyMatch.jsp inserts into dsg_tournament_results with a hardcoded section=1 (addTourneyMatch.jsp:50-59), which is a different table from the dsg_tournament_match rows the tourney engine reads (MySQLTourneyStorer.java:759, :801), so it cannot inject a replay match; markTourneyGame.jsp only rewrites pente_game.event_id/round/section (markTourneyGame.jsp:31-41) and sets no result.
- docs/renju-integration-guide.md contains nothing on tournament draws, forfeits, or the admin surface (grep for draw/forfeit/admin/tourn returns only board-rendering "draw*" hits).

BOTTOM LINE for the operator-facing audit: the manual workaround exists and is sufficient to keep a Renju TB tournament moving. It is ugly (a forfeit standing in for a draw, survivor chosen by fiat) but it is not missing scaffolding. The real item to report remains the auditor's original blocker — a TB draw writing result=0 is indistinguishable from unplayed (CacheTBStorer.java:1255-1259 vs TourneyMatch.java:25).

*Verifier evidence:* dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:41-65 (only forfeit/drop params feed updateMatches); :130-146 (UI is two checkboxes per player, no result field); :117-122 (round-incomplete warning)
dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:118 (skips only result != RESULT_UNFINISHED — drawn match with result 0 is NOT skipped); :113-134 (forfeit sets result 1/2, both-checked -> RESULT_DBL_FORFEIT); :90-101 (getUnplayedMatch keys on !hasBeenPlayed)
dsg_src/java/org/pente/gameServer/tourney/TourneyRound.java:170-177 (fans forfeitPlayers across all sections of the round)
dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:566-578 (updateMatches -> backingStorer.updateMatch + applyMatchTo + persistTourney + checkRoundStatus); :534-556 (RESULT_TIE -> createMoreMatchesAfterTie, isSingleGameSet inserts one replay match); :547
dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1251-1260 (tourneyMatch.setResult(game.getWinner()); draw winner == 0; the "!= 0: not a draw" swap guard from 1ed9aad)
dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25 (RESULT_UNFINISHED = 0); :29 (RESULT_TIE = 4); :74-76 (hasBeenPlayed() == result != 0 || isBye)
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:154-156 (any RESULT_UNFINISHED game forces aggregate unfinished); :186 (getWinners DOES have a RESULT_TIE branch, contra the auditor)
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:76-80 (isComplete excludes TIE and UNFINISHED); :88-94 (RESULT_TIE set only on the derived aggregate); :104-110 ("defeats (forfeit)" rendering)
dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:794-808 (updateMatch persists only gid/result/forfeit to dsg_tournament_match); :759 (insert into dsg_tournament_match)
dsg_src/httpdocs/gameServer/admin/addTourneyMatch.jsp:50-59 (writes dsg_tournament_results, hardcoded section=1 — wrong table, cannot inject a replay match)
dsg_src/httpdocs/gameServer/admin/markTourneyGame.jsp:31-41 (only updates pente_game event_id/round/section; sets no result)
git show 1ed9aad (draw guard on TB tournament winner flip — confirms draw == winner 0 is the intended representation)
docs/renju-integration-guide.md (no coverage of tournament draws/forfeits/admin surface)

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:559-578 (updateMatches, "right now only called from admin management screen"); dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:111-134

## Turn-based game creation & completion inside a tournament (Renju / TB_RENJU=81)
Area verdict: **gaps**

### [OK] Operator can select Turn-based Renju when creating a tourney

newTourney.jsp:116-121 builds the <select name="game"> by iterating GridStateFactory.getAllGames() and emitting games[i].getId() as the option value with getDisplayName(id) as the label. allGames[] contains TB_RENJU_GAME (GridStateFactory.java:153) and displaygames[] contains new Game(TB_RENJU, "Turn-based Renju", false) (:187), so getDisplayName(81) resolves to a non-null label. newTourney.jsp:24-29 parses the param and calls tourney.setGame(81). GridStateFactory.getGame(81) resolves via the TB branch: tbGames[(81-50-1)/2] = tbGames[15] = TB_RENJU_GAME (:392-397 with tbGames[] at :226-235, 16 entries, TB_RENJU last) — index math checks out, so getGameName(81) used in the tourney notification mail (CacheTBStorer.java:2225-2226) will not throw.

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:116-121, :24-29; dsg_src/java/org/pente/game/GridStateFactory.java:153, :187, :430-441

### [OK] Tourney gets its own event id; tb_game rows carry it

insertTourney (MySQLTourneyStorer.java:231-236) creates a fresh GameEventData for tourney.getGame() via gameVenueStorer.addGameEventData(...) and stores the generated eid on the Tourney. createTournamentSet then stamps that eid onto the TBGame (CacheTBStorer.java:2195, :2207). The completion path distinguishes tourney games from ordinary ones by comparing against the boot-registered default TB event (CacheTBStorer.java:1250 `game.getEventId() != getEventId(game.getGame())`, getEventId at :1366-1376). Since the tourney event is a new row, that inequality holds and the tourney branch is taken.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:226-236; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2195, :1366-1376

### [OK] (1) Tourney-created tb_game gets game=81 and the correct event id

CacheTourneyStorer.insertMatch:487-496 fires createTournamentSet when `t.getGame() > 50` (81 qualifies) and `(p1pid < p2pid || GridStateFactory.isSingleGameSet(t.getGame()))`. isSingleGameSet (GridStateFactory.java:502-506) includes TB_RENJU, so the pid-ordering dedupe is bypassed and every match gets its own set. CacheTBStorer.createTournamentSet:2189-2198 sets game=81, eventId=tourney eid, rated=true, STATE_ACTIVE; :2200-2211 skips the second (reversed-seat) TBGame because isSingleGameSet(81) is true, producing a genuine one-game set. SingleEliminationFormat:111-120 and DoubleEliminationFormat:261 use the same flag, so the bracket does not create a duplicate reverse match. RoundRobinFormat:97-108 creates both directions unconditionally, which combined with the isSingleGameSet bypass at CacheTourneyStorer.java:492 yields two single-game sets per pair (one black each) — coherent, not a defect.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:483-497; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2185-2218; dsg_src/java/org/pente/game/GridStateFactory.java:502-506

### [OK] tb_game.round / tb_game.section are not populated for tourney games

createTournamentSet never calls setRound/setSection on the TBGame, so the INSERT (MySQLTBGameStorer.java:151-157, which does include round and section columns) writes 0/0. The archive path compensates by re-deriving the round from the tourney match at CacheTBStorer.java:974-979. Pre-existing behaviour shared with every other TB tourney game, not Renju-specific — flagged only so it is not mistaken for a Renju regression.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2189-2211; dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:151-157; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:970-979

### [OK] (2) Renju opening (renju_swaps/renju_offers, MoveServlet renjuAction) behaves identically for tourney games

The Renju decision handler at MoveServlet.java:449-488 keys solely on `game.getGame() == GridStateFactory.TB_RENJU && renjuAction != null`; it reconstructs state via RenjuState.reconstruct(game, game.getRenjuSwaps(), game.getRenjuOffers()) (:450-451) and dispatches swap/branch/offer through tbGameStorer.renjuSwap/renjuBranch/renjuOffers (:464-488). There is no event-id or tourney predicate anywhere in that block. The only tourney reference in MoveServlet is dead code (:146-148, commented out), and the event-id-based hide check is likewise commented out (:438). The draw-offer guard at :411-421 branches on game type and renju phase only. Conclusion: no tourney branch bypasses Renju logic.

*Evidence:* dsg_src/java/org/pente/turnBased/web/MoveServlet.java:449-488, :411-421, :146-148, :438

### [UNCLEAR → REFUTED] Renju opening columns are absent from the tb_game INSERT (write-then-update pattern)

createGame's column list (MySQLTBGameStorer.java:153-155) omits renju_swaps and renju_offers, so a tourney-created row starts with the schema defaults, both NULL (schema.sql:891-892). Those columns are only ever written by the later UPDATE statements (:1414, :1457, :1496) and read back by the SELECT projections (:429, :436). I did not read the ResultSet mapping code, so I cannot confirm that a NULL renju_swaps is coerced to 0 rather than surfacing as a null/0 ambiguity in RenjuState.reconstruct. This is identical for tourney and non-tourney Renju games, so if it were broken ordinary Renju play would be broken too — which argues it is fine — but it is unverified here.

**Verification:** REFUTED. The auditor's factual observations are all correct (the INSERT does omit both columns; the schema defaults are NULL; only the later UPDATEs write them), but the inferred risk — a "null/0 ambiguity in RenjuState.reconstruct" — does not exist. I read the ResultSet mapping the auditor had not read, and NULL round-trips to exactly the fresh-game state, bit for bit.

The chain:

1. Load mapping. MySQLTBGameStorer.java:637 uses `result.getInt(...)` with no `wasNull` check, so SQL NULL becomes 0. Line 638 uses `getBytes(...)`, which returns Java null on SQL NULL, and `RenjuOpeningState.decodeOffers` null-guards and returns null (RenjuOpeningState.java:82-85).

2. 0 is unambiguous, not a sentinel collision. RenjuOpeningState.java:16-18 defines PENDING=0, NO=1, YES=2, and `encode()` (RenjuOpeningState.java:27-33) is `swap1 + 3*swap2 + 9*swap3 + 27*swap4 + 81*branch + 243*swap5`. Every non-PENDING digit contributes at least 1, so packed==0 holds if and only if all six digits are PENDING. There is no decided state that also encodes to 0, so NULL→0 cannot be misread as a made decision. This is why the code deliberately does not need `wasNull` here (unlike the archive table `pente_game`, where the guide at docs/renju-integration-guide.md:187 notes `wasNull` IS used — a different column with different semantics).

3. The coerced values equal the in-memory defaults. TBGame.java:85 `private int renjuSwaps = 0; // RenjuOpeningState packed word (0 = fresh / non-Renju)` and TBGame.java:86 `private int[] renjuOffers = null;`. So a freshly-INSERTed row reloads into a TBGame indistinguishable from the one that was inserted. The write-then-update pattern loses nothing.

4. reconstruct handles it correctly, and there is a test for exactly this case. RenjuState.java:80-91: `decode(0)` yields all-PENDING; the swap loop replays move 1 then returns at `swaps[0] == PENDING`. RenjuReconstructTest.java:32-40 (`testReconstructMidOpening_pendingSwapAfterMove1`) builds `new RenjuOpeningState()` — all fields 0, so `encode()==0` — with `offers=null`, which is precisely the state a NULL row produces, and asserts `isAwaitingSwapDecision()`. The coverage the auditor thought was missing is present.

5. Nothing is lost at INSERT time. Grepping every `setRenjuSwaps`/`setRenjuOffers` caller in dsg_src/java shows only the load path (MySQLTBGameStorer.java:637-638, CacheTBStorer.java:2088), the decision path (MoveServlet.java:487, which runs on an already-persisted game), GameData export (TBGame.java:747-748), and tests. No caller populates these fields on a TBGame before `createGame`, so there is no value for the INSERT to drop. CacheTBStorer.java:1460-1471 just delegates to `baseStorer.createGame` with no renju handling.

6. The pattern is pre-existing and production-proven. The same INSERT (MySQLTBGameStorer.java:151-156) also omits `winner`, `completion_date`, `dpente_swap`, `hiddenBy`, and `swap2pass` — dPente and Swap2 have shipped this way for years. Renju is not special here.

Tourney-specific check: nothing in the tourney path differs. The only TBGame construction in dsg_src/java/org/pente/gameServer/tourney/ is commented out (SingleEliminationFormat.java:124, :137), so tourney TB games go through the same CacheTBStorer/MySQLTBGameStorer.createGame as ordinary games. The auditor's own "if this were broken, ordinary Renju play would be broken too" reasoning is sound and now confirmed at the code level.

One adjacent risk the auditor flagged indirectly and that I also cleared: `loadGame` falls back to selecting TB_COLUMNS from `tb_game_ai` (MySQLTBGameStorer.java:436), which the guide warns would break all AI TB loads if the columns were missing. schema.sql:921-922 confirms `tb_game_ai` has both `renju_swaps` and `renju_offers`. Not a gap.

Verdict: this does not block or degrade a Renju TB tournament. No fix needed. Note this is static analysis only — I did not execute the test suite or query a live DB (read-only audit), but the JDBC NULL→0 coercion is specified behavior, not an inference.

*Verifier evidence:* dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:151-156 (INSERT column list, confirms omission); :429 and :436 (TB_SET_COLUMNS / TB_COLUMNS projections include g.renju_swaps, g.renju_offers); :637 `game.setRenjuSwaps(result.getInt(r++));` (JDBC NULL -> 0); :638 `game.setRenjuOffers(org.pente.game.RenjuOpeningState.decodeOffers(result.getBytes(r++)));` (JDBC NULL -> null); :1414, :1457, :1496 (the three UPDATE statements).
dsg_src/java/org/pente/game/RenjuOpeningState.java:16-18 (PENDING=0, NO=1, YES=2); :27-33 (encode(); packed==0 iff all digits PENDING); :35-44 (decode()); :82-85 (decodeOffers null-guard returns null).
dsg_src/java/org/pente/turnBased/TBGame.java:85 `private int renjuSwaps = 0; // RenjuOpeningState packed word (0 = fresh / non-Renju)`; :86 `private int[] renjuOffers = null;`; :556-568 (getRenjuPhase calls RenjuState.reconstruct); :747-748.
dsg_src/java/org/pente/game/RenjuState.java:80-91 (reconstruct: decode(packed), returns at first PENDING swap digit).
dsg_src/java/org/pente/game/test/RenjuReconstructTest.java:32-40 (testReconstructMidOpening_pendingSwapAfterMove1 — `new RenjuOpeningState()` all-PENDING, encode()==0, offers=null, asserts isAwaitingSwapDecision()).
dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1460-1471 (createGame delegates, no renju handling).
dsg_src/java/org/pente/turnBased/web/MoveServlet.java:487 (only pre-persist-independent setter, runs on existing game).
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:124,:137 (only TBGame constructions in tourney package, both commented out).
dsg_src/sql/schema.sql:869 (CREATE TABLE tb_game), :891-892 (renju_swaps smallint unsigned DEFAULT NULL, renju_offers varbinary(10) DEFAULT NULL); :899 (CREATE TABLE tb_game_ai), :921-922 (same two columns present).
docs/renju-integration-guide.md:55 (both tb_game and tb_game_ai must carry the columns), :187 (wasNull is used on pente_game, the archive table — a different, genuinely nullable-semantics column).

*Evidence:* dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:151-157, :429, :436, :1414, :1457, :1496; dsg_src/sql/schema.sql:891-892

### [OK] (3) Completion reports the result back to the tourney (win/loss case)

All game endings funnel into the EndGameRunnable body (class opens at CacheTBStorer.java:832) whose tourney branch is :1250-1262: it looks up the match with getUnplayedMatch(game.getOriginalPlayer1Pid(), game.getOriginalPlayer2Pid(), game.getEventId()), sets the gid, flips the winner when the seats were swapped, and calls tourneyStorer.updateMatch. The original-seat helpers handle Renju correctly: TBGame.seatsSwapped():614-622 delegates to RenjuOpeningState.netSwapped(renjuSwaps) for TB_RENJU, and getOriginalPlayer1Pid/getOriginalPlayer2Pid:633-639 invert accordingly. So a decisive Renju tourney game — including one where a Taraguchi-10 take-over flipped the seats — is recorded from the match's original perspective.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1250-1262, :832; dsg_src/java/org/pente/turnBased/TBGame.java:614-639

### [GAP → CONFIRMED REAL] (3) DRAW results reach the tourney — pass-pass, agreed draw, timeout-draw

BLOCKER. CacheTBStorer.java:1255-1259 does `int winner = game.getWinner(); ... tourneyMatch.setResult(winner)`. For every draw the winner is 0 — the code says so itself at :1256 (`if (game.seatsSwapped() && winner != 0) { // != 0: not a draw`). But TourneyMatch.RESULT_UNFINISHED == 0 and RESULT_TIE == 4 (TourneyMatch.java:25, :29), and hasBeenPlayed() is `return result != 0 || isBye()` (:74-76). A drawn Renju tourney game therefore writes result=0 and the match stays permanently UNPLAYED: TourneySection.isComplete():63-72 and TourneyRound.isComplete():42-49 can never return true, so the round never advances and the tourney never completes. getUnplayedMatch keeps returning the same match, so a later game between the same pair would overwrite it. Nothing in the codebase maps a game draw to RESULT_TIE — the sole setResult(RESULT_TIE) is SingleEliminationMatch.updateResult():83-95, gated on `getResult() == -1`, which aggregates a two-game set's win counts and so never runs for a single-game-set game like Renju. All three Renju draw sources converge on this same line: agreed draw (CacheTBStorer.java:304-307, setWinner(0) then endGame REASON_DRAW), double-pass/board-full (:1738-1744, winner 0 -> REASON_DRAW), and timeout-draw (:720-722 then :741). The identical defect exists on the live-game path (ServerTable.java:3822-3823, `tourneyMatch.setResult(localWinner2)` where localWinner2 is 0 on a draw), so it is not TB-specific — but Renju is the first game where draws are a designed, routine outcome, which is what turns a latent bug into a blocker.

**Verification:** CONFIRMED — real blocker. I tried four independent refutation routes and all failed; I also found a fifth line of evidence the original auditor missed that makes the case stronger.

WHAT I CONFIRMED VERBATIM
1. The defect line is exactly as cited. CacheTBStorer.java:1251-1259 fetches the unplayed match, then `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { // != 0: not a draw ... } tourneyMatch.setResult(winner);`. TourneyMatch.java:25 `RESULT_UNFINISHED = 0`, :29 `RESULT_TIE = 4`, :74-76 `hasBeenPlayed() { return result != 0 || isBye(); }`. A draw therefore writes 0 and is byte-identical to "never played".
2. Draws genuinely reach that line — no early return. CacheTBStorer.java:1058-1078 explicitly assigns winnerData/loserData for `game.isDraw()`, and control flows straight through the message block (:1223-1226 use `game.isDraw()` ternaries) into the tourney block at :1244-1260.
3. All three Renju draw sources set winner 0, as claimed: agreed draw CacheTBStorer.java:304-307 (`tbGame.setWinner(0)` -> REASON_DRAW), timeout-draw :720-722 (`fresh.setWinner(0); fresh.setDraw(true)`) fired at :741, double-pass/board-full :1738-1744 (`game.getWinner() == 0 ? REASON_DRAW : REASON_WIN`).
4. The completion chain is dead as described. TourneySection.isComplete():63-72 loops `!m.hasBeenPlayed() -> return false`; TourneyRound.isComplete():42-49 delegates to it; AbstractTourneyFormat.java:91 and Tourney.java:282 delegate upward.

REFUTATIONS I ATTEMPTED AND WHY THEY FAILED
A. "Some other format handles it." No. Grepping every `isComplete()` shows only TourneySection:63, TourneyRound:42, SingleEliminationMatch:76 and Tourney:282 — TourneySection.isComplete() has no override, so Swiss, RoundRobin, single- and double-elimination all inherit the broken test. SwissSection.java:237-238 repeats the identical clobber (`m.getResult() == RESULT_UNFINISHED -> currentMatch.setResult(RESULT_UNFINISHED)`).
B. "A later commit fixed it." No — the opposite. `git log` on the tourney package and CacheTBStorer shows the last touch to this exact line is 1ed9aad "Polish: TBGame helper javadoc + draw guard on TB tournament winner flip". Its diff is a one-liner: `- if (game.seatsSwapped())` / `+ if (game.seatsSwapped() && winner != 0) { // != 0: not a draw`. The author looked directly at the draw case here and fixed only the seat-flip, deliberately leaving `setResult(0)` intact. e55f161 (the Renju draw feature) never touched it.
C. "The renju single-game-set tourney work (f7db119) covered it." It tried and cannot fire. CacheTourneyStorer.java:547-555 has a tie-rematch block gated on `m.getResult() == TourneyMatch.RESULT_TIE`, and inside it an explicit `if (!GridStateFactory.isSingleGameSet(t.getGame()))` branch (:551) added precisely for Renju single-game sets. But that block is unreachable for draws (see D), so the Renju-specific scaffolding that exists is dead code. This is evidence the case was anticipated and left unfinished, not that it is handled.
D. "updateResult() maps it to RESULT_TIE." No, and the auditor's mechanism needs one correction that does not change the verdict. `SingleEliminationMatch.updateResult()` IS called (SingleEliminationSection.java:119) — the auditor said it "never runs". But it is a no-op: the gate is `getResult() == -1` (SingleEliminationMatch.java:84, field initialised `result = -1` at :17), and SingleEliminationSection.java:154-155 has already clobbered it via `else if (m.getResult() == RESULT_UNFINISHED) currentMatch.setResult(RESULT_UNFINISHED)` when it read the drawn TourneyMatch's 0. So the reason RESULT_TIE is never reached is "runs after the result was overwritten to 0", not "never runs". Outcome identical; worth knowing for whoever fixes it.

NEW EVIDENCE THE ORIGINAL AUDIT MISSED — the DB round-trip is lossy the same way
`dsg_tournament_match.result` is `enum('1','2','3','4')` (dsg_src/sql/schema.sql) — there is no '0' member. MySQLTourneyStorer.java:805 does `stmt.setInt(2, tourneyMatch.getResult())`, so a draw's 0 is written as MariaDB's invalid-value sentinel `''`. On read, MySQLTourneyStorer.java:366 uses `DBUtil.enumInt(results, 11)`, which is `v.isEmpty() ? 0 : parseInt(v)` (DBUtil.java:16-21) — back to 0. Commit 11d12e7 documents exactly this empty-string-numeric-ENUM class of bug. So a drawn Renju tourney match persists as `result=''` and reloads as RESULT_UNFINISHED, surviving a server restart as permanently unplayed. Encouragingly, the column CAN already hold '4', so the fix needs no schema change.

CONSEQUENCES (all verified reachable)
- Round never advances, tourney never completes: TourneySection.isComplete():63-72 -> TourneyRound.isComplete():42-49 -> Tourney.isComplete():282 all stuck false. CacheTourneyStorer.checkRoundStatus (:606-611) therefore never promotes the round.
- Match-row corruption: TourneySection.getUnplayedMatch():90-102 is gated on `!m.hasBeenPlayed()`, so it keeps returning the drawn match; CacheTBStorer.java:974-976 would bind a later set between the same pair to that same row and overwrite its gid.
- Not TB-specific: the live path has the identical defect at ServerTable.java:3818-3823 (`if (swapped && localWinner2 != 0) { // != 0: not a draw` then `tourneyMatch.setResult(localWinner2)`).
- The guide does not cover it: grepping docs/renju-integration-guide.md for tourney/tournament/RESULT_TIE returns no tournament-draw guidance; every "draw" hit is rendering code (drawStone/drawGrid/drawBoard).

VERDICT: The gap genuinely blocks running a Renju turn-based tournament to completion. Renju draws are a designed, routine outcome with three distinct sources, and a single drawn game permanently stalls the round with no admin recovery path short of a manual DB edit or a forfeit override (TournamentServer.java:348-362 can only force RESULT_DBL_FORFEIT, which misreports a legitimate draw as a double forfeit).

*Verifier evidence:* CONFIRMS THE CITED CLAIM
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1251-1259 — getUnplayedMatch -> `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { // != 0: not a draw` -> `tourneyMatch.setResult(winner)`; draw writes 0.
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25 (RESULT_UNFINISHED = 0), :29 (RESULT_TIE = 4), :74-76 (hasBeenPlayed = `result != 0 || isBye()`).
- dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:63-72 (isComplete loops !hasBeenPlayed -> false).
- dsg_src/java/org/pente/gameServer/tourney/TourneyRound.java:42-49 (delegates to section.isComplete).
- dsg_src/java/org/pente/gameServer/tourney/AbstractTourneyFormat.java:91; dsg_src/java/org/pente/gameServer/tourney/Tourney.java:282 (delegate upward).

DRAW REACHES THE TOURNEY BLOCK (no early return)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1058-1078 — `if (game.isDraw())` assigns winnerData/loserData; :1223-1226 draw-aware message text; flow continues into :1244-1260.

THREE DRAW SOURCES, ALL winner 0
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:304-307 (agreed draw: `tbGame.setWinner(0)` -> REASON_DRAW)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:720-722, fired at :741 (timeout draw: `fresh.setWinner(0); fresh.setDraw(true)` -> REASON_TO)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1738-1744 (double-pass/board-full: `game.getWinner() == 0 ? REASON_DRAW : REASON_WIN`)

NO DRAW->RESULT_TIE MAPPING ANYWHERE (full grep of setResult/RESULT_* in dsg_src, tests excluded)
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:17 (`private int result = -1`), :84 (`if (getResult() == -1)`), :93 (`setResult(TourneyMatch.RESULT_TIE)`) — the only RESULT_TIE write in the codebase.
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:119 (updateResult IS called), :154-155 (`else if (m.getResult() == RESULT_UNFINISHED) currentMatch.setResult(RESULT_UNFINISHED)` — clobbers -1 to 0, making :84's gate fail).
- dsg_src/java/org/pente/gameServer/tourney/SwissSection.java:237-238 — identical clobber, so Swiss breaks too.
- Other setResult sites write only 1/2/DBL_FORFEIT: TourneySection.java:127/:129/:134; SingleEliminationSection.java:153; SwissSection.java:236; TournamentServer.java:348/:358/:362; TournamentServerTable.java:175/:208; ServerTable.java:3823.

RENJU-SPECIFIC TIE SCAFFOLDING EXISTS BUT IS UNREACHABLE
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:547 (`if (m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound())`), :551 (`if (!GridStateFactory.isSingleGameSet(t.getGame()))` — added by f7db119 for renju single-game sets). Dead for draws because RESULT_TIE is never set.

NO LATER FIX (refutation attempt failed)
- `git show 1ed9aad -- dsg_src/java/org/pente/turnBased/CacheTBStorer.java` — last touch to the line; diff is `- if (game.seatsSwapped())` / `+ if (game.seatsSwapped() && winner != 0) { // != 0: not a draw`. setResult(winner) left unchanged. Commit subject: "Polish: TBGame helper javadoc + draw guard on TB tournament winner flip".
- `git log --oneline -- dsg_src/java/org/pente/gameServer/tourney/` head: 11d12e7, f7db119, d4951df, 559d937 — none address draw->tie.
- `git log --oneline -- .../CacheTBStorer.java` head: e55f161, 3d86ea5, f7db119 — e55f161 (renju draws) did not touch the tourney block.

NEW: LOSSY DB ROUND-TRIP (not in the original report)
- dsg_src/sql/schema.sql, table dsg_tournament_match: `result enum('1','2','3','4') DEFAULT NULL` — no '0' member.
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:805 — `stmt.setInt(2, tourneyMatch.getResult())` writes 0 into that enum -> invalid-value sentinel ''.
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:366 — `match.setResult(DBUtil.enumInt(results, 11))`.
- dsg_src/java/org/pente/database/DBUtil.java:16-21 — `return v.isEmpty() ? 0 : Integer.parseInt(v);` -> reloads as RESULT_UNFINISHED. Commit 11d12e7 documents this empty-string-numeric-ENUM behaviour.

COLLATERAL / SAME DEFECT ELSEWHERE
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:3818-3823 — live path, identical `!= 0` guard + `setResult(localWinner2)`.
- dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:90-102 — getUnplayedMatch gated on !hasBeenPlayed, keeps returning the drawn match; rebinding risk at dsg_src/java/org/pente/turnBased/CacheTBStorer.java:974-976.
- dsg_src/java/org/pente/gameServer/server/TournamentServer.java:348-362 — only admin override is RESULT_DBL_FORFEIT; no way to record a true draw.

GUIDE SILENT
- docs/renju-integration-guide.md — grep for tourney/tournament/RESULT_TIE yields no tournament-draw guidance; all "draw" hits are rendering (drawStone/drawGrid/drawBoard, e.g. :96, :442, :1172).

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259; dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25, :29, :74-76; dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:63-72; dsg_src/java/org/pente/gameServer/tourney/TourneyRound.java:42-49

### [GAP → CONFIRMED REAL] Downstream scoring/display of a tied match

Even if the result-0 mapping above were fixed to RESULT_TIE, SwissSection's per-match scoring (:234-253) has branches for DBL_FORFEIT, UNFINISHED, 1 and 2 but none for RESULT_TIE, so a tie awards no increment to either player. As written today a draw renders as "vs." (SingleEliminationMatch.getResultStr():109-110) — indistinguishable from an unplayed match — instead of "tied with" (:121-122). Separately, the two getUnplayedMatch implementations disagree on what unplayed means: MySQLTourneyStorer:658-664 filters `result is null` while updateMatch:800-807 always writes an int (never null), so the SQL fallback and the cached TourneySection.getUnplayedMatch (`!m.hasBeenPlayed()`) can diverge once any result has been written.

**Verification:** REAL, but the reported item needs two corrections and its severity is understated.

CONFIRMED (verbatim re-read):
1. Reachability of the trigger. The tourney-update block in CacheTBStorer.storeGameDSG is NOT gated on a non-draw. Brace-depth trace shows the enclosing chain at line 1251 is class -> `EndGameRunnable` -> `storeGameDSG` (:920) -> `try` (:923) -> `else if (game.getEventId() != getEventId(game.getGame()))` (:1250) -- the tournament branch. Draws are explicitly handled just above for messaging (`if (game.isDraw()) { winnerData = player1; loserData = player2; }`), so a drawn game reaches :1251-1260 and executes `tourneyMatch.setResult(winner)` with winner==0. The code's own comment at :1256 (`// != 0: not a draw`) proves the author knew 0 means draw, yet passes it straight through.

2. 0 collides with UNFINISHED. `RESULT_UNFINISHED = 0`, `RESULT_TIE = 4` (TourneyMatch.java:25,:29). So a drawn Renju TB game persists as "unfinished".

3. The consequence is worse than display. `hasBeenPlayed()` is `result != 0 || isBye()` (TourneyMatch.java:74-76). Therefore `TourneySection.isComplete()` (:63-72) returns false forever, so `TourneyRound.isComplete()` (:42-46) is false and `checkRoundStatus` (CacheTourneyStorer.java:606-611) never advances the round -- the tournament STALLS, it is not merely mis-rendered. Additionally `TourneySection.getUnplayedMatch()` (:90-102) keeps handing back the same drawn match, so the pair can be re-paired/replayed.

4. The two display/scoring claims hold as stated. SwissSection:234-253 genuinely has branches only for DBL_FORFEIT, UNFINISHED, 1 and 2 -- no RESULT_TIE. And today a draw does render "vs." (SingleEliminationMatch.java:109-110): the per-game loop sets the aggregate to RESULT_UNFINISHED at :237-238, after which `updateResult()` (:83-95) is a no-op because it only fires `if (getResult() == -1)`, so the "tied with" branch at :121-122 is never reached.

5. Amplified by single-game sets. Renju tourney matches are single-game (CacheTourneyStorer.java:551 `GridStateFactory.isSingleGameSet(t.getGame())`; commit f7db119 "renju single-game tournament sets"), so ONE draw permanently freezes that match -- there is no second game to break the tie.

REFUTED -- drop this strand: the "two getUnplayedMatch implementations disagree" claim describes DEAD CODE, not a live divergence. `CacheTourneyStorer.getUnplayedMatch()` (:405-423) consults only the cached sections via `s.getUnplayedMatch(...)` (:416) and never falls back to the backing storer; `grep -rn "backingStorer.getUnplayedMatch" dsg_src/` returns NONE. `MySQLTourneyStorer.getUnplayedMatch()` (:647-699, the `result is null` query) is therefore unreachable on the production path. Its inconsistency with `updateMatch`'s always-int write (:805) is latent and cannot degrade a Renju TB tournament today.

CORRECTED -- the implied Swiss-vs-SingleElim asymmetry is wrong: `SingleEliminationSection.java:151-170` has the IDENTICAL missing RESULT_TIE branch in its per-game loop. SingleElim only handles ties at the aggregate level (`getWinners():186-189` plus `createMoreMatchesAfterTie` at CacheTourneyStorer:547-555), and that follow-up is gated on `SingleEliminationFormat` AND final round -- so Swiss has no tie follow-up whatsoever.

CORRECTED -- the conditional claim overshoots on display: if the mapping were fixed to write RESULT_TIE, display would self-correct. The per-game loop increments neither player, wins stay equal, and `updateResult()` :88-94 falls to the else -> `setResult(RESULT_TIE)` -> "tied with" (:121-122). What genuinely survives that fix is (a) no half-point scoring -- Swiss ranks off integer `incrementMatchWins(m.getPlayer1Wins())` (SwissSection.java:44-48), so a draw scores 0/0 rather than 0.5/0.5; and (b) `SingleEliminationMatch.isComplete()` (:78-79) returns false for RESULT_TIE, so an all-drawn Swiss match still never completes. So the item's core assertion -- that fixing the mapping alone is insufficient -- is correct.

NO LATER FIX EXISTS. `git log` for SwissSection.java / SingleEliminationMatch.java ends at d4951df "tournament server review", predating all Renju work. The Renju commits that touched CacheTBStorer (e55f161 / PR #19 which ADDED draws, f7db119 / PR #12) did not change the result mapping. docs/renju-integration-guide.md covers only client-side rendering (board decode, star points, opening masks) and contains no tourney tie handling.

NET ANSWER TO THE AUDITED QUESTION: an operator CAN start a Renju TB tournament, but cannot reliably RUN one to completion. The first drawn game -- reachable via pass moves, draw offers, or the timeout-draw rule shipped in PR #19 -- permanently wedges that match and its round. Root fix is one line (CacheTBStorer.java:1259 must map winner==0 to RESULT_TIE), but it must be accompanied by a RESULT_TIE branch in both per-game loops, a tie-aware `hasBeenPlayed()`/`isComplete()`, and a half-point-capable Swiss score, or the stall simply moves from UNFINISHED to TIE.

*Verifier evidence:* ROOT CAUSE (draw -> result 0):
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259 -- `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { // != 0: not a draw` ... `tourneyMatch.setResult(winner);`
- Enclosing chain (brace-depth trace, depth 5 at :1251): class :19 -> EndGameRunnable :832 -> storeGameDSG :920 -> try :923 -> `} else if (game.getEventId() != getEventId(game.getGame())) {` :1250 -- NOT draw-gated; `game.isDraw()` handled for messaging above (winnerData=player1, loserData=player2)
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25 `RESULT_UNFINISHED = 0`; :29 `RESULT_TIE = 4`

STALL (worse than display):
- TourneyMatch.java:74-76 `hasBeenPlayed() { return result != 0 || isBye(); }`
- TourneySection.java:63-72 `isComplete()` returns false if any `!m.hasBeenPlayed()`
- TourneySection.java:90-102 `getUnplayedMatch()` re-returns the drawn match
- TourneyRound.java:42-46 `if (!s.isComplete()) return false;`
- CacheTourneyStorer.java:606-611 `checkRoundStatus` -> `else if (t.getLastRound().isComplete())` never fires

CONFIRMED AS REPORTED:
- SwissSection.java:234-253 -- branches for DBL_FORFEIT (:234-236), UNFINISHED (:237-238), 1 (:241-246), 2 (:247-252); no RESULT_TIE
- SingleEliminationMatch.java:109-110 `else if (getResult() == RESULT_UNFINISHED) result = "vs.";`
- SingleEliminationMatch.java:121-122 `else if (getResult() == RESULT_TIE) result = "tied with";`
- SingleEliminationMatch.java:83-95 `updateResult()` guarded by `if (getResult() == -1)` -- no-op once :238 set 0
- MySQLTourneyStorer.java:658-664 `"... and result is null"`; :805 `stmt.setInt(2, tourneyMatch.getResult())`
- dsg_src/sql/schema.sql:322 `` `result` enum('1','2','3','4') DEFAULT NULL `` -- index 0 is not a valid member

REFUTING THE getUnplayedMatch DIVERGENCE STRAND:
- CacheTourneyStorer.java:405-423 -- delegates only to `s.getUnplayedMatch(player1ID, player2ID)` (:416); no backing-storer call
- `grep -rn "backingStorer.getUnplayedMatch" dsg_src/` -> NONE
- MySQLTourneyStorer.java:647-699 unreachable in production

CORRECTING THE ASYMMETRY CLAIM:
- SingleEliminationSection.java:151-170 -- same per-game loop, same missing RESULT_TIE branch
- SingleEliminationSection.java:186-189 -- RESULT_TIE handled only in getWinners() (aggregate)
- CacheTourneyStorer.java:547-555 -- `if (m.getResult() == RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound())` gated on SingleEliminationFormat (:538) and final round; :551 `GridStateFactory.isSingleGameSet(t.getGame())`

SCORING GAP THAT SURVIVES A MAPPING FIX:
- SwissSection.java:44-48 `incrementMatchWins(m.getPlayer1Wins())` / `incrementMatchLosses(...)` -- integer win counts, no half-point
- SwissSection.java:116-117 ranking by `p2.getMatchWins() - p1.getMatchWins()`
- SingleEliminationMatch.java:78-79 `isComplete()` returns false for RESULT_TIE

HISTORY (no later fix):
- `git log --oneline -- SwissSection.java SingleEliminationMatch.java` -> newest d4951df "tournament server review" (pre-Renju)
- `git log --oneline -- CacheTBStorer.java` -> e55f161 (#19, added draws), f7db119 (#12), neither changed the result mapping
- docs/renju-integration-guide.md -- client rendering only; no tourney tie handling

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/SwissSection.java:234-253; dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:109-110, :121-122; dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:658-664, :800-807

### [OK] (4) Timeout handling for tourney TB games invokes the Renju timeout-draw rule

TimeoutCheckRunnable (CacheTBStorer.java:557, timer armed at :364-365) is the sole TB timeout authority and does not branch on tourney vs non-tourney. At :710 it tests `fresh.getGame() == GridStateFactory.TB_RENJU` and computes `timeoutDraw = !RenjuTimeoutDrawEvaluator.opponentCanWin(rs, 3 - rs.getCurrentPlayer())` (:717-718); on a draw it does setWinner(0) + setDraw(true) (:721-722), otherwise awards the win to the beneficiary seat (:724-725). It then calls endGameRunnable.endGame(fresh, REASON_TO) at :741, which is the same handler carrying the tourney reporting block. Tourney-specific extra time is honoured: :619-621 reads tourney.getIncrementalTime() as maxExtraDays for tourney games. So the Renju timeout-draw rule does run for tourney games — but its draw outcome then hits the result-0 gap above, so a timed-out-to-draw Renju tourney game stalls its round.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:557-561, :704-726, :740-742, :619-621

### [GAP] Tourney status pages render a Renju single-game set correctly

statusRound.jsp:16-18 computes `isTBSingleGame` as TB_GO || TB_GO9 || TB_GO13 — TB_RENJU is missing, even though GridStateFactory.isSingleGameSet(81) is true. The flag drives statusSingleElim.jsp:72 (`colspan=isTBSingleGame?4:2`) and :80-83, which emit a second <td colspan="2"> for matchSet.getGame2(); statusDoubleElim.jsp:67,:75 do the same. For Renju getGame2() is null, so the extra cell renders empty and the colspans are wrong. Cosmetic only — the null is guarded at statusSingleElim.jsp:85-86 so nothing throws — but the round-status page will look wrong for the first Renju tourney. Cheap one-line fix.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:16-18; dsg_src/httpdocs/gameServer/tournaments/statusSingleElim.jsp:72, :80-90; dsg_src/httpdocs/gameServer/tournaments/statusDoubleElim.jsp:67, :75

### [OK] TourneyListener path for auto-creating sets on new rounds

CacheTBStorer is registered as a TourneyListener at DSGContextListener.java:276, but its tourneyEventOccurred (CacheTBStorer.java:2162-2183) is entirely commented out ("causing problems"). Set creation does not depend on it: CacheTourneyStorer.insertRound:461-466 calls insertMatch per match, and insertMatch:494-495 calls createTournamentSet directly. The NEW_ROUND event at :471 is only consumed for live/speed tourney notification in the main room. So the dead listener is not a missing-scaffolding problem for TB Renju.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2162-2183; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:461-471, :483-496; dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:276

## Tournament scoring & standings vs Renju draws (turn-based)
Area verdict: **gaps**

### [OK] Draw is representable in the match model and DB (RESULT_TIE)

The slot exists. TourneyMatch defines RESULT_UNFINISHED=0, P1_WINS=1, P2_WINS=2, DBL_FORFEIT=3, RESULT_TIE=4 (TourneyMatch.java:25-29) and dsg_tournament_match.result can store '4'. So a draw is representable end-to-end. The defect is not the model — it is that nothing on the game-completion path ever writes the value.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:29 (RESULT_TIE = 4); dsg_src/sql/schema.sql:322 (`result` enum('1','2','3','4'))

### [GAP → CONFIRMED REAL] Turn-based game completion maps a Renju draw to a tie

THE PRIMARY DEFECT. On turn-based tourney game completion: `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { winner = 3 - winner; } tourneyMatch.setResult(winner);`. TBGame.getWinner() returns 0 for a draw (TBGame.java:479-488: setWinner sets draw=true when state is COMPLETED/COMPLETED_TO and winner==0). So a drawn Renju game calls setResult(0) — which IS TourneyMatch.RESULT_UNFINISHED. The inline comment `// != 0: not a draw` proves the author knew 0 means draw, but the draw case was never mapped to RESULT_TIE. The draw is silently overwritten as 'never played'.

**Verification:** CONFIRMED — the auditor is right, and the defect is worse than described: it stalls the tournament, not merely mis-records one game.

VERIFIED CHAIN (each link read, not inferred):
1. Cited code is verbatim at CacheTBStorer.java:1255-1259.
2. TBGame.java:479-488 — getWinner() returns the raw field; setWinner(0) with STATE_COMPLETED/COMPLETED_TO sets draw=true. So a drawn TB game yields getWinner()==0.
3. TourneyMatch.java:25 RESULT_UNFINISHED=0 vs :29 RESULT_TIE=4 — the value collision is exactly as claimed.
4. Draws are NOT short-circuited before the tourney block: CacheTBStorer.java:1058 branches on game.isDraw() for ratings/messages and falls straight through to the tourney block at :1244-1263. A drawn Renju tourney game reaches setResult(0).
5. It is persisted: MySQLTourneyStorer.java:794-808 unconditionally writes `set gid=?, result=?` — so the row lands with a real gid AND result=0, i.e. "a game was played but the match was never played".
6. Consequence beyond the auditor's claim: TourneyMatch.hasBeenPlayed() = `result != 0 || isBye()` (TourneyMatch.java:74-76) → false. TourneySection.isComplete() returns false on any unplayed match (TourneySection.java:63-72) → TourneyRound.isComplete() false (TourneyRound.java:42-49) → CacheTourneyStorer.checkRoundStatus() (:606-619) never fires createNextRound. The round never advances. The tournament hangs permanently on the drawn match.
7. No compensating second game: TB_RENJU is a single-game set (GridStateFactory.java:502-506, whose own comment says renju "balances it via the opening swap protocol... one game per tournament set"). The draw IS the match outcome.

REFUTATIONS I ATTEMPTED, ALL DEFEATED:
- Best candidate: SingleEliminationMatch.updateResult() (:83-96) derives RESULT_TIE when player1Wins==player2Wins, which a 0-0 drawn match would satisfy. DEAD: SingleEliminationSection.init():154-156 explicitly maps `m.getResult()==RESULT_UNFINISHED` → `currentMatch.setResult(RESULT_UNFINISHED)`, and updateResult() is guarded by `getResult()==-1` (SingleEliminationMatch.java:84), so it never runs. isComplete() (:76-81) then returns false and getWinners() (:178-193) adds nobody. The bracket stalls too. The setResult(0) has destroyed the very information (draw vs. not-yet-played) the tie logic needs.
- Live path as counter-example: NOT a mitigation — ServerTable.java:3812-3823 carries the identical raw-winner bug with the same `// != 0: not a draw` comment. That widens the defect, it does not excuse the TB path.
- Later fix: none. `git log -S "setResult(winner)"` on CacheTBStorer returns only 4caa9da ("d-pente fixes", pre-Renju). f7db119 (PR #12) added only the `// != 0: not a draw` comment while touching the swap-flip. e55f161 (PR #19, the renju-draw commit) rewrote CacheTBStorer (+89/-12) but its diff contains zero setResult/TourneyMatch/tourney lines.
- Admin workaround: no clean one. manageTourney.jsp:61-65 is the only result-writing UI and it calls only forfeitPlayers(), which sets 1/2/DBL_FORFEIT (TourneySection.java:118-137) — never RESULT_TIE. Worse, that path is gated on `getResult() != RESULT_UNFINISHED` (TourneySection.java:116), so the drawn match is eligible for forfeit, letting the operator only mis-record the draw as a forfeit loss. Recovery requires manual SQL on dsg_tournament_match.

DAMNING CORROBORATION: e55f161's own commit message lists, under "Fix plans per adversarial cross-review", the item "tournament RESULT_TIE check" — and docs/superpowers/plans/2026-07-15-renju-pass-draw-server.md:2130 still carries it as an UNCHECKED box: "- [ ] Tournament plumbing (spec §9): ... confirm a drawn renju game maps to RESULT_TIE ...; fix mapping if a draw falls through". The team identified this exact gap and shipped the draw feature without closing it.

SCOPE HONESTY: this does not block STARTING a Renju TB tournament, and it only bites when a draw actually occurs. But Renju draws are reachable in TB today (CacheTBStorer.java:299-305 accept-draw; :721 timeout-draw), and Taraguchi-10 exists precisely to balance the game, which makes draws a designed-for outcome rather than an edge case. First draw = silently stalled tournament requiring DBA intervention. That degrades running a Renju TB tournament and is not handled anywhere else.

*Verifier evidence:* PRIMARY DEFECT (verbatim, confirmed):
dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1254-1260
 tourneyMatch.setGid(game.getGid());
 int winner = game.getWinner();
 if (game.seatsSwapped() && winner != 0) { // != 0: not a draw
 winner = 3 - winner;
 }
 tourneyMatch.setResult(winner);
 tourneyStorer.updateMatch(tourneyMatch);

VALUE COLLISION:
dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25 — public static final int RESULT_UNFINISHED = 0;
dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:29 — public static final int RESULT_TIE = 4;

DRAW => getWinner()==0:
dsg_src/java/org/pente/turnBased/TBGame.java:483-488 — setWinner(): `if ((state == STATE_COMPLETED || state == STATE_COMPLETED_TO) && winner == 0) { draw = true; }`
dsg_src/java/org/pente/turnBased/TBGame.java:479-481 — getWinner() returns raw winner.

DRAWS REACH THE BLOCK (no short-circuit):
dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1058 — `if (game.isDraw()) {` (ratings/message branch), falls through to :1244-1263.
dsg_src/java/org/pente/turnBased/CacheTBStorer.java:299-305 (accept-draw setWinner(0)), :721 (timeout-draw setWinner(0)) — TB renju draws are reachable.

PERSISTED AS result=0:
dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:800-808 — "update dsg_tournament_match set gid = ?, result = ?, forfeit = ? where mid = ?" with setInt(2, tourneyMatch.getResult()); unconditional.

STALL CHAIN (beyond auditor's claim):
dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:74-76 — `hasBeenPlayed() { return result != 0 || isBye(); }`
dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:63-72 — isComplete() returns false if any `!m.hasBeenPlayed()`
dsg_src/java/org/pente/gameServer/tourney/TourneyRound.java:42-49 — isComplete() false if any section incomplete
dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:606-619 — checkRoundStatus(): next round only created when `t.getLastRound().isComplete()`

SINGLE-GAME SET (no compensating game):
dsg_src/java/org/pente/game/GridStateFactory.java:502-506 — isSingleGameSet() includes RENJU, SPEED_RENJU, TB_RENJU (comment :499-501).

REFUTATION CLOSED (accidental-tie path is explicitly blocked):
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:154-156 — `else if (m.getResult() == TourneyMatch.RESULT_UNFINISHED) { currentMatch.setResult(TourneyMatch.RESULT_UNFINISHED); }`
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:84 — updateResult() guarded by `if (getResult() == -1)`, so the RESULT_TIE branch at :93 never fires.
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationMatch.java:76-81 — isComplete() false for RESULT_UNFINISHED.
dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:178-193 — getWinners() adds no player for a 0-result match.

SAME BUG IN LIVE PATH (widens, not refutes):
dsg_src/java/org/pente/gameServer/server/ServerTable.java:3812-3823 — `int localWinner2 = localWinner; if (swapped && localWinner2 != 0) { // != 0: not a draw ... } tourneyMatch.setResult(localWinner2);`

NO ADMIN WORKAROUND FOR A TIE:
dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:61-65 — only forfeitPlayers(...) + updateMatches(...)
dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:116-137 — forfeitPlayers sets only RESULT_DBL_FORFEIT / 2 / 1; skips matches where `getResult() != RESULT_UNFINISHED`.

KNOWN-UNDONE (unchecked task box):
docs/superpowers/plans/2026-07-15-renju-pass-draw-server.md:2130 — "- [ ] Tournament plumbing (spec §9): grep tourney result recording (`TourneyMatch.RESULT_TIE=4`, live `TournamentServerTable`) and confirm a drawn renju game maps to `RESULT_TIE` where sets feed tournaments; fix mapping if a draw falls through to a win/loss."
docs/superpowers/specs/2026-07-15-renju-pass-draw-design.md:190 — defers tournament draw handling to the plan.

GIT HISTORY (no later fix):
`git log -S "setResult(winner)" -- .../CacheTBStorer.java` => only 4caa9da "d-pente fixes" (pre-Renju).
`git log -S "// != 0: not a draw"` => f7db119 (PR #12) added the comment only.
`git show e55f161 -- .../CacheTBStorer.java | grep "setResult|TourneyMatch|tourney"` => no matches (+89/-12 lines, none in the tourney block), despite its commit message listing "tournament RESULT_TIE check".
HEAD at audit: 433afe3; working tree clean for CacheTBStorer.java and dsg_src/java/org/pente/gameServer/tourney/.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259

### [GAP → CONFIRMED REAL] Drawn games actually reach the tourney-result write (not short-circuited earlier)

Confirmed reachable, not dead code. At CacheTBStorer.java:1058 `if (game.isDraw())` winnerData/loserData are still assigned (arbitrarily, by seat) so execution continues to the tourney branch at :1250 (`else if (game.getEventId() != getEventId(game.getGame()))`), which resolves the match via getUnplayedMatch(originalP1Pid, originalP2Pid, eventId) and writes result=0. A real drawn Renju tourney game will execute this line.

**Verification:** VERDICT: REAL — and the auditor understated it. I tried to refute it and instead found the reachability they confirmed is precisely what triggers a tournament-stalling bug they did not name.

The auditor's stated ITEM ("drawn games reach the tourney-result write, not dead code") is CORRECT and I confirm it: at CacheTBStorer.java:1058-1078 the `game.isDraw()` branch assigns winnerData/loserData by seat rather than returning, and there is no `return`/short-circuit anywhere between :1078 and the tourney branch at :1250 — the intervening code is only rating updates, message-text building, and two `createMessage` calls (:1228-1242). Execution reaches :1250.

WHERE THE AUDITOR STOPPED TOO SOON: they wrote "resolves the match ... and writes result=0" and treated result=0 as a benign draw encoding. It is not. `TourneyMatch.RESULT_UNFINISHED = 0` (TourneyMatch.java:25) and the draw code is `RESULT_TIE = 4` (TourneyMatch.java:29). At CacheTBStorer.java:1255-1259 `winner = game.getWinner()` is 0 for a draw, the swap-flip is deliberately skipped (`if (game.seatsSwapped() && winner != 0) { // != 0: not a draw`), and `setResult(0)` is written unmapped. A drawn Renju TB tourney game therefore persists as UNFINISHED.

THE STALL CHAIN (each link verified):
1. CacheTBStorer.java:1259 `setResult(0)` → CacheTourneyStorer.java:597 → MySQLTourneyStorer.java:794-806, which writes `set gid = ?, result = ?` — so result=0 is persisted to dsg_tournament_match while gid points at a genuinely played game.
2. TourneyMatch.java:73-75 `hasBeenPlayed() { return result != 0 || isBye(); }` → false.
3. TourneySection.java:63-72 `isComplete()` returns false if ANY match `!hasBeenPlayed()`.
4. TourneyRound.java:42-46 `isComplete()` returns false if any section incomplete.
5. CacheTourneyStorer.java:608-611, immediately after `updateMatch`, gates advancement on `t.isComplete()` / `t.getLastRound().isComplete()` — so the round never advances. The tournament hangs on the drawn match forever.

Additionally, TourneySection.java:90-101 `getUnplayedMatch()` filters on `!m.hasBeenPlayed()`, so the drawn match stays re-pairable and a later game between the same pair silently overwrites its gid/result.

REFUTATIONS I TESTED, ALL FAILED:
- Handled elsewhere? No. `RESULT_TIE` is never written by any live or TB game-completion path; grep shows it set only in SingleEliminationMatch.java:93 (internal sub-match aggregation). No path maps `getWinner()==0` to 4.
- Admin workaround? No proper one. manageTourney.jsp:41-65 exposes only `forfeitPlayers`, and TourneySection.java:116-132 maps that to RESULT_P1_WINS / RESULT_P2_WINS / RESULT_DBL_FORFEIT — never RESULT_TIE. The operator's only escape is forfeiting a player who actually drew, which falsifies standings (and drops them if the drop box is checked).
- Automatic sweep? No. TournamentServer.java:340-368 does force DBL_FORFEIT at a round cutoff — its comment even says "never persist RESULT_UNFINISHED ... a contradictory state" — but that class has zero turnBased/TB references; it is the live-room presence sweep. TB tourneys have no equivalent.
- Fixed by a later commit? No. `git log -L1244,1263` on CacheTBStorer shows the last change to that block is f7db119 (PR #12), which ADDED the `winner != 0` guard while leaving `setResult(0)` intact. TourneyMatch.java is untouched since d4951df.
- Covered by the guide? No. grep of docs/renju-integration-guide.md for RESULT_TIE / setResult / hasBeenPlayed / unplayed / draw-vs-tourney returns zero hits.

SCOPE NOTE (fairness): this is platform-wide, not Renju-only — the live path ServerTable.java:3817-3821 has the identical unmapped `setResult(localWinner2)` with the same `// != 0: not a draw` comment. But it is Renju that makes it routine rather than theoretical: PR #19 added pass moves, draw offers, and RenjuTimeoutDrawEvaluator, and f7db119 made renju tourney sets single-game, so one drawn game IS the whole match with no second game to rescue it.

CONFIDENCE / LIMITS: static analysis only, per the read-only constraint — I did not compile, run, or touch the DB. The code chain is unambiguous; what I did not empirically confirm is a live stalled tournament.

*Verifier evidence:* CacheTBStorer.java:1058-1078 (draw branch assigns winner/loser by seat, no return — reachability confirmed as auditor claimed); CacheTBStorer.java:1250-1263 (tourney branch; :1255 `int winner = game.getWinner()`; :1256 `if (game.seatsSwapped() && winner != 0) { // != 0: not a draw`; :1259 `tourneyMatch.setResult(winner)` — writes 0 for a draw, unmapped); TourneyMatch.java:25 (`RESULT_UNFINISHED = 0`); TourneyMatch.java:29 (`RESULT_TIE = 4`); TourneyMatch.java:73-75 (`hasBeenPlayed() { return result != 0 || isBye(); }`); TourneySection.java:63-72 (`isComplete()` false if any `!hasBeenPlayed()`); TourneySection.java:90-101 (`getUnplayedMatch` filters on `!hasBeenPlayed()` — drawn match stays re-pairable); TourneyRound.java:42-46 (round incomplete if any section incomplete); CacheTourneyStorer.java:597 + 608-611 (updateMatch then advancement gated on isComplete); MySQLTourneyStorer.java:794-806 (`update dsg_tournament_match set gid = ?, result = ?, forfeit = ?` — persists result=0); TourneySection.java:116-132 (forfeitPlayers maps only to RESULT_P1_WINS/RESULT_P2_WINS/RESULT_DBL_FORFEIT, never RESULT_TIE); manageTourney.jsp:41-65 (admin's only lever is forfeitPlayers/updateMatches); TournamentServer.java:340-368 (round-cutoff DBL_FORFEIT sweep, live-room only, no TB path); ServerTable.java:3817-3821 (identical unmapped draw write on the live path); SingleEliminationMatch.java:93 (only site that ever writes RESULT_TIE); git log -L1244,1263 CacheTBStorer.java → f7db119 is newest touch, added the `!= 0` guard without fixing setResult; git log TourneyMatch.java → newest d4951df, no draw fix; grep of docs/renju-integration-guide.md for RESULT_TIE/setResult/hasBeenPlayed/unplayed/draw+tourney → no matches

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1058-1076, 1250-1262

### [GAP → REFUTED] Live (non-turn-based) path handles draws

Identical defect, same shape: `boolean swapped = gridState != null && gridState.seatsSwapped(); if (swapped && localWinner2 != 0) { // != 0: not a draw ... } tourneyMatch.setResult(localWinner2);`. A drawn live Renju/Speed-Renju tourney game also records result=0. Out of the strict scope of the turn-based question, but it means the fix must land in both writers, not one.

**Verification:** The technical observation is FACTUALLY CORRECT but OUT OF SCOPE — it cannot block or degrade a Renju TURN-BASED tournament, because the live and turn-based tourney-result writers are keyed to disjoint tournaments by construction.

WHAT I CONFIRMED (the claim is accurate as a description of the LIVE path):
1. The cited code exists verbatim. ServerTable.java:3812-3823 — `int localWinner2 = localWinner; boolean swapped = gridState != null && gridState.seatsSwapped(); if (swapped && localWinner2 != 0) { // != 0: not a draw ... } tourneyMatch.setGid(...); tourneyMatch.setResult(localWinner2);`
2. `localWinner == 0` really does mean draw on this path: ServerTable.java:3802 passes `localWinner == 0` as the draw flag to `GameOverUtilities.updateGameData`, mirroring the sibling branch at :3743 which passes `localSet.getWinner() == 0`.
3. Live Renju draws really are reachable post-e55f161: `handleRenjuAcceptDraw` calls `gameOver(true, ...)` at ServerTable.java:2343-2344, and the timeout-draw branch calls `gameOver(true, ...)` at ServerTable.java:2997-2999 (guarded by `RenjuTimeoutDrawEvaluator.opponentCanWin` at :2993). Board-full draws reach :1678 / :2151 via `gameOver(gridState.getWinner() == 0, ...)`.
4. Writing 0 is genuinely wrong, not merely cosmetic: `TourneyMatch.RESULT_UNFINISHED = 0` and `RESULT_TIE = 4` (TourneyMatch.java:25,29). `hasBeenPlayed()` is `result != 0 || isBye()` (TourneyMatch.java:~80), `TourneySection.forfeitPlayers` skips anything with `result != RESULT_UNFINISHED` (TourneySection.java:116), and `SwissSection` branches on `RESULT_UNFINISHED` as a distinct standings case (SwissSection.java:237). `MySQLTourneyStorer.updateMatch` persists the int straight into `dsg_tournament_match.result` (MySQLTourneyStorer.java:800-807) with no draw normalisation. So a drawn tourney game is recorded as never played.

WHY IT IS NOT IN SCOPE FOR THE TB QUESTION (the refutation):
- There are exactly two tourney-result writers outside the tourney package: ServerTable.java:3823-3825 (live) and CacheTBStorer.java:1259-1260 (turn-based). No other non-tourney-package caller of `updateMatch` exists.
- `Tourney.isTurnBased()` is `return this.game > 50;` (Tourney.java:122-124). A Renju TB tournament is game 81 (TB_RENJU); a live Renju tournament is game 31/32.
- The live writer resolves its match via `getGameEvent(game).getEventID()` where `game` is the LIVE game id (ServerTable.java:3844-3845; same pattern in TournamentServerTable.java:331-333). It can only ever resolve a live event (31/32-derived), never the (81,'Turn-based Game') event a TB Renju tournament runs on.
- The TB writer resolves via `game.getEventId()` on the TBGame (CacheTBStorer.java:1250-1252), inside a branch explicitly gated on the game's event NOT being the default TB event and NOT being the KotH event.
- Consequence: a Renju TB tournament's matches are written exclusively by CacheTBStorer. ServerTable never touches them. A live-path defect therefore does not block or degrade starting/running a Renju TB tournament — the auditor concedes this themselves ("Out of the strict scope of the turn-based question").

WHAT IS ACTUALLY IN SCOPE: the identical defect in the TB writer at CacheTBStorer.java:1255-1259 (`int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { winner = 3 - winner; } tourneyMatch.setResult(winner);`). That one DOES affect Renju TB tournaments and should be scored as the real finding. This reported item is a correct companion note about where the eventual fix must also land, not an independent TB blocker.

NOT ALREADY FIXED: `git log -S 'RESULT_TIE'` shows only the initial commit (no later remediation), and `git log -S 'seatsSwapped() && winner != 0'` shows f7db119 plus 1ed9aad ("draw guard on TB tournament winner flip") — both of which ADDED the `!= 0` guard rather than fixing the result mapping. The guard itself is correct (flipping a draw would be meaningless); the bug is the 0-vs-RESULT_TIE mapping, which neither commit addresses. `docs/renju-integration-guide.md` contains no mention of RESULT_TIE, setResult, dsg_tournament_match, or the unfinished/draw mapping — it is entirely client-rendering oriented on this subject, so the guide neither documents nor waives this.

*Verifier evidence:* CONFIRMING THE CITED CODE (live path, accurate as described):
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:3812-3823 — `int localWinner2 = localWinner;` … `boolean swapped = gridState != null && gridState.seatsSwapped();` `if (swapped && localWinner2 != 0) { // != 0: not a draw` … `tourneyMatch.setResult(localWinner2);`
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:3802 — `localWinner == 0, k);` passed as the draw flag (proves 0 == draw)
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:3743 — sibling branch `localSet.getWinner() == 0, k);`
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:2343-2344 — `gameOver(true, playingPlayers[1].getName(), playingPlayers[2].getName(), false, false, false);` (Renju accept-draw)
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:2993-2999 — `timeoutDraw = !RenjuTimeoutDrawEvaluator.opponentCanWin(...)` then `gameOver(true, ...)`
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:1678, 2151 — `gameOver(gridState.getWinner() == 0, winner, loser, false, false, false);`
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:25 — `RESULT_UNFINISHED = 0`
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:29 — `RESULT_TIE = 4`
- dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:~80 — `hasBeenPlayed()` = `result != 0 || isBye()`
- dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:116 — `if (m.getResult() != TourneyMatch.RESULT_UNFINISHED) continue;`
- dsg_src/java/org/pente/gameServer/tourney/SwissSection.java:237 — `else if (m.getResult() == TourneyMatch.RESULT_UNFINISHED)`
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:800-807 — `"update dsg_tournament_match set gid = ?, result = ?, forfeit = ? where mid = ?"` / `stmt.setInt(2, tourneyMatch.getResult());`

REFUTING IN-SCOPE IMPACT (live and TB writers are disjoint):
- dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 — `public boolean isTurnBased() { return this.game > 50; }`
- dsg_src/java/org/pente/gameServer/server/ServerTable.java:3843-3845 — `getUnplayedMatch(newPid1, newPid2, getGameEvent(game).getEventID());` (live game id)
- dsg_src/java/org/pente/gameServer/server/TournamentServerTable.java:331-333 — `getUnplayedMatch(newPid1, newPid2, getGameEvent(game).getEventID());` (live game id)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1250-1252 — `} else if (game.getEventId() != getEventId(game.getGame())) { TourneyMatch tourneyMatch = tourneyStorer.getUnplayedMatch(game.getOriginalPlayer1Pid(), game.getOriginalPlayer2Pid(), game.getEventId());`
- Only non-tourney-package `updateMatch` call sites: ServerTable.java:3824, CacheTBStorer.java:1260, plus forfeit-only writers TournamentServer.java:364 and TournamentServerTable.java:210.

THE ACTUAL IN-SCOPE INSTANCE (separate finding, not this one):
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1255-1259 — `int winner = game.getWinner(); if (game.seatsSwapped() && winner != 0) { // != 0: not a draw \n winner = 3 - winner; } \n tourneyMatch.setResult(winner);`

NO LATER FIX:
- `git log --oneline --all -S 'RESULT_TIE' -- dsg_src/java` → only `7da6a61 Initial commit`
- `git log --oneline --all -S 'seatsSwapped() && winner != 0'` → `f7db119` (PR #12) and `1ed9aad` ("Polish: TBGame helper javadoc + draw guard on TB tournament winner flip") — both add the guard, neither changes the 0→RESULT_TIE mapping
- `grep -n 'RESULT_TIE\|setResult\|dsg_tournament_match\|unfinished' docs/renju-integration-guide.md` → no output

*Evidence:* dsg_src/java/org/pente/gameServer/server/ServerTable.java:3812-3822

### [GAP] Section/round completion when a match draws

PERMANENT STALL, no crash. hasBeenPlayed() is `result != 0 || isBye()` (TourneyMatch.java:74-76) → false for a drawn match. TourneySection.isComplete() (:63-72) returns false if any match !hasBeenPlayed(). TourneyRound.isComplete() (:42-49) requires all sections complete. AbstractTourneyFormat.isTourneyComplete() (:86-95) requires all rounds complete. So one drawn Renju game freezes the round forever: CacheTourneyStorer.checkRoundStatus() (:606-619) takes neither the completeTourney nor the createNextRound branch. Worse, the match stays visible to TourneySection.getUnplayedMatch() (:90-102) even though its game is finished and no new game is ever created.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/TourneyMatch.java:74-76; TourneySection.java:63-72; TourneyRound.java:42-49; AbstractTourneyFormat.java:86-95

### [GAP] Single-elimination advancement on a draw

Neither player advances — silent elimination of both. In SingleEliminationSection.init(), result==RESULT_UNFINISHED sets the SE match to UNFINISHED (:154-156). getWinners() (:182-189) only adds a player on bye, result==1, result==2, or RESULT_TIE (which adds BOTH). result==0 matches no branch, so the bracket slot yields zero winners. Combined with the stall above, the tournament neither advances nor errors.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/SingleEliminationSection.java:154-156, 182-189

### [GAP] Tie-replay machinery exists and is Renju/single-game aware

The replay path is already built and correct — but UNREACHABLE for draws. CacheTourneyStorer.applyMatchTo() at :547 fires `if (m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound())` → createMoreMatchesAfterTie() inserts a colour-swapped replay, and at :551 `if (!GridStateFactory.isSingleGameSet(t.getGame()))` correctly inserts only ONE replay for single-game games like Renju. This is the intended fix target: making the writer emit RESULT_TIE would light up existing, Renju-aware replay logic. Note the SE tie is currently only produced by SingleEliminationMatch.updateResult() (:83-96) when per-game win counts are equal — never by a drawn game.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:547-555; SingleEliminationFormat.java:170-195

### [OK] Renju tourney matches are single-game sets (no second game to break a draw)

Confirmed and correctly wired — but it raises the severity of the draw bug. Because a Renju match is ONE game (not the classic 2-game colour-balanced set), a drawn game is the entire match result. There is no companion game whose win could break the tie, so the drawn match cannot self-resolve; it is fully dependent on the RESULT_TIE replay path that never triggers.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:492, 551; SingleEliminationFormat.java:111; DoubleEliminationFormat.java:261 (all via GridStateFactory.isSingleGameSet)

### [GAP] Round-robin standings with draws (points computation)

No half-point concept, and a draw is double-counted as unplayed. init() does `results[i][j*3] = match.getResult()`, then `if (results[i][j*3] != 0) { totalGames[i]++; totalGames[j]++; }` (:160-163) — a drawn game (0) is NOT counted as played, so possibleWins (`wins[i] + (numGames - totalGames[i])`, :179) stays inflated and the winner-determination loop (:195-208) waits indefinitely for a game that will never be replayed. Wins are integer-only (`if (...==1) wins[i]++; else if (...==2) wins[j]++;`, :164-165). Even if the draw were correctly stored as 4, both players would score 0 for that game rather than 0.5 — there is no half-point anywhere in the codebase.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/RoundRobinSection.java:156-166, 176-193

### [GAP] Swiss standings with draws

Same shape. SwissSection.init() handles only DBL_FORFEIT, UNFINISHED, 1, and 2 (:234-253) — no tie branch, so a drawn game increments neither player's wins. TourneyPlayerData carries only matchWins, opponentWins, matchLosses, numByes, numForfeits (:18-22) with integer increments (:88-94) — there is no draws counter and no fractional score field. Swiss pairing/ranking (getPlayersRanked, used by getWinners at :266-270) therefore cannot express the standard Swiss half-point a draw should award.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/SwissSection.java:234-253; TourneyPlayerData.java:18-22, 76-102

### [OK] Any assert/exception/crash on a drawn tourney game

No crash — and that is the danger. The failure is entirely silent. updateMatch does `stmt.setInt(2, tourneyMatch.getResult())` with 0 into enum('1','2','3','4'); reading back, DBUtil.enumInt returns 0 for both NULL and empty string (:18-20), so the value round-trips to 0 regardless of how MariaDB stores index 0. Independently, the in-memory Redis aggregate applies result=0 directly (CacheTourneyStorer.java:527), so the stall occurs even if the DB write were rejected. No exception, no log ERROR, no admin notification — the tournament just stops progressing.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:800-808; dsg_src/java/org/pente/database/DBUtil.java:16-21

### [GAP] Operator recovery path for a drawn match

A workaround exists but mis-records the game. manageTourney.jsp exposes ONLY forfeit/drop checkboxes (:130-141) calling forfeitPlayers (:61-62); there is no 'set match result' or 'declare tie' control anywhere in the admin JSPs (manageTourney, markTourneyGame, addTourneyMatch). forfeitPlayers only ever assigns 1, 2, or DBL_FORFEIT (TourneySection.java:127-135). It does gate on `if (m.getResult() != RESULT_UNFINISHED) continue;` (:116), and since a drawn match sits at 0 it IS still forfeitable — so the operator can unstick the tournament, but only by recording a legitimate draw as a loss or double forfeit.

*Evidence:* dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:41-65, 130-141; dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:111-149

### [OK] dsg_tournament_results table cannot encode a tie

Not on the live path, so not a blocker. The table's enum has no tie value, but grep shows the modern tourney engine never reads or writes it — MySQLTourneyStorer touches dsg_tournament_match only (:801-803). Its only references are the archived JSPs under httpdocs/gameServer/tournaments/old/ and the legacy admin addTourneyMatch.jsp insert. Flagging so it is not mistaken for a second required migration.

*Evidence:* dsg_src/sql/schema.sql:359 (`result` enum('0','1','2','3')); dsg_src/httpdocs/gameServer/tournaments/old/tournament4Results.jsp:237, tournament5Results.jsp:215; addTourneyMatch.jsp:51

### [GAP] Test coverage for drawn tournament games

None. The tourney test directory holds CacheTourneyStorerRedisTest, DoubleEliminationDriver, SwissDriver, InMemoryTourneyStorer. The single RESULT_TIE reference is in DoubleEliminationDriver.java:64, a manual simulation driver, not an assertion-bearing test. CacheTourneyStorerRedisTest only round-trips RESULT_P1_WINS (:166, :174). No test asserts what happens when a game ends in a draw, which is why the regression shipped with the Renju merge.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/test/ (4 files); only match is DoubleEliminationDriver.java:64

### [GAP] Living guide documents Renju draw handling in tournaments

The guide covers Renju draws and the Taraguchi-10 opening extensively, but its only 'tournament' hits are maskTournamentOpening / OpeningMask discussion — an unrelated use of the word. There is no section on tournament scoring, standings, or draw handling, so an operator following the guide has no warning that starting a Renju tournament risks a permanent stall.

*Evidence:* docs/renju-integration-guide.md — grep for 'tourney|tournament' returns only :545, :546, :650 (all about the opening mask)

## Tourney-facing JSP/UI for Renju (TB_RENJU = 81)
Area verdict: **gaps**

### [OK] Admin can create a Renju TB tournament (game dropdown includes game=81)

newTourney.jsp:117 iterates GridStateFactory.getAllGames() and emits value=games[i].getId() (line 120) with label GridStateFactory.getDisplayName(games[i].getId()). allGames includes TB_RENJU_GAME (GridStateFactory.java:153) and displaygames includes new Game(TB_RENJU, "Turn-based Renju", false) (line 187), so the option renders as 'Turn-based Renju' with value 81. The dropdown is data-driven, not hardcoded — no missing entry. The posted value is read at newTourney.jsp:24 (Integer.parseInt(request.getParameter("game"))) and stored via tourney.setGame(game) (line 29).

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:116-123; dsg_src/java/org/pente/game/GridStateFactory.java:153,187,430-432,434-441

### [OK] Game id 81 resolves to a display name everywhere tourney pages look it up

Two lookup paths, both correct for 81. (a) GridStateFactory.getGameName(81) -> getGame(81) -> tbGames[(81-50-1)/2] = tbGames[15] = TB_RENJU_GAME -> "Renju" (GridStateFactory.java:392-398,137,153). Used by statusRound.jsp:23 and tournamentConfirm.jsp:102/113/123. (b) Tourney.getGameName() (Tourney.java:90-92) delegates to GridStateFactory.getDisplayName(81) -> "Turn-based Renju" (GridStateFactory.java:187,434-441). Used by tourneyDetails.jsp:16 and tournamentConfirm.jsp:83. No null and no array-index error.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/Tourney.java:90-92; dsg_src/java/org/pente/game/GridStateFactory.java:392-398,404-406,434-441; dsg_src/httpdocs/gameServer/tournaments/tourneyDetails.jsp:16; statusRound.jsp:23; tournamentConfirm.jsp:83,102,113,123

### [OK] Tournament list / detail / signup / confirm pages render a game=81 tourney

All four are game-agnostic. index.jsp:120-127 loops currentT/signup generically and labels via t.isTurnBased(). tournamentConfirm.jsp uses dsgPlayerData.getPlayerGameData(tourney.getGame()) (lines 15/65/227) — requires a rating row for game 81, which comes from the boot game_event registration path, not from these JSPs. tournamentSignup.jsp never touches the game id at all.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/index.jsp:119-127; tourneyDetails.jsp:13-51; tournamentConfirm.jsp:15,65,83,227; tournamentSignup.jsp:22-52; status.jsp:10-40

### [OK] Playing a tourney Renju TB game uses the existing Renju-capable tb/ path

There is NO tourney-specific game JSP. Every status format includes ../tb/listedMobileGame.jsp, which is already Renju-aware: p1Black includes TB_RENJU (listedMobileGame.jsp:67) and a TB_RENJU branch at line 73. That tile links into the same tb/mobileGame.jsp that carries the full Taraguchi-10 UI (renjuPhase/renjuOffers export at mobileGame.jsp:130-142; decision gating at 309-319 using TBGame.RENJU_SWAP/RENJU_BRANCH/RENJU_SELECTION/RENJU_COMPLETE). gameConstants.jspf exposes GAME.RENJU/SPEED_RENJU/TB_RENJU to gameScript.js. Board size is server-derived (GridStateFactory.java:372-378 returns new RenjuState(15,15)).

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:146; statusSwiss.jsp:136,162; statusSingleElim.jsp:76,87; statusDoubleElim.jsp:71,82; dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp:65-73; tb/mobileGame.jsp:125-142,309-319; dsg_src/httpdocs/gameServer/gameConstants.jspf (RENJU/SPEED_RENJU/TB_RENJU present)

### [GAP → CONFIRMED REAL] Round-robin status page renders an active Renju round without error

BLOCKING for the round-robin format. statusRoundRobin.jsp:156 calls s.getGame2().getState() with NO null guard. For Renju, CacheTBStorer.createTournamentSet leaves tbg2 = null because GridStateFactory.isSingleGameSet(TB_RENJU) is true (GridStateFactory.java:506), and TBSet.getGame2() simply returns games[1] (TBSet.java:79-81) — i.e. null. Rendering statusRound.jsp for a round-robin Renju tourney with any active set therefore throws NullPointerException and falls through to errorPage="../../five00.jsp" (statusRound.jsp:2). Contrast the sibling formats, which ARE guarded: statusSwiss.jsp:149-150 (game = s.getGame2(); if (game != null && ...)), statusSingleElim.jsp:85-86, statusDoubleElim.jsp:80-81. Note this also affects TB Go tourneys, so it is a latent pre-existing bug that Renju newly exposes.

**Verification:** CONFIRMED — I could not refute it. Every link in the auditor's causal chain verified, and I found two pieces of corroborating evidence the auditor did not cite.

WHAT I VERIFIED (attempting refutation at each step):

1. The unguarded dereference is real and unconditionally reached. statusRoundRobin.jsp:156 reads `s.getGame2().getState()`. I did not take this on faith — I ran a brace-depth trace over the file's scriptlets. Result: the scriptlet opening at line 153 enters at depth 7 and the `if (s.getGame2()...)` test sits at that same depth, i.e. inside `if (m1 != null || m2 != null)` -> `for (TBSet s : sets)` -> `if (game1.getEventId()==eid && s.getState()==ACTIVE && game1.getPlayer2Pid()==m.getPlayer2().getPlayerID())` (lines 136-141). The preceding `if (s.getGame1()...)` block closes at line 148 and the `if (r==3)` block closes at line 153. So getGame2() is dereferenced for EVERY active set matching the tourney event — there is no isTwoGameSet() or null test anywhere in the file (grep for getGame2/isTwoGameSet returns only line 156-157).

2. games[1] really is null on the LOAD path, not just at creation. This was my strongest refutation candidate — the JSP reads sets from tbStorer.loadSets(), not from the object createTournamentSet built, so a load path that always populates two games would have refuted the claim. It does not. MySQLTBGameStorer.java:95-96 persists `gid2 = getGames()[1] != null ? ... : 0`, so single-game sets store gid2=0. loadSets joins `s.gid1 = g.gid or s.gid2 = g.gid` (:778,:786), yielding exactly one row, and the single addGame() call site (:864) fills games[0] first (TBSet.java:67-73), leaving games[1] null. Redis path delegates to the same baseStorer (CacheTBStorer.java:1549-1581) and serializes the null through.

3. The path is reachable by an operator, and is in fact the DEFAULT. Tourney.isTurnBased() is `game > 50` (Tourney.java:122-124); TB_RENJU = TB_START(50) + RENJU(31) = 81, so the TB block at statusRoundRobin.jsp:120 executes. TB_RENJU is a registered game (GridStateFactory.java:137,153,187) so it appears in the newTourney.jsp game dropdown. Critically, newTourney.jsp:175-179 renders `<select name="format">` with `<option value="1">Round-Robin` FIRST and no `selected` attribute on any option — browsers select the first option, so Round-Robin is what an operator gets unless they deliberately change it (format==1 -> new RoundRobinFormat(), newTourney.jsp:50-51).

4. Sets ARE created for Renju TB tourney matches. CacheTourneyStorer.java:487-496: insertMatch calls tbStorer.createTournamentSet when `t.getGame() > 50` and (pid ordering OR isSingleGameSet) — the isSingleGameSet clause at :492 exists precisely so single-game (Renju/Go) matches create one set per pairing. CacheTBStorer.java:2199-2201 then leaves tbg2 null because GridStateFactory.isSingleGameSet(TB_RENJU) is true (GridStateFactory.java:502-507, which explicitly lists RENJU/SPEED_RENJU/TB_RENJU with a comment saying Renju balances via Taraguchi-10 swap so it plays one game per set).

5. Failure mode is a 500 page, not degraded rendering. statusRound.jsp:2 sets errorPage="../../five00.jsp" (that file exists). The NPE aborts the whole page, including the results grid rendered above it.

6. No later fix exists. `git log --all -- statusRoundRobin.jsp` stops at 0aab6dd "formatting"; the newest functional change is c95d9e0 "Round Robin view TB games" — the commit that INTRODUCED this block. `git log --all -S"s.getGame2().getState()"` returns only c95d9e0. The guide docs/renju-integration-guide.md (1479 lines) contains no round-robin, statusRound, or tournament-status material at all — its only "tournament" hits are about the iOS opening-mask enum.

CORROBORATION THE AUDITOR MISSED (strengthens the finding):
f7db119 — the very commit titled "renju single-game tournament sets" — patched SingleEliminationFormat.java:111, DoubleEliminationFormat.java:261, CacheTourneyStorer.java:492,551 and CacheTBStorer.java:2200 for single-game sets, but touched NEITHER RoundRobinFormat.java NOR statusRoundRobin.jsp (full file list in `git show --stat f7db119`). grep for isSingleGameSet across the tourney package returns zero hits in RoundRobinFormat.java. So round-robin was systematically skipped by the Renju single-game-set work — the JSP crash is the visible symptom of that omission, and RoundRobinFormat itself may have further single-game gaps beyond the UI.

SEPARATE (lesser) GAP FOUND: statusRound.jsp:16-18 defines `isTBSingleGame` as TB_GO || TB_GO9 || TB_GO13 — TB_RENJU is NOT included. It is consumed at statusSingleElim.jsp:72,80 and statusDoubleElim.jsp:67,75 to collapse the two-game columns. For a Renju single/double-elim tourney this renders a spurious extra empty result column with wrong colspan. Cosmetic only, not a crash, because those files DO null-guard the game (statusSingleElim.jsp:85-86, statusDoubleElim.jsp:80-81).

SCOPE HONESTY: this does not block ALL Renju TB tournaments — Swiss, Single-Elim and Double-Elim status pages are null-guarded and render fine. It blocks the Round-Robin format specifically. Because Round-Robin is the default-selected format in the creation form, an operator who does not know to avoid it will produce a tournament whose status page 500s for the entire duration of any round with active games. That genuinely degrades running a Renju TB tournament. The auditor's note that TB Go is equally affected is correct and does not reduce severity — it means Renju newly exposes a latent bug that no one hit because Go TB round-robin tourneys were evidently never run.

*Verifier evidence:* PRIMARY DEFECT (unguarded deref):
- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:156-157 — `if (s.getGame2().getState() == TBGame.STATE_ACTIVE) { TBGame game = s.getGame2();` with no null test
- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:120 — `if (tourney.isTurnBased()) {` gates the block
- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:136-141 — enclosing filter (m1/m2 non-null; game1 eventId==eid; set ACTIVE; opponent match) — brace-depth trace shows :156 sits inside this, at the same depth as the guarded :142 sibling
- dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:2 — errorPage="../../five00.jsp" (file exists: dsg_src/httpdocs/five00.jsp)
- dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:75-76 — dispatch `if (tourney.getFormat() instanceof RoundRobinFormat) { include statusRoundRobin.jsp }`

WHY getGame2() IS NULL (creation AND load):
- dsg_src/java/org/pente/game/GridStateFactory.java:502-507 — isSingleGameSet() returns true for RENJU/SPEED_RENJU/TB_RENJU
- dsg_src/java/org/pente/game/GridStateFactory.java:61,77 — TB_START=50; TB_RENJU = TB_START + RENJU = 81
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2199-2201 — `TBGame tbg2 = null; if (!GridStateFactory.isSingleGameSet(game)) { tbg2 = new TBGame(); ... }`
- dsg_src/java/org/pente/turnBased/TBSet.java:14 — `private TBGame games[] = new TBGame[2];`
- dsg_src/java/org/pente/turnBased/TBSet.java:67-73 — addGame() fills games[0] first, games[1] only on second call
- dsg_src/java/org/pente/turnBased/TBSet.java:79-81 — `getGame2() { return games[1]; }` (no guard, unlike getGame(:97-101)/getOtherGame(:103-107)/isCompleted(:170-179)/isTwoGameSet(:181-183) which all null-check games[1])
- dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:95-96 — `long gid2 = tbSet.getGames()[1] != null ? tbSet.getGames()[1].getGid() : 0;` persists gid2=0
- dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:778,786 — loadSets join `and (s.gid1 = g.gid or s.gid2 = g.gid)` -> one row for single-game sets
- dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:864 — the ONLY addGame() call site in the package (grep-verified)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1549-1581 — Redis loadSets delegates to baseStorer.loadSets; null survives serialization

REACHABILITY:
- dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 — `isTurnBased() { return this.game > 50; }` (81 > 50)
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:487-496 — insertMatch -> createTournamentSet when game>50 and (pid order OR isSingleGameSet(:492))
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:175-179 — `<select name="format">` with `<option value="1">Round-Robin` first and NO selected attribute => default
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:49-51 — `format == 1 -> tourney.setFormat(new RoundRobinFormat())`
- dsg_src/java/org/pente/game/GridStateFactory.java:137,153,187 — TB_RENJU_GAME registered, so it appears in the newTourney game dropdown
- link paths to statusRound.jsp: dsg_src/httpdocs/gameServer/tournaments/index.jsp:124, status.jsp:37, playTemplate.jspf:88

SIBLING FORMATS ARE GUARDED (proves this is an omission, not a convention):
- dsg_src/httpdocs/gameServer/tournaments/statusSwiss.jsp:149-150 — `game = s.getGame2(); if (game != null && ...)`
- dsg_src/httpdocs/gameServer/tournaments/statusSingleElim.jsp:85-86 — `TBGame game = matchSet.getGame2(); if (game != null && ...)`
- dsg_src/httpdocs/gameServer/tournaments/statusDoubleElim.jsp:80-81 — same pattern

NO LATER FIX:
- `git log --all --oneline -- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp` -> 0aab6dd, 73729d4, 6ec2aa8, c95d9e0 ("Round Robin view TB games" = introduced it), a53b84f, 7da6a61
- `git log --all --oneline -S"s.getGame2().getState()" -- dsg_src/httpdocs/` -> only c95d9e0
- `git show --stat f7db119` ("renju single-game tournament sets") — modifies SingleEliminationFormat.java, DoubleEliminationFormat.java, CacheTourneyStorer.java, CacheTBStorer.java; does NOT touch RoundRobinFormat.java or statusRoundRobin.jsp
- grep isSingleGameSet across dsg_src/java/org/pente/gameServer/tourney/ -> DoubleEliminationFormat.java:261, SingleEliminationFormat.java:111, CacheTourneyStorer.java:492,551 — zero hits in RoundRobinFormat.java
- docs/renju-integration-guide.md (1479 lines) — no round-robin / statusRound / tournament-status coverage

SECONDARY GAP:
- dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:16-18 — `boolean isTBSingleGame = tourney.getGame() == GridStateFactory.TB_GO || ... TB_GO9 || ... TB_GO13;` omits TB_RENJU; consumed at statusSingleElim.jsp:72,80 and statusDoubleElim.jsp:67,75 (cosmetic column/colspan error for Renju, no crash)

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:156-157; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2185-2213; dsg_src/java/org/pente/turnBased/TBSet.java:79-81,181-183; dsg_src/java/org/pente/game/GridStateFactory.java:502-507

### [GAP → REFUTED] statusRound.jsp single-game-set detection covers Renju

statusRound.jsp:16-18 hardcodes isTBSingleGame = (TB_GO || TB_GO9 || TB_GO13) instead of calling the canonical helper GridStateFactory.isSingleGameSet(tourney.getGame()), which was extended to cover RENJU/SPEED_RENJU/TB_RENJU (GridStateFactory.java:506, with the Taraguchi-10 rationale in the javadoc at lines 496-501). Consequence for a Renju single/double-elim tourney: isTBSingleGame is false, so statusSingleElim.jsp:72 emits colspan="2" instead of "4" and lines 80-84 open a second <td colspan="2"> for the non-existent game2 (same at statusDoubleElim.jsp:67,75). Cosmetic only — the game2 block is null-guarded — but the bracket table renders with a stray empty column.

**Verification:** The code inconsistency is REAL and confirmed, but it does not block or meaningfully degrade running a Renju turn-based tournament, and the described consequence is overstated. Verdict: not a gap for this audit question.

WHAT I CONFIRMED (the factual core of the report is accurate):
1. statusRound.jsp:16-18 does hardcode `isTBSingleGame = (TB_GO || TB_GO9 || TB_GO13)` and never calls the canonical helper. GridStateFactory.isSingleGameSet does cover Renju (GridStateFactory.java:506, Taraguchi-10 rationale in javadoc at 496-501). So for a TB_RENJU (81) tourney, isTBSingleGame is false today.
2. The variable is defined exactly once and flows into the two includes that consume it: statusRound.jsp:77-80 pulls in statusDoubleElim.jsp / statusSingleElim.jsp, which read it at statusSingleElim.jsp:72,80 and statusDoubleElim.jsp:67,75.
3. No later fix exists. `git log` over the three JSPs stops at d05c65c "swap2 keryo initial commit" — none of the Renju commits touched them. `git log -S isSingleGameSet -- dsg_src/` returns only f7db119, which added the helper to Java and wired the Java callers (SingleEliminationFormat.java:111, DoubleEliminationFormat.java:261, CacheTourneyStorer.java:492,551, CacheTBStorer.java:2200) but never the JSP. The guide docs/renju-integration-guide.md does not mention statusRound.jsp or the single-game-set flag at all.
4. Not an exception: CacheTBStorer.createTournamentSet leaves tbg2 null for single-game-set games (CacheTBStorer.java:2199-2211), so TBSet.getGame2() (TBSet.java:79-81) returns null and the `game != null` guard at statusSingleElim.jsp:86 / statusDoubleElim.jsp:81 holds. No NullPointerException, no HTTP 500.

WHERE THE REPORT IS WRONG — the "stray empty column" claim does not hold:
The table's column count is unchanged either way. The header is 4 columns (statusSingleElim.jsp:11-14, 25% each). In the games row:
- intended path (isTBSingleGame true): one `<td colspan="4">` = 4 columns
- Renju path today (false): `<td colspan="2">` at :72 plus `<td colspan="2">` at :82 = 4 columns
Totals are identical, so nothing misaligns against the header or the player rows. The only real artifact is one empty bordered cell filling the right half of the games row, with the Renju miniature centred over the left half instead of the full width. That is a centring nit, not a broken bracket.

SCOPE IS ALSO NARROWER THAN IMPLIED: only single- and double-elimination brackets read the flag. statusRoundRobin.jsp and statusSwiss.jsp never reference isTBSingleGame, so a Renju round-robin or Swiss tourney is completely unaffected.

WHY IT IS STILL WORTH A ONE-LINE FIX (but not a blocker): the identical go-only triple elsewhere WAS swept during the Renju work — listedMobileGame.jsp:67 and viewLiveGameMobile.jsp:151 both got `|| GridStateFactory.TB_RENJU` appended — so statusRound.jsp:16-18 is a genuine oversight, not a deliberate exclusion. Replacing lines 16-18 with `GridStateFactory.isSingleGameSet(tourney.getGame())` is a safe, self-contained change. But an operator can start a Renju TB tournament, have pairings generated as single-game sets, and have players play them today; the bracket page renders without error. It fails the "genuinely blocks or degrades" bar.

*Verifier evidence:* CONFIRMED HARDCODE (report accurate here):
- dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:16-18 — `boolean isTBSingleGame = tourney.getGame() == GridStateFactory.TB_GO || tourney.getGame() == GridStateFactory.TB_GO9 || tourney.getGame() == GridStateFactory.TB_GO13;` (no Renju, no helper call)
- dsg_src/java/org/pente/game/GridStateFactory.java:496-507 — javadoc "renju (Taraguchi-10) balances it via the opening swap protocol. Both therefore play one game per tournament set."; :506 `game == RENJU || game == SPEED_RENJU || game == TB_RENJU;`

CONSUMPTION PATH:
- statusRound.jsp:75-83 — format dispatch: :76 includes statusRoundRobin.jsp, :78 statusDoubleElim.jsp, :80 statusSingleElim.jsp, :82 statusSwiss.jsp
- dsg_src/httpdocs/gameServer/tournaments/statusSingleElim.jsp:72 `<td colspan="<%=(isTBSingleGame?"4":"2")%>">`; :80 `<% if (!isTBSingleGame) { %>`; :82 `<td colspan="2" align="center">`; :85 `TBGame game = matchSet.getGame2();`
- dsg_src/httpdocs/gameServer/tournaments/statusDoubleElim.jsp:67, :75, :77, :80 — same pattern
- Only these three files reference isTBSingleGame (repo-wide grep); statusRoundRobin.jsp and statusSwiss.jsp do not.

COLSPAN MATH REFUTING "stray empty column":
- statusSingleElim.jsp:11-14 — four `<th width="25%">` (Player 1 / Result / Player 2 / Score) = 4-column table
- true branch: statusSingleElim.jsp:72 emits one td colspan=4 → 4
- false branch (Renju today): :72 td colspan=2 + :82 td colspan=2 → 4
Row width identical; no misalignment.

NO EXCEPTION (null-safe):
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2199-2212 — `TBGame tbg2 = null; if (!GridStateFactory.isSingleGameSet(game)) { ... } TBSet tbs = new TBSet(tbg1, tbg2);`
- dsg_src/java/org/pente/turnBased/TBSet.java:79-81 — `public TBGame getGame2() { return games[1]; }`
- statusSingleElim.jsp:86 / statusDoubleElim.jsp:81 — `if (game != null && !game.isCompleted() && !game.isHidden())`

NO LATER FIX:
- `git log --oneline -- statusRound.jsp statusSingleElim.jsp statusDoubleElim.jsp` → newest is d05c65c "swap2 keryo initial commit"; f7db119, e55f161, c453ace absent
- `git log -S "isSingleGameSet" -- dsg_src/` → only f7db119 (Java-side only)
- docs/renju-integration-guide.md — no match for statusRound / isTBSingleGame / isSingleGameSet

EVIDENCE IT IS AN OVERSIGHT, NOT DELIBERATE (same triple swept elsewhere):
- dsg_src/httpdocs/gameServer/tb/listedMobileGame.jsp:67 — `... || game.getGame() == GridStateFactory.TB_RENJU;` and :73 `else if (game.getGame() == GridStateFactory.TB_RENJU)`
- dsg_src/httpdocs/gameServer/viewLiveGameMobile.jsp:151 — `... || gameId == GridStateFactory.TB_RENJU;`

JAVA CALLERS ALREADY CORRECT (tournament mechanics unaffected):
- dsg_src/java/org/pente/gameServer/tourney/SingleEliminationFormat.java:111
- dsg_src/java/org/pente/gameServer/tourney/DoubleEliminationFormat.java:261
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:492, :551
- dsg_src/java/org/pente/game/test/GridStateFactorySingleGameSetTest.java:27,36,46

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRound.jsp:16-18; dsg_src/java/org/pente/game/GridStateFactory.java:502-507; statusSingleElim.jsp:72,80; statusDoubleElim.jsp:67,75

### [GAP → REFUTED] Round-robin set lookup survives a Renju Taraguchi-10 seat swap

statusRoundRobin.jsp:141 matches the set by raw physical pid: s.getGame1().getPlayer2Pid() == m.getPlayer2().getPlayerID(). Renju's opening protocol physically flips player1Pid/player2Pid — that is exactly why PR #12 added TBGame.seatsSwapped()/getOriginalPlayer1Pid()/getOriginalPlayer2Pid() (TBGame.java:614,633-639). Those helpers are currently called from ONE place, CacheTBStorer.java:1252, and from no JSP (grep for getOriginalPlayer[12]Pid across dsg_src/java + dsg_src/httpdocs returns only the definition and that single caller). So after a swap the round-robin tile lookup can fail to match and the in-progress game simply will not be listed on the round page. The elimination/Swiss formats are unaffected because they match on getEventId() only (statusSingleElim.jsp:61, statusDoubleElim.jsp:55, statusSwiss.jsp:124).

**Verification:** REFUTED as stated. The auditor's premise — that round-robin matches the set by one raw ordered pid and therefore loses the game after a Renju seat flip — is wrong because round-robin enumerates BOTH orderings of every pair, which makes the lookup swap-invariant.

The refutation chain:

1. `RoundRobinFormat.java:94-110` builds matches with a nested j/k loop over the section's players, skipping only j==k. It creates a TourneyMatch for (A,B) AND for (B,A), unconditionally — no `isSingleGameSet` check. This is confirmed independently by `RoundRobinSection.getNumPlayers()` (RoundRobinSection.java:44-60), which maps 2 matches→2 players, 6→3, 12→4, 20→5, 30→6, i.e. n·(n−1) = ordered pairs; and by `getResultsMatrix()`'s 6-columns-per-opponent stride (2 games × 3 columns).

2. Both ordered matches are persisted. `CacheTourneyStorer.insertRound` calls `insertMatch(m)` for every match in the round; only the *set creation* inside `insertMatch` is conditional (CacheTourneyStorer.java:~490-497), not the match row itself. So `section.getMatches()` in the JSP contains both orderings for Renju.

3. `loadSets(pid)` is seat-agnostic: `MySQLTBGameStorer.java:775-785` selects `where (s.p1_pid = ? or s.p2_pid = ?)`. Critically, the SET-level p1/p2 (`CacheTBStorer.java:2213-2214`) are assigned at creation and are never touched by `renjuSwap` — `TBGame.renjuSwap` (TBGame.java:575-592) flips only the GAME's player1Pid/player2Pid. So a swapped set is still returned for both participants.

4. Therefore the JSP predicate at statusRoundRobin.jsp:139-141 is a total cover. For any active set S of pair {A,B}, `S.getGame1().getPlayer2Pid()` is always one of {A,B} (swapped or not). The unique match satisfying both `m.p2 == S.game1.player2Pid` and `S ∈ loadSets(m.p1)` is m = (other, S.game1.player2Pid) — which always exists in the round-robin list. Worked example with A=11, B=22 and setAB swapped (physical p1=22, p2=11): iteration m=(A,B) misses, iteration m=(B,A) hits (`game1.player2Pid`==11==m.p2). The game still renders, exactly once. Every swap combination I traced (neither swapped, one swapped, both swapped) yields each active game rendered exactly once — no omissions, no duplicates. The `m1 != null || m2 != null` guard at :134-136 is itself symmetric (`getUnplayedMatch` is checked in both directions), so it never gates one ordering out.

The auditor is factually right that `getOriginalPlayer1Pid`/`getOriginalPlayer2Pid` have exactly one caller (CacheTBStorer.java:1252) and zero JSP callers — but that absence is harmless here, because round-robin's double enumeration already provides swap invariance. The elimination formats need `isSingleGameSet` awareness precisely because they suppress the mirrored match (SingleEliminationFormat.java:111, DoubleEliminationFormat.java:261); round-robin does not suppress it, so it is safe.

SEPARATE, MORE SEVERE FINDING at nearly the same lines (different mechanism, not what was reported, and NOT dependent on any swap): statusRoundRobin.jsp:156 dereferences `s.getGame2().getState()` with no null guard, inside the matched-set block (braces: the `if` opened at :139 closes at :170, so :156 is reached for every matching set). For Renju, `GridStateFactory.isSingleGameSet` returns true for TB_RENJU (GridStateFactory.java:502-507), so `CacheTBStorer.createTournamentSet` leaves `TBGame tbg2 = null` (CacheTBStorer.java:2199-2211) and builds `new TBSet(tbg1, null)` (:2212), which sets `games[1] = null` (TBSet.java:52-56). On reload, `MySQLTBGameStorer.loadSets(Connection,PreparedStatement)` (:835-874) only calls `addGame` per joined tb_game row, so a single-game set leaves `games[1]` null and `getGame2()` returns null (TBSet.java:79-81). Result: NullPointerException on the first matching active set — a 500 on the tournament round page for any Renju (or Go) turn-based round-robin, from the moment the first set is created. The codebase already has the correct guard, `TBSet.isTwoGameSet()` (TBSet.java:181-183), and the admin JSPs use it (admin/tb/player.jsp:34, admin/tb/all.jsp:33); statusRoundRobin.jsp does not. statusSwiss.jsp:149 and statusSingleElim.jsp:85 / statusDoubleElim.jsp:80 also call getGame2() and may share this exposure — I did not trace their guarding, so treat those three as unclear rather than confirmed.

Net answer to the audited question: the swap-lookup gap is not real and does not block a Renju TB round-robin tournament. A null-game2 NPE at statusRoundRobin.jsp:156 does appear to block it, and should be re-filed as its own item. Note the guide docs/renju-integration-guide.md contains no mention of round-robin, statusRoundRobin, single-game sets, or getOriginalPlayer* (grep returned nothing), so neither issue is documented there.

*Verifier evidence:* REFUTING EVIDENCE (reported gap):
- dsg_src/java/org/pente/gameServer/tourney/RoundRobinFormat.java:94-110 — nested j/k loop, `if (j == k) continue;`, then setPlayer1(p1)/setPlayer2(p2) and addMatch for every ordered pair; no isSingleGameSet check
- dsg_src/java/org/pente/gameServer/tourney/RoundRobinSection.java:44-60 — getNumPlayers(): 2→2, 6→3, 12→4, 20→5, 30→6 = n·(n−1) ordered matches
- dsg_src/java/org/pente/gameServer/tourney/TourneySection.java:90-102 — getUnplayedMatch(p1,p2) is strictly ordered, which is why the JSP checks both directions
- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:133-136 — loop over all matches; symmetric guard `m1 != null || m2 != null`
- dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:775-785 — loadSets: `where (s.p1_pid = ? or s.p2_pid = ?)` (seat-agnostic)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2213-2214 — set-level p1/p2 fixed at creation
- dsg_src/java/org/pente/turnBased/TBGame.java:575-592 — renjuSwap flips only the GAME pids, not the set's
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:~490-497 — insertMatch creates a set when (p1<p2 || isSingleGameSet); match ROW insertion is unconditional
- Contrast (why elim formats differ): SingleEliminationFormat.java:111, DoubleEliminationFormat.java:261 — these suppress the mirrored match for single-game sets; RoundRobinFormat does not

SUPPORTING EVIDENCE (separate NPE finding):
- dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:156-157 — `if (s.getGame2().getState() == TBGame.STATE_ACTIVE)` unguarded, inside the :139-:170 matched-set block
- dsg_src/java/org/pente/game/GridStateFactory.java:502-507 — isSingleGameSet includes RENJU, SPEED_RENJU, TB_RENJU (and the go family)
- dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2199-2212 — `TBGame tbg2 = null; if (!isSingleGameSet(game)) {...}` then `new TBSet(tbg1, tbg2)`
- dsg_src/java/org/pente/turnBased/TBSet.java:52-56, 79-81 — games[1] = game2; getGame2() returns games[1]
- dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:835-874 — loadSets(con,stmt) calls addGame once per joined tb_game row; single-game set leaves games[1] null
- dsg_src/java/org/pente/turnBased/TBSet.java:181-183 — `isTwoGameSet() { return games[1] != null; }` (the guard that exists but is unused here)
- dsg_src/httpdocs/gameServer/admin/tb/player.jsp:34 and dsg_src/httpdocs/gameServer/admin/tb/all.jsp:33 — `if (s.isTwoGameSet()) games.add(s.getGame2());` (correct pattern)
- UNCLEAR / untraced: statusSwiss.jsp:149, statusSingleElim.jsp:85, statusDoubleElim.jsp:80 also call getGame2(); guarding not verified
- docs/renju-integration-guide.md — grep for round.robin|statusRoundRobin|single-game set|singleGameSet|getOriginalPlayer returned no matches

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:139-141; dsg_src/java/org/pente/turnBased/TBGame.java:614,633-639; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1252

### [GAP] Completed-tournament highlights grid on tournaments/index.jsp includes Renju

The 'last completed TB tourney per game' grid is a hardcoded list of getLastTBTourney(...) calls for TB_GOMOKU/TB_KERYO/TB_BOAT_PENTE/TB_DPENTE/TB_CONNECT6/TB_POOF_PENTE/TB_DKERYO/TB_GPENTE/TB_GO/TB_GO9/TB_GO13/TB_OPENTE/TB_SWAP2PENTE/TB_SWAP2KERYO (index.jsp:73-89). TB_RENJU is absent, so a finished Renju tourney will never appear in that grid. Cosmetic and post-completion only — the live 'current/upcoming' listing at index.jsp:119-127 is fully generic and will show a Renju tourney. Caveat when fixing: the grid dereferences these Tourney vars unguarded (e.g. index.jsp:209 tbPenteOpen.getEventID()), so adding a Renju cell needs a null check until a Renju tourney has actually completed.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/index.jsp:73-91 (grep 'RENJU' in that file returns 0 matches); index.jsp:203-260

### [OK] Tourney admin JSPs contain no hardcoded game dropdown missing Renju

newTourney.jsp is the only tourney admin page with a game selector and it is data-driven from getAllGames() (see first check). manageTourneys.jsp:29-35 selects by event id only. manageTourney.jsp and addTourneyMatch.jsp take no game parameter. markTourneyGame.jsp operates on raw gid via SQL (lines 31, 86-87). Nothing to update for Renju.

*Evidence:* dsg_src/httpdocs/gameServer/admin/manageTourneys.jsp:29-35; admin/manageTourney.jsp (no <select name="game">); admin/addTourneyMatch.jsp (no game select); admin/markTourneyGame.jsp:69,86-87; admin/newTourney.jsp:116-123

### [UNCLEAR] Historic/completed tourney game viewer for Renju games

All four formats expose 'View Games (in Games History)' via submitToDatabase('<%= gameName %>', 'Pente.org', tourneyName, round, section) where gameName = GridStateFactory.getGameName(tourney.getGame()) (statusRound.jsp:23) = "Renju" for game 81. submitDb.js:3 forwards it as filter_data game=Renju to the search controller. GridStateFactory.getGameId("Renju") (lines 408-420) scans allGames first and returns 31 (the LIVE RENJU id), not 81 — but this is identical to every other TB game (getGameName(TB_PENTE=51) is "Pente", getGameId("Pente") is 1), so it is the established convention rather than a Renju regression. I could not locate the code that maps a TB game id to the archived pente_game game id, so I cannot confirm archived Renju tourney games are actually retrievable by this link. Not verified either way.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/statusRoundRobin.jsp:22-26; statusSwiss.jsp:5-6; statusSingleElim.jsp:5-6; statusDoubleElim.jsp:7-9; dsg_src/httpdocs/gameServer/js/submitDb.js:3-30; dsg_src/java/org/pente/game/GridStateFactory.java:408-420

### [OK] tourneyDetails.jsp 'Rounds last' row for a TB Renju tourney

Not a Renju gap. tourneyDetails.jsp:34 gates the 'Rounds last: N Days' row on tourney.getGame() < 50, so it is suppressed for EVERY turn-based game (TB ids start at 51), leaving an empty <tr>. Renju behaves identically to TB Pente here. The per-move timer row at lines 39-50 keys off tourney.isTurnBased() and does render correctly.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/tourneyDetails.jsp:30-38,39-51

### [GAP] Help/rules pages reachable from a Renju tourney page

Renju game rules DO exist and are linked from the general rules page (playGameRules.jsp:25 anchor '#renju', body at 186-192 with Taraguchi-10 references). Gap is narrower: tourneyDetails.jsp:21-27 links only the generic /help/helpWindow.jsp?file=tourney<Format> and ?file=tournaments pages, and a grep for 'renju|single game|two game' across dsg_src/httpdocs/help/tourney*.jsp and help/tournaments.jsp returns zero matches. So nothing tells a signing-up player that a Renju tourney match is ONE game rather than the usual two-game colour-alternating set — a rules-comprehension gap, not a functional one.

*Evidence:* dsg_src/httpdocs/help/playGameRules.jsp:25,186-192; dsg_src/httpdocs/gameServer/tournaments/tourneyDetails.jsp:21-27; ls dsg_src/httpdocs/help/ (tourneyFormats.jsp, tourneyRound-Robin.jsp, tourneySwiss.jsp, tourneySingle-Elimination.jsp, tourneyDouble-Elimination.jsp, tournaments.jsp)

## Live local DB state (read-only) — Renju turn-based tournament scaffolding
Area verdict: **ready**

### [UNCLEAR → REFUTED] Mandated access path: docker exec penteorg-main_db-1

The whole penteorg stack is ABSENT, not merely stopped — so no live SQL query was run and no server-side behaviour was observed. Per instructions I did not start it. I substituted a strictly read-only alternate path: the DB datadir is a bind mount (`docker-compose.yml:119` -> `./dockerMain/db:/var/lib/mysql`), and every table below is MyISAM, so I parsed the actual on-disk `.MYD`/`.frm` files of this same database. File mtimes are Aug 6-7 2026 (MYI Aug 7 13:01), i.e. data current as of the last run. Treat all row values below as decoded-from-disk, high confidence but NOT a live `SELECT`.

**Verification:** NOT a real gap. The auditor's environment claim is factually accurate — I reproduced every part of it — and the disclosure is honest and correctly labelled. But it is an audit-coverage limitation, not missing scaffolding, and it does not block or degrade running a Renju turn-based (TB) tournament. Nothing that a live SELECT would have revealed changes the answer.

Refutation, point by point:

1. The one live-DB precondition that could genuinely block a Renju TB tournament — the Taraguchi-10 opening-state columns — is CONFIRMED PRESENT in the authoritative main DB. `strings dockerMain/db/dsg/tb_game.frm` returns both `renju_swaps` and `renju_offers`, i.e. migration `dsg_src/sql/2026-06-14-renju-opening-state.sql:14-15` is already applied to this database. This is a sharper check than the auditor's row decode and it lands exactly on the decisive question. There is no auto-migration runner (`dockerMain/dbAlwaysInit/0001_init.sh` only creates users), so "were the columns ever applied?" was the real risk — and it resolves clean.

2. The row-level precondition (a `game_event` row for game=81) is boot-time self-healing, so its pre-boot state is irrelevant and a live SELECT would have proved nothing durable. `MySQLGameVenueStorer.registerAllGames` (dsg_src/java/org/pente/game/MySQLGameVenueStorer.java:690) loops `GridStateFactory.TB_GAMES` — which contains `TB_RENJU` (GridStateFactory.java:82) — inserting `Turn-based Game` + `King of the Hill` rows through an insert-if-absent NOT EXISTS guard, explicitly documented idempotent and safe to re-run every boot. It is invoked at DSGContextListener.java:118. Whatever the DB holds today, starting the stack makes it correct.

3. I closed the residual risk in #2. `registerAllGames` is wrapped in a swallowing try/catch and throws if `game_site` has no row for sid=2, which would silently skip registration. But `game_site.MYD` on disk contains the "Pente.org" rows, so the site exists. That failure mode would also break all 16 TB games identically — it is not Renju-specific.

4. The tournament-start path is code-driven, not DB-driven, so no live SQL was needed to answer "can the operator start one." `newTourney.jsp:116-123` builds the Game dropdown from `GridStateFactory.getAllGames()` (:430-432 → `allGames`), and `allGames` includes `TB_RENJU_GAME` at GridStateFactory.java:153. No DB game catalogue gates the selection.

5. The tournament-specific Renju work from f7db119 is present and code-verifiable: `isSingleGameSet` (GridStateFactory.java:502-506) includes `TB_RENJU`, consumed by CacheTourneyStorer.java:492 and :551 for single-game set handling and unplayed-match resolution via original seats.

Honest residual, correctly scoped: no end-to-end runtime observation of a Renju TB tournament was made, and none was possible without starting the stack, which the audit rules forbade. That limitation is real and the auditor was right to flag it — but it is a statement about audit method, not about missing product scaffolding, and every substantive precondition it left open is settled above by code, schema, and on-disk table definitions.

*Verifier evidence:* ENVIRONMENT CLAIM — independently reproduced, accurate:
- `docker ps -a` → exactly 1 container (d422646949b7, ghcr.io/chopratejas/headroom:latest, "vibrant_driscoll"), unrelated to penteorg.
- `docker compose ls -a` → header row only, ZERO projects.
- `lsof -nP -iTCP -sTCP:LISTEN` → nothing on 3306/3307/3316/3317 (postgres on 5432, tor 9050, docker 8787; no MariaDB).
- `which mysql mariadb mysqldump` → empty; no client installed.
- Bind mount confirmed: /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/docker-compose.yml — `./dockerMain/db:/var/lib/mysql`.

REFUTING EVIDENCE:
- /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dockerMain/db/dsg/tb_game.frm — `strings` yields `renju_swaps` and `renju_offers`. Migration already applied to the live main DB. (tb_game.MYD 52 MB, mtime Aug 7 13:04.)
- dsg_src/sql/2026-06-14-renju-opening-state.sql:14-15,21-22,26 — ADD COLUMN IF NOT EXISTS renju_swaps/renju_offers.
- dsg_src/sql/schema.sql:770, 891-892, 921-922 — same columns in checked-in schema.
- dsg_src/java/org/pente/game/GridStateFactory.java:77 — `TB_RENJU = TB_START + RENJU` (50+31 = 81).
- GridStateFactory.java:82 — TB_RENJU present in `TB_GAMES[]`.
- GridStateFactory.java:153 — TB_RENJU_GAME present in `allGames[]`.
- GridStateFactory.java:187 — `new Game(TB_RENJU, "Turn-based Renju", false)` in displaygames.
- GridStateFactory.java:430-432 — `getAllGames()` returns `allGames`.
- GridStateFactory.java:502-506 — `isSingleGameSet(...)` includes `game == TB_RENJU`.
- dsg_src/java/org/pente/game/MySQLGameVenueStorer.java:690 — `registerAllGames(int siteId)`; loops TB_GAMES adding TB_EVENT + KOTH_EVENT; `ensureGameEvents` uses "insert ... select ?,?,? from dual where not exists (...)", commented "insert-if-absent, so this is idempotent and safe to re-run on boot".
- MySQLGameVenueStorer.java:34-35 — GAME_SITE_TABLE="game_site", GAME_EVENT_TABLE="game_event".
- dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:118 — `gameVenueStorer.registerAllGames(2);` inside a best-effort try/catch ("boot degrades rather than fails"); comment identifies site 2 as the live Pente.org site.
- dockerMain/db/dsg/game_site.MYD — `strings` yields "Pente.org", confirming the sid=2 site row exists, so registerAllGames will not throw-and-skip.
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:116-123 — `<select name="game">` populated by `GridStateFactory.getAllGames()` + `getDisplayName(...)`; line 24 `int game = Integer.parseInt(request.getParameter("game"))`, line 29 `tourney.setGame(game)`. Code-driven, no DB catalogue.
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:492, :551 — `GridStateFactory.isSingleGameSet(t.getGame())` used in match/seat handling.
- git log: f7db119 "Original-seats helper for swap-variant games + renju single-game tournament sets (#12)" — includes "TB tournament: resolve unplayed match via original seats (covers renju)" and "swapSeats: skip end-of-game rotation via net swap parity (adds renju)". e55f161 (#19) touches RenjuState.java, RenjuTimeoutDrawEvaluator.java, CacheTBStorer.java, MySQLTBGameStorer.java, TBGame.java, MoveServlet.java.
- dockerMain/dbAlwaysInit/0001_init.sh and dockerMain/dbInit/0001_init.sh — user/replication grants only; confirms no automatic SQL migration runner exists (which is why check #1 above was the decisive one).

*Evidence:* `docker ps -a` returns exactly 1 container (`vibrant_driscoll`, unrelated headroom image); `docker compose ls -a` lists ZERO projects; no penteorg container exists even in stopped state. `lsof -nP -iTCP -sTCP:LISTEN` shows nothing on 3306/3307/3316/3317; `mariadb`/`mysql` clients are not installed on the host.

### [OK] (1) game_event rows exist for games 31, 32, 81

All 7 expected rows present. This is byte-identical in shape to every other registered game — e.g. the immediately preceding game group 29/30/79 has exactly 7 rows too (eids 1619-1626): 29=[Live 1620, KOTH 1622, TB 1626], 30=[Live 1621, KOTH 1623], 79=[TB 1619, KOTH 1624]. Renju = 31:[Live 1798, KOTH 1799, TB 1804], 32:[Live 1800, KOTH 1801], 81:[TB 1802, KOTH 1803]. The extra 'Turn-based Game' row on the BASE id (eid=1804, game=31) is NOT an anomaly — games 1,3,5,7,9,11,13,15,17,21,23,25,27,29 all carry the same base-id TB row. The row a TB tournament venue needs (game=81, 'Turn-based Game', eid=1802) exists. Registration matches `MySQLGameVenueStorer.registerAllGames` as described in docs/renju-integration-guide.md:51.

*Evidence:* /Users/waliedothman/mariposa/coding/pente.org-project/pente.org/dockerMain/db/dsg/game_event.MYD — 824 rows decoded, max eid 1814. Exact rows: eid=1798 game=31 site_id=2 name='Live Game'; eid=1799 game=31 site_id=2 name='King of the Hill'; eid=1800 game=32 site_id=2 name='Live Game'; eid=1801 game=32 site_id=2 name='King of the Hill'; eid=1802 game=81 site_id=2 name='Turn-based Game'; eid=1803 game=81 site_id=2 name='King of the Hill'; eid=1804 game=31 site_id=2 name='Turn-based Game'. All mailing_list=''.

### [OK] (2) renju_swaps / renju_offers columns on tb_game, tb_game_ai, pente_game

The migration has been APPLIED to the live local DB, not just committed to the repo. pente_game correctly lacks `renju_offers` — that is by design, live-game offers go to the side table (migration comment at 2026-06-14-renju-opening-state.sql:24). Column types per schema.sql: renju_swaps smallint(5) unsigned NULL (base-3 packed, range 0..728), renju_offers varbinary(10) NULL (ten 0..224 board positions). tb_game_ai carrying the columns matters because MySQLTBGameStorer selects TB_COLUMNS against it too (migration comment lines 17-19).

*Evidence:* Live .frm files in dockerMain/db/dsg/: tb_game.frm contains both `renju_swaps` and `renju_offers`; tb_game_ai.frm contains both; pente_game.frm contains `renju_swaps` only. Matches dsg_src/sql/schema.sql:891-892 (tb_game), :921-922 (tb_game_ai), :770 (pente_game) and the migration dsg_src/sql/2026-06-14-renju-opening-state.sql:13-15, :20-22, :25-26.

### [OK] (2b) pente_renju_offer table exists and holds real data

Table created AND populated, so live Renju games have already reached Taraguchi-10 Branch-B offer submission end-to-end. Caveat for the audited question: this side table serves LIVE games only; turn-based Renju stores its offers inline in tb_game.renju_offers, so these ~110 rows are not evidence that the TB Renju path has been exercised.

*Evidence:* dockerMain/db/dsg/pente_renju_offer.frm exists with columns gid, site_id, offer_num, move; pente_renju_offer.MYD is 1540 bytes (~110 rows at 14-byte fixed rows). DDL: dsg_src/sql/schema.sql:781 and dsg_src/sql/2026-06-14-renju-opening-state.sql:28-34 — PRIMARY KEY (gid, site_id, offer_num), ENGINE=MyISAM.

### [OK] (3) Tournament tables — game/event columns and any constraint on game ids

NO tournament table has a `game` column. The game is reached only indirectly: dsg_tournament_detail.event_id -> game_event.eid -> game_event.game. NOTHING constrains which game id a tournament may use — no FOREIGN KEY, no CHECK, no enum; every table is ENGINE=MyISAM so FKs would not be enforced even if declared. game_event.game is tinyint(3) unsigned (schema.sql:375), range 0-255, so 81 fits with no overflow. The turn-based knob is dsg_tournament_detail.round_length_days; dsg_tournament_restriction is event-scoped (type/value) and carries nothing game-id related (its .MYD is only 392 bytes). Conclusion: the DB imposes zero obstacle to a tournament on game=81.

*Evidence:* dsg_src/sql/schema.sql:272-280 dsg_tournament(pid, event_id, signup_date, rating, seed, dropout_round); :293-309 dsg_tournament_detail(event_id PK, status enum('N','S','A','C'), timer enum('N','S','I'), initial_time, incremental_time, round_length_days, creation_date, signup_end_date, start_date, completion_date, format tinyint(3) unsigned, speed enum('Y','N'), forumID, prize); :314-326 dsg_tournament_match(mid, event_id, round, section, gid, p1_pid, p2_pid, result, match_seq, forfeit); :342-347 dsg_tournament_restriction(event_id, type, value); :352-366 dsg_tournament_results(result_id, pid1, pid2, event_id, round, section, result, forfeit, p1_wins, p1_losses, p2_wins, p2_losses).

### [OK] (3b) Structural note — each tournament is itself a game_event row

Important for interpreting check (1): the auto-registered eid=1802 (game=81, 'Turn-based Game') is the VENUE/lobby event, not a tournament. Creating a Renju TB tournament means INSERTing a NEW game_event row with game=81 and a matching dsg_tournament_detail row — it does not reuse eid=1802. So the operator-facing question reduces to whether the tournament-creation UI offers game id 81 in its game dropdown (servlet/JSP area, outside this AREA's scope). The DB itself will accept game=81 without modification.

*Evidence:* dsg_tournament_detail event_ids decoded from dockerMain/db/dsg/dsg_tournament_detail.MYD join directly into game_event.MYD: eid=1814 -> game=79 name='Swap2-Keryo Aug 2026 (1)'; eid=1813 -> game=73 name='Go (13x13) Aug 2026'; eid=1812 -> game=71 name='Go (9x9) Aug 2026'; eid=1808 -> game=73 name='Go (13x13) Jul 2026 (1)'. game_event also holds legacy rows like eid=1 game=1 name='October 2000 Main #1 Tournament'.

### [OK] (4) Existing tournaments referencing Renju

No Renju tournament has ever been created. Byte-pattern hits for 1798-1804 in dsg_tournament_match.MYD (1 each) and dsg_tournament_player.MYD (1802 x2) are coincidental gid/pid collisions, not event_id references — dsg_tournament_detail is the authoritative table (event_id is its PRIMARY KEY, schema.sql:308) and it has none. This is an expected state (nobody has made one), not a missing-scaffolding defect.

*Evidence:* dockerMain/db/dsg/dsg_tournament_detail.MYD — 236 rows, event_id range 1084..1814, distinct ids only. ZERO rows with event_id in 1798..1804. Highest event_ids present: 1786, 1789, 1790, 1791, 1793, 1794, 1795, 1797, 1805, 1807, 1808, 1811, 1812, 1813, 1814 — note 1798-1804 are skipped exactly. Status distribution: N=179, S=22, A=0, C=35.

### [OK] (4b) Turn-based tournaments are an established, actively-used pattern — but game=81 is the one TB id never used

game=81 (TB_RENJU) is the ONLY turn-based game id with zero tournaments — every other TB id from 51 to 79 has 5-17. Sharpest signal in this audit: eids 1811-1814 were created AFTER the Renju game_event rows (1798-1804), so the operator has been actively creating TB tournaments since the Renju merge and has not made a Renju one. The machinery demonstrably works for game ids 51-79; the Renju path is simply unexercised, so any Renju-specific breakage (e.g. Taraguchi swap/offer decisions inside tournament-created games, original-seats handling for swap-variant games from commit f7db119) would be undetected by existing data. All TB tournaments use timer='I'. Caveat: 55 of 236 tournament event_ids did not resolve to a game_event row under my parser — either parser misses or genuine orphans; pre-existing and not Renju-specific, I did not chase it.

*Evidence:* Joining dsg_tournament_detail.MYD to game_event.MYD: 164 of 181 resolvable tournaments use a turn-based game id. Counts by game id — 51:17, 53:14, 55:14, 57:10, 59:8, 61:9, 63:10, 65:12, 67:11, 69:10, 71:16, 73:14, 75:7, 77:5, 79:7, 81:0. Most recent four (all timer='I'): eid=1811 game=79 status='S' 'Swap2-Keryo Aug 2026'; eid=1812 game=71 status='N' 'Go (9x9) Aug 2026'; eid=1813 game=73 status='N' 'Go (13x13) Aug 2026'; eid=1814 game=79 status='N' 'Swap2-Keryo Aug 2026 (1)'.

## Ratings & player data for Renju turn-based tournaments
Area verdict: **blocked**

### [OK] Per-game rating storage is keyed by game id (no per-game columns)

dsg_player_game(pid, game tinyint(3) unsigned, wins, losses, draws, rating decimal(14,9), streak, last_game_date, computer, tourney_winner) with PRIMARY KEY (pid, game, computer). Rating is a row per game id, not a column per game — there is no renju-shaped hole. insertGame binds stmt.setInt(2, dsgPlayerGameData.getGame()) and updateGame binds stmt.setInt(8, ...getGame()); both fully generic. Game ids 31 and 81 fit tinyint unsigned (0-255). No DDL or migration is needed for Renju ratings.

*Evidence:* dsg_src/sql/schema.sql:150-165; dsg_src/java/org/pente/gameServer/core/MySQLDSGPlayerStorer.java:752-758, 792-812

### [OK] TB game-completion updates ratings for game 81, with a Renju-aware K-factor

On set completion CacheTBStorer calls GameOverUtilities.updateGameData(dsgPlayerStorer, winnerData, winnerData.getPlayerGameData(game.getGame(), false), loserData, loserData.getPlayerGameData(game.getGame(), false), set.isDraw(), k). game.getGame() is the TB id; TB_START=50 (GridStateFactory:61) + RENJU=31 (:37) = 81. Renju is explicitly named in the K-factor test at CacheTBStorer:1086-1089 (k=32 for TB_GO/TB_GO13/TB_GO9/TB_RENJU, else 64), so someone deliberately wired Renju in. NOTE — this corrects the premise in the task brief: TB ratings are NOT stored under base id 31. They are stored under the TB id (TB_PENTE=51, TB_RENJU=81) and always have been. Live Renju is a separate rating pool under 31 (ServerTable.java:3650-3652 lists RENJU and SPEED_RENJU in k32Game).

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1085-1094; dsg_src/java/org/pente/gameServer/core/GameOverUtilities.java:14-42; dsg_src/java/org/pente/game/GridStateFactory.java:37,61

### [OK] Renju draws are handled correctly by the rating math

Matters because Renju is the draw-capable variant (pass moves, draw offers, RenjuTimeoutDrawEvaluator). gameOver() case DRAW increments draws and zeroes streak (:184-187). updateRating has DRAW branches in both the provisional path (:226-227) and the established Elo path (w=0.5 at :235-237, guard at :249-250). CacheTBStorer passes set.isDraw() as the draw flag (:1094), and GameOverUtilities.updateGameData branches on it (:20-24). No gap.

*Evidence:* dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerGameData.java:184-187, 226-227, 235-237, 249-250; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1094

### [OK] A Renju tourney's rating restriction resolves against game 81

insertTourney creates the tourney's game_event row with newEvent.setGame(tourney.getGame()) (:232), so a TB Renju tourney's event carries game=81. tournamentConfirm.jsp:65 reads dsgPlayerData.getPlayerGameData(tourney.getGame()) and checks RATING_RESTRICTION_BELOW/ABOVE/GAMES_RESTRICTION_ABOVE at :93-125. CacheTourneyStorer.setInitialSeeds:429-452 re-checks rating restrictions server-side against tourney.getGame(). Restriction types are RATING_RESTRICTION_ABOVE=1, BELOW=2, GAMES_RESTRICTION_ABOVE=3 (Restriction.java:11-13), stored in dsg_tournament_restriction(event_id, type, value) (schema.sql:342-350). The restriction mechanism itself is game-generic and correct for 81.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:231-236; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:429-452; dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:65,93-125

### [OK] Provisional/default rating for a game nobody has played (Java layer)

SimpleDSGPlayerGameData's constructor sets rating = 1600 (:58) and isProvisional() returns getTotalGames() < 20 (:264-266). SimpleDSGPlayerData.getPlayerGameData(game, false) synthesizes a fresh 1600 / 0-games record when no row exists (:404-409). So on the JSP signup-confirm path, a player who has never played Renju reads as rating 1600, provisional, 0 games — they pass an unrestricted tourney and see a sane number. The Java layer has no gap. The gap is that the seeding path does not go through this code (see next item).

*Evidence:* dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerGameData.java:57-59, 264-267; dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerData.java:404-409

### [GAP → CONFIRMED REAL] Seeding query silently drops every player with no rating row for the tourney's game

HARD BLOCKER. getTourneyPlayers runs: 'from dsg_tournament_player tp left outer join dsg_player_game g on tp.pid = g.pid join player p left outer join game_event e on g.game = e.game and g.computer=\'N\' where tp.event_id = ? and tp.event_id = e.eid and tp.pid = p.pid order by g.rating desc' (:477-484). The WHERE predicate tp.event_id = e.eid sits on the outer-joined game_event e, which discards NULL-extended rows and turns both LEFT JOINs into effective INNER joins. Since the tourney's game_event row carries game=81 (insertTourney:232), only players who ALREADY have a dsg_player_game row with game=81 AND computer='N' produce an output row; everyone else vanishes from the list. Those rows are created lazily and only on rated game completion — insertPlayer (MySQLDSGPlayerStorer.java:84-135) creates none, and the only insertGame callers anywhere are GameOverUtilities.java:33 and :39. Chain to failure: CacheTourneyStorer.startTournament (:660-679) calls setInitialSeeds -> getTourneyPlayers; when fewer than 2 rows return it logs 'Not enough players to start tournament' and calls cancelTourney, setting status 'S' (:665-670). Net effect: a TB Renju tournament created today, before any player has completed a rated TB Renju set, seeds ZERO players and auto-cancels itself at start time. Note that having played LIVE Renju does not help — live Renju writes game=31 rows, not 81. This is latent for established games (most TB Pente signups already have game=51 rows) and only becomes a 100% failure for a brand-new game id. Caveat on evidence: this was established by reading the SQL and the call chain; I was instructed not to query the DB, so I did not empirically confirm the current row count for game=81 in dsg_player_game.

**Verification:** CONFIRMED — the mechanism is real, unfixed, and it does block the first TB Renju tournament. I attempted five refutations; all failed.

REFUTATION 1 — "the cache layer bypasses the SQL." Failed. CacheTourneyStorer.getTourneyPlayers (:381-385) is a pure delegate to backingStorer; CacheTourneyStorer.setInitialSeeds (:425-453) only applies rating restrictions then returns backingStorer.setInitialSeeds(eid) (:453), which calls getTourneyPlayers(eid) at MySQLTourneyStorer:709. No Redis path supplies the seed list.

REFUTATION 2 — "a later commit fixed the query." Failed. `git log -S 'tp.event_id = e.eid' -- dsg_src/` returns only 7da6a61 (Initial commit). The single edit ever made to that statement is 3ff66ab (2020-07-01), which changed `g.wins + g.losses` to `g.wins + g.losses + g.draws` — nothing about the join. Neither f7db119, e55f161, nor any Renju commit touches MySQLTourneyStorer.getTourneyPlayers.

REFUTATION 3 — "the tourney's game_event row isn't game=81, so live Renju rows (game=31) would match." Failed. Tourney.isTurnBased() is `return this.game > 50` (Tourney.java:122-124), so a TB tourney's game *is* the TB id by definition; TB_RENJU = TB_START(50) + RENJU(31) = 81 (GridStateFactory.java:61,77). insertTourney sets newEvent.setGame(tourney.getGame()) and adopts that event's eid as the tourney eid (MySQLTourneyStorer:231-236), so e.eid = tourney eid pins e.game = 81, and the ON clause forces g.game = 81.

REFUTATION 4 — "something pre-creates the rating rows (signup, login, or a boot backfill analogous to registerAllGames)." Failed. The only `insert into dsg_player_game` in Java is MySQLDSGPlayerStorer:752, reachable only from GameOverUtilities:33/:39, guarded by `getTotalGames() == 0` (:16-17), whose only callers are ServerTable:3719/3739/3798 (live) and CacheTBStorer:1090 (TB) — i.e. rated-game completion. DSGContextListener:118 calls registerAllGames, which creates game_event rows only, not player rating rows. insertPlayer (MySQLDSGPlayerStorer:84-135) inserts into dsg_player/player only.

REFUTATION 5 — "the default rating object covers it." Partially true in memory, irrelevant in SQL, and it actually sharpens the finding: SimpleDSGPlayerData.getPlayerGameData (:381-405) *fabricates* a default record when none exists ("if no data exists yet create it", :404-405). So the object model reports every player as rated for game 81 while the seeding SQL sees no row — that asymmetry is exactly why this fails silently rather than throwing. (It also means the rating-restriction loop at CacheTourneyStorer:435-436 does not NPE.)

SQL semantics re-checked independently: MySQL/MariaDB joins are left-associative, so the FROM parses as ((dsg_tournament_player tp LEFT JOIN dsg_player_game g ON tp.pid=g.pid) CROSS JOIN player p) LEFT JOIN game_event e ON g.game=e.game AND g.computer='N'. The WHERE predicate `tp.event_id = e.eid` sits on the null-extended side; `x = NULL` evaluates NULL, so every null-extended row is discarded and both LEFT JOINs degrade to INNER. Auditor's parse is correct.

TWO CORRECTIONS / ADDITIONS to the report:

(a) Severity is conditional, not unconditional. TB Renju is playable outside tournaments today — dsg_src/httpdocs/gameServer/tb/new.jsp:85 offers TB_RENJU and NewGameServlet.java:467 handles it — so as soon as two players finish one rated TB Renju set, seeding works normally. The correct framing is a cold-start ordering constraint: the *first* TB Renju tournament auto-cancels unless the operator first ensures ≥2 entrants have a completed rated TB Renju set. That is still a genuine blocker for the audited question ("can the operator start and run one today"), but it is escapable without a code change, which the "HARD BLOCKER" label obscures.

(b) The auditor missed the worse failure mode, and also missed a mitigating symptom. Worse: in a *mixed* field (some entrants have game=81 rows, some don't), the tourney does NOT cancel — it starts with only the rated subset. setInitialSeeds (MySQLTourneyStorer:709-724) updates seeds only for returned rows, so dropped entrants keep seed 0 in dsg_tournament_player, never enter a bracket, and receive no error. Mitigating: manageTourney.jsp:26 renders the admin player list from the same getTourneyPlayers call, so the operator sees an empty/short roster before the start date — the failure is visible in advance if anyone looks.

*Verifier evidence:* Query and its effective-inner-join behavior: dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:476-484 (WHERE tp.event_id = e.eid on the outer-joined game_event e), :487-495 (result loop), :709-724 (setInitialSeeds consumes it and only updates seeds for returned rows).
Cache layer is a pure delegate: dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:381-385, :425-453 (return backingStorer.setInitialSeeds at :453).
Auto-cancel path: dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:660-670 (players.size() < 2 -> "Not enough players to start tournament" -> setStatus('S') -> cancelTourney).
Tourney game id is necessarily the TB id: dsg_src/java/org/pente/gameServer/tourney/Tourney.java:122-124 (isTurnBased = game > 50); dsg_src/java/org/pente/game/GridStateFactory.java:61 (TB_START = 50), :77 (TB_RENJU = TB_START + RENJU = 81).
game_event row created with game = tourney game: dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:231-236.
Only writer of dsg_player_game rows: dsg_src/java/org/pente/gameServer/core/MySQLDSGPlayerStorer.java:752 (insert into dsg_player_game); sole callers dsg_src/java/org/pente/gameServer/core/GameOverUtilities.java:16-17 (totalGames==0 guard), :33, :39; sole callers of updateGameData: dsg_src/java/org/pente/gameServer/server/ServerTable.java:3719, :3739, :3798 and dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1090 (all game-completion paths).
No backfill at boot: dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:118 (registerAllGames -> game_event rows only); no `insert into dsg_player_game` outside dsg_src/sql/legacy/*.sql.
Registration creates no rating rows: dsg_src/java/org/pente/gameServer/core/MySQLDSGPlayerStorer.java:84-135 (DSG_PLAYER_TABLE only).
In-memory default rating masks the DB gap: dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerData.java:381-405.
No later fix: `git log -S 'tp.event_id = e.eid' -- dsg_src/` -> 7da6a61 (Initial commit) only; `git show 3ff66ab` -> single-line change adding `+ g.draws`, join untouched.
Operator-visible symptom: dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:26 (roster from same query), :88 (setInitialSeeds).
Cold start is escapable: dsg_src/httpdocs/gameServer/tb/new.jsp:85 and dsg_src/java/org/pente/turnBased/web/NewGameServlet.java:467 (TB Renju invitable outside tournaments).
Signup list itself is unaffected: dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:523-533 (getTourneyPlayerPids has no game_event join).
Not empirically confirmed: current row count for game=81 in dsg_player_game (read-only audit; no DB queries run).

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:476-495, 704-736; dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:660-679; dsg_src/java/org/pente/gameServer/core/MySQLDSGPlayerStorer.java:84-135; dsg_src/java/org/pente/gameServer/core/GameOverUtilities.java:32-42

### [GAP → REFUTED] GAMES_RESTRICTION_ABOVE ('require established players') is never enforced server-side

setInitialSeeds only tests RATING_RESTRICTION_ABOVE and RATING_RESTRICTION_BELOW (:431-432); GAMES_RESTRICTION_ABOVE (type 3) is not handled in the loop at all. The newTourney.jsp 'Require established players (>20 games)?' radio (:143-146) therefore produces a UI-only restriction, enforced solely by the confirm page at tournamentConfirm.jsp:117-125. Pre-existing and not Renju-specific, but it bites Renju hardest: every player has 0 Renju games, so ticking that box blocks the entire signup form for everyone while providing no seeding-time guarantee.

**Verification:** The code fact is accurate — I confirmed every cited line and found the hole is even wider than reported — but it does NOT block or degrade running a Renju turn-based tournament, so it fails the isReal bar for this audit.

CONFIRMED (not refuted): At HEAD, CacheTourneyStorer.setInitialSeeds tests only RATING_RESTRICTION_ABOVE/BELOW (:431-432); GAMES_RESTRICTION_ABOVE = 3 (Restriction.java:13) is never matched, so type-3 players are never pruned at seeding. The backing implementation does no filtering either — MySQLTourneyStorer.setInitialSeeds:704-736 is pure seed numbering (one UPDATE per player), so CacheTourneyStorer:429-452 is the entire seeding-time filter in the system. Worse than the auditor stated: tournamentSignup.jsp performs NO restriction re-check at all — it validates only the rules checkbox (:11-17) then calls addPlayerToTourney (:23) directly, and CacheTourneyStorer.addPlayerToTourney:352-366 is a pure write. So a direct POST of eid + rules=Y bypasses the confirm page entirely, meaning ALL three restriction types (including the two RATING ones) are unenforced on the write path; only setInitialSeeds cleans up afterward, and only for types 1 and 2. No later commit fixes this: the current f7db119-era file still shows the two-type test, and git log on newTourney.jsp / tournamentConfirm.jsp shows nothing post-Renju. docs/renju-integration-guide.md never mentions tournament restrictions or signup (its only "tournament" hits are the iOS maskTournamentOpening board mask at :545-546, :650).

WHY IT IS STILL NOT A GAP FOR THIS QUESTION:
1. Opt-in and off by default. newTourney.jsp adds the restriction only when erType equals "1" (:82-85); neither radio at :144-145 carries `checked`, so the default create path submits erType=null and no type-3 Restriction is ever constructed. An operator creating a Renju TB tourney simply leaves the box alone and nothing about seeding, signup, or round generation changes.
2. The Renju-specific sting is redundant. tournamentConfirm.jsp:77-84 already hard-gates signup on `game == null || game.getTotalGames() == 0` for the tourney's own game id, BEFORE the restriction loop at :93 is reached, and the submit button at :177 sits inside the `if (pass)` block at :131. Every player has 0 games in TB_RENJU=81 at launch, so the entire signup form is suppressed for everyone with or without the checkbox. The checkbox only widens an existing block from "0 games" to "≤20 games" — it does not create the Renju signup problem, and it is not the thing to fix to solve it.
3. No functional breakage at start time. setInitialSeeds still returns the player list and startTournament (CacheTourneyStorer:660-679) proceeds normally; the only consequence of the missing type-3 branch is a wrongly-admitted player, which cannot occur here because nobody can reach signup in the first place.
4. Genuinely pre-existing and game-agnostic — it affects Pente, Go, and Keryo identically, and the production Masters/Amateurs/Open rotation (CacheTourneyStorer:755-813) keys exclusively off RATING restrictions, so the type-3 omission has never been exercised.

WORTH ESCALATING SEPARATELY (different item, real): tournamentConfirm.jsp:77-84 is the actual Renju TB signup blocker — 0 rated TB Renju sets means the signup form renders suppressed for every player on day one, and there is no admin add-player path to work around it (manageTourney.jsp has only removePlayerFromTourney at :82 and setInitialSeeds at :88; tournamentSignup.jsp:23 is the sole insertion point). The tournamentSignup.jsp bypass is also a genuine authorization hole, but it is a security/robustness item, not a Renju scaffolding one.

*Verifier evidence:* CONFIRMS THE CODE FACT:
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:429-452 — setInitialSeeds restriction loop; :431-432 tests only RATING_RESTRICTION_ABOVE || RATING_RESTRICTION_BELOW; :437 and :441 are the only branches; type 3 never handled; :449-451 removes only the players collected by those two branches.
- dsg_src/java/org/pente/gameServer/tourney/Restriction.java:11-13 — RATING_RESTRICTION_ABOVE=1, RATING_RESTRICTION_BELOW=2, GAMES_RESTRICTION_ABOVE=3.
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:704-736 — backing setInitialSeeds does zero restriction filtering (only "update dsg_tournament_player set seed = ?"), so :453's delegation adds no enforcement.
- dsg_src/httpdocs/gameServer/tournaments/tournamentSignup.jsp:11-17, :23 — only the rules checkbox is validated; addPlayerToTourney is called with no restriction check (wider hole than reported).
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:352-366 — addPlayerToTourney is an unconditional write + cache update + PLAYER_REGISTER notify; no validation.
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:117-125 — the sole GAMES_RESTRICTION_ABOVE check (`game.getTotalGames() <= r.getValue()` → pass=false).

REFUTES THE "BLOCKS RENJU TB" FRAMING:
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:81-85 — restriction added only `if (erType != null && erType.equals("1"))`.
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:143-146 — neither the Yes nor the No radio has `checked`; default submission omits erType, so no type-3 restriction is created.
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:77-84 — unconditional pre-restriction gate `if (game == null || game.getTotalGames() == 0) pass = false;` blocks all 0-Renju-game players regardless of the checkbox; the restriction loop only begins at :93 in the else branch (:87).
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:131, :177 — submit button nested inside `if (pass)`, so the gate at :78 alone suppresses signup.
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:660-679 — startTournament proceeds normally off whatever setInitialSeeds returns; no crash or missing scaffolding from the omitted branch.
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:755-813 — the auto Masters/Amateurs/Open rotation branches only on RATING restrictions, so type 3 is unexercised in production.

NO FIX ELSEWHERE:
- git log -- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java → newest is f7db119; the :431-432 two-type test above is the current HEAD text, i.e. already post-f7db119. git log -- tournamentConfirm.jsp → newest 24ad0dc (pre-Renju); git log -- newTourney.jsp → newest 3a9cdf4 (Jakarta migration).
- docs/renju-integration-guide.md — grep for tournament/tourney/restriction/signup returns only iOS opening-mask lines (:545-546, :650, and Renju move-restriction prose at :407, :724, :1145, :1280, :1332, :1459); the guide is silent on tournament restrictions.

NEIGHBORING REAL ITEM:
- dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:82, :88 — admin page exposes removePlayerFromTourney and setInitialSeeds but no add-player, so tournamentSignup.jsp:23 is the only path into dsg_tournament_player, making the tournamentConfirm.jsp:77-84 0-games gate a hard Renju day-one signup blocker.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:429-452; dsg_src/httpdocs/gameServer/admin/newTourney.jsp:143-146; dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:117-125

### [GAP → REFUTED] Signup POST endpoint does not re-check restrictions

tournamentSignup.jsp loads the tourney and immediately calls resources.getTourneyStorer().addPlayerToTourney(dsgPlayerData.getPlayerID(), eid) with no restriction evaluation — the only check is the confirm page hiding the form. A direct POST bypasses it. Pre-existing and not Renju-specific; partially mitigated for rating restrictions by the setInitialSeeds re-check, but not at all for GAMES_RESTRICTION_ABOVE.

**Verification:** The FACTS in the claim are confirmed, but the SEVERITY framing fails the bar ("genuinely blocks or degrades running a Renju TB tournament and is not already handled elsewhere"). It neither blocks nor practically degrades a Renju TB tournament, and the part that matters IS handled elsewhere.

WHAT IS CONFIRMED (I could not refute the mechanics):
- tournamentSignup.jsp:22-23 does exactly what was reported: getTourneyDetails(eid) then addPlayerToTourney(pid, eid). The ONLY guard on the whole page is the `rules` checkbox at lines 11-17. No restriction evaluation.
- The storer does not compensate: MySQLTourneyStorer.java:414-438 is a bare `insert into dsg_tournament_player` with no validation. CacheTourneyStorer.java:352-366 just delegates + updates cache.
- Enforcement really is render-side only: tournamentConfirm.jsp:93-179 evaluates restrictions solely to set `pass`, and `pass` only decides whether the submit button (line 177) renders. A hand-crafted POST skips all of it.

WHY IT STILL FAILS THE BAR:

1. The mitigation is real and fires at exactly the right moment — but the auditor mis-located it. CacheTourneyStorer.java:425-454 re-checks RATING_RESTRICTION_ABOVE/BELOW and calls removePlayerFromTourney on violators. MySQLTourneyStorer.setInitialSeeds:704-736 has NO such check — it only assigns seed numbers. Production wires the cache (DSGContextListener.java:273 `new CacheTourneyStorer(...)`), so the re-check is live. It runs from manageTourney.jsp:88, i.e. immediately before createFirstRound — a forged rating-restriction signup is auto-purged before round 1 exists.

2. Both restriction types are OPT-IN, not defaults. newTourney.jsp:68-79 adds a rating restriction only if rrType != 0 (radio defaults to "None", line 136), and newTourney.jsp:81-85 adds GAMES_RESTRICTION_ABOVE 20 only if erType == "1" — neither radio at lines 144-145 is preselected. For a first Renju tournament an operator would not set a >20-Renju-games requirement, so the one unchecked restriction type has no realistic instance.

3. The operator has a direct remedy on the same page and the same submit: manageTourney.jsp:78-85 drops any selected pid via removePlayerFromTourney before setInitialSeeds/createFirstRound run at lines 88-90.

4. No corruption or crash path. dsg_tournament_player has PRIMARY KEY (event_id, pid) (schema.sql:331-337), so a replayed POST cannot duplicate a player — it raises a duplicate-key error. The tournament still creates, seeds, and runs with an extra player.

5. Zero Renju coupling. Nothing in the Renju work touches signup: `git log -- dsg_src/httpdocs/gameServer/tournaments/ dsg_src/java/org/pente/gameServer/tourney/` shows f7db119 and 11d12e7 as the recent tourney-area commits; e55f161 (draws) does not appear. The auditor concedes it is pre-existing. It is a generic pre-Renju web hygiene nit, not Renju TB scaffolding.

Net: the residual exposure is the conjunction of (operator opts into GAMES_RESTRICTION_ABOVE) AND (a logged-in player forges a POST) AND (the director does not drop them at manageTourney) — and even then the tournament runs. That is a hardening backlog item, not a gap that blocks or degrades starting a Renju TB tournament today.

ADJACENT LEAD, UNVERIFIED (flagging, not asserting): while checking the confirm page I found tournamentConfirm.jsp:77-84 sets pass=false and hides the form when `game == null || game.getTotalGames() == 0` for tourney.getGame(). If a first-ever Renju tournament's game id resolves to one where no player has any rated sets, the signup form would be hidden for EVERY player — the inverse problem, and a genuine blocker candidate. I did not confirm how tourney.getGame() vs getPlayerGameData() resolve for a TB Renju tourney (RENJU=31 vs TB_RENJU=81), so I mark this UNCLEAR and recommend it be audited as its own item. I also did not verify whether the signupEnd deadline is enforced on the POST path — also unclear.

*Verifier evidence:* CONFIRMS THE MECHANICS:
- dsg_src/httpdocs/gameServer/tournaments/tournamentSignup.jsp:11-17 — only guard is the `rules` param, forwards back to confirm page if absent
- dsg_src/httpdocs/gameServer/tournaments/tournamentSignup.jsp:22-23 — getTourneyDetails(eid) then addPlayerToTourney(dsgPlayerData.getPlayerID(), eid), no restriction evaluation
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:414-438 — bare INSERT, no validation
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:352-366 — delegates to backing storer, no validation
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:93-119 — restriction loop sets `pass` only
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:131 (`<% if (pass) { %>`) and :177 (`<input type="submit" value="Signup">`) — `pass` gates only whether the button renders

REFUTES THE SEVERITY:
- dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:425-454 — setInitialSeeds re-checks RATING_RESTRICTION_ABOVE/BELOW (lines 431-445) and calls removePlayerFromTourney (lines 449-451) before delegating
- dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:704-736 — no re-check here (corrects the auditor's attribution)
- dsg_src/java/org/pente/gameServer/server/DSGContextListener.java:273 — `new CacheTourneyStorer(` confirms the re-checking implementation is the production path
- dsg_src/httpdocs/gameServer/admin/manageTourney.jsp:78-85 — director drops players; :88-90 setInitialSeeds then createFirstRound
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:68-79 — rating restriction added only when rrType != 0
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:81-85 — GAMES_RESTRICTION_ABOVE 20 added only when erType == "1"
- dsg_src/httpdocs/gameServer/admin/newTourney.jsp:136 — rrType "None" radio is `selected`; :144-145 — erType radios have no default
- dsg_src/sql/schema.sql:331-337 — dsg_tournament_player PRIMARY KEY (event_id, pid), blocks duplicate-signup corruption
- dsg_src/java/org/pente/gameServer/tourney/Restriction.java:11-13 — only three restriction types exist; two of the three are re-checked

UNCLEAR (adjacent, not part of this claim):
- dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:77-84 — zero-rated-sets gate hides the form; effect on a first Renju tournament not verified
- signupEnd deadline enforcement on the POST path — not verified

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/tournamentSignup.jsp:22-23

### [GAP] Operator trap: default 1600 makes most rating restrictions all-or-nothing for Renju

Because every player's Renju rating reads as exactly 1600 with 0 games until they finish a rated Renju set, a RATING_RESTRICTION_ABOVE with value > 1600 excludes literally every signup, and a RATING_RESTRICTION_BELOW with value <= 1600 does the same. There is no warning or guard anywhere for setting a rating restriction on a game with no rating history. For the first Renju tourney the only safe configuration is no restrictions at all.

*Evidence:* dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerGameData.java:58; dsg_src/httpdocs/gameServer/tournaments/tournamentConfirm.jsp:95-96,106-107

### [OK] Tournament creation dropdown lists Turn-based Renju distinctly

newTourney.jsp enumerates GridStateFactory.getAllGames() generically and labels via getDisplayName(). TB_RENJU_GAME is present in allGames (:153) and displaygames carries new Game(TB_RENJU, "Turn-based Renju", false) (:186), so getDisplayName(81) returns 'Turn-based Renju' — visually distinct from the live 'Renju' (31) entry. No mis-selection hazard and no hardcoded game whitelist to extend.

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:117-121; dsg_src/java/org/pente/game/GridStateFactory.java:139-154, 155-186, 434-441

### [GAP] Tournament listing 'Current Tournament Champs' panel has no Renju entry

Cosmetic only. index.jsp builds a hardcoded per-game champions roster (getLastTBTourney / getLastPenteOpenTBTourney / getLastPenteUnder1800TBTourney at :22-48, plus the explicit Tourney variables at :73-91) with no GridStateFactory.TB_RENJU entry. A Renju tourney still appears in the signup and current lists, which iterate generically at :154-163, and no code path throws — it simply never shows up under the champs table after completing.

*Evidence:* dsg_src/httpdocs/gameServer/tournaments/index.jsp:22-91, 154-163

### [GAP] Living guide coverage of ratings and tournaments

The 1479-line guide contains no ratings or tournament section for Renju — the only occurrence of 'rating' is an incidental field inside a JSON payload sample at :1440, and there are no 'tourney'/'tournament' hits describing this path. Neither the game-81 rating pool, the K=32 choice, nor the seeding prerequisite is documented, so an operator following the guide has no warning about the blocker above.

*Evidence:* docs/renju-integration-guide.md:1440

## Completeness critic (lifecycle steps no auditor covered)
Area verdict: **gaps**

### [GAP] Undo during the Taraguchi-10 opening is neither blocked nor rolled back (renju_swaps/renju_offers desync)

NEW — no auditor touched undo. MoveServlet.requestUndo explicitly refuses opening-phase undos for two families only: TB_DPENTE/TB_DKERYO when getNumMoves()<5 (:237-241) and swap2 when getNumMoves()<6 (:242-246). There is NO TB_RENJU guard, so an undo can be requested and accepted at any point inside the 10-move Taraguchi opening. The JSP button is gated only on `game.getDPenteState() != 2` (mobileGame.jsp ~:355), which is meaningless for Renju. On accept, MoveServlet:299 calls CacheTBStorer.undoLastMove(gid, numMoves), which only deletes the MAX(move_num) tb_move row per iteration (MySQLTBGameStorer:361-366) and calls TBGame.undoMove() — and undoMove is literally `moves.remove(moves.size()-1)` (TBGame:267-269). Nothing clears renjuSwaps, nothing clears renjuOffers, and nothing reverses the physical player1Pid/player2Pid flip that TBGame.renjuSwap performs (:575-592, which sets both the pids and `renjuSwaps = st.encode()`). Result: the packed opening word still records decisions whose moves no longer exist, RenjuState.reconstruct(moves, renjuSwaps, renjuOffers) replays a decided opening against a shortened move list, and seatsSwapped()/getOriginalPlayer1Pid() (:614-622, :633-640) stay flipped — which is exactly the helper the tourney result write depends on. Mitigations: undo needs opponent consent and is subscriber-only (hasPlayerDonated, MoveServlet:254-258). Nothing exempts tournament games. Not verified at runtime (read-only audit) — the desync is established from the code path, not observed.

*Evidence:* dsg_src/java/org/pente/turnBased/web/MoveServlet.java:236-247, :281-299; dsg_src/java/org/pente/turnBased/CacheTBStorer.java:207-232; dsg_src/java/org/pente/turnBased/TBGame.java:267-269, :575-602, :614-622; dsg_src/java/org/pente/turnBased/MySQLTBGameStorer.java:351-366; dsg_src/httpdocs/gameServer/tb/mobileGame.jsp:~355-361

### [GAP] No deploy step applies dsg_src/sql/2026-06-14-renju-opening-state.sql to production

NEW — deploy was flagged in my brief and no auditor covered it. There is no migration runner: not in the boot listener, not in the Dockerfile, not in either init script, not in either deploy script. dbAlwaysInit only recreates DB users; dbInit's gzipped dump runs only when the MariaDB volume is empty. So the two Renju opening columns (tb_game.renju_swaps / renju_offers, tb_game_ai, pente_game.renju_swaps, pente_renju_offer) reach an existing prod database only by a human running the migration by hand. If it was not run on prod, the first Renju move that triggers the opening UPDATE (MySQLTBGameStorer:1414, :1457, :1496) fails and TB Renju is unplayable regardless of tournament wiring. Auditor 5 confirmed the columns exist in the LOCAL dockerMain/db datadir; prod could not be inspected (read-only, no prod access). Note the dbInit dump is dated 20260615, one day after the migration, so a fresh prod install would be fine — an in-place upgrade would not. Recommend the operator verify `SHOW COLUMNS FROM tb_game LIKE 'renju%'` on prod before creating the tournament.

*Evidence:* build_and_deploy.sh (whole file — rsync dsg_src/java→deploy/, `docker compose build`, push, ssh restart; no SQL anywhere); sync_gameServer.sh:1-30 (rsync httpdocs + react builds, then ./justCompile); dockerMain/dbAlwaysInit/0001_init.sh (CREATE USER / GRANT only); dockerMain/dbInit/penteDBdocker-20260615.sql.gz (fresh-volume seed only); Dockerfile (no sql COPY — only mmai, httpdocs, lib, deploy, build-docker.xml); grep for '.sql' in dsg_src/java returns only java.sql imports

### [UNCLEAR] Prod currency of the Renju server classes (image rebuild) vs JSPs (rsync)

NEW. The two halves of a Renju deploy travel by different mechanisms: JSPs (mobileGame.jsp, statusRound*.jsp, newTourney.jsp) go over rsync and take effect on the next Tomcat reload, but every Java class touched by the Renju merge (RenjuState, RenjuTimeoutDrawEvaluator, CacheTBStorer, TBGame, MoveServlet, GridStateFactory, CacheTourneyStorer) only reaches prod through a full image rebuild + push + container restart. A JSP-only sync would leave prod serving Renju tournament pages against a pre-Renju server. Additionally the build files themselves (Dockerfile, both compose files) have uncommitted local modifications, so the image a rebuild would produce today is not the image described by HEAD. I cannot inspect prod, so whether e55f161/f7db119 are actually live there is unverified — flagging as a required pre-flight check, not as a defect.

*Evidence:* build_and_deploy.sh (java rsynced to deploy/ then baked into the image via Dockerfile:30 `COPY deploy /usr/local/tomcat/webapps/tmp_src` + Dockerfile:31 build-docker.xml); sync_gameServer.sh:5 (`rsync dsg_src/httpdocs/gameServer/ debian@pente.org:~/dockerMain/gameServer/`) then `./justCompile`; git status: Dockerfile, docker-compose.yml, docker-compose-replica.yml modified and uncommitted

### [GAP] completeTourney throws NPE when the tourney has no prize, leaving completion half-applied

NEW — no auditor examined the completion/crown step. newTourney.jsp:87-89 sets the prize only when the parameter is non-null and non-empty, so an operator who clears the field (its rendered value at :150 is the placeholder string 'gold,silver,or any other text') creates a tourney with prize == null. At completion, CacheTourneyStorer.completeTourney:295 calls getCrownInt(tourney.getPrize()) and :623 dereferences it — NPE. Ordering makes the failure partial rather than clean: backingStorer.completeTourney already ran at :282 (dsg_tournament_detail gets completion_date + status='C'), but persistTourney(:311), moveEid(TOURNEY_LIST_CURRENT → TOURNEY_LIST_COMPLETED)(:312-314) and startAnotherTourney(:315-316) are all skipped. The tourney is complete in MariaDB while Redis still lists it as current, so tournaments/index.jsp keeps showing it in progress until the cache is rebuilt. Pre-existing and game-agnostic (not a Renju regression) and fully avoidable by leaving any text in the Prize field — but it sits on the last step of the lifecycle this audit is scoped to. Static analysis only; not reproduced.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:621-623 (`private int getCrownInt(String prizeStr) { int crownInt = 0; prizeStr = prizeStr.toLowerCase();` — no null guard), :282, :295, :311-316; dsg_src/httpdocs/gameServer/admin/newTourney.jsp:87-89, :150

### [GAP] round_length_days is collected and stored but never enforced — a TB round has no deadline

NEW. The value round-trips through the form, the DB and the model and is read by exactly one JSP line that is suppressed for turn-based games. No scheduler, timer or sweep consumes it — CacheTourneyStorer.checkRoundStatus is the sole TB advancement trigger and fires only on match results. So a TB Renju round ends only when every match reports; the only backstop is the per-move clock (TimeoutCheckRunnable, CacheTBStorer:557-561, :704-742), which does correctly forfeit or draw an absentee. That is adequate for a silent player but provides no recovery for the draw→RESULT_UNFINISHED stall the other auditors confirmed (CacheTBStorer:1255-1259) — the live-room round cutoff that force-sets DBL_FORFEIT (TournamentServer.java:340-368) is gated on main-room presence and never runs for TB. Net: the operator has no automatic round deadline, only the admin forfeit button.

*Evidence:* dsg_src/httpdocs/gameServer/admin/newTourney.jsp:36-38 (required parse); dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:242, :249, :565, :583 (write + read back); dsg_src/java/org/pente/gameServer/tourney/Tourney.java:114-118 (getter/setter); dsg_src/httpdocs/gameServer/tournaments/tourneyDetails.jsp:36 (only consumer, and auditor 4 confirms :34 suppresses that row for game>=50); repo-wide grep for getRoundLengthDays outside those files returns nothing

### [OK] Both paired players are notified (site message + push) when a tourney set is created

Checked because my brief called it out and no auditor covered it. createTournamentSet sends one message per player after createSet, each naming the game via getGameName(game) (resolves for 81), the opponent, the round number, the days-per-move, and a statusRound.jsp?eid=..&round=.. link, then fires a push through notificationServer.sendMessageNotification. Entirely game-agnostic — nothing to add for Renju. Minor cosmetic note only: the push sender label is the literal string "rainwolf" at :2232/:2246 ("pente.org" is used elsewhere at :328), pre-existing and unrelated to Renju.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2216-2247 (two DSGMessages built, dsgMessageStorer.createMessage, notificationServer.sendMessageNotification at :2232 and :2246); GridStateFactory.getGameName(81) = "Renju"

### [OK] Tourney TB games get a real per-move clock (initial_time, not the ignored incremental)

Verified because a wrong wiring here (passing the deliberately-ignored incremental_time) would give every tourney game 0 days per move and instant timeouts. insertMatch passes getInitialTime(), matching the form label, so a Renju tourney game gets the operator's chosen days-per-move. No gap.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:483-497 (`this.tbStorer.createTournamentSet(t.getGame(), p1, p2, t.getInitialTime(), t.getEventID())`); dsg_src/httpdocs/gameServer/admin/newTourney.jsp:127 ("Initial time: (Minutes for live, days for TB)"); dsg_src/java/org/pente/turnBased/CacheTBStorer.java:2187-2191 (tbg1.setDaysPerMove(daysPerMove))

### [OK] A finished tourney Renju game archives with its opening state intact

Checked because losing the packed swap word on archive would make every completed tourney Renju game replay with the wrong colours in Games History — a silent corruption of the tournament record. It is wired: the TB completion path copies both fields onto the GameData before storeGame, pente_game carries renju_swaps and the offers go to the pente_renju_offer side table, and the load path restores both. No gap.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:1038-1039 (`gameData.setRenjuSwaps(game.getRenjuSwaps()); gameData.setRenjuOffers(game.getRenjuOffers());`); dsg_src/java/org/pente/game/MySQLPenteGameStorer.java:348 (insert column list includes renju_swaps), :412-417 (offers written to pente_renju_offer), :759-761 and :794-807 (read back); dsg_src/java/org/pente/turnBased/TBGame.java:747-748

### [OK] Archive round/section and event-name derivation for a tourney game (swap-safe; filed under the base game id)

Two things checked that no auditor did. (a) Ordering and seat safety: the archive block runs at ~:970, well before the tourney result is written at :1259, so the match is still 'unplayed' and resolvable; and unlike some sibling code it tries getUnplayedMatch(p1,p2) then getUnplayedMatch(p2,p1), so a Taraguchi seat flip does not lose the round/section. (b) Filing: the archive resolves site and event through GridStateFactory.getGameId(data.getGame()) where data.getGame() is the NAME getGameName(81)="Renju" → id 31, so archived tourney Renju games are stored with pente_game.game=31 and an event row created under game 31 named after the tourney. That is the same convention every TB game has always followed (TB Pente archives as "Pente"/1) and it matches what the status pages' "View Games" link submits, so it is self-consistent. Operator expectation only: Renju tourney games land in the live-Renju archive bucket, not under 81.

*Evidence:* dsg_src/java/org/pente/turnBased/CacheTBStorer.java:~968-982 (loop over getCurrentTournies(); getUnplayedMatch tried in BOTH pid orders; setRound/setSection from the match) versus the result write at :1250-1260; dsg_src/java/org/pente/game/MySQLPenteGameStorer.java:360-378, :385 (`stmt.setInt(18, GridStateFactory.getGameId(data.getGame()))`)

### [OK] Crown / tourney_winner recorded against game 81 on completion, and the auto-chained follow-on tourney

Verified because 'finish + ratings' includes the crown, which nobody checked. assignCrown/removeCrown are parameterised on game, so the crown lands on the winner's game=81 rating row, and SimpleDSGPlayerData.getTourneyWinner picks the best crown across all a player's games generically — a Renju champion's crown displays like any other. Worth telling the operator: CacheTourneyStorer.completeTourney:314-316 calls startAnotherTourney for every turn-based tourney, so completing the first Renju TB tournament automatically creates a follow-on one (game 81 falls to the generic branch auditor 2 verified at :815). That is intended behaviour, not a defect, but it is a surprise if unexpected.

*Evidence:* dsg_src/java/org/pente/gameServer/tourney/MySQLTourneyStorer.java:827 (`update dsg_player_game set tourney_winner=? where pid = ? and computer = 'N' and game = ?`), :850 (removeCrown, same shape); dsg_src/java/org/pente/gameServer/tourney/CacheTourneyStorer.java:304-309, :314-316; dsg_src/java/org/pente/gameServer/core/SimpleDSGPlayerData.java:527-538
