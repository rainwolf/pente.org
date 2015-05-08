package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.core.*;

public class StatsServlet extends HttpServlet {

    private static Category 		cat = Category.getInstance(StatsServlet.class.getName());

	private static final String		PLAYER_STATS_ERROR_PAGE = "/gameServer/statsError.jsp";
	private static final String		PLAYER_STATS_DISPLAY_PAGE = "/gameServer/stats.jsp";

	private static DBHandler 		dbHandler;
	private static DSGPlayerStorer	dsgPlayerStorer;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

        } catch (Throwable t) {
            cat.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
            doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

		String command = request.getParameter("command");
		if (command == null) {
		}
		else if (command.equals("playerStats")) {
			String nextPage = PLAYER_STATS_DISPLAY_PAGE;
			try {
				
				StatsData statsData = new StatsData();
				statsData.initialize(request);
				if (!statsData.isValidSearch()) {
					nextPage = PLAYER_STATS_ERROR_PAGE;
				}
				else {
					Vector searchResults = dsgPlayerStorer.search(
                        statsData.getGame(),
                        statsData.getSortField(), 
                        statsData.getStartNum(), 
                        statsData.getLength(), 
                        statsData.getIncludeProvisional(), 
                        statsData.getIncludeInactive(),
                        statsData.getPlayerType());

					statsData.setResults(searchResults);
					int numResults = dsgPlayerStorer.getNumPlayers(
                        statsData.getGame(), 
                        statsData.getIncludeProvisional(), 
                        statsData.getIncludeInactive(),
                        statsData.getPlayerType());
					
                    statsData.setNumResults(numResults);			
					request.setAttribute("statsData", statsData);
				}

			} catch (Throwable t) {
				cat.error("Error showing player stats", t);
				nextPage = PLAYER_STATS_ERROR_PAGE;
			}
			
			getServletContext().getRequestDispatcher(nextPage).forward(request, response);
		}
    }
}

