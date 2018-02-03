package org.pente.gameServer.client.web;

import org.apache.log4j.Category;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JNLPServlet extends HttpServlet {

    private static Category log4j =
            Category.getInstance(ViewAvatarServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer = null;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(
                    DSGPlayerStorer.class.getName());

        }
        catch (Throwable t) {
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

        DSGPlayerData dsgPlayerData = null;
        String name = (String) request.getParameter("name");
        String password = (String) request.getParameter("password");
        try {
            if (name != null) {
                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            }
        } catch (DSGPlayerStoreException e) {
            e.printStackTrace();
        }
    
        if (password != null && password.equals(dsgPlayerData.getPassword())) {
            getServletContext().getRequestDispatcher("/gameServer/pentejnlp.jsp?name="+name+"&password="+dsgPlayerData.getPassword()).forward(
                    request, response);
        } else {
            getServletContext().getRequestDispatcher("/gameServer/pentejnlp.jsp").forward(
                    request, response);
        }
    }
}
