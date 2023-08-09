/**
 * PlayerStatsDialog.java
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
import java.text.*;
import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.event.DSGEventListener;
import org.pente.gameServer.event.DSGIgnoreEvent;

public class PlayerStatsDialog extends Dialog
        implements PlayerDataChangeListener {

    private static final Font labelFont = new Font("Dialog", Font.BOLD, 14);
    private static final NumberFormat numberFormat =
            NumberFormat.getPercentInstance();
    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

    private int game = -1;  // no game initially selected
    private String playerName;

    private Label nameDataLabel = new Label();
    private Label donorDataLabel = new Label();
    private Label registerDataLabel = new Label();
    private Label loginsDataLabel = new Label();
    private Label locationDataLabel = new Label();
    private Label sexDataLabel = new Label();
    private Label ageDataLabel = new Label();
    private Label emailDataLabel = new Label();
    private Label homepageDataLabel = new Label();
    private Label noteDataLabel = new Label();

    private ImageCanvas imageCanvas = null;
    private PlayerDataCache playerDataCache;

    private Choice gameChoice = new Choice();
    private Label playerNameDataLabel = new Label();
    private Label ratingDataLabel = new Label();
    private Label winsDataLabel = new Label();
    private Label lossesDataLabel = new Label();
    private Label totalGamesDataLabel = new Label();
    private Label percentageDataLabel = new Label();
    private Label streakDataLabel = new Label();

    private boolean firstLoad = true;
    private ItemListener itemListener;

    private Checkbox ignoreChatCheck;
    private Checkbox ignoreInviteCheck;

    /**
     * This constructor sets up the labels and displays the frame.
     */
    public PlayerStatsDialog(
            final Frame frame,
            final Point location,
            final String playerName,
            final String host,
            final GameStyles gameStyle,
            final PlayerDataCache playerDataCache,
            final DSGEventListener dsgEventListener,
            final boolean me) {
        super(frame, "Player Stats", false);

        this.playerName = playerName;
        this.playerDataCache = playerDataCache;

        Vector labels = new Vector();
        Label nameLabel = new Label("Name:");
        labels.addElement(nameLabel);
        labels.addElement(nameDataLabel);
        Label donorLabel = new Label("Donor:");
        labels.addElement(donorLabel);
        labels.addElement(donorDataLabel);
        Label registerLabel = new Label("Register:");
        labels.addElement(registerLabel);
        labels.addElement(registerDataLabel);
        Label loginsLabel = new Label("Logins:");
        labels.addElement(loginsLabel);
        labels.addElement(loginsDataLabel);
        Label locationLabel = new Label("Location:");
        labels.addElement(locationLabel);
        labels.addElement(locationDataLabel);
        Label sexLabel = new Label("Sex:");
        labels.addElement(sexLabel);
        labels.addElement(sexDataLabel);
        Label ageLabel = new Label("Age:");
        labels.addElement(ageLabel);
        labels.addElement(ageDataLabel);
        Label emailLabel = new Label("Email:");
        labels.addElement(emailLabel);
        labels.addElement(emailDataLabel);
        Label homepageLabel = new Label("Home page:");
        labels.addElement(homepageLabel);
        labels.addElement(homepageDataLabel);
        Label noteLabel = new Label("Note:");
        labels.addElement(noteLabel);
        labels.addElement(noteDataLabel);

        if (!me) {
            final DSGPlayerData d = playerDataCache.getPlayer(playerName);
            ItemListener updateIgnores = new ItemListener() {
                public void itemStateChanged(ItemEvent event) {

                    DSGIgnoreData i = playerDataCache.getIgnore(d.getPlayerID());
                    if (i == null) {
                        i = new DSGIgnoreData();
                        i.setPid(0);
                        i.setIgnorePid(d.getPlayerID());
                        playerDataCache.addIgnore(i);
                    }
                    i.setIgnoreChat(ignoreChatCheck.getState());
                    i.setIgnoreInvite(ignoreInviteCheck.getState());
                    i.setGuest(d.isGuest());

                    dsgEventListener.eventOccurred(
                            new DSGIgnoreEvent(0, new DSGIgnoreData[]{i}));

                    if (!i.getIgnoreChat() && !i.getIgnoreInvite()) {
                        playerDataCache.removeIgnore(d.getPlayerID());
                    }
                }
            };


            ignoreChatCheck = new Checkbox("Ignore chat from this player");
            ignoreChatCheck.setBackground(gameStyle.boardBack);
            ignoreChatCheck.setForeground(gameStyle.foreGround);
            ignoreChatCheck.setState(playerDataCache.isChatIgnored(d.getPlayerID()));
            ignoreChatCheck.addItemListener(updateIgnores);

            ignoreInviteCheck = new Checkbox("Ignore invites from this player");
            ignoreInviteCheck.setBackground(gameStyle.boardBack);
            ignoreInviteCheck.setForeground(gameStyle.foreGround);
            ignoreInviteCheck.setState(playerDataCache.isInviteIgnored(d.getPlayerID()));
            ignoreInviteCheck.addItemListener(updateIgnores);
        }

        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        Panel panel = new Panel();
        panel.setLayout(gridBagLayout);

        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < labels.size(); i++) {
            Label l = (Label) labels.elementAt(i);
            l.setForeground(gameStyle.foreGround);
            l.setBackground(gameStyle.boardBack);
            l.setFont(labelFont);
            constraints.gridx = i % 2 + 1;
            constraints.gridy = i / 2 + 1;
            panel.add(l, constraints);
        }


        //byte b[] = null;
        //imageCanvas = new ImageCanvas(b);
        imageCanvas = new ImageCanvas(playerName, host);
        constraints.gridx = 3;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 9;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(imageCanvas, constraints);


        Vector labels2 = new Vector();
        labels2.addElement(new Label());
        labels2.addElement(new Label());

        labels2.addElement(new Label("Game Stats"));
        labels2.addElement(new Label());
        Label gameLabel = new Label("Game:");
        labels2.addElement(gameLabel);
        labels2.addElement(gameChoice);
        Label ratingLabel = new Label("Rating: ");
        labels2.addElement(ratingLabel);
        labels2.addElement(ratingDataLabel);
        Label winsLabel = new Label("Wins: ");
        labels2.addElement(winsLabel);
        labels2.addElement(winsDataLabel);
        Label lossesLabel = new Label("Losses: ");
        labels2.addElement(lossesLabel);
        labels2.addElement(lossesDataLabel);
        Label totalGamesLabel = new Label("Total: ");
        labels2.addElement(totalGamesLabel);
        labels2.addElement(totalGamesDataLabel);
        Label percentageLabel = new Label("% wins: ");
        labels2.addElement(percentageLabel);
        labels2.addElement(percentageDataLabel);
        Label streakLabel = new Label("Streak: ");
        labels2.addElement(streakLabel);
        labels2.addElement(streakDataLabel);


        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        for (int i = labels.size(); i < labels.size() + labels2.size(); i++) {
            Component l = (Component) labels2.elementAt(i - labels.size());
            l.setForeground(gameStyle.foreGround);
            l.setBackground(gameStyle.boardBack);
            l.setFont(labelFont);
            constraints.gridx = i % 2 + 1;
            constraints.gridy = i / 2 + 1;
            panel.add(l, constraints);
        }
        gameChoice.setBackground(Color.white);
        gameChoice.setForeground(Color.black);

        if (!me) {
            constraints.gridx = 1;
            constraints.gridy = labels.size() / 2 + labels2.size() + 1;
            constraints.gridwidth = 2;
            constraints.gridheight = 1;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            panel.add(ignoreChatCheck, constraints);

            constraints.gridx = 1;
            constraints.gridy = labels.size() / 2 + labels2.size() + 2;
            constraints.gridwidth = 2;
            constraints.gridheight = 1;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            panel.add(ignoreInviteCheck, constraints);
        }

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                playerDataCache.removeChangeListener(PlayerStatsDialog.this);
                dispose();
            }
        });

        itemListener = new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                game = GridStateFactory.getGameId(gameChoice.getSelectedItem());
                playerChanged(playerDataCache.getPlayer(playerName));
            }
        };

        // populate
        playerChanged(playerDataCache.getPlayer(playerName));
        playerDataCache.addChangeListener(this);


        setBackground(gameStyle.boardBack);
        panel.setBackground(gameStyle.boardBack);
        add(panel);
        pack();
        setResizable(false);
        setLocation(location);
        setVisible(true);
    }

    public void playerChanged(DSGPlayerData updateData) {
        if (!playerName.equals(updateData.getName())) {
            return;
        }

        String name = updateData.getName();
        if (updateData.isAdmin()) {
            name += " (admin)";
        }
        nameDataLabel.setText(name);
        donorDataLabel.setText(updateData.hasPlayerDonated() ? "Yes" : "No");
        registerDataLabel.setText(dateFormat.format(updateData.getRegisterDate()));
        loginsDataLabel.setText(updateData.getLogins() + "");
        String location = updateData.getLocation() == null ? "" : updateData.getLocation();
        locationDataLabel.setText(location);
        String sex = "";
        if (updateData.getSex() == 'M') sex = "Male";
        else if (updateData.getSex() == 'F') sex = "Female";
        sexDataLabel.setText(sex);
        String age = updateData.getAge() > 0 ? updateData.getAge() + "" : "";
        ageDataLabel.setText(age);
        String email = updateData.getEmailVisible() ?
                updateData.getEmail() : "Not visible";
        emailDataLabel.setText(email);
        String homepage = updateData.getHomepage() == null ? "" : updateData.getHomepage();
        homepageDataLabel.setText(homepage);
        String note = updateData.getNote() == null ? "" : updateData.getNote();
        noteDataLabel.setText(note);

        imageCanvas.updateImage(updateData.hasAvatar());


        if (updateData.hasPlayerPlayed()) {

            // populate all available games only once
            // used to repopulate each time but would loop under linux
            if (firstLoad) {
                firstLoad = false;

                Game games[] = GridStateFactory.getAllGames();
                for (int i = 1; i < games.length; i++) {
                    DSGPlayerGameData g = updateData.getPlayerGameData(i,
                            updateData.isComputer());
                    if (g != null && g.getTotalGames() > 0) {
                        if (game == -1) {
                            game = g.getGame();
                        }
                        gameChoice.add(games[i].getName());
                    }
                }
            }

            gameChoice.addItemListener(itemListener);

            // update all stat field labels
            DSGPlayerGameData dsgPlayerGameData = updateData.getPlayerGameData(game,
                    updateData.isComputer());
            playerNameDataLabel.setText(updateData.getName());
            ratingDataLabel.setText(Long.toString(Math.round(dsgPlayerGameData.getRating())));
            winsDataLabel.setText(Integer.toString(dsgPlayerGameData.getWins()));
            lossesDataLabel.setText(Integer.toString(dsgPlayerGameData.getLosses()));
            totalGamesDataLabel.setText(Integer.toString(dsgPlayerGameData.getTotalGames()));
            percentageDataLabel.setText(numberFormat.format(dsgPlayerGameData.getPercentageWins()));
            streakDataLabel.setText(Integer.toString(dsgPlayerGameData.getStreak()));
        }
    }
}
