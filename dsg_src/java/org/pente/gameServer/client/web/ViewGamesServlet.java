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

public class ViewGamesServlet extends HttpServlet {

	private static final Category log4j = Category.getInstance(
		ViewGamesServlet.class.getName());

    private Resources resources;
    private static final String redirectPage = "/gameServer/viewLiveGames.jsp";

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
    // p = name, required
    // g = game, required
    // s = start seq nbr, not required
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {
        
        try {
        	String me = (String) request.getAttribute("name");
        	String name = (String) request.getParameter("p");
            
        	if (name == null) {
        		log4j.error("ViewGamesServlet, Player not found: " + name);
        		handleError(request, response,
        			"Player not found, please try again.");
        		return;
        	}
        	DSGPlayerData playerData = null;
        	DSGPlayerData meData = null;
        	try {
        		playerData = resources.getDsgPlayerStorer().loadPlayer(name);
        		meData = resources.getDsgPlayerStorer().loadPlayer(me);
        	} catch (DSGPlayerStoreException dpse) {
        		log4j.error("ViewGamesServlet, player data not found: " + name, dpse);
        		handleError(request, response, "Error loading player data.");
        		return;
        	}
        	request.setAttribute("dsgPlayerData", playerData);
        	
            String gameStr = (String) request.getParameter("g");
        	int game = -1;
            if (gameStr != null) {
            	try {
            		game = Integer.parseInt(gameStr); 
            	} catch (NumberFormatException nfe) {}
            }
        	if (game == -1) {
        		log4j.error("ViewGamesServlet, Invalid game: " + gameStr);
        		handleError(request, response,
        			"Invalid game " + gameStr);
        		return;
        	}
        	request.setAttribute("game", new Integer(game));
        	
        	String startSeqStr = request.getParameter("s");
        	int startSeq = 0;
        	if (startSeqStr != null) {
            	try {
            		startSeq = Integer.parseInt(startSeqStr); 
            	} catch (NumberFormatException nfe) {}
        	}
        	request.setAttribute("start", new Integer(startSeq));

        	log4j.info("view player games for " + name + ", " + game + "," + startSeq);
        	
        	List<GameData> games = resources.getDsgGameLookup().search(
        		name, playerData.getPlayerID(), playerData.getNameColorRGB(),
        		meData.getPlayerID(),
        		game, startSeq, 100);
    		int count = resources.getDsgGameLookup().count(
    			playerData.getPlayerID(), game);
        	
			List<GameData> wins = new ArrayList<GameData>();
			List<GameData> losses = new ArrayList<GameData>();
			
			for (GameData d :games) {
				int player = d.getPlayer1Data().getUserIDName().equals(
					playerData.getName()) ? 1 : 2;
			    if (d.getWinner() == player) {
					wins.add(d);
				}
				else {
					losses.add(d);
				}
			}
			
			request.setAttribute("wins", wins);
			request.setAttribute("losses", losses);
			request.setAttribute("count", new Integer(count));
	       	getServletContext().getRequestDispatcher(redirectPage).forward(
	            request, response);

        } catch (Throwable t) {
        	log4j.error("ViewGamesServlet, unknown error", t);
        	handleError(request, response, "Unknown error.");
        }
    }
    
	private void handleError(HttpServletRequest request,
            HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
		request.setAttribute("error", errorMessage);
       	getServletContext().getRequestDispatcher(redirectPage).forward(
            request, response);
	}
}