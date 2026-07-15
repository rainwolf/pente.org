# Renju Pass / Draw Offers — React Live Game Room Implementation Plan (2/4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PASS button, draw-offer arming, and draw-offer accept/reject flows for renju in the react live game room.

**Architecture:** Protocol descriptors gain two accept/reject events (commands auto-generated); the move command carries an optional `drawOffer` passthrough field; reducer state follows the absent-when-inactive convention (`draw_requested`, `draw_armed`, `draw_pending`); UI = two buttons in `GameInfoPanel`, a `DrawOfferModal` cloned from `UndoModal`, snackbar notices via the existing `notification` plumbing.

**Tech Stack:** React 18.3 + MUI, Redux (+ redux-websocket), Vitest.

**Repo:** `/Users/waliedothman/mariposa/coding/pente.org-project/react_live_game_room` — all paths below relative to its `src/`; commits go to THIS repo (signed, `git commit -S`).

**Spec:** `pente.org/docs/superpowers/specs/2026-07-15-renju-pass-draw-design.md`. Server counterpart: plan 1/4 (Tasks 5-6) — the wire contract below matches it exactly.

## Global Constraints

- Renju pass = `game.gridSize * game.gridSize` (= 225) sent as a normal move — identical to the Go pass frame.
- Wire contract (server plan Task 5/6): outbound `dsgMoveTableEvent` may carry `drawOffer: true`; inbound move events may carry it too. New events both directions: `dsgRenjuAcceptDrawTableEvent` / `dsgRenjuRejectDrawTableEvent` (`player`, `table`). Inbound error: `dsgRenjuDrawTableErrorEvent`. Inbound `dsgGameStateTableEvent` may carry `drawOfferedBy` (player name) on rejoin.
- Pass/DRAW? buttons show ONLY when: renju game, `renjuPhaseNow() === RenjuPhase.COMPLETE`, my turn, `GameState.State.STARTED`. `RenjuPhase.MOVE` is an opening sub-phase — never enables them.
- State convention: request fields are absent-when-inactive (`undefined`), like `undo_requested`.
- Run tests with `npm test` (Vitest) from the repo root.

---

### Task 1: Protocol — descriptors, commands, decode

**Files:**
- Modify: `src/protocol/messages.js`
- Test: `src/protocol/__tests__/commands.test.js` (extend)

**Interfaces:**
- Produces: `Commands.renjuAcceptDraw({player, table})`, `Commands.renjuRejectDraw({player, table})` (auto-generated from descriptors); `Commands.move(...)` accepts an extra `drawOffer` arg (passthrough — `buildCommand` copies all args, `out` only lists REQUIRED fields, so no descriptor change is needed for the flag); decode accepts the two new inbound events + the error event.

- [ ] **Step 1: Failing tests** — append to `commands.test.js`:

```javascript
describe('renju draw-offer commands', () => {
  test('renjuAcceptDraw frame', () => {
    expect(Commands.renjuAcceptDraw({ player: 'alice', table: 5 }))
      .toEqual({ dsgRenjuAcceptDrawTableEvent: { player: 'alice', table: 5, time: 0 } });
  });
  test('renjuRejectDraw frame', () => {
    expect(Commands.renjuRejectDraw({ player: 'bob', table: 5 }))
      .toEqual({ dsgRenjuRejectDrawTableEvent: { player: 'bob', table: 5, time: 0 } });
  });
  test('move passes drawOffer through', () => {
    expect(Commands.move({ move: 225, moves: [225], player: 'alice', table: 5, drawOffer: true }))
      .toEqual({ dsgMoveTableEvent: { move: 225, moves: [225], player: 'alice', table: 5, drawOffer: true, time: 0 } });
  });
});
```

- [ ] **Step 2: Run** — `npm test` → the new tests FAIL (`Commands.renjuAcceptDraw` undefined; passthrough already passes — confirm which fail).

- [ ] **Step 3: Implement** — in `messages.js`, after the three `dsgRenjuTaraguchi*` lines (L58-60):

```javascript
  dsgRenjuAcceptDrawTableEvent:         { dir: 'both', cmd: 'renjuAcceptDraw', out: ['player', 'table'], req: TBL },
  dsgRenjuRejectDrawTableEvent:         { dir: 'both', cmd: 'renjuRejectDraw', out: ['player', 'table'], req: TBL },
```

