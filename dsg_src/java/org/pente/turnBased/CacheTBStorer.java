package org.pente.turnBased;

import java.util.*;
import java.text.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.message.*;
import org.pente.database.*;

import org.pente.gameServer.tourney.*;

import org.pente.kingOfTheHill.*;

import org.apache.log4j.*;
import org.pente.notifications.NotificationServer;

public class CacheTBStorer implements TBGameStorer, TourneyListener {

	private Category log4j = Category.getInstance(CacheTBStorer.class.getName());

	private DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	
	private TBGameStorer baseStorer;
	private DSGPlayerStorer dsgPlayerStorer;
	private GameStorer gameStorer;
	private DSGMessageStorer dsgMessageStorer;
	private TourneyStorer tourneyStorer;
	private CacheKOTHStorer kothStorer;
	private NotificationServer notificationServer;
	
	private Map<Long, TBVacation> vacationPerPlayer;

	/** used to cache event ids for tb-games */
	private Map<Integer, Integer> eidMap = new HashMap<Integer, Integer>();
	
	/** used for quick access by gid */
	private Map<Long, TBGame> gamesMap = new HashMap<Long, TBGame>();

	/** used for quick access by sid */
	private Map<Long, TBSet> setsMap = new HashMap<Long, TBSet>();
	
	/** cache of waiting games */
	/** might be some duplication with games above, but thats probably ok */
	private Set<TBSet> waitingSets = new TreeSet<TBSet>(new Comparator<TBSet>() {

		public int compare(TBSet s1, TBSet s2) {
			TBGame g1 = s1.getGame1();
			TBGame g2 = s2.getGame1();
			if (g1.getGid() == g2.getGid()) return 0;
			
			if (g1.getGame() != g2.getGame()) {
				return g2.getGame() - g1.getGame();
			}
			// if 2 games have same creation date, don't say they are equal
			// because then they won't be stored in set
			int comp = g2.getCreationDate().compareTo(g1.getCreationDate());
			if (comp == 0) {
				return (int) (g1.getGid() - g2.getGid());
			}
			else {
				return comp;
			}
		}
	});
	private boolean waitingSetsLoaded = false;

	/** used to cache game gids by pid */
	private Map<Long, List<Long>> setsByPid = new HashMap<Long, List<Long>>();
	
	
	private Object cacheTbLock = new Object();

	private Timer loadExpireSoonTimer;
    private Timer checkTimeoutTimer;
    private Timer stalePlayerTimer;
	private Thread endGameThread;
	private EndGameRunnable endGameRunnable = new EndGameRunnable();
	
	private CacheStats cacheStats = new CacheStats();
	
	
	/** these accessor functions provide more direct access to underlying data */
	public CacheStats getCacheStats() {
		return cacheStats;
	}
	public TBGame getGame(long gid) {
		synchronized (cacheTbLock) {
			return gamesMap.get(gid);
		}
	}
	public List<TBGame> getGames() {
		synchronized (cacheTbLock) {
			return new ArrayList<TBGame>(gamesMap.values());
		}
	}
	public List<TBSet> getSets() {
		synchronized (cacheTbLock) {
			return new ArrayList<TBSet>(setsMap.values());
		}
	}
	public List<Long> getCachedPids() {
		synchronized (cacheTbLock) {
			return new ArrayList<Long>(setsByPid.keySet());
		}
	}
	public List<TBSet> getSetsByPid(long pid) {
		synchronized (cacheTbLock) {
			List<Long> sids = setsByPid.get(pid);
			if (sids == null) {
				return new ArrayList<TBSet>();
			}
			List<TBSet> sets = new ArrayList<TBSet>(sids.size());
			for (Long l : sids) {
				TBSet s = setsMap.get(l);
				// since not synced since loaded sids, sets might have
				// been flushed from cache
				if (s != null) {
					sets.add(s);
				}
			}
			return sets;
		}
	}
	public List<String> getThreadStates() {
		List<String> l = new ArrayList<String>(3);
		l.add("TB-LoadExpireSoon " + loadExpireSoonTimer);
		l.add("TB-TimeoutCheck " + checkTimeoutTimer);
		
		String endGame = "alive=false";
		if (endGameThread != null) {
			endGame = "alive=" + endGameThread.isAlive();
		}
		if (endGameRunnable != null) {
			endGame += " " + endGameRunnable.toString();
		}
		l.add(endGame);

		return l;
	}

	public List<TBSet> getWaitingSets() {
		synchronized (cacheTbLock) {
			return new ArrayList<TBSet>(waitingSets);
		}
	}
	/** end accessors */

	/** methods for admin screens to clear caches in event of problems */

	public void uncacheGamesForPlayer(long pid) {

		synchronized (cacheTbLock) {
			setsByPid.remove(pid);
		}
	}
	public void uncacheAll() {

		log4j.debug("CacheTBStorer.uncacheAll()");
		synchronized (cacheTbLock) {
			gamesMap.clear();
			setsMap.clear();
			waitingSets.clear();
			waitingSetsLoaded = false;
			setsByPid.clear();
			
			vacationPerPlayer.clear();
			
			// restart threads
			restartTasks();
		}
	}

	public void undoLastMove(long gid, int numMoves) {
		synchronized (cacheTbLock) {
			TBGame tbGame = getGame(gid);
			if (tbGame.isUndoRequested()) {
				tbGame.setUndoRequested(false);
				((MySQLTBGameStorer)baseStorer).undoLastMove(gid);
			}
			for (int i = 0; i < numMoves; i++) {
				((MySQLTBGameStorer)baseStorer).undoLastMove(gid);
				tbGame.undoMove();
			}
			long newTimeout = Utilities.calculateNewTimeout(
					tbGame, dsgPlayerStorer);

			tbGame.setTimeoutDate(new Date(newTimeout));

			try {
				baseStorer.updateGameAfterMove(tbGame);
			} catch (TBStoreException e) {
				e.printStackTrace();
			}
		}
	}
	public void declineUndo(long gid) {
		synchronized (cacheTbLock) {
			TBGame tbGame = getGame(gid);
			if (tbGame.isUndoRequested()) {
				tbGame.setUndoRequested(false);
				((MySQLTBGameStorer)baseStorer).undoLastMove(gid);
			}
//			long newTimeout = Utilities.calculateNewTimeout(
//					tbGame, dsgPlayerStorer);
//
//			tbGame.setTimeoutDate(new Date(newTimeout));
//
//			try {
//				baseStorer.updateGameAfterMove(tbGame);
//			} catch (TBStoreException e) {
//				e.printStackTrace();
//			}
		}
	}

	public void requestUndo(long gid) {
		synchronized (cacheTbLock) {
			TBGame tbGame = getGame(gid);
			try {
				((MySQLTBGameStorer)baseStorer).storeNewMove(gid, tbGame.getNumMoves(), -1);
				tbGame.setUndoRequested(true);
//				long newTimeout = Utilities.calculateNewTimeout(
//						tbGame, dsgPlayerStorer);
//
//				tbGame.setTimeoutDate(new Date(newTimeout));

				baseStorer.updateGameAfterMove(tbGame);
			} catch (TBStoreException e) {
				e.printStackTrace();
			}
		}
	}

	public void hideGame(long gid, byte hiddenBy) {
		synchronized (cacheTbLock) {
			TBGame tbGame = getGame(gid);
			baseStorer.hideGame(gid, hiddenBy);
			tbGame.setHiddenBy(hiddenBy);
		}
	}


	public CacheTBStorer(TBGameStorer baseStorer, 
		DSGPlayerStorer dsgPlayerStorer, GameStorer gameStorer,
		DSGMessageStorer dsgMessageStorer, CacheKOTHStorer kothStorer) {
		this.baseStorer = baseStorer;
		this.dsgPlayerStorer = dsgPlayerStorer;
		this.gameStorer = gameStorer;
		this.dsgMessageStorer = dsgMessageStorer;
		this.kothStorer = kothStorer;
		
		this.vacationPerPlayer = new HashMap<>();
		
		startTasks();
	}
	
