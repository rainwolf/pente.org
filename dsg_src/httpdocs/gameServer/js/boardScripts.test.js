// Pages must load the shared board JS through boardScripts.jspf, which adds a ?v=<mtime>
// cache-buster. A bare <script src=".../gameScript.js"> lets browsers keep a heuristically
// cached old copy after a deploy, while the freshly served JSP calls functions that only the
// new copy defines (e.g. isTombstoneGame), so every board click throws a ReferenceError.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const HTTPDOCS = path.join(__dirname, '../..');
const SHARED = ['/gameServer/tb/gameScript.js', '/gameServer/js/boardCommon.js'];

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (/\.(jsp|jspf|html)$/.test(e.name)) out.push(p);
  }
  return out;
}

const files = walk(HTTPDOCS, []);
const jspf = path.join(HTTPDOCS, 'gameServer/boardScripts.jspf');
assert.ok(fs.existsSync(jspf), 'gameServer/boardScripts.jspf must exist');
const jspfSrc = fs.readFileSync(jspf, 'utf8');

// The include must emit both scripts, in order, each with a version query.
let last = -1;
for (const s of SHARED) {
  const i = jspfSrc.indexOf('src="' + s + '?v=');
  assert.ok(i > last, 'boardScripts.jspf must load ' + s + '?v=... (after the previous script)');
  last = i;
}

let includers = 0;
for (const f of files) {
  if (f === jspf) continue;
  const src = fs.readFileSync(f, 'utf8');
  const rel = path.relative(HTTPDOCS, f);
  for (const s of SHARED) {
    assert.ok(!src.includes('src="' + s + '"'),
      rel + ' loads ' + s + ' without a cache-buster; include boardScripts.jspf instead');
  }
  if (/<%@\s*include\s+file="[^"]*boardScripts\.jspf"\s*%>/.test(src)) includers++;
}
assert.ok(includers >= 9, 'expected the board pages to include boardScripts.jspf, found ' + includers);

console.log('boardScripts.test.js: ok (' + includers + ' pages)');
