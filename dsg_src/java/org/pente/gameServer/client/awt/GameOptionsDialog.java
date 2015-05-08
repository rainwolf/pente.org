/** GameOptionsFrame.java
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

package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.client.*;

public class GameOptionsDialog extends Dialog {

    private static final String APPLY =         "Apply";
    private static final String CLOSE =         "Ok";
    private static final String SOUND =         "Audio alert";
    private static final String LAST_MOVE =     "Last Move";
    private static final String PIECES_3D =     "Use 3D Pieces";

    private Vector              listeners = new Vector();

    public GameOptionsDialog(Frame frame,
    						GameStyles gameStyle,
                            final GameOptions gameOptions,
                            int maxPlayers) {

        super(frame, "Game Options", false);

        Label player1ColorLabel = new Label("Player 1 Color");
        player1ColorLabel.setForeground(gameStyle.foreGround);

        final Choice player1Colors = new Choice();
        player1Colors.add("White");
        player1Colors.add("Black");
        player1Colors.add("Red");
        player1Colors.add("Orange");
        player1Colors.add("Yellow");
        player1Colors.add("Blue");
        player1Colors.add("Green");
        player1Colors.add("Purple");
        player1Colors.select("White");
        player1Colors.setBackground(Color.white);
        player1Colors.setForeground(Color.black);
        player1Colors.select(gameOptions.getPlayerColor(1));

        Label player2ColorLabel = new Label("Player 2 Color");
        player2ColorLabel.setForeground(gameStyle.foreGround);

        final Choice player2Colors = new Choice();
        player2Colors.add("White");
        player2Colors.add("Black");
        player2Colors.add("Red");
        player2Colors.add("Orange");
        player2Colors.add("Yellow");
        player2Colors.add("Blue");
        player2Colors.add("Green");
        player2Colors.add("Purple");
        player2Colors.select("Black");
        player2Colors.setBackground(Color.white);
        player2Colors.setForeground(Color.black);
        player2Colors.select(gameOptions.getPlayerColor(2));

        final Checkbox soundCheck = new Checkbox(SOUND, true);
        soundCheck.setBackground(gameStyle.boardBack);
        soundCheck.setForeground(gameStyle.foreGround);
        soundCheck.setState(gameOptions.getPlaySound());

        final Checkbox lastMoveCheck = new Checkbox(LAST_MOVE, true);
        lastMoveCheck.setBackground(gameStyle.boardBack);
        lastMoveCheck.setForeground(gameStyle.foreGround);
        lastMoveCheck.setState(gameOptions.getShowLastMove());

        final Checkbox draw3DCheck = new Checkbox(PIECES_3D, true);
        draw3DCheck.setBackground(gameStyle.boardBack);
        draw3DCheck.setForeground(gameStyle.foreGround);
        draw3DCheck.setState(gameOptions.getDraw3DPieces());


        class UpdateGameOptions implements ActionListener {
            public void actionPerformed(ActionEvent e) {

                gameOptions.setPlayerColor(player1Colors.getSelectedIndex(), 1);
                gameOptions.setPlayerColor(player2Colors.getSelectedIndex(), 2);
                gameOptions.setDraw3DPieces(draw3DCheck.getState());
                gameOptions.setPlaySound(soundCheck.getState());
                gameOptions.setShowLastMove(lastMoveCheck.getState());

                for (int i = 0; i < listeners.size(); i++) {
                    GameOptionsChangeListener l = (GameOptionsChangeListener) listeners.elementAt(i);
                    l.gameOptionsChanged(gameOptions);
                }
            }
        }
        ActionListener updateGameOptions = new UpdateGameOptions();

        Button closeButton = gameStyle.createDSGButton(CLOSE);
        closeButton.addActionListener(updateGameOptions);
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        Button applyButton = gameStyle.createDSGButton(APPLY);
        applyButton.addActionListener(updateGameOptions);

        setLayout(new GridLayout(9, 1, 5, 5));
        add(player1ColorLabel);
        add(player1Colors);
        add(player2ColorLabel);
        add(player2Colors);
        add(soundCheck);
        add(lastMoveCheck);
        add(draw3DCheck);
        add(closeButton);
        add(applyButton);

        setBackground(gameStyle.boardBack);
        setResizable(false);

        pack();
        centerDialog(frame);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    public void addGameOptionsChangeListener(GameOptionsChangeListener listener) {
        listeners.addElement(listener);
    }
    public void removeGameOptionsChangeListener(GameOptionsChangeListener listener) {
        listeners.removeElement(listener);
    }

    public Insets getInsets() {
        return new Insets(30, 30, 30, 30);
    }
    
	private void centerDialog(Frame frame) {

		Point location = new Point();
		location.x = frame.getLocation().x +
				     (frame.getSize().width + frame.getInsets().right - frame.getInsets().left) / 2 -
				     getSize().width / 2;
		location.y = frame.getLocation().y +
				     (frame.getSize().height + frame.getInsets().top - frame.getInsets().bottom) / 2 -
				     (getSize().height + getInsets().top - getInsets().bottom) / 2;
		setLocation(location);
	}
}

