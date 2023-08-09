package org.pente.gameServer.client.awt.test;

import java.util.*;
import java.awt.Color;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.event.*;

public class GameBoardFrameTest {

    private static final GameStyles gameStyle = new GameStyles(
            new Color(0, 0, 153), //board back
            new Color(51, 102, 204), //button back
            Color.white, //button fore
            new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
            Color.white, //player 1 back
            Color.black, //player 1 fore
            Color.black, //player 2 back
            Color.white, //player 2 fore
            new Color(51, 102, 204)); //watcher

    public static void main(String args[]) {

        new GameBoardFrameTest();
    }

    private GameBoardFrame frame;

    public GameBoardFrameTest() {

        DSGEventListener listener = new DSGEventListener() {
            public void eventOccurred(DSGEvent dsgEvent) {
                System.out.println(dsgEvent);
                if (dsgEvent instanceof DSGExitTableEvent) {
                    frame.dispose();
                }
            }
        };

        PlayerListComponent mainRoomPlayer = new PlayerListPanel(gameStyle.boardBack);
        DSGPlayerData d = new SimpleDSGPlayerData();
        d.setName("peter");
        d.setPlayerType(DSGPlayerData.HUMAN);
        mainRoomPlayer.addPlayer(d);

        d = new SimpleDSGPlayerData();
        d.setName("mmammel");
        d.setPlayerType(DSGPlayerData.HUMAN);
        mainRoomPlayer.addPlayer(d);

        Vector aiData = new Vector();
        AIData ai1 = new AIData();
        ai1.setName("mm_ai");
        ai1.addValidGame(GridStateFactory.PENTE);
        ai1.addValidGame(GridStateFactory.KERYO);
        ai1.setNumLevels(8);
        aiData.add(ai1);

        DSGPlayerData me = new SimpleDSGPlayerData();
        me.setName("dweebo");
        me.setPlayerType(DSGPlayerData.HUMAN);
        frame = new GameBoardFrame(
                "localhost", gameStyle, 1, listener, me, new Vector(),
                new String[0], null, aiData, mainRoomPlayer, new PlayerDataCache(),
                new PreferenceHandler(new DSGEventSource() {
                    public void addListener(DSGEventListener dsgEventListener) {
                    }

                    public void removeListener(DSGEventListener dsgEventListener) {
                    }
                },
                        new DSGEventListener() {
                            public void eventOccurred(DSGEvent dsgEvent) {
                            }

                            ;
                        }
                ));
        frame.receiveSetOwner("dweebo");
        frame.receivePlayerJoin(new DSGJoinTableEvent("dweebo", 1));
    }
}
