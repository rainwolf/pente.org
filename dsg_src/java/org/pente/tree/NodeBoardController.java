package org.pente.tree;

import java.util.*;

import org.pente.gameServer.core.*;

import org.apache.log4j.*;

//TODO make default node work, broken since we moved to hashcode version...
//how to make it work?  lookup position using hashcode, if none found
//lookup previous position and check for defaultNode

//also complicates the process of entering/deleting default nodes
//and of going back/forth and viewing default node

public class NodeBoardController implements NodeBoardListener,
    OrderedPieceCollection {

    private static final Category log4j = Category.getInstance(
        NodeBoardController.class.getName());
    
    private boolean defaultOn = false;
    
    // model
    private Node current = null;
    private Node prevDefault = null;
    private NodeSearcher nodeSearcher;
    private List nodes = new ArrayList(50);
    private int nodeInView = -1;
    private boolean viewOnly = false;
    
    // view
    private AWTNodeEditor editor;
    private AWTBoard board;

    public NodeBoardController(NodeSearcher nodeSearcher, boolean viewOnly) {
        this.nodeSearcher = nodeSearcher;
        this.viewOnly = viewOnly;
    }
    
    public void registerView(AWTNodeEditor editor, AWTBoard board) {
        this.editor = editor;
        this.board = board;
        board.addNodeBoardListener(this);
        board.getCoordinatesList().addOrderedPieceCollectionVisitListener(this);
    }
    
    public void load() {

        try {
            // loads just the root
            current = nodeSearcher.loadAll();
            nodes.add(current);


            board.addMove(180);
//            board.addMove(179);
//            board.addMove(184);
//            board.addMove(178);
//            board.addMove(182);
//            board.addMove(177);
            
            current = nodeSearcher.loadPosition(board.getGridState());
            nodes.add(current);
            nodeInView = 1;
            editor.setComment(current.getComment());
            board.drawPotentialMoves(current);
            prevDefault = current.getDefaultNextMove();
            
        } catch (NodeSearchException nse) {
            nse.printStackTrace();
        }
    }
    public void store() {
        try {
            current.setComment(editor.getComment());
            nodeSearcher.storeAll();
        } catch (NodeSearchException ne) {
            ne.printStackTrace();
        }
    }
    public void destroy() {
        nodeSearcher.destroy();
    }

    public void toggleDefault() {
        defaultOn = !defaultOn;

        // clear out moves
        if (defaultOn) {
            board.clearPotentialMoves();
        }
        else {
            board.drawPotentialMoves(current);
        }
    }

    public void addMove(int position, int type) {
        try {
            
            //TODO sanity-check that there is already a nextMove in the same position
            // as the default move.  otherwise the opponent could play his
            // next move where the default is and it would break
            if (defaultOn) {
                //do something different for default nodes
                Node n = new SimpleNode(position, 
                    board.getGridState().getCurrentPlayer(), Node.TYPE_WIN);
                n.setHash(0); // hash doesn't matter for default nodes
                // the rotation we want is actually the rotation of the
                // previous move
                n.setRotation(board.getGridState().getRotation(
                    board.getGridState().getNumMoves() - 2));
                nodeSearcher.storeAll();
                current.setDefaultNextMove(n);

                defaultOn = false;
                
                // don't actually make a move on the board when adding a default
                return;
            }

            board.clearPotentialMoves();

            //TODO check if user is back in history and is going forward
            // again by adding a move, if so just call visitNextTurn() and return
            // and possibly change the type of the node
            board.addMove(position);
            
            Node existing = nodeSearcher.loadPosition(
                board.getGridState().getHash());
            if (existing != null) {
                System.out.println("existing move exists");
                if (existing.getType() != type) {
                    System.out.println("new type="+type);
                    existing.setType(type);
                }
//                String cm = existing.getComment();
//                if (editor.getComment() != null && !editor.getComment().equals("") && !cm.equals(editor.getComment())) {
//                    System.out.println("new comment="+editor.getComment());
//                    existing.setComment(editor.getComment());
//                }
                
                existing.setParent(current);
                current = existing;
                editor.setComment(current.getComment());
                board.drawPotentialMoves(current);
                prevDefault = current.getDefaultNextMove();
                
                //if (existing.nodeNeedsWrite()) {
                //    nodeSearcher.storePosition(existing);
                //}
            }
            else if (prevDefault != null) {
                
                // create temporary node and add the default node as a next move
                // hopefully this node won't get saved by hibernate
                Node n = new SimpleNode(0, 0, Node.TYPE_LOSE);
                n.addNextMove(prevDefault);
                current = n;
            }
            else if (!viewOnly) {
                
                System.out.println("creating new move");
                Node n = new SimpleNode(position,
                    3 - board.getGridState().getCurrentPlayer(), type);
                n.setDepth(board.getGridState().getNumMoves());
                n.setHash(board.getGridState().getHash());
                // the rotation we want is actually the rotation BEFORE
                // adding this move
                n.setRotation(board.getGridState().getRotation(
                    board.getGridState().getNumMoves() - 2));

                n.setComment(editor.getComment());
                
                current.addNextMove(n);
                current = n;
                prevDefault = null;
                
                nodeSearcher.storePosition(n);
            }
            
            trimPath();
            nodes.add(current);
            nodeInView++;            
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    public void deleteMove(int position) {
        Node n = current.getNextMove(position);
        if (n != null) {
            current.removeNextMove(n);
            board.clearPotentialMove(n);
            
            // if delete down current local path, truncate it
            // if viewing leaf, return
            if (nodeInView == nodes.size() - 1) {
                return;
            }
            Node c = (Node) nodes.get(nodeInView + 1);
            if (c.getPosition() == position) {
                trimPath();
                board.trimPath();
            }
        }
    }
    
    private void trimPath() {
        while (nodeInView != nodes.size() - 1) {
            nodes.remove(nodeInView + 1);
        }
    }

    // OrderedPieceCollection implementation
    // Used to allow user to travel back in history and view potential moves
    public void addPiece(GridPiece gridPiece, int turn) {}
    public void removePiece(GridPiece gridPiece, int turn) {}
    public void undoLastTurn() {}
    public void clearPieces() {}

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
        editor.setComment(current.getComment() + "\n" + current.getHash());
    }
    

    // experimental pente analyzer stuff
    public void singleScan() {
        
        Scanner scanner = new Scanner(nodeSearcher, 
            board.getGridState(), current);
        scanner.singleScan();
    }

    public void scan(int maxDepth, int maxNodes) {

        Scanner scanner = new Scanner(nodeSearcher, 
            board.getGridState(), current);
        //scanner.scan(maxDepth, maxNodes);
        scanner.scanBest(maxNodes);
    }
    
    //not implemented for simple scanner
    public void stop() { }
    public void visitNextScanned() {}
    public void visitPrevScanned() {}
    public void scanCompleted() {}
}