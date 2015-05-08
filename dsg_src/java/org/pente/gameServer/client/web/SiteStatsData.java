
package org.pente.gameServer.client.web;

import org.pente.gameDatabase.*;
import org.pente.gameServer.server.*;

public class SiteStatsData {

	private ServerStatsHandler serverStatsHandler;
	private GameStats gameStats;
	
	public SiteStatsData(
		ServerStatsHandler serverStatsHandler,
		GameStats gameStats) {

	    this.serverStatsHandler = serverStatsHandler;
	    this.gameStats = gameStats;
	}

	public int getNumPlayers() {
        return gameStats.getNumDSGPlayers();
	}
	public int getNumGames() {
	    return gameStats.getNumDSGGames();
	}
	public int getNumCurrentPlayers() {
        return serverStatsHandler.getCurrentPlayers();
	}
	public int getNumTbGames() {
		return gameStats.getNumTbGames();
	}
}
