package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;

public class CustomTablesPanelThreadTest {

    private static final GameStyles gameStyle = new GameStyles(
        new Color(0, 0, 153), //board back
        new Color(188, 188, 188), //button back
        Color.white, //button fore
        new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
        Color.white, //player 1 back
        Color.black, //player 1 fore
        Color.black, //player 2 back
        Color.white, //player 2 fore
        new Color(51, 102, 204)); //watcher

    public static void main(String[] args) {

        final Frame f = new Frame("CustomTablePanel");
        f.setLayout(new BorderLayout(2, 2));
        DSGPlayerData me = new SimpleDSGPlayerData();
        me.setName("dweebo");
        me.setPlayerType(DSGPlayerData.HUMAN);
        final CustomTablesPanel panel = new CustomTablesPanel(me, gameStyle);
        f.add(panel, "Center");

        f.pack();
        f.setLocation(100, 100);
        f.setVisible(true);


        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 600; i++) {
                    Dimension s = f.getSize();
                    int x = (int) (Math.random() * 50);
                    int y = (int) (Math.random() * 50);
                        
                    int b = i % 2 == 0 ? -1 : 1;
                    x = s.width + b * x;
                    y = s.height + b * y;
    
                    f.setSize(x, y);
                    panel.setSize(x, y);
                        
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();

        final DSGChangeStateTableEvent event = new DSGChangeStateTableEvent();
        event.setGame(0);
        event.setIncrementalSeconds(5);
        event.setInitialMinutes(7);
        event.setRated(false);
        event.setTableType(DSGChangeStateTableEvent.TABLE_TYPE_PUBLIC);
        event.setTimed(true);
        
        new Thread(new Runnable() {
            public void run() {
                for (int i = 0; i < 600; i++) {
                    for (int j = 0; j < 20; j++) {
                        panel.addTable(j + 1);

                        DSGPlayerData d = new SimpleDSGPlayerData();
                        d.setName("testplayer");
                        d.setPlayerType(DSGPlayerData.HUMAN);
                        panel.addPlayer(j + 1, d);
                        

                        d = new SimpleDSGPlayerData();
                        d.setName("testplayer2");
                        d.setPlayerType(DSGPlayerData.HUMAN);
                        panel.addPlayer(j + 1, d);
                        

                        d = new SimpleDSGPlayerData();
                        d.setName("testplayer3");
                        d.setPlayerType(DSGPlayerData.HUMAN);
                        panel.addPlayer(j + 1, d);
                        
                        panel.sitPlayer(j + 1, "testplayer", 1);
                        panel.sitPlayer(j + 1, "testplayer2", 2);
                        panel.standPlayer(j + 1, "testplayer");
                        panel.standPlayer(j + 1, "testplayer2");
                        panel.changeTableState(j + 1, event);
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                
                    for (int j = 0; j < 20; j++) {
                        panel.removeTable(j + 1);
                    }
                }
            }            
        }).start();
        
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
            }
        });
    }
}
