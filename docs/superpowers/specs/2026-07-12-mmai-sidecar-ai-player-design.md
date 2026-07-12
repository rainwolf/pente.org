# MMAI Sidecar AI Player — Design

- **Date:** 2026-07-12
- **Status:** Implemented (mechanism) — see docs/superpowers/plans/2026-07-12-mmai-sidecar-ai-player.md; activation (arena seating + ai_config.xml) is future work

## 1. Overview & Goals

pente.org's only server-side AI is `MarksAIPlayer`, a pure-Java hand-port of Mark Mammel's original C engine that supports only Pente and Keryo-Pente at 8 levels. Meanwhile the mobile clients (Android, iOS, react_mmai WASM) ship a far more capable C++ engine (`CAi` in `react_mmai/MMAIWASM/Ai.cpp`, ~2523 lines) covering six game variants.

This design brings that C++ engine to the server as a **sidecar process**: a small native binary (`mmai_player`) wrapping `CAi`, spoken to over a one-line-each-way stdin/stdout protocol by a new Java class `MMAIPlayer` that implements the existing `AIPlayer` interface.

**Goals:**

1. A working `MMAIPlayer` + `mmai_player` bridge for all six canonical game ids at launch: Pente (1), Keryo-Pente (3), Poof-Pente (11), Connect6 (13), Boat-Pente (15), O-Pente (25). Even ids are Speed twins and are normalized to `id - 1` before reaching `CAi`.
2. **Mechanism only.** The class is fully wired to the `AIPlayer` contract and testable end-to-end, but it is **never seated**: it is not registered in `dsg_src/conf/ai_config.xml`, and no controller code changes. Arena trigger conditions (when it sits, whom it plays) are a later, separate design.
3. Engine sources sync-copied from `react_mmai/MMAIWASM` (single upstream, Android-style), replacing the ancient engine copy currently under `dsg_src/mmai/`.
4. Zero changes to `AIPlayerFactory`, `ThreadedAIPlayer`, `ServerAIController`, `ServerAIMainRoomController`, `ServerAITableController`.

## 2. Non-Goals / Out of Scope

- **Arena seating / trigger conditions** — when the AI sits, whom it plays, guest-only rules. `ArenaServerTable` keeps its no-op `handleAddAI`/`handleInvite` (lines 192–196); re-enabling is future activation work (§10).
- **`ai_config.xml` registration** — deliberately deferred. `MMAIPlayer` is `setOption`-compatible with `XMLAIConfigurator` so registration later is a config-only change.
- **Ratings treatment** of AI games.
- **Renju / swap2 special decision events** — Renju is not among the six launch games.
- **Speed-twin time-control awareness** — the engine has no clock; level (1–8) is the only strength/time knob.
- **Removing `MarksAIPlayer`** or its `mm_ai` config — untouched. Old `dsg_src/mmai` artifacts (1191-line `Ai.cpp`, `.orig` files, `Ai.dll`, cygwin builds, old JNI `AiWrapper`) are removed only insofar as they are replaced by the new `engine/` dir.

## 3. Background

### The existing AIPlayer stack

- **Interface:** `pente.org/dsg_src/java/org/pente/gameServer/server/AIPlayer.java`. Contract: `init()`, `setGame(int)`, `setLevel(int)`, `setSeat(int seat /* 1|2 */)`, `setOption(String,String)`, `addMove(int move /* flat row-major 19x19, m = y*19 + x */)`, `stopThinking()`, `undoMove()`, `getMove() throws InterruptedException`, `destroy()`. A **new instance is created per game**; implementations need a 0-arg constructor (instantiated reflectively from config).
- **`AbstractAIPlayer`** provides `startThinking`/`stopThinking`/`checkStopped` thread-interrupt plumbing.
- **`AIPlayerFactory.getAIPlayerThreaded(AIData, ThreadedAIPlayerCallback)`** wraps the engine in `ThreadedAIPlayer`, which runs `getMove()` on its own `"AIPlayerThread"`, sleeps 5 s before moving (the undo window), and delivers the move via `callback.receiveMove(int)`.
- **Live integration chain** (unchanged by this work): `web.xml` `aiConfigFile` → `DSGContextListener` → `Server.initAiData` → `XMLAIConfigurator` parses `dsg_src/conf/ai_config.xml` → `ServerTable.handleAddAI` (~line 2889) → `ServerAIController.addAIPlayer` → `ServerAIMainRoomController` → `ServerAITableController` (implements `DSGEventListener` + `ThreadedAIPlayerCallback`): constructs the engine on `GAME_IN_PROGRESS` via `AIPlayerFactory.getAIPlayerThreaded` (line 137), feeds each `DSGMoveTableEvent` to `addMove` (line 100), handles undo (line 116), submits AI moves via `server.routeEventToTable` (lines 168–173), destroys on game end.

