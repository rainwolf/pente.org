package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGJoinMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {

	private String player;
    private DSGPlayerData dsgPlayerData;

	public DSGJoinMainRoomEvent() {		
	}

	public DSGJoinMainRoomEvent(String player, DSGPlayerData dsgPlayerData) {
		setPlayer(player);
		setDSGPlayerData(dsgPlayerData);
	}
	
	public void setPlayer(String player) {
		this.player = player;
	}
	public String getPlayer() {
		return player;
	}
	
    public DSGPlayerData getDSGPlayerData() {
        return dsgPlayerData;
    }
    public void setDSGPlayerData(DSGPlayerData dsgPlayerData) {
        this.dsgPlayerData = dsgPlayerData;
    }

	public String toString() {
		return "join " + getPlayer() + " main room";
	}
}

