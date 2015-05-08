package org.pente.gameServer.event;

public class DSGJoinMainRoomErrorEvent extends AbstractDSGEvent implements DSGMainRoomErrorEvent {

	private String player;
	private int error;

	public DSGJoinMainRoomErrorEvent() {		
	}

    public DSGJoinMainRoomErrorEvent(String player, int error) {

        setPlayer(player);
        setError(error);
    }

	public void setPlayer(String player) {
		this.player = player;
	}
	public String getPlayer() {
		return player;
	}
	
	public void setError(int error) {
		this.error = error;
	}
	public int getError() {
		return error;
	}
	
	public String toString() {
		return "join " + getPlayer() + " main room error " + getError();
	}
}