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

        ServletContext ctx = getServletContext();
        Resources resources = (Resources)
                ctx.getAttribute(Resources.class.getName());
        SessionListener sessionListener = (SessionListener) ctx.getAttribute(SessionListener.class.getName());
        List<WhosOnlineRoom> rooms = WhosOnline.getPlayers(resources, sessionListener);
        
        PrintWriter out = new PrintWriter(response.getOutputStream());
        response.setContentType("text/plain");

        log4j.info("sending active servers");

        for (Iterator it = resources.getServerData().iterator(); it.hasNext();) {
            ServerData data = (ServerData) it.next();
            String serverName = data.getName();
            log4j.info(data.getPort() + " " + serverName);
            boolean empty = true;
            for (WhosOnlineRoom room : rooms) {
                if (serverName.equals(room.getName())) {
                    empty = false;
                    out.write(data.getPort() + " " + serverName + " (" + room.getPlayers().size() + ")");
                    out.write("\n");
                    break;
                }
            }
            if (empty) {
                out.write(data.getPort() + " " + serverName + " (0)");
                out.write("\n");
            }
        }
        out.flush();
    }
}

