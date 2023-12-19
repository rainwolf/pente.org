/**
 * IYTSpider.java
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
import java.io.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.filter.iyt.game.*;

/** This is a sample class that shows how you could create an iyt spider
 *  to store all games in a tourament.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTSpider {

    public static void main(String args[]) throws Throwable {

        BasicConfigurator.configure();

        if (args.length < 3) {
            System.err.println("usage: IYTSpider <tournament id nbr> <cookie> " +
                    "<db user> <db passwd> <db name> <db host>");
            System.exit(-1);
        }

        Hashtable<String, String> cookies = new Hashtable<>();
        cookies.put(IYTConstants.USERID_COOKIE, args[1]);

        int id = Integer.parseInt(args[0]);
        int gameTypeID = 42;

        GameStorer gameStorer = null;
        PlayerStorer playerStorer = null;

        DBHandler dbHandler = new MySQLDBHandler(args[2], args[3], args[4], args[5]);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
        //gameStorer = new HttpGameStorer("localhost", 8080, new PGNGameFormat(), "/dsg");
        playerStorer = (PlayerStorer) gameStorer;

        PlayerData myPlayerData = new DefaultPlayerData();
        myPlayerData.setUserID(Long.parseLong(args[1].substring(0, 14)));


        // loop incrementing tournamentID
        for (int tournamentID = id; tournamentID < 409; tournamentID++) {

            Vector<String> games = new Vector<>();

            File dir = new File("/dsg_dev/data/tournament");
            File file2 = new File(dir, Integer.toString(tournamentID) + ".txt");
            if (file2.exists()) {

                BufferedReader bufferedReader = new BufferedReader(new FileReader(file2));
                String line = null;
                while (true) {

                    line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }

                    games.addElement(line);
                }

                bufferedReader.close();
            } else {

                // for each round in the tournament
                for (int i = 1; i < 6; i++) {

                    // get the players in the round
                    IYTTournamentPlayersBuilder playersBuilder = new IYTTournamentPlayersBuilder(tournamentID, gameTypeID, i, cookies);
                    playersBuilder.run();

                    // for each player in the round, get the games the player played in
                    // into the games vector
                    Vector<String> players = playersBuilder.getPlayers();
                    for (int j = 0; j < players.size(); j++) {
                        String playerID = (String) players.elementAt(j);
                        IYTTournamentGamesBuilder gamesBuilder = new IYTTournamentGamesBuilder(tournamentID, gameTypeID, i, playerID, cookies, games);
                        gamesBuilder.run();
                    }
                }

                FileOutputStream fileStream = new FileOutputStream(file2);
                for (int i = 0; i < games.size(); i++) {
                    String gameIDStr = games.elementAt(i);
                    fileStream.write(gameIDStr.getBytes());
                    fileStream.write('\r');
                    fileStream.write('\n');
                }
                fileStream.close();
            }


            // run through all games twice, to catch any straglers...
            for (int j = 0; j < 2; j++) {

                // for each game, build the game and store it
                for (int i = 0; i < games.size(); i++) {

                    try {

                        String gameIDStr = games.elementAt(i);
                        long gameID = Long.parseLong(gameIDStr);

                        boolean buildGame = false;

                        if (gameStorer.gameAlreadyStored(gameID)) {
                            GameData storedData = new IYTGameData();
                            storedData = gameStorer.loadGame(gameID, storedData);
                            if (storedData.getWinner() == GameData.UNKNOWN) {
                                buildGame = true;
                            }
                        } else {
                            buildGame = true;
                        }

                        if (buildGame) {

                            IYTGameBuilder gameBuilder = new IYTGameBuilder(gameIDStr,
                                    IYTConstants.GAME_REQUEST,
                                    gameStorer,
                                    playerStorer,
                                    myPlayerData,
                                    cookies,
                                    "");
                            gameBuilder.run();
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace(System.err);
                    }
                }
            }
        }

        if (gameStorer != null) {
            gameStorer.destroy();
        }
        if (playerStorer != null) {
            playerStorer.destroy();
        }
    }
}