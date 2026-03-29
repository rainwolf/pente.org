package org.pente.kingOfTheHill;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by waliedothman on 25/06/16.
 */
public class Step implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ArrayList<Player> players;

    public Step() {
        players = new ArrayList<Player>();
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(ArrayList<Player> players) {
        this.players = players;
    }

    public void addPlayer(long playerID) {
        if (players == null) {
            players = new ArrayList<Player>();
        }
        Player player = new Player(playerID, new Date());
        players.add(player);
    }

    public void addPlayer(Player player) {
        if (players == null) {
            players = new ArrayList<Player>();
        }
        players.add(player);
    }

    public boolean removePlayer(long playerID) {
        if (players != null) {
            for (Iterator<Player> iterator = players.iterator(); iterator.hasNext(); ) {
                Player player = iterator.next();
                if (player.getPid() == playerID) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasPlayer(long pid) {
        for (Player player : players) {
            if (player.getPid() == pid) {
                return true;
            }
        }
        return false;
    }

    public int getNumPlayers() {
        if (players != null) {
            return players.size();
        }
        return 0;
    }

}
