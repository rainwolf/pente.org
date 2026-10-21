package org.pente.gameServer.kids;

import com.google.gson.Gson;
import org.apache.log4j.Category;
import org.pente.gameServer.client.web.ForumUserSetup;
import org.pente.gameServer.core.*;
import org.pente.gameServer.mobile.MobileJsonHelper;
import org.pente.gameServer.server.PasswordHelper;
import org.pente.gameServer.server.Resources;
import org.pente.gameServer.server.SendMail2;
import org.pente.turnBased.TBGameStorer;
import org.pente.turnBased.TBStoreException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Kid account management for the apps and the website: /gameServer/kids.
 * The caller is LoginFilter's "name" request attribute (name2/password2 params or the website
 * session). Not in SimpleLoginAccessController.loginPages, so a missing login gets 401 JSON here
 * instead of the HTML login page.
 * <p>
 * GET: the caller's view. POST action=create|convert|password|whitelistAdd|whitelistRemove|
 * settings|release; every success returns the GET body (plus "message" for convert).
 * Errors are {"success":false,"error":...} with the strings in {@link KidAccountRules}.
 */
public class KidsServlet extends HttpServlet {
    private static final long serialVersionUID = 4316220593418765012L;

    private static final Category log4j =
            Category.getInstance(KidsServlet.class.getName());

    private transient CacheDSGPlayerStorer players;
    private transient CacheKidStorer kidStorer;
    private transient TBGameStorer tbGameStorer;
    private transient RegisterHandler registerHandler;
    private transient PasswordHelper passwordHelper;
    private transient MySQLDSGReturnEmailStorer returnEmailStorer;
    private transient UsernameFilter usernameFilter;
    private boolean emailEnabled;
    private final transient Gson gson = new Gson();

    private static class PlayerEntry {
        long pid;
        String name;
        int nameColor;
        int tourneyWinner;
    }

    private static class KidEntry {
        long kidPid;
        String name;
        boolean copyMessages;
        boolean copyLiveChat;
        boolean copyTbMessages;
        List<PlayerEntry> whitelist;
    }

    private static class ActiveGamesReply {
        boolean success;
        long kidPid;
        List<KidActiveGames.Entry> games;
    }

