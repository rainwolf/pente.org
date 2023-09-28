/**
 * InviteDialog.java
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
import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.client.*;

public class InviteDialog extends Dialog {

    private static final Font labelFont = new Font("Dialog", Font.BOLD, 14);

    private TextField inviteText;

    public InviteDialog(
            Frame frame,
            GameStyles gameStyle,
            int game, boolean rated, boolean timed, int initialTime,
            int incrementalTime,
            final PlayerListComponent playerList,
            final PlayerActionListener inviteActionListener) {

        super(frame, "Invite Player", false);

        ActionListener disposer = e -> dispose();

        Label gameLabel = new Label("Game: " + GridStateFactory.getGameName(
                game));
        Label ratedLabel = new Label("Rated: " + (rated ? "Yes" : "No"));
        Label timerLabel = new Label("");
        if (timed) {
            timerLabel.setText("Timer: " + initialTime + "/" + incrementalTime);
        } else {
            timerLabel.setText("Timer: No");
        }
        Label inviteTextLabel = new Label("Optional Message:");


        gameLabel.setForeground(gameStyle.foreGround);
        ratedLabel.setForeground(gameStyle.foreGround);
        timerLabel.setForeground(gameStyle.foreGround);
        inviteTextLabel.setForeground(gameStyle.foreGround);

        inviteText = new TextField(15);

        Button inviteButton = gameStyle.createDSGButton("Invite");
        inviteButton.addActionListener(e -> {
            if (playerList.getSelectedPlayer() != null) {
                inviteActionListener.actionRequested(
                        playerList.getSelectedPlayer());
            }
            dispose();
        });

        Button cancelButton = gameStyle.createDSGButton("Cancel");
        cancelButton.addActionListener(disposer);

        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setLayout(new BorderLayout());
        panel.setBackground(gameStyle.boardBack);

        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.gridheight = 3;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add((Component) playerList, gbc);

        gbc.gridy = 1;
        gbc.gridx = 2;
        gbc.gridwidth = 2;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;

        Panel labelPanel = new Panel();
        labelPanel.setLayout(new GridLayout(4, 1));
        labelPanel.add(gameLabel);
        labelPanel.add(ratedLabel);
        labelPanel.add(timerLabel);
        labelPanel.add(inviteTextLabel);
        panel.add(labelPanel, gbc);

        gbc.gridy++;
        panel.add(inviteText, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(inviteButton, gbc);

        gbc.gridx = 3;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(cancelButton, gbc);

        setLayout(new BorderLayout());
        setBackground(gameStyle.boardBack);
        add("Center", panel);

        pack();
        centerDialog(frame);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    public String getInviteText() {
        return inviteText.getText();
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
