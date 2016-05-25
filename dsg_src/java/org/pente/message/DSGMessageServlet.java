package org.pente.message;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

import org.pente.turnBased.SendNotification;

public class DSGMessageServlet extends HttpServlet {
	
	private static final Category log4j = Category.getInstance(
		DSGMessageServlet.class.getName());

	private static final String messagesPage = "/gameServer/myMessages.jsp";
	private static final String messagePage = "/gameServer/viewMessage.jsp";
	private static final String newMessagePage = "/gameServer/newMessage.jsp";
	private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";
	
	private Resources resources;
	
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

	// expected params:
	// player - required (user logged in so will be there)
	// command - load, new, delete, view, reply - required
	// load
	//   start index - optional
	// create
	//   subject, body
	// delete
	//   mid's to delete
	// view
	//   mid to view
    // reply
    //   mid to reply to
    public void doGet(HttpServletRequest request,
            		  HttpServletResponse response)
		throws ServletException, IOException {
			request.setCharacterEncoding("UTF-8");
			doPost(request, response);
	}

	public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

		request.setCharacterEncoding("UTF-8");

		String error = null;

		String errorRedirectPage = "/gameServer/myMessages.jsp";
		
		DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
		DSGMessageStorer dsgMessageStorer = resources.getDsgMessageStorer();

        String player = (String) request.getAttribute("name");
		DSGPlayerData playerData = null;
		
        String command = (String) request.getParameter("command");
		
		if (command == null) {
			command = "load";
		}
		log4j.debug("DSGMessageServlet, command: " + command);

		// player must be logged in, so name will be populated
	    try {
			playerData = dsgPlayerStorer.loadPlayer(player);
	    } catch (DSGPlayerStoreException e) {
	    	log4j.error("DSGMessageServlet, problem loading player " + player, e);
	    	handleError(request, response, 
				"Database error, please try again later.", errorRedirectPage);
			return;
	    }
		
