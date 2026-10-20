package org.pente.gameServer.kids;

/** Text of the emails sent when a parent creates or converts a kid account (pure). */
public final class KidAccountEmails {

    private static final String NL = "\n";
    private static final String MANAGE_URL = "https://www.pente.org/gameServer/myprofile/kids";
    private static final String CONFIRM_URL = "https://www.pente.org/confirmKidLink?code=";

    private KidAccountEmails() {
    }

    public static String createdSubject() {
        return "Pente.org kid account created";
    }

    public static String createdBody(String parentName, String kidName, String password) {
        return "Dear " + parentName + "," + NL + NL
            + "You created the kid account " + kidName + " on Pente.org." + NL + NL
            + "Name: " + kidName + NL
            + "Password: " + password + NL + NL
            + kidName + " can play anyone and can chat only with players you approve at "
            + MANAGE_URL + NL;
    }

    public static String conversionSubject() {
        return "Pente.org kid account request";
    }

    public static String conversionBody(String kidName, String parentName, String code) {
        return "Dear " + kidName + "," + NL + NL
            + parentName + " asked to manage your Pente.org account " + kidName
            + " as a kid account." + NL + NL
            + "A kid account can still play anyone, but can chat and exchange messages only with "
            + "players " + parentName + " approves. " + parentName
            + " will manage its password and email, and can choose to get copies of its "
            + "messages and live chat." + NL + NL
            + "To accept, open this link and confirm:" + NL
            + CONFIRM_URL + code + NL + NL
            + "The link works for 7 days. If you don't want this, ignore this email and "
            + "nothing will change." + NL;
    }
}
