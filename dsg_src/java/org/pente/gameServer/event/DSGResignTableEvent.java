package org.pente.gameServer.event;

public class DSGResignTableEvent extends AbstractDSGTableEvent {

    public DSGResignTableEvent() {
        super();
    }

    public DSGResignTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "resign " + super.toString();
    }
}