		if (command.equals("load")) {

			// return to message screen
			try {
				List<DSGMessage> messages = dsgMessageStorer.getMessages(
					playerData.getPlayerID());
				Collections.sort(messages, new Comparator<DSGMessage>() {
					public int compare(DSGMessage m1, DSGMessage m2) {
						return m2.getCreationDate().compareTo(m1.getCreationDate());
					}
				});
				request.setAttribute("messages", messages);
		       	getServletContext().getRequestDispatcher(messagesPage).forward(
			            request, response);
				
			} catch (DSGMessageStoreException dmse) {
				log4j.error("DSGMessageServlet, problem loading messages " + player, dmse);

		    	handleError(request, response, 
					"Database error, please try again later.", errorRedirectPage);
				return;
			}
		}
		else if (command.equals("delete")) {
			
			String midStrs[] = request.getParameterValues("mid");
			if (midStrs != null) {
				int mids[] = new int[midStrs.length];
				for (int i = 0; i < mids.length; i++) {
					mids[i] = -1;
					if (midStrs[i] != null) {
						try {
							mids[i] = Integer.parseInt(midStrs[i]);
						} catch (NumberFormatException nfe) {}
					}
					if (mids[i] == -1) {
						handleError(request, response, "Invalid message id.", 
							errorRedirectPage);
						return;
					}
				}
				for (int i = 0; i < mids.length; i++) {
					try {
						if (mids[i] != 0) {
							dsgMessageStorer.deleteMessage(mids[i]);
						}
					} catch (DSGMessageStoreException dmse) {
						log4j.error("MessageServlet delete error.", dmse);
					}
				}
			}
			
			String isMobile = (String) request.getParameter("mobile");
			if (isMobile == null) {
				response.sendRedirect("/gameServer/mymessages?command=load");
			} else {
		        response.sendRedirect(mobileRedirectPage);
			}
		}
		else if (command.equals("view")) {

			errorRedirectPage = "/gameServer/viewMessage.jsp";
			try {
				String midStr = request.getParameter("mid");
				int mid = -1;
				if (midStr != null) {
					try {
						mid = Integer.parseInt(midStr);
					} catch (NumberFormatException nfe) {}
				}
				if (mid == -1) {
					handleError(request, response, "Invalid message id.", 
						errorRedirectPage);
					return;
				}

				DSGMessage m = dsgMessageStorer.getMessage(mid);
				if (m == null) {
					handleError(request, response, "Message not found: " + mid, 
						errorRedirectPage);
					return;
				}

				// if someone is trying to view someone elses message
				if (m.getToPid() != playerData.getPlayerID()) {
					handleError(request, response, "Message not found: " + mid,
						errorRedirectPage);
					return;
				}
				request.setAttribute("message", m);
				
				if (!m.isRead()) {
					dsgMessageStorer.readMessage(m);
				}
				
		       	getServletContext().getRequestDispatcher(messagePage).forward(
			            request, response);

			} catch (DSGMessageStoreException dmse) {
				log4j.error("MessageServlet view error.", dmse);
				handleError(request, response, "Database error, please try again later.", errorRedirectPage);
			}
			
		}
		else if (command.equals("reply")) {
			try {
				String midStr = request.getParameter("mid");
				int mid = -1;
				if (midStr != null) {
					try {
						mid = Integer.parseInt(midStr);
					} catch (NumberFormatException nfe) {}
				}
				if (mid == -1) {
					handleError(request, response, "Invalid message id.", 
						errorRedirectPage);
					return;
				}
				
				DSGMessage m = dsgMessageStorer.getMessage(mid);
				if (m == null) {
					handleError(request, response, "Message not found: " + mid, 
						errorRedirectPage);
					return;
				}
				// if someone is trying to reply to someone elses message
				if (m.getToPid() != playerData.getPlayerID()) {
					handleError(request, response, "Message not found: " + mid,
						errorRedirectPage);
					return;
				}
				
				DSGPlayerData toPlayerData = dsgPlayerStorer.loadPlayer(m.getFromPid());
				if (!toPlayerData.isActive()) {
					handleError(request, response, "Player not found.", errorRedirectPage);
					return;
				}
				
				request.setAttribute("message", m);
				request.setAttribute("to", toPlayerData.getName());
				
		       	getServletContext().getRequestDispatcher(newMessagePage).forward(
			        request, response);
		       	
			} catch (DSGPlayerStoreException dpse) {
				log4j.error("MessageServlet, lookup player error.", dpse);
				handleError(request, response, "Player not found.", errorRedirectPage);
			} catch (DSGMessageStoreException dmse) {
				log4j.error("MessageServlet view error.", dmse);
				handleError(request, response, "Database error, please try again later.", errorRedirectPage);
			}
		}
		else if (command.equals("create")) {
			
			errorRedirectPage = "/gameServer/newMessage.jsp";
			String to = null;
			try {
				DSGMessage m = new DSGMessage();
				String body = request.getParameter("body");
				to = request.getParameter("to");
				String subject = request.getParameter("subject");
				
				if (body == null || body.equals("")) {
					handleError(request, response, "You must enter a message.", errorRedirectPage);
					return;
				}
				else if (to == null || to.equals("")) {
					handleError(request, response, "You must enter who you are " +
						"sending this message to.", errorRedirectPage);
					return;
				}
				else if (subject == null || subject.equals("")) {
					handleError(request, response, "You must enter a subject.", errorRedirectPage);
					return;
				}
				
				DSGPlayerData toPlayerData = dsgPlayerStorer.loadPlayer(to);
				if (toPlayerData == null || !toPlayerData.isActive()) {
					handleError(request, response, "Player " + to + " not found.", errorRedirectPage);
					return;
				}
				else {
					DSGIgnoreData i = resources.getDsgPlayerStorer().getIgnoreData(
						toPlayerData.getPlayerID(), playerData.getPlayerID());
					if (i != null && i.getIgnoreChat()) {
						log4j.debug("Ignore chat");
						handleError(request, response, "Player is ignoring your messages.", errorRedirectPage);
						return;
					}
				}
				
				m.setBody(body);
				m.setSubject(subject);
				m.setFromPid(playerData.getPlayerID());
				m.setToPid(toPlayerData.getPlayerID());
				m.setCreationDate(new Date());

				dsgMessageStorer.createMessage(m);

					ServletContext ctx = getServletContext();
					String penteLiveGCMkey = ctx.getInitParameter("penteLiveGCMkey");
					String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
					String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
					boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
					Thread thread = new Thread(new SendNotification(3, m.getMid(), playerData.getPlayerID(), toPlayerData.getPlayerID(), 
						subject, penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, resources.getDbHandler(), penteLiveGCMkey));
					thread.start();
				
				String isMobile = (String) request.getParameter("mobile");
				if (isMobile == null) {
					response.sendRedirect("/gameServer/mymessages?command=load");
				} else {
			        response.sendRedirect(mobileRedirectPage);
				}
				
			} catch (DSGPlayerStoreException dpse) {
				log4j.error("MessageServlet, lookup to player error.", dpse);
				handleError(request, response, "Player " + to + " not found.", errorRedirectPage);
				return;
			} catch (DSGMessageStoreException dmse) {
				log4j.error("MessageServlet create error.", dmse);
				handleError(request, response, "Database error, please try again later.", errorRedirectPage);
			}
		}

    }
	
	private void handleError(HttpServletRequest request,
            HttpServletResponse response, String errorMessage, String page)
			throws ServletException, IOException {
		request.setAttribute("error", errorMessage);
       	getServletContext().getRequestDispatcher(page).forward(
            request, response);
	}
}
