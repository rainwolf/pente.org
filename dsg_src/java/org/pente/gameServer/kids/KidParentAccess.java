package org.pente.gameServer.kids;

import org.apache.log4j.Category;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;

/**
 * The one place that decides whether a viewer is the parent of a player in a
 * game. Read-only access: a parent who is not a player gets no write path.
 */
public final class KidParentAccess {

    public static final String REQUEST_ATTR = "kidParentViewer";

    private static Category log4j = Category.getInstance(KidParentAccess.class.getName());

    private KidParentAccess() {
    }

    private static boolean parentOf(DSGPlayerData viewer, DSGPlayerData p) {
        return p != null && p.getParentPid() != null
                && p.getParentPid().longValue() == viewer.getPlayerID();
    }

    public static boolean isParentOf(DSGPlayerData viewer, DSGPlayerData p1, DSGPlayerData p2) {
        if (viewer == null) {
            return false;
        }
        return parentOf(viewer, p1) || parentOf(viewer, p2);
    }

    /** Loads both players (pid 0 means none); any failure is logged and denies access. */
    public static boolean isParentOf(DSGPlayerData viewer, DSGPlayerStorer players,
                                     long p1Pid, long p2Pid) {
        try {
            if (viewer == null) {
                return false;
            }
            DSGPlayerData p1 = p1Pid == 0 ? null : players.loadPlayer(p1Pid);
            DSGPlayerData p2 = p2Pid == 0 ? null : players.loadPlayer(p2Pid);
            return isParentOf(viewer, p1, p2);
        } catch (Throwable t) {
            log4j.error("Kid parent access check failed", t);
            return false;
        }
    }

    /**
     * Parent read access applies to turn-based games only (they carry the
     * messages the parent may read). Private live games stay hidden.
     */
    public static boolean canReadGame(DSGPlayerData viewer, DSGPlayerStorer players,
                                      long p1Pid, long p2Pid, boolean tbGame) {
        return tbGame && isParentOf(viewer, players, p1Pid, p2Pid);
    }

    /**
     * The pid whose private games the viewer may see in profile's game history.
     * The parent exemption applies only when listing turn-based games.
     */
    public static long historyRequestorPid(DSGPlayerData viewer, DSGPlayerData profile,
                                           boolean tbGame) {
        if (tbGame && isParentOf(viewer, profile, null)) {
            return profile.getPlayerID();
        }
        return viewer.getPlayerID();
    }
}
