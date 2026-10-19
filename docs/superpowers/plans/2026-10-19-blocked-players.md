# Blocked Players Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App users can block a player from an opened message or a personal invitation, and review or unblock players from a Blocked players screen in settings.

**Architecture:** The server's `ChangeIgnoredServlet` (`/gameServer/ignored`) is the single write path. It is hardened, and `add` merges flags into an existing entry. Each app gets one small testable model/logic unit plus one network client, and the three UI touch points (settings screen, message screen, invitation row) call that client.

**Tech Stack:**
- Server: Java 21 / Tomcat, JUnit 3.7, Ant.
- iOS: ObjC + Swift 5, InAppSettingsKit, XCTest.
- Android: Java, AppCompat, RecyclerView, Gson, JUnit 4.

**Spec:** `pente.org/docs/superpowers/specs/2026-10-19-blocked-players-design.md`

## Global Constraints

- Repos: `pente.org`, `penteLive-iOS`, `penteLive-Android` under `/Users/waliedothman/mariposa/coding/pente.org-project/`.
  - Branch `blocked-players` in each.
  - Always use `git -C <repo>`.
  - Commit with `-S`.
  - No co-author or generated-by lines.
- No shadow ban. `DSGMessageServlet`, `NewGameServlet` and `ReplyInvitationServlet` are not modified.
- Block from an invitation is `add` (`ignoreInvite=Y&ignoreChat=N`) and then a normal decline. `ignore=Y` on `replyInvitation` is never used.
- Block from a message is `add` with `ignoreChat=Y&ignoreInvite=N`.
- Block buttons appear only on opened received messages (reply mode) and on personal received invitations. They never appear on public, open or sent invitations.
- Icon is a red disc with a white horizontal bar.
  - iOS: SF Symbol `minus.circle.fill`, palette `[.white, .systemRed]`, `.alwaysOriginal`.
  - Android: new `res/drawable/ic_block_player.xml`, not tinted.
- Copy (English, exact). `<name>` is the other player's name. `<kind>` is `messages`, `invitations` or `messages and invitations`.
  - Settings row and screen title: `Blocked players`
  - Filter labels: `Messages`, `Invitations`, `Both` (default `Both`)
  - Empty state: `No blocked players`
  - Swipe action: `Unblock`
  - Unblock dialog: title `Unblock <name>?`, message `<name> will be able to send you <kind> again.`, buttons `Cancel` and `Unblock`
  - Message block dialog: title `Block <name>?`, message `You will no longer receive messages from <name>.\nYou can unblock players in Settings.`, buttons `Cancel` and `Block` (destructive)
  - Invitation block dialog: title `Block <name>?`, message `This declines the invitation and you will no longer receive invitations from <name>.\nYou can unblock players in Settings.`, buttons `Cancel` and `Block` (destructive)
  - Errors: title `Couldn't block <name>` or `Couldn't unblock <name>`. Message is the server `error` string, or `Please try again.` when there is none.
- Translations:
  - iOS: `zh-Hans.lproj/Localizable.strings` (UTF-16). Settings row in new `Settings.bundle/{en,zh-CN}.lproj/MoreSettings.strings` (UTF-16LE).
  - Android: `values-de/strings.xml`.
  - Machine translation is fine.
- iOS UI must work on iPhone and iPad (`penteLive-iOS/CLAUDE.md`).
- Dev switches (iOS `#define development`, Android `PentePlayer.development`) and test credentials are never committed. E2E harnesses live in `/private/tmp/claude-501/blocked-players-e2e/`, never in a repo.
- Server responses: a reply that is not JSON (expired session returns the HTML login page with 200) or that has `success:false` is a failure.
- Name comparison between the ignore list and message authors is case-insensitive.

## Review Focus

