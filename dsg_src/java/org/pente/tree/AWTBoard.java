package org.pente.tree;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.game.*;
import org.pente.gameDatabase.swing.SwingDSGButton;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;


public class AWTBoard extends Panel implements OrderedPieceCollection {

    private static final int TYPE_DELETE = 6;

    private GridBoardCanvas gridBoardCanvas;
    private GridBoardOrderedPieceCollectionAdapter gridBoard;
    private GridState gridState;
    private GameOptions gameOptions;
    private CoordinatesListPanel coordinatesList;

    /**
     * used to go back/forth in history and check for valid moves
     */
    private GridState localGridState;

    private int type = 0;
    private boolean readOnly;

    private NodeBoardListener listener;

    private static final GridCoordinates coordinates[] = {
            new AlphaNumericGridCoordinates(19, 19),
            new TraditionalGridCoordinates(19, 19)
    };

    public AWTBoard(GameStyles gs, boolean readOnly) {
        super();

        this.readOnly = readOnly;

        // setup game options
        gameOptions = new SimpleGameOptions(6);
        gameOptions.setPlayerColor(GameOptions.WHITE, 1);
        gameOptions.setPlayerColor(GameOptions.BLACK, 2);
        // special other pieces
        gameOptions.setPlayerColor(GameOptions.YELLOW, Node.TYPE_UNKNOWN);
        gameOptions.setPlayerColor(GameOptions.GREEN, Node.TYPE_WIN);
        gameOptions.setPlayerColor(GameOptions.RED, Node.TYPE_LOSE);
        gameOptions.setPlayerColor(GameOptions.BLUE, TYPE_DELETE);

        gameOptions.setDraw3DPieces(true);
        gameOptions.setPlaySound(true);
        gameOptions.setShowLastMove(true);


        // setup canvas
        PenteBoardCanvas penteCanvas = new PenteBoardCanvas();
        penteCanvas.gridCoordinatesChanged(coordinates[0]);
        penteCanvas.gameOptionsChanged(gameOptions);
        gridBoardCanvas = penteCanvas;
        gridBoardCanvas.addGridBoardListener(new GridBoardController());
        // end setup canvas

        // setup adapter
        GridBoardOrderedPieceCollectionAdapter penteAdapter = new
                PenteBoardOrderedPieceCollectionAdapter(penteCanvas, true);
        penteAdapter.setGridHeight(19);
        penteAdapter.setGridWidth(19);
        penteAdapter.setOnGrid(true);
        penteAdapter.setThinkingPiecePlayer(2);
        penteAdapter.setThinkingPieceVisible(true);
        penteAdapter.setDrawInnerCircles(true);
        penteAdapter.setGameName("Tree");
        gridBoard = penteAdapter;
        // end setup adapter

        // setup grid state
        PenteStatePieceCollectionAdapter penteState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        penteState.addOrderedPieceCollectionListener(penteAdapter);
        penteState.setTournamentRule(true);
        gridState = new SynchronizedPenteState(penteState);
        // end grid state

        coordinatesList = new CoordinatesListPanel(gs, 2, new AWTDSGButton());
        coordinatesList.setHighlightColor(gs.boardBack);
        coordinatesList.gridCoordinatesChanged(coordinates[0]);
        coordinatesList.gameOptionsChanged(gameOptions);
        coordinatesList.setPlayer(1, "White");
        coordinatesList.setPlayer(2, "Black");

        coordinatesList.addOrderedPieceCollectionVisitListener(this);
        coordinatesList.addOrderedPieceCollectionVisitListener(penteAdapter);
        penteState.addOrderedPieceCollectionListener(coordinatesList);

        localGridState = GridStateFactory.createGridState(GridStateFactory.PENTE);

        setLayout(new BorderLayout(2, 2));
        add("Center", gridBoardCanvas);
        add("East", coordinatesList);

        setSize(640, 480);
    }

    public void destroy() {
        gridBoardCanvas.destroy();
        coordinatesList.destroy();
    }

    public void addNodeBoardListener(NodeBoardListener listener) {
        this.listener = listener;
    }

    public CoordinatesListComponent getCoordinatesList() {
        return coordinatesList;
    }

    public GridBoardCanvas getGridBoardCanvas() {
        return gridBoardCanvas;
    }

    public GridState getGridState() {
        return localGridState;
    }

    public GridBoardOrderedPieceCollectionAdapter getGridBoard() {
        return gridBoard;
    }

    public void addMove(int move) {

        trimPath();

        gridState.addMove(move);
        localGridState.addMove(move);
        gridBoard.setThinkingPieceVisible(false);
    }

