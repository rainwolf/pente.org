package org.pente.tree;

import java.util.*;

import org.pente.gameServer.core.*;

import org.apache.log4j.*;

public class AIBoardController implements NodeBoardListener,
        OrderedPieceCollection {

    private static final Category log4j = Category.getInstance(
            AIBoardController.class.getName());


    // model
    private Node current = null;
    private BestFirstScanner bScanner = new BestFirstScanner(this);
    private List<Node> nodes = new ArrayList<Node>(50);
    private int nodeInView = -1;

    // view
    private AWTNodeEditor editor;
    private AWTBoard board;

    public AIBoardController() {
    }

    public void registerView(AWTNodeEditor editor, AWTBoard board) {
        this.editor = editor;
        this.board = board;
        board.addNodeBoardListener(this);
        board.getCoordinatesList().addOrderedPieceCollectionVisitListener(this);
    }

    public void load() {

        //try {

        current = SimpleNode.createRoot();
        addMove(180, Node.TYPE_UNKNOWN);

        // loads just the root
        //current = nodeSearcher.loadAll();
        //nodes.add(current);


        //board.addMove(180);
//            board.addMove(179);
//            board.addMove(184);
//            board.addMove(178);
//            board.addMove(182);
//            board.addMove(177);

        //current = nodeSearcher.loadPosition(board.getGridState());
        //nodes.add(current);
        //nodeInView = 1;
        //editor.setComment(current.getComment());
        //board.drawPotentialMoves(current);

//        } catch (NodeSearchException nse) {
//            nse.printStackTrace();
//        }
    }

    public void store() {
//        try {
//            current.setComment(editor.getComment());
//            nodeSearcher.storeAll();
//        } catch (NodeSearchException ne) {
//            ne.printStackTrace();
//        }
    }

    public void destroy() {
//        nodeSearcher.destroy();
    }

    public void toggleDefault() {

    }

    public void addMove(int position, int type) {
        try {

            board.clearPotentialMoves();

            //TODO check if user is back in history and is going forward
            // again by adding a move, if so just call visitNextTurn() and return
            // and possibly change the type of the node
            board.addMove(position);

            Node existing = bScanner.loadPosition(
                    board.getGridState().getHash());
            if (existing != null) {
                //System.out.println("existing move exists");

                current = existing;
                editor.setComment(current.getComment() + "\n" + current);
                board.drawPotentialMoves(current);
            } else {
                System.out.println("new move");

                Node n = new SimpleNode(position,
                        3 - board.getGridState().getCurrentPlayer(), type);
                n.setDepth(board.getGridState().getNumMoves());
                n.setHash(board.getGridState().getHash());
                // the rotation we want is actually the rotation BEFORE
                // adding this move
                if (board.getGridState().getNumMoves() > 1) {
                    n.setRotation(board.getGridState().getRotation(
                            board.getGridState().getNumMoves() - 2));
                }

                //n.setComment(editor.getComment());
                editor.setComment("");

                current.addNextMove(n);
                current = n;

                //nodeSearcher.storePosition(n);
            }

            trimPath();
            nodes.add(current);
            nodeInView++;

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void deleteMove(int position) {
//        Node n = current.getNextMove(position);
//        if (n != null) {
//            current.removeNextMove(n);
//            board.clearPotentialMove(n);
//            
//            // if delete down current local path, truncate it
//            // if viewing leaf, return
//            if (nodeInView == nodes.size() - 1) {
//                return;
//            }
//            Node c = (Node) nodes.get(nodeInView + 1);
//            if (c.getPosition() == position) {
//                trimPath();
//                board.trimPath();
//            }
//        }
    }

    private void trimPath() {
        while (nodeInView != nodes.size() - 1) {
            nodes.remove(nodeInView + 1);
        }
    }

    // OrderedPieceCollection implementation
    // Used to allow user to travel back in history and view potential moves
    public void addPiece(GridPiece gridPiece, int turn) {
    }

    public void removePiece(GridPiece gridPiece, int turn) {
    }

    public void undoLastTurn() {
    }

    public void clearPieces() {
    }

    public void visitNextTurn() {
        if (nodeInView == nodes.size() - 1) return;
        visitTurn(nodeInView + 1);
    }

    public void visitPreviousTurn() {
        if (nodeInView <= 0) return;
        visitTurn(nodeInView - 1);
    }

    public void visitFirstTurn() {
        if (nodeInView <= 0) return;
        nodeInView = 0;// don't need to do anything else because
        // visitNextTurn() will be called right after this
    }

    public void visitLastTurn() {
        if (nodeInView == nodes.size() - 1) return;
        visitTurn(nodes.size() - 1);
    }

    public void visitTurn(int turn) {
        board.clearPotentialMoves();
        nodeInView = turn;
        current = (Node) nodes.get(nodeInView);
        board.drawPotentialMoves(current);
        board.getGridBoard().setThinkingPieceVisible(true);
        editor.setComment(current.getComment());
    }


    // experimental pente analyzer stuff
    public void singleScan() {

        Scanner scanner = new Scanner(null,
                board.getGridState(), current);
        scanner.singleScan();
    }

    public void scan(int maxDepth, int maxNodes) {

        bScanner.scanBest(maxNodes, current);
    }

    public void scanCompleted() {
        visitTurn(nodeInView);
    }

    public void stop() {
        bScanner.stop();
    }

    private int scannedIndex = 0;

    public void visitNextScanned() {
        visitScanned(++scannedIndex);
    }

    public void visitPrevScanned() {
        if (scannedIndex > 0) {
            visitScanned(--scannedIndex);
        }
    }

    public void visitScanned(int index) {
        board.clearPotentialMoves();
        board.getGridBoard().clearPieces();
        board.getGridState().clear();

        Node n = bScanner.getSearched(index);
        List<Node> path = new ArrayList<Node>(n.getDepth());
        Node p = n;
        while (!p.isRoot()) {
            path.add(p);
            p = p.getParent();
        }
        nodes.clear();
        for (int i = path.size() - 1; i >= 0; i--) {
            nodes.add(path.get(i));
            board.addMove(path.get(i).getPosition());
        }
        nodeInView = path.size() - 1;
        current = path.get(0);

        board.drawPotentialMoves(current);
        board.getGridBoard().setThinkingPieceVisible(true);
        editor.setComment(current.getComment());
    }

    public void findPositionInRankTree() {
        bScanner.findPositionInRankTree(current);
    }
}