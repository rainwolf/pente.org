package org.pente.gameServer.event;

public class DSGTimeUpTableEvent extends AbstractDSGTableEvent {

	public DSGTimeUpTableEvent() {
		super();
	}

	public DSGTimeUpTableEvent(String player, int table) {
		super(player, table);
	}

    public String toString() {
        return "time up " + super.toString();
    }
}
