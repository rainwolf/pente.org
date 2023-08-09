package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.client.GameStyles;

public class DSGDialog extends Dialog {

    public static final String CLOSE_ACTION = "close";

    private Vector listeners;

    ActionRouter actionRouter = new ActionRouter();

    public DSGDialog(Frame frame, String name, boolean closable) {
        super(frame, name, false);

        listeners = new Vector();

        if (closable) {
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    actionRouter.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, CLOSE_ACTION));
                }
            });
        }
    }

    public DSGDialog(Frame frame,
                     String name,
                     GameStyles gameStyle,
                     String label1Str, String label2Str,
                     String button1Str, String button2Str,
                     boolean closable) {

        super(frame, name, false);

        listeners = new Vector();

        int gridy = label2Str == null ? 2 : 3;
        setLayout(new GridLayout(gridy, 1, 0, 0));

        Label label1 = new Label(label1Str, Label.CENTER);
        label1.setForeground(gameStyle.foreGround);
        add(label1);

        if (label2Str != null) {
            Label label2 = new Label(label2Str, Label.CENTER);
            label2.setForeground(gameStyle.foreGround);
            add(label2);
        }

        Panel buttonPanel = new Panel();
        Button button1 = gameStyle.createDSGButton(button1Str);
        button1.addActionListener(actionRouter);
        buttonPanel.add(button1);

        if (button2Str != null) {
            Button button2 = gameStyle.createDSGButton(button2Str);
            button2.addActionListener(actionRouter);
            buttonPanel.add(button2);
        }

        add(buttonPanel);

        if (closable) {
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    actionRouter.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, CLOSE_ACTION));
                }
            });
        }

        setBackground(gameStyle.boardBack);
        setResizable(false);
        pack();
        centerDialog(frame);
    }

    public void addActionListener(ActionListener actionListener) {
        listeners.addElement(actionListener);
    }

    public void removeActionListener(ActionListener actionListener) {
        listeners.remove(actionListener);
    }

    private class ActionRouter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            for (int i = 0; i < listeners.size(); i++) {
                ((ActionListener) listeners.elementAt(i)).actionPerformed(e);
            }
            dispose();
        }
    }

    void centerDialog(Frame frame) {

        Point location = new Point();
        location.x = frame.getLocation().x +
                (frame.getSize().width + frame.getInsets().right - frame.getInsets().left) / 2 -
                getSize().width / 2;
        location.y = frame.getLocation().y +
                (frame.getSize().height + frame.getInsets().top - frame.getInsets().bottom) / 2 -
                (getSize().height + getInsets().top - getInsets().bottom) / 2;
        setLocation(location);
    }

    /**
     * Overridden to request the focus when becoming visible
     * Hopefully this will prevent users from closing the window without seeing
     * it because they were typing
     */
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            requestFocus();
        }
    }
}