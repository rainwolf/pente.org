/** LoginPanel.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;

/**
 * Provides a simple login panel for a server.
 */
public class LoginPanel extends Panel implements LoginComponent {

    private static final String LOGIN =             "Login";

    private String              loginMsg;
    private TextArea            loginText;
    private TextField           loginName;
    private TextField           loginPassword;
    private Choice              loginRoom;
    
    private Vector              listeners = new Vector();

    /** Constructor.
     *  @param gameStyle A GameStyle object to setup the appropriate style for the panel.
     */
    public LoginPanel(final Vector activeServers, GameStyles gameStyle) {

        loginMsg = new String("Pente.org Login\n\n" +
                              "Enter your user name and password to login.\n");

        loginText = new TextArea(loginMsg, 5, 35, TextArea.SCROLLBARS_NONE);
        loginText.setBackground(Color.white);
        loginText.setEditable(false);

        Label loginNameLabel = new Label("Name");
        loginNameLabel.setForeground(gameStyle.foreGround);
        loginName = new TextField("", 10);
        loginName.setBackground(Color.white);

        Label loginPasswordLabel = new Label("Password");
        loginPasswordLabel.setForeground(gameStyle.foreGround);
        loginPassword = new TextField("", 10);
        loginPassword.setBackground(Color.white);
        loginPassword.setEchoChar('*');

        Button loginButton = gameStyle.createDSGButton(LOGIN);

        if (activeServers.size() > 1) {
            loginRoom = new Choice();
            loginRoom.setBackground(Color.white);
            for (int i = 0; i < activeServers.size(); i++) {
                ServerData d = (ServerData) activeServers.elementAt(i);
                loginRoom.addItem(d.getName());
            }
        }
        Label loginRoomLabel = new Label("Game Room");
        loginRoomLabel.setForeground(gameStyle.foreGround);
        
        class LoginActionListener implements ActionListener {

            public void actionPerformed(ActionEvent e) {

                String name = loginName.getText().toLowerCase();
                String password = loginPassword.getText();
                int port = 0;
                if (activeServers.size() == 1) {
                    port = ((ServerData) activeServers.elementAt(0)).getPort();
                }
                else {
                    port = ((ServerData) activeServers.elementAt(loginRoom.getSelectedIndex())).getPort();
                }
                
                for (int i = 0; i < listeners.size(); i++) {
                    LoginListener loginListener = (LoginListener) listeners.elementAt(i);
                    loginListener.login(name, password, port);
                }
            }
        }

        ActionListener loginActionListener = new LoginActionListener();
        loginPassword.addActionListener(loginActionListener);
        loginButton.addActionListener(loginActionListener);



        Panel controlsPanel = new Panel();
        controlsPanel.setBackground(gameStyle.boardBack);
        controlsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 3, 3, 3);

        int gridy = 1;
        gbc.gridx = 1;
        gbc.gridy = gridy;
        gbc.gridwidth = 2;
        gbc.gridheight = 6;
        gbc.fill = GridBagConstraints.BOTH;
        controlsPanel.add(loginText, gbc);

        gbc.gridx = 3;
        gbc.gridy = gridy;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        controlsPanel.add(loginNameLabel, gbc);

        gbc.gridx = 4;
        gbc.gridy = gridy;
        controlsPanel.add(loginName, gbc);

        gbc.gridx = 3;
        gbc.gridy = ++gridy;
        controlsPanel.add(loginPasswordLabel, gbc);

        gbc.gridx = 4;
        gbc.gridy = gridy;
        controlsPanel.add(loginPassword, gbc);

        if (activeServers.size() > 1) {
            gbc.gridx = 3;
            gbc.gridy = ++gridy;
            controlsPanel.add(loginRoomLabel, gbc);
    
            gbc.gridx = 4;
            gbc.gridy = gridy;
            controlsPanel.add(loginRoom, gbc);
        }

        gbc.gridy = ++gridy;
        controlsPanel.add(loginButton, gbc);

        gbc.gridy = ++gridy;
        controlsPanel.add(new Panel(), gbc);

        InsetPanel innerPanel = new InsetPanel(5, 5, 5, 5, 2);
        innerPanel.setBackground(gameStyle.boardBack);
        innerPanel.add(controlsPanel);

        add(innerPanel);
    }

    public void addLoginListener(LoginListener loginListener) {
        listeners.addElement(loginListener);
    }

    public void removeLoginListener(LoginListener loginListener) {
        listeners.removeElement(loginListener);
    }


    public void showAlreadyLoggedIn() {

        loginName.setText("");
        loginPassword.setText("");
        loginText.setText(loginMsg + "\n" +
                          "You are already logged in\n"+
                          "If you got disconnected, try again in a minute.");
    }

    public void showInvalidLogin() {

        loginText.setText(loginMsg + "\n" +
                          "User name or Password incorrect, try again.");
    }

    public void showValidLogin() {

        loginName.setText("");
        loginPassword.setText("");
        loginText.setText(loginMsg);
    }
    
    public void showPrivateRoom() {
    	
    	loginName.setText("");
        loginPassword.setText("");
        
        loginText.setText(loginMsg + "\n" +
                "This is a private room and you do not have access.");
    }
}
