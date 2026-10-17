package org.pente.gameServer.event;

public class DSGResignTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 6915860943258883744L;

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

