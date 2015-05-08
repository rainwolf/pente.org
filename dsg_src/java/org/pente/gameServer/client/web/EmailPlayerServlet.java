package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
/** don't think this is used anymore */
public class EmailPlayerServlet extends HttpServlet {

	private static final Category cat = Category.getInstance(EmailPlayerServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer;
    private MySQLDSGReturnEmailStorer returnEmailStorer;

    private boolean emailEnabled;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            returnEmailStorer = (MySQLDSGReturnEmailStorer) ctx.getAttribute(MySQLDSGReturnEmailStorer.class.getName());
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());

            emailEnabled = ((Boolean) ctx.getAttribute("emailEnabled")).booleanValue();

        } catch (Throwable t) {
            cat.error("Problem in init()", t);
        }
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

        String fromName = (String) request.getAttribute("name");
        String toName = request.getParameter("emailToName");
		String subject = request.getParameter("emailSubject");
		String message = request.getParameter("emailText");

        DSGPlayerData fromDSGPlayerData = null;
        DSGPlayerData toDSGPlayerData = null;
        
        String emailPlayerError = null;
        String redirectPage = "/gameServer/emailPlayerConfirmation.jsp";
        String lineSeparator = "\n";

		cat.info("Email player: from=" + fromName + ", to=" + toName + ", subject=" + subject);

	    try {
			fromDSGPlayerData = dsgPlayerStorer.loadPlayer(fromName);
	    } catch (DSGPlayerStoreException e) {
	    	cat.error("Problem loading player " + fromName + " to send email from.", e);
	    	emailPlayerError = "Database error, please try again later.";
	    	redirectPage = "/gameServer/emailPlayer.jsp";
	    }
	    
	    try {
	    	if (toName != null) {
        	    toName = toName.trim().toLowerCase();
    			toDSGPlayerData = dsgPlayerStorer.loadPlayer(toName);
	    	}
	    } catch (DSGPlayerStoreException e) {
	    	cat.error("Problem loading player " + toName + " to send email to.", e);
	    	emailPlayerError = "Database error, please try again later.";
	    	redirectPage = "/gameServer/emailPlayer.jsp";
	    }
	    
        if (toDSGPlayerData == null || !toDSGPlayerData.isActive()) {
        	emailPlayerError = "Player " + toName + " not found, please check " +
                "the name and try again.";
        	redirectPage = "/gameServer/emailPlayer.jsp";
        }
        else if (!fromDSGPlayerData.getEmailValid()) {
            emailPlayerError = "Pente.org has your email address marked as invalid. " +
                "This means the last time an email was sent to you " +
                "it was returned with errors.  You must correct the address in " +
                "<b><a href=" + request.getContextPath() + "/gameServer/myprofile" +
                ">My Profile</a></b> before you can email another player.";
            redirectPage = "/gameServer/emailPlayer.jsp";
        }
        else if (!toDSGPlayerData.getEmailValid()) {
            emailPlayerError = "Pente.org has the email address of the player you " +
                "wish to email marked as invalid, your email has not been sent.";
            redirectPage = "/gameServer/emailPlayer.jsp";
        }
        else if (!emailEnabled) {
            cat.info("Email not enabled, no email sent.");
        }
        else {

            if (subject == null) {
            	subject = "Message from Pente.org";
            }
            else {
                subject = "Message from Pente.org - " + subject;
            }

            message += lineSeparator + lineSeparator +
                	"This message has been sent from Pente.org at" + lineSeparator +
                    "http://www.pente.org/" + lineSeparator + lineSeparator;

            try {
                SendMail2.sendMailSaveInDb(
                    fromDSGPlayerData.getName(),
                    fromDSGPlayerData.getEmail(),
                    toDSGPlayerData.getPlayerID(),
                    toDSGPlayerData.getName(),
                    toDSGPlayerData.getEmail(),
                    subject,
                    message,
                    false,
                    null,
                    returnEmailStorer);
            } catch (Throwable t) {			    
            	cat.error("Error sending player email to " + toDSGPlayerData.getName(), t);
			    emailPlayerError = "Error sending email to " + toDSGPlayerData.getName() + ".  Please try again later.";
			    redirectPage = "/gameServer/emailPlayer.jsp";
            }
        }

        if (emailPlayerError != null) {
    		request.setAttribute("emailPlayerError", emailPlayerError);
	       	getServletContext().getRequestDispatcher(redirectPage).forward(
                request, response);
        }
        // send redirect to confirmation to avoid duplicate emails
        else {
            response.sendRedirect(request.getContextPath() + redirectPage + "?name=" + toName);
        }
    }
}