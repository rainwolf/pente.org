package org.pente.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renju rules + Taraguchi-10 opening, as a decorator over a SimpleGomokuState.
 * Black (color 1) wins only on exactly five; white (color 2) wins on five or more.
 * Black playing a forbidden point (overline / double-four / double-three) is a legal
 * but immediately losing move: the game ends at once with white the winner.
 */
public class RenjuState extends GridStateDecorator implements GomokuState, HashCalculator {

    private final RenjuForbiddenPointFinder finder;

    private final List<Integer> offeredFifth = new ArrayList<Integer>();
    private Integer selectedFifth = null;

    public RenjuState() {
        this(15, 15);
    }

    public RenjuState(GridState gridState) {
        super(gridState);
        // Mirror the (int,int) ctor setup so a wrapped SimpleGomokuState behaves
        // identically: report overlines (white wins on 6+) and skip its own hashing.
        if (gridState instanceof SimpleGomokuState) {
            ((SimpleGomokuState) gridState).allowOverlines(true);
            ((SimpleGomokuState) gridState).setDoHashes(false);
        }
        finder = new RenjuForbiddenPointFinder(gridState.getGridSizeX());
        refreshFinder();
    }

    public RenjuState(int boardSizeX, int boardSizeY) {
        super(new SimpleGomokuState(boardSizeX, boardSizeY));
        ((SimpleGomokuState) gridState).allowOverlines(true);
        ((SimpleGomokuState) gridState).setDoHashes(false);
        finder = new RenjuForbiddenPointFinder(boardSizeX);
        refreshFinder();
    }

    // GomokuState interface (overline policy is Renju-specific; report allowed for white)
    public void allowOverlines(boolean allow) {
        ((SimpleGomokuState) gridState).allowOverlines(allow);
    }

    public boolean areOverlinesAllowed() {
        return ((SimpleGomokuState) gridState).areOverlinesAllowed();
    }

    /**
     * Rebuild a RenjuState from a move list (used by GridStateFactory to
     * reconstruct stored games). Must return a RenjuState — without this the
     * decorator would delegate to the wrapped gomoku and silently drop Renju
     * rules. Swap/branch decisions are not part of the move list (same
     * reconstruction limitation as swap2/dPente); the board and move order are
     * reproduced exactly.
     */
    public GridState getInstance(MoveData moveData) {
        RenjuState state = new RenjuState(
                gridState.getGridSizeX(), gridState.getGridSizeY());
        for (int i = 0; i < moveData.getNumMoves(); i++) {
            state.addMove(moveData.getMove(i));
        }
        return state;
    }

    /**
     * Rebuild a RenjuState from a persisted move list + opening state.
     * Replays moves AND re-applies swap/branch/offer/select decisions in
     * protocol order, stopping wherever the persisted state is still pending,
     * so the result reports the correct pending decision and current player.
     *
     * @param moves   the played moves (opening moves, then the selected 5th, then the rest)
     * @param renjuSwapsPacked the base-3 packed opening word (see RenjuOpeningState)
     * @param offers  the 10 offered 5th moves (Branch B), or null
     */
    public static RenjuState reconstruct(MoveData moves, int renjuSwapsPacked, int[] offers) {
        RenjuState s = new RenjuState(15, 15);
        RenjuOpeningState st = RenjuOpeningState.decode(renjuSwapsPacked);
        int n = moves.getNumMoves();
        int idx = 0;

        // moves 1-4 each followed by a swap window
        int[] swaps = {st.swap1, st.swap2, st.swap3, st.swap4};
        for (int k = 0; k < 4; k++) {
            if (idx >= n) return s;
            s.addMove(moves.getMove(idx++));
            if (swaps[k] == RenjuOpeningState.PENDING) return s;
            s.renjuSwapDecisionMade(swaps[k] == RenjuOpeningState.YES);
        }

        // branch choice
        if (st.branch == RenjuOpeningState.PENDING) return s;
        boolean tenOffer = st.branch == RenjuOpeningState.YES;
        s.chooseBranch(tenOffer);

        if (!tenOffer) {
            // Branch A: move 5, its swap window, move 6, then the rest
            if (idx >= n) return s;
            s.addMove(moves.getMove(idx++));            // move 5
            if (st.swap5 == RenjuOpeningState.PENDING) return s;
            s.renjuSwapDecisionMade(st.swap5 == RenjuOpeningState.YES);
            while (idx < n) {
                s.addMove(moves.getMove(idx++));
            }
            return s;
        }

        // Branch B: re-offer the candidates, then the selection commits move 5
        if (offers != null) {
            for (int off : offers) {
                s.offerFifthMove(off);
            }
        }
        if (offers != null && offers.length == 10 && idx < n) {
            s.selectFifthMove(moves.getMove(idx++)); // selectFifthMove addMoves the chosen move
            while (idx < n) {
                s.addMove(moves.getMove(idx++));
            }
        }
        return s;
    }

