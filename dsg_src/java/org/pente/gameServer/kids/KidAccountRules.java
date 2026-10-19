package org.pente.gameServer.kids;

/** Error strings for kid accounts (API contract text, verbatim). */
public class KidAccountRules {

    public static final String KID_PM_ERROR =
        "You can only send messages to players your parent approved.";
    public static final String KID_BROADCAST_ERROR = "Kid accounts can't broadcast.";

    private KidAccountRules() {
    }
}
