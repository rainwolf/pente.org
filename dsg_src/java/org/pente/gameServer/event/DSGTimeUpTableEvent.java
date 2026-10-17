package org.pente.gameServer.event;

public class DSGTimeUpTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 6425431511435684649L;

    public DSGTimeUpTableEvent() {
        super();
    }

    public DSGTimeUpTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "time up " + super.toString();
    }
}
