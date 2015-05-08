package org.pente.tree;

import java.awt.Point;

import org.pente.game.*;
import org.pente.gameServer.core.*;


public class Utils {
    
    private static GridCoordinates coordinates = 
        new AlphaNumericGridCoordinates(19, 19);

    public static String printMove(int move) {
        return coordinates.getCoordinate(move);
    }
    public static int getMove(String move) {
        Point p = coordinates.getPoint(move);
        return (18 - p.y) * 19 + p.x;
    }

    public static PenteState createState(String moves[]) {
        FastPenteStateZobrist f = new FastPenteStateZobrist();
        for (int i = 0; i < moves.length; i++) {
            f.addMove(getMove(moves[i]));
        }
        return f;
    }
    
    public static String printState(PenteState penteState) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < penteState.getNumMoves(); i++) {
            buf.append(coordinates.getCoordinate(penteState.getMove(i)));
            if (i != penteState.getNumMoves() - 1) buf.append(", ");
        }
        return buf.toString();
    }
}
