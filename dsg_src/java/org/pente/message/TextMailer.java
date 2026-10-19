package org.pente.message;

/** Sends plain-text emails, queued or synchronously. */
public interface TextMailer {

    /** Queues a plain-text email on the mailer's worker thread. */
    void mailText(String toName, String toEmail, String subject, String body);

    /** Sends on the caller's thread with SMTP timeouts; throws on any failure. */
    void mailTextNow(String toName, String toEmail, String subject, String body,
                     int timeoutMillis) throws Exception;
}
