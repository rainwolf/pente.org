package org.pente.gameServer.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import org.pente.game.GridStateFactory;

/** AIPlayer backed by the C++ MMAI engine (CAi) running as a per-game sidecar
 *  process (mmai_player, built from dsg_src/mmai/). One-line-each-way stdin/
 *  stdout protocol; see MMAIProtocol and the design spec
 *  docs/superpowers/specs/2026-07-12-mmai-sidecar-ai-player-design.md.
 *
 *  Supported games (canonical ids): Pente=1, Keryo=3, Poof=11, Connect6=13,
 *  Boat=15, O-Pente=25, plus their even Speed twins (normalized in the shim).
 *
 *  MECHANISM ONLY: this class is deliberately NOT registered in
 *  dsg_src/conf/ai_config.xml. Future activation just adds a config entry with
 *  the binaryPath / dataDirectory options — XMLAIConfigurator and
 *  AIPlayerFactory.getAIPlayerThreaded pick it up with zero code changes.
 *  Note the dataDirectory must exist inside the runtime container (the Docker
 *  image only ships the binary at /usr/local/bin/mmai_player). The engine-data
 *  directory option is spelled "dataDirectory"; "configDirectory" is accepted
 *  as an alias for parity with MarksAIPlayer's vocabulary.
 *
 *  Engine replay limit: the C++ engine replays the full move history into
 *  fixed 362-entry arrays (engine/Ai.h), so at most 361 prior moves can be
 *  encoded. getMove() throws a descriptive RuntimeException rather than
 *  spawning an ERR/respawn loop if the history exceeds this hard limit.
 *
 *  Speed games: moveTimeoutSeconds (default 300) is a static per-config bound
 *  with no linkage to the table's game clock. For a supported Speed game it
 *  MUST be configured well below the clock (e.g. a few seconds), otherwise a
 *  wedged sidecar burns the AI's entire clock before the timeout fires.
 *
 *  Threading (spec §6.5): all calls arrive from the controller thread and the
 *  single "AIPlayerThread" inside ThreadedAIPlayer. stopThinking() is the one
 *  cross-thread call: it kills the sidecar, so the blocked reader observes
 *  EOF/death and getMove() surfaces InterruptedException via checkStopped().
 *  A RuntimeException escaping getMove() kills the AIPlayerThread (verified:
 *  ThreadedAIPlayer catches only InterruptedException) — the AI stops moving
 *  for that game, the same degradation as existing engines. Fail loudly,
 *  never fabricate a move. */
public class MMAIPlayer extends AbstractAIPlayer {

    private static final Logger log4j = Logger.getLogger(MMAIPlayer.class);

    private static final String[] REQUIRED_DATA_FILES =
        {"pente.tbl", "pente.scs", "opngbk.pen"};
    private static final long KILL_GRACE_MILLIS = 2000;

    /** Engine replay arrays are fixed at 362 (engine/Ai.h), so at most 361
     *  prior moves fit in a MOVE request. */
    private static final int MAX_REPLAY_MOVES = 361;

    /** Supported canonical game ids plus their even Speed twins (the shim
     *  normalizes the twin to the canonical rules). Anything else is silently
     *  remapped to plain Pente by the engine, so it is rejected up front. */
    private static final Set<Integer> SUPPORTED_GAMES = new HashSet<Integer>(
        Arrays.asList(
            GridStateFactory.PENTE, GridStateFactory.SPEED_PENTE,
            GridStateFactory.KERYO, GridStateFactory.SPEED_KERYO,
            GridStateFactory.POOF_PENTE, GridStateFactory.SPEED_POOF_PENTE,
            GridStateFactory.CONNECT6, GridStateFactory.SPEED_CONNECT6,
            GridStateFactory.BOAT_PENTE, GridStateFactory.SPEED_BOAT_PENTE,
            GridStateFactory.OPENTE, GridStateFactory.SPEED_OPENTE));

    /** Engine level bound, mirroring the shim's 1..8 check (main.cpp). */
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 8;

    /** Authoritative game state: every move, including our own echoed back
     *  by the controller (spec §6.2 addMove). */
    private final List<Integer> moves = new ArrayList<Integer>();
    private int game;
    private int level;
    private int seat; // stored, never sent: the engine derives side-to-move

    /** Connect6 second-stone cache (spec §6.3). */
    private final MMAIProtocol.PendingMove pendingMove =
        new MMAIProtocol.PendingMove();

    private String binaryPath;
    private String dataDirectory;
    private int moveTimeoutSeconds = 300;

    private Process process;
    private BufferedWriter toSidecar;
    private BufferedReader fromSidecar;
    private int sidecarRequests;
    private int spawns;

    public MMAIPlayer() {
    }

