const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const canvas = { getContext: () => ({}) };
const GAME = { PENTE: 1, RENJU: 31, TB_RENJU: 81, TOMBSTONE: 33, SPEED_TOMBSTONE: 34, TB_TOMBSTONE: 83 };
const sandbox = { document: { getElementById: () => canvas }, GAME, gridSize: 19, rated: false, moves: [] };
const ctx = vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../tb/gameScript.js'), 'utf8'), ctx);
const cell = (x, y) => ctx.abstractBoard[x][y];
const replay = (m) => { ctx.whiteCaptures = 0; ctx.blackCaptures = 0; ctx.replayTombstoneGame(ctx.abstractBoard, m, m.length); };
const J = (x) => JSON.parse(JSON.stringify(x));
let n = 0;
const eq = (a, b, msg) => { assert.deepStrictEqual(J(a), b, msg); n++; };
const is = (a, b, msg) => { assert.strictEqual(a, b, msg); n++; };

// decode
eq(ctx.tombstoneDecode(0), { kind: 'own', point: 0 });
eq(ctx.tombstoneDecode(360), { kind: 'own', point: 360 });
eq(ctx.tombstoneDecode(361), { kind: 'dead', point: 0 });
eq(ctx.tombstoneDecode(721), { kind: 'dead', point: 360 });
eq(ctx.tombstoneDecode(722), { kind: 'pass', point: -1 });
eq(ctx.tombstoneDecode(723), { kind: 'invalid', point: -1 });
eq(ctx.tombstoneDecode(-1), { kind: 'invalid', point: -1 });

// isTombstoneGame
[33, 34, 83].forEach(g => is(ctx.isTombstoneGame(g), true));
[1, 31, 81].forEach(g => is(ctx.isTombstoneGame(g), false));

// turns
const m1 = [180, 181, 561, 722, 722, 361, 200];
eq(ctx.tombstoneTurns(m1), [
  { player: 1, own: 180, dead: -1, start: 0, end: 0 },
  { player: 2, own: 181, dead: 561, start: 1, end: 2 },
  { player: 1, own: 722, dead: -1, start: 3, end: 3 },
  { player: 2, own: 722, dead: 361, start: 4, end: 5 },
  { player: 1, own: 200, dead: -1, start: 6, end: 6 }]);
eq(ctx.tombstoneTurns(m1, 3), [
  { player: 1, own: 180, dead: -1, start: 0, end: 0 },
  { player: 2, own: 181, dead: 561, start: 1, end: 2 }]);

// orphan dead
eq(ctx.tombstoneTurns([180, 561])[1], { player: 0, own: -1, dead: 561, start: 1, end: 1 });
is(ctx.tombstoneTurns([180, 181, 561, 562]).length, 3);
const o = ctx.tombstoneTurns([180, 999, 561]);
is(o.length, 3);
eq(o[1], { player: 0, own: 999, dead: -1, start: 1, end: 1 });
eq(o[2], { player: 0, own: -1, dead: 561, start: 2, end: 2 });

// current player
is(ctx.tombstoneCurrentPlayer([]), 1);
is(ctx.tombstoneCurrentPlayer([180]), 2);
is(ctx.tombstoneCurrentPlayer([180, 181, 561]), 1);
is(ctx.tombstoneCurrentPlayer([180, 722]), 1);
is(ctx.tombstoneCurrentPlayer([180, 722, 361, 200]), 2);

// replay capture + dead on vacated point
replay([180, 181, 300, 182, 183, 542]);
is(cell(9, 9), 1); is(cell(10, 9), 3); is(cell(11, 9), 0); is(cell(12, 9), 1); is(cell(15, 15), 1);
is(ctx.blackCaptures, 2);

// dead stone blocks a capture
replay([180, 181, 300, 543, 0, 183]);
is(cell(10, 9), 2); is(cell(11, 9), 3); is(cell(12, 9), 1); is(ctx.blackCaptures, 0);

