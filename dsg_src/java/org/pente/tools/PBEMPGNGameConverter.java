/**
 * PBEMGameConverter.java
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

package org.pente.tools;

import java.io.*;

import org.pente.game.*;
import org.pente.database.*;

import org.apache.log4j.*;

public class PBEMPGNGameConverter {

    private File gameDir;
    private GameFormat gameFormat;
    private GameStorer gameStorer;
    private DBHandler dbHandler;

    /**
     * @param args
     * <user> <password> <db> <host> <directory>
     * @throws Throwable
     */
    public static void main(String args[]) throws Throwable {

        BasicConfigurator.configure();

        PBEMPGNGameConverter pbemConverter = new PBEMPGNGameConverter();

        String user = args[0];
        String password = args[1];
        String db = args[2];
        String host = args[3];
        pbemConverter.dbHandler = new MySQLDBHandler(user, password, db, host);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(
                pbemConverter.dbHandler);
        pbemConverter.gameStorer = new MySQLPenteGameStorer(
                pbemConverter.dbHandler, gameVenueStorer);

        pbemConverter.gameDir = new File(args[4]);


        pbemConverter.loadGames();


        pbemConverter.gameStorer.destroy();
    }

    public PBEMPGNGameConverter() {
        gameFormat = new PGNGameFormat("\r\n", "yyyy.MM.dd");
    }

    public void loadGames() throws Exception {

        String files[] = gameDir.list();
        for (int i = 0; i < files.length; i++) {

            if (new File(gameDir, files[i]).isFile()) {
                GameData data = loadGame(files[i]);

                if (data != null) {

                    gameStorer.storeGame(data);

                    System.out.println("loaded " + files[i] + " ok");
                } else {
                    System.out.println("not loaded " + files[i] + " ok");
                }
            }
        }
    }

    public GameData loadGame(String file) throws Exception {

        File gameFile = new File(gameDir, file);
        FileReader reader = null;
        GameData gameData = null;

        try {
            // read game into a StringBuffer
            reader = new FileReader(gameFile);
            StringBuffer buffer = new StringBuffer();
            char chars[];
            while (true) {

                chars = new char[1024];
                int length = reader.read(chars);
                if (length == -1) {
                    break;
                } else {
                    buffer.append(chars);
                }
            }

            // parse the game data
            gameData = new DefaultGameData();
            gameData = (GameData) gameFormat.parse(gameData, buffer);

            if (gameData != null) {
                gameData.setEvent("Abstracta Marathon");
                gameData.setTimed(true);
                gameData.setInitialTime(90);
                gameData.setRated(true);
                gameData.setSite(PBEMGameFormat.SITE_NAME);

                PlayerStorer playerStorer = (PlayerStorer) gameStorer;
                if (playerStorer.playerAlreadyStored(gameData.getPlayer1Data().getUserIDName(), PBEMGameFormat.SITE_NAME)) {
                    PlayerData player1Data = playerStorer.loadPlayer(gameData.getPlayer1Data().getUserIDName(), PBEMGameFormat.SITE_NAME);
                    gameData.getPlayer1Data().setUserID(player1Data.getUserID());
                }

                if (playerStorer.playerAlreadyStored(gameData.getPlayer2Data().getUserIDName(), PBEMGameFormat.SITE_NAME)) {
                    PlayerData player2Data = playerStorer.loadPlayer(gameData.getPlayer2Data().getUserIDName(), PBEMGameFormat.SITE_NAME);
                    gameData.getPlayer2Data().setUserID(player2Data.getUserID());
                }
            }

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return gameData;
    }
}