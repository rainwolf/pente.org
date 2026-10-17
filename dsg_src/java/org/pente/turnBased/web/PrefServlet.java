package org.pente.turnBased.web;

import java.io.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;

public class PrefServlet extends HttpServlet {

    private static final long serialVersionUID = 4081743069807558230L;

    private static final Category log4j = Category.getInstance(
            PrefServlet.class.getName());

    private transient Resources resources;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {


        String name = request.getParameter("name");
        String value = request.getParameter("value");

        String player = (String) request.getAttribute("name");

        System.out.println(name + "," + value + "," + player);
        if (player == null || name == null || value == null) {
            log4j.error("error. PropertyServlet empty name or value");
        } else if (!name.equals("attach") && !name.equals("tbCycle")) {
            log4j.error("error. Trying to set an unknown property");
        } else if (name.equals("tbCycle") && !value.equals("true") && !value.equals("false")) {
            log4j.error("error. Invalid value for tbCycle property");
        } else {
            try {
                DSGPlayerData playerData = resources.getDsgPlayerStorer().
                        loadPlayer(player);
                DSGPlayerPreference pref = new DSGPlayerPreference(
                        name, value);
                resources.getDsgPlayerStorer().storePlayerPreference(
                        playerData.getPlayerID(), pref);
            } catch (DSGPlayerStoreException dpse) {
            }
        }

        PrintWriter out = response.getWriter();
        response.setContentType("text/plain");
        out.write("property set");
    }
}
