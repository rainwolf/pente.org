package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;

public class ViewProfileServlet extends HttpServlet {

	private static final Category log4j = Category.getInstance(
		ViewProfileServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
            
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
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

		String redirectPage = "/gameServer/profile.jsp";
		String viewProfileError = null;
        DSGPlayerData dsgPlayerData = null;
        
        try {
        	String name = (String) request.getParameter("viewName");
        	log4j.info("view player profile for " + name);
            
        	if (name == null) {
        		viewProfileError = "Player not found, please try again.";
        	}
        	else {
	            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
	            if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
	            	viewProfileError = "Player not found, please try again.";
                    dsgPlayerData = null;
	            }
        	}

        } catch (DSGPlayerStoreException e) {
        	viewProfileError = "Database error.";
        	log4j.error("View profile error, dp.", e);
        }
        	
        request.setAttribute("dsgPlayerData", dsgPlayerData);
		request.setAttribute("viewProfileError", viewProfileError);
		getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}