1. **Expired login session.** The app gets an HTML 200 response. Expect an error alert, no UI change, and no crash. Pinned by parse tests in Tasks 2 and 6.
2. **Names with spaces, `+`, `&` or non-ASCII.** The name must be form-encoded so the right player is blocked. Pinned by the form-encoding tests in Tasks 2 and 6.
3. **Case mismatch.** The author is shown as `Graviton` and the list holds `graviton`. Reply must still be hidden. Pinned by the `isChatBlocked` tests in Tasks 2 and 6.
4. **Partial unblock.** A player blocked for both, unblocked under the Messages filter, must stay listed under Invitations. Pinned by the `remainingFlags` tests in Tasks 2 and 6, and checked in E2E Tasks 10 and 11.
5. **Invitation row on iPad, in landscape and in split view.** The four buttons must not overlap or clip. Pinned in Task 5's layout step and in Task 10's iPad run.

---

### Task 1: Server — harden and merge in ChangeIgnoredServlet

**Files:**
- Create: `pente.org/dsg_src/java/org/pente/gameServer/client/web/IgnoreRules.java`
- Modify: `pente.org/dsg_src/java/org/pente/gameServer/client/web/ChangeIgnoredServlet.java` (the `add` method)
- Test: `pente.org/dsg_src/java/org/pente/gameServer/client/web/test/IgnoreRulesTest.java`
- Modify: `pente.org/build.xml`. Add the test class to the `test` target's fixed list, next to `org.pente.message.test.MessageJsonServletTest`.

**Interfaces:**
- Produces:
  - `static String IgnoreRules.rejectTarget(DSGPlayerData target, long myPid)`. Returns `"Player to ignore not found"` if `target == null || !target.isActive()`, `"You can't ignore yourself silly"` if it is the caller, else `null`.
  - `static boolean IgnoreRules.mergeFlags(DSGIgnoreData existing, boolean invite, boolean chat)`. ORs the flags into `existing` and returns true if anything changed.
  - HTTP: `POST action=add` on an existing entry now returns 200 with the merged list instead of 409.

- [ ] **Step 1: Branch.** `git -C pente.org checkout -b blocked-players`

- [ ] **Step 2: Write failing tests** in `IgnoreRulesTest` (JUnit 3 `TestCase`, plain `DSGPlayerData`/`DSGIgnoreData` objects, no DB):
  - `testNullTargetRejected`: `assertEquals("Player to ignore not found", rejectTarget(null, 1))`
  - `testInactiveTargetRejected`: target with `setActive(false)` gives the same string.
  - `testSelfRejected`: target pid 1, me 1, gives `"You can't ignore yourself silly"`.
  - `testActiveOtherAccepted`: `assertNull`.
  - `testMergeAddsChatKeepsInvite`: existing invite=Y chat=N, merge(false, true) returns true, both now Y.
  - `testMergeNoChange`: existing both Y, merge(true, false) returns false, both still Y.

- [ ] **Step 3: Run to see them fail.** From `pente.org/`, run `ant test-one -Dtest=org.pente.gameServer.client.web.test.IgnoreRulesTest`. Expected: compile failure, `IgnoreRules` not found.

- [ ] **Step 4: Implement `IgnoreRules`.** Then change `add`:
  - Replace the null and self checks with `rejectTarget`. Status 404 for "not found", 400 for self.
  - On an existing entry, call `mergeFlags`. If it returns true, call `updateIgnore` and `changed(me.getPlayerID())`. Then `success(...)`.
  - Update the class javadoc to say that `add` merges.

- [ ] **Step 5: Run the tests.** Same `test-one` command. Expected: `OK (6 tests)`. Then run `ant test`. Expected: all pass.

- [ ] **Step 6: Deploy locally and run an HTTP check.**
  - Deploy: `./justCompile` in `pente.org/`.
  - Write `/private/tmp/claude-501/blocked-players-e2e/server_check.py`. Use Python `urllib` with an unverified SSL context (curl is hook-blocked). Creds come from `pente-react-native/.env.local` (`TEST_USER_A_PASS`=iostest, `TEST_USER_B_PASS`=graviton). Pass them as `name2`/`password2` params.
  - Check these against `https://localhost/gameServer/ignored`:
    - No creds: 401.
    - `add ignoreName=nosuchplayerxyz`: 404.
    - `add` self: 400.
    - `add graviton invite=Y chat=N`: 200.
    - `add graviton invite=N chat=Y` again: 200 with both true.
    - `update` with both N: 200 with graviton gone.
    - `remove` of a pid that isn't ignored: 404.
  - For an inactive account, run `docker exec` into the MariaDB container, pick a player with `status` inactive (read the `dsg_player` schema first), and expect 404.
  - Expected: the script prints `ALL OK`.

