package org.pente.gameServer.client.awt;

import java.awt.*;

import org.pente.gameServer.client.*;

public class PlayerLeftDialog extends DSGDialog {

    private Label timerLabel;
    private GameTimer timer;

    private Button cancelButton;
    private Button forceResignButton;

    public PlayerLeftDialog(
            Frame frame, GameStyles gameStyle, int timeUpMinutes, boolean middleOfSet,
            boolean firstGameSet) {

        super(frame, "Player Left", false);
        String text = middleOfSet ?
                "You opponent has left in the middle of your set, most likely " +
                        "because of a connection problem, but maybe because they have given " +
                        "in to your superior play!\n\nUnfortunately, the server can't tell " +
                        "the difference, so you get to decide what to do.  You may keep " +
                        "waiting for your opponent, resign the set, cancel the set or force " +
                        "your opponent to resign the set.\n\nPlease play nicely! " +
                        "If you are sure to lose, don't force a resignation!"
                :
                (firstGameSet ?
                        "Your opponent has left the game, most likely because of a network " +
                                "connection problem, but maybe because they have given in to your " +
                                "superior play!\n\nUnfortunately, the server can't tell the " +
                                "difference, so you will have to give your opponent a chance to " +
                                "return to finish the game.  If your opponent does't return within " +
                                timeUpMinutes + " minutes then they probably aren't coming back.  If that " +
                                "happens, the server will allow you to keep waiting longer, " +
                                "cancel the set, or force your opponent to resign the set.  Also, at any time " +
                                "you may resign the set to your opponent.\n\nPlease play nicely! " +
                                "If you are sure to lose, don't force a resignation!"
                        :
                        "Your opponent has left the game, most likely because of a network " +
                                "connection problem, but maybe because they have given in to your " +
                                "superior play!\n\nUnfortunately, the server can't tell the " +
                                "difference, so you will have to give your opponent a chance to " +
                                "return to finish the game.  If your opponent does't return within " +
                                timeUpMinutes + " minutes then they probably aren't coming back.  If that " +
                                "happens, the server will allow you to keep waiting longer, " +
                                "cancel the game, or force your opponent to resign the game.  Also, at any time " +
                                "you may resign the game to your opponent.\n\nPlease play nicely! " +
                                "If you are sure to lose, don't force a resignation!");

        int rows = middleOfSet ? 10 : 13;
        TextArea textArea = new TextArea(text,
                rows, 50, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textArea.setBackground(Color.white);
        textArea.setEditable(false);

        timerLabel = new Label("Time left: " + timeUpMinutes + ":00", Label.CENTER);
        timerLabel.setForeground(gameStyle.foreGround);

        Panel bottomPanel = new Panel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));
        bottomPanel.add(timerLabel);

        Button resignButton = gameStyle.createDSGButton("Resign " + (middleOfSet ? "2nd game" : (firstGameSet ? "Set" : "")));
        resignButton.addActionListener(actionRouter);
        bottomPanel.add(resignButton);

        cancelButton = gameStyle.createDSGButton("Cancel " + (middleOfSet ? "Set" : (firstGameSet ? "Set" : "Game")));
        cancelButton.addActionListener(actionRouter);
        cancelButton.setEnabled(middleOfSet); // disabled until time up
        bottomPanel.add(cancelButton);

        forceResignButton = gameStyle.createDSGButton("Force Resign " + (middleOfSet ? "2nd Game" : (firstGameSet ? "Set" : "")));
        forceResignButton.addActionListener(actionRouter);
        forceResignButton.setEnabled(middleOfSet); // disabled until time up
        bottomPanel.add(forceResignButton);


        setBackground(gameStyle.boardBack);

        InsetPanel panel = new InsetPanel(4, 4, 4, 4);
        panel.setBackground(gameStyle.boardBack);
        panel.setLayout(new BorderLayout(2, 2));
        panel.add(textArea, BorderLayout.NORTH);
        panel.add(bottomPanel, BorderLayout.CENTER);
        add(panel);

        setResizable(false);
        pack();
        centerDialog(frame);

        if (!middleOfSet) {
            timer = new SimpleGameTimer();
            timer.setStartMinutes(timeUpMinutes);
            timer.addGameTimerListener((newMinutes, newSeconds) -> {
                if (newMinutes == 0 && newSeconds == 0) {
                    timer.stop();
                }
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                timerLabel.setText("Time left: " + newMinutes + ":" + newSecondsStr);
            });
            timer.reset();
            timer.go();
        }
    }

    public void timeHasExpired() {
        if (timer != null) {
            timer.stop();
        }
        timerLabel.setText("Time left: 0:00");

        cancelButton.setEnabled(true);
        forceResignButton.setEnabled(true);
    }

    public void dispose() {
        super.dispose();

        if (timer != null) {
            timer.destroy();
        }
    }
}