/**
 * IYTGameFilter.java
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

package org.pente.filter.iyt;

import java.util.*;
import java.awt.*;
import java.text.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.filter.*;
import org.pente.filter.iyt.game.*;

/** This line filter filters out all information for a game
 *  @see IYTGameBuilder
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTGameFilter implements LineFilter {

    private static Category cat = Category.getInstance(IYTGameFilter.class.getName());

    /** Game name - used by getInfo() */
    private final String GAME_NAME_FIELD = "game:";
    /** Game enclosing tag - used by getInfo() */
    private final String GAME_NAME_TAG = "b";
    /** Event name - used by getInfo() */
    private final String EVENT_FIELD = "event:";
    /** Event enclosing tag - used by getInfo() */
    private final String EVENT_TAG = "b";
    /** Round name - used by getInfo() */
    private final String ROUND_FIELD = "round";
    /** Round enclosing tag - used by getInfo() */
    private final String ROUND_TAG = "b";
    /** Section name - used by getInfo() */
    private final String SECTION_FIELD = "section";
    /** Section enclosing tag - used by getInfo() */
    private final String SECTION_TAG = "b";
    /** Player type name - used by getInfo() */
    private final String PLAYER_TYPE_FIELD = "playing";
    /** Player type enclosing tag - used by getInfo() */
    private final String PLAYER_TYPE_TAG = "b";
    /** Winner name - used by getInfo() */
    private final String WINNER_FIELD = "Game complete:";
    /** Winner enclosing tag - used by getInfo() */
    private final String WINNER_TAG = "b";
    /** Date name - used by getInfo() */
    private final String DATE_FIELD = "Last move made at:";
    /** Date enclosing tag - used by getInfo() */
    private final String DATE_TAG = null;
    /** Date offset from DATE_FIELD - used to get date */
    private final int DATE_MAGIC_OFFSET = 12;

    private final String START_BOARD = "Current state of this game";
    private final int START_BOARD_FIRST_OFFSET = -3;
    private final int ROW_LINE_OFFSET = -2;

    private final String START_MOVES = "navline1.js";

    /** Temporary game name */
    private String gameName;
    /** Temporary game event */
    private String event;
    /** Temporary game round */
    private String round;
    /** Temporary game section */
    private String section;
    /** Temporary player type */
    private String playerType;
    /** Temporary game winner */
    private String winner;
    /** Temporary game date */
    private String date;

    /** my player data */
    private PlayerData myPlayerData;
    /** opponent player data */
    private PlayerData opponentPlayerData;

    /** filter states */
    private final int SEARCHING_FOR_MOVES = 0,
            PROCESSING_BOARD = 1,
            DONE = 2;
    private int filterState = PROCESSING_BOARD;

    /** The dimension of the board */
    private Dimension boardSize;

    /** The previous filter to call before filtering */
    private LineFilter prevFilter;

    /** The filtered game data */
    private GameData gameData;
    /** The game storer */
    private GameStorer gameStorer;
    /** The player data for the player requesting the game */
    private PlayerData sessionPlayerData;
    /** The player storer */
    private PlayerStorer playerStorer;

    /** The parameters to connect to iyt */
    private Hashtable params;
    /** The cookies to connect to iyt */
    private Hashtable cookies;
    /** Flag to indicate if the game was found or not */
    private boolean gameFound = false;

    /** A temporary vector of coordinate lines */
    private Vector coordinateLines;
    /** The number of moves in the game */
    private int moves;
    /** Flag if all moves were found */
    private boolean allMovesFound;


    /** The redirected host for use by game.txt link */
    private String redirectedHost;

    /** A list of supported games to show coordinates for */
    private static Vector supportedGames = new Vector();

    static {
        supportedGames.addElement("Pro Pente");
    }

    /** Use this constructor if this is the first filter in the chain
     *  @param gameStorer The game storer
     *  @param playerStorer The player storer
     *  @param sessionPlayerData The player data for the player requesting the game
     *  @param params The parameters used to connect to iyt
     *  @param cookies The cookies used to connect to iyt
     *  @param redirectedHost The host to redirect to
     */
    public IYTGameFilter(GameStorer gameStorer,
                         PlayerStorer playerStorer,
                         PlayerData sessionPlayerData,
                         Hashtable params,
                         Hashtable cookies,
                         String redirectedHost) {

        this(null, gameStorer, playerStorer, sessionPlayerData, params, cookies, redirectedHost);
    }

    /** Use this constructor if this is not the first filter in the chain
     *  @param gameStorer The game storer
     *  @param playerStorer The player storer
     *  @param sessionPlayerData The player data for the player requesting the game
     *  @param params The parameters used to connect to iyt
     *  @param cookies The cookies used to connect to iyt
     *  @param redirectedHost The host to redirect to
     */
    public IYTGameFilter(LineFilter prevFilter,
                         GameStorer gameStorer,
                         PlayerStorer playerStorer,
                         PlayerData sessionPlayerData,
                         Hashtable params,
                         Hashtable cookies,
                         String redirectedHost) {

        this.prevFilter = prevFilter;
        this.gameStorer = gameStorer;
        this.playerStorer = playerStorer;
        this.sessionPlayerData = sessionPlayerData;
        this.params = params;
        this.cookies = cookies;
        this.redirectedHost = redirectedHost;

        coordinateLines = new Vector();

        boardSize = new Dimension(19, 19);
        gameData = new IYTGameData();

        String game = (String) params.get(IYTConstants.GAME_PARAMETER);
        if (game == null) {
            game = (String) params.get(IYTConstants.OLD_GAME_PARAMETER);
        }
        if (game != null) {
            gameData.setGameID(Long.parseLong(game));
        }
    }

    /** Gets the filtered game data, but only if it filtered ok
     *  @return GameData
     */
    public GameData getGameData() {
        if (!gameFound) return null;
        return gameData;
    }

    /** Filter all the info for a game
     *  @param line The line to filter
     *  @return String The filtered line
     */
    public String filterLine(String line) {

        // if there is a previous filter, allow it to filter before
        // doing any other filtering.
        if (prevFilter != null) {
            line = prevFilter.filterLine(line);
        }

        // if the line is null don't filter
        if (line == null) {
            return line;
        }

//System.out.println(line);

        switch (filterState) {

            case PROCESSING_BOARD:

                if (line.indexOf(GAME_NAME_FIELD) == -1) break;
///iyt.dll?a&g=15300015520869&u=15200000710511&t=1&gn=42
///iyt.dll?a?game=15300015520869&stage=7&
                // get the game name
                if (gameName == null) {
                    gameName = getInfo(line, GAME_NAME_FIELD, GAME_NAME_TAG);
                    if (gameName != null) {

                        // if game is supported, set the game name
                        if (supportedGames.contains(gameName)) {
                            gameData.setGame("Pente");
                        }
                        // else stop filtering
                        else {
                            filterState = DONE;
                            return line;
                        }
                    }
                }

                // get the game event
                if (event == null) {
                    event = getInfo(line, EVENT_FIELD, EVENT_TAG);
                    if (event != null) {
                        gameData.setEvent(event);

                        if (event.indexOf("Main") != -1) {
                            gameData.setInitialTime(48);
                            gameData.setIncrementalTime(48);
                        } else if (event.indexOf("Fast") != -1) {
                            gameData.setInitialTime(28);
                            gameData.setIncrementalTime(28);
                        }
                    }
                }

                // get the round
                if (round == null) {
                    round = getInfo(line, ROUND_FIELD, ROUND_TAG);
                    if (round != null) {
                        gameData.setRound(round);
                    }
                }

                // get the section
                if (section == null) {
                    section = getInfo(line, SECTION_FIELD, SECTION_TAG);
                    if (section != null) {
                        gameData.setSection(section);
                    }
                }

                // get the winner
                if (winner == null) {
                    winner = getInfo(line, WINNER_FIELD, WINNER_TAG);

                    if (winner != null) {
                        if (winner.equals(IYTConstants.PLAYER1)) {
                            gameData.setWinner(GameData.PLAYER1);
                        } else {
                            gameData.setWinner(GameData.PLAYER2);
                        }
                    }
                }

                // get the players data
                getPlayers(line);

                // get the date
                if (date == null) {
                    String dateLine = getInfo(line, DATE_FIELD, DATE_TAG);
                    if (dateLine != null) {
                        int dateIndex = dateLine.indexOf(DATE_FIELD);

                        dateIndex += DATE_FIELD.length() + DATE_MAGIC_OFFSET;
                        date = dateLine.substring(dateIndex, dateIndex + 17);

                        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy-hh:mm:ss");
                        Date gameDate = null;
                        try {
                            gameDate = dateFormat.parse(date);
                            Calendar cal = new GregorianCalendar();
                            cal.setTime(gameDate);
                            cal.set(Calendar.YEAR, cal.get(Calendar.YEAR));
                            gameDate = cal.getTime();
                        } catch (ParseException ex) {
                            System.err.println("Error parsing date " + date);
                        }
                        gameData.setDate(gameDate);

                    }
                }

                filterState = SEARCHING_FOR_MOVES;
                break;

            case SEARCHING_FOR_MOVES:

                int numMovesIndex = line.indexOf("navallmovenum = ");
                if (numMovesIndex >= 0) {
                    numMovesIndex += 16;
                    int endMovesIndex = line.indexOf(";", numMovesIndex);
                    if (endMovesIndex >= 0) {

                        String moveStr = line.substring(numMovesIndex, endMovesIndex).trim();
                        moves = Integer.parseInt(moveStr);
                    }

                    // have all we need except the moves
                    if (moves == 0) {
                        if (gameData.getWinner() == GameData.PLAYER1) {
                            gameData.addMove(180);
                        }
                        loadGame(gameData.getNumMoves());
                    } else {

                        loadGame(moves);
                    }
                    if (gameData.getNumMoves() == moves) {
                        gameFound = true;
                    } else {
                        line = "<br>Error loading moves<br>Please try again<br>";
                    }
                    line = "";
                    filterState = DONE;
                }

                break;

            default:
                break;
        }

        return line;
    }

    /** Gets the player data for both players from a line
     *  @param line The String to get the player info from
     */
    private void getPlayers(String line) {

        if (playerType == null) {
            playerType = getInfo(line, PLAYER_TYPE_FIELD, PLAYER_TYPE_TAG);
        }

        if (playerType != null) {

            int playerTypeIndex = line.indexOf(PLAYER_TYPE_FIELD);

            if (myPlayerData == null) {
                myPlayerData = getPlayer(line, 0, playerTypeIndex);
            }
            if (opponentPlayerData == null) {
                opponentPlayerData = getPlayer(line, playerTypeIndex, line.length());
            }
        }
    }

    /** Gets the player data for a player from a line
     *  @param line The String to get the player info from
     *  @param begin The begin index of the line to search
     *  @param end The end index of the line to search
     */
    private PlayerData getPlayer(String line, int begin, int end) {

        PlayerData playerData = null;

        String searchString = IYTConstants.USER_PROFILE_REQUEST + "?" + IYTConstants.USERID_PARAMETER + "=";
        int nameIndex = line.indexOf(searchString,
                begin);

        if (nameIndex >= 0 && nameIndex < end) {

            int startPID = nameIndex + searchString.length();
            int endPID = line.indexOf(">", startPID);
            if (endPID >= 0) {
                playerData = new DefaultPlayerData();
                playerData.setType(PlayerData.HUMAN);
                playerData.setUserID(Long.parseLong(line.substring(startPID, endPID)));
            }
        }

        return playerData;
    }

    /** This does the actual loading of the game data either by getting it from
     *  the game storer or with a IYTMovesBuilder.
     *  @param numMoves The number of moves in the game
     */
    private void loadGame(int numMoves) {

        try {
            boolean gameLoaded = false;

            // if the game is already stored, get the game from storage
            // this also gets player info, so we don't need to explicitly do it
            if (gameStorer.gameAlreadyStored(gameData.getGameID())) {

                // the winner and date are the 2 fields that can change
                // in the games data (and moves) so override the data from the db
                int pageWinner = gameData.getWinner();
                Date pageDate = gameData.getDate();

                try {
                    gameData = gameStorer.loadGame(gameData.getGameID(), gameData);
                    if (gameData != null) {
                        gameLoaded = true;

                        // override db values from page values
                        gameData.setWinner(pageWinner);
                        if (pageDate != null) {
                            gameData.setDate(pageDate);
                        }
                    }

                    // if the game can't be loaded and throws an exception
                    // then continue loading it from iyt below
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            if (!gameLoaded) {

                // if my player data is null, get player data from session
                if (myPlayerData == null) {
                    myPlayerData = sessionPlayerData;
                }

                // check, the player might already be in the database
                if (playerStorer.playerAlreadyStored(myPlayerData.getUserID(), IYTConstants.SITE_NAME)) {
                    myPlayerData = playerStorer.loadPlayer(myPlayerData.getUserID(), IYTConstants.SITE_NAME);
                }
                // else try to get the player data from iyt
                else {
                    IYTUserProfileBuilder userProfileBuilder = new IYTUserProfileBuilder(myPlayerData, cookies);
                    userProfileBuilder.run();
                }

                // check, the player might already be in the database
                if (playerStorer.playerAlreadyStored(opponentPlayerData.getUserID(), IYTConstants.SITE_NAME)) {
                    opponentPlayerData = playerStorer.loadPlayer(opponentPlayerData.getUserID(), IYTConstants.SITE_NAME);
                }
                // else try to get the player data from iyt
                else {
                    IYTUserProfileBuilder userProfileBuilder = new IYTUserProfileBuilder(opponentPlayerData, cookies);
                    userProfileBuilder.run();
                }

                // set up game data with player data
                if (playerType.equals(IYTConstants.PLAYER1)) {
                    gameData.setPlayer1Data(myPlayerData);
                    gameData.setPlayer2Data(opponentPlayerData);
                } else {
                    gameData.setPlayer1Data(opponentPlayerData);
                    gameData.setPlayer2Data(myPlayerData);
                }

                if (gameData.getEvent() == null) {
                    gameData.setEvent("Non-Tournament Game");
                    gameData.setRated(false);
                    gameData.setTimed(false);
                }
            }

            // now attempt to build the game
            IYTMovesBuilder movesBuilder = new IYTSimpleMovesBuilder(params, cookies, gameData, numMoves);
            movesBuilder.buildMoves();

            if (gameData.getNumMoves() == numMoves) {
                gameStorer.storeGame(gameData);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            gameData = new IYTGameData();

        }
    }


    /** Looks for information in a line of html
     *  @param line The line to look in
     *  @param name A string that precedes the info we are looking for
     *  @param enclosingTag After we find the 'name', the info we are
     *  looking for should be inside a html tag, like <b>info</b>
     *  @return String The info we are looking for, null if not found
     */
    private String getInfo(String line, String name, String enclosingTag) {

        String lineLower = line.toLowerCase();

        int index = line.indexOf(name);
        if (index < 0) return null;

        if (enclosingTag == null) {
            return line;
        }

        int tag_begin = lineLower.indexOf("<" + enclosingTag, index + 1);
        if (tag_begin < 0) return null;

        tag_begin = lineLower.indexOf(">", tag_begin + 1);
        if (tag_begin < 0) return null;
        tag_begin++;

        int tag_end = lineLower.indexOf("</" + enclosingTag + ">", tag_begin + 1);
        if (tag_end < 0) return null;

        return line.substring(tag_begin, tag_end).trim();
    }
}