package org.pente.gameServer.event;

public class DSGMoveTableErrorEvent extends AbstractDSGTableErrorEvent {
    private static final long serialVersionUID = 7894513671611040114L;

    private int move;

    public DSGMoveTableErrorEvent() {
        super();
    }

    public DSGMoveTableErrorEvent(String player, int table, int move, int error) {
        super(player, table, error);

        setMove(move);
    }

    public void setMove(int move) {
        this.move = move;
    }

    public int getMove() {
        return move;
    }

    public String toString() {
        return "move " + move + " " + super.toString();
    }
}

