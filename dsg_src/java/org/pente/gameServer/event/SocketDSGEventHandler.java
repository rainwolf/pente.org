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

import java.awt.*;
import java.net.*;
import java.io.*;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pente.gameServer.core.*;

public class SocketDSGEventHandler implements DSGEventListener, DSGEventSource {

    Socket socket;
    ObjectInputStream in;
    ObjectOutputStream out;

    // DataInputStream inStream;
    // DataOutputStream outStream;
    BufferedInputStream inStream;
    BufferedOutputStream outStream;

    Thread readObjectThread;
    Thread writeObjectThread;
    volatile boolean running;

    Vector listeners = new Vector();
    SynchronizedQueue outputQueue = new SynchronizedQueue();

    class ObjectReader implements Runnable {
        public void run() {
            Throwable t = null;
            try {
                DSGEventWrapper wrappedEvent = null;
                int b = -1;
                while (running) {
//                    System.out.println("kittycat reading loop ");
                    wrappedEvent = null;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    while ((b = inStream.read()) > -1) {
                        // System.out.println("kittycat: " + b);
                        if (b != 255) {
                            baos.write(b);
                        } else {
                            byte result[] = baos.toByteArray();
                            String jsonStr = new String(result, "UTF-8");
//                            System.out.println("ObjectReader: " + jsonStr);
                            GsonBuilder gsonBuilder = new GsonBuilder();
                            gsonBuilder.setPrettyPrinting();
                            gsonBuilder.registerTypeAdapter(Color.class, new DSGColorAdapter());
                            gsonBuilder.registerTypeAdapter(DSGPlayerData.class, new DSGPlayerDataAdapter());
                            gsonBuilder.registerTypeAdapter(DSGPlayerGameData.class, new DSGPlayerGameDataAdapter());
                            Gson gson = gsonBuilder.create();
                            wrappedEvent = gson.fromJson(jsonStr, DSGEventWrapper.class);
                            baos.reset();
                            break;
                        }
                    }

                    // Object obj = in.readObject();
                    Object obj = null;
                    if (wrappedEvent != null) {
                        obj = wrappedEvent.getEncodedEvent();
                    }
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

//                    System.out.println("kittycat writing loop ");
                    if (socket == null || outStream == null) {
                        throw new IOException("Socket or outputstream is null.");
                    }

                    Object o = outputQueue.remove();

                    if (!running) break;

                    DSGEventWrapper wrappedEvent = new DSGEventWrapper(o);

                    String jsonStr = wrappedEvent.getJSON();
//                    System.out.println("ObjectWriter: " + jsonStr);

                    byte[] bytes = jsonStr.getBytes("UTF-8");
                    // byte[] arr = {10, 20, 30, 40, 50};
                    // outStream.write(arr, 0, 5);
                    // outStream.flush();
                    outStream.write(bytes, 0, bytes.length);
                    outStream.write(255);
                    outStream.flush();
                    // out.writeObject(o);
                    // out.reset();
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