package org.pente.gameServer.client.web;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Category;
import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerPreference;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.server.PasswordHelper;

import java.io.IOException;
import java.io.PrintWriter;

public class AgeVerificationServlet extends HttpServlet {

    private static final Category cat = Category.getInstance(AgeVerificationServlet.class.getName());

    CacheDSGPlayerStorer dsgPlayerStorer;
    private PasswordHelper passwordHelper;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext ctx = config.getServletContext();
        dsgPlayerStorer = (CacheDSGPlayerStorer) ctx.getAttribute(DSGPlayerStorer.class.getName());
        try {
            passwordHelper = (PasswordHelper) ctx.getAttribute(PasswordHelper.class.getName());
        } catch (Throwable t) {
            // passwordHelper is optional; log and continue
            cat.info("No PasswordHelper found in context, plain passwords will be compared.");
        }
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        String name = (String) request.getAttribute("name");
        String adultParam = request.getParameter("adult");
        boolean adult = adultParam != null && (adultParam.equalsIgnoreCase("true") || adultParam.equals("1") || adultParam.equalsIgnoreCase("y") || adultParam.equalsIgnoreCase("yes"));

        PrintWriter out = response.getWriter();

        if (name == null || name.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"result\":failed,\"error\":\"missing_name\"}");
            return;
        }

        try {
            DSGPlayerData player = dsgPlayerStorer.loadPlayer(name);
            if (player == null || !player.isActive()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"result\":failed,\"error\":\"invalid_player\"}");
                return;
            }

            player.setMobileAdult(adult);
            dsgPlayerStorer.updatePlayer(player);

            out.print("{\"result\":success,\"adult\":" + adult + "}");

        } catch (DSGPlayerStoreException e) {
            cat.error("Error accessing player store", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"result\":failed,\"error\":\"server_error\"}");
        } catch (Throwable t) {
            cat.error("Unexpected error in AgeVerificationServlet", t);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"result\":failed,\"error\":\"server_error\"}");
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // forward GET to POST handler for convenience
        doPost(request, response);
    }
}