    /** only call if delete move on current path */

    /**
     * Trims the current path back to where the user is viewing.
     * Could happen if user went back in history and then started down
     * a new path, or if user went back in history and deleted the
     * subtree already visited.
     */
    public void trimPath() {
        while (localGridState.getNumMoves() < gridState.getNumMoves()) {
            gridState.undoMove();
        }
    }

    public void drawPotentialMoves(Node parent) {
        for (Iterator it = parent.getNextMoves().iterator(); it.hasNext(); ) {
            Node child = (Node) it.next();
            if (child != null) {
                gridBoardCanvas.addPiece(createGridPiece(child));
            }
        }
    }

    public void clearPotentialMoves() {
        Vector pieces = gridBoardCanvas.getGridPieces();
        for (Iterator it = pieces.iterator(); it.hasNext(); ) {
            GridPiece p = (GridPiece) it.next();
            if (p.getPlayer() > 2) {
                gridBoardCanvas.removePiece(p);
            }
        }
    }

    public void clearPotentialMove(Node n) {
        gridBoardCanvas.removePiece(createGridPiece(n));
    }

    private GridPiece createGridPiece(Node n) {
        int pos = localGridState.rotateMoveToLocalRotation(n.getPosition(),
                n.getRotation());
        Coord p = localGridState.convertMove(pos);
        return new SimpleGridPiece(p.x, 18 - p.y, n.getType());
    }


    private void updateType() {
        if (readOnly || type < 3) {
            type = localGridState.getCurrentPlayer();
        } else if (type == Node.TYPE_LOSE) {
            type = Node.TYPE_WIN;
        } else if (type == Node.TYPE_WIN) {
            type = Node.TYPE_LOSE;
        }
        gridBoard.setThinkingPiecePlayer(type);
    }

    // OrderedPieceCollection, implemented to keep
    // local grid state up to date with history for validation purposes
    // in gridClicked(), gridMoved()
    public void addPiece(GridPiece gridPiece, int turn) {
    }

    public void removePiece(GridPiece gridPiece, int turn) {
    }

    public void undoLastTurn() {
    }

    public void clearPieces() {
    }

    public void visitNextTurn() {
        if (localGridState.getNumMoves() == gridState.getNumMoves()) return;
        localGridState.addMove(gridState.getMove(localGridState.getNumMoves()));
        updateType();
    }

    public void visitPreviousTurn() {
        if (localGridState.getNumMoves() == 0) return;
        localGridState.undoMove();
        updateType();
    }

    public void visitFirstTurn() {
        localGridState.clear();
        updateType();
    }

    public void visitLastTurn() {
        while (localGridState.getNumMoves() < gridState.getNumMoves()) {
            localGridState.addMove(gridState.getMove(localGridState.getNumMoves()));
        }
        updateType();
    }

    public void visitTurn(int turn) {
        while (localGridState.getNumMoves() < turn) {
            localGridState.addMove(gridState.getMove(localGridState.getNumMoves()));
        }
        while (localGridState.getNumMoves() > turn) {
            localGridState.undoMove();
        }
        updateType();
    }

    private class GridBoardController implements GridBoardListener {

        public void gridMoved(int x, int y) {
            int move = (gridBoard.getGridHeight() - y - 1) *
                    gridBoard.getGridWidth() + x;
            gridBoard.setThinkingPieceVisible(
                    localGridState.isValidMove(move, localGridState.getCurrentPlayer()));
        }

        public void gridClicked(int x, int y, int button) {

            if (button == MouseEvent.BUTTON1_MASK) {
                int move = (gridBoard.getGridHeight() - y - 1) *
                        gridBoard.getGridWidth() + x;
                if (!localGridState.isValidMove(move, localGridState.getCurrentPlayer())) {
                    return;
                }

                if (type == TYPE_DELETE) {
                    listener.deleteMove(move);
                } else {
                    // white/black moves default to yellow
                    if (type < 3) {
                        type = Node.TYPE_UNKNOWN;
                    }
                    listener.addMove(move, type);
                    updateType();
                }

            } else if (!readOnly) {
                if (type < 3) {
                    type = Node.TYPE_UNKNOWN;
                } else if (type == Node.TYPE_UNKNOWN) {
                    type = 3;
                } else if (type == Node.TYPE_WIN) {
                    type = Node.TYPE_LOSE;
                } else if (type == Node.TYPE_LOSE) {
                    type = TYPE_DELETE;
                } else if (type == TYPE_DELETE) {
                    type = localGridState.getCurrentPlayer();
                }

                gridBoard.setThinkingPiecePlayer(type);
            }
        }
    }
}
