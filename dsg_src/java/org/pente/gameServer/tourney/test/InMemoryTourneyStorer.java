package org.pente.gameServer.tourney.test;

import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyListener;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;
import org.pente.gameServer.tourney.TourneyRound;
import org.pente.gameServer.tourney.TourneyStorer;
import org.pente.turnBased.test.RedisConnectionManagerCloneHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal in-memory TourneyStorer used as the backing store in
 * CacheTourneyStorer Redis-migration tests.
 *
 * Real behaviour ONLY for the data paths the migration cares about:
 *  - insertTourney / getTourney / getTourneyDetails (deep-copied by eid)
 *  - updateMatch (records deep copies, exposed via getUpdatedMatches())
 *  - addPlayerToTourney / removePlayerFromTourney / getTourneyPlayerPids
 * Every other interface method is a harmless no-op default.
 *
 * Deep copies go through RedisConnectionManagerCloneHelper.clone so the backing
 * store hands out independent instances, mimicking a real datastore.
 */
public class InMemoryTourneyStorer implements TourneyStorer {

    private final Map<Integer, Tourney> tournies = new HashMap<Integer, Tourney>();
    private final Map<Integer, List<Long>> playerPids = new HashMap<Integer, List<Long>>();
    private final List<TourneyMatch> updatedMatches = new ArrayList<TourneyMatch>();

    private static Tourney copy(Tourney t) {
        return t == null ? null : (Tourney) RedisConnectionManagerCloneHelper.clone(t);
    }

    // ---- real behaviour ----

    public void insertTourney(Tourney t) throws Throwable {
        tournies.put(t.getEventID(), copy(t));
    }

    public Tourney getTourney(int eid) throws Throwable {
        return copy(tournies.get(eid));
    }

    public Tourney getTourneyDetails(int eid) throws Throwable {
        return copy(tournies.get(eid));
    }

    public void updateMatch(TourneyMatch m) throws Throwable {
        updatedMatches.add((TourneyMatch) RedisConnectionManagerCloneHelper.clone(m));
    }

    public void addPlayerToTourney(long pid, int eid) throws Throwable {
        List<Long> l = playerPids.get(eid);
        if (l == null) {
            l = new ArrayList<Long>();
            playerPids.put(eid, l);
        }
        if (!l.contains(pid)) {
            l.add(pid);
        }
    }

    public void removePlayerFromTourney(long pid, int eid) throws Throwable {
        List<Long> l = playerPids.get(eid);
        if (l != null) {
            l.remove(Long.valueOf(pid));
        }
    }

    public List<Long> getTourneyPlayerPids(int eid) throws Throwable {
        List<Long> l = playerPids.get(eid);
        return l == null ? new ArrayList<Long>() : new ArrayList<Long>(l);
    }

    /** Test accessor: deep copies of every match passed to updateMatch. */
    public List<TourneyMatch> getUpdatedMatches() {
        return updatedMatches;
    }

    // ---- no-op defaults ----

    public List<Tourney> getUpcomingTournies() throws Throwable {
        return new ArrayList<Tourney>();
    }

    public List<Tourney> getCurrentTournies() throws Throwable {
        return new ArrayList<Tourney>();
    }

    public List<Tourney> getCompletedTournies() throws Throwable {
        return new ArrayList<Tourney>();
    }

    public TourneyMatch getUnplayedMatch(long player1ID, long player2ID, int eid)
            throws Throwable {
        return null;
    }

    public void insertRound(TourneyRound round) throws Throwable {
    }

    public void insertMatch(TourneyMatch tourneyMatch) throws Throwable {
    }

    public void updateMatches(List tourneyMatches, Tourney t) throws Throwable {
    }

    public void completeTourney(Tourney tourney) throws Throwable {
    }

    public List<TourneyPlayerData> getTourneyPlayers(int eid) throws Throwable {
        return new ArrayList<TourneyPlayerData>();
    }

    public List<TourneyPlayerData> setInitialSeeds(int eid) throws Throwable {
        return new ArrayList<TourneyPlayerData>();
    }

    public void addTourneyListener(TourneyListener listener) {
    }

    public void removeTourneyListener(TourneyListener listener) {
    }

    public void assignCrown(int eid, int game, long pid, int crown) throws Throwable {
    }

    public void removeCrown(int eid, int game, long pid, int crown) throws Throwable {
    }

    public void cancelTourney(int eid) throws Throwable {
    }

    public String findNextTournamentName(String baseName) throws Throwable {
        return null;
    }
}
