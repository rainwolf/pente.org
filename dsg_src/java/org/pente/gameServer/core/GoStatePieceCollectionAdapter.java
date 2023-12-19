package org.pente.gameServer.core;

import org.pente.game.Coord;
import org.pente.game.GoState;
import org.pente.game.GridState;
import org.pente.game.SimpleGridState;
import org.pente.gameServer.client.GridBoardComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class GoStatePieceCollectionAdapter extends GoState {

    protected Vector<OrderedPieceCollection> listeners;
    protected Vector<GridPieceAction> gridPieces;

    private GoState shadowState;

    public GoStatePieceCollectionAdapter() {
        this(19, 19);
    }

    /**
     * Create an empty pente state wrapped around the given GridState
     *
     * @param gridState The base GridState to use
     */
    public GoStatePieceCollectionAdapter(GridState gridState) {
        super(gridState);

        listeners = new Vector<>();
        gridPieces = new Vector<>();
    }

    /**
     * Create a pente state with a certain board size
     *
     * @param boardSize The board size
     */
    public GoStatePieceCollectionAdapter(int boardSizeX, int boardSizeY) {
        super(boardSizeX, boardSizeY);

        shadowState = new GoState(boardSizeX, boardSizeY);

        listeners = new Vector<>();
        gridPieces = new Vector<>();
    }


    public void addOrderedPieceCollectionListener(OrderedPieceCollection pieceCollection) {
        listeners.addElement(pieceCollection);
    }

    public void removePieceCollectionListener(OrderedPieceCollection pieceCollection) {
        listeners.removeElement(pieceCollection);
    }

    /**
     * Clears the grid state
     */
    public void clear() {
        super.clear();
        shadowState.clear();
        gridPieces.removeAllElements();
        for (int i = 0; i < listeners.size(); i++) {
            OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(i);
            o.clearPieces();
            updateScoreOnBoard(i);
        }
    }

    /**
     * Add a move for this board
     *
     * @param move An integer representation of a move
     */
    public synchronized void addMove(int move) {

        GridPiece p = null;
        // add grid piece
        p = new SimpleGridPiece();
        if (markStones && move < passMove) {
            int dp = getPosition(move);
            p.setPlayer(3 - dp + 3);
        } else {
            p.setPlayer(getCurrentColor());
        }
        p.setDepth(super.getNumMoves() + 1);
        int x = move % super.getGridSizeX();
        int y = super.getGridSizeY() - move / super.getGridSizeX() - 1;
        p.setX(x);
        p.setY(y);

        super.addMove(move);
        shadowState.addMove(move);

        GridPieceAction a = new GridPieceAction(p, super.getNumMoves(), GridPieceAction.ADD);
        gridPieces.addElement(a);

        for (int i = 0; i < listeners.size(); i++) {
            OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(i);
            o.addPiece(p, super.getNumMoves());
            updateScoreOnBoard(i);
        }
    }

    public synchronized boolean isValidMove(int move, int player) {
        return shadowState.isValidMove(move, player);
    }

    /**
     * Undo the last move
     */
    public synchronized void undoMove() {

        for (int i = 0; i < gridPieces.size(); i++) {
            GridPieceAction a = (GridPieceAction) gridPieces.elementAt(i);
            if (a.getTurn() == super.getNumMoves()) {
                gridPieces.removeElementAt(i);
            }
        }


        shadowState.undoMove();
//        super.undoMove();
        initWithState(shadowState);


        for (int i = 0; i < listeners.size(); i++) {
            OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(i);
            o.undoLastTurn();
            updateScoreOnBoard(i);
        }
    }

    private void updateScoreOnBoard(int i) {
        if (listeners.elementAt(i) instanceof GridBoardComponent) {
            if (markStones) {
                ((GridBoardComponent) listeners.elementAt(i)).setTerritory(getTerritories());
                ((GridBoardComponent) listeners.elementAt(i)).setMessage(getScoreMessage());
            } else {
                ((GridBoardComponent) listeners.elementAt(i)).setTerritory(null);
                ((GridBoardComponent) listeners.elementAt(i)).setMessage(null);
            }
        }
    }

    public synchronized void rejectAndContinue() {
        gridPieces.removeAllElements();
        for (int i = 0; i < listeners.size(); i++) {
            OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(i);
            o.clearPieces();
        }
        super.rejectAndContinue();
        shadowState.rejectAndContinue();
    }


    private synchronized void initWithState(GoState state) {
        this.groupsByPlayerAndID = new HashMap<>(state.getGroupsByPlayerAndID());
        this.stoneGroupIDsByPlayer = new HashMap<>(state.getStoneGroupIDsByPlayer());
        this.koMove = state.getKoMove();
        this.markStones = state.isMarkStones();
        this.evaluateStones = state.isEvaluateStones();

        for (int i = 0; i < 3; ++i) {
            captures[i] = state.captures[i];
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 361; j++) {
                capturedAt[i][j] = state.capturedAt[i][j];
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 361; j++) {
                capturedMoves[i][j] = state.capturedMoves[i][j];
            }
        }

        ((SimpleGridState) gridState).setMoves(shadowState.getGridState().getMovesVector());

        positionHashes = new ArrayList<>(state.getPositionHashes());
        deadStones = new ArrayList<>(state.getDeadStones());
        for (int i = 0; i < getGridSizeX(); i++) {
            for (int j = 0; j < getGridSizeY(); j++) {
                setPosition(i, j, state.getPosition(i, j));
            }
        }
    }

    public int getCurrentColor() {
        if (markStones) {
            return 3;
        }
        return 3 - gridState.getCurrentColor();
    }

    // overridden
    protected synchronized void captureMove(int move, int capturePlayer) {
        super.captureMove(move, capturePlayer);
        removePieces(move);
    }

    private synchronized void removePieces(int move) {
        if (move < passMove) {
            Coord p = convertMove(move);
            int x = p.x;
            int y = p.y;
            y = super.getGridSizeY() - y - 1;
            for (int i = gridPieces.size() - 1; i >= 0; i--) {
                GridPieceAction a = (GridPieceAction) gridPieces.elementAt(i);
                if (a.getGridPiece().getX() == x &&
                        a.getGridPiece().getY() == y) {

                    for (int j = 0; j < listeners.size(); j++) {
                        OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(j);
                        o.removePiece(a.getGridPiece(), super.getNumMoves());
                    }

                    break;
                }
            }
        }
    }

    protected void addDeadStone(int deadStone) {
        int player = 0;
        if (deadStone < passMove) {
            player = getPosition(deadStone);
        }
        super.addDeadStone(deadStone);
        if (deadStone < passMove) {
            removePieces(deadStone);

//            GridPiece p = null;
//            p = new SimpleGridPiece();
//            p.setPlayer(player);
//            p.setDepth(super.getNumMoves() + 1);
//            int x = deadStone % super.getGridSizeX();
//            int y = super.getGridSizeY() - deadStone / super.getGridSizeX() - 1;
//            p.setX(x);
//            p.setY(y);
//            
//            Color c = p.getColor();
//            p.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 127));
//            
//            
//
//            GridPieceAction a = new GridPieceAction(p, super.getNumMoves(), GridPieceAction.ADD);
//            gridPieces.addElement(a);
//
//            for (int i = 0; i < listeners.size(); i++) {
//                OrderedPieceCollection o = (OrderedPieceCollection) listeners.elementAt(i);
//                o.addPiece(p, super.getNumMoves());
//            }
        }
    }
}