- [ ] **Step 7: Commit.** `git -C pente.org add <files> && git -C pente.org commit -S -m "Ignore endpoint: reject inactive players, merge add into existing ignore"`

---

### Task 2: iOS — ignore list model, logic and client

**Files:**
- Create: `penteLive-iOS/test1/BlockedPlayers/BlockedPlayers.swift` (model and pure logic)
- Create: `penteLive-iOS/test1/BlockedPlayers/BlockedPlayersService.swift` (network and shared UI helpers)
- Test: `penteLive-iOS/PenteEngineTests/BlockedPlayersTests.swift`
- Modify: the Xcode project, to add the files to target `test1` and the test to `PenteEngineTests`.

**Interfaces:**
- Produces, in `BlockedPlayers.swift`:
  - `struct IgnoreEntry: Decodable, Equatable { let ignorePid: Int64; let name: String; let ignoreInvite: Bool; let ignoreChat: Bool }`
  - `enum BlockFilter: Int, CaseIterable { case messages, invitations, both }`, plus `var title: String` (localized `Messages`/`Invitations`/`Both`)
  - `enum BlockedPlayers`:
    - `static func parse(_ data: Data?) -> [IgnoreEntry]?`. Returns nil for nil, non-JSON or `success:false`.
    - `static func parseError(_ data: Data?) -> String?`
    - `static func filter(_ entries: [IgnoreEntry], by: BlockFilter) -> [IgnoreEntry]`
    - `static func remainingFlags(_ e: IgnoreEntry, unblocking: BlockFilter) -> (invite: Bool, chat: Bool)`
    - `static func kindText(invite: Bool, chat: Bool) -> String` returns `messages`, `invitations` or `messages and invitations`.
    - `static func isChatBlocked(_ name: String, in entries: [IgnoreEntry]) -> Bool` (case-insensitive)
    - `static func formEncode(_ params: [(String, String)]) -> String`
- Produces, in `BlockedPlayersService.swift`:
  - `@objc final class BlockedPlayersService: NSObject`. It uses `PenteHTTPClient`, the `development` URL switch via `developmentEnabled()`, and `name2`/`password2` from `NSUserDefaults` keys `username`/`password`, form-encoded. All completions run on main.
    - `static func load(_ completion: @escaping ([IgnoreEntry]?, String?) -> Void)`
    - `@objc static func block(name: String, invite: Bool, chat: Bool, completion: @escaping (Bool, String?) -> Void)` (`action=add`)
    - `static func update(pid: Int64, invite: Bool, chat: Bool, _ completion: @escaping ([IgnoreEntry]?, String?) -> Void)`
    - `@objc static func isChatBlocked(name: String, completion: @escaping (Bool) -> Void)`. On failure it passes false.
    - `@objc static func blockIcon() -> UIImage`
    - `@objc static func confirmBlock(name: String, forInvitation: Bool, onConfirm: @escaping () -> Void)`. Shows the exact dialog copy via `PenteAlert` (button index 1 = Block).
    - `@objc static func showError(blocking: Bool, name: String, message: String?)`

- [ ] **Step 1: Branch.** `git -C penteLive-iOS checkout -b blocked-players`

- [ ] **Step 2: Write failing tests** in `BlockedPlayersTests` (XCTest, `@testable import penteLive`):
  - `testParseValid`: `{"success":true,"ignores":[{"ignorePid":5,"name":"graviton","ignoreInvite":true,"ignoreChat":false}]}` gives one entry, pid 5.
  - `testParseHtmlLoginPageIsNil`: `"<html>login</html>"` gives nil.
  - `testParseFailureIsNil`: `{"success":false,"error":"Login required"}` gives nil, and `parseError` returns `"Login required"`.
  - `testFilter`: entries A(invite), B(chat), C(both). Messages gives [B, C], Invitations gives [A, C], Both gives [A, B, C].
  - `testRemainingFlags`: C under `.messages` gives (true, false). Under `.both` it gives (false, false).
  - `testKindText`: (false, true) gives `"messages"`, (true, false) gives `"invitations"`, (true, true) gives `"messages and invitations"`.
  - `testIsChatBlockedCaseInsensitive`: `"Graviton"` with entry `graviton` chat=Y is true. With chat=N it is false.
  - `testFormEncodeSpecialChars`: `formEncode([("ignoreName","a b+c&d")])` gives `"ignoreName=a%20b%2Bc%26d"`.

