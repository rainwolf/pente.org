package org.pente.webdb.dto;

/**
 * Wire request for {@code POST /api/db/games/search}.
 *
 * <pre>
 * {"game": 1, "moves": [180, 210], "scope": "archive",
 *  "filters": {...}, "offset": 0, "limit": 25}
 * </pre>
 *
 * {@code moves} is the position to constrain results to, in the caller's board
 * orientation (first move {@code 180} for non-Go variants). When {@code moves}
 * is null/empty the search is filter-only (no position constraint).
 * {@code filters} reuses the Task 3 filter DTO ({@link PositionStatsRequest.Filters}).
 */
public class GameSearchRequest {

    /** Game/variant id (see {@code GridStateFactory}, e.g. {@code PENTE == 1}). */
    public int game;

    /** Position to constrain to, caller's orientation; null/empty = no position. */
    public int[] moves;

    /** {@code "archive"} (default) or {@code "mine"} (added in a later task). */
    public String scope;

    /** Optional venue/player/date/winner filters; null means "no filters". */
    public PositionStatsRequest.Filters filters;

    /** Zero-based row offset into the ordered result set. */
    public int offset;

    /** Max rows to return; clamped by the handler. */
    public int limit = 25;
}
