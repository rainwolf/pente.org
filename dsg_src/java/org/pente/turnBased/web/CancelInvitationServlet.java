package org.pente.turnBased.web;

import java.io.*;
import java.util.List;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class CancelInvitationServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		CancelInvitationServlet.class.getName());

	private static final String successPage = "/gameServer/index.jsp";
	private static final String loadRedirectPage = "/gameServer/tb/cancelInvitation.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
	
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected params:
	// player - required (user logged in so will be there)
	// sid - required
	// command - load, Cancel - required
	// if load - redirect to jsp, put game, inviteedata in request
	// if accept/decline - redirect to index
    public void doGet(HttpServletRequest request,
            		  HttpServletResponse response)
		throws ServletException, IOException {
			doPost(request, response);
	}

	public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {
		
		DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
		TBGameStorer tbGameStorer = resources.getTbGameStorer();

        String inviterPlayer = (String) request.getAttribute("name");
		DSGPlayerData inviter = null;
		DSGPlayerData invitee = null;
		
		String sidStr = (String) request.getParameter("sid");
		long sid = 0;
		TBSet set = null;
        String command = (String) request.getParameter("command");
		
		if (command == null) {
			command = "load";
		}
		log4j.debug("CancelInvitationServlet, command=" + command);
		
		if (sidStr != null) {
			try {
				sid = Long.parseLong(sidStr);
			} catch (NumberFormatException nef) {}
		}
		if (sid == 0) {
			log4j.error("CancelInvitationServlet, invalid sid " + sidStr);
			handleError(request, response, "No set or invalid set.",
				loadRedirectPage);
			return;
		}

		try {

			set = tbGameStorer.loadSet(sid);
			if (set.getState() != TBSet.STATE_NOT_STARTED) {
				log4j.error("CancelInvitationServlet, game state invalid");
				handleError(request, response, "Invalid set, already started.",
					loadRedirectPage);
				return;
			}
			
			// player must be logged in, so name will be populated
		    try {
				inviter = dsgPlayerStorer.loadPlayer(inviterPlayer);
				invitee = dsgPlayerStorer.loadPlayer(set.getInviteePid());
		    } catch (DSGPlayerStoreException e) {
		    	log4j.error("Problem loading inviter " + inviterPlayer + 
					" to cancel turn-based game.", e);
				handleError(request, response, "Database error, please try again later.",
					loadRedirectPage);
				return;
		    }

//             beginner sets cannot be canceled
            if (set.getInvitationRestriction() == TBSet.BEGINNER) {
                List<TBSet> waitingSets = tbGameStorer.loadWaitingSets();
                int total = 0, thisGame = 0;
                for (TBSet s: waitingSets) {
                    if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                        total++;
                        if (s.getGame1() != null && set.getGame1() != null && s.getGame1().getGame() == set.getGame1().getGame()) {
                            thisGame++;
                        }
                    }
                }
                if (thisGame < 5 || thisGame < total/2) {
                    log4j.error("Beginner sets cannot be canceled.");
                    handleError(request, response, "Beginner sets cannot be canceled.",
                            loadRedirectPage);
                    return;
                }
            }


            // check that either invitation is open (no invitee) or that this
			// player is the invitee
			if (set.getInviterPid() != inviter.getPlayerID()) {
				log4j.error("CancelInvitationServlet, invalid player");
				handleError(request, response, "Invalid set, you are not the inviter.",
					loadRedirectPage);
				return;
			}
		
			if (command.equals("load")) {
				log4j.debug("CancelInvitationServlet, load");

				request.setAttribute("set", set);
	    		request.setAttribute("invitee", invitee);
	    		request.setAttribute("inviter", inviter);
		       	getServletContext().getRequestDispatcher(loadRedirectPage).forward(
		            request, response);
		       	return;
			}
			else if (command.equals("Cancel")) {

				tbGameStorer.cancelSet(set);

				String isMobile = (String) request.getParameter("mobile");
				if (isMobile == null) {
					response.sendRedirect(request.getContextPath() + successPage);
				} else {
			        response.sendRedirect(mobileRedirectPage);
				}
				return;
			}
			
		} catch (TBStoreException tbe) {
	    	log4j.error("CancelInviteServlet: " + sid, tbe);
	    	handleError(request, response, 
				"Database error, please try again later.", loadRedirectPage);
		}

    }
	
	private void handleError(HttpServletRequest request,
            HttpServletResponse response, String errorMessage, String page) throws ServletException,
            IOException {
		request.setAttribute("error", errorMessage);
       	getServletContext().getRequestDispatcher(page).forward(
            request, response);
	}
}