    public void setGame(int game) {
        this.game = game;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public void setOption(String optionName, String optionValue) {
        if ("binaryPath".equals(optionName)) {
            binaryPath = optionValue;
        } else if ("dataDirectory".equals(optionName)
                || "configDirectory".equals(optionName)) {
            // "configDirectory" is MarksAIPlayer's spelling for the same
            // engine-data directory; accept it as an alias (spec §6.2).
            dataDirectory = optionValue;
        } else if ("moveTimeoutSeconds".equals(optionName)) {
            moveTimeoutSeconds = Integer.parseInt(optionValue);
        }
        // unknown keys ignored (configurator tolerance, spec §6.2)
    }

    /** Fail fast (spec §6.2): binary must be executable, all three engine
     *  data files must exist. Then spawn the sidecar.
     *
     *  Idempotent: AIPlayerFactory.getAIPlayerThreaded calls init() twice on
     *  the activation path (once via getAIPlayer, once via the ThreadedAIPlayer
     *  wrapper), so a live sidecar is left untouched rather than leaked by a
     *  second respawn. */
    public synchronized void init() {
        validateConfig();
        if (process != null && process.isAlive()) {
            return;
        }
        try {
            spawn();
        } catch (IOException e) {
            throw new RuntimeException(
                "MMAIPlayer failed to spawn " + binaryPath, e);
        }
    }

    private void validateConfig() {
        if (!SUPPORTED_GAMES.contains(Integer.valueOf(game))) {
            throw new RuntimeException(
                "MMAIPlayer unsupported game id: " + game
                + " (supported: Pente/Keryo/Poof/Connect6/Boat/O-Pente and"
                + " their Speed twins)");
        }
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new RuntimeException(
                "MMAIPlayer level out of range: level=" + level
                + " (must be " + MIN_LEVEL + ".." + MAX_LEVEL + ")");
        }
        if (binaryPath == null || !new File(binaryPath).canExecute()) {
            throw new RuntimeException(
                "MMAIPlayer binaryPath missing or not executable: " + binaryPath);
        }
        if (dataDirectory == null) {
            throw new RuntimeException("MMAIPlayer dataDirectory not set");
        }
        for (String f : REQUIRED_DATA_FILES) {
            File df = new File(dataDirectory, f);
            if (!df.isFile()) {
                throw new RuntimeException(
                    "MMAIPlayer data file missing: " + df
                    + " (need pente.tbl, pente.scs, opngbk.pen)");
            }
        }
    }

