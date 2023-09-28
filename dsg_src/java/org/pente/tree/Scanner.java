package org.pente.tree;

import org.pente.game.*;
import org.pente.gameServer.core.*;

import java.util.*;

import org.apache.log4j.*;

/**
 * a test of the scanner idea.  currently uses recursion and a depth-first
 * min-max search.  uses alpha-beta if a win is found.  also handles transpositions
 * or already searched nodes by looking them up using hashcode+gridstate and
 * reusing results.
 * <p>
 * scanning stuff should be separate from creating analysis of the board
 * in order to try different search algorithms, techniques, etc.
 */
public class Scanner {

    private static final Category log4j = Category.getInstance(
            Scanner.class.getName());

    private NodeSearcher nodeSearcher;
    private GridState gridState;
    private Node current;

    public Scanner(NodeSearcher nodeSearcher, GridState gridState, Node current) {
        this.nodeSearcher = nodeSearcher;
        this.gridState = gridState;
        this.current = current;
    }

    public void singleScan() {

        PenteState state = new FastPenteStateZobrist();
        for (int i = 0; i < gridState.getNumMoves(); i++) {
            state.addMove(gridState.getMove(i));
        }
        System.out.println("state = " + printState(state));
        System.out.println("hash = " + state.getHash());

        PenteAnalyzer analyzer = new PenteAnalyzer(state);

        PositionAnalysis a = analyzer.analyzeMove();
        int moves[] = a.getNextMoves();

        //System.out.println(a);
        for (int i = 0; i < moves.length; i++) {
            System.out.print(printMove(moves[i]) + ", ");
            state.addMove(moves[i]);

//            try {
//                Node n = nodeSearcher.loadPosition(state);
//                if (n == null) {
//                    n = new SimpleNode();
//                    n.setDepth(state.getNumMoves());
//                    n.setHash(state.getHash());
//                    n.setRotation(state.getRotation(state.getNumMoves() - 2));
//                    n.setParent(current);
//                    n.setPlayer(3 - gridState.getCurrentPlayer());
//                    n.setPosition(moves[i]);
//                    if (state.isGameOver()) {
//                        n.setType(Node.TYPE_WIN);
//                        n.setComment("singleScan win");
//                        current.setType(Node.TYPE_LOSE);
//                        current.setComment("singleScan lose");
//                        nodeSearcher.storePosition(current);
//                    }
//                    else {
//                        n.setType(Node.TYPE_UNKNOWN);
//                    }
//                    n.setComment("singleScan");
//                    nodeSearcher.storePosition(n);
//                    current.addNextMove(n);
//                }
//
//            } catch (NodeSearchException nse) {
//                nse.printStackTrace();
//            }

            state.undoMove();
        }
    }

    // experimental pente analyzer stuff

    private long startTime;
    private long sTime2;

    public void scan(int maxDepth, int maxNodes) {

        this.maxDepth = maxDepth;
        this.maxNodes = maxNodes;

        new Thread(() -> {

            Node oldCurrent = current;
            //PenteState state = (PenteState) GridStateFactory.createGridState(
            //    GridStateFactory.PENTE);

            PenteState state = new FastPenteStateZobrist();
            for (int i = 0; i < gridState.getNumMoves() - 1; i++) {
                state.addMove(gridState.getMove(i));
            }
            int move = gridState.getMove(gridState.getNumMoves() - 1);

            scanCount = 0;
            scanHits = 0;
            maxScanDepth = 0;
            startTime = System.currentTimeMillis();
            sTime2 = System.currentTimeMillis();
            int result = scan(state, move, 1);

            current = oldCurrent;
            //board.drawPotentialMoves(current);

            log4j.info("scanned " + scanCount + " nodes, maxDepth = " + maxScanDepth);
            log4j.info("node searcher stats = " + nodeSearcher);
            log4j.info("total time = " + (System.currentTimeMillis() - startTime) + " milliseconds.");
            System.out.println("scanned " + scanCount + " nodes, maxDepth = " + maxScanDepth);
            System.out.println("node searcher stats = " + nodeSearcher);
            System.out.println("total time = " + (System.currentTimeMillis() - startTime) + " milliseconds.");

        }).start();
    }

