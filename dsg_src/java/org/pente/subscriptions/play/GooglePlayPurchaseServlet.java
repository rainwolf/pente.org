package org.pente.subscriptions.play;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.log4j.Category;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.server.Resources;

/**
 * /gameServer/googlePlayPurchase (spec 2026-10-11 §4.4). In SimpleLoginAccessController.loginPages;
 * the player is LoginFilter's "name" request attribute, never a request parameter.
 * GET: store-page JSON. POST productId, purchaseToken: sync, reply with a PlayClassification reply string.
 */
public class GooglePlayPurchaseServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(GooglePlayPurchaseServlet.class.getName());

    /** LoginFilter sets "guest" for crawler user agents; that is never a buyer */
    public static boolean acceptsPlayerName(String name) {
        return name != null && !name.trim().isEmpty() && !"guest".equalsIgnoreCase(name.trim());
    }

    public static final String UNKNOWN_PRODUCT = "<unknown product>";

    /** the client's productId for the billing log: a whitelisted id, else a fixed placeholder (no forged lines) */
    public static String loggableProductId(String productId) {
        if (PlayProducts.isSubscription(productId) || PlayProducts.isOneTime(productId)) {
            return productId;
        }
        return UNKNOWN_PRODUCT;
    }

    private PlayEntitlementService service() {
        return (PlayEntitlementService) getServletContext().getAttribute(PlayEntitlementService.class.getName());
    }

    /** the logged-in player's pid, or null when there is no valid non-guest login */
    private Long loggedInPid(HttpServletRequest request) throws DSGPlayerStoreException {
        String name = (String) request.getAttribute("name");
        if (!acceptsPlayerName(name)) {
            return null;
        }
        ServletContext ctx = getServletContext();
        Resources resources = (Resources) ctx.getAttribute(Resources.class.getName());
        DSGPlayerData data = resources.getDsgPlayerStorer().loadPlayer(name);
        if (data == null || data.isGuest()) {
            return null;
        }
        return Long.valueOf(data.getPlayerID());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        PlayEntitlementService service = service();
        if (service == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        try {
            Long pid = loggedInPid(request);
            if (pid == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            String json = service.storeInfo(pid.longValue()).toJson();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(json);
        } catch (DSGPlayerStoreException e) {
            log4j.error("GooglePlayPurchaseServlet: GET failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (PlayStoreException e) {
            log4j.error("GooglePlayPurchaseServlet: GET failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        PlayEntitlementService service = service();
        Long pid;
        try {
            pid = loggedInPid(request);
        } catch (DSGPlayerStoreException e) {
            log4j.error("GooglePlayPurchaseServlet: loading the player failed", e);
            pid = null;
        }
        if (pid == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        if (service == null) {
            out.println(PlayClassification.RETRY_REPLY);
            return;
        }
        String productId = request.getParameter("productId");
        String purchaseToken = request.getParameter("purchaseToken");
        if (productId == null || productId.isEmpty() || purchaseToken == null || purchaseToken.isEmpty()) {
            out.println(PlayClassification.INVALID.getReply());
            return;
        }
        try {
            PlayClassification result = service.syncFromClient(productId, purchaseToken, pid.longValue());
            log4j.info("GooglePlayPurchaseServlet: " + loggableProductId(productId) + " for pid " + pid + " -> " + result);
            out.println(result.getReply());
        } catch (PlayApiException e) {
            log4j.warn("GooglePlayPurchaseServlet: Google API error, client retries: " + e);
            out.println(PlayClassification.RETRY_REPLY);
        } catch (PlayStoreException e) {
            log4j.warn("GooglePlayPurchaseServlet: DB error, client retries: " + e);
            out.println(PlayClassification.RETRY_REPLY);
        }
    }
}
