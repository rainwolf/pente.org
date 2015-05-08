package org.pente.tree;

import java.awt.*;
import java.awt.event.*;

import org.apache.log4j.BasicConfigurator;

//NOT USED
public class HibernateEditor {

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();
        
        NodeSearcher nodeSearcher = new LocalFileNodeSearcher(args[0]);
        //NodeSearcher nodeSearcher = new CacheNodeSearcher(
        //    new HibernateNodeSearcher(false));
        final NodeBoardController controller = new NodeBoardController(
            nodeSearcher, false);

        final AWTNodeEditor editor = new AWTNodeEditor(
            controller, "Tree", false);

        final Frame frame = new Frame();
        frame.setSize(550, 500);
        frame.add(editor);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                editor.destroy();
                controller.destroy();
                frame.dispose();
            }
        });

        controller.load();

        frame.setVisible(true);
    }
}