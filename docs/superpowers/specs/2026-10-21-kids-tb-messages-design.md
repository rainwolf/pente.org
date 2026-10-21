# Kids accounts: turn-based messages for parents — design

Follow-up to `2026-10-19-kids-accounts-design.md` (shipped: pente.org#48, react_live_game_room#14, penteLive-Android#31, pentelive-ios#44).

## Intent

The kids feature gives parents email copies of their kid's private messages and live-room chat, but turn-based (TB) game messages were never covered. Today a parent can neither receive them nor read them: every server path that returns TB message text admits only the two players (plus `rainwolf`/admin on some paths). The owner wants parents to see the full conversation between their kid and the kid's opponent, both by email and by opening the game.

## Goals

1. **Parent can read TB messages in the kid's games**, on web, Android and iOS. Includes private and hidden games. Read-only: the parent never moves, replies, cancels or undoes.
2. **Active TB games list on the My Kids page**, per kid, on web and in each app's kid screen. Each row opens the game read-only. Finished games are not listed; the parent finds them through the kid's profile history (owner decision): `playerstatsbox.jsp:31` → `/gameServer/viewLiveGames` (`ViewGamesServlet`) → `FastMySQLDSGGameLookup.search`, rows open `viewLiveGame.jsp`. That query hides private games unless the viewer is a player (`FastMySQLDSGGameLookup.java:234-235`, `g.private = 'N' or g.player1_pid = ? or g.player2_pid = ?`, and the mirrored player-2 half). It must also admit the profile player's parent for **turn-based games only**, so the kid's finished private TB games appear in the parent's view of the kid's history. Private live games stay hidden from the parent: they carry no chat, so there is nothing for the parent to read (owner decision 2026-10-21). Count/total queries that feed the paging should stay consistent with it.
3. **End-of-game transcript email**, behind a new per-kid toggle. One email per TB game when it ends: every message in that game from both sides, in move order, with author and time, plus a link to the game page.

## Non-goals

- Deep links / universal links so the email link opens in the app. Neither app supports app links today; the email link opens the website. (Builder's/controller's call, out of scope.)
- Changing live-room transcripts (per session, not per game) or their crash behaviour.
- Filtering the boilerplate TB system notices (win/loss, dead stones) that already reach parents through the PM copy path.
- Feedback to a kid whose TB text was dropped by the whitelist.
- A finished-games list on the My Kids page.

## Anti-goals (failure even if it "works")

- A parent gains any write ability on the kid's game (move, message, cancel reply, undo, resign, draw).
- Any non-parent non-player gains access to messages or to private/hidden games.
- A transcript email for a game whose kid has the toggle off, or to anyone but the kid's linked parent.
- An email per message or a burst of emails at once for one game.
- Game end is delayed or fails because the email failed.

## Data model (decided)

- New column `dsg_kid_settings.copy_tb_messages char(1) default 'N'`, migration file `dsg_src/sql/2026-10-21-kid-tb-copy.sql` plus `schema.sql`. Flows through `KidSettings`, `MySQLKidStorer`/`CacheKidStorer`, the `/gameServer/kids` settings action and its JSON, like `copy_messages`/`copy_live_chat`.
- No new table. The transcript is built from the game's stored messages at game end.

## Rules

### Who counts as the parent

The viewer is the parent of a player in the game when that player's `parent_pid` equals the viewer's pid **at request time**. A released kid (no `parent_pid`) ends parent access immediately.

### Viewing (server)

Every gate that decides whether message text is returned also admits the parent of either player. Known gates (verify complete during planning):

- `turnBased/web/MoveServlet.java:137-158`: private/hidden refusal for non-players gets a parent exemption; `showMessages` admits the parent.
- `httpdocs/gameServer/tb/mobileGame.jsp:64` (the page MoveServlet actually serves for both desktop and mobile).
- `httpdocs/gameServer/mobile/json/game.jsp:39-41` (`canSeeMessages`, used by Android and iOS).
- `httpdocs/gameServer/mobile/game.jsp:66`, `tb/deadGo.jsp:63`, `tb/finalGo.jsp:63` (line 62 is a commented-out older check; confirm the live one).
- `httpdocs/gameServer/viewLiveGame.jsp:111-115` (finished TB games opened from the game database) and `viewLiveGameMobile.jsp:106-111`.

The parent check lives in one shared helper used by all gates, not copied into each JSP.

**Parent sees the whole conversation** *(controller's call, from the owner's "full conversation with the kid's opponent")*: all stored messages of the game, including the opponent's open-invitation text that `KidInvitationText.hidden()` hides from the kid. The transcript email includes it too. Adding the `KidInvitationText` filter to `mobileGame.jsp` for kid viewers (today only `tb/game.jsp` and `mobile/json/game.jsp` apply it) is a pre-existing gap and out of scope.

The parent's view is read-only everywhere: the server must reject any write (move, message, cancel, undo, resign, draw) from a parent who is not a player, regardless of client behaviour. Existing non-player rejection should already cover this; planning confirms it per servlet.

### Active games list

- Server: a new read endpoint (or a new action on `/gameServer/kids`) returning, for a kid the caller is parent of, the kid's active TB games: gid, opponent name, game type, whose turn, last move time. Refuse for anyone else.
- Web My Kids page (`myprofileKids.jsp`): per-kid list, each row links to the existing TB game page.
- Android `KidDetailActivity`, iOS `test1/KidDetailViewController.swift`: per-kid list; tapping opens the existing read-only board path (`BoardActivity` with `setActive(false)` / `BoardViewController` with `activeGame=false`).

### Transcript email

- Toggle label: "Turn-based game messages", off by default, next to the two existing toggles on web and both apps.
- Trigger: a TB game that **was started** (at least one move played after the invitation was accepted) reaches an end state: win, loss, draw, resign, timeout via `CacheTBStorer` end-game handling, or an active set cancelled by agreement. Each game of a two-game set is its own email. Planning enumerates every end path (including timeouts and admin actions) and hooks one place if possible.
- Not a trigger *(controller's call)*: invitations that never became games. `cancelSet` (`CacheTBStorer.java:1968`) also runs for an inviter withdrawing (`CancelInvitationServlet.java:145`), an invitee declining (`ReplyInvitationServlet.java:345`), stale open-invite cleanup (`CacheTBStorer.java:464`) and the `STATE_NOT_STARTED` branch (`:1991`); none of these send a transcript.
- Sent when the game has at least one stored message (invitation text at move 0 counts); no messages → no email.
- Content: subject naming kid, opponent and game; body lists each message with move number, author and time, oldest first, then a link `https://www.pente.org/gameServer/tb/game?gid=<gid>&command=load`. Plain text, built in `KidCopyEmails`, sent through `KidCopyMailer` via the existing background mailer and `usableParent` rule (same as PM copies).
- Both players kids: each parent with the toggle on gets their own email (mirrors `copyPrivateMessage`).
- Toggle and parent link are read **at game end**, not at game start. Turning it on mid-game still sends the full conversation at the end.
- Never throws into game-end processing; failures are logged.

## Clients

- **Android**: no change for message display (renders whatever the server returns; non-player is already read-only). Adds the toggle and the active-games list.
- **iOS**: renders whatever the server returns, but has two non-player bugs to fix for parents: (a) labels assume the viewer is player 2 when not player 1 (`BoardViewController.m:2196` `iAmP1`); (b) a pending cancel request prompts a non-player to answer it. Adds the toggle and the active-games list.
- **Web**: toggle and list on `myprofileKids.jsp`.

## Left to the builder

- Endpoint shape for the active-games list.
- Exact email wording and subject format.
- Where the single parent-check helper lives.
- Ordering of messages with equal timestamps (move order is authoritative).
