package org.pente.gameServer.client.web;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;

public class ViewAvatarServlet extends HttpServlet {

    private static Category log4j =
            Category.getInstance(ViewAvatarServlet.class.getName());

    private DSGPlayerStorer dsgPlayerStorer = null;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();
            dsgPlayerStorer = (DSGPlayerStorer) ctx.getAttribute(
                    DSGPlayerStorer.class.getName());

        } catch (Throwable t) {
            log4j.error("Problem in init", t);
        }
    }

    public long getLastModified(HttpServletRequest request) {

        DSGPlayerData dsgPlayerData = null;
        String name = (String) request.getParameter("name");

        try {
            if (name == null) {
                log4j.error("Player to view avatar last mod for is null");
            } else {
                log4j.info("view player avatar last mod for " + name);
                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            }

        } catch (DSGPlayerStoreException e) {
            log4j.info("view player avatar last mod for " + name + " failed", e);
        }

        if (dsgPlayerData == null || !dsgPlayerData.isActive() ||
                !dsgPlayerData.hasAvatar()) {
            log4j.error("Player not found or inactive or has no" +
                    "avatar, returning current time for last mod");
            return System.currentTimeMillis();
        } else {
            log4j.info("last mod date = " + new java.util.Date(
                    dsgPlayerData.getAvatarLastModified()));
            return dsgPlayerData.getAvatarLastModified();
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

        try {
            if (name == null) {
                log4j.error("Player to view avatar for is null");
            } else {
                log4j.info("view player avatar for " + name);
                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
            }

        } catch (DSGPlayerStoreException e) {
            log4j.info("view player avatar for " + name + " failed", e);
        }

        if (dsgPlayerData == null || !dsgPlayerData.isActive() ||
                !dsgPlayerData.hasAvatar()) {
            log4j.error("Player not found or inactive or has no" +
                    "avatar, returning 404");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            response.setContentType(dsgPlayerData.getAvatarContentType());
            //response.setHeader("Cache-Control", "max-age=3600");
            response.setHeader("Cache-Control", "no-cache"); //HTTP 1.1
            response.setHeader("Pragma", "no-cache"); //HTTP 1.0
            response.setDateHeader("Expires", 0); //prevents caching at the proxy server

            OutputStream out = response.getOutputStream();
            out.write(dsgPlayerData.getAvatar());
            out.flush();
        }
    }
}
