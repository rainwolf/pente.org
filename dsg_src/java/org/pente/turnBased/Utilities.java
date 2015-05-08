package org.pente.turnBased;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.apache.log4j.Category;
import org.pente.game.*;
import org.pente.gameServer.core.*;

public class Utilities {

	private static Category log4j = Category.getInstance(
		Utilities.class.getName());
	
	public static boolean allowAccess(
		DSGPlayerStorer dsgPlayerStorer,
		HttpServletRequest request,
		HttpServletResponse response)
		throws IOException, DSGPlayerStoreException {
		
		String name = (String) request.getAttribute("name");
		if (name == null) return false;
		DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
		List prefs = dsgPlayerStorer.loadPlayerPreferences(data.getPlayerID());
		for (Iterator it = prefs.iterator(); it.hasNext();) {
			DSGPlayerPreference p = (DSGPlayerPreference) it.next();
			if (p.getName().equals("pentedb")) {
				return true;
			}
		}
		
		return false;
	}

	public static final Comparator<TBSet> CREATION_DATE_COMP = 
		new Comparator<TBSet>() {
			public int compare(TBSet s1, TBSet s2) {
				return s1.getCreationDate().compareTo(s2.getCreationDate());
			}
		};
	public static final Comparator<TBGame> TIMEOUT_COMP = 
		new Comparator<TBGame>() {
			public int compare(TBGame g1, TBGame g2) {
				return g1.getTimeoutDate().compareTo(g2.getTimeoutDate());
			}
		};
		
	public static void organizeGames(long pid, List<TBSet> sets, 
		List<TBSet> invitesTo, List<TBSet> invitesFrom, 
		List<TBGame> myTurn, List<TBGame> oppTurn) {
		
		// first split up into different lists
		for (TBSet s : sets) {
			if (s.getState() == TBSet.STATE_NOT_STARTED) {
				if (pid == s.getInviterPid()) {
					invitesFrom.add(s);
				}
				else {
					invitesTo.add(s);
				}
			}
			else if (s.getState() == TBSet.STATE_ACTIVE) {
				for (int i = 0; i < 2; i++) {
					TBGame g = s.getGames()[i];
					if (g == null) break;
					if (g.getState() == TBGame.STATE_ACTIVE) {
                        // if its other players turn but they request
                        // cancel, then it sort of becomes your turn
						if (s.getCancelPid() != 0 && s.getCancelPid() != pid) {
						    myTurn.add(g);
                        }
                        else if (pid == g.getCurrentPlayer()) {
							myTurn.add(g);
						}
						else {
							oppTurn.add(g);
						}
					}
				}
			}
		}
		
		// then sort
		Collections.sort(invitesTo, CREATION_DATE_COMP);
		Collections.sort(invitesFrom, CREATION_DATE_COMP);
		Collections.sort(myTurn, TIMEOUT_COMP);
		Collections.sort(oppTurn, TIMEOUT_COMP);
	}
	
	public static String getTimeLeft(long timeout) {
		long diff = timeout - System.currentTimeMillis();
		int days =  (int) (diff / 86400000);
		diff %= 86400000;
		int hours = (int) (diff / 3600000);
		diff %= 3600000;
		int minutes = (int) (diff / 60000);
		
		if (days > 0) {
			return days + " days, " + hours + " hours";
		}
		else {
			return hours + " hours, " + minutes + " minutes";
		}
	}

	public static long calculateNewTimeout(TBGame game, 
		DSGPlayerStorer dsgPlayerStorer) {
		int weekend[]=new int[] { 7, 1 }; //sat/sun default
		List<Date> vacationDays = null;
		try {
			// get wk1, wk2 for player whose turn it now is
			List l = dsgPlayerStorer.loadPlayerPreferences(game.getCurrentPlayer());
			for (Iterator it = l.iterator(); it.hasNext();) {
				DSGPlayerPreference p = (DSGPlayerPreference) it.next();
				if (p.getName().equals("weekend")) {
					weekend = (int[]) p.getValue();
				}
			}
			vacationDays = dsgPlayerStorer.loadVacationDays(game.getCurrentPlayer());
			
		} catch (DSGPlayerStoreException dpse) {
			log4j.error("Error getting weekend for player " + 
				game.getCurrentPlayer() + ", game=" + game.getGid(), dpse);
		}
		log4j.debug("calculateNewTimeout("+game.getGid()+" for " + game.getCurrentPlayer() +
			", using weekend " + weekend[0] + "," + weekend[1]);
		
		long newTimeout = Utilities.calculateNewTimeout(
			game.getLastMoveDate().getTime(),
			game.getDaysPerMove(), weekend[0], weekend[1], vacationDays);
		log4j.debug("new timeout="+ newTimeout);
		return newTimeout;
	}

	public static long calculateNewTimeout(long startTime, int daysPerMove,
		int wk1, int wk2, List<Date> vacationDays) {

		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(startTime);

		int daysLeft = daysPerMove;
		boolean first = true;

		while (daysLeft > 0) {
			int td = now.get(Calendar.DAY_OF_WEEK);
			int d = now.get(Calendar.DAY_OF_MONTH);
			int month = now.get(Calendar.MONTH);
			int year = now.get(Calendar.YEAR);
			boolean iswk = (td == wk1 || td == wk2);
			boolean isvc = false;
			for (Date vc : vacationDays) {
                 if (vc.getDate() == d && vc.getMonth() == month && vc.getYear() == (year - 1900)) {
                	 isvc = true;
                	 break;
                 }
            }
			if (isvc || iswk) {
				now.add(Calendar.DATE, 1);
				
				// if making move on a weekend day
				//   set time to beginning of next date
				if (first) {
					now.set(Calendar.HOUR_OF_DAY, 0);
					now.set(Calendar.MINUTE, 0);
					now.set(Calendar.SECOND, 0);
				}
			}
			else {
				now.add(Calendar.DATE, 1);
				daysLeft--;
			}
			first = false;
		}
		
		return now.getTimeInMillis();
	}
}
