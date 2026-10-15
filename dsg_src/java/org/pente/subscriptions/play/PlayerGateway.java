package org.pente.subscriptions.play;

/**
 * Player-side effects of a Play sync. DsgPlayGateway is the real implementation
 * (CacheDSGPlayerStorer, CacheTBStorer, TourneyStorer, messages, admin push); tests use a fake.
 */
public interface PlayerGateway {

    /** DSGPlayerData.getSubscriptionExpiration() in epoch ms, or null */
    Long subscriptionExpirationMs(long pid) throws PlayStoreException;

    /** false while the player is in the last round of a current turn-based tourney with incremental time */
    boolean vacationAllowed(long pid) throws PlayStoreException;

    /** CacheTBStorer.addExtraTBVacationDays (not idempotent) */
    void addVacationDays(long pid, int days) throws PlayStoreException;

    /** whole turn-based vacation days left this year: CacheTBStorer.getTBVacation(pid).getHoursLeft() / 24, 0 if none */
    int vacationDaysLeft(long pid) throws PlayStoreException;

    /** after a grant: default name colour, thank-you message when NEW, refreshPlayer, admin push */
    void granted(long pid, String productId, PlayClassification classification) throws PlayStoreException;

    /** after a deferral, clamp or revoke: refreshPlayer */
    void entitlementChanged(long pid) throws PlayStoreException;

    /** admin push notification */
    void adminAlert(String message);
}
