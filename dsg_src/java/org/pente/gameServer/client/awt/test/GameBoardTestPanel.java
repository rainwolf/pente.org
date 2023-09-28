package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.game.*;

public class GameBoardTestPanel extends Panel {

    private GridBoardCanvas canvas;
    private GridBoardOrderedPieceCollectionAdapter gridBoard;
    private GridState gridState;
    private GameOptionsDialog options;
    private CoordinatesListPanel coordsList;
    private ChatComponent chatComponent;
    private PlayerListComponent playerListComponent;

    private Button undoButton;
    private Button clearButton;

    // temp
    int players = 0;

    public GameBoardTestPanel(GameStyles gameStyle) {

        // setup canvas
        canvas = new PenteBoardCanvas();
        gridBoard = new PenteBoardOrderedPieceCollectionAdapter(
                (PenteBoardComponent) canvas, false);

        gridBoard.setGridHeight(19);
        gridBoard.setGridWidth(19);
        gridBoard.setOnGrid(true);
        gridBoard.setGameName("Boat-Pente");
        gridBoard.setDrawInnerCircles(true);
        gridBoard.setBackgroundColor(new Color(37, 186, 255).getRGB());
        // coordinates list
        coordsList = new CoordinatesListPanel(
                gameStyle, 2, new AWTDSGButton());
        coordsList.setGame(GridStateFactory.CONNECT6);
        coordsList.setPlayer(1, "peter");
        coordsList.setPlayer(2, "dweebo");

        int gridSize = (canvas.getOnGrid()) ? 19 : 18;
        GridCoordinates coordinates = new AlphaNumericGridCoordinates(gridSize, gridSize);
        canvas.gridCoordinatesChanged(coordinates);
        coordsList.gridCoordinatesChanged(coordinates);

        GameOptions gameOptions = new SimpleGameOptions(2);
        gameOptions.setPlayerColor(GameOptions.WHITE, 1);
        gameOptions.setPlayerColor(GameOptions.BLACK, 2);
        gameOptions.setDraw3DPieces(true);
        gameOptions.setPlaySound(true);
        gameOptions.setShowLastMove(true);
        canvas.gameOptionsChanged(gameOptions);
        coordsList.gameOptionsChanged(gameOptions);

        final PenteStatePieceCollectionAdapter penteState = new PenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        penteState.addOrderedPieceCollectionListener(gridBoard);
        penteState.addOrderedPieceCollectionListener(coordsList);
        // for keryo
        penteState.setCaptureLengths(new int[]{2, 3});
        penteState.setCapturesToWin(15);

        PoofPenteStatePieceCollectionAdapter poofPenteState = new PoofPenteStatePieceCollectionAdapter(new SimpleGomokuState(19, 19));
        poofPenteState.addOrderedPieceCollectionListener(gridBoard);
        poofPenteState.addOrderedPieceCollectionListener(coordsList);

        GridStatePieceCollectionAdapter connect6State = new GridStatePieceCollectionAdapter(
                new SimpleConnect6State(19, 19));
        connect6State.addOrderedPieceCollectionListener(gridBoard);
        connect6State.addOrderedPieceCollectionListener(coordsList);

        PenteStatePieceCollectionAdapter boatPenteState = new PenteStatePieceCollectionAdapter(
                new SimpleGomokuState(19, 19));
        boatPenteState.addOrderedPieceCollectionListener(gridBoard);

        gridState = boatPenteState;

        gridBoard.setThinkingPiecePlayer(1);
        gridBoard.setThinkingPieceVisible(true);

        canvas.setNewMovesAvailable(false);

        // this won't work, if need to test, need a dummy preferenceshandler
        chatComponent = new ChatPanel(5, 5, 3, null);
        playerListComponent = new PlayerListPanel(gameStyle.boardBack);

        coordsList.addOrderedPieceCollectionVisitListener(gridBoard);

        clearButton = gameStyle.createDSGButton("Clear");
        undoButton = gameStyle.createDSGButton("Undo");

// temp
        Button addPlayerButton = gameStyle.createDSGButton("Add Player");
        addPlayerButton.addActionListener(e -> {
            DSGPlayerData d = new SimpleDSGPlayerData();
            d.setName("player" + ++players);
            d.setPlayerType(DSGPlayerData.HUMAN);
            playerListComponent.addPlayer(d);
        });
        Button removePlayerButton = gameStyle.createDSGButton("Remove Player");
        removePlayerButton.addActionListener(e -> playerListComponent.removePlayer("player" + players--));
        Button clearPlayerButton = gameStyle.createDSGButton("Clear Player");
        clearPlayerButton.addActionListener(e -> playerListComponent.clearPlayers());
// end temp

        // end setup canvas

        // game options frame
        //options = new GameOptionsFrame(gameStyle,
        //                               gameOptions,
        //                               2);
        //options.addGameOptionsChangeListener(canvas);
        //options.addGameOptionsChangeListener(coordsList);

        coordsList.setHighlightColor(gameStyle.boardBack);
        // end game options frame

        final Panel navigatePanel = new Panel();
        navigatePanel.setLayout(new GridLayout(5, 1));
        navigatePanel.add(clearButton);
        navigatePanel.add(undoButton);
// temp
        navigatePanel.add(addPlayerButton);
        navigatePanel.add(removePlayerButton);
        navigatePanel.add(clearPlayerButton);
// end temp

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        add(coordsList, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(canvas, gbc);

        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(navigatePanel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        add((Component) chatComponent, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        add((Component) playerListComponent, gbc);


        setBackground(gameStyle.boardBack);


    }


    private void destroy() {
        canvas.destroy();
        //options.dispose();
        coordsList.destroy();
    }

    public static void main(String args[]) {

        GameStyles gameStyle = new GameStyles(new Color(0, 0, 153), //board back
                new Color(51, 102, 204), //button back
                Color.white, //button fore
                new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                Color.white, //player 1 back
                Color.black, //player 1 fore
                Color.black, //player 2 back
                Color.white, //player 2 fore
                new Color(51, 102, 204)); //watcher

        final GridState boatPenteState = GridStateFactory.createGridState(GridStateFactory.BOAT_PENTE);

        final GameBoardTestPanel panel = new GameBoardTestPanel(gameStyle);
        class MoveMaker implements GridBoardListener {
            public void gridClicked(int x, int y, int button) {
                int move = (19 - y - 1) * 19 + x;
                if (panel.gridState.isValidMove(move, panel.gridState.getCurrentPlayer())) {
                    panel.gridState.addMove(move);
                    panel.gridBoard.setThinkingPiecePlayer(panel.gridState.getCurrentPlayer());

                    boatPenteState.addMove(move);
                    System.out.println("game over = " + boatPenteState.isGameOver());
                    System.out.println("winner = " + boatPenteState.getWinner());
                }
            }

            public void gridMoved(int x, int y) {
            }
        }
        final MoveMaker moveMaker = new MoveMaker();

        panel.canvas.addGridBoardListener(moveMaker);
        panel.clearButton.addActionListener(e -> panel.gridState.clear());
        panel.undoButton.addActionListener(e -> panel.gridState.undoMove());

        // echo chat back to screen
        panel.chatComponent.addChatListener(message -> panel.chatComponent.newChatMessage("dweebo: " + message));
        panel.playerListComponent.setTableName("Test table");
        DSGPlayerData d = new SimpleDSGPlayerData();
        d.setName("dweebo");
        d.setPlayerType(DSGPlayerData.HUMAN);
        d.setNameColor(Color.red);
        panel.playerListComponent.addPlayer(d);

        panel.playerListComponent.setOwner("dweebo");


        final Frame f = new Frame("GridBoardTestPanel");

        f.add(panel, "Center");

        f.setSize(800, 600);
        f.setLocation(100, 100);


        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
                panel.destroy();
                System.exit(0);
            }
        });

        f.setVisible(true);
    }
}