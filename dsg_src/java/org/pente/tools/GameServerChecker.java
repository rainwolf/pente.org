package org.pente.tools;

import java.net.*;
import java.io.*;

import org.pente.gameServer.event.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class GameServerChecker implements DSGEventListener {
    
    /** Checks status of game server.  Doesn't send errors
     *  more than 5 times.  Need tomcat to reset status file
     *  on startup.
     * 
     *  TODO, add checks for ALL game servers?
     */
    public static void main(String args[]) throws Throwable {
        
        BasicConfigurator.configure();
        
        int port = Integer.parseInt(args[0]);
        String mailHost = args[1];
        String mailUser = args[2];
        String mailPassword = args[3];
        File statusFile = new File(args[4]);
        // optional host
        String host = "localhost";
        if (args.length == 6) {
            host = args[5];
        }
        
        // don't send out more than 5 messages
        int failures = 0;
        if (statusFile.exists()) {
            FileInputStream in = new FileInputStream(statusFile);
            failures = in.read();
            in.close();
            if (failures >= 5) return;
        }
        
        GameServerChecker checker = new GameServerChecker(host, port);
        if (!checker.isGameServerUp()) {
            try {
                System.setProperty("mail.smtp.host", mailHost);
                System.setProperty("mail.smtp.user", mailUser);
                System.setProperty("mail.smtp.password", mailPassword);
                System.setProperty("mail.smtp.auth", "true");

                SendMail2.sendMail("DSG", "dweebo@pente.org",
                    "DSG Admins", "admins@pente.org", "DSG May Be Down", "", false, null);

                failures++;
                
            } catch (Throwable t) {
                t.printStackTrace();
                failures--; // try again if failed to send email
            }
        }
        // reset failures when successful
        else {
            failures = 0;
        }
        
        // write out failure count
        FileOutputStream out = new FileOutputStream(statusFile);
        out.write(failures);
    }
    
    private String host;
    private int port;
    private boolean valid = false;
    private boolean finished = false;
    private Socket socket = null;
    private SocketDSGEventHandler eventHandler = null;
    private Object lock;
    
    public GameServerChecker(String host, int port) {
        this.host = host;
        this.port = port;
        lock = new Object();
    }
    
    /** Determine if the game server is up.
     *  This blocks until game server response is returned.
     */
    public boolean isGameServerUp() {

        try {
            setupEventHandler();
            eventHandler.eventOccurred(new DSGLoginEvent("stat", "stat", null));
                
        } catch (Throwable t) {
            return false;
        }
        
        while (!finished) {
            try {
                synchronized (lock) {
                    lock.wait(10 * 1000);
                    // if can't login in 10 seconds, give up
                    finished = true;
                }
            } catch (InterruptedException i) {
            }
        }
        
        destroyEventHandler();
        
        return valid;
    }

    private void setupEventHandler() throws Throwable {
        socket = new Socket(host, port);
        eventHandler = new ClientSocketDSGEventHandler(socket);
        eventHandler.addListener(this);
    }
    private void destroyEventHandler() {
        if (eventHandler != null) {
            eventHandler.removeListener(this);
            eventHandler.destroy();
        }
        if (socket != null) {
            try { socket.close(); } catch (IOException e) {}
        }
    }
    
    public void eventOccurred(DSGEvent dsgEvent) {
        
        // login ok
        if (dsgEvent instanceof DSGLoginEvent) {
            valid = true;
            finished = true;
        }
        // shouldn't happen
        else if (dsgEvent instanceof DSGLoginErrorEvent) {
            valid = false;
            finished = true;
        }
        // socket broken
        else if (dsgEvent instanceof DSGExitMainRoomEvent) {
            finished = true;
        }
        
        if (finished) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
}