    private static class KidsReply {
        boolean success;
        String error;
        Boolean isKid;
        Boolean emailVerified;
        String parentName;
        List<PlayerEntry> whitelist;
        List<KidEntry> kids;
        String message;
    }

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            ServletContext ctx = config.getServletContext();
            Resources resources = (Resources) ctx.getAttribute(Resources.class.getName());
            players = resources.getDsgPlayerStorer();
            kidStorer = resources.getKidStorer();
            tbGameStorer = resources.getTbGameStorer();
            registerHandler = new DSGPlayerStorerRegisterHandler(players);
            passwordHelper = (PasswordHelper) ctx.getAttribute(PasswordHelper.class.getName());
            returnEmailStorer = (MySQLDSGReturnEmailStorer)
                    ctx.getAttribute(MySQLDSGReturnEmailStorer.class.getName());
            emailEnabled = ((Boolean) ctx.getAttribute("emailEnabled")).booleanValue();
        } catch (Throwable t) {
            log4j.error("Problem in init()", t);
        }
        // fails open, like RegisterServlet: the reserved fragments still apply
        try {
            usernameFilter = UsernameFilter.loadShipped();
        } catch (Throwable t) {
            log4j.error("Kids: username filter not loaded", t);
        }
    }

    /**
     * True when the host of origin (or, when origin is absent, of referer) equals serverName.
     * Neither header, or an unparseable one, is a fail.
     */
    public static boolean sameOrigin(String origin, String referer, String serverName) {
        String source = origin != null && !origin.trim().isEmpty() ? origin : referer;
        if (source == null || source.trim().isEmpty() || serverName == null) {
            return false;
        }
        try {
            String host = new URI(source.trim()).getHost();
            return host != null && host.equalsIgnoreCase(serverName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True only when the name2/password2 request params really are the logged-in player's
     * credentials: name2 (trimmed, case-insensitive) equals loggedInName and the encrypted
     * password2 equals the stored encrypted password. LoginFilter ignores these params when a
     * session exists, so their mere presence must not skip the origin check (CSRF).
     */
    public static boolean paramsAuthenticate(String name2, String encryptedPassword2,
                                             String loggedInName, String storedPassword) {
        if (name2 == null || loggedInName == null || encryptedPassword2 == null
                || storedPassword == null || encryptedPassword2.isEmpty()) {
            return false;
        }
        return name2.trim().equalsIgnoreCase(loggedInName.trim())
                && encryptedPassword2.equals(storedPassword);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            DSGPlayerData me = loggedInPlayer(request);
            if (me == null) {
                error(response, HttpServletResponse.SC_UNAUTHORIZED, KidAccountRules.LOGIN_REQUIRED);
                return;
            }
            if ("activeGames".equals(request.getParameter("action"))) {
                activeGames(request, response, me);
                return;
            }
            success(response, me, null);
        } catch (TBStoreException e) {
            log4j.error("Kids: GET activeGames failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, KidAccountRules.DATABASE);
        } catch (DSGPlayerStoreException e) {
            log4j.error("Kids: GET failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, KidAccountRules.DATABASE);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            DSGPlayerData me = loggedInPlayer(request);
            if (me == null) {
                error(response, HttpServletResponse.SC_UNAUTHORIZED, KidAccountRules.LOGIN_REQUIRED);
                return;
            }
            String password2 = request.getParameter("password2");
            String encryptedPassword2 = password2 == null || password2.trim().isEmpty()
                    ? null : passwordHelper.encrypt(password2.trim());
            boolean appAuth = paramsAuthenticate(request.getParameter("name2"),
                    encryptedPassword2, me.getName(), me.getPassword());
            if (!appAuth && !sameOrigin(request.getHeader("Origin"),
                    request.getHeader("Referer"), request.getServerName())) {
                error(response, HttpServletResponse.SC_FORBIDDEN, KidAccountRules.BAD_ORIGIN);
                return;
            }
            String action = request.getParameter("action");
            String parentError = KidAccountRules.parentError(me,
                    "create".equals(action) || "convert".equals(action));
            if (parentError != null) {
                error(response, HttpServletResponse.SC_FORBIDDEN, parentError);
                return;
            }
            if ("create".equals(action)) {
                create(request, response, me);
            } else if ("convert".equals(action)) {
                convert(request, response, me);
            } else if ("password".equals(action)) {
                password(request, response, me);
            } else if ("whitelistAdd".equals(action)) {
                whitelistAdd(request, response, me);
            } else if ("whitelistRemove".equals(action)) {
                whitelistRemove(request, response, me);
            } else if ("settings".equals(action)) {
                settings(request, response, me);
            } else if ("release".equals(action)) {
                release(request, response, me);
            } else {
                error(response, HttpServletResponse.SC_BAD_REQUEST, KidAccountRules.UNKNOWN_ACTION);
            }
        } catch (DSGPlayerStoreException e) {
            log4j.error("Kids: POST failed", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, KidAccountRules.DATABASE);
        }
    }

    private void activeGames(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, TBStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        ActiveGamesReply reply = new ActiveGamesReply();
        reply.success = true;
        reply.kidPid = kid.getPlayerID();
        reply.games = KidActiveGames.build(kid.getPlayerID(),
                tbGameStorer.loadSets(kid.getPlayerID()), players);
        logAction("activeGames", me, kid.getPlayerID());
        write(response, HttpServletResponse.SC_OK, reply);
    }

    private void create(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        String name = request.getParameter("kidName");
        if (name != null) {
            name = name.trim().toLowerCase();
        }
        String pw = request.getParameter("kidPassword");
        if (pw != null) {
            pw = pw.trim();
        }
        if (name != null && ReservedNames.isReserved(name, usernameFilter)) {
            error(response, HttpServletResponse.SC_CONFLICT, KidAccountRules.nameTaken(name));
            return;
        }
        if (!registerHandler.isValidRegistration(name, pw, me.getEmail(), false)) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, KidAccountRules.INVALID_DATA);
            return;
        }
        if (players.loadPlayer(name) != null) {
            error(response, HttpServletResponse.SC_CONFLICT, KidAccountRules.nameTaken(name));
            return;
        }
        // one row carries the kid flag and the restrictions: never an unrestricted window
        SimpleDSGPlayerData kid = KidAccountRules.newKidData(me, name, passwordHelper.encrypt(pw));
        players.insertPlayer(kid);
        ForumUserSetup.initNewUser(name, false);
        if (emailEnabled) {
            mail(me.getPlayerID(), me.getName(), me.getEmail(),
                    KidAccountEmails.createdSubject(),
                    KidAccountEmails.createdBody(me.getName(), name, pw));
        }
        logAction("create", me, kid.getPlayerID());
        success(response, me, null);
    }

    private void convert(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        String kidName = request.getParameter("kidName");
        DSGPlayerData target = kidName == null || kidName.trim().isEmpty()
                ? null : players.loadPlayer(kidName.trim());
        boolean hasKids = target != null
                && !kidStorer.getKidPids(target.getPlayerID()).isEmpty();
        String err = KidAccountRules.conversionError(me, target, hasKids);
        if (KidAccountRules.PLAYER_NOT_FOUND.equals(err)) {
            error(response, HttpServletResponse.SC_NOT_FOUND, err);
            return;
        }
        if (KidAccountRules.CANT_ADD_SELF.equals(err)) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, err);
            return;
        }
        String message = "Confirmation email sent to " + target.getName();
        // kid, has-kids and admin targets get the neutral success: kid status stays private
        if (KidAccountRules.conversionSilentlyRefused(target, hasKids)) {
            logAction("convert", me, target.getPlayerID());
            success(response, me, message);
            return;
        }
        if (err != null) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, err);
            return;
        }
        String code = KidLinkRequest.newCode();
        kidStorer.storeLinkRequest(new KidLinkRequest(code, me.getPlayerID(),
                target.getPlayerID(), new Date()));
        if (emailEnabled) {
            mail(target.getPlayerID(), target.getName(), target.getEmail(),
                    KidAccountEmails.conversionSubject(),
                    KidAccountEmails.conversionBody(target.getName(), me.getName(), code));
        }
        logAction("convert", me, target.getPlayerID());
        success(response, me, message);
    }

    private void password(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        String pw = request.getParameter("kidPassword");
        if (pw != null) {
            pw = pw.trim();
        }
        if (!KidAccountRules.passwordValid(pw)) {
            error(response, HttpServletResponse.SC_BAD_REQUEST, KidAccountRules.INVALID_PASSWORD);
            return;
        }
        kid.setPassword(passwordHelper.encrypt(pw));
        // safe on a cached copy: parent_pid is not in the generic UPDATE
        players.updatePlayer(kid);
        logAction("password", me, kid.getPlayerID());
        success(response, me, null);
    }

    private void whitelistAdd(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        String playerName = request.getParameter("playerName");
        DSGPlayerData target = playerName == null || playerName.trim().isEmpty()
                ? null : players.loadPlayer(playerName.trim());
        String err = KidAccountRules.whitelistAddError(kid, target);
        if (err != null) {
            int status = KidAccountRules.PLAYER_NOT_FOUND.equals(err)
                    ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_BAD_REQUEST;
            error(response, status, err);
            return;
        }
        kidStorer.addToWhitelist(kid.getPlayerID(), target.getPlayerID());
        logAction("whitelistAdd", me, kid.getPlayerID());
        success(response, me, null);
    }

    private void whitelistRemove(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        Long playerPid = parseLong(request.getParameter("playerPid"));
        if (playerPid == null || !kidStorer.getWhitelist(kid.getPlayerID()).contains(playerPid)) {
            error(response, HttpServletResponse.SC_NOT_FOUND, KidAccountRules.NOT_APPROVED);
            return;
        }
        kidStorer.removeFromWhitelist(kid.getPlayerID(), playerPid.longValue());
        logAction("whitelistRemove", me, kid.getPlayerID());
        success(response, me, null);
    }

    private void settings(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        KidSettings current = kidStorer.getSettings(kid.getPlayerID());
        boolean copyMessages = request.getParameter("copyMessages") == null
                ? current.isCopyMessages() : flag(request, "copyMessages");
        boolean copyLiveChat = request.getParameter("copyLiveChat") == null
                ? current.isCopyLiveChat() : flag(request, "copyLiveChat");
        boolean copyTbMessages = request.getParameter("copyTbMessages") == null
                ? current.isCopyTbMessages() : flag(request, "copyTbMessages");
        kidStorer.storeSettings(new KidSettings(kid.getPlayerID(), copyMessages, copyLiveChat,
                copyTbMessages));
        logAction("settings", me, kid.getPlayerID());
        success(response, me, null);
    }

    private void release(HttpServletRequest request, HttpServletResponse response, DSGPlayerData me)
            throws DSGPlayerStoreException, IOException {
        DSGPlayerData kid = ownedKid(request, response, me);
        if (kid == null) {
            return;
        }
        kidStorer.setParentPid(kid.getPlayerID(), null);
        players.refreshPlayer(kid.getName());
        // after the refresh: its notify makes live sessions reload the player as not a kid
        kidStorer.deleteKidData(kid.getPlayerID());
        logAction("release", me, kid.getPlayerID());
        success(response, me, null);
    }

    /** the caller's kid for the kidPid parameter; null after writing the 404 reply */
    private DSGPlayerData ownedKid(HttpServletRequest request, HttpServletResponse response,
                                   DSGPlayerData me) throws DSGPlayerStoreException, IOException {
        Long kidPid = parseLong(request.getParameter("kidPid"));
        DSGPlayerData kid = kidPid == null ? null : players.loadPlayer(kidPid.longValue());
        if (!KidAccountRules.ownsKid(me, kid)) {
            error(response, HttpServletResponse.SC_NOT_FOUND, KidAccountRules.KID_NOT_FOUND);
            return null;
        }
        return kid;
    }

    private static Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** the logged-in non-guest player, or null (LoginFilter sets "guest" for crawlers) */
    private DSGPlayerData loggedInPlayer(HttpServletRequest request) throws DSGPlayerStoreException {
        String name = (String) request.getAttribute("name");
        if (name == null || name.trim().isEmpty() || "guest".equalsIgnoreCase(name.trim())) {
            return null;
        }
        DSGPlayerData data = players.loadPlayer(name);
        if (data == null || data.isGuest()) {
            return null;
        }
        return data;
    }

    private static boolean flag(HttpServletRequest request, String param) {
        String v = request.getParameter(param);
        return "Y".equals(v) || "true".equalsIgnoreCase(v);
    }

    private void logAction(String action, DSGPlayerData me, long kidPid) {
        log4j.info("Kids: " + action + " by " + me.getName() + " kid=" + kidPid);
    }

    private void mail(long toPid, String toName, String toEmail, String subject, String body) {
        try {
            SendMail2.sendMailSaveInDb("Pente.org", System.getProperty("mail.smtp.user"),
                    toPid, toName, toEmail, subject, body, false, null, returnEmailStorer);
        } catch (Throwable t) {
            log4j.error("Kids: mail to " + toName + " failed", t);
        }
    }

    /** whitelist entries sorted by name, without the kid's own parent and missing players */
    private List<PlayerEntry> whitelist(DSGPlayerData kid) throws DSGPlayerStoreException {
        Set<Long> pids = kidStorer.getWhitelist(kid.getPlayerID());
        List<PlayerEntry> list = new ArrayList<PlayerEntry>();
        for (Long pid : pids) {
            if (kid.getParentPid() != null && pid.longValue() == kid.getParentPid().longValue()) {
                continue;
            }
            DSGPlayerData d = players.loadPlayer(pid.longValue());
            if (d == null) {
                continue;
            }
            PlayerEntry e = new PlayerEntry();
            e.pid = d.getPlayerID();
            e.name = d.getName();
            e.nameColor = MobileJsonHelper.playerColorNonZero(d);
            e.tourneyWinner = d.getTourneyWinner();
            list.add(e);
        }
        Collections.sort(list, new Comparator<PlayerEntry>() {
            public int compare(PlayerEntry a, PlayerEntry b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return list;
    }

    private void success(HttpServletResponse response, DSGPlayerData me, String message)
            throws DSGPlayerStoreException, IOException {
        KidsReply reply = new KidsReply();
        reply.success = true;
        reply.emailVerified = Boolean.valueOf(me.getEmailValid());
        reply.kids = new ArrayList<KidEntry>();
        reply.message = message;
        if (me.getParentPid() != null) {
            reply.isKid = Boolean.TRUE;
            DSGPlayerData parent = players.loadPlayer(me.getParentPid().longValue());
            reply.parentName = parent == null ? null : parent.getName();
            reply.whitelist = whitelist(me);
        } else {
            reply.isKid = Boolean.FALSE;
            for (Long kidPid : kidStorer.getKidPids(me.getPlayerID())) {
                DSGPlayerData kid = players.loadPlayer(kidPid.longValue());
                if (kid == null) {
                    continue;
                }
                KidSettings s = kidStorer.getSettings(kid.getPlayerID());
                KidEntry e = new KidEntry();
                e.kidPid = kid.getPlayerID();
                e.name = kid.getName();
                e.copyMessages = s.isCopyMessages();
                e.copyLiveChat = s.isCopyLiveChat();
                e.copyTbMessages = s.isCopyTbMessages();
                e.whitelist = whitelist(kid);
                reply.kids.add(e);
            }
        }
        write(response, HttpServletResponse.SC_OK, reply);
    }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        KidsReply reply = new KidsReply();
        reply.success = false;
        reply.error = message;
        write(response, status, reply);
    }

    private void write(HttpServletResponse response, int status, Object reply) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(gson.toJson(reply));
    }
}
