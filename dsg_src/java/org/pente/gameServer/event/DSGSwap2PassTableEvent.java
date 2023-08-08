package org.pente.gameServer.event;

public class DSGSwap2PassTableEvent extends AbstractDSGTableEvent {

    boolean silent = false;

	public DSGSwap2PassTableEvent() {
	}

	public DSGSwap2PassTableEvent(String player, int table, boolean silent) {
		super(player, table);
        this.silent = silent;
	}
 
    public boolean isSilent() {
        return silent;
    }
 
	public String toString() {
		return ("Pass the Swap2 Choice ") +
            super.toString();
	}
}

