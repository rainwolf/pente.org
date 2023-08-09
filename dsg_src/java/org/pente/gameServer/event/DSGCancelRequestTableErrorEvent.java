package org.pente.gameServer.event;

public class DSGCancelRequestTableErrorEvent extends AbstractDSGTableErrorEvent {

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

