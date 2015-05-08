package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.core.AIData;

public class AddAIDialog extends Dialog {

    private Choice aiChoice;
    private Choice levelChoice;
    private Choice seatChoice;

    private Vector aiData;
    private int game;

    public AddAIDialog(
        Frame frame,
        GameStyles gameStyle,
        final ActionListener addAIListener,
        int game,
        Vector aiData) {

        super(frame, "Play Computer", false);
 
        this.aiData = aiData;
        this.game = game;
        
        Label nameLabel = new Label("Opponent");
        nameLabel.setForeground(gameStyle.foreGround);
        
        aiChoice = new Choice();
        aiChoice.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                updateLevels();
            }
        });
        updateNames();
        
        Label levelLabel = new Label("Level");
        levelLabel.setForeground(gameStyle.foreGround);
        levelChoice = new Choice();
        updateLevels();
        
        Label seatLabel = new Label("Seat");
        seatLabel.setForeground(gameStyle.foreGround);
        seatChoice = new Choice();
        seatChoice.add("1");
        seatChoice.add("2");


        Panel computerOptionsPanel = new Panel();
        computerOptionsPanel.setLayout(new GridLayout(3, 2, 5, 5));        
        computerOptionsPanel.setBackground(gameStyle.boardBack);
        computerOptionsPanel.add(nameLabel);
        computerOptionsPanel.add(aiChoice);
        computerOptionsPanel.add(levelLabel);
        computerOptionsPanel.add(levelChoice);
        computerOptionsPanel.add(seatLabel);
        computerOptionsPanel.add(seatChoice);

        Button inviteButton = gameStyle.createDSGButton("Play");
        inviteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addAIListener.actionPerformed(e);
                dispose();
            }
        });
        
        Button cancelButton = gameStyle.createDSGButton("Cancel");
        ActionListener disposer = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        };
        cancelButton.addActionListener(disposer);


        InsetPanel panel = new InsetPanel(3, 3, 3, 3);
        panel.setLayout(new BorderLayout());
        panel.setBackground(gameStyle.boardBack);
        panel.setLayout(new GridBagLayout());
		
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
		
        gbc.gridy = 1;
		gbc.gridx = 1;
		gbc.gridwidth = 2;
		//gbc.weightx = 1;
		//gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(computerOptionsPanel, gbc);
        
        gbc.gridy = 2;
		gbc.gridx = 1;
		gbc.gridwidth = 1;
		gbc.weightx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(inviteButton, gbc);

        gbc.gridy = 2;
        gbc.gridx = 2;
		gbc.weightx = 1;
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

    private void updateNames() {
        aiChoice.removeAll();
        for (int i = 0; i < aiData.size(); i++) {
            AIData d = (AIData) aiData.elementAt(i);
            if (d.isValidForGame(game)) {
                aiChoice.add(d.getName());
            }
        }
    }

    private void updateLevels() {
       levelChoice.removeAll();
       
        for (int i = 0; i < aiData.size(); i++) {
            AIData d = (AIData) aiData.elementAt(i);
            if (d.getName().equals(aiChoice.getSelectedItem())) {
                for (int j = 1; j <= d.getNumLevels(); j++) {
                    levelChoice.add(Integer.toString(j));
                }
            }
        }
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

    public AIData getData() {
        
        AIData data = (AIData) getAIData(aiChoice.getSelectedItem()).clone();

        data.setLevel(levelChoice.getSelectedIndex() + 1);
        data.setSeat(seatChoice.getSelectedIndex() + 1);
        data.setGame(game);
        
        return data;
    }
    
    public AIData getAIData(String name) {
        for (int i = 0; i < aiData.size(); i++) {
            AIData data = (AIData) aiData.elementAt(i);
            if (data.getName().equals(name)) {
                return data;
            }
        }
        return null;
    }
}
