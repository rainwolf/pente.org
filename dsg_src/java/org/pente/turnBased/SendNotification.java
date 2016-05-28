package org.pente.turnBased;

import java.util.*;
import java.util.List;
import java.util.Date;
import java.sql.*;
import javapns.Push;
import javapns.notification.*;
import javapns.notification.PushNotificationPayload;
import org.pente.database.*;

import org.apache.log4j.*;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class SendNotification implements Runnable {

    private int notificationType;
    private long gsmID;
    private long opponentPID;
    private long myPID;
    private String gameName;
    private String penteLiveAPNSkey;
    private String penteLiveGCMkey;
    private String penteLiveAPNSpwd;
    private boolean productionFlag;
    private DBHandler dbHandler;

    private Category log4j = Category.getInstance(
        SendNotification.class.getName());

    public SendNotification(int notificationType, long gsmID, long opponentPID, long myPID, String gameName, String penteLiveAPNSkey, String penteLiveAPNSpwd, boolean productionFlag, DBHandler dbHandler, String penteLiveGCMkey) {
        // , String penteLiveAPNSkey, String penteLiveAPNSpwd, boolean productionFlag
        this.notificationType = notificationType;
        this.gsmID = gsmID;
        this.opponentPID = opponentPID;
        this.myPID = myPID;
        this.gameName = gameName;
        this.penteLiveGCMkey = penteLiveGCMkey;
        this.penteLiveAPNSkey = penteLiveAPNSkey;
        this.penteLiveAPNSpwd = penteLiveAPNSpwd;
        this.productionFlag = productionFlag;
        this.dbHandler = dbHandler;
        this.penteLiveGCMkey = penteLiveGCMkey;
    }

    public void run() {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {

            // log4j.debug("Notification from pid " + this.opponentPID + " to my pid " + this.myPID + " of type " + this.notificationType);

            // if (this.myPID == 23000000016237) {
            //     this.productionFlag = false;
            //     this.penteLiveAPNSkey = "/etc/dsg/PenteLiveDevAPNSkey.p12";
            // }

            con = dbHandler.getConnection();


            stmt = con.prepareStatement("select token, lastping from notifications where pid = ?");
            stmt.setLong(1, this.myPID);
            rs = stmt.executeQuery();

            String name = "";
            if (rs.isBeforeFirst()) {
                    PreparedStatement stmt1 = con.prepareStatement("select name from player where pid=?");
                    stmt1.setLong(1, this.opponentPID);
                    ResultSet rs1 = stmt1.executeQuery();
                    if (rs1.next()) {
                        name = rs1.getString("name");
                    }
                    stmt1.close();
            }
            while (rs.next()) {
                Timestamp lastPing = rs.getTimestamp("lastping");
                java.util.Date date = new java.util.Date();
                Timestamp lastWeekTimestamp = new Timestamp(date.getTime());
                long lastWeek = lastWeekTimestamp.getTime() - (7*1000*3600*24);
                lastWeekTimestamp.setTime(lastWeek);
                if (lastWeekTimestamp.before(lastPing)) {


                    try{
                        PushNotificationPayload payload = PushNotificationPayload.complex();

                        payload.addSound("penteLiveNotificationSound.caf");
                        if (notificationType == 1) {
                            payload.addAlert("It's your move in a game of " + gameName + " against " + name);
                            payload.addCustomDictionary("gameID", "" + this.gsmID);
                        } else if (notificationType == 2) {
                            payload.addAlert("" + name + " has invited you to a game of " + gameName);
                            payload.addCustomDictionary("setID", "" + this.gsmID);
                        }  else if (notificationType == 3) {
                            payload.addAlert("" + name + " sent you a new message! \n\"" + gameName + "\"");
                            payload.addCustomDictionary("msgID", "" + this.gsmID);
                        } else if (notificationType == 0) {
                            payload.addAlert(gameName);
                            payload.addSound("default");
                        }
                        payload.addBadge(1);

                        String device = rs.getString("token");
                        List<PushedNotification> notifications = Push.payload(payload, this.penteLiveAPNSkey, this.penteLiveAPNSpwd, this.productionFlag, device);

                        String logString = "iOS Notification from pid " + this.opponentPID + " to my pid " + this.myPID + " of type " + this.notificationType;
                        for (PushedNotification notification : notifications) {
                                if (notification.isSuccessful()) {
                                    logString += " was successful";
                                } else {
                                        String invalidToken = notification.getDevice().getToken();
                                        ResponsePacket theErrorResponse = notification.getResponse();
                                        /* Add code here to remove invalidToken from your database */  

                                        if (theErrorResponse.getMessage().indexOf("Invalid token") != -1) {
                                            PreparedStatement stmt1 = con.prepareStatement("DELETE from notifications where token=?");
                                            stmt1.setString(1, invalidToken);
                                            stmt1.executeUpdate();
                                            stmt1.close();
                                        } else {
                                            /* Find out more about what the problem was */  
                                            Exception theProblem = notification.getException();
                                            theProblem.printStackTrace();
                                        }

                                        /* If the problem was an error-response packet returned by Apple, get it */  
                                        if (theErrorResponse != null) {
                                                logString += " was unsuccessful because: " + theErrorResponse.getMessage() + " with token " + device;
                                        }
                                }
                        }
                        log4j.debug(logString);

                    } catch(Exception e){
                        return;            // Always must return something
                    }
                }
            }





            stmt = con.prepareStatement("select token, lastping from notifications_android where pid = ?");
            stmt.setLong(1, this.myPID);
            rs = stmt.executeQuery();

            if (rs.isBeforeFirst() && name.equals("")) {
                    PreparedStatement stmt1 = con.prepareStatement("select name from player where pid=?");
                    stmt1.setLong(1, this.opponentPID);
                    ResultSet rs1 = stmt1.executeQuery();
                    if (rs1.next()) {
                        name = rs1.getString("name");
                    }
                    stmt1.close();
            }
            while (rs.next()) {
                Timestamp lastPing = rs.getTimestamp("lastping");
                java.util.Date date = new java.util.Date();
                Timestamp lastWeekTimestamp = new Timestamp(date.getTime());
                long lastWeek = lastWeekTimestamp.getTime() - (7*1000*3600*24);
                lastWeekTimestamp.setTime(lastWeek);
                if (lastWeekTimestamp.before(lastPing)) {


                    try{

                        // Prepare JSON containing the GCM message content. What to send and where to send.
                        JSONObject jGcmData = new JSONObject();
                        JSONObject jData = new JSONObject();
                        String device = rs.getString("token");
                        String message = "";
                        if (notificationType == 1) {
                            message = "It's your move in a game of " + gameName + " against " + name;
                            jData.put("gameID", "" + this.gsmID);
                        } else if (notificationType == 2) {
                            message = "" + name + " has invited you to a game of " + gameName;
                            jData.put("setID", "" + this.gsmID);
                        }  else if (notificationType == 3) {
                            message = "" + name + " sent you a new message! \n\"" + gameName + "\"";
                            jData.put("msgID", "" + this.gsmID);
                        } else if (notificationType == 0) {
                            message = gameName;
                        }
                        jData.put("message", message);
                        jGcmData.put("to", device);
                        jGcmData.put("data", jData);

                        try {

                            // Create connection to send GCM Message request.
                            URL url = new URL("https://fcm.googleapis.com/fcm/send");
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestProperty("Authorization", "key=" + penteLiveGCMkey);
                            // conn.setRequestProperty("accept-charset", "UTF-8");
                            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);

                            // Send GCM message content.
                            OutputStream outputStream = conn.getOutputStream();
                            outputStream.write(jGcmData.toString().getBytes("UTF-8"));

                            // Read GCM response.
                            InputStream inputStream = conn.getInputStream();
                            String resp = IOUtils.toString(inputStream);
                            System.out.println(resp);
                            System.out.println("Check your device/emulator for notification or logcat for " +
                                    "confirmation of the receipt of the GCM message.");

                            String logString = "Android Notification from pid " + this.opponentPID + " to my pid " + this.myPID + " of type " + this.notificationType;
                            if (resp.indexOf("InvalidRegistration") > -1 || resp.indexOf("NotRegistered") > -1 ) {
                                PreparedStatement stmt1 = con.prepareStatement("DELETE from notifications_android where token=?");
                                stmt1.setString(1, device);
                                stmt1.executeUpdate();
                                stmt1.close();
                            } else {
                                logString += " was successful";
                            }
                            log4j.debug(logString);

                        } catch (IOException e) {
                            System.out.println("Unable to send GCM message.");
                            System.out.println("Please ensure that API_KEY has been replaced by the server " +
                                    "API key, and that the device's registration token is correct (if specified).");
                            e.printStackTrace();
                        }



                    } catch(Exception e){
                        return;            // Always must return something
                    }
                }
            }

            stmt.close();


            if (con != null) {
                dbHandler.freeConnection(con);
            }
        } catch(Exception e) {
            return;
        }




    }
}
