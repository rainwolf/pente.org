package org.pente.gameServer.server.test;

import java.net.*;
import java.io.*;

import org.pente.gameServer.event.*;

public class LoginLoadTest {

    private static final String[] PLAYERS = new String[]
            {"dweebo", "peter", "hewitt", "speed_dw"};

    public static void main(final String[] args) {

        // create 4 player threads
        for (int i = 0; i < PLAYERS.length; i++) {
            final int ii = i;
            Thread t = new Thread(() -> {
                Socket socket = null;
                SocketDSGEventHandler handler = null;
                // login 100 times for each player
                for (int j = 0; j < 20; j++) {
                    try {
                        socket = new Socket(args[0], Integer.parseInt(args[1]));
                        handler = new ClientSocketDSGEventHandler(socket);
                        System.out.println("Thread: " + ii + " - logging in for " + PLAYERS[ii] + " attempt " + j);
                        handler.eventOccurred(new DSGLoginEvent(PLAYERS[ii], args[2], new ClientInfo()));

                        // sleep 2 seconds to receive login events
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException i1) {
                        }

                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));
                        // sleep 2 seconds to receive login events
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException i1) {
                        }
                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));
                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException i1) {
                        }
                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));
                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException i1) {
                        }
                        handler.eventOccurred(new DSGJoinTableEvent(null, DSGJoinTableEvent.CREATE_NEW_TABLE));

                        handler.eventOccurred(new DSGJoinTableEvent(null, ii));
                        handler.eventOccurred(new DSGJoinTableEvent(null, (ii * 2 + 3) % 24));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException i1) {
                        }
                        handler.eventOccurred(new DSGSitTableEvent(null, (ii * 2 + 3) % 24, 1));
                        handler.eventOccurred(new DSGSitTableEvent(null, ii, 22));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException i1) {
                        }
                        handler.eventOccurred(new DSGPlayTableEvent(null, ii));
                        handler.eventOccurred(new DSGPlayTableEvent(null, (ii * 2 + 3) % 24));

                        handler.eventOccurred(new DSGTextTableEvent(null, ii, "test"));
                        handler.eventOccurred(new DSGTextMainRoomEvent(null, "test"));

                        // sleep few seconds to receive events
                        try {
                            Thread.sleep(2000 * (int) Math.random());
                        } catch (InterruptedException i1) {
                        }

                    } catch (Throwable t1) {
                        t1.printStackTrace();
                    }

                    // cleanup player thread to simulate logout
                    if (handler != null) {
                        handler.destroy();
                        handler = null;
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                        }
                        socket = null;
                    }
                }
            });
            t.start();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }
    }
}