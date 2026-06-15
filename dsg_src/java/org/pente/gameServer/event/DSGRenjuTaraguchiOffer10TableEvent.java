package org.pente.gameServer.event;

public class DSGRenjuTaraguchiOffer10TableEvent extends AbstractDSGTableEvent {

    private int[] moves;

    public DSGRenjuTaraguchiOffer10TableEvent() {
        super();
    }

    /**
     * Branch B: the ten 5th-move candidates offered by black. Implies "declined
     * the move-4 swap + chose Branch B". Also re-sent (player==null) to a client
     * that joins while selection is still pending.
     */
    public DSGRenjuTaraguchiOffer10TableEvent(String player, int table, int[] moves) {
        super(player, table);
        this.moves = moves;
    }

    public void setMoves(int[] moves) {
        this.moves = moves;
    }

    public int[] getMoves() {
        return moves;
    }

    public String toString() {
        return "Renju Taraguchi offer10 " +
                (moves == null ? 0 : moves.length) + " moves " + super.toString();
    }
}
