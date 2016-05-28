package org.pente.gameServer.tourney;

import java.util.List;

public interface TourneyFormat {

    public String getName();

    public TourneyRound createFirstRound(List<TourneyPlayerData> players, Tourney tourney);
    public TourneyRound createNextRound(Tourney tourney);
	
	public boolean isTourneyComplete(Tourney tourney);
}
