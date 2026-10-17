package org.pente.subscriptions.apple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.log4j.Category;

/**
 * /gameServer/appStoreNotifications: App Store Server Notifications V2 (spec 2026-10-15 §4.6). Public path (not in
 * loginPages): authenticity comes from Apple's signature. 200 handled, 401 not Apple's, 500 Apple retries.
 */
public class AppStoreNotificationServlet extends HttpServlet {

    private static final long serialVersionUID = 1323893510437669562L;

    private static final Category log4j = Category.getInstance(AppStoreNotificationServlet.class.getName());

    /** a V2 notification is tens of KB at most; anything bigger is not Apple's */
    static final int MAX_BODY_BYTES = 256 * 1024;

    /** the HTTP status for a POST body (null when empty; an oversize body is refused too) */
    public static int status(AppleBilling billing, byte[] body) {
        if (body == null || body.length > MAX_BODY_BYTES) {
            log4j.warn(body == null ? "AppStoreNotificationServlet: empty body, refused"
                    : "AppStoreNotificationServlet: body too large, refused");
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (billing == null) {
            log4j.warn("AppStoreNotificationServlet: App Store billing not wired, Apple retries");
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        return billing.notifications.handle(new String(body, StandardCharsets.UTF_8));
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        AppleBilling billing = (AppleBilling) getServletContext().getAttribute(AppleBilling.class.getName());
        response.setStatus(status(billing, body));
    }
}