- [ ] **Step 3: Run to see them fail.** `xcodebuild test -workspace penteLive-iOS/penteLive.xcworkspace -scheme test1 -destination 'platform=iOS Simulator,id=50EDEBC7-5478-4136-B9CD-3487CDB0D7DD' -only-testing:PenteEngineTests/BlockedPlayersTests`. Expected: build failure, unresolved `BlockedPlayers`.

- [ ] **Step 4: Implement both files.** Copy the request pattern from `MessagesViewController.m:646-690` (form POST, 7.0 s timeout) and the encoder style from `StoreAdapters.swift:41`. Make sure the bridging header exposes `PenteHTTPClient`/`PenteAlert`, which it already does.

- [ ] **Step 5: Run the tests.** Same command. Expected: `Executed 8 tests, with 0 failures`.

- [ ] **Step 6: Commit.** `git -C penteLive-iOS commit -S -m "Blocked players: model, logic and service"`

---

### Task 3: iOS — Blocked players screen and settings row

**Files:**
- Create: `penteLive-iOS/test1/BlockedPlayers/BlockedPlayersViewController.swift`
- Modify:
  - `penteLive-iOS/test1/Settings.bundle/MoreSettings.plist`. Add a new `PSGroupSpecifier` group after the subscriber-only group, holding an `IASKButtonSpecifier` with key `blockedPlayersButton` and title `Blocked players`.
  - `penteLive-iOS/test1/SettingsViewController.m` (`settingsViewController:buttonTappedForSpecifier:`, ~L267)
  - `en.lproj`/`zh-Hans.lproj` `Localizable.strings`
- Create: `penteLive-iOS/test1/Settings.bundle/en.lproj/MoreSettings.strings` and `zh-CN.lproj/MoreSettings.strings` (UTF-16LE, only the new key)

**Interfaces:**
- Consumes: Task 2's `BlockedPlayersService.load/update`, `BlockedPlayers.filter/remainingFlags/kindText`, `BlockFilter`.
- Produces: `@objc final class BlockedPlayersViewController: UITableViewController`, created with `init(style: .insetGrouped)`.

- [ ] **Step 1: Add the settings row.** Handle it with `if ([specifier.key isEqualToString:@"blockedPlayersButton"]) { [self.navigationController pushViewController:[[BlockedPlayersViewController alloc] init] animated:YES]; return; }`. It is not gated on subscriber status. The row is hidden via `hiddenKeys` when not logged in, if the other login-only rows do the same.

- [ ] **Step 2: Implement the screen.**
  - Title is `Blocked players`.
  - The filter is a `UIButton` with a `UIMenu` of the `BlockFilter` cases (`showsMenuAsPrimaryAction`), placed as the table header. Default `.both`.
  - Under `.both`, row subtitles come from `kindText`.
  - When the filtered list is empty, `backgroundView` shows the label `No blocked players`.
  - `trailingSwipeActionsConfigurationForRowAt` has a destructive `Unblock` action.
    - It shows the unblock dialog, with `<kind>` = `kindText` of the flags being removed.
    - Confirm calls `update(pid:, remainingFlags...)` and replaces the data with the returned list.
    - Cancel calls `completion(false)`.
    - Errors go through `showError(blocking:false, ...)`.
  - Load on `viewWillAppear`, with a `UIRefreshControl`.

- [ ] **Step 3: Build and smoke test** on iPhone 18 Pro and iPad Pro 13-inch. Build with `xcodebuild build -workspace ... -scheme test1 -destination 'platform=iOS Simulator,id=<each>'`. Expected: `BUILD SUCCEEDED`. The UI check is in Task 10.

- [ ] **Step 4: Commit.** `"Blocked players screen in More settings"`

