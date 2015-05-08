package org.pente.tree;

import java.io.*;
import java.net.*;

import org.pente.game.GridState;
import org.pente.game.MoveData;

public class HttpNodeSearcher implements NodeSearcher {

    private String host;
    private int port;
    private String path;
    private Node root;
    
    public HttpNodeSearcher(String host, int port, String path) throws NodeSearchException {
        this.host = host;
        this.port = port;
        this.path = path;
    }
    
    public Node loadAll() throws NodeSearchException {
        root = loadPosition(82592);
        return root;
    }

    public void storeAll() throws NodeSearchException {

    }

    public Node loadPosition(MoveData moveData) throws NodeSearchException {
        return null;
    }

    public Node loadPosition(GridState state) throws NodeSearchException {
        throw new UnsupportedOperationException("not implemented yet.");
    }
    public Node loadPosition(long hash) throws NodeSearchException {

        ObjectInputStream in = null;
        Node n = null;
        try {
            URL remoteNodeURL = new URL("http", host, port, path + "/" + hash);
            in = new ObjectInputStream(
                remoteNodeURL.openStream());
            n = (Node) in.readObject();

            in.close();
        } catch (Throwable t) {
            throw new NodeSearchException("Can't retrieve node from: " + 
                host + ", " + path, t);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ie) {}
            }
        }
        return n;
    }

    public void storePosition(Node node) throws NodeSearchException {

    }

    public void destroy() {

    }
}
