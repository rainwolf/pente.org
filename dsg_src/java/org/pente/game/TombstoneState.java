package org.pente.game;

/** Tombstone Pente rules engine: turn grammar, colour-explicit captures, five and draws. */
public class TombstoneState extends GridStateDecorator implements HashCalculator {
    private static final long serialVersionUID = 4120957311650277421L;

    public static final int MAX_ENTRIES = 3000;
    public static final int CAPTURES_TO_WIN = 10;
    public static final int EMPTY_TURNS_FOR_DRAW = 6;
    private static final int CENTER = 180;
    private static final int[][] DIRS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    private final int[] entryPlayer = new int[MAX_ENTRIES];
    private final int[] capturesBefore1 = new int[MAX_ENTRIES];
    private final int[] capturesBefore2 = new int[MAX_ENTRIES];
    private final int[] captures = new int[3];
    private final int[] lastCapturedPoints = new int[16];
    private int lastCapturedCount;
    private int turnCount;
    private boolean awaitingDead;
    private int consecutiveEmptyTurns;
    private boolean gameOver;
    private int winner;
    private int emptyPoints = 361;

    public TombstoneState() {
        super(new SimpleGridState(19, 19, MAX_ENTRIES));
        ((SimpleGridState) gridState).setAllowNonBoardMoves(true);
    }

    private static int playerOfTurn(int t) {
        return t % 2 + 1;
    }

    public int getCurrentPlayer() {
        return awaitingDead ? playerOfTurn(turnCount - 1) : playerOfTurn(turnCount);
    }

    public int getCurrentColor() {
        return getCurrentPlayer();
    }

    public int getColor(int entryIndex) {
        if (entryIndex >= 0 && entryIndex < getNumMoves()) {
            return entryPlayer[entryIndex];
        }
        return 0;
    }