    private int scanCount;
    private int maxScanDepth;
    private int maxDepth;
    private int maxNodes;
    private int scanHits;

    //recursive scan
    private int scan(PenteState state, int move, int scanDepth) {

        // stop looking
        if (scanDepth > maxDepth) {
            log4j.debug("reached depth limit, returning 3");
            return 3;
        }
        // stop looking
        if (scanCount > maxNodes) {
            log4j.debug("reached scan limit, returning 3");
            return 3;
        }

        if (scanDepth > maxScanDepth) {
            maxScanDepth = scanDepth;
        }

        // if depth = 1, we already have a node so don't need to create it
        if (scanDepth != 1) {
            Node n = addPotential(state, move, Node.TYPE_UNKNOWN, scanCount);
            if (n != null) {
                int r = 0;
                if (n.getType() == Node.TYPE_WIN) {
                    r = 3 - n.getPlayer();
                    //current.setComment(current.getComment() + "\nreturn lose, already searched" +
                    //    "before, my moves=" + printState(current) + "\nalready searched moves=" + printState(n));
                } else if (n.getType() == Node.TYPE_LOSE) {
                    r = n.getPlayer();
                    //current.setComment(current.getComment() + "\nreturn win, already searched" +
                    //        "before, my moves=" + printState(current) + "\nalready searched moves=" + printState(n));

                } else {
                    r = 3;
                }
                //log4j.debug("already scanned " + move + ", returning " + r);
                //TODO seems like here we run into problem of if we already
                //looked at a position, then we can't use the tool and move
                //forward down a certain line and rescan. because if we encounter
                //a position we already saw we won't keep looking down it to the
                //correct depth
                scanHits++;
                if (scanHits % 1000 == 0) {
                    System.out.println("existing " + scanHits);
                    //try { Thread.sleep(100); }catch(InterruptedException i) {}
                }

                return r;
            }
            // move current to the node we just created
            current = current.getNextMove(move);
        }

        // only count UNIQUE scans
        scanCount++;

        state.addMove(move);

        if (state.isGameOver()) {
            //System.out.println("game over " + scanCount);        	try { Thread.sleep(100); }catch(InterruptedException i) {}

            //printState(current);

            //current.setComment(current.getComment() + "\ngame over, moves=" + printState(current));

            current.setType(Node.TYPE_WIN);
            state.undoMove();
            current = current.getParent();
            return state.getCurrentPlayer();
        }

        PenteAnalyzer analyzer = new PenteAnalyzer(state);
        //log4j.debug("count = " + scanCount + ", depth=" + scanDepth);
        PositionAnalysis a = analyzer.analyzeMove();

        if (scanCount % 5000 == 0) {
            //log4j.info("scan count = " + scanCount);
            System.out.print("scan count=" + scanCount + " depth=" + scanDepth);
            System.out.println("  total time = " + (System.currentTimeMillis() - sTime2) + " milliseconds.");
            System.out.flush();
            try {
                Thread.sleep(100);
            } catch (InterruptedException i) {
            }
            sTime2 = System.currentTimeMillis();
            //Thread.yield();
        }

        // then consider next moves
        int nextMoves[] = a.getNextMoves();

        // recursively scan the next moves depth first
        // keep track of results, if all results are the same, then we
        // know one player has a forced win, so return that player, otherwise
        // return 3 for unknown
        int r = -1;

        //if (log4j.isDebugEnabled()) {

        // temp
        //current.setComment(current.getComment() + "\nmoves=" + printState(current));

        //StringBuffer buf = new StringBuffer("next moves=");
        //for (int i = 0; i < nextMoves.length; i++) {
        //    buf.append(printMove(nextMoves[i]));
        //    if (i != nextMoves.length - 1) {
        //        buf.append(", ");
        //    }
        //}
        //current.setComment(current.getComment() + "\n" + buf.toString());
        //}

        for (int i = 0; i < nextMoves.length; i++) {
            //if (scanDepth == 1) {
            //    System.out.println("d1 before, current = " + current);
            //    System.out.println("d1 before, state = " + printState(state));
            //    ((SimplePenteState) state).printBoard();
            //}
            if (state.getPosition(nextMoves[i]) != 0) {
                log4j.error("Error! Trying to add a move over and existing one - " + printMove(nextMoves[i]));
                log4j.error(printState(state));
            }
            int t = scan(state, nextMoves[i], scanDepth + 1);

            //if (scanDepth == 1) {
            //    board.drawPotentialMoves(current);
            //}
            //if (scanDepth == 1) {
            //    System.out.println("d1 after, current = " + current);
            //    System.out.println("d1 after, state = " + printState(state));
            //    ((SimplePenteState) state).printBoard();
            //}

            // alpha-beta search
            if (t == state.getCurrentPlayer()) {
                r = t;
                break;
            }
            // set 1st result
            if (r == -1) {
                r = t;
            }
            // if subsequent result is different than other results
            // then we have no solution, set to 3
            else if (r != t) {
                r = 3;
            }
        }

        if (r == -1) r = 3;
        log4j.debug("return " + r);
        if (r < 3 && r != state.getCurrentPlayer()) {
            current.setType(Node.TYPE_WIN);
            current.setComment(current.getComment() + "\nset win, all next moves are losers, moves=" + printState(current));
        } else if (r < 3 && r == state.getCurrentPlayer()) {
            current.setComment(current.getComment() + "\nset lose, found a win on next move, moves=" + printState(current));

            current.setType(Node.TYPE_LOSE);
        }

        //if (state.getNumCaptures(3 - state.getCurrentPlayer()) > 0) {
        //    System.out.println("captures made");
        //}
        state.undoMove();
        current = current.getParent();

        return r;
    }

