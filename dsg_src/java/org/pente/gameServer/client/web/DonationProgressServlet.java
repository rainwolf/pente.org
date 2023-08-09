package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;

public class DonationProgressServlet extends HttpServlet {

    private static Category log4j =
            Category.getInstance(DonationProgressServlet.class.getName());

    private DBHandler dbHandler = null;
    private int percentage;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            dbHandler = (DBHandler) ctx.getAttribute(
                    DBHandler.class.getName());

            percentage = 10;
        } catch (Throwable t) {
            log4j.error("Problem in init", t);
        }
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("image/jpeg");
        OutputStream out = response.getOutputStream();
        //out.write();
        out.flush();
    }
}