	private void restartTasks() {
		stopTasks();
		startTasks();
	}
	private void startTasks() {

		loadExpireSoonTimer = new Timer();
		loadExpireSoonTimer.scheduleAtFixedRate(
			new LoadExpireSoonRunnable(), 5000, 58 * 60 * 1000);
		checkTimeoutTimer = new Timer();
		checkTimeoutTimer.scheduleAtFixedRate(
			new TimeoutCheckRunnable(), 5000, 60000);
		stalePlayerTimer = new Timer();
		stalePlayerTimer.scheduleAtFixedRate(new RemoveStalePlayersInvitations(), 6000, 1000L*60*60);
		
		endGameRunnable.reset();
		endGameThread = new Thread(endGameRunnable);
		endGameThread.start();
	}
	private void stopTasks() {
		// not sure if cancel stops currently executing stuff, probably ok anyways
		if (loadExpireSoonTimer != null) {
			loadExpireSoonTimer.cancel();
			loadExpireSoonTimer = null;
		}
		if (checkTimeoutTimer != null) {
			checkTimeoutTimer.cancel();
			checkTimeoutTimer = null;
		}
		if (stalePlayerTimer != null) {
		    stalePlayerTimer.cancel();
		    stalePlayerTimer = null;
        }
		if (endGameThread != null && endGameRunnable != null) {
			endGameRunnable.kill();
			endGameThread.interrupt();
		}
	}
	
	
	public void destroy() {
		log4j.debug("CacheTBStorer.destroy()");
		stopTasks();
	}


    private class RemoveStalePlayersInvitations extends TimerTask {

        private static final int DELAY = 60;

