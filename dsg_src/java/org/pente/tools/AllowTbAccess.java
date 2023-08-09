package org.pente.tools;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.DSGPreferenceEvent;

public class AllowTbAccess {


    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
                args[0], args[1], args[2], args[3]);

        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(dbHandler,
                gameVenueStorer);

        DSGPlayerPreference pref = new DSGPlayerPreference("pentedb", new Boolean(true));
        dsgPlayerStorer.storePlayerPreference(22000000000815L, pref);

    }

}
