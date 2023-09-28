package org.pente.tree;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.client.awt.*;

//TODO in future, might have ai-like thingy predict yellow moves (in threat positions)
//TODO in future, might have ai-like thingy verify end game positions

public class AWTNodeEditor extends Panel {

    private AWTBoard board;
    private TextArea comments;


    /**
     * could make this settable later
     */
    public static final GameStyles gs =
            new GameStyles(new Color(0, 102, 153), //board back
                    new Color(188, 188, 188), //button back
                    Color.black, //button fore
                    new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                    Color.white, //player 1 back
                    Color.black, //player 1 fore
                    Color.black, //player 2 back
                    Color.white, //player 2 fore
                    new Color(188, 188, 188)); //watcher


    public AWTNodeEditor(final NodeBoardListener controller,
                         String name, boolean readOnly) {

        super();

        board = new AWTBoard(gs, readOnly);
        controller.registerView(this, board);

        // specify how high comment area is with "height"
        comments = new TextArea(5, 4);

        setLayout(new GridBagLayout());
        setBackground(gs.boardBack);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 100;
        c.weighty = 98;
        add(board, c);

        c.gridy = 2;
        c.weighty = 1;
        add(comments, c);

        if (!readOnly) {
            Button defaultButton = gs.createDSGButton("Default");
            defaultButton.addActionListener(e -> controller.toggleDefault());

            Button saveButton = gs.createDSGButton("Save");
            saveButton.addActionListener(e -> controller.store());


            final TextField maxDepth = new TextField("10");
            final TextField maxNodes = new TextField("2000");
            final Button scanButton = gs.createDSGButton("Scan");
            scanButton.addActionListener(e -> {
                if (scanButton.getLabel().equals("Scan")) {
                    String maxDStr = maxDepth.getText();
                    String maxNStr = maxNodes.getText();
                    int maxD = 10;
                    int maxN = 200;
                    if (maxDStr != null) {
                        maxD = Integer.parseInt(maxDStr);
                    }
                    if (maxNStr != null) {
                        maxN = Integer.parseInt(maxNStr);
                    }
                    scanButton.setLabel("Stop");
                    controller.scan(maxD, maxN);
                } else {
                    scanButton.setLabel("Scan");
                    controller.stop();
                }
            });


            Button singleScanButton = gs.createDSGButton("Single Scan");
            singleScanButton.addActionListener(e -> controller.singleScan());


            Panel buttonPanel = new Panel();
            buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
            buttonPanel.add(defaultButton);
            buttonPanel.add(saveButton);
            buttonPanel.add(new Label("Max Depth:"));
            buttonPanel.add(maxDepth);
            buttonPanel.add(new Label("Max Nodes:"));
            buttonPanel.add(maxNodes);
            buttonPanel.add(scanButton);
            buttonPanel.add(singleScanButton);
            if (controller instanceof AIBoardController) {
                Button findPositionButton = gs.createDSGButton("RankTreePos");
                findPositionButton.addActionListener(e -> ((AIBoardController) controller).findPositionInRankTree());
                buttonPanel.add(findPositionButton);
            }
            c.gridy = 3;
            c.weighty = 1;
            add(buttonPanel, c);
        }

        Panel scannedPanel = new Panel();
        scannedPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        Button nextButton = gs.createDSGButton("Next Scanned");
        nextButton.addActionListener(e -> controller.visitNextScanned());
        Button prevButton = gs.createDSGButton("Prev Scanned");
        prevButton.addActionListener(e -> controller.visitPrevScanned());
        scannedPanel.add(nextButton);
        scannedPanel.add(prevButton);

        c.gridy = 4;
        add(scannedPanel, c);
    }

    public void destroy() {
        board.destroy();
    }

    public void setComment(String comment) {
        comments.setText(comment);
    }

    public String getComment() {
        return comments.getText();
    }
}
