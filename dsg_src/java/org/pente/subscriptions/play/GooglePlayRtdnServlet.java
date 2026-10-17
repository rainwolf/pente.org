package org.pente.subscriptions.play;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.log4j.Category;

/**
 * /gameServer/googlePlayRTDN: Pub/Sub push endpoint for Play real-time developer notifications
 * (spec 2026-10-11 §4.6). Public path (not in loginPages), authenticated by the push OIDC token.
 */
public class GooglePlayRtdnServlet extends HttpServlet {

    private static final long serialVersionUID = 7007486796395836381L;

    private static final Category log4j = Category.getInstance(GooglePlayRtdnServlet.class.getName());

    /** Pub/Sub push bodies are small; anything bigger is not an RTDN */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private transient volatile JwtSignatureVerifier verifier;

    public void init() throws ServletException {
        String audience = getServletContext().getInitParameter("googlePlayRtdnAudience");
        if (audience != null && !audience.trim().isEmpty()) {
            verifier = new GoogleJwtVerifier(audience.trim());
        } else {
            log4j.error("GooglePlayRtdnServlet: googlePlayRtdnAudience not set, every notification gets 401");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ServletContext ctx = getServletContext();
        String audience = ctx.getInitParameter("googlePlayRtdnAudience");
        String serviceAccount = ctx.getInitParameter("googlePlayRtdnServiceAccount");
        if (!RtdnAuth.authorized(request.getHeader("Authorization"), verifier,
                audience == null ? null : audience.trim(), serviceAccount == null ? null : serviceAccount.trim())) {
            log4j.warn("GooglePlayRtdnServlet: unauthorized push from " + request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        PlayEntitlementService service = (PlayEntitlementService) ctx.getAttribute(PlayEntitlementService.class.getName());
        if (service == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        String body;
        try (InputStream in = request.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                log4j.warn("GooglePlayRtdnServlet: body too large, ignored");
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        }
        String packageName = ctx.getInitParameter("googlePlayPackageName");
        // a RuntimeException from the dispatcher is deliberately not caught: the container answers 500 and Pub/Sub retries
        response.setStatus(new RtdnDispatcher(service, packageName).dispatch(body));
    }
}
