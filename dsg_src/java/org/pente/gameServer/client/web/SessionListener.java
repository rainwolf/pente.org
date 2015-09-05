package org.pente.gameServer.client.web;

import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

public class SessionListener implements
    HttpSessionListener, 
    HttpSessionActivationListener,
    ServletContextListener {

    private static final Category log4j =
        Category.getInstance("SessionListener");

    private List<HttpSession> activeSessions = 
    	Collections.synchronizedList(new ArrayList<HttpSession>());
    private Set<String> activePlayers =
        Collections.synchronizedSet(new HashSet<String>());
    private Set<String> activeMobilePlayers =
        Collections.synchronizedSet(new HashSet<String>());
    private Map<String, String> pages = new HashMap<String, String>();
    
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        servletContextEvent.getServletContext().setAttribute(
            SessionListener.class.getName(), this);
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {    
    }

    public void sessionDidActivate(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        log4j.info("session activated");

        synchronized (activeSessions) {
            activeSessions.add(session);
        }
    }
    public void sessionWillPassivate(HttpSessionEvent event) {
    }
    
    public List<HttpSession> getActiveSessions() {
        return activeSessions;
    }
    
    public void visit(String name, String page) {
        if (page.contains("gameServer/mobile/")) {
            synchronized (activeMobilePlayers) {
                activeMobilePlayers.add(name);
                pages.put(name, page);
            }
        } else {
            if (page.contains("gameServer/index.jsp")) {
                synchronized (activeMobilePlayers) {
                    activeMobilePlayers.remove(name);
                }
            }
            synchronized (activePlayers) {
                activePlayers.add(name);
                pages.put(name, page);
            }
        }
    }
    public String getLastPage(String name) {
    	synchronized (activePlayers) {
    		return pages.get(name);
    	}
    }
    public List<String> getActivePlayers() {
        return new ArrayList<String>(activePlayers);
    }
    public List<String> getActiveMobilePlayers() {
        return new ArrayList<String>(activeMobilePlayers);
    }
    public boolean isActive(String name) {
    	synchronized (activePlayers) {
    		return activePlayers.contains(name);
    	}
    }
    
    public void sessionCreated(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        log4j.info("session created");

        synchronized (activeSessions) {
            activeSessions.add(session);
        }
    }

    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        log4j.info("session destroyed for " + session.getAttribute("name"));
        
        synchronized (activeSessions) {
            activeSessions.remove(session);
        }

        try {
	        String name = (String) session.getAttribute("name");
	        if (name != null) {
                synchronized (activePlayers) {
                    activePlayers.remove(name);
                    pages.remove(name);
                }
                synchronized (activeMobilePlayers) {
                    activeMobilePlayers.remove(name);
                    pages.remove(name);
                }
	        }
        } catch (IllegalStateException ignore) {}
    }
}
