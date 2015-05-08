package org.pente.gameServer.event;

public class DSGCancelRequestTableEvent extends AbstractDSGTableEvent {

    public DSGCancelRequestTableEvent() {
        super();
    }

    public DSGCancelRequestTableEvent(String player, int table) {
        super(player, table);
    }

	public String toString() {
		return "cancel request " + super.toString();
	}
}

