package org.pente.gameServer.event;

public class DSGExitTableEvent extends AbstractDSGTableEvent {

	private boolean forced;
    private boolean booted;

    public DSGExitTableEvent() {
    }

    public DSGExitTableEvent(String player, int table, boolean forced,
        boolean booted) {

        super(player, table);
        
        setForced(forced);
        setBooted(booted);
    }
    
    public void setForced(boolean forced) {
    	this.forced = forced;
    }
    public boolean getForced() {
    	return forced;
    }
    public void setBooted(boolean booted) {
        this.booted = booted;
    }
    public boolean wasBooted() {
        return booted;
    }
    
    public String toString() {
    	return "exit " + 
            (getForced() ? "forced " : "not forced ") + 
            (wasBooted() ? "booted " : "not booted ") + super.toString();
    }
}

