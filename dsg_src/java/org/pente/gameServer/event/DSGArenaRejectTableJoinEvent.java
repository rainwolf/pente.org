package org.pente.gameServer.event;

public class DSGArenaRejectTableJoinEvent extends AbstractDSGTableEvent {

    String playerToReject, message;

    public DSGArenaRejectTableJoinEvent(String player, int table, String playerToReject, String message) {
        super(player, table);
        this.playerToReject = playerToReject;
        this.message = message;
    }

    public String toString() {
        return "Arena reject (" + playerToReject + ") join " + super.toString() + " with message: " + message;
    }

    public String getPlayerToReject() {
        return playerToReject;
    }

    public String getMessage() {
        return message;
    }

}

