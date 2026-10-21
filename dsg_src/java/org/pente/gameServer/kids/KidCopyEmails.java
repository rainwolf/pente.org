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

    public static String tbSubject(String kidName, String opponentName, String gameName) {
        return "Pente.org game messages: " + kidName + " vs " + opponentName
                + " (" + gameName + ")";
    }

    public static String tbLine(int moveNum, Date when, String author, String text) {
        if (when == null) {
            return "Move " + moveNum + ", " + author + ": " + text;
        }
        return "Move " + moveNum + ", " + format(when) + ", " + author + ": " + text;
    }

    public static String tbBody(String kidName, String p1Name, String p2Name,
                                String gameName, long gid, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Turn-based game messages between ").append(p1Name).append(" and ")
                .append(p2Name).append(" (").append(gameName).append("):\n\n");
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        sb.append("\nView the game: https://www.pente.org/gameServer/tb/game?gid=")
                .append(gid).append("&command=load\n\n")
                .append("You get this copy because turn-based game message copies are on for ")
                .append(kidName).append(". Change it at ").append(KIDS_URL);
        return sb.toString();
    }
}
