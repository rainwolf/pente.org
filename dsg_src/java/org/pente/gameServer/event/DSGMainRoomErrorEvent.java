package org.pente.gameServer.event;

public interface DSGMainRoomErrorEvent extends DSGMainRoomEvent {

	public static final int ALREADY_IN_MAIN_ROOM = 1;
	public static final int NOT_IN_MAIN_ROOM = 2;

	public static final int UNKNOWN = 99;

	public void setError(int error);
	public int getError();
}