### Why MarksAIPlayer is pure Java, and why this one isn't

`MarksAIPlayer` was hand-ported to Java decades ago and has since diverged from the C lineage; porting the modern 2500-line C++ engine (with its opening books, variant tables, and ongoing upstream development in `react_mmai/MMAIWASM`) would create a second divergent copy. A sidecar keeps one canonical C++ source, byte-identically synced — exactly the model `pentelive-android` already uses for the same four files.

### Why a sidecar, not JNI

Android already wraps this engine via JNI (`pentelive-android/app/src/main/jni/AiWrapper.cpp`), but on the server a JNI crash takes down Tomcat. A per-game sidecar process isolates engine crashes (kill + respawn), needs no JNI build machinery in the server image, and `ThreadedAIPlayer` already runs `getMove()` on a dedicated thread, so a blocking read on the sidecar's stdout is fine.

## 4. Architecture

One sidecar process per `MMAIPlayer` instance, i.e. per game (the server already creates a new `AIPlayer` per game).

```
 Tomcat JVM                                        sidecar process (per game)
┌──────────────────────────────────────┐          ┌───────────────────────────┐
│ ServerAITableController              │          │ mmai_player               │
│   │ (ThreadedAIPlayerCallback)       │          │  ┌─────────────────────┐  │
│   ▼                                  │          │  │ main.cpp shim       │  │
│ ThreadedAIPlayer ("AIPlayerThread")  │  stdin   │  │  parse MOVE line    │  │
│   │ getMove() on own thread          │ ───────► │  │  (game,lvl) keyed   │  │
│   ▼                                  │  "MOVE…" │  │  persistent CAi     │  │
│ MMAIPlayer (extends AbstractAIPlayer)│          │  └────────┬────────────┘  │
│   - List<Integer> moves              │  stdout  │           ▼               │
│   - pendingMove (Connect6 2nd stone) │ ◄─────── │  CAi::getMove(moves,n)    │
│   - ProcessBuilder handle            │  "OK …"  │  (replays full list)      │
└──────────────────────────────────────┘          │  data: pente.tbl, .scs,   │
                                                  │        opngbk.pen (argv[1])│
                                                  └───────────────────────────┘
```

The Java side is the single source of truth for game state (the move list). The sidecar is stateless across turns in the semantic sense: `CAi::getMove(int* moves, int count)` (`Ai.h:182`) takes the **full move list each call**, self-resets and replays. This makes undo trivial and makes respawn-after-crash lossless.

## 5. Sidecar binary: `mmai_player`

New `main.cpp` (lives only in `pente.org/dsg_src/mmai/`, **not** synced from upstream) wrapping `CAi` from the synced engine sources.

### 5.1 Invocation

```
mmai_player <dataFilesDir>
```

`argv[1]` is the engine data directory (the `marksAI` conf dir or its container path), passed to the `CAi` constructor as `filesDir` (`CAi(int game, int lvl, bool openingBook, const char* filesDir = "files")`, `Ai.h:57`).

### 5.2 Protocol

Line-oriented, one line each way, UTF-8/ASCII, `\n`-terminated. stdout is unbuffered / line-flushed (`setvbuf(stdout, nullptr, _IOLBF, 0)` or explicit `fflush` after every reply).

| Request (stdin)                          | Reply (stdout)   | Semantics |
|------------------------------------------|------------------|-----------|
| `MOVE <game> <level> <n> <m1> … <mn>`    | `OK <move>`      | Compute the next move for `<game>` (canonical id; even Speed ids normalized to `id-1` by the shim before constructing `CAi`) at `<level>` (1–8), given the `n` moves played so far as flat 19×19 indices (`m = y*19 + x`, 0–360). Non-Connect6: `<move>` ∈ 0–360. Connect6 (13/14): `<move>` is the whole two-stone turn packed base-362 **exactly as `CAi::getMove` returns it**: `ret = m1*362 + m2`, with `m2 == 361` the single-stone sentinel (opening turn). |
| `MOVE …` (engine failure)                | `ERR <message>`  | `CAi::ok()` false after construction (data file load failure sets `loadErr`), or engine returned the cancelled/no-move sentinel `-1`. Process stays alive. |
| `QUIT`                                   | *(none)*         | Exit 0. |
| malformed / unknown line                 | `ERR <message>`  | Process stays alive. |
| EOF on stdin                             | *(none)*         | Exit 0 (orphan safety: parent death closes the pipe). |

