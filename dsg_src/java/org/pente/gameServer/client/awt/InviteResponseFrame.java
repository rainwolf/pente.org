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
import org.pente.gameServer.event.DSGInviteTableEvent;

public class InviteResponseFrame extends Frame implements ActionListener {

    private TextField responseText;
    private ActionListener actionListener;
    private Checkbox ignoreCheck;

    private static final Font boldFont = new Font("Dialog", Font.BOLD, 14);

    public InviteResponseFrame(
            GameStyles gameStyle,
            DSGInviteTableEvent inviteEvent,
            int rating,
            CustomTableData tableData) {

        super("Invitation");

        Label textLabel = new Label(inviteEvent.getPlayer() +
                " has invited you to a game.");

        Label tableLabel = new Label("Table: " + inviteEvent.getTable());
        Label gameLabel = new Label("Game: " + GridStateFactory.getGameName(
                tableData.getGame()));
        Label ratedLabel = new Label("Rated: " + (tableData.isRated() ? "Yes" : "No"));
        Label timerLabel = new Label("");
        if (tableData.isTimed()) {
            timerLabel.setText("Timer: " + tableData.getInitialTime() +
                    "/" + tableData.getIncrementalTime());
        } else {
            timerLabel.setText("Timer: No");
        }
        Label messageLabel = new Label(inviteEvent.getPlayer() +
                "'s Message:");
        Label opponentRatingLabel = new Label(inviteEvent.getPlayer() +
                "'s Rating: " + rating);

        Label responseMessageLabel = new Label("Your response:");


        textLabel.setForeground(gameStyle.foreGround);
        textLabel.setFont(boldFont);
        tableLabel.setForeground(gameStyle.foreGround);
        gameLabel.setForeground(gameStyle.foreGround);
        ratedLabel.setForeground(gameStyle.foreGround);
        timerLabel.setForeground(gameStyle.foreGround);
        messageLabel.setForeground(gameStyle.foreGround);
        opponentRatingLabel.setForeground(gameStyle.foreGround);
        responseMessageLabel.setForeground(gameStyle.foreGround);

        String inviteText = inviteEvent.getInviteText();
        if (inviteText == null) {
            inviteText = "";
        }
        TextArea messageText = new TextArea(inviteText, 1, 25, TextArea.SCROLLBARS_VERTICAL_ONLY);
        messageText.setEditable(false);
        messageText.setBackground(Color.white);

        responseText = new TextField(25);

        ignoreCheck = new Checkbox("Ignore invites from this player");
        ignoreCheck.setBackground(gameStyle.boardBack);
        ignoreCheck.setForeground(gameStyle.foreGround);
        ignoreCheck.setState(false);

        Button acceptButton = gameStyle.createDSGButton("Accept");
        acceptButton.addActionListener(this);

        final Button declineButton = gameStyle.createDSGButton("Decline");
        declineButton.addActionListener(this);

        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setLayout(new BorderLayout());
        panel.setBackground(gameStyle.boardBack);

        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.gridheight = 1;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(textLabel, gbc);

        gbc.gridy = 2;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;

        Panel labelPanel = new Panel();
        labelPanel.setLayout(new GridLayout(4, 1));
        labelPanel.add(gameLabel);
        labelPanel.add(tableLabel);
        labelPanel.add(ratedLabel);
        labelPanel.add(timerLabel);
        panel.add(labelPanel, gbc);

        gbc.gridx = 2;
        gbc.gridheight = 1;
        Panel labelPanel2 = new Panel();
        labelPanel2.setLayout(new GridLayout(2, 1));
        labelPanel2.add(opponentRatingLabel);
        labelPanel2.add(messageLabel);
        panel.add(labelPanel2, gbc);

        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(messageText, gbc);


        gbc.gridy = 4;
        gbc.gridwidth = 2;
        panel.add(new Panel(), gbc);

        gbc.gridy = 5;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(responseMessageLabel, gbc);

        gbc.gridx = 2;
        panel.add(responseText, gbc);

        gbc.gridy = 6;
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttonPanel.add(acceptButton);
        buttonPanel.add(declineButton);
        panel.add(buttonPanel, gbc);

        gbc.gridy = 7;
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(ignoreCheck, gbc);

        setLayout(new BorderLayout());
        setBackground(gameStyle.boardBack);
        add("Center", panel);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                actionListener.actionPerformed(new ActionEvent(this, 0, "Decline"));
                dispose();
            }
        });

        pack();
        //centerDialog(frame);
    }

    public void setVisible(boolean visible) {
        super.setVisible(visible);
        toFront();
        requestFocus();
    }

    public void addActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void actionPerformed(ActionEvent e) {
        actionListener.actionPerformed(e);
        dispose();
    }

    public String getResponseText() {
        return responseText.getText();
    }

    public boolean getIgnore() {
        return ignoreCheck.getState();
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
