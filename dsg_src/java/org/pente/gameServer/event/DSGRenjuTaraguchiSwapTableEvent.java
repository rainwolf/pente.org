package org.pente.gameServer.event;

public class DSGRenjuTaraguchiSwapTableEvent extends AbstractDSGTableEvent {

    private boolean swap;
    private int move;

    public DSGRenjuTaraguchiSwapTableEvent() {
        super();
    }

    /**
     * A Renju Taraguchi-10 swap window (after moves 1-4, and the move-5 window).
     * swap=true  -> take the other side; no stone placed (move ignored).
     * swap=false -> decline; place the next opening stone (move): moves 2-4 in
     *               their box, or move 5 in the 9x9 = Branch A. At the move-5
     *               window a decline carries no stone (move 5 already on board).
     */
    public DSGRenjuTaraguchiSwapTableEvent(String player, int table, boolean swap, int move) {
        super(player, table);
        this.swap = swap;
        this.move = move;
    }

    public void setSwap(boolean swap) {
        this.swap = swap;
    }

    public boolean isSwap() {
        return swap;
    }

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public String toString() {
        return "Renju Taraguchi swap=" + swap + " move=" + move + " " + super.toString();
    }
}
