package org.pente.gameServer.event;

public class DSGOwnerTableEvent extends AbstractDSGTableEvent {

    public DSGOwnerTableEvent() {
        super();
    }

    public DSGOwnerTableEvent(String player, int table) {
        super(player, table);
        if (player == null) {
            this.setPlayer("");
        }
    }

    public String toString() {
        return "set owner " + super.toString();
    }
}

