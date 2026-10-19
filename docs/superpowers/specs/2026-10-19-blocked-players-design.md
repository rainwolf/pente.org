# Blocked players in the mobile apps

Date: 2026-10-19. Repos: `pente.org` (server), `penteLive-iOS`, `penteLive-Android`.

## Intent

Players can already ignore others on the web (`myprofilePrefs.jsp`). The mobile apps have no way to do it. The server endpoint `/gameServer/ignored` (`ChangeIgnoredServlet`, merged in pente.org#37) exists but nothing calls it. This project gives app users a way to block a player from an opened message or a personal invitation, and to review and lift blocks in settings.

## Goals

1. A Blocked players screen in each app, reached from the extra settings next to "Change my name color" / "Change my avatar".
2. A block button on an opened message (blocks messages).
3. A block button on a tapped personal turn-based invitation (blocks invitations and declines it).
4. Reply hidden on a message whose sender the reader has blocked for messages.
5. The servlet only accepts logged-in players, and only adds real, active players.

## Non-goals

- No shadow ban. Owner decision: mimic current server behavior. A blocked sender keeps getting the existing errors ("Player is ignoring your messages." / "Player is ignoring your invitations."). `DSGMessageServlet` and `NewGameServlet` are not changed.
- No block button on public (open) invitations, sent invitations, or the live game room invitation dialog.
- No "add a player by name" field on the Blocked players screen.
- No web UI changes.

## Anti-goals

- A block that silently fails: every block or unblock call must show an error alert on failure and leave the UI unchanged.
- Breaking iPad layouts (see `penteLive-iOS/CLAUDE.md`).

## Server (`pente.org`)

`dsg_src/java/org/pente/gameServer/client/web/ChangeIgnoredServlet.java`:

1. Login: keep `loggedInPlayer()` (401 for no login or guest). Auth comes from `LoginFilter`, which accepts `name2`/`password2` params or the login cookie. Both apps already carry the cookie.
2. `add`: reject when the target is missing **or** `!isActive()` with the same 404 "Player to ignore not found" (matches `DSGMessageServlet`).
3. `add` on an existing entry: merge instead of 409. Set the requested flags on top of the existing ones (OR), save with `updateIgnore`, call `changed()`, return success. Reason: a player blocked for invitations can then be blocked for messages from the message screen without the client reading the list first. Same merge semantics as `ReplyInvitationServlet`'s `ignore=Y`. (Builder's call: nothing calls `add` yet, so the 409 contract has no consumers.)
4. Unit test class for the servlet, added to the `ant test` list in `build.xml`, in the style of `org.pente.message.test.MessageJsonServletTest` (no DB). Cover: guest/no login → 401, unknown name → 404, inactive → 404, self → 400, add new, add merge, update to both-false removes, remove.

Invitation blocking does not use `replyInvitation`'s `ignore=Y`. That path swallows save errors (`ReplyInvitationServlet` ~L152-168 only logs them) and never calls `notifyIgnoreListeners`, so the live server would not reload the ignore list. The apps instead call `ChangeIgnoredServlet` `add` first and only decline normally once that succeeds (see Invitation block). (Builder's call, after review.)

Test approach is left to the builder. Server tests use JUnit 3.7 with no mocking library, so either write small fakes for `DSGPlayerStorer` or pull the validation and merge logic into a plain helper and test that.

## Shared client behavior

API: `GET /gameServer/ignored` → `{success, ignores:[{ignorePid, name, ignoreInvite, ignoreChat}]}`. `POST` form-encoded: `action=add&ignoreName=&ignoreInvite=Y|N&ignoreChat=Y|N`, `action=update&ignorePid=&ignoreInvite=&ignoreChat=` (both N removes). Use the app's authed request path with the dev localhost switch. HTML login page or `success:false` = failure.

Icon: a "no entry" road sign, red disc with a white horizontal bar. It is not a slashed circle.
- iOS: SF Symbol `minus.circle.fill` with palette colors white (bar) and system red (disc), rendered as original so the nav bar tint does not recolor it.
- Android: new vector drawable `ic_block_player.xml` (red circle, white bar). It is not `ic_do_not_disturb`, which is a slashed circle. Do not apply `toolbar_icon_tint` to it.

Name matching between the ignore list and message authors is case-insensitive.

### Blocked players screen

- iOS: `BlockedPlayersViewController` (Swift). Pushed from a new `IASKButtonSpecifier` row "Blocked players" (key `blockedPlayersButton`) in `Settings.bundle/MoreSettings.plist`. It goes in a new `PSGroupSpecifier` group after the subscriber-only group, so it does not read as a subscriber feature. It is handled in `SettingsViewController settingsViewController:buttonTappedForSpecifier:`. Available to all logged-in players, not just subscribers.
- Android: `BlockedPlayersActivity`, registered in the manifest like `SettingsActivity`. Opened from a new full-width "Blocked players" button below `avatarButton` in `activity_settings.xml` (re-point the `layout_below` chain).
- Layout: a dropdown at the top with **Messages**, **Invitations**, **Both**, then a list of player names. Default filter: Both.
  - Messages lists entries with `ignoreChat`. Invitations lists entries with `ignoreInvite`. Both lists every entry, with a subtitle per row: "messages", "invitations" or "messages and invitations".
  - iOS dropdown: a `UIButton` with a `UIMenu` (iOS 15), or a segmented control if simpler. Builder's call, but it must work on iPhone and iPad.
  - Android dropdown: `AppCompatSpinner`, matching `live_table_settings.xml`.
