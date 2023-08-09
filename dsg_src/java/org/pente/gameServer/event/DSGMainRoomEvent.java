package org.pente.gameServer.event;

public interface DSGMainRoomEvent extends DSGEvent {

    public void setPlayer(String player);

    public String getPlayer();
}

