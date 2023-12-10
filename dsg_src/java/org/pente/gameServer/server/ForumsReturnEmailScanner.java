package org.pente.gameServer.server;

import java.io.*;
import java.util.*;
import java.sql.*;

import javax.mail.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.jive.*;

public class ForumsReturnEmailScanner {

    public static void main(String[] args) throws Throwable {
        DBHandler dbHandler = new MySQLDBHandler(args[0], args[1], args[2]);
        GameVenueStorer gameVenueStorer = new MySQLGameVenueStorer(dbHandler);
        DSGPlayerStorer dsgPlayerStorer = new MySQLDSGPlayerStorer(
                dbHandler, gameVenueStorer);
        new ForumsReturnEmailScanner(dbHandler, dsgPlayerStorer).scanEmails();
    }

    private static Category log4j =
            Category.getInstance(ForumsReturnEmailScanner.class.getName());

    private DSGAuthToken adminToken = null;

    private DBHandler dbHandler;
    private DSGPlayerStorer dsgPlayerStorer;

    private ArrayList scannedMessages = new ArrayList();

    private class Data {
        public Data(Message message, String email) {
            this.message = message;
            this.email = email;
        }

        public Message message;
        public String email;
    }

    ;

    public ForumsReturnEmailScanner(
            DBHandler dbHandler,
            DSGPlayerStorer dsgPlayerStorer) {

        this.dbHandler = dbHandler;
        this.dsgPlayerStorer = dsgPlayerStorer;

        adminToken = new DSGAuthToken(22000000000002L);
    }

    public void scanEmails() throws Throwable {

        String mailHost = System.getProperty("mail.imap.host");
        String user = "forums";
        String password = System.getProperty("mail.imap.password");

        Session session = Session.getDefaultInstance(
                System.getProperties(), null);

        Store store = session.getStore("imap");
        store.connect(mailHost, user, password);

        // Get inbox
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        ArrayList data = new ArrayList();
        Message message[] = inbox.getMessages();
        for (int i = 0, n = message.length; i < n; i++) {

            if (scannedMessages.contains(message[i])) continue;
            scannedMessages.add(message[i]);

            addAddresses(message[i], data);
        }

        // update database to mark as invalid
        updateDatabase(data);

        // move messages to returned folder
        Message move[] = new Message[data.size()];
        for (int i = 0; i < data.size(); i++) {
            Data d = (Data) data.get(i);
            move[i] = d.message;
        }
        Folder returned = store.getFolder("returned");
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

    private void addAddresses(Message message, ArrayList data) throws Throwable {

        ArrayList emails = new ArrayList(5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);

        byte[] bytes = out.toByteArray();
        int leftIndex = -1;
        int atIndex = -1;
        int rightIndex = -1;
        for (int j = 0; j < bytes.length; j++) {
            byte b = bytes[j];
            if (b == '<') {
                leftIndex = j;
                atIndex = -1;
                rightIndex = -1;
            } else if (b == '@' && leftIndex != -1) {
                atIndex = j;
            } else if (b == '>' && atIndex != -1) {
                rightIndex = j;

                // found email address
                String address = new String(bytes, leftIndex, rightIndex - leftIndex).trim();
                if (!address.equals("forums@pente.org") &&
                        !emails.contains(address)) {

                    emails.add(address);
                    data.add(new Data(message, address));
                }
            }
        }
    }


    private void updateDatabase(List data) throws Throwable {

        Set pids = new TreeSet();
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;

        // first get unique pid's that match emails
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                    "select pid " +
                            "from dsg_player " +
                            "where email = ?");

            for (Iterator it = data.iterator(); it.hasNext(); ) {
                Data d = (Data) it.next();
                stmt.setString(1, d.email);
                result = stmt.executeQuery();
                boolean empty = true;
                while (result.next()) {
                    empty = false;
                    Long pid = Long.valueOf(result.getLong(1));
                    pids.add(pid);
                }
                // don't remove this message from inbox if email not found
                // in database
                if (empty) {
                    it.remove();
                }
            }
        } finally {
            if (result != null) {
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            dbHandler.freeConnection(con);
        }

        // then look up all player data and mark emails invalid and
        // remove from forums
        for (Iterator it = pids.iterator(); it.hasNext(); ) {
            Long pid = (Long) it.next();

            try {
                DSGPlayerData dsgPlayerData =
                        dsgPlayerStorer.loadPlayer(pid.longValue());

                if (dsgPlayerData == null) {
                    log4j.error(pid.longValue() +
                            " not found in returned email processing.");
                    continue;
                }

                if (dsgPlayerData.getEmailValid()) {

                    log4j.info("Returned forum mail - updating " +
                            dsgPlayerData.getName() + "'s email (" +
                            dsgPlayerData.getEmail() + ") to invalid.");

                    dsgPlayerData.setEmailValid(false);
                    dsgPlayerData.setLastUpdateDate(new java.util.Date());
                    dsgPlayerStorer.updatePlayer(dsgPlayerData);
                } else {
                    log4j.info("Returned forum email - " +
                            dsgPlayerData.getName() +
                            " already marked invalid.");
                }

            } catch (DSGPlayerStoreException e) {
                log4j.error("Problem loading/updating player in returned " +
                        "email processing.", e);
            }
        }
    }
}