- An empty state message appears when the filtered list is empty.
- Swipe a row to reveal Unblock.
  - iOS: trailing swipe action.
  - Android: `ItemTouchHelper` on a `RecyclerView`, restoring the row on cancel.
- The swipe opens a confirmation: title "Unblock <name>?", message "<name> will be able to send you <messages | invitations | messages and invitations> again." Confirming calls `update` and clears only the filtered kind. Under Both it clears what the row has. The list then reloads from the reply's `ignores`.

### Message block

- iOS `MessagesViewController`: a third `rightBarButtonItems` entry in reply mode (`messageID != nil`).
- Android `ReplyMessageActivity`: a third item in `res/menu/message_menu.xml`.
- Tapping opens a confirmation:
  - Title: "Block <name>?"
  - Message: "You will no longer receive messages from <name>." followed by "You can unblock players in Settings."
  - Buttons: Cancel and Block (destructive).
- Confirming calls `add` with `ignoreChat=Y`, `ignoreInvite=N` (the server merges). On success, reply is hidden. The message stays readable and is not deleted.
- On opening a message, the app fetches the ignore list. If the author is blocked for messages, the reply view and send button are hidden, and the block button is hidden too since the sender is already blocked.
  - iOS: `replyMessageView`, `sendButton`. `viewWillAppear` un-hides `sendButton`, so it needs the same guard.
  - Android: `reply`, `sendButton`, plus the `KeyboardVisibilityEvent` listener that toggles `sendButton`.
  - If the fetch fails, reply stays visible. The server does not stop such replies; this is client-side only, matching web.

### Invitation block

- Only on personal received invitations:
  - iOS: `INVITATIONSSECTION`, `addButtonsToCell:` in `GamesTableViewController.m`.
  - Android: the `INVITATIONSGROUP` branch in `MainActivity`'s child click listener.
- A square button with the icon sits to the right of the existing three buttons.
  - iOS: the existing three are cancel / accept / decline. The square's side equals the existing button height. Re-derive spacing for four buttons from the cell's content width, not `UIScreen` width, so iPad and split view lay out correctly. Add and remove it alongside the others in `addButtonsToCell:` / `removeButtonsFromCell`.
  - Android: the visible three are dismiss / decline / accept in `dashboardrow_layout.xml`. Add an `ImageButton` at the end of that row, `gone` by default and shown only for `INVITATIONSGROUP`. It is hidden everywhere the code hides the other buttons (`viewWithOpenButtons`).
- Tapping opens a confirmation:
  - Title: "Block <name>?"
  - Message: "This declines the invitation and you will no longer receive invitations from <name>." followed by "You can unblock players in Settings."
  - Buttons: Cancel and Block (destructive).
- Confirming runs two steps:
  1. Call `add` with `ignoreName=<opponentName>&ignoreInvite=Y&ignoreChat=N`. The server merges this with any existing block. If it fails, show an error alert, leave the invitation as it is, and stop.
  2. Run the existing decline path unchanged, with its existing removal and refresh:
     - iOS: `rejectInvitation:`.
     - Android: `respondInvitation(..., false, ...)`.

  If the decline fails after the block succeeded, the existing decline error handling applies and the block stays in place.

### Strings

New strings in English, plus translations in the locales each app ships: iOS app strings in `zh-Hans.lproj/Localizable.strings` (UTF-16). For the settings row, `MoreSettings.plist` has no `StringsTable` key, so InAppSettingsKit looks up `MoreSettings.strings`. Add new UTF-16LE `Settings.bundle/{en,zh-CN}.lproj/MoreSettings.strings` holding only the new row's strings, and leave `Root.strings` and the existing rows untouched. Android: `values-de`. (Builder's call: machine translation is acceptable. The owner may refine it.)

## Testing

- Server: the unit tests above, plus `./justCompile` on the local stack and a scripted check of the servlet over HTTPS (Python `urllib`, unverified SSL; `curl` is hook-blocked). Cover login, unknown name, inactive, merge, update and remove.
- iOS unit tests (`PenteEngineTests`): ignore-list JSON parsing, the filter logic and the confirmation text builder.
- Android unit tests (`src/test`): the same.
- Automated E2E against the running local stack, with test accounts `iostest` and `graviton` (creds in `pente-react-native/.env.local`, never committed).
  - iOS: a throwaway XCUITest in an rsync copy of the repo with `development YES`, run headless on iPhone 18 Pro and iPad Pro 13-inch. Trust `pente.org/localhost.crt` in the sim.
  - Android: an adb + `uiautomator dump`/`input tap` script on the `pentelive_api37` AVD, with `PentePlayer.development = true` in a scratch copy only. Use the Android Studio JBR as `JAVA_HOME`.
  - Seed data with HTTP posts as the other account: message via `/gameServer/mymessages command=create`, personal invitation via `/gameServer/tb/newGame invitee=`.
  - Scenarios on both apps:
    - Block from a message, then confirm reply is hidden and the sender's next send gets the server error.
    - Block from a personal invitation, then confirm it is declined and the inviter's next invite is rejected.
    - Confirm public invitations show no block button.
    - On the Blocked players screen, check each filter, then swipe to unblock, cancel (row restored), and confirm (entry updated or removed).
    - Confirm both dialogs contain the settings line.
  - E2E harnesses live in the session scratch dir, never in the repos. The dev switches are never committed.

## Delivery

Branch `blocked-players` in each repo, signed commits, one PR per repo. Server deploy order: classes before JSPs (none here). The server change is backward compatible, so it can ship before the apps.
