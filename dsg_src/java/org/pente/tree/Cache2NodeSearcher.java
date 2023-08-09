package org.pente.tree;

import java.util.*;

import org.apache.log4j.Category;

import org.pente.game.GridState;

public class Cache2NodeSearcher implements NodeSearcher {

    private static final Category log4j = Category.getInstance(
            Cache2NodeSearcher.class.getName());

    public NodeSearcher baseSearcher;
    private Node root;
    private Map<Long, Node> nodes = new HashMap<Long, Node>();

    public Cache2NodeSearcher(NodeSearcher baseSearcher) {
        this.baseSearcher = baseSearcher;
    }

    public void destroy() {
        baseSearcher.destroy();
    }

    //not really, just loading root
    public Node loadAll() throws NodeSearchException {
        root = baseSearcher.loadAll();
        cache(root);
        return root;
    }


    public Node loadPosition(long hash) throws NodeSearchException {
        //log4j.info("loadPosition("+hash+")");
        Node n = nodes.get(hash);
        if (n == null) {
            n = baseSearcher.loadPosition(hash);
            cache(n);
        }
        // it will probably be that the position is loaded but not the children
        else if (n.isStored() && !n.nextMovesLoaded()) {
            Node n2 = baseSearcher.loadPosition(hash);
            if (n2 != null) {
                cache(n2);
                n = n2;
            }
        }

        return n;
    }

    private void cache(Node n) {
        if (n != null) {
            // store in cache
            nodes.put(n.getHash(), n);
            // store children in cache
            for (Node n2 : n.getNextMoves()) {
                nodes.put(n2.getHash(), n2);
            }
            if (n.getHash() != 0) { //if not root
                Node p = nodes.get(n.getParentHash());
                if (p != null) {
                    p.addNextMove(n);
                }
            }
        }
    }

    public Node loadPosition(GridState state) throws NodeSearchException {
//        Node n = root;
//        int moves[] = state.getMoves();
//        for (int i = 0; i < moves.length; i++) {
//            n = n.getNextMove(moves[i]);
//            if (n == null) {
//                break;
//            }
//        }
//        if (n == null) {
//            n = loadPosition(state.getHash());
//        }
        return loadPosition(state.getHash());
    }

    public void storeAll() throws NodeSearchException {
        storeChanged(root);
    }

    private void storeChanged(Node n) throws NodeSearchException {
        if (n.nodeNeedsWrite()) {
            log4j.info("store(" + n.getHash() + ")");
            baseSearcher.storePosition(n);
        }
        for (Node n2 : n.getNextMoves()) {
            storeChanged(n2);
        }
    }

    public void storePosition(Node node) throws NodeSearchException {
        //System.out.println("stored " + node.getHash() + " " + node.getParentHash() + " " + node.nodeNeedsWrite());
        cache(node);//needed?


        //baseSearcher.storePosition(node);
        //only store when storeall called
    }

}
