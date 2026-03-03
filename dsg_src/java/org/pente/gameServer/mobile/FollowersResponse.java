package org.pente.gameServer.mobile;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes a player's followers and following lists (followers.jsp).
 */
public class FollowersResponse {

    public static class PlayerEntry {
        public final String name;
        public final int donated;
        public final int color;
        public final int tourneyWinner;
        public final int rating;

        public PlayerEntry(DSGPlayerData d, int game) {
            DSGPlayerGameData gameData = d.getPlayerGameData(game);
            this.name = d.getName();
            this.donated = d.hasPlayerDonated() ? 1 : 0;
            this.color = MobileJsonHelper.playerColor(d);
            this.tourneyWinner = d.getTourneyWinner();
            this.rating = gameData != null ? (int) gameData.getRating() : 1600;
        }
    }

    public final List<PlayerEntry> followers;
    public final List<PlayerEntry> following;

    private FollowersResponse(List<PlayerEntry> followers, List<PlayerEntry> following) {
        this.followers = followers;
        this.following = following;
    }

    /**
     * @param followerPids  PIDs of players who follow the current user
     * @param followingPids PIDs of players the current user follows
     * @param storer        player storer for loading player data by PID
     * @param game          game ID to look up per-game rating
     */
    public static FollowersResponse build(List<Long> followerPids, List<Long> followingPids,
                                          DSGPlayerStorer storer, int game) throws DSGPlayerStoreException {
        List<PlayerEntry> followerList = new ArrayList<>();
        for (long pid : followerPids) {
            followerList.add(new PlayerEntry(storer.loadPlayer(pid), game));
        }
        List<PlayerEntry> followingList = new ArrayList<>();
        for (long pid : followingPids) {
            followingList.add(new PlayerEntry(storer.loadPlayer(pid), game));
        }
        return new FollowersResponse(followerList, followingList);
    }
}