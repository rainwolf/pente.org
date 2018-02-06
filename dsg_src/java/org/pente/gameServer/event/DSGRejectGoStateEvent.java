package org.pente.gameServer.event;

public class DSGRejectGoStateEvent extends AbstractDSGTableEvent {

	public DSGRejectGoStateEvent() {		
	}

	public DSGRejectGoStateEvent(String player, int table) {
		super(player, table);
	}
	

	public String toString() {
		return "DSGRejectGoStateEvent " + super.toString();
	}
}

