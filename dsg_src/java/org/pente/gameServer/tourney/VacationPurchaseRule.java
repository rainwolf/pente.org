package org.pente.gameServer.tourney;

/**
 * Whether a player may buy extra turn-based vacation days: not while in the last round of a current
 * turn-based tourney with incremental time. Shared by gameServer/subscribe.jsp (hideVacation) and the
 * Google Play store (spec 2026-10-11 §4.4).
 */
public final class VacationPurchaseRule {

    private VacationPurchaseRule() {
    }

    public static boolean vacationAllowed(String playerName, TourneyStorer tourneyStorer) throws Throwable {
        for (Tourney current : tourneyStorer.getCurrentTournies()) {
            if (blocksVacation(tourneyStorer.getTourney(current.getEventID()), playerName)) {
                return false;
            }
        }
        return true;
    }

    /** true when t is turn-based with incremental time and playerName is in its last round */
    public static boolean blocksVacation(Tourney t, String playerName) {
        if (t == null || playerName == null || !t.isTurnBased() || t.getIncrementalTime() <= 0 || t.getNumRounds() <= 0) {
            return false;
        }
        for (TourneyPlayerData p : t.getLastRound().getPlayers()) {
            if (playerName.equals(p.getName())) {
                return true;
            }
        }
        return false;
    }
}
