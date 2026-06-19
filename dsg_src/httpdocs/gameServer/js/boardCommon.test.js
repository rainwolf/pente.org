const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

const HERE = __dirname;
const gameScriptSrc = fs.readFileSync(path.join(HERE, '../tb/gameScript.js'), 'utf8');
const boardCommonSrc = fs.readFileSync(path.join(HERE, 'boardCommon.js'), 'utf8');

// Pull ONLY the leading `var <name>Color = "...";` lines out of gameScript.js so the
// test stays in sync with the real palette without evaluating its DOM-dependent body.
const colorLines = gameScriptSrc
  .split('\n')
  .filter(l => /^var\s+\w+Color\s*=/.test(l))
  .join('\n');

// Named ids, mirroring org.pente.game.GridStateFactory (live=base, SPEED=+1, TB=+50).
const base = {
  PENTE: 1, KERYO: 3, GOMOKU: 5, DPENTE: 7, GPENTE: 9, POOF_PENTE: 11,
  CONNECT6: 13, BOAT_PENTE: 15, DKERYO: 17, GO: 19, GO9: 21, GO13: 23,
  OPENTE: 25, SWAP2PENTE: 27, SWAP2KERYO: 29, RENJU: 31,
};
const GAME = {};
for (const [name, id] of Object.entries(base)) {
  GAME[name] = id;
  GAME['SPEED_' + name] = id + 1;
  GAME['TB_' + name] = id + 50;
}

// Expected family -> color var name + replay fn name (the authoritative mapping).
const FAMILIES = [
  { names: ['PENTE'],       color: 'penteColor',      replay: 'replayPenteGame' },
  { names: ['KERYO'],       color: 'keryoPenteColor',  replay: 'replayKeryoPenteGame' },
  { names: ['GOMOKU'],      color: 'gomokuColor',      replay: 'replayGomokuGame' },
  { names: ['DPENTE'],      color: 'dPenteColor',      replay: 'replayPenteGame' },
  { names: ['GPENTE'],      color: 'gPenteColor',      replay: 'replayGPenteGame' },
  { names: ['POOF_PENTE'],  color: 'poofPenteColor',   replay: 'replayPoofPenteGame' },
  { names: ['CONNECT6'],    color: 'connect6Color',    replay: 'replayConnect6Game' },
  { names: ['BOAT_PENTE'],  color: 'boatPenteColor',   replay: 'replayPenteGame' },
  { names: ['DKERYO'],      color: 'dkeryoPenteColor',  replay: 'replayKeryoPenteGame' },
  { names: ['GO','GO9','GO13'], color: 'goColor',       replay: 'replayGoGame' },
  { names: ['OPENTE'],      color: 'oPenteColor',      replay: 'replayOPenteGame' },
  { names: ['SWAP2PENTE'],  color: 'swap2PenteColor',  replay: 'replayPenteGame' },
  { names: ['SWAP2KERYO'],  color: 'swap2KeryoColor',  replay: 'replayKeryoPenteGame' },
  { names: ['RENJU'],       color: 'renjuColor',       replay: 'replayRenjuGame' },
];

const REPLAY_FNS = [
  'replayPenteGame','replayKeryoPenteGame','replayGomokuGame','replayRenjuGame','replayGPenteGame',
  'replayPoofPenteGame','replayConnect6Game','replayGoGame','replayOPenteGame',
];

let lastCalled = null;
const sandbox = { GAME, game: 0, whiteCaptures: 0, blackCaptures: 0 };
REPLAY_FNS.forEach(fn => { sandbox[fn] = () => { lastCalled = fn; }; });

const ctx = vm.createContext(sandbox);
vm.runInContext(colorLines, ctx);     // defines penteColor, ..., swap2KeryoColor
vm.runInContext(boardCommonSrc, ctx); // defines getBoardColor, replayMoves

let checks = 0;
for (const fam of FAMILIES) {
  for (const short of fam.names) {
    for (const prefix of ['', 'SPEED_', 'TB_']) {
      const id = GAME[prefix + short];
      const expectedColor = ctx[fam.color];
      assert.strictEqual(
        ctx.getBoardColor(id), expectedColor,
        `getBoardColor(${prefix + short}=${id}) should be ${fam.color} (${expectedColor})`);

      sandbox.game = id;
      lastCalled = null;
      ctx.replayMoves([], [], 0);
      assert.strictEqual(
        lastCalled, fam.replay,
        `replayGame for ${prefix + short}=${id} should call ${fam.replay}, called ${lastCalled}`);
      checks += 2;
    }
  }
}

// Unknown ids are loud.
assert.throws(() => ctx.getBoardColor(99999), /unknown game id/);
sandbox.game = 99999;
assert.throws(() => ctx.replayMoves([], [], 0), /unknown game id/);

console.log(`OK: ${checks} mapping assertions + 2 throw assertions passed`);
