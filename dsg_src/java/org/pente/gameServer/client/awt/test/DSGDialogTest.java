/**
 * DSGDialogTest.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.GameStyles;
import org.pente.gameServer.client.awt.*;

public class DSGDialogTest {

    public static void main(String args[]) {

        final Frame f = new Frame("UndoDialogTest");

        final GameStyles gameStyle =
                new GameStyles(new Color(0, 0, 153), //board back
                        new Color(51, 102, 204), //button back
                        Color.white, //button fore
                        new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                        Color.white, //player 1 back
                        Color.black, //player 1 fore
                        Color.black, //player 2 back
                        Color.white, //player 2 fore
                        new Color(51, 102, 204)); //watcher

        Button undoButton = new Button("Undo");
        undoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                DSGDialog undo = DSGDialogFactory.createUndoDialog(f, gameStyle);
                undo.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        System.out.println("undo action performed: " + e.getActionCommand());
                    }
                });
                undo.setVisible(true);
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                DSGDialog cancel = DSGDialogFactory.createCancelDialog(f, gameStyle, "game");
                cancel.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        System.out.println("cancel action performed: " + e.getActionCommand());
                    }
                });
                cancel.setVisible(true);
            }
        });
        Button resignButton = new Button("Resign");
        resignButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                DSGDialog resign = DSGDialogFactory.createResignDialog(f, gameStyle);
                resign.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        System.out.println("resign action performed: " + e.getActionCommand());
                    }
                });
                resign.setVisible(true);
            }
        });

        f.setLayout(new GridLayout(3, 1));
        f.add(undoButton);
        f.add(cancelButton);
        f.add(resignButton);

        f.setSize(200, 200);
        f.setLocation(100, 100);
        f.setVisible(true);

        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                f.dispose();
            }
        });
    }
}