Variant behavior (capture rules, Connect6 two-stone turns, O-Pente wrap, etc.) is entirely the engine's: `CAi::configFor` (`Ai.cpp:17–57`).

### 5.3 Lifecycle & CAi caching

Android precedent (`AiWrapper.cpp`): a **persistent `CAi` instance, rebuilt only when `(game, level)` changes** between `MOVE` requests. Since `getMove` replays the full move list every call anyway, this is purely a construction-cost optimization (data files parsed once). First `MOVE` constructs `CAi`; construction failure (`!ok()`) yields `ERR` and the shim retries construction on the next `MOVE`.

`requestStop()` exists in `CAi` for cancellation, but the sidecar does **not** use it: the shim is single-threaded and blocked inside `getMove` while thinking, so cancellation is implemented Java-side by killing the process (§6, `stopThinking`).

### 5.4 Data files

Loaded from `<dataFilesDir>/`: `pente.tbl`, `pente.scs`, `opngbk.pen`. All three already exist in-repo at `pente.org/dsg_src/conf/marksAI/` (the fourth file there, `opngbk.kpn`, is `MarksAIPlayer`-only and is not read by `CAi`).

## 6. Java: `MMAIPlayer`

`org.pente.gameServer.server.MMAIPlayer extends AbstractAIPlayer` — same package as `AIPlayer`/`MarksAIPlayer`, 0-arg constructor, configured via `setOption`, so a future `ai_config.xml` entry needs no code change.

### 6.1 State

- `List<Integer> moves` — the authoritative move list.
- `int game, level, seat` — from setters.
- `int pendingMove = -1` — cached Connect6 second stone (§6.3).
- `Process process` + buffered writer/reader on its stdin/stdout; `boolean respawnNeeded`.
- Options: `binaryPath` (path to `mmai_player`, required), `dataDirectory` (engine data dir, required), `moveTimeoutSeconds` (optional, default 300 — generous, sized for top-level think time).

### 6.2 Per-method behavior (the `AIPlayer` contract)

| Method | Behavior |
|---|---|
| `setOption(key, value)` | Store `binaryPath` / `dataDirectory` / `moveTimeoutSeconds`; ignore unknown keys (matches configurator tolerance). |
| `setGame(int)` / `setLevel(int)` / `setSeat(int)` | Store. Game id sent on the wire is the canonical id as given; even→odd normalization happens in the shim (§5.2). Level is a straight 1–8 pass-through to `CAi`. Seat is stored but never sent on the wire: `CAi::getMove` derives the side to move from the replayed move list, exactly as the Android wrapper's stateless `move(ptr, moves[], game, level)` does — it passes no seat either. |
| `init()` | **Fail fast:** verify `binaryPath` is an executable file and `dataDirectory` contains `pente.tbl`, `pente.scs`, `opngbk.pen`; throw otherwise. Spawn via `ProcessBuilder(binaryPath, dataDirectory)` with stderr redirected to the server log (dedicated consumer thread or `Redirect.INHERIT`). |
| `addMove(int m)` | Append `m` to `moves`. **Always** — including the AI's own moves echoed back by the controller (§6.3). |
| `undoMove()` | Remove last element of `moves`; set `pendingMove = -1` (a cached second stone is stale once the board rewinds). No sidecar interaction — the engine replays from the list on the next `MOVE`. |
| `getMove()` | §6.3. |
| `stopThinking()` | Call `super.stopThinking()` (interrupt plumbing), then `process.destroy()` the sidecar (it is blocked inside `CAi::getMove` and cannot read a stop line); set `respawnNeeded = true`; set `pendingMove = -1`. A stop aborts the in-flight Connect6 turn: the first stone may never have been delivered (stop during `ThreadedAIPlayer`'s 5 s undo window), so a cached second stone must not survive a stop — the engine recomputes the whole turn from the move list on the next `MOVE` (§6.3). The blocked reader in `getMove` sees EOF; because the stop was requested, this surfaces as the interruption/stop path, not an engine failure. Next `getMove()` respawns first. |
| `destroy()` | Best-effort write `QUIT` + flush; wait a short grace (~2 s) for exit; then `process.destroyForcibly()`. Close streams. |

### 6.3 `getMove()` and the Connect6 two-stone bridge

This is the subtlest part. The `AIPlayer` contract returns **one stone per `getMove()`**, but the engine returns Connect6's whole two-stone turn in one packed value (decode precedent: `pentelive-android` `Ai.java:20–22` and `MMAIBoardView.processAImove`), and the controller (`ServerAITableController`, line 100) echoes **every** table move — including the AI's own submitted moves — back into `addMove`. The bridge exploits that echo instead of fighting it:

```
getMove():
  1. if pendingMove != -1:            # second stone of a Connect6 turn
       m = pendingMove; pendingMove = -1
       return m                        # NO sidecar round-trip
  2. if respawnNeeded or process dead: respawn (as in init())
  3. write "MOVE <game> <level> <moves.size()> <moves…>\n", flush
  4. block-read one reply line, guarded by moveTimeoutSeconds
  5. "OK <v>":
       non-Connect6:      return v
       Connect6 (13/14):  m1 = v / 362; m2 = v % 362
                          if m2 != 361: pendingMove = m2   # sentinel 361 = single-stone turn
                          return m1
  6. "ERR …" / EOF / timeout / v == -1:  engine failure (§6.4)
     # v == -1 is defensive only: the shim already maps engine -1 to ERR (§5.2)
```

**Why the echo interaction is consistent:** after step 5 returns `m1`, the controller routes it to the table and the resulting `DSGMoveTableEvent` comes back through `addMove(m1)`, appending `m1` to `moves` — which is exactly what the engine's full-list replay expects. The next `getMove()` returns the cached `m2` **without consulting the engine**, so the fact that `moves` at that instant contains `m1` (a half-finished turn from the engine's perspective) is never observed by the sidecar. `addMove(m2)` then completes the turn in the list. Invariants:

