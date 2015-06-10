package org.pente.turnBased.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.game.*;
import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class ResignServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		ResignServlet.class.getName());

	private static final String errorRedirectPage = "/gameServer/tb/error.jsp";
	private static final String confirmRedirectPage = "/gameServer/tb/confirmResign.jsp";
	private static final String resignRedirectPage = "/gameServer/index.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
	
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected params:
	// player - required (user logged in so will be there)
    // command - confirm or resign
	// gid - required
	// message - optional
    public void doGet(HttpServletRequest request,
            		  HttpServletResponse response)
		throws ServletException, IOException {
			doPost(request, response);
	}

	public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

		String error = null;
		
		DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
		TBGameStorer tbGameStorer = resources.getTbGameStorer();

        String player = (String) request.getAttribute("name");
		DSGPlayerData playerData = null;
		
		String gidStr = (String) request.getParameter("gid");
		long gid = 0;
		TBGame game = null;

		// player must be logged in, so name will be populated
	    try {
			playerData = dsgPlayerStorer.loadPlayer(player);
	    } catch (DSGPlayerStoreException e) {
	    	log4j.error("ResignServlet, problem loading player " + player, e);
	    	handleError(request, response, 
				"Database error, please try again later.");
			return;
	    }
			
		if (gidStr != null) {
			try {
				gid = Long.parseLong(gidStr);
			} catch (NumberFormatException nef) {}
		}
		if (gid == 0) {
			log4j.error("ResignServlet, invalid gid " + gidStr);
			handleError(request, response, "No game or invalid game.");
			return;
		}
		
		String command = request.getParameter("command");
		if (command == null || command.equals("")) {
			log4j.error("ResignServlet, invalid command");
			handleError(request, response, "Invalid command.");
			return;
		}
		String msg = request.getParameter("message");
		
		try {
			
			game = tbGameStorer.loadGame(gid);
			if (game == null) {
				log4j.error("ResignServlet, invalid game, storer returned null " + gid);
				handleError(request, response, "Game not found.");
				return;
			}
			else if (game.getState() != TBGame.STATE_ACTIVE) {
				log4j.error("ResignServlet, not active " + gid);
				handleError(request, response, "Game not active.");
				return;
			}

			request.setAttribute("game", game);
			
			if (command.equals("confirm")) {
				if (msg == null || msg.trim().equals("")) msg = "";
				request.setAttribute("message", msg);
				
				try {
					DSGPlayerData opponent = dsgPlayerStorer.loadPlayer(
						game.getOpponent(playerData.getPlayerID()));
					request.setAttribute("opponent", opponent);
					
				} catch (DSGPlayerStoreException dpse) {
					log4j.error("ResignServlet, error loading player data", dpse);
					handleError(request, response, "Database error");
					return;
				}
				
				getServletContext().getRequestDispatcher(confirmRedirectPage).
					forward(request, response);
				return;				
			}
			else if (command.equals("resign")) {

				if (playerData.getPlayerID() != game.getCurrentPlayer()) {
					log4j.error("ResignServlet, not current player" + gid + 
						"," + playerData.getPlayerID());
					handleError(request, response, "Not your turn.");
					return;
				}

				TBMessage message = null;
				if (msg != null && !msg.trim().equals("")) {
					message = new TBMessage();
					message.setMessage(msg);
					message.setDate(new java.util.Date());
					// add message after other players since not making a move
					message.setMoveNum(game.getNumMoves());
					message.setSeqNbr(2);
					message.setPid(game.getCurrentPlayer());
				}
				tbGameStorer.resignGame(game);
				if (message != null) {
					tbGameStorer.storeNewMessage(game.getGid(), message);
				}
				
				String isMobile = (String) request.getParameter("mobile");
				if (isMobile == null) {
					getServletContext().getRequestDispatcher(resignRedirectPage).forward(request, response);
				} else {
					getServletContext().getRequestDispatcher(mobileRedirectPage).forward(request, response);
				}
			}

		} catch (TBStoreException tbe) {
	    	log4j.error("ResignServlet: " + gid, tbe);
	    	handleError(request, response, "Database error, please try again later.");
		} catch (Throwable t) {
			log4j.error("ResignServlet: " + gid, t);
			handleError(request, response, "Unknown error.");
		}

    }
	
	private void handleError(HttpServletRequest request,
            HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
		request.setAttribute("error", errorMessage);
       	getServletContext().getRequestDispatcher(errorRedirectPage).forward(
            request, response);
	}
}
