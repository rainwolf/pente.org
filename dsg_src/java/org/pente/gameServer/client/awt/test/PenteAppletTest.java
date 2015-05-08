package org.pente.gameServer.client.awt.test;

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;

import org.pente.gameDatabase.swing.DefaultExceptionHandler;
import org.pente.gameServer.client.awt.PenteApplet;

public class PenteAppletTest {

    public static void main(String[] args) {

        Thread.setDefaultUncaughtExceptionHandler(
            new DefaultExceptionHandler());
        
		final String host = args[0];
        final Frame f = new Frame("PenteApplet");
        final PenteApplet applet = new PenteApplet();

        applet.setStub(new AppletStub() {
            public String getParameter(String key) {
                return System.getProperties().getProperty(key);
            }
            
            public boolean isActive() {
                return true;
            }

            public URL getDocumentBase() {
                try {
                    return new URL("http://" + host + "/gameServer");
                } catch (MalformedURLException e) {
                }
                return null;
            }

            public URL getCodeBase() {
                try {
                    return new URL("http://" + host + "/gameServer/lib");
                } catch (MalformedURLException e) {
                }
                return null;
            }

            public AppletContext getAppletContext() {
                return null;
            }

            public void appletResize(int width, int height) {
            }
        });

        f.add(applet, "Center");

        f.setSize(640, 420);
        f.setLocation(100, 100);


        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
                applet.destroy();
                System.exit(0);
            }
        });

        f.setVisible(true);
        applet.init();
        applet.start();
    }
}
