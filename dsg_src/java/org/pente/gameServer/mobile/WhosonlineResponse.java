package org.pente.gameServer.mobile;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes the Mobile room player list (whosonline.jsp).
 * Gson serializes {@code List<PlayerEntry>} directly to a JSON array.
 */
public class WhosonlineResponse {

    public static class PlayerEntry {
        public final String name;
        public final int rating;
        public final int color;
        public final int tourneyWinner;
        public final int totalGames;

        public PlayerEntry(DSGPlayerData d) {
            DSGPlayerGameData gameData = d.getPlayerGameData(GridStateFactory.TB_PENTE);
            this.name = d.getName();
            this.rating = MobileJsonHelper.playerRating(gameData);
            this.color = MobileJsonHelper.playerColor(d);
            this.tourneyWinner = d.getTourneyWinner();
            this.totalGames = d.getTotalGames();
        }
    }

    /**
     * @param mobileRoomPlayers players from the "Mobile" {@link org.pente.gameServer.server.WhosOnlineRoom}
     */
    public static List<PlayerEntry> build(List<DSGPlayerData> mobileRoomPlayers) {
        List<PlayerEntry> result = new ArrayList<>();
        for (DSGPlayerData d : mobileRoomPlayers) {
            result.add(new PlayerEntry(d));
        }
        return result;
    }
}