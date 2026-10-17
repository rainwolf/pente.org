package org.pente.puzzle;

import java.util.*;

public class Puzzle {

    private int game;
    private String creator;
    private Date creationDate;

    private int winInMoves;
    private int whiteCaps;
    private int blackCaps;

    private int views;
    private int solved;
    private int solveFailures;

    public List<Integer> whiteMoves;
    private List<Integer> blackMoves;

    private Move responseRoot;

    public int getGame() {
        return game;
    }

    public void setGame(int game) {
        this.game = game;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getWinInMoves() {
        return winInMoves;
    }

    public void setWinInMoves(int winInMoves) {
        this.winInMoves = winInMoves;
    }

    public int getWhiteCaps() {
        return whiteCaps;
    }

    public void setWhiteCaps(int whiteCaps) {
        this.whiteCaps = whiteCaps;
    }

    public int getBlackCaps() {
        return blackCaps;
    }

    public void setBlackCaps(int blackCaps) {
        this.blackCaps = blackCaps;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public int getSolved() {
        return solved;
    }

    public void setSolved(int solved) {
        this.solved = solved;
    }

    public int getSolveFailures() {
        return solveFailures;
    }

    public void setSolveFailures(int solveFailures) {
        this.solveFailures = solveFailures;
    }

    public List<Integer> getWhiteMoves() {
        return whiteMoves;
    }

    public void setWhiteMoves(int[] wm) {
        whiteMoves = new ArrayList<Integer>(wm.length);
        for (int m : wm) {
            whiteMoves.add(m);
        }
    }

    public void setWhiteMoves(List<Integer> whiteMoves) {
        this.whiteMoves = whiteMoves;
    }

    public List<Integer> getBlackMoves() {
        return blackMoves;
    }

    public void setBlackMoves(int[] bm) {
        blackMoves = new ArrayList<Integer>(bm.length);
        for (int m : bm) {
            blackMoves.add(m);
        }
    }

    public Move getResponseRoot() {
        return responseRoot;
    }

    public void setResponseRoot(Move responseRoot) {
        this.responseRoot = responseRoot;
    }
}