    private void spawn() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(binaryPath, dataDirectory);
        // sidecar stderr straight to the server process log (spec §6.2)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = pb.start();
        toSidecar = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream()));
        fromSidecar = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        spawns++;
    }

    /** Called for EVERY table move, including this AI's own moves echoed back
     *  by the controller — always append (spec §6.2). */
    public void addMove(int move) {
        moves.add(move);
    }

    public void undoMove() {
        if (!moves.isEmpty()) {
            moves.remove(moves.size() - 1);
        }
        // cached Connect6 second stone is stale once the board rewinds
        pendingMove.clear();
        // no sidecar interaction: the engine replays the list on next MOVE
    }

    /** Spec §6.3. One stone per call; Connect6 second stone served from the
     *  cache without a sidecar round-trip. */
    public int getMove() throws InterruptedException {
        startThinking();
        int cached = pendingMove.consume();
        if (cached >= 0) {
            return cached;
        }
        // Hard engine limit: the C++ side replays the history into fixed
        // 362-entry arrays (engine/Ai.h), so more than 361 prior moves cannot
        // be encoded. Fail loudly here rather than sending an oversized MOVE
        // that the shim rejects with ERR, which would only trigger an
        // identically-failing respawn loop.
        if (moves.size() > MAX_REPLAY_MOVES) {
            throw new RuntimeException(
                "mmai engine replay limit (" + MAX_REPLAY_MOVES
                + " moves) exceeded: history has " + moves.size() + " moves");
        }
        boolean requestWritten = false;
        try {
            // Respawn is derived purely from process liveness (no separate
            // flag): a killed sidecar — from a stop, a failure, or the very
            // first getMove() — is lazily restarted here. Capture the process
            // reference before dereferencing so a concurrent stopThinking()
            // null-write cannot surface as a raw NullPointerException.
            Process current = process;
            if (current == null || !current.isAlive()) {
                killProcess();
                validateConfig();
                spawn();
            }
            // Capture the stream/process references once: stopThinking() on
            // the controller thread nulls the fields concurrently, and that
            // must surface as IOException -> fail() -> checkStopped() ->
            // InterruptedException, never as a raw NullPointerException.
            Process proc = process;
            BufferedWriter out = toSidecar;
            BufferedReader in = fromSidecar;
            if (proc == null || out == null || in == null) {
                throw new IOException("sidecar not running");
            }
            String request =
                MMAIProtocol.encodeMoveRequest(game, level, moves);
            out.write(request);
            out.newLine();
            out.flush();
            requestWritten = true;
            sidecarRequests++;
            String reply = readReplyLine(proc, in);
            int v = MMAIProtocol.parseOkReply(reply);
            if (v < 0) {
                // defensive: shim already maps engine -1 to ERR (spec §5.2)
                throw new MMAIProtocol.ProtocolException(
                    "engine no-move sentinel: " + v);
            }
            if (MMAIProtocol.isConnect6(game)) {
                return pendingMove.acceptPacked(v);
            }
            return v;
        } catch (IOException e) {
            return fail("sidecar I/O failure", e);
        } catch (MMAIProtocol.ProtocolException e) {
            return fail("sidecar protocol failure", e);
        } catch (InterruptedException e) {
            // A deliberate stop (from checkStopped()/sleep in readReplyLine)
            // landed after the MOVE request was written: the sidecar is
            // mid-search and its eventual reply is stale, so kill it now to
            // guarantee no in-flight request survives the stop. Respawn happens
            // lazily on the next getMove().
            if (requestWritten) {
                killProcess();
            }
            throw e;
        }
    }

    /** Failure path (spec §6.4): log context, kill the half-dead process (the
     *  next getMove() respawns lazily off liveness); a deliberate stop surfaces
     *  InterruptedException, anything else is a loud RuntimeException. Never
     *  fabricates a move. */
    private int fail(String what, Exception cause) throws InterruptedException {
        log4j.error("MMAIPlayer failure [" + what + "] game=" + game
            + " level=" + level + " seat=" + seat
            + " moveCount=" + moves.size(), cause);
        killProcess();
        checkStopped(); // throws InterruptedException if stopThinking() ran
        throw new RuntimeException("MMAIPlayer: " + what + ": "
            + cause.getMessage(), cause);
    }

    /** Poll-read one reply line, guarded by moveTimeoutSeconds, process
     *  liveness, and checkStopped(). Polling keeps the read interruptible;
     *  the sidecar always writes complete flushed lines, so once ready()
     *  is true readLine() returns promptly. Operates on references captured
     *  by getMove(): a concurrent stopThinking() closes the underlying
     *  stream, so ready()/readLine() throw IOException instead of the
     *  nulled fields causing a NullPointerException. */
    private String readReplyLine(Process proc, BufferedReader in)
            throws IOException, InterruptedException {
        long deadline =
            System.currentTimeMillis() + moveTimeoutSeconds * 1000L;
        while (!in.ready()) {
            checkStopped();
            if (!proc.isAlive() && !in.ready()) {
                throw new IOException("sidecar died (EOF before reply)");
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("sidecar reply timeout after "
                    + moveTimeoutSeconds + "s");
            }
            Thread.sleep(25);
        }
        String line = in.readLine();
        if (line == null) {
            throw new IOException("sidecar closed stdout");
        }
        return line;
    }

    /** Cross-thread stop (controller thread, spec §6.2/§6.5): interrupt the
     *  thinking thread, drop any cached Connect6 stone, and kill the sidecar
     *  — it is single-threaded and blocked inside CAi::getMove, so it cannot
     *  read an in-band stop line. Respawn happens lazily on next getMove(). */
    public synchronized void stopThinking() {
        super.stopThinking();
        pendingMove.clear();
        killProcess();
    }

    /** Post-game cleanup: polite QUIT, then grace, then force (spec §6.2). */
    public void destroy() {
        // Capture the fields into locals before dereferencing: a concurrent
        // stopThinking()/killProcess() may null them, and that must not surface
        // as a NullPointerException on the cleanup thread.
        Process proc = process;
        BufferedWriter out = toSidecar;
        try {
            if (proc != null && proc.isAlive() && out != null) {
                out.write("QUIT");
                out.newLine();
                out.flush();
            }
        } catch (IOException ignored) {
            // already dying; killProcess() below handles it
        }
        killProcess();
    }

    /** destroy() the process (no-op after QUIT already exited it), wait the
     *  grace period, escalate to destroyForcibly(), close streams.
     *  Synchronized so the process/stream null-writes are atomic against the
     *  concurrent stopThinking()/fail()/destroy() callers; the reader loop
     *  never calls this while blocked, preserving the no-monitor-while-blocking
     *  property. */
    private synchronized void killProcess() {
        Process proc = process;
        if (proc == null) {
            return;
        }
        try {
            proc.destroy();
            if (!proc.waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly();
            process = null;
        }
    }

    private void closeQuietly() {
        try {
            if (toSidecar != null) toSidecar.close();
        } catch (IOException ignored) {
        }
        try {
            if (fromSidecar != null) fromSidecar.close();
        } catch (IOException ignored) {
        }
        toSidecar = null;
        fromSidecar = null;
    }

    /** Test observability only: number of MOVE requests written to the
     *  sidecar since construction. Lets integration tests prove the Connect6
     *  second stone is served without a round-trip (spec §8.2). */
    public int getSidecarRequestCount() {
        return sidecarRequests;
    }

    /** Test observability only: number of times the sidecar process has been
     *  spawned since construction. Lets tests prove init() is idempotent — a
     *  second init() on an already-live process must not respawn (spec §6.5). */
    public int getSpawnCount() {
        return spawns;
    }
}
