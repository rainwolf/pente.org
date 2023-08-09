package org.pente.gameServer.server;

import java.io.*;
import java.util.*;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;

public class ReturnEmailScanner {

    public static void main(String[] args) throws Throwable {
        DBHandler dbHandler = new MySQLDBHandler(args[0], args[1], args[2]);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(
                dbHandler, gameVenueStorer);
        new ReturnEmailScanner(dbHandler, dsgPlayerStorer).scanEmails();
    }

    private static Category log4j =
            Category.getInstance(ReturnEmailScanner.class.getName());

    private MySQLDSGReturnEmailStorer returnEmailStorer;
    private DSGPlayerStorer dsgPlayerStorer;

    private Hashtable scannedMessageIds = new Hashtable();

    public ReturnEmailScanner(
            DBHandler dbHandler,
            DSGPlayerStorer dsgPlayerStorer) {

        this.dsgPlayerStorer = dsgPlayerStorer;

        returnEmailStorer = new MySQLDSGReturnEmailStorer(dbHandler);
    }

    public void scanEmails() throws Throwable {

        String mailHost = System.getProperty("mail.imap.host");
        String user = System.getProperty("mail.imap.user");
        String password = System.getProperty("mail.imap.password");

        Session session = Session.getDefaultInstance(
                System.getProperties(), null);

        Store store = session.getStore("imap");
        store.connect(mailHost, user, password);

        // Get folder
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);


        Message message[] = inbox.getMessages();
        ArrayList moveMessages = new ArrayList();
        for (int i = 0, n = message.length; i < n; i++) {
            Vector messageIds = getMessageIds(message[i]);

            if (messageIds != null) {
                for (Iterator it = messageIds.iterator(); it.hasNext(); ) {
                    String m = (String) it.next();

                    // only scan messages once
                    if (scannedMessageIds.get(m) != null) {
                        continue;
                    }
                    // if the message id is for the received email ignore it
                    // we only want the message id of other included emails
                    else {
                        if (!(message[i] instanceof MimeMessage)) continue;
                        String mid = ((MimeMessage) message[i]).getMessageID();
                        if (mid != null && mid.equals(m)) continue;
                    }

                    scannedMessageIds.put(m, m);

                    updateDatabase(m);

                    moveMessages.add(message[i]);
                }
            }
        }

        // move messages to returned folder
        Message move[] = new Message[moveMessages.size()];
        for (int i = 0; i < moveMessages.size(); i++) {
            move[i] = (Message) moveMessages.get(i);
        }
        Folder returned = store.getFolder("mail/returned");
        returned.open(Folder.READ_WRITE);

        inbox.copyMessages(move, returned);

        for (int i = 0; i < move.length; i++) {
            move[i].setFlag(Flags.Flag.DELETED, true);
        }
        inbox.expunge();

        // close everything
        inbox.close(false);
        returned.close(false);
        store.close();
    }

    private static final byte[] messageIdBytes = "message-id:".getBytes();

    private Vector getMessageIds(Message message) throws Throwable {

        Vector messageIds = new Vector();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);

        byte[] bytes = out.toByteArray();
        int messageIdIndex = 0;
        for (int j = 0; j < bytes.length; j++) {
            byte b = bytes[j];
            if (b >= 'A' && b <= 'Z') {
                b = (byte) (b + 32);
            }
            if (b == messageIdBytes[messageIdIndex]) {
                messageIdIndex++;
            } else {
                messageIdIndex = 0;
            }

            if (messageIdIndex == messageIdBytes.length) {

                int begin = j + 1;
                while (++j < bytes.length) {
                    if (bytes[j] == 13 || bytes[j] == 10) {
                        break;
                    }
                }
                String messageId = new String(bytes, begin, j - begin).trim();
                messageIds.add(messageId);
                messageIdIndex = 0;
            }
        }

        return messageIds;
    }


    private void updateDatabase(String messageId) throws Throwable {

        DSGReturnEmailData returnEmailData =
                returnEmailStorer.getReturnedEmailData(messageId);

        if (returnEmailData == null) {
            log4j.debug("Message " + messageId + " not found in database.");
            return;
        }

        try {
            DSGPlayerData dsgPlayerData =
                    dsgPlayerStorer.loadPlayer(returnEmailData.getPid());

            if (dsgPlayerData == null) {
                log4j.error(returnEmailData.getPid() +
                        " not found in returned email processing.");
                return;
            }

            // if the player updated their profile after the
            // email was sent, assume they fixed their email
            // address (cautious!)
            if (dsgPlayerData.getEmail().equals(returnEmailData.getEmail()) &&
                    !dsgPlayerData.getLastUpdateDate().after(returnEmailData.getSendDate()) &&
                    dsgPlayerData.getEmailValid()) {

                log4j.info("For message " + messageId + ", updating " +
                        dsgPlayerData.getName() + "'s email (" +
                        dsgPlayerData.getEmail() + ") to invalid.");

                dsgPlayerData.setEmailValid(false);
                dsgPlayerData.setLastUpdateDate(new java.util.Date());
                dsgPlayerStorer.updatePlayer(dsgPlayerData);
            } else {
                log4j.info("Email returned for " + dsgPlayerData.getName() +
                        " but player has updated profile.");
            }

        } catch (DSGPlayerStoreException e) {
            log4j.error("Problem loading/updating player in returned email processing.", e);
        }
    }
}
