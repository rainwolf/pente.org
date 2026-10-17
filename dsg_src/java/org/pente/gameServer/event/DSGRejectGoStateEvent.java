package org.pente.gameServer.event;

public class DSGRejectGoStateEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 2443976795937587055L;

    public DSGRejectGoStateEvent() {
    }

    public DSGRejectGoStateEvent(String player, int table) {
        super(player, table);
    }


    public String toString() {
        return "DSGRejectGoStateEvent " + super.toString();
    }
}

