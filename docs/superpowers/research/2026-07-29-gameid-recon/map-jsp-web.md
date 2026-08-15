# Game-ID Touchpoint Inventory — Tomcat JSP/JS web layer

Subsystem: `pente.org/dsg_src/httpdocs/gameServer/**` (JSP pages + hand-written client JS,
excluding the two React SPAs, which are covered separately in `map-react-android.md`).

Recon only, this session, via direct `grep`/`Read` against the source tree (not from any
cached/injected blob — see the injection note at the bottom of the master report).

---

## 0. The single source of truth for the client — `gameConstants.jspf`

`httpdocs/gameServer/gameConstants.jspf` is a JSP fragment, included at the top of every
board/board-list page, that does nothing but emit a JS `GAME` object by evaluating
`GridStateFactory.<CONST>` 48 times (16 families x 3: live/speed/TB):

```jsp
var GAME = { PENTE: <%= GridStateFactory.PENTE %>, SPEED_PENTE: <%= ... %>, TB_PENTE: <%= ... %>, ... };
```

This is the good pattern: every JS file that uses `GAME.X` is automatically correct if
`GridStateFactory` changes, with zero JS edits. The problem (classes B/C below) is that not
every JS file actually goes through `GAME.*` — several bypass it with raw literals despite
`GAME` being in scope on the same page.

## Class A — `js/boardCommon.js`: the one clean, symbolic, fail-loud dispatcher

| # | Location | What |
|---|---|---|
| 1 | `js/boardCommon.js:9-45` `getBoardColor(game)` | `switch(game)` over `GAME.<FAMILY>`/`GAME.SPEED_<FAMILY>`/`GAME.TB_<FAMILY>`, 3 cases per family, **all symbolic**, `default: throw new Error("getBoardColor: unknown game id " + game)` |
| 2 | `js/boardCommon.js:51-84` `replayMoves(...)` | Same shape, dispatches to `replay<Family>Game()` fns defined in `tb/gameScript.js`; **same explicit throw on default** |

This is the single most defensive touchpoint found across the *entire* investigation (all
layers): an unrecognized id fails loudly, in the browser console, at the exact call site,
rather than silently mis-rendering or crashing somewhere downstream. It is also the only
place backed by a dedicated regression test.

| # | Location | What |
|---|---|---|
| 3 | `js/boardCommon.test.js:18-28` | Node/`vm`-sandboxed test that **independently re-derives** the id formulas (`SPEED = base+1`, `TB = base+50`) from a hardcoded `base` map, to check `boardCommon.js` against them without evaluating the DOM-dependent `gameScript.js` body. If `GridStateFactory`'s offsets ever change, this test's own hardcoded formula must be updated by hand — it is not sourced from Java. |
| 4 | `js/boardCommon.test.js:82-85` | Explicit assertions that `getBoardColor(99999)` and `replayMoves()` with `game=99999` **throw** `/unknown game id/` — the only unknown-id test in the whole codebase across all layers |

## Class B — `tb/gameScript.js` (1647-line legacy TB applet JS): raw magic-number ids, despite `GAME.*` being in scope

| # | Location | What |
|---|---|---|
| 5 | `tb/gameScript.js:83` | `if (game === 69 \|\| game === 71 \|\| game === 73)` — raw literals for `TB_GO`/`TB_GO9`/`TB_GO13`; `GAME.TB_GO` etc. exist on the same page and are unused here |
| 6 | `tb/gameScript.js:577` | `if ((game < 69 \|\| game > 74) && game !== 31 && game !== 32 && game !== 81)` — range exclusion (roughly "not a TB Go/O-Pente family id") combined with 3 more raw Renju literals (31/32/81) |
| 7 | `tb/gameScript.js:600` | `else if (game === 69 \|\| game === 73 \|\| game === 31 \|\| game === 32 \|\| game === 81)` — **asymmetric**: includes `TB_GO`(69) and `TB_GO13`(73) but **not** `TB_GO9`(71), which is handled separately one branch later. Worth flagging to whoever owns this file — it is either an intentional per-board-size distinction (GO9 has different pass/board-fill semantics) or a latent omission; the raw-literal style makes it impossible to tell from the code alone |
| 8 | `tb/gameScript.js:642` | `else if (game === 71)` — `TB_GO9` handled alone, confirming #7 is deliberate, but still via raw literal |

