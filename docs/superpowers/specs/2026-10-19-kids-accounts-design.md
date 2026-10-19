# Kids accounts — design

Date: 2026-10-19. Repos: `pente.org` (server + website), `react_live_game_room`, `pentelive-android`, `penteLive-iOS`.

## Intent

Parents should feel comfortable letting their kids play on pente.org, and playing them. The idea comes from lichess kid mode, but chat is not switched off: a kid can talk only with players the parent approved. Games stay open, so a kid can play anyone.

Success means a parent can set up a kid in a few minutes from the website or either app. The kid can then play anyone and chat only with approved players. The parent can optionally get copies of the kid's messages.

## Goals

1. A parent (any normal account) creates kid accounts and sets their passwords.
2. A parent converts an existing account into a kid account, but only after confirmation from that account's registered email.
3. A kid account cannot change its own password or email. Forgot-password emails go to the parent.
4. A kid can exchange text only with players on the parent-managed whitelist, on every chat surface. Admin and system messages always reach the kid.
5. Optional parent email copies, as two separate toggles, both off by default:
   - **Private-message copies**: one email per private message the kid sends or receives, sent right away.
   - **Live-chat copies**: live-room chat lines the kid sends or can see are buffered in Redis for the session. One email goes to the parent when the kid disconnects, then the buffer is deleted.
6. Parents create and manage kids from the website, Android and iOS. The React live room shows kid-mode behaviour.

## Non-goals

