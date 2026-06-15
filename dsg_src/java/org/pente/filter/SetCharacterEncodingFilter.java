package org.pente.filter;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/**
 * Set the character encoding of the request to as set in "encoding"
 * this should be done before the request is accessed.
 */
public class SetCharacterEncodingFilter implements Filter {

    private FilterConfig filterConfig;
    private String encoding;

    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
        this.encoding = filterConfig.getInitParameter("encoding");
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String enc = this.encoding;
        // PayPal posts its IPN body in the account charset (windows-1252), not UTF-8.
        // The body must be decoded with that charset BEFORE the first getParameter*
        // call parses & caches it -- otherwise a byte like 0xE9 ('e'-acute) is mangled
        // to U+FFFD and the _notify-validate postback no longer matches what PayPal
        // sent, so PayPal returns INVALID. This filter is the first in the chain, so
        // setting it here is honoured even though LoginFilter reads request parameters
        // before PaypalIPNListenerServlet ever runs (the servlet's own
        // setCharacterEncoding is too late by then -- it is a no-op after parsing).
        if (request instanceof HttpServletRequest) {
            String uri = ((HttpServletRequest) request).getRequestURI();
            if (uri != null && uri.endsWith("/paypaLstnr")) {
                enc = "windows-1252";
            }
        }
        request.setCharacterEncoding(enc);
        chain.doFilter(request, response);
    }

    public void destroy() {
        this.filterConfig = null;
    }
}
