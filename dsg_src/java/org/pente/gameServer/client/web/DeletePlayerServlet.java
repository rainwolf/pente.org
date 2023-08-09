package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.jive.*;

import com.jivesoftware.base.*;
import com.jivesoftware.forum.*;

public class DeletePlayerServlet extends HttpServlet {

    private static final DSGAuthToken adminToken =
            new DSGAuthToken(22000000000002L);

    private static final Category log4j =
            Category.getInstance(DeletePlayerServlet.class.getName());

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

        String redirectPage = "/gameServer/deletePlayer.jsp";
        String deletePlayerError = null;
        String deletePlayerSuccess = null;
        DSGPlayerData dsgPlayerData = null;

        try {
            String name = (String) request.getAttribute("name");
            //String name = (String) request.getParameter("deleteName");
            //String hashCode = (String) request.getParameter("hashCode");
            String deleteConfirm = request.getParameter("deleteConfirm");

            //if (name == null || hashCode == null) {
            //	deletePlayerError = "Invalid parameters to delete, please try again.";
            //}
            //else if (deleteConfirm == null) {
            // forward to delete confirm page
            //}
            if (deleteConfirm == null) {
                // forward to delete confirm page
            } else {
                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
                if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
                    deletePlayerError = "Player not found, please try again.";
                }
                //else if (dsgPlayerData.getHashCode().equals(hashCode)) {
                else {

                    try {
                        ForumFactory forumFactory = ForumFactory.getInstance(
                                adminToken);
                        UserManager userManager = forumFactory.getUserManager();
                        User user = userManager.getUser(name);
                        WatchManager watchManager = forumFactory.getWatchManager();
                        watchManager.deleteWatches(user);
                    } catch (Throwable t) {
                        log4j.info("Problem deleting watches for " + name, t);
                    }

                    dsgPlayerData.deRegister(DSGPlayerData.DEACTIVE);
                    dsgPlayerStorer.updatePlayer(dsgPlayerData);

                    request.setAttribute("name", null);
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }

                    LoginCookieHandler loginCookieHandler = new LoginCookieHandler();
                    loginCookieHandler.loadCookie(request);
                    loginCookieHandler.deleteCookie(request, response);
                    deletePlayerSuccess = name + " deleted.";
                }
            }

            if (deleteConfirm != null) {
                log4j.info("delete player " + name + ", " +
                        (deletePlayerError == null ? "success" : "failure"));
            }

        } catch (DSGPlayerStoreException e) {
            deletePlayerError = "Database error.";
            log4j.error("Delete player error.", e);
        }

        request.setAttribute("deletePlayerError", deletePlayerError);
        request.setAttribute("deletePlayerSuccess", deletePlayerSuccess);
        getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}