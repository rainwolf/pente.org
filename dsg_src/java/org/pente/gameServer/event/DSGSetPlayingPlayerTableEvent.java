package org.pente.gameServer.event;

public class DSGSetPlayingPlayerTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 3080602166092484076L;

    private int seat;

    public DSGSetPlayingPlayerTableEvent() {
        super();
    }

    public DSGSetPlayingPlayerTableEvent(String player, int table, int seat) {
        super(player, table);

        this.seat = seat;
    }

    public int getSeat() {
        return seat;
    }

    public String toString() {
        return "set playing player, seat " + seat + " " + super.toString();
    }
}
