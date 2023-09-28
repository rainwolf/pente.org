/**
 * BootDialog.java
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

import org.pente.gameServer.client.*;

public class BootDialog extends Dialog {

    public BootDialog(
            Frame frame,
            GameStyles gameStyle,
            final PlayerListComponent playerList,
            final PlayerActionListener bootListener,
            final boolean showMinutes) {

        super(frame, "Boot Player", false);

        setBackground(gameStyle.boardBack);


        final Label minutesLabel = new Label("Time");
        minutesLabel.setBackground(gameStyle.boardBack);
        minutesLabel.setForeground(gameStyle.foreGround);

        final TextField minutesText = new TextField("5");
        minutesText.setBackground(Color.white);

        Button bootButton = gameStyle.createDSGButton("Boot");
        bootButton.addActionListener(e -> {
            if (playerList.getSelectedPlayer() != null) {
                if (showMinutes) {
                    bootListener.actionRequested(
                            playerList.getSelectedPlayer(),
                            new Integer(minutesText.getText()));
                } else {
                    bootListener.actionRequested(playerList.getSelectedPlayer());
                }
            }
            dispose();
        });
        Button cancelButton = gameStyle.createDSGButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        int y = 1;
        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setBackground(gameStyle.boardBack);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 1;
        gbc.gridy = y++;
        gbc.weighty = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.anchor = GridBagConstraints.NORTH;
        panel.add((Component) playerList, gbc);

        if (showMinutes) {

            gbc.gridy = y++;
            gbc.gridx = 1;
            gbc.weighty = 0;
            gbc.weightx = 1;
            gbc.gridwidth = 1;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.EAST;
            panel.add(minutesLabel, gbc);

            gbc.gridx = 2;
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(minutesText, gbc);
        }

        gbc.gridy = y;
        gbc.gridx = 1;
        gbc.weighty = 0;
        gbc.weightx = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(bootButton, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(cancelButton, gbc);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        setLayout(new BorderLayout());
        setBackground(gameStyle.boardBack);
        add("Center", panel);

        pack();
        centerDialog(frame);
        setVisible(true);
    }

    private void centerDialog(Frame frame) {

        Point location = new Point();
        location.x = frame.getLocation().x +
                (frame.getSize().width + frame.getInsets().right - frame.getInsets().left) / 2 -
                getSize().width / 2;
        location.y = frame.getLocation().y +
                (frame.getSize().height + frame.getInsets().top - frame.getInsets().bottom) / 2 -
                (getSize().height + getInsets().top - getInsets().bottom) / 2;
        setLocation(location);
    }
}
