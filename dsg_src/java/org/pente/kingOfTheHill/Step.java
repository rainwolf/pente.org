package org.pente.kingOfTheHill;

import java.util.ArrayList;
import java.util.*;
import java.util.Date;

/**
 * Created by waliedothman on 25/06/16.
 */
public class Step {

    private List<Player> players;
    
    public Step() {
        players = new ArrayList<Player>();
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
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
            for (Iterator<Player> iterator = players.iterator(); iterator.hasNext();) {
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
