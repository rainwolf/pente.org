package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameDatabase.GameStorerSearchRequestData;
import org.pente.gameDatabase.GameStorerSearchRequestFilterData;
import org.pente.gameDatabase.GameStorerSearchResponseData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestFilterData;
import org.pente.gameDatabase.SimpleGameStorerSearchResponseData;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.turnBased.*;

public class ViewGameServlet extends HttpServlet {

	private static final Category log4j = Category.getInstance(
		ViewGameServlet.class.getName());

    private Resources resources;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            resources = (Resources) ctx.getAttribute(Resources.class.getName());
            
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }
    
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
        doPost(request, response);
    }

    // params
    // g = gid, required
    // e, optional means to embed
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

        String redirectPage = "/gameServer/viewLiveGame.jsp";
        try {
        	String gidStr = (String) request.getParameter("g");
            
        	long gid = -1;
        	if (gidStr != null) {
	        	try {
		        	gid = Long.parseLong(gidStr);
	        	} catch (NumberFormatException n) {}
        	}
        	if (gid == -1) {
        		log4j.error("ViewGameServlet, gid not found or invalid");
        		handleError(request, response,
        			"Game id invalid, please try again.", redirectPage);
        		return;
        	}

	        String player = (String) request.getAttribute("name");
	        DSGPlayerData playerData = null;
        	try {
	        	playerData = resources.getDsgPlayerStorer().
	        		loadPlayer(player);

        	} catch (DSGPlayerStoreException dpse) {
        		log4j.error("Error getting attach", dpse);
        	}
        	
        	GameData game = new DefaultGameData();
        	resources.getGameStorer().loadGame(gid, game);
        	
        	if (game.isPrivateGame() && 
        		playerData.getPlayerID() != game.getPlayer1Data().getUserID() &&
        		playerData.getPlayerID() != game.getPlayer2Data().getUserID()) {
        		log4j.error("Illegal access to private game " + player + " " + game.getGameID());
        		handleError(request, response, "Game id invalid, please try again.", redirectPage);
        		return;
        	}
        	
        	TBGame tbGame = resources.getTbGameStorer().loadGame(gid);

        	
        	
			request.setAttribute("game", game);
			if (tbGame != null) {
				request.setAttribute("tbGame", tbGame);
			}

			if (request.getParameter("e") != null) {
				redirectPage = "/gameServer/viewLiveGameEmbed.jsp";
			}
			getServletContext().getRequestDispatcher(redirectPage).forward(
	            request, response);

        } catch (Throwable t) {
        	log4j.error("ViewGamesServlet, unknown error", t);
        	handleError(request, response, "Unknown error.", redirectPage);
        }
    }
    
	private void handleError(HttpServletRequest request,
            HttpServletResponse response, String errorMessage, String redirectPage) throws ServletException,
            IOException {
		request.setAttribute("error", errorMessage);
       	getServletContext().getRequestDispatcher(redirectPage).forward(
            request, response);
	}
}