    /**
     * return Node for position.  If db already has node it will be returned,
     * otherwise a new one will be created.
     */
    private Node addPotential(PenteState state, int move, int type, int count) {
        state.addMove(move);

        try {

            Node node = nodeSearcher.loadPosition(state);

            if (node == null) {
                Node n = new SimpleNode(move, state.getCurrentPlayer(), type);
                n.setRotation(state.getRotation(state.getNumMoves() - 2));
                n.setHash(state.getHash());
                n.setDepth(state.getNumMoves());
                n.setComment("move=" + printMove(move) + "=" + move + ", count=" + count + ", depth=" + state.getNumMoves());


                n.setParentHash(current.getHash());
                nodeSearcher.storePosition(n);

                state.undoMove();
                return null;
            } else {
                // get moves leading up to this node, walk up the tree
//                ArrayList m = new ArrayList();
//                Node w = node;
//                while (w.getParent() != null) {
//                    m.add(new Integer(w.getPosition()));
//                    w = w.getParent();
//                }
//                // create a pente state out of those nodes
//                FastPenteStateZobrist fromNode = new FastPenteStateZobrist();
//                for (int i = m.size() - 1; i >= 0; i--) {
//                    fromNode.addMove(((Integer) m.get(i)).intValue());
//                }


                // check that states actually are the same
//                if (!state.positionEquals(fromNode)) {
//                    //log4j.error("hash clash!, same hashes, different state.");
//                    //log4j.error("hash = " + state.getHash());
//                    //log4j.error("new state = " + printState(state));
//                    //log4j.error("old state = " + printState(fromNode));
//                    //hashClashs++;
//                    
//                    Node n = new SimpleNode(move, state.getCurrentPlayer(), type);
//                    n.setRotation(state.getRotation(state.getNumMoves() - 2));
//                    n.setHash(state.getHash());
//                    n.setDepth(depth);
//                    n.setComment("move=" + printMove(move) + "=" + move + ", count=" + count + ", depth=" + depth);
//                    
//                    current.addNextMove(n);
//                    state.undoMove();
//                    return null;
//                }

                //log4j.error("hashing ok");
//                System.out.println("new state = " + printState(state));
//                System.out.println("old state = " + printState(fromNode));

                //System.out.println("add existing for move " + move);
                //current.addExistingNextMove(node);

                state.undoMove();
                return node;
            }

        } catch (NodeSearchException nse) {
            log4j.error("nse", nse);
        }

        return null;
    }

