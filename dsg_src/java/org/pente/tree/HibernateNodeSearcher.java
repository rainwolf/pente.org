package org.pente.tree;

import java.util.*;

import org.pente.game.*;

import org.apache.log4j.*;

import net.sf.hibernate.*;
import net.sf.hibernate.cfg.*;

//NOT USED
public class HibernateNodeSearcher implements NodeSearcher {

    private static final Category log4j = Category.getInstance(
            HibernateNodeSearcher.class.getName());

    private static final SessionFactory sessionFactory;

    static {
        try {
            Configuration cfg = new Configuration()
                    .addClass(org.pente.tree.SimpleNode.class);

            sessionFactory = cfg.buildSessionFactory();
        } catch (HibernateException ex) {
            throw new RuntimeException("Exception building SessionFactory: " + ex.getMessage(), ex);
        }
    }

    public static void main(String args[]) throws Throwable {

        HibernateNodeSearcher s = new HibernateNodeSearcher(true);

        GridState state = GridStateFactory.createGridState(GridStateFactory.PENTE);

        Node r = SimpleNode.createRoot();
        s.root = r;
        Node move1 = new SimpleNode(180, 1, Node.TYPE_UNKNOWN);
        state.addMove(180);
        move1.setHash(state.getHash());
        move1.setRotation(state.getRotation());
        r.addNextMove(move1);
/*
        Node n = new Node(181, 2, Node.TYPE_UNKNOWN);
        state.addMove(181);
        state.updateHashes();
        n.setHash(state.getHash());
        n.setRotation(state.getRotation());
        move1.addNextMove(n);
        Node n2 = new Node(183, 1, Node.TYPE_UNKNOWN);
        state.addMove(183);
        state.updateHashes();
        n2.setHash(state.getHash());
        n2.setRotation(state.getRotation());
        n.addNextMove(n2);
        
        Node n3 = new Node(184, 1, Node.TYPE_LOSE);
        state.undoMove();
        state.addMove(184);
        state.updateHashes();
        n3.setHash(state.getHash());
        n3.setRotation(state.getRotation());
        n.addNextMove(n3);
*/
//        Node r = s.loadAll();
//        
//        Node n = new Node(181, 2, Node.TYPE_UNKNOWN);
//        state.addMove(180);
//        state.addMove(181);
//        state.updateHashes();
//        n.setHash(state.getHash());
//        r.addNextMove(n);

        s.session.save(r);
        s.storeAll();

        s.destroy();
    }

    private Session session;
    private Node root;
    private boolean lazy = true;

    public HibernateNodeSearcher(boolean lazy) throws NodeSearchException {
        this.lazy = lazy;
        try {
            session = sessionFactory.openSession();
        } catch (HibernateException h) {
            throw new NodeSearchException("", h);
        }
    }

    public Node loadAll() throws NodeSearchException {

        try {
            root = (SimpleNode) session.get(SimpleNode.class, Long.valueOf(1));
            if (!lazy) {
                // visit all nodes to load them from hibernate
                List toVisit = new ArrayList(100);
                toVisit.add(root);
                while (!toVisit.isEmpty()) {
                    Node n = (Node) toVisit.remove(0);
                    for (Iterator it = n.getNextMoves().iterator(); it.hasNext(); ) {
                        Node n2 = (Node) it.next();
                        if (n2 != null) {
                            toVisit.add(n2);
                        }
                    }
                }
            }

        } catch (Throwable t) {
            log4j.error("loadAll error", t);
            throw new NodeSearchException("loadAll error", t);
        }

        return root;
    }

    public void storeAll() throws NodeSearchException {

        try {
            // should just have to flush to store changes
            session.flush();

        } catch (Throwable t) {
            log4j.error("storeAll error", t);
            throw new NodeSearchException("storeAll error", t);
        }
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

    public Node loadPosition(long hash) throws NodeSearchException {
        try {
            List l = (List) session.find("from SimpleNode node where node.hash = ?",
                    hash,
                    Hibernate.LONG);
            if (l == null || l.isEmpty()) {
                return null;
            }
            return (Node) l.get(0);

        } catch (Throwable t) {
            throw new NodeSearchException("loadPosition error", t);
        }
    }

    public Node loadPosition(GridState state) throws NodeSearchException {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    public void storePosition(Node node) throws NodeSearchException {
        storeAll();
    }

    public void destroy() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (HibernateException he) {
            log4j.error("destroy() close hibernate session error", he);
        }
    }
}
