package org.pente.turnBased.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

public class LogServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(
            LogServlet.class.getName());

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {

        String message = request.getParameter("message");
        if (message == null) {
            log4j.error("error. LogServlet empty message");
        } else {
            log4j.info(message);
        }

        PrintWriter out = response.getWriter();
        response.setContentType("text/plain");
        out.write("logged ok");
    }
}
