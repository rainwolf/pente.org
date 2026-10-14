package org.pente.subscriptions.play;

import java.util.Date;

import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.tourney.TourneyStorer;
import org.pente.gameServer.tourney.VacationPurchaseRule;
import org.pente.message.DSGMessage;
import org.pente.message.DSGMessageStorer;
import org.pente.notifications.NotificationServer;
import org.pente.turnBased.CacheTBStorer;

/** PlayerGateway over the live storers, following IOSReceiptServlet's grant side effects. */
public class DsgPlayGateway implements PlayerGateway {

    /** sender of the thank-you message, as in IOSReceiptServlet */
    private static final long ADMIN_PID = 23000000016237L;
    /** default name colour for new subscribers, as in IOSReceiptServlet */
    private static final int DEFAULT_SUBSCRIBER_COLOR = -16751616;

    private final CacheDSGPlayerStorer playerStorer;
    private final CacheTBStorer tbStorer;
    private final TourneyStorer tourneyStorer;
    private final DSGMessageStorer messageStorer;
    private final NotificationServer notificationServer;

    public DsgPlayGateway(CacheDSGPlayerStorer playerStorer, CacheTBStorer tbStorer, TourneyStorer tourneyStorer,
                          DSGMessageStorer messageStorer, NotificationServer notificationServer) {
        this.playerStorer = playerStorer;
        this.tbStorer = tbStorer;
        this.tourneyStorer = tourneyStorer;
        this.messageStorer = messageStorer;
        this.notificationServer = notificationServer;
    }

    private DSGPlayerData load(long pid) throws PlayStoreException {
        try {
            DSGPlayerData data = playerStorer.loadPlayer(pid);
            if (data == null) {
                throw new PlayStoreException("unknown pid " + pid);
            }
            return data;
        } catch (DSGPlayerStoreException e) {
            throw new PlayStoreException("loadPlayer " + pid + " failed", e);
        }
    }

    public Long subscriptionExpirationMs(long pid) throws PlayStoreException {
        Date expiration = load(pid).getSubscriptionExpiration();
        return expiration == null ? null : Long.valueOf(expiration.getTime());
    }

    public boolean vacationAllowed(long pid) throws PlayStoreException {
        String name = load(pid).getName();
        try {
            return VacationPurchaseRule.vacationAllowed(name, tourneyStorer);
        } catch (Throwable t) {
            throw new PlayStoreException("vacation rule for " + name + " failed", t);
        }
    }

    public void addVacationDays(long pid, int days) {
        tbStorer.addExtraTBVacationDays(pid, days);
    }

    public void granted(long pid, String productId, PlayClassification classification) throws PlayStoreException {
        DSGPlayerData data = load(pid);
        boolean vacation = PlayProducts.vacationDays(productId) > 0;
        try {
            if (!vacation && data.getNameColorRGB() == 0) {
                data.setNameColorRGB(DEFAULT_SUBSCRIBER_COLOR);
                playerStorer.updatePlayer(data);
            }
            DSGMessage message = null;
            if (classification == PlayClassification.NEW) {
                message = new DSGMessage();
                message.setCreationDate(new Date());
                message.setFromPid(ADMIN_PID);
                message.setToPid(pid);
                if (vacation) {
                    message.setSubject("Vacation days purchase successful");
                    message.setBody("Hi there,\n\n Thank you for your purchase, " + PlayProducts.vacationDays(productId)
                            + " extra turn-based vacation days were added to your account. They do not roll over to next year."
                            + "\n\nPS: if you have any questions, feel free to reply to this message.");
                } else {
                    message.setSubject("Subscription purchase successful");
                    message.setBody("Hi there,\n\n Thank you for subscribing to pente.org. Your contribution will help us endure"
                            + " and flourish for years to come.\n\nHave oodles of fun here at pente.org."
                            + "\n\nPS: if you have any questions, feel free to reply to this message.");
                }
                messageStorer.createMessage(message);
            }
            playerStorer.refreshPlayer(data.getName());
            if (message != null) {
                notificationServer.sendMessageNotification("rainwolf", message.getToPid(), message.getMid(), message.getSubject());
            }
        } catch (Exception e) {
            throw new PlayStoreException("grant side effects for " + data.getName() + " failed", e);
        }
        notificationServer.sendAdminNotification("Google Play " + productId + " (" + classification.name().toLowerCase()
                + ") for " + data.getName());
    }

    public void entitlementChanged(long pid) throws PlayStoreException {
        String name = load(pid).getName();
        try {
            playerStorer.refreshPlayer(name);
        } catch (DSGPlayerStoreException e) {
            throw new PlayStoreException("refreshPlayer " + name + " failed", e);
        }
    }

    public void adminAlert(String message) {
        notificationServer.sendAdminNotification(message);
    }
}
