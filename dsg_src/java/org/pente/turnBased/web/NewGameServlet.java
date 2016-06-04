package org.pente.turnBased.web;

import java.io.*;
import java.util.*;
import java.util.Date;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.game.*;
import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

import org.pente.turnBased.SendNotification;

public class NewGameServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		NewGameServlet.class.getName());

	private static final String redirectPage = "/gameServer/index.jsp";
	private static final String errorRedirectPage = "/gameServer/tb/new.jsp";
	private static final String aiErrorRedirectPage = "/gameServer/tb/newAIgame.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
	
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected http params:
	// invite player - required
	// invitee player - optional
	// game - required
	// days per move - required
	// rated - required
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

		String error = null;
		
		DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
		TBGameStorer tbGameStorer = resources.getTbGameStorer();

        String invitePlayer = (String) request.getAttribute("name");
		DSGPlayerData invitePlayerData = null;
		String inviteePlayer = request.getParameter("invitee");
		DSGPlayerData inviteePlayerData = null;
		String gameStr = request.getParameter("game");
		int game = -1;
		String daysPerMoveStr = request.getParameter("daysPerMove");
		int daysPerMove = -1;
		
		String playAsStr = request.getParameter("playAs");
		int playAs = 1;
		if (playAsStr != null && !playAsStr.equals("")) {
	        try {
	            playAs = Integer.parseInt(playAsStr);
	    	} catch (NumberFormatException nfe) {}
		}

		String ratedStr = request.getParameter("rated");
		boolean rated = false;
		String privateStr = request.getParameter("privateGame");
		boolean privateGame = false;

		char invitationRestriction = TBSet.ANY_RATING;

		
		String inviterMessage = request.getParameter("inviterMessage");
		if (inviterMessage != null && inviterMessage.equals("")) {
			inviterMessage = null;
		}
		if (inviterMessage != null && inviterMessage.length() > 255) {
			inviterMessage = inviterMessage.substring(0, 255);
		}
		
		// player must be logged in, so name will be populated
	    try {
			invitePlayerData = dsgPlayerStorer.loadPlayer(invitePlayer);
	    } catch (DSGPlayerStoreException e) {
	    	log4j.error("Problem loading player " + invitePlayer + 
				" to start turn-based game.", e);
	    	error = "Database error, please try again later.";
	    }
		
		// player to invite is optional
		try {
			if (inviteePlayer != null && !inviteePlayer.equals("")) {
				inviteePlayer = inviteePlayer.trim().toLowerCase();
				inviteePlayerData = dsgPlayerStorer.loadPlayer(inviteePlayer);
				if (inviteePlayerData == null) {
					log4j.debug("NewGameServlet, player to invite not found: " + inviteePlayer);
					error = "Player not found: " + inviteePlayer;
				}
				else if (inviteePlayerData.getPlayerID() == invitePlayerData.getPlayerID()) {
					log4j.error("Trying to invite self " + inviteePlayer);
					error = "You can't invite yourself to a game.";
				}
				else {
					DSGIgnoreData i = resources.getDsgPlayerStorer().getIgnoreData(
						inviteePlayerData.getPlayerID(), invitePlayerData.getPlayerID());
					if (i != null && i.getIgnoreInvite()) {
						log4j.debug("Ignore invitation");
						error = "Player is ignoring your invitations.";
					}
				}
			}
		} catch (DSGPlayerStoreException e) {
	    	log4j.error("Problem loading player " + inviteePlayer + 
				" to start turn-based game.", e);
		    error = "Database error, please try again later.";
		}

		if (gameStr != null) {
			try {
				game = Integer.parseInt(gameStr);
			} catch (NumberFormatException nef) {
		    	log4j.error("Problem with the game to start turn-based game.", nef);
			    error = "Error, parsing the game.";
			}
		}

		
		// if (inviteePlayer != null && !inviteePlayer.equals("") && (error == null)) {

	 //        try {
		// 		String isMobile = (String) request.getParameter("mobile");
		//         ServletContext ctx = getServletContext();
		// 		List<TBSet> currentSets = tbGameStorer.loadSets(invitePlayerData.getPlayerID());
		// 		List<TBSet> invitesTo = new ArrayList<TBSet>();
		// 		List<TBSet> invitesFrom = new ArrayList<TBSet>();
		// 		List<TBGame> myTurn = new ArrayList<TBGame>();
		// 		List<TBGame> oppTurn = new ArrayList<TBGame>();
		// 		Utilities.organizeGames(invitePlayerData.getPlayerID(), currentSets,
		// 		    invitesTo, invitesFrom, myTurn, oppTurn);
		// 		boolean limitExceeded;
		// 		int gamesLimit = Integer.parseInt(ctx.getInitParameter("TBGamesLimit"));
		// 		if (invitePlayerData.unlimitedMobileTBGames() && (isMobile != null)) {
		// 			limitExceeded = false;
		// 		} else if (invitePlayerData.unlimitedTBGames()) {
		// 		  	limitExceeded = false;
		// 		} else {
		// 			int currentCount = myTurn.size() + oppTurn.size();
		// 			if (!invitesFrom.isEmpty()) {
		// 				for (TBSet s : invitesFrom) {
		// 					if (s.isTwoGameSet()) {
		// 						currentCount += 2;
		// 					} else {
		// 						currentCount++;
		// 					}
		// 				}
		// 			}
		// 			if (currentCount > gamesLimit) {
		// 				limitExceeded = true;
		// 			} else {
		// 				limitExceeded = false;
		// 			}
		// 		}

		// 		if (limitExceeded) {
		// 			error = "Free account games limit exceeded.";
		// 		} 
		// 	} catch (TBStoreException tbe) {
		//     	log4j.error("NewGameServlet: ", tbe);
		//     	error = "Database error, please try again later.";
		// 	}
		// } else 
		if (error == null) {
			String invitationRestrictionString = request.getParameter("invitationRestriction");
			if (invitationRestrictionString != null && invitationRestrictionString.length() > 0) {
				invitationRestriction = invitationRestrictionString.charAt(0);
			}
		}


		
		if (game == -1 || game > GridStateFactory.getMaxGameId()) {
			log4j.error("NewGameServlet, invalid game " + gameStr);
			error = "You must select a game to play.";
		}
		
		if (daysPerMoveStr != null) {
			try {
				daysPerMove = Integer.parseInt(daysPerMoveStr);
			} catch (NumberFormatException nef) {}
		}
		if (daysPerMove == -1) {
			log4j.error("NewGameServlet, invalid days per move " + daysPerMoveStr);
			error = "You must specify how long each player has to move.";
		}
		
		if (ratedStr == null || (!ratedStr.equals("Y") && !ratedStr.equals("N"))) {
			log4j.error("NewGameServlet, missing or invalid rated");
			error = "You must specify if the game is rated or not.";
		}
		else {
			rated = ratedStr.equals("Y");
		}
		if (privateStr != null && privateStr.equals("Y")) {
			privateGame = true;
		}


		if (error == null && inviteePlayer != null && inviteePlayer.equals("computer")) {
	        try {
				List<TBSet> currentSets = tbGameStorer.loadSets(invitePlayerData.getPlayerID());
				List<TBSet> invitesTo = new ArrayList<TBSet>();
				List<TBSet> invitesFrom = new ArrayList<TBSet>();
				List<TBGame> myTurn = new ArrayList<TBGame>();
				List<TBGame> oppTurn = new ArrayList<TBGame>();
				Utilities.organizeGames(invitePlayerData.getPlayerID(), currentSets,
				    invitesTo, invitesFrom, myTurn, oppTurn);
	            for (TBGame g : myTurn) {
	                if (g.getPlayer1Pid() == 23000000020606L || g.getPlayer2Pid() == 23000000020606L) {
	                	if (g.getGame() == game) {
							log4j.error("NewGameServlet, already playing computer game");
							error = "You are already playing a game of " + GridStateFactory.getGameName(game) + " against the AI player. You can start a new one after finishing the current one.";
							break;
	                	}
	                }
	            }
	            if (error == null) {
		            for (TBGame g : oppTurn) {
		                if (g.getPlayer1Pid() == 23000000020606L || g.getPlayer2Pid() == 23000000020606L) {
		                	if (g.getGame() == game) {
								log4j.error("NewGameServlet, already playing computer game");
								error = "You are already playing a game of " + GridStateFactory.getGameName(game) + " against the AI player. You can start a new one after finishing the current one.";
								break;
		                	}
		                }
		            }
	            }
			} catch (TBStoreException tbe) {
		    	log4j.error("NewGameServlet: ", tbe);
		    	error = "Database error, please try again later.";
			}

            if (error == null) {
				String difficultyStr = request.getParameter("difficulty");
				int difficulty = 1;
		        try {
		            difficulty = Integer.parseInt(difficultyStr);
		    	} catch (NumberFormatException nfe) {
			    	log4j.error("Problem with the difficulty to start turn-based game.", nfe);
				    error = "Error parsing the difficulty.";
		    	}
		    	if (game != 51 && game != 55) {
			    	log4j.error("NewGameServlet: game not supported by the AI");
				    error = "The AI only supports Gomoku and Pente";
		    	}

	    		if (error == null) {
					long pid1 = 0, pid2 = 0;
					if (!rated && playAs == 1) {
						pid1 = invitePlayerData.getPlayerID();
						pid2 = 23000000020606L;
					} else {
						pid2 = invitePlayerData.getPlayerID();
						pid1 = 23000000020606L;
					}
			    	((CacheTBStorer) tbGameStorer).createAISet(game, pid1, pid2, daysPerMove, rated, difficulty);
	    		}
    		}
		} else if (error == null) {
			try {
				
				TBGame tbg = null;
				TBGame tbg2 = null;
				TBSet tbs = null;
				long pid1 = 0, pid2 = 0;
				if (rated) {
					tbg = createGame(1, invitePlayerData, inviteePlayerData,
						game, daysPerMove, rated);
					
					tbg2 = createGame(2, invitePlayerData, inviteePlayerData,
						game, daysPerMove, rated);

					pid1 = invitePlayerData.getPlayerID();
					if (inviteePlayerData != null) {
						pid2 = inviteePlayerData.getPlayerID();
					}
				}
				else {
					tbg = createGame(playAs, invitePlayerData, inviteePlayerData,
						game, daysPerMove, rated);
					
					if (playAs == 1) {
						pid1 = invitePlayerData.getPlayerID();
						if (inviteePlayerData != null) {
							pid2 = inviteePlayerData.getPlayerID();
						}
					}
					else {
						pid2 = invitePlayerData.getPlayerID();
						if (inviteePlayerData != null) {
							pid1 = inviteePlayerData.getPlayerID();
						}
					}
						
				}
				
				tbs = new TBSet(tbg, tbg2);
				tbs.setPlayer1Pid(pid1);
				tbs.setPlayer2Pid(pid2);
				tbs.setInviterPid(invitePlayerData.getPlayerID());
				tbs.setPrivateGame(privateGame);
				tbs.setInvitationRestriction(invitationRestriction);
				tbGameStorer.createSet(tbs);

				if (inviteePlayerData != null) {
					ServletContext ctx = getServletContext();
					String penteLiveGCMkey = ctx.getInitParameter("penteLiveGCMkey");
					String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
					String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
					boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
					Thread thread = new Thread(new SendNotification(2, tbs.getSetId(), invitePlayerData.getPlayerID(), inviteePlayerData.getPlayerID(), 
						GridStateFactory.getGameName(game), penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, resources.getDbHandler(), penteLiveGCMkey));
					thread.start();
				}

				if (inviterMessage != null) {
					TBMessage m = new TBMessage();
					m.setPid(invitePlayerData.getPlayerID());
					m.setDate(new Date());
					m.setMessage(inviterMessage);
					m.setMoveNum(0);
					m.setSeqNbr(1);
					tbGameStorer.storeNewMessage(tbg.getGid(), m);
					if (tbg2 != null) {
						tbGameStorer.storeNewMessage(tbg2.getGid(), m);
					}
				}

				
			} catch (Throwable throwable) {
				log4j.error("Problem creating set", throwable);
				error = "Database error creating set, try again later.";
			}
		}
		
		
		if (error != null) {
    		request.setAttribute("error", error);
    		if (request.getParameter("difficulty") == null) {
		       	getServletContext().getRequestDispatcher(errorRedirectPage).forward(
	                request, response);
    		} else {
		       	getServletContext().getRequestDispatcher(aiErrorRedirectPage).forward(
	                request, response);
    		}
		} else {
			String isMobile = (String) request.getParameter("mobile");
			if (isMobile == null) {
	            response.sendRedirect(request.getContextPath() + redirectPage);
			} else {
		        response.sendRedirect(mobileRedirectPage);
			}
		}
    }
	

	private TBGame createGame(int player, DSGPlayerData invitePlayer,
		DSGPlayerData inviteePlayer, int game, int daysPerMove, boolean rated) throws Throwable {

		TBGame tbg = new TBGame();
		tbg.setGame(game);
		tbg.setDaysPerMove(daysPerMove);
		tbg.setRated(rated);
		
		if (player == 1) {
			tbg.setPlayer1Pid(invitePlayer.getPlayerID());
			if (inviteePlayer != null) {
				tbg.setPlayer2Pid(inviteePlayer.getPlayerID());
			}
		}
		else {
			tbg.setPlayer2Pid(invitePlayer.getPlayerID());
			if (inviteePlayer != null) {
				tbg.setPlayer1Pid(inviteePlayer.getPlayerID());
			}
		}
		
		return tbg;
	}
}
