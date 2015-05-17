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



public class SendNotification implements Runnable {

    private long gameID;
    private long setID;
    private long opponentPID;
    private long myPID;
    private String gameName;
    private String penteLiveAPNSkey;
    private String penteLiveAPNSpwd;
    private boolean productionFlag;
    private DBHandler dbHandler;

    private Category log4j = Category.getInstance(
        SendNotification.class.getName());

    public SendNotification(long gameID, long setID, long opponentPID, long myPID, String gameName, String penteLiveAPNSkey, String penteLiveAPNSpwd, boolean productionFlag, DBHandler dbHandler) {
        // , String penteLiveAPNSkey, String penteLiveAPNSpwd, boolean productionFlag
        this.gameID = gameID;
        this.setID = setID;
        this.opponentPID = opponentPID;
        this.myPID = myPID;
        this.gameName = gameName;
        this.penteLiveAPNSkey = penteLiveAPNSkey;
        this.penteLiveAPNSpwd = penteLiveAPNSpwd;
        this.productionFlag = productionFlag;
        this.dbHandler = dbHandler;
    }

    public void run() {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {

            log4j.debug("Notification from pid " + opponentPID + " to my pid " + myPID);

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

                        if (gameID > 0) {
                            payload.addAlert("It's your move in a game of " + gameName + " against " + name);
                            payload.addCustomDictionary("gameID", "" + gameID);
                        } else if (setID > 0) {
                            payload.addAlert("" + name + " has invited you to a game of " + gameName);
                            payload.addCustomDictionary("setID", "" + setID);
                        } else {
                            payload.addAlert("" + name + " sent you a new message! " + gameName);
                            payload.addCustomDictionary("newMessage", "newMsg");
                        }
                        payload.addSound("default");

                        String device = rs.getString("token");
                        List<PushedNotification> notifications = Push.payload(payload, penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, device);


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
