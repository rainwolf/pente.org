package org.pente.subscriptions;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;

import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.*;

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
    private Resources resources;
    private DBHandler dbHandler;
    private Date startDate = null;
    private String transactionId = null;

    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        ServletContext ctx = getServletContext();
        String iOSSharedSecret = ctx.getInitParameter("iOSSharedSecret");
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
        CacheDSGPlayerStorer dsgPlayerStorer = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
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
        log4j.info("IOSReceiptServlet: username and logged in: " +username+" and "+ player);
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

        if (!checkReceipt(receiptDataStr, iOSSharedSecret, true)) {
            log4j.info("IOSReceiptServlet: error: Receipt not valid");
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println("invalid receipt");
            return;
        }

            try {
                subscriberData = dsgPlayerStorer.loadPlayer(username.toLowerCase());
                if (username == null) {
                    log4j.info("IOSReceiptServlet: error: invalid username");
                    return;
                }
                long subscriberPid = subscriberData.getPlayerID();
                log4j.info("IOSReceiptServlet: dbHandler");
                dbHandler = resources.getDbHandler();
                con = dbHandler.getConnection();

//                stmt = con.prepareStatement("INSERT INTO dsg_subscribers_ios (pid, paymentdate, receipt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate)");
                stmt = con.prepareStatement("INSERT INTO dsg_subscribers_ios (pid, paymentdate, receipt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate), receipt=VALUES(receipt)");
                stmt.setLong(1, subscriberPid);
                stmt.setTimestamp(2, new Timestamp(startDate.getTime()));
                stmt.setString(3, receiptDataStr);
                log4j.info("IOSReceiptServlet: before executeUpdate of insert");
                int worked = stmt.executeUpdate();
                stmt.close();

                int subscriptionLvl = 0;
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEYEAR);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.UNLIMITEDTBGAMES);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.NOADS);
                subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.DBACCESS);

                log4j.info("IOSReceiptServlet: Before insert");
                DSGPlayerData dsgPlayerData = subscriberData;
                stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount, verified) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE paymentdate=VALUES(paymentdate)");
                stmt.setLong(1, subscriberPid);
                stmt.setInt(2, subscriptionLvl);
                stmt.setTimestamp(3, new Timestamp(startDate.getTime()));
                stmt.setString(4, transactionId);
                stmt.setDouble(5, 0);
                stmt.setInt(6, 1);
                log4j.info("IOSReceiptServlet: before executeUpdate of insert");
                worked = stmt.executeUpdate();
                stmt.close();

                if (dsgPlayerData.getNameColorRGB() == 0) {
                    dsgPlayerData.setNameColorRGB(-16751616);
                    dsgPlayerStorer.updatePlayer(dsgPlayerData);
                }

                DSGMessageStorer dsgMessageStorer = resources.getDsgMessageStorer();
                DSGMessage message = new DSGMessage();
                message.setCreationDate(new java.util.Date());
                message.setFromPid(23000000016237L);
                message.setToPid(subscriberPid);
                message.setSubject("Subscription purchase successful");
                String msg = "Hi there,\n\n Thank you for subscribing to pente.org. Your contribution will help us endure and flourish for years to come.\n";
                message.setBody(msg + "\nHave oodles of fun here at pente.org.\n\nPS: if you have any questions, feel free to reply to this message.");
                dsgMessageStorer.createMessage(message);
                dsgPlayerStorer.refreshPlayer(subscriberData.getName());

                NotificationServer notificationServer = resources.getNotificationServer();
                notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());
                
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println("success");

                log4j.info("IOSReceiptServlet: iOS subscription purchase successful for " + subscriberData.getName());
                notificationServer.sendAdminNotification("iOS subscription for " + subscriberData.getName());
                
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

        private boolean checkReceipt(String receiptDataStr, String sharedSecret, boolean production) {
            String lines = "";
            // sandbox URL
            try {
                String SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt";
                // production URL
                String PRODUCTION_URL = "https://buy.itunes.apple.com/verifyReceipt";
                JSONObject obj = new JSONObject();
                obj.put("receipt-data", receiptDataStr);
                obj.put("password", sharedSecret);

                final URL url = new URL(production?PRODUCTION_URL:SANDBOX_URL);
                final HttpURLConnection conn = (HttpsURLConnection)url.openConnection();
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

                JSONObject json =  new JSONObject(lines);

                // verify the response: something like {"status":21004} etc...
                int status = json.getInt("status");
                switch (status) {
                    case 0: return getStartDate(json);
                    case 21000: log4j.info("IOSReceiptServlet: " + status + ": App store could not read"); return false;
                    case 21002: log4j.info("IOSReceiptServlet: " + status + ": Data was malformed"); return false;
                    case 21003: log4j.info("IOSReceiptServlet: " + status + ": Receipt not authenticated"); return false;
                    case 21004: log4j.info("IOSReceiptServlet: " + status + ": Shared secret does not match"); return false;
                    case 21005: log4j.info("IOSReceiptServlet: " + status + ": Receipt server unavailable"); return false;
                    case 21006: log4j.info("IOSReceiptServlet: " + status + ": Receipt valid but sub expired"); return false;
                    case 21007: log4j.info("IOSReceiptServlet: " + status + ": Sandbox receipt sent to Production environment"); return checkReceipt(receiptDataStr, sharedSecret, false);
                    case 21008: log4j.info("IOSReceiptServlet: " + status + ": Production receipt sent to Sandbox environment"); return false;
                    default:
                        // unknown error code (nevertheless a problem)
                        log4j.info("IOSReceiptServlet: " + "Unknown error: status code = " + status);
                        return false;
                }
            } catch (IOException e) {
                // I/O-error: let's assume bad news...
                log4j.info("IOSReceiptServlet: I/O error during verification: " + e);
                e.printStackTrace();
                return false;
            } catch (JSONException e) {
                log4j.info("IOSReceiptServlet: JSONException during verification: " + e);
                log4j.info("IOSReceiptServlet: received response: " + lines);
                e.printStackTrace();
                return false;
            }
        }

        private boolean getStartDate(JSONObject json) {
            try {
                long start_ms = 0;

                JSONObject tmpJSON = json.getJSONObject("receipt");
                JSONArray jsonArray = tmpJSON.getJSONArray("in_app");
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsn = jsonArray.getJSONObject(i);
                    if ("1YRNOADSORLIMITS".equals(jsn.getString("product_id"))) {
                        transactionId = jsn.getString("original_transaction_id");
                        break;
                    }
                }
                start_ms = tmpJSON.getLong("original_purchase_date_ms");
                startDate = new Date();
                startDate.setTime(start_ms);
                
                if (json.has("latest_receipt_info")) {
                    jsonArray = json.getJSONArray("latest_receipt_info");
                } else if (json.has("latest_expired_receipt_info")) {
                    jsonArray = json.getJSONArray("latest_expired_receipt_info");
                } else {
                    log4j.info("IOSReceiptServlet: getStartDate: Returned data: " + json.toString());
                    return false;
                }


                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsn = jsonArray.getJSONObject(i);
                    if ("1YRNOADSORLIMITS".equals(jsn.getString("product_id"))) {
                        if (jsn.getLong("expires_date_ms") - (364L*24*3600*1000) > start_ms) {
                            transactionId = jsn.getString("transaction_id");
                            start_ms = jsn.getLong("expires_date_ms") - (364L*24*3600*1000);
                        }
                    }
                }
                startDate.setTime(start_ms);
                if (transactionId != null) {
                    return true;
                } else {
                    log4j.info("IOSReceiptServlet: getStartDate: Returned data: " + json.toString());
                    return false;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return false;
        }
}