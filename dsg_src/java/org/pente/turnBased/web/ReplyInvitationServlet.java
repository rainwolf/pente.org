package org.pente.turnBased.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.game.*;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.Hill;
import org.pente.kingOfTheHill.KOTHException;
import org.pente.kingOfTheHill.KOTHStorer;
import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.message.*;

import org.apache.log4j.*;

public class ReplyInvitationServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		ReplyInvitationServlet.class.getName());

	private static final String successPage = "/gameServer/index.jsp";
	private static final String loadRedirectPage = "/gameServer/tb/replyInvitation.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
	
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected params:
	// accepting player - required (user logged in so will be there)
	// sid - required
	// command - load, accept or decline - required
	// invitee message response - optional
    // ignore - optional
	// if load - redirect to jsp, put game, inviterdata in request
	// if accept/decline - redirect to index
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
		DSGMessageStorer dsgMessageStorer = resources.getDsgMessageStorer();

        String inviteePlayer = (String) request.getAttribute("name");
		DSGPlayerData invitee = null;
		DSGPlayerData inviter = null;
		
		String sidStr = (String) request.getParameter("sid");
		long sid = 0;
		TBSet set = null;
        String command = (String) request.getParameter("command");
		
        log4j.info("Reply Invitation: " + inviteePlayer + ","+ sidStr + "," + command);
        
		if (command == null) {
			error = "Invalid command.";
			log4j.error("ReplyInvitationServlet, invalid command");
		}
		else {
			
			// player must be logged in, so name will be populated
		    try {
				invitee = dsgPlayerStorer.loadPlayer(inviteePlayer);
		    } catch (DSGPlayerStoreException e) {
		    	log4j.error("Problem loading invitee " + inviteePlayer + 
					" to accept/decline turn-based game.", e);
		    	error = "Database error, please try again later.";
		    }
			
			if (sidStr != null) {
				try {
					sid = Long.parseLong(sidStr);
				} catch (NumberFormatException nef) {}
			}
			if (sid == 0) {
				log4j.error("ReplyInvitationServlet, invalid sid " + sidStr);
				error = "No set or invalid set.";
			}
			try {
				set = tbGameStorer.loadSet(sid);
				if (set.getState() != TBSet.STATE_NOT_STARTED) {
					log4j.error("ReplyInvitationServlet, set state invalid");
				    error = "Invalid set, already started.";
				}
				// check that either invitation is open (no invitee) or that this
				// player is the invitee
				if (set.getInviteePid() != 0) {
				    if (set.getInviteePid() != invitee.getPlayerID()) {
						log4j.error("ReplyInvitationServlet, invalid player");
				        error = "Invalid set, you were not invited.";
				    }
				}
			
				//TODO check that inviter doesn't do anything here, can't accept
				// own invite
				
				if (error == null && command.equals("load")) {
					log4j.debug("ReplyInvitationServlet, load");
				    try {
		                inviter = dsgPlayerStorer.loadPlayer(set.getInviterPid());
						
						request.setAttribute("set", set);
			    		request.setAttribute("inviter", inviter);
				       	getServletContext().getRequestDispatcher(loadRedirectPage).forward(
				            request, response);
				       	return;
						
				    } catch (DSGPlayerStoreException e) {
				    	log4j.error("Problem loading inviter " + set.getInviterPid() + 
							" to accept/decline turn-based set.", e);
				    	error = "Database error, please try again later.";
				    }
				}
				else if (error == null) {

					String inviteeMessage = request.getParameter("inviteeMessage");
					if (inviteeMessage != null && inviteeMessage.equals("")) {
						inviteeMessage = null;
					}
					if (inviteeMessage != null && inviteeMessage.length() > 255) {
						inviteeMessage = inviteeMessage.substring(0, 255);
					}


					if (request.getParameter("ignore") != null &&
						request.getParameter("ignore").equals("Y")) {
						
						try {
							
							DSGIgnoreData d = dsgPlayerStorer.getIgnoreData(
								invitee.getPlayerID(), set.getInviterPid());
			        		if (d != null) {
			        			d.setIgnoreInvite(true);
			        			dsgPlayerStorer.updateIgnore(d);
			        		}
			        		else {
				        		d = new DSGIgnoreData();
				        		d.setPid(invitee.getPlayerID());
				        		d.setIgnorePid(set.getInviterPid());
				        		d.setIgnoreInvite(true);
				        		dsgPlayerStorer.insertIgnore(d);
			        		}
						} catch (DSGPlayerStoreException dpse) {
							log4j.error("Error saving ignore", dpse);
						}
					}
					
					if (command.equals("Accept")) {

						String isMobile = (String) request.getParameter("mobile");
//				        ServletContext ctx = getServletContext();
//						List<TBSet> currentSets = tbGameStorer.loadSets(invitee.getPlayerID());
//						List<TBSet> invitesTo = new ArrayList<TBSet>();
//						List<TBSet> invitesFrom = new ArrayList<TBSet>();
//						List<TBGame> myTurn = new ArrayList<TBGame>();
//						List<TBGame> oppTurn = new ArrayList<TBGame>();
//						Utilities.organizeGames(invitee.getPlayerID(), currentSets,
//						    invitesTo, invitesFrom, myTurn, oppTurn);
//						boolean limitExceeded;
//						int gamesLimit = Integer.parseInt(ctx.getInitParameter("TBGamesLimit"));
//						if (invitee.unlimitedMobileTBGames() && (isMobile != null)) {
//							limitExceeded = false;
//						} else if (invitee.unlimitedTBGames()) {
//						  	limitExceeded = false;
//						} else {
//							int currentCount = myTurn.size() + oppTurn.size();
//							if (!invitesFrom.isEmpty()) {
//								for (TBSet s : invitesFrom) {
//									if (s.isTwoGameSet()) {
//										currentCount += 2;
//									} else {
//										currentCount++;
//									}
//								}
//							}
//							if (currentCount > gamesLimit) {
//								limitExceeded = true;
//							} else {
//								limitExceeded = false;
//							}
//						}
//
//						if (limitExceeded) {
//							error = "Free account games limit exceeded.";
//						} else {

                        if (set.isTwoGameSet()) {
                            CacheKOTHStorer kothStorer = (CacheKOTHStorer) resources.getKOTHStorer();
                            int game = set.getGame1().getGame();
                            if (kothStorer.getEventId(game) == set.getGame1().getEventId()) {
                                Hill hill = kothStorer.getHill(game);
                                if (hill != null) {
                                    long pid1 = set.getInviterPid(), pid2 = invitee.getPlayerID();
//                                    int stepsBetween = hill.stepsBetween(pid1, pid2);
//                                    if (stepsBetween < 0) {
//                                        stepsBetween *= -1;
//                                    }
                                    if (!hill.hasPlayer(pid1)) {
                                        error = "The inviter hasn't joined the King of the Hill for turn-based " + GridStateFactory.getGameName(game);
										for (int i = 0; i < 2; i++) {
											TBGame g = set.getGames()[i];
											if (g == null) {
												continue;
											}
											int eventID = tbGameStorer.getEventId(game);
											g.setEventId(eventID);
											tbGameStorer.setGameEventId(g.getGid(), eventID);
										}
                                    } else if (!hill.hasPlayer(pid2)) {
                                        error = "You haven't joined the King of the Hill for turn-based " + GridStateFactory.getGameName(game) + " yet.";
//                                    } else if (stepsBetween*stepsBetween > 4) {
//                                        error = "The inviter is " + stepsBetween + " apart from you, it should be 2 or less.";
//                                    } else if (!invitee.hasPlayerDonated() && !kothStorer.canPlayerBeChallenged(game, pid2)) {
//                                        error = "You are already playing 2 or more King of the Hill games for turn-based " + GridStateFactory.getGameName(game) + ", subscribers don't have this limit.";
//                                    } else if (!kothStorer.canPlayerBeChallenged(game, pid1)) {
//                                        error = "You are already playing 2 or more King of the Hill games for turn-based " + GridStateFactory.getGameName(game) + ", they cannot accept more at this time.";
                                    }
                                }
                            }
                        }

                        if (error == null) {
							log4j.debug("ReplyInvitationServlet, accept");
							tbGameStorer.acceptInvite(set, invitee.getPlayerID());
							if (inviteeMessage != null) {
								TBMessage m = new TBMessage();
								m.setDate(new Date());
								m.setMessage(inviteeMessage);
								m.setMoveNum(0);
								m.setSeqNbr(2);
								m.setPid(invitee.getPlayerID());
								for (int i = 0; i < 2; i++) {
									TBGame game = set.getGames()[i];
									if (game == null) continue;
									tbGameStorer.storeNewMessage(game.getGid(), m);
								}
							}
							
							if (isMobile == null) {
						        response.sendRedirect(request.getContextPath() + successPage);
							} else {
						        response.sendRedirect(mobileRedirectPage);
							}
					        return;
                        }
//						}
					}
					else if (command.equals("Decline")) {

						log4j.debug("ReplyInvitationServlet, decline");
						
						try {
							tbGameStorer.cancelSet(set);
							
							DSGMessage message = new DSGMessage();
							message.setCreationDate(new Date());
							message.setFromPid(set.getInviteePid());
							message.setToPid(set.getInviterPid());
							message.setSubject("Decline your invitation");
							String msg = invitee.getName() + " has declined your invitation " +
								" to play " + GridStateFactory.getGameName(set.getGame1().getGame()) + ".\n\n";
							if (inviteeMessage != null) {
								msg += inviteeMessage;
							}
							message.setBody(msg);
							dsgMessageStorer.createMessage(message);

                            if (set.isTwoGameSet()) {
                                CacheKOTHStorer kothStorer = (CacheKOTHStorer) resources.getKOTHStorer();
                                int game = set.getGame1().getGame();
                                if (kothStorer.getEventId(game) == set.getGame1().getEventId() && set.getGame1().getDaysPerMove() >= 5) {
                                    Hill hill = kothStorer.getHill(game);
                                    if (hill != null) {
                                        long pid1 = set.getInviterPid(), pid2 = invitee.getPlayerID(), kingPid = hill.getKing();
                                        try {
                                            inviter = dsgPlayerStorer.loadPlayer(set.getInviterPid());
                                        } catch (DSGPlayerStoreException e) {
                                            log4j.error("Problem loading inviter " + set.getInviterPid() +
                                                    " to decline turn-based koth set.", e);
                                            error = "Database error, please try again later.";
                                        }
                                        if (pid2 == kingPid) {
                                            kothStorer.movePlayersUpDown(game, 0L, pid2);
                                            message = new DSGMessage();
                                            message.setCreationDate(new Date());
                                            message.setFromPid(23000000016237L);
                                            message.setToPid(pid2);
                                            message.setSubject("Hill invitation declined");
                                            msg = "You declined a KotH (" + GridStateFactory.getGameName(set.getGame1().getGame()) + 
                                                    ") invitation from " + inviter.getName() + 
                                                    ".\n When you are king of that hill and the timeout is 5 days per move or more, " +
                                                    "declining an invitation results in losing the top step.\n\n";
                                            message.setBody(msg);
                                            dsgMessageStorer.createMessage(message);
                                        } else {
                                            DSGPlayerGameData inviteeGameData = invitee.getPlayerGameData(game), inviterGameData = inviter.getPlayerGameData(game);
                                            Double inviteeRating = 1600.0, inviterRating = 1600.0;
                                            if (inviteeGameData != null) {
                                                inviteeRating = inviteeGameData.getRating();
                                            }
                                            if (inviterGameData != null) {
                                                inviterRating = inviterGameData.getRating();
                                            }
                                            if (inviteeRating > inviterRating) {
                                                kothStorer.movePlayersUpDown(game, 0L, pid2);
                                                message = new DSGMessage();
                                                message.setCreationDate(new Date());
                                                message.setFromPid(23000000016237L);
                                                message.setToPid(pid2);
                                                message.setSubject("Hill invitation declined");
                                                msg = "You declined a KotH (" + GridStateFactory.getGameName(set.getGame1().getGame()) +
                                                        ") invitation from " + inviter.getName() +
                                                        ".\n When the challenger's rating is lower than yours " + 
                                                        "and the timeout is 5 days per move or more, " +
                                                        "declining an invitation results in moving down a step.\n\n";
                                                message.setBody(msg);
                                                dsgMessageStorer.createMessage(message);
                                            }
                                        }
                                    }
                                }
                            }

                        } catch (DSGMessageStoreException dmse) {
							log4j.error("Problem sending message for decline.", dmse);
						}
                        
						if (error == null) {
                            String isMobile = (String) request.getParameter("mobile");
                            if (isMobile == null) {
                                response.sendRedirect(request.getContextPath() + successPage);
                            } else {
                                response.sendRedirect(mobileRedirectPage);
                            }
                            return;
                        }
					}
				}
				
			} catch (TBStoreException tbe) {
		    	log4j.error("ReplyInviteServlet: " + sid, tbe);
		    	error = "Database error, please try again later.";
			}
		}
		
		
		if (error != null) {
			log4j.error("ReplyInvitation failed: " + error);
    		request.setAttribute("error", error);
	       	getServletContext().getRequestDispatcher(loadRedirectPage).forward(
                request, response);
		}

    }
}
