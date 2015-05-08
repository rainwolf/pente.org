package org.pente.gameServer.event;

public class AbstractDSGEvent implements DSGEvent {

	private long time;
	
	public void setCurrentTime() {
		time = System.currentTimeMillis();
	}

	public long getTime() {
		return time;
	}

}
