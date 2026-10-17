package org.pente.gameServer.event;

public class DSGUndoRequestTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 16796375214522846L;

    public DSGUndoRequestTableErrorEvent() {
        super();
    }

    public DSGUndoRequestTableErrorEvent(String player, int table, int error) {
        super(player, table, error);
    }

    public String toString() {
        return "undo request " + super.toString();
    }
}

