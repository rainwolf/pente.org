package org.pente.subscriptions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.*;

import org.apache.log4j.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.database.*;
import org.pente.message.*;
import org.pente.notifications.NotificationServer;

import org.json.JSONObject;

public class IOSReceiptServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(IOSReceiptServlet.class.getName());

    /** MySQL/MariaDB ER_DUP_ENTRY */
    private static final int ER_DUP_ENTRY = 1062;

    /** paymentdate is stored as latest expires_date - 364 days (see getStartDate), so expiry = paymentdate + this */
    public static final long PAYMENTDATE_OFFSET_MS = 364L * 24 * 3600 * 1000;

    /** a previous iOS subscription that ended up to this long ago still counts as current (billing retry, late re-POST) */
    public static final long RENEWAL_GRACE_MS = 30L * 24 * 3600 * 1000;

    /**
     * How a valid receipt POST relates to what is already recorded. Every reply
     * contains "success" so shipped clients (which test containsString:@"success")
     * keep clearing their pending receipt.
     */
    public enum ReceiptClass {
        /** genuinely new purchase: thank-you sent */
        NEW("success"),
        /** renewal/extension recorded, no thank-you */
        RENEWAL("success:renewal"),
        /** this transaction was already recorded for this player (replay/restore) */
        KNOWN("success:known"),
        /** the subscription chain (or its latest transaction) is already recorded under a different player (shared Apple ID) */
        SHARED("success:shared");

        private final String reply;

        ReceiptClass(String reply) {
            this.reply = reply;
        }

        public String getReply() {
            return reply;
        }
    }

    /** Who already has dsg_subscribers rows for the transactions of a receipt's subscription chain. */
    public enum ChainOwnership {
        /** no transaction of the chain is recorded yet */
        UNOWNED,
        /** only the posting player owns chain transactions */
        MINE,
        /** the posting player owns chain transactions, but other players own some too */
        MINE_AND_OTHERS,
        /** only other players own chain transactions: the subscription belongs to someone else */
        OTHERS
    }

    /**
     * Pure ownership decision for a subscription chain, shared by this servlet and
     * CacheDSGPlayerStorer's CheckiOSSubscribersRunnable.
     *
     * @param pid        the player posting (or whose stored receipt is re-validated)
     * @param ownerPids  pids of the dsg_subscribers rows whose transactionid is in the chain
     */
    public static ChainOwnership chainOwnership(long pid, Collection<Long> ownerPids) {
        boolean mine = false;
        boolean others = false;
        for (Long ownerPid : ownerPids) {
            if (ownerPid.longValue() == pid) {
                mine = true;
            } else {
                others = true;
            }
        }
        if (mine) {
            return others ? ChainOwnership.MINE_AND_OTHERS : ChainOwnership.MINE;
        }
        return others ? ChainOwnership.OTHERS : ChainOwnership.UNOWNED;
    }

    /**
     * All transaction ids (transaction_id and original_transaction_id) of 1YRNOADSORLIMITS entries
     * in a verifyReceipt response: receipt.in_app plus latest_receipt_info (or
     * latest_expired_receipt_info), i.e. the whole subscription chain.
     */
    public static Set<String> chainTransactionIds(JSONObject json) {
        Set<String> ids = new LinkedHashSet<String>();
        JSONObject receipt = json.optJSONObject("receipt");
        if (receipt != null) {
            addChainTransactionIds(receipt.optJSONArray("in_app"), ids);
        }
        if (json.has("latest_receipt_info")) {
            addChainTransactionIds(json.optJSONArray("latest_receipt_info"), ids);
        } else if (json.has("latest_expired_receipt_info")) {
            addChainTransactionIds(json.optJSONArray("latest_expired_receipt_info"), ids);
        }
        return ids;
    }

    private static void addChainTransactionIds(JSONArray entries, Set<String> ids) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.length(); i++) {
            JSONObject jsn = entries.optJSONObject(i);
            if (jsn == null || !"1YRNOADSORLIMITS".equals(jsn.optString("product_id", null))) {
                continue;
            }
            String transactionId = jsn.optString("transaction_id", "");
            if (!transactionId.isEmpty()) {
                ids.add(transactionId);
            }
            String originalTransactionId = jsn.optString("original_transaction_id", "");
            if (!originalTransactionId.isEmpty()) {
                ids.add(originalTransactionId);
            }
        }
    }

    /**
     * Reply when the receipt could not be checked right now (Apple/network/config
     * trouble). Contains neither "success" nor "invalid receipt", so shipped
     * clients keep their pending receipt and POST it again on next launch.
     */
    public static final String RETRY_REPLY = "retry later";

    /** Result of verifying a receipt with Apple. */
    public enum ReceiptOutcome {
        /** Apple accepted the receipt */
        VALID,
        /** the receipt itself is bad or expired: client may drop it */
        INVALID,
        /** could not be decided now (Apple down, network, our config): client must retry */
        TRANSIENT
    }

    /**
     * Pure mapping of a verifyReceipt status code to an outcome. 21007 (sandbox
     * receipt sent to production) is handled by checkReceipt before this mapping
     * (it retries against the sandbox), so its value here is never used there.
     * Unknown codes are TRANSIENT so a real purchase is never dropped by surprise.
     */
    public static ReceiptOutcome statusOutcome(int status) {
        switch (status) {
            case 0:
                return ReceiptOutcome.VALID;
            case 21000: // App Store could not read the JSON
            case 21002: // receipt data malformed
            case 21003: // receipt not authenticated
            case 21006: // valid but subscription expired
            case 21007: // sandbox receipt sent to production
            case 21008: // production receipt sent to sandbox
            case 21010: // account not found / deleted
                return ReceiptOutcome.INVALID;
            default:
                // 21004 shared secret mismatch (our config), 21005 server unavailable,
                // 21009 internal data access error, 21100-21199 internal data access
                // errors, and anything unknown
                return ReceiptOutcome.TRANSIENT;
        }
    }

    /**
     * Pure classification of a validated receipt.
     *
     * @param chainOwnership        who owns the receipt's subscription chain, checked before anything is written;
     *                              OTHERS means nothing was written and the receipt is SHARED
     * @param inserted              true if the plain INSERT INTO dsg_subscribers of transactionId succeeded
     * @param ownedBySubscriber     on a duplicate: true if the existing row belongs to the posting player
     * @param previousIosPaymentMs  paymentdate (epoch ms) of the player's dsg_subscribers_ios row as it was
     *                              when read by this request, or null if there was no row
     * @param receiptPaymentMs      paymentdate (epoch ms) this receipt itself records (ReceiptInfo.startMs)
     * @param nowMs                 current time (epoch ms)
     * @param transactionId         latest transaction_id from the receipt
     * @param originalTransactionId its original_transaction_id
     */
    public static ReceiptClass classify(ChainOwnership chainOwnership, boolean inserted, boolean ownedBySubscriber,
                                        Long previousIosPaymentMs, long receiptPaymentMs, long nowMs,
                                        String transactionId, String originalTransactionId) {
        if (chainOwnership == ChainOwnership.OTHERS) {
            return ReceiptClass.SHARED;
        }
        if (!inserted) {
            return ownedBySubscriber ? ReceiptClass.KNOWN : ReceiptClass.SHARED;
        }
        if (isRecentIosSubscription(previousIosPaymentMs, receiptPaymentMs, nowMs)
                && originalTransactionId != null && !originalTransactionId.equals(transactionId)) {
            return ReceiptClass.RENEWAL;
        }
        return ReceiptClass.NEW;
    }

    /**
     * true if the stored iOS row is a previous subscription (its paymentdate is strictly earlier than
     * this receipt's, so it was not written for this same receipt by a failed or concurrent request)
     * whose expiry (paymentdate + PAYMENTDATE_OFFSET_MS) is still current or no more than
     * RENEWAL_GRACE_MS ago.
     */
    private static boolean isRecentIosSubscription(Long previousIosPaymentMs, long receiptPaymentMs, long nowMs) {
        if (previousIosPaymentMs == null || previousIosPaymentMs.longValue() >= receiptPaymentMs) {
            return false;
        }
        long previousExpiryMs = previousIosPaymentMs + PAYMENTDATE_OFFSET_MS;
        return previousExpiryMs >= nowMs - RENEWAL_GRACE_MS;
    }

    /** Per-request result of receipt validation. */
    private static final class ReceiptInfo {
        final long startMs;
        final String transactionId;
        final String originalTransactionId;
        /** every transaction id of the subscription chain in this receipt, including transactionId */
        final Set<String> chainTransactionIds;

        ReceiptInfo(long startMs, String transactionId, String originalTransactionId, Set<String> chainTransactionIds) {
            this.startMs = startMs;
            this.transactionId = transactionId;
            this.originalTransactionId = originalTransactionId;
            this.chainTransactionIds = chainTransactionIds;
        }
    }

    /** Outcome of checkReceipt; info is non-null exactly when outcome is VALID. */
    private static final class ReceiptCheck {
        static final ReceiptCheck INVALID = new ReceiptCheck(ReceiptOutcome.INVALID, null);
        static final ReceiptCheck TRANSIENT = new ReceiptCheck(ReceiptOutcome.TRANSIENT, null);

        final ReceiptOutcome outcome;
        final ReceiptInfo info;

        private ReceiptCheck(ReceiptOutcome outcome, ReceiptInfo info) {
            this.outcome = outcome;
            this.info = info;
        }

        /** a status-0 response: VALID with info, or INVALID when no matching product was found */
        static ReceiptCheck of(ReceiptInfo info) {
            return info != null ? new ReceiptCheck(ReceiptOutcome.VALID, info) : INVALID;
        }
    }

    private static boolean isDuplicateKey(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException || e.getErrorCode() == ER_DUP_ENTRY;
    }

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {

        ServletContext ctx = getServletContext();
        String iOSSharedSecret = ctx.getInitParameter("iOSSharedSecret");
        Resources resources = (Resources) ctx.getAttribute(Resources.class.getName());
        CacheDSGPlayerStorer dsgPlayerStorer = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
        DBHandler dbHandler = null;
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        DSGPlayerData subscriberData = null;

        String username = request.getParameter("name");
        if (username == null) {
            log4j.info("IOSReceiptServlet: error: no username");
            return;
        }
        String player = (String) request.getAttribute("name");
        log4j.info("IOSReceiptServlet: username and logged in: " + username + " and " + player);
//        if (player != null && !username.toLowerCase().equals(player.toLowerCase())) {
//            log4j.info("IOSReceiptServlet: error: username and logged in mismatch: " +username+" and "+ player);
//            return;
//        }

        final String receiptDataStr = request.getParameter("receipt");
//        log4j.info("IOSReceiptServlet: receipt data received: " + receiptDataStr);
        if (receiptDataStr == null) {
            log4j.info("IOSReceiptServlet: error: no receipt data");
            return;
        }

        final ReceiptCheck receiptCheck = checkReceipt(receiptDataStr, iOSSharedSecret, true);
        final ReceiptInfo receiptInfo = receiptCheck.info;
        if (receiptCheck.outcome == ReceiptOutcome.TRANSIENT) {
            // no DB writes, no notifications: client keeps the receipt and POSTs it again
            log4j.error("IOSReceiptServlet: receipt for " + username + " could not be verified now, replying " + RETRY_REPLY);
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(RETRY_REPLY);
            return;
        } else if (receiptInfo == null) {
            log4j.info("IOSReceiptServlet: error: Receipt not valid");
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println("invalid receipt");
            return;
        } else if (receiptInfo.startMs + (364L * 24 * 3600 * 1000) < (new Date()).getTime()) {
            log4j.info("IOSReceiptServlet: error: Receipt too old");
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println("invalid receipt");
            return;
        }

        try {
            subscriberData = dsgPlayerStorer.loadPlayer(username.toLowerCase());
            if (subscriberData == null) {
                // neither "success" nor "invalid receipt": the client keeps the receipt and retries later
                log4j.info("IOSReceiptServlet: error: unknown player " + username);
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println("unknown player");
                return;
            }
            long subscriberPid = subscriberData.getPlayerID();
            String transactionId = receiptInfo.transactionId;
            Timestamp paymentDate = new Timestamp(receiptInfo.startMs);
            log4j.info("IOSReceiptServlet: dbHandler");
            dbHandler = resources.getDbHandler();
            con = dbHandler.getConnection();

            // did this player already have an iOS subscription before this request, and when?
            stmt = con.prepareStatement("SELECT paymentdate FROM dsg_subscribers_ios WHERE pid = ?");
            stmt.setLong(1, subscriberPid);
            rs = stmt.executeQuery();
            Long previousIosPaymentMs = null;
            if (rs.next()) {
                Timestamp previousIosPaymentDate = rs.getTimestamp(1);
                if (previousIosPaymentDate != null) {
                    previousIosPaymentMs = previousIosPaymentDate.getTime();
                }
            }
            rs.close();
            rs = null;
            stmt.close();

            // who already owns this receipt's subscription chain? Ownership is decided per chain, not per
            // latest transaction, so a second player on the same Apple ID cannot take over a renewal.
            List<Long> chainOwnerPids = new ArrayList<Long>();
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < receiptInfo.chainTransactionIds.size(); i++) {
                placeholders.append(i == 0 ? "?" : ", ?");
            }
            stmt = con.prepareStatement("SELECT pid FROM dsg_subscribers WHERE transactionid IN (" + placeholders + ")");
            int chainIdx = 1;
            for (String chainTransactionId : receiptInfo.chainTransactionIds) {
                stmt.setString(chainIdx++, chainTransactionId);
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                chainOwnerPids.add(rs.getLong(1));
            }
            rs.close();
            rs = null;
            stmt.close();
            ChainOwnership chainOwnership = chainOwnership(subscriberPid, chainOwnerPids);
            long ownerPid = subscriberPid;
            for (Long chainOwnerPid : chainOwnerPids) {
                if (chainOwnerPid.longValue() != subscriberPid) {
                    ownerPid = chainOwnerPid;
                    break;
                }
            }
            if (chainOwnership == ChainOwnership.MINE_AND_OTHERS) {
                log4j.warn("IOSReceiptServlet: subscription chain of transaction " + transactionId + " posted by " +
                        subscriberData.getName() + " is also registered to other pids " + chainOwnerPids + "; proceeding");
            }

            DSGPlayerData dsgPlayerData = subscriberData;
            boolean inserted = false;
            if (chainOwnership != ChainOwnership.OTHERS) {
                // idempotent upsert before the gate INSERT, so a failure here leaves no dsg_subscribers row.
                // A retry after a later failure (or a concurrent POST) may read the paymentdate written here;
                // classify() ignores a stored row that is not older than this receipt's paymentDate.
//                stmt = con.prepareStatement("INSERT INTO dsg_subscribers_ios (pid, paymentdate, receipt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate)");
                stmt = con.prepareStatement("INSERT INTO dsg_subscribers_ios (pid, paymentdate, receipt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate), receipt=VALUES(receipt)");
                stmt.setLong(1, subscriberPid);
                stmt.setTimestamp(2, paymentDate);
                stmt.setString(3, receiptDataStr);
                log4j.info("IOSReceiptServlet: before executeUpdate of ios upsert");
                stmt.executeUpdate();
                stmt.close();

                int subscriptionLvl = 0;
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEYEAR);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.UNLIMITEDTBGAMES);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.NOADS);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.DBACCESS);

                // gate: a plain insert, the transactionid primary key decides which request records it first
                log4j.info("IOSReceiptServlet: Before insert");
                stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount, verified) VALUES (?, ?, ?, ?, ?, ?)");
                stmt.setLong(1, subscriberPid);
                stmt.setInt(2, subscriptionLvl);
                stmt.setTimestamp(3, paymentDate);
                stmt.setString(4, transactionId);
                stmt.setDouble(5, 0);
                stmt.setInt(6, 1);
                log4j.info("IOSReceiptServlet: before executeUpdate of insert");
                try {
                    stmt.executeUpdate();
                    inserted = true;
                } catch (SQLException e) {
                    if (!isDuplicateKey(e)) {
                        throw e;
                    }
                    inserted = false;
                }
                stmt.close();

                ownerPid = subscriberPid;
                if (!inserted) {
                    stmt = con.prepareStatement("SELECT pid FROM dsg_subscribers WHERE transactionid = ?");
                    stmt.setString(1, transactionId);
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        ownerPid = rs.getLong(1);
                    }
                    rs.close();
                    rs = null;
                    stmt.close();
                }
            }

            ReceiptClass receiptClass = classify(chainOwnership, inserted, ownerPid == subscriberPid, previousIosPaymentMs,
                    receiptInfo.startMs, System.currentTimeMillis(), transactionId, receiptInfo.originalTransactionId);
            log4j.info("IOSReceiptServlet: " + subscriberData.getName() + " transaction " + transactionId +
                    " (original " + receiptInfo.originalTransactionId + ") classified " + receiptClass);

            if (dsgPlayerData.getNameColorRGB() == 0) {
                dsgPlayerData.setNameColorRGB(-16751616);
                dsgPlayerStorer.updatePlayer(dsgPlayerData);
            }

            DSGMessage message = null;
            if (receiptClass == ReceiptClass.NEW) {
                DSGMessageStorer dsgMessageStorer = resources.getDsgMessageStorer();
                message = new DSGMessage();
                message.setCreationDate(new java.util.Date());
                message.setFromPid(23000000016237L);
                message.setToPid(subscriberPid);
                message.setSubject("Subscription purchase successful");
                String msg = "Hi there,\n\n Thank you for subscribing to pente.org. Your contribution will help us endure and flourish for years to come.\n";
                message.setBody(msg + "\nHave oodles of fun here at pente.org.\n\nPS: if you have any questions, feel free to reply to this message.");
                dsgMessageStorer.createMessage(message);
            }
            dsgPlayerStorer.refreshPlayer(subscriberData.getName());

            NotificationServer notificationServer = resources.getNotificationServer();
            if (message != null) {
                notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());
            }

            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(receiptClass.getReply());

            switch (receiptClass) {
                case NEW:
                    log4j.info("IOSReceiptServlet: iOS subscription purchase successful for " + subscriberData.getName());
                    notificationServer.sendAdminNotification("iOS subscription for " + subscriberData.getName());
                    break;
                case RENEWAL:
                    log4j.info("IOSReceiptServlet: iOS subscription renewal recorded for " + subscriberData.getName());
                    notificationServer.sendAdminNotification(subscriberData.getName() + " extended iOS subscription");
                    break;
                case KNOWN:
                    log4j.info("IOSReceiptServlet: replay of known transaction " + transactionId + " for " + subscriberData.getName());
                    break;
                case SHARED:
                    log4j.info("IOSReceiptServlet: " + subscriberData.getName() + " posted transaction " + transactionId + " already registered to pid " + ownerPid);
                    notificationServer.sendAdminNotification(subscriberData.getName() + " posted an iOS receipt already registered to pid " + ownerPid);
                    break;
            }

        } catch (SQLException e) {
            log4j.info("IOSReceiptServlet SQLException " + e);
        } catch (DSGMessageStoreException e) {
            log4j.info("IOSReceiptServlet DSGMessageStoreException " + e);
        } catch (DSGPlayerStoreException e) {
            log4j.info("IOSReceiptServlet DSGPlayerStoreException " + e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            } catch (SQLException e) {
                log4j.info("IOSReceiptServlet SQLException " + e);
            }
        }
    }

    private ReceiptCheck checkReceipt(String receiptDataStr, String sharedSecret, boolean production) {
        String lines = "";
        // sandbox URL
        try {
            String SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt";
            // production URL
            String PRODUCTION_URL = "https://buy.itunes.apple.com/verifyReceipt";
            JSONObject obj = new JSONObject();
            obj.put("receipt-data", receiptDataStr);
            obj.put("password", sharedSecret);

            final URL url = new URI(production ? PRODUCTION_URL : SANDBOX_URL).toURL();
            final HttpURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            final OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(obj.toString());
            wr.flush();

            // obtain the response
            final BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                lines += line + "\n";
            }
            wr.close();
            rd.close();

            JSONObject json = new JSONObject(lines);

            // verify the response: something like {"status":21004} etc...
            int status = json.getInt("status");
            switch (status) {
                case 0:
                    return ReceiptCheck.of(getStartDate(json));
                case 21000:
                    log4j.info("IOSReceiptServlet: " + status + ": App store could not read");
                    break;
                case 21002:
                    log4j.info("IOSReceiptServlet: " + status + ": Data was malformed");
                    break;
                case 21003:
                    log4j.info("IOSReceiptServlet: " + status + ": Receipt not authenticated");
                    break;
                case 21004:
                    log4j.error("IOSReceiptServlet: " + status + ": Shared secret does not match");
                    break;
                case 21005:
                    log4j.error("IOSReceiptServlet: " + status + ": Receipt server unavailable");
                    break;
                case 21006:
                    log4j.info("IOSReceiptServlet: " + status + ": Receipt valid but sub expired");
                    break;
                case 21007:
                    log4j.info("IOSReceiptServlet: " + status + ": Sandbox receipt sent to Production environment");
                    return checkReceipt(receiptDataStr, sharedSecret, false);
                case 21008:
                    log4j.info("IOSReceiptServlet: " + status + ": Production receipt sent to Sandbox environment");
                    break;
                default:
                    // unknown error code (nevertheless a problem)
                    log4j.error("IOSReceiptServlet: " + "Unknown error: status code = " + status);
                    break;
            }
            return statusOutcome(status) == ReceiptOutcome.TRANSIENT ? ReceiptCheck.TRANSIENT : ReceiptCheck.INVALID;
        } catch (IOException e) {
            // I/O error (HTTP 5xx, timeout, ...): Apple or the network, not the receipt
            log4j.error("IOSReceiptServlet: I/O error during verification: " + e);
            e.printStackTrace();
            return ReceiptCheck.TRANSIENT;
        } catch (JSONException e) {
            log4j.error("IOSReceiptServlet: JSONException during verification: " + e);
            log4j.error("IOSReceiptServlet: received response: " + lines);
            e.printStackTrace();
            return ReceiptCheck.TRANSIENT;
        } catch (URISyntaxException e) {
            log4j.error("IOSReceiptServlet: URISyntaxException during verification: " + e);
            return ReceiptCheck.TRANSIENT;
        }
    }

    private ReceiptInfo getStartDate(JSONObject json) {
        try {
            long start_ms = 0;
            String transactionId = null;
            String originalTransactionId = null;

            JSONObject tmpJSON = json.getJSONObject("receipt");
            JSONArray jsonArray = tmpJSON.getJSONArray("in_app");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsn = jsonArray.getJSONObject(i);
                if ("1YRNOADSORLIMITS".equals(jsn.getString("product_id"))) {
                    long tmp_start_ms = jsn.getLong("purchase_date_ms");
                    if (tmp_start_ms > start_ms) {
                        start_ms = tmp_start_ms;
                        transactionId = jsn.getString("original_transaction_id");
                        originalTransactionId = transactionId;
                    }
                }
            }

            if (json.has("latest_receipt_info")) {
                jsonArray = json.getJSONArray("latest_receipt_info");
            } else if (json.has("latest_expired_receipt_info")) {
                jsonArray = json.getJSONArray("latest_expired_receipt_info");
            } else {
                log4j.info("IOSReceiptServlet: getStartDate: Returned data: " + json.toString());
                return null;
            }

            start_ms = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsn = jsonArray.getJSONObject(i);
                if ("1YRNOADSORLIMITS".equals(jsn.getString("product_id"))) {
                    long tmp_start_ms = jsn.getLong("expires_date_ms") - (364L * 24 * 3600 * 1000);
                    if (tmp_start_ms > start_ms) {
                        transactionId = jsn.getString("transaction_id");
                        originalTransactionId = jsn.optString("original_transaction_id", null);
                        start_ms = tmp_start_ms;
                    }
                }
            }
            if (transactionId != null) {
                Set<String> chainTransactionIds = chainTransactionIds(json);
                chainTransactionIds.add(transactionId);
                return new ReceiptInfo(start_ms, transactionId, originalTransactionId, chainTransactionIds);
            } else {
                log4j.info("IOSReceiptServlet: getStartDate: Returned data: " + json.toString());
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}