package org.pente.tree;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.apache.log4j.*;

import org.pente.database.*;

public class AITester {
    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure(new ConsoleAppender(new PatternLayout()));

        Category.getInstance(BestFirstScanner.class.getName()).setLevel(Level.INFO);
        Category.getInstance(PenteAnalyzer.class.getName()).setLevel(Level.ERROR);
        Category.getInstance(PositionAnalysis.class.getName()).setLevel(Level.INFO);

//        DBHandler dbHandler = new MySQLDBHandler(args[0], args[1], args[2], args[3]);
//        NodeSearcher nodeSearcher = new Cache2NodeSearcher(
//            new MySQLNodeSearcher(dbHandler));

        final AIBoardController controller = new AIBoardController();

        final AWTNodeEditor editor = new AWTNodeEditor(
                controller, "Tree", false);

        final Frame frame = new Frame();
        frame.setSize(850, 700);
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
