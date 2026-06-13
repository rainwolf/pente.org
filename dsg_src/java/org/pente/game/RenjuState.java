package org.pente.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Renju rules + Taraguchi-10 opening, as a decorator over a SimpleGomokuState.
 * Black (color 1) wins only on exactly five; white (color 2) wins on five or more.
 * Black may not play a forbidden point (overline / double-four / double-three).
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

    /** Sync the finder's board from the wrapped grid (color 1 -> 'X', 2 -> 'O', 0 -> '.'). */
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
        for (int i = 0; i < swapDecision.length; i++) swapDecision[i] = false;
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
        finder.setStone(c.x, c.y, RenjuForbiddenPointFinder.EMPTY);
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        // restore
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);

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
        boolean five = finder.isFive(c.x, c.y, lastColor == 1 ? 0 : 1);
        finder.setStone(c.x, c.y,
                lastColor == 1 ? RenjuForbiddenPointFinder.BLACK : RenjuForbiddenPointFinder.WHITE);
        return five ? lastColor : 0;
    }

    private boolean outOfBounds(int move) {
        Coord c = convertMove(move);
        return c.x < 0 || c.x >= gridState.getGridSizeX()
                || c.y < 0 || c.y >= gridState.getGridSizeY();
    }

    /** Black forbidden points on the current board (for UI). */
    public List<Coord> getForbiddenPoints() {
        return finder.findForbiddenPoints();
    }

    // --- opening protocol state (fully wired in Task 4) ---
    private boolean openingComplete = false;

    public boolean isOpeningComplete() {
        return openingComplete;
    }

    /** Test/seam hook: mark the opening done so post-opening rules apply. */
    public void forceOpeningComplete() {
        openingComplete = true;
    }

    public boolean isAwaitingBranchChoice() {
        return !openingComplete && !awaitingSwap
                && gridState.getNumMoves() == 4 && !branchChosen;
    }

    /** Black picks the post-move-4 path. false = Branch A (9x9 + swap), true = Branch B (10 offers). */
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

    /** Rotation indices (0..7) whose D4 operation leaves the current stones invariant. */
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
        awaitingSwap = false;
    }

    private int centerX() { return gridState.getGridSizeX() / 2; }
    private int centerY() { return gridState.getGridSizeY() / 2; }

    /** Opening central-square restriction by number of stones already placed (n). */
    private boolean withinOpeningSquare(int move, int n) {
        Coord c = convertMove(move);
        int dx = Math.abs(c.x - centerX());
        int dy = Math.abs(c.y - centerY());
        switch (n) {
            case 0: return dx == 0 && dy == 0; // move 1: center
            case 1: return dx <= 1 && dy <= 1; // move 2: 3x3
            case 2: return dx <= 2 && dy <= 2; // move 3: 5x5
            case 3: return dx <= 3 && dy <= 3; // move 4: 7x7
            case 4: return dx <= 4 && dy <= 4; // move 5 (Branch A): 9x9
            default: return true;              // move 6+: anywhere
        }
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
            if (!withinOpeningSquare(move, n)) return false;
            return true;
        }

        // post-opening: block black forbidden points
        if (getCurrentColor() == 1) {
            Coord c = convertMove(move);
            if (finder.isForbidden(c.x, c.y)) return false;
        }
        return true;
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
