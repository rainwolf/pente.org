package org.pente.gameServer.event;

public class DSGPlayTableEvent extends AbstractDSGTableEvent {

    public DSGPlayTableEvent() {
    }

    public DSGPlayTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "play " + super.toString();
    }
}

