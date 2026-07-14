package org.pente.notifications;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import org.apache.log4j.Category;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;

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
    private GoogleCredentials googleCredentials;
    private String penteLiveAPNSpwd;
    private boolean productionFlag;

    private Timer checkRecordsTimer;

    private ApnsClient client;

    public CacheNotificationServer(MySQLNotificationServer baseStorer, String penteLiveAPNSkey, GoogleCredentials googleCredentials, String penteLiveAPNSpwd, boolean productionFlag) {
        this.baseStorer = baseStorer;
        this.penteLiveAPNSkey = penteLiveAPNSkey;
        this.googleCredentials = googleCredentials;
        this.penteLiveAPNSpwd = penteLiveAPNSpwd;
        this.productionFlag = productionFlag;

        checkRecordsTimer = new Timer();
        checkRecordsTimer.scheduleAtFixedRate(
                new CheckNotificationRecordsRunnable(), 10000, 24L * 3600 * 1000);

        try {
            client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setClientCredentials(new File(penteLiveAPNSkey), penteLiveAPNSpwd)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        checkRecordsTimer.cancel();
        checkRecordsTimer.purge();
        try {
            client.close().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
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
        Runnable runnable = () -> {
            if (device == iOS) {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("Your device has been registered for notifications");

                final SimpleApnsPushNotification pushNotification;
                pushNotification = new SimpleApnsPushNotification(token, "be.submanifold.pentelive", payloadBuilder.build());
                final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
                        sendNotificationFuture = client.sendNotification(pushNotification);
                try {
                    final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
                            sendNotificationFuture.get();

                    if (pushNotificationResponse.isAccepted()) {
                        System.out.println("Push notification accepted by APNs gateway.");
                    } else {
                        System.out.println("Notification rejected by the APNs gateway: " +
                                pushNotificationResponse.getRejectionReason());

                        pushNotificationResponse.getTokenInvalidationTimestamp().ifPresent(timestamp -> {
                            System.out.println("\t…and the token is invalid as of " + timestamp);
                        });
                    }
                } catch (final ExecutionException | InterruptedException e) {
                    System.err.println("Failed to send push notification.");
                    e.printStackTrace();
                }
            } else if (device == ANDROID) {
                JSONObject jGcmData = new JSONObject();
                JSONObject jMessage = new JSONObject();
                String message = "Your device has been registered for push notifications";
                try {
                    jMessage.put("token", token);
                    jMessage.put("data", new JSONObject().put("message", message));
                    jGcmData.put("message", jMessage);

                    sendAndroidNotification(pid, token, jGcmData.toString());
                } catch (JSONException e) {
                    log4j.error("sendRegistrationConfirmation android error.");
                    e.printStackTrace();
                }
            }
        };
        (new Thread(runnable)).start();
    }

    private void sendAndroidNotification(long pid, String token, String message) {
        Runnable runnable = () -> {
            String resp = "";
            HttpURLConnection conn = null;
            try {
                this.googleCredentials.refreshIfExpired();
                AccessToken accessToken = this.googleCredentials.getAccessToken();

                // Create connection to send GCM Message request.
                URL url = new URI("https://fcm.googleapis.com/v1/projects/pente-live/messages:send").toURL();
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + accessToken.getTokenValue());
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                // Send GCM message content.
                OutputStream outputStream = conn.getOutputStream();
                outputStream.write(message.getBytes("UTF-8"));

                // Flush the request body and obtain the FCM v1 response status BEFORE reading any stream.
                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    // Strictly 2xx: accepted. The success body is only available via getInputStream() (may be null).
                    try (InputStream is = conn.getInputStream()) {
                        resp = is == null ? "" : IOUtils.toString(is, "UTF-8");
                    }
                    log4j.info("Android Push notification accepted.");
                    log4j.info("==============" + resp);
                } else {
                    // Any non-2xx (incl. 1xx/3xx and >=400): error. The body is only available via
                    // getErrorStream() (may be null).
                    try (InputStream es = conn.getErrorStream()) {
                        resp = es == null ? "" : IOUtils.toString(es, "UTF-8");
                    }
                    // Structured FCM v1 error parse. Only UNREGISTERED means the token is permanently invalid;
                    // INVALID_ARGUMENT (and every other code) can stem from a malformed request field, so a
                    // payload bug must NEVER mass-delete valid tokens.
                    String fcmError = extractFcmErrorCode(resp);
                    if ("UNREGISTERED".equals(fcmError)) {
                        removeInvalidToken(pid, token, ANDROID);
                        log4j.info("Removed unregistered Android token for " + pid);
                    } else {
                        log4j.error("Android Push notification failed: HTTP " + responseCode +
                                (fcmError.isEmpty() ? "" : " " + fcmError) + " for " + pid +
                                " with token " + token + ". Response: " + resp);
                    }
                }
            } catch (IOException e) {
                log4j.error("Unable to send GCM message.");
                log4j.error("Problem sending android notification for " + pid + " with token " + token);
                log4j.error("Response: " + resp);
                e.printStackTrace();
            } catch (NotificationServerException e) {
                log4j.error("Removing android token failed. " + token);
                e.printStackTrace();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } finally {
                // Always release the socket, even if IOUtils.toString throws mid-read.
                if (conn != null) {
                    conn.disconnect();
                }
            }
        };
        (new Thread(runnable)).start();
    }

    private String extractFcmErrorCode(String body) {
        if (body == null || body.isEmpty()) return "";
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            if (error == null) return "";
            org.json.JSONArray details = error.optJSONArray("details");
            if (details != null) {
                for (int i = 0; i < details.length(); i++) {
                    JSONObject d = details.optJSONObject(i);
                    if (d != null && d.optString("@type", "").endsWith("FcmError")) {
                        String c = d.optString("errorCode", "");
                        if (!c.isEmpty()) return c;
                    }
                }
            }
            return error.optString("status", "");
        } catch (JSONException e) {
            return "";
        }
    }

    private void sendiOSNotification(long pid, String token, String payload) {
        Runnable runnable = () -> {
            final SimpleApnsPushNotification pushNotification;
            pushNotification = new SimpleApnsPushNotification(token, "be.submanifold.pentelive", payload);
            final PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>>
                    sendNotificationFuture = client.sendNotification(pushNotification);
            try {
                final PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse =
                        sendNotificationFuture.get();

                if (pushNotificationResponse.isAccepted()) {
                    log4j.info("iOS Push notification accepted by APNs gateway.");
                } else {
                    log4j.info("Notification rejected by the APNs gateway: " +
                            pushNotificationResponse.getRejectionReason());

                    pushNotificationResponse.getTokenInvalidationTimestamp().ifPresent(timestamp -> {
                        log4j.info("\t…and the token is invalid as of " + timestamp);
                    });
                }
            } catch (final ExecutionException | InterruptedException e) {
                System.err.println("Failed to send push notification.");
                e.printStackTrace();
            }
        };
        (new Thread(runnable)).start();
    }

    @Override
    public void sendMoveNotification(String fromName, long pid, long gameId, String gameName) {
        Map<String, Date> tokenMap = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L * 3600 * 24 * 7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("It's your move in a game of " + gameName + " against " + fromName)
                        .setBadgeNumber(1)
                        .setSound("penteLiveNotificationSound.caf")
                        .addCustomProperty("gameID", "" + gameId);
                sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jMessage = new JSONObject();
                String message = "It's your move in a game of " + gameName + " against " + fromName;
                try {
                    jMessage.put("token", tokenEntry.getKey());
                    jMessage.put("data", new JSONObject().put("gameID", "" + gameId).put("message", message));
                    jGcmData.put("message", jMessage);

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
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L * 3600 * 24 * 7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("" + fromName + " has invited you to a game of " + gameName)
                        .setBadgeNumber(1)
                        .setSound("penteLiveNotificationSound.caf")
                        .addCustomProperty("setID", "" + setId);
                sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jMessage = new JSONObject();
                String message = "" + fromName + " has invited you to a game of " + gameName;
                try {
                    jMessage.put("token", tokenEntry.getKey());
                    jMessage.put("data", new JSONObject().put("setID", "" + setId).put("message", message));
                    jGcmData.put("message", jMessage);

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
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L * 3600 * 24 * 7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("" + fromName + " sent you a new message! \n\"" + subject + "\"")
                        .setBadgeNumber(1)
                        .setSound("penteLiveNotificationSound.caf")
                        .addCustomProperty("msgID", "" + messageId);
                sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jMessage = new JSONObject();
                String message = "" + fromName + " sent you a new message! \n\"" + subject + "\"";
                try {
                    jMessage.put("token", tokenEntry.getKey());
                    jMessage.put("data", new JSONObject().put("msgID", "" + messageId).put("message", message));
                    jGcmData.put("message", jMessage);

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

        long pid = 23000000016237L;

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertBody(message)
                    .setBadgeNumber(1)
                    .setSound("default");
            sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            JSONObject jGcmData = new JSONObject();
            JSONObject jMessage = new JSONObject();
            try {
                jMessage.put("token", tokenEntry.getKey());
                jMessage.put("data", new JSONObject().put("message", message));
                jGcmData.put("message", jMessage);

                sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendSilentNotification(long pid) {
//        if (pid != 23000000016237L) {
//            return;
//        }
        Map<String, Date> tokenMap = null;

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            try {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("").setAlertTitle("")
                        .setBadgeNumber(0)
                        .setSound("")
                        .addCustomProperty("silentNotification", "");
                sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            JSONObject jGcmData = new JSONObject();
            JSONObject jMessage = new JSONObject();
            try {
                jMessage.put("token", tokenEntry.getKey());
                jMessage.put("data", new JSONObject().put("message", "silentNotification"));
                jGcmData.put("message", jMessage);

                sendAndroidNotification(pid, tokenEntry.getKey(), jGcmData.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void sendBroadcastNotification(String player, String game, long pid) {
        Map<String, Date> tokenMap = null;
        Date oneWeekAgo = new Date();
        long timeMillis = oneWeekAgo.getTime();
        oneWeekAgo.setTime(timeMillis - 1000L * 3600 * 24 * 7);

        try {
            tokenMap = new HashMap<>(getTokens(pid, iOS));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {
                ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
                payloadBuilder.setAlertBody("Live Game Alert\n" + player + " wants to play live " + game)
                        .setBadgeNumber(1)
                        .setSound("newplayer.caf")
                        .addCustomProperty("liveBroadCastPlayer", player)
                        .addCustomProperty("liveBroadCastGame", game);
                sendiOSNotification(pid, tokenEntry.getKey(), payloadBuilder.build());
            }
        }

        try {
            tokenMap = new HashMap<>(getTokens(pid, ANDROID));
        } catch (NotificationServerException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, Date> tokenEntry : tokenMap.entrySet()) {
            if (oneWeekAgo.before(tokenEntry.getValue())) {

                JSONObject jGcmData = new JSONObject();
                JSONObject jMessage = new JSONObject();
                try {
                    jMessage.put("token", tokenEntry.getKey());
                    JSONObject jData = new JSONObject();
                    jData.put("liveBroadCastPlayer", player);
                    jData.put("liveBroadCastGame", game);
                    jData.put("message", "Live Game Alert\n" + player + " wants to play live " + game);
                    jMessage.put("data", jData);
                    jGcmData.put("message", jMessage);

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
            oneHourAgo.setTime(timeMillis - 1000L * 3600);
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
            twoWeeksAgo.setTime(timeMillis - 1000L * 3600 * 24 * 14);
            for (Map.Entry<Long, Map<String, Date>> tokenMapEntry : iOStokens.entrySet()) {
                Iterator<Map.Entry<String, Date>> iter = tokenMapEntry.getValue().entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, Date> tokenEntry = iter.next();
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
            for (Map.Entry<Long, Map<String, Date>> tokenMapEntry : androidTokens.entrySet()) {
                Iterator<Map.Entry<String, Date>> iter = tokenMapEntry.getValue().entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, Date> tokenEntry = iter.next();
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
