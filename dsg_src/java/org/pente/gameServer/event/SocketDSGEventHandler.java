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
import java.util.*;

import org.pente.gameServer.core.*;

public class SocketDSGEventHandler implements DSGEventListener, DSGEventSource {

	Socket socket;
	ObjectInputStream in;
	ObjectOutputStream out;
	
	Thread readObjectThread;
    Thread writeObjectThread;
	volatile boolean running;

	Vector listeners = new Vector();;
    SynchronizedQueue outputQueue = new SynchronizedQueue();

    class ObjectReader implements Runnable {
		public void run() {
			Throwable t = null;
			try {
				while (running) {
					
					Object obj = in.readObject();
					if (obj == null) {
						handleError(null);
                        return;
					}
					else if (!(obj instanceof DSGEvent)) {
						handleError(null);
                        return;
					}
					else {
						notifyListeners((DSGEvent) obj);
					}
				}
			// on any throwable stop the thread
			} catch (Throwable th) {
				t = th;
			}

			handleError(t);
		}
    };

    class ObjectWriter implements Runnable {
        public void run() {
            Throwable t = null;
            try {
                while (running) {
                    
                    if (socket == null || out == null) {
                        throw new IOException("Socket or outputstream is null.");
                    }

                    Object o = outputQueue.remove();

                    if (!running) break;
                    
                    out.writeObject(o);
                    out.reset();
                }
            // on any throwable stop the thread
            } catch (Throwable th) {
                t = th;
            }

            handleError(t);
        }
    }

    /** Assumes that subclasses have setup all necessary objects before
     *  calling this
     */
    public synchronized void go() {

        running = true;
        readObjectThread = new Thread(
            new ObjectReader(), "SocketDSGEventHandler [reader]");
        readObjectThread.start();
        
        writeObjectThread = new Thread(
            new ObjectWriter(), "SocketDSGEventHandler [writer]");
        writeObjectThread.start();
    }

	public void destroy() {

        // only destroy once
        if (!running) return;
        
		running = false;
		if (readObjectThread != null) {
			readObjectThread.interrupt();
            readObjectThread = null;
		}
        if (writeObjectThread != null) {
            writeObjectThread.interrupt();
            writeObjectThread = null;
        }
        
		// close socket here because interrupting read object thread
		// doesn't do anything as long as its trying to read in an object
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
			} finally {
				socket = null;
                in = null;
                out = null;
			}
		}
	}

	void handleError(Throwable t) {
		destroy();
		notifyListeners(new DSGExitMainRoomEvent());
	}

	public void addListener(DSGEventListener dsgEventListener) {
		listeners.addElement(dsgEventListener);
	}
	public void removeListener(DSGEventListener dsgEventListener) {
		listeners.removeElement(dsgEventListener);
	}

	public void notifyListeners(DSGEvent dsgEvent) {
		for (int i = 0; i < listeners.size(); i++) {
			((DSGEventListener) listeners.elementAt(i)).eventOccurred(dsgEvent);
		}
	}

    public synchronized void eventOccurred(DSGEvent dsgEvent) {
    	outputQueue.add(dsgEvent);
    }
}