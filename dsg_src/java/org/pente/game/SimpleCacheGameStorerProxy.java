/**
 * SimpleCacheGameStorerProxy.java
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

/** A simple implementation of GameStorer, PlayerStorer that creates a cache
 *  of players and games in a faster storer (MemoryGameStorer) from which to
 *  check/load/store first.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class SimpleCacheGameStorerProxy implements GameStorer, PlayerStorer {

    /** The quicker of the two game storers */
    private GameStorer quickerGameStorer;

    /** The slower of the two game storers */
    private GameStorer slowerGameStorer;

    /** The quicker of the two game storers */
    private PlayerStorer quickerPlayerStorer;

    /** The slower of the two game storers */
    private PlayerStorer slowerPlayerStorer;

    /** Constructor
     *  @param quickerGameStorer The quicker game storer
     *  @param slowerGameStorer The slower game storer
     *  @param quickerPlayerStorer The quicker player storer
     *  @param slowerPlayerStorer The slower player storer
     */
    public SimpleCacheGameStorerProxy(GameStorer quickerGameStorer,
                                      GameStorer slowerGameStorer,
                                      PlayerStorer quickerPlayerStorer,
                                      PlayerStorer slowerPlayerStorer) {

        this.quickerGameStorer = quickerGameStorer;
        this.slowerGameStorer = slowerGameStorer;
        this.quickerPlayerStorer = quickerPlayerStorer;
        this.slowerPlayerStorer = slowerPlayerStorer;
    }

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public synchronized boolean gameAlreadyStored(long gameID) throws Exception {

        // check if game is stored in the quicker storer
        boolean stored = quickerGameStorer.gameAlreadyStored(gameID);

        // if so return true
        if (stored) {
            return true;
        }
        // else check for the game in the slower storer
        else {
            return slowerGameStorer.gameAlreadyStored(gameID);
        }
    }

    /** Checks to see if the game has already been stored
     *  @param gameData The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public synchronized boolean gameAlreadyStored(GameData data) throws Exception {

        // check if game is stored in the quicker storer
        boolean stored = quickerGameStorer.gameAlreadyStored(data);

        // if so return true
        if (stored) {
            return true;
        }
        // else check for the game in the slower storer
        else {
            return slowerGameStorer.gameAlreadyStored(data);
        }
    }

    /** Stores the game information
     *  @param data The game data
     *  @exception Exception If the game cannot be stored
     */
    public synchronized void storeGame(GameData data) throws Exception {

        // store the game in both storers
        quickerGameStorer.storeGame(data);
        slowerGameStorer.storeGame(data);
    }

    /** Loads the game information
     *  @param gameID The unique game id
     *  @param data To load the game data into
     *  @return GameData The game data
     *  @exception Exception If the game cannot be loaded
     */
    public GameData loadGame(long gameID, GameData data) throws Exception {

        // if the quicker storer already has the data stored, get it from the
        // quicker storer
        if (quickerGameStorer.gameAlreadyStored(gameID)) {
            return quickerGameStorer.loadGame(gameID, data);
        }
        // else get it from the slower storer and also place a copy in the faster
        // storer for future accesses
        else {
            data = slowerGameStorer.loadGame(gameID, data);
            if (data != null) {
                quickerGameStorer.storeGame(data);
            }

            return data;
        }
    }

    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(long playerID, String site) throws Exception {

        // check if player is stored in the quicker storer
        boolean stored = quickerPlayerStorer.playerAlreadyStored(playerID, site);

        // if so return true
        if (stored) {
            return true;
        }
        // else check for the player in the slower storer
        else {
            return slowerPlayerStorer.playerAlreadyStored(playerID, site);
        }
    }

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     *  @exception Exception If the player cannot be checked
     */
    public boolean playerAlreadyStored(String name, String site) throws Exception {

        // check if player is stored in the quicker storer
        boolean stored = quickerPlayerStorer.playerAlreadyStored(name, site);

        // if so return true
        if (stored) {
            return true;
        }
        // else check for the player in the slower storer
        else {
            return slowerPlayerStorer.playerAlreadyStored(name, site);
        }
    }

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @param site The site the player is registered for
     *  @exception If the player cannot be stored
     */
    public void storePlayer(PlayerData data, String site) throws Exception {

        // store the player data in both storers
        quickerPlayerStorer.storePlayer(data, site);
        slowerPlayerStorer.storePlayer(data, site);
    }

    /** Loads the player information
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(long playerID, String site) throws Exception {

        // if the quicker storer already has the data stored, get it from the
        // quicker storer
        if (quickerPlayerStorer.playerAlreadyStored(playerID, site)) {
            return quickerPlayerStorer.loadPlayer(playerID, site);
        }
        // else get it from the slower storer and also place a copy in the faster
        // storer for future accesses
        else {
            PlayerData data = slowerPlayerStorer.loadPlayer(playerID, site);
            if (data != null) {
                quickerPlayerStorer.storePlayer(data, site);
            }

            return data;
        }
    }

    /** Loads the player information
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     *  @exception If the player cannot be stored
     */
    public PlayerData loadPlayer(String name, String site) throws Exception {

        // if the quicker storer already has the data stored, get it from the
        // quicker storer
        if (quickerPlayerStorer.playerAlreadyStored(name, site)) {
            return quickerPlayerStorer.loadPlayer(name, site);
        }
        // else get it from the slower storer and also place a copy in the faster
        // storer for future accesses
        else {
            PlayerData data = slowerPlayerStorer.loadPlayer(name, site);
            if (data != null) {
                quickerPlayerStorer.storePlayer(data, site);
            }

            return data;
        }
    }

    /** Clean up any resources that are still being held
     */
    public void destroy() {

        // allow proxied storers to destroy()
        quickerGameStorer.destroy();
        slowerGameStorer.destroy();
        quickerPlayerStorer.destroy();
        slowerGameStorer.destroy();
    }
}