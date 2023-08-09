/**
 * PlayerListPanelTest.java
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

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;

/** This class attempts to test any synchronization, threading
 *  problems that might occur by opening a number of threads
 *  having them add and delete from a PlayerListPanel
 */
public class PlayerListPanelTest {

    public static void main(String args[]) {

        final Frame f = new Frame("PlayerListPanelTest");

        final PlayerListPanel playerListPanel = new PlayerListPanel(Color.blue);
        playerListPanel.setTableName("Main Room");
        playerListPanel.setGame(1);

        f.add(playerListPanel);

        f.pack();
        f.setLocation(100, 100);
        f.setVisible(true);

        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
            }
        });

        for (int i = 0; i < 9; i++) {
            DSGPlayerData d = new SimpleDSGPlayerData();
            d.setName("dweebo" + i);
            d.setPlayerType(DSGPlayerData.HUMAN);
            DSGPlayerGameData g = new SimpleDSGPlayerGameData();
            g.setGame(1);
            g.setComputer('N');
            g.setRating(999 + i * 200);
            if (i == 7) g.setWins(0);
            else if (i == 8) g.setWins(1);
            else g.setWins(21);
            d.addPlayerGameData(g);
            d.setAdmin(i % 5 == 0);
            if (i == 3) {
                playerListPanel.setOwner(d.getName());
                g.setTourneyWinner(DSGPlayerGameData.TOURNEY_WINNER_GOLD);
            }

            playerListPanel.addPlayer(d);
        }

//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//		}
        System.out.println("starting to run");

//		for (int i = 0; i < 10; i++) {
//			addThread(playerListPanel, i, 1000, 0);
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//			}
//		}

        playerListPanel.addGetStatsListener(new PlayerActionAdapter() {
            public void actionRequested(String playerName) {
                System.out.println("get stats for " + playerName);
            }
        });
    }

    private static void addThread(final PlayerListPanel p, final int threadNum, final int repitition, final int delay) {
        new Thread(new Runnable() {
            public void run() {
                for (int i = 1; i < repitition; i++) {
                    DSGPlayerData d = new SimpleDSGPlayerData();
                    d.setName(threadNum + "-" + i);
                    d.setPlayerType(DSGPlayerData.HUMAN);

                    p.addPlayer(d);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                    }
                }
                for (int i = 1; i < repitition; i++) {
                    p.removePlayer(threadNum + "-" + i);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();
    }
}

