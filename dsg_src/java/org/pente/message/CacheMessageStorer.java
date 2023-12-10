package org.pente.message;

import java.util.*;

import org.pente.gameServer.core.*;

import org.apache.log4j.Category;

public class CacheMessageStorer implements DSGMessageStorer {

    private Category log4j = Category.getInstance(
            CacheMessageStorer.class.getName());

    private DSGMessageStorer baseStorer;

    private Object cacheLock = new Object();

    private Map<Integer, DSGMessage> messages =
            new HashMap<Integer, DSGMessage>();
    private Map<Long, List<Integer>> playerMids =
            new HashMap<Long, List<Integer>>();

    private DSGPlayerStorer dsgPlayerStorer;
    private BackgroundMailer mailer;
    private boolean emailEnabled;

    public CacheMessageStorer(
            DSGMessageStorer baseStorer,
            boolean emailEnabled,
            String smtpHost,
            int smtpPort,
            String smtpUser,
            String smtpPassword,
            DSGPlayerStorer dsgPlayerStorer) {

        this.baseStorer = baseStorer;
        this.dsgPlayerStorer = dsgPlayerStorer;
        this.emailEnabled = emailEnabled;
        mailer = new BackgroundMailer(smtpHost, smtpPort,
                smtpUser, smtpPassword, dsgPlayerStorer);
    }

    private void cacheMessage(DSGMessage message) {

        log4j.debug("CacheMessageStorer.cacheMessage(" +
                message.getMid() + ")");

        synchronized (cacheLock) {
            messages.put(message.getMid(), message);

            List<Integer> mids = playerMids.get(message.getToPid());
            if (mids != null) {
                mids.add(message.getMid());
            }
        }
    }

    private void uncacheMessage(int mid) {
        log4j.debug("CacheMessageStorer.uncacheMessage(" + mid + ")");

        synchronized (cacheLock) {
            DSGMessage m = messages.remove(mid);
            if (m != null) {
                List<Integer> mids = playerMids.get(m.getToPid());
                if (mids != null) {
                    mids.remove(Integer.valueOf(mid));
                }
            }
        }
    }

    public void createMessage(DSGMessage message)
            throws DSGMessageStoreException {
        createMessage(message, true);
    }

    public void createMessage(DSGMessage message, boolean ccSenderPossible)
            throws DSGMessageStoreException {

        log4j.debug("CacheMessageStorer.createMessage(" + message.getFromPid() +
                "->" + message.getToPid() + " : " + message.getSubject() + ", " +
                "ccSender=" + ccSenderPossible + ")");

        baseStorer.createMessage(message);

        unCacheMessagesForPlayer(message.getToPid());

//		cacheMessage(message);

        if (emailEnabled) {
            try {
                List toPrefs = dsgPlayerStorer.loadPlayerPreferences(
                        message.getToPid());
                List fromPrefs = dsgPlayerStorer.loadPlayerPreferences(
                        message.getFromPid());

                //assumes that all users want to send messages via email
                //unless set a property to not
                boolean email = true;
                // cc sender is opt-in however
                boolean ccSender = false;
                if (ccSenderPossible) {
                    for (Iterator it = fromPrefs.iterator(); it.hasNext(); ) {
                        DSGPlayerPreference pref = (DSGPlayerPreference) it.next();
                        if (pref.getName().equals("emailSentDsgMessages")) {
                            ccSender = ((Boolean) pref.getValue()).booleanValue();
                        }
                    }
                }
                for (Iterator it = toPrefs.iterator(); it.hasNext(); ) {
                    DSGPlayerPreference pref = (DSGPlayerPreference) it.next();
                    if (pref.getName().equals("emailDsgMessages")) {
                        email = ((Boolean) pref.getValue()).booleanValue();
                    }
                }

                mailer.mail(message, email, ccSender);

            } catch (DSGPlayerStoreException dpse) {
                log4j.debug("CacheMessageStorer, error loading prefs to send email.", dpse);
            }
        }
    }

    public void readMessage(DSGMessage message) throws DSGMessageStoreException {

        log4j.debug("CacheMessageStorer.readMessage(" + message.getMid() + ")");

        baseStorer.readMessage(message);
    }

    public void deleteMessage(int mid) throws DSGMessageStoreException {
        log4j.debug("CacheMessageStorer.deleteMessage(" + mid + ")");

        baseStorer.deleteMessage(mid);
        DSGMessage m = messages.get(mid);
        if (m != null) {
            long pid = m.getToPid();
            unCacheMessagesForPlayer(pid);
        }
//		uncacheMessage(mid);
    }

    private void unCacheMessagesForPlayer(long pid) {
        synchronized (cacheLock) {
            List<Integer> mids = playerMids.get(pid);
            if (mids != null) {
                for (Integer mid : mids) {
                    messages.remove(mid);
                }
                playerMids.remove(pid);
            }
        }
    }

    public DSGMessage getMessage(int mid) throws DSGMessageStoreException {

        log4j.debug("CacheMessageStorer.getMessage(" + mid + ")");

        DSGMessage message = null;

        synchronized (cacheLock) {
            message = messages.get(mid);
        }
        if (message == null) {
            message = baseStorer.getMessage(mid);
            cacheMessage(message);
        }

        return message;
    }

    public List<DSGMessage> getMessages(long pid)
            throws DSGMessageStoreException {

        List<DSGMessage> ms = null;
        List<Integer> mids = null;

        log4j.debug("CacheMessageStorer.getMessages(" + pid + ")");

        synchronized (cacheLock) {
            mids = playerMids.get(pid);
            // copy mids since whole method is not synched
            if (mids != null) {
                mids = new ArrayList<Integer>(mids);
            }
        }

        if (mids == null) {
            log4j.debug("not cached");
            ms = baseStorer.getMessages(pid);
            mids = new ArrayList<Integer>(ms.size());
            synchronized (cacheLock) {
                for (DSGMessage m : ms) {
                    cacheMessage(m);
                    mids.add(m.getMid());
                }
                playerMids.put(pid, mids);
            }
            return new ArrayList<DSGMessage>(ms);
        } else {
            log4j.debug("cached");
            ms = new ArrayList<DSGMessage>(mids.size());
            for (Integer i : mids) {
                DSGMessage m = messages.get(i);
                if (m != null) {
                    ms.add(m);
                }
            }
            return ms;
        }
    }

    @Override
    public List<DSGMessage> getNextMessages(long pid, long start) throws DSGMessageStoreException {
        List<DSGMessage> ms = null;
        List<Integer> mids = null;

        log4j.debug("CacheMessageStorer.getNextMessages(" + pid + ", " + start + ")");

        ms = baseStorer.getNextMessages(pid, start);

        return ms;
    }

    public int getNumNewMessages(long pid) throws DSGMessageStoreException {

        log4j.debug("CacheMessageStorer.getNumMessages(" + pid + ")");

        List<Integer> mids = null;
        synchronized (cacheLock) {
            mids = playerMids.get(pid);
        }
        if (mids == null) {
            getMessages(pid);
            mids = playerMids.get(pid);
        }

        // this could be improved
        int cnt = 0;
        for (Integer i : mids) {
            DSGMessage m = messages.get(i);
            if (!m.isRead()) {
                cnt++;
            }
        }
        return cnt;
    }

}
