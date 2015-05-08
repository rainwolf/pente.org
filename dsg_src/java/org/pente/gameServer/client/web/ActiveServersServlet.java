package org.pente.gameServer.client.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.ServerData;
import org.pente.gameServer.server.*;

public class ActiveServersServlet extends HttpServlet {

	private static final Category log4j =
		Category.getInstance(ActiveServersServlet.class.getName());

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {

        Resources resources = (Resources) 
            getServletContext().getAttribute(Resources.class.getName());

        PrintWriter out = new PrintWriter(response.getOutputStream());
        response.setContentType("text/plain");

        log4j.info("sending active servers");
        
        for (Iterator it = resources.getServerData().iterator(); it.hasNext();) {
            ServerData data = (ServerData) it.next();
            log4j.info(data.getPort() + " " + data.getName());
            out.write(data.getPort() + " " + data.getName());
            out.write("\n");
        }
        out.flush();
    }
}

