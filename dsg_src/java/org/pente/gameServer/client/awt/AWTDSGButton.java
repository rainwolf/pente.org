package org.pente.gameServer.client.awt;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.Button;

import org.pente.gameServer.client.*;

public class AWTDSGButton implements DSGButton {

    private Button button;

    public AWTDSGButton() {
    }

    public AWTDSGButton(String text, GameStyles gameStyles) {

        button = new Button(text);
        button.setBackground(gameStyles.buttonBack);
        button.setForeground(gameStyles.buttonFore);
    }

    public DSGButton createButton(String text, GameStyles gameStyles) {
        return new AWTDSGButton(text, gameStyles);
    }

    public void addActionListener(ActionListener listener) {
        button.addActionListener(listener);
    }

    public Component getButton() {
        return button;
    }

}
