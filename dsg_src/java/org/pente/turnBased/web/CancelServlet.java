package org.pente.turnBased.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.game.*;
import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class CancelServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(
            CancelServlet.class.getName());

    private static final String errorRedirectPage = "/gameServer/tb/error.jsp";
    private static final String confirmRedirectPage = "/gameServer/tb/requestCancel.jsp";
    private static final String requestRedirectPage = "/gameServer/tb/";
    private static final String cancelRedirectPage = "/gameServer/index.jsp";
    private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";

    private Resources resources;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

    // expected params:
    // player - required (user logged in so will be there)
    // command - confirm, request, Yes, No
    // gid - required
    // message - optional
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String error = null;

        DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
        TBGameStorer tbGameStorer = resources.getTbGameStorer();

        String player = (String) request.getAttribute("name");
        DSGPlayerData playerData = null;

        String sidStr = (String) request.getParameter("sid");
        long sid = 0;
        String gidStr = (String) request.getParameter("gid");
        TBSet set = null;

        // player must be logged in, so name will be populated
        try {
            playerData = dsgPlayerStorer.loadPlayer(player);
        } catch (DSGPlayerStoreException e) {
            log4j.error("CancelServlet, problem loading player " + player, e);
            handleError(request, response,
                    "Database error, please try again later.");
            return;
        }

        if (sidStr != null) {
            try {
                sid = Long.parseLong(sidStr);
            } catch (NumberFormatException nef) {
            }
        }
        if (sid == 0) {
            log4j.error("CancelServlet, invalid sid " + sidStr);
            handleError(request, response, "No set or invalid set.");
            return;
        }

        String command = request.getParameter("command");
        if (command == null || command.equals("")) {
            log4j.error("CancelServlet, invalid command");
            handleError(request, response, "Invalid command.");
            return;
        }
        String msg = request.getParameter("message");
        log4j.debug("cancel game : " + command + "," + sid);

        try {

            set = tbGameStorer.loadSet(sid);
            if (set == null) {
                log4j.error("CancelServlet, invalid set, storer returned null " + sid);
                handleError(request, response, "Set not found.");
                return;
            } else if (set.getState() != TBSet.STATE_ACTIVE) {
                log4j.error("CancelServlet, not active " + sid);
                handleError(request, response, "Set not active.");
                return;
            } else if (set.getPlayer1Pid() == 23000000020606L || set.getPlayer2Pid() == 23000000020606L) {
                log4j.error("CancelServlet, trying to cancel against AI player");
                handleError(request, response, "Sets against the AI player cannot be cancelled.");
                return;
            }

            request.setAttribute("set", set);

            TBGame tbg = set.getGame1();
            if (tbg.getEventId() != tbGameStorer.getEventId(tbg.getGame())) {
                log4j.error("CancelServlet: trying to cancel a tournament set " + sid);
                handleError(request, response, "Tournament sets cannot be cancelled.");
                return;
            }
            // from applet
            if (command.equals("confirm")) {
                if (msg == null || msg.trim().equals("")) msg = "";
                request.setAttribute("message", msg);

                try {
                    DSGPlayerData opponent = dsgPlayerStorer.loadPlayer(
                            set.getGame1().getOpponent(playerData.getPlayerID()));
                    request.setAttribute("opponent", opponent);

                } catch (DSGPlayerStoreException dpse) {
                    log4j.error("CancelServlet, error loading player data", dpse);
                    handleError(request, response, "Database error");
                    return;
                }

                getServletContext().getRequestDispatcher(confirmRedirectPage).
                        forward(request, response);
                return;
            }
            // from jsp 
            else if (command.equals("request")) {
                if (msg == null || msg.trim().equals("")) msg = "";

                try {
                    if (playerData.getPlayerID() != set.getGame1().getPlayer1Pid() &&
                            playerData.getPlayerID() != set.getGame1().getPlayer2Pid()) {
                        handleError(request, response, "Invalid set.");
                        return;
                    } else if (set.getCancelPid() != 0) {
                        handleError(request, response, "Cancel request already exists.");
                        return;
                    }
                    tbGameStorer.requestCancel(set, playerData.getPlayerID(), msg);

                } catch (TBStoreException tbe) {
                    log4j.error("CancelServlet, error requesting cancel", tbe);
                    handleError(request, response, "Database error");
                    return;
                }

                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(requestRedirectPage).
                            forward(request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileRedirectPage).
                            forward(request, response);
                }
                return;
            }
            // from jsp
            else if (command.equals("Yes")) {
                // can't cancel someone elses set
                if (playerData.getPlayerID() != set.getGame1().getPlayer1Pid() &&
                        playerData.getPlayerID() != set.getGame1().getPlayer2Pid()) {
                    handleError(request, response, "Invalid set.");
                    return;
                }

                // can't cancel it yourself
                if (playerData.getPlayerID() == set.getCancelPid()) {
                    handleError(request, response, "Invalid set.");
                    return;
                }

                tbGameStorer.cancelSet(set);

                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(cancelRedirectPage).
                            forward(request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileRedirectPage).
                            forward(request, response);
                }
            }
            // from jsp
            else if (command.equals("No")) {

                // can't cancel someone elses set
                if (playerData.getPlayerID() != set.getGame1().getPlayer1Pid() &&
                        playerData.getPlayerID() != set.getGame1().getPlayer2Pid()) {
                    handleError(request, response, "Invalid set.");
                    return;
                }

                // can't cancel it yourself
                if (playerData.getPlayerID() == set.getCancelPid()) {
                    handleError(request, response, "Invalid set.");
                    return;
                }

                tbGameStorer.declineCancel(set);

                long gid = Long.parseLong(gidStr);
                TBGame game = set.getGame(gid);
                String redirect = "";
                // if my turn redirect to the game
                String isMobile = (String) request.getParameter("mobileBrowser");
                if (game.getCurrentPlayer() == playerData.getPlayerID()) {
                    if (isMobile == null) {
                        redirect = "/gameServer/tb/game?gid=" + gidStr + "&command=load";
                    } else {
                        redirect = "/gameServer/tb/game?gid=" + gidStr + "&command=load&mobile=";
                    }
                }
                // else redirect back to list of games
                else {
                    redirect = cancelRedirectPage;
                }

                if (isMobile == null) {
                    isMobile = (String) request.getParameter("mobile");
                } else {
                    isMobile = null;
                }
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(redirect).forward(
                            request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileRedirectPage).
                            forward(request, response);
                }
            }

        } catch (TBStoreException tbe) {
            log4j.error("CancelServlet: " + sid, tbe);
            handleError(request, response, "Database error, please try again later.");
        } catch (Throwable t) {
            log4j.error("CancelServlet: " + sid, t);
            handleError(request, response, "Unknown error.");
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