    public int getTurnCount() { return turnCount; }
    public boolean isAwaitingDeadStone() { return awaitingDead; }
    public int getConsecutiveEmptyTurns() { return consecutiveEmptyTurns; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
    public int getNumCaptures(int player) { return captures[player]; }

    public boolean isValidMove(int code, int player) {
        if (gameOver || player != getCurrentPlayer()) return false;
        if (getNumMoves() == 0) return code == CENTER;
        if (awaitingDead) {
            return TombstoneCodes.isDeadStone(code)
                    && gridState.getPosition(TombstoneCodes.pointOf(code)) == 0;
        }
        if (TombstoneCodes.isOwnPass(code)) return true;
        return TombstoneCodes.isOwnStone(code) && gridState.getPosition(code) == 0;
    }

    private boolean lastEntryIsPass() {
        int n = getNumMoves();
        return n > 0 && gridState.getMove(n - 1) == TombstoneCodes.OWN_PASS;
    }

    private void closeTurn(boolean empty) {
        awaitingDead = false;
        consecutiveEmptyTurns = empty ? consecutiveEmptyTurns + 1 : 0;
        if (consecutiveEmptyTurns >= EMPTY_TURNS_FOR_DRAW) {
            gameOver = true;
            winner = 0;
        }
    }

    public void skipDeadStone() {
        if (!awaitingDead) {
            throw new IllegalStateException("not awaiting a dead stone");
        }
        closeTurn(lastEntryIsPass());
    }

    public void addMove(int code) {
        int n = getNumMoves();
        if (gameOver) throw new IllegalArgumentException("game is over");
        if (code < 0 || code > TombstoneCodes.OWN_PASS) {
            throw new IllegalArgumentException("bad code " + code);
        }
        if (n >= MAX_ENTRIES) throw new IllegalArgumentException("too many entries");
        boolean dead = TombstoneCodes.isDeadStone(code);
        if (n == 0) {
            if (code != CENTER) throw new IllegalArgumentException("first move must be the centre");
        } else if (dead) {
            if (!awaitingDead) throw new IllegalArgumentException("not awaiting a dead stone");
            if (gridState.getPosition(TombstoneCodes.pointOf(code)) != 0) {
                throw new IllegalArgumentException("dead stone on occupied point " + code);
            }
        } else {
            if (TombstoneCodes.isOwnStone(code) && gridState.getPosition(code) != 0) {
                throw new IllegalArgumentException("position already filled " + code);
            }
            if (awaitingDead && lastEntryIsPass()
                    && consecutiveEmptyTurns + 1 >= EMPTY_TURNS_FOR_DRAW) {
                throw new IllegalArgumentException("game ends when the open turn closes");
            }
        }

        if (!dead && awaitingDead) {
            closeTurn(lastEntryIsPass());
        }
        int p;
        if (n == 0) {
            p = 1;
        } else if (dead) {
            p = playerOfTurn(turnCount - 1);
        } else {
            p = playerOfTurn(turnCount);
        }

        gridState.addMove(code);
        if (dead) {
            gridState.setPosition(TombstoneCodes.pointOf(code), TombstoneCodes.DEAD_CELL);
        } else if (TombstoneCodes.isOwnStone(code)) {
            gridState.setPosition(code, p);
        }
        int before1 = captures[1];
        int before2 = captures[2];
        lastCapturedCount = 0;
        if (TombstoneCodes.isOwnStone(code)) {
            emptyPoints--;
            applyCaptures(code, p);
        } else if (dead) {
            emptyPoints--;
        }
        entryPlayer[n] = p;
        capturesBefore1[n] = before1;
        capturesBefore2[n] = before2;
        updateHash(this);

        if (dead) {
            closeTurn(false);
            if (!gameOver && emptyPoints == 0) {
                gameOver = true;
                winner = 0;
            }
            return;
        }
        turnCount++;
        if (n == 0) {
            awaitingDead = false;
        } else if (TombstoneCodes.isOwnStone(code)
                && (captures[p] >= CAPTURES_TO_WIN || hasFive(code, p))) {
            gameOver = true;
            winner = p;
            awaitingDead = false;
        } else if (emptyPoints == 0) {
            gameOver = true;
            winner = 0;
            awaitingDead = false;
        } else {
            awaitingDead = true;
        }
    }

    private void applyCaptures(int point, int c) {
        int x = point % 19;
        int y = point / 19;
        int opp = 3 - c;
        for (int[] d : DIRS) {
            int x3 = x + 3 * d[0];
            int y3 = y + 3 * d[1];
            if (x3 < 0 || x3 > 18 || y3 < 0 || y3 > 18) continue;
            if (gridState.getPosition(x + d[0], y + d[1]) == opp
                    && gridState.getPosition(x + 2 * d[0], y + 2 * d[1]) == opp
                    && gridState.getPosition(x3, y3) == c) {
                for (int k = 1; k <= 2; k++) {
                    gridState.setPosition(x + k * d[0], y + k * d[1], 0);
                    lastCapturedPoints[lastCapturedCount++] = (x + k * d[0]) + 19 * (y + k * d[1]);
                }
                captures[c] += 2;
                emptyPoints += 2;
            }
        }
    }

    private boolean hasFive(int point, int c) {
        int x = point % 19;
        int y = point / 19;
        int[][] axes = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] a : axes) {
            int count = 1;
            for (int sign = -1; sign <= 1; sign += 2) {
                int cx = x + sign * a[0];
                int cy = y + sign * a[1];
                while (cx >= 0 && cx <= 18 && cy >= 0 && cy <= 18
                        && gridState.getPosition(cx, cy) == c) {
                    count++;
                    cx += sign * a[0];
                    cy += sign * a[1];
                }
            }
            if (count >= 5) return true;
        }
        return false;
    }

    public void clear() {
        gridState.clear();
        captures[1] = 0;
        captures[2] = 0;
        lastCapturedCount = 0;
        turnCount = 0;
        awaitingDead = false;
        consecutiveEmptyTurns = 0;
        gameOver = false;
        winner = 0;
        emptyPoints = 361;
    }

    public long calcHash(long cHash, int p, int move, int rot) {
        if (TombstoneCodes.isOwnPass(move)) return cHash;
        if (TombstoneCodes.isDeadStone(move)) {
            return cHash ^ ZobristUtil.rand[ZobristUtil.TOMBSTONE_DEAD_ROW]
                    [rotateMove(TombstoneCodes.pointOf(move), rot)];
        }
        int last = getNumMoves() - 1;
        int c = entryPlayer[last];
        long h = cHash ^ ZobristUtil.rand[c - 1][rotateMove(move, rot)];
        for (int i = 0; i < lastCapturedCount; i++) {
            h ^= ZobristUtil.rand[(3 - c) - 1][rotateMove(lastCapturedPoints[i], rot)];
        }
        int row = c == 1 ? 2 : 3;
        int before = c == 1 ? capturesBefore1[last] : capturesBefore2[last];
        int after = captures[c];
        if (before > 0) h ^= ZobristUtil.rand[row][before];
        if (after > 0) h ^= ZobristUtil.rand[row][after];
        return h;
    }

    public void printBoard() {
        ((SimpleGridState) gridState).printBoard();
    }
}
