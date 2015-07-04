package org.pente.subscriptions;

import java.sql.*;

import java.io.IOException;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.*;
import javax.servlet.http.*;

import com.paypal.core.LoggingManager;
import com.paypal.ipn.IPNMessage;
// import com.sample.util.Configuration;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;
import org.pente.database.*;
import org.pente.message.*;
import org.pente.turnBased.SendNotification;



public class PaypalIPNListenerServlet extends HttpServlet {
    
    private static final Category log4j = Category.getInstance(PaypalIPNListenerServlet.class.getName());
    private Resources resources;
    private DBHandler dbHandler;
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /* 
     * receiver for PayPal ipn call back.
     */
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        ServletContext ctx = getServletContext();
        String paypalMode = ctx.getInitParameter("paypalMode");

        // For a full list of configuration parameters refer in wiki page. 
        // (https://github.com/paypal/sdk-core-java/wiki/SDK-Configuration-Parameters)
        Map<String,String> configurationMap = new HashMap<String,String>();
        configurationMap.put("mode", paypalMode);
        IPNMessage  ipnlistener = new IPNMessage(request,configurationMap);
        boolean isIpnVerified = ipnlistener.validate();


        String transactionType = ipnlistener.getTransactionType();
        Map<String,String> map = ipnlistener.getIpnMap();
        

        log4j.info("******* IPN (name:value) pair : "+ map + "  " + "######### TransactionType : "+transactionType+"  ======== IPN verified : "+ isIpnVerified);

