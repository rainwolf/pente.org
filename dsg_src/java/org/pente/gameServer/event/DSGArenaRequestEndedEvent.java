package org.pente.gameServer.event;

/**
 * Server to whoever needs a notice that a request ended or was refused.
 * player is the requester, owner the table owner's name ("" if unknown).
 */
public class DSGArenaRequestEndedEvent extends AbstractDSGTableEvent {

    public static final String DECLINED = "DECLINED";
    public static final String TABLE_FULL = "TABLE_FULL";
    public static final String TABLE_CLOSED = "TABLE_CLOSED";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String BLOCKED = "BLOCKED";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    public static final String GUEST_RATED = "GUEST_RATED";
    public static final String BOOTED = "BOOTED";
    public static final String NO_LONGER_AVAILABLE = "NO_LONGER_AVAILABLE";

    private String owner;
    private String reason;

    public DSGArenaRequestEndedEvent(String player, int table, String owner, String reason) {
        super(player, table);
        this.owner = owner;
        this.reason = reason;
    }

    public String getOwner() {
        return owner;
    }

    public String getReason() {
        return reason;
    }

    public String toString() {
        return "Arena request ended (" + reason + ", owner " + owner + ") " + super.toString();
    }
}
