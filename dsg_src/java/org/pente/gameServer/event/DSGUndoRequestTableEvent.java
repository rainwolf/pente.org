package org.pente.gameServer.event;

public class DSGUndoRequestTableEvent extends AbstractDSGTableEvent {

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

