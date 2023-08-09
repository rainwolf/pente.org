/**
 * PenteApplet.java
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
import java.applet.*;
import java.net.*;
import java.util.*;

import org.pente.gameServer.client.*;
import org.pente.gameServer.event.*;
import org.pente.gameServer.core.AIData;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

public class PenteApplet extends Applet {

    //private DSGEventLogger dsgEventLogger;
    private SocketDSGEventHandler socketDSGEventHandler;

    private boolean guest;
    private String playerName;
    private String password;
    private ClientInfo info;

    private String host;
    private int port;

    private CardLayout cardLayout = null;

    private ConnectPanel connectPanel;
    private LoginComponent loginPnl;
    private ErrorPanel errorPnl;
    private MainRoomPanel mainRoomPnl;
    private GameStyles gameStyle;

    private static final String LOGIN = "login";
    private static final String ERROR_PANEL = "error";
    private static final String MAINROOM = "mainRoom";

    private Sounds sounds = new Sounds();

    private TableController tableController;
    private Frame statsFrame;

    private Vector<AIData> aiDataVector = new Vector<AIData>();
    private PlayerDataCache playerDataCache;
    private PreferenceHandler preferenceHandler;

    private Vector activeServers;
    private boolean changingRooms = false;

    private boolean booted = false;

    public void init() {

        try {

            getClientInfo();
            host = getCodeBase().getHost();
//System.out.println("host="+host);
            // load sounds
            if (getParameter("loadSounds") != null) {
                sounds.addSound(getAudioClip(getCodeBase(), "yourturn.au"), "move");
                sounds.addSound(getAudioClip(getCodeBase(), "newplayer.au"), "join");
                sounds.addSound(getAudioClip(getCodeBase(), "woohoo.au"), "win");
                sounds.addSound(getAudioClip(getCodeBase(), "doh.au"), "lose");
                sounds.addSound(getAudioClip(getCodeBase(), "invite.au"), "invite");
            }

            //set up game styles
            gameStyle = new GameStyles(new Color(50, 90, 203), //board back
                    new Color(188, 188, 188), //button back
                    Color.black, //button fore
                    new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                    Color.white, //player 1 back
                    Color.black, //player 1 fore
                    Color.black, //player 2 back
                    Color.white, //player 2 fore
                    Color.white); //foreGround

            String background = getParameter("background");
            String foreground = getParameter("foreground");
            if (background != null) {
                gameStyle.boardBack = Color.decode(background);
            }
            if (foreground != null) {
                gameStyle.foreGround = Color.decode(foreground);
            }

            cardLayout = new CardLayout();
            setLayout(cardLayout);

            setBackground(Color.white);

            connectPanel = new ConnectPanel(
                    gameStyle.boardBack, gameStyle.foreGround);
            add("CONNECT", connectPanel);

            errorPnl = new ErrorPanel(this, gameStyle);
            add(ERROR_PANEL, errorPnl);


            activeServers = ActiveServerLoader.getActiveServers(host);
            loginPnl = new LoginPanel(activeServers, gameStyle);
            loginPnl.addLoginListener(new LoginListener() {

                public void login(String name, String password, int port) {

                    PenteApplet.this.playerName = name;
                    PenteApplet.this.password = password;
                    PenteApplet.this.port = port;
                    try {
                        connect();
                    } catch (Throwable t) {
                        System.out.println("unknown error on login");
                        t.printStackTrace();
                        cardLayout.show(PenteApplet.this, ERROR_PANEL);
                    }
                }
            });
            add(LOGIN, (Component) loginPnl);


            setSize(640, 390);

            // applet connections
            guest = getParameter("guest") != null;
            playerName = getParameter("playerName");
            password = getParameter("password");

            if (guest || (playerName != null && password != null)) {
                port = Integer.parseInt(getParameter("gameServerPort"));
                //System.out.println("read port="+port);
                connect();
            }
            // jws connections
            else {
                cardLayout.show(this, LOGIN);
            }

        } catch (Throwable t) {
            System.out.println("unknown init error");
            t.printStackTrace();
            cardLayout.show(this, ERROR_PANEL);
        }
    }

    private void getClientInfo() {
        info = new ClientInfo();
        // browser can't be read by jws clients
        try {
            info.setBrowser(System.getProperty("browser"));
        } catch (SecurityException e) {
            info.setBrowser("JWS");
        }
        // all these should be readable by jws and browsers
        try {
            info.setJavaVersion(System.getProperty("java.version"));
            info.setJavaClassVersion(System.getProperty("java.class.version"));
            info.setOs(System.getProperty("os.name"));
            info.setOsVersion(System.getProperty("os.version"));

        } catch (SecurityException e) {
            System.out.println("getClientInfo error");
            e.printStackTrace();
        }
    }

    public String getHost() {
        return host;
    }

    public void reconnect(int port) {
        this.port = port;
        changingRooms = true;
        reconnect();
        changingRooms = false;
    }

    public void reconnect() {
        logout();
        connect();
    }

    private void connect() {

        try {
            cardLayout.show(this, "CONNECT");
            connectPanel.printMessage("Connecting to game room, port " + port + "...");
            System.out.println("Connecting to game room, host=" + host + ", port=" + port);
            statsFrame = new Frame();
            playerDataCache = new PlayerDataCache();

            SocketFactory factory = SSLSocketFactory.getDefault();
            Socket socket = factory.createSocket(host, port);
            // because client sends many short messages
            socket.setTcpNoDelay(true);
            // timeout after 30 seconds
            // this should be ok because we receive pings every 15 seconds
            //socket.setSoTimeout(30 * 1000); 

            socketDSGEventHandler = new ClientSocketDSGEventHandler(socket);
            socketDSGEventHandler.addListener(new DSGEventListener() {
                public void eventOccurred(DSGEvent dsgEvent) {
                    if (dsgEvent instanceof DSGPingEvent) {
                        socketDSGEventHandler.eventOccurred(dsgEvent);
                        //DSGPingEvent p = (DSGPingEvent) dsgEvent;
                    }
                    //else {//TEMP
                    //    System.out.println("in " + playerName + ": " + dsgEvent);
                    //}
                }
            });
            //dsgEventLogger = new DSGEventLogger(socketDSGEventHandler);
            preferenceHandler = new PreferenceHandler(
                    socketDSGEventHandler, socketDSGEventHandler);

            final DummyFrame frame = new DummyFrame(this);
            frame.setSize(640, 440);

            socketDSGEventHandler.addListener(new DSGEventListener() {
                public void eventOccurred(DSGEvent dsgEvent) {

                    try {

                        if (dsgEvent instanceof DSGJoinMainRoomEvent) {
                            DSGJoinMainRoomEvent mainRoomEvent = (DSGJoinMainRoomEvent) dsgEvent;
                            if (mainRoomEvent.getPlayer().equals(playerName)) {
                                booted = false;
                                cardLayout.show(PenteApplet.this, MAINROOM);
                            } else {
                                playerDataCache.addPlayer(mainRoomEvent.getDSGPlayerData());
                            }
                        } else if (dsgEvent instanceof DSGJoinMainRoomErrorEvent) {
                            DSGJoinMainRoomErrorEvent mainRoomError = (DSGJoinMainRoomErrorEvent) dsgEvent;
                            if (mainRoomError.getError() == DSGMainRoomErrorEvent.ALREADY_IN_MAIN_ROOM) {
                                cardLayout.show(PenteApplet.this, LOGIN);
                                loginPnl.showAlreadyLoggedIn();
                            }
                        } else if (dsgEvent instanceof DSGExitMainRoomEvent) {
                            DSGExitMainRoomEvent exitEvent = (DSGExitMainRoomEvent) dsgEvent;
                            if (exitEvent.getPlayer() == null && !booted) {
                                if (!changingRooms) {
                                    errorPnl.setConnectionError();
                                    cardLayout.show(PenteApplet.this, ERROR_PANEL);
                                    logout();
                                }
                            } else if (exitEvent.getPlayer().equals(playerName) &&
                                    exitEvent.wasBooted()) {
                                errorPnl.setBooted();
                                booted = true;
                                cardLayout.show(PenteApplet.this, ERROR_PANEL);
                                logout();
                            }
                        } else if (dsgEvent instanceof DSGUpdatePlayerDataEvent) {
                            DSGUpdatePlayerDataEvent updateEvent =
                                    (DSGUpdatePlayerDataEvent) dsgEvent;
                            playerDataCache.updatePlayer(updateEvent.getDSGPlayerData());
                        } else if (dsgEvent instanceof DSGIgnoreEvent) {
                            DSGIgnoreEvent ignoreEvent = (DSGIgnoreEvent) dsgEvent;
                            playerDataCache.updateIgnore(ignoreEvent.getPlayers());
                            //note any open dialogs won't be modified
                        } else if (dsgEvent instanceof DSGServerStatsEvent) {
                            DSGServerStatsEvent statsEvent = (DSGServerStatsEvent) dsgEvent;

                            new ServerStatsDialog(
                                    statsFrame,
                                    gameStyle,
                                    statsEvent,
                                    safeGetLocationOnScreen());
                        } else if (dsgEvent instanceof DSGAddAITableEvent) {
                            aiDataVector.addElement(((DSGAddAITableEvent) dsgEvent).getAIData());
                        } else if (dsgEvent instanceof DSGLoginErrorEvent) {
                            DSGLoginErrorEvent loginErrorEvent = (DSGLoginErrorEvent) dsgEvent;
                            if (loginErrorEvent.getError() == DSGLoginErrorEvent.INVALID_LOGIN) {
                                cardLayout.show(PenteApplet.this, LOGIN);
                                loginPnl.showInvalidLogin();
                            } else if (loginErrorEvent.getError() == DSGLoginErrorEvent.PRIVATE_ROOM) {
                                cardLayout.show(PenteApplet.this, LOGIN);
                                loginPnl.showPrivateRoom();
                            }
                        } else if (dsgEvent instanceof DSGLoginEvent) {

                            DSGLoginEvent l = (DSGLoginEvent) dsgEvent;
                            if (l.isGuest()) {
                                playerName = l.getPlayer();
                            }
                            playerDataCache.addPlayer(l.getMe());
                            mainRoomPnl = new MainRoomPanel(
                                    l.getTime(),
                                    PenteApplet.this,
                                    l.getMe(),
                                    l.getServerData(),
                                    activeServers,
                                    socketDSGEventHandler,
                                    socketDSGEventHandler,
                                    gameStyle,
                                    sounds,
                                    frame,
                                    playerDataCache,
                                    preferenceHandler);
                            add(MAINROOM, mainRoomPnl);

                            tableController =
                                    new TableController(
                                            getHost(),
                                            l.getMe(),
                                            gameStyle,
                                            socketDSGEventHandler,
                                            socketDSGEventHandler,
                                            sounds,
                                            aiDataVector,
                                            mainRoomPnl.getPlayerList(),
                                            playerDataCache,
                                            preferenceHandler);

                            mainRoomPnl.setTableController(tableController);
                        }

                    } catch (Throwable t) {
                        System.out.println("unknown socket event error");
                        t.printStackTrace();
                    }
                }
            });


            DSGLoginEvent l = null;
            if (guest) {
                connectPanel.printMessage("Connected. Logging in as guest");
                l = new DSGLoginEvent(guest, info);
                // if guest disconnected, reconnect with the correct guest name
                if (playerName != null) {
                    l.setPlayer(playerName);
                }
            } else {
                connectPanel.printMessage("Connected. Logging in as " + playerName + "...");
                l = new DSGLoginEvent(playerName, password, info);
            }
            socketDSGEventHandler.eventOccurred(l);

        } catch (Throwable t) {
            System.out.println("unknown connect error");
            t.printStackTrace();
            cardLayout.show(this, ERROR_PANEL);
        }
    }

    public Point safeGetLocationOnScreen() {
        Point location = getLocationOnScreen();

        if (location.x < 0) {
            location.x = 0;
        }
        if (location.y < 0) {
            location.y = 0;
        }

        return location;
    }

    public void stop() {
        logout();
    }

    public void destroy() {
        logout();
    }

    public void logout() {

        if (mainRoomPnl != null) {
            mainRoomPnl.destroy();
            mainRoomPnl = null;
        }

        if (tableController != null) {
            tableController.destroy();
            tableController = null;
        }

        if (statsFrame != null) {
            statsFrame.dispose();
            statsFrame = null;
        }

        if (aiDataVector != null) {
            aiDataVector.removeAllElements();
        }

        if (socketDSGEventHandler != null) {
            socketDSGEventHandler.destroy();
            socketDSGEventHandler = null;
        }
    }

    public boolean isGuest() {
        return guest;
    }
}