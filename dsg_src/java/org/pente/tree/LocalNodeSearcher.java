package org.pente.tree;

import java.util.*;

import org.pente.game.*;

/** Locally we can load the whole game tree and just load individual
 *  positions by navigating the positions through Node methods
 */
public abstract class LocalNodeSearcher implements NodeSearcher {

    Map<Long, Node> map = new HashMap<Long, Node>(1000);
    Node root;

    int clashes = 0;
    int hits = 0;
    int size = 0;
    
    public LocalNodeSearcher() {
    }
    public LocalNodeSearcher(Node root) {
        setRoot(root);
    }
    public void setRoot(Node root) {
        this.root = root;
        storeHashes(root);        
    }
    /** recursive function to visit all nodes in tree and store in hashmap */
    private void storeHashes(Node node) {
        long hash = node.getHash();
        Object o = map.get(hash);
        if (o == null) {
            map.put(hash, node);
        }
        else {
            System.out.println("collision?");
        }
        
        size++;
        
        for (Iterator it = node.getNextMoves().iterator(); it.hasNext();) {
            Node n = (Node) it.next();
            storeHashes(n);
        }
    }

    public Node loadAll() throws NodeSearchException {
        return root;
    }
    
    public Node loadPosition(MoveData moveData) {
        
        Node current = root;
        int moves[] = moveData.getMoves();
        for (int i = 0; i < moves.length; i++) {
            current = current.getNextMove(moves[i]);
            if (current == null) {
                return null;
            }
        }

        return current;
    }
    
    public Node loadPosition(long hash) {
        //return (Node) map.get(new Integer(hash));
        throw new UnsupportedOperationException("not supported, bad");
    }
    
    public Node loadPosition(GridState state) throws NodeSearchException {
        
        Object o = map.get(state.getHash());
        return (Node) o;//now that use better zobrist hashing, shouldn't
        // be collisions
//        if (o == null) {
//            return null;
//        }
//        else if (o instanceof Node) {
//            SimpleNode n = (SimpleNode) o;
//            FastPenteState s = n.getState();
//            if (s.positionEquals(state)) {
//                hits++;
//                return n;
//            }
//            else {
//                return null;
//            }
//        }
//        else {
//            List l = (List) o;
//            for (int i = 0; i < l.size(); i++) {
//                SimpleNode n = (SimpleNode) l.get(i);
//                FastPenteState s = n.getState();
//                if (s.positionEquals(state)) {
//                    hits++;
//                    return n;
//                }
//            }
//        }
//        return null;
    }
    
    public void storePosition(Node node) throws NodeSearchException {
        storeHashes(node);
    }
    
    public String toString() {
        return "[size=" + size + ", clashes=" + clashes + ", hits=" + hits + "]";
    }
}
