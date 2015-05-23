package org.pente.turnBased.web;

import java.io.*;
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
			} catch (NumberFormatException nef) {}
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
		
		if (error == null) {
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
				tbGameStorer.createSet(tbs);

				if (inviteePlayerData != null) {
					ServletContext ctx = getServletContext();
					String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
					String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
					boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
					Thread thread = new Thread(new SendNotification(2, tbs.getSetId(), invitePlayerData.getPlayerID(), inviteePlayerData.getPlayerID(), 
						GridStateFactory.getGameName(game), penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, resources.getDbHandler() ) );
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
	       	getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                request, response);
		}
		else {
            response.sendRedirect(request.getContextPath() + redirectPage);
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
