package org.pente.gameServer.client.web;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.*;

public class LoginCookieHandler {

    public static final String      NAME_COOKIE = "name2";
    public static final String      PASSWORD_COOKIE = "password2";
    public static final String      PLUGIN_COOKIE = "plugin";
    private static final int        COOKIE_MAX_AGE = 60 * 60 * 24 * 365;  // 1 year

    private String name;
    private String password;
    private static final int NO_PLUGIN_CHOICE = 0;
    private static final int DONT_USE_PLUGIN = 1;
    private static final int USE_PLUGIN = 2;
    private int plugin = NO_PLUGIN_CHOICE;

    public LoginCookieHandler() {
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }
    
    public void setPluginChoice(boolean choice) {
        plugin = choice ? USE_PLUGIN : DONT_USE_PLUGIN;
    }
    public boolean pluginChoiceMade() {
        return plugin != NO_PLUGIN_CHOICE;
    }
    public boolean usePlugin() {
        return plugin == USE_PLUGIN;
    }

    public void loadCookie(HttpServletRequest request) {

        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
	        for (int i = 0; i < cookies.length; i++) {
	        	if (cookies[i].getName().equals(NAME_COOKIE)) {
	                name = cookies[i].getValue();
	            }
	            else if (cookies[i].getName().equals(PASSWORD_COOKIE)) {
	                password = cookies[i].getValue();
	            }
                else if (cookies[i].getName().equals(PLUGIN_COOKIE)) {
                    plugin = cookies[i].getValue().equals(
                        String.valueOf(USE_PLUGIN)) ?
                        USE_PLUGIN : DONT_USE_PLUGIN;
                }
	        }
        }
    }
    public static String getCookie(HttpServletRequest request, String name) {
    	Cookie cookies[] = request.getCookies();
        if (cookies != null) {
	        for (int i = 0; i < cookies.length; i++) {
	        	if (cookies[i].getName().equals(name)) {
	                return cookies[i].getValue();
	            }
	        }
        }
        return null;
    }

    public void setCookie(
        HttpServletRequest request, HttpServletResponse response) {

        Cookie nameCookie = new Cookie(NAME_COOKIE, name);
        Cookie passwordCookie = new Cookie(PASSWORD_COOKIE, password);
        Cookie pluginCookie = new Cookie(PLUGIN_COOKIE, String.valueOf(plugin));

        nameCookie.setMaxAge(COOKIE_MAX_AGE);
        passwordCookie.setMaxAge(COOKIE_MAX_AGE);
        pluginCookie.setMaxAge(COOKIE_MAX_AGE);

        nameCookie.setPath(request.getContextPath() + "/");
        passwordCookie.setPath(request.getContextPath() + "/");
        pluginCookie.setPath(request.getContextPath() + "/");

//        response.addCookie(nameCookie);
//        response.addCookie(passwordCookie);
//        response.addCookie(pluginCookie);
        
        writeCookie(nameCookie, request, response);
        writeCookie(passwordCookie, request, response);
        writeCookie(pluginCookie, request, response);
    }
    private static final DateFormat df = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss");
    private void writeCookie(Cookie cookie, HttpServletRequest request, HttpServletResponse response) {
        StringBuffer buf = new StringBuffer();
        buf.append(cookie.getName() + "=\"" + cookie.getValue() + "\"; Version=1; ");
        buf.append ("Expires=" + df.format(new Date(System.currentTimeMillis() + cookie.getMaxAge() *1000L)) + " GMT; ");
        buf.append("Path=" + cookie.getPath());
        response.addHeader("Set-Cookie", buf.toString());
    }
    
    public void deleteCookie(
        HttpServletRequest request, HttpServletResponse response) {

        Cookie nameCookie = new Cookie(NAME_COOKIE, "");
        Cookie passwordCookie = new Cookie(PASSWORD_COOKIE, "");

        deleteCookie(nameCookie, "/", request, response);
        deleteCookie(passwordCookie, "/", request, response);
        deleteCookie(nameCookie, "/gameServer", request, response);
        deleteCookie(passwordCookie, "/gameServer", request, response);

        // don't delete plugin cookie, is probably shared across multiple users
        // of the same pc
    }
    
    public void deleteCookie(Cookie cookie, String path,
        HttpServletRequest request, HttpServletResponse response) {
        
        cookie.setMaxAge(0);
        cookie.setPath(request.getContextPath() + path);
        response.addCookie(cookie);
    }
}