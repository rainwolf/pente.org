package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;

public class DonationServlet extends HttpServlet {

	private static final Category cat = Category.getInstance(DonationServlet.class.getName());

	public static final String DONATION_LIST = "donationList";

    private DSGPlayerStorer dsgPlayerStorer;

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
    
        String redirectPage = "/gameServer/donations.jsp";
        String lineSeparator = "\n";

		String command = request.getParameter("command");
		if (command != null && command.equals("thanks")) {
			redirectPage = "/gameServer/donations-thanks.jsp";
			
			// possibly add donation data to db here
		}
		else {
			
			List<DSGDonationData> donations = null;
			try {
				donations = dsgPlayerStorer.getAllPlayersWhoDonated();
			} catch (DSGPlayerStoreException e) {
				cat.error("Problem loading donation list.", e);
				donations = new Vector();
			}
			
			request.setAttribute(DONATION_LIST, donations);
		}

		getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}

