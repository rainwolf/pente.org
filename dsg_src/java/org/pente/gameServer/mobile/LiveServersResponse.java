package org.pente.gameServer.mobile;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.client.web.WhosOnlineRoom;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.ServerData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serializes the live server list with their current player rosters (liveServers.jsp).
 * Gson serializes {@code List<ServerEntry>} directly to a JSON array.
 */
public class LiveServersResponse {

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

    public static class ServerEntry {
        public final int port;
        public final String name;
        public final int playerCount;
        public final List<PlayerEntry> players;

        public ServerEntry(ServerData data, List<DSGPlayerData> serverPlayers) {
            this.port = data.getPort();
            this.name = data.getName();
            this.playerCount = serverPlayers.size();
            this.players = new ArrayList<>();
            for (DSGPlayerData d : serverPlayers) {
                this.players.add(new PlayerEntry(d));
            }
        }
    }

    /**
     * @param servers all server descriptors from {@code Resources.getServerData()}
     * @param rooms   current who's-online rooms to match against server names
     */
    public static List<ServerEntry> build(Iterable<ServerData> servers, List<WhosOnlineRoom> rooms) {
        List<ServerEntry> result = new ArrayList<>();
        for (ServerData data : servers) {
            List<DSGPlayerData> serverPlayers = Collections.emptyList();
            for (WhosOnlineRoom room : rooms) {
                if (data.getName().equals(room.getName())) {
                    serverPlayers = room.getPlayers();
                    break;
                }
            }
            result.add(new ServerEntry(data, serverPlayers));
        }
        return result;
    }
}