And in `ERROR_EVENTS` (L84-97) add:

```javascript
  'dsgRenjuDrawTableErrorEvent',
```

(Do NOT add `drawOffer` to the move descriptor's `out` — that list is required-fields and would break every existing move.)

- [ ] **Step 4: Run** — `npm test` → PASS (all suites; decode tests keep passing since `INBOUND_TYPES` derives from `MESSAGES`).

- [ ] **Step 5: Commit**

```bash
git add src/protocol/messages.js src/protocol/__tests__/commands.test.js
git commit -S -m "feat(renju): draw-offer protocol descriptors (accept/reject/error, move drawOffer passthrough)"
```

---

### Task 2: Reducer — draw-offer state lifecycle

**Files:**
- Modify: `src/redux_reducers/utils.js`
- Modify: `src/redux_reducers/rootReducer.js`
- Modify: `src/redux_actions/actionTypes.js`
- Test: `src/redux_reducers/__tests__/reducer.test.js` (extend)

**Interfaces:**
- Consumes: Task 1 event types; existing `addMove`, `changeGameState`, `addTableMessage`, `EVENT_HANDLERS`, absent-when-inactive convention.
- Produces state fields (Task 3/4 consume): `draw_requested` (opponent's pending offer → my modal; holds offerer name), `draw_armed` (I pressed DRAW?, not yet moved; boolean), `draw_pending` (my offer awaiting opponent; boolean), `notification` reuse with `{kind:'info', message}`. UI actions: `ARM_DRAW_OFFER`, `DISARM_DRAW_OFFER`, `DISMISS_DRAW_MODAL`.

- [ ] **Step 1: Failing tests** — append to `reducer.test.js` (follow its existing setup helpers for building a table/game state; adapt constructor calls to the file's local helpers, keep the assertions):

```javascript
describe('renju draw offers', () => {
  test('opponent move with drawOffer sets draw_requested', () => {
    // state: me='bob', renju table, opponent 'alice' moves with drawOffer
    const s = reduce(baseRenjuState('bob'), {
      type: 'dsgMoveTableEvent',
      payload: { move: 100, moves: [100], player: 'alice', table: 1, drawOffer: true },
    });
    expect(s.draw_requested).toBe('alice');
  });

  test('own move echo with drawOffer flips armed -> pending', () => {
    const s0 = { ...baseRenjuState('bob'), draw_armed: true };
    const s = reduce(s0, {
      type: 'dsgMoveTableEvent',
      payload: { move: 100, moves: [100], player: 'bob', table: 1, drawOffer: true },
    });
    expect(s.draw_armed).toBeUndefined();
    expect(s.draw_pending).toBe(true);
  });

  test('own move without offer clears a pending incoming offer (implicit decline)', () => {
    const s0 = { ...baseRenjuState('bob'), draw_requested: 'alice' };
    const s = reduce(s0, {
      type: 'dsgMoveTableEvent',
      payload: { move: 101, moves: [101], player: 'bob', table: 1 },
    });
    expect(s.draw_requested).toBeUndefined();
  });

  test('reject event clears everything and notifies the offerer', () => {
    const s0 = { ...baseRenjuState('bob'), draw_pending: true };
    const s = reduce(s0, {
      type: 'dsgRenjuRejectDrawTableEvent',
      payload: { player: 'alice', table: 1 },
    });
    expect(s.draw_pending).toBeUndefined();
    expect(s.notification).toEqual({ kind: 'info', message: 'Draw offer declined' });
  });

  test('game-state sync restores pending offer on rejoin', () => {
    const s = reduce(baseRenjuState('bob'), {
      type: 'dsgGameStateTableEvent',
      payload: { table: 1, state: 2, drawOfferedBy: 'alice' },
    });
    expect(s.draw_requested).toBe('alice');
    const s2 = reduce(baseRenjuState('bob'), {
      type: 'dsgGameStateTableEvent',
      payload: { table: 1, state: 2, drawOfferedBy: 'bob' },
    });
    expect(s2.draw_pending).toBe(true);
  });
});
```

- [ ] **Step 2: Run** — `npm test` → new tests FAIL.

- [ ] **Step 3: Implement**

`actionTypes.js` — add three constants next to the other UI actions:

```javascript
export const ARM_DRAW_OFFER = 'ARM_DRAW_OFFER';
export const DISARM_DRAW_OFFER = 'DISARM_DRAW_OFFER';
export const DISMISS_DRAW_MODAL = 'DISMISS_DRAW_MODAL';
```

`utils.js`:

1. In `addMove` (L198-219), after the existing table-match logic (inside `if (data.table === state.table)`), append:

```javascript
      if (data.drawOffer && data.player !== state.me) {
         state.draw_requested = data.player;
         addTableMessage({player: 'game server', text: 'draw offered'}, state);
      }
      if (data.player === state.me) {
         delete state.draw_requested;          // my move implicitly declines
         if (data.drawOffer) {
            delete state.draw_armed;
            state.draw_pending = true;         // my offer is now out
            addTableMessage({player: 'game server', text: 'draw offer sent'}, state);
         }
      }
```

2. New handlers (next to `undoReply`):

```javascript
export function renjuAcceptDraw(data, state) {
   if (data.table === state.table) {
      delete state.draw_requested;
      delete state.draw_pending;
      delete state.draw_armed;
      addTableMessage({player: 'game server', text: 'draw accepted'}, state);
      // the game end itself arrives via dsgGameStateTableEvent
   }
}

export function renjuRejectDraw(data, state) {
   if (data.table === state.table) {
      const wasPending = state.draw_pending;
      delete state.draw_requested;
      delete state.draw_pending;
      delete state.draw_armed;
      if (wasPending) {
         state.notification = {kind: 'info', message: 'Draw offer declined'};
      }
      addTableMessage({player: 'game server', text: 'draw offer declined'}, state);
   }
}
```

3. In `changeGameState` (the transition-cleanup block at ~L221-231 that clears `undo_requested`/`cancel_requested`): also `delete state.draw_requested; delete state.draw_pending; delete state.draw_armed;` THEN restore from sync:

```javascript
   if (data.drawOfferedBy) {
      if (data.drawOfferedBy === state.me) {
         state.draw_pending = true;
      } else {
         state.draw_requested = data.drawOfferedBy;
      }
   }
```

`rootReducer.js`:

1. `EVENT_HANDLERS` map — two lines after the renju taraguchi entries:

```javascript
   dsgRenjuAcceptDrawTableEvent: (p, s) => renjuAcceptDraw(p, s),
   dsgRenjuRejectDrawTableEvent: (p, s) => renjuRejectDraw(p, s),
```

(+ import the two handlers from `./utils`.)

2. Switch cases for the UI actions (next to whatever handles `PRESSED_PLAY`):

```javascript
      case ARM_DRAW_OFFER:
         newState.draw_armed = true;
         break;
      case DISARM_DRAW_OFFER:
         delete newState.draw_armed;
         break;
      case DISMISS_DRAW_MODAL:
         delete newState.draw_requested;
         break;
```

- [ ] **Step 4: Run** — `npm test` → PASS (all suites).

- [ ] **Step 5: Commit**

```bash
git add src/redux_reducers/ src/redux_actions/actionTypes.js
git commit -S -m "feat(renju): draw-offer reducer lifecycle (requested/armed/pending, rejoin sync, implicit decline)"
```

---

### Task 3: GameInfoPanel — PASS + DRAW? buttons; move send carries the flag

**Files:**
- Modify: `src/Components/Table/GameInfoPanel.js`
- Modify: `src/Components/Board/Board.js`

**Interfaces:**
- Consumes: Task 2 (`draw_armed`, `ARM_DRAW_OFFER`/`DISARM_DRAW_OFFER`), `RenjuPhase` from `game/openingPhase`, `game.renjuPhaseNow()`, `game.isRenjuGame()`, `table.isMyTurn(game)`.
- Produces: renju PASS button (move 225, offer-aware), DRAW? toggle (green when armed), all `Commands.move` call sites offer-aware.

- [ ] **Step 1: GameInfoPanel changes**

Imports: add `import {RenjuPhase} from '../../game/openingPhase';` and `ARM_DRAW_OFFER, DISARM_DRAW_OFFER` to the actionTypes import.

`mapStateToProps`: add `draw_armed: state.draw_armed, draw_pending: state.draw_pending,`.

`mapDispatchToProps`: add:

```javascript
      arm_draw: () => dispatch({type: ARM_DRAW_OFFER}),
      disarm_draw: () => dispatch({type: DISARM_DRAW_OFFER}),
```

Inside the component, next to `pass()` (L294-302) — make pass offer-aware and add helpers:

```javascript
   const renjuPostOpening = game.isRenjuGame()
      && game.renjuPhaseNow() === RenjuPhase.COMPLETE;

   const pass = () => {
      const pass_move = game.gridSize * game.gridSize;
      props.send_message(Commands.move({
         move: pass_move,
         moves: [pass_move],
         player: table.me,
         table: table.table,
         ...(props.draw_armed ? {drawOffer: true} : {}),
      }));
   };

   const toggleDraw = () => {
      props.draw_armed ? props.disarm_draw() : props.arm_draw();
   };
```

Button row — extend the Go PASS gate (L386-394) to renju, and add DRAW?:

```javascript
                     {(table.isMyTurn(game) && (game.isGo() || renjuPostOpening)
                           && game.gameState.state === GameState.State.STARTED) &&
                        <Grid item xs>
                           <div style={{display: 'table', margin: '0 auto'}}>
                              <Button variant="outlined" color="primary" onClick={pass}>
                                 PASS
                              </Button>
                           </div>
                        </Grid>
                     }
                     {(table.isMyTurn(game) && renjuPostOpening
                           && game.gameState.state === GameState.State.STARTED && !props.draw_pending) &&
                        <Grid item xs>
                           <div style={{display: 'table', margin: '0 auto'}}>
                              <Button variant={props.draw_armed ? 'contained' : 'outlined'}
                                      color={props.draw_armed ? 'success' : 'primary'}
                                      onClick={toggleDraw}>
                                 DRAW?
                              </Button>
                           </div>
                        </Grid>
                     }
```

Armed notice — simplest consistent mechanism: when arming, also emit the info snackbar. In `toggleDraw`, on arm: `props.notify('Draw offer will be sent with your move');` where `notify` dispatches the existing notification shape:

```javascript
      notify: (message) => dispatch({type: SET_NOTIFICATION, payload: {kind: 'info', message}}),
```

CHECK FIRST: grep `redux_actions/actionTypes` and the reducer for how `state.notification` is SET today (the excerpt shows only `REMOVE_SNACK` clearing it and reducers setting it directly). If no `SET_NOTIFICATION` action exists, set it reducer-side instead: handle it inside the `ARM_DRAW_OFFER` case (`newState.notification = {kind:'info', message:'Draw offer will be sent with your move'};`) and drop the `notify` prop — prefer this if in doubt, it keeps notification writes in the reducer like every existing site.

- [ ] **Step 2: Board.js — stone moves carry the flag**

`Board.js:41` (and renju move variants at L47/51/55): every `Commands.move({...})` call gains the same conditional spread. Board must read `draw_armed` from state (add to its `mapStateToProps`):

```javascript
   ...(props.draw_armed ? {drawOffer: true} : {}),
```

(Only for NORMAL stone placements — renju opening sends `renjuSwap`/`renjuOffer10`/`renjuSelect1` frames, which can never be armed since arming requires phase COMPLETE.)

- [ ] **Step 3: Verify + commit**

`npm test` (suites still green), `npm run build` (bundles clean). Manual check happens in Task 5.

```bash
git add src/Components/Table/GameInfoPanel.js src/Components/Board/Board.js
git commit -S -m "feat(renju): PASS + DRAW? buttons, offer-aware move sends"
```

---

### Task 4: DrawOfferModal + mounting + offerer feedback

**Files:**
- Create: `src/Components/Table/DrawOfferModal.js`
- Modify: `src/Pages/Table.js`

**Interfaces:**
- Consumes: Task 2 (`draw_requested`, `DISMISS_DRAW_MODAL`), Task 1 (`Commands.renjuAcceptDraw/renjuRejectDraw`).

- [ ] **Step 1: Create the modal** — clone of `UndoModal.js`, full file:

```javascript
import React from 'react';
import PropTypes from 'prop-types';
import {withStyles} from '@mui/styles';
import Typography from '@mui/material/Typography';
import Modal from '@mui/material/Modal';
import Button from '@mui/material/Button';

import {connect} from 'react-redux';
import {send_message, DISMISS_DRAW_MODAL} from "../../redux_actions/actionTypes";
import {Commands} from '../../protocol';
import {selectCurrentTable} from '../../selectors';

function getModalStyle() {
   const top = 50;
   const left = 50;

   return {
      top: `${top}%`,
      left: `${left}%`,
      transform: `translate(-${top}%, -${left}%)`,
   };
}

const styles = theme => ({
   paper: {
      position: 'absolute',
      backgroundColor: 'white',
      boxShadow: '10px 10px 10px black',
      padding: '2%',
      outline: 'none',
   },
});

const mapStateToProps = state => {
   return {
      table: selectCurrentTable(state),
      draw_requested: state.draw_requested
   }
};

const mapDispatchToProps = dispatch => {
   return {
      send_message: message => {
         dispatch(send_message(message));
      },
      dismiss: () => {
         dispatch({type: DISMISS_DRAW_MODAL});
      }
   }
};

const UnconnectedDrawOfferModal = (props) => {

   const {classes, table, draw_requested} = props;

   const accept = () => {
      props.send_message(Commands.renjuAcceptDraw({player: table.me, table: table.table}));
   };
   const reject = () => {
      props.send_message(Commands.renjuRejectDraw({player: table.me, table: table.table}));
   };

   return (
      <div>
         <Modal
            aria-labelledby="draw-offer-title"
            aria-describedby="draw-offer-description"
            open={draw_requested !== undefined}
            onClose={props.dismiss}
         >
            <div style={getModalStyle()} className={classes.paper}>
               <Typography variant="h6" id="draw-offer-title">
                  Draw offered
               </Typography>
               <Typography variant="subtitle1" id="draw-offer-description">
                  {draw_requested} offers a draw. Playing a move also declines it.
               </Typography>
               <Button onClick={accept}>Accept</Button>
               <Button onClick={reject}>Reject</Button>
            </div>
         </Modal>
      </div>
   );
};

UnconnectedDrawOfferModal.propTypes = {
   classes: PropTypes.object.isRequired,
};

const DrawOfferModal = connect(mapStateToProps, mapDispatchToProps)(withStyles(styles)(UnconnectedDrawOfferModal));

export default DrawOfferModal;
```

(Import note: if `send_message` and action constants live in different modules, split the import to match `UndoModal.js`'s exact import lines. `onClose` fires on backdrop/esc — the DISMISS action hides the modal locally; the offer stays pending server-side and the player's next move declines it, matching the spec.)

- [ ] **Step 2: Mount** — `Pages/Table.js`: add `import DrawOfferModal from '../Components/Table/DrawOfferModal';` and `<DrawOfferModal/>` next to `<UndoModal/>` in the modal list (L126-139).

- [ ] **Step 3: Offerer feedback mount check** — `MessageSnack` (renders `notification.kind === 'info'`) is NOT mounted in `Pages/Table.js`. Grep its mount point (`grep -rn "MessageSnack" src/`). If it isn't rendered on the table page, add `<MessageSnack/>` beside `<Snack/>` in the modal list — the reject-notice and armed-notice from Tasks 2-3 depend on it being visible at the table.

- [ ] **Step 4: Verify + commit**

`npm test` + `npm run build`.

```bash
git add src/Components/Table/DrawOfferModal.js src/Pages/Table.js src/Components/MessageSnack.js
git commit -S -m "feat(renju): draw-offer modal + table-page notification mount"
```

---

### Task 5: End-to-end against the local server (manual)

Prereq: server plan (1/4) Tasks 1-7 deployed on the local docker stack.

- [ ] `npm start`, two browser sessions (test accounts), renju table, play through the Taraguchi opening.
- [ ] During opening: no PASS, no DRAW? (phase MOVE included — check move 5/6 window).
- [ ] After opening: PASS sends 225, move list renders "PASS", turn flips.
- [ ] Two consecutive passes: game ends, both clients show draw (game-state text "game over, game is a draw").
- [ ] DRAW? arms (green + snackbar), stone move carries offer → opponent modal appears; Accept ends game drawn; Reject clears + offerer sees "Draw offer declined"; dismissing the modal then moving declines (offerer notified via reject broadcast).
- [ ] Pass+offer combo: arm then PASS — offer rides the pass.
- [ ] Undo request while my offer pending → offer cancelled (server reject broadcast clears my pending state).
- [ ] Rejoin (reload page) with an offer pending: modal (or pending notice) restored via `drawOfferedBy`.
- [ ] Old-client sanity: this client vs an old build — moves with `drawOffer` don't break the old client (field ignored).

No commit — checklist only. Fix regressions found here in the task that owns the code.
