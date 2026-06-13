package org.pente.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Faithful Java port of CForbiddenPointFinder (C++, by Wenzhe Lu).
 * Detects Renju forbidden points for black: overline, double-four, double-three.
 * Pure board logic — no GridState dependency. 0-based external coordinates.
 */
public class RenjuForbiddenPointFinder {

    public static final char BLACK = 'X';
    public static final char WHITE = 'O';
    public static final char EMPTY = '.';
    public static final char BORDER = '$';

    private final int size;
    private final char[][] b; // (size+2) x (size+2), 1-based playable region

    public RenjuForbiddenPointFinder() {
        this(15);
    }

    public RenjuForbiddenPointFinder(int size) {
        this.size = size;
        this.b = new char[size + 2][size + 2];
        clear();
    }

    public int getSize() {
        return size;
    }

    public void clear() {
        for (int i = 0; i < size + 2; i++) {
            b[0][i] = BORDER;
            b[size + 1][i] = BORDER;
            b[i][0] = BORDER;
            b[i][size + 1] = BORDER;
        }
        for (int i = 1; i <= size; i++) {
            for (int j = 1; j <= size; j++) {
                b[i][j] = EMPTY;
            }
        }
    }

    public void setStone(int x, int y, char stone) {
        b[x + 1][y + 1] = stone;
    }

    public char getStone(int x, int y) {
        return b[x + 1][y + 1];
    }

    /** Black (nColor==0): exactly 5. White (nColor==1): 5 or more. */
    public boolean isFive(int x, int y, int nColor) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        char c;
        boolean exact; // black: require ==5; white: >=5
        if (nColor == 0) { c = BLACK; exact = true; }
        else if (nColor == 1) { c = WHITE; exact = false; }
        else { return false; }

