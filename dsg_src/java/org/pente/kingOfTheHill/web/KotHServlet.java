package org.pente.kingOfTheHill.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Category;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.server.Resources;
import org.pente.kingOfTheHill.CacheKOTHStorer;

import java.io.IOException;

public class KotHServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(
            KotHServlet.class.getName());

    private static final String redirectPage = "/gameServer/stairs.jsp";
    private static final String errorRedirectPage = "/gameServer/stairs.jsp";
    private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";

    private Resources resources;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

    // expected http params:
    // game - required
    // join or leave - required
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String error = null;

        DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
        CacheKOTHStorer kothStorer = resources.getKOTHStorer();

        String meName = (String) request.getAttribute("name");
        String gameStr = request.getParameter("game");
        int game = -1;

        if (gameStr != null) {
            try {
                game = Integer.parseInt(gameStr);
            } catch (NumberFormatException nef) {
                log4j.error("Problem with the game to start turn-based game.", nef);
                error = "Error, parsing the game.";
            }
        }

        boolean join = false;
        join = request.getParameter("join") != null;

        if (error == null) {
            error = "Invalid game.";
            for (int i = 0; i < GridStateFactory.TB_GAMES.length; i++) {
                if (game == GridStateFactory.TB_GAMES[i]) {
                    error = null;
                    break;
                }
            }
        }

        if (error == null) {
            try {
                long pid = dsgPlayerStorer.loadPlayer(meName).getPlayerID();
                if (join) {
                    kothStorer.addPlayer(game, pid);
                    kothStorer.updatePlayerLastGameDate(game, pid);
                } else {
                    kothStorer.removePlayer(game, pid);
                }
            } catch (Throwable throwable) {
                log4j.error("Problem creating set", throwable);
                error = "Database error creating set, try again later.";
            }
        }

        request.setAttribute("game", "" + game);
        if (error != null) {
            request.setAttribute("error", error);
            getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                    request, response);
        } else {
            String isMobile = (String) request.getParameter("mobile");
            if (isMobile == null) {
                getServletContext().getRequestDispatcher(redirectPage).forward(
                        request, response);
            } else {
                response.sendRedirect(mobileRedirectPage);
            }
        }
    }


}
