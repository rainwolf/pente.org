package org.pente.gameServer.event;

public class DSGTextMainRoomEvent extends AbstractDSGEvent implements DSGMainRoomEvent {
    private static final long serialVersionUID = 5185422425650761318L;

    private String player;
    private String text;

    public DSGTextMainRoomEvent() {
    }

    public DSGTextMainRoomEvent(String player, String text) {

        setPlayer(player);
        setText(text);
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


    public String toString() {
        return "text " + getPlayer() + " \"" + getText() + "\"";
    }
}

