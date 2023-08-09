package org.pente.tutorial;

/**
 * SimpleTutorialScreen.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;

public class SimpleTutorialScreen extends Panel
        implements TutorialScreen, GridBoardListener {

    public static void main(String args[]) {

        final Frame f = new Frame("SimpleTutorialScreen");

        TutorialController controller =
                new SimpleTutorialBuilder().buildTutorial();
        final SimpleTutorialScreen panel = new SimpleTutorialScreen(
                controller.getSections());
        controller.setTutorialScreen(panel);

        f.add(panel, "Center");

        f.setSize(480, 600);
        f.setLocation(100, 100);


        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                panel.destroy();
                f.dispose();
                System.exit(0);
            }
        });

        f.setVisible(true);

        controller.switchSection("Rules");
    }

    //private PenteBoardCanvas gridBoardCanvas;
    //private GridBoardOrderedPieceCollectionAdapter gridBoard;
    //private PenteStatePieceCollectionAdapter penteState;

    private int game;
    private GridBoardCanvas gridBoardCanvas;
    private GridBoardOrderedPieceCollectionAdapter gridBoard;
    private GridState gridState;

    private GridState gridStates[];
    private GridBoardOrderedPieceCollectionAdapter gridBoards[];

    private TextArea textArea;
    private Label stepLabel;
    private Label stepNumberLabel;
    private Choice sectionChoice;

    private TutorialActionListener tutorialActionListener;

    private static final GridCoordinates coordinates[] = {
            new AlphaNumericGridCoordinates(19, 19),
            new TraditionalGridCoordinates(19, 19)};

    private static final GameStyles gameStyles =
            new GameStyles(new Color(44, 134, 47), //board back
                    new Color(188, 188, 188), //button back
                    Color.black, //button fore
                    new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                    Color.white, //player 1 back
                    Color.black, //player 1 fore
                    Color.black, //player 2 back
                    Color.white, //player 2 fore
                    new Color(188, 188, 188)); //watcher


    /**
     * Constructor for SimpleTutorialScreen.
     */
    public SimpleTutorialScreen(Enumeration sectionNames) {

        GameOptions gameOptions = new SimpleGameOptions(2);
        gameOptions.setPlayerColor(GameOptions.WHITE, 1);
        gameOptions.setPlayerColor(GameOptions.BLACK, 2);
        gameOptions.setDraw3DPieces(true);
        gameOptions.setPlaySound(true);
        gameOptions.setShowLastMove(true);


        // setup canvas
        PenteBoardCanvas penteCanvas = new PenteBoardCanvas();
        penteCanvas.gridCoordinatesChanged(coordinates[0]);
        penteCanvas.gameOptionsChanged(gameOptions);

        gridBoardCanvas = penteCanvas;
        gridBoardCanvas.addGridBoardListener(this);
        // end setup canvas

        // setup adapters
        GridBoardOrderedPieceCollectionAdapter penteAdapter =
                new PenteBoardOrderedPieceCollectionAdapter(penteCanvas, false);
        penteAdapter.setGridHeight(19);
        penteAdapter.setGridWidth(19);
        penteAdapter.setOnGrid(true);
        penteAdapter.setThinkingPiecePlayer(1);
        penteAdapter.setThinkingPieceVisible(false);
        penteAdapter.setDrawInnerCircles(true);

        gridBoards = new GridBoardOrderedPieceCollectionAdapter[]{penteAdapter, penteAdapter, penteAdapter, penteAdapter};
        gridBoard = gridBoards[0];
        // end setup adapters

        // setup grid states
        PenteStatePieceCollectionAdapter penteState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        penteState.addOrderedPieceCollectionListener(penteAdapter);

        PenteStatePieceCollectionAdapter keryoState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        keryoState.setCaptureLengths(new int[]{2, 3});
        keryoState.setCapturesToWin(15);
        keryoState.addOrderedPieceCollectionListener(penteAdapter);

        PenteStatePieceCollectionAdapter gpenteState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        gpenteState.setGPenteRules(true);
        gpenteState.addOrderedPieceCollectionListener(penteAdapter);

        GridStatePieceCollectionAdapter gomokuState = new GridStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        gomokuState.addOrderedPieceCollectionListener(penteAdapter);

        //PENTE 1
        //SPEED PENTE 2
        //KERYO 3
        //SPEED KERYO 4
        //GOMOKU 5
        //SPEED GOMOKU 6
        //D-PENTE 7
        //SPEED D-PENTE 8
        //G-PENTE 9
        //SPEED G-PENTE 10
        //POOF PENTE 11
        //SPEED POOF PENTE 12
        // insert nulls to match up with GridStateFactory.game ids
        gridStates = new GridState[]{null/*0 index is empty*/,
                new SynchronizedPenteState(penteState), null /*speed pente*/,
                new SynchronizedPenteState(keryoState), null /*speed keryo*/,
                new SynchronizedGridState(gomokuState), null /*speed gomoku*/,
                null, null, /*d-pente*/
                new SynchronizedPenteState(gpenteState), null /*speed gpente*/,
                null, null};
        gridState = gridStates[GridStateFactory.PENTE];
        gridBoard.setGameName(GridStateFactory.getGameName(GridStateFactory.PENTE));
        game = GridStateFactory.PENTE;
        // end grid states


        textArea = new TextArea("", 8, 10, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textArea.setEditable(false);
        textArea.setBackground(Color.white);

        sectionChoice = new Choice();
        for (; sectionNames.hasMoreElements(); ) {
            String section = (String) sectionNames.nextElement();
            sectionChoice.add(section);
        }
        sectionChoice.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                tutorialActionListener.switchSection(sectionChoice.getSelectedItem());
            }
        });

        Panel navigatePanel = new Panel();
        Button prevButton = gameStyles.createDSGButton("<<");
        prevButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                tutorialActionListener.prevStep();
            }
        });
        Button nextButton = gameStyles.createDSGButton(">>");
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                tutorialActionListener.nextStep();
            }
        });
        stepLabel = new Label("", Label.CENTER);
        stepLabel.setForeground(Color.black);
        stepNumberLabel = new Label("1 of 1", Label.CENTER);
        stepNumberLabel.setForeground(Color.black);

        navigatePanel.setLayout(new GridBagLayout());
        GridBagConstraints navGbc = new GridBagConstraints();
        navGbc.insets = new Insets(1, 1, 1, 1);
        navGbc.gridx = 1;
        navGbc.gridy = 1;
        navGbc.fill = GridBagConstraints.NONE;
        navigatePanel.add(sectionChoice, navGbc);

        navGbc.gridx++;
        navGbc.weightx = 1;
        navGbc.fill = GridBagConstraints.HORIZONTAL;
        navigatePanel.add(stepLabel, navGbc);

        navGbc.gridx++;
        navGbc.weightx = 0;
        navGbc.fill = GridBagConstraints.NONE;
        navigatePanel.add(prevButton, navGbc);

        navGbc.gridx++;
        navigatePanel.add(stepNumberLabel, navGbc);

        navGbc.gridx++;
        navigatePanel.add(nextButton, navGbc);


        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 8;
        gbc.fill = GridBagConstraints.BOTH;
        add(gridBoardCanvas, gbc);

        gbc.gridy++;
        gbc.weightx = 0;
        gbc.weighty = 0;
        add(navigatePanel, gbc);

        gbc.gridy++;
        gbc.weighty = 2;
        add(textArea, gbc);

        setBackground(Color.white);

        clear();
    }


    /**
     * @see org.pente.tutorial.TutorialScreen#clear()
     */
    public void clear() {
        textArea.setText("");
        stepLabel.setText("");
        gridBoardCanvas.clearPieces();
        gridBoardCanvas.setThinkingPiecePlayer(1);
        gridBoard.clearPieces();
        gridState.clear();
        gridBoardCanvas.setMessage(null);
    }

    public void destroy() {
        gridBoardCanvas.destroy();
    }

    /**
     * @see org.pente.tutorial.TutorialScreen#move(int, int, int)
     */
    public void addMove(int move) {

        gridBoardCanvas.setMessage(null);
        gridState.addMove(move);
        gridBoard.setThinkingPiecePlayer(gridState.getCurrentColor());
    }

    public void addMove(GridPiece p) {
        gridBoardCanvas.setMessage(null);
        gridBoardCanvas.addPiece(p);
    }

    /**
     * @see org.pente.tutorial.TutorialScreen#text(String)
     */
    public void text(String message) {
        textArea.setText(message);
    }

    /**
     * @see org.pente.tutorial.TutorialScreen#popup(String)
     */
    public void popup(final String message) {

//        if (frame instanceof DummyFrame) {
//            ((DummyFrame) frame).setDummyLocation();
//        }
//        DSGDialog dialog = new DSGDialog(
//            frame,
//            "Tutorial",
//            gameStyles,
//            message,
//            null,
//            "Ok",
//            null,
//            true);
//        dialog.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent ae) {
//                tutorialActionListener.popupClosed(message);
//            }
//        });
//        dialog.setVisible(true);
//        dialog.toFront();
        gridBoardCanvas.setMessage(message);
    }

    public void setStepLabel(String label) {
        stepLabel.setText(label);
    }

    public void setStepNumber(int current, int total) {
        stepNumberLabel.setText(current + " of " + total);
    }

    public void setThinkingPieceVisible(boolean visible) {
        gridBoard.setThinkingPieceVisible(visible);
    }

    public void addHighlightMove(GridPiece piece) {
        gridBoard.setHighlightPiece(piece);
    }

    /**
     * @see org.pente.tutorial.TutorialScreen#addTutorialActionListener(TutorialActionListener)
     */
    public void addTutorialActionListener(TutorialActionListener listener) {
        this.tutorialActionListener = listener;
    }

    public void switchSection(String name) {
        sectionChoice.select(name);
        gridBoardCanvas.setMessage(null);
    }

    public void gridMoved(int x, int y) {

    }

    public void gridClicked(int x, int y, int button) {
        gridBoardCanvas.setMessage(null);
        tutorialActionListener.moveMade(x, y);
    }

    public void switchGame(int newGame) {
        if (newGame != game) {
            game = newGame;
            gridState.clear();
            gridState = gridStates[newGame];
            //gridBoard = gridBoards[newGame];
            gridBoard.setGameName(GridStateFactory.getGameName(newGame));
        }
    }
}