- `addMove` never skips or de-duplicates anything: every echoed stone, ours or the opponent's, is appended exactly once.
- `pendingMove` is consumed before any sidecar I/O, and cleared by both `undoMove()` and `stopThinking()`.
- The sidecar only ever sees `moves` at turn boundaries (steps 3–4 run only when `pendingMove == -1`). This holds across stops too: in the existing controller flow a stop is always followed by undo(s) (`ServerAITableController` line 116) or game-end `destroy()`, so `moves` is back at a turn boundary before any subsequent `MOVE`.

### 6.4 Failure semantics

`ERR` reply, EOF (process died un-stopped), read timeout, or an `OK -1` (engine's cancelled/no-move sentinel — defensive only: the shim maps engine `-1` to `ERR` (§5.2) and we never send an in-band stop): log the protocol context (game, level, move count, raw reply) and **throw `RuntimeException`** from `getMove()`, after marking `respawnNeeded = true` and force-killing any half-dead process. Distinguish the deliberate-stop path: if `stopThinking()` was invoked (flag from `AbstractAIPlayer.checkStopped` plumbing), surface `InterruptedException` per the contract instead.

*Implementation note: verify exactly how `ThreadedAIPlayer` handles a `RuntimeException` escaping `getMove()` on the `AIPlayerThread` (swallow-and-log vs. no `receiveMove` callback vs. thread death), and match the throw type/logging so the game degrades the same way existing engine failures do. This is a verification task for the implementation plan, not an open design question — the design position is: fail loudly, never fabricate a move.*

### 6.5 Threading assumptions

All `AIPlayer` calls arrive from the controller thread and the single `"AIPlayerThread"` inside `ThreadedAIPlayer`; `getMove()` blocking on the sidecar's stdout is safe because it never runs on a request thread. No internal synchronization beyond what `MarksAIPlayer` assumes today; document this in the class javadoc. `stopThinking()` is the one cross-thread call (controller thread → kills process while `AIPlayerThread` blocks in read); `Process.destroy()` + EOF-on-read is the safe cross-thread signal.

## 7. Source sync & build

### 7.1 Engine source home & sync

- Synced files: `Ai.cpp`, `Ai.h`, `CPoint.cpp`, `CPoint.h` → `pente.org/dsg_src/mmai/engine/`, **replacing** the ancient engine copy at `dsg_src/mmai/` (1191-line `Ai.cpp`, `.orig` files, `Ai.dll`, cygwin builds, old JNI `AiWrapper` — historic, superseded).
- Upstream: `react_mmai/MMAIWASM/` — the single canonical source, exactly as `pentelive-android` already consumes it.
- Sync script `dsg_src/mmai/sync_from_upstream.sh`: copies the 4 files and stamps each with a 4-line header:

  ```
  // Synced from react_mmai/MMAIWASM at commit <hash>.
  // DO NOT EDIT HERE — edit upstream & re-sync
  // via dsg_src/mmai/sync_from_upstream.sh.
  //
  ```

  where `<hash>` is the upstream repo's current HEAD at sync time (precedent: `pentelive-android` does exactly this for the same files).
- `main.cpp` (the sidecar shim) lives only in `pente.org/dsg_src/mmai/` and is **not** synced.

### 7.2 Build

- **Dockerfile** (`pente.org/Dockerfile`): a `g++` compile step — three trivial translation units:

  ```dockerfile
  RUN g++ -O2 -std=c++11 \
      dsg_src/mmai/engine/Ai.cpp dsg_src/mmai/engine/CPoint.cpp dsg_src/mmai/main.cpp \
      -o /usr/local/bin/mmai_player
  ```

  (plus `apt-get install g++` in the build stage if not already present).
- **Local dev/test build:** `dsg_src/mmai/build.sh` producing `dsg_src/mmai/mmai_player` with the same flags, used by the JUnit integration tests and manual poking.

## 8. Testing

### 8.1 Pure-unit tests (no process)

Protocol encode/decode: `MOVE` line formatting from a move list; `OK`/`ERR` parsing; Connect6 base-362 unpack including the `m2 == 361` single-stone sentinel; `pendingMove` state machine (return-then-clear, cleared by `undoMove` and by `stopThinking`).

### 8.2 JUnit integration tests (spawn the real binary via `build.sh` output)

- For **each of the six game ids** (1, 3, 11, 13, 15, 25): play scripted opening move lists through `MMAIPlayer`, assert the reply is a legal empty intersection in 0–360.
- **Connect6 packed decode:** first turn returns a single stone (`m2 == 361` sentinel path); a later turn returns two stones via consecutive `getMove()` calls with the echo `addMove` in between, second call verified to not touch the sidecar.
- **Process-death recovery:** kill the sidecar externally; next `getMove()` respawns and answers correctly (full-list replay proves statelessness).
- **Malformed-reply / timeout:** point at a stub binary emitting garbage / nothing; assert the specified failure semantics (§6.4).
- **Fail-fast `init()`:** missing binary, missing data files.

### 8.3 Manual smoke path

Local flow only (never `sync_gameServer.sh` — that is the PROD deploy): compile server classes, bind-mount the compiled classes into the `penteorg-pente.org-1` container, restart it, and exercise `MMAIPlayer` via a temporary test hook (it cannot be seated through config, by design). Same flow already documented for other server-class smoke tests.

## 9. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Sidecar orphaned when Tomcat dies uncleanly | Shim exits 0 on stdin EOF (§5.2); `destroy()` sends `QUIT` then `destroyForcibly()`. |
| Engine crash mid-think | Process isolation is the point: EOF → `RuntimeException`, `respawnNeeded`, lossless respawn from the Java-side move list. Tomcat unaffected (vs. JNI). |
| Protocol desync (partial line, double reply) | Strict one-request-one-reply; any parse anomaly is treated as failure → kill + respawn, which resets the channel completely. |
| Read blocked forever on a hung engine | `moveTimeoutSeconds` guard (default 300 s) → failure path. |
| Per-game process resource cost | One small process per active AI game; data files loaded once per process; AI games are few. Acceptable; revisit only if arena activation multiplies counts. |
| Stale Connect6 `pendingMove` after undo or stop | Cleared in both `undoMove()` and `stopThinking()` (§6.2); engine never sees mid-turn state (§6.3 invariants). |
| Engine sources drifting from upstream | DO-NOT-EDIT header + commit-stamped sync script; edits happen only in `react_mmai/MMAIWASM`. |
| Data file mismatch / missing at runtime | `init()` fail-fast check; `CAi::ok()`/`loadErr` surfaced as `ERR` with message. |

## 10. Future work (activation pointers)

- **Arena seating:** `ArenaServerTable` overrides `handleAddAI`/`handleInvite` as no-ops (lines 192–196) — that is the single reason an AI cannot be seated in the arena today. The activation design will define trigger conditions (when the AI sits, whom it plays) and lift those no-ops accordingly.
- **`ai_config.xml` registration:** add an entry under `dsg_src/conf/ai_config.xml` for `org.pente.gameServer.server.MMAIPlayer` with `binaryPath` / `dataDirectory` options; `XMLAIConfigurator` and `AIPlayerFactory.getAIPlayerThreaded` then pick it up with zero code changes — that compatibility is a hard requirement of this design (§6).
- Level/strength presentation (naming the 1–8 levels per variant), and any per-variant availability rules, belong to that activation design.
