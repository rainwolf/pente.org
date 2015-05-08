package org.pente.tree;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.apache.log4j.BasicConfigurator;

import org.pente.database.*;

public class MySQLEditor {
    public static void main(String[] args) throws Throwable {

        //BasicConfigurator.configure();
         
        DBHandler dbHandler = new MySQLDBHandler(args[0], args[1], args[2], args[3]);
        NodeSearcher nodeSearcher = new Cache2NodeSearcher(
            new MySQLNodeSearcher(dbHandler));

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
