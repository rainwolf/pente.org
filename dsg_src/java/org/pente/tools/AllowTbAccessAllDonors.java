package org.pente.tools;

import java.util.*;

import org.apache.log4j.BasicConfigurator;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GameVenueStorer;
import org.pente.game.MySQLGameVenueStorer;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.DSGPreferenceEvent;

public class AllowTbAccessAllDonors {


    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
                args[0], args[1], args[2], args[3]);

        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(dbHandler,
                gameVenueStorer);

        DSGPlayerPreference pref =
                new DSGPlayerPreference("tb", new Boolean(true));
        for (Iterator d = dsgPlayerStorer.getAllPlayersWhoDonated().iterator();
             d.hasNext(); ) {
            SimpleDSGDonationData s = (SimpleDSGDonationData) d.next();
            dsgPlayerStorer.storePlayerPreference(s.getPid(), pref);
        }
    }

}
