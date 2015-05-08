package org.pente.gameServer.client.web;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.server.Resources;
import org.pente.tools.RatingsGrapher;

public class RatingsGraphServlet extends HttpServlet {

    private static Category log4j =
        Category.getInstance(RatingsGraphServlet.class.getName());

    private Resources resources;
    private DBHandler dbHandler;
    
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            resources = (Resources) ctx.getAttribute(Resources.class.getName());
            dbHandler = resources.getDbHandlerRo();
            
        } catch (Exception e) {
        	log4j.error("Error init()", e);
        }
    }


    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
    	
    	String pidStr = request.getParameter("pid");
    	String gameStr = request.getParameter("game");
    	long pid = -1;
    	int game = -1;
    	if (pidStr != null) {
    		try {
    			pid = Long.parseLong(pidStr);
    		} catch (NumberFormatException nfe) {}
    	}
    	if (gameStr != null) {
    		try {
    			game = Integer.parseInt(gameStr);
    		} catch (NumberFormatException nfe) {}
    	}
    	if (pid == -1 || game == -1) {
    		response.sendError(404);
    	}
    	else {
    		RatingsGrapher rg = new RatingsGrapher(dbHandler, pid, game);
            response.setContentType("image/png");
            response.setHeader("Cache-Control", "max-age=3600");
            rg.generateGraph(response.getOutputStream());
            response.getOutputStream().flush();
    	}
    }
}
