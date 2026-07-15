package org.pente.gameServer.event;

public class DSGRenjuDrawTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGRenjuDrawTableErrorEvent() {
        super();
    }

    public DSGRenjuDrawTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "renju draw error " + super.toString();
    }
}
