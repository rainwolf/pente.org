package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.event.DSGBootMainRoomEvent;
import org.pente.gameServer.server.*;

public class BootDialogTest {

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
		
		PlayerListComponent playerList = new PlayerListPanel(gameStyle.boardBack);
		playerList.setGame(1);
		playerList.setTableName("Boot Player");
		DSGPlayerData d1 = new SimpleDSGPlayerData();
		d1.setName("dweebo");
		d1.setAdmin(true);
		d1.setPlayerType(DSGPlayerData.HUMAN);
		
		playerList.addPlayer(d1);
		
        final Frame f = new Frame();
		f.setLocation(500, 500);
        final BootDialog d = new BootDialog(

            f, gameStyle, playerList, 
            new PlayerActionAdapter() {
                public void actionRequested(String player, Object o) {
					System.out.println("boot " + player + " for " + ((Integer)o).intValue());
                }
            }, true);
	}
}
