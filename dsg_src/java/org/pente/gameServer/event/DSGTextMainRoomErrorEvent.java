package org.pente.gameServer.event;

public class DSGTextMainRoomErrorEvent extends AbstractDSGEvent implements DSGMainRoomErrorEvent {

	private String player;
	private String text;
	private int error;

	public DSGTextMainRoomErrorEvent() {		
	}

    public DSGTextMainRoomErrorEvent(String player, String text, int error) {
        
        setPlayer(player);
        setText(text);
        setError(error);
    }

	public void setPlayer(String player) {
		this.player = player;
	}
	public String getPlayer() {
		return player;
	}

	public void setText(String text) {
		this.text = text;
	}
	public String getText() {
		return text;
	}
	
	public void setError(int error) {
		this.error = error;
	}
	public int getError() {
		return error;
	}
	
	public String toString() {
		return "text " + getPlayer() + " \"" + getText() + "\" error " + getError();
	}
}

