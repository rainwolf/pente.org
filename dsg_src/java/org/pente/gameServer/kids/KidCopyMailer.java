package org.pente.gameServer.kids;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Category;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.message.BackgroundMailer;
import org.pente.message.DSGMessage;
import org.pente.message.TextMailer;

/** Emails parents copies of their kids' private messages and live chat. */
public class KidCopyMailer {

    private static Category log4j = Category.getInstance(KidCopyMailer.class.getName());

    private final boolean emailEnabled;
    private final TextMailer mailer;
    private final DSGPlayerStorer players;
    private final KidStorer kids;

    public KidCopyMailer(boolean emailEnabled, TextMailer mailer,
                         DSGPlayerStorer players, KidStorer kids) {
        this.emailEnabled = emailEnabled;
        this.mailer = mailer;
        this.players = players;
        this.kids = kids;
    }

    public void copyPrivateMessage(DSGMessage m) {
        try {
            DSGPlayerData from = players.loadPlayer(m.getFromPid());
            DSGPlayerData to = players.loadPlayer(m.getToPid());
            if (from == null || to == null) {
                return;
            }
            // kid pid -> parent pid, insertion ordered, one mail per parent
            Set<Long> parents = new LinkedHashSet<Long>();
            Long[] kidParents = new Long[2];
            DSGPlayerData[] sides = {from, to};
            for (int i = 0; i < sides.length; i++) {
                Long parent = sides[i].getParentPid();
                if (parent != null && kids.getSettings(sides[i].getPlayerID()).isCopyMessages()) {
                    parents.add(parent);
                    kidParents[i] = parent;
                }
            }
            for (Long parentPid : parents) {
                DSGPlayerData kid = kidParents[0] != null && kidParents[0].equals(parentPid) ? from : to;
                DSGPlayerData parent = usableParent(parentPid);
                if (parent == null) {
                    continue;
                }
                Date sent = m.getCreationDate() == null ? new Date() : m.getCreationDate();
                mailer.mailText(parent.getName(), parent.getEmail(),
                        KidCopyEmails.pmSubject(from.getName(), to.getName(), m.getSubject()),
                        KidCopyEmails.pmBody(kid.getName(), from.getName(), to.getName(),
                                sent, m.getSubject(), m.getBody()));
                log4j.info("Kid copy queued: kid=" + kid.getPlayerID()
                        + " parent=" + parentPid + " kind=pm");
            }
        } catch (Throwable t) {
            log4j.error("Kid message copy failed", t);
        }
    }

    public void copyLiveChat(long kidPid, long parentPid, List<String> lines) {
        try {
            if (lines == null || lines.isEmpty()) {
                return;
            }
            DSGPlayerData parent = usableParent(parentPid);
            DSGPlayerData kid = players.loadPlayer(kidPid);
            if (parent == null || kid == null) {
                return;
            }
            mailer.mailText(parent.getName(), parent.getEmail(),
                    KidCopyEmails.liveChatSubject(kid.getName()),
                    KidCopyEmails.liveChatBody(kid.getName(), lines));
            log4j.info("Kid copy queued: kid=" + kidPid + " parent=" + parentPid + " kind=live");
        } catch (Throwable t) {
            log4j.error("Kid live chat copy failed", t);
        }
    }

    /** Shutdown path: sends on the caller's thread. False means the send failed; keep the buffer. */
    public boolean copyLiveChatNow(long kidPid, long parentPid, List<String> lines,
                                   int timeoutMillis) {
        try {
            if (lines == null || lines.isEmpty()) {
                return true;
            }
            DSGPlayerData parent = usableParent(parentPid);
            DSGPlayerData kid = players.loadPlayer(kidPid);
            if (parent == null || kid == null) {
                return true;
            }
            mailer.mailTextNow(parent.getName(), parent.getEmail(),
                    KidCopyEmails.liveChatSubject(kid.getName()),
                    KidCopyEmails.liveChatBody(kid.getName(), lines), timeoutMillis);
            log4j.info("Kid copy sent: kid=" + kidPid + " parent=" + parentPid
                    + " kind=live-shutdown");
            return true;
        } catch (Throwable t) {
            log4j.error("Kid live chat shutdown copy failed", t);
            return false;
        }
    }

    public void destroy() {
        if (mailer instanceof BackgroundMailer) {
            ((BackgroundMailer) mailer).destroy();
        }
    }

    /** The parent when email is enabled and the parent is active with a valid email, else null. */
    private DSGPlayerData usableParent(long parentPid) throws Exception {
        if (!emailEnabled) {
            return null;
        }
        DSGPlayerData parent = players.loadPlayer(parentPid);
        if (parent == null || !parent.isActive() || !parent.getEmailValid()) {
            return null;
        }
        return parent;
    }
}
