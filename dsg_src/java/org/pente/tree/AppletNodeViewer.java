package org.pente.tree;

import java.awt.*;
import java.applet.Applet;

// TODO implement local node searcher that reads in a root and  creates a hashmap
// to map hashcodes for later use by loadPosition(hash)
public class AppletNodeViewer extends Applet {

    private NodeSearcher nodeSearcher;
    private NodeBoardController controller;
    private AWTNodeEditor viewer;

    public void init() {

        try {
            String host = getCodeBase().getHost();
            int port = getCodeBase().getPort();
            String path = getParameter("nodeSearcherPath");
            nodeSearcher = new HttpNodeSearcher(host, port, path);

            controller = new NodeBoardController(nodeSearcher, true);

            viewer = new AWTNodeEditor(controller, "Tree", true);

            setLayout(new BorderLayout());
            add("Center", viewer);

        } catch (NodeSearchException nse) {
            System.err.println("Error creating http node searcher");
            nse.printStackTrace();
        }
    }

    public void start() {
        controller.load();
    }

    public void stop() {

    }

    public void destroy() {
        viewer.destroy();
        controller.destroy();
    }
}
