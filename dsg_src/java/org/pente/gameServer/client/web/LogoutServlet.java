package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

public class LogoutServlet extends HttpServlet {

    private static final String redirectPage = "/gameServer/login.jsp";

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        LoginCookieHandler loginCookieHandler = new LoginCookieHandler();
        loginCookieHandler.deleteCookie(request, response);

        request.setAttribute("name", null);
        request.setAttribute("password", null);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        getServletContext().getRequestDispatcher(redirectPage).forward(request, response);
    }
}

