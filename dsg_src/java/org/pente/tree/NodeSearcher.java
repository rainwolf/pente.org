package org.pente.tree;

import org.pente.game.*;

public interface NodeSearcher {

    /** Loads the full game tree of positions
     *  Useful for local clients, fast access
     *  @return Node Root of the game tree
     */
    public Node loadAll() throws NodeSearchException;
    
    /** Store the full game tree of positions
     *  Useful for local clients, faster access, small db's
     */
    public void storeAll() throws NodeSearchException;

    public Node loadPosition(long hash) throws NodeSearchException;
    /** replaces hash version which is bad */
    public Node loadPosition(GridState state) throws NodeSearchException;

    /** Store just a single position
     *  Useful for remote clients
     *  @param node The position to store
     */
    public void storePosition(Node node) throws NodeSearchException;
    
    public void destroy();
}
