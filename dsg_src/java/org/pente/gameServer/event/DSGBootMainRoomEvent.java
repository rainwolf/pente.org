package org.pente.gameServer.event;


public class DSGBootMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {
    private static final long serialVersionUID = 6752928894668823816L;

    private String player;
    private String toBoot;
    private int minutes;

    public DSGBootMainRoomEvent() {
    }

    public DSGBootMainRoomEvent(String player, String toBoot, int minutes) {
        setPlayer(player);
        this.toBoot = toBoot;
        this.minutes = minutes;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getPlayer() {
        return player;
    }

    public String getPlayerToBoot() {
        return toBoot;
    }

    public int getBootMinutes() {
        return minutes;
    }

    public String toString() {
        return "boot main " + toBoot + ", " + minutes + " " + super.toString();
    }
}

