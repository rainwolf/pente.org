package org.pente.gameServer.event;

public class DSGCancelReplyTableErrorEvent extends AbstractDSGTableErrorEvent {

    private boolean accepted;

    public DSGCancelReplyTableErrorEvent() {
        super();
    }

    public DSGCancelReplyTableErrorEvent(String player, int table, int error, boolean accepted) {
        super(player, table, error);

        this.accepted = accepted;
    }

    public boolean getAccepted() {
        return accepted;
    }

    public String toString() {
        return "cancel granted: " + (accepted ? "yes " : "no ") + super.toString();
    }
}

