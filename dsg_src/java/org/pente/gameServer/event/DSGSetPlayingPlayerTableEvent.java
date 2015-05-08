package org.pente.gameServer.event;

public class DSGSetPlayingPlayerTableEvent extends AbstractDSGTableEvent {

    private int seat;

	public DSGSetPlayingPlayerTableEvent() {
		super();
	}

	public DSGSetPlayingPlayerTableEvent(String player, int table, int seat) {
		super(player, table);
        
        this.seat = seat;
	}

    public int getSeat() {
        return seat;
    }

    public String toString() {
        return "set playing player, seat " + seat + " " + super.toString();
    }
}
