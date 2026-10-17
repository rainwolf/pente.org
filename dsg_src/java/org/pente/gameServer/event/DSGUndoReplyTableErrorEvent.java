package org.pente.gameServer.event;

public class DSGUndoReplyTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = -9157606028829220307L;

    private boolean accepted;

    public DSGUndoReplyTableErrorEvent() {
        super();
    }

    public DSGUndoReplyTableErrorEvent(String player, int table, boolean accepted, int error) {
        super(player, table, error);

        this.accepted = accepted;
    }

    public boolean getAccepted() {
        return accepted;
    }

    public String toString() {
        return "undo granted: " + (accepted ? "yes " : "no ") + super.toString();
    }
}

