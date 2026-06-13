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

    public RenjuState() {
        this(15, 15);
    }

    public RenjuState(GridState gridState) {
        super(gridState);
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
        if (!openingComplete && n >= 6) {
            openingComplete = true;
        }
        updateHash(this);
    }

    public void undoMove() {
        gridState.undoMove();
        refreshFinder();
        updateHash(this);
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

    // branch flags (Branch B / offers wired in Tasks 6-7; default Branch A here)
    private boolean branchChosen = true;   // Task 5/6 will flip default to false
    private boolean tenOffer = false;

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
            int n = gridState.getNumMoves();
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
        // Straight-line sequencing (swaps added in Task 5): color by parity.
        int n = gridState.getNumMoves();
        return n % 2 + 1;
    }

    public long calcHash(long cHash, int p, int move, int rot) {
        cHash ^= ZobristUtil.rand[p - 1][rotateMove(move, rot)];
        return cHash;
    }

    public void printBoard() {
        ((SimpleGomokuState) gridState).printBoard();
    }
}
