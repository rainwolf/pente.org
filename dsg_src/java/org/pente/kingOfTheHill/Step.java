package org.pente.kingOfTheHill;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by waliedothman on 25/06/16.
 */
public class Step {

    private List<Long> players;

    public List<Long> getPlayers() {
        return players;
    }

    public void setPlayers(List<Long> players) {
        this.players = players;
    }

    public void addPlayer(long playerID) {
        if (players == null) {
            players = new ArrayList<Long>();
        }
        players.add(playerID);
    }
    public boolean removePlayer(long playerID) {
        if (players != null) {
            return players.remove(playerID);
        }
        return false;
    }

    public boolean hasPlayer(long pid) {
        for (long playerId : players) {
            if (playerId == pid) {
                return true;
            }
        }
        return false;
    }

}
