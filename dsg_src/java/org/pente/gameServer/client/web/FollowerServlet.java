package org.pente.gameServer.client.web;

import org.apache.log4j.Category;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Created by waliedothman on 25/01/2017.
 */
public class FollowerServlet extends HttpServlet {
    private static final String errorRedirectPage = "/gameServer/error.jsp";


    private static final Category log4j =
            Category.getInstance(FollowerServlet.class.getName());
    
    private DSGFollowerStorer followerStorer;
    private DSGPlayerStorer dsgPlayerStorer;
    private ServletContext ctx;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {
            ctx = getServletContext();
            Resources resources = (Resources)
                    ctx.getAttribute(Resources.class.getName());
            followerStorer = resources.getFollowerStorer();
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

        String error = null;
        String player = (String) request.getAttribute("name");
        String follow = (String) request.getParameter("follow");
        String unFollow = (String) request.getParameter("unfollow");
        DSGPlayerData playerData = null, followPlayerData = null, unFollowPlayerData = null;
        if (player == null) {
            handleError(request, response, "not logged in");
            return;
        }
        try {
            playerData = dsgPlayerStorer.loadPlayer(player);
        } catch (DSGPlayerStoreException e) {
            e.printStackTrace();
            log4j.error("FollowerServlet error loading player: "+player);
            handleError(request, response, "database error, try again later");
            return;
        }

        if (follow != null) {
            if (!playerData.hasPlayerDonated()) {
                int followingLimit = Integer.parseInt(ctx.getInitParameter("NONSUBSCRIBERFOLLOWINGLIMIT"));
                try {
                    List<Long> following = followerStorer.getFollowing(playerData.getPlayerID());
                    if (following.size() > followingLimit) {
                        handleError(request, response, "You've reached the limit("+followingLimit+") for non-subscribers. Subscribers can follow an unlimited number of players.");
                        return;
                    }
                } catch (DSGFollowerStoreException e) {
                    e.printStackTrace();
                    handleError(request, response, "database error, try again later");
                    return;
                }
            }
            try {
                followPlayerData = dsgPlayerStorer.loadPlayer(follow);
            } catch (DSGPlayerStoreException e) {
                e.printStackTrace();
                log4j.error("FollowerServlet error loading follow player: "+follow);
                handleError(request, response, "database error, try again later");
                return;
            }
            try {
                followerStorer.addFollower(followPlayerData.getPlayerID(), playerData.getPlayerID());
            } catch (DSGFollowerStoreException e) {
                e.printStackTrace();
                handleError(request, response, "database error, try again later");
                return;
            }
        }
        
        if (unFollow != null) {
            try {
                unFollowPlayerData = dsgPlayerStorer.loadPlayer(unFollow);
            } catch (DSGPlayerStoreException e) {
                e.printStackTrace();
                log4j.error("FollowerServlet error loading unfollow player: "+unFollow);
                handleError(request, response, "database error, try again later");
                return;
            }
            try {
                followerStorer.removeFollower(unFollowPlayerData.getPlayerID(), playerData.getPlayerID());
            } catch (DSGFollowerStoreException e) {
                e.printStackTrace();
                handleError(request, response, "database error, try again later");
                return;
            }
        }

        if (follow != null && followPlayerData != null) {
            getServletContext().getRequestDispatcher("/gameServer/profile?viewName="+followPlayerData.getName()).forward(request, response);
        } else {
            if (request.getParameter("social") != null) {
                try {
                    request.setAttribute("following", followerStorer.getFollowing(playerData.getPlayerID()));
                    request.setAttribute("followers", followerStorer.getFollowers(playerData.getPlayerID()));
                } catch (DSGFollowerStoreException e) {
                    e.printStackTrace();
                    handleError(request, response, "database error, try again later");
                    return;
                }
                getServletContext().getRequestDispatcher("/gameServer/followersing.jsp").forward(request, response);
            } else if (unFollow != null) {
                getServletContext().getRequestDispatcher("/gameServer/profile?viewName="+unFollowPlayerData.getName()).forward(request, response);
            } else {
                getServletContext().getRequestDispatcher("/gameServer/index.jsp").forward(request, response);
            }
        }
    }

    private void handleError(HttpServletRequest request,
                             HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
        request.setAttribute("error", errorMessage);
        getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                request, response);
    }

}
