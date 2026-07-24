package org.pente.webdb.dto;

/**
 * Wire request for {@code POST /api/db/position-stats}.
 *
 * Deserialized from JSON by Gson (see {@code JsonHttp.readBody}); Gson invokes
 * the implicit no-arg constructor, so the field initializers below supply the
 * documented defaults (seat 1 / seat 2, {@code winner == 0}) whenever a key is
 * absent from the request body.
 *
 * <pre>
 * {"game": 1, "moves": [180, 210, ...], "scope": "archive",
 *  "filters": {"player1Name": null, "player1Seat": 1,
 *              "player2Name": null, "player2Seat": 2,
 *              "site": null, "event": null, "round": null, "section": null,
 *              "afterDate": null, "beforeDate": null, "winner": 0}}
 * </pre>
 *
 * {@code moves} are cell ints {@code 0..360} in the caller's board orientation
 * (first move is the center, {@code 180}, for non-Go variants). {@code filters}
 * may be null (no filtering).
 */
public class PositionStatsRequest {

    /** Game/variant id (see {@code GridStateFactory}, e.g. {@code PENTE == 1}). */
    public int game;

    /** Move list in the caller's orientation; may be empty. */
    public int[] moves;

    /** {@code "archive"} (default) or {@code "mine"} (added in Task 6). */
    public String scope;

    /** Optional venue/player/date/winner filters; null means "no filters". */
    public Filters filters;

    /** Seat filters mirror {@code GameStorerSearchRequestFilterData}. */
    public static class Filters {

        /** Player-in-any-seat sentinel (matches {@code SEAT_ALL == 0}). */
        public static final int SEAT_ALL = 0;

        public String player1Name;
        public int player1Seat = 1;

        public String player2Name;
        public int player2Seat = 2;

        /** Site display name (resolved to {@code g.site_id}). */
        public String site;
        /** Event display name (resolved to {@code g.event_id}; needs {@link #site}). */
        public String event;
        /** {@code g.round} exact match. */
        public String round;
        /** {@code g.section} exact match. */
        public String section;

        /** ISO {@code yyyy-MM-dd}; {@code g.play_date > afterDate}. */
        public String afterDate;
        /** ISO {@code yyyy-MM-dd}; {@code g.play_date < beforeDate}. */
        public String beforeDate;

        /** {@code 0} = any; otherwise {@code m.winner = winner} (1 or 2). */
        public int winner;
    }
}
