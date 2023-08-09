package org.pente.gameDatabase.swing;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.JButton;

import org.pente.gameServer.client.*;

public class SwingDSGButton implements DSGButton {

    private JButton button;

    public SwingDSGButton() {
    }

    public SwingDSGButton(String text, GameStyles gameStyles) {

        button = new JButton(text);
        button.setBackground(gameStyles.boardBack);
        button.setForeground(gameStyles.buttonFore);
        button.setFont(new Font("Dialog", Font.PLAIN, 10));
    }

    public DSGButton createButton(String text, GameStyles gameStyles) {
        return new SwingDSGButton(text, gameStyles);
    }

    public void addActionListener(ActionListener listener) {
        button.addActionListener(listener);
    }

    public Component getButton() {
        return button;
    }

}
