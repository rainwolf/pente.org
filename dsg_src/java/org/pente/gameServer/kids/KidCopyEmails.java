package org.pente.gameServer.kids;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/** Pure text builders for parent copy emails. */
public final class KidCopyEmails {

    private static final String KIDS_URL = "https://www.pente.org/gameServer/myprofile/kids";

    private KidCopyEmails() {
    }

    private static String format(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d) + " UTC";
    }

    public static String pmSubject(String fromName, String toName, String subject) {
        return "Pente.org message copy: " + fromName + " to " + toName + ": " + subject;
    }

    public static String pmBody(String kidName, String fromName, String toName,
                                Date sent, String subject, String body) {
        return "From: " + fromName + "\n"
                + "To: " + toName + "\n"
                + "Date: " + format(sent) + "\n"
                + "Subject: " + subject + "\n"
                + "\n"
                + body + "\n"
                + "\n"
                + "You get this copy because message copies are on for " + kidName
                + ". Change it at " + KIDS_URL;
    }

    public static String liveChatLine(Date when, String where, String author, String text) {
        return format(when) + " [" + where + "] " + author + ": " + text;
    }

    public static String liveChatSubject(String kidName) {
        return "Pente.org live chat copy: " + kidName;
    }

    public static String liveChatBody(String kidName, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Live game room chat that ").append(kidName).append(" sent or saw:\n\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        sb.append("\nYou get this copy because live chat copies are on for ")
                .append(kidName).append(". Change it at ").append(KIDS_URL);
        return sb.toString();
    }
}
