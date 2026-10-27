package org.pente.gameServer.event;

public class DSGTombstoneSkipDeadTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 6184730592847163025L;

    boolean silent = false;

    public DSGTombstoneSkipDeadTableEvent() {
    }

    public DSGTombstoneSkipDeadTableEvent(String player, int table, boolean silent) {
        super(player, table);
        this.silent = silent;
    }

    public boolean isSilent() {
        return silent;
    }

    public String toString() {
        return "tombstone skip dead stone " + super.toString();
    }
}
