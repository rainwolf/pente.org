/**
 * GameVenueJSServlet.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameDatabase;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.apache.log4j.*;

import org.pente.filter.http.*;
import org.pente.game.*;

public class GameVenueJSServlet extends HttpServlet {

    private long lastModified;
    private String jsString;
    private Timer timer;

    private static Category cat = Category.getInstance(GameVenueJSServlet.class.getName());

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {
            ServletContext ctx = config.getServletContext();

            jsString = "";
            lastModified = System.currentTimeMillis();
            // update first so that a request doesn't beat the timer
            updateJsString();
            timer = new Timer(60 * 60 * 1000);

        } catch (Throwable t) {
            cat.error("Error in init()", t);
        }
    }

    // used by servlet container to send back 304 (not changed)
    // if browser sends in-modified-since header
    public long getLastModified(HttpServletRequest request) {

        return lastModified;
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        //cat.debug("doPost()");

        String update = (String) request.getParameter("update");
        if (update != null) {
            cat.info("manual reload");
            timer.update();
        }

        PrintWriter out = response.getWriter();
        response.setContentType(HttpConstants.CONTENT_TYPE_JS);

        synchronized (jsString) {
            out.print(jsString);
        }
    }

    public void destroy() {
        if (timer != null) {
            timer.stopRunning();
        }
    }

    private final class Timer implements Runnable {

        private int delay;
        private Thread thread;
        private volatile boolean running = true;
        private volatile boolean update = false;

        private Timer(int delay) {
            this.delay = delay;

            thread = new Thread(this);
            thread.start();
        }

        public void run() {

            //cat.debug("timer run()");

            while (running) {

                try {
                    if (!update) {
                        Thread.sleep(delay);
                    }

                    if (running) {
                        update = false;
                        updateJsString();
                    }

                } catch (InterruptedException ex) {
                }
            }
        }

        public void update() {
            update = true;
            if (thread != null) {
                thread.interrupt();
            }
        }

        public void stopRunning() {
            running = false;

            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    public void updateJsString() {

        cat.info("updateJsString()");

        String newJsString;

        GameVenueStorer gameVenueStorer = (GameVenueStorer) getServletContext().getAttribute(GameVenueStorer.class.getName());

        GameVenueJSFormat gameVenueJSFormat = new GameVenueJSFormat();
        StringBuffer buf = new StringBuffer();
        buf = gameVenueJSFormat.format(gameVenueStorer.getGameTree());

        synchronized (jsString) {
            jsString = buf.toString();
        }

        lastModified = System.currentTimeMillis();
    }
}