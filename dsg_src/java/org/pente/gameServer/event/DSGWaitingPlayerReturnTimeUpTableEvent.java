package org.pente.gameServer.event;

public class DSGWaitingPlayerReturnTimeUpTableEvent extends AbstractDSGTableEvent {

	private int waitingForPlayerToReturnSeqNbr;
	private boolean set;
	
    public DSGWaitingPlayerReturnTimeUpTableEvent() {
        super();
    }

    public DSGWaitingPlayerReturnTimeUpTableEvent(String player, int table, int waitingForPlayerToReturnSeqNbr, boolean set) {
        super(player, table);
        
        this.waitingForPlayerToReturnSeqNbr = waitingForPlayerToReturnSeqNbr;
        this.set = set;
    }

	public int getWaitingForPlayerToReturnSeqNbr() {
		return waitingForPlayerToReturnSeqNbr;
	}
	public boolean isSet() {
		return set;
	}
	
	public String toString() {
		return "waiting for player to return timeup, sequence number " + waitingForPlayerToReturnSeqNbr + ", set = " + set + " " + super.toString();
	}
}

