package org.pente.gameServer.client.web;

/**
* Created by waliedothman on 01/02/2017.
*/

import org.apache.log4j.Category;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;
import org.pente.message.DSGMessage;
import org.pente.message.DSGMessageStoreException;
import org.pente.message.DSGMessageStorer;
import org.pente.notifications.NotificationServer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class BroadcastServlet  extends HttpServlet {
    private static final String errorRedirectPage = "/gameServer/error.jsp";


    private static final Category log4j =
            Category.getInstance(FollowerServlet.class.getName());

    private DSGFollowerStorer followerStorer;
    private DSGPlayerStorer dsgPlayerStorer;
    private NotificationServer notificationServer;
    private DSGMessageStorer dsgMessageStorer;

    private ServletContext ctx;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {
            ctx = getServletContext();
            Resources resources = (Resources)
                    ctx.getAttribute(Resources.class.getName());
            followerStorer = resources.getFollowerStorer();
            dsgPlayerStorer = resources.getDsgPlayerStorer();
            notificationServer = resources.getNotificationServer();
            dsgMessageStorer = resources.getDsgMessageStorer();
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

        String error = null;
        String player = (String) request.getAttribute("name");
        String game = (String) request.getParameter("game");
        String sendTo = (String) request.getParameter("sendTo");
        DSGPlayerData playerData = null, followPlayerData = null, unFollowPlayerData = null;
        if (player == null) {
            handleError(request, response, "not logged in");
            return;
        }
        try {
            playerData = dsgPlayerStorer.loadPlayer(player);
        } catch (DSGPlayerStoreException e) {
            e.printStackTrace();
            log4j.error("FollowerServlet error loading player: " + player);
            handleError(request, response, "database error, try again later");
            return;
        }

        if (!notificationServer.canBroadcast(playerData.getPlayerID())) {
            handleError(request, response, "You can't broadcast more than once per hour.");
            return;
        }

        if (!playerData.hasPlayerDonated()) {
            handleError(request, response, "Broadcasting to followers or friends is only available to subscribers");
            return;
        }

        List<Long> recipients;
        if (sendTo == null) {
            handleError(request, response, "no recipients specified");
            return;
        }
        try {
            if (sendTo.equals("followers")) {
                recipients = followerStorer.getFollowers(playerData.getPlayerID());
            } else {
                recipients = followerStorer.getFriends(playerData.getPlayerID());
            }
        } catch (DSGFollowerStoreException e) {
            log4j.error("BroadcastServlet: error getting recipients " + e);
            handleError(request, response, "error getting recipients");
            return;
        }

        for (long pid : recipients) {
            DSGMessage message = new DSGMessage();
            message.setCreationDate(new java.util.Date());
            message.setFromPid(playerData.getPlayerID());
            message.setToPid(pid);
            message.setSubject("live game room alert");
            message.setBody("I'm looking for players to play live " + game + " games.\n\n"+
                    "Reply to this message if interested.\n\n"+
                    "You are receiving this message because you are following "+player+
                    "\n\nThis is an automated server message.");
            try {
                dsgMessageStorer.createMessage(message);
            } catch (DSGMessageStoreException e) {
                log4j.error("BroadcastServlet: error sending messages " + e);
                handleError(request, response, "error sending messages");
                return;
            }
            notificationServer.sendBroadcastNotification(player, game, pid);
        }
        notificationServer.storeBroadcastDate(playerData.getPlayerID());

        response.sendRedirect("/gameServer/index.jsp");
    }


    private void handleError(HttpServletRequest request,
                             HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
        request.setAttribute("error", errorMessage);
        getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                request, response);
    }

}
