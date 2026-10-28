package org.pente.filter;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/**
 * SPA fallback for the pentedb React app served as static files from
 * /gameServer/db/. Client-side routes such as /gameServer/db/p/1/180 or
 * /gameServer/db/g/123 have no file behind them, so a browser reload would 404.
 * For GET/HEAD requests to such "route-shaped" paths (no file extension in the
 * last segment, not under the asset dirs static/ and ai/) the request is
 * forwarded to /gameServer/db/index.html. Everything else passes through
 * untouched. Mapped after LoginFilter in web.xml so login rules still apply, and
 * it only matches /gameServer/db/*, so /api/db is never involved.
 */
public class DbDeepLinkFilter implements Filter {

    public static final String BASE = "/gameServer/db/";
    public static final String INDEX = "/gameServer/db/index.html";

    /** The path to forward to, or null if the request should pass through. */
    public static String forwardTarget(String method, String path) {
        if (method == null || path == null) return null;
        if (!"GET".equals(method) && !"HEAD".equals(method)) return null;
        if (!path.startsWith(BASE)) return null;
        String rest = path.substring(BASE.length());
        if (rest.isEmpty()) return null; // the directory itself: welcome file already works
        if (rest.startsWith("static/") || rest.startsWith("ai/")) return null;
        String last = rest.substring(rest.lastIndexOf('/') + 1);
        if (last.indexOf('.') >= 0) return null; // a real file (js, css, wasm, html, ...)
        return INDEX;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String uri = req.getRequestURI();
            String ctx = req.getContextPath();
            String path = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
            String target = forwardTarget(req.getMethod(), path);
            if (target != null) {
                req.getRequestDispatcher(target).forward(request, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
