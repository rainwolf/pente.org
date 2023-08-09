package org.pente.gameServer.server;

import org.pente.gameServer.core.*;

public class ActivityTableData {

    private long serverId;
    private int tableNum;
    private long hashCode;
    private int moves[];
    private boolean rated;

    private static final GridCoordinates gridCoordinates =
            new AlphaNumericGridCoordinates(19, 19);

    public ActivityTableData(long serverId, int tableNum, long hashCode,
                             int moves[], boolean rated) {
        this.serverId = serverId;
        this.tableNum = tableNum;
        this.hashCode = hashCode;
        this.moves = moves;
        this.rated = rated;
    }

    public long getServerId() {
        return serverId;
    }

    public int getTableNum() {
        return tableNum;
    }

    public long getHashCode() {
        return hashCode;
    }

    public void setHashCode(long hashCode) {
        this.hashCode = hashCode;
    }

    public String getMoves() {
        String m = "";
        if (moves == null) return m;
        for (int i = 0; i < moves.length; i++) {
            m += gridCoordinates.getCoordinate(moves[i]);
            if (i != moves.length - 1) {
                m += ",";
            }
        }
        return m;
    }

    public void setMoves(int moves[]) {
        this.moves = moves;
    }

    public int getNumMoves() {
        return moves.length;
    }

    public boolean isRated() {
        return rated;
    }

    public String toString() {
        String s = "[";
        if (serverId != -1) {
            s += serverId + ":" + tableNum + " ";
        }
        s += hashCode + " " + getMoves();
        if (serverId != -1) {
            s += " " + (rated ? "r" : "ur");
        }
        s += "]";

        return s;
    }
}
