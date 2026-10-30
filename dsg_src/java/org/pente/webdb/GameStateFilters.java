package org.pente.webdb;

import java.util.List;

import org.pente.webdb.dto.PositionStatsRequest;

/**
 * Single source for the timeout and live/turn-based predicates shared by
 * position-stats and games/search. All predicates reference {@code pente_game g}.
 */
public final class GameStateFilters {

    public static final String LIVE = "live";
    public static final String TURN_BASED = "turnBased";
    public static final String ALL = "all";

    public static final int PENTE_ORG_SITE_ID = 2;
    public static final long TB_GID_START = 50000000000000L;
    public static final String STATUS_TIMEOUT = "T";

    private GameStateFilters() {}

    /**
     * @return null (off), {@link #LIVE} or {@link #TURN_BASED}.
     * @throws IllegalArgumentException for any other non-blank value (case-sensitive)
     */
    public static String liveMode(PositionStatsRequest.Filters f) {
        if (f == null) return null;
        String v = f.liveOrTurnBased;
        if (v == null || v.isEmpty() || ALL.equals(v)) return null;
        if (LIVE.equals(v)) return LIVE;
        if (TURN_BASED.equals(v)) return TURN_BASED;
        throw new IllegalArgumentException("invalid liveOrTurnBased: " + v);
    }

    /** True iff a predicate is set that needs {@code pente_game g}. */
    public static boolean needsGame(PositionStatsRequest.Filters f) {
        return f != null && (f.excludeTimeouts || liveMode(f) != null);
    }

    /** Appends the timeout predicate, then the live/turn-based one, each only when set. */
    public static void append(StringBuilder where, List<Object> params,
                              PositionStatsRequest.Filters f) {
        if (f == null) return;
        if (f.excludeTimeouts) {
            where.append(" and (g.status is null or g.status <> ?)");
            params.add(STATUS_TIMEOUT);
        }
        String mode = liveMode(f);
        if (LIVE.equals(mode)) {
            where.append(" and (g.site_id = ? and g.gid < ?)");
            params.add(Integer.valueOf(PENTE_ORG_SITE_ID));
            params.add(Long.valueOf(TB_GID_START));
        } else if (TURN_BASED.equals(mode)) {
            where.append(" and ((g.site_id = ? and g.gid >= ?) or g.site_id <> ?)");
            params.add(Integer.valueOf(PENTE_ORG_SITE_ID));
            params.add(Long.valueOf(TB_GID_START));
            params.add(Integer.valueOf(PENTE_ORG_SITE_ID));
        }
    }
}
