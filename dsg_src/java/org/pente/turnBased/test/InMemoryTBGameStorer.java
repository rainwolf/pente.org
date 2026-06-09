package org.pente.turnBased.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBGameStorer;
import org.pente.turnBased.TBMessage;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;
import org.pente.turnBased.TBVacation;

/**
 * In-memory implementation of {@link TBGameStorer} used as a base storer in
 * CacheTBStorer tests. Deep-copies sets on store and on load so it mirrors the
 * serialize/deserialize semantics of the Redis-backed storer, letting tests
 * catch identity bugs.
 */
public class InMemoryTBGameStorer implements TBGameStorer {

    private final Map<Long, TBSet> setsById = new HashMap<Long, TBSet>();
    private final Map<Long, Long> gidToSid = new HashMap<Long, Long>();
    private final Map<Long, List<Integer>> storedMoveNums =
            new HashMap<Long, List<Integer>>();
    private long nextGid = 1L;

    /** Move numbers passed to storeNewMove per gid, in call order (for tests). */
    public synchronized List<Integer> getStoredMoveNums(long gid) {
        List<Integer> l = storedMoveNums.get(gid);
        return l == null ? new ArrayList<Integer>() : new ArrayList<Integer>(l);
    }

    private static TBSet copy(TBSet set) {
        if (set == null) {
            return null;
        }
        return (TBSet) RedisConnectionManagerCloneHelper.clone(set);
    }

    public int getEventId(int game) throws TBStoreException {
        return 0;
    }

    public void createSet(TBSet set) throws TBStoreException {
        // Assign gids to any games whose gid == 0, on the passed-in set so the
        // caller can read the generated gids back.
        TBGame[] games = set.getGames();
        for (int i = 0; i < games.length; i++) {
            TBGame g = games[i];
            if (g != null) {
                if (g.getGid() == 0) {
                    g.setGid(nextGid++);
                }
                gidToSid.put(g.getGid(), set.getSetId());
            }
        }
        // Store a deep copy keyed by the explicit setId.
        setsById.put(set.getSetId(), copy(set));
    }

    public void createGame(TBGame game) throws TBStoreException {
        // no-op
    }

    public TBSet loadSet(long setId) throws TBStoreException {
        return copy(setsById.get(setId));
    }

    public TBSet loadSetByGid(long gid) throws TBStoreException {
        Long sid = gidToSid.get(gid);
        if (sid == null) {
            return null;
        }
        return copy(setsById.get(sid));
    }

    public TBGame loadGame(long gid) throws TBStoreException {
        TBSet set = loadSetByGid(gid);
        if (set == null) {
            return null;
        }
        return set.getGame(gid);
    }

    public List<TBSet> loadGamesExpiringBefore(Date date) throws TBStoreException {
        return new ArrayList<TBSet>();
    }

    public List<TBSet> loadWaitingSets() throws TBStoreException {
        return new ArrayList<TBSet>();
    }

    public int getNumGamesMyTurn(long pid) throws TBStoreException {
        return 0;
    }

    public List<TBSet> loadSets(long pid) throws TBStoreException {
        return new ArrayList<TBSet>();
    }

    public synchronized void storeNewMove(long gid, int moveNum, int move)
            throws TBStoreException {
        // Record the move number so tests can assert no tb_move PK collision.
        // Moves themselves persist via updateGameAfterMove in this mock.
        List<Integer> nums = storedMoveNums.get(gid);
        if (nums == null) {
            nums = new ArrayList<Integer>();
            storedMoveNums.put(gid, nums);
        }
        nums.add(moveNum);
    }

    public void storeNewMessage(long gid, TBMessage message)
            throws TBStoreException {
        // no-op
    }

    public void updateGameAfterMove(TBGame game) throws TBStoreException {
        long sid = game.getSetId();
        TBSet stored = setsById.get(sid);
        if (stored == null) {
            Long mapped = gidToSid.get(game.getGid());
            if (mapped != null) {
                stored = setsById.get(mapped);
            }
        }
        if (stored == null) {
            return;
        }
        TBGame copyGame = (TBGame) RedisConnectionManagerCloneHelper.clone(game);
        TBGame[] games = stored.getGames();
        for (int i = 0; i < games.length; i++) {
            if (games[i] != null && games[i].getGid() == game.getGid()) {
                games[i] = copyGame;
                break;
            }
        }
    }

    public void setGameEventId(long gameId, long eventId) throws TBStoreException {
        // no-op
    }

    public void acceptInvite(TBSet set, long pid) throws TBStoreException {
        // no-op
    }

    public void cancelSet(TBSet set) throws TBStoreException {
        // no-op
    }

    public void resignGame(TBGame game) throws TBStoreException {
        // no-op
    }

    public void resignGame(TBGame game, long resigningPlayer) throws TBStoreException {
        // no-op
    }

    public void endSet(TBSet set) throws TBStoreException {
        // no-op
    }

    public void endGame(TBGame game) throws TBStoreException {
        // no-op
    }

    public void requestCancel(TBSet set, long requestorPid, String message)
            throws TBStoreException {
        // no-op
    }

    public void declineCancel(TBSet set) throws TBStoreException {
        // no-op
    }

    public void updateDPenteState(TBGame game, int state) throws TBStoreException {
        // no-op
    }

    public void dPenteSwap(TBGame game, boolean swap) throws TBStoreException {
        // no-op
    }

    public void swap2Pass(TBGame game) throws TBStoreException {
        // no-op
    }

    public void restoreGame(long gid) throws TBStoreException {
        // no-op
    }

    public TBVacation getTBVacation(long pid) {
        return null;
    }

    public void hideGame(long gid, byte hiddenBy) {
        // no-op
    }

    public void updateDaysOff(long pid, int[] weekend) throws TBStoreException {
        // no-op
    }

    public void destroy() {
        // no-op
    }
}
