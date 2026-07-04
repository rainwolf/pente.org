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
            // Replay is authoritative from the persisted digits, so suppress the
            // live move-4-swap auto-commit of Branch A -- the branch digit below
            // drives it (and must be able to replay a legacy Branch-B-after-swap
            // save faithfully rather than being overridden to Branch A).
            s.renjuSwapDecisionMade(swaps[k] == RenjuOpeningState.YES, false);
        }

        // branch choice -- authoritative from the persisted branch digit. A move-4
        // take-over persists as branch=NO (live) or branch=PENDING+swap4=YES (turn-based,
        // whose move 5 is a plain PLACE that never writes the digit); both mean Branch A.
        // Only a genuine still-pending DECLINE (PENDING without a take-over) stops the
        // replay here; otherwise NO -> Branch A and YES -> Branch B (incl. a legacy
        // Branch-B-after-swap save, replayed faithfully rather than forced to Branch A).
        if (st.branch == RenjuOpeningState.PENDING && st.swap4 != RenjuOpeningState.YES) {
            return s;
        }
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
        List<int[]> stabilizer = positionStabilizer();
        List<Integer> accepted = new ArrayList<Integer>(offeredFifth);
        for (int move : moves) {
            if (outOfBounds(move) || getPosition(move) != 0) {
                return false;
            }
            boolean duplicate = false;
            for (int[] transform : stabilizer) {
                int image = applyTransform(move, transform);
                if (image < 0) {
                    continue; // image off-board: cannot collide with any offer
                }
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
     * D4 symmetry dedup: a candidate is a duplicate if some symmetry that maps the
     * current placed-stone shape onto itself also maps the candidate onto an existing offer.
     */
    private boolean isSymmetricDuplicate(int move) {
        for (int[] transform : positionStabilizer()) {
            int image = applyTransform(move, transform);
            if (image < 0) {
                continue; // image off-board: cannot equal any offered point
            }
            for (Integer existing : offeredFifth) {
                if (existing.intValue() == image) {
                    return true;
                }
            }
        }
        return false;
    }

    // Linear D4 parts applied to ABSOLUTE board coords (no centre offset). Index
    // table matches SimpleGridState.rotx/roty/rotf; kept local so the stabilizer's
    // affine maps are computed directly in absolute coordinates.
    private static final int[] LROTX = {1, 1, 1, 1, -1, -1, -1, -1};
    private static final int[] LROTY = {1, 1, -1, -1, -1, -1, 1, 1};
    private static final int[] LROTF = {0, 1, 0, 1, 0, 1, 0, 1};

    /** Linear part of D4 op r in absolute coords: returns [x1, y1]. */
    private int[] lin(int x, int y, int r) {
        int x1 = x * LROTX[r];
        int y1 = y * LROTY[r];
        if (LROTF[r] != 0) {
            int t = x1;
            x1 = y1;
            y1 = t;
        }
        return new int[]{x1, y1};
    }

    /**
     * Apply an affine stabilizer transform (r, tx, ty) to a board point.
     * g(p) = lin(p, r) + (tx, ty). BOUNDS GUARD: returns the board index if the
     * image is on-board, else sentinel -1 (so row wraparound cannot create a false
     * duplicate).
     */
    private int applyTransform(int move, int[] transform) {
        Coord c = convertMove(move);
        int[] l = lin(c.x, c.y, transform[0]);
        int X = l[0] + transform[1];
        int Y = l[1] + transform[2];
        int sx = gridState.getGridSizeX();
        int sy = gridState.getGridSizeY();
        if (X >= 0 && X < sx && Y >= 0 && Y < sy) {
            return convertMove(X, Y);
        }
        return -1;
    }

    /**
     * Affine D4 symmetries (r, tx, ty) of the current placed-stone shape, computed
     * in ABSOLUTE coordinates (not about the fixed board centre). A transform is a
     * colour-preserving bijection of the placed set onto itself: g(p) = lin(p, r) +
     * (tx, ty). The translation is solved from where the first stone must land, so
     * symmetries about an off-centre point/axis are found (the centre-based version
     * missed them, letting mirror-equivalent 5th-offer candidates pass as distinct).
     * Always contains identity (0,0,0); for an asymmetric shape it is exactly that.
     */
    private List<int[]> positionStabilizer() {
        int sx = gridState.getGridSizeX();
        int sy = gridState.getGridSizeY();
        // Collect placed stones with colours: {x, y, colour}. Walk the actual
        // move list (~4 stones during the opening) instead of rescanning all
        // sx*sy board cells on every offer. Colour is read from the board so it
        // reflects any opening swaps, not the raw move parity.
        List<int[]> placed = new ArrayList<int[]>();
        int numMoves = gridState.getNumMoves();
        for (int i = 0; i < numMoves; i++) {
            int mv = gridState.getMove(i);
            if (outOfBounds(mv)) {
                continue;
            }
            Coord c = convertMove(mv);
            int colour = gridState.getPosition(c.x, c.y);
            if (colour != 0) {
                placed.add(new int[]{c.x, c.y, colour});
            }
        }

        List<int[]> stab = new ArrayList<int[]>();
        if (placed.isEmpty()) {
            stab.add(new int[]{0, 0, 0});
            return stab;
        }

        int[] p0 = placed.get(0);
        int c0 = p0[2];
        for (int r = 0; r < 8; r++) {
            int[] lp0 = lin(p0[0], p0[1], r);
            // p0 must map to some placed stone of the same colour: that fixes (tx, ty).
            for (int[] q : placed) {
                if (q[2] != c0) {
                    continue;
                }
                int tx = q[0] - lp0[0];
                int ty = q[1] - lp0[1];
                boolean ok = true;
                for (int[] pi : placed) {
                    int[] l = lin(pi[0], pi[1], r);
                    int X = l[0] + tx;
                    int Y = l[1] + ty;
                    if (X < 0 || X >= sx || Y < 0 || Y >= sy) {
                        ok = false;
                        break;
                    }
                    if (gridState.getPosition(X, Y) != pi[2]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    boolean dup = false;
                    for (int[] t : stab) {
                        if (t[0] == r && t[1] == tx && t[2] == ty) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        stab.add(new int[]{r, tx, ty});
                    }
                }
            }
        }

        // Identity is always a symmetry; guard in case it was not enumerated.
        boolean hasId = false;
        for (int[] t : stab) {
            if (t[0] == 0 && t[1] == 0 && t[2] == 0) {
                hasId = true;
                break;
            }
        }
        if (!hasId) {
            stab.add(0, new int[]{0, 0, 0});
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

    /** Net parity of the recorded take-over decisions (windows 1-5). */
    @Override
    public boolean seatsSwapped() {
        return swapDecision[1] ^ swapDecision[2] ^ swapDecision[3]
                ^ swapDecision[4] ^ swapDecision[5];
    }

    public void renjuSwapDecisionMade(boolean swap) {
        renjuSwapDecisionMade(swap, true);
    }

    /**
     * @param autoCommitBranchA when true (live play), taking the swap at the move-4
     *   window auto-commits Branch A (see below). {@link #reconstruct} passes false:
     *   a replay is authoritative from the persisted branch digit, which already
     *   records the outcome (Branch A as NO, legacy Branch B as YES, turn-based
     *   take-over as PENDING), so reconstruct drives the branch itself and must not
     *   let a take-over silently override a persisted Branch-B save.
     */
    public void renjuSwapDecisionMade(boolean swap, boolean autoCommitBranchA) {
        if (!awaitingSwap) {
            throw new IllegalStateException("no swap decision pending");
        }
        swapDecision[gridState.getNumMoves()] = swap;
        swapResolved[gridState.getNumMoves()] = true;
        awaitingSwap = false;
        // Taraguchi-10: taking the swap at the move-4 window IS one of the two
        // "Branch A" outcomes, so it resolves the branch decision. Auto-commit
        // Branch A here; otherwise the branch window stays open and the
        // swapped-in player is wrongly re-presented the swap/Branch-B choice.
        // The DECLINE path (swap == false) is unchanged: it still leaves the
        // A-vs-B (offer-ten) choice open for the next decision.
        if (autoCommitBranchA && swap && gridState.getNumMoves() == 4) {
            // Canonical Branch-A commit -- the same chooseBranch path reconstruct()
            // and every other caller use. At this point isAwaitingBranchChoice() is
            // true (awaitingSwap just cleared, numMoves==4, branch unchosen), so it
            // does not throw; routing through chooseBranch keeps the live and replay
            // take-over commits from diverging.
            chooseBranch(false);
        }
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
     * swap and play the bundled stone) AND the branch-choice state reached by
     * DECLINING the move-4 window (black picks Branch A by playing move 5 in the
     * 9x9). A move-4 TAKE-OVER no longer reaches this method: it auto-commits Branch A
     * (renjuSwapDecisionMade), so its move 5 is placed like any ordinary move -- the
     * live server validates it via isValidMove in handleMove, and the turn-based path
     * via its move storer (CacheTBStorer). Mutates nothing. Returns false unless a
     * swap decision OR a branch choice
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
