package org.pente.gameServer.event;

public class DSGTombstoneSkipDeadTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = -7302915846201938475L;

    public DSGTombstoneSkipDeadTableErrorEvent() {
        super();
    }

    public DSGTombstoneSkipDeadTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "tombstone skip dead stone error " + super.toString();
    }
}
