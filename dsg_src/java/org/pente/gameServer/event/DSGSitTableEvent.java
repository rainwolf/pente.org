package org.pente.gameServer.event;

public class DSGSitTableEvent extends AbstractDSGTableEvent {

	private int seat;

	public DSGSitTableEvent() {		
	}

	public DSGSitTableEvent(String player, int table, int seat) {
		super(player, table);
		
		setSeat(seat);
	}
	
	public void setSeat(int seat) {
		this.seat = seat;
	}
	public int getSeat() {
		return seat;
	}
	
	public String toString() {
		return "sit " + getSeat() + " " + super.toString();
	}
}