## Class C — `tb/mobileGame.jsp` (1674 lines): the highest concentration of raw-literal ids in the codebase

Server-side (Java, inline JSP expressions) is symbolic throughout; **client-side JS blocks
in the same file are not**:

| # | Location | What |
|---|---|---|
| 9 | `tb/mobileGame.jsp:119-132` | Java-side: `game.getGame() == GridStateFactory.TB_GO \|\| TB_GO9 \|\| TB_GO13` (board-orientation flag), then per-size branches, then a separate `TB_RENJU` check — all symbolic |
| 10 | `tb/mobileGame.jsp:660` | `var isSwap2 = game === 77 \|\| game === 79;` — raw literals for `TB_SWAP2PENTE`(77)/`TB_SWAP2KERYO`(79); **TB-only**, no live/speed check anywhere in this file (mobileGame.jsp is TB-only by construction, so that's consistent, but the id is still unnamed) |
| 11 | `tb/mobileGame.jsp:670` | `var isRenju = game === <%= GridStateFactory.TB_RENJU %>;` — the **one** spot in this file's JS that goes back through the Java constant via a JSP expression instead of a raw literal; inconsistent style within the same file (contrast with #10 one line away) |
| 12 | `tb/mobileGame.jsp:226,236,733,740,764,778,844,910,954,976,980,992,954,1153,1205,1250,1262,1327,1329,1353` (20+ sites) | `game === 63` / `game !== 63` — raw literal for `TB_CONNECT6`, gating the double-stone move-count arithmetic (`moves.length % 2`, `Math.floor((until-1)/2) % 2`) throughout the move-history/replay rendering logic. This is the single most-repeated magic number found in this investigation across every layer |
| 13 | `tb/mobileGame.jsp:745,918,1213` | `game === 57 \|\| game === 67 \|\| isSwap2` — raw literals for `TB_DPENTE`(57)/`TB_DKERYO`(67), OR'd with the swap2 flag from #10, gating "negotiated opening, don't force move 0" logic — the client-side mirror of `GridStateFactory.firstMoveCanBeOffCenter()` (Java layer finding: this predicate is hand-copied in *three* different Java files already; this JSP is effectively a **fourth**, JS-side copy, with a *different* member list again — Java's `HttpGameServlet` version has GO, `MobileGameServlet` version doesn't, and this JSP has neither Go family id, only DPENTE/DKERYO/SWAP2) |

## Class D — mobile JSP pages: `> 50` heuristics and `getGameName`/`getDisplayName` NPE exposure

| # | Location | What |
|---|---|---|
| 14 | `mobile/koth.jsp:56,75` | `if (game > 50) { ... }` — raw `50`, not `TB_START` (which is `private` in Java anyway, so JSP could not reference it even if it wanted to) — the JSP-layer instance of the same `> 50` pattern already found ~14x in Java (`Tourney.isTurnBased`, `KothResponse`, `IndexResponse`, `CacheKOTHStorer`, ...) |
| 15 | `mobile/game.jsp:77` | `(tbGame.getGame() == GridStateFactory.TB_CONNECT6 ? 2 : 0)` — symbolic, Connect6 double-stone move-number offset, JSP-side twin of `GameResponse.java:286`'s identical fudge factor |
| 16 | `mobile/game.jsp:120` | `GridStateFactory.getGameName(tbGame.getGame())` in a `gameName=` field emitted straight into the page — **NPE on any unknown id** per the Java-layer finding (`getGameName` → `getGame(id).getName()`) |
| 17 | `mobile/game.jsp:140-143` | `tbGame.getGame() == TB_DPENTE \|\| TB_DKERYO \|\| TB_SWAP2PENTE \|\| TB_SWAP2KERYO` — symbolic, gates the draw-offer/negotiated-opening UI block. **Yet another** (5th) hand-copied variant of the "has a negotiated opening" predicate, this one TB-only and missing the Go family entirely — none of the ~5 copies of this predicate across Java+JSP agree on membership |
| 18 | `kothBox.jsp:57-71` | `for (i < GridStateFactory.TB_GAMES.length)` / `LIVE_GAMES.length` — **array-driven**, good pattern, auto-extends |
| 19 | `kothBox.jsp:88` | `(players.get(i).getGame() > 50 ? "TB-" : "") + GridStateFactory.getGameName(...)` — raw `50` again (JSP-layer copy of `IndexResponse.java:481`'s identical `"tb-"` prefix trick), plus the same NPE exposure as #16 |
| 20 | `viewLiveGames.jsp:26` | `GridStateFactory.getDisplayName(game)` — same NPE-on-unknown-id path (`displaygames[]` linear scan, returns `null`, which JSP then renders as the literal string `"null"`) |
| 21 | `viewLiveGames.jsp:63` | `if (game > 0)` — a validity/"is a game selected" guard, not id-scheme arithmetic; low relevance, listed for completeness |
| 22 | `tb/listedMobileGame.jsp:67-73` | `game.getGame() == TB_GO \|\| TB_GO9 \|\| TB_GO13 \|\| TB_RENJU` (board-orientation `p1Black` flag) then per-size branches — symbolic |
| 23 | `tb/listedMobileGame.jsp:215` | `if (game === 63 && moves.length > 1)` — raw literal, same Connect6 double-move-count pattern as class C |
| 24 | `tb/applet.jsp:45,91` | `game.getGame() == GridStateFactory.TB_DPENTE` — symbolic, gates a `dPenteState` param — client-side twin of the same conditional found in `viewLiveGame.jsp` earlier in this investigation |
| 25 | `admin/tb/games.jsp:40` | `GridStateFactory.getGameName(game.getGame())` — generic label, same NPE exposure as #16, but low-traffic (admin page) |

## Unknown-id behaviour — JSP/JS layer

Split, same shape as every other layer:

- **Fail-loud (the good pattern, but only 1 module)**: `js/boardCommon.js` throws
  `Error("... unknown game id " + game)` from both `getBoardColor` and `replayMoves`. This
  is the *only* place in the entire cross-platform investigation where an unknown id is
  caught with an explicit, tested, developer-facing error at the point of dispatch.
- **Silent `"null"` string rendered into the page**: every `getDisplayName`/`getGameName`
  call (#16, #20, #25) inherits the Java-layer `null`-return-on-miss behaviour; JSP does not
  guard against it, so the page literally shows the text "null" where a game name belongs.
- **Silent default styling/behaviour**: none of the raw-literal `===`/`==` OR-chains (#5-13,
  #17, #22-24) have an `else`/`default` that does anything special for an unrecognized id —
  they simply fail every check and the caller falls through to whatever the "normal" branch
  does (usually: no double-stone offset, forced first move, wrong board orientation). No
  exception, no log line, nothing visible to a developer.
- **`> 50` heuristics** (#14, #19) degrade the same way core Java's `Tourney.isTurnBased()`
  does: an id ≤ 50 that isn't actually a live id (e.g. one of the phantom 33-48 ids the Java
  report flags as already "occupied" by `allGames[]`) would be silently treated as live here.

## Cross-layer duplication this sweep confirms

The "has a negotiated/off-center opening" predicate (DPENTE/DKERYO/SWAP2/GO family) now has
**5 independently hand-maintained copies with 3 different membership lists**, once this JSP
layer is added to the Java-layer count from `map-java-core.md`/`map-java-db-wire.md`:
`GridStateFactory.firstMoveCanBeOffCenter` (Go+DPENTE+DKERYO), `HttpGameServlet.java:298-303`
(DPENTE/DKERYO/GO, no GO9/GO13), `MobileGameServlet.java:222-225` (same minus GO),
`ServerTable.java:1930-1938` (DPENTE/DKERYO/GO/GO9/GO13/SWAP2, no direct Go — the "auto
center-stone" gate), and now `tb/mobileGame.jsp:745,918,1213` (DPENTE/DKERYO/SWAP2 only, no
Go family at all). None of the 5 agree. This is the single clearest evidence in the whole
investigation that consolidating onto one `Game`/descriptor-level boolean flag (as
`map-java-core.md` already recommends) would fix a **pre-existing, already-live**
correctness bug, not just a future refactor risk.

## Touchpoint count

**25 distinct touchpoints** catalogued (#1-25 above), across `boardCommon.js`,
`boardCommon.test.js`, `gameScript.js`, `mobileGame.jsp`, and 7 other JSP pages.
