# Renju Pass / Draw Offers — iOS Implementation Plan (4/4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renju PASS + DRAW? buttons and draw-offer flows in penteLive-iOS — live (Swift/WebSocket) and turn-based (ObjC/HTTP).

**Architecture:** Live: two code-laid-out `UIButton`s beside `playButton`, `sendMove(move:drawOffer:)` dictionary field, accept/reject wire events routed through `RoomViewController` to `UIAlertController`s (the `requestUndo` pattern). TB: two code-created buttons in the bottom row (the `lockButton` precedent — the rest is IB, don't touch the storyboard), `&drawOffer=true` URL suffix, `command=acceptDraw` POST (the `acceptUndo` pattern), `drawOffered` from `jsonResponse`; TSMessage bottom notifications for armed/pending notices.

**Tech Stack:** Swift + Objective-C hybrid; build the WORKSPACE: `xcodebuild test -workspace penteLive.xcworkspace -scheme test1 -destination 'platform=iOS Simulator,name=iPhone 16' SWIFT_ENABLE_EXPLICIT_MODULES=NO` (the flag is mandatory — `module 'CocoaAsyncSocket' not found` otherwise). Test target: `PenteEngineTests`. Must work on iPhone AND iPad (see `penteLive-iOS/CLAUDE.md`) — frame math from `view.frame.width`, alert controllers need `popoverPresentationController` anchors on iPad (copy the `requestUndo` anchor pattern).

**Repo:** `/Users/waliedothman/mariposa/coding/pente.org-project/penteLive-iOS` — commits to THIS repo, signed (`git commit -S`).

**Spec:** `pente.org/docs/superpowers/specs/2026-07-15-renju-pass-draw-design.md`. Wire/API contract identical to plans 2/3.

## Global Constraints

- Pass = `table.gridSize * table.gridSize` (= 225 for renju) as a normal move.
- Gates: live = `table.state.renju.complete == true` + my turn + `.started` (the `RenjuTracking.complete` flag mirrors server `openingComplete`; never gate on a `MOVE`-like phase). TB = `[self.renjuPhase isEqualToString:@"COMPLETE"]` + `activeGame`.
- Live wire: move dict may carry `"drawOffer": true`; new events `dsgRenjuAcceptDrawTableEvent`/`dsgRenjuRejectDrawTableEvent` (`player`, `table`, `time: 0`); inbound `dsgRenjuDrawTableErrorEvent`; `dsgGameStateTableEvent` may carry `drawOfferedBy`.
- TB API: `command=move...&drawOffer=true`; `command=acceptDraw` POST; polled JSON key `drawOffered`.
- PASS stays visible while DRAW? is armed; staging a stone (TB `finalMove > -1`) hides PASS, DRAW? stays.
- Localized strings via `NSLocalizedString`, matching existing casual style ("draw offered", "draw declined").

---

### Task 1: Live model — pass move safety (Swift `Table`/engine)

**Files:**
- Modify: `test1/HelperClasses.swift` (the `Table`/`GameState` model — `addMoves`, `stone(at:)` consumers, `syncFromEngine`)
- Test: add `RenjuPassTests.swift` to `PenteEngineTests`

**Interfaces:**
- Produces: `Table.isPass(_ move: Int) -> Bool`; `addMoves`/engine application skips board placement for pass moves while keeping them in `moves` (parity + `advanceRenjuTracking` counters depend on `moves.count`). Task 2 consumes `isPass`.

- [ ] **Step 1: Failing test** — new `PenteEngineTests/RenjuPassTests.swift`:

```swift
import XCTest
@testable import test1

final class RenjuPassTests: XCTestCase {

    // Build a renju Table the way existing PenteEngineTests do (reuse their
    // factory/fixtures; adapt setup, never assertions).
    func makeRenjuTableWithCompletedOpening() -> Table {
        // 6 stones, no swaps, Branch A — advanceRenjuTracking marks complete
        // (see existing renju tests for the canonical setup)
        fatalError("use the existing test fixture helper")
    }

    func testPassMoveKeptInListNotOnBoard() {
        let t = makeRenjuTableWithCompletedOpening()
        let n = t.moves.count
        let pass = t.gridSize * t.gridSize
        t.addMoves(moves: [pass])            // must not trap
        XCTAssertEqual(t.moves.count, n + 1)
        XCTAssertTrue(t.isPass(pass))
        XCTAssertTrue(t.state.renju.complete) // tracking unaffected
        // no cell changed: stone(at:) over the full board equals the pre-pass snapshot
    }
}
```

- [ ] **Step 2: Run** — `xcodebuild test ... -only-testing:PenteEngineTests/RenjuPassTests SWIFT_ENABLE_EXPLICIT_MODULES=NO` → FAIL (no `isPass`, or a trap in `addMoves`).

- [ ] **Step 3: Implement** — in `HelperClasses.swift`:

```swift
    func isPass(_ move: Int) -> Bool {
        return move == gridSize * gridSize
    }
```

In `addMoves(moves:)` (and any single-`addMove` path): for a pass, append to `moves` and run the same bookkeeping (`advanceRenjuTracking`) but skip the engine/board application — grep every `engine.addMove` / `abstractBoard[` write reached from `addMoves` and guard with `if isPass(move) { continue-equivalent }`. NOTE (adversarial review): `PenteGame.play` already guards `move < cells` (no-op on 225), so much of this may already be safe — the explicit guard + `isPass` helper still land for clarity and for any path bypassing the engine; expect this task to be small. Audit renderers reading move HISTORY by coordinate (last-move marker, `stone(at:)` calls with a history move) and skip pass values.

- [ ] **Step 4: Run** — new test green; full `PenteEngineTests` green (154+ baseline).

- [ ] **Step 5: Commit**

```bash
git add test1/HelperClasses.swift PenteEngineTests/RenjuPassTests.swift
git commit -S -m "feat(renju): live model tolerates pass move 225 (list-only, board untouched)"
```

---

### Task 2: Live UI — PASS + DRAW? buttons, offer send/receive (Swift)

**Files:**
- Modify: `test1/TableViewController.swift`
- Modify: `test1/RoomViewController.swift`
- Modify: `test1/PenteLiveSocket.swift` — **the inbound event key-dispatch lives here** (`processEvent`, ~L105-183), NOT in RoomViewController (that file only holds handler bodies). Without new branches here, inbound accept/reject/rejoin never route (adversarial-review MAJOR #3).

**Interfaces:**
- Consumes: Task 1 (`isPass`), `RenjuTracking.complete`, `sendMove` (L621-624), `requestUndo` alert pattern (L1065-1090), TSMessage (already used Swift-side, L430/L1038), RoomViewController event routing (L820-850 pattern).
- Produces: `renjuPassButton`/`renjuDrawButton` (code-laid-out), `drawArmed`, `sendMove(move:drawOffer:)`, `drawOffered(player:)`, `drawRejected(player:)` on TableViewController; `renjuAcceptDrawTableEvent`/`renjuRejectDrawTableEvent`/move-flag routing in RoomViewController.

- [ ] **Step 1: Buttons + arming (TableViewController)**

Properties (next to `playButton`, L32):

```swift
    let renjuPassButton = UIButton()
    let renjuDrawButton = UIButton()
    var drawArmed = false
```

Setup in `init` (mirror `playButton` config, L78-81):

```swift
        renjuPassButton.setTitle(NSLocalizedString("PASS", comment: ""), for: .normal)
        renjuPassButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 20)
        renjuPassButton.setTitleColor(UIColor.blue, for: .normal)
        renjuPassButton.addTarget(self, action: #selector(renjuPass), for: .touchUpInside)
        renjuDrawButton.setTitle(NSLocalizedString("DRAW?", comment: ""), for: .normal)
        renjuDrawButton.titleLabel?.font = UIFont.boldSystemFont(ofSize: 20)
        renjuDrawButton.setTitleColor(UIColor.blue, for: .normal)
        renjuDrawButton.addTarget(self, action: #selector(toggleDrawOffer), for: .touchUpInside)
```

Layout in `viewDidLoad` (after the `playButton` frame block, L138-143): split the `playButton` slot (the `ratedTimerLabel` frame at the seats row) into two halves:

```swift
        var rframe = seatsView.ratedTimerLabel.frame
        rframe.origin.y = seatsView.frame.origin.y
        rframe.size.width = rframe.size.width / 2
        renjuPassButton.frame = rframe
        rframe.origin.x += rframe.size.width
        renjuDrawButton.frame = rframe
        renjuPassButton.isHidden = true
        renjuDrawButton.isHidden = true
        view.addSubview(renjuPassButton)
        view.addSubview(renjuDrawButton)
```

Actions + gate + visibility:

```swift
    func renjuPostOpeningMyTurn() -> Bool {
        return table.isRenju() && table.state.state == .started
            && table.state.renju.complete && table.currentPlayerName() == me
    }

    func updateRenjuDrawButtons() {
        let show = renjuPostOpeningMyTurn()
        renjuPassButton.isHidden = !show
        renjuDrawButton.isHidden = !show
        if table.state.state != .started { setDrawArmed(false) }
    }

    func setDrawArmed(_ armed: Bool) {
        drawArmed = armed
        renjuDrawButton.backgroundColor = armed ? UIColor.systemGreen : UIColor.clear
    }

    @objc func renjuPass() {
        guard renjuPostOpeningMyTurn() else { return }
        sendMove(move: table.gridSize * table.gridSize, drawOffer: drawArmed)
        setDrawArmed(false)
    }

    @objc func toggleDrawOffer() {
        setDrawArmed(!drawArmed)
        if drawArmed {
            TSMessage.showNotification(in: self,
                title: NSLocalizedString("Draw offer", comment: ""),
                subtitle: NSLocalizedString("Draw offer will be sent with your move", comment: ""),
                type: TSMessageNotificationType.message)
        }
    }
```

(Use whichever `TSMessage.showNotification` overload the file already calls — copy the L430 call shape and trim.) Call `updateRenjuDrawButtons()` at every site that manages the Go PASS retitle: `stateChanged()` both regions (L697-701, L988), `addMoves` (L1011 region), `rejectDeadStones` (L1023 region).

- [ ] **Step 2: `sendMove` carries the flag**

Replace `sendMove` (L621-624) with a defaulted-parameter version — every existing call site compiles unchanged:

```swift
    func sendMove(move: Int, drawOffer: Bool = false) {
        var inner: [String: Any] = ["move": move, "moves": [move], "player": me, "table": table.table, "time": 0]
        if drawOffer { inner["drawOffer"] = true }
        socket.sendEvent(eventDictionary: ["dsgMoveTableEvent": inner])
    }
```

Board-tap send sites (L298/301/318 — the `sendMove(move:)` calls from `boardTouch`): pass the armed flag and clear it:

```swift
        sendMove(move: m, drawOffer: drawArmed)
        setDrawArmed(false)
```

(Only in the normal-play renju path; opening decision sends go through `RenjuWire` and can never be armed.)

- [ ] **Step 3: Incoming offer / accept / reject (TableViewController)**

Mirror `requestUndo(player:)` (L1065-1090) exactly, including the iPad popover anchor:

```swift
    func drawOffered(player: String) {
        if me != player, table.amIseated(i: me) {
            let alertController = UIAlertController(
                title: NSLocalizedString("\(player) offers a draw. Playing a move also declines it.", comment: ""),
                message: nil, preferredStyle: .alert)
            alertController.addAction(UIAlertAction(title: NSLocalizedString("accept draw", comment: ""), style: .default) { _ in
                self.socket.sendEvent(eventDictionary: ["dsgRenjuAcceptDrawTableEvent": ["player": self.me, "table": self.table.table, "time": 0] as [String: Any]])
            })
            alertController.addAction(UIAlertAction(title: NSLocalizedString("reject draw", comment: ""), style: .destructive) { _ in
                self.socket.sendEvent(eventDictionary: ["dsgRenjuRejectDrawTableEvent": ["player": self.me, "table": self.table.table, "time": 0] as [String: Any]])
            })
            if let popoverController = alertController.popoverPresentationController {
                popoverController.barButtonItem = navigationItem.rightBarButtonItems?[isArenaTable ? 0 : 1]
            }
            present(alertController, animated: true)
        }
    }

    func drawRejected(player _: String) {
        setDrawArmed(false)
        addText(text: NSLocalizedString("* draw offer declined *", comment: ""))
    }

    func drawAccepted(player _: String) {
        addText(text: NSLocalizedString("* draw accepted *", comment: ""))
        // game end arrives via the game-state event
    }
```

- [ ] **Step 4: Routing (RoomViewController)**

1. Find the move-event routing (the handler that forwards `dsgMoveTableEvent` to `tableViewController` — same dispatch family as `undoRequestTableEvent`, L825). After the move is applied, add:

```swift
            if let offer = event["drawOffer"] as? Bool, offer,
               let playerName = event["player"] as? String,
               playerName != self.me {
                self.tableViewController?.drawOffered(player: playerName)
            }
```

2. **Dispatch registration in `PenteLiveSocket.processEvent` (L105-183):** find how `"dsgUndoRequestTableEvent"` is keyed to its RoomViewController handler and register `"dsgRenjuAcceptDrawTableEvent"` → `renjuAcceptDrawTableEvent(event:)` and `"dsgRenjuRejectDrawTableEvent"` → `renjuRejectDrawTableEvent(event:)` identically (`dsgRenjuDrawTableErrorEvent` may be ignored or logged). Then add the handler bodies in RoomViewController:

```swift
    func renjuAcceptDrawTableEvent(event: [String: Any]) {
        DispatchQueue.main.async {
            let tableId = event["table"] as! Int
            if tableId == self.tableViewController?.table.table {
                self.tableViewController?.drawAccepted(player: event["player"] as! String)
            }
        }
    }

    func renjuRejectDrawTableEvent(event: [String: Any]) {
        DispatchQueue.main.async {
            let tableId = event["table"] as! Int
            if tableId == self.tableViewController?.table.table {
                self.tableViewController?.drawRejected(player: event["player"] as! String)
            }
        }
    }
```

3. Rejoin: in the `dsgGameStateTableEvent` handler, read `event["drawOfferedBy"] as? String`; if present and != me → `tableViewController?.drawOffered(player: name)`; if == me → `tableViewController?.addText(text: "* draw offer pending *")` and set its armed-pending display (optional: `setDrawArmed(false)` stays correct — pending ≠ armed).

- [ ] **Step 5: Build + commit**

`xcodebuild build -workspace penteLive.xcworkspace -scheme test1 -destination 'platform=iOS Simulator,name=iPhone 16' SWIFT_ENABLE_EXPLICIT_MODULES=NO` → succeeds.

```bash
git add test1/TableViewController.swift test1/RoomViewController.swift
git commit -S -m "feat(renju): live PASS + DRAW? buttons, draw-offer flows (iOS)"
```

---

### Task 3: Turn-based (ObjC BoardViewController)

**Files:**
- Modify: `test1/BoardViewController.m`
- Modify: `test1/BoardViewController.h`

**Interfaces:**
- Consumes: server plan Tasks 10-11 (`drawOffer` param, `acceptDraw` command, `drawOffered` JSON); `renjuPhase` property (`.h` L48); `renjuActionForCurrentPhaseFillingMoves` already returns plain-move for `COMPLETE` (L1792+, "MOVE / COMPLETE -> plain placement"); code-created-button precedent `lockButton` (L723); `presentUndoOptions` (L2994+); TSMessage bottom calls (L603 shape).
- Produces: `renjuPassButton2`/`renjuDrawButton` (code-created; NOT the storyboard `passButton`, which is the swap2/renju-opening button), `drawArmed` BOOL, `presentDrawOptions`, `&drawOffer=true` on the move URL.

- [ ] **Step 1: Buttons (code-created, bottom row)**

`.h`: add properties:

```objectivec
@property(nonatomic, strong) UIButton *renjuPassButton2;
@property(nonatomic, strong) UIButton *renjuDrawButton;
@property(nonatomic) BOOL drawArmed;
```

`.m`, in the layout block after the storyboard `passButton` frame math (L232-238) — same row, reuse the thirds math (`x = self.view.bounds.size.width / 4`):

```objectivec
    self.renjuPassButton2 = [[UIButton alloc] init];
    rect = passButton.frame;           // same row/y as the decision buttons
    rect.origin.x = x;
    self.renjuPassButton2.frame = rect;
    [self.renjuPassButton2 setTitle:NSLocalizedString(@"PASS", nil) forState:UIControlStateNormal];
    [self.renjuPassButton2 setTitleColor:[UIColor blueColor] forState:UIControlStateNormal];
    [self.renjuPassButton2 addTarget:self action:@selector(renjuPassTapped:) forControlEvents:UIControlEventTouchUpInside];
    [self.renjuPassButton2 setHidden:YES];
    [self.view addSubview:self.renjuPassButton2];

    self.renjuDrawButton = [[UIButton alloc] init];
    rect.origin.x = 2 * x;
    self.renjuDrawButton.frame = rect;
    [self.renjuDrawButton setTitle:NSLocalizedString(@"DRAW?", nil) forState:UIControlStateNormal];
    [self.renjuDrawButton setTitleColor:[UIColor blueColor] forState:UIControlStateNormal];
    [self.renjuDrawButton addTarget:self action:@selector(toggleDrawOffer:) forControlEvents:UIControlEventTouchUpInside];
    [self.renjuDrawButton setHidden:YES];
    [self.view addSubview:self.renjuDrawButton];
```

(These overlap the `player1Button`/`player2Button` slots — which are only visible during opening decisions, never simultaneously with phase COMPLETE. Verify visually; shift to `0.5*x`/`2.5*x` if the storyboard `passButton` at `3*x` is ever concurrently visible — it isn't post-opening.)

- [ ] **Step 2: Visibility + button-state rule**

Central helper:

```objectivec
- (void)updateRenjuTbButtons {
    BOOL renjuComplete = [self.renjuPhase isEqualToString:@"COMPLETE"] && activeGame;
    BOOL staged = (finalMove > -1);
    [self.renjuPassButton2 setHidden:!(renjuComplete && !staged)];
    [self.renjuDrawButton setHidden:!renjuComplete];
    self.renjuDrawButton.backgroundColor = self.drawArmed ? [UIColor systemGreenColor] : [UIColor clearColor];
}
```

Call it: (a) in the poll-response render path right after `self.renjuPhase` is assigned (L2425-2445 region); (b) in the stone-staging touch regions where `finalMove` is set/reset and submit is enabled/disabled (L1215-1230 and L1640-1655 — both branches); (c) after submit. The existing non-Go default already disables SUBMIT until a stone is staged (L1215-1222) — the 3-button spec's "disabled SUBMIT" needs no extra work; verify it holds for renju COMPLETE.

- [ ] **Step 3: Actions + URL param**

```objectivec
- (void)toggleDrawOffer:(UIButton *)sender {
    self.drawArmed = !self.drawArmed;
    [self updateRenjuTbButtons];
    if (self.drawArmed) {
        [TSMessage showNotificationInViewController:self.navigationController
                                              title:NSLocalizedString(@"Draw offer", nil)
                                           subtitle:NSLocalizedString(@"Draw offer will be sent after you move", nil)
                                              image:nil
                                               type:TSMessageNotificationTypeMessage
                                           duration:TSMessageNotificationDurationAutomatic
                                           callback:^{ [TSMessage dismissActiveNotification]; }
                                        buttonTitle:nil
                                     buttonCallback:nil
                                         atPosition:TSMessageNotificationPositionBottom
                               canBeDismissedByUser:YES];
    }
}

- (void)renjuPassTapped:(UIButton *)sender {
    if (![self.renjuPhase isEqualToString:@"COMPLETE"] || !activeGame) return;
    finalMove = gridSize * gridSize;   // pass sentinel; renjuAction stays nil (plain move)
    [self submitMove:sender];
}
```

`submitMoveToServer` (L1875-1905): build a `drawSuffix` beside `renjuSuffix` and append it to ALL FOUR URL variants (prod/dev × empty/non-empty message):

```objectivec
    NSString *drawSuffix = self.drawArmed ? @"&drawOffer=true" : @"";
```

…and add `%@` + `drawSuffix` to each format string (order after `renjuSuffix`). Clear `self.drawArmed = NO;` in the submit completion success path.

- [ ] **Step 4: Incoming offer + accept**

In the poll-response parse (next to `undoRequested` read, L2138):

```objectivec
             BOOL drawOffered = [jsonResponse[@"drawOffered"] boolValue];
```

Where `undoRequest` drives UI (the L2790-2815 region decides by whose turn it is): if `drawOffered && activeGame`:
- my turn → `[self presentDrawOptions]` — clone `presentUndoOptions` (L2994+) verbatim with title `NSLocalizedString(@"Draw offered", nil)`, actions "Accept draw" (POST body `gid=%@&command=acceptDraw&mobile=` — same request scaffold, incl. the `development` localhost variant) and "Dismiss" (cancel style, no-op — playing a move declines).
- not my turn (I offered) → TSMessage bottom, type Message, title `NSLocalizedString(@"Draw offered", nil)`, subtitle `NSLocalizedString(@"Waiting for your opponent to reply", nil)`.

- [ ] **Step 5: Build + commit**

Workspace build + `PenteEngineTests` still green.

```bash
git add test1/BoardViewController.m test1/BoardViewController.h
git commit -S -m "feat(renju): TB PASS/DRAW? buttons, drawOffer param, acceptDraw flow (iOS)"
```

---

### Task 4: End-to-end (manual, simulator + local backend)

- Local backend via the `development` flag (URLs hit `https://localhost`); test accounts iostest/graviton (from `pente-react-native/.env.local`, never commit).
- [ ] Live renju vs react client: buttons only post-opening on my turn (iPhone + iPad layouts); PASS works; double-pass draws; arm→stone and arm→pass carry the offer; accept/reject both directions; reject un-arms + notice; rejoin restores.
- [ ] TB renju: PASS/SUBMIT-disabled/DRAW? behavior; staging hides PASS; DRAW? green + TSMessage; `drawOffered` alert on next poll; Accept ends drawn; moving declines.
- [ ] iPad: draw-offer alert presents correctly (popover anchor), buttons hit-testable.
- [ ] Regression: Go pass (live playButton + TB submit-as-PASS), swap2/renju opening buttons, undo request/accept flows unchanged.
