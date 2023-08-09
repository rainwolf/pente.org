package org.pente.gameServer.event;

public class DSGPlayTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGPlayTableErrorEvent() {
    }

    public DSGPlayTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "play " + super.toString();
    }
}

