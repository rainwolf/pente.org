package org.pente.notifications;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by waliedothman on 28/01/2017.
 */
public interface NotificationServer {
    int iOS = 0;
    int ANDROID = 1;
    
    void registerDevice(long pid, String token, int device) throws NotificationServerException;
    Map<String, Date> getTokens(long pid, int device) throws NotificationServerException;
    void removeInvalidToken(long pid, String token, int device) throws NotificationServerException;
    void sendMoveNotification(String fromName, long pid, long gameId, String gameName);
    void sendInvitationNotification(String fromName, long pid, long setId, String gameName);
    void sendMessageNotification(String fromName, long pid, long messageId, String subject);
    void sendAdminNotification(String message);
    void sendBroadcastNotification(String player, String game, long pid);
    boolean canBroadcast(long pid);
    void storeBroadcastDate(long pid);
}
