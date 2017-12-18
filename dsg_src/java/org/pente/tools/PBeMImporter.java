package org.pente.tools;

import java.io.*;
import java.util.*;

import org.pente.game.*;
import org.pente.database.*;

import org.apache.log4j.*;

public class PBeMImporter {

    private File        gameDir;
    private GameFormat  gameFormat;
    private GameStorer  gameStorer;
    private DBHandler   dbHandler;

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        PBeMImporter importer = new PBeMImporter();

        String user = args[0];
        String password = args[1];
        String db = args[2];
        String host = args[3];
        importer.dbHandler = new MySQLDBHandler(user, password, db, host);

        //importer.dbHandler = new DerbyDBHandler("/derby_db/game_db");
        final GameVenueStorer gvs = new MySQLGameVenueStorer(importer.dbHandler);
        MySQLPenteGameStorer p = new MySQLPenteGameStorer(
                importer.dbHandler, gvs);

        importer.gameStorer = p;

        importer.gameDir = new File(args[4]);
        importer.gameFormat = new PGNGameFormat("\r\n", "yyyy.MM.dd");
        // importer.gameFormat = new DSG2_12GameFormat("\r\n");

        importer.loadGames();

        importer.gameStorer.destroy();
    }

    public void loadGames() throws Exception {
        Scanner scan = new Scanner(System.in);

        String files[] = gameDir.list();
        for (int i = 0; i < files.length; i++) {
            File file = new File(gameDir, files[i]);
            if (file.isFile()) {
                GameData data = loadGame(files[i]);

                if (i%500 == 0) {
                    System.out.println("Pausing... press enter to continue");
                    scan.nextLine();
                }

                if (data != null) {
                    
                    try {
                        gameStorer.storeGame(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                        file.renameTo(new File(gameDir+"1", files[i]));  
                    }

                    System.out.println("loaded " + files[i] + " ok");
                }
                else {
                    System.out.println("not loaded " + files[i] + " ok");
                }
            }
        }
    }

    private static final long BASE_GID=31000000001170L;
    public GameData loadGame(String file) throws Exception {

        File gameFile = new File(gameDir, file);
        FileReader reader = null;
        GameData gameData  = null;

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
                }
                else {
                    buffer.append(chars);
                }
            }

            // parse the game data
            gameData = new DefaultGameData();

            if (gameData != null) {

                //reuse bk id's
                gameData = (GameData) gameFormat.parse(gameData, buffer);

                gameData.setSite("Richard's PBeM Server");

                long gid = new Long(file.substring(5, file.length()-4)).longValue();
                gameData.setGameID(BASE_GID + gid);
                System.out.println("gid " + gid);

                PlayerStorer playerStorer = (PlayerStorer) gameStorer;
                if (playerStorer.playerAlreadyStored(gameData.getPlayer1Data().getUserIDName(), "Richard's PBeM Server")) {
                    PlayerData player1Data = playerStorer.loadPlayer(gameData.getPlayer1Data().getUserIDName(), "Richard's PBeM Server");
                    gameData.getPlayer1Data().setUserID(player1Data.getUserID());
                }

                if (playerStorer.playerAlreadyStored(gameData.getPlayer2Data().getUserIDName(), "Richard's PBeM Server")) {
                    PlayerData player2Data = playerStorer.loadPlayer(gameData.getPlayer2Data().getUserIDName(), "Richard's PBeM Server");
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