        public String getName() {
            return "TB-RemoveStalePlayersInvitations";
        }
        public void run() {

            log4j.debug(getName() + " run");
            long now = System.currentTimeMillis();
            long oneWeekAgo = now -
                    1000L * 60 * 60 * 24 * 5;
            Date oneWeekAgoDate = new Date(oneWeekAgo);
            List<TBSet> sets = getWaitingSets();
            log4j.debug(getName() + " loaded " + sets.size() + " sets");
            synchronized (cacheTbLock) {
                for (TBSet s : sets) {
                    if (s.isWaitingSet()) {
                        if (s.getInviteePid() == 0) {
                            try {
                                DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(s.getInviterPid());
                                if (playerData.getLastLoginDate().before(oneWeekAgoDate)) {
                                    try {
                                        cancelSet(s);
                                    } catch (TBStoreException tse) {
                                        log4j.error("Error canceling set RemoveStalePlayersInvitations", tse);
                                    }
                                }
                            } catch (DSGPlayerStoreException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }


	// algorithm - load games that expire soon thread
	// goal, ensure all games that will expire, are in cache prior to check to
	// thread looking at them
	//
	// do forever
	//  synch (lock)
	//    check if server stilling running, if not, die
	//  end synch
	//  games = baseStorer.loadGamesExpiringBefore(1 hr from now)
	//  synch (lock)
	//    for each game loaded  from db
	//      if db.game.lastMoveDate <= cache.game.lastMoveDate
	//        do nothing, local copy just updated
	//      else
	//        update local cache
	//  end synch
	//
	//  sleep (less than 1 hr, but how much??)
	//
	private class LoadExpireSoonRunnable extends TimerTask {

		private static final int DELAY = 60;
		
		public String getName() {
			return "TB-LoadExpireSoon";
		}
		public void run() {

			log4j.debug(getName() + " run");
			try {
				long now = System.currentTimeMillis();
				long hourFromNow = now + 
					1000 * 60 * DELAY;
				Date d = new Date(hourFromNow);
				log4j.debug(getName() + " load games expiring before " +
					dateFormat.format(d));
				List<TBSet> sets = baseStorer.loadGamesExpiringBefore(d);
				log4j.debug(getName() + " loaded " + sets.size() + " sets");
				synchronized (cacheTbLock) {
					for (TBSet s : sets) {
						TBSet cachedSet = setsMap.get(s.getSetId());
						
						log4j.debug(getName() + " examine set " + s.getSetId());
						if (cachedSet == null) {
							cacheSet(s);
							log4j.debug(getName() + " cache set");
						}
					}
				}
				
			} catch (TBStoreException tse) {
				log4j.error("Error loading games to expire soon", tse);
				// try again soon, don't wait full hour?
			}
		}
	}
	
	// algorithm - check timeout thread
	// do forever
	//   synch (lock)
	//     check if server still running, if not, die
	//     for (int i = 0; i < games.size(); i++
	//       get currenttime in millis
	//       if game[i].timeouttime < currenttime
	//         change state of game to complete, end game in cache
	//         queue game to endgame thread for storing to db
	//       else break;
	//   end synch
	//   
	//   sleep one minute
	//
	
	private class TimeoutCheckRunnable extends TimerTask {
		
		public String getName() {
			return "TB-TimeoutCheck";
		}
		public void run() {
			log4j.debug(getName() + " run");
			
			List<TBGame> gs = null;
			synchronized (cacheTbLock) {
				gs = new ArrayList<TBGame>(gamesMap.values());
			}

			// this should probably be synced, and also
			// only look at games in sorted order until you find one that
			// is inactive or has NOT timed out
			long now = System.currentTimeMillis();

			Collections.sort(gs,  new Comparator<TBGame>() {
			    public int compare(TBGame o1, TBGame o2) {
		            return (int) (o1.getGid() - o2.getGid());
			    }
			});

			for (TBGame t : gs) {
	
				if (t.getState() == TBGame.STATE_ACTIVE &&
					t.getTimeoutDate() != null &&
				    t.getTimeoutDate().getTime() < now) {
					try {
						TBSet tSet = baseStorer.loadSetByGid(t.getGid());
						if (tSet.isTwoGameSet()) {
							TBGame otherGame = tSet.getOtherGame(t.getGid());
							if (otherGame != null) {
								if (gs.contains(otherGame) && (otherGame.getState() == TBGame.STATE_ACTIVE 
										&& otherGame.getTimeoutDate() != null 
										&& otherGame.getTimeoutDate().getTime() < now)) {
									if (t.getGid() < otherGame.getGid()) {
										continue;
									}
								}
							}
						}
					} catch (TBStoreException dpse) {
						log4j.error("TimeoutCheckRunnable: Error getting other game for " + t.getGid(), dpse);
					}
					log4j.debug("game t/o, " + t.getGid());
					long cp = t.getCurrentPlayer();
					boolean useVacation = true;
					try {
						if (getEventId(t.getGame()) != t.getEventId()) {
                            Tourney tourney = tourneyStorer.getTourney(t.getEventId());
                            if (tourney != null) {
                                int maxExtraDays = tourney.getIncrementalTime();
                                if (maxExtraDays == 0) {
                                    useVacation = false;
                                }
                            }
    
                        }
					} catch (Throwable throwable) {
						throwable.printStackTrace();
					}
					try {
						log4j.debug("game t/o, " + t.getGid() + " check floating vacationDays for " + cp);


						if (useVacation) {
							Date newTimeOutDate = newTimeout(cp);
							if (newTimeOutDate != null) {
								DSGPlayerData playerData = dsgPlayerStorer.loadPlayer(cp);
								int weekend[]=new int[] { 7, 1 }; //sat/sun default
								try {
									// get wk1, wk2 for player whose turn it now is
									List l = dsgPlayerStorer.loadPlayerPreferences(cp);
									for (Iterator it = l.iterator(); it.hasNext();) {
										DSGPlayerPreference p = (DSGPlayerPreference) it.next();
										if (p.getName().equals("weekend")) {
											weekend = (int[]) p.getValue();
										}
									}
								} catch (DSGPlayerStoreException dpse) {
									log4j.error("TimeoutCheckRunnable: Error getting weekend for player " +
											cp + ", game=" + t.getGid(), dpse);
								}
								Calendar newTimeout = Calendar.getInstance();
								newTimeout.setTime(newTimeOutDate);
								TimeZone tz = TimeZone.getTimeZone(playerData.getTimezone());
								if (tz != null) {
									newTimeout.setTimeZone(tz);
								}
								if ((newTimeout.get(Calendar.DAY_OF_WEEK) == weekend[0]) || (newTimeout.get(Calendar.DAY_OF_WEEK) == weekend[1])) {
									newTimeout.add(Calendar.DATE, 1);
									newTimeout.set(Calendar.HOUR_OF_DAY, 0);
									newTimeout.set(Calendar.MINUTE, 0);
									newTimeout.set(Calendar.SECOND, 0);
								}
								if ((newTimeout.get(Calendar.DAY_OF_WEEK) == weekend[0]) || (newTimeout.get(Calendar.DAY_OF_WEEK) == weekend[1])) {
									newTimeout.add(Calendar.DATE, 1);
									newTimeout.set(Calendar.HOUR_OF_DAY, 0);
									newTimeout.set(Calendar.MINUTE, 0);
									newTimeout.set(Calendar.SECOND, 0);
								}

								t.setTimeoutDate(newTimeout.getTime());
								updateGameAfterMove(t);
								continue;
							}
						}
					} catch (DSGPlayerStoreException e) {
						log4j.debug("TimeoutCheckRunnable, DSGPlayerStoreException " + e);
					} catch (TBStoreException e) {
						log4j.debug("TimeoutCheckRunnable, TBStoreException " + e);
					} catch (Throwable e) {
						log4j.debug("TimeoutCheckRunnable, Throwable " + e);
					}

					t.timeout();
					int seat = t.getPlayerSeat(cp);
					t.setWinner(3 - seat);

					//keep around for viewing through profile
					//uncacheGameForPlayer(t);
					
					endGameRunnable.endGame(t, EndGameRunnable.Data.REASON_TO);
				}
			}
		}
	};

	
	
	private Date newTimeout(long pid) {
		TBVacation vacation = getTBVacation(pid);
		if (vacation != null) {
			Date lastPinched = vacation.getLastPinched();
			Date oneHourAgo = new Date(System.currentTimeMillis() - 3600L * 1000);
			if (lastPinched != null && oneHourAgo.before(lastPinched)) {
				return new Date(lastPinched.getTime() + 3600L * 1000);
			} else {
				int hoursLeft = vacation.getHoursLeft();
				if (hoursLeft > 0) {
					if (hoursLeft - 1 == 24) {
						DSGMessage message = new DSGMessage();
						message.setFromPid(23000000016237L);
						message.setToPid(pid);
						message.setSubject("24 hours of vacation left");
						message.setBody("The gameServer informs you that you only have 24 hours of vacation time left for this year." +
								" Once these are depleted, timeouts for turn-based games become hard deadlines.\n \n " +
								"You can purchase additional vacation days at https://www.pente.org/gameServer/subscriptions \n\n");
						message.setCreationDate(new java.util.Date());
						try {
							dsgMessageStorer.createMessage(message, false);
						} catch (DSGMessageStoreException e) {
							e.printStackTrace();
						}
						notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());
					}
					lastPinched = new Date();
					vacation.setLastPinched(lastPinched);
					vacation.setHoursLeft(hoursLeft - 1);
					((MySQLTBGameStorer) baseStorer).storeTBVacation(pid, vacation);
					return new Date(System.currentTimeMillis() + 3600L * 1000);
				}
			}
		}
		return null;
	}

	public TBVacation getTBVacation(long pid) {
		TBVacation vacation = this.vacationPerPlayer.get(pid);
		if (vacation == null) {
			vacation = baseStorer.getTBVacation(pid);
			this.vacationPerPlayer.put(pid, vacation);
		}
		return vacation;
	}
	
	public void addExtraTBVacationDays(long pid, int extraDays) {
		TBVacation vacation = getTBVacation(pid);
		if (vacation != null) {
			int hoursLeft = vacation.getHoursLeft();
			vacation.setHoursLeft(hoursLeft + 24*extraDays);
			((MySQLTBGameStorer) baseStorer).storeTBVacation(pid, vacation);
		}
	}

	// algorithm - end game thread (just offloads updating ended games to db)
	// do forever
	//   synch (lock)
	//     check if server still running, if not, die
	//   end synch
	//   end game in base storer
	private class EndGameRunnable implements Runnable {

		class Data {
			public static final int REASON_WIN = 1;
			public static final int REASON_TO = 2;
			public static final int REASON_RESIGN = 3;
			TBGame game;
			int reason;
			public Data(TBGame game, int reason) {
				this.game = game;
				this.reason = reason;
			}
		}
		public String getName() {
			return "TB-EndGame";
		}

		private volatile boolean alive = true;
		private SynchronizedQueue queue = new SynchronizedQueue();
		
		public void endGame(TBGame game, int reason) {
			log4j.debug("EndGameRunnable.endGame(" + game.getGid() + ", " +
				reason + ")");
			queue.add(new Data(game, reason));
		}
		public String toString() {
			return getName() + " queue.size()=" + queue;
		}
		

		public void kill() {
			alive = false;
		}
		public void reset() {
			alive = true;
		}

		public void run() {

			while (alive) {
				log4j.debug(getName() + " run");
				Data data = null;
	
				try {
					data = (Data) queue.remove();
					log4j.debug("end game " + data.game.getGid());
	
					cacheStats.incrementGamesCompleted();
					
					// store tb game
					baseStorer.endGame(data.game);
					
					TBSet set = data.game.getTbSet();
					if (set.isCompleted()) {
						set.setState(TBSet.STATE_COMPLETED);
						set.setCompletionDate(new Date());
						baseStorer.endSet(set);
					}
					
					// store game in DSG game db, and update DSG ratings
					storeGameDSG(data, set);
					
				} catch (InterruptedException e) {
					// no action, if killed thread will see in next loop
					log4j.error(getName() + " Interrupted, " + (
						(data == null) ? "ok" : " processing " + data.game.getGid())); 
				} catch (TBStoreException tse) {
					// just log error, not sure what else can do
					if (data != null) {
						log4j.error("Error ending game " + data.game.getGid(), tse);
					}
					log4j.error("Error ending game", tse);
				} catch (Throwable t) {
					log4j.error("Unknown error ending game", t);
				}
			}
		}

		public void storeGameDSG(Data data, TBSet set) {

			TBGame game = data.game;
			try {

				if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
					long pid = 0;
					if (game.getPlayer1Pid() == 23000000020606L) {
						pid = game.getPlayer2Pid();
					} else {
						pid = game.getPlayer1Pid();
					}

					String subject = "", body = "";
					if (game.getWinner() == 1) {
						if (pid == game.getPlayer2Pid()) {
							subject = "You lost";
						} else {
							subject = "You won";
						}
					} else {
						if (pid == game.getPlayer2Pid()) {
							subject = "You won";
						} else {
							subject = "You lost";
						}
					}
					body = subject + " your game of " + GridStateFactory.getGameName(game.getGame()) + " against computer";

					DSGMessage message = new DSGMessage();
					message.setFromPid(23000000020606L);
					message.setToPid(pid);
					message.setSubject(subject);
					message.setBody(body);
					message.setCreationDate(new java.util.Date());
					dsgMessageStorer.createMessage(message, false);

					return;
				}				

				GameData gameData = new DefaultGameData();
				gameData.setGameID(game.getGid());
		        gameData.setDate(game.getCompletionDate());
		        gameData.setGame(GridStateFactory.getGameName(game.getGame()));
				gameData.setSite("Pente.org");
                gameData.setEvent("Turn-based Game");
				if (game.getEventId() != getEventId(game.getGame())) {
                    if (game.getEventId() != kothStorer.getEventId(game.getGame())) {
                        for (Tourney t: tourneyStorer.getCurrentTournies()) {
                            if (t.getEventID() == game.getEventId()) {
                                Tourney tourney = tourneyStorer.getTourney(t.getEventID());
                                gameData.setEvent(tourney.getName());
                                TourneyMatch tourneyMatch = tourneyStorer.getUnplayedMatch(game.getPlayer1Pid(),game.getPlayer2Pid(),game.getEventId());
                                if (tourneyMatch != null) {
                                    gameData.setRound(tourneyMatch.getRound()+"");
                                    gameData.setSection(tourneyMatch.getSection()+"");
                                }
                                break;
                            }
                        }
                    }
				}
				gameData.setInitialTime(game.getDaysPerMove());
				gameData.setRated(game.isRated());
				gameData.setTimed(true);
				gameData.setPrivateGame(set.isPrivateGame());
				
				if (game.getState() == TBGame.STATE_COMPLETED_TO) {
				    gameData.setStatus(GameData.STATUS_TIMEOUT);
                }
				
				DSGPlayerData player1 = null;
				DSGPlayerData player2 = null;
	        
	            player1 = dsgPlayerStorer.loadPlayer(
					game.getPlayer1Pid());

	            DSGPlayerGameData p1Data = 
	                player1.getPlayerGameData(game.getGame());

				PlayerData p1 = new DefaultPlayerData();
		        p1.setType(PlayerData.HUMAN);
	            p1.setRating((int) p1Data.getRating());
				p1.setUserIDName(player1.getName());
				p1.setUserID(player1.getPlayerID());
				gameData.setPlayer1Data(p1);

	            player2 = dsgPlayerStorer.loadPlayer(
					game.getPlayer2Pid());

	            DSGPlayerGameData p2Data = 
	                player2.getPlayerGameData(game.getGame());
				PlayerData p2 = new DefaultPlayerData();
		        p2.setType(PlayerData.HUMAN);
	            p2.setRating((int) p2Data.getRating());
				p2.setUserIDName(player2.getName());
				p2.setUserID(player2.getPlayerID());
				gameData.setPlayer2Data(p2);
				

		        gameData.setWinner(game.getWinner());

		        if (game.getGame() == GridStateFactory.TB_DPENTE || game.getGame() == GridStateFactory.TB_DKERYO) {
		            gameData.setSwapped(game.didDPenteSwap());
		        }
	
		        for (int i = 0; i < game.getNumMoves(); i++) {
		            gameData.addMove(game.getMove(i));
		        }
	
				gameStorer.storeGame(gameData);

                
                // update player ratings
                DSGPlayerData winnerData = null;
                DSGPlayerData loserData = null;
                if (game.isDraw()) {
                    if (set.isCompleted() && set.getWinnerPid() == player1.getPlayerID()) {
                        winnerData = player1;
                        loserData = player2;
                    }
                    //either set is not complete
                    //or     set is a draw
                    //or     set is a win for p2
                    else {
                        winnerData = player2;
                        loserData = player1;
                    }
                }
                else {
    				if (game.getWinner() == 1) {
    					winnerData = player1;
    					loserData = player2;
    				}
    				else {
    					winnerData = player2;
    					loserData = player1;
    				}
                }

				// track before/after ratings for message
				double ratings[] = new double[4];
				ratings[0] = winnerData.getPlayerGameData(game.getGame()).getRating();
				ratings[2] = loserData.getPlayerGameData(game.getGame()).getRating();

				if (set.isCompleted() && game.isRated()) {
					GameOverUtilities.updateGameData(dsgPlayerStorer, winnerData,
						winnerData.getPlayerGameData(game.getGame(), false),
						loserData,
						loserData.getPlayerGameData(game.getGame(), false),
						set.isDraw(), 64);
				}
				ratings[1] = winnerData.getPlayerGameData(game.getGame()).getRating();
				ratings[3] = loserData.getPlayerGameData(game.getGame()).getRating();

				// send out text message
				String winText = "Congratulations!, You won your " +
					GridStateFactory.getGameName(game.getGame())  +
					" game against " + loserData.getName();

				String lossText = "You lost your " +
					GridStateFactory.getGameName(game.getGame()) + 
					" game against " + winnerData.getName();
				
				String drawText = "Your " +
                    GridStateFactory.getGameName(game.getGame()) + 
                    " game - " + player1.getName() + " vs. " + player2.getName() +
                    " has ended in a draw";
                String drawGameWinSetText = drawText;
                String drawGameLoseSetText = drawText;
                    
				if (set.isTwoGameSet()) {
					if (set.isCompleted()) {
						if (set.isDraw()) {
							winText += " and the set is a draw";
							lossText += " and the set is a draw";
                            drawGameWinSetText += " and the set is a draw";
                            drawGameLoseSetText += " and the set is a draw";
						}
						else {
							winText += " and you won the set";
                            drawGameWinSetText += " and you won the set";
							lossText += " and you lost the set";
                            drawGameLoseSetText += " and you lost the set";
						}
					}
				}
				
				
				winText += ".\n" + "You can view your game at http://www.pente.org/gameServer/viewLiveGame?mobile&g=" + game.getGid();
				if (set.isTwoGameSet()) {
				    if (set.isCompleted()) {
						 winText += "\n or invite " + loserData.getName() + 
							" for another game of " + GridStateFactory.getGameName(game.getGame()) + " with the following link: http://www.pente.org/gameServer/tb/new.jsp?game=" + 
							game.getGame() + "&invitee=" + loserData.getName();
					}
				}
				lossText += ".\n" + "You can view your game at http://www.pente.org/gameServer/viewLiveGame?mobile&g=" + game.getGid();
				if (set.isTwoGameSet()) {
				    if (set.isCompleted()) {
				        lossText += "\n or invite " + winnerData.getName() + 
				        " for another game of " + GridStateFactory.getGameName(game.getGame()) + " with the following link: http://www.pente.org/gameServer/tb/new.jsp?game=" + 
								game.getGame() + "&invitee=" + winnerData.getName();
					}
				}
		        drawGameWinSetText += ".\n" + "You can view your game at http://www.pente.org/gameServer/viewLiveGame?g=" + game.getGid();
		        if (set.isTwoGameSet()) {
		            if (set.isCompleted()) {
		        				 drawGameWinSetText += "\n or invite " + loserData.getName() + 
		        					" for another game of " + GridStateFactory.getGameName(game.getGame()) + " with the following link: http://www.pente.org/gameServer/tb/new.jsp?game=" + 
		        					game.getGame() + "&invitee=" + loserData.getName();
					}
				}
		        drawGameLoseSetText += ".\n" + "You can view your game at http://www.pente.org/gameServer/viewLiveGame?g=" + game.getGid();
		        if (set.isTwoGameSet()) {
		            if (set.isCompleted()) {
		                drawGameLoseSetText += "\n or invite " + winnerData.getName() + 
		                " for another game of " + GridStateFactory.getGameName(game.getGame()) + " with the following link: http://www.pente.org/gameServer/tb/new.jsp?game=" + 
		    					game.getGame() + "&invitee=" + winnerData.getName();
					}
				}

				
				if (data.reason == Data.REASON_RESIGN) {
					winText += "\n\n" + loserData.getName() + " has resigned.";
					lossText += "\n\nYou resigned to " + 
						winnerData.getName() + ".";

					//get last message from player who resigned if any and put 
					// at beginning
					TBMessage m = game.getMessage(game.getNumMoves());
					if (m != null && m.getPid() == loserData.getPlayerID()) {
						winText = m.getMessage() + "\n\n" + winText;
					}
				}
				else if (data.reason == Data.REASON_TO) {
					winText += "\n\n" + loserData.getName() + " ran out of time.";
					lossText += "\n\nYou ran out of time.";
				}
				else if (data.reason == Data.REASON_WIN) {

					//get last message from winner if any and put at beginning
					TBMessage m = game.getMessage(game.getNumMoves());
					if (m != null) {
						lossText = m.getMessage() + "\n\n" + lossText;
                        drawGameLoseSetText = m.getMessage() + "\n\n" + drawGameLoseSetText;
					}
				}

				if (game.isRated()) {
					if (set.isCompleted()) {
						// append ratings change (even if a draw)
						String ratingsChange =
							winnerData.getName() + "'s rating change: " + 
								Math.round(ratings[0]) + " - " +
								Math.round(ratings[1]) + "\n" +
							loserData.getName() + "'s rating change: " +
								Math.round(ratings[2]) + " - " +
								Math.round(ratings[3]);
						winText += "\n\n" + ratingsChange;
						lossText += "\n\n" + ratingsChange;
                        drawGameWinSetText += "\n\n" + ratingsChange;
                        drawGameLoseSetText += "\n\n" + ratingsChange;
					}
					else {
						String setMsg = "This set is not complete yet, once the " +
							"other game is completed your rating will be updated.";
						
						winText += "\n\n" + setMsg;
						lossText += "\n\n" + setMsg;
                        drawGameWinSetText += "\n\n" + setMsg;
                        drawGameLoseSetText += "\n\n" + setMsg;
					}
				} else {
					winText += " \n \n ";
					lossText += "\n \n ";
				}
				
                String winSubj = game.isDraw() ? "It's a Draw" : "Congratulations, you won!";
                String loseSubj = game.isDraw() ? "It's a Draw" : "You lost";
                String aWinText = game.isDraw() ? drawGameWinSetText : winText;
                String aLossText = game.isDraw() ? drawGameLoseSetText : lossText;
                
                DSGMessage winMessage = new DSGMessage();
				winMessage.setFromPid(loserData.getPlayerID());
				winMessage.setToPid(winnerData.getPlayerID());
				winMessage.setSubject(winSubj);
				winMessage.setBody(aWinText);
				winMessage.setCreationDate(new java.util.Date());
				dsgMessageStorer.createMessage(winMessage, false);
				
				DSGMessage loseMessage = new DSGMessage();
				loseMessage.setFromPid(winnerData.getPlayerID());
				loseMessage.setToPid(loserData.getPlayerID());
				loseMessage.setSubject(loseSubj);
				loseMessage.setBody(aLossText);
				loseMessage.setCreationDate(new java.util.Date());
				dsgMessageStorer.createMessage(loseMessage, false);

				if (game.getEventId() == kothStorer.getEventId(game.getGame())) {
					if (set.isCompleted() && game.isRated() && !set.isDraw()) {
						kothStorer.movePlayersUpDown(game.getGame(), winnerData.getPlayerID(), loserData.getPlayerID());
					}
					kothStorer.updatePlayerLastGameDate(game.getGame(), winnerData.getPlayerID());
					kothStorer.updatePlayerLastGameDate(game.getGame(), loserData.getPlayerID());
				} else if (game.getEventId() != getEventId(game.getGame())) {
					TourneyMatch tourneyMatch = null;
					if ((game.getGame() == GridStateFactory.TB_DPENTE || game.getGame() == GridStateFactory.TB_DKERYO) && game.didDPenteSwap()) {
						tourneyMatch = tourneyStorer.getUnplayedMatch(game.getPlayer2Pid(),game.getPlayer1Pid(),game.getEventId());
					} else {
						tourneyMatch = tourneyStorer.getUnplayedMatch(game.getPlayer1Pid(),game.getPlayer2Pid(),game.getEventId());
					}
					if (tourneyMatch != null) {
						tourneyMatch.setGid(game.getGid());
						int winner = game.getWinner();
						if ((game.getGame() == GridStateFactory.TB_DPENTE || game.getGame() == GridStateFactory.TB_DKERYO) && game.didDPenteSwap()) {
							winner = 3 - winner;
						}						
						tourneyMatch.setResult(winner);
						tourneyStorer.updateMatch(tourneyMatch);
						// return;
					}
				}
				
				
			} catch (Throwable t) {
				log4j.error("CacheTBStorer, problem storing at DSG.", t);
			}
		}
	}
	


	private void cacheSetForPlayer(TBSet set, long pid, boolean create) {
		log4j.debug("CacheTBGameStorer.cacheSetForPlayer(" + set.getSetId() + ", " + pid + ", " + 
			create + ")");

		if (pid == 0) return;
		synchronized (cacheTbLock) {
			List<Long> sids = setsByPid.get(pid);
			if (sids == null && create) {
				log4j.debug("new cache for player: " + pid + ", " + set.getSetId());
				sids = new ArrayList<Long>();
				setsByPid.put(pid, sids);
			}
			if (sids != null) {
				log4j.debug("add to cache");
				sids.add(set.getSetId());
			}
		}
	}
	private void uncacheSetForPlayer(TBSet set) {
		log4j.debug("CacheTBGameStorer.uncacheSetForPlayer(" + set.getSetId() + ")");

		synchronized (cacheTbLock) {
			List<Long> sids = setsByPid.get(set.getPlayer1Pid());
			if (sids != null) {
				sids.remove(set.getSetId());
			}
			sids = setsByPid.get(set.getPlayer2Pid());
			if (sids != null) {
				sids.remove(set.getSetId());
			}
		}
	}


	private void cacheSet(TBSet set) {
	
		log4j.debug("CacheTBGameStorer.cacheSet(" + set.getSetId() + ")");
		
		cacheStats.incrementSetsCached();
		
		synchronized (cacheTbLock) {
			setsMap.put(set.getSetId(), set);
		}
		
		for (int i = 0; i < 2; i++) {
			TBGame g = set.getGames()[i];
			if (g != null) {
				g.setTbSet(set);
				cacheGame(g);
			}
		}
	}
	private void uncacheSet(TBSet set) {
		log4j.debug("CacheTBGameStorer.uncacheSet(" + set.getSetId() + ")");

		cacheStats.incrementSetsUncached();
		
		synchronized (cacheTbLock) {
			setsMap.remove(set.getSetId());
			uncacheSetForPlayer(set);
		}
		
		for (int i = 0; i < 2; i++) {
			TBGame g = set.getGames()[i];
			if (g != null) {
				uncacheGame(g);
			}
		}
	}

	private void cacheGame(TBGame game) {

		log4j.debug("CacheTBGameStorer.cacheGame(" + game.getGid() + ")");

		cacheStats.incrementGameCached();
		
		synchronized (cacheTbLock) {
			gamesMap.put(game.getGid(), game);
		}
	}
	private void uncacheGame(TBGame game) {
		log4j.debug("CacheTBGameStorer.uncacheGame(" + game.getGid() + ")");

		cacheStats.incrementGameUncached();
		
		synchronized (cacheTbLock) {
			gamesMap.remove(game.getGid());
		}
	}

	public int getEventId(int game) throws TBStoreException {
		log4j.debug("CacheTBStorer.getEventId(" + game + ")");
		
		Integer e = eidMap.get(game);
		if (e == null) {
			int eid = baseStorer.getEventId(game);
			eidMap.put(game, eid);
			return eid;
		}
		return e.intValue();
	}
	
	public void createSet(TBSet set) throws TBStoreException {

		log4j.debug("CacheTBGameStorer.createSet()");
		
		set.setCreationDate(new Date());
		
        
        for (int i = 0; i < 2; i++) {
            TBGame game = set.getGames()[i];
			if (game == null) {
				continue;
			}
            if (game.getState() == TBGame.STATE_ACTIVE) {
            	game.setLastMoveDate(new Date());
            	game.setTimeoutDate(new Date());
            }
			createGame(game);
        }
        
		baseStorer.createSet(set);
		
		loadWaitingSets(); // make sure loaded
		
		// update waiting games
		synchronized (cacheTbLock) {
			if (set.isWaitingSet()) {
				waitingSets.add(set);
			}
		}
		
		cacheSet(set);
		
		// if players involved in set already have cached sets, update caches
		cacheSetForPlayer(set, set.getPlayer1Pid(), false);
		cacheSetForPlayer(set, set.getPlayer2Pid(), false);

        // tourney games are started automatically
        if (set.getState() == TBSet.STATE_ACTIVE) {
            for (int i = 0; i < 2; i++) {
                TBGame game = set.getGames()[i];
                if (game == null) {
                    continue;
                }
                if (game.getGame() == GridStateFactory.TB_DPENTE ||
                        game.getGame() == GridStateFactory.TB_DKERYO) {

                    long newTimeout = Utilities.calculateNewTimeout(
                            game, dsgPlayerStorer);
                    synchronized (cacheTbLock) {
                        game.setTimeoutDate(new Date(newTimeout));
                    }
                    baseStorer.updateGameAfterMove(game);
                    continue;

                }
                storeNewMove(game.getGid(), 0, 180);
				
            }
        }
	}

	public TBSet loadSetByGid(long gid) throws TBStoreException {
		throw new UnsupportedOperationException("not supported");
	}
	public TBSet loadSet(long sid) throws TBStoreException {
		log4j.debug("CacheTBGameStorer.loadSet(" + sid + ")");

		cacheStats.incrementSetLoads();
		
		TBSet s = null;
		synchronized (cacheTbLock) {
			s = setsMap.get(sid);
		}

		if (s == null) {
			log4j.debug("not cached");
			s = baseStorer.loadSet(sid);
			if (s != null) {
				cacheSet(s);
			}
		}
		else {
			cacheStats.incrementSetLoadsCached();
		}

		return s;
	}
	
	public void createGame(TBGame game) throws TBStoreException {
		
		log4j.debug("CacheTBGameStorer.createGame()");
		
		cacheStats.incrementGamesCreated();
		
        if (game.getEventId() == 0) {
            game.setEventId(getEventId(game.getGame()));
        }
		game.setCreationDate(new Date());
		baseStorer.createGame(game);
	}

	public TBGame loadGame(long gid) throws TBStoreException {
		log4j.debug("CacheTBGameStorer.loadGame(" + gid + ")");

		cacheStats.incrementGameLoads();
		TBGame g = null;
		synchronized (cacheTbLock) {
			g = gamesMap.get(gid);
		}
		if (g == null) {
			log4j.debug("not cached");
			TBSet s = baseStorer.loadSetByGid(gid);
			if (s != null) {
				cacheSet(s);
				g = s.getGame(gid);
			}
		}
		else {
			cacheStats.incrementGameLoadsCached();
		}

		return g;
	}

	public List<TBSet> loadGamesExpiringBefore(Date date)
			throws TBStoreException {
		throw new UnsupportedOperationException("not supported");
	}

	public List<TBSet> loadWaitingSets() throws TBStoreException {

		log4j.debug("CacheTBGameStorer.loadWaitingSets()");
		synchronized (cacheTbLock) {
			if (waitingSetsLoaded) {
				log4j.debug("cached");
				// return copy so can modify it locally while
				// clients display on screen
				return new ArrayList<TBSet>(waitingSets);
			}
		}
		
		List<TBSet> gs = baseStorer.loadWaitingSets();
		synchronized (cacheTbLock) {
			waitingSets.clear();//protect against race condition
			waitingSets.addAll(gs);
			for (TBSet s : gs) {
				log4j.debug("cached " + s.getSetId());
			}
			waitingSetsLoaded = true;
		}
		return new ArrayList<TBSet>(waitingSets);
	}

	public int getNumGamesMyTurn(long pid) throws TBStoreException {
		
		log4j.debug("CacheTBGameStorer.getNumGamesMyTurn(" + pid + ")");
		
		List<TBSet> sets = loadSets(pid);
		int count = 0;
		for (TBSet s : sets) {
			if (s.getState() == TBGame.STATE_NOT_STARTED &&
				s.getInviterPid() != pid) {
				count++;
			}
			else if (s.getState() == TBGame.STATE_ACTIVE) {
				if (s.getGame1().getState() == TBGame.STATE_ACTIVE &&
					(s.getGame1().getCurrentPlayer() == pid ||
                     (s.getCancelPid() != 0 && s.getCancelPid() != pid))) count++;
				if (s.getGame2() != null && 
					s.getGame2().getState() == TBGame.STATE_ACTIVE &&
					(s.getGame2().getCurrentPlayer() == pid ||
                     (s.getCancelPid() != 0 && s.getCancelPid() != pid))) count++;
			}
		}
		return count;
	}

	public List<TBSet> loadSets(long pid) throws TBStoreException {
		
		log4j.debug("CacheTBGameStorer.loadSets(" + pid + ")");
		// maintain a separate cache for each pid
		// store all game data in gamesMap
		// store all set data in setMap
		// store list of setIds in setsByPid

		List<Long> sids = null;
		List<TBSet> sets = null;
		
		synchronized (cacheTbLock) {
			sids = setsByPid.get(pid); 
			// copy sids since whole method is not synched
			if (sids != null) {
				sids = new ArrayList<Long>(sids);
			}
		}

		if (sids == null) {
			log4j.debug("not cached yet");
			
			sets = baseStorer.loadSets(pid);
			
			cacheStats.incrementSetLoads(sets.size());
			
			synchronized (cacheTbLock) {
				// even if player has no tb games, cache an empty list
				// just so we don't hit db every page load
				if (sets.isEmpty()) {
					setsByPid.put(pid, new ArrayList<Long>());
				}
				else {
					for (TBSet s : sets) {
						cacheSet(s);
						cacheSetForPlayer(s, pid, true);
					}
				}
			}

			return new ArrayList<TBSet>(sets);
		}
		else {
			log4j.debug("cached");
			cacheStats.incrementSetLoads(sids.size());
			cacheStats.incrementSetLoadsCached(sids.size());

			sets = new ArrayList<TBSet>(sids.size());
			for (Long l : sids) {
				TBSet s = setsMap.get(l);
				// since not synced since loaded sids, sets might have
				// been flushed from cache
				if (s != null) {
					sets.add(s);
				}
			}
			return sets;
		}

	}

	public void storeNewMove(long gid, int moveNum, int move)
		throws TBStoreException {
		log4j.debug("CacheTbStorer.storeNewMove(" + gid + ", " + moveNum + ", " +
			move + ")");
		
		cacheStats.incrementMovesMade();
		
		TBGame game = loadGame(gid);
		if (game == null) {
			throw new TBStoreException("Invalid game, not found: " + gid);
		}
		if (game.getState() != TBGame.STATE_ACTIVE) {
			throw new TBStoreException("Game is not active: " + gid);
		}
		

		// check that it is a valid move
		//TODO performance might show keeping state around a good idea
		GridState state = GridStateFactory.createGridState(
			game.getGame(), game);
		if (game.getGame() == GridStateFactory.TB_PENTE ||
			game.getGame() == GridStateFactory.TB_KERYO ||
			game.getGame() == GridStateFactory.TB_BOAT_PENTE) {
			((PenteState) state).setTournamentRule(game.isRated());
		}
		if (!state.isValidMove(move, state.getCurrentPlayer())) {
			throw new TBStoreException("Invalid move [" + move + "] for game: " +
				game.getGid());
		}

		synchronized (cacheTbLock) {
			game.addMove(move);
		}

		long newTimeout = Utilities.calculateNewTimeout(
			game, dsgPlayerStorer);
		
		synchronized (cacheTbLock) {
			game.setTimeoutDate(new Date(newTimeout));
		}

		// check if game over
		state.addMove(move);
		if (state.isGameOver()) {
			log4j.debug("CacheTbStorer.gameover, send to endGameRunnable");
			synchronized (cacheTbLock) {
				game.end();
				game.setWinner(state.getWinner());
			}
			endGameRunnable.endGame(game, EndGameRunnable.Data.REASON_WIN);
		}

		// do this in background thread for performance?
		if (game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
			((MySQLTBGameStorer) baseStorer).storeNewAIMove(gid, moveNum, move);
		} else {
			baseStorer.storeNewMove(gid, moveNum, move);
		}

		baseStorer.updateGameAfterMove(game);
	}

	public void fixGame(long gid) throws Throwable {
		TBGame game = loadGame(gid);
		int reason = 0;
		if (game.getState() == TBGame.STATE_COMPLETED_TO) {
			reason = EndGameRunnable.Data.REASON_TO;
		}
		else {
			GridState gs = GridStateFactory.createGridState(game.getGame(), game);
			if (gs.isGameOver()) {
				reason = EndGameRunnable.Data.REASON_WIN;
			}
			else {
				reason = EndGameRunnable.Data.REASON_RESIGN;
			}
		}
		endGameRunnable.endGame(game, reason);
	}
	
	public void storeNewMessage(long gid, TBMessage message)
		throws TBStoreException {
		log4j.debug("CacheTbStorer.storeNewMessage(" + gid + ")");
		
		TBGame game = loadGame(gid);
		if (game == null) {
			throw new TBStoreException("Invalid game, not found: " + gid);
		}
		
		synchronized (cacheTbLock) {
			game.addMessage(message);
		}

		baseStorer.storeNewMessage(gid, message);
	}

	public void updateGameAfterMove(TBGame game) throws TBStoreException {
		baseStorer.updateGameAfterMove(game);
	}

	public void setGameEventId(long gameId, long eventId) throws TBStoreException {
		baseStorer.setGameEventId(gameId, eventId);
	}


	public void acceptInvite(TBSet s, long pid)	throws TBStoreException {

		log4j.debug("CacheTBStorer.acceptInvite(" + s.getSetId() + ", " +
			pid + ")");

		TBSet set = loadSet(s.getSetId());

		loadWaitingSets(); // in case set is a waiting set
		
		synchronized (cacheTbLock) {
			if (set.getState() != TBSet.STATE_NOT_STARTED) {
				throw new TBStoreException("Set can not be accepted, state " +
					"has changed. " + s.getSetId() + ", pid=" + pid);
			}

			if (set.isWaitingSet()) {
				waitingSets.remove(set);
				cacheSetForPlayer(set, pid, false);
			}

			set.acceptInvite(pid);
			for (int i = 0; i < 2; i++) {
				TBGame game = set.getGames()[i];
				if (game == null) {
					continue;
				}
				game.acceptInvite(pid, set.getInviterPid());
				if (game.getGame() != GridStateFactory.TB_DPENTE && game.getGame() != GridStateFactory.TB_DKERYO) {
					game.addMove(180);
				}
			}
		}


//		TBGame game = set.getGame1();
		TBGame game;

		synchronized (cacheTbLock) {

			for (int i = 0; i < 2; i++) {
                game = set.getGames()[i];
				if (game == null) {
					continue;
				}
                long newTimeout = Utilities.calculateNewTimeout(
                        game, dsgPlayerStorer);
				game.setTimeoutDate(new Date(newTimeout));
			}
		}

		// do this in background thread for performance?
		baseStorer.acceptInvite(set, pid);

		for (int i = 0; i < 2; i++) {
			game = set.getGames()[i];
			if (game == null) {
				continue;
			}
			if (game.getGame() != GridStateFactory.TB_DPENTE && game.getGame() != GridStateFactory.TB_DKERYO) {
				baseStorer.storeNewMove(game.getGid(), 0, 180);
			}
		}
	}

	public void cancelSet(TBSet s) throws TBStoreException {
		
		log4j.debug("CacheTBGameStorer.cancelSet(" + s.getSetId() + ")");
		
		TBSet set = loadSet(s.getSetId());
		loadWaitingSets();
		synchronized (cacheTbLock) {
			if (set.isWaitingSet()) {
				waitingSets.remove(set);
			}
            
            if (set.getState() == TBSet.STATE_ACTIVE) {
                if (set.getGame1().getState() == TBGame.STATE_ACTIVE) {
                    set.getGame1().setState(TBGame.STATE_CANCEL);
                    set.getGame1().setCompletionDate(new Date());
                }
                if (set.getGame2() != null &&
                    set.getGame2().getState() == TBGame.STATE_ACTIVE) {
                    set.getGame2().setState(TBGame.STATE_CANCEL);
                    set.getGame2().setCompletionDate(new Date());
                }
                set.setCancelMsg("");
                set.setCancelPid(0);
            }
            else if (set.getState() == TBSet.STATE_NOT_STARTED) {
                set.getGame1().setCompletionDate(new Date());
                if (set.getGame2() != null) {
                    set.getGame2().setCompletionDate(new Date());
                }
            }
			set.setState(TBSet.STATE_CANCEL);
			set.setCompletionDate(new Date());
		}
		
		baseStorer.cancelSet(set);
		
		uncacheSet(set);
	}
    public void declineCancel(TBSet set) throws TBStoreException {
        synchronized (cacheTbLock) {
            //should do some error checking here but too lazy
            set.setCancelMsg("");
            set.setCancelPid(0);
        }
        baseStorer.declineCancel(set);
    }
    public void requestCancel(TBSet set, long requestorPid, String message) throws TBStoreException {
        synchronized (cacheTbLock) {
            //should do some error checking here but too lazy
            set.setCancelMsg(message);
            set.setCancelPid(requestorPid);
        }
        baseStorer.requestCancel(set, requestorPid, message);
    }

	public void resignGame(TBGame g) throws TBStoreException {

		log4j.debug("CacheTBGameStorer.resignGame(" + g.getGid() + ")");

		TBGame game = loadGame(g.getGid());
		synchronized (cacheTbLock) {
			game.end();
			game.setWinner(3 - g.getPlayerSeat(g.getCurrentPlayer()));
			endGameRunnable.endGame(game, EndGameRunnable.Data.REASON_RESIGN);
		}
	}

	public void endSet(TBSet set) throws TBStoreException {
		throw new UnsupportedOperationException("not supported");
	}
	public void endGame(TBGame game) throws TBStoreException {
		throw new UnsupportedOperationException("not supported");
	}

	public void updateDPenteState(TBGame g, int state)
		throws TBStoreException {

		log4j.debug("CacheTBGameStorer.updateDPenteState(" + g.getGid() + ", " +
			state + ")");
		TBGame game = loadGame(g.getGid());
		
		synchronized (cacheTbLock) {
			game.setDPenteState(state);
		}
		baseStorer.updateDPenteState(game, state);
	}
	
	public void dPenteSwap(TBGame g, boolean swap) throws TBStoreException {

		log4j.debug("CacheTBGameStorer.dPenteSwap(" + g.getGid() + ", " + swap + ")");
		TBGame game = loadGame(g.getGid());

		synchronized (cacheTbLock) {
			game.dPenteSwap(swap);
		}
		long newTimeout = Utilities.calculateNewTimeout(
				game, dsgPlayerStorer);
		synchronized (cacheTbLock) {
			game.setTimeoutDate(new Date(newTimeout));
		}
		baseStorer.dPenteSwap(game, swap);
	}
	

	public void updateDaysOff(long pid, int weekend[]) throws TBStoreException {
		log4j.debug("updateWeekend(" + pid + ", " + weekend[0] + "," + 
			weekend[1] + ")");

		// load player games
		// for each active game where its that players turn
		//  recalc t/o based on lastmove and new weekend
		//  store new t/o
		List<TBSet> sets = loadSets(pid);
		for (TBSet s : sets) {
			if (s.getState() == TBSet.STATE_ACTIVE) {
				for (int i = 0; i < 2; i++) {
					TBGame g = s.getGames()[i];

					if (g != null &&
						g.getState() == TBGame.STATE_ACTIVE &&
						g.getCurrentPlayer() == pid) {

                        TimeZone tz = null;
                        try {
                            tz = TimeZone.getTimeZone(dsgPlayerStorer.loadPlayer(g.getCurrentPlayer()).getTimezone());
                        } catch (DSGPlayerStoreException e) {
                            log4j.error("CacheTBStorer updateDaysOff " + e);
                            e.printStackTrace();
                        }
                        Calendar startTimeCal = Calendar.getInstance();
                        startTimeCal.setTimeInMillis(g.getLastMoveDate().getTime());
                        if (tz != null) {
                            startTimeCal.setTimeZone(tz);
                        }

                        boolean update = false;
						synchronized (cacheTbLock) {
							long newTimeout = Utilities.calculateNewTimeout(
								startTimeCal,
								g.getDaysPerMove(),
								weekend[0], weekend[1]);
							log4j.debug("update t/o to " + newTimeout + " for " + g.getGid());
							
							if (g.getTimeoutDate().getTime() != newTimeout) {
								g.setTimeoutDate(new Date(newTimeout));
								update = true;
							}
						}
						if (update) {
							baseStorer.updateGameAfterMove(g);
						}
					}
				}
			}
		}
	}
    
    public void setTourneyStorer(TourneyStorer storer) {
        this.tourneyStorer = storer;
    }
    /** for tb tournaments, listen for when new rounds or
     *  matches are created and create sets here
     */
    public void tourneyEventOccurred(TourneyEvent event) {
        
        /* causing problems
        try {
            if (event.getType() == TourneyEvent.NEW_ROUND) {
                
                Tourney t = tourneyStorer.getTourney(event.getEid());
                if (GridStateFactory.isTurnbasedGame(t.getGame())) {
                    TourneyRound r = t.getLastRound();
                    // could use a listener for tbgames
                    for (TourneySection s : r.getSections()) {
                        for (TBSet set : s.getSets()) {
                            createSet(set);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log4j.error("Problem creating tb sets for tournament", t);
        }
        */
    }

    public void createTournamentSet(int game, long player1PID, long player2PID, int daysPerMove, int eventID) {
        try {
	        TBGame tbg1 = new TBGame();
	        tbg1.setGame(game);
	        tbg1.setDaysPerMove(daysPerMove);
	        tbg1.setRated(true);
	        tbg1.setPlayer1Pid(player1PID);
	        tbg1.setPlayer2Pid(player2PID);
	        tbg1.setEventId(eventID);
	        tbg1.setState(TBGame.STATE_ACTIVE);
	        tbg1.setLastMoveDate(new Date());
	        tbg1.setStartDate(new Date());
	        TBGame tbg2 = new TBGame();
	        tbg2.setGame(game);
	        tbg2.setDaysPerMove(daysPerMove);
	        tbg2.setRated(true);
	        tbg2.setPlayer2Pid(player1PID);
	        tbg2.setPlayer1Pid(player2PID);
	        tbg2.setEventId(eventID);
	        tbg2.setState(TBGame.STATE_ACTIVE);
	        tbg2.setLastMoveDate(new Date());
	        tbg2.setStartDate(new Date());
	        TBSet tbs = new TBSet(tbg1, tbg2);
	        tbs.setPlayer1Pid(player1PID);
	        tbs.setPlayer2Pid(player2PID);
	        tbs.setPrivateGame(false);
	        tbs.setState(TBSet.STATE_ACTIVE);
	        tbs.setInvitationRestriction(TBSet.ANY_RATING);
	        createSet(tbs);

	        Tourney tourney = tourneyStorer.getTourney(eventID);
			DSGPlayerData toPlayer = dsgPlayerStorer.loadPlayer(player2PID);

            DSGMessage message = new DSGMessage();
			message.setFromPid(23000000016237L);
			message.setToPid(player1PID);
			message.setSubject("New Tournament " + GridStateFactory.getGameName(game) + " Set started against " + toPlayer.getName());
			message.setBody("The gameServer has started a new "  + GridStateFactory.getGameName(game) + " set for you against " + toPlayer.getName() +
								" in round " + tourney.getNumRounds() + " of the " + tourney.getName() + "." + 
								"\nYou have " + daysPerMove + " days per move. \n \nTournament details can be found at https://www.pente.org/gameServer/tournaments/statusRound.jsp?eid="
								+ eventID + "&round=" + tourney.getNumRounds() + "  \n \n ");
			message.setCreationDate(new java.util.Date());
			dsgMessageStorer.createMessage(message, false);
			notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());

			toPlayer = dsgPlayerStorer.loadPlayer(player1PID);

            message = new DSGMessage();
			message.setFromPid(23000000016237L);
			message.setToPid(player2PID);
			message.setSubject("New Tournament " + GridStateFactory.getGameName(game) + " Set started against " + toPlayer.getName());
			message.setBody("The gameServer has started a new "  + GridStateFactory.getGameName(game) + " set for you against " + toPlayer.getName() + 
								" in round " + tourney.getNumRounds() + " of the " + tourney.getName() + "." + 
								"\nYou have " + daysPerMove + " days per move. \n \nTournament details can be found at https://www.pente.org/gameServer/tournaments/statusRound.jsp?eid="
								+ eventID + "&round=" + tourney.getNumRounds() + "  \n \n ");
			message.setCreationDate(new java.util.Date());
			dsgMessageStorer.createMessage(message, false);
			notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());
        } catch (Throwable t) {
            log4j.error("Problem creating tb sets for tournament", t);
        }
    }

    public void createAISet(int game, long player1PID, long player2PID, int daysPerMove, boolean set, int difficulty) {
        try {
	        TBGame tbg1 = new TBGame();
	        tbg1.setGame(game);
	        tbg1.setDaysPerMove(daysPerMove);
	        tbg1.setRated(set);
	        tbg1.setRound(difficulty);
	        tbg1.setPlayer1Pid(player1PID);
	        tbg1.setPlayer2Pid(player2PID);
	        tbg1.setEventId(getEventId(game));
	        tbg1.setState(TBGame.STATE_ACTIVE);
	        tbg1.setLastMoveDate(new Date());
	        tbg1.setStartDate(new Date());
	        TBGame tbg2 = null;
	        if (set) {
		        tbg2 = new TBGame();
		        tbg2.setGame(game);
		        tbg2.setDaysPerMove(daysPerMove);
		        tbg2.setRated(set);
		        tbg2.setRound(difficulty);
		        tbg2.setPlayer2Pid(player1PID);
		        tbg2.setPlayer1Pid(player2PID);
		        tbg2.setEventId(getEventId(game));
		        tbg2.setState(TBGame.STATE_ACTIVE);
		        tbg2.setLastMoveDate(new Date());
		        tbg2.setStartDate(new Date());
	        }
	        TBSet tbs = new TBSet(tbg1, tbg2);
	        tbs.setPlayer1Pid(player1PID);
	        tbs.setPlayer2Pid(player2PID);
	        tbs.setPrivateGame(false);
	        tbs.setState(TBSet.STATE_ACTIVE);
	        tbs.setInvitationRestriction(TBSet.ANY_RATING);
	        createSet(tbs);

				

        } catch (Throwable t) {
            log4j.error("Problem creating tb sets for tournament", t);
        }
    }

	public void restoreGame(long gid) throws TBStoreException {
		log4j.debug("CacheTBGameStorer.restoreGame " + gid);

		synchronized (cacheTbLock) {
			TBGame game = loadGame(gid);
			baseStorer.restoreGame(gid);
			uncacheGamesForPlayer(game.getPlayer1Pid());
			uncacheGamesForPlayer(game.getPlayer2Pid());
		}
	}


	public void setNotificationServer(NotificationServer notificationServer) {
		this.notificationServer = notificationServer;
	}

}
