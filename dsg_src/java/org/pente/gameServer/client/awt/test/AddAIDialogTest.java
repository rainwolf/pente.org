package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.server.*;

public class AddAIDialogTest {

    public static void main(String[] args) throws Throwable {

        GameStyles gameStyle = new GameStyles(
                new Color(0, 102, 153), //board back
                new Color(188, 188, 188), //button back
                Color.black, //button fore
                new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                Color.white, //player 1 back
                Color.black, //player 1 fore
                Color.black, //player 2 back
                Color.white, //player 2 fore
                new Color(188, 188, 188)); //watcher

        AIConfigurator configurator = new XMLAIConfigurator();
        Collection aiDataCollection = configurator.getAIData(args[0]);
        Vector aiData = new Vector(aiDataCollection);

        final Frame f = new Frame();
        f.setLocation(500, 500);
        final AddAIDialog d = new AddAIDialog(
                f, gameStyle, e -> System.out.println("add AI " + e.getActionCommand()), 1, aiData);
    }

}
