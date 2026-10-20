package org.pente.gameServer.kids.test;

import java.util.ArrayList;
import java.util.List;

import org.pente.message.TextMailer;

/** Test fake: records queued and synchronous sends. */
public class RecordingTextMailer implements TextMailer {

    public final List<String[]> sent = new ArrayList<String[]>();
    public final List<String[]> sentNow = new ArrayList<String[]>();
    public final List<Integer> nowTimeouts = new ArrayList<Integer>();
    public boolean failNow;
    public Runnable onSendNow;

    public void mailText(String toName, String toEmail, String subject, String body) {
        sent.add(new String[]{toName, toEmail, subject, body});
    }

    public void mailTextNow(String toName, String toEmail, String subject, String body,
                            int timeoutMillis) throws Exception {
        if (onSendNow != null) {
            onSendNow.run();
        }
        if (failNow) {
            throw new Exception("send failed");
        }
        sentNow.add(new String[]{toName, toEmail, subject, body});
        nowTimeouts.add(timeoutMillis);
    }
}
