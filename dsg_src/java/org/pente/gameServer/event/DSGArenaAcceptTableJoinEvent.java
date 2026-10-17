package org.pente.gameServer.event;

public class DSGArenaAcceptTableJoinEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -4372940674250105995L;

    String playerToAccept;

    public DSGArenaAcceptTableJoinEvent(String player, int table, String playerToAccept) {
        super(player, table);
        this.playerToAccept = playerToAccept;
    }

    public String getPlayerToAccept() {
        return playerToAccept;
    }

    public String toString() {
        return "Arena accept (" + playerToAccept + ") join " + super.toString();
    }
}

