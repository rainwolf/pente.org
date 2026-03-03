package org.pente.gameServer.mobile;

import org.pente.gameServer.core.*;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.Hill;
import org.pente.kingOfTheHill.Player;
import org.pente.kingOfTheHill.Step;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Serializes the King-of-the-Hill ladder for a single game (koth.jsp).
 * Gson serializes {@code List<List<PlayerEntry>>} to a JSON array of step arrays.
 */
public class KothResponse {

    public static class PlayerEntry {
        public final String name;
        public final int rating;
        public final boolean canChallenge;
        public final int color;
        public final int tourneyWinner;
        public final String lastGame;

        PlayerEntry(String name, int rating, boolean canChallenge,
                    int color, int tourneyWinner, String lastGame) {
            this.name = name;
            this.rating = rating;
            this.canChallenge = canChallenge;
            this.color = color;
            this.tourneyWinner = tourneyWinner;
            this.lastGame = lastGame;
        }
    }

    /**
     * Builds the full hill ladder as a list of steps, each step being a list of players.
     * Returns an empty list if the hill is null or empty.
     *
     * @param hill       the KotH hill for the requested game
     * @param game       game ID
     * @param myPid      the requesting player's PID
     * @param myData     the requesting player's data
     * @param storer     player storer for loading opponent data
     * @param kothStorer KotH storer for challenge eligibility checks
     */
    public static List<List<PlayerEntry>> build(Hill hill, int game, long myPid,
                                                DSGPlayerData myData,
                                                DSGPlayerStorer storer,
                                                CacheKOTHStorer kothStorer) throws DSGPlayerStoreException {
        List<List<PlayerEntry>> result = new ArrayList<>();
        if (hill == null || hill.getSteps().isEmpty()) {
            return result;
        }

        boolean canIchallenge = hill.hasPlayer(myPid);
        if (game > 50 && !myData.hasPlayerDonated()) {
            canIchallenge = canIchallenge && kothStorer.canPlayerBeChallenged(game, myPid);
        }
        int myStep = canIchallenge ? hill.myStep(myPid) : -1;
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

        List<Step> steps = hill.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            final int stepIndex = i;
            List<Player> players = steps.get(i).getPlayers();
            Collections.sort(players, (o1, o2) -> o2.getLastGame().compareTo(o1.getLastGame()));

            List<PlayerEntry> stepEntries = new ArrayList<>();
            for (Player player : players) {
                long pid = player.getPid();
                DSGPlayerData d = storer.loadPlayer(pid);
                DSGPlayerGameData gameData = d.getPlayerGameData(game);

                boolean withinRange = canIchallenge && myPid != pid
                        && (myStep - stepIndex) * (myStep - stepIndex) < 5;
                boolean canChallengeThem = false;
                if (withinRange) {
                    if (game > 50) {
                        boolean iAmIgnored = false;
                        for (DSGIgnoreData id : storer.getIgnoreData(pid)) {
                            if (id.getIgnorePid() == myPid && id.getIgnoreInvite()) {
                                iAmIgnored = true;
                                break;
                            }
                        }
                        canChallengeThem = !iAmIgnored && kothStorer.canPlayerBeChallenged(game, pid);
                    } else {
                        canChallengeThem = true;
                    }
                }

                stepEntries.add(new PlayerEntry(
                        d.getName(),
                        MobileJsonHelper.playerRating(gameData),
                        withinRange && canChallengeThem,
                        MobileJsonHelper.playerColor(d),
                        d.getTourneyWinner(),
                        dateFormat.format(player.getLastGame())
                ));
            }
            result.add(stepEntries);
        }
        return result;
    }
}