package org.pente.gameServer.client.awt;

import java.awt.*;

public class ConnectPanel extends Panel {
    
    private Label messageLabel;
    public ConnectPanel(Color background, Color foreground) {
        
        Panel centerPanel = new Panel();
        messageLabel = new Label("");
        centerPanel.add(messageLabel);
        
        setBackground(background);
        setForeground(foreground);
        
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;

        add(messageLabel, gbc);
    }
    
    public void printMessage(String message) {
        messageLabel.setText(message);
        validate();
    }
}