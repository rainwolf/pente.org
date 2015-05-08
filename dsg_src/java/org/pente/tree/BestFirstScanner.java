package org.pente.tree;

import org.pente.game.*;
import org.pente.gameServer.core.*;

import java.util.*;

import org.apache.log4j.*;

/** 
 */
public class BestFirstScanner {

    private static final Category log4j = Category.getInstance(
        BestFirstScanner.class.getName());

    
    public BestFirstScanner(NodeBoardListener listener) {
        this.listener = listener;
    }
    
    private long startTime;
    private long sTime2;
    
    
    private NodeBoardListener listener;
 
    private int scanCount;
    private int hashHits;
    private int twinAdds;
    
    private int maxScanDepth;
    private int maxDepth;
    private int maxNodes;
    
    private List<Node> searched = new ArrayList<Node>();
    public Node getSearched(int index) {
        return searched.get(index);
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
    //List<Node> rankTree = new ArrayList<Node>();
    Comparator<Node> c = new Comparator<Node>() {
    	private int hits;
        public int compare(Node o1, Node o2) {
//          if (hits++ %1000 == 0) {
//        	  log4j.info(hits + " comparisons");
//          }
          if (o1.getHash() == o2.getHash()) return 0;
          
          Rank r1 = o1.getBestRank();
          Rank r2 = o2.getBestRank();

          if (r2 == null) {
              return -1;
          }
          else if (r1 == null) {
              return 1;
          }
          

          //TODO i have no idea if 8 is a good value for this
          //int depthDiff = 0;//(o1.getDepth() - o2.getDepth()) / 8;
          
          //int r1Group = r1.getOffenseGroup() + depthDiff;
          //int r2Group = r2.getOffenseGroup() - depthDiff;
          
          int r1Group = r1.getOffenseGroup() + (o1.getDepth() / 8);
          int r2Group = r2.getOffenseGroup() + (o2.getDepth() / 8);
          
          if (r2Group < r1Group) {
              return 1;
          }
          else if (r2Group > r1Group) {
              return -1;
          }
          else {
              int rd = r2.getOffenseRank() - r1.getOffenseRank();
              if (rd == 0) {
                  return o2.getDepth() - o2.getDepth();
              }
              else {
                  return rd;
              }
          }
      }
    };
    PriorityQ rankTree = new PriorityQ(c, 10000);

        
        
        
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
    
    public Node loadPosition(long hash) {
        return hashTree.get(hash);
    }
    public void storePosition(Node node) {
        hashTree.put(node.getHash(), node);
    }
    
    public void findPositionInRankTree(Node n) {
//        for (int i = 0; i < rankTree.size(); i++) {
//            Node r = rankTree.get(i);
//            if (r == n) {
//                log4j.info("Position = " + i + " of " + rankTree.size() + " for node " + n);
//                return;
//            }
//        }
//        log4j.info("not found");
    }
    
    Thread thread = null;
    volatile boolean running = false;
    public void stop() {
        running = false;
        
        log4j.info("rankTree size = " + rankTree.size());
        log4j.info("scanned nodes = " + scanCount);
        log4j.info("scanRoot type = " + scanRoot.getType());
        log4j.info("hash hits = " + hashHits);
        log4j.info("twins set = " + twinAdds);
        long totalTime = (System.currentTimeMillis() - startTime);
        float timePerNode = ((float) totalTime) / ((float) (hashHits + scanCount));
        log4j.info("total time = " + totalTime + " milliseconds.");
        log4j.info("scantime/node = " + timePerNode);
    }
    
    FastPenteStateZobrist state = null;
    
    public void scanBest(final int maxNodes, final Node node) {
        
        // if restarting after a break
        if (rankTree.isEmpty()) {
            startTime = System.currentTimeMillis();
            sTime2 = startTime;
            
            scanRoot = node;
            state = new FastPenteStateZobrist();
            List<Node> path = new ArrayList<Node>(node.getDepth());
            Node p = node;
            do {
                path.add(p);
                p = p.getParent();
            } while (!p.isRoot());
            for (int i = path.size() - 1; i >= 0; i--) {
                state.addMove(path.get(i).getPosition());
            }
    
            rankTree.clear();
            hashTree.clear();
            
            scanMyTurn(state, node);
        }
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                scan(maxNodes, node);
            }
        });
        thread.start();
    }
    
    private void scan(int maxNodes, Node node) {

        
        while (!rankTree.isEmpty() && 
                scanRoot.getType() == Node.TYPE_UNKNOWN &&
                scanCount < maxNodes) {

            if (!running) {
                return;
            }
            
            //Collections.sort(rankTree, c);
            //Node best = rankTree.get(0);
            
            Node best = rankTree.max();
            
            incrementScanCount(best);
            

            syncState(state, best);
            
//            Rank br = best.getBestRank();
//            if (log4j.isDebugEnabled()) log4j.debug(scanCount + " best rank " + br);
//            if (br == null) {
//                System.err.println("bad rank, problem!");
//                break;
//            }//tmp
//
//            //Node nm = best.getBestPotential(br);
//            
            Node nm = best.getBestPotential();
            if (log4j.isDebugEnabled()) log4j.debug("best node " + nm);
            state.addMove(nm.getPosition());
            nm.setHash(state.getHash());
            nm.setRotation(state.getRotation(state.getNumMoves() - 2));
            
            if (best.getNumPotentials() == 0) {
                rankTree.remove(best);
            }
            else {
                rankTree.increaseKey(best);
            }
            
            Node e = loadPosition(nm.getHash());
            if (e != null) {
                hashHits++;
                if (e.getType() == Node.TYPE_UNKNOWN) {
                    e.addTwin(nm);
                    twinAdds++;
                }
                else if (e.getType() == Node.TYPE_LOSE) {
                    nm.setComment("count="+scanCount++ +"\n" +printState(nm) + "\n2set as op win from existing \n" + e.getComment());
                    updateILost(state, nm);
                    nm.setTwin(true);
                }
                else {
                    nm.setComment("count="+scanCount++ +"\n" +printState(nm) + "\n2set as I win from existing \n" + e.getComment());
                    updateIWon(state, nm);
                    nm.setTwin(true);
                }
            }
            else {
                
                scanOpTurn(state, nm);
            }
            // in this case if we find an e, just continue with the loop
            // which will find the next best move
        }
        log4j.info("rankTree size = " + rankTree.size());
        log4j.info("scanned nodes = " + scanCount);
        String result = "unsolved";
        if (scanRoot.getType() == Node.TYPE_WIN) {
            result = "SOLVED FOR LOSS!";
        }
        else if (scanRoot.getType() == Node.TYPE_LOSE) {
            result = "SOLVED FOR WIN!";
        }
        log4j.info("scanRoot type = " + result);
        log4j.info("hash hits = " + hashHits);
        log4j.info("twins set = " + twinAdds);
        long totalTime = (System.currentTimeMillis() - startTime);
        float timePerNode = ((float) totalTime) / ((float) (hashHits + scanCount));
        log4j.info("total time = " + totalTime + " milliseconds.");
        log4j.info("scantime/node = " + timePerNode + " milliseconds");
        
        listener.scanCompleted();
    }
    private void incrementScanCount(Node n) {
        if (++scanCount % 1000 == 0) {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory()/1024/1024;
            long total = rt.totalMemory()/1024/1024;
            long max = rt.maxMemory()/1024/1024;
            log4j.info("scan count: " + scanCount + "\tdepth:" + n.getDepth() + 
            		"\trankTree: " + rankTree.size() + "\thashTable: " +
            		hashTree.size() + "\ttime: " + (System.currentTimeMillis() - sTime2) + " ms " +
                    "\tmem: " + (total-free) + "/" + total + "/" + max + "mb");
            try { Thread.sleep(100); }catch(InterruptedException i) {}
            sTime2 = System.currentTimeMillis();
        }
    }
    
    private Comparator<Rank> offenseRankComp = new Comparator<Rank>() {
        public int compare(Rank r1, Rank r2) {
            if (r2.getOffenseGroup() < r1.getOffenseGroup()) {
                return 1;
            }
            else if (r2.getOffenseGroup() > r1.getOffenseGroup()) {
                return -1;
            }
            else {
                return r2.getOffenseRank() - r1.getOffenseRank();
            }
        }
    };
    
    private void scanMyTurn(PenteState state, Node n) {
        
        searched.add(n);
        if (log4j.isDebugEnabled()) log4j.debug("scanMyTurn " + printState(state));
        
        Node e = hashTree.get(n.getHash());
        if (e != null) {
            log4j.error("scanMyTurn found clash, shouldn't happen");
        }


        hashTree.put(n.getHash(), n);

        incrementScanCount(n);
        n.setComment("count="+scanCount +"\n" +
            printState(state) + "\n");
        if (state.isGameOver()) {
            updateOpWon(state, n);
            return;
        }
        
        // my turn so just looking for what moves are good for me
        // and adding them to ranktree for later analysis
        PenteAnalyzer analyzer = new PenteAnalyzer(state);
        List<Rank> p = analyzer.analyzeMove().getNextMoveRanks();
        
        if (!p.isEmpty()) {
            Collections.sort(p, offenseRankComp);
            n.setPotentialNextMoves(p);
            rankTree.add(n);
        }
//        Rank br = n.getBestRank();
//        n.setComment("count="+scanCount++ +"\n" +
//            printState(state) + "\n" + br.getOffenseGroup() + ":"+br.getOffenseRank());
//        rankTree.add(n);
    }
    
    private void scanOpTurn(PenteState state, Node n) {

        searched.add(n);
        if (log4j.isDebugEnabled()) log4j.debug("scanOpTurn " + printState(state));
        
        incrementScanCount(n);
        
        n.setComment("count="+scanCount +"\n" +
            printState(state) + "\n" + printState(n));

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
 
        Node e = loadPosition(nm.getHash());
        if (e != null) {
            hashHits++;
            if (e.getType() == Node.TYPE_UNKNOWN) {
                e.addTwin(nm);
                twinAdds++;
            }
            else if (e.getType() == Node.TYPE_LOSE) {
                incrementScanCount(nm);
                nm.setComment("count="+scanCount +"\n" +printState(nm) + "\n1set as I win from existing \n" + e.getComment());
                updateOpLost(state, nm);
                nm.setTwin(true);
                return;
            }
            else {
                incrementScanCount(nm);
                nm.setComment("count="+scanCount +"\n" +printState(nm) + "\n1set as op win from existing \n" + e.getComment());
                updateOpWon(state, nm);
                nm.setTwin(true);
                return;
            }
        }
        else {
            scanMyTurn(state, nm);
        }
    }
    
    private void updateOpWon(PenteState state, Node n) {
        if (log4j.isDebugEnabled()) log4j.debug("updateOpWon " + printState(n));
        n.setType(Node.TYPE_WIN);

        updateILost(state, n.getParent());
        
        if (n.getNumTwins() > 0) {
        	for (Node t : n.getTwins()) {
        		updateOpWon(state, t);
        	}
        }
    }
    private void updateILost(PenteState state, Node n) {
        if (log4j.isDebugEnabled()) log4j.debug("updateILost " + printState(n));

        clearFromRankTree(n);
        //p.trimNonWinning();//optional to free up space
        n.setType(Node.TYPE_LOSE);

        Node p = n.getParent();
        if (p.allNextMovesLose()) {
            updateOpWon(state, p);
        }
    }
    
    private void updateIWon(PenteState state, Node n) {
        if (log4j.isDebugEnabled()) log4j.debug("updateIWon " + printState(n));
        n.setType(Node.TYPE_WIN);
        updateOpLost(state, n.getParent());
        
        if (n.getNumTwins() > 0) {
        	for (Node t : n.getTwins()) {
        		if (log4j.isDebugEnabled()) log4j.debug("update twin ");
        		updateIWon(state, t);
        	}
        }
    }
    public void updateOpLost(PenteState state, Node n) {
        if (log4j.isDebugEnabled()) log4j.debug("updateOpLost " + printState(n));
        
        clearFromRankTree(n);
        //n.getParent().trimNonWinning();//optional to free up space
        n.setType(Node.TYPE_LOSE);

        if (n == scanRoot) { //other parents would have to be as well
            return;
        }

        Node p = n.getParent();
        if (p.allNextMovesLose()) {
            updateIWon(state, p);
        }
        else if (p.getNumPotentials() > 0) {
            Node nm = p.getBestPotential();
            
            //nm=ops move
            syncState(state, nm);
            nm.setHash(state.getHash());
            nm.setRotation(state.getRotation(state.getNumMoves() - 2));

            Node e = loadPosition(nm.getHash());
            if (e != null) {
                hashHits++;
                if (e.getType() == Node.TYPE_UNKNOWN) {
                    e.addTwin(nm);
                    twinAdds++;
                }
                else if (e.getType() == Node.TYPE_LOSE) {
                    incrementScanCount(nm);
                    nm.setComment("count="+scanCount +"\n" +printState(nm) + "\n3set as I win from existing \n" + e.getComment());         
                    // if op loses
                    nm.setType(Node.TYPE_LOSE);
                    updateOpLost(state, nm);
                    nm.setTwin(true);
                }
                else if (e.getType()  == Node.TYPE_WIN) {
                    incrementScanCount(nm);
                    nm.setComment("count="+scanCount +"\n" +printState(nm) + "\n3set as op win from existing \n" + e.getComment());
                    //if op wins
                    updateOpWon(state, nm);
                    nm.setTwin(true);
                }
            }
            else {
                scanMyTurn(state, nm);
            }
        }
        // if no more potentials then just return

        if (n.getNumTwins() > 0) {
        	for (Node t : n.getTwins()) {
                if (log4j.isDebugEnabled()) log4j.debug("update twin ");
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
