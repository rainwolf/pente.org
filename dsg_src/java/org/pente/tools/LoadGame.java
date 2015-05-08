package org.pente.tools;

import java.sql.*;
import java.util.*;

import org.apache.log4j.*;
import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameDatabase.*;

public class LoadGame {

	public static void main(String[] args) throws Throwable {
        BasicConfigurator.configure();
        
        DBHandler dbHandler = null;
        GameStorer gameStorer = null;
        GameVenueStorer vs = null;


        try {
        	//String user, String password, String db, String host
            dbHandler = new MySQLDBHandler(
				args[0], args[1], args[2], args[3]);
			vs = new MySQLGameVenueStorer(dbHandler);
            
			gameStorer = new MySQLPenteGameStorer(dbHandler, vs);
			
			GameData g = gameStorer.loadGame(34194139747898L, null);

			System.out.println(g.getNumMoves());
			
			GameStorerSearcher gs = new MySQLGameStorerSearcher(
				dbHandler, gameStorer, vs);
			GameStorerSearchRequestData req = new SimpleGameStorerSearchRequestData();
			req.setGameStorerSearchResponseFormat("org.pente.gameDatabase.SimpleGameStorerSearchResponseFormat");
			GameStorerSearchRequestFilterData filt = new SimpleGameStorerSearchRequestFilterData();
			filt.setEvent("D-Pente - Fall 2005");
			filt.setGame(GridStateFactory.DPENTE);
			filt.setRound("4");
			filt.setSection("1");
			filt.setSite("Pente.org");
			filt.setStartGameNum(0);
			filt.setEndGameNum(10);
			req.addMove(180);
			req.setGameStorerSearchRequestFilterData(filt);
			GameStorerSearchResponseData res = new SimpleGameStorerSearchResponseData();
			gs.search(req, res);
			
        }  finally {

	        if (dbHandler != null) {
	            dbHandler.destroy();
	        }
	    }
	}
}
