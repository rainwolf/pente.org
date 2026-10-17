package org.pente.gameServer.event;

public class DSGPlayTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 5782787377884348521L;

    public DSGPlayTableErrorEvent() {
    }

    public DSGPlayTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "play " + super.toString();
    }
}

