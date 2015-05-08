package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import org.pente.gameServer.client.*;

public class MiddleSetDialog extends DSGDialog {


    private Label timerLabel;
    private GameTimer timer;

	private Checkbox ignoreCheck;
	
    
    public MiddleSetDialog(
        Frame frame, GameStyles gameStyle) {
        
        super(frame, "Game 1 of 2 Completed", false);
        
      	TextArea textArea = new TextArea(
      		"You are playing a rated set which means you must complete 2 games " +
      		"against the same player.  The server has switched your seats so " +
      		"that you play one game as player 1 and one game as player 2.\n\n" +
      		"Click Play to begin the second game when you are ready.\n\n" +
      		"Once your opponent clicks play you'll have 7 minutes " +
      		"to start the game.",
        9, 50, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textArea.setBackground(Color.white);
        textArea.setEditable(false);
        
        timerLabel = new Label("Time left: 7:00", Label.CENTER);
        timerLabel.setForeground(gameStyle.foreGround);
        
        Button playButton = gameStyle.createDSGButton("Play");
        playButton.addActionListener(actionRouter);

		ignoreCheck = new Checkbox("Don't show me this again");
		ignoreCheck.setBackground(gameStyle.boardBack);
		ignoreCheck.setForeground(gameStyle.foreGround);
		ignoreCheck.setState(false);

        setBackground(gameStyle.boardBack);
        
        InsetPanel panel = new InsetPanel(4, 4, 4, 4);
        panel.setLayout(new GridBagLayout());
        panel.setBackground(gameStyle.boardBack);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
		gbc.gridheight = 1;
		gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(textArea, gbc);
        
        gbc.gridy++;
        gbc.gridwidth = 1;
        panel.add(timerLabel, gbc);

        gbc.gridx++;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(playButton, gbc);
        
        gbc.gridx++;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(ignoreCheck, gbc);
        
        add(panel);

        
        setResizable(false);
        pack();
        centerDialog(frame);
    }

    public void startTimer(int minutes, int seconds) {
    	timer = new SimpleGameTimer();
    	timer.setStartMinutes(minutes);
    	timer.setStartSeconds(seconds);
        timer.addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int newMinutes, int newSeconds) {
                if (newMinutes == 0 && newSeconds == 0) {
                    timer.stop();
                }
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                timerLabel.setText("Time left: " + newMinutes + ":" + newSecondsStr);
            }
        });
    	timer.reset();
    	timer.go();
    }
    
    public boolean getIgnore() {
    	return ignoreCheck.getState();
    }
    
    public void dispose() {
        super.dispose();
        
        if (timer != null) {
        	timer.stop();
            timer.destroy();
        }
    }
}