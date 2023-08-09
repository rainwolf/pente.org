/**
 * ServerStatsDialog.java
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

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.event.*;

public class ServerStatsDialog extends Dialog {

    private static final Font labelFont = new Font("Dialog", Font.BOLD, 14);

    public ServerStatsDialog(Frame frame, GameStyles gameStyle, DSGServerStatsEvent statsEvent, Point location) {

        super(frame, "Server Stats", false);

        Label labels[] = new Label[10];
        labels[0] = new Label("Running for: ");
        labels[1] = new Label(statsEvent.getUpTime());
        labels[2] = new Label("Logins: ");
        labels[3] = new Label(Integer.toString(statsEvent.getLogins()));
        labels[4] = new Label("Max Players on: ");
        labels[5] = new Label(Integer.toString(statsEvent.getMaxPlayers()));
        labels[6] = new Label("Games played: ");
        labels[7] = new Label(Integer.toString(statsEvent.getGames()));
        labels[8] = new Label("Commands sent: ");
        labels[9] = new Label(Integer.toString(statsEvent.getEvents()));

        Panel panel = new Panel();
        panel.setBackground(gameStyle.boardBack);
        panel.setLayout(new GridLayout(5, 2, 0, 0));

        //setLayout(new GridLayout(5, 2, 0, 0));
        //setBackground(gameStyle.boardBack);

        for (int i = 0; i < labels.length; i++) {
            labels[i].setForeground(gameStyle.foreGround);
            labels[i].setFont(labelFont);
            panel.add(labels[i]);
        }

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        add(panel);
        pack();
        setResizable(false);
        setLocation(location);
        setVisible(true);
    }
}

