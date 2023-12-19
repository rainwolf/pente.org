package org.pente.filter.iyt;

import java.util.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.filter.iyt.game.*;
import org.pente.filter.*;
import org.pente.filter.http.*;

public class IYTPlayerGamesBuilder implements Runnable {

    private Vector<String> games;

    private String playerID;
    private Hashtable<String, String> cookies;

    /**
     * @param args <player id> <iyt cookie> <db user> <db pass>
     *             <db name> <db host>
     * @throws Throwable
     */
    public static void main(String args[]) throws Throwable {

        BasicConfigurator.configure();

        String playerID = args[0];
        Hashtable<String, String> cookies = new Hashtable<>();
        cookies.put(IYTConstants.USERID_COOKIE, args[1]);

        GameStorer gameStorer = null;
        PlayerStorer playerStorer = null;

        DBHandler dbHandler = new MySQLDBHandler(
                args[2], args[3], args[4], args[5]);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(
                dbHandler);
        gameStorer = new MySQLPenteGameStorer(dbHandler, gameVenueStorer);
        playerStorer = (PlayerStorer) gameStorer;

        PlayerData myPlayerData = new DefaultPlayerData();
        myPlayerData.setUserID(Long.parseLong(args[1].substring(0, 14)));

        IYTPlayerGamesBuilder builder = new IYTPlayerGamesBuilder(playerID,
                cookies);
        builder.run();
        Vector<String> games = builder.getGames();

        for (Iterator<String> it = builder.getGames().iterator(); it.hasNext(); ) {
            String game = it.next();
            System.out.println("Game: " + game);
        }

        // run through all games twice, to catch any straglers...
        for (int j = 0; j < 2; j++) {

            // for each game, build the game and store it
            for (int i = 0; i < games.size(); i++) {

                try {

                    String gameIDStr = (String) games.elementAt(i);
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

        if (gameStorer != null) {
            gameStorer.destroy();
        }
        if (playerStorer != null) {
            playerStorer.destroy();
        }
    }

    public IYTPlayerGamesBuilder(String playerID, Hashtable<String, String> cookies) {
        this.playerID = playerID;
        this.cookies = cookies;

        games = new Vector<>();
    }

    /**
     * Gets the game ids filtered
     *
     * @return Vector The game ids
     */
    public Vector<String> getGames() {
        return games;
    }

    /**
     * Creates an IYTTournamentGamesFilter and uses it with a HttpFilterController
     * to filter out game ids
     */
    public void run() {
//http://www.itsyourturn.com/iyt.dll?completedgame?userid=15200000098568&gametype=42&tn=0
        Hashtable<String, String> params = new Hashtable<>();
        params.put("gametype", "42");
        params.put("tn", "0");
        params.put(IYTConstants.USERID_PARAMETER, playerID);

        IYTTournamentGamesFilter filter = new IYTTournamentGamesFilter(games);

        FilterController httpFilterController = new HttpFilterController("GET",
                IYTConstants.HOST,
                IYTConstants.COMPLETE_GAMES_REQUEST,
                params,
                cookies,
                filter);
        httpFilterController.run();
    }
}
