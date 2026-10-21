package org.pente.gameServer.tourney;

import java.util.Date;
import java.util.List;

/**
 * Pure withdraw rules for tournament sign-up withdrawal. No DB or server state.
 */
public final class TourneyWithdraw {

    public enum Result { OK, NOT_LOGGED_IN, NO_TOURNEY, REGISTRATION_CLOSED, NOT_REGISTERED }

    private TourneyWithdraw() {
    }

    /**
     * Registration is open iff the tournament is in the upcoming list and its
     * signup end date is still in the future.
     */
    public static boolean isRegistrationOpen(Tourney t, List<Tourney> upcoming, Date now) {
        if (t == null || upcoming == null || t.getSignupEndDate() == null) {
            return false;
        }
        boolean upcomingFound = false;
        for (Tourney u : upcoming) {
            if (u != null && u.getEventID() == t.getEventID()) {
                upcomingFound = true;
                break;
            }
        }
        return upcomingFound && t.getSignupEndDate().after(now);
    }

    /** Checks in order; first failure wins. */
    public static Result check(boolean loggedIn, Tourney t, boolean registrationOpen, boolean registered) {
        if (!loggedIn) {
            return Result.NOT_LOGGED_IN;
        }
        if (t == null) {
            return Result.NO_TOURNEY;
        }
        if (!registrationOpen) {
            return Result.REGISTRATION_CLOSED;
        }
        if (!registered) {
            return Result.NOT_REGISTERED;
        }
        return Result.OK;
    }

    public static String message(Result r, String tourneyName) {
        switch (r) {
            case OK:
                return "You have withdrawn from " + tourneyName + ". You can sign up again while registration is open.";
            case NOT_LOGGED_IN:
                return "You must be logged in to withdraw from a tournament.";
            case NO_TOURNEY:
                return "Tournament not found.";
            case REGISTRATION_CLOSED:
                return "Registration for " + tourneyName + " is closed, so you can no longer withdraw.";
            case NOT_REGISTERED:
                return "You are not signed up for " + tourneyName + ".";
            default:
                throw new IllegalArgumentException("Unknown result: " + r);
        }
    }
}
