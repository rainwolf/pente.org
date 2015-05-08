/** ChatPanel.java
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
import java.text.*;

import org.pente.gameServer.client.*;

public class ChatPanel extends Panel implements ChatComponent, ActionListener {

    private static final DateFormat dateFormat = new SimpleDateFormat("(HH:mm)");
    
    private TextArea chatArea;
    private TextField chatEnter;
    private FontMetrics fontMetrics;
    
    private boolean notified = false;
    private boolean destroyed = false;
    
    private PreferenceHandler preferenceHandler;
    
    private Vector listeners = new Vector();

    /**
     * Call it to make a new ChatPanel, then add it to your interface.
     * @param width The width in characters you want the ChatPanel to be.
     * @param height The height in characters you want the ChatPanel to be.
     * @param spacing The spacing between the TextField and TextArea, in pixels.
     * @param PreferenceHandler The preference handler
     */
    public ChatPanel(int width, int height, int spacing, PreferenceHandler preferenceHandler/*, Boolean isGuest */) {

        this.preferenceHandler = preferenceHandler;
        
        chatArea = new TextArea("", height, width, TextArea.SCROLLBARS_VERTICAL_ONLY);
        chatArea.setEditable(false);
        chatArea.setBackground(Color.white);
        chatEnter = new TextField("");
//        if (isGuest) chatEnter.setEditable(false);
        chatEnter.setBackground(Color.white);

        setLayout(new BorderLayout(spacing, spacing));
        add("Center", chatArea);
        add("South", chatEnter);

        chatEnter.addActionListener(this);

        Font dialogFont = new Font("Dialog", Font.PLAIN, 12);
        fontMetrics = chatArea.getFontMetrics(dialogFont);
    }
    
    public void addNotify() {
        super.addNotify();
        notified = true;
    }

    public void addChatListener(ChatListener chatListener) {
        listeners.addElement(chatListener);
    }

    public void removeChatListener(ChatListener chatListener) {
        listeners.removeElement(chatListener);
    }

    public void clear() {

        synchronized (chatArea) {
            if (destroyed) return;
            chatArea.setText("");
        }
    }

    public void newChatMessage(String message) {
        synchronized (chatArea) {
            if (destroyed) return;
            chatArea.append(message + "\n");
            //if (notified) {
                // hack to scroll chatarea down, seems to not work by
                // default on mac OS X's
            //    chatArea.setCaretPosition(Integer.MAX_VALUE);
            //}
        }
    }
    
    public void newChatMessage(String message, String player) {
        newChatMessage(getMessage(message, player, false));
    }

    public void newSystemMessage(String message) {
        newChatMessage(getMessage(message, null, true));
    }

    private String getMessage(String message, String player, boolean system) {
        
        // might be bad for performance to get timestamp preference
        // for every text message but easier this way
        boolean showTimestamp = false;
        Boolean showTimestampPref = (Boolean) preferenceHandler.getPref(
            "chatTimestamp");
        if (showTimestampPref != null) {
            showTimestamp = showTimestampPref.booleanValue();
        }
        
        String msg = "";
        if (player != null) {
            msg += player;
        }
        if (showTimestamp) {
            msg += " " + dateFormat.format(new Date());
        }

        if ((player != null || showTimestamp) && !system) {
            msg += ": ";
        }
        else if (system) {
            if (showTimestamp) {
                msg += " * ";
            }
            else {
                msg += "* ";
            }
        }

        msg += message;
        if (system) {
            msg += " *";
        }
        
        return msg;
    }
    
    public void actionPerformed(ActionEvent e) {

        synchronized (chatArea) {
            if (destroyed) return;
        }

        String text = chatEnter.getText();
        if (text.equals("")) {
            return;
        }

        chatEnter.setText("");

        for (int i = 0; i < listeners.size(); i++) {
            ChatListener chatListener = (ChatListener) listeners.elementAt(i);
            chatListener.chatEntered(text);
        }
    }
    public void destroy() {
        synchronized (chatArea) {
            destroyed = true;
        }
    }
}