    private GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);

    public String printMove(int move) {
        return coordinates.getCoordinate(move);
    }

    private String printState(PenteState penteState) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < penteState.getNumMoves(); i++) {
            buf.append(coordinates.getCoordinate(penteState.getMove(i)));
            if (i != penteState.getNumMoves() - 1) buf.append(", ");
        }
        return buf.toString();
    }

    private String printState(Node node) {
        String buf = "";
        do {
            buf = printMove(node.getPosition()) + ", " + buf;
            node = node.getParent();
        } while (!node.isRoot());
        return buf;
    }

    // problem is that as nodes are evaluated their value as defined by
    // below comparator changes, so it becomes impossible to remove them
    // for now, just use a list and sort it each time, later will
    // implement a faster custom tree class
    List<Node> rankTree = new ArrayList<Node>();
    Comparator<Node> c = (o1, o2) -> {
        if (o1.getHash() == o2.getHash()) return 0;

        Rank r1 = o1.getBestRank();
        Rank r2 = o2.getBestRank();

        if (r2 == null) {
            return -1;
        } else if (r1 == null) {
            return 1;
        } else if (r2.getOffenseGroup() < r1.getOffenseGroup()) {
            return 1;
        } else if (r2.getOffenseGroup() > r1.getOffenseGroup()) {
            return -1;
        } else {
            return r2.getOffenseRank() - r1.getOffenseRank();
        }
    };

    //    TreeSet<Node> rankTree= new TreeSet<Node>(new Comparator<Node>() {
