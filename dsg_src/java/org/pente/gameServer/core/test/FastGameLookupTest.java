package org.pente.gameServer.core.test;

import java.util.*;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.*;
import org.pente.gameServer.core.*;

public class FastGameLookupTest {


    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
                args[0], args[1], args[2], args[3]);

        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        FastMySQLDSGGameLookup lookup = new FastMySQLDSGGameLookup(dbHandler,
                gameVenueStorer);

        List<GameData> games = lookup.search(
                "dweebo", 22000000000002L, 0, 22000000000002L, 1, 0, 100);
        for (GameData d : games) {
            System.out.println(d.getGameID() + ": " + d.getPlayer1Data().getUserIDName() +
                    " vs. " + d.getPlayer2Data().getUserIDName() + ", " + d.getWinner());
        }
    }

}
