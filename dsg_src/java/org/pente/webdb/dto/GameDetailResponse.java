package org.pente.webdb.dto;

/**
 * Wire response for {@code GET /api/db/games/{gid}}.
 *
 * <pre>
 * {"header": GameHeader, "moves": [180, 199, ...], "renjuOffers": [...]}
 * </pre>
 *
 * {@code moves} is the full reconstructed move list in board order (the center
 * stone {@code 180} first for non-Go variants). {@code renjuOffers} is present
 * (non-null) only for Renju games that recorded swap offers.
 */
public class GameDetailResponse {

    /** The game's header row (same shape used in listings). */
    public GameHeader header;

    /** Full move list, board order. */
    public int[] moves;

    /** Renju swap offers, or {@code null} when the game has none. */
    public int[] renjuOffers;
}
