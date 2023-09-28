package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.client.awt.*;

public class MiddleSetDialogTester {

    public static void main(String args[]) {

        GameStyles gameStyle = new GameStyles(
                new Color(0, 102, 153), //board back
                new Color(188, 188, 188), //button back
                Color.black, //button fore
                new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                Color.white, //player 1 back
                Color.black, //player 1 fore
                Color.black, //player 2 back
                Color.white, //player 2 fore
                new Color(188, 188, 188)); //watcher

        final Frame f = new Frame();
        f.setLocation(100, 100);
        final MiddleSetDialog d = new MiddleSetDialog(f, gameStyle);
        d.setLocation(100, 100);
        d.addActionListener(e -> {
            System.out.println("actionPerformed - " + e.getActionCommand());
            d.dispose();
            f.dispose();
        });

        d.setVisible(true);

        d.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                d.dispose();
                f.dispose();
            }
        });

        try {
            Thread.sleep(1000 * 62);
        } catch (InterruptedException e) {
        }

        //d.timeHasExpired();
    }
}
