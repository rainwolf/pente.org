package org.pente.gameServer.event;

public class DSGRenjuRejectDrawTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -4345640525852890250L;

    public DSGRenjuRejectDrawTableEvent() {
        super();
    }

    public DSGRenjuRejectDrawTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "renju reject draw " + super.toString();
    }
}
