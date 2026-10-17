package org.pente.gameServer.event;

public class DSGCancelRequestTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 7507864047018958240L;

    public DSGCancelRequestTableErrorEvent() {
        super();
    }

    public DSGCancelRequestTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "cancel request " + super.toString();
    }
}

