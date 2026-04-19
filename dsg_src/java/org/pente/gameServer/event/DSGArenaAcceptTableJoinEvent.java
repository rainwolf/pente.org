package org.pente.gameServer.event;

public class DSGArenaAcceptTableJoinEvent extends AbstractDSGTableEvent {

    String playerToAccept;

    public DSGArenaAcceptTableJoinEvent(String player, int table, String playerToAccept) {
        super(player, table);
        this.playerToAccept = playerToAccept;
    }

    public String getPlayerToAccept() {
        return playerToAccept;
    }

    public String toString() {
        return "Arena reject (" + playerToAccept + ") join " + super.toString();
    }
}

