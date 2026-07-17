package org.pente.gameServer.event;

public class DSGRenjuRejectDrawTableEvent extends AbstractDSGTableEvent {

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
