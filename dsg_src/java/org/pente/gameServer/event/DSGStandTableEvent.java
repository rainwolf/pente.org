package org.pente.gameServer.event;

public class DSGStandTableEvent extends AbstractDSGTableEvent {

    public DSGStandTableEvent() {
    }

    public DSGStandTableEvent(String player, int tableNum) {
        super(player, tableNum);
    }

    public String toString() {
        return "stand " + super.toString();
    }
}

