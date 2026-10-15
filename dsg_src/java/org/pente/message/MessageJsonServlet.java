package org.pente.message;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Category;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.Resources;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * One private message for mobile apps: /gameServer/messageJson?mid=N (in SimpleLoginAccessController.loginPages).
 * The player is LoginFilter's "name" request attribute (name + password request params), never a
 * separate parameter. GET and POST behave the same.
 * <p>
 * Same rules as DSGMessageServlet command=view: the message must be addressed to the player, it is
 * marked read, and the message it replied to is included only when the player wrote it to this
 * message's sender.
 * <p>
 * 200: {"success":true, "message":{...}, "replyTo":{...} or null}, each message
 * {"mid", "replyToMid" (0 = none), "from", "to", "subject", "body" (raw stored text), "date" (epoch ms), "read"}.
 * Errors: {"success":false, "error":string} with 400 (bad mid), 401, 404 (missing or not the player's) or 500.
 */
public class MessageJsonServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(MessageJsonServlet.class.getName());

    private static final String NOT_FOUND = "Message not found";

    private DSGPlayerStorer dsgPlayerStorer;
    private DSGMessageStorer dsgMessageStorer;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    private static class MessageEntry {
        int mid;
        int replyToMid;
        String from;
        String to;
        String subject;
        String body;
        long date;
    }

    /** the viewer's own received message; replyTo has no read flag (it would be a read receipt for the other player) */
    private static class ReceivedMessageEntry extends MessageEntry {
        boolean read;
    }

    private static class MessageReply {
        boolean success;
        ReceivedMessageEntry message;
        MessageEntry replyTo;
    }

    private static class ErrorReply {
        boolean success;
        String error;
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            Resources resources = (Resources) getServletContext().getAttribute(Resources.class.getName());
            dsgPlayerStorer = resources.getDsgPlayerStorer();
            dsgMessageStorer = resources.getDsgMessageStorer();
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    /** message id from the "mid" parameter, or -1 when missing or not a positive integer */
    public static int parseMid(String midStr) {
        if (midStr == null) {
            return -1;
        }
        try {
            int mid = Integer.parseInt(midStr.trim());
            return mid > 0 ? mid : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** the player may read m only when it was sent to them */
    public static boolean canView(DSGMessage m, long viewerPid) {
        return m != null && m.getToPid() == viewerPid;
    }

    /**
     * parent (the message m replied to) may be shown only when the viewer wrote it
     * to m's sender, so a reply can never reveal someone else's message.
     */
    public static boolean canViewReplyTo(DSGMessage m, DSGMessage parent, long viewerPid) {
        return m != null && parent != null &&
                m.getReplyToMid() > 0 &&
                parent.getMid() == m.getReplyToMid() &&
                parent.getFromPid() == viewerPid &&
                parent.getToPid() == m.getFromPid();
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            DSGPlayerData me = loggedInPlayer(request);
            if (me == null) {
                error(response, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
                return;
            }
            int mid = parseMid(request.getParameter("mid"));
            if (mid == -1) {
                error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid message id");
                return;
            }

            // getMessage(mid) only returns viewable (not deleted) messages
            DSGMessage m = dsgMessageStorer.getMessage(mid);
            if (!canView(m, me.getPlayerID())) {
                error(response, HttpServletResponse.SC_NOT_FOUND, NOT_FOUND);
                return;
            }

            DSGMessage parent = null;
            if (m.getReplyToMid() > 0) {
                // true: include messages the viewer deleted from their own box
                DSGMessage p = dsgMessageStorer.getMessage(m.getReplyToMid(), true);
                if (canViewReplyTo(m, p, me.getPlayerID())) {
                    parent = p;
                }
            }

            // build everything before readMessage so a failure never marks the message read
            long senderPid = m.getFromPid();
            DSGPlayerData sender = dsgPlayerStorer.loadPlayer(senderPid);
            if (sender == null) {
                log4j.error("MessageJsonServlet: sender player not found, mid=" + mid + " pid=" + senderPid);
                error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
                return;
            }

            MessageReply reply = new MessageReply();
            reply.success = true;
            ReceivedMessageEntry received = new ReceivedMessageEntry();
            fill(received, m, sender.getName(), me.getName());
            received.read = m.isRead();
            reply.message = received;
            if (parent != null) {
                // canViewReplyTo: the viewer wrote parent to m's sender
                MessageEntry replyTo = new MessageEntry();
                fill(replyTo, parent, me.getName(), sender.getName());
                reply.replyTo = replyTo;
            }

            if (!m.isRead()) {
                dsgMessageStorer.readMessage(m);
            }
            write(response, HttpServletResponse.SC_OK, gson.toJson(reply));

        } catch (DSGMessageStoreException e) {
            log4j.error("MessageJsonServlet: message load failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        } catch (DSGPlayerStoreException e) {
            log4j.error("MessageJsonServlet: player load failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private static void fill(MessageEntry e, DSGMessage m, String from, String to) {
        e.mid = m.getMid();
        e.replyToMid = m.getReplyToMid();
        e.from = from;
        e.to = to;
        e.subject = m.getSubject();
        e.body = m.getBody();
        e.date = m.getCreationDate().getTime();
    }

    private DSGPlayerData loggedInPlayer(HttpServletRequest request) throws DSGPlayerStoreException {
        String name = (String) request.getAttribute("name");
        if (name == null || name.trim().isEmpty() || "guest".equalsIgnoreCase(name.trim())) {
            return null;
        }
        DSGPlayerData data = dsgPlayerStorer.loadPlayer(name);
        if (data == null || data.isGuest()) {
            return null;
        }
        return data;
    }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        ErrorReply reply = new ErrorReply();
        reply.success = false;
        reply.error = message;
        write(response, status, gson.toJson(reply));
    }

    private void write(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(json);
    }
}
