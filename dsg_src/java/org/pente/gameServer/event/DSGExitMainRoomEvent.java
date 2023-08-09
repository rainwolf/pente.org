package org.pente.gameServer.event;

public class DSGExitMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {

    private String player;
    private boolean booted;

    public DSGExitMainRoomEvent() {
        super();
    }

    public DSGExitMainRoomEvent(String player, boolean booted) {
        setPlayer(player);
        this.booted = booted;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getPlayer() {
        return player;
    }

    public boolean wasBooted() {
        return booted;
    }

    public String toString() {
        return "exit main room " + booted + " " + getPlayer();
    }
}

