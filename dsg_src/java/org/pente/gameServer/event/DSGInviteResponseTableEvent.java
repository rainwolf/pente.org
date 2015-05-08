package org.pente.gameServer.event;

public class DSGInviteResponseTableEvent extends AbstractDSGTableEvent {

	private String toPlayer;
	private String responseText;
	private boolean accept;
	private boolean ignore;
	
	public DSGInviteResponseTableEvent(String player, int table,
		String toPlayer, String responseText, boolean accept, boolean ignore) {
		super(player, table);

		this.toPlayer = toPlayer;
		this.responseText = responseText;
		this.accept = accept;
		this.ignore = ignore;
	}

	public boolean getAccept() {
		return accept;
	}
	public boolean getIgnore() {
		return ignore;
	}

	public String getResponseText() {
		return responseText;
	}

	public String getToPlayer() {
		return toPlayer;
	}
	
	public String toString() {
        return "invite response " + toPlayer + ", " + responseText + " " + 
        	super.toString();
	}
}
