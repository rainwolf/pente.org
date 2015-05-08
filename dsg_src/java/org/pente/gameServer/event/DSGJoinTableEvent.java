package org.pente.gameServer.event;

public class DSGJoinTableEvent extends AbstractDSGTableEvent {

    public static final int CREATE_NEW_TABLE = -1;
    
	public DSGJoinTableEvent() {		
	}

	public DSGJoinTableEvent(String player, int table) {
		super(player, table);
	}

	public String toString() {
		if (getTable() == CREATE_NEW_TABLE) {
            return "create new table";
        }
        else {
            return "join " + super.toString();
        } 
	}
}

