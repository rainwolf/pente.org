/** SocketDSGEventHandler.java
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

public class ClientSocketDSGEventHandler extends SocketDSGEventHandler {

	private boolean handledError = false;
	public ClientSocketDSGEventHandler(Socket s) {
		this.socket = s;

		try {
			// out = new ObjectOutputStream(socket.getOutputStream());
			// in = new ObjectInputStream(socket.getInputStream());
			outStream = new BufferedOutputStream(socket.getOutputStream());
			inStream = new BufferedInputStream(socket.getInputStream());
			// outStream.flush();
			// outStream = new DataOutputStream(socket.getOutputStream());
			// outStream.flush();
			// inStream = new DataInputStream(socket.getInputStream());
		} catch (Throwable t) {

			System.err.println("Error creating socket object streams");
			t.printStackTrace();
			// this kills the connection before it gets created
			return;
		}

		super.go();
	}


	void handleError(Throwable t) {
		// provent duplicated handling
		synchronized (this) {
			if (handledError) return;
			handledError = true;
		}
		System.err.println("SocketHandler Unhandled exception, disconnecting.");
		t.printStackTrace();
		//if (!(t instanceof IOException)) {
		//    eventOccurred(new DSGClientErrorEvent(t));
		//}
		super.handleError(t);
	}
}