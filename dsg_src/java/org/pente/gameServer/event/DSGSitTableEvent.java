package org.pente.gameServer.event;

public class DSGSitTableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = 1688257350336070055L;

    private int seat;

    public DSGSitTableEvent() {
    }

    public DSGSitTableEvent(String player, int table, int seat) {
        super(player, table);

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

