package org.pente.gameServer.event;

public class DSGUndoReplyTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 541513388705199440L;

    private boolean accepted;

    public DSGUndoReplyTableEvent() {
        super();
    }

    public DSGUndoReplyTableEvent(String player, int table, boolean accepted) {
        super(player, table);

        this.accepted = accepted;
    }

    public boolean getAccepted() {
        return accepted;
    }

    public String toString() {
        return "undo granted: " + (accepted ? "yes " : "no ") + super.toString();
    }
}

