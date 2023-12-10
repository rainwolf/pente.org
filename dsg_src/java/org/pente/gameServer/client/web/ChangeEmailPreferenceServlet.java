package org.pente.gameServer.client.web;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;
import org.apache.commons.fileupload.*;

import com.jivesoftware.base.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.jive.DSGUserManager;


public class ChangeEmailPreferenceServlet extends HttpServlet {

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
                log4j.error("ChangeEmailPreferenceServlet failed: name=null");
                return;
            }
            log4j.info("ChangeEmailPreferenceServlet: name=" + name);

            dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            if (dsgPlayerData == null || !dsgPlayerData.isActive()) {
                log4j.error("ChangeEmailPreferenceServlet failed: player invalid: " + name);
                return;
            }

            String email = (String) request.getParameter("emailMe");
            boolean emailDsgMessages = email != null && email.equals("Y");
            DSGPlayerPreference p = new DSGPlayerPreference(
                    "emailDsgMessages", Boolean.valueOf(emailDsgMessages));
            dsgPlayerStorer.storePlayerPreference(
                    dsgPlayerData.getPlayerID(), p);

        } catch (DSGPlayerStoreException e) {
            changeProfileError = "Database error.";
            log4j.error("ChangeEmailPreferenceServlet error.", e);
        } catch (Throwable t) {
            changeProfileError = "Unknown error, contact dweebo.";
            log4j.error("ChangeEmailPreferenceServlet error.", t);
        }

        request.setAttribute("dsgPlayerData", dsgPlayerData);
        request.setAttribute("changeProfileError", changeProfileError);
        request.setAttribute("changeProfileSuccess", changeProfileSuccess);
        getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}