package org.pente.notifications.web;

import org.apache.log4j.Category;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.server.Resources;
import org.pente.notifications.NotificationServer;
import org.pente.notifications.NotificationServerException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by waliedothman on 28/01/2017.
 */
public class NotificationServlet extends HttpServlet {
    private static final String errorRedirectPage = "/gameServer/error.jsp";

    private static final Category log4j =
            Category.getInstance(NotificationServlet.class.getName());

    private NotificationServer notificationServer;
    private DSGPlayerStorer dsgPlayerStorer;
    private ServletContext ctx;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {
            ctx = getServletContext();
            Resources resources = (Resources)
                    ctx.getAttribute(Resources.class.getName());
            notificationServer = resources.getNotificationServer();
            dsgPlayerStorer = resources.getDsgPlayerStorer();

        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response)
            throws ServletException, IOException {

        String player = (String) request.getAttribute("name");
        String device = (String) request.getParameter("device");
        String token = (String) request.getParameter("token"); 

        DSGPlayerData playerData = null;
        if (player == null) {
            handleError(request, response, "not logged in");
            return;
        }
        try {
            playerData = dsgPlayerStorer.loadPlayer(player);
        } catch (DSGPlayerStoreException e) {
            e.printStackTrace();
            log4j.error("NotificationServlet error loading player: "+player);
            handleError(request, response, "database error, try again later");
            return;
        }

        try {
            if (device.equals("iOS")) {
                notificationServer.registerDevice(playerData.getPlayerID(), token, NotificationServer.iOS);
            } else if (device.equals("android")) {
                notificationServer.registerDevice(playerData.getPlayerID(), token, NotificationServer.ANDROID);
            }
        } catch (NotificationServerException e) {
            e.printStackTrace();
            log4j.error("NotificationServlet error registering for "+player);
            handleError(request, response, "database error, try again later");
            return;
        }

        PrintWriter out = new PrintWriter(response.getOutputStream());
        response.setContentType("text/plain");
        out.write("It seems to have worked");
        out.write("\n");
        out.flush();
    }

    private void handleError(HttpServletRequest request,
                             HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
        request.setAttribute("error", errorMessage);
        getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                request, response);
    }

}
