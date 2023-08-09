package org.pente.gameServer.client.puzzle;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.core.AlphaNumericGridCoordinates;
import org.pente.gameServer.core.GridCoordinates;

public class PuzzleApplet extends Applet {

    private Frame frame;
    private GameBoard gameBoard;
    private GameStyles gameStyles;

    public void init() {
        gameStyles = new GameStyles();

        gameBoard = new GameBoard();
        gameBoard.setGridState(5);
        gameBoard.getGridBoard().setGameName("Poof-Pente");
    }

    public void start() {

        Button startButton = gameStyles.createDSGButton(
                "Load Watsu's Poof-Pente Puzzle");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                frame = new Frame("Puzzle");
                frame.add("Center", gameBoard);

                frame.setSize(600, 500);
                frame.setVisible(true);

                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        frame.dispose();
                        frame = null;
                    }
                });

                addMoves();
            }
        });

        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));
        setBackground(Color.white);
        add(startButton);

    }

    private void addMoves() {

        final GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);
        final String moves[] = new String[]{
                "K10", "A4", "A9", "A16", "C19", "A17", "E10", "B4", "E15", "B9",
                "F14", "C2", "G6", "C4", "K13", "C9", "L9", "C17", "M5", "D1", "M6",
                "G16", "M7", "D18", "N1", "E1", "N4", "E9", "N13", "G15", "O4", "F19",
                "O7", "G8", "O16", "A13", "P7", "H12", "P17", "K1", "Q3", "K18",
                "Q6", "L1", "Q8", "L13", "Q17", "L17", "Q18", "L19", "R2", "M1", "R6",
                "M8", "S7", "M11", "S17", "M12", "T17", "M14", "A19", "M19", "J17",
                "N7", "C12", "N19", "D11", "R8", "T15", "S16", "O13", "D13", "C13",
                "J7", "K9", "T19", "H6"
        };

//			old version
//			"K10", "E12", "C2", "G1", "D3", "G6", "D13", "H3", "E4", "H7",
//			"E14", "J1", "E18", "J2", "F12", "J13", "F14", "K8", "F17", "K12",
//			"G13", "M10", "H13", "N1", "H16", "N12", "J16", "N14", "K11", "O1",
//			"K13", "P15", "K14", "Q11", "K15", "R3", "L11", "R14", "M15", "S4",
//			"N19", "S12", "O18", "S17", "P14", "S18", "P17", "S19", "Q1", "T6",
//			"Q15", "T7", "Q16", "T16", "R1", "C3", "B4", "D4", "C5", "C7", "C6",
//			"C4", "R13", "A1", "S1", "A19", "T9", "A18", "T10"
//		};

        new Thread(new Runnable() {
            public void run() {

                gameBoard.setCursor(Cursor.WAIT_CURSOR);
                gameBoard.setMessage("Loading puzzle...");

                gameBoard.getGridState().clear();
                for (int i = 0; i < moves.length; i++) {
                    Point move = coordinates.getPoint(moves[i]);
                    int m = gameBoard.getGridState().convertMove(move.x, 18 - move.y);
                    gameBoard.getGridState().addMove(m);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                    }
                }

                gameBoard.setCursor(Cursor.DEFAULT_CURSOR);
                gameBoard.setMessage(null);
            }
        }).start();
    }

    public void stop() {
    }

    public void destroy() {
        if (gameBoard != null) {
            gameBoard.destroy();
        }
        if (frame != null) {
            frame.dispose();
        }
    }
}