        setStone(x, y, c);
        boolean five = false;
        for (int dir = 1; dir <= 4 && !five; dir++) {
            int n = countLine(x, y, c, dir);
            five = exact ? (n == 5) : (n >= 5);
        }
        setStone(x, y, EMPTY);
        return five;
    }

    /** Count consecutive stones of color c through (x,y) along dir (the placed stone counts). */
    private int countLine(int x, int y, char c, int dir) {
        int nLine = 1;
        int i, j;
        switch (dir) {
            case 1: // horizontal
                i = x;
                while (i > 0) { boolean m = b[i][y + 1] == c; i--; if (m) nLine++; else break; }
                i = x + 2;
                while (i < size + 1) { boolean m = b[i][y + 1] == c; i++; if (m) nLine++; else break; }
                break;
            case 2: // vertical
                i = y;
                while (i > 0) { boolean m = b[x + 1][i] == c; i--; if (m) nLine++; else break; }
                i = y + 2;
                while (i < size + 1) { boolean m = b[x + 1][i] == c; i++; if (m) nLine++; else break; }
                break;
            case 3: // diagonal '/'
                i = x; j = y;
                while (i > 0 && j > 0) { boolean m = b[i][j] == c; i--; j--; if (m) nLine++; else break; }
                i = x + 2; j = y + 2;
                while (i < size + 1 && j < size + 1) { boolean m = b[i][j] == c; i++; j++; if (m) nLine++; else break; }
                break;
            case 4: // diagonal '\'
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) { boolean m = b[i][j] == c; i--; j++; if (m) nLine++; else break; }
                i = x + 2; j = y;
                while (i < size + 1 && j > 0) { boolean m = b[i][j] == c; i++; j--; if (m) nLine++; else break; }
                break;
            default:
                return 0;
        }
        return nLine;
    }

    /** True if placing black at (x,y) makes 6+ in some direction (but a direction of exactly 5 cancels). */
    public boolean isOverline(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        setStone(x, y, BLACK);
        boolean overline = false;
        for (int dir = 1; dir <= 4; dir++) {
            int n = countLine(x, y, BLACK, dir);
            if (n == 5) {
                setStone(x, y, EMPTY);
                return false;
            } else {
                overline |= (n >= 6);
            }
        }
        setStone(x, y, EMPTY);
        return overline;
    }

    /** Single-direction five (exactly 5), color only picks the stone char. */
    public boolean isFive(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) {
            return false;
        }
        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        int n = countLine(x, y, c, dir);
        setStone(x, y, EMPTY);
        return n == 5;
    }

    /** True if placing color at (x,y) yields exactly a four (one empty extension makes five) along dir. */
    public boolean isFour(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, nColor)) return false;
        if (nColor == 0 && isOverline(x, y)) return false;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        boolean result = false;
        int i, j;
        switch (dir) {
            case 1:
                i = x;
                while (i > 0) {
                    if (b[i][y + 1] == c) { i--; continue; }
                    else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; continue; }
                        else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i > 0) {
                    if (b[x + 1][i] == c) { i--; continue; }
                    else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; continue; }
                        else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i > 0 && j > 0) {
                    if (b[i][j] == c) { i--; j--; continue; }
                    else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) {
                    if (b[i][j] == c) { i--; j++; continue; }
                    else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                    else break;
                }
                if (!result) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { result = true; } break; }
                        else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return result;
    }

    /** 0 = not open four; 1 = open four of exactly 4 stones; 2 = 5-stone variant (counts double). */
    public int isOpenFour(int x, int y, int nColor, int dir) {
        if (b[x + 1][y + 1] != EMPTY) return 0;
        if (isFive(x, y, nColor)) return 0;
        if (nColor == 0 && isOverline(x, y)) return 0;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return 0;

        setStone(x, y, c);
        int nLine = 1;
        int i, j;
        int ret = 0;
        boolean done = false;
        switch (dir) {
            case 1:
                i = x;
                while (i >= 0) {
                    if (b[i][y + 1] == c) { i--; nLine++; continue; }
                    else if (b[i][y + 1] == EMPTY) { if (!isFive(i - 1, y, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; nLine++; continue; }
                        else if (b[i][y + 1] == EMPTY) { if (isFive(i - 1, y, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i >= 0) {
                    if (b[x + 1][i] == c) { i--; nLine++; continue; }
                    else if (b[x + 1][i] == EMPTY) { if (!isFive(x, i - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; nLine++; continue; }
                        else if (b[x + 1][i] == EMPTY) { if (isFive(x, i - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i >= 0 && j >= 0) {
                    if (b[i][j] == c) { i--; j--; nLine++; continue; }
                    else if (b[i][j] == EMPTY) { if (!isFive(i - 1, j - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; nLine++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i >= 0 && j <= size + 1) {
                    if (b[i][j] == c) { i--; j++; nLine++; continue; }
                    else if (b[i][j] == EMPTY) { if (!isFive(i - 1, j - 1, 0, dir)) { done = true; } break; }
                    else { done = true; break; }
                }
                if (!done) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; nLine++; continue; }
                        else if (b[i][j] == EMPTY) { if (isFive(i - 1, j - 1, 0, dir)) { ret = (nLine == 4 ? 1 : 2); } break; }
                        else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return ret;
    }

    public boolean isDoubleFour(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, 0)) return false;

        int nFour = 0;
        for (int dir = 1; dir <= 4; dir++) {
            if (isOpenFour(x, y, 0, dir) == 2) nFour += 2;
            else if (isFour(x, y, 0, dir)) nFour++;
        }
        return nFour >= 2;
    }

    public boolean isOpenThree(int x, int y, int nColor, int dir) {
        if (isFive(x, y, nColor)) return false;
        if (nColor == 0 && isOverline(x, y)) return false;

        char c;
        if (nColor == 0) c = BLACK;
        else if (nColor == 1) c = WHITE;
        else return false;

        setStone(x, y, c);
        boolean result = false;
        int i, j;
        switch (dir) {
            case 1:
                i = x;
                while (i > 0) {
                    if (b[i][y + 1] == c) { i--; continue; }
                    else if (b[i][y + 1] == EMPTY) {
                        if (isOpenFour(i - 1, y, nColor, dir) == 1 && !isDoubleFour(i - 1, y) && !isDoubleThree(i - 1, y)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2;
                    while (i < size + 1) {
                        if (b[i][y + 1] == c) { i++; continue; }
                        else if (b[i][y + 1] == EMPTY) {
                            if (isOpenFour(i - 1, y, nColor, dir) == 1 && !isDoubleFour(i - 1, y) && !isDoubleThree(i - 1, y)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 2:
                i = y;
                while (i > 0) {
                    if (b[x + 1][i] == c) { i--; continue; }
                    else if (b[x + 1][i] == EMPTY) {
                        if (isOpenFour(x, i - 1, nColor, dir) == 1 && !isDoubleFour(x, i - 1) && !isDoubleThree(x, i - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = y + 2;
                    while (i < size + 1) {
                        if (b[x + 1][i] == c) { i++; continue; }
                        else if (b[x + 1][i] == EMPTY) {
                            if (isOpenFour(x, i - 1, nColor, dir) == 1 && !isDoubleFour(x, i - 1) && !isDoubleThree(x, i - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 3:
                i = x; j = y;
                while (i > 0 && j > 0) {
                    if (b[i][j] == c) { i--; j--; continue; }
                    else if (b[i][j] == EMPTY) {
                        if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2; j = y + 2;
                    while (i < size + 1 && j < size + 1) {
                        if (b[i][j] == c) { i++; j++; continue; }
                        else if (b[i][j] == EMPTY) {
                            if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            case 4:
                i = x; j = y + 2;
                while (i > 0 && j < size + 1) {
                    if (b[i][j] == c) { i--; j++; continue; }
                    else if (b[i][j] == EMPTY) {
                        if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                        break;
                    } else break;
                }
                if (!result) {
                    i = x + 2; j = y;
                    while (i < size + 1 && j > 0) {
                        if (b[i][j] == c) { i++; j--; continue; }
                        else if (b[i][j] == EMPTY) {
                            if (isOpenFour(i - 1, j - 1, nColor, dir) == 1 && !isDoubleFour(i - 1, j - 1) && !isDoubleThree(i - 1, j - 1)) { result = true; }
                            break;
                        } else break;
                    }
                }
                break;
            default:
                break;
        }
        setStone(x, y, EMPTY);
        return result;
    }

    public boolean isDoubleThree(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        if (isFive(x, y, 0)) return false;

        int nThree = 0;
        for (int dir = 1; dir <= 4; dir++) {
            if (isOpenThree(x, y, 0, dir)) nThree++;
        }
        return nThree >= 2;
    }

    /** A point is forbidden for black if it makes an overline, double-four, or double-three. */
    public boolean isForbidden(int x, int y) {
        if (b[x + 1][y + 1] != EMPTY) return false;
        return isOverline(x, y) || isDoubleFour(x, y) || isDoubleThree(x, y);
    }

    /** Scan the whole board for black forbidden points (for UI hinting). */
    public List<Coord> findForbiddenPoints() {
        List<Coord> pts = new ArrayList<Coord>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (b[i + 1][j + 1] != EMPTY) continue;
                if (isOverline(i, j) || isDoubleFour(i, j) || isDoubleThree(i, j)) {
                    pts.add(new Coord(i, j));
                }
            }
        }
        return pts;
    }
}
