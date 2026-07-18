package org.pente.webdb.dto;

import java.util.List;

/**
 * Wire response for {@code POST /api/db/games/search}.
 *
 * <pre>
 * {"total": 4321, "games": [GameHeader, ...]}
 * </pre>
 *
 * {@code total} is the full count of games matching the query (before
 * {@code offset}/{@code limit} paging); {@code games} is the current page.
 */
public class GameSearchResponse {

    /** Total matching games, ignoring paging. */
    public long total;

    /** The current page of matching games, in query order. */
    public List<GameHeader> games;
}
