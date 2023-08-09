package org.pente.gameServer.tourney.web;

import org.apache.log4j.Category;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;
import org.pente.gameServer.tourney.TourneyStorer;

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
public class TournamentServlet extends HttpServlet {
    private static final String errorRedirectPage = "/gameServer/error.jsp";


    private static final Category log4j =
            Category.getInstance(TournamentServlet.class.getName());

    private TourneyStorer tourneyStorer;
    private ServletContext ctx;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {
            ctx = getServletContext();
            Resources resources = (Resources)
                    ctx.getAttribute(Resources.class.getName());
            tourneyStorer = resources.getTourneyStorer();

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

        String eidStr = (String) request.getParameter("eid");
        String crownCommand = (String) request.getParameter("crown");

        if (player == null || !"rainwolf".equals(player)) {
            handleError(request, response, "illegal access");
            return;
        }

        if (eidStr == null) {
            handleError(request, response, "no tournament event id");
            return;
        }
        if (crownCommand == null) {
            handleError(request, response, "no action specified");
            return;
        }

        int eid = Integer.parseInt(eidStr);
        if (crownCommand.equals("assign")) {
            try {
                tourneyStorer.assignCrown(eid, 0, 0, 0);
            } catch (Throwable throwable) {
                handleError(request, response, "error assigning crown " + throwable);
                return;
            }
        }
        if (crownCommand.equals("remove")) {
            try {
                tourneyStorer.removeCrown(eid, 0, 0, 0);
            } catch (Throwable throwable) {
                handleError(request, response, "error removing crown " + throwable);
                return;
            }
        }

        if (request.getParameter("mobile") != null) {
            response.sendRedirect("/gameServer/tb/empty.jsp");
            return;
        }

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
