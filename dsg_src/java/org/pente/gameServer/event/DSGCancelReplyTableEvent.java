package org.pente.gameServer.event;

public class DSGCancelReplyTableEvent extends AbstractDSGTableEvent {

    private boolean accepted;

    public DSGCancelReplyTableEvent() {
        super();
    }

    public DSGCancelReplyTableEvent(String player, int table, boolean accepted) {
        super(player, table);

        this.accepted = accepted;
    }

    public boolean getAccepted() {
        return accepted;
    }

    public String toString() {
        return "cancel granted: " + (accepted ? "yes " : "no ") + super.toString();
    }
}

