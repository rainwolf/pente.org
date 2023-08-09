package org.pente.puzzle;

import java.util.*;

import org.pente.gameServer.core.GridPiece;
import org.pente.gameServer.core.SimpleGridPiece;

public class Move {

    private int position;
    private int player;

    // this is used for both solving/challenging
    private List<Move> next;

    // this is used when challenging only i think
    private Move genericMove;

    public Move() {
    }

    public Move(int position, int player) {
        this.position = position;
        this.player = player;
    }

    public int getX() {
        return position % 19;
    }

    public int getY() {
        return 18 - position / 19;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        this.player = player;
    }

    public void addNext(Move m) {
        if (next == null) {
            next = new ArrayList<Move>();
        }
        next.add(m);
    }

    public List<Move> getNext() {
        return next;
    }

    public void setNext(List<Move> next) {
        this.next = next;
    }
//
//	public void addGenericNext(Move m) {
//		if (genericNext == null) {
//			genericNext = new ArrayList<Move>();
//		}
//		genericNext.add(m);
//	}
//	public List<Move> getGenericNext() {
//		return genericNext;
//	}
//	public void setGenericNext(List<Move> genericNext) {
//		this.genericNext = genericNext;
//	}
}