---

### Task 4: iOS — block from a message, hide reply

**Files:**
- Modify: `penteLive-iOS/test1/MessagesViewController.m`
  - nav items in `viewDidAppear` ~L108-130
  - `viewWillAppear` ~L75-77
  - `applyLayoutMode` ~L155

**Interfaces:**
- Consumes: `BlockedPlayersService.blockIcon/confirmBlock/block/isChatBlocked/showError`.

- [ ] **Step 1: Track the block state.** Add `@property BOOL senderBlocked`. In reply mode (`messageID != nil`), call `isChatBlocked:author` when the view appears. On the result, set `senderBlocked`, re-run `applyLayoutMode`, and rebuild the nav items.

- [ ] **Step 2: Hide reply for a blocked sender.**
  - While `senderBlocked` is true, `replyMessageView` and `sendButton` are hidden. Guard the unconditional unhide in `viewWillAppear` the same way.
  - The nav items are `[trash, play, block]`, where block is `[[UIBarButtonItem alloc] initWithImage:[BlockedPlayersService blockIcon] style:UIBarButtonItemStylePlain target:self action:@selector(blockTap)]`.
  - Leave out the block item when `senderBlocked` is true.

- [ ] **Step 3: Wire the block action.** `blockTap` calls `confirmBlock(name:author, forInvitation:NO)`. On confirm, call `block(name:author, invite:NO, chat:YES)`. On success, set `senderBlocked = YES` and update the layout. On failure, call `showError(blocking:YES ...)`.

- [ ] **Step 4: Build.** Expected: `BUILD SUCCEEDED`. Also run the existing `MessageThreadViewTests`/`MessageReplyInputTests`. Expected: pass.

- [ ] **Step 5: Commit.** `"Block a message sender from the message screen"`

---

### Task 5: iOS — block button on personal invitations

**Files:**
- Modify: `penteLive-iOS/test1/GamesTableViewController.m`
  - `addButtonsToCell:` ~L2565-2650
  - `removeButtonsFromCell` ~L2650
  - `rejectInvitation:` ~L2470

**Interfaces:**
- Consumes: `BlockedPlayersService.blockIcon/confirmBlock/block/showError`.

- [ ] **Step 1: Add the button.** Add a `blockButton` ivar: a `UIButtonTypeCustom` with `blockIcon`, square, side equal to the existing button height. Add it to the right of `rejectButton`.
  - Only `addButtonsToCell:` (the `INVITATIONSSECTION` path) adds it. `addButtonsToPublicInvitationsCell:` stays unchanged.
  - `removeButtonsFromCell` removes it.

- [ ] **Step 2: Lay out four buttons.** Recompute spacing for four items from `cell.contentView.bounds.size.width` instead of `UIScreen.mainScreen.bounds.size.width`. Check on iPad in landscape: buttons don't overlap, and the block button's right edge is at or inside the content width minus the existing margin.

- [ ] **Step 3: Wire the block action.** `blockInvitation:` reads `self.player.invitations[selectedInvitationIndexPath.row].opponentName` and calls `confirmBlock(name:, forInvitation:YES)`. On confirm, call `block(name:, invite:YES, chat:NO)`. On success, call the existing `rejectInvitation:` unchanged. On failure, call `showError` and leave the row as it is.

- [ ] **Step 4: Build** for the iPhone and iPad destinations. Expected: `BUILD SUCCEEDED`.

- [ ] **Step 5: Commit.** `"Block an inviter from a personal invitation"`

---

### Task 6: Android — ignore list model, logic and client

**Files:**
- Create: `penteLive-Android/app/src/main/java/be/submanifold/pentelive/blocked/BlockedPlayers.java` (model and pure logic)
- Create: `penteLive-Android/app/src/main/java/be/submanifold/pentelive/blocked/BlockedPlayersClient.java` (network, blocking calls for use inside `BackgroundTask`)
- Test: `penteLive-Android/app/src/test/java/be/submanifold/pentelive/blocked/BlockedPlayersTest.java`

