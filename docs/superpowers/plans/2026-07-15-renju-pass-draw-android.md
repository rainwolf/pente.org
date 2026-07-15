# Renju Pass / Draw Offers — Android Implementation Plan (3/4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renju PASS + DRAW? buttons and draw-offer flows in pentelive-android — live play (socket) and turn-based (REST polling).

**Architecture:** Live: two dedicated buttons in `fragment_live_table.xml`, offer flag appended to the hand-built `dsgMoveTableEvent` JSON, accept/reject via two new `dsgRenju*DrawTableEvent` frames, incoming offers via the existing bottom `AlertDialog` pattern. Turn-based: PASS/DRAW? buttons beside SUBMIT, `&drawOffer=true` on the move URL, `command=acceptDraw`, `drawOffered` from the polled JSON.

**Tech Stack:** Java, Gradle (`:app` Android, `:rules` pure JVM + JUnit 4). Build needs JDK 17+: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (system default is JDK 8 — builds fail without this).

**Repo:** `/Users/waliedothman/mariposa/coding/pente.org-project/pentelive-android` — commits go to THIS repo, signed (`git commit -S`).

**Spec:** `pente.org/docs/superpowers/specs/2026-07-15-renju-pass-draw-design.md`. Server: plan 1/4. Wire/API contract identical to react plan 2/4.

## Global Constraints

- Renju pass = `gridSize*gridSize` = 225, sent/submitted as a normal move.
- Gates everywhere: renju + opening complete + my turn + game active/started. Live: `RenjuLiveState.Phase.COMPLETE`. TB: `"COMPLETE".equals(game.renjuPhase)`. `MOVE` is an opening sub-phase — NEVER enables pass/draw.
- Live wire: `dsgMoveTableEvent` JSON may carry `,"drawOffer":true`; new frames `dsgRenjuAcceptDrawTableEvent` / `dsgRenjuRejectDrawTableEvent` (`player`, `table`, `time:0`); inbound `dsgRenjuDrawTableErrorEvent`; inbound `dsgGameStateTableEvent` may carry `drawOfferedBy`.
- TB API: `command=move&...&drawOffer=true`; `command=acceptDraw&gid=...`; polled JSON gains `drawOffered` (Boolean).
- PASS stays visible while DRAW? is armed (offer may ride a pass). Staging a stone hides PASS; DRAW? stays visible for disarm.
- New strings follow the repo convention: `request_draw`? No — draws are offered, not requested: `draw_question`, `offers_draw`, `draw_offer_armed`, `draw_offered`, `draw_accepted`, `draw_declined`, `draw_offer_pending`.
- Tests: `./gradlew :rules:test` (fast JVM) and `./gradlew :app:testDebugUnitTest`.

---

### Task 1: Live model safety — pass moves must not touch the board array

