/**
 * SimpleFileGameStorer.java
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

package org.pente.game;

import java.io.*;

/** A simple implementation of GameStorer, PlayerStorer that stores game and
 *  player data in files.  The game can be stored in a human readable format,
 *  this might be useful for people.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class SimpleFileGameStorer implements GameStorer, PlayerStorer {

    /** The Game Format to use when storing the file */
    private GameFormat gameFormat;

    /** The directory to store game files in */
    private File gameDir;

    /** The directory to store player files in */
    private File playerDir;

    /** Used to store the player data in memory for retrieval */
    private PlayerStorer playerStorer;

    /** Used if storing a player with pid == 0 */
    private long maxPid = 0;

    /** Used if storing a game with gid == 0 */
    private long maxGid = 0;

    /** Constructor
     *  @param gameFormat The game format to use when storing/loading games
     *  @param gameDir The directory to store game files in
     *  @param playerDir The directory to store player files in
     *  @exception Exception If the players can't be loaded
     */
    public SimpleFileGameStorer(GameFormat gameFormat, File gameDir, File playerDir) throws Exception {

        this.gameFormat = gameFormat;
        this.gameDir = gameDir;
        this.playerDir = playerDir;

        playerStorer = (PlayerStorer) new SimpleMemoryGameStorer();

        loadPlayers();
        loadGames();
    }

    /** Read in all player files and store the PlayerData objects
     *  in the hashtables.
     *  @exception Exception If the players can't be loaded
     */
    public void loadPlayers() throws Exception {

        // get a list of all files in the directory
        File playerFiles[] = playerDir.listFiles();

        // for each file
        for (int i = 0; i < playerFiles.length; i++) {
            try {
                long pid = Long.parseLong(playerFiles[i].getName());
                if (pid > maxPid) {
                    maxPid = pid;
                }
            } catch (NumberFormatException ex) {
            }

            // read in the player data with an object stream
            ObjectInputStream inputStream = null;
            try {
                inputStream = new ObjectInputStream(new FileInputStream(playerFiles[i]));
                PlayerData playerData = (PlayerData) inputStream.readObject();
                String site = (String) inputStream.readUTF();

                // store the player data in the hashtables
                if (playerData != null) {
                    playerStorer.storePlayer(playerData, site);
                }

            } catch (Exception ex) {
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }
    }

    public void loadGames() throws Exception {

        File gameFiles[] = gameDir.listFiles();

        for (int i = 0; i < gameFiles.length; i++) {
            try {
                long gid = Long.parseLong(gameFiles[i].getName());
                if (gid > maxGid) {
                    maxGid = gid;
                }
            } catch (NumberFormatException ex) {
            }
        }
    }

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(long gameID) throws Exception {

        File gameFile = new File(gameDir, Long.toString(gameID));
        return gameFile.exists();
    }

    /** Checks to see if the game has already been stored
     *  @param gameData The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(GameData data) throws Exception {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Stores the game information
     *  @param data The game data
     *  @exception Exception If the game cannot be stored
     */
    public synchronized void storeGame(GameData data) throws Exception {

        // if storing a game without a gid, create a new one
        if (data.getGameID() == 0) {
            data.setGameID(getNewGid());
        }

        File gameFile = new File(gameDir, Long.toString(data.getGameID()));
        FileWriter fileWriter = null;

        try {
            // format the game
            StringBuffer gameBuffer = new StringBuffer();
            gameFormat.format(data, gameBuffer);

            // write the game to a file
            fileWriter = new FileWriter(gameFile);
            fileWriter.write(gameBuffer.toString());

            // store player data for the players in the game if not already stored
            if (!playerAlreadyStored(data.getPlayer1Data().getUserID(), data.getSite())) {
                storePlayer(data.getPlayer1Data(), data.getSite());
            }
            if (!playerAlreadyStored(data.getPlayer2Data().getUserID(), data.getSite())) {
                storePlayer(data.getPlayer2Data(), data.getSite());
            }

        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    /** Loads the game information
     *  @param gameID The unique game id
     *  @param data To load the game data into
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public synchronized GameData loadGame(long gameID, GameData gameData) throws Exception {

        File gameFile = new File(gameDir, Long.toString(gameID));
        FileReader reader = null;

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
                    buffer.append(chars, 0, length);
                }
            }

            // parse the game data
            if (gameData == null) {
                gameData = new DefaultGameData();
            }
            gameData.setGameID(gameID);
            gameFormat.parse(gameData, buffer);

            // load the player data for the players in the game
            gameData.setPlayer1Data(loadPlayer(gameData.getPlayer1Data().getUserIDName(), gameData.getSite()));
            gameData.setPlayer2Data(loadPlayer(gameData.getPlayer2Data().getUserIDName(), gameData.getSite()));

        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return gameData;
    }

    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(long playerID, String site) throws Exception {
        return playerStorer.playerAlreadyStored(playerID, site);
    }

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(String name, String site) throws Exception {
        return playerStorer.playerAlreadyStored(name, site);
    }

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @param site The site the player is registered for
     *  @exception If the player cannot be stored
     */
    public synchronized void storePlayer(PlayerData data, String site) throws Exception {

        // if player already stored, return
        if (playerAlreadyStored(data.getUserIDName(), site)) {
            return;
        }

        // if storing a game without a gid, create a new one
        if (data.getUserID() == 0) {
            data.setUserID(getNewPid());
        }

        playerStorer.storePlayer(data, site);

        // write the player data with an object stream
        ObjectOutputStream outputStream = null;
        File playerFile = new File(playerDir, Long.toString(data.getUserID()));

        try {
            outputStream = new ObjectOutputStream(new FileOutputStream(playerFile.getCanonicalPath()));
            outputStream.writeObject(data);
            outputStream.writeUTF(site);

        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    /** Loads the player information
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     */
    public synchronized PlayerData loadPlayer(long playerID, String site) throws Exception {
        return playerStorer.loadPlayer(playerID, site);
    }

    /** Loads the player information
     *  @param userIDName The name of the player
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     */
    public PlayerData loadPlayer(String name, String site) throws Exception {
        return playerStorer.loadPlayer(name, site);
    }

    public synchronized long getNewPid() {
        return ++maxPid;
    }

    public synchronized long getNewGid() {
        return ++maxGid;
    }

    /** Nothing to destroy */
    public void destroy() {
    }
}