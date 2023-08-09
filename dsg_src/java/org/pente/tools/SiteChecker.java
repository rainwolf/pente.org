package org.pente.tools;

import java.util.*;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import javax.mail.*;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.internet.*;

public class SiteChecker {


    public static void main(final String[] args) {

        GetMethod method = null;
        int r = 0;
        try {
            // maybe format and attach a pdf version of registration form
            // send email
            HttpClient client = new HttpClient();

            method = new GetMethod(args[0]);

            r = client.executeMethod(method);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        try {
            if (r != 200) {

                String mailHost = "smtp.gmail.com";
                final String user = "dweebo@gmail.com";
                final String password = args[1];
                int port = 465;

                Properties props = new Properties();
                props.put("mail.smtp.user", user);
                props.put("mail.smtp.host", mailHost);
                props.put("mail.smtp.port", port);
                props.put("mail.transport.protocol", "smtp");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl", "true");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.socketFactory.port", port);
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");

                Authenticator auth = new javax.mail.Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password);
                    }
                };
                Session session = Session.getInstance(props, auth);
                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress("dweebo@gmail.com"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress("peter@hewittsoft.com"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress("dweebo@gmail.com"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress("19376265862@mmst5.tracfone.com"));

                message.setSubject(args[0] + " down");
                message.setSentDate(new java.util.Date());

                message.setText(args[0] + " down");

                Transport transport = session.getTransport("smtp");
                transport.connect(mailHost, port, user, password);
                transport.sendMessage(message, message.getAllRecipients());
                transport.close();
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }
}

