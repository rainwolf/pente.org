package org.pente.gameServer.event;

public class DSGArenaRequestJoinTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 3914109241639785321L;

    public DSGArenaRequestJoinTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "Arena request join " + super.toString();
    }
}

