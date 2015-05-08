/** GameBoardFrame.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client.awt;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.applet.AudioClip;

import javax.swing.JPanel;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.game.*;
import org.pente.gameServer.event.*;


public class GameBoardFrame extends Frame implements TableComponent,
    ActionListener {

    private static final int PLAYERTYPE_NOT_SITTING = 3;
    private static final int MAX_PLAYERS = 2;

    private int tableNum;
    private DSGPlayerData me;
    private String playerName;
    private int playerType;
    private boolean sitting;
    private boolean owner;
    
    private int state;
    private int timerMinutes = 20;
    private int timerIncremental = 0;
    private boolean timed = true;
    private int game;
    

    private String sittingPlayers[] = new String[MAX_PLAYERS + 1];
    // playing players is set just for the game in case players
    // get kicked off we still know who was playing
    private String playingPlayers[] = new String[MAX_PLAYERS + 1];

    private GameStyles gameStyle;

    private CoordinatesListPanel coordinatesList;

    private static final GridCoordinates coordinates[] = {
        new AlphaNumericGridCoordinates(19, 19),
        new TraditionalGridCoordinates(19, 19)
    };

    //private CardLayout cardLayout;
    //private Panel panelCardLayout;
    //private GridBoardCanvas gridBoardCanvas;
    //private GridBoardOrderedPieceCollectionAdapter gridBoard;
    //private GridState gridState;

    //private GridState gridStates[];
    //private GridBoardOrderedPieceCollectionAdapter gridBoards[];
    //private GridBoardCanvas gridCanvases[];
	private GameBoard gameBoard;
	
    public ChatComponent chatArea;
    private PlayerListPanel playerList;

    private Sounds sounds;


    private Choice      gameChoice;

    private static final String PLAY = "Play";
    private static final String UNDO = "Undo";
    private static final String EMAIL = "Email Game";
    private static final String SIT1 = "Sit 1";
    private static final String SIT2 = "Sit 2";
    private static final String STAND = "Stand";
    private static final String SET_TIME = "Set Timer";
    private static final String RESIGN = "Resign";
    private static final String CANCEL = "Cancel";
    private static final String OPTIONS = "Options";

    private static final String RATED = "Rated";
    
    
    private DSGDialog undoDialog;
    private DSGDialog cancelDialog;
    private PlayerLeftDialog playerReturnTimeUpDialog;
    private DSGDialog resignDialog;
    private DSGDialog swapDialog;
    private MiddleSetDialog middleSetDialog;
    
    private GameOptions gameOptions;

    private Checkbox ratedCheck;
    private Button setTimeButton;
    private Label timedLabel;

    private int tableType = DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC;
    private Choice tableTypeChoice;
    private PlayerListPanel bootList;
    private Button bootButton;
    private BootDialog bootDialog;
    private PlayerListPanel inviteList;
    private Button inviteButton;
    private InviteDialog inviteDialog;
    private Button playAIButton;
    private AddAIDialog addAIDialog;

    private GameTimer setTimer;
    private int gameNumInSet;
    private Panel gameInSetPanel;
    private CardLayout gameInSetLayout;
    private Label gameInSetLabel;
    
    private Label playerTimerLabels[];
    private GameTimer playerGameTimers[];

    private Button sit1Button;
    private Button sit2Button;

	private boolean clickedPlay = false;
	
    private DSGEventListener dsgEventListener;

    private PlayerDataCache playerDataCache;
    private Vector aiData;
    
    private PreferenceHandler preferenceHandler;
    // the size of the table window initially upon opening
    // checked against size when closing window to save pref.
    private Dimension initialSize;

    private SetTimerListener setTimerListener;
    private ActionListener addAIListener;
    
    
    public GameBoardFrame(final String host,
                          final GameStyles gameStyle,
                          final int tableNum,
                          final DSGEventListener dsgEventListener,
                          final DSGPlayerData me,
                          final Vector playersInTable,
                          String sittingPlayers[],
                          Sounds sounds,
                          final Vector aiDataVector,
                          final PlayerListComponent mainRoomPlayers,
                          final PlayerDataCache playerDataCache,
                          final PreferenceHandler preferenceHandler) {

        super("Table " + tableNum);

        this.gameStyle = gameStyle;
        this.tableNum = tableNum;
        this.me = me;
        this.playerName = me.getName();
        this.dsgEventListener = dsgEventListener;
        this.playerDataCache = playerDataCache;
        this.sounds = sounds;
        this.aiData = aiDataVector;
        this.preferenceHandler = preferenceHandler;

        playerType = PLAYERTYPE_NOT_SITTING;
        state = DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;


        final Button playButton = gameStyle.createDSGButton(PLAY);
        playButton.addActionListener(this);


        final Button undoButton = gameStyle.createDSGButton(UNDO);
        undoButton.addActionListener(this);

        final Button cancelButton = gameStyle.createDSGButton(CANCEL);
        cancelButton.addActionListener(this);


        final Button emailButton = gameStyle.createDSGButton(EMAIL);
        emailButton.addActionListener(this);
        if (me.isGuest()) {
        	emailButton.setEnabled(false);
        }


        sit1Button = gameStyle.createDSGButton(SIT1);
        sit1Button.addActionListener(this);

        
        sit2Button = gameStyle.createDSGButton(SIT2);
        sit2Button.addActionListener(this);


        final Button resignButton = gameStyle.createDSGButton(RESIGN);
        resignButton.addActionListener(this);


        Button gameOptionsButton = gameStyle.createDSGButton(OPTIONS);
        gameOptionsButton.addActionListener(this);



        ItemListener changeStateListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                sendChangeTableState();
            }
        };


        ratedCheck = new Checkbox(RATED);
        ratedCheck.setBackground(gameStyle.boardBack);
        ratedCheck.setForeground(gameStyle.foreGround);
        ratedCheck.setEnabled(false);
        ratedCheck.setState(true);
        ratedCheck.addItemListener(changeStateListener);

        timedLabel = new Label("");
        updateTimedLabel();
        timedLabel.setBackground(gameStyle.boardBack);
        timedLabel.setForeground(gameStyle.foreGround);

        setTimerListener = new SetTimerListener() {
            public void setTimer(boolean t, int m, int s) {
                timed = t;
                timerMinutes = m;
                timerIncremental = s;
                sendChangeTableState();
            }
        };

        setTimeButton = gameStyle.createDSGButton(SET_TIME);
        setTimeButton.setEnabled(false);
        setTimeButton.addActionListener(this);


        playerTimerLabels = new Label[MAX_PLAYERS + 1];
        playerGameTimers = new GameTimer[MAX_PLAYERS + 1];
        
        playerGameTimers[1] = new SimpleGameTimer();
        playerGameTimers[1].setStartMinutes(15);
        playerTimerLabels[1] = new Label("20:00", Label.CENTER);
        playerTimerLabels[1].setForeground(gameStyle.foreGround);
        playerGameTimers[1].addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int newMinutes, int newSeconds) {
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                playerTimerLabels[1].setText(newMinutes + ":" + newSecondsStr);
            }
        });

        playerGameTimers[2] = new SimpleGameTimer();
        playerGameTimers[2].setStartMinutes(15);
        playerTimerLabels[2] = new Label("20:00", Label.CENTER);
        playerTimerLabels[2].setForeground(gameStyle.foreGround);
        playerGameTimers[2].addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int newMinutes, int newSeconds) {
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                playerTimerLabels[2].setText(newMinutes + ":" + newSecondsStr);
            }
        });


        class TimerUp implements GameTimerListener {
            public void timeChanged(int newMinutes, int newSeconds) {
                if (newMinutes == 0 && newSeconds == 0) {
                    playerGameTimers[1].stop();
                    playerGameTimers[2].stop();
                }
            }
        }
        TimerUp timerUp = new TimerUp();
        playerGameTimers[1].addGameTimerListener(timerUp);
        playerGameTimers[2].addGameTimerListener(timerUp);

        Panel underBoard=new Panel();
        underBoard.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        underBoard.add(sit1Button);
        underBoard.add(playerTimerLabels[1]);
        underBoard.add(ratedCheck);
        underBoard.add(timedLabel);
        underBoard.add(setTimeButton);
        underBoard.add(playerTimerLabels[2]);
        underBoard.add(sit2Button);


        // create main player list and boot player list
        playerList = new PlayerListPanel(new Color(0, 51, 102));
        bootList = new PlayerListPanel(new Color(0, 51, 102));
        playerList.setTableName("Table " + tableNum);
        playerList.showNumPlayers(true);
        bootList.setTableName("Boot Player");

        for (int i = 0; i < playersInTable.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) playersInTable.elementAt(i);
            playerList.addPlayer(d);
            if (!d.getName().equals(playerName)) {
                bootList.addPlayer(d);
            }
        }

        playerList.addGetStatsListener(new PlayerActionAdapter() {
            public void actionRequested(String name) {
                new PlayerStatsDialog(GameBoardFrame.this,
                    GameBoardFrame.this.getLocationOnScreen(),
                    name, host, gameStyle, playerDataCache, dsgEventListener,
                    name.equals(playerName));
            }
        });
        // end player list
        // create invite player list
        inviteList = new PlayerListPanel((PlayerListPanel) mainRoomPlayers);
        inviteList.setTableName("Invite Player");
        inviteList.removePlayer(playerName);
        // remove all players in this room
        for (int i = 0; i < playersInTable.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) playersInTable.elementAt(i);
            inviteList.removePlayer(d.getName());
        }

        for (Enumeration e = playerDataCache.getAllPlayers(); e.hasMoreElements();) {
            DSGPlayerData d = (DSGPlayerData) e.nextElement();
            if (d.isComputer()) {
                inviteList.removePlayer(d.getName());
            }
        }
        // end invite player list

        playerDataCache.addChangeListener(playerList);
        playerDataCache.addChangeListener(bootList);
        playerDataCache.addChangeListener(inviteList);

        Label tableTypeLabel = new Label("Table Type:");
        tableTypeLabel.setForeground(gameStyle.foreGround);
        tableTypeChoice = new Choice();
        tableTypeChoice.addItemListener(changeStateListener);
        tableTypeChoice.add("Public");
        tableTypeChoice.add("Private");
        tableTypeChoice.select(0);
        tableTypeChoice.setBackground(Color.white);
        tableTypeChoice.setForeground(Color.black);
        tableTypeChoice.setEnabled(false);

        bootButton = gameStyle.createDSGButton("Boot Player");
        bootButton.setEnabled(false);
        bootButton.addActionListener(this);
        

        addAIListener = new ActionListener() {
            public void actionPerformed(ActionEvent e2) {
                dsgEventListener.eventOccurred(
                    new DSGAddAITableEvent(
                    playerName, tableNum, addAIDialog.getData()));
            }
        };
        
        inviteButton = gameStyle.createDSGButton("Invite Player");
        inviteButton.setEnabled(false);
        inviteButton.addActionListener(this);

		playAIButton = gameStyle.createDSGButton("Play Computer");
		playAIButton.setEnabled(false);
		playAIButton.addActionListener(this);
		
        Label gameLabel = new Label("Game:");
        gameLabel.setForeground(gameStyle.foreGround);

        gameChoice = new Choice();
        Game games[] = GridStateFactory.getNormalGames();
        for (int i = 0; i < games.length; i++) {
            gameChoice.add(games[i].getName());
        }
        
        gameChoice.addItemListener(changeStateListener);
        gameChoice.setBackground(Color.white);
        gameChoice.setForeground(Color.black);
        gameChoice.setEnabled(false);

        
        Panel gamePanel = new Panel();
        gamePanel.setLayout(new GridBagLayout());
        GridBagConstraints gamePanelConstraints = new GridBagConstraints();
        gamePanelConstraints.anchor = GridBagConstraints.NORTH;
        gamePanelConstraints.fill = GridBagConstraints.HORIZONTAL;
        gamePanelConstraints.insets = new Insets(1, 1, 1, 1);

        gamePanelConstraints.gridx = 1;
        gamePanelConstraints.gridy = 1;
        gamePanel.add(gameLabel, gamePanelConstraints);
        
        gamePanelConstraints.gridy++;
        gamePanel.add(gameChoice, gamePanelConstraints);

        gamePanelConstraints.gridy++;
        gamePanel.add(playButton, gamePanelConstraints);
        
        gamePanelConstraints.gridy++;
        gamePanel.add(undoButton, gamePanelConstraints);
        
        gamePanelConstraints.gridy++;
        gamePanel.add(cancelButton, gamePanelConstraints);

        gamePanelConstraints.gridy++;
        gamePanel.add(resignButton, gamePanelConstraints);
        
        gamePanelConstraints.gridy++;
        gamePanel.add(gameOptionsButton, gamePanelConstraints);

        gamePanelConstraints.gridy++;
        gamePanel.add(tableTypeLabel, gamePanelConstraints);

        gamePanelConstraints.gridy++;
        gamePanel.add(tableTypeChoice, gamePanelConstraints);
        
        gamePanelConstraints.gridy++;
        gamePanel.add(bootButton, gamePanelConstraints);

        gamePanelConstraints.gridy++;
        gamePanel.add(inviteButton, gamePanelConstraints);

		gamePanelConstraints.gridy++;
		gamePanel.add(playAIButton, gamePanelConstraints);
        

        chatArea = new ChatPanel(5, 5, 3, preferenceHandler/*, me.isGuest()*/);
        chatArea.addChatListener(new ChatListener() {
            public void chatEntered(final String message) {
                dsgEventListener.eventOccurred(new DSGTextTableEvent(null, tableNum, message));
            }
        });


        Panel chatPanel = new Panel();
        chatPanel.setLayout(new BorderLayout(0, 0));
        chatPanel.add("Center", (Panel) chatArea);

        // setup game options
        gameOptions = (GameOptions) preferenceHandler.getPref("gameOptions");
        if (gameOptions == null) {
            gameOptions = new SimpleGameOptions(2);
            gameOptions.setPlayerColor(GameOptions.WHITE, 1);
            gameOptions.setPlayerColor(GameOptions.BLACK, 2);
            gameOptions.setDraw3DPieces(true);
            gameOptions.setPlaySound(true);
            gameOptions.setShowLastMove(true);
        }
        // end setup game options

        // setup coordinates list
        coordinatesList = new CoordinatesListPanel(gameStyle, 2, new AWTDSGButton());
        coordinatesList.setHighlightColor(gameStyle.boardBack);
        coordinatesList.gridCoordinatesChanged(coordinates[0]);
        coordinatesList.gameOptionsChanged(gameOptions);
		int numSitting = 0;
        for (int i = 1; i < sittingPlayers.length; i++) {
            if (sittingPlayers[i] != null) {
				numSitting++;
                receivePlayerSit(new DSGSitTableEvent(sittingPlayers[i], tableNum, i));
            }
        }
        // end setup coordinates list

		gameBoard = new GameBoard(gameOptions, coordinates[0],
			new GridBoardController(), coordinatesList, false);
		
		game = GridStateFactory.PENTE;

        final Choice coordsChoice = new Choice();
        coordsChoice.add("Alpha-Numeric");
        coordsChoice.add("Traditional");
        coordsChoice.setBackground(Color.white);
        coordsChoice.setForeground(Color.black);
        coordsChoice.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                int coordsIndex = coordsChoice.getSelectedIndex();
                gameBoard.gridCoordinatesChanged(coordinates[coordsIndex]);
                coordinatesList.gridCoordinatesChanged(coordinates[coordsIndex]);
            }
        });

        gameInSetLabel = new Label("");
        gameInSetLabel.setForeground(gameStyle.foreGround);
        
        gameInSetLayout = new CardLayout();
        gameInSetPanel = new Panel();
        gameInSetPanel.setLayout(gameInSetLayout);
        gameInSetPanel.add(gameInSetLabel, "set");
        gameInSetPanel.add(new Label(""), "game");
        gameInSetLayout.show(gameInSetPanel, "game");
        
        Panel coordsPanel = new Panel();
        coordsPanel.setLayout(new BorderLayout());
        Panel bottomCoordsPanel = new Panel();
        bottomCoordsPanel.setLayout(new BorderLayout(2, 2));
        bottomCoordsPanel.add(coordsChoice, "North");
        bottomCoordsPanel.add(emailButton, "Center");
        bottomCoordsPanel.add(gameInSetPanel, "South");
        coordsPanel.add(coordinatesList, "West");
        coordsPanel.add(bottomCoordsPanel, "South");



        Panel panel = new InsetPanel(2, 2, 2, 2);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);
        
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTH;
        panel.add(gamePanel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(gameBoard, gbc);

        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(coordsPanel, gbc);

        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(underBoard, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(chatPanel, gbc);

        gbc.gridx = 3;
        gbc.gridwidth = 1;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(playerList, gbc);

        panel.setBackground(gameStyle.boardBack);
        add(panel, "Center");

        pack();
        setLocation(0, 0);

        Dimension sizePref = (Dimension) preferenceHandler.getPref("tableSize");

        if (sizePref != null) {
            // make sure window will fit on users current screen size
            // (user could have changed resolutions or computers since last login)
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            if (sizePref.height <= screenSize.height &&
                sizePref.width <= screenSize.width) {
                setSize(sizePref);
            }
            else {
                sizePref = getSize();
            }
        }
        else {
            sizePref = getSize();
        }        
        initialSize = sizePref;
        
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dsgEventListener.eventOccurred(
                    new DSGExitTableEvent(null, tableNum, false, false));
            }
        });
            
        setVisible(true);
		
		if (numSitting != 2) {
			gameBoard.setMessage("Click Sit 1 or Sit 2 to start a game");
		}
    }

    public void actionPerformed(ActionEvent event) {
        if (event.getActionCommand().equals(PLAY)) {
            dsgEventListener.eventOccurred(new DSGPlayTableEvent(null, tableNum));

			// if a valid click (as far as client can tell)
			if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
			    amSitting() && sittingPlayers[1] != null && sittingPlayers[2] != null) {
				clickedPlay = true;
				updateMessage();
			}
        } 
        else if (event.getActionCommand().equals(UNDO)) {
            if (playerType != PLAYERTYPE_NOT_SITTING &&
                gameBoard.getGridState().canPlayerUndo(playerType)) {
                dsgEventListener.eventOccurred(new DSGUndoRequestTableEvent(
                    null, tableNum));
            }
        }
        else if (event.getActionCommand().equals(CANCEL)) {
            if (playerType != PLAYERTYPE_NOT_SITTING) {
                dsgEventListener.eventOccurred(new DSGCancelRequestTableEvent(null, tableNum));
            }
        }
        else if (event.getActionCommand().equals(EMAIL)) {
            chatArea.newSystemMessage("email game request sent");
            dsgEventListener.eventOccurred(
                new DSGEmailGameRequestTableEvent(null, tableNum));
        }
        else if (event.getActionCommand().equals(SIT1)) {
            dsgEventListener.eventOccurred(new DSGSitTableEvent(null, tableNum, 1));
        }
        else if (event.getActionCommand().equals(SIT2)) {
            dsgEventListener.eventOccurred(new DSGSitTableEvent(null, tableNum, 2));
        }
        else if (event.getActionCommand().equals(STAND)) {
            dsgEventListener.eventOccurred(new DSGStandTableEvent(null, tableNum));
        }
        else if (event.getActionCommand().equals(SET_TIME)) {
            if (setTimeButton.isEnabled()) {
                new SetTimerDialog(
                    GameBoardFrame.this,
                    setTimerListener,
                    GameBoardFrame.this.gameStyle,
                    timed,
                    timerMinutes,
                    timerIncremental);
            }
        }
        else if (event.getActionCommand().equals(RESIGN)) {
            if (state != DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
                playerType != PLAYERTYPE_NOT_SITTING) {
                if (resignDialog != null) {
                    resignDialog.dispose();
                }
                resignDialog = DSGDialogFactory.createResignDialog(GameBoardFrame.this, gameStyle);
                resignDialog.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ee) {
                        if (ee.getActionCommand().equals("Yes")) {              
                            dsgEventListener.eventOccurred(
                                new DSGResignTableEvent(playerName, tableNum));
                        }
                    }
                });
                resignDialog.setVisible(true);
            }
        }
        else if (event.getActionCommand().equals(OPTIONS)) {
            GameOptionsDialog options = new GameOptionsDialog(
                GameBoardFrame.this,
                gameStyle,
                gameOptions.newInstance(),//pass copy so changes aren't propogated to all tables
                2);
            options.addGameOptionsChangeListener(gameBoard);
            options.addGameOptionsChangeListener(coordinatesList);
            options.addGameOptionsChangeListener(new GameOptionsChangeListener() {
                public void gameOptionsChanged(GameOptions newOptions) {
                    if (!gameOptions.equals(newOptions)) {
                    	gameOptions = newOptions;
                        preferenceHandler.storePref("gameOptions", gameOptions);
                    }
                }
            });
        }
        else if (event.getActionCommand().equals("Boot Player")) {
            if (bootDialog != null) {
                bootDialog.dispose();
            }
            bootDialog = new BootDialog(
                GameBoardFrame.this, gameStyle, bootList, 
                new PlayerActionAdapter() {
                    public void actionRequested(String player) {
                        dsgEventListener.eventOccurred(
                            new DSGBootTableEvent(
                            playerName, tableNum, player));
                    }
                }, false
            );
        }
        else if (event.getActionCommand().equals("Invite Player")) {
            if (inviteDialog != null) {
                inviteDialog.dispose();
            }
            inviteDialog = new InviteDialog(GameBoardFrame.this, gameStyle,
                game, ratedCheck.getState(), timed, timerMinutes, 
                timerIncremental, inviteList,
                new PlayerActionAdapter() {
                    public void actionRequested(String player) {
                        dsgEventListener.eventOccurred(
                            new DSGInviteTableEvent(
                            playerName, tableNum, player, 
                            inviteDialog.getInviteText()));
                    }
                }
            );
        }
        else if (event.getActionCommand().equals("Play Computer")) {
			if (addAIDialog != null) {
				addAIDialog.dispose();
			}
			addAIDialog = new AddAIDialog(GameBoardFrame.this, gameStyle,
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						dsgEventListener.eventOccurred(new
							DSGAddAITableEvent(null, tableNum, 
							addAIDialog.getData()));
					}
				},
				game, aiData);
        }
    }
    private class GridBoardController implements GridBoardListener {

        public void gridMoved(int x, int y) {

            if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                gameBoard.getGridBoard().setThinkingPieceVisible(false);
            }
            else {
                int move = (gameBoard.getGridBoard().getGridHeight() - y - 1) * 
                    gameBoard.getGridBoard().getGridWidth() + x;
				gameBoard.getGridBoard().setThinkingPieceVisible(
					gameBoard.getGridState().isValidMove(move, playerType));
            }
        }

        public void gridClicked(int x, int y, int button) {

            if (state != DSGGameStateTableEvent.GAME_IN_PROGRESS) {
                return;
            }
            int move = (gameBoard.getGridBoard().getGridHeight() - y - 1) * 
			gameBoard.getGridBoard().getGridWidth() + x;
            if (!gameBoard.getGridState().isValidMove(move, playerType)) {
                return;
            }

            dsgEventListener.eventOccurred(new DSGMoveTableEvent(
				playerName, tableNum, move));
        }
    }


    public void destroy() {

        playerDataCache.removeChangeListener(playerList);
        playerDataCache.removeChangeListener(bootList);
        playerDataCache.removeChangeListener(inviteList);

        if (coordinatesList != null) {
            coordinatesList.destroy();
        }

        if (gameBoard != null) {
			gameBoard.destroy();
        }
        if (playerList != null) {
            playerList.destroy();
        }

        if (undoDialog != null) {
            undoDialog.dispose();
        }
        if (cancelDialog != null) {
            cancelDialog.dispose();
        }
        if (playerReturnTimeUpDialog != null) {
            playerReturnTimeUpDialog.dispose();
        }
        if (middleSetDialog != null) {
        	middleSetDialog.dispose();
        }
        if (resignDialog != null) {
            resignDialog.dispose();
        }
        if (bootDialog != null) {
            bootDialog.dispose();
        }
        if (bootList != null) {
            bootList.destroy();
        }
        if (inviteDialog != null) {
            inviteDialog.dispose();
        }
        if (inviteList != null) {
            inviteList.destroy();
        }
        if (swapDialog != null) {
            swapDialog.dispose();
        }
		if (addAIDialog != null) {
			addAIDialog.dispose();
		}

        if (chatArea != null) {
            chatArea.destroy();
        }
        for (int i = 1; i < playerGameTimers.length; i++) {
            if (playerGameTimers[i] != null) {
                playerGameTimers[i].destroy();
                playerGameTimers[i] = null;
            }
        }
        if (setTimer != null) {
        	setTimer.destroy();
        	setTimer = null;
        }
    }

    
    private void sendChangeTableState() {

        int localGame = GridStateFactory.getGameId(gameChoice.getSelectedItem());
        DSGChangeStateTableEvent changeEvent = new DSGChangeStateTableEvent(playerName, tableNum);
        changeEvent.setInitialMinutes(timerMinutes);
        changeEvent.setIncrementalSeconds(timerIncremental);
        changeEvent.setRated(ratedCheck.getState());
        changeEvent.setTimed(timed);
        changeEvent.setGame(localGame);
        changeEvent.setTableType(tableTypeChoice.getSelectedItem());
        
        dsgEventListener.eventOccurred(changeEvent);
    }


    public void changeTableState(DSGChangeStateTableEvent changeStateEvent) {

        try {
            if (tableType != changeStateEvent.getTableType()) {
                
                tableType = changeStateEvent.getTableType();
                if (changeStateEvent.isTablePrivate()) {
                    tableTypeChoice.select("Private");
                }
                else {
                    tableTypeChoice.select("Public");
                }
            }
    
            if (changeStateEvent instanceof DSGChangeStateTableErrorEvent) {
    
                DSGChangeStateTableErrorEvent error = 
                    (DSGChangeStateTableErrorEvent) changeStateEvent;
                if (error.getError() == DSGTableErrorEvent.COMPUTER_SITTING) {
                    chatArea.newSystemMessage("games against computers can't be rated or timed.");
                }
                else if (error.getError() == DSGTableErrorEvent.TOURNAMENT_GAME) {
                    chatArea.newSystemMessage("can't change game settings for a " +
                    "tournament game");
                }
                
                Game g = GridStateFactory.getGame(game);
                gameChoice.select(g.getName());
            }

			// any of these changes require player to click play again
			// this variable just controls message to player
			//clickedPlay = false;
			
            timed = changeStateEvent.getTimed();
            timerMinutes = changeStateEvent.getInitialMinutes();
            timerIncremental = changeStateEvent.getIncrementalSeconds();
            updateTimedLabel();

            playerGameTimers[1].setStartMinutes(timerMinutes);
            playerGameTimers[1].reset();
            playerGameTimers[2].setStartMinutes(timerMinutes);
            playerGameTimers[2].reset();

            ratedCheck.setState(changeStateEvent.getRated());

            if (changeStateEvent.getGame() != game) {
                switchGame(changeStateEvent.getGame());
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
		
		updateMessage();
    }
    
    private void switchGame(int newGame) throws Throwable {

        game = newGame;
        // set game name according to game, speed or not
        gameBoard.getGridBoard().setGameName(GridStateFactory.getGameName(newGame));

        // set game choice according to normal game
        Game g = GridStateFactory.getGame(newGame);
        if (g.isSpeed()) {
            g = GridStateFactory.getNormalGame(g);
        }
        gameChoice.select(g.getName());
        
        // map game from 1-16 (speed and normal)
        // to 0-7 games (normal only)
        int localGame = (game - 1) / 2;
        gameBoard.getGridState().clear();
		gameBoard.setGridState(localGame);
		gameBoard.getGridBoard().setBackgroundColor(GameBoard.GAME_BG_COLORS[localGame]);
        playerList.setGame(newGame);
        bootList.setGame(game);
        inviteList.setGame(game);
		coordinatesList.setGame(game);
        
		if (owner) {
			updatePlayAIControls();
		}
    }
	
	private void updatePlayAIControls() {
		
        boolean isValidGame = false;
        for (int i = 0; i < aiData.size(); i++) {
            AIData d = (AIData) aiData.elementAt(i);
            if (d.isValidForGame(game)) {
                isValidGame = true;
            }
        }
		playAIButton.setEnabled(isValidGame);
		if (!isValidGame && addAIDialog != null && addAIDialog.isVisible()) {
			addAIDialog.dispose();
			addAIDialog = null;
		}
	}
    
    private void updateTimedLabel() {
        if (timed) {
            timedLabel.setText("Timer: " + timerMinutes + "/" + timerIncremental);
        }
        else {
            timedLabel.setText("Timer: no");
        }
    }

    public void setGameState(DSGGameStateTableEvent stateEvent) {
        
        // start new game
        if ((state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS ||
        	 state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) &&
            stateEvent.getState() == DSGGameStateTableEvent.GAME_IN_PROGRESS) {

        	if (setTimer != null) {
        		setTimer.stop();
        	}
    		if (playerReturnTimeUpDialog != null) {
    			playerReturnTimeUpDialog.dispose();
    		}
    		if (middleSetDialog != null) {
    			middleSetDialog.dispose();
    			middleSetDialog = null;
    		}
        	
        	updateGameInset(stateEvent); 

            //clickedPlay = false;
			
            // remove sitting players from boot list
            for (int i = 0; i < sittingPlayers.length; i++) {
                if (sittingPlayers[i] != null) {
                    bootList.removePlayer(sittingPlayers[i]);
                }
            }

            Game normalGame = GridStateFactory.getGame(game);
            if (normalGame.isSpeed()) {
                normalGame = GridStateFactory.getNormalGame(normalGame);
            }
            if (normalGame.getId() == GridStateFactory.PENTE ||
                normalGame.getId() == GridStateFactory.KERYO ||
                normalGame.getId() == GridStateFactory.BOAT_PENTE) {
                ((PenteState) gameBoard.getGridState()).setTournamentRule(ratedCheck.getState());
            }
			gameBoard.getGridState().clear();
            
            resetTimers();

            state = stateEvent.getState();
            
            // don't bother normal players with messages
            if (me.getTotalGames() < 10 && playerType != PLAYERTYPE_NOT_SITTING) {
                chatArea.newSystemMessage("point and click on the board to make your moves");
            }
            if (playerType == 1 && (game == GridStateFactory.DPENTE ||
                game == GridStateFactory.SPEED_DPENTE)) {
                chatArea.newSystemMessage("place the first four stones, then " +
                    "your opponent will get a chance to swap seats");
            }
            
        }
        // just joined a room that is waiting for a player to return
        else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
                 stateEvent.getState() == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
        	
        	updateGameInset(stateEvent); 
        	
            Game normalGame = GridStateFactory.getGame(game);
            if (normalGame.isSpeed()) {
                normalGame = GridStateFactory.getNormalGame(normalGame);
            }
            if (normalGame.getId() == GridStateFactory.PENTE ||
                normalGame.getId() == GridStateFactory.KERYO) {
                ((PenteState) gameBoard.getGridState()).setTournamentRule(ratedCheck.getState());
            }
			gameBoard.getGridState().clear();
            
            resetTimers();

            state = stateEvent.getState();
        }
        // else if game is ending one way or another
        // or if joining a table that is waiting for 2nd game to start
        else if ((stateEvent.getState() == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS ||
        		  stateEvent.getState() == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) &&
                 state != DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
                 state != stateEvent.getState()) {

            state = stateEvent.getState();
            stopTimers();

        	updateGameInset(stateEvent); 
            
        	boolean sitting = false;
            // if set over and boot dialog open, add sitting players back (if not null)
			// play sounds if player is winner or loser
            for (int i = 0; i < sittingPlayers.length; i++) {
                if (sittingPlayers[i] == null) continue;
				// this player is playing
				if (sittingPlayers[i].equals(playerName)) {
					sitting = true;
					// this game completed, not cancelled
					if (stateEvent.getWinner() != null && gameOptions.getPlaySound()) {
						// if this player won
						if (stateEvent.getWinner().equals(playerName)) {
							AudioClip sound = sounds.getSound("win");
							if (sound != null) {
								sound.play();
							}
						}
						// this player lost
						else {
							AudioClip sound = sounds.getSound("lose");
							if (sound != null) {
								sound.play();
							}
						}
					}
				}
				else if (stateEvent.getState() == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {

                    bootList.addPlayer(playerDataCache.getPlayer(
                        sittingPlayers[i]));
                }
            }
            
            // close any open dialogs
            if (swapDialog != null) {
                swapDialog.dispose();
            }
            if (undoDialog != null) {
                undoDialog.dispose();
            }
			if (playerReturnTimeUpDialog != null) {
				playerReturnTimeUpDialog.dispose();
			}
			if (middleSetDialog != null) {
				middleSetDialog.dispose();
				middleSetDialog = null;
			}
            
            if (sitting && 
            	state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {
            	
            	Boolean ignoreMiddleSet = (Boolean)
                	preferenceHandler.getPref("ims");
            	if (ignoreMiddleSet == null || !ignoreMiddleSet.booleanValue()) {
            		middleSetDialog = new MiddleSetDialog(this, gameStyle);
            		middleSetDialog.addActionListener(new ActionListener() {
	            		public void actionPerformed(ActionEvent e) {
	            			if (e.getActionCommand().equals("Play")) {
	            				GameBoardFrame.this.actionPerformed(e);
	            				if (middleSetDialog.getIgnore()) {
		                            preferenceHandler.storePref("ims", new Boolean(true));
	            				}
	            			}
	            		}
	            	});
                	middleSetDialog.setVisible(true);
                	if (setTimer != null && setTimer.isRunning()) {
                		middleSetDialog.startTimer(setTimer.getMinutes(), setTimer.getSeconds());
                	}
            	}
            }
            
        }
        // else if game in progress and one player has left
        else if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS &&
                 stateEvent.getState() == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {

            state = stateEvent.getState();
            stopTimers();

            // if this is the player remaining, open the player left dialog
            if (playerType != PLAYERTYPE_NOT_SITTING) {
                
                if (swapDialog != null) {
                    swapDialog.dispose();
                }
                if (undoDialog != null) {
                    undoDialog.dispose();
                }
                createPlayerReturnTimeUpDialog(false);
            }
        }
        // else if game is resuming
        else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN &&
                 stateEvent.getState() == DSGGameStateTableEvent.GAME_IN_PROGRESS) {

            state = stateEvent.getState();
            if (timed) {
	            playerGameTimers[gameBoard.getGridState().getCurrentPlayer()].go();
            }
            if (playerReturnTimeUpDialog != null) {
                playerReturnTimeUpDialog.dispose();
            }
            
            // could be that player 1 is returning to game after 1st 4 moves
            // of d-pente and player 2 still needs to decide to swap
            showSwapDialog();
        }
        else if (state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET &&
        		stateEvent.getState() == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {

            state = stateEvent.getState();
   		}
        else if (state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN &&
        	stateEvent.getState() == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

        	updateGameInset(stateEvent); 

            state = stateEvent.getState();
        }
        // just joined a room that is waiting for game 2 to start
        else if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS &&
                 stateEvent.getState() == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

        	updateGameInset(stateEvent); 

            state = stateEvent.getState();
        }

        if (stateEvent.getChangeText() != null) {
            chatArea.newSystemMessage(stateEvent.getChangeText());
        }
        
        updateOwnerFields();
        if (me.isAdmin()) {
            updateAdminFields();
        }

		updateMessage();
    }

	private void createPlayerReturnTimeUpDialog(boolean middleOfSet) {
		if (playerReturnTimeUpDialog != null) {
			playerReturnTimeUpDialog.dispose();
		}
		if (middleSetDialog != null) {
			middleSetDialog.dispose();
			middleSetDialog = null;
		}

		int minutes = middleOfSet ? 0 : 7;
		// should still be allowed to cancel game if 1 player left
		playerReturnTimeUpDialog = new PlayerLeftDialog(
			this, gameStyle, minutes, middleOfSet, 
			ratedCheck.getState() && gameNumInSet == 1);
		playerReturnTimeUpDialog.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent e) {
		        if (e.getActionCommand().equals("Resign")) {
		            dsgEventListener.eventOccurred(
		                new DSGResignTableEvent(playerName, tableNum));
		        }
		        else {
		          int action = e.getActionCommand().startsWith("Cancel") ? 
		              DSGForceCancelResignTableEvent.CANCEL : 
		              DSGForceCancelResignTableEvent.RESIGN;
      
		          dsgEventListener.eventOccurred(
		              new DSGForceCancelResignTableEvent(playerName, tableNum, action));
		        }
		    }
		});
		playerReturnTimeUpDialog.setVisible(true);
	}

	private void updateGameInset(DSGGameStateTableEvent stateEvent) {
		gameNumInSet = stateEvent.getGameInSet();
		if (!ratedCheck.getState() || gameNumInSet == 0) {
			gameNumInSet = 0;
        	gameInSetLabel.setText("");
            gameInSetLayout.show(gameInSetPanel, "game");
		}
		else {
			if (setTimer == null || !setTimer.isRunning()) {
				gameInSetLabel.setText("Game " + gameNumInSet + " of 2");
				gameInSetLayout.show(gameInSetPanel, "set");
			}
		}
	}

    public void receivePlayerPlaying(DSGSetPlayingPlayerTableEvent setPlayingPlayerEvent) {
        playingPlayers[setPlayingPlayerEvent.getSeat()] = setPlayingPlayerEvent.getPlayer();
    }
	/** if play clicked play successfully */
	public void receivePlay(DSGPlayTableEvent playEvent) {
		clickedPlay = true;
		updateMessage();
	}
	/** if player clicked play but received error */
	public void receivePlayError(DSGPlayTableErrorEvent playErrorEvent) {
		clickedPlay = false;
		updateMessage();
	}

    private void resetTimers() {
        if (timed) {
            playerGameTimers[1].reset();
            playerGameTimers[2].reset();
        }
    }
    
    private void switchTimers() {
        if (timed && state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
            if (gameBoard.getGridState().getCurrentPlayer() == 1) {
                playerGameTimers[2].stop();
                playerGameTimers[1].go();
            } else {
                playerGameTimers[1].stop();
                playerGameTimers[2].go();
            }
        }
    }

    private void stopTimers() {
        playerGameTimers[1].stop();
        playerGameTimers[2].stop();
    }

    public void receiveMove(DSGMoveTableEvent moveEvent) {

        if (undoDialog != null) {
            undoDialog.dispose();
        }
        if (cancelDialog != null) {
            cancelDialog.dispose();
        }

        int moves[] = moveEvent.getMoves();
        for (int i = 0; i < moves.length; i++) {
			gameBoard.getGridState().addMove(moves[i]);
        }

		gameBoard.getGridBoard().setThinkingPieceVisible(false);
		gameBoard.getGridBoard().setThinkingPiecePlayer(gameBoard.getGridState().getCurrentColor());

		AudioClip moveSound = sounds.getSound("move");
        if (playerType != 3 - gameBoard.getGridState().getCurrentPlayer() && // this player didn't make the move
            gameOptions.getPlaySound() &&
            moveSound != null) {
            moveSound.play();
        }

        if (state == DSGGameStateTableEvent.GAME_IN_PROGRESS) {
            switchTimers();
        }
        
        showSwapDialog();
    }

    /** Checks if it's time to show the swap dialog and does so */
    private void showSwapDialog() {
        if (gameBoard.getGridState().getNumMoves() == 4 &&
            (game == GridStateFactory.DPENTE || game == GridStateFactory.SPEED_DPENTE) &&
            !((PenteState) gameBoard.getGridState()).wasDPenteSwapDecisionMade()) {

            if (playerType == 2) {
                if (swapDialog != null) {
                    swapDialog.dispose();
                }
                swapDialog = DSGDialogFactory.createSwapDialog(this, gameStyle);
                swapDialog.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        boolean swap = false;
                        if (e.getActionCommand().equals("Player 1") && playerType == 2) {
                            swap = true;
                        }
                        else if (e.getActionCommand().equals("Player 2") && playerType == 1) {
                            swap = true;
                        }
                        
                        // if undo requested at same time and p2 answers swap
                        // then close undo
                        if (undoDialog != null) {
                            undoDialog.dispose();
                        }
                        
                        dsgEventListener.eventOccurred(
                            new DSGSwapSeatsTableEvent(playerName, tableNum, swap, false));
                    }
                });
                swapDialog.setVisible(true);
            }
            else {
                chatArea.newSystemMessage("waiting for " + sittingPlayers[2] + 
                    " to decide whether to swap seats or not");
            }
        }
    }

    public void receiveTimerChange(DSGTimerChangeTableEvent timerChangeEvent) {
        
        for (int i = 1; i < playingPlayers.length; i++) {
            if (timerChangeEvent.getPlayer().equals(playingPlayers[i])) {
                playerGameTimers[i].adjust(timerChangeEvent.getMinutes(), timerChangeEvent.getSeconds());
            }
        }
    }

	private void updateMessage() {
		if (gameBoard == null || gameBoard.getGridBoard() == null) return;
		
		String message = null;
		// if game in progress or waiting for a player to return, nothing for me to do
		if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS ||
			state == DSGGameStateTableEvent.WAIT_GAME_TWO_OF_SET) {

			// if i already clicked play and are waiting for opponent to click play
			if (clickedPlay) {
				message = "Waiting for opponent to click Play...";
			}
			// i'm sitting and an opponent is sitting
			else if ((sittingPlayers[1] != null && 
				      sittingPlayers[2] != null) && amSitting()) {
				if (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) {
					message = "Click Play to begin game";
				}
				else {
					message = "Game 1 completed, click play to begin game 2";
				}
			}
			// i'm sitting and there is a seat open
			else if (amSitting()) {
				message = "Waiting for an opponent to Sit. Hint: Try Invite Player or " +
					"Play Computer";
			}
			// a seat is open and i'm not sitting
			else if (sittingPlayers[1] == null || sittingPlayers[2] == null){
				message = "Click Sit 1 or Sit 2 to begin game";
			}
			// two other players sitting, nothing for me to do
			else {
				message = null;
			}
		}
		else if (amSitting() && 
				 state == DSGGameStateTableEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
			message = "Waiting for your opponent to return...";
		}

		gameBoard.getGridBoard().setMessage(message);
	}

	private boolean amSitting() {
		return (sittingPlayers[1] != null && sittingPlayers[1].equals(playerName)) ||
		    (sittingPlayers[2] != null && sittingPlayers[2].equals(playerName));
	}
	
    public void receivePlayerSit(DSGSitTableEvent sitEvent) {

        coordinatesList.setPlayer(sitEvent.getSeat(), sitEvent.getPlayer());
        if (sitEvent.getPlayer().equals(playerName)) {
            sit1Button.setLabel(STAND);
            sit2Button.setLabel(STAND);
            sitting = true;
            playerType = sitEvent.getSeat();
        }
        
        sittingPlayers[sitEvent.getSeat()] = sitEvent.getPlayer();
		
		updateMessage();
    }

    
    public void receivePlayerStand(DSGStandTableEvent standEvent) {

        coordinatesList.removePlayer(standEvent.getPlayer());
        if (standEvent.getPlayer().equals(playerName)) {
            sit1Button.setLabel(SIT1);
            sit2Button.setLabel(SIT2);
            sitting = false;
            playerType = PLAYERTYPE_NOT_SITTING;
        }
        
        for (int i = 1; i < sittingPlayers.length; i++) {
            if (standEvent.getPlayer().equals(sittingPlayers[i])) {
                sittingPlayers[i] = null;
                break;
            }
        }
		//clickedPlay = false;//TODO broken if waiting between games in set,
		
		updateMessage();
    }

	public void receivePlayerStandError(DSGStandTableErrorEvent standEvent) {
        if (standEvent.getError() == DSGExitTableErrorEvent.GAME_IN_PROGRESS ||
        	standEvent.getError() == DSGExitTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
                
            chatArea.newSystemMessage("you can't stand when a game is in progress");
        }
        else if (standEvent.getError() == DSGExitTableErrorEvent.WAIT_GAME_TWO_OF_SET) {

            chatArea.newSystemMessage("you can't stand in the middle of a rated set of 2 games");
        }
	}

	public void receivePlayerSwap(DSGSwapSeatsTableEvent swapEvent) {

        if (game != GridStateFactory.DPENTE &&
            game != GridStateFactory.SPEED_DPENTE) return; // sanity

        // close undo request since p2 decided to swap or not before
        // undo request made it
        if (undoDialog != null) {
            undoDialog.dispose();
        }

        // a silent swap event is only used when one player is booted
        // and then returns to the game AFTER the swap decision was made
        // in this case, just record that the decision was made in the grid
        // state.  or if a watcher joins after the swap the same applies
        if (swapEvent.wantsToSwap() && !swapEvent.isSilent()) {
            chatArea.newSystemMessage(sittingPlayers[2] + " has decided to " +
                "swap seats, it's now " + sittingPlayers[2] + "'s turn");
            
            // swap players
            String tmp = sittingPlayers[1];
            sittingPlayers[1] = sittingPlayers[2];
            sittingPlayers[2] = tmp;
            playingPlayers[1] = sittingPlayers[1];
            playingPlayers[2] = sittingPlayers[2];

            // update player display with swapped players
            coordinatesList.setPlayer(1, sittingPlayers[1]);
            coordinatesList.setPlayer(2, sittingPlayers[2]);

            // switch player type
            if (playerType != PLAYERTYPE_NOT_SITTING) {
                playerType = 3 - playerType;
            }
            
            // switch local timers
            if (timed) {
                playerGameTimers[1].stop();
                playerGameTimers[2].stop();
                
                int s1 = playerGameTimers[1].getSeconds();
                int m1 = playerGameTimers[1].getMinutes();
                int s2 = playerGameTimers[2].getSeconds();
                int m2 = playerGameTimers[2].getMinutes();
                playerGameTimers[1].adjust(m2, s2);
                playerGameTimers[2].adjust(m1, s1);
            }
            
        }
        else if (!swapEvent.isSilent()){
            chatArea.newSystemMessage(sittingPlayers[2] + " has decided not " +
                "to swap seats, it's now " + sittingPlayers[1] + "'s turn");
        }

        ((PenteState) gameBoard.getGridState()).dPenteSwapDecisionMade(swapEvent.wantsToSwap());

		AudioClip moveSound = sounds.getSound("move");
        // alert current player that it's their turn to move
        if (playerType != 3 - gameBoard.getGridState().getCurrentPlayer() && // this player didn't make the move
            gameOptions.getPlaySound() &&
            moveSound != null) {
            moveSound.play();
        }

        // startup timers again        
        if (timed) {
            switchTimers();
        }
    }

    public void receivePlayerJoin(DSGJoinTableEvent joinEvent) {
        playerList.addPlayer(playerDataCache.getPlayer(
            joinEvent.getPlayer()));

        if (!joinEvent.getPlayer().equals(playerName)) {

            Boolean showJoinExitMessagesPref = (Boolean)
                preferenceHandler.getPref("showPlayerJoinExit");
            if (showJoinExitMessagesPref == null ||
                showJoinExitMessagesPref.booleanValue()) {
                chatArea.newSystemMessage(joinEvent.getPlayer() + " has entered the table");
            }
            bootList.addPlayer(playerDataCache.getPlayer(joinEvent.getPlayer()));
            inviteList.removePlayer(joinEvent.getPlayer());
        }
    }
    public void receivePlayerExit(DSGExitTableEvent exitEvent) {
        playerList.removePlayer(exitEvent.getPlayer());
        
        if (!exitEvent.getPlayer().equals(playerName)) {
            bootList.removePlayer(exitEvent.getPlayer());
            DSGPlayerData d = playerDataCache.getPlayer(exitEvent.getPlayer());
            if (d.isHuman()) {
                inviteList.addPlayer(d);
            }
        }

        Boolean showJoinExitMessagesPref = (Boolean)
            preferenceHandler.getPref("showPlayerJoinExit");

        if (exitEvent.getPlayer().equals(playerName)) {
            //should be handled by the TableController so don't repeat here
            //destroy();
            //dispose();
        }
        else if (showJoinExitMessagesPref == null ||
                 showJoinExitMessagesPref.booleanValue()) {
            if (exitEvent.wasBooted()) {
                chatArea.newSystemMessage(exitEvent.getPlayer() + " was booted from the table and cannot return for 5 minutes");
            }
            else {
                chatArea.newSystemMessage(exitEvent.getPlayer() + " has left the table");
            }
        }
    }

    public void receivePlayerJoinMainRoom(DSGJoinMainRoomEvent joinEvent) {
        if (joinEvent.getDSGPlayerData().isHuman() &&
            !joinEvent.getPlayer().equals(playerName)) {
            inviteList.addPlayer(joinEvent.getDSGPlayerData());
        }
    }
    public void receivePlayerExitMainRoom(DSGExitMainRoomEvent exitEvent) {
        inviteList.removePlayer(exitEvent.getPlayer());
    }

    public void receivePlayerExitError(DSGExitTableErrorEvent exitErrorEvent) {
        if (exitErrorEvent.getError() == DSGExitTableErrorEvent.GAME_IN_PROGRESS ||
            exitErrorEvent.getError() == DSGExitTableErrorEvent.GAME_WAITING_FOR_PLAYER_TO_RETURN) {
            
            chatArea.newSystemMessage("you can't exit a game in progress, " +
                "if you must go, first resign or request to cancel the game");
        }
        else if (exitErrorEvent.getError() == DSGExitTableErrorEvent.WAIT_GAME_TWO_OF_SET) {

            chatArea.newSystemMessage("you can't exit in the middle of a rated set of 2 games, " +
                "if you must go, first resign or request to cancel the set");
        }
    }

    public void receiveSetOwner(String player) {
        
        playerList.setOwner(player);
        
        if (player.equals(playerName)) {
            owner = true;
            updateOwnerFields();
        
            String msg = "you are now the owner of this table";
            if (me.getTotalGames() < 10) {
            	msg += ", which means you can set the time controls, set the game to " +
                "rated/unrated, and set the table type to public/private and " +
                "invite or boot players";
            }
            chatArea.newSystemMessage(msg);
        }
        else {
            chatArea.newSystemMessage(player + " is the owner of this table");
        }
        
        if (me.isAdmin()) {
            updateAdminFields();
        }
    }

    private void updateAdminFields() {
        boolean enabled = state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS;

        ratedCheck.setEnabled(enabled);
        setTimeButton.setEnabled(enabled);
        gameChoice.setEnabled(enabled);
        
        bootButton.setEnabled(true);
        inviteButton.setEnabled(true);
        tableTypeChoice.setEnabled(true);
		updatePlayAIControls();
    }
    private void updateOwnerFields() {

        boolean enabled = (state == DSGGameStateTableEvent.NO_GAME_IN_PROGRESS) ? owner : false;

        ratedCheck.setEnabled(enabled);
        setTimeButton.setEnabled(enabled);
        gameChoice.setEnabled(enabled);
        
        // always keep boot/invite available only to owner
        // and always to admin users
        if (owner) {
            bootButton.setEnabled(true);
            inviteButton.setEnabled(true);
            tableTypeChoice.setEnabled(true);
			updatePlayAIControls();
        }
    }

    public void receiveText(DSGTextTableEvent textEvent) {
        chatArea.newChatMessage(textEvent.getText(), textEvent.getPlayer());
    }
    public void receiveSystemMessage(DSGSystemMessageTableEvent systemMessageEvent) {
        chatArea.newSystemMessage(systemMessageEvent.getMessage());
    }

    public void receiveUndoRequest(DSGUndoRequestTableEvent undoRequestEvent) {
        
        chatArea.newSystemMessage("undo requested");
        if (playerType != PLAYERTYPE_NOT_SITTING &&
            !playerName.equals(undoRequestEvent.getPlayer())) {
            
            undoDialog = DSGDialogFactory.createUndoDialog(this, gameStyle);
            undoDialog.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    boolean reply = e.getActionCommand().equals("Yes") ? true : false;
                    
                    // if undo requested at same time as swap and p2 answers
                    // undo, then close swap
                    if (reply && swapDialog != null) {
                        swapDialog.dispose();
                    }
                    
                    dsgEventListener.eventOccurred(
                        new DSGUndoReplyTableEvent(playerName, tableNum, reply));                   
                }
            });
            undoDialog.setVisible(true);
        }
    }
    
    
    public void receiveUndoReply(DSGUndoReplyTableEvent undoReplyEvent) {
        
        chatArea.newSystemMessage("undo " + (undoReplyEvent.getAccepted() ? "accepted" : "denied"));
        
        if (undoReplyEvent.getAccepted()) {
			gameBoard.getGridState().undoMove();
            gameBoard.getGridBoard().setThinkingPiecePlayer(gameBoard.getGridState().getCurrentColor());
            switchTimers();
        }
    }

    public void receiveCancelRequest(DSGCancelRequestTableEvent cancelRequestEvent) {

    	String txt = "game";
    	if (gameNumInSet != 0) {
    		txt = "set";
    	}
        chatArea.newSystemMessage("cancel " + txt + " requested");
        if (playerType != PLAYERTYPE_NOT_SITTING &&
            !playerName.equals(cancelRequestEvent.getPlayer())) {
            
            cancelDialog = DSGDialogFactory.createCancelDialog(this, gameStyle, txt);
            cancelDialog.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    boolean reply = e.getActionCommand().equals("Yes") ? true : false;
                    
                    dsgEventListener.eventOccurred(
                        new DSGCancelReplyTableEvent(playerName, tableNum, reply));
                }
            });
            cancelDialog.setVisible(true);
        }
    }
    
    public void receiveCancelReply(DSGCancelReplyTableEvent cancelReplyEvent) {

    	String txt = "game";
    	if (gameNumInSet != 0) {
    		txt = "set";
    	}
        chatArea.newSystemMessage("cancel " + txt + " " + (cancelReplyEvent.getAccepted() ? "accepted" : "denied"));
        
        // actual cancel handled in change game state event}
    }
    

    public void receivePlayerReturnTimeUpEvent(DSGWaitingPlayerReturnTimeUpTableEvent timeUpEvent) {
        
    	if (timeUpEvent.isSet()) {
            createPlayerReturnTimeUpDialog(true);
    	}
    	else if (playerReturnTimeUpDialog != null) {
            playerReturnTimeUpDialog.timeHasExpired();
        }
    }
    
    public void receiveEmailGameReply(DSGEmailGameReplyTableEvent emailGameReplyEvent) {
        chatArea.newSystemMessage(emailGameReplyEvent.getReply());
    }
	
	public void receiveInviteResponse(
		DSGInviteResponseTableEvent inviteResponseEvent) {
	
		chatArea.newSystemMessage(inviteResponseEvent.getPlayer() + " has " +
			(inviteResponseEvent.getAccept() ? "accepted " : "declined ") +
			"your invitation.");
		String response = inviteResponseEvent.getResponseText();
		if (response != null && !response.equals("")) {
			chatArea.newSystemMessage(inviteResponseEvent.getPlayer() + 
				"'s response: " + inviteResponseEvent.getResponseText());
		}
	}

    public void receiveStartSetTimerEvent(DSGStartSetTimerEvent timerEvent) {
        if (setTimer != null) {
        	setTimer.stop();
        }
        else {
        	setTimer = new SimpleGameTimer();
        }
        int minutes = (int) (timerEvent.getTimeLeft() / 1000 / 60);
        int seconds = (int) (timerEvent.getTimeLeft() / 1000 % 60);
        setTimer.setStartMinutes(minutes);
        setTimer.setStartSeconds(seconds);

        setTimer.addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int newMinutes, int newSeconds) {
            	if (newMinutes == 0 && newSeconds == 0) {
            		setTimer.stop();
            		//and open dialog
            	}
            	if (!setTimer.isRunning()) return;//don't update if stopped
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                gameInSetLabel.setText("Set Timeout in: " + newMinutes + ":" + newSecondsStr);
            }
        });
        setTimer.reset();
        setTimer.go();
        
        gameInSetLayout.show(gameInSetPanel, "set");
        
        if (middleSetDialog != null) {
        	middleSetDialog.startTimer(minutes, seconds);
        }
    }

    public Dimension getNewTableSizePref() {
        if (!initialSize.equals(getSize())) {
            return getSize();
        }
        else {
            return null;
        }
    }
}
