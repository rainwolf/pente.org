package org.pente.notifications;

import javapns.Push;
import javapns.communication.exceptions.CommunicationException;
import javapns.communication.exceptions.KeystoreException;
import javapns.notification.PushNotificationBigPayload;
import javapns.notification.PushNotificationPayload;
import javapns.notification.PushedNotification;
import javapns.notification.ResponsePacket;
import org.apache.log4j.Category;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Created by waliedothman on 28/01/2017.
 */
public class CacheNotificationServer implements NotificationServer {
    private static final Category log4j =
            Category.getInstance(CacheNotificationServer.class.getName());

    MySQLNotificationServer baseStorer;
    
    private Map<Long, Map<String, Date>> iOStokens = new HashMap<>();
    private Map<Long, Map<String, Date>> androidTokens = new HashMap<>();
    private Map<Long, Date> broadcasts = new HashMap<>();

    private String penteLiveAPNSkey;
    private String penteLiveGCMkey;
    private String penteLiveAPNSpwd;
    private boolean productionFlag;

    private Timer checkRecordsTimer;

    public CacheNotificationServer(MySQLNotificationServer baseStorer, String penteLiveAPNSkey, String penteLiveGCMkey, String penteLiveAPNSpwd, boolean productionFlag) {
        this.baseStorer = baseStorer;
        this.penteLiveAPNSkey = penteLiveAPNSkey;
        this.penteLiveGCMkey = penteLiveGCMkey;
        this.penteLiveAPNSpwd = penteLiveAPNSpwd;
        this.productionFlag = productionFlag;

        checkRecordsTimer = new Timer();
        checkRecordsTimer.scheduleAtFixedRate(
                new CheckNotificationRecordsRunnable(), 10000, 24L * 3600 * 1000);

    }

    @Override
    synchronized public void registerDevice(long pid, String token, int device) throws NotificationServerException {
        Map<String, Date> notificationRecords = getTokens(pid, device);
        if (notificationRecords.get(token) == null) {
            sendRegistrationConfirmation(pid, token, device);
        }
        notificationRecords.put(token, new Date());
        baseStorer.registerDevice(pid, token, device);
    }

    @Override
    public Map<String, Date> getTokens(long pid, int device) throws NotificationServerException {
        Map<String, Date> notificationRecords = null;
        if (device == iOS) {
            notificationRecords = iOStokens.get(pid);
        } else if (device == ANDROID) {
            notificationRecords = androidTokens.get(pid);
        }
        if (notificationRecords == null) {
            notificationRecords = baseStorer.getTokens(pid, device);
            if (device == iOS) {
                iOStokens.put(pid, notificationRecords);
            } else if (device == ANDROID) {
                androidTokens.put(pid, notificationRecords);
            }
        }
        return notificationRecords;
    }

    @Override
    synchronized public void removeInvalidToken(long pid, String token, int device) throws NotificationServerException {
        Map<String, Date> notificationRecords = getTokens(pid, device);
        notificationRecords.remove(token);
        baseStorer.removeInvalidToken(pid, token, device);
    }