**Interfaces:**
- Produces, in `BlockedPlayers`:
  - `static final class IgnoreEntry { public long ignorePid; public String name; public boolean ignoreInvite; public boolean ignoreChat; }` (Gson)
  - `enum Filter { MESSAGES, INVITATIONS, BOTH }`
  - `enum Kind { MESSAGES, INVITATIONS, BOTH }` plus `static Kind kind(boolean invite, boolean chat)`. The UI maps `Kind` to the strings `blocked_kind_messages`, `blocked_kind_invitations` and `blocked_kind_both`.
  - `static final class Reply { public boolean success; public String error; public List<IgnoreEntry> ignores; }`
  - `static Reply parse(String body)`. Returns null for null, non-JSON or `success:false`. Use `Json.GSON`.
  - `static String parseError(String body)`
  - `static List<IgnoreEntry> filter(List<IgnoreEntry>, Filter)`
  - `static boolean[] remainingFlags(IgnoreEntry, Filter unblocking)`, as `{invite, chat}`
  - `static boolean isChatBlocked(String name, List<IgnoreEntry>)` (case-insensitive)
  - `static String formEncode(String... keyValues)` (UTF-8, spaces as `%20`)
- Produces, in `BlockedPlayersClient`. Each method uses `AuthedHttp.shared().get` / `postForm` on `/gameServer/ignored`, returns the entries, and throws `BlockedPlayersClient.Failure extends IOException` whose `getMessage()` is the server error or null:
  - `static List<IgnoreEntry> load() throws IOException`
  - `static List<IgnoreEntry> block(String name, boolean invite, boolean chat) throws IOException`
  - `static List<IgnoreEntry> update(long pid, boolean invite, boolean chat) throws IOException`

- [ ] **Step 1: Branch.** `git -C penteLive-Android checkout -b blocked-players`

- [ ] **Step 2: Write failing tests.** Same cases and values as Task 2 Step 2, in JUnit 4: `parseValid`, `parseHtmlLoginPageIsNull`, `parseFailureIsNull` (+`parseError`), `filter`, `remainingFlags`, `kind`, `isChatBlockedCaseInsensitive`, and `formEncodeSpecialChars` expecting `"ignoreName=a%20b%2Bc%26d"`.

- [ ] **Step 3: Run to see them fail.** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"; cd penteLive-Android && ./gradlew testDebugUnitTest --tests 'be.submanifold.pentelive.blocked.BlockedPlayersTest'`. Expected: compilation failure.

- [ ] **Step 4: Implement** both classes. Follow the `ReplyMessageActivity.java:244` / `:281-318` patterns for HTTP, and log with `RedactingLog`.

- [ ] **Step 5: Run the tests.** Same command. Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 6: Commit.** `"Blocked players: model, logic and client"`

---

### Task 7: Android — BlockedPlayersActivity and settings button

**Files:**
- Create:
  - `.../blocked/BlockedPlayersActivity.java`
  - `res/layout/activity_blocked_players.xml` (Toolbar + `AppCompatSpinner` + `RecyclerView` + empty `TextView`)
  - `res/layout/blocked_player_row.xml`
  - `res/drawable/ic_block_player.xml`
- Modify:
  - `AndroidManifest.xml`, adding `<activity android:name=".blocked.BlockedPlayersActivity" android:screenOrientation="portrait"/>` like `SettingsActivity`
  - `res/layout/activity_settings.xml`, adding a `blockedPlayersButton` below `avatarButton` and re-pointing the `layout_below` of `infoLabel3` and `avatarView`
  - `SettingsActivity.java` `onCreate`
  - `res/values/strings.xml`, `res/values-de/strings.xml`

**Interfaces:**
- Consumes: Task 6's `BlockedPlayers`, `BlockedPlayersClient`.
- Produces: `R.drawable.ic_block_player`, used by Tasks 8 and 9, and strings `block_dialog_title`, `block_message_dialog_text`, `block_invitation_dialog_text`, `block_error_title`, `unblock_error_title`, `try_again` and `block_dialog_title_generic` ("Block player", used as the content description), used by Tasks 8 and 9.

- [ ] **Step 1: Add the drawable.** `ic_block_player.xml`: a 24 dp vector with a red (`#D32F2F`) filled circle and a white horizontal rounded bar about 60% of the diameter wide and 16% tall.

