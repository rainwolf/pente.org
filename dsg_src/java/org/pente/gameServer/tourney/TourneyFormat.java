package org.pente.gameServer.tourney;

import org.pente.gameServer.core.DSGPlayerStorer;

import java.util.List;

public interface TourneyFormat {

    public String getName();

    public TourneyRound createFirstRound(List<TourneyPlayerData> players, Tourney tourney);

    public TourneyRound createNextRound(Tourney tourney, DSGPlayerStorer dsgPlayerStorer);

    public boolean isTourneyComplete(Tourney tourney);
}