    /**
     * Sync the finder's board from the wrapped grid (color 1 -> 'X', 2 -> 'O', 0 -> '.').
     */
    private void refreshFinder() {
        finder.clear();
        int sx = gridState.getGridSizeX();
        int sy = gridState.getGridSizeY();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                int p = gridState.getPosition(x, y);
                if (p == 1) finder.setStone(x, y, RenjuForbiddenPointFinder.BLACK);
                else if (p == 2) finder.setStone(x, y, RenjuForbiddenPointFinder.WHITE);
            }
        }
    }

    public void addMove(int move) {
        gridState.addMove(move);
        refreshFinder();
        int n = gridState.getNumMoves();
        if (!openingComplete) {
            if (n >= 1 && n <= 4) {
                awaitingSwap = true;
            } else if (n == 5 && !tenOffer) {
                awaitingSwap = true; // Branch A: white may swap before move 6
            } else if (n == 6) {
                openingComplete = true;
            }
        }
        updateHash(this);
    }

    public void undoMove() {
        gridState.undoMove();
        refreshFinder();
        updateHash(this);
    }

    public void clear() {
        super.clear();
        openingComplete = false;
        awaitingSwap = false;
        branchChosen = false;
        tenOffer = false;
        Arrays.fill(swapDecision, false);
        Arrays.fill(swapResolved, false);
        offeredFifth.clear();
        selectedFifth = null;
        refreshFinder();
    }

    public boolean isGameOver() {
        int n = getNumMoves();
        if (n == 0) return false;

        int lastMove = getMove(n - 1);
        if (outOfBounds(lastMove)) {
            return drawCheck(n);
        }
        Coord c = convertMove(lastMove);
        int lastColor = getColor(n - 1); // 1 = black, 2 = white

        // Re-evaluate the last move as if just placed: temporarily clear it in the finder.
        // isForbidden/isFive only inspect EMPTY points (they set/clear the stone
        // themselves), so the stone must be cleared for the duration of this window.
        finder.setStone(c.x, c.y, RenjuForbiddenPointFinder.EMPTY);
        boolean forbidden = (lastColor == 1) && finder.isForbidden(c.x, c.y);
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        // restore
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);

        if (forbidden) return true; // black played a forbidden point -> game over (white wins)
        if (five) return true;
        return drawCheck(n);
    }

    private boolean drawCheck(int n) {
        return n == gridState.getGridSizeX() * gridState.getGridSizeY();
    }

    public int getWinner() {
        if (!isGameOver()) return 0;
        int n = getNumMoves();
        int lastMove = getMove(n - 1);
        if (outOfBounds(lastMove)) return 0; // draw
        Coord c = convertMove(lastMove);
        int lastColor = getColor(n - 1);
        finder.setStone(c.x, c.y, RenjuForbiddenPointFinder.EMPTY);
        boolean forbidden = (lastColor == 1) && finder.isForbidden(c.x, c.y);
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);
        if (forbidden) return 2; // black played a forbidden point -> white wins
        return five ? lastColor : 0;
    }

    private boolean outOfBounds(int move) {
        Coord c = convertMove(move);
        return c.x < 0 || c.x >= gridState.getGridSizeX()
                || c.y < 0 || c.y >= gridState.getGridSizeY();
    }

    /** Public bounds check for callers validating a candidate point. */
    public boolean isOutOfBounds(int move) {
        return outOfBounds(move);
    }

    /**
     * Black forbidden points on the current board (for UI).
     */
    public List<Coord> getForbiddenPoints() {
        return finder.findForbiddenPoints();
    }

    // --- opening protocol state (fully wired in Task 4) ---
    private boolean openingComplete = false;

    public boolean isOpeningComplete() {
        return openingComplete;
    }

    /**
     * Test/seam hook: mark the opening done so post-opening rules apply.
     */
    public void forceOpeningComplete() {
        openingComplete = true;
    }

    public boolean isAwaitingBranchChoice() {
        return !openingComplete && !awaitingSwap
                && gridState.getNumMoves() == 4 && !branchChosen;
    }

    /**
     * Black picks the post-move-4 path. false = Branch A (9x9 + swap), true = Branch B (10 offers).
     */
    public void chooseBranch(boolean tenOffer) {
        if (!isAwaitingBranchChoice()) {
            throw new IllegalStateException("branch choice not pending");
        }
        this.tenOffer = tenOffer;
        this.branchChosen = true;
    }

    public boolean isAwaitingFifthOffers() {
        return !openingComplete && branchChosen && tenOffer
                && gridState.getNumMoves() == 4 && offeredFifth.size() < 10;
    }

    public boolean isAwaitingFifthSelection() {
        return !openingComplete && branchChosen && tenOffer
                && gridState.getNumMoves() == 4 && offeredFifth.size() == 10
                && selectedFifth == null;
    }

    public List<Integer> getOfferedFifthMoves() {
        return new ArrayList<Integer>(offeredFifth);
    }

    /**
     * Encode the engine's resolved opening decisions into the RenjuOpeningState
     * packed word (base-3). Unresolved windows encode as PENDING, so this is
     * correct mid-opening (used live) and at game-over (used for archival).
     */
    public int getRenjuSwapsPacked() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = swapDigit(1);
        st.swap2 = swapDigit(2);
        st.swap3 = swapDigit(3);
        st.swap4 = swapDigit(4);
        st.branch = branchChosen
                ? (tenOffer ? RenjuOpeningState.YES : RenjuOpeningState.NO)
                : RenjuOpeningState.PENDING;
        st.swap5 = swapDigit(5);
        return st.encode();
    }

    private int swapDigit(int window) {
        if (!swapResolved[window]) {
            return RenjuOpeningState.PENDING;
        }
        return swapDecision[window] ? RenjuOpeningState.YES : RenjuOpeningState.NO;
    }

    public void offerFifthMove(int move) {
        if (!isAwaitingFifthOffers()) {
            throw new IllegalStateException("not accepting 5th-move offers");
        }
        if (outOfBounds(move) || getPosition(move) != 0) {
            throw new IllegalArgumentException("offered move not an empty board point");
        }
        if (isSymmetricDuplicate(move)) {
            throw new IllegalArgumentException("offered move is a symmetric duplicate");
        }
        offeredFifth.add(move);
    }

    /**
     * Atomically offer all ten Branch-B 5th-move candidates. Each is validated by
     * the SAME rules as offerFifthMove (empty board point, in bounds, not a
     * duplicate, not a symmetric duplicate). If ANY is rejected, NO offer is
     * committed: the offeredFifth list is restored to its prior contents and the
     * triggering exception is rethrown. Reuses offerFifthMove - no new rules.
     */
    public void offerFifthMoves(int[] moves) {
        if (!isAwaitingFifthOffers()) {
            throw new IllegalStateException("not accepting 5th-move offers");
        }
        if (moves == null || moves.length != 10) {
            throw new IllegalArgumentException("Branch B requires exactly ten 5th-move offers");
        }
        List<Integer> snapshot = new ArrayList<Integer>(offeredFifth);
        try {
            for (int m : moves) {
                offerFifthMove(m);
            }
        } catch (RuntimeException e) {
            offeredFifth.clear();
            offeredFifth.addAll(snapshot);
            throw e;
        }
    }

    /**
     * Pure pre-check for the ten Branch-B 5th-move candidates: returns true iff
     * offerFifthMoves(moves) would accept all of them. Applies the SAME rules
     * (exactly ten entries, each in bounds, on an empty point, and not a
     * symmetric duplicate of the offers accepted before it), WITHOUT mutating any
     * state, so a caller can validate before committing the swap/branch flags.
     */
    public boolean wouldAcceptFifthOffers(int[] moves) {
        if (moves == null || moves.length != 10) {
            return false;
        }
        List<Integer> stabilizer = positionStabilizer();
        List<Integer> accepted = new ArrayList<Integer>(offeredFifth);
        for (int move : moves) {
            if (outOfBounds(move) || getPosition(move) != 0) {
                return false;
            }
            boolean duplicate = false;
            for (int rot : stabilizer) {
                int image = rotateMove(move, rot);
                for (Integer existing : accepted) {
                    if (existing.intValue() == image) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    break;
                }
            }
            if (duplicate) {
                return false;
            }
            accepted.add(move);
        }
        return true;
    }

    public void selectFifthMove(int move) {
        if (!isAwaitingFifthSelection()) {
            throw new IllegalStateException("not awaiting 5th-move selection");
        }
        if (!offeredFifth.contains(move)) {
            throw new IllegalArgumentException("selected move was not offered");
        }
        selectedFifth = move;
        addMove(move); // commit as move 5 (color parity -> black); discards the other 9
    }

    /**
     * D4 symmetry dedup: a candidate is a duplicate if some board symmetry that maps the
     * current placed-stone position onto itself also maps the candidate onto an existing offer.
     */
    private boolean isSymmetricDuplicate(int move) {
        for (int rot : positionStabilizer()) {
            int image = rotateMove(move, rot);
            for (Integer existing : offeredFifth) {
                if (existing.intValue() == image) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rotation indices (0..7) whose D4 operation leaves the current stones invariant.
     */
    private List<Integer> positionStabilizer() {
        List<Integer> stab = new ArrayList<Integer>();
        int n = gridState.getNumMoves();
        for (int rot = 0; rot < 8; rot++) {
            boolean invariant = true;
            for (int m = 0; m < n && invariant; m++) {
                int src = getMove(m);
                if (outOfBounds(src)) continue;
                int dst = rotateMove(src, rot);
                if (getPosition(dst) != getPosition(src)) {
                    invariant = false;
                }
            }
            if (invariant) stab.add(rot);
        }
        return stab;
    }

    // branch flags (Branch B / offers wired in Tasks 6-7; default Branch A here)
    private boolean branchChosen = false;
    private boolean tenOffer = false;

    private boolean awaitingSwap = false;
    // swap decisions indexed by the stone count after which the window opened (1..5)
    private final boolean[] swapDecision = new boolean[6];
    // parallels swapDecision: true once the window's decision has been recorded
    // (so a false decision is distinguishable from "not yet decided").
    private final boolean[] swapResolved = new boolean[6];

    public boolean isAwaitingSwapDecision() {
        return awaitingSwap;
    }

    public boolean didSwapAt(int afterStone) {
        return swapDecision[afterStone];
    }

    public void renjuSwapDecisionMade(boolean swap) {
        if (!awaitingSwap) {
            throw new IllegalStateException("no swap decision pending");
        }
        swapDecision[gridState.getNumMoves()] = swap;
        swapResolved[gridState.getNumMoves()] = true;
        awaitingSwap = false;
    }

    /**
     * The CURRENT decision point of the Taraguchi-10 opening, computed from the
     * existing predicates. This is the server truth a (re)joining client must
     * reconstruct from only (numMoves, rejoin-signal); see {@link RenjuRejoin}.
     */
    public RenjuOpeningPhase getOpeningPhase() {
        if (openingComplete) return RenjuOpeningPhase.COMPLETE;
        if (isAwaitingSwapDecision()) return RenjuOpeningPhase.SWAP;
        if (isAwaitingBranchChoice()) return RenjuOpeningPhase.BRANCH;
        if (isAwaitingFifthSelection()) return RenjuOpeningPhase.SELECTION;
        return RenjuOpeningPhase.MOVE;
    }

    /**
     * The resolved swap decision for the window that opened after {@code window}
     * stones (1..5). Only meaningful once that window has been resolved; a
     * not-yet-resolved or never-existent window reads false. Used by the rejoin
     * encoder to populate the silent swap-seats signal.
     *
     * This is the CURRENT (per-window) decision, NOT the net seat orientation:
     * with multiple windows an earlier take-over can flip the orientation while a
     * later window declines. The rejoin signal carries this as a phase-marker
     * datum only; clients must read seats from sendPlayingPlayers and must not
     * derive who-owns-black from it.
     */
    public boolean getSwapDecisionAt(int window) {
        return swapDecision[window];
    }

    /**
     * Whether the swap window that opened after {@code window} stones (1..5) has
     * had its decision recorded. Used by the rejoin emitter to suppress a
     * meaningless silent swap-seats event when no window has resolved yet
     * (numMoves==0); see ServerTable's Renju (re)join block.
     */
    public boolean isSwapResolvedAt(int window) {
        return swapResolved[window];
    }

    /** True once Branch B (the ten-offer branch) has been chosen. */
    public boolean isBranchOffer() {
        return tenOffer;
    }

    /** True once the post-move-4 branch (A or B) has been chosen. */
    public boolean isBranchChosen() {
        return branchChosen;
    }

    /**
     * The board point committed as move 5 in Branch B (the selected offer), or
     * -1 if no selection has been committed. Used to replay the select1 signal
     * to a client that (re)joins after the Branch-B 5th move was chosen.
     */
    public int getSelectedFifthMove() {
        return selectedFifth != null ? selectedFifth.intValue() : -1;
    }

    private int centerX() {
        return gridState.getGridSizeX() / 2;
    }

    private int centerY() {
        return gridState.getGridSizeY() / 2;
    }

    /**
     * Opening central-square restriction by number of stones already placed (n).
     */
    private boolean withinOpeningSquare(int move, int n) {
        Coord c = convertMove(move);
        int dx = Math.abs(c.x - centerX());
        int dy = Math.abs(c.y - centerY());
        return switch (n) {
            case 0 -> dx == 0 && dy == 0; // move 1: center
            case 1 -> dx <= 1 && dy <= 1; // move 2: 3x3
            case 2 -> dx <= 2 && dy <= 2; // move 3: 5x5
            case 3 -> dx <= 3 && dy <= 3; // move 4: 7x7
            case 4 -> dx <= 4 && dy <= 4; // move 5 (Branch A): 9x9
            default -> true;              // move 6+: anywhere
        };
    }

    public boolean isValidMove(int move, int player) {
        if (outOfBounds(move)) return false;
        if (player != getCurrentPlayer()) return false;
        if (getPosition(move) != 0) return false;

        if (!openingComplete) {
            if (awaitingSwap) return false;
            int n = gridState.getNumMoves();
            if (n == 4 && !branchChosen) return false; // must choose branch first
            if (branchChosen && tenOffer && n == 4) {
                // offers/selection go through dedicated hooks, not isValidMove
                return false;
            }
            return withinOpeningSquare(move, n);
        }

        // Post-opening: any empty in-bounds point is legal. A black forbidden point
        // (overline / double-four / double-three) is NOT rejected here -- under Renju
        // rules black is allowed to play it, and doing so is an immediate loss. The
        // game-over / winner verdict is reported by isGameOver()/getWinner().
        return true;
    }

    /**
     * Pure check: would the current player's bundled opening stone be legal as a
     * Branch-A continuation? Covers BOTH the move-4 swap window (decline the pending
     * swap and play the bundled stone) AND the post-swap branch-choice state (the
     * move-4 swap was accepted; black now chooses Branch A by playing move 5 in the
     * 9x9). Mutates nothing. Returns false unless a swap decision OR a branch choice
     * is currently pending. Single-thread use only (table events are serialized by
     * SynchronizedServerTable).
     *
     * isValidMove() returns false while awaitingSwap is set or the branch is unchosen,
     * so this temporarily lifts the gating flags (simulating chooseBranch(false) -- a
     * no-op for the already-cleared awaitingSwap in the branch-choice case), delegates
     * to the EXISTING isValidMove, and restores every flag in finally.
     */
    public boolean wouldAcceptDeclinedOpeningMove(int move) {
        if (!awaitingSwap && !isAwaitingBranchChoice()) {
            return false;
        }
        boolean savedAwaiting = awaitingSwap, savedBranch = branchChosen, savedTen = tenOffer;
        try {
            awaitingSwap = false;  // lift the swap gate (no-op in the branch-choice case)
            branchChosen = true;   // simulate chooseBranch(false): branch picked,
            tenOffer = false;      // Branch A. Harmless for windows 1-3, where these
                                   // flags are not consulted unless n == 4.
            return isValidMove(move, getCurrentPlayer());
        } finally {
            awaitingSwap = savedAwaiting;
            branchChosen = savedBranch;
            tenOffer = savedTen;
        }
    }

    public int getCurrentPlayer() {
        if (openingComplete) return super.getCurrentPlayer();
        int n = gridState.getNumMoves();
        if (awaitingSwap) {
            int lastColor = (n - 1) % 2 + 1;
            return 3 - lastColor;
        }
        if (branchChosen && tenOffer && n == 4) {
            if (offeredFifth.size() < 10) return 1;   // black offering
            // selectFifthMove() commits via addMove(), bumping numMoves to 5, so
            // selectedFifth is always null while this n==4 block is reachable; the
            // old trailing `return 2;` after this line was unreachable dead code.
            if (selectedFifth == null) return 2;      // white selecting
        }
        if (n == 4 && !branchChosen) {
            return 1; // black chooses branch (and would play move 5)
        }
        return n % 2 + 1;
    }

    public boolean canPlayerUndo(int player) {
        if (!openingComplete) {
            // No undo while any opening decision is pending or being negotiated.
            return false;
        }
        // The completed opening is 6 committed stones. openingComplete is latched and
        // undoMove() does not recompute the opening flags, so an undo that dropped
        // numMoves below 6 would re-enter the negotiated region with the state machine
        // already past it. Mirror SimplePenteState: never undo back into the committed
        // opening.
        if (gridState.getNumMoves() <= 6) {
            return false;
        }
        return gridState.canPlayerUndo(player);
    }

    public long calcHash(long cHash, int p, int move, int rot) {
        cHash ^= ZobristUtil.rand[p - 1][rotateMove(move, rot)];
        return cHash;
    }

    public void printBoard() {
        ((SimpleGomokuState) gridState).printBoard();
    }
}