    private void sendRegistrationConfirmation(long pid, String token, int device) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (device == iOS) {
                    try {
                        Push.alert("Your device has been registered for notifications", penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, token);
                    } catch (CommunicationException | KeystoreException e) {
                        log4j.error("sendRegistrationConfirmation: iOS error: " + e);
                    }
                } else if (device == ANDROID) {
                    JSONObject jGcmData = new JSONObject();
                    JSONObject jData = new JSONObject();
                    String message = "Your device has been registered for push notifications";
                    try {
                        jData.put("message", message);
                        jGcmData.put("to", token);
                        jGcmData.put("data", jData);
                        sendAndroidNotification(pid, token, jGcmData.toString());
                    } catch (JSONException e) {
                        log4j.error("sendRegistrationConfirmation android error.");
                        e.printStackTrace();
                    }
                }
            }
        };
        (new Thread(runnable)).start();
    }
    
    private void sendAndroidNotification(long pid, String token, String message) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // Create connection to send GCM Message request.
                    URL url = new URL("https://android.googleapis.com/gcm/send");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Authorization", "key=" + penteLiveGCMkey);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);

                    // Send GCM message content.
                    OutputStream outputStream = conn.getOutputStream();
                    outputStream.write(message.toString().getBytes());

                    // Read GCM response.
                    InputStream inputStream = conn.getInputStream();
                    String resp = IOUtils.toString(inputStream, "UTF-8");

                    if (resp.contains("InvalidRegistration") || resp.contains("NotRegistered")) {
                        removeInvalidToken(pid, token, ANDROID);
                    }
                } catch (IOException e) {
                    log4j.error("Unable to send GCM message.");
                    log4j.error("Problem sending android notification for " + pid + " with token " + token);
                    e.printStackTrace();
                } catch (NotificationServerException e) {
                    log4j.error("Removing android token failed. " + token);
                    e.printStackTrace();
                }
            }
        };
        (new Thread(runnable)).start();
    }
    
    private void sendiOSNotification(long pid, String token, PushNotificationPayload payload) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                List<PushedNotification> notifications = null;
                try {
                    notifications = Push.payload(payload, penteLiveAPNSkey, penteLiveAPNSpwd, productionFlag, token);
                    for (PushedNotification notification : notifications) {
                        if (!notification.isSuccessful()) {
                            String invalidToken = notification.getDevice().getToken();
                            ResponsePacket theErrorResponse = notification.getResponse();
                                            /* Add code here to remove invalidToken from your database */
    
                            if (theErrorResponse.getMessage().contains("Invalid token")) {
                                try {
                                    removeInvalidToken(pid, invalidToken, iOS);
                                } catch (NotificationServerException e) {
                                    log4j.error("sendiOSNotification error removing token " + invalidToken);
                                    e.printStackTrace();
                                }
                            } else {
                                               /* Find out more about what the problem was */
                                Exception theProblem = notification.getException();
                                theProblem.printStackTrace();
                                log4j.error("Problem sending ios notification for " + pid + " with token " + token);
                            }
    
                                            /* If the problem was an error-response packet returned by Apple, get it */
                            if (theErrorResponse != null) {
                                log4j.error("sendiOSNotification was unsuccessful because: " + theErrorResponse.getMessage() + " with token " + invalidToken);
                                log4j.error("Problem sending ios notification for " + pid + " with token " + token);
                            }
                        }
                    }
                } catch (CommunicationException | KeystoreException e) {
                    e.printStackTrace();
                }
            }
        };
        (new Thread(runnable)).start();
    }

    @Override
    public void sendMoveNotification(String fromName, long pid, long gameId, String gameName) {
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L*3600*24*7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                payload = PushNotificationPayload.complex();
                try {
                    payload.addSound("penteLiveNotificationSound.caf");
                    payload.addAlert("It's your move in a game of " + gameName + " against " + fromName);
                    payload.addCustomDictionary("gameID", "" + gameId);
                    payload.addBadge(1);
                    sendiOSNotification(pid, tokenEntry.getKey(), payload);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jData = new JSONObject();
                String message = "It's your move in a game of " + gameName + " against " + fromName;
                try {
                    jData.put("gameID", "" + gameId);
                    jData.put("message", message);
                    jGcmData.put("to", tokenEntry.getKey());
                    jGcmData.put("data", jData);

                    sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void sendInvitationNotification(String fromName, long pid, long setId, String gameName) {
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L*3600*24*7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                payload = PushNotificationPayload.complex();
                try {
                    payload.addSound("penteLiveNotificationSound.caf");
                    payload.addAlert("" + fromName + " has invited you to a game of " + gameName);
                    payload.addCustomDictionary("setID", "" + setId);
                    payload.addBadge(1);
                    sendiOSNotification(pid, tokenEntry.getKey(), payload);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jData = new JSONObject();
                String message = "" + fromName + " has invited you to a game of " + gameName;
                try {
                    jData.put("setID", "" + setId);
                    jData.put("message", message);
                    jGcmData.put("to", tokenEntry.getKey());
                    jGcmData.put("data", jData);

                    sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void sendMessageNotification(String fromName, long pid, long messageId, String subject) {
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L*3600*24*7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                payload = PushNotificationPayload.complex();
                try {
                    payload.addSound("penteLiveNotificationSound.caf");
                    payload.addAlert("" + fromName + " sent you a new message! \n\"" + subject + "\"");
                    payload.addCustomDictionary("msgID", "" + messageId);
                    payload.addBadge(1);
                    sendiOSNotification(pid, tokenEntry.getKey(), payload);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jData = new JSONObject();
                String message = "" + fromName + " sent you a new message! \n\"" + subject + "\"";
                try {
                    jData.put("msgID", "" + messageId);
                    jData.put("message", message);
                    jGcmData.put("to", tokenEntry.getKey());
                    jGcmData.put("data", jData);

                    sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void sendAdminNotification(String message) {
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;
        
        long pid = 23000000016237L;
        
        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            payload = PushNotificationPayload.complex();
            try {
                payload.addSound("default");
                payload.addAlert(message);
                payload.addBadge(1);
                sendiOSNotification(pid, tokenEntry.getKey(), payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            JSONObject jGcmData = new JSONObject();
            JSONObject jData = new JSONObject();
            try {
                jData.put("message", message);
                jGcmData.put("to", tokenEntry.getKey());
                jGcmData.put("data", jData);

                sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendSilentNotification(long pid) {
        if (pid != 23000000016237L) {
            return;
        }
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            try {
                payload = PushNotificationPayload.complex();
                payload.addCustomDictionary("silentNotification", "");
                payload.addAlert("");
                payload.addSound("");
                payload.addBadge(0);
                sendiOSNotification(pid, tokenEntry.getKey(), payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            JSONObject jGcmData = new JSONObject();
            JSONObject jData = new JSONObject();
            try {
                jData.put("message", "silentNotification");
                jGcmData.put("to", tokenEntry.getKey());
                jGcmData.put("data", jData);

                sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void sendBroadcastNotification(String player, String game, long pid) {
        Map<String, Date> tokenMap = null;
        PushNotificationPayload payload = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L*3600*24*7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                payload = PushNotificationPayload.complex();
                try {
                    payload.addSound("newplayer.caf");
                    payload.addAlert("Live Game Alert\n" + player + " wants to play live " + game);
                    payload.addCustomDictionary("liveBroadCastPlayer", player);
                    payload.addCustomDictionary("liveBroadCastGame", game);
                    payload.addBadge(1);
                    sendiOSNotification(pid, tokenEntry.getKey(), payload);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry: tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jData = new JSONObject();
                try {
                    jData.put("liveBroadCastPlayer", player);
                    jData.put("liveBroadCastGame", game);
                    jData.put("message", "Live Game Alert\n" + player + " wants to play live " + game);
                    jGcmData.put("to", tokenEntry.getKey());
                    jGcmData.put("data", jData);

                    sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean canBroadcast(long pid) {
        Date lastBroadcast = broadcasts.get(pid);
        if (lastBroadcast != null) {
            Date oneHourAgo = new Date();
            long timeMillis = oneHourAgo.getTime();
            oneHourAgo.setTime(timeMillis - 1000L*3600);
            if (oneHourAgo.before(lastBroadcast)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void storeBroadcastDate(long pid) {
        broadcasts.put(pid, new Date());
    }

    private class CheckNotificationRecordsRunnable extends TimerTask {

        private static final int DELAY = 60;
        public String getName() {
            return "CheckNotificationRecordsRunnable";
        }

        public void run() {
            Date twoWeeksAgo = new Date();
            long timeMillis = twoWeeksAgo.getTime();
            twoWeeksAgo.setTime(timeMillis - 1000L*3600*24*14);
            for (Map.Entry<Long, Map<String, Date>> tokenMapEntry: iOStokens.entrySet()) {
                Iterator<Map.Entry<String, Date>> iter = tokenMapEntry.getValue().entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String,Date> tokenEntry = iter.next();
                    if (tokenEntry.getValue().before(twoWeeksAgo)) {
                        try {
                            baseStorer.removeInvalidToken(tokenMapEntry.getKey(), tokenEntry.getKey(), iOS);
                            iter.remove();
                        } catch (NotificationServerException e) {
                            log4j.info("CheckNotificationRecordsRunnable iOS: Something went wrong: " + e);
                        }
                    }
                }
            }
            for (Map.Entry<Long, Map<String, Date>> tokenMapEntry: androidTokens.entrySet()) {
                Iterator<Map.Entry<String, Date>> iter = tokenMapEntry.getValue().entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String,Date> tokenEntry = iter.next();
                    if (tokenEntry.getValue().before(twoWeeksAgo)) {
                        try {
                            baseStorer.removeInvalidToken(tokenMapEntry.getKey(), tokenEntry.getKey(), ANDROID);
                            iter.remove();
                        } catch (NotificationServerException e) {
                            log4j.info("CheckNotificationRecordsRunnable Android: Something went wrong: " + e);
                        }
                    }
                }
            }
            try {
                baseStorer.removeOldTokens();
            } catch (NotificationServerException e) {
                log4j.info("CheckNotificationRecordsRunnable removeOldTokens: Something went wrong: " + e);
            }
        }
    }

}