**Files:**
- Modify: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/Table.java`
- Modify: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveBoardView.java` (render/audit only)
- Test: `app/src/test/java/be/submanifold/pentelive/LiveTablePassTest.java` (new; if `Table` has Android deps that block a JVM test, fold the assertions into `GameStateWiringTest`'s existing harness instead)

**Interfaces:**
- Produces: `Table.addMoves`/`addMove` accept move `225` without writing `abstractBoard` (15×15 — index 225/15=15 would crash), while keeping it in the move list so turn parity and `RenjuLiveState.advanceAfterMove(numMoves,…)` stay correct. Helper `Table.isPass(int move)` (`move == gridSize*gridSize`). Task 2 relies on `isPass`.

- [ ] **Step 1: Write the failing test**

```java
package be.submanifold.pentelive;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import be.submanifold.pentelive.liveGameRoom.Table;

public class LiveTablePassTest {

    // Build a renju table the way GameStateWiringTest does (reuse its factory/helpers
    // if Table's constructor needs more context — adapt setup, never assertions).
    private Table renjuTable() {
        Table t = new Table(/* per existing test harness */);
        // ensure gridSize == 15 / renju game type
        return t;
    }

    @Test public void passMoveDoesNotTouchBoardAndKeepsParity() {
        Table t = renjuTable();
        t.addMoves(Arrays.asList(112, 113, 114, 115, 116, 117)); // opening done (6 stones)
        int before = t.getMoves().size();
        t.addMoves(Arrays.asList(225)); // pass — must not throw
        assertEquals(before + 1, t.getMoves().size());
        assertTrue(t.isPass(225));
        // parity advanced: current color flipped compared to before the pass
    }
}
```

- [ ] **Step 2: Run** — `JAVA_HOME=... ./gradlew :app:testDebugUnitTest --tests '*LiveTablePassTest*'` → FAIL (crash or missing `isPass`).

- [ ] **Step 3: Implement**

In `Table.java`: add

```java
    public boolean isPass(int move) {
        return move == getGridSize() * getGridSize();
    }
```

Find every place a move index is applied to `abstractBoard` (`addMoves`, `addMove`, capture logic, replay) — grep `abstractBoard[` — and guard each application site:

```java
        if (isPass(move)) {
            moves.add(move);   // keeps parity + renju tracking counters
            continue;          // no stone, no captures
        }
```

(Adapt to each site's local shape; the invariant: pass enters the move LIST, never the board ARRAY, never capture processing.) Then audit `LiveBoardView` for `move / gridSize` or `move % gridSize` on HISTORY moves (e.g. last-move marker, move-list rendering) and skip/label pass moves there ("PASS" text where a coordinate would render, mirroring Go).

- [ ] **Step 4: Run** — test passes; full `:app:testDebugUnitTest` green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/be/submanifold/pentelive/liveGameRoom/ app/src/test/java/be/submanifold/pentelive/LiveTablePassTest.java
git commit -S -m "feat(renju): live table tolerates pass move 225 (list-only, board untouched)"
```

---

### Task 2: Live UI — PASS + DRAW? buttons, offer send/receive

**Files:**
- Modify: `app/src/main/res/layout/fragment_live_table.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveTableFragment.java`
- Modify: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveBoardView.java`
- Modify: `app/src/main/java/be/submanifold/pentelive/liveGameRoom/LiveGameRoomActivity.java`

**Interfaces:**
- Consumes: Task 1 (`isPass`), `RenjuLiveState.phase(n)`, existing dialog pattern (`undoRequested` at `LiveTableFragment.java:1026-1050`), dispatch chain (`LiveGameRoomActivity.java:340-370`), `sendEvent` string JSON convention.
- Produces: fragment methods `drawOffered(String player)`, `onDrawRejected(String player)`, `isDrawArmed()`; buttons `renjuPassButton`, `renjuDrawButton`.

- [ ] **Step 1: Layout + strings**

`fragment_live_table.xml` — insert a new row directly below `actionLayout` (L47-118), same 3-column feel not needed; a simple GONE-by-default horizontal row:

```xml
        <LinearLayout
            android:id="@+id/renjuActionLayout"
            android:layout_width="match_parent"
            android:layout_below="@id/actionLayout"
            android:orientation="horizontal"
            android:visibility="gone"
            android:layout_height="wrap_content">
            <Button
                android:id="@+id/renjuPassButton"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="@string/pass"
                android:textStyle="bold" />
            <Button
                android:id="@+id/renjuDrawButton"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="@string/draw_question"
                android:textStyle="bold" />
        </LinearLayout>
```

CHECK: the view below `actionLayout` today (L120+, a TextView) anchors with `layout_below="@id/actionLayout"` — re-anchor it to `@id/renjuActionLayout` so nothing overlaps.

`strings.xml` additions:

```xml
    <string name="draw_question">DRAW?</string>
    <string name="offers_draw">%1$s offers a draw. Playing a move also declines it.</string>
    <string name="draw_offer_armed">Draw offer will be sent with your move</string>
    <string name="draw_offered">draw offered</string>
    <string name="draw_accepted">draw accepted</string>
    <string name="draw_declined">draw offer declined</string>
    <string name="draw_offer_pending">draw offer pending…</string>
```

- [ ] **Step 2: Fragment — arming, visibility, sends**

`LiveTableFragment.java`:

Fields + accessors:

```java
    private LinearLayout renjuActionLayout;
    private Button renjuPassButton, renjuDrawButton;
    private boolean drawArmed = false;

    public boolean isDrawArmed() {
        return drawArmed;
    }

    private void setDrawArmed(boolean armed) {
        drawArmed = armed;
        if (renjuDrawButton != null) {
            renjuDrawButton.getBackground().setColorFilter(
                    armed ? android.graphics.Color.parseColor("#4CAF50") : null,
                    android.graphics.PorterDuff.Mode.MULTIPLY);
        }
    }
```

View wiring (next to the `playButton` lookup, L248):

```java
        renjuActionLayout = getView().findViewById(R.id.renjuActionLayout);
        renjuPassButton = getView().findViewById(R.id.renjuPassButton);
        renjuDrawButton = getView().findViewById(R.id.renjuDrawButton);
        renjuPassButton.setOnClickListener(v -> {
            if (mListener != null && renjuPostOpeningMyTurn()) {
                int passMove = table.getGridSize() * table.getGridSize();
                mListener.sendEvent("{\"dsgMoveTableEvent\":{\"move\":" + passMove
                        + ",\"moves\":[" + passMove + "],\"player\":\"" + me
                        + "\",\"table\":" + table.getId()
                        + (drawArmed ? ",\"drawOffer\":true" : "")
                        + ",\"time\":0}}");
                setDrawArmed(false);
            }
        });
        renjuDrawButton.setOnClickListener(v -> {
            setDrawArmed(!drawArmed);
            if (drawArmed) {
                Toast.makeText(activity, getString(R.string.draw_offer_armed), Toast.LENGTH_LONG).show();
            }
        });
```

Gate helper + visibility — add:

```java
    private boolean renjuPostOpeningMyTurn() {
        return table.isRenju()
                && table.getGameState().state == State.STARTED
                && table.isMyTurn(me)
                && table.getGameState().renjuState.phase(table.getMoves().size())
                       == RenjuLiveState.Phase.COMPLETE;
    }

    private void updateRenjuActionButtons() {
        if (renjuActionLayout == null) return;
        boolean show = renjuPostOpeningMyTurn();
        renjuActionLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show && table.getGameState().state != State.STARTED) {
            setDrawArmed(false); // game ended/reset
        }
    }
```

Call `updateRenjuActionButtons()` at the SAME three sites that manage the Go pass button (`stateChanged` L404-409, `addMove` tail ~L497-502, `addMoves` tail L546-551) and in `rejectGoState` for symmetry.

Incoming-offer dialog + reject feedback (mirror `undoRequested` L1026-1050):

```java
    public void drawOffered(String player) {
        if (!isAdded()) return;
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.offers_draw, player));
        String[] options = {getString(R.string.accept), getString(R.string.decline)};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    sendDrawReply(true);
                    break;
                case 1:
                    sendDrawReply(false);
                    break;
            }
        });
        AlertDialog dlg = builder.create();
        dlg.setCanceledOnTouchOutside(false);
        Window window = dlg.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.BOTTOM;
        dlg.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(wlp);
        dlg.show();
    }

    private void sendDrawReply(boolean accept) {
        if (mListener != null) {
            mListener.sendEvent("{\"dsgRenju" + (accept ? "Accept" : "Reject")
                    + "DrawTableEvent\":{\"player\":\"" + me + "\",\"table\":"
                    + table.getId() + ",\"time\":0}}");
        }
    }

    public void onDrawRejected(String player) {
        if (!isAdded()) return;
        setDrawArmed(false);
        Toast.makeText(activity, getString(R.string.draw_declined), Toast.LENGTH_SHORT).show();
    }
```

(Note the dialog is item-list style with an explicit Decline — matching undo. Back-button dismissal leaves the offer pending; the player's next move declines it server-side.)

- [ ] **Step 3: Board — armed offer rides stone moves**

`LiveBoardView.java`, RENJU_IDLE send site (L338) — replace the JSON with the flag-aware version:

```java
                    if (up) {
                        boolean offer = fragment.isDrawArmed();
                        fragment.getListener().sendEvent("{\"dsgMoveTableEvent\":{\"move\":" + move
                                + ",\"moves\":[" + move + "],\"player\":\"" + me
                                + "\",\"table\":" + table.getId()
                                + (offer ? ",\"drawOffer\":true" : "")
                                + ",\"time\":0}}");
                        if (offer) fragment.clearDrawArmedAfterSend();
                    }
```

Add `public void clearDrawArmedAfterSend() { setDrawArmed(false); }` to the fragment (public wrapper). Do NOT touch the non-renju send site at L249 (renju taps never reach it) or the opening send helpers (arming impossible pre-COMPLETE).

- [ ] **Step 4: Activity — dispatch new events + rejoin**

`LiveGameRoomActivity.java`:

1. `updateTableMove(...)` — after the move is applied, detect the flag:

```java
        Object offerFlag = data.get("drawOffer");
        String movePlayer = (String) data.get("player");
        if (Boolean.TRUE.equals(offerFlag) && movePlayer != null && !movePlayer.equals(me)) {
            addTableMessage(tableId, "* " + getString(R.string.draw_offered));
            LiveTableFragment fragment = (LiveTableFragment)
                    getSupportFragmentManager().findFragmentByTag("liveTable");
            if (fragment != null && fragment.table.getId() == tableId) {
                fragment.drawOffered(movePlayer);
            }
        }
```

(Adapt to `updateTableMove`'s actual local variables; it already extracts `table`/`player`.)

2. Dispatch-chain branches (insert after the `dsgUndoReplyTableEvent` branch, L360-362):

```java
                                } else if (jsonEvent.get("dsgRenjuAcceptDrawTableEvent") != null) {
                                    Map<String, Object> data = (Map<String, Object>) jsonEvent.get("dsgRenjuAcceptDrawTableEvent");
                                    addTableMessage((int) data.get("table"), "* " + getString(R.string.draw_accepted));
                                    // game end itself arrives via dsgGameStateTableEvent
                                } else if (jsonEvent.get("dsgRenjuRejectDrawTableEvent") != null) {
                                    Map<String, Object> data = (Map<String, Object>) jsonEvent.get("dsgRenjuRejectDrawTableEvent");
                                    int tblId = (int) data.get("table");
                                    addTableMessage(tblId, "* " + getString(R.string.draw_declined));
                                    LiveTableFragment fragment = (LiveTableFragment)
                                            getSupportFragmentManager().findFragmentByTag("liveTable");
                                    if (fragment != null && fragment.table.getId() == tblId) {
                                        fragment.onDrawRejected((String) data.get("player"));
                                    }
                                } else if (jsonEvent.get("dsgRenjuDrawTableErrorEvent") != null) {
                                    // invalid accept/reject — informational only
                                }
```

3. Rejoin: in `updateTableGameState(...)` read `drawOfferedBy` from the map; if present and not `me`, call `fragment.drawOffered(drawOfferedBy)`; if it IS `me`, `addTableMessage(tableId, "* " + getString(R.string.draw_offer_pending))`.

- [ ] **Step 5: Build + commit**

`JAVA_HOME=... ./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. `:app:testDebugUnitTest` green.

```bash
git add app/src/main/res/ app/src/main/java/be/submanifold/pentelive/liveGameRoom/
git commit -S -m "feat(renju): live PASS + DRAW? buttons, draw-offer send/receive/reject flows"
```

---

### Task 3: Turn-based — PASS/SUBMIT/DRAW? buttons, offer submit, accept flow

**Files:**
- Modify: `app/src/main/res/layout/activity_board.xml`
- Modify: `app/src/main/java/be/submanifold/pentelive/BoardActivity.java`
- Modify: `app/src/main/java/be/submanifold/pentelive/Game.java`
- Modify: `app/src/main/java/be/submanifold/pentelive/JsonModels.java`
- Test: extend `app/src/test/java/be/submanifold/pentelive/GameRenjuUnitTest.java`

**Interfaces:**
- Consumes: server plan Tasks 10-11 (`drawOffer` param, `acceptDraw`, `drawOffered` JSON); `"COMPLETE".equals(game.renjuPhase)`; `Game.submitMove(move, message, renjuAction)` → `buildSubmitMoveUrl` (`Game.java:929-938`).
- Produces: `Game.buildSubmitMoveUrl(..., boolean drawOffer)` appending `&drawOffer=true`; `Game.submitMove(String move, String message, String renjuAction, boolean drawOffer)`; `Game.acceptDraw()` (GET `command=acceptDraw&gid=...` — mirror `ResignTask`); `JsonModels.GameResponse.drawOffered`; `Game.isDrawOffered()`.

- [ ] **Step 1: Failing unit test** — extend `GameRenjuUnitTest`:

```java
    @Test public void submitMoveUrlCarriesDrawOffer() {
        String url = Game.buildSubmitMoveUrl("", "42", "225", "hi", null, true);
        assertTrue(url.contains("&drawOffer=true"));
        assertTrue(url.contains("&moves=225"));
        String plain = Game.buildSubmitMoveUrl("", "42", "112", "hi", null, false);
        assertFalse(plain.contains("drawOffer"));
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests '*GameRenjuUnitTest*'` → compile FAIL (no 6-arg overload).

- [ ] **Step 2: Game.java plumbing**

```java
    public static String buildSubmitMoveUrl(String hideStr, String gid, String moves,
                                            String message, String renjuAction) {
        return buildSubmitMoveUrl(hideStr, gid, moves, message, renjuAction, false);
    }

    public static String buildSubmitMoveUrl(String hideStr, String gid, String moves,
                                            String message, String renjuAction, boolean drawOffer) {
        String url = "https://www.pente.org/gameServer/tb/game?command=move" + hideStr
                + "&mobile=&gid=" + gid + "&moves=" + moves + "&message=" + message
                + PentePlayer.writeCreds();
        if (renjuAction != null && !renjuAction.isEmpty()) {
            url += "&renjuAction=" + renjuAction;
        }
        if (drawOffer) {
            url += "&drawOffer=true";
        }
        return url;
    }
```

`SubmitMoveTask`: add a `drawOffer` boolean field + constructor arg, thread it into BOTH URL builds (production `buildSubmitMoveUrl(...)` call at L544 and the `PentePlayer.development` branch at L546-551 — append `&drawOffer=true` there too). `submitMove` overloads:

```java
    public void submitMove(String move, String message, String renjuAction, boolean drawOffer) {
        SubmitMoveTask submitTask = new SubmitMoveTask(move, message, renjuAction, drawOffer);
        submitTask.execute((Void) null);
    }
```

JSON model + copy: `JsonModels.GameResponse` gains `public Boolean drawOffered;` (after `undoRequested`, L138); in `Game`'s response-copy block (~L1025, next to `undoRequested = Boolean.TRUE.equals(...)`):

```java
        drawOffered = Boolean.TRUE.equals(mGameJson.drawOffered);
```

plus field + getter `public boolean isDrawOffered()`.

`acceptDraw`: clone the `ResignTask` pattern (find it via `new ResignTask(` at `BoardActivity.java:192`) as `AcceptDrawTask` hitting `command=acceptDraw&gid=` + creds (+ the development-URL variant), exposed as `Game.acceptDraw()` or a task the activity runs — follow whichever home `ResignTask` has (it lives in `BoardActivity`; keep the new task beside it).

- [ ] **Step 3: Buttons — layout + behavior**

`activity_board.xml` `submitLayout` (L25-65): add two buttons between `submitButton` and `searchDBbutton`:

```xml
        <Button
            android:layout_width="0dp"
            android:layout_weight="2"
            android:layout_height="wrap_content"
            android:focusable="false"
            android:text="@string/pass"
            android:visibility="gone"
            android:textStyle="bold"
            android:id="@+id/renjuPassButton" />
        <Button
            android:layout_width="0dp"
            android:layout_weight="2"
            android:layout_height="wrap_content"
            android:focusable="false"
            android:text="@string/draw_question"
            android:visibility="gone"
            android:textStyle="bold"
            android:id="@+id/renjuDrawButton" />
```

`BoardActivity.java`:

State + wiring (in `onCreate`, near `setRegularSubmitListener()` L91):

```java
        renjuDrawArmed = false;
        Button renjuPass = findViewById(R.id.renjuPassButton);
        Button renjuDraw = findViewById(R.id.renjuDrawButton);
        if (renjuPass != null) renjuPass.setOnClickListener(v -> {
            if (!game.isActive()) return;
            game.submitMove("225", msg(), null, renjuDrawArmed);
            finish();
        });
        if (renjuDraw != null) renjuDraw.setOnClickListener(v -> {
            renjuDrawArmed = !renjuDrawArmed;
            renjuDraw.getBackground().setColorFilter(
                    renjuDrawArmed ? android.graphics.Color.parseColor("#4CAF50") : null,
                    android.graphics.PorterDuff.Mode.MULTIPLY);
            if (renjuDrawArmed) {
                Toast.makeText(this, getString(R.string.draw_offer_armed), Toast.LENGTH_LONG).show();
            }
        });
```

(`msg()` = the existing message-input read used by the renju SWAP buttons at L98; reuse it.)

Button-state rule (the 3-button spec) — centralize:

```java
    void updateRenjuTbButtons() {
        Button renjuPass = findViewById(R.id.renjuPassButton);
        Button renjuDraw = findViewById(R.id.renjuDrawButton);
        Button submit = findViewById(R.id.submitButton);
        boolean renjuComplete = game.isRenju() && "COMPLETE".equals(game.renjuPhase)
                && game.isActive();
        boolean staged = board.playedMove > -1;
        if (renjuPass != null) renjuPass.setVisibility(renjuComplete && !staged ? View.VISIBLE : View.GONE);
        if (renjuDraw != null) renjuDraw.setVisibility(renjuComplete ? View.VISIBLE : View.GONE);
        if (submit != null && renjuComplete) submit.setEnabled(staged);
    }
```

Call it: after game load/render (wherever `renjuPhase` regions run — the L311/321/328 branches' setup site), and from the board's stone-staging hook — `BoardView` already re-styles submit for renju (`styleRenjuSubmit` per L299 comment); add the call where `board.playedMove` changes land (grep `styleRenjuSubmit` and `invalidate()` calls in `BoardView`'s touch-up path; if no activity callback exists, add one: `boardActivity.updateRenjuTbButtons()` on ACTION_UP).

Submit path: in `setRegularSubmitListener`'s renju `"MOVE"` branch — CORRECTION: the gate must be phase `"COMPLETE"` for normal post-opening moves; read the current `renjuPhase` handling (L321 checks `"MOVE"`) and verify against the server's TB phase strings: `RENJU_MOVE` covers in-opening placements, `RENJU_COMPLETE` post-opening. Add a `"COMPLETE"` branch identical to the `"MOVE"` one (plain move, `renjuAction` null) if absent, and pass the armed flag:

```java
                    game.submitMove(moves, message, renjuAction, renjuDrawArmed);
```

(single change at the method's dispatch call; non-renju games pass `false`.)

Incoming offer (opponent offered; it's my turn): where the game JSON lands and `undoRequested`-style state is applied (the load/render path around the `game.renjuPhase` copy, `Game.java` ~L1084 / `BoardActivity` render entry): if `game.isDrawOffered() && game.isActive()` show a bottom `AlertDialog` (clone the `action_cancel_resign` chooser at L186-219):

```java
            String[] options = {getString(R.string.accept), getString(R.string.dismiss)};
            // title: getString(R.string.offers_draw, opponentName)
            // case 0: acceptDraw task -> refresh/finish
            // dismiss: do nothing — playing a move declines
```

Offerer side (`game.isDrawOffered()` but NOT my turn — I offered): show a persistent hint (Toast long or the existing status TextView): `R.string.draw_offer_pending`.

- [ ] **Step 4: Run tests + build**

`./gradlew :app:testDebugUnitTest` → green (incl. the new URL test). `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_board.xml app/src/main/java/be/submanifold/pentelive/ app/src/test/java/be/submanifold/pentelive/GameRenjuUnitTest.java
git commit -S -m "feat(renju): TB PASS/DRAW? buttons, drawOffer submit param, acceptDraw flow"
```

---

### Task 4: End-to-end (manual, emulator + local backend)

- Local backend: set `PentePlayer.development = true` (hits `https://10.0.2.2` — the working localhost backend). Test accounts from `pente-react-native/.env.local` (never commit).
- [ ] Live renju vs second client (react room in a browser): buttons appear only post-opening on my turn; PASS works; double-pass draws; DRAW?→stone carries offer (react side sees modal); accept/reject both directions; reject un-arms/toasts; dismissing dialog then moving declines; rejoin restores pending offer.
- [ ] TB renju: 3-button behavior per spec (PASS/SUBMIT-disabled/DRAW?; staging hides PASS, enables SUBMIT; DRAW? green + toast; PASS visible while armed); offer visible to opponent on next poll (`drawOffered`); Accept ends game drawn; moving declines; offer + pass combo.
- [ ] Regression: Go pass unaffected (playButton live, submitButton TB); pente/swap2/d-pente boards unchanged; renju opening flows (swap/branch/offer/selection) unchanged.