- [ ] **Step 2: Add the settings button.** `blockedPlayersButton` → `startActivity(new Intent(this, BlockedPlayersActivity.class))`. It is not subscriber-gated.

- [ ] **Step 3: Build the activity.**
  - Green toolbar like `SettingsActivity`, titled `Blocked players`.
  - The spinner lists `Messages/Invitations/Both`, default Both.
  - Rows show the name, plus the `Kind` text under Both.
  - Swipe uses `ItemTouchHelper(SimpleCallback(0, LEFT|RIGHT))`. `onSwiped` opens the unblock `AlertDialog`.
    - Cancel or dismiss calls `adapter.notifyItemChanged(pos)` to restore the row.
    - Confirm runs a `BackgroundTask` calling `update(...)` with `remainingFlags`, then replaces the list.
    - Failure shows the error dialog and restores the row.
  - Load happens in `onResume` via `BackgroundTask`.
  - Back behavior is the default. Don't intercept it (predictive back).

- [ ] **Step 4: Build.** `./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL`. Then run `./gradlew lintDebug`. Expected: no new errors in the changed files.

- [ ] **Step 5: Commit.** `"Blocked players screen in settings"`

---

### Task 8: Android — block from a message, hide reply

**Files:**
- Modify:
  - `res/menu/message_menu.xml`, adding `action_block` with `@drawable/ic_block_player`, `showAsAction="ifRoom"` and no `iconTint`
  - `ReplyMessageActivity.java`
    - `onCreateOptionsMenu` ~L170
    - the menu click listener ~L124-138
    - the reply and `sendButton` visibility, including the `KeyboardVisibilityEvent` listener

**Interfaces:**
- Consumes: `BlockedPlayersClient.load/block`, `BlockedPlayers.isChatBlocked`, Task 7's drawable and strings.

- [ ] **Step 1: Track the block state.** Add `boolean senderBlocked`. In `onCreate`, for a received message, run a `BackgroundTask` that loads the list and sets `senderBlocked = isChatBlocked(message.getAuthor(), list)`. Then hide `reply` and `sendButton`, `invalidateOptionsMenu()`, and make the keyboard listener respect the flag. On failure it stays false.

- [ ] **Step 2: Wire the block item.** `onPrepareOptionsMenu` hides `action_block` while `senderBlocked` is true. `action_block` shows the message block dialog. Confirm runs `block(author, false, true)`. On success, apply the hidden state. On failure, show the error dialog.

- [ ] **Step 3: Build and test.** `./gradlew assembleDebug testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, with existing `MessageThreadTest` passing.

- [ ] **Step 4: Commit.** `"Block a message sender from the message screen"`

---

### Task 9: Android — block button on personal invitations

**Files:**
- Modify:
  - `res/layout/dashboardrow_layout.xml`, adding `ImageButton` `@+id/blockButton` as the last child of the button row: square (width equals the row height), `visibility="gone"`, `src=@drawable/ic_block_player`, `contentDescription=@string/block_dialog_title_generic` ("Block player")
  - `MainActivity.java` child click listener ~L98-260, hiding it wherever the code hides the other buttons and through `viewWithOpenButtons`

**Interfaces:**
- Consumes: `BlockedPlayersClient.block`, `player.respondInvitation(gameID, false, listAdapter)`, Task 7's drawable and strings.

- [ ] **Step 1: Show the button.** Make `blockButton` visible only in the `INVITATIONSGROUP` branch. It stays gone for `PUBLICINVITATIONSGROUP` and `SENTINVITATIONSGROUP`.

- [ ] **Step 2: Wire the button.** It reads `player.getInvitations().get(childPosition)` and shows the invitation block dialog with `getOpponentName()`. Confirm runs a `BackgroundTask` calling `block(name, true, false)`. On success, call the existing `player.respondInvitation(game.getGameID(), false, listAdapter)`. On failure, show the error dialog and leave the row.

- [ ] **Step 3: Build.** `./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit.** `"Block an inviter from a personal invitation"`

---

### Task 10: iOS E2E on simulator (scratch only)

