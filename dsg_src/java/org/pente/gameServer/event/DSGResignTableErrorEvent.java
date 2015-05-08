package org.pente.gameServer.event;

public class DSGResignTableErrorEvent extends AbstractDSGTableErrorEvent {

    public DSGResignTableErrorEvent() {
        super();
    }

	public DSGResignTableErrorEvent(String player, int table, int error) {
		super(player, table, error);
	}
	
	public String toString() {
		return "resign " + super.toString();
	}
}

