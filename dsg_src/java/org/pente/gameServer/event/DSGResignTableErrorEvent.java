package org.pente.gameServer.event;

public class DSGResignTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 3587968317072955307L;

    public DSGResignTableErrorEvent() {
        super();
    }

    public DSGResignTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "resign " + super.toString();
    }
}

