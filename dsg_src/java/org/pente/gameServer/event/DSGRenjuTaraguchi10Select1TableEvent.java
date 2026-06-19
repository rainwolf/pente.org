package org.pente.gameServer.event;

public class DSGRenjuTaraguchi10Select1TableEvent extends AbstractDSGTableEvent {

    private int move;

    public DSGRenjuTaraguchi10Select1TableEvent() {
        super();
    }

    /** The other player picks one of the ten offered candidates as move 5. */
    public DSGRenjuTaraguchi10Select1TableEvent(String player, int table, int move) {
        super(player, table);
        this.move = move;
    }

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public String toString() {
        return "Renju Taraguchi select1 move=" + move + " " + super.toString();
    }
}
