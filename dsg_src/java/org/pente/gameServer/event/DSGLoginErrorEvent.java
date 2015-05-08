package org.pente.gameServer.event;

public class DSGLoginErrorEvent extends DSGLoginEvent {

	public static final int INVALID_LOGIN = 1;
	public static final int PRIVATE_ROOM = 2;
	
	private int error;

	public DSGLoginErrorEvent() {		
	}

	public DSGLoginErrorEvent(
        String player, String password, int error) {
		
        super(player, password, null);
		
		setError(error);
	}
	
	public void setError(int error) {
		this.error = error;
	}
	public int getError() {
		return error;
	}

	public String toString() {
		return "login " + getPlayer() + " error " + getError();
	}
}

