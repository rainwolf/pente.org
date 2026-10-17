package org.pente.gameServer.event;

public class DSGStandTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 3305421786024016802L;

    public DSGStandTableErrorEvent() {
    }

    public DSGStandTableErrorEvent(String player, int tableNum, int error) {
        super(player, tableNum, error);
    }

    public String toString() {
        return "stand " + super.toString();
    }
}

