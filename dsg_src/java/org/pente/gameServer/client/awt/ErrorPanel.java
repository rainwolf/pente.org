package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;

/**
 * A generic error panel that is shown when the connection is lost to the
 * game server.
 */
public class ErrorPanel extends Panel implements ActionListener {
    
    private PenteApplet parent;
    
    private Label errorLabel, errorLabel2;
    
    public ErrorPanel(PenteApplet parent, GameStyles gameStyle) {

        this.parent = parent;
        
        setBackground(gameStyle.boardBack);
        setForeground(gameStyle.foreGround);

        errorLabel=new Label();
        errorLabel2=new Label();
        setConnectionError();
        errorLabel.setForeground(gameStyle.foreGround);
        errorLabel2.setForeground(gameStyle.foreGround);

        Button reconnectButton = gameStyle.createDSGButton("Reconnect");
        reconnectButton.addActionListener(this);
        
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        add(errorLabel, gbc);
        
        gbc.gridy++;
        add(errorLabel2, gbc);
        
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.CENTER;
        add(reconnectButton, gbc);
    }
    public void actionPerformed(ActionEvent e) {
        parent.reconnect();
    }
    
    public void setConnectionError() {
        errorLabel.setText("There has been an error with your " +
            "connection to Pente.org.");
        errorLabel2.setText("Check your internet connection and then " +
            "try to reconnect.");
        
    	validate();
    }
    public void setBooted() {
    	errorLabel.setText("You have been booted by an admin from the server, you can " +
    		"try to connect again later.");
    	errorLabel2.setText("");
    	
    	validate();
    }
}
