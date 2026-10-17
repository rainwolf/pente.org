package org.pente.gameServer.event;

public class DSGUndoRequestTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 5935965273195914116L;

    public DSGUndoRequestTableEvent() {
        super();
    }

    public DSGUndoRequestTableEvent(String player, int table) {
        super(player, table);
    }

    public String toString() {
        return "undo request " + super.toString();
    }
}

