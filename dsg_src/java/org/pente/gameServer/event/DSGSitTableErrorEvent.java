package org.pente.gameServer.event;

public class DSGSitTableErrorEvent extends AbstractDSGTableErrorEvent {

    private int seat;

    public DSGSitTableErrorEvent() {
    }

    public DSGSitTableErrorEvent(String player, int table, int seat, int error) {
        super(player, table, error);

        setSeat(seat);
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public int getSeat() {
        return seat;
    }

    public String toString() {
        return "sit " + getSeat() + " " + super.toString();
    }
}