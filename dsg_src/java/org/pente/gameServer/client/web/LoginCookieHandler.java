package org.pente.gameServer.client.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.servlet.http.*;

import org.pente.filter.CanonicalHostFilter;

public class LoginCookieHandler {

    public static final String NAME_COOKIE = "name2";
    public static final String PASSWORD_COOKIE = "password2";
    public static final String PLUGIN_COOKIE = "plugin";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 365;  // 1 year

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
                } else if (cookies[i].getName().equals(PASSWORD_COOKIE)) {
                    password = cookies[i].getValue();
                } else if (cookies[i].getName().equals(PLUGIN_COOKIE)) {
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

        if (cookieDomain(request) != null) {
            // a pre-domain-change host-only cookie would shadow the new
            // Domain variant (browser send order for same-name cookies is
            // undefined), so expire the host-only ones first
            expireCookie(NAME_COOKIE, "/", null, request, response);
            expireCookie(PASSWORD_COOKIE, "/", null, request, response);
            expireCookie(PLUGIN_COOKIE, "/", null, request, response);
        }

        writeCookie(nameCookie, request, response);
        writeCookie(passwordCookie, request, response);
        writeCookie(pluginCookie, request, response);
    }

    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter
            .ofPattern("EEE, dd-MMM-yyyy HH:mm:ss", Locale.US)
            .withZone(ZoneOffset.UTC);

    private void writeCookie(Cookie cookie, HttpServletRequest request, HttpServletResponse response) {
        // the header is assembled by hand, so a ';' etc. in a value would
        // let it masquerade as extra cookie attributes — refuse instead
        if (!cookieTokenSafe(cookie.getName()) || !cookieTokenSafe(cookie.getValue())) {
            return;
        }
        StringBuffer buf = new StringBuffer();
        buf.append(cookie.getName() + "=" + cookie.getValue() + "; Version=1; ");
        buf.append("Expires=" + EXPIRES_FORMAT.format(Instant.ofEpochMilli(System.currentTimeMillis() + cookie.getMaxAge() * 1000L)) + " GMT; ");
        String domain = cookieDomain(request);
        if (domain != null) {
            buf.append("Domain=" + domain + "; ");
        }
        buf.append("Path=" + cookie.getPath());
        response.addHeader("Set-Cookie", buf.toString());
    }

    private static boolean cookieTokenSafe(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == ';' || c == ',' || c == '"' || c == '\\') {
                return false;
            }
        }
        return true;
    }

    /** Domain=pente.org makes the login shared between pente.org and
     *  www.pente.org only — other subdomains must not receive the
     *  credential cookie. null on any other host (dev/localhost) keeps the
     *  cookie host-only so the browser doesn't reject it. */
    private static String cookieDomain(HttpServletRequest request) {
        String host = request.getServerName();
        if (host != null) {
            host = host.toLowerCase();
            if (host.equals(CanonicalHostFilter.APEX_HOST)
                    || host.equals(CanonicalHostFilter.CANONICAL_HOST)) {
                return CanonicalHostFilter.APEX_HOST;
            }
        }
        return null;
    }

    public void deleteCookie(
            HttpServletRequest request, HttpServletResponse response) {

        expireCookie(NAME_COOKIE, "/", null, request, response);
        expireCookie(PASSWORD_COOKIE, "/", null, request, response);
        expireCookie(NAME_COOKIE, "/gameServer", null, request, response);
        expireCookie(PASSWORD_COOKIE, "/gameServer", null, request, response);

        // the Domain=pente.org variants only ever exist at Path=/
        // (writeCookie), so one deletion per name covers them
        String domain = cookieDomain(request);
        if (domain != null) {
            expireCookie(NAME_COOKIE, "/", domain, request, response);
            expireCookie(PASSWORD_COOKIE, "/", domain, request, response);
        }

        // don't delete plugin cookie, is probably shared across multiple users
        // of the same pc
    }

    private void expireCookie(String name, String path, String domain,
            HttpServletRequest request, HttpServletResponse response) {

        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        cookie.setPath(request.getContextPath() + path);
        if (domain != null) {
            cookie.setDomain(domain);
        }
        response.addCookie(cookie);
    }
}