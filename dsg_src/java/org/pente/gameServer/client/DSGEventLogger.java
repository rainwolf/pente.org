package org.pente.gameServer.client;

import org.pente.gameServer.event.*;

public class DSGEventLogger extends DSGEventChainer {

    public DSGEventLogger(DSGEventListener chainedEventListener) {
        super(chainedEventListener);
    }

    public void eventOccurred(DSGEvent dsgEvent) {
    	super.eventOccurred(dsgEvent);
    	
    	System.out.println("out: " + dsgEvent);
    }
}

