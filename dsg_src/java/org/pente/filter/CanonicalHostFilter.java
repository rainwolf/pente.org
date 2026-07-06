package org.pente.filter;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/**
 * Redirect the apex host (pente.org) to the canonical host (www.pente.org)
 * so logins live on a single hostname. GET/HEAD get a 301, other methods a
 * 308 so the method and body survive the redirect. Exempt: WebSocket
 * handshakes (clients treat a redirect as a connection failure, and the
 * live game room dials the apex host directly) and PayPal IPN, whose
 * notification URL must keep answering 200 at whatever host PayPal has
 * configured.
 */
public class CanonicalHostFilter implements Filter {

    public static final String APEX_HOST = "pente.org";
    public static final String CANONICAL_HOST = "www.pente.org";

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            if (APEX_HOST.equalsIgnoreCase(req.getServerName())
                    && !"websocket".equalsIgnoreCase(req.getHeader("Upgrade"))
                    && !req.getRequestURI().endsWith("/paypaLstnr")) {

                StringBuffer url = new StringBuffer();
                url.append(req.getScheme()).append("://").append(CANONICAL_HOST);
                url.append(req.getRequestURI());
                if (req.getQueryString() != null) {
                    url.append('?').append(req.getQueryString());
                }
                String method = req.getMethod();
                boolean safeMethod = "GET".equals(method) || "HEAD".equals(method);
                // 308 keeps the method and body on POST etc.; 301 would repost as GET
                res.setStatus(safeMethod ? HttpServletResponse.SC_MOVED_PERMANENTLY : 308);
                res.setHeader("Location", url.toString());
                // permanent redirects are cached forever by default; cap it so the
                // canonicalization can still be rolled back within a day
                res.setHeader("Cache-Control", "max-age=86400");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
