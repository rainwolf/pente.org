package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.*;

public class MainRoomOptionsDialog extends Dialog {

    private Boolean playJoinSoundPref;
    private Checkbox playJoinSoundCheck;
    private Boolean playInviteSoundPref;
    private Checkbox playInviteSoundCheck;
    private Boolean showTimestampsPref;
    private Checkbox showTimestampsCheck;
    private Boolean showPlayerJoinExitPref;
    private Checkbox showPlayerJoinExitCheck;

    private PreferenceHandler preferenceHandler;

    private Frame parent;

    public MainRoomOptionsDialog(
            Frame parent, GameStyles gameStyle,
            final PreferenceHandler preferenceHandler) {

        super(parent, "Options", false);

        this.parent = parent;

        // get state from stored preference if available
        playJoinSoundPref = (Boolean) preferenceHandler.getPref("playJoinSound");
        if (playJoinSoundPref == null) {
            playJoinSoundPref = Boolean.valueOf(true);
        }
        playInviteSoundPref = (Boolean) preferenceHandler.getPref("playInviteSound");
        if (playInviteSoundPref == null) {
            playInviteSoundPref = Boolean.valueOf(true);
        }
        showTimestampsPref = (Boolean) preferenceHandler.getPref("chatTimestamp");
        if (showTimestampsPref == null) {
            showTimestampsPref = Boolean.valueOf(false);
        }
        showPlayerJoinExitPref = (Boolean) preferenceHandler.getPref("showPlayerJoinExit");
        if (showPlayerJoinExitPref == null) {
            showPlayerJoinExitPref = Boolean.valueOf(true);
        }

        // create components
        playJoinSoundCheck = new Checkbox(
                "Play sound when players join", playJoinSoundPref.booleanValue());
        playJoinSoundCheck.setBackground(gameStyle.boardBack);
        playJoinSoundCheck.setForeground(gameStyle.foreGround);

        playInviteSoundCheck = new Checkbox(
                "Play sound when receive invitation", playInviteSoundPref.booleanValue());
        playInviteSoundCheck.setBackground(gameStyle.boardBack);
        playInviteSoundCheck.setForeground(gameStyle.foreGround);

        showTimestampsCheck = new Checkbox(
                "Show timestamps on chat messages", showTimestampsPref.booleanValue());
        showTimestampsCheck.setBackground(gameStyle.boardBack);
        showTimestampsCheck.setForeground(gameStyle.foreGround);

        showPlayerJoinExitCheck = new Checkbox(
                "Show player join/exit messages", showPlayerJoinExitPref.booleanValue());
        showPlayerJoinExitCheck.setBackground(gameStyle.boardBack);
        showPlayerJoinExitCheck.setForeground(gameStyle.foreGround);

        Button okButton = gameStyle.createDSGButton("Ok");
        okButton.addActionListener(e -> {
            // save state to server and close window
            if (playJoinSoundCheck.getState() != playJoinSoundPref.booleanValue()) {
                preferenceHandler.storePref("playJoinSound",
                        Boolean.valueOf(playJoinSoundCheck.getState()));
            }
            if (playInviteSoundCheck.getState() != playInviteSoundPref.booleanValue()) {
                preferenceHandler.storePref("playInviteSound",
                        Boolean.valueOf(playInviteSoundCheck.getState()));
            }
            if (showTimestampsCheck.getState() != showTimestampsPref.booleanValue()) {
                preferenceHandler.storePref("chatTimestamp",
                        Boolean.valueOf(showTimestampsCheck.getState()));
            }
            if (showPlayerJoinExitCheck.getState() != showPlayerJoinExitPref.booleanValue()) {
                preferenceHandler.storePref("showPlayerJoinExit",
                        Boolean.valueOf(showPlayerJoinExitCheck.getState()));
            }
            dispose();
        });

        Button cancelButton = gameStyle.createDSGButton("Cancel");
        cancelButton.addActionListener(e -> dispose());


        setLayout(new BorderLayout());
        setBackground(gameStyle.boardBack);

        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setLayout(new GridLayout(5, 1));
        panel.setBackground(gameStyle.boardBack);

        panel.add(playJoinSoundCheck);
        panel.add(playInviteSoundCheck);
        panel.add(showTimestampsCheck);
        panel.add(showPlayerJoinExitCheck);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 1, 1));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel);

        add("Center", panel);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        setResizable(false);
        pack();
        centerDialog();
    }


    public void centerDialog() {

        Point location = new Point();
        location.x = parent.getLocation().x +
                parent.getSize().width / 2 -
                getSize().width / 2;
        location.y = parent.getLocation().y +
                parent.getSize().height / 2 -
                getSize().height / 2;
        setLocation(location);
    }
}
