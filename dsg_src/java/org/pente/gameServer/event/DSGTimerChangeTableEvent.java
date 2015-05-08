package org.pente.gameServer.event;

public class DSGTimerChangeTableEvent extends AbstractDSGTableEvent {

	private int minutes;
	private int seconds;

    public DSGTimerChangeTableEvent() {
        super();
    }

    public DSGTimerChangeTableEvent(String player, int table, int minutes, int seconds) {
        super(player, table);
        
        this.minutes = minutes;
        this.seconds = seconds;
    }
    
    public int getMinutes() {
    	return minutes;
    }
    public int getSeconds() {
    	return seconds;
    }
    
    public String toString() {
    	return "timer change " + minutes + ":" + seconds + " " + super.toString();
    }
}