        resources = (Resources) ctx.getAttribute(Resources.class.getName());
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        if (isIpnVerified) {
            try {
                String msg = "Hi there,\n\n Thank you for subscribing to pente.org. Your contribution will help us endure and flourish for years to come.\n\n";
                String receiverEmail = ctx.getInitParameter("paypalEmail");
                String logString = "PaypalIPNListenerServlet: ";
                String indentString = "\n                          ";

                String transactionID = map.get("txn_id");
                logString = logString + indentString + "transactionID = " + transactionID;
                String refundTXid = null;
                if ("refund".equals(map.get("reason_code"))) {
                    refundTXid = map.get("parent_txn_id");
                    logString = logString + indentString + "refund for " + refundTXid;
                }
                logString = logString + indentString + " receiver_email = " + receiverEmail;
                if (!map.get("receiver_email").equals(receiverEmail)) {
                    log4j.error(logString + indentString + "Error: wrong recipient: " + map.get("receiver_email") + " and I am " + receiverEmail);
                    return;
                }

                logString = logString + indentString + " currency = " + map.get("mc_currency");
                // if (!"USD".equals(map.get("mc_currency"))) {
                if (!"EUR".equals(map.get("mc_currency"))) {
                    log4j.error(logString + indentString + "Error: wrong currency: " + map.get("mc_currency") + " and I expected EUR");
                    return;
                }

                double grossAmount = Double.parseDouble(map.get("mc_gross"));
                double feeAmount = Double.parseDouble(map.get("mc_fee"));
                double amount = (grossAmount - feeAmount);
                logString = logString + indentString + " nett amount = " + amount;

                dbHandler = resources.getDbHandler();
                con = dbHandler.getConnection();

                stmt = con.prepareStatement("select * from dsg_subscribers where transactionid = ?");
                stmt.setString(1, transactionID);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    log4j.error(logString + indentString + "Error: transaction already exists: " + transactionID);
                    return;
                }
                String customString = map.get("custom");
                logString = logString + indentString + " custom parameter = " + customString;
                String[] customParts = customString.split(";");
                if (customParts.length != 2) {
                    log4j.error(logString + indentString + "Error: malformed custom parameter: I received " + customParts.length + " parts instead of 2");
                    return;
                }
                stmt = con.prepareStatement("select pid from player where name = ?");
                stmt.setString(1, customParts[1]);
                rs = stmt.executeQuery();
                logString = logString + indentString + " subscriber username = " + customParts[1];
                long subscriberPid = 0;
                if (rs.next()) {
                    subscriberPid = rs.getLong("pid");
                    logString = logString + indentString + " subscriber pid = " + subscriberPid;
                } else {
                    log4j.error(logString + indentString + "Error: subscriber username not recognized: " + customParts[1]);
                    return;
                }
                long gifterPid = 0;
                if ((customParts[0] != null)  && !"".equals(customParts[0]) && !"null".equals(customParts[0])) {
                    stmt = con.prepareStatement("select pid from player where name = ?");
                    stmt.setString(1, customParts[0]);
                    rs = stmt.executeQuery();
                    if (rs.next()) {
                        gifterPid = rs.getLong("pid");
                    } else {
                        log4j.error(logString + indentString + "Error: gifter username not recognized: " + customParts[0]);
                        return;
                    }
                } else {
                    logString = logString + indentString + " no gifter username";
                }

                if ((customParts[1] == null)  || "".equals(customParts[1]) || "null".equals(customParts[1])) {
                    log4j.error(logString + indentString + "Error: giftee username not recognized: " + customParts[1]);
                    return;
                }

                String itemID = map.get("item_number");
                int tries = 1;
                while ((itemID == null) && tries < 5) {
                    itemID = map.get("item_number"+tries);
                    tries++;
                }
                logString = logString + indentString + " itemID = " + itemID;

                msg = msg + " You have purchased ";
                int subscriptionLvl = 0;
                if (itemID != null) {
                    if (itemID.contains("1MONTH")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEMONTH);
                        msg = msg + "1 month of :\n";
                    }
                     if (itemID.contains("1YR")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.ONEYEAR);
                        msg = msg + "1 year of :\n";
                    }
                    if (itemID.contains("UNLIMITEDGAMES")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.UNLIMITEDTBGAMES);
                        msg = msg + "- unlimited games\n";
                    }
                    if (itemID.contains("UNLIMITEDMOBILEGAMES")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.UNLIMITEDMOBILETBGAMES);
                        msg = msg + "- unlimited mobile games\n";
                    }
                    if (itemID.contains("NOADS")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.NOADS);
                        msg = msg + "- no advertisements\n";
                    }
                    if (itemID.contains("DATABASE")) {
                        subscriptionLvl = (subscriptionLvl | org.pente.gameServer.core.MySQLDSGPlayerStorer.DBACCESS);
                        msg = msg + "- database access\n";
                    }
                }
                if (subscriptionLvl == 0) {
                    log4j.error(logString + indentString + "Error: itemID not recognized: " + itemID);
                    return;
                }
                if (refundTXid != null) {
                    subscriptionLvl = 0;
                    stmt = con.prepareStatement("UPDATE dsg_subscribers SET level=0 WHERE transactionid = ?");
                    stmt.setString(1, refundTXid);
                    int worked = stmt.executeUpdate();
                    if (worked < 1) {
                        log4j.error(logString + indentString + "Error: updating after refund FAILED **");
                        return;
                    } else {
                        log4j.info(logString + indentString + "Refund successfully registered");
                    }
                }
                stmt = con.prepareStatement("INSERT INTO dsg_subscribers (pid, level, paymentdate, transactionid, amount) VALUES (?, ?, NOW(), ?, ?)");
                stmt.setLong(1, subscriberPid);
                stmt.setInt(2, subscriptionLvl);
                stmt.setString(3, transactionID);
                stmt.setDouble(4, amount);
                int worked = stmt.executeUpdate();
                if (worked < 1) {
                    log4j.error(logString + indentString + "Error: inserting purchase FAILED **");
                    return;
                } else {
                    log4j.info(logString + indentString + "Purchase successfully registered");
                }
                DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
                DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(subscriberPid);
                if (dsgPlayerData.getNameColorRGB() == 0) {
                    dsgPlayerData.setNameColorRGB(-16751616);
                    dsgPlayerStorer.updatePlayer(dsgPlayerData);
                }

                if (refundTXid == null) {
                    DSGMessageStorer dsgMessageStorer = resources.getDsgMessageStorer();
                    DSGMessage message = new DSGMessage();
                    message.setCreationDate(new java.util.Date());
                    message.setFromPid(23000000016237L);
                    message.setToPid(subscriberPid);
                    message.setSubject("Subscription purchase successful");
                    if (gifterPid != 0) {
                        message.setBody(msg.replace("You have purchased ", customParts[0] + " has purchased for you ") + "\nnHave oodles of fun here at pente.org.\n\nPS: if you have any questions, feel free to reply to this message.");
                    } else {
                        message.setBody(msg + "\nHave oodles of fun here at pente.org.\n\nPS: if you have any questions, feel free to reply to this message.");
                    }
                    dsgMessageStorer.createMessage(message);
                    if (gifterPid != 0) {
                        message = new DSGMessage();
                        message.setCreationDate(new java.util.Date());
                        message.setFromPid(23000000016237L);
                        message.setToPid(gifterPid);
                        message.setSubject("Subscription purchase successful");
                        message.setBody(msg + " for " + customParts[1] + ".");
                        dsgMessageStorer.createMessage(message);
                    }
                }

                CacheDSGPlayerStorer d = (CacheDSGPlayerStorer) resources.getDsgPlayerStorer();
                d.refreshPlayer(customParts[1]);

                stmt = con.prepareStatement("SELECT SUM(amount) FROM dsg_subscribers");
                rs = stmt.executeQuery();
                double totalSum = 0;
                if (rs.next()) {
                    totalSum = rs.getDouble(1);
                } 

                String penteLiveAPNSkey = ctx.getInitParameter("penteLiveAPNSkey");
                String penteLiveAPNSpwd = ctx.getInitParameter("penteLiveAPNSpassword");
                boolean productionFlag = ctx.getInitParameter("penteLiveAPNSproductionFlag").equals("true");
                Thread thread = new Thread(new SendNotification(0, 0, 23000000016237L, 23000000016237L, 
                    customParts[1] + ((refundTXid == null)?" subscribed":" was refunded") + ((gifterPid != 0)?" by "+customParts[0]:"") + " for EUR " + amount + ", the total is now: " + totalSum, penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, dbHandler ) );
                thread.start();

            } catch (SQLException e) {
                log4j.error("PaypalIPNListenerServlet SQLException " + e);
            } catch (DSGMessageStoreException e) {
                log4j.error("PaypalIPNListenerServlet DSGMessageStoreException " + e);
            } catch (DSGPlayerStoreException e) {
                log4j.error("PaypalIPNListenerServlet DSGPlayerStoreException " + e);
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
                    log4j.info("PaypalIPNListenerServlet SQLException " + e);
                }

            }
        }


    }
}