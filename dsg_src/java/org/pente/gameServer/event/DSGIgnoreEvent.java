package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGIgnoreEvent extends AbstractDSGEvent {
    private static final long serialVersionUID = -3392204318905559942L;

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
            for (DSGIgnoreData player : players) {
                ids += player + " ";
            }
            return "ignore " + ids;
        } else {
            return "ignore pid: " + pid;
        }
    }
}
