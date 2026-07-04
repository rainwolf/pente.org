package org.pente.game;

/**
 * Codec for Renju Taraguchi-10 opening state.
 *
 * Six base-3 digits packed into one int (0..728, fits smallint unsigned):
 *   d0 swap after move 1, d1 after move 2, d2 after move 3, d3 after move 4,
 *   d4 branch choice, d5 swap after move 5 (Branch A only).
 * Each digit: 0 = pending, 1 = no (swap declined / branch A), 2 = yes (swap / branch B).
 *
 * Also codes the 10 offered 5th moves as a byte array (each 15x15 position 0..224
 * fits one unsigned byte).
 */
public class RenjuOpeningState {

    public static final int PENDING = 0;
    public static final int NO = 1;
    public static final int YES = 2;

    public int swap1;
    public int swap2;
    public int swap3;
    public int swap4;
    public int branch;
    public int swap5;

    public int encode() {
        return swap1
                + swap2 * 3
                + swap3 * 9
                + swap4 * 27
                + branch * 81
                + swap5 * 243;
    }

    public static RenjuOpeningState decode(int packed) {
        RenjuOpeningState s = new RenjuOpeningState();
        s.swap1 = packed % 3; packed /= 3;
        s.swap2 = packed % 3; packed /= 3;
        s.swap3 = packed % 3; packed /= 3;
        s.swap4 = packed % 3; packed /= 3;
        s.branch = packed % 3; packed /= 3;
        s.swap5 = packed % 3;
        return s;
    }

    /**
     * Net seat orientation: true iff an odd number of seat swaps happened —
     * take-overs after moves 1-4 plus the Branch A 5th-move swap.
     * The branch digit is a branch choice, not a swap. PENDING and NO digits
     * count as no swap, so this is valid mid-game.
     * keep in sync with RenjuState.seatsSwapped() — same window classification
     */
    public boolean netSwapped() {
        int yes = 0;
        if (swap1 == YES) yes++;
        if (swap2 == YES) yes++;
        if (swap3 == YES) yes++;
        if (swap4 == YES) yes++;
        if (swap5 == YES) yes++;
        return (yes & 1) == 1;
    }

    /** Net seat orientation straight from the packed word. */
    public static boolean netSwapped(int packed) {
        return decode(packed).netSwapped();
    }

    /** Pack offered positions (0..224 each) into one byte each. */
    public static byte[] encodeOffers(int[] offers) {
        if (offers == null) {
            return null;
        }
        byte[] bytes = new byte[offers.length];
        for (int i = 0; i < offers.length; i++) {
            bytes[i] = (byte) (offers[i] & 0xFF);
        }
        return bytes;
    }

    /** Unpack offered positions (unsigned bytes back to 0..224). */
    public static int[] decodeOffers(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] offers = new int[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            offers[i] = bytes[i] & 0xFF;
        }
        return offers;
    }
}
