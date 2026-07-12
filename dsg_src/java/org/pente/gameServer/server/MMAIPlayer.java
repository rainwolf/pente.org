package org.pente.gameServer.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

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
 *  image only ships the binary at /usr/local/bin/mmai_player).
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
    private boolean respawnNeeded;
    private int sidecarRequests;

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
        } else if ("dataDirectory".equals(optionName)) {
            dataDirectory = optionValue;
        } else if ("moveTimeoutSeconds".equals(optionName)) {
            moveTimeoutSeconds = Integer.parseInt(optionValue);
        }
        // unknown keys ignored (configurator tolerance, spec §6.2)
    }

    /** Fail fast (spec §6.2): binary must be executable, all three engine
     *  data files must exist. Then spawn the sidecar. */
    public void init() {
        validateConfig();
        try {
            spawn();
        } catch (IOException e) {
            throw new RuntimeException(
                "MMAIPlayer failed to spawn " + binaryPath, e);
        }
    }

    private void validateConfig() {
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
        respawnNeeded = false;
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
        if (pendingMove.hasPending()) {
            return pendingMove.consume();
        }
        try {
            if (respawnNeeded || process == null || !process.isAlive()) {
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
        }
    }

    /** Failure path (spec §6.4): log context, mark for respawn, kill the
     *  half-dead process; deliberate stop surfaces InterruptedException,
     *  anything else is a loud RuntimeException. Never fabricates a move. */
    private int fail(String what, Exception cause) throws InterruptedException {
        log4j.error("MMAIPlayer failure [" + what + "] game=" + game
            + " level=" + level + " seat=" + seat
            + " moveCount=" + moves.size(), cause);
        respawnNeeded = true;
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
        respawnNeeded = true;
        killProcess();
    }

    /** Post-game cleanup: polite QUIT, then grace, then force (spec §6.2). */
    public void destroy() {
        try {
            if (process != null && process.isAlive() && toSidecar != null) {
                toSidecar.write("QUIT");
                toSidecar.newLine();
                toSidecar.flush();
            }
        } catch (IOException ignored) {
            // already dying; killProcess() below handles it
        }
        killProcess();
    }

    /** destroy() the process (no-op after QUIT already exited it), wait the
     *  grace period, escalate to destroyForcibly(), close streams. */
    private void killProcess() {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            if (!process.waitFor(KILL_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
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
}
