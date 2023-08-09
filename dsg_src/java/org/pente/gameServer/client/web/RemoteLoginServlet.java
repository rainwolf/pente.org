package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.DBHandler;
import org.pente.game.*;
import org.pente.gameDatabase.GameStorerSearchRequestData;
import org.pente.gameDatabase.GameStorerSearchRequestFilterData;
import org.pente.gameDatabase.GameStorerSearchResponseData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestData;
import org.pente.gameDatabase.SimpleGameStorerSearchRequestFilterData;
import org.pente.gameDatabase.SimpleGameStorerSearchResponseData;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.turnBased.*;

public class RemoteLoginServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(
            RemoteLoginServlet.class.getName());

    private Resources resources;

    private PasswordHelper passwordHelper;
    private ActivityLogger activityLogger;
    private LoginHandler loginHandler;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            resources = (Resources) ctx.getAttribute(Resources.class.getName());

            DBHandler dbHandler = (DBHandler) ctx.getAttribute(DBHandler.class.getName());
            loginHandler = new SmallLoginHandler(dbHandler);

            passwordHelper = (PasswordHelper) ctx.getAttribute(
                    PasswordHelper.class.getName());

            activityLogger = (ActivityLogger) ctx.getAttribute(
                    ActivityLogger.class.getName());

        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    // params
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        try {
            LoginCookieHandler loginCookieHandler = new LoginCookieHandler();
            loginCookieHandler.loadCookie(request);

            String name = loginCookieHandler.getName();
            String password = loginCookieHandler.getPassword();


            if (name != null) {
                name = name.trim().toLowerCase();
            }
            if (password != null) {
                password = password.trim();
            }

            int loginResult = loginHandler.isValidLogin(name, password);

            if (loginResult != LoginHandler.VALID) {
                // if fail, try with it encrypted
                password = passwordHelper.encrypt(password);
                loginResult = loginHandler.isValidLogin(name, password);
            }

            DSGPlayerData pdata = resources.getDsgPlayerStorer().loadPlayer(name);
            boolean access_allowed = pdata.databaseAccess() || pdata.getRegisterDate().getTime() > System.currentTimeMillis() - 1000L * 3600 * 24 * 30;


            if (loginResult == LoginHandler.VALID && access_allowed) {
                activityLogger.login(
                        new ActivityData(name, request.getRemoteAddr()),
                        request.getRequestURI());
                response.setStatus(200);
                response.getOutputStream().write("OK".getBytes());
                loginCookieHandler.setPassword(password);
                loginCookieHandler.setCookie(request, response);
            } else {
                log4j.info("Invalid login: " + name);
                response.setStatus(404);
                response.getOutputStream().write("Bad".getBytes());
            }
            return;

        } catch (Throwable t) {
            log4j.error("RemoteLoginServlet, error", t);
        }
        response.setStatus(500);
        response.getOutputStream().write("Error".getBytes());
    }
}