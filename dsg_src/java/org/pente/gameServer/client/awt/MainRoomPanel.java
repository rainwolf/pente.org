/**
 * MainRoomPanel.java
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

package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.applet.*;
import java.net.*;
import java.util.*;
import java.text.*;

import org.pente.game.GameEventData;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.event.*;

/**
 * The main room panel, provides a chat area, a list of players, and a table status
 * area that shows where players are and what the status of tables are.
 */
public class MainRoomPanel extends Panel implements ActionListener,
        PlayerActionListener, TableJoinListener, ChatListener {

    private PenteApplet applet;
    private PlayerListComponent playerList;
    private CustomTablesPanel tablesPanel;

    private static final int componentSpacing = 5;

    private Vector<InviteResponseFrame> invitations = new Vector<InviteResponseFrame>();

    private boolean displayPlayerMessages = false;
    private DSGPlayerData me;
    private ServerData serverData;

    private Vector<ServerData> activeServers;
    private Choice loginRoom;

    private PreferenceHandler preferenceHandler;
    private MainRoomOptionsDialog optionsDialog;

    private DummyFrame frame;
    private GameStyles gameStyle;
    private DSGEventListener dsgEventListener;
    private PlayerDataCache playerDataCache;

    private TableController tableController;

    private static final DateFormat df = new SimpleDateFormat("HH:mm:ss");
    private long timeDiff = 0;
    private ChatComponent chatArea;

    private PlayerListPanel bootList;
    private BootDialog bootDialog;
    private MilliSecondGameTimer timer = null;

    public MainRoomPanel(final long dsgTime,
                         final PenteApplet applet,
                         final DSGPlayerData me,
                         final ServerData serverData,
                         final Vector servers,
                         final DSGEventSource dsgEventSource,
                         final DSGEventListener dsgEventListener,
                         final GameStyles gameStyle,
                         final Sounds sounds,
                         final DummyFrame frame,
                         final PlayerDataCache playerDataCache,
                         final PreferenceHandler preferenceHandler) {

        this.applet = applet;
        this.me = me;
        this.serverData = serverData;
        this.gameStyle = gameStyle;
        this.dsgEventListener = dsgEventListener;
        this.frame = frame;
        this.playerDataCache = playerDataCache;
        this.preferenceHandler = preferenceHandler;

        int chatHeight = 10;
        timeDiff = System.currentTimeMillis() - dsgTime;

        activeServers = new Vector<ServerData>(servers.size() - 1);
        // remove the server we're in from list
        for (int i = 0; i < servers.size(); i++) {
            ServerData d = (ServerData) servers.elementAt(i);
            if (!d.getName().equals(serverData.getName())) {
                activeServers.addElement(d);
            }
        }


        chatArea = new ChatPanel(
                50, chatHeight, componentSpacing, preferenceHandler/*, applet.isGuest()*/);

        // print server defined login messages
        for (int i = 0; i < serverData.getLoginMessages().size(); i++) {
            String m = (String) serverData.getLoginMessages().elementAt(i);
            chatArea.newChatMessage(m);
        }
        if (applet.isGuest()) {
            chatArea.newChatMessage("\n*You are logged in as: " + me.getName() + ". You " +
                            "can play as many games as you want but you cannot play rated games unless " +
                            "you join pente.org (free!). \n"
//        		"Note that, as a guest, you can not participate in the chat." +
            );
        } else {
            chatArea.newChatMessage("*You are logged in as: " + me.getName() + ". \n" +
                    "- Note that double clicking on another user's name reveals an options menu, " +
                    "where you can ignore that user's chat and/or invitations."
            );
        }

        chatArea.addChatListener(this);

        tablesPanel = new CustomTablesPanel(me, gameStyle);
        tablesPanel.addTableJoinListener(this);

        playerList = new PlayerListPanel(new Color(0, 51, 102));
        playerList.setTableName(serverData.isTournament() ?
                "Tournament" : "Main Room");
        playerList.showNumPlayers(true);
        if (serverData.isTournament()) {
            com.google.gson.internal.LinkedTreeMap gedLTM = (com.google.gson.internal.LinkedTreeMap) serverData.getGameEvents().elementAt(0);
//            GameEventData ged = (GameEventData) serverData.getGameEvents().elementAt(0);
//            System.out.println(gedLTM.toString());
            int game = ((Double) gedLTM.get("game")).intValue();
            playerList.setGame(game);
        } else {
            playerList.setGame(1);
        }
        playerList.addGetStatsListener(this);
        playerDataCache.addChangeListener(playerList);

        final Label timeLabel = new Label("");
        timeLabel.setForeground(gameStyle.foreGround);
        timeLabel.setBackground(gameStyle.boardBack);
        timer = new MilliSecondGameTimer("DSGTime");
        timer.setStartMinutes(604800);//one week
        timer.addGameTimerListener((newSeconds, newMinutes) -> {
            long t = System.currentTimeMillis() - timeDiff;
            String time = df.format(new Date(t));
            timeLabel.setText("Time: " + time);
        });
        timer.reset();
        timer.go();

        dsgEventSource.addListener(dsgEvent -> {
            if (dsgEvent.getTime() != 0) {
                timeDiff = System.currentTimeMillis() - dsgEvent.getTime();
            }
            if (dsgEvent instanceof DSGTextMainRoomEvent) {
                DSGTextMainRoomEvent textEvent = (DSGTextMainRoomEvent) dsgEvent;
                chatArea.newChatMessage(textEvent.getText(),
                        textEvent.getPlayer());
            } else if (dsgEvent instanceof DSGSystemMessageTableEvent) {
                DSGSystemMessageTableEvent messageEvent = (DSGSystemMessageTableEvent) dsgEvent;
                if (messageEvent.getTable() == 0) {
                    chatArea.newSystemMessage(messageEvent.getMessage());
                }
            } else if (dsgEvent instanceof DSGJoinMainRoomEvent) {
                DSGJoinMainRoomEvent joinMainRoom = (DSGJoinMainRoomEvent) dsgEvent;

                playerList.addPlayer(joinMainRoom.getDSGPlayerData());

                AudioClip joinSound = sounds.getSound("join");
                if (!joinMainRoom.getDSGPlayerData().isComputer() &&
                        joinSound != null) {
                    Boolean playJoinSoundPref = (Boolean)
                            preferenceHandler.getPref("playJoinSound");
                    if (playJoinSoundPref == null ||
                            playJoinSoundPref.booleanValue()) {
                        joinSound.play();
                    }
                }

                if (displayPlayerMessages) {
                    Boolean showJoinExitMessagesPref = (Boolean)
                            preferenceHandler.getPref("showPlayerJoinExit");
                    if (showJoinExitMessagesPref == null ||
                            showJoinExitMessagesPref.booleanValue()) {
                        chatArea.newSystemMessage(
                                joinMainRoom.getPlayer() +
                                        " has joined the main room");
                    }
                }

                if (joinMainRoom.getPlayer().equals(me.getName())) {
                    displayPlayerMessages = true;
                }
            } else if (dsgEvent instanceof DSGExitMainRoomEvent) {
                DSGExitMainRoomEvent exitMainRoom = (DSGExitMainRoomEvent) dsgEvent;

                // if I am exiting, then let PenteApplet handle it
                if (exitMainRoom.getPlayer() == null) {
                    return;
                }

                playerList.removePlayer(exitMainRoom.getPlayer());
                if (displayPlayerMessages) {
                    Boolean showJoinExitMessagesPref = (Boolean)
                            preferenceHandler.getPref("showPlayerJoinExit");
                    if (showJoinExitMessagesPref == null ||
                            showJoinExitMessagesPref.booleanValue()) {

                        String message = "has exited";
                        if (exitMainRoom.wasBooted()) {
                            message = "was booted from";
                        }
                        chatArea.newSystemMessage(exitMainRoom.getPlayer() +
                                " " + message + " the game room");
                    }
                }
            } else if (dsgEvent instanceof DSGJoinTableEvent) {
                DSGJoinTableEvent joinEvent = (DSGJoinTableEvent) dsgEvent;
                tablesPanel.addPlayer(joinEvent.getTable(),
                        playerDataCache.getPlayer(joinEvent.getPlayer()));
            } else if (dsgEvent instanceof DSGExitTableEvent) {
                DSGExitTableEvent exitEvent = (DSGExitTableEvent) dsgEvent;
                tablesPanel.removePlayer(exitEvent.getTable(), exitEvent.getPlayer());
            } else if (dsgEvent instanceof DSGSitTableEvent) {
                DSGSitTableEvent sitEvent = (DSGSitTableEvent) dsgEvent;
                tablesPanel.sitPlayer(sitEvent.getTable(), sitEvent.getPlayer(),
                        sitEvent.getSeat());
            } else if (dsgEvent instanceof DSGStandTableEvent) {
                DSGStandTableEvent standEvent = (DSGStandTableEvent) dsgEvent;
                tablesPanel.standPlayer(standEvent.getTable(),
                        standEvent.getPlayer());
            } else if (dsgEvent instanceof DSGSwapSeatsTableEvent) {
                DSGSwapSeatsTableEvent swapEvent = (DSGSwapSeatsTableEvent) dsgEvent;
                if (swapEvent.wantsToSwap()) {
                    tablesPanel.swapPlayers(swapEvent.getTable());
                }
            } else if (dsgEvent instanceof DSGChangeStateTableEvent) {
                DSGChangeStateTableEvent changeEvent = (DSGChangeStateTableEvent) dsgEvent;
                tablesPanel.changeTableState(changeEvent.getTable(),
                        changeEvent);
            } else if (dsgEvent instanceof DSGBootTableEvent) {
                DSGBootTableEvent bootEvent = (DSGBootTableEvent) dsgEvent;
                chatArea.newSystemMessage("you were booted from table " +
                        bootEvent.getTable());
            } else if (dsgEvent instanceof DSGInviteTableEvent) {
                final DSGInviteTableEvent inviteEvent = (DSGInviteTableEvent) dsgEvent;

                CustomTableData data = tablesPanel.getTable(inviteEvent.getTable());
                DSGPlayerData playerData = playerDataCache.getPlayer(
                        inviteEvent.getPlayer());

                if (data != null && playerData != null) {
                    DSGPlayerGameData gameData = playerData.getPlayerGameData(data.getGame());
                    int rating = gameData == null ? 1600 : (int) Math.round(
                            gameData.getRating());
                    final InviteResponseFrame invite = new InviteResponseFrame(
                            gameStyle, inviteEvent, rating,
                            data);

                    invite.addActionListener(e -> {
                        boolean accept = e.getActionCommand().equals("Accept");
                        dsgEventListener.eventOccurred(
                                new DSGInviteResponseTableEvent(
                                        null, inviteEvent.getTable(),
                                        inviteEvent.getPlayer(),
                                        invite.getResponseText(),
                                        accept,
                                        invite.getIgnore()));

                        if (accept) {
                            dsgEventListener.eventOccurred(
                                    new DSGJoinTableEvent(null,
                                            inviteEvent.getTable()));
                        }

                        if (invite.getIgnore()) {
                            final DSGPlayerData d = playerDataCache.getPlayer(inviteEvent.getPlayer());
                            DSGIgnoreData i = playerDataCache.getIgnore(d.getPlayerID());
                            if (i == null) {
                                i = new DSGIgnoreData();
                                i.setPid(0);
                                i.setIgnorePid(d.getPlayerID());
                                playerDataCache.addIgnore(i);
                            }
                            i.setIgnoreInvite(true);
                        }
                    });

                    invitations.addElement(invite);
                    invite.setLocation(MainRoomPanel.this.applet.safeGetLocationOnScreen());
                    invite.setVisible(true);
                    invite.toFront();


                    AudioClip inviteSound = sounds.getSound("invite");
                    if (inviteSound != null) {
                        Boolean playInviteSoundPref = (Boolean)
                                preferenceHandler.getPref("playInviteSound");
                        if (playInviteSoundPref == null ||
                                playInviteSoundPref.booleanValue()) {
                            inviteSound.play();
                        }
                    }
                }
            }
        });

        Button serverStatsButton = gameStyle.createDSGButton("Server Stats");
        serverStatsButton.addActionListener(this);

        Button optionsButton = gameStyle.createDSGButton("Options");
        optionsButton.addActionListener(this);

        Button newTableButton = gameStyle.createDSGButton("Create New Table");
        newTableButton.addActionListener(this);

        Button tourneyButton = gameStyle.createDSGButton("Tournaments");
        tourneyButton.addActionListener(this);

        Button helpButton = gameStyle.createDSGButton("Help");
        helpButton.addActionListener(this);

        Button bootButton = gameStyle.createDSGButton("Boot");
        bootButton.addActionListener(this);
        bootList = new PlayerListPanel(new Color(0, 51, 102));
        bootList.setTableName("Boot Player");


        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        setLayout(gridbag);

        constraints.insets = new Insets(2, 2, 2, 2);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.weightx = 90;
        constraints.weighty = 48;
        constraints.fill = GridBagConstraints.BOTH;
        gridbag.setConstraints(tablesPanel, constraints);
        add(tablesPanel);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 1, 1));
        buttonPanel.add(newTableButton);
        buttonPanel.add(serverStatsButton);
        buttonPanel.add(optionsButton);
        if (!serverData.isTournament()) {
            buttonPanel.add(tourneyButton);
        }
        buttonPanel.add(helpButton);
        if (me.isAdmin()) {
            buttonPanel.add(bootButton);
        }

        constraints.gridx = 1;
        constraints.gridy++;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        gridbag.setConstraints(buttonPanel, constraints);
        add(buttonPanel);

        // contains change room button possibly and dsg time
        Panel buttonPanel2 = new Panel();
        buttonPanel2.setLayout(new BorderLayout());

        if (!activeServers.isEmpty()) {
            loginRoom = new Choice();
            loginRoom.setBackground(Color.white);

            for (int i = 0; i < activeServers.size(); i++) {
                ServerData d = (ServerData) activeServers.elementAt(i);
                loginRoom.addItem(d.getName());
            }
            Button changeRoomButton = gameStyle.createDSGButton("Change to Room:");
            changeRoomButton.addActionListener(this);

            Panel buttonPanel3 = new Panel();
            buttonPanel3.setLayout(new FlowLayout(FlowLayout.LEFT, 1, 1));
            buttonPanel3.add(changeRoomButton);
            buttonPanel3.add(loginRoom);

            if (serverData.isTournament()) {
                Button standingsButton = gameStyle.createDSGButton("Tournament Standings");
                standingsButton.addActionListener(this);
                buttonPanel3.add(standingsButton);
            }
            buttonPanel2.add("West", buttonPanel3);
        }

        buttonPanel2.add("East", timeLabel);

        constraints.gridy++;
        constraints.gridx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        gridbag.setConstraints(buttonPanel2, constraints);
        add(buttonPanel2);


        constraints.gridy++;
        constraints.gridx = 1;
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.weightx = 90;
        constraints.weighty = 48;
        constraints.fill = GridBagConstraints.BOTH;
        gridbag.setConstraints((Panel) chatArea, constraints);
        add((Panel) chatArea);

        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.gridheight = 4;
        constraints.gridwidth = 1;
        constraints.weightx = 10;
        constraints.weighty = 100;
        constraints.fill = GridBagConstraints.BOTH;

        Panel rightPanel = new Panel();
        rightPanel.setLayout(new BorderLayout(2, 2));
        rightPanel.add("Center", (Component) playerList);
        rightPanel.add("South", new RatingsCanvas(new Color(0, 51, 102)));

        gridbag.setConstraints(rightPanel, constraints);
        add(rightPanel);

        setBackground(gameStyle.boardBack);
    }

    public void chatEntered(final String message) {
        dsgEventListener.eventOccurred(new DSGTextMainRoomEvent(null, message));
    }

    public void actionRequested(String name) {
        frame.setDummyLocation();
        new PlayerStatsDialog(frame,
                frame.getLocation(),
                name, applet.getHost(), gameStyle, playerDataCache,
                dsgEventListener, name.equals(me.getName()));
    }

    public void actionRequested(String name, Object o) {
    }

    public void joinTable(int tableNum) {
        dsgEventListener.eventOccurred(new DSGJoinTableEvent(null,
                tableNum));
    }

    public void actionPerformed(ActionEvent e) {

        if (e.getActionCommand().equals("Server Stats")) {
            dsgEventListener.eventOccurred(new DSGServerStatsEvent());
        } else if (e.getActionCommand().equals("Options")) {
            if (optionsDialog != null) {
                optionsDialog.dispose();
            }
            optionsDialog = new MainRoomOptionsDialog(frame, gameStyle,
                    preferenceHandler);
            optionsDialog.setVisible(true);
        } else if (e.getActionCommand().equals("Create New Table")) {
            dsgEventListener.eventOccurred(new DSGJoinTableEvent(
                    null, DSGJoinTableEvent.CREATE_NEW_TABLE));
        } else if (e.getActionCommand().equals("Tournaments")) {
            openWebPage("/gameServer/tournaments");
        } else if (e.getActionCommand().equals("Help")) {
            openWebPage("/help/helpWindow.jsp?file=play");
        } else if (e.getActionCommand().equals("Change to Room:")) {
            int port = ((ServerData) activeServers.elementAt(
                    loginRoom.getSelectedIndex())).getPort();
            applet.reconnect(port);
        } else if (e.getActionCommand().equals("Tournament Standings")) {
            com.google.gson.internal.LinkedTreeMap gedLTM = (com.google.gson.internal.LinkedTreeMap) serverData.getGameEvents().elementAt(0);
//            GameEventData ged = (GameEventData) serverData.getGameEvents().elementAt(0);
//            System.out.println(gedLTM.toString());
            int eid = ((Double) gedLTM.get("id")).intValue();
//            int eid = ((GameEventData) 
//                serverData.getGameEvents().elementAt(0)).getEventID();
            openWebPage("/gameServer/tournaments/status.jsp?eid=" + eid);
        } else if (e.getActionCommand().equals("Boot")) {
            if (bootDialog != null) {
                bootDialog.dispose();
            }

            // setup player list based on main room player list
            bootList.clearPlayers();
            for (Enumeration en = playerList.getPlayers(); en.hasMoreElements(); ) {
                DSGPlayerData d = (DSGPlayerData) playerDataCache.getPlayer((String) en.nextElement());
                if (!d.getName().equals(me.getName()) && d.isHuman()) {
                    bootList.addPlayer(d);
                }
            }

            frame.setDummyLocation();
            bootDialog = new BootDialog(
                    frame, gameStyle, bootList,
                    new PlayerActionAdapter() {
                        public void actionRequested(String player, Object o) {
                            dsgEventListener.eventOccurred(
                                    new DSGBootMainRoomEvent(
                                            me.getName(), player, ((Integer) o).intValue()));
                        }
                    }, true
            );
        }

    }

    private void openWebPage(String page) {
        try {
            URL url = new URL(
                    "http://" + applet.getHost() + page);
            applet.getAppletContext().showDocument(url, "_blank");
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
    }

    public void destroy() {
        if (playerList != null) {
            ((PlayerListPanel) playerList).destroy();
        }
        if (tablesPanel != null) {
            tablesPanel.destroy();
        }
        for (int i = 0; i < invitations.size(); i++) {
            Frame f = (Frame) invitations.elementAt(i);
            f.dispose();
        }
        if (optionsDialog != null) {
            optionsDialog.dispose();
        }
        if (chatArea != null) {
            chatArea.destroy();
        }
        if (timer != null) {
            timer.destroy();
        }
    }

    public Insets getInsets() {
        return new Insets(5, 5, 5, 5);
    }

    public PlayerListComponent getPlayerList() {
        return playerList;
    }

    public void setTableController(TableController tableController) {
        this.tableController = tableController;
    }
}
