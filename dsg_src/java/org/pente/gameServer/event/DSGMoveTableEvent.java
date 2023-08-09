package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGMoveTableEvent extends AbstractDSGTableEvent {

    private static final GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);

    private int move;
    private int moves[];

    public DSGMoveTableEvent() {
        super();
    }

    public DSGMoveTableEvent(String player, int table, int move) {
        super(player, table);
        setMove(move);
    }

    /**
     * used to send ALL moves in a game to player just joining a table
     */
    public DSGMoveTableEvent(int table, int moves[]) {
        super(null, table);
        this.moves = moves;
    }

    public void setMove(int move) {
        this.move = move;
        moves = new int[]{move};
    }

    public int getNumMoves() {
        if (moves != null) return moves.length;
        return 1;
    }

    public int getMove() {
        return move;
    }

    public int[] getMoves() {
        return moves;
    }

    public String toString() {
        if (getNumMoves() == 1) {
            return "move " + coordinates.getCoordinate(move) + " " + super.toString();
        } else {
            String r = "moves ";
            for (int i = 0; i < moves.length; i++) {
                r += coordinates.getCoordinate(moves[i]);
                if (i < moves.length - 1) r += ",";
            }
            r += " " + super.toString();
            return r;
        }
    }

}

