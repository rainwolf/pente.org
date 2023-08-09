package org.pente.turnBased;

public class MessageEncoder {

    public static void main(String args[]) {
        System.out.println(args[0]);
        String enc = encodeMessage(args[0]);
        System.out.println(enc);
        String dec = decodeMessage(enc);
        System.out.println(dec);
        if (!dec.equals(args[0])) {
            System.out.println("failed");
        }

    }

    public static String encodeMessage(String msg) {
        StringBuffer ret = new StringBuffer(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            // commas used as array delimiter of messages
            if (msg.charAt(i) == ',') {
                ret.append("\\1");
            }
            // quotes mess up because stored in applet param tag
            else if (msg.charAt(i) == '"') {
                ret.append("\\2");
            }
            // & used in escaped html, but applet restores it to html
            // so double-escape html
            else if (msg.charAt(i) == '&') {
                ret.append("&amp;");
            }
            // escape the escape character
            else if (msg.charAt(i) == '\\') {
                ret.append("\\\\");
            } else {
                ret.append(msg.charAt(i));
            }
        }
        return ret.toString();
    }

    public static String decodeMessage(String msg) {
        StringBuffer ret = new StringBuffer(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            if (msg.charAt(i) == '\\') {
                if (i < msg.length() - 1 &&
                        msg.charAt(i + 1) == '1') {
                    ret.append(',');
                } else if (i < msg.length() - 1 &&
                        msg.charAt(i + 1) == '2') {
                    ret.append("\"");
                } else {
                    ret.append(msg.charAt(i + 1));
                }
                i++;
            } else {
                ret.append(msg.charAt(i));
            }
        }
        return ret.toString();
    }
}
