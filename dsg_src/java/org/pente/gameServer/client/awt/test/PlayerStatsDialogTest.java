/**
 * PlayerStatsDialogTest.java
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

package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGEventListener;

public class PlayerStatsDialogTest {

    public static void main(String[] args) {
        final Frame f = new Frame("PlayerStatsDialog2Test");

        final GameStyles gameStyle =
                new GameStyles(new Color(0, 0, 153), //board back
                        new Color(51, 102, 204), //button back
                        Color.white, //button fore
                        new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                        Color.white, //player 1 back
                        Color.black, //player 1 fore
                        Color.black, //player 2 back
                        Color.white, //player 2 fore
                        new Color(51, 102, 204)); //watcher

        Button statsButton = new Button("Get Stats");
        statsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                DSGPlayerData dsgPlayerData = new SimpleDSGPlayerData();
                dsgPlayerData.setName("dweebo");
                dsgPlayerData.setAge(26);
                dsgPlayerData.setEmail("dweebo@pente.org");
                dsgPlayerData.setEmailVisible(true);
                dsgPlayerData.setHomepage("http://www.google.com");
                dsgPlayerData.setLastLoginDate(new java.util.Date());
                dsgPlayerData.setLocation("Here");
                dsgPlayerData.setLogins(2125);
                dsgPlayerData.setNameColor(Color.red);
                dsgPlayerData.setNote("Test note");
                dsgPlayerData.setSex('M');
                dsgPlayerData.setRegisterDate(new java.util.Date());

                int c = 0;
                byte b[] = new byte[1024 * 100];
                try {
                    FileInputStream in = new FileInputStream(
                            "/dsg_src/httpdocs/gameServer/images/dweebo.jpg");
                    while (true) {
                        int cnt = in.read(b, c, 1024);
                        if (cnt == -1) break;
                        c += cnt;
                    }
                    in.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                byte a[] = new byte[c];
                for (int i = 0; i < a.length; i++) a[i] = b[i];

                dsgPlayerData.setAvatar(a);

                DSGPlayerGameData penteGameData = new SimpleDSGPlayerGameData();
                penteGameData.setGame(GridStateFactory.PENTE);
                penteGameData.setLosses(10);
                penteGameData.setWins(100);
                penteGameData.setRating(1800.00d);
                penteGameData.setStreak(7);
                dsgPlayerData.addPlayerGameData(penteGameData);
                DSGPlayerGameData keryoGameData = penteGameData.getCopy();
                keryoGameData.setGame(GridStateFactory.KERYO);
                keryoGameData.setLosses(12);
                keryoGameData.setWins(120);
                keryoGameData.setRating(1700.00d);
                keryoGameData.setStreak(8);
                dsgPlayerData.addPlayerGameData(keryoGameData);
                PlayerDataCache cc = new PlayerDataCache();
                cc.addPlayer(dsgPlayerData);

                new PlayerStatsDialog(f, f.getLocation(), "dweebo", "localhost",
                        gameStyle, cc, new DSGEventListener() {
                    public void eventOccurred(DSGEvent dsgEvent) {
                        System.out.println("event occurred = " + dsgEvent);
                    }
                }, false);
            }
        });

        f.add(statsButton);

        f.setSize(400, 400);
        f.setLocation(100, 100);
        f.setVisible(true);

        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
            }
        });
    }
}

