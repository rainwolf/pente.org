package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.event.DSGInviteTableEvent;
import org.pente.gameServer.server.*;

public class InviteResponseFrameTest {

    public static void main(String[] args) throws Throwable {

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

        DSGInviteTableEvent inviteEvent = new DSGInviteTableEvent("dweebo",
                2, "peter", "come play with me");
        CustomTableData data = new CustomTableData();

        final Frame f = new Frame();
        f.setLocation(500, 500);
        final InviteResponseFrame d = new InviteResponseFrame(
                gameStyle, inviteEvent, 1932, data);
        d.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("action " + e.getActionCommand());
                System.exit(1);
            }
        });
        d.setVisible(true);
    }
}
