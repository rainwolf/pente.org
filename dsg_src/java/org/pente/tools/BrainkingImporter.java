package org.pente.tools;

import java.io.*;

import org.pente.game.*;
import org.pente.database.*;

import org.apache.log4j.*;

public class BrainkingImporter {

    private File        gameDir;
    private GameFormat  gameFormat;
    private GameStorer  gameStorer;
    private DBHandler   dbHandler;

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        BrainkingImporter importer = new BrainkingImporter();
        
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
        importer.gameFormat = new PGNGameFormat("\n", "yyyy.MM.dd");
        // importer.gameFormat = new DSG2_12GameFormat("\r\n");
        
        importer.loadGames();

        importer.gameStorer.destroy();
    }

    public void loadGames() throws Exception {

        String files[] = gameDir.list();
        for (int i = 0; i < files.length; i++) {

            if (new File(gameDir, files[i]).isFile()) {
                GameData data = loadGame(files[i]);

                if (data != null) {

                    gameStorer.storeGame(data);

                    System.out.println("loaded " + files[i] + " ok");
                }
                else {
                    System.out.println("not loaded " + files[i] + " ok");
                }
            }
        }
    }

    private static final long BASE_GID=41000000000000L;
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
                
                gameData.setSite("BrainKing");
                
                long gid = new Long(file.substring(0, file.length()-4)).longValue();
                gameData.setGameID(BASE_GID + gid);
                    System.out.println("gid " + gid);
                
                PlayerStorer playerStorer = (PlayerStorer) gameStorer;
                if (playerStorer.playerAlreadyStored(gameData.getPlayer1Data().getUserIDName(), "BrainKing")) {
                    PlayerData player1Data = playerStorer.loadPlayer(gameData.getPlayer1Data().getUserIDName(), "BrainKing");
                    gameData.getPlayer1Data().setUserID(player1Data.getUserID());
                }

                if (playerStorer.playerAlreadyStored(gameData.getPlayer2Data().getUserIDName(), "BrainKing")) {
                    PlayerData player2Data = playerStorer.loadPlayer(gameData.getPlayer2Data().getUserIDName(), "BrainKing");
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
