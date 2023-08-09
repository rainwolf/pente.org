package org.pente.tree;


public class CacheNodeSearcher extends LocalNodeSearcher {

    private HibernateNodeSearcher hibernateNodeSearcher;

    public CacheNodeSearcher(HibernateNodeSearcher hibernateNodeSearcher) {
        super();

        this.hibernateNodeSearcher = hibernateNodeSearcher;
    }

    public Node loadAll() throws NodeSearchException {
        Node n = hibernateNodeSearcher.loadAll();
        setRoot(n);
        return n;
    }

    public void storeAll() throws NodeSearchException {
        hibernateNodeSearcher.storeAll();
    }

    public void destroy() {
        hibernateNodeSearcher.destroy();
    }
}
