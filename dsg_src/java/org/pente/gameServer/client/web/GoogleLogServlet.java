package org.pente.gameServer.client.web;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.log4j.*;

public class GoogleLogServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance("google");

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        log4j.info(request.getParameter("ref"));
    }
}
