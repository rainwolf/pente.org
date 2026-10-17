package org.pente.gameServer.event;

public class DSGStandTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 7384416516738930758L;

    public DSGStandTableEvent() {
    }

    public DSGStandTableEvent(String player, int tableNum) {
        super(player, tableNum);
    }

    public String toString() {
        return "stand " + super.toString();
    }
}