//    	public int compare(Node o1, Node o2) {
//    		if (o1.getHash() == o2.getHash()) return 0;
//            
//            Rank r1 = o1.getBestRank();
//    		Rank r2 = o2.getBestRank();
//
//            if (r2 == null) {
//                return -1;
//            }
//            else if (r1 == null) {
//                return 1;
//            }
//            else if (r2.getGroup() < r1.getGroup()) {
//                return 1;
//            }
//            else if (r2.getGroup() > r1.getGroup()) {
//                return -1;
//            }
//            else {
//    			return r2.getOffenseRank() - r1.getOffenseRank();
//            }
//    	}
//    });
    Map<Long, Node> hashTree = new HashMap<Long, Node>();
    Node scanRoot = null;

    public void scanBest(int maxNodes) {

        startTime = System.currentTimeMillis();
        Node node = current;
        PenteState state = new FastPenteStateZobrist();
        for (int i = 0; i < gridState.getNumMoves(); i++) {
            state.addMove(gridState.getMove(i));
        }
        scanRoot = current;
        rankTree.clear();
        hashTree.clear();

        scanMyTurn(state, node);

        while (!rankTree.isEmpty() &&
                scanRoot.getType() == Node.TYPE_UNKNOWN &&
                scanCount < maxNodes) {

            Collections.sort(rankTree, c);
            Node best = rankTree.get(0);

            syncState(state, best);

//            Rank br = best.getBestRank();
//            if (log4j.isDebugEnabled()) log4j.debug("best rank " + br);
//            System.out.println(scanCount + " best rank = " + br);
//            if (br == null) {
//                break;
//            }//tmp

            Node nm = best.getBestPotential();
            if (log4j.isDebugEnabled()) log4j.debug("best node " + nm);
            System.out.println(printState(nm));
            state.addMove(nm.getPosition());
            nm.setHash(state.getHash());
            nm.setRotation(state.getRotation(state.getNumMoves() - 2));

            if (best.getNumPotentials() == 0) {
                rankTree.remove(best);
            }

            Node e = hashTree.get(nm.getHash());
            if (e != null) {
                if (e.getType() == Node.TYPE_UNKNOWN) {
                    e.addTwin(nm);
                } else if (e.getType() == Node.TYPE_LOSE) {
                    nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n2set as op win from existing \n" + e.getComment());
                    updateILost(state, nm);
                } else {
                    nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n2set as I win from existing \n" + e.getComment());
                    updateIWon(state, nm);
                }
            } else {
                scanOpTurn(state, nm);
            }
        }
        System.out.println("rankTree size = " + rankTree.size());
        System.out.println("scanned nodes = " + scanCount);
        System.out.println("scanRoot type = " + scanRoot.getType());
        System.out.println("total time = " + (System.currentTimeMillis() - startTime) + " milliseconds.");

        /*
         * scan_my_turn(state, node)
         *
         * while (rank tree not empty and not max scanned nodes)
         *
         * pick out highest ranked node from ranktree Node n =
         * highrank.addNextMoveFromPotential()
         *
         * Node p = n; Node path = new ArrayList<Node>(); // undo moves until
         * find a common position for (int i=state.getNumMoves(); i--; i>0) { if
         * (state.getHash(i) != p.getHash()) path.add(p); p = p.getParent();
         * state.undoMove(); } else { break; } } //now add moves until at
         * correct position for (Node a : path) state.addMove(a.getMove());
         *
         * scan_op_turn(state, n) }
         */
    }

    private void scanMyTurn(PenteState state, Node n) {
        log4j.debug("scanMyTurn " + printState(state));
        System.out.println("scanMyTurn " + printState(state));

        Node e = hashTree.get(n.getHash());
        if (e != null) {
            System.out.println("something wrong");
        }

        n.setComment("count=" + scanCount++ + "\n" +
                printState(state) + "\n" + printState(n));

        try {
            nodeSearcher.storePosition(n);
        } catch (NodeSearchException n2) {
        }
        hashTree.put(n.getHash(), n);

        if (state.isGameOver()) {
            updateOpWon(state, n);
            return;
        }

        // my turn so just looking for what moves are good for me
        // and adding them to ranktree for later analysis
        PenteAnalyzer analyzer = new PenteAnalyzer(state);
        n.setPotentialNextMoves(analyzer.analyzeMove().getNextMoveRanks());

        rankTree.add(n);
    }

    private void scanOpTurn(PenteState state, Node n) {
        log4j.debug("scanOpTurn " + printState(state));
        System.out.println("scanOpTurn " + printState(state));
        if (printState(state).equals("K10, L9, M7, L11, K8, L10, L8, J8, K9, L12, L13, M8, L8, J10, K11, K12, O5, N6, K7, K8, J7, L7, M10, H7, K8, K7, J7, K7, J13, K12, H6, L9, N7, M8, L9, J11, L9, K8, K6")) {
            System.out.println("crash?");
        }
        n.setComment("count=" + scanCount++ + "\n" +
                printState(state) + "\n" + printState(n));

        // we must check before calling this method that there is no clash
        try {
            nodeSearcher.storePosition(n);
        } catch (NodeSearchException n2) {
        }
        hashTree.put(n.getHash(), n);

        if (state.isGameOver()) {
            n.setType(Node.TYPE_WIN);
            updateIWon(state, n);
            return;
        }

        PenteAnalyzer analyzer = new PenteAnalyzer(state);
        PositionAnalysis a = analyzer.analyzeMove();
        List<Rank> next = a.getNextMoveRanks();
        n.setPotentialNextMoves(next);

        Node nm = n.getBestPotential();
        state.addMove(nm.getPosition());
        nm.setHash(state.getHash());
        nm.setRotation(state.getRotation(state.getNumMoves() - 2));

        Node e = hashTree.get(nm.getHash());
        if (e != null) {
            if (e.getType() == Node.TYPE_UNKNOWN) {
                e.addTwin(nm);
            } else if (e.getType() == Node.TYPE_LOSE) {
                nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n1set as I win from existing \n" + e.getComment());
                updateOpLost(state, nm);
            } else {
                nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n1set as op win from existing \n" + e.getComment());
                updateOpWon(state, nm);
            }
        } else {
            scanMyTurn(state, nm);
        }
    }

    private void updateOpWon(PenteState state, Node n) {
        System.out.println("updateOpWon " + printState(n));
        n.setType(Node.TYPE_WIN);

        updateILost(state, n.getParent());

        if (n.getNumTwins() > 0) {
            for (Node t : n.getTwins()) {
                updateOpWon(state, t);
            }
        }
    }

    private void updateILost(PenteState state, Node n) {
        System.out.println("updateILost " + printState(n));

        clearFromRankTree(n);

        //p.trimNonWinning();//optional to free up space
        n.setType(Node.TYPE_LOSE);

        Node p = n.getParent();
        if (p.allNextMovesLose()) {
            updateOpWon(state, p);
        }
    }

    private void updateIWon(PenteState state, Node n) {
        System.out.println("updateIWon " + printState(n));
        n.setType(Node.TYPE_WIN);
        updateOpLost(state, n.getParent());

        if (n.getNumTwins() > 0) {
            for (Node t : n.getTwins()) {
                System.out.print("update twin ");
                updateIWon(state, t);
            }
        }
    }

    public void updateOpLost(PenteState state, Node n) {
        System.out.println("updateOpLost " + printState(n));

        clearFromRankTree(n);

        //n.getParent().trimNonWinning();//optional to free up space
        n.setType(Node.TYPE_LOSE);

        if (n == scanRoot) { //other parents would have to be as well
            return;
        }

        Node p = n.getParent();
        if (p.allNextMovesLose()) {
            updateIWon(state, p);
        } else if (p.getNumPotentials() > 0) {
            Node nm = p.getBestPotential();
            //nm=ops move
            syncState(state, nm);
            nm.setHash(state.getHash());
            nm.setRotation(state.getRotation(state.getNumMoves() - 2));

            Node e = hashTree.get(nm.getHash());
            if (e != null) {
                if (e.getType() == Node.TYPE_UNKNOWN) {
                    e.addTwin(nm);
                } else if (e.getType() == Node.TYPE_LOSE) {
                    nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n3set as I win from existing \n" + e.getComment());
                    // if op loses
                    nm.setType(Node.TYPE_LOSE);
                    updateOpLost(state, nm);
                } else if (e.getType() == Node.TYPE_WIN) {
                    nm.setComment("count=" + scanCount++ + "\n" + printState(nm) + "\n3set as op win from existing \n" + e.getComment());
                    //if op wins
                    updateOpWon(state, nm);
                }
            } else {
                scanMyTurn(state, nm);
            }
        }
        // if no more potentials then just return

        if (n.getNumTwins() > 0) {
            for (Node t : n.getTwins()) {
                System.out.print("update twin ");
                updateOpLost(state, t);
            }
        }
    }

    private void clearFromRankTree(Node n) {
        rankTree.remove(n);
        n.clearPotentials();
        for (Node c : n.getNextMoves()) {
            for (Node c2 : c.getNextMoves()) {
                clearFromRankTree(c2);
            }
        }
    }

    private void syncState(PenteState state, Node n) {

        // crappy but correct implementation
        while (state.getNumMoves() > scanRoot.getDepth()) {
            state.undoMove();
        }

        Node p = n;
        List<Node> path = new ArrayList<Node>();
        while (p != scanRoot) {
            path.add(p);
            p = p.getParent();
        }
        // now add back moves from node to state
        for (int i = path.size() - 1; i >= 0; i--) {
            state.addMove(path.get(i).getPosition());
        }


        // seems to be buggy
        // should work even if moves are transposed
        // but NOT if moves are mirrored!

//        Node p = n;
//        List<Node> path = new ArrayList<Node>();
//        
//        // undo moves from state until same depth as new move
//        while (state.getNumMoves() > p.getDepth()) {
//            state.undoMove();
//        }
//        // walk up node until find the same depth as state
//        while (p.getDepth() > state.getNumMoves()) {
//            path.add(p);
//            p = p.getParent();
//        }
//        // now depth is the same, so go backwards until find a common hash
//        // undo moves until find a common position
//        for (int i = state.getNumMoves() - 1; i >= 0; i--) {
//            if (state.getHash(i) != p.getHash()) {
//                path.add(p);
//                p = p.getParent();
//                state.undoMove();
//            } else {
//                break;
//            }
//        }
//        // now add back moves from node to state
//        for (int i = path.size() - 1; i >= 0; i--) {
//            state.addMove(path.get(i).getPosition());
//        }
    }
}
