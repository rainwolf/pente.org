package org.pente.turnBased;

import java.util.*;

public interface TBGameStorer {

	public int getEventId(int game) throws TBStoreException;
	
	public void createSet(TBSet set) throws TBStoreException;
	public void createGame(TBGame game) throws TBStoreException;
	public TBSet loadSet(long setId) throws TBStoreException;
	public TBSet loadSetByGid(long gid) throws TBStoreException;
	public TBGame loadGame(long gid) throws TBStoreException;
	public List<TBSet> loadGamesExpiringBefore(Date date) throws TBStoreException;
	public List<TBSet> loadWaitingSets() throws TBStoreException;
	public int getNumGamesMyTurn(long pid) throws TBStoreException;
	public List<TBSet> loadSets(long pid) throws TBStoreException;
	 
	public void storeNewMove(long gid, int moveNum, int move)
		throws TBStoreException;
	public void storeNewMessage(long gid, TBMessage message) 
		throws TBStoreException;
	public void updateGameAfterMove(TBGame game) throws TBStoreException;

	public void setGameEventId(long gameId, long eventId) throws TBStoreException;
	public void acceptInvite(TBSet set, long pid) throws TBStoreException;
	public void cancelSet(TBSet set) throws TBStoreException;
	public void resignGame(TBGame game) throws TBStoreException;
	public void endSet(TBSet set) throws TBStoreException;
	public void endGame(TBGame game) throws TBStoreException;
	
    public void requestCancel(TBSet set, long requestorPid, String message) throws TBStoreException;
    public void declineCancel(TBSet set) throws TBStoreException;
	public void updateDPenteState(TBGame game, int state)
		throws TBStoreException;
	public void dPenteSwap(TBGame game, boolean swap)
		throws TBStoreException;
	public void restoreGame(long gid) throws TBStoreException;
	public TBVacation getTBVacation(long pid);
	
	public void hideGame(long gid, byte hiddenBy);
	
	public void updateDaysOff(long pid, int weekend[]) throws TBStoreException;
	
	public void destroy();
}