- Restricting games. Kids can play anyone, rated or unrated, live or turn-based.
- Forums. They stay open to kids.
- Profile text, avatars, follow/social lists, store/subscriptions. These are not chat and are unchanged.
- Filtering messages that already exist. Enforcement happens only when text is written. A converted account keeps its existing inbox and TB game messages, and taking someone off the whitelist does not hide their past messages. (Decided by the user.)
- Changing how passwords are stored or emailed. Plaintext password emails are existing behaviour.
- Age verification or COPPA (the US children's online privacy law) compliance work.

## Anti-goals (failure even if it "works")

- A kid sees or receives text from a non-whitelisted player through any channel. Client-only hiding counts as a failure: enforcement must be server-side.
- A kid changes parent-owned settings (whitelist, copy toggles, password, email, parent link), including by forging websocket frames. The live room currently lets a client write any `dsg_player_prefs` row via `DSGPreferenceEvent`, so parent settings must not live in prefs.
- Chat copies are kept long-term. Live-chat buffers exist only until the email is sent.
- Copies are logged or emailed without the parent turning that toggle on.
- Admin or system notices stop reaching kids.

## Data model (decided)

- `dsg_player.parent_pid bigint(20) unsigned DEFAULT NULL`. Null means "not a kid". Migration file: `sql/2026-10-19-kid-accounts.sql`, idempotent `ADD COLUMN IF NOT EXISTS`, following `sql/2026-10-15-message-reply-to.sql`. It flows through `DSGPlayerData` / `SimpleDSGPlayerData`, the 4 SQL sites and the positional row mapper in `MySQLDSGPlayerStorer`, and the Redis-cached player blob. Use a nullable `Long`.
- **My call: kid status is private.** `DSGPlayerDataAdapter` serialises the whole player record to everyone in the live room, so `parentPid` must be excluded from that serialisation (transient, or stripped in the adapter). The kid flag reaches only the kid's own client, as a login-only field: `dsgLoginEvent` `me`, the mobile index JSON, and the `/gameServer/kids` endpoint. Clients read "kid" from that field only.
- **My call:** kids are forced to `mobile_adult = 'N'`, and `/gameServer/ageVerification` refuses to change it for kids.
- **My call:** new table `dsg_kid_whitelist(kid_pid, allowed_pid, last_update_date)` with a unique key on `(kid_pid, allowed_pid)`. It is cached and invalidated like `dsg_player_ignore`.
- **My call:** new table `dsg_kid_settings(kid_pid PK, copy_messages char(1) default 'N', copy_live_chat char(1) default 'N')`. It is parent-owned and is not a pref.
- **My call:** new table `dsg_kid_link_request(code, parent_pid, kid_pid, creation_date)` for conversion confirmations, following `dsg_email_verification`. Codes expire after 7 days.

## Rules (decided unless marked)

### Who counts as allowed for a kid

A sender and recipient may exchange text if, for each side that is a kid, the other side is one of these:
- on that kid's whitelist;
- the kid's own parent (**my call**: always implicitly allowed);
- an admin (`dsg_player.admin = 'Y'`);
- the system account `23000000016237` or the computer player `23000000020606`.

Introduce one shared constant for each of the two hard-coded pids. Kid-to-kid chat therefore needs each kid on the other's whitelist.

### Parent and kid lifecycle

- **My call:** a parent must have a verified email (`email_valid = 'Y'`), since resets and copies go there.
- **My call:** a kid cannot be a parent, and a parent cannot be a kid (no nesting).
- **Create.** The parent supplies username and password, with the same name and password rules as `/join`. The kid row is created with `parent_pid` set and the email set to the parent's email (**my call**). The registration email goes to the parent. No reCAPTCHA is needed because the parent is logged in. Forum (Jive) user creation follows `RegisterServlet`.
- **Convert.** The parent enters an existing username. The server emails a confirmation link to that account's registered email, and `parent_pid` is set only when the link is clicked. The account's email is unchanged. The account must not be an admin, a parent, or already a kid.
- **Release (my call).** The parent can release a kid, which sets `parent_pid` to null and deletes the whitelist and settings rows. The kid cannot release themself. Release only undoes the kid link. Once released, the account's owner can change the email and password through the normal profile flow, which already re-verifies a new email.
- **Parent actions** (website and apps): list kids, create, convert, set or reset a kid's password, edit the whitelist (add by username, remove), toggle the two copy options, release.

### Kid restrictions (all server-enforced)

- **Password and email.** `ChangeProfileServlet` rejects password and email changes for kids. Other profile fields are unchanged.
- **Forgot password.** `ForgotPasswordServlet` sends to the parent's email when `parent_pid` is set.
- **Parent-owned data.** Kids cannot write it. The kid's own `dsg_player_ignore` list stays as is, but clients hide the blocked-players screen for kids (**my call**).
- **`emailDsgMessages` pref.** Unchanged. It only controls the kid's own inbox emails.

### Chat surfaces and enforcement

| Surface | Choke point | Kid rule |
|---|---|---|
| Private messages | `CacheMessageStorer.createMessage` (all callers), plus a friendly error in `DSGMessageServlet` `create` | Reject if not allowed. The user-facing error text is a builder's choice. |
| Broadcast to followers | `BroadcastServlet` | **My call:** blocked when the broadcaster is a kid. Recipients that are kids skip the broadcast message unless the broadcaster is allowed. |
| Lobby chat | `ServerMainRoom.handleText` per-recipient loop | Deliver a line only to recipients where the allowed rule holds both ways. Kid text goes only to allowed players. Kids see only allowed players' text. |
| Table chat (players + spectators) | `ServerTable.handleText` per-recipient loop | Same as lobby. The AI player is allowed. |
| Live invite text / invite response text | `ServerTable.handleInvite` / `handleInviteResponse` | The invite itself goes through (games are open). The text is stripped when not allowed. |
| TB invitation text, move/resign messages | `CacheTBStorer.storeNewMessage` | The move or invite goes through. The text is not stored when not allowed. |
| TB cancel-request message | `CacheTBStorer.requestCancel` (`tb_set.cancel_msg`) | The cancel request goes through. The text is stripped when not allowed. |
| TB invitation decline PM (`ReplyInvitationServlet` appends invitee text) | `CacheMessageStorer` | The PM rule applies. Builder decides whether to strip the free text and still send the notice. |
| Opponent-attributed TB notices (dead stones, win/loss/draw from `CacheTBStorer`) | `CacheMessageStorer` | These are server-generated and must still reach kids. Mark them as system (for example a flag on the call) so the PM rule exempts them. |
| System table/room messages (`DSGSystemMessageTableEvent`, "game server" text) | n/a | Already bypass `handleText`. Unchanged. |
| Push notifications for messages/broadcasts | follow from the above | Not sent when the message is refused. |
| Forums | n/a | Open. |

The kid flag and whitelist are loaded once per live session in `ServerPlayer` (where `playerData` is loaded, around `:168`). They are refreshed through a listener when the parent edits, the same way `notifyIgnoreListeners` works.

### Parent copies

- **Private-message copies (`copy_messages = 'Y'`).** In `CacheMessageStorer.createMessage`, after a message involving a kid is accepted, queue an extra email to the parent through `BackgroundMailer`. It goes out right away, once per message, whether the kid sent or received it. The subject names the kid and the other player. The global `emailEnabled` switch still applies. Copies are sent whether or not the kid's own email is valid, but they need the parent's email to be valid.
- **Live-chat copies (`copy_live_chat = 'Y'`).**
  - While the kid is connected, every lobby or table line the kid sends or is delivered is appended to a Redis list keyed per kid session, with timestamp, room or table, author and text. System lines may be included (builder's choice).
  - On disconnect, the list is turned into one email to the parent and the key is deleted. Disconnect covers logout, socket close and server-side boot; the builder finds the hook (`ServerPlayer` has no clean one today).
  - A Redis TTL (time-to-live) of about 24 hours is a backstop if the server restarts before flushing. Nothing is written when the toggle is off.
  - If Redis is unavailable, live-chat copies are skipped (Redis is best-effort in this stack). **My call**, to be noted in the parent UI.
  - An empty session sends no email.

## Clients

- **Website (JSP).** A "My kids" page under `/gameServer/myprofile/` handles create, convert, list, password reset, whitelist, copy toggles and release, plus the confirmation landing page for conversion. Kids see password and email fields as read-only, with "managed by your parent".
- **JSON endpoints.** One servlet (for example `/gameServer/kids`) that the apps and the website both use. It authenticates via `LoginFilter` and every write checks `kid.parent_pid == caller pid`. Its shape is left to the builder, following `/gameServer/ignored`.
- **Android.**
  - A "Kid accounts" entry in `SettingsActivity` next to `blockedPlayersButton`, as native screens following the `blocked/` package pattern (**my call**: native, matching blocked players).
  - For a kid login: hide blocked players, the new-message composer limits recipients to the whitelist, the chat input hint says only approved players see messages, and broadcast is hidden.
  - Server refusal strings are handled where the app already matches error strings.
  - The kid flag comes through `PentePlayer` (Parcelable) from the index JSON.
  - The native live room (`LiveGameRoomActivity`, `LiveTableFragment`) gets the same kid-mode UI as React: a chat hint, hidden invite text, and handling of any refusal event.
- **iOS.**
  - The same as Android, via a `Root.inApp.plist`/`MoreSettings.plist` row handled in `SettingsViewController` and modelled on `BlockedPlayersViewController`/`BlockedPlayersService`.
  - Must work on iPhone and iPad (see `penteLive-iOS/CLAUDE.md`).
  - The native live room (`PenteLiveSocket`, `RoomViewController`, `TableViewController`) gets the same kid-mode UI as React.
- **React live room.**
  - No account UI exists there, so management stays on the website and React only links to it (**my call**).
  - Store `isKid` from `dsgLoginEvent` `me.parentPid`.
  - The chat input shows a hint that only approved players will see messages. Invite text fields are hidden for kids.
  - Handle `dsgTextTableErrorEvent` if the server sends one.

## Left to the builder

- Exact endpoint names, JSON shapes, error strings and email wording.
- Where the disconnect flush hooks in, and the Redis key layout.
- Whether to refuse loudly or strip silently for each surface marked "strip".
- Test strategy. Follow existing patterns: `core/test/IgnoreRulesTest.java` for pure rules, the app unit tests for blocked players. A pure `KidChatRules` class keeps the allowed rule in one testable place.
- Deploy order: SQL migration, then server, then apps. Apps must work against a server without the feature (no `parentPid` means not a kid).
