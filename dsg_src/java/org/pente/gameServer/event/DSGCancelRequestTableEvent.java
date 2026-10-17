package org.pente.gameServer.event;

public class DSGCancelRequestTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -6431261668640642004L;

    public DSGCancelRequestTableEvent() {
        super();
    }

    public DSGCancelRequestTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "cancel request " + super.toString();
    }
}