**Files:**
- Create, all outside the repo:
  - `/private/tmp/claude-501/blocked-players-e2e/ios/` as an rsync copy of `penteLive-iOS` with `#define development YES`
  - a throwaway UI test target
  - `seed.py`

**Interfaces:**
- Consumes: all iOS tasks, Task 1 deployed locally, accounts `iostest` and `graviton` (creds from `.env.local`, passed via `-username`/`-password` launch args, never written into the copy's sources).

- [ ] **Step 1: Write `seed.py`** (Python `urllib`, unverified SSL), which runs as `graviton`:
  - `message`: POST `/gameServer/mymessages` `command=create&to=iostest&subject=E2E <n>&body=hi&mobile=`
  - `invite`: POST `/gameServer/tb/newGame` with `invitee=iostest`. Read `NewGameServlet` for the remaining required params.
  - `reset`: `iostest`'s ignores removed via `/gameServer/ignored`
  - `try-send`: sends a message and prints the server reply.

- [ ] **Step 2: Write the XCUITest scenarios.** On iPhone 18 Pro (`50EDEBC7-…`) and iPad Pro 13-inch (`A533402B-…`), headless, with `pente.org/localhost.crt` added via `xcrun simctl keychain <id> add-root-cert`:
  1. reset + message: open the message, tap block, the dialog text contains `You can unblock players in Settings.`, tap Block, and the reply field is gone. Then `seed.py try-send` prints `Player is ignoring your messages.`
  2. reset + invite: tap the invitation, the block button appears to the right of decline, the dialog contains the settings line, confirm, and the row is gone. Then `seed.py invite` prints `Player is ignoring your invitations.`
  3. A public invitation row shows no block button.
  4. Blocked players screen:
     - Both lists graviton with `messages and invitations`, after the two scenarios above.
     - Messages filter: swipe, Cancel, and the row is still there.
     - Swipe, Unblock, and graviton disappears from Messages but remains under Invitations.
  5. iPad landscape: screenshot of the invitation row. Assert the block button frame is inside the cell frame and doesn't overlap `decline`.

- [ ] **Step 3: Run** `xcodebuild test` on the copy for both destinations. Expected: all scenarios pass. Save screenshots to `/private/tmp/claude-501/blocked-players-e2e/ios/shots/`.

- [ ] **Step 4: Check that nothing leaked into the repo.** `git -C penteLive-iOS status --porcelain`. Expected: empty.

---

### Task 11: Android E2E on emulator (scratch only)

**Files:**
- Create, outside the repo: `/private/tmp/claude-501/blocked-players-e2e/android/` as an rsync copy with `PentePlayer.development = true`, and `run_e2e.py` (adb + `uiautomator dump` + `input tap`/`swipe`, reusing `../seed.py`)

**Interfaces:**
- Consumes: all Android tasks, Task 1, the `pentelive_api37` AVD.

- [ ] **Step 1: Start the emulator and install.** Run `~/Library/Android/sdk/emulator/emulator -avd pentelive_api37 -no-window -no-audio &`, then `adb wait-for-device`, then from the copy run `./gradlew installDebug` with the JBR `JAVA_HOME`. Log in as iostest through the UI.

- [ ] **Step 2: Run the scenarios.** Same scenarios 1-4 as Task 10. Find elements by `resource-id` (`blockButton`, `action_block`, `blockedPlayersButton`) and text in the uiautomator XML. Each assertion checks dumped XML or `seed.py` output. Take `adb exec-out screencap -p` screenshots to `.../android/shots/`.

- [ ] **Step 3: Run** `python3 run_e2e.py`. Expected: `ALL SCENARIOS PASSED`.

- [ ] **Step 4: Clean up.** Kill the emulator, then run `git -C penteLive-Android status --porcelain`. Expected: empty.

---

### Task 12: PRs

- [ ] **Step 1: Push and open the PRs.**
  - Run `git -C <repo> push -u origin blocked-players` for each repo.
  - Open one PR per repo with `gh pr create`. Each PR body names the spec, the tests run and the E2E results.
  - The server PR notes that it is backward compatible and can deploy first.
  - Pushing and opening PRs is outward-facing, so confirm with the owner before this step.
