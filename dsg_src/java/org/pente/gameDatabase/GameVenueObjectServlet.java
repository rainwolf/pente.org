package org.pente.gameDatabase;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.apache.log4j.Category;
import org.pente.game.*;

public class GameVenueObjectServlet extends HttpServlet {

    private static Category log4j = Category.getInstance(GameVenueObjectServlet.class.getName());

    private byte[]		data;
    private Timer       timer;


    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            // update first so that a request doesn't beat the timer
            updateData();
            timer = new Timer(60 * 60 * 1000);

        } catch (Throwable t) {
            log4j.error("Error in init()", t);
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

    	long startTime = System.currentTimeMillis();
        //log4j.info("data len=" + data.length);
        response.setContentLength(data.length);
        OutputStream ro = response.getOutputStream();
        int i = 0;
        while (true) {
        	int n = i + 1024 > data.length ? data.length - i : 1024;
        	//log4j.info("n=" + n);
        	ro.write(data, i, n);
        	ro.flush();
        	i += n;
        	//try {
        	//	Thread.sleep(200);
        	//} catch (InterruptedException ie) {}
        	//log4j.debug("i=" + i);
        	if (i == data.length) break;
        }
        
        log4j.info("send in " + (System.currentTimeMillis() - startTime));
    }
    
    public void updateData() {
    	try {
	        MySQLGameVenueStorer gameVenueStorer = (MySQLGameVenueStorer) 
	    	getServletContext().getAttribute(GameVenueStorer.class.getName());
	    	ByteArrayOutputStream out = new ByteArrayOutputStream();
	        ObjectOutputStream o = new ObjectOutputStream(out);
	        o.writeObject(gameVenueStorer.getGameTree());
	
	        data = out.toByteArray();
    	} catch (Exception e) {
    		e.printStackTrace();
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
                        updateData();
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
}