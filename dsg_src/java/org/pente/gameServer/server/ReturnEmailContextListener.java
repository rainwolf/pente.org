package org.pente.gameServer.server;

import javax.servlet.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.core.*;

public class ReturnEmailContextListener implements ServletContextListener {

    private static Category log4j = 
        Category.getInstance(ReturnEmailContextListener.class.getName());

    private static final int SLEEP_TIME = 1000 * 60 * 1;

    private Thread thread;
    private volatile boolean running = true;

    public void contextInitialized(ServletContextEvent servletContextEvent) {

        try {

            ServletContext ctx = servletContextEvent.getServletContext();

            Boolean emailEnabled = (Boolean) ctx.getAttribute("emailEnabled");
            if (!emailEnabled.booleanValue()) {
                log4j.info("Email not enabled, not starting returned email scanner.");
                return;
            }
            
            DSGPlayerStorer dsgPlayerStorer = 
                (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
            DBHandler dbHandler = 
                (DBHandler) ctx.getAttribute(DBHandler.class.getName());
            final ReturnEmailScanner scanner = new ReturnEmailScanner(
                dbHandler, dsgPlayerStorer);

            thread = new Thread(new Runnable() {
                public void run() {
                    while (running) {
                        
                        try {
                            scanner.scanEmails();
                        } catch (Throwable t) {
                            log4j.error("Problem scanning emails.", t);
                        }
                        
                        try {
                            Thread.sleep(SLEEP_TIME);
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }, "ReturnEmailScanner");
            //thread.start();

        } catch (Throwable t) {
            log4j.error("Problem in contextInitialized()", t);
        }
    }
    
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        running = false;
        if (thread != null) {
            log4j.info("Stopping return email scanning.");
            thread.interrupt();
        }
    }
}