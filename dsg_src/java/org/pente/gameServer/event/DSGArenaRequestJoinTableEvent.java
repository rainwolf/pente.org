package org.pente.gameServer.event;

public class DSGArenaRequestJoinTableEvent extends AbstractDSGTableEvent {


    public DSGArenaRequestJoinTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "Arena request join " + super.toString();
    }
}

