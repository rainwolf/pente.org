package org.pente.gameServer.client.web;

import org.apache.log4j.Category;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerPreference;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


public class ChangeAdsPersonalizationPreferenceServlet extends HttpServlet {

    private static final Category log4j =
            Category.getInstance(ChangeProfileServlet.class.getName());

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

    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response)
            throws ServletException, IOException {

        String redirectPage = "/gameServer/mobile/empty.jsp";
        String changeProfileError = null;
        String changeProfileSuccess = null;
        DSGPlayerData dsgPlayerData = null;


        try {

            String name = (String) request.getAttribute("name");
            if (name == null) {
                log4j.error("ChangeAdsPersonalizationPreferenceServlet failed: name=null");
                return;
            }
            log4j.info("ChangeAdsPersonalizationPreferenceServlet: name=" + name);

            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
                log4j.error("ChangeAdsPersonalizationPreferenceServlet failed: player invalid: " + name);
                return;
            }

            String email = (String) request.getParameter("personalizeAds");
            boolean emailDsgMessages = email != null && email.equals("Y");
            DSGPlayerPreference p = new DSGPlayerPreference(
                    "personalizeAds", Boolean.valueOf(emailDsgMessages));
            dsgPlayerStorer.storePlayerPreference(
                    dsgPlayerData.getPlayerID(), p);

        } catch (DSGPlayerStoreException e) {
            changeProfileError = "Database error.";
            log4j.error("ChangeAdsPersonalizationPreferenceServlet error.", e);
        } catch (Throwable t) {
            changeProfileError = "Unknown error, contact dweebo.";
            log4j.error("ChangeAdsPersonalizationPreferenceServlet error.", t);
        }

        request.setAttribute("dsgPlayerData", dsgPlayerData);
        request.setAttribute("changeProfileError", changeProfileError);
        request.setAttribute("changeProfileSuccess", changeProfileSuccess);
        getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}