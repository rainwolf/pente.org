package org.pente.tree;

import java.util.*;

public class SimpleNode implements Node {

    private Long id;
    private int position;
    private int player;
    
    private int type;

    private String comment;
    
    private long hash;
    private int rotation;
    private int depth;

    private long parentHash;
    private Node parent;
    private boolean nextMovesLoaded = false;
    private List<Node> nextMoves;

    private List<Rank> potentialNextMoves;
    private List<Node> twins;
    private boolean isTwin = false;
    
    private boolean stored = false;
    private boolean changedNode = true;
    
    /** Each node has a default node which represents the next move by the opponent
     *  in the case where it doesn't matter what the move is.  No matter what
     *  the opponent does we know what our next move will be.  In this case
     *  we'll add our move onto the defaultNode. 
     */
    private Node defaultNode;
    
    public SimpleNode() {
        nextMoves = new ArrayList<Node>(5);
    }
    public SimpleNode(int position, int player, int type) {
        setPosition(position);
        setPlayer(player);
        setType(type);
        
        nextMoves = new ArrayList<Node>(5);
    }

    public static Node createRoot() {
        return new SimpleNode();
    }
    public static Node createDefault(SimpleNode parent) {
        Node n = new SimpleNode();
        n.setParent(parent);
        return n;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public void setStored(boolean stored) {
        this.stored = true;
        if (stored) {
            changedNode = false;
        }
    }
    public boolean isStored() {
        return stored;
    }
    public boolean nodeNeedsWrite() {
        return changedNode;
    }

    public int getPlayer() {
        return player;
    }
    public void setPlayer(int player) {
        this.player = player;
    }
    public int getPosition() {
        return position;
    }
    public void setPosition(int position) {
        this.position = position;
    }
    public int getType() {
        return type;
    }
    public void setType(int type) {
        this.type = type;
        changedNode = true;
    }

    public String getComment() {
        return comment;
    }
    public void setComment(String comment) {
        this.comment = comment;
        changedNode = true;
    }
    
    public long getHash() {
        return hash;
    }
    public void setHash(long hash) {
        this.hash = hash;
    }
    public int getRotation() {
        return rotation;
    }
    public void setRotation(int rotation) {
        this.rotation = rotation;
    }
    public int getDepth() {
        return depth;
    }
    public void setDepth(int depth) {
        this.depth = depth;
    }

    public long getParentHash() {
        return parentHash;
    }
    public void setParentHash(long hash) {
        this.parentHash = hash;
    }
    public Node getParent() {
        return parent;
    }
    public void setParent(Node parent) {
        this.parent = parent;
        this.parentHash = parent.getHash();
    }

    public void addTwin(Node twin) {
        if (twins == null) {
        	twins = new ArrayList<Node>(1);
        }
        twins.add(twin);
        twin.setTwin(true);
    }
    public int getNumTwins() {
    	if (twins == null) {
    		return 0;
    	}
        return twins.size();
    }
    public List<Node> getTwins() {
        return twins;
    }

    
    
    public boolean isRoot() {
        return parent == null;
    }
    
    public Rank getBestRank() {
      if (potentialNextMoves == null || potentialNextMoves.isEmpty()) {
          return null;
      }
      return potentialNextMoves.get(0);
    }
//    
//    public Rank getBestRank() {
//        if (potentialNextMoves == null || potentialNextMoves.isEmpty()) {
//            return null;
//        }
//        Rank b = potentialNextMoves.get(0);
//        for (int i = 1; i < potentialNextMoves.size(); i++) {
//        	Rank r = potentialNextMoves.get(i);
//        	if (r.getOffenseGroup() < b.getOffenseGroup()) {
//        		b = r;
//        	}
//        	else if (r.getOffenseGroup() == b.getOffenseGroup() &&
//        			 r.getOffenseRank() > b.getOffenseRank()) {
//        		b = r;
//        	}
//        }
//        return b;
//    }
    public int getNumPotentials() {
        if (potentialNextMoves == null) return 0;
    	return potentialNextMoves.size();
    }
//    /**
//     * Gets the best offense potential move
//     * Used by offensive player methods
//     */
//    public Node getBestPotential(Rank r) {
//        potentialNextMoves.remove(r);
//    	Node n = new SimpleNode(r.move, 3 - player, Node.TYPE_UNKNOWN);
//        n.setDepth(depth + 1);
//        n.setParent(this);
//        
//        addNextMove(n);//not sure if that will cause problem if we've already
//        //scanned the same position
//        return n; 
//    }
    /**
     * Gets the best potential offense+defense
     * Used by defensive player methods
     */
    public Node getBestPotential() {
        if (potentialNextMoves == null || potentialNextMoves.isEmpty()) {
            return null;
        }
        Rank r = potentialNextMoves.remove(0);
        Node n = new SimpleNode(r.move, 3 - player, Node.TYPE_UNKNOWN);
        n.setDepth(depth + 1);
        n.setParent(this);

        addNextMove(n);//not sure if that will cause problem if we've already
        //scanned the same position
        return n; 
    }
    
    // make sure sorted by offense+defense rank
    public void setPotentialNextMoves(List<Rank> p) {
        this.potentialNextMoves = p;
    }
    public void clearPotentials() {
        potentialNextMoves = null;
    }
    public void setNextMoves(List<Node> nextMoves) {
        this.nextMoves = nextMoves;
        nextMovesLoaded = true;
    }
    public boolean allNextMovesLose() {
        if (potentialNextMoves != null && !potentialNextMoves.isEmpty()) {
            return false;
        }
        for (Node n : nextMoves) {
            if (n.getType() != Node.TYPE_LOSE) return false;
        }
        return true;
    }
    public List<Node> getNextMoves() {
        return nextMoves;
    }
    public boolean nextMovesLoaded() {
        return nextMovesLoaded;
    }
    public Node getNextMove(int position) {
        for (Iterator it = getNextMoves().iterator(); it.hasNext();) {
            SimpleNode n = (SimpleNode) it.next();
            if (n == null) {
                continue;
            }
            if (n.position == position) {
                return n;
            }
        }
        return null;
    }
    public Node getNextMoveSafe(int position) {
        Node n = getNextMove(position);
        if (n != null) {
            return n;
        }

        if (defaultNode == null) {
            defaultNode = createDefault(this);
        }
        return defaultNode;
    }
    public void addExistingNextMove(Node n) {

        if (nextMoves.contains(n)) {
            return;
        }
        nextMoves.add(n);
        nextMovesLoaded = true;
    }
    public void addNextMove(Node n) {
        if (nextMoves.contains(n)) {
            return;
        }

        nextMoves.add(n);
        n.setParent(this);
        nextMovesLoaded = true;
    }
    public void removeNextMove(Node n) {
        n.setParent(null);
        nextMoves.remove(n);
    }

    public void setDefaultNextMove(Node afterDefault) {
        if (afterDefault == null) {
            return;
        }

        if (defaultNode == null) {
            defaultNode = createDefault(this);
        }
        defaultNode.addNextMove(afterDefault);
    }
    public Node getDefaultNextMove() {
        if (defaultNode == null) {
            return null;
        }
        List moves = defaultNode.getNextMoves();
        if (moves.isEmpty()) {
            return null;
        }
        return (Node) moves.get(0);
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof SimpleNode)) return false;
        SimpleNode n = (SimpleNode) o;
        return n.hash == hash;
    }
    //this might cause problems....
    public int hashCode() {
        return (int) hash;
    }

    public String toString() {
        return hashCode() + " id=" + id + ",position=" + position + ",player=" + player + 
            ",type=" + type + ",hash=" + hash + ",rotation=" + rotation + 
            ",depth=" + depth + ",istwin="+isTwin + ",twins="+(twins!=null?twins.size():0);
    }
 
    public FastPenteStateZobrist getState() {
        int m[] = new int[depth];
        Node w = this;
        for (int i = depth - 1; i >= 0; i--) {
            m[i] = w.getPosition();
            w = w.getParent();
        }
        // create a pente state out of those nodes
        FastPenteStateZobrist fromNode = new FastPenteStateZobrist();
        for (int i = 0; i < m.length; i++) {
            fromNode.addMove(m[i]);
        }

        return fromNode;
    }
    public boolean isTwin()
    {
        return isTwin;
    }
    public void setTwin(boolean isTwin)
    {
        this.isTwin = isTwin;
    }
    
    private int heapIndex;
    public int getHeapIndex() {
        return heapIndex;
    }
    public void setHeapIndex(int i) {
        heapIndex = i;
    }
}