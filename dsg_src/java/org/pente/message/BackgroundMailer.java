package org.pente.message;

import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.*;


public class BackgroundMailer extends BackgroundWorker implements TextMailer {

    private static Category log4j =
            Category.getInstance(BackgroundMailer.class.getName());

    private String smtpHost;
    private int smtpPort;
    private String smtpUser;
    private String smtpPassword;

    private DSGPlayerStorer dsgPlayerStorer;

    public BackgroundMailer(String smtpHost, int smtpPort,
                            String smtpUser, String smtpPassword, DSGPlayerStorer dsgPlayerStorer) {
        super("Mailer");

        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser;
        this.smtpPassword = smtpPassword;
        this.dsgPlayerStorer = dsgPlayerStorer;
    }

    class Work {
        DSGMessage message;
        boolean ccSender, email;

        public Work(DSGMessage message, boolean email, boolean ccSender) {
            this.message = message;
            this.ccSender = ccSender;
            this.email = email;
        }
    }

    class TextWork {
        String toName, toEmail, subject, body;

        TextWork(String toName, String toEmail, String subject, String body) {
            this.toName = toName;
            this.toEmail = toEmail;
            this.subject = subject;
            this.body = body;
        }
    }

    public void mail(DSGMessage message, boolean email, boolean ccSender) {
        doWork(new Work(message, email, ccSender));
    }

    public void mailText(String toName, String toEmail, String subject, String body) {
        doWork(new TextWork(toName, toEmail, subject, body));
    }

    public void mailTextNow(String toName, String toEmail, String subject, String body,
                            int timeoutMillis) throws Exception {
        sendText(new TextWork(toName, toEmail, subject, body), timeoutMillis);
    }

    /** timeoutMillis &lt;= 0 means no explicit SMTP timeouts. */
    private void sendText(TextWork w, int timeoutMillis) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.user", smtpUser);
        props.put("mail.smtp.password", smtpPassword);
        props.put("mail.smtp.auth", "true");
        if (timeoutMillis > 0) {
            String t = String.valueOf(timeoutMillis);
            props.put("mail.smtp.connectiontimeout", t);
            props.put("mail.smtp.timeout", t);
            props.put("mail.smtp.writetimeout", t);
        }

        // getInstance, not getDefaultInstance: the default instance caches the
        // first Properties JVM-wide, so the timeouts would be ignored.
        Session session = Session.getInstance(props, null);
        MimeMessage message = buildTextMessage(session, w);

        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    private MimeMessage buildTextMessage(Session session, TextWork w) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpUser, "Pente.org"));
        message.addRecipient(Message.RecipientType.TO,
                new InternetAddress(w.toEmail, w.toName));
        message.setSubject(w.subject);
        message.setSentDate(new java.util.Date());
        message.setReplyTo(new Address[]{new InternetAddress("noreply@pente.org")});
        message.setText(w.body);
        return message;
    }

    public void internalDoWork(Object obj) {
        if (obj instanceof TextWork) {
            TextWork tw = (TextWork) obj;
            try {
                sendText(tw, 0);
            } catch (Throwable t) {
                log4j.error("Problem sending text email to " + tw.toEmail, t);
            }
            return;
        }
        Work w = (Work) obj;
        DSGMessage m = w.message;

        if (!w.email && !w.ccSender) {
            return;
        }

        try {
            log4j.info("Email message " + m.getMid() + ", cc=" + w.ccSender);

            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.user", smtpUser);
            props.put("mail.smtp.password", smtpPassword);
            props.put("mail.smtp.auth", "true");

            Session session = Session.getDefaultInstance(props, null);
            MimeMessage message = new MimeMessage(session);

            DSGPlayerData fromData = dsgPlayerStorer.loadPlayer(m.getFromPid());
            DSGPlayerData toData = dsgPlayerStorer.loadPlayer(m.getToPid());

            if (!toData.isActive()) {
                return;
            }

            message.setFrom(new InternetAddress(smtpUser, fromData.getName()));
            if (w.email) {
                message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(toData.getEmail(), toData.getName()));
                if (w.ccSender) {
                    message.addRecipient(Message.RecipientType.BCC,
                            new InternetAddress(fromData.getEmail(), fromData.getName()));
                }
            } else if (w.ccSender) {
                message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(fromData.getEmail(), fromData.getName()));
            }
            //message.addRecipient(Message.RecipientType.BCC,
            //	new InternetAddress("dweebo@pente.org", "dweebo"));

            message.setSubject("Pente.org Message: " + m.getSubject());
            message.setSentDate(m.getCreationDate());
            message.setReplyTo(new Address[]{new InternetAddress("noreply@pente.org")});

            String body = m.getBody() + "\n\n" +
                    "This message was sent through http://pente.org.\n" +
                    "DO NOT REPLY VIA EMAIL, instead view and reply at " +
                    "https://pente.org/gameServer/mymessages?command=view&mid=" + m.getMid() +
                    "\n \n Email settings can be changed at https://pente.org/gameServer/myprofile/prefs";
            message.setText(body);

            Transport transport = session.getTransport("smtp");
            transport.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();

        } catch (DSGPlayerStoreException dpse) {
            log4j.error("Problem loading players to send dsg message " +
                    m.getMid(), dpse);
        } catch (Throwable t) {
            log4j.error("Problem sending dsg message " + m.getMid(), t);
        }
    }
}
