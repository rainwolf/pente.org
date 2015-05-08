package org.pente.gameServer.event;

public class DSGPingEvent extends AbstractDSGEvent {

	private String player;
	private long pingStart;
	
    public DSGPingEvent(String player) {
       	this.player = player;
       	
       	pingStart = System.currentTimeMillis();
    }

	public String getPlayer() {
		return player;
	}
	
	public long getPingStart() {
		return pingStart;
	}
}

