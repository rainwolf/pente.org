/** ServerSocketDSGEventHandler.java
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

package org.pente.gameServer.event;

import java.net.*;
import java.io.*;

import org.apache.log4j.*;

/** Overrides SocketDSGEventHandler just to log stuff when errors occur */
public class ServerSocketDSGEventHandler extends SocketDSGEventHandler {

    private static Category log4j = Category.getInstance(
        ServerSocketDSGEventHandler.class.getName());

    private String playerName;
    private boolean handledError;
    
	public ServerSocketDSGEventHandler(Socket s) {

		this.socket = s;
        this.handledError = false;
		
		try {
			in = new ObjectInputStream(socket.getInputStream());
			out = new ObjectOutputStream(socket.getOutputStream());
		} catch (Throwable t) {
			log4j.error("Error creating socket object streams", t);
			// this kills the connection before it gets created
			return;
		}
		
        super.go();
	}

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

	void handleError(Throwable t) {

        if (handledError) {
            log4j.info(playerName + " - handleError - already handled, returning.");
            return;
        }
        handledError = true;
        
		if (t == null) {
            log4j.info(playerName + " - handleError obj == null || obj != DSGEvent");
		}
		else {
            log4j.info(playerName + " - handleError", t);
		}

		super.handleError(t);
	}
    
    public String getHostAddress() {
        return socket.getInetAddress().getHostAddress();
    }
    
    public synchronized void eventOccurred(DSGEvent dsgEvent) {
    	dsgEvent.setCurrentTime();
    	super.eventOccurred(dsgEvent);
    }
}