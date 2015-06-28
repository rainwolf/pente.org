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

import org.pente.turnBased.SendNotification;

public class MoveServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		MoveServlet.class.getName());

	private static final String gamePage = "/gameServer/tb/game.jsp";
	private static final String errorRedirectPage = "/gameServer/tb/error.jsp";
	private static final String moveRedirectPage = "/gameServer/index.jsp";
	private static final String cancelRedirectPage = "/gameServer/tb/cancelReply.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
    
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected params:
	// player - required (user logged in so will be there)
	// gid - required
	// command - load, move - required
	// gowhere - optional
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
        TBSet set = null;
        String command = (String) request.getParameter("command");
		
        String attachStr = request.getParameter("attach");
        
		if (command == null) {
			log4j.error("MoveServlet, invalid command");
			handleError(request, response, "Invalid command.");
			return;
		}
		log4j.debug("MoveServlet, command: " + command);

		// player must be logged in, so name will be populated
	    try {
			playerData = dsgPlayerStorer.loadPlayer(player);
	    } catch (DSGPlayerStoreException e) {
	    	log4j.error("MoveServlet, problem loading player " + player, e);
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
			log4j.error("MoveServlet, invalid gid " + gidStr);
			handleError(request, response, "No game or invalid game.");
			return;
		}
		try {
			game = tbGameStorer.loadGame(gid);
            set = game.getTbSet();
			if (game == null || set == null) {
				log4j.error("MoveServlet, invalid game, storer returned null " + gid);
				handleError(request, response, "Game not found.");
				return;
			}

			request.setAttribute("game", game);
		
			try {
				DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(game.getPlayer1Pid());
				DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(game.getPlayer2Pid());
				request.setAttribute("p1", p1);
				request.setAttribute("p2", p2);
				
			} catch (DSGPlayerStoreException dpse) {
				log4j.error("MoveServlet, error loading player data", dpse);
				handleError(request, response, "Database error");
				return;
			}
			
			if (command.equals("load")) {
				if (game.getState() == TBGame.STATE_ACTIVE &&
					game.getCurrentPlayer() == playerData.getPlayerID()) {
					request.setAttribute("myTurn", "true");
				}
				if (playerData.getPlayerID() != game.getPlayer1Pid() &&
					playerData.getPlayerID() != game.getPlayer2Pid()) {

					if (!playerData.isAdmin() && 
						game.getState() == TBGame.STATE_ACTIVE) {
						log4j.error("MoveServlet, game state invalid " + gid);
						handleError(request, response, "Invalid game, game active and other player trying to view it.");
						return;
					}

					// else if complete, show game but restrict messages
					request.setAttribute("showMessages", new Boolean(playerData.isAdmin()));
				}

                // if someone requested a cancel for the set
                // and this game is active
                // and the requestor is not this player
                if (set.getCancelPid() != 0 && 
                    game.getState() == TBGame.STATE_ACTIVE &&
                    set.getCancelPid() != playerData.getPlayerID()) {
                    log4j.debug("forward to cancel reply page");

                    request.setAttribute("set", set);
                    getServletContext().getRequestDispatcher(cancelRedirectPage).forward(
                        request, response);
                    return;
                }
                
                // if player prefers to make moves attached or not
				List prefs = dsgPlayerStorer.loadPlayerPreferences(
					playerData.getPlayerID());
            	for (Iterator it = prefs.iterator(); it.hasNext();) {
            		DSGPlayerPreference p = (DSGPlayerPreference) it.next();
            		if (p.getName().equals("attach")) {
            			request.setAttribute("attach", p.getValue());
            		}
            	}
				
                
                
				log4j.debug("forward to game page");
		       	getServletContext().getRequestDispatcher(gamePage).forward(
		            request, response);
		       	log4j.debug("done forwarding");
		       	return;
			}
			else if (command.equals("move")) {
				
// log4j.debug("************current player initial pid " + game.getCurrentPlayer());

				if (game.getCurrentPlayer() != playerData.getPlayerID()) {
					log4j.debug("MoveServlet, " + playerData.getName() + "" +
							"attempted to make move out of turn: " + game.getGid());
					handleError(request, response, "Its not your turn.");
					return;
				}
				if (game.getState() != TBGame.STATE_ACTIVE) {
					log4j.error("MoveServlet, game state invalid " + gid);
					handleError(request, response, "Invalid game, game not active.");
					return;
				}
				
				// handle dpente stuff here, underlying code doesn't need
				// to change
				
				// load moves
				int moves[] = null;
				String moveStr = request.getParameter("moves");
				if ((moveStr != null) && !"(null)".equals(moveStr)) {
					try {
						StringTokenizer st = new StringTokenizer(moveStr, ",");
						moves = new int[st.countTokens()];
						for (int i = 0; i < moves.length; i++) {
							moves[i] = Integer.parseInt(st.nextToken());
						}
					} catch (NumberFormatException nfe) {}
				}
				if (moves == null) {
					log4j.error("MoveServlet, invalid moves " + moveStr + ": " + 
						game.getGid());
					handleError(request, response, "Invalid moves.");
					return;
				}

				String msg = request.getParameter("message");
				TBMessage message = null;
				if (msg != null && !msg.trim().equals("")) {
					message = new TBMessage();
					message.setMessage(msg);
					message.setDate(new java.util.Date());
					message.setMoveNum(game.getNumMoves() + 1);
					// default seq nbr = 1
					message.setSeqNbr(1);
					message.setPid(game.getCurrentPlayer());
				}

				// handle dpente separately
				if (game.getGame() == GridStateFactory.TB_DPENTE &&
					game.getDPenteState() != TBGame.DPENTE_STATE_DECIDED) {

					log4j.debug("MoveServlet, handle dpente move");
					if (game.getDPenteState() == TBGame.DPENTE_STATE_START) {
						if (moves.length != 3) {
							log4j.error("MoveServlet, dpente game start, " +
								"expected 3 moves.");
							handleError(request, response, "Invalid start of dpente.");
							return;
						}
						log4j.debug("MoveServlet, handle dpente start");
						
						tbGameStorer.updateDPenteState(game, TBGame.DPENTE_STATE_DECIDE);

						for (int i = 0; i < 2; i++) {
							tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
								moves[i]);
						}

						tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
							moves[2]);
						if (message != null) {
							message.setMoveNum(4);
							tbGameStorer.storeNewMessage(game.getGid(), message);
						}
					}
					else if (game.getDPenteState() == TBGame.DPENTE_STATE_DECIDE) {

						log4j.debug("MoveServlet, handle dpente decision");

						boolean swap = moves[0] == 1;
						tbGameStorer.dPenteSwap(game, swap);

						// didn't swap but still might have written message
						if (!swap && message != null) {
							// set seq nbr
							log4j.debug("MoveServlet, no swap record message");
							message.setMoveNum(4);
							message.setSeqNbr(2);
							tbGameStorer.storeNewMessage(game.getGid(), message);
						}
						else if (swap) {
							log4j.debug("MoveServlet, swap, " + moves[1]);
							game.setDPenteSwapped(true);
							tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
								moves[1]);
							if (message != null) {
								message.setMoveNum(5);
								tbGameStorer.storeNewMessage(game.getGid(), message);
							}
						}

					}
				}
				else if (game.getGame() == GridStateFactory.TB_CONNECT6) {
					log4j.debug("MoveServlet, store moves " + moves[0] + "," + moves[1]);
					if (moves.length != 2) {
						log4j.error("MoveServlet, more moves received than, " +
							"expected.");
						handleError(request, response, "Invalid move.");
						return;
					}
					tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
						moves[0]);
					// this will not add the 2nd move if the player
					// won the game on the 1st move
					if (!game.isCompleted()) {
						tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
							moves[1]);
					}
					if (message != null) {
						message.setMoveNum(game.getNumMoves());
						tbGameStorer.storeNewMessage(game.getGid(), message);
					}
				}
				else {
					log4j.debug("MoveServlet, store move " + moves[0]);
					if (moves.length != 1) {
						log4j.error("MoveServlet, more moves received than, " +
							"expected.");
						handleError(request, response, "Invalid move.");
						return;
					}
					tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
						moves[0]);
					if (message != null) {
						tbGameStorer.storeNewMessage(game.getGid(), message);
					}
				}


// log4j.debug("************current player pid " + game.getCurrentPlayer());


				if (!game.isCompleted()) {
					ServletContext ctx = getServletContext();
					String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
					String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
					boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
					Thread thread = new Thread(new SendNotification(1, game.getGid(), (game.getCurrentPlayer() == game.getPlayer1Pid())?game.getPlayer2Pid():game.getPlayer1Pid(), game.getCurrentPlayer(), 
						GridStateFactory.getGameName(game.getGame()), penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, resources.getDbHandler() ) );
					thread.start();
				}


				//redirect to somewhere
				String isMobile = (String) request.getParameter("mobile");
				if (isMobile == null) {
			        response.sendRedirect(moveRedirectPage);
				} else {
			        response.sendRedirect(mobileRedirectPage);
				}
			}
			
		} catch (TBStoreException tbe) {
	    	log4j.error("MoveServlet: " + gid, tbe);
	    	handleError(request, response, "Database error, please try again later.");
		} catch (Throwable t) {
			log4j.error("MoveServlet: " + gid, t);
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
