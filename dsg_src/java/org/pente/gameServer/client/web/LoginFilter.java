package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.turnBased.Utilities;

public class LoginFilter implements Filter {

    private FilterConfig filterConfig;

    private String loginPage;

    private LoginAccessController loginAccessController;
    private LoginHandler loginHandler;
    private PasswordHelper passwordHelper;
    private ActivityLogger activityLogger;
    private Resources resources;
    private SessionListener sessionListener;

    private static final Category cat = Category.getInstance(LoginFilter.class.getName());

    public void init(FilterConfig config) throws ServletException {
        this.filterConfig = config;

        loginPage = filterConfig.getInitParameter("loginPage");
        if (loginPage == null) {
            cat.error("loginPage not defined for LoginFilter");
        }

        loginAccessController = new SimpleLoginAccessController();

        try {
            ServletContext ctx = filterConfig.getServletContext();

            DBHandler dbHandler = (DBHandler) ctx.getAttribute(DBHandler.class.getName());
            loginHandler = new SmallLoginHandler(dbHandler);

            resources = (Resources) ctx.getAttribute(Resources.class.getName());

            passwordHelper = (PasswordHelper) ctx.getAttribute(
                    PasswordHelper.class.getName());

            activityLogger = (ActivityLogger) ctx.getAttribute(
                    ActivityLogger.class.getName());

            sessionListener = (SessionListener) ctx.getAttribute(
                    SessionListener.class.getName());

        } catch (Throwable t) {
            cat.error("Problem creating login handler", t);
        }
    }

    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain)
            throws IOException, ServletException {

        boolean forward = false;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            // no need to filter these
            String uri = httpRequest.getRequestURI();
            if (uri.startsWith("/gameServer/images") ||
                    uri.startsWith("/res") ||
                    uri.startsWith("/gameServer/js") ||
                    uri.startsWith("/gameServer/forums/style.jsp") ||
                    uri.startsWith("/gameServer/forums/images") ||
                    uri.startsWith("/robots.txt") ||
                    uri.startsWith("/favicon.ico") ||
                    uri.startsWith("/gameServer/lib") ||
                    uri.startsWith("/gameServer/avatar") ||
                    uri.startsWith("/gameServer/appletAd.jsp") ||
                    uri.startsWith("/gameServer/META-INF/")) {
                filterChain.doFilter(request, response);
                return;
            }

            httpResponse.setBufferSize(30000);

            String name = request.getParameter(LoginCookieHandler.NAME_COOKIE);
            String password = request.getParameter(LoginCookieHandler.PASSWORD_COOKIE);
            boolean pluginChoiceMade = false;
            boolean plugin = false;

            boolean validLogin = true;
            boolean loginAttempted = false;
            LoginCookieHandler loginCookieHandler = null;

            HttpSession session = httpRequest.getSession(false);

            String userAgent = httpRequest.getHeader("User-Agent");
            boolean spider = false;
            if (userAgent != null) {
                if (userAgent.contains("Mediapartners-Google") ||
                        userAgent.contains("Googlebot") ||
                        userAgent.contains("Yahoo! Slurp") ||
                        userAgent.startsWith("msnbot")) {

                    // don't let spiders into tb
                    if (uri.indexOf("/tb/") > -1) {
                        spider = false;
                    } else {
                        spider = true;
                    }
                }
            }

            // allow special access for spiders
            if (spider) {
                name = "guest";
                password = "spider";
                activityLogger.viewPage(
                        new ActivityData(name, request.getRemoteAddr()), uri);
                request.setAttribute("spider", new Object());
            }
            // get session, if exists proceed
            else if (session != null && session.getAttribute("name") != null) {

                name = (String) session.getAttribute("name");
                password = (String) session.getAttribute("password");
                pluginChoiceMade = session.getAttribute("pluginChoiceMade") != null;
                plugin = session.getAttribute("plugin") != null;
                activityLogger.viewPage(
                        new ActivityData(name, request.getRemoteAddr()), uri);
                sessionListener.visit(name, uri);
            }
            // else check if this is a new login, or try reading info
            // from cookie and create a new session
            else {
                loginCookieHandler = new LoginCookieHandler();
                // load this because plugin might be set, even if name/pass aren't
                loginCookieHandler.loadCookie(httpRequest);

                if (name != null && password != null) {
                    loginAttempted = true;
                } else {
                    name = loginCookieHandler.getName();
                    password = loginCookieHandler.getPassword();
                }

                if (name != null) {
                    name = name.trim().toLowerCase();
                }
                if (password != null) {
                    password = password.trim();
                }

                if (name == null || password == null ||
                        name.equals("") || password.equals("")) {
                    validLogin = false;
                } else {
                    // new logins need to encrypt the password before verifying
                    // otherwise, cookie stores password in encrypted format
                    if (loginAttempted) {
                        password = passwordHelper.encrypt(password);
                    }

                    int loginResult = loginHandler.isValidLogin(name, password);
                    if (loginResult == LoginHandler.INVALID) {
                        cat.debug("Invalid login for " + name);
                        validLogin = false;
                        request.setAttribute("invalidLogin", "invalid");
                    } else if (loginResult == LoginHandler.SPEED) {
                        cat.debug("Login for converted speed name " + name);
                        validLogin = false;
                        request.setAttribute("invalidLogin", "speed");
                    } else if (loginResult == LoginHandler.VALID) {
                        // log login activity here to log ip/name
                        activityLogger.login(
                                new ActivityData(name, request.getRemoteAddr()),
                                httpRequest.getRequestURI());
                        sessionListener.visit(name, "login");

                        //this is done instead using dsgplayerstorer so cache reflects change
                        //loginHandler.login(name, password);//record timestamp of last login

                        // create new session
                        session = httpRequest.getSession(true);
                        // session.setMaxInactiveInterval(10);
                        session.setAttribute("name", name);
                        session.setAttribute("password", password);
                        session.setAttribute("ip", request.getRemoteAddr());
                        if (loginCookieHandler.pluginChoiceMade()) {
                            pluginChoiceMade = true;
                            session.setAttribute("pluginChoiceMade", new Object());
                            if (loginCookieHandler.usePlugin()) {
                                plugin = true;
                                session.setAttribute("plugin", new Object());
                            }
                        }

                        if (loginAttempted) {
                            loginCookieHandler.setName(name);
                            loginCookieHandler.setPassword(password);
                            loginCookieHandler.setCookie(httpRequest, httpResponse);
                        }

                        // load game room size preference and store in session
                        // for use on every page load
                        try {
                            DSGPlayerData data = resources.getDsgPlayerStorer().loadPlayer(name);
                            data.loginSuccessful();
                            resources.getDsgPlayerStorer().updatePlayer(data);
                            List prefs = resources.getDsgPlayerStorer().loadPlayerPreferences(data.getPlayerID());
                            for (Iterator it = prefs.iterator(); it.hasNext(); ) {
                                DSGPlayerPreference p = (DSGPlayerPreference) it.next();
                                if (p.getName().equals("gameRoomSize")) {
                                    session.setAttribute("gameRoomSize", p.getValue());
                                }
                            }
                        } catch (DSGPlayerStoreException dpse) {
                            cat.error("LoginFilter error loading prefs.", dpse);
                        }
                    }
                }
            }

            if (validLogin) {
                request.setAttribute("name", name);
                request.setAttribute("password", password);
                if (pluginChoiceMade) {
                    if (plugin) {
                        request.setAttribute("plugin", new Object());
                    }
                    request.setAttribute("pluginChoiceMade", new Object());
                }
            }

            // restrict access to the admin area
            if (loginAccessController.requiresAdmin(httpRequest.getRequestURI())) {
                if (!validLogin) {
                    httpResponse.sendError(404, httpRequest.getRequestURI());
                    return;
                } else {
                    try {
                        cat.debug("lookup player data for " + name);
                        DSGPlayerData d = resources.getDsgPlayerStorer().
                                loadPlayer(name);
                        if (!d.isAdmin()) {
                            cat.debug("player is not admin, 404");
                            httpResponse.sendError(404, httpRequest.getRequestURI());
                            return;
                        }

                    } catch (DSGPlayerStoreException dpse) {
                        cat.error(dpse);
                        httpResponse.sendError(404, httpRequest.getRequestURI());
                        return;
                    }
                }
            }

            if (loginAccessController.isRestricted(httpRequest.getRequestURI())) {
                httpResponse.sendError(404, httpRequest.getRequestURI());
                return;
            } else if (validLogin) {
                //nothing

                if (uri.indexOf("/pentedb/") > -1) {
                    try {
                        if (!Utilities.allowAccess(
                                resources.getDsgPlayerStorer(),
                                httpRequest, httpResponse)) {

                            httpResponse.sendError(404, httpRequest.getRequestURI());
                            return;
                        }
                    } catch (Throwable t) {
                        cat.error("problem with pentedb access check.", t);
                        forward = false;
                    }
                }
                // turn-based now live
                // if accessing tb (exception being looking at played games)
//                if (uri.indexOf("/tb/") > -1 && 
//                	uri.indexOf("/tb/log") == -1) {
//                	try {
//	                	if (!Utilities.allowAccess(
//	                		resources.getDsgPlayerStorer(),
//	                		httpRequest, httpResponse)) {
//
//	        				filterConfig.getServletContext().getRequestDispatcher(
//								"/gameServer/tb/donorOnly.jsp").forward(request, response);
//	        				return;
//	                	}
//                	} catch (Throwable t) {
//                		cat.error("problem with tb access check.", t);
//                		forward = false;
//                	}
//                }
            } else if (loginAttempted || loginAccessController.requiresLogin(httpRequest.getRequestURI())) {
                request.setAttribute("loginAction", httpRequest.getRequestURI());
                forward = true;
                filterConfig.getServletContext().getRequestDispatcher(loginPage).forward(request, response);
            }

            if (!forward) {
                filterChain.doFilter(request, response);
            }
        }

        // non http filtering?  do nothing
    }

    public void destroy() {
    }
}