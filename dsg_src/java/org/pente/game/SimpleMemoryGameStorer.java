/**
 * SimpleMemoryGameStorer.java
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

import java.util.*;

/** A simple implementation of GameStorer, PlayerStorer that holds game and
 *  player data in memory using Hashtables.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class SimpleMemoryGameStorer implements GameStorer, PlayerStorer {

    /** stores hashtables of players indexed by names */
    private Hashtable siteNames;

    /** stores hashtables of players indexed by player id */
    private Hashtable sitePlayerIDs;

    /** stores game data */
    private Hashtable games;

    /** Initialize the Hashtables */
    public SimpleMemoryGameStorer() {
        siteNames = new Hashtable();
        sitePlayerIDs = new Hashtable();
        games = new Hashtable();
    }

    /** Checks to see if the game has already been stored
     *  @param gameID The unique game id
     *  @return boolean Flag if game has been stored
     */
    public synchronized boolean gameAlreadyStored(long gameID) {
        return games.containsKey(Long.valueOf(gameID));
    }

    /** Checks to see if the game has already been stored
     *  @param gameData The game data
     *  @return boolean Flag if game has been stored
     *  @exception Exception If the game cannot be checked
     */
    public boolean gameAlreadyStored(GameData data) throws Exception {
        // could use gamedatahasher to create hash key for game and store games
        // referenced by it, then use gamedata.equals() to check
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Stores the game information
     *  @param data The game data
     */
    public synchronized void storeGame(GameData data) {
        games.put(Long.valueOf(data.getGameID()), data);
        storePlayer(data.getPlayer1Data(), data.getSite());
        storePlayer(data.getPlayer2Data(), data.getSite());
    }

    /** Loads the game information
     *  @param gameID The unique game id
     *  @param data Caution, this data isn't used
     *  @return GameData The game data
     */
    public synchronized GameData loadGame(long gameID, GameData data) {
        return (GameData) games.get(Long.valueOf(gameID));
    }

    /** Checks to see if the player has already been stored
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     */
    public synchronized boolean playerAlreadyStored(long playerID, String site) {

        Hashtable players = (Hashtable) sitePlayerIDs.get(site);
        if (players == null) {
            return false;
        } else {
            return players.containsKey(Long.valueOf(playerID));
        }
    }

    /** Checks to see if the player has already been stored
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return boolean Flag if player has been stored
     */
    public synchronized boolean playerAlreadyStored(String name, String site) {

        Hashtable players = (Hashtable) siteNames.get(site);
        if (players == null) {
            return false;
        } else {
            return players.containsKey(name);
        }
    }

    /** Stores the player information
     *  @param data The PlayerData for a game
     *  @param site The site the player is registered for
     */
    public synchronized void storePlayer(PlayerData data, String site) {

        Hashtable players = (Hashtable) sitePlayerIDs.get(site);
        if (players == null) {
            players = new Hashtable();
            sitePlayerIDs.put(site, players);
        }

        players.put(Long.valueOf(data.getUserID()), data);

        players = (Hashtable) siteNames.get(site);
        if (players == null) {
            players = new Hashtable();
            siteNames.put(site, players);
        }

        players.put(data.getUserIDName(), data);
    }

    /** Loads the player information
     *  @param playerID The unique player id
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     */
    public synchronized PlayerData loadPlayer(long playerID, String site) {

        Hashtable players = (Hashtable) sitePlayerIDs.get(site);
        if (players == null) {
            return null;
        } else {
            return (PlayerData) players.get(Long.valueOf(playerID));
        }
    }

    /** Loads the player information
     *  @param name The players name
     *  @param site The site the player is registered for
     *  @return PlayerData The player data
     */
    public synchronized PlayerData loadPlayer(String name, String site) {

        Hashtable players = (Hashtable) siteNames.get(site);
        if (players == null) {
            return null;
        } else {
            return (PlayerData) players.get(name);
        }
    }

    /** Nothing to destroy... */
    public void destroy() {
    }
}
