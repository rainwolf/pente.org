package org.pente.subscriptions.apple;

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
import org.pente.subscriptions.play.GooglePlayPurchaseServlet;
import org.pente.subscriptions.play.PlayClassification;
import org.pente.subscriptions.play.PlayStoreException;

/**
 * /gameServer/appStorePurchase (spec 2026-10-15 §4.4). In SimpleLoginAccessController.loginPages; the player is
 * LoginFilter's "name" request attribute, never a request parameter.
 * GET: store-page JSON. POST signedTransaction=&lt;jwsRepresentation&gt;: one PlayClassification reply line.
 */
public class AppStorePurchaseServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(AppStorePurchaseServlet.class.getName());

    /** a StoreKit 2 jwsRepresentation is a few KB; anything far bigger is not one */
    static final int MAX_SIGNED_TRANSACTION_CHARS = 64 * 1024;

    /** the reply line for a POST: a PlayClassification reply, or "retry later" on any transient failure */
    public static String reply(AppleEntitlementService service, String signedTransaction, long pid) {
        if (service == null) {
            return PlayClassification.RETRY_REPLY;
        }
        if (signedTransaction == null || signedTransaction.trim().isEmpty()
                || signedTransaction.length() > MAX_SIGNED_TRANSACTION_CHARS) {
            return PlayClassification.INVALID.getReply();
        }
        try {
            PlayClassification result = service.purchase(signedTransaction.trim(), pid);
            log4j.info("AppStorePurchaseServlet: pid " + pid + " -> " + result);
            return result.getReply();
        } catch (AppleApiException e) {
            log4j.warn("AppStorePurchaseServlet: Apple unavailable, client retries: " + e);
            return PlayClassification.RETRY_REPLY;
        } catch (PlayStoreException e) {
            log4j.warn("AppStorePurchaseServlet: DB error, client retries: " + e);
            return PlayClassification.RETRY_REPLY;
        }
    }

    private AppleEntitlementService service() {
        AppleBilling billing = (AppleBilling) getServletContext().getAttribute(AppleBilling.class.getName());
        return billing == null ? null : billing.service;
    }

    /** the logged-in player's pid, or null when there is no valid non-guest login */
    private Long loggedInPid(HttpServletRequest request) throws DSGPlayerStoreException {
        String name = (String) request.getAttribute("name");
        if (!GooglePlayPurchaseServlet.acceptsPlayerName(name)) {
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
        AppleEntitlementService service = service();
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
            log4j.error("AppStorePurchaseServlet: GET failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (PlayStoreException e) {
            log4j.error("AppStorePurchaseServlet: GET failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Long pid;
        try {
            pid = loggedInPid(request);
        } catch (DSGPlayerStoreException e) {
            log4j.error("AppStorePurchaseServlet: loading the player failed", e);
            pid = null;
        }
        if (pid == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(reply(service(), request.getParameter("signedTransaction"), pid.longValue()));
    }
}