// pass places nothing
replay([180, 722]);
let nonzero = 0;
for (let x = 0; x < 19; x++) for (let y = 0; y < 19; y++) if (cell(x, y) !== 0) nonzero++;
is(nonzero, 1); is(cell(9, 9), 1);

// labels
is(ctx.tombstoneLabel(180), 'K10'); is(ctx.tombstoneLabel(722), 'PASS');
is(ctx.tombstoneLabel(561), 'dead L9'); is(ctx.tombstoneLabel(999), '?');
is(ctx.tombstoneTurnLabel({ own: 181, dead: 561 }), 'L10 + dead L9');
is(ctx.tombstoneTurnLabel({ own: 722, dead: 361 }), 'PASS + dead A19');
is(ctx.tombstoneTurnLabel({ own: 181, dead: -1 }), 'L10');
is(ctx.tombstoneTurnLabel({ own: 722, dead: -1 }), 'PASS');
is(ctx.tombstoneTurnLabel({ own: -1, dead: 561 }), 'dead L9');

// button state
eq(ctx.tombstoneTbButtonState(-1, -1), { label: 'Place only a dead stone', action: 'ownPass' });
eq(ctx.tombstoneTbButtonState(181, -1), { label: 'Submit without dead stone', action: 'submitOwn' });
eq(ctx.tombstoneTbButtonState(722, -1), { label: 'Submit without dead stone', action: 'submitOwn' });
eq(ctx.tombstoneTbButtonState(181, 200), { label: 'Submit', action: 'submitBoth' });

// click
const click = (a, b, p, v) => { const r = ctx.tombstoneTbClick(a, b, p, v); return [r.tsOwn, r.tsDead]; };
eq(click(-1, -1, 181, 0), [181, -1]);
eq(click(181, -1, 200, 0), [181, 200]);
eq(click(722, -1, 200, 0), [722, 200]);
eq(click(181, -1, 181, 2), [-1, -1]);
eq(click(181, 200, 200, 3), [-1, -1]);
eq(click(181, 200, 50, 0), [181, 200]);

// preview / submit
eq(ctx.tombstonePreviewCodes(-1, -1), []); is(ctx.tombstoneSubmitMoves(-1, -1), null);
eq(ctx.tombstonePreviewCodes(181, -1), [181]); is(ctx.tombstoneSubmitMoves(181, -1), '181');
eq(ctx.tombstonePreviewCodes(181, 200), [181, 561]); is(ctx.tombstoneSubmitMoves(181, 200), '181,561');
eq(ctx.tombstonePreviewCodes(722, -1), [722]); is(ctx.tombstoneSubmitMoves(722, -1), '722');
eq(ctx.tombstonePreviewCodes(722, 0), [722, 361]); is(ctx.tombstoneSubmitMoves(722, 0), '722,361');

// last marks
const lm = [180, 181, 561];
eq(ctx.tombstoneLastMarks(lm, 3), [181, 200]);
eq(ctx.tombstoneLastMarks(lm, 2), [181]);
eq(ctx.tombstoneLastMarks(lm, 1), [180]);
eq(ctx.tombstoneLastMarks(lm, 0), []);
eq(ctx.tombstoneLastMarks([180, 722], 2), []);
eq(ctx.tombstoneLastMarks([180, 722, 361], 3), [0]);

// step
const m = [180, 181, 561, 722, 200];
const st = (u, d) => ctx.tombstoneStep(m, u, d);
is(st(5, -1), 4); is(st(4, -1), 3); is(st(3, -1), 1); is(st(1, -1), 1); is(st(2, -1), 1);
is(st(1, 1), 3); is(st(2, 1), 3); is(st(4, 1), 5); is(st(5, 1), 5);

// drawLastMarks
const calls = [];
ctx.drawRedDot = (i, j) => calls.push([i, j]);
ctx.tombstoneDrawLastMarks([180, 181, 561], 3);
eq(calls, [[10, 9], [10, 10]]);

console.log(`OK: ${n} tombstone assertions passed`);
