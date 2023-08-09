package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGIgnoreEvent extends AbstractDSGEvent {

    private long pid;
    private DSGIgnoreData[] players;

    public DSGIgnoreEvent(long pid, DSGIgnoreData[] players) {
        this.pid = pid;
        this.players = players;
    }

    public long getPid() {
        return pid;
    }

    public DSGIgnoreData[] getPlayers() {
        return players;
    }

    public void setPlayers(DSGIgnoreData players[]) {
        this.players = players;
    }

    public String toString() {
        String ids = "";
        if (players != null) {
            for (int i = 0; i < players.length; i++) {
                ids += players[i] + " ";
            }
            return "ignore " + ids;
        } else {
            return "ignore pid: " + pid;
        }
    }
}
