package org.pente.gameServer.event;

public interface DSGEventSource {

	public void addListener(DSGEventListener dsgEventListener);
	public void removeListener(DSGEventListener dsgEventListener);
}

