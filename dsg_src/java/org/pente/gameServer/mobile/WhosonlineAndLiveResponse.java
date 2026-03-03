package org.pente.gameServer.mobile;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.client.web.WhosOnlineRoom;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes all live server rooms plus the website room (whosonlineandlive.jsp).
 * Gson serializes {@code List<RoomEntry>} directly to a JSON array.
 */
public class WhosonlineAndLiveResponse {

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

    public static class RoomEntry {
        public final String name;
        public final List<PlayerEntry> players;

        public RoomEntry(WhosOnlineRoom room) {
            this.name = room.getName();
            this.players = new ArrayList<>();
            for (DSGPlayerData d : room.getPlayers()) {
                this.players.add(new PlayerEntry(d));
            }
        }
    }

    /**
     * @param rooms ordered list of rooms; caller is responsible for renaming "web" → "Website"
     *              and ordering (website room last), matching the JSP logic.
     */
    public static List<RoomEntry> build(List<WhosOnlineRoom> rooms) {
        List<RoomEntry> result = new ArrayList<>();
        for (WhosOnlineRoom room : rooms) {
            result.add(new RoomEntry(room));
        }
        return result;
    }
}