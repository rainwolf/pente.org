package org.pente.gameServer.mobile;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.turnBased.TBGame;
import org.pente.gameServer.tourney.Tourney;

import java.util.List;

/**
 * Shared utility methods for mobile JSON serializers.
 */
public class MobileJsonHelper {

    /** Subscriber's RGB color, or 0 for non-subscribers. */
    public static int playerColor(DSGPlayerData d) {
        return d.hasPlayerDonated() ? d.getNameColorRGB() : 0;
    }

    /**
     * Subscriber's RGB color with non-zero alpha guard, or 0 for non-subscribers.
     * Used wherever 0 would be ambiguous (e.g. invitation/game opponent colors).
     */
    public static int playerColorNonZero(DSGPlayerData d) {
        if (!d.hasPlayerDonated()) return 0;
        int color = d.getNameColorRGB();
        return (color & 0xFFFFFF) == 0 ? ((255 << 24) + 1) : color;
    }

    /** Rating from game data, defaulting to 1600 if null or unplayed. */
    public static int playerRating(DSGPlayerGameData gameData) {
        return gameData != null ? (int) Math.round(gameData.getRating()) : 1600;
    }

    /** Builds the "Rated" / "KotH" / "Tournament" / "Not Rated" label. */
    public static String ratedStr(boolean koth, boolean tourney, boolean rated) {
        if (koth) return "KotH";
        if (tourney) return "Tournament";
        if (rated) return "Rated";
        return "Not Rated";
    }

    /** Returns true if this game belongs to any of the given active tournaments. */
    public static boolean isTournamentGame(long eventId, List<Tourney> currentTournies) {
        for (Tourney t : currentTournies) {
            if (t.getEventID() == eventId) return true;
        }
        return false;
    }

    /** Returns the color string ("white (p1)", "black (p2)", etc.) for a turn-based game. */
    public static String gameColor(TBGame g, long myPID) {
        boolean go = isGoGame(g.getGame());
        if (g.getPlayer1Pid() == myPID) {
            return (!go) ? "white (p1)" : "black (p1)";
        } else {
            return (go) ? "white (p2)" : "black (p2)";
        }
    }

    public static boolean isGoGame(int game) {
        return game == org.pente.game.GridStateFactory.TB_GO
            || game == org.pente.game.GridStateFactory.TB_GO9
            || game == org.pente.game.GridStateFactory.TB_GO13;
    }
}