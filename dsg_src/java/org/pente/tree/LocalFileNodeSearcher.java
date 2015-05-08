package org.pente.tree;

import java.io.*;

import org.pente.game.GridState;
import org.pente.game.GridStateFactory;

//NOT USED
public class LocalFileNodeSearcher extends LocalNodeSearcher {

    private String fileName;
    
    public LocalFileNodeSearcher(String fileName)
        throws ClassNotFoundException, IOException, NodeSearchException {
        
        this.fileName = fileName;

        Node n = null;

        File f = new File(fileName);
        if (!f.exists()) {
            n = SimpleNode.createRoot();
            GridState state = GridStateFactory.createGridState(
                GridStateFactory.PENTE);

            Node move1 = new SimpleNode(180, 1, Node.TYPE_UNKNOWN);
            state.addMove(180);
            move1.setHash(state.getHash());
            move1.setRotation(state.getRotation());
            move1.setDepth(1);
            n.addNextMove(move1);
            //setRoot(n);
            setRoot(move1);
            storeAll();
        }
        else {
            ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(fileName));
            n = (SimpleNode) in.readObject();
            in.close();
            setRoot(n);
        }
    }

    public void storeAll() throws NodeSearchException{

        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(
                new FileOutputStream(fileName));
            out.writeObject(root);
            out.close();

        } catch (IOException ie) {
            throw new NodeSearchException("Local store problem", ie);
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ie) {}
            }
        }
    }

    /** storing to file, no way to store just updates to this node so
     *  just store everything again
     */
    public void storePosition(Node node) throws NodeSearchException {
        storeAll();
    }

    public void destroy() {
        
    }
}
