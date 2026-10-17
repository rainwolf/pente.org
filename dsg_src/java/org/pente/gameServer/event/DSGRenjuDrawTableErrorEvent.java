package org.pente.gameServer.event;

public class DSGRenjuDrawTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = -4520069179104949914L;

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
