package org.pente.gameServer.event;

public class DSGRenjuAcceptDrawTableEvent extends AbstractDSGTableEvent {

    public DSGRenjuAcceptDrawTableEvent() {
        super();
    }

    public DSGRenjuAcceptDrawTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "renju accept draw " + super.toString();
    }
}
