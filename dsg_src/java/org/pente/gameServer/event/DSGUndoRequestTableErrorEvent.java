package org.pente.gameServer.event;

public class DSGUndoRequestTableErrorEvent extends AbstractDSGTableErrorEvent